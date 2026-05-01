// port-lint: source mem.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

/**
 * This replaces the value behind the `v` unique reference by calling the
 * relevant function.
 *
 * If a panic occurs in the `change` closure, the entire process will be aborted.
 */
internal fun <T> takeMut(v: T, change: (T) -> T): T {
    return replace(v) { value -> Pair(change(value), Unit) }.first
}

private object Intrinsics {
    fun abort(): Nothing {
        throw IllegalStateException("aborted")
    }
}

private class PanicGuard {
    fun drop(): Nothing {
        Intrinsics.abort()
    }
}

/**
 * This replaces the value behind the `v` unique reference by calling the
 * relevant function, and returns a result obtained along the way.
 *
 * If a panic occurs in the `change` closure, the entire process will be aborted.
 */
internal fun <T, R> replace(v: T, change: (T) -> Pair<T, R>): Pair<T, R> {
    val guard = PanicGuard()
    return try {
        val value = v
        val (newValue, ret) = change(value)
        Pair(newValue, ret)
    } catch (_: Throwable) {
        guard.drop()
    }
}
