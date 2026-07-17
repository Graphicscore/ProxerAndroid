package me.proxer.app.chat.prv.conference.info

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
import me.proxer.app.chat.prv.LocalConference
import me.proxer.library.api.messenger.ConferenceInfoEndpoint
import me.proxer.library.entity.messenger.ConferenceInfo
import me.proxer.library.entity.messenger.ConferenceParticipant
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.threeten.bp.Instant
import java.util.Date

/**
 * ConferenceInfoActivity requires a LocalConference parcelable; the screen passes conference.id.toString() as
 * the VM's conferenceId. The JVM test fixture uses participants = emptyList(), which would render only the
 * vacuous toolbar title (conference.topic), so this stubs a ConferenceInfo with one ConferenceParticipant and
 * asserts its username -- the only fetch-derived assertion available.
 */
@RunWith(AndroidJUnit4::class)
class ConferenceInfoScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val conference = LocalConference(
        id = 777L,
        topic = "Some topic",
        customTopic = "",
        participantAmount = 1,
        image = "",
        imageType = "",
        isGroup = true,
        localIsRead = true,
        isRead = true,
        date = Instant.ofEpochMilli(0L),
        unreadMessageAmount = 0,
        lastReadMessageId = "0",
        isFullyLoaded = true,
    )

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        val endpoint = mockk<ConferenceInfoEndpoint>(relaxed = true)

        // The screen derives conferenceId from conference.id.toString() -> "777".
        every { api.messenger.conferenceInfo("777") } returns endpoint
        every { endpoint.build() } returns mockProxerCall(
            ConferenceInfo(
                "Some topic",
                1,
                Date(),
                Date(),
                "1",
                listOf(ConferenceParticipant("1", "image.png", "Participant Alpha", "online")),
            ),
        )
    }

    @Test
    fun success_renders_participant_username() {
        val intent = ConferenceInfoActivity.getIntent(context, conference)

        ActivityScenario.launch<ConferenceInfoActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("Participant Alpha").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Participant Alpha").assertIsDisplayed()
        }
    }
}
