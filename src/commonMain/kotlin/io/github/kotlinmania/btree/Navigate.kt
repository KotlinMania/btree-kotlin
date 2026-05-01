// port-lint: source navigate.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

// `front` and `back` are always both `None` or both `Some`.
internal class LeafRange<BorrowType, K, V>(
    var front: Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>?,
    var back: Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>?,
) {
    companion object {
        internal fun <BorrowType, K, V> default(): LeafRange<BorrowType, K, V> =
            LeafRange(front = null, back = null)

        internal fun <BorrowType, K, V> none(): LeafRange<BorrowType, K, V> =
            LeafRange(front = null, back = null)
    }

    internal fun clone(): LeafRange<BorrowType, K, V> {
        return LeafRange(front, back)
    }

    private fun isEmpty(): Boolean {
        val f = front
        val b = back
        return when {
            f == null && b == null -> true
            f == null || b == null -> false
            else -> f.eq(b)
        }
    }

    /** Temporarily takes out another, immutable equivalent of the same range. */
    internal fun reborrow(): LeafRange<Marker.Immut, K, V> {
        return LeafRange(
            front = front?.reborrow(),
            back = back?.reborrow(),
        )
    }
}

private fun <BorrowType : Marker.BorrowType, K, V, NodeType>
    Handle<NodeRef<BorrowType, K, V, NodeType>, Marker.KV>.intoKvPair(): Pair<K, V> {
    check(idx < node.len())
    val leaf = node.node
    val k = leaf.keys[idx].initializedValue
    val v = leaf.vals[idx].initializedValue
    return Pair(k, v)
}

internal fun <BorrowType : Marker.BorrowType, K, V> LeafRange<BorrowType, K, V>.nextChecked(): Pair<K, V>? {
    return performNextChecked { kv -> kv.intoKvPair() }
}

internal fun <BorrowType : Marker.BorrowType, K, V> LeafRange<BorrowType, K, V>.nextBackChecked(): Pair<K, V>? {
    return performNextBackChecked { kv -> kv.intoKvPair() }
}

/**
 * If possible, extract some result from the following KV and move to the edge beyond it.
 */
private fun <BorrowType : Marker.BorrowType, K, V, R> LeafRange<BorrowType, K, V>.performNextChecked(
    f: (Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.KV>) -> R,
): R? {
    if (isEmptyInternal()) return null
    val frontVal = front!!
    val (newFront, result) = replace(frontVal) { fr ->
        val kv = when (val r = fr.nextKv()) {
            is NextKvResult.Ok -> r.handle
            is NextKvResult.Err -> error("unreachable: isEmpty() short-circuits empty ranges")
        }
        val ret = f(kv)
        Pair(kv.nextLeafEdge(), ret)
    }
    front = newFront
    return result
}

/**
 * If possible, extract some result from the preceding KV and move to the edge beyond it.
 */
private fun <BorrowType : Marker.BorrowType, K, V, R> LeafRange<BorrowType, K, V>.performNextBackChecked(
    f: (Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.KV>) -> R,
): R? {
    if (isEmptyInternal()) return null
    val backVal = back!!
    val (newBack, result) = replace(backVal) { bk ->
        val kv = when (val r = bk.nextBackKv()) {
            is NextKvResult.Ok -> r.handle
            is NextKvResult.Err -> error("unreachable: isEmpty() short-circuits empty ranges")
        }
        val ret = f(kv)
        Pair(kv.nextBackLeafEdge(), ret)
    }
    back = newBack
    return result
}

private fun <BorrowType, K, V> LeafRange<BorrowType, K, V>.isEmptyInternal(): Boolean {
    val f = front
    val b = back
    return when {
        f == null && b == null -> true
        f == null || b == null -> false
        else -> f.eq(b)
    }
}

internal sealed class LazyLeafHandle<BorrowType, K, V> {
    /** Not yet descended. */
    data class Root<BorrowType, K, V>(
        val node: NodeRef<BorrowType, K, V, Marker.LeafOrInternal>,
    ) : LazyLeafHandle<BorrowType, K, V>()

