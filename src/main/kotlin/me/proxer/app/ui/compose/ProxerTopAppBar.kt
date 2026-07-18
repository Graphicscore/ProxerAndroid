package me.proxer.app.ui.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.proxer.app.R

/**
 * The app's standard top bar: the primary accent as the container, with [MaterialTheme]'s
 * `onPrimary` for the title and every icon slot.
 *
 * Material3's [TopAppBar] otherwise defaults to `colorScheme.surface`, which leaves the bar white in
 * light mode and grey at night while the navigation drawer header sits on the accent color.
 *
 * The only bar that legitimately skips this is the video-player overlay in `StreamScreen`, which
 * needs a translucent container to read over arbitrary video frames.
 *
 * Anything placed in [title] or [actions] that brings its own colors — a text field, a button —
 * still needs styling for an accent-colored background. Use [ProxerTopAppBarSearchField] for search
 * input rather than a bare `TextField`, whose defaults are keyed to `surface` and `primary` and go
 * invisible here.
 *
 * @param onBack when non-null, renders the standard labelled back arrow as the navigation icon.
 * Prefer this over hand-rolling one in [navigationIcon] — it keeps the content description in one
 * place instead of leaving each screen to forget it. Mutually exclusive with [navigationIcon]:
 * passing both is a programming error and throws rather than silently dropping one of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxerTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    require(onBack == null || navigationIcon == null) {
        "ProxerTopAppBar takes either onBack or navigationIcon, not both — one would be discarded."
    }

    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = {
            when {
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }

                navigationIcon != null -> navigationIcon()
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
