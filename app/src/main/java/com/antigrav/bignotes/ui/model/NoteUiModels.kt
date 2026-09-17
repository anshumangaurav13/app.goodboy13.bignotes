package com.antigrav.bignotes.ui.model

data class FolderItem(
    val id: String,
    val name: String,
    val noteCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class NoteItem(
    val id: String,
    val folderId: String,
    val title: String,
    val previewText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val maxY: Float = 0f,
    val strokeCount: Int = 0
)

object MockData {
    val sampleFolders = listOf(
        FolderItem(id = "folder_1", name = "Folder 1", noteCount = 3),
        FolderItem(id = "folder_2", name = "Folder 2", noteCount = 4),
        FolderItem(id = "folder_3", name = "Ideas & Sketches", noteCount = 5),
        FolderItem(id = "folder_4", name = "Project Architecture", noteCount = 2)
    )

    val sampleNotes = listOf(
        NoteItem(
            id = "note_1",
            folderId = "folder_1",
            title = "Note 1",
            previewText = "Draft outline for the new drawing canvas engine...",
            createdAt = System.currentTimeMillis() - 86400000L * 2,
            updatedAt = System.currentTimeMillis() - 60000L * 5
        ),
        NoteItem(
            id = "note_2",
            folderId = "folder_1",
            title = "Note 2",
            previewText = "Infinite vertical scrolling notes with 100%-200% zoom...",
            createdAt = System.currentTimeMillis() - 86400000L,
            updatedAt = System.currentTimeMillis() - 60000L * 30
        ),
        NoteItem(
            id = "note_3",
            folderId = "folder_1",
            title = "Quick Sketch",
            previewText = "Diagram coordinates and layout scaling tests...",
            createdAt = System.currentTimeMillis() - 86400000L * 3,
            updatedAt = System.currentTimeMillis() - 3600000L * 2
        ),
        NoteItem(
            id = "note_4",
            folderId = "folder_2",
            title = "Research Reference",
            previewText = "Comparing Catmull-Rom tessellation and stylus latency...",
            createdAt = System.currentTimeMillis() - 86400000L * 5,
            updatedAt = System.currentTimeMillis() - 86400000L * 2
        ),
        NoteItem(
            id = "note_5",
            folderId = "folder_2",
            title = "Action Items",
            previewText = "1. Skeleton UI\n2. Hook up Sketchman engine\n3. Restrict downward pan",
            createdAt = System.currentTimeMillis() - 86400000L * 7,
            updatedAt = System.currentTimeMillis() - 86400000L * 4
        )
    )
}
