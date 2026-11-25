package app.goodboy13.bignotes.core.gl

import android.graphics.Bitmap
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

object NoiseTextureGenerator {
    const val TEXTURE_SIZE = 512

    // Finalized Paper Tooth Shader Constants (Velvet Soft)
    const val TEXTURE_SCALE = 0.183f
    const val TOOTH_SHARPNESS = 0.050f
    const val TOOTH_BITE = 0.72f
    const val DUAL_SAMPLE_MIX = 0.26f

    private val GRADIENTS_X = floatArrayOf(1f, -1f, 0f, 0f, 0.7071f, -0.7071f, 0.7071f, -0.7071f)
    private val GRADIENTS_Y = floatArrayOf(0f, 0f, 1f, -1f, 0.7071f, 0.7071f, -0.7071f, -0.7071f)

    private val perm = IntArray(512)

    init {
        val p = intArrayOf(
            151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,
            8,99,37,240,21,10,23,190,6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,
            35,11,32,57,177,33,88,237,149,56,87,174,20,125,136,171,168,68,175,74,165,71,
            134,139,48,27,166,77,146,158,231,83,111,229,122,60,211,133,230,220,105,92,41,
            55,46,245,40,244,102,143,54,65,25,63,161,1,216,80,73,209,76,132,187,208,89,
            18,169,200,196,135,130,116,188,159,86,164,100,109,198,173,186,3,64,52,217,226,
            250,124,123,5,202,38,147,118,126,255,82,85,212,207,206,59,227,47,16,58,17,182,
            189,28,42,223,183,170,213,119,248,152,2,44,154,163,70,221,153,101,155,167,43,
            172,9,129,22,39,253,19,98,108,110,79,113,224,232,178,185,112,104,218,246,97,
            228,251,34,242,193,238,210,144,12,191,179,162,241,81,51,145,235,249,14,239,
            107,49,192,214,31,181,199,106,157,184,84,204,176,115,121,50,45,127,4,150,254,
            138,236,205,93,222,114,67,29,24,72,243,141,128,195,78,66,215,61,156,180
        )
        for (i in 0 until 256) {
            perm[i] = p[i]
            perm[256 + i] = p[i]
        }
    }

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

    private fun grad(hash: Int, x: Float, y: Float): Float {
        val h = hash and 7
        return GRADIENTS_X[h] * x + GRADIENTS_Y[h] * y
    }

    /**
     * Seamless 2D Perlin noise that wraps perfectly at periodW and periodH.
     */
    private fun seamlessNoise(x: Float, y: Float, periodW: Int, periodH: Int): Float {
        val xFloor = floor(x).toInt()
        val yFloor = floor(y).toInt()
        val x0 = ((xFloor % periodW) + periodW) % periodW
        val y0 = ((yFloor % periodH) + periodH) % periodH
        val x1 = (x0 + 1) % periodW
        val y1 = (y0 + 1) % periodH

        val u = x - xFloor
        val v = y - yFloor

        val fu = fade(u)
        val fv = fade(v)

        val g00 = grad(perm[perm[x0] + y0], u, v)
        val g10 = grad(perm[perm[x1] + y0], u - 1f, v)
        val g01 = grad(perm[perm[x0] + y1], u, v - 1f)
        val g11 = grad(perm[perm[x1] + y1], u - 1f, v - 1f)

        val nx0 = g00 + fu * (g10 - g00)
        val nx1 = g01 + fu * (g11 - g01)
        return nx0 + fv * (nx1 - nx0)
    }

    /**
     * Generates a byte array representing raw 8-bit paper tooth noise (Velvet Soft).
     */
    fun generateRawNoise(): ByteArray {
        val raw = ByteArray(TEXTURE_SIZE * TEXTURE_SIZE)
        val floatBuffer = FloatArray(TEXTURE_SIZE * TEXTURE_SIZE)
        var minVal = Float.MAX_VALUE
        var maxVal = -Float.MAX_VALUE

        // Soft, multi-frequency velvet grain with gentle S-curve response
        for (y in 0 until TEXTURE_SIZE) {
            val v = y.toFloat() / TEXTURE_SIZE
            for (x in 0 until TEXTURE_SIZE) {
                val u = x.toFloat() / TEXTURE_SIZE
                var total = 0f
                var amp = 0.40f
                var freq = 10
                for (octave in 0 until 5) {
                    total += seamlessNoise(u * freq, v * freq, freq, freq) * amp
                    amp *= 0.50f
                    freq *= 2
                }
                floatBuffer[y * TEXTURE_SIZE + x] = total
                if (total < minVal) minVal = total
                if (total > maxVal) maxVal = total
            }
        }

        val range = (maxVal - minVal).coerceAtLeast(1e-4f)
        for (i in floatBuffer.indices) {
            var normalized = ((floatBuffer[i] - minVal) / range).coerceIn(0f, 1f)
            // S-curve contrast softening for velvety tonal gradation
            normalized = normalized * normalized * (3f - 2f * normalized)
            raw[i] = (normalized * 255f).toInt().toByte()
        }
        return raw
    }

