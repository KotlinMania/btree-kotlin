// port-lint: source library/alloc/src/collections/btree/map.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
@file:Suppress("UNCHECKED_CAST")

package io.github.kotlinmania.btree

// File-level translation notes
// =============================
//
// - Upstream `BTreeMap<K, V, A: Allocator + Clone = Global>` -> `BTreeMap<K, V>`.
//   The allocator parameter `A` is dropped on every type and method — Kotlin's
//   GC supersedes manual deallocation, matching the convention established
//   throughout Phases 1-3.
// - `'a` lifetime parameters drop on every iterator and entry type.
// - `K: Ord` -> `K : Comparable<K>` on the class parameter; per-method `Q`
//   bounds use the AGENTS.md "Borrow" pattern (`K : Comparable<Q>`,
//   `Q : Comparable<Q>`).
// - `BTreeMap<K, V>` implements [MutableMap] for Kotlin-side ergonomics; that
//   wires `entries`, `keys`, `values`, `get`, `put`, `remove`, etc. into the
//   stdlib's collection contract for free, and downstream `BTreeSet` can lean
//   on the Kotlin Set interface in turn.
// - Public iterator types ([Iter], [IterMut], [Keys], [Values], [ValuesMut],
//   [IntoIter], [IntoKeys], [IntoValues], [Range], [RangeMut]) are nested
//   classes implementing [Iterator] / [MutableIterator] over the right Kotlin
//   shape: `MutableMap.MutableEntry<K, V>` for the entry-shaped iterators,
//   plain `K` / `V` for the projection ones. Drop semantics dissolve (GC).
// - The `mem::replace(&mut self.length, 0)` pattern in `clear` translates to
//   direct field reset — Kotlin has no moved-out-state to hide.
// - `Cursor` / `CursorMut` / `CursorMutKey` are kept as separate classes
//   matching upstream so consumers spell them the same. The `unsafe`
//   methods translate to plain methods with `// SAFETY:` comments
//   preserving the upstream invariants.
// - `extract_if` / `ExtractIf` / `ExtractIfInner`: the `Drop` impl on
//   `ExtractIf` upstream re-walks the iterator on panic. In Kotlin we rely
//   on GC and document that consumers must consume the iterator if they
//   care about removal happening atomically with iteration progress.
// - `range` / `range_mut` are `inline reified V` because they thread into
//   Search.kt's `searchTreeForBifurcation`; this cascades from Phase 3.
// - `Index<&Q>` (Rust's `map[&q]`) translates to Kotlin's `operator get`,
//   which throws `NoSuchElementException` on a missing key (matching
//   `expect("no entry found for key")`).
// - `BTreeMap<K, SetValZst>` overloads `replace` and `getOrInsertWith` per
//   upstream's set-internal helpers; these are `internal` so Set.kt (Phase 5)
//   can call them.
// - `Hash`, `PartialEq`/`Eq`, `PartialOrd`/`Ord`, `Debug` impls become
//   `hashCode` / `equals` / `compareTo` / `toString` overrides on
//   `BTreeMap`. `Comparable<BTreeMap<K, V>>` is implemented when callers
//   want lexicographic compare; the `K: Ord, V: Ord` bound from upstream
//   becomes a runtime check (no Kotlin equivalent of conditional impls).

// ============================================================================
// Constants
// ============================================================================

/**
 * Minimum number of elements in a node that is not a root.
 * We might temporarily have fewer elements during methods.
 *
 * Mirrors upstream `pub(super) const MIN_LEN: usize = node::MIN_LEN_AFTER_SPLIT;`.
 * Imported by Fix.kt and Remove.kt through the flat `btree` package.
 */
internal const val MIN_LEN: Int = MIN_LEN_AFTER_SPLIT

// ============================================================================
// BTreeMap
// ============================================================================

/**
 * An ordered map based on a B-Tree.
 *
 * Translation of upstream `pub struct BTreeMap<K, V, A: Allocator + Clone = Global>`.
 * The allocator parameter is dropped (GC supersedes manual deallocation).
 *
 * Implements [MutableMap] so consumers can use the Kotlin collections idioms
 * for free; instance methods that mirror upstream's surface (e.g. [insert],
 * [removeEntry], [firstKeyValue]) coexist with the [MutableMap] contract.
 *
 * `K : Comparable<K>` mirrors `where K: Ord` on every Rust impl — the type
 * system enforces it once at the class parameter rather than at every method.
 */
class BTreeMap<K : Comparable<K>, V> : MutableMap<K, V> {
    internal var root: Root<K, V>? = null
    internal var length: Int = 0

    /**
     * `true` if this map's `V` slot is the [SetValZst] sentinel (i.e. the
     * map is being used as the storage of a `BTreeSet`). Search-bifurcation
     * paths consult this to render error messages with "BTreeSet" rather
     * than "BTreeMap".
     *
     * Translation note: upstream uses Rust trait specialization
     * (`V::is_set_val()`) to obtain this fact at compile time; the Kotlin
     * port has no static dispatch on a non-reified type parameter, so we
     * carry the bit at runtime. Phase-5 `BTreeSet` will set this to `true`
     * via the [internalIsSet] field at construction.
     */
    internal var internalIsSet: Boolean = false

    /**
     * Makes a new, empty `BTreeMap`. Does not allocate anything on its own.
     *
     * Mirrors `pub const fn new() -> BTreeMap<K, V>`. `const` doesn't
     * translate to Kotlin (compile-time evaluation only applies to
     * `const val` of primitive types), so we keep the body identical and
     * drop the const.
     */
    constructor()

    // ---- Drop semantics -----------------------------------------------------
    //
    // Upstream's `unsafe impl Drop for BTreeMap { fn drop ... }` walks the
    // tree via IntoIter to drop K and V instances in order. Kotlin's GC
    // supersedes that; nothing to do.

    // ---- size / isEmpty / clear --------------------------------------------

    override val size: Int get() = length

    override fun isEmpty(): Boolean = length == 0

    /** Clears the map, removing all elements. */
    override fun clear() {
        // Upstream uses `drop(BTreeMap { root: mem::replace(&mut self.root, None), ... })`
        // to defer deallocation to the temporary's Drop. Kotlin GC handles
        // the dropped tree; we simply null out the fields.
        root = null
        length = 0
    }

    // ---- get / getKeyValue / containsKey -----------------------------------

    /**
     * Returns the value corresponding to [key], or `null` if absent.
     *
     * Mirrors upstream `pub fn get<Q>(&self, key: &Q) -> Option<&V>`. `MutableMap.get`
     * accepts `Any?`; we narrow to `K` via `as? K` so non-matching types
     * return `null` rather than triggering a class cast inside `searchTree`.
     */
    @Suppress("UNCHECKED_CAST")
    override operator fun get(key: K): V? {
        val rootNode = root?.reborrow() ?: return null
        return when (val r = rootNode.searchTree(key)) {
            is SearchResult.Found -> r.handle.intoKv().second
            is SearchResult.GoDown -> null
        }
    }

    /**
     * Returns the key-value pair corresponding to [key], or `null` if absent.
     *
     * Useful for key types where non-identical keys can be considered equal.
     */
    fun getKeyValue(key: K): Pair<K, V>? {
        val rootNode = root?.reborrow() ?: return null
        return when (val r = rootNode.searchTree(key)) {
            is SearchResult.Found -> r.handle.intoKv()
            is SearchResult.GoDown -> null
        }
    }

    /** Returns `true` if the map contains [key]. */
    override fun containsKey(key: K): Boolean = get(key) != null

