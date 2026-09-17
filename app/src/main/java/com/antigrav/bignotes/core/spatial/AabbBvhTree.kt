package com.antigrav.bignotes.core.spatial

import android.graphics.PointF
import com.antigrav.bignotes.core.model.Stroke
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Node in the Dynamic AABB Bounding Volume Hierarchy.
 */
class BvhNode(
    var id: Int = -1,
    var minX: Float = 0f,
    var minY: Float = 0f,
    var maxX: Float = 0f,
    var maxY: Float = 0f,
    var parent: Int = -1,
    var left: Int = -1,
    var right: Int = -1,
    var height: Int = 0,
    var stroke: Stroke? = null
) {
    val isLeaf: Boolean get() = left == -1

    fun surfaceArea(): Float {
        val w = maxX - minX
        val h = maxY - minY
        return 2f * (w + h)
    }

    fun setBounds(otherMinX: Float, otherMinY: Float, otherMaxX: Float, otherMaxY: Float) {
        minX = otherMinX
        minY = otherMinY
        maxX = otherMaxX
        maxY = otherMaxY
    }

    fun union(b1: BvhNode, b2: BvhNode) {
        minX = minOf(b1.minX, b2.minX)
        minY = minOf(b1.minY, b2.minY)
        maxX = maxOf(b1.maxX, b2.maxX)
        maxY = maxOf(b1.maxY, b2.maxY)
    }
}

/**
 * Dynamic AABB Tree (Bounding Volume Hierarchy) for spatial indexing of strokes.
 *
 * Replaces fixed-cell grids with a hierarchical, scale-independent structure:
 * - O(log N) insertion with Surface Area Heuristic (SAH) and AVL tree rotations.
 * - O(log N) removal and fast subtree refitting.
 * - O(N log N) optimal bulk-loading (median split along max-variance axis).
 * - O(log N) circle and box queries with pre-allocated traversal stacks (zero GC churn).
 */
class AabbBvhTree(initialCapacity: Int = 512) {

    private var nodes = Array(initialCapacity) { id -> BvhNode(id = id) }
    private var rootIndex: Int = -1
    private var freeListHead: Int = 0
    private var nodeCount: Int = 0

    // Stroke ID -> Leaf node index
    private val strokeMap = ConcurrentHashMap<String, Int>()

    // Reusable traversal stack for non-allocating queries (thread-safe per query via synchronized)
    private var queryStack = IntArray(256)

    init {
        // Initialize free list linked via 'parent'
        for (i in 0 until initialCapacity - 1) {
            nodes[i].parent = i + 1
        }
        nodes[initialCapacity - 1].parent = -1
    }

    private fun allocateNode(): Int {
        if (freeListHead == -1) {
            val oldCap = nodes.size
            val newCap = oldCap * 2
            val newNodes = Array(newCap) { id ->
                if (id < oldCap) nodes[id] else BvhNode(id = id)
            }
            for (i in oldCap until newCap - 1) {
                newNodes[i].parent = i + 1
            }
            newNodes[newCap - 1].parent = -1
            freeListHead = oldCap
            nodes = newNodes
        }
        val nodeId = freeListHead
        freeListHead = nodes[nodeId].parent
        val node = nodes[nodeId]
        node.parent = -1
        node.left = -1
        node.right = -1
        node.height = 0
        node.stroke = null
        nodeCount++
        return nodeId
    }

    private fun freeNode(nodeId: Int) {
        val node = nodes[nodeId]
        node.stroke = null
        node.parent = freeListHead
        node.left = -1
        node.right = -1
        freeListHead = nodeId
        nodeCount--
    }

    @Synchronized
    fun clear() {
        strokeMap.clear()
        rootIndex = -1
        nodeCount = 0
        for (i in 0 until nodes.size - 1) {
            nodes[i].parent = i + 1
            nodes[i].left = -1
            nodes[i].right = -1
            nodes[i].stroke = null
        }
        nodes[nodes.size - 1].parent = -1
        nodes[nodes.size - 1].stroke = null
        freeListHead = 0
    }

    @Synchronized
    fun size(): Int = strokeMap.size

    @Synchronized
    fun get(strokeId: String): Stroke? {
        val leafIdx = strokeMap[strokeId] ?: return null
        return nodes[leafIdx].stroke
    }

