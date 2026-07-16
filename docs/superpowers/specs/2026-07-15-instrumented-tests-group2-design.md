# Instrumented UI Tests — Group 2 (Tabbed Containers) Design

**Date:** 2026-07-15
**Scope:** Group 2 of the rollout defined in `2026-07-15-instrumented-test-rollout-design.md`. Covers the tab-host screens and the remaining screens reachable through existing intent factories. Adds a tab-navigation helper and hoists the `ProxerCall` fixture.
**Depends on:** Group 1 (branch `instrumented-tests-group1-no-arg-screens`, 30/30 green), which established the harness patterns this group extends.

---

## Context

Group 1 covered the six no-arg screens and, more importantly, proved the harness works at all: it found that the suite could never pass on CI's API 30 (mockk-android's agent + `ComponentActivity.onPictureInPictureUiStateChanged`'s API 31+ parameter type), that the `instrumented-tests` job was `continue-on-error: true` and so had never gated anything, and that four CLAUDE.md claims were stale. CI now runs API 31 and the job blocks merges. The full suite is 30 tests in ~32s.

Group 2 is the first group where the *screens* are the hard part rather than the infrastructure.

---

## Scope correction to the rollout spec

The rollout spec assigned Group 2 "~19 — Media (+6 tabs), Profile (+6 tabs), ChatContainer, Topic, EditComment, CreateConference, PrvMessenger (list mode)", with the harness capability "tab-navigation helper; `IntentFixtures` over *existing* public factories". Two parts of that are wrong, discovered while exploring:

**1. Three of those screens are Room-backed, not API-backed — moved to Group 4.** `ConferenceViewModel.dataSingle` is `Single.never()`: there is no network fetch on that path at all. `data` is populated exclusively from a Room `MediatorLiveData`, and the launch path calls `MessengerWorker.isRunning`, which blocks on `WorkManager` and returns `true` for an empty work list (`.all {}` on empty is vacuously true), so the sync it guards never runs. Every text assertion there would be vacuous with respect to the API, and the screen needs the in-memory DAO / WorkManager fixtures that Group 4 exists to build.

This moves **`ConferenceScreen` (PrvMessenger list mode)**, **`ConferenceList`**, and **`ChatContainerScreen`** to Group 4. `ChatContainerScreen` has to move as a unit: its two tabs are `ChatRoomList` (API-backed) and `ConferenceList` (the *same* Room-backed ViewModel), so it cannot be coherently covered until those fixtures exist.

**2. `ProfileActivity` has no public factory, so "existing factories" doesn't hold.** It exposes only `navigateTo(Activity, …)`, which builds the intent inline and launches it, returning nothing. The rollout spec put new-factory production seams in Group 3; Profile needs one here. Adding it in Group 2 follows the same judgement that put `PermissionFixtures` in Group 1 when Task 1 turned out to need it.

