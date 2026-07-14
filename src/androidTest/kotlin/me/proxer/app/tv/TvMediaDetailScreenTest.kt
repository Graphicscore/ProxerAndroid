package me.proxer.app.tv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.proxer.app.R
import me.proxer.app.tv.detail.TvMediaDetailScreenContent
import me.proxer.app.util.ErrorUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvMediaDetailScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun `populated_state_shows_entry_name`() {
        composeTestRule.setContent {
            TvTheme {
                TvMediaDetailScreenContent(
                    entryId = "1",
                    entryName = "Attack on Titan",
                    description = "Humanity fights giant humanoid creatures.",
                    rating = 9.5f,
                    episodeAmount = 25,
                    isLoading = false,
                    error = null,
                    onWatchEpisodes = {},
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Attack on Titan").assertIsDisplayed()
    }

    @Test fun `populated_state_shows_description`() {
        composeTestRule.setContent {
            TvTheme {
                TvMediaDetailScreenContent(
                    entryId = "1",
                    entryName = "Attack on Titan",
                    description = "Humanity fights giant humanoid creatures.",
                    rating = 9.5f,
                    episodeAmount = 25,
                    isLoading = false,
                    error = null,
                    onWatchEpisodes = {},
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Humanity fights giant humanoid creatures.").assertIsDisplayed()
    }

    @Test fun `error_state_shows_error_message`() {
        composeTestRule.setContent {
            TvTheme {
                TvMediaDetailScreenContent(
                    entryId = "1",
                    entryName = "Attack on Titan",
                    description = null,
                    rating = null,
                    episodeAmount = null,
                    isLoading = false,
                    error = ErrorUtils.ErrorAction(message = R.string.error_no_network),
                    onWatchEpisodes = {},
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        val errorText = context.getString(R.string.error_no_network)
        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()
    }

    @Test fun `loading_state_does_not_show_description`() {
        composeTestRule.setContent {
            TvTheme {
                TvMediaDetailScreenContent(
                    entryId = "1",
                    entryName = "Attack on Titan",
                    description = null,
                    rating = null,
                    episodeAmount = null,
                    isLoading = true,
                    error = null,
                    onWatchEpisodes = {},
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Humanity fights").assertDoesNotExist()
    }
}
