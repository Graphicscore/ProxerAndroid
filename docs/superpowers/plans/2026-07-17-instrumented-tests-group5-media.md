# Group 5 (Media-heavy Screens) Instrumented Tests — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add instrumented Compose UI smoke coverage for the three media-heavy screens (Anime, Manga, ImageDetail) — 4 tests across 3 test files — plus the 3 production seams they require, closing the final rollout group.

**Architecture:** Reuse the Groups 1–4 harness unchanged (`InstrumentedTestBase`, `LoginFixtures.stubLoggedIn`, `ProxerCallFixtures.mockProxerCall`, `TestApplication`). Anime/Manga launch via a bare `Intent` (all extras have fallbacks; `id` defaults to `"-1"`), so their endpoints are stubbed at the default argument values. Manga's happy path hides all chrome and renders through a semantics-less `AndroidView`, so it needs a `testTag` seam. ImageDetail requires a `getIntent` factory (its extra is `requireNotNull`) and a real `contentDescription` on its Refresh icon (currently `null`).

**Tech Stack:** Kotlin, Jetpack Compose, Espresso/`createEmptyComposeRule` (androidx.compose.ui.test.junit4.v2), MockK relaxed mocks via Koin `fakeAppModule()`, RxJava 2, ProxerLibJava concrete endpoints.

**Branch:** `instrumented-tests-group5-media` (spec committed `23a7cb92`).

---

## Ground truth (verified in source — do not re-derive)

- `buildSingle()` = `ProxerCallSingle(build())`; `buildPartialErrorSingle(input)` delegates to `buildSingle()` (`ProxerRxExtensions.kt:15,20`). **Both route through `endpoint.build()` → `ProxerCall.safeExecute()`.** So stubbing `endpoint.build()` to return `mockProxerCall(value)` drives all three endpoints — identical to the Group 3 Industry pattern (`IndustryScreenTest` stubs `infoEndpoint.build()`).
- Concrete endpoint types: `EntryCoreEndpoint` (`me.proxer.library.api.info`), `StreamsEndpoint` (`me.proxer.library.api.anime`), `ChapterEndpoint` (`me.proxer.library.api.manga`). Mock the concrete type.
- Bare-Intent defaults: Anime `id="-1"`, `episode=1`, `language=AnimeLanguage.ENGLISH_SUB`. Manga `id="-1"`, `episode=1`, `language=Language.ENGLISH`.
- `AnimeViewModel`/`MangaViewModel` extend `BaseViewModel` (login-gated, `isLoginRequired=true` inherited). `validators.validateLogin()` is a relaxed-mock no-op after `stubLoggedIn`, so login passes. `data` starts null; the screen calls `viewModel.load()` in `LaunchedEffect(Unit)`.
- `BaseActivity.onCreate` subscribes `preferenceHelper.themeObservable` (`BaseActivity.kt:65`); unstubbed it crashes the launch. `stubLoggedIn` stubs it. **Every test calls `stubLoggedIn`** — including ImageDetail (no ViewModel/login gate, but the BaseActivity themeObservable requirement stands). Relaxed `preferenceHelper.themeContainer.theme` resolves to a real `Theme` enum entry (enums return their first value under relaxed MockK), so `.noBackground`/`.main` need no stub.
- Entity constructors (copied from the passing JVM `AnimeViewModelTest`/`MangaViewModelTest`):
  - `EntryCore(id, name, genres, fskConstraints, description, medium, episodeAmount, state, ratingSum, ratingAmount, clicks, category, license, adaptionInfo)` — 14 named args. `fskConstraints=emptySet()` ⇒ not age-restricted ⇒ no age gate.
  - `AnimeStreamInfo(name, episodeAmount, streams)`.
  - `AnimeStream(id, hoster, hosterName, image, uploaderId, uploaderName, date: Instant, translatorGroupId?, translatorGroupName?, isOfficial, isPublic, isSupported, resolutionResult?)` — 13 fields. `resolutionResult=null` ⇒ renders `StreamItem` (not `MessageStreamItem`) whose collapsed header is `Text(hosterName)`.
  - `Chapter(id, entryId, title, uploaderId, uploaderName, date: java.util.Date, scanGroupId, scanGroupName, server, pages)` — 10 args. `pages=emptyList()` (non-null) ⇒ `isOfficial=false` ⇒ dodges both `MangaLinkException`/`MangaNotAvailableException` branches ⇒ valid success.
  - `MangaChapterInfo(chapter, name, episodeAmount)`.
