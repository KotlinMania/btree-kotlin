// port-lint: source range.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

/**
 * An unbounded range (`..`).
 *
 * `RangeFull` is primarily used as a slicing index, its shorthand is `..`.
 * It cannot serve as an `Iterator` because it doesn't have a starting point.
 */
data object RangeFull : RangeBounds<Nothing> {
    override fun toString(): String = ".."

    override fun startBound(): Bound<Nothing> = Bound.Unbounded
    override fun endBound(): Bound<Nothing> = Bound.Unbounded
}

/**
 * A (half-open) range bounded inclusively below and exclusively above
 * (`start..end`).
 *
 * The range `start..end` contains all values with `start <= x < end`.
 * It is empty if `start >= end`.
 */
data class OpsRange<Idx>(
    /** The lower bound of the range (inclusive). */
    val start: Idx,
    /** The upper bound of the range (exclusive). */
    val end: Idx,
) : RangeBounds<Idx> {
    override fun toString(): String = "$start..$end"

    override fun startBound(): Bound<Idx> = Bound.Included(start)
    override fun endBound(): Bound<Idx> = Bound.Excluded(end)

    /**
     * Returns `true` if `item` is contained in the range.
     */
    override fun contains(item: Idx): Boolean = (this as RangeBounds<Idx>).contains(item)

    /**
     * Returns `true` if the range contains no items.
     *
     * The range is empty if `start >= end`. Translates Rust's
     * `!(self.start < self.end)`.
     */
    override fun isEmpty(): Boolean {
        val s = start as Comparable<Idx>
        return !(s.compareTo(end) < 0)
    }
}

/**
 * A range only bounded inclusively below (`start..`).
 *
 * The `RangeFrom` `start..` contains all values with `x >= start`.
 *
 * Note: Overflow in the `Iterator` implementation (when the contained
 * data type reaches its numerical limit) is allowed to panic, wrap, or
 * saturate. This behavior is defined by the implementation of the `Step`
 * trait. For primitive integers, this follows the normal rules, and respects
 * the overflow checks profile (panic in debug, wrap in release).
 */
data class RangeFrom<Idx>(
    /** The lower bound of the range (inclusive). */
    val start: Idx,
) : RangeBounds<Idx> {
    override fun toString(): String = "$start.."

    override fun startBound(): Bound<Idx> = Bound.Included(start)
    override fun endBound(): Bound<Idx> = Bound.Unbounded

    /** Returns `true` if `item` is contained in the range. */
    override fun contains(item: Idx): Boolean = (this as RangeBounds<Idx>).contains(item)
}

/**
 * A range only bounded exclusively above (`..end`).
 *
 * The `RangeTo` `..end` contains all values with `x < end`.
 * It cannot serve as an `Iterator` because it doesn't have a starting point.
 */
data class RangeTo<Idx>(
    /** The upper bound of the range (exclusive). */
    val end: Idx,
) : RangeBounds<Idx> {
    override fun toString(): String = "..$end"

    override fun startBound(): Bound<Idx> = Bound.Unbounded
    override fun endBound(): Bound<Idx> = Bound.Excluded(end)

    /** Returns `true` if `item` is contained in the range. */
    override fun contains(item: Idx): Boolean = (this as RangeBounds<Idx>).contains(item)
}

/**
 * A range bounded inclusively below and above (`start..=end`).
 *
 * The `RangeInclusive` `start..=end` contains all values with `x >= start`
 * and `x <= end`. It is empty unless `start <= end`.
 *
 * This iterator is fused, but the specific values of `start` and `end` after
 * iteration has finished are unspecified other than that `isEmpty()`
 * will return `true` once no more values will be produced.
 */
