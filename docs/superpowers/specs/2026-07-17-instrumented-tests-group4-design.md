# Instrumented UI Tests — Group 4 (Room-Backed Chat) Design

**Date:** 2026-07-17
**Scope:** Group 4 of the rollout defined in `2026-07-15-instrumented-test-rollout-design.md`. Covers the private-messenger screens whose content comes from a Room DAO rather than `ProxerApi`, plus the tabbed chat container that straddles both. This is the first group to add genuinely new harness infrastructure rather than reuse Groups 1–3.
**Depends on:** Groups 1+2+3 (branch `instrumented-tests-group1-no-arg-screens`, 53/53 green, CI passing). Reuses `InstrumentedTestBase`, `LoginFixtures`, `ProxerCallFixtures`, `TabFixtures.switchToTab`, `PermissionFixtures.grantStoragePermission`, `MainActivity.getSectionIntent`, and the `TestApplication` emoji-provider install.

---

## Context

Group 2 moved three screens here after discovering they are Room-backed, not API-backed: `ConferenceScreen` (the private-messenger conference list), `ConferenceList` (its embedded twin, a tab of `ChatContainerScreen`), and `ChatContainerScreen` itself. Group 4 adds them to `MessengerScreen` (the private-chat detail), the group's original member.

What makes these different from every prior group: **`ConferenceViewModel.dataSingle` and `MessengerViewModel.dataSingle` both return `Single.never()`** — there is no network fetch. Their visible content is published entirely from Room DAO `LiveData` (`getConferencesLiveData`, `getMessagesLiveDataForConference`, `getConferenceLiveData`). So the tests assert data that flowed through a stubbed DAO, not a stubbed endpoint. `ChatContainerScreen`'s two tabs split the difference: tab 0 (`ChatRoomList`) is API-backed, tab 1 (`ConferenceList`) is Room-backed.

Two traps make this the group most likely to hang rather than fail cleanly, both confirmed in source:

- **`MessengerWorker.isRunning`** does `workManager.getWorkInfosForUniqueWork(NAME).get().all { … }`. Against the relaxed `WorkManager` mock, `.get()` returns a relaxed value that `ClassCastException`s at `.all{}`. `ConferenceViewModel.dataSingle` evaluates `isRunning` on every `load()`, so rendering `ConferenceScreen` crashes without a fix.
- **`storageHelper.areConferencesSynchronized`** gates the conference-list spinner. An empty conference emission is *dropped* unless `areConferencesSynchronized = true`, leaving `data` null and the spinner up forever — a timeout, not a clean failure.

---

## Goals

- Instrumented coverage for `MessengerScreen`, `ConferenceScreen`, and `ChatContainerScreen`'s two tabs, driven by stubbed Room DAO `LiveData`.
- Two small new fixtures that make the Room/WorkManager path testable without touching the shared `InstrumentedTestBase` or wiring in-memory Room.
- One deep test for the `areConferencesSynchronized` empty-state gate, the group's nastiest wiring footgun.

## Non-goals

- In-memory Room. Rejected — see "Harness decision".
- Exercising real Room query logic (the `getConferencesLiveData` JOIN, the `MediatorLiveData` merge). If ever needed, that belongs in a plain Room DAO unit test with no Koin, not in the Compose/instrumented harness.
- Re-asserting ViewModel logic the JVM suite already covers.
- The `MessengerWorker` enqueue behaviour itself (fire-and-forget no-ops on the relaxed `WorkManager`).

---

## Harness decision: stub the MessengerDao mock, not in-memory Room

The DAO and database are already bound in `FakeAppModule` as two independent relaxed mocks, and `InstrumentedTestBase.resetFakeAppModuleMocks` hardcodes `clearMocks(… messengerDao, … messengerDatabase)` in its `@After`. Stubbing the DAO mock is drop-in: `getConferencesLiveData(any())` returns a `MutableLiveData(list)`, the `@After` clears the stubs each test, and nothing new is bound.

In-memory Room was rejected. It is dependency-free (`room-runtime` is on the androidTest classpath, schemas are exported) but fights the harness on three fronts: `clearMocks` throws on a real (non-mock) DAO/DB instance, so the shared base class would need editing; the DAO and database must be overridden *together* as `get<MessengerDatabase>().dao()` to share one instance; and per-class Koin overrides leak across the once-per-process `startKoin`. The realism gain does not justify destabilising the shared harness for four screens.

One confirmed caveat: `getMessagesLiveDataForConference` is an `open` concrete method that assembles a `MediatorLiveData`. A relaxed mock **replaces its body**, so stubbing its underlying abstract helpers is inert — it must be stubbed **directly**.

---

## New harness fixtures

