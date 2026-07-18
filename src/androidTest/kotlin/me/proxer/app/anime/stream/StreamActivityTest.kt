package me.proxer.app.anime.stream

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.verify
import me.proxer.app.anime.resolver.AnimeStreamContext
import me.proxer.app.anime.resolver.StreamResolutionResult
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.stubLoggedIn
import me.proxer.library.enums.AnimeLanguage
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The in-place episode swap of [StreamActivity].
 *
 * The video URLs never resolve to real media; nothing here waits for playback. What matters is the intent
 * bookkeeping around a swap, which happens synchronously in [StreamActivity.switchToEpisode] regardless of
 * whether the player ever managed to load anything.
 *
 * `ActivityScenario` is deliberately not used: it identifies its activity by comparing the launch intent
 * against `activity.getIntent()`, and a swap replaces that intent by design -- the scenario then stops
 * recognising its own activity and blocks until close() times out. [android.app.Instrumentation.startActivitySync]
 * hands back the activity instance directly and has no such coupling.
 */
@RunWith(AndroidJUnit4::class)
class StreamActivityTest : InstrumentedTestBase() {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun video(episode: Int) =
        StreamResolutionResult.Video("https://example.org/episode-$episode.mp4".toHttpUrl(), "video/mp4")

    private fun launchIntent(episode: Int, episodeAmount: Int): Intent = video(episode).makeIntent(
        instrumentation.targetContext,
        AnimeStreamContext(
            id = "1337",
            name = "Test Anime",
            episode = episode,
            episodeAmount = episodeAmount,
            language = AnimeLanguage.ENGLISH_SUB,
            coverUri = null,
            hosterName = "Test Hoster",
        ),
        forceInternal = true,
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun withActivity(episode: Int, episodeAmount: Int, block: (StreamActivity) -> Unit) {
        val activity = instrumentation.startActivitySync(launchIntent(episode, episodeAmount)) as StreamActivity

        try {
            instrumentation.runOnMainSync { block(activity) }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        every { storageHelper.getLastAnimePosition(any(), any(), any()) } returns null
    }

    @Test
    fun firstEpisodeHasNoPreviousButHasNext() {
        withActivity(episode = 1, episodeAmount = 12) { activity ->
            assertFalse(activity.hasPreviousEpisode)
            assertTrue(activity.hasNextEpisode)
        }
    }

    @Test
    fun lastEpisodeHasPreviousButNoNext() {
        withActivity(episode = 12, episodeAmount = 12) { activity ->
            assertTrue(activity.hasPreviousEpisode)
            assertFalse(activity.hasNextEpisode)
        }
    }

    @Test
    fun unknownEpisodeAmountHasNeitherNeighbour() {
        withActivity(episode = 5, episodeAmount = -1) { activity ->
            assertEquals(-1, activity.episodeAmount)
            assertFalse(activity.hasPreviousEpisode)
            assertFalse(activity.hasNextEpisode)
        }
    }

    @Test
    fun switchToEpisodeSwapsIntentAndResumesIncomingPosition() {
        every { storageHelper.getLastAnimePosition("1337", 4, AnimeLanguage.ENGLISH_SUB) } returns 90_000L

        withActivity(episode = 3, episodeAmount = 12) { activity ->
            activity.switchToEpisode(4, video(4), "Proxer")

            assertEquals(4, activity.episode)
            assertEquals("https://example.org/episode-4.mp4", activity.uri.toString())
            // Context the swap has to carry over from the outgoing intent.
            assertEquals("1337", activity.id)
            assertEquals(12, activity.episodeAmount)
            assertEquals(AnimeLanguage.ENGLISH_SUB, activity.language)
            // The hoster is the exception: the one that actually resolved wins over the outgoing
            // one, so a hoster that already fell through is not re-tried on the next navigation.
            assertEquals("Proxer", activity.hosterName)

            assertEquals(90_000L, activity.playerManager.currentPlayer.currentPosition)
        }
    }

    @Test
    fun switchToEpisodePersistsOutgoingPlayerPosition() {
        withActivity(episode = 3, episodeAmount = 12) { activity ->
            activity.playerManager.currentPlayer.seekTo(42_000L)

            val outgoingPosition = activity.playerManager.currentPlayer.currentPosition

            // Without this the assertion below would be satisfied by a hardcoded 0 as well, making it
            // indistinguishable from the clearOutgoingPosition path.
            assertTrue(outgoingPosition > 0)

            activity.switchToEpisode(4, video(4), "Proxer")

            verify { storageHelper.putLastAnimePosition("1337", 3, AnimeLanguage.ENGLISH_SUB, outgoingPosition) }
        }
    }

    @Test
    fun switchToEpisodeClearsOutgoingPositionWhenRequested() {
        withActivity(episode = 3, episodeAmount = 12) { activity ->
            activity.playerManager.currentPlayer.seekTo(42_000L)

            activity.switchToEpisode(4, video(4), "Proxer", clearOutgoingPosition = true)

            verify { storageHelper.putLastAnimePosition("1337", 3, AnimeLanguage.ENGLISH_SUB, 0) }
        }
    }

    /**
     * A swap bumps the content key and rebuilds the whole Compose subtree, while the navigation
     * observers registered inside it survive that rebuild and keep handling events. Autoplay state
     * must therefore outlive the subtree: remembering it inside the composition loses the "playback
     * ended by itself" reading on every swap, so from the second episode onwards autoplay persists
     * the outgoing position instead of clearing it -- and reopening that episode then seeks to its
     * end, ends immediately and chain-loads the rest of the series.
     *
     * This guards the state's lifetime, not the wiring: it still passes if a future change moves
     * the composition back onto its own `remember` while leaving these holders in place.
     */
    @Test
    fun autoplayStateSurvivesEpisodeSwap() {
        withActivity(episode = 3, episodeAmount = 12) { activity ->
            activity.endedEpisodeState.value = 3
            activity.autoplaySecondsLeftState.value = 5

            activity.switchToEpisode(4, video(4), "Proxer")

            assertEquals(3, activity.endedEpisodeState.value)
            assertEquals(5, activity.autoplaySecondsLeftState.value)
        }
    }
}