    data class Edge<BorrowType, K, V>(
        val edge: Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>,
    ) : LazyLeafHandle<BorrowType, K, V>()

    internal fun clone(): LazyLeafHandle<BorrowType, K, V> {
        return when (this) {
            is Root -> Root(node)
            is Edge -> Edge(edge)
        }
    }
}

internal fun <BorrowType, K, V> LazyLeafHandle<BorrowType, K, V>.reborrow():
    LazyLeafHandle<Marker.Immut, K, V> = when (this) {
    is LazyLeafHandle.Root -> LazyLeafHandle.Root(node.reborrow())
    is LazyLeafHandle.Edge -> LazyLeafHandle.Edge(edge.reborrow())
}

// `front` and `back` are always both `None` or both `Some`.
internal class LazyLeafRange<BorrowType, K, V>(
    var front: LazyLeafHandle<BorrowType, K, V>?,
    var back: LazyLeafHandle<BorrowType, K, V>?,
) {
    companion object {
        internal fun <BorrowType, K, V> default(): LazyLeafRange<BorrowType, K, V> =
            LazyLeafRange(front = null, back = null)

        internal fun <BorrowType, K, V> none(): LazyLeafRange<BorrowType, K, V> =
            LazyLeafRange(front = null, back = null)
    }

    internal fun clone(): LazyLeafRange<BorrowType, K, V> {
        return LazyLeafRange(front?.clone(), back?.clone())
    }

    /** Temporarily takes out another, immutable equivalent of the same range. */
    internal fun reborrow(): LazyLeafRange<Marker.Immut, K, V> {
        return LazyLeafRange(
            front = front?.reborrow(),
            back = back?.reborrow(),
        )
    }
}

internal fun <BorrowType : Marker.BorrowType, K, V> LazyLeafRange<BorrowType, K, V>.nextUnchecked(): Pair<K, V> {
    val edge = initFront()!!
    val (newEdge, kv) = edge.nextUnchecked()
    front = LazyLeafHandle.Edge(newEdge)
    return kv
}

internal fun <BorrowType : Marker.BorrowType, K, V> LazyLeafRange<BorrowType, K, V>.nextBackUnchecked(): Pair<K, V> {
    val edge = initBack()!!
    val (newEdge, kv) = edge.nextBackUnchecked()
    back = LazyLeafHandle.Edge(newEdge)
    return kv
}

internal fun <K, V> LazyLeafRange<Marker.Dying, K, V>.takeFront():
    Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>? {
    val taken = front ?: return null
    front = null
    return when (taken) {
        is LazyLeafHandle.Root -> taken.node.firstLeafEdge()
        is LazyLeafHandle.Edge -> taken.edge
    }
}

internal fun <K, V> LazyLeafRange<Marker.Dying, K, V>.deallocatingNextUnchecked():
    Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.KV> {
    check(front != null)
    val edge = initFront()!!
    val (newEdge, kv) = edge.deallocatingNextUnchecked()
    front = LazyLeafHandle.Edge(newEdge)
    return kv
}

internal fun <K, V> LazyLeafRange<Marker.Dying, K, V>.deallocatingNextBackUnchecked():
    Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.KV> {
    check(back != null)
    val edge = initBack()!!
    val (newEdge, kv) = edge.deallocatingNextBackUnchecked()
    back = LazyLeafHandle.Edge(newEdge)
    return kv
}

internal fun <K, V> LazyLeafRange<Marker.Dying, K, V>.deallocatingEnd() {
    val front = takeFront()
    front?.deallocatingEnd()
}

internal fun <BorrowType : Marker.BorrowType, K, V> LazyLeafRange<BorrowType, K, V>.initFront():
    Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>? {
    val current = front
    if (current is LazyLeafHandle.Root) {
        front = LazyLeafHandle.Edge(current.node.firstLeafEdge())
    }
    return when (val f = front) {
        null -> null
        is LazyLeafHandle.Edge -> f.edge
        is LazyLeafHandle.Root -> error("unreachable: Root case was rewritten above")
    }
}

