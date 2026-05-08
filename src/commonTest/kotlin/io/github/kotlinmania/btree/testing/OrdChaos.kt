// port-lint: source testing/ord_chaos.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree.testing

// Minimal type with a [Comparable] implementation violating transitivity.
internal sealed class Cyclic3 : Comparable<Cyclic3> {
    data object A : Cyclic3()
    data object B : Cyclic3()
    data object C : Cyclic3()

    override fun compareTo(other: Cyclic3): Int = when (this to other) {
        A to A, B to B, C to C -> 0
        A to B, B to C, C to A -> -1
        else -> 1
    }
}

// Controls the ordering of values wrapped by [Governed].
internal class Governor {
    var flipped: Boolean = false

    companion object {
        fun new(): Governor = Governor()
    }

    fun flip() {
        flipped = !flipped
    }
}

// Type with a [Comparable] implementation that forms a total order at any moment
// (assuming that [T] respects total order), but can suddenly be made to invert
// that total order.
internal class Governed<T : Comparable<T>>(val value: T, val gov: Governor) : Comparable<Governed<T>> {
    override fun compareTo(other: Governed<T>): Int {
        check(gov === other.gov)
        val ord = value.compareTo(other.value)
        return if (gov.flipped) -ord else ord
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Governed<*>) return false
        check(gov === other.gov)
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
}

// Comparison based only on the ID, the name is ignored.
internal class IdBased(val id: Int, val name: String) : Comparable<IdBased> {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IdBased) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun compareTo(other: IdBased): Int = id.compareTo(other.id)
}
