package com.antigrav.bignotes.core.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.antigrav.bignotes.ui.model.FolderItem
import com.antigrav.bignotes.ui.model.NoteItem
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32

data class SnapshotNoteEntry(
    val id: String,
    val folderId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val previewText: String = "",
    val blobId: String
)

data class SnapshotManifest(
    val id: String,
    val timestamp: Long,
    val formattedDate: String,
    val trigger: String, // e.g. "Manual Backup", "Auto (App Minimized)"
    val folders: List<FolderItem>,
    val notes: List<SnapshotNoteEntry>
)

data class TimelineSnapshotSummary(
    val id: String,
    val timestamp: Long,
    val formattedDate: String,
    val trigger: String,
    val totalNotes: Int,
    val totalFolders: Int,
    val summary: String = ""
)

data class TimelineIndex(
    val version: Int = 1,
    val lastUpdated: Long,
    val snapshots: List<TimelineSnapshotSummary>
)

data class BackupResult(
    val success: Boolean,
    val snapshotId: String? = null,
    val notesBackedUp: Int = 0,
    val newBlobsWritten: Int = 0,
    val message: String,
    val error: String? = null
)

data class RestoreResult(
    val success: Boolean,
    val snapshotId: String? = null,
    val notesRestored: Int = 0,
    val foldersRestored: Int = 0,
    val message: String,
    val restoredFolders: List<FolderItem> = emptyList(),
    val restoredNotes: List<NoteItem> = emptyList(),
    val error: String? = null
)

data class CherryPickResult(
    val success: Boolean,
    val restoredNote: NoteItem? = null,
    val restoredFolder: FolderItem? = null,
    val message: String,
    val error: String? = null
)

data class PruneResult(
    val success: Boolean,
    val deletedSnapshotsCount: Int = 0,
    val deletedBlobsCount: Int = 0,
    val message: String,
    val error: String? = null
)

object BackupManager {
    const val TIMELINE_INDEX_FILE = "timeline.json"
    const val SNAPSHOTS_DIR = "snapshots"
    const val OBJECTS_DIR = "objects"

    private const val MIME_BINARY = "application/octet-stream"
    private const val MIME_JSON = "application/json"

