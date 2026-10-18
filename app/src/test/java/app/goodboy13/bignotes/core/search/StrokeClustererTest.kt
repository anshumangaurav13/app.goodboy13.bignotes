package app.goodboy13.bignotes.core.search

import app.goodboy13.bignotes.core.model.PencilTone
import app.goodboy13.bignotes.core.model.Stroke
import app.goodboy13.bignotes.core.model.StrokePoint
import app.goodboy13.bignotes.core.search.clustering.StrokeClusterer
import org.junit.Assert.*
import org.junit.Test

class StrokeClustererTest {

    private fun createStroke(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        isHighlighter: Boolean = false,
        time: Long = 1000L
    ): Stroke {
        val points = listOf(
            StrokePoint(startX, startY, 0.5f, time),
            StrokePoint((startX + endX) * 0.5f, (startY + endY) * 0.5f, 0.5f, time + 20L),
            StrokePoint(endX, endY, 0.5f, time + 40L)
        )
        return Stroke(
            points = points,
            tone = PencilTone.INK_BLACK,
            baseWidth = 5f,
            isHighlighter = isHighlighter
        )
    }

    @Test
    fun testSingleLineStrokesClusteredTogether() {
        // Line 1: Three adjacent strokes representing letters/words on line y ~ 200
        val s1 = createStroke(100f, 190f, 130f, 220f, time = 1000L)
        val s2 = createStroke(140f, 195f, 180f, 215f, time = 1050L)
        val s3 = createStroke(190f, 192f, 230f, 222f, time = 1100L)

        val clusters = StrokeClusterer.clusterStrokes("note_1", listOf(s1, s2, s3))

        assertEquals("Should form exactly 1 cluster for a continuous line", 1, clusters.size)
        val cluster = clusters[0]
        assertEquals("Cluster should contain all 3 strokes", 3, cluster.strokes.size)
        assertTrue("Bounding box minX <= 100", cluster.minX <= 100f)
        assertTrue("Bounding box maxX >= 230", cluster.maxX >= 230f)
    }

    @Test
    fun testMultiLineStrokesFormSeparateClusters() {
        // Line 1 at y ~ 100
        val l1s1 = createStroke(100f, 90f, 150f, 115f, time = 1000L)
        val l1s2 = createStroke(160f, 92f, 210f, 118f, time = 1050L)

        // Line 2 at y ~ 350 (250px below line 1)
        val l2s1 = createStroke(100f, 340f, 160f, 370f, time = 2000L)
        val l2s2 = createStroke(170f, 342f, 220f, 368f, time = 2050L)

        val clusters = StrokeClusterer.clusterStrokes("note_1", listOf(l1s1, l1s2, l2s1, l2s2))

        assertEquals("Should form 2 distinct clusters for separate lines", 2, clusters.size)
        // Top line should come first
        assertTrue("First cluster is top line", clusters[0].minY < clusters[1].minY)
        assertEquals(2, clusters[0].strokes.size)
        assertEquals(2, clusters[1].strokes.size)
    }

    @Test
    fun testHighlighterStrokesExcluded() {
        val ink1 = createStroke(100f, 100f, 150f, 130f, isHighlighter = false)
        val hl = createStroke(90f, 90f, 300f, 140f, isHighlighter = true)

        val clusters = StrokeClusterer.clusterStrokes("note_1", listOf(ink1, hl))

        assertEquals("Only ink stroke should be clustered", 1, clusters.size)
        assertEquals(1, clusters[0].strokes.size)
        assertFalse("Highlighter not in cluster", clusters[0].strokes.any { it.isHighlighter })
    }

    @Test
    fun testSinglePointStrokesIgnored() {
        val singlePointStroke = Stroke(
            points = listOf(StrokePoint(100f, 100f, 0.5f, 1000L)),
            tone = PencilTone.INK_BLACK,
            baseWidth = 5f
        )
        val validStroke = createStroke(150f, 150f, 200f, 180f)

        val clusters = StrokeClusterer.clusterStrokes("note_1", listOf(singlePointStroke, validStroke))

        assertEquals(1, clusters.size)
        assertEquals(1, clusters[0].strokes.size)
    }

    @Test
    fun testSnippetGeneration() {
        val fullText = "Quantum computing with superconducting qubits provides exponential speedup"
        val snippet = HandwritingSearchManager.generateSnippet(fullText, "qubits", windowSize = 15)

        assertTrue("Snippet should contain matching keyword", snippet.contains("qubits", ignoreCase = true))
        assertTrue("Snippet should have leading ellipsis if truncated", snippet.startsWith("..."))
        assertTrue("Snippet should have trailing ellipsis if truncated", snippet.endsWith("..."))

        // Test short string without ellipsis
        val shortText = "Hello World"
        val shortSnippet = HandwritingSearchManager.generateSnippet(shortText, "Hello", windowSize = 20)
        assertEquals("Hello World", shortSnippet)
    }
}