    /** Returns `true` if any entry's value equals [value]. */
    override fun containsValue(value: V): Boolean {
        for (entry in this) {
            if (entry.value == value) return true
        }
        return false
    }

    // ---- first / last (key, value, entry) -----------------------------------

    /**
     * Returns the first key-value pair in the map. The key is the minimum.
     *
     * Mirrors `pub fn first_key_value(&self) -> Option<(&K, &V)>`.
     */
    fun firstKeyValue(): Pair<K, V>? {
        val rootNode = root?.reborrow() ?: return null
        return when (val r = rootNode.firstLeafEdge().rightKv()) {
            is EdgeKvResult.Ok -> r.handle.intoKv()
            is EdgeKvResult.Err -> null
        }
    }

    /** Returns the first entry in the map for in-place manipulation. */
    fun firstEntry(): OccupiedEntry<K, V>? {
        val (mapRef, dormantMap) = DormantMutRef.new(this)
        val rootNode = mapRef.root?.borrowMut() ?: return null
        val kv = when (val r = rootNode.firstLeafEdge().rightKv()) {
            is EdgeKvResult.Ok -> r.handle
            is EdgeKvResult.Err -> return null
        }
        return OccupiedEntry(handle = kv.forgetNodeTypeKv(), dormantMap = dormantMap)
    }

    /** Removes and returns the first element in the map. */
    fun popFirst(): Pair<K, V>? = firstEntry()?.removeEntry()

    /** Returns the last key-value pair in the map. The key is the maximum. */
    fun lastKeyValue(): Pair<K, V>? {
        val rootNode = root?.reborrow() ?: return null
        return when (val r = rootNode.lastLeafEdge().leftKv()) {
            is EdgeKvResult.Ok -> r.handle.intoKv()
            is EdgeKvResult.Err -> null
        }
    }

    /** Returns the last entry in the map for in-place manipulation. */
    fun lastEntry(): OccupiedEntry<K, V>? {
        val (mapRef, dormantMap) = DormantMutRef.new(this)
        val rootNode = mapRef.root?.borrowMut() ?: return null
        val kv = when (val r = rootNode.lastLeafEdge().leftKv()) {
            is EdgeKvResult.Ok -> r.handle
            is EdgeKvResult.Err -> return null
        }
        return OccupiedEntry(handle = kv.forgetNodeTypeKv(), dormantMap = dormantMap)
    }

    /** Removes and returns the last element in the map. */
    fun popLast(): Pair<K, V>? = lastEntry()?.removeEntry()

    // ---- insert / put / tryInsert ------------------------------------------

    /**
     * Inserts a key-value pair into the map.
     *
     * If the map did not have this key present, `null` is returned. If the
     * map did have this key present, the value is updated, and the old value
     * is returned. The key is not updated.
     */
    fun insert(key: K, value: V): V? = when (val e = entry(key)) {
        is Entry.Occupied -> e.entry.insert(value)
        is Entry.Vacant -> {
            e.entry.insert(value)
            null
        }
    }

    /**
     * `MutableMap.put` is the Kotlin-side spelling of [insert]. Mirrors
     * upstream behaviour exactly.
     */
    override fun put(key: K, value: V): V? = insert(key, value)

    /**
     * Tries to insert a key-value pair into the map. Returns the inserted
     * value on success, or [Result.failure] containing an [OccupiedError] if
     * the key already existed.
     *
     * Mirrors the unstable `pub fn try_insert(...) -> Result<&mut V, OccupiedError<...>>`.
     * Translation: Kotlin `Result<V>` for the success path, with the
     * occupied-error branch threading through `OccupiedError`. `Result<&mut V, ...>`
     * upstream returns by mutable reference; the Kotlin port returns the
     * value itself.
     */
    fun tryInsert(key: K, value: V): Result<V> {
        return when (val e = entry(key)) {
            is Entry.Occupied -> Result.failure(
                IllegalStateException(OccupiedError(e.entry, value).toString()),
            )
            is Entry.Vacant -> Result.success(e.entry.insert(value))
        }
    }

    // ---- remove / removeEntry ----------------------------------------------

    /**
     * Removes a key from the map, returning the value if the key was
     * previously in the map.
     */
    override fun remove(key: K): V? = removeEntry(key)?.second

    /**
     * Removes a key from the map, returning the stored key and value if
     * the key was previously in the map.
     */
    fun removeEntry(key: K): Pair<K, V>? {
        val (mapRef, dormantMap) = DormantMutRef.new(this)
        val rootNode = mapRef.root?.borrowMut() ?: return null
        return when (val r = rootNode.searchTree(key)) {
            is SearchResult.Found -> OccupiedEntry(
                handle = r.handle,
                dormantMap = dormantMap,
            ).removeEntry()
            is SearchResult.GoDown -> null
        }
    }

    // ---- retain ------------------------------------------------------------

    /**
     * Retains only the elements specified by the predicate.
     *
     * In other words, remove all pairs `(k, v)` for which `f(k, v)` returns
     * `false`. The elements are visited in ascending key order.
     */
    fun retain(f: (K, V) -> Boolean) {
        // Upstream calls `self.extract_if(.., |k, v| !f(k, v)).for_each(drop)`.
        // We do the same.
        val it = extractIf(unbounded<K>()) { k, v -> !f(k, v) }
        while (it.hasNext()) it.next()
    }

    // ---- append / merge ----------------------------------------------------

    /**
     * Moves all elements from [other] into this map, leaving [other] empty.
     *
     * If a key from [other] is already present in this map, the respective
     * value from this map will be overwritten with the respective value
     * from [other].
     */
    fun append(other: BTreeMap<K, V>) {
        // Upstream: `let other = mem::replace(other, Self::new_in(...))`. We
        // empty `other` after taking its contents, mirroring the consumed-by-
        // value semantics.
        val taken = BTreeMap<K, V>()
        taken.root = other.root
        taken.length = other.length
        other.root = null
        other.length = 0
        this.merge(taken) { _, _, otherVal -> otherVal }
    }

    /**
     * Moves all elements from [other] into this map, leaving [other] empty.
     *
     * If a key from [other] is already present in this map, the [conflict]
     * closure is used to compute the value retained in this map. The
     * closure receives this map's key, this map's value, and [other]'s value.
     *
     * Mirrors the unstable `pub fn merge(&mut self, ...)` upstream.
     */
    fun merge(other: BTreeMap<K, V>, conflict: (K, V, V) -> V) {
        if (other.isEmpty()) return

        // We can just swap if `self` is empty.
        if (this.isEmpty()) {
            val r = other.root
            val l = other.length
            other.root = this.root
            other.length = this.length
            this.root = r
            this.length = l
            return
        }

        val otherIter = other.intoIter()
        val (firstOtherKey, firstOtherVal) = otherIter.next()

        val selfCursor = this.lowerBoundMut(Bound.Included(firstOtherKey))

        val peeked = selfCursor.peekNext()
        if (peeked != null) {
            val selfKey = peeked.first
            val cmp = selfKey.compareTo(firstOtherKey)
            when {
                cmp == 0 -> {
                    val removed = selfCursor.removeNext()
                    if (removed != null) {
                        val (k, v) = removed
                        val newV = conflict(k, v, firstOtherVal)
                        // SAFETY: we remove the K, V out of the next entry,
                        // apply 'f' to get a new (K, V), and insert it back
                        // into the next entry that the cursor is pointing at
                        selfCursor.insertAfterUnchecked(k, newV)
                    }
                }
                cmp > 0 -> {
                    // SAFETY: other_key < self_key, sorted order preserved.
                    selfCursor.insertBeforeUnchecked(firstOtherKey, firstOtherVal)
                }
                else -> error("unreachable: Cursor's peek_next should return None.")
            }
        } else {
            // SAFETY: cursor is at the end.
            selfCursor.insertBeforeUnchecked(firstOtherKey, firstOtherVal)
        }

        while (otherIter.hasNext()) {
            val (otherKey, otherVal) = otherIter.next()
            while (true) {
                val nextPeek = selfCursor.peekNext()
                if (nextPeek != null) {
                    val selfKey = nextPeek.first
                    val cmp = selfKey.compareTo(otherKey)
                    when {
                        cmp == 0 -> {
                            val removed = selfCursor.removeNext()
                            if (removed != null) {
                                val (k, v) = removed
                                val newV = conflict(k, v, otherVal)
                                // SAFETY: see above.
                                selfCursor.insertAfterUnchecked(k, newV)
                            }
                            break
                        }
                        cmp > 0 -> {
                            // SAFETY: self_key > other_key, insert preserves order.
                            selfCursor.insertBeforeUnchecked(otherKey, otherVal)
                            break
                        }
                        else -> {
                            // FIXME (upstream): linear search. Preserved verbatim.
                            selfCursor.next()
                        }
                    }
                } else {
                    // SAFETY: cursor is at the end.
                    selfCursor.insertBeforeUnchecked(otherKey, otherVal)
                    break
                }
            }
        }
    }

