// port-lint: source library/alloc/src/collections/btree/map/entry.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

/**
 * A view into a single entry in a map, which may either be vacant or occupied.
 *
 * This sealed class is constructed by [BTreeMap.entry].
 */
sealed class Entry<K : Comparable<K>, V> {
    /** A vacant entry. */
    class Vacant<K : Comparable<K>, V>(val entry: VacantEntry<K, V>) : Entry<K, V>() {
        override fun toString(): String = "Entry($entry)"
    }

    /** An occupied entry. */
    class Occupied<K : Comparable<K>, V>(val entry: OccupiedEntry<K, V>) : Entry<K, V>() {
        override fun toString(): String = "Entry($entry)"
    }

    // ---- Entry<K, V> methods (shared by both variants) ----------------------

    /**
     * Ensures a value is in the entry by inserting [default] if empty, and
     * returns the value in the entry.
     */
    fun orInsert(default: V): V = when (this) {
        is Occupied -> entry.intoMut()
        is Vacant -> entry.insert(default)
    }

    /**
     * Ensures a value is in the entry by inserting the result of [default] if
     * empty, and returns the value in the entry.
     */
    fun orInsertWith(default: () -> V): V = when (this) {
        is Occupied -> entry.intoMut()
        is Vacant -> entry.insert(default())
    }

    /**
     * Ensures a value is in the entry by inserting the result of `default(key)`
     * if empty, and returns the value in the entry.
     *
     * The reference to the moved key is provided so that cloning or copying
     * the key is unnecessary, unlike with [orInsertWith].
     */
    fun orInsertWithKey(default: (K) -> V): V = when (this) {
        is Occupied -> entry.intoMut()
        is Vacant -> {
            val value = default(entry.key())
            entry.insert(value)
        }
    }

    /** Returns a reference to this entry's key. */
    fun key(): K = when (this) {
        is Occupied -> entry.key()
        is Vacant -> entry.key()
    }

    /**
     * Provides in-place mutable access to an occupied entry before any
     * potential inserts into the map.
     */
    fun andModify(f: (V) -> V): Entry<K, V> {
        when (this) {
            is Occupied -> {
                // Upstream signature: `f: FnOnce(&mut V)`. The closure mutates
                // the value through `&mut`. Since Kotlin lambdas can't take
                // `&mut V`, we model this as "compute the new value from the
                // old and write it back" — semantically equivalent.
                val old = entry.get()
                val updated = f(old)
                entry.insert(updated)
            }
            is Vacant -> { /* no-op */ }
        }
        return this
    }

    /** Sets the value of the entry, and returns an `OccupiedEntry`. */
    fun insertEntry(value: V): OccupiedEntry<K, V> = when (this) {
        is Occupied -> {
            entry.insert(value)
            entry
        }
        is Vacant -> entry.insertEntry(value)
    }

    /**
     * Ensures a value is in the entry by inserting the value supplied by
     * [default] if empty, and returns the value in the entry.
     *
     * Upstream's `orDefault` requires `V: Default`. Kotlin has no `Default`
     * trait — callers pass an explicit factory instead. Method named
     * `orDefault` to match upstream spelling; the factory parameter is the
     * Kotlin-side accommodation.
     */
    fun orDefault(default: () -> V): V = when (this) {
        is Occupied -> entry.intoMut()
        is Vacant -> entry.insert(default())
    }
}

/**
 * A view into a vacant entry in a `BTreeMap`. It is part of the [Entry] enum.
 */
