package com.antigrav.bignotes.core.gl

import android.opengl.GLES30

class FboLayer {
    var fboId: Int = 0
        private set
    var textureId: Int = 0
        private set

    var width: Int = 0
        private set
    var height: Int = 0
        private set

    private val prevFbo = IntArray(1)

    fun init(w: Int, h: Int) {
        if (w == width && h == height && fboId != 0) return
        cleanup()

        width = w
        height = h

        val fbos = IntArray(1)
        GLES30.glGenFramebuffers(1, fbos, 0)
        fboId = fbos[0]

        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
            width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )

        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, prevFbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, textureId, 0
        )

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("FBO incomplete status: $status")
        }

        clearParchment()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, prevFbo[0])
    }

    fun bind() {
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, prevFbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glViewport(0, 0, width, height)
    }

    fun unbind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, prevFbo[0])
    }

    fun clearColor(r: Float, g: Float, b: Float, a: Float = 1.0f) {
        GLES30.glClearColor(r, g, b, a)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    fun clearParchment() {
        // Solarized Light Base3: #FDF6E3 (R: 253/255, G: 246/255, B: 227/255)
        clearColor(0.99215f, 0.96470f, 0.89019f, 1.0f)
    }

    fun cleanup() {
        if (textureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
        if (fboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            fboId = 0
        }
        width = 0
        height = 0
    }
}