- Assertable strings: `R.string.error_no_data_anime` = "Aktuell gibt es noch keine unterstützten Streams"; `R.string.error_action_retry` = "Erneut versuchen" (both exist in `strings.xml`).
- **Never tap Play** in the Anime deep test — `onPlay` → `viewModel.resolve` → fires a real Intent to `StreamActivity`.

---

## File Structure

**Production (seams — 3 changes, 2 files):**
- `src/main/kotlin/me/proxer/app/ui/ImageDetailActivity.kt` — add public `getIntent(Context, HttpUrl): Intent`; `navigateTo` delegates to it.
- `src/main/kotlin/me/proxer/app/ui/ImageDetailScreen.kt` — Refresh icon `contentDescription = null` → `stringResource(R.string.error_action_retry)`.
- `src/main/kotlin/me/proxer/app/manga/MangaScreen.kt` — add `internal const val MANGA_READER_TEST_TAG = "mangaReader"`; apply `.testTag(MANGA_READER_TEST_TAG)` to both the `LazyColumn` and `HorizontalPager` reader containers.

**Tests (3 files, 4 tests):**
- `src/androidTest/kotlin/me/proxer/app/ui/ImageDetailScreenTest.kt` — 1 smoke.
- `src/androidTest/kotlin/me/proxer/app/manga/MangaScreenTest.kt` — 1 smoke.
- `src/androidTest/kotlin/me/proxer/app/anime/AnimeScreenTest.kt` — 2 tests (empty-state smoke + populated deep).

---

### Task 1: ImageDetail seams + smoke test

**Goal:** Add `ImageDetailActivity.getIntent` and a real Refresh-icon `contentDescription`, then a smoke test asserting the error-state icon is displayed.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/ui/ImageDetailActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/ui/ImageDetailScreen.kt`
- Create: `src/androidTest/kotlin/me/proxer/app/ui/ImageDetailScreenTest.kt`

**Acceptance Criteria:**
- [ ] `ImageDetailActivity.getIntent(context, url)` exists and returns the intent with `URL_EXTRA`; `navigateTo` delegates to it.
- [ ] `ImageDetailScreen.kt` Refresh `Icon` has `contentDescription = stringResource(R.string.error_action_retry)` (no longer `null`).
- [ ] `ImageDetailScreenTest` launches via `getIntent`, waits for the image load to fail, and asserts the Refresh icon is displayed by its content description.
- [ ] Test passes on the API 31 AVD.

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.ui.ImageDetailScreenTest` → BUILD SUCCESSFUL, 1 test, 0 failures.

**Steps:**

- [ ] **Step 1: Add the `getIntent` factory to `ImageDetailActivity`.**

Edit the `companion object` in `src/main/kotlin/me/proxer/app/ui/ImageDetailActivity.kt`. Add the two imports (`android.content.Context`, `android.content.Intent`) at the top, then replace the companion body:

```kotlin
    companion object {
        private const val URL_EXTRA = "url"

        fun getIntent(context: Context, url: HttpUrl): Intent =
            context.intentFor<ImageDetailActivity>(URL_EXTRA to url.toString())

        fun navigateTo(context: Activity, url: HttpUrl, imageView: ImageView? = null) {
            ActivityUtils.navigateToWithImageTransition(getIntent(context, url), context, imageView)
        }
    }
```

Imports to add near the existing `import android.app.Activity`:

```kotlin
import android.content.Context
import android.content.Intent
```

- [ ] **Step 2: Give the Refresh icon a real `contentDescription`.**

In `src/main/kotlin/me/proxer/app/ui/ImageDetailScreen.kt`, add two imports:

```kotlin
import androidx.compose.ui.res.stringResource
import me.proxer.app.R
```

Then change the Refresh `Icon` (currently `contentDescription = null`):

