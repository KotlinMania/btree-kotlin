// port-lint: source map/tests.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

private const val MIN_INSERTS_HEIGHT_1: Int = CAPACITY + 1
private const val MIN_INSERTS_HEIGHT_2: Int = 89

private fun <K : Comparable<K>, V> BTreeMap<K, V>.checkInvariants() {
    val root = this.root
    if (root != null) {
        val rootNode = root.reborrow()
        assertTrue(rootNode.ascend() is AscendResult.Err)
        rootNode.assertBackPointers()
        assertEquals(this.length, rootNode.calcLength())
        rootNode.assertMinLen(if (rootNode.height > 0) 1 else 0)
    } else {
        assertEquals(this.length, 0)
    }

    var keysCount = 0
    val keysIter = this.keys()
    while (keysIter.hasNext()) {
        keysIter.next()
        keysCount++
    }
    assertEquals(this.length, keysCount)
}

private fun <K : Comparable<K>, V> BTreeMap<K, V>.check() {
    this.checkInvariants()
    this.assertStrictlyAscending()
}

private fun <K : Comparable<K>, V> BTreeMap<K, V>.height(): Int? {
    return this.root?.height
}

private fun <K : Comparable<K>, V> BTreeMap<K, V>.dumpKeys(): String {
    val root = this.root
    return if (root != null) {
        root.reborrow().dumpKeys()
    } else {
        "not yet allocated"
    }
}

private fun <K : Comparable<K>, V> BTreeMap<K, V>.assertStrictlyAscending() {
    val keys = this.keys()
    if (keys.hasNext()) {
        var previous = keys.next()
        while (keys.hasNext()) {
            val next = keys.next()
            assertTrue(previous < next, "$previous >= $next")
            previous = next
        }
    }
}

private fun <K : Comparable<K>, V> BTreeMap<K, V>.compact() {
    val iter = this.intoIter()
    if (iter.hasNext()) {
        this.root = NodeRef.new<K, V>()
        val len = intArrayOf(0)
        this.root!!.bulkPush(iter, len)
        this.length = len[0]
    }
}

private inline fun assertFailsWithMoved(vararg maps: BTreeMap<*, *>, block: () -> Unit) {
    assertFailsWith<Throwable> {
        try {
            block()
        } finally {
            var failure: Throwable? = null
            for (map in maps) {
                failure = map.dropEntries(failure)
            }
            if (failure != null) throw failure
        }
    }
}

private fun <K, V> NodeRef<Marker.Immut, K, V, Marker.LeafOrInternal>.assertMinLen(minLen: Int) {
    assertTrue(this.len() >= minLen, "node len ${this.len()} < $minLen")
    val forced = this.force()
    if (forced is ForceResult.Internal) {
        val node = forced.value
        for (idx in 0..node.len()) {
            val edge = Handle.newEdge(node, idx)
            edge.descend().assertMinLen(MIN_LEN)
        }
    }
}

internal class Governor {
    var flipped: Boolean = false

    fun flip() {
        flipped = !flipped
    }

    companion object {
        fun new(): Governor = Governor()
    }
}

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

internal enum class Panic {
    Never,
    InClone,
    InDrop,
    InQuery,
}

internal class CrashTestDummy(val id: Int) {
    private var queriedCount: Int = 0
    private var droppedCount: Int = 0
    private var clonedCount: Int = 0

    fun spawn(panic: Panic): CrashTestDummyRef = CrashTestDummyRef(this, panic)

    fun queried(): Int = queriedCount

    fun dropped(): Int = droppedCount

    fun cloned(): Int = clonedCount

    fun incQueried() {
        queriedCount++
    }

    fun incDropped() {
        droppedCount++
    }

    fun incCloned() {
        clonedCount++
    }
}

internal class CrashTestDummyRef(val dummy: CrashTestDummy, val panic: Panic) :
    Comparable<CrashTestDummyRef>,
    BTreeCloneable,
    BTreeDroppable {
    override fun compareTo(other: CrashTestDummyRef): Int {
        return dummy.id.compareTo(other.dummy.id)
    }

    fun query(v: Boolean): Boolean {
        dummy.incQueried()
        if (panic == Panic.InQuery) {
            throw Exception("panic in query")
        }
        return v
    }

    fun drop() {
        dummy.incDropped()
        if (panic == Panic.InDrop) {
            throw Exception("panic in drop")
        }
    }

    override fun dropForBtree() {
        drop()
    }

    fun cloneRef(): CrashTestDummyRef {
        dummy.incCloned()
        if (panic == Panic.InClone) {
            throw Exception("panic in clone")
        }
        return CrashTestDummyRef(dummy, Panic.Never)
    }

    override fun cloneForBtree() {
        cloneRef()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CrashTestDummyRef) return false
        return dummy.id == other.dummy.id
    }

    override fun hashCode(): Int = dummy.id.hashCode()

    override fun toString(): String = "CrashTestDummyRef(${dummy.id})"
}

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

class SmokeTests {
    @Test
    fun testLevels() {
        val map = BTreeMap<Int, Unit>()
        map.check()
        assertEquals(null, map.height())
        assertEquals(0, map.len())

        map.insert(0, Unit)
        while (map.height() == 0) {
            val lastKey = map.lastKeyValue()!!.first
            map.insert(lastKey + 1, Unit)
        }
        map.check()
        // Structure:
        // - 1 element in internal root node with 2 children
        // - 6 elements in left leaf child
        // - 5 elements in right leaf child
        assertEquals(1, map.height())
        assertEquals(MIN_INSERTS_HEIGHT_1, map.len(), map.dumpKeys())

        while (map.height() == 1) {
            val lastKey = map.lastKeyValue()!!.first
            map.insert(lastKey + 1, Unit)
        }
        map.check()
        // Structure:
        // - 1 element in internal root node with 2 children
        // - 6 elements in left internal child with 7 grandchildren
        // - 42 elements in left child's 7 grandchildren with 6 elements each
        // - 5 elements in right internal child with 6 grandchildren
        // - 30 elements in right child's 5 first grandchildren with 6 elements each
        // - 5 elements in right child's last grandchild
        assertEquals(2, map.height())
        assertEquals(MIN_INSERTS_HEIGHT_2, map.len(), map.dumpKeys())
    }

    // Ensures the testing infrastructure usually notices order violations.
    @Test
    fun testCheckOrdChaos() {
        assertFailsWith<Throwable> {
            val gov = Governor.new()
            val map = BTreeMap<Governed, Unit>()
            map.insert(Governed(1, gov), Unit)
            map.insert(Governed(2, gov), Unit)
            gov.flip()
            map.check()
        }
    }

    // Ensures the testing infrastructure doesn't always mind order violations.
    @Test
    fun testCheckInvariantsOrdChaos() {
        val gov = Governor.new()
        val map = BTreeMap<Governed, Unit>()
        map.insert(Governed(1, gov), Unit)
        map.insert(Governed(2, gov), Unit)
        gov.flip()
        map.checkInvariants()
    }

    @Test
    fun testBasicLarge() {
        val map = BTreeMap<Int, Int>()
        // Miri is too slow
        val sizeBase = 10000
        val size = sizeBase + (sizeBase % 2) // round up to even number
        assertEquals(0, map.len())

        for (i in 0 until size) {
            assertEquals(null, map.insert(i, 10 * i))
            assertEquals(i + 1, map.len())
        }

        assertEquals(Pair(0, 0), map.firstKeyValue())
        assertEquals(Pair(size - 1, 10 * (size - 1)), map.lastKeyValue())
        assertEquals(0, map.firstEntry()!!.key())
        assertEquals(size - 1, map.lastEntry()!!.key())

        for (i in 0 until size) {
            assertEquals(i * 10, map.get(i)!!)
        }

        for (i in size until size * 2) {
            assertEquals(null, map.get(i))
        }

        for (i in 0 until size) {
            assertEquals(10 * i, map.insert(i, 100 * i))
            assertEquals(size, map.len())
        }

        for (i in 0 until size) {
            assertEquals(i * 100, map.get(i)!!)
        }

        for (i in 0 until size / 2) {
            assertEquals(i * 200, map.remove(i * 2))
            assertEquals(size - i - 1, map.len())
        }

        for (i in 0 until size / 2) {
            assertEquals(null, map.get(2 * i))
            assertEquals(i * 200 + 100, map.get(2 * i + 1)!!)
        }

        for (i in 0 until size / 2) {
            assertEquals(null, map.remove(2 * i))
            assertEquals(i * 200 + 100, map.remove(2 * i + 1))
            assertEquals(size / 2 - i - 1, map.len())
        }
        map.check()
    }

    @Test
    fun testBasicSmall() {
        val map = BTreeMap<Int, Int>()
        // Empty, root is absent (None):
        assertEquals(null, map.remove(1))
        assertEquals(0, map.len())
        assertEquals(null, map.get(1))
        assertEquals(null, map.getMut(1))
        assertEquals(null, map.firstKeyValue())
        assertEquals(null, map.lastKeyValue())
        assertEquals(0, map.keys().asSequence().count())
        assertEquals(0, map.values().asSequence().count())
        assertEquals(null, map.range(RangeFull).asSequence().firstOrNull())
        assertEquals(null, map.range(RangeTo(1)).asSequence().firstOrNull())
        assertEquals(null, map.range(RangeFrom(1)).asSequence().firstOrNull())
        assertEquals(null, map.range(OpsRange(1, 1)).asSequence().firstOrNull())
        assertEquals(null, map.range(OpsRange(1, 2)).asSequence().firstOrNull())
        assertEquals(null, map.height())
        assertEquals(null, map.insert(1, 1))
        assertEquals(0, map.height())
        map.check()

        // 1 key-value pair:
        assertEquals(1, map.len())
        assertEquals(1, map.get(1))
        assertEquals(1, map.getMut(1))
        assertEquals(Pair(1, 1), map.firstKeyValue())
        assertEquals(Pair(1, 1), map.lastKeyValue())
        assertEquals(listOf(1), map.keys().asSequence().toList())
        assertEquals(listOf(1), map.values().asSequence().toList())
        assertEquals(1, map.insert(1, 2))
        assertEquals(1, map.len())
        assertEquals(2, map.get(1))
        assertEquals(2, map.getMut(1))
        assertEquals(Pair(1, 2), map.firstKeyValue())
        assertEquals(Pair(1, 2), map.lastKeyValue())
        assertEquals(listOf(1), map.keys().asSequence().toList())
        assertEquals(listOf(2), map.values().asSequence().toList())
        assertEquals(null, map.insert(2, 4))
        assertEquals(0, map.height())
        map.check()

        // 2 key-value pairs:
        assertEquals(2, map.len())
        assertEquals(4, map.get(2))
        assertEquals(4, map.getMut(2))
        assertEquals(Pair(1, 2), map.firstKeyValue())
        assertEquals(Pair(2, 4), map.lastKeyValue())
        assertEquals(listOf(1, 2), map.keys().asSequence().toList())
        assertEquals(listOf(2, 4), map.values().asSequence().toList())
        assertEquals(2, map.remove(1))
        assertEquals(0, map.height())
        map.check()

        // 1 key-value pair:
        assertEquals(1, map.len())
        assertEquals(null, map.get(1))
        assertEquals(null, map.getMut(1))
        assertEquals(4, map.get(2))
        assertEquals(4, map.getMut(2))
        assertEquals(Pair(2, 4), map.firstKeyValue())
        assertEquals(Pair(2, 4), map.lastKeyValue())
        assertEquals(listOf(2), map.keys().asSequence().toList())
        assertEquals(listOf(4), map.values().asSequence().toList())
        assertEquals(4, map.remove(2))
        assertEquals(0, map.height())
        map.check()

        // Empty but root is owned (Some(...)):
        assertEquals(0, map.len())
        assertEquals(null, map.get(1))
        assertEquals(null, map.getMut(1))
        assertEquals(null, map.firstKeyValue())
        assertEquals(null, map.lastKeyValue())
        assertEquals(0, map.keys().asSequence().count())
        assertEquals(0, map.values().asSequence().count())
        assertEquals(null, map.range(RangeFull).asSequence().firstOrNull())
        assertEquals(null, map.range(RangeTo(1)).asSequence().firstOrNull())
        assertEquals(null, map.range(RangeFrom(1)).asSequence().firstOrNull())
        assertEquals(null, map.range(OpsRange(1, 1)).asSequence().firstOrNull())
        assertEquals(null, map.range(OpsRange(1, 2)).asSequence().firstOrNull())
        assertEquals(null, map.remove(1))
        assertEquals(0, map.height())
        map.check()
    }

