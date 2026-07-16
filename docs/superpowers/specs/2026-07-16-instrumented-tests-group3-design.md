# Instrumented UI Tests — Group 3 (Intent-Seam Screens) Design

**Date:** 2026-07-16
**Scope:** Group 3 of the rollout defined in `2026-07-15-instrumented-test-rollout-design.md`. Covers the screens whose Activities `requireNotNull` an intent extra and so crash on a bare launch — the group's harness work is adding the public `getIntent` factories they lack.
**Depends on:** Groups 1+2 (branch `instrumented-tests-group1-no-arg-screens`, 46/46 green, CI passing). Reuses their fixtures unchanged: `ProxerCallFixtures`, `TabFixtures.switchToTab`, `InstrumentedTestBase`, `LoginFixtures`, and the `TestApplication` emoji-provider install.

---

## Context

Group 2 covered the tab hosts reachable through existing factories and added `ProfileActivity.getIntent`. Group 3 is the intent-seam group: six screens from the rollout spec whose Activities take only `navigateTo(Activity, …)` and read a required extra via `getSafeStringExtra`/`getSafeParcelableExtra`, so `ActivityScenario.launch(Class)` throws `IllegalArgumentException: No value found for key …` in `onCreate`.

The rollout spec's projection held up better than Group 2's did — but two facts were still worth verifying, and one changed the plan:

**All six are API-backed. None move to Group 4.** Group 2 found `ConferenceScreen`/`ConferenceList`/`ChatContainer` were Room-backed (`dataSingle = Single.never()`); the concern was that more chat screens would be too. They are not. `ChatViewModel` (`api.chat.messages`), `ChatRoomInfoViewModel` (`api.chat.roomUsers`) and `ConferenceInfoViewModel` (`api.messenger.conferenceInfo`) all fetch from `ProxerApi` and are stubbable. Confirmed by reading each `dataSingle`/`endpoint`.

**WebView is dropped.** `WebViewActivity` has no ViewModel, no API, no fetch. Its only Compose-assertable content is the `TopAppBar` title, which is `Text(url)` — the exact URL passed in the intent. A test could only assert that the string echoes back; the actual page renders inside an `AndroidView` `WebView`, invisible to Compose semantics. That verifies nothing about rendering or data, so it is recorded as a deliberate gap (see "Known gaps"), not covered with a nominal test.

Net scope: **5 screens as originally listed minus WebView, 7 tests, 5 production seams.**

---

## Goals

- Instrumented smoke coverage for Industry (2 tabs), TranslatorGroup (2 tabs), ChatActivity, ChatRoomInfo and ConferenceInfo.
- The five `getIntent` factories these need, following the convention Group 2 established.

## Non-goals

- WebView (recorded gap).
- Room-backed chat — Group 4.
- Re-asserting ViewModel logic the JVM suite already covers; every screen here has a passing `*ViewModelTest`.
- Deep tests (error/retry/pagination). All seven are smoke tests. Every Group 3 screen is login-gated, so a logged-out deep test would only re-prove what the POC already covers.

---

## Production seams — 5 getIntent factories

Each Activity currently exposes only `navigateTo(Activity, …)`, which builds the intent inline and launches it. Add a public `getIntent(Context, …): Intent` and have `navigateTo` delegate to it, exactly as `ProfileActivity.getIntent` did in Group 2. The extras' key constants are `private const val`, so tests cannot reference them — the factory is how a test reaches these Activities without hardcoding string literals that rot on rename.

