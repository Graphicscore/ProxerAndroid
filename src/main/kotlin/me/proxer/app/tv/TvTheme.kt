package me.proxer.app.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import me.proxer.app.R

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary      = colorResource(R.color.primary),
            onPrimary    = colorResource(R.color.on_primary),
            secondary    = colorResource(R.color.primary_light),
            background   = Color(0xFF121212),
            onBackground = Color(0xFFE8E8E8),
            surface      = Color(0xFF1E1E1E),
            onSurface    = Color(0xFFE8E8E8),
            error        = Color(0xFFCF6679),

        ),
        content = content
    )
}
