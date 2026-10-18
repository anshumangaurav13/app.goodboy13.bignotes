package app.goodboy13.bignotes.core.gl

import android.opengl.GLES30
import app.goodboy13.bignotes.core.model.GridConfig
import app.goodboy13.bignotes.core.model.GridStyle
import app.goodboy13.bignotes.core.model.PaperColor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance GLES 3.0 procedural background grid renderer.
 *
 * Renders configurable Dots or Squares in canvas world space with a single draw call.
 * Uses analytical screen-space derivatives (fwidth) to guarantee crisp, 1-pixel wide grid lines
 * and resolution-independent dots without shimmering or CPU tessellation overhead.
 */
class GridBackgroundRenderer {

    private var programId = 0
    private var vboId = 0

    // Uniform locations
    private var uInvMvpLoc = -1
    private var uGridSpacingLoc = -1
    private var uOpacityLoc = -1
    private var uGridStyleLoc = -1 // 0 = Dots, 1 = Squares
    private var uDotRadiusLoc = -1
    private var uGridColorLoc = -1

    private val vertexShaderSource = """
        #version 300 es
        layout(location = 0) in vec2 a_position;

        uniform mat4 u_InvMvpMatrix;

        out vec2 v_worldPos;

        void main() {
            gl_Position = vec4(a_position, 0.0, 1.0);
            // Reconstruct world-space position from NDC [-1, 1]
            vec4 worldP = u_InvMvpMatrix * vec4(a_position, 0.0, 1.0);
            v_worldPos = worldP.xy / worldP.w;
        }
    """.trimIndent()

    private val fragmentShaderSource = """
        #version 300 es
        precision highp float;

        in vec2 v_worldPos;

        uniform float u_GridSpacing;
        uniform float u_Opacity;
        uniform int u_GridStyle;
        uniform float u_DotRadius;
        uniform vec3 u_GridColor;

        out vec4 fragColor;

        void main() {
            float spacing = max(u_GridSpacing, 8.0);
            vec2 gridCoord = v_worldPos / spacing;

            float patternAlpha = 0.0;

            if (u_GridStyle == 0) {
                // --- DOT GRID ---
                // Distance from closest grid intersection in world units
                vec2 cellFract = fract(gridCoord + 0.5) - 0.5;
                float distWorld = length(cellFract * spacing);
                float radius = max(u_DotRadius, 0.5);
                // Antialiased circle edge
                patternAlpha = 1.0 - smoothstep(radius - 0.8, radius + 0.8, distWorld);
            } else {
                // --- SQUARE GRID (LINES) ---
                // Analytical 1-pixel screen-space line anti-aliasing via fwidth
                vec2 gridDerivative = fwidth(gridCoord);
                vec2 lineDist = abs(fract(gridCoord - 0.5) - 0.5) / max(gridDerivative, vec2(1e-5));
                float line = min(lineDist.x, lineDist.y);
                patternAlpha = 1.0 - min(line, 1.0);
            }

            if (patternAlpha <= 0.001) discard;

            float finalAlpha = patternAlpha * u_Opacity;
            // Premultiplied alpha output for clean blending over paper background
            fragColor = vec4(u_GridColor * finalAlpha, finalAlpha);
        }
    """.trimIndent()

    fun initGl() {
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
            throw RuntimeException("GridBackgroundRenderer program link failed: $log")
        }

        uInvMvpLoc = GLES30.glGetUniformLocation(programId, "u_InvMvpMatrix")
        uGridSpacingLoc = GLES30.glGetUniformLocation(programId, "u_GridSpacing")
        uOpacityLoc = GLES30.glGetUniformLocation(programId, "u_Opacity")
        uGridStyleLoc = GLES30.glGetUniformLocation(programId, "u_GridStyle")
        uDotRadiusLoc = GLES30.glGetUniformLocation(programId, "u_DotRadius")
        uGridColorLoc = GLES30.glGetUniformLocation(programId, "u_GridColor")

        // Fullscreen quad in NDC coordinates [-1, 1]
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
        gridConfig: GridConfig,
        effectiveScale: Float
    ) {
        if (!gridConfig.enabled || programId == 0) return

        GLES30.glUseProgram(programId)

        GLES30.glUniformMatrix4fv(uInvMvpLoc, 1, false, invMvpMatrix, 0)
        GLES30.glUniform1f(uGridSpacingLoc, gridConfig.spacingPx)
        GLES30.glUniform1f(uOpacityLoc, gridConfig.opacity)
        GLES30.glUniform1i(uGridStyleLoc, if (gridConfig.style == GridStyle.DOTS) 0 else 1)

        // Keep dots at ~1.5-2.0 screen pixels regardless of zoom
        val screenDotPx = 1.6f
        val dotRadiusWorld = maxOf(0.4f, screenDotPx / maxOf(effectiveScale, 0.05f))
        GLES30.glUniform1f(uDotRadiusLoc, dotRadiusWorld)

        // Select grid tint: light contrast on dark paper, dark contrast on light paper
        val isDarkPaper = (paperColor.r * 0.299f + paperColor.g * 0.587f + paperColor.b * 0.114f) < 0.45f
        if (isDarkPaper) {
            GLES30.glUniform3f(uGridColorLoc, 0.72f, 0.74f, 0.78f)
        } else {
            GLES30.glUniform3f(uGridColorLoc, 0.25f, 0.28f, 0.34f)
        }

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun destroyGl() {
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
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
            throw RuntimeException("Grid shader compilation failed ($type): $log")
        }
        return shader
    }
}
