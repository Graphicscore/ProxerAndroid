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