### `base/WorkManagerFixtures.kt` — `stubWorkManagerIdle(workManager)`

```
every { workManager.getWorkInfosForUniqueWork(any()) } returns <ListenableFuture mock whose get() = emptyList()>
```

This makes `MessengerWorker.isRunning` return true (`emptyList().all{}` is vacuously true), so the `if (!isRunning) enqueueSynchronization()` guard skips and nothing hits the relaxed `WorkManager` in a crashing way. The remaining `enqueue*` calls (which are unconditional on some paths) are harmless no-ops on the relaxed mock — the *only* crash is `isRunning`'s cast, and this fixes it.

Chosen over `mockkObject(MessengerWorker.Companion)` (the JVM-test approach) deliberately: `mockkObject` on a companion leaks process-wide because the companion caches its Koin deps in `by safeInject` lazies that `resetFakeAppModuleMocks` never clears, so it demands a matching `unmockkObject` in every `@After` or it poisons later test classes. The WorkManager future stub needs no `@After` at all — `WorkManager` is already in the `clearMocks` vararg.

Called in `@Before` for every Room-backed test. Applying it even to `MessengerScreen` (which can dodge the worker) is simplest and harmless.

### `base/MessengerDaoFixtures.kt` — DAO stubs and entity builders

Helpers stubbing the DAO's `LiveData` returns and building the Room entities from their verified constructors:

- `stubConferences(dao, list)` → `getConferencesLiveData(any())` returns `MutableLiveData(list)`
- `stubMessages(dao, conferenceId, list)` → `getMessagesLiveDataForConference(conferenceId)` returns `MutableLiveData(list)` (stubbed directly)
- `stubConference(dao, conferenceId, conference)` → `getConferenceLiveData(conferenceId)` returns `MutableLiveData(conference)` (the `MessengerScreen` header)
- builders: `localConference(...)`, `conferenceWithMessage(...)`, `localMessage(...)`

**Verified constructors (from source / the 5.4.0 jar):**
- `LocalConference(id: Long, topic: String, customTopic: String, participantAmount: Int, image: String, imageType: String, isGroup: Boolean, localIsRead: Boolean, isRead: Boolean, date: org.threeten.bp.Instant, unreadMessageAmount: Int, lastReadMessageId: String, isFullyLoaded: Boolean)` — `@Entity("conferences")`, `Parcelable`.
- `ConferenceWithMessage(conference: LocalConference, message: SimpleLocalMessage?)` where `SimpleLocalMessage(messageId: Long, messageText: String, userId: String, username: String, messageAction: MessageAction)`.
- `LocalMessage(id: Long, conferenceId: Long, userId: String, username: String, message: String, action: MessageAction, date: org.threeten.bp.Instant, device: Device)` — `@Entity("messages")` with a `@ForeignKey` to `conferences`; `styledMessage = message.toSimpleBBTree()` runs eagerly in the constructor (fine on-device — real `android.text`, emoji provider installed).
- `ChatRoom(id: String, name: String, topic: String, isReadOnly: Boolean)` — `me.proxer.library.entity.chat.ChatRoom`.
- Enums: `MessageAction.NONE`, `Device.MOBILE` (`me.proxer.library.enums.*`).

---

## Test inventory — 5 tests

| Screen | Test | Launch | Data source | Assertion |
|---|---|---|---|---|
| MessengerScreen (detail) | populated | `PrvMessengerActivity.getIntent(context, conference)` | `getMessagesLiveDataForConference` (+ `getConferenceLiveData` header) | `message.username` |
| ConferenceScreen (list) | populated | bare `PrvMessengerActivity` launch | `getConferencesLiveData` | `conference.topic` |
| ConferenceScreen (list) | **empty state** | bare launch, `areConferencesSynchronized = true` | `getConferencesLiveData` → `emptyList()` | `R.string.error_no_data_conferences` |
| ChatContainer tab 0 (ChatRoomList) | populated | `MainActivity.getSectionIntent(DrawerItem.CHAT)` | `api.chat.publicRooms()` + `userRooms()` | `room.name` |
| ChatContainer tab 1 (ConferenceList) | populated | same, then `switchToTab` | `getConferencesLiveData` | `conference.topic` |

Five test files, one per row except the two ChatContainer tabs share `ChatContainerScreenTest`. The ChatContainer tab-1 test covers `ConferenceList`, the embedded twin of `ConferenceScreen`, which has no standalone entry of its own.

---

## Test pattern

Each test extends `InstrumentedTestBase`; in `@Before` calls `stubLoggedIn(storageHelper, preferenceHelper)` and `stubWorkManagerIdle(workManager)`; launches via the recipe above; and asserts with `createEmptyComposeRule()` + `waitUntil` at a 5s timeout. All four screens are login-gated.

