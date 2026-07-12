package me.proxer.app.bookmark

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubNullableError
import me.proxer.app.base.stubNullableSuccess
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.exception.NotLoggedInException
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.Validators
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.ucp.BookmarksEndpoint
import me.proxer.library.api.ucp.DeleteBookmarkEndpoint
import me.proxer.library.entity.ucp.Bookmark
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaLanguage
import me.proxer.library.enums.MediaState
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

class BookmarkViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()
    private val validators: Validators by inject()

    private lateinit var viewModel: BookmarkViewModel

    private fun bookmark(id: String) = Bookmark(
        id,
        "entry-$id",
        Category.ANIME,
        "Bookmark $id",
        3,
        MediaLanguage.GERMAN_SUB,
        Medium.ANIMESERIES,
        MediaState.FINISHED,
        "Chapter $id",
        true,
    )

    private fun fullPage(prefix: String) = (0 until 30).map { bookmark("$prefix-$it") }

    private fun mockBookmarksEndpoint(): BookmarksEndpoint {
        val endpoint = mockk<BookmarksEndpoint>(relaxed = true)

        every { api.ucp.bookmarks() } returns endpoint
        every { endpoint.name(any()) } returns endpoint
        every { endpoint.category(any()) } returns endpoint
        every { endpoint.filterAvailable(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        viewModel = BookmarkViewModel(null, null, false)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockBookmarksEndpoint()
        val page = fullPage("p0")
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockBookmarksEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `load sets login-required error when validators rejects a logged-out user`() {
        every { validators.validateLogin() } throws NotLoggedInException()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.LOGIN, viewModel.error.value?.buttonAction)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockBookmarksEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockBookmarksEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockBookmarksEndpoint()
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
    fun `second load appends new page, advances the page number and dedups by id`() {
        val endpoint = mockBookmarksEndpoint()
        val pageSlot = slot<Int>()

        val firstPage = fullPage("p0")
        endpoint.stubPagingSuccess(firstPage)
        every { endpoint.page(capture(pageSlot)) } returns endpoint

        viewModel.load()
        assertEquals(0, pageSlot.captured)
        assertEquals(30, viewModel.data.value?.size)

        val secondPage = listOf(bookmark("p1-0"), bookmark("p1-1"))
        endpoint.stubPagingSuccess(secondPage)
        every { endpoint.page(capture(pageSlot)) } returns endpoint

        viewModel.loadIfPossible()

        assertEquals(1, pageSlot.captured)
        assertEquals(firstPage + secondPage, viewModel.data.value)
    }

    @Test
    fun `load dedups entries that share an id within a single page`() {
        val endpoint = mockBookmarksEndpoint()
        val duplicate = bookmark("dup")
        endpoint.stubPagingSuccess(listOf(duplicate, bookmark("unique"), duplicate.copy()))

        viewModel.load()

        assertEquals(listOf("dup", "unique"), viewModel.data.value?.map { it.id })
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockBookmarksEndpoint()
        val lastPage = listOf(bookmark("last-0"), bookmark("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(lastPage, viewModel.data.value)

        endpoint.stubPagingSuccess(listOf(bookmark("should-not-appear")))

        viewModel.loadIfPossible()

        assertEquals(lastPage, viewModel.data.value)
    }

    @Test
    fun `refresh behaves like reload and clears data first, so errors never become refreshError`() {
        val endpoint = mockBookmarksEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        endpoint.stubPagingError()

        viewModel.refresh()

        // BookmarkViewModel overrides refresh() as reload(), which nulls data before the
        // failing load runs, so PagedViewModel's "page 0 with existing data" refreshError
        // branch is never reachable through this VM's public API.
        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
        assertNull(viewModel.refreshError.value)
    }

    @Test
    fun `category change triggers reload`() {
        val endpoint = mockBookmarksEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val newPage = listOf(bookmark("manga-0"))
        endpoint.stubPagingSuccess(newPage)

        viewModel.category = Category.MANGA

        assertEquals(newPage, viewModel.data.value)
    }

    @Test
    fun `addItemToDelete removes item from data and sets undoData on success`() {
        val endpoint = mockBookmarksEndpoint()
        val items = listOf(bookmark("a"), bookmark("b"))
        endpoint.stubPagingSuccess(items)
        viewModel.load()

        val deleteEndpoint = mockk<DeleteBookmarkEndpoint>(relaxed = true)
        every { api.ucp.deleteBookmark("a") } returns deleteEndpoint
        deleteEndpoint.stubNullableSuccess(null)

        viewModel.addItemToDelete(items[0])

        assertEquals(listOf(items[1]), viewModel.data.value)
        assertNotNull(viewModel.undoData.value)
    }

    @Test
    fun `addItemToDelete sets itemDeletionError on failure`() {
        val endpoint = mockBookmarksEndpoint()
        val items = listOf(bookmark("a"))
        endpoint.stubPagingSuccess(items)
        viewModel.load()

        val deleteEndpoint = mockk<DeleteBookmarkEndpoint>(relaxed = true)
        every { api.ucp.deleteBookmark("a") } returns deleteEndpoint
        deleteEndpoint.stubNullableError()

        viewModel.addItemToDelete(items[0])

        assertNotNull(viewModel.itemDeletionError.value)
        assertEquals(items, viewModel.data.value)
    }
}