internal fun <BorrowType : Marker.BorrowType, K, V> LazyLeafRange<BorrowType, K, V>.initBack():
    Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>? {
    val current = back
    if (current is LazyLeafHandle.Root) {
        back = LazyLeafHandle.Edge(current.node.lastLeafEdge())
    }
    return when (val b = back) {
        null -> null
        is LazyLeafHandle.Edge -> b.edge
        is LazyLeafHandle.Root -> error("unreachable: Root case was rewritten above")
    }
}

/**
 * Finds the distinct leaf edges delimiting a specified range in a tree.
 *
 * If such distinct edges exist, returns them in ascending order, meaning
 * that a non-zero number of calls to `nextUnchecked` on the `front` of
 * the result and/or calls to `nextBackUnchecked` on the `back` of the
 * result will eventually reach the same edge.
 *
 * If there are no such edges, i.e., if the tree contains no key within
 * the range, returns an empty `front` and `back`.
 *
 * Safety:
 * Unless `BorrowType` is `Immut`, do not use the handles to visit the same
 * KV twice.
 */
internal inline fun <BorrowType : Marker.BorrowType, K, reified V, Q : Comparable<Q>, R : RangeBounds<Q>>
    NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.findLeafEdgesSpanningRange(
    range: R,
): LeafRange<BorrowType, K, V> where K : Comparable<Q> = findLeafEdgesSpanningRangeExplicit(range, isSetVal<V>())

internal fun <BorrowType : Marker.BorrowType, K, V, Q : Comparable<Q>, R : RangeBounds<Q>>
    NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.findLeafEdgesSpanningRangeExplicit(
    range: R,
    isSet: Boolean,
): LeafRange<BorrowType, K, V> where K : Comparable<Q> {
    when (val r = this.searchTreeForBifurcationExplicit<BorrowType, K, V, Q, R>(range, isSet)) {
        is BifurcationResult.LeafEdge -> return LeafRange.none()
        is BifurcationResult.Ok -> {
            val bif = r.value
            var lowerEdge: Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.Edge> =
                Handle.newEdge(bif.node, bif.lowerEdgeIdx)
            var upperEdge: Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.Edge> =
                Handle.newEdge(bif.node, bif.upperEdgeIdx)
            var lowerChildBound = bif.lowerChildBound
            var upperChildBound = bif.upperChildBound
            while (true) {
                val lowerForced = lowerEdge.force()
                val upperForced = upperEdge.force()
                if (lowerForced is ForceResult.Leaf && upperForced is ForceResult.Leaf) {
                    return LeafRange(front = lowerForced.value, back = upperForced.value)
                } else if (lowerForced is ForceResult.Internal && upperForced is ForceResult.Internal) {
                    val (newLowerEdge, newLowerBound) =
                        lowerForced.value.descend().findLowerBoundEdge(lowerChildBound)
                    val (newUpperEdge, newUpperBound) =
                        upperForced.value.descend().findUpperBoundEdge(upperChildBound)
                    lowerEdge = newLowerEdge
                    upperEdge = newUpperEdge
                    lowerChildBound = newLowerBound
                    upperChildBound = newUpperBound
                } else {
                    error("BTreeMap has different depths")
                }
            }
        }
    }
    error("unreachable: while(true) above always returns or throws")
}

internal fun <BorrowType : Marker.BorrowType, K, V> fullRange(
    root1: NodeRef<BorrowType, K, V, Marker.LeafOrInternal>,
    root2: NodeRef<BorrowType, K, V, Marker.LeafOrInternal>,
): LazyLeafRange<BorrowType, K, V> {
    return LazyLeafRange(
        front = LazyLeafHandle.Root(root1),
        back = LazyLeafHandle.Root(root2),
    )
}

/**
 * Finds the pair of leaf edges delimiting a specific range in a tree.
 *
 * The result is meaningful only if the tree is ordered by key, like the tree
 * in a [BTreeMap] is.
 */
internal inline fun <BorrowType : Marker.BorrowType, K, reified V, Q : Comparable<Q>, R : RangeBounds<Q>>
    NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.rangeSearch(
    range: R,
): LeafRange<BorrowType, K, V> where K : Comparable<Q> {
    return this.findLeafEdgesSpanningRange<BorrowType, K, V, Q, R>(range)
}

