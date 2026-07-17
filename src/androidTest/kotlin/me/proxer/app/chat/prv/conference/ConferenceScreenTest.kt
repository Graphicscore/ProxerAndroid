package me.proxer.app.chat.prv.conference

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import io.mockk.every
import me.proxer.app.R
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.conferenceWithMessage
import me.proxer.app.base.localConference
import me.proxer.app.base.stubConferences
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.base.stubWorkManagerIdle
import me.proxer.app.chat.prv.PrvMessengerActivity
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.util.extension.safeInject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A bare PrvMessengerActivity launch (no extras) renders ConferenceScreen -- the conference list. Content is
 * Room-driven (dataSingle is Single.never()); all text is plain Compose Text, so the topic is directly
 * assertable. The empty-state test exercises the areConferencesSynchronized gate: an empty emission is
 * dropped unless that flag is true, leaving an infinite spinner.
 */
@RunWith(AndroidJUnit4::class)
class ConferenceScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val messengerDao: MessengerDao by safeInject()
    private val workManager: WorkManager by safeInject()

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
        stubWorkManagerIdle(workManager)
    }

    @Test
    fun populated_renders_conference_topic() {
        stubConferences(messengerDao, listOf(conferenceWithMessage(localConference(1L, "Topic Alpha"))))

        ActivityScenario.launch(PrvMessengerActivity::class.java).use {
            awaitText("Topic Alpha")
        }
    }

    @Test
    fun empty_state_renders_no_data_message() {
        // Without areConferencesSynchronized = true, the empty emission is dropped and the spinner never clears.
        every { storageHelper.areConferencesSynchronized } returns true
        stubConferences(messengerDao, emptyList())

        ActivityScenario.launch(PrvMessengerActivity::class.java).use {
            awaitText(context.getString(R.string.error_no_data_conferences))
        }
    }
}
