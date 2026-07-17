package me.proxer.app.chat.prv.message

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.localConference
import me.proxer.app.base.localMessage
import me.proxer.app.base.stubConference
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.base.stubMessages
import me.proxer.app.base.stubWorkManagerIdle
import me.proxer.app.chat.prv.PrvMessengerActivity
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.util.extension.safeInject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PrvMessengerActivity renders the detail (MessengerScreen) only when a CONFERENCE_EXTRA LocalConference
 * parcelable is present -- a bare launch renders the conference list. The message body is a BBCodeView
 * AndroidView with no Compose semantics, so assert message.username: a real Compose Text shown only for
 * non-own messages. stubLoggedIn leaves storageHelper.user a relaxed mock (id ""), which differs from the
 * fixture's userId, so the message is non-own and the username renders.
 */
@RunWith(AndroidJUnit4::class)
class MessengerScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val messengerDao: MessengerDao by safeInject()
    private val workManager: WorkManager by safeInject()

    private val conference = localConference(1L, "Some topic")

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
        stubWorkManagerIdle(workManager)

        stubConference(messengerDao, conference.id, conference)
        // Non-empty so the dataSource observer publishes content and never hits the enqueueMessageLoad-on-empty
        // path (which would evaluate MessengerWorker on a page-0 load).
        stubMessages(messengerDao, conference.id, listOf(localMessage(1L, conference.id, "User Alpha")))
    }

    @Test
    fun success_renders_message_username() {
        val intent = PrvMessengerActivity.getIntent(context, conference)

        ActivityScenario.launch<PrvMessengerActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("User Alpha").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("User Alpha").assertIsDisplayed()
        }
    }
}
