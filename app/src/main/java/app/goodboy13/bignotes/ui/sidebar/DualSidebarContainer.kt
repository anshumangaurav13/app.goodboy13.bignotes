package app.goodboy13.bignotes.ui.sidebar

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.goodboy13.bignotes.ui.model.FolderItem
import app.goodboy13.bignotes.ui.model.NoteItem
import app.goodboy13.bignotes.ui.model.NoteSortOrder
import kotlin.math.roundToInt

object SidebarLayoutDefaults {
    fun folderWidth(screenWidthDp: Int, isPortrait: Boolean): Dp = when {
        screenWidthDp < 600 -> 130.dp
        isPortrait -> 155.dp
        else -> 180.dp
    }

    fun noteWidth(screenWidthDp: Int, isPortrait: Boolean): Dp = when {
        screenWidthDp < 600 -> 175.dp
        isPortrait -> 200.dp
        else -> 230.dp
    }

    fun totalWidth(screenWidthDp: Int, isPortrait: Boolean, isFoldersVisible: Boolean = true): Dp =
        (if (isFoldersVisible) folderWidth(screenWidthDp, isPortrait) else 0.dp) + noteWidth(screenWidthDp, isPortrait)
}

@Composable
fun DualSidebarContainer(
    isVisible: Boolean,
    folders: List<FolderItem>,
    selectedFolderId: String?,
    notes: List<NoteItem>,
    selectedNoteId: String?,
    sortOrder: NoteSortOrder = NoteSortOrder.MODIFIED_DESC,
    onSortOrderChange: (NoteSortOrder) -> Unit = {},
    onFolderSelect: (String) -> Unit,
    onFolderDelete: (String) -> Unit,
    onFolderRename: ((folderId: String, newName: String) -> Unit)? = null,
    onNoteSelect: (String) -> Unit,
    onNoteDelete: (String) -> Unit,
    onNoteRename: ((noteId: String, newTitle: String) -> Unit)? = null,
    onNoteExportPdf: ((NoteItem) -> Unit)? = null,
    onAddFolder: () -> Unit,
    onAddNote: () -> Unit,
    isFoldersVisible: Boolean = true,
    onToggleFoldersVisible: () -> Unit = {},
    onOpenSettings: () -> Unit,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    searchHits: Map<String, List<app.goodboy13.bignotes.core.search.model.SearchHit>> = emptyMap(),
    onSearchQueryChanged: ((String) -> Unit)? = null,
    onSearchHitSelected: ((app.goodboy13.bignotes.core.search.model.SearchHit) -> Unit)? = null,
    pageFormat: app.goodboy13.bignotes.core.export.PageFormat = app.goodboy13.bignotes.core.export.PageFormat.A4,
    margin: app.goodboy13.bignotes.core.export.PdfMargin = app.goodboy13.bignotes.core.export.PdfMargin.NONE,
    getPageCount: ((NoteItem) -> Int)? = null,
    statusBarHeight: Dp = 0.dp,
    foldersProgress: Float = if (isFoldersVisible) 1f else 0f,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val screenWidth = configuration.screenWidthDp

    val folderWidth = SidebarLayoutDefaults.folderWidth(screenWidth, isPortrait)
    val noteWidth = SidebarLayoutDefaults.noteWidth(screenWidth, isPortrait)
    val clampedFoldersProgress = foldersProgress.coerceIn(0f, 1f)
    val containerShape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp)

    Surface(
        modifier = modifier
            .fillMaxHeight(),
        shape = containerShape,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxHeight()
        ) {
            if (clampedFoldersProgress > 0.001f) {
                val dividerWidth = 0.5.dp
                val fullFolderPaneWidth = folderWidth + dividerWidth
                val fullFolderPanePx = with(density) { fullFolderPaneWidth.toPx() }
                Box(
                    modifier = Modifier
                        .width(fullFolderPaneWidth * clampedFoldersProgress)
                        .fillMaxHeight()
                        .clipToBounds()
                ) {
                    Row(
                        modifier = Modifier
                            .requiredWidth(fullFolderPaneWidth)
                            .fillMaxHeight()
                            .offset {
                                IntOffset(
                                    x = (-(1f - clampedFoldersProgress) * fullFolderPanePx).roundToInt(),
                                    y = 0
                                )
                            }
                    ) {
                        // Folders Column (maintains fixed width so content never squishes while sliding)
                        FoldersSidebar(
                            folders = folders,
                            selectedFolderId = selectedFolderId,
                            onFolderSelect = onFolderSelect,
                            onFolderDelete = onFolderDelete,
                            onFolderRename = onFolderRename,
                            onAddFolder = onAddFolder,
                            onToggleCollapse = onToggleFoldersVisible,
                            onOpenSettings = onOpenSettings,
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = onToggleDarkMode,
                            statusBarHeight = statusBarHeight,
                            modifier = Modifier
                                .width(folderWidth)
                                .fillMaxHeight()
                        )

                        // Vertical divider between Folders and Notes
                        VerticalDivider(
                            thickness = dividerWidth,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                        )
                    }
                }
            }

            // Notes Column (fixed width so it slides cleanly without squishing)
            NotesSidebar(
                notes = notes,
                selectedNoteId = selectedNoteId,
                sortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange,
                canAddNote = folders.isNotEmpty(),
                onNoteSelect = onNoteSelect,
                onNoteDelete = onNoteDelete,
                onNoteRename = onNoteRename,
                onNoteExportPdf = onNoteExportPdf,
                onAddNote = onAddNote,
                isFoldersVisible = isFoldersVisible,
                onToggleFoldersVisible = onToggleFoldersVisible,
                onOpenSettings = onOpenSettings,
                isDarkMode = isDarkMode,
                onToggleDarkMode = onToggleDarkMode,
                searchHits = searchHits,
                onSearchQueryChanged = onSearchQueryChanged,
                onSearchHitSelected = onSearchHitSelected,
                pageFormat = pageFormat,
                margin = margin,
                getPageCount = getPageCount,
                statusBarHeight = statusBarHeight,
                modifier = Modifier
                    .width(noteWidth)
                    .fillMaxHeight()
            )
        }
    }
}
