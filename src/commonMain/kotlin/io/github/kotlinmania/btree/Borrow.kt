// port-lint: source borrow.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

/**
 * Models a reborrow of some unique reference, when you know that the reborrow
 * and all its descendants, all references derived from it, will not be used any
 * more at some point, after which you want to use the original unique reference
 * again.
 *
 * Ordinary code usually handles this stacking of borrows for you, but some
 * control flows that accomplish this stacking are too complicated to express
 * directly. A [DormantMutRef] allows you to check borrowing yourself, while
 * still expressing its stacked nature.
 */
internal class DormantMutRef<T> private constructor(private val ref: T) {
    companion object {
        /**
         * Capture a unique borrow, and immediately reborrow it. The reborrow is
         * valid for the same scope as the original reference, but callers promise
         * to use it for a shorter period.
         */
        fun <T> new(t: T): Pair<T, DormantMutRef<T>> = Pair(t, DormantMutRef(t))
    }

    /**
     * Revert to the unique borrow initially captured.
     *
     * The reborrow must have ended, i.e., the reference returned by [new] and
     * all references derived from it, must not be used anymore.
     */
    fun awaken(): T = ref

    /**
     * Borrows a new mutable reference from the unique borrow initially captured.
     *
     * The reborrow must have ended, i.e., the reference returned by [new] and
     * all references derived from it, must not be used anymore.
     */
    fun reborrow(): T = ref

    /**
     * Borrows a new shared reference from the unique borrow initially captured.
     *
     * The reborrow must have ended, i.e., the reference returned by [new] and
     * all references derived from it, must not be used anymore.
     */
    fun reborrowShared(): T = ref
}
