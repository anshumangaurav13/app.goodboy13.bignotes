package com.antigrav.bignotes.ui.help

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.res.painterResource
import com.antigrav.bignotes.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.antigrav.bignotes.ui.theme.CustomIcons

@Composable
fun HelpAboutDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier
                .widthIn(min = 360.dp, max = 480.dp)
                .fillMaxHeight(0.76f)
                .padding(6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header with App Emblem
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.bignotes_logo),
                            contentDescription = "BigNotes Logo",
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "BigNotes",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Infinite Canvas & Inking Guide • v1.0",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Section 1: Writing & Inking
                    Text(
                        text = "WRITING & INKING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    HelpCard(
                        icon = CustomIcons.FountainPen,
                        title = "Fountain Pen & Pencil",
                        description = "Choose the Fountain Pen for smooth, expressive pressure-sensitive handwriting, or the Pencil for textured sketching and shading. Quickly toggle between 6 preset line sizes (1 to 12) and curated colors."
                    )

                    HelpCard(
                        icon = Icons.Outlined.TouchApp,
                        title = "Stylus vs. Finger Gestures",
                        description = "Your S Pen / stylus is dedicated exclusively to writing, sketching, and lasso selection. Use one or two fingers to smoothly scroll, pan, and pinch-to-zoom without leaving accidental ink marks."
                    )

                    HelpCard(
                        icon = Icons.Outlined.Tune,
                        title = "Pressure & Velocity Bézier Curves",
                        description = "Calibrate stylus pressure and calligraphy velocity thinning using the interactive 4x4 Bézier graph in Settings. Drag control points to shape your curve and test it immediately on the live test pad."
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Section 2: Editing & Selection
                    Text(
                        text = "EDITING & LASSO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    HelpCard(
                        icon = Icons.Default.Gesture,
                        title = "Lasso Selection & Moving",
                        description = "Switch to the Lasso tool and circle any strokes with your stylus. Drag inside the selection box to reposition your notes anywhere on the canvas. Tap the floating controls to duplicate, delete, or cancel."
                    )

                    HelpCard(
                        icon = Icons.Default.Fullscreen,
                        title = "Fullscreen Canvas",
                        description = "Tap the sidebar button in the top-left to collapse the folders and notes panel. BigNotes automatically enters immersive fullscreen so you have the entire display for distraction-free writing."
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Section 3: Backup Timeline & Git Architecture
                    Text(
                        text = "BACKUP & TIMELINE (GIT ARCHITECTURE)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    HelpCard(
                        icon = Icons.Outlined.Timeline,
                        title = "Automated & Deduplicated Snapshots",
                        description = "Point BigNotes to any storage folder via SAF. When you switch or close the app, it automatically saves a deduplicated snapshot of all notes without wasting storage or overwriting old backups."
                    )

                    HelpCard(
                        icon = Icons.Outlined.Restore,
                        title = "Point-in-Time Restore & Cherry-Pick",
                        description = "Open the Backup Timeline to inspect past snapshots. Restore your entire notebook to any previous point in time, cherry-pick individual notes, or prune older snapshots."
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Section 4: Notes, Export & Themes
                    Text(
                        text = "NOTES, EXPORT & THEMES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    HelpCard(
                        icon = Icons.Default.Edit,
                        title = "In-Place Renaming",
                        description = "Tap on the note title in the top toolbar or in the sidebar list to rename notes instantly without opening extra dialogs."
                    )

                    HelpCard(
                        icon = Icons.Outlined.PictureAsPdf,
                        title = "Swipe Right to Export PDF",
                        description = "Swipe right on any note in the sidebar to reveal the PDF export menu. Choose standard page sizes (Letter, A4, or Legal 14) to generate clean, paginated documents ready for printing or sharing."
                    )

                    HelpCard(
                        icon = Icons.Default.DeleteSweep,
                        title = "Swipe Left to Delete Safely",
                        description = "Swipe left on any note to reveal the delete button. The button will blink for 3 seconds; tap it during the blink to confirm deletion, or let it automatically snap closed to cancel."
                    )

                    HelpCard(
                        icon = Icons.Outlined.Palette,
                        title = "Curated Palettes & Dark Mode",
                        description = "Switch between Default (Royal Blue), Warm Sepia, Charcoal, and Mint themes in Settings. Each theme features cohesive ink palettes with dark mode contrasts tuned for effortless reading."
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Dismiss Action
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Got it",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        }
    }
}
