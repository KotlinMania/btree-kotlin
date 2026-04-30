// port-lint: source borrow.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

/**
 * Models a reborrow of some unique reference, when you know that the reborrow
 * and all its descendants (i.e., all pointers and references derived from it)
 * will not be used any more at some point, after which you want to use the
 * original unique reference again.
 *
 * In Rust the reborrow checker usually handles this stacking of borrows; some
 * control flows that accomplish this stacking are too complicated for the
 * compiler to follow, and `DormantMutRef` lets the caller manage the lifetime
 * manually. In a GC'd Kotlin runtime there are no raw pointers and no
 * exclusive-borrow tracking — every `DormantMutRef` operation simply hands
 * the captured reference back.
 */
internal class DormantMutRef<T> private constructor(private val ref: T) {
    companion object {
        /** Capture a borrow, and immediately reborrow it. */
        fun <T> new(t: T): Pair<T, DormantMutRef<T>> = Pair(t, DormantMutRef(t))
    }

    /** Revert to the borrow initially captured. */
    fun awaken(): T = ref

    /** Borrows a new mutable reference from the borrow initially captured. */
    fun reborrow(): T = ref

    /** Borrows a new shared reference from the borrow initially captured. */
    fun reborrowShared(): T = ref
}
