// port-lint: source map/tests.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree.testing

/**
 * A minimal wrapper used by the upstream map/set tests: a single-field cell
 * that gives every value its own identity. Equality and ordering forward to
 * the wrapped payload; the wrapper is otherwise transparent.
 *
 * IntArray payloads are compared element-wise so two-element array keys order
 * consistently.
 */
class Box<T>(var value: T) : Comparable<Box<T>> {
    @Suppress("UNCHECKED_CAST")
    override fun compareTo(other: Box<T>): Int {
        val a = value
        val b = other.value
        return when {
            a is IntArray && b is IntArray -> compareIntArrays(a, b)
            a is Comparable<*> -> (a as Comparable<Any?>).compareTo(b)
            else -> error("Box value is not Comparable: $a")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Box<*>) return false
        val a = value
        val b = other.value
        return when {
            a is IntArray && b is IntArray -> a.contentEquals(b)
            else -> a == b
        }
    }

    override fun hashCode(): Int {
        val v = value
        return when (v) {
            is IntArray -> v.contentHashCode()
            else -> v?.hashCode() ?: 0
        }
    }

    override fun toString(): String = "Box($value)"

    private fun compareIntArrays(a: IntArray, b: IntArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val c = a[i].compareTo(b[i])
            if (c != 0) return c
        }
        return a.size.compareTo(b.size)
    }
}

/**
 * Gathers all references from a mutable iterator and makes sure the GC notices
 * if using them is dangerous.
 *
 * Mirrors the upstream helper that swaps each yielded mutable reference with a
 * dummy slot to exercise iterator aliasing behavior. Here each yielded box is
 * a movable cell whose value field can be swapped in place, twice.
 */
fun <T> testAllRefs(dummy: Box<T>, iter: Iterator<Box<T>>) {
    // Gather all those references.
    val refs = mutableListOf<Box<T>>()
    while (iter.hasNext()) refs.add(iter.next())
    // Use them all. Twice, to be sure we got all interleavings.
    for (r in refs) {
        val tmp = dummy.value
        dummy.value = r.value
        r.value = tmp
    }
    for (r in refs) {
        val tmp = dummy.value
        dummy.value = r.value
        r.value = tmp
    }
}
