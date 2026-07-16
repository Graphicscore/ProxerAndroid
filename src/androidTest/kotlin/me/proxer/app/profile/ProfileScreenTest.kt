package me.proxer.app.profile

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
import me.proxer.library.api.user.UserAboutEndpoint
import me.proxer.library.api.user.UserCommentsEndpoint
import me.proxer.library.api.user.UserHistoryEndpoint
import me.proxer.library.api.user.UserInfoEndpoint
import me.proxer.library.api.user.UserMediaListEndpoint
import me.proxer.library.api.user.UserTopTenEndpoint
import me.proxer.library.entity.info.RatingDetails
import me.proxer.library.entity.user.TopTenEntry
import me.proxer.library.entity.user.UserAbout
import me.proxer.library.entity.user.UserComment
import me.proxer.library.entity.user.UserHistoryEntry
import me.proxer.library.entity.user.UserInfo
import me.proxer.library.entity.user.UserMediaListEntry
import me.proxer.library.enums.Category
import me.proxer.library.enums.Gender
import me.proxer.library.enums.MediaLanguage
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import me.proxer.library.enums.RelationshipStatus
import me.proxer.library.enums.UserMediaProgress
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * ProfileScreen hosts seven tabs in a lazily-composed HorizontalPager, so each child screen only fetches once
 * its page composes. Every test launches fresh, switches to one tab, and asserts a field that could only have
 * come from that tab's own endpoint -- one tab per test.
 *
 * All seven ViewModels branch on `storageHelper.user?.matches(userId, username)`; these tests cover the
 * other-profile (`api.user.*`) branch only, forced by `storageHelper.user == null` in [setup]. The `api.ucp.*`
 * own-profile branch is a recorded gap in the Group 2 spec.
 *
 * Do not merge these into a single walk: PagedViewModel does not null `data` on load, so a revisited paged tab
 * appends against an already-advanced page and stale content can mask a failed fetch. The per-test launch is
 * what keeps each assertion honest; see [setup] for why the stubs themselves are shared.
 */
