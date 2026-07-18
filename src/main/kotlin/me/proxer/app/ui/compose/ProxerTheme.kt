package me.proxer.app.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import me.proxer.app.R
import me.proxer.app.util.extension.resolveColor

@Composable
fun ProxerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val primary = Color(context.resolveColor(R.attr.colorPrimary))
    val onPrimary = Color(context.resolveColor(R.attr.colorOnPrimary))
    val secondary = Color(context.resolveColor(R.attr.colorSecondary))
    val onSecondary = Color(context.resolveColor(R.attr.colorOnSecondary))
    val background = Color(context.resolveColor(android.R.attr.colorBackground))
    val onBackground = Color(context.resolveColor(R.attr.colorOnBackground))
    val surface = Color(context.resolveColor(R.attr.colorSurface))
    val onSurface = Color(context.resolveColor(R.attr.colorOnSurface))
    val error = Color(context.resolveColor(R.attr.colorError))

    // The explicit colors above already resolve correctly in both modes: all three theme variants
    // define these attributes DayNight-aware across values/styles.xml and values-night/styles.xml.
    // Picking the *matching* builder is what stops the derived roles Material 3 fills in for us
    // (surfaceVariant, surfaceContainer, outline, scrim, ...) from taking light-mode values at
    // night. Overriding via copy() keeps the explicit set in one place, so the two modes cannot
    // drift apart.
    //
    // isSystemInDarkTheme() reads Configuration.uiMode, which the app drives via
    // AppCompatDelegate.setDefaultNightMode from the theme-variant preference
    // (MainApplication.kt:221, ThemeVariant.kt), so no extra plumbing is needed here.
    val colorScheme = (if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()).copy(
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        error = error,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun ProxerThemePreview() {
    ProxerTheme {
        Text("Preview")
    }
}
