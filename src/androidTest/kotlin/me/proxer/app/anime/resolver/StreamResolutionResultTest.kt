package me.proxer.app.anime.resolver

import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.COVER_EXTRA
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.EPISODE_AMOUNT_EXTRA
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.EPISODE_EXTRA
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.HOSTER_NAME_EXTRA
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.ID_EXTRA
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.LANGUAGE_EXTRA
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.NAME_EXTRA
import me.proxer.library.enums.AnimeLanguage
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The intent contract between a resolved stream and [me.proxer.app.anime.stream.StreamActivity].
 *
 * These live in androidTest rather than test because [StreamResolutionResult.Video] builds a real [android.content.Intent]
 * in its constructor, which is unavailable on the JVM test classpath.
 */
@RunWith(AndroidJUnit4::class)
class StreamResolutionResultTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun video() = StreamResolutionResult.Video("https://example.org/video.mp4".toHttpUrl(), "video/mp4")

    @Test
    fun makeIntentCarriesFullStreamContext() {
        val cover = Uri.parse("https://example.org/cover.jpg")

        val intent = video().makeIntent(
            context,
            AnimeStreamContext(
                id = "1337",
                name = "Test Anime",
                episode = 3,
                episodeAmount = 12,
                language = AnimeLanguage.ENGLISH_SUB,
                coverUri = cover,
                hosterName = "Test Hoster",
            ),
        )

        assertEquals("1337", intent.getStringExtra(ID_EXTRA))
        assertEquals("Test Anime", intent.getStringExtra(NAME_EXTRA))
        assertEquals(3, intent.getIntExtra(EPISODE_EXTRA, -1))
        assertEquals(12, intent.getIntExtra(EPISODE_AMOUNT_EXTRA, -1))
        assertEquals("Test Hoster", intent.getStringExtra(HOSTER_NAME_EXTRA))
        assertEquals(cover, IntentCompat.getParcelableExtra(intent, COVER_EXTRA, Uri::class.java))
        assertEquals(
            AnimeLanguage.ENGLISH_SUB,
            IntentCompat.getSerializableExtra(intent, LANGUAGE_EXTRA, AnimeLanguage::class.java),
        )
    }

    @Test
    fun makeIntentOmitsNullableContextFields() {
        val intent = video().makeIntent(
            context,
            AnimeStreamContext(
                id = "1337",
                name = null,
                episode = 1,
                episodeAmount = -1,
                language = AnimeLanguage.GERMAN_SUB,
                coverUri = null,
                hosterName = null,
            ),
        )

        assertFalse(intent.hasExtra(NAME_EXTRA))
        assertFalse(intent.hasExtra(COVER_EXTRA))
        assertFalse(intent.hasExtra(HOSTER_NAME_EXTRA))
        assertEquals(-1, intent.getIntExtra(EPISODE_AMOUNT_EXTRA, -1))
    }

    /**
     * The path taken by `StreamActivity.openInOtherApp`, which hands the raw video off to an external player and
     * therefore supplies no context at all. The activity's accessors must fall back to -1 / null.
     */
    @Test
    fun makeIntentWithoutContextSetsNoStreamExtras() {
        val intent = video().makeIntent(context)

        assertNull(intent.getStringExtra(ID_EXTRA))
        assertFalse(intent.hasExtra(EPISODE_EXTRA))
        assertEquals(-1, intent.getIntExtra(EPISODE_AMOUNT_EXTRA, -1))
        assertNull(intent.getStringExtra(HOSTER_NAME_EXTRA))
    }

    @Test
    fun makeIntentTargetsStreamActivityOnlyWhenForcedInternal() {
        assertNull(video().makeIntent(context, forceInternal = false).component)
        assertEquals(
            "me.proxer.app.anime.stream.StreamActivity",
            video().makeIntent(context, forceInternal = true).component?.className,
        )
    }
}