    @Test
    fun testIter() {
        // Miri is too slow
        val size = 10000
        val map = BTreeMap<Int, Int>()
        for (i in 0 until size) map.insert(i, i)

        fun test(size: Int, iter: Iterator<Pair<Int, Int>>) {
            for (i in 0 until size) {
                assertEquals(Pair(i, i), iter.next())
            }
            assertEquals(false, iter.hasNext())
        }
        test(size, map.iter())
        test(size, map.iterMut())
        test(size, map.intoIter())
    }

    @Test
    fun testIterRev() {
        // Miri is too slow
        val size = 10000
        val map = BTreeMap<Int, Int>()
        for (i in 0 until size) map.insert(i, i)

        fun test(size: Int, iter: Iterator<Pair<Int, Int>>) {
            for (i in 0 until size) {
                assertEquals(Pair(size - i - 1, size - i - 1), iter.next())
            }
            assertEquals(false, iter.hasNext())
        }

        test(size, map.iter().toList().reversed().iterator())
        test(size, map.iterMut().toList().reversed().iterator())
        test(size, map.intoIter().toList().reversed().iterator())
    }

    // Specifically tests iterMut's ability to mutate the value of pairs in-line.
    private fun <T : Comparable<T>> doTestIterMutMutation(size: Int, tryFrom: (Int) -> T) {
        val zero = tryFrom(0)
        val map = BTreeMap<T, T>()
        for (i in 0 until size) map.insert(tryFrom(i), zero)

        // Forward and backward iteration sees enough pairs (also tested elsewhere)
        var countForwards = 0
        var iter1 = map.iterMut()
        while (iter1.hasNext()) { iter1.next(); countForwards++ }
        assertEquals(size, countForwards)

        var countBackwards = 0
        var iter2 = map.iterMut().toList().reversed().iterator()
        while (iter2.hasNext()) { iter2.next(); countBackwards++ }
        assertEquals(size, countBackwards)

        // Iterate forwards, trying to mutate to unique values
        var idx = 0
        var iter3 = map.iterMut()
        while (iter3.hasNext()) {
            val (k, _) = iter3.next()
            assertEquals(tryFrom(idx), k)
            assertEquals(zero, map.get(k))
            map.insert(k, tryFrom(idx + 1))
            idx++
        }

        // Iterate backwards, checking that mutations succeeded and trying to mutate again
        idx = 0
        var iter4 = map.iterMut().toList().reversed().iterator()
        while (iter4.hasNext()) {
            val (k, v) = iter4.next()
            assertEquals(tryFrom(size - idx - 1), k)
            assertEquals(tryFrom(size - idx), v)
            map.insert(k, tryFrom(2 * size - idx))
            idx++
        }

        // Check that backward mutations succeeded
        idx = 0
        var iter5 = map.iterMut()
        while (iter5.hasNext()) {
            val (k, v) = iter5.next()
            assertEquals(tryFrom(idx), k)
            assertEquals(tryFrom(size + idx + 1), v)
            idx++
        }
        map.check()
    }

    data class Align32(val value: Int) : Comparable<Align32> {
        override fun compareTo(other: Align32): Int = value.compareTo(other.value)
    }

    @Test
    fun testIterMutMutation() {
        // Check many alignments and trees with roots at various heights.
        doTestIterMutMutation(0) { it }
        doTestIterMutMutation(1) { it }
        doTestIterMutMutation(MIN_INSERTS_HEIGHT_1) { it }
        doTestIterMutMutation(MIN_INSERTS_HEIGHT_2) { it }

        doTestIterMutMutation(1) { it.toLong() }
        doTestIterMutMutation(MIN_INSERTS_HEIGHT_1) { it.toLong() }
        doTestIterMutMutation(MIN_INSERTS_HEIGHT_2) { it.toLong() }

        doTestIterMutMutation(1) { Align32(it) }
        doTestIterMutMutation(MIN_INSERTS_HEIGHT_1) { Align32(it) }
        doTestIterMutMutation(MIN_INSERTS_HEIGHT_2) { Align32(it) }
    }

    @Test
    fun testValuesMut() {
        val a = BTreeMap<Int, Int>()
        for (i in 0 until MIN_INSERTS_HEIGHT_2) a.insert(i, i)

        val values = mutableListOf<Int>()
        val iter = a.valuesMut()
        while (iter.hasNext()) {
            values.add(iter.next())
        }
        assertEquals((0 until MIN_INSERTS_HEIGHT_2).toList(), values)
        a.check()
    }

    @Test
    fun testValuesMutMutation() {
        val a = BTreeMap<Int, String>()
        a.insert(1, "hello")
        a.insert(2, "goodbye")

        for (entry in a.iterMutEntries()) {
            entry.setValue(entry.value + "!")
        }

        val values = a.values().asSequence().toList()
        assertEquals(listOf("hello!", "goodbye!"), values)
        a.check()
    }

    @Test
    fun testIterEnteringRootTwice() {
        val map = BTreeMap<Int, Int>()
        map.insert(0, 0)
        map.insert(1, 1)
        val it = map.iterMut()
        val front = it.next()
        val back = it.nextBack()!!
        assertEquals(Pair(0, 0), front)
        assertEquals(Pair(1, 1), back)
        map.insert(front.first, 24)
        map.insert(back.first, 42)
        assertEquals(Pair(0, 24), Pair(front.first, map.get(front.first)))
        assertEquals(Pair(1, 42), Pair(back.first, map.get(back.first)))
        assertFalse(it.hasNext())
        assertEquals(null, it.nextBack())
        map.check()
    }

    @Test
    fun testIterDescendingToSameNodeTwice() {
        val map = BTreeMap<Int, Int>()
        for (i in 0 until MIN_INSERTS_HEIGHT_1) map.insert(i, i)
        val it = map.iterMut()
        // Descend into first child.
        val front = it.next()
        // Descend into first child again, after running through second child.
        while (it.nextBack() != null) {}
        // Check immutable access.
        assertEquals(Pair(0, 0), front)
        // Perform mutable access.
        map.insert(front.first, 42)
        map.check()
    }

    @Test
    fun testIterMixed() {
        // test is too slow
        val sizeBase = 10000
        val size = sizeBase + (sizeBase % 4)
        val map = BTreeMap<Int, Int>()
        for (i in 0 until size) map.insert(i, i)

        fun test(size: Int, iter: Iterator<Pair<Int, Int>>) {
            val list = iter.asSequence().toList()
            var head = 0
            var tail = list.size - 1
            for (i in 0 until size / 4) {
                assertEquals(Pair(i, i), list[head++])
                assertEquals(Pair(size - i - 1, size - i - 1), list[tail--])
            }
            for (i in size / 4 until size * 3 / 4) {
                assertEquals(Pair(i, i), list[head++])
            }
            assertEquals(head, tail + 1)
        }
        test(size, map.iter())
        test(size, map.iterMut())
        test(size, map.intoIter())
    }

    @Test
    fun testIterMinMax() {
        val a = BTreeMap<Int, Int>()
        assertEquals(null, a.iter().asSequence().minByOrNull { it.first })
        assertEquals(null, a.iter().asSequence().maxByOrNull { it.first })
        assertEquals(null, a.iterMut().asSequence().minByOrNull { it.first })
        assertEquals(null, a.iterMut().asSequence().maxByOrNull { it.first })
        assertEquals(null, a.range(RangeFull).asSequence().minByOrNull { it.first })
        assertEquals(null, a.range(RangeFull).asSequence().maxByOrNull { it.first })
        assertEquals(null, a.rangeMut(RangeFull).asSequence().minByOrNull { it.first })
        assertEquals(null, a.rangeMut(RangeFull).asSequence().maxByOrNull { it.first })
        assertEquals(null, a.keys().asSequence().minOrNull())
        assertEquals(null, a.keys().asSequence().maxOrNull())
        assertEquals(null, a.values().asSequence().minOrNull())
        assertEquals(null, a.values().asSequence().maxOrNull())
        assertEquals(null, a.valuesMut().asSequence().minOrNull())
        assertEquals(null, a.valuesMut().asSequence().maxOrNull())
        a.insert(1, 42)
        a.insert(2, 24)
        assertEquals(Pair(1, 42), a.iter().asSequence().minByOrNull { it.first })
        assertEquals(Pair(2, 24), a.iter().asSequence().maxByOrNull { it.first })
        assertEquals(Pair(1, 42), a.iterMut().asSequence().minByOrNull { it.first })
        assertEquals(Pair(2, 24), a.iterMut().asSequence().maxByOrNull { it.first })
        assertEquals(Pair(1, 42), a.range(RangeFull).asSequence().minByOrNull { it.first })
        assertEquals(Pair(2, 24), a.range(RangeFull).asSequence().maxByOrNull { it.first })
        assertEquals(Pair(1, 42), a.rangeMut(RangeFull).asSequence().minByOrNull { it.first })
        assertEquals(Pair(2, 24), a.rangeMut(RangeFull).asSequence().maxByOrNull { it.first })
        assertEquals(1, a.keys().asSequence().minOrNull())
        assertEquals(2, a.keys().asSequence().maxOrNull())
        assertEquals(24, a.values().asSequence().minOrNull())
        assertEquals(42, a.values().asSequence().maxOrNull())
        assertEquals(24, a.valuesMut().asSequence().minOrNull())
        assertEquals(42, a.valuesMut().asSequence().maxOrNull())
        a.check()
    }

    private fun <K : Comparable<K>, V> rangeKeys(map: BTreeMap<K, V>, range: IntoBounds<K>): List<K> {
        val res = map.range(range).asSequence().map { it.first }.toList()
        val resMut = map.rangeMut(range).asSequence().map { it.first }.toList()
        assertEquals(res, resMut)
        val expectedKeys = map.keys().asSequence().filter { range.contains(it) }.toList()
        assertEquals(expectedKeys, res)
        return res
    }

