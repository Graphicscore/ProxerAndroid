package me.proxer.app.profile.history

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import me.proxer.app.auth.LocalUser
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.ucp.UcpHistoryEndpoint
import me.proxer.library.api.user.UserHistoryEndpoint
import me.proxer.library.entity.ucp.UcpHistoryEntry
import me.proxer.library.entity.user.UserHistoryEntry
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaLanguage
import me.proxer.library.enums.Medium
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

class HistoryViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private fun ucpEntry(id: String) = UcpHistoryEntry(
        id,
        "entry-$id",
        "Entry $id",
        MediaLanguage.GERMAN_SUB,
        Medium.ANIMESERIES,
        Category.ANIME,
        1,
        Date(),
    )

    private fun userEntry(id: String) = UserHistoryEntry(
        id,
        "entry-$id",
        "Entry $id",
        MediaLanguage.GERMAN_SUB,
        Medium.ANIMESERIES,
        Category.ANIME,
        1,
    )

    private fun mockUcpHistoryEndpoint(): UcpHistoryEndpoint {
        val endpoint = mockk<UcpHistoryEndpoint>(relaxed = true)

        every { api.ucp.history() } returns endpoint

        return endpoint
    }

    private fun mockUserHistoryEndpoint(): UserHistoryEndpoint {
        val endpoint = mockk<UserHistoryEndpoint>(relaxed = true)

        every { api.user.history("other-id", "othername") } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { storageHelper.user } returns LocalUser("token", "self-id", "Self", "image.png")
    }

    @Test
    fun `load sets data on success for the current user's own profile`() {
        val viewModel = HistoryViewModel("self-id", null)
        val endpoint = mockUcpHistoryEndpoint()
        val page = (0 until 50).map { ucpEntry("p0-$it") }
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load uses the user endpoint for another user's profile`() {
        val viewModel = HistoryViewModel("other-id", "othername")
        val endpoint = mockUserHistoryEndpoint()
        val page = (0 until 50).map { userEntry("p0-$it") }
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val viewModel = HistoryViewModel("self-id", null)
        val endpoint = mockUcpHistoryEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful and failed loads`() {
        val viewModel = HistoryViewModel("self-id", null)
        val endpoint = mockUcpHistoryEndpoint()

        endpoint.stubPagingSuccess(listOf(ucpEntry("a")))
        viewModel.load()
        assertFalse(viewModel.isLoading.value == true)

        endpoint.stubPagingError()
        viewModel.load()
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val viewModel = HistoryViewModel("self-id", null)
        val endpoint = mockUcpHistoryEndpoint()
        val page = (0 until 50).map { ucpEntry("p0-$it") }
        endpoint.stubPagingSuccess(page)
        viewModel.load()
        assertEquals(50, viewModel.data.value?.size)

        val secondPage = (0 until 50).map { ucpEntry("p1-$it") }
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val viewModel = HistoryViewModel("self-id", null)
        val endpoint = mockUcpHistoryEndpoint()
        val lastPage = listOf(ucpEntry("last-0"), ucpEntry("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        endpoint.stubPagingSuccess(listOf(ucpEntry("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(2, viewModel.data.value?.size)
    }

    @Test
    fun `refresh merges page-0 results and a refresh error sets refreshError`() {
        val viewModel = HistoryViewModel("self-id", null)
        val endpoint = mockUcpHistoryEndpoint()
        val original = (0 until 50).map { ucpEntry("p0-$it") }
        endpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(50, viewModel.data.value?.size)

        val refreshed = listOf(ucpEntry("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()
        assertEquals(refreshed.map { it.id } + listOf(original.first().id), viewModel.data.value?.map { it.id })

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `a login state change triggers a reload`() {
        val loginSubject = PublishSubject.create<Boolean>()
        every { storageHelper.isLoggedInObservable } returns loginSubject

        val viewModel = HistoryViewModel("self-id", null)
        val endpoint = mockUcpHistoryEndpoint()
        val firstPage = listOf(ucpEntry("first"))
        endpoint.stubPagingSuccess(firstPage)
        viewModel.load()
        assertEquals(firstPage.map { it.id }, viewModel.data.value?.map { it.id })

        val secondPage = listOf(ucpEntry("second"))
        endpoint.stubPagingSuccess(secondPage)

        loginSubject.onNext(true)

        assertEquals(secondPage.map { it.id }, viewModel.data.value?.map { it.id })
    }
}
