package me.proxer.app.tv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.proxer.app.R
import me.proxer.app.tv.episode.TvEpisodeScreenContent
import me.proxer.app.util.ErrorUtils
import me.proxer.library.enums.AnimeLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvEpisodeScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun `entry name is shown in header`() {
        composeTestRule.setContent {
            TvTheme {
                TvEpisodeScreenContent(
                    entryName = "Sword Art Online",
                    episodes = emptyList(),
                    isLoading = false,
                    error = null,
                    onEpisodeClick = { _, _ -> },
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Sword Art Online").assertIsDisplayed()
    }

    @Test fun `episodes list shows episode numbers`() {
        val episodes = listOf(
            fakeEpisodeRow(number = 1),
            fakeEpisodeRow(number = 2),
            fakeEpisodeRow(number = 3),
        )
        composeTestRule.setContent {
            TvTheme {
                TvEpisodeScreenContent(
                    entryName = "SAO",
                    episodes = episodes,
                    isLoading = false,
                    error = null,
                    onEpisodeClick = { _, _ -> },
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Episode 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Episode 2").assertIsDisplayed()
    }

    @Test fun `error state shows error message`() {
        composeTestRule.setContent {
            TvTheme {
                TvEpisodeScreenContent(
                    entryName = "SAO",
                    episodes = null,
                    isLoading = false,
                    error = ErrorUtils.ErrorAction(message = R.string.error_no_network),
                    onEpisodeClick = { _, _ -> },
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        val errorText = context.getString(R.string.error_no_network)
        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()
    }

    @Test fun `loading state with no episodes does not show entry list`() {
        composeTestRule.setContent {
            TvTheme {
                TvEpisodeScreenContent(
                    entryName = "SAO",
                    episodes = null,
                    isLoading = true,
                    error = null,
                    onEpisodeClick = { _, _ -> },
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Episode 1").assertDoesNotExist()
    }
}
