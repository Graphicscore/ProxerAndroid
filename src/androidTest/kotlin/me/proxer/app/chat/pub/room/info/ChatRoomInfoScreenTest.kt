package me.proxer.app.chat.pub.room.info

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
import me.proxer.library.api.chat.ChatRoomUsersEndpoint
import me.proxer.library.entity.chat.ChatRoomUser
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * user.name and user.status are plain Compose Text and fully fetch-derived. The toolbar title is the
 * chatRoomName intent extra -- vacuous, not asserted. ChatRoomInfoViewModel polls every 10s; stub is idempotent.
 */
@RunWith(AndroidJUnit4::class)
class ChatRoomInfoScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val chatRoomId = "555"

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        val endpoint = mockk<ChatRoomUsersEndpoint>(relaxed = true)

        every { api.chat.roomUsers(chatRoomId) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(
            listOf(ChatRoomUser("1", "User Alpha", "image.png", "online", false)),
        )
    }

    @Test
    fun success_renders_room_user_name() {
        val intent = ChatRoomInfoActivity.getIntent(context, chatRoomId, "Room Name")

        ActivityScenario.launch<ChatRoomInfoActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("User Alpha").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("User Alpha").assertIsDisplayed()
        }
    }
}
