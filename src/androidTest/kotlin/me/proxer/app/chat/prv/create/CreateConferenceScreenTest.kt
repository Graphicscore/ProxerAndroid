package me.proxer.app.chat.prv.create

import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onSiblings
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.R
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.library.api.messenger.CreateConferenceEndpoint
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CreateConferenceScreen renders nothing fetch-derived: its title, participant list and message field all come
 * from intent extras or local UI state. The only API-observable signal is that the create endpoint fires on
 * submit, so this test drives the 1:1 chat flow to a submit and asserts the endpoint invocation -- it does NOT
 * assert rendered content.
 *
 * It deliberately does NOT assert navigation. On success the VM only stores a conference id and enqueues a
 * MessengerWorker sync; `result` is set from an RxBus SynchronizationEvent plus a Room lookup, both of which
 * are Group 4 fixtures.
 *
 * Driving the flow (bare launch -> 1:1 mode, participants empty):
 *   1. Tap the "add participant" TextButton to reveal the participant entry row.
 *   2. Type the username into the participant field (matched by its floating label).
 *   3. Tap the row's confirm (Add) IconButton. It carries no contentDescription/testTag, and the row's other
 *      IconButton (cancel) is identical, so it is targeted as the FIRST clickable sibling of the field -- the
 *      Add button is declared before the Close button, so semantic order puts it at index 0. Confirming in 1:1
 *      mode collapses the row and adds the participant.
 *   4. Type the first message (matched by its label).
 *   5. Tap the create Button -- matched by text AND a click action, because the TopAppBar title shares the
 *      same `action_create_chat` string but has no click action.
 */
@RunWith(AndroidJUnit4::class)
class CreateConferenceScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
    }

    @Test
    fun submitting_invokes_the_create_conference_endpoint() {
        val endpoint = mockk<CreateConferenceEndpoint>(relaxed = true)

        every { api.messenger.createConference(any(), any()) } returns endpoint
        every { endpoint.build() } returns mockProxerCall("123")

        // Bare launch is correct: isGroup defaults false and initialParticipant is null -> 1:1 chat mode.
        ActivityScenario.launch(CreateConferenceActivity::class.java).use {
            // 1. Reveal the participant entry row.
            composeTestRule
                .onNodeWithText(context.getString(R.string.fragment_create_conference_add_participant))
                .performClick()

            // 2. Enter the participant username.
            val participantHint = context.getString(R.string.fragment_create_conference_add_participant_hint)

            composeTestRule
                .onNodeWithText(participantHint)
                .performTextInput("bob")

            // 3. Confirm the participant via the first clickable sibling (the Add IconButton) of the field.
            composeTestRule
                .onNodeWithText(participantHint)
                .onSiblings()
                .filter(hasClickAction())
                .onFirst()
                .performClick()

            // 4. Enter the first message.
            composeTestRule
                .onNodeWithText(context.getString(R.string.fragment_messenger_message))
                .performTextInput("hello")

            // 5. Submit. The TopAppBar title shares this string, so require a click action to hit the Button.
            composeTestRule
                .onNode(
                    hasText(context.getString(R.string.action_create_chat)) and hasClickAction(),
                )
                .performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                runCatching { verify { api.messenger.createConference("hello", "bob") } }.isSuccess
            }
        }
    }
}