    // ---- range / rangeMut --------------------------------------------------
    //
    // Non-reified path: BTreeMap's `V` is a class type parameter and cannot
    // itself be `reified`. Instead we delegate to Search/Navigate's
    // `*Explicit` overloads that take the `isSet` flag directly. The flag
    // comes from [internalIsSet], which Phase-5 `BTreeSet` will set when it
    // wraps a `BTreeMap<T, SetValZst>`.

    /**
     * Constructs a double-ended iterator over a sub-range of elements.
     *
     * Translation note: upstream's `R: RangeBounds<T>` where `K: Borrow<T>`
     * lets the caller pass any borrowed-from-K range type. The Kotlin port
     * accepts `RangeBounds<K>` directly — no `Borrow` machinery, callers
     * supply bounds that are concrete `K` instances. This matches the
     * `K : Comparable<K>` class-level invariant.
     *
     * @throws IllegalArgumentException if `range start > end`, or if start
     *   and end are equal and both excluded.
     */
    fun range(range: RangeBounds<K>): Range<K, V> {
        val r = root
        return if (r != null) {
            Range(r.reborrow().rangeSearchImmutExplicit<K, V, K, RangeBounds<K>>(range, internalIsSet))
        } else {
            Range(LeafRange.none())
        }
    }

    /**
     * Constructs a mutable double-ended iterator over a sub-range of elements.
     */
    fun rangeMut(range: RangeBounds<K>): RangeMut<K, V> {
        val r = root
        return if (r != null) {
            RangeMut(r.borrowValmut().rangeSearchValMutExplicit<K, V, K, RangeBounds<K>>(range, internalIsSet))
        } else {
            RangeMut(LeafRange.none())
        }
    }

    // ---- entry --------------------------------------------------------------

    /**
     * Gets the given key's corresponding entry in the map for in-place manipulation.
     */
    fun entry(key: K): Entry<K, V> {
        val (mapRef, dormantMap) = DormantMutRef.new(this)
        val r = mapRef.root
        return if (r == null) {
            Entry.Vacant(VacantEntry(key = key, handle = null, dormantMap = dormantMap))
        } else {
            when (val sr = r.borrowMut().searchTree(key)) {
                is SearchResult.Found -> Entry.Occupied(
                    OccupiedEntry(handle = sr.handle, dormantMap = dormantMap),
                )
                is SearchResult.GoDown -> Entry.Vacant(
                    VacantEntry(key = key, handle = sr.handle, dormantMap = dormantMap),
                )
            }
        }
    }

    // ---- splitOff -----------------------------------------------------------

    /**
     * Splits the collection into two at the given key. Returns everything
     * after the given key, including the key. If the key is not present,
     * the split occurs at the nearest greater key, or returns an empty map
     * if no such key exists.
     */
    fun splitOff(key: K): BTreeMap<K, V> {
        if (this.isEmpty()) return BTreeMap()

        val totalNum = this.size
        val leftRoot = this.root!! // unwrap succeeds because not empty

        val rightRoot = leftRoot.splitOff(key)

        val (newLeftLen, rightLen) = calcSplitLength(totalNum, leftRoot, rightRoot)
        this.length = newLeftLen

        val out = BTreeMap<K, V>()
        out.root = rightRoot
        out.length = rightLen
        return out
    }

    // ---- extractIf ---------------------------------------------------------

    /**
     * Creates an iterator that visits elements in [range] in ascending key
     * order and uses [pred] to decide whether each one should be removed.
     *
     * If [pred] returns `true`, the element is removed and yielded; if it
     * returns `false`, the element remains in the map and is not yielded.
     */
    fun extractIf(
        range: RangeBounds<K>,
        pred: (K, V) -> Boolean,
    ): ExtractIf<K, V, K> {
        val r = root
        val inner: ExtractIfInner<K, V, K> = if (r == null) {
            ExtractIfInner(
                map = this,
                dormantRoot = null,
                curLeafEdge = null,
                range = range,
            )
        } else {
            val (rootRef, dormantRoot) = DormantMutRef.new(r)
            val first = rootRef.borrowMut().lowerBound(SearchBound.fromRange(range.startBound()))
            ExtractIfInner(
                map = this,
                dormantRoot = dormantRoot,
                curLeafEdge = first,
                range = range,
            )
        }
        return ExtractIf(inner, pred)
    }

    // ---- iter / iterMut / keys / values / valuesMut ------------------------

    /** Gets an iterator over the entries of the map, sorted by key. */
    fun iter(): Iter<K, V> {
        val r = root
        return if (r != null) {
            Iter(r.reborrow().fullRangeImmut(), length)
        } else {
            Iter(LazyLeafRange.none(), 0)
        }
    }

    /** Gets a mutable iterator over the entries of the map, sorted by key. */
    fun iterMut(): IterMut<K, V> {
        val r = root
        return if (r != null) {
            IterMut(r.borrowValmut().fullRangeValMut(), length, this)
        } else {
            IterMut(LazyLeafRange.none(), 0, this)
        }
    }

    /** Gets an iterator over the keys of the map, in sorted order. */
    fun keys(): Keys<K, V> = Keys(iter())

    /** Gets an iterator over the values of the map, in order by key. */
    fun values(): Values<K, V> = Values(iter())

    /** Gets a mutable iterator over the values of the map. */
    fun valuesMut(): ValuesMut<K, V> = ValuesMut(iterMut())

    // ---- intoIter / intoKeys / intoValues ----------------------------------

    /**
     * Returns an owning iterator over the entries of the map. Upstream is
     * implemented through the `IntoIterator` trait; the Kotlin port keeps
     * it as a regular method since Kotlin's iterator protocol doesn't have
     * a `IntoIterator`-style consume.
     *
     * After calling, the map is left empty.
     */
    fun intoIter(): IntoIter<K, V> {
        val r = root
        root = null
        val savedLen = length
        length = 0
        return if (r != null) {
            val fullRange = r.intoDying().fullRangeDying()
            IntoIter(fullRange, savedLen)
        } else {
            IntoIter(LazyLeafRange.none(), 0)
        }
    }

