package me.proxer.app.tv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.proxer.app.R
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.ErrorUtils.ErrorAction.Companion.ACTION_MESSAGE_HIDE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvErrorViewTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun `generic error shows Retry button`() {
        composeTestRule.setContent {
            TvTheme {
                TvErrorView(
                    error = ErrorUtils.ErrorAction(message = R.string.error_unknown),
                    onRetryClick = {},
                )
            }
        }
        val label = context.getString(R.string.error_action_retry)
        composeTestRule.onNodeWithText(label).assertIsDisplayed()
    }

    @Test fun `LOGIN action shows login button`() {
        composeTestRule.setContent {
            TvTheme {
                TvErrorView(
                    error = ErrorUtils.ErrorAction(
                        message = R.string.error_unknown,
                        buttonMessage = R.string.error_action_login,
                        buttonAction = ButtonAction.LOGIN,
                    ),
                    onLoginClick = {},
                    onRetryClick = {},
                )
            }
        }
        val label = context.getString(R.string.error_action_login)
        composeTestRule.onNodeWithText(label).assertIsDisplayed()
    }

    @Test fun `AGE_CONFIRMATION action shows age button`() {
        composeTestRule.setContent {
            TvTheme {
                TvErrorView(
                    error = ErrorUtils.ErrorAction(
                        message = R.string.error_age_confirmation_needed,
                        buttonMessage = R.string.error_action_confirm,
                        buttonAction = ButtonAction.AGE_CONFIRMATION,
                    ),
                    onRetryClick = {},
                    onAgeConfirmed = {},
                )
            }
        }
        val label = context.getString(R.string.error_action_confirm)
        composeTestRule.onNodeWithText(label).assertIsDisplayed()
    }

    @Test fun `AGE_CONFIRMATION click shows AlertDialog`() {
        composeTestRule.setContent {
            TvTheme {
                TvErrorView(
                    error = ErrorUtils.ErrorAction(
                        message = R.string.error_age_confirmation_needed,
                        buttonMessage = R.string.error_action_confirm,
                        buttonAction = ButtonAction.AGE_CONFIRMATION,
                    ),
                    onRetryClick = {},
                    onAgeConfirmed = {},
                )
            }
        }
        val confirmButton = context.getString(R.string.error_action_confirm)
        composeTestRule.onNodeWithText(confirmButton).performClick()

        val dialogContent = context.getString(R.string.dialog_age_confirmation_content)
        composeTestRule.onNodeWithText(dialogContent, substring = true).assertIsDisplayed()
    }

    @Test fun `ACTION_MESSAGE_HIDE removes button`() {
        composeTestRule.setContent {
            TvTheme {
                TvErrorView(
                    error = ErrorUtils.ErrorAction(
                        message = R.string.error_media_removed_due_to_copyright,
                        buttonMessage = ACTION_MESSAGE_HIDE,
                    ),
                    onRetryClick = {},
                )
            }
        }
        val retryLabel = context.getString(R.string.error_action_retry)
        composeTestRule.onNodeWithText(retryLabel).assertDoesNotExist()
    }

    @Test fun `error message text is displayed`() {
        composeTestRule.setContent {
            TvTheme {
                TvErrorView(
                    error = ErrorUtils.ErrorAction(message = R.string.error_no_network),
                    onRetryClick = {},
                )
            }
        }
        val errorText = context.getString(R.string.error_no_network)
        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()
    }
}
