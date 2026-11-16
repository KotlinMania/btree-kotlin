// port-lint: source testing/rng.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree.testing

/** XorShiftRng */
internal class DeterministicRng {
    private var count: Int = 0
    private var x: UInt = 0x193a6754u
    private var y: UInt = 0xa8a7d469u
    private var z: UInt = 0x97830e05u
    private var w: UInt = 0x113ba7bbu

    /** Guarantees that each returned number is unique. */
    fun next(): UInt {
        count += 1
        check(count <= 70029)
        val xv = x
        val t = xv xor (xv shl 11)
        x = y
        y = z
        z = w
        val wv = w
        w = wv xor (wv shr 19) xor (t xor (t shr 8))
        return w
    }

    companion object {
        fun new(): DeterministicRng = DeterministicRng()
    }
}

internal fun randData(len: Int): List<UInt> {
    val rng = DeterministicRng()
    return List(len) { rng.next() }
}

internal fun randPairData(len: Int): List<Pair<UInt, UInt>> {
    val rng = DeterministicRng()
    return List(len) { Pair(rng.next(), rng.next()) }
}
