package me.proxer.app.tv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.proxer.app.R
import me.proxer.app.tv.search.TvSearchScreenContent
import me.proxer.app.util.ErrorUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvSearchScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun `search_field_is_visible`() {
        composeTestRule.setContent {
            TvTheme {
                TvSearchScreenContent(
                    query = "",
                    onQueryChange = {},
                    entries = emptyList(),
                    isLoading = false,
                    error = null,
                    onMediaClick = { _, _ -> },
                    onRetry = {},
                )
            }
        }
        composeTestRule.onNodeWithText(context.getString(R.string.error_unknown)).assertDoesNotExist()
    }

    @Test fun `results_grid_shows_entry_names`() {
        val entries = listOf(
            fakeMediaListEntry(id = "1", name = "Attack on Titan"),
            fakeMediaListEntry(id = "2", name = "Sword Art Online"),
        )
        composeTestRule.setContent {
            TvTheme {
                TvSearchScreenContent(
                    query = "attack",
                    onQueryChange = {},
                    entries = entries,
                    isLoading = false,
                    error = null,
                    onMediaClick = { _, _ -> },
                    onRetry = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Attack on Titan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sword Art Online").assertIsDisplayed()
    }

    @Test fun `query_text_is_shown_in_search_field`() {
        composeTestRule.setContent {
            TvTheme {
                TvSearchScreenContent(
                    query = "naruto",
                    onQueryChange = {},
                    entries = emptyList(),
                    isLoading = false,
                    error = null,
                    onMediaClick = { _, _ -> },
                    onRetry = {},
                )
            }
        }
        composeTestRule.onNodeWithText("naruto").assertIsDisplayed()
    }

    @Test fun `error_state_shows_error_message`() {
        composeTestRule.setContent {
            TvTheme {
                TvSearchScreenContent(
                    query = "",
                    onQueryChange = {},
                    entries = null,
                    isLoading = false,
                    error = ErrorUtils.ErrorAction(message = R.string.error_no_network),
                    onMediaClick = { _, _ -> },
                    onRetry = {},
                )
            }
        }
        val errorText = context.getString(R.string.error_no_network)
        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()
    }
}