class RangeInclusive<Idx>(
    start: Idx,
    end: Idx,
) : RangeBounds<Idx> {
    // Note that the fields here are not public to allow changing the
    // representation in the future; in particular, while we could plausibly
    // expose start/end, modifying them without changing (future/current)
    // private fields may lead to incorrect behavior, so we don't want to
    // support that mode.
    internal val start: Idx = start
    internal val end: Idx = end

    // This field is:
    //  - `false` upon construction
    //  - `false` when iteration has yielded an element and the iterator is not exhausted
    //  - `true` when iteration has been used to exhaust the iterator
    //
    // This is required to support PartialEq and Hash without a PartialOrd bound or specialization.
    internal var exhausted: Boolean = false

    /** Returns the lower bound of the range (inclusive). */
    fun start(): Idx = start

    /** Returns the upper bound of the range (inclusive). */
    fun end(): Idx = end

    /** Destructures the `RangeInclusive` into (lower bound, upper (inclusive) bound). */
    fun intoInner(): Pair<Idx, Idx> = Pair(start, end)

    override fun toString(): String =
        if (exhausted) "$start..=$end (exhausted)" else "$start..=$end"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RangeInclusive<*>) return false
        return start == other.start && end == other.end && exhausted == other.exhausted
    }

    override fun hashCode(): Int {
        var h = start.hashCode()
        h = 31 * h + end.hashCode()
        h = 31 * h + exhausted.hashCode()
        return h
    }

    override fun startBound(): Bound<Idx> = Bound.Included(start)
    override fun endBound(): Bound<Idx> =
        if (exhausted) {
            // When the iterator is exhausted, we usually have start == end,
            // but we want the range to appear empty, containing nothing.
            Bound.Excluded(end)
        } else {
            Bound.Included(end)
        }

    /** Returns `true` if `item` is contained in the range. */
    override fun contains(item: Idx): Boolean = (this as RangeBounds<Idx>).contains(item)

    /** Returns `true` if the range contains no items. */
    override fun isEmpty(): Boolean {
        if (exhausted) return true
        val s = start as Comparable<Idx>
        return !(s.compareTo(end) <= 0)
    }

    companion object {
        /** Creates a new inclusive range. Equivalent to writing `start..=end`. */
        fun <Idx> new(start: Idx, end: Idx): RangeInclusive<Idx> = RangeInclusive(start, end)
    }
}

/**
 * Converts to an exclusive `Range` for `SliceIndex` implementations.
 * The caller is responsible for dealing with `end == Int.MAX_VALUE`.
 *
 * Translates the slice-range helper for inclusive ranges over indices. Kotlin uses
 * `Int` because BTreeMap's slice consumers only feed it container
 * indices.
 */
internal fun RangeInclusive<Int>.intoSliceRange(): OpsRange<Int> {
    // If we're not exhausted, we want to simply slice `start..end + 1`.
    // If we are exhausted, then slicing with `end + 1..end + 1` gives us an
    // empty range that is still subject to bounds-checks for that endpoint.
    val exclusiveEnd = end + 1
    val rangeStart = if (exhausted) exclusiveEnd else start
    return OpsRange(rangeStart, exclusiveEnd)
}

/**
 * A range only bounded inclusively above (`..=end`).
 *
 * The `RangeToInclusive` `..=end` contains all values with `x <= end`.
 * It cannot serve as an `Iterator` because it doesn't have a starting point.
 */
data class RangeToInclusive<Idx>(
    /** The upper bound of the range (inclusive) */
    val end: Idx,
) : RangeBounds<Idx> {
    override fun toString(): String = "..=$end"

    override fun startBound(): Bound<Idx> = Bound.Unbounded
    override fun endBound(): Bound<Idx> = Bound.Included(end)

    /** Returns `true` if `item` is contained in the range. */
    override fun contains(item: Idx): Boolean = (this as RangeBounds<Idx>).contains(item)
}

// RangeToInclusive<Idx> cannot implementation From<RangeTo<Idx>>
// because underflow would be possible with (..0).into()

