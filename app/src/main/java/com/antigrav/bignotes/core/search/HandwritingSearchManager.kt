package com.antigrav.bignotes.core.search

import android.content.Context
import android.util.Log
import com.antigrav.bignotes.core.model.Stroke
import com.antigrav.bignotes.core.search.clustering.StrokeClusterer
import com.antigrav.bignotes.core.search.db.HandwritingSearchDao
import com.antigrav.bignotes.core.search.db.HandwritingSearchEntity
import com.antigrav.bignotes.core.search.db.SearchDatabase
import com.antigrav.bignotes.core.search.model.ModelDownloadStatus
import com.antigrav.bignotes.core.search.model.SearchHit
import com.antigrav.bignotes.core.search.recognition.DigitalInkRecognitionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Coordinates stroke clustering, asynchronous ML Kit handwriting recognition,
 * Room database indexing, and full-text keyword querying.
 */
class HandwritingSearchManager(
    context: Context,
    private val recognitionEngine: DigitalInkRecognitionEngine = DigitalInkRecognitionEngine()
) {
    private val TAG = "HandwritingSearchMgr"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val searchDao: HandwritingSearchDao = SearchDatabase.getInstance(context).handwritingSearchDao()

    private var activeIndexingJob: Job? = null

    val downloadStatus: StateFlow<ModelDownloadStatus> = recognitionEngine.downloadStatus

    fun downloadModel(onComplete: ((Boolean) -> Unit)? = null) {
        recognitionEngine.downloadModelAsync(onComplete)
    }

    /**
     * Clusters strokes and indexes recognized handwriting for the specified note asynchronously in the background.
     */
    fun indexNoteAsync(noteId: String, folderId: String, strokes: List<Stroke>) {
        activeIndexingJob?.cancel()
        activeIndexingJob = scope.launch {
            try {
                // 1. Spatially cluster ink strokes into lines/phrases
                val clusters = withContext(Dispatchers.Default) {
                    StrokeClusterer.clusterStrokes(noteId, strokes)
                }

                if (clusters.isEmpty()) {
                    searchDao.deleteForNote(noteId)
                    return@launch
                }

                // 2. Perform ML Kit recognition for each cluster
                val entities = mutableListOf<HandwritingSearchEntity>()
                val now = System.currentTimeMillis()

                for (cluster in clusters) {
                    val text = recognitionEngine.recognizeCluster(cluster)
                    if (!text.isNullOrBlank()) {
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
                    }
                }

                // 3. Atomically update the search database for this note
                searchDao.deleteForNote(noteId)
                if (entities.isNotEmpty()) {
                    searchDao.insertAll(entities)
                    Log.d(TAG, "Indexed ${entities.size} handwriting clusters for note $noteId")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error indexing note $noteId", e)
            }
        }
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

    suspend fun deleteNote(noteId: String) = withContext(Dispatchers.IO) {
        searchDao.deleteForNote(noteId)
    }

    suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        searchDao.deleteForFolder(folderId)
    }

    fun close() {
        recognitionEngine.close()
    }

    companion object {
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
