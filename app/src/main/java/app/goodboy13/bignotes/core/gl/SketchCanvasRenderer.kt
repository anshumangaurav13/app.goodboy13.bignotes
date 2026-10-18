package app.goodboy13.bignotes.core.gl

import android.content.Context
import android.opengl.GLES30
import android.opengl.Matrix
import androidx.graphics.lowlatency.BufferInfo
import androidx.graphics.lowlatency.GLFrontBufferedRenderer
import androidx.graphics.opengl.egl.EGLManager
import app.goodboy13.bignotes.core.geometry.CachedStrokeMesh
import app.goodboy13.bignotes.core.geometry.StrokeTessellator
import app.goodboy13.bignotes.core.geometry.TessellatedMesh
import app.goodboy13.bignotes.core.model.BezierControlPoints
import app.goodboy13.bignotes.core.model.GridConfig
import app.goodboy13.bignotes.core.model.PaperColor
import app.goodboy13.bignotes.core.model.PencilTone
import app.goodboy13.bignotes.core.model.Stroke
import app.goodboy13.bignotes.core.model.StrokePoint
import java.lang.ref.SoftReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap

data class FrontBufferPacket(
    val dabs: List<StrokePoint> = emptyList(),
    val clearBeforeDraw: Boolean = false
)