internal fun <BorrowType : Marker.BorrowType, K, V, Q : Comparable<Q>, R : RangeBounds<Q>>
    NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.rangeSearch(
    range: R,
    isSet: Boolean,
): LeafRange<BorrowType, K, V> where K : Comparable<Q> {
    return this.findLeafEdgesSpanningRangeExplicit<BorrowType, K, V, Q, R>(range, isSet)
}

/** Finds the pair of leaf edges delimiting an entire tree. */
internal fun <BorrowType : Marker.BorrowType, K, V> NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.fullRange():
    LazyLeafRange<BorrowType, K, V> {
    val self2 = NodeRef<BorrowType, K, V, Marker.LeafOrInternal>(height = height, node = node)
    return fullRange(this, self2)
}

/** Result of [nextKv] / [nextBackKv]: either the neighbour KV (`Ok`) or the root node (`Err`). */
internal sealed class NextKvResult<BorrowType, K, V> {
    data class Ok<BorrowType, K, V>(
        val handle: Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.KV>,
    ) : NextKvResult<BorrowType, K, V>()

    data class Err<BorrowType, K, V>(
        val node: NodeRef<BorrowType, K, V, Marker.LeafOrInternal>,
    ) : NextKvResult<BorrowType, K, V>()
}

/**
 * Given a leaf edge handle, returns [NextKvResult.Ok] with a handle to the
 * neighboring KV on the right side, which is either in the same leaf node
 * or in an ancestor node. If the leaf edge is the last one in the tree,
 * returns [NextKvResult.Err] with the root node.
 */
internal fun <BorrowType : Marker.BorrowType, K, V>
    Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>.nextKv():
    NextKvResult<BorrowType, K, V> {
    var edge: Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.Edge> =
        this.forgetNodeType(LeafEdgeForgetNodeType)
    while (true) {
        edge = when (val rk = edge.rightKv()) {
            is EdgeKvResult.Ok -> return NextKvResult.Ok(rk.handle)
            is EdgeKvResult.Err -> when (val asc = rk.handle.intoNode().ascend()) {
                is AscendResult.Ok -> asc.handle.forgetNodeType(InternalEdgeForgetNodeType)
                is AscendResult.Err -> return NextKvResult.Err(asc.node)
            }
        }
    }
}

/** Mirror of [nextKv] for the left side. */
internal fun <BorrowType : Marker.BorrowType, K, V>
    Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>.nextBackKv():
    NextKvResult<BorrowType, K, V> {
    var edge: Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.Edge> =
        this.forgetNodeType(LeafEdgeForgetNodeType)
    while (true) {
        edge = when (val lk = edge.leftKv()) {
            is EdgeKvResult.Ok -> return NextKvResult.Ok(lk.handle)
            is EdgeKvResult.Err -> when (val asc = lk.handle.intoNode().ascend()) {
                is AscendResult.Ok -> asc.handle.forgetNodeType(InternalEdgeForgetNodeType)
                is AscendResult.Err -> return NextKvResult.Err(asc.node)
            }
        }
    }
}

/** Result of [nextKvInternal]: either the neighbour KV (`Ok`) or the internal node (`Err`). */
internal sealed class NextKvInternalResult<BorrowType, K, V> {
    data class Ok<BorrowType, K, V>(
        val kv: Handle<NodeRef<BorrowType, K, V, Marker.Internal>, Marker.KV>,
    ) : NextKvInternalResult<BorrowType, K, V>()

    data class Err<BorrowType, K, V>(
        val node: NodeRef<BorrowType, K, V, Marker.Internal>,
    ) : NextKvInternalResult<BorrowType, K, V>()
}

/**
 * Given an internal edge handle, returns Ok with a handle to the neighboring KV
 * on the right side, which is either in the same internal node or in an ancestor node.
 */
