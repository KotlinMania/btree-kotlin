// port-lint: source node/tests.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

import kotlin.test.Test
import kotlin.test.assertTrue

internal fun <K, V> NodeRef<Marker.Immut, K, V, Marker.LeafOrInternal>.assertBackPointers() {
    val f = this.force()
    if (f is ForceResult.Internal) {
        val node = f.value
        for (idx in 0..node.len()) {
            // SAFETY: idx is in bounds [0, len].
            val edge = Handle.newEdge(node, idx)
            val child = edge.descend()
            assertTrue(child.ascend() is AscendResult.Ok)
            assertTrue((child.ascend() as AscendResult.Ok).handle == edge)
            child.assertBackPointers()
        }
    }
}

internal fun <K, V> NodeRef<Marker.Immut, K, V, Marker.LeafOrInternal>.dumpKeys(): String {
    val result = StringBuilder()
    this.visitNodesInOrder { pos ->
        when (pos) {
            is Position.Leaf -> {
                val depth = this.height
                val indent = "  ".repeat(depth)
                // Kotlin arrays/lists of keys can be dumped easily, mimicking Rust's Debug output
                result.append("\n").append(indent).append(pos.node.keys().toList().toString())
            }
            is Position.Internal -> {}
            is Position.InternalKV -> {}
        }
    }
    return result.toString()
}

class NodeTests {
    @Test
    fun testSplitpoint() {
        for (idx in 0..CAPACITY) {
            val (middleKvIdx, insertion) = splitpoint(idx)

            // Simulate performing the split:
            var leftLen = middleKvIdx
            var rightLen = CAPACITY - middleKvIdx - 1
            when (insertion) {
                is LeftOrRight.Left -> {
                    val edgeIdx = insertion.value
                    assertTrue(edgeIdx <= leftLen)
                    leftLen += 1
                }
                is LeftOrRight.Right -> {
                    val edgeIdx = insertion.value
                    assertTrue(edgeIdx <= rightLen)
                    rightLen += 1
                }
            }
            assertTrue(leftLen >= MIN_LEN_AFTER_SPLIT)
            assertTrue(rightLen >= MIN_LEN_AFTER_SPLIT)
            assertTrue(leftLen + rightLen == CAPACITY)
        }
    }

    @Test
    fun testPartialEq() {
        var root1 = NodeRef.newLeaf<Int, Unit>()
        root1.borrowMut().push(1, Unit)
        val internalNode = NodeRef.newInternal<Int, Unit>(root1.forgetType())
        var root1Final = internalNode.forgetType()
        val root2 = newOwnedTree<Int, Unit>()
        root1Final.reborrow().assertBackPointers()
        root2.reborrow().assertBackPointers()

        val leafEdge1a = root1Final.reborrow().firstLeafEdge().forgetNodeTypeLeafEdge()
        val leafEdge1b = root1Final.reborrow().lastLeafEdge().forgetNodeTypeLeafEdge()
        val topEdge1 = root1Final.reborrow().firstEdge()
        val topEdge2 = root2.reborrow().firstEdge()

        assertTrue(leafEdge1a == leafEdge1a)
        assertTrue(leafEdge1a != leafEdge1b)
        assertTrue(leafEdge1a != topEdge1)
        assertTrue(leafEdge1a != topEdge2)
        assertTrue(topEdge1 == topEdge1)
        assertTrue(topEdge1 != topEdge2)

        root1Final.popInternalLevel()
        root1Final.intoDying().deallocateAndAscend()
        root2.intoDying().deallocateAndAscend()
    }
}
