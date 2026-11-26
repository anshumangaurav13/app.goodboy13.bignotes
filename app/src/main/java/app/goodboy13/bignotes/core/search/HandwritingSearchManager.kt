package app.goodboy13.bignotes.core.search

import android.content.Context
import android.util.Log
import app.goodboy13.bignotes.core.model.Stroke
import app.goodboy13.bignotes.core.search.clustering.StrokeClusterer
import app.goodboy13.bignotes.core.search.db.HandwritingSearchDao
import app.goodboy13.bignotes.core.search.db.HandwritingSearchEntity
import app.goodboy13.bignotes.core.search.db.SearchDatabase
import app.goodboy13.bignotes.core.search.model.ModelDownloadStatus
import app.goodboy13.bignotes.core.search.model.SearchHit
import app.goodboy13.bignotes.core.search.model.StrokeCluster
import app.goodboy13.bignotes.core.search.recognition.DigitalInkRecognitionEngine
import app.goodboy13.bignotes.core.storage.BigNoteDocument
import app.goodboy13.bignotes.core.storage.NoteStorageManager
import app.goodboy13.bignotes.core.storage.RecognizedTextBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Coordinates stroke clustering, asynchronous ML Kit handwriting recognition,
 * Room database indexing, and full-text keyword querying.
 */