class VacantEntry<K : Comparable<K>, V> internal constructor(
    internal var key: K,
    /** `null` for an (empty) map without root. */
    internal var handle: Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge>?,
    internal val dormantMap: DormantMutRef<BTreeMap<K, V>>,
) {
    /** Gets a reference to the key that would be used when inserting through this entry. */
    fun key(): K = key

    /** Take ownership of the key. */
    fun intoKey(): K = key

    /**
     * Sets the value of the entry with this `VacantEntry`'s key, and returns
     * the value in the entry.
     */
    fun insert(value: V): V = insertEntry(value).intoMut()

    /**
     * Sets the value of the entry with this `VacantEntry`'s key, and returns
     * an `OccupiedEntry`.
     */
    fun insertEntry(value: V): OccupiedEntry<K, V> {
        val h = handle
        val newHandle: Handle<NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>, Marker.KV> =
            if (h == null) {
                // SAFETY: There is no tree yet so no reference to it exists.
                val map = dormantMap.reborrow()
                if (map.root == null) {
                    map.root = NodeRef.newLeaf<K, V>().forgetType()
                }
                val root = map.root!!
                // SAFETY: We *just* created the root as a leaf, and we're
                // stacking the new handle on the original borrow.
                val leaf = root.borrowMut().castToLeafUnchecked()
                val pushed = leaf.pushWithHandle(this.key, value)
                pushed.forgetNodeTypeKv()
            } else {
                h.insertRecursing(this.key, value) { ins ->
                    // SAFETY: Pushing a new root node doesn't invalidate
                    // handles to existing nodes.
                    val map = dormantMap.reborrow()
                    val root = map.root!! // same as ins.left
                    root.pushInternalLevel().push(ins.kv.first, ins.kv.second, ins.right)
                }.forgetNodeTypeKv()
            }
        // SAFETY: modifying the length doesn't invalidate handles to existing nodes.
        dormantMap.reborrow().length += 1

        return OccupiedEntry(handle = newHandle, dormantMap = dormantMap)
    }

    override fun toString(): String = "VacantEntry(${this.key})"
}

/**
 * A view into an occupied entry in a `BTreeMap`. It is part of the [Entry] enum.
 */
class OccupiedEntry<K : Comparable<K>, V> internal constructor(
    internal var handle: Handle<NodeRef<Marker.Mut, K, V, Marker.LeafOrInternal>, Marker.KV>,
    internal val dormantMap: DormantMutRef<BTreeMap<K, V>>,
) {
    /** Gets a reference to the key in the entry. */
    fun key(): K = handle.reborrow().intoKv().first

    /** Converts the entry into a reference to its key. */
    internal fun intoKey(): K = handle.intoKvMut().first

    /** Take ownership of the key and value from the map. */
    fun removeEntry(): Pair<K, V> = removeKv()

    /** Gets a reference to the value in the entry. */
    fun get(): V = handle.reborrow().intoKv().second

    /** Gets a mutable reference to the value in the entry. */
    fun getMut(): V = handle.kvMut().second

    /** Converts the entry into a mutable reference to its value. */
    fun intoMut(): V = handle.intoValMut()

    /**
     * Sets the value of the entry, and returns the entry's old value.
     */
    fun insert(value: V): V {
        val old = handle.intoValMut()
        handle.setValMut(value)
        return old
    }

    /** Takes the value of the entry out of the map, and returns it. */
    fun remove(): V = removeKv().second

    /**
     * Body of [removeEntry], split out because the upstream name reflects
     * the returned pair.
     */
    internal fun removeKv(): Pair<K, V> {
        var emptiedInternalRoot = false
        val (oldKv, _) = handle.removeKvTracking { emptiedInternalRoot = true }
        // SAFETY: we consumed the intermediate root borrow, `self.handle`.
        val map = dormantMap.awaken()
        map.length -= 1
        if (emptiedInternalRoot) {
            val root = map.root!!
            root.popInternalLevel()
        }
        return oldKv
    }

    override fun toString(): String =
        "OccupiedEntry(key=${this.key()}, value=${this.get()})"
}

/**
 * The error returned by [BTreeMap.tryInsert] when the key already exists.
 *
 * Contains the occupied entry, and the value that was not inserted. Mirrors
 * upstream's `(unstable(mapTryInsert))` `OccupiedError` struct.
 */
class OccupiedError<K : Comparable<K>, V> internal constructor(
    /** The entry in the map that was already occupied. */
    val entry: OccupiedEntry<K, V>,
    /** The value which was not inserted, because the entry was already occupied. */
    val value: V,
) {
    override fun toString(): String =
        "OccupiedError(key=${entry.key()}, oldValue=${entry.get()}, newValue=$value)"
}
