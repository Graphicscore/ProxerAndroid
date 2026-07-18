package me.proxer.app.media.episode

import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    private fun episodeRow(vararg languages: MediaLanguage) = EpisodeRow(
        category = Category.ANIME,
        userProgress = null,
        episodeAmount = 12,
        episodes = languages.map { language ->
            AnimeEpisode(
                number = 1,
                language = language,
                hosters = emptySet(),
                hosterImages = emptyList(),
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
}
