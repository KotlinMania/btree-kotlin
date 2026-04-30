// port-lint: source set.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

// Cross-iterator state machines:
//   - `DifferenceInner` / `IntersectionInner` translate as private sealed
//     classes (`Stitch` / `Search` / `Iterate` / `Answer`).
//   - `Peekable<Iter<T>>` in `DifferenceInner.Stitch` inlines a small
//     one-element-buffer adapter (`PeekableSetIter`), since Kotlin stdlib has
//     no `Peekable`.

/** Estimated relative size at which searching beats iterating. */
private const val ITER_PERFORMANCE_TIPPING_SIZE_DIFF: Int = 16

/**
 * An ordered set based on a B-Tree.
 *
 * See [BTreeMap]'s documentation for a detailed discussion of this collection's
 * performance benefits and drawbacks.
 *
 * It is a logic error for an item to be modified in such a way that the
 * item's ordering relative to any other item, as determined by [Comparable],
 * changes while it is in the set. The behavior resulting from such a logic
 * error is not specified, but will be encapsulated to the [BTreeSet] that
 * observed the logic error and not result in undefined behavior. This could
 * include exceptions, incorrect results, aborts, memory leaks, and
 * non-termination.
 *
 * Iterators returned by [iter] and [iterator] produce their items in order,
 * and take worst-case logarithmic and amortized constant time per item
 * returned.
 *
 * Implements [MutableSet] so consumers can import the Kotlin collections idioms
 * for free.
 */
class BTreeSet<T : Comparable<T>> : MutableSet<T> {
    /** Backing map; `internalIsSet = true` so range-bound errors render "BTreeSet". */
    internal val map: BTreeMap<T, SetValZst>

    /** Makes a new, empty `BTreeSet`. Does not allocate anything on its own. */
    constructor() {
        this.map = BTreeMap<T, SetValZst>()
        this.map.internalIsSet = true
    }

    /** Internal constructor wrapping a pre-built map (used by [splitOff]). */
    internal constructor(map: BTreeMap<T, SetValZst>) {
        this.map = map
        this.map.internalIsSet = true
    }

    /** Makes a new `BTreeSet` with a reasonable choice of B. */
    companion object {
        /** Makes a new, empty `BTreeSet`. Does not allocate anything on its own. */
        fun <T : Comparable<T>> new(): BTreeSet<T> = BTreeSet()

        /** Makes a new, empty `BTreeSet` with a reasonable choice of B. */
        fun <T : Comparable<T>> newIn(): BTreeSet<T> = BTreeSet()

        /** Creates an empty `BTreeSet`. */
        fun <T : Comparable<T>> default(): BTreeSet<T> = BTreeSet()

        /** Constructs a `BTreeSet` from any source of values. */
        fun <T : Comparable<T>> fromIter(iter: Iterable<T>): BTreeSet<T> = fromIterable(iter)

        /** Constructs a `BTreeSet` from an iterable of values. */
        fun <T : Comparable<T>> fromIterable(items: Iterable<T>): BTreeSet<T> {
            val list = items.toMutableList()
            if (list.isEmpty()) return BTreeSet()
            list.sortWith(Comparator { a, b -> a.compareTo(b) })
            return fromSortedIter(list.iterator())
        }

        /** Constructs a `BTreeSet` from a vararg of values. */
        fun <T : Comparable<T>> of(vararg values: T): BTreeSet<T> = fromIterable(values.asIterable())

        fun <T : Comparable<T>> from(vararg values: T): BTreeSet<T> = fromIterable(values.asIterable())

        internal fun <T : Comparable<T>> fromSortedIter(iter: Iterator<T>): BTreeSet<T> {
            val mapped = object : Iterator<Pair<T, SetValZst>> {
                override fun hasNext(): Boolean = iter.hasNext()
                override fun next(): Pair<T, SetValZst> = Pair(iter.next(), SetValZst)
            }
            val map = BTreeMap.bulkBuildFromSortedIter<T, SetValZst>(mapped)
            return BTreeSet(map)
        }
    }

    // ---- range / set algebra constructors ----------------------------------

    /**
     * Constructs a double-ended iterator over a sub-range of elements in the
     * set, taking a `RangeBounds<T>` with [Bound] endpoints.
     *
     * @throws IllegalArgumentException if `range start > end`, or if start
     *   and end are equal and both excluded.
     */
    fun range(range: RangeBounds<T>): Range<T> = Range(map.range(range))

    fun range(range: RangeFull): Range<T> = Range(map.range(range))