    fun formatTimestamp(epochMillis: Long): String {
        return try {
            val sdf = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault())
            sdf.format(Date(epochMillis))
        } catch (e: Exception) {
            "Unknown date"
        }
    }

    fun computeCrc32(bytes: ByteArray): String {
        val crc = CRC32()
        crc.update(bytes)
        return java.lang.Long.toHexString(crc.value)
    }

    fun getFolderDisplayName(context: Context, treeUri: Uri): String {
        return try {
            val docFile = DocumentFile.fromTreeUri(context, treeUri)
            val name = docFile?.name
            if (!name.isNullOrBlank()) return name

            val path = treeUri.path ?: return "Backup Folder"
            val lastPart = path.substringAfterLast(':').substringAfterLast('/')
            if (lastPart.isNotBlank()) lastPart else "Backup Folder"
        } catch (e: Exception) {
            "Backup Folder"
        }
    }

    private fun getOrCreateSubdirectory(parent: DocumentFile, dirName: String): DocumentFile? {
        val existing = parent.findFile(dirName)
        if (existing != null && existing.isDirectory) return existing
        return parent.createDirectory(dirName)
    }

    fun serializeTimelineIndex(index: TimelineIndex): String {
        val root = JSONObject()
        root.put("version", index.version)
        root.put("lastUpdated", index.lastUpdated)

        val array = JSONArray()
        for (item in index.snapshots) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("timestamp", item.timestamp)
            obj.put("formattedDate", item.formattedDate)
            obj.put("trigger", item.trigger)
            obj.put("totalNotes", item.totalNotes)
            obj.put("totalFolders", item.totalFolders)
            obj.put("summary", item.summary)
            array.put(obj)
        }
        root.put("snapshots", array)
        return root.toString(2)
    }

    fun deserializeTimelineIndex(jsonStr: String): TimelineIndex? {
        return try {
            val root = JSONObject(jsonStr)
            val version = root.optInt("version", 1)
            val lastUpdated = root.optLong("lastUpdated", System.currentTimeMillis())
            val array = root.optJSONArray("snapshots") ?: JSONArray()

            val list = mutableListOf<TimelineSnapshotSummary>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TimelineSnapshotSummary(
                        id = obj.getString("id"),
                        timestamp = obj.optLong("timestamp", 0L),
                        formattedDate = obj.optString("formattedDate", ""),
                        trigger = obj.optString("trigger", "Manual Backup"),
                        totalNotes = obj.optInt("totalNotes", 0),
                        totalFolders = obj.optInt("totalFolders", 0),
                        summary = obj.optString("summary", "")
                    )
                )
            }
            TimelineIndex(version = version, lastUpdated = lastUpdated, snapshots = list)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun serializeSnapshot(manifest: SnapshotManifest): String {
        val root = JSONObject()
        root.put("id", manifest.id)
        root.put("timestamp", manifest.timestamp)
        root.put("formattedDate", manifest.formattedDate)
        root.put("trigger", manifest.trigger)

        val foldersArray = JSONArray()
        for (f in manifest.folders) {
            val obj = JSONObject()
            obj.put("id", f.id)
            obj.put("name", f.name)
            foldersArray.put(obj)
        }
        root.put("folders", foldersArray)

        val notesArray = JSONArray()
        for (n in manifest.notes) {
            val obj = JSONObject()
            obj.put("id", n.id)
            obj.put("folderId", n.folderId)
            obj.put("title", n.title)
            obj.put("createdAt", n.createdAt)
            obj.put("updatedAt", n.updatedAt)
            obj.put("previewText", n.previewText)
            obj.put("blobId", n.blobId)
            notesArray.put(obj)
        }
        root.put("notes", notesArray)

        return root.toString(2)
    }

    fun deserializeSnapshot(jsonStr: String): SnapshotManifest? {
        return try {
            val root = JSONObject(jsonStr)
            val id = root.getString("id")
            val timestamp = root.optLong("timestamp", System.currentTimeMillis())
            val formattedDate = root.optString("formattedDate", formatTimestamp(timestamp))
            val trigger = root.optString("trigger", "Manual Backup")

            val foldersList = mutableListOf<FolderItem>()
            val foldersArray = root.optJSONArray("folders")
            if (foldersArray != null) {
                for (i in 0 until foldersArray.length()) {
                    val obj = foldersArray.getJSONObject(i)
                    foldersList.add(
                        FolderItem(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            noteCount = 0
                        )
                    )
                }
            }

            val notesList = mutableListOf<SnapshotNoteEntry>()
            val notesArray = root.optJSONArray("notes")
            if (notesArray != null) {
                for (i in 0 until notesArray.length()) {
                    val obj = notesArray.getJSONObject(i)
                    notesList.add(
                        SnapshotNoteEntry(
                            id = obj.getString("id"),
                            folderId = obj.optString("folderId", "folder_main"),
                            title = obj.optString("title", "Untitled Note"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                            previewText = obj.optString("previewText", ""),
                            blobId = obj.optString("blobId", "${obj.getString("id")}.bignote")
                        )
                    )
                }
            }

            SnapshotManifest(
                id = id,
                timestamp = timestamp,
                formattedDate = formattedDate,
                trigger = trigger,
                folders = foldersList,
                notes = notesList
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createSnapshot(
        context: Context,
        treeUri: Uri,
        folders: List<FolderItem>,
        notes: List<NoteItem>,
        storageManager: NoteStorageManager,
        trigger: String = "Manual Backup"
    ): BackupResult {
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: return BackupResult(false, message = "Cannot access backup folder", error = "DocumentFile null")

            if (!rootDoc.canWrite()) {
                return BackupResult(false, message = "Backup folder is read-only", error = "Not writable")
            }

            val objectsDir = getOrCreateSubdirectory(rootDoc, OBJECTS_DIR)
                ?: return BackupResult(false, message = "Cannot create objects pool", error = "Objects dir null")

            val snapshotsDir = getOrCreateSubdirectory(rootDoc, SNAPSHOTS_DIR)
                ?: return BackupResult(false, message = "Cannot create snapshots directory", error = "Snapshots dir null")

            // Cache existing object blobs to eliminate redundant network/disk writes
            val existingBlobs = objectsDir.listFiles().associateBy { it.name ?: "" }

            var newBlobsWritten = 0
            val snapshotNotes = mutableListOf<SnapshotNoteEntry>()

            for (note in notes) {
                val localFile = storageManager.getFileForNote(note.id)
                if (!localFile.exists()) continue

                val bytes = localFile.readBytes()
                val crc = computeCrc32(bytes)
                val blobId = "${note.id}_${crc}.bignote"

                // Deduplication: only write blob if not already in objects pool
                if (!existingBlobs.containsKey(blobId)) {
                    val newBlobDoc = objectsDir.createFile(MIME_BINARY, blobId)
                    if (newBlobDoc != null) {
                        context.contentResolver.openOutputStream(newBlobDoc.uri, "wt")?.use { out ->
                            out.write(bytes)
                            out.flush()
                        }
                        newBlobsWritten++
                    }
                }

                snapshotNotes.add(
                    SnapshotNoteEntry(
                        id = note.id,
                        folderId = note.folderId,
                        title = note.title,
                        createdAt = note.createdAt,
                        updatedAt = note.updatedAt,
                        previewText = note.previewText,
                        blobId = blobId
                    )
                )
            }

            val now = System.currentTimeMillis()
            val snapshotId = "snap_$now"
            val formattedDate = formatTimestamp(now)

            val manifest = SnapshotManifest(
                id = snapshotId,
                timestamp = now,
                formattedDate = formattedDate,
                trigger = trigger,
                folders = folders,
                notes = snapshotNotes
            )

            // Write snapshot file
            val snapshotFileName = "$snapshotId.json"
            val snapshotDoc = snapshotsDir.createFile(MIME_JSON, snapshotFileName)
            if (snapshotDoc != null) {
                val jsonStr = serializeSnapshot(manifest)
                context.contentResolver.openOutputStream(snapshotDoc.uri, "wt")?.use { out ->
                    out.write(jsonStr.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }

            // Update timeline index
            val existingTimeline = getTimelineIndex(context, rootDoc)
            val titlesSummary = if (snapshotNotes.isEmpty()) {
                "0 notes (Empty state)"
            } else {
                snapshotNotes.take(3).joinToString(", ") { it.title } + if (snapshotNotes.size > 3) " +${snapshotNotes.size - 3} more" else ""
            }

            val newSummary = TimelineSnapshotSummary(
                id = snapshotId,
                timestamp = now,
                formattedDate = formattedDate,
                trigger = trigger,
                totalNotes = snapshotNotes.size,
                totalFolders = folders.size,
                summary = titlesSummary
            )

            val updatedSnapshots = listOf(newSummary) + (existingTimeline?.snapshots ?: emptyList())
            val updatedTimeline = TimelineIndex(
                version = 1,
                lastUpdated = now,
                snapshots = updatedSnapshots
            )

            val timelineDoc = rootDoc.findFile(TIMELINE_INDEX_FILE)
                ?: rootDoc.createFile(MIME_JSON, TIMELINE_INDEX_FILE)

            if (timelineDoc != null) {
                val jsonStr = serializeTimelineIndex(updatedTimeline)
                context.contentResolver.openOutputStream(timelineDoc.uri, "wt")?.use { out ->
                    out.write(jsonStr.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }

            BackupResult(
                success = true,
                snapshotId = snapshotId,
                notesBackedUp = snapshotNotes.size,
                newBlobsWritten = newBlobsWritten,
                message = "Snapshot created: ${snapshotNotes.size} note${if (snapshotNotes.size == 1) "" else "s"} ($newBlobsWritten new blob${if (newBlobsWritten == 1) "" else "s"})."
            )
        } catch (e: Exception) {
            e.printStackTrace()
            BackupResult(false, message = "Snapshot failed: ${e.localizedMessage}", error = e.message)
        }
    }

    private fun getTimelineIndex(context: Context, rootDoc: DocumentFile): TimelineIndex? {
        val fileDoc = rootDoc.findFile(TIMELINE_INDEX_FILE) ?: return null
        return try {
            val text = context.contentResolver.openInputStream(fileDoc.uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return null
            deserializeTimelineIndex(text)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getTimeline(context: Context, treeUri: Uri): List<TimelineSnapshotSummary> {
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
            val index = getTimelineIndex(context, rootDoc)
            if (index != null && index.snapshots.isNotEmpty()) {
                return index.snapshots
            }

            // Fallback: scan snapshots directory if timeline.json missing
            val snapshotsDir = rootDoc.findFile(SNAPSHOTS_DIR)
            if (snapshotsDir != null && snapshotsDir.isDirectory) {
                val files = snapshotsDir.listFiles().filter { it.name?.endsWith(".json") == true }
                val summaries = mutableListOf<TimelineSnapshotSummary>()
                for (f in files) {
                    val text = context.contentResolver.openInputStream(f.uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: continue
                    val manifest = deserializeSnapshot(text) ?: continue
                    val titlesSummary = manifest.notes.take(3).joinToString(", ") { it.title }
                    summaries.add(
                        TimelineSnapshotSummary(
                            id = manifest.id,
                            timestamp = manifest.timestamp,
                            formattedDate = manifest.formattedDate,
                            trigger = manifest.trigger,
                            totalNotes = manifest.notes.size,
                            totalFolders = manifest.folders.size,
                            summary = titlesSummary
                        )
                    )
                }
                summaries.sortedByDescending { it.timestamp }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getSnapshotDetail(context: Context, treeUri: Uri, snapshotId: String): SnapshotManifest? {
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            val snapshotsDir = rootDoc.findFile(SNAPSHOTS_DIR) ?: return null
            val snapDoc = snapshotsDir.findFile("$snapshotId.json") ?: return null

            val text = context.contentResolver.openInputStream(snapDoc.uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return null

            deserializeSnapshot(text)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun restoreSnapshot(
        context: Context,
        treeUri: Uri,
        snapshotId: String,
        storageManager: NoteStorageManager
    ): RestoreResult {
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: return RestoreResult(false, message = "Cannot access backup folder", error = "DocumentFile null")

            val manifest = getSnapshotDetail(context, treeUri, snapshotId)
                ?: return RestoreResult(false, message = "Snapshot descriptor not found", error = "Manifest null")

            val objectsDir = rootDoc.findFile(OBJECTS_DIR)
                ?: return RestoreResult(false, message = "Objects directory not found", error = "Objects dir missing")

            val objectFiles = objectsDir.listFiles().associateBy { it.name ?: "" }

            var restoredCount = 0
            val restoredNotes = mutableListOf<NoteItem>()

            for (entry in manifest.notes) {
                val blobDoc = objectFiles[entry.blobId] ?: objectFiles["${entry.id}.bignote"] ?: continue
                val bytes = context.contentResolver.openInputStream(blobDoc.uri)?.use { it.readBytes() } ?: continue

                val destFile = storageManager.getFileForNote(entry.id)
                destFile.writeBytes(bytes)
                restoredCount++

                val parsedDoc = try {
                    BigNoteBinaryFormat.deserialize(bytes)
                } catch (e: Exception) {
                    null
                }

                val title = parsedDoc?.title ?: entry.title
                val folderId = parsedDoc?.folderId ?: entry.folderId
                val createdAt = parsedDoc?.createdAt ?: entry.createdAt
                val updatedAt = parsedDoc?.updatedAt ?: entry.updatedAt
                val strokesCount = parsedDoc?.strokes?.size ?: 0
                val preview = if (strokesCount == 0) "Empty note" else "$strokesCount stroke${if (strokesCount == 1) "" else "s"}"

                restoredNotes.add(
                    NoteItem(
                        id = entry.id,
                        folderId = folderId,
                        title = title,
                        previewText = preview,
                        createdAt = createdAt,
                        updatedAt = updatedAt
                    )
                )
            }

            val folderMap = manifest.folders.associateBy { it.id }.toMutableMap()
            if (!folderMap.containsKey("folder_main")) {
                folderMap["folder_main"] = FolderItem(id = "folder_main", name = "Main", noteCount = 0)
            }

            for (n in restoredNotes) {
                if (!folderMap.containsKey(n.folderId)) {
                    folderMap[n.folderId] = FolderItem(id = n.folderId, name = "Restored Folder", noteCount = 0)
                }
            }

            val folderCounts = restoredNotes.groupingBy { it.folderId }.eachCount()
            val finalFolders = folderMap.values.map { f ->
                f.copy(noteCount = folderCounts[f.id] ?: 0)
            }

            storageManager.saveFolders(finalFolders)

            RestoreResult(
                success = true,
                snapshotId = snapshotId,
                notesRestored = restoredCount,
                foldersRestored = finalFolders.size,
                restoredFolders = finalFolders,
                restoredNotes = restoredNotes,
                message = "Restored entire state to ${manifest.formattedDate} ($restoredCount notes)."
            )
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult(false, message = "Restore failed: ${e.localizedMessage}", error = e.message)
        }
    }

    fun cherryPickNote(
        context: Context,
        treeUri: Uri,
        snapshotId: String,
        noteId: String,
        storageManager: NoteStorageManager
    ): CherryPickResult {
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: return CherryPickResult(false, message = "Cannot access backup folder", error = "DocumentFile null")

            val manifest = getSnapshotDetail(context, treeUri, snapshotId)
                ?: return CherryPickResult(false, message = "Snapshot descriptor not found", error = "Manifest null")

            val noteEntry = manifest.notes.find { it.id == noteId }
                ?: return CherryPickResult(false, message = "Note not found in this snapshot", error = "Note missing")

            val objectsDir = rootDoc.findFile(OBJECTS_DIR)
                ?: return CherryPickResult(false, message = "Objects directory not found", error = "Objects dir missing")

            val objectFiles = objectsDir.listFiles().associateBy { it.name ?: "" }
            val blobDoc = objectFiles[noteEntry.blobId] ?: objectFiles["${noteEntry.id}.bignote"]
                ?: return CherryPickResult(false, message = "Note data blob not found in objects pool", error = "Blob missing")

            val bytes = context.contentResolver.openInputStream(blobDoc.uri)?.use { it.readBytes() }
                ?: return CherryPickResult(false, message = "Failed to read note blob", error = "Read stream error")

            // Write into app storage
            val destFile = storageManager.getFileForNote(noteEntry.id)
            destFile.writeBytes(bytes)

            val parsedDoc = try {
                BigNoteBinaryFormat.deserialize(bytes)
            } catch (e: Exception) {
                null
            }

            val title = parsedDoc?.title ?: noteEntry.title
            val folderId = parsedDoc?.folderId ?: noteEntry.folderId
            val createdAt = parsedDoc?.createdAt ?: noteEntry.createdAt
            val updatedAt = parsedDoc?.updatedAt ?: noteEntry.updatedAt
            val strokesCount = parsedDoc?.strokes?.size ?: 0
            val preview = if (strokesCount == 0) "Empty note" else "$strokesCount stroke${if (strokesCount == 1) "" else "s"}"

            val restoredNote = NoteItem(
                id = noteEntry.id,
                folderId = folderId,
                title = title,
                previewText = preview,
                createdAt = createdAt,
                updatedAt = updatedAt
            )

            val restoredFolder = manifest.folders.find { it.id == folderId }
                ?: FolderItem(id = folderId, name = "Restored Folder", noteCount = 1)

            CherryPickResult(
                success = true,
                restoredNote = restoredNote,
                restoredFolder = restoredFolder,
                message = "Successfully restored '${restoredNote.title}'."
            )
        } catch (e: Exception) {
            e.printStackTrace()
            CherryPickResult(false, message = "Cherry-pick failed: ${e.localizedMessage}", error = e.message)
        }
    }

    fun deleteSnapshot(
        context: Context,
        treeUri: Uri,
        snapshotId: String
    ): PruneResult {
        return pruneSnapshotsInternal(context, treeUri) { id, _, _ -> id == snapshotId }
    }

    fun pruneSnapshots(
        context: Context,
        treeUri: Uri,
        keepLatestCount: Int? = null,
        olderThanDays: Int? = null
    ): PruneResult {
        val now = System.currentTimeMillis()
        val cutoffMillis = olderThanDays?.let { now - it * 24L * 60L * 60L * 1000L }

        return pruneSnapshotsInternal(context, treeUri) { _, index, timestamp ->
            var shouldDelete = false
            if (keepLatestCount != null && index >= keepLatestCount) {
                shouldDelete = true
            }
            if (cutoffMillis != null && timestamp < cutoffMillis) {
                shouldDelete = true
            }
            shouldDelete
        }
    }

    private fun pruneSnapshotsInternal(
        context: Context,
        treeUri: Uri,
        shouldDeletePredicate: (id: String, index: Int, timestamp: Long) -> Boolean
    ): PruneResult {
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: return PruneResult(false, message = "Cannot access backup folder", error = "DocumentFile null")

            val snapshotsDir = rootDoc.findFile(SNAPSHOTS_DIR)
                ?: return PruneResult(false, message = "Snapshots directory not found", error = "Snapshots dir null")

            val timelineIndex = getTimelineIndex(context, rootDoc)
                ?: return PruneResult(false, message = "Timeline index not found", error = "Index null")

            val toDelete = mutableListOf<TimelineSnapshotSummary>()
            val toKeep = mutableListOf<TimelineSnapshotSummary>()

            timelineIndex.snapshots.forEachIndexed { index, summary ->
                if (shouldDeletePredicate(summary.id, index, summary.timestamp)) {
                    toDelete.add(summary)
                } else {
                    toKeep.add(summary)
                }
            }

            if (toDelete.isEmpty()) {
                return PruneResult(true, deletedSnapshotsCount = 0, deletedBlobsCount = 0, message = "No snapshots matched prune criteria.")
            }

            // 1. Delete matching snapshot descriptor files
            val snapshotFiles = snapshotsDir.listFiles().associateBy { it.name ?: "" }
            var deletedSnapsCount = 0
            for (snap in toDelete) {
                val fileName = "${snap.id}.json"
                val fileDoc = snapshotFiles[fileName]
                if (fileDoc != null && fileDoc.delete()) {
                    deletedSnapsCount++
                }
            }

            // 2. Update timeline.json
            val updatedTimeline = timelineIndex.copy(
                lastUpdated = System.currentTimeMillis(),
                snapshots = toKeep
            )
            val timelineDoc = rootDoc.findFile(TIMELINE_INDEX_FILE)
            if (timelineDoc != null) {
                val jsonStr = serializeTimelineIndex(updatedTimeline)
                context.contentResolver.openOutputStream(timelineDoc.uri, "wt")?.use { out ->
                    out.write(jsonStr.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }

            // 3. Garbage collect orphaned objects in objects/ pool
            val deletedBlobs = garbageCollectObjects(context, rootDoc, snapshotsDir, toKeep)

            PruneResult(
                success = true,
                deletedSnapshotsCount = deletedSnapsCount,
                deletedBlobsCount = deletedBlobs,
                message = "Pruned $deletedSnapsCount snapshot${if (deletedSnapsCount == 1) "" else "s"} and removed $deletedBlobs unreferenced object${if (deletedBlobs == 1) "" else "s"}."
            )
        } catch (e: Exception) {
            e.printStackTrace()
            PruneResult(false, message = "Prune failed: ${e.localizedMessage}", error = e.message)
        }
    }

    private fun garbageCollectObjects(
        context: Context,
        rootDoc: DocumentFile,
        snapshotsDir: DocumentFile,
        activeSnapshots: List<TimelineSnapshotSummary>
    ): Int {
        val objectsDir = rootDoc.findFile(OBJECTS_DIR) ?: return 0
        val activeBlobIds = mutableSetOf<String>()

        val snapshotFiles = snapshotsDir.listFiles().associateBy { it.name ?: "" }
        for (snap in activeSnapshots) {
            val fileDoc = snapshotFiles["${snap.id}.json"] ?: continue
            val manifestText = context.contentResolver.openInputStream(fileDoc.uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: continue
            val manifest = deserializeSnapshot(manifestText) ?: continue
            for (note in manifest.notes) {
                if (note.blobId.isNotBlank()) {
                    activeBlobIds.add(note.blobId)
                }
                activeBlobIds.add("${note.id}.bignote")
            }
        }

        var deletedBlobs = 0
        for (blobDoc in objectsDir.listFiles()) {
            val name = blobDoc.name ?: continue
            if (!activeBlobIds.contains(name)) {
                if (blobDoc.delete()) {
                    deletedBlobs++
                }
            }
        }
        return deletedBlobs
    }
}