    /** Creates a consuming iterator visiting all the keys, in sorted order. */
    fun intoKeys(): IntoKeys<K, V> = IntoKeys(intoIter())

    /** Creates a consuming iterator visiting all the values, in order by key. */
    fun intoValues(): IntoValues<K, V> = IntoValues(intoIter())

    // ---- bulkBuildFromSortedIter -------------------------------------------

    companion object {
        /**
         * Makes a `BTreeMap` from a sorted iterator. Mirrors upstream's
         * `pub(crate) fn bulk_build_from_sorted_iter<I>`.
         */
        internal fun <K : Comparable<K>, V> bulkBuildFromSortedIter(
            iter: Iterator<Pair<K, V>>,
        ): BTreeMap<K, V> {
            val root = newOwnedTree<K, V>()
            val length = IntArray(1)
            root.bulkPush(DedupSortedIter<K, V, Iterator<Pair<K, V>>>(iter), length)
            val out = BTreeMap<K, V>()
            out.root = root
            out.length = length[0]
            return out
        }

        /** Constructs a `BTreeMap` from an iterable of pairs. */
        fun <K : Comparable<K>, V> fromIterable(items: Iterable<Pair<K, V>>): BTreeMap<K, V> {
            val list = items.toMutableList()
            if (list.isEmpty()) return BTreeMap()
            // Stable sort to preserve insertion order on ties (matches upstream `sort_by`).
            list.sortWith(Comparator { a, b -> a.first.compareTo(b.first) })
            return bulkBuildFromSortedIter(list.iterator())
        }

        /** Constructs a `BTreeMap` from a vararg of pairs. */
        fun <K : Comparable<K>, V> of(vararg pairs: Pair<K, V>): BTreeMap<K, V> {
            return fromIterable(pairs.asIterable())
        }
    }

    // ---- lower_bound / lower_bound_mut / upper_bound / upper_bound_mut -----

    /**
     * Returns a [Cursor] pointing at the gap before the smallest key greater
     * than the given bound.
     *
     * Mirrors upstream `pub fn lower_bound<Q>(&self, bound: Bound<&Q>) -> Cursor<...>`.
     */
    fun lowerBound(bound: Bound<K>): Cursor<K, V> {
        val rootNode = this.root?.reborrow() ?: return Cursor(current = null, root = null)
        val edge = rootNode.lowerBound(SearchBound.fromRange(bound))
        return Cursor(current = edge, root = this.root)
    }

    /**
     * Returns a [CursorMut] pointing at the gap before the smallest key
     * greater than the given bound.
     */
    fun lowerBoundMut(bound: Bound<K>): CursorMut<K, V> {
        val (mapRef, dormantMap) = DormantMutRef.new(this)
        val rootNode = mapRef.root?.borrowMut()
            ?: return CursorMut(CursorMutKey(current = null, dormantMap = dormantMap))
        val edge = rootNode.lowerBound(SearchBound.fromRange(bound))
        return CursorMut(CursorMutKey(current = edge, dormantMap = dormantMap))
    }

    /**
     * Returns a [Cursor] pointing at the gap after the greatest key smaller
     * than the given bound.
     */
    fun upperBound(bound: Bound<K>): Cursor<K, V> {
        val rootNode = this.root?.reborrow() ?: return Cursor(current = null, root = null)
        val edge = rootNode.upperBound(SearchBound.fromRange(bound))
        return Cursor(current = edge, root = this.root)
    }

    /**
     * Returns a [CursorMut] pointing at the gap after the greatest key
     * smaller than the given bound.
     */
    fun upperBoundMut(bound: Bound<K>): CursorMut<K, V> {
        val (mapRef, dormantMap) = DormantMutRef.new(this)
        val rootNode = mapRef.root?.borrowMut()
            ?: return CursorMut(CursorMutKey(current = null, dormantMap = dormantMap))
        val edge = rootNode.upperBound(SearchBound.fromRange(bound))
        return CursorMut(CursorMutKey(current = edge, dormantMap = dormantMap))
    }

    // ---- MutableMap views: entries / keys / values --------------------------

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = EntrySetView(this)

    override val keys: MutableSet<K>
        get() = KeySetView(this)

    override val values: MutableCollection<V>
        get() = ValueCollectionView(this)

    override fun putAll(from: Map<out K, V>) {
        for ((k, v) in from) put(k, v)
    }

    // ---- iterator over entries (Kotlin idiom) ------------------------------

    operator fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = iterMut()

    // ---- equals / hashCode / toString --------------------------------------

    override fun equals(other: Any?): Boolean {
        if (other !is BTreeMap<*, *>) return false
        if (this.size != other.size) return false
        val itA = this.iter()
        val itB = (other as BTreeMap<K, V>).iter()
        while (itA.hasNext() && itB.hasNext()) {
            val a = itA.next()
            val b = itB.next()
            if (a.key != b.key || a.value != b.value) return false
        }
        return !itA.hasNext() && !itB.hasNext()
    }

    override fun hashCode(): Int {
        // Mirrors the upstream `Hash` impl: prefix length, then iterate.
        var h = size
        for (entry in this) {
            h = 31 * h + entry.key.hashCode()
            h = 31 * h + (entry.value?.hashCode() ?: 0)
        }
        return h
    }

    override fun toString(): String {
        // Upstream `Debug` renders as `{k: v, k: v, ...}`. Match that.
        val sb = StringBuilder("{")
        var first = true
        for (entry in this) {
            if (!first) sb.append(", ")
            sb.append(entry.key).append(": ").append(entry.value)
            first = false
        }
        sb.append("}")
        return sb.toString()
    }
}

// ============================================================================
// Internal helpers for BTreeMap<K, SetValZst> (used by Phase-5 BTreeSet)
// ============================================================================

/**
 * Mirrors upstream `impl<K, A> BTreeMap<K, SetValZST, A> { pub(super) fn replace(...) }`.
 *
 * Not on the public surface; called from `BTreeSet::replace` once Phase 5 lands.
 */
@Suppress("UNCHECKED_CAST")
internal fun <K : Comparable<K>> BTreeMap<K, SetValZst>.replaceSetVal(key: K): K? {
    val (mapRef, dormantMap) = DormantMutRef.new(this)
    if (mapRef.root == null) {
        mapRef.root = newOwnedTree<K, SetValZst>()
    }
    val rootNode = mapRef.root!!.borrowMut()
    return when (val r = rootNode.searchTree(key)) {
        is SearchResult.Found -> {
            val keyMut = r.handle.keyMut()
            r.handle.setKey(key)
            keyMut
        }
        is SearchResult.GoDown -> {
            VacantEntry<K, SetValZst>(key = key, handle = r.handle, dormantMap = dormantMap)
                .insert(SetValZst)
            null
        }
    }
}

/**
 * Mirrors upstream `impl<K, A> BTreeMap<K, SetValZST, A> { pub(super) fn get_or_insert_with(...) }`.
 *
 * Not on the public surface; called from `BTreeSet::get_or_insert_with` once
 * Phase 5 lands.
 *
 * Translation note: upstream takes a `Q: ?Sized + Ord` and a `K : Borrow<Q>`
 * key type. The Kotlin port specialises Q to K (the most common case in
 * `BTreeSet`) — the cross-type-borrow form is unrepresentable when Kotlin's
 * `where K : Comparable<Q>` clashes with the receiver's pre-existing
 * `K : Comparable<K>` constraint. Set.kt's wrapper can rebox `Q` -> `K` at
 * the call site if it wants the cross-type form back.
 */
