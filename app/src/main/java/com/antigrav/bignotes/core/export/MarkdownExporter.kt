package com.antigrav.bignotes.core.export

import android.content.Context
import com.antigrav.bignotes.core.storage.RecognizedTextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object MarkdownExporter {

    /**
     * Converts a note's recognized handwriting text blocks into a structured Markdown document.
     * Sorts blocks top-to-bottom and groups adjacent lines into paragraphs.
     */
    suspend fun exportNoteToMarkdown(
        context: Context,
        noteTitle: String,
        textBlocks: List<RecognizedTextBlock>
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val sanitizedTitle = noteTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifEmpty { "BigNote" }
        val outputFile = File(exportDir, "${sanitizedTitle}_${System.currentTimeMillis()}.md")

        val markdownContent = generateMarkdown(noteTitle, textBlocks)

        OutputStreamWriter(FileOutputStream(outputFile), StandardCharsets.UTF_8).use { writer ->
            writer.write(markdownContent)
        }

        outputFile
    }

    /**
     * Generates structured markdown text from note title and recognized text blocks.
     */
    fun generateMarkdown(
        noteTitle: String,
        textBlocks: List<RecognizedTextBlock>
    ): String {
        val sb = StringBuilder()
        sb.append("# ").append(noteTitle.trim().ifEmpty { "Untitled Note" }).append("\n\n")

        if (textBlocks.isEmpty()) {
            sb.append("*(No recognized handwriting)*\n")
            return sb.toString()
        }

        // Sort blocks by vertical position first, then horizontal position
        val sortedBlocks = textBlocks.sortedWith(compareBy({ it.minY }, { it.minX }))

        var prevBottomY = -Float.MAX_VALUE
        var prevHeight = 20f

        for (block in sortedBlocks) {
            val text = block.text.trim()
            if (text.isEmpty()) continue

            val blockHeight = maxOf(10f, block.maxY - block.minY)
            val gap = block.minY - prevBottomY

            // If vertical gap is significantly larger than previous line height, treat as a paragraph break
            if (prevBottomY > 0f) {
                if (gap > prevHeight * 1.6f) {
                    sb.append("\n\n")
                } else {
                    sb.append("\n")
                }
            }

            sb.append(text)
            prevBottomY = block.maxY
            prevHeight = blockHeight
        }

        sb.append("\n")
        return sb.toString()
    }
}
