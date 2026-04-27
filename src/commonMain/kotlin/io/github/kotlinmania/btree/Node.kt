// port-lint: source library/alloc/src/collections/btree/node.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
@file:Suppress("UNCHECKED_CAST", "RemoveExplicitTypeArguments", "ConvertTwoComparisonsToRangeCheck")

package io.github.kotlinmania.btree

// This is an attempt at an implementation following the ideal
//
// ```
// struct BTreeMap<K, V> {
//     height: usize,
//     root: Option<Box<Node<K, V, height>>>
// }
//
// struct Node<K, V, height: usize> {
//     keys: [K; 2 * B - 1],
//     vals: [V; 2 * B - 1],
//     edges: [if height > 0 { Box<Node<K, V, height - 1>> } else { () }; 2 * B],
//     parent: Option<(NonNull<Node<K, V, height + 1>>, u16)>,
//     len: u16,
// }
// ```
//
// Since Rust doesn't actually have dependent types and polymorphic recursion,
// we make do with lots of unsafety. The Kotlin port is even more dynamic:
// it represents `LeafNode` and `InternalNode` as a class hierarchy, with
// `InternalNode` extending `LeafNode`. That mirrors the upstream
// `#[repr(C)]` trick — the upstream cast `*mut InternalNode -> *mut LeafNode`
// is, in Kotlin, the implicit upcast that comes for free with subclassing.
//
// A major goal of this module is to avoid complexity by treating the tree as a generic (if
// weirdly shaped) container and avoiding dealing with most of the B-Tree invariants. As such,
// this module doesn't care whether the entries are sorted, which nodes can be underfull, or
// even what underfull means. However, we do rely on a few invariants:
//
// - Trees must have uniform depth/height. This means that every path down to a leaf from a
//   given node has exactly the same length.
// - A node of length `n` has `n` keys, `n` values, and `n + 1` edges.
//   This implies that even an empty node has at least one edge.
//   For a leaf node, "having an edge" only means we can identify a position in the node,
//   since leaf edges are empty and need no data representation. In an internal node,
//   an edge both identifies a position and contains a pointer to a child node.

internal const val B: Int = 6
internal const val CAPACITY: Int = 2 * B - 1
internal const val MIN_LEN_AFTER_SPLIT: Int = B - 1
private const val KV_IDX_CENTER: Int = B - 1
private const val EDGE_IDX_LEFT_OF_CENTER: Int = B - 1
private const val EDGE_IDX_RIGHT_OF_CENTER: Int = B

/**
 * The underlying representation of leaf nodes and part of the representation of internal nodes.
 *
 * The class is `open` so that [InternalNode] can extend it. This mirrors the
 * upstream `#[repr(C)]` layout where an `InternalNode` begins with a
 * `LeafNode` field and a pointer to the internal can be cast to a pointer
 * to its leaf portion. In Kotlin a Leaf-typed reference is allowed to point
 * at an [InternalNode] instance, and the runtime `height` field
 * disambiguates when it matters.
 */
internal open class LeafNode<K, V> {
    /** We want to be covariant in `K` and `V`. */
    var parent: InternalNode<K, V>? = null

    /**
     * This node's index into the parent node's `edges` array.
     * `*node.parent.edges[node.parent_idx]` should be the same thing as `node`.
     * This is only guaranteed to be initialized when `parent` is non-null.
     */
    var parentIdx: Int = 0

    /** The number of keys and values this node stores. */
    var len: Int = 0

    /**
     * The arrays storing the actual data of the node. Only the first `len` elements of each
     * array are initialized and valid.
     *
     * Translation note: Rust uses `[MaybeUninit<K>; CAPACITY]`. In Kotlin this is an
     * `arrayOfNulls<K>(CAPACITY)`. Indexed access in code paths that "know" the slot is
     * initialised uses `!!` with a `// SAFETY: <invariant>` comment immediately above.
     */
    val keys: Array<Any?> = arrayOfNulls<Any?>(CAPACITY)
    val vals: Array<Any?> = arrayOfNulls<Any?>(CAPACITY)

    companion object {
        /**
         * Initializes a new `LeafNode` in-place — in Kotlin this is just the
         * default constructor, since `parent`, `len`, `keys`, and `vals` are
         * initialised by the field initialisers above.
         *
         * Creates a new `LeafNode`. Upstream returns `Box<Self, A>`; in Kotlin
         * the GC handles the heap and we just construct the object.
         */
        fun <K, V> new(): LeafNode<K, V> = LeafNode()
    }
}

/**
 * The underlying representation of internal nodes. As with `LeafNode`s, these should be hidden
 * behind `BoxedNode`s to prevent dropping uninitialized keys and values. Any pointer to an
 * `InternalNode` can be directly cast to a pointer to the underlying `LeafNode` portion of the
 * node, allowing code to act on leaf and internal nodes generically without having to even check
 * which of the two a pointer is pointing at. This property is enabled by the use of `repr(C)`
 * upstream; in Kotlin it is enabled by class inheritance ([InternalNode] extends [LeafNode]).
 */
internal class InternalNode<K, V> : LeafNode<K, V>() {
    /**
     * The pointers to the children of this node. `len + 1` of these are considered
     * initialized and valid, except that near the end, while the tree is held
     * through borrow type `Dying`, some of these pointers are dangling.
     */
    val edges: Array<LeafNode<K, V>?> = arrayOfNulls<LeafNode<K, V>?>(2 * B)

    companion object {
        /**
         * Creates a new `InternalNode`.
         *
         * # Safety
         * An invariant of internal nodes is that they have at least one
         * initialized and valid edge. This function does not set up
         * such an edge.
         */
        // SAFETY: caller must set up at least one edge before exposing the node.
        fun <K, V> new(): InternalNode<K, V> = InternalNode()
    }
}

/**
 * A managed, non-null pointer to a node. This is either an owned pointer to
 * `LeafNode<K, V>` or an owned pointer to `InternalNode<K, V>`.
 *
 * However, `BoxedNode` contains no information as to which of the two types
 * of nodes it actually contains, and, partially due to this lack of information,
 * is not a separate type and has no destructor.
 *
 * In Kotlin this becomes simply [LeafNode]: an [InternalNode] is a subclass
 * of [LeafNode], so a `LeafNode` reference can hold either kind of node.
 */
internal typealias BoxedNode<K, V> = LeafNode<K, V>

/**
 * A reference to a node.
 *
 * This type has a number of parameters that control how it acts:
 * - `BorrowType`: A dummy type that describes the kind of borrow and carries a lifetime.
 *    - When this is `Immut`, the `NodeRef` acts roughly like an immutable reference.
 *    - When this is `ValMut`, the `NodeRef` acts roughly like an immutable reference
 *      with respect to keys and tree structure, but also allows many
 *      mutable references to values throughout the tree to coexist.
 *    - When this is `Mut`, the `NodeRef` acts roughly like an exclusive reference,
 *      although insert methods allow a mutable pointer to a value to coexist.
 *    - When this is `Owned`, the `NodeRef` acts roughly like an owning pointer,
 *      but does not have a destructor, and must be cleaned up manually.
 *    - When this is `Dying`, the `NodeRef` still acts roughly like an owning pointer,
 *      but has methods to destroy the tree bit by bit, and ordinary methods,
 *      while not marked as unsafe to call, can invoke UB if called incorrectly.
 *   Since any `NodeRef` allows navigating through the tree, `BorrowType`
 *   effectively applies to the entire tree, not just to the node itself.
 * - `K` and `V`: These are the types of keys and values stored in the nodes.
 * - `Type`: This can be `Leaf`, `Internal`, or `LeafOrInternal`. When this is
 *   `Leaf`, the `NodeRef` points to a leaf node, when this is `Internal` the
 *   `NodeRef` points to an internal node, and when this is `LeafOrInternal` the
 *   `NodeRef` could be pointing to either type of node.
 *   `Type` is named `NodeType` when used outside `NodeRef`.
 *
 * Translation notes:
 * - The `'a` lifetime parameter from upstream drops; Kotlin uses GC and has no
 *   borrow checker to satisfy.
 * - Both type parameters are kept as phantom-style markers (no `PhantomData`
 *   needed; Kotlin variance handles what `PhantomData` was conveying).
 * - Where upstream `force()` discriminates between Leaf and Internal at the
 *   type level (matching on `match height { 0 => Leaf, _ => Internal }`),
 *   the Kotlin port discriminates at runtime via the same `height` field.
 */
internal class NodeRef<BorrowType, K, V, Type> internal constructor(
    /**
     * The number of levels that the node and the level of leaves are apart, a
     * constant of the node that cannot be entirely described by `Type`, and that
     * the node itself does not store. We only need to store the height of the root
     * node, and derive every other node's height from it.
     * Must be zero if `Type` is `Leaf` and non-zero if `Type` is `Internal`.
     */
    var height: Int,
    /**
     * The pointer to the leaf or internal node. The definition of `InternalNode`
     * ensures that the pointer is valid either way (an [InternalNode] is also a
     * [LeafNode] by inheritance).
     */
    var node: LeafNode<K, V>,
) {
    companion object {
        // ---- new_leaf / new_internal --------------------------------------

        /**
         * Mirrors `NodeRef::<Owned, K, V, Leaf>::new_leaf` (upstream node.rs,
         * line 224). Returns an Owned NodeRef wrapping a freshly allocated leaf.
         */
        fun <K, V> newLeaf(): NodeRef<Marker.Owned, K, V, Marker.Leaf> {
            return fromNewLeaf(LeafNode.new<K, V>())
        }

        private fun <K, V> fromNewLeaf(leaf: LeafNode<K, V>): NodeRef<Marker.Owned, K, V, Marker.Leaf> {
            // The allocator must be dropped, not leaked. (No allocator in Kotlin; GC handles it.)
            return NodeRef(height = 0, node = leaf)
        }

        /** Creates a new internal (height > 0) `NodeRef`. */
        fun <K, V> newInternal(child: Root<K, V>): NodeRef<Marker.Owned, K, V, Marker.Internal> {
            // SAFETY: we set up edges[0] before exposing the node; satisfies InternalNode.new's invariant.
            val newNode = InternalNode.new<K, V>()
            newNode.edges[0] = child.node
            // NonZero::new(child.height + 1).unwrap() — child.height + 1 is always > 0.
            return fromNewInternal(newNode, child.height + 1)
        }

        /** Creates a new internal (height > 0) `NodeRef` from an existing internal node. */
        private fun <K, V> fromNewInternal(
            internal: InternalNode<K, V>,
            height: Int,
        ): NodeRef<Marker.Owned, K, V, Marker.Internal> {
            // The allocator must be dropped, not leaked. (No allocator in Kotlin; GC handles it.)
            val self = NodeRef<Marker.Owned, K, V, Marker.Internal>(height = height, node = internal)
            self.borrowMut().correctAllChildrensParentLinks()
            return self
        }

        /** Unpack a node reference that was packed as `NodeRef::parent`. */
        internal fun <BorrowType, K, V> fromInternal(
            node: InternalNode<K, V>,
            height: Int,
        ): NodeRef<BorrowType, K, V, Marker.Internal> {
            check(height > 0) // debug_assert!(height > 0)
            return NodeRef(height = height, node = node)
        }
    }

    // ---- core accessors (any BorrowType, any Type) ------------------------

    /**
     * Finds the length of the node. This is the number of keys or values.
     * The number of edges is `len() + 1`.
     */
    fun len(): Int {
        // Crucially, we only access the `len` field here. If BorrowType is Marker.ValMut,
        // there might be outstanding mutable references to values that we must not invalidate.
        return node.len
    }

    /**
     * Returns the number of levels that the node and leaves are apart. Zero
     * height means the node is a leaf itself.
     */
    fun height(): Int = height

    /** Temporarily takes out another, immutable reference to the same node. */
    fun reborrow(): NodeRef<Marker.Immut, K, V, Type> {
        return NodeRef(height = height, node = node)
    }

    /**
     * Could be a public implementation of PartialEq, but only used in this module.
     */
    fun structuralEq(other: NodeRef<BorrowType, K, V, Type>): Boolean {
        return if (node === other.node) {
            check(height == other.height) // debug_assert_eq!(height, other.height)
            true
        } else {
            false
        }
    }

    // ---- forget_type ------------------------------------------------------

    /**
     * Removes any static information asserting that this node is a `Leaf`
     * (or `Internal`) node. Mirrors both `forget_type` impls in upstream.
     */
    fun forgetType(): NodeRef<BorrowType, K, V, Marker.LeafOrInternal> {
        return NodeRef(height = height, node = node)
    }
}

