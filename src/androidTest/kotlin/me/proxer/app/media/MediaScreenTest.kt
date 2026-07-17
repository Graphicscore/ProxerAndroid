package me.proxer.app.media

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.R
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.base.switchToTab
import me.proxer.library.api.info.CommentsEndpoint
import me.proxer.library.api.info.EntryEndpoint
import me.proxer.library.api.info.EpisodeInfoEndpoint
import me.proxer.library.api.info.ForumDiscussionsEndpoint
import me.proxer.library.api.info.MediaUserInfoEndpoint
import me.proxer.library.api.info.RecommendationsEndpoint
import me.proxer.library.api.info.RelationsEndpoint
import me.proxer.library.entity.info.AdaptionInfo
import me.proxer.library.entity.info.Comment
import me.proxer.library.entity.info.Entry
import me.proxer.library.entity.info.EpisodeInfo
import me.proxer.library.entity.info.ForumDiscussion
import me.proxer.library.entity.info.MangaEpisode
import me.proxer.library.entity.info.MediaUserInfo
import me.proxer.library.entity.info.RatingDetails
import me.proxer.library.entity.info.Recommendation
import me.proxer.library.entity.info.Relation
import me.proxer.library.enums.Category
import me.proxer.library.enums.License
import me.proxer.library.enums.MediaLanguage
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import me.proxer.library.enums.UserMediaProgress
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * MediaScreen hosts six tabs in a lazily-composed HorizontalPager, so each child screen only fetches once its
 * page composes. Every test launches fresh, switches to one tab, and asserts a field that could only have come
 * from that tab's own endpoint -- one tab per test.
 *
 * Do not merge these into a single walk: PagedViewModel does not null `data` on load, so a revisited paged tab
 * appends against an already-advanced page and stale content can mask a failed fetch. The per-test launch is
 * what keeps each assertion honest; see [setup] for why the stubs themselves are shared.
 */
