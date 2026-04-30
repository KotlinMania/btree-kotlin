package io.github.kotlinmania.core.ptr

value class NonNull<T>(private val value: T) {
    companion object {
        fun <T> from(t: T): NonNull<T> = NonNull(t)
    }

    fun asPtr(): T = value
}
