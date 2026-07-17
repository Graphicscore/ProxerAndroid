package me.proxer.app.anime

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.R
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.library.api.anime.StreamsEndpoint
import me.proxer.library.api.info.EntryCoreEndpoint
import me.proxer.library.entity.anime.Stream
import me.proxer.library.entity.info.AdaptionInfo
import me.proxer.library.entity.info.EntryCore
import me.proxer.library.enums.AnimeLanguage
import me.proxer.library.enums.Category
import me.proxer.library.enums.License
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Anime launches via a bare Intent: id defaults to "-1", episode 1, language ENGLISH_SUB. AnimeScreen has NO
 * player surface (playback is a separate StreamActivity), so no player is constructed here -- but the tests must
 * never tap Play, which would fire a real Intent to StreamActivity.
 *
 * Do not pass a `name`/`episode_amount` extra: the title and EpisodeControlCard would then render from the
 * intent, vacuously. The honest fetch-backed targets are error_no_data_anime (empty streams) and the per-stream
 * Text(hosterName) header.
 */
@RunWith(AndroidJUnit4::class)
class AnimeScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var streamsEndpoint: StreamsEndpoint

    private fun entryCore() = EntryCore(
        id = "-1",
        name = "Test Anime",
        genres = emptySet(),
        fskConstraints = emptySet(),
        description = "A description",
        medium = Medium.ANIMESERIES,
        episodeAmount = 12,
        state = MediaState.FINISHED,
        ratingSum = 100,
        ratingAmount = 20,
        clicks = 500,
        category = Category.ANIME,
        license = License.UNKNOWN,
        adaptionInfo = AdaptionInfo("99", "Related Manga", Medium.MANGASERIES),
    )

    // The streams endpoint returns library `Stream` entities, which AnimeViewModel maps to AnimeStream via
    // `.toAnimeStream(...)`. An unknown hosterName ("Hoster Alpha") matches no StreamResolver, so no real
    // resolution fires and the mapped AnimeStream has resolutionResult=null -> renders a plain StreamItem.
    private fun stream(hosterName: String) = Stream(
        id = "s0",
        hoster = "hoster",
        hosterName = hosterName,
        image = "image",
        uploaderId = "u0",
        uploaderName = "Uploader",
        date = Date(0),
        translatorGroupId = null,
        translatorGroupName = null,
        isOfficial = false,
        isPublic = true,
    )

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        val entryEndpoint = mockk<EntryCoreEndpoint>(relaxed = true)
        every { api.info.entryCore("-1") } returns entryEndpoint
        every { entryEndpoint.build() } returns mockProxerCall(entryCore())

        streamsEndpoint = mockk(relaxed = true)
        every { api.anime.streams("-1", 1, AnimeLanguage.ENGLISH_SUB) } returns streamsEndpoint
        every { streamsEndpoint.includeProxerStreams(true) } returns streamsEndpoint
    }

    @Test
    fun renders_empty_state_when_no_streams() {
        every { streamsEndpoint.build() } returns mockProxerCall(emptyList())

        ActivityScenario.launch<AnimeActivity>(
            Intent(context, AnimeActivity::class.java),
        ).use {
            val emptyText = context.getString(R.string.error_no_data_anime)

            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText(emptyText).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(emptyText).assertIsDisplayed()
        }
    }

    @Test
    fun renders_hoster_name_when_streams_present() {
        every { streamsEndpoint.build() } returns mockProxerCall(listOf(stream("Hoster Alpha")))

        ActivityScenario.launch<AnimeActivity>(
            Intent(context, AnimeActivity::class.java),
        ).use {
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText("Hoster Alpha").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Hoster Alpha").assertIsDisplayed()
        }
    }
}
