// port-lint: source library/alloc/src/collections/btree/navigate.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

// Upstream `# Safety` clauses about not visiting the same KV twice are
// preserved verbatim in KDoc -- callers must respect them.

// ============================================================================
// LeafRange
// ============================================================================

// `front` and `back` are always both `null` or both non-null.
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

    // Cloning of immutable ranges is omitted: Kotlin Iterator-shaped types
    // are not generally cloneable, and consumers of this port don't fork
    // ranges. The two equivalent immutable handles can be reconstructed via
    // [reborrow].

    private fun isEmpty(): Boolean {
        // self.front == self.back  — uses Handle::structuralEq.
        val f = front
        val b = back
        return when {
            f == null && b == null -> true
            f == null || b == null -> false
            else -> f.structuralEq(b)
        }
    }

    /** Temporarily takes out another, immutable equivalent of the same range. */
    internal fun reborrow(): LeafRange<Marker.Immut, K, V> {
        return LeafRange(
            front = front?.reborrow(),
            back = back?.reborrow(),
        )
    }

    // -------------------------------------------------------------------------
    // LeafRange<Immut, K, V>
    // -------------------------------------------------------------------------

    /**
     * Call this only when the underlying borrow type is [Marker.Immut].
     */
    internal fun nextCheckedImmut(): Pair<K, V>? {
        val self = this as LeafRange<Marker.Immut, K, V>
        return self.performNextChecked { kv -> kv.intoKv() }
    }

    internal fun nextBackCheckedImmut(): Pair<K, V>? {
        val self = this as LeafRange<Marker.Immut, K, V>
        return self.performNextBackChecked { kv -> kv.intoKv() }
    }

    // -------------------------------------------------------------------------
    // LeafRange with ValMut borrow
    // -------------------------------------------------------------------------

    internal fun nextCheckedValMut(): Pair<K, V>? {
        val self = this as LeafRange<Marker.ValMut, K, V>
        // SAFETY: Kotlin uses the reference directly; GC keeps it alive.
        return self.performNextChecked { kv -> kv.intoKvValmut() }
    }

    internal fun nextBackCheckedValMut(): Pair<K, V>? {
        val self = this as LeafRange<Marker.ValMut, K, V>
        // SAFETY: Kotlin uses the reference directly; GC keeps it alive.
        return self.performNextBackChecked { kv -> kv.intoKvValmut() }
    }
}

/**
 * If possible, extract some result from the following KV and move to the edge beyond it.
 */