class SketchCanvasRenderer(
    private val context: Context,
    var bezierConfig: BezierControlPoints = BezierControlPoints()
) : GLFrontBufferedRenderer.Callback<FrontBufferPacket> {

    private val highlighterStore = ChunkedVboStore()
    private val inkStore = ChunkedVboStore()
    private var graphiteShader: GraphiteShader? = null
    private var noiseTextureId: Int = 0
    private val pageSeparatorRenderer = PageSeparatorRenderer()
    private val gridRenderer = GridBackgroundRenderer()
    private val imageQuadRenderer = ImageQuadRenderer()
    val images = mutableListOf<app.goodboy13.bignotes.core.model.CanvasImage>()

    @Volatile
    var gridConfig: GridConfig = GridConfig()


    @Volatile
    var showPageSeparators: Boolean = true
    @Volatile
    var worldPageHeight: Float = 1766.73f

    var currentTone: PencilTone = PencilTone.SOFT
    var currentColorRgb: Long = PencilTone.SOFT.colorRgb
    var currentPaletteIndex: Int = 0
    var currentBaseWidth: Float = 5.0f
    var isCurrentInk: Boolean = false
    var isCurrentHighlighter: Boolean = false

    @Volatile
    var paperColor: PaperColor = PaperColor.PARCHMENT
        set(value) {
            if (field != value) {
                field = value
                isFboDirty = true
            }
        }

    @Volatile
    var isDarkMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                isFboDirty = true
            }
        }

    // Viewport transform (volatile for thread-safe GL reads)
    @Volatile
    var panX: Float = 0f
    @Volatile
    var panY: Float = 0f
    @Volatile
    var viewOffsetX: Float = 0f
    @Volatile
    var viewOffsetY: Float = 0f
    @Volatile
    var zoomScale: Float = 1.0f
    @Volatile
    var rotationDegrees: Float = 0f
    @Volatile
    var isFlippedHorizontal: Boolean = false

    // Relative page scaling: expands drawing when canvas expands (e.g. sidebar hidden)
    @Volatile
    var basePageWidth: Float = 1200f
    @Volatile
    var currentViewWidth: Float = 0f

    val pageScale: Float
        get() {
            val base = basePageWidth
            val visibleWidth = currentViewWidth - viewOffsetX
            return if (base > 50f && visibleWidth > 50f) visibleWidth / base else 1.0f
        }

    // Viewport state when FBO layer was last committed/rendered
    @Volatile
    var fboPanX: Float = 0f
    @Volatile
    var fboPanY: Float = 0f
    @Volatile
    var fboViewOffsetX: Float = 0f
    @Volatile
    var fboViewOffsetY: Float = 0f
    @Volatile
    var fboZoom: Float = 1.0f
    @Volatile
    var fboPageScale: Float = 1.0f
    @Volatile
    var fboRotation: Float = 0f
    @Volatile
    var fboFlipped: Boolean = false
    @Volatile
    var isFboDirty: Boolean = true

    // Instantaneous gesture state for UI debounce
    @Volatile
    var isInteractiveGesture: Boolean = false
    @Volatile
    var inkBleedConfig: app.goodboy13.bignotes.core.model.InkBleedConfig = app.goodboy13.bignotes.core.model.InkBleedConfig.DEFAULT
        set(value) {
            val marginChanged = field.quadMarginRatio != value.quadMarginRatio
            field = value
            StrokeTessellator.activeInkConfig = value
            applyInkShaderParams()
            if (marginChanged) {
                meshCache.clear()
            }
            isFboDirty = true
        }

    fun applyInkShaderParams() {
        graphiteShader?.let { shader ->
            shader.bleedRatio = inkBleedConfig.bleedRatio
            shader.bleedPower = inkBleedConfig.bleedPower
            shader.coreShiftRatio = inkBleedConfig.coreShiftRatio
            shader.bleedBaseOpacity = inkBleedConfig.bleedBaseOpacity
            shader.aaWidth = inkBleedConfig.aaWidth
            shader.paperGrainMix = inkBleedConfig.paperGrainMix
        }
    }

    val overscanFactor: Float = 1.35f
    var fboWidth: Int = 0
        private set
    var fboHeight: Int = 0
        private set

    private var committedFbo: FboLayer? = null
    private var blitShader: BlitShader? = null

    // FBO rendering and affine blit matrices (allocated once)
    private val fboOrthoMatrix = FloatArray(16)
    private val fboWorldToViewMatrix = FloatArray(16)
    private val fboMvpMatrix = FloatArray(16)
    private val invFboMvpMatrix = FloatArray(16)
    private val invFboWorldToViewMatrix = FloatArray(16)
    private val affineMatrix = FloatArray(16)
    private val finalBlitMatrix = FloatArray(16)

    // Viewport and screen projection matrices (allocated once)
    private val screenOrthoMatrix = FloatArray(16)
    private val screenProjectionMatrix = FloatArray(16)
    private val currentWorldToViewMatrix = FloatArray(16)
    private val frontBufferMvpMatrix = FloatArray(16)
    private val directScreenMvpMatrix = FloatArray(16)
    private val invDirectScreenMvpMatrix = FloatArray(16)
    private val invWorldToViewMatrix = FloatArray(16)

    // Frustum culling corner vectors (allocated once, zero per-frame garbage)
    private val cornerIn = FloatArray(4)
    private val cornerOut = FloatArray(4)
    private val corners = FloatArray(8)

    // Selection state & matrices (direct vector render for selected strokes)
    private val selectedStrokeIds = mutableSetOf<String>()
    private val selectedStrokes = mutableListOf<Stroke>()
    var selectionDragDx: Float = 0f
    var selectionDragDy: Float = 0f
    private val selectionWorldToViewMatrix = FloatArray(16)
    private val selectionMvpMatrix = FloatArray(16)

    private var isGlInitialized = false

    private val meshCache = ConcurrentHashMap<String, SoftReference<CachedStrokeMesh>>()

    private var frontBufferDirectByteBuffer: ByteBuffer? = null
    private var frontBufferFloatBuffer: FloatBuffer? = null
    private var frontBufferCapacityVertices = 4096

    fun getOrCreateMesh(stroke: Stroke): CachedStrokeMesh {
        val cached = meshCache[stroke.id]?.get()
        if (cached != null) return cached
        val created = StrokeTessellator.tessellateToCachedMesh(
            stroke.points,
            stroke.tone,
            stroke.baseWidth,
            colorRgb = stroke.colorRgb,
            isInk = stroke.isInk,
            isHighlighter = stroke.isHighlighter
        )
        meshCache[stroke.id] = SoftReference(created)
        return created
    }

    fun markDirty() {
        isFboDirty = true
    }

    fun markBatchDirty() {
        isFboDirty = true
    }

    fun updateBezierConfig(newConfig: BezierControlPoints) {
        bezierConfig = newConfig
    }

    fun prewarmMeshCache(strokes: List<Stroke>) {
        // Softly prewarm visible strokes without hard-pinning heap
        for (stroke in strokes.take(128)) {
            getOrCreateMesh(stroke)
        }
    }

    fun setStrokes(strokes: List<Stroke>) {
        val (highlighters, inks) = strokes.partition { it.isHighlighter }
        highlighterStore.setAllStrokes(highlighters, ::getOrCreateMesh)
        inkStore.setAllStrokes(inks, ::getOrCreateMesh)
        meshCache.clear()
        isFboDirty = true
    }

    fun addCommittedStroke(stroke: Stroke) {
        if (stroke.isHighlighter) {
            highlighterStore.appendStroke(stroke, ::getOrCreateMesh)
        } else {
            inkStore.appendStroke(stroke, ::getOrCreateMesh)
        }
        getOrCreateMesh(stroke)
        isFboDirty = true
    }

    fun addCommittedStrokes(strokes: List<Stroke>) {
        if (strokes.isEmpty()) return
        val (highlighters, inks) = strokes.partition { it.isHighlighter }
        if (highlighters.isNotEmpty()) highlighterStore.appendStrokes(highlighters, ::getOrCreateMesh)
        if (inks.isNotEmpty()) inkStore.appendStrokes(inks, ::getOrCreateMesh)
        for (s in strokes) {
            getOrCreateMesh(s)
        }
        isFboDirty = true
    }

    fun setSelectedStrokes(strokes: List<Stroke>) {
        selectedStrokes.clear()
        selectedStrokeIds.clear()
        selectionDragDx = 0f
        selectionDragDy = 0f
        if (strokes.isNotEmpty()) {
            selectedStrokes.addAll(strokes)
            selectedStrokeIds.addAll(strokes.map { it.id })
            for (s in strokes) getOrCreateMesh(s)
        }
        isFboDirty = true
    }

    fun updateSelectionDragDelta(dx: Float, dy: Float) {
        selectionDragDx = dx
        selectionDragDy = dy
    }

    fun clearSelection() {
        if (selectedStrokes.isNotEmpty() || selectedStrokeIds.isNotEmpty() || selectionDragDx != 0f || selectionDragDy != 0f) {
            selectedStrokes.clear()
            selectedStrokeIds.clear()
            selectionDragDx = 0f
            selectionDragDy = 0f
            isFboDirty = true
        }
    }

    fun removeStrokes(strokeIds: Set<String>) {
        highlighterStore.removeStrokes(strokeIds, ::getOrCreateMesh)
        inkStore.removeStrokes(strokeIds, ::getOrCreateMesh)
        selectedStrokes.removeAll { it.id in strokeIds }
        selectedStrokeIds.removeAll(strokeIds)
        strokeIds.forEach { meshCache.remove(it) }
        isFboDirty = true
    }

    fun replaceStrokes(removedIds: Set<String>, added: List<Stroke>) {
        highlighterStore.removeStrokes(removedIds, ::getOrCreateMesh)
        inkStore.removeStrokes(removedIds, ::getOrCreateMesh)
        val (highlighters, inks) = added.partition { it.isHighlighter }
        if (highlighters.isNotEmpty()) highlighterStore.appendStrokes(highlighters, ::getOrCreateMesh)
        if (inks.isNotEmpty()) inkStore.appendStrokes(inks, ::getOrCreateMesh)
        selectedStrokes.removeAll { it.id in removedIds }
        selectedStrokeIds.removeAll(removedIds)
        removedIds.forEach { meshCache.remove(it) }
        isFboDirty = true
    }

    fun clearCanvas() {
        highlighterStore.clear()
        inkStore.clear()
        images.clear()
        selectedStrokes.clear()
        selectedStrokeIds.clear()
        selectionDragDx = 0f
        selectionDragDy = 0f
        meshCache.clear()
        isFboDirty = true
    }

    fun setImages(newImages: List<app.goodboy13.bignotes.core.model.CanvasImage>) {
        images.clear()
        images.addAll(newImages)
        isFboDirty = true
    }

    fun addImage(image: app.goodboy13.bignotes.core.model.CanvasImage) {
        images.add(image)
        isFboDirty = true
    }

    fun removeImage(imageId: String) {
        images.removeAll { it.id == imageId }
        isFboDirty = true
    }

    fun updateImage(image: app.goodboy13.bignotes.core.model.CanvasImage) {
        val idx = images.indexOfFirst { it.id == image.id }
        if (idx >= 0) {
            images[idx] = image
            isFboDirty = true
        }
    }

    private fun getFrontBuffer(vertexCount: Int): FloatBuffer {
        val requiredBytes = vertexCount * StrokeTessellator.STRIDE_BYTES
        val currentBuf = frontBufferDirectByteBuffer
        if (currentBuf == null || currentBuf.capacity() < requiredBytes) {
            val newCapVertices = maxOf(frontBufferCapacityVertices, vertexCount * 2)
            val newBytes = newCapVertices * StrokeTessellator.STRIDE_BYTES
            val byteBuf = ByteBuffer.allocateDirect(newBytes).order(ByteOrder.nativeOrder())
            frontBufferDirectByteBuffer = byteBuf
            frontBufferFloatBuffer = byteBuf.asFloatBuffer()
            frontBufferCapacityVertices = newCapVertices
        }
        val fb = frontBufferFloatBuffer!!
        fb.clear()
        return fb
    }

    fun getCommittedStrokes(): List<Stroke> {
        val all = highlighterStore.getAllStrokes() + inkStore.getAllStrokes()
        return all.sortedBy { it.zIndex }
    }

    fun getMaxStrokeY(): Float = maxOf(highlighterStore.getMaxStrokeY(), inkStore.getMaxStrokeY())

    private fun ensureGlInitialized(width: Int, height: Int) {
        if (!isGlInitialized) {
            val shader = GraphiteShader(context)
            shader.init()
            graphiteShader = shader
            applyInkShaderParams()

            val blit = BlitShader()
            blit.init()
            blitShader = blit

            pageSeparatorRenderer.initGl()
            gridRenderer.initGl()
            imageQuadRenderer.initGl()

            noiseTextureId = NoiseTextureGenerator.createPaperToothTexture()
            isGlInitialized = true
        }


        val maxTexSize = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTexSize, 0)
        val maxDim = if (maxTexSize[0] > 0) maxTexSize[0] else 4096

        val rawW = (width * overscanFactor).toInt()
        val rawH = (height * overscanFactor).toInt()
        val targetFboWidth = (((rawW + 15) / 16) * 16).coerceIn(width, maxDim)
        val targetFboHeight = (((rawH + 15) / 16) * 16).coerceIn(height, maxDim)

        if (committedFbo == null) {
            committedFbo = FboLayer()
        }
        val fbo = committedFbo!!
        if (fbo.width < targetFboWidth || fbo.height < targetFboHeight) {
            val allocW = maxOf(fbo.width, targetFboWidth).coerceIn(width, maxDim)
            val allocH = maxOf(fbo.height, targetFboHeight).coerceIn(height, maxDim)
            fbo.init(allocW, allocH)
            fboWidth = allocW
            fboHeight = allocH
            isFboDirty = true
        }
    }

    private fun computeWorldToViewMatrix(
        matrix: FloatArray,
        viewWidth: Float,
        panX: Float,
        panY: Float,
        zoom: Float,
        rotation: Float,
        isFlipped: Boolean,
        worldOffsetX: Float = 0f,
        worldOffsetY: Float = 0f
    ) {
        Matrix.setIdentityM(matrix, 0)
        if (isFlipped) {
            val flipCenterX = viewOffsetX + (viewWidth - viewOffsetX) * 0.5f
            Matrix.translateM(matrix, 0, flipCenterX, 0f, 0f)
            Matrix.scaleM(matrix, 0, -1f, 1f, 1f)
            Matrix.translateM(matrix, 0, -flipCenterX, 0f, 0f)
        }
        Matrix.translateM(matrix, 0, panX + viewOffsetX, panY + viewOffsetY, 0f)
        val effScale = zoom * pageScale
        Matrix.scaleM(matrix, 0, effScale, effScale, 1.0f)
        if (rotation != 0f) {
            Matrix.rotateM(matrix, 0, rotation, 0f, 0f, 1f)
        }
        if (worldOffsetX != 0f || worldOffsetY != 0f) {
            Matrix.translateM(matrix, 0, worldOffsetX, worldOffsetY, 0f)
        }
    }

    // --- GLFrontBufferedRenderer.Callback Implementation ---

    override fun onDrawFrontBufferedLayer(
        eglManager: EGLManager,
        width: Int,
        height: Int,
        bufferInfo: BufferInfo,
        transform: FloatArray,
        param: FrontBufferPacket
    ) {
        currentViewWidth = width.toFloat()
        ensureGlInitialized(width, height)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bufferInfo.frameBufferId)
        GLES30.glViewport(0, 0, bufferInfo.width, bufferInfo.height)

        if (param.clearBeforeDraw) {
            GLES30.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        if (param.dabs.isEmpty()) return

        GLES30.glEnable(GLES30.GL_BLEND)
        // Premultiplied alpha: writes stroke alpha to front buffer for SurfaceFlinger compositor
        GLES30.glBlendFuncSeparate(
            GLES30.GL_ONE,
            GLES30.GL_ONE_MINUS_SRC_ALPHA,
            GLES30.GL_ONE,
            GLES30.GL_ONE_MINUS_SRC_ALPHA
        )

        val shader = graphiteShader ?: return

        // 1. screenOrthoMatrix: maps buffer pixels [0..bufW, 0..bufH] to buffer NDC [-1, 1]
        Matrix.orthoM(screenOrthoMatrix, 0, 0f, bufferInfo.width.toFloat(), 0f, bufferInfo.height.toFloat(), -1f, 1f)
        // 2. screenProjectionMatrix: maps view pixels -> buffer pixels -> buffer NDC
        Matrix.multiplyMM(screenProjectionMatrix, 0, screenOrthoMatrix, 0, transform, 0)

        // 3. currentWorldToViewMatrix: maps canvas world coordinates -> view pixels
        computeWorldToViewMatrix(
            currentWorldToViewMatrix,
            width.toFloat(),
            panX, panY, zoomScale, rotationDegrees, isFlippedHorizontal
        )

        // 4. Final MVP: world coordinates -> view pixels -> buffer NDC
        Matrix.multiplyMM(frontBufferMvpMatrix, 0, screenProjectionMatrix, 0, currentWorldToViewMatrix, 0)

        shader.invertColors = isDarkMode
        shader.use(frontBufferMvpMatrix, noiseTextureId)

        val targetBuf = getFrontBuffer(param.dabs.size * 6)
        val mesh = StrokeTessellator.tessellateDabs(
            param.dabs,
            currentTone,
            currentBaseWidth,
            targetBuf,
            colorRgb = currentColorRgb,
            isInk = isCurrentInk,
            isHighlighter = isCurrentHighlighter
        )
        shader.renderMesh(mesh.vertexBuffer, mesh.vertexCount)
    }

    override fun onDrawMultiBufferedLayer(
        eglManager: EGLManager,
        width: Int,
        height: Int,
        bufferInfo: BufferInfo,
        transform: FloatArray,
        params: Collection<FrontBufferPacket>
    ) {
        currentViewWidth = width.toFloat()
        ensureGlInitialized(width, height)

        val shader = graphiteShader ?: return

        // 1. Direct bind to target screen buffer (SurfaceFlinger front-buffered swapchain)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bufferInfo.frameBufferId)
        GLES30.glViewport(0, 0, bufferInfo.width, bufferInfo.height)

        // Clear target surface to current PaperColor
        GLES30.glClearColor(paperColor.r, paperColor.g, paperColor.b, paperColor.a)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        // 2. Compute screen projection & world-to-view matrices
        // screenOrthoMatrix: maps buffer pixels [0..bufW, 0..bufH] to buffer NDC [-1, 1]
        Matrix.orthoM(screenOrthoMatrix, 0, 0f, bufferInfo.width.toFloat(), 0f, bufferInfo.height.toFloat(), -1f, 1f)
        // screenProjectionMatrix: maps view pixels -> buffer pixels -> buffer NDC
        Matrix.multiplyMM(screenProjectionMatrix, 0, screenOrthoMatrix, 0, transform, 0)

        // currentWorldToViewMatrix: maps canvas world coordinates -> view pixels
        computeWorldToViewMatrix(
            currentWorldToViewMatrix,
            width.toFloat(),
            panX, panY, zoomScale, rotationDegrees, isFlippedHorizontal
        )

        // directScreenMvpMatrix: maps canvas world coordinates -> view pixels -> buffer NDC
        Matrix.multiplyMM(directScreenMvpMatrix, 0, screenProjectionMatrix, 0, currentWorldToViewMatrix, 0)

        // Static Chunk FBO Compositor: during interactive gestures (pinch, pan, rotate, fling),
        // blit the pre-rendered committedFbo texture in a single hardware quad pass (<0.2ms GPU time)
        // rather than recalculating and drawing hundreds of chunk meshes at 120Hz.
        if (isInteractiveGesture) {
            val fbo = committedFbo
            if (fbo != null && fbo.textureId != 0) {
                val zoomRatio = if (fboZoom > 0f) zoomScale / fboZoom else 1.0f
                val effScale = zoomScale * pageScale
                val driftX = kotlin.math.abs(panX - fboPanX) * effScale
                val driftY = kotlin.math.abs(panY - fboPanY) * effScale
                val maxDriftX = (fboWidth - width) * 0.40f
                val maxDriftY = (fboHeight - height) * 0.40f
                val driftExceeded = zoomRatio !in 0.70f..1.40f || driftX > maxDriftX || driftY > maxDriftY || (isFlippedHorizontal != fboFlipped)

                if (isFboDirty || driftExceeded) {
                    renderCommittedFbo(width, height)
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bufferInfo.frameBufferId)
                    GLES30.glViewport(0, 0, bufferInfo.width, bufferInfo.height)
                }

                if (Matrix.invertM(invFboMvpMatrix, 0, fboMvpMatrix, 0)) {
                    Matrix.multiplyMM(finalBlitMatrix, 0, directScreenMvpMatrix, 0, invFboMvpMatrix, 0)
                    blitShader?.blit(fbo.textureId, finalBlitMatrix, -1f, -1f, 1f, 1f, enableBlend = false)
                }

                // Render lasso-selected strokes directly with real-time translation offset
                if (selectedStrokes.isNotEmpty()) {
                    computeWorldToViewMatrix(
                        selectionWorldToViewMatrix,
                        width.toFloat(),
                        panX, panY, zoomScale, rotationDegrees, isFlippedHorizontal,
                        worldOffsetX = selectionDragDx,
                        worldOffsetY = selectionDragDy
                    )
                    Matrix.multiplyMM(selectionMvpMatrix, 0, screenProjectionMatrix, 0, selectionWorldToViewMatrix, 0)
                    shader.invertColors = isDarkMode
                    shader.use(selectionMvpMatrix, noiseTextureId)
                    val (selHighlighters, selInks) = selectedStrokes.partition { it.isHighlighter }
                    if (selHighlighters.isNotEmpty()) {
                        highlighterStore.renderStrokesDirect(shader, selHighlighters, ::getOrCreateMesh)
                    }
                    if (selInks.isNotEmpty()) {
                        inkStore.renderStrokesDirect(shader, selInks, ::getOrCreateMesh)
                    }
                }
                return
            }
        }

        // 3. Viewport AABB Frustum Culling (only visible chunks are drawn)
        var visMinX = Float.POSITIVE_INFINITY
        var visMinY = Float.POSITIVE_INFINITY
        var visMaxX = Float.NEGATIVE_INFINITY
        var visMaxY = Float.NEGATIVE_INFINITY

        if (Matrix.invertM(invWorldToViewMatrix, 0, currentWorldToViewMatrix, 0)) {
            val w = width.toFloat()
            val h = height.toFloat()
            corners[0] = 0f; corners[1] = 0f
            corners[2] = w; corners[3] = 0f
            corners[4] = w; corners[5] = h
            corners[6] = 0f; corners[7] = h

            cornerIn[2] = 0f
            cornerIn[3] = 1f
            for (ci in 0 until 4) {
                cornerIn[0] = corners[ci * 2]
                cornerIn[1] = corners[ci * 2 + 1]
                Matrix.multiplyMV(cornerOut, 0, invWorldToViewMatrix, 0, cornerIn, 0)
                val invW = 1.0f / cornerOut[3]
                val wx = cornerOut[0] * invW
                val wy = cornerOut[1] * invW
                if (wx < visMinX) visMinX = wx
                if (wy < visMinY) visMinY = wy
                if (wx > visMaxX) visMaxX = wx
                if (wy > visMaxY) visMaxY = wy
            }
            val margin = 64f
            visMinX -= margin
            visMinY -= margin
            visMaxX += margin
            visMaxY += margin
        } else {
            visMinX = Float.NEGATIVE_INFINITY
            visMinY = Float.NEGATIVE_INFINITY
            visMaxX = Float.POSITIVE_INFINITY
            visMaxY = Float.POSITIVE_INFINITY
        }

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        // 3.4 Render procedural background grid (Dots or Squares)
        if (gridConfig.enabled) {
            if (Matrix.invertM(invDirectScreenMvpMatrix, 0, directScreenMvpMatrix, 0)) {
                gridRenderer.draw(
                    invDirectScreenMvpMatrix,
                    paperColor,
                    gridConfig,
                    zoomScale * pageScale
                )
            }
        }

        // 3.5 Render page separator lines beneath strokes with zero latency
        if (showPageSeparators) {
            pageSeparatorRenderer.draw(
                directScreenMvpMatrix,
                visMinY, visMaxY,
                paperColor,
                basePageWidth,
                worldPageHeight
            )
        }

        // 3.6 Render inserted images directly on paper, beneath strokes
        if (images.isNotEmpty()) {
            val visibleImages = images.filter { img ->
                val halfW = img.width * 0.5f
                val halfH = img.height * 0.5f
                img.centerX + halfW >= visMinX && img.centerX - halfW <= visMaxX &&
                img.centerY + halfH >= visMinY && img.centerY - halfH <= visMaxY
            }.sortedBy { it.zIndex }
            imageQuadRenderer.render(visibleImages, directScreenMvpMatrix, context)
        }

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        shader.invertColors = isDarkMode
        shader.use(directScreenMvpMatrix, noiseTextureId)


        // 4. Render visible highlighters FIRST so they naturally sit BEHIND ink strokes!
        highlighterStore.renderVisibleChunks(
            shader,
            visMinX, visMinY, visMaxX, visMaxY,
            selectedStrokeIds,
            ::getOrCreateMesh
        )

        // 4.1. Render visible pen, pencil, and other ink strokes ON TOP of highlighters!
        inkStore.renderVisibleChunks(
            shader,
            visMinX, visMinY, visMaxX, visMaxY,
            selectedStrokeIds,
            ::getOrCreateMesh
        )

        // 5. Render lasso-selected strokes directly with real-time translation offset
        if (selectedStrokes.isNotEmpty()) {
            computeWorldToViewMatrix(
                selectionWorldToViewMatrix,
                width.toFloat(),
                panX, panY, zoomScale, rotationDegrees, isFlippedHorizontal,
                worldOffsetX = selectionDragDx,
                worldOffsetY = selectionDragDy
            )
            Matrix.multiplyMM(selectionMvpMatrix, 0, screenProjectionMatrix, 0, selectionWorldToViewMatrix, 0)
            shader.invertColors = isDarkMode
            shader.use(selectionMvpMatrix, noiseTextureId)
            val (selHighlighters, selInks) = selectedStrokes.partition { it.isHighlighter }
            if (selHighlighters.isNotEmpty()) {
                highlighterStore.renderStrokesDirect(shader, selHighlighters, ::getOrCreateMesh)
            }
            if (selInks.isNotEmpty()) {
                inkStore.renderStrokesDirect(shader, selInks, ::getOrCreateMesh)
            }
        }

        // Keep committedFbo primed with latest camera and strokes so interactive gestures start instantly
        renderCommittedFbo(width, height)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bufferInfo.frameBufferId)
        GLES30.glViewport(0, 0, bufferInfo.width, bufferInfo.height)

        val err = GLES30.glGetError()
        if (err != GLES30.GL_NO_ERROR) {
            android.util.Log.e("SketchCanvasRenderer", "GL Error in onDrawMultiBufferedLayer: $err")
        }
    }

    /**
     * Re-renders all visible committed strokes into the committedFbo layer at current camera transform.
     */
    fun renderCommittedFbo(width: Int, height: Int) {
        val fbo = committedFbo ?: return
        val shader = graphiteShader ?: return

        val offsetX = (fbo.width - width) * 0.5f
        val offsetY = (fbo.height - height) * 0.5f

        fbo.bind()
        fbo.clearColor(paperColor.r, paperColor.g, paperColor.b, paperColor.a)

        Matrix.orthoM(
            fboOrthoMatrix, 0,
            -offsetX, width.toFloat() + offsetX,
            -offsetY, height.toFloat() + offsetY,
            -1f, 1f
        )

        computeWorldToViewMatrix(
            fboWorldToViewMatrix,
            width.toFloat(),
            panX, panY, zoomScale, rotationDegrees, isFlippedHorizontal
        )

        Matrix.multiplyMM(fboMvpMatrix, 0, fboOrthoMatrix, 0, fboWorldToViewMatrix, 0)

        // Compute visible world bounds for frustum culling
        var visMinX = Float.POSITIVE_INFINITY
        var visMinY = Float.POSITIVE_INFINITY
        var visMaxX = Float.NEGATIVE_INFINITY
        var visMaxY = Float.NEGATIVE_INFINITY

        if (Matrix.invertM(invFboWorldToViewMatrix, 0, fboWorldToViewMatrix, 0)) {
            val x0 = -offsetX
            val y0 = -offsetY
            val x1 = width.toFloat() + offsetX
            val y1 = height.toFloat() + offsetY

            corners[0] = x0; corners[1] = y0
            corners[2] = x1; corners[3] = y0
            corners[4] = x1; corners[5] = y1
            corners[6] = x0; corners[7] = y1

            cornerIn[2] = 0f
            cornerIn[3] = 1f
            for (ci in 0 until 4) {
                cornerIn[0] = corners[ci * 2]
                cornerIn[1] = corners[ci * 2 + 1]
                Matrix.multiplyMV(cornerOut, 0, invFboWorldToViewMatrix, 0, cornerIn, 0)
                val invW = 1.0f / cornerOut[3]
                val wx = cornerOut[0] * invW
                val wy = cornerOut[1] * invW
                if (wx < visMinX) visMinX = wx
                if (wy < visMinY) visMinY = wy
                if (wx > visMaxX) visMaxX = wx
                if (wy > visMaxY) visMaxY = wy
            }
            val margin = 64f
            visMinX -= margin
            visMinY -= margin
            visMaxX += margin
            visMaxY += margin
        } else {
            visMinX = Float.NEGATIVE_INFINITY
            visMinY = Float.NEGATIVE_INFINITY
            visMaxX = Float.POSITIVE_INFINITY
            visMaxY = Float.POSITIVE_INFINITY
        }

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        // Render procedural background grid into FBO
        if (gridConfig.enabled) {
            if (Matrix.invertM(invFboMvpMatrix, 0, fboMvpMatrix, 0)) {
                gridRenderer.draw(
                    invFboMvpMatrix,
                    paperColor,
                    gridConfig,
                    zoomScale * pageScale
                )
            }
        }

        if (showPageSeparators) {
            pageSeparatorRenderer.draw(
                fboMvpMatrix,
                visMinY, visMaxY,
                paperColor,
                basePageWidth,
                worldPageHeight
            )
        }

        // Render inserted images directly on paper, beneath strokes
        if (images.isNotEmpty()) {
            val visibleImages = images.filter { img ->
                val halfW = img.width * 0.5f
                val halfH = img.height * 0.5f
                img.centerX + halfW >= visMinX && img.centerX - halfW <= visMaxX &&
                img.centerY + halfH >= visMinY && img.centerY - halfH <= visMaxY
            }.sortedBy { it.zIndex }
            imageQuadRenderer.render(visibleImages, fboMvpMatrix, context)
        }

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        shader.invertColors = isDarkMode
        shader.use(fboMvpMatrix, noiseTextureId)


        // Render visible highlighters first, then ink strokes
        highlighterStore.renderVisibleChunks(
            shader,
            visMinX, visMinY, visMaxX, visMaxY,
            selectedStrokeIds,
            ::getOrCreateMesh
        )
        inkStore.renderVisibleChunks(
            shader,
            visMinX, visMinY, visMaxX, visMaxY,
            selectedStrokeIds,
            ::getOrCreateMesh
        )

        fbo.unbind()

        fboPanX = panX
        fboPanY = panY
        fboViewOffsetX = viewOffsetX
        fboViewOffsetY = viewOffsetY
        fboZoom = zoomScale
        fboPageScale = pageScale
        fboRotation = rotationDegrees
        fboFlipped = isFlippedHorizontal
        isFboDirty = false
    }

    // Pre-allocated direct buffer for 5x5 pixel sampling (100 bytes, allocated once)
    private val sampleBuffer: ByteBuffer = ByteBuffer.allocateDirect(5 * 5 * 4).order(ByteOrder.nativeOrder())
    private val prevFboBinding = IntArray(1)
    private val pixelR = IntArray(25)
    private val pixelG = IntArray(25)
    private val pixelB = IntArray(25)
    private val kernelWeights = floatArrayOf(
        0.125f, 0.25f, 0.5f, 0.25f, 0.125f,
        0.25f,  1.0f,  2.0f, 1.0f,  0.25f,
        0.5f,   2.0f,  4.0f, 2.0f,  0.5f,
        0.25f,  1.0f,  2.0f, 1.0f,  0.25f,
        0.125f, 0.25f, 0.5f, 0.25f, 0.125f
    )

    /**
     * Reads a 5x5 window of actual rendered pixels from committedFbo around the given screen coordinates,
     * computing the optical average color visible to the user.
     * Uses distance-weighted pigment discrimination to prevent thin lines and shading from being diluted by paper.
     * Must be called on the GL thread where the OpenGL context is current.
     */
    fun sampleRenderedPixel(screenX: Float, screenY: Float, viewWidth: Int, viewHeight: Int): Long {
        ensureGlInitialized(viewWidth, viewHeight)
        val fbo = committedFbo ?: return paperColor.colorHex
        if (fbo.fboId == 0 || fbo.width <= 0 || fbo.height <= 0) {
            return paperColor.colorHex
        }

        val curCtx = android.opengl.EGL14.eglGetCurrentContext()
        if (curCtx == null || curCtx == android.opengl.EGL14.EGL_NO_CONTEXT) {
            android.util.Log.e("SketchCanvasRenderer", "sampleRenderedPixel: EGL context is NOT current!")
            return paperColor.colorHex
        }

        // If FBO is dirty or camera drifted, re-render FBO immediately so committedFbo matches display 1:1
        val panDx = kotlin.math.abs(panX - fboPanX)
        val panDy = kotlin.math.abs(panY - fboPanY)
        val offsetDx = kotlin.math.abs(viewOffsetX - fboViewOffsetX)
        val offsetDy = kotlin.math.abs(viewOffsetY - fboViewOffsetY)
        val zoomDrift = if (fboZoom > 0f) kotlin.math.abs(zoomScale - fboZoom) / fboZoom else 0f
        val rotDrift = kotlin.math.abs(rotationDegrees - fboRotation)
        val flipChanged = isFlippedHorizontal != fboFlipped

        if (isFboDirty || flipChanged || panDx > 0.5f || panDy > 0.5f || offsetDx > 0.5f || offsetDy > 0.5f || zoomDrift > 0.001f || rotDrift > 0.05f) {
            renderCommittedFbo(viewWidth, viewHeight)
        }

        val offsetX = (fbo.width - viewWidth) * 0.5f
        val offsetY = (fbo.height - viewHeight) * 0.5f

        val centerGlX = (screenX + offsetX).toInt()
        val centerGlY = (screenY + offsetY).toInt()

        val sampleSize = 5
        val halfSize = sampleSize / 2
        val x0 = (centerGlX - halfSize).coerceIn(0, fbo.width - sampleSize)
        val y0 = (centerGlY - halfSize).coerceIn(0, fbo.height - sampleSize)

        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, prevFboBinding, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo.fboId)

        sampleBuffer.position(0)
        GLES30.glReadPixels(x0, y0, sampleSize, sampleSize, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, sampleBuffer)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, prevFboBinding[0])

        val paperR = (paperColor.r * 255f + 0.5f).toInt()
        val paperG = (paperColor.g * 255f + 0.5f).toInt()
        val paperB = (paperColor.b * 255f + 0.5f).toInt()

        sampleBuffer.position(0)
        for (i in 0 until 25) {
            pixelR[i] = sampleBuffer.get().toInt() and 0xFF
            pixelG[i] = sampleBuffer.get().toInt() and 0xFF
            pixelB[i] = sampleBuffer.get().toInt() and 0xFF
            sampleBuffer.get() // skip alpha
        }

        // Center pixel (directly under pen/reticle) is at row 2, col 2: index = 12
        val centerIndex = 12
        val centerR = pixelR[centerIndex]
        val centerG = pixelG[centerIndex]
        val centerB = pixelB[centerIndex]

        val centerDistToPaper = kotlin.math.abs(centerR - paperR) +
                kotlin.math.abs(centerG - paperG) +
                kotlin.math.abs(centerB - paperB)

        var pigmentCount = 0
        for (i in 0 until 25) {
            val d = kotlin.math.abs(pixelR[i] - paperR) +
                    kotlin.math.abs(pixelG[i] - paperG) +
                    kotlin.math.abs(pixelB[i] - paperB)
            if (d > 15) {
                pigmentCount++
            }
        }

        // If pen is on bare paper and no pigment cluster in 5x5 window, return paper color
        if (centerDistToPaper <= 15 && pigmentCount < 3) {
            return paperColor.colorHex
        }

        // Threshold to ignore bare paper gaps in textured pencil shading or surrounding thin lines
        val paperThreshold = if (centerDistToPaper > 20) 12 else 6

        var totalWeight = 0f
        var sumR = 0f
        var sumG = 0f
        var sumB = 0f

        for (i in 0 until 25) {
            val distToPaper = kotlin.math.abs(pixelR[i] - paperR) +
                    kotlin.math.abs(pixelG[i] - paperG) +
                    kotlin.math.abs(pixelB[i] - paperB)

            // Include pigment pixels with kernel weights, excluding bare paper background
            if (distToPaper >= paperThreshold) {
                val w = kernelWeights[i]
                sumR += pixelR[i] * w
                sumG += pixelG[i] * w
                sumB += pixelB[i] * w
                totalWeight += w
            }
        }

        if (totalWeight <= 0f) {
            return paperColor.colorHex
        }

        val avgR = (sumR / totalWeight + 0.5f).toInt().coerceIn(0, 255)
        val avgG = (sumG / totalWeight + 0.5f).toInt().coerceIn(0, 255)
        val avgB = (sumB / totalWeight + 0.5f).toInt().coerceIn(0, 255)

        return 0xFF000000L or (avgR.toLong() shl 16) or (avgG.toLong() shl 8) or avgB.toLong()
    }

    fun cleanup() {
        graphiteShader?.cleanup()
        graphiteShader = null
        blitShader?.cleanup()
        blitShader = null
        committedFbo?.cleanup()
        committedFbo = null
        pageSeparatorRenderer.cleanup()
        if (noiseTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(noiseTextureId), 0)
            noiseTextureId = 0
        }
        highlighterStore.cleanup()
        inkStore.cleanup()
        imageQuadRenderer.releaseTextures()
        frontBufferDirectByteBuffer = null
        frontBufferFloatBuffer = null
        meshCache.clear()
        isGlInitialized = false
    }

}