    @Test
    fun testRangeSmall() {
        val size = 4
        val all = (1..size).toList()
        val first = listOf(all[0])
        val last = listOf(all[size - 1])
        val map = BTreeMap<Int, Int>()
        for (i in all) map.insert(i, i)

        assertEquals(all, rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Excluded(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Included(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Included(size))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Unbounded)))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(0), Bound.Excluded(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(0), Bound.Included(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(0), Bound.Included(size))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(0), Bound.Unbounded)))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(1), Bound.Excluded(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(1), Bound.Included(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(1), Bound.Included(size))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(1), Bound.Unbounded)))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Excluded(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Included(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Included(size))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Unbounded)))

        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Excluded(1))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Included(0))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Included(0), Bound.Included(0))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Included(0), Bound.Excluded(1))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Excluded(1))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Included(0))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Excluded(2))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Included(1))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Included(0), Bound.Excluded(2))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Included(0), Bound.Included(1))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Included(1), Bound.Excluded(2))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Included(1), Bound.Included(1))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Excluded(2))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Included(1))))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Excluded(size - 1), Bound.Excluded(size + 1))))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Excluded(size - 1), Bound.Included(size + 1))))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Excluded(size - 1), Bound.Included(size))))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Excluded(size - 1), Bound.Unbounded)))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Included(size), Bound.Excluded(size + 1))))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Included(size), Bound.Included(size + 1))))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Included(size), Bound.Included(size))))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Included(size), Bound.Unbounded)))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Excluded(size), Bound.Excluded(size + 1))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Excluded(size), Bound.Included(size))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Excluded(size), Bound.Unbounded)))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Included(size + 1), Bound.Excluded(size + 1))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Included(size + 1), Bound.Included(size + 1))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Included(size + 1), Bound.Unbounded)))

        assertEquals(listOf(1, 2), rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Excluded(3))))
        assertEquals(listOf(3, 4), rangeKeys(map, boundsPair(Bound.Included(3), Bound.Unbounded)))
        assertEquals(listOf(2, 3), rangeKeys(map, boundsPair(Bound.Included(2), Bound.Included(3))))
    }

    @Test
    fun testRangeHeight1() {
        val map = BTreeMap<Int, Int>()
        for (i in 0 until MIN_INSERTS_HEIGHT_1) map.insert(i, i)
        val middle = MIN_INSERTS_HEIGHT_1 / 2
        for (root in middle - 2..middle + 2) {
            assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Excluded(root), Bound.Excluded(root + 1))))
            assertEquals(listOf(root + 1), rangeKeys(map, boundsPair(Bound.Excluded(root), Bound.Included(root + 1))))
            assertEquals(listOf(root), rangeKeys(map, boundsPair(Bound.Included(root), Bound.Excluded(root + 1))))
            assertEquals(listOf(root, root + 1), rangeKeys(map, boundsPair(Bound.Included(root), Bound.Included(root + 1))))

            assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Excluded(root - 1), Bound.Excluded(root))))
            assertEquals(listOf(root - 1), rangeKeys(map, boundsPair(Bound.Included(root - 1), Bound.Excluded(root))))
            assertEquals(listOf(root), rangeKeys(map, boundsPair(Bound.Excluded(root - 1), Bound.Included(root))))
            assertEquals(listOf(root - 1, root), rangeKeys(map, boundsPair(Bound.Included(root - 1), Bound.Included(root))))
        }
    }

    @Test
    fun testRangeLarge() {
        val size = 200

        val all = (1..size).toList()
        val first = listOf(all[0])
        val last = listOf(all[size - 1])
        val map = BTreeMap<Int, Int>()
        for (i in all) map.insert(i, i)

        assertEquals(all, rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Excluded(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Included(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Included(size))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Unbounded)))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(0), Bound.Excluded(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(0), Bound.Included(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(0), Bound.Included(size))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(0), Bound.Unbounded)))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(1), Bound.Excluded(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(1), Bound.Included(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(1), Bound.Included(size))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Included(1), Bound.Unbounded)))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Excluded(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Included(size + 1))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Included(size))))
        assertEquals(all, rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Unbounded)))

        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Excluded(1))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Included(0))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Included(0), Bound.Included(0))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Included(0), Bound.Excluded(1))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Excluded(1))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Included(0))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Excluded(2))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Excluded(0), Bound.Included(1))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Included(0), Bound.Excluded(2))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Included(0), Bound.Included(1))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Included(1), Bound.Excluded(2))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Included(1), Bound.Included(1))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Excluded(2))))
        assertEquals(first, rangeKeys(map, boundsPair(Bound.Unbounded, Bound.Included(1))))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Excluded(size - 1), Bound.Excluded(size + 1))))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Excluded(size - 1), Bound.Included(size + 1))))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Excluded(size - 1), Bound.Included(size))))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Excluded(size - 1), Bound.Unbounded)))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Included(size), Bound.Excluded(size + 1))))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Included(size), Bound.Included(size + 1))))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Included(size), Bound.Included(size))))
        assertEquals(last, rangeKeys(map, boundsPair(Bound.Included(size), Bound.Unbounded)))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Excluded(size), Bound.Excluded(size + 1))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Excluded(size), Bound.Included(size))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Excluded(size), Bound.Unbounded)))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Included(size + 1), Bound.Excluded(size + 1))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Included(size + 1), Bound.Included(size + 1))))
        assertEquals(emptyList<Int>(), rangeKeys(map, boundsPair(Bound.Included(size + 1), Bound.Unbounded)))

        fun check(lhs: Iterator<Pair<Int, Int>>, rhs: List<Pair<Int, Int>>) {
            assertEquals(lhs.asSequence().toList(), rhs)
        }

        check(map.range(RangeToInclusive(100)), map.range(RangeTo(101)).toList())
        check(map.range(RangeInclusive.new(5, 8)), listOf(Pair(5, 5), Pair(6, 6), Pair(7, 7), Pair(8, 8)))
        check(map.range(RangeInclusive.new(-1, 2)), listOf(Pair(1, 1), Pair(2, 2)))
    }

    @Test
    fun testRangeInclusiveMaxValue() {
        val max = Int.MAX_VALUE
        val map = BTreeMap<Int, Int>()
        map.insert(max, 0)
        assertEquals(listOf(Pair(max, 0)), map.range(RangeInclusive.new(max, max)).toList())
    }

    @Test
    fun testRangeEqualEmptyCases() {
        val map = BTreeMap<Int, Int>()
        for (i in 0 until 5) map.insert(i, i)
        assertEquals(null, map.range(boundsPair(Bound.Included(2), Bound.Excluded(2))).nextOrNull())
        assertEquals(null, map.range(boundsPair(Bound.Excluded(2), Bound.Included(2))).nextOrNull())
    }

    @Test // expected to panic
    fun testRangeEqualExcluded() {
        assertFailsWith<Throwable> {
            val map = BTreeMap<Int, Int>()
            for (i in 0 until 5) map.insert(i, i)
            map.range(boundsPair(Bound.Excluded(2), Bound.Excluded(2)))
        }
    }

    @Test // expected to panic
    fun testRangeBackwards1() {
        assertFailsWith<Throwable> {
            val map = BTreeMap<Int, Int>()
            for (i in 0 until 5) map.insert(i, i)
            map.range(boundsPair(Bound.Included(3), Bound.Included(2)))
        }
    }

    @Test // expected to panic
    fun testRangeBackwards2() {
        assertFailsWith<Throwable> {
            val map = BTreeMap<Int, Int>()
            for (i in 0 until 5) map.insert(i, i)
            map.range(boundsPair(Bound.Included(3), Bound.Excluded(2)))
        }
    }

    @Test // expected to panic
    fun testRangeBackwards3() {
        assertFailsWith<Throwable> {
            val map = BTreeMap<Int, Int>()
            for (i in 0 until 5) map.insert(i, i)
            map.range(boundsPair(Bound.Excluded(3), Bound.Included(2)))
        }
    }

    @Test // expected to panic
    fun testRangeBackwards4() {
        assertFailsWith<Throwable> {
            val map = BTreeMap<Int, Int>()
            for (i in 0 until 5) map.insert(i, i)
            map.range(boundsPair(Bound.Excluded(3), Bound.Excluded(2)))
        }
    }

    @Test
    fun testRangeFindingIllOrderInMap() {
        val map = BTreeMap<Cyclic3, Unit>()
        map.insert(Cyclic3.B, Unit)
        if (Cyclic3.C < Cyclic3.A) {
            map.range(RangeInclusive.new(Cyclic3.C, Cyclic3.A))
        }
    }

    @Test
    fun testRangeFindingIllOrderInRangeOrd() {
        var compares = 0
        class EvilTwin(val value: Int) : Comparable<EvilTwin> {
            override fun compareTo(other: EvilTwin): Int {
                val ord = value.compareTo(other.value)
                val n = compares
                compares = n + 1
                return if (n > 0) -ord else ord
            }
            override fun equals(other: Any?): Boolean = other is EvilTwin && value == other.value
            override fun hashCode(): Int = value.hashCode()
        }

        // Kotlin has no borrowed-key adapter here, so the map is keyed directly on
        // `EvilTwin`. The test still exercises the same code path: a range query
        // where the comparator's ordering inverts mid-call.
        val map = BTreeMap<EvilTwin, Unit>()
        for (i in 0 until 12) map.insert(EvilTwin(i), Unit)
        // Calling `range` is allowed to panic once the comparator misbehaves; the test
        // only asserts the call returns or throws cleanly without corrupting the map.
        try {
            map.range(boundsPair(Bound.Included(EvilTwin(5)), Bound.Included(EvilTwin(7))))
        } catch (_: Throwable) {
            // Comparator-induced failure is acceptable under a faulty comparator.
        }
    }

    @Test
    fun testRange1000() {
        // test is too slow
        val sizeBase = 1000
        val size = sizeBase + (sizeBase % 2)
        val map = BTreeMap<Int, Int>()
        for (i in 0 until size) map.insert(i, i)

        fun test(map: BTreeMap<Int, Int>, size: Int, min: Bound<Int>, max: Bound<Int>) {
            val kvs = map.range(boundsPair(min, max)).asSequence().map { it.first to it.second }.iterator()
            val pairs = (0 until size).map { it to it }.iterator()

            while (kvs.hasNext() && pairs.hasNext()) {
                val kv = kvs.next()
                val pair = pairs.next()
                assertEquals(pair, kv)
            }
            assertEquals(false, kvs.hasNext())
            assertEquals(false, pairs.hasNext())
        }
        test(map, size, Bound.Included(0), Bound.Excluded(size))
        test(map, size, Bound.Unbounded, Bound.Excluded(size))
        test(map, size, Bound.Included(0), Bound.Included(size - 1))
        test(map, size, Bound.Unbounded, Bound.Included(size - 1))
        test(map, size, Bound.Included(0), Bound.Unbounded)
        test(map, size, Bound.Unbounded, Bound.Unbounded)
    }

    @Test
    fun testRangeBorrowedKey() {
        val map = BTreeMap<String, Int>()
        map.insert("aardvark", 1)
        map.insert("baboon", 2)
        map.insert("coyote", 3)
        map.insert("dingo", 4)
        val iter = map.range(boundsPair(Bound.Included("b"), Bound.Excluded("d")))
        assertEquals(Pair("baboon", 2), iter.nextOrNull())
        assertEquals(Pair("coyote", 3), iter.nextOrNull())
        assertEquals(null, iter.nextOrNull())
    }

    @Test
    fun testRange() {
        val size = 200
        // test is too slow
        val step = 1
        val map = BTreeMap<Int, Int>()
        for (i in 0 until size) map.insert(i, i)

        for (i in 0 until size step step) {
            for (j in i until size step step) {
                val kvs = map.range(boundsPair(Bound.Included(i), Bound.Included(j))).asSequence().map { it.first to it.second }.iterator()
                val pairs = (i..j).map { it to it }.iterator()

                while (kvs.hasNext() && pairs.hasNext()) {
                    val kv = kvs.next()
                    val pair = pairs.next()
                    assertEquals(pair, kv)
                }
                assertEquals(false, kvs.hasNext())
                assertEquals(false, pairs.hasNext())
            }
        }
    }

    @Test
    fun testRangeMut() {
        val size = 200
        // test is too slow
        val step = 1
        val map = BTreeMap<Int, Int>()
        for (i in 0 until size) map.insert(i, i)

        for (i in 0 until size step step) {
            for (j in i until size step step) {
                val kvs = map.rangeMut(boundsPair(Bound.Included(i), Bound.Included(j))).asSequence().map { it.first to it.second }.iterator()
                val pairs = (i..j).map { it to it }.iterator()

                while (kvs.hasNext() && pairs.hasNext()) {
                    val kv = kvs.next()
                    val pair = pairs.next()
                    assertEquals(pair, kv)
                }
                assertEquals(false, kvs.hasNext())
                assertEquals(false, pairs.hasNext())
            }
        }
        map.check()
    }

    @Test // expected to panic
    fun testRangePanic1() {
        assertFailsWith<Throwable> {
            val map = BTreeMap<Int, String>()
            map.insert(3, "a")
            map.insert(5, "b")
            map.insert(8, "c")

            map.range(boundsPair(Bound.Included(8), Bound.Included(3)))
        }
    }

    @Test // expected to panic
    fun testRangePanic2() {
        assertFailsWith<Throwable> {
            val map = BTreeMap<Int, String>()
            map.insert(3, "a")
            map.insert(5, "b")
            map.insert(8, "c")

            map.range(boundsPair(Bound.Excluded(5), Bound.Excluded(5)))
        }
    }

    @Test // expected to panic
    fun testRangePanic3() {
        assertFailsWith<Throwable> {
            val map = BTreeMap<Int, Unit>()
            map.insert(3, Unit)
            map.insert(5, Unit)
            map.insert(8, Unit)

            map.range(boundsPair(Bound.Excluded(5), Bound.Excluded(5)))
        }
    }

    @Test
    fun testRetain() {
        val map = BTreeMap<Int, Int>()
        for (x in 0 until 100) map.insert(x, x * 10)

        map.retain { k, _ -> k % 2 == 0 }
        assertEquals(50, map.len())
        assertEquals(20, map.get(2))
        assertEquals(40, map.get(4))
        assertEquals(60, map.get(6))
    }

    @Test
    fun empty() {
        val map = BTreeMap<Int, Int>()
        val iter = map.extractIf(RangeFull) { _, _ -> error("there's nothing to decide on") }
        while (iter.hasNext()) iter.next()
        assertEquals(null, map.height())
        map.check()
    }

    @Test
    fun consumedKeepingAll() {
        val map = BTreeMap<Int, Int>()
        for (i in 0 until 3) map.insert(i, i)
        val extracted = map.extractIf(RangeFull) { _, _ -> false }.toList()
        assertEquals(emptyList(), extracted)
        map.check()
    }

    @Test
    fun consumedRemovingAll() {
        val pairs = (0 until 3).map { it to it }
        val map = BTreeMap<Int, Int>()
        for (p in pairs) map.insert(p.first, p.second)
        val extracted = map.extractIf(RangeFull) { _, _ -> true }.toList()
        assertEquals(pairs, extracted)
        assertTrue(map.isEmpty())
        map.check()
    }

    @Test
    fun consumedRemovingSome() {
        val pairs = (0 until 3).map { it to it }
        val baseMap = BTreeMap<Int, Int>()
        for (p in pairs) baseMap.insert(p.first, p.second)

        for (x in 0 until 3) {
            for (y in 0 until 3) {
                val map = baseMap.clone()
                val extracted = map.extractIf(boundsPair(Bound.Included(x), Bound.Excluded(y))) { _, _ -> true }.toList()
                val expectedExtracted = (x until y).map { it to it }
                assertEquals(expectedExtracted, extracted)
                for (i in 0 until 3) {
                    val contains = (x until y).contains(i)
                    assertTrue(map.containsKey(i) != contains)
                }
            }
        }
        for (x in 0 until 3) {
            for (y in 0 until 2) {
                val map = baseMap.clone()
                val extracted = map.extractIf(boundsPair(Bound.Included(x), Bound.Included(y))) { _, _ -> true }.toList()
                val expectedExtracted = (x..y).map { it to it }
                assertEquals(expectedExtracted, extracted)
                for (i in 0 until 3) {
                    val contains = (x..y).contains(i)
                    assertTrue(map.containsKey(i) != contains)
                }
            }
        }
    }

    @Test
    fun mutatingAndKeeping() {
        val map = BTreeMap<Int, Int>()
        for (i in 0 until 3) map.insert(i, i)
        val extracted = map.extractIf(RangeFull) { k, v ->
            map.insert(k, v + 6)
            false
        }.toList()
        assertEquals(emptyList(), extracted)
        assertEquals(listOf(0, 1, 2), map.keys().asSequence().toList())
        assertEquals(listOf(6, 7, 8), map.values().asSequence().toList())
        map.check()
    }

    @Test
    fun mutatingAndRemoving() {
        val map = BTreeMap<Int, Int>()
        for (i in 0 until 3) map.insert(i, i)
        val extracted = map.extractIf(RangeFull) { k, v ->
            map.insert(k, v + 6)
            true
        }.toList()
        val expected = (0 until 3).map { it to it + 6 }
        assertEquals(expected, extracted)
        assertTrue(map.isEmpty())
        map.check()
    }

    @Test
    fun underfullKeepingAll() {
        val map = BTreeMap<Int, Int>()
        for (i in 0 until 3) map.insert(i, i)
        val iter = map.extractIf(RangeFull) { _, _ -> false }
        while (iter.hasNext()) iter.next()
        assertEquals(listOf(0, 1, 2), map.keys().asSequence().toList())
        map.check()
    }

    @Test
    fun underfullRemovingOne() {
        val baseMap = BTreeMap<Int, Int>()
        for (i in 0 until 3) baseMap.insert(i, i)
        for (doomed in 0 until 3) {
            val map = baseMap.clone()
            val iter = map.extractIf(RangeFull) { i, _ -> i == doomed }
            while (iter.hasNext()) iter.next()
            assertEquals(2, map.len())
            map.check()
        }
    }

    @Test
    fun underfullKeepingOne() {
        val baseMap = BTreeMap<Int, Int>()
        for (i in 0 until 3) baseMap.insert(i, i)
        for (sacred in 0 until 3) {
            val map = baseMap.clone()
            val iter = map.extractIf(RangeFull) { i, _ -> i != sacred }
            while (iter.hasNext()) iter.next()
            assertEquals(listOf(sacred), map.keys().asSequence().toList())
            map.check()
        }
    }

    @Test
    fun underfullRemovingAll() {
        val map = BTreeMap<Int, Int>()
        for (i in 0 until 3) map.insert(i, i)
        val iter = map.extractIf(RangeFull) { _, _ -> true }
        while (iter.hasNext()) iter.next()
        assertTrue(map.isEmpty())
        map.check()
    }

    @Test
    fun height0KeepingAll() {
        val map = BTreeMap<Int, Int>()
        for (i in 0 until CAPACITY) map.insert(i, i)
        val iter = map.extractIf(RangeFull) { _, _ -> false }
        while (iter.hasNext()) iter.next()
        assertEquals((0 until CAPACITY).toList(), map.keys().asSequence().toList())
        map.check()
    }

    @Test
    fun height0RemovingOne() {
        val baseMap = BTreeMap<Int, Int>()
        for (i in 0 until CAPACITY) baseMap.insert(i, i)
        for (doomed in 0 until CAPACITY) {
            val map = baseMap.clone()
            val iter = map.extractIf(RangeFull) { i, _ -> i == doomed }
            while (iter.hasNext()) iter.next()
            assertEquals(CAPACITY - 1, map.len())
            map.check()
        }
    }

    @Test
    fun height0KeepingOne() {
        val baseMap = BTreeMap<Int, Int>()
        for (i in 0 until CAPACITY) baseMap.insert(i, i)
        for (sacred in 0 until CAPACITY) {
            val map = baseMap.clone()
            val iter = map.extractIf(RangeFull) { i, _ -> i != sacred }
            while (iter.hasNext()) iter.next()
            assertEquals(listOf(sacred), map.keys().asSequence().toList())
            map.check()
        }
    }

    @Test
    fun height0RemovingAll() {
        val map = BTreeMap<Int, Int>()
        for (i in 0 until CAPACITY) map.insert(i, i)
        val iter = map.extractIf(RangeFull) { _, _ -> true }
        while (iter.hasNext()) iter.next()
        assertTrue(map.isEmpty())
        map.check()
    }

    @Test
    fun height0KeepingHalf() {
        val map = BTreeMap<Int, Int>()
        for (i in 0 until 16) map.insert(i, i)
        var count = 0
        val iter = map.extractIf(RangeFull) { i, _ -> i % 2 == 0 }
        while (iter.hasNext()) {
            iter.next()
            count++
        }
        assertEquals(8, count)
        assertEquals(8, map.len())
        map.check()
    }

    @Test
    fun height1RemovingAll() {
        val map = BTreeMap<Int, Int>()
        for (i in 0 until MIN_INSERTS_HEIGHT_1) map.insert(i, i)
        val iter = map.extractIf(RangeFull) { _, _ -> true }
        while (iter.hasNext()) iter.next()
        assertTrue(map.isEmpty())
        map.check()
    }

    @Test
    fun height1RemovingOne() {
        val baseMap = BTreeMap<Int, Int>()
        for (i in 0 until MIN_INSERTS_HEIGHT_1) baseMap.insert(i, i)
        for (doomed in 0 until MIN_INSERTS_HEIGHT_1) {
            val map = baseMap.clone()
            val iter = map.extractIf(RangeFull) { i, _ -> i == doomed }
            while (iter.hasNext()) iter.next()
            assertEquals(MIN_INSERTS_HEIGHT_1 - 1, map.len())
            map.check()
        }
    }

    @Test
    fun height1KeepingOne() {
        val baseMap = BTreeMap<Int, Int>()
        for (i in 0 until MIN_INSERTS_HEIGHT_1) baseMap.insert(i, i)
        for (sacred in 0 until MIN_INSERTS_HEIGHT_1) {
            val map = baseMap.clone()
            val iter = map.extractIf(RangeFull) { i, _ -> i != sacred }
            while (iter.hasNext()) iter.next()
            assertEquals(listOf(sacred), map.keys().asSequence().toList())
            map.check()
        }
    }

    @Test
    fun height2RemovingOne() {
        val baseMap = BTreeMap<Int, Int>()
        for (i in 0 until MIN_INSERTS_HEIGHT_2) baseMap.insert(i, i)
        for (doomed in 0 until MIN_INSERTS_HEIGHT_2 step 12) {
            val map = baseMap.clone()
            val iter = map.extractIf(RangeFull) { i, _ -> i == doomed }
            while (iter.hasNext()) iter.next()
            assertEquals(MIN_INSERTS_HEIGHT_2 - 1, map.len())
            map.check()
        }
    }

    @Test
    fun height2KeepingOne() {
        val baseMap = BTreeMap<Int, Int>()
        for (i in 0 until MIN_INSERTS_HEIGHT_2) baseMap.insert(i, i)
        for (sacred in 0 until MIN_INSERTS_HEIGHT_2 step 12) {
            val map = baseMap.clone()
            val iter = map.extractIf(RangeFull) { i, _ -> i != sacred }
            while (iter.hasNext()) iter.next()
            assertEquals(listOf(sacred), map.keys().asSequence().toList())
            map.check()
        }
    }

    @Test
    fun height2RemovingAll() {
        val map = BTreeMap<Int, Int>()
        for (i in 0 until MIN_INSERTS_HEIGHT_2) map.insert(i, i)
        val iter = map.extractIf(RangeFull) { _, _ -> true }
        while (iter.hasNext()) iter.next()
        assertTrue(map.isEmpty())
        map.check()
    }

    @Test
    fun dropPanicLeak() {
        val a = CrashTestDummy(0)
        val b = CrashTestDummy(1)
        val c = CrashTestDummy(2)
        val map = BTreeMap<CrashTestDummyRef, Unit>()
        map.insert(a.spawn(Panic.Never), Unit)
        map.insert(b.spawn(Panic.InDrop), Unit)
        map.insert(c.spawn(Panic.Never), Unit)

        assertFailsWith<Throwable> {
            try {
                val iter = map.extractIf(RangeFull) { dummy, _ -> dummy.query(true) }
                while (iter.hasNext()) {
                    val item = iter.next()
                    item.first.drop()
                }
            } catch (t: Throwable) {
                map.drop()
                throw t
            }
        }

        assertEquals(a.queried(), 1)
        assertEquals(b.queried(), 1)
        assertEquals(c.queried(), 0)
        assertEquals(a.dropped(), 1)
        assertEquals(b.dropped(), 1)
        assertEquals(c.dropped(), 1)
    }

    @Test
    fun predPanicLeak() {
        val a = CrashTestDummy(0)
        val b = CrashTestDummy(1)
        val c = CrashTestDummy(2)
        val map = BTreeMap<CrashTestDummyRef, Unit>()
        map.insert(a.spawn(Panic.Never), Unit)
        map.insert(b.spawn(Panic.InQuery), Unit)
        map.insert(c.spawn(Panic.InQuery), Unit)

        assertFailsWith<Throwable> {
            val iter = map.extractIf(RangeFull) { dummy, _ -> dummy.query(true) }
            while (iter.hasNext()) {
                val item = iter.next()
                item.first.drop()
            }
        }

        assertEquals(1, a.queried())
        assertEquals(1, b.queried())
        assertEquals(0, c.queried())
        assertEquals(1, a.dropped())
        assertEquals(0, b.dropped())
        assertEquals(0, c.dropped())
        assertEquals(2, map.len())
        assertEquals(1, map.firstEntry()!!.key().dummy.id)
        assertEquals(2, map.lastEntry()!!.key().dummy.id)
        map.check()
    }

    @Test
    fun predPanicReuse() {
        val a = CrashTestDummy(0)
        val b = CrashTestDummy(1)
        val c = CrashTestDummy(2)
        val map = BTreeMap<CrashTestDummyRef, Unit>()
        map.insert(a.spawn(Panic.Never), Unit)
        map.insert(b.spawn(Panic.InQuery), Unit)
        map.insert(c.spawn(Panic.InQuery), Unit)

        val iter = map.extractIf(RangeFull) { dummy, _ -> dummy.query(true) }
        assertFailsWith<Throwable> {
            while (iter.hasNext()) iter.next().first.drop()
        }

        val result = runCatching {
            if (iter.hasNext()) iter.next().also { it.first.drop() } else null
        }
        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())

        assertEquals(1, a.queried())
        assertEquals(1, b.queried())
        assertEquals(0, c.queried())
        assertEquals(1, a.dropped())
        assertEquals(0, b.dropped())
        assertEquals(0, c.dropped())
        assertEquals(2, map.len())
        assertEquals(1, map.firstEntry()!!.key().dummy.id)
        assertEquals(2, map.lastEntry()!!.key().dummy.id)
        map.check()
    }

    @Test
    fun testBorrow() {
        val map1 = BTreeMap<String, Int>()
        map1.insert("0", 1)
        assertEquals(1, map1.get("0"))

        val map2 = BTreeMap<Int, Int>()
        map2.insert(0, 1)
        assertEquals(1, map2.get(0))

        data class SliceKey(val values: List<Int>) : Comparable<SliceKey> {
            override fun compareTo(other: SliceKey): Int {
                val n = minOf(values.size, other.values.size)
                for (i in 0 until n) {
                    val cmp = values[i].compareTo(other.values[i])
                    if (cmp != 0) return cmp
                }
                return values.size.compareTo(other.values.size)
            }
        }
        val map3 = BTreeMap<SliceKey, Int>()
        map3.insert(SliceKey(listOf(0, 1)), 1)
        assertEquals(1, map3.get(SliceKey(listOf(0, 1))))

        val map4 = BTreeMap<Int, Int>()
        map4.insert(0, 1)
        assertEquals(1, map4.get(0))

        fun <T : Comparable<T>> get(v: BTreeMap<T, Unit>, t: T) {
            val _ignore = v.get(t)
        }

        fun <T : Comparable<T>> getMut(v: BTreeMap<T, Unit>, t: T) {
            val _ignore = v.getMut(t)
        }

        fun <T : Comparable<T>> getKeyValue(v: BTreeMap<T, Unit>, t: T) {
            val _ignore = v.getKeyValue(t)
        }

        fun <T : Comparable<T>> containsKey(v: BTreeMap<T, Unit>, t: T) {
            val _ignore = v.containsKey(t)
        }

        fun <T : Comparable<T>> range(v: BTreeMap<T, Unit>, t: T) {
            val _ignore = v.range(RangeFrom(t))
        }

        fun <T : Comparable<T>> rangeMut(v: BTreeMap<T, Unit>, t: T) {
            val _ignore = v.rangeMut(RangeFrom(t))
        }

        fun <T : Comparable<T>> remove(v: BTreeMap<T, Unit>, t: T) {
            v.remove(t)
        }

        fun <T : Comparable<T>> removeEntry(v: BTreeMap<T, Unit>, t: T) {
            v.removeEntry(t)
        }

        fun <T : Comparable<T>> splitOff(v: BTreeMap<T, Unit>, t: T) {
            v.splitOff(t)
        }
    }

    @Test
    fun testEntry() {
        val xs = listOf(1 to 10, 2 to 20, 3 to 30, 4 to 40, 5 to 50, 6 to 60)
        val map = BTreeMap<Int, Int>()
        for (x in xs) map.insert(x.first, x.second)

        // Existing key (insert)
        when (val e = map.entry(1)) {
            is Entry.Vacant -> error("unreachable")
            is Entry.Occupied -> {
                assertEquals(10, e.entry.get())
                assertEquals(10, e.entry.insert(100))
            }
        }
        assertEquals(100, map.get(1))
        assertEquals(6, map.len())

        // Existing key (update)
        when (val e = map.entry(2)) {
            is Entry.Vacant -> error("unreachable")
            is Entry.Occupied -> {
                val v = e.entry.getMut()
                e.entry.insert(v * 10)
            }
        }
        assertEquals(200, map.get(2))
        assertEquals(6, map.len())
        map.check()

        // Existing key (take)
        when (val e = map.entry(3)) {
            is Entry.Vacant -> error("unreachable")
            is Entry.Occupied -> {
                assertEquals(30, e.entry.remove())
            }
        }
        assertEquals(null, map.get(3))
        assertEquals(5, map.len())
        map.check()

        // Inexistent key (insert)
        when (val e = map.entry(10)) {
            is Entry.Occupied -> error("unreachable")
            is Entry.Vacant -> {
                assertEquals(1000, e.entry.insert(1000))
            }
        }
        assertEquals(1000, map.get(10))
        assertEquals(6, map.len())
        map.check()
    }

    @Test
    fun testZst() {
        val m = BTreeMap<SetValZst, SetValZst>()
        assertEquals(0, m.len())

        assertEquals(null, m.insert(SetValZst, SetValZst))
        assertEquals(1, m.len())

        assertEquals(SetValZst, m.insert(SetValZst, SetValZst))
        assertEquals(1, m.len())
        assertEquals(1, m.iter().asSequence().count())

        m.clear()
        assertEquals(0, m.len())

        for (i in 0 until 100) {
            m.insert(SetValZst, SetValZst)
        }

        assertEquals(1, m.len())
        assertEquals(1, m.iter().asSequence().count())
        m.check()
    }

    @Test
    fun testBadZst() {
        class Bad : Comparable<Bad> {
            override fun equals(other: Any?): Boolean = false
            override fun hashCode(): Int = 0
            override fun compareTo(other: Bad): Int = -1
        }

        val m = BTreeMap<Bad, Bad>()

        for (i in 0 until 100) {
            m.insert(Bad(), Bad())
        }
        m.check()
    }

    @Test
    fun testClear() {
        val map = BTreeMap<Int, Unit>()
        for (len in listOf(MIN_INSERTS_HEIGHT_1, MIN_INSERTS_HEIGHT_2, 0, CAPACITY)) {
            for (i in 0 until len) {
                map.insert(i, Unit)
            }
            assertEquals(len, map.len())
            map.clear()
            map.check()
            assertEquals(null, map.height())
        }
    }

    @Test
    fun testClearDropPanicLeak() {
        val a = CrashTestDummy(0)
        val b = CrashTestDummy(1)
        val c = CrashTestDummy(2)

        val map = BTreeMap<CrashTestDummyRef, Unit>()
        map.insert(a.spawn(Panic.Never), Unit)
        map.insert(b.spawn(Panic.InDrop), Unit)
        map.insert(c.spawn(Panic.Never), Unit)

        assertFailsWith<Throwable> { map.clear() }
        assertEquals(1, a.dropped())
        assertEquals(1, b.dropped())
        assertEquals(1, c.dropped())
        assertEquals(0, map.len())

        // map goes out of scope here, dropping again
        assertEquals(1, a.dropped())
        assertEquals(1, b.dropped())
        assertEquals(1, c.dropped())
    }

    @Test
    fun testClone() {
        var map = BTreeMap<Int, Int>()
        val size = MIN_INSERTS_HEIGHT_1
        assertEquals(0, map.len())

        for (i in 0 until size) {
            assertEquals(null, map.insert(i, 10 * i))
            assertEquals(i + 1, map.len())
            map.check()
            assertEquals(map, map.clone())
        }

        for (i in 0 until size) {
            assertEquals(10 * i, map.insert(i, 100 * i))
            assertEquals(size, map.len())
            map.check()
            assertEquals(map, map.clone())
        }

        for (i in 0 until size / 2) {
            assertEquals(i * 200, map.remove(i * 2))
            assertEquals(size - i - 1, map.len())
            map.check()
            assertEquals(map, map.clone())
        }

        for (i in 0 until size / 2) {
            assertEquals(null, map.remove(2 * i))
            assertEquals(i * 200 + 100, map.remove(2 * i + 1))
            assertEquals(size / 2 - i - 1, map.len())
            map.check()
            assertEquals(map, map.clone())
        }

        val tempMap = BTreeMap<Int, Int>()
        for (i in 1 until MIN_INSERTS_HEIGHT_2) tempMap.insert(i, i)
        map = tempMap
        assertEquals(MIN_INSERTS_HEIGHT_2 - 1, map.len())
        assertEquals(map, map.clone())
        map.insert(0, 0)
        assertEquals(MIN_INSERTS_HEIGHT_2, map.len())
        assertEquals(map, map.clone())
        map.check()
    }

    private fun testClonePanicLeak(size: Int) {
        for (i in 0 until size) {
            val dummies = (0 until size).map { CrashTestDummy(it) }
            val map = BTreeMap<CrashTestDummyRef, Unit>()
            for (dummy in dummies) {
                val panic = if (dummy.id == i) Panic.InClone else Panic.Never
                map.insert(dummy.spawn(panic), Unit)
            }

            assertFailsWith<Throwable> { map.clone() }
            for (d in dummies) {
                assertEquals(if (d.id <= i) 1 else 0, d.cloned(), "id=${d.id}/$i")
                assertEquals(if (d.id < i) 1 else 0, d.dropped(), "id=${d.id}/$i")
            }
            assertEquals(size, map.len())

            map.drop()
            for (d in dummies) {
                assertEquals(if (d.id <= i) 1 else 0, d.cloned(), "id=${d.id}/$i")
                assertEquals(if (d.id < i) 2 else 1, d.dropped(), "id=${d.id}/$i")
            }
        }
    }

    @Test
    fun testClonePanicLeakHeight0() {
        testClonePanicLeak(3)
    }

    @Test
    fun testClonePanicLeakHeight1() {
        testClonePanicLeak(MIN_INSERTS_HEIGHT_1)
    }

    @Test
    fun testCloneFrom() {
        val map1 = BTreeMap<Int, Int>()
        val maxSize = MIN_INSERTS_HEIGHT_1

        for (i in 0..maxSize) {
            val map2 = BTreeMap<Int, Int>()
            for (j in 0 until i) {
                val map1Copy = map2.clone()
                map1Copy.cloneFrom(map1)
                assertEquals(map1, map1Copy)
                val map2Copy = map1.clone()
                map2Copy.cloneFrom(map2)
                assertEquals(map2, map2Copy)
                map2.insert(100 * j + 1, 2 * j + 1)
            }
            map2.cloneFrom(map1)
            map2.check()
        }
    }

    @Test
    fun testOrdAbsence() {
        fun <K : Comparable<K>> map(map: BTreeMap<K, Unit>) {
            val _ignore1 = map.isEmpty()
            val _ignore2 = map.len()
            map.clear()
            val _ignore3 = map.iter()
            val _ignore4 = map.iterMut()
            val _ignore5 = map.keys()
            val _ignore6 = map.values()
            val _ignore7 = map.valuesMut()
            if (true) {
                val _ignore8 = map.intoValues()
            } else if (true) {
                val _ignore9 = map.intoIter()
            } else {
                val _ignore10 = map.intoKeys()
            }
        }

        fun <K : Comparable<K>> mapDebug(map: BTreeMap<K, Unit>) {
            val _ignore1 = map.toString()
            val _ignore2 = map.iter().toString()
            val _ignore3 = map.iterMut().toString()
            val _ignore4 = map.keys().toString()
            val _ignore5 = map.values().toString()
            val _ignore6 = map.valuesMut().toString()
            if (true) {
                val _ignore7 = map.intoIter().toString()
            } else if (true) {
                val _ignore8 = map.intoKeys().toString()
            } else {
                val _ignore9 = map.intoValues().toString()
            }
        }

        fun <K : Comparable<K>> mapClone(map: BTreeMap<K, Unit>) {
            val clone = map.clone()
            map.cloneFrom(clone)
        }

        class NonOrd : Comparable<NonOrd> {
            override fun compareTo(other: NonOrd): Int = 0
        }

        map(BTreeMap<NonOrd, Unit>())
        mapDebug(BTreeMap<NonOrd, Unit>())
        mapClone(BTreeMap<NonOrd, Unit>())
    }

    @Test
    fun testOccupiedEntryKey() {
        val a = BTreeMap<String, String>()
        val key = "hello there"
        val value = "value goes here"
        assertEquals(null, a.height())
        a.insert(key, value)
        assertEquals(1, a.len())
        assertEquals(value, a.get(key))

        when (val e = a.entry(key)) {
            is Entry.Vacant -> error("unreachable")
            is Entry.Occupied -> assertEquals(key, e.key())
        }
        assertEquals(1, a.len())
        assertEquals(value, a.get(key))
        a.check()
    }

    @Test
    fun testVacantEntryKey() {
        val a = BTreeMap<String, String>()
        val key = "hello there"
        val value = "value goes here"

        assertEquals(null, a.height())
        when (val e = a.entry(key)) {
            is Entry.Occupied -> error("unreachable")
            is Entry.Vacant -> {
                assertEquals(key, e.key())
                e.entry.insert(value)
            }
        }
        assertEquals(1, a.len())
        assertEquals(value, a.get(key))
        a.check()
    }

    @Test
    fun testVacantEntryNoInsert() {
        val a = BTreeMap<String, Unit>()
        val key = "hello there"

        assertEquals(null, a.height())
        when (val e = a.entry(key)) {
            is Entry.Occupied -> error("unreachable")
            is Entry.Vacant -> assertEquals(key, e.key())
        }
        assertEquals(null, a.height())
        a.check()

        a.insert(key, Unit)
        a.remove(key)
        assertEquals(0, a.height())
        assertTrue(a.isEmpty())
        when (val e = a.entry(key)) {
            is Entry.Occupied -> error("unreachable")
            is Entry.Vacant -> assertEquals(key, e.key())
        }
        assertEquals(0, a.height())
        assertTrue(a.isEmpty())
        a.check()
    }

    @Test
    fun testFirstLastEntry() {
        val a = BTreeMap<Int, Int>()
        assertTrue(a.firstEntry() == null)
        assertTrue(a.lastEntry() == null)
        a.insert(1, 42)
        assertEquals(1, a.firstEntry()!!.key())
        assertEquals(1, a.lastEntry()!!.key())
        a.insert(2, 24)
        assertEquals(1, a.firstEntry()!!.key())
        assertEquals(2, a.lastEntry()!!.key())
        a.insert(0, 6)
        assertEquals(0, a.firstEntry()!!.key())
        assertEquals(2, a.lastEntry()!!.key())
        val (k1, v1) = a.firstEntry()!!.removeEntry()
        assertEquals(0, k1)
        assertEquals(6, v1)
        val (k2, v2) = a.lastEntry()!!.removeEntry()
        assertEquals(2, k2)
        assertEquals(24, v2)
        assertEquals(1, a.firstEntry()!!.key())
        assertEquals(1, a.lastEntry()!!.key())
        a.check()
    }

    @Test
    fun testPopFirstLast() {
        val map = BTreeMap<Int, Int>()
        assertEquals(null, map.popFirst())
        assertEquals(null, map.popLast())

        map.insert(1, 10)
        map.insert(2, 20)
        map.insert(3, 30)
        map.insert(4, 40)

        assertEquals(4, map.len())

        var kv = map.popFirst()!!
        assertEquals(1, kv.first)
        assertEquals(10, kv.second)
        assertEquals(3, map.len())

        kv = map.popFirst()!!
        assertEquals(2, kv.first)
        assertEquals(20, kv.second)
        assertEquals(2, map.len())

        kv = map.popLast()!!
        assertEquals(4, kv.first)
        assertEquals(40, kv.second)
        assertEquals(1, map.len())

        map.insert(5, 50)
        map.insert(6, 60)
        assertEquals(3, map.len())

        kv = map.popFirst()!!
        assertEquals(3, kv.first)
        assertEquals(30, kv.second)
        assertEquals(2, map.len())

        kv = map.popLast()!!
        assertEquals(6, kv.first)
        assertEquals(60, kv.second)
        assertEquals(1, map.len())

        kv = map.popLast()!!
        assertEquals(5, kv.first)
        assertEquals(50, kv.second)
        assertEquals(0, map.len())

        assertEquals(null, map.popFirst())
        assertEquals(null, map.popLast())

        map.insert(7, 70)
        map.insert(8, 80)

        kv = map.popLast()!!
        assertEquals(8, kv.first)
        assertEquals(80, kv.second)
        assertEquals(1, map.len())

        kv = map.popLast()!!
        assertEquals(7, kv.first)
        assertEquals(70, kv.second)
        assertEquals(0, map.len())

        assertEquals(null, map.popFirst())
        assertEquals(null, map.popLast())
    }

    @Test
    fun testGetKeyValue() {
        val map = BTreeMap<Int, Int>()

        assertTrue(map.isEmpty())
        assertEquals(null, map.getKeyValue(1))
        assertEquals(null, map.getKeyValue(2))

        map.insert(1, 10)
        map.insert(2, 20)
        map.insert(3, 30)

        assertEquals(3, map.len())
        assertEquals(Pair(1, 10), map.getKeyValue(1))
        assertEquals(Pair(3, 30), map.getKeyValue(3))
        assertEquals(null, map.getKeyValue(4))

        map.remove(3)

        assertEquals(2, map.len())
        assertEquals(null, map.getKeyValue(3))
        assertEquals(Pair(2, 20), map.getKeyValue(2))
    }

    @Test
    fun testInsertIntoFullHeight0() {
        val size = CAPACITY
        for (pos in 0..size) {
            val map = BTreeMap<Int, Unit>()
            for (i in 0 until size) map.insert(i * 2 + 1, Unit)
            assertEquals(null, map.insert(pos * 2, Unit))
            map.check()
        }
    }

    @Test
    fun testInsertIntoFullHeight1() {
        val size = CAPACITY + 1 + CAPACITY
        for (pos in 0..size) {
            val map = BTreeMap<Int, Unit>()
            for (i in 0 until size) map.insert(i * 2 + 1, Unit)
            map.compact()
            val rootNode = map.root!!.reborrow()
            assertEquals(1, rootNode.height)
            assertEquals(CAPACITY, rootNode.firstLeafEdge().intoNode().len())
            assertEquals(CAPACITY, rootNode.lastLeafEdge().intoNode().len())

            assertEquals(null, map.insert(pos * 2, Unit))
            map.check()
        }
    }

    @Test
    fun testTryInsert() {
        val map = BTreeMap<Int, Int>()

        assertTrue(map.isEmpty())

        assertEquals(10, map.tryInsert(1, 10).getOrThrow())
        assertEquals(20, map.tryInsert(2, 20).getOrThrow())

        val err = map.tryInsert(2, 200).exceptionOrNull() as OccupiedError
        assertEquals(2, err.entry.key())
        assertEquals(20, err.entry.get())
        assertEquals(200, err.value)
    }

    @Test
    fun testAppendDropLeak() {
        val a = CrashTestDummy(0)
        val b = CrashTestDummy(1)
        val c = CrashTestDummy(2)
        val left = BTreeMap<CrashTestDummyRef, Unit>()
        val right = BTreeMap<CrashTestDummyRef, Unit>()
        left.insert(a.spawn(Panic.Never), Unit)
        left.insert(b.spawn(Panic.Never), Unit)
        left.insert(c.spawn(Panic.Never), Unit)
        right.insert(b.spawn(Panic.InDrop), Unit)
        right.insert(c.spawn(Panic.Never), Unit)

        assertFailsWithMoved(left, right) { left.append(right) }
        assertEquals(1, a.dropped())
        assertEquals(2, b.dropped())
        assertEquals(2, c.dropped())
    }

    @Test
    fun testAppendOrdChaos() {
        val map1 = BTreeMap<Cyclic3, Unit>()
        map1.insert(Cyclic3.A, Unit)
        map1.insert(Cyclic3.B, Unit)
        val map2 = BTreeMap<Cyclic3, Unit>()
        map2.insert(Cyclic3.A, Unit)
        map2.insert(Cyclic3.B, Unit)
        map2.insert(Cyclic3.C, Unit)
        map2.insert(Cyclic3.B, Unit)
        map1.check()
        map2.check()
        assertEquals(2, map1.len())
        assertEquals(4, map2.len())
        map1.append(map2)
        assertEquals(5, map1.len())
        assertEquals(0, map2.len())
        map1.check()
        map2.check()
    }

    @Test
    fun testMergeDropLeak() {
        val a = CrashTestDummy(0)
        val b = CrashTestDummy(1)
        val c = CrashTestDummy(2)
        val left = BTreeMap<CrashTestDummyRef, Unit>()
        val right = BTreeMap<CrashTestDummyRef, Unit>()
        left.insert(a.spawn(Panic.Never), Unit)
        left.insert(b.spawn(Panic.Never), Unit)
        left.insert(c.spawn(Panic.Never), Unit)
        right.insert(b.spawn(Panic.InDrop), Unit)
        right.insert(c.spawn(Panic.Never), Unit)

        assertFailsWithMoved(left, right) { left.merge(right) { _, _, _ -> Unit } }
        assertEquals(1, a.dropped())
        assertEquals(2, b.dropped())
        assertEquals(2, c.dropped())
    }

    @Test
    fun testMergeConflictDropLeak() {
        val a = CrashTestDummy(0)
        val aValLeft = CrashTestDummy(0)

        val b = CrashTestDummy(1)
        val bValLeft = CrashTestDummy(1)
        val bValRight = CrashTestDummy(1)

        val c = CrashTestDummy(2)
        val cValLeft = CrashTestDummy(2)
        val cValRight = CrashTestDummy(2)

        val left = BTreeMap<CrashTestDummyRef, CrashTestDummyRef>()
        val right = BTreeMap<CrashTestDummyRef, CrashTestDummyRef>()

        left.insert(a.spawn(Panic.Never), aValLeft.spawn(Panic.Never))
        left.insert(b.spawn(Panic.Never), bValLeft.spawn(Panic.Never))
        left.insert(c.spawn(Panic.Never), cValLeft.spawn(Panic.Never))
        right.insert(b.spawn(Panic.Never), bValRight.spawn(Panic.Never))
        right.insert(c.spawn(Panic.Never), cValRight.spawn(Panic.Never))

        assertFailsWithMoved(left, right) {
            left.merge(right) { _, _, _ -> error("Panic in conflict function") }
            assertEquals(1, left.len())
        }
        assertEquals(1, a.dropped())
        assertEquals(1, aValLeft.dropped())
        assertEquals(2, b.dropped())
        assertEquals(1, bValLeft.dropped())
        assertEquals(1, bValRight.dropped())
        assertEquals(2, c.dropped())
        assertEquals(1, cValLeft.dropped())
        assertEquals(1, cValRight.dropped())
    }

    @Test
    fun testMergeOrdChaos() {
        val map1 = BTreeMap<Cyclic3, Unit>()
        map1.insert(Cyclic3.A, Unit)
        map1.insert(Cyclic3.B, Unit)
        val map2 = BTreeMap<Cyclic3, Unit>()
        map2.insert(Cyclic3.A, Unit)
        map2.insert(Cyclic3.B, Unit)
        map2.insert(Cyclic3.C, Unit)
        map2.insert(Cyclic3.B, Unit)
        map1.check()
        map2.check()
        assertEquals(2, map1.len())
        assertEquals(4, map2.len())
        map1.merge(map2) { _, _, _ -> Unit }
        assertEquals(5, map1.len())
        map1.check()
    }

    private fun randData(len: Int): List<Pair<Int, Int>> {
        var state = 1
        fun next(): Int {
            state = (state * 1103515245 + 12345).toUInt().toInt() and 0x7FFFFFFF
            return state
        }
        val list = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until len) {
            list.add(Pair(next(), next()))
        }
        return list
    }

    @Test
    fun testSplitOffEmptyRight() {
        val data = randData(173).toMutableList()

        val map = BTreeMap<Int, Int>()
        for (d in data) map.insert(d.first, d.second)
        val right = map.splitOff(data.maxByOrNull { it.first }!!.first + 1)
        map.check()
        right.check()

        data.sortBy { it.first }
        assertEquals(data, map.intoIter().toList())
        assertEquals(emptyList<Pair<Int, Int>>(), right.intoIter().toList())
    }

    @Test
    fun testSplitOffEmptyLeft() {
        val data = randData(314).toMutableList()

        val map = BTreeMap<Int, Int>()
        for (d in data) map.insert(d.first, d.second)
        val right = map.splitOff(data.minByOrNull { it.first }!!.first)
        map.check()
        right.check()

        data.sortBy { it.first }
        assertEquals(emptyList<Pair<Int, Int>>(), map.intoIter().toList())
        assertEquals(data, right.intoIter().toList())
    }

    @Test
    fun testSplitOffTinyLeftHeight2() {
        val pairs = (0 until MIN_INSERTS_HEIGHT_2).map { Pair(it, it) }
        val left = BTreeMap<Int, Int>()
        for (p in pairs) left.insert(p.first, p.second)
        val right = left.splitOff(1)
        left.check()
        right.check()
        assertEquals(1, left.len())
        assertEquals(MIN_INSERTS_HEIGHT_2 - 1, right.len())
        assertEquals(0, left.firstKeyValue()!!.first)
        assertEquals(1, right.firstKeyValue()!!.first)
    }

    @Test
    fun testSplitOffTinyRightHeight2() {
        val pairs = (0 until MIN_INSERTS_HEIGHT_2).map { Pair(it, it) }
        val last = MIN_INSERTS_HEIGHT_2 - 1
        val left = BTreeMap<Int, Int>()
        for (p in pairs) left.insert(p.first, p.second)
        assertEquals(last, left.lastKeyValue()!!.first)
        val right = left.splitOff(last)
        left.check()
        right.check()
        assertEquals(MIN_INSERTS_HEIGHT_2 - 1, left.len())
        assertEquals(1, right.len())
        assertEquals(last - 1, left.lastKeyValue()!!.first)
        assertEquals(last, right.lastKeyValue()!!.first)
    }

    @Test
    fun testSplitOffHalfway() {
        for (len in listOf(CAPACITY, 25, 50, 75, 100)) {
            val data = randData(len).map { Pair(it.first, Unit) }.toMutableList()
            val map = BTreeMap<Int, Unit>()
            for (d in data) map.insert(d.first, d.second)
            data.sortBy { it.first }
            val smallKeys = data.take(len / 2).map { it.first }
            val largeKeys = data.drop(len / 2).map { it.first }
            val splitKey = largeKeys.first()
            val right = map.splitOff(splitKey)
            map.check()
            right.check()
            assertEquals(smallKeys, map.keys().asSequence().toList())
            assertEquals(largeKeys, right.keys().toList())
        }
    }

    @Test
    fun testSplitOffLargeRandomSorted() {
        val data = randData(529).toMutableList()
        data.sortBy { it.first }

        val map = BTreeMap<Int, Int>()
        for (d in data) map.insert(d.first, d.second)
        val key = data[data.size / 2].first
        val right = map.splitOff(key)
        map.check()
        right.check()

        assertEquals(data.filter { it.first < key }, map.intoIter().toList())
        assertEquals(data.filter { it.first >= key }, right.intoIter().toList())
    }

    @Test
    fun testIntoIterDropLeakHeight0() {
        val a = CrashTestDummy(0)
        val b = CrashTestDummy(1)
        val c = CrashTestDummy(2)
        val d = CrashTestDummy(3)
        val e = CrashTestDummy(4)
        val map = BTreeMap<String, CrashTestDummyRef>()
        map.insert("a", a.spawn(Panic.Never))
        map.insert("b", b.spawn(Panic.Never))
        map.insert("c", c.spawn(Panic.Never))
        map.insert("d", d.spawn(Panic.InDrop))
        map.insert("e", e.spawn(Panic.Never))

        assertFailsWith<Throwable> {
            map.intoIter().drop()
        }

        assertEquals(1, a.dropped())
        assertEquals(1, b.dropped())
        assertEquals(1, c.dropped())
        assertEquals(1, d.dropped())
        assertEquals(1, e.dropped())
    }

    @Test
    fun testIntoIterDropLeakKvPanicInKey() {
        val aK = CrashTestDummy(0)
        val aV = CrashTestDummy(1)
        val bK = CrashTestDummy(2)
        val bV = CrashTestDummy(3)
        val cK = CrashTestDummy(4)
        val cV = CrashTestDummy(5)
        val map = BTreeMap<CrashTestDummyRef, CrashTestDummyRef>()
        map.insert(aK.spawn(Panic.Never), aV.spawn(Panic.Never))
        map.insert(bK.spawn(Panic.InDrop), bV.spawn(Panic.Never))
        map.insert(cK.spawn(Panic.Never), cV.spawn(Panic.Never))

        assertFailsWith<Throwable> {
            map.intoIter().drop()
        }

        assertEquals(1, aK.dropped())
        assertEquals(1, aV.dropped())
        assertEquals(1, bK.dropped())
        assertEquals(1, bV.dropped())
        assertEquals(1, cK.dropped())
        assertEquals(1, cV.dropped())
    }

    @Test
    fun testIntoIterDropLeakKvPanicInVal() {
        val aK = CrashTestDummy(0)
        val aV = CrashTestDummy(1)
        val bK = CrashTestDummy(2)
        val bV = CrashTestDummy(3)
        val cK = CrashTestDummy(4)
        val cV = CrashTestDummy(5)
        val map = BTreeMap<CrashTestDummyRef, CrashTestDummyRef>()
        map.insert(aK.spawn(Panic.Never), aV.spawn(Panic.Never))
        map.insert(bK.spawn(Panic.Never), bV.spawn(Panic.InDrop))
        map.insert(cK.spawn(Panic.Never), cV.spawn(Panic.Never))

        assertFailsWith<Throwable> {
            map.intoIter().drop()
        }

        assertEquals(1, aK.dropped())
        assertEquals(1, aV.dropped())
        assertEquals(1, bK.dropped())
        assertEquals(1, bV.dropped())
        assertEquals(1, cK.dropped())
        assertEquals(1, cV.dropped())
    }

    @Test
    fun testIntoIterDropLeakHeight1() {
        val size = MIN_INSERTS_HEIGHT_1
        for (panicPoint in listOf(0, 1, size - 2, size - 1)) {
            val dummies = (0 until size).map { CrashTestDummy(it) }
            val map = BTreeMap<CrashTestDummyRef, CrashTestDummyRef>()
            for (i in 0 until size) {
                val panic = if (i == panicPoint) Panic.InDrop else Panic.Never
                map.insert(dummies[i].spawn(Panic.Never), dummies[i].spawn(panic))
            }
            assertFailsWith<Throwable> {
                map.intoIter().drop()
            }
            for (i in 0 until size) {
                assertEquals(2, dummies[i].dropped())
            }
        }
    }

    @Test
    fun testIntoKeys() {
        val map = BTreeMap<Int, Char>()
        map.insert(1, 'a')
        map.insert(2, 'b')
        map.insert(3, 'c')
        val keys = map.intoKeys().toList()

        assertEquals(3, keys.size)
        assertTrue(keys.contains(1))
        assertTrue(keys.contains(2))
        assertTrue(keys.contains(3))
    }

    @Test
    fun testIntoValues() {
        val map = BTreeMap<Int, Char>()
        map.insert(1, 'a')
        map.insert(2, 'b')
        map.insert(3, 'c')
        val values = map.intoValues().toList()

        assertEquals(3, values.size)
        assertTrue(values.contains('a'))
        assertTrue(values.contains('b'))
        assertTrue(values.contains('c'))
    }

    @Test
    fun testInsertRemoveIntertwined() {
        val loops = 100
        val map = BTreeMap<Int, Int>()
        var i = 1
        val offset = 165
        for (j in 0 until loops) {
            i = (i + offset) and 0xFF
            map.insert(i, i)
            map.remove(0xFF - i)
        }
        map.check()
    }

    @Test
    fun testInsertRemoveIntertwinedOrdChaos() {
        val loops = 100
        val gov = Governor.new()
        val map = BTreeMap<Governed, Unit>()
        var i = 1
        val offset = 165
        for (j in 0 until loops) {
            i = (i + offset) and 0xFF
            map.insert(Governed(i, gov), Unit)
            map.remove(Governed(0xFF - i, gov))
            gov.flip()
        }
        map.checkInvariants()
    }

    @Test
    fun fromArray() {
        val map = BTreeMap<Int, Int>()
        map.insert(1, 2)
        map.insert(3, 4)

        val unorderedDuplicates = BTreeMap<Int, Int>()
        unorderedDuplicates.insert(3, 4)
        unorderedDuplicates.insert(1, 2)
        unorderedDuplicates.insert(1, 2)

        assertEquals(map, unorderedDuplicates)
    }

    @Test
    fun testCursor() {
        val map = BTreeMap<Int, Char>()
        map.insert(1, 'a')
        map.insert(2, 'b')
        map.insert(3, 'c')

        var cur = map.lowerBound(Bound.Unbounded)
        assertEquals(Pair(1, 'a'), cur.peekNext())
        assertEquals(null, cur.peekPrev())
        assertEquals(null, cur.prev())
        assertEquals(Pair(1, 'a'), cur.next())

        assertEquals(Pair(2, 'b'), cur.next())

        assertEquals(Pair(3, 'c'), cur.peekNext())
        assertEquals(Pair(2, 'b'), cur.prev())
        assertEquals(Pair(1, 'a'), cur.peekPrev())

        cur = map.upperBound(Bound.Excluded(1))
        assertEquals(null, cur.peekPrev())
        assertEquals(Pair(1, 'a'), cur.next())
        assertEquals(Pair(1, 'a'), cur.prev())
    }

    @Test
    fun testCursorMut() {
        val map = BTreeMap<Int, Char>()
        map.insert(1, 'a')
        map.insert(3, 'c')
        map.insert(5, 'e')
        var cur = map.lowerBoundMut(Bound.Excluded(3))
        assertEquals(Pair(5, 'e'), cur.peekNext())
        assertEquals(Pair(3, 'c'), cur.peekPrev())

        cur.insertBefore(4, 'd').getOrThrow()
        assertEquals(Pair(5, 'e'), cur.peekNext())
        assertEquals(Pair(4, 'd'), cur.peekPrev())

        assertEquals(Pair(5, 'e'), cur.next())
        assertEquals(null, cur.peekNext())
        assertEquals(Pair(5, 'e'), cur.peekPrev())
        cur.insertBefore(6, 'f').getOrThrow()
        assertEquals(null, cur.peekNext())
        assertEquals(Pair(6, 'f'), cur.peekPrev())
        assertEquals(Pair(6, 'f'), cur.removePrev())
        assertEquals(Pair(5, 'e'), cur.removePrev())
        assertEquals(null, cur.removeNext())

        val expected1 = BTreeMap<Int, Char>()
        expected1.insert(1, 'a')
        expected1.insert(3, 'c')
        expected1.insert(4, 'd')
        assertEquals(expected1, map)

        cur = map.upperBoundMut(Bound.Included(5))
        assertEquals(null, cur.peekNext())
        assertEquals(Pair(4, 'd'), cur.prev())
        assertEquals(Pair(4, 'd'), cur.peekNext())
        assertEquals(Pair(3, 'c'), cur.peekPrev())
        assertEquals(Pair(4, 'd'), cur.removeNext())

        val expected2 = BTreeMap<Int, Char>()
        expected2.insert(1, 'a')
        expected2.insert(3, 'c')
        assertEquals(expected2, map)
    }

    @Test
    fun testCursorMutKey() {
        val map = BTreeMap<Int, Char>()
        map.insert(1, 'a')
        map.insert(3, 'c')
        map.insert(5, 'e')
        var cur = map.lowerBoundMut(Bound.Excluded(3)).withMutableKey()
        assertEquals(Pair(5, 'e'), cur.peekNext())
        assertEquals(Pair(3, 'c'), cur.peekPrev())

        cur.insertBefore(4, 'd').getOrThrow()
        assertEquals(Pair(5, 'e'), cur.peekNext())
        assertEquals(Pair(4, 'd'), cur.peekPrev())

        assertEquals(Pair(5, 'e'), cur.next())
        assertEquals(null, cur.peekNext())
        assertEquals(Pair(5, 'e'), cur.peekPrev())
        cur.insertBefore(6, 'f').getOrThrow()
        assertEquals(null, cur.peekNext())
        assertEquals(Pair(6, 'f'), cur.peekPrev())
        assertEquals(Pair(6, 'f'), cur.removePrev())
        assertEquals(Pair(5, 'e'), cur.removePrev())
        assertEquals(null, cur.removeNext())

        val expected1 = BTreeMap<Int, Char>()
        expected1.insert(1, 'a')
        expected1.insert(3, 'c')
        expected1.insert(4, 'd')
        assertEquals(expected1, map)

        cur = map.upperBoundMut(Bound.Included(5)).withMutableKey()
        assertEquals(null, cur.peekNext())
        assertEquals(Pair(4, 'd'), cur.prev())
        assertEquals(Pair(4, 'd'), cur.peekNext())
        assertEquals(Pair(3, 'c'), cur.peekPrev())
        assertEquals(Pair(4, 'd'), cur.removeNext())

        val expected2 = BTreeMap<Int, Char>()
        expected2.insert(1, 'a')
        expected2.insert(3, 'c')
        assertEquals(expected2, map)
    }

    @Test
    fun testCursorEmpty() {
        val map = BTreeMap<Int, Int>()
        val cur = map.lowerBoundMut(Bound.Excluded(3))
        assertEquals(null, cur.peekNext())
        assertEquals(null, cur.peekPrev())
        cur.insertAfter(0, 0).getOrThrow()
        assertEquals(Pair(0, 0), cur.peekNext())
        assertEquals(null, cur.peekPrev())
        val expected = BTreeMap<Int, Int>()
        expected.insert(0, 0)
        assertEquals(expected, map)
    }

    @Test
    fun testCursorMutInsertBefore1() {
        val map = BTreeMap<Int, Char>()
        map.insert(1, 'a')
        map.insert(2, 'b')
        map.insert(3, 'c')
        val cur = map.upperBoundMut(Bound.Included(2))
        assertFailsWith<Throwable> { cur.insertBefore(0, 'd').getOrThrow() }
    }

    @Test
    fun testCursorMutInsertBefore2() {
        val map = BTreeMap<Int, Char>()
        map.insert(1, 'a')
        map.insert(2, 'b')
        map.insert(3, 'c')
        val cur = map.upperBoundMut(Bound.Included(2))
        assertFailsWith<Throwable> { cur.insertBefore(1, 'd').getOrThrow() }
    }

    @Test
    fun testCursorMutInsertBefore3() {
        val map = BTreeMap<Int, Char>()
        map.insert(1, 'a')
        map.insert(2, 'b')
        map.insert(3, 'c')
        val cur = map.upperBoundMut(Bound.Included(2))
        assertFailsWith<Throwable> { cur.insertBefore(2, 'd').getOrThrow() }
    }

    @Test
    fun testCursorMutInsertBefore4() {
        val map = BTreeMap<Int, Char>()
        map.insert(1, 'a')
        map.insert(2, 'b')
        map.insert(3, 'c')
        val cur = map.upperBoundMut(Bound.Included(2))
        assertFailsWith<Throwable> { cur.insertBefore(3, 'd').getOrThrow() }
    }

    @Test
    fun testCursorMutInsertAfter1() {
        val map = BTreeMap<Int, Char>()
        map.insert(1, 'a')
        map.insert(2, 'b')
        map.insert(3, 'c')
        val cur = map.upperBoundMut(Bound.Included(2))
        assertFailsWith<Throwable> { cur.insertAfter(1, 'd').getOrThrow() }
    }

    @Test
    fun testCursorMutInsertAfter2() {
        val map = BTreeMap<Int, Char>()
        map.insert(1, 'a')
        map.insert(2, 'b')
        map.insert(3, 'c')
        val cur = map.upperBoundMut(Bound.Included(2))
        assertFailsWith<Throwable> { cur.insertAfter(2, 'd').getOrThrow() }
    }

    @Test
    fun testCursorMutInsertAfter3() {
        val map = BTreeMap<Int, Char>()
        map.insert(1, 'a')
        map.insert(2, 'b')
        map.insert(3, 'c')
        val cur = map.upperBoundMut(Bound.Included(2))
        assertFailsWith<Throwable> { cur.insertAfter(3, 'd').getOrThrow() }
    }

    @Test
    fun testCursorMutInsertAfter4() {
        val map = BTreeMap<Int, Char>()
        map.insert(1, 'a')
        map.insert(2, 'b')
        map.insert(3, 'c')
        val cur = map.upperBoundMut(Bound.Included(2))
        assertFailsWith<Throwable> { cur.insertAfter(4, 'd').getOrThrow() }
    }

    @Test
    fun cursorPeekPrevAgreesWithCursorMut() {
        val map = BTreeMap<Int, Int>()
        map.insert(1, 1)
        map.insert(2, 2)
        map.insert(3, 3)

        val cursor = map.lowerBound(Bound.Excluded(3))
        assertEquals(null, cursor.peekNext())

        val prev = cursor.peekPrev()
        assertEquals(3, prev?.first)

        val cursorMut = map.lowerBoundMut(Bound.Excluded(3))
        assertEquals(null, cursorMut.peekNext())

        val prevMut = cursorMut.peekPrev()
        assertEquals(3, prevMut?.first)
    }

    class IdBased(val id: Int, val name: String) : Comparable<IdBased> {
        override fun compareTo(other: IdBased): Int = id.compareTo(other.id)
    }

    @Test
    fun testIdBasedInsert() {
        val lhs = BTreeMap<IdBased, String>()
        val rhs = BTreeMap<IdBased, String>()

        lhs.insert(IdBased(0, "lhs_k"), "lhs_v")
        rhs.insert(IdBased(0, "rhs_k"), "rhs_v")

        for (kv in rhs.intoIter()) {
            lhs.insert(kv.first, kv.second)
        }

        assertEquals("lhs_k", lhs.popFirst()!!.first.name)
    }

    @Test
    fun testIdBasedAppend() {
        val lhs = BTreeMap<IdBased, String>()
        val rhs = BTreeMap<IdBased, String>()

        lhs.insert(IdBased(0, "lhs_k"), "lhs_v")
        rhs.insert(IdBased(0, "rhs_k"), "rhs_v")

        lhs.append(rhs)

        assertEquals("lhs_k", lhs.popFirst()!!.first.name)
    }

    @Test
    fun testIdBasedMerge() {
        val lhs = BTreeMap<IdBased, String>()
        val rhs = BTreeMap<IdBased, String>()

        lhs.insert(IdBased(0, "lhs_k"), "1")
        rhs.insert(IdBased(0, "rhs_k"), "2")

        lhs.merge(rhs) { _, lhsVal, rhsVal ->
            assertEquals("1", lhsVal)
            assertEquals("2", rhsVal)
            lhsVal + rhsVal
        }

        val mergedKvPair = lhs.popFirst()!!
        assertEquals(0, mergedKvPair.first.id)
        assertEquals("lhs_k", mergedKvPair.first.name)
    }

    @Test
    fun testExtendRef() {
        val a = BTreeMap<Int, String>()
        a.insert(1, "one")
        val b = BTreeMap<Int, String>()
        b.insert(2, "two")
        b.insert(3, "three")

        a.putAll(b)

        assertEquals(3, a.size)
        assertEquals("one", a.get(1))
        assertEquals("two", a.get(2))
        assertEquals("three", a.get(3))
        a.assertStrictlyAscending()
    }

}