private inline fun <BorrowType : Marker.BorrowType, K, V, R> LeafRange<BorrowType, K, V>.performNextChecked(
    f: (Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.KV>) -> R,
): R? {
    if (isEmptyInternal()) return null
    // mem::replace(self.front.asMut().unwrap(), |front| { ... })
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
private inline fun <BorrowType : Marker.BorrowType, K, V, R> LeafRange<BorrowType, K, V>.performNextBackChecked(
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

// `LeafRange.isEmpty` is private; expose a same-package shim for the
// extension-function based `performNext*Checked` to use.
private fun <BorrowType, K, V> LeafRange<BorrowType, K, V>.isEmptyInternal(): Boolean {
    val f = front
    val b = back
    return when {
        f == null && b == null -> true
        f == null || b == null -> false
        else -> f.structuralEq(b)
    }
}

// ============================================================================
// LazyLeafHandle
// ============================================================================

/**
 * `enum LazyLeafHandle<BorrowType, K, V> { Root(...), Edge(...) }`,
 * rendered as a sealed class with two variants.
 */
internal sealed class LazyLeafHandle<BorrowType, K, V> {
    /** Not yet descended. */
    data class Root<BorrowType, K, V>(
        val node: NodeRef<BorrowType, K, V, Marker.LeafOrInternal>,
    ) : LazyLeafHandle<BorrowType, K, V>()

    data class Edge<BorrowType, K, V>(
        val edge: Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>,
    ) : LazyLeafHandle<BorrowType, K, V>()

    // Cloning is implicit: data class generates structural equality and
    // copying a NodeRef or Handle is just copying the reference.
}

internal fun <BorrowType, K, V> LazyLeafHandle<BorrowType, K, V>.reborrow():
    LazyLeafHandle<Marker.Immut, K, V> = when (this) {
    is LazyLeafHandle.Root -> LazyLeafHandle.Root(node.reborrow())
    is LazyLeafHandle.Edge -> LazyLeafHandle.Edge(edge.reborrow())
}

// ============================================================================
// LazyLeafRange
// ============================================================================

// `front` and `back` are always both `null` or both non-null.
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

    // Cloning omitted — see [LeafRange] for the rationale.

    /** Temporarily takes out another, immutable equivalent of the same range. */
    internal fun reborrow(): LazyLeafRange<Marker.Immut, K, V> {
        return LazyLeafRange(
            front = front?.reborrow(),
            back = back?.reborrow(),
        )
    }

    // -------------------------------------------------------------------------
    // LazyLeafRange<Immut, K, V>
    // -------------------------------------------------------------------------

    /**
     * SAFETY: There must be another KV in the direction travelled.
     */
    internal fun nextUncheckedImmut(): Pair<K, V> {
        val self = this as LazyLeafRange<Marker.Immut, K, V>
        // SAFETY: caller-enforced precondition guarantees initFront() returns non-null.
        val edge = self.initFront()!!
        val (newEdge, kv) = edge.nextUncheckedImmut()
        self.front = LazyLeafHandle.Edge(newEdge)
        return kv
    }

    internal fun nextBackUncheckedImmut(): Pair<K, V> {
        val self = this as LazyLeafRange<Marker.Immut, K, V>
        val edge = self.initBack()!!
        val (newEdge, kv) = edge.nextBackUncheckedImmut()
        self.back = LazyLeafHandle.Edge(newEdge)
        return kv
    }

    // -------------------------------------------------------------------------
    // LazyLeafRange with ValMut borrow
    // -------------------------------------------------------------------------

    /**
     * SAFETY: There must be another KV in the direction travelled.
     */
    internal fun nextUncheckedValMut(): Pair<K, V> {
        val self = this as LazyLeafRange<Marker.ValMut, K, V>
        val edge = self.initFront()!!
        val (newEdge, kv) = edge.nextUncheckedValMut()
        self.front = LazyLeafHandle.Edge(newEdge)
        return kv
    }

    internal fun nextBackUncheckedValMut(): Pair<K, V> {
        val self = this as LazyLeafRange<Marker.ValMut, K, V>
        val edge = self.initBack()!!
        val (newEdge, kv) = edge.nextBackUncheckedValMut()
        self.back = LazyLeafHandle.Edge(newEdge)
        return kv
    }

    // -------------------------------------------------------------------------
    // LazyLeafRange with Dying borrow
    // -------------------------------------------------------------------------

    internal fun takeFrontDying():
        Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>? {
        val self = this as LazyLeafRange<Marker.Dying, K, V>
        val taken = self.front ?: return null
        self.front = null
        return when (taken) {
            is LazyLeafHandle.Root -> taken.node.firstLeafEdge()
            is LazyLeafHandle.Edge -> taken.edge
        }
    }

    /**
     * SAFETY: caller has previously primed `front` to non-null; that
     * invariant is re-checked here as a debug assertion.
     */
    internal fun deallocatingNextUncheckedDying():
        Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.KV> {
        val self = this as LazyLeafRange<Marker.Dying, K, V>
        check(self.front != null) // debugAssert(self.front.isSome())
        val edge = self.initFront()!!
        val (newEdge, kv) = edge.deallocatingNextUnchecked()
        self.front = LazyLeafHandle.Edge(newEdge)
        return kv
    }

    internal fun deallocatingNextBackUncheckedDying():
        Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.KV> {
        val self = this as LazyLeafRange<Marker.Dying, K, V>
        check(self.back != null) // debugAssert(self.back.isSome())
        val edge = self.initBack()!!
        val (newEdge, kv) = edge.deallocatingNextBackUnchecked()
        self.back = LazyLeafHandle.Edge(newEdge)
        return kv
    }

    internal fun deallocatingEndDying() {
        val self = this as LazyLeafRange<Marker.Dying, K, V>
        val front = self.takeFrontDying()
        front?.deallocatingEnd()
    }
}

// -------------------------------------------------------------------------
// LazyLeafRange initFront / initBack
// -------------------------------------------------------------------------

/**
 * Returns the underlying leaf-edge handle, descending from the root if the
 * front slot is still in [LazyLeafHandle.Root] form. The caller stores the
 * returned handle back into the [front] field after any state-machine update.
 */
internal fun <BorrowType : Marker.BorrowType, K, V> LazyLeafRange<BorrowType, K, V>.initFront():
    Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>? {
    val current = front
    if (current is LazyLeafHandle.Root) {
        // SAFETY: ptr::read(root) — Kotlin uses the reference directly.
        front = LazyLeafHandle.Edge(current.node.firstLeafEdge())
    }
    return when (val f = front) {
        null -> null
        is LazyLeafHandle.Edge -> f.edge
        // SAFETY: the code above would have replaced it.
        is LazyLeafHandle.Root -> error("unreachable: Root case was rewritten above")
    }
}

internal fun <BorrowType : Marker.BorrowType, K, V> LazyLeafRange<BorrowType, K, V>.initBack():
    Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>? {
    val current = back
    if (current is LazyLeafHandle.Root) {
        // SAFETY: ptr::read(root) — Kotlin uses the reference directly.
        back = LazyLeafHandle.Edge(current.node.lastLeafEdge())
    }
    return when (val b = back) {
        null -> null
        is LazyLeafHandle.Edge -> b.edge
        is LazyLeafHandle.Root -> error("unreachable: Root case was rewritten above")
    }
}

// ============================================================================
// NodeRef<LeafOrInternal>: findLeafEdgesSpanningRange
// ============================================================================

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
 * # Safety
 * Unless `BorrowType` is `Immut`, do not import the handles to visit the same
 * KV twice.
 */
internal inline fun <BorrowType : Marker.BorrowType, K, reified V, Q : Comparable<Q>, R : RangeBounds<Q>>
    NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.findLeafEdgesSpanningRange(
    range: R,
): LeafRange<BorrowType, K, V> where K : Comparable<Q> = findLeafEdgesSpanningRangeExplicit(range, isSetVal<V>())

/**
 * Explicit-`isSet` variant of [findLeafEdgesSpanningRange] for non-reified
 * callers (class methods on `BTreeMap`/`BTreeSet`).
 */
internal fun <BorrowType : Marker.BorrowType, K, V, Q : Comparable<Q>, R : RangeBounds<Q>>
    NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.findLeafEdgesSpanningRangeExplicit(
    range: R,
    isSet: Boolean,
): LeafRange<BorrowType, K, V> where K : Comparable<Q> {
    when (val r = this.searchTreeForBifurcationExplicit<BorrowType, K, V, Q, R>(range, isSet)) {
        is BifurcationResult.LeafEdge -> return LeafRange.none()
        is BifurcationResult.Ok -> {
            val bif = r.value
            // SAFETY: ptr::read(&node) — Kotlin uses the reference directly.
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

// ============================================================================
// fullRange (free function)
// ============================================================================

internal fun <BorrowType : Marker.BorrowType, K, V> fullRange(
    root1: NodeRef<BorrowType, K, V, Marker.LeafOrInternal>,
    root2: NodeRef<BorrowType, K, V, Marker.LeafOrInternal>,
): LazyLeafRange<BorrowType, K, V> {
    return LazyLeafRange(
        front = LazyLeafHandle.Root(root1),
        back = LazyLeafHandle.Root(root2),
    )
}

// ============================================================================
// NodeRef<Immut, ...>: rangeSearch, fullRange
// ============================================================================

/**
 * Finds the pair of leaf edges delimiting a specific range in a tree.
 *
 * Renamed to [rangeSearchImmut] because Kotlin extension-function overload
 * resolution rejects same-name extensions whose receivers differ only in
 * concrete generic argument.
 *
 * The result is meaningful only if the tree is ordered by key.
 */
internal inline fun <K, reified V, Q : Comparable<Q>, R : RangeBounds<Q>>
    NodeRef<Marker.Immut, K, V, Marker.LeafOrInternal>.rangeSearchImmut(
    range: R,
): LeafRange<Marker.Immut, K, V> where K : Comparable<Q> {
    // SAFETY: our borrow type is immutable.
    return this.findLeafEdgesSpanningRange<Marker.Immut, K, V, Q, R>(range)
}

/** Explicit-`isSet` variant of [rangeSearchImmut] for non-reified callers. */
internal fun <K, V, Q : Comparable<Q>, R : RangeBounds<Q>>
    NodeRef<Marker.Immut, K, V, Marker.LeafOrInternal>.rangeSearchImmutExplicit(
    range: R,
    isSet: Boolean,
): LeafRange<Marker.Immut, K, V> where K : Comparable<Q> {
    return this.findLeafEdgesSpanningRangeExplicit<Marker.Immut, K, V, Q, R>(range, isSet)
}

/** Finds the pair of leaf edges delimiting an entire tree (Immut). */
internal fun <K, V> NodeRef<Marker.Immut, K, V, Marker.LeafOrInternal>.fullRangeImmut():
    LazyLeafRange<Marker.Immut, K, V> {
    return fullRange(this, this)
}

// ============================================================================
// NodeRef<ValMut, ...>: rangeSearch, fullRange
// ============================================================================

/**
 * Splits a unique reference into a pair of leaf edges delimiting a specified range.
 * The result are non-unique references allowing (some) mutation, which must be used
 * carefully.
 *
 * The result is meaningful only if the tree is ordered by key.
 *
 * # Safety
 * Do not import the duplicate handles to visit the same KV twice.
 */
internal inline fun <K, reified V, Q : Comparable<Q>, R : RangeBounds<Q>>
    NodeRef<Marker.ValMut, K, V, Marker.LeafOrInternal>.rangeSearchValMut(
    range: R,
): LeafRange<Marker.ValMut, K, V> where K : Comparable<Q> {
    return this.findLeafEdgesSpanningRange<Marker.ValMut, K, V, Q, R>(range)
}

/** Explicit-`isSet` variant of [rangeSearchValMut] for non-reified callers. */
internal fun <K, V, Q : Comparable<Q>, R : RangeBounds<Q>>
    NodeRef<Marker.ValMut, K, V, Marker.LeafOrInternal>.rangeSearchValMutExplicit(
    range: R,
    isSet: Boolean,
): LeafRange<Marker.ValMut, K, V> where K : Comparable<Q> {
    return this.findLeafEdgesSpanningRangeExplicit<Marker.ValMut, K, V, Q, R>(range, isSet)
}

/**
 * Splits a unique reference into a pair of leaf edges delimiting the full range of the tree.
 * The results are non-unique references allowing mutation (of values only), so must be used
 * with care.
 */
internal fun <K, V> NodeRef<Marker.ValMut, K, V, Marker.LeafOrInternal>.fullRangeValMut():
    LazyLeafRange<Marker.ValMut, K, V> {
    // We duplicate the root NodeRef here -- we will never visit the same KV
    // twice, and never end up with overlapping value references.
    // SAFETY: ptr::read(&self) — Kotlin reference copy.
    val self2 = NodeRef<Marker.ValMut, K, V, Marker.LeafOrInternal>(height = height, node = node)
    return fullRange(this, self2)
}

// ============================================================================
// NodeRef<Dying, ...>: fullRange
// ============================================================================

/**
 * Splits a unique reference into a pair of leaf edges delimiting the full range of the tree.
 * The results are non-unique references allowing massively destructive mutation, so must be
 * used with the utmost care.
 */
internal fun <K, V> NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>.fullRangeDying():
    LazyLeafRange<Marker.Dying, K, V> {
    // We duplicate the root NodeRef here -- we will never access it in a way
    // that overlaps references obtained from the root.
    // SAFETY: ptr::read(&self) — Kotlin reference copy.
    val self2 = NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>(height = height, node = node)
    return fullRange(this, self2)
}

// ============================================================================
// Handle<Leaf, Edge>: nextKv / nextBackKv
// ============================================================================

/**
 * Translates `Result<Handle<..., LeafOrInternal, KV>, NodeRef<..., LeafOrInternal>>`
 * for [nextKv] / [nextBackKv]. Same sealed-class-of-Ok-or-Err shape used elsewhere.
 */
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
        this.forgetNodeTypeLeafEdge()
    while (true) {
        edge = when (val rk = edge.rightKv()) {
            is EdgeKvResult.Ok -> return NextKvResult.Ok(rk.handle)
            is EdgeKvResult.Err -> when (val asc = rk.handle.intoNode().ascend()) {
                is AscendResult.Ok -> asc.handle.forgetNodeTypeInternalEdge()
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
        this.forgetNodeTypeLeafEdge()
    while (true) {
        edge = when (val lk = edge.leftKv()) {
            is EdgeKvResult.Ok -> return NextKvResult.Ok(lk.handle)
            is EdgeKvResult.Err -> when (val asc = lk.handle.intoNode().ascend()) {
                is AscendResult.Ok -> asc.handle.forgetNodeTypeInternalEdge()
                is AscendResult.Err -> return NextKvResult.Err(asc.node)
            }
        }
    }
}

// ============================================================================
// Handle<Internal, Edge>: nextKv (private)
// ============================================================================

/**
 * Translates the private `Result<Handle<..., Internal, KV>, NodeRef<..., Internal>>`
 * for [nextKvInternal]. The variant names mirror the upstream `Ok` / `Err`.
 */
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
 *
 * Renamed to [nextKvInternal] because Kotlin extension-function overload
 * resolution rejects same-name extensions whose receivers differ only by the
 * [Marker.Leaf] vs [Marker.Internal] type argument.
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
                    // Upstream returns `NodeRef<..., Internal>` from the
                    // ascend Err branch via type inference; here Node.kt's
                    // `ascend()` widens the recovery type to `LeafOrInternal`.
                    // Since the receiver is `Internal`, the recovered root
                    // (which is `self` if no parent existed) is also
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

// ============================================================================
// Handle<Dying, Leaf, Edge>: deallocatingNext / deallocatingNextBack / deallocatingEnd
// ============================================================================

/**
 * Given a leaf edge handle into a dying tree, returns the next leaf edge
 * on the right side, and the key-value pair in between, if they exist.
 *
 * If the given edge is the last one in a leaf, this method deallocates
 * the leaf, as well as any ancestor nodes whose last edge was reached.
 * This implies that if no more key-value pair follows, the entire tree
 * will have been deallocated and there is nothing left to return.
 *
 * # Safety
 * - The given edge must not have been previously returned by counterpart
 *   `deallocatingNextBack`.
 * - The returned KV handle is only valid to access the key and value,
 *   and only valid until the next call to a `deallocating_` method.
 */
internal fun <K, V>
    Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>.deallocatingNext():
    Pair<
        Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>,
        Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.KV>,
        >? {
    var edge: Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.Edge> =
        this.forgetNodeTypeLeafEdge()
    while (true) {
        edge = when (val rk = edge.rightKv()) {
            is EdgeKvResult.Ok -> {
                val kv = rk.handle
                // SAFETY: ptr::read(&kv) — Kotlin reference reuse; GC keeps `kv` alive.
                return Pair(kv.nextLeafEdge(), kv)
            }
            is EdgeKvResult.Err -> {
                val node = rk.handle.intoNode()
                val parent = node.deallocateAndAscend()
                if (parent != null) {
                    parent.forgetNodeTypeInternalEdge()
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
        this.forgetNodeTypeLeafEdge()
    while (true) {
        edge = when (val lk = edge.leftKv()) {
            is EdgeKvResult.Ok -> {
                val kv = lk.handle
                // SAFETY: ptr::read(&kv) — Kotlin reference reuse; GC keeps `kv` alive.
                return Pair(kv.nextBackLeafEdge(), kv)
            }
            is EdgeKvResult.Err -> {
                val node = lk.handle.intoNode()
                val parent = node.deallocateAndAscend()
                if (parent != null) {
                    parent.forgetNodeTypeInternalEdge()
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
        this.forgetNodeTypeLeafEdge()
    while (true) {
        val parent = edge.intoNode().deallocateAndAscend() ?: return
        edge = parent.forgetNodeTypeInternalEdge()
    }
}

// ============================================================================
// Handle<Immut, Leaf, Edge>: nextUnchecked / nextBackUnchecked
// ============================================================================

/**
 * Returns a `Pair<newEdge, kv>` while advancing across one KV. The caller
 * assigns the new edge back into whatever slot held the receiver (always
 * [LazyLeafRange.front] or [LazyLeafRange.back] here).
 *
 * SAFETY: There must be another KV in the direction travelled.
 */
internal fun <K, V>
    Handle<NodeRef<Marker.Immut, K, V, Marker.Leaf>, Marker.Edge>.nextUncheckedImmut():
    Pair<Handle<NodeRef<Marker.Immut, K, V, Marker.Leaf>, Marker.Edge>, Pair<K, V>> {
    val (newEdge, kvRef) = replace(this) { leafEdge ->
        val kv = when (val r = leafEdge.nextKv()) {
            is NextKvResult.Ok -> r.handle
            is NextKvResult.Err -> error("unreachable: caller-asserted there is another KV")
        }
        Pair(kv.nextLeafEdge(), kv)
    }
    return Pair(newEdge, kvRef.intoKv())
}

/** Mirror of [nextUncheckedImmut] for the previous leaf edge. */
internal fun <K, V>
    Handle<NodeRef<Marker.Immut, K, V, Marker.Leaf>, Marker.Edge>.nextBackUncheckedImmut():
    Pair<Handle<NodeRef<Marker.Immut, K, V, Marker.Leaf>, Marker.Edge>, Pair<K, V>> {
    val (newEdge, kvRef) = replace(this) { leafEdge ->
        val kv = when (val r = leafEdge.nextBackKv()) {
            is NextKvResult.Ok -> r.handle
            is NextKvResult.Err -> error("unreachable: caller-asserted there is another KV")
        }
        Pair(kv.nextBackLeafEdge(), kv)
    }
    return Pair(newEdge, kvRef.intoKv())
}

// ============================================================================
// Handle<ValMut, Leaf, Edge>: nextUnchecked / nextBackUnchecked
// ============================================================================

/**
 * SAFETY: There must be another KV in the direction travelled.
 *
 * The intoKvValmut() call is deferred until after the swap as a micro
 * optimisation noted upstream as "Doing this last is faster, according to
 * benchmarks". Preserved verbatim.
 */
internal fun <K, V>
    Handle<NodeRef<Marker.ValMut, K, V, Marker.Leaf>, Marker.Edge>.nextUncheckedValMut():
    Pair<Handle<NodeRef<Marker.ValMut, K, V, Marker.Leaf>, Marker.Edge>, Pair<K, V>> {
    val (newEdge, kv) = replace(this) { leafEdge ->
        val kv = when (val r = leafEdge.nextKv()) {
            is NextKvResult.Ok -> r.handle
            is NextKvResult.Err -> error("unreachable: caller-asserted there is another KV")
        }
        // SAFETY: ptr::read(&kv) — Kotlin uses the reference directly.
        Pair(kv.nextLeafEdge(), kv)
    }
    // Doing this last is faster, according to benchmarks.
    return Pair(newEdge, kv.intoKvValmut())
}

/** Mirror of [nextUncheckedValMut] for the previous leaf edge. */
internal fun <K, V>
    Handle<NodeRef<Marker.ValMut, K, V, Marker.Leaf>, Marker.Edge>.nextBackUncheckedValMut():
    Pair<Handle<NodeRef<Marker.ValMut, K, V, Marker.Leaf>, Marker.Edge>, Pair<K, V>> {
    val (newEdge, kv) = replace(this) { leafEdge ->
        val kv = when (val r = leafEdge.nextBackKv()) {
            is NextKvResult.Ok -> r.handle
            is NextKvResult.Err -> error("unreachable: caller-asserted there is another KV")
        }
        // SAFETY: ptr::read(&kv) — Kotlin uses the reference directly.
        Pair(kv.nextBackLeafEdge(), kv)
    }
    // Doing this last is faster, according to benchmarks.
    return Pair(newEdge, kv.intoKvValmut())
}

// ============================================================================
// Handle<Dying, Leaf, Edge>: deallocatingNextUnchecked / deallocatingNextBackUnchecked
// ============================================================================

/**
 * SAFETY:
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
        // SAFETY: caller-asserted there is another KV; deallocatingNext returns non-null.
        leafEdge.deallocatingNext() ?: error("unreachable: caller-asserted KV present")
    }
}

/** Mirror of [deallocatingNextUnchecked] for the previous leaf edge. */
internal fun <K, V>
    Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>.deallocatingNextBackUnchecked():
    Pair<
        Handle<NodeRef<Marker.Dying, K, V, Marker.Leaf>, Marker.Edge>,
        Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.KV>,
        > {
    return replace(this) { leafEdge ->
        // SAFETY: caller-asserted there is another KV; deallocatingNextBack returns non-null.
        leafEdge.deallocatingNextBack() ?: error("unreachable: caller-asserted KV present")
    }
}

// ============================================================================
// NodeRef<LeafOrInternal>: firstLeafEdge / lastLeafEdge
// ============================================================================

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

// ============================================================================
// Position<BorrowType, K, V>: visitNodesInOrder, calcLength
// ============================================================================

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

// ============================================================================
// Handle<LeafOrInternal, KV>: nextLeafEdge / nextBackLeafEdge
// ============================================================================

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

// ============================================================================
// NodeRef<LeafOrInternal>: lowerBound / upperBound
// ============================================================================

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
