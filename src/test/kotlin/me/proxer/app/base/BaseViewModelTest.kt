package me.proxer.app.base

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.reactivex.Observable
import io.reactivex.Single
import me.proxer.app.exception.NotLoggedInException
import me.proxer.app.util.Validators
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.io.IOException

class BaseViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()
    private val validators: Validators by inject()

    private lateinit var viewModel: TestViewModel

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        viewModel = TestViewModel()
    }

    @Test
    fun `load sets data on success`() {
        viewModel.nextResponse = Single.just("hello")
        viewModel.load()
        assertEquals("hello", viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        viewModel.nextResponse = Single.error(IOException("net error"))
        viewModel.load()
        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        viewModel.nextResponse = Single.just("ok")
        viewModel.load()
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        viewModel.nextResponse = Single.error(IOException("fail"))
        viewModel.load()
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error before new load completes`() {
        viewModel.nextResponse = Single.just("first")
        viewModel.load()
        assertEquals("first", viewModel.data.value)

        // Stall the reload with Single.never() so we can observe the cleared state
        viewModel.nextResponse = Single.never()
        viewModel.reload()
        // Data and error should be null immediately after reload() is called, before load completes
        assertNull(viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `reload loads new data after clearing`() {
        viewModel.nextResponse = Single.just("first")
        viewModel.load()
        assertEquals("first", viewModel.data.value)

        viewModel.nextResponse = Single.just("second")
        viewModel.reload()
        assertEquals("second", viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `loadIfPossible skips when already loading`() {
        // Use Single.never() so load never completes — isLoading stays true
        viewModel.nextResponse = Single.never()
        viewModel.load()
        // At this point isLoading should be true (never completed)
        assertTrue(viewModel.isLoading.value == true)

        // Second loadIfPossible should not change state
        viewModel.nextResponse = Single.just("should not land")
        viewModel.loadIfPossible()
        // data still null — load was skipped
        assertNull(viewModel.data.value)
    }

    @Test
    fun `load clears previous error`() {
        viewModel.nextResponse = Single.error(IOException("fail"))
        viewModel.load()
        assertNotNull(viewModel.error.value)

        viewModel.nextResponse = Single.just("ok")
        viewModel.load()
        assertNull(viewModel.error.value)
        assertEquals("ok", viewModel.data.value)
    }

    @Test
    fun `load surfaces a NotLoggedInException from validate() as a login-required error`() {
        every { validators.validateLogin() } throws NotLoggedInException()
        viewModel.isLoginRequired = true

        viewModel.nextResponse = Single.just("ignored")
        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    // Inner fake ViewModel — override dataSingle via var so tests can control responses.
    // isLoginRequired is overridden to false so that the isLoggedInObservable subscription
    // in init does not trigger spurious reload() calls during tests.
    private inner class TestViewModel : BaseViewModel<String>() {
        public override var isLoginRequired = false
        var nextResponse: Single<String> = Single.never()
        override val dataSingle: Single<String>
            get() = Single.fromCallable { validate() }.flatMap { nextResponse }
    }
}
