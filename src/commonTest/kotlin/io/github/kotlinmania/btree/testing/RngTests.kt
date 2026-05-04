// port-lint: ignore
// Pinned regression tests for the Kotlin port of upstream
// `testing/rng.rs::DeterministicRng`. These tests are not transliterated
// from upstream — upstream does not test its test-helper RNG. They exist
// solely to detect any future drift between this port and upstream's
// XorShift128 by asserting the first few u32 outputs match the values
// the upstream algorithm produces from the documented seed.
package io.github.kotlinmania.btree.testing

import kotlin.test.Test
import kotlin.test.assertEquals

class RngTests {
    @Test
    fun firstFiveOutputsMatchUpstream() {
        val rng = DeterministicRng()
        val first = List(5) { rng.next() }
        assertEquals(
            listOf(
                0xDBF1620Fu, // 3690029583
                0x4D63E184u, // 1298391428
                0xC21F3D0Bu, // 3256827147
                0x0ED55C8Cu, //  248863884
                0x5E64A643u, // 1583654467
            ),
            first,
        )
    }

    @Test
    fun outputsAreUniqueWithinBudget() {
        val rng = DeterministicRng()
        val seen = HashSet<UInt>()
        repeat(1529) { idx ->
            val v = rng.next()
            assertEquals(true, seen.add(v), "duplicate at index $idx: $v")
        }
    }

    @Test
    fun budgetIsEnforced() {
        val rng = DeterministicRng()
        repeat(70029) { rng.next() }
        var threw = false
        try {
            rng.next()
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertEquals(true, threw, "DeterministicRng must reject the 70030th call")
    }
}