    /**
     * Generates an interleaved RG8 buffer packing coarse paper tooth (R) and fine
     * cellulose fiber streaks (G) for multi-scale shading and zoom-stable mipmaps.
     */
    fun generateMultiScaleRgNoise(): ByteArray {
        val pixelCount = TEXTURE_SIZE * TEXTURE_SIZE
        val coarseBuf = FloatArray(pixelCount)
        val fiberBuf = FloatArray(pixelCount)
        var minC = Float.MAX_VALUE
        var maxC = -Float.MAX_VALUE
        var minF = Float.MAX_VALUE
        var maxF = -Float.MAX_VALUE

        for (y in 0 until TEXTURE_SIZE) {
            val v = y.toFloat() / TEXTURE_SIZE
            for (x in 0 until TEXTURE_SIZE) {
                val u = x.toFloat() / TEXTURE_SIZE

                // R: Coarse paper tooth (8, 16, 32, 64 octaves)
                var coarse = 0f
                var ampC = 0.45f
                var freqC = 8
                for (octave in 0 until 4) {
                    coarse += seamlessNoise(u * freqC, v * freqC, freqC, freqC) * ampC
                    ampC *= 0.50f
                    freqC *= 2
                }

                // G: High-frequency cellulose fibers with crossed directional periodicity
                val f1 = seamlessNoise(u * 24f, v * 96f, 24, 96) * 0.42f
                val f2 = seamlessNoise(u * 96f + 0.31f, v * 24f + 0.17f, 96, 24) * 0.42f
                val f3 = seamlessNoise(u * 128f, v * 128f, 128, 128) * 0.25f
                val f4 = seamlessNoise(u * 256f, v * 256f, 256, 256) * 0.15f
                val fiber = f1 + f2 + f3 + f4

                val idx = y * TEXTURE_SIZE + x
                coarseBuf[idx] = coarse
                fiberBuf[idx] = fiber
                if (coarse < minC) minC = coarse
                if (coarse > maxC) maxC = coarse
                if (fiber < minF) minF = fiber
                if (fiber > maxF) maxF = fiber
            }
        }

        val rangeC = (maxC - minC).coerceAtLeast(1e-4f)
        val rangeF = (maxF - minF).coerceAtLeast(1e-4f)
        val rg = ByteArray(pixelCount * 2)
        for (i in 0 until pixelCount) {
            var normC = ((coarseBuf[i] - minC) / rangeC).coerceIn(0f, 1f)
            normC = normC * normC * (3f - 2f * normC)
            var normF = ((fiberBuf[i] - minF) / rangeF).coerceIn(0f, 1f)
            normF = normF * normF * (3f - 2f * normF)

            rg[i * 2] = (normC * 255f).toInt().toByte()
            rg[i * 2 + 1] = (normF * 255f).toInt().toByte()
        }
        return rg
    }

    /**
     * Generates a seamless 512x512 multi-scale RG8 paper tooth + cellulose fiber texture
     * with mipmaps for zoom-invariant sharpness.
     */
    fun createPaperToothTexture(): Int {
        val rawData = generateMultiScaleRgNoise()
        val buffer = ByteBuffer.allocateDirect(rawData.size).order(ByteOrder.nativeOrder())
        buffer.put(rawData)
        buffer.position(0)

        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        val textureId = textures[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RG8,
            TEXTURE_SIZE,
            TEXTURE_SIZE,
            0,
            GLES30.GL_RG,
            GLES30.GL_UNSIGNED_BYTE,
            buffer
        )
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        return textureId
    }

    /**
     * Creates a Bitmap of the paper tooth noise for CPU export rendering.
     */
    fun createNoiseBitmap(): Bitmap {
        val raw = generateRawNoise()
        val pixels = IntArray(TEXTURE_SIZE * TEXTURE_SIZE)
        for (i in raw.indices) {
            val v = raw[i].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(pixels, TEXTURE_SIZE, TEXTURE_SIZE, Bitmap.Config.ARGB_8888)
    }
}