| Activity | New factory | Required extras it fills |
|---|---|---|
| `IndustryActivity` | `getIntent(context: Context, id: String, name: String? = null)` | `"id"` (req), `"name"` (opt) |
| `TranslatorGroupActivity` | `getIntent(context: Context, id: String, name: String? = null)` | `"id"` (req), `"name"` (opt) |
| `ChatActivity` | `getIntent(context: Context, chatRoomId: String, chatRoomName: String, chatRoomIsReadOnly: Boolean = false)` | `"chat_room_id"` (req), `"chat_room_name"` (req), `"chat_room_is_read_only"` (opt) |
| `ChatRoomInfoActivity` | `getIntent(context: Context, chatRoomId: String, chatRoomName: String)` | `"chat_room_id"` (req), `"chat_room_name"` (req) |
| `ConferenceInfoActivity` | `getIntent(context: Context, conference: LocalConference)` | `"conference"` (req, `LocalConference` parcelable) |

`navigateTo` keeps its `Activity` parameter where it performs a transition; `getIntent` takes `Context`. `ConferenceInfoActivity`'s factory is the one that passes a parcelable rather than strings — its `navigateTo(Activity, conference)` delegates to `getIntent(context, conference)`.

These are additive, low-risk changes to production code, each defensible on its own as following the established convention. The alternative — tests hardcoding `"chat_room_id"` etc. — was rejected by the rollout spec for the same reason it rejected it in Group 2.

---

## Test inventory — 7 tests

| Screen | Tests | Endpoint (concrete type) | Assertion target |
|---|---|---|---|
| Industry — Info tab | 1 | `api.info.industry(id)` → `IndustryEndpoint` | `industry.description` |
| Industry — Projects tab | 1 | `api.list.industryProjectList(id).includeHentai()` → `IndustryProjectListEndpoint` | `project.name` |
| TranslatorGroup — Info tab | 1 | `api.info.translatorGroup(id)` → `TranslatorGroupEndpoint` | `group.description` |
| TranslatorGroup — Projects tab | 1 | `api.list.translatorGroupProjectList(id).includeHentai()` → `TranslatorGroupProjectListEndpoint` | `project.name` |
| ChatActivity | 1 | `api.chat.messages(id).messageId("0")` → `ChatMessagesEndpoint` | `message.username` |
| ChatRoomInfo | 1 | `api.chat.roomUsers(id)` → `ChatRoomUsersEndpoint` | `user.name` |
| ConferenceInfo | 1 | `api.messenger.conferenceInfo(id)` → `ConferenceInfoEndpoint` | `participant.username` |

Industry and TranslatorGroup share one test file each (the two tabs), so there are 5 test files. All endpoints are non-null (`stub safeExecute()` via `mockProxerCall`). Only the Projects endpoints page (stub `page`/`limit`/`includeHentai`); the rest are single-shot or cursor-based.

Industry and TranslatorGroup are structural twins, but their Projects entities differ in one constructor slot (see traps), so the two files are not copy-paste identical.

---

## Test pattern

Each test extends `InstrumentedTestBase`, calls `stubLoggedIn(storageHelper, preferenceHelper)` in `@Before`, launches via the screen's new `getIntent`, and uses `createEmptyComposeRule()` + `waitUntil` at a 5s timeout — the Group 2 own-activity pattern. No `grantStoragePermission()` (none is MainActivity-hosted). No `mockkObject(TextPrototype)` (on-device `android.text` is real and `TestApplication` installs the emoji provider).

For Industry/TranslatorGroup, both tab endpoints are stubbed in `@Before` (not per-test): `animateScrollToPage` transiently composes the neighbour page, so an unstubbed relaxed-mock endpoint would `ClassCastException` and kill the process. The Info test asserts on launch; the Projects test does `switchToTab` first. This is the MediaScreen/ProfileScreen convention from Group 2.

---

## Traps the plan must encode

All confirmed in source during exploration.

- **Never assert a toolbar title.** Industry/TranslatorGroup title is `data?.name ?: initialName ?: ""` — falls back to the `name` intent extra. Chat and ChatRoomInfo titles are `chatRoomName`. ConferenceInfo title is `conference.topic` from the passed parcelable. All are intent-extra-derived and render before/regardless of the fetch. Assert body content instead — this is the vacuity trap that made Group 1's original `ProfileSettings` assertion worthless.