    /**
     * Visits the elements representing the difference, i.e., the elements
     * that are in `self` but not in [other], in ascending order.
     */
    fun difference(other: BTreeSet<T>): Difference<T> {
        val selfMin = this.first()
        val selfMax = this.last()
        val otherMin = other.first()
        val otherMax = other.last()
        if (selfMin != null && selfMax != null && otherMin != null && otherMax != null) {
            val cmpMinMax = selfMin.compareTo(otherMax)
            val cmpMaxMin = selfMax.compareTo(otherMin)
            val inner: DifferenceInner<T> = when {
                cmpMinMax > 0 || cmpMaxMin < 0 -> DifferenceInner.Iterate(this.iter())
                cmpMinMax == 0 -> {
                    val selfIter = this.iter()
                    selfIter.next()
                    DifferenceInner.Iterate(selfIter)
                }
                cmpMaxMin == 0 -> {
                    val selfIter = this.iter()
                    selfIter.nextBack()
                    DifferenceInner.Iterate(selfIter)
                }
                this.size <= other.size / ITER_PERFORMANCE_TIPPING_SIZE_DIFF ->
                    DifferenceInner.Search(selfIter = this.iter(), otherSet = other)
                else -> DifferenceInner.Stitch(
                    selfIter = this.iter(),
                    otherIter = PeekableSetIter(other.iter()),
                )
            }
            return Difference(inner)
        }
        return Difference(DifferenceInner.Iterate(this.iter()))
    }

    /**
     * Visits the elements representing the symmetric difference, i.e., the
     * elements that are in `self` or in [other] but not in both, in ascending
     * order.
     */
    fun symmetricDifference(other: BTreeSet<T>): SymmetricDifference<T> =
        SymmetricDifference(MergeIterInner(this.iter(), other.iter()))

    /**
     * Visits the elements representing the intersection, i.e., the elements
     * that are both in `self` and [other], in ascending order.
     */
    fun intersection(other: BTreeSet<T>): Intersection<T> {
        val selfMin = this.first()
        val selfMax = this.last()
        val otherMin = other.first()
        val otherMax = other.last()
        if (selfMin != null && selfMax != null && otherMin != null && otherMax != null) {
            val cmpMinMax = selfMin.compareTo(otherMax)
            val cmpMaxMin = selfMax.compareTo(otherMin)
            val inner: IntersectionInner<T> = when {
                cmpMinMax > 0 || cmpMaxMin < 0 -> IntersectionInner.Answer(null)
                cmpMinMax == 0 -> IntersectionInner.Answer(selfMin)
                cmpMaxMin == 0 -> IntersectionInner.Answer(selfMax)
                this.size <= other.size / ITER_PERFORMANCE_TIPPING_SIZE_DIFF ->
                    IntersectionInner.Search(smallIter = this.iter(), largeSet = other)
                other.size <= this.size / ITER_PERFORMANCE_TIPPING_SIZE_DIFF ->
                    IntersectionInner.Search(smallIter = other.iter(), largeSet = this)
                else -> IntersectionInner.Stitch(a = this.iter(), b = other.iter())
            }
            return Intersection(inner)
        }
        return Intersection(IntersectionInner.Answer(null))
    }

    /**
     * Visits the elements representing the union, i.e., all the elements in
     * `self` or [other], without duplicates, in ascending order.
     */
    fun union(other: BTreeSet<T>): Union<T> = Union(MergeIterInner(this.iter(), other.iter()))

    // ---- clear / contains / get --------------------------------------------

    /** Clears the set, removing all elements. */
    override fun clear() {
        map.clear()
    }

    /** Returns `true` if the set contains an element equal to [element]. */
    override fun contains(element: T): Boolean = map.containsKey(element)

    /**
     * Returns a reference to the element in the set, if any, that is equal
     * to [value].
     */
    fun get(value: T): T? = map.getKeyValue(value)?.first

    /**
     * Returns `true` if `self` has no elements in common with [other]. This
     * is equivalent to checking for an empty intersection.
     */
    fun isDisjoint(other: BTreeSet<T>): Boolean = !this.intersection(other).hasNext()

    /**
     * Returns `true` if the set is a subset of [other], i.e., [other]
     * contains at least all the elements in `self`.
     */
    fun isSubset(other: BTreeSet<T>): Boolean {
        // Same result as self.difference(other).next().isNone()
        // but the code below is faster (hugely in some cases).
        if (this.size > other.size) return false // self has more elements than other
        val selfMin = this.first() ?: return true // self is empty
        val selfMax = this.last() ?: return true
        val otherMin = other.first() ?: return false // other is empty
        val otherMax = other.last() ?: return false
        val selfIter = this.iter()
        when {
            selfMin < otherMin -> return false // other does not contain selfMin
            selfMin == otherMin -> {
                selfIter.next() // selfMin is contained in other, so remove it from consideration
                // otherMin is now not in selfIter (used below)
            }
            else -> { /* selfMin > otherMin: otherMin is not in selfIter (used below) */ }
        }

        when {
            selfMax > otherMax -> return false // other does not contain selfMax
            selfMax == otherMax -> {
                selfIter.nextBack() // selfMax is contained in other, so remove it from consideration
                // otherMax is now not in selfIter (used below)
            }
            else -> { /* selfMax < otherMax: otherMax is not in selfIter (used below) */ }
        }
        return if (selfIter.len() <= other.size / ITER_PERFORMANCE_TIPPING_SIZE_DIFF) {
            // selfIter.all { e -> other.contains(e) }
            while (selfIter.hasNext()) {
                if (!other.contains(selfIter.next())) return false
            }
            true
        } else {
            val otherIter = other.iter()
            // remove otherMin and otherMax as they are not in selfIter (see above)
            otherIter.next()
            otherIter.nextBack()
            // custom `selfIter.all { e -> other.contains(e) }`
            while (selfIter.hasNext()) {
                val self1 = selfIter.next()
                var matched = false
                while (otherIter.hasNext()) {
                    val other1 = otherIter.next()
                    val cmp = other1.compareTo(self1)
                    if (cmp < 0) continue // skip over elements that are smaller
                    if (cmp == 0) { matched = true; break } // self1 is in other
                    /* cmp > 0 */ return false // self1 is not in other
                }
                if (!matched) return false
            }
            true
        }
    }

