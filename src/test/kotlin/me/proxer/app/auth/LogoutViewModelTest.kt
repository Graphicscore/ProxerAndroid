package me.proxer.app.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubNullableError
import me.proxer.app.base.stubNullableSuccess
import me.proxer.library.ProxerApi
import me.proxer.library.api.user.LogoutEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class LogoutViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()

    private lateinit var viewModel: LogoutViewModel

    @Before
    fun setup() {
        viewModel = LogoutViewModel()
    }

    @Test
    fun `logout sets success on success`() {
        val logoutEndpoint = mockk<LogoutEndpoint>(relaxed = true)
        every { api.user.logout() } returns logoutEndpoint
        logoutEndpoint.stubNullableSuccess(Unit)

        viewModel.logout()

        assertEquals(Unit, viewModel.success.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `logout sets error on failure`() {
        val logoutEndpoint = mockk<LogoutEndpoint>(relaxed = true)
        every { api.user.logout() } returns logoutEndpoint
        logoutEndpoint.stubNullableError()

        viewModel.logout()

        assertNull(viewModel.success.value)
        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `logout is ignored while already loading`() {
        viewModel.isLoading.value = true

        viewModel.logout()

        verify(exactly = 0) { api.user.logout() }
    }
}
