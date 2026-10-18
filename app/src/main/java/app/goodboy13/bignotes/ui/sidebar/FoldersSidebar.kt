package app.goodboy13.bignotes.ui.sidebar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import app.goodboy13.bignotes.ui.components.InPlaceRenameField
import app.goodboy13.bignotes.ui.components.SwipeToRevealItem
import app.goodboy13.bignotes.ui.model.FolderItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoldersSidebar(
    folders: List<FolderItem>,
    selectedFolderId: String?,
    onFolderSelect: (String) -> Unit,
    onFolderDelete: (String) -> Unit,
    onFolderRename: ((folderId: String, newName: String) -> Unit)? = null,
    onAddFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    onToggleCollapse: (() -> Unit)? = null,
    statusBarHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    var editingFolderId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (statusBarHeight > 0.dp) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
            )
        }
        // Folders Header (aligned with toolbar height)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onToggleCollapse != null) {
                    IconButton(
                        onClick = onToggleCollapse,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuOpen,
                            contentDescription = "Collapse Folders",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = "Folders",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAddFolder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "New Folder",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        // Folders List with iOS-style Swipe-to-Reveal Delete
        if (folders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No folders",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(folders, key = { it.id }) { folder ->
                    val isSelected = folder.id == selectedFolderId
                    val isEditing = folder.id == editingFolderId
                    SwipeToRevealItem(
                        onDelete = { onFolderDelete(folder.id) },
                        revealWidthDp = 48,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        FolderRowItem(
                            folder = folder,
                            isSelected = isSelected,
                            isEditing = isEditing,
                            onClick = { onFolderSelect(folder.id) },
                            onLongClick = { editingFolderId = folder.id },
                            onCommitRename = { newName ->
                                onFolderRename?.invoke(folder.id, newName)
                                editingFolderId = null
                            },
                            onCancelRename = { editingFolderId = null }
                        )
                    }
                }
            }
        }

        // Bottom Footer with Sleek Rounded Pill Dock (Theme Toggle & Settings)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleDarkMode,
                    modifier = Modifier.size(36.dp)
                ) {
                    AnimatedContent(
                        targetState = isDarkMode,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.8f))
                                .togetherWith(fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.8f))
                        },
                        label = "FoldersThemeToggleAnimation"
                    ) { dark ->
                        Icon(
                            imageVector = if (dark) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = if (dark) "Switch to Light Mode" else "Switch to Dark Mode",
                            tint = if (dark) Color(0xFFFFD54F) else Color(0xFFF57C00),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRowItem(
    folder: FolderItem,
    isSelected: Boolean,
    isEditing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCommitRename: (String) -> Unit,
    onCancelRename: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val highlightBorder = if (isSelected) {
        Modifier.border(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            shape = shape
        )
    } else {
        Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
            shape = shape
        )
    }

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    }

    val clickModifier = if (!isEditing) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier
    }

    val themePrimary = MaterialTheme.colorScheme.primary
    val folderColor = remember(folder.id, themePrimary) {
        FolderColorManager.getFolderColor(folder.id, themePrimary)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(highlightBorder)
            .background(backgroundColor)
            .then(clickModifier)
            .padding(horizontal = 9.dp, vertical = if (isEditing) 5.dp else 8.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(folderColor.copy(alpha = if (isSelected) 0.25f else 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                tint = folderColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(7.dp))

        if (isEditing) {
            InPlaceRenameField(
                initialText = folder.name,
                onCommit = onCommitRename,
                onCancel = onCancelRename,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            )
        } else {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp
                ),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (folder.noteCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "${folder.noteCount}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

object FolderColorManager {
    // 12 curated, distinct, stationery base colors
    private val BASE_COLORS = listOf(
        Color(0xFFE5983C), // Warm Amber
        Color(0xFF3B82F6), // Azure Blue
        Color(0xFF10B981), // Emerald Green
        Color(0xFFEC4899), // Rose Pink
        Color(0xFF8B5CF6), // Purple Amethyst
        Color(0xFFF97316), // Warm Coral
        Color(0xFF06B6D4), // Cyan Teal
        Color(0xFFE11D48), // Ruby Crimson
        Color(0xFF84CC16), // Lime Olive
        Color(0xFF6366F1), // Indigo Slate
        Color(0xFFD97706), // Golden Ochre
        Color(0xFF14B8A6)  // Turquoise
    )

    fun getFolderColor(folderId: String, themePrimary: Color): Color {
        val index = (folderId.hashCode().toLong() and 0x7FFFFFFF).toInt() % BASE_COLORS.size
        val base = BASE_COLORS[index]
        // 22% theme primary tint + 78% fixed base color: keeps folder identity rock-solid across themes
        val tintRatio = 0.22f
        val r = base.red * (1f - tintRatio) + themePrimary.red * tintRatio
        val g = base.green * (1f - tintRatio) + themePrimary.green * tintRatio
        val b = base.blue * (1f - tintRatio) + themePrimary.blue * tintRatio
        return Color(r, g, b, 1f)
    }
}

private fun formatCreationDateTime(millis: Long): String {
    if (millis <= 0L) return ""
    return try {
        val date = java.util.Date(millis)
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy, h:mm a", java.util.Locale.getDefault())
        sdf.format(date)
    } catch (e: Exception) {
        ""
    }
}