// =====================================================================
// NodeRef: methods restricted by BorrowType / Type
// =====================================================================
//
// Upstream achieves the static restrictions via `impl<...> NodeRef<...>`
// blocks; in Kotlin we use top-level extension functions guarded by
// generic constraints (when applicable) or by relying on the call site
// to pick the right type-parameter instantiation. The type system here
// is weaker than Rust's: methods that upstream restricts to e.g.
// `BorrowType: marker::BorrowType` are exposed as extension functions
// that take the runtime constraint as a generic bound where possible.

/**
 * Finds the parent of the current node. Returns the handle if the current
 * node actually has a parent, where `handle` points to the edge of the parent
 * that points to the current node. Returns `null` if the current node has
 * no parent. Upstream's `Result<Ok, Err(self)>` collapses to nullable since
 * `self` is recoverable from the caller's binding (it was passed by value).
 *
 * The method name assumes you picture trees with the root node on top.
 *
 * `edge.descend().ascend().unwrap()` and `node.ascend().unwrap().descend()` should
 * both, upon success, do nothing.
 *
 * Translation note: upstream signature is
 * `fn ascend(self) -> Result<Handle<...>, Self>`. We return a Kotlin sealed
 * class `AscendResult` so the caller can recover `self` on the failure
 * branch (mirrors the `Err(self)` shape).
 */
internal sealed class AscendResult<BorrowType, K, V> {
    data class Ok<BorrowType, K, V>(
        val handle: Handle<NodeRef<BorrowType, K, V, Marker.Internal>, Marker.Edge>,
    ) : AscendResult<BorrowType, K, V>()

    data class Err<BorrowType, K, V>(
        val node: NodeRef<BorrowType, K, V, Marker.LeafOrInternal>,
    ) : AscendResult<BorrowType, K, V>()
}

internal fun <BorrowType : Marker.BorrowType, K, V, Type> NodeRef<BorrowType, K, V, Type>.ascend():
    AscendResult<BorrowType, K, V> {
    // const { assert!(BorrowType::TRAVERSAL_PERMIT) } — Kotlin has no compile-time
    // discrimination on type parameters; the upstream Owned impl sets
    // TRAVERSAL_PERMIT = false to forbid traversal. In practice ascend()
    // is never called on Owned NodeRefs by the rest of the port.
    val parent = node.parent
    return if (parent != null) {
        AscendResult.Ok(
            Handle(
                node = NodeRef.fromInternal<BorrowType, K, V>(parent, height + 1),
                idx = node.parentIdx,
            ),
        )
    } else {
        // The Err branch upstream returns `self`. Because `Type` may be `Leaf`
        // or `Internal` or `LeafOrInternal`, we widen to `LeafOrInternal` here
        // for the recovery handle.
        AscendResult.Err(NodeRef(height = height, node = node))
    }
}

internal fun <BorrowType : Marker.BorrowType, K, V, Type> NodeRef<BorrowType, K, V, Type>.firstEdge():
    Handle<NodeRef<BorrowType, K, V, Type>, Marker.Edge> {
    // SAFETY: 0 is a valid edge index for any node.
    return Handle.newEdge(this, 0)
}

internal fun <BorrowType : Marker.BorrowType, K, V, Type> NodeRef<BorrowType, K, V, Type>.lastEdge():
    Handle<NodeRef<BorrowType, K, V, Type>, Marker.Edge> {
    val len = this.len()
    // SAFETY: `len` is a valid edge index for any node (edges range 0..=len).
    return Handle.newEdge(this, len)
}

/** Note that `self` must be nonempty. */
internal fun <BorrowType : Marker.BorrowType, K, V, Type> NodeRef<BorrowType, K, V, Type>.firstKv():
    Handle<NodeRef<BorrowType, K, V, Type>, Marker.KV> {
    val len = this.len()
    check(len > 0) // assert!(len > 0)
    // SAFETY: 0 < len.
    return Handle.newKv(this, 0)
}

/** Note that `self` must be nonempty. */
internal fun <BorrowType : Marker.BorrowType, K, V, Type> NodeRef<BorrowType, K, V, Type>.lastKv():
    Handle<NodeRef<BorrowType, K, V, Type>, Marker.KV> {
    val len = this.len()
    check(len > 0) // assert!(len > 0)
    // SAFETY: len - 1 < len.
    return Handle.newKv(this, len - 1)
}

// ---- NodeRef<Immut, ..., Type> ------------------------------------------

/**
 * Borrows a view into the keys stored in the node.
 *
 * Upstream returns `&[K]`; in Kotlin we return a [List] view backed by the
 * internal storage. This list has size `len()` and indexed access reads
 * the (initialised) slot. `Search.kt` calls this method.
 */
internal fun <K, V, Type> NodeRef<Marker.Immut, K, V, Type>.keys(): List<K> {
    val n = node.len
    // SAFETY: `keys[0..len]` are all initialised by node-shape invariants.
    return object : AbstractList<K>() {
        override val size: Int get() = n
        override fun get(index: Int): K {
            if (index < 0 || index >= n) throw IndexOutOfBoundsException("index $index out of bounds [0, $n)")
            // SAFETY: index is in 0..len, slot is initialised.
            @Suppress("UNCHECKED_CAST")
            return node.keys[index] as K
        }
    }
}

/**
 * Generic `keys` accessor — the upstream `pub(super) fn keys(&self) -> &[K]`
 * is restricted to `Immut`, but Search.kt calls `keys()` on
 * `NodeRef<BorrowType, K, V, Type>` after a `reborrow()`, which yields an
 * `Immut` borrow. We expose this convenience with the same generic
 * BorrowType so Search.kt's `node.reborrow().keys()` resolves identically
 * to upstream.
 */
internal fun <BorrowType, K, V, Type> NodeRef<BorrowType, K, V, Type>.keys(): List<K> {
    val n = node.len
    return object : AbstractList<K>() {
        override val size: Int get() = n
        override fun get(index: Int): K {
            if (index < 0 || index >= n) throw IndexOutOfBoundsException("index $index out of bounds [0, $n)")
            // SAFETY: index is in 0..len, slot is initialised.
            @Suppress("UNCHECKED_CAST")
            return node.keys[index] as K
        }
    }
}

// ---- NodeRef<Dying, ...> -----------------------------------------------

/**
 * Similar to `ascend`, gets a reference to a node's parent node, but also
 * deallocates the current node in the process. Upstream is `unsafe` because
 * the current node will still be accessible despite being deallocated; in
 * Kotlin the GC keeps the current node alive as long as anyone holds a
 * reference, so the function is safe — we just drop the link.
 *
 * Naming: the rename `dying_*` -> bare name doesn't apply here because the
 * function is already named `deallocate_and_ascend` upstream (no leading
 * `dying_`). The body, however, has its `alloc.deallocate(...)` call
 * dissolved (GC supersedes manual deallocation).
 */
internal fun <K, V> NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>.deallocateAndAscend():
    Handle<NodeRef<Marker.Dying, K, V, Marker.Internal>, Marker.Edge>? {
    val ret = when (val r = this.ascend()) {
        is AscendResult.Ok -> r.handle
        is AscendResult.Err -> null
    }
    // SAFETY: alloc.deallocate(...) — dissolved; GC handles deallocation.
    return ret
}

// ---- NodeRef<DormantMut, ...> -------------------------------------------

/**
 * Revert to the unique borrow initially captured.
 *
 * # Safety
 * The reborrow must have ended, i.e., the reference returned by `new` and
 * all pointers and references derived from it, must not be used anymore.
 */
internal fun <K, V, Type> NodeRef<Marker.DormantMut, K, V, Type>.awaken():
    NodeRef<Marker.Mut, K, V, Type> {
    return NodeRef(height = height, node = node)
}

// ---- NodeRef<Mut, ...> --------------------------------------------------

/**
 * Temporarily takes out another mutable reference to the same node. Beware,
 * as this method is very dangerous, doubly so since it might not immediately
 * appear dangerous.
 */
internal fun <K, V, Type> NodeRef<Marker.Mut, K, V, Type>.reborrowMut():
    NodeRef<Marker.Mut, K, V, Type> {
    // SAFETY: caller-enforced uniqueness; upstream marks the function `unsafe`.
    return NodeRef(height = height, node = node)
}

/** Returns a dormant copy of this node which can be reawakened later. */
internal fun <K, V, Type> NodeRef<Marker.Mut, K, V, Type>.dormant():
    NodeRef<Marker.DormantMut, K, V, Type> {
    return NodeRef(height = height, node = node)
}

// ---- NodeRef<Owned, ..., Type> -----------------------------------------

/**
 * Mutably borrows the owned root node. Unlike `reborrowMut`, this is safe
 * because the return value cannot be used to destroy the root, and there
 * cannot be other references to the tree.
 */
internal fun <K, V, Type> NodeRef<Marker.Owned, K, V, Type>.borrowMut():
    NodeRef<Marker.Mut, K, V, Type> {
    return NodeRef(height = height, node = node)
}

/** Slightly mutably borrows the owned root node. */
internal fun <K, V, Type> NodeRef<Marker.Owned, K, V, Type>.borrowValmut():
    NodeRef<Marker.ValMut, K, V, Type> {
    return NodeRef(height = height, node = node)
}