```kotlin
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.error_action_retry),
                    tint = Color.White,
                    modifier = Modifier
                        .size(64.dp)
                        .clickable { hasError = false },
                )
```

- [ ] **Step 3: Compile the production change.**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Write the smoke test.**

Create `src/androidTest/kotlin/me/proxer/app/ui/ImageDetailScreenTest.kt`:

```kotlin
package me.proxer.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.proxer.app.R
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.stubLoggedIn
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ImageDetail is a pure display screen (no ViewModel). Its SubsamplingScaleImageView receives a raw http URI,
 * bypassing the mocked OkHttpClient, so the native decode fails and drives hasError = true -> the Refresh icon.
 * The error state is the only Compose-assertable state; it is also the natural terminal state under test.
 *
 * stubLoggedIn is called not for a login gate (there is none) but because BaseActivity.onCreate subscribes
 * preferenceHelper.themeObservable, which must be stubbed or the launch crashes.
 */
@RunWith(AndroidJUnit4::class)
class ImageDetailScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
    }

    @Test
    fun renders_retry_icon_when_image_fails_to_load() {
        val retry = context.getString(R.string.error_action_retry)

        ActivityScenario.launch<ImageDetailActivity>(
            ImageDetailActivity.getIntent(context, "https://proxer.me/nonexistent.jpg".toHttpUrl()),
        ).use {
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithContentDescription(retry).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithContentDescription(retry).assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 5: Run the test.**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.ui.ImageDetailScreenTest`
Expected: BUILD SUCCESSFUL, `Starting 1 tests`, 0 failed.

- [ ] **Step 6: Commit.**

```bash
git add src/main/kotlin/me/proxer/app/ui/ImageDetailActivity.kt \
        src/main/kotlin/me/proxer/app/ui/ImageDetailScreen.kt \
        src/androidTest/kotlin/me/proxer/app/ui/ImageDetailScreenTest.kt
git commit -m "test: ImageDetail smoke test; add getIntent factory + Refresh icon contentDescription"
```

---

### Task 2: Manga reader testTag seam + smoke test

**Goal:** Add a `testTag` to the Manga reader containers, then a smoke test that stubs a valid empty-pages chapter and asserts the reader container is displayed after the happy path hides all chrome.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/manga/MangaScreen.kt`
- Create: `src/androidTest/kotlin/me/proxer/app/manga/MangaScreenTest.kt`

**Acceptance Criteria:**
- [ ] `MangaScreen.kt` declares `internal const val MANGA_READER_TEST_TAG = "mangaReader"`.
- [ ] Both the `LazyColumn` (vertical) and `HorizontalPager` (horizontal) reader containers carry `.testTag(MANGA_READER_TEST_TAG)`.
- [ ] `MangaScreenTest` launches Manga via a bare Intent, stubs `entryCore("-1")` + `chapter("-1", 1, Language.ENGLISH)` with `pages=emptyList()`, and asserts the `mangaReader` node is displayed.
- [ ] Test passes on the API 31 AVD.

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.manga.MangaScreenTest` → BUILD SUCCESSFUL, 1 test, 0 failures.

**Steps:**

- [ ] **Step 1: Add the testTag import and constant to `MangaScreen.kt`.**

Add the import (with the other `androidx.compose.ui.*` imports):

```kotlin
import androidx.compose.ui.platform.testTag
```

Add a top-level constant (e.g. directly under the `package`/imports, above the first composable):

```kotlin
internal const val MANGA_READER_TEST_TAG = "mangaReader"
```

- [ ] **Step 2: Tag both reader containers.**

In `MangaContent`'s `ContentScreen { … }` lambda, change the two container modifiers. The `LazyColumn` (vertical branch):

```kotlin
                    LazyColumn(modifier = Modifier.fillMaxSize().testTag(MANGA_READER_TEST_TAG)) {
```

The `HorizontalPager` (left-to-right / right-to-left branch):

```kotlin
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().testTag(MANGA_READER_TEST_TAG),
                    ) { pageIndex ->
```

