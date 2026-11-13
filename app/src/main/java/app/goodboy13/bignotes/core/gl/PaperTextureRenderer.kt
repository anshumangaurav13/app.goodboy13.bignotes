package app.goodboy13.bignotes.core.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import app.goodboy13.bignotes.core.model.PaperColor
import app.goodboy13.bignotes.ui.theme.NotebookThemePreset
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance GLES 3.0 physical paper texture renderer.
 *
 * Renders authentic, scanned physical paper surfaces in world space:
 * - Classic Saddle: Antique Ivory Laid Cotton-Rag Vellum with fine laid watermark wires & cotton fibers.
 * - Traveler's Espresso: Field Journal Raw Kraft Paper with organic wood-pulp tooth & natural bark flecks.
 * - Vintage Oxford: Archival Manuscript Parchment with historical mottling, follicular pores & honey patina.
 * - Studio Clean: Clean smooth paper.
 *
 * Reconstructs world-space coordinates via inverse MVP matrix so the physical paper
 * remains anchored to the paper sheet during pan and zoom gestures.
 * Uses hardware mipmapping for silky-smooth 120 FPS rendering without aliasing or moiré.
 */
class PaperTextureRenderer {

    private var programId = 0
    private var vboId = 0

    // Uniform locations
    private var uInvMvpLoc = -1
    private var uPaperTextureLoc = -1
    private var uPaperColorLoc = -1
    private var uIsDarkLoc = -1
    private var uScaleLoc = -1
    private var uOpacityLoc = -1

    // Pre-loaded texture IDs
    private var vellumTexId = 0
    private var kraftTexId = 0
    private var parchmentTexId = 0

    private val vertexShaderSource = """
        #version 300 es
        layout(location = 0) in vec2 a_position;

        uniform mat4 u_InvMvpMatrix;

        out vec2 v_worldPos;

        void main() {
            gl_Position = vec4(a_position, 0.0, 1.0);
            vec4 worldP = u_InvMvpMatrix * vec4(a_position, 0.0, 1.0);
            v_worldPos = worldP.xy / worldP.w;
        }
    """.trimIndent()

    private val fragmentShaderSource = """
        #version 300 es
        precision highp float;

        in vec2 v_worldPos;

        uniform sampler2D u_PaperTexture;
        uniform vec3 u_PaperColor;
        uniform int u_IsDark;
        uniform float u_Scale;
        uniform float u_Opacity;

        out vec4 fragColor;

        void main() {
            // World-anchored texture coordinates with physical sheet scaling
            vec2 uv = v_worldPos * u_Scale;

            // Sample the authentic photographic scanned paper texture (with hardware mipmapping)
            vec4 tex = texture(u_PaperTexture, uv);

            vec3 finalRgb;
            if (u_IsDark == 0) {
                // Light Mode:
                // Micro-relief lighting from surface luminance gradient
                float luma = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
                vec2 grad = vec2(dFdx(luma), dFdy(luma));
                float relief = dot(grad, normalize(vec2(-0.7071, -0.7071))) * 0.45;

                // Blend the calibrated photographic paper with subtle micro-relief
                vec3 paperRgb = tex.rgb + relief * 0.035;
                finalRgb = mix(u_PaperColor, paperRgb, u_Opacity);
            } else {
                // Dark Mode / Night Journal:
                // Use texture luminance to highlight paper fibers & flecks against deep dark leather/parchment
                float luma = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
                vec2 grad = vec2(dFdx(luma), dFdy(luma));
                float relief = dot(grad, normalize(vec2(-0.7071, -0.7071))) * 0.45;

                vec3 fiberHighlights = (luma - 0.5 + relief * 0.3) * vec3(0.09, 0.08, 0.07);
                finalRgb = clamp(u_PaperColor + fiberHighlights * u_Opacity, 0.0, 1.0);
            }

            fragColor = vec4(finalRgb, 1.0);
        }
    """.trimIndent()

