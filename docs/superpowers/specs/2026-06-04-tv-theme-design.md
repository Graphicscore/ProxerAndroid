# TV Frontend: Theme, Bookmarks, Schedule, Surface Migration

**Date:** 2026-06-04
**Branch:** tv-menu

## Goals

1. Apply the phone UI color scheme to the TV frontend via a proper Compose TV Material3 theme.
2. Implement functional Bookmarks and Schedule screens for TV.
3. Migrate all clickable TV cards from `material3.Card` to `tv.material3.Surface` for TV focus/glow/scale behavior.

---

## Section 1: TvTheme

### New file: `me/proxer/app/tv/TvTheme.kt`

A `@Composable` function wrapping `androidx.tv.material3.MaterialTheme` with a `darkColorScheme`. Brand colors are read from the existing `colors.xml` via `colorResource()` so a single change in `colors.xml` propagates to both phone and TV. TV-specific dark background/surface values are hardcoded here since the phone uses a light background.

```kotlin
@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(  // androidx.tv.material3.MaterialTheme
        colorScheme = darkColorScheme(
            primary      = colorResource(R.color.primary),       // #8A0E0E
            onPrimary    = colorResource(R.color.on_primary),    // #FFFFFF
            secondary    = colorResource(R.color.primary_light), // #C14535
            background   = Color(0xFF121212),
            onBackground = Color(0xFFE8E8E8),
            surface      = Color(0xFF1E1E1E),
            onSurface    = Color(0xFFE8E8E8),
            error        = Color(0xFFCF6679),
        ),
        content = content
    )
}
```

### Activity wiring

All TV activities replace `MaterialTheme { }` with `TvTheme { }`:
- `TvMainActivity`
- `TvLoginActivity`
- `TvSearchActivity`
- `TvMediaDetailActivity`
- `TvEpisodeActivity`
- `TvStreamActivity`

### Hardcoded color removal

All screens replace hardcoded color literals with theme tokens:

| Literal | Replacement |
|---|---|
| `Color.Black`, `Color(0xFF0D0D0D)` | `MaterialTheme.colorScheme.background` |
| `Color(0xFF1A1A1A)` | `MaterialTheme.colorScheme.surface` |
| `Color.White` on `CircularProgressIndicator` | drop — inherits primary |
| `Color.White` on `Text` | `MaterialTheme.colorScheme.onBackground` or `onSurface` |
| `Color.Gray` on secondary `Text` | `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)` |

`TvNavigationDrawer.kt` hardcoded colors (`Color(0xFF3B82F6)` blue tint on icon, `Color(0xFFEF4444)` red on sign-out) stay — they are semantic UI indicators, not background/surface colors.

---

## Section 2: TvBookmarksScreen

### New file: `me/proxer/app/tv/TvBookmarksScreen.kt`

**ViewModel:** `BookmarkViewModel` via `koinViewModel { parametersOf(null, Category.ANIME, false) }`
- `null` search query, `Category.ANIME` (anime-only — no TV manga reader), `filterAvailable = false`
- `isLoginRequired = true` (VM default) — unauthenticated users see `TvErrorView` with login button

**Layout:** 5-column `LazyVerticalGrid` matching `TvBrowseScreen`:
- Same `shouldLoadMore` / `loadIfPossible()` infinite-scroll pattern
- `LaunchedEffect(Unit) { viewModel.load() }` to trigger initial load

**Card:** `tv.material3.Surface(onClick = ...)` — same dimensions as `TvMediaCard` (180×270 dp):
- Top: cover image (`AsyncImage`, `ContentScale.Crop`, fills weight)
- Bottom bar: title (2-line max, ellipsis) + episode badge (e.g. "Ep 12 • Sub")
- Background on bottom bar: `MaterialTheme.colorScheme.surface`

**Click action:** `TvEpisodeActivity.navigateTo(context, bookmark.entryId, bookmark.name)`

**Loading/error:** same pattern as `TvBrowseScreen` — `CircularProgressIndicator` centered on initial load, `TvErrorView` on error.

### Wire in TvAppShell

```kotlin
TvSection.BOOKMARKS -> TvBookmarksScreen(
    onEpisodeClick = { id, name -> TvEpisodeActivity.navigateTo(context, id, name) }
)
```

---

## Section 3: TvScheduleScreen

### New file: `me/proxer/app/tv/TvScheduleScreen.kt`

