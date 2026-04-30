package io.github.kotlinmania.btree.testing

class Governor {
    var flipped = false
    fun flip() {
        flipped = !flipped
    }

    companion object {
        fun new(): Governor = Governor()
    }
}

class Governed(val id: Int, val gov: Governor) : Comparable<Governed> {
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
}

enum class Panic {
    Never,
    InClone,
    InDrop,
    InQuery
}

class CrashTestDummy(val id: Int) {
    private var queried = 0
    private var dropped = 0
    private var cloned = 0

    fun spawn(panic: Panic): CrashTestDummyRef {
        return CrashTestDummyRef(this, panic)
    }

    fun queried(): Int = queried
    fun dropped(): Int = dropped
    fun cloned(): Int = cloned

    internal fun incQueried() { queried++ }
    internal fun incDropped() { dropped++ }
    internal fun incCloned() { cloned++ }
}

class CrashTestDummyRef(val dummy: CrashTestDummy, val panic: Panic) : Comparable<CrashTestDummyRef> {
    override fun compareTo(other: CrashTestDummyRef): Int {
        if (panic == Panic.InQuery) {
            throw Exception("panic in query")
        }
        return dummy.id.compareTo(other.dummy.id)
    }

    fun query(v: Boolean): Boolean {
        if (panic == Panic.InQuery) {
            throw Exception("panic in query")
        }
        dummy.incQueried()
        return v
    }

    // Kotlin does not have Drop; these tests (which rely on drop tracking)
    // might just be empty or manual in the port. We simulate by doing nothing
    // automatically, and test code must call drop() manually if they want to track it.
    fun drop() {
        if (panic == Panic.InDrop) {
            throw Exception("panic in drop")
        }
        dummy.incDropped()
    }

    fun cloneRef(): CrashTestDummyRef {
        if (panic == Panic.InClone) {
            throw Exception("panic in clone")
        }
        dummy.incCloned()
        return CrashTestDummyRef(dummy, panic)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CrashTestDummyRef) return false
        return dummy.id == other.dummy.id
    }

    override fun hashCode(): Int = dummy.id.hashCode()
}

sealed class Cyclic3 : Comparable<Cyclic3> {
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