/**
 * Irreversibly transitions to a reference that permits traversal and offers
 * destructive methods and little else.
 */
internal fun <K, V, Type> NodeRef<Marker.Owned, K, V, Type>.intoDying():
    NodeRef<Marker.Dying, K, V, Type> {
    return NodeRef(height = height, node = node)
}

// ---- Owned, LeafOrInternal: tree-shape mutators -------------------------

/** Returns a new owned tree, with its own root node that is initially empty. */
internal fun <K, V> newOwnedTree(): Root<K, V> {
    return NodeRef.newLeaf<K, V>().forgetType()
}

/**
 * Adds a new internal node with a single edge pointing to the previous root node,
 * make that new node the root node, and return it. This increases the height by 1
 * and is the opposite of `popInternalLevel`.
 */
internal fun <K, V> NodeRef<Marker.Owned, K, V, Marker.LeafOrInternal>.pushInternalLevel():
    NodeRef<Marker.Mut, K, V, Marker.Internal> {
    // takeMut(self, |old_root| NodeRef::new_internal(old_root, alloc).forget_type())
    // In Kotlin we read self, build the new root, and write back fields.
    val oldRoot: Root<K, V> = NodeRef(height = height, node = node)
    val newRoot = NodeRef.newInternal(oldRoot).forgetType()
    height = newRoot.height
    node = newRoot.node

    // `self.borrow_mut()`, except that we just forgot we're internal now.
    return NodeRef(height = height, node = node)
}

/**
 * Removes the internal root node, using its first child as the new root node.
 * As it is intended only to be called when the root node has only one child,
 * no cleanup is done on any of the keys, values and other children.
 * This decreases the height by 1 and is the opposite of `pushInternalLevel`.
 *
 * Does not invalidate any handles or references pointing into the subtree
 * rooted at the first child of `self`.
 *
 * Panics if there is no internal level, i.e., if the root node is a leaf.
 */
internal fun <K, V> NodeRef<Marker.Owned, K, V, Marker.LeafOrInternal>.popInternalLevel() {
    check(height > 0) // assert!(self.height > 0)

    // SAFETY: we asserted to be internal.
    val internalSelf: NodeRef<Marker.Mut, K, V, Marker.Internal> =
        this.borrowMut().castToInternalUnchecked()
    val internalNode: InternalNode<K, V> = internalSelf.asInternalMut()
    // SAFETY: the first edge is always initialized.
    node = internalNode.edges[0]!!
    height -= 1
    this.clearParentLink()
    // SAFETY: alloc.deallocate(top, Layout::new::<InternalNode>()) — dissolved (GC).
}

/** Clears the root's link to its parent edge. */
private fun <K, V> NodeRef<Marker.Owned, K, V, Marker.LeafOrInternal>.clearParentLink() {
    val rootNode = this.borrowMut()
    val leaf = rootNode.asLeafMut()
    leaf.parent = null
}

// ---- NodeRef<Mut, ..., LeafOrInternal>: parent linkage -----------------

/**
 * Sets the node's link to its parent edge,
 * without invalidating other references to the node.
 */
internal fun <K, V> NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>.setParentLink(
    parent: InternalNode<K, V>,
    parentIdx: Int,
) {
    val leaf = node
    leaf.parent = parent
    leaf.parentIdx = parentIdx
}

// ---- NodeRef<Mut, ..., Internal>: edges + child links -------------------

/**
 * Borrows exclusive access to the data of an internal node.
 *
 * Upstream returns `&mut InternalNode<K, V>`; in Kotlin we return the
 * `InternalNode` reference directly. The `as` downcast mirrors the upstream
 * `as_internal_ptr` cast (which is sound by the static type guarantee that
 * the node is `Marker.Internal`, i.e. the runtime instance is an
 * `InternalNode`).
 */
internal fun <K, V> NodeRef<Marker.Mut, K, V, Marker.Internal>.asInternalMut(): InternalNode<K, V> {
    // SAFETY: the static node type is `Internal`, so the runtime instance is an InternalNode.
    return node as InternalNode<K, V>
}

/**
 * Borrows exclusive access to the leaf portion of a leaf or internal node.
 */
internal fun <K, V, Type> NodeRef<Marker.Mut, K, V, Type>.asLeafMut(): LeafNode<K, V> {
    // SAFETY: we have exclusive access to the entire node.
    return node
}

/**
 * Offers exclusive access to the leaf portion of a leaf or internal node.
 * Upstream `into_leaf_mut` consumed `self`; in Kotlin we just return the
 * underlying node reference.
 */
internal fun <K, V, Type> NodeRef<Marker.Mut, K, V, Type>.intoLeafMut(): LeafNode<K, V> {
    return node
}

/**
 * Borrows exclusive access to the length of the node. Upstream returns
 * `&mut u16`; in Kotlin there's no equivalent of a mutable reference to a
 * field — we expose getter/setter helpers instead. Callers do
 * `setLen(getLen() + 1)` where Rust does `*len += 1`.
 *
 * For ergonomics we provide [setLen] / [incLen].
 */
internal fun <K, V, Type> NodeRef<Marker.Mut, K, V, Type>.setLen(newLen: Int) {
    asLeafMut().len = newLen
}

internal fun <K, V, Type> NodeRef<Marker.Mut, K, V, Type>.incLen(by: Int = 1) {
    asLeafMut().len += by
}

// ---- NodeRef<Dying, ..., Type> -----------------------------------------

/**
 * Borrows exclusive access to the leaf portion of a dying leaf or internal node.
 */
internal fun <K, V, Type> NodeRef<Marker.Dying, K, V, Type>.asLeafDying(): LeafNode<K, V> {
    // SAFETY: we have exclusive access to the entire node.
    return node
}

// ---- NodeRef<Mut, ..., Type>: key/val area accessors -------------------
//
// Upstream uses `SliceIndex` so a single `key_area_mut(idx)` /
// `key_area_mut(start..end)` / `key_area_mut(..end)` covers all forms.
// Kotlin doesn't have a slice-index trait, so we expose three small
// helpers per area: a single-slot accessor and bulk shift/insert/remove
// helpers operate directly on the underlying arrays via the
// `slice_*` free functions below.

/**
 * Writes [value] into the key slot at [idx].
 *
 * # Safety
 * `idx` is in bounds of `0..CAPACITY`.
 */
internal fun <K, V, Type> NodeRef<Marker.Mut, K, V, Type>.writeKeyArea(idx: Int, value: K) {
    // SAFETY: idx is in 0..CAPACITY by caller contract.
    asLeafMut().keys[idx] = value
}

/**
 * Writes [value] into the value slot at [idx].
 *
 * # Safety
 * `idx` is in bounds of `0..CAPACITY`.
 */
internal fun <K, V, Type> NodeRef<Marker.Mut, K, V, Type>.writeValArea(idx: Int, value: V) {
    // SAFETY: idx is in 0..CAPACITY by caller contract.
    asLeafMut().vals[idx] = value
}

/**
 * Reads (and conceptually moves out of) the key slot at [idx].
 *
 * Mirrors `key_area_mut(idx).assume_init_read()`. In Kotlin the slot is
 * not nulled out (GC will reclaim once no longer reachable from `len`-area
 * scope), but the caller treats the slot as logically uninitialised.
 *
 * # Safety
 * `idx` is in bounds of `0..len`, slot is initialised.
 */
@Suppress("UNCHECKED_CAST")
internal fun <K, V, Type> NodeRef<Marker.Mut, K, V, Type>.readKeyArea(idx: Int): K {
    // SAFETY: caller ensures idx < len so slot is initialised.
    return asLeafMut().keys[idx] as K
}

@Suppress("UNCHECKED_CAST")
internal fun <K, V, Type> NodeRef<Marker.Mut, K, V, Type>.readValArea(idx: Int): V {
    // SAFETY: caller ensures idx < len so slot is initialised.
    return asLeafMut().vals[idx] as V
}

/**
 * Writes [edge] into the edge slot at [idx]. Internal-node only.
 *
 * # Safety
 * `idx` is in bounds of `0..CAPACITY + 1`.
 */
internal fun <K, V> NodeRef<Marker.Mut, K, V, Marker.Internal>.writeEdgeArea(
    idx: Int,
    edge: BoxedNode<K, V>,
) {
    // SAFETY: idx is in 0..CAPACITY+1 by caller contract.
    asInternalMut().edges[idx] = edge
}

/**
 * Reads (and conceptually moves out of) the edge slot at [idx].
 *
 * # Safety
 * `idx` is in bounds of `0..len + 1`, slot is initialised.
 */
internal fun <K, V> NodeRef<Marker.Mut, K, V, Marker.Internal>.readEdgeArea(idx: Int): BoxedNode<K, V> {
    // SAFETY: caller ensures idx <= len so slot is initialised.
    return asInternalMut().edges[idx]!!
}

// ---- NodeRef<ValMut, ...> ----------------------------------------------

/**
 * # Safety
 * - The node has more than `idx` initialized elements.
 *
 * Upstream returns `(&'a K, &'a mut V)`. In Kotlin we return [Pair] and the
 * caller can write back via [writeValArea] if desired. Reading the value is
 * the only operation Search.kt-and-below need.
 */
@Suppress("UNCHECKED_CAST")
internal fun <K, V, Type> NodeRef<Marker.ValMut, K, V, Type>.intoKeyValMutAt(idx: Int): Pair<K, V> {
    // SAFETY: idx < len, slots are initialised.
    val key = node.keys[idx] as K
    val v = node.vals[idx] as V
    return Pair(key, v)
}

// ---- NodeRef<Mut, ..., Internal>: parent-link correctness --------------

/**
 * # Safety
 * Every item in `range` is a valid edge index for the node.
 */
internal fun <K, V> NodeRef<Marker.Mut, K, V, Marker.Internal>.correctChildrensParentLinks(
    range: IntRange,
) {
    for (i in range) {
        check(i <= len()) // debug_assert!(i <= self.len())
        // SAFETY: caller-provided range items are valid edge indices.
        Handle.newEdge(this.reborrowMut(), i).correctParentLink()
    }
}

internal fun <K, V> NodeRef<Marker.Mut, K, V, Marker.Internal>.correctAllChildrensParentLinks() {
    val len = this.len()
    // SAFETY: 0..=len is the full range of valid edge indices.
    this.correctChildrensParentLinks(0..len)
}

// ---- NodeRef<Mut, ..., Leaf>: push --------------------------------------

/**
 * Adds a key-value pair to the end of the node, and returns
 * a handle to the inserted value.
 *
 * # Safety
 * The returned handle has an unbound lifetime. (Irrelevant in Kotlin; GC.)
 */
internal fun <K, V> NodeRef<Marker.Mut, K, V, Marker.Leaf>.pushWithHandle(
    key: K,
    value: V,
): Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.KV> {
    val idx = this.len()
    check(idx < CAPACITY) // assert!(idx < CAPACITY)
    setLen(idx + 1)
    // SAFETY: idx < CAPACITY by the assert above.
    this.writeKeyArea(idx, key)
    this.writeValArea(idx, value)
    return Handle.newKv(NodeRef(height = height, node = node), idx)
}

