# Instrumented UI Tests — Group 3 (Intent-Seam Screens) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Instrumented smoke tests for Industry (2 tabs), TranslatorGroup (2 tabs), ChatActivity, ChatRoomInfo and ConferenceInfo — 7 tests across 5 files — plus the 5 public `getIntent` factories those Activities need.

**Architecture:** One production task adds `getIntent(Context, …)` to the five Activities (each `navigateTo` delegates to it), following the `ProfileActivity.getIntent` convention from Group 2. Then five test files, each extending `InstrumentedTestBase`, stubbing its endpoint on the shared relaxed `ProxerApi` Koin mock, launching via the new factory, and asserting body content with `createEmptyComposeRule()` + `waitUntil`. All fixtures come verbatim from the JVM `*ViewModelTest`s or the 5.4.0 jar.

**Tech Stack:** Kotlin, Compose UI test (`androidx.compose.ui.test.junit4.v2`), MockK (`mockk-android`), Koin, RxJava 2, JUnit4 + `AndroidJUnit4`.

**Spec:** `docs/superpowers/specs/2026-07-16-instrumented-tests-group3-design.md`

---

## Shared Context — read before Task 1

**Environment.** Instrumented tests require **API 31+** (CLAUDE.md). AVD `ci_api31_x86_64` matches CI. `connectedDebugAndroidTest` does **NOT** accept `--tests`; filter with `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.

**Harness rules inherited from Groups 1+2** (all apply):
- Extend `InstrumentedTestBase`; don't re-inject `api`/`storageHelper`/`preferenceHelper`/`validators`. It clears stubs between tests.
- `stubLoggedIn(storageHelper, preferenceHelper)` in `@Before` — **every Group 3 screen is login-gated**, so without it every screen renders the login error.
- `mockProxerCall(value)` from `me.proxer.app.base.ProxerCallFixtures` for the `ProxerCall` mock (stubs `clone()` + `safeExecute()`). All Group 3 endpoints are non-null.
- `switchToTab(label)` from `me.proxer.app.base.TabFixtures` for the Projects tab.
- No `grantStoragePermission()` — none of these is MainActivity-hosted.
- No `mockkObject(TextPrototype)` — on-device `android.text` is real and `TestApplication` installs the emoji provider.
- Timeout `5_000`, matching Group 2's own-activity screens. Raise only with a measurement + comment.
- All UI strings are German; resolve with `context.getString(...)`.

**Never assert a toolbar title.** Every Group 3 screen's title is intent-extra-derived (Industry/TranslatorGroup fall back to the `name` extra; Chat/ChatRoomInfo use `chatRoomName`; ConferenceInfo uses `conference.topic`). Asserting it proves nothing about the fetch. Assert body content.

**Verbatim constructors (from the 5.4.0 jar — `../ProxerLibJava` is not checked out):**
- `Industry(id: String, name: String, type: IndustryType, country: Country, link: HttpUrl, description: String)`
- `TranslatorGroup(id: String, name: String, country: Country, image: String, link: HttpUrl, description: String, clicks: Int, projectAmount: Int)`
- `IndustryProject(id, name, genres: Set<String>, fskConstraints: Set<FskConstraint>, medium: Medium, type: IndustryType, state: MediaState, ratingSum: Int, ratingAmount: Int)`
- `TranslatorGroupProject(id, name, genres: Set<String>, fskConstraints: Set<FskConstraint>, medium: Medium, state: ProjectState, mediaState: MediaState, ratingSum: Int, ratingAmount: Int)` — **slot 6 is `ProjectState`, not `IndustryType`**
- `ChatMessage(id: String, userId: String, username: String, image: String, message: String, action: ChatMessageAction, date: Date)`
- `ChatRoomUser(id: String, name: String, image: String, status: String, isModerator: Boolean)`
- `ConferenceInfo(topic: String, participantAmount: Int, firstMessageTime: Date, lastMessageTime: Date, leaderId: String, participants: List<ConferenceParticipant>)`
- `ConferenceParticipant(id: String, image: String, username: String, status: String)`
- `LocalConference` — 13 fields, see Task 6.

---

### Task 1: Add 5 getIntent factories

**Goal:** Give the five intent-seam Activities public `getIntent(Context, …)` factories, each delegating from `navigateTo`, so tests can build their intents without hardcoding private extra keys.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/info/industry/IndustryActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/chat/pub/message/ChatActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/chat/pub/room/info/ChatRoomInfoActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/conference/info/ConferenceInfoActivity.kt`

