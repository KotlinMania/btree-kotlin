// port-lint: source library/alloc/src/collections/btree/map/tests.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmokeTests {
    @Test
    fun btreeMapInsertGetRemove() {
        val m = BTreeMap<Int, String>()
        assertTrue(m.isEmpty())

        assertNull(m.insert(2, "b"))
        assertNull(m.insert(1, "a"))
        assertNull(m.insert(3, "c"))

        assertEquals(3, m.size)
        assertEquals("a", m.get(1))
        assertEquals("b", m.get(2))
        assertEquals("c", m.get(3))

        assertEquals("b", m.insert(2, "bb"))
        assertEquals(3, m.size)
        assertEquals("bb", m.get(2))

        assertEquals("a", m.remove(1))
        assertEquals(2, m.size)
        assertNull(m.get(1))
    }

    @Test
    fun btreeMapIterationIsSorted() {
        val m = BTreeMap<Int, String>()
        m.insert(3, "c")
        m.insert(1, "a")
        m.insert(2, "b")

        val keys = mutableListOf<Int>()
        val values = mutableListOf<String>()
        for ((k, v) in m.iter()) {
            keys.add(k)
            values.add(v)
        }
        assertEquals(listOf(1, 2, 3), keys)
        assertEquals(listOf("a", "b", "c"), values)
    }

    @Test
    fun btreeSetInsertContainsRemove() {
        val s = BTreeSet<Int>()
        assertTrue(s.isEmpty())

        assertTrue(s.insert(2))
        assertTrue(s.insert(1))
        assertTrue(s.insert(3))
        assertFalse(s.insert(2))

        assertEquals(3, s.len())
        assertTrue(s.contains(1))
        assertTrue(s.contains(2))
        assertTrue(s.contains(3))
        assertFalse(s.contains(4))

        assertTrue(s.remove(2))
        assertEquals(2, s.len())
        assertFalse(s.contains(2))
    }

    @Test
    fun btreeMapFirstLastKeyValue() {
        val m = BTreeMap<Int, String>()
        assertNull(m.firstKeyValue())
        assertNull(m.lastKeyValue())

        m.insert(2, "b")
        m.insert(1, "a")
        m.insert(3, "c")

        val first = m.firstKeyValue()
        val last = m.lastKeyValue()

        assertNotNull(first)
        assertNotNull(last)
        assertEquals(1, first.first)
        assertEquals("a", first.second)
        assertEquals(3, last.first)
        assertEquals("c", last.second)
    }
}