**ViewModel:** `ScheduleViewModel` via `koinViewModel()`
- `ScheduleViewModel.isLoginRequired` must be overridden to `false` (schedule is public data; the base VM default is `true` which would block unauthenticated users unnecessarily)
- Override in `ScheduleViewModel`: `override val isLoginRequired = false`
- `LaunchedEffect(Unit) { viewModel.load() }`

**Layout:** Netflix-style — vertical `LazyColumn` of day rows, each row contains:
1. Day header: `Text(day.toAppString(context))` styled with `MaterialTheme.colorScheme.primary`
2. Horizontal `LazyRow` of show cards

**Show card:** `tv.material3.Surface` (~160×220 dp):
- Cover image (fills weight)
- Title (1-line, ellipsis)
- Air time: `DateFormat.getTimeInstance(DateFormat.SHORT).format(entry.date)` (e.g. "20:30")

**Click action:** `TvMediaDetailActivity.navigateTo(context, entry.entryId, entry.name, null)`

**Day order:** `CalendarDay` enum order (Mon → Sun as returned by the API grouping).

**Loading/error:** same pattern — centered spinner, `TvErrorView` on error.

### Wire in TvAppShell

```kotlin
TvSection.SCHEDULE -> TvScheduleScreen()
```

---

## Section 4: Surface Migration

Replace `material3.Card` with `tv.material3.Surface` in three files. `tv.material3.Surface` provides D-pad focus indication (scale + glow) that `material3.Card` lacks on TV.

### `TvSearchScreen.TvSearchResultCard`

```kotlin
// Before
Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) { ... }

// After
Surface(onClick = onClick) { ... }  // androidx.tv.material3.Surface
```

Remove `material3.Card` and `CardDefaults` imports. Container color from theme.

### `TvEpisodeScreen.TvEpisodeItem`

```kotlin
// Before
Card(onClick = onClick, colors = CardDefaults.cardColors(
    containerColor = if (isWatched) Color(0xFF1A2A1A) else Color(0xFF1A1A1A)
)) { ... }

// After
Surface(
    onClick = onClick,
    colors = ClickableSurfaceDefaults.colors(
        containerColor = if (isWatched)
            MaterialTheme.colorScheme.surface.copy(green = 0.18f)  // subtle watched tint
        else
            MaterialTheme.colorScheme.surface
    )
) { ... }
```

The watched tint uses a slight green shift on the surface color rather than a hardcoded hex.

### `TvStreamScreen.TvStreamItem`

```kotlin
// Before
Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) { ... }

// After
Surface(onClick = onClick) { ... }  // androidx.tv.material3.Surface
```

`TvBadge` in the same file uses `material3.Surface` (non-interactive badge) — **no change**, correct as-is.

### `TvBrowseScreen.TvMediaCard`

Already uses `tv.material3.Surface` — **no change required**.

---

## Files Created / Modified

| Action | File |
|---|---|
| Create | `src/main/kotlin/me/proxer/app/tv/TvTheme.kt` |
| Create | `src/main/kotlin/me/proxer/app/tv/TvBookmarksScreen.kt` |
| Create | `src/main/kotlin/me/proxer/app/tv/TvScheduleScreen.kt` |
| Modify | `src/main/kotlin/me/proxer/app/tv/TvMainActivity.kt` |
| Modify | `src/main/kotlin/me/proxer/app/tv/TvAppShell.kt` |
| Modify | `src/main/kotlin/me/proxer/app/tv/TvBrowseScreen.kt` |
| Modify | `src/main/kotlin/me/proxer/app/tv/TvPlaceholderScreen.kt` |
| Modify | `src/main/kotlin/me/proxer/app/tv/auth/TvLoginActivity.kt` |
| Modify | `src/main/kotlin/me/proxer/app/tv/search/TvSearchScreen.kt` |
| Modify | `src/main/kotlin/me/proxer/app/tv/episode/TvEpisodeScreen.kt` |
| Modify | `src/main/kotlin/me/proxer/app/tv/stream/TvStreamScreen.kt` |
| Modify | `src/main/kotlin/me/proxer/app/tv/detail/TvMediaDetailActivity.kt` |
| Modify | `src/main/kotlin/me/proxer/app/tv/detail/TvMediaDetailScreen.kt` |
| Modify | `src/main/kotlin/me/proxer/app/anime/schedule/ScheduleViewModel.kt` |

---

## Out of Scope

- TV manga/novel bookmarks (filtered out; no TV reader)
- Bookmark delete/undo on TV
- TV light theme variant
- News, Info, Settings screens (remain as `TvPlaceholderScreen`)
