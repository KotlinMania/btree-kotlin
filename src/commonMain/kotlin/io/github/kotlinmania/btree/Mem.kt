// port-lint: source library/alloc/src/collections/btree/mem.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

/**
 * This replaces the value behind the `v` unique reference by calling the
 * relevant function.
 *
 * If a panic occurs in the `change` closure, the entire process will be aborted.
 *
 * Translation note: Rust takes `&mut T` and writes back in place via
 * `ptr::read` / `ptr::write`, guarded by a `PanicGuard` whose `Drop` calls
 * `intrinsics::abort` so a panic mid-transition can never expose a moved-out
 * slot. In Kotlin there is no `&mut` to a stack value and no moved-out state
 * to hide — the caller assigns the returned value back. If `change` throws,
 * the caller's binding keeps its previous value, which is the safe outcome
 * the panic-guard was simulating.
 */
// keep as illustration and for future use
internal inline fun <T> takeMut(v: T, change: (T) -> T): T {
    return replace(v) { value -> Pair(change(value), Unit) }.first
}

/**
 * This replaces the value behind the `v` unique reference by calling the
 * relevant function, and returns a result obtained along the way.
 *
 * If a panic occurs in the `change` closure, the entire process will be aborted.
 *
 * Returns a [Pair] of the new value and the side result; the caller is
 * responsible for writing the new value back into whatever slot held `v`.
 */
internal inline fun <T, R> replace(v: T, change: (T) -> Pair<T, R>): Pair<T, R> {
    // SAFETY: Rust uses `ptr::read(v)` here to move out of `*v` and a
    // `PanicGuard` whose `Drop` aborts the process if `change` panics, so the
    // slot is never observed in a moved-out state. Kotlin has no moved-out
    // state — `value` is just a reference — so the guard and the `mem::forget`
    // both dissolve.
    val value = v
    val (newValue, ret) = change(value)
    return Pair(newValue, ret)
}