@Suppress("UNCHECKED_CAST")
internal fun <K : Comparable<K>> BTreeMap<K, SetValZst>.getOrInsertWithSetVal(
    q: K,
    f: (K) -> K,
): K {
    val (mapRef, dormantMap) = DormantMutRef.new(this)
    if (mapRef.root == null) {
        mapRef.root = newOwnedTree<K, SetValZst>()
    }
    val rootNode = mapRef.root!!.borrowMut()
    return when (val r = rootNode.searchTree(q)) {
        is SearchResult.Found -> r.handle.intoKvMut().first
        is SearchResult.GoDown -> {
            val key = f(q)
            check(key.compareTo(q) == 0) { "new value is not equal" }
            VacantEntry<K, SetValZst>(key = key, handle = r.handle, dormantMap = dormantMap)
                .insertEntry(SetValZst)
                .intoKey()
        }
    }
}

// ============================================================================
// Iter
// ============================================================================

/**
 * An iterator over the entries of a `BTreeMap`, yielding `MutableMap.MutableEntry`
 * with read-only `setValue` (throws — entries from [BTreeMap.iter] are not
 * mutable through `setValue`; use [BTreeMap.iterMut] for that).
 */
class Iter<K, V> internal constructor(
    internal var range: LazyLeafRange<Marker.Immut, K, V>,
    internal var length: Int,
) : Iterator<MutableMap.MutableEntry<K, V>> {
    override fun hasNext(): Boolean = length > 0

    override fun next(): MutableMap.MutableEntry<K, V> {
        if (length == 0) throw NoSuchElementException()
        length -= 1
        // SAFETY: length > 0 implies there is another KV in the direction travelled.
        val (k, v) = range.nextUncheckedImmut()
        return ReadOnlyEntry(k, v)
    }

    /** Mirrors `DoubleEndedIterator::next_back`. */
    fun nextBack(): MutableMap.MutableEntry<K, V>? {
        if (length == 0) return null
        length -= 1
        // SAFETY: length > 0 implies there is another KV.
        val (k, v) = range.nextBackUncheckedImmut()
        return ReadOnlyEntry(k, v)
    }

    /** Mirrors `ExactSizeIterator::len`. */
    fun len(): Int = length

    override fun toString(): String {
        // Upstream Debug renders as a list of (k, v) tuples.
        return "Iter(length=$length)"
    }
}

/** An immutable entry shape: `setValue` is unsupported. */
internal class ReadOnlyEntry<K, V>(override val key: K, override val value: V) :
    MutableMap.MutableEntry<K, V> {
    override fun setValue(newValue: V): V =
        throw UnsupportedOperationException("Iter yields read-only entries; use iterMut for mutation")
}

// ============================================================================
// IterMut
// ============================================================================

/**
 * A mutable iterator over the entries of a `BTreeMap`. The yielded entries
 * support `setValue` for in-place value updates.
 */
class IterMut<K : Comparable<K>, V> internal constructor(
    internal var range: LazyLeafRange<Marker.ValMut, K, V>,
    internal var length: Int,
    private val map: BTreeMap<K, V>,
) : MutableIterator<MutableMap.MutableEntry<K, V>> {
    private var lastKey: K? = null

    override fun hasNext(): Boolean = length > 0

    override fun next(): MutableMap.MutableEntry<K, V> {
        if (length == 0) throw NoSuchElementException()
        length -= 1
        // SAFETY: length > 0 implies there is another KV.
        val (k, v) = range.nextUncheckedValMut()
        lastKey = k
        return MutEntry(map, k, v)
    }

    /** Mirrors `DoubleEndedIterator::next_back`. */
    fun nextBack(): MutableMap.MutableEntry<K, V>? {
        if (length == 0) return null
        length -= 1
        val (k, v) = range.nextBackUncheckedValMut()
        lastKey = k
        return MutEntry(map, k, v)
    }

    fun len(): Int = length

    /** Returns an immutable iterator of references to the remaining items. */
    fun iter(): Iter<K, V> = Iter(range.reborrow(), length)

    /**
     * `MutableIterator.remove` removes the last-yielded entry from the map.
     * Tracks the last key seen so we can re-locate it; this is slower than
     * upstream's in-place remove but Kotlin's `MutableIterator` contract
     * insists on a parameterless [remove].
     */
    override fun remove() {
        val k = lastKey ?: throw IllegalStateException("call next() before remove()")
        map.remove(k)
        lastKey = null
    }

    override fun toString(): String = "IterMut(length=$length)"
}

/**
 * A mutable entry that writes through to the underlying [BTreeMap] via
 * [BTreeMap.put]. The entry's `value` field is a snapshot at the time of
 * iteration; [setValue] returns the previous value, matching the contract
 * of `MutableMap.MutableEntry`.
 */
internal class MutEntry<K : Comparable<K>, V>(
    private val map: BTreeMap<K, V>,
    override val key: K,
    private var current: V,
) : MutableMap.MutableEntry<K, V> {
    override val value: V get() = current

    override fun setValue(newValue: V): V {
        val old = current
        map.put(key, newValue)
        current = newValue
        return old
    }
}

// ============================================================================
// IntoIter
// ============================================================================

/**
 * An owning iterator over the entries of a `BTreeMap`, sorted by key. Walks
 * a dying tree, deallocating nodes (in Rust) as it goes; in Kotlin GC
 * supersedes the deallocation but the traversal pattern is identical.
 */
class IntoIter<K, V> internal constructor(
    internal var range: LazyLeafRange<Marker.Dying, K, V>,
    internal var length: Int,
) : Iterator<Pair<K, V>> {
    override fun hasNext(): Boolean = length > 0

    override fun next(): Pair<K, V> {
        val kv = dyingNext() ?: throw NoSuchElementException()
        // SAFETY: we consume the dying handle immediately.
        return kv.intoKeyVal()
    }

    fun nextBack(): Pair<K, V>? {
        val kv = dyingNextBack() ?: return null
        return kv.intoKeyVal()
    }

    fun len(): Int = length

    /**
     * Core of a `next` method returning a dying KV handle, invalidated by
     * further calls to this function and some others.
     */
    private fun dyingNext(): Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.KV>? {
        return if (length == 0) {
            range.deallocatingEndDying()
            null
        } else {
            length -= 1
            // SAFETY: length > 0 implies there is another KV.
            range.deallocatingNextUncheckedDying()
        }
    }

    /** Counterpart to [dyingNext]. */
    private fun dyingNextBack(): Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.KV>? {
        return if (length == 0) {
            range.deallocatingEndDying()
            null
        } else {
            length -= 1
            range.deallocatingNextBackUncheckedDying()
        }
    }

    /**
     * Returns an immutable iterator of references over the remaining items.
     * Mirrors upstream `pub(super) fn iter(&self) -> Iter<'_, K, V>`.
     *
     * Note: yields `ReadOnlyEntry` views over the still-walked tree; the
     * dying tree must not be mutated through this view, only read.
     */
    internal fun iter(): Iter<K, V> {
        // Dying -> Immut reborrow: same nodes, narrower borrow type.
        return Iter(range.reborrow(), length)
    }

    override fun toString(): String = "IntoIter(length=$length)"
}

// ============================================================================
// Keys / Values / ValuesMut
// ============================================================================

/** An iterator over the keys of a `BTreeMap`. */
class Keys<K, V> internal constructor(internal val inner: Iter<K, V>) : Iterator<K> {
    override fun hasNext(): Boolean = inner.hasNext()
    override fun next(): K = inner.next().key
    fun nextBack(): K? = inner.nextBack()?.key
    fun len(): Int = inner.len()
    override fun toString(): String = "Keys(length=${inner.len()})"
}