@RunWith(AndroidJUnit4::class)
class ProfileScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val userId = "54321"
    private val username = "testuser"

    private fun launchProfile() = ActivityScenario.launch<ProfileActivity>(
        ProfileActivity.getIntent(context, userId, username),
    )

    private fun awaitText(text: String, substring: Boolean = false) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(text, substring = substring).assertIsDisplayed()
    }

    /**
     * Every endpoint ProfileScreen can reach is stubbed for every test, not just the tab under assertion.
     *
     * Two things force this. First, ProfileScreen itself owns a ProfileViewModel and loads it on every launch
     * regardless of the selected tab (tab 0's ProfileInfoScreen shares that same keyless VM and also loads it),
     * so `api.user.info` fires on every launch. Second, `animateScrollToPage` composes the pages it travels
     * past, so switching to one tab transiently composes its neighbours -- an unstubbed call on the relaxed
     * `api` mock returns a `java.lang.Object_1_Proxy` that ClassCastExceptions the moment production code
     * touches it, killing the process and aborting the whole run. Stubbing everything up front keeps each
     * test's assertion the only variable; the tests still assert one tab each, from that tab's own endpoint.
     */
    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        // Forces the other-profile (api.user.*) branch. LocalUser.matches is an OR over id and case-insensitive
        // username, so stubbing a LocalUser risks silently landing in the api.ucp.* branch and leaving these
        // stubs unfired. Null short-circuits at the `?.` and cannot.
        every { storageHelper.user } returns null

        // Read (non-Observable) by the other-user branches of TopTen, History and ProfileMediaList to build
        // their includeHentai argument.
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false

        // ---- tab 0: Profil ----
        val infoEndpoint = mockk<UserInfoEndpoint>(relaxed = true)

        every { api.user.info(userId, username) } returns infoEndpoint
        every { infoEndpoint.build() } returns mockProxerCall(userInfo())

        // ---- tab 1: Infos ----
        val aboutEndpoint = mockk<UserAboutEndpoint>(relaxed = true)

        every { api.user.about(userId, username) } returns aboutEndpoint
        every { aboutEndpoint.build() } returns mockProxerCall(about())

        // ---- tab 2: Favoriten ----
        // The other-user path fans out AFTER includeHentai: base -> includeHentai -> category(ANIME|MANGA),
        // then zips both singles. Stub only one and the zip never emits -- an infinite spinner, not a clean
        // failure. The includeHentai identity hop is required or category() misses these stubs.
        val topTenBase = mockk<UserTopTenEndpoint>(relaxed = true)
        val topTenAnime = mockk<UserTopTenEndpoint>(relaxed = true)
        val topTenManga = mockk<UserTopTenEndpoint>(relaxed = true)

        every { api.user.topTen(userId, username) } returns topTenBase
        every { topTenBase.includeHentai(any()) } returns topTenBase
        every { topTenBase.category(Category.ANIME) } returns topTenAnime
        every { topTenBase.category(Category.MANGA) } returns topTenManga
        every { topTenAnime.build() } returns
            mockProxerCall(listOf(TopTenEntry("a1", "Favorite Alpha", Category.ANIME, Medium.ANIMESERIES)))
        every { topTenManga.build() } returns
            mockProxerCall(listOf(TopTenEntry("m1", "Favorite Beta", Category.MANGA, Medium.MANGASERIES)))

        // ---- tabs 3 and 4: Anime / Manga ----
        // Both tabs call the SAME api.user.mediaList(userId, username), then split on category(). Keying the two
        // category() results to distinct endpoints lets both media tests share this @Before yet see distinct
        // content. Chain: mediaList -> includeHentai -> category -> filter -> page -> limit -> build.
        val mediaListBase = mockk<UserMediaListEndpoint>(relaxed = true)
        val mediaListAnime = mockMediaListLeaf("AnimeList Alpha", Medium.ANIMESERIES)
        val mediaListManga = mockMediaListLeaf("MangaList Alpha", Medium.MANGASERIES)

        every { api.user.mediaList(userId, username) } returns mediaListBase
        every { mediaListBase.includeHentai(any()) } returns mediaListBase
        every { mediaListBase.category(Category.ANIME) } returns mediaListAnime
        every { mediaListBase.category(Category.MANGA) } returns mediaListManga

        // ---- tab 5: Kommentare ----
        val commentsEndpoint = mockk<UserCommentsEndpoint>(relaxed = true)

        every { api.user.comments(userId, username) } returns commentsEndpoint
        every { commentsEndpoint.category(any()) } returns commentsEndpoint
        // hasContent is a vararg param -- any() does not match it, anyVararg() does.
        every { commentsEndpoint.hasContent(*anyVararg()) } returns commentsEndpoint
        every { commentsEndpoint.page(any()) } returns commentsEndpoint
        every { commentsEndpoint.limit(any()) } returns commentsEndpoint
        every { commentsEndpoint.build() } returns mockProxerCall(listOf(comment("CommentEntry Alpha")))

        // ---- tab 6: Chronik ----
        val historyEndpoint = mockk<UserHistoryEndpoint>(relaxed = true)

        every { api.user.history(userId, username) } returns historyEndpoint
        every { historyEndpoint.includeHentai(any()) } returns historyEndpoint
        every { historyEndpoint.page(any()) } returns historyEndpoint
        every { historyEndpoint.limit(any()) } returns historyEndpoint
        every { historyEndpoint.build() } returns mockProxerCall(listOf(historyEntry("History Alpha")))
    }

    private fun userInfo() = UserInfo(
        id = userId,
        username = username,
        image = "avatar.png",
        isTeamMember = false,
        isDonator = false,
        status = "Status Alpha",
        lastStatusChange = Date(0),
        uploadPoints = 10,
        forumPoints = 20,
        animePoints = 30,
        mangaPoints = 40,
        infoPoints = 5,
        miscPoints = 1,
    )

    private fun about() = UserAbout(
        website = "https://example.com",
        occupation = "Occupation Alpha",
        interests = "Anime",
        city = "Berlin",
        country = "Germany",
        about = "About me",
        facebook = "facebook.user",
        youtube = "youtube.user",
        chatango = "chatango.user",
        twitter = "twitter.user",
        skype = "skype.user",
        deviantart = "deviantart.user",
        birthday = "2000-01-01",
        gender = Gender.UNKNOWN,
        relationshipStatus = RelationshipStatus.UNKNOWN,
    )

    private fun mediaEntry(id: String, name: String, medium: Medium = Medium.ANIMESERIES) = UserMediaListEntry(
        id,
        name,
        12,
        medium,
        MediaState.FINISHED,
        "comment-$id",
        "Comment content",
        UserMediaProgress.WATCHING,
        5,
        0,
    )

    private fun mockMediaListLeaf(name: String, medium: Medium): UserMediaListEndpoint {
        val leaf = mockk<UserMediaListEndpoint>(relaxed = true)

        every { leaf.filter(any()) } returns leaf
        every { leaf.page(any()) } returns leaf
        every { leaf.limit(any()) } returns leaf
        every { leaf.build() } returns mockProxerCall(listOf(mediaEntry("m0", name, medium)))

        return leaf
    }

    private fun comment(entryName: String) = UserComment(
        "c0",
        "entry-1",
        entryName,
        Medium.ANIMESERIES,
        Category.ANIME,
        "author-c0",
        UserMediaProgress.WATCHED,
        RatingDetails(),
        "Comment body",
        0,
        1,
        0,
        Date(),
        "Author c0",
        "image.png",
    )

    private fun historyEntry(name: String) = UserHistoryEntry(
        "h0",
        "entry-h0",
        name,
        MediaLanguage.GERMAN_SUB,
        Medium.ANIMESERIES,
        Category.ANIME,
        1,
    )

    @Test
    fun info_tab_renders_user_status() {
        launchProfile().use {
            // Tab 0 is the default page -- no switch needed. ProfileScreen and ProfileInfoScreen share the same
            // keyless ProfileViewModel and both call load(), so api.user.info fires twice here. That is by
            // design; never assert verify(exactly = 1) on it.
            //
            // The status Text renders as `status + " - " + <relative time>`, so match on the status substring
            // rather than the full concatenated line.
            awaitText("Status Alpha", substring = true)
        }
    }

    @Test
    fun about_tab_renders_occupation() {
        launchProfile().use {
            composeTestRule.switchToTab(context.getString(R.string.section_profile_about))

            // Not `about.about` -- that renders in a ProxerWebView AndroidView with no Compose semantics.
            awaitText("Occupation Alpha")
        }
    }

    @Test
    fun top_ten_tab_renders_entry_name() {
        launchProfile().use {
            composeTestRule.switchToTab(context.getString(R.string.section_top_ten))

            awaitText("Favorite Alpha")
        }
    }

    @Test
    fun anime_list_tab_renders_entry_name() {
        launchProfile().use {
            composeTestRule.switchToTab(context.getString(R.string.section_user_media_list_anime))

            awaitText("AnimeList Alpha")
        }
    }

    @Test
    fun manga_list_tab_renders_entry_name() {
        launchProfile().use {
            composeTestRule.switchToTab(context.getString(R.string.section_user_media_list_manga))

            awaitText("MangaList Alpha")
        }
    }

    @Test
    fun comments_tab_renders_entry_name() {
        launchProfile().use {
            composeTestRule.switchToTab(context.getString(R.string.section_user_comments))

            // The comment BODY renders inside a BBCodeView AndroidView with no Compose semantics; entryName is
            // a real Compose Text, so assert that.
            awaitText("CommentEntry Alpha")
        }
    }

    @Test
    fun history_tab_renders_entry_name() {
        launchProfile().use {
            composeTestRule.switchToTab(context.getString(R.string.section_user_history))

            awaitText("History Alpha")
        }
    }
}