    @Synchronized
    fun insert(stroke: Stroke) {
        val existing = strokeMap[stroke.id]
        if (existing != null) {
            remove(stroke.id)
        }

        val leaf = allocateNode()
        val node = nodes[leaf]
        node.stroke = stroke

        val pad = stroke.baseWidth * 0.5f
        node.minX = stroke.minX - pad
        node.minY = stroke.minY - pad
        node.maxX = stroke.maxX + pad
        node.maxY = stroke.maxY + pad
        node.height = 0

        insertLeaf(leaf)
        strokeMap[stroke.id] = leaf
    }

    @Synchronized
    fun remove(strokeId: String) {
        val leaf = strokeMap.remove(strokeId) ?: return
        removeLeaf(leaf)
        freeNode(leaf)
    }

    private fun insertLeaf(leaf: Int) {
        if (rootIndex == -1) {
            rootIndex = leaf
            nodes[leaf].parent = -1
            return
        }

        val leafNode = nodes[leaf]
        var sibling = rootIndex

        // 1. Find the best sibling using Surface Area Heuristic (SAH)
        while (!nodes[sibling].isLeaf) {
            val left = nodes[sibling].left
            val right = nodes[sibling].right

            val area = nodes[sibling].surfaceArea()

            val combinedMinX = minOf(nodes[sibling].minX, leafNode.minX)
            val combinedMinY = minOf(nodes[sibling].minY, leafNode.minY)
            val combinedMaxX = maxOf(nodes[sibling].maxX, leafNode.maxX)
            val combinedMaxY = maxOf(nodes[sibling].maxY, leafNode.maxY)
            val combinedArea = 2f * ((combinedMaxX - combinedMinX) + (combinedMaxY - combinedMinY))

            // Cost of creating a new parent for this node and the new leaf
            val cost = 2f * combinedArea
            val inheritanceCost = 2f * (combinedArea - area)

            // Cost of descending into left
            val costLeft = if (nodes[left].isLeaf) {
                val cMinX = minOf(nodes[left].minX, leafNode.minX)
                val cMinY = minOf(nodes[left].minY, leafNode.minY)
                val cMaxX = maxOf(nodes[left].maxX, leafNode.maxX)
                val cMaxY = maxOf(nodes[left].maxY, leafNode.maxY)
                2f * ((cMaxX - cMinX) + (cMaxY - cMinY)) + inheritanceCost
            } else {
                val cMinX = minOf(nodes[left].minX, leafNode.minX)
                val cMinY = minOf(nodes[left].minY, leafNode.minY)
                val cMaxX = maxOf(nodes[left].maxX, leafNode.maxX)
                val cMaxY = maxOf(nodes[left].maxY, leafNode.maxY)
                val newArea = 2f * ((cMaxX - cMinX) + (cMaxY - cMinY))
                (newArea - nodes[left].surfaceArea()) + inheritanceCost
            }

            // Cost of descending into right
            val costRight = if (nodes[right].isLeaf) {
                val cMinX = minOf(nodes[right].minX, leafNode.minX)
                val cMinY = minOf(nodes[right].minY, leafNode.minY)
                val cMaxX = maxOf(nodes[right].maxX, leafNode.maxX)
                val cMaxY = maxOf(nodes[right].maxY, leafNode.maxY)
                2f * ((cMaxX - cMinX) + (cMaxY - cMinY)) + inheritanceCost
            } else {
                val cMinX = minOf(nodes[right].minX, leafNode.minX)
                val cMinY = minOf(nodes[right].minY, leafNode.minY)
                val cMaxX = maxOf(nodes[right].maxX, leafNode.maxX)
                val cMaxY = maxOf(nodes[right].maxY, leafNode.maxY)
                val newArea = 2f * ((cMaxX - cMinX) + (cMaxY - cMinY))
                (newArea - nodes[right].surfaceArea()) + inheritanceCost
            }

            // Descend according to minimum cost
            sibling = if (cost < costLeft && cost < costRight) {
                break
            } else if (costLeft < costRight) {
                left
            } else {
                right
            }
        }

        // 2. Create a new parent
        val oldParent = nodes[sibling].parent
        val newParent = allocateNode()
        nodes[newParent].parent = oldParent
        nodes[newParent].union(leafNode, nodes[sibling])
        nodes[newParent].height = nodes[sibling].height + 1

        if (oldParent != -1) {
            // Sibling was not root
            if (nodes[oldParent].left == sibling) {
                nodes[oldParent].left = newParent
            } else {
                nodes[oldParent].right = newParent
            }
            nodes[newParent].left = sibling
            nodes[newParent].right = leaf
            nodes[sibling].parent = newParent
            nodes[leaf].parent = newParent
        } else {
            // Sibling was root
            nodes[newParent].left = sibling
            nodes[newParent].right = leaf
            nodes[sibling].parent = newParent
            nodes[leaf].parent = newParent
            rootIndex = newParent
        }

        // 3. Walk back up the tree refitting and balancing
        var current = nodes[leaf].parent
        while (current != -1) {
            current = balance(current)

            val l = nodes[current].left
            val r = nodes[current].right
            nodes[current].height = 1 + max(nodes[l].height, nodes[r].height)
            nodes[current].union(nodes[l], nodes[r])

            current = nodes[current].parent
        }
    }

