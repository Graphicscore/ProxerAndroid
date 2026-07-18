# Manga Chapter-Control Element Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the lost Manga-reader chapter-control element (uploader, translator group, date, mark-as-read / finish) as an in-scroll header + footer in all three orientations, and fix the bug where the app bars vanish permanently on chapter load.

**Architecture:** All work is confined to `src/main/kotlin/me/proxer/app/manga/MangaScreen.kt`. A new stateless `MangaChapterControls` Compose `Card` is rendered as the first and last item of the reader content (a `LazyColumn` in vertical mode, a `HorizontalPager` in LTR/RTL). A small sealed `MangaReaderItem` type keeps header/page/footer rendering uniform. Prev/next stay in the existing `BottomAppBar`; the card carries only metadata + bookmark/finish actions. A single-tap `OnClickListener` on each page view toggles `isFullscreen`, which drives both app bars and the system bars together.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (Material3), Koin, RxJava 2, `SubsamplingScaleImageView`, ProxerLibJava. Build via `./gradlew` (JBR at `/opt/android-studio/jbr`). No coroutines beyond Compose.

**Design reference:** `docs/superpowers/specs/2026-07-17-manga-chapter-controls-design.md` (on `master`).

**Reusable facts (all verified against the codebase / git history):**
- `MangaViewModel` already exposes `bookmark(episode: Int)` and `markAsFinished()`. `MangaScreen` already holds `currentEpisode`, `totalEpisodes`, and `data: MangaChapterInfo?` whose `.chapter` carries `uploaderId: String`, `uploaderName: String`, `scanGroupId: String?`, `scanGroupName: String?`, `date: java.util.Date`, `title: String?`, `pages: List<Page>?`.
- Success/error snackbars for bookmark/finish already exist via the `userStateData` / `userStateError` observers (including the not-logged-in error). **Do not add new snackbar logic.**
- `ProfileActivity.navigateTo(context: Activity, userId: String? = null, username: String? = null, ...)` — no-ops if both are null/blank.
- `TranslatorGroupActivity.navigateTo(context: Activity, id: String, name: String? = null)`.
- `Date.toLocalDateTimeBP(): LocalDateTime` (in `me.proxer.app.util.extension`); `Utils.dateFormatter` formats `LocalDateTime` as `dd.MM.yyyy`.
- All required strings already exist: `R.string.view_media_control_uploader`, `view_media_control_translator_group`, `view_media_control_date`, `view_media_control_finish`, `fragment_manga_bookmark_this_chapter`, `fragment_manga_bookmark_next_chapter`. **No new string resources.**

**Verification reality:** The changed code is Compose UI. There is no JVM unit-test harness for Compose in this repo (Robolectric is not set up), so per-task verification is a clean `compileDebugKotlin` + clean `detekt` + a green `@Preview`. Existing `MangaViewModel` JVM unit tests must remain green (`./gradlew testDebugUnitTest`) — this is the pipeline's overall gate. Instrumented `MangaScreenTest` assertions for the new UI are a device-only follow-up, out of this plan's scope.

---

### Task 1: `MangaChapterControls` composable + preview

**Goal:** Add a self-contained, stateless control-card composable (metadata rows + two action buttons) plus a `@Preview`, with no wiring into the reader yet.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/manga/MangaScreen.kt` (add new private composable + preview near the existing `MangaContentPreview`, and the needed imports)

**Acceptance Criteria:**
- [ ] A private `MangaChapterControls(...)` composable exists taking only display strings + lambdas (no `Chapter`, no ViewModel), so it is preview-safe.
- [ ] It renders a chapter-title line always; uploader/translator-group/date rows only when `showMetadata` is true and the corresponding value is non-null; uploader and translator rows are `clickable`.
- [ ] Two `TextButton`s: "mark this chapter read" and a second whose label is `view_media_control_finish` when `isLastChapter` else `fragment_manga_bookmark_next_chapter`.
- [ ] A `@Preview` composable `MangaChapterControlsPreview` renders it inside `ProxerTheme`.
- [ ] `./gradlew compileDebugKotlin` succeeds; `./gradlew detekt` reports no new violations.

**Verify:** `./gradlew compileDebugKotlin detekt --console=plain` → `BUILD SUCCESSFUL`, no detekt findings on `MangaScreen.kt`.

**Steps:**

- [ ] **Step 1: Add imports** (only those not already present in the file) near the other imports in `MangaScreen.kt`:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
```

