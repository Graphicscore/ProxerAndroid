# TV Compose Previews Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `@Preview` annotations to all TV Compose screens and sub-composables by extracting stateless content composables from ViewModel-driven screens.

**Architecture:** Each VM-driven screen is split into a stateless `TvXxxContent(...)` composable (plain data + callbacks, fully previewable) and a thin stateful wrapper (unchanged public API, observes ViewModel). Private sub-composables (cards, items) are previewed via `@Preview` functions in the same file. Shared fake data lives in `TvPreviewData.kt`. `TvErrorView` loses its internal `koinInject<PreferenceHelper>()` in favour of an `onAgeConfirmed: () -> Unit = {}` parameter.

**Tech Stack:** Compose for TV (`androidx.tv.material3`), `androidx.compose.ui.tooling.preview.Preview`, Koin 4.2.1, ProxerLibJava 5.4.0, Kotlin 2.2.10.

---

### Task 0: Create shared preview data file

**Goal:** Provide fake library entity instances used by `@Preview` functions across all TV screens.

**Files:**
- Create: `src/main/kotlin/me/proxer/app/tv/TvPreviewData.kt`

**Acceptance Criteria:**
- [ ] File compiles without errors (`./gradlew compileDebugKotlin`)
- [ ] All five helper functions (`fakeMediaListEntry`, `fakeBookmark`, `fakeCalendarEntry`, `fakeEpisodeRow`, `fakeAnimeStream`) are present and return non-null values
- [ ] Functions are `internal` (not part of the public API)

**Verify:** `./gradlew compileDebugKotlin --rerun-tasks 2>&1 | grep -E "error:|BUILD"` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Create `TvPreviewData.kt`**

```kotlin
package me.proxer.app.tv

import me.proxer.app.anime.AnimeStream
import me.proxer.app.media.episode.EpisodeRow
import me.proxer.library.entity.info.AnimeEpisode
import me.proxer.library.entity.list.MediaListEntry
import me.proxer.library.entity.media.CalendarEntry
import me.proxer.library.entity.ucp.Bookmark
import me.proxer.library.enums.CalendarDay
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaLanguage
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.threeten.bp.Instant
import java.util.Date

internal fun fakeMediaListEntry(
    id: String = "1",
    name: String = "Attack on Titan",
) = MediaListEntry(
    id = id,
    name = name,
    genres = emptySet(),
    medium = Medium.ANIMESERIES,
    episodeAmount = 25,
    state = MediaState.FINISHED,
    ratingSum = 890,
    ratingAmount = 100,
    languages = setOf(MediaLanguage.ENGLISH_SUB),
)

internal fun fakeBookmark(
    id: String = "1",
    entryId: String = "1",
    name: String = "Attack on Titan",
    episode: Int = 5,
) = Bookmark(
    id = id,
    entryId = entryId,
    category = Category.ANIME,
    name = name,
    episode = episode,
    language = MediaLanguage.ENGLISH_SUB,
    medium = Medium.ANIMESERIES,
    state = MediaState.FINISHED,
    chapterName = null,
    isAvailable = true,
)

internal fun fakeCalendarEntry(
    id: String = "1",
    day: CalendarDay = CalendarDay.MONDAY,
    name: String = "Attack on Titan",
) = CalendarEntry(
    id = id,
    entryId = "1",
    name = name,
    episode = 5,
    episodeTitle = "The Fall of Shiganshina",
    date = Date(System.currentTimeMillis() + 3_600_000L),
    timezone = "UTC",
    industryId = "0",
    industryName = null,
    weekDay = day,
    uploadDate = Date(System.currentTimeMillis() + 7_200_000L),
    genres = emptySet(),
    ratingSum = 890,
    ratingAmount = 100,
)

internal fun fakeEpisodeRow(
    number: Int = 1,
    watched: Boolean = false,
) = EpisodeRow(
    category = Category.ANIME,
    userProgress = if (watched) number else null,
    episodeAmount = 24,
    episodes = listOf(
        AnimeEpisode(
            number = number,
            language = MediaLanguage.ENGLISH_SUB,
            hosters = emptySet(),
            hosterImages = emptyList(),
        ),
    ),
)

internal fun fakeAnimeStream(
    id: String = "1",
    hosterName: String = "Vidoza",
    isOfficial: Boolean = false,
    isSupported: Boolean = true,
) = AnimeStream(
    id = id,
    hoster = "vidoza",
    hosterName = hosterName,
    image = "",
    uploaderId = "1",
    uploaderName = "SubsPlease",
    date = Instant.now(),
    translatorGroupId = null,
    translatorGroupName = null,
    isOfficial = isOfficial,
    isPublic = true,
    isSupported = isSupported,
    resolutionResult = null,
)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"`  
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/tv/TvPreviewData.kt
git commit -m "feat(tv/preview): add shared fake data helpers for @Preview"
```

---

### Task 1: Refactor TvErrorView — replace koinInject with onAgeConfirmed parameter

**Goal:** Remove `koinInject<PreferenceHelper>()` from `TvErrorView` and replace the internal age-confirmation side-effect with an `onAgeConfirmed: () -> Unit = {}` callback parameter.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/tv/TvErrorView.kt`

**Acceptance Criteria:**
- [ ] `TvErrorView` no longer imports or calls `koinInject`
- [ ] `TvErrorView` compiles without a Koin dependency
- [ ] Existing callers (which do not pass `onAgeConfirmed`) still compile because the parameter has a default value
- [ ] The age-confirmation `AlertDialog` confirm button calls `onAgeConfirmed()` instead of setting `preferenceHelper.isAgeRestrictedMediaAllowed = true`
- [ ] `@Preview` functions added for retry and login error states