/**
 * `Bound<T>` mirrors `core::ops::Bound`. An endpoint of a range of keys.
 *
 * Three variants — Included, Excluded, Unbounded — rendered as a sealed
 * class with per-subclass `toString()` matching upstream's Debug-derive
 * output.
 *
 * `Bound`s are range endpoints:
 *
 * ```
 * (..100).startBound() == Unbounded
 * (1..12).startBound() == Included(1)
 * (1..12).endBound()   == Excluded(12)
 * ```
 */
sealed class Bound<out T> {
    /** An inclusive bound. */
    data class Included<T>(val value: T) : Bound<T>() {
        override fun toString(): String = "Included($value)"
    }

    /** An exclusive bound. */
    data class Excluded<T>(val value: T) : Bound<T>() {
        override fun toString(): String = "Excluded($value)"
    }

    /** An infinite endpoint. Indicates that there is no bound in this direction. */
    data object Unbounded : Bound<Nothing>() {
        override fun toString(): String = "Unbounded"
    }

    /**
     * Maps a `Bound<T>` to a `Bound<U>` by applying a function to the contained value
     * (including both `Included` and `Excluded`), returning a `Bound` of the same kind.
     */
    fun <U> map(f: (T) -> U): Bound<U> = when (this) {
        is Unbounded -> Unbounded
        is Included -> Included(f(value))
        is Excluded -> Excluded(f(value))
    }

    /**
     * Converts from `&Bound<T>` to `Bound<&T>`.
     *
     * Kotlin has no shared-borrow vocabulary, so the conversion is the
     * identity. Provided for symmetry with the upstream API.
     */
    fun asRef(): Bound<T> = this

    /**
     * Converts from `&mut Bound<T>` to `Bound<&mut T>`.
     *
     * Kotlin has no shared-borrow vocabulary, so the conversion is the
     * identity. Provided for symmetry with the upstream API.
     */
    fun asMut(): Bound<T> = this

    companion object
}

/**
 * Map a `Bound<T>` to a `Bound<T>` by cloning the contents of the bound.
 */
fun <T> Bound<T>.cloned(): Bound<T> = this

/**
 * Map a `Bound<T>` to a `Bound<T>` by copying the contents of the bound.
 */
fun <T> Bound<T>.copied(): Bound<T> = this

/**
 * `RangeBounds` is implemented by Rust's built-in range types, produced
 * by range syntax like `..`, `a..`, `..b`, `..=c`, `d..e`, or `f..=g`.
 */
interface RangeBounds<T> {
    /**
     * Start index bound. Returns the start value as a `Bound`.
     */
    fun startBound(): Bound<T>

    /**
     * End index bound. Returns the end value as a `Bound`.
     */
    fun endBound(): Bound<T>

    /**
     * Returns `true` if `item` is contained in the range.
     *
     * Mirrors `function contains(item: T) -> bool where T: Comparable<T>`.
     * Bound values are asserted to be `Comparable<T>` at the runtime cast.
     */
    fun contains(item: T): Boolean {
        val cmpItem = item as Comparable<T>
        val startOk = when (val s = startBound()) {
            is Bound.Included -> cmpItem.compareTo(s.value) >= 0
            is Bound.Excluded -> cmpItem.compareTo(s.value) > 0
            Bound.Unbounded -> true
        }
        if (!startOk) return false
        return when (val e = endBound()) {
            is Bound.Included -> cmpItem.compareTo(e.value) <= 0
            is Bound.Excluded -> cmpItem.compareTo(e.value) < 0
            Bound.Unbounded -> true
        }
    }

    /**
     * Returns `true` if the range contains no items.
     * One-sided ranges (`RangeFrom`, etc) always return `false`.
     */
    fun isEmpty(): Boolean {
        val s = startBound()
        val e = endBound()
        if (s is Bound.Unbounded || e is Bound.Unbounded) return false
        if (s is Bound.Included && e is Bound.Included) {
            val a = s.value as Comparable<T>
            return !(a.compareTo(e.value) <= 0)
        }

        val sv: T = when (s) {
            is Bound.Included -> s.value
            is Bound.Excluded -> s.value
            Bound.Unbounded -> return false
        }
        val ev: T = when (e) {
            is Bound.Included -> e.value
            is Bound.Excluded -> e.value
            Bound.Unbounded -> return false
        }
        val a = sv as Comparable<T>
        return !(a.compareTo(ev) < 0)
    }
}

