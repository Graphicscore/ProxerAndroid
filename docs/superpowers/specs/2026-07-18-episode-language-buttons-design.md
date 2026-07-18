# Episode/Chapter language buttons with flag icons

**Date:** 2026-07-18
**Status:** Approved
**Branch:** `worktree-episode-language-buttons` (worktree at `.claude/worktrees/episode-language-buttons`, based on `origin/master` @ `4ca3c67c`)
**Baseline:** `./gradlew testDebugUnitTest` — 365 tests, 0 failures

## Problem

In the episode list (anime) and chapter list (manga/novel), expanding a row renders each
available language as a full-width plain-text label stacked vertically:

```kotlin
// EpisodeScreen.kt:206-239
if (expanded) {
    episode.languageHosterList.forEach { (language, _) ->
        Text(
            text = language.toAppString(context),
            ...
            modifier = Modifier.fillMaxWidth().clickable { ... },
        )
    }
}
```

Two regressions from the pre-Compose UI:

1. **No flag icons.** The old `EpisodeAdapter` set the flag as a compound drawable on the
   language `TextView`; the languages sat side by side in equal-weight columns.
2. **No hoster icons.** The old `layout_episode_language.xml` had a `FlexboxLayout` showing
   which streaming hosts served each anime language. `EpisodeRow.languageHosterList` still
   carries this data, but `EpisodeScreen` destructures it away (`{ (language, _) -> }`) at
   both line 118 and line 207 and never renders it.

Both were lost in commit `bff49889` ("feat: migrate Media detail screen to Compose").

## Scope note: it is not always two languages

The originating request describes "two labels for English and German". That holds for
manga/novel chapters, which carry `MediaLanguage.GERMAN` / `MediaLanguage.ENGLISH`. Anime
episodes can carry up to **four**: `GERMAN_SUB`, `GERMAN_DUB`, `ENGLISH_SUB`, `ENGLISH_DUB`.

A flag alone cannot distinguish GerSub from GerDub, so each button carries **flag *and*
label**, and the layout must survive four buttons on a narrow phone.

"If a language is not available the button should not be there" requires no explicit
handling: the code iterates `languageHosterList`, which only contains languages the API
returned. Absent languages were never rendered.

## Design

### 1. Shared flag resource helper

`Language.toAppDrawable(context)` (`ProxerLibExtensions.kt:135`) returns a `Drawable`, which
Compose cannot use with `painterResource`. Three Compose call sites already work around this
by inlining their own mapping.

Add the missing seam to `ProxerLibExtensions.kt`:

```kotlin
@DrawableRes
fun Language.toAppDrawableRes() = when (this) {
    Language.GERMAN -> R.drawable.ic_germany
    Language.ENGLISH -> R.drawable.ic_united_states
    Language.OTHER -> R.drawable.ic_united_nations
}
```

Rewrite the existing `toAppDrawable(context)` to delegate to it, so the mapping has one
source of truth. Collapse the inline duplicate at `MediaListScreen.kt:425-439` onto it.

The `Country -> @DrawableRes Int` duplicates in `TranslatorGroupScreen.kt:413` and
`IndustryScreen.kt:432` are a **different enum** and out of scope.

### 2. `LanguageButton` composable

New private composable in `EpisodeScreen.kt`. An `OutlinedButton` laying out, in order:

- flag `Image` at 16dp, from `language.toGeneralLanguage().toAppDrawableRes()`
- the label `Text` from `language.toAppString(context)`
- **only when `hosterImages` is non-null and non-empty:** a `VerticalDivider`, then one
  `AsyncImage` per hoster at 18dp

Hoster images load via Coil from `ProxerUrls.hosterImage(...)` with `ContentScale.Fit`,
matching the existing call site at `AnimeScreen.kt:606`. Coil is the established Compose
image loader here (`io.coil-kt:coil-compose:2.7.0`); Glide has no Compose integration
artifact in this project.

Manga and novel chapters have no hosters — only `AnimeEpisode` exposes `hosterImages`
(`EpisodeRow.kt:36`), so chapter rows render plain pills and the divider never appears.

`contentDescription = null` on both image types, matching the convention across every
non-TV screen: the adjacent label text carries the meaning, and a description would cause a
duplicate announcement.

### 3. Wiring into `EpisodeItem`

`EpisodeScreen.kt:206-239` becomes:

```kotlin
if (expanded) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        episode.languageHosterList.forEach { (language, hosterImages) ->
            LanguageButton(
                language = language,
                hosterImages = hosterImages,
                onClick = { navigateToEpisode(activity, episode, language, mediaId, mediaName) },
            )
        }
    }
}
```

`FlowRow` is already used in `BookmarkScreen.kt:396` and `MediaListScreen.kt:511`, so this
introduces no new idiom. Buttons are content-sized and wrap to a second line when they run
out of width — chosen over equal-width columns because four anime language variants get
cramped on a narrow phone.

The navigation `when (episode.category)` block is currently a ~20-line lambda nested inside
a modifier. Extract it to a private `navigateToEpisode(...)` function so the layout code
stays readable. Behaviour is unchanged.

### 4. Bookmark dialog consistency

The bookmark language picker (`EpisodeScreen.kt:117-135`) lists the same languages as bare
text. Add the same flag `Image` before each label, at 20dp — slightly larger than the 16dp
used in the buttons, to match the dialog's `bodyLarge` text. Hoster icons are **not** shown
here: the dialog is about picking a language to bookmark, not about where to watch.

## Testing

**JVM** (`src/test`, runs in CI):
- `toAppDrawableRes()` maps GERMAN → `ic_germany`, ENGLISH → `ic_united_states`,
  OTHER → `ic_united_nations`.
- `toAppDrawable(context)` still resolves for all three values after being rewritten to
  delegate.

**Instrumented** (`src/androidTest`, manual verification only):
- `EpisodeScreenTest`: a two-language row renders exactly two language buttons; a
  one-language row renders exactly one.

Precedent: `TvEpisodeScreenTest.kt`. Compose UI test deps are already declared
(`androidx.compose.ui:ui-test-junit4`). Per CLAUDE.md these require an **API 31+** emulator
and will not run in the `testDebugUnitTest` gate, so this test is written but verified
manually.

## Error handling

Unchanged. No Coil call site in this codebase sets a `placeholder` or `error` drawable, so a
hoster logo that fails to load leaves blank space — identical to `AnimeScreen` today. Not
worth diverging for.

## Files

| File | Change |
|---|---|
| `util/extension/ProxerLibExtensions.kt` | Add `Language.toAppDrawableRes()`; delegate `toAppDrawable()` to it |
| `media/episode/EpisodeScreen.kt` | Add `LanguageButton` + `navigateToEpisode`; rewrite expanded section as `FlowRow`; add flags to bookmark dialog |
| `media/list/MediaListScreen.kt` | Collapse inline flag mapping onto `toAppDrawableRes()` |
| `src/test/kotlin/me/proxer/app/util/extension/ProxerLibExtensionsTest.kt` | **New file** (no extension tests exist today) — drawable mapping tests |
| `src/androidTest/kotlin/me/proxer/app/media/episode/EpisodeScreenTest.kt` | **New file, new directory** (`media/` currently holds only `list/` and `MediaScreenTest.kt`) — button rendering tests |

## Out of scope

- `TvEpisodeScreen.kt`, which renders raw `lang.name` chips — this change targets the
  phone/tablet UI.
- The `Country -> Drawable` duplication in `TranslatorGroupScreen` / `IndustryScreen`.
- Replacing `ic_united_states` with a UK flag for English (pre-existing choice, unrelated).