**Verify:** `./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Replace koinInject with parameter in `TvErrorView.kt`**

Remove the import:
```kotlin
import me.proxer.app.util.data.PreferenceHelper
import org.koin.compose.koinInject
```

Add parameter `onAgeConfirmed: () -> Unit = {}` to the function signature:
```kotlin
@Composable
fun TvErrorView(
    error: ErrorUtils.ErrorAction,
    onLoginClick: (() -> Unit)? = null,
    onRetryClick: () -> Unit,
    onAgeConfirmed: () -> Unit = {},
) {
```

Remove the line inside the function body:
```kotlin
val preferenceHelper: PreferenceHelper = koinInject()
```

In the `AlertDialog` confirm button `onClick`, replace:
```kotlin
preferenceHelper.isAgeRestrictedMediaAllowed = true
showAgeConfirmDialog = false
```
with:
```kotlin
onAgeConfirmed()
showAgeConfirmDialog = false
```

- [ ] **Step 2: Add `@Preview` functions at the bottom of `TvErrorView.kt`**

Add these imports if not already present:
```kotlin
import androidx.compose.ui.tooling.preview.Preview
import me.proxer.app.R
```

Add at the bottom of the file (outside the composable functions, still in the same file):
```kotlin
@Preview(showBackground = true)
@Composable
private fun TvErrorViewRetryPreview() {
    TvTheme {
        TvErrorView(
            error = ErrorUtils.ErrorAction(message = R.string.error_unknown),
            onRetryClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TvErrorViewLoginPreview() {
    TvTheme {
        TvErrorView(
            error = ErrorUtils.ErrorAction(
                message = R.string.error_unknown,
                buttonAction = ErrorUtils.ErrorAction.ButtonAction.LOGIN,
            ),
            onLoginClick = {},
            onRetryClick = {},
        )
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"`  
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/tv/TvErrorView.kt
git commit -m "refactor(tv): replace TvErrorView koinInject with onAgeConfirmed callback"
```

---

### Task 2: Add previews to TvPlaceholderScreen and TvNavigationDrawer

**Goal:** Add `@Preview` functions to `TvPlaceholderScreen` (already stateless) and `TvNavigationDrawerContent` (requires wrapping in `NavigationDrawer`).

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/tv/TvPlaceholderScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/tv/TvNavigationDrawer.kt`

**Acceptance Criteria:**
- [ ] `TvPlaceholderScreen` has a `@Preview` function rendering "Coming Soon" text
- [ ] `TvNavigationDrawerContent` has two `@Preview` functions: logged-out and logged-in
- [ ] Both files compile without errors

**Verify:** `./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add preview to `TvPlaceholderScreen.kt`**

Add import:
```kotlin
import androidx.compose.ui.tooling.preview.Preview
```

Add at the bottom of the file:
```kotlin
@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun TvPlaceholderScreenPreview() {
    TvTheme { TvPlaceholderScreen(sectionName = "Settings") }
}
```

- [ ] **Step 2: Add previews to `TvNavigationDrawer.kt`**

Add imports:
```kotlin
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.NavigationDrawer
import me.proxer.app.auth.LocalUser
```

Add at the bottom of the file:
```kotlin
@Preview(device = "id:tv_1080p", showBackground = true, name = "Logged out")
@Composable
private fun TvNavigationDrawerContentLoggedOutPreview() {
    TvTheme {
        NavigationDrawer(
            drawerContent = { drawerValue ->
                TvNavigationDrawerContent(
                    currentSection = TvSection.ANIME,
                    user = null,
                    drawerValue = drawerValue,
                    onSectionSelected = {},
                    onLoginClick = {},
                    onLogoutClick = {},
                )
            },
        ) {}
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Logged in")
@Composable
private fun TvNavigationDrawerContentLoggedInPreview() {
    TvTheme {
        NavigationDrawer(
            drawerContent = { drawerValue ->
                TvNavigationDrawerContent(
                    currentSection = TvSection.BOOKMARKS,
                    user = LocalUser(token = "", id = "1", name = "Asteria", image = ""),
                    drawerValue = drawerValue,
                    onSectionSelected = {},
                    onLoginClick = {},
                    onLogoutClick = {},
                )
            },
        ) {}
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"`  
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/tv/TvPlaceholderScreen.kt \
        src/main/kotlin/me/proxer/app/tv/TvNavigationDrawer.kt
git commit -m "feat(tv/preview): add @Preview for TvPlaceholderScreen and TvNavigationDrawer"
```

---

### Task 3: Extract TvBrowseScreenContent and add previews

**Goal:** Split `TvBrowseScreen` into a stateless `TvBrowseScreenContent` composable and a thin ViewModel wrapper; add `@Preview` for the content and for `TvMediaCard`.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/tv/TvBrowseScreen.kt`

**Acceptance Criteria:**
- [ ] `TvBrowseScreenContent(entries, isLoading, error, onMediaClick, onSearchClick, onLoadMore, onRetry, onLoginClick, onAgeConfirmed)` exists and contains all layout code
- [ ] `TvBrowseScreen` is a thin wrapper that only observes the ViewModel and delegates to `TvBrowseScreenContent`
- [ ] `TvBrowseScreen` public signature is unchanged: `fun TvBrowseScreen(onMediaClick: (String, String) -> Unit, onSearchClick: () -> Unit)`
- [ ] Three `@Preview` functions: content empty state, content loading state, `TvMediaCard`
- [ ] Compiles without errors

**Verify:** `./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add `TvBrowseScreenContent` signature above `TvBrowseScreen`**

Insert this new composable before `TvBrowseScreen` in the file:

```kotlin
@Composable
fun TvBrowseScreenContent(
    entries: List<MediaListEntry>?,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    onMediaClick: (String, String) -> Unit,
    onSearchClick: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onLoginClick: () -> Unit = {},
    onAgeConfirmed: () -> Unit = {},
) {
    // move the entire body of TvBrowseScreen from `val gridState = ...`
    // downwards into here, replacing:
    //   viewModel.loadIfPossible()  →  onLoadMore()
    //   viewModel.load()            →  onRetry()
    //   context.startActivity<TvLoginActivity>()  →  onLoginClick()
    // and pass onAgeConfirmed to TvErrorView:
    //   TvErrorView(error = error!!, onLoginClick = onLoginClick, onRetryClick = onRetry, onAgeConfirmed = onAgeConfirmed)
}
```

- [ ] **Step 2: Replace `TvBrowseScreen` body with a thin wrapper**

After moving the layout code to `TvBrowseScreenContent`, rewrite `TvBrowseScreen`:

```kotlin
@Composable
fun TvBrowseScreen(
    onMediaClick: (String, String) -> Unit,
    onSearchClick: () -> Unit,
) {
    val viewModel: MediaListViewModel =
        koinViewModel {
            parametersOf(
                MediaSearchSortCriteria.RATING,
                MediaType.ANIMESERIES,
                null as String?,
                null as Language?,
                emptyList<LocalTag>(),
                emptyList<LocalTag>(),
                enumSetOf<FskConstraint>(),
                emptyList<LocalTag>(),
                emptyList<LocalTag>(),
                null as TagRateFilter?,
                null as TagSpoilerFilter?,
                null as Boolean?,
            )
        }

    val preferenceHelper: PreferenceHelper = koinInject()
    val context = LocalContext.current
    val entries by viewModel.data.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    TvBrowseScreenContent(
        entries = entries,
        isLoading = isLoading ?: false,
        error = error,
        onMediaClick = onMediaClick,
        onSearchClick = onSearchClick,
        onLoadMore = { viewModel.loadIfPossible() },
        onRetry = { viewModel.load() },
        onLoginClick = { context.startActivity<TvLoginActivity>() },
        onAgeConfirmed = { preferenceHelper.isAgeRestrictedMediaAllowed = true },
    )
}
```

Add these imports (if not already present):
```kotlin
import me.proxer.app.util.data.PreferenceHelper
import org.koin.compose.koinInject
```

- [ ] **Step 3: Add `@Preview` functions at the bottom of the file**

Add imports:
```kotlin
import androidx.compose.ui.tooling.preview.Preview
import me.proxer.app.R
import me.proxer.app.util.ErrorUtils
```

Add at the bottom:
```kotlin
@Preview(device = "id:tv_1080p", showBackground = true, name = "Empty")
@Composable
private fun TvBrowseScreenContentEmptyPreview() {
    TvTheme {
        TvBrowseScreenContent(
            entries = emptyList(),
            isLoading = false,
            error = null,
            onMediaClick = { _, _ -> },
            onSearchClick = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Loading")
@Composable
private fun TvBrowseScreenContentLoadingPreview() {
    TvTheme {
        TvBrowseScreenContent(
            entries = emptyList(),
            isLoading = true,
            error = null,
            onMediaClick = { _, _ -> },
            onSearchClick = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TvMediaCardPreview() {
    TvTheme {
        TvMediaCard(
            entry = fakeMediaListEntry(),
            onClick = {},
        )
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"`  
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/tv/TvBrowseScreen.kt
git commit -m "feat(tv/preview): extract TvBrowseScreenContent and add @Preview"
```

---

### Task 4: Extract TvBookmarksScreenContent and add previews

**Goal:** Split `TvBookmarksScreen` into a stateless content composable and a ViewModel wrapper; add previews for the screen and `TvBookmarkCard`.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/tv/TvBookmarksScreen.kt`

**Acceptance Criteria:**
- [ ] `TvBookmarksScreenContent(entries, isLoading, error, onBookmarkClick, onRetry, onLoginClick, onAgeConfirmed)` exists with all layout code
- [ ] `TvBookmarksScreen` is a thin wrapper; public signature `fun TvBookmarksScreen()` unchanged
- [ ] Three `@Preview` functions: content empty, content loading, `TvBookmarkCard`
- [ ] Compiles without errors

**Verify:** `./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add `TvBookmarksScreenContent` above `TvBookmarksScreen`**

```kotlin
@Composable
fun TvBookmarksScreenContent(
    entries: List<Bookmark>?,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    onBookmarkClick: (Bookmark) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onLoginClick: () -> Unit = {},
    onAgeConfirmed: () -> Unit = {},
) {
    // Move the entire Column + when{} body from TvBookmarksScreen here.
    // Replace:
    //   TvEpisodeActivity.navigateTo(context, ...) → onBookmarkClick(bookmark)
    //   context.startActivity<TvLoginActivity>()   → onLoginClick()
    //   viewModel.load()                           → onRetry()
    //   viewModel.loadIfPossible()                 → onLoadMore()
    // Pass onAgeConfirmed to TvErrorView.
}
```

- [ ] **Step 2: Replace `TvBookmarksScreen` body with a thin wrapper**

```kotlin
@Composable
fun TvBookmarksScreen() {
    val viewModel: BookmarkViewModel =
        koinViewModel { parametersOf(null, Category.ANIME, false) }
    val preferenceHelper: PreferenceHelper = koinInject()
    val context = LocalContext.current
    val entries by viewModel.data.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    TvBookmarksScreenContent(
        entries = entries,
        isLoading = isLoading ?: false,
        error = error,
        onBookmarkClick = { bookmark ->
            TvEpisodeActivity.navigateTo(context, bookmark.entryId, bookmark.name, 0)
        },
        onRetry = { viewModel.load() },
        onLoadMore = { viewModel.loadIfPossible() },
        onLoginClick = { context.startActivity<TvLoginActivity>() },
        onAgeConfirmed = { preferenceHelper.isAgeRestrictedMediaAllowed = true },
    )
}
```

Add imports if not present:
```kotlin
import me.proxer.app.util.data.PreferenceHelper
import org.koin.compose.koinInject
```

- [ ] **Step 3: Add `@Preview` functions**

```kotlin
import androidx.compose.ui.tooling.preview.Preview
import me.proxer.app.R
import me.proxer.app.util.ErrorUtils
```

```kotlin
@Preview(device = "id:tv_1080p", showBackground = true, name = "Empty")
@Composable
private fun TvBookmarksScreenContentEmptyPreview() {
    TvTheme {
        TvBookmarksScreenContent(
            entries = emptyList(),
            isLoading = false,
            error = null,
            onBookmarkClick = {},
            onRetry = {},
            onLoadMore = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Loading")
@Composable
private fun TvBookmarksScreenContentLoadingPreview() {
    TvTheme {
        TvBookmarksScreenContent(
            entries = emptyList(),
            isLoading = true,
            error = null,
            onBookmarkClick = {},
            onRetry = {},
            onLoadMore = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TvBookmarkCardPreview() {
    TvTheme {
        TvBookmarkCard(
            bookmark = fakeBookmark(),
            onClick = {},
        )
    }
}
```

- [ ] **Step 4: Verify + commit**

```bash
./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"
# expect: BUILD SUCCESSFUL
git add src/main/kotlin/me/proxer/app/tv/TvBookmarksScreen.kt
git commit -m "feat(tv/preview): extract TvBookmarksScreenContent and add @Preview"
```

---

### Task 5: Extract TvScheduleScreenContent and add previews

**Goal:** Split `TvScheduleScreen` into a stateless content composable and a ViewModel wrapper; add previews for the screen, `TvScheduleDayRow`, and `TvScheduleCard`.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/tv/TvScheduleScreen.kt`

**Acceptance Criteria:**
- [ ] `TvScheduleScreenContent(schedule, isLoading, error, onEntryClick, onRetry)` exists
- [ ] `TvScheduleScreen` is a thin wrapper; public signature `fun TvScheduleScreen()` unchanged
- [ ] `@Preview` functions: content (with fake schedule), `TvScheduleDayRow`, `TvScheduleCard`
- [ ] Compiles without errors

**Verify:** `./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add `TvScheduleScreenContent` above `TvScheduleScreen`**

```kotlin
@Composable
fun TvScheduleScreenContent(
    schedule: Map<CalendarDay, List<CalendarEntry>>?,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    onEntryClick: (CalendarEntry) -> Unit,
    onRetry: () -> Unit,
) {
    // Move the entire when{} block from TvScheduleScreen here.
    // Replace:
    //   viewModel.load()                              → onRetry()
    //   TvMediaDetailActivity.navigateTo(context, …) → onEntryClick(entry)
    // Note: LocalContext.current is still needed inside TvScheduleDayRow.
}
```

- [ ] **Step 2: Replace `TvScheduleScreen` body with a thin wrapper**

```kotlin
@Composable
fun TvScheduleScreen() {
    val viewModel: ScheduleViewModel = koinViewModel()
    val context = LocalContext.current
    val schedule by viewModel.data.observeAsState(emptyMap())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    TvScheduleScreenContent(
        schedule = schedule,
        isLoading = isLoading ?: false,
        error = error,
        onEntryClick = { entry -> TvMediaDetailActivity.navigateTo(context, entry.entryId, entry.name) },
        onRetry = { viewModel.load() },
    )
}
```

- [ ] **Step 3: Pass `onEntryClick` through `TvScheduleDayRow`**

`TvScheduleDayRow` currently calls `TvMediaDetailActivity.navigateTo` directly via `LocalContext`. After extraction, `TvScheduleScreenContent` receives `onEntryClick` as a parameter and passes it to `TvScheduleDayRow`. Update `TvScheduleDayRow` signature:

```kotlin
@Composable
private fun TvScheduleDayRow(
    day: CalendarDay,
    entries: List<CalendarEntry>,
    onEntryClick: (CalendarEntry) -> Unit,
) {
    // Remove: val context = LocalContext.current
    // Replace: TvMediaDetailActivity.navigateTo(context, entry.entryId, entry.name)
    //      with: onEntryClick(entry)
```

Update the call site in `TvScheduleScreenContent` to pass `onEntryClick`:
```kotlin
TvScheduleDayRow(day = day, entries = dayEntries, onEntryClick = onEntryClick)
```

- [ ] **Step 4: Add `@Preview` functions**

Add imports:
```kotlin
import androidx.compose.ui.tooling.preview.Preview
import me.proxer.app.R
import me.proxer.app.util.ErrorUtils
```

```kotlin
@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun TvScheduleScreenContentPreview() {
    TvTheme {
        TvScheduleScreenContent(
            schedule = mapOf(
                CalendarDay.MONDAY to listOf(fakeCalendarEntry(id = "1", day = CalendarDay.MONDAY)),
                CalendarDay.WEDNESDAY to listOf(
                    fakeCalendarEntry(id = "2", day = CalendarDay.WEDNESDAY, name = "Demon Slayer"),
                    fakeCalendarEntry(id = "3", day = CalendarDay.WEDNESDAY, name = "One Piece"),
                ),
            ),
            isLoading = false,
            error = null,
            onEntryClick = {},
            onRetry = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
private fun TvScheduleDayRowPreview() {
    TvTheme {
        TvScheduleDayRow(
            day = CalendarDay.MONDAY,
            entries = listOf(
                fakeCalendarEntry(id = "1"),
                fakeCalendarEntry(id = "2", name = "Demon Slayer"),
            ),
            onEntryClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TvScheduleCardPreview() {
    TvTheme {
        TvScheduleCard(
            entry = fakeCalendarEntry(),
            onClick = {},
        )
    }
}
```

- [ ] **Step 5: Verify + commit**

```bash
./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"
git add src/main/kotlin/me/proxer/app/tv/TvScheduleScreen.kt
git commit -m "feat(tv/preview): extract TvScheduleScreenContent and add @Preview"
```

---

### Task 6: Extract TvLoginScreenContent and add previews

**Goal:** Split `TvLoginScreen` into a stateless content composable and a ViewModel wrapper; add previews for idle, loading, and 2FA states.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/tv/auth/TvLoginScreen.kt`

**Acceptance Criteria:**
- [ ] `TvLoginScreenContent(username, password, secretKey, isLoading, isTwoFa, error, onUsernameChange, onPasswordChange, onSecretKeyChange, onLogin)` exists
- [ ] `TvLoginScreen` is a thin wrapper; public signature `fun TvLoginScreen(onLoginSuccess: () -> Unit)` unchanged
- [ ] Three `@Preview` functions: idle, loading, 2FA visible
- [ ] Compiles without errors

**Verify:** `./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add `TvLoginScreenContent` above `TvLoginScreen`**

```kotlin
@Composable
fun TvLoginScreenContent(
    username: String,
    password: String,
    secretKey: String,
    isLoading: Boolean,
    isTwoFa: Boolean,
    error: ErrorUtils.ErrorAction?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSecretKeyChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    // Move the entire Box { Column { ... } } body from TvLoginScreen here.
    // The state variables (username, password, secretKey) are now parameters instead of
    // remember { mutableStateOf("") }. The OutlinedTextFields use these params directly.
}
```

- [ ] **Step 2: Replace `TvLoginScreen` body with a thin wrapper**

```kotlin
@Composable
fun TvLoginScreen(onLoginSuccess: () -> Unit) {
    val viewModel: LoginViewModel = koinViewModel()
    val success by viewModel.success.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()
    val isTwoFa by viewModel.isTwoFactorAuthenticationEnabled.observeAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }

    LaunchedEffect(success) {
        if (success != null) onLoginSuccess()
    }

    TvLoginScreenContent(
        username = username,
        password = password,
        secretKey = secretKey,
        isLoading = isLoading == true,
        isTwoFa = isTwoFa == true,
        error = error,
        onUsernameChange = { username = it },
        onPasswordChange = { password = it },
        onSecretKeyChange = { secretKey = it },
        onLogin = {
            viewModel.login(
                username,
                password,
                if (isTwoFa == true) secretKey.takeIf { it.isNotBlank() } else null,
            )
        },
    )
}
```

- [ ] **Step 3: Add `@Preview` functions**

Add imports:
```kotlin
import androidx.compose.ui.tooling.preview.Preview
import me.proxer.app.R
import me.proxer.app.tv.TvTheme
import me.proxer.app.util.ErrorUtils
```

```kotlin
@Preview(device = "id:tv_1080p", showBackground = true, name = "Idle")
@Composable
private fun TvLoginScreenContentIdlePreview() {
    TvTheme {
        TvLoginScreenContent(
            username = "",
            password = "",
            secretKey = "",
            isLoading = false,
            isTwoFa = false,
            error = null,
            onUsernameChange = {},
            onPasswordChange = {},
            onSecretKeyChange = {},
            onLogin = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Loading")
@Composable
private fun TvLoginScreenContentLoadingPreview() {
    TvTheme {
        TvLoginScreenContent(
            username = "Asteria",
            password = "••••••••",
            secretKey = "",
            isLoading = true,
            isTwoFa = false,
            error = null,
            onUsernameChange = {},
            onPasswordChange = {},
            onSecretKeyChange = {},
            onLogin = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "2FA")
@Composable
private fun TvLoginScreenContent2FaPreview() {
    TvTheme {
        TvLoginScreenContent(
            username = "Asteria",
            password = "••••••••",
            secretKey = "",
            isLoading = false,
            isTwoFa = true,
            error = null,
            onUsernameChange = {},
            onPasswordChange = {},
            onSecretKeyChange = {},
            onLogin = {},
        )
    }
}
```

- [ ] **Step 4: Verify + commit**

```bash
./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"
git add src/main/kotlin/me/proxer/app/tv/auth/TvLoginScreen.kt
git commit -m "feat(tv/preview): extract TvLoginScreenContent and add @Preview"
```

---

### Task 7: Extract TvMediaDetailScreenContent and add previews

**Goal:** Split `TvMediaDetailScreen` into a stateless content composable (using flat display-field parameters rather than the full `Entry` object) and a ViewModel wrapper; add previews for loading and populated states.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/tv/detail/TvMediaDetailScreen.kt`

**Acceptance Criteria:**
- [ ] `TvMediaDetailScreenContent(entryId, entryName, description, rating, episodeAmount, isLoading, error, onWatchEpisodes, onBack, onRetry, onAgeConfirmed)` exists
- [ ] `TvMediaDetailScreen` is a thin wrapper; public signature unchanged
- [ ] Two `@Preview` functions: loading state and populated state
- [ ] Compiles without errors

**Verify:** `./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add `TvMediaDetailScreenContent` above `TvMediaDetailScreen`**

```kotlin
@Composable
fun TvMediaDetailScreenContent(
    entryId: String,
    entryName: String,
    description: String,
    rating: Float,
    episodeAmount: Int,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    onWatchEpisodes: (episodeAmount: Int) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAgeConfirmed: () -> Unit = {},
) {
    // Move the entire Row { AsyncImage; Column { ... } } body from TvMediaDetailScreen here.
    // Replace `e.name` with `entryName`, `e.rating` with `rating`,
    // `e.episodeAmount` with `episodeAmount`, `e.description` with `description`.
    // The loading / error / else when-block now uses isLoading, error, and a non-null
    // check on `episodeAmount > 0 || description.isNotBlank()` — or just show data
    // when error == null && !isLoading.
    // Pass onAgeConfirmed to TvErrorView.
}
```

The content composable shows data when `error == null && !isLoading`. Replace the `entry?.let { e -> ... }` block with direct parameter references. The outer `when` block structure stays the same:

```kotlin
when {
    isLoading && episodeAmount == 0 && description.isBlank() -> { CircularProgressIndicator() }
    error != null -> { TvErrorView(error = error, onRetryClick = onRetry, onAgeConfirmed = onAgeConfirmed) }
    else -> {
        Text(entryName, ...)
        Text("Rating: ${"%.1f".format(rating.toDouble())}/10", ...)
        Text("Episodes: $episodeAmount", ...)
        if (description.isNotBlank()) { ... }
        Button(onClick = { onWatchEpisodes(episodeAmount) }) { Text("Watch Episodes") }
    }
}
```

- [ ] **Step 2: Replace `TvMediaDetailScreen` body with a thin wrapper**

```kotlin
@Composable
fun TvMediaDetailScreen(
    entryId: String,
    entryName: String,
    onWatchEpisodes: (episodeAmount: Int) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: MediaInfoViewModel = koinViewModel { parametersOf(entryId) }
    val preferenceHelper: PreferenceHelper = koinInject()
    val entry by viewModel.data.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    TvMediaDetailScreenContent(
        entryId = entryId,
        entryName = entryName,
        description = entry?.description.orEmpty(),
        rating = entry?.rating ?: 0f,
        episodeAmount = entry?.episodeAmount ?: 0,
        isLoading = isLoading ?: false,
        error = error,
        onWatchEpisodes = onWatchEpisodes,
        onBack = onBack,
        onRetry = { viewModel.load() },
        onAgeConfirmed = { preferenceHelper.isAgeRestrictedMediaAllowed = true },
    )
}
```

Add imports if not present:
```kotlin
import me.proxer.app.util.data.PreferenceHelper
import org.koin.compose.koinInject
```

- [ ] **Step 3: Add `@Preview` functions**

Add imports:
```kotlin
import androidx.compose.ui.tooling.preview.Preview
import me.proxer.app.R
import me.proxer.app.tv.TvTheme
import me.proxer.app.tv.fakeMediaListEntry
import me.proxer.app.util.ErrorUtils
```

```kotlin
@Preview(device = "id:tv_1080p", showBackground = true, name = "Loading")
@Composable
private fun TvMediaDetailScreenContentLoadingPreview() {
    TvTheme {
        TvMediaDetailScreenContent(
            entryId = "1",
            entryName = "Attack on Titan",
            description = "",
            rating = 0f,
            episodeAmount = 0,
            isLoading = true,
            error = null,
            onWatchEpisodes = {},
            onBack = {},
            onRetry = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Populated")
@Composable
private fun TvMediaDetailScreenContentPopulatedPreview() {
    TvTheme {
        TvMediaDetailScreenContent(
            entryId = "1",
            entryName = "Attack on Titan",
            description = "A long time ago, humanity was driven to the brink of extinction by giant humanoid creatures known as Titans.",
            rating = 8.9f,
            episodeAmount = 25,
            isLoading = false,
            error = null,
            onWatchEpisodes = {},
            onBack = {},
            onRetry = {},
        )
    }
}
```

- [ ] **Step 4: Verify + commit**

```bash
./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"
git add src/main/kotlin/me/proxer/app/tv/detail/TvMediaDetailScreen.kt
git commit -m "feat(tv/preview): extract TvMediaDetailScreenContent and add @Preview"
```

---

### Task 8: Extract TvEpisodeScreenContent and add previews

**Goal:** Split `TvEpisodeScreen` into a stateless content composable and a ViewModel wrapper; add previews for the screen content and `TvEpisodeItem` (watched and unwatched).

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/tv/episode/TvEpisodeScreen.kt`

**Acceptance Criteria:**
- [ ] `TvEpisodeScreenContent(entryName, episodes, isLoading, error, onEpisodeClick, onBack, onRetry, onAgeConfirmed)` exists
- [ ] `TvEpisodeScreen` is a thin wrapper; public signature unchanged
- [ ] Four `@Preview` functions: content (list), content (loading), `TvEpisodeItem` unwatched, `TvEpisodeItem` watched
- [ ] Compiles without errors (note: `import androidx.compose.ui.tooling.preview.Preview` already present)

**Verify:** `./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add `TvEpisodeScreenContent` above `TvEpisodeScreen`**

```kotlin
@Composable
fun TvEpisodeScreenContent(
    entryName: String,
    episodes: List<EpisodeRow>?,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    onEpisodeClick: (episode: Int, language: AnimeLanguage) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAgeConfirmed: () -> Unit = {},
) {
    // Move the entire Column { Row { ... }; when { ... } } body from TvEpisodeScreen here.
    // Replace viewModel.reload() → onRetry()
    // Pass onAgeConfirmed to TvErrorView.
}
```

- [ ] **Step 2: Replace `TvEpisodeScreen` body with a thin wrapper**

```kotlin
@Composable
fun TvEpisodeScreen(
    entryId: String,
    entryName: String,
    onEpisodeClick: (episode: Int, language: AnimeLanguage) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: EpisodeViewModel = koinViewModel { parametersOf(entryId) }
    val preferenceHelper: PreferenceHelper = koinInject()
    val episodes by viewModel.data.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    TvEpisodeScreenContent(
        entryName = entryName,
        episodes = episodes,
        isLoading = isLoading ?: false,
        error = error,
        onEpisodeClick = onEpisodeClick,
        onBack = onBack,
        onRetry = { viewModel.reload() },
        onAgeConfirmed = { preferenceHelper.isAgeRestrictedMediaAllowed = true },
    )
}
```

Add imports if not present:
```kotlin
import me.proxer.app.util.data.PreferenceHelper
import org.koin.compose.koinInject
```

- [ ] **Step 3: Add `@Preview` functions** (the `@Preview` import already exists in this file)

Add imports:
```kotlin
import me.proxer.app.R
import me.proxer.app.tv.TvTheme
import me.proxer.app.tv.fakeEpisodeRow
import me.proxer.app.util.ErrorUtils
```

```kotlin
@Preview(device = "id:tv_1080p", showBackground = true, name = "Episode list")
@Composable
private fun TvEpisodeScreenContentPreview() {
    TvTheme {
        TvEpisodeScreenContent(
            entryName = "Attack on Titan",
            episodes = (1..10).map { fakeEpisodeRow(number = it, watched = it <= 3) },
            isLoading = false,
            error = null,
            onEpisodeClick = { _, _ -> },
            onBack = {},
            onRetry = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Loading")
@Composable
private fun TvEpisodeScreenContentLoadingPreview() {
    TvTheme {
        TvEpisodeScreenContent(
            entryName = "Attack on Titan",
            episodes = emptyList(),
            isLoading = true,
            error = null,
            onEpisodeClick = { _, _ -> },
            onBack = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, name = "Unwatched")
@Composable
private fun TvEpisodeItemUnwatchedPreview() {
    TvTheme { TvEpisodeItem(episodeRow = fakeEpisodeRow(number = 1, watched = false), onClick = {}) }
}

@Preview(showBackground = true, name = "Watched")
@Composable
private fun TvEpisodeItemWatchedPreview() {
    TvTheme { TvEpisodeItem(episodeRow = fakeEpisodeRow(number = 1, watched = true), onClick = {}) }
}
```

- [ ] **Step 4: Verify + commit**

```bash
./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"
git add src/main/kotlin/me/proxer/app/tv/episode/TvEpisodeScreen.kt
git commit -m "feat(tv/preview): extract TvEpisodeScreenContent and add @Preview"
```

---

### Task 9: Extract TvStreamScreenContent and add previews

**Goal:** Split `TvStreamScreen` into a stateless content composable and a ViewModel wrapper; add previews for the content, `TvBadge`, and `TvStreamItem`.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/tv/stream/TvStreamScreen.kt`

**Acceptance Criteria:**
- [ ] `TvStreamScreenContent(entryName, episode, language, streams, isLoading, hasError, showResolutionError, resolvingStreamId, onStreamClick, onBack, onRetry)` exists (`hasError: Boolean` replaces the `ErrorAction?` — the stream screen shows a static "Failed to load streams" string, not the ErrorAction message)
- [ ] `TvStreamScreen` is a thin wrapper; public signature unchanged
- [ ] Five `@Preview` functions: content (stream list), content (loading), `TvBadge`, `TvStreamItem` (supported), `TvStreamItem` (resolving)
- [ ] Compiles without errors

**Verify:** `./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add `TvStreamScreenContent` above `TvStreamScreen`**

```kotlin
@Composable
fun TvStreamScreenContent(
    entryName: String,
    episode: Int,
    language: AnimeLanguage,
    streams: List<AnimeStream>,
    isLoading: Boolean,
    hasError: Boolean,
    showResolutionError: Boolean,
    resolvingStreamId: String?,
    onStreamClick: (AnimeStream) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    // Move the entire Column { Row { ... }; if (showResolutionError) { ... }; when { ... } }
    // body from TvStreamScreen here.
    // Replace viewModel.reload() → onRetry()
    // Replace viewModel.resolve(stream) → onStreamClick(stream)
    // `error` is removed — stream errors are shown inline (existing pattern), not via TvErrorView.
}
```

- [ ] **Step 2: Replace `TvStreamScreen` body with a thin wrapper**

```kotlin
@Composable
fun TvStreamScreen(
    entryId: String,
    episode: Int,
    language: AnimeLanguage,
    entryName: String,
    onBack: () -> Unit,
) {
    val viewModel: AnimeViewModel = koinViewModel { parametersOf(entryId, language, episode) }
    val streamInfo by viewModel.data.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val resolutionResult by viewModel.resolutionResult.observeAsState()
    val resolutionError by viewModel.resolutionError.observeAsState()
    val context = LocalContext.current
    var resolvingStreamId by remember { mutableStateOf<String?>(null) }
    var showResolutionError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadIfPossible() }

    LaunchedEffect(resolutionError) {
        if (resolutionError != null) {
            showResolutionError = true
            resolvingStreamId = null
        }
    }

    LaunchedEffect(resolutionResult) {
        when (val result = resolutionResult) {
            is StreamResolutionResult.Video -> {
                result.play(context, entryId, entryName, episode, language, ProxerUrls.entryImage(entryId).androidUri(), true)
            }
            is StreamResolutionResult.Link -> {
                try { context.startActivity(result.makeIntent()) }
                catch (e: Exception) { context.toast("No app found to open this link", Toast.LENGTH_SHORT) }
            }
            is StreamResolutionResult.App -> {
                try { result.navigate(context) }
                catch (e: Exception) { context.toast("No app found to handle this stream", Toast.LENGTH_SHORT) }
            }
            is StreamResolutionResult.Message -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
            null -> Unit
        }
        if (resolutionResult != null) {
            showResolutionError = false
            resolvingStreamId = null
        }
    }

    TvStreamScreenContent(
        entryName = entryName,
        episode = episode,
        language = language,
        streams = streamInfo?.streams ?: emptyList(),
        isLoading = isLoading ?: false,
        hasError = error != null,
        showResolutionError = showResolutionError,
        resolvingStreamId = resolvingStreamId,
        onStreamClick = { stream ->
            resolvingStreamId = stream.id
            viewModel.resolve(stream)
        },
        onBack = onBack,
        onRetry = { viewModel.reload() },
    )
}
```

- [ ] **Step 3: Add `@Preview` functions**

Add imports:
```kotlin
import androidx.compose.ui.tooling.preview.Preview
import me.proxer.app.tv.TvTheme
import me.proxer.app.tv.fakeAnimeStream
import me.proxer.library.enums.AnimeLanguage
```

```kotlin
@Preview(device = "id:tv_1080p", showBackground = true, name = "Stream list")
@Composable
private fun TvStreamScreenContentPreview() {
    TvTheme {
        TvStreamScreenContent(
            entryName = "Attack on Titan",
            episode = 1,
            language = AnimeLanguage.ENGLISH_SUB,
            streams = listOf(
                fakeAnimeStream(id = "1", hosterName = "Vidoza", isOfficial = true),
                fakeAnimeStream(id = "2", hosterName = "Streamtape", isSupported = false),
            ),
            isLoading = false,
            showResolutionError = false,
            resolvingStreamId = null,
            onStreamClick = {},
            onBack = {},
            onRetry = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Loading")
@Composable
private fun TvStreamScreenContentLoadingPreview() {
    TvTheme {
        TvStreamScreenContent(
            entryName = "Attack on Titan",
            episode = 1,
            language = AnimeLanguage.ENGLISH_SUB,
            streams = emptyList(),
            isLoading = true,
            showResolutionError = false,
            resolvingStreamId = null,
            onStreamClick = {},
            onBack = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, name = "Official badge")
@Composable
private fun TvBadgePreview() {
    TvTheme { TvBadge(label = "Official", background = Color(0xFF1B5E20)) }
}

@Preview(showBackground = true, name = "Stream item")
@Composable
private fun TvStreamItemPreview() {
    TvTheme {
        TvStreamItem(
            stream = fakeAnimeStream(isOfficial = true),
            isResolving = false,
            onClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Stream item resolving")
@Composable
private fun TvStreamItemResolvingPreview() {
    TvTheme {
        TvStreamItem(
            stream = fakeAnimeStream(isOfficial = false, isSupported = false),
            isResolving = true,
            onClick = {},
        )
    }
}
```

- [ ] **Step 4: Verify + commit**

```bash
./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"
git add src/main/kotlin/me/proxer/app/tv/stream/TvStreamScreen.kt
git commit -m "feat(tv/preview): extract TvStreamScreenContent and add @Preview"
```

---

### Task 10: Extract TvSearchScreenContent and add previews

**Goal:** Split `TvSearchScreen` into a stateless content composable (query state lifted to wrapper) and a ViewModel wrapper; add previews for content and `TvSearchResultCard`.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/tv/search/TvSearchScreen.kt`

**Acceptance Criteria:**
- [ ] `TvSearchScreenContent(query, onQueryChange, entries, isLoading, error, onMediaClick, onRetry)` exists
- [ ] `TvSearchScreen` is a thin wrapper; public signature `fun TvSearchScreen(onMediaClick: (String, String) -> Unit)` unchanged
- [ ] Debounce `LaunchedEffect(query)` lives in the stateful wrapper (not in the content composable)
- [ ] Three `@Preview` functions: content (results), content (loading), `TvSearchResultCard`
- [ ] Compiles without errors

**Verify:** `./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Add `TvSearchScreenContent` above `TvSearchScreen`**

```kotlin
@Composable
fun TvSearchScreenContent(
    query: String,
    onQueryChange: (String) -> Unit,
    entries: List<MediaListEntry>?,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    onMediaClick: (String, String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    // Move the Column { Row { OutlinedTextField ... }; if (error != null) { ... } else { LazyVerticalGrid ... } }
    // body here.
    // The OutlinedTextField's onValueChange calls onQueryChange(it).
    // Replace viewModel.reload() → onRetry()
    // Replace viewModel.loadIfPossible() → onLoadMore()
    // The gridState and shouldLoadMore derivedState stay here (local UI state).
    // LaunchedEffect(shouldLoadMore) stays here, calling onLoadMore().
}
```

- [ ] **Step 2: Replace `TvSearchScreen` body with a thin wrapper**

```kotlin
@Composable
fun TvSearchScreen(onMediaClick: (String, String) -> Unit) {
    val viewModel: MediaListViewModel =
        koinViewModel {
            parametersOf(
                MediaSearchSortCriteria.RATING,
                MediaType.ANIMESERIES,
                null as String?,
                null as Language?,
                emptyList<LocalTag>(),
                emptyList<LocalTag>(),
                enumSetOf<FskConstraint>(),
                emptyList<LocalTag>(),
                emptyList<LocalTag>(),
                null as TagRateFilter?,
                null as TagSpoilerFilter?,
                null as Boolean?,
            )
        }

    var query by remember { mutableStateOf("") }
    val entries by viewModel.data.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    LaunchedEffect(query) {
        delay(500)
        viewModel.searchQuery = query.takeIf { it.isNotBlank() }
        viewModel.reload()
    }

    TvSearchScreenContent(
        query = query,
        onQueryChange = { query = it },
        entries = entries,
        isLoading = isLoading ?: false,
        error = error,
        onMediaClick = onMediaClick,
        onRetry = { viewModel.reload() },
        onLoadMore = { viewModel.loadIfPossible() },
    )
}
```

- [ ] **Step 3: Add `@Preview` functions**

Add imports:
```kotlin
import androidx.compose.ui.tooling.preview.Preview
import me.proxer.app.R
import me.proxer.app.tv.TvTheme
import me.proxer.app.tv.fakeMediaListEntry
import me.proxer.app.util.ErrorUtils
```

```kotlin
@Preview(device = "id:tv_1080p", showBackground = true, name = "With results")
@Composable
private fun TvSearchScreenContentResultsPreview() {
    TvTheme {
        TvSearchScreenContent(
            query = "attack",
            onQueryChange = {},
            entries = listOf(
                fakeMediaListEntry(id = "1", name = "Attack on Titan"),
                fakeMediaListEntry(id = "2", name = "Attack on Titan: Final Season"),
                fakeMediaListEntry(id = "3", name = "A-Channel"),
            ),
            isLoading = false,
            error = null,
            onMediaClick = { _, _ -> },
            onRetry = {},
            onLoadMore = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, name = "Loading")
@Composable
private fun TvSearchScreenContentLoadingPreview() {
    TvTheme {
        TvSearchScreenContent(
            query = "",
            onQueryChange = {},
            entries = emptyList(),
            isLoading = true,
            error = null,
            onMediaClick = { _, _ -> },
            onRetry = {},
            onLoadMore = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TvSearchResultCardPreview() {
    TvTheme {
        TvSearchResultCard(
            entry = fakeMediaListEntry(),
            onClick = {},
        )
    }
}
```

Note: `TvSearchResultCard` is `private` — the preview function is in the same file so it can call it.

- [ ] **Step 4: Verify + commit**

```bash
./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "error:|BUILD"
git add src/main/kotlin/me/proxer/app/tv/search/TvSearchScreen.kt
git commit -m "feat(tv/preview): extract TvSearchScreenContent and add @Preview"
```

---

### Task 11: Final build verification

**Goal:** Confirm all changes compile cleanly and no regressions were introduced.

**Files:**
- None (verification only)

**Acceptance Criteria:**
- [ ] `./gradlew compileDebugKotlin` exits with `BUILD SUCCESSFUL`
- [ ] No new `error:` lines compared to baseline (pre-existing `w:` deprecation warnings are expected and acceptable)

**Verify:** `./gradlew compileDebugKotlin --no-daemon --rerun-tasks 2>&1 | grep -c "error:"` → `0`

**Steps:**

- [ ] **Step 1: Run full compilation**

```bash
./gradlew compileDebugKotlin --no-daemon --rerun-tasks 2>&1 | tail -20
```

Expected output includes `BUILD SUCCESSFUL`. If any `error:` lines appear, fix them before marking this task complete.

- [ ] **Step 2: Commit (if any last-minute fixes were needed)**

```bash
git add -p   # stage only the fix
git commit -m "fix(tv/preview): resolve compilation errors from preview extraction"
```