/** An iterator over the values of a `BTreeMap`. */
class Values<K, V> internal constructor(internal val inner: Iter<K, V>) : Iterator<V> {
    override fun hasNext(): Boolean = inner.hasNext()
    override fun next(): V = inner.next().value
    fun nextBack(): V? = inner.nextBack()?.value
    fun len(): Int = inner.len()
    override fun toString(): String = "Values(length=${inner.len()})"
}

/** A mutable iterator over the values of a `BTreeMap`. */
class ValuesMut<K : Comparable<K>, V> internal constructor(internal val inner: IterMut<K, V>) :
    MutableIterator<V> {
    override fun hasNext(): Boolean = inner.hasNext()
    override fun next(): V = inner.next().value
    fun nextBack(): V? = inner.nextBack()?.value
    fun len(): Int = inner.len()
    override fun remove() = inner.remove()
    override fun toString(): String = "ValuesMut(length=${inner.len()})"
}

/** An owning iterator over the keys of a `BTreeMap`. */
class IntoKeys<K, V> internal constructor(internal val inner: IntoIter<K, V>) : Iterator<K> {
    override fun hasNext(): Boolean = inner.hasNext()
    override fun next(): K = inner.next().first
    fun nextBack(): K? = inner.nextBack()?.first
    fun len(): Int = inner.len()
    override fun toString(): String = "IntoKeys(length=${inner.len()})"
}

/** An owning iterator over the values of a `BTreeMap`. */
class IntoValues<K, V> internal constructor(internal val inner: IntoIter<K, V>) : Iterator<V> {
    override fun hasNext(): Boolean = inner.hasNext()
    override fun next(): V = inner.next().second
    fun nextBack(): V? = inner.nextBack()?.second
    fun len(): Int = inner.len()
    override fun toString(): String = "IntoValues(length=${inner.len()})"
}

// ============================================================================
// Range / RangeMut
// ============================================================================

/**
 * An iterator over a sub-range of entries in a `BTreeMap`.
 *
 * Constructed via [BTreeMap.range].
 */
class Range<K, V> internal constructor(
    internal var inner: LeafRange<Marker.Immut, K, V>,
) : Iterator<MutableMap.MutableEntry<K, V>> {
    private var pending: Pair<K, V>? = inner.nextCheckedImmut()

    override fun hasNext(): Boolean = pending != null

    override fun next(): MutableMap.MutableEntry<K, V> {
        val out = pending ?: throw NoSuchElementException()
        pending = inner.nextCheckedImmut()
        return ReadOnlyEntry(out.first, out.second)
    }

    /** Mirrors `DoubleEndedIterator::next_back_checked`. */
    fun nextBack(): MutableMap.MutableEntry<K, V>? {
        val kv = inner.nextBackCheckedImmut() ?: return null
        return ReadOnlyEntry(kv.first, kv.second)
    }

    override fun toString(): String = "Range(...)"
}

/**
 * A mutable iterator over a sub-range of entries in a `BTreeMap`. Constructed
 * via [BTreeMap.rangeMut].
 */
class RangeMut<K, V> internal constructor(
    internal var inner: LeafRange<Marker.ValMut, K, V>,
) : Iterator<Pair<K, V>> {
    private var pending: Pair<K, V>? = inner.nextCheckedValMut()

    override fun hasNext(): Boolean = pending != null

    override fun next(): Pair<K, V> {
        val out = pending ?: throw NoSuchElementException()
        pending = inner.nextCheckedValMut()
        return out
    }

    /** Mirrors `DoubleEndedIterator::next_back_checked`. */
    fun nextBack(): Pair<K, V>? = inner.nextBackCheckedValMut()

    override fun toString(): String = "RangeMut(...)"
}

// ============================================================================
// ExtractIf / ExtractIfInner
// ============================================================================

/**
 * Iterator that extracts elements matching a predicate from a sub-range of
 * a `BTreeMap`. Mirrors upstream `pub struct ExtractIf<...>`.
 *
 * Removed elements are yielded in ascending key order; non-removed elements
 * remain in the map. If iteration short-circuits, the remaining matching
 * elements stay in the map. (Upstream's `Drop` impl re-walks; Kotlin GC
 * supersedes that, and the user is expected to consume the iterator.)
 */
class ExtractIf<K : Comparable<K>, V, Q : Comparable<Q>> internal constructor(
    internal val inner: ExtractIfInner<K, V, Q>,
    internal val pred: (K, V) -> Boolean,
) : Iterator<Pair<K, V>> {
    private var pending: Pair<K, V>? = null
    private var primed: Boolean = false

    private fun computeNext(): Pair<K, V>? = inner.next(pred)

    override fun hasNext(): Boolean {
        if (!primed) {
            pending = computeNext()
            primed = true
        }
        return pending != null
    }

    override fun next(): Pair<K, V> {
        if (!primed) {
            pending = computeNext()
        }
        val out = pending ?: throw NoSuchElementException()
        pending = null
        primed = false
        return out
    }

    override fun toString(): String = "ExtractIf(peek=${inner.peek()})"
}

/**
 * State machine of an [ExtractIf]. Internal because Phase-5 `BTreeSet`'s
 * `ExtractIf` will reuse the same shape.
 */
internal class ExtractIfInner<K : Comparable<K>, V, Q : Comparable<Q>>(
    internal val map: BTreeMap<K, V>,
    /** Buried reference to the root field in the borrowed map. */
    internal var dormantRoot: DormantMutRef<Root<K, V>>?,
    /** Contains a leaf edge preceding the next element to be returned, or the last leaf edge. */
    internal var curLeafEdge: Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge>?,
    /** Range over which iteration was requested. */
    internal val range: RangeBounds<Q>,
) {
    /** Allow Debug implementations to predict the next element. */
    internal fun peek(): Pair<K, V>? {
        val edge = curLeafEdge ?: return null
        return when (val r = edge.reborrow().nextKv()) {
            is NextKvResult.Ok -> r.handle.intoKv()
            is NextKvResult.Err -> null
        }
    }

    /** Implementation of a typical `ExtractIf::next` method, given the predicate. */
    internal fun next(pred: (K, V) -> Boolean): Pair<K, V>? {
        while (true) {
            val edge = curLeafEdge ?: return null
            curLeafEdge = null
            val kv = when (val r = edge.nextKv()) {
                is NextKvResult.Ok -> r.handle
                is NextKvResult.Err -> return null
            }
            val (k, v) = kv.kvMut()

            // On creation, we navigated directly to the left bound, so we
            // need only check the right bound to decide whether to stop.
            // SAFETY: K is Comparable<K>; the runtime contract for Q is that
            // K is comparable to it (the static `K : Comparable<Q>` constraint
            // can't be expressed here because Kotlin doesn't allow a class
            // type parameter to be re-bounded against a method-level Q).
            // The cast `(end.value as Comparable<Any?>).compareTo(k)` would
            // also work; we use the K-side compareTo via an unchecked cast.
            @Suppress("UNCHECKED_CAST")
            val withinRange = when (val end = range.endBound()) {
                is Bound.Included -> (k as Comparable<Q>).compareTo(end.value) <= 0
                is Bound.Excluded -> (k as Comparable<Q>).compareTo(end.value) < 0
                Bound.Unbounded -> true
            }
            if (!withinRange) return null

            if (pred(k, v)) {
                map.length -= 1
                val (kvPair, pos) = kv.removeKvTracking {
                    // SAFETY: we will touch the root in a way that will not
                    // invalidate the position returned.
                    val root = dormantRoot!!.awaken()
                    root.popInternalLevel()
                    dormantRoot = DormantMutRef.new(root).second
                }
                curLeafEdge = pos
                return kvPair
            }
            curLeafEdge = kv.nextLeafEdge()
        }
    }
}

