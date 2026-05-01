// port-lint: source map.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

/**
 * Minimum number of elements in a node that is not a root.
 * We might temporarily have fewer elements during methods.
 */
internal const val MIN_LEN: Int = MIN_LEN_AFTER_SPLIT

// ============================================================================
// BTreeMap
// ============================================================================

/**
 * An ordered map based on a B-Tree.
 *
 * Implements [MutableMap] so consumers can import the Kotlin collections idioms
 * for free.
 *
 * The key type is constrained to [Comparable] so ordering is available across
 * all operations.
 */
class BTreeMap<K : Comparable<K>, V> : MutableMap<K, V> {
    internal var root: NodeRef<Marker.Owned, K, V, Marker.LeafOrInternal>? = null
    internal var length: Int = 0

    /**
     * `true` if this map's `V` slot is the [SetValZst] sentinel (i.e. the
     * map is being used as the storage of a `BTreeSet`). Search-bifurcation
     * paths consult this to render error messages with "BTreeSet" rather
     * than "BTreeMap". `BTreeSet` sets it to `true` at construction.
     */
    internal var internalIsSet: Boolean = false

    /** Makes a new, empty `BTreeMap`. Does not allocate anything on its own. */
    constructor()

    // ---- size / isEmpty / clear --------------------------------------------

    override val size: Int get() = length

    override fun isEmpty(): Boolean = length == 0

    /** Clears the map, removing all elements. */
    override fun clear() {
        root = null
        length = 0
    }

    // ---- get / getKeyValue / containsKey -----------------------------------

    /**
     * Returns the value corresponding to [key], or `null` if absent.
     *
     * Returns `null` rather than throwing when the key is absent.
     */
    override operator fun get(key: K): V? {
        val rootNode = root?.reborrow() ?: return null
        return when (val r = rootNode.searchTree(key)) {
            is SearchResult.Found -> r.handle.intoKv().second
            is SearchResult.GoDown -> null
        }
    }

