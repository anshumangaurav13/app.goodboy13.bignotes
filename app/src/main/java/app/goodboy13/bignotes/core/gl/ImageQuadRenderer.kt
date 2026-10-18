package app.goodboy13.bignotes.core.gl

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import app.goodboy13.bignotes.core.model.CanvasImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance GLES 3.0 textured quad renderer for images on the infinite canvas.
 * Renders images beneath ink strokes with bilinear texture filtering and non-destructive UV cropping.
 */
class ImageQuadRenderer {

    private var program = 0
    private var uMvpMatrixLoc = -1
    private var uTextureLoc = -1
    private var isInitialized = false

    private val textureCache = mutableMapOf<String, Int>()
    private var vertexBuffer: FloatBuffer? = null

    companion object {
        private const val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec2 a_position;
layout(location = 1) in vec2 a_texCoord;

uniform mat4 u_mvpMatrix;
out vec2 v_texCoord;

void main() {
    v_texCoord = a_texCoord;
    gl_Position = u_mvpMatrix * vec4(a_position, 0.0, 1.0);
}
"""

        private const val FRAGMENT_SHADER = """#version 300 es
precision mediump float;

uniform sampler2D u_texture;
in vec2 v_texCoord;
out vec4 fragColor;

void main() {
    vec4 col = texture(u_texture, v_texCoord);
    fragColor = vec4(col.rgb * col.a, col.a);
}
"""
    }

    fun initGl() {
        if (isInitialized) return
        val vs = loadShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fs = loadShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            program = 0
            throw RuntimeException("ImageQuadRenderer shader link error: $error")
        }

        uMvpMatrixLoc = GLES30.glGetUniformLocation(program, "u_mvpMatrix")
        uTextureLoc = GLES30.glGetUniformLocation(program, "u_texture")

        // 6 vertices per quad * 4 floats per vertex (x, y, u, v) = 24 floats
        val bb = ByteBuffer.allocateDirect(24 * 4).order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()

        isInitialized = true
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, code)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("ImageQuadRenderer compile error ($type): $error")
        }
        return shader
    }

    fun getTextureId(context: Context, fileName: String): Int {
        val cached = textureCache[fileName]
        if (cached != null && cached != 0) return cached

        val mediaDir = File(context.filesDir, "bignotes_media")
        val file = File(mediaDir, fileName)
        if (!file.exists()) return 0

        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return 0

        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        val texId = textures[0]
        if (texId == 0) {
            bitmap.recycle()
            return 0
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()

        textureCache[fileName] = texId
        return texId
    }

    fun render(
        images: List<CanvasImage>,
        mvpMatrix: FloatArray,
        context: Context
    ) {
        if (images.isEmpty() || !isInitialized) return

        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(uMvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniform1i(uTextureLoc, 0)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        val vBuf = vertexBuffer ?: return

        for (img in images) {
            val texId = getTextureId(context, img.localFileName)
            if (texId == 0) continue

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)

            val hw = img.width * 0.5f
            val hh = img.height * 0.5f

            val rad = Math.toRadians(img.rotationDegrees.toDouble())
            val cosR = cos(rad).toFloat()
            val sinR = sin(rad).toFloat()

            // Calculate 4 corners in world space
            fun rotateX(lx: Float, ly: Float): Float = img.centerX + lx * cosR - ly * sinR
            fun rotateY(lx: Float, ly: Float): Float = img.centerY + lx * sinR + ly * cosR

            val x0 = rotateX(-hw, -hh)
            val y0 = rotateY(-hw, -hh)
            val x1 = rotateX(hw, -hh)
            val y1 = rotateY(hw, -hh)
            val x2 = rotateX(hw, hh)
            val y2 = rotateY(hw, hh)
            val x3 = rotateX(-hw, hh)
            val y3 = rotateY(-hw, hh)

            val u0 = img.cropLeft
            val v0 = img.cropTop
            val u1 = img.cropRight
            val v1 = img.cropBottom

            vBuf.clear()
            // Triangle 1: (0, 1, 2)
            vBuf.put(x0).put(y0).put(u0).put(v0)
            vBuf.put(x1).put(y1).put(u1).put(v0)
            vBuf.put(x2).put(y2).put(u1).put(v1)

            // Triangle 2: (0, 2, 3)
            vBuf.put(x0).put(y0).put(u0).put(v0)
            vBuf.put(x2).put(y2).put(u1).put(v1)
            vBuf.put(x3).put(y3).put(u0).put(v1)

            vBuf.position(0)

            GLES30.glEnableVertexAttribArray(0)
            GLES30.glEnableVertexAttribArray(1)

            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 4 * 4, vBuf)
            vBuf.position(2)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 4 * 4, vBuf)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

            GLES30.glDisableVertexAttribArray(0)
            GLES30.glDisableVertexAttribArray(1)
        }
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    }

    fun releaseTextures() {
        for (texId in textureCache.values) {
            if (texId != 0) {
                val arr = intArrayOf(texId)
                GLES30.glDeleteTextures(1, arr, 0)
            }
        }
        textureCache.clear()
    }
}