**Acceptance Criteria:**
- [ ] Each Activity has a public `getIntent(context: Context, …): Intent` matching the spec's signature table
- [ ] Each existing `navigateTo` delegates to its `getIntent` rather than building the intent inline
- [ ] `navigateTo` signatures and behaviour (including any `Activity`-typed param) are unchanged
- [ ] `./gradlew compileDebugKotlin` passes

**Verify:** `./gradlew compileDebugKotlin` → `BUILD SUCCESSFUL`

**Steps:**

- [ ] **Step 1: Read one existing example and each target**

Read `src/main/kotlin/me/proxer/app/profile/ProfileActivity.kt`'s companion (added in Group 2) as the reference for the pattern: a public `getIntent(context: Context, …) = context.intentFor<Activity>(KEY to value, …)`, with `navigateTo` calling it. Then read each of the five target Activities' companion objects to see their current `navigateTo` and private extra-key constants.

- [ ] **Step 2: Add getIntent to each, delegate navigateTo**

For each Activity, add the factory using its existing private key constants, and rewrite `navigateTo` to delegate. Add `import android.content.Context` / `import android.content.Intent` if absent. The five factories:

`IndustryActivity` (keys `ID_EXTRA="id"`, `NAME_EXTRA="name"`):
```kotlin
fun getIntent(context: Context, id: String, name: String? = null): Intent =
    context.intentFor<IndustryActivity>(
        ID_EXTRA to id,
        NAME_EXTRA to name,
    )
```

`TranslatorGroupActivity` (same keys): identical shape with `<TranslatorGroupActivity>`.

`ChatActivity` (keys `CHAT_ROOM_ID_EXTRA="chat_room_id"`, `CHAT_ROOM_NAME_EXTRA="chat_room_name"`, `CHAT_ROOM_IS_READ_ONLY_EXTRA="chat_room_is_read_only"`):
```kotlin
fun getIntent(
    context: Context,
    chatRoomId: String,
    chatRoomName: String,
    chatRoomIsReadOnly: Boolean = false,
): Intent =
    context.intentFor<ChatActivity>(
        CHAT_ROOM_ID_EXTRA to chatRoomId,
        CHAT_ROOM_NAME_EXTRA to chatRoomName,
        CHAT_ROOM_IS_READ_ONLY_EXTRA to chatRoomIsReadOnly,
    )
```

`ChatRoomInfoActivity` (keys `CHAT_ROOM_ID_EXTRA`, `CHAT_ROOM_NAME_EXTRA`):
```kotlin
fun getIntent(context: Context, chatRoomId: String, chatRoomName: String): Intent =
    context.intentFor<ChatRoomInfoActivity>(
        CHAT_ROOM_ID_EXTRA to chatRoomId,
        CHAT_ROOM_NAME_EXTRA to chatRoomName,
    )
```

`ConferenceInfoActivity` (key `CONFERENCE_EXTRA="conference"`):
```kotlin
fun getIntent(context: Context, conference: LocalConference): Intent =
    context.intentFor<ConferenceInfoActivity>(
        CONFERENCE_EXTRA to conference,
    )
```

In each, rewrite the body of `navigateTo` to call `getIntent(context, …)` and launch/transition the returned intent, changing only *where the intent is built*. Match how `ProfileActivity.navigateTo` delegates. If any `navigateTo` uses `startActivity<Activity>(...)` inline, replace with `context.startActivity(getIntent(context, …))`.

`intentFor` is the existing extension in `src/main/kotlin/me/proxer/app/util/extension/AndroidExtensions.kt` — the same one `getIntent`/`navigateTo` already use.

- [ ] **Step 3: Verify compile**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Confirm no navigateTo behaviour change**

