package me.proxer.app.base

import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick

/**
 * Clicks the tab labelled [label] in a `PrimaryScrollableTabRow`.
 *
 * Tabs carry no testTag and no contentDescription, so they can only be matched by their label text -- but a
 * bare `onNodeWithText(label)` is not safe. TopTenScreen renders its section headers from the SAME string
 * resources as Profile's tabs 3 and 4 (`section_user_media_list_anime` / `..._manga`), so while the Favoriten
 * tab is displayed, "Anime" matches two nodes and `onNodeWithText` throws. Filtering by click action
 * disambiguates: the tab is clickable, the section header is not.
 *
 * The click routes through `animateScrollToPage`, so the pager settles asynchronously and the target tab's
 * child screen only starts fetching once its page composes. Callers must await their content with `waitUntil`
 * rather than asserting immediately.
 */
fun ComposeTestRule.switchToTab(label: String) {
    onAllNodesWithText(label).filterToOne(hasClickAction()).performClick()
}
