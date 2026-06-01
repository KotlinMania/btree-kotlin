// port-lint: source testing/crash_test.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree.testing

import io.github.kotlinmania.btree.BTreeCloneable
import io.github.kotlinmania.btree.BTreeDroppable

/**
 * A blueprint for crash test dummy instances that monitor particular events.
 * Some instances may be configured to panic at some point.
 * Events are `clone`, `drop` or some anonymous `query`.
 *
 * Crash test dummies are identified and ordered by an id, so they can be used
 * as keys in a sorted map.
 */
internal class CrashTestDummy(
    val id: Int,
) {
    private var clonedCount: Int = 0
    private var droppedCount: Int = 0
    private var queriedCount: Int = 0

    /**
     * Creates an instance of a crash test dummy that records what events
     * it experiences and optionally panics.
     */
    fun spawn(panic: Panic): Instance = Instance(this, panic)

    /** Returns how many times instances of the dummy have been cloned. */
    fun cloned(): Int = clonedCount

    /** Returns how many times instances of the dummy have been dropped. */
    fun dropped(): Int = droppedCount

    /** Returns how many times instances of the dummy have had their query member invoked. */
    fun queried(): Int = queriedCount

    internal fun incCloned() {
        clonedCount++
    }

    internal fun incDropped() {
        droppedCount++
    }

    internal fun incQueried() {
        queriedCount++
    }

    companion object {
        /** Creates a crash test dummy design. The [id] determines order and equality of instances. */
        fun new(id: Int): CrashTestDummy = CrashTestDummy(id)
    }
}

internal enum class Panic {
    Never,
    InClone,
    InDrop,
    InQuery,
}

/**
 * An instance spawned from a [CrashTestDummy]. Records every lifecycle
 * call against its origin dummy and optionally panics at the configured
 * [Panic] site. Implements [BTreeCloneable] and [BTreeDroppable] so the
 * btree's clone and drop paths route through the dummy's counters
 * deterministically.
 */
internal class Instance(
    private val origin: CrashTestDummy,
    private val panic: Panic,
) : Comparable<Instance>,
    BTreeCloneable,
    BTreeDroppable {
    fun id(): Int = origin.id

    /** Some anonymous query, the result of which is already given. */
    fun <R> query(result: R): R {
        origin.incQueried()
        if (panic == Panic.InQuery) {
            throw Exception("panic in `query`")
        }
        return result
    }

    fun clone(): Instance {
        origin.incCloned()
        if (panic == Panic.InClone) {
            throw Exception("panic in `clone`")
        }
        return Instance(origin, Panic.Never)
    }

    fun drop() {
        origin.incDropped()
        if (panic == Panic.InDrop) {
            throw Exception("panic in `drop`")
        }
    }

    override fun cloneForBtree() {
        clone()
    }

    override fun dropForBtree() {
        drop()
    }

    override fun compareTo(other: Instance): Int = id().compareTo(other.id())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Instance) return false
        return id() == other.id()
    }

    override fun hashCode(): Int = id().hashCode()

    override fun toString(): String = "Instance(${id()})"
}