    /**
     * Returns `true` if the set is a superset of [other], i.e., `self`
     * contains at least all the elements in [other].
     */
    fun isSuperset(other: BTreeSet<T>): Boolean = other.isSubset(this)

    /**
     * Returns a reference to the first element in the set, if any. This
     * element is always the minimum of all elements in the set.
     */
    fun first(): T? = map.firstKeyValue()?.first

    /**
     * Returns a reference to the last element in the set, if any. This
     * element is always the maximum of all elements in the set.
     */
    fun last(): T? = map.lastKeyValue()?.first

    /**
     * Removes the first element from the set and returns it, if any.
     * The first element is always the minimum element in the set.
     */
    fun popFirst(): T? = map.popFirst()?.first

    /**
     * Removes the last element from the set and returns it, if any.
     * The last element is always the maximum element in the set.
     */
    fun popLast(): T? = map.popLast()?.first

    // ---- insert / replace / getOrInsert / getOrInsertWith -------------

    /**
     * Adds a value to the set.
     *
     * Returns whether the value was newly inserted. That is:
     *
     *   - If the set did not previously contain an equal value, `true` is
     *     returned.
     *   - If the set already contained an equal value, `false` is returned,
     *     and the entry is not updated.
     */
    override fun add(element: T): Boolean = map.insert(element, SetValZst) == null

    /** Kotlin alias for [add]. */
    fun insert(value: T): Boolean = add(value)

    /**
     * Adds a value to the set, replacing the existing element, if any, that
     * is equal to the value. Returns the replaced element.
     */
    fun replace(value: T): T? = map.replace(value)

    /**
     * Inserts the given [value] into the set if it is not present, then
     * returns the value in the set.
     */
    fun getOrInsert(value: T): T = map.getOrInsertWith(value) { it }

    /**
     * Inserts a value computed from [f] into the set if the given [value] is
     * not present, then returns the value in the set.
     */
    fun getOrInsertWith(value: T, f: (T) -> T): T = map.getOrInsertWith(value, f)

    /**
     * Gets the given value's corresponding entry in the set for in-place manipulation.
     */
    fun entry(value: T): SetEntry<T> = when (val e = map.entry(value)) {
        is io.github.kotlinmania.btree.Entry.Occupied -> SetEntry.Occupied(SetOccupiedEntry(e.entry))
        is io.github.kotlinmania.btree.Entry.Vacant -> SetEntry.Vacant(SetVacantEntry(e.entry))
    }

    // ---- sub / bitxor / bitand / bitor --------------------------------------

    fun sub(rhs: BTreeSet<T>): BTreeSet<T> {
        val diffIter = this.difference(rhs).asSequence().toList().iterator()
        return BTreeSet.fromSortedIter(diffIter)
    }

    fun bitxor(rhs: BTreeSet<T>): BTreeSet<T> {
        val symDiffIter = this.symmetricDifference(rhs).asSequence().toList().iterator()
        return BTreeSet.fromSortedIter(symDiffIter)
    }

    fun bitand(rhs: BTreeSet<T>): BTreeSet<T> {
        val intersectionIter = this.intersection(rhs).asSequence().toList().iterator()
        return BTreeSet.fromSortedIter(intersectionIter)
    }

    fun bitor(rhs: BTreeSet<T>): BTreeSet<T> {
        val unionIter = this.union(rhs).asSequence().toList().iterator()
        return BTreeSet.fromSortedIter(unionIter)
    }

    // ---- remove / take / retain / append / splitOff / extractIf -----------

    /**
     * If the set contains an element equal to [element], removes it from the
     * set and drops it. Returns whether such an element was present.
     */
    override fun remove(element: T): Boolean = map.remove(element) != null

    /**
     * Removes and returns the element in the set, if any, that is equal to
     * [value].
     */
    fun take(value: T): T? = map.removeEntry(value)?.first

    /**
     * Retains only the elements specified by [f].
     *
     * In other words, remove all elements `e` for which `f(e)` returns
     * `false`. The elements are visited in ascending order.
     */
    fun retain(f: (T) -> Boolean) {
        val it = extractIf(unboundedSet<T>()) { v -> !f(v) }
        while (it.hasNext()) it.next()
    }

