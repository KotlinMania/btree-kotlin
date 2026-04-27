// port-lint: source library/alloc/src/collections/btree/remove.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

// Phase-2 dependencies satisfied by Node.kt: NodeRef, Handle, ForceResult,
// Marker, LeftOrRight, ChooseParentKvResult, EdgeKvResult, AscendResult,
// BalancingContext, plus the leaf-KV `remove()`, `chooseParentKv()`,
// `mergeTrackingChildEdge()`, `stealLeft()`, `stealRight()`,
// `castToLeafUnchecked()`, `replaceKv()`, `forgetType()`, and the Handle
// `force()` / `leftEdge()`.
//
// Sibling-phase dependencies satisfied:
// - Fix.kt: `MIN_LEN` constant (re-exported from
//   `MIN_LEN`, mirroring upstream `super::map::MIN_LEN`),
//   `NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>.fixNodeAndAffectedAncestors(): Boolean`
//   (returns `true` if the tree was fixed, `false` if the root became empty).
//
// Forward references not yet provided by sibling files:
// - Navigate.kt: `Handle<...Leaf, Edge>.lastLeafEdge()`,
//   `Handle<...Leaf, Edge>.nextKv()` (returns Ok(LeafOrInternal-KV) /
//   Err(LeafOrInternal-NodeRef), shape parallels [EdgeKvResult]),
//   `Handle<...LeafOrInternal, KV>.nextLeafEdge()`,
//   `Handle<...Internal, Edge>.descend()` is in Node.kt. Upstream:
//   library/alloc/src/collections/btree/navigate.rs.
//
// Until Navigate.kt lands, this file will not compile in isolation; that
// is permitted per AGENTS.md "compile-time-incomplete files are OK in
// early phases".
//
// Allocator parameter: upstream `A: Allocator + Clone` is dropped — Kotlin's
// GC supersedes manual deallocation, matching the convention already
// established in Node.kt (e.g. `mergeTrackingChildEdge` has no `alloc`).

/**
 * Removes a key-value pair from the tree, and returns that pair, as well as
 * the leaf edge corresponding to that former pair. It's possible this empties
 * a root node that is internal, which the caller should pop from the map
 * holding the tree. The caller should also decrement the map's length.
 */
internal fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>, Marker.KV>.removeKvTracking(
    handleEmptiedInternalRoot: () -> Unit,
): Pair<Pair<K, V>, Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge>> {
    return when (val f = this.force()) {
        is ForceResult.Leaf -> f.value.removeLeafKv(handleEmptiedInternalRoot)
        is ForceResult.Internal -> f.value.removeInternalKv(handleEmptiedInternalRoot)
    }
}

private fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.KV>.removeLeafKv(
    handleEmptiedInternalRoot: () -> Unit,
): Pair<Pair<K, V>, Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge>> {
    val (oldKv, posInit) = this.remove()
    var pos: Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge> = posInit
    val len = pos.node.len()
    if (len < MIN_LEN) {
        val idx = pos.idx()
        // We have to temporarily forget the child type, because there is no
        // distinct node type for the immediate parents of a leaf.
        val newPos: Handle<NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>, Marker.Edge> =
            when (val choose = pos.intoNode().forgetType().chooseParentKv()) {
                is ChooseParentKvResult.Ok -> when (val side = choose.context) {
                    is LeftOrRight.Left -> {
                        val leftParentKv = side.value
                        check(leftParentKv.rightChildLen() == MIN_LEN - 1)
                        if (leftParentKv.canMerge()) {
                            leftParentKv.mergeTrackingChildEdge(LeftOrRight.Right(idx))
                        } else {
                            check(leftParentKv.leftChildLen() > MIN_LEN)
                            leftParentKv.stealLeft(idx)
                        }
                    }
                    is LeftOrRight.Right -> {
                        val rightParentKv = side.value
                        check(rightParentKv.leftChildLen() == MIN_LEN - 1)
                        if (rightParentKv.canMerge()) {
                            rightParentKv.mergeTrackingChildEdge(LeftOrRight.Left(idx))
                        } else {
                            check(rightParentKv.rightChildLen() > MIN_LEN)
                            rightParentKv.stealRight(idx)
                        }
                    }
                }
                // SAFETY: the recovered `pos` NodeRef is the same node we
                // started from; its length is `len` and `idx` was a valid
                // edge index there, so `idx <= len` still holds.
                is ChooseParentKvResult.Err -> Handle.newEdge(choose.node, idx)
            }
        // SAFETY: `newPos` is the leaf we started from or a sibling.
        pos = newPos.castToLeafUnchecked()

        // Only if we merged, the parent (if any) has shrunk, but skipping
        // the following step otherwise does not pay off in benchmarks.
        //
        // SAFETY: We won't destroy or rearrange the leaf where `pos` is at
        // by handling its parent recursively; at worst we will destroy or
        // rearrange the parent through the grandparent, thus change the
        // link to the parent inside the leaf.
        when (val ascended = pos.node.ascend()) {
            is AscendResult.Ok -> {
                val parent = ascended.handle
                if (!parent.intoNode().forgetType().fixNodeAndAffectedAncestors()) {
                    handleEmptiedInternalRoot()
                }
            }
            is AscendResult.Err -> Unit
        }
    }
    return Pair(oldKv, pos)
}

private fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.Internal>, Marker.KV>.removeInternalKv(
    handleEmptiedInternalRoot: () -> Unit,
): Pair<Pair<K, V>, Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge>> {
    // Remove an adjacent KV from its leaf and then put it back in place of
    // the element we were asked to remove. Prefer the left adjacent KV,
    // for the reasons listed in `chooseParentKv`.
    val leftLeafEdgeKv = this.leftEdge().descend().lastLeafEdge().leftKv()
    // SAFETY: the left edge of an internal KV descends into a non-empty
    // subtree, so `lastLeafEdge` lands at a leaf edge with at least one KV
    // to its left — `leftKv` is therefore always `Ok` here.
    val leftLeafKv = (leftLeafEdgeKv as EdgeKvResult.Ok).handle
    val (leftKv, leftHole) = leftLeafKv.removeLeafKv(handleEmptiedInternalRoot)

    // The internal node may have been stolen from or merged. Go back right
    // to find where the original KV ended up.
    // SAFETY: we removed a KV strictly to the left of the original internal
    // KV, so a KV to the right of `leftHole` always exists.
    val internal = (leftHole.nextKv() as NextKvResult.Ok).handle
    val oldKv = internal.replaceKv(leftKv.first, leftKv.second)
    val pos = internal.nextLeafEdge()
    return Pair(oldKv, pos)
}
