package me.proxer.app.profile.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubNullableError
import me.proxer.app.base.stubNullableSuccess
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.toLocalSettings
import me.proxer.library.ProxerApi
import me.proxer.library.api.ucp.SetSettingsEndpoint
import me.proxer.library.api.ucp.SettingsEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class ProfileSettingsViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()

    private val fixtureSettings = LocalProfileSettings.default().toNonLocalSettings()
    private val otherFixtureSettings = fixtureSettings.copy(adInterval = 7)

    private lateinit var settingsEndpoint: SettingsEndpoint
    private lateinit var viewModel: ProfileSettingsViewModel

    @Before
    fun setup() {
        every { storageHelper.profileSettings } returns LocalProfileSettings.default()

        settingsEndpoint = mockk(relaxed = true)
        every { api.ucp.settings() } returns settingsEndpoint
        settingsEndpoint.stubSuccess(fixtureSettings)

        viewModel = ProfileSettingsViewModel()
    }

    @Test
    fun `construction triggers a refresh and populates data`() {
        assertEquals(fixtureSettings.toLocalSettings(), viewModel.data.value)
        assertNull(viewModel.error.value)

        verify { storageHelper.profileSettings = fixtureSettings.toLocalSettings() }
    }

    @Test
    fun `refresh sets error on failure and keeps previous data`() {
        val previousData = viewModel.data.value

        settingsEndpoint.stubError()
        viewModel.refresh()

        assertEquals(previousData, viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `refresh clears a previous error on success`() {
        settingsEndpoint.stubError()
        viewModel.refresh()
        assertNotNull(viewModel.error.value)

        settingsEndpoint.stubSuccess(otherFixtureSettings)
        viewModel.refresh()

        assertNull(viewModel.error.value)
        assertEquals(otherFixtureSettings.toLocalSettings(), viewModel.data.value)
    }

    @Test
    fun `update optimistically sets data and persists on success`() {
        val setSettingsEndpoint = mockk<SetSettingsEndpoint>(relaxed = true)
        every { api.ucp.setSettings(any()) } returns setSettingsEndpoint
        setSettingsEndpoint.stubNullableSuccess(listOf("profileVisibility"))

        val newData = otherFixtureSettings.toLocalSettings()
        viewModel.update(newData)

        assertEquals(newData, viewModel.data.value)
        assertNull(viewModel.updateError.value)

        verify { storageHelper.profileSettings = newData }
    }

    @Test
    fun `update sets updateError on failure but keeps the optimistic data`() {
        val setSettingsEndpoint = mockk<SetSettingsEndpoint>(relaxed = true)
        every { api.ucp.setSettings(any()) } returns setSettingsEndpoint
        setSettingsEndpoint.stubNullableError()

        val newData = otherFixtureSettings.toLocalSettings()
        viewModel.update(newData)

        assertEquals(newData, viewModel.data.value)
        assertNotNull(viewModel.updateError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `retryUpdate resends the current data`() {
        val ucpApi = api.ucp
        val setSettingsEndpoint = mockk<SetSettingsEndpoint>(relaxed = true)
        every { ucpApi.setSettings(any()) } returns setSettingsEndpoint
        setSettingsEndpoint.stubNullableSuccess(listOf("profileVisibility"))

        val newData = otherFixtureSettings.toLocalSettings()
        viewModel.update(newData)
        viewModel.retryUpdate()

        verify(exactly = 2) { ucpApi.setSettings(newData.toNonLocalSettings()) }
    }
}
