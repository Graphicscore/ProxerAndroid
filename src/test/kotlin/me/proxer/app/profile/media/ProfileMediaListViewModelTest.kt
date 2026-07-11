package me.proxer.app.profile.media

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.auth.LocalUser
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.mockProxerCallNullableError
import me.proxer.app.base.mockProxerCallNullableSuccess
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.ucp.DeleteCommentEndpoint
import me.proxer.library.api.ucp.UcpMediaListEndpoint
import me.proxer.library.api.user.UserMediaListEndpoint
import me.proxer.library.entity.user.UserMediaListEntry
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import me.proxer.library.enums.UserMediaListFilterType
import me.proxer.library.enums.UserMediaProgress
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

class ProfileMediaListViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: ProfileMediaListViewModel

    private fun mediaEntry(id: String, commentId: String = "comment-$id") = UserMediaListEntry(
        id,
        "Entry $id",
        12,
        Medium.ANIMESERIES,
        MediaState.FINISHED,
        commentId,
        "Comment content",
        UserMediaProgress.WATCHING,
        5,
        8,
    )

    private fun localMediaEntry(id: String, commentId: String = "comment-$id") = LocalUserMediaListEntry.Ucp(
        id,
        "Entry $id",
        12,
        Medium.ANIMESERIES,
        MediaState.FINISHED,
        commentId,
        "Comment content",
        UserMediaProgress.WATCHING,
        5,
        8,
    )

    private fun mockUcpMediaListEndpoint(): UcpMediaListEndpoint {
        val endpoint = mockk<UcpMediaListEndpoint>(relaxed = true)

        every { api.ucp.mediaList() } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        every { endpoint.category(any()) } returns endpoint
        every { endpoint.filter(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { storageHelper.user } returns LocalUser("token", "self-id", "Self", "image.png")

        viewModel = ProfileMediaListViewModel("self-id", null, Category.ANIME, UserMediaListFilterType.WATCHING)
    }

    @Test
    fun `load sets data on success for the current user's own profile`() {
        val endpoint = mockUcpMediaListEndpoint()
        val page = (0 until 30).map { mediaEntry("p0-$it") }
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load uses the user endpoint for another user's profile`() {
        val other = ProfileMediaListViewModel("other-id", "othername", Category.ANIME, null)
        val endpoint = mockk<UserMediaListEndpoint>(relaxed = true)
        every { api.user.mediaList("other-id", "othername") } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        every { endpoint.category(any()) } returns endpoint
        every { endpoint.filter(any()) } returns endpoint

        val page = (0 until 30).map { mediaEntry("p0-$it") }
        endpoint.stubPagingSuccess(page)

        other.load()

        assertEquals(page.map { it.id }, other.data.value?.map { it.id })
        assertNull(other.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockUcpMediaListEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful and failed loads`() {
        val endpoint = mockUcpMediaListEndpoint()

        endpoint.stubPagingSuccess(listOf(mediaEntry("a")))
        viewModel.load()
        assertFalse(viewModel.isLoading.value == true)

        endpoint.stubPagingError()
        viewModel.load()
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockUcpMediaListEndpoint()
        val page = (0 until 30).map { mediaEntry("p0-$it") }
        endpoint.stubPagingSuccess(page)
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val secondPage = (0 until 30).map { mediaEntry("p1-$it") }
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockUcpMediaListEndpoint()
        val lastPage = listOf(mediaEntry("last-0"), mediaEntry("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        endpoint.stubPagingSuccess(listOf(mediaEntry("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(2, viewModel.data.value?.size)
    }

    @Test
    fun `refresh merges page-0 results and a refresh error sets refreshError`() {
        val endpoint = mockUcpMediaListEndpoint()
        val original = (0 until 30).map { mediaEntry("p0-$it") }
        endpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val refreshed = listOf(mediaEntry("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()
        assertEquals(refreshed.map { it.id } + listOf(original.first().id), viewModel.data.value?.map { it.id })

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `category and filter changes trigger a reload`() {
        val endpoint = mockUcpMediaListEndpoint()
        endpoint.stubPagingSuccess(listOf(mediaEntry("initial")))
        viewModel.load()

        val afterCategory = listOf(mediaEntry("manga-0"))
        endpoint.stubPagingSuccess(afterCategory)
        viewModel.category = Category.MANGA
        assertEquals(afterCategory.map { it.id }, viewModel.data.value?.map { it.id })

        val afterFilter = listOf(mediaEntry("watched-0"))
        endpoint.stubPagingSuccess(afterFilter)
        viewModel.filter = UserMediaListFilterType.WATCHED
        assertEquals(afterFilter.map { it.id }, viewModel.data.value?.map { it.id })
    }

    @Test
    fun `addItemToDelete removes the comment on success and chains to the next queued deletion`() {
        val endpoint = mockUcpMediaListEndpoint()
        val apiItems = listOf(mediaEntry("a", "comment-a"), mediaEntry("b", "comment-b"))
        endpoint.stubPagingSuccess(apiItems)
        viewModel.load()

        val localItems = listOf(localMediaEntry("a", "comment-a"), localMediaEntry("b", "comment-b"))
        val deleteEndpoint = mockk<DeleteCommentEndpoint>(relaxed = true)
        every { api.ucp.deleteComment("comment-a") } returns deleteEndpoint
        every { deleteEndpoint.build() } returns mockProxerCallNullableSuccess(Unit)

        viewModel.addItemToDelete(localItems[0])

        assertEquals(localItems.drop(1), viewModel.data.value)
    }

    @Test
    fun `addItemToDelete sets itemDeletionError on failure`() {
        val endpoint = mockUcpMediaListEndpoint()
        val apiItems = listOf(mediaEntry("a", "comment-a"))
        endpoint.stubPagingSuccess(apiItems)
        viewModel.load()

        val localItems = listOf(localMediaEntry("a", "comment-a"))
        val deleteEndpoint = mockk<DeleteCommentEndpoint>(relaxed = true)
        every { api.ucp.deleteComment("comment-a") } returns deleteEndpoint
        every { deleteEndpoint.build() } returns mockProxerCallNullableError<Unit>()

        viewModel.addItemToDelete(localItems[0])

        assertNotNull(viewModel.itemDeletionError.value)
        assertEquals(localItems, viewModel.data.value)
    }
}
