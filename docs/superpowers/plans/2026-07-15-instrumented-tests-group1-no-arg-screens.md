# Instrumented UI Tests — Group 1 (No-Arg Screens) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add instrumented UI smoke tests for the six Group 1 screens that launch with no intent arguments, reusing the landed harness with zero new harness capability.

**Architecture:** Each test extends `InstrumentedTestBase`, stubs its ViewModel's endpoint on the shared relaxed `ProxerApi` Koin mock, launches the real Activity via `ActivityScenario`, and asserts rendered text with `createEmptyComposeRule()` + `waitUntil`. Four of the six screens are `MainActivity` drawer sections launched through the public `MainActivity.getSectionIntent(Context, DrawerItem)` factory; two are standalone Activities. No production code changes.

**Tech Stack:** Kotlin, Compose UI test (`androidx.compose.ui.test.junit4.v2`), MockK (`mockk-android` 1.14.11), Koin 4.2.1, RxJava 2, JUnit4 + `AndroidJUnit4`, OkHttp 5.3.2, jsoup.

**Spec:** `docs/superpowers/specs/2026-07-15-instrumented-test-rollout-design.md`

---

## Shared Context — read before Task 1

**Depth rule (from the spec).** Every screen gets a **smoke** test: launches, DI resolves, content renders from a stubbed API. Deep tests only where wiring itself can break. The JVM suite (`src/test/`) already covers every one of these ViewModels' success/error/retry/pagination logic — **do not re-assert it**. The POC (`NotificationScreenTest`) already covers error, retry, pagination and the login gate, so no Group 1 task repeats those.

**Harness facts that constrain every task:**

- `TestApplication` calls `startKoin` **once per instrumentation process** with `listOf(viewModelModule, fakeAppModule())`. The real `viewModelModule` is used, so ViewModels are constructed for real against relaxed mocks. Mock instances are **shared across all test classes**.
- **Always extend `InstrumentedTestBase`** — its `@After resetFakeAppModuleMocks()` clears stubs on every `fakeAppModule` mock. It exposes `api`, `storageHelper`, `preferenceHelper`, `validators`. Anything else (`OkHttpClient`, `TagDao`) must be obtained with `by safeInject()`.
- **Always call `stubLoggedIn(storageHelper, preferenceHelper)` in `@Before`**, even for screens with `isLoginRequired = false`. Two independent reasons: `BaseViewModel.init` subscribes to `storageHelper.isLoggedInObservable` and `preferenceHelper.isAgeRestrictedMediaAllowedObservable` unconditionally, and `BaseActivity.onCreate` needs `preferenceHelper.themeObservable`. Leaving `themeObservable` unstubbed crashes every `BaseActivity` screen on launch (documented at `LoginFixtures.kt:14-18`).
- `src/test/kotlin/me/proxer/app/base/ProxerEndpointTestUtils.kt` (`stubPagingSuccess` etc.) is **JVM-only and NOT on the androidTest classpath**. Hand-roll the `ProxerCall` mocks following `NotificationScreenTest.kt:61-77`. Likewise `koin-test` (`inject()`, `KoinTestRule`) and `RxTrampolineRule` are JVM-only.
- **Non-null `Endpoint<T>` → stub `call.safeExecute()`. Nullable `Endpoint<T?>` → stub `call.execute()`.** Every endpoint in Group 1 is the non-null path except `SetSettingsEndpoint` (Task 6, not exercised).
- Relaxed mocks do **not** return `this` from builder methods — every builder in a chain (`page`, `limit`, `markAsRead`, …) must be explicitly stubbed to return the endpoint, or the chain yields a foreign mock.
- **All UI strings are German.** Resolve via `context.getString(R.string.…)`, never hardcode.

**Launching `MainActivity` — do NOT use `ActivityScenario.launch(MainActivity::class.java)`.** That overload builds its intent with `Intent.makeMainActivity`, i.e. `action = ACTION_MAIN`. Combined with the relaxed `PreferenceHelper` (`launches` returns `0`), `MainActivity.kt:55-62` takes the introduction branch and **`setContent` is never called**:

```kotlin
val shouldIntroduce = savedInstanceState == null &&
    preferenceHelper.launches <= 0 &&
    intent.action == Intent.ACTION_MAIN
if (shouldIntroduce) { … return }   // setContent NEVER runs
```

