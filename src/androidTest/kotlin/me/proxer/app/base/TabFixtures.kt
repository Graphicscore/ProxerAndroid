package me.proxer.app.base

import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/**
 * Scrolls the tab labelled [label] into view and clicks it, in a `PrimaryScrollableTabRow`.
 *
 * Tabs carry no testTag and no contentDescription, so they can only be matched by their label text -- but a
 * bare `onNodeWithText(label)` is not safe. TopTenScreen renders its section headers from the SAME string
 * resources as Profile's tabs 3 and 4 (`section_user_media_list_anime` / `..._manga`), so while the Favoriten
 * tab is displayed, "Anime" matches two nodes and `onNodeWithText` throws. Filtering by click action
 * disambiguates: the tab is clickable, the section header is not.
 *
 * [performScrollTo] is REQUIRED, not defensive. The row is scrollable and only the leading tabs fit on screen:
 * on a 1440px-wide API 31 emulator, MediaScreen's six German labels put tab 4 at x=1636 and tab 5 at x=2069,
 * both fully outside the viewport. A fully-clipped node reports `boundsInRoot=Rect(0,0,0,0)`, and
 * [performClick] on it SILENTLY SUCCEEDS while dispatching nothing -- no exception, no tab change, so the only
 * symptom is the caller's `waitUntil` timing out on content that was never going to appear. Scrolling first
 * gives the node real bounds and the click lands. It is a no-op for tabs already on screen.
 *
 * The click routes through `animateScrollToPage`, so the pager settles asynchronously and the target tab's
 * child screen only starts fetching once its page composes. Callers must await their content with `waitUntil`
 * rather than asserting immediately.
 */
fun ComposeTestRule.switchToTab(label: String) {
    onAllNodesWithText(label).filterToOne(hasClickAction()).performScrollTo().performClick()
}