(Note: `Column`, `Row`, `Spacer`, `Text`, `Modifier`, `Alignment`, `padding`, `fillMaxWidth`, `weight`, `dp`, `stringResource`, `MaterialTheme`, `ProxerTheme`, `Preview`, `Composable` are already imported.)

- [ ] **Step 2: Add the composable.** Place it directly after `MangaContentPreview` (currently ends near line 384):

```kotlin
@Composable
private fun MangaChapterControls(
    chapterTitle: String,
    uploaderName: String?,
    translatorGroupName: String?,
    dateText: String?,
    isLastChapter: Boolean,
    showMetadata: Boolean,
    onUploaderClick: () -> Unit,
    onTranslatorGroupClick: () -> Unit,
    onMarkThisRead: () -> Unit,
    onMarkReadUpToHereOrFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.titleMedium,
            )

            if (showMetadata) {
                if (uploaderName != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onUploaderClick)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.view_media_control_uploader),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = uploaderName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (translatorGroupName != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onTranslatorGroupClick)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.view_media_control_translator_group),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = translatorGroupName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (dateText != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.view_media_control_date),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = dateText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onMarkThisRead) {
                    Text(stringResource(R.string.fragment_manga_bookmark_this_chapter))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onMarkReadUpToHereOrFinish) {
                    val label = if (isLastChapter) {
                        stringResource(R.string.view_media_control_finish)
                    } else {
                        stringResource(R.string.fragment_manga_bookmark_next_chapter)
                    }
                    Text(label)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MangaChapterControlsPreview() {
    ProxerTheme {
        MangaChapterControls(
            chapterTitle = "Kapitel 3",
            uploaderName = "SomeUploader",
            translatorGroupName = "SomeGroup",
            dateText = "05.01.2024",
            isLastChapter = false,
            showMetadata = true,
            onUploaderClick = {},
            onTranslatorGroupClick = {},
            onMarkThisRead = {},
            onMarkReadUpToHereOrFinish = {},
        )
    }
}
```

- [ ] **Step 3: Verify compile + lint.**

Run: `./gradlew compileDebugKotlin detekt --console=plain`
Expected: `BUILD SUCCESSFUL`; no detekt findings referencing `MangaScreen.kt`.

---

### Task 2: Render header + footer in all orientations and wire actions

