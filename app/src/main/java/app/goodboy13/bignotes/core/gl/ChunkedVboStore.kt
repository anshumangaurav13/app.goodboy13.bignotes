package app.goodboy13.bignotes.core.gl

import android.opengl.GLES30
import app.goodboy13.bignotes.core.geometry.CachedStrokeMesh
import app.goodboy13.bignotes.core.geometry.StrokeTessellator
import app.goodboy13.bignotes.core.model.Stroke
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * GPU-resident VBO chunk that holds up to [CHUNK_MAX_VERTICES] vertices in GL VRAM.
 * Tracks world-space AABB for fast viewport frustum culling.
 */
class VboChunk(
    val chunkIndex: Int,
    var vboId: Int = 0,
    var vertexCount: Int = 0,
    val strokes: MutableList<Stroke> = mutableListOf(),
    var minX: Float = Float.POSITIVE_INFINITY,
    var minY: Float = Float.POSITIVE_INFINITY,
    var maxX: Float = Float.NEGATIVE_INFINITY,
    var maxY: Float = Float.NEGATIVE_INFINITY,
    var isDirty: Boolean = true,
    var isSealed: Boolean = false
) {
    fun containsStroke(id: String): Boolean = strokes.any { it.id == id }

    fun intersects(bMinX: Float, bMinY: Float, bMaxX: Float, bMaxY: Float): Boolean {
        if (vertexCount == 0 || strokes.isEmpty()) return false
        return minX <= bMaxX && maxX >= bMinX && minY <= bMaxY && maxY >= bMinY
    }

    fun recomputeBounds() {
        minX = Float.POSITIVE_INFINITY
        minY = Float.POSITIVE_INFINITY
        maxX = Float.NEGATIVE_INFINITY
        maxY = Float.NEGATIVE_INFINITY
        for (i in 0 until strokes.size) {
            val s = strokes[i]
            if (s.minX < minX) minX = s.minX
            if (s.minY < minY) minY = s.minY
            if (s.maxX > maxX) maxX = s.maxX
            if (s.maxY > maxY) maxY = s.maxY
        }
    }
}

/**
 * Manages static GPU-resident chunked VBOs for committed sketch strokes.
 *
 * Eliminates per-frame CPU-to-GPU vertex streaming during camera re-anchors
 * by maintaining static VBOs on the GPU and applying AABB frustum culling
 * to skip off-screen chunks entirely.
 */
class ChunkedVboStore {

    companion object {
        // Target vertices per chunk for spatial partitioning and frustum culling.
        // Chunks dynamically grow to accommodate strokes of any size with ZERO truncation.
        const val CHUNK_TARGET_VERTICES = 65520
    }

    private val chunks = mutableListOf<VboChunk>()
    private var nextChunkIndex = 0

    // Reusable direct float buffer for VBO upload (allocated once, dynamically grows if single stroke exceeds capacity)
    private var scratchDirectByteBuffer: ByteBuffer? = null
    private var scratchFloatBuffer: FloatBuffer? = null
    private var scratchVboId = 0

