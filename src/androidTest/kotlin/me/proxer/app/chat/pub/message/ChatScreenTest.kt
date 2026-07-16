package me.proxer.app.chat.pub.message

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
import me.proxer.library.api.chat.ChatMessagesEndpoint
import me.proxer.library.entity.chat.ChatMessage
import me.proxer.library.enums.ChatMessageAction
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * The message BODY renders through a BBCodeView AndroidView with no Compose semantics, so assert the username.
 * ChatViewModel polls api.chat.messages every 3s; the stub is idempotent so repeated calls are harmless -- do
 * not write verify(exactly = 1). ChatMessage id must be numeric-parseable: ChatViewModel calls id.toLong().
 */
@RunWith(AndroidJUnit4::class)
class ChatScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val chatRoomId = "room-1"

    private fun message(id: String, username: String) = ChatMessage(
        id,
        "user-$id",
        username,
        "image.png",
        "Message body $id",
        ChatMessageAction.NONE,
        Date(),
    )

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        val endpoint = mockk<ChatMessagesEndpoint>(relaxed = true)

        every { api.chat.messages(chatRoomId) } returns endpoint
        every { endpoint.messageId(any()) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(listOf(message("0", "User Alpha")))
    }

    @Test
    fun success_renders_message_username() {
        val intent = ChatActivity.getIntent(context, chatRoomId, "Room Name")

        ActivityScenario.launch<ChatActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("User Alpha").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("User Alpha").assertIsDisplayed()
        }
    }
}
