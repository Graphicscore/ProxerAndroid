package me.proxer.app.comment

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.ui.view.bbcode.BBArgs
import me.proxer.app.ui.view.bbcode.BBTree
import me.proxer.app.ui.view.bbcode.prototype.TextPrototype
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.toLocalComment
import me.proxer.library.ProxerApi
import me.proxer.library.ProxerException
import me.proxer.library.api.comment.CommentEndpoint
import me.proxer.library.entity.info.Comment
import me.proxer.library.entity.info.RatingDetails
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

class EditCommentViewModelTest : KoinTest {

    private companion object {
        private const val COMMENT_ID = "42"
        private const val ENTRY_ID = "100"
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

    private val commentEndpoint = mockk<CommentEndpoint>(relaxed = true)

    private lateinit var viewModel: EditCommentViewModel

    private val remoteComment = Comment(
        id = COMMENT_ID,
        entryId = ENTRY_ID,
        authorId = "7",
        mediaProgress = UserMediaProgress.WATCHED,
        ratingDetails = RatingDetails(genre = 1, story = 2, animation = 3, characters = 4, music = 5),
        content = "",
        overallRating = 8,
        episode = 5,
        helpfulVotes = 3,
        date = Date(),
        author = "Someone",
        image = "avatar.png",
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { api.comment.comment(COMMENT_ID, null) } returns commentEndpoint

        // Comment content is eagerly parsed into a BB tree via toLocalComment(), which otherwise calls
        // through to android.text.LinkifyCompat / SpannableStringBuilder - unavailable in plain JUnit
        // (no Robolectric). Stub it out the same way BBParserTest does.
        mockkObject(TextPrototype)
        every { TextPrototype.construct(any<String>(), any<BBTree>()) } answers {
            BBTree(TextPrototype, secondArg(), args = BBArgs(text = firstArg<String>()))
        }

        viewModel = EditCommentViewModel(COMMENT_ID, null)
    }

    @After
    fun teardown() {
        unmockkObject(TextPrototype)
    }

    @Test
    fun `load sets data on success`() {
        commentEndpoint.stubSuccess(remoteComment)

        viewModel.load()

        assertNotNull(viewModel.data.value)
        assertEquals(COMMENT_ID, viewModel.data.value?.id)
        assertEquals(ENTRY_ID, viewModel.data.value?.entryId)
        assertEquals("", viewModel.data.value?.content)
        assertEquals(UserMediaProgress.WATCHED, viewModel.data.value?.mediaProgress)
        assertEquals(RatingDetails(genre = 1, story = 2, animation = 3, characters = 4, music = 5), viewModel.data.value?.ratingDetails)
        assertEquals(8, viewModel.data.value?.overallRating)
        assertEquals(5, viewModel.data.value?.episode)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        commentEndpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `load resolves invalid comment error to default comment`() {
        every { api.comment.comment(null, ENTRY_ID) } returns commentEndpoint
        commentEndpoint.stubError(
            ProxerException(ProxerException.ErrorType.SERVER, ProxerException.ServerErrorType.COMMENT_INVALID_COMMENT),
        )
        val newCommentViewModel = EditCommentViewModel(null, ENTRY_ID)

        newCommentViewModel.load()

        val expectedDefault = LocalComment(
            id = "",
            entryId = "",
            mediaProgress = UserMediaProgress.WATCHED,
            ratingDetails = RatingDetails(genre = 0, story = 0, animation = 0, characters = 0, music = 0),
            content = "",
            overallRating = 0,
            episode = 0,
        )

        assertEquals(expectedDefault, newCommentViewModel.data.value)
        assertNull(newCommentViewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        commentEndpoint.stubSuccess(remoteComment)

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        commentEndpoint.stubError()
        viewModel.load()
        assertNotNull(viewModel.error.value)

        commentEndpoint.stubSuccess(remoteComment)
        viewModel.reload()

        assertNotNull(viewModel.data.value)
        assertEquals(COMMENT_ID, viewModel.data.value?.id)
        assertEquals(ENTRY_ID, viewModel.data.value?.entryId)
        assertEquals("", viewModel.data.value?.content)
        assertEquals(UserMediaProgress.WATCHED, viewModel.data.value?.mediaProgress)
        assertEquals(RatingDetails(genre = 1, story = 2, animation = 3, characters = 4, music = 5), viewModel.data.value?.ratingDetails)
        assertEquals(8, viewModel.data.value?.overallRating)
        assertEquals(5, viewModel.data.value?.episode)
        assertNull(viewModel.error.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor throws when both id and entryId are null`() {
        EditCommentViewModel(null, null)
    }
}