    private fun ensureScratchBuffer(requiredVerts: Int = CHUNK_TARGET_VERTICES): FloatBuffer {
        val targetVerts = maxOf(CHUNK_TARGET_VERTICES, requiredVerts)
        val requiredBytes = targetVerts * StrokeTessellator.STRIDE_BYTES
        val cur = scratchDirectByteBuffer
        if (cur == null || cur.capacity() < requiredBytes) {
            val byteBuf = ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.nativeOrder())
            scratchDirectByteBuffer = byteBuf
            scratchFloatBuffer = byteBuf.asFloatBuffer()
        }
        val fb = scratchFloatBuffer!!
        fb.clear()
        return fb
    }

    @Synchronized
    fun getAllStrokes(): List<Stroke> {
        val result = ArrayList<Stroke>()
        for (chunk in chunks) {
            result.addAll(chunk.strokes)
        }
        return result
    }

    @Synchronized
    fun getChunkCount(): Int = chunks.size

    @Synchronized
    fun appendStroke(stroke: Stroke, getMesh: (Stroke) -> CachedStrokeMesh) {
        val mesh = getMesh(stroke)
        val strokeVerts = mesh.vertexCount
        if (strokeVerts == 0) return

        var tail = chunks.lastOrNull()

        // If tail chunk has strokes and adding this stroke would exceed target chunk size, seal it
        if (tail != null && (!tail.isSealed) && tail.strokes.isNotEmpty() && (tail.vertexCount + strokeVerts > CHUNK_TARGET_VERTICES)) {
            tail.isSealed = true
            tail = null
        }

        if (tail == null || tail.isSealed) {
            tail = VboChunk(chunkIndex = nextChunkIndex++)
            chunks.add(tail)
        }

        tail.strokes.add(stroke)
        tail.vertexCount += strokeVerts
        if (stroke.minX < tail.minX) tail.minX = stroke.minX
        if (stroke.minY < tail.minY) tail.minY = stroke.minY
        if (stroke.maxX > tail.maxX) tail.maxX = stroke.maxX
        if (stroke.maxY > tail.maxY) tail.maxY = stroke.maxY
        tail.isDirty = true

        // If tail has reached or exceeded target size, seal it so next stroke starts in a fresh chunk
        if (tail.vertexCount >= CHUNK_TARGET_VERTICES) {
            tail.isSealed = true
        }
    }

    @Synchronized
    fun appendStrokes(strokes: List<Stroke>, getMesh: (Stroke) -> CachedStrokeMesh) {
        for (i in 0 until strokes.size) {
            appendStroke(strokes[i], getMesh)
        }
    }

    @Synchronized
    fun removeStrokes(removedIds: Set<String>, getMesh: (Stroke) -> CachedStrokeMesh) {
        if (removedIds.isEmpty()) return
        val toRemoveChunks = mutableListOf<VboChunk>()

        for (chunk in chunks) {
            val hadStrokes = chunk.strokes.any { it.id in removedIds }
            if (hadStrokes) {
                chunk.strokes.removeAll { it.id in removedIds }
                if (chunk.strokes.isEmpty()) {
                    toRemoveChunks.add(chunk)
                } else {
                    chunk.recomputeBounds()
                    // Recalculate vertex count
                    var vCount = 0
                    for (s in chunk.strokes) {
                        vCount += getMesh(s).vertexCount
                    }
                    chunk.vertexCount = vCount
                    chunk.isDirty = true
                }
            }
        }

        for (ch in toRemoveChunks) {
            deleteChunkVbo(ch)
            chunks.remove(ch)
        }
    }

    @Synchronized
    fun replaceStrokes(
        removedIds: Set<String>,
        added: List<Stroke>,
        getMesh: (Stroke) -> CachedStrokeMesh
    ) {
        removeStrokes(removedIds, getMesh)
        appendStrokes(added, getMesh)
    }

    @Synchronized
    fun setAllStrokes(strokes: List<Stroke>, getMesh: (Stroke) -> CachedStrokeMesh) {
        clear()
        appendStrokes(strokes, getMesh)
    }

    private val pendingDeleteVbos = mutableListOf<Int>()

    private fun deleteChunkVbo(chunk: VboChunk) {
        if (chunk.vboId != 0) {
            synchronized(pendingDeleteVbos) {
                pendingDeleteVbos.add(chunk.vboId)
            }
            chunk.vboId = 0
        }
    }

    private fun uploadChunk(chunk: VboChunk, getMesh: (Stroke) -> CachedStrokeMesh) {
        if (chunk.strokes.isEmpty()) {
            chunk.vertexCount = 0
            chunk.isDirty = false
            return
        }

        var requiredVerts = 0
        for (i in 0 until chunk.strokes.size) {
            requiredVerts += getMesh(chunk.strokes[i]).vertexCount
        }

        if (requiredVerts == 0) {
            chunk.vertexCount = 0
            chunk.isDirty = false
            return
        }

        val floatBuf = ensureScratchBuffer(requiredVerts)
        var totalVerts = 0

        for (i in 0 until chunk.strokes.size) {
            val stroke = chunk.strokes[i]
            val mesh = getMesh(stroke)
            val vCount = mesh.vertexCount
            if (vCount == 0) continue

            val floatsToPut = vCount * StrokeTessellator.FLOATS_PER_VERTEX
            floatBuf.put(mesh.data, 0, floatsToPut)
            totalVerts += vCount
        }

        chunk.vertexCount = totalVerts
        floatBuf.position(0)
        val bytes = totalVerts * StrokeTessellator.STRIDE_BYTES

        if (chunk.vboId == 0) {
            val vbos = IntArray(1)
            GLES30.glGenBuffers(1, vbos, 0)
            chunk.vboId = vbos[0]
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, chunk.vboId)
        val usage = if (chunk.isSealed) GLES30.GL_STATIC_DRAW else GLES30.GL_DYNAMIC_DRAW
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, bytes, floatBuf, usage)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        chunk.isDirty = false
    }

    /**
     * Renders all visible chunks directly from GPU memory.
     * Frustum culls off-screen chunks against [visMinX, visMinY, visMaxX, visMaxY].
     */
    @Synchronized
    fun renderVisibleChunks(
        shader: GraphiteShader,
        visMinX: Float,
        visMinY: Float,
        visMaxX: Float,
        visMaxY: Float,
        excludedStrokeIds: Set<String>,
        getMesh: (Stroke) -> CachedStrokeMesh
    ) {
        synchronized(pendingDeleteVbos) {
            if (pendingDeleteVbos.isNotEmpty()) {
                val ids = pendingDeleteVbos.toIntArray()
                GLES30.glDeleteBuffers(ids.size, ids, 0)
                pendingDeleteVbos.clear()
            }
        }

        shader.prepareVboDraw()
        try {
            for (i in 0 until chunks.size) {
                val chunk = chunks[i]
                if (chunk.vertexCount == 0 && !chunk.isDirty) continue

                // Upload modified/appended chunk data to GPU before culling so bounds are up-to-date
                if (chunk.isDirty) {
                    uploadChunk(chunk, getMesh)
                }

                if (chunk.vertexCount == 0) continue

                // Viewport AABB Frustum Culling
                if (!chunk.intersects(visMinX, visMinY, visMaxX, visMaxY)) {
                    continue
                }

                if (chunk.vboId == 0) continue

                // If lasso selection is active and this chunk contains an excluded stroke,
                // render only unselected strokes for this chunk via dynamic scratch buffer
                if (excludedStrokeIds.isNotEmpty() && chunk.strokes.any { it.id in excludedStrokeIds }) {
                    renderChunkExcluding(shader, chunk, excludedStrokeIds, getMesh)
                } else {
                    // FAST PATH: Zero CPU buffer copy, direct GPU VBO dispatch!
                    shader.renderVboDirect(chunk.vboId, chunk.vertexCount)
                }
            }
        } finally {
            shader.finishVboDraw()
        }
    }

    private fun renderChunkExcluding(
        shader: GraphiteShader,
        chunk: VboChunk,
        excludedStrokeIds: Set<String>,
        getMesh: (Stroke) -> CachedStrokeMesh
    ) {
        val floatBuf = ensureScratchBuffer(chunk.vertexCount)
        var drawnVerts = 0

        for (i in 0 until chunk.strokes.size) {
            val stroke = chunk.strokes[i]
            if (stroke.id in excludedStrokeIds) continue
            val mesh = getMesh(stroke)
            val vCount = mesh.vertexCount
            if (vCount == 0) continue

            val floatsToPut = vCount * StrokeTessellator.FLOATS_PER_VERTEX
            floatBuf.put(mesh.data, 0, floatsToPut)
            drawnVerts += vCount
        }

        if (drawnVerts > 0) {
            floatBuf.position(0)
            val bytes = drawnVerts * StrokeTessellator.STRIDE_BYTES
            if (scratchVboId == 0) {
                val vbos = IntArray(1)
                GLES30.glGenBuffers(1, vbos, 0)
                scratchVboId = vbos[0]
            }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, scratchVboId)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, bytes, floatBuf, GLES30.GL_DYNAMIC_DRAW)
            shader.renderVboDirect(scratchVboId, drawnVerts)
        }
    }

    /**
     * Renders a specific collection of strokes (e.g. for selection FBO or live incremental append)
     * using the reusable scratch buffer.
     */
    fun renderStrokesDirect(
        shader: GraphiteShader,
        strokes: Iterable<Stroke>,
        getMesh: (Stroke) -> CachedStrokeMesh
    ) {
        val iter = strokes.iterator()
        if (!iter.hasNext()) return
        val floatBuf = ensureScratchBuffer(CHUNK_TARGET_VERTICES)
        var batchVerts = 0

        if (scratchVboId == 0) {
            val vbos = IntArray(1)
            GLES30.glGenBuffers(1, vbos, 0)
            scratchVboId = vbos[0]
        }

        fun flushBatch() {
            if (batchVerts == 0) return
            floatBuf.position(0)
            val bytes = batchVerts * StrokeTessellator.STRIDE_BYTES
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, scratchVboId)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, bytes, floatBuf, GLES30.GL_DYNAMIC_DRAW)
            shader.prepareVboDraw()
            shader.renderVboDirect(scratchVboId, batchVerts)
            shader.finishVboDraw()
            floatBuf.clear()
            batchVerts = 0
        }

        for (stroke in strokes) {
            val mesh = getMesh(stroke)
            val vertexCount = mesh.vertexCount
            if (vertexCount == 0) continue

            val data = mesh.data
            var dataOffset = 0
            var remainingVerts = vertexCount

            while (remainingVerts > 0) {
                val spaceVerts = CHUNK_TARGET_VERTICES - batchVerts
                val trianglesAvailable = spaceVerts / 3
                if (trianglesAvailable == 0) {
                    flushBatch()
                    continue
                }
                val trianglesRemaining = remainingVerts / 3
                val trianglesToPut = minOf(trianglesRemaining, trianglesAvailable)
                val vertsToPut = trianglesToPut * 3
                val floatsToPut = vertsToPut * StrokeTessellator.FLOATS_PER_VERTEX

                floatBuf.put(data, dataOffset, floatsToPut)
                batchVerts += vertsToPut
                dataOffset += floatsToPut
                remainingVerts -= vertsToPut

                if (batchVerts >= CHUNK_TARGET_VERTICES) {
                    flushBatch()
                }
            }
        }
        flushBatch()
    }

    @Synchronized
    fun clear() {
        for (chunk in chunks) {
            deleteChunkVbo(chunk)
        }
        chunks.clear()
        nextChunkIndex = 0
    }

    @Synchronized
    fun getMaxStrokeY(): Float {
        var max = 0f
        for (i in 0 until chunks.size) {
            val y = chunks[i].maxY
            if (y.isFinite() && y > max) max = y
        }
        return max
    }

    @Synchronized
    fun cleanup() {
        clear()
        synchronized(pendingDeleteVbos) {
            if (pendingDeleteVbos.isNotEmpty()) {
                val ids = pendingDeleteVbos.toIntArray()
                GLES30.glDeleteBuffers(ids.size, ids, 0)
                pendingDeleteVbos.clear()
            }
        }
        if (scratchVboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(scratchVboId), 0)
            scratchVboId = 0
        }
        scratchDirectByteBuffer = null
        scratchFloatBuffer = null
    }
}

