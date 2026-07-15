package me.proxer.app.news

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.MainActivity
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.grantStoragePermission
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.util.wrapper.DrawerItem
import me.proxer.library.ProxerCall
import me.proxer.library.api.notifications.NewsEndpoint
import me.proxer.library.entity.notifications.NewsArticle
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class NewsScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun article(id: String) = NewsArticle(
        id,
        Date(),
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

    private fun mockNewsEndpoint(): NewsEndpoint {
        val endpoint = mockk<NewsEndpoint>(relaxed = true)

        every { api.notifications.news() } returns endpoint
        every { endpoint.markAsRead(any()) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint

        return endpoint
    }

    private fun mockCall(value: List<NewsArticle>): ProxerCall<List<NewsArticle>> {
        val call = mockk<ProxerCall<List<NewsArticle>>>(relaxed = true)

        every { call.clone() } returns call
        every { call.safeExecute() } returns value

        return call
    }

    @Before
    fun setup() {
        // Required for every MainActivity-launched screen; see grantStoragePermission's KDoc.
        grantStoragePermission()

        stubLoggedIn(storageHelper, preferenceHelper)
    }

    @Test
    fun success_renders_article_subject() {
        val endpoint = mockNewsEndpoint()
        every { endpoint.build() } returns mockCall(listOf(article("n0")))

        val intent = MainActivity.getSectionIntent(context, DrawerItem.NEWS)

        ActivityScenario.launch<MainActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 15_000) {
                composeTestRule.onAllNodesWithText("Subject n0").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Subject n0").assertIsDisplayed()
        }
    }
}
