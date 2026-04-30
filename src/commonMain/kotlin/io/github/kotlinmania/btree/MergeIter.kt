// port-lint: source merge_iter.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

/**
 * Benchmarks faster than wrapping both iterators in a Peekable,
 * probably because we can afford to impose a FusedIterator bound.
 */
internal sealed class Peeked<out T> {
    data class A<out T>(val item: T) : Peeked<T>()
    data class B<out T>(val item: T) : Peeked<T>()
}

/**
 * Core of an iterator that merges the output of two strictly ascending iterators,
 * for instance a union or a symmetric difference.
 */
internal class MergeIterInner<T>(
    private val a: BTreeSet.Iter<T>,
    private val b: BTreeSet.Iter<T>,
) {
    private var peeked: Peeked<T>? = null

    companion object {
        /** Creates a new core for an iterator merging a pair of sources. */
        internal fun <T> new(a: BTreeSet.Iter<T>, b: BTreeSet.Iter<T>): MergeIterInner<T> {
            return MergeIterInner(a, b)
        }
    }

    override fun toString(): String {
        return "MergeIterInner(a=$a, b=$b, peeked=$peeked)"
    }

    /**
     * Returns the next pair of items stemming from the pair of sources
     * being merged. If both returned options contain a value, that value
     * is equal and occurs in both sources. If one of the returned options
     * contains a value, that value doesn't occur in the other source (or
     * the sources are not strictly ascending). If neither returned option
     * contains a value, iteration has finished and subsequent calls will
     * return the same empty pair.
     */
    internal fun nexts(cmp: (T, T) -> Int): Pair<T?, T?> {
        var aNext: T?
        var bNext: T?
        val taken = peeked
        peeked = null
        when (taken) {
            is Peeked.A -> {
                aNext = taken.item
                bNext = if (b.hasNext()) b.next() else null
            }
            is Peeked.B -> {
                bNext = taken.item
                aNext = if (a.hasNext()) a.next() else null
            }
            null -> {
                aNext = if (a.hasNext()) a.next() else null
                bNext = if (b.hasNext()) b.next() else null
            }
        }
        val a1 = aNext
        val b1 = bNext
        if (a1 != null && b1 != null) {
            val ord = cmp(a1, b1)
            when {
                ord < 0 -> {
                    peeked = bNext?.let { Peeked.B(it) }
                    bNext = null
                }
                ord > 0 -> {
                    peeked = aNext?.let { Peeked.A(it) }
                    aNext = null
                }
                else -> Unit
            }
        }
        return Pair(aNext, bNext)
    }

    /** Returns a pair of upper bounds for the size hint of the final iterator. */
    internal fun lens(): Pair<Int, Int> = when (peeked) {
        is Peeked.A -> Pair(1 + a.len(), b.len())
        is Peeked.B -> Pair(a.len(), 1 + b.len())
        null -> Pair(a.len(), b.len())
    }
}
