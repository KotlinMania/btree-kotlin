// port-lint: source library/alloc/src/collections/btree/borrow.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

/**
 * Models a reborrow of some unique reference, when you know that the reborrow
 * and all its descendants (i.e., all pointers and references derived from it)
 * will not be used any more at some point, after which you want to import the
 * original unique reference again.
 *
 * The borrow checker usually handles this stacking of borrows for you, but
 * some control flows that accomplish this stacking are too complicated for
 * the compiler to follow. A `DormantMutRef` allows you to check borrowing
 * yourself, while still expressing its stacked nature, and encapsulating
 * the raw pointer code needed to do this without undefined behavior.
 *
 * In Kotlin the `'a` lifetime parameter and `NonNull<T>` raw pointer dissolve:
 * the wrapper just holds a regular reference, and the GC supersedes the
 * lifetime gymnastics that the Rust borrow checker required.
 */
internal class DormantMutRef<T> private constructor(
    private val ptr: T,
) {
    companion object {
        /**
         * Capture a unique borrow, and immediately reborrow it. For the compiler,
         * the lifetime of the new reference is the same as the lifetime of the
         * original reference, but you promise to import it for a shorter period.
         *
         * Rust returns the tuple `(&'a mut T, Self)`; in Kotlin that destructure
         * becomes a `Pair<T, DormantMutRef<T>>`.
         */
        fun <T> new(t: T): Pair<T, DormantMutRef<T>> {
            val ptr = t
            // SAFETY: we hold the borrow throughout 'a via `_marker`, and we expose
            // only this reference, so it is unique.
            val newRef = ptr
            return Pair(newRef, DormantMutRef(ptr))
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
        return ptr
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
        return ptr
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
        return ptr
    }
}
