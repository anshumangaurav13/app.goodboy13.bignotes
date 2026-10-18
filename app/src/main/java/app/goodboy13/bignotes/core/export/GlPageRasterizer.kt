package app.goodboy13.bignotes.core.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.opengl.*
import app.goodboy13.bignotes.core.geometry.StrokeTessellator
import app.goodboy13.bignotes.core.gl.GraphiteShader
import app.goodboy13.bignotes.core.gl.NoiseTextureGenerator
import app.goodboy13.bignotes.core.model.InkBleedConfig
import app.goodboy13.bignotes.core.model.Stroke
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * Headless, offscreen OpenGL ES 3.0 page rasterizer for high-resolution 300 DPI PDF export.
 * Renders notes using the exact GPU GraphiteShader (with 10% ink dilution, outward capillary bleed,
 * and continuous paper tooth noise) into an offscreen FBO, guaranteeing 100% visual parity
 * with the on-screen canvas.
 */
class GlPageRasterizer(private val context: Context) : AutoCloseable {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var shader: GraphiteShader? = null
    private var noiseTextureId: Int = 0
    private val imageQuadRenderer = app.goodboy13.bignotes.core.gl.ImageQuadRenderer()

    private var fboId: Int = 0
    private var fboTextureId: Int = 0
    private var fboWidth: Int = 0
    private var fboHeight: Int = 0

