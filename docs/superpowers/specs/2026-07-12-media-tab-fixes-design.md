# Media tab fixes: category switching + manga label

Date: 2026-07-12

## Problem

Two regressions from the Compose migration:

1. Clicking the Anime or Manga drawer tab only ever shows the category that was clicked first. Switching between them afterward shows stale data from the first tab.
2. Opening a manga entry's detail screen shows the "Episoden" (Episodes) tab label instead of "Kapitel" (Chapters).

## Root causes

**Bug 1 — tab switching stuck on first category**

`MediaListScreen` (`src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt:101`) resolves its ViewModel via:

```kotlin
val viewModel = koinViewModel<MediaListViewModel> {
    parametersOf(MediaSearchSortCriteria.RATING, defaultType, null as String?, null as Language?, ...)
}
```

No `key` is passed. Koin/AndroidX scope ViewModels to the hosting `ViewModelStoreOwner` (the Activity) keyed only by class by default, so the Anime and Manga tabs — both instantiating `MediaListViewModel` from the same `ModalNavigationDrawer` content in `MainScreen.kt:315-324` — share a single cached instance. Whichever category is opened first wins; the `parametersOf` passed on the second call are ignored because the instance already exists.

`ProfileMediaListScreen` (`src/main/kotlin/me/proxer/app/profile/media/ProfileMediaListScreen.kt:58-61`) already avoids this by passing a category-scoped key:

```kotlin
val viewModel = koinViewModel<ProfileMediaListViewModel>(
    key = "profile_media_${category.name}",
) { parametersOf(userId, username, category, null) }
```

`MediaListScreen` is missing the equivalent.

**Bug 2 — "Episoden" label hardcoded regardless of category**

`MediaScreen` (`src/main/kotlin/me/proxer/app/media/MediaScreen.kt:42-57`) has no `category` parameter and hardcodes:

```kotlin
val tabs = listOf(
    R.string.section_media_info,
    R.string.section_comments,
    R.string.category_anime_episodes_title,
    ...
)
```

`MediaActivity` (`src/main/kotlin/me/proxer/app/media/MediaActivity.kt`) already resolves `category: Category?` from the `CATEGORY_EXTRA` intent extra (line 57-58) but never forwards it into the `MediaScreen(...)` call (lines 78-84). Both `category_anime_episodes_title` ("Episoden") and `category_manga_episodes_title` ("Kapitel") already exist in `strings.xml:776-777`; only the wiring to pick between them is missing.

(Tab *content* — `EpisodeScreen`/`EpisodeViewModel` — already branches correctly on category; only the tab *label* is affected.)

## Fix

**Bug 1**

- Add a top-level, `internal` function in `MediaListScreen.kt`:
  ```kotlin
  internal fun mediaListViewModelKey(category: Category): String = "media_list_${category.name}"
  ```
- Pass it as `key = mediaListViewModelKey(category)` to the `koinViewModel<MediaListViewModel>()` call.

**Bug 2**

- Add `category: Category?` parameter to `MediaScreen`.
- `MediaActivity` passes its existing `category` property through to `MediaScreen(...)`.
- Add a top-level, `internal` function in `MediaScreen.kt`:
  ```kotlin
  internal fun episodeTabTitleRes(category: Category?): Int =
      if (category == Category.MANGA) R.string.category_manga_episodes_title else R.string.category_anime_episodes_title
  ```
- `MediaScreen` uses `episodeTabTitleRes(category)` when building the `tabs` list instead of the hardcoded resource.
- Default (anime or unknown/null category) preserves current behavior.

## Testing

Plain JVM unit tests, no new test infra (Compose UI testing isn't set up for these screens outside the `tv-support` branch, and isn't needed here — `R.string.*` are compile-time int constants, comparable without an Android context):

- `mediaListViewModelKey(Category.ANIME) != mediaListViewModelKey(Category.MANGA)` — and each call is deterministic (same category → same key).
- `episodeTabTitleRes(Category.ANIME) == R.string.category_anime_episodes_title`
- `episodeTabTitleRes(Category.MANGA) == R.string.category_manga_episodes_title`
- `episodeTabTitleRes(null) == R.string.category_anime_episodes_title`

No changes needed to `MediaListViewModelTest.kt` or `MediaInfoViewModelTest.kt`.

## Out of scope

- `MainScreen.kt` drawer selection state (`selectedItem`) — already correct, not the source of either bug.
- Adding Compose UI test infrastructure — not present for these screens outside `tv-support`; pure-function extraction gives regression coverage without that investment.