Net scope: **18 screens, 16 tests** — the two hosts plus their 6 and 7 child tabs, plus `Topic`, `EditComment` and `CreateConference`. (The rollout spec's "~19" undercounted: it listed Profile as "+6 tabs" when it has 7 — Profil, Infos, Favoriten, Anime, Manga, Kommentare, Chronik. Removing the three Room-backed screens from a correct count of 20 leaves 18.) There are 16 rather than 18 tests because each host is covered implicitly by its tab-0 test.

---

## Goals

- Instrumented smoke coverage for both tab hosts and all 13 child tabs, plus `Topic`, `EditComment` and `CreateConference`.
- A tab-navigation helper that survives the label-collision trap, so the 13 tab tests don't each solve it.
- Collapse the `ProxerCall` fixture duplication before 16 more call sites entrench it.

## Non-goals

- Room-backed chat screens — Group 4.
- The `api.ucp.*` (own-profile) endpoint branch — see "Known gaps".
- Re-asserting ViewModel logic the JVM suite already covers. Every screen here has a passing `*ViewModelTest`.
- Deep tests (error/retry/pagination) except where noted; the depth rule from the rollout spec still applies.

---

## Test inventory — 16 tests

| Host | Tab / screen | Endpoint (concrete type) | Assertion target |
|---|---|---|---|
| Media | 0 Info | `api.info.entry(id)` → `EntryEndpoint` | `entry.description` |
| Media | 1 Comments | `api.info.comments(id).sort().page().limit()` → `CommentsEndpoint` | `comment.author` |
| Media | 2 Episodes | `api.info.episodeInfo(id).limit()` → `EpisodeInfoEndpoint` | `episode.title` |
| Media | 3 Relations | `api.info.relations(id).includeHentai()` → `RelationsEndpoint` | `relation.name` |
| Media | 4 Recommendations | `api.info.recommendations(id)` → `RecommendationsEndpoint` | `recommendation.name` |
| Media | 5 Discussions | `api.info.forumDiscussions(id)` → `ForumDiscussionsEndpoint` | `discussion.subject` |
| Profile | 0 Profil | `api.user.info(userId, username)` → `UserInfoEndpoint` | point-row values |
| Profile | 1 Infos | `api.user.about(...)` → `UserAboutEndpoint` | field values (not `about.about` — WebView) |
| Profile | 2 Favoriten | `api.user.topTen(...).category(ANIME\|MANGA)` zipped → `UserTopTenEndpoint` | `entry.name` |
| Profile | 3 Anime | `api.user.mediaList(...).category(ANIME)…` → `UserMediaListEndpoint` | `entry.name` |
| Profile | 4 Manga | same, `category(MANGA)` | `entry.name` |
| Profile | 5 Kommentare | `api.user.comments(...).category().page().limit().hasContent()` → `UserCommentsEndpoint` | `comment.entryName` |
| Profile | 6 Chronik | `api.user.history(...).includeHentai().page().limit()` → `UserHistoryEndpoint` | `entry.name` |
| — | Topic | `api.forum.topic(id).page().limit()` → `TopicEndpoint` | `post.username` |
| — | EditComment | `api.comment.comment(id, entryId)` → `CommentEndpoint` | char counter |
| — | CreateConference | `api.messenger.createConference(...)` → `CreateConferenceEndpoint` | endpoint invoked + error path |

Tab-0 tests cover their host implicitly (launch, tab row, default tab), so there are no separate host tests. All endpoints are non-null (`stub safeExecute()`) except `EditComment`'s publish path, which is not exercised here.

### `CreateConferenceScreen` is deliberately thin

Every string it renders — title, participant list, message field — comes from an intent extra or local UI state, never the network. Its success path sets a conference id, then waits on a `MessengerWorker.SynchronizationEvent` and a Room lookup before `result` is set and navigation fires. So the only API-observable signals are the loading spinner and the error snackbar, and its one test asserts the endpoint is invoked and the error path surfaces — not that content rendered. If that reads as too thin to earn a test, it is a clean candidate to move to Group 4 with the rest of the chat work. It is kept here because its *endpoint* is cleanly stubbable, unlike the Room screens.

---

## Harness additions

Three, in dependency order. The first two must land before any test is written.

### 1. `base/ProxerCallFixtures.kt` — hoist `mockProxerCall`

The `clone()` + `safeExecute()` helper is copy-pasted across six files today (the `NotificationScreen` POC plus all five Group 1 tests), and the clone-gotcha comment survives in only two of them. Group 2 adds 16 more call sites.

Provides `fun <T : Any> mockProxerCall(value: T)` and `mockProxerErrorCall<T>()`, plus a **nullable** variant `mockProxerNullableCall` that stubs `execute()` rather than `safeExecute()`. The nullable variant matters: `EditComment`'s publish path is `UpdateCommentEndpoint : Endpoint<Unit?>`, the only nullable endpoint in this group, and stubbing `safeExecute()` on that path silently fails to intercept.

Migrating the six existing files is mechanical and the suite is green, so regressions surface immediately. This mirrors the `grantStoragePermission` hoist in Group 1 — done before the copies multiply, not after.

### 2. `ProfileActivity.getIntent(Context, userId: String? = null, username: String? = null)`

The production seam. Follows the convention `MediaActivity.getIntent`, `TopicActivity.getIntent` and `NotificationActivity.getIntent` already set, with the existing `navigateTo` delegating to it — which also removes its inline intent-building. `navigateTo` keeps its `Activity` parameter because it performs a shared-element transition; `getIntent` takes a plain `Context` and does not, matching the others.

The alternative — hardcoding the private `"user_id"` / `"username"` keys in tests — is rejected for the same reason the rollout spec rejected it for Group 3: the literals rot silently on rename, and the failure would point at the screen rather than the rename.

### 3. `base/TabFixtures.kt` — tab-switch helper

`fun ComposeTestRule.switchToTab(label: String)`: click the tab, then poll until its content settles. Tabs carry no `testTag` or `contentDescription`, so matching is by label text; clicks route through `animateScrollToPage`, so the helper must `waitUntil` rather than assume synchrony.

**It must resolve tabs positionally against the known tab-row labels, not by bare text match.** `TopTenScreen` renders section headers from the *same* string resources as Profile tabs 3 and 4 — `section_user_media_list_anime` ("Anime") and `..._manga` ("Manga") — so while the Favoriten tab is displayed, `onNodeWithText("Anime")` matches two nodes and throws. Solving this once in the helper keeps it from biting thirteen call sites.

**No separate `IntentFixtures` file.** Once `ProfileActivity.getIntent` exists, every Group 2 screen has a usable factory (`Media`, `Topic`, `CreateConference` already do; `EditComment` has `Contract().createIntent`). The rollout spec anticipated needing one; it turns out not to.

---

## Test pattern

Each test extends `InstrumentedTestBase`, calls `stubLoggedIn(storageHelper, preferenceHelper)` in `@Before`, launches via the screen's factory, and uses `createEmptyComposeRule()` + `waitUntil` polling — all as established in Group 1. Tab tests add: stub the tab's endpoint → launch host → `switchToTab(label)` → assert that tab's entity text. Tab-0 tests skip the switch.

Host-launched screens use the **15s** timeout Group 1 settled on; the two non-host screens start at 5s.

**All 13 tabs are login-gated.** No ViewModel under `media/` or `profile/` overrides `isLoginRequired`, and the `true` default traces to a commit named "Require login for all views". `stubLoggedIn` is mandatory or every tab renders the login error instead of content.

**Profile tests stub `storageHelper.user` so it never matches.** Four Profile tabs branch on `storageHelper.user?.matches(userId, username)` and select a different endpoint chain entirely. All seven Profile tests take the other-profile (`api.user.*`) branch.

---

## Traps the plan must encode

All confirmed in source during exploration.

- **Tabs compose lazily.** `HorizontalPager` with `beyondViewportPageCount` unset (0), and each child screen fetches from its own `LaunchedEffect(Unit) { load() }`. Only the settled page composes, so a fetch fires only when its tab scrolls into view. One launch cannot assert several tabs, and a tab's stubs must be in place *before* switching to it.
- **`initialTab` is URI-only.** Both Activities derive it solely from an `ACTION_VIEW` intent's `data` path segments — there is no tab extra, and `getIntent` (extras-only, no action) always yields tab 0. No URI maps to Profile tabs 2 (Favoriten) or 5 (Kommentare), so those are click-only regardless.
- **Shared-ViewModel double-load.** `MediaScreen` and `MediaInfoScreen` both resolve `MediaInfoViewModel` with no Koin `key` from the same Activity store — the same instance — and both run `LaunchedEffect(Unit) { load() }`. A tab-0 launch fires `api.info.entry()` **twice**. Same for `ProfileViewModel`. `verify(exactly = 1)` will fail; this is expected, not a bug to chase.
- **`EditComment` draft pollution across tests.** `EditCommentActivity.onDestroy` persists `storageHelper.putCommentDraft(entryId, content)` whenever create-mode content is non-blank, and the ViewModel *overlays* that draft onto the fetched comment. One test can therefore leave a draft that makes the next test pass with its network stub removed. Clear the draft in `@Before` or use a unique `entryId` per test.
- **`TopTen` other-user needs both zipped singles.** It is `Singles.zip` of two `api.user.topTen(...)` calls (ANIME and MANGA). Stub only one and the zip never emits — an infinite spinner and a timeout, not a clean assertion failure.
- **BBCode bodies are invisible to Compose.** `Topic` post bodies, `Comments` bodies and `EditComment`'s preview render through `AndroidView { BBCodeView }`; `onNodeWithText` cannot see them. Assert on Compose siblings: `post.username`, `comment.author`, the char counter. (The JVM suite's `mockkObject(TextPrototype)` workaround is *not* needed on-device — `SpannableStringBuilder` is real there.)
- **Never assert a title that falls back to an intent extra.** `TopicScreen`'s subject falls back to the `topic` extra and `MediaScreen`'s to `name`; asserting either proves the extra, not the fetch. This is the vacuity trap that made Group 1's originally-specified `ProfileSettings` assertion worthless.
- **`Relations` drops self-referential fixtures.** `RelationViewModel` applies `.filterNot { it.id == entryId }`, so a fixture relation whose id equals the media id silently vanishes.
- **`PagedViewModel` does not null `data` on load.** Revisiting a paged tab re-runs `load()` against a VM whose `page` already advanced, appending rather than replacing, and a page-0 failure with existing data routes to `refreshError` (a snackbar) rather than `error`. One test per tab avoids this by never round-tripping; do not "optimise" the tests into a single tab walk.

