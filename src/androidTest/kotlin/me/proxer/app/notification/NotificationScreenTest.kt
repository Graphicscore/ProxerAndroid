package me.proxer.app.notification

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.R
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.mockProxerErrorCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.base.stubLoggedOut
import me.proxer.app.util.extension.ProxerNotification
import me.proxer.library.api.notifications.NotificationsEndpoint
import me.proxer.library.entity.notifications.Notification
import me.proxer.library.enums.NotificationType
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class NotificationScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun notification(id: String, text: String = "text-$id") = Notification(
        id,
        NotificationType.BOARD_MESSAGE,
        "content-$id",
        "https://proxer.me/".toHttpUrl(),
        text,
        Date(),
        "extra",
    )

    private fun mockNotificationsEndpoint(): NotificationsEndpoint {
        val endpoint = mockk<NotificationsEndpoint>(relaxed = true)

        every { api.notifications.notifications() } returns endpoint
        every { endpoint.markAsRead(any()) } returns endpoint
        every { endpoint.filter(any()) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
    }

    @Test
    fun success_shows_unread_and_read_notification_text() {
        val endpoint = mockNotificationsEndpoint()
        val unread = listOf(notification("u0"))
        val read = listOf(notification("r0"))
        every { endpoint.build() } returnsMany listOf(mockProxerCall(unread), mockProxerCall(read))

        ActivityScenario.launch(NotificationActivity::class.java).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("text-u0").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("text-u0").assertIsDisplayed()
            composeTestRule.onNodeWithText("text-r0").assertIsDisplayed()
        }
    }

    @Test
    fun scrolling_to_the_bottom_loads_the_next_page() {
        val endpoint = mockNotificationsEndpoint()
        val firstPageUnread = (0 until 30).map { notification("u$it") }
        val secondPageItem = notification("p2-0", text = "page-two-item")
        every { endpoint.build() } returnsMany listOf(
            mockProxerCall(firstPageUnread),
            mockProxerCall(emptyList()),
            mockProxerCall(listOf(secondPageItem)),
        )

        ActivityScenario.launch(NotificationActivity::class.java).use {
            // 30 items is enough that a cold activity launch + first composition can take noticeably
            // longer than the 5s budget that's fine for the 2-item success test (observed up to ~8s on a
            // local emulator); use a more generous timeout here to avoid flakiness on slower devices/CI.
            composeTestRule.waitUntil(timeoutMillis = 15_000) {
                composeTestRule.onAllNodesWithText("text-u0").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNode(hasScrollAction()).performScrollToIndex(29)

            // Reaching the last item triggers NotificationScreen's load-more effect, which appends the next
            // page at the end of the list (now index 30) once it arrives -- but LazyColumn does not
            // auto-scroll to reveal newly-appended content, so the new item stays off-screen (and
            // uncomposed, hence unfindable) until something scrolls to it again. The extra item doesn't
            // exist yet at the moment we scrolled to index 29, so repeatedly retry scrolling to index 30
            // here until the next page has actually loaded and that scroll succeeds.
            composeTestRule.waitUntil(timeoutMillis = 15_000) {
                try {
                    composeTestRule.onNode(hasScrollAction()).performScrollToIndex(30)
                } catch (expected: IllegalArgumentException) {
                    // Next page hasn't loaded yet, so index 30 doesn't exist. Keep polling.
                }

                composeTestRule.onAllNodesWithText("page-two-item").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("page-two-item").assertIsDisplayed()
        }
    }

    @Test
    fun error_shows_io_error_message_and_retry_button() {
        val endpoint = mockNotificationsEndpoint()
        every { endpoint.build() } returns mockProxerErrorCall<List<ProxerNotification>>()

        ActivityScenario.launch(NotificationActivity::class.java).use {
            val errorText = context.getString(R.string.error_io)
            val retryText = context.getString(R.string.error_action_retry)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(errorText).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(errorText).assertIsDisplayed()
            composeTestRule.onNodeWithText(retryText).assertIsDisplayed()
        }
    }

    @Test
    fun retry_replaces_error_with_content_on_success() {
        val endpoint = mockNotificationsEndpoint()
        val unread = listOf(notification("u0"))
        val read = listOf(notification("r0"))
        every { endpoint.build() } returns mockProxerErrorCall<List<ProxerNotification>>()

        ActivityScenario.launch(NotificationActivity::class.java).use {
            val retryText = context.getString(R.string.error_action_retry)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(retryText).fetchSemanticsNodes().isNotEmpty()
            }

            every { endpoint.build() } returnsMany listOf(mockProxerCall(unread), mockProxerCall(read))
            composeTestRule.onNodeWithText(retryText).performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("text-u0").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("text-u0").assertIsDisplayed()
        }
    }

    @Test
    fun logged_out_user_sees_login_required_message_instead_of_content() {
        stubLoggedOut(storageHelper, preferenceHelper, validators)

        ActivityScenario.launch(NotificationActivity::class.java).use {
            val loginRequiredText = context.getString(R.string.error_login_required)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(loginRequiredText).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(loginRequiredText).assertIsDisplayed()
        }
    }
}
