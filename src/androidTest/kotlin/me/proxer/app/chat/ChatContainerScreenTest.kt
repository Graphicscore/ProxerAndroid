package me.proxer.app.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.MainActivity
import me.proxer.app.R
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.conferenceWithMessage
import me.proxer.app.base.grantStoragePermission
import me.proxer.app.base.localConference
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubConferences
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.base.stubWorkManagerIdle
import me.proxer.app.base.switchToTab
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.util.extension.safeInject
import me.proxer.app.util.wrapper.DrawerItem
import me.proxer.library.api.chat.PublicChatRoomsEndpoint
import me.proxer.library.api.chat.UserChatRoomsEndpoint
import me.proxer.library.entity.chat.ChatRoom
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ChatContainerScreen is a MainActivity drawer section (DrawerItem.CHAT) with a SecondaryTabRow. Tab 0
 * (ChatRoomList) is API-backed and is the DEFAULT tab, so landing on ChatContainer immediately calls
 * api.chat.publicRooms(); ChatRoomViewModel zips api.chat.userRooms() when logged in, so BOTH must be stubbed
 * or the tab hangs. Tab 1 (ConferenceList) is Room-backed, the embedded twin of ConferenceScreen.
 */
@RunWith(AndroidJUnit4::class)
class ChatContainerScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val workManager: WorkManager by safeInject()
    private val messengerDao: MessengerDao by safeInject()

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun launchChat() = ActivityScenario.launch<MainActivity>(
        MainActivity.getSectionIntent(context, DrawerItem.CHAT),
    )

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
        grantStoragePermission()
        stubWorkManagerIdle(workManager)

        // Tab 0 (ChatRoomList) is the default tab and loads on landing. ChatRoomViewModel zips public+user when
        // logged in, so both endpoints must resolve or the zip never emits.
        val publicEndpoint = mockk<PublicChatRoomsEndpoint>(relaxed = true)
        val userEndpoint = mockk<UserChatRoomsEndpoint>(relaxed = true)

        every { api.chat.publicRooms() } returns publicEndpoint
        every { api.chat.userRooms() } returns userEndpoint
        every { publicEndpoint.build() } returns
            mockProxerCall(listOf(ChatRoom("1", "Room Alpha", "Topic", false)))
        every { userEndpoint.build() } returns mockProxerCall(emptyList<ChatRoom>())
    }

    @Test
    fun public_tab_renders_chat_room_name() {
        launchChat().use {
            awaitText("Room Alpha")
        }
    }

    @Test
    fun private_tab_renders_conference_topic() {
        stubConferences(messengerDao, listOf(conferenceWithMessage(localConference(1L, "Topic Alpha"))))

        launchChat().use {
            composeTestRule.switchToTab(context.getString(R.string.fragment_chat_container_private))

            awaitText("Topic Alpha")
        }
    }
}
