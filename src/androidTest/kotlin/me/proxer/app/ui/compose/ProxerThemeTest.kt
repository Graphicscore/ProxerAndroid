package me.proxer.app.ui.compose

import android.content.res.Configuration
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.proxer.app.R
import me.proxer.app.util.extension.resolveColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that [ProxerTheme] picks the color scheme builder matching the current night mode.
 *
 * Night mode is forced per-subtree by overriding [LocalConfiguration]'s `uiMode`, which is exactly what
 * `isSystemInDarkTheme()` reads -- so a single composition can host both a day and a night [ProxerTheme] at once.
 * That matters because [androidx.compose.ui.test.junit4.ComposeTestRule.setContent] may only be called once per
 * test, hence [captureSchemes] rendering both variants side by side instead of one call per mode.
 *
 * [LocalContext] is deliberately the SAME [ContextThemeWrapper] for both subtrees: their explicit colors come
 * from identical attribute lookups, so any difference observed between the two schemes can only come from the
 * builder choice. That also makes the "identical explicit color set is passed to both branches" requirement
 * directly assertable, and keeps the test independent of the device's own night setting.
 */
@RunWith(AndroidJUnit4::class)
class ProxerThemeTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val themedContext by lazy {
        ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().targetContext, R.style.Theme_App)
    }

    /** Renders [ProxerTheme] once with night mode forced on and once forced off, returning `dark to light`. */
    private fun captureSchemes(): Pair<ColorScheme, ColorScheme> {
        lateinit var dark: ColorScheme
        lateinit var light: ColorScheme

        composeTestRule.setContent {
            WithNightMode(night = true) { ProxerTheme { dark = MaterialTheme.colorScheme } }
            WithNightMode(night = false) { ProxerTheme { light = MaterialTheme.colorScheme } }
        }

        composeTestRule.waitForIdle()

        return dark to light
    }

    @Composable
    private fun WithNightMode(night: Boolean, content: @Composable () -> Unit) {
        val configuration = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }

        CompositionLocalProvider(
            LocalContext provides themedContext,
            LocalConfiguration provides configuration,
            content = content,
        )
    }

    @Test fun `night_mode_uses_the_dark_color_scheme_for_derived_roles`() {
        val (dark, _) = captureSchemes()
        val reference = darkColorScheme()

        assertEquals(reference.surfaceVariant, dark.surfaceVariant)
        assertEquals(reference.onSurfaceVariant, dark.onSurfaceVariant)
        assertEquals(reference.surfaceContainer, dark.surfaceContainer)
        assertEquals(reference.outline, dark.outline)
    }

    @Test fun `day_mode_uses_the_light_color_scheme_for_derived_roles`() {
        val (_, light) = captureSchemes()
        val reference = lightColorScheme()

        assertEquals(reference.surfaceVariant, light.surfaceVariant)
        assertEquals(reference.onSurfaceVariant, light.onSurfaceVariant)
        assertEquals(reference.surfaceContainer, light.surfaceContainer)
        assertEquals(reference.outline, light.outline)
    }

    @Test fun `derived_roles_actually_differ_between_the_two_modes`() {
        // Guards the whole point of the change: were both branches to use the same builder again, the explicit
        // roles would still match everywhere and only this assertion would catch the regression.
        val (dark, light) = captureSchemes()

        assertNotEquals(light.surfaceVariant, dark.surfaceVariant)
        assertNotEquals(light.surfaceContainer, dark.surfaceContainer)
    }

    @Test fun `both_modes_receive_an_identical_explicit_color_set`() {
        val (dark, light) = captureSchemes()

        assertEquals(light.primary, dark.primary)
        assertEquals(light.onPrimary, dark.onPrimary)
        assertEquals(light.secondary, dark.secondary)
        assertEquals(light.onSecondary, dark.onSecondary)
        assertEquals(light.background, dark.background)
        assertEquals(light.onBackground, dark.onBackground)
        assertEquals(light.surface, dark.surface)
        assertEquals(light.onSurface, dark.onSurface)
        assertEquals(light.error, dark.error)
    }

    @Test fun `explicit_roles_are_resolved_from_theme_attributes`() {
        val (dark, _) = captureSchemes()

        assertEquals(Color(themedContext.resolveColor(R.attr.colorPrimary)), dark.primary)
        assertEquals(Color(themedContext.resolveColor(R.attr.colorOnPrimary)), dark.onPrimary)
        assertEquals(Color(themedContext.resolveColor(R.attr.colorSecondary)), dark.secondary)
        assertEquals(Color(themedContext.resolveColor(R.attr.colorOnSecondary)), dark.onSecondary)
        assertEquals(Color(themedContext.resolveColor(android.R.attr.colorBackground)), dark.background)
        // onBackground was previously dropped entirely, so it silently fell back to the builder default.
        assertEquals(Color(themedContext.resolveColor(R.attr.colorOnBackground)), dark.onBackground)
        assertEquals(Color(themedContext.resolveColor(R.attr.colorSurface)), dark.surface)
        assertEquals(Color(themedContext.resolveColor(R.attr.colorOnSurface)), dark.onSurface)
        assertEquals(Color(themedContext.resolveColor(R.attr.colorError)), dark.error)
    }
}