    fun initGl(context: Context) {
        if (programId != 0) return

        val vs = loadShader(GLES30.GL_VERTEX_SHADER, vertexShaderSource)
        val fs = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderSource)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vs)
        GLES30.glAttachShader(programId, fs)
        GLES30.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            programId = 0
            throw RuntimeException("PaperTextureRenderer program link failed: $log")
        }

        uInvMvpLoc = GLES30.glGetUniformLocation(programId, "u_InvMvpMatrix")
        uPaperTextureLoc = GLES30.glGetUniformLocation(programId, "u_PaperTexture")
        uPaperColorLoc = GLES30.glGetUniformLocation(programId, "u_PaperColor")
        uIsDarkLoc = GLES30.glGetUniformLocation(programId, "u_IsDark")
        uScaleLoc = GLES30.glGetUniformLocation(programId, "u_Scale")
        uOpacityLoc = GLES30.glGetUniformLocation(programId, "u_Opacity")

        // Load authentic physical paper textures from assets
        vellumTexId = loadTextureFromAsset(context, "paper/paper_vellum.webp")
        kraftTexId = loadTextureFromAsset(context, "paper/paper_kraft.webp")
        parchmentTexId = loadTextureFromAsset(context, "paper/paper_parchment.webp")

        // Fullscreen quad covering NDC [-1, 1]
        val quadVertices = floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
             1.0f,  1.0f,
            -1.0f, -1.0f,
              1.0f,  1.0f,
            -1.0f,  1.0f
        )
        val fb = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        fb.put(quadVertices).position(0)

        val vbos = IntArray(1)
        GLES30.glGenBuffers(1, vbos, 0)
        vboId = vbos[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quadVertices.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw(
        invMvpMatrix: FloatArray,
        paperColor: PaperColor,
        preset: NotebookThemePreset
    ) {
        if (preset == NotebookThemePreset.STUDIO_CLEAN || programId == 0) return

        val texId = when (preset) {
            NotebookThemePreset.CLASSIC_SADDLE -> vellumTexId
            NotebookThemePreset.TRAVELERS_ESPRESSO -> kraftTexId
            NotebookThemePreset.VINTAGE_OXFORD -> parchmentTexId
            NotebookThemePreset.STUDIO_CLEAN -> 0
        }
        if (texId == 0) return

        GLES30.glUseProgram(programId)

        GLES30.glUniformMatrix4fv(uInvMvpLoc, 1, false, invMvpMatrix, 0)
        GLES30.glUniform3f(uPaperColorLoc, paperColor.r, paperColor.g, paperColor.b)
        GLES30.glUniform1i(uIsDarkLoc, if (paperColor.isDark) 1 else 0)
        // 1 tile = 1200 world units (matches CANONICAL_PAGE_WIDTH for 1:1 true-to-life paper scale)
        GLES30.glUniform1f(uScaleLoc, 1.0f / 1200.0f)
        GLES30.glUniform1f(uOpacityLoc, 1.0f)

        // Bind active paper texture to texture unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glUniform1i(uPaperTextureLoc, 0)

        // Disable blending to directly write background pixels
        GLES30.glDisable(GLES30.GL_BLEND)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    fun destroyGl() {
        val textures = intArrayOf(vellumTexId, kraftTexId, parchmentTexId)
        GLES30.glDeleteTextures(textures.size, textures, 0)
        vellumTexId = 0
        kraftTexId = 0
        parchmentTexId = 0

        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }

    private fun loadTextureFromAsset(context: Context, assetPath: String): Int {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        if (texId == 0) return 0

        val bitmap: Bitmap? = try {
            context.assets.open(assetPath).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }

        if (bitmap == null) {
            GLES30.glDeleteTextures(1, texIds, 0)
            return 0
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)

        bitmap.recycle()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return texId
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compilation failed: $log")
        }
        return shader
    }
}