Re-read each rewritten `navigateTo`: same parameters, same launch/transition call, only the intent construction now goes through `getIntent`. These are production files — a regression breaks real navigation.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/info/industry/IndustryActivity.kt \
        src/main/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupActivity.kt \
        src/main/kotlin/me/proxer/app/chat/pub/message/ChatActivity.kt \
        src/main/kotlin/me/proxer/app/chat/pub/room/info/ChatRoomInfoActivity.kt \
        src/main/kotlin/me/proxer/app/chat/prv/conference/info/ConferenceInfoActivity.kt
git commit -m "feat: add getIntent factories to five intent-seam activities"
```

---

### Task 2: IndustryScreen tab tests

**Goal:** Two tests — the Info tab renders the fetched industry's description, the Projects tab renders a project's name.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/info/industry/IndustryScreenTest.kt`

**Acceptance Criteria:**
- [ ] Info-tab test asserts `industry.description` (fetch-derived, not the toolbar title)
- [ ] Projects-tab test does `switchToTab("Projekte")` then asserts `project.name`
- [ ] Both `IndustryEndpoint` and `IndustryProjectListEndpoint` stubbed in `@Before` (the Projects page composes lazily; `animateScrollToPage` transiently composes the neighbour)
- [ ] Launches via `IndustryActivity.getIntent`

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.info.industry.IndustryScreenTest` → `BUILD SUCCESSFUL`, 2 tests passed

**Steps:**

- [ ] **Step 1: Write the test file**

Create `src/androidTest/kotlin/me/proxer/app/info/industry/IndustryScreenTest.kt`:

```kotlin
package me.proxer.app.info.industry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.base.switchToTab
import me.proxer.library.api.info.IndustryEndpoint
import me.proxer.library.api.list.IndustryProjectListEndpoint
import me.proxer.library.entity.info.Industry
import me.proxer.library.entity.list.IndustryProject
import me.proxer.library.enums.Country
import me.proxer.library.enums.FskConstraint
import me.proxer.library.enums.IndustryType
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IndustryScreen hosts two lazily-composed tabs (Info, Projects). Both endpoints are stubbed in @Before
 * because animateScrollToPage transiently composes the neighbour page, and an unstubbed relaxed-mock endpoint
 * would ClassCastException and kill the process. The Info test asserts on launch; the Projects test switches
 * first. Toolbar title is never asserted -- it falls back to the `name` intent extra.
 */
@RunWith(AndroidJUnit4::class)
class IndustryScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val industryId = "321"

    // ratingSum/ratingAmount 0/0 suppresses the rating row so it can't collide with assertions.
    private fun project(id: String, name: String) = IndustryProject(
        id,
        name,
        setOf("Action"),
        setOf(FskConstraint.FSK_0),
        Medium.ANIMESERIES,
        IndustryType.STUDIO,
        MediaState.FINISHED,
        0,
        0,
    )

    private fun launchIndustry() = ActivityScenario.launch<IndustryActivity>(
        IndustryActivity.getIntent(context, industryId),
    )

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        val infoEndpoint = mockk<IndustryEndpoint>(relaxed = true)

        every { api.info.industry(industryId) } returns infoEndpoint
        every { infoEndpoint.build() } returns mockProxerCall(
            Industry(
                industryId,
                "Studio Alpha",
                IndustryType.STUDIO,
                Country.JAPAN,
                "https://proxer.me".toHttpUrl(),
                "Industry Description Alpha",
            ),
        )

        val projectsEndpoint = mockk<IndustryProjectListEndpoint>(relaxed = true)

        every { api.list.industryProjectList(industryId) } returns projectsEndpoint
        every { projectsEndpoint.includeHentai(any()) } returns projectsEndpoint
        every { projectsEndpoint.page(any()) } returns projectsEndpoint
        every { projectsEndpoint.limit(any()) } returns projectsEndpoint
        every { projectsEndpoint.build() } returns mockProxerCall(listOf(project("p0", "Project Alpha")))
    }

    @Test
    fun info_tab_renders_industry_description() {
        launchIndustry().use {
            // description is rendered as plain Compose Text (not the toolbar title, which is intent-extra).
            awaitText("Industry Description Alpha")
        }
    }

    @Test
    fun projects_tab_renders_project_name() {
        launchIndustry().use {
            composeTestRule.switchToTab(context.getString(R.string.section_industry_projects))

            awaitText("Project Alpha")
        }
    }
}
```

The test uses `R.string.section_industry_projects`, so add `import me.proxer.app.R` to the import list (the test package is `me.proxer.app.info.industry`, not `me.proxer.app`, so `R` must be imported — as `MediaScreenTest` does).

- [ ] **Step 2: Confirm the tab-label resource**

Read `src/main/kotlin/me/proxer/app/info/industry/IndustryScreen.kt` around the `tabs = listOf(...)` declaration and confirm the Projects tab label resource is `R.string.section_industry_projects` (value "Projekte" in `strings.xml`). If the resource name differs, correct the test and say so. (Recall Group 2's `section_episodes` did not exist — verify, don't assume.)

- [ ] **Step 3: Run**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.info.industry.IndustryScreenTest`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

