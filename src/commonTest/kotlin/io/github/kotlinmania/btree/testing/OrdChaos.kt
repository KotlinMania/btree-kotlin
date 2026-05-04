// port-lint: source testing/ord_chaos.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree.testing

/**
 * A mutable ordering authority shared by [Governed] keys. Tests use a
 * single [Governor] to violate the [Comparable] contract on demand:
 * insert keys into a sorted map, [flip] the governor, then re-query —
 * the tree's internal invariants must remain self-consistent even though
 * the comparison results have changed.
 */
internal class Governor {
    var flipped: Boolean = false

    fun flip() {
        flipped = !flipped
    }

    companion object {
        fun new(): Governor = Governor()
    }
}

/**
 * A key that consults a [Governor] to decide its ordering. Two [Governed]
 * keys with the same [id] are equal, but their relative order depends on
 * the governor's [Governor.flipped] flag.
 */
internal class Governed(val id: Int, val gov: Governor) : Comparable<Governed> {
    override fun compareTo(other: Governed): Int {
        val cmp = id.compareTo(other.id)
        return if (gov.flipped) -cmp else cmp
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Governed) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Governed($id)"
}

/**
 * A non-transitive ordering: `A < B`, `B < C`, `C < A`. Used to stress
 * test code paths that assume [Comparable] actually behaves transitively.
 */
internal sealed class Cyclic3 : Comparable<Cyclic3> {
    data object A : Cyclic3()
    data object B : Cyclic3()
    data object C : Cyclic3()

    override fun compareTo(other: Cyclic3): Int {
        if (this == other) return 0
        return when (this) {
            A -> if (other == B) -1 else 1
            B -> if (other == C) -1 else 1
            C -> if (other == A) -1 else 1
        }
    }
}

/**
 * A key whose [Comparable] order looks at [id] only, while equality also
 * distinguishes [name]. Used by the upstream id-based merge / append /
 * insert tests to verify that the tree honours its [Comparable] instance
 * for ordering and does not silently de-duplicate on the
 * structural-equal [name] field.
 */
internal class IdBased(val id: Int, val name: String) : Comparable<IdBased> {
    override fun compareTo(other: IdBased): Int = id.compareTo(other.id)
}
