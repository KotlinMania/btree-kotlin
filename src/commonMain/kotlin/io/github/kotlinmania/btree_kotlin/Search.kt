// port-lint: source library/alloc/src/collections/btree/search.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree_kotlin

// Forward references: the symbols below are introduced in Phase 2 by Node.kt
// (the port of node.rs) and in later phases. They are spelled here the way
// they will resolve once those files land. Until then this file does not
// compile in isolation, and per project rule we do not introduce stub
// classes to paper over that.
//
// Phase-2 dependencies (all in package `btree_kotlin`):
//   - class NodeRef<BorrowType, K, V, Type>
//       fun keys(): List<K>
//       fun len(): Int
//       fun reborrow(): NodeRef<Marker.Immut, K, V, Type>
//       (on Type = Marker.LeafOrInternal) fun force():
//         ForceResult<NodeRef<BorrowType, K, V, Marker.Leaf>,
//                     NodeRef<BorrowType, K, V, Marker.Internal>>
//   - class Handle<Node, HandleType>
//       companion: fun <BT, K, V, NT> newKv(node, idx): Handle<NodeRef<...>, Marker.KV>
//                  fun <BT, K, V, NT> newEdge(node, idx): Handle<NodeRef<...>, Marker.Edge>
//       (on Edge handle to LeafOrInternal node) fun force():
//         ForceResult<Handle<NodeRef<..., Marker.Leaf>, Marker.Edge>,
//                     Handle<NodeRef<..., Marker.Internal>, Marker.Edge>>
//       (on Edge handle to Internal node) fun descend():
//         NodeRef<BorrowType, K, V, Marker.LeafOrInternal>
//   - sealed class ForceResult<L, I> { class Leaf(val value: L); class Internal(val value: I) }
//   - object Marker — phantom-type tags. Members:
//       Leaf, Internal, LeafOrInternal, Owned, Dying, DormantMut, Immut, Mut, ValMut,
//       KV, Edge, sealed class BorrowType
//
// Other forward references (not in node.rs):
//   - sealed class Bound<T> { Included(T); Excluded(T); object Unbounded } in Map.kt (Phase 4)
//   - interface RangeBounds<T> { fun startBound(): Bound<T>; fun endBound(): Bound<T> }
//     also in Map.kt (Phase 4)
//   - fun <V> isSetVal(value: V): Boolean — IsSetVal bridge from set_val.rs
//     (sibling port SetVal.kt, landed). Rust's `V::is_set_val()` is a pure
//     static dispatch on the type parameter; Kotlin has no trait specialization,
//     so the bridge is a runtime `value is SetValZst` check that needs an actual
//     V on hand. searchTreeForBifurcation has no V value at the entry point —
//     once Phase-2 NodeRef lands, the call site below should source a sample V
//     from `self` (e.g. via a `values()` accessor returning `List<V>`) and pass
//     it through. Until then the call below remains spelled `isSetVal<V>()`,
//     consistent with Search.kt being not-compilable-in-isolation per the
//     Phase-2-dep marker in PORTING.md.

/**
 * `SearchBound` mirrors `core::ops::Bound` but adds two unconditional
 * variants used to short-circuit further bound checks once the search has
 * fallen entirely on one side of a key.
 */
internal sealed class SearchBound<out T> {
    /** An inclusive bound to look for, just like `Bound::Included(T)`. */
    data class Included<T>(val value: T) : SearchBound<T>() {
        override fun toString(): String = "Included($value)"
    }

    /** An exclusive bound to look for, just like `Bound::Excluded(T)`. */
    data class Excluded<T>(val value: T) : SearchBound<T>() {
        override fun toString(): String = "Excluded($value)"
    }

    /** An unconditional inclusive bound, just like `Bound::Unbounded`. */
    data object AllIncluded : SearchBound<Nothing>() {
        override fun toString(): String = "AllIncluded"
    }

    /** An unconditional exclusive bound. */
    data object AllExcluded : SearchBound<Nothing>() {
        override fun toString(): String = "AllExcluded"
    }

    companion object {
        internal fun <T> fromRange(rangeBound: Bound<T>): SearchBound<T> = when (rangeBound) {
            is Bound.Included -> Included(rangeBound.value)
            is Bound.Excluded -> Excluded(rangeBound.value)
            is Bound.Unbounded -> AllIncluded
        }
    }
}