/**
 * Adds a key-value pair to the end of the node, and returns
 * the (newly-inserted) value. Upstream returns `*mut V`; we return the
 * value the caller passed in, since GC keeps it reachable through the node.
 */
internal fun <K, V> NodeRef<Marker.Mut, K, V, Marker.Leaf>.push(key: K, value: V): V {
    // SAFETY: The unbound handle is no longer accessible.
    val handle = this.pushWithHandle(key, value)
    return handle.intoValMut()
}

// ---- NodeRef<Mut, ..., Internal>: push ----------------------------------

/**
 * Adds a key-value pair, and an edge to go to the right of that pair,
 * to the end of the node.
 */
internal fun <K, V> NodeRef<Marker.Mut, K, V, Marker.Internal>.push(
    key: K,
    value: V,
    edge: Root<K, V>,
) {
    check(edge.height == this.height - 1) // assert!(edge.height == self.height - 1)

    val idx = this.len()
    check(idx < CAPACITY) // assert!(idx < CAPACITY)
    setLen(idx + 1)
    // SAFETY: idx < CAPACITY (asserted above) and idx + 1 <= CAPACITY.
    this.writeKeyArea(idx, key)
    this.writeValArea(idx, value)
    this.writeEdgeArea(idx + 1, edge.node)
    Handle.newEdge(this.reborrowMut(), idx + 1).correctParentLink()
}

// ---- NodeRef<...>: force --------------------------------------------------

/**
 * Checks whether a node is an `Internal` node or a `Leaf` node.
 *
 * Upstream uses static type-tag dispatch encoded in the height field; the
 * Kotlin port does the same — height==0 → Leaf, height>0 → Internal.
 */
internal fun <BorrowType, K, V> NodeRef<BorrowType, K, V, Marker.LeafOrInternal>.force():
    ForceResult<NodeRef<BorrowType, K, V, Marker.Leaf>, NodeRef<BorrowType, K, V, Marker.Internal>> {
    return if (height == 0) {
        ForceResult.Leaf(NodeRef(height = height, node = node))
    } else {
        ForceResult.Internal(NodeRef(height = height, node = node))
    }
}

/** Unsafely asserts to the compiler the static information that this node is a `Leaf`. */
internal fun <K, V> NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>.castToLeafUnchecked():
    NodeRef<Marker.Mut, K, V, Marker.Leaf> {
    check(height == 0) // debug_assert!(self.height == 0)
    return NodeRef(height = height, node = node)
}

/** Unsafely asserts to the compiler the static information that this node is an `Internal`. */
internal fun <K, V> NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>.castToInternalUnchecked():
    NodeRef<Marker.Mut, K, V, Marker.Internal> {
    check(height > 0) // debug_assert!(self.height > 0)
    return NodeRef(height = height, node = node)
}

// =====================================================================
// Root type alias
// =====================================================================

/** The root node of an owned tree. */
internal typealias Root<K, V> = NodeRef<Marker.Owned, K, V, Marker.LeafOrInternal>

// =====================================================================
// Handle
// =====================================================================

/**
 * A reference to a specific key-value pair or edge within a node. The `Node` parameter
 * must be a `NodeRef`, while the `Type` can either be [Marker.KV] (signifying a handle on
 * a key-value pair) or [Marker.Edge] (signifying a handle on an edge).
 *
 * Note that even `Leaf` nodes can have `Edge` handles. Instead of representing a pointer to
 * a child node, these represent the spaces where child pointers would go between the
 * key-value pairs. For example, in a node with length 2, there would be 3 possible edge
 * locations - one to the left of the node, one between the two pairs, and one at the right
 * of the node.
 */
internal class Handle<Node, Type> internal constructor(
    val node: Node,
    val idx: Int,
) {
    /** Retrieves the node that contains the edge or key-value pair this handle points to. */
    fun intoNode(): Node = node

    /** Returns the position of this handle in the node. */
    fun idx(): Int = idx

    companion object {
        // ---- KV constructors ------------------------------------------------

        /**
         * Creates a new handle to a key-value pair in `node`.
         * Unsafe upstream because the caller must ensure that `idx < node.len()`.
         */
        fun <BorrowType, K, V, NodeType> newKv(
            node: NodeRef<BorrowType, K, V, NodeType>,
            idx: Int,
        ): Handle<NodeRef<BorrowType, K, V, NodeType>, Marker.KV> {
            check(idx < node.len()) // debug_assert!(idx < node.len())
            return Handle(node, idx)
        }

        // ---- Edge constructors ----------------------------------------------

        /**
         * Creates a new handle to an edge in `node`.
         * Unsafe upstream because the caller must ensure that `idx <= node.len()`.
         */
        fun <BorrowType, K, V, NodeType> newEdge(
            node: NodeRef<BorrowType, K, V, NodeType>,
            idx: Int,
        ): Handle<NodeRef<BorrowType, K, V, NodeType>, Marker.Edge> {
            check(idx <= node.len()) // debug_assert!(idx <= node.len())
            return Handle(node, idx)
        }
    }
}

// ---- Handle KV: edges, equality ----------------------------------------

internal fun <BorrowType, K, V, NodeType> Handle<NodeRef<BorrowType, K, V, NodeType>, Marker.KV>.leftEdge():
    Handle<NodeRef<BorrowType, K, V, NodeType>, Marker.Edge> {
    return Handle.newEdge(node, idx)
}

internal fun <BorrowType, K, V, NodeType> Handle<NodeRef<BorrowType, K, V, NodeType>, Marker.KV>.rightEdge():
    Handle<NodeRef<BorrowType, K, V, NodeType>, Marker.Edge> {
    return Handle.newEdge(node, idx + 1)
}

/**
 * Structural equality for handles. Upstream is `impl PartialEq` on
 * `Handle<NodeRef<...>, ...>`. We expose this as an extension function
 * rather than overriding [Object.equals] so we don't impose equality on
 * generic [Handle] (whose `Node` may not itself be a [NodeRef]).
 */
internal fun <BorrowType, K, V, NodeType, HandleType>
Handle<NodeRef<BorrowType, K, V, NodeType>, HandleType>.structuralEq(
    other: Handle<NodeRef<BorrowType, K, V, NodeType>, HandleType>,
): Boolean {
    return node.structuralEq(other.node) && idx == other.idx
}

// ---- Handle: reborrow / dormant / awaken --------------------------------

internal fun <BorrowType, K, V, NodeType, HandleType>
Handle<NodeRef<BorrowType, K, V, NodeType>, HandleType>.reborrow():
    Handle<NodeRef<Marker.Immut, K, V, NodeType>, HandleType> {
    return Handle(node.reborrow(), idx)
}

internal fun <K, V, NodeType, HandleType>
Handle<NodeRef<Marker.Mut, K, V, NodeType>, HandleType>.reborrowMut():
    Handle<NodeRef<Marker.Mut, K, V, NodeType>, HandleType> {
    // SAFETY: caller-enforced uniqueness, mirrors NodeRef.reborrowMut.
    return Handle(node.reborrowMut(), idx)
}

internal fun <K, V, NodeType, HandleType>
Handle<NodeRef<Marker.Mut, K, V, NodeType>, HandleType>.dormant():
    Handle<NodeRef<Marker.DormantMut, K, V, NodeType>, HandleType> {
    return Handle(node.dormant(), idx)
}

internal fun <K, V, NodeType, HandleType>
Handle<NodeRef<Marker.DormantMut, K, V, NodeType>, HandleType>.awaken():
    Handle<NodeRef<Marker.Mut, K, V, NodeType>, HandleType> {
    return Handle(node.awaken(), idx)
}

// ---- Handle Edge: left_kv / right_kv ------------------------------------

/**
 * Upstream returns `Result<Handle<..., KV>, Self>`. We translate as a sealed
 * class wrapper similar to [AscendResult].
 */
internal sealed class EdgeKvResult<BorrowType, K, V, NodeType> {
    data class Ok<BorrowType, K, V, NodeType>(
        val handle: Handle<NodeRef<BorrowType, K, V, NodeType>, Marker.KV>,
    ) : EdgeKvResult<BorrowType, K, V, NodeType>()

    data class Err<BorrowType, K, V, NodeType>(
        val handle: Handle<NodeRef<BorrowType, K, V, NodeType>, Marker.Edge>,
    ) : EdgeKvResult<BorrowType, K, V, NodeType>()
}

internal fun <BorrowType, K, V, NodeType>
Handle<NodeRef<BorrowType, K, V, NodeType>, Marker.Edge>.leftKv():
    EdgeKvResult<BorrowType, K, V, NodeType> {
    return if (idx > 0) {
        // SAFETY: idx > 0 so idx - 1 >= 0; idx <= len so idx - 1 < len.
        EdgeKvResult.Ok(Handle.newKv(node, idx - 1))
    } else {
        EdgeKvResult.Err(this)
    }
}

internal fun <BorrowType, K, V, NodeType>
Handle<NodeRef<BorrowType, K, V, NodeType>, Marker.Edge>.rightKv():
    EdgeKvResult<BorrowType, K, V, NodeType> {
    return if (idx < node.len()) {
        // SAFETY: idx < len so idx is a valid KV index.
        EdgeKvResult.Ok(Handle.newKv(node, idx))
    } else {
        EdgeKvResult.Err(this)
    }
}

// =====================================================================
// LeftOrRight
// =====================================================================

internal sealed class LeftOrRight<T> {
    data class Left<T>(val value: T) : LeftOrRight<T>()
    data class Right<T>(val value: T) : LeftOrRight<T>()
}

/**
 * Given an edge index where we want to insert into a node filled to capacity,
 * computes a sensible KV index of a split point and where to perform the insertion.
 */
private fun splitpoint(edgeIdx: Int): Pair<Int, LeftOrRight<Int>> {
    check(edgeIdx <= CAPACITY) // debug_assert!(edge_idx <= CAPACITY)
    return when {
        edgeIdx < EDGE_IDX_LEFT_OF_CENTER -> Pair(KV_IDX_CENTER - 1, LeftOrRight.Left(edgeIdx))
        edgeIdx == EDGE_IDX_LEFT_OF_CENTER -> Pair(KV_IDX_CENTER, LeftOrRight.Left(edgeIdx))
        edgeIdx == EDGE_IDX_RIGHT_OF_CENTER -> Pair(KV_IDX_CENTER, LeftOrRight.Right(0))
        else -> Pair(KV_IDX_CENTER + 1, LeftOrRight.Right(edgeIdx - (KV_IDX_CENTER + 1 + 1)))
    }
}

// =====================================================================
// Handle Mut Leaf Edge: insert_fit / insert
// =====================================================================

/**
 * Inserts a new key-value pair between the key-value pairs to the right and left of
 * this edge. This method assumes that there is enough space in the node for the new pair.
 */
private fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge>.insertFit(
    key: K,
    value: V,
): Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.KV> {
    check(node.len() < CAPACITY) // debug_assert!(self.node.len() < CAPACITY)
    val newLen = node.len() + 1

    // SAFETY: caller ensured there is space; idx <= len() < CAPACITY = newLen-? — see slice_insert contract.
    sliceInsert(node.asLeafMut().keys, newLen, idx, key as Any?)
    sliceInsert(node.asLeafMut().vals, newLen, idx, value as Any?)
    node.setLen(newLen)

    return Handle.newKv(node, idx)
}

/**
 * Inserts a new key-value pair between the key-value pairs to the right and left of
 * this edge. This method splits the node if there isn't enough room.
 *
 * Returns a dormant handle to the inserted node which can be reawakened
 * once splitting is complete.
 */
private fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge>.insert(
    key: K,
    value: V,
): Pair<SplitResult<K, V, Marker.Leaf>?, Handle<NodeRef<Marker.DormantMut, K, V, Marker.Leaf>, Marker.KV>> {
    return if (node.len() < CAPACITY) {
        // SAFETY: There is enough space in the node for insertion.
        val handle = this.insertFit(key, value)
        Pair(null, handle.dormant())
    } else {
        val (middleKvIdx, insertion) = splitpoint(idx)
        // SAFETY: middleKvIdx < node.len() == CAPACITY.
        val middle = Handle.newKv(node, middleKvIdx)
        val result = middle.split()
        val insertionEdge = when (insertion) {
            is LeftOrRight.Left -> {
                // SAFETY: insertion index from splitpoint is bounds-checked there.
                Handle.newEdge(result.left.reborrowMut(), insertion.value)
            }
            is LeftOrRight.Right -> {
                // SAFETY: insertion index from splitpoint is bounds-checked there.
                Handle.newEdge(result.right.borrowMut(), insertion.value)
            }
        }
        // SAFETY: We just split the node, so there is enough space for insertion.
        val handle = insertionEdge.insertFit(key, value).dormant()
        Pair(result, handle)
    }
}

// =====================================================================
// Handle Mut Internal Edge: correct_parent_link, insert_fit, insert
// =====================================================================

/**
 * Fixes the parent pointer and index in the child node that this edge
 * links to. This is useful when the ordering of edges has been changed.
 */
internal fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.Internal>, Marker.Edge>.correctParentLink() {
    // Create backpointer without invalidating other references to the node.
    val ptr: InternalNode<K, V> = node.asInternalMut()
    val savedIdx = idx
    val child = this.descend()
    child.setParentLink(ptr, savedIdx)
}

/**
 * Inserts a new key-value pair and an edge that will go to the right of that new pair
 * between this edge and the key-value pair to the right of this edge. This method assumes
 * that there is enough space in the node for the new pair to fit.
 */
private fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.Internal>, Marker.Edge>.insertFit(
    key: K,
    value: V,
    edge: Root<K, V>,
) {
    check(node.len() < CAPACITY) // debug_assert!(self.node.len() < CAPACITY)
    check(edge.height == node.height - 1) // debug_assert!(edge.height == self.node.height - 1)
    val newLen = node.len() + 1

    val internal = node.asInternalMut()
    sliceInsert(internal.keys, newLen, idx, key as Any?)
    sliceInsert(internal.vals, newLen, idx, value as Any?)
    @Suppress("UNCHECKED_CAST")
    sliceInsert(internal.edges as Array<Any?>, newLen + 1, idx + 1, edge.node as Any?)
    node.setLen(newLen)

    node.correctChildrensParentLinks(idx + 1..newLen)
}

/**
 * Inserts a new key-value pair and an edge that will go to the right of that new pair
 * between this edge and the key-value pair to the right of this edge. This method splits
 * the node if there isn't enough room.
 */
private fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.Internal>, Marker.Edge>.insert(
    key: K,
    value: V,
    edge: Root<K, V>,
): SplitResult<K, V, Marker.Internal>? {
    check(edge.height == node.height - 1) // assert!(edge.height == self.node.height - 1)

    return if (node.len() < CAPACITY) {
        this.insertFit(key, value, edge)
        null
    } else {
        val (middleKvIdx, insertion) = splitpoint(idx)
        // SAFETY: middleKvIdx < node.len() == CAPACITY.
        val middle = Handle.newKv(node, middleKvIdx)
        val result = middle.split()
        val insertionEdge = when (insertion) {
            is LeftOrRight.Left -> {
                // SAFETY: insertion index from splitpoint is bounds-checked there.
                Handle.newEdge(result.left.reborrowMut(), insertion.value)
            }
            is LeftOrRight.Right -> {
                // SAFETY: insertion index from splitpoint is bounds-checked there.
                Handle.newEdge(result.right.borrowMut(), insertion.value)
            }
        }
        insertionEdge.insertFit(key, value, edge)
        result
    }
}

// =====================================================================
// Handle Mut Leaf Edge: insert_recursing
// =====================================================================

/**
 * Inserts a new key-value pair between the key-value pairs to the right and left of
 * this edge. This method splits the node if there isn't enough room, and tries to
 * insert the split off portion into the parent node recursively, until the root is reached.
 *
 * If the closure receives a [SplitResult], the `left` field will be the root node.
 * The returned handle points to the inserted value, which in the case of a split is
 * in the `left` or `right` tree.
 */
internal fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge>.insertRecursing(
    key: K,
    value: V,
    splitRoot: (SplitResult<K, V, Marker.LeafOrInternal>) -> Unit,
): Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.KV> {
    val (firstSplit, handle) = this.insert(key, value)
    var split: SplitResult<K, V, Marker.LeafOrInternal> = if (firstSplit == null) {
        // SAFETY: we have finished splitting and can now re-awaken the
        // handle to the inserted element.
        return handle.awaken()
    } else {
        firstSplit.forgetNodeTypeLeaf()
    }

    while (true) {
        split = when (val ascended = split.left.ascend()) {
            is AscendResult.Ok -> {
                val parent = ascended.handle
                val sub = parent.insert(split.kv.first, split.kv.second, split.right)
                if (sub == null) {
                    // SAFETY: we have finished splitting and can now re-awaken the
                    // handle to the inserted element.
                    return handle.awaken()
                } else {
                    sub.forgetNodeTypeInternal()
                }
            }
            is AscendResult.Err -> {
                splitRoot(SplitResult(left = ascended.node, kv = split.kv, right = split.right))
                // SAFETY: we have finished splitting and can now re-awaken the
                // handle to the inserted element.
                return handle.awaken()
            }
        }
    }
}

// =====================================================================
// Handle Internal Edge: descend
// =====================================================================

/** Finds the node pointed to by this edge. */
internal fun <BorrowType : Marker.BorrowType, K, V>
Handle<NodeRef<BorrowType, K, V, Marker.Internal>, Marker.Edge>.descend():
    NodeRef<BorrowType, K, V, Marker.LeafOrInternal> {
    // const { assert!(BorrowType::TRAVERSAL_PERMIT) } — see ascend() note.
    val parentPtr: InternalNode<K, V> = node.node as InternalNode<K, V>
    // SAFETY: idx <= len, so edges[idx] is initialised.
    val childNode = parentPtr.edges[idx]!!
    return NodeRef(height = node.height - 1, node = childNode)
}

// =====================================================================
// Handle Immut KV: into_kv
// =====================================================================

@Suppress("UNCHECKED_CAST")
internal fun <K, V, NodeType> Handle<NodeRef<Marker.Immut, K, V, NodeType>, Marker.KV>.intoKv(): Pair<K, V> {
    check(idx < node.len()) // debug_assert!(self.idx < self.node.len())
    val leaf = node.node
    // SAFETY: idx < len, slots are initialised.
    val k = leaf.keys[idx] as K
    val v = leaf.vals[idx] as V
    return Pair(k, v)
}

// =====================================================================
// Handle Mut KV: key_mut, into_val_mut, into_kv_mut, kv_mut, replace_kv
// =====================================================================

@Suppress("UNCHECKED_CAST")
internal fun <K, V, NodeType> Handle<NodeRef<Marker.Mut, K, V, NodeType>, Marker.KV>.keyMut(): K {
    // SAFETY: idx < len by Handle invariant; slot initialised.
    return node.asLeafMut().keys[idx] as K
}

internal fun <K, V, NodeType> Handle<NodeRef<Marker.Mut, K, V, NodeType>, Marker.KV>.setKey(value: K) {
    // SAFETY: idx < len by Handle invariant.
    node.asLeafMut().keys[idx] = value
}

@Suppress("UNCHECKED_CAST")
internal fun <K, V, NodeType> Handle<NodeRef<Marker.Mut, K, V, NodeType>, Marker.KV>.intoValMut(): V {
    check(idx < node.len()) // debug_assert!(self.idx < self.node.len())
    val leaf = node.intoLeafMut()
    // SAFETY: idx < len, slot initialised.
    return leaf.vals[idx] as V
}

internal fun <K, V, NodeType> Handle<NodeRef<Marker.Mut, K, V, NodeType>, Marker.KV>.setValMut(value: V) {
    // SAFETY: idx < len by Handle invariant.
    node.asLeafMut().vals[idx] = value
}

@Suppress("UNCHECKED_CAST")
internal fun <K, V, NodeType> Handle<NodeRef<Marker.Mut, K, V, NodeType>, Marker.KV>.intoKvMut(): Pair<K, V> {
    check(idx < node.len()) // debug_assert!(self.idx < self.node.len())
    val leaf = node.intoLeafMut()
    // SAFETY: idx < len, slots initialised.
    val k = leaf.keys[idx] as K
    val v = leaf.vals[idx] as V
    return Pair(k, v)
}

@Suppress("UNCHECKED_CAST")
internal fun <K, V, NodeType> Handle<NodeRef<Marker.Mut, K, V, NodeType>, Marker.KV>.kvMut(): Pair<K, V> {
    check(idx < node.len()) // debug_assert!(self.idx < self.node.len())
    // SAFETY: idx < len, slots initialised.
    val leaf = node.asLeafMut()
    val key = leaf.keys[idx] as K
    val v = leaf.vals[idx] as V
    return Pair(key, v)
}

/** Replaces the key and value that the KV handle refers to. */
@Suppress("UNCHECKED_CAST")
internal fun <K, V, NodeType> Handle<NodeRef<Marker.Mut, K, V, NodeType>, Marker.KV>.replaceKv(
    k: K,
    v: V,
): Pair<K, V> {
    val leaf = node.asLeafMut()
    // mem::replace(key, k) / mem::replace(val, v)
    val oldK = leaf.keys[idx] as K
    val oldV = leaf.vals[idx] as V
    leaf.keys[idx] = k
    leaf.vals[idx] = v
    return Pair(oldK, oldV)
}

// =====================================================================
// Handle ValMut KV: into_kv_valmut
// =====================================================================

