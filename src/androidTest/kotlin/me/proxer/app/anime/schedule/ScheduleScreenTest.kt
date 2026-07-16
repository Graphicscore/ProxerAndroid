package me.proxer.app.anime.schedule

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
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.base.stubLoggedOut
import me.proxer.app.util.wrapper.DrawerItem
import me.proxer.library.api.media.CalendarEndpoint
import me.proxer.library.entity.media.CalendarEntry
import me.proxer.library.enums.CalendarDay
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * [createEmptyComposeRule] is the group-wide convention for tests that launch their own activity via
 * `ActivityScenario` (see NewsScreenTest, BookmarkScreenTest, NotificationScreenTest) -- the rule must not host
 * content of its own. The TV tests use `createComposeRule()` precisely because they call `setContent` themselves.
 *
 * ScheduleScreen additionally renders a `while (true) { ...; delay(1_000) }` status ticker per entry card. That
 * is why content is awaited with explicit [androidx.compose.ui.test.junit4.ComposeTestRule.waitUntil] polling,
 * and why nothing derived from the ticker (airing info, status text) is asserted on: it is wall-clock dependent.
 *
 * The entry's rating is not asserted either, but for a different reason -- the fixture zeroes ratingSum and
 * ratingAmount, so `if (entry.rating > 0)` suppresses the rating row entirely. `entry.name` is the stable
 * render target.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun entry(id: String, name: String) = CalendarEntry(
        id = id,
        entryId = "entry-$id",
        name = name,
        episode = 1,
        episodeTitle = "Ep 1",
        date = Date(),
        timezone = "Europe/Berlin",
        industryId = "10",
        industryName = "Studio A",
        weekDay = CalendarDay.MONDAY,
        uploadDate = Date(),
        genres = emptySet(),
        ratingSum = 0,
        ratingAmount = 0,
    )

    // CalendarEndpoint is a plain Endpoint<List<CalendarEntry>>, not a PagingLimitEndpoint: it declares nothing
    // but build(), and ScheduleViewModel calls api.media.calendar().buildSingle() with no page/limit builders.
    private fun mockCalendarEndpoint(): CalendarEndpoint {
        val endpoint = mockk<CalendarEndpoint>(relaxed = true)

        every { api.media.calendar() } returns endpoint

        return endpoint
    }

    private fun launchSchedule() = ActivityScenario.launch<MainActivity>(
        MainActivity.getSectionIntent(context, DrawerItem.SCHEDULE),
    )

    private fun awaitEntry(name: String) {
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(name).assertIsDisplayed()
    }

    @Before
    fun setup() {
        // Required for every MainActivity-launched screen; see grantStoragePermission's KDoc.
        grantStoragePermission()

        // Logged-in is the default here; the logged-out test re-stubs over it, matching NotificationScreenTest.
        stubLoggedIn(storageHelper, preferenceHelper)
    }

    @Test
    fun success_renders_calendar_entry_name() {
        val endpoint = mockCalendarEndpoint()
        every { endpoint.build() } returns mockProxerCall(listOf(entry("c0", "Show A")))

        launchSchedule().use {
            awaitEntry("Show A")
        }
    }

    /**
     * ScheduleViewModel is one of only two `isLoginRequired = false` view models in the app, so a logged-out
     * user must be SERVED content here rather than blocked. [stubLoggedOut] makes `validators.validateLogin()`
     * throw; BaseViewModel.validate() short-circuits before reaching it precisely because login is not
     * required, so the load succeeds. Seeing `R.string.error_login_required` instead would be a production
     * regression in that short-circuit, not a test defect.
     */
    @Test
    fun logged_out_user_still_sees_content_since_login_is_not_required() {
        stubLoggedOut(storageHelper, preferenceHelper, validators)

        val endpoint = mockCalendarEndpoint()
        every { endpoint.build() } returns mockProxerCall(listOf(entry("c0", "Show A")))

        launchSchedule().use {
            awaitEntry("Show A")
        }
    }
}