internal fun <BorrowType : Marker.BorrowType, K, V>
    Handle<NodeRef<BorrowType, K, V, Marker.Internal>, Marker.Edge>.nextKvInternal():
    NextKvInternalResult<BorrowType, K, V> {
    var edge: Handle<NodeRef<BorrowType, K, V, Marker.Internal>, Marker.Edge> = this
    while (true) {
        edge = when (val rk = edge.rightKv()) {
            is EdgeKvResult.Ok -> return NextKvInternalResult.Ok(rk.handle)
            is EdgeKvResult.Err -> when (val asc = rk.handle.intoNode().ascend()) {
                is AscendResult.Ok -> asc.handle
                is AscendResult.Err -> {
                    // Receiver is `Internal`, so the recovered root is
                    // `Internal` at runtime — narrow back via direct
                    // construction with the known type tag.
                    val recovered = asc.node
                    return NextKvInternalResult.Err(
                        NodeRef(height = recovered.height, node = recovered.node),
                    )
                }
            }
        }
    }
}

/**
 * Given a leaf edge handle into a dying tree, returns the next leaf edge
 * on the right side, and the key-value pair in between, if they exist.
 *
 * If the given edge is the last one in a leaf, this method deallocates
 * the leaf, as well as any ancestor nodes whose last edge was reached.
 * This implies that if no more key-value pair follows, the entire tree
 * will have been deallocated and there is nothing left to return.
 *
 * Safety:
 * - The given edge must not have been previously returned by counterpart
 *   `deallocatingNextBack`.
 * - The returned KV handle is only valid to access the key and value,
 *   and only valid until the next call to a deallocating method.
 */
internal fun <K, V>
    Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>.deallocatingNext():
    Pair<
        Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>,
        Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.KV>,
        >? {
    var edge: Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.Edge> =
        this.forgetNodeType(LeafEdgeForgetNodeType)
    while (true) {
        edge = when (val rk = edge.rightKv()) {
            is EdgeKvResult.Ok -> {
                val kv = rk.handle
                return Pair(kv.nextLeafEdge(), kv)
            }
            is EdgeKvResult.Err -> {
                val node = rk.handle.intoNode()
                val parent = node.deallocateAndAscend()
                if (parent != null) {
                    parent.forgetNodeType(InternalEdgeForgetNodeType)
                } else {
                    return null
                }
            }
        }
    }
}

/** Mirror of [deallocatingNext] for the left side. */
internal fun <K, V>
    Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>.deallocatingNextBack():
    Pair<
        Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>,
        Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.KV>,
        >? {
    var edge: Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.Edge> =
        this.forgetNodeType(LeafEdgeForgetNodeType)
    while (true) {
        edge = when (val lk = edge.leftKv()) {
            is EdgeKvResult.Ok -> {
                val kv = lk.handle
                return Pair(kv.nextBackLeafEdge(), kv)
            }
            is EdgeKvResult.Err -> {
                val node = lk.handle.intoNode()
                val parent = node.deallocateAndAscend()
                if (parent != null) {
                    parent.forgetNodeType(InternalEdgeForgetNodeType)
                } else {
                    return null
                }
            }
        }
    }
}

/**
 * Deallocates a pile of nodes from the leaf up to the root.
 * This is the only way to deallocate the remainder of a tree after
 * `deallocatingNext` and `deallocatingNextBack` have been nibbling at
 * both sides of the tree, and have hit the same edge. As it is intended
 * only to be called when all keys and values have been returned,
 * no cleanup is done on any of the keys or values.
 */
internal fun <K, V>
    Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>.deallocatingEnd() {
    var edge: Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.Edge> =
        this.forgetNodeType(LeafEdgeForgetNodeType)
    while (true) {
        val parent = edge.intoNode().deallocateAndAscend() ?: return
        edge = parent.forgetNodeType(InternalEdgeForgetNodeType)
    }
}

/**
 * Returns the next leaf edge handle and the key and value in between.
 *
 * Safety:
 * There must be another KV in the direction travelled.
 */
internal fun <BorrowType : Marker.BorrowType, K, V>
    Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>.nextUnchecked():
    Pair<Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>, Pair<K, V>> {
    val (newEdge, kvRef) = replace(this) { leafEdge ->
        val kv = when (val r = leafEdge.nextKv()) {
            is NextKvResult.Ok -> r.handle
            is NextKvResult.Err -> error("unreachable: caller-asserted there is another KV")
        }
        Pair(kv.nextLeafEdge(), kv)
    }
    return Pair(newEdge, kvRef.intoKvPair())
}

