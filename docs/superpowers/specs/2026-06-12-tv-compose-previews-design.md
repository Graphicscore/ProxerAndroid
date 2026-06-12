# TV Compose Previews — Design Spec

**Date:** 2026-06-12  
**Scope:** All composable files under `me.proxer.app.tv`

---

## Goal

Add `@Preview` annotations to every TV Compose screen and sub-composable so they render in Android Studio's Layout Inspector without a running device.

---

## Approach

State-hoisting extraction (Approach B). Each VM-driven screen is split into:

- **Content composable** — stateless, takes plain data + callbacks, fully previewable
- **Stateful wrapper** — unchanged public API, observes ViewModel, delegates to content composable

Leaf composables (cards, items) are previewed via `@Preview` functions in the same file (private visibility is file-local, so they are accessible).

---

## `TvErrorView` Refactor

Remove `koinInject<PreferenceHelper>()`. Replace with:

```kotlin
onAgeConfirmed: () -> Unit = {}
```

Inside the age-confirmation dialog, call `onAgeConfirmed()` instead of setting the preference directly.  
Callers in stateful wrappers pass: `onAgeConfirmed = { preferenceHelper.isAgeRestrictedMediaAllowed = true }`.  
The ViewModel auto-reloads via `isAgeRestrictedMediaAllowedObservable` — no additional wiring needed.

---

## NavigationDrawer Preview

`TvNavigationDrawerContent` is a `NavigationDrawerScope` extension and cannot be called outside that scope. Preview via:

```kotlin
NavigationDrawer(drawerContent = { drawerValue ->
    TvNavigationDrawerContent(currentSection = TvSection.ANIME, user = null, drawerValue = drawerValue, ...)
}) {}
```

Two preview variants: logged-out (`user = null`) and logged-in (`user = LocalUser("Asteria", "")`).

---

## Shared Fake Data

New file: `me/proxer/app/tv/TvPreviewData.kt`

`internal fun` helpers (not annotated — preview-only by convention):

| Helper | Type |
|---|---|
| `fakeMediaListEntry()` | `MediaListEntry` |
| `fakeBookmark()` | `Bookmark` |
| `fakeCalendarEntry(day)` | `CalendarEntry` |
| `fakeEpisodeRow(number, watched)` | `EpisodeRow` |
| `fakeAnimeStream(official, supported)` | `AnimeStream` |

---

## Files Changed

### New files
- `tv/TvPreviewData.kt` — shared fake data helpers

### Modified files

| File | Content Composable Extracted | Previews Added |
|---|---|---|
| `TvErrorView.kt` | no (already stateless-ish, param change only) | `TvErrorView` (retry state), `TvErrorView` (login state) |
| `TvPlaceholderScreen.kt` | no (already stateless) | `TvPlaceholderScreen` |
| `TvNavigationDrawer.kt` | no | logged-out, logged-in |
| `TvBrowseScreen.kt` | `TvBrowseScreenContent` | content (empty), content (loading), `TvMediaCard` |
| `TvBookmarksScreen.kt` | `TvBookmarksScreenContent` | content (empty), `TvBookmarkCard` |
| `TvScheduleScreen.kt` | `TvScheduleScreenContent` | content, `TvScheduleDayRow`, `TvScheduleCard` |
| `TvLoginScreen.kt` | `TvLoginScreenContent` | idle, loading, 2FA visible |
| `TvMediaDetailScreen.kt` | `TvMediaDetailScreenContent` | loading, populated |
| `TvEpisodeScreen.kt` | `TvEpisodeScreenContent` | content (list), `TvEpisodeItem` (unwatched), `TvEpisodeItem` (watched) |
| `TvStreamScreen.kt` | `TvStreamScreenContent` | content (streams), `TvBadge`, `TvStreamItem` (official), `TvStreamItem` (resolving) |
| `search/TvSearchScreen.kt` | `TvSearchScreenContent` | content (results), `TvSearchResultCard` |
| `TvAppShell.kt` | no change | none (sub-screens covered) |

---

## Content Composable Signatures

### `TvBrowseScreenContent`
```kotlin
fun TvBrowseScreenContent(
    entries: List<MediaListEntry>?,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    onMediaClick: (String, String) -> Unit,
    onSearchClick: () -> Unit,
    onRetry: () -> Unit,
    onAgeConfirmed: () -> Unit = {},
    onLoginClick: () -> Unit = {},
)
```

### `TvSearchScreenContent`
```kotlin
fun TvSearchScreenContent(
    query: String,
    onQueryChange: (String) -> Unit,
    entries: List<MediaListEntry>?,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    onMediaClick: (String, String) -> Unit,
    onRetry: () -> Unit,
)
```

### `TvBookmarksScreenContent`
```kotlin
fun TvBookmarksScreenContent(
    entries: List<Bookmark>?,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    onBookmarkClick: (Bookmark) -> Unit,
    onRetry: () -> Unit,
    onLoginClick: () -> Unit = {},
    onAgeConfirmed: () -> Unit = {},
)
```

### `TvScheduleScreenContent`
```kotlin
fun TvScheduleScreenContent(
    schedule: Map<CalendarDay, List<CalendarEntry>>?,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    onEntryClick: (CalendarEntry) -> Unit,
    onRetry: () -> Unit,
)
```

### `TvLoginScreenContent`
```kotlin
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
)
```

### `TvMediaDetailScreenContent`
```kotlin
fun TvMediaDetailScreenContent(
    entryId: String,
    entryName: String,
    entry: me.proxer.library.entity.info.Entry?,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    onWatchEpisodes: (Int) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
)
```

### `TvEpisodeScreenContent`
```kotlin
fun TvEpisodeScreenContent(
    entryName: String,
    episodes: List<EpisodeRow>?,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    onEpisodeClick: (Int, AnimeLanguage) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
)
```

### `TvStreamScreenContent`
```kotlin
fun TvStreamScreenContent(
    entryName: String,
    episode: Int,
    language: AnimeLanguage,
    streams: List<AnimeStream>,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    showResolutionError: Boolean,
    resolvingStreamId: String?,
    onStreamClick: (AnimeStream) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
)
```

---

## Preview Device

All previews use `@Preview(device = "id:tv_1080p", showBackground = true)` for a 1080p TV aspect ratio.  
Card/item previews use the default device (phone) since they don't depend on TV dimensions.

---

## Out of Scope

- `TvAppShell.kt` — navigation shell; no content extraction, no preview
- `TvTheme.kt` — the preview wrapper itself, not previewable
- `TvSection.kt`, `TvShellViewModel.kt` — no composables
- Activity files — not composables
