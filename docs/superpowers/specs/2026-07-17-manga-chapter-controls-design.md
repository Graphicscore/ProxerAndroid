# Manga Reader — restore chapter-control element (+ fix vanishing bars)

**Date:** 2026-07-17
**Status:** Approved
**Area:** `me.proxer.app.manga` (Compose reader)

## Problem

Before the Compose migration (`acb81461`), `MangaFragment` inflated a `MediaControlView`
(`layout_media_control.xml`) as **both a header and a footer** inside the RecyclerView of
pages. It showed the uploader (→ profile), translator/scan group (→ translator group), the
chapter date, and four buttons (previous / next chapter, "Lesezeichen: Dieses Kapitel",
"Lesezeichen: Nächstes Kapitel" → "Abgeschlossen" on the last chapter).

`MediaControlView` was **deleted** during the migration and nothing replaced it. The current
`MangaScreen` keeps only a `TopAppBar` (name + chapter subtitle, back, orientation toggle) and a
`BottomAppBar` (prev/next chapter icons + "Chapter X / total"). Lost: uploader, date, scan
group, "mark this chapter read", and "finish".

Additionally there is a **bug**: `isFullscreen` starts `false`, is set to `true` the moment data
loads (both app bars are gated behind `if (!isFullscreen)`), and is never flipped back — there
is no tap handling. Once a chapter loads, both bars vanish permanently and cannot be recovered.
The old reader used a tap-zone scheme (center tap → `toggleFullscreen()`); the Compose rewrite
dropped it.

## Decisions (confirmed with user)

- **Layout:** Additive — keep the `BottomAppBar`; prev/next stay there. The restored card shows
  metadata + mark-read/finish only (no prev/next on the card).
- **Header + footer:** restore both, as the original did.
- **Orientations:** all three (vertical, LTR, RTL).
- **Chrome toggle:** single tap anywhere on a page toggles the top + bottom + system bars
  together. Reader opens immersive by default.

## Design

### 1. `MangaChapterControls` composable

A Material3 `Card` in `MangaScreen.kt` (mirrors anime's `EpisodeControlCard`), top to bottom:

- **Chapter line** — `displayChapterTitle` if non-blank, else the episode label ("Kapitel N").
- **Uploader row** — `R.string.view_media_control_uploader` label + `chapter.uploaderName`;
  row clickable → `ProfileActivity.navigateTo(activity, uploaderId, uploaderName)`. Hidden when
  uploader id/name is null/blank.
- **Translator/scan group row** — `R.string.view_media_control_translator_group` label +
  `chapter.scanGroupName`; clickable → `TranslatorGroupActivity.navigateTo(activity, scanGroupId,
  scanGroupName)`. Hidden when scan group id/name is null.
- **Date** — `chapter.date.toLocalDateTime...` formatted via `Utils.dateFormatter`.
- **Action buttons:**
  - "Lesezeichen: Dieses Kapitel" (`R.string.fragment_manga_bookmark_this_chapter`) →
    `onMarkThisRead()` → `viewModel.bookmark(currentEpisode)`.
  - "Lesezeichen: Nächstes Kapitel" (`R.string.fragment_manga_bookmark_next_chapter`), replaced
    by "Abgeschlossen" (`R.string.view_media_control_finish`) when `currentEpisode >=
    totalEpisodes` → `onMarkReadUpToHereOrFinish()` → `viewModel.bookmark(currentEpisode + 1)`
    or `viewModel.markAsFinished()`.

No new string resources are needed — all of the above already exist in `strings.xml`.

### 2. Placement — header + footer, all orientations

Model the reader content as an ordered list `Header · page₁…pageₙ · Footer`:

- **Vertical** (`LazyColumn`): `item { Header }; items(pages) { … }; item { Footer }`.
- **LTR** (`HorizontalPager`): pages `[Header] + pages + [Footer]`.
- **RTL** (`HorizontalPager`): `[Header] + pages.reversed() + [Footer]`.

In the pager, Header/Footer render as full-screen pages (`fillMaxSize`), vertically centered and
vertically scrollable so a tall card fits. A small sealed type (e.g. `MangaReaderItem`:
`Header`, `Footer`, `PageItem(page)`) keeps the LazyColumn/pager rendering uniform. Page keys
must stay stable (pages keyed by `decodedName`; header/footer get constant keys).

### 3. Fix the vanishing bars — tap-to-toggle

- Keep `isFullscreen` as user intent. On first successful chapter load → `true` (immersive), as
  today.
- Add `onToggleUi()` that flips `isFullscreen`. Wire it as an `OnClickListener` on each page's
  `SubsamplingScaleImageView` (in the `factory`) and the GIF `ImageView`. SSIV fires
  `performClick()` on a confirmed single tap (this is the same hook the old adapter used via
  `image.clicks()`), so pan/zoom/pager-swipe are unaffected.
- One `LaunchedEffect(isFullscreen, data, error)` applies the `WindowInsetsControllerCompat`
  show/hide: when `data != null && error == null`, follow `isFullscreen`; otherwise force the
  bars visible (loading/error). The `TopAppBar`/`BottomAppBar` keep rendering on `!isFullscreen`,
  so both app bars and the system bars toggle together on tap.
- Taps land on pages only; the control card's buttons handle their own clicks.

### 4. Data flow & feedback

`MangaScreen` already holds `currentEpisode`, `totalEpisodes`, and `data.chapter` (carrying
`uploaderId/uploaderName`, `scanGroupId/scanGroupName`, `date`, `title`). New callbacks threaded
through `MangaContent`: `onUploaderClick(id, name)`, `onTranslatorGroupClick(id, name)`,
`onMarkThisRead()`, `onMarkReadUpToHereOrFinish()`, `onToggleUi()`. They route to
`ProfileActivity` / `TranslatorGroupActivity` / `viewModel.bookmark(...)` /
`viewModel.markAsFinished()`. The existing `userStateData` / `userStateError` observers already
show success and error snackbars (including the not-logged-in error surfaced through
`validators.validateLogin()`), so they are reused unchanged.

### 5. Error / loading & testing

- The card renders only when a chapter is loaded; loading/error keep the current `ContentScreen`
  behavior with bars visible.
- Verification: add `@Preview` for the new card (compile-time safety); existing `MangaViewModel`
  unit tests (`./gradlew testDebugUnitTest`) must stay green; `compileDebugKotlin` clean.
- Instrumented `MangaScreenTest` assertions for the card (uploader/date visible, buttons work,
  tap toggles bars) are a **follow-up** — they need a device (API 31+) and are not part of the
  JVM test gate.

## Scope guard (YAGNI)

Out of scope: edge-tap pagination, prev/next on the card, new string resources, and any change to
automatic-bookmark-on-next behavior.

## Touched files

- `src/main/kotlin/me/proxer/app/manga/MangaScreen.kt` — new `MangaChapterControls`, reader-item
  list, tap-to-toggle, new callbacks/params, `@Preview`.
- (No changes expected to `MangaViewModel.kt`, strings, or layouts.)