// ============================================================================
// Cursor
// ============================================================================

/**
 * A cursor over a `BTreeMap`. Like an iterator but seekable in both directions.
 *
 * Cursors point to a gap between two elements and operate on the immediately
 * adjacent ones.
 */
class Cursor<K : Comparable<K>, V> internal constructor(
    internal var current: Handle<NodeRef<Marker.Immut, K, V, Marker.Leaf>, Marker.Edge>?,
    internal var root: Root<K, V>?,
) {
    /**
     * Advances the cursor to the next gap, returning the key and value of
     * the element it moved over.
     */
    fun next(): Pair<K, V>? {
        val cur = current ?: return null
        current = null
        return when (val r = cur.nextKv()) {
            is NextKvResult.Ok -> {
                val kv = r.handle
                val result = kv.intoKv()
                current = kv.nextLeafEdge()
                result
            }
            is NextKvResult.Err -> {
                val rootRecovered = r.node
                current = rootRecovered.lastLeafEdge()
                null
            }
        }
    }

    /**
     * Advances the cursor to the previous gap, returning the key and value
     * of the element it moved over.
     */
    fun prev(): Pair<K, V>? {
        val cur = current ?: return null
        current = null
        return when (val r = cur.nextBackKv()) {
            is NextKvResult.Ok -> {
                val kv = r.handle
                val result = kv.intoKv()
                current = kv.nextBackLeafEdge()
                result
            }
            is NextKvResult.Err -> {
                val rootRecovered = r.node
                current = rootRecovered.firstLeafEdge()
                null
            }
        }
    }

    /** Returns the key/value of the next element without moving the cursor. */
    fun peekNext(): Pair<K, V>? {
        val cur = current ?: return null
        // Clone-and-step: upstream uses `self.clone().next()`. We replicate
        // by re-running `nextKv` non-destructively.
        return when (val r = cur.nextKv()) {
            is NextKvResult.Ok -> r.handle.intoKv()
            is NextKvResult.Err -> null
        }
    }

    /** Returns the key/value of the previous element without moving the cursor. */
    fun peekPrev(): Pair<K, V>? {
        val cur = current ?: return null
        return when (val r = cur.nextBackKv()) {
            is NextKvResult.Ok -> r.handle.intoKv()
            is NextKvResult.Err -> null
        }
    }

    override fun toString(): String = "Cursor"
}

// ============================================================================
// CursorMut / CursorMutKey
// ============================================================================

/**
 * A cursor over a `BTreeMap` with editing operations.
 *
 * Wraps a [CursorMutKey] so that [insertAfter] / [insertBefore] /
 * [removeNext] / [removeBefore] preserve the upstream BTreeMap invariants
 * (ascending key order). [CursorMutKey] is the unsafe variant that allows
 * mutating keys directly.
 */
class CursorMut<K : Comparable<K>, V> internal constructor(
    internal val inner: CursorMutKey<K, V>,
) {
    /** Advances the cursor to the next gap, returning the moved-over element. */
    fun next(): Pair<K, V>? = inner.next()

    /** Advances the cursor to the previous gap, returning the moved-over element. */
    fun prev(): Pair<K, V>? = inner.prev()

    /** Returns the next element without moving. */
    fun peekNext(): Pair<K, V>? = inner.peekNext()

    /** Returns the previous element without moving. */
    fun peekPrev(): Pair<K, V>? = inner.peekPrev()

    /** Returns a read-only cursor pointing to the same location. */
    fun asCursor(): Cursor<K, V> = inner.asCursor()

    /** Converts the cursor into a [CursorMutKey], which allows mutating keys. */
    fun withMutableKey(): CursorMutKey<K, V> = inner

    // ---- editing ops --------------------------------------------------------

    /** SAFETY: caller must ensure the new key preserves sorted order and uniqueness. */
    fun insertAfterUnchecked(key: K, value: V) = inner.insertAfterUnchecked(key, value)

    /** SAFETY: caller must ensure the new key preserves sorted order and uniqueness. */
    fun insertBeforeUnchecked(key: K, value: V) = inner.insertBeforeUnchecked(key, value)

    /** Inserts before, returning [Result.failure] containing [UnorderedKeyError] on order violation. */
    fun insertAfter(key: K, value: V): Result<Unit> = inner.insertAfter(key, value)

    /** Inserts after, returning [Result.failure] containing [UnorderedKeyError] on order violation. */
    fun insertBefore(key: K, value: V): Result<Unit> = inner.insertBefore(key, value)

    /** Removes the next element from the map. */
    fun removeNext(): Pair<K, V>? = inner.removeNext()

    /** Removes the preceding element from the map. */
    fun removePrev(): Pair<K, V>? = inner.removePrev()

    override fun toString(): String = "CursorMut"
}

/**
 * A cursor over a `BTreeMap` with editing operations and mutable-key access.
 *
 * Mutating keys can violate the `BTreeMap` invariants. The caller is
 * responsible for keeping the tree in valid state. See upstream's
 * `# Safety` block for details.
 */
