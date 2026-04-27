// port-lint: source library/alloc/src/collections/btree/fix.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree_kotlin

// `super::map::MIN_LEN` (upstream `map.rs` line 31) is `node::MIN_LEN_AFTER_SPLIT`.
// Map.kt is not yet ported (Phase 4); we expose the same constant here so fix.rs
// and remove.rs (Phase 1 siblings) compile against it. When map.rs lands, this
// declaration moves there and this file imports it through the flat package.
internal const val MIN_LEN: Int = MIN_LEN_AFTER_SPLIT

/**
 * Translation: upstream `fn fix_node_through_parent` returns
 * `Result<Option<NodeRef<Mut, K, V, Internal>>, Self>` — both branches carry
 * data, so per AGENTS.md we use a sealed result wrapper rather than a throw.
 */
private sealed class FixNodeThroughParentResult<K, V> {
    /** `Ok(None)` (no shrunk parent) or `Ok(Some(parent))` (returns shrunk parent). */
    data class Ok<K, V>(val parent: NodeRef<Marker.Mut, K, V, Marker.Internal>?) :
        FixNodeThroughParentResult<K, V>()

    /** `Err(self)` — the node was an empty root. */
    data class Err<K, V>(val node: NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>) :
        FixNodeThroughParentResult<K, V>()
}

/**
 * Stocks up a possibly underfull node by merging with or stealing from a
 * sibling. If successful but at the cost of shrinking the parent node,
 * returns that shrunk parent node. Returns an `Err` if the node is
 * an empty root.
 */
private fun <K, V> NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>.fixNodeThroughParent():
    FixNodeThroughParentResult<K, V> {
    val len = this.len()
    return if (len >= MIN_LEN) {
        FixNodeThroughParentResult.Ok(null)
    } else {
        when (val choice = this.chooseParentKv()) {
            is ChooseParentKvResult.Ok -> when (val side = choice.context) {
                is LeftOrRight.Left -> {
                    val leftParentKv = side.value
                    if (leftParentKv.canMerge()) {
                        val parent = leftParentKv.mergeTrackingParent()
                        FixNodeThroughParentResult.Ok(parent)
                    } else {
                        leftParentKv.bulkStealLeft(MIN_LEN - len)
                        FixNodeThroughParentResult.Ok(null)
                    }
                }
                is LeftOrRight.Right -> {
                    val rightParentKv = side.value
                    if (rightParentKv.canMerge()) {
                        val parent = rightParentKv.mergeTrackingParent()
                        FixNodeThroughParentResult.Ok(parent)
                    } else {
                        rightParentKv.bulkStealRight(MIN_LEN - len)
                        FixNodeThroughParentResult.Ok(null)
                    }
                }
            }
            is ChooseParentKvResult.Err -> {
                val root = choice.node
                if (len > 0) {
                    FixNodeThroughParentResult.Ok(null)
                } else {
                    FixNodeThroughParentResult.Err(root)
                }
            }
        }
    }
}

/**
 * Stocks up a possibly underfull node, and if that causes its parent node
 * to shrink, stocks up the parent, recursively.
 * Returns `true` if it fixed the tree, `false` if it couldn't because the
 * root node became empty.
 *
 * This method does not expect ancestors to already be underfull upon entry
 * and panics if it encounters an empty ancestor.
 */
internal fun <K, V> NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>.fixNodeAndAffectedAncestors():
    Boolean {
    var self = this
    while (true) {
        when (val r = self.fixNodeThroughParent()) {
            is FixNodeThroughParentResult.Ok -> {
                val parent = r.parent
                if (parent != null) {
                    self = parent.forgetType()
                } else {
                    return true
                }
            }
            is FixNodeThroughParentResult.Err -> return false
        }
    }
}

/** Removes empty levels on the top, but keeps an empty leaf if the entire tree is empty. */
internal fun <K, V> Root<K, V>.fixTop() {
    while (this.height() > 0 && this.len() == 0) {
        this.popInternalLevel()
    }
}

/**
 * Stocks up or merge away any underfull nodes on the right border of the
 * tree. The other nodes, those that are not the root nor a rightmost edge,
 * must already have at least MIN_LEN elements.
 */