    /** Moves all elements from [other] into `self`, leaving [other] empty. */
    fun append(other: BTreeSet<T>) {
        map.append(other.map)
    }

    /**
     * Splits the collection into two at the value. Returns a new collection
     * with all elements greater than or equal to the value.
     */
    fun splitOff(value: T): BTreeSet<T> = BTreeSet(map.splitOff(value))

    /**
     * Creates an iterator that visits elements in [range] in ascending order
     * and uses [pred] to determine if an element should be removed.
     *
     * If [pred] returns `true`, the element is removed from the set and
     * yielded. If [pred] returns `false`, the element remains in the set and
     * will not be yielded.
     */
    fun extractIf(range: RangeBounds<T>, pred: (T) -> Boolean): ExtractIf<T> {
        val mapExtract = map.extractIf(range) { k, _ -> pred(k) }
        return ExtractIf(mapExtract)
    }

    fun extractIf(range: RangeFull, pred: (T) -> Boolean): ExtractIf<T> {
        val mapExtract = map.extractIf(range) { k, _ -> pred(k) }
        return ExtractIf(mapExtract)
    }

    // ---- iter / len / isEmpty -----------------------------------------------

    /**
     * Gets an iterator that visits the elements in the `BTreeSet` in
     * ascending order.
     */
    fun iter(): Iter<T> = Iter(map.iter())

    fun intoIter(): IntoIter<T> = IntoIter(map.intoIter())

    /** Returns the number of elements in the set. */
    fun len(): Int = map.size

    /** Inserts every value from [iter] into this set, ignoring duplicates. */
    fun extend(iter: Iterable<T>) {
        for (v in iter) {
            insert(v)
        }
    }

    /** Inserts a single value into this set. */
    fun extendOne(value: T) {
        insert(value)
    }

    fun clone(): BTreeSet<T> = fromSortedIter(iter().asSequence().toList().iterator())

    fun cloneFrom(source: BTreeSet<T>) {
        clear()
        extend(source.iter().asSequence().toList())
    }

    /** Returns `true` if the set contains no elements. */
    override fun isEmpty(): Boolean = map.isEmpty()

    // ---- lowerBound / lowerBoundMut / upperBound / upperBoundMut -----

    /**
     * Returns a [Cursor] pointing at the gap before the smallest element
     * greater than the given bound.
     */
    fun lowerBound(bound: Bound<T>): Cursor<T> = Cursor(map.lowerBound(bound))

    /**
     * Returns a [CursorMut] pointing at the gap before the smallest element
     * greater than the given bound.
     */
    fun lowerBoundMut(bound: Bound<T>): CursorMut<T> = CursorMut(map.lowerBoundMut(bound))

    /**
     * Returns a [Cursor] pointing at the gap after the greatest element
     * smaller than the given bound.
     */
    fun upperBound(bound: Bound<T>): Cursor<T> = Cursor(map.upperBound(bound))

    /**
     * Returns a [CursorMut] pointing at the gap after the greatest element
     * smaller than the given bound.
     */
    fun upperBoundMut(bound: Bound<T>): CursorMut<T> = CursorMut(map.upperBoundMut(bound))

    // ---- MutableSet contract ------------------------------------------------

    override val size: Int get() = map.size

    override fun iterator(): MutableIterator<T> = object : MutableIterator<T> {
        private val inner = map.iterMut()
        override fun hasNext(): Boolean = inner.hasNext()
        override fun next(): T = inner.next().first
        override fun remove() = inner.remove()
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        for (e in elements) if (!contains(e)) return false
        return true
    }

    override fun addAll(elements: Collection<T>): Boolean {
        var changed = false
        for (e in elements) if (add(e)) changed = true
        return changed
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        var changed = false
        for (e in elements) if (remove(e)) changed = true
        return changed
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        val keep = elements.toHashSet()
        val before = size
        retain { it in keep }
        return size != before
    }

    // ---- equals / hashCode / toString ---------------------------------------

    override fun equals(other: Any?): Boolean {
        if (other !is BTreeSet<*>) return false
        if (this.size != other.size) return false
        val itA = this.iter()
        val itB = (other as BTreeSet<T>).iter()
        while (itA.hasNext() && itB.hasNext()) {
            if (itA.next() != itB.next()) return false
        }
        return !itA.hasNext() && !itB.hasNext()
    }

    override fun hashCode(): Int {
        return map.hashCode()
    }

    /**
     * Lexicographic ordering: returns the first non-zero element-comparison,
     * or the comparison of the sets' sizes if every paired element compares
     * equal.
     */
    operator fun compareTo(other: BTreeSet<T>): Int {
        val left = iter()
        val right = other.iter()
        while (left.hasNext() && right.hasNext()) {
            val order = left.next().compareTo(right.next())
            if (order != 0) return order
        }
        return when {
            left.hasNext() -> 1
            right.hasNext() -> -1
            else -> 0
        }
    }

