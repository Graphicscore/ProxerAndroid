package com.gojuno.koptional

sealed class Optional<out T>

data class Some<out T>(val value: T) : Optional<T>()

object None : Optional<Nothing>()

fun <T : Any> T?.toOptional(): Optional<T> = if (this != null) Some(this) else None

val <T : Any> Optional<T>.valueOrNull: T?
    get() = when (this) {
        is Some -> value
        None -> null
    }

fun <T : Any> Optional<T>.toNullable(): T? = valueOrNull

fun <A : Any, B : Any> Optional<A>.map(body: (A) -> B): Optional<B> = when (this) {
    is Some -> Some(body(value))
    None -> None
}

fun <A : Any, B : Any> Optional<A>.flatMap(body: (A) -> Optional<B>): Optional<B> = when (this) {
    is Some -> body(value)
    None -> None
}

fun <A : Any> Optional<A>.filter(predicate: (A) -> Boolean): Optional<A> = when (this) {
    is Some -> if (predicate(value)) this else None
    None -> None
}
