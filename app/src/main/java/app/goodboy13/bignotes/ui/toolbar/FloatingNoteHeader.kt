package app.goodboy13.bignotes.ui.toolbar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goodboy13.bignotes.ui.components.InPlaceRenameField

/**
 * Minimal, translucent floating header chip (Option A).
 * Anchored at the top-left, hosting sidebar navigation toggle,
 * breadcrumb note title (with in-place rename), and quick export.
 */
@Composable
fun FloatingNoteHeader(
    isSidebarVisible: Boolean,
    onToggleSidebar: () -> Unit,
    folderTitle: String,
    noteTitle: String,
    onRenameCurrentNote: ((String) -> Unit)? = null,
    onExportNote: (() -> Unit)? = null,
    sidebarOffsetDp: Dp = 0.dp,
    statusBarHeightDp: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    var isEditingTitle by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        shadowElevation = 3.dp,
        tonalElevation = 1.dp,
        modifier = modifier
            .padding(
                start = sidebarOffsetDp + 12.dp,
                top = statusBarHeightDp + 10.dp
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            // 1. Sidebar Toggle / Navigation Button
            IconButton(
                onClick = onToggleSidebar,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isSidebarVisible) Icons.AutoMirrored.Filled.ViewSidebar else Icons.Default.Menu,
                    contentDescription = if (isSidebarVisible) "Hide Sidebar" else "Show Sidebar",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 2. Folder / Note Title Breadcrumb
            if (isEditingTitle && onRenameCurrentNote != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = "$folderTitle / ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    InPlaceRenameField(
                        initialText = noteTitle,
                        onCommit = { newTitle ->
                            onRenameCurrentNote(newTitle)
                            isEditingTitle = false
                        },
                        onCancel = { isEditingTitle = false },
                        modifier = Modifier.widthIn(min = 90.dp, max = 200.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            if (onRenameCurrentNote != null) {
                                Modifier.clickable { isEditingTitle = true }
                            } else Modifier
                        )
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$folderTitle / ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = noteTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
