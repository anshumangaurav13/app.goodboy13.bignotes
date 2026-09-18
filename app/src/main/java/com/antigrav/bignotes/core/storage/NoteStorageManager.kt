package com.antigrav.bignotes.core.storage

import android.content.Context
import com.antigrav.bignotes.core.model.Stroke
import com.antigrav.bignotes.ui.model.NoteItem
import java.io.File

class NoteStorageManager(private val context: Context) {
    private val notesDir: File
        get() = File(context.filesDir, "bignotes").apply { if (!exists()) mkdirs() }

    fun getFileForNote(noteId: String): File {
        return File(notesDir, "$noteId.bignote")
    }

    val mediaDir: File
        get() = File(context.filesDir, "bignotes_media").apply { if (!exists()) mkdirs() }

    fun getImageFile(fileName: String): File {
        return File(mediaDir, fileName)
    }

    fun saveImageMedia(uri: android.net.Uri): Pair<String, Pair<Int, Int>>? {
        return try {
            val fileName = "img_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}.jpg"
            val destFile = File(mediaDir, fileName)

            var rotation = 0
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = android.media.ExifInterface(stream)
                    val orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                    rotation = when (orientation) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (e: Exception) {
                // Ignore EXIF errors
            }

            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            }

            val maxDim = 2048
            var sampleSize = 1
            val origW = options.outWidth
            val origH = options.outHeight
            if (origW <= 0 || origH <= 0) return null

            while (origW / sampleSize > maxDim || origH / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val finalBitmap = if (rotation != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = android.graphics.Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                rawBitmap.recycle()
                rotated
            } else {
                rawBitmap
            }

            destFile.outputStream().use { out ->
                finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
            }
            val finalW = finalBitmap.width
            val finalH = finalBitmap.height
            finalBitmap.recycle()

            Pair(fileName, Pair(finalW, finalH))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteImageMedia(fileName: String): Boolean {
        val file = File(mediaDir, fileName)
        return if (file.exists()) file.delete() else false
    }

    fun saveNote(
        note: NoteItem,
        strokes: List<Stroke>,
        zoom: Float = 1f,
        scrollY: Float = 0f,
        images: List<com.antigrav.bignotes.core.model.CanvasImage> = emptyList(),
        textBlocks: List<RecognizedTextBlock> = emptyList()
    ): File {
        val file = getFileForNote(note.id)
        val finalBlocks = if (textBlocks.isNotEmpty()) {
            textBlocks
        } else {
            loadNote(note.id)?.textBlocks ?: emptyList()
        }
        val doc = BigNoteDocument(
            noteId = note.id,
            folderId = note.folderId,
            title = note.title,
            createdAt = if (note.createdAt > 0L) note.createdAt else System.currentTimeMillis(),
            updatedAt = if (note.updatedAt > 0L) note.updatedAt else System.currentTimeMillis(),
            zoom = zoom,
            scrollY = scrollY,
            strokes = strokes,
            images = images,
            textBlocks = finalBlocks
        )
        val bytes = BigNoteBinaryFormat.serialize(doc)
        file.writeBytes(bytes)

        val count = strokes.size
        var maxY = 0f
        for (s in strokes) {
            val halfBase = s.baseWidth * 0.5f
            for (p in s.points) {
                val ptY = p.y + halfBase * p.pressure
                if (ptY > maxY) maxY = ptY
            }
        }
        val preview = if (count == 0) "Empty note" else "$count stroke${if (count == 1) "" else "s"}"
        val updatedItem = note.copy(
            previewText = preview,
            updatedAt = doc.updatedAt,
            maxY = maxY,
            strokeCount = count
        )
        val currentIndex = loadNotesIndex() ?: emptyList()
        val nextIndex = if (currentIndex.any { it.id == note.id }) {
            currentIndex.map { if (it.id == note.id) updatedItem else it }
        } else {
            currentIndex + updatedItem
        }
        saveNotesIndex(nextIndex)

        return file
    }

    fun loadNote(noteId: String): BigNoteDocument? {
        val file = getFileForNote(noteId)
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            BigNoteBinaryFormat.deserialize(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun updateNoteTextBlocks(noteId: String, textBlocks: List<RecognizedTextBlock>): Boolean {
        val file = getFileForNote(noteId)
        if (!file.exists()) return false
        return try {
            val doc = loadNote(noteId) ?: return false
            val updated = doc.copy(textBlocks = textBlocks)
            val bytes = BigNoteBinaryFormat.serialize(updated)
            file.writeBytes(bytes)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteNote(noteId: String): Boolean {
        val file = getFileForNote(noteId)
        val deleted = if (file.exists()) file.delete() else true
        val currentIndex = loadNotesIndex()
        if (currentIndex != null) {
            val updated = currentIndex.filter { it.id != noteId }
            saveNotesIndex(updated)
        }
        return deleted
    }

    fun listSavedNoteIds(): List<String> {
        return notesDir.listFiles { file -> file.extension == "bignote" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    fun readNoteMetadata(noteId: String): BigNoteMetadata? {
        val file = getFileForNote(noteId)
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            BigNoteBinaryFormat.deserializeMetadata(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveNotesIndex(notes: List<NoteItem>) {
        try {
            val jsonArray = org.json.JSONArray()
            for (n in notes) {
                val obj = org.json.JSONObject()
                obj.put("id", n.id)
                obj.put("folderId", n.folderId)
                obj.put("title", n.title)
                obj.put("previewText", n.previewText)
                obj.put("createdAt", n.createdAt)
                obj.put("updatedAt", n.updatedAt)
                obj.put("maxY", n.maxY.toDouble())
                obj.put("strokeCount", n.strokeCount)
                jsonArray.put(obj)
            }
            val indexFile = File(context.filesDir, "notes_index.json")
            indexFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadNotesIndex(): List<NoteItem>? {
        val indexFile = File(context.filesDir, "notes_index.json")
        if (!indexFile.exists()) return null
        return try {
            val text = indexFile.readText()
            val jsonArray = org.json.JSONArray(text)
            val list = mutableListOf<NoteItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    NoteItem(
                        id = obj.getString("id"),
                        folderId = obj.getString("folderId"),
                        title = obj.getString("title"),
                        previewText = obj.optString("previewText", ""),
                        createdAt = obj.optLong("createdAt", 0L),
                        updatedAt = obj.optLong("updatedAt", 0L),
                        maxY = obj.optDouble("maxY", 0.0).toFloat(),
                        strokeCount = obj.optInt("strokeCount", 0)
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadAllNotesMetadataFast(): List<NoteItem> {
        val cached = loadNotesIndex()
        if (cached != null && cached.isNotEmpty()) {
            return cached
        }
        val savedIds = listSavedNoteIds()
        if (savedIds.isEmpty()) return emptyList()

        val list = mutableListOf<NoteItem>()
        for (id in savedIds) {
            val meta = readNoteMetadata(id) ?: continue
            list.add(
                NoteItem(
                    id = meta.noteId,
                    folderId = meta.folderId,
                    title = meta.title,
                    previewText = "Note",
                    createdAt = meta.createdAt,
                    updatedAt = meta.updatedAt,
                    maxY = 0f,
                    strokeCount = 0
                )
            )
        }
        if (list.isNotEmpty()) {
            saveNotesIndex(list)
        }
        return list
    }

    fun saveFolders(folders: List<com.antigrav.bignotes.ui.model.FolderItem>) {
        try {
            val jsonArray = org.json.JSONArray()
            for (f in folders) {
                val obj = org.json.JSONObject()
                obj.put("id", f.id)
                obj.put("name", f.name)
                obj.put("createdAt", f.createdAt)
                jsonArray.put(obj)
            }
            val foldersFile = File(context.filesDir, "folders.json")
            foldersFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadFolders(): List<com.antigrav.bignotes.ui.model.FolderItem>? {
        val foldersFile = File(context.filesDir, "folders.json")
        if (!foldersFile.exists()) return null
        return try {
            val text = foldersFile.readText()
            val jsonArray = org.json.JSONArray(text)
            val list = mutableListOf<com.antigrav.bignotes.ui.model.FolderItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    com.antigrav.bignotes.ui.model.FolderItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        noteCount = 0,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Seeds initial "Main" folder and "Note 1" on a brand-new installation.
     * If folders.json already exists (even if empty because user deleted all folders),
     * it respects user choices and will not re-create deleted items.
     */
    fun initializeDefaultWorkspaceIfEmpty(): Pair<List<com.antigrav.bignotes.ui.model.FolderItem>, List<NoteItem>> {
        val foldersFile = File(context.filesDir, "folders.json")
        val indexFile = File(context.filesDir, "notes_index.json")
        val savedNotes = listSavedNoteIds()

        if (!foldersFile.exists() && !indexFile.exists() && savedNotes.isEmpty()) {
            val defaultFolder = com.antigrav.bignotes.ui.model.FolderItem(
                id = "folder_main",
                name = "Main",
                noteCount = 1,
                createdAt = System.currentTimeMillis()
            )
            val defaultNote = NoteItem(
                id = "note_1",
                folderId = "folder_main",
                title = "Note 1",
                previewText = "Empty note",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                maxY = 0f,
                strokeCount = 0
            )
            saveFolders(listOf(defaultFolder))
            saveNote(defaultNote, emptyList())
            return Pair(listOf(defaultFolder), listOf(defaultNote))
        }

        val loadedFolders = loadFolders() ?: emptyList()
        val loadedNotes = loadAllNotesMetadataFast()
        return Pair(loadedFolders, loadedNotes)
    }
}
