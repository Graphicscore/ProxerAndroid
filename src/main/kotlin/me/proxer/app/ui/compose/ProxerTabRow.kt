package me.proxer.app.ui.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A tab strip that continues the [ProxerTopAppBar] it sits beneath.
 *
 * Material3's tab rows default to `colorScheme.surface`, which leaves a white or grey band abutting
 * the accent-colored bar. The legacy View design put tabs on the accent too — see
 * `Widget.App.TabLayout` in `values/styles.xml`, which uses `on_primary` for both label and
 * indicator — so this restores that, and keeps the bar and its tabs reading as one element.
 *
 * Three things have to be overridden together, not just the container:
 * - `contentColor`, which drives the labels.
 * - the **indicator**, whose default is `colorScheme.primary`. Left alone it paints primary on a
 *   primary container and no tab appears selected at all — worse than the surface-colored strip
 *   this replaces, since the indicator is the only selection affordance the labels don't carry.
 * - the **divider**, whose default is `outlineVariant` — a surface-derived hairline that shows up
 *   as a light line along the bottom of the accent strip. Dropped entirely here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxerTabRow(selectedTabIndex: Int, modifier: Modifier = Modifier.fillMaxWidth(), tabs: @Composable () -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        indicator = { ProxerTabIndicator(selectedTabIndex) },
        divider = {},
        tabs = tabs,
    )
}

/**
 * The horizontally scrollable counterpart to [ProxerTabRow], for screens with more tabs than fit —
 * media detail and profile. Same colors, same reasoning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxerScrollableTabRow(selectedTabIndex: Int, modifier: Modifier = Modifier, tabs: @Composable () -> Unit) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        indicator = { ProxerTabIndicator(selectedTabIndex) },
        divider = {},
        tabs = tabs,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabIndicatorScope.ProxerTabIndicator(selectedTabIndex: Int) {
    TabRowDefaults.PrimaryIndicator(
        modifier = Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = true),
        color = MaterialTheme.colorScheme.onPrimary,
    )
}