internal fun <K, V, NodeType> Handle<NodeRef<Marker.ValMut, K, V, NodeType>, Marker.KV>.intoKvValmut():
    Pair<K, V> {
    return node.intoKeyValMutAt(idx)
}

// =====================================================================
// Handle Dying KV: into_key_val, drop_key_val
// =====================================================================

/**
 * Extracts the key and value that the KV handle refers to.
 *
 * Naming: upstream is `into_key_val` already (no `dying_` prefix to drop).
 *
 * # Safety
 * The node that the handle refers to must not yet have been deallocated.
 */
@Suppress("UNCHECKED_CAST")
internal fun <K, V, NodeType> Handle<NodeRef<Marker.Dying, K, V, NodeType>, Marker.KV>.intoKeyVal():
    Pair<K, V> {
    check(idx < node.len()) // debug_assert!(self.idx < self.node.len())
    val leaf = node.asLeafDying()
    // SAFETY: idx < len, slots initialised.
    val key = leaf.keys[idx] as K
    val v = leaf.vals[idx] as V
    return Pair(key, v)
}

/**
 * Drops the key and value that the KV handle refers to.
 *
 * In Kotlin GC handles deallocation, so the body is empty. The function is
 * preserved (rather than removed) so that downstream code that mirrors
 * upstream's drop sequencing has a slot to call.
 *
 * # Safety
 * The node that the handle refers to must not yet have been deallocated.
 */
@Suppress("UNUSED_PARAMETER")
internal fun <K, V, NodeType> Handle<NodeRef<Marker.Dying, K, V, NodeType>, Marker.KV>.dropKeyVal() {
    // Run the destructor of the value even if the destructor of the key panics.
    // SAFETY: GC supersedes manual drop; the entire body dissolves.
    check(idx < node.len()) // debug_assert!(self.idx < self.node.len())
}

// =====================================================================
// Handle Mut KV: split_leaf_data
// =====================================================================

/**
 * Helps implementations of `split` for a particular `NodeType`,
 * by taking care of leaf data.
 */
@Suppress("UNCHECKED_CAST")
private fun <K, V, NodeType> Handle<NodeRef<Marker.Mut, K, V, NodeType>, Marker.KV>.splitLeafData(
    newNode: LeafNode<K, V>,
): Pair<K, V> {
    check(idx < node.len()) // debug_assert!(self.idx < self.node.len())
    val oldLen = node.len()
    val newLen = oldLen - idx - 1
    newNode.len = newLen
    val leaf = node.asLeafMut()
    // SAFETY: idx < len so the slot is initialised.
    val k = leaf.keys[idx] as K
    val v = leaf.vals[idx] as V

    moveToSlice(leaf.keys, idx + 1, newNode.keys, 0, newLen)
    moveToSlice(leaf.vals, idx + 1, newNode.vals, 0, newLen)

    node.setLen(idx)
    return Pair(k, v)
}

// =====================================================================
// Handle Mut Leaf KV: split, remove
// =====================================================================

/**
 * Splits the underlying node into three parts:
 * - The node is truncated to only contain the key-value pairs to the left of this handle.
 * - The key and value pointed to by this handle are extracted.
 * - All the key-value pairs to the right of this handle are put into a newly allocated node.
 */
internal fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.KV>.split():
    SplitResult<K, V, Marker.Leaf> {
    val newNode = LeafNode.new<K, V>()
    val kv = this.splitLeafData(newNode)
    val right = NodeRef<Marker.Owned, K, V, Marker.Leaf>(height = 0, node = newNode)
    return SplitResult(left = node, kv = kv, right = right)
}

/**
 * Removes the key-value pair pointed to by this handle and returns it, along with the edge
 * that the key-value pair collapsed into.
 */
internal fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.KV>.remove():
    Pair<Pair<K, V>, Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge>> {
    val oldLen = node.len()
    val leaf = node.asLeafMut()
    @Suppress("UNCHECKED_CAST")
    val k = sliceRemove(leaf.keys, oldLen, idx) as K
    @Suppress("UNCHECKED_CAST")
    val v = sliceRemove(leaf.vals, oldLen, idx) as V
    node.setLen(oldLen - 1)
    return Pair(Pair(k, v), this.leftEdge())
}

// =====================================================================
// Handle Mut Internal KV: split
// =====================================================================

/**
 * Splits the underlying node into three parts:
 * - The node is truncated to only contain the edges and key-value pairs to the
 *   left of this handle.
 * - The key and value pointed to by this handle are extracted.
 * - All the edges and key-value pairs to the right of this handle are put into
 *   a newly allocated node.
 */
internal fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.Internal>, Marker.KV>.split():
    SplitResult<K, V, Marker.Internal> {
    val oldLen = node.len()
    val newNode = InternalNode.new<K, V>()
    val kv = this.splitLeafData(newNode)
    val newLen = newNode.len
    val srcInternal = node.asInternalMut()
    @Suppress("UNCHECKED_CAST")
    moveToSlice(
        srcInternal.edges as Array<Any?>, idx + 1,
        newNode.edges as Array<Any?>, 0,
        newLen + 1,
    )

    // SAFETY: self is `Marker::Internal`, so `node.height` is positive.
    val height = node.height
    val right = NodeRef<Marker.Owned, K, V, Marker.Internal>(height = height, node = newNode)
    right.borrowMut().correctAllChildrensParentLinks()
    return SplitResult(left = node, kv = kv, right = right)
}

// =====================================================================
// BalancingContext
// =====================================================================

/**
 * Represents a session for evaluating and performing a balancing operation
 * around an internal key-value pair.
 */
internal class BalancingContext<K, V> internal constructor(
    val parent: Handle<NodeRef<Marker.Mut, K, V, Marker.Internal>, Marker.KV>,
    val leftChild: NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>,
    val rightChild: NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>,
)

internal fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.Internal>, Marker.KV>.considerForBalancing():
    BalancingContext<K, V> {
    // Upstream uses ptr::read twice to alias `self` for the two .descend() calls.
    // In Kotlin we just construct sibling Handles that share the same node.
    val leftChild = Handle<NodeRef<Marker.Mut, K, V, Marker.Internal>, Marker.Edge>(
        node = NodeRef(height = node.height, node = node.node),
        idx = idx,
    ).descend()
    val rightChild = Handle<NodeRef<Marker.Mut, K, V, Marker.Internal>, Marker.Edge>(
        node = NodeRef(height = node.height, node = node.node),
        idx = idx + 1,
    ).descend()
    return BalancingContext(parent = this, leftChild = leftChild, rightChild = rightChild)
}

/**
 * Chooses a balancing context involving the node as a child, thus between
 * the KV immediately to the left or to the right in the parent node.
 * Returns an `Err` if there is no parent.
 * Panics if the parent is empty.
 *
 * Translation: upstream returns `Result<LeftOrRight<BalancingContext>, Self>`.
 * We model with a sealed result type analogous to [AscendResult].
 */
internal sealed class ChooseParentKvResult<K, V> {
    data class Ok<K, V>(val context: LeftOrRight<BalancingContext<K, V>>) : ChooseParentKvResult<K, V>()
    data class Err<K, V>(val node: NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>) : ChooseParentKvResult<K, V>()
}

internal fun <K, V> NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>.chooseParentKv():
    ChooseParentKvResult<K, V> {
    // Upstream uses ptr::read(&self) to copy `self` so that `ascend` can consume it.
    // In Kotlin we just take the field values; nothing prevents this aliasing.
    val selfCopy: NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal> = NodeRef(height, node)
    return when (val ascended = selfCopy.ascend()) {
        is AscendResult.Ok -> {
            val parentEdge = ascended.handle
            when (val left = parentEdge.leftKv()) {
                is EdgeKvResult.Ok -> {
                    val leftParentKv = left.handle
                    // ptr::read(&left_parent_kv): construct a sibling handle aliasing the same node.
                    val parentForCtx = Handle<NodeRef<Marker.Mut, K, V, Marker.Internal>, Marker.KV>(
                        node = NodeRef(leftParentKv.node.height, leftParentKv.node.node),
                        idx = leftParentKv.idx,
                    )
                    val leftChild = leftParentKv.leftEdge().descend()
                    ChooseParentKvResult.Ok(
                        LeftOrRight.Left(
                            BalancingContext(
                                parent = parentForCtx,
                                leftChild = leftChild,
                                rightChild = this,
                            ),
                        ),
                    )
                }
                is EdgeKvResult.Err -> {
                    val parentEdge2 = left.handle
                    when (val right = parentEdge2.rightKv()) {
                        is EdgeKvResult.Ok -> {
                            val rightParentKv = right.handle
                            val parentForCtx = Handle<NodeRef<Marker.Mut, K, V, Marker.Internal>, Marker.KV>(
                                node = NodeRef(rightParentKv.node.height, rightParentKv.node.node),
                                idx = rightParentKv.idx,
                            )
                            val rightChild = rightParentKv.rightEdge().descend()
                            ChooseParentKvResult.Ok(
                                LeftOrRight.Right(
                                    BalancingContext(
                                        parent = parentForCtx,
                                        leftChild = this,
                                        rightChild = rightChild,
                                    ),
                                ),
                            )
                        }
                        is EdgeKvResult.Err -> error("empty internal node")
                    }
                }
            }
        }
        is AscendResult.Err -> ChooseParentKvResult.Err(this)
    }
}

// ---- BalancingContext: simple accessors --------------------------------

internal fun <K, V> BalancingContext<K, V>.leftChildLen(): Int = leftChild.len()
internal fun <K, V> BalancingContext<K, V>.rightChildLen(): Int = rightChild.len()

internal fun <K, V> BalancingContext<K, V>.intoLeftChild():
    NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal> = leftChild

internal fun <K, V> BalancingContext<K, V>.intoRightChild():
    NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal> = rightChild

/**
 * Returns whether merging is possible, i.e., whether there is enough room
 * in a node to combine the central KV with both adjacent child nodes.
 */
internal fun <K, V> BalancingContext<K, V>.canMerge(): Boolean {
    return leftChild.len() + 1 + rightChild.len() <= CAPACITY
}

// ---- BalancingContext: do_merge ----------------------------------------

/**
 * Performs a merge and lets a closure decide what to return.
 */
