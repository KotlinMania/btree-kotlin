// port-lint: source set_val.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

/**
 * Marker object for internal `BTreeSet` values.
 * Used instead of `Unit` to differentiate between:
 * * `BTreeMap<T, Unit>` (possible user-defined map)
 * * `BTreeMap<T, SetValZst>` (internal set representation)
 */
internal data object SetValZst : Comparable<SetValZst> {
    override fun toString(): String = "SetValZST"

    override fun compareTo(other: SetValZst): Int = 0
}

/**
 * An interface to differentiate between `BTreeMap` and `BTreeSet` values.
 * Returns `true` only for type [SetValZst], `false` for all other types.
 */
internal interface IsSetVal {
    fun isSetVal(): Boolean
}

internal fun <V> isSetVal(value: V): Boolean = value is SetValZst

internal inline fun <reified V> isSetVal(): Boolean = V::class == SetValZst::class