**Goal:** Insert the control card as the first (full-metadata) and last (actions-only) item of the reader content in vertical, LTR, and RTL modes, and connect its callbacks to `ProfileActivity`, `TranslatorGroupActivity`, and the ViewModel.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/manga/MangaScreen.kt` (add `MangaReaderItem`; extend `MangaContent` params + content rendering; wire callbacks in `MangaScreen`; update `MangaContentPreview`)

**Acceptance Criteria:**
- [ ] A private sealed interface `MangaReaderItem` with `Header`, `Footer`, and `PageItem(page)` exists.
- [ ] In vertical mode the `LazyColumn` renders `Header`, then all pages, then `Footer`, keyed stably. In LTR/RTL the `HorizontalPager` renders `[Header] + pages(+reversed for RTL) + [Footer]` as full-screen pages.
- [ ] Header uses `showMetadata = true`; footer uses `showMetadata = false`.
- [ ] Tapping uploader → `ProfileActivity.navigateTo`; translator group → `TranslatorGroupActivity.navigateTo`; "mark this read" → `viewModel.bookmark(currentEpisode)`; the second button → `viewModel.markAsFinished()` on the last chapter else `viewModel.bookmark(currentEpisode + 1)`.
- [ ] `MangaContentPreview` still compiles with the new parameters.
- [ ] `./gradlew compileDebugKotlin` and `./gradlew detekt` are clean; the reader still displays pages in every orientation (manual/preview check).

**Verify:** `./gradlew compileDebugKotlin detekt --console=plain` → `BUILD SUCCESSFUL`, no detekt findings on `MangaScreen.kt`.

**Steps:**

- [ ] **Step 1: Add imports** (only those not already present):

```kotlin
import me.proxer.app.profile.ProfileActivity
import me.proxer.app.info.translatorgroup.TranslatorGroupActivity
import me.proxer.app.util.Utils
import me.proxer.app.util.extension.toLocalDateTimeBP
```

- [ ] **Step 2: Add the sealed reader-item type.** Place it just above the `MangaChapterControls` composable:

```kotlin
private sealed interface MangaReaderItem {
    data object Header : MangaReaderItem
    data object Footer : MangaReaderItem
    data class PageItem(val page: Page) : MangaReaderItem
}
```

- [ ] **Step 3: Extend `MangaContent`'s signature.** Add these parameters to the `private fun MangaContent(...)` declaration (after the existing `onLowMemory: () -> Unit,`):

```kotlin
    uploaderName: String?,
    translatorGroupName: String?,
    dateText: String?,
    isLastChapter: Boolean,
    onUploaderClick: () -> Unit,
    onTranslatorGroupClick: () -> Unit,
    onMarkThisRead: () -> Unit,
    onMarkReadUpToHereOrFinish: () -> Unit,
```

- [ ] **Step 4: Replace the content body** inside `ContentScreen { ... }` (currently the `val chapterData = ...` through the end of the `when (readerOrientation) { ... }` block) with a version that builds a `MangaReaderItem` list and renders header/footer. Replace this existing block:

```kotlin
            val chapterData = data ?: return@ContentScreen
            val pages = chapterData.chapter.pages ?: return@ContentScreen

            when (readerOrientation) {
                MangaReaderOrientation.VERTICAL -> {
                    val screenWidth = remember { DeviceUtils.getScreenWidth(context) }
                    LazyColumn(modifier = Modifier.fillMaxSize().testTag(MANGA_READER_TEST_TAG)) {
                        items(pages, key = { it.decodedName }) { page ->
                            MangaPage(
                                page = page,
                                chapter = chapterData.chapter,
                                isVertical = true,
                                screenWidth = screenWidth,
                                onLowMemory = onLowMemory,
                            )
                        }
                    }
                }

                MangaReaderOrientation.LEFT_TO_RIGHT, MangaReaderOrientation.RIGHT_TO_LEFT -> {
                    val displayPages = if (readerOrientation == MangaReaderOrientation.RIGHT_TO_LEFT) {
                        pages.reversed()
                    } else {
                        pages
                    }
                    val pagerState = rememberPagerState { displayPages.size }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().testTag(MANGA_READER_TEST_TAG),
                    ) { pageIndex ->
                        MangaPage(
                            page = displayPages[pageIndex],
                            chapter = chapterData.chapter,
                            isVertical = false,
                            screenWidth = 0,
                            onLowMemory = onLowMemory,
                        )
                    }
                }
            }
