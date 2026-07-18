# Episode navigation and autoplay in the anime player

**Date:** 2026-07-18
**Status:** Approved

## Goal

While an anime episode is streaming, the player must offer next/previous episode controls
(shown only when that neighbour exists) and automatically continue with the next episode when
the current one finishes.

## Background

`StreamActivity` is launched with an *already resolved* video URL, produced by
`StreamResolutionResult.Video.play(...)` from `AnimeScreen`. The intent carries `id`, `name`,
`episode`, `language`, `coverUri` and `referer` — but no episode count, and the activity has no
API access of its own. Resolution is a two-step process owned by `AnimeViewModel`:

1. `api.anime.streams(entryId, episode, language)` — the list of hosters for one episode.
2. `StreamResolverFactory.resolverFor(hosterName).resolve(streamId)` — one hoster to a playable
   result.

`StreamActivity.onNewIntent` already swaps in a new URL in place
(`playerManager.reset()` + `contentKey++`), so part of the in-place switching plumbing exists.

## Decisions

**The player resolves neighbouring episodes itself and swaps the media source in place.**
The rejected alternative — `finish()`ing back to `AnimeScreen` with a new episode number — is
much smaller, but it degrades autoplay into "open a hoster list", which does not satisfy the
requirement. Eagerly pre-resolving the next episode during playback was also rejected: resolvers
are heavy and flaky (Cloudflare, ad tags) and resolved URLs expire, so the work is often wasted
or stale. Resolution is lazy, triggered by a button press or by playback ending.

**`episodeAmount` and `hosterName` travel as intent extras; the player performs no network call
just to render its controls.** `AnimeScreen` already holds `episodeAmount` before any stream can
be played — both come from the same `AnimeViewModel` load — and `StreamActivity` is not exported,
so nothing outside the app launches it. When `episodeAmount` is absent the controls hide and
autoplay is disabled; there is no fallback fetch and no crash path.

**The neighbouring episode prefers the hoster the user is already watching**, falling back to the
first playable stream in the existing sort order. Users choose a hoster for a reason (sub group,
quality); silently reassigning it mid-binge is worse than a slightly longer candidate walk.

**Autoplay is gated by a new setting, default on, and announced by a cancellable countdown.**
Advancing with no warning is hostile — falling asleep burns through a season, and the sudden
black-screen-then-load has no visible cause. The countdown also covers resolution latency
honestly.

**Next/previous live in the existing Compose overlay `TopAppBar`** in `StreamScreen`, which
already tracks the Media3 controller's visibility via `isToolbarVisible`. Media3's built-in
prev/next buttons only activate for a real multi-item timeline; we play one `MediaSource` at a
time, so using them would mean faking a playlist or overriding `exo_player_control_view.xml` —
disproportionate machinery for two buttons.

## Architecture

```
AnimeScreen (already knows episodeAmount + stream.hosterName)
      │  Video.play(AnimeStreamContext(...))
      ▼
StreamActivity ──── intent extras: id, name, episode, language, cover,
      │                            episodeAmount, hosterName
      │
      ├── StreamPlayerManager   ── new: playbackEndedSubject on STATE_ENDED
      │                            reset(startPosition) instead of reset()
      │
      └── StreamEpisodeViewModel (new, standalone ViewModel)
                │  navigateTo(episode, preferredHoster)
                ▼
          api.anime.streams(id, targetEpisode, language)
                │  filter ignored resolvers · group by hoster
                │  order: preferred hoster first, then existing sort
                ▼
          walk candidates, resolve each until one yields StreamResolutionResult.Video
                │
                ▼
          episodeNavigationResult ─► activity.switchToEpisode(episode, video)
                                        · save old episode's position
                                        · swap intent, playerManager.reset(newPosition)
                                        · contentKey++ (re-compose)
```

`StreamEpisodeViewModel` is a standalone `ViewModel`, not a `BaseViewModel`. `BaseViewModel`'s
`data`/`load()`/`dataSingle` contract models "load the screen's one payload"; episode navigation
is an on-demand side-channel, which is exactly the shape `AnimeViewModel.resolve()` already uses
(`resolutionResult` / `resolutionError` / `isLoading` as separate `ResettingMutableLiveData`).

The episode swap does not go through `startActivity`. `StreamActivity` is `singleTask`, so
re-launching would route into `onNewIntent`, but that path saves neither the outgoing episode's
position nor restores the incoming one — both are needed regardless. The shared body is
extracted into a private `applyStreamIntent(intent, startPosition)` used by **both**
`onNewIntent` and the new `switchToEpisode()`, so there is one code path and `onNewIntent` gains
the position handling it currently lacks.

## Components

### New

`anime/stream/StreamEpisodeViewModel.kt` — standalone `ViewModel(entryId, language)`.

- `episodeNavigationResult: ResettingMutableLiveData<EpisodeNavigationTarget>` — target episode
  plus its resolved `StreamResolutionResult.Video`.
- `episodeNavigationError: ResettingMutableLiveData<ErrorAction>`
- `isNavigating: MutableLiveData<Boolean>`
- `navigateTo(episode: Int, preferredHoster: String?)`
- `bookmark(episode: Int)`

