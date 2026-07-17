# Instrumented UI Tests — Group 5 (Media-heavy Screens) Design

**Date:** 2026-07-17
**Scope:** Group 5 — the final group of the rollout defined in `2026-07-15-instrumented-test-rollout-design.md`. Covers the three media-heavy screens: Anime (Media3 player host), Manga (`SubsamplingScaleImageView` reader), and ImageDetail. `StreamScreen` is deliberately excluded.
**Depends on:** Groups 1–4 (landed on `master`, PR #87, 58/58 green, CI passing). Reuses their fixtures unchanged: `InstrumentedTestBase`, `LoginFixtures`, `ProxerCallFixtures`, and the `TestApplication` once-per-process Koin + emoji-provider install.

---

## Context

Group 5 is the media group — the first whose screens render their primary content through an `AndroidView` (a Media3 surface, a `SubsamplingScaleImageView` reader) that publishes no Compose semantics. That is the whole difficulty of the group: on the happy path these screens can expose zero matchable Compose nodes, so coverage requires either targeting a Compose-visible non-media state (empty/error) or adding a production `testTag`/`contentDescription` seam.

Three facts, verified against source during exploration, shaped the plan:

**Anime has no player landmine.** `AnimeScreen` contains no `AndroidView` and constructs no ExoPlayer. Playback is deferred: tapping Play resolves a stream and launches a *separate* `StreamActivity`. `AnimeViewModel` is an ordinary `BaseViewModel<AnimeStreamInfo>` fetching `api.info.entryCore` zipped with `api.anime.streams`. So Anime is testable like any Group 3 API-backed screen — provided tests never tap Play (which fires a real Intent).

**Manga's happy path hides everything.** `MangaScreen`'s `LaunchedEffect(data, error)` sets `isFullscreen = true` the instant `data != null && error == null`, which removes both the `TopAppBar` and `BottomAppBar`; the reader itself is an `AndroidView`-hosted `SubsamplingScaleImageView` with no `testTag`/`contentDescription`. The `LocalInspectionMode` grey-box fallback is preview-only and does not render under instrumentation. Net: the successful reader exposes zero matchable nodes without a source-side `testTag`.

**ImageDetail is a pure display screen with a required extra and a null contentDescription.** No ViewModel, no fetch — it hands the intent-extra URL straight to a `SubsamplingScaleImageView` via `ImageSource.uri(url)`, bypassing Glide. In a test that native decode fails, driving `hasError = true` and rendering a Refresh icon whose `contentDescription` is currently `null`. The URL extra is `requireNotNull`, so a bare launch throws — the Activity needs a `getIntent` factory (its key constant is private).

Net scope: **3 screens, 4 tests, 3 production seams across 2 files.** No new test-infra fixtures (one small test-side `AnimeStream` builder for the deep test).

---

## Goals

- Instrumented smoke coverage for Anime, Manga and ImageDetail — proving DI/launch/wiring for the media screens.
- One deep test: Anime's populated stream list, the screen's actual purpose.
- The three production seams these screens need, each defensible on its own merits.

## Non-goals

- `StreamScreen` (see Out of scope).
- Asserting media rendering itself — the player surface and the reader/detail bitmaps are `AndroidView` internals, invisible to Compose regardless of fixture.
- Re-asserting ViewModel logic the JVM suite already covers (`AnimeViewModelTest`, `MangaViewModelTest` both pass; ImageDetail has no ViewModel).
- Building `Page`/`Stream` library fixtures where a simpler path exists (empty-pages success for Manga; local `AnimeStream` for Anime).

---

## Test inventory — 4 tests, 3 files

| # | Screen | Kind | Launch | Stub | Assertion target |
|---|---|---|---|---|---|
| 1 | Anime | smoke | bare `Intent` (id → `"-1"`) | `entryCore("-1")` success + `streams(...)` = `emptyList()` | `R.string.error_no_data_anime` text |
| 2 | Anime | deep | bare `Intent` | `streams(...)` = `[AnimeStream]` (new test-side builder) | `Text(stream.hosterName)` |
| 3 | Manga | smoke | bare `Intent` | `entryCore("-1")` success + `chapter(...)` = `Chapter(pages=emptyList())` | reader-container `testTag("mangaReader")` displayed |
| 4 | ImageDetail | smoke | `getIntent(context, url)` (new factory) | none (pure display; native decode fails → error state) | Refresh icon via new `contentDescription` |

Tests 1–3 are login-gated (`AnimeViewModel`/`MangaViewModel` inherit `isLoginRequired = true`) → each calls `stubLoggedIn(storageHelper, preferenceHelper)` in `@Before`. Test 4 has no ViewModel and no login gate. Anime's two tests share one file; three test files total.

---

## Production seams — 3 changes, 2 files

All additive, narrow, and defensible without reference to the tests — the same posture as Group 3's seams.

### 1. `ImageDetailActivity.getIntent(context, url)` — new factory

`ImageDetailActivity` today exposes only `navigateTo(context: Activity, url: HttpUrl, imageView: ImageView? = null)` (`ImageDetailActivity.kt:22`), which builds the intent inline with the private `URL_EXTRA = "url"` (`:20`) and launches with a shared-element transition. The extra is read back via `getSafeStringExtra` = `requireNotNull` (`:32-33`), so a bare launch throws `IllegalArgumentException` in `onCreate`.

Add a public factory following the established convention (`MediaActivity.getIntent`, the 5 Group 3 factories):

```kotlin
fun getIntent(context: Context, url: HttpUrl): Intent =
    context.intentFor<ImageDetailActivity>(URL_EXTRA to url.toString())
```

`navigateTo` delegates to it (keeping the shared-element transition path). Tests reach the required extra without hardcoding `"url"`.

### 2. `ImageDetailScreen` Refresh icon `contentDescription` — null → real (a11y fix + seam)

`ImageDetailScreen.kt:44` — the retry icon is `contentDescription = null`, invisible to both `onNodeWithContentDescription` and TalkBack. The icon resets `hasError = false` (a retry gesture). Give it the existing string that already means exactly this:

```kotlin
contentDescription = stringResource(R.string.error_action_retry)
```

A genuine accessibility improvement; the test assertion is a side benefit.

### 3. `MangaScreen` reader container `testTag`

`MangaContent`'s `ContentScreen { … }` success lambda (`MangaScreen.kt:~316-352`) hosts the `LazyColumn` (vertical orientation) / `HorizontalPager` (horizontal) that composes only when `data != null && pages != null`. Add `Modifier.testTag("mangaReader")` to that container (both orientation branches, or their shared parent `Box`). It is the single "reader rendered on successful load" anchor — the only Compose-visible proof the happy path reached fullscreen reader mode.

The `testTag` is required, not cosmetic: page-level tags would not help because the empty-pages success fixture composes zero `MangaImagePage`s, and the page bitmap is an `AndroidView` internal in any case.

---

## Test pattern

Each test extends `InstrumentedTestBase`, uses `createEmptyComposeRule()` + `waitUntil { onAllNodesWithText(...)/onAllNodesWithContentDescription(...)/onAllNodesWithTag(...).fetchSemanticsNodes().isNotEmpty() }` at a 5s timeout — the Group 2 own-activity pattern. No `grantStoragePermission()` (none is MainActivity-hosted). No `mockkObject(TextPrototype)` (on-device `android.text` is real; `TestApplication` installs the emoji provider).

Tests 1–3 launch Anime/Manga via a bare `Intent(context, XActivity::class.java)` — every extra has a fallback, so no factory or private literal is needed. Test 4 launches ImageDetail via the new `getIntent(context, url)` with any well-formed `HttpUrl`.

---

## Traps the plan must encode

All confirmed in source during exploration.

- **Bare-Intent defaults are the endpoint contract.** With no extras, `id` → `"-1"`, `episode` → `1`, `language` → `AnimeLanguage.ENGLISH_SUB` (Anime) / `Language.ENGLISH` (Manga). Stub the endpoints at exactly those argument values: `api.info.entryCore("-1")`, `api.anime.streams("-1", 1, AnimeLanguage.ENGLISH_SUB).includeProxerStreams(true)`, `api.manga.chapter("-1", 1, Language.ENGLISH)`. Using the defaults keeps the private `"id"`/`"episode"`/`"language"` literals out of the test and needs no new Anime/Manga factory.

- **Concrete endpoint types.** `EntryCoreEndpoint`, `StreamsEndpoint`, `ChapterEndpoint` — mock the concrete subtype, not `Endpoint<T>`. `StreamsEndpoint` chains `.includeProxerStreams(true)` (stub it to return self). `ChapterEndpoint` resolves through `buildPartialErrorSingle(entry)`, a different call wrapper than plain `buildSingle()` — the plan pins the exact `ProxerCall` stub (mirrors how `MangaViewModelTest` uses `stubSuccess`). `EntryCoreEndpoint` and `StreamsEndpoint` use `buildSingle`/`buildPartialErrorSingle` respectively.

- **Manga happy path = empty pages, deliberately.** `Chapter(pages = emptyList())` is non-null, so it dodges both post-map branches (`isOfficial` → `MangaLinkException`, `pages == null` → `MangaNotAvailableException`). It is a valid success that flips `isFullscreen = true`. Assert `testTag("mangaReader")`, never chrome (it's been removed). This reuses the exact shape `MangaViewModelTest` already builds.

- **Do not assert Anime title / episode / control card.** `name = data?.name ?: initialName`; the episode text is seeded from the intent; `EpisodeControlCard` is gated only on `episodeAmount != null` — all satisfiable vacuously by intent extras. Pass NO `name`/`episode_amount` extras and assert only `error_no_data_anime` (test 1) and `Text(hosterName)` (test 2), which come exclusively from the fetch. This is the vacuity trap from Group 1's original `ProfileSettings` assertion.

- **Anime deep fixture: build the local `AnimeStream` directly.** 13 fields (`id, hoster, hosterName, image, uploaderId, uploaderName, date: Instant, translatorGroupId?, translatorGroupName?, isOfficial, isPublic, isSupported, resolutionResult?`) — simpler than the library `Stream` + `toAnimeStream` mapping. `StreamItem`'s collapsed header always renders `Text(hosterName)`. **Never tap Play** — it calls `viewModel.resolve` and fires a real Intent to `StreamActivity` (plus a cellular-network `AlertDialog` gate).

- **Coil hoster icons bypass the mocked client.** `AnimeScreen`'s per-stream `AsyncImage` (`model = ProxerUrls.hosterImage(...)`) uses Coil's own default `ImageLoader` (no `ImageLoaderFactory` on `MainApplication`), not the Koin `OkHttpClient`, and has `contentDescription = null`. It silently renders a blank box — harmless (no error slot, no flake), never asserted.

- **ImageDetail image fails by design.** The raw-URI `SubsamplingScaleImageView` (`ImageSource.uri(url)`, `ImageDetailScreen.kt:81`) bypasses Glide, so the mocked `OkHttpClient` never intercepts it; the native decode fails and the `OnImageEventListener` sets `hasError = true` **asynchronously**. `waitUntil` the Refresh `contentDescription` node appears — don't assert synchronously.

- **Possible latent first-frame NPE.** Both `AnimeScreen` and `MangaScreen` may reference `data!!`-style values inside a content lambda that can compose before `load()` completes — the same class of bug that crashed Group 3's `ConferenceInfoScreen`. If a happy-path test surfaces one, fix the loading guard in `src/main` (per the Group 3 precedent), escalated to the user before any production change.

---

## Error handling

No production error-handling changes. The `getIntent` factory and the two semantics seams (`contentDescription`, `testTag`) are all additive.

---

## Testing and verification

- Per test: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` against the API 31 AVD (`ci_api31_x86_64`). `--tests` is not accepted by `connectedDebugAndroidTest`.
- Final: full `./gradlew connectedDebugAndroidTest` → **62 tests** (58 existing + 4 new). Record the wall-clock delta against Group 4's ~63s baseline.
- Confirm the existing TV stateless-content tests (`TvSearchScreenTest` et al.) still pass — they share the `androidTest` variant's `TestApplication`, so any harness or seam change can break them.

Group 4 measured ~63s / 58 tests (~1.1s per test on-device). Group 5's 4 tests should add roughly 4–5s (~63s → ~68s). CI time is not the binding constraint (established at the Group 1 checkpoint), so no further gate is proposed.

---

## Out of scope

**`StreamScreen` — deliberately uncovered.** Carried verbatim from the rollout spec: `StreamPlayerManager` is constructed inline (`StreamActivity.kt:101`), passed to `StreamScreen` as a concrete class rather than an interface, and its constructor calls `prepare()` — a real ExoPlayer build plus a network fetch — before any test can intervene. It additionally requires a non-null `intent.data` whose URI `Util.inferContentType` recognises, and pulls in `CastContext` and `ImaAdsLoader`. Covering it means extracting an interface and injecting it via Koin — a test-only refactor of the most fragile screen in the app. Not worth it.

---

## Known gaps

Deliberate, recorded rather than hidden:

- **`StreamScreen` is untested** (see Out of scope).
- **Manga page-image rendering is unasserted.** The `mangaReader` testTag proves the reader shell composed on successful load, not that a page bitmap decoded. Real page decoding (Glide → `File` → `SubsamplingScaleImageView`) is an `AndroidView` internal, untestable through Compose semantics.
- **Anime playback is unasserted.** Playback is a separate `StreamActivity`; tapping Play is deliberately avoided. Group 5 covers the stream list and wiring, not the player.
- **ImageDetail happy-path image is unasserted.** It bypasses the mocked client and is not a Compose node; the error state is the only reachable and assertable one, and is the natural terminal state under test — the smoke test rides it.
- **Anime/Manga first-frame NPE guards are not audited proactively.** If a test surfaces one it is fixed (Group 3 precedent); no separate audit is performed.

---

## Follow-up

Group 5 is the **final rollout group.** After it lands, all ~40 in-scope screens carry instrumented smoke coverage, with `StreamScreen` the sole deliberate exclusion. Two optional Minor polish notes from the Group 4 review remain open and may be folded into this branch: extracting an `awaitText` helper in `MessengerScreenTest`, and a one-line pointer to the `stubConferences` timing rationale in the Group 4 spec's Known gaps.