If the Info test times out, the description may be gated on non-blank (it is — fixture uses a non-blank string) or the id mismatched between `getIntent` and the stub. If the Projects test times out, `switchToTab` matched the wrong node or a Projects builder is unstubbed.

- [ ] **Step 4: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/info/industry/IndustryScreenTest.kt
git commit -m "test: add IndustryScreen instrumented tab tests"
```

---

### Task 3: TranslatorGroupScreen tab tests

**Goal:** Two tests — the Info tab renders the fetched group's description, the Projects tab renders a project's name. Structural twin of Task 2, but the entity constructors differ.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupScreenTest.kt`

**Acceptance Criteria:**
- [ ] Info-tab test asserts `group.description`
- [ ] Projects-tab test does `switchToTab("Projekte")` then asserts `project.name`
- [ ] Both endpoints stubbed in `@Before`
- [ ] `TranslatorGroupProject`'s slot-6 arg is `ProjectState` (not `IndustryType`)
- [ ] Launches via `TranslatorGroupActivity.getIntent`

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.info.translatorgroup.TranslatorGroupScreenTest` → `BUILD SUCCESSFUL`, 2 tests passed

**Steps:**

- [ ] **Step 1: Write the test file**

Create `src/androidTest/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupScreenTest.kt`:

```kotlin
package me.proxer.app.info.translatorgroup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.base.switchToTab
import me.proxer.library.api.info.TranslatorGroupEndpoint
import me.proxer.library.api.list.TranslatorGroupProjectListEndpoint
import me.proxer.library.entity.info.TranslatorGroup
import me.proxer.library.entity.list.TranslatorGroupProject
import me.proxer.library.enums.Country
import me.proxer.library.enums.FskConstraint
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import me.proxer.library.enums.ProjectState
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Twin of IndustryScreenTest. Two lazily-composed tabs, both endpoints stubbed in @Before, no toolbar-title
 * assertion. The one shape difference: TranslatorGroupProject's slot-6 arg is ProjectState, where
 * IndustryProject has IndustryType.
 */
@RunWith(AndroidJUnit4::class)
class TranslatorGroupScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val groupId = "654"

    private fun project(id: String, name: String) = TranslatorGroupProject(
        id,
        name,
        setOf("Action"),
        setOf(FskConstraint.FSK_0),
        Medium.ANIMESERIES,
        ProjectState.ONGOING,
        MediaState.FINISHED,
        0,
        0,
    )

    private fun launchGroup() = ActivityScenario.launch<TranslatorGroupActivity>(
        TranslatorGroupActivity.getIntent(context, groupId),
    )

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        val infoEndpoint = mockk<TranslatorGroupEndpoint>(relaxed = true)

        every { api.info.translatorGroup(groupId) } returns infoEndpoint
        every { infoEndpoint.build() } returns mockProxerCall(
            TranslatorGroup(
                groupId,
                "Group Alpha",
                Country.GERMANY,
                "group.png",
                "https://proxer.me".toHttpUrl(),
                "Group Description Alpha",
                1000,
                12,
            ),
        )

        val projectsEndpoint = mockk<TranslatorGroupProjectListEndpoint>(relaxed = true)

        every { api.list.translatorGroupProjectList(groupId) } returns projectsEndpoint
        every { projectsEndpoint.includeHentai(any()) } returns projectsEndpoint
        every { projectsEndpoint.page(any()) } returns projectsEndpoint
        every { projectsEndpoint.limit(any()) } returns projectsEndpoint
        every { projectsEndpoint.build() } returns mockProxerCall(listOf(project("p0", "Project Alpha")))
    }

    @Test
    fun info_tab_renders_group_description() {
        launchGroup().use {
            awaitText("Group Description Alpha")
        }
    }

    @Test
    fun projects_tab_renders_project_name() {
        launchGroup().use {
            composeTestRule.switchToTab(context.getString(R.string.section_translator_group_projects))

            awaitText("Project Alpha")
        }
    }
}
```

The test uses `R.string.section_translator_group_projects`, so add `import me.proxer.app.R` to the import list (same reason as Task 2).

- [ ] **Step 2: Confirm the tab-label resource**

Read `src/main/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupScreen.kt`'s `tabs = listOf(...)` and confirm the Projects label resource is `R.string.section_translator_group_projects`. Correct and report if it differs.

- [ ] **Step 3: Run**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.info.translatorgroup.TranslatorGroupScreenTest`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 4: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupScreenTest.kt
git commit -m "test: add TranslatorGroupScreen instrumented tab tests"
```

---

### Task 4: ChatScreen test

**Goal:** One test asserting `ChatScreen` renders a message's username from a stubbed `ChatMessagesEndpoint`.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/chat/pub/message/ChatScreenTest.kt`