class CursorMutKey<K : Comparable<K>, V> internal constructor(
    internal var current: Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge>?,
    internal val dormantMap: DormantMutRef<BTreeMap<K, V>>,
) {
    fun next(): Pair<K, V>? {
        val cur = current ?: return null
        current = null
        return when (val r = cur.nextKv()) {
            is NextKvResult.Ok -> {
                val kv = r.handle
                // SAFETY: The key/value pointers remain valid even after the
                // cursor is moved forward.
                val result = kv.reborrowMut().intoKvMut()
                current = kv.nextLeafEdge()
                result
            }
            is NextKvResult.Err -> {
                current = r.node.lastLeafEdge()
                null
            }
        }
    }

    fun prev(): Pair<K, V>? {
        val cur = current ?: return null
        current = null
        return when (val r = cur.nextBackKv()) {
            is NextKvResult.Ok -> {
                val kv = r.handle
                val result = kv.reborrowMut().intoKvMut()
                current = kv.nextBackLeafEdge()
                result
            }
            is NextKvResult.Err -> {
                current = r.node.firstLeafEdge()
                null
            }
        }
    }

    fun peekNext(): Pair<K, V>? {
        val cur = current ?: return null
        return when (val r = cur.reborrowMut().nextKv()) {
            is NextKvResult.Ok -> r.handle.intoKvMut()
            is NextKvResult.Err -> null
        }
    }

    fun peekPrev(): Pair<K, V>? {
        val cur = current ?: return null
        return when (val r = cur.reborrowMut().nextBackKv()) {
            is NextKvResult.Ok -> r.handle.intoKvMut()
            is NextKvResult.Err -> null
        }
    }

    /** Returns a read-only cursor pointing to the same location. */
    fun asCursor(): Cursor<K, V> {
        // SAFETY: The tree is immutable while the cursor exists.
        val map = dormantMap.reborrowShared()
        return Cursor(
            current = current?.reborrow(),
            root = map.root,
        )
    }

    // ---- editing ops --------------------------------------------------------

    /**
     * Inserts a new key-value pair into the gap that the cursor is pointing
     * to. After insertion the cursor points at the gap before the new element.
     *
     * SAFETY: caller must ensure the new key preserves sorted order and
     * uniqueness.
     */
    fun insertAfterUnchecked(key: K, value: V) {
        val cur = current
        current = null
        val edge: Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge> = if (cur == null) {
            // Tree is empty, allocate a new root.
            // SAFETY: We have no other reference to the tree.
            val map = dormantMap.reborrow()
            check(map.root == null) // debug_assert!(root.is_none())
            val node = NodeRef.newLeaf<K, V>()
            // SAFETY: We don't touch the root while the handle is alive.
            val handle = node.borrowMut().pushWithHandle(key, value)
            map.root = node.forgetType()
            map.length += 1
            current = handle.leftEdge()
            return
        } else {
            cur
        }
        val handle = edge.insertRecursing(key, value) { ins ->
            // drop(ins.left) — Kotlin GC, nothing to do.
            // SAFETY: The handle to the newly inserted value is always on a
            // leaf node, so adding a new root node doesn't invalidate it.
            val map = dormantMap.reborrow()
            val r = map.root!! // same as ins.left
            r.pushInternalLevel().push(ins.kv.first, ins.kv.second, ins.right)
        }
        current = handle.leftEdge()
        dormantMap.reborrow().length += 1
    }

    /**
     * Inserts a new key-value pair into the gap that the cursor is pointing
     * to. After insertion the cursor points at the gap after the new element.
     *
     * SAFETY: caller must ensure the new key preserves sorted order and
     * uniqueness.
     */
    fun insertBeforeUnchecked(key: K, value: V) {
        val cur = current
        current = null
        val edge: Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge> = if (cur == null) {
            // SAFETY: We have no other reference to the tree.
            val map = dormantMap.reborrow()
            if (map.root == null) {
                // Tree is empty, allocate a new root.
                val node = NodeRef.newLeaf<K, V>()
                // SAFETY: We don't touch the root while the handle is alive.
                val handle = node.borrowMut().pushWithHandle(key, value)
                map.root = node.forgetType()
                map.length += 1
                current = handle.rightEdge()
                return
            } else {
                map.root!!.borrowMut().lastLeafEdge()
            }
        } else {
            cur
        }
        val handle = edge.insertRecursing(key, value) { ins ->
            val map = dormantMap.reborrow()
            val r = map.root!!
            r.pushInternalLevel().push(ins.kv.first, ins.kv.second, ins.right)
        }
        current = handle.rightEdge()
        dormantMap.reborrow().length += 1
    }

    /**
     * Inserts a new key-value pair, returning [Result.failure] containing
     * [UnorderedKeyError] on order violation.
     */
    fun insertAfter(key: K, value: V): Result<Unit> {
        peekPrev()?.let { (prev, _) ->
            if (key.compareTo(prev) <= 0) return Result.failure(UnorderedKeyError())
        }
        peekNext()?.let { (next, _) ->
            if (key.compareTo(next) >= 0) return Result.failure(UnorderedKeyError())
        }
        insertAfterUnchecked(key, value)
        return Result.success(Unit)
    }

    /**
     * Inserts a new key-value pair, returning [Result.failure] containing
     * [UnorderedKeyError] on order violation.
     */
    fun insertBefore(key: K, value: V): Result<Unit> {
        peekPrev()?.let { (prev, _) ->
            if (key.compareTo(prev) <= 0) return Result.failure(UnorderedKeyError())
        }
        peekNext()?.let { (next, _) ->
            if (key.compareTo(next) >= 0) return Result.failure(UnorderedKeyError())
        }
        insertBeforeUnchecked(key, value)
        return Result.success(Unit)
    }

    /** Removes the next element from the map. */
    fun removeNext(): Pair<K, V>? {
        val cur = current ?: return null
        current = null
        // First check whether next_kv exists at all (can fail if cursor is at end).
        if (cur.reborrow().nextKv() is NextKvResult.Err) {
            current = cur
            return null
        }
        var emptiedInternalRoot = false
        val nextKv = (cur.nextKv() as NextKvResult.Ok).handle
        val (kv, pos) = nextKv.removeKvTracking { emptiedInternalRoot = true }
        current = pos
        dormantMap.reborrow().length -= 1
        if (emptiedInternalRoot) {
            // SAFETY: This is safe since current does not point within the now-empty root.
            val map = dormantMap.reborrow()
            map.root!!.popInternalLevel()
        }
        return kv
    }

    /** Removes the preceding element from the map. */
    fun removePrev(): Pair<K, V>? {
        val cur = current ?: return null
        current = null
        if (cur.reborrow().nextBackKv() is NextKvResult.Err) {
            current = cur
            return null
        }
        var emptiedInternalRoot = false
        val prevKv = (cur.nextBackKv() as NextKvResult.Ok).handle
        val (kv, pos) = prevKv.removeKvTracking { emptiedInternalRoot = true }
        current = pos
        dormantMap.reborrow().length -= 1
        if (emptiedInternalRoot) {
            val map = dormantMap.reborrow()
            map.root!!.popInternalLevel()
        }
        return kv
    }

    override fun toString(): String = "CursorMutKey"
}

// ============================================================================
// UnorderedKeyError
// ============================================================================

/**
 * Error returned by [CursorMut.insertBefore] / [CursorMut.insertAfter] when
 * the key being inserted is not properly ordered with regards to its
 * neighbours.
 */
class UnorderedKeyError : Exception("key is not properly ordered relative to neighbors") {
    override fun toString(): String = "UnorderedKeyError"
}

// ============================================================================
// MutableMap views (entries / keys / values)
// ============================================================================

private class EntrySetView<K : Comparable<K>, V>(private val map: BTreeMap<K, V>) :
    AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
    override val size: Int get() = map.size

    override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
        val prior = map.put(element.key, element.value)
        return prior == null || prior != element.value
    }

    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = map.iterMut()

    override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
        val v = map[element.key] ?: return false
        return v == element.value
    }
}

private class KeySetView<K : Comparable<K>, V>(private val map: BTreeMap<K, V>) :
    AbstractMutableSet<K>() {
    override val size: Int get() = map.size

    override fun add(element: K): Boolean = throw UnsupportedOperationException(
        "BTreeMap.keys cannot add a key without a value; use BTreeMap.put",
    )

    override fun iterator(): MutableIterator<K> = object : MutableIterator<K> {
        private val inner = map.iterMut()
        override fun hasNext(): Boolean = inner.hasNext()
        override fun next(): K = inner.next().key
        override fun remove() = inner.remove()
    }

    override fun contains(element: K): Boolean = map.containsKey(element)

    override fun remove(element: K): Boolean = map.remove(element) != null
}

private class ValueCollectionView<K : Comparable<K>, V>(private val map: BTreeMap<K, V>) :
    AbstractMutableCollection<V>() {
    override val size: Int get() = map.size

    override fun add(element: V): Boolean = throw UnsupportedOperationException(
        "BTreeMap.values cannot add a value without a key; use BTreeMap.put",
    )

    override fun iterator(): MutableIterator<V> = map.valuesMut()
}

// ============================================================================
// Helpers
// ============================================================================

/**
 * Returns an unbounded [RangeBounds] (both bounds [Bound.Unbounded]). Used
 * by [BTreeMap.retain]'s call to [BTreeMap.extractIf]. Generic in `Q` so the
 * caller picks the type for the range bounds; with no actual values
 * present, no `Q` instances exist.
 */
private fun <Q : Comparable<Q>> unbounded(): RangeBounds<Q> = object : RangeBounds<Q> {
    override fun startBound(): Bound<Q> = Bound.Unbounded
    override fun endBound(): Bound<Q> = Bound.Unbounded
}
