package app.goodboy13.bignotes.core.gl

import android.content.Context
import android.opengl.GLES30
import app.goodboy13.bignotes.core.geometry.StrokeTessellator
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.FloatBuffer

class GraphiteShader(private val context: Context) {
    var programId: Int = 0
        private set

    private var uMvpLocation: Int = -1
    private var uToothTextureLocation: Int = -1
    private var uBleedRatioLocation: Int = -1
    private var uBleedPowerLocation: Int = -1
    private var uCoreShiftRatioLocation: Int = -1
    private var uBleedBaseOpacityLocation: Int = -1
    private var uAaWidthLocation: Int = -1
    private var uPaperGrainMixLocation: Int = -1
    private var uInvertColorsLocation: Int = -1

    var bleedRatio: Float = 0.25f
    var bleedPower: Float = 2.0f
    var coreShiftRatio: Float = 0.0f
    var bleedBaseOpacity: Float = 1.0f
    var aaWidth: Float = 1.25f
    var paperGrainMix: Float = 0.0f
    var invertColors: Boolean = false

    private val locPosition = 0
    private val locNormal = 1
    private val locRadius = 2
    private val locColor = 3
    private val locEdgeDist = 4

    fun init() {
        val vertCode = loadShaderFromAssets("shaders/pencil.vert")
        val fragCode = loadShaderFromAssets("shaders/pencil.frag")

        val vertShader = compileShader(GLES30.GL_VERTEX_SHADER, vertCode)
        val fragShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragCode)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vertShader)
        GLES30.glAttachShader(programId, fragShader)

        GLES30.glBindAttribLocation(programId, locPosition, "a_position")
        GLES30.glBindAttribLocation(programId, locNormal, "a_normal")
        GLES30.glBindAttribLocation(programId, locRadius, "a_radius")
        GLES30.glBindAttribLocation(programId, locColor, "a_color")
        GLES30.glBindAttribLocation(programId, locEdgeDist, "a_edgeDist")

        GLES30.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            throw RuntimeException("Graphite shader link error: $error")
        }

        uMvpLocation = GLES30.glGetUniformLocation(programId, "u_MVP")
        uToothTextureLocation = GLES30.glGetUniformLocation(programId, "u_ToothTexture")
        uBleedRatioLocation = GLES30.glGetUniformLocation(programId, "u_BleedRatio")
        uBleedPowerLocation = GLES30.glGetUniformLocation(programId, "u_BleedPower")
        uCoreShiftRatioLocation = GLES30.glGetUniformLocation(programId, "u_CoreShiftRatio")
        uBleedBaseOpacityLocation = GLES30.glGetUniformLocation(programId, "u_BleedBaseOpacity")
        uAaWidthLocation = GLES30.glGetUniformLocation(programId, "u_AaWidth")
        uPaperGrainMixLocation = GLES30.glGetUniformLocation(programId, "u_PaperGrainMix")
        uInvertColorsLocation = GLES30.glGetUniformLocation(programId, "u_InvertColors")

        GLES30.glDeleteShader(vertShader)
        GLES30.glDeleteShader(fragShader)
    }

    fun use(mvpMatrix: FloatArray, textureId: Int) {
        GLES30.glUseProgram(programId)
        GLES30.glUniformMatrix4fv(uMvpLocation, 1, false, mvpMatrix, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(uToothTextureLocation, 0)

        if (uBleedRatioLocation >= 0) GLES30.glUniform1f(uBleedRatioLocation, bleedRatio)
        if (uBleedPowerLocation >= 0) GLES30.glUniform1f(uBleedPowerLocation, bleedPower)
        if (uCoreShiftRatioLocation >= 0) GLES30.glUniform1f(uCoreShiftRatioLocation, coreShiftRatio)
        if (uBleedBaseOpacityLocation >= 0) GLES30.glUniform1f(uBleedBaseOpacityLocation, bleedBaseOpacity)
        if (uAaWidthLocation >= 0) GLES30.glUniform1f(uAaWidthLocation, aaWidth)
        if (uPaperGrainMixLocation >= 0) GLES30.glUniform1f(uPaperGrainMixLocation, paperGrainMix)
        if (uInvertColorsLocation >= 0) GLES30.glUniform1i(uInvertColorsLocation, if (invertColors) 1 else 0)

        GLES30.glEnableVertexAttribArray(locPosition)
        GLES30.glEnableVertexAttribArray(locNormal)
        GLES30.glEnableVertexAttribArray(locRadius)
        GLES30.glEnableVertexAttribArray(locColor)
        GLES30.glEnableVertexAttribArray(locEdgeDist)
    }

    fun renderMesh(vertexBuffer: FloatBuffer, vertexCount: Int) {
        if (vertexCount == 0) return

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glEnableVertexAttribArray(locPosition)
        GLES30.glEnableVertexAttribArray(locNormal)
        GLES30.glEnableVertexAttribArray(locRadius)
        GLES30.glEnableVertexAttribArray(locColor)
        GLES30.glEnableVertexAttribArray(locEdgeDist)

        vertexBuffer.position(0)
        GLES30.glVertexAttribPointer(
            locPosition, 2, GLES30.GL_FLOAT, false,
            StrokeTessellator.STRIDE_BYTES, vertexBuffer
        )

        vertexBuffer.position(2)
        GLES30.glVertexAttribPointer(
            locNormal, 2, GLES30.GL_FLOAT, false,
            StrokeTessellator.STRIDE_BYTES, vertexBuffer
        )

        vertexBuffer.position(4)
        GLES30.glVertexAttribPointer(
            locRadius, 1, GLES30.GL_FLOAT, false,
            StrokeTessellator.STRIDE_BYTES, vertexBuffer
        )

        vertexBuffer.position(5)
        GLES30.glVertexAttribPointer(
            locColor, 4, GLES30.GL_FLOAT, false,
            StrokeTessellator.STRIDE_BYTES, vertexBuffer
        )

        vertexBuffer.position(9)
        GLES30.glVertexAttribPointer(
            locEdgeDist, 1, GLES30.GL_FLOAT, false,
            StrokeTessellator.STRIDE_BYTES, vertexBuffer
        )

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)

        GLES30.glDisableVertexAttribArray(locPosition)
        GLES30.glDisableVertexAttribArray(locNormal)
        GLES30.glDisableVertexAttribArray(locRadius)
        GLES30.glDisableVertexAttribArray(locColor)
        GLES30.glDisableVertexAttribArray(locEdgeDist)
    }

    /**
     * Prepares vertex attribute state once for a batch of VBO draws.
     */
    fun prepareVboDraw() {
        GLES30.glEnableVertexAttribArray(locPosition)
        GLES30.glEnableVertexAttribArray(locNormal)
        GLES30.glEnableVertexAttribArray(locRadius)
        GLES30.glEnableVertexAttribArray(locColor)
        GLES30.glEnableVertexAttribArray(locEdgeDist)
    }

    /**
     * Renders a VBO directly assuming vertex attribute arrays are already enabled via [prepareVboDraw].
     */
    fun renderVboDirect(vboId: Int, vertexCount: Int) {
        if (vertexCount == 0 || vboId == 0) return
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        val stride = StrokeTessellator.STRIDE_BYTES
        GLES30.glVertexAttribPointer(locPosition, 2, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glVertexAttribPointer(locNormal, 2, GLES30.GL_FLOAT, false, stride, 2 * 4)
        GLES30.glVertexAttribPointer(locRadius, 1, GLES30.GL_FLOAT, false, stride, 4 * 4)
        GLES30.glVertexAttribPointer(locColor, 4, GLES30.GL_FLOAT, false, stride, 5 * 4)
        GLES30.glVertexAttribPointer(locEdgeDist, 1, GLES30.GL_FLOAT, false, stride, 9 * 4)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
    }

    /**
     * Cleans up vertex attribute state after completing a batch of VBO draws.
     */
    fun finishVboDraw() {
        GLES30.glDisableVertexAttribArray(locPosition)
        GLES30.glDisableVertexAttribArray(locNormal)
        GLES30.glDisableVertexAttribArray(locRadius)
        GLES30.glDisableVertexAttribArray(locColor)
        GLES30.glDisableVertexAttribArray(locEdgeDist)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Renders vertices directly from GPU VBO without CPU memory transfer (single-call convenience).
     */
    fun renderVbo(vboId: Int, vertexCount: Int) {
        if (vertexCount == 0 || vboId == 0) return
        prepareVboDraw()
        renderVboDirect(vboId, vertexCount)
        finishVboDraw()
    }

    fun cleanup() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }

    private fun compileShader(type: Int, code: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, code)
        GLES30.glCompileShader(shader)

        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compilation error: $error")
        }
        return shader
    }

    private fun loadShaderFromAssets(filename: String): String {
        val stream = context.assets.open(filename)
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }
    }
}