/**
 * Used to convert a range into start and end bounds, consuming the
 * range by value.
 *
 * `IntoBounds` is implemented by Rust's built-in range types, produced
 * by range syntax like `..`, `a..`, `..b`, `..=c`, `d..e`, or `f..=g`.
 */
interface IntoBounds<T> : RangeBounds<T> {
    /**
     * Convert this range into the start and end bounds.
     * Returns `(startBound, endBound)`.
     */
    fun intoBounds(): Pair<Bound<T>, Bound<T>>

    /**
     * Compute the intersection of `self` and `other`.
     */
    fun intersect(other: IntoBounds<T>): Pair<Bound<T>, Bound<T>> {
        val (selfStart, selfEnd) = this.intoBounds()
        val (otherStart, otherEnd) = other.intoBounds()

        val start: Bound<T> = run {
            when {
                selfStart is Bound.Included && otherStart is Bound.Included -> {
                    val a = selfStart.value as Comparable<T>
                    Bound.Included(if (a.compareTo(otherStart.value) >= 0) selfStart.value else otherStart.value)
                }
                selfStart is Bound.Excluded && otherStart is Bound.Excluded -> {
                    val a = selfStart.value as Comparable<T>
                    Bound.Excluded(if (a.compareTo(otherStart.value) >= 0) selfStart.value else otherStart.value)
                }
                selfStart is Bound.Unbounded && otherStart is Bound.Unbounded -> Bound.Unbounded
                selfStart is Bound.Unbounded -> otherStart
                otherStart is Bound.Unbounded -> selfStart
                else -> {
                    // (Included, Excluded) or (Excluded, Included)
                    val (i, e) = if (selfStart is Bound.Included) {
                        (selfStart.value to (otherStart as Bound.Excluded).value)
                    } else {
                        ((otherStart as Bound.Included).value to (selfStart as Bound.Excluded).value)
                    }
                    val ic = i as Comparable<T>
                    if (ic.compareTo(e) > 0) Bound.Included(i) else Bound.Excluded(e)
                }
            }
        }
        val end: Bound<T> = run {
            when {
                selfEnd is Bound.Included && otherEnd is Bound.Included -> {
                    val a = selfEnd.value as Comparable<T>
                    Bound.Included(if (a.compareTo(otherEnd.value) <= 0) selfEnd.value else otherEnd.value)
                }
                selfEnd is Bound.Excluded && otherEnd is Bound.Excluded -> {
                    val a = selfEnd.value as Comparable<T>
                    Bound.Excluded(if (a.compareTo(otherEnd.value) <= 0) selfEnd.value else otherEnd.value)
                }
                selfEnd is Bound.Unbounded && otherEnd is Bound.Unbounded -> Bound.Unbounded
                selfEnd is Bound.Unbounded -> otherEnd
                otherEnd is Bound.Unbounded -> selfEnd
                else -> {
                    val (i, e) = if (selfEnd is Bound.Included) {
                        (selfEnd.value to (otherEnd as Bound.Excluded).value)
                    } else {
                        ((otherEnd as Bound.Included).value to (selfEnd as Bound.Excluded).value)
                    }
                    val ic = i as Comparable<T>
                    if (ic.compareTo(e) < 0) Bound.Included(i) else Bound.Excluded(e)
                }
            }
        }

        return Pair(start, end)
    }
}

/**
 * Wrapper around `(Bound<T>, Bound<T>)` that implements `RangeBounds<T>`.
 *
 * Translates `implementation<T> const RangeBounds<T> for (Bound<T>, Bound<T>)`. Kotlin
 * cannot retroactively make `Pair` implement an interface, so we provide a
 * dedicated wrapper. [boundsPair] gives a concise factory.
 */
