package com.antigrav.bignotes.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.antigrav.bignotes.core.storage.BackupManager
import com.antigrav.bignotes.core.storage.SnapshotManifest
import com.antigrav.bignotes.core.storage.SnapshotNoteEntry
import com.antigrav.bignotes.core.storage.TimelineSnapshotSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupTimelineDialog(
    treeUri: Uri,
    onRestoreSnapshot: (snapshotId: String) -> Unit,
    onCherryPickNote: (snapshotId: String, noteId: String) -> Unit,
    isRestoring: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoadingTimeline by remember { mutableStateOf(true) }
    var timelineSnapshots by remember { mutableStateOf<List<TimelineSnapshotSummary>>(emptyList()) }

    var selectedSnapshotId by remember { mutableStateOf<String?>(null) }
    var isLoadingDetail by remember { mutableStateOf(false) }
    var selectedManifest by remember { mutableStateOf<SnapshotManifest?>(null) }
    var showRestoreAllConfirm by remember { mutableStateOf(false) }

    // Prune & Delete state
    var isPruning by remember { mutableStateOf(false) }
    var pruneFeedback by remember { mutableStateOf<String?>(null) }
    var showPruneMenu by remember { mutableStateOf(false) }
    var pendingPruneConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingPruneConfirmMessage by remember { mutableStateOf<String?>(null) }
    var snapshotToDelete by remember { mutableStateOf<TimelineSnapshotSummary?>(null) }

    fun executePrune(block: suspend () -> com.antigrav.bignotes.core.storage.PruneResult) {
        isPruning = true
        coroutineScope.launch(Dispatchers.IO) {
            val result = block()
            val list = BackupManager.getTimeline(context, treeUri)
            withContext(Dispatchers.Main) {
                isPruning = false
                timelineSnapshots = list
                pruneFeedback = result.message
                if (selectedSnapshotId != null && list.none { it.id == selectedSnapshotId }) {
                    selectedSnapshotId = null
                    selectedManifest = null
                }
            }
        }
    }

    // Load timeline on open
    LaunchedEffect(treeUri) {
        isLoadingTimeline = true
        withContext(Dispatchers.IO) {
            val list = BackupManager.getTimeline(context, treeUri)
            withContext(Dispatchers.Main) {
                timelineSnapshots = list
                isLoadingTimeline = false
            }
        }
    }

    // Load snapshot detail when selected
    fun loadSnapshotDetail(id: String) {
        selectedSnapshotId = id
        isLoadingDetail = true
        coroutineScope.launch(Dispatchers.IO) {
            val manifest = BackupManager.getSnapshotDetail(context, treeUri, id)
            withContext(Dispatchers.Main) {
                selectedManifest = manifest
                isLoadingDetail = false
            }
        }
    }

    Dialog(onDismissRequest = { if (!isRestoring && !isPruning) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier
                .widthIn(min = 360.dp, max = 500.dp)
                .fillMaxHeight(0.76f)
                .padding(6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedSnapshotId != null) {
                            IconButton(
                                onClick = {
                                    selectedSnapshotId = null
                                    selectedManifest = null
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                            text = if (selectedSnapshotId != null) "Snapshot Details" else "Backup Timeline",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedSnapshotId == null && timelineSnapshots.size > 1) {
                            Box {
                                OutlinedButton(
                                    onClick = { showPruneMenu = true },
                                    enabled = !isPruning && !isRestoring,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Prune",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showPruneMenu,
                                    onDismissRequest = { showPruneMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Keep latest 5 snapshots (prune older)") },
                                        onClick = {
                                            showPruneMenu = false
                                            pendingPruneConfirmMessage = "Keep the latest 5 snapshots and permanently delete older snapshots and unreferenced blobs?"
                                            pendingPruneConfirmAction = {
                                                executePrune { BackupManager.pruneSnapshots(context, treeUri, keepLatestCount = 5) }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Keep latest 10 snapshots (prune older)") },
                                        onClick = {
                                            showPruneMenu = false
                                            pendingPruneConfirmMessage = "Keep the latest 10 snapshots and permanently delete older snapshots and unreferenced blobs?"
                                            pendingPruneConfirmAction = {
                                                executePrune { BackupManager.pruneSnapshots(context, treeUri, keepLatestCount = 10) }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete snapshots older than 7 days") },
                                        onClick = {
                                            showPruneMenu = false
                                            pendingPruneConfirmMessage = "Permanently delete snapshots older than 7 days and clean up unreferenced note blobs?"
                                            pendingPruneConfirmAction = {
                                                executePrune { BackupManager.pruneSnapshots(context, treeUri, olderThanDays = 7) }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete snapshots older than 30 days") },
                                        onClick = {
                                            showPruneMenu = false
                                            pendingPruneConfirmMessage = "Permanently delete snapshots older than 30 days and clean up unreferenced note blobs?"
                                            pendingPruneConfirmAction = {
                                                executePrune { BackupManager.pruneSnapshots(context, treeUri, olderThanDays = 30) }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete all except latest snapshot", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showPruneMenu = false
                                            pendingPruneConfirmMessage = "Keep only the newest snapshot and delete all older snapshots?"
                                            pendingPruneConfirmAction = {
                                                executePrune { BackupManager.pruneSnapshots(context, treeUri, keepLatestCount = 1) }
                                            }
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        IconButton(
                            onClick = onDismiss,
                            enabled = !isRestoring && !isPruning,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (pruneFeedback != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = pruneFeedback ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { pruneFeedback = null },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedSnapshotId == null) {
                    // Timeline List View
                    if (isLoadingTimeline) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        }
                    } else if (timelineSnapshots.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No snapshots recorded yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "Tap 'Backup Now' in Settings to create your first snapshot.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "${timelineSnapshots.size} snapshot${if (timelineSnapshots.size == 1) "" else "s"} in timeline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(timelineSnapshots, key = { it.id }) { summary ->
                                TimelineCard(
                                    summary = summary,
                                    onClick = { loadSnapshotDetail(summary.id) },
                                    onDelete = { snapshotToDelete = summary }
                                )
                            }
                        }
                    }
                } else {
                    // Snapshot Detail View
                    if (isLoadingDetail) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        }
                    } else if (selectedManifest != null) {
                        val manifest = selectedManifest!!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            // Snapshot Header Card
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = manifest.formattedDate,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        TriggerBadge(trigger = manifest.trigger)
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${manifest.notes.size} note${if (manifest.notes.size == 1) "" else "s"} • ${manifest.folders.size} folder${if (manifest.folders.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = { showRestoreAllConfirm = true },
                                        enabled = !isRestoring,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        if (isRestoring) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Restoring...")
                                        } else {
                                            Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Restore Entire Snapshot")
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "NOTES IN THIS SNAPSHOT (Cherry-pick to restore individually):",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.8.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            if (manifest.notes.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Empty snapshot (0 notes)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(manifest.notes, key = { it.id }) { noteEntry ->
                                        val folderName = manifest.folders.find { it.id == noteEntry.folderId }?.name ?: "Main"
                                        CherryPickNoteItem(
                                            entry = noteEntry,
                                            folderName = folderName,
                                            isRestoring = isRestoring,
                                            onCherryPick = {
                                                selectedSnapshotId?.let { sId ->
                                                    onCherryPickNote(sId, noteEntry.id)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    enabled = !isRestoring,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }

    // Confirmation Alert for Restoring Entire Snapshot
    if (showRestoreAllConfirm && selectedSnapshotId != null) {
        val count = selectedManifest?.notes?.size ?: 0
        AlertDialog(
            onDismissRequest = { showRestoreAllConfirm = false },
            title = {
                Text(
                    text = "Restore Entire Snapshot?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "This will restore $count note${if (count == 1) "" else "s"} from this snapshot into your library. Existing notes with matching IDs will be updated.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreAllConfirm = false
                        selectedSnapshotId?.let { onRestoreSnapshot(it) }
                    }
                ) {
                    Text("Restore All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreAllConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Prune History Confirmation Dialog
    if (pendingPruneConfirmAction != null) {
        AlertDialog(
            onDismissRequest = {
                pendingPruneConfirmAction = null
                pendingPruneConfirmMessage = null
            },
            title = {
                Text(
                    text = "Prune Snapshot History?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = pendingPruneConfirmMessage ?: "This will permanently remove selected snapshots and delete any unreferenced files in storage.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val action = pendingPruneConfirmAction
                        pendingPruneConfirmAction = null
                        pendingPruneConfirmMessage = null
                        action?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Prune History")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingPruneConfirmAction = null
                        pendingPruneConfirmMessage = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Single Snapshot Confirmation Dialog
    if (snapshotToDelete != null) {
        val snap = snapshotToDelete!!
        AlertDialog(
            onDismissRequest = { snapshotToDelete = null },
            title = {
                Text(
                    text = "Delete Snapshot?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete the snapshot from ${snap.formattedDate}? Unreferenced note blobs will be removed to free storage.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        snapshotToDelete = null
                        executePrune { BackupManager.deleteSnapshot(context, treeUri, snap.id) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { snapshotToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TimelineCard(
    summary: TimelineSnapshotSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = summary.formattedDate,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TriggerBadge(trigger = summary.trigger)
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${summary.totalNotes} note${if (summary.totalNotes == 1) "" else "s"} • ${summary.totalFolders} folder${if (summary.totalFolders == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                if (summary.summary.isNotBlank()) {
                    Text(
                        text = summary.summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete snapshot",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View Details",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CherryPickNoteItem(
    entry: SnapshotNoteEntry,
    folderName: String,
    isRestoring: Boolean,
    onCherryPick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Folder: $folderName • ${entry.previewText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            OutlinedButton(
                onClick = onCherryPick,
                enabled = !isRestoring,
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    text = "Cherry-pick",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun TriggerBadge(trigger: String) {
    val isAuto = trigger.startsWith("Auto", ignoreCase = true)
    val bgColor = if (isAuto) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val textColor = if (isAuto) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val label = if (isAuto) "Auto" else "Manual"

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            ),
            color = textColor
        )
    }
}