internal sealed class SearchResult<BorrowType, K, V, FoundType, GoDownType> {
    data class Found<BorrowType, K, V, FoundType, GoDownType>(
        val handle: Handle<NodeRef<BorrowType, K, V, FoundType>, Marker.KV>,
    ) : SearchResult<BorrowType, K, V, FoundType, GoDownType>() {
        override fun toString(): String = "Found($handle)"
    }

    data class GoDown<BorrowType, K, V, FoundType, GoDownType>(
        val handle: Handle<NodeRef<BorrowType, K, V, GoDownType>, Marker.Edge>,
    ) : SearchResult<BorrowType, K, V, FoundType, GoDownType>() {
        override fun toString(): String = "GoDown($handle)"
    }
}

internal sealed class IndexResult {
    data class KV(val idx: Int) : IndexResult() {
        override fun toString(): String = "KV($idx)"
    }

    data class Edge(val idx: Int) : IndexResult() {
        override fun toString(): String = "Edge($idx)"
    }
}

/**
 * Thrown by [searchTreeForBifurcation] when the lower and upper bounds
 * collapse onto the same leaf edge. Per AGENTS.md, `Result<T, E>` translates
 * to "throw `E`, return `T`". This wrapper carries the Err handle that the
 * Rust signature would have returned in the `Err` arm.
 */
internal class BifurcationLeafEdge<BorrowType, K, V>(
    val edge: Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>,
) : RuntimeException()

/**
 * Tuple holding the bifurcation result of [searchTreeForBifurcation]: the
 * node at which the range's lower and upper edges diverge, the edge indices
 * delimiting the range, and the bounds to continue with in any child node.
 */
internal data class Bifurcation<BorrowType, K, V, Q>(
    val node: NodeRef<BorrowType, K, V, Marker.LeafOrInternal>,
    val lowerEdgeIdx: Int,
    val upperEdgeIdx: Int,
    val lowerChildBound: SearchBound<Q>,
    val upperChildBound: SearchBound<Q>,
)

/**
 * Looks up a given key in a (sub)tree headed by the node, recursively.
 * Returns a `Found` with the handle of the matching KV, if any. Otherwise,
 * returns a `GoDown` with the handle of the leaf edge where the key belongs.
 *
 * The result is meaningful only if the tree is ordered by key, like the tree
 * in a `BTreeMap` is.
 */
// `BorrowType : Marker.BorrowType` mirrors the upstream `BorrowType: marker::BorrowType` bound.
// `Q : Comparable<Q>` replaces `Q: ?Sized + Ord`; `?Sized` is irrelevant in Kotlin.
// `K : Comparable<Q>` replaces the upstream `K: Borrow<Q>` bound: rather than
// borrow K down to Q and compare, the Kotlin port asks K to compare directly
// against Q.
internal fun <BorrowType : Marker.BorrowType, K, V, Q : Comparable<Q>> NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.searchTree(
    key: Q,
): SearchResult<BorrowType, K, V, Marker.LeafOrInternal, Marker.Leaf>
    where K : Comparable<Q> {
    var self = this
    while (true) {
        when (val r = self.searchNode(key)) {
            is SearchResult.Found -> return SearchResult.Found(r.handle)
            is SearchResult.GoDown -> when (val forced = r.handle.force()) {
                is ForceResult.Leaf -> return SearchResult.GoDown(forced.value)
                is ForceResult.Internal -> self = forced.value.descend()
            }
        }
    }
}

/**
 * Descends to the nearest node where the edge matching the lower bound
 * of the range is different from the edge matching the upper bound, i.e.,
 * the nearest node that has at least one key contained in the range.
 *
 * If found, returns a [Bifurcation] with that node, the strictly ascending
 * pair of edge indices in the node delimiting the range, and the
 * corresponding pair of bounds for continuing the search in the child
 * nodes, in case the node is internal.
 *
 * If not found, throws [BifurcationLeafEdge] carrying the leaf edge
 * matching the entire range. (Rust: `Err(handle)`.)
 *
 * As a diagnostic service, panics if the range specifies impossible bounds.
 *
 * The result is meaningful only if the tree is ordered by key.
 */
