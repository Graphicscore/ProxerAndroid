package me.proxer.app.profile.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.R
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.stubLoggedIn
import me.proxer.library.ProxerCall
import me.proxer.library.api.ucp.SettingsEndpoint
import me.proxer.library.entity.ucp.UcpSettings
import me.proxer.library.enums.UcpSettingConstraint
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for the one screen in this group whose ViewModel is a plain [androidx.lifecycle.ViewModel]
 * rather than a `BaseViewModel`: [ProfileSettingsViewModel] has no `load()`, and [ProfileSettingsScreen] has
 * no `LaunchedEffect`. The fetch fires from the VM's `init` block, so every stub must be in place before
 * `ActivityScenario.launch` constructs the VM.
 */
@RunWith(AndroidJUnit4::class)
class ProfileSettingsScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    // ProfileSettingsViewModel.init seeds `data` from storageHelper.profileSettings BEFORE refresh() returns,
    // so the cached value alone is enough to get past ProfileSettingsScreen's `settings ?: return@Scaffold`
    // guard. The fetched fixture therefore has to DIFFER from the cached one for anything on screen to
    // distinguish "the network fetch landed" from "only the cache landed" -- see profileVisibility below.
    private val cachedSettings = LocalProfileSettings.default()

    // Round-tripping the production default avoids hand-building UcpSettings' 17 parameters.
    private val fetchedSettings = cachedSettings
        .copy(profileVisibility = UcpSettingConstraint.PRIVATE)
        .toNonLocalSettings()

    private fun mockCall(value: UcpSettings): ProxerCall<UcpSettings> {
        val call = mockk<ProxerCall<UcpSettings>>(relaxed = true)

        // ProxerCallSingle clones the call before executing it, so an unstubbed clone() would hand back a
        // different mock whose safeExecute() is not stubbed.
        every { call.clone() } returns call
        every { call.safeExecute() } returns value

        return call
    }

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        every { storageHelper.profileSettings } returns cachedSettings

        val endpoint = mockk<SettingsEndpoint>(relaxed = true)

        every { api.ucp.settings() } returns endpoint
        every { endpoint.build() } returns mockCall(fetchedSettings)
    }

    @Test
    fun success_renders_ads_category_header_and_fetched_profile_visibility() {
        ActivityScenario.launch(ProfileSettingsActivity::class.java).use {
            val adsHeader = context.getString(R.string.profile_preference_ads_category_title)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(adsHeader).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(adsHeader).assertIsDisplayed()

            // The header above proves only that the screen composed past the null-settings guard -- which the
            // cached value alone already satisfies. The profile row's supporting text is indexed out of
            // profile_settings_constraint_titles by the constraint's ordinal, so asserting on PRIVATE's title
            // ("Nur ich") -- which the cache, being all-DEFAULT ("Standard"), never produces -- is what
            // actually proves the SettingsEndpoint response reached the UI.
            val constraintTitles = context.resources
                .getStringArray(R.array.profile_settings_constraint_titles)
            val privateTitle = constraintTitles[UcpSettingConstraint.PRIVATE.ordinal]

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(privateTitle).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(privateTitle).assertIsDisplayed()
        }
    }
}
