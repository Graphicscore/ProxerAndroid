package me.proxer.app.ui.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A tab strip that continues the [ProxerTopAppBar] it sits beneath.
 *
 * Material3's tab rows default to `colorScheme.surface`, which leaves a white or grey band abutting
 * the accent-colored bar. The legacy View design put tabs on the accent too — see
 * `Widget.App.TabLayout` in `values/styles.xml`, which uses `on_primary` for both label and
 * indicator — so this restores that, and keeps the bar and its tabs reading as one element.
 */
@Composable
fun ProxerTabRow(selectedTabIndex: Int, modifier: Modifier = Modifier.fillMaxWidth(), tabs: @Composable () -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tabs = tabs,
    )
}

/**
 * The horizontally scrollable counterpart to [ProxerTabRow], for screens with more tabs than fit —
 * media detail and profile. Same colors, same reasoning.
 */
@Composable
fun ProxerScrollableTabRow(selectedTabIndex: Int, modifier: Modifier = Modifier, tabs: @Composable () -> Unit) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tabs = tabs,
    )
}
