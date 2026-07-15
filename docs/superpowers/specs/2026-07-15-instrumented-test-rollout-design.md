# Instrumented UI Test Rollout Design — ProxerAndroid

**Date:** 2026-07-15
**Scope:** The rollout roadmap for per-screen instrumented UI tests, on top of the harness landed by `2026-07-15-instrumented-ui-test-infra-design.md` (merged as PR #86). Decides how the app's ~40 non-TV screens are grouped into follow-up cycles, in what order, at what depth, and which production seams the rollout requires. Does **not** specify the tests for any individual screen — each group below gets its own brainstorming → spec → plan cycle.

**Resolves:** the "Follow-up (out of scope here)" section of the infra design, which listed 14 feature packages and deferred grouping and priority order to "when we get there, not now". We are there.

---

## Context

The harness exists and works. `src/androidTest/kotlin/me/proxer/app/base/` provides `FakeAppModule` (10 mocked Koin singletons), `LoginFixtures` (`stubLoggedIn`/`stubLoggedOut`), `TestApplication` (starts Koin with the fakes plus the **real** `viewModelModule`), `TestRunner`, and `InstrumentedTestBase` (per-test mock reset). `NotificationScreenTest` proves the full path in 199 lines across 4 cases.

Two facts discovered while scoping this rollout reshape it, and both cut against the naive "apply the POC to every screen" plan:

**1. JVM ViewModel coverage is already essentially complete.** `src/test/` holds 40 `*ViewModelTest.kt` files, covering nearly every ViewModel in the app. Success/error/retry/pagination *logic* is therefore already tested, fast, off-emulator. Replaying the POC's 4-case treatment across ~40 screens would re-assert that logic on an emulator at a cost of roughly 8000 lines of test code, for a marginal gain over what the JVM suite already guarantees.

**2. Screens are not uniformly testable.** The POC's `ActivityScenario.launch(NotificationActivity::class.java)` pattern transfers cleanly only to screens that launch with a bare intent. Eight Activities `requireNotNull` an intent extra and crash in `onCreate` under a bare launch, and their extra-key constants are `private const val` with no public `getIntent` factory to reach them. Three screens render zero assertable nodes. One screen (`StreamScreen`) cannot be launched at all without a production refactor.

---

## Goals

- A grouping and ordering of the remaining ~40 screens into a small number of follow-up cycles, where each cycle adds exactly one harness capability and is unblocked by the cycles before it.
- A depth rule that spends emulator time on wiring, not on logic the JVM suite already covers.
- An explicit, upfront statement of which production seams the rollout needs and which it refuses.
- An explicit, upfront statement of what stays uncovered, so it reads as a decision rather than an oversight.

## Non-goals

- The per-screen test cases for any group — deferred to that group's own spec.
- Screenshot/visual regression testing.
- CI configuration changes. `connectedDebugAndroidTest` already runs on an api-30 x86_64 emulator and picks up new `src/androidTest/` sources automatically.
- Migrating the existing TV stateless-content tests. They remain valid for cheap, ViewModel-independent rendering checks — a different purpose from this harness.
- Re-asserting ViewModel logic already covered by the JVM suite.

---

## Strategy: what instrumented tests are for here

Given complete JVM ViewModel coverage, instrumented tests earn their emulator time only on what JVM tests structurally cannot reach:

- **DI resolution** — every Koin binding an Activity needs actually resolves at launch.
- **Activity launch** — required intent extras are satisfied, `BaseActivity`'s observable subscriptions survive, `onCreate` does not crash.
- **ViewModel→UI wiring** — `LaunchedEffect(Unit) { viewModel.load() }` is present and state reaches the composable.
- **Tab/navigation wiring** — child screens mount, initial tab resolves from intent/URI.

### Depth rule

- **Every covered screen gets a smoke test:** launches, DI resolves, content renders from a stubbed API.
- **Deep tests (error / retry / login-gate / pagination) only where the wiring itself can break:** login gates, pagination triggers, tab hosts, intent-arg parsing.

The POC's 4-case treatment is the exception, not the template. Where a deep case would only re-run assertions the screen's `*ViewModelTest` already makes, it is omitted, and the group's spec says so.

The one behaviour the harness must never paper over: `BaseViewModel` has no auto-load. A screen missing `LaunchedEffect(Unit) { viewModel.load() }` will hang waiting for content that is never requested. That is a real bug the smoke test should catch.

---

## Grouping

Five groups, each defined by the single harness capability it adds. Ordered cheapest-first, then by risk. Each group is unblocked by its predecessors, and no group re-solves an earlier group's gap.

| # | Group | Screens (approx.) | Harness capability added |
|---|---|---|---|
| 1 | No-arg screens | ~6 — News, Bookmark, Schedule, MediaList (drawer sections via the public `MainActivity.getSectionIntent`), ServerStatus, ProfileSettings | **None.** POC pattern applied as-is |
| 2 | Tabbed containers | ~19 — Media (+6 tabs), Profile (+6 tabs), ChatContainer, Topic, EditComment, CreateConference, PrvMessenger (list mode) | Tab-navigation helper; `IntentFixtures` over *existing* public factories |
| 3 | Intent-seam screens | ~10 — Industry (+2 tabs), TranslatorGroup (+2 tabs), Chat (pub), ChatRoomInfo, ConferenceInfo, WebView | Production seam: public `getIntent(Context, …)` on the 6 Activities lacking one |
| 4 | Room-backed chat | 2 — Conference, Messenger | `MessengerWorker.Companion` fixture; `MessengerDao` `LiveData` stubs |
| 5 | Media-heavy | 3 — Anime, Manga, ImageDetail | Production seams: `testTag` / `contentDescription` on reader and detail views; plus a `getIntent` factory for `ImageDetailActivity`, which needs both |
| — | **Out of scope** | Stream | Rejected — see below |

`NotificationScreen` is already covered by the POC and is not re-listed.

Group 2 is the largest by screen count but not by effort: the six `MediaScreen` tabs and six `ProfileScreen` tabs share one host pattern each, so the per-screen marginal cost after the first tab is small.

### Judgment calls

**Group 4 stubs the DAO rather than using in-memory Room.** In-memory Room is superficially attractive — `room-runtime` is already an `implementation` dependency, schemas are exported, and it would exercise `MessengerDao.getMessagesLiveDataForConference`'s real `MediatorLiveData` composition that a relaxed mock silently voids. It is rejected because `TestApplication` calls `startKoin` **once per instrumentation process**, so a real database would have to become the process-wide binding, and `InstrumentedTestBase.resetFakeAppModuleMocks` would then call `clearMocks` on a non-mock and throw. It fights the harness's central design for a benefit these two screens do not need. Revisit only if the DAO stubs become unmanageable — and if so, as a harness change with its own spec, not inline in Group 4.

**`ServerStatusScreen` is in Group 1 despite being trivial.** It is the only screen in the app with `isLoginRequired = false`, so it is the only test that proves the harness works *without* a login fixture. Worth one cheap test.

**`StreamScreen` is out of scope.** `StreamPlayerManager` is constructed inline (`StreamActivity.kt:101`), is passed to `StreamScreen` as a concrete class rather than an interface, and its constructor calls `prepare()` — a real ExoPlayer build plus a network fetch — before any test can intervene. It additionally requires a non-null `intent.data` whose URI `Util.inferContentType` recognises, and pulls in CastContext and `ImaAdsLoader`. Covering it means extracting an interface and injecting it via Koin: a test-only refactor of the most fragile screen in the app (ExoPlayer + Cast + IMA ads). Not worth it. `StreamScreen` stays uncovered, deliberately.

---

## Ordering and cycle structure

**G1 → G2 → G3 → G4 → G5.** Five cycles, not the 14 the infra spec's package list implied. Package-based grouping was rejected because most of the 14 packages contribute a single screen, so the ceremony would exceed the tests, while the real gaps (intent factories, Room/Worker fixtures, testTags) cut *across* packages and would have been re-solved several times over.

Each group is one brainstorming → spec → plan cycle. G1's spec should be thin by design: it adds no harness capability and exists to bank ~6 screens of coverage and prove the POC pattern generalises before any harness work is committed to.

### Checkpoint after G1

Measure the CI wall-clock delta on `connectedDebugAndroidTest` once G1 lands. The full rollout implies roughly 60–80 instrumented tests on an api-30 emulator. If G1's ~6 screens show the per-test cost is materially worse than expected, the remaining groups are re-scoped or sharded **before** the expensive groups land, not after. This checkpoint is a gate on G2, not a formality.

---

## Production seams

Two changes. Both are narrow, and both are defensible on their own merits without reference to the tests.

### 1. Public `getIntent(Context, …)` factories

Added to the 7 Activities that lack one: `ConferenceInfoActivity`, `ChatActivity`, `ChatRoomInfoActivity`, `IndustryActivity`, `TranslatorGroupActivity`, `WebViewActivity` (Group 3), and `ImageDetailActivity` (Group 5).

This follows existing convention rather than inventing test scaffolding — `MediaActivity.getIntent:39`, `TopicActivity.getIntent:35`, `NotificationActivity.getIntent:20` and `PrvMessengerActivity.getIntent:45` already do exactly this. Each existing `navigateTo(Activity, …)` then delegates to its new `getIntent`, removing duplicated intent-building.

The alternative — tests hand-building intents with hardcoded private string literals (`"id"`, `"url"`, `"conference"`, `"chat_room_id"`) — was rejected: those literals rot silently when a key is renamed, and the test failure would point at the screen rather than the rename.

### 2. Semantics for screens that render nothing assertable

- **`MangaScreen`** — a `testTag` on the reader container. Required, not cosmetic: `LaunchedEffect(data, error)` sets `isFullscreen = true` as soon as data loads, hiding both the `TopAppBar` and `BottomAppBar`, and the `AndroidView`-hosted `SubsamplingScaleImageView` publishes no Compose semantics (no `contentDescription`, no `testTag`). The happy path currently exposes zero matchable nodes. The `LocalInspectionMode` grey-box fallbacks are preview-only and do not apply under instrumentation.
- **`ImageDetailScreen`** — a real `contentDescription` on the refresh/error icon, currently `null`. An accessibility fix that is also assertable.

### Explicitly rejected

Extracting a `StreamPlayerManager` interface (see Group 5 / out-of-scope above).

Making the `private` `AnimeContent` / `StreamContent` / `MangaContent` composables `internal` to enable cheap TV-style `setContent { Content(...) }` tests. Not needed: these groups go through `ActivityScenario`, which is what exercises the DI and launch wiring this harness exists to test. Widening visibility would buy a weaker test.

---

## Error handling

No production error-handling changes. Both seams are additive.

---

## Testing and verification

Per group, before it is considered done:

- `./gradlew connectedDebugAndroidTest --tests "*<Screen>Test*"` locally against a connected emulator or device.
- Confirm the existing TV stateless-content tests (`TvSearchScreenTest` et al.) still pass — they share the `androidTest` variant's `TestApplication`, so a harness extension in any group can break them.

---

## Risks carried into the group specs

**`mockkObject(MessengerWorker.Companion)` leaks process-wide (Group 4).** `MessengerWorker`'s companion caches its Koin dependencies in `by safeInject` lazies that `InstrumentedTestBase.resetFakeAppModuleMocks` never touches. Group 4 needs a matching `unmockkObject` in `@After` or it poisons later-running test classes. This is the same class of bug as the JVM suite's `ErrorUtils` `by lazy` poisoning that forced `forkEvery = 4`.

Group 4 also needs `storageHelper.areConferencesSynchronized` stubbed `true`, or an empty conference list never clears the spinner; and `MessengerWorker.isRunning` stubbed, since its `workManager.getWorkInfosForUniqueWork(NAME).get()` against a relaxed `WorkManager` mock casts a relaxed child mock to `List` and is expected to throw `ClassCastException`. `MessengerViewModelTest` already establishes the precedent for both.

**Relaxed mocks break RxJava and LiveData return types.** Already documented for `themeObservable` in `LoginFixtures`. Group 4 hits it again via `MessengerDao`'s `LiveData` returns. Note that `getMessagesLiveDataForConference` is an `open` concrete method whose body a relaxed mock **replaces**, so stubbing the two underlying abstract methods has no effect — it must be stubbed directly.

**`AnimeScreen`'s Coil `AsyncImage` bypasses the mocked Koin `OkHttpClient`** and hits real network for hoster images. There is no `ImageLoaderFactory` on `MainApplication`, so Coil builds its own default `ImageLoader` with its own client. Failures are silent (a blank 64.dp box, no error slot), so it will not flake tests, but Group 5 must not assert on hoster images. Contrast with Glide, which *is* routed through the mocked client by `ProxerGlideModule`.

**Shared mock instances across the process.** `TestApplication` starts Koin once, so every test class runs against the same `fakeAppModule` mock instances. `InstrumentedTestBase` exists to clear them between tests; every group's tests must extend it rather than re-injecting the singletons.

---

## Follow-up

Group 1 (`no-arg screens`) is the next brainstorming → spec → plan cycle. Groups 2–5 follow in order, with the G1 CI checkpoint gating G2.