`MainActivity.getSectionIntent` sets action `"me.proxer.app.intent.action.NEWS"`, so `shouldIntroduce` is false. Always launch drawer sections via `ActivityScenario.launch<MainActivity>(MainActivity.getSectionIntent(context, DrawerItem.X))`.

**Timeouts.** Use `15_000` for `MainActivity`-hosted screens (cold launch + drawer + `InAppUpdateFlow` make these slower than the POC's 2-item case) and `5_000` for the two standalone Activities. The POC observed up to ~8s for a 30-item cold launch (`NotificationScreenTest.kt:113-116`).

**Keep fixture item names short and unique** (e.g. `"Subject n0"`). Several screens apply `maxLines` + `TextOverflow.Ellipsis`, which breaks `onNodeWithText` on long strings.

---

### Task 1: NewsScreen smoke test

**Goal:** Prove the `MainActivity.getSectionIntent` drawer-launch pattern works end-to-end and that `NewsScreen` renders an article from a stubbed endpoint.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/news/NewsScreenTest.kt`

**Acceptance Criteria:**
- [ ] `MainActivity` launched with `DrawerItem.NEWS` renders `NewsScreen`, not the introduction flow
- [ ] A stubbed `NewsEndpoint` returning one `NewsArticle` results in that article's `subject` being displayed
- [ ] Test extends `InstrumentedTestBase` and calls `stubLoggedIn` in `@Before`
- [ ] No assertion duplicates `NewsViewModelTest` (no error/pagination cases)

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.news.NewsScreenTest` → `BUILD SUCCESSFUL`, 1 test passed

**Steps:**

- [ ] **Step 1: Write the test**

Create `src/androidTest/kotlin/me/proxer/app/news/NewsScreenTest.kt`:

```kotlin
package me.proxer.app.news

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
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.util.wrapper.DrawerItem
import me.proxer.library.ProxerCall
import me.proxer.library.api.notifications.NewsEndpoint
import me.proxer.library.entity.notifications.NewsArticle
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class NewsScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun article(id: String) = NewsArticle(
        id,
        Date(),
        "Description $id",
        "image.png",
        "Subject $id",
        10,
        "thread-$id",
        "author-$id",
        "Author $id",
        0,
        "cat-1",
        "Category",
    )

    private fun mockNewsEndpoint(): NewsEndpoint {
        val endpoint = mockk<NewsEndpoint>(relaxed = true)

        every { api.notifications.news() } returns endpoint
        every { endpoint.markAsRead(any()) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint

        return endpoint
    }

    private fun mockCall(value: List<NewsArticle>): ProxerCall<List<NewsArticle>> {
        val call = mockk<ProxerCall<List<NewsArticle>>>(relaxed = true)

        every { call.clone() } returns call
        every { call.safeExecute() } returns value

        return call
    }

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
    }

    @Test
    fun success_renders_article_subject() {
        val endpoint = mockNewsEndpoint()
        every { endpoint.build() } returns mockCall(listOf(article("n0")))

        val intent = MainActivity.getSectionIntent(context, DrawerItem.NEWS)

        ActivityScenario.launch<MainActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 15_000) {
                composeTestRule.onAllNodesWithText("Subject n0").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Subject n0").assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Run the test — expect it to pass**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.news.NewsScreenTest`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

This is a characterisation test of working code, so it should pass on the first run. If it fails, that is a real finding — do not weaken the assertion to make it green. Two likely causes, both worth reporting rather than papering over:
- Introduction flow rendered instead of `NewsScreen` → the `getSectionIntent` action is not suppressing `shouldIntroduce`.
- Timeout with no matching node → `NewsScreen`'s `LaunchedEffect(Unit) { viewModel.load() }` (`NewsScreen.kt:68`) is not firing, or the endpoint chain has an unstubbed builder returning a foreign mock.

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/news/NewsScreenTest.kt
git commit -m "test: add NewsScreen instrumented smoke test"
```

---

### Task 2: BookmarkScreen smoke test

**Goal:** Prove `BookmarkScreen` renders a bookmark from a stubbed endpoint via the drawer-launch pattern.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/bookmark/BookmarkScreenTest.kt`

**Acceptance Criteria:**
- [ ] `MainActivity` launched with `DrawerItem.BOOKMARKS` renders `BookmarkScreen`
- [ ] A stubbed `BookmarksEndpoint` returning one `Bookmark` results in that bookmark's `name` being displayed
- [ ] Test extends `InstrumentedTestBase` and calls `stubLoggedIn` in `@Before`
- [ ] No swipe gesture is performed (items are wrapped in `SwipeToDismissBox`; a stray swipe triggers deletion)

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.bookmark.BookmarkScreenTest` → `BUILD SUCCESSFUL`, 1 test passed

**Steps:**

- [ ] **Step 1: Write the test**

Create `src/androidTest/kotlin/me/proxer/app/bookmark/BookmarkScreenTest.kt`:

```kotlin
package me.proxer.app.bookmark

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
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.util.wrapper.DrawerItem
import me.proxer.library.ProxerCall
import me.proxer.library.api.ucp.BookmarksEndpoint
import me.proxer.library.entity.ucp.Bookmark
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaLanguage
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun bookmark(id: String) = Bookmark(
        id,
        "entry-$id",
        Category.ANIME,
        "Bookmark $id",
        3,
        MediaLanguage.GERMAN_SUB,
        Medium.ANIMESERIES,
        MediaState.FINISHED,
        "Chapter $id",
        true,
    )

    private fun mockBookmarksEndpoint(): BookmarksEndpoint {
        val endpoint = mockk<BookmarksEndpoint>(relaxed = true)

        every { api.ucp.bookmarks() } returns endpoint
        every { endpoint.name(any()) } returns endpoint
        every { endpoint.category(any()) } returns endpoint
        every { endpoint.filterAvailable(any()) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint

        return endpoint
    }

    private fun mockCall(value: List<Bookmark>): ProxerCall<List<Bookmark>> {
        val call = mockk<ProxerCall<List<Bookmark>>>(relaxed = true)

        every { call.clone() } returns call
        every { call.safeExecute() } returns value

        return call
    }

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
    }

    @Test
    fun success_renders_bookmark_name() {
        val endpoint = mockBookmarksEndpoint()
        every { endpoint.build() } returns mockCall(listOf(bookmark("b0")))

        val intent = MainActivity.getSectionIntent(context, DrawerItem.BOOKMARKS)

        ActivityScenario.launch<MainActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 15_000) {
                composeTestRule.onAllNodesWithText("Bookmark b0").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Bookmark b0").assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.bookmark.BookmarkScreenTest`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/bookmark/BookmarkScreenTest.kt
git commit -m "test: add BookmarkScreen instrumented smoke test"
```

---

### Task 3: ScheduleScreen smoke test and logged-out wiring test

**Goal:** Prove `ScheduleScreen` renders a calendar entry, and that its `isLoginRequired = false` path genuinely serves content to a logged-out user.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/anime/schedule/ScheduleScreenTest.kt`

**Acceptance Criteria:**
- [ ] A stubbed `CalendarEndpoint` returning one `CalendarEntry` results in that entry's `name` being displayed
- [ ] A second test using `stubLoggedOut` still renders content — **not** `R.string.error_login_required`
- [ ] `CalendarEndpoint` is stubbed with **no** `page`/`limit` builders (it is a plain `Endpoint`, not a `PagingLimitEndpoint`)
- [ ] No assertion on `airingInfoText` / `statusText` / rating (time-dependent, recomputed every second)

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.anime.schedule.ScheduleScreenTest` → `BUILD SUCCESSFUL`, 2 tests passed

**Steps:**

- [ ] **Step 1: Write the tests**

The logged-out case is the one deep test in Group 1 that is *not* redundant with the POC: `NotificationScreenTest` proves a logged-out user is **blocked**; this proves a logged-out user is **served**. `ScheduleViewModel.kt:13` (`override val isLoginRequired = false`) and `ServerStatusViewModel.kt:25` are the only two such overrides in the app.

Note two hazards encoded below. `ScheduleScreen.kt:219-249` runs a `while (true) { … delay(1_000) }` loop that keeps Compose perpetually busy — `waitForIdle()` would hang, which is why `createEmptyComposeRule()` (auto-sync disabled) plus explicit `waitUntil` polling is mandatory here. And `ScheduleContent` bails out entirely if `context !is Activity` (`ScheduleScreen.kt:116`), which the real-Activity launch satisfies.

Create `src/androidTest/kotlin/me/proxer/app/anime/schedule/ScheduleScreenTest.kt`:

```kotlin
package me.proxer.app.anime.schedule

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
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.base.stubLoggedOut
import me.proxer.app.util.wrapper.DrawerItem
import me.proxer.library.ProxerCall
import me.proxer.library.api.media.CalendarEndpoint
import me.proxer.library.entity.media.CalendarEntry
import me.proxer.library.enums.CalendarDay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ScheduleScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun entry(id: String, name: String) = CalendarEntry(
        id = id,
        entryId = "entry-$id",
        name = name,
        episode = 1,
        episodeTitle = "Ep 1",
        date = Date(),
        timezone = "Europe/Berlin",
        industryId = "10",
        industryName = "Studio A",
        weekDay = CalendarDay.MONDAY,
        uploadDate = Date(),
        genres = emptySet(),
        ratingSum = 0,
        ratingAmount = 0,
    )

    // CalendarEndpoint is a plain Endpoint<List<CalendarEntry>>, NOT a PagingLimitEndpoint --
    // ScheduleViewModel calls api.media.calendar().buildSingle() with no page/limit builders.
    private fun mockCalendarEndpoint(): CalendarEndpoint {
        val endpoint = mockk<CalendarEndpoint>(relaxed = true)

        every { api.media.calendar() } returns endpoint

        return endpoint
    }

    private fun mockCall(value: List<CalendarEntry>): ProxerCall<List<CalendarEntry>> {
        val call = mockk<ProxerCall<List<CalendarEntry>>>(relaxed = true)

        every { call.clone() } returns call
        every { call.safeExecute() } returns value

        return call
    }

    private fun launchSchedule() = ActivityScenario.launch<MainActivity>(
        MainActivity.getSectionIntent(context, DrawerItem.SCHEDULE),
    )

    @Test
    fun success_renders_calendar_entry_name() {
        stubLoggedIn(storageHelper, preferenceHelper)

        val endpoint = mockCalendarEndpoint()
        every { endpoint.build() } returns mockCall(listOf(entry("c0", "Show A")))

        launchSchedule().use {
            composeTestRule.waitUntil(timeoutMillis = 15_000) {
                composeTestRule.onAllNodesWithText("Show A").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Show A").assertIsDisplayed()
        }
    }

    @Test
    fun logged_out_user_still_sees_content_since_login_is_not_required() {
        stubLoggedOut(storageHelper, preferenceHelper, validators)

        val endpoint = mockCalendarEndpoint()
        every { endpoint.build() } returns mockCall(listOf(entry("c0", "Show A")))

        launchSchedule().use {
            composeTestRule.waitUntil(timeoutMillis = 15_000) {
                composeTestRule.onAllNodesWithText("Show A").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Show A").assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.anime.schedule.ScheduleScreenTest`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

If `logged_out_user_still_sees_content_since_login_is_not_required` fails with `error_login_required` visible, that is a genuine regression in the `isLoginRequired = false` path — report it, do not adjust the test.

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/anime/schedule/ScheduleScreenTest.kt
git commit -m "test: add ScheduleScreen instrumented smoke and logged-out tests"
```

---

### Task 4: MediaListScreen smoke test

**Goal:** Prove `MediaListScreen` renders a media entry, including the `loadTags()` wiring its `LaunchedEffect` performs alongside `load()`.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/media/list/MediaListScreenTest.kt`

**Acceptance Criteria:**
- [ ] A stubbed `MediaSearchEndpoint` returning one `MediaListEntry` results in that entry's `name` being displayed
- [ ] All 12 `MediaSearchEndpoint` builders are stubbed to return the endpoint, plus `page`/`limit`
- [ ] `api.list` is pinned to a single `ListApi` mock instance
- [ ] `preferenceHelper.lastTagUpdateDate` and `tagDao.getTags()` are stubbed so `loadTags()` resolves from cache and issues no remote tag fetch

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.media.list.MediaListScreenTest` → `BUILD SUCCESSFUL`, 1 test passed

**Steps:**

- [ ] **Step 1: Write the test**

Three details make this the fiddliest Group 1 task, all confirmed in source:

`MediaListScreen.kt:150-153` calls **`viewModel.loadTags()` as well as `viewModel.load()`**. `loadTags()` (`MediaListViewModel.kt:106-144`) evaluates `shouldUpdateTags() || cachedTags.isEmpty()` — and `shouldUpdateTags()` (`:155-157`) reads `preferenceHelper.lastTagUpdateDate.toLocalDate()`. `lastTagUpdateDate` returns `org.threeten.bp.Instant`, a **final class** that mockk-android cannot fabricate a child mock for. Because `||` evaluates left-to-right, this is hit on every launch and must be stubbed. Stubbing it to `Instant.now()` makes `shouldUpdateTags()` false; returning a non-empty `tagDao.getTags()` makes `cachedTags.isEmpty()` false; together they take the `Single.just(cachedTags)` branch and skip the remote fetch entirely.

`api.list` is pinned to one `ListApi` mock (the precedent and rationale are at `MediaListViewModelTest.kt:67-71`) because both `mediaSearch()` and `tagList()` resolve through it.

`MediaListViewModel.kt:52-56` makes `isLoginRequired` **unconditionally true** (`super.isLoginRequired` is `true`), and `isAgeConfirmationRequired` is defined as `isLoginRequired`, so `validate()` calls both `validators.validateLogin()` and `validators.validateAgeConfirmation()`. `stubLoggedIn` covers the former; the latter stays unstubbed on the relaxed `Validators` mock and is a no-op.

Create `src/androidTest/kotlin/me/proxer/app/media/list/MediaListScreenTest.kt`:

```kotlin
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
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.media.LocalTag
import me.proxer.app.media.TagDao
import me.proxer.app.util.extension.safeInject
import me.proxer.app.util.wrapper.DrawerItem
import me.proxer.library.ProxerCall
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

    // Both mediaSearch() and tagList() resolve through api.list. Pinning it to one fixed mock instance
    // keeps the two chains from resolving against different dynamically-created child mocks.
    private val listApi = mockk<ListApi>(relaxed = true)

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

    private fun mockCall(value: List<MediaListEntry>): ProxerCall<List<MediaListEntry>> {
        val call = mockk<ProxerCall<List<MediaListEntry>>>(relaxed = true)

        every { call.clone() } returns call
        every { call.safeExecute() } returns value

        return call
    }

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        every { api.list } returns listApi

        // loadTags() evaluates `shouldUpdateTags() || cachedTags.isEmpty()`. shouldUpdateTags() reads
        // preferenceHelper.lastTagUpdateDate, whose org.threeten.bp.Instant return type is final and cannot
        // be auto-mocked by mockk-android. A fresh Instant plus a non-empty cache takes the cached branch,
        // so no remote tag fetch is issued.
        every { preferenceHelper.lastTagUpdateDate } returns Instant.now()
        every { tagDao.getTags() } returns listOf(localTag("t0"))
    }

    @Test
    fun success_renders_media_entry_name() {
        val endpoint = mockMediaSearchEndpoint()
        every { endpoint.build() } returns mockCall(listOf(entry("m0", "Entry A")))

        val intent = MainActivity.getSectionIntent(context, DrawerItem.ANIME)

        ActivityScenario.launch<MainActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 15_000) {
                composeTestRule.onAllNodesWithText("Entry A").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Entry A").assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.media.list.MediaListScreenTest`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

If it fails inside `loadTags()` with a MockK "final class" or child-mock error on `Instant`, the `lastTagUpdateDate` stub is not taking effect — check that `@Before` ordering puts it before `ActivityScenario.launch`.

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/media/list/MediaListScreenTest.kt
git commit -m "test: add MediaListScreen instrumented smoke test"
```

---

### Task 5: ServerStatusScreen smoke test

**Goal:** Prove the harness covers the raw-`OkHttpClient` scraping path — the only Group 1 screen that does not go through `ProxerApi`, and one of only two that work without a login fixture.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/settings/status/ServerStatusScreenTest.kt`

**Acceptance Criteria:**
- [ ] A stubbed `OkHttpClient` returning canned HTML results in the scraped server name being displayed
- [ ] `call.clone()` is stubbed (`CallStringBodySingle` clones before executing) and `call.execute()` returns the canned `Response`
- [ ] The `OkHttpClient` is obtained via `safeInject`, not `koin-test`'s `inject()`
- [ ] Test still calls `stubLoggedIn` despite `isLoginRequired = false`

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.settings.status.ServerStatusScreenTest` → `BUILD SUCCESSFUL`, 1 test passed

**Steps:**

- [ ] **Step 1: Write the test**

`ServerStatusViewModel` fetches via `client.newCall(constructRequest()).toBodySingle()` → `CallStringBodySingle` (`src/main/kotlin/me/proxer/app/util/rx/CallStringBodySingle.kt:14-28`), which calls `originalCall.clone()` and then the **synchronous** `execute()`. Stubbing `execute()` without `clone()` is inert, because an unstubbed relaxed `clone()` returns a different mock.

The canned HTML must satisfy the scraper (`ServerStatusViewModel.kt:44-81`): `<td>` elements, no `<img>` children, a name `TextNode` containing `"server"` (case-insensitive), and an adjacent status `Element` whose text is exactly `Online`/`Offline`. `Call` is an OkHttp **interface** and `OkHttpClient.newCall` is open, so both mock cleanly. The `Response.Builder` recipe below is lifted from the passing JVM test (`ServerStatusViewModelTest.kt:66-90`).

Create `src/androidTest/kotlin/me/proxer/app/settings/status/ServerStatusScreenTest.kt`:

```kotlin
package me.proxer.app.settings.status

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
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.util.extension.safeInject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerStatusScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    // InstrumentedTestBase does not expose OkHttpClient, and koin-test's inject() is JVM-only.
    private val client: OkHttpClient by safeInject()

    // The scraper keeps <td> nodes without <img> children, flattens their child nodes, zips them pairwise,
    // and keeps pairs of (TextNode containing "server", Element whose text is exactly Online/Offline).
    private val onlineHtml = """
        <html><body><table>
        <tr><td>Server 1:</td><td><b>Online</b></td></tr>
        <tr><td>Server 2:</td><td><b>Offline</b></td></tr>
        </table></body></html>
    """.trimIndent()

    private fun buildResponse(html: String) = Response.Builder()
        .request(Request.Builder().url("https://proxer.de").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(html.toResponseBody("text/html".toMediaType()))
        .build()

    @Before
    fun setup() {
        // Required even though ServerStatusViewModel has isLoginRequired = false: BaseViewModel.init
        // subscribes to the login/age observables unconditionally, and BaseActivity.onCreate needs
        // preferenceHelper.themeObservable or it crashes on launch.
        stubLoggedIn(storageHelper, preferenceHelper)
    }

    @Test
    fun success_renders_scraped_server_name() {
        val call = mockk<Call>(relaxed = true)

        // CallStringBodySingle clones the call before executing it, so an unstubbed clone() would
        // hand back a different mock whose execute() is not stubbed.
        every { call.clone() } returns call
        every { call.execute() } returns buildResponse(onlineHtml)
        every { client.newCall(any()) } returns call

        ActivityScenario.launch(ServerStatusActivity::class.java).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("Server 1").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Server 1").assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.settings.status.ServerStatusScreenTest`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/settings/status/ServerStatusScreenTest.kt
git commit -m "test: add ServerStatusScreen instrumented smoke test"
```

---

### Task 6: ProfileSettingsScreen smoke test

**Goal:** Prove `ProfileSettingsScreen` renders, covering the app's only plain-`ViewModel` load path — one that fetches from the VM `init` block rather than a `LaunchedEffect`.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/profile/settings/ProfileSettingsScreenTest.kt`

**Acceptance Criteria:**
- [ ] A stubbed `SettingsEndpoint` plus a stubbed `storageHelper.profileSettings` results in the "Werbung" section header being displayed
- [ ] `api.ucp.settings()` and `storageHelper.profileSettings` are stubbed **before** `ActivityScenario.launch` (the VM fetches in `init`, at construction time)
- [ ] Assertions use `context.getString(...)`, not hardcoded German
- [ ] No assertion on `profile_preference_banner_ads_title` (that row is deliberately not rendered)

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.profile.settings.ProfileSettingsScreenTest` → `BUILD SUCCESSFUL`, 1 test passed

**Steps:**

- [ ] **Step 1: Write the test**

This screen is structurally unlike every other Group 1 screen, and the differences are the point of covering it. `ProfileSettingsViewModel.kt:20` extends `androidx.lifecycle.ViewModel`, **not** `BaseViewModel` — there is no `isLoading`, no `isLoginRequired`, no `validate()`, and **no `load()` method**. `ProfileSettingsScreen.kt:50-63` has **no `LaunchedEffect`**; the fetch fires from the VM's `init` block (`:30-34`), which reads `storageHelper.profileSettings` and then calls `refresh()`. Both stubs must therefore be in place before the Activity launches.

`ProfileSettingsActivity` self-`finish()`es on any `false` from `isLoggedInObservable`, but does **not** gate on login. Under `stubLoggedIn` that observable is `Observable.never()`, so `finish()` never fires.

Rather than hand-building the 17-parameter `UcpSettings`, reuse the production round-trip the JVM test uses (`ProfileSettingsViewModelTest.kt:42-43`): `LocalProfileSettings.default().toNonLocalSettings()`. The test lives in the same package, so neither type needs an import.

Create `src/androidTest/kotlin/me/proxer/app/profile/settings/ProfileSettingsScreenTest.kt`:

```kotlin
package me.proxer.app.profile.settings

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
import me.proxer.app.base.stubLoggedIn
import me.proxer.library.ProxerCall
import me.proxer.library.api.ucp.SettingsEndpoint
import me.proxer.library.entity.ucp.UcpSettings
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileSettingsScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    // Round-tripping the production default avoids hand-building UcpSettings' 17 parameters.
    private val fixtureSettings = LocalProfileSettings.default().toNonLocalSettings()

    private fun mockCall(value: UcpSettings): ProxerCall<UcpSettings> {
        val call = mockk<ProxerCall<UcpSettings>>(relaxed = true)

        every { call.clone() } returns call
        every { call.safeExecute() } returns value

        return call
    }

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        // ProfileSettingsViewModel is a plain ViewModel that loads from its init block, not from a
        // LaunchedEffect -- both of these must be stubbed before the Activity constructs the VM.
        every { storageHelper.profileSettings } returns LocalProfileSettings.default()

        val endpoint = mockk<SettingsEndpoint>(relaxed = true)

        every { api.ucp.settings() } returns endpoint
        every { endpoint.build() } returns mockCall(fixtureSettings)
    }

    @Test
    fun success_renders_ads_category_header() {
        ActivityScenario.launch(ProfileSettingsActivity::class.java).use {
            val adsHeader = context.getString(R.string.profile_preference_ads_category_title)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(adsHeader).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(adsHeader).assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.profile.settings.ProfileSettingsScreenTest`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

If only the TopAppBar renders and the header never appears, `settings` is null — `ProfileSettingsScreen.kt:198` early-returns (`val currentSettings = settings ?: return@Scaffold`), which means the `storageHelper.profileSettings` stub is not landing before VM construction.

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/profile/settings/ProfileSettingsScreenTest.kt
git commit -m "test: add ProfileSettingsScreen instrumented smoke test"
```

---

### Task 7: Full-suite run and CI wall-clock checkpoint

**Goal:** Confirm the whole instrumented suite is green with Group 1 added, and measure the `connectedDebugAndroidTest` wall-clock delta that the spec makes a gate on Group 2.

**USER-ORDERED GATE — NON-SKIPPABLE.** This task was requested by the user in the current conversation. It MUST NOT be closed by walking around it, by declaring it "verified inline", or by substituting a cheaper check. Close only after every item in `acceptanceCriteria` has been re-validated independently, with output captured.

**Files:**
- None (measurement and reporting only)

**Acceptance Criteria:**
- [ ] A **baseline** wall-clock measurement of `connectedDebugAndroidTest` is captured at the Group 1 starting commit (`6b4a26b2`, the merge of PR #86 — POC + TV tests only)
- [ ] An **after** wall-clock measurement of `connectedDebugAndroidTest` is captured with all Group 1 tests present
- [ ] The full suite passes — including the pre-existing TV tests (`TvSearchScreenTest`, `TvEpisodeScreenTest`, `TvErrorViewTest`, `TvMediaDetailScreenTest`), which share the `androidTest` variant's `TestApplication`
- [ ] Both numbers and the delta are reported to the user, with a recommendation on whether Group 2 proceeds as scoped or gets re-scoped/sharded

**Verify:** `./gradlew connectedDebugAndroidTest` → `BUILD SUCCESSFUL`; both timings captured and the delta reported

**Steps:**

- [ ] **Step 1: Capture the baseline**

The baseline must come from a tree without the Group 1 tests. Use a worktree at the pre-Group-1 commit so the working branch is untouched:

```bash
git worktree add /tmp/g1-baseline 6b4a26b2
cd /tmp/g1-baseline
cp /home/asteria/Documents/projects/ProxerAndroid/secrets.properties .
/usr/bin/time -f "BASELINE_WALL_CLOCK=%E" ./gradlew connectedDebugAndroidTest 2>&1 | tail -20
```

Record the `BASELINE_WALL_CLOCK` value. Note the emulator must be running and idle for both runs, and no other `./gradlew test*` task may run concurrently on the same checkout.

- [ ] **Step 2: Capture the after-measurement**

```bash
cd /home/asteria/Documents/projects/ProxerAndroid
/usr/bin/time -f "AFTER_WALL_CLOCK=%E" ./gradlew connectedDebugAndroidTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. Confirm the run includes the Group 1 classes plus the four TV classes and `NotificationScreenTest`.

- [ ] **Step 3: Clean up the baseline worktree**

```bash
git worktree remove /tmp/g1-baseline
```

- [ ] **Step 4: Report and recommend**

Report to the user: baseline, after, absolute delta, per-test average for the 7 new tests, and a recommendation on Group 2. Group 2 is ~19 screens — roughly 3x Group 1 — so extrapolate the delta and say plainly whether that is acceptable or whether Group 2 needs sharding.

**Do not close this task on a green build alone.** The gate is the measurement and the Group 2 recommendation, not the passing suite.

```json:metadata
{"files": [], "verifyCommand": "./gradlew connectedDebugAndroidTest", "acceptanceCriteria": ["Baseline wall-clock captured at commit 6b4a26b2 without Group 1 tests", "After wall-clock captured with all Group 1 tests present", "Full suite passes including the four pre-existing TV test classes", "Delta reported to user with a proceed/re-scope recommendation for Group 2"], "userGate": true, "tags": ["user-gate"], "requiresUserSpecification": true, "requireEvidenceTokens": [["baseline", "before-g1", "6b4a26b2"], ["after-g1", "with-g1"]], "gateScope": "Group 1 completion gates Group 2 scoping", "failurePolicy": "If the delta is unacceptable, stop and re-scope Group 2 with the user rather than proceeding"}
```

---

## Notes and Risks

**`requiresUserSpecification` on Task 7.** The spec mandates the measurement but sets no pass/fail threshold — "acceptable delta" is a user judgment about their CI budget, not something derivable from the code. `/specify-gate` should collect the threshold at execute time.

**`InAppUpdateFlow` snackbar.** `MainScreen.kt:241-267` starts an update check gated on Play Services availability. On an emulator image **with** Play Services it can post an indefinite snackbar ("Update verfügbar"). It does not interfere with the item-text assertions in Tasks 1–4, but prefer a Play-Services-free emulator image. If a snackbar ever occludes an assertion, that is the cause.

**Shared-mock bleed.** Koin starts once per process and all test classes share the `fakeAppModule` mocks. Every task extends `InstrumentedTestBase` so `resetFakeAppModuleMocks` clears stubs between tests. Do not add `clearAllMocks()` — it would reset mocks belonging to other test classes.

**CLAUDE.md is stale on two points** discovered while writing this plan. Worth fixing separately, out of scope here:
- It claims ProxerLibJava source is checked out at `../ProxerLibJava`. It is not present. Entity constructors in this plan were taken from the existing JVM tests instead.
- It references `BaseContentFragment.onViewCreated` for the no-auto-load behaviour; Fragments are gone since the Compose migration. The infra design spec already flagged this.