```

with:

```kotlin
            val chapterData = data ?: return@ContentScreen
            val pages = chapterData.chapter.pages ?: return@ContentScreen

            val orderedPages = if (readerOrientation == MangaReaderOrientation.RIGHT_TO_LEFT) {
                pages.reversed()
            } else {
                pages
            }
            val readerItems = remember(pages, readerOrientation) {
                buildList<MangaReaderItem> {
                    add(MangaReaderItem.Header)
                    orderedPages.forEach { add(MangaReaderItem.PageItem(it)) }
                    add(MangaReaderItem.Footer)
                }
            }

            val controlsTitle = displayChapterTitle?.takeIf { it.isNotBlank() } ?: episodeLabel

            val itemKey: (MangaReaderItem) -> Any = { item ->
                when (item) {
                    MangaReaderItem.Header -> "manga-header"
                    MangaReaderItem.Footer -> "manga-footer"
                    is MangaReaderItem.PageItem -> item.page.decodedName
                }
            }

            when (readerOrientation) {
                MangaReaderOrientation.VERTICAL -> {
                    val screenWidth = remember { DeviceUtils.getScreenWidth(context) }
                    LazyColumn(modifier = Modifier.fillMaxSize().testTag(MANGA_READER_TEST_TAG)) {
                        items(readerItems, key = itemKey) { item ->
                            when (item) {
                                MangaReaderItem.Header -> MangaChapterControls(
                                    chapterTitle = controlsTitle,
                                    uploaderName = uploaderName,
                                    translatorGroupName = translatorGroupName,
                                    dateText = dateText,
                                    isLastChapter = isLastChapter,
                                    showMetadata = true,
                                    onUploaderClick = onUploaderClick,
                                    onTranslatorGroupClick = onTranslatorGroupClick,
                                    onMarkThisRead = onMarkThisRead,
                                    onMarkReadUpToHereOrFinish = onMarkReadUpToHereOrFinish,
                                )
                                MangaReaderItem.Footer -> MangaChapterControls(
                                    chapterTitle = controlsTitle,
                                    uploaderName = uploaderName,
                                    translatorGroupName = translatorGroupName,
                                    dateText = dateText,
                                    isLastChapter = isLastChapter,
                                    showMetadata = false,
                                    onUploaderClick = onUploaderClick,
                                    onTranslatorGroupClick = onTranslatorGroupClick,
                                    onMarkThisRead = onMarkThisRead,
                                    onMarkReadUpToHereOrFinish = onMarkReadUpToHereOrFinish,
                                )
                                is MangaReaderItem.PageItem -> MangaPage(
                                    page = item.page,
                                    chapter = chapterData.chapter,
                                    isVertical = true,
                                    screenWidth = screenWidth,
                                    onLowMemory = onLowMemory,
                                )
                            }
                        }
                    }
                }

                MangaReaderOrientation.LEFT_TO_RIGHT, MangaReaderOrientation.RIGHT_TO_LEFT -> {
                    val pagerState = rememberPagerState { readerItems.size }
                    HorizontalPager(
                        state = pagerState,
                        key = { index -> itemKey(readerItems[index]) },
                        modifier = Modifier.fillMaxSize().testTag(MANGA_READER_TEST_TAG),
                    ) { pageIndex ->
                        when (val item = readerItems[pageIndex]) {
                            MangaReaderItem.Header -> Box(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.Center,
                            ) {
                                MangaChapterControls(
                                    chapterTitle = controlsTitle,
                                    uploaderName = uploaderName,
                                    translatorGroupName = translatorGroupName,
                                    dateText = dateText,
                                    isLastChapter = isLastChapter,
                                    showMetadata = true,
                                    onUploaderClick = onUploaderClick,
                                    onTranslatorGroupClick = onTranslatorGroupClick,
                                    onMarkThisRead = onMarkThisRead,
                                    onMarkReadUpToHereOrFinish = onMarkReadUpToHereOrFinish,
                                )
                            }
                            MangaReaderItem.Footer -> Box(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.Center,
                            ) {
                                MangaChapterControls(
                                    chapterTitle = controlsTitle,
                                    uploaderName = uploaderName,
                                    translatorGroupName = translatorGroupName,
                                    dateText = dateText,
                                    isLastChapter = isLastChapter,
                                    showMetadata = false,
                                    onUploaderClick = onUploaderClick,
                                    onTranslatorGroupClick = onTranslatorGroupClick,
                                    onMarkThisRead = onMarkThisRead,
                                    onMarkReadUpToHereOrFinish = onMarkReadUpToHereOrFinish,
                                )
                            }
                            is MangaReaderItem.PageItem -> MangaPage(
                                page = item.page,
                                chapter = chapterData.chapter,
                                isVertical = false,
                                screenWidth = 0,
                                onLowMemory = onLowMemory,
                            )
                        }
                    }
                }
            }
