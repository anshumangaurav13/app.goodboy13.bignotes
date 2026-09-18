package com.antigrav.bignotes.core.storage

import com.antigrav.bignotes.core.model.PencilTone
import com.antigrav.bignotes.core.model.Stroke
import com.antigrav.bignotes.core.model.StrokePoint
import java.io.*
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.math.roundToInt

data class BigNoteMetadata(
    val noteId: String,
    val folderId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val zoom: Float = 1.0f,
    val scrollY: Float = 0.0f
)

data class RecognizedTextBlock(
    val clusterId: String,
    val text: String,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val updatedAt: Long = System.currentTimeMillis()
)

data class BigNoteDocument(
    val noteId: String,
    val folderId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val zoom: Float = 1.0f,
    val scrollY: Float = 0.0f,
    val strokes: List<Stroke> = emptyList(),
    val images: List<com.antigrav.bignotes.core.model.CanvasImage> = emptyList(),
    val textBlocks: List<RecognizedTextBlock> = emptyList()
)

/**
 * Compact, high-efficiency binary serializer for .bignote files.
 * Uses 16-bit delta coordinate quantization and standard Zlib Deflater compression.
 */
object BigNoteBinaryFormat {
    private val MAGIC = byteArrayOf('B'.code.toByte(), 'N'.code.toByte(), 'O'.code.toByte(), 'T'.code.toByte(), 'E'.code.toByte())
    private const val VERSION_RAW: Byte = 2
    private const val VERSION_V1_DEFLATED: Byte = 1
    private const val VERSION_V3_DEFLATED: Byte = 3
    private const val VERSION_V4_DEFLATED: Byte = 4
    private const val VERSION_V5_DEFLATED: Byte = 5
    private const val V1_QUANTIZE_SCALE = 20.0f

    fun serialize(doc: BigNoteDocument): ByteArray {
        val byteOut = ByteArrayOutputStream(1024)
        val out = DataOutputStream(BufferedOutputStream(byteOut, 16384))

        // 1. Header (Magic + Version 5: Quantized + Deflater Zlib + Highlighter flag + Images + Text Blocks)
        out.write(MAGIC)
        out.writeByte(VERSION_V5_DEFLATED.toInt())


        // 2. Metadata Block
        out.writeUTF(doc.noteId)
        out.writeUTF(doc.folderId)
        out.writeUTF(doc.title)
        out.writeLong(doc.createdAt)
        out.writeLong(doc.updatedAt)
        out.writeFloat(doc.zoom)
        out.writeFloat(doc.scrollY)

        // 3. Compress Strokes with 16-bit Delta Quantization + Zlib Deflater
        val strokeByteOut = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.BEST_SPEED)
        val deflaterOut = DeflaterOutputStream(strokeByteOut, deflater)
        val strokeDataOut = DataOutputStream(deflaterOut)

        strokeDataOut.writeInt(doc.strokes.size)
        for (stroke in doc.strokes) {
            strokeDataOut.writeUTF(stroke.id)
            strokeDataOut.writeInt(stroke.tone.id)
            strokeDataOut.writeFloat(stroke.baseWidth)
            strokeDataOut.writeLong(stroke.colorRgb)
            strokeDataOut.writeInt(stroke.paletteIndex)
            strokeDataOut.writeBoolean(stroke.isInk)
            strokeDataOut.writeBoolean(stroke.isHighlighter)

            val points = stroke.points
            strokeDataOut.writeInt(points.size)
            if (points.isNotEmpty()) {
                var lastX = points[0].x
                var lastY = points[0].y
                var lastTime = points[0].timeMillis

                strokeDataOut.writeFloat(lastX)
                strokeDataOut.writeFloat(lastY)
                strokeDataOut.writeByte((points[0].pressure.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255))
                strokeDataOut.writeLong(lastTime)

                for (p in 1 until points.size) {
                    val pt = points[p]
                    val dxQuant = ((pt.x - lastX) * V1_QUANTIZE_SCALE).roundToInt().coerceIn(-32768, 32767).toShort()
                    val dyQuant = ((pt.y - lastY) * V1_QUANTIZE_SCALE).roundToInt().coerceIn(-32768, 32767).toShort()
                    val rawP = (pt.pressure.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255).toByte()
                    val dt = (pt.timeMillis - lastTime).toInt()

                    strokeDataOut.writeShort(dxQuant.toInt())
                    strokeDataOut.writeShort(dyQuant.toInt())
                    strokeDataOut.writeByte(rawP.toInt())
                    strokeDataOut.writeInt(dt)

                    // Accumulate exact reconstructed steps to prevent floating drift
                    lastX += dxQuant.toFloat() / V1_QUANTIZE_SCALE
                    lastY += dyQuant.toFloat() / V1_QUANTIZE_SCALE
                    lastTime += dt
                }
            }
        }
        strokeDataOut.flush()
        deflaterOut.finish()
        deflater.end()

