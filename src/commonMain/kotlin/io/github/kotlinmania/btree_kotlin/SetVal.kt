// port-lint: source library/alloc/src/collections/btree/set_val.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree_kotlin

/**
 * Zero-Sized Type (ZST) for internal `BTreeSet` values.
 * Used instead of `Unit` to differentiate between:
 * * `BTreeMap<T, Unit>` (possible user-defined map)
 * * `BTreeMap<T, SetValZst>` (internal set representation)
 *
 * Upstream spells this `SetValZST`; we use Kotlin's PascalCase
 * convention (`SetValZst`) per project naming rules.
 *
 * Modeled as a `data object`, which gives `equals`/`hashCode` (always
 * equal to itself) and a stable `toString()` matching the upstream
 * `#[derive(Debug)]` rendering of the unit struct. `Comparable` is
 * provided so this type can stand in wherever the upstream
 * `#[derive(Ord, PartialOrd)]` is needed; all instances compare equal.
 */
internal data object SetValZst : Comparable<SetValZst> {
    /** Matches Rust's `Debug` derive on the unit struct `SetValZST`. */
    override fun toString(): String = "SetValZST"

    /** Single-instance ZST: every value is equal to every other. */
    override fun compareTo(other: SetValZst): Int = 0
}

/**
 * Bridge for upstream's `IsSetVal` trait.
 *
 * Upstream uses Rust trait specialization — a blanket
 * `impl<V> IsSetVal for V { default fn is_set_val() -> bool { false } }`
 * plus a specialized `impl IsSetVal for SetValZST { fn is_set_val() ->
 * bool { true } }`. Kotlin has no trait specialization; per
 * AGENTS.md the equivalent is a runtime `is SetValZst` check at the
 * call site.
 *
 * Note the signature change relative to upstream: Rust's
 * `V::is_set_val()` takes no value (pure static dispatch on the type
 * parameter), whereas this Kotlin form requires a value of `V` so the
 * runtime check has something to look at. Callers that previously
 * called `isSetVal<V>()` need to thread through whatever `V` they
 * have on hand.
 */
internal fun <V> isSetVal(value: V): Boolean = value is SetValZst
