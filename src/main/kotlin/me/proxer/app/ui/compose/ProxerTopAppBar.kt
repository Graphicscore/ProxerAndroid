package me.proxer.app.ui.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's standard top bar: the primary accent as the container, with [MaterialTheme]'s
 * `onPrimary` for the title and every icon slot.
 *
 * Material3's [TopAppBar] otherwise defaults to `colorScheme.surface`, which leaves the bar white in
 * light mode and grey at night while the navigation drawer header sits on the accent color.
 *
 * The only bar that legitimately skips this is the video-player overlay in `StreamScreen`, which
 * needs a translucent container to read over arbitrary video frames.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxerTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