`ChatContainerScreen` is MainActivity-hosted, so its tests also call `grantStoragePermission()` (the Group 1 drawer pattern) and launch via `getSectionIntent` (whose custom action skips the introduction flow).

---

## Traps the plan must encode

All confirmed in source.

- **`PrvMessengerActivity` branches on its extras.** A bare launch (no `CONFERENCE_EXTRA`, no shortcut id) renders `ConferenceScreen` — the list. `MessengerScreen` (detail) is reached only with the `CONFERENCE_EXTRA` `LocalConference` parcelable via `getIntent(context, conference)`. So the Messenger test passes the parcelable; both ConferenceScreen tests launch bare.

- **MessengerScreen's message body is a `BBCodeView` `AndroidView`** — no Compose semantics, unmatchable. Assert `message.username`, a real Compose `Text` shown only for non-own messages. `stubLoggedIn` leaves `storageHelper.user` a relaxed mock whose id is `""`, which differs from the fixture's `userId`, so the message is non-own and the username renders (the mechanism the Group 3 Chat test used).

- **MessengerScreen dodges the worker; ConferenceScreen cannot.** MessengerScreen's first `load()` is page 0 → `markConferenceAsRead` (a no-op on the relaxed DAO); stubbing a *non-empty* message list avoids the `enqueueMessageLoad`-on-empty path, so it never evaluates `isRunning`. ConferenceScreen's `dataSingle` evaluates `isRunning` on every `load()`. `stubWorkManagerIdle` is applied to every test regardless — simplest and harmless.

- **The `areConferencesSynchronized` gate.** A populated list renders regardless (the `isNotEmpty()` short-circuits). An **empty** emission publishes only if `areConferencesSynchronized = true` **and** `isLoggedIn = true`, else `data` stays null and the spinner never clears. The empty-state test must stub `areConferencesSynchronized = true`; `stubLoggedIn` covers the login half.

- **ChatContainer needs both a Room fixture and API stubs, and the API side is a zip.** Tab 0 (`ChatRoomList`) is API-backed and is the **default tab**, so landing on ChatContainer immediately calls `api.chat.publicRooms()`. `ChatRoomViewModel` additionally zips `api.chat.userRooms()` when logged in — so **both** endpoints must be stubbed or the zip never emits and the tab hangs. Both ChatContainer tests stub `publicRooms` + `userRooms`; the tab-1 test additionally stubs `getConferencesLiveData` and switches tabs.

- **`ChatContainerScreen` is a `SecondaryTabRow` host** — `switchToTab` (already handles fixed and scrollable rows) reaches tab 1. Tab labels are `R.string.fragment_chat_container_public` (tab 0) / `..._private` (tab 1).

- **`getConferencesLiveData` returns `List<ConferenceWithMessage>`, not raw conferences** — an `@Embedded LocalConference` plus a nullable `SimpleLocalMessage`. The fixture wraps each `LocalConference` in a `ConferenceWithMessage`.

**No cache-seeding vacuity here.** The screens render purely from the stubbed DAO `LiveData`, so a populated assertion genuinely proves the data flowed through.

---

## Error handling

No production changes anticipated. (Group 3 found a real NPE via its first test of a screen; if a Group 4 screen has an analogous latent crash, it is caught and handled per the rollout's precedent — flagged to the user, fixed minimally, verified.)

---

## Testing and verification

- Per test: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` against an API 31+ emulator (`ci_api31_x86_64` matches CI). `--tests` is not accepted.
- Final: full `connectedDebugAndroidTest`. Expect 53 existing + 5 new = 58 tests.

Group 3 measured ~1.06s per test (56s / 53). Group 4 should add ~6s (~56s → ~62s). CI time is not the binding constraint (established at the Group 1 checkpoint).

---

## Known gaps

- **Message/conference body BBCode and formatted subtitle strings are unasserted** where they render through `BBCodeView` or as format-dependent `messageAction.toAppString` — the username/topic fields are the stable targets.
- **Real Room query/merge logic is unexercised** (stubbed DAO returns fabricated `LiveData`). Deliberate; belongs in a Koin-free DAO unit test if ever wanted.
- **`MessengerWorker` enqueue behaviour is not asserted** — `isRunning` is forced idle and the enqueues are no-ops on the relaxed `WorkManager`.

---

## Follow-up

Group 5 (media-heavy: Anime, Manga, ImageDetail) is the final group. It needs the reader/detail `testTag` seams the rollout spec named, and `StreamScreen` stays out of scope. With Group 4 landed, the rollout has covered every screen it set out to except the deliberate gaps (WebView, Stream, the `api.ucp.*` own-profile branch, and the recorded latent-NPE / BBCode-body items).
