package me.proxer.app.media.episode

import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.proxer.app.R
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.extension.toEpisodeAppString
import me.proxer.library.entity.info.AnimeEpisode
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpisodeScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val germanSubLabel get() = context.getString(R.string.language_german_sub)
    private val englishSubLabel get() = context.getString(R.string.language_english_sub)
    private val episodeTitle get() = Category.ANIME.toEpisodeAppString(context, 1)

    private fun episodeRow(vararg languages: MediaLanguage) =
        episodeRowWithHosters(*languages.map { it to emptyList<String>() }.toTypedArray())

    private fun episodeRowWithHosters(vararg entries: Pair<MediaLanguage, List<String>>) = EpisodeRow(
        category = Category.ANIME,
        userProgress = null,
        episodeAmount = 12,
        episodes = entries.map { (language, hosterImages) ->
            AnimeEpisode(
                number = 1,
                language = language,
                hosters = emptySet(),
                hosterImages = hosterImages,
            )
        },
    )

    private fun setContent(row: EpisodeRow) = composeTestRule.setContent {
        // ProxerTheme resolves colorPrimary and friends off the context theme; the bare host Activity behind
        // createComposeRule has no app theme, so resolveColor throws unless the context is wrapped explicitly.
        val themedContext = ContextThemeWrapper(LocalContext.current, R.style.Theme_App)

        CompositionLocalProvider(LocalContext provides themedContext) {
            ProxerTheme {
                EpisodeContent(
                    data = listOf(row),
                    error = null,
                    isLoading = false,
                    mediaId = "1",
                    mediaName = "Test Media",
                    bookmarkResult = MutableLiveData<Unit?>(null),
                    bookmarkError = MutableLiveData<ErrorAction?>(null),
                    onRetry = {},
                    onBookmark = { _, _, _ -> },
                )
            }
        }
    }

    @Test fun `language_buttons_are_hidden_before_expanding`() {
        setContent(episodeRow(MediaLanguage.GERMAN_SUB, MediaLanguage.ENGLISH_SUB))

        composeTestRule.onNodeWithText(episodeTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(germanSubLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText(englishSubLabel).assertDoesNotExist()
    }

    @Test fun `language_buttons_are_shown_after_expanding`() {
        setContent(episodeRow(MediaLanguage.GERMAN_SUB, MediaLanguage.ENGLISH_SUB))

        composeTestRule.onNodeWithText(episodeTitle).performClick()

        composeTestRule.onNodeWithText(germanSubLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(englishSubLabel).assertIsDisplayed()
    }

    @Test fun `only_available_languages_are_shown`() {
        setContent(episodeRow(MediaLanguage.GERMAN_SUB))

        composeTestRule.onNodeWithText(episodeTitle).performClick()

        composeTestRule.onNodeWithText(germanSubLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(englishSubLabel).assertDoesNotExist()
    }

    // The hoster assertions below all pass useUnmergedTree = true: OutlinedButton merges its
    // descendants' semantics, so the tagged icon nodes are invisible in the merged tree.

    @Test fun `one_hoster_icon_is_rendered_per_hoster_image`() {
        setContent(
            episodeRowWithHosters(
                MediaLanguage.GERMAN_SUB to listOf("crunchyroll.jpg", "vidoza.jpg", "streamcloud.jpg"),
            ),
        )

        composeTestRule.onNodeWithText(episodeTitle).performClick()

        composeTestRule.onAllNodesWithTag(HOSTER_ICON_TEST_TAG, useUnmergedTree = true).assertCountEquals(3)
        composeTestRule.onAllNodesWithTag(HOSTER_ROW_TEST_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun `no_hoster_row_is_rendered_when_there_are_no_hoster_images`() {
        setContent(episodeRow(MediaLanguage.GERMAN_SUB, MediaLanguage.ENGLISH_SUB))

        composeTestRule.onNodeWithText(episodeTitle).performClick()

        composeTestRule.onAllNodesWithTag(HOSTER_ROW_TEST_TAG, useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(HOSTER_ICON_TEST_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun `each_language_gets_its_own_hoster_row`() {
        setContent(
            episodeRowWithHosters(
                MediaLanguage.GERMAN_SUB to listOf("crunchyroll.jpg"),
                MediaLanguage.ENGLISH_SUB to listOf("vidoza.jpg", "streamcloud.jpg"),
            ),
        )

        composeTestRule.onNodeWithText(episodeTitle).performClick()

        composeTestRule.onAllNodesWithTag(HOSTER_ROW_TEST_TAG, useUnmergedTree = true).assertCountEquals(2)
        composeTestRule.onAllNodesWithTag(HOSTER_ICON_TEST_TAG, useUnmergedTree = true).assertCountEquals(3)
    }

    @Test fun `a_language_without_hosters_gets_no_row_even_when_a_sibling_has_them`() {
        setContent(
            episodeRowWithHosters(
                MediaLanguage.GERMAN_SUB to listOf("crunchyroll.jpg"),
                MediaLanguage.ENGLISH_SUB to emptyList(),
            ),
        )

        composeTestRule.onNodeWithText(episodeTitle).performClick()

        composeTestRule.onAllNodesWithTag(HOSTER_ROW_TEST_TAG, useUnmergedTree = true).assertCountEquals(1)
        composeTestRule.onAllNodesWithTag(HOSTER_ICON_TEST_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun `hoster_icons_that_overflow_the_button_stay_reachable_by_scrolling`() {
        val manyHosters = (1..12).map { "hoster$it.jpg" }

        setContent(episodeRowWithHosters(MediaLanguage.GERMAN_SUB to manyHosters))

        composeTestRule.onNodeWithText(episodeTitle).performClick()

        composeTestRule.onAllNodesWithTag(HOSTER_ICON_TEST_TAG, useUnmergedTree = true).assertCountEquals(12)

        // performScrollTo only succeeds inside a scrollable container, so this fails if the
        // horizontalScroll modifier is ever dropped and the trailing icons get clipped instead.
        composeTestRule
            .onNodeWithContentDescription("hoster12", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test fun `hoster_icons_are_described_by_their_name_without_the_file_extension`() {
        setContent(episodeRowWithHosters(MediaLanguage.GERMAN_SUB to listOf("crunchyroll.jpg")))

        composeTestRule.onNodeWithText(episodeTitle).performClick()

        composeTestRule
            .onNodeWithContentDescription("crunchyroll", useUnmergedTree = true)
            .assertExists()
    }
}
