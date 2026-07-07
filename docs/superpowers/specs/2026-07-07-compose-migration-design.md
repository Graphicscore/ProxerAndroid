# Compose Migration Design

**Date:** 2026-07-07
**Branch:** compose-migration
**Scope:** Migrate all mobile UI (Activities, Fragments, RecyclerView Adapters, XML layouts) to Jetpack Compose. TV frontend (`me.proxer.app.tv`) already uses Compose — unchanged.

---

## Decisions

| Topic | Decision |
|---|---|
| Strategy | Big-bang on `compose-migration` branch, one commit per screen |
| BBCode renderer | Wrap in `AndroidView` — no rewrite |
| RxJava | Keep as-is; bridge to Compose via `observeAsState` |
| Navigation | Keep multi-Activity; delete Fragments; Activities call `setContent { }` directly |
| App widgets | Leave as-is (RemoteViews, independent of app UI) |
| App widgets (Glance) | Out of scope |

---

## Architecture

Each Activity becomes a `ComponentActivity` (or keeps `AppCompatActivity` where needed). Fragments are deleted entirely. Activities call `setContent { ProxerTheme { ScreenContent() } }` directly — consistent with the TV frontend pattern.

ViewModels are untouched. Compose screens observe ViewModel state via:
- `viewModel.data.observeAsState()`
- `viewModel.isLoading.observeAsState()`
- `viewModel.error.observeAsState()`

`kotterknife` (`bindView`) and `RxBinding` are removed file-by-file as each screen is migrated. No file retains both Compose and View-binding imports.

Special Views that have no Compose equivalent stay as Views wrapped in `AndroidView { }`:
- `SubsamplingScaleImageView` (manga reader)
- `android.graphics.pdf.PdfRenderer` (PDF pages in manga)
- Media3 `StyledPlayerView` (stream player)
- BBCode renderer root view (`BBCodeView`)

---

## Compose Infrastructure (first commit)

### `ProxerTheme`
Wraps `MaterialTheme` with dynamic colors driven by `preferenceHelper.themeContainer`. Replaces `getTheme().applyStyle(theme, true)` in `BaseActivity.onCreate`. Mirror of `TvTheme` — same pattern, mobile color tokens.

### `ContentScreen` composable
Replaces `BaseContentFragment`'s automatic loading/error/content state management.

```
ContentScreen(
    isLoading: Boolean,
    error: ErrorAction?,
    onRetry: () -> Unit,
    content: @Composable () -> Unit
)
```

Renders:
- `isLoading == true` → `CircularProgressIndicator`
- `error != null` → error text + retry/action button (same `ErrorAction` logic as `BaseContentFragment.showError`)
- otherwise → `content()`

### Dependencies to add
- `androidx.compose.runtime:runtime-livedata` — for `LiveData.observeAsState()`
- `androidx.lifecycle:lifecycle-runtime-compose` — for `collectAsStateWithLifecycle` (future use)

`kotterknife` and `RxBinding` dependencies remain in `build.gradle` until the last screen using them is migrated (final cleanup commit).

---

## DrawerActivity Migration

`DrawerActivity` currently wraps `MaterialDrawerWrapper` (mikepenz library) with RxJava click subjects. Replaced with Compose `ModalNavigationDrawer`.

### Changes to `DrawerActivity`
- `setContentView` → `setContent { }` 
- `MaterialDrawerWrapper` and its `itemClickSubject`/`profileClickSubject` deleted
- `DrawerState` hoisted to Activity level
- `ActionBarDrawerToggle` deleted

### `MainActivity` Compose structure
```
ModalNavigationDrawer(
    drawerContent = {
        DrawerHeader(user = storageHelper.user.observeAsState())
        DrawerItem entries for each DrawerItem enum value
    }
) {
    selectedScreen composable
}
```

`MainActivity.getItemToLoad()` routing logic is unchanged — drives a `selectedItem: MutableState<DrawerItem>` instead of `drawer.select(item)`.

`DrawerActivity` subclasses that aren't `MainActivity` (e.g. `ProfileActivity`, `MediaActivity`) use a simple back-arrow `TopAppBar` pattern — they extend `BaseActivity` directly after migration.

---

## Per-Screen Migration Pattern

Each screen commit follows this sequence:

1. Create `XxxScreen` composable (same package as old Fragment). Takes ViewModel + nav callbacks. Uses `ContentScreen` for loading/error states. List screens use `LazyColumn` with item composables.
2. Update Activity — remove `setContentView`, remove Fragment transaction, add `setContent { ProxerTheme { XxxScreen(...) } }`.
3. Delete Fragment file.
4. Delete Adapter + ViewHolder files (replaced by item composables).
5. Delete layout XMLs (`fragment_xxx.xml`, `item_xxx.xml`).
6. Delete menu XMLs (toolbar actions become `IconButton` in `TopAppBar`).
7. Remove `kotterknife`/`RxBinding` imports from all touched files.

**Tabs:** Screens with `ViewPager` + `TabLayout` (MediaActivity, ProfileActivity) use `TabRow` + `HorizontalPager`.

**Paged lists:** Screens extending `PagedContentFragment` (most list screens) use `LazyColumn` with an `onEndReached` side-effect: when the last visible item index approaches the list end, call `viewModel.loadMore()`. The ViewModel's existing `PagedViewModel` append logic is unchanged.

**Dialogs:** `LoginDialog`, `LogoutDialog`, `NoWifiDialog`, `ThemeDialog`, `LinkCheckDialog`, `ChatReportDialog` become `AlertDialog` composables. Migrated alongside their parent screen commit.

---

## Migration Order

| # | Commit | Notes |
|---|---|---|
| 1 | Infrastructure | `ProxerTheme`, `ContentScreen`, deps |
| 2 | DrawerActivity + MainActivity | `ModalNavigationDrawer`, delete `MaterialDrawerWrapper` |
| 3 | ServerStatus | Simplest list screen; establishes pattern |
| 4 | About | Static content |
| 5 | Settings + ProfileSettings | `LazyColumn` preference items (no Compose `PreferenceScreen` equivalent) |
| 6 | Schedule | Day-grouped list |
| 7 | News | Paged list |
| 8 | Bookmarks | Paged list |
| 9 | Notifications | Paged list |
| 10 | Media list | Paged list + filter bottom sheet |
| 11 | Media detail | `TabRow` + `HorizontalPager` (episodes, comments, recommendations, relations, discussion) |
| 12 | Profile | `TabRow` + `HorizontalPager` (info, about, media lists, comments, history, top-ten) |
| 13 | Chat / Messenger | Real-time paged list, CAB selection, complex |
| 14 | Forum / Topic | Paged posts |
| 15 | Industry + TranslatorGroup | Tabbed info screens |
| 16 | Edit comment | Rich text editor, toolbar menus |
| 17 | Manga reader | `AndroidView(SubsamplingScaleImageView)`, `AndroidView(PdfRenderer)` |
| 18 | Stream player | `AndroidView(StyledPlayerView)`, keep `StreamPlayerManager` untouched |
| 19 | Image detail + WebView | Thin wrappers |
| 20 | Crash activity | Release variant only |
| 21 | Cleanup | Remove `kotterknife`, `RxBinding`, `mikepenz` Material Drawer deps; delete all remaining layout XMLs |

---

## Out of Scope

- RxJava → coroutines/Flow migration (independent future PR)
- App widget migration to Glance
- BBCode renderer rewrite
- TV frontend changes (`me.proxer.app.tv`)
- Navigation Compose / single-Activity refactor