Registered in `MainModules.kt` as
`viewModel { (entryId: String, language: AnimeLanguage) -> StreamEpisodeViewModel(entryId, language) }`.

### Modified

- `resolver/StreamResolutionResult.kt` — `makeIntent`/`play` already take seven loose optional
  parameters; adding two more would reach nine. They collapse into one `AnimeStreamContext` data
  class (`id`, `name`, `episode`, `episodeAmount`, `language`, `coverUri`, `hosterName`) alongside
  the existing `forceInternal` flag. Only three call sites exist (`AnimeScreen`, `TvStreamScreen`,
  and `StreamActivity.openInOtherApp`, which passes nothing).
- `anime/stream/StreamActivity.kt` — accessors for the two new extras; extract
  `applyStreamIntent(intent, startPosition)`; add `switchToEpisode(episode, video)`; save the
  outgoing position and restore the incoming one.
- `anime/stream/StreamPlayerManager.kt` — add `playbackEndedSubject`, fired from the existing
  `STATE_ENDED` branch; `reset()` gains a `startPosition: Long` parameter in place of its
  hardcoded `lastPosition = -1`.
- `anime/stream/StreamScreen.kt` — `SkipPrevious`/`SkipNext` `IconButton`s in the overlay
  `TopAppBar`, the autoplay countdown card, and the ViewModel wiring.
- `anime/AnimeScreen.kt` — remember the stream passed to `onPlay` so its `hosterName` and the
  known `episodeAmount` reach `Video.play`.
- `util/data/PreferenceHelper.kt` and `settings/SettingsScreen.kt` — `AUTOPLAY_NEXT_EPISODE` key
  and `isAutoplayNextEpisodeEnabled` property (default `true`), plus one switch row following the
  existing `autoBookmark` pattern.
- `res/values/strings.xml` — new German strings, matching the project's single-locale setup. The
  existing `fragment_anime_next_episode` / `fragment_anime_previous_episode` are reused for the
  button content descriptions.

## Behaviour

**Control visibility.** Previous is shown when `episode > 1`, next when `episode < episodeAmount`.
When `episodeAmount` is absent from the intent, both are hidden and autoplay is disabled.

**Candidate walk.** Resolvers may return `Link`, `App` or `Message` rather than `Video`
(Crunchyroll, Netflix, and similar). `navigateTo` resolves candidates in order and takes the first
`Video`. A candidate that errors is skipped rather than being fatal. If no candidate yields a
`Video`, `episodeNavigationError` fires and the current episode continues untouched. The walk
skips hosters whose resolver sets `ignore`, exactly as `AnimeViewModel.streamSingle` does, and
additionally skips non-public streams when logged out — `streamSingle` leaves that check to
`AnimeScreen`'s per-stream UI, which the player has no equivalent of.

**Error surfacing.** Navigation failures show a toast, not the existing error `AlertDialog` —
that dialog offers Retry/Finish and dismissing it ends the session. A failed navigation must not
endanger the episode already playing.

**Autoplay.** `STATE_ENDED` fires `playbackEndedSubject`. A per-episode guard flag prevents
repeated or cast-side `STATE_ENDED` emissions from stacking countdowns. The countdown runs inside
`repeatOnLifecycle(RESUMED)` — `lifecycle-runtime-compose` is already a dependency — so a
backgrounded player cannot chain-load episodes. Autoplay is suppressed on the last episode and
when the neighbour cannot be resolved to a playable `Video`.

**Countdown UI.** A bottom-centre card reading "Nächste Episode in 5…", with Cancel and a
play-now action. Cancel leaves the viewer on the finished episode.

**Position bookkeeping.** Advancing saves the outgoing episode's position and restores the
incoming episode's stored position. On natural end, the finished episode's stored position is
reset to `0`; otherwise reopening it would resume at the credits. `onStop` has the same flaw
today — fixing it for the ended case is in scope, reworking it generally is not.

**Bookmarks.** Advancing, by button or autoplay, calls `bookmark(newEpisode)` when
`areBookmarksAutomatic && isLoggedIn`, identical to `AnimeScreen.onNext`. Going backwards does not
bookmark, also matching `AnimeScreen`.

**Cast.** The episode swap goes through `playerManager.reset()`, which already handles both the
local and the cast player via `retry()`. No special-casing.

## Out of scope

The TV frontend (`tv/stream/TvStreamActivity`) delegates to its own screen and needs a separate
pass.

## Testing

JVM unit tests in `src/test/kotlin/me/proxer/app/anime/stream/StreamEpisodeViewModelTest.kt`,
built on the existing `RxTrampolineRule` and `ProxerEndpointTestUtils` infrastructure:

- preferred hoster is chosen when present;
- fallback to the first playable stream when the preferred hoster is absent from that episode;
- the walk skips `Link`, `App` and `Message` results and resolver errors;
- all candidates failing emits `episodeNavigationError` and no navigation result;
- `bookmark` fires only when `areBookmarksAutomatic` is set and the user is logged in.

Per `CLAUDE.md`, endpoint mocks target the concrete endpoint subtype rather than `Endpoint<T>`.
