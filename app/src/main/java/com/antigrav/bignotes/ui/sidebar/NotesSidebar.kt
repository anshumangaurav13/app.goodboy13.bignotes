package com.antigrav.bignotes.ui.sidebar

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
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOff
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
import androidx.compose.ui.unit.sp
import com.antigrav.bignotes.ui.components.InPlaceRenameField
import com.antigrav.bignotes.ui.components.SwipeToRevealItem
import com.antigrav.bignotes.ui.model.NoteItem
import com.antigrav.bignotes.core.export.PageFormat
import com.antigrav.bignotes.core.export.PdfPagePaginator
import com.antigrav.bignotes.core.search.model.SearchHit
import kotlin.math.ceil
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesSidebar(
    notes: List<NoteItem>,
    selectedNoteId: String?,
    onNoteSelect: (String) -> Unit,
    onNoteDelete: (String) -> Unit,
    onNoteRename: ((noteId: String, newTitle: String) -> Unit)? = null,
    onNoteExportPdf: ((NoteItem) -> Unit)? = null,
    onAddNote: () -> Unit,
    canAddNote: Boolean = true,
    searchHits: Map<String, List<SearchHit>> = emptyMap(),
    onSearchQueryChanged: ((String) -> Unit)? = null,
    onSearchHitSelected: ((SearchHit) -> Unit)? = null,
    pageFormat: PageFormat = PageFormat.A4,
    getPageCount: ((NoteItem) -> Int)? = null,
    statusBarHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredNotes = remember(notes, searchQuery, searchHits) {
        if (searchQuery.isBlank()) notes
        else {
            val q = searchQuery.trim().lowercase()
            notes.filter { note ->
                note.title.lowercase().contains(q) ||
                formatCreationDateTime(note.createdAt).lowercase().contains(q) ||
                searchHits.containsKey(note.id)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (statusBarHeight > 0.dp) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
            )
        }
        // Notes Header (aligned with toolbar height)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Notes",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            val addNoteBgColor = if (canAddNote) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
            val addNoteIconTint = if (canAddNote) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            }
            Surface(
                shape = CircleShape,
                color = addNoteBgColor,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .then(if (canAddNote) Modifier.clickable(onClick = onAddNote) else Modifier)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                        contentDescription = if (canAddNote) "New Note" else "Create a folder first",
                        tint = addNoteIconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        // Compact Searchbar
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .height(34.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Notes",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearchQueryChanged?.invoke(it)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search notes & handwriting...",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            onSearchQueryChanged?.invoke("")
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Notes List with iOS Swipe-to-Reveal Delete
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (canAddNote) Icons.Default.Description else Icons.Default.FolderOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = if (canAddNote) "No notes yet" else "Create a folder first",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    if (!canAddNote) {
                        Text(
                            text = "Notes must belong to a folder",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }
                    FilledTonalButton(
                        onClick = onAddNote,
                        enabled = canAddNote,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("New Note", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else if (filteredNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
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
                items(filteredNotes, key = { it.id }) { note ->
                    val isSelected = note.id == selectedNoteId
                    val isEditing = note.id == editingNoteId
                    SwipeToRevealItem(
                        onDelete = { onNoteDelete(note.id) },
                        onActionRight = if (onNoteExportPdf != null) { { onNoteExportPdf(note) } } else null,
                        revealWidthDp = 48,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        NoteRowItem(
                            note = note,
                            isSelected = isSelected,
                            isEditing = isEditing,
                            matchingHits = searchHits[note.id],
                            pageFormat = pageFormat,
                            getPageCount = getPageCount,
                            onClick = {
                                onNoteSelect(note.id)
                                searchHits[note.id]?.firstOrNull()?.let { hit ->
                                    onSearchHitSelected?.invoke(hit)
                                }
                            },
                            onLongClick = { editingNoteId = note.id },
                            onCommitRename = { newTitle ->
                                onNoteRename?.invoke(note.id, newTitle)
                                editingNoteId = null
                            },
                            onCancelRename = { editingNoteId = null }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRowItem(
    note: NoteItem,
    isSelected: Boolean,
    isEditing: Boolean,
    matchingHits: List<SearchHit>? = null,
    pageFormat: PageFormat = PageFormat.A4,
    getPageCount: ((NoteItem) -> Int)? = null,
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
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    val clickModifier = if (!isEditing) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(highlightBorder)
            .background(backgroundColor)
            .then(clickModifier)
            .padding(horizontal = 9.dp, vertical = if (isEditing) 5.dp else 7.dp)
    ) {
        val pageCount = remember(note, pageFormat) {
            getPageCount?.invoke(note) ?: run {
                val worldH = PdfPagePaginator.getWorldPageHeight(pageFormat)
                if (note.maxY <= 0f || note.strokeCount == 0) 1 else max(1, ceil(note.maxY / worldH).toInt())
            }
        }
        val pageStr = "$pageCount ${if (pageCount == 1) "page" else "pages"}"

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))

            if (isEditing) {
                InPlaceRenameField(
                    initialText = note.title,
                    onCommit = onCommitRename,
                    onCancel = onCancelRename,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
            } else {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Text(
                        text = pageStr,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (!isEditing) {
            val createdStr = remember(note.createdAt) { formatCreationDateTime(note.createdAt) }
            val editedStr = remember(note.updatedAt) { formatRelativeTimeAgo(note.updatedAt) }

            if (createdStr.isNotBlank() || editedStr.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (createdStr.isNotBlank()) {
                        Text(
                            text = createdStr,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (editedStr.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (editedStr == "just now") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = editedStr,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = if (editedStr == "just now") FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (editedStr == "just now") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            val topHit = matchingHits?.firstOrNull()
            if (topHit != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = topHit.snippet,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
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

private fun formatRelativeTimeAgo(millis: Long): String {
    if (millis <= 0L) return ""
    val diff = System.currentTimeMillis() - millis
    if (diff < 0L) return "just now"
    val seconds = diff / 1000L
    val minutes = seconds / 60L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        seconds < 45L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        days < 7L -> "${days}d ago"
        else -> {
            try {
                val date = java.util.Date(millis)
                val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                sdf.format(date)
            } catch (e: Exception) {
                ""
            }
        }
    }
}