    override fun toString(): String {
        val sb = StringBuilder("{")
        var first = true
        val it = iter()
        while (it.hasNext()) {
            if (!first) sb.append(", ")
            sb.append(it.next())
            first = false
        }
        sb.append("}")
        return sb.toString()
    }

    // ========================================================================
    // Nested iterator types
    // ========================================================================

    /** An iterator over the items of a `BTreeSet`. Created by [iter]. */
    class Iter<T> internal constructor(
        internal val inner: io.github.kotlinmania.btree.Iter<T, SetValZst>,
    ) : Iterator<T> {
        override fun hasNext(): Boolean = inner.hasNext()
        override fun next(): T = inner.next().first

        /** Returns the next element from the back of the iterator, or `null` if exhausted. */
        fun nextBack(): T? = inner.nextBack()?.first

        /** Returns the number of remaining elements. */
        fun len(): Int = inner.len()

        fun sizeHint(): Pair<Int, Int?> = inner.sizeHint()

        fun last(): T? = nextBack()

        fun min(): T? = if (hasNext()) next() else null

        fun max(): T? = nextBack()

        override fun toString(): String = "Iter(${inner})"
    }

    /** An owning iterator over the items of a `BTreeSet` in ascending order. */
    class IntoIter<T> internal constructor(
        internal val inner: io.github.kotlinmania.btree.IntoIter<T, SetValZst>,
    ) : Iterator<T> {
        override fun hasNext(): Boolean = inner.hasNext()
        override fun next(): T = inner.next().first

        /** Returns the next element from the back of the iterator, or `null` if exhausted. */
        fun nextBack(): T? = inner.nextBack()?.first

        /** Returns the number of remaining elements. */
        fun len(): Int = inner.len()

        fun sizeHint(): Pair<Int, Int?> = inner.sizeHint()

        /** Consumes the iterator and returns the last element. */
        fun last(): T? = nextBack()

        /** Returns the minimum element. O(1) for sorted iterators. */
        fun min(): T? = if (hasNext()) next() else null

        /** Returns the maximum element. O(1) for sorted iterators. */
        fun max(): T? = nextBack()

        override fun toString(): String = "IntoIter(${inner})"
    }

    /**
     * An iterator over a sub-range of items in a `BTreeSet`. Created by
     * [BTreeSet.range].
     */
    class Range<T> internal constructor(
        internal val inner: io.github.kotlinmania.btree.Range<T, SetValZst>,
    ) : Iterator<T> {
        override fun hasNext(): Boolean = inner.hasNext()
        override fun next(): T = inner.next().first

        /** Returns the next element from the back of the iterator, or `null` if exhausted. */
        fun nextBack(): T? = inner.nextBack()?.first

        fun sizeHint(): Pair<Int, Int?> = Pair(0, null)

        fun last(): T? = nextBack()

        fun min(): T? = if (hasNext()) next() else null

        fun max(): T? = nextBack()

        override fun toString(): String = "Range(${inner})"
    }

    /**
     * A lazy iterator producing elements in the difference of `BTreeSet`s.
     */
    class Difference<T : Comparable<T>> internal constructor(
        internal var inner: DifferenceInner<T>,
    ) : Iterator<T> {
        // Pending-cache: Kotlin separates `hasNext` / `next` whereas Rust's
        // `Iterator::next` rolls them together. Compute the next yielded
        // element lazily into a `pending` cache.
        private var pending: T? = null
        private var primed: Boolean = false

        private fun computeNext(): T? {
            return when (val inner = this.inner) {
                is DifferenceInner.Stitch -> {
                    var selfNext = inner.selfIter.advance() ?: return null
                    while (true) {
                        val peeked = inner.otherIter.peek()
                        val cmp = if (peeked == null) -1 else selfNext.compareTo(peeked)
                        when {
                            cmp < 0 -> return selfNext
                            cmp == 0 -> {
                                selfNext = inner.selfIter.advance() ?: return null
                                inner.otherIter.next()
                            }
                            else -> {
                                inner.otherIter.next()
                            }
                        }
                    }
                    null
                }
                is DifferenceInner.Search -> {
                    while (true) {
                        val selfNext = inner.selfIter.advance() ?: return null
                        if (!inner.otherSet.contains(selfNext)) return selfNext
                    }
                    null
                }
                is DifferenceInner.Iterate -> inner.iter.advance()
            }
        }

        override fun hasNext(): Boolean {
            if (!primed) { pending = computeNext(); primed = true }
            return pending != null
        }

        override fun next(): T {
            if (!primed) pending = computeNext()
            val out = pending ?: throw NoSuchElementException()
            pending = null
            primed = false
            return out
        }

        fun min(): T? = if (hasNext()) next() else null

        fun sizeHint(): Pair<Int, Int?> {
            val (selfLen, otherLen) = when (val inner = inner) {
                is DifferenceInner.Stitch -> Pair(inner.selfIter.len(), inner.otherIter.len())
                is DifferenceInner.Search -> Pair(inner.selfIter.len(), inner.otherSet.len())
                is DifferenceInner.Iterate -> Pair(inner.iter.len(), 0)
            }
            return Pair((selfLen - otherLen).coerceAtLeast(0), selfLen)
        }

        override fun toString(): String = "Difference($inner)"
    }

