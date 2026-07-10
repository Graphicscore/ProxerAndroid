package me.proxer.app.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.profile.settings.LocalProfileSettings
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.toLocalSettings
import me.proxer.library.ProxerApi
import me.proxer.library.ProxerException
import me.proxer.library.api.ucp.SettingsEndpoint
import me.proxer.library.api.user.LoginEndpoint
import me.proxer.library.entity.user.User
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

class LoginViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: LoginViewModel

    private val fixtureUser = User("42", "image.jpg", false, false, "token123")
    private val fixtureSettings = LocalProfileSettings.default().toNonLocalSettings()

    @Before
    fun setup() {
        every { preferenceHelper.isTwoFactorAuthenticationEnabled } returns false

        val settingsEndpoint = mockk<SettingsEndpoint>(relaxed = true)
        every { api.ucp.settings() } returns settingsEndpoint
        settingsEndpoint.stubSuccess(fixtureSettings)

        viewModel = LoginViewModel()
    }

    private fun stubLogin(): LoginEndpoint {
        val loginEndpoint = mockk<LoginEndpoint>(relaxed = true)

        every { api.user.login(any(), any()) } returns loginEndpoint
        every { loginEndpoint.secretKey(any()) } returns loginEndpoint

        return loginEndpoint
    }

    @Test
    fun `init seeds isTwoFactorAuthenticationEnabled from preferenceHelper`() {
        every { preferenceHelper.isTwoFactorAuthenticationEnabled } returns true

        val freshViewModel = LoginViewModel()

        assertTrue(freshViewModel.isTwoFactorAuthenticationEnabled.value == true)
    }

    @Test
    fun `login sets success and stores user and settings on success`() {
        val loginEndpoint = stubLogin()
        loginEndpoint.stubSuccess(fixtureUser)

        viewModel.login("someuser", "hunter2", null)

        assertEquals(Unit, viewModel.success.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)

        verify { storageHelper.user = LocalUser("token123", "42", "someuser", "image.jpg") }
        verify { storageHelper.profileSettings = fixtureSettings.toLocalSettings() }
    }

    @Test
    fun `login clears temporaryToken after completion`() {
        val loginEndpoint = stubLogin()
        loginEndpoint.stubSuccess(fixtureUser)

        viewModel.login("someuser", "hunter2", null)

        verify { storageHelper.temporaryToken = "token123" }
        verify { storageHelper.temporaryToken = null }
    }

    @Test
    fun `login sets error on generic failure`() {
        val loginEndpoint = stubLogin()
        loginEndpoint.stubError()

        viewModel.login("someuser", "wrong", null)

        assertNull(viewModel.success.value)
        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)
        assertFalse(viewModel.isTwoFactorAuthenticationEnabled.value == true)
    }

    @Test
    fun `login enables two-factor flag when server requires it`() {
        val loginEndpoint = stubLogin()
        loginEndpoint.stubError(
            ProxerException(
                ProxerException.ErrorType.SERVER,
                ProxerException.ServerErrorType.USER_2FA_SECRET_REQUIRED,
            ),
        )

        viewModel.login("someuser", "hunter2", null)

        assertNotNull(viewModel.error.value)
        assertTrue(viewModel.isTwoFactorAuthenticationEnabled.value == true)
        verify { preferenceHelper.isTwoFactorAuthenticationEnabled = true }
    }

    @Test
    fun `login is ignored while already loading`() {
        viewModel.isLoading.value = true

        viewModel.login("someuser", "hunter2", null)

        verify(exactly = 0) { api.user.login(any(), any()) }
    }

    @Test
    fun `login passes the secretKey to the endpoint`() {
        val loginEndpoint = stubLogin()
        loginEndpoint.stubSuccess(fixtureUser)

        viewModel.login("someuser", "hunter2", "123456")

        verify { loginEndpoint.secretKey("123456") }
    }
}
