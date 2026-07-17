package me.proxer.app.media.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.MainActivity
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.grantStoragePermission
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.media.LocalTag
import me.proxer.app.media.TagDao
import me.proxer.app.util.extension.safeInject
import me.proxer.app.util.wrapper.DrawerItem
import me.proxer.library.api.list.ListApi
import me.proxer.library.api.list.MediaSearchEndpoint
import me.proxer.library.entity.list.MediaListEntry
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import me.proxer.library.enums.TagSubType
import me.proxer.library.enums.TagType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.threeten.bp.Instant

@RunWith(AndroidJUnit4::class)
class MediaListScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val tagDao: TagDao by safeInject()

    // Mirrors MediaListViewModelTest: gives both the mediaSearch() and tagList() chains a single named handle
    // to stub against.
    private val listApi = mockk<ListApi>(relaxed = true)

    // ratingSum/ratingAmount are deliberately 0/0: MediaListEntry.rating returns 0f when ratingAmount <= 0, so
    // `if (entry.rating > 0)` suppresses the rating row and there is no time- or format-dependent text to
    // collide with the name assertion. Don't "restore" these to non-zero without a reason.
    private fun entry(id: String, name: String) = MediaListEntry(
        id,
        name,
        setOf("Action"),
        Medium.ANIMESERIES,
        12,
        MediaState.FINISHED,
        0,
        0,
        emptySet(),
    )

    private fun localTag(id: String) = LocalTag(id, TagType.GENRE, "Tag $id", "desc", TagSubType.OTHER, false)

    private fun mockMediaSearchEndpoint(): MediaSearchEndpoint {
        val endpoint = mockk<MediaSearchEndpoint>(relaxed = true)

        every { listApi.mediaSearch() } returns endpoint
        every { endpoint.sort(any()) } returns endpoint
        every { endpoint.name(any()) } returns endpoint
        every { endpoint.language(any()) } returns endpoint
        every { endpoint.genres(any()) } returns endpoint
        every { endpoint.excludedGenres(any()) } returns endpoint
        every { endpoint.fskConstraints(any()) } returns endpoint
        every { endpoint.tags(any()) } returns endpoint
        every { endpoint.excludedTags(any()) } returns endpoint
        every { endpoint.tagRateFilter(any()) } returns endpoint
        every { endpoint.tagSpoilerFilter(any()) } returns endpoint
        every { endpoint.hideFinished(any()) } returns endpoint
        every { endpoint.type(any()) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        // Required for every MainActivity-launched screen; see grantStoragePermission's KDoc.
        grantStoragePermission()

        stubLoggedIn(storageHelper, preferenceHelper)

        every { api.list } returns listApi

        // MediaListScreen's LaunchedEffect calls loadTags() alongside load(). loadTags() evaluates
        // `shouldUpdateTags() || cachedTags.isEmpty()`; Kotlin's `||` is left-to-right, so shouldUpdateTags()
        // -- which reads preferenceHelper.lastTagUpdateDate -- runs on every launch. These two stubs make it
        // take the Single.just(cachedTags) branch, resolving tags in-memory.
        //
        // They are for determinism, not necessity: `api` is a relaxed mock, so nothing here could reach the
        // network either way, and unstubbed the cached branch would likely still be taken -- but only as an
        // artifact of relaxed MockK defaulting `isBefore` to false, not of any date comparison. Stubbing a
        // real Instant makes the cached branch an intentional property of this test rather than a coincidence
        // a MockK default-value change could silently flip.
        every { preferenceHelper.lastTagUpdateDate } returns Instant.now()
        every { tagDao.getTags() } returns listOf(localTag("t0"))
    }

    @Test
    fun success_renders_media_entry_name() {
        val endpoint = mockMediaSearchEndpoint()
        every { endpoint.build() } returns mockProxerCall(listOf(entry("m0", "Entry A")))

        val intent = MainActivity.getSectionIntent(context, DrawerItem.ANIME)

        ActivityScenario.launch<MainActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 15_000) {
                composeTestRule.onAllNodesWithText("Entry A").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Entry A").assertIsDisplayed()
        }
    }
}