    /**
     * A lazy iterator producing elements in the symmetric difference of
     * `BTreeSet`s.
     */
    class SymmetricDifference<T : Comparable<T>> internal constructor(
        internal val inner: MergeIterInner<T>,
    ) : Iterator<T> {
        private var pending: T? = null
        private var primed: Boolean = false

        private fun computeNext(): T? {
            while (true) {
                val (a, b) = inner.nexts { x, y -> x.compareTo(y) }
                if (a != null && b != null) continue // both produced => equal => skip both
                return a ?: b
            }
        }

        override fun hasNext(): Boolean {
            if (!primed) { pending = computeNext(); primed = true }
            return pending != null
        }

        override fun next(): T {
            if (!primed) pending = computeNext()
            val out = pending ?: throw NoSuchElementException()
            pending = null
            primed = false
            return out
        }

        fun min(): T? = if (hasNext()) next() else null

        fun sizeHint(): Pair<Int, Int?> {
            val (aLen, bLen) = inner.lens()
            return Pair(0, aLen + bLen)
        }

        override fun toString(): String = "SymmetricDifference($inner)"
    }

    /**
     * A lazy iterator producing elements in the intersection of `BTreeSet`s.
     */
    class Intersection<T : Comparable<T>> internal constructor(
        internal var inner: IntersectionInner<T>,
    ) : Iterator<T> {
        private var pending: T? = null
        private var primed: Boolean = false

        private fun computeNext(): T? {
            return when (val inner = this.inner) {
                is IntersectionInner.Stitch -> {
                    var aNext = inner.a.advance() ?: return null
                    var bNext = inner.b.advance() ?: return null
                    while (true) {
                        val cmp = aNext.compareTo(bNext)
                        when {
                            cmp < 0 -> aNext = inner.a.advance() ?: return null
                            cmp > 0 -> bNext = inner.b.advance() ?: return null
                            else -> return aNext
                        }
                    }
                    null
                }
                is IntersectionInner.Search -> {
                    while (true) {
                        val smallNext = inner.smallIter.advance() ?: return null
                        if (inner.largeSet.contains(smallNext)) return smallNext
                    }
                    null
                }
                is IntersectionInner.Answer -> {
                    val answer = inner.value
                    inner.value = null
                    answer
                }
            }
        }

        override fun hasNext(): Boolean {
            if (!primed) { pending = computeNext(); primed = true }
            return pending != null
        }

        override fun next(): T {
            if (!primed) pending = computeNext()
            val out = pending ?: throw NoSuchElementException()
            pending = null
            primed = false
            return out
        }

        fun min(): T? = if (hasNext()) next() else null

        fun sizeHint(): Pair<Int, Int?> = when (val inner = inner) {
            is IntersectionInner.Stitch -> Pair(0, minOf(inner.a.len(), inner.b.len()))
            is IntersectionInner.Search -> Pair(0, inner.smallIter.len())
            is IntersectionInner.Answer -> Pair(if (inner.value == null) 0 else 1, if (inner.value == null) 0 else 1)
        }

        override fun toString(): String = "Intersection($inner)"
    }

    /**
     * A lazy iterator producing elements in the union of `BTreeSet`s.
     */
    class Union<T : Comparable<T>> internal constructor(
        internal val inner: MergeIterInner<T>,
    ) : Iterator<T> {
        private var pending: T? = null
        private var primed: Boolean = false

        private fun computeNext(): T? {
            val (a, b) = inner.nexts { x, y -> x.compareTo(y) }
            return a ?: b
        }

        override fun hasNext(): Boolean {
            if (!primed) { pending = computeNext(); primed = true }
            return pending != null
        }

        override fun next(): T {
            if (!primed) pending = computeNext()
            val out = pending ?: throw NoSuchElementException()
            pending = null
            primed = false
            return out
        }

        fun min(): T? = if (hasNext()) next() else null

        fun sizeHint(): Pair<Int, Int?> {
            val (aLen, bLen) = inner.lens()
            return Pair(maxOf(aLen, bLen), aLen + bLen)
        }

        override fun toString(): String = "Union($inner)"
    }

    /** Iterator returned by [extractIf]. */
    class ExtractIf<T : Comparable<T>> internal constructor(
        internal val inner: io.github.kotlinmania.btree.ExtractIf<T, SetValZst, T>,
    ) : Iterator<T> {
        override fun hasNext(): Boolean = inner.hasNext()
        override fun next(): T = inner.next().first

        fun sizeHint(): Pair<Int, Int?> = inner.sizeHint()

        override fun toString(): String = "ExtractIf(${inner})"
    }

    // ========================================================================
    // Cursor types
    // ========================================================================

