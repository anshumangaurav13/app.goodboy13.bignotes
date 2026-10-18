package app.goodboy13.bignotes

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import app.goodboy13.bignotes.core.model.CanvasImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.FileProvider
import app.goodboy13.bignotes.core.storage.BackupManager
import app.goodboy13.bignotes.ui.settings.BackupTimelineDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.goodboy13.bignotes.core.export.PdfExporter
import app.goodboy13.bignotes.core.export.PageFormat
import app.goodboy13.bignotes.core.export.PdfRenderMode
import app.goodboy13.bignotes.core.gl.SketchCanvasView
import app.goodboy13.bignotes.core.storage.NoteStorageManager
import app.goodboy13.bignotes.core.model.InkBleedConfig
import app.goodboy13.bignotes.ui.dialogs.ExportPdfDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import app.goodboy13.bignotes.core.model.ActiveTool
import app.goodboy13.bignotes.core.model.PaperColor
import app.goodboy13.bignotes.core.model.PencilTone
import app.goodboy13.bignotes.core.model.BezierControlPoints
import app.goodboy13.bignotes.core.model.HoverCursorOrientation
import app.goodboy13.bignotes.core.model.PressureCurveDefaults
import app.goodboy13.bignotes.core.model.PressureCurveType
import app.goodboy13.bignotes.core.model.VelocityCurveDefaults
import app.goodboy13.bignotes.core.model.Stroke
import app.goodboy13.bignotes.core.model.GridConfig
import app.goodboy13.bignotes.core.model.GridStyle
import app.goodboy13.bignotes.ui.canvas.BigNotesCanvasView
import app.goodboy13.bignotes.ui.model.FolderItem
import app.goodboy13.bignotes.ui.model.MockData
import app.goodboy13.bignotes.ui.model.NoteItem
import app.goodboy13.bignotes.ui.settings.SettingsDialog
import app.goodboy13.bignotes.ui.settings.SettingsCategory
import app.goodboy13.bignotes.core.search.HandwritingSearchManager
import app.goodboy13.bignotes.core.search.model.SearchHit
import app.goodboy13.bignotes.core.search.model.ModelDownloadStatus
import app.goodboy13.bignotes.ui.sidebar.DualSidebarContainer
import app.goodboy13.bignotes.ui.sidebar.SidebarLayoutDefaults
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.goodboy13.bignotes.ui.theme.BigNotesTheme
import app.goodboy13.bignotes.ui.theme.InkPalette
import app.goodboy13.bignotes.ui.theme.ThemeConfig
import app.goodboy13.bignotes.ui.toolbar.DockEdge
import app.goodboy13.bignotes.ui.toolbar.FloatingNoteHeader
import app.goodboy13.bignotes.ui.toolbar.FloatingNoteToolbar
import kotlin.math.roundToInt
import app.goodboy13.bignotes.core.math.model.MathOverlayState
import app.goodboy13.bignotes.core.math.model.MathResult
import app.goodboy13.bignotes.core.math.normalizer.MathNormalizer
import app.goodboy13.bignotes.core.math.evaluator.MathEvaluator
import app.goodboy13.bignotes.core.math.font.VectorStrokeFont
import app.goodboy13.bignotes.core.math.spatial.SpatialMathParser

