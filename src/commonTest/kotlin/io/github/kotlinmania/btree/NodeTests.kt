// port-lint: source library/alloc/src/collections/btree/node/tests.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

import kotlin.test.Test
import kotlin.test.assertTrue

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
}
