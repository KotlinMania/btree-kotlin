// port-lint: source library/alloc/src/collections/btree/dedup_sorted_iter.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

// Rust: import core::iter::Peekable;
// Kotlin's stdlib has no Peekable adapter, so we inline a tiny one-element
// lookahead buffer that mirrors `Peekable::next` / `Peekable::peek`. The
// buffered element is itself a `Pair<K, V>`, which is a non-null reference,
// so `Pair<K, V>?` cleanly encodes Rust's `Option<(K, V)>`.
private class Peekable<K, V>(private val source: Iterator<Pair<K, V>>) {
    private var buffered: Pair<K, V>? = null

    fun peek(): Pair<K, V>? {
        if (buffered == null && source.hasNext()) {
            buffered = source.next()
        }
        return buffered
    }

    fun next(): Pair<K, V>? {
        val b = buffered
        if (b != null) {
            buffered = null
            return b
        }
        return if (source.hasNext()) source.next() else null
    }
}

/// An iterator for deduping the key of a sorted iterator.
/// When encountering the duplicated key, only the last key-value pair is yielded.
///
/// Used by [`BTreeMap::bulkBuildFromSortedIter`][1].
///
/// [1]: crate::collections::BTreeMap::bulkBuildFromSortedIter
internal class DedupSortedIter<K, V, I : Iterator<Pair<K, V>>>(
    iter: I,
) : Iterator<Pair<K, V>> where K : Any {
    private val iter: Peekable<K, V> = Peekable(iter)

    companion object {
        internal fun <K : Any, V, I : Iterator<Pair<K, V>>> new(iter: I): DedupSortedIter<K, V, I> {
            return DedupSortedIter(iter)
        }
    }

    // Kotlin's `Iterator` interface separates `hasNext` from `next`; Rust's
    // `Iterator::next` returns `Option<Item>` in one call. We compute the
    // next yielded item lazily and cache it so both methods agree.
    private var pending: Pair<K, V>? = null

    private fun computeNext(): Pair<K, V>? {
        while (true) {
            val next = iter.next() ?: return null

            val peeked = iter.peek() ?: return next

            if (next.first != peeked.first) {
                return next
            }
        }
    }

    override fun hasNext(): Boolean {
        if (pending == null) {
            pending = computeNext()
        }
        return pending != null
    }

    override fun next(): Pair<K, V> {
        if (pending == null) {
            pending = computeNext()
        }
        val out = pending ?: throw NoSuchElementException()
        pending = null
        return out
    }
}