Tagging both makes the assertion robust to the default reader orientation. Both containers compose only inside the `ContentScreen` success lambda (after `data ?: return@ContentScreen` and `pages ?: return@ContentScreen`), so the tag is a true "reader rendered on successful load" signal. `pages=emptyList()` is non-null, so the lambda does not return early and the container composes with zero items — still a matchable node.

- [ ] **Step 3: Compile the production change.**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Write the smoke test.**

Create `src/androidTest/kotlin/me/proxer/app/manga/MangaScreenTest.kt`:

```kotlin
package me.proxer.app.manga

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.library.api.info.EntryCoreEndpoint
import me.proxer.library.api.manga.ChapterEndpoint
import me.proxer.library.entity.info.AdaptionInfo
import me.proxer.library.entity.info.EntryCore
import me.proxer.library.entity.manga.Chapter
import me.proxer.library.enums.Category
import me.proxer.library.enums.Language
import me.proxer.library.enums.License
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Manga launches via a bare Intent: id defaults to "-1", episode 1, language ENGLISH. The happy path flips
 * isFullscreen = true (LaunchedEffect on data), removing TopAppBar + BottomAppBar, and the reader is an
 * AndroidView SubsamplingScaleImageView with no Compose semantics -- so the only assertable happy-path node is
 * the MANGA_READER_TEST_TAG on the reader container. Chapter(pages = emptyList()) is a valid success (non-null
 * pages dodge the MangaLink/MangaNotAvailable branches); the container composes with zero items but is still a
 * matchable node.
 */
@RunWith(AndroidJUnit4::class)
class MangaScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val fixedDate = Date()

    private fun entryCore() = EntryCore(
        id = "-1",
        name = "Test Manga",
        genres = emptySet(),
        fskConstraints = emptySet(),
        description = "A description",
        medium = Medium.MANGASERIES,
        episodeAmount = 30,
        state = MediaState.FINISHED,
        ratingSum = 80,
        ratingAmount = 16,
        clicks = 200,
        category = Category.MANGA,
        license = License.UNKNOWN,
        adaptionInfo = AdaptionInfo("50", "Related Anime", Medium.ANIMESERIES),
    )

    private fun chapter() = Chapter(
        id = "1",
        entryId = "-1",
        title = "Chapter 1",
        uploaderId = "7",
        uploaderName = "Uploader",
        date = fixedDate,
        scanGroupId = "1",
        scanGroupName = "Scan Group",
        server = "https://example.com/manga",
        pages = emptyList(),
    )

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        val entryEndpoint = mockk<EntryCoreEndpoint>(relaxed = true)
        every { api.info.entryCore("-1") } returns entryEndpoint
        every { entryEndpoint.build() } returns mockProxerCall(entryCore())

        val chapterEndpoint = mockk<ChapterEndpoint>(relaxed = true)
        every { api.manga.chapter("-1", 1, Language.ENGLISH) } returns chapterEndpoint
        every { chapterEndpoint.build() } returns mockProxerCall(chapter())
    }

    @Test
    fun renders_reader_container_on_successful_load() {
        ActivityScenario.launch<MangaActivity>(
            Intent(context, MangaActivity::class.java),
        ).use {
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithTag(MANGA_READER_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag(MANGA_READER_TEST_TAG).assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 5: Run the test.**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.manga.MangaScreenTest`
Expected: BUILD SUCCESSFUL, `Starting 1 tests`, 0 failed.

If it fails on an unmatched `mangaReader` node, first confirm the default reader orientation composes the tagged container; both branches are tagged, so a genuine failure here more likely indicates the happy path did not reach the success lambda (check the stub argument values match the bare-Intent defaults `"-1"`, `1`, `Language.ENGLISH`).

- [ ] **Step 6: Commit.**

```bash
git add src/main/kotlin/me/proxer/app/manga/MangaScreen.kt \
        src/androidTest/kotlin/me/proxer/app/manga/MangaScreenTest.kt
git commit -m "test: Manga reader smoke test; add mangaReader testTag seam"
```

---

### Task 3: Anime smoke + deep tests

