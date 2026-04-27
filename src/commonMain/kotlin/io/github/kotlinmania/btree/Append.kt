// port-lint: source library/alloc/src/collections/btree/append.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

// Forward reference resolved by a sibling phase:
//   - `lastLeafEdge` on a `NodeRef<Marker.Mut, ..., LeafOrInternal>` lives
//     in Navigate.kt (port of navigate.rs). Until that file lands the two
//     call-sites below are unresolved; this is the documented
//     "Compile-time-incomplete files are OK" pattern from AGENTS.md.
//   - `fixRightBorderOfPlentiful` on `Root<K, V>` lives in Fix.kt (port of
//     fix.rs); already landed.
// (PORTING.md tracks this cross-file dependency.)
//
// Translation notes:
//   - The upstream `implementation<K, V> Root<K, V> { function bulkPush... }` becomes a
//     Kotlin extension function on the `Root<K, V>` typealias from Node.kt
//     (`Root<K, V> = NodeRef<Marker.Owned, K, V, Marker.LeafOrInternal>`).
//   - The `length: &mut usize` out-parameter is preserved as an `IntArray`
//     of size 1 — Kotlin can't pass a primitive by reference, and the
//     caller (Map.kt's `append`) needs the side-effect of "increment per
//     iteration so a panicking iterator doesn't leak the appended pairs".
//     A single-element IntArray is the smallest faithful translation; the
//     caller reads `length[0]` after the call.
//   - The `alloc: A` parameter dissolves: Kotlin's heap is GC-managed and
//     the `pushInternalLevel` / `Root::new` ports take no allocator.
//   - `unsafe { ... }` blocks (none in this file upstream) would dissolve
//     to `// SAFETY:` comments.

/**
 * Pushes all key-value pairs to the end of the tree, incrementing a
 * `length` variable along the way. The latter makes it easier for the
 * caller to avoid a leak when the iterator panicks.
 *
 * `length` is a single-element `IntArray` used as a mutable out-parameter
 * (see file-level translation notes for the rationale).
 */
internal fun <K, V> NodeRef<Marker.Owned, K, V, Marker.LeafOrInternal>.bulkPush(
    iter: Iterator<Pair<K, V>>,
    length: IntArray,
) {
    var curNode: NodeRef<Marker.Mut, K, V, Marker.Leaf> =
        this.borrowMut().lastLeafEdge().intoNode()
    // Iterate through all key-value pairs, pushing them into nodes at the right level.
    for ((key, value) in iter) {
        // Try to push key-value pair into the current leaf node.
        if (curNode.len() < CAPACITY) {
            curNode.push(key, value)
        } else {
            // No space left, go up and push there.
            val openNode: NodeRef<Marker.Mut, K, V, Marker.Internal>
            var testNode: NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal> =
                curNode.forgetType()
            while (true) {
                when (val ascended = testNode.ascend()) {
                    is AscendResult.Ok -> {
                        val parent: NodeRef<Marker.Mut, K, V, Marker.Internal> =
                            ascended.handle.intoNode()
                        if (parent.len() < CAPACITY) {
                            // Found a node with space left, push here.
                            openNode = parent
                            break
                        } else {
                            // Go up again.
                            testNode = parent.forgetType()
                        }
                    }
                    is AscendResult.Err -> {
                        // We are at the top, create a new root node and push there.
                        openNode = this.pushInternalLevel()
                        break
                    }
                }
            }

            // Push key-value pair and new right subtree.
            val treeHeight = openNode.height() - 1
            val rightTree: Root<K, V> = newOwnedTree()
            for (i in 0 until treeHeight) {
                rightTree.pushInternalLevel()
            }
            openNode.push(key, value, rightTree)

            // Go down to the rightmost leaf again.
            curNode = openNode.forgetType().lastLeafEdge().intoNode()
        }

        // Increment length every iteration, to make sure the map drops
        // the appended elements even if advancing the iterator panicks.
        length[0] += 1
    }
    this.fixRightBorderOfPlentiful()
}