**Cache-seeding is not a risk here.** None of the 13 tab ViewModels seeds from cache: `BaseViewModel.load()` actively nulls `data` on subscribe, so "fetch landed" is genuinely distinguishable from "fetch didn't". `ProfileSettingsViewModel` remains the only cache-seeded screen in the app.

---

## Error handling

No production error-handling changes. `ProfileActivity.getIntent` is additive.

---

## Testing and verification

- Per test: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` against an API 31+ emulator. **`--tests` is not accepted by this task.**
- Instrumented tests require **API 31+** — see CLAUDE.md. The local AVD `ci_api31_x86_64` matches CI.
- After the `mockProxerCall` migration, run the full suite: the six migrated files must stay green.
- Final: full `connectedDebugAndroidTest`. Expect 30 existing + 16 new = 46 tests.

Group 1 measured ~0.86s per test, so Group 2 should add roughly 15s (~32s → ~47s). The Group 1 checkpoint established that CI time is not the binding constraint, so no further gate is proposed here.

---

## Known gaps

Deliberate, recorded rather than hidden:

- **The `api.ucp.*` (own-profile) branch is untested.** Four Profile tabs have a second endpoint chain selected by `storageHelper.user?.matches(...)`, and only the Ucp branch produces the `.Ucp` entity subtypes that gate delete buttons and the `_ucp_status_` string variants. Covering both would add four tests to test a branch whose only UI difference is which endpoint filled the same list.
- **`MediaInfoViewModel`'s age-restriction path is untested.** It enforces age restriction post-fetch (`AgeConfirmationRequiredException` when `isTrulyAgeRestricted` and the pref is unset), which is deep-scope, not smoke.
- **BBCode content is unasserted** everywhere it appears, for the `AndroidView` reason above. Reaching it would need Espresso interop (`onView(withText(...))`), which no test in this suite currently uses.
- **`CreateConferenceScreen`'s success navigation is unasserted** — it requires a WorkManager sync event and a Room row, both Group 4 fixtures.

---

## Follow-up

Group 3 (intent-seam screens) is next, minus `ProfileActivity`'s factory which lands here. Group 4 grows to absorb `ConferenceScreen`, `ConferenceList` and `ChatContainerScreen` alongside `MessengerScreen`, making it the Room-backed chat group in full.
