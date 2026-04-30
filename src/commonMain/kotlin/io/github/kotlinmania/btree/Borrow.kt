// port-lint: source borrow.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

import io.github.kotlinmania.core.marker.PhantomData
import io.github.kotlinmania.core.ptr.NonNull

/**
 * Models a reborrow of some unique reference, when you know that the reborrow
 * and all its descendants (i.e., all pointers and references derived from it)
 * will not be used any more at some point, after which you want to use the
 * original unique reference again.
 *
 * The borrow checker usually handles this stacking of borrows for you, but
 * some control flows that accomplish this stacking are too complicated for
 * the compiler to follow. A `DormantMutRef` allows you to check borrowing
 * yourself, while still expressing its stacked nature, and encapsulating
 * the raw pointer code needed to do this without undefined behavior.
 */
internal class DormantMutRef<T> private constructor(
    private val ptr: NonNull<T>,
    private val _marker: PhantomData,
) {
    companion object {
        /**
         * Capture a unique borrow, and immediately reborrow it. For the compiler,
         * the scope of the new reference is the same as the scope of the
         * original reference, but you promise to use it for a shorter period.
         */
        fun <T> new(t: T): Pair<T, DormantMutRef<T>> {
            val ptr = NonNull.from(t)
            // SAFETY: we hold the borrow throughout the scope via `_marker`, and we expose
            // only this reference, so it is unique.
            val newRef = ptr.asPtr()
            return Pair(newRef, DormantMutRef(ptr, PhantomData))
        }
    }

    /**
     * Revert to the unique borrow initially captured.
     *
     * # Safety
     *
     * The reborrow must have ended, i.e., the reference returned by `new` and
     * all pointers and references derived from it, must not be used anymore.
     */
    fun awaken(): T {
        // SAFETY: our own safety conditions imply this reference is again unique.
        return this.ptr.asPtr()
    }

    /**
     * Borrows a new mutable reference from the unique borrow initially captured.
     *
     * # Safety
     *
     * The reborrow must have ended, i.e., the reference returned by `new` and
     * all pointers and references derived from it, must not be used anymore.
     */
    fun reborrow(): T {
        // SAFETY: our own safety conditions imply this reference is again unique.
        return this.ptr.asPtr()
    }

    /**
     * Borrows a new shared reference from the unique borrow initially captured.
     *
     * # Safety
     *
     * The reborrow must have ended, i.e., the reference returned by `new` and
     * all pointers and references derived from it, must not be used anymore.
     */
    fun reborrowShared(): T {
        // SAFETY: our own safety conditions imply this reference is again unique.
        return this.ptr.asPtr()
    }
}
