// port-lint: source library/alloc/src/collections/btree/split.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree_kotlin

// Forward reference resolved by a sibling phase:
//   - `calcLength()` on a `NodeRef<Marker.Immut, ..., LeafOrInternal>` lives
//     in Navigate.kt (port of navigate.rs). Until that file lands the four
//     call-sites in `calcSplitLength` below are unresolved; this is the
//     documented "Compile-time-incomplete files are OK" pattern from
//     AGENTS.md. `fixRightBorder` / `fixLeftBorder` (also `pub(super)` on
//     `Root<K, V>` upstream) already live in Fix.kt and resolve cleanly.
// (PORTING.md tracks this cross-file dependency.)

/**
 * Calculates the length of both trees that result from splitting up
 * a given number of distinct key-value pairs.
 */
internal fun <K, V> calcSplitLength(
    totalNum: Int,
    rootA: Root<K, V>,
    rootB: Root<K, V>,
): Pair<Int, Int> {
    val lengthA: Int
    val lengthB: Int
    if (rootA.height() < rootB.height()) {
        lengthA = rootA.reborrow().calcLength()
        lengthB = totalNum - lengthA
        check(lengthB == rootB.reborrow().calcLength()) // debug_assert_eq!
    } else {
        lengthB = rootB.reborrow().calcLength()
        lengthA = totalNum - lengthB
        check(lengthA == rootA.reborrow().calcLength()) // debug_assert_eq!
    }
    return Pair(lengthA, lengthB)
}

/**
 * Split off a tree with key-value pairs at and after the given key.
 * The result is meaningful only if the tree is ordered by key,
 * and if the ordering of `Q` corresponds to that of `K`.
 * If `self` respects all `BTreeMap` tree invariants, then both
 * `self` and the returned tree will respect those invariants.
 */
internal fun <K, V, Q : Comparable<Q>> NodeRef<Marker.Owned, K, V, Marker.LeafOrInternal>.splitOff(
    key: Q,
): Root<K, V>
    where K : Comparable<Q> {
    val leftRoot = this
    val rightRoot = newPillar<K, V>(leftRoot.height())
    var leftNode = leftRoot.borrowMut()
    var rightNode = rightRoot.borrowMut()

    while (true) {
        val splitEdge = when (val r = leftNode.searchNode(key)) {
            // key is going to the right tree
            is SearchResult.Found -> r.handle.leftEdge()
            is SearchResult.GoDown -> r.handle
        }

        splitEdge.moveSuffix(rightNode)

        val lf = splitEdge.force()
        val rf = rightNode.force()
        when (lf) {
            is ForceResult.Internal -> when (rf) {
                is ForceResult.Internal -> {
                    leftNode = lf.value.descend()
                    rightNode = rf.value.firstEdge().descend()
                }
                is ForceResult.Leaf -> error("unreachable")
            }
            is ForceResult.Leaf -> when (rf) {
                is ForceResult.Leaf -> break
                is ForceResult.Internal -> error("unreachable")
            }
        }
    }

    leftRoot.fixRightBorder()
    rightRoot.fixLeftBorder()
    return rightRoot
}

/** Creates a tree consisting of empty nodes. */
private fun <K, V> newPillar(height: Int): Root<K, V> {
    val root = newOwnedTree<K, V>()
    for (i in 0 until height) {
        root.pushInternalLevel()
    }
    return root
}