data class BoundsPair<T>(
    val startBoundValue: Bound<T>,
    val endBoundValue: Bound<T>,
) : IntoBounds<T> {
    override fun startBound(): Bound<T> = startBoundValue
    override fun endBound(): Bound<T> = endBoundValue
    override fun intoBounds(): Pair<Bound<T>, Bound<T>> = Pair(startBoundValue, endBoundValue)
}

/** Convenience factory mirroring Rust's `(start, end)` tuple usage. */
fun <T> boundsPair(start: Bound<T>, end: Bound<T>): BoundsPair<T> = BoundsPair(start, end)

/**
 * `IntoBounds` implementation for [RangeFull].
 */
fun RangeFull.intoBounds(): Pair<Bound<Nothing>, Bound<Nothing>> = Pair(Bound.Unbounded, Bound.Unbounded)

/**
 * `IntoBounds` implementation for [RangeFrom].
 */
fun <T> RangeFrom<T>.intoBounds(): Pair<Bound<T>, Bound<T>> = Pair(Bound.Included(start), Bound.Unbounded)

/**
 * `IntoBounds` implementation for [RangeTo].
 */
fun <T> RangeTo<T>.intoBounds(): Pair<Bound<T>, Bound<T>> = Pair(Bound.Unbounded, Bound.Excluded(end))

/**
 * `IntoBounds` implementation for [Range].
 */
fun <T> OpsRange<T>.intoBounds(): Pair<Bound<T>, Bound<T>> = Pair(Bound.Included(start), Bound.Excluded(end))

/**
 * `IntoBounds` implementation for [RangeInclusive].
 */
fun <T> RangeInclusive<T>.intoBounds(): Pair<Bound<T>, Bound<T>> = Pair(
    Bound.Included(start),
    if (exhausted) {
        // When the iterator is exhausted, we usually have start == end,
        // but we want the range to appear empty, containing nothing.
        Bound.Excluded(end)
    } else {
        Bound.Included(end)
    },
)

/**
 * `IntoBounds` implementation for [RangeToInclusive].
 */
fun <T> RangeToInclusive<T>.intoBounds(): Pair<Bound<T>, Bound<T>> = Pair(Bound.Unbounded, Bound.Included(end))

/**
 * An internal helper for `splitOff` functions indicating
 * which end a `OneSidedRange` is bounded on.
 */
enum class OneSidedRangeBound {
    /** The range is bounded inclusively from below and is unbounded above. */
    StartInclusive,

    /** The range is bounded exclusively from above and is unbounded below. */
    End,

    /** The range is bounded inclusively from above and is unbounded below. */
    EndInclusive,
}

/**
 * `OneSidedRange` is implemented for built-in range types that are unbounded
 * on one side. For example, `a..`, `..b` and `..=c` implement `OneSidedRange`,
 * but `..`, `d..e`, and `f..=g` do not.
 *
 * Types that implement `OneSidedRange<T>` must return `Bound::Unbounded`
 * from one of `RangeBounds.startBound` or `RangeBounds.endBound`.
 */
interface OneSidedRange<T> : RangeBounds<T> {
    /**
     * An internal-only helper function for `splitOff` and
     * `splitOffMut` that returns the bound of the one-sided range.
     */
    fun bound(): Pair<OneSidedRangeBound, T>
}

/**
 * `OneSidedRange` implementation for [RangeTo].
 */
fun <T> RangeTo<T>.bound(): Pair<OneSidedRangeBound, T> = Pair(OneSidedRangeBound.End, end)

/**
 * `OneSidedRange` implementation for [RangeFrom].
 */
fun <T> RangeFrom<T>.bound(): Pair<OneSidedRangeBound, T> = Pair(OneSidedRangeBound.StartInclusive, start)

/**
 * `OneSidedRange` implementation for [RangeToInclusive].
 */
fun <T> RangeToInclusive<T>.bound(): Pair<OneSidedRangeBound, T> = Pair(OneSidedRangeBound.EndInclusive, end)