**Goal:** Two Anime tests in one file — an empty-state smoke test (reuses the `emptyList()` streams stub, asserts `error_no_data_anime`) and a populated deep test (builds an `AnimeStream`, asserts `Text(hosterName)`). No production seam.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/anime/AnimeScreenTest.kt`

**Acceptance Criteria:**
- [ ] `AnimeScreenTest` launches Anime via a bare Intent, stubbing `entryCore("-1")` + `streams("-1", 1, AnimeLanguage.ENGLISH_SUB)`.
- [ ] The smoke test stubs `streams = emptyList()` and asserts `R.string.error_no_data_anime` is displayed.
- [ ] The deep test stubs `streams = [AnimeStream]` (`resolutionResult=null`) and asserts the stream's `hosterName` Text is displayed.
- [ ] Neither test taps Play.
- [ ] Both tests pass on the API 31 AVD.

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.anime.AnimeScreenTest` → BUILD SUCCESSFUL, 2 tests, 0 failures.

**Steps:**

- [ ] **Step 1: Write the test file (both tests).**

Because the two tests need different `streams` payloads, each test builds its own endpoint stubs (not a shared `@Before` stub of `streams`). `stubLoggedIn` and the `entryCore` stub are shared in `@Before`.

Create `src/androidTest/kotlin/me/proxer/app/anime/AnimeScreenTest.kt`:

```kotlin
package me.proxer.app.anime

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
import me.proxer.library.api.anime.StreamsEndpoint
import me.proxer.library.api.info.EntryCoreEndpoint
import me.proxer.library.entity.info.AdaptionInfo
import me.proxer.library.entity.info.EntryCore
import me.proxer.library.enums.AnimeLanguage
import me.proxer.library.enums.Category
import me.proxer.library.enums.License
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.threeten.bp.Instant

/**
 * Anime launches via a bare Intent: id defaults to "-1", episode 1, language ENGLISH_SUB. AnimeScreen has NO
 * player surface (playback is a separate StreamActivity), so no player is constructed here -- but the tests must
 * never tap Play, which would fire a real Intent to StreamActivity.
 *
 * Do not pass a `name`/`episode_amount` extra: the title and EpisodeControlCard would then render from the
 * intent, vacuously. The honest fetch-backed targets are error_no_data_anime (empty streams) and the per-stream
 * Text(hosterName) header.
 */
@RunWith(AndroidJUnit4::class)
class AnimeScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var streamsEndpoint: StreamsEndpoint

    private fun entryCore() = EntryCore(
        id = "-1",
        name = "Test Anime",
        genres = emptySet(),
        fskConstraints = emptySet(),
        description = "A description",
        medium = Medium.ANIMESERIES,
        episodeAmount = 12,
        state = MediaState.FINISHED,
        ratingSum = 100,
        ratingAmount = 20,
        clicks = 500,
        category = Category.ANIME,
        license = License.UNKNOWN,
        adaptionInfo = AdaptionInfo("99", "Related Manga", Medium.MANGASERIES),
    )

    private fun animeStream(hosterName: String) = AnimeStream(
        id = "s0",
        hoster = "hoster",
        hosterName = hosterName,
        image = "image",
        uploaderId = "u0",
        uploaderName = "Uploader",
        date = Instant.EPOCH,
        translatorGroupId = null,
        translatorGroupName = null,
        isOfficial = false,
        isPublic = true,
        isSupported = true,
        resolutionResult = null,
    )

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        val entryEndpoint = mockk<EntryCoreEndpoint>(relaxed = true)
        every { api.info.entryCore("-1") } returns entryEndpoint
        every { entryEndpoint.build() } returns mockProxerCall(entryCore())

        streamsEndpoint = mockk(relaxed = true)
        every { api.anime.streams("-1", 1, AnimeLanguage.ENGLISH_SUB) } returns streamsEndpoint
        every { streamsEndpoint.includeProxerStreams(true) } returns streamsEndpoint
        // `build()` is stubbed per-test with the empty vs populated payload.
    }

    @Test
    fun renders_empty_state_when_no_streams() {
        every { streamsEndpoint.build() } returns mockProxerCall(emptyList())

        ActivityScenario.launch<AnimeActivity>(
            android.content.Intent(context, AnimeActivity::class.java),
        ).use {
            val emptyText = context.getString(R.string.error_no_data_anime)

            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText(emptyText).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(emptyText).assertIsDisplayed()
        }
    }

    @Test
    fun renders_hoster_name_when_streams_present() {
        every { streamsEndpoint.build() } returns mockProxerCall(listOf(animeStream("Hoster Alpha")))

        ActivityScenario.launch<AnimeActivity>(
            android.content.Intent(context, AnimeActivity::class.java),
        ).use {
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText("Hoster Alpha").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Hoster Alpha").assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Run both tests.**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.anime.AnimeScreenTest`
Expected: BUILD SUCCESSFUL, `Starting 2 tests`, 0 failed.