        val compressedBytes = strokeByteOut.toByteArray()
        out.writeInt(compressedBytes.size)
        out.write(compressedBytes)

        // 4. Images Block (Version 4+)
        out.writeInt(doc.images.size)
        for (img in doc.images) {
            out.writeUTF(img.id)
            out.writeUTF(img.localFileName)
            out.writeFloat(img.centerX)
            out.writeFloat(img.centerY)
            out.writeFloat(img.width)
            out.writeFloat(img.height)
            out.writeFloat(img.rotationDegrees)
            out.writeFloat(img.cropLeft)
            out.writeFloat(img.cropTop)
            out.writeFloat(img.cropRight)
            out.writeFloat(img.cropBottom)
            out.writeLong(img.zIndex)
        }

        // 5. Recognized Text Clusters Block (Version 5+)
        out.writeInt(doc.textBlocks.size)
        for (block in doc.textBlocks) {
            out.writeUTF(block.clusterId)
            out.writeUTF(block.text)
            out.writeFloat(block.minX)
            out.writeFloat(block.minY)
            out.writeFloat(block.maxX)
            out.writeFloat(block.maxY)
            out.writeLong(block.updatedAt)
        }

        out.flush()
        return byteOut.toByteArray()
    }


    fun deserializeMetadata(bytes: ByteArray): BigNoteMetadata {
        val byteIn = ByteArrayInputStream(bytes)
        val `in` = DataInputStream(byteIn)

        val magic = ByteArray(5)
        `in`.readFully(magic)
        if (!magic.contentEquals(MAGIC)) {
            throw IOException("Invalid .bignote file magic: ${String(magic)}")
        }

        val version = `in`.readByte()
        val noteId = `in`.readUTF()
        val folderId = `in`.readUTF()
        val title = `in`.readUTF()
        val createdAt = `in`.readLong()
        val updatedAt = `in`.readLong()
        val zoom = `in`.readFloat()
        val scrollY = `in`.readFloat()

        return BigNoteMetadata(
            noteId = noteId,
            folderId = folderId,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            zoom = zoom,
            scrollY = scrollY
        )
    }

    fun deserialize(bytes: ByteArray): BigNoteDocument {
        val byteIn = ByteArrayInputStream(bytes)
        val `in` = DataInputStream(BufferedInputStream(byteIn, 32768))

        // 1. Verify Magic
        val magic = ByteArray(5)
        `in`.readFully(magic)
        if (!magic.contentEquals(MAGIC)) {
            throw IOException("Invalid .bignote file magic: ${String(magic)}")
        }

        // 2. Version
        val version = `in`.readByte()

        // 3. Metadata
        val noteId = `in`.readUTF()
        val folderId = `in`.readUTF()
        val title = `in`.readUTF()
        val createdAt = `in`.readLong()
        val updatedAt = `in`.readLong()
        val zoom = `in`.readFloat()
        val scrollY = `in`.readFloat()

        return when (version) {
            VERSION_RAW -> {
                // Version 2: Raw Instantaneous Binary Dump
                val strokeCount = `in`.readInt()
                val strokes = ArrayList<Stroke>(strokeCount)

                for (s in 0 until strokeCount) {
                    val strokeId = `in`.readUTF()
                    val toneId = `in`.readInt()
                    val tone = PencilTone.entries.find { it.id == toneId } ?: PencilTone.SOFT
                    val baseWidth = `in`.readFloat()
                    val colorRgb = `in`.readLong()
                    val paletteIndex = `in`.readInt()
                    val isInk = `in`.readBoolean()

                    val pointCount = `in`.readInt()
                    val points = ArrayList<StrokePoint>(pointCount)
                    for (p in 0 until pointCount) {
                        val x = `in`.readFloat()
                        val y = `in`.readFloat()
                        val pressure = `in`.readFloat()
                        val timeMillis = `in`.readLong()
                        points.add(StrokePoint(x, y, pressure, timeMillis))
                    }

                    strokes.add(
                        Stroke(
                            id = strokeId,
                            points = points,
                            tone = tone,
                            baseWidth = baseWidth,
                            colorRgb = colorRgb,
                            paletteIndex = paletteIndex,
                            isInk = isInk
                        )
                    )
                }

                BigNoteDocument(
                    noteId = noteId,
                    folderId = folderId,
                    title = title,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    zoom = zoom,
                    scrollY = scrollY,
                    strokes = strokes
                )
            }
            VERSION_V1_DEFLATED, VERSION_V3_DEFLATED, VERSION_V4_DEFLATED, VERSION_V5_DEFLATED -> {
                // Version 1 / Version 3 / Version 4 / Version 5 (Deflated with 16-bit Quantization + Images + Text Blocks)
                val compressedSize = `in`.readInt()
                val compressedBytes = ByteArray(compressedSize)
                `in`.readFully(compressedBytes)

                val strokeByteIn = ByteArrayInputStream(compressedBytes)
                val inflaterIn = InflaterInputStream(strokeByteIn, Inflater())
                val strokeDataIn = DataInputStream(inflaterIn)

                val strokeCount = strokeDataIn.readInt()
                val strokes = ArrayList<Stroke>(strokeCount)

                for (s in 0 until strokeCount) {
                    val strokeId = strokeDataIn.readUTF()
                    val toneId = strokeDataIn.readInt()
                    val tone = PencilTone.entries.find { it.id == toneId } ?: PencilTone.SOFT
                    val baseWidth = strokeDataIn.readFloat()
                    val colorRgb = strokeDataIn.readLong()
                    val paletteIndex = strokeDataIn.readInt()
                    val isInk = strokeDataIn.readBoolean()
                    val isHighlighter = if (version >= VERSION_V3_DEFLATED) strokeDataIn.readBoolean() else false

                    val pointCount = strokeDataIn.readInt()
                    val points = ArrayList<StrokePoint>(pointCount)

                    if (pointCount > 0) {
                        var curX = strokeDataIn.readFloat()
                        var curY = strokeDataIn.readFloat()
                        var curP = (strokeDataIn.readByte().toInt() and 0xFF) / 255.0f
                        var curTime = strokeDataIn.readLong()
                        points.add(StrokePoint(curX, curY, curP, curTime))

                        for (p in 1 until pointCount) {
                            val dx = strokeDataIn.readShort()
                            val dy = strokeDataIn.readShort()
                            val rawP = strokeDataIn.readByte()
                            val dt = strokeDataIn.readInt()

                            curX += dx.toFloat() / V1_QUANTIZE_SCALE
                            curY += dy.toFloat() / V1_QUANTIZE_SCALE
                            curP = (rawP.toInt() and 0xFF) / 255.0f
                            curTime += dt

                            points.add(StrokePoint(curX, curY, curP, curTime))
                        }
                    }

                    strokes.add(
                        Stroke(
                            id = strokeId,
                            points = points,
                            tone = tone,
                            baseWidth = baseWidth,
                            colorRgb = colorRgb,
                            paletteIndex = paletteIndex,
                            isInk = isInk,
                            isHighlighter = isHighlighter
                        )
                    )
                }

                val images = if (version >= VERSION_V4_DEFLATED) {
                    val imgCount = `in`.readInt()
                    val list = ArrayList<com.antigrav.bignotes.core.model.CanvasImage>(imgCount)
                    for (i in 0 until imgCount) {
                        list.add(
                            com.antigrav.bignotes.core.model.CanvasImage(
                                id = `in`.readUTF(),
                                localFileName = `in`.readUTF(),
                                centerX = `in`.readFloat(),
                                centerY = `in`.readFloat(),
                                width = `in`.readFloat(),
                                height = `in`.readFloat(),
                                rotationDegrees = `in`.readFloat(),
                                cropLeft = `in`.readFloat(),
                                cropTop = `in`.readFloat(),
                                cropRight = `in`.readFloat(),
                                cropBottom = `in`.readFloat(),
                                zIndex = `in`.readLong()
                            )
                        )
                    }
                    list
                } else {
                    emptyList()
                }

                val textBlocks = if (version >= VERSION_V5_DEFLATED) {
                    val count = `in`.readInt()
                    val list = ArrayList<RecognizedTextBlock>(count)
                    for (i in 0 until count) {
                        list.add(
                            RecognizedTextBlock(
                                clusterId = `in`.readUTF(),
                                text = `in`.readUTF(),
                                minX = `in`.readFloat(),
                                minY = `in`.readFloat(),
                                maxX = `in`.readFloat(),
                                maxY = `in`.readFloat(),
                                updatedAt = `in`.readLong()
                            )
                        )
                    }
                    list
                } else {
                    emptyList()
                }

                BigNoteDocument(
                    noteId = noteId,
                    folderId = folderId,
                    title = title,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    zoom = zoom,
                    scrollY = scrollY,
                    strokes = strokes,
                    images = images,
                    textBlocks = textBlocks
                )

            }
            else -> throw IOException("Unsupported .bignote version: $version")
        }
    }
}
