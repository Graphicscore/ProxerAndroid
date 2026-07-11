package me.proxer.app.media.discussion

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
import me.proxer.library.api.info.ForumDiscussionsEndpoint
import me.proxer.library.entity.info.ForumDiscussion
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

class DiscussionViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val entryId = "12345"

    private lateinit var viewModel: DiscussionViewModel

    private fun createDiscussion(id: String) = ForumDiscussion(
        id = id,
        categoryId = "10",
        categoryName = "General",
        subject = "Test Discussion $id",
        postAmount = 5,
        hits = 100,
        firstPostDate = Date(0),
        firstPostUserId = "1",
        firstPostUsername = "user1",
        lastPostDate = Date(0),
        lastPostUserId = "2",
        lastPostUsername = "user2",
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        viewModel = DiscussionViewModel(entryId)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockk<ForumDiscussionsEndpoint>(relaxed = true)
        val discussions = listOf(createDiscussion("1"), createDiscussion("2"))

        every { api.info.forumDiscussions(entryId) } returns endpoint
        endpoint.stubSuccess(discussions)

        viewModel.load()

        assertEquals(discussions, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockk<ForumDiscussionsEndpoint>(relaxed = true)

        every { api.info.forumDiscussions(entryId) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockk<ForumDiscussionsEndpoint>(relaxed = true)

        every { api.info.forumDiscussions(entryId) } returns endpoint
        endpoint.stubSuccess(listOf(createDiscussion("1")))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockk<ForumDiscussionsEndpoint>(relaxed = true)

        every { api.info.forumDiscussions(entryId) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockk<ForumDiscussionsEndpoint>(relaxed = true)
        val firstDiscussions = listOf(createDiscussion("1"))
        val secondDiscussions = listOf(createDiscussion("2"), createDiscussion("3"))

        every { api.info.forumDiscussions(entryId) } returns endpoint
        endpoint.stubSuccess(firstDiscussions)
        viewModel.load()
        assertEquals(firstDiscussions, viewModel.data.value)

        endpoint.stubSuccess(secondDiscussions)
        viewModel.reload()

        assertEquals(secondDiscussions, viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
