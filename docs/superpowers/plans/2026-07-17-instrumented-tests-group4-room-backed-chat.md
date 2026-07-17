# Instrumented UI Tests — Group 4 (Room-Backed Chat) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Instrumented tests for the Room-backed chat screens — MessengerScreen, ConferenceScreen (×2), and ChatContainerScreen's two tabs — 5 tests, driven by a stubbed MessengerDao, plus two small new fixtures.

**Architecture:** Two harness fixtures land first: `stubWorkManagerIdle` (defuses the MessengerWorker `isRunning` crash) and `MessengerDaoFixtures` (stubs the DAO's LiveData returns + entity builders). Then five tests, each extending `InstrumentedTestBase`, stubbing the DAO on the shared relaxed Koin mock, launching the real Activity, and asserting DAO-driven content with `createEmptyComposeRule()` + `waitUntil`. No in-memory Room, no production changes anticipated.

**Tech Stack:** Kotlin, Compose UI test (`androidx.compose.ui.test.junit4.v2`), MockK (`mockk-android`), Koin, RxJava 2, Room LiveData, WorkManager, JUnit4 + `AndroidJUnit4`.

**Spec:** `docs/superpowers/specs/2026-07-17-instrumented-tests-group4-design.md`

---

## Shared Context — read before Task 1

**Environment.** Instrumented tests require **API 31+** (CLAUDE.md). AVD `ci_api31_x86_64` matches CI. `connectedDebugAndroidTest` does **NOT** accept `--tests`; filter with `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.

**Harness rules inherited from Groups 1–3:**
- Extend `InstrumentedTestBase`; it exposes `api`/`storageHelper`/`preferenceHelper`/`validators` and clears the `fakeAppModule` mocks (including `messengerDao` and `workManager`) in its `@After`. Resolve anything else (`MessengerDao`, `WorkManager`) with `by safeInject()`.
- `stubLoggedIn(storageHelper, preferenceHelper)` in `@Before` — all four screens are login-gated.
- `mockProxerCall(value)` from `me.proxer.app.base.ProxerCallFixtures` for `ProxerCall` mocks (ChatContainer tab 0 only).
- `switchToTab(label)` from `me.proxer.app.base.TabFixtures` handles both scrollable and fixed rows.
- `grantStoragePermission()` from `me.proxer.app.base.PermissionFixtures` — required for MainActivity-hosted screens (ChatContainer).
- No `mockkObject(TextPrototype)` — on-device `android.text` is real and `TestApplication` installs the emoji provider (which `LocalMessage`'s eager `toSimpleBBTree()` needs).
- All UI strings are German; resolve with `context.getString(...)`.

**Verified facts (source / 5.4.0 jar):**
- `MessengerDao.getConferencesLiveData(searchQuery: String): LiveData<List<ConferenceWithMessage>>`
- `MessengerDao.getConferenceLiveData(id: Long): LiveData<LocalConference?>`
- `MessengerDao.getMessagesLiveDataForConference(conferenceId: Long): MediatorLiveData<List<LocalMessage>>` — **`open` concrete; a relaxed mock replaces its body, so stub it directly**
- `MessengerViewModel(initialConference: LocalConference)`; the screen resolves it via `parametersOf(conference)` and the VM reads `initialConference.id` (a `Long`) for the two DAO calls
- `PrvMessengerActivity.getIntent(context: Context, conference: LocalConference, initialMessage: String? = null): Intent` (`CONFERENCE_EXTRA = "conference"`). A **bare** `ActivityScenario.launch(PrvMessengerActivity::class.java)` (no extras) renders `ConferenceScreen` — the list — not the detail.
- `ConferenceWithMessage(conference: LocalConference, message: SimpleLocalMessage?)`
- Entity constructors and enums: see `MessengerDaoFixtures` (Task 2)
- `ChatRoom(id: String, name: String, topic: String, isReadOnly: Boolean)`; `api.chat.publicRooms() → PublicChatRoomsEndpoint`, `api.chat.userRooms() → UserChatRoomsEndpoint`; `ChatRoomViewModel` **zips** public+user when logged in
- `DrawerItem.CHAT`; tab labels `R.string.fragment_chat_container_public` ("Öffentlich", tab 0, default) / `R.string.fragment_chat_container_private` ("Privat", tab 1); `R.string.error_no_data_conferences` ("Du hast noch keine Chats")

**Timeouts.** ChatContainer is MainActivity-hosted → `15_000` (the Group 1 drawer default). The `PrvMessengerActivity` screens are own-activity → `5_000`.

---

### Task 1: WorkManagerFixtures — stubWorkManagerIdle

**Goal:** A fixture that defuses the one real Room-screen crash — `MessengerWorker.isRunning`'s `.get().all{}` ClassCastException on the relaxed WorkManager mock.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/base/WorkManagerFixtures.kt`

**Acceptance Criteria:**
- [ ] `stubWorkManagerIdle(workManager: WorkManager)` exists in `me.proxer.app.base`
- [ ] It stubs `getWorkInfosForUniqueWork(any())` to a future whose `get()` returns `emptyList()`
- [ ] `./gradlew assembleDebugAndroidTest` compiles

**Verify:** `./gradlew assembleDebugAndroidTest` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Create the fixture**

Create `src/androidTest/kotlin/me/proxer/app/base/WorkManagerFixtures.kt`:

```kotlin
package me.proxer.app.base

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.mockk

/**
 * Stubs [WorkManager.getWorkInfosForUniqueWork] to return a future of an empty list.
 *
 * This is the one thing that crashes a Room-backed chat screen. MessengerWorker.isRunning does
 * `workManager.getWorkInfosForUniqueWork(NAME).get().all { … }`; against the relaxed WorkManager mock, `.get()`
 * returns a relaxed value that ClassCastExceptions at `.all { }`. Returning an empty list makes `isRunning`
 * true (`emptyList().all {}` is vacuously true), so the `if (!isRunning) enqueueSynchronization()` guard skips.
 * The remaining enqueue* calls are harmless no-ops on the relaxed mock -- isRunning's cast is the only crash.
 *
 * No @After is needed: WorkManager is already cleared by InstrumentedTestBase.resetFakeAppModuleMocks. Chosen
 * over mockkObject(MessengerWorker.Companion), which leaks process-wide (the companion caches Koin deps in
 * by-safeInject lazies that reset never clears) and would need a matching unmockkObject in every @After.
 */
fun stubWorkManagerIdle(workManager: WorkManager) {
    val future = mockk<ListenableFuture<List<WorkInfo>>>(relaxed = true)

    every { future.get() } returns emptyList()
    every { workManager.getWorkInfosForUniqueWork(any()) } returns future
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`.

If the `every { workManager.getWorkInfosForUniqueWork(any()) } returns future` line fails to type-check (WorkManager's return is `ListenableFuture<MutableList<WorkInfo>>` on some versions), change the mock's type parameter to match the exact return type reported by the compiler error and adjust `get()`'s return to `mutableListOf()`. Report what you changed. `com.google.common.util.concurrent.ListenableFuture` is on the androidTest classpath transitively via `androidx.work`.

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/base/WorkManagerFixtures.kt
git commit -m "test: add stubWorkManagerIdle fixture for Room-backed chat tests"
```

---

### Task 2: MessengerDaoFixtures — DAO stubs and entity builders

**Goal:** Shared helpers to stub the MessengerDao's LiveData returns and build the Room entities from their verified constructors.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/base/MessengerDaoFixtures.kt`

**Acceptance Criteria:**
- [ ] `stubConferences`, `stubConference`, `stubMessages` exist and stub the three DAO methods
- [ ] `localConference`, `conferenceWithMessage`, `localMessage` builders exist with the verified constructors
- [ ] `getMessagesLiveDataForConference` is stubbed directly (returns a `MediatorLiveData` with its value set)
- [ ] `./gradlew assembleDebugAndroidTest` compiles

**Verify:** `./gradlew assembleDebugAndroidTest` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Create the fixture**

Create `src/androidTest/kotlin/me/proxer/app/base/MessengerDaoFixtures.kt`:

```kotlin
package me.proxer.app.base

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import me.proxer.app.chat.prv.ConferenceWithMessage
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.chat.prv.LocalMessage
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.library.enums.Device
import me.proxer.library.enums.MessageAction
import org.threeten.bp.Instant

fun localConference(
    id: Long,
    topic: String,
    unreadMessageAmount: Int = 0,
    localIsRead: Boolean = true,
) = LocalConference(
    id = id,
    topic = topic,
    customTopic = "",
    participantAmount = 2,
    image = "",
    imageType = "",
    isGroup = false,
    localIsRead = localIsRead,
    isRead = true,
    date = Instant.ofEpochMilli(0L),
    unreadMessageAmount = unreadMessageAmount,
    lastReadMessageId = "0",
    isFullyLoaded = true,
)

fun conferenceWithMessage(conference: LocalConference) = ConferenceWithMessage(conference, null)

fun localMessage(
    id: Long,
    conferenceId: Long,
    username: String,
    userId: String = "u$id",
    message: String = "Message body $id",
) = LocalMessage(
    id = id,
    conferenceId = conferenceId,
    userId = userId,
    username = username,
    message = message,
    action = MessageAction.NONE,
    date = Instant.ofEpochMilli(0L),
    device = Device.MOBILE,
)

/** getConferencesLiveData returns LiveData<List<ConferenceWithMessage>>; MutableLiveData satisfies it. */
fun stubConferences(dao: MessengerDao, conferences: List<ConferenceWithMessage>) {
    every { dao.getConferencesLiveData(any()) } returns MutableLiveData(conferences)
}

/** getConferenceLiveData(id) returns LiveData<LocalConference?> -- the MessengerScreen header. */
fun stubConference(dao: MessengerDao, conferenceId: Long, conference: LocalConference) {
    every { dao.getConferenceLiveData(conferenceId) } returns MutableLiveData(conference)
}

/**
 * getMessagesLiveDataForConference is an OPEN concrete method returning MediatorLiveData; a relaxed mock
 * replaces its body, so it must be stubbed directly (its abstract helpers are inert). MediatorLiveData extends
 * MutableLiveData, so setting .value gives the observer a value once the LiveData becomes active.
 */
fun stubMessages(dao: MessengerDao, conferenceId: Long, messages: List<LocalMessage>) {
    every { dao.getMessagesLiveDataForConference(conferenceId) } returns
        MediatorLiveData<List<LocalMessage>>().apply { value = messages }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew assembleDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`.

The constructors were extracted from source and the 5.4.0 jar; if any arg name/order is rejected, read `src/main/kotlin/me/proxer/app/chat/prv/LocalConference.kt`, `LocalMessage.kt`, and `ConferenceWithMessage.kt`, correct, and report.

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/base/MessengerDaoFixtures.kt
git commit -m "test: add MessengerDao stub + entity fixtures for Room-backed chat tests"
```

---

### Task 3: MessengerScreen test

**Goal:** One test asserting `MessengerScreen` (private chat detail) renders a message's username from the stubbed DAO.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/chat/prv/message/MessengerScreenTest.kt`

**Acceptance Criteria:**
- [ ] Launches `PrvMessengerActivity.getIntent(context, conference)` with a `LocalConference` (detail mode, not the list)
- [ ] Stubs `getMessagesLiveDataForConference(conference.id)` non-empty and `getConferenceLiveData(conference.id)`
- [ ] Asserts `message.username` (the body is a `BBCodeView` AndroidView, unmatchable)
- [ ] `stubWorkManagerIdle` in `@Before`

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.chat.prv.message.MessengerScreenTest` → `BUILD SUCCESSFUL`, 1 test passed

**Steps:**

- [ ] **Step 1: Write the test**

Create `src/androidTest/kotlin/me/proxer/app/chat/prv/message/MessengerScreenTest.kt`:

```kotlin
package me.proxer.app.chat.prv.message

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.localConference
import me.proxer.app.base.localMessage
import me.proxer.app.base.stubConference
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.base.stubMessages
import me.proxer.app.base.stubWorkManagerIdle
import me.proxer.app.chat.prv.PrvMessengerActivity
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.util.extension.safeInject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PrvMessengerActivity renders the detail (MessengerScreen) only when a CONFERENCE_EXTRA LocalConference
 * parcelable is present -- a bare launch renders the conference list. The message body is a BBCodeView
 * AndroidView with no Compose semantics, so assert message.username: a real Compose Text shown only for
 * non-own messages. stubLoggedIn leaves storageHelper.user a relaxed mock (id ""), which differs from the
 * fixture's userId, so the message is non-own and the username renders.
 */
@RunWith(AndroidJUnit4::class)
class MessengerScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val messengerDao: MessengerDao by safeInject()
    private val workManager: WorkManager by safeInject()

    private val conference = localConference(1L, "Some topic")

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
        stubWorkManagerIdle(workManager)

        stubConference(messengerDao, conference.id, conference)
        // Non-empty so the dataSource observer publishes content and never hits the enqueueMessageLoad-on-empty
        // path (which would evaluate MessengerWorker on a page-0 load).
        stubMessages(messengerDao, conference.id, listOf(localMessage(1L, conference.id, "User Alpha")))
    }

    @Test
    fun success_renders_message_username() {
        val intent = PrvMessengerActivity.getIntent(context, conference)

        ActivityScenario.launch<PrvMessengerActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("User Alpha").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("User Alpha").assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.chat.prv.message.MessengerScreenTest`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

Watch for a first-frame NPE like Group 3's ConferenceInfoScreen (a screen doing `data!!` in a `ContentScreen` content lambda before `load()` runs). If it crashes with `NullPointerException` in `MessengerScreen`, that is a real production bug — **report it, do not weaken the test**; the coordinator decides the fix. If the username times out, confirm the message rendered as non-own (check `MessengerScreen.kt`'s `isOwnMessage`); report what you observe rather than switching to assert the BBCodeView body.

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/chat/prv/message/MessengerScreenTest.kt
git commit -m "test: add MessengerScreen instrumented smoke test"
```

---

### Task 4: ConferenceScreen tests (populated + empty state)

**Goal:** Two tests — the conference list renders a topic when populated, and the empty-state message when the DAO is empty (the `areConferencesSynchronized` gate).

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/chat/prv/conference/ConferenceScreenTest.kt`

**Acceptance Criteria:**
- [ ] Populated test: bare `PrvMessengerActivity` launch, stub non-empty conferences, assert `conference.topic`
- [ ] Empty-state test: stub `areConferencesSynchronized = true` + empty conferences, assert `R.string.error_no_data_conferences`
- [ ] `stubWorkManagerIdle` in `@Before` (ConferenceViewModel evaluates `isRunning` on every load)

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.chat.prv.conference.ConferenceScreenTest` → `BUILD SUCCESSFUL`, 2 tests passed

**Steps:**

- [ ] **Step 1: Write the tests**

The empty-state test is the group's one deep test: without `areConferencesSynchronized = true`, an empty conference emission is dropped, `data` stays null, and the spinner never clears — a timeout, not a clean failure.

Create `src/androidTest/kotlin/me/proxer/app/chat/prv/conference/ConferenceScreenTest.kt`:

```kotlin
package me.proxer.app.chat.prv.conference

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import io.mockk.every
import me.proxer.app.R
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.conferenceWithMessage
import me.proxer.app.base.localConference
import me.proxer.app.base.stubConferences
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.base.stubWorkManagerIdle
import me.proxer.app.chat.prv.PrvMessengerActivity
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.util.extension.safeInject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A bare PrvMessengerActivity launch (no extras) renders ConferenceScreen -- the conference list. Content is
 * Room-driven (dataSingle is Single.never()); all text is plain Compose Text, so the topic is directly
 * assertable. The empty-state test exercises the areConferencesSynchronized gate: an empty emission is
 * dropped unless that flag is true, leaving an infinite spinner.
 */
@RunWith(AndroidJUnit4::class)
class ConferenceScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val messengerDao: MessengerDao by safeInject()
    private val workManager: WorkManager by safeInject()

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
        stubWorkManagerIdle(workManager)
    }

    @Test
    fun populated_renders_conference_topic() {
        stubConferences(messengerDao, listOf(conferenceWithMessage(localConference(1L, "Topic Alpha"))))

        ActivityScenario.launch(PrvMessengerActivity::class.java).use {
            awaitText("Topic Alpha")
        }
    }

    @Test
    fun empty_state_renders_no_data_message() {
        // Without areConferencesSynchronized = true, the empty emission is dropped and the spinner never clears.
        every { storageHelper.areConferencesSynchronized } returns true
        stubConferences(messengerDao, emptyList())

        ActivityScenario.launch(PrvMessengerActivity::class.java).use {
            awaitText(context.getString(R.string.error_no_data_conferences))
        }
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.chat.prv.conference.ConferenceScreenTest`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

If `populated_renders_conference_topic` times out, `stubWorkManagerIdle` may not be defusing `isRunning` — check logcat for a `ClassCastException` in `MessengerWorker`. If `empty_state_renders_no_data_message` times out, confirm `areConferencesSynchronized` is stubbed true (an infinite spinner is the exact failure mode of that gate).

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/chat/prv/conference/ConferenceScreenTest.kt
git commit -m "test: add ConferenceScreen instrumented populated and empty-state tests"
```

---

### Task 5: ChatContainerScreen tests (both tabs)

**Goal:** Two tests — the default ChatRoomList tab (API-backed) renders a room name, and the ConferenceList tab (Room-backed) renders a conference topic after switching.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/chat/ChatContainerScreenTest.kt`

**Acceptance Criteria:**
- [ ] Launches via `MainActivity.getSectionIntent(context, DrawerItem.CHAT)` with `grantStoragePermission()`
- [ ] Both tests stub `api.chat.publicRooms()` AND `api.chat.userRooms()` (the logged-in zip) — tab 0 loads by default
- [ ] Tab-0 test asserts `room.name`; tab-1 test switches via `switchToTab("Privat")` and asserts `conference.topic`
- [ ] `stubWorkManagerIdle` in `@Before` (tab 1's ConferenceList evaluates `isRunning`)

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.chat.ChatContainerScreenTest` → `BUILD SUCCESSFUL`, 2 tests passed

**Steps:**

- [ ] **Step 1: Write the tests**

Create `src/androidTest/kotlin/me/proxer/app/chat/ChatContainerScreenTest.kt`:

```kotlin
package me.proxer.app.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.MainActivity
import me.proxer.app.R
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.conferenceWithMessage
import me.proxer.app.base.grantStoragePermission
import me.proxer.app.base.localConference
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubConferences
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.base.stubWorkManagerIdle
import me.proxer.app.base.switchToTab
import me.proxer.app.util.extension.safeInject
import me.proxer.app.util.wrapper.DrawerItem
import me.proxer.library.api.chat.PublicChatRoomsEndpoint
import me.proxer.library.api.chat.UserChatRoomsEndpoint
import me.proxer.library.entity.chat.ChatRoom
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ChatContainerScreen is a MainActivity drawer section (DrawerItem.CHAT) with a SecondaryTabRow. Tab 0
 * (ChatRoomList) is API-backed and is the DEFAULT tab, so landing on ChatContainer immediately calls
 * api.chat.publicRooms(); ChatRoomViewModel zips api.chat.userRooms() when logged in, so BOTH must be stubbed
 * or the tab hangs. Tab 1 (ConferenceList) is Room-backed, the embedded twin of ConferenceScreen.
 */
@RunWith(AndroidJUnit4::class)
class ChatContainerScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val workManager: WorkManager by safeInject()
    private val messengerDao: me.proxer.app.chat.prv.sync.MessengerDao by safeInject()

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun launchChat() = ActivityScenario.launch<MainActivity>(
        MainActivity.getSectionIntent(context, DrawerItem.CHAT),
    )

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
        grantStoragePermission()
        stubWorkManagerIdle(workManager)

        // Tab 0 (ChatRoomList) is the default tab and loads on landing. ChatRoomViewModel zips public+user when
        // logged in, so both endpoints must resolve or the zip never emits.
        val publicEndpoint = mockk<PublicChatRoomsEndpoint>(relaxed = true)
        val userEndpoint = mockk<UserChatRoomsEndpoint>(relaxed = true)

        every { api.chat.publicRooms() } returns publicEndpoint
        every { api.chat.userRooms() } returns userEndpoint
        every { publicEndpoint.build() } returns
            mockProxerCall(listOf(ChatRoom("1", "Room Alpha", "Topic", false)))
        every { userEndpoint.build() } returns mockProxerCall(emptyList<ChatRoom>())
    }

    @Test
    fun public_tab_renders_chat_room_name() {
        launchChat().use {
            awaitText("Room Alpha")
        }
    }

    @Test
    fun private_tab_renders_conference_topic() {
        stubConferences(messengerDao, listOf(conferenceWithMessage(localConference(1L, "Topic Alpha"))))

        launchChat().use {
            composeTestRule.switchToTab(context.getString(R.string.fragment_chat_container_private))

            awaitText("Topic Alpha")
        }
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.chat.ChatContainerScreenTest`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

If `public_tab_renders_chat_room_name` times out, the zip likely didn't emit — confirm both `publicRooms` and `userRooms` are stubbed. If `private_tab_renders_conference_topic` times out on the switch, `switchToTab` may have matched the wrong node (both tab labels are short German words); verify with `onAllNodesWithText("Privat").fetchSemanticsNodes().size`. If the introduction flow renders instead of ChatContainer, the section intent's action isn't suppressing it (it should — `getSectionIntent` sets a custom action).

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/chat/ChatContainerScreenTest.kt
git commit -m "test: add ChatContainerScreen instrumented tab tests"
```

---

### Task 6: Full-suite verification

**Goal:** Confirm the whole instrumented suite is green with Group 4 added, and record the wall-clock.

**Files:**
- None (verification only)

**Acceptance Criteria:**
- [ ] `./gradlew connectedDebugAndroidTest` passes with **58 tests** (53 existing + 5 new), 0 failed
- [ ] The 53 existing tests still pass
- [ ] Wall-clock recorded and compared against the ~56s Group 3 baseline

**Verify:** `./gradlew connectedDebugAndroidTest` → `BUILD SUCCESSFUL`, 58 tests, 0 failed

**Steps:**

- [ ] **Step 1: Run the full suite with timing**

```bash
start=$(date +%s)
./gradlew connectedDebugAndroidTest 2>&1 | tail -20
echo "WALL_CLOCK_SECONDS=$(( $(date +%s) - start ))"
```

Expected: `BUILD SUCCESSFUL`, `Starting 58 tests`, 0 failed. `/usr/bin/time` is not installed — use the shell arithmetic.

- [ ] **Step 2: Report**

Report the test count, the wall-clock, and the delta against Group 3's ~56s. ~62s is expected (5 × ~1.06s + a MainActivity-hosted pair).

---

## Notes and Risks

**No production changes are anticipated**, but the plan is prepared for one. Group 3's ConferenceInfoScreen NPE was found by its first instrumented test; if MessengerScreen or a ConferenceScreen has an analogous first-frame `data!!` crash, Task 3/4's Step 2 says to report it (not weaken the test), and the coordinator applies the minimal fix under review — exactly as Group 3 did.

**`stubWorkManagerIdle` is the one genuinely new piece of infrastructure and is unproven until Task 3/4 run.** If a Room test still crashes with a `ClassCastException` in `MessengerWorker.isRunning`, the fixture's future stub isn't taking effect — check that `workManager` is resolved via `safeInject()` (the same Koin instance the worker's companion injects) before diagnosing anything else.

**The `MediatorLiveData` stub for `getMessagesLiveDataForConference`** must return a `MediatorLiveData` (not a plain `MutableLiveData`) because the DAO method's declared return type is `MediatorLiveData<List<LocalMessage>>`. Setting `.value` is enough — no sources needed — because the VM observes it and reads the current value when it becomes active.

**detekt is red on master** — 4 pre-existing `ErrorUtils.kt:235` violations, unrelated. Out of scope.
