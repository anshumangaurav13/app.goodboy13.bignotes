package com.antigrav.bignotes.ui.sidebar

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antigrav.bignotes.ui.model.FolderItem
import com.antigrav.bignotes.ui.model.NoteItem

object SidebarLayoutDefaults {
    fun folderWidth(screenWidthDp: Int, isPortrait: Boolean): Dp = when {
        screenWidthDp < 600 -> 110.dp
        isPortrait -> 135.dp
        else -> 160.dp
    }

    fun noteWidth(screenWidthDp: Int, isPortrait: Boolean): Dp = when {
        screenWidthDp < 600 -> 150.dp
        isPortrait -> 175.dp
        else -> 200.dp
    }

    fun totalWidth(screenWidthDp: Int, isPortrait: Boolean): Dp =
        folderWidth(screenWidthDp, isPortrait) + noteWidth(screenWidthDp, isPortrait)
}

@Composable
fun DualSidebarContainer(
    isVisible: Boolean,
    folders: List<FolderItem>,
    selectedFolderId: String?,
    notes: List<NoteItem>,
    selectedNoteId: String?,
    onFolderSelect: (String) -> Unit,
    onFolderDelete: (String) -> Unit,
    onFolderRename: ((folderId: String, newName: String) -> Unit)? = null,
    onNoteSelect: (String) -> Unit,
    onNoteDelete: (String) -> Unit,
    onNoteRename: ((noteId: String, newTitle: String) -> Unit)? = null,
    onNoteExportPdf: ((NoteItem) -> Unit)? = null,
    onAddFolder: () -> Unit,
    onAddNote: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelpAbout: () -> Unit = {},
    searchHits: Map<String, List<com.antigrav.bignotes.core.search.model.SearchHit>> = emptyMap(),
    onSearchQueryChanged: ((String) -> Unit)? = null,
    onSearchHitSelected: ((com.antigrav.bignotes.core.search.model.SearchHit) -> Unit)? = null,
    pageFormat: com.antigrav.bignotes.core.export.PageFormat = com.antigrav.bignotes.core.export.PageFormat.A4,
    getPageCount: ((NoteItem) -> Int)? = null,
    statusBarHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val screenWidth = configuration.screenWidthDp

    val folderWidth = SidebarLayoutDefaults.folderWidth(screenWidth, isPortrait)
    val noteWidth = SidebarLayoutDefaults.noteWidth(screenWidth, isPortrait)
    val totalWidth = SidebarLayoutDefaults.totalWidth(screenWidth, isPortrait)

    val containerShape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp)

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(totalWidth),
        shape = containerShape,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Folders Column
            FoldersSidebar(
                folders = folders,
                selectedFolderId = selectedFolderId,
                onFolderSelect = onFolderSelect,
                onFolderDelete = onFolderDelete,
                onFolderRename = onFolderRename,
                onAddFolder = onAddFolder,
                onOpenSettings = onOpenSettings,
                onOpenHelpAbout = onOpenHelpAbout,
                statusBarHeight = statusBarHeight,
                modifier = Modifier
                    .width(folderWidth)
                    .fillMaxHeight()
            )

            // Vertical divider between Folders and Notes
            VerticalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            )

            // Notes Column
            NotesSidebar(
                notes = notes,
                selectedNoteId = selectedNoteId,
                onNoteSelect = onNoteSelect,
                onNoteDelete = onNoteDelete,
                onNoteRename = onNoteRename,
                onNoteExportPdf = onNoteExportPdf,
                onAddNote = onAddNote,
                searchHits = searchHits,
                onSearchQueryChanged = onSearchQueryChanged,
                onSearchHitSelected = onSearchHitSelected,
                pageFormat = pageFormat,
                getPageCount = getPageCount,
                statusBarHeight = statusBarHeight,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}