private inline fun <K, V, R> BalancingContext<K, V>.doMerge(
    result: (
        NodeRef<Marker.Mut, K, V, Marker.Internal>,
        NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>,
    ) -> R,
): R {
    // Destructure parent: { node: parent_node, idx: parent_idx, _marker }
    val parentNode = parent.node
    val parentIdx = parent.idx
    val oldParentLen = parentNode.len()
    val leftNode = leftChild
    val oldLeftLen = leftNode.len()
    val rightNode = rightChild
    val rightLen = rightNode.len()
    val newLeftLen = oldLeftLen + 1 + rightLen

    check(newLeftLen <= CAPACITY) // assert!(new_left_len <= CAPACITY)

    leftNode.setLen(newLeftLen)

    val parentKey = sliceRemove(parentNode.asLeafMut().keys, oldParentLen, parentIdx)
    @Suppress("UNCHECKED_CAST")
    leftNode.writeKeyArea(oldLeftLen, parentKey as K)
    moveToSlice(
        rightNode.asLeafMut().keys, 0,
        leftNode.asLeafMut().keys, oldLeftLen + 1,
        rightLen,
    )

    val parentVal = sliceRemove(parentNode.asLeafMut().vals, oldParentLen, parentIdx)
    @Suppress("UNCHECKED_CAST")
    leftNode.writeValArea(oldLeftLen, parentVal as V)
    moveToSlice(
        rightNode.asLeafMut().vals, 0,
        leftNode.asLeafMut().vals, oldLeftLen + 1,
        rightLen,
    )

    val parentInternal = parentNode.asInternalMut()
    @Suppress("UNCHECKED_CAST")
    sliceRemove(parentInternal.edges as Array<Any?>, oldParentLen + 1, parentIdx + 1)
    parentNode.correctChildrensParentLinks(parentIdx + 1 until oldParentLen)
    parentNode.setLen(oldParentLen - 1)

    if (parentNode.height > 1) {
        // SAFETY: the height of the nodes being merged is one below the height
        // of the node of this edge, thus above zero, so they are internal.
        val leftInternal = leftNode.reborrowMut().castToInternalUnchecked()
        val rightInternal = rightNode.castToInternalUnchecked()
        @Suppress("UNCHECKED_CAST")
        moveToSlice(
            rightInternal.asInternalMut().edges as Array<Any?>, 0,
            leftInternal.asInternalMut().edges as Array<Any?>, oldLeftLen + 1,
            rightLen + 1,
        )
        leftInternal.correctChildrensParentLinks(oldLeftLen + 1..newLeftLen)

        // alloc.deallocate(right_node, Layout::new::<InternalNode>()) — dissolved (GC).
    } else {
        // alloc.deallocate(right_node, Layout::new::<LeafNode>()) — dissolved (GC).
    }

    return result(parentNode, leftNode)
}

/**
 * Merges the parent's key-value pair and both adjacent child nodes into
 * the left child node and returns the shrunk parent node.
 */
internal fun <K, V> BalancingContext<K, V>.mergeTrackingParent():
    NodeRef<Marker.Mut, K, V, Marker.Internal> {
    return this.doMerge { parent, _child -> parent }
}

/**
 * Merges the parent's key-value pair and both adjacent child nodes into
 * the left child node and returns that child node.
 */
internal fun <K, V> BalancingContext<K, V>.mergeTrackingChild():
    NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal> {
    return this.doMerge { _parent, child -> child }
}

/**
 * Merges the parent's key-value pair and both adjacent child nodes into
 * the left child node and returns the edge handle in that child node
 * where the tracked child edge ended up.
 */
internal fun <K, V> BalancingContext<K, V>.mergeTrackingChildEdge(
    trackEdgeIdx: LeftOrRight<Int>,
): Handle<NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>, Marker.Edge> {
    val oldLeftLen = leftChild.len()
    val rightLen = rightChild.len()
    check(
        when (trackEdgeIdx) {
            is LeftOrRight.Left -> trackEdgeIdx.value <= oldLeftLen
            is LeftOrRight.Right -> trackEdgeIdx.value <= rightLen
        },
    )
    val child = this.mergeTrackingChild()
    val newIdx = when (trackEdgeIdx) {
        is LeftOrRight.Left -> trackEdgeIdx.value
        is LeftOrRight.Right -> oldLeftLen + 1 + trackEdgeIdx.value
    }
    return Handle.newEdge(child, newIdx)
}

// ---- BalancingContext: steal_left / steal_right ------------------------

/**
 * Removes a key-value pair from the left child and places it in the key-value storage
 * of the parent, while pushing the old parent key-value pair into the right child.
 * Returns a handle to the edge in the right child corresponding to where the original
 * edge specified by `trackRightEdgeIdx` ended up.
 */
internal fun <K, V> BalancingContext<K, V>.stealLeft(
    trackRightEdgeIdx: Int,
): Handle<NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>, Marker.Edge> {
    this.bulkStealLeft(1)
    return Handle.newEdge(rightChild, 1 + trackRightEdgeIdx)
}

/**
 * Removes a key-value pair from the right child and places it in the key-value storage
 * of the parent, while pushing the old parent key-value pair onto the left child.
 * Returns a handle to the edge in the left child specified by `trackLeftEdgeIdx`,
 * which didn't move.
 */
internal fun <K, V> BalancingContext<K, V>.stealRight(
    trackLeftEdgeIdx: Int,
): Handle<NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>, Marker.Edge> {
    this.bulkStealRight(1)
    return Handle.newEdge(leftChild, trackLeftEdgeIdx)
}

/** Bulk steal `count` entries from the left child into the right child. */
internal fun <K, V> BalancingContext<K, V>.bulkStealLeft(count: Int) {
    check(count > 0) // assert!(count > 0)
    val leftNode = leftChild
    val oldLeftLen = leftNode.len()
    val rightNode = rightChild
    val oldRightLen = rightNode.len()

    // Make sure that we may steal safely.
    check(oldRightLen + count <= CAPACITY) // assert!(old_right_len + count <= CAPACITY)
    check(oldLeftLen >= count) // assert!(old_left_len >= count)

    val newLeftLen = oldLeftLen - count
    val newRightLen = oldRightLen + count
    leftNode.setLen(newLeftLen)
    rightNode.setLen(newRightLen)

    // Move leaf data.
    run {
        val rightLeaf = rightNode.asLeafMut()
        val leftLeaf = leftNode.asLeafMut()
        // Make room for stolen elements in the right child.
        sliceShr(rightLeaf.keys, newRightLen, count)
        sliceShr(rightLeaf.vals, newRightLen, count)

        // Move elements from the left child to the right one.
        moveToSlice(leftLeaf.keys, newLeftLen + 1, rightLeaf.keys, 0, count - 1)
        moveToSlice(leftLeaf.vals, newLeftLen + 1, rightLeaf.vals, 0, count - 1)

        // Move the leftmost stolen pair to the parent.
        @Suppress("UNCHECKED_CAST")
        val k = leftLeaf.keys[newLeftLen] as K
        @Suppress("UNCHECKED_CAST")
        val v = leftLeaf.vals[newLeftLen] as V
        val (pk, pv) = parent.replaceKv(k, v)

        // Move parent's key-value pair to the right child.
        rightLeaf.keys[count - 1] = pk
        rightLeaf.vals[count - 1] = pv
    }

    when (val lf = leftNode.reborrowMut().force()) {
        is ForceResult.Internal -> {
            when (val rf = rightNode.reborrowMut().force()) {
                is ForceResult.Internal -> {
                    val left = lf.value
                    val right = rf.value
                    val leftEdges = left.asInternalMut().edges
                    val rightEdges = right.asInternalMut().edges
                    // Make room for stolen edges.
                    @Suppress("UNCHECKED_CAST")
                    sliceShr(rightEdges as Array<Any?>, newRightLen + 1, count)

                    // Steal edges.
                    @Suppress("UNCHECKED_CAST")
                    moveToSlice(
                        leftEdges as Array<Any?>, newLeftLen + 1,
                        rightEdges as Array<Any?>, 0,
                        count,
                    )

                    right.correctChildrensParentLinks(0..newRightLen)
                }
                is ForceResult.Leaf -> error("unreachable")
            }
        }
        is ForceResult.Leaf -> {
            when (val rf = rightNode.reborrowMut().force()) {
                is ForceResult.Leaf -> { /* ok */ }
                is ForceResult.Internal -> error("unreachable")
            }
        }
    }
}

/** The symmetric clone of [bulkStealLeft]. */
internal fun <K, V> BalancingContext<K, V>.bulkStealRight(count: Int) {
    check(count > 0) // assert!(count > 0)
    val leftNode = leftChild
    val oldLeftLen = leftNode.len()
    val rightNode = rightChild
    val oldRightLen = rightNode.len()

    // Make sure that we may steal safely.
    check(oldLeftLen + count <= CAPACITY) // assert!(old_left_len + count <= CAPACITY)
    check(oldRightLen >= count) // assert!(old_right_len >= count)

    val newLeftLen = oldLeftLen + count
    val newRightLen = oldRightLen - count
    leftNode.setLen(newLeftLen)
    rightNode.setLen(newRightLen)

    // Move leaf data.
    run {
        val leftLeaf = leftNode.asLeafMut()
        val rightLeaf = rightNode.asLeafMut()
        // Move the rightmost stolen pair to the parent.
        @Suppress("UNCHECKED_CAST")
        val k = rightLeaf.keys[count - 1] as K
        @Suppress("UNCHECKED_CAST")
        val v = rightLeaf.vals[count - 1] as V
        val (pk, pv) = parent.replaceKv(k, v)

        // Move parent's key-value pair to the left child.
        leftLeaf.keys[oldLeftLen] = pk
        leftLeaf.vals[oldLeftLen] = pv

        // Move elements from the right child to the left one.
        moveToSlice(rightLeaf.keys, 0, leftLeaf.keys, oldLeftLen + 1, count - 1)
        moveToSlice(rightLeaf.vals, 0, leftLeaf.vals, oldLeftLen + 1, count - 1)

        // Fill gap where stolen elements used to be.
        sliceShl(rightLeaf.keys, oldRightLen, count)
        sliceShl(rightLeaf.vals, oldRightLen, count)
    }

    when (val lf = leftNode.reborrowMut().force()) {
        is ForceResult.Internal -> {
            when (val rf = rightNode.reborrowMut().force()) {
                is ForceResult.Internal -> {
                    val left = lf.value
                    val right = rf.value
                    val leftEdges = left.asInternalMut().edges
                    val rightEdges = right.asInternalMut().edges
                    // Steal edges.
                    @Suppress("UNCHECKED_CAST")
                    moveToSlice(
                        rightEdges as Array<Any?>, 0,
                        leftEdges as Array<Any?>, oldLeftLen + 1,
                        count,
                    )

                    // Fill gap where stolen edges used to be.
                    @Suppress("UNCHECKED_CAST")
                    sliceShl(rightEdges as Array<Any?>, oldRightLen + 1, count)

                    left.correctChildrensParentLinks(oldLeftLen + 1..newLeftLen)
                    right.correctChildrensParentLinks(0..newRightLen)
                }
                is ForceResult.Leaf -> error("unreachable")
            }
        }
        is ForceResult.Leaf -> {
            when (val rf = rightNode.reborrowMut().force()) {
                is ForceResult.Leaf -> { /* ok */ }
                is ForceResult.Internal -> error("unreachable")
            }
        }
    }
}

// =====================================================================
// Handle forget_node_type (Leaf Edge / Internal Edge / Leaf KV)
// =====================================================================

