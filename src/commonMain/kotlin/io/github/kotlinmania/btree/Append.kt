// port-lint: source library/alloc/src/collections/btree/append.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

/**
 * Pushes all key-value pairs to the end of the tree, incrementing a
 * `length` variable along the way. The latter makes it easier for the
 * caller to avoid a leak when the iterator panicks.
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
