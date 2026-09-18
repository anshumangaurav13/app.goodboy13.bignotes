package com.antigrav.bignotes.core

import com.antigrav.bignotes.core.export.MarkdownExporter
import com.antigrav.bignotes.core.export.PageFormat
import com.antigrav.bignotes.core.export.PdfPagePaginator
import com.antigrav.bignotes.core.model.PencilTone
import com.antigrav.bignotes.core.model.Stroke
import com.antigrav.bignotes.core.model.StrokePoint
import com.antigrav.bignotes.core.storage.BigNoteBinaryFormat
import com.antigrav.bignotes.core.storage.BigNoteDocument
import com.antigrav.bignotes.core.storage.RecognizedTextBlock
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class BigNoteExportAndStorageTest {

    @Test
    fun testBigNoteBinaryFormat_roundTripSerializationAndQuantization() {
        val originalPoints = listOf(
            StrokePoint(100.0f, 200.0f, 0.50f, 1000L),
            StrokePoint(105.23f, 203.47f, 0.65f, 1016L),
            StrokePoint(112.89f, 210.12f, 0.80f, 1032L),
            StrokePoint(120.45f, 220.88f, 0.40f, 1048L)
        )

        val stroke = Stroke(
            id = "stroke_1",
            points = originalPoints,
            tone = PencilTone.INK_BLACK,
            baseWidth = 6.0f,
            colorRgb = 0xFF14171AL,
            paletteIndex = 0,
            isInk = true
        )

        val doc = BigNoteDocument(
            noteId = "test_note_42",
            folderId = "folder_main",
            title = "Algorithmic Notes",
            createdAt = 1700000000000L,
            updatedAt = 1700000050000L,
            zoom = 1.25f,
            scrollY = 150.0f,
            strokes = listOf(stroke)
        )

        // 1. Serialize
        val bytes = BigNoteBinaryFormat.serialize(doc)
        assertTrue("Serialized byte array must not be empty", bytes.isNotEmpty())
        assertTrue("First 5 bytes must match BNOTE magic", bytes[0] == 'B'.code.toByte() && bytes[1] == 'N'.code.toByte())

        // 2. Deserialize
        val deserialized = BigNoteBinaryFormat.deserialize(bytes)
        assertEquals("test_note_42", deserialized.noteId)
        assertEquals("folder_main", deserialized.folderId)
        assertEquals("Algorithmic Notes", deserialized.title)
        assertEquals(1.25f, deserialized.zoom, 0.001f)
        assertEquals(150.0f, deserialized.scrollY, 0.001f)
        assertEquals(1, deserialized.strokes.size)

        val restoredStroke = deserialized.strokes[0]
        assertEquals("stroke_1", restoredStroke.id)
        assertEquals(PencilTone.INK_BLACK, restoredStroke.tone)
        assertTrue(restoredStroke.isInk)
        assertEquals(6.0f, restoredStroke.baseWidth, 0.001f)
        assertEquals(4, restoredStroke.points.size)

        // 3. Verify high-fidelity coordinate and pressure reconstruction (Quantized + Deflater Zlib)
        for (i in originalPoints.indices) {
            val orig = originalPoints[i]
            val rest = restoredStroke.points[i]

            assertEquals("Point $i X must be within 0.05pt quantization tolerance", orig.x, rest.x, 0.05f)
            assertEquals("Point $i Y must be within 0.05pt quantization tolerance", orig.y, rest.y, 0.05f)
            assertEquals("Point $i Pressure must be within 8-bit quantization tolerance", orig.pressure, rest.pressure, 0.005f)
            assertEquals("Timestamp must match exactly", orig.timeMillis, rest.timeMillis)
        }
    }

    @Test
    fun testBigNoteBinaryFormat_fastMetadataHeaderParsing() {
        val stroke = Stroke(
            id = "stroke_1",
            points = listOf(StrokePoint(10f, 20f, 0.5f, 100L)),
            tone = PencilTone.INK_BLACK,
            baseWidth = 4.0f
        )
        val doc = BigNoteDocument(
            noteId = "quick_note_99",
            folderId = "folder_work",
            title = "Fast Index Test",
            createdAt = 1700000000123L,
            updatedAt = 1700000050456L,
            zoom = 1.5f,
            scrollY = 320.0f,
            strokes = listOf(stroke)
        )
        val bytes = BigNoteBinaryFormat.serialize(doc)
        val metadata = BigNoteBinaryFormat.deserializeMetadata(bytes)

        assertNotNull(metadata)
        assertEquals("quick_note_99", metadata!!.noteId)
        assertEquals("folder_work", metadata.folderId)
        assertEquals("Fast Index Test", metadata.title)
        assertEquals(1700000000123L, metadata.createdAt)
        assertEquals(1700000050456L, metadata.updatedAt)
        assertEquals(1.5f, metadata.zoom, 0.001f)
        assertEquals(320.0f, metadata.scrollY, 0.001f)
    }

    @Test
    fun testPdfPagePaginator_overlapRuleStrokesRepeatedOnBothPages() {
        // Usable dimensions for US Letter (612x792, margin 36pt -> usable 540x720, scale = 540/1200 = 0.45)
        // World page height = 720 / 0.45 = 1600 world units.
        val stroke1 = Stroke(
            id = "stroke_top_1",
            points = listOf(StrokePoint(100f, 100f, 0.5f, 0L), StrokePoint(200f, 250f, 0.5f, 10L)),
            tone = PencilTone.INK_BLACK,
            baseWidth = 4f
        )
        val stroke2 = Stroke(
            id = "stroke_middle_2",
            points = listOf(StrokePoint(100f, 600f, 0.5f, 0L), StrokePoint(200f, 800f, 0.5f, 10L)),
            tone = PencilTone.INK_BLACK,
            baseWidth = 4f
        )
        // Stroke 3 spans across the nominal break at Y=1600 (minY ~ 1550, maxY ~ 1680)
        val stroke3Overlapping = Stroke(
            id = "stroke_overlapping_3",
            points = listOf(StrokePoint(100f, 1550f, 0.5f, 0L), StrokePoint(200f, 1680f, 0.5f, 10L)),
            tone = PencilTone.INK_BLACK,
            baseWidth = 4f
        )
        val stroke4 = Stroke(
            id = "stroke_page2_4",
            points = listOf(StrokePoint(100f, 1800f, 0.5f, 0L), StrokePoint(200f, 1950f, 0.5f, 10L)),
            tone = PencilTone.INK_BLACK,
            baseWidth = 4f
        )

        val pages = PdfPagePaginator.paginate(
            strokes = listOf(stroke1, stroke2, stroke3Overlapping, stroke4),
            pageFormat = PageFormat.LETTER
        )

        assertEquals("Must paginate into 2 pages", 2, pages.size)

        // PAGE 1:
        val page1 = pages[0]
        val page1Ids = page1.strokes.map { it.id }
        assertTrue("Page 1 must contain stroke 1", page1Ids.contains("stroke_top_1"))
        assertTrue("Page 1 must contain stroke 2", page1Ids.contains("stroke_middle_2"))
        assertTrue("STRICT RULE: Overlapping stroke must appear on page 1", page1Ids.contains("stroke_overlapping_3"))
        assertFalse("Page 1 must not contain future stroke 4", page1Ids.contains("stroke_page2_4"))

        // PAGE 2:
        val page2 = pages[1]
        val page2Ids = page2.strokes.map { it.id }
        assertTrue("STRICT RULE: Overlapping stroke must ALSO appear on page 2 (repeated on both pages)", page2Ids.contains("stroke_overlapping_3"))
        assertTrue("Page 2 must contain subsequent stroke 4", page2Ids.contains("stroke_page2_4"))
        assertFalse("Page 2 must not contain page 1 stroke 1", page2Ids.contains("stroke_top_1"))

        // Verify page 2 start Y anchors at or before stroke 3's top, avoiding any top clipping
        val stroke3Bounds = PdfPagePaginator.calculateStrokeBounds(stroke3Overlapping)
        assertTrue(
            "Page 2 worldTopY (${page2.worldTopY}) must be <= stroke 3 minY (${stroke3Bounds.minY})",
            page2.worldTopY <= stroke3Bounds.minY
        )
    }

    @Test
    fun testPdfPageFormats_pageDimensions() {
        assertEquals(612, PageFormat.LEGAL_14.widthPt)
        assertEquals(1008, PageFormat.LEGAL_14.heightPt)
        assertEquals(612, PageFormat.LETTER.widthPt)
        assertEquals(792, PageFormat.LETTER.heightPt)
        assertEquals(595, PageFormat.A4.widthPt)
        assertEquals(842, PageFormat.A4.heightPt)
    }

    @Test
    fun testBigNoteBinaryFormat_recognizedTextBlocksRoundTrip() {
        val stroke = Stroke(
            id = "stroke_txt_1",
            points = listOf(StrokePoint(10f, 20f, 0.5f, 100L)),
            tone = PencilTone.INK_BLACK,
            baseWidth = 4.0f
        )
        val textBlocks = listOf(
            RecognizedTextBlock(
                clusterId = "cluster_1",
                text = "Hello BigNotes",
                minX = 10f,
                minY = 20f,
                maxX = 150f,
                maxY = 60f,
                updatedAt = 1700000000000L
            ),
            RecognizedTextBlock(
                clusterId = "cluster_2",
                text = "Searchable handwriting layer",
                minX = 10f,
                minY = 70f,
                maxX = 300f,
                maxY = 110f,
                updatedAt = 1700000005000L
            )
        )
        val doc = BigNoteDocument(
            noteId = "note_with_text",
            folderId = "folder_default",
            title = "Text Recognition Test",
            createdAt = 1700000000000L,
            updatedAt = 1700000010000L,
            strokes = listOf(stroke),
            textBlocks = textBlocks
        )

        val bytes = BigNoteBinaryFormat.serialize(doc)
        val restored = BigNoteBinaryFormat.deserialize(bytes)

        assertEquals("note_with_text", restored.noteId)
        assertEquals(2, restored.textBlocks.size)
        assertEquals("cluster_1", restored.textBlocks[0].clusterId)
        assertEquals("Hello BigNotes", restored.textBlocks[0].text)
        assertEquals(10f, restored.textBlocks[0].minX, 0.01f)
        assertEquals(60f, restored.textBlocks[0].maxY, 0.01f)
        assertEquals(1700000000000L, restored.textBlocks[0].updatedAt)

        assertEquals("cluster_2", restored.textBlocks[1].clusterId)
        assertEquals("Searchable handwriting layer", restored.textBlocks[1].text)
        assertEquals(300f, restored.textBlocks[1].maxX, 0.01f)
    }

    @Test
    fun testMarkdownExporter_structuredFormatting() {
        val blocks = listOf(
            RecognizedTextBlock(
                clusterId = "c2",
                text = "This is the second line of the first paragraph.",
                minX = 50f,
                minY = 70f,
                maxX = 400f,
                maxY = 90f
            ),
            RecognizedTextBlock(
                clusterId = "c1",
                text = "This is the first line.",
                minX = 50f,
                minY = 30f,
                maxX = 300f,
                maxY = 50f
            ),
            RecognizedTextBlock(
                clusterId = "c3",
                text = "And here is a distinct second paragraph #physics.",
                minX = 50f,
                minY = 200f,
                maxX = 450f,
                maxY = 225f
            )
        )

        val md = MarkdownExporter.generateMarkdown("Quantum Mechanics", blocks)

        assertTrue("Must start with H1 title", md.startsWith("# Quantum Mechanics\n\n"))
        assertTrue("First line must precede second line based on Y sorting", md.indexOf("This is the first line.") < md.indexOf("This is the second line of the first paragraph."))
        assertTrue("Large gap must create paragraph break", md.contains("\n\nAnd here is a distinct second paragraph #physics."))
    }
}