internal fun <BorrowType, K, V>
Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.Edge>.forgetNodeTypeLeafEdge():
    Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.Edge> {
    return Handle.newEdge(node.forgetType(), idx)
}

internal fun <BorrowType, K, V>
Handle<NodeRef<BorrowType, K, V, Marker.Internal>, Marker.Edge>.forgetNodeTypeInternalEdge():
    Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.Edge> {
    return Handle.newEdge(node.forgetType(), idx)
}

internal fun <BorrowType, K, V>
Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Marker.KV>.forgetNodeTypeKv():
    Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Marker.KV> {
    return Handle.newKv(node.forgetType(), idx)
}

// =====================================================================
// Handle LeafOrInternal: force, cast_to_leaf_unchecked
// =====================================================================

/** Checks whether the underlying node is an `Internal` node or a `Leaf` node. */
internal fun <BorrowType, K, V, Type>
Handle<NodeRef<BorrowType, K, V, Marker.LeafOrInternal>, Type>.force():
    ForceResult<
        Handle<NodeRef<BorrowType, K, V, Marker.Leaf>, Type>,
        Handle<NodeRef<BorrowType, K, V, Marker.Internal>, Type>,
    > {
    return when (val r = node.force()) {
        is ForceResult.Leaf -> ForceResult.Leaf(Handle(r.value, idx))
        is ForceResult.Internal -> ForceResult.Internal(Handle(r.value, idx))
    }
}

/** Unsafely asserts to the compiler the static information that the handle's node is a `Leaf`. */
internal fun <K, V, Type>
Handle<NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>, Type>.castToLeafUnchecked():
    Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Type> {
    val leafNode = node.castToLeafUnchecked()
    return Handle(leafNode, idx)
}

// =====================================================================
// Handle Mut LeafOrInternal Edge: move_suffix
// =====================================================================

/**
 * Move the suffix after `self` from one node to another one. `right` must be empty.
 * The first edge of `right` remains unchanged.
 */
internal fun <K, V> Handle<NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>, Marker.Edge>.moveSuffix(
    right: NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>,
) {
    val newLeftLen = idx
    val leftNode = this.reborrowMut().intoNode()
    val oldLeftLen = leftNode.len()

    val newRightLen = oldLeftLen - newLeftLen
    val rightNode = right.reborrowMut()

    check(rightNode.len() == 0) // assert!(right_node.len() == 0)
    check(leftNode.height == rightNode.height) // assert!(left_node.height == right_node.height)

    if (newRightLen > 0) {
        leftNode.setLen(newLeftLen)
        rightNode.setLen(newRightLen)

        moveToSlice(
            leftNode.asLeafMut().keys, newLeftLen,
            rightNode.asLeafMut().keys, 0,
            newRightLen,
        )
        moveToSlice(
            leftNode.asLeafMut().vals, newLeftLen,
            rightNode.asLeafMut().vals, 0,
            newRightLen,
        )
        when (val lf = leftNode.force()) {
            is ForceResult.Internal -> when (val rf = rightNode.force()) {
                is ForceResult.Internal -> {
                    val left = lf.value
                    val rightI = rf.value
                    @Suppress("UNCHECKED_CAST")
                    moveToSlice(
                        left.asInternalMut().edges as Array<Any?>, newLeftLen + 1,
                        rightI.asInternalMut().edges as Array<Any?>, 1,
                        newRightLen,
                    )
                    rightI.correctChildrensParentLinks(1..newRightLen)
                }
                is ForceResult.Leaf -> error("unreachable")
            }
            is ForceResult.Leaf -> when (val rf = rightNode.force()) {
                is ForceResult.Leaf -> { /* ok */ }
                is ForceResult.Internal -> error("unreachable")
            }
        }
    }
}

// =====================================================================
// ForceResult, SplitResult
// =====================================================================

internal sealed class ForceResult<L, I> {
    data class Leaf<L, I>(val value: L) : ForceResult<L, I>()
    data class Internal<L, I>(val value: I) : ForceResult<L, I>()
}

/**
 * Result of insertion, when a node needed to expand beyond its capacity.
 */
internal class SplitResult<K, V, NodeType>(
    /** Altered node in existing tree with elements and edges that belong to the left of `kv`. */
    val left: NodeRef<Marker.Mut, K, V, NodeType>,
    /** Some key and value that existed before and were split off, to be inserted elsewhere. */
    val kv: Pair<K, V>,
    /** Owned, unattached, new node with elements and edges that belong to the right of `kv`. */
    val right: NodeRef<Marker.Owned, K, V, NodeType>,
)

/** Specialised `forget_node_type` for `SplitResult<..., Leaf>`. */
internal fun <K, V> SplitResult<K, V, Marker.Leaf>.forgetNodeTypeLeaf():
    SplitResult<K, V, Marker.LeafOrInternal> {
    return SplitResult(
        left = left.forgetType(),
        kv = kv,
        right = right.forgetType(),
    )
}

/** Specialised `forget_node_type` for `SplitResult<..., Internal>`. */
internal fun <K, V> SplitResult<K, V, Marker.Internal>.forgetNodeTypeInternal():
    SplitResult<K, V, Marker.LeafOrInternal> {
    return SplitResult(
        left = left.forgetType(),
        kv = kv,
        right = right.forgetType(),
    )
}

// =====================================================================
// Marker namespace
// =====================================================================

/**
 * Phantom-type tag namespace. Mirrors `pub(super) mod marker` upstream.
 *
 * The Rust types are `enum`s with no variants (uninhabited zero-sized
 * markers); we render them as empty Kotlin classes.
 */
internal object Marker {
    /** Marker for nodes that are statically known to be leaves. */
    class Leaf private constructor()

    /** Marker for nodes that are statically known to be internal. */
    class Internal private constructor()

    /** Marker for nodes that may be either leaves or internal. */
    class LeafOrInternal private constructor()

    /** Marker for owned tree references — no traversal permitted. */
    class Owned private constructor() : BorrowType {
        override val traversalPermit: Boolean get() = false
    }

    /** Marker for dying tree references — destructive operations only. */
    class Dying private constructor() : BorrowType

    /** Marker for dormant mutable references — see [DormantMutRef]. */
    class DormantMut private constructor() : BorrowType

    /** Marker for shared (immutable) references. */
    class Immut private constructor() : BorrowType

    /** Marker for exclusive (mutable) references. */
    class Mut private constructor() : BorrowType

    /** Marker for value-mut references — keys are immutable, values are mutable. */
    class ValMut private constructor() : BorrowType

    /** Marker for handles pointing at a key-value slot. */
    class KV private constructor()

    /** Marker for handles pointing at an edge between two slots. */
    class Edge private constructor()

    /**
     * Constraint trait for the `BorrowType` parameter. Mirrors the upstream
     * `marker::BorrowType` trait, including its `TRAVERSAL_PERMIT` constant.
     *
     * Translation: Kotlin lacks Rust's compile-time `const` items in traits,
     * so the constant becomes an instance property. Because the markers are
     * uninstantiable (zero-sized; private constructor), no value of these
     * types ever exists at runtime — the types serve purely as type tags
     * and the property is never queried. The body of `ascend()` and
     * `descend()`, which upstream gates on `BorrowType::TRAVERSAL_PERMIT`,
     * dissolves to a comment in the Kotlin port.
     */
    interface BorrowType {
        val traversalPermit: Boolean get() = true
    }
}

// =====================================================================
// Slice helpers
// =====================================================================
//
// Upstream takes `&mut [MaybeUninit<T>]` slice references; Kotlin lacks
// slice references, so each helper takes the underlying array plus the
// slice's logical length (`sliceLen`), or explicit `srcOffset`/`dstOffset`.
//
// All helpers operate on `Array<Any?>` storage. The K/V/edge types are
// erased at this level (the storage arrays are allocated as
// `arrayOfNulls<Any?>(...)` for a uniform shape).

/**
 * Inserts a value into a slice of initialized elements followed by one uninitialized element.
 *
 * # Safety
 * The slice has more than `idx` elements (i.e. `idx < sliceLen`).
 */
private fun sliceInsert(slice: Array<Any?>, sliceLen: Int, idx: Int, value: Any?) {
    check(sliceLen > idx) // debug_assert!(len > idx)
    if (sliceLen > idx + 1) {
        // ptr::copy(slice_ptr.add(idx), slice_ptr.add(idx + 1), len - idx - 1)
        // overlapping move toward higher indices: iterate from high end.
        for (i in sliceLen - 1 downTo idx + 1) {
            slice[i] = slice[i - 1]
        }
    }
    slice[idx] = value
}

/**
 * Removes and returns a value from a slice of all initialized elements, leaving behind one
 * trailing uninitialized element.
 *
 * # Safety
 * The slice has more than `idx` elements (i.e. `idx < sliceLen`).
 */
private fun sliceRemove(slice: Array<Any?>, sliceLen: Int, idx: Int): Any? {
    check(idx < sliceLen) // debug_assert!(idx < len)
    val ret = slice[idx]
    // ptr::copy(slice_ptr.add(idx + 1), slice_ptr.add(idx), len - idx - 1)
    // overlapping move toward lower indices: iterate from low end.
    for (i in idx until sliceLen - 1) {
        slice[i] = slice[i + 1]
    }
    slice[sliceLen - 1] = null
    return ret
}

/**
 * Shifts the elements in a slice `distance` positions to the left.
 *
 * # Safety
 * The slice has at least `distance` elements (`distance <= sliceLen`).
 */
private fun sliceShl(slice: Array<Any?>, sliceLen: Int, distance: Int) {
    // ptr::copy(slice_ptr.add(distance), slice_ptr, slice.len() - distance)
    // overlapping copy toward lower indices: iterate from low end.
    for (i in 0 until sliceLen - distance) {
        slice[i] = slice[i + distance]
    }
}

/**
 * Shifts the elements in a slice `distance` positions to the right.
 *
 * # Safety
 * The slice has at least `distance` elements (`distance <= sliceLen`).
 */
private fun sliceShr(slice: Array<Any?>, sliceLen: Int, distance: Int) {
    // ptr::copy(slice_ptr, slice_ptr.add(distance), slice.len() - distance)
    // overlapping copy toward higher indices: iterate from high end.
    for (i in sliceLen - 1 downTo distance) {
        slice[i] = slice[i - distance]
    }
}

/**
 * Moves all values from a slice of initialized elements to a slice
 * of uninitialized elements, leaving behind `src` as all uninitialized.
 *
 * Upstream is non-overlapping (copy_nonoverlapping); this Kotlin port works
 * for non-overlapping spans of the same or different arrays. The caller is
 * responsible for the non-overlap precondition.
 */
private fun moveToSlice(
    src: Array<Any?>,
    srcOffset: Int,
    dst: Array<Any?>,
    dstOffset: Int,
    count: Int,
) {
    // assert!(src.len() == dst.len()) — caller passes matching lengths via `count`.
    for (i in 0 until count) {
        dst[dstOffset + i] = src[srcOffset + i]
        src[srcOffset + i] = null
    }
}