**Acceptance Criteria:**
- [ ] Launches `ChatActivity` via `getIntent(context, chatRoomId, chatRoomName)`
- [ ] Asserts `message.username` (the body renders in a `BBCodeView` AndroidView and is unmatchable)
- [ ] `ChatMessage` id is numeric-parseable (`ChatViewModel` calls `id.toLong()`)
- [ ] Endpoint stubbed with `messageId(any())` returning the endpoint, plus `build()`

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.chat.pub.message.ChatScreenTest` → `BUILD SUCCESSFUL`, 1 test passed

**Steps:**

- [ ] **Step 1: Write the test**

Create `src/androidTest/kotlin/me/proxer/app/chat/pub/message/ChatScreenTest.kt`:

```kotlin
package me.proxer.app.chat.pub.message

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.library.api.chat.ChatMessagesEndpoint
import me.proxer.library.entity.chat.ChatMessage
import me.proxer.library.enums.ChatMessageAction
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * The message BODY renders through a BBCodeView AndroidView with no Compose semantics, so assert the username.
 * ChatViewModel polls api.chat.messages every 3s; the stub is idempotent so repeated calls are harmless -- do
 * not write verify(exactly = 1). ChatMessage id must be numeric-parseable: ChatViewModel calls id.toLong().
 */
