package me.proxer.app.media.comments

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.mockProxerCallNullableError
import me.proxer.app.base.mockProxerCallNullableSuccess
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.comment.LocalComment
import me.proxer.app.ui.view.bbcode.BBArgs
import me.proxer.app.ui.view.bbcode.BBTree
import me.proxer.app.ui.view.bbcode.prototype.TextPrototype
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.comment.UpdateCommentEndpoint
import me.proxer.library.api.info.CommentsEndpoint
import me.proxer.library.entity.info.Comment
import me.proxer.library.entity.info.RatingDetails
import me.proxer.library.enums.CommentSortCriteria
import me.proxer.library.enums.UserMediaProgress
import org.junit.After
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

class CommentsViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: CommentsViewModel
    private lateinit var commentsEndpoint: CommentsEndpoint

    private fun comment(id: String) = Comment(
        id,
        "entry-1",
        "author-$id",
        UserMediaProgress.WATCHED,
        RatingDetails(),
        "Comment $id",
        5,
        1,
        0,
        Date(),
        "Author $id",
        "image.png",
    )

    private fun fullPage(prefix: String) = (0 until 10).map { comment("$prefix-$it") }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        // Comment.toParsedComment() eagerly parses `content` into a BB tree on construction, which
        // otherwise calls through to android.text.LinkifyCompat / SpannableStringBuilder - unavailable
        // in plain JUnit (no Robolectric). Stub it out the same way BBParserTest/MessengerViewModelTest do.
        mockkObject(TextPrototype)
        every { TextPrototype.construct(any<String>(), any<BBTree>()) } answers {
            BBTree(TextPrototype, secondArg(), args = BBArgs(text = firstArg<String>()))
        }

        // Mock the endpoint chain
        commentsEndpoint = mockk(relaxed = true)
        every { api.info.comments("entry-1") } returns commentsEndpoint
        every { commentsEndpoint.sort(any()) } returns commentsEndpoint

        viewModel = CommentsViewModel("entry-1", CommentSortCriteria.TIME)
    }

    @After
    fun teardown() {
        unmockkObject(TextPrototype)
    }

    @Test
    fun `load sets data on success`() {
        val page = fullPage("p0")
        commentsEndpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        commentsEndpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        commentsEndpoint.stubPagingSuccess(fullPage("p0"))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        commentsEndpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        commentsEndpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val secondPage = fullPage("p1")
        commentsEndpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val lastPage = listOf(comment("last-0"), comment("last-1"))
        commentsEndpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        commentsEndpoint.stubPagingSuccess(listOf(comment("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(2, viewModel.data.value?.size)
    }

    @Test
    fun `refresh merges page-0 results and a refresh error sets refreshError`() {
        val original = fullPage("p0")
        commentsEndpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val refreshed = listOf(comment("new-0")) + original.drop(1)
        commentsEndpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()

        val expectedIds = refreshed.map { it.id } + listOf(original.first().id)
        assertEquals(expectedIds, viewModel.data.value?.map { it.id })

        commentsEndpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `sortCriteria change triggers reload`() {
        commentsEndpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val ratingSorted = listOf(comment("rating-0"))
        commentsEndpoint.stubPagingSuccess(ratingSorted)

        viewModel.sortCriteria = CommentSortCriteria.RATING

        assertEquals(ratingSorted.map { it.id }, viewModel.data.value?.map { it.id })
    }

    @Test
    fun `deleteComment removes the item and deletes the draft on success`() {
        val comments = fullPage("p0")
        commentsEndpoint.stubPagingSuccess(comments)
        viewModel.load()

        val target = viewModel.data.value!!.first()
        val updateEndpoint = mockk<UpdateCommentEndpoint>(relaxed = true)
        every { api.comment.update(target.id) } returns updateEndpoint
        every { updateEndpoint.comment(any()) } returns updateEndpoint
        every { updateEndpoint.rating(any()) } returns updateEndpoint
        every { updateEndpoint.build() } returns mockProxerCallNullableSuccess(null)

        viewModel.deleteComment(target)

        assertEquals(9, viewModel.data.value?.size)
        assertFalse(viewModel.data.value!!.any { it.id == target.id })
    }

    @Test
    fun `deleteComment sets itemDeletionError on failure`() {
        val comments = fullPage("p0")
        commentsEndpoint.stubPagingSuccess(comments)
        viewModel.load()

        val target = viewModel.data.value!!.first()
        val updateEndpoint = mockk<UpdateCommentEndpoint>(relaxed = true)
        every { api.comment.update(target.id) } returns updateEndpoint
        every { updateEndpoint.comment(any()) } returns updateEndpoint
        every { updateEndpoint.rating(any()) } returns updateEndpoint
        every { updateEndpoint.build() } returns mockProxerCallNullableError()

        viewModel.deleteComment(target)

        assertNotNull(viewModel.itemDeletionError.value)
        assertEquals(comments.map { it.id }, viewModel.data.value?.map { it.id })
    }

    @Test
    fun `updateComment updates the matching entry in place`() {
        val comments = fullPage("p0")
        commentsEndpoint.stubPagingSuccess(comments)
        viewModel.load()

        val target = viewModel.data.value!!.first()
        val update = LocalComment(
            target.id,
            target.entryId,
            UserMediaProgress.WILL_WATCH,
            RatingDetails(9, 8, 7, 6, 5),
            "Updated",
            9,
            target.episode,
        )

        viewModel.updateComment(update)

        val updated = viewModel.data.value!!.first { it.id == target.id }
        assertEquals(9, updated.overallRating)
    }
}
