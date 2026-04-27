// port-lint: source library/alloc/src/collections/btree/merge_iter.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

/**
 * Core of an iterator that merges the output of two strictly ascending iterators,
 * for instance a union or a symmetric difference.
 *
 * Translated from `MergeIterInner<I: Iterator>` in upstream `merge_iter.rs`. Rust
 * stores a single peeked value of either origin in an `Option<Peeked<I>>`; we
 * encode the same state with two nullable fields plus a tag, since Kotlin lacks
 * a free-standing tagged union for this small a state machine.
 *
 * Rust's `impl Clone` is omitted: cloning a Kotlin `Iterator<T>` is not generally
 * possible, so the bound `I: Clone` has no portable analogue. Callers that need
 * to fork iteration must construct two `MergeIterInner` instances over freshly
 * obtained iterators.
 */
internal class MergeIterInner<T>(
    private val a: Iterator<T>,
    private val b: Iterator<T>,
) {
    /** Peeked-from-A slot. Non-null iff [peekedSide] == [PeekSide.A]. */
    private var peekedA: T? = null
    /** Peeked-from-B slot. Non-null iff [peekedSide] == [PeekSide.B]. */
    private var peekedB: T? = null
    /** Which (if any) side the most-recently-peeked-but-not-yielded item came from. */
    private var peekedSide: PeekSide = PeekSide.NONE

    private enum class PeekSide { NONE, A, B }

    /**
     * Returns the next pair of items stemming from the pair of sources
     * being merged. If both returned options contain a value, that value
     * is equal and occurs in both sources. If one of the returned options
     * contains a value, that value doesn't occur in the other source (or
     * the sources are not strictly ascending). If neither returned option
     * contains a value, iteration has finished and subsequent calls will
     * return the same empty pair.
     *
     * The [cmp] callback must obey the [Comparator.compare] contract:
     * negative when its first argument precedes the second, zero when they
     * are equal, positive when the first follows. This matches Rust's
     * `Ordering::{Less, Equal, Greater}` mapping.
     *
     * The Rust signature requires `I: FusedIterator`; Kotlin iterators are
     * effectively fused once `hasNext()` returns `false`, which is the
     * idiom this method relies on.
     */
    fun nexts(cmp: (T, T) -> Int): Pair<T?, T?> {
        var aNext: T?
        var bNext: T?
        when (peekedSide) {
            PeekSide.A -> {
                aNext = peekedA
                bNext = if (b.hasNext()) b.next() else null
            }
            PeekSide.B -> {
                bNext = peekedB
                aNext = if (a.hasNext()) a.next() else null
            }
            PeekSide.NONE -> {
                aNext = if (a.hasNext()) a.next() else null
                bNext = if (b.hasNext()) b.next() else null
            }
        }
        // Clear the peeked slot now that we've moved its contents into a/bNext;
        // mirrors `self.peeked.take()` in the Rust source.
        peekedA = null
        peekedB = null
        peekedSide = PeekSide.NONE
        val a1 = aNext
        val b1 = bNext
        if (a1 != null && b1 != null) {
            val ord = cmp(a1, b1)
            when {
                ord < 0 -> {
                    // Ordering::Less => self.peeked = b_next.take().map(Peeked::B)
                    peekedB = bNext
                    peekedSide = PeekSide.B
                    bNext = null
                }
                ord > 0 -> {
                    // Ordering::Greater => self.peeked = a_next.take().map(Peeked::A)
                    peekedA = aNext
                    peekedSide = PeekSide.A
                    aNext = null
                }
                else -> {
                    // Ordering::Equal => ()
                }
            }
        }
        return Pair(aNext, bNext)
    }

    /**
     * Returns a pair of upper bounds for the `size_hint` of the final iterator.
     *
     * Rust constrains this method with `I: ExactSizeIterator`; Kotlin's
     * `Iterator<T>` has no length, so callers must supply the remaining
     * length of each underlying iterator via [aLen] and [bLen]. The peeked
     * item, if any, is added on top — matching the Rust body verbatim.
     */
    fun lens(aLen: Int, bLen: Int): Pair<Int, Int> = when (peekedSide) {
        PeekSide.A -> Pair(1 + aLen, bLen)
        PeekSide.B -> Pair(aLen, 1 + bLen)
        PeekSide.NONE -> Pair(aLen, bLen)
    }

    /**
     * Mirrors Rust's `impl Debug for MergeIterInner`. The peeked slot is rendered
     * as `null`, `A(<value>)`, or `B(<value>)` to match the upstream
     * `Option<Peeked<I>>` discriminator.
     */
    override fun toString(): String {
        val peekedRepr = when (peekedSide) {
            PeekSide.NONE -> "null"
            PeekSide.A -> "A($peekedA)"
            PeekSide.B -> "B($peekedB)"
        }
        return "MergeIterInner($a, $b, $peekedRepr)"
    }
}
