package app.goodboy13.bignotes.core.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class BlitShader {
    private var programId: Int = 0
    private var uTextureLocation: Int = -1
    private var uTransformLocation: Int = -1

    private val quadVertices: FloatBuffer = ByteBuffer.allocateDirect(16 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            // Quad covering [-1, 1] in NDC
            // Triangle Strip:
            // V0: (-1, -1), uv: (0, 0)
            // V1: ( 1, -1), uv: (1, 0)
            // V2: (-1,  1), uv: (0, 1)
            // V3: ( 1,  1), uv: (1, 1)
            put(floatArrayOf(
                -1.0f, -1.0f, 0.0f, 0.0f,
                 1.0f, -1.0f, 1.0f, 0.0f,
                -1.0f,  1.0f, 0.0f, 1.0f,
                 1.0f,  1.0f, 1.0f, 1.0f
            ))
            position(0)
        }

    fun init() {
        val vertCode = """
            #version 300 es
            layout(location = 0) in vec2 a_position;
            layout(location = 1) in vec2 a_texCoord;
            uniform mat4 u_Transform;
            out vec2 v_texCoord;
            void main() {
                v_texCoord = a_texCoord;
                gl_Position = u_Transform * vec4(a_position, 0.0, 1.0);
            }
        """.trimIndent()

        val fragCode = """
            #version 300 es
            precision mediump float;
            in vec2 v_texCoord;
            uniform sampler2D u_Texture;
            out vec4 fragColor;
            void main() {
                fragColor = texture(u_Texture, v_texCoord);
            }
        """.trimIndent()

        val vert = compile(GLES30.GL_VERTEX_SHADER, vertCode)
        val frag = compile(GLES30.GL_FRAGMENT_SHADER, fragCode)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vert)
        GLES30.glAttachShader(programId, frag)
        GLES30.glLinkProgram(programId)

        uTextureLocation = GLES30.glGetUniformLocation(programId, "u_Texture")
        uTransformLocation = GLES30.glGetUniformLocation(programId, "u_Transform")

        GLES30.glDeleteShader(vert)
        GLES30.glDeleteShader(frag)
    }

    fun blit(
        textureId: Int,
        transformMatrix: FloatArray,
        x0: Float = -1.0f,
        y0: Float = -1.0f,
        x1: Float = 1.0f,
        y1: Float = 1.0f,
        enableBlend: Boolean = false
    ) {
        if (enableBlend) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            GLES30.glDisable(GLES30.GL_BLEND)
        }
        GLES30.glUseProgram(programId)

        GLES30.glUniformMatrix4fv(uTransformLocation, 1, false, transformMatrix, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(uTextureLocation, 0)

        quadVertices.position(0)
        quadVertices.put(x0).put(y0).put(0.0f).put(0.0f)
        quadVertices.put(x1).put(y0).put(1.0f).put(0.0f)
        quadVertices.put(x0).put(y1).put(0.0f).put(1.0f)
        quadVertices.put(x1).put(y1).put(1.0f).put(1.0f)
        quadVertices.position(0)

        quadVertices.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 4 * 4, quadVertices)
        GLES30.glEnableVertexAttribArray(0)

        quadVertices.position(2)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 4 * 4, quadVertices)
        GLES30.glEnableVertexAttribArray(1)

        GLES30.glDisableVertexAttribArray(2)
        GLES30.glDisableVertexAttribArray(3)
        GLES30.glDisableVertexAttribArray(4)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    fun cleanup() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        return s
    }
}

