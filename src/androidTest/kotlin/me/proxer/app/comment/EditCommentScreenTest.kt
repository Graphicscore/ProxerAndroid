package me.proxer.app.comment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.library.api.comment.CommentEndpoint
import me.proxer.library.entity.info.Comment
import me.proxer.library.entity.info.RatingDetails
import me.proxer.library.enums.UserMediaProgress
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * EditCommentActivity has no getIntent -- its only public construction path is the ActivityResultContract.
 * Launching it bare would crash: EditCommentViewModel.init does `require(id != null || entryId != null)`.
 *
 * The draft path is deliberately neutralised. EditCommentViewModel overlays
 * storageHelper.getCommentDraft(entryId) onto the fetched comment, and EditCommentActivity.onDestroy WRITES a
 * draft whenever create-mode content is non-blank -- so a previous test can leave a draft that makes this one
 * pass with its network stub removed. clearMocks in InstrumentedTestBase resets the stub to relaxed baseline,
 * which for a String? return is "" rather than null, so stub it explicitly rather than relying on that.
 */
@RunWith(AndroidJUnit4::class)
class EditCommentScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val commentId = "42"

    private fun comment() = Comment(
        id = commentId,
        entryId = "100",
        authorId = "7",
        mediaProgress = UserMediaProgress.WATCHED,
        ratingDetails = RatingDetails(genre = 1, story = 2, animation = 3, characters = 4, music = 5),
        content = "Comment Content Alpha",
        overallRating = 8,
        episode = 5,
        helpfulVotes = 3,
        date = Date(),
        author = "Someone",
        image = "avatar.png",
    )

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        every { storageHelper.getCommentDraft(any()) } returns null
    }

    @Test
    fun success_renders_loaded_comment_content() {
        val endpoint = mockk<CommentEndpoint>(relaxed = true)

        every { api.comment.comment(commentId, null) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(comment())

        val intent = EditCommentActivity.Contract().createIntent(
            context,
            EditCommentActivity.Contract.Input(id = commentId),
        )

        ActivityScenario.launch<EditCommentActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("Comment Content Alpha").fetchSemanticsNodes().isNotEmpty()
            }

            // The editor is an OutlinedTextField, so its content IS a real Compose text node -- unlike the
            // preview tab, which renders through BBCodeView.
            composeTestRule.onNodeWithText("Comment Content Alpha").assertIsDisplayed()
        }
    }
}
