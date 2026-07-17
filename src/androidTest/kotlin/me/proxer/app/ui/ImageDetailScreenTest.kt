package me.proxer.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.proxer.app.R
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.stubLoggedIn
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ImageDetail is a pure display screen (no ViewModel). Its SubsamplingScaleImageView receives a raw http URI,
 * bypassing the mocked OkHttpClient, so the native decode fails and drives hasError = true -> the Refresh icon.
 * The error state is the only Compose-assertable state; it is also the natural terminal state under test.
 *
 * stubLoggedIn is called not for a login gate (there is none) but because BaseActivity.onCreate subscribes
 * preferenceHelper.themeObservable, which must be stubbed or the launch crashes.
 */
@RunWith(AndroidJUnit4::class)
class ImageDetailScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
    }

    @Test
    fun renders_retry_icon_when_image_fails_to_load() {
        val retry = context.getString(R.string.error_action_retry)

        ActivityScenario.launch<ImageDetailActivity>(
            ImageDetailActivity.getIntent(context, "https://proxer.me/nonexistent.jpg".toHttpUrl()),
        ).use {
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithContentDescription(retry).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithContentDescription(retry).assertIsDisplayed()
        }
    }
}
