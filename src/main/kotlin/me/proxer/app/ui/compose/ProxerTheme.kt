package me.proxer.app.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val surface = Color(context.resolveColor(R.attr.colorSurface))
    val onSurface = Color(context.resolveColor(R.attr.colorOnSurface))
    val error = Color(context.resolveColor(R.attr.colorError))

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            background = background,
            surface = surface,
            onSurface = onSurface,
            error = error,
        ),
        content = content,
    )
}