@RunWith(AndroidJUnit4::class)
class ChatScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val chatRoomId = "room-1"

    private fun message(id: String, username: String) = ChatMessage(
        id,
        "user-$id",
        username,
        "image.png",
        "Message body $id",
        ChatMessageAction.NONE,
        Date(),
    )

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        val endpoint = mockk<ChatMessagesEndpoint>(relaxed = true)

        every { api.chat.messages(chatRoomId) } returns endpoint
        every { endpoint.messageId(any()) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(listOf(message("0", "User Alpha")))
    }

    @Test
    fun success_renders_message_username() {
        val intent = ChatActivity.getIntent(context, chatRoomId, "Room Name")

        ActivityScenario.launch<ChatActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("User Alpha").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("User Alpha").assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.chat.pub.message.ChatScreenTest`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

If it times out: the username only renders for non-own messages (`message.userId != myUserId`); logged-in `stubLoggedIn` leaves `storageHelper.user` as a relaxed mock — confirm the username still shows. If not, the message may be treated as own; report it. (The JVM test renders usernames fine with a logged-in stub, so this should hold.)

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/chat/pub/message/ChatScreenTest.kt
git commit -m "test: add ChatScreen instrumented smoke test"
```

---

### Task 5: ChatRoomInfoScreen test

**Goal:** One test asserting `ChatRoomInfoScreen` renders a room user's name from a stubbed `ChatRoomUsersEndpoint`.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/chat/pub/room/info/ChatRoomInfoScreenTest.kt`

**Acceptance Criteria:**
- [ ] Launches `ChatRoomInfoActivity` via `getIntent(context, chatRoomId, chatRoomName)`
- [ ] Asserts `user.name` (a real Compose Text, fetch-derived)
- [ ] Endpoint stubbed non-paging

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.chat.pub.room.info.ChatRoomInfoScreenTest` → `BUILD SUCCESSFUL`, 1 test passed

**Steps:**

- [ ] **Step 1: Write the test**

Create `src/androidTest/kotlin/me/proxer/app/chat/pub/room/info/ChatRoomInfoScreenTest.kt`:

```kotlin
package me.proxer.app.chat.pub.room.info

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.library.api.chat.ChatRoomUsersEndpoint
import me.proxer.library.entity.chat.ChatRoomUser
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * user.name and user.status are plain Compose Text and fully fetch-derived. The toolbar title is the
 * chatRoomName intent extra -- vacuous, not asserted. ChatRoomInfoViewModel polls every 10s; stub is idempotent.
 */
@RunWith(AndroidJUnit4::class)
class ChatRoomInfoScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val chatRoomId = "555"

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        val endpoint = mockk<ChatRoomUsersEndpoint>(relaxed = true)

        every { api.chat.roomUsers(chatRoomId) } returns endpoint
        every { endpoint.build() } returns mockProxerCall(
            listOf(ChatRoomUser("1", "User Alpha", "image.png", "online", false)),
        )
    }

    @Test
    fun success_renders_room_user_name() {
        val intent = ChatRoomInfoActivity.getIntent(context, chatRoomId, "Room Name")

        ActivityScenario.launch<ChatRoomInfoActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("User Alpha").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("User Alpha").assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.chat.pub.room.info.ChatRoomInfoScreenTest`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/chat/pub/room/info/ChatRoomInfoScreenTest.kt
git commit -m "test: add ChatRoomInfoScreen instrumented smoke test"
```

---

### Task 6: ConferenceInfoScreen test

**Goal:** One test asserting `ConferenceInfoScreen` renders a participant's username from a stubbed `ConferenceInfoEndpoint`, using a non-empty participant list.

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/chat/prv/conference/info/ConferenceInfoScreenTest.kt`

**Acceptance Criteria:**
- [ ] Launches `ConferenceInfoActivity` via `getIntent(context, conference)` with a `LocalConference` whose `id` matches the stubbed `conferenceInfo(id)`
- [ ] Stubs a `ConferenceInfo` with one `ConferenceParticipant` and asserts its `username` (the JVM fixture uses empty participants — this is new)
- [ ] Does NOT assert the toolbar title (`conference.topic`, intent-extra-derived)

**Verify:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.chat.prv.conference.info.ConferenceInfoScreenTest` → `BUILD SUCCESSFUL`, 1 test passed

**Steps:**

- [ ] **Step 1: Confirm the LocalConference constructor and the participant render**

Read `src/main/kotlin/me/proxer/app/chat/prv/LocalConference.kt` for the exact 13-field constructor, and `src/main/kotlin/me/proxer/app/chat/prv/conference/info/ConferenceInfoScreen.kt` to confirm (a) it passes `conference.id.toString()` as the VM's conferenceId, and (b) `ParticipantItem` renders `participant.username` as a Compose `Text`. The `LocalConference` fields (from the Group 2 `CreateConferenceViewModelTest` fixture) are: `id: Long, topic: String, customTopic: String, participantAmount: Int, image: String, imageType: String, isGroup: Boolean, localIsRead: Boolean, isRead: Boolean, date: org.threeten.bp.Instant, unreadMessageAmount: Int, lastReadMessageId: String, isFullyLoaded: Boolean`.

- [ ] **Step 2: Write the test**

Create `src/androidTest/kotlin/me/proxer/app/chat/prv/conference/info/ConferenceInfoScreenTest.kt`:

```kotlin
package me.proxer.app.chat.prv.conference.info

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.base.InstrumentedTestBase
import me.proxer.app.base.mockProxerCall
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.chat.prv.LocalConference
import me.proxer.library.api.messenger.ConferenceInfoEndpoint
import me.proxer.library.entity.messenger.ConferenceInfo
import me.proxer.library.entity.messenger.ConferenceParticipant
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.threeten.bp.Instant
import java.util.Date

/**
 * ConferenceInfoActivity requires a LocalConference parcelable; the screen passes conference.id.toString() as
 * the VM's conferenceId. The JVM test fixture uses participants = emptyList(), which would render only the
 * vacuous toolbar title (conference.topic), so this stubs a ConferenceInfo with one ConferenceParticipant and
 * asserts its username -- the only fetch-derived assertion available.
 */
@RunWith(AndroidJUnit4::class)
class ConferenceInfoScreenTest : InstrumentedTestBase() {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val conference = LocalConference(
        id = 777L,
        topic = "Some topic",
        customTopic = "",
        participantAmount = 1,
        image = "",
        imageType = "",
        isGroup = true,
        localIsRead = true,
        isRead = true,
        date = Instant.ofEpochMilli(0L),
        unreadMessageAmount = 0,
        lastReadMessageId = "0",
        isFullyLoaded = true,
    )

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)

        val endpoint = mockk<ConferenceInfoEndpoint>(relaxed = true)

        // The screen derives conferenceId from conference.id.toString() -> "777".
        every { api.messenger.conferenceInfo("777") } returns endpoint
        every { endpoint.build() } returns mockProxerCall(
            ConferenceInfo(
                "Some topic",
                1,
                Date(),
                Date(),
                "1",
                listOf(ConferenceParticipant("1", "image.png", "Participant Alpha", "online")),
            ),
        )
    }

    @Test
    fun success_renders_participant_username() {
        val intent = ConferenceInfoActivity.getIntent(context, conference)

        ActivityScenario.launch<ConferenceInfoActivity>(intent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("Participant Alpha").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Participant Alpha").assertIsDisplayed()
        }
    }
}
```

If Step 1 shows the `LocalConference` field order/names differ from the above, correct the fixture and say so.

- [ ] **Step 3: Run**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.chat.prv.conference.info.ConferenceInfoScreenTest`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

If it times out, confirm `ConferenceParticipant`'s argument order — the assertion depends on `username` being the **3rd** arg (`id, image, username, status`). If the wrong string renders, the order is off; fix and report.

- [ ] **Step 4: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/chat/prv/conference/info/ConferenceInfoScreenTest.kt
git commit -m "test: add ConferenceInfoScreen instrumented smoke test"
```

---

### Task 7: Full-suite verification

**Goal:** Confirm the whole instrumented suite is green with Group 3 added, and record the wall-clock.

**Files:**
- None (verification only)

**Acceptance Criteria:**
- [ ] `./gradlew connectedDebugAndroidTest` passes with **53 tests** (46 existing + 7 new), 0 failed
- [ ] The 46 existing tests (Groups 1+2 + POC + TV) still pass
- [ ] Wall-clock recorded and compared against the ~52s Group 2 baseline

**Verify:** `./gradlew connectedDebugAndroidTest` → `BUILD SUCCESSFUL`, 53 tests, 0 failed

**Steps:**

- [ ] **Step 1: Run the full suite with timing**

```bash
start=$(date +%s)
./gradlew connectedDebugAndroidTest 2>&1 | tail -20
echo "WALL_CLOCK_SECONDS=$(( $(date +%s) - start ))"
```

Expected: `BUILD SUCCESSFUL`, `Starting 53 tests`, 0 failed. `/usr/bin/time` is not installed — use the shell arithmetic.

- [ ] **Step 2: Report**

Report the test count, the wall-clock, and the delta against Group 2's ~52s. ~60s is expected (7 × ~1.1s added).

---

## Notes and Risks

**Task 1 is the only production change** — five additive `getIntent` factories. A regression there breaks real navigation, so Step 4 re-checks each `navigateTo` for behavioural equivalence. The five together are one commit.

**ConferenceParticipant argument order is the one fixture I could not confirm from a JVM test** — it was extracted from the 5.4.0 jar as `(id, image, username, status)`. Task 6 Step 3 flags the check: if the wrong string renders, the order is off. Everything else comes verbatim from a JVM `*ViewModelTest`.

**Tab-label resources for Industry/TranslatorGroup are verified in Steps 2 of Tasks 2/3** rather than trusted — Group 2's `section_episodes` guess did not exist, so each Projects-tab resource name is checked against the screen source before the run.

**detekt is red on master** (4 pre-existing `ErrorUtils.kt:235` violations) — unrelated, will block any CI gate that runs detekt. Out of scope.
