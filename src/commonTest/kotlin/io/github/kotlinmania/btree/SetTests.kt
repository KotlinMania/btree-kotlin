// port-lint: source set/tests.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

import io.github.kotlinmania.btree.testing.CrashTestDummy
import io.github.kotlinmania.btree.testing.CrashTestDummyRef
import io.github.kotlinmania.btree.testing.Panic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetTests {
    @Test
    fun testCloneEq() {
        val m = BTreeSet<Int>()

        m.insert(1)
        m.insert(2)

        // BTreeSet equality is structural; "clone" in Rust is a value copy.
        // We rebuild a copy by inserting all elements and compare for equality.
        val copy = BTreeSet<Int>()
        for (v in m) copy.insert(v)
        assertEquals(copy, m)
    }

    @Test
    fun testIterMinMax() {
        val a = BTreeSet<Int>()
        assertNull(a.iter().asSequence().minOrNull())
        assertNull(a.iter().asSequence().maxOrNull())
        assertNull(a.range(RangeFull).asSequence().minOrNull())
        assertNull(a.range(RangeFull).asSequence().maxOrNull())
        assertNull(a.difference(BTreeSet()).asSequence().minOrNull())
        assertNull(a.difference(BTreeSet()).asSequence().maxOrNull())
        assertNull(a.intersection(a).asSequence().minOrNull())
        assertNull(a.intersection(a).asSequence().maxOrNull())
        assertNull(a.symmetricDifference(BTreeSet()).asSequence().minOrNull())
        assertNull(a.symmetricDifference(BTreeSet()).asSequence().maxOrNull())
        assertNull(a.union(a).asSequence().minOrNull())
        assertNull(a.union(a).asSequence().maxOrNull())
        a.insert(1)
        a.insert(2)
        assertEquals(1, a.iter().asSequence().minOrNull())
        assertEquals(2, a.iter().asSequence().maxOrNull())
        assertEquals(1, a.range(RangeFull).asSequence().minOrNull())
        assertEquals(2, a.range(RangeFull).asSequence().maxOrNull())
        assertEquals(1, a.difference(BTreeSet()).asSequence().minOrNull())
        assertEquals(2, a.difference(BTreeSet()).asSequence().maxOrNull())
        assertEquals(1, a.intersection(a).asSequence().minOrNull())
        assertEquals(2, a.intersection(a).asSequence().maxOrNull())
        assertEquals(1, a.symmetricDifference(BTreeSet()).asSequence().minOrNull())
        assertEquals(2, a.symmetricDifference(BTreeSet()).asSequence().maxOrNull())
        assertEquals(1, a.union(a).asSequence().minOrNull())
        assertEquals(2, a.union(a).asSequence().maxOrNull())
    }

    /**
     * Builds two sets from [a] and [b], calls [f] with a probe that asserts each
     * yielded element matches [expected] in order, and finally checks that
     * exactly [expected.size] elements were yielded.
     */
    private fun check(
        a: IntArray,
        b: IntArray,
        expected: IntArray,
        f: (BTreeSet<Int>, BTreeSet<Int>, (Int) -> Boolean) -> Boolean,
    ) {
        val setA = BTreeSet<Int>()
        val setB = BTreeSet<Int>()

        for (x in a) {
            assertTrue(setA.insert(x))
        }
        for (y in b) {
            assertTrue(setB.insert(y))
        }

        var i = 0
        f(setA, setB) { x ->
            if (i < expected.size) {
                assertEquals(x, expected[i])
            }
            i += 1
            true
        }
        assertEquals(i, expected.size)
    }

    @Test
    fun testIntersection() {
        fun checkIntersection(a: IntArray, b: IntArray, expected: IntArray) {
            check(a, b, expected) { x, y, f ->
                val it = x.intersection(y)
                var ok = true
                while (it.hasNext()) {
                    if (!f(it.next())) {
                        ok = false
                        break
                    }
                }
                ok
            }
        }

        checkIntersection(intArrayOf(), intArrayOf(), intArrayOf())
        checkIntersection(intArrayOf(1, 2, 3), intArrayOf(), intArrayOf())
        checkIntersection(intArrayOf(), intArrayOf(1, 2, 3), intArrayOf())
        checkIntersection(intArrayOf(2), intArrayOf(1, 2, 3), intArrayOf(2))
        checkIntersection(intArrayOf(1, 2, 3), intArrayOf(2), intArrayOf(2))
        checkIntersection(
            intArrayOf(11, 1, 3, 77, 103, 5, -5),
            intArrayOf(2, 11, 77, -9, -42, 5, 3),
            intArrayOf(3, 5, 11, 77),
        )

        val large = IntArray(100) { it }
        checkIntersection(intArrayOf(), large, intArrayOf())
        checkIntersection(large, intArrayOf(), intArrayOf())
        checkIntersection(intArrayOf(-1), large, intArrayOf())
        checkIntersection(large, intArrayOf(-1), intArrayOf())
        checkIntersection(intArrayOf(0), large, intArrayOf(0))
        checkIntersection(large, intArrayOf(0), intArrayOf(0))
        checkIntersection(intArrayOf(99), large, intArrayOf(99))
        checkIntersection(large, intArrayOf(99), intArrayOf(99))
        checkIntersection(intArrayOf(100), large, intArrayOf())
        checkIntersection(large, intArrayOf(100), intArrayOf())
        checkIntersection(intArrayOf(11, 5000, 1, 3, 77, 8924), large, intArrayOf(1, 3, 11, 77))
    }

    @Test
    fun testIntersectionSizeHint() {
        val x = BTreeSet.fromIterable(listOf(3, 4))
        val y = BTreeSet.fromIterable(listOf(1, 2, 3))
        var iter = x.intersection(y)
        assertEquals(Pair(1, 1), iter.sizeHint())
        assertEquals(3, iter.next())
        assertEquals(Pair(0, 0), iter.sizeHint())
        assertFalse(iter.hasNext())

        iter = y.intersection(y)
        assertEquals(Pair(0, 3), iter.sizeHint())
        assertEquals(1, iter.next())
        assertEquals(Pair(0, 2), iter.sizeHint())
    }

    @Test
    fun testDifference() {
        fun checkDifference(a: IntArray, b: IntArray, expected: IntArray) {
            check(a, b, expected) { x, y, f ->
                val it = x.difference(y)
                var ok = true
                while (it.hasNext()) {
                    if (!f(it.next())) {
                        ok = false
                        break
                    }
                }
                ok
            }
        }

        checkDifference(intArrayOf(), intArrayOf(), intArrayOf())
        checkDifference(intArrayOf(1, 12), intArrayOf(), intArrayOf(1, 12))
        checkDifference(intArrayOf(), intArrayOf(1, 2, 3, 9), intArrayOf())
        checkDifference(intArrayOf(1, 3, 5, 9, 11), intArrayOf(3, 9), intArrayOf(1, 5, 11))
        checkDifference(intArrayOf(1, 3, 5, 9, 11), intArrayOf(3, 6, 9), intArrayOf(1, 5, 11))
        checkDifference(intArrayOf(1, 3, 5, 9, 11), intArrayOf(0, 1), intArrayOf(3, 5, 9, 11))
        checkDifference(intArrayOf(1, 3, 5, 9, 11), intArrayOf(11, 12), intArrayOf(1, 3, 5, 9))
        checkDifference(
            intArrayOf(-5, 11, 22, 33, 40, 42),
            intArrayOf(-12, -5, 14, 23, 34, 38, 39, 50),
            intArrayOf(11, 22, 33, 40, 42),
        )

        val large = IntArray(100) { it }
        checkDifference(intArrayOf(), large, intArrayOf())
        checkDifference(intArrayOf(-1), large, intArrayOf(-1))
        checkDifference(intArrayOf(0), large, intArrayOf())
        checkDifference(intArrayOf(99), large, intArrayOf())
        checkDifference(intArrayOf(100), large, intArrayOf(100))
        checkDifference(intArrayOf(11, 5000, 1, 3, 77, 8924), large, intArrayOf(5000, 8924))
        checkDifference(large, intArrayOf(), large)
        checkDifference(large, intArrayOf(-1), large)
        checkDifference(large, intArrayOf(100), large)
    }

    @Test
    fun testDifferenceSizeHint() {
        val s246 = BTreeSet.fromIterable(listOf(2, 4, 6))
        val s23456 = BTreeSet.fromIterable((2..6).toList())
        var iter = s246.difference(s23456)
        assertEquals(Pair(0, 3), iter.sizeHint())
        assertFalse(iter.hasNext())

        val s12345 = BTreeSet.fromIterable((1..5).toList())
        iter = s246.difference(s12345)
        assertEquals(Pair(0, 3), iter.sizeHint())
        assertEquals(6, iter.next())
        assertEquals(Pair(0, 0), iter.sizeHint())
        assertFalse(iter.hasNext())

        val s34567 = BTreeSet.fromIterable((3..7).toList())
        iter = s246.difference(s34567)
        assertEquals(Pair(0, 3), iter.sizeHint())
        assertEquals(2, iter.next())
        assertEquals(Pair(0, 2), iter.sizeHint())
        assertFalse(iter.hasNext())

        val s1 = BTreeSet.fromIterable((-9..1).toList())
        iter = s246.difference(s1)
        assertEquals(Pair(3, 3), iter.sizeHint())

        val s2 = BTreeSet.fromIterable((-9..2).toList())
        iter = s246.difference(s2)
        assertEquals(Pair(2, 2), iter.sizeHint())
        assertEquals(4, iter.next())
        assertEquals(Pair(1, 1), iter.sizeHint())

        val s23 = BTreeSet.fromIterable(listOf(2, 3))
        iter = s246.difference(s23)
        assertEquals(Pair(1, 3), iter.sizeHint())
        assertEquals(4, iter.next())
        assertEquals(Pair(1, 1), iter.sizeHint())

        val s4 = BTreeSet.fromIterable(listOf(4))
        iter = s246.difference(s4)
        assertEquals(Pair(2, 3), iter.sizeHint())
        assertEquals(2, iter.next())
        assertEquals(Pair(1, 2), iter.sizeHint())
        assertEquals(6, iter.next())
        assertEquals(Pair(0, 0), iter.sizeHint())
        assertFalse(iter.hasNext())

        val s56 = BTreeSet.fromIterable(listOf(5, 6))
        iter = s246.difference(s56)
        assertEquals(Pair(1, 3), iter.sizeHint())
        assertEquals(2, iter.next())
        assertEquals(Pair(0, 2), iter.sizeHint())

        val s6 = BTreeSet.fromIterable((6..19).toList())
        iter = s246.difference(s6)
        assertEquals(Pair(2, 2), iter.sizeHint())
        assertEquals(2, iter.next())
        assertEquals(Pair(1, 1), iter.sizeHint())

        val s7 = BTreeSet.fromIterable((7..19).toList())
        iter = s246.difference(s7)
        assertEquals(Pair(3, 3), iter.sizeHint())
    }

    @Test
    fun testSymmetricDifference() {
        fun checkSymmetricDifference(a: IntArray, b: IntArray, expected: IntArray) {
            check(a, b, expected) { x, y, f ->
                val it = x.symmetricDifference(y)
                var ok = true
                while (it.hasNext()) {
                    if (!f(it.next())) {
                        ok = false
                        break
                    }
                }
                ok
            }
        }

        checkSymmetricDifference(intArrayOf(), intArrayOf(), intArrayOf())
        checkSymmetricDifference(intArrayOf(1, 2, 3), intArrayOf(2), intArrayOf(1, 3))
        checkSymmetricDifference(intArrayOf(2), intArrayOf(1, 2, 3), intArrayOf(1, 3))
        checkSymmetricDifference(
            intArrayOf(1, 3, 5, 9, 11),
            intArrayOf(-2, 3, 9, 14, 22),
            intArrayOf(-2, 1, 5, 11, 14, 22),
        )
    }

    @Test
    fun testSymmetricDifferenceSizeHint() {
        val x = BTreeSet.fromIterable(listOf(2, 4))
        val y = BTreeSet.fromIterable(listOf(1, 2, 3))
        val iter = x.symmetricDifference(y)
        assertEquals(Pair(0, 5), iter.sizeHint())
        assertEquals(1, iter.next())
        assertEquals(Pair(0, 4), iter.sizeHint())
        assertEquals(3, iter.next())
        assertEquals(Pair(0, 1), iter.sizeHint())
    }

    @Test
    fun testUnion() {
        fun checkUnion(a: IntArray, b: IntArray, expected: IntArray) {
            check(a, b, expected) { x, y, f ->
                val it = x.union(y)
                var ok = true
                while (it.hasNext()) {
                    if (!f(it.next())) {
                        ok = false
                        break
                    }
                }
                ok
            }
        }

        checkUnion(intArrayOf(), intArrayOf(), intArrayOf())
        checkUnion(intArrayOf(1, 2, 3), intArrayOf(2), intArrayOf(1, 2, 3))
        checkUnion(intArrayOf(2), intArrayOf(1, 2, 3), intArrayOf(1, 2, 3))
        checkUnion(
            intArrayOf(1, 3, 5, 9, 11, 16, 19, 24),
            intArrayOf(-2, 1, 5, 9, 13, 19),
            intArrayOf(-2, 1, 3, 5, 9, 11, 13, 16, 19, 24),
        )
    }

    @Test
    fun testUnionSizeHint() {
        val x = BTreeSet.fromIterable(listOf(2, 4))
        val y = BTreeSet.fromIterable(listOf(1, 2, 3))
        val iter = x.union(y)
        assertEquals(Pair(3, 5), iter.sizeHint())
        assertEquals(1, iter.next())
        assertEquals(Pair(2, 4), iter.sizeHint())
        assertEquals(2, iter.next())
        assertEquals(Pair(1, 2), iter.sizeHint())
    }

    @Test
    // Only tests the simple function definition with respect to intersection.
    fun testIsDisjoint() {
        val one = BTreeSet<Int>().also { it.insert(1) }
        val two = BTreeSet<Int>().also { it.insert(2) }
        assertTrue(one.isDisjoint(two))
    }

    @Test
    // Also implicitly tests the trivial function definition of `isSuperset`.
    fun testIsSubset() {
        fun isSubset(a: IntArray, b: IntArray): Boolean {
            val setA = BTreeSet<Int>()
            for (v in a) setA.insert(v)
            val setB = BTreeSet<Int>()
            for (v in b) setB.insert(v)
            return setA.isSubset(setB)
        }

        assertEquals(isSubset(intArrayOf(), intArrayOf()), true)
        assertEquals(isSubset(intArrayOf(), intArrayOf(1, 2)), true)
        assertEquals(isSubset(intArrayOf(0), intArrayOf(1, 2)), false)
        assertEquals(isSubset(intArrayOf(1), intArrayOf(1, 2)), true)
        assertEquals(isSubset(intArrayOf(2), intArrayOf(1, 2)), true)
        assertEquals(isSubset(intArrayOf(3), intArrayOf(1, 2)), false)
        assertEquals(isSubset(intArrayOf(1, 2), intArrayOf(1)), false)
        assertEquals(isSubset(intArrayOf(1, 2), intArrayOf(1, 2)), true)
        assertEquals(isSubset(intArrayOf(1, 2), intArrayOf(2, 3)), false)
        assertEquals(
            isSubset(
                intArrayOf(-5, 11, 22, 33, 40, 42),
                intArrayOf(-12, -5, 11, 14, 22, 23, 33, 34, 38, 39, 40, 42),
            ),
            true,
        )
        assertEquals(
            isSubset(
                intArrayOf(-5, 11, 22, 33, 40, 42),
                intArrayOf(-12, -5, 11, 14, 22, 23, 34, 38),
            ),
            false,
        )

        val large = IntArray(100) { it }
        assertEquals(isSubset(intArrayOf(), large), true)
        assertEquals(isSubset(large, intArrayOf()), false)
        assertEquals(isSubset(intArrayOf(-1), large), false)
        assertEquals(isSubset(intArrayOf(0), large), true)
        assertEquals(isSubset(intArrayOf(1, 2), large), true)
        assertEquals(isSubset(intArrayOf(99, 100), large), false)
    }

    @Test
    fun testIsSuperset() {
        fun isSuperset(a: IntArray, b: IntArray): Boolean {
            val setA = BTreeSet<Int>()
            for (v in a) setA.insert(v)
            val setB = BTreeSet<Int>()
            for (v in b) setB.insert(v)
            return setA.isSuperset(setB)
        }

        assertEquals(isSuperset(intArrayOf(), intArrayOf()), true)
        assertEquals(isSuperset(intArrayOf(), intArrayOf(1, 2)), false)
        assertEquals(isSuperset(intArrayOf(0), intArrayOf(1, 2)), false)
        assertEquals(isSuperset(intArrayOf(1), intArrayOf(1, 2)), false)
        assertEquals(isSuperset(intArrayOf(4), intArrayOf(1, 2)), false)
        assertEquals(isSuperset(intArrayOf(1, 4), intArrayOf(1, 2)), false)
        assertEquals(isSuperset(intArrayOf(1, 2), intArrayOf(1, 2)), true)
        assertEquals(isSuperset(intArrayOf(1, 2, 3), intArrayOf(1, 3)), true)
        assertEquals(isSuperset(intArrayOf(1, 2, 3), intArrayOf()), true)
        assertEquals(isSuperset(intArrayOf(-1, 1, 2, 3), intArrayOf(-1, 3)), true)

        val large = IntArray(100) { it }
        assertEquals(isSuperset(intArrayOf(), large), false)
        assertEquals(isSuperset(large, intArrayOf()), true)
        assertEquals(isSuperset(large, intArrayOf(1)), true)
        assertEquals(isSuperset(large, intArrayOf(50, 99)), true)
        assertEquals(isSuperset(large, intArrayOf(100)), false)
        assertEquals(isSuperset(large, intArrayOf(0, 99)), true)
        assertEquals(isSuperset(intArrayOf(-1), large), false)
        assertEquals(isSuperset(intArrayOf(0), large), false)
        assertEquals(isSuperset(intArrayOf(99, 100), large), false)
    }

    @Test
    fun testRetain() {
        val set = BTreeSet.fromIterable(listOf(1, 2, 3, 4, 5, 6))
        set.retain { it % 2 == 0 }
        assertEquals(3, set.len())
        assertTrue(set.contains(2))
        assertTrue(set.contains(4))
        assertTrue(set.contains(6))
    }

    @Test
    fun testExtractIf() {
        val x = BTreeSet<Int>().also { it.insert(1) }
        val y = BTreeSet<Int>().also { it.insert(1) }

        val xIt = x.extractIf(RangeFull) { true }
        while (xIt.hasNext()) xIt.next()
        val yIt = y.extractIf(RangeFull) { false }
        while (yIt.hasNext()) yIt.next()
        assertEquals(x.len(), 0)
        assertEquals(y.len(), 1)
    }

    @Test
    fun testExtractIfDropPanicLeak() {
        val a = CrashTestDummy(0)
        val b = CrashTestDummy(1)
        val c = CrashTestDummy(2)
        val set = BTreeSet<CrashTestDummyRef>()
        set.insert(a.spawn(Panic.Never))
        set.insert(b.spawn(Panic.InDrop))
        set.insert(c.spawn(Panic.Never))

        // Iteration triggers query(true) for each yielded element, then the explicit drop hook
        // can throw for `b`; swallow that exception and continue the postconditions.
        try {
            val it = set.extractIf(RangeFull) { d -> d.query(true) }
            while (it.hasNext()) {
                it.next().drop()
            }
        } catch (_: Throwable) {
            // Swallow drop failure.
        }

        assertEquals(a.queried(), 1)
        assertEquals(b.queried(), 1)
        assertEquals(c.queried(), 0)
        assertEquals(a.dropped(), 1)
        assertEquals(b.dropped(), 0)
        assertEquals(c.dropped(), 0)
    }

    @Test
    fun testExtractIfPredPanicLeak() {
        val a = CrashTestDummy(0)
        val b = CrashTestDummy(1)
        val c = CrashTestDummy(2)
        val set = BTreeSet<CrashTestDummyRef>()
        set.insert(a.spawn(Panic.Never))
        set.insert(b.spawn(Panic.InQuery))
        set.insert(c.spawn(Panic.InQuery))

        try {
            val it = set.extractIf(RangeFull) { d -> d.query(true) }
            while (it.hasNext()) {
                it.next().drop()
            }
        } catch (_: Throwable) {
            // predicate panic on `b` is caught and iteration stops.
        }

        assertEquals(a.queried(), 1)
        // Note: in Rust the panic occurs inside `query`, before `dummy.queried` is incremented for `b`.
        // The Kotlin `query()` panics first too, so `b.queried()` stays 0.
        assertEquals(b.queried(), 0)
        assertEquals(c.queried(), 0)
        assertEquals(a.dropped(), 1)
        assertEquals(b.dropped(), 0)
        assertEquals(c.dropped(), 0)
        assertEquals(set.len(), 2)
    }

    @Test
    fun testClear() {
        val x = BTreeSet<Int>()
        x.insert(1)

        x.clear()
        assertTrue(x.isEmpty())
    }

    @Test
    fun testRemove() {
        val x = BTreeSet<Int>()
        assertTrue(x.isEmpty())

        x.insert(1)
        x.insert(2)
        x.insert(3)
        x.insert(4)

        assertEquals(x.remove(2), true)
        assertEquals(x.remove(0), false)
        assertEquals(x.remove(5), false)
        assertEquals(x.remove(1), true)
        assertEquals(x.remove(2), false)
        assertEquals(x.remove(3), true)
        assertEquals(x.remove(4), true)
        assertEquals(x.remove(4), false)
        assertTrue(x.isEmpty())
    }

    @Test
    fun testZip() {
        val x = BTreeSet<Int>()
        x.insert(5)
        x.insert(12)
        x.insert(11)

        val y = BTreeSet<String>()
        y.insert("foo")
        y.insert("bar")

        val z = x.iter().asSequence().zip(y.iter().asSequence()).iterator()

        assertEquals(z.next(), Pair(5, "bar"))
        assertEquals(z.next(), Pair(11, "foo"))
        assertFalse(z.hasNext())
    }

    @Test
    fun testFromIter() {
        val xs = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)

        val set = BTreeSet.fromIter(xs.asIterable())

        for (x in xs) {
            assertTrue(set.contains(x))
        }
    }

    @Test
    fun testShow() {
        val set = BTreeSet<Int>()
        val empty = BTreeSet<Int>()

        set.insert(1)
        set.insert(2)

        val setStr = set.toString()

        assertEquals(setStr, "{1, 2}")
        assertEquals(empty.toString(), "{}")
    }

    @Test
    fun testExtendRef() {
        val a = BTreeSet<Int>()
        a.insert(1)

        a.extend(listOf(2, 3, 4))

        assertEquals(a.len(), 4)
        assertTrue(a.contains(1))
        assertTrue(a.contains(2))
        assertTrue(a.contains(3))
        assertTrue(a.contains(4))

        val b = BTreeSet<Int>()
        b.insert(5)
        b.insert(6)

        a.extend(b)

        assertEquals(a.len(), 6)
        assertTrue(a.contains(1))
        assertTrue(a.contains(2))
        assertTrue(a.contains(3))
        assertTrue(a.contains(4))
        assertTrue(a.contains(5))
        assertTrue(a.contains(6))
    }

    @Test
    fun testRecovery() {
        // Mirrors the upstream `Foo` struct that compares by string but prints by both fields.
        // Equality is by `name` only; the integer field is incidental data.
        data class Foo(val name: String, val data: Int) : Comparable<Foo> {
            override fun compareTo(other: Foo): Int = name.compareTo(other.name)
            override fun equals(other: Any?): Boolean {
                if (other !is Foo) return false
                return name == other.name
            }
            override fun hashCode(): Int = name.hashCode()
        }

        val s = BTreeSet<Foo>()
        assertNull(s.replace(Foo("a", 1)))
        assertEquals(s.len(), 1)
        assertEquals(s.replace(Foo("a", 2)), Foo("a", 1))
        assertEquals(s.len(), 1)

        run {
            val it = s.iter()
            assertEquals(it.next(), Foo("a", 2))
            assertFalse(it.hasNext())
        }

        assertEquals(s.get(Foo("a", 1)), Foo("a", 2))
        assertEquals(s.take(Foo("a", 1)), Foo("a", 2))
        assertEquals(s.len(), 0)

        assertNull(s.get(Foo("a", 1)))
        assertNull(s.take(Foo("a", 1)))

        assertFalse(s.iter().hasNext())
    }

    private fun randData(len: Int): List<Int> {
        var state = 1
        return List(len) {
            state = (state * 1103515245 + 12345).toUInt().toInt() and 0x7FFFFFFF
            state
        }
    }

    @Test
    fun testSplitOffEmptyRight() {
        val data = randData(173)

        val set = BTreeSet.fromIterable(data)
        val right = set.splitOff(data.maxOrNull()!! + 1)

        assertEquals(data.distinct().sorted(), set.iter().asSequence().toList())
        assertEquals(emptyList(), right.iter().asSequence().toList())
    }

    @Test
    fun testSplitOffEmptyLeft() {
        val data = randData(314)

        val set = BTreeSet.fromIterable(data)
        val right = set.splitOff(data.minOrNull()!!)

        assertEquals(emptyList(), set.iter().asSequence().toList())
        assertEquals(data.distinct().sorted(), right.iter().asSequence().toList())
    }

    @Test
    fun testSplitOffLargeRandomSorted() {
        val data = randData(1529).distinct().sorted()

        val set = BTreeSet.fromIterable(data)
        val key = data[data.size / 2]
        val right = set.splitOff(key)

        assertEquals(data.filter { it < key }, set.iter().asSequence().toList())
        assertEquals(data.filter { it >= key }, right.iter().asSequence().toList())
    }

    @Test
    fun fromArray() {
        val set = BTreeSet.from(1, 2, 3, 4)
        val unorderedDuplicates = BTreeSet.from(4, 1, 4, 3, 2)
        assertEquals(set, unorderedDuplicates)
    }

    @Test
    fun testAppend() {
        val a = BTreeSet<Int>()
        a.insert(1)
        a.insert(2)
        a.insert(3)

        val b = BTreeSet<Int>()
        b.insert(3)
        b.insert(4)
        b.insert(5)

        a.append(b)

        assertEquals(a.len(), 5)
        assertEquals(b.len(), 0)

        assertEquals(a.contains(1), true)
        assertEquals(a.contains(2), true)
        assertEquals(a.contains(3), true)
        assertEquals(a.contains(4), true)
        assertEquals(a.contains(5), true)
    }

    @Test
    fun testFirstLast() {
        val a = BTreeSet<Int>()
        assertNull(a.first())
        assertNull(a.last())
        a.insert(1)
        assertEquals(a.first(), 1)
        assertEquals(a.last(), 1)
        a.insert(2)
        assertEquals(a.first(), 1)
        assertEquals(a.last(), 2)
        for (i in 3..12) {
            a.insert(i)
        }
        assertEquals(a.first(), 1)
        assertEquals(a.last(), 12)
        assertEquals(a.popFirst(), 1)
        assertEquals(a.popLast(), 12)
        assertEquals(a.popFirst(), 2)
        assertEquals(a.popLast(), 11)
        assertEquals(a.popFirst(), 3)
        assertEquals(a.popLast(), 10)
        assertEquals(a.popFirst(), 4)
        assertEquals(a.popFirst(), 5)
        assertEquals(a.popFirst(), 6)
        assertEquals(a.popFirst(), 7)
        assertEquals(a.popFirst(), 8)
        // Clone via a fresh-set copy so we don't disturb `a` for the next pop.
        run {
            val clone = BTreeSet<Int>()
            for (v in a) clone.insert(v)
            assertEquals(clone.popLast(), 9)
        }
        assertEquals(a.popFirst(), 9)
        assertNull(a.popFirst())
        assertNull(a.popLast())
    }

    @Test
    fun testRangePanic1() {
        val set = BTreeSet<Int>()
        set.insert(3)
        set.insert(5)
        set.insert(8)

        assertFails {
            set.range(boundsPair(Bound.Included(8), Bound.Included(3)))
        }
    }

    @Test
    fun testRangePanic2() {
        val set = BTreeSet<Int>()
        set.insert(3)
        set.insert(5)
        set.insert(8)

        assertFails {
            set.range(boundsPair(Bound.Excluded(5), Bound.Excluded(5)))
        }
    }
}
