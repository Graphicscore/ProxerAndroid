package me.proxer.app.forum

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
import me.proxer.library.api.forum.TopicEndpoint
import me.proxer.library.entity.forum.Post
import me.proxer.library.entity.forum.Topic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * The post BODY renders through a BBCodeView AndroidView, which publishes no Compose semantics, so
 * onNodeWithText cannot see it -- assert on the username instead. Unlike the JVM TopicViewModelTest, no
 * mockkObject(TextPrototype) is needed: android.text is real on-device, and TopicScreen supplies a real
 * Resources to toParsedPost itself.
 */
@RunWith(AndroidJUnit4::class)
class TopicScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val topicId = "topic-1"

    private fun post(id: String) = Post(
        id,
        "0",
        "user-$id",
        "User $id",
        "image.png",
        Date(),
        null,
        null,
        null,
        null,
        null,
        "Message $id",
        0,
    )

    private fun topic(posts: List<Post>) = Topic(
        "cat-1",
        "Category",
        "Subject",
        false,
        posts.size,
        10,
        Date(),
        Date(),
        posts,
    )

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
    }

    @Test
    fun success_renders_post_username() {
        val endpoint = mockk<TopicEndpoint>(relaxed = true)

        every { api.forum.topic(topicId) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(topic(listOf(post("p0"))))

        val intent = TopicActivity.getIntent(context, topicId, "cat-1")

        ActivityScenario.launch<TopicActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("User p0").fetchSemanticsNodes().isNotEmpty()
            }

            // Do NOT assert the toolbar subject: it falls back to the `topic` intent extra, so it would pass
            // without the fetch landing at all.
            composeTestRule.onNodeWithText("User p0").assertIsDisplayed()
        }
    }
}