    private fun removeLeaf(leaf: Int) {
        if (leaf == rootIndex) {
            rootIndex = -1
            return
        }

        val parent = nodes[leaf].parent
        val grandParent = nodes[parent].parent
        val sibling = if (nodes[parent].left == leaf) nodes[parent].right else nodes[parent].left

        if (grandParent != -1) {
            // Destroy parent and connect sibling to grandParent
            if (nodes[grandParent].left == parent) {
                nodes[grandParent].left = sibling
            } else {
                nodes[grandParent].right = sibling
            }
            nodes[sibling].parent = grandParent
            freeNode(parent)

            // Walk back up the tree refitting and balancing
            var current = grandParent
            while (current != -1) {
                current = balance(current)

                val l = nodes[current].left
                val r = nodes[current].right
                nodes[current].union(nodes[l], nodes[r])
                nodes[current].height = 1 + max(nodes[l].height, nodes[r].height)

                current = nodes[current].parent
            }
        } else {
            rootIndex = sibling
            nodes[sibling].parent = -1
            freeNode(parent)
        }
    }

    /**
     * Perform tree rotations (AVL-style) to balance node [iA].
     */
    private fun balance(iA: Int): Int {
        val a = nodes[iA]
        if (a.isLeaf || a.height < 2) return iA

        val iB = a.left
        val iC = a.right
        val b = nodes[iB]
        val c = nodes[iC]

        val balance = c.height - b.height

        // Rotate C up
        if (balance > 1) {
            val iF = c.left
            val iG = c.right
            val f = nodes[iF]
            val g = nodes[iG]

            // Swap A and C
            c.left = iA
            c.parent = a.parent
            a.parent = iC

            // A's old parent points to C
            if (c.parent != -1) {
                if (nodes[c.parent].left == iA) {
                    nodes[c.parent].left = iC
                } else {
                    nodes[c.parent].right = iC
                }
            } else {
                rootIndex = iC
            }

            // Rotate
            if (f.height > g.height) {
                c.right = iF
                a.right = iG
                g.parent = iA
                a.union(b, g)
                c.union(a, f)

                a.height = 1 + max(b.height, g.height)
                c.height = 1 + max(a.height, f.height)
            } else {
                c.right = iG
                a.right = iF
                f.parent = iA
                a.union(b, f)
                c.union(a, g)

                a.height = 1 + max(b.height, f.height)
                c.height = 1 + max(a.height, g.height)
            }
            return iC
        }

        // Rotate B up
        if (balance < -1) {
            val iD = b.left
            val iE = b.right
            val d = nodes[iD]
            val e = nodes[iE]

            // Swap A and B
            b.left = iA
            b.parent = a.parent
            a.parent = iB

            // A's old parent points to B
            if (b.parent != -1) {
                if (nodes[b.parent].left == iA) {
                    nodes[b.parent].left = iB
                } else {
                    nodes[b.parent].right = iB
                }
            } else {
                rootIndex = iB
            }

            // Rotate
            if (d.height > e.height) {
                b.right = iD
                a.left = iE
                e.parent = iA
                a.union(c, e)
                b.union(a, d)

                a.height = 1 + max(c.height, e.height)
                b.height = 1 + max(a.height, d.height)
            } else {
                b.right = iE
                a.left = iD
                d.parent = iA
                a.union(c, d)
                b.union(a, e)

                a.height = 1 + max(c.height, d.height)
                b.height = 1 + max(a.height, e.height)
            }
            return iB
        }

        return iA
    }

