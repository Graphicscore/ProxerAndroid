package me.proxer.app.manga

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.library.api.info.EntryCoreEndpoint
import me.proxer.library.api.manga.ChapterEndpoint
import me.proxer.library.entity.info.AdaptionInfo
import me.proxer.library.entity.info.EntryCore
import me.proxer.library.entity.manga.Chapter
import me.proxer.library.enums.Category
import me.proxer.library.enums.Language
import me.proxer.library.enums.License
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Manga launches via a bare Intent: id defaults to "-1", episode 1, language ENGLISH. The happy path flips
 * isFullscreen = true (LaunchedEffect on data), removing TopAppBar + BottomAppBar, and the reader is an
 * AndroidView SubsamplingScaleImageView with no Compose semantics -- so the only assertable happy-path node is
 * the MANGA_READER_TEST_TAG on the reader container. Chapter(pages = emptyList()) is a valid success (non-null
 * pages dodge the MangaLink/MangaNotAvailable branches); the container composes with zero items but is still a
 * matchable node.
 */
@RunWith(AndroidJUnit4::class)
class MangaScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val fixedDate = Date()

    private fun entryCore() = EntryCore(
        id = "-1",
        name = "Test Manga",
        genres = emptySet(),
        fskConstraints = emptySet(),
        description = "A description",
        medium = Medium.MANGASERIES,
        episodeAmount = 30,
        state = MediaState.FINISHED,
        ratingSum = 80,
        ratingAmount = 16,
        clicks = 200,
        category = Category.MANGA,
        license = License.UNKNOWN,
        adaptionInfo = AdaptionInfo("50", "Related Anime", Medium.ANIMESERIES),
    )

    private fun chapter() = Chapter(
        id = "1",
        entryId = "-1",
        title = "Chapter 1",
        uploaderId = "7",
        uploaderName = "Uploader",
        date = fixedDate,
        scanGroupId = "1",
        scanGroupName = "Scan Group",
        server = "https://example.com/manga",
        pages = emptyList(),
    )

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        val entryEndpoint = mockk<EntryCoreEndpoint>(relaxed = true)
        every { api.info.entryCore("-1") } returns entryEndpoint
        every { entryEndpoint.build() } returns mockProxerCall(entryCore())

        val chapterEndpoint = mockk<ChapterEndpoint>(relaxed = true)
        every { api.manga.chapter("-1", 1, Language.ENGLISH) } returns chapterEndpoint
        every { chapterEndpoint.build() } returns mockProxerCall(chapter())
    }

    @Test
    fun renders_reader_container_on_successful_load() {
        ActivityScenario.launch<MangaActivity>(
            Intent(context, MangaActivity::class.java),
        ).use {
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithTag(MANGA_READER_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag(MANGA_READER_TEST_TAG).assertIsDisplayed()
        }
    }
}
