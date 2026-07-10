package me.proxer.app.info.industry

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.info.IndustryEndpoint
import me.proxer.library.entity.info.Industry
import me.proxer.library.enums.Country
import me.proxer.library.enums.IndustryType
import okhttp3.HttpUrl.Companion.toHttpUrl
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

class IndustryInfoViewModelTest : KoinTest {

    private companion object {
        private const val INDUSTRY_ID = "321"
    }

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val endpoint = mockk<IndustryEndpoint>(relaxed = true)

    private lateinit var viewModel: IndustryInfoViewModel

    private val firstIndustry = Industry(
        id = INDUSTRY_ID,
        name = "Test Studio",
        type = IndustryType.STUDIO,
        country = Country.JAPAN,
        link = "https://proxer.me".toHttpUrl(),
        description = "A description",
    )

    private val secondIndustry = firstIndustry.copy(name = "Updated Studio")

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { api.info.industry(INDUSTRY_ID) } returns endpoint

        viewModel = IndustryInfoViewModel(INDUSTRY_ID)
    }

    @Test
    fun `load sets data on success`() {
        endpoint.stubSuccess(firstIndustry)

        viewModel.load()

        assertEquals(firstIndustry, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        endpoint.stubSuccess(firstIndustry)

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        endpoint.stubSuccess(firstIndustry)
        viewModel.load()
        assertEquals(firstIndustry, viewModel.data.value)

        endpoint.stubSuccess(secondIndustry)
        viewModel.reload()

        assertEquals(secondIndustry, viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