/**
 * Returns the previous leaf edge handle and the key and value in between.
 *
 * Safety:
 * There must be another KV in the direction travelled.
 */
internal fun <BorrowType : Marker.BorrowType, K, V>
    Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>.nextBackUnchecked():
    Pair<Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>, Pair<K, V>> {
    val (newEdge, kvRef) = replace(this) { leafEdge ->
        val kv = when (val r = leafEdge.nextBackKv()) {
            is NextKvResult.Ok -> r.handle
            is NextKvResult.Err -> error("unreachable: caller-asserted there is another KV")
        }
        Pair(kv.nextBackLeafEdge(), kv)
    }
    return Pair(newEdge, kvRef.intoKvPair())
}

/**
 * Moves the leaf edge handle to the next leaf edge and returns the key and value
 * in between, deallocating any node left behind while leaving the corresponding
 * edge in its parent node dangling.
 *
 * Safety:
 * - There must be another KV in the direction travelled.
 * - That KV was not previously returned by counterpart
 *   `deallocatingNextBackUnchecked` on any copy of the handles
 *   being used to traverse the tree.
 *
 * The only safe way to proceed with the updated handle is to compare it, drop it,
 * or call this method or counterpart `deallocatingNextBackUnchecked` again.
 */
internal fun <K, V>
    Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>.deallocatingNextUnchecked():
    Pair<
        Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>,
        Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.KV>,
        > {
    return replace(this) { leafEdge ->
        leafEdge.deallocatingNext() ?: error("unreachable: caller-asserted KV present")
    }
}

/**
 * Moves the leaf edge handle to the previous leaf edge and returns the key and value
 * in between, deallocating any node left behind while leaving the corresponding
 * edge in its parent node dangling.
 *
 * Safety:
 * - There must be another KV in the direction travelled.
 * - That leaf edge was not previously returned by counterpart
 *   `deallocatingNextUnchecked` on any copy of the handles
 *   being used to traverse the tree.
 *
 * The only safe way to proceed with the updated handle is to compare it, drop it,
 * or call this method or counterpart `deallocatingNextUnchecked` again.
 */
internal fun <K, V>
    Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>.deallocatingNextBackUnchecked():
    Pair<
        Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>,
        Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.KV>,
        > {
    return replace(this) { leafEdge ->
        leafEdge.deallocatingNextBack() ?: error("unreachable: caller-asserted KV present")
    }
}

/**
 * Returns the leftmost leaf edge in or underneath a node - in other words, the edge
 * you need first when navigating forward (or last when navigating backward).
 */
internal fun <BorrowType : Marker.BorrowType, K, V>
    NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.firstLeafEdge():
    Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge> {
    var node = this
    while (true) {
        node = when (val f = node.force()) {
            is ForceResult.Leaf -> return f.value.firstEdge()
            is ForceResult.Internal -> f.value.firstEdge().descend()
        }
    }
}

/**
 * Returns the rightmost leaf edge in or underneath a node - in other words, the edge
 * you need last when navigating forward (or first when navigating backward).
 */
internal fun <BorrowType : Marker.BorrowType, K, V>
    NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.lastLeafEdge():
    Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge> {
    var node = this
    while (true) {
        node = when (val f = node.force()) {
            is ForceResult.Leaf -> return f.value.lastEdge()
            is ForceResult.Internal -> f.value.lastEdge().descend()
        }
    }
}

internal sealed class Position<BorrowType, K, V> {
    data class Leaf<BorrowType, K, V>(
        val node: NodeRef<BorrowType, K, V, Marker.Leaf>,
    ) : Position<BorrowType, K, V>()

    data class Internal<BorrowType, K, V>(
        val node: NodeRef<BorrowType, K, V, Marker.Internal>,
    ) : Position<BorrowType, K, V>()

    class InternalKV<BorrowType, K, V> : Position<BorrowType, K, V>() {
        override fun toString(): String = "InternalKV"
    }
}