    private val mvpMatrix = FloatArray(16)
    private var directMeshBuffer: FloatBuffer = ByteBuffer.allocateDirect(128 * 1024 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private var readbackBuffer: ByteBuffer? = null

    init {
        initEgl()
        initGl()
    }

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("EGL14: Failed to get default display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("EGL14: Failed to initialize display")
        }

        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            throw RuntimeException("EGL14: Failed to choose GLES3 config")
        }
        val eglConfig = configs[0]!!

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("EGL14: Failed to create GLES3 context")
        }

        val pbufferAttribs = intArrayOf(
            EGL14.EGL_WIDTH, 1,
            EGL14.EGL_HEIGHT, 1,
            EGL14.EGL_NONE
        )
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("EGL14: Failed to create pbuffer surface")
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("EGL14: Failed to make context current on export thread")
        }
    }

    private fun initGl() {
        val s = GraphiteShader(context)
        s.init()
        s.bleedRatio = InkBleedConfig.DEFAULT.bleedRatio
        s.bleedPower = InkBleedConfig.DEFAULT.bleedPower
        s.coreShiftRatio = InkBleedConfig.DEFAULT.coreShiftRatio
        s.bleedBaseOpacity = InkBleedConfig.DEFAULT.bleedBaseOpacity
        s.aaWidth = InkBleedConfig.DEFAULT.aaWidth
        s.paperGrainMix = InkBleedConfig.DEFAULT.paperGrainMix
        shader = s

        noiseTextureId = NoiseTextureGenerator.createPaperToothTexture()
        imageQuadRenderer.initGl()
    }

    private fun ensureFbo(width: Int, height: Int) {
        if (fboId != 0 && fboWidth >= width && fboHeight >= height) return

        cleanupFbo()

        fboWidth = width
        fboHeight = height

        val fbos = IntArray(1)
        GLES30.glGenFramebuffers(1, fbos, 0)
        fboId = fbos[0]

        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        fboTextureId = textures[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTextureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
            fboWidth, fboHeight, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, fboTextureId, 0
        )

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("GlPageRasterizer: FBO incomplete status $status")
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun getDirectMeshBuffer(requiredFloats: Int): FloatBuffer {
        if (directMeshBuffer.capacity() < requiredFloats) {
            val newCap = maxOf(directMeshBuffer.capacity() * 2, requiredFloats + 4096)
            directMeshBuffer = ByteBuffer.allocateDirect(newCap * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        }
        directMeshBuffer.clear()
        return directMeshBuffer
    }

    private fun getReadbackBuffer(requiredBytes: Int): ByteBuffer {
        val current = readbackBuffer
        if (current == null || current.capacity() < requiredBytes) {
            val newBuf = ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.nativeOrder())
            readbackBuffer = newBuf
            return newBuf
        }
        current.clear()
        return current
    }

    /**
     * Renders a partition page of strokes at 300 DPI directly onto the target PDF page Canvas.
     * Divides the page into two vertical memory-safe tiles to guarantee compatibility across
     * all GPU texture limit constraints.
     */
    fun renderPagePartition(
        pageCanvas: Canvas,
        partition: PdfPagePartition,
        pageFormat: PageFormat,
        colorResolver: (Stroke) -> Int,
        filterStroke: (Stroke) -> Boolean = { true },
        clearWhite: Boolean = true,
        images: List<app.goodboy13.bignotes.core.model.CanvasImage> = emptyList()
    ) {
        val marginPt = PdfPagePaginator.MARGIN_PT
        val usableWidthPt = pageFormat.widthPt - (2 * marginPt)
        val usableHeightPt = pageFormat.heightPt - (2 * marginPt)
        val scale = usableWidthPt / PdfPagePaginator.CANONICAL_WIDTH

        val worldMarginX = marginPt / scale
        val worldMarginY = marginPt / scale

        val pageWorldLeft = -worldMarginX
        val pageWorldRight = PdfPagePaginator.CANONICAL_WIDTH + worldMarginX
        val pageWorldTop = partition.worldTopY - worldMarginY
        val pageWorldBottom = pageWorldTop + (pageFormat.heightPt / scale)

        // 300 DPI raster scale: 300 / 72 = 4.166667
        val dpiScale = 300f / 72f
        val pagePixelWidth = (pageFormat.widthPt * dpiScale).roundToInt()
        val numTiles = 2

        val targetStrokes = partition.strokes.filter(filterStroke).sortedBy { if (it.isHighlighter) 0 else 1 }

        for (tileIndex in 0 until numTiles) {
            val tileTopPt = tileIndex * (pageFormat.heightPt.toFloat() / numTiles)
            val tileBottomPt = (tileIndex + 1) * (pageFormat.heightPt.toFloat() / numTiles)
            val tileHeightPt = tileBottomPt - tileTopPt

            val tileWidthPx = pagePixelWidth
            val tileHeightPx = (tileHeightPt * dpiScale).roundToInt()

            val tileWorldTop = pageWorldTop + (tileTopPt / scale)
            val tileWorldBottom = pageWorldTop + (tileBottomPt / scale)

            ensureFbo(tileWidthPx, tileHeightPx)

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
            GLES30.glViewport(0, 0, tileWidthPx, tileHeightPx)

            if (clearWhite) {
                GLES30.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            } else {
                GLES30.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            }

            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)

            // Orthographic projection mapping world coordinates directly to tile pixels.
            // By passing bottom = tileWorldTop and top = tileWorldBottom, the NDC Y coordinates
            // are inverted so that glReadPixels row 0 corresponds directly to the top row of the Bitmap.
            Matrix.orthoM(
                mvpMatrix, 0,
                pageWorldLeft, pageWorldRight,
                tileWorldTop, tileWorldBottom,
                -1f, 1f
            )

            if (images.isNotEmpty()) {
                val tileImages = images.filter { img ->
                    val hw = img.width * 0.5f
                    val hh = img.height * 0.5f
                    img.centerX + hw >= pageWorldLeft && img.centerX - hw <= pageWorldRight &&
                    img.centerY + hh >= tileWorldTop && img.centerY - hh <= tileWorldBottom
                }.sortedBy { it.zIndex }
                if (tileImages.isNotEmpty()) {
                    imageQuadRenderer.render(tileImages, mvpMatrix, context)
                }
            }
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)

            val currentShader = shader ?: return
            currentShader.use(mvpMatrix, noiseTextureId)

            for (stroke in targetStrokes) {
                val pts = stroke.points
                if (pts.isEmpty()) continue

                // Check rough bounding box overlap with tile
                var strokeMinY = Float.MAX_VALUE
                var strokeMaxY = -Float.MAX_VALUE
                for (i in pts.indices) {
                    val y = pts[i].y
                    if (y < strokeMinY) strokeMinY = y
                    if (y > strokeMaxY) strokeMaxY = y
                }
                val margin = stroke.baseWidth * 2.0f
                if (strokeMaxY < tileWorldTop - margin || strokeMinY > tileWorldBottom + margin) {
                    continue
                }

                val strokeColor = colorResolver(stroke)
                val mesh = StrokeTessellator.tessellateToCachedMesh(
                    points = stroke.points,
                    tone = stroke.tone,
                    baseWidth = stroke.baseWidth,
                    colorRgb = (strokeColor.toLong() and 0xFFFFFFFFL),
                    isInk = stroke.isInk || stroke.tone.isInk,
                    isHighlighter = stroke.isHighlighter
                )

                if (mesh.vertexCount > 0) {
                    val buf = getDirectMeshBuffer(mesh.data.size)
                    buf.put(mesh.data, 0, mesh.data.size)
                    buf.position(0)
                    currentShader.renderMesh(buf, mesh.vertexCount)
                }
            }

            // Read pixels back into Bitmap
            val bufferBytes = tileWidthPx * tileHeightPx * 4
            val readBuf = getReadbackBuffer(bufferBytes)
            GLES30.glReadPixels(0, 0, tileWidthPx, tileHeightPx, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, readBuf)
            readBuf.position(0)

            val tileBitmap = Bitmap.createBitmap(tileWidthPx, tileHeightPx, Bitmap.Config.ARGB_8888)
            tileBitmap.copyPixelsFromBuffer(readBuf)

            val dstRect = RectF(0f, tileTopPt, pageFormat.widthPt.toFloat(), tileBottomPt)
            pageCanvas.drawBitmap(tileBitmap, null, dstRect, null)
            tileBitmap.recycle()
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun cleanupFbo() {
        if (fboTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(fboTextureId), 0)
            fboTextureId = 0
        }
        if (fboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            fboId = 0
        }
        fboWidth = 0
        fboHeight = 0
    }

    override fun close() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            cleanupFbo()
            imageQuadRenderer.releaseTextures()

            if (noiseTextureId != 0) {
                GLES30.glDeleteTextures(1, intArrayOf(noiseTextureId), 0)
                noiseTextureId = 0
            }

            shader?.let {
                if (it.programId != 0) {
                    GLES30.glDeleteProgram(it.programId)
                }
            }
            shader = null

            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }
}