- **ConferenceInfo needs a non-empty participant.** The JVM `ConferenceInfoViewModelTest` fixture uses `participants = emptyList()`, which renders only the vacuous title and a format-dependent date header. To get an honest assertion, stub a `ConferenceInfo` carrying one `ConferenceParticipant(id, image, username, status)` (constructor extracted from the 5.4.0 jar — not present in any JVM test) and assert its `username`.

- **Industry/TranslatorGroup Projects tab composes lazily.** `HorizontalPager` with the Projects page (index 1) not fetched until switched to. `switchToTab` with the label "Projekte" (both features' Projects tab resolves to the German `R.string.*_projects` = "Projekte"). The Info tab label is "Info". Both features render the same two label strings.

- **`IndustryProject` and `TranslatorGroupProject` differ in one slot.** `IndustryProject`'s 6th positional arg is `type: IndustryType`; `TranslatorGroupProject`'s is `state: ProjectState`. The fixtures are not interchangeable; the plan carries each verbatim from its JVM test.

- **Chat message body is a `BBCodeView` AndroidView** — unmatchable by `onNodeWithText`. Assert `message.username`, a real Compose `Text`. Note the body text is the field a naive test would reach for.

- **Chat polls every 3s.** `ChatViewModel.startPolling` re-hits `api.chat.messages(id).messageId("0")` on a loop. The stub is idempotent, so repeated calls are harmless and content stays stable — but the plan notes it so no one chases phantom endpoint invocations or writes `verify(exactly = 1)`.

- **ChatMessage id must be numeric-parseable.** `ChatViewModel` calls `id.toLong()` in its merge/cursor logic; a non-numeric fixture id crashes. Use `"0"`, `"1"`, etc.

**Cache-seeding is not a risk here.** None of these ViewModels seeds `data` from `StorageHelper` or Room in `init`; `BaseViewModel.init` only wires reactive reload subscriptions. `data` starts null and is set only by a successful `load()`, so a failed fetch shows the error state, not stale content. Every body assertion genuinely distinguishes fetch-success from failure. (`ChatViewModel` reads a message *draft* from storage, but that only fills the input field, not the message list.)

---

## Error handling

No production error-handling changes. The five `getIntent` factories are additive.

---

## Testing and verification

- Per test: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` against an API 31+ emulator. `--tests` is not accepted.
- The AVD `ci_api31_x86_64` matches CI.
- Final: full `connectedDebugAndroidTest`. Expect 46 existing + 7 new = 53 tests.

Group 2 measured ~1.1s per test (52s / 46). Group 3 should add roughly 8s (~52s → ~60s). CI time is not the binding constraint (established at the Group 1 checkpoint), so no further gate is proposed.

---

## Known gaps

Deliberate, recorded rather than hidden:

- **WebView is untested.** No ViewModel, no fetch, and the page renders in an `AndroidView` `WebView` outside Compose semantics — the only Compose-assertable content is the toolbar title, which is the intent-extra URL. A meaningful test would need Espresso-Web (`onWebView()`) against the live page, which no test in this suite uses and which depends on real network. Revisit only if an Espresso-Web pass is ever warranted.
- **Chat/ConferenceInfo message and date bodies are unasserted** where they render through `BBCodeView` or as format-dependent date strings — the username/participant fields are the stable targets.
- **Polling behaviour is not asserted.** Chat's 3s poll and ChatRoomInfo's 10s poll are tolerated (idempotent stubs), not verified.

---

## Follow-up

Group 4 (Room-backed chat) is next: `MessengerScreen`, plus `ConferenceScreen`, `ConferenceList` and `ChatContainerScreen` moved there from Group 2. It is the group that builds the in-memory `MessengerDao` / `WorkManager` fixtures. Group 5 (media-heavy: Anime, Manga, ImageDetail) follows.
