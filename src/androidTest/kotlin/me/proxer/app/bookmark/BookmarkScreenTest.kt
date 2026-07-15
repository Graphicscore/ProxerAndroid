package me.proxer.app.bookmark

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
import me.proxer.library.api.ucp.BookmarksEndpoint
import me.proxer.library.entity.ucp.Bookmark
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaLanguage
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

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

    private fun mockBookmarksEndpoint(): BookmarksEndpoint {
        val endpoint = mockk<BookmarksEndpoint>(relaxed = true)

        // BookmarkViewModel's endpoint getter unconditionally chains name/category/filterAvailable
        // (filterAvailable is still called, with null, when the flag is false), so all three need stubbing
        // alongside the paging calls. What to stub comes from the VM's endpoint getter, not from parametersOf.
        every { api.ucp.bookmarks() } returns endpoint
        every { endpoint.name(any()) } returns endpoint
        every { endpoint.category(any()) } returns endpoint
        every { endpoint.filterAvailable(any()) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint

        return endpoint
    }

    private fun mockCall(value: List<Bookmark>): ProxerCall<List<Bookmark>> {
        val call = mockk<ProxerCall<List<Bookmark>>>(relaxed = true)

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
    fun success_renders_bookmark_name() {
        val endpoint = mockBookmarksEndpoint()
        every { endpoint.build() } returns mockCall(listOf(bookmark("b0")))

        val intent = MainActivity.getSectionIntent(context, DrawerItem.BOOKMARKS)

        ActivityScenario.launch<MainActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 15_000) {
                composeTestRule.onAllNodesWithText("Bookmark b0").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Bookmark b0").assertIsDisplayed()
        }
    }
}
