// port-lint: source testing/crash_test.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree.testing

import io.github.kotlinmania.btree.BTreeCloneable
import io.github.kotlinmania.btree.BTreeDroppable

/**
 * Site at which a [CrashTestDummyRef] should panic. [Never] keeps a quiet
 * dummy; the others throw on the corresponding lifecycle event.
 */
internal enum class Panic {
    Never,
    InClone,
    InDrop,
    InQuery,
}

/**
 * A blueprint for crash test dummy instances that monitor particular events.
 * Some instances may be configured to panic at some point.
 * Events are [cloneRef] / [drop] or some anonymous [query].
 *
 * Crash test dummies are identified and ordered by an id, so they can be used
 * as keys in a [BTreeMap].
 */
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

/**
 * A reference into a [CrashTestDummy] that records every lifecycle call
 * on the dummy and panics at the configured [panic] site. Implements
 * [BTreeCloneable] and [BTreeDroppable] so the btree's clone and drop
 * paths route through the dummy's counters.
 */
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
