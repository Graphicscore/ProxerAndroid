package me.proxer.app.base

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.mockk

/**
 * Stubs [WorkManager.getWorkInfosForUniqueWork] to return a future of an empty list.
 *
 * This is the one thing that crashes a Room-backed chat screen. MessengerWorker.isRunning does
 * `workManager.getWorkInfosForUniqueWork(NAME).get().all { … }`; against the relaxed WorkManager mock, `.get()`
 * returns a relaxed value that ClassCastExceptions at `.all { }`. Returning an empty list makes `isRunning`
 * true (`emptyList().all {}` is vacuously true), so the `if (!isRunning) enqueueSynchronization()` guard skips.
 * The remaining enqueue* calls are harmless no-ops on the relaxed mock -- isRunning's cast is the only crash.
 *
 * No @After is needed: WorkManager is already cleared by InstrumentedTestBase.resetFakeAppModuleMocks. Chosen
 * over mockkObject(MessengerWorker.Companion), which leaks process-wide (the companion caches Koin deps in
 * by-safeInject lazies that reset never clears) and would need a matching unmockkObject in every @After.
 */
fun stubWorkManagerIdle(workManager: WorkManager) {
    val future = mockk<ListenableFuture<List<WorkInfo>>>(relaxed = true)

    every { future.get() } returns emptyList()
    every { workManager.getWorkInfosForUniqueWork(any()) } returns future
}
