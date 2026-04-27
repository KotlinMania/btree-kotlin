// port-lint: source library/core/src/ops/range.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree_kotlin

// Subset port: only the `Bound<T>` enum and the `RangeBounds<T>` trait
// (plus its default `contains` method) are translated. The concrete
// `Range<T>`, `RangeFrom<T>`, `RangeTo<T>`, `RangeInclusive<T>`,
// `RangeToInclusive<T>`, `RangeFull`, and `(Bound<T>, Bound<T>)`
// implementations are out of scope for this port — Kotlin's stdlib has
// its own range types and the BTreeMap port only consumes the trait
// interface (Search.kt's `searchTreeForBifurcation` calls `startBound()`
// / `endBound()`). Concrete adapters can be wired later in Phase 4 when
// `BTreeMap::range` lands and a public surface needs them.
//
// Lifetime translation: Rust's `fn start_bound(&self) -> Bound<&T>`
// borrows the inner T. Kotlin has no shared-borrow vocabulary, so the
// port returns `Bound<T>` directly. Implementations are free to return
// the stored T as-is.

/**
 * `Bound<T>` mirrors `core::ops::Bound`. An endpoint of a range of keys.
 *
 * Translates Rust's `pub enum Bound<T> { Included(T), Excluded(T), Unbounded }`.
 * Per AGENTS.md "Sum types" guidance this is rendered as a sealed class
 * with three variants and per-subclass `toString()` matching the upstream
 * `#[derive(Debug)]` rendering.
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
}

/**
 * `RangeBounds<T>` mirrors `core::ops::RangeBounds`. Implemented by Rust's
 * built-in range types, produced by range syntax like `..`, `a..`, `..b`,
 * `..=c`, `d..e`, or `f..=g`.
 *
 * Translates the upstream `pub trait RangeBounds<T: ?Sized>`. The
 * `?Sized` bound is irrelevant in Kotlin and is dropped. Rust's
 * `start_bound(&self) -> Bound<&T>` becomes `startBound(): Bound<T>`
 * (no shared-borrow vocabulary in Kotlin — see file header).
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
     * Translates Rust's
     * `fn contains<U>(&self, item: &U) -> bool where T: PartialOrd<U>, U: ?Sized + PartialOrd<T>`.
     * The Kotlin port narrows U to T and requires the bound values to be
     * `Comparable<T>` (asserted via a runtime cast — Kotlin interface
     * methods cannot impose extra constraints on the interface's own
     * type parameter beyond what's declared on the interface). Cross-type
     * partial ordering has no Kotlin equivalent.
     */
    fun contains(item: T): Boolean {
        @Suppress("UNCHECKED_CAST")
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
}