const val CANONICAL_PAGE_WIDTH = 1200f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("bignotes_prefs", android.content.Context.MODE_PRIVATE) }
            val savedIsDark = remember { prefs.getBoolean("is_dark_mode", false) }
            var themeConfig by remember { mutableStateOf(ThemeConfig(isDark = savedIsDark)) }

            val configuration = LocalConfiguration.current
            val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val savedSidebarVisible = remember { prefs.getBoolean("is_sidebar_visible", !isPortrait) }
            var isSidebarVisible by remember { mutableStateOf(savedSidebarVisible) }

            BigNotesTheme(themeConfig = themeConfig) {
                val toolbarColor = MaterialTheme.colorScheme.surface
                val view = LocalView.current

                // Fullscreen toggle & System bar styling (status bar + navbar matching toolbar color)
                DisposableEffect(isSidebarVisible, themeConfig) {
                    val window = (view.context as? Activity)?.window
                    if (window != null && !view.isInEditMode) {
                        val insetsController = WindowCompat.getInsetsController(window, view)
                        insetsController.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        insetsController.isAppearanceLightStatusBars = !themeConfig.isDark
                        insetsController.isAppearanceLightNavigationBars = !themeConfig.isDark

                        val barColorInt = toolbarColor.toArgb()
                        window.statusBarColor = android.graphics.Color.TRANSPARENT
                        window.navigationBarColor = barColorInt
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            window.isNavigationBarContrastEnforced = false
                            window.isStatusBarContrastEnforced = false
                        }

                        // Auto-hide navigation bar ALWAYS
                        insetsController.hide(WindowInsetsCompat.Type.navigationBars())

                        // Toggle status bar based on sidebar visibility
                        if (isSidebarVisible) {
                            insetsController.show(WindowInsetsCompat.Type.statusBars())
                        } else {
                            insetsController.hide(WindowInsetsCompat.Type.statusBars())
                        }
                    }
                    onDispose {}
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(toolbarColor)
                ) {
                    BigNotesMainScreen(
                        themeConfig = themeConfig,
                        onThemeConfigChange = { newConfig ->
                            themeConfig = newConfig
                            prefs.edit()
                                .putBoolean("is_dark_mode", newConfig.isDark)
                                .apply()
                        },
                        isSidebarVisible = isSidebarVisible,
                        onToggleSidebar = {
                            val newVisible = !isSidebarVisible
                            isSidebarVisible = newVisible
                            prefs.edit().putBoolean("is_sidebar_visible", newVisible).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BigNotesMainScreen(
    themeConfig: ThemeConfig,
    onThemeConfigChange: (ThemeConfig) -> Unit,
    isSidebarVisible: Boolean,
    onToggleSidebar: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bignotes_prefs", android.content.Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    val noteStorageManager = remember { NoteStorageManager(context) }
    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) break
            ctx = ctx.baseContext
        }
        ctx as? Activity
    }
    val initialOpenSettings = remember(activity) {
        activity?.intent?.getBooleanExtra("open_settings", false) ?: false
    }
    val initialSettingsCategoryName = remember(activity) {
        activity?.intent?.getStringExtra("settings_category")
    }
    val initialSettingsCategory = remember(initialSettingsCategoryName) {
        initialSettingsCategoryName?.let { name ->
            try {
                when (name.uppercase()) {
                    "CANVAS_STYLUS", "PEN_FEEL" -> SettingsCategory.PEN_STYLUS
                    "CANVAS_GRID" -> SettingsCategory.CANVAS_GRID
                    else -> SettingsCategory.valueOf(name.uppercase())
                }
            } catch (e: Exception) { null }
        }
    }
    var activeSettingsCategory by remember { mutableStateOf<SettingsCategory?>(initialSettingsCategory) }
    var showSettingsDialog by remember { mutableStateOf(initialOpenSettings) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportTargetNote by remember { mutableStateOf<NoteItem?>(null) }
    var selectedPdfPageFormat by remember {
        val savedFormatName = prefs.getString("pdf_page_format", PageFormat.A4.name) ?: PageFormat.A4.name
        mutableStateOf(try { PageFormat.valueOf(savedFormatName) } catch (e: Exception) { PageFormat.A4 })
    }
    var selectedPdfRenderMode by remember {
        val savedModeName = prefs.getString("pdf_render_mode", PdfRenderMode.RASTER_300DPI.name) ?: PdfRenderMode.RASTER_300DPI.name
        mutableStateOf(try { PdfRenderMode.valueOf(savedModeName) } catch (e: Exception) { PdfRenderMode.RASTER_300DPI })
    }
    var showPageSeparators by remember {
        mutableStateOf(prefs.getBoolean("show_page_separators", true))
    }
    var gridConfig by remember {
        val enabled = prefs.getBoolean("grid_enabled", false)
        val styleStr = prefs.getString("grid_style", GridStyle.DOTS.name) ?: GridStyle.DOTS.name
        val style = try { GridStyle.valueOf(styleStr) } catch (e: Exception) { GridStyle.DOTS }
        val spacing = prefs.getFloat("grid_spacing", 48f)
        val opacity = prefs.getFloat("grid_opacity", 0.25f)
        mutableStateOf(GridConfig(enabled = enabled, style = style, spacingPx = spacing, opacity = opacity))
    }
    var strictStylusMode by remember {
        mutableStateOf(prefs.getBoolean("strict_stylus_mode", true))
    }
    var isFoldersVisible by remember {
        mutableStateOf(prefs.getBoolean("is_folders_visible", true))
    }
    var multiFingerGesturesEnabled by remember {
        mutableStateOf(prefs.getBoolean("multi_finger_gestures", true))
    }
    var shapeSnappingEnabled by remember {
        mutableStateOf(prefs.getBoolean("shape_snapping_enabled", true))
    }
    var shapeSnapDelayMs by remember {
        mutableLongStateOf(prefs.getLong("shape_snap_delay_ms", 400L))
    }
    var holdToEraseEnabled by remember {
        mutableStateOf(prefs.getBoolean("hold_to_erase_enabled", true))
    }
    var holdToEraseDelayMs by remember {
        mutableLongStateOf(prefs.getLong("hold_to_erase_delay_ms", 350L))
    }
    var hoverCursorEnabled by remember {
        mutableStateOf(prefs.getBoolean("hover_cursor_enabled", true))
    }
    var hoverCursorOrientation by remember {
        mutableStateOf(
            HoverCursorOrientation.fromString(prefs.getString("hover_cursor_orientation", "UP_RIGHT"))
        )
    }
    var backupFolderUriString by remember { mutableStateOf(prefs.getString("backup_folder_uri", null)) }

    var autoBackupEnabled by remember { mutableStateOf(prefs.getBoolean("auto_backup_enabled", true)) }
    var lastBackupTimestamp by remember { mutableLongStateOf(prefs.getLong("last_backup_timestamp", 0L)) }
    var isBackupInProgress by remember { mutableStateOf(false) }
    var isRestoreInProgress by remember { mutableStateOf(false) }
    var showTimelineDialog by remember { mutableStateOf(false) }
    var hasUnsavedChangesSinceLastSnapshot by remember { mutableStateOf(false) }
    val inkBleedConfig = InkBleedConfig.DEFAULT

    val backupFolderDisplayName = remember(backupFolderUriString) {
        backupFolderUriString?.let { uriStr ->
            try {
                BackupManager.getFolderDisplayName(context, Uri.parse(uriStr))
            } catch (e: Exception) {
                "Backup Folder"
            }
        } ?: ""
    }

    val formattedLastBackup = remember(lastBackupTimestamp) {
        if (lastBackupTimestamp > 0L) {
            BackupManager.formatTimestamp(lastBackupTimestamp)
        } else ""
    }

    val openBackupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val uriStr = uri.toString()
            backupFolderUriString = uriStr
            prefs.edit().putString("backup_folder_uri", uriStr).apply()
            val folderName = BackupManager.getFolderDisplayName(context, uri)
            Toast.makeText(context, "Backup folder set: $folderName", Toast.LENGTH_SHORT).show()
        }
    }

    var isPenDown by remember { mutableStateOf(false) }
    var dirtyNoteId by remember { mutableStateOf<String?>(null) }
    var saveJob by remember { mutableStateOf<Job?>(null) }

    val (initialFolders, initialNotes) = remember {
        noteStorageManager.initializeDefaultWorkspaceIfEmpty()
    }
    val lastOpenedNoteId = remember {
        prefs.getString("last_opened_note_id", null)
    }
    val initialSelectedNote = remember {
        initialNotes.find { it.id == lastOpenedNoteId } ?: initialNotes.firstOrNull()
    }
    val computedInitialFolders = remember {
        initialFolders.map { f ->
            val count = initialNotes.count { it.folderId == f.id }
            f.copy(noteCount = count)
        }
    }

    var folders by remember { mutableStateOf(computedInitialFolders) }
    var selectedFolderId by remember { mutableStateOf<String?>(initialSelectedNote?.folderId ?: initialFolders.firstOrNull()?.id) }
    var allNotes by remember { mutableStateOf(initialNotes) }
    var selectedNoteId by remember { mutableStateOf<String?>(initialSelectedNote?.id) }

    // Canvas Engine State
    var canvasView by remember { mutableStateOf<SketchCanvasView?>(null) }
    val initialToolName = remember(activity) {
        activity?.intent?.getStringExtra("initial_tool")
    }
    var activeTool by remember {
        mutableStateOf(
            when (initialToolName?.uppercase()) {
                "HIGHLIGHTER" -> ActiveTool.HIGHLIGHTER
                "PENCIL" -> ActiveTool.PENCIL
                else -> ActiveTool.FOUNTAIN_PEN
            }
        )
    }
    var strokeSize by remember { mutableFloatStateOf(5.0f) }
    var eraserRadius by remember { mutableFloatStateOf(prefs.getFloat("eraser_radius", 36f)) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    val initialColorIndex = remember { prefs.getInt("selected_color_idx", 0) }
    var selectedColorIndex by remember { mutableIntStateOf(initialColorIndex) }
    val initialHlColorIndex = remember { prefs.getInt("selected_highlighter_color_idx", 0) }
    var selectedHighlighterColorIndex by remember { mutableIntStateOf(initialHlColorIndex) }
    var prevIsDark by remember { mutableStateOf(themeConfig.isDark) }

    val savedPressureCp1x = remember { prefs.getFloat("pressure_cp1x", PressureCurveDefaults.STANDARD.cp1x) }
    val savedPressureCp1y = remember { prefs.getFloat("pressure_cp1y", PressureCurveDefaults.STANDARD.cp1y) }
    val savedPressureCp2x = remember { prefs.getFloat("pressure_cp2x", PressureCurveDefaults.STANDARD.cp2x) }
    val savedPressureCp2y = remember { prefs.getFloat("pressure_cp2y", PressureCurveDefaults.STANDARD.cp2y) }
    var pressureBezierConfig by remember {
        mutableStateOf(
            BezierControlPoints(
                cp1x = savedPressureCp1x,
                cp1y = savedPressureCp1y,
                cp2x = savedPressureCp2x,
                cp2y = savedPressureCp2y
            )
        )
    }

    val savedVelocityCp1x = remember { prefs.getFloat("velocity_cp1x", VelocityCurveDefaults.CALLIGRAPHIC.cp1x) }
    val savedVelocityCp1y = remember { prefs.getFloat("velocity_cp1y", VelocityCurveDefaults.CALLIGRAPHIC.cp1y) }
    val savedVelocityCp2x = remember { prefs.getFloat("velocity_cp2x", VelocityCurveDefaults.CALLIGRAPHIC.cp2x) }
    val savedVelocityCp2y = remember { prefs.getFloat("velocity_cp2y", VelocityCurveDefaults.CALLIGRAPHIC.cp2y) }
    var velocityBezierConfig by remember {
        mutableStateOf(
            BezierControlPoints(
                cp1x = savedVelocityCp1x,
                cp1y = savedVelocityCp1y,
                cp2x = savedVelocityCp2x,
                cp2y = savedVelocityCp2y
            )
        )
    }

    // Per-note stroke & image persistence in memory
    val noteStrokes = remember { mutableStateMapOf<String, List<Stroke>>() }
    val noteImages = remember { mutableStateMapOf<String, List<CanvasImage>>() }
    var selectedCanvasImage by remember { mutableStateOf<CanvasImage?>(null) }

    // Offline Handwriting Recognition & Search Manager
    val handwritingSearchManager = remember { HandwritingSearchManager(context) }
    DisposableEffect(Unit) {
        onDispose {
            handwritingSearchManager.close()
        }
    }
    val modelDownloadStatus by handwritingSearchManager.downloadStatus.collectAsState()
    var searchHits by remember { mutableStateOf<Map<String, List<SearchHit>>>(emptyMap()) }
    var activeSearchHit by remember { mutableStateOf<SearchHit?>(null) }
    var activeMathOverlayState by remember { mutableStateOf<MathOverlayState?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val onSearchQueryChanged: (String) -> Unit = { query ->
        searchJob?.cancel()
        if (query.isBlank()) {
            searchHits = emptyMap()
            activeSearchHit = null
        } else {
            searchJob = coroutineScope.launch {
                delay(120L)
                val hits = handwritingSearchManager.search(query)
                searchHits = hits
                val currentHit = hits[selectedNoteId]?.firstOrNull()
                if (currentHit != null) {
                    activeSearchHit = currentHit
                }
            }
        }
    }

    // Auto-download language pack if not downloaded & synchronize handwriting search index
    LaunchedEffect(Unit) {
        if (modelDownloadStatus is ModelDownloadStatus.NotDownloaded) {
            handwritingSearchManager.downloadModel()
        }
        withContext(Dispatchers.IO) {
            val indexedTimestamps = handwritingSearchManager.getIndexedTimestamps()
            allNotes.forEach { note ->
                val lastIndexed = indexedTimestamps[note.id]
                // Skip if Room index is already up to date with the note's last modification
                if (lastIndexed != null && lastIndexed >= note.updatedAt) {
                    return@forEach
                }

                // Check document for cached pre-recognized text blocks
                val doc = noteStorageManager.loadNote(note.id) ?: return@forEach
                if (doc.textBlocks.isNotEmpty()) {
                    handwritingSearchManager.indexFromDocumentIfCached(note.folderId, doc)
                } else if (doc.strokes.isNotEmpty()) {
                    handwritingSearchManager.indexNoteAsync(note.id, note.folderId, doc.strokes)
                }
            }
        }
    }

    // Helper to ensure a note exists the moment drawing starts on an empty canvas
    val ensureActiveNote: () -> String = {
        val existing = selectedNoteId
        if (existing != null) {
            existing
        } else {
            val activeFolderId = selectedFolderId ?: folders.firstOrNull()?.id
            if (activeFolderId == null) {
                ""
            } else {
                val countInFolder = allNotes.count { it.folderId == activeFolderId }
                val noteNum = countInFolder + 1
                val newNote = NoteItem(
                    id = "note_${System.currentTimeMillis()}",
                    folderId = activeFolderId,
                    title = "Note $noteNum",
                    previewText = "1 stroke",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                allNotes = listOf(newNote) + allNotes
                noteStrokes[newNote.id] = emptyList()
                noteImages[newNote.id] = emptyList()
                selectedNoteId = newNote.id
                prefs.edit().putString("last_opened_note_id", newNote.id).apply()

                folders = folders.map { folder ->
                    if (folder.id == activeFolderId) {
                        folder.copy(noteCount = folder.noteCount + 1)
                    } else folder
                }
                newNote.id
            }
        }
    }

    val scheduleDebouncedSave: () -> Unit = {
        saveJob?.cancel()
        saveJob = coroutineScope.launch {
            delay(1000L) // Wait 1 second of inactivity after lifting pen
            if (!isPenDown) {
                val nId = dirtyNoteId ?: return@launch
                val note = allNotes.find { it.id == nId } ?: return@launch
                val strokesToSave = noteStrokes[nId] ?: emptyList()
                val imagesToSave = canvasView?.getImages() ?: noteImages[nId] ?: emptyList()
                val currentPanY = canvasView?.renderer?.panY ?: 0f
                val now = System.currentTimeMillis()
                val updatedNote = note.copy(updatedAt = now)
                allNotes = allNotes.map { if (it.id == nId) updatedNote else it }
                withContext(Dispatchers.IO) {
                    noteStorageManager.saveNote(updatedNote, strokesToSave, zoomScale, scrollY = currentPanY, images = imagesToSave)
                }
                handwritingSearchManager.indexNoteAsync(nId, updatedNote.folderId, strokesToSave)
                hasUnsavedChangesSinceLastSnapshot = true
                dirtyNoteId = null
            }
        }
    }

    val flushSaveNow: (String?) -> Unit = { targetId ->
        val id = targetId ?: dirtyNoteId
        if (id != null && id == dirtyNoteId) {
            saveJob?.cancel()
            val note = allNotes.find { it.id == id }
            if (note != null) {
                val strokesToSave = noteStrokes[id] ?: emptyList()
                val imagesToSave = canvasView?.getImages() ?: noteImages[id] ?: emptyList()
                val currentPanY = canvasView?.renderer?.panY ?: 0f
                val now = System.currentTimeMillis()
                val updatedNote = note.copy(updatedAt = now)
                allNotes = allNotes.map { if (it.id == id) updatedNote else it }
                coroutineScope.launch(Dispatchers.IO) {
                    noteStorageManager.saveNote(updatedNote, strokesToSave, zoomScale, scrollY = currentPanY, images = imagesToSave)
                }
                handwritingSearchManager.indexNoteAsync(id, updatedNote.folderId, strokesToSave)
                hasUnsavedChangesSinceLastSnapshot = true
            }
            dirtyNoteId = null
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val noteId = ensureActiveNote()
            coroutineScope.launch(Dispatchers.IO) {
                val mediaResult = noteStorageManager.saveImageMedia(uri)
                if (mediaResult != null) {
                    val (fileName, dimensions) = mediaResult
                    val (origW, origH) = dimensions
                    val aspectRatio = if (origH > 0) origW.toFloat() / origH.toFloat() else 1.0f

                    val displayWidth = 500f
                    val displayHeight = displayWidth / aspectRatio

                    withContext(Dispatchers.Main) {
                        val view = canvasView
                        val screenCenterX = (view?.width?.toFloat() ?: 1200f) * 0.5f
                        val screenCenterY = (view?.height?.toFloat() ?: 800f) * 0.5f
                        val (worldCenterX, worldCenterY) = view?.screenToWorld(screenCenterX, screenCenterY) ?: Pair(600f, 600f)

                        val newImage = CanvasImage(
                            localFileName = fileName,
                            centerX = worldCenterX,
                            centerY = worldCenterY,
                            width = displayWidth,
                            height = displayHeight
                        )

                        view?.addImage(newImage)
                        selectedCanvasImage = newImage
                        dirtyNoteId = noteId
                        scheduleDebouncedSave()
                    }
                }
            }
        }
    }

    val createNewFolder: () -> Unit = {
        val newIndex = folders.size + 1
        val newFolder = FolderItem(
            id = "folder_${System.currentTimeMillis()}",
            name = "Folder $newIndex",
            noteCount = 0
        )
        folders = folders + newFolder
        selectedFolderId = newFolder.id
        hasUnsavedChangesSinceLastSnapshot = true
    }

    val createNewNote: () -> Unit = {
        if (folders.isNotEmpty()) {
            flushSaveNow(selectedNoteId)
            selectedNoteId?.let { currentId ->
                canvasView?.let { v ->
                    noteStrokes[currentId] = v.renderer.getCommittedStrokes()
                }
            }
            val activeFolderId = selectedFolderId ?: folders.firstOrNull()?.id
            if (activeFolderId != null) {
                val countInFolder = allNotes.count { it.folderId == activeFolderId }
                val newNoteIndex = countInFolder + 1
                val newNote = NoteItem(
                    id = "note_${System.currentTimeMillis()}",
                    folderId = activeFolderId,
                    title = "Note $newNoteIndex",
                    previewText = "Empty note",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                allNotes = listOf(newNote) + allNotes
                noteStrokes[newNote.id] = emptyList()
                selectedNoteId = newNote.id

                folders = folders.map { folder ->
                    if (folder.id == activeFolderId) {
                        folder.copy(noteCount = folder.noteCount + 1)
                    } else folder
                }
                hasUnsavedChangesSinceLastSnapshot = true
            }
        }
    }

    val triggerBackupNow: () -> Unit = {
        val uriStr = backupFolderUriString
        if (uriStr != null) {
            val treeUri = Uri.parse(uriStr)
            isBackupInProgress = true
            flushSaveNow(selectedNoteId)
            coroutineScope.launch(Dispatchers.IO) {
                val result = BackupManager.createSnapshot(
                    context = context,
                    treeUri = treeUri,
                    folders = folders,
                    notes = allNotes,
                    storageManager = noteStorageManager,
                    trigger = "Manual Backup"
                )
                withContext(Dispatchers.Main) {
                    isBackupInProgress = false
                    if (result.success) {
                        hasUnsavedChangesSinceLastSnapshot = false
                        val now = System.currentTimeMillis()
                        lastBackupTimestamp = now
                        prefs.edit().putLong("last_backup_timestamp", now).apply()
                    }
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val executeRestoreSnapshot: (String) -> Unit = { snapshotId ->
        val uriStr = backupFolderUriString
        if (uriStr != null) {
            val treeUri = Uri.parse(uriStr)
            saveJob?.cancel()
            dirtyNoteId = null
            isRestoreInProgress = true
            coroutineScope.launch(Dispatchers.IO) {
                val result = BackupManager.restoreSnapshot(context, treeUri, snapshotId, noteStorageManager)
                withContext(Dispatchers.Main) {
                    isRestoreInProgress = false
                    showTimelineDialog = false
                    if (result.success) {
                        saveJob?.cancel()
                        dirtyNoteId = null
                        folders = result.restoredFolders
                        allNotes = result.restoredNotes
                        for (note in result.restoredNotes) {
                            val doc = noteStorageManager.loadNote(note.id)
                            if (doc != null) {
                                noteStrokes[note.id] = doc.strokes
                            }
                        }
                        val target = selectedNoteId?.let { id -> result.restoredNotes.find { it.id == id } }
                            ?: result.restoredNotes.firstOrNull()
                        if (target != null) {
                            selectedFolderId = target.folderId
                            selectedNoteId = target.id
                            canvasView?.loadPage(target.id, noteStrokes[target.id] ?: emptyList())
                        }
                    }
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val executeCherryPickNote: (String, String) -> Unit = { snapshotId, noteId ->
        val uriStr = backupFolderUriString
        if (uriStr != null) {
            val treeUri = Uri.parse(uriStr)
            saveJob?.cancel()
            dirtyNoteId = null
            isRestoreInProgress = true
            coroutineScope.launch(Dispatchers.IO) {
                val result = BackupManager.cherryPickNote(context, treeUri, snapshotId, noteId, noteStorageManager)
                withContext(Dispatchers.Main) {
                    isRestoreInProgress = false
                    if (result.success && result.restoredNote != null) {
                        val restored = result.restoredNote
                        val doc = noteStorageManager.loadNote(restored.id)
                        if (doc != null) {
                            noteStrokes[restored.id] = doc.strokes
                        }
                        if (folders.none { it.id == restored.folderId } && result.restoredFolder != null) {
                            folders = folders + result.restoredFolder
                        }
                        val existingIndex = allNotes.indexOfFirst { it.id == restored.id }
                        allNotes = if (existingIndex >= 0) {
                            allNotes.map { if (it.id == restored.id) restored else it }
                        } else {
                            listOf(restored) + allNotes
                        }
                        noteStorageManager.saveNotesIndex(allNotes)
                        val folderCounts = allNotes.groupingBy { it.folderId }.eachCount()
                        folders = folders.map { f -> f.copy(noteCount = folderCounts[f.id] ?: 0) }

                        selectedFolderId = restored.folderId
                        selectedNoteId = restored.id
                        canvasView?.loadPage(restored.id, noteStrokes[restored.id] ?: emptyList())
                    }
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Lifecycle observer: flush saves on pause/stop, and auto-snapshot on focus loss (ON_STOP)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                flushSaveNow(selectedNoteId)
            }
            if (event == Lifecycle.Event.ON_STOP) {
                if (autoBackupEnabled && backupFolderUriString != null && hasUnsavedChangesSinceLastSnapshot) {
                    val uriStr = backupFolderUriString
                    if (uriStr != null) {
                        val treeUri = Uri.parse(uriStr)
                        coroutineScope.launch(Dispatchers.IO) {
                            val res = BackupManager.createSnapshot(
                                context = context,
                                treeUri = treeUri,
                                folders = folders,
                                notes = allNotes,
                                storageManager = noteStorageManager,
                                trigger = "Auto (App Minimized)"
                            )
                            if (res.success) {
                                hasUnsavedChangesSinceLastSnapshot = false
                                val now = System.currentTimeMillis()
                                lastBackupTimestamp = now
                                prefs.edit().putLong("last_backup_timestamp", now).apply()
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Background fast verification of storage
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val fastNotes = noteStorageManager.loadAllNotesMetadataFast()
            val savedFolders = noteStorageManager.loadFolders()
            withContext(Dispatchers.Main) {
                if (!savedFolders.isNullOrEmpty()) {
                    folders = savedFolders
                }
                if (fastNotes.isNotEmpty()) {
                    allNotes = fastNotes
                    folders = folders.map { f ->
                        f.copy(noteCount = fastNotes.count { it.folderId == f.id })
                    }
                }
            }
        }
    }

    LaunchedEffect(folders) {
        withContext(Dispatchers.IO) {
            noteStorageManager.saveFolders(folders)
        }
    }

    LaunchedEffect(selectedNoteId) {
        if (selectedNoteId != null) {
            prefs.edit().putString("last_opened_note_id", selectedNoteId).apply()
        }
    }

    // Update stylus pressure curve and velocity curve on canvas
    LaunchedEffect(pressureBezierConfig, velocityBezierConfig, canvasView) {
        val view = canvasView ?: return@LaunchedEffect
        view.bezierConfig = pressureBezierConfig
        view.velocityBezierConfig = velocityBezierConfig
    }

    // Load active note strokes and images asynchronously when note or canvas changes
    LaunchedEffect(selectedNoteId, canvasView) {
        val view = canvasView ?: return@LaunchedEffect
        val noteId = selectedNoteId
        selectedCanvasImage = null
        activeMathOverlayState = null
        if (noteId == null) {
            view.loadPage("empty", emptyList(), images = emptyList())
        } else {
            val cached = noteStrokes[noteId]
            val cachedImages = noteImages[noteId]
            if (cached != null && cachedImages != null) {
                view.loadPage(noteId, cached, images = cachedImages)
            } else {
                withContext(Dispatchers.IO) {
                    val doc = noteStorageManager.loadNote(noteId)
                    val loadedStrokes = doc?.strokes ?: emptyList()
                    val loadedImages = doc?.images ?: emptyList()
                    withContext(Dispatchers.Main) {
                        noteStrokes[noteId] = loadedStrokes
                        noteImages[noteId] = loadedImages
                        if (selectedNoteId == noteId) {
                            view.loadPage(noteId, loadedStrokes, images = loadedImages)
                        }
                    }
                }
            }
        }
    }

    // Synchronize paper, dark mode, active tool, and current color reactively
    LaunchedEffect(themeConfig, activeTool, selectedColorIndex, selectedHighlighterColorIndex, canvasView) {
        val view = canvasView ?: return@LaunchedEffect
        view.paperColor = PaperColor.forTheme(themeConfig.isDark)
        view.isDarkMode = themeConfig.isDark

        val isHl = activeTool == ActiveTool.HIGHLIGHTER
        val activePalette = if (isHl) {
            InkPalette.getHighlighterColors()
        } else {
            InkPalette.getColors()
        }
        val chosenIndex = if (isHl) selectedHighlighterColorIndex else selectedColorIndex
        val activeColor = activePalette.getOrElse(chosenIndex) { activePalette[0] }
        view.renderer.currentColorRgb = activeColor.colorRgb
        view.renderer.currentPaletteIndex = activeColor.slotIndex
        view.renderer.currentTone = when (activeTool) {
            ActiveTool.HIGHLIGHTER -> PencilTone.LIGHT
            ActiveTool.FOUNTAIN_PEN -> PencilTone.INK_BLACK
            ActiveTool.PENCIL -> PencilTone.SOFT
            else -> view.renderer.currentTone
        }

        view.requestRedraw()
    }

    // When sidebar is toggled, ensure canvas re-anchors to full or split view width
    LaunchedEffect(isSidebarVisible) {
        kotlinx.coroutines.delay(240)
        canvasView?.requestRedraw()
    }

    val currentNotes = remember(selectedFolderId, allNotes) {
        allNotes.filter { it.folderId == selectedFolderId }
    }

    val currentFolder = remember(selectedFolderId, folders) {
        folders.find { it.id == selectedFolderId }
    }

    val currentNote = remember(selectedNoteId, allNotes) {
        allNotes.find { it.id == selectedNoteId }
    }

    val density = LocalDensity.current
    val sidebarWidthDp = SidebarLayoutDefaults.totalWidth(configuration.screenWidthDp, isPortrait, isFoldersVisible)
    val sidebarWidthPx = with(density) { sidebarWidthDp.toPx() }

    val animatedSidebarWidthPx by animateFloatAsState(
        targetValue = if (isSidebarVisible) sidebarWidthPx else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "sidebarWidth"
    )
    val animatedSidebarWidthDp = with(density) { animatedSidebarWidthPx.toDp() }

    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    var lastKnownStatusBarHeightDp by remember { mutableStateOf(24.dp) }
    if (statusBarTopPx > 0) {
        lastKnownStatusBarHeightDp = with(density) { statusBarTopPx.toDp() }
    }
    val animatedStatusBarDp by animateDpAsState(
        targetValue = if (isSidebarVisible) lastKnownStatusBarHeightDp else 0.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "statusBarHeight"
    )

    val animatedTopOffsetPx = with(density) { animatedStatusBarDp.toPx() }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Permanent Full-Screen Canvas Engine (SurfaceView NEVER resizes, NEVER reallocates -> ZERO flicker!)
        BigNotesCanvasView(
            activeTool = activeTool,
            strokeSize = strokeSize,
            eraserRadius = eraserRadius,
            holdToEraseEnabled = holdToEraseEnabled,
            holdToEraseDelayMs = holdToEraseDelayMs,
            hoverCursorEnabled = hoverCursorEnabled,
            hoverCursorOrientation = hoverCursorOrientation,
            basePageWidth = CANONICAL_PAGE_WIDTH,
            viewOffsetX = animatedSidebarWidthPx,
            viewOffsetY = animatedTopOffsetPx,
            bezierConfig = pressureBezierConfig,
            velocityBezierConfig = velocityBezierConfig,
            pageFormat = selectedPdfPageFormat,
            showPageSeparators = showPageSeparators,
            inkBleedConfig = inkBleedConfig,
            isCanvasEnabled = selectedNoteId != null,
            hasFolders = folders.isNotEmpty(),
            onAddNote = createNewNote,
            onAddFolder = createNewFolder,
            onUndoRedoStateChanged = { undo, redo ->
                canUndo = undo
                canRedo = redo
            },
            onZoomChanged = { zoom ->
                zoomScale = zoom
            },
            onCanvasBound = { boundView ->
                canvasView = boundView
                boundView.renderer.currentTone = when (activeTool) {
                    ActiveTool.FOUNTAIN_PEN -> PencilTone.INK_BLACK
                    ActiveTool.HIGHLIGHTER -> PencilTone.LIGHT
                    else -> PencilTone.SOFT
                }
                boundView.paperColor = PaperColor.forTheme(themeConfig.isDark)
                boundView.isDarkMode = themeConfig.isDark
                val activePalette = if (activeTool == ActiveTool.HIGHLIGHTER) {
                    InkPalette.getHighlighterColors()
                } else {
                    InkPalette.getColors()
                }
                val chosenIndex = if (activeTool == ActiveTool.HIGHLIGHTER) selectedHighlighterColorIndex else selectedColorIndex
                val activeColor = activePalette.getOrElse(chosenIndex) { activePalette[0] }
                boundView.renderer.currentColorRgb = activeColor.colorRgb
                boundView.renderer.currentPaletteIndex = activeColor.slotIndex
                boundView.renderer.currentBaseWidth = strokeSize
                boundView.renderer.basePageWidth = CANONICAL_PAGE_WIDTH
                boundView.renderer.viewOffsetX = animatedSidebarWidthPx
                boundView.renderer.viewOffsetY = animatedTopOffsetPx
                boundView.renderer.showPageSeparators = showPageSeparators
                boundView.gridConfig = gridConfig
                boundView.strictStylusMode = strictStylusMode
                boundView.multiFingerGesturesEnabled = multiFingerGesturesEnabled
                boundView.shapeSnappingEnabled = shapeSnappingEnabled
                boundView.shapeSnapDelayMs = shapeSnapDelayMs
                boundView.holdToEraseEnabled = holdToEraseEnabled
                boundView.holdToEraseDelayMs = holdToEraseDelayMs
                boundView.eraserRadius = eraserRadius
                boundView.renderer.inkBleedConfig = inkBleedConfig

                boundView.renderer.worldPageHeight = app.goodboy13.bignotes.core.export.PdfPagePaginator.getWorldPageHeight(selectedPdfPageFormat)
            },
            onStrokesChanged = { strokes ->
                selectedNoteId?.let { id ->
                    noteStrokes[id] = strokes
                    allNotes = allNotes.map { note ->
                        if (note.id == id) {
                            note.copy(updatedAt = System.currentTimeMillis())
                        } else note
                    }
                    dirtyNoteId = id
                    if (!isPenDown) {
                        scheduleDebouncedSave()
                    }
                }
            },
            onPenContactChanged = { down ->
                isPenDown = down
                if (down) {
                    saveJob?.cancel()
                } else {
                    if (dirtyNoteId != null) {
                        scheduleDebouncedSave()
                    }
                }
            },
            selectedImage = selectedCanvasImage,
            onSelectedImageChange = { selectedCanvasImage = it },
            activeSearchHit = activeSearchHit,
            onDismissSearchHit = { activeSearchHit = null },
            onCopySelectedStrokesAsText = { strokes ->
                val noteId = selectedNoteId
                if (noteId != null && strokes.isNotEmpty()) {
                    coroutineScope.launch {
                        val text = handwritingSearchManager.getTextForStrokes(noteId, strokes)
                        if (text.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Handwritten Note", text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied: \"$text\"", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No text recognized in selection", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onSolveMathSelectedStrokes = { strokes ->
                val noteId = selectedNoteId
                if (strokes.isNotEmpty()) {
                    coroutineScope.launch {
                        val rawText = SpatialMathParser.parseAndRecognize(strokes) { subStrokes ->
                            handwritingSearchManager.recognizeStrokesDirect(subStrokes)
                                .ifBlank {
                                    if (noteId != null) handwritingSearchManager.getTextForStrokes(noteId, subStrokes) else ""
                                }
                        }

                        if (rawText.isBlank()) {
                            Toast.makeText(context, "No handwriting recognized to solve", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val normalized = MathNormalizer.normalize(rawText)
                        if (normalized.isBlank()) {
                            Toast.makeText(context, "No mathematical expression found in \"$rawText\"", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val mathResult = MathEvaluator.evaluate(normalized)

                        var minX = Float.MAX_VALUE
                        var minY = Float.MAX_VALUE
                        var maxX = -Float.MAX_VALUE
                        var maxY = -Float.MAX_VALUE
                        for (s in strokes) {
                            minX = minOf(minX, s.minX)
                            minY = minOf(minY, s.minY)
                            maxX = maxOf(maxX, s.maxX)
                            maxY = maxOf(maxY, s.maxY)
                        }
                        val worldBounds = android.graphics.RectF(minX, minY, maxX, maxY)

                        activeMathOverlayState = MathOverlayState(
                            expression = normalized,
                            result = mathResult,
                            worldBounds = worldBounds,
                            sourceStrokes = strokes
                        )
                    }
                }
            },
            mathOverlayState = activeMathOverlayState,
            onInsertMathAnswer = {
                val overlay = activeMathOverlayState
                if (overlay != null && overlay.result is MathResult.Success && overlay.sourceStrokes.isNotEmpty()) {
                    val sampleStroke = overlay.sourceStrokes.first()
                    val tone = sampleStroke.tone
                    val colorRgb = sampleStroke.colorRgb
                    val isInk = sampleStroke.isInk
                    val baseWidth = sampleStroke.baseWidth

                    val bounds = overlay.worldBounds
                    val height = (bounds.bottom - bounds.top).coerceIn(24f, 120f)
                    val centerY = (bounds.top + bounds.bottom) * 0.5f

                    val insertText = if (overlay.expression.endsWith("=")) " ${overlay.result.formattedResult}" else " = ${overlay.result.formattedResult}"
                    val startX = bounds.right + height * 0.25f

                    val generatedStrokes = VectorStrokeFont.generateStrokesForText(
                        text = insertText,
                        startX = startX,
                        centerY = centerY,
                        targetHeight = height,
                        baseWidth = baseWidth,
                        tone = tone,
                        colorRgb = colorRgb,
                        isInk = isInk
                    )

                    canvasView?.insertStrokes(generatedStrokes, selectAfterAdd = false)
                    activeMathOverlayState = null
                    Toast.makeText(context, "Inserted \"$insertText\"", Toast.LENGTH_SHORT).show()
                }
            },
            onCopyMathAnswer = {
                val overlay = activeMathOverlayState
                if (overlay != null && overlay.result is MathResult.Success) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Math Result", overlay.result.formattedResult)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied: ${overlay.result.formattedResult}", Toast.LENGTH_SHORT).show()
                }
            },
            onDismissMathOverlay = {
                activeMathOverlayState = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Floating Note Header (Option A: Minimal top-left pill for sidebar toggle, note title & export)
        FloatingNoteHeader(
            isSidebarVisible = isSidebarVisible,
            onToggleSidebar = onToggleSidebar,
            folderTitle = currentFolder?.name ?: if (folders.isEmpty()) "No Folders" else "Main",
            noteTitle = currentNote?.title ?: if (folders.isEmpty()) "Create folder first" else "No Note",
            onRenameCurrentNote = { newTitle ->
                currentNote?.let { target ->
                    val now = System.currentTimeMillis()
                    val updated = target.copy(title = newTitle, updatedAt = now)
                    allNotes = allNotes.map { note ->
                        if (note.id == target.id) updated else note
                    }
                    coroutineScope.launch(Dispatchers.IO) {
                        noteStorageManager.saveNote(updated, noteStrokes[target.id] ?: emptyList(), zoomScale, images = canvasView?.getImages() ?: noteImages[target.id] ?: emptyList())
                    }
                }
            },
            onExportNote = {
                exportTargetNote = currentNote
                showExportDialog = true
            },
            sidebarOffsetDp = animatedSidebarWidthDp,
            statusBarHeightDp = animatedStatusBarDp
        )

        // 3. Collapsible Floating Inking Toolbar with Edge Docking and Radial Color/Size Flyout
        val isHighlighterActive = activeTool == ActiveTool.HIGHLIGHTER
        val rawPalette = if (isHighlighterActive) {
            InkPalette.getHighlighterColors()
        } else {
            InkPalette.getColors()
        }
        val displayPalette = remember(rawPalette, themeConfig.isDark) {
            if (themeConfig.isDark) {
                rawPalette.map { item ->
                    val c = item.color
                    item.copy(color = Color(1f - c.red, 1f - c.green, 1f - c.blue, c.alpha))
                }
            } else {
                rawPalette
            }
        }
        val currentColorIndex = if (isHighlighterActive) selectedHighlighterColorIndex else selectedColorIndex

        val savedDockEdgeName = remember(prefs) {
            prefs.getString("floating_toolbar_edge", DockEdge.TOP.name) ?: DockEdge.TOP.name
        }
        val savedDockEdge = remember(savedDockEdgeName) {
            try { DockEdge.valueOf(savedDockEdgeName) } catch (e: Exception) { DockEdge.TOP }
        }
        val savedPosFraction = remember(prefs) {
            prefs.getFloat("floating_toolbar_pos_fraction", 0.5f)
        }
        val savedIsCollapsed = remember(prefs) {
            prefs.getBoolean("floating_toolbar_collapsed", false)
        }

        FloatingNoteToolbar(
            activeTool = activeTool,
            onToolChange = { tool ->
                activeTool = tool
                canvasView?.let { view ->
                    view.activeTool = tool
                    if (tool == ActiveTool.HIGHLIGHTER) {
                        view.renderer.currentTone = PencilTone.LIGHT
                        val hlPalette = InkPalette.getHighlighterColors()
                        val chosen = hlPalette.getOrElse(selectedHighlighterColorIndex) { hlPalette[0] }
                        view.renderer.currentColorRgb = chosen.colorRgb
                        view.renderer.currentPaletteIndex = chosen.slotIndex
                    } else if (tool == ActiveTool.FOUNTAIN_PEN) {
                        view.renderer.currentTone = PencilTone.INK_BLACK
                        val inkPalette = InkPalette.getColors()
                        val chosen = inkPalette.getOrElse(selectedColorIndex) { inkPalette[0] }
                        view.renderer.currentColorRgb = chosen.colorRgb
                        view.renderer.currentPaletteIndex = chosen.slotIndex
                    } else if (tool == ActiveTool.PENCIL) {
                        view.renderer.currentTone = PencilTone.SOFT
                        val inkPalette = InkPalette.getColors()
                        val chosen = inkPalette.getOrElse(selectedColorIndex) { inkPalette[0] }
                        view.renderer.currentColorRgb = chosen.colorRgb
                        view.renderer.currentPaletteIndex = chosen.slotIndex
                    }
                }
            },
            strokeSize = strokeSize,
            onStrokeSizeChange = { newSize ->
                strokeSize = newSize
                canvasView?.renderer?.currentBaseWidth = newSize
            },
            eraserRadius = eraserRadius,
            onEraserRadiusChange = { newRadius ->
                eraserRadius = newRadius
                canvasView?.eraserRadius = newRadius
                prefs.edit().putFloat("eraser_radius", newRadius).apply()
            },
            paletteColors = displayPalette,
            selectedColorIndex = currentColorIndex,
            onColorSelect = { idx ->
                if (activeTool == ActiveTool.HIGHLIGHTER) {
                    selectedHighlighterColorIndex = idx
                    prefs.edit().putInt("selected_highlighter_color_idx", idx).apply()
                    val hlPalette = InkPalette.getHighlighterColors()
                    val chosen = hlPalette.getOrElse(idx) { hlPalette[0] }
                    canvasView?.let { view ->
                        view.renderer.currentColorRgb = chosen.colorRgb
                        view.renderer.currentPaletteIndex = chosen.slotIndex
                    }
                } else {
                    selectedColorIndex = idx
                    prefs.edit().putInt("selected_color_idx", idx).apply()
                    val inkPalette = InkPalette.getColors()
                    val chosen = inkPalette.getOrElse(idx) { inkPalette[0] }
                    canvasView?.let { view ->
                        view.renderer.currentColorRgb = chosen.colorRgb
                        view.renderer.currentPaletteIndex = chosen.slotIndex
                    }
                }
            },
            zoomScale = zoomScale,
            canUndo = canUndo,
            canRedo = canRedo,
            onUndo = { canvasView?.undo() },
            onRedo = { canvasView?.redo() },
            onResetZoom = { canvasView?.resetZoom() },
            onInsertImage = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            initialDockEdge = savedDockEdge,
            initialPosFraction = savedPosFraction,
            initialIsCollapsed = savedIsCollapsed,
            onDockStateChanged = { edge, fraction, collapsed ->
                prefs.edit()
                    .putString("floating_toolbar_edge", edge.name)
                    .putFloat("floating_toolbar_pos_fraction", fraction)
                    .putBoolean("floating_toolbar_collapsed", collapsed)
                    .apply()
            },
            sidebarOffsetPx = animatedSidebarWidthPx,
            statusBarHeightPx = statusBarTopPx.toFloat()
        )

        // Dual Sidebar Overlay (smooth animated slide on top of canvas)
        if (animatedSidebarWidthPx > 0f) {
            DualSidebarContainer(
                isVisible = true,
                folders = folders,
                selectedFolderId = selectedFolderId,
                notes = currentNotes,
                selectedNoteId = selectedNoteId,
                onFolderSelect = { folderId ->
                    flushSaveNow(selectedNoteId)
                    selectedNoteId?.let { currentId ->
                        canvasView?.let { v ->
                            noteStrokes[currentId] = v.renderer.getCommittedStrokes()
                            noteImages[currentId] = v.getImages()
                        }
                    }
                    selectedFolderId = folderId
                    val firstNoteInFolder = allNotes.firstOrNull { it.folderId == folderId }
                    selectedNoteId = firstNoteInFolder?.id
                },
                onFolderDelete = { folderId ->
                    val notesToDelete = allNotes.filter { it.folderId == folderId }
                    notesToDelete.forEach {
                        noteStrokes.remove(it.id)
                        noteImages.remove(it.id)
                        noteStorageManager.deleteNote(it.id)
                    }
                    coroutineScope.launch(Dispatchers.IO) {
                        handwritingSearchManager.deleteFolder(folderId)
                    }
                    folders = folders.filter { it.id != folderId }
                    allNotes = allNotes.filter { it.folderId != folderId }
                    if (selectedFolderId == folderId) {
                        selectedFolderId = folders.firstOrNull()?.id
                        selectedNoteId = allNotes.firstOrNull { it.folderId == selectedFolderId }?.id
                    }
                    hasUnsavedChangesSinceLastSnapshot = true
                },
                onFolderRename = { folderId, newName ->
                    folders = folders.map { folder ->
                        if (folder.id == folderId) folder.copy(name = newName) else folder
                    }
                    hasUnsavedChangesSinceLastSnapshot = true
                },
                onNoteSelect = { noteId ->
                    flushSaveNow(selectedNoteId)
                    selectedNoteId?.let { currentId ->
                        canvasView?.let { v ->
                            val currentStrokes = v.renderer.getCommittedStrokes()
                            noteStrokes[currentId] = currentStrokes
                            noteImages[currentId] = v.getImages()
                        }
                    }
                    selectedNoteId = noteId
                    val hit = searchHits[noteId]?.firstOrNull()
                    activeSearchHit = hit
                },
                onNoteDelete = { noteId ->
                    noteStrokes.remove(noteId)
                    noteImages.remove(noteId)
                    noteStorageManager.deleteNote(noteId)
                    coroutineScope.launch(Dispatchers.IO) {
                        handwritingSearchManager.deleteNote(noteId)
                    }
                    val deletedNote = allNotes.find { it.id == noteId }
                    allNotes = allNotes.filter { it.id != noteId }
                    if (selectedNoteId == noteId) {
                        val remainingInFolder = allNotes.filter { it.folderId == selectedFolderId }
                        selectedNoteId = remainingInFolder.firstOrNull()?.id
                    }
                    if (deletedNote != null) {
                        folders = folders.map { folder ->
                            if (folder.id == deletedNote.folderId && folder.noteCount > 0) {
                                folder.copy(noteCount = folder.noteCount - 1)
                            } else folder
                        }
                    }
                    hasUnsavedChangesSinceLastSnapshot = true
                },
                onNoteRename = { noteId, newTitle ->
                    val now = System.currentTimeMillis()
                    allNotes = allNotes.map { note ->
                        if (note.id == noteId) {
                            val updated = note.copy(title = newTitle, updatedAt = now)
                            coroutineScope.launch(Dispatchers.IO) {
                                noteStorageManager.saveNote(updated, noteStrokes[noteId] ?: emptyList(), zoomScale)
                            }
                            handwritingSearchManager.indexNoteAsync(noteId, updated.folderId, noteStrokes[noteId] ?: emptyList())
                            updated
                        } else note
                    }
                    hasUnsavedChangesSinceLastSnapshot = true
                },
                onNoteExportPdf = { note ->
                    exportTargetNote = note
                    showExportDialog = true
                },
                onAddFolder = createNewFolder,
                onAddNote = createNewNote,
                isFoldersVisible = isFoldersVisible,
                onToggleFoldersVisible = {
                    val next = !isFoldersVisible
                    isFoldersVisible = next
                    prefs.edit().putBoolean("is_folders_visible", next).apply()
                },
                onOpenSettings = {
                    activeSettingsCategory = null
                    showSettingsDialog = true
                },
                isDarkMode = themeConfig.isDark,
                onToggleDarkMode = {
                    onThemeConfigChange(themeConfig.copy(isDark = !themeConfig.isDark))
                },
                searchHits = searchHits,
                onSearchQueryChanged = onSearchQueryChanged,
                onSearchHitSelected = { hit ->
                    activeSearchHit = hit
                },
                pageFormat = selectedPdfPageFormat,
                getPageCount = { note ->
                    val inMemStrokes = noteStrokes[note.id]
                    if (inMemStrokes != null && inMemStrokes.isNotEmpty()) {
                        app.goodboy13.bignotes.core.export.PdfPagePaginator.paginate(inMemStrokes, selectedPdfPageFormat).size
                    } else {
                        val worldH = app.goodboy13.bignotes.core.export.PdfPagePaginator.getWorldPageHeight(selectedPdfPageFormat)
                        if (note.maxY <= 0f || note.strokeCount == 0) 1 else kotlin.math.max(1, kotlin.math.ceil(note.maxY / worldH).toInt())
                    }
                },
                statusBarHeight = animatedStatusBarDp,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(sidebarWidthDp)
                    .offset { IntOffset(x = (animatedSidebarWidthPx - sidebarWidthPx).roundToInt(), y = 0) }
            )
        }
    }

    // Settings & Appearance Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            pressureBezier = pressureBezierConfig,
            onPressureBezierChange = { newCurve ->
                pressureBezierConfig = newCurve
                prefs.edit()
                    .putFloat("pressure_cp1x", newCurve.cp1x)
                    .putFloat("pressure_cp1y", newCurve.cp1y)
                    .putFloat("pressure_cp2x", newCurve.cp2x)
                    .putFloat("pressure_cp2y", newCurve.cp2y)
                    .apply()
            },
            velocityBezier = velocityBezierConfig,
            onVelocityBezierChange = { newCurve ->
                velocityBezierConfig = newCurve
                prefs.edit()
                    .putFloat("velocity_cp1x", newCurve.cp1x)
                    .putFloat("velocity_cp1y", newCurve.cp1y)
                    .putFloat("velocity_cp2x", newCurve.cp2x)
                    .putFloat("velocity_cp2y", newCurve.cp2y)
                    .apply()
            },
            backupFolderDisplayName = backupFolderDisplayName,
            isBackupFolderConfigured = backupFolderUriString != null,
            onSelectBackupFolder = { openBackupFolderLauncher.launch(null) },
            lastBackupTimeFormatted = formattedLastBackup,
            isBackupInProgress = isBackupInProgress,
            onBackupNow = triggerBackupNow,
            isRestoreInProgress = isRestoreInProgress,
            onRestoreClicked = { showTimelineDialog = true },
            autoBackupEnabled = autoBackupEnabled,
            onAutoBackupEnabledChange = { enabled ->
                autoBackupEnabled = enabled
                prefs.edit().putBoolean("auto_backup_enabled", enabled).apply()
            },
            showPageSeparators = showPageSeparators,
            onShowPageSeparatorsChange = { show ->
                showPageSeparators = show
                canvasView?.showPageSeparators = show
                prefs.edit().putBoolean("show_page_separators", show).apply()
            },
            gridConfig = gridConfig,
            onGridConfigChange = { newGrid ->
                gridConfig = newGrid
                canvasView?.gridConfig = newGrid
                prefs.edit()
                    .putBoolean("grid_enabled", newGrid.enabled)
                    .putString("grid_style", newGrid.style.name)
                    .putFloat("grid_spacing", newGrid.spacingPx)
                    .putFloat("grid_opacity", newGrid.opacity)
                    .apply()
            },
            strictStylusMode = strictStylusMode,
            onStrictStylusModeChange = { mode ->
                strictStylusMode = mode
                canvasView?.strictStylusMode = mode
                prefs.edit().putBoolean("strict_stylus_mode", mode).apply()
            },
            multiFingerGesturesEnabled = multiFingerGesturesEnabled,
            onMultiFingerGesturesEnabledChange = { enabled ->
                multiFingerGesturesEnabled = enabled
                canvasView?.multiFingerGesturesEnabled = enabled
                prefs.edit().putBoolean("multi_finger_gestures", enabled).apply()
            },
            shapeSnappingEnabled = shapeSnappingEnabled,
            onShapeSnappingEnabledChange = { enabled ->
                shapeSnappingEnabled = enabled
                canvasView?.shapeSnappingEnabled = enabled
                prefs.edit().putBoolean("shape_snapping_enabled", enabled).apply()
            },
            shapeSnapDelayMs = shapeSnapDelayMs,
            onShapeSnapDelayMsChange = { delay ->
                shapeSnapDelayMs = delay
                canvasView?.shapeSnapDelayMs = delay
                prefs.edit().putLong("shape_snap_delay_ms", delay).apply()
            },
            holdToEraseEnabled = holdToEraseEnabled,
            onHoldToEraseEnabledChange = { enabled ->
                holdToEraseEnabled = enabled
                canvasView?.holdToEraseEnabled = enabled
                prefs.edit().putBoolean("hold_to_erase_enabled", enabled).apply()
            },
            holdToEraseDelayMs = holdToEraseDelayMs,
            onHoldToEraseDelayMsChange = { delay ->
                holdToEraseDelayMs = delay
                canvasView?.holdToEraseDelayMs = delay
                prefs.edit().putLong("hold_to_erase_delay_ms", delay).apply()
            },
            hoverCursorEnabled = hoverCursorEnabled,
            onHoverCursorEnabledChange = { enabled ->
                hoverCursorEnabled = enabled
                prefs.edit().putBoolean("hover_cursor_enabled", enabled).apply()
            },
            hoverCursorOrientation = hoverCursorOrientation,
            onHoverCursorOrientationChange = { orientation ->
                hoverCursorOrientation = orientation
                prefs.edit().putString("hover_cursor_orientation", orientation.name).apply()
            },
            modelDownloadStatus = modelDownloadStatus,
            onDownloadModel = { handwritingSearchManager.downloadModel() },
            initialCategory = activeSettingsCategory,
            onDismiss = {
                showSettingsDialog = false
                activeSettingsCategory = null
            }

        )
    }

    // Backup Timeline Dialog
    if (showTimelineDialog && backupFolderUriString != null) {
        BackupTimelineDialog(
            treeUri = Uri.parse(backupFolderUriString!!),
            onRestoreSnapshot = executeRestoreSnapshot,
            onCherryPickNote = executeCherryPickNote,
            isRestoring = isRestoreInProgress,
            onDismiss = { showTimelineDialog = false }
        )
    }

    // Export PDF Dialog
    if (showExportDialog) {
        val targetNote = exportTargetNote ?: currentNote
        val targetStrokes = if (targetNote != null) {
            if (targetNote.id == selectedNoteId) {
                canvasView?.renderer?.getCommittedStrokes()?.also {
                    noteStrokes[targetNote.id] = it
                } ?: noteStrokes[targetNote.id] ?: emptyList()
            } else {
                noteStrokes[targetNote.id] ?: runBlocking(Dispatchers.IO) {
                    noteStorageManager.loadNote(targetNote.id)?.strokes
                } ?: emptyList()
            }
        } else emptyList()

        ExportPdfDialog(
            noteTitle = targetNote?.title ?: "Note",
            strokeCount = targetStrokes.size,
            isExporting = isExporting,
            initialFormat = selectedPdfPageFormat,
            initialMode = selectedPdfRenderMode,
            onFormatChanged = { format ->
                selectedPdfPageFormat = format
                prefs.edit().putString("pdf_page_format", format.name).apply()
            },
            onModeChanged = { mode ->
                selectedPdfRenderMode = mode
                prefs.edit().putString("pdf_render_mode", mode.name).apply()
            },
            onExport = { exportOptions ->
                selectedPdfPageFormat = exportOptions.pageFormat
                selectedPdfRenderMode = exportOptions.renderMode
                prefs.edit()
                    .putString("pdf_page_format", exportOptions.pageFormat.name)
                    .putString("pdf_render_mode", exportOptions.renderMode.name)
                    .apply()
                coroutineScope.launch {
                    try {
                        isExporting = true
                        val targetImages = if (targetNote != null && targetNote.id == selectedNoteId) {
                            canvasView?.getImages() ?: noteImages[targetNote.id] ?: emptyList()
                        } else {
                            targetNote?.id?.let { noteImages[it] } ?: emptyList()
                        }
                        val targetTextBlocks = targetNote?.id?.let {
                            handwritingSearchManager.getTextBlocks(it)
                        } ?: emptyList()
                        val pdfFile = PdfExporter.exportNoteToPdf(
                            context = context,
                            noteTitle = targetNote?.title ?: "Note",
                            strokes = targetStrokes,
                            options = exportOptions,
                            images = targetImages,
                            textBlocks = targetTextBlocks
                        )
                        isExporting = false
                        showExportDialog = false
                        exportTargetNote = null

                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            pdfFile
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share PDF Note"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        isExporting = false
                    }
                }
            },
            onExportMarkdown = {
                coroutineScope.launch {
                    try {
                        isExporting = true
                        val targetTextBlocks = targetNote?.id?.let {
                            handwritingSearchManager.getTextBlocks(it)
                        } ?: emptyList()
                        val mdFile = app.goodboy13.bignotes.core.export.MarkdownExporter.exportNoteToMarkdown(
                            context = context,
                            noteTitle = targetNote?.title ?: "Note",
                            textBlocks = targetTextBlocks
                        )
                        isExporting = false
                        showExportDialog = false
                        exportTargetNote = null

                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            mdFile
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/markdown"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Markdown Note"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        isExporting = false
                    }
                }
            },
            onDismiss = {
                showExportDialog = false
                exportTargetNote = null
            }
        )
    }
}