```

- [ ] **Step 5: Add the two imports needed by the pager header/footer scroll** (if not already present):

```kotlin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
```

- [ ] **Step 6: Wire the callbacks in `MangaScreen`.** Just before the `MangaContent(` call (currently line ~168), add derived values:

```kotlin
    val chapter = data?.chapter
    val isLastChapter = totalEpisodes?.let { currentEpisode >= it } == true
    val uploaderName = chapter?.uploaderName
    val translatorGroupName = chapter?.scanGroupName
    val dateText = remember(chapter) {
        chapter?.date?.let { Utils.dateFormatter.format(it.toLocalDateTimeBP()) }
    }
```

Then add these arguments to the `MangaContent(...)` call (alongside the existing ones, e.g. after `onLowMemory = { ... },`):

```kotlin
        uploaderName = uploaderName,
        translatorGroupName = translatorGroupName,
        dateText = dateText,
        isLastChapter = isLastChapter,
        onUploaderClick = {
            data?.chapter?.let { c -> ProfileActivity.navigateTo(activity, c.uploaderId, c.uploaderName) }
        },
        onTranslatorGroupClick = {
            val c = data?.chapter
            val groupId = c?.scanGroupId
            if (c != null && groupId != null) {
                TranslatorGroupActivity.navigateTo(activity, groupId, c.scanGroupName)
            }
        },
        onMarkThisRead = { viewModel.bookmark(currentEpisode) },
        onMarkReadUpToHereOrFinish = {
            val total = totalEpisodes
            if (total != null && currentEpisode >= total) {
                viewModel.markAsFinished()
            } else {
                viewModel.bookmark(currentEpisode + 1)
            }
        },
```

These lambdas use `let` / `if` guards rather than labeled returns, so there is no `return@` ambiguity from multiple lambda arguments.

- [ ] **Step 7: Update `MangaContentPreview`.** Add the new arguments to its `MangaContent(...)` call:

```kotlin
            uploaderName = "SomeUploader",
            translatorGroupName = "SomeGroup",
            dateText = "05.01.2024",
            isLastChapter = false,
            onUploaderClick = {},
            onTranslatorGroupClick = {},
            onMarkThisRead = {},
            onMarkReadUpToHereOrFinish = {},
```

- [ ] **Step 8: Verify compile + lint.**

Run: `./gradlew compileDebugKotlin detekt --console=plain`
Expected: `BUILD SUCCESSFUL`; no detekt findings on `MangaScreen.kt`.

---

### Task 3: Tap-to-toggle the app + system bars

**Goal:** Make a single tap on any page toggle `isFullscreen` (hiding/showing the top bar, bottom bar, and system bars together), so the bars are recoverable after the reader auto-enters immersive on load.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/manga/MangaScreen.kt` (restructure the fullscreen `LaunchedEffect`; add `onToggleUi` threaded to `MangaPage` → `MangaImagePage` / `MangaGifPage`; set `OnClickListener` on the page views; update previews)

**Acceptance Criteria:**
- [ ] The reader auto-enters immersive (`isFullscreen = true`) the first time a chapter becomes ready, exactly as today.
- [ ] A single tap on a manga page (regular or GIF) flips `isFullscreen`; when it becomes false both `TopAppBar` and `BottomAppBar` reappear together with the system bars, and tapping again hides them.
- [ ] During loading/error the bars stay visible (no immersive).
- [ ] Pan/zoom on `SubsamplingScaleImageView` and pager swipe still work (tap toggle uses the view's single-tap `performClick`, not a gesture overlay).
- [ ] `./gradlew compileDebugKotlin` and `./gradlew detekt` are clean.

**Verify:** `./gradlew compileDebugKotlin detekt --console=plain` → `BUILD SUCCESSFUL`, no detekt findings on `MangaScreen.kt`.

**Steps:**

- [ ] **Step 1: Replace the fullscreen effect** in `MangaScreen`. Replace this existing block (currently lines ~151-162):

```kotlin
    // Fullscreen: hide system bars when content is ready, show during loading/error
    LaunchedEffect(data, error) {
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (data != null && error == null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            isFullscreen = true
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            isFullscreen = false
        }
    }
```

with:

```kotlin
    val isContentReady = data != null && error == null

    // Auto-enter immersive the first time content becomes ready; leave immersive on loading/error.
    LaunchedEffect(isContentReady) {
        isFullscreen = isContentReady
    }

    // Apply system-bar visibility whenever the fullscreen intent or readiness changes.
    LaunchedEffect(isFullscreen, isContentReady) {
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (isContentReady && isFullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
```

- [ ] **Step 2: Add the toggle argument to the `MangaContent(...)` call** in `MangaScreen` (alongside the other callbacks):

```kotlin
        onToggleUi = { if (isContentReady) isFullscreen = !isFullscreen },
```

- [ ] **Step 3: Add `onToggleUi` to `MangaContent`'s signature** (after the callbacks added in Task 2):

```kotlin
    onToggleUi: () -> Unit,
```

- [ ] **Step 4: Pass `onToggleUi` down to every `MangaPage` call** (both the vertical and horizontal branches from Task 2). Update each `MangaPage(` invocation to add:

```kotlin
                                    onToggleUi = onToggleUi,
```

- [ ] **Step 5: Thread `onToggleUi` through `MangaPage`.** Change its signature and both call sites inside it:

```kotlin
@Composable
private fun MangaPage(
    page: Page,
    chapter: Chapter,
    isVertical: Boolean,
    screenWidth: Int,
    onLowMemory: () -> Unit,
    onToggleUi: () -> Unit,
) {
    if (page.decodedName.endsWith(".gif", ignoreCase = true)) {
        MangaGifPage(
            page = page,
            chapter = chapter,
            isVertical = isVertical,
            screenWidth = screenWidth,
            onToggleUi = onToggleUi,
        )
    } else {
        MangaImagePage(
            page = page,
            chapter = chapter,
            isVertical = isVertical,
            screenWidth = screenWidth,
            onLowMemory = onLowMemory,
            onToggleUi = onToggleUi,
        )
    }
}
```

- [ ] **Step 6: Set the click listener on the `SubsamplingScaleImageView`.** In `MangaImagePage`, add `onToggleUi: () -> Unit,` to the signature, and inside the `AndroidView` `factory`'s `SubsamplingScaleImageView(ctx).apply { ... }` block add (e.g. right after `setMinimumDpi(90)`):

```kotlin
                        isClickable = true
                        setOnClickListener { onToggleUi() }
```

`SubsamplingScaleImageView` invokes `performClick()` on a confirmed single tap (the same hook the old `MangaAdapter` used via `image.clicks()`), so this does not interfere with pan/zoom.

- [ ] **Step 7: Set the click listener on the GIF `ImageView`.** In `MangaGifPage`, add `onToggleUi: () -> Unit,` to the signature, and inside the `AndroidView` `factory`'s `ImageView(ctx).apply { ... }` block add:

```kotlin
                        setOnClickListener { onToggleUi() }
```

- [ ] **Step 8: Update `MangaContentPreview`** — add `onToggleUi = {},` to its `MangaContent(...)` call.

- [ ] **Step 9: Verify compile + lint, and that the existing unit suite is unaffected.**

Run: `./gradlew compileDebugKotlin detekt --console=plain`
Expected: `BUILD SUCCESSFUL`; no detekt findings on `MangaScreen.kt`.

Run: `./gradlew testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL` (existing `MangaViewModelTest` and the rest stay green).

---

## Notes for the executor

- **Order matters:** Task 2 depends on Task 1 (uses `MangaChapterControls`); Task 3 depends on Task 2 (edits the `MangaPage` calls Task 2 rewrote). Do them 1 → 2 → 3.
- **Single file:** every change is in `MangaScreen.kt`. Do not touch `MangaViewModel.kt`, strings, or layouts.
- **No new strings, no prev/next on the card, no edge-tap pagination** (explicit scope guard).
- **Commit:** the ship pipeline commits after execution; no per-task `git commit` step is required here.