internal fun <K, V> Root<K, V>.fixRightBorder() {
    this.fixTop()
    if (this.len() > 0) {
        this.borrowMut().lastKv().fixRightBorderOfRightEdge()
        this.fixTop()
    }
}

/** The symmetric clone of [fixRightBorder]. */
internal fun <K, V> Root<K, V>.fixLeftBorder() {
    this.fixTop()
    if (this.len() > 0) {
        this.borrowMut().firstKv().fixLeftBorderOfLeftEdge()
        this.fixTop()
    }
}

/**
 * Stocks up any underfull nodes on the right border of the tree.
 * The other nodes, those that are neither the root nor a rightmost edge,
 * must be prepared to have up to MIN_LEN elements stolen.
 */
internal fun <K, V> Root<K, V>.fixRightBorderOfPlentiful() {
    var curNode: NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal> = this.borrowMut()
    while (true) {
        val internal = when (val f = curNode.force()) {
            is ForceResult.Internal -> f.value
            is ForceResult.Leaf -> return
        }
        // Check if rightmost child is underfull.
        val lastKv = internal.lastKv().considerForBalancing()
        check(lastKv.leftChildLen() >= MIN_LEN * 2) // debug_assert!(...)
        val rightChildLen = lastKv.rightChildLen()
        if (rightChildLen < MIN_LEN) {
            // We need to steal.
            lastKv.bulkStealLeft(MIN_LEN - rightChildLen)
        }

        // Go further down.
        curNode = lastKv.intoRightChild()
    }
}

private fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>, Marker.KV>.fixLeftBorderOfLeftEdge() {
    var self = this
    while (true) {
        val internalKv = when (val f = self.force()) {
            is ForceResult.Internal -> f.value
            is ForceResult.Leaf -> return
        }
        self = internalKv.fixLeftChild().firstKv()
        check(self.reborrow().intoNode().len() > MIN_LEN) // debug_assert!(...)
    }
}

private fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>, Marker.KV>.fixRightBorderOfRightEdge() {
    var self = this
    while (true) {
        val internalKv = when (val f = self.force()) {
            is ForceResult.Internal -> f.value
            is ForceResult.Leaf -> return
        }
        self = internalKv.fixRightChild().lastKv()
        check(self.reborrow().intoNode().len() > MIN_LEN) // debug_assert!(...)
    }
}

/**
 * Stocks up the left child, assuming the right child isn't underfull, and
 * provisions an extra element to allow merging its children in turn
 * without becoming underfull.
 * Returns the left child.
 */
private fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.Internal>, Marker.KV>.fixLeftChild():
    NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal> {
    val internalKv = this.considerForBalancing()
    val leftLen = internalKv.leftChildLen()
    check(internalKv.rightChildLen() >= MIN_LEN) // debug_assert!(...)
    return if (internalKv.canMerge()) {
        internalKv.mergeTrackingChild()
    } else {
        // `MIN_LEN + 1` to avoid readjust if merge happens on the next level.
        val count = ((MIN_LEN + 1) - leftLen).coerceAtLeast(0) // saturating_sub
        if (count > 0) {
            internalKv.bulkStealRight(count)
        }
        internalKv.intoLeftChild()
    }
}

/**
 * Stocks up the right child, assuming the left child isn't underfull, and
 * provisions an extra element to allow merging its children in turn
 * without becoming underfull.
 * Returns wherever the right child ended up.
 */
private fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.Internal>, Marker.KV>.fixRightChild():
    NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal> {
    val internalKv = this.considerForBalancing()
    val rightLen = internalKv.rightChildLen()
    check(internalKv.leftChildLen() >= MIN_LEN) // debug_assert!(...)
    return if (internalKv.canMerge()) {
        internalKv.mergeTrackingChild()
    } else {
        // `MIN_LEN + 1` to avoid readjust if merge happens on the next level.
        val count = ((MIN_LEN + 1) - rightLen).coerceAtLeast(0) // saturating_sub
        if (count > 0) {
            internalKv.bulkStealLeft(count)
        }
        internalKv.intoRightChild()
    }
}