- [ ] **Step 3: Commit.**

```bash
git add src/androidTest/kotlin/me/proxer/app/anime/AnimeScreenTest.kt
git commit -m "test: Anime empty-state smoke + populated stream-list deep tests"
```

---

### Task 4: Full-suite verification

**Goal:** Run the entire instrumented suite and confirm 62 tests pass (58 existing + 4 new), recording the wall-clock delta against Group 4's ~63s baseline.

**Files:** none (verification only).

**Acceptance Criteria:**
- [ ] `./gradlew connectedDebugAndroidTest` reports `Starting 62 tests`, 0 failed.
- [ ] Wall-clock recorded and compared to the ~63s Group 4 baseline.
- [ ] The TV stateless-content tests (`TvSearchScreenTest` et al.) are among the passing tests (shared `TestApplication` not broken).

**Verify:** `./gradlew connectedDebugAndroidTest` → BUILD SUCCESSFUL, `Starting 62 tests`, 0 failed.

**Steps:**

- [ ] **Step 1: Run the full suite and time it.**

```bash
start=$(date +%s)
./gradlew connectedDebugAndroidTest 2>&1 | tail -20
echo "WALL_CLOCK_SECONDS=$(( $(date +%s) - start ))"
```

Expected: BUILD SUCCESSFUL, `Starting 62 tests on ci_api31_x86_64(AVD)`, 0 failed. Wall-clock ≈ 63–68s.

- [ ] **Step 2: Confirm the count and TV tests.**

Confirm the run reports exactly 62 tests and includes `me.proxer.app.tv.*` classes among them (grep the test-results or the console `Starting 62 tests`). If the count is not 62, a test class failed to run — investigate before proceeding.

- [ ] **Step 3: Record the result.**

No commit needed unless doc updates are made. Report: total tests, failures, wall-clock, delta vs ~63s.

**Note on a possible production NPE:** If any happy-path test surfaces a first-frame crash of the `data!!` class (the bug fixed in Group 3's `ConferenceInfoScreen`), stop and escalate to the user before changing `src/main` — do not fix a production screen silently. The plan does not anticipate one (`MangaContent` uses `data ?: return@ContentScreen`; the Anime empty-state is guarded), but the posture is carried per the Group 3 precedent.

---

## Self-Review

**Spec coverage:** All 4 spec tests mapped (Task 1 = ImageDetail smoke; Task 2 = Manga smoke; Task 3 = Anime smoke + deep; Task 4 = 62-test gate). All 3 seams mapped (Task 1 getIntent + contentDescription; Task 2 testTag). Traps encoded: bare-Intent defaults, concrete endpoints, empty-pages Manga success, no-name/episode extras for honest Anime assertions, never-tap-Play, Coil-bypass (not asserted), async ImageDetail error, themeObservable requirement, NPE escalation posture. StreamScreen out of scope — no task, correct.

**Placeholder scan:** None. Every code step carries complete code and exact commands.

**Type consistency:** `MANGA_READER_TEST_TAG` defined in Task 2 and used only there. Endpoint types (`EntryCoreEndpoint`/`StreamsEndpoint`/`ChapterEndpoint`), entity constructors (`EntryCore` 14-arg, `AnimeStream` 13-field, `Chapter` 10-arg, `AnimeStreamInfo`/`MangaChapterInfo`), and `mockProxerCall`/`stubLoggedIn` signatures all match the verified source. `getIntent(Context, HttpUrl): Intent` consistent between Task 1 code and the ImageDetail test call site.
