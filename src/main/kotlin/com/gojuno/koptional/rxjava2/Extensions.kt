package com.gojuno.koptional.rxjava2

import com.gojuno.koptional.None
import com.gojuno.koptional.Optional
import com.gojuno.koptional.Some
import com.gojuno.koptional.toOptional
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single

fun <T : Any> Observable<Optional<T>>.filterSome(): Observable<T> = filter { it is Some }.map { (it as Some<T>).value }

fun <T : Any, R : Any> Observable<Optional<T>>.mapSome(body: (T) -> R): Observable<Optional<R>> =
    map { if (it is Some) Some(body(it.value)) else None }

fun <T : Any> Observable<Optional<T>>.filterNone(): Observable<Optional<T>> = filter { it is None }

fun <T : Any> Observable<T>.toOptional(): Observable<Optional<T>> = map { it.toOptional() }

fun <T : Any> Flowable<Optional<T>>.filterSome(): Flowable<T> = filter { it is Some }.map { (it as Some<T>).value }

fun <T : Any, R : Any> Flowable<Optional<T>>.mapSome(body: (T) -> R): Flowable<Optional<R>> =
    map { if (it is Some) Some(body(it.value)) else None }

fun <T : Any> Flowable<Optional<T>>.filterNone(): Flowable<Optional<T>> = filter { it is None }

fun <T : Any> Flowable<T>.toOptional(): Flowable<Optional<T>> = map { it.toOptional() }

fun <T : Any> Single<Optional<T>>.filterSome(): Maybe<T> = toMaybe().filter { it is Some }.map { (it as Some<T>).value }

fun <T : Any> Single<T>.toOptional(): Single<Optional<T>> = map { it.toOptional() }

fun <T : Any> Maybe<Optional<T>>.filterSome(): Maybe<T> = filter { it is Some }.map { (it as Some<T>).value }

fun <T : Any> Maybe<T>.toOptional(): Maybe<Optional<T>> = map { it.toOptional() }
