package me.proxer.app.base

import io.mockk.every
import io.mockk.mockk
import me.proxer.library.ProxerCall
import me.proxer.library.ProxerException

/**
 * Mocks a [ProxerCall] returning [value] from a NON-NULL endpoint (`Endpoint<T>`).
 *
 * `clone()` must be stubbed to return the same mock: `ProxerCallSingle.subscribeActual` clones the call before
 * executing it, so an unstubbed relaxed `clone()` hands back a DIFFERENT mock whose `safeExecute()` is not
 * stubbed, and the single never emits. This is the single most common way these tests fail silently.
 *
 * Non-null endpoints resolve through `ProxerCallSingle`, which calls `safeExecute()`. Nullable endpoints
 * (`Endpoint<T?>`) resolve through `ProxerCallNullableSingle`, which calls `execute()` -- use
 * [mockProxerNullableCall] for those, or the stub is inert.
 */
fun <T : Any> mockProxerCall(value: T): ProxerCall<T> {
    val call = mockk<ProxerCall<T>>(relaxed = true)

    every { call.clone() } returns call
    every { call.safeExecute() } returns value

    return call
}

/**
 * Mocks a [ProxerCall] from a non-null endpoint that fails with [exception]. See [mockProxerCall] for why
 * `clone()` is stubbed.
 */
fun <T : Any> mockProxerErrorCall(
    exception: ProxerException = ProxerException(ProxerException.ErrorType.IO),
): ProxerCall<T> {
    val call = mockk<ProxerCall<T>>(relaxed = true)

    every { call.clone() } returns call
    every { call.safeExecute() } throws exception

    return call
}

/**
 * Mocks a [ProxerCall] returning [value] from a NULLABLE endpoint (`Endpoint<T?>`).
 *
 * These resolve through `ProxerCallNullableSingle`, which calls `execute()` rather than `safeExecute()` --
 * stubbing `safeExecute()` on this path is inert and the call never resolves.
 */
fun <T : Any> mockProxerNullableCall(value: T? = null): ProxerCall<T?> {
    val call = mockk<ProxerCall<T?>>(relaxed = true)

    every { call.clone() } returns call
    every { call.execute() } returns value

    return call
}