internal fun <BorrowType : Marker.BorrowType, K, V, Q : Comparable<Q>, R : RangeBounds<Q>> NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.searchTreeForBifurcation(
    range: R,
): Bifurcation<BorrowType, K, V, Q>
    where K : Comparable<Q> {
    var self = this
    // Determine if map or set is being searched
    val isSet = isSetVal<V>()

    // Inlining these variables should be avoided. We assume the bounds reported by `range`
    // remain the same, but an adversarial implementation could change between calls (#81138).
    val start = range.startBound()
    val end = range.endBound()
    when {
        start is Bound.Excluded && end is Bound.Excluded && start.value == end.value -> {
            if (isSet) {
                throw IllegalArgumentException("range start and end are equal and excluded in BTreeSet")
            } else {
                throw IllegalArgumentException("range start and end are equal and excluded in BTreeMap")
            }
        }
        (start is Bound.Included || start is Bound.Excluded) &&
            (end is Bound.Included || end is Bound.Excluded) -> {
            val s: Q = when (start) {
                is Bound.Included -> start.value
                is Bound.Excluded -> start.value
                else -> error("unreachable")
            }
            val e: Q = when (end) {
                is Bound.Included -> end.value
                is Bound.Excluded -> end.value
                else -> error("unreachable")
            }
            if (s > e) {
                if (isSet) {
                    throw IllegalArgumentException("range start is greater than range end in BTreeSet")
                } else {
                    throw IllegalArgumentException("range start is greater than range end in BTreeMap")
                }
            }
        }
    }
    var lowerBound = SearchBound.fromRange(start)
    var upperBound = SearchBound.fromRange(end)
    while (true) {
        val (lowerEdgeIdx, lowerChildBound) = self.findLowerBoundIndex(lowerBound)
        // SAFETY: `lowerEdgeIdx` is a valid edge index returned by `findLowerBoundIndex`.
        val (upperEdgeIdx, upperChildBound) = self.findUpperBoundIndex(upperBound, lowerEdgeIdx)
        if (lowerEdgeIdx < upperEdgeIdx) {
            return Bifurcation(
                self,
                lowerEdgeIdx,
                upperEdgeIdx,
                lowerChildBound,
                upperChildBound,
            )
        }
        check(lowerEdgeIdx == upperEdgeIdx) // debug_assert_eq!(lower_edge_idx, upper_edge_idx);
        // SAFETY: `lowerEdgeIdx` is a valid edge index for `self`.
        val commonEdge = Handle.newEdge(self, lowerEdgeIdx)
        when (val forced = commonEdge.force()) {
            is ForceResult.Leaf -> throw BifurcationLeafEdge(forced.value)
            is ForceResult.Internal -> {
                self = forced.value.descend()
                lowerBound = lowerChildBound
                upperBound = upperChildBound
            }
        }
    }
}

/**
 * Finds an edge in the node delimiting the lower bound of a range.
 * Also returns the lower bound to be used for continuing the search in
 * the matching child node, if `self` is an internal node.
 *
 * The result is meaningful only if the tree is ordered by key.
 */
internal fun <BorrowType : Marker.BorrowType, K, V, Q : Comparable<Q>> NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.findLowerBoundEdge(
    bound: SearchBound<Q>,
): Pair<Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.Edge>, SearchBound<Q>>
    where K : Comparable<Q> {
    val (edgeIdx, newBound) = this.findLowerBoundIndex(bound)
    // SAFETY: `edgeIdx` is the edge index returned by `findLowerBoundIndex`.
    val edge = Handle.newEdge(this, edgeIdx)
    return Pair(edge, newBound)
}

/** Clone of [findLowerBoundEdge] for the upper bound. */
internal fun <BorrowType : Marker.BorrowType, K, V, Q : Comparable<Q>> NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.findUpperBoundEdge(
    bound: SearchBound<Q>,
): Pair<Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.Edge>, SearchBound<Q>>
    where K : Comparable<Q> {
    // SAFETY: `0` is a valid edge index for any node.
    val (edgeIdx, newBound) = this.findUpperBoundIndex(bound, 0)
    // SAFETY: `edgeIdx` is the edge index returned by `findUpperBoundIndex`.
    val edge = Handle.newEdge(this, edgeIdx)
    return Pair(edge, newBound)
}

/**
 * Looks up a given key in the node, without recursion.
 * Returns a `Found` with the handle of the matching KV, if any. Otherwise,
 * returns a `GoDown` with the handle of the edge where the key might be found
 * (if the node is internal) or where the key can be inserted.
 *
 * The result is meaningful only if the tree is ordered by key, like the tree
 * in a `BTreeMap` is.
 */
internal fun <BorrowType, K, V, Type, Q : Comparable<Q>> NodeRef<BorrowType, K, V, Type>.searchNode(
    key: Q,
): SearchResult<BorrowType, K, V, Type, Type>
    where K : Comparable<Q> {
    // SAFETY: `0` is a valid edge index for any node.
    return when (val r = this.findKeyIndex(key, 0)) {
        is IndexResult.KV -> SearchResult.Found(Handle.newKv(this, r.idx))
        is IndexResult.Edge -> SearchResult.GoDown(Handle.newEdge(this, r.idx))
    }
}