    /**
     * Returns a mutable reference to the value corresponding to the key.
     *
     * The key may be any borrowed form of the map's key type, but
     * [Any.hashCode] and [Any.equals] on the borrowed form *must* match those for
     * the key type.
     */
    fun getMut(key: K): V? {
        val rootNode = root?.borrowMut() ?: return null
        return when (val r = rootNode.searchTree(key)) {
            is SearchResult.Found -> r.handle.intoValMut()
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

    /** Returns the first key-value pair in the map. The key is the minimum. */
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

    // ---- map clone ---------------------------------------------------------

    fun clone(): BTreeMap<K, V> {
        fun cloneSubtree(
            node: NodeRef<Marker.Immut, K, V, Marker.LeafOrInternal>,
        ): BTreeMap<K, V> {
            when (val f = node.force()) {
                is ForceResult.Leaf -> {
                    val leaf = f.value
                    val outTree = BTreeMap<K, V>()
                    outTree.root = newOwnedTree<K, V>()

                    val outRoot = outTree.root!!
                    val outNode = when (val r = outRoot.borrowMut().force()) {
                        is ForceResult.Leaf -> r.value
                        is ForceResult.Internal -> error("unreachable")
                    }

                    var inEdge = leaf.firstEdge()
                    while (true) {
                        val rk = inEdge.rightKv()
                        if (rk is EdgeKvResult.Err) break
                        val kv = rk as EdgeKvResult.Ok
                        val (k, v) = kv.handle.intoKv()
                        inEdge = kv.handle.rightEdge()

                        outNode.push(k, v)
                        outTree.length += 1
                    }
                    return outTree
                }
                is ForceResult.Internal -> {
                    val internal = f.value
                    val outTree = cloneSubtree(internal.firstEdge().descend())

                    val outRoot = outTree.root!!
                    val outNode = outRoot.pushInternalLevel()
                    var inEdge = internal.firstEdge()
                    while (true) {
                        val rk = inEdge.rightKv()
                        if (rk is EdgeKvResult.Err) break
                        val kv = rk as EdgeKvResult.Ok
                        val (k, v) = kv.handle.intoKv()
                        inEdge = kv.handle.rightEdge()

                        val subtree = cloneSubtree(inEdge.descend())
                        val subroot = subtree.root
                        val sublength = subtree.length

                        outNode.push(
                            k,
                            v,
                            subroot ?: newOwnedTree<K, V>(),
                        )
                        outTree.length += 1 + sublength
                    }
                    return outTree
                }
            }
        }

        if (this.isEmpty()) {
            return BTreeMap() // matches newIn
        } else {
            return cloneSubtree(this.root!!.reborrow())
        }
    }

    /** Replaces this map's contents with a clone of [source]. */
    fun cloneFrom(source: BTreeMap<K, V>) {
        val cloned = source.clone()
        this.clear()
        for ((k, v) in cloned) put(k, v)
    }

    internal fun newIn(): BTreeMap<K, V> = BTreeMap()

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

    /** [MutableMap.put] is the Kotlin-side spelling of [insert]. */
    override fun put(key: K, value: V): V? = insert(key, value)

    /**
     * Tries to insert a key-value pair into the map. Returns the inserted
     * value on success, or [Result.failure] containing an [OccupiedError] if
     * the key already existed.
     */
    fun tryInsert(key: K, value: V): Result<V> {
        return when (val e = entry(key)) {
            is Entry.Occupied -> Result.failure(OccupiedError(e.entry, value))
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
                        // apply `conflict` to get a new (K, V), and insert it
                        // back into the next entry that the cursor is pointing at
                        selfCursor.insertAfterUnchecked(k, newV)
                    }
                }
                cmp > 0 -> {
                    selfCursor.insertBeforeUnchecked(firstOtherKey, firstOtherVal)
                }
                else -> error("unreachable: Cursor's peekNext should return null.")
            }
        } else {
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
                                selfCursor.insertAfterUnchecked(k, newV)
                            }
                            break
                        }
                        cmp > 0 -> {
                            selfCursor.insertBeforeUnchecked(otherKey, otherVal)
                            break
                        }
                        else -> {
                            selfCursor.next()
                        }
                    }
                } else {
                    selfCursor.insertBeforeUnchecked(otherKey, otherVal)
                    break
                }
            }
        }
    }

    // ---- range / rangeMut --------------------------------------------------

    /**
     * Constructs a double-ended iterator over a sub-range of elements.
     *
     * @throws IllegalArgumentException if `range start > end`, or if start
     *   and end are equal and both excluded.
     */
    fun range(range: RangeBounds<K>): Range<K, V> {
        val r = root
        return if (r != null) {
            Range(r.reborrow().rangeSearch<K, V, K, RangeBounds<K>>(range, internalIsSet))
        } else {
            Range(LeafRange.none())
        }
    }

    fun range(range: RangeFull): Range<K, V> = range(unbounded())

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

    fun rangeMut(range: RangeFull): RangeMut<K, V> = rangeMut(unbounded())

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
    ): ExtractIf<K, V> {
        val inner = extractIfInner(range)
        return ExtractIf(inner, pred)
    }

    fun extractIf(
        range: RangeFull,
        pred: (K, V) -> Boolean,
    ): ExtractIf<K, V> = extractIf(unbounded(), pred)

    internal fun extractIfInner(range: RangeBounds<K>): ExtractIfInner<K, V> {
        val r = root
        return if (r == null) {
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
    }

    /**
     * Returns a reference to the value corresponding to the supplied key.
     *
     * Panics if the key is not present in the `BTreeMap`.
     */
    fun index(key: K): V = this.get(key) ?: throw NoSuchElementException("no entry found for key")

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
     * Returns an owning iterator over the entries of the map.
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

    /** Returns the number of elements in the map. */
    fun len(): Int = length

    /**
     * Inserts every entry from [iter] into this map. Existing keys are
     * overwritten.
     */
    fun extend(iter: Iterable<Pair<K, V>>) {
        for ((k, v) in iter) {
            insert(k, v)
        }
    }

    fun extend(iter: Iterable<Pair<K, V>>, route: CopiedMapExtend) {
        when (route) {
            CopiedMapExtend -> extend(iter)
        }
    }

    /** Inserts a single entry into this map. */
    fun extendOne(entry: Pair<K, V>) {
        insert(entry.first, entry.second)
    }

    fun extendOne(entry: Pair<K, V>, route: CopiedMapExtend) {
        when (route) {
            CopiedMapExtend -> insert(entry.first, entry.second)
        }
    }

    companion object {
        /** Makes a new, empty `BTreeMap`. Does not allocate anything on its own. */
        fun <K : Comparable<K>, V> new(): BTreeMap<K, V> {
            return BTreeMap()
        }

        /**
         * Initializes a `BTreeMap` from an array of key-value pairs.
         */
        fun <K : Comparable<K>, V> from(arr: Array<Pair<K, V>>): BTreeMap<K, V> {
            val map = BTreeMap<K, V>()
            for (kv in arr) {
                map.insert(kv.first, kv.second)
            }
            return map
        }

        /** Creates an empty `BTreeMap`. */
        fun <K : Comparable<K>, V> default(): BTreeMap<K, V> {
            return BTreeMap()
        }

        /** Makes a `BTreeMap` from a sorted iterator. */
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

        /** Constructs a `BTreeMap` from any source of `(K, V)` pairs. */
        fun <K : Comparable<K>, V> fromIter(iter: Iterable<Pair<K, V>>): BTreeMap<K, V> {
            val inputs = iter.toMutableList()

            if (inputs.isEmpty()) {
                return new()
            }

            // use stable sort to preserve the insertion order.
            inputs.sortWith(Comparator { a, b -> a.first.compareTo(b.first) })
            return bulkBuildFromSortedIter(inputs.iterator())
        }

        /** Constructs a `BTreeMap` from an iterable of pairs. */
        fun <K : Comparable<K>, V> fromIterable(items: Iterable<Pair<K, V>>): BTreeMap<K, V> {
            val list = items.toMutableList()
            if (list.isEmpty()) return BTreeMap()
            list.sortWith(Comparator { a, b -> a.first.compareTo(b.first) })
            return bulkBuildFromSortedIter(list.iterator())
        }

        /** Constructs a `BTreeMap` from a vararg of pairs. */
        fun <K : Comparable<K>, V> of(vararg pairs: Pair<K, V>): BTreeMap<K, V> {
            return fromIterable(pairs.asIterable())
        }
    }

    // ---- lowerBound / lowerBoundMut / upperBound / upperBoundMut -----

    /**
     * Returns a [Cursor] pointing at the gap before the smallest key greater
     * than the given bound.
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

    operator fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> =
        iterMutEntries()

    /** [iterMut] adapted to the Kotlin `MutableMap` contract. */
    internal fun iterMutEntries(): MutableIterator<MutableMap.MutableEntry<K, V>> {
        val inner = iterMut()
        val map = this
        return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
            override fun hasNext(): Boolean = inner.hasNext()
            override fun next(): MutableMap.MutableEntry<K, V> {
                val (k, v) = inner.next()
                return MutEntry(map, k, v)
            }
            override fun remove() = inner.remove()
        }
    }

    // ---- equals / hashCode / toString --------------------------------------

    override fun equals(other: Any?): Boolean {
        if (other !is BTreeMap<*, *>) return false
        if (this.size != other.size) return false
        val itA = this.iter()
        val itB = other.entries.iterator()
        while (itA.hasNext() && itB.hasNext()) {
            val a = itA.next()
            val b = itB.next()
            if (a.first != b.key || a.second != b.value) return false
        }
        return !itA.hasNext() && !itB.hasNext()
    }

    override fun hashCode(): Int {
        var h = size
        for (entry in this) {
            h = 31 * h + entry.key.hashCode()
            h = 31 * h + (entry.value?.hashCode() ?: 0)
        }
        return h
    }

    override fun toString(): String {
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

object SharedMapIntoIter

object MutableMapIntoIter

object CopiedMapExtend

fun <K : Comparable<K>, V> BTreeMap<K, V>.intoIter(route: SharedMapIntoIter): Iter<K, V> {
    return when (route) {
        SharedMapIntoIter -> iter()
    }
}

fun <K : Comparable<K>, V> BTreeMap<K, V>.intoIter(route: MutableMapIntoIter): IterMut<K, V> {
    return when (route) {
        MutableMapIntoIter -> iterMut()
    }
}

/**
 * Lexicographic ordering: returns the first non-zero comparison among the
 * paired entries (by key, then by value), or the comparison of the maps'
 * sizes if every paired entry compares equal.
 */
operator fun <K : Comparable<K>, V : Comparable<V>> BTreeMap<K, V>.compareTo(
    other: BTreeMap<K, V>,
): Int {
    val itA = this.iterator()
    val itB = other.iterator()
    while (itA.hasNext() && itB.hasNext()) {
        val a = itA.next()
        val b = itB.next()
        val k = a.key.compareTo(b.key)
        if (k != 0) return k
        val v = a.value.compareTo(b.value)
        if (v != 0) return v
    }
    return when {
        itA.hasNext() -> 1
        itB.hasNext() -> -1
        else -> 0
    }
}

// Internal functionality for `BTreeSet`.

internal fun <K : Comparable<K>> BTreeMap<K, SetValZst>.replace(key: K): K? {
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

internal fun <K : Comparable<K>> BTreeMap<K, SetValZst>.getOrInsertWith(
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
) : Iterator<Pair<K, V>> {
    override fun hasNext(): Boolean = length > 0

    override fun next(): Pair<K, V> {
        if (length == 0) throw NoSuchElementException()
        length -= 1
        return range.nextUnchecked()
    }

    /** Returns the next entry from the back of the iterator, or `null` if exhausted. */
    fun nextBack(): Pair<K, V>? {
        if (length == 0) return null
        length -= 1
        return range.nextBackUnchecked()
    }

    /** Returns the number of remaining entries. */
    fun len(): Int = length

    fun sizeHint(): Pair<Int, Int?> = Pair(length, length)

    fun last(): Pair<K, V>? = nextBack()

    fun min(): Pair<K, V>? = if (hasNext()) next() else null

    fun max(): Pair<K, V>? = nextBack()

    fun clone(): Iter<K, V> = Iter(range.clone(), length)

    /** Drains the iterator into a list. */
    fun toList(): List<Pair<K, V>> {
        val out = ArrayList<Pair<K, V>>(length)
        while (hasNext()) out.add(next())
        return out
    }

    override fun toString(): String = clone().toList().toString()

    companion object {
        /**
         * Creates an empty [Iter].
         */
        fun <K, V> default(): Iter<K, V> = Iter(LazyLeafRange.none(), 0)
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
) : MutableIterator<Pair<K, V>> {
    private var lastKey: K? = null

    override fun hasNext(): Boolean = length > 0

    override fun next(): Pair<K, V> {
        if (length == 0) throw NoSuchElementException()
        length -= 1
        val kv = range.nextUncheckedValMut()
        lastKey = kv.first
        return kv
    }

    /** Returns the next entry from the back of the iterator, or `null` if exhausted. */
    fun nextBack(): Pair<K, V>? {
        if (length == 0) return null
        length -= 1
        val kv = range.nextBackUncheckedValMut()
        lastKey = kv.first
        return kv
    }

    fun len(): Int = length

    fun sizeHint(): Pair<Int, Int?> = Pair(length, length)

    fun last(): Pair<K, V>? = nextBack()

    fun min(): Pair<K, V>? = if (hasNext()) next() else null

    fun max(): Pair<K, V>? = nextBack()

    /** Drains the iterator into a list. */
    fun toList(): List<Pair<K, V>> {
        val out = ArrayList<Pair<K, V>>(length)
        while (hasNext()) out.add(next())
        return out
    }

    /** Returns an immutable iterator of references to the remaining items. */
    fun iter(): Iter<K, V> = Iter(range.reborrow(), length)

    /** Removes the last-yielded entry from the map. */
    override fun remove() {
        val k = lastKey ?: throw IllegalStateException("call next() before remove()")
        map.remove(k)
        lastKey = null
    }

    override fun toString(): String = iter().toList().toString()

    companion object {
        /**
         * Creates an empty [IterMut].
         */
        fun <K : Comparable<K>, V> default(): IterMut<K, V> =
            IterMut(LazyLeafRange.none(), 0, BTreeMap())
    }
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
 * a dying tree, dropping the link to each node as it advances.
 */
class IntoIter<K, V> internal constructor(
    internal var range: LazyLeafRange<Marker.Dying, K, V>,
    internal var length: Int,
) : Iterator<Pair<K, V>> {
    override fun hasNext(): Boolean = length > 0

    override fun next(): Pair<K, V> {
        val kv = dyingNext() ?: throw NoSuchElementException()
        return kv.intoKeyVal()
    }

    fun nextBack(): Pair<K, V>? {
        val kv = dyingNextBack() ?: return null
        return kv.intoKeyVal()
    }

    fun len(): Int = length

    fun sizeHint(): Pair<Int, Int?> = Pair(length, length)

    /** Consumes the iterator and returns the last entry. */
    fun last(): Pair<K, V>? = nextBack()

    /** Returns the minimum entry. O(1) for sorted iterators. */
    fun min(): Pair<K, V>? = if (hasNext()) next() else null

    /** Returns the maximum entry. O(1) for sorted iterators. */
    fun max(): Pair<K, V>? = nextBack()

    /** Drains the iterator into a list. */
    fun toList(): List<Pair<K, V>> {
        val out = ArrayList<Pair<K, V>>(length)
        while (hasNext()) out.add(next())
        return out
    }

    /**
     * Core of a `next` method returning a dying KV handle, invalidated by
     * further calls to this function and some others.
     */
    private fun dyingNext(): Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.KV>? {
        return if (length == 0) {
            range.deallocatingEnd()
            null
        } else {
            length -= 1
            range.deallocatingNextUnchecked()
        }
    }

    /** Counterpart to [dyingNext]. */
    private fun dyingNextBack(): Handle<NodeRef<Marker.Dying, K, V, Marker.LeafOrInternal>, Marker.KV>? {
        return if (length == 0) {
            range.deallocatingEnd()
            null
        } else {
            length -= 1
            range.deallocatingNextBackUnchecked()
        }
    }

    /**
     * Returns an immutable iterator of references over the remaining items.
     *
     * Yields [ReadOnlyEntry] views over the still-walked tree; the dying
     * tree must not be mutated through this view, only read.
     */
    internal fun iter(): Iter<K, V> = Iter(range.reborrow(), length)

    override fun toString(): String = iter().toList().toString()

    companion object {
        /**
         * Creates an empty [IntoIter].
         */
        fun <K, V> default(): IntoIter<K, V> = IntoIter(LazyLeafRange.none(), 0)
    }
}

// ============================================================================
// Keys / Values / ValuesMut
// ============================================================================

/** An iterator over the keys of a `BTreeMap`. */
class Keys<K, V> internal constructor(internal val inner: Iter<K, V>) : Iterator<K> {
    override fun hasNext(): Boolean = inner.hasNext()
    override fun next(): K = inner.next().first
    fun nextBack(): K? = inner.nextBack()?.first
    fun len(): Int = inner.len()
    fun sizeHint(): Pair<Int, Int?> = inner.sizeHint()
    fun last(): K? = nextBack()
    fun min(): K? = if (hasNext()) next() else null
    fun max(): K? = nextBack()
    fun toList(): List<K> {
        val out = ArrayList<K>(inner.len())
        while (hasNext()) out.add(next())
        return out
    }
    fun clone(): Keys<K, V> = Keys(inner.clone())
    override fun toString(): String = clone().toList().toString()

    companion object {
        /**
         * Creates an empty [Keys].
         */
        fun <K, V> default(): Keys<K, V> = Keys(Iter.default())
    }
}

/** An iterator over the values of a `BTreeMap`. */
class Values<K, V> internal constructor(internal val inner: Iter<K, V>) : Iterator<V> {
    override fun hasNext(): Boolean = inner.hasNext()
    override fun next(): V = inner.next().second
    fun nextBack(): V? = inner.nextBack()?.second
    fun len(): Int = inner.len()
    fun sizeHint(): Pair<Int, Int?> = inner.sizeHint()
    fun last(): V? = nextBack()
    fun toList(): List<V> {
        val out = ArrayList<V>(inner.len())
        while (hasNext()) out.add(next())
        return out
    }
    fun clone(): Values<K, V> = Values(inner.clone())
    override fun toString(): String = clone().toList().toString()

    companion object {
        /**
         * Creates an empty [Values].
         */
        fun <K, V> default(): Values<K, V> = Values(Iter.default())
    }
}

/** A mutable iterator over the values of a `BTreeMap`. */
class ValuesMut<K : Comparable<K>, V> internal constructor(internal val inner: IterMut<K, V>) :
    MutableIterator<V> {
    override fun hasNext(): Boolean = inner.hasNext()
    override fun next(): V = inner.next().second
    fun nextBack(): V? = inner.nextBack()?.second
    fun len(): Int = inner.len()
    fun sizeHint(): Pair<Int, Int?> = inner.sizeHint()
    fun last(): V? = nextBack()
    fun toList(): List<V> {
        val out = ArrayList<V>(inner.len())
        while (hasNext()) out.add(next())
        return out
    }
    override fun remove() = inner.remove()
    override fun toString(): String = inner.iter().toList().map { it.second }.toString()

    companion object {
        /**
         * Creates an empty [ValuesMut].
         */
        fun <K : Comparable<K>, V> default(): ValuesMut<K, V> = ValuesMut(IterMut.default())
    }
}

/** An owning iterator over the keys of a `BTreeMap`. */
class IntoKeys<K, V> internal constructor(internal val inner: IntoIter<K, V>) : Iterator<K> {
    override fun hasNext(): Boolean = inner.hasNext()
    override fun next(): K = inner.next().first
    fun nextBack(): K? = inner.nextBack()?.first
    fun len(): Int = inner.len()
    fun sizeHint(): Pair<Int, Int?> = inner.sizeHint()
    fun last(): K? = nextBack()
    fun min(): K? = if (hasNext()) next() else null
    fun max(): K? = nextBack()
    fun toList(): List<K> {
        val out = ArrayList<K>(inner.len())
        while (hasNext()) out.add(next())
        return out
    }
    override fun toString(): String = inner.iter().toList().map { it.first }.toString()

    companion object {
        /**
         * Creates an empty [IntoKeys].
         */
        fun <K, V> default(): IntoKeys<K, V> = IntoKeys(IntoIter.default())
    }
}

/** An owning iterator over the values of a `BTreeMap`. */
class IntoValues<K, V> internal constructor(internal val inner: IntoIter<K, V>) : Iterator<V> {
    override fun hasNext(): Boolean = inner.hasNext()
    override fun next(): V = inner.next().second
    fun nextBack(): V? = inner.nextBack()?.second
    fun len(): Int = inner.len()
    fun sizeHint(): Pair<Int, Int?> = inner.sizeHint()
    fun last(): V? = nextBack()
    fun toList(): List<V> {
        val out = ArrayList<V>(inner.len())
        while (hasNext()) out.add(next())
        return out
    }
    override fun toString(): String = inner.iter().toList().map { it.second }.toString()

    companion object {
        /**
         * Creates an empty [IntoValues].
         */
        fun <K, V> default(): IntoValues<K, V> = IntoValues(IntoIter.default())
    }
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
    private var pending: Pair<K, V>? = inner.nextChecked(),
) : Iterator<Pair<K, V>> {
    override fun hasNext(): Boolean = pending != null

    override fun next(): Pair<K, V> {
        val out = pending ?: throw NoSuchElementException()
        pending = inner.nextChecked()
        return out
    }

    /** Returns the next entry, or `null` if exhausted. */
    fun nextOrNull(): Pair<K, V>? = if (hasNext()) next() else null

    /** Returns the next entry from the back, or `null` if exhausted. */
    fun nextBack(): Pair<K, V>? = inner.nextBackChecked()

    /** Alias for [nextBack]. */
    fun nextBackOrNull(): Pair<K, V>? = nextBack()

    fun sizeHint(): Pair<Int, Int?> = Pair(0, null)

    fun last(): Pair<K, V>? = nextBack()

    fun min(): Pair<K, V>? = if (hasNext()) next() else null

    fun max(): Pair<K, V>? = nextBack()

    fun clone(): Range<K, V> = Range(inner.clone(), pending)

    /** Drains the iterator into a list. */
    fun toList(): List<Pair<K, V>> {
        val out = ArrayList<Pair<K, V>>()
        while (hasNext()) out.add(next())
        return out
    }

    override fun toString(): String = clone().toList().toString()

    companion object {
        /**
         * Creates an empty [Range].
         */
        fun <K, V> default(): Range<K, V> = Range(LeafRange.none())
    }
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

    /** Returns the next entry from the back, or `null` if exhausted. */
    fun nextBack(): Pair<K, V>? = inner.nextBackCheckedValMut()

    fun sizeHint(): Pair<Int, Int?> = Pair(0, null)

    fun last(): Pair<K, V>? = nextBack()

    fun min(): Pair<K, V>? = if (hasNext()) next() else null

    fun max(): Pair<K, V>? = nextBack()

    /** Drains the iterator into a list. */
    fun toList(): List<Pair<K, V>> {
        val out = ArrayList<Pair<K, V>>()
        while (hasNext()) out.add(next())
        return out
    }

    override fun toString(): String = Range(inner.reborrow()).toList().toString()

    companion object {
        /**
         * Creates an empty [RangeMut].
         */
        fun <K, V> default(): RangeMut<K, V> = RangeMut(LeafRange.none())
    }
}

// ============================================================================
// ExtractIf / ExtractIfInner
// ============================================================================

/**
 * Iterator that extracts elements matching a predicate from a sub-range of
 * a `BTreeMap`.
 *
 * Removed elements are yielded in ascending key order; non-removed elements
 * remain in the map. If iteration short-circuits, the remaining matching
 * elements stay in the map; consumers must drive the iterator to completion
 * if they want every match removed.
 */
class ExtractIf<K : Comparable<K>, V> internal constructor(
    internal val inner: ExtractIfInner<K, V>,
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

    /** Drains the iterator into a list. */
    fun toList(): List<Pair<K, V>> {
        val out = ArrayList<Pair<K, V>>()
        while (hasNext()) out.add(next())
        return out
    }

    override fun toString(): String = "ExtractIf(peek=${inner.peek()})"

    internal fun sizeHint(): Pair<Int, Int?> = inner.sizeHint()
}

/**
 * State machine of an [ExtractIf]. Internal because `BTreeSet.ExtractIf`
 * reuses the same shape.
 */
internal class ExtractIfInner<K : Comparable<K>, V>(
    internal val map: BTreeMap<K, V>,
    /** Buried reference to the root field in the borrowed map. */
    internal var dormantRoot: DormantMutRef<NodeRef<Marker.Owned, K, V, Marker.LeafOrInternal>>?,
    /** Contains a leaf edge preceding the next element to be returned, or the last leaf edge. */
    internal var curLeafEdge: Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge>?,
    /** Range over which iteration was requested. */
    internal val range: RangeBounds<K>,
) {
    internal fun sizeHint(): Pair<Int, Int?> = Pair(0, map.len())

    /** Allow Debug implementations to predict the next element. */
    internal fun peek(): Pair<K, V>? {
        val edge = curLeafEdge ?: return null
        return when (val r = edge.reborrow().nextKv()) {
            is NextKvResult.Ok -> r.handle.intoKv()
            is NextKvResult.Err -> null
        }
    }

    /** Implementation of a typical [ExtractIf.next] method, given the predicate. */
    internal fun next(pred: (K, V) -> Boolean): Pair<K, V>? {
        while (true) {
            val edge = curLeafEdge ?: return null
            curLeafEdge = null
            val kv = when (val r = edge.nextKv()) {
                is NextKvResult.Ok -> r.handle
                is NextKvResult.Err -> return null
            }
            val (k, v) = kv.kvMut()

            val withinRange = when (val end = range.endBound()) {
                is Bound.Included -> k.compareTo(end.value) <= 0
                is Bound.Excluded -> k.compareTo(end.value) < 0
                Bound.Unbounded -> true
            }
            if (!withinRange) return null

            if (pred(k, v)) {
                map.length -= 1
                val (kvPair, pos) = kv.removeKvTracking {
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
    internal var root: NodeRef<Marker.Owned, K, V, Marker.LeafOrInternal>?,
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
 * [removeNext] / [removeBefore] preserve the BTreeMap invariants
 * (ascending key order). [CursorMutKey] is the lower-level variant that
 * allows mutating keys directly with the additional caller obligation.
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

    /**
     * Inserts a new key-value pair into the map in the gap that the cursor is
     * currently pointing to.
     *
     * After the insertion the cursor will be pointing at the gap after the
     * newly inserted element.
     *
     * # Safety
     *
     * You must ensure that the [BTreeMap] invariants are maintained.
     * Specifically:
     *
     * * The key of the newly inserted element must be unique in the tree.
     * * All keys in the tree must remain in sorted order.
     */
    fun insertAfterUnchecked(key: K, value: V) = inner.insertAfterUnchecked(key, value)

    /**
     * Inserts a new key-value pair into the map in the gap that the cursor is
     * currently pointing to.
     *
     * After the insertion the cursor will be pointing at the gap after the
     * newly inserted element.
     *
     * # Safety
     *
     * You must ensure that the [BTreeMap] invariants are maintained.
     * Specifically:
     *
     * * The key of the newly inserted element must be unique in the tree.
     * * All keys in the tree must remain in sorted order.
     */
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
 * responsible for keeping the tree in valid state.
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
     * # Safety
     *
     * You must ensure that the [BTreeMap] invariants are maintained.
     * Specifically:
     *
     * * The key of the newly inserted element must be unique in the tree.
     * * All keys in the tree must remain in sorted order.
     */
    fun insertAfterUnchecked(key: K, value: V) {
        val cur = current
        current = null
        val edge: Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge> = if (cur == null) {
            // Tree is empty, allocate a new root.
            val map = dormantMap.reborrow()
            check(map.root == null)
            val node = NodeRef.newLeaf<K, V>()
            val handle = node.borrowMut().pushWithHandle(key, value)
            map.root = node.forgetType()
            map.length += 1
            current = handle.leftEdge()
            return
        } else {
            cur
        }
        val handle = edge.insertRecursing(key, value) { ins ->
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
     * # Safety
     *
     * You must ensure that the [BTreeMap] invariants are maintained.
     * Specifically:
     *
     * * The key of the newly inserted element must be unique in the tree.
     * * All keys in the tree must remain in sorted order.
     */
    fun insertBeforeUnchecked(key: K, value: V) {
        val cur = current
        current = null
        val edge: Handle<NodeRef<Marker.Mut, K, V, Marker.Leaf>, Marker.Edge> = if (cur == null) {
            val map = dormantMap.reborrow()
            if (map.root == null) {
                // Tree is empty, allocate a new root.
                val node = NodeRef.newLeaf<K, V>()
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
        // First check whether nextKv exists at all (can fail if cursor is at end).
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

    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = map.iterMutEntries()

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
        override fun next(): K = inner.next().first
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