    /**
     * Optimal bulk-loading for stroke collections:
     * Recursively splits strokes along the axis of maximum variance (X or Y),
     * producing a perfectly balanced binary tree with minimal bounding-box overlap.
     */
    @Synchronized
    fun rebuild(strokes: Collection<Stroke>) {
        clear()
        if (strokes.isEmpty()) return

        val strokeList = ArrayList(strokes)
        val leafIndices = IntArray(strokeList.size)

        for (i in strokeList.indices) {
            val s = strokeList[i]
            val leaf = allocateNode()
            val n = nodes[leaf]
            n.stroke = s
            val pad = s.baseWidth * 0.5f
            n.minX = s.minX - pad
            n.minY = s.minY - pad
            n.maxX = s.maxX + pad
            n.maxY = s.maxY + pad
            n.height = 0
            strokeMap[s.id] = leaf
            leafIndices[i] = leaf
        }

        rootIndex = buildRecursive(leafIndices, 0, leafIndices.size - 1)
        if (rootIndex != -1) {
            nodes[rootIndex].parent = -1
        }
    }

    private fun buildRecursive(leaves: IntArray, start: Int, end: Int): Int {
        val count = end - start + 1
        if (count == 1) {
            return leaves[start]
        }
        if (count == 2) {
            val parent = allocateNode()
            val left = leaves[start]
            val right = leaves[end]
            nodes[parent].left = left
            nodes[parent].right = right
            nodes[parent].union(nodes[left], nodes[right])
            nodes[parent].height = 1
            nodes[left].parent = parent
            nodes[right].parent = parent
            return parent
        }

        // Determine axis of maximum variance
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        for (i in start..end) {
            val n = nodes[leaves[i]]
            val cx = (n.minX + n.maxX) * 0.5f
            val cy = (n.minY + n.maxY) * 0.5f
            if (cx < minX) minX = cx
            if (cx > maxX) maxX = cx
            if (cy < minY) minY = cy
            if (cy > maxY) maxY = cy
        }

        val splitX = (maxX - minX) >= (maxY - minY)
        val mid = (start + end) / 2

        // Partial sort/partition around median
        quickSelect(leaves, start, end, mid, splitX)

        val leftChild = buildRecursive(leaves, start, mid)
        val rightChild = buildRecursive(leaves, mid + 1, end)

        val parent = allocateNode()
        nodes[parent].left = leftChild
        nodes[parent].right = rightChild
        nodes[parent].union(nodes[leftChild], nodes[rightChild])
        nodes[parent].height = 1 + max(nodes[leftChild].height, nodes[rightChild].height)
        nodes[leftChild].parent = parent
        nodes[rightChild].parent = parent
        return parent
    }

    private fun quickSelect(leaves: IntArray, start: Int, end: Int, k: Int, splitX: Boolean) {
        var s = start
        var e = end
        while (s < e) {
            val pivotIdx = partition(leaves, s, e, splitX)
            if (pivotIdx == k) {
                return
            } else if (pivotIdx < k) {
                s = pivotIdx + 1
            } else {
                e = pivotIdx - 1
            }
        }
    }

    private fun partition(leaves: IntArray, start: Int, end: Int, splitX: Boolean): Int {
        val mid = (start + end) / 2
        val pivotVal = getNodeCenter(leaves[mid], splitX)
        val tmp = leaves[mid]
        leaves[mid] = leaves[end]
        leaves[end] = tmp

        var store = start
        for (i in start until end) {
            if (getNodeCenter(leaves[i], splitX) < pivotVal) {
                val t = leaves[i]
                leaves[i] = leaves[store]
                leaves[store] = t
                store++
            }
        }
        val t = leaves[store]
        leaves[store] = leaves[end]
        leaves[end] = t
        return store
    }

    private fun getNodeCenter(nodeId: Int, splitX: Boolean): Float {
        val n = nodes[nodeId]
        return if (splitX) (n.minX + n.maxX) * 0.5f else (n.minY + n.maxY) * 0.5f
    }

