package me.proxer.app.base

import io.mockk.every
import io.mockk.mockk
import me.proxer.library.ProxerCall
import me.proxer.library.ProxerException
import me.proxer.library.api.Endpoint
import me.proxer.library.api.PagingLimitEndpoint

/**
 * Creates a relaxed mock [ProxerCall] whose [ProxerCall.safeExecute] returns [value].
 * `clone()` is stubbed to return the mock itself, mirroring [me.proxer.app.util.rx.ProxerCallSingle].
 */
fun <T : Any> mockProxerCallSuccess(value: T): ProxerCall<T> {
    val call = mockk<ProxerCall<T>>(relaxed = true)

    every { call.clone() } returns call
    every { call.safeExecute() } returns value

    return call
}

/**
 * Creates a relaxed mock [ProxerCall] whose [ProxerCall.safeExecute] throws [exception].
 */
fun <T : Any> mockProxerCallError(
    exception: ProxerException = ProxerException(ProxerException.ErrorType.IO),
): ProxerCall<T> {
    val call = mockk<ProxerCall<T>>(relaxed = true)

    every { call.clone() } returns call
    every { call.safeExecute() } throws exception

    return call
}

/** Stubs this mocked [Endpoint] so `buildSingle()` emits [value]. */
fun <T : Any> Endpoint<T>.stubSuccess(value: T) {
    every { build() } returns mockProxerCallSuccess(value)
}

/** Stubs this mocked [Endpoint] so `buildSingle()` errors with [exception]. */
fun <T : Any> Endpoint<T>.stubError(
    exception: ProxerException = ProxerException(ProxerException.ErrorType.IO),
) {
    every { build() } returns mockProxerCallError(exception)
}

/**
 * Stubs this mocked [PagingLimitEndpoint] so calling `.page(x).limit(y).buildSingle()` (as done by
 * [me.proxer.app.base.PagedContentViewModel]) emits [value] regardless of the requested page/limit.
 */
fun <T : Any> PagingLimitEndpoint<T>.stubPagingSuccess(value: T) {
    every { page(any()) } returns this
    every { limit(any()) } returns this
    every { build() } returns mockProxerCallSuccess(value)
}

/**
 * Stubs this mocked [PagingLimitEndpoint] so calling `.page(x).limit(y).buildSingle()` errors with [exception].
 */
fun <T : Any> PagingLimitEndpoint<T>.stubPagingError(
    exception: ProxerException = ProxerException(ProxerException.ErrorType.IO),
) {
    every { page(any()) } returns this
    every { limit(any()) } returns this
    every { build() } returns mockProxerCallError(exception)
}

/**
 * Creates a relaxed mock [ProxerCall] for a *nullable* endpoint (`Endpoint<T?>`), whose
 * [ProxerCall.execute] returns [value]. Nullable endpoints resolve through
 * [me.proxer.app.util.rx.ProxerCallNullableSingle], which calls `execute()`, NOT `safeExecute()` —
 * unlike [mockProxerCallSuccess]/[mockProxerCallError], which are for the non-null `Endpoint<T>` path.
 * Stubbing `safeExecute()` on a mock driven by this path is inert (the real code never calls it).
 */
fun <T : Any> mockProxerCallNullableSuccess(value: T?): ProxerCall<T?> {
    val call = mockk<ProxerCall<T?>>(relaxed = true)

    every { call.clone() } returns call
    every { call.execute() } returns value

    return call
}

/** Creates a relaxed mock [ProxerCall] for a *nullable* endpoint whose [ProxerCall.execute] throws [exception]. */
fun <T : Any> mockProxerCallNullableError(
    exception: ProxerException = ProxerException(ProxerException.ErrorType.IO),
): ProxerCall<T?> {
    val call = mockk<ProxerCall<T?>>(relaxed = true)

    every { call.clone() } returns call
    every { call.execute() } throws exception

    return call
}

/** Stubs this mocked nullable [Endpoint] so `buildSingle()` emits [value] (defaults to `null`). */
fun <T : Any> Endpoint<T?>.stubNullableSuccess(value: T? = null) {
    every { build() } returns mockProxerCallNullableSuccess(value)
}

/** Stubs this mocked nullable [Endpoint] so `buildSingle()` errors with [exception]. */
fun <T : Any> Endpoint<T?>.stubNullableError(
    exception: ProxerException = ProxerException(ProxerException.ErrorType.IO),
) {
    every { build() } returns mockProxerCallNullableError(exception)
}
