package me.proxer.app.news

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
import me.proxer.library.api.notifications.NewsEndpoint
import me.proxer.library.entity.notifications.NewsArticle
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
import java.util.Date

class NewsViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: NewsViewModel

    private fun article(id: String, date: Date = Date()) = NewsArticle(
        id,
        date,
        "Description $id",
        "image.png",
        "Subject $id",
        10,
        "thread-$id",
        "author-$id",
        "Author $id",
        0,
        "cat-1",
        "Category",
    )

    private fun fullPage(prefix: String) = (0 until 15).map { article("$prefix-$it") }

    private fun mockNewsEndpoint(): NewsEndpoint {
        val endpoint = mockk<NewsEndpoint>(relaxed = true)

        every { api.notifications.news() } returns endpoint
        every { endpoint.markAsRead(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        viewModel = NewsViewModel()
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockNewsEndpoint()
        val page = fullPage("p0")
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockNewsEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockNewsEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockNewsEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockNewsEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(15, viewModel.data.value?.size)

        val secondPage = fullPage("p1")
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockNewsEndpoint()
        val lastPage = listOf(article("last-0"), article("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(lastPage, viewModel.data.value)

        endpoint.stubPagingSuccess(listOf(article("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(lastPage, viewModel.data.value)
    }

    @Test
    fun `refresh merges page-0 results and a refresh error sets refreshError`() {
        val endpoint = mockNewsEndpoint()
        val original = fullPage("p0")
        endpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(15, viewModel.data.value?.size)

        val refreshed = listOf(article("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()
        assertEquals(refreshed + listOf(original.first()), viewModel.data.value)

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `markAsRead is true on page 0 and false beyond it`() {
        val endpoint = mockNewsEndpoint()
        val firstPage = fullPage("p0")
        endpoint.stubPagingSuccess(firstPage)

        viewModel.load()
        verify { endpoint.markAsRead(true) }

        val secondPage = listOf(article("p1-0"))
        endpoint.stubPagingSuccess(secondPage)
        viewModel.loadIfPossible()

        verify { endpoint.markAsRead(false) }
    }

    @Test
    fun `lastNewsDate is set from the first article only on a page-0 success`() {
        val endpoint = mockNewsEndpoint()
        val firstDate = Date(1_700_000_000_000L)
        val firstPage = listOf(article("p0-0", firstDate)) + fullPage("p0").drop(1)
        endpoint.stubPagingSuccess(firstPage)

        viewModel.load()

        verify {
            preferenceHelper.lastNewsDate = firstDate.toInstant().let {
                org.threeten.bp.Instant.ofEpochMilli(
                    it.toEpochMilli(),
                )
            }
        }
    }
}
