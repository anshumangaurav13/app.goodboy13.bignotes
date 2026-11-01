package app.goodboy13.bignotes.core.geometry

import app.goodboy13.bignotes.core.model.PencilTone
import app.goodboy13.bignotes.core.model.StrokePoint
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StrokeTessellatorOptimizationTest {

    @Test
    fun testStepDistance_inkAndPencilOptimized() {
        // Fine pen tip (baseWidth = 1.0f)
        val fineInkStep = StrokeTessellator.getStepDistance(1.0f, isInk = true)
        assertTrue("Fine ink step should be at least 0.28 to avoid overdraw bloat", fineInkStep >= 0.28f)

        // Medium pen tip (baseWidth = 2.5f)
        val medInkStep = StrokeTessellator.getStepDistance(2.5f, isInk = true)
        assertTrue("Medium ink step should be between 0.28 and 1.6", medInkStep in 0.28f..1.6f)

        // Graphite pencil (baseWidth = 2.0f)
        val pencilStep = StrokeTessellator.getStepDistance(2.0f, isInk = false)
        assertTrue("Pencil step should be >= 0.30 to halve quad density", pencilStep >= 0.30f)
    }

    @Test
    fun testDabGeneratorRaw_matchesStandardDabs() {
        val step = 0.5f
        val genStd = DabGenerator(step)
        val genRaw = DabGenerator(step)

        // Start
        val stdStart = genStd.start(10f, 20f, 0.5f)
        val rawBuf = FloatArray(256 * 3)
        val rawStartCount = genRaw.startRaw(10f, 20f, 0.5f, rawBuf, 0)

        assertEquals(1, stdStart.size)
        assertEquals(1, rawStartCount)
        assertEquals(stdStart[0].x, rawBuf[0], 0.001f)
        assertEquals(stdStart[0].y, rawBuf[1], 0.001f)
        assertEquals(stdStart[0].pressure, rawBuf[2], 0.001f)

        // Add segment
        val stdAdded = genStd.addPoint(50f, 60f, 0.8f)
        val rawAddedCount = genRaw.addPointRaw(50f, 60f, 0.8f, rawBuf, rawStartCount * 3)

        assertEquals("Dab counts must match identically between raw and standard", stdAdded.size, rawAddedCount)
        for (i in stdAdded.indices) {
            val offset = (rawStartCount + i) * 3
            assertEquals(stdAdded[i].x, rawBuf[offset], 0.001f)
            assertEquals(stdAdded[i].y, rawBuf[offset + 1], 0.001f)
            assertEquals(stdAdded[i].pressure, rawBuf[offset + 2], 0.001f)
        }
    }

    @Test
    fun testTessellateRawDabs_producesValidMesh() {
        val rawDabs = floatArrayOf(
            10f, 20f, 0.5f,
            12f, 22f, 0.6f,
            15f, 25f, 0.7f
        )
        val targetBuf = ByteBuffer.allocateDirect(3 * 6 * StrokeTessellator.STRIDE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        val mesh = StrokeTessellator.tessellateRawDabs(
            rawDabs = rawDabs,
            dabCount = 3,
            tone = PencilTone.INK_BLACK,
            baseWidth = 2.0f,
            targetBuffer = targetBuf,
            isInk = true
        )

        assertEquals(18, mesh.vertexCount) // 3 dabs * 6 vertices
        assertEquals(18 * StrokeTessellator.FLOATS_PER_VERTEX, mesh.vertexBuffer.limit())
        assertEquals(0, mesh.vertexBuffer.position())
    }
}