    /**
     * A cursor over a `BTreeSet`.
     *
     * A `Cursor` is like an iterator, except that it can freely seek
     * back-and-forth.
     *
     * Cursors always point to a gap between two elements in the set, and can
     * operate on the two immediately adjacent elements.
     *
     * A `Cursor` is created with [BTreeSet.lowerBound] and [BTreeSet.upperBound].
     */
    class Cursor<K : Comparable<K>> internal constructor(
        internal val inner: io.github.kotlinmania.btree.Cursor<K, SetValZst>,
    ) {
        /**
         * Advances the cursor to the next gap, returning the element that it
         * moved over.
         */
        fun next(): K? = inner.next()?.first

        /** Advances the cursor to the previous gap, returning the moved-over element. */
        fun prev(): K? = inner.prev()?.first

        /** Returns the next element without moving the cursor. */
        fun peekNext(): K? = inner.peekNext()?.first

        /** Returns the previous element without moving the cursor. */
        fun peekPrev(): K? = inner.peekPrev()?.first

        override fun toString(): String = "Cursor"
    }

    /**
     * A cursor over a `BTreeSet` with editing operations.
     */
    class CursorMut<T : Comparable<T>> internal constructor(
        internal val inner: io.github.kotlinmania.btree.CursorMut<T, SetValZst>,
    ) {
        /** Advances the cursor to the next gap, returning the moved-over element. */
        fun next(): T? = inner.next()?.first

        /** Advances the cursor to the previous gap, returning the moved-over element. */
        fun prev(): T? = inner.prev()?.first

        /** Returns the next element without moving the cursor. */
        fun peekNext(): T? = inner.peekNext()?.first

        /** Returns the previous element without moving the cursor. */
        fun peekPrev(): T? = inner.peekPrev()?.first

        /** Returns a read-only cursor pointing to the same location. */
        fun asCursor(): Cursor<T> = Cursor(inner.asCursor())

        /** Converts the cursor into a [CursorMutKey]. SAFETY: caller must preserve invariants. */
        fun withMutableKey(): CursorMutKey<T> = CursorMutKey(inner.withMutableKey())

        // ---- editing ops ---------------------------------------------------

        /** SAFETY: caller must ensure the new value preserves sorted order and uniqueness. */
        fun insertAfterUnchecked(value: T) = inner.insertAfterUnchecked(value, SetValZst)

        /** SAFETY: caller must ensure the new value preserves sorted order and uniqueness. */
        fun insertBeforeUnchecked(value: T) = inner.insertBeforeUnchecked(value, SetValZst)

        /** Inserts after, returning [Result.failure] containing [UnorderedKeyError] on order violation. */
        fun insertAfter(value: T): Result<Unit> = inner.insertAfter(value, SetValZst)

        /** Inserts before, returning [Result.failure] containing [UnorderedKeyError] on order violation. */
        fun insertBefore(value: T): Result<Unit> = inner.insertBefore(value, SetValZst)

        /** Removes the next element from the `BTreeSet`. */
        fun removeNext(): T? = inner.removeNext()?.first

        /** Removes the preceding element from the `BTreeSet`. */
        fun removePrev(): T? = inner.removePrev()?.first

        override fun toString(): String = "CursorMut"
    }

    /**
     * A cursor over a `BTreeSet` with editing operations, and which allows
     * mutating elements.
     *
     * SAFETY: callers must ensure all elements remain in sorted order and
     * unique while the cursor is held.
     */
    class CursorMutKey<T : Comparable<T>> internal constructor(
        internal val inner: io.github.kotlinmania.btree.CursorMutKey<T, SetValZst>,
    ) {
        /** Advances the cursor to the next gap, returning the moved-over element. */
        fun next(): T? = inner.next()?.first

        /** Advances the cursor to the previous gap, returning the moved-over element. */
        fun prev(): T? = inner.prev()?.first

        /** Returns the next element without moving the cursor. */
        fun peekNext(): T? = inner.peekNext()?.first

        /** Returns the previous element without moving the cursor. */
        fun peekPrev(): T? = inner.peekPrev()?.first

        /** Returns a read-only cursor pointing to the same location. */
        fun asCursor(): Cursor<T> = Cursor(inner.asCursor())

        // ---- editing ops ---------------------------------------------------

        /** SAFETY: caller must ensure the new value preserves sorted order and uniqueness. */
        fun insertAfterUnchecked(value: T) = inner.insertAfterUnchecked(value, SetValZst)

        /** SAFETY: caller must ensure the new value preserves sorted order and uniqueness. */
        fun insertBeforeUnchecked(value: T) = inner.insertBeforeUnchecked(value, SetValZst)

        /** Inserts after, returning [Result.failure] containing [UnorderedKeyError] on order violation. */
        fun insertAfter(value: T): Result<Unit> = inner.insertAfter(value, SetValZst)

        /** Inserts before, returning [Result.failure] containing [UnorderedKeyError] on order violation. */
        fun insertBefore(value: T): Result<Unit> = inner.insertBefore(value, SetValZst)

        /** Removes the next element from the `BTreeSet`. */
        fun removeNext(): T? = inner.removeNext()?.first

        /** Removes the preceding element from the `BTreeSet`. */
        fun removePrev(): T? = inner.removePrev()?.first

        override fun toString(): String = "CursorMutKey"
    }
}

