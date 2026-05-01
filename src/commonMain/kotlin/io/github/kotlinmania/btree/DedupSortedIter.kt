// port-lint: source dedup_sorted_iter.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

private class Peekable<K, V>(private val source: Iterator<Pair<K, V>>) {
    private var buffered: Pair<K, V>? = null

    fun peek(): Pair<K, V>? {
        if (buffered == null && source.hasNext()) {
            buffered = source.next()
        }
        return buffered
    }

    fun next(): Pair<K, V>? {
        val item = buffered
        if (item != null) {
            buffered = null
            return item
        }
        return if (source.hasNext()) source.next() else null
    }
}

/**
 * An iterator for deduping the key of a sorted iterator.
 * When encountering the duplicated key, only the last key-value pair is yielded.
 *
 * Used by [BTreeMap.bulkBuildFromSortedIter].
 */
internal class DedupSortedIter<K : Any, V, I : Iterator<Pair<K, V>>>(
    iter: I,
) : Iterator<Pair<K, V>> {
    private val iter = Peekable(iter)
    private var pending: Pair<K, V>? = null

    companion object {
        internal fun <K : Any, V, I : Iterator<Pair<K, V>>> new(iter: I): DedupSortedIter<K, V, I> {
            return DedupSortedIter(iter)
        }
    }

    override fun hasNext(): Boolean {
        if (pending == null) {
            pending = try {
                next()
            } catch (_: NoSuchElementException) {
                null
            }
        }
        return pending != null
    }

    override fun next(): Pair<K, V> {
        val pending = pending
        if (pending != null) {
            this.pending = null
            return pending
        }

        while (true) {
            val next = iter.next() ?: throw NoSuchElementException()

            val peeked = iter.peek() ?: return next

            if (next.first != peeked.first) {
                return next
            }
        }
    }
}