class HandwritingSearchManager(
    context: Context,
    private val recognitionEngine: DigitalInkRecognitionEngine = DigitalInkRecognitionEngine(),
    private val noteStorageManager: NoteStorageManager = NoteStorageManager(context)
) {
    private val TAG = "HandwritingSearchMgr"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val searchDao: HandwritingSearchDao = SearchDatabase.getInstance(context).handwritingSearchDao()

    private val activeIndexingJobs = ConcurrentHashMap<String, Job>()
    // Per-note cache mapping exact stroke-cluster signature -> recognized text to avoid re-running ML Kit on unchanged clusters
    private val clusterTextCache = ConcurrentHashMap<String, MutableMap<String, String>>()
    // Per-note seed cache from persisted RecognizedTextBlocks (matched by exact cluster bounds on first edit after load)
    private val docBboxTextSeed = ConcurrentHashMap<String, MutableMap<String, String>>()
    // Last indexed normalized cluster summary per note to skip identical Room/disk writes
    private val lastIndexedSnapshots = ConcurrentHashMap<String, List<Pair<String, String>>>()

    val downloadStatus: StateFlow<ModelDownloadStatus> = recognitionEngine.downloadStatus

    fun downloadModel(onComplete: ((Boolean) -> Unit)? = null) {
        recognitionEngine.downloadModelAsync(onComplete)
    }

    suspend fun getIndexedTimestamps(): Map<String, Long> = withContext(Dispatchers.IO) {
        searchDao.getAllIndexedNotesTimestamps().associate { it.noteId to it.lastIndexedAt }
    }

    /**
     * Fast-indexes pre-recognized text blocks directly from the document into Room cache
     * in < 1ms without invoking ML Kit.
     */
    suspend fun indexFromDocumentIfCached(folderId: String, doc: BigNoteDocument): Boolean = withContext(Dispatchers.IO) {
        if (doc.textBlocks.isEmpty()) return@withContext false
        val seedMap = HashMap<String, String>(doc.textBlocks.size)
        val entities = doc.textBlocks.map { tb ->
            seedMap[bboxKey(tb.minX, tb.minY, tb.maxX, tb.maxY)] = tb.text
            HandwritingSearchEntity(
                clusterId = tb.clusterId,
                noteId = doc.noteId,
                folderId = folderId,
                recognizedText = tb.text,
                minX = tb.minX,
                minY = tb.minY,
                maxX = tb.maxX,
                maxY = tb.maxY,
                updatedAt = tb.updatedAt
            )
        }
        docBboxTextSeed[doc.noteId] = seedMap
        searchDao.deleteForNote(doc.noteId)
        searchDao.insertAll(entities)
        Log.d(TAG, "Cached indexed ${entities.size} text blocks for note ${doc.noteId}")
        true
    }

    /**
     * Clusters strokes and incrementally indexes recognized handwriting asynchronously in the background.
     * Reuses cached recognition for unchanged clusters so only newly written/edited lines invoke ML Kit.
     */
    fun indexNoteAsync(
        noteId: String,
        folderId: String,
        strokes: List<Stroke>,
        debounceMs: Long = DEFAULT_INDEX_DEBOUNCE_MS
    ) {
        activeIndexingJobs[noteId]?.cancel()
        val job = scope.launch {
            try {
                if (debounceMs > 0L) {
                    delay(debounceMs)
                }

                // 1. Spatially cluster ink strokes into lines/phrases
                val clusters = withContext(Dispatchers.Default) {
                    StrokeClusterer.clusterStrokes(noteId, strokes)
                }

                if (clusters.isEmpty()) {
                    clusterTextCache.remove(noteId)
                    docBboxTextSeed.remove(noteId)
                    if (lastIndexedSnapshots[noteId]?.isEmpty() == true) return@launch
                    lastIndexedSnapshots[noteId] = emptyList()
                    searchDao.deleteForNote(noteId)
                    noteStorageManager.updateNoteTextBlocks(noteId, emptyList())
                    return@launch
                }

                val noteSigCache = clusterTextCache[noteId]
                val bboxSeed = docBboxTextSeed[noteId]
                val nextSigCache = HashMap<String, String>(clusters.size)
                val nextSnapshot = ArrayList<Pair<String, String>>(clusters.size)

                // 2. Incrementally recognize ONLY new or modified clusters
                val entities = mutableListOf<HandwritingSearchEntity>()
                val textBlocks = mutableListOf<RecognizedTextBlock>()
                val now = System.currentTimeMillis()
                var mlKitCalls = 0

                for (cluster in clusters) {
                    val sig = computeClusterSignature(cluster)
                    val bKey = bboxKey(cluster.minX, cluster.minY, cluster.maxX, cluster.maxY)
                    val cachedText = noteSigCache?.get(sig) ?: bboxSeed?.get(bKey)

                    val text = if (cachedText != null) {
                        cachedText
                    } else {
                        mlKitCalls++
                        recognitionEngine.recognizeCluster(cluster)
                    }

                    if (!text.isNullOrBlank()) {
                        nextSigCache[sig] = text
                        nextSnapshot.add(cluster.clusterId to text)
                        entities.add(
                            HandwritingSearchEntity(
                                clusterId = cluster.clusterId,
                                noteId = noteId,
                                folderId = folderId,
                                recognizedText = text,
                                minX = cluster.minX,
                                minY = cluster.minY,
                                maxX = cluster.maxX,
                                maxY = cluster.maxY,
                                updatedAt = now
                            )
                        )
                        textBlocks.add(
                            RecognizedTextBlock(
                                clusterId = cluster.clusterId,
                                text = text,
                                minX = cluster.minX,
                                minY = cluster.minY,
                                maxX = cluster.maxX,
                                maxY = cluster.maxY,
                                updatedAt = now
                            )
                        )
                    } else {
                        // Cache empty recognition result for unchanged scribble clusters so we don't re-infer repeatedly
                        if (text != null) {
                            nextSigCache[sig] = ""
                        }
                    }
                }

                clusterTextCache[noteId] = nextSigCache

                val prevSnapshot = lastIndexedSnapshots[noteId]
                if (mlKitCalls == 0 && prevSnapshot != null && prevSnapshot == nextSnapshot) {
                    return@launch
                }
                lastIndexedSnapshots[noteId] = nextSnapshot

                // 3. Atomically update the search database for this note
                searchDao.deleteForNote(noteId)
                if (entities.isNotEmpty()) {
                    searchDao.insertAll(entities)
                    Log.d(TAG, "Indexed ${entities.size} clusters ($mlKitCalls new ML Kit calls) for note $noteId")
                }

                // 4. Persist recognized text blocks into .bignote file only when text content changed
                noteStorageManager.updateNoteTextBlocks(noteId, textBlocks)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Error indexing note $noteId", e)
            } finally {
                activeIndexingJobs.remove(noteId)
            }
        }
        activeIndexingJobs[noteId] = job
    }

    /**
     * Executes keyword search across all indexed handwritten notes.
     * Returns a map of NoteId -> List of SearchHit.
     */
    suspend fun search(query: String): Map<String, List<SearchHit>> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyMap()

        val matchingEntities = searchDao.search(q)
        if (matchingEntities.isEmpty()) return@withContext emptyMap()

        val results = mutableMapOf<String, MutableList<SearchHit>>()
        for (entity in matchingEntities) {
            val snippet = generateSnippet(entity.recognizedText, q)
            val hit = SearchHit(
                noteId = entity.noteId,
                clusterId = entity.clusterId,
                matchedText = entity.recognizedText,
                query = q,
                minX = entity.minX,
                minY = entity.minY,
                maxX = entity.maxX,
                maxY = entity.maxY,
                snippet = snippet
            )
            results.getOrPut(entity.noteId) { mutableListOf() }.add(hit)
        }

        results
    }

    /**
     * Obtains recognized text for a given collection of strokes.
     * Tries on-device recognition directly; if unavailable or empty, matches against cached text blocks.
     */
    suspend fun getTextForStrokes(noteId: String, strokes: List<Stroke>): String = withContext(Dispatchers.Default) {
        if (strokes.isEmpty()) return@withContext ""

        // 1. Direct ML Kit recognition on the selected strokes
        val directText = recognizeStrokesDirect(strokes)
        if (directText.isNotBlank()) {
            return@withContext directText
        }

        // 2. Fallback: match against cached text blocks from Room
        val cachedEntities = withContext(Dispatchers.IO) { searchDao.getForNote(noteId) }
        if (cachedEntities.isEmpty()) return@withContext ""

        // Compute stroke selection bounding box
        var selMinX = Float.MAX_VALUE
        var selMinY = Float.MAX_VALUE
        var selMaxX = -Float.MAX_VALUE
        var selMaxY = -Float.MAX_VALUE
        for (stroke in strokes) {
            selMinX = min(selMinX, stroke.minX)
            selMinY = min(selMinY, stroke.minY)
            selMaxX = max(selMaxX, stroke.maxX)
            selMaxY = max(selMaxY, stroke.maxY)
        }

        // Find overlapping blocks
        val matchedBlocks = cachedEntities.filter { entity ->
            !(selMaxX < entity.minX || selMinX > entity.maxX || selMaxY < entity.minY || selMinY > entity.maxY)
        }.sortedWith(compareBy({ it.minY }, { it.minX }))

        matchedBlocks.joinToString(" ") { it.recognizedText }
    }

    /**
     * Direct recognition of arbitrary strokes without persisting to search index.
     */
    suspend fun recognizeStrokesDirect(strokes: List<Stroke>): String = withContext(Dispatchers.Default) {
        if (strokes.isEmpty()) return@withContext ""
        val clusters = StrokeClusterer.clusterStrokes("direct_rec", strokes)
        val textList = mutableListOf<String>()
        for (cluster in clusters) {
            val txt = recognitionEngine.recognizeCluster(cluster)
            if (!txt.isNullOrBlank()) {
                textList.add(txt)
            }
        }
        textList.joinToString(" ")
    }

    /**
     * Gets all text blocks currently indexed for a note.
     * Falls back to reading directly from the .bignote document file if Room cache is empty.
     */
    suspend fun getTextBlocks(noteId: String): List<RecognizedTextBlock> = withContext(Dispatchers.IO) {
        val roomBlocks = searchDao.getForNote(noteId).map { entity ->
            RecognizedTextBlock(
                clusterId = entity.clusterId,
                text = entity.recognizedText,
                minX = entity.minX,
                minY = entity.minY,
                maxX = entity.maxX,
                maxY = entity.maxY,
                updatedAt = entity.updatedAt
            )
        }
        if (roomBlocks.isNotEmpty()) {
            roomBlocks
        } else {
            noteStorageManager.loadNote(noteId)?.textBlocks ?: emptyList()
        }
    }

    suspend fun deleteNote(noteId: String) = withContext(Dispatchers.IO) {
        activeIndexingJobs.remove(noteId)?.cancel()
        clusterTextCache.remove(noteId)
        docBboxTextSeed.remove(noteId)
        lastIndexedSnapshots.remove(noteId)
        searchDao.deleteForNote(noteId)
    }

    suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        searchDao.deleteForFolder(folderId)
    }

    fun close() {
        recognitionEngine.close()
    }

    companion object {
        const val DEFAULT_INDEX_DEBOUNCE_MS = 5000L

        internal fun computeClusterSignature(cluster: StrokeCluster): String {
            val sb = StringBuilder(cluster.strokes.size * 28)
            for (s in cluster.strokes) {
                sb.append(s.id).append(':').append(s.points.size)
                val first = s.points.firstOrNull()
                val last = s.points.lastOrNull()
                if (first != null && last != null) {
                    sb.append(':')
                        .append(first.x.toBits()).append(',').append(first.y.toBits())
                        .append(',')
                        .append(last.x.toBits()).append(',').append(last.y.toBits())
                }
                sb.append(';')
            }
            return sb.toString()
        }

        private fun bboxKey(minX: Float, minY: Float, maxX: Float, maxY: Float): String {
            return "${minX.toBits()}:${minY.toBits()}:${maxX.toBits()}:${maxY.toBits()}"
        }

        fun generateSnippet(fullText: String, query: String, windowSize: Int = 24): String {
            val idx = fullText.indexOf(query, ignoreCase = true)
            if (idx == -1) {
                return if (fullText.length <= 40) fullText else fullText.take(40) + "..."
            }

            val start = max(0, idx - windowSize)
            val end = min(fullText.length, idx + query.length + windowSize)

            val prefix = if (start > 0) "..." else ""
            val suffix = if (end < fullText.length) "..." else ""
            return prefix + fullText.substring(start, end).trim() + suffix
        }
    }
}
