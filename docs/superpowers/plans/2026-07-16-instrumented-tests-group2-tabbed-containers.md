# Instrumented UI Tests — Group 2 (Tabbed Containers) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Instrumented smoke tests for the two tab hosts and all 13 child tabs, plus Topic, EditComment and CreateConference — 16 tests across 5 test files.

**Architecture:** Each test extends `InstrumentedTestBase`, stubs its endpoint on the shared relaxed `ProxerApi` Koin mock, launches the real Activity via its intent factory, and asserts rendered text with `createEmptyComposeRule()` + `waitUntil`. Tab tests click through to their tab first, since `HorizontalPager` composes lazily. Three harness pieces land first: a hoisted `ProxerCall` fixture, a `ProfileActivity.getIntent` factory, and a tab-switch helper.

**Tech Stack:** Kotlin, Compose UI test (`androidx.compose.ui.test.junit4.v2`), MockK (`mockk-android` 1.14.11), Koin 4.2.1, RxJava 2, JUnit4 + `AndroidJUnit4`.

**Spec:** `docs/superpowers/specs/2026-07-15-instrumented-tests-group2-design.md`

---

## Shared Context — read before Task 1

**Environment.** Instrumented tests require **API 31+** (see CLAUDE.md — mockk-android's agent + `ComponentActivity.onPictureInPictureUiStateChanged` crash the process on API 30). The AVD `ci_api31_x86_64` matches CI. `connectedDebugAndroidTest` does **NOT** accept `--tests`; filter with `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.

**Harness rules inherited from Group 1** (all still apply):

- Extend `InstrumentedTestBase`. Koin starts **once per instrumentation process**, so all test classes share the same mock instances; its `@After resetFakeAppModuleMocks()` clears stubs between tests. Don't re-inject `api`/`storageHelper`/`preferenceHelper`/`validators`.
- Call `stubLoggedIn(storageHelper, preferenceHelper)` in `@Before`. **Every screen in this group is login-gated** — no ViewModel under `media/` or `profile/` overrides `isLoginRequired`. Without it, `preferenceHelper.themeObservable` is unstubbed and `BaseActivity.onCreate`'s `.autoDisposable(scope())` chain crashes the screen on launch.
- `grantStoragePermission()` is **only** for `MainActivity`-launched screens. None of this group's screens is MainActivity-hosted — do **not** add it here.
- `src/test/` helpers (`ProxerEndpointTestUtils.kt`, `RxTrampolineRule`, `koin-test`'s `inject()`/`KoinTestRule`, `InstantTaskExecutorRule`) are on the **unit-test** classpath only, not androidTest.
- Non-null `Endpoint<T>` → stub `call.safeExecute()`. Nullable `Endpoint<T?>` → stub `call.execute()`.
- Relaxed mocks do **not** return `this` from builders — stub every builder in a chain or it yields a foreign mock.
- All UI strings are German. Resolve with `context.getString(...)`, never hardcode.
- **Drop every `mockkObject(TextPrototype)` block** the JVM tests carry. Those exist because `SpannableStringBuilder` is an unmocked stub on the JVM classpath. On-device it's real, and mocking it would suppress the BBCode path under test.

**Timeouts.** These screens are Activity-hosted but lighter than `MainActivity`. Start at `5_000`, matching the group's own-activity screens (`NotificationScreenTest`, `ServerStatusScreenTest`, `ProfileSettingsScreenTest`). Raise only with a real logcat measurement **and** a comment.

**Tabs compose lazily.** `HorizontalPager` with `beyondViewportPageCount` unset (0); each child fetches from its own `LaunchedEffect(Unit) { load() }`. Only the settled page composes. So: stub the tab's endpoint → launch → `switchToTab(label)` → assert. **One test per tab, one tab per test.** Do not "optimise" into a single walk — `PagedViewModel` doesn't null `data` on load, so revisiting a paged tab appends against an already-advanced `page` and stale content can make assertions pass.

**Expect double-loads.** `MediaScreen:49` and `MediaInfoScreen:66` both resolve `MediaInfoViewModel` with no Koin `key` from the same Activity store — the same instance — and both run `LaunchedEffect(Unit) { load() }`. A tab-0 launch fires `api.info.entry()` **twice**. Same for `ProfileViewModel`. Never write `verify(exactly = 1)` against these.

**Profile: force the other-profile branch with `every { storageHelper.user } returns null`.** Four Profile tabs branch on `storageHelper.user?.matches(userId, username)`. `LocalUser.matches` is an **OR** over id and case-insensitive username, so stubbing a `LocalUser` risks silently landing in the `api.ucp.*` branch and leaving the `api.user.*` stubs unfired. `returns null` short-circuits at the `?.` and is immune to that. (This is `ProfileViewModelTest.kt:69`'s idiom; `ProfileMediaListViewModelTest`/`HistoryViewModelTest` use the inverse idiom — do not copy those.)

---

### Task 1: Hoist the ProxerCall fixture

**Goal:** One shared `ProxerCall` mock fixture, replacing six copies, before 16 more call sites entrench the duplication.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/base/ProxerCallFixtures.kt`
- Modify: `src/androidTest/kotlin/me/proxer/app/notification/NotificationScreenTest.kt`
- Modify: `src/androidTest/kotlin/me/proxer/app/news/NewsScreenTest.kt`
- Modify: `src/androidTest/kotlin/me/proxer/app/bookmark/BookmarkScreenTest.kt`
- Modify: `src/androidTest/kotlin/me/proxer/app/anime/schedule/ScheduleScreenTest.kt`
- Modify: `src/androidTest/kotlin/me/proxer/app/media/list/MediaListScreenTest.kt`
- Modify: `src/androidTest/kotlin/me/proxer/app/profile/settings/ProfileSettingsScreenTest.kt`

**Acceptance Criteria:**
- [ ] `mockProxerCall`, `mockProxerErrorCall` and `mockProxerNullableCall` exist as top-level functions in `me.proxer.app.base`
- [ ] All six existing test files use them; no local `mockCall`/`mockErrorCall` definitions remain in `src/androidTest`
- [ ] The clone-gotcha explanation lives in exactly one place (the fixture's KDoc)
- [ ] The full suite still passes: 30/30

**Verify:** `./gradlew connectedDebugAndroidTest` → `BUILD SUCCESSFUL`, 30 tests, 0 failed

**Steps:**

- [ ] **Step 1: Create the fixture**

Create `src/androidTest/kotlin/me/proxer/app/base/ProxerCallFixtures.kt`:

```kotlin
package me.proxer.app.base

import io.mockk.every
import io.mockk.mockk
import me.proxer.library.ProxerCall
import me.proxer.library.ProxerException

/**
 * Mocks a [ProxerCall] returning [value] from a NON-NULL endpoint (`Endpoint<T>`).
 *
 * `clone()` must be stubbed to return the same mock: `ProxerCallSingle.subscribeActual` clones the call before
 * executing it, so an unstubbed relaxed `clone()` hands back a DIFFERENT mock whose `safeExecute()` is not
 * stubbed, and the single never emits. This is the single most common way these tests fail silently.
 *
 * Non-null endpoints resolve through `ProxerCallSingle`, which calls `safeExecute()`. Nullable endpoints
 * (`Endpoint<T?>`) resolve through `ProxerCallNullableSingle`, which calls `execute()` -- use
 * [mockProxerNullableCall] for those, or the stub is inert.
 */
fun <T : Any> mockProxerCall(value: T): ProxerCall<T> {
    val call = mockk<ProxerCall<T>>(relaxed = true)

    every { call.clone() } returns call
    every { call.safeExecute() } returns value

    return call
}

/**
 * Mocks a [ProxerCall] from a non-null endpoint that fails with [exception]. See [mockProxerCall] for why
 * `clone()` is stubbed.
 */
fun <T : Any> mockProxerErrorCall(
    exception: ProxerException = ProxerException(ProxerException.ErrorType.IO),
): ProxerCall<T> {
    val call = mockk<ProxerCall<T>>(relaxed = true)

    every { call.clone() } returns call
    every { call.safeExecute() } throws exception

    return call
}

/**
 * Mocks a [ProxerCall] returning [value] from a NULLABLE endpoint (`Endpoint<T?>`).
 *
 * These resolve through `ProxerCallNullableSingle`, which calls `execute()` rather than `safeExecute()` --
 * stubbing `safeExecute()` on this path is inert and the call never resolves.
 */
fun <T : Any> mockProxerNullableCall(value: T? = null): ProxerCall<T?> {
    val call = mockk<ProxerCall<T?>>(relaxed = true)

    every { call.clone() } returns call
    every { call.execute() } returns value

    return call
}
```

- [ ] **Step 2: Migrate the six files**

In each file: delete its local `mockCall(...)` / `mockErrorCall(...)` private function and the now-duplicated clone comment, add `import me.proxer.app.base.mockProxerCall` (and `mockProxerErrorCall` where the file has an error variant), and replace call sites.

`NotificationScreenTest.kt` has both a success and an error variant — `mockCall(...)` → `mockProxerCall(...)`, `mockErrorCall()` → `mockProxerErrorCall()`. The other five have success only.

Type inference: `mockProxerCall(listOf(article("n0")))` infers `ProxerCall<List<NewsArticle>>`. For `mockProxerErrorCall` the type must be supplied explicitly at the `returns` site if inference fails: `every { endpoint.build() } returns mockProxerErrorCall<List<ProxerNotification>>()`.

- [ ] **Step 3: Verify the full suite still passes**

Run: `./gradlew connectedDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`, 30 tests, 0 failed.

This is a pure refactor of a green suite. Any failure is a real regression from the migration — fix it, don't work around it.

- [ ] **Step 4: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/base/ProxerCallFixtures.kt src/androidTest/kotlin/me/proxer/app
git commit -m "test: hoist ProxerCall mock into a shared androidTest fixture"
```

---

### Task 2: Add ProfileActivity.getIntent

**Goal:** Give `ProfileActivity` the public intent factory its tests need, following the convention the other Activities already set.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/profile/ProfileActivity.kt`

**Acceptance Criteria:**
- [ ] `ProfileActivity.getIntent(context: Context, userId: String? = null, username: String? = null): Intent` exists and is public
- [ ] The existing `navigateTo(...)` delegates to it rather than building the intent inline
- [ ] `navigateTo` keeps its `Activity` parameter and its shared-element transition behaviour unchanged
- [ ] `./gradlew compileDebugKotlin` passes

**Verify:** `./gradlew compileDebugKotlin` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Read the current companion**

Read `src/main/kotlin/me/proxer/app/profile/ProfileActivity.kt:19-40`. It currently has private `USER_ID_EXTRA = "user_id"` / `USERNAME_EXTRA = "username"` consts and a `navigateTo(context: Activity, userId: String?, username: String?, image: String?, imageView: ImageView?)` that builds the intent inline, no-ops when both `userId` and `username` are blank, and calls `ActivityUtils.navigateToWithImageTransition`.

- [ ] **Step 2: Add getIntent and delegate**

Mirror `MediaActivity.kt:39-44`. Add to the companion:

```kotlin
fun getIntent(context: Context, userId: String? = null, username: String? = null): Intent =
    context.intentFor<ProfileActivity>(
        USER_ID_EXTRA to userId,
        USERNAME_EXTRA to username,
    )
```

Then rewrite `navigateTo`'s intent construction to call `getIntent(context, userId, username)` and add the `image` extra onto the returned intent before passing it to `ActivityUtils.navigateToWithImageTransition`. Keep the blank-guard and the transition exactly as they are — this task changes *where the intent is built*, nothing else.

`getIntent` takes `Context` (not `Activity`) because it does no transition; `navigateTo` keeps `Activity` because it does. This matches `MediaActivity`.

Add `import android.content.Context` and `import android.content.Intent` if absent.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Confirm no behaviour change to navigateTo**

Read the rewritten `navigateTo` and confirm: the blank-guard still short-circuits, the image extra is still attached, and `navigateToWithImageTransition` still receives the same intent it did before. This is a production file — a regression here breaks real navigation, not just tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/profile/ProfileActivity.kt
git commit -m "feat: add ProfileActivity.getIntent factory"
```

---

### Task 3: Add the tab-switch helper

**Goal:** One helper that switches tabs by label and survives the label-collision trap, so 13 tab tests don't each solve it.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/base/TabFixtures.kt`

**Acceptance Criteria:**
- [ ] `ComposeTestRule.switchToTab(label: String)` exists in `me.proxer.app.base`
- [ ] It disambiguates by click action, not by node index
- [ ] Its KDoc records why (the TopTen header collision)
- [ ] Compiles: `./gradlew assembleDebugAndroidTest` passes

**Verify:** `./gradlew assembleDebugAndroidTest` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Create the helper**

Create `src/androidTest/kotlin/me/proxer/app/base/TabFixtures.kt`:

```kotlin
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
 * The click routes through `animateScrollToPage`, so the pager settles asynchronously. Callers must await
 * their tab's content with `waitUntil` rather than asserting immediately.
 */
fun ComposeTestRule.switchToTab(label: String) {
    onAllNodesWithText(label).filterToOne(hasClickAction()).performClick()
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`. (It has no test of its own — Task 4 is its first real exercise.)

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/base/TabFixtures.kt
git commit -m "test: add tab-switch fixture for instrumented tab-host tests"
```

---

### Task 4: MediaScreen tab tests

**Goal:** Six tests, one per Media tab, each asserting its tab renders content from its own stubbed endpoint.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/media/MediaScreenTest.kt`

**Acceptance Criteria:**
- [ ] Six tests, one per tab (Info, Comments, Episodes, Relations, Recommendations, Discussions)
- [ ] Tab 0 asserts without switching; tabs 1-5 use `switchToTab` first
- [ ] Each asserts a fetch-derived entity field, never an intent extra
- [ ] Launches via `MediaActivity.getIntent`
- [ ] No `verify(exactly = 1)` on `api.info.entry` (it is called twice by design)

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.media.MediaScreenTest` → `BUILD SUCCESSFUL`, 6 tests passed

**Steps:**

- [ ] **Step 1: Write the test file**

Create `src/androidTest/kotlin/me/proxer/app/media/MediaScreenTest.kt`:

```kotlin
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
import me.proxer.library.api.info.RecommendationsEndpoint
import me.proxer.library.api.info.RelationsEndpoint
import me.proxer.library.entity.info.AdaptionInfo
import me.proxer.library.entity.info.Comment
import me.proxer.library.entity.info.Entry
import me.proxer.library.entity.info.EpisodeInfo
import me.proxer.library.entity.info.ForumDiscussion
import me.proxer.library.entity.info.MangaEpisode
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
 * page settles. Every test therefore stubs one tab's endpoint, launches, switches to that tab, and asserts --
 * one tab per test. Do not merge these into a single walk: PagedViewModel does not null `data` on load, so a
 * revisited paged tab appends against an already-advanced page and stale content can mask a failed fetch.
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

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        // RelationViewModel and others read this (not just the Observable variant) to build includeHentai.
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
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
        val endpoint = mockk<EntryEndpoint>(relaxed = true)

        every { api.info.entry(entryId) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(entry())

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
        val endpoint = mockk<CommentsEndpoint>(relaxed = true)

        every { api.info.comments(entryId) } returns endpoint
        every { endpoint.sort(any()) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(listOf(comment("c0")))

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
        val endpoint = mockk<EpisodeInfoEndpoint>(relaxed = true)

        every { api.info.episodeInfo(entryId) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(episodeInfo())

        launchMedia().use {
            // The tab label depends on the category intent extra, which getIntent leaves null -> "Episoden".
            composeTestRule.switchToTab(context.getString(R.string.section_episodes))

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
        val endpoint = mockk<RelationsEndpoint>(relaxed = true)

        every { api.info.relations(entryId) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        // Relation id must NOT equal entryId: RelationViewModel applies filterNot { it.id == entryId }.
        every { endpoint.build() } returns mockProxerCall(listOf(relation("99")))

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
        val endpoint = mockk<RecommendationsEndpoint>(relaxed = true)

        every { api.info.recommendations(entryId) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(listOf(recommendation("r0")))

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
        val endpoint = mockk<ForumDiscussionsEndpoint>(relaxed = true)

        every { api.info.forumDiscussions(entryId) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(listOf(discussion("d0")))

        launchMedia().use {
            composeTestRule.switchToTab(context.getString(R.string.section_discussions))

            awaitText("Test Discussion d0")
        }
    }
}
```

- [ ] **Step 2: Confirm the tab-label resource names**

Before running, verify each `R.string.section_*` used above exists and matches the label `MediaScreen.kt:56-63` builds. Read `MediaScreen.kt:56-63` and `src/main/res/values/strings.xml`. Tab 2's label comes from `episodeTabTitleRes(category)` — with `category` null (getIntent leaves it unset) it resolves to the "Episoden" resource. If the resource name differs from `section_episodes`, correct the test and say so.

- [ ] **Step 3: Run**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.media.MediaScreenTest`
Expected: `BUILD SUCCESSFUL`, 6 tests passed.

Likely failures worth reporting rather than working around:
- Timeout on a switched tab → `switchToTab` matched the wrong node, or the endpoint chain has an unstubbed builder returning a foreign mock.
- Tab 0 times out → the double-load may be racing; check logcat for a `ProxerException` before assuming the stub is wrong.

- [ ] **Step 4: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/media/MediaScreenTest.kt
git commit -m "test: add MediaScreen instrumented tab tests"
```

---

### Task 5: ProfileScreen tab tests

**Goal:** Seven tests, one per Profile tab, all exercising the other-profile (`api.user.*`) branch.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/profile/ProfileScreenTest.kt`

**Acceptance Criteria:**
- [ ] Seven tests, one per tab (Profil, Infos, Favoriten, Anime, Manga, Kommentare, Chronik)
- [ ] `every { storageHelper.user } returns null` in `@Before` forces the other-profile branch
- [ ] The Favoriten test stubs BOTH zipped category endpoints
- [ ] Launches via the new `ProfileActivity.getIntent`
- [ ] No `verify(exactly = 1)` on `api.user.info` (called twice by design)

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.profile.ProfileScreenTest` → `BUILD SUCCESSFUL`, 7 tests passed

**Steps:**

- [ ] **Step 1: Write the test file**

Create `src/androidTest/kotlin/me/proxer/app/profile/ProfileScreenTest.kt`:

```kotlin
package me.proxer.app.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.anyVararg
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
 * ProfileScreen hosts seven tabs in a lazily-composed HorizontalPager -- one tab per test, each stubbing only
 * its own endpoint.
 *
 * Four of these ViewModels branch on `storageHelper.user?.matches(userId, username)` and hit a completely
 * different endpoint depending on whether the profile is the logged-in user's own. These tests cover the
 * other-profile (api.user.*) branch only; the api.ucp.* branch is a recorded gap in the Group 2 spec.
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

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        // Forces the other-profile (api.user.*) branch. LocalUser.matches is an OR over id and
        // case-insensitive username, so stubbing a LocalUser risks silently landing in the api.ucp.* branch
        // and leaving these stubs unfired. Null short-circuits at the `?.` and cannot.
        every { storageHelper.user } returns null

        // Read (non-Observable) by the other-user branches of TopTen, History and ProfileMediaList to build
        // their includeHentai argument.
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
    }

    // ---- tab 0: Profil ----

    @Test
    fun info_tab_renders_user_status() {
        val endpoint = mockk<UserInfoEndpoint>(relaxed = true)
        val userInfo = UserInfo(
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

        every { api.user.info(userId, username) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(userInfo)

        launchProfile().use {
            // Tab 0 is the default page. ProfileScreen and ProfileInfoScreen share a keyless ProfileViewModel
            // and both call load(), so api.user.info fires twice -- by design, never verify(exactly = 1).
            awaitText("Status Alpha")
        }
    }

    // ---- tab 1: Infos ----

    @Test
    fun about_tab_renders_occupation() {
        val endpoint = mockk<UserAboutEndpoint>(relaxed = true)
        val about = UserAbout(
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

        every { api.user.about(userId, username) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(about)

        launchProfile().use {
            composeTestRule.switchToTab(context.getString(R.string.section_profile_about))

            // Not `about.about` -- that renders in a ProxerWebView AndroidView with no Compose semantics.
            awaitText("Occupation Alpha")
        }
    }

    // ---- tab 2: Favoriten ----

    @Test
    fun top_ten_tab_renders_entry_name() {
        // The other-user path fans out AFTER includeHentai: base -> includeHentai -> category(ANIME|MANGA),
        // then zips both singles. Stub only one and the zip never emits -- an infinite spinner, not a
        // clean failure. The includeHentai identity hop is required or category() misses these stubs.
        val baseEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)
        val animeEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)
        val mangaEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)

        every { api.user.topTen(userId, username) } returns baseEndpoint
        every { baseEndpoint.includeHentai(any()) } returns baseEndpoint
        every { baseEndpoint.category(Category.ANIME) } returns animeEndpoint
        every { baseEndpoint.category(Category.MANGA) } returns mangaEndpoint
        every { animeEndpoint.build() } returns
            mockProxerCall(listOf(TopTenEntry("a1", "Favorite Alpha", Category.ANIME, Medium.ANIMESERIES)))
        every { mangaEndpoint.build() } returns
            mockProxerCall(listOf(TopTenEntry("m1", "Favorite Beta", Category.MANGA, Medium.MANGASERIES)))

        launchProfile().use {
            composeTestRule.switchToTab(context.getString(R.string.section_top_ten))

            awaitText("Favorite Alpha")
        }
    }

    // ---- tabs 3 and 4: Anime / Manga ----

    private fun mediaEntry(id: String, name: String) = UserMediaListEntry(
        id,
        name,
        12,
        Medium.ANIMESERIES,
        MediaState.FINISHED,
        "comment-$id",
        "Comment content",
        UserMediaProgress.WATCHING,
        5,
        0,
    )

    private fun mockMediaListEndpoint(): UserMediaListEndpoint {
        val endpoint = mockk<UserMediaListEndpoint>(relaxed = true)

        every { api.user.mediaList(userId, username) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        every { endpoint.category(any()) } returns endpoint
        every { endpoint.filter(any()) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint

        return endpoint
    }

    @Test
    fun anime_list_tab_renders_entry_name() {
        val endpoint = mockMediaListEndpoint()

        every { endpoint.build() } returns mockProxerCall(listOf(mediaEntry("m0", "AnimeList Alpha")))

        launchProfile().use {
            composeTestRule.switchToTab(context.getString(R.string.section_user_media_list_anime))

            awaitText("AnimeList Alpha")
        }
    }

    @Test
    fun manga_list_tab_renders_entry_name() {
        val endpoint = mockMediaListEndpoint()

        every { endpoint.build() } returns mockProxerCall(listOf(mediaEntry("m1", "MangaList Alpha")))

        launchProfile().use {
            composeTestRule.switchToTab(context.getString(R.string.section_user_media_list_manga))

            awaitText("MangaList Alpha")
        }
    }

    // ---- tab 5: Kommentare ----

    @Test
    fun comments_tab_renders_entry_name() {
        val endpoint = mockk<UserCommentsEndpoint>(relaxed = true)
        val comment = UserComment(
            "c0",
            "entry-1",
            "CommentEntry Alpha",
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

        every { api.user.comments(userId, username) } returns endpoint
        every { endpoint.category(any()) } returns endpoint
        // hasContent is a vararg param -- any() does not match it, anyVararg() does.
        every { endpoint.hasContent(*anyVararg()) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(listOf(comment))

        launchProfile().use {
            composeTestRule.switchToTab(context.getString(R.string.section_user_comments))

            // The comment body is a BBCodeView AndroidView with no Compose semantics; entryName is real Text.
            awaitText("CommentEntry Alpha")
        }
    }

    // ---- tab 6: Chronik ----

    @Test
    fun history_tab_renders_entry_name() {
        val endpoint = mockk<UserHistoryEndpoint>(relaxed = true)
        val entry = UserHistoryEntry(
            "h0",
            "entry-h0",
            "History Alpha",
            MediaLanguage.GERMAN_SUB,
            Medium.ANIMESERIES,
            Category.ANIME,
            1,
        )

        every { api.user.history(userId, username) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(listOf(entry))

        launchProfile().use {
            composeTestRule.switchToTab(context.getString(R.string.section_user_history))

            awaitText("History Alpha")
        }
    }
}
```

- [ ] **Step 2: Confirm tab-label resources**

Verify each `R.string.section_*` matches the labels `ProfileScreen.kt:53-61` builds. If any name differs, correct the test and say so.

- [ ] **Step 3: Run**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.profile.ProfileScreenTest`
Expected: `BUILD SUCCESSFUL`, 7 tests passed.

If the Favoriten test hangs to timeout, the zip isn't emitting — check both `category()` stubs fired and that the `includeHentai` identity hop is present.

- [ ] **Step 4: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/profile/ProfileScreenTest.kt
git commit -m "test: add ProfileScreen instrumented tab tests"
```

---

### Task 6: TopicScreen test

**Goal:** One test asserting `TopicScreen` renders a post's username from a stubbed endpoint.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/forum/TopicScreenTest.kt`

**Acceptance Criteria:**
- [ ] Launches via `TopicActivity.getIntent`
- [ ] Asserts `post.username`, NOT the toolbar subject (which falls back to the `topic` intent extra)
- [ ] No `mockkObject(TextPrototype)` — BBCode parses for real on-device
- [ ] Smoke scope only: 1 test, no error/pagination

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.forum.TopicScreenTest` → `BUILD SUCCESSFUL`, 1 test passed

**Steps:**

- [ ] **Step 1: Write the test**

Create `src/androidTest/kotlin/me/proxer/app/forum/TopicScreenTest.kt`:

```kotlin
package me.proxer.app.forum

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.library.api.forum.TopicEndpoint
import me.proxer.library.entity.forum.Post
import me.proxer.library.entity.forum.Topic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * The post BODY renders through a BBCodeView AndroidView, which publishes no Compose semantics, so
 * onNodeWithText cannot see it -- assert on the username instead. Unlike the JVM TopicViewModelTest, no
 * mockkObject(TextPrototype) is needed: android.text is real on-device, and TopicScreen supplies a real
 * Resources to toParsedPost itself.
 */
@RunWith(AndroidJUnit4::class)
class TopicScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val topicId = "topic-1"

    private fun post(id: String) = Post(
        id,
        "0",
        "user-$id",
        "User $id",
        "image.png",
        Date(),
        null,
        null,
        null,
        null,
        null,
        "Message $id",
        0,
    )

    private fun topic(posts: List<Post>) = Topic(
        "cat-1",
        "Category",
        "Subject",
        false,
        posts.size,
        10,
        Date(),
        Date(),
        posts,
    )

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
    }

    @Test
    fun success_renders_post_username() {
        val endpoint = mockk<TopicEndpoint>(relaxed = true)

        every { api.forum.topic(topicId) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(topic(listOf(post("p0"))))

        val intent = TopicActivity.getIntent(context, topicId, "cat-1")

        ActivityScenario.launch<TopicActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("User p0").fetchSemanticsNodes().isNotEmpty()
            }

            // Do NOT assert the toolbar subject: it falls back to the `topic` intent extra, so it would pass
            // without the fetch landing at all.
            composeTestRule.onNodeWithText("User p0").assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.forum.TopicScreenTest`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/forum/TopicScreenTest.kt
git commit -m "test: add TopicScreen instrumented smoke test"
```

---

### Task 7: EditCommentScreen test

**Goal:** One test asserting `EditCommentScreen` loads a comment, with the draft path explicitly neutralised.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/comment/EditCommentScreenTest.kt`

**Acceptance Criteria:**
- [ ] Launches via `EditCommentActivity.Contract().createIntent(...)` (there is no `getIntent`)
- [ ] `storageHelper.getCommentDraft(any())` is explicitly stubbed to `null`
- [ ] Asserts the loaded comment's content, which the draft path could otherwise override
- [ ] Smoke scope only: 1 test, no publish/update path

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.comment.EditCommentScreenTest` → `BUILD SUCCESSFUL`, 1 test passed

**Steps:**

- [ ] **Step 1: Write the test**

Create `src/androidTest/kotlin/me/proxer/app/comment/EditCommentScreenTest.kt`:

```kotlin
package me.proxer.app.comment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.library.api.comment.CommentEndpoint
import me.proxer.library.entity.info.Comment
import me.proxer.library.entity.info.RatingDetails
import me.proxer.library.enums.UserMediaProgress
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * EditCommentActivity has no getIntent -- its only public construction path is the ActivityResultContract.
 * Launching it bare would crash: EditCommentViewModel.init does `require(id != null || entryId != null)`.
 *
 * The draft path is deliberately neutralised. EditCommentViewModel overlays
 * storageHelper.getCommentDraft(entryId) onto the fetched comment, and EditCommentActivity.onDestroy WRITES a
 * draft whenever create-mode content is non-blank -- so a previous test can leave a draft that makes this one
 * pass with its network stub removed. clearMocks in InstrumentedTestBase resets the stub to relaxed baseline,
 * which for a String? return is "" rather than null, so stub it explicitly rather than relying on that.
 */
@RunWith(AndroidJUnit4::class)
class EditCommentScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val commentId = "42"

    private fun comment() = Comment(
        id = commentId,
        entryId = "100",
        authorId = "7",
        mediaProgress = UserMediaProgress.WATCHED,
        ratingDetails = RatingDetails(genre = 1, story = 2, animation = 3, characters = 4, music = 5),
        content = "Comment Content Alpha",
        overallRating = 8,
        episode = 5,
        helpfulVotes = 3,
        date = Date(),
        author = "Someone",
        image = "avatar.png",
    )

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        every { storageHelper.getCommentDraft(any()) } returns null
    }

    @Test
    fun success_renders_loaded_comment_content() {
        val endpoint = mockk<CommentEndpoint>(relaxed = true)

        every { api.comment.comment(commentId, null) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(comment())

        val intent = EditCommentActivity.Contract().createIntent(
            context,
            EditCommentActivity.Contract.Input(id = commentId),
        )

        ActivityScenario.launch<EditCommentActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("Comment Content Alpha").fetchSemanticsNodes().isNotEmpty()
            }

            // The editor is an OutlinedTextField, so its content IS a real Compose text node -- unlike the
            // preview tab, which renders through BBCodeView.
            composeTestRule.onNodeWithText("Comment Content Alpha").assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.comment.EditCommentScreenTest`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 3: Prove the test has teeth (negative control)**

Temporarily change the endpoint stub to throw:

```kotlin
every { endpoint.build() } returns me.proxer.app.base.mockProxerErrorCall<Comment>()
```

Run again. It MUST fail with a `ComposeTimeoutException`. If it still passes, the assertion is vacuous — a draft or a default comment is supplying the text — and that must be reported, not papered over. Restore the success stub afterwards and confirm green.

- [ ] **Step 4: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/comment/EditCommentScreenTest.kt
git commit -m "test: add EditCommentScreen instrumented smoke test"
```

---

### Task 8: CreateConferenceScreen test

**Goal:** One test asserting the create endpoint is invoked — the only API-observable behaviour this screen has.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/chat/prv/create/CreateConferenceScreenTest.kt`

**Acceptance Criteria:**
- [ ] Launches `CreateConferenceActivity` bare (it defaults to 1:1 chat mode and does not crash)
- [ ] Asserts the endpoint is invoked after entering a message and submitting
- [ ] Does NOT assert navigation (that needs a WorkManager sync event and a Room row — Group 4)
- [ ] Smoke scope only: 1 test

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.chat.prv.create.CreateConferenceScreenTest` → `BUILD SUCCESSFUL`, 1 test passed

**Steps:**

- [ ] **Step 1: Read the screen first**

This test is unavoidably thin and its shape depends on the screen's actual input affordances. Read `src/main/kotlin/me/proxer/app/chat/prv/create/CreateConferenceScreen.kt` fully before writing, specifically: the participant field, the message field (label `R.string.fragment_messenger_message`), and the submit action. Note the title/participant/message text all come from intent extras or local state — **none of it is fetch-derived**, so the only honest assertion is that `api.messenger.createConference(...)` was invoked.

- [ ] **Step 2: Write the test**

Create `src/androidTest/kotlin/me/proxer/app/chat/prv/create/CreateConferenceScreenTest.kt`:

```kotlin
package me.proxer.app.chat.prv.create

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.R
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.library.api.messenger.CreateConferenceEndpoint
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This screen renders nothing fetch-derived: its title, participant list and message field all come from
 * intent extras or local UI state. The only API-observable signals are the loading spinner and the error
 * snackbar, so this test asserts the endpoint is invoked rather than asserting rendered content.
 *
 * It deliberately does NOT assert navigation. On success the VM only stores a conference id and enqueues a
 * MessengerWorker sync; `result` is set from an RxBus SynchronizationEvent plus a Room lookup, both of which
 * are Group 4 fixtures.
 */
@RunWith(AndroidJUnit4::class)
class CreateConferenceScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
    }

    @Test
    fun submitting_invokes_the_create_conference_endpoint() {
        val endpoint = mockk<CreateConferenceEndpoint>(relaxed = true)

        every { api.messenger.createConference(any(), any()) } returns endpoint
        every { endpoint.build() } returns mockProxerCall("123")

        // Bare launch is correct here: isGroup defaults false and initialParticipant null -> 1:1 chat mode.
        ActivityScenario.launch(CreateConferenceActivity::class.java).use {
            composeTestRule
                .onNodeWithText(context.getString(R.string.fragment_create_conference_add_participant))
                .performTextInput("bob")

            composeTestRule
                .onNodeWithText(context.getString(R.string.fragment_messenger_message))
                .performTextInput("hello")

            composeTestRule.onNodeWithContentDescription(
                context.getString(R.string.action_create_chat),
            ).performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                runCatching { verify { api.messenger.createConference("hello", "bob") } }.isSuccess
            }
        }
    }
}
```

- [ ] **Step 3: Adapt to the real screen and run**

The interaction code above is a best-effort sketch against affordances that must be confirmed in Step 1 — the participant-add flow, the submit control and its matcher (text vs content description) are all likely to differ. **Adapt it to what the screen actually exposes, and report what you changed.** The acceptance criterion is that the endpoint is provably invoked; the exact gestures are yours to get right.

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.chat.prv.create.CreateConferenceScreenTest`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

If driving the UI to a submit turns out to need more interaction than a smoke test warrants, **stop and report** rather than expanding scope. The spec already names this screen as a candidate to move to Group 4; that outcome is an acceptable answer.

- [ ] **Step 4: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/chat/prv/create/CreateConferenceScreenTest.kt
git commit -m "test: add CreateConferenceScreen instrumented endpoint test"
```

---

### Task 9: Full-suite verification

**Goal:** Confirm the whole instrumented suite is green with Group 2 added, and record the wall-clock cost.

**Files:**
- None (verification only)

**Acceptance Criteria:**
- [ ] `./gradlew connectedDebugAndroidTest` passes with **46 tests** (30 existing + 16 new), 0 failed
- [ ] The six Group 1 files and the POC still pass after the Task 1 fixture migration
- [ ] The four TV tests still pass
- [ ] Wall-clock is recorded and compared against the 32s Group 1 baseline

**Verify:** `./gradlew connectedDebugAndroidTest` → `BUILD SUCCESSFUL`, 46 tests, 0 failed

**Steps:**

- [ ] **Step 1: Run the full suite with timing**

```bash
start=$(date +%s)
./gradlew connectedDebugAndroidTest 2>&1 | tail -20
echo "WALL_CLOCK_SECONDS=$(( $(date +%s) - start ))"
```

Expected: `BUILD SUCCESSFUL`, `Starting 46 tests`, 0 failed.

Note `/usr/bin/time` is **not installed** on this machine — use the shell arithmetic above.

- [ ] **Step 2: Report**

Report the test count, the wall-clock, and the delta against Group 1's 32s baseline. Group 1 measured ~0.86s/test, so ~47s is expected. A materially larger number is worth flagging — the tab tests each launch an Activity and click, so they may cost more than the Group 1 average.

---

## Notes and Risks

**`switchToTab` is unproven until Task 4.** It is written against the label-collision analysis but has no test of its own. If Task 4's switched tabs time out, suspect the helper before the endpoint stubs — verify with `onAllNodesWithText(label).fetchSemanticsNodes().size` to see how many nodes actually match.

**Task 8 is the weakest task in the plan and is scoped to fail gracefully.** Its interaction code is a sketch; the screen may not be drivable to a submit within smoke scope. Reporting that outcome and moving the screen to Group 4 is an acceptable result, explicitly sanctioned by the spec.

**The `api.ucp.*` own-profile branch is a recorded gap**, not an oversight — see the spec's "Known gaps". Same for `MediaInfoViewModel`'s age-restriction path and all BBCode body content.

**detekt is red on master** — 4 pre-existing violations at `src/main/kotlin/me/proxer/app/util/ErrorUtils.kt:235`, unrelated to this work. It will block any CI gate that runs detekt. Out of scope here.