// ============================================================================
// Internal sum types for Difference / Intersection state machines
// ============================================================================

/** State for [BTreeSet.Difference]. */
internal sealed class DifferenceInner<T : Comparable<T>> {
    /** Iterate all of `self` and some of `other`, spotting matches along the way. */
    class Stitch<T : Comparable<T>>(
        val selfIter: BTreeSet.Iter<T>,
        val otherIter: PeekableSetIter<T>,
    ) : DifferenceInner<T>() {
        override fun toString(): String = "Stitch(self_iter=$selfIter, other_iter=$otherIter)"
    }

    /** Iterate `self`, look up in `other`. */
    class Search<T : Comparable<T>>(
        val selfIter: BTreeSet.Iter<T>,
        val otherSet: BTreeSet<T>,
    ) : DifferenceInner<T>() {
        override fun toString(): String = "Search(self_iter=$selfIter, other_iter=$otherSet)"
    }

    /** Simply produce all elements in `self`. */
    class Iterate<T : Comparable<T>>(val iter: BTreeSet.Iter<T>) : DifferenceInner<T>() {
        override fun toString(): String = "Iterate($iter)"
    }
}

/** State for [BTreeSet.Intersection]. */
internal sealed class IntersectionInner<T : Comparable<T>> {
    /** Iterate similarly sized sets jointly, spotting matches along the way. */
    class Stitch<T : Comparable<T>>(
        val a: BTreeSet.Iter<T>,
        val b: BTreeSet.Iter<T>,
    ) : IntersectionInner<T>() {
        override fun toString(): String = "Stitch(a=$a, b=$b)"
    }

    /** Iterate a small set, look up in the large set. */
    class Search<T : Comparable<T>>(
        val smallIter: BTreeSet.Iter<T>,
        val largeSet: BTreeSet<T>,
    ) : IntersectionInner<T>() {
        override fun toString(): String = "Search(small_iter=$smallIter, large_set=$largeSet)"
    }

    /** Return a specific element or emptiness. Mutable so the answer can be taken once. */
    class Answer<T : Comparable<T>>(var value: T?) : IntersectionInner<T>() {
        override fun toString(): String = "Answer($value)"
    }
}

/**
 * One-element-buffer adapter over a [BTreeSet.Iter], emulating Rust's
 * `Peekable<Iter<T>>` (Kotlin stdlib has no `Peekable`).
 */
internal class PeekableSetIter<T>(private val source: BTreeSet.Iter<T>) {
    private var buffered: T? = null
    private var bufferedFilled: Boolean = false

    fun peek(): T? {
        if (!bufferedFilled && source.hasNext()) {
            buffered = source.next()
            bufferedFilled = true
        }
        return buffered
    }

    fun next(): T? {
        if (bufferedFilled) {
            val b = buffered
            buffered = null
            bufferedFilled = false
            return b
        }
        return if (source.hasNext()) source.next() else null
    }

    /** Returns the number of remaining elements, including the buffered peek if present. */
    fun len(): Int = source.len() + (if (bufferedFilled) 1 else 0)

    override fun toString(): String = "Peekable(${source})"
}

/** Pulls the next item from a `BTreeSet.Iter<T>`, returning `null` on exhaustion. */
private fun <T> BTreeSet.Iter<T>.advance(): T? = if (hasNext()) next() else null

/** An unbounded [RangeBounds] used by [BTreeSet.retain] to cover all keys. */
private fun <T> unboundedSet(): RangeBounds<T> = object : RangeBounds<T> {
    override fun startBound(): Bound<T> = Bound.Unbounded
    override fun endBound(): Bound<T> = Bound.Unbounded
}
/**
 * A view into a single entry in a set, which may either be vacant or occupied.
 *
 * This `enum` is constructed from the [`BTreeSet.entry`] method.
 */
sealed class SetEntry<T : Comparable<T>> {
    /** A view into an occupied entry in a `BTreeSet`. */
    class Occupied<T : Comparable<T>>(val entry: SetOccupiedEntry<T>) : SetEntry<T>()

    /** A view into a vacant entry in a `BTreeSet`. */
    class Vacant<T : Comparable<T>>(val entry: SetVacantEntry<T>) : SetEntry<T>()

    fun get(): T = when (this) {
        is Occupied -> entry.get()
        is Vacant -> entry.get()
    }
}

class SetOccupiedEntry<T : Comparable<T>> internal constructor(
    internal val inner: OccupiedEntry<T, SetValZst>
) {
    fun get(): T = inner.key()
    fun remove(): T = inner.removeEntry().first
}

class SetVacantEntry<T : Comparable<T>> internal constructor(
    internal val inner: VacantEntry<T, SetValZst>
) {
    fun get(): T = inner.key()
    fun insert() {
        inner.insert(SetValZst)
    }
}
