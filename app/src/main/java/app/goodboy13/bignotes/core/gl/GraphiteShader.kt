package app.goodboy13.bignotes.core.gl

import android.content.Context
import android.opengl.GLES30
import app.goodboy13.bignotes.core.geometry.StrokeTessellator
import app.goodboy13.bignotes.core.model.InkBleedConfig
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
    private var uInkDilutionLocation: Int = -1
    private var uFiberFeatheringLocation: Int = -1
    private var uMeniscusRimLocation: Int = -1
    private var uChromaHaloLocation: Int = -1

    var bleedRatio: Float = InkBleedConfig.DEFAULT.bleedRatio
    var bleedPower: Float = InkBleedConfig.DEFAULT.bleedPower
    var coreShiftRatio: Float = InkBleedConfig.DEFAULT.coreShiftRatio
    var bleedBaseOpacity: Float = InkBleedConfig.DEFAULT.bleedBaseOpacity
    var aaWidth: Float = InkBleedConfig.DEFAULT.aaWidth
    var paperGrainMix: Float = InkBleedConfig.DEFAULT.paperGrainMix
    var inkDilution: Float = InkBleedConfig.DEFAULT.inkDilution
    var fiberFeathering: Float = InkBleedConfig.DEFAULT.fiberFeathering
    var meniscusRim: Float = InkBleedConfig.DEFAULT.meniscusRim
    var chromaHalo: Float = InkBleedConfig.DEFAULT.chromaHalo
    var invertColors: Boolean = false

    private val locPosition = 0
    private val locSegStart = 1
    private val locSegEnd = 2
    private val locRadii = 3
    private val locColor = 4
    private val locTone = 5
    private val locTrims = 6
    private val locNib = 7

    fun init() {
        val vertCode = loadShaderFromAssets("shaders/pencil.vert")
        val fragCode = loadShaderFromAssets("shaders/pencil.frag")

        val vertShader = compileShader(GLES30.GL_VERTEX_SHADER, vertCode)
        val fragShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragCode)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vertShader)
        GLES30.glAttachShader(programId, fragShader)

        GLES30.glBindAttribLocation(programId, locPosition, "a_position")
        GLES30.glBindAttribLocation(programId, locSegStart, "a_segStart")
        GLES30.glBindAttribLocation(programId, locSegEnd, "a_segEnd")
        GLES30.glBindAttribLocation(programId, locRadii, "a_radii")
        GLES30.glBindAttribLocation(programId, locColor, "a_color")
        GLES30.glBindAttribLocation(programId, locTone, "a_tone")
        GLES30.glBindAttribLocation(programId, locTrims, "a_trims")
        GLES30.glBindAttribLocation(programId, locNib, "a_nib")

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
        uInkDilutionLocation = GLES30.glGetUniformLocation(programId, "u_InkDilution")
        uFiberFeatheringLocation = GLES30.glGetUniformLocation(programId, "u_FiberFeathering")
        uMeniscusRimLocation = GLES30.glGetUniformLocation(programId, "u_MeniscusRim")
        uChromaHaloLocation = GLES30.glGetUniformLocation(programId, "u_ChromaHalo")

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
        if (uInkDilutionLocation >= 0) GLES30.glUniform1f(uInkDilutionLocation, inkDilution)
        if (uFiberFeatheringLocation >= 0) GLES30.glUniform1f(uFiberFeatheringLocation, fiberFeathering)
        if (uMeniscusRimLocation >= 0) GLES30.glUniform1f(uMeniscusRimLocation, meniscusRim)
        if (uChromaHaloLocation >= 0) GLES30.glUniform1f(uChromaHaloLocation, chromaHalo)

        enableVertexAttribs()
    }

    private fun enableVertexAttribs() {
        GLES30.glEnableVertexAttribArray(locPosition)
        GLES30.glEnableVertexAttribArray(locSegStart)
        GLES30.glEnableVertexAttribArray(locSegEnd)
        GLES30.glEnableVertexAttribArray(locRadii)
        GLES30.glEnableVertexAttribArray(locColor)
        GLES30.glEnableVertexAttribArray(locTone)
        GLES30.glEnableVertexAttribArray(locTrims)
        GLES30.glEnableVertexAttribArray(locNib)
    }

    private fun disableVertexAttribs() {
        GLES30.glDisableVertexAttribArray(locPosition)
        GLES30.glDisableVertexAttribArray(locSegStart)
        GLES30.glDisableVertexAttribArray(locSegEnd)
        GLES30.glDisableVertexAttribArray(locRadii)
        GLES30.glDisableVertexAttribArray(locColor)
        GLES30.glDisableVertexAttribArray(locTone)
        GLES30.glDisableVertexAttribArray(locTrims)
        GLES30.glDisableVertexAttribArray(locNib)
    }

    fun renderMesh(vertexBuffer: FloatBuffer, vertexCount: Int) {
        if (vertexCount == 0) return

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        enableVertexAttribs()

        val stride = StrokeTessellator.STRIDE_BYTES
        vertexBuffer.position(0)
        GLES30.glVertexAttribPointer(locPosition, 2, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(2)
        GLES30.glVertexAttribPointer(locSegStart, 2, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(4)
        GLES30.glVertexAttribPointer(locSegEnd, 2, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(6)
        GLES30.glVertexAttribPointer(locRadii, 4, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(10)
        GLES30.glVertexAttribPointer(locColor, 4, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(14)
        GLES30.glVertexAttribPointer(locTone, 1, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(15)
        GLES30.glVertexAttribPointer(locTrims, 4, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(19)
        GLES30.glVertexAttribPointer(locNib, 3, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
        disableVertexAttribs()
    }

    /**
     * Prepares vertex attribute state once for a batch of VBO draws.
     */
    fun prepareVboDraw() {
        enableVertexAttribs()
    }

    /**
     * Renders a VBO directly assuming vertex attribute arrays are already enabled via [prepareVboDraw].
     */
    fun renderVboDirect(vboId: Int, vertexCount: Int) {
        if (vertexCount == 0 || vboId == 0) return
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        val stride = StrokeTessellator.STRIDE_BYTES
        GLES30.glVertexAttribPointer(locPosition, 2, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glVertexAttribPointer(locSegStart, 2, GLES30.GL_FLOAT, false, stride, 2 * 4)
        GLES30.glVertexAttribPointer(locSegEnd, 2, GLES30.GL_FLOAT, false, stride, 4 * 4)
        GLES30.glVertexAttribPointer(locRadii, 4, GLES30.GL_FLOAT, false, stride, 6 * 4)
        GLES30.glVertexAttribPointer(locColor, 4, GLES30.GL_FLOAT, false, stride, 10 * 4)
        GLES30.glVertexAttribPointer(locTone, 1, GLES30.GL_FLOAT, false, stride, 14 * 4)
        GLES30.glVertexAttribPointer(locTrims, 4, GLES30.GL_FLOAT, false, stride, 15 * 4)
        GLES30.glVertexAttribPointer(locNib, 3, GLES30.GL_FLOAT, false, stride, 19 * 4)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
    }

    /**
     * Cleans up vertex attribute state after completing a batch of VBO draws.
     */
    fun finishVboDraw() {
        disableVertexAttribs()
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
