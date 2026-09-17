package com.antigrav.bignotes.core.gl

import android.opengl.GLES30
import android.opengl.Matrix
import com.antigrav.bignotes.core.model.PaperColor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance GPU renderer for canvas page separation guidelines.
 *
 * Renders directly in OpenGL multi-buffered / front-buffered swapchain
 * right on top of PaperColor background and beneath stroke meshes.
 * Guaranteed 100% frame-perfect synchronization with camera pan, zoom,
 * and fast scrolling with zero Compose recomposition overhead.
 */
class PageSeparatorRenderer {
    private var programId: Int = 0
    private var uMvpLoc: Int = -1
    private var uColorLoc: Int = -1
    private var vboId: Int = 0
    private var vertexCount: Int = 0
    private var lastGeneratedWidth: Float = -1f

    private val tempMvp = FloatArray(16)

    fun initGl() {
        val vertCode = """#version 300 es
            layout(location = 0) in vec2 a_position;
            uniform mat4 u_MVP;
            void main() {
                gl_Position = u_MVP * vec4(a_position, 0.0, 1.0);
            }
        """.trimIndent()

        val fragCode = """#version 300 es
            precision mediump float;
            uniform vec4 u_Color;
            out vec4 fragColor;
            void main() {
                fragColor = u_Color;
            }
        """.trimIndent()

        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertCode)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragCode)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vs)
        GLES30.glAttachShader(programId, fs)
        GLES30.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            throw RuntimeException("PageSeparatorRenderer link error: $error")
        }

        uMvpLoc = GLES30.glGetUniformLocation(programId, "u_MVP")
        uColorLoc = GLES30.glGetUniformLocation(programId, "u_Color")

        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)

        val buffers = IntArray(1)
        GLES30.glGenBuffers(1, buffers, 0)
        vboId = buffers[0]
    }

    private fun updateMesh(width: Float) {
        if (kotlin.math.abs(width - lastGeneratedWidth) < 1f && vertexCount > 0) return
        lastGeneratedWidth = width

        val dashLen = 14f
        val gapLen = 10f
        val halfThickness = 1.6f // 3.2 world units thick line for prominent, crisp visibility
        val pitch = dashLen + gapLen

        val numDashes = (width / pitch).toInt() + 1
        val coords = ArrayList<Float>(numDashes * 12)

        var curX = 0f
        while (curX < width) {
            val x0 = curX
            val x1 = minOf(curX + dashLen, width)
            val y0 = -halfThickness
            val y1 = halfThickness

            // Triangle 1
            coords.add(x0); coords.add(y0)
            coords.add(x1); coords.add(y0)
            coords.add(x0); coords.add(y1)

            // Triangle 2
            coords.add(x1); coords.add(y0)
            coords.add(x1); coords.add(y1)
            coords.add(x0); coords.add(y1)

            curX += pitch
        }

        vertexCount = coords.size / 2
        val floatBuffer = ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        for (f in coords) {
            floatBuffer.put(f)
        }
        floatBuffer.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, coords.size * 4, floatBuffer, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw(
        mvpMatrix: FloatArray,
        visMinY: Float,
        visMaxY: Float,
        paperColor: PaperColor,
        basePageWidth: Float,
        worldPageHeight: Float
    ) {
        if (programId == 0 || vboId == 0 || worldPageHeight <= 50f) return

        updateMesh(basePageWidth)
        if (vertexCount <= 0) return

        val startPage = kotlin.math.floor(visMinY / worldPageHeight).toInt().coerceAtLeast(0)
        val endPage = kotlin.math.ceil(visMaxY / worldPageHeight).toInt().coerceAtLeast(startPage)
        if (endPage < startPage) return

        // Fast rejection: check if any line falls inside visible range
        var anyVisible = false
        for (p in startPage..endPage) {
            val pageY = p * worldPageHeight
            if (pageY in (visMinY - 20f)..(visMaxY + 20f)) {
                anyVisible = true
                break
            }
        }
        if (!anyVisible) return

        GLES30.glUseProgram(programId)

        // Determine high-contrast line color based on paper luminance
        val lum = paperColor.r * 0.299f + paperColor.g * 0.587f + paperColor.b * 0.114f
        val (r, g, b, alpha) = if (lum > 0.5f) {
            // Light paper (white, cream, sepia): prominent deep slate-indigo guideline
            floatArrayOf(0.28f, 0.34f, 0.46f, 0.75f)
        } else {
            // Dark paper (dark, dracula, everforest): prominent bright cool silver guideline
            floatArrayOf(0.76f, 0.82f, 0.94f, 0.75f)
        }

        // Premultiplied alpha for standard GL_ONE, GL_ONE_MINUS_SRC_ALPHA
        GLES30.glUniform4f(uColorLoc, r * alpha, g * alpha, b * alpha, alpha)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, 0)

        for (p in startPage..endPage) {
            val pageY = p * worldPageHeight
            if (pageY in (visMinY - 20f)..(visMaxY + 20f)) {
                Matrix.translateM(tempMvp, 0, mvpMatrix, 0, 0f, pageY, 0f)
                GLES30.glUniformMatrix4fv(uMvpLoc, 1, false, tempMvp, 0)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
            }
        }

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun cleanup() {
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
        vertexCount = 0
        lastGeneratedWidth = -1f
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compilation failed: $log")
        }
        return shader
    }
}