/**
 * Returns either the KV index in the node at which the key (or an equivalent)
 * exists, or the edge index where the key belongs, starting from a particular index.
 *
 * The result is meaningful only if the tree is ordered by key, like the tree
 * in a `BTreeMap` is.
 *
 * # Safety
 * `startIndex` must be a valid edge index for the node.
 */
private fun <BorrowType, K, V, Type, Q : Comparable<Q>> NodeRef<BorrowType, K, V, Type>.findKeyIndex(
    key: Q,
    startIndex: Int,
): IndexResult
    where K : Comparable<Q> {
    val node = this.reborrow()
    val keys = node.keys()
    check(startIndex <= keys.size) // debug_assert!(start_index <= keys.len());
    // SAFETY: `startIndex <= keys.size`, so the slice from `startIndex` is in bounds.
    // Iterate by index rather than allocating a sublist; matches `keys.get_unchecked(start..)`.
    for (offset in 0 until (keys.size - startIndex)) {
        val k = keys[startIndex + offset]
        // `key.cmp(k.borrow())` -> `key.compareTo(k)`, with the convention that
        // K : Comparable<Q> means we invert: compare k to key, then negate.
        // Equivalently, use `-k.compareTo(key)` so that:
        //   key > k  -> Greater (positive)
        //   key == k -> Equal   (zero)
        //   key < k  -> Less    (negative)
        val cmp = -k.compareTo(key)
        when {
            cmp > 0 -> {} // Ordering::Greater
            cmp == 0 -> return IndexResult.KV(startIndex + offset) // Ordering::Equal
            else -> return IndexResult.Edge(startIndex + offset) // Ordering::Less
        }
    }
    return IndexResult.Edge(keys.size)
}

/**
 * Finds an edge index in the node delimiting the lower bound of a range.
 * Also returns the lower bound to be used for continuing the search in
 * the matching child node, if `self` is an internal node.
 *
 * The result is meaningful only if the tree is ordered by key.
 */
private fun <BorrowType, K, V, Type, Q : Comparable<Q>> NodeRef<BorrowType, K, V, Type>.findLowerBoundIndex(
    bound: SearchBound<Q>,
): Pair<Int, SearchBound<Q>>
    where K : Comparable<Q> = when (bound) {
    is SearchBound.Included -> when (val r = this.findKeyIndex(bound.value, 0)) {
        is IndexResult.KV -> Pair(r.idx, SearchBound.AllExcluded)
        is IndexResult.Edge -> Pair(r.idx, bound)
    }
    is SearchBound.Excluded -> when (val r = this.findKeyIndex(bound.value, 0)) {
        is IndexResult.KV -> Pair(r.idx + 1, SearchBound.AllIncluded)
        is IndexResult.Edge -> Pair(r.idx, bound)
    }
    SearchBound.AllIncluded -> Pair(0, SearchBound.AllIncluded)
    SearchBound.AllExcluded -> Pair(this.len(), SearchBound.AllExcluded)
}

/**
 * Mirror image of [findLowerBoundIndex] for the upper bound,
 * with an additional parameter to skip part of the key array.
 *
 * # Safety
 * `startIndex` must be a valid edge index for the node.
 */
private fun <BorrowType, K, V, Type, Q : Comparable<Q>> NodeRef<BorrowType, K, V, Type>.findUpperBoundIndex(
    bound: SearchBound<Q>,
    startIndex: Int,
): Pair<Int, SearchBound<Q>>
    where K : Comparable<Q> = when (bound) {
    is SearchBound.Included -> when (val r = this.findKeyIndex(bound.value, startIndex)) {
        is IndexResult.KV -> Pair(r.idx + 1, SearchBound.AllExcluded)
        is IndexResult.Edge -> Pair(r.idx, bound)
    }
    is SearchBound.Excluded -> when (val r = this.findKeyIndex(bound.value, startIndex)) {
        is IndexResult.KV -> Pair(r.idx, SearchBound.AllIncluded)
        is IndexResult.Edge -> Pair(r.idx, bound)
    }
    SearchBound.AllIncluded -> Pair(this.len(), SearchBound.AllIncluded)
    SearchBound.AllExcluded -> Pair(startIndex, SearchBound.AllExcluded)
}
