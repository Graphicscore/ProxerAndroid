package me.proxer.app.info.translatorgroup

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.list.TranslatorGroupProjectListEndpoint
import me.proxer.library.entity.list.TranslatorGroupProject
import me.proxer.library.enums.FskConstraint
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import me.proxer.library.enums.ProjectState
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

class TranslatorGroupProjectViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: TranslatorGroupProjectViewModel

    private fun project(id: String) = TranslatorGroupProject(
        id,
        "Project $id",
        setOf("Action"),
        setOf(FskConstraint.FSK_0),
        Medium.ANIMESERIES,
        ProjectState.ONGOING,
        MediaState.FINISHED,
        10,
        2,
    )

    private fun fullPage(prefix: String) = (0 until 30).map { project("$prefix-$it") }

    private fun mockTranslatorGroupEndpoint(): TranslatorGroupProjectListEndpoint {
        val endpoint = mockk<TranslatorGroupProjectListEndpoint>(relaxed = true)

        every { api.list.translatorGroupProjectList("group-1") } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        viewModel = TranslatorGroupProjectViewModel("group-1")
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockTranslatorGroupEndpoint()
        val page = fullPage("p0")
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockTranslatorGroupEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockTranslatorGroupEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockTranslatorGroupEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockTranslatorGroupEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val secondPage = fullPage("p1")
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockTranslatorGroupEndpoint()
        val lastPage = listOf(project("last-0"), project("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(lastPage, viewModel.data.value)

        endpoint.stubPagingSuccess(listOf(project("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(lastPage, viewModel.data.value)
    }

    @Test
    fun `refresh merges page-0 results with existing data, new items first`() {
        val endpoint = mockTranslatorGroupEndpoint()
        val original = fullPage("p0")
        endpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val refreshed = listOf(project("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()

        val expected = refreshed + listOf(original.first())
        assertEquals(expected, viewModel.data.value)
    }

    @Test
    fun `error during refresh with existing data sets refreshError, not error`() {
        val endpoint = mockTranslatorGroupEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
        assertEquals(30, viewModel.data.value?.size)
    }

    @Test
    fun `includeHentai reflects age-restricted media allowance and login state`() {
        val endpoint = mockTranslatorGroupEndpoint()
        endpoint.stubPagingSuccess(emptyList())

        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns true
        every { storageHelper.isLoggedIn } returns true
        viewModel.load()
        verify { endpoint.includeHentai(true) }

        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
        viewModel.load()
        verify { endpoint.includeHentai(false) }
    }
}
