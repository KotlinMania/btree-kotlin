// port-lint: source library/alloc/src/collections/btree/mem.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

/**
 * This replaces the value behind the `v` unique reference by calling the
 * relevant function.
 *
 * If a panic occurs in the `change` closure, the entire process will be aborted.
 */
internal inline fun <T> takeMut(v: T, change: (T) -> T): T {
    return replace(v) { value -> Pair(change(value), Unit) }.first
}

/**
 * This replaces the value behind the `v` unique reference by calling the
 * relevant function, and returns a result obtained along the way.
 *
 * If a panic occurs in the `change` closure, the entire process will be aborted.
 */
internal inline fun <T, R> replace(v: T, change: (T) -> Pair<T, R>): Pair<T, R> {
    val value = v
    val (newValue, ret) = change(value)
    return Pair(newValue, ret)
}