    /**
     * Queries all strokes whose bounding boxes intersect the circle centered at (cx, cy) with radius R.
     * Scale-independent: operates efficiently at 10% zoom as well as 1000% zoom.
     */
    @Synchronized
    fun queryCircle(cx: Float, cy: Float, radius: Float): List<Stroke> {
        if (rootIndex == -1) return emptyList()

        val radSq = radius * radius
        val results = ArrayList<Stroke>(32)

        var stackPtr = 0
        if (queryStack.size < 256) queryStack = IntArray(256)
        queryStack[stackPtr++] = rootIndex

        while (stackPtr > 0) {
            val current = queryStack[--stackPtr]
            val node = nodes[current]

            // Distance from circle center (cx, cy) to closest point on AABB
            val clampedX = cx.coerceIn(node.minX, node.maxX)
            val clampedY = cy.coerceIn(node.minY, node.maxY)
            val dx = cx - clampedX
            val dy = cy - clampedY

            if ((dx * dx + dy * dy) <= radSq) {
                if (node.isLeaf) {
                    node.stroke?.let { results.add(it) }
                } else {
                    if (stackPtr + 2 >= queryStack.size) {
                        queryStack = queryStack.copyOf(queryStack.size * 2)
                    }
                    queryStack[stackPtr++] = node.left
                    queryStack[stackPtr++] = node.right
                }
            }
        }

        return results
    }

    /**
     * Queries all strokes whose bounding boxes intersect the given rectangular bounds.
     */
    @Synchronized
    fun queryRect(minX: Float, minY: Float, maxX: Float, maxY: Float): List<Stroke> {
        if (rootIndex == -1) return emptyList()

        val results = ArrayList<Stroke>(32)

        var stackPtr = 0
        if (queryStack.size < 256) queryStack = IntArray(256)
        queryStack[stackPtr++] = rootIndex

        while (stackPtr > 0) {
            val current = queryStack[--stackPtr]
            val node = nodes[current]

            // Check AABB vs AABB overlap
            if (node.minX <= maxX && node.maxX >= minX && node.minY <= maxY && node.maxY >= minY) {
                if (node.isLeaf) {
                    node.stroke?.let { results.add(it) }
                } else {
                    if (stackPtr + 2 >= queryStack.size) {
                        queryStack = queryStack.copyOf(queryStack.size * 2)
                    }
                    queryStack[stackPtr++] = node.left
                    queryStack[stackPtr++] = node.right
                }
            }
        }

        return results
    }

    /**
     * Queries all strokes intersecting or inside a closed polygon (e.g. Lasso selection).
     */
    @Synchronized
    fun queryPolygon(poly: List<PointF>): List<Stroke> {
        if (poly.size < 3 || rootIndex == -1) return emptyList()

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        for (i in poly.indices) {
            val p = poly[i]
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }

        // Broad phase: query rectangular bounding box via BVH
        val candidates = queryRect(minX, minY, maxX, maxY)
        if (candidates.isEmpty()) return emptyList()

        // Simplify polygon if needed
        val simplifiedPoly = if (poly.size > 48) {
            val step = maxOf(1, poly.size / 40)
            val simplified = ArrayList<PointF>(48)
            for (i in poly.indices step step) {
                simplified.add(poly[i])
            }
            if (simplified.last() != poly.last()) {
                simplified.add(poly.last())
            }
            simplified
        } else {
            poly
        }

        // Narrow phase: test stroke points inside polygon
        val selected = ArrayList<Stroke>(candidates.size)
        for (i in candidates.indices) {
            val stroke = candidates[i]
            val pts = stroke.points
            if (pts.isEmpty()) continue

            // Centroid test
            val midIdx = pts.size / 2
            val cx = (pts[0].x + pts[midIdx].x + pts.last().x) / 3f
            val cy = (pts[0].y + pts[midIdx].y + pts.last().y) / 3f

            if (isPointInPolygon(cx, cy, simplifiedPoly)) {
                selected.add(stroke)
                continue
            }

            // Fallback endpoints
            if (isPointInPolygon(pts[0].x, pts[0].y, simplifiedPoly) ||
                isPointInPolygon(pts.last().x, pts.last().y, simplifiedPoly) ||
                isPointInPolygon(pts[midIdx].x, pts[midIdx].y, simplifiedPoly)
            ) {
                selected.add(stroke)
            }
        }

        return selected
    }

    companion object {
        fun isPointInPolygon(px: Float, py: Float, poly: List<PointF>): Boolean {
            var inside = false
            var j = poly.size - 1
            for (i in poly.indices) {
                val pi = poly[i]
                val pj = poly[j]
                if (((pi.y > py) != (pj.y > py)) &&
                    (px < (pj.x - pi.x) * (py - pi.y) / (pj.y - pi.y) + pi.x)
                ) {
                    inside = !inside
                }
                j = i
            }
            return inside
        }
    }
}