@RunWith(AndroidJUnit4::class)
class MediaScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val entryId = "1"

    private fun launchMedia() = ActivityScenario.launch<MediaActivity>(
        MediaActivity.getIntent(context, entryId),
    )

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    /**
     * Every endpoint MediaScreen can reach is stubbed for every test, not just the tab under assertion.
     *
     * Two things force this. First, MediaScreen itself owns a MediaInfoViewModel and loads it on every launch
     * regardless of the selected tab, and on success -- when logged in -- that VM also fires `api.info.userInfo`.
     * Second, `animateScrollToPage` composes the pages it travels past, so switching to one tab transiently
     * composes its neighbours: a run that stubbed only `api.info.relations` still crashed inside
     * RecommendationScreen (page 4).
     *
     * An unstubbed call on the relaxed `api` mock does not return null -- it returns a `java.lang.Object_1_Proxy`
     * that ClassCastExceptions the moment production code touches it (`... cannot be cast to
     * me.proxer.library.entity.info.MediaUserInfo`, `... cannot be cast to java.util.List`), killing the process
     * and aborting the whole run. Stubbing everything up front keeps each test's assertion the only variable;
     * the tests still assert one tab each, from that tab's own endpoint.
     */
    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        // RelationViewModel and others read this (not just the Observable variant) to build includeHentai.
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false

        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)

        every { api.info.entry(entryId) } returns entryEndpoint
        every { entryEndpoint.build() } returns mockProxerCall(entry())

        // Fired from MediaInfoViewModel.doAfterSuccess whenever the entry loads and the user is logged in.
        val userInfoEndpoint = mockk<MediaUserInfoEndpoint>(relaxed = true)

        every { api.info.userInfo(entryId) } returns userInfoEndpoint
        every { userInfoEndpoint.build() } returns mockProxerCall(
            MediaUserInfo(
                isNoted = false,
                isFinished = false,
                isCanceled = false,
                isTopTen = false,
                isSubscribed = false,
            ),
        )

        val commentsEndpoint = mockk<CommentsEndpoint>(relaxed = true)

        every { api.info.comments(entryId) } returns commentsEndpoint
        every { commentsEndpoint.sort(any()) } returns commentsEndpoint
        every { commentsEndpoint.page(any()) } returns commentsEndpoint
        every { commentsEndpoint.limit(any()) } returns commentsEndpoint
        every { commentsEndpoint.build() } returns mockProxerCall(listOf(comment("c0")))

        val episodeEndpoint = mockk<EpisodeInfoEndpoint>(relaxed = true)

        every { api.info.episodeInfo(entryId) } returns episodeEndpoint
        every { episodeEndpoint.limit(any()) } returns episodeEndpoint
        every { episodeEndpoint.build() } returns mockProxerCall(episodeInfo())

        val relationsEndpoint = mockk<RelationsEndpoint>(relaxed = true)

        every { api.info.relations(entryId) } returns relationsEndpoint
        every { relationsEndpoint.includeHentai(any()) } returns relationsEndpoint
        // Relation id must NOT equal entryId: RelationViewModel applies filterNot { it.id == entryId }.
        every { relationsEndpoint.build() } returns mockProxerCall(listOf(relation("99")))

        val recommendationsEndpoint = mockk<RecommendationsEndpoint>(relaxed = true)

        every { api.info.recommendations(entryId) } returns recommendationsEndpoint
        every { recommendationsEndpoint.build() } returns mockProxerCall(listOf(recommendation("r0")))

        val discussionsEndpoint = mockk<ForumDiscussionsEndpoint>(relaxed = true)

        every { api.info.forumDiscussions(entryId) } returns discussionsEndpoint
        every { discussionsEndpoint.build() } returns mockProxerCall(listOf(discussion("d0")))
    }

    // ---- tab 0: Info ----

    // ratingSum/ratingAmount are 0/0 so the rating row stays suppressed and cannot collide with assertions.
    private fun entry() = Entry(
        id = entryId,
        name = "Test Anime",
        fskConstraints = emptySet(),
        description = "A test description",
        medium = Medium.ANIMESERIES,
        episodeAmount = 12,
        state = MediaState.FINISHED,
        ratingSum = 0,
        ratingAmount = 0,
        clicks = 10000,
        category = Category.ANIME,
        license = License.LICENSED,
        adaptionInfo = AdaptionInfo(id = "99999", name = "Adaption", medium = Medium.MANGASERIES),
        isAgeRestricted = false,
        synonyms = emptyList(),
        languages = setOf(MediaLanguage.GERMAN_SUB),
        seasons = emptyList(),
        translatorGroups = emptyList(),
        industries = emptyList(),
        tags = emptyList(),
        genres = emptyList(),
    )

    @Test
    fun info_tab_renders_entry_description() {
        launchMedia().use {
            // Tab 0 is the default page -- no switch needed. Note MediaScreen and MediaInfoScreen both resolve
            // the same keyless MediaInfoViewModel and both call load(), so api.info.entry fires twice here.
            // That is by design; never assert verify(exactly = 1) on it.
            awaitText("A test description")
        }
    }

    // ---- tab 1: Comments ----

    private fun comment(id: String) = Comment(
        id,
        entryId,
        "author-$id",
        UserMediaProgress.WATCHED,
        RatingDetails(),
        "Comment $id",
        0,
        1,
        0,
        Date(),
        "Author $id",
        "image.png",
    )

    @Test
    fun comments_tab_renders_comment_author() {
        launchMedia().use {
            composeTestRule.switchToTab(context.getString(R.string.section_comments))

            // The comment BODY renders inside a BBCodeView AndroidView, which publishes no Compose semantics
            // -- onNodeWithText cannot see it. The author is a real Compose Text, so assert that.
            awaitText("Author c0")
        }
    }

    // ---- tab 2: Episodes ----

    // MangaEpisode, not AnimeEpisode: EpisodeRow.title is `(firstEpisode as? MangaEpisode)?.title`, so an
    // AnimeEpisode fixture makes title null and the screen falls back to a localized episode string. A
    // MangaEpisode gives a literal, fetch-derived assertion target.
    private fun episodeInfo() = EpisodeInfo(
        firstEpisode = 1,
        lastEpisode = 1,
        category = Category.MANGA,
        availableLanguages = setOf(MediaLanguage.ENGLISH),
        userProgress = 0,
        episodes = listOf(MangaEpisode(1, MediaLanguage.ENGLISH, "Chapter Alpha")),
    )

    @Test
    fun episodes_tab_renders_episode_title() {
        launchMedia().use {
            // The tab label comes from episodeTabTitleRes(category); getIntent leaves the category extra null,
            // which falls through to the anime branch -> R.string.category_anime_episodes_title ("Episoden").
            // There is no R.string.section_episodes.
            composeTestRule.switchToTab(context.getString(R.string.category_anime_episodes_title))

            awaitText("Chapter Alpha")
        }
    }

    // ---- tab 3: Relations ----

    private fun relation(id: String) = Relation(
        id = id,
        name = "Related Anime $id",
        genres = setOf("Action"),
        fskConstraints = emptySet(),
        description = "A description",
        medium = Medium.ANIMESERIES,
        episodeAmount = 12,
        state = MediaState.FINISHED,
        ratingSum = 0,
        ratingAmount = 0,
        clicks = 500,
        category = Category.ANIME,
        license = License.LICENSED,
        languages = setOf(MediaLanguage.GERMAN_SUB),
        year = 2020,
        season = null,
    )

    @Test
    fun relations_tab_renders_relation_name() {
        launchMedia().use {
            composeTestRule.switchToTab(context.getString(R.string.section_relations))

            awaitText("Related Anime 99")
        }
    }

    // ---- tab 4: Recommendations ----

    private fun recommendation(id: String) = Recommendation(
        id = id,
        name = "Recommended Anime $id",
        genres = setOf("Action"),
        fskConstraints = emptySet(),
        description = "A description",
        medium = Medium.ANIMESERIES,
        episodeAmount = 12,
        state = MediaState.FINISHED,
        ratingSum = 0,
        ratingAmount = 0,
        clicks = 500,
        category = Category.ANIME,
        license = License.LICENSED,
        positiveVotes = 10,
        negativeVotes = 2,
        userVote = null,
    )

    @Test
    fun recommendations_tab_renders_recommendation_name() {
        launchMedia().use {
            composeTestRule.switchToTab(context.getString(R.string.section_recommendations))

            awaitText("Recommended Anime r0")
        }
    }

    // ---- tab 5: Discussions ----

    private fun discussion(id: String) = ForumDiscussion(
        id = id,
        categoryId = "10",
        categoryName = "General",
        subject = "Test Discussion $id",
        postAmount = 5,
        hits = 100,
        firstPostDate = Date(0),
        firstPostUserId = "1",
        firstPostUsername = "user1",
        lastPostDate = Date(0),
        lastPostUserId = "2",
        lastPostUsername = "user2",
    )

    @Test
    fun discussions_tab_renders_discussion_subject() {
        launchMedia().use {
            composeTestRule.switchToTab(context.getString(R.string.section_discussions))

            awaitText("Test Discussion d0")
        }
    }
}