/**
 * Visits leaf nodes and internal KVs in order of ascending keys, and also
 * visits internal nodes as a whole in a depth first order, meaning that
 * internal nodes precede their individual KVs and their child nodes.
 */
internal inline fun <K, V> NodeRef<Marker.Immut, K, V, Marker.LeafOrInternal>.visitNodesInOrder(
    visit: (Position<Marker.Immut, K, V>) -> Unit,
) {
    when (val f = this.force()) {
        is ForceResult.Leaf -> visit(Position.Leaf(f.value))
        is ForceResult.Internal -> {
            val internal = f.value
            visit(Position.Internal(internal))
            var edge: Handle<NodeRef<Marker.Immut, K, V, Marker.Internal>, Marker.Edge> =
                internal.firstEdge()
            while (true) {
                edge = when (val descForced = edge.descend().force()) {
                    is ForceResult.Leaf -> {
                        val leaf = descForced.value
                        visit(Position.Leaf(leaf))
                        when (val nk = edge.nextKvInternal()) {
                            is NextKvInternalResult.Ok -> {
                                visit(Position.InternalKV())
                                nk.kv.rightEdge()
                            }
                            is NextKvInternalResult.Err -> return
                        }
                    }
                    is ForceResult.Internal -> {
                        val sub = descForced.value
                        visit(Position.Internal(sub))
                        sub.firstEdge()
                    }
                }
            }
        }
    }
}

/** Calculates the number of elements in a (sub)tree. */
internal fun <K, V> NodeRef<Marker.Immut, K, V, Marker.LeafOrInternal>.calcLength(): Int {
    var result = 0
    this.visitNodesInOrder { pos ->
        when (pos) {
            is Position.Leaf -> result += pos.node.len()
            is Position.Internal -> result += pos.node.len()
            is Position.InternalKV -> { /* () */ }
        }
    }
    return result
}

/** Returns the leaf edge closest to a KV for forward navigation. */
internal fun <BorrowType : Marker.BorrowType, K, V>
    Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.KV>.nextLeafEdge():
    Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge> {
    return when (val f = this.force()) {
        is ForceResult.Leaf -> f.value.rightEdge()
        is ForceResult.Internal -> {
            val internalKv = f.value
            val nextInternalEdge = internalKv.rightEdge()
            nextInternalEdge.descend().firstLeafEdge()
        }
    }
}

/** Returns the leaf edge closest to a KV for backward navigation. */
internal fun <BorrowType : Marker.BorrowType, K, V>
    Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.KV>.nextBackLeafEdge():
    Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge> {
    return when (val f = this.force()) {
        is ForceResult.Leaf -> f.value.leftEdge()
        is ForceResult.Internal -> {
            val internalKv = f.value
            val nextInternalEdge = internalKv.leftEdge()
            nextInternalEdge.descend().lastLeafEdge()
        }
    }
}

/**
 * Returns the leaf edge corresponding to the first point at which the
 * given bound is true.
 */
internal fun <BorrowType : Marker.BorrowType, K, V, Q : Comparable<Q>>
    NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.lowerBound(
    bound: SearchBound<Q>,
): Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge> where K : Comparable<Q> {
    var node = this
    var b = bound
    while (true) {
        val (edge, newBound) = node.findLowerBoundEdge(b)
        when (val f = edge.force()) {
            is ForceResult.Leaf -> return f.value
            is ForceResult.Internal -> {
                node = f.value.descend()
                b = newBound
            }
        }
    }
}

/**
 * Returns the leaf edge corresponding to the last point at which the
 * given bound is true.
 */
internal fun <BorrowType : Marker.BorrowType, K, V, Q : Comparable<Q>>
    NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.upperBound(
    bound: SearchBound<Q>,
): Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge> where K : Comparable<Q> {
    var node = this
    var b = bound
    while (true) {
        val (edge, newBound) = node.findUpperBoundEdge(b)
        when (val f = edge.force()) {
            is ForceResult.Leaf -> return f.value
            is ForceResult.Internal -> {
                node = f.value.descend()
                b = newBound
            }
        }
    }
}
