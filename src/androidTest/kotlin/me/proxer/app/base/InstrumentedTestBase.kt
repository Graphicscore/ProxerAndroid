package me.proxer.app.base

import androidx.work.WorkManager
import io.mockk.clearMocks
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.chat.prv.sync.MessengerDatabase
import me.proxer.app.media.TagDao
import me.proxer.app.util.Validators
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.safeInject
import me.proxer.library.ProxerApi
import okhttp3.OkHttpClient
import org.junit.After
import org.koin.core.context.GlobalContext

/**
 * Base class for instrumented Compose UI tests that rely on [fakeAppModule]'s mocked Koin singletons.
 *
 * [TestApplication] calls `startKoin` once per instrumentation process, so every test class runs against the
 * SAME [fakeAppModule] mock instances -- they are not recreated per test class or per test method. Any
 * `every {}` stub registered on one of them therefore survives past the test that registered it and can leak
 * into unrelated, later-running tests.
 *
 * Extend this class instead of injecting [api]/[storageHelper]/[preferenceHelper]/[validators] again; the
 * [resetFakeAppModuleMocks] `@After` clears stubs on every mock [fakeAppModule] provides (not just the four
 * exposed here) between tests, so each test starts from a clean relaxed-mock baseline regardless of what an
 * earlier test stubbed. `RxBus` is intentionally excluded -- it's a real instance, not a mock, so MockK has
 * nothing to clear on it. `clearAllMocks()` is deliberately avoided since it would reset every mock in the
 * process, including ones used by other, possibly concurrently-running test classes.
 */
open class InstrumentedTestBase {

    protected val api: ProxerApi by safeInject()
    protected val storageHelper: StorageHelper by safeInject()
    protected val preferenceHelper: PreferenceHelper by safeInject()
    protected val validators: Validators by safeInject()

    @After
    fun resetFakeAppModuleMocks() {
        val koin = GlobalContext.get()

        clearMocks(
            api,
            storageHelper,
            preferenceHelper,
            validators,
            koin.get<MessengerDao>(),
            koin.get<OkHttpClient>(),
            koin.get<TagDao>(),
            koin.get<WorkManager>(),
            koin.get<MessengerDatabase>(),
            answers = false,
        )
    }
}
