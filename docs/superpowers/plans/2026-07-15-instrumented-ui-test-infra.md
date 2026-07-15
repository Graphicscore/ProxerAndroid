# Instrumented UI Test Infra Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reusable harness for full-integration instrumented UI tests (real Activity, real Koin DI wired to mocked dependencies, real ViewModel) and prove it end-to-end on one screen (`NotificationScreen`).

**Architecture:** A custom `AndroidJUnitRunner` (`TestRunner`) swaps in a `TestApplication` that starts Koin with the real `viewModelModule` (now `internal`, not `private`) plus a new androidTest-only `fakeAppModule()` (mocked `ProxerApi`/`StorageHelper`/etc., mirroring the existing JVM `src/test` module) instead of the real production modules — so no real network/storage code ever runs in tests. Each test stubs the specific mocked calls it needs, then manually launches the Activity under test via `ActivityScenario` (not via the Compose rule's own activity-launch, which would race ahead of `@Before` stubbing) and asserts against it with `androidx.compose.ui.test.junit4.v2` APIs.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (BOM via `gradle/dependencies.gradle`), `androidx.compose.ui.test.junit4.v2` (already used by `TvSearchScreenTest` etc.), MockK (`mockk-android`, already an `androidTestImplementation` dependency), Koin 4.2.1.

## Global Constraints

- Always use `./gradlew`, never system `gradle` (7.6.3 incompatible with Java 21 JBR). Always run with the daemon on.
- Never run `./gradlew test*` concurrently on the same checkout — corrupts `build/test-results`; fix with `rm -rf build`.
- Filter instrumented tests with `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` (`--tests` is NOT accepted by `connectedDebugAndroidTest` on this AGP version, despite CLAUDE.md; confirmed empirically during Task 2 — CLAUDE.md needs correcting separately).
- `secrets.properties` must exist (gitignored). If missing for build-testing only: `echo "PROXER_API_KEY=dummy_build_key" > secrets.properties`. **Never overwrite an existing one.**
- Endpoints often return concrete subtypes (e.g. `NotificationsEndpoint`) — mock the concrete type, not `Endpoint<T>`.
- `BaseViewModel.isLoggedInObservable` uses `.skip(1)` — cold-start screens rely on the explicit `LaunchedEffect(Unit) { viewModel.load() }` call, not the reactive path, to surface a logged-out state.
- Running `connectedDebugAndroidTest` requires a connected device/emulator. CI already runs it via `reactivecircus/android-emulator-runner@v2` (api-level 30, x86_64) — no CI changes needed in this plan.
- Source root is `src/` (no `app/` subdir).

---

### Task 1: androidTest fake DI module

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/base/FakeAppModule.kt`

**Interfaces:**
- Produces: `fun fakeAppModule(): Module` (Koin module) — provides `single<StorageHelper>`, `single<PreferenceHelper>`, `single<ProxerApi>`, `single<RxBus>`, `single<Validators>`, `single<MessengerDao>`, `single<OkHttpClient>`, `single<TagDao>`, `single<WorkManager>`, `single<MessengerDatabase>`, all `mockk(relaxed = true)` except `RxBus` (real `RxBus()`). Consumed by Task 2's `TestApplication`.

This mirrors `src/test/kotlin/me/proxer/app/base/FakeAppModule.kt` exactly, but lives in a separate source set (`androidTest`) so it compiles against `mockk-android` instead of JVM `mockk`.

- [ ] **Step 1: Write the module**

```kotlin
package me.proxer.app.base

import androidx.work.WorkManager
import com.rubengees.rxbus.RxBus
import io.mockk.mockk
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.chat.prv.sync.MessengerDatabase
import me.proxer.app.media.TagDao
import me.proxer.app.util.Validators
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import okhttp3.OkHttpClient
import org.koin.dsl.module

fun fakeAppModule() = module {
    single<StorageHelper> { mockk(relaxed = true) }
    single<PreferenceHelper> { mockk(relaxed = true) }
    single<ProxerApi> { mockk(relaxed = true) }
    single<RxBus> { RxBus() }
    single<Validators> { mockk(relaxed = true) }
    single<MessengerDao> { mockk(relaxed = true) }
    single<OkHttpClient> { mockk(relaxed = true) }
    single<TagDao> { mockk(relaxed = true) }
    single<WorkManager> { mockk(relaxed = true) }
    single<MessengerDatabase> { mockk(relaxed = true) }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileDebugAndroidTestKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/base/FakeAppModule.kt
git commit -m "test: add androidTest fake Koin module"
```

---

### Task 2: Custom test Application + Runner wiring

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/MainModules.kt:253`
- Create: `src/androidTest/kotlin/me/proxer/app/base/TestApplication.kt`
- Create: `src/androidTest/kotlin/me/proxer/app/base/TestRunner.kt`
- Modify: `build.gradle:44`

**Interfaces:**
- Consumes: `fakeAppModule()` from Task 1.
- Produces: `TestApplication` (started by instrumentation instead of `MainApplication` for `androidTest`), used implicitly by every test in Task 4-6 (and all future per-package screen tests) since it's wired in at the `testInstrumentationRunner` level.

`MainApplication.onCreate()` calls `startKoin{}` with the real `koinModules` before any test code can run, so the DI swap has to happen at `Application` construction via a custom `AndroidJUnitRunner`, not by overriding modules afterward.

- [ ] **Step 1: Expose `viewModelModule` for the test Application to reuse**

`viewModelModule` (`src/main/kotlin/me/proxer/app/MainModules.kt:253`) is `private`, so it can't be referenced from `me.proxer.app.base.TestApplication`. Change it to `internal` — AGP's Kotlin compilation friend-links `androidTest` against `main`, so `internal` declarations are visible there. This lets tests reuse the real ViewModel-to-Koin wiring (catching DI wiring regressions) instead of hand-duplicating `viewModel { NotificationViewModel() }` declarations that could silently drift from production.

Before:
```kotlin
private val viewModelModule =
    module {
```

After:
```kotlin
internal val viewModelModule =
    module {
```

- [ ] **Step 2: Write `TestApplication`**

```kotlin
package me.proxer.app.base

import android.app.Application
import me.proxer.app.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@TestApplication)

            modules(listOf(viewModelModule, fakeAppModule()))
        }
    }
}
```

- [ ] **Step 3: Write `TestRunner`**

```kotlin
package me.proxer.app.base

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class TestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestApplication::class.java.name, context)
}
```

- [ ] **Step 4: Point Gradle at the new runner**

`build.gradle:44`

Before:
```groovy
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
```

After:
```groovy
        testInstrumentationRunner "me.proxer.app.base.TestRunner"
```

- [ ] **Step 5: Verify the existing TV tests still pass under the new Application**

The TV tests (`TvSearchScreenTest` etc.) render stateless composables directly and don't touch Koin, so they should be unaffected by swapping `MainApplication` for `TestApplication` — but this needs verifying since the change applies to the whole `androidTest` variant, not just new tests.

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=me.proxer.app.tv`
Expected: `BUILD SUCCESSFUL`, all existing TV tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/me/proxer/app/MainModules.kt src/androidTest/kotlin/me/proxer/app/base/TestApplication.kt src/androidTest/kotlin/me/proxer/app/base/TestRunner.kt build.gradle
git commit -m "test: swap in fake DI module for instrumented tests via custom test runner"
```

---

### Task 3: Login-state fixture helpers

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/base/LoginFixtures.kt`

**Interfaces:**
- Consumes: `StorageHelper`, `PreferenceHelper`, `Validators` mocks from Task 1's `fakeAppModule()` (injected into the test class via `by safeInject<T>()`).
- Produces: `fun stubLoggedIn(storageHelper: StorageHelper, preferenceHelper: PreferenceHelper)`, `fun stubLoggedOut(storageHelper: StorageHelper, preferenceHelper: PreferenceHelper, validators: Validators)`. Consumed by Task 4-6's `NotificationScreenTest`, and every future per-package screen test.

`BaseViewModel.init` subscribes to `storageHelper.isLoggedInObservable` and `preferenceHelper.isAgeRestrictedMediaAllowedObservable` immediately on construction — a relaxed MockK mock's default `Observable` return is unpredictable, so both must always be stubbed to `Observable.never()` regardless of login state under test (matches the existing JVM `NotificationViewModelTest` `@Before` pattern). Login-gating itself happens via `Validators.validateLogin()` throwing `NotLoggedInException`, not directly via `StorageHelper`.

- [ ] **Step 1: Write the fixtures**

```kotlin
package me.proxer.app.base

import io.mockk.every
import io.reactivex.Observable
import me.proxer.app.exception.NotLoggedInException
import me.proxer.app.util.Validators
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper

fun stubLoggedIn(storageHelper: StorageHelper, preferenceHelper: PreferenceHelper) {
    every { storageHelper.isLoggedInObservable } returns Observable.never()
    every { storageHelper.isLoggedIn } returns true
    every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
}

fun stubLoggedOut(storageHelper: StorageHelper, preferenceHelper: PreferenceHelper, validators: Validators) {
    every { storageHelper.isLoggedInObservable } returns Observable.never()
    every { storageHelper.isLoggedIn } returns false
    every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
    every { validators.validateLogin() } throws NotLoggedInException()
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileDebugAndroidTestKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/base/LoginFixtures.kt
git commit -m "test: add shared login-state fixtures for instrumented tests"
```

---

### Task 4: NotificationScreenTest — success and pagination

**Files:**
- Create: `src/androidTest/kotlin/me/proxer/app/notification/NotificationScreenTest.kt`

**Interfaces:**
- Consumes: `fakeAppModule()` (Task 1, wired automatically via `TestApplication`), `stubLoggedIn`/`stubLoggedOut` (Task 3), `me.proxer.app.notification.NotificationActivity`, `me.proxer.library.api.notifications.NotificationsEndpoint`, `me.proxer.library.entity.notifications.Notification`.

Proves the harness end-to-end: real `NotificationActivity` → real `NotificationViewModel` (via Koin) → real `ProxerApi` call path (mocked at the endpoint level), matching the mocking pattern already validated by the JVM `NotificationViewModelTest`.

- [ ] **Step 1: Write the test file with setup and the success/pagination cases**

```kotlin
package me.proxer.app.notification

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import me.proxer.app.base.stubLoggedIn
import me.proxer.app.util.extension.ProxerNotification
import me.proxer.app.util.extension.safeInject
import me.proxer.library.ProxerApi
import me.proxer.library.ProxerCall
import me.proxer.library.api.notifications.NotificationsEndpoint
import me.proxer.library.entity.notifications.Notification
import me.proxer.library.enums.NotificationType
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class NotificationScreenTest {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val api: ProxerApi by safeInject()
    private val storageHelper: StorageHelper by safeInject()
    private val preferenceHelper: PreferenceHelper by safeInject()

    private fun notification(id: String, text: String = "text-$id") = Notification(
        id,
        NotificationType.BOARD_MESSAGE,
        "content-$id",
        "https://proxer.me/".toHttpUrl(),
        text,
        Date(),
        "extra",
    )

    private fun mockNotificationsEndpoint(): NotificationsEndpoint {
        val endpoint = mockk<NotificationsEndpoint>(relaxed = true)

        every { api.notifications.notifications() } returns endpoint
        every { endpoint.markAsRead(any()) } returns endpoint
        every { endpoint.filter(any()) } returns endpoint
        every { endpoint.page(any()) } returns endpoint
        every { endpoint.limit(any()) } returns endpoint

        return endpoint
    }

    private fun mockCall(value: List<ProxerNotification>): ProxerCall<List<ProxerNotification>> {
        val call = mockk<ProxerCall<List<ProxerNotification>>>(relaxed = true)

        every { call.clone() } returns call
        every { call.safeExecute() } returns value

        return call
    }

    @Before
    fun setup() {
        stubLoggedIn(storageHelper, preferenceHelper)
    }

    @Test
    fun success_shows_unread_and_read_notification_text() {
        val endpoint = mockNotificationsEndpoint()
        val unread = listOf(notification("u0"))
        val read = listOf(notification("r0"))
        every { endpoint.build() } returnsMany listOf(mockCall(unread), mockCall(read))

        ActivityScenario.launch(NotificationActivity::class.java).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("text-u0").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("text-u0").assertIsDisplayed()
            composeTestRule.onNodeWithText("text-r0").assertIsDisplayed()
        }
    }

    @Test
    fun scrolling_to_the_bottom_loads_the_next_page() {
        val endpoint = mockNotificationsEndpoint()
        val firstPageUnread = (0 until 30).map { notification("u$it") }
        val secondPageItem = notification("p2-0", text = "page-two-item")
        every { endpoint.build() } returnsMany listOf(
            mockCall(firstPageUnread),
            mockCall(emptyList()),
            mockCall(listOf(secondPageItem)),
        )

        ActivityScenario.launch(NotificationActivity::class.java).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("text-u0").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNode(hasScrollAction()).performScrollToIndex(29)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("page-two-item").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("page-two-item").assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.notification.NotificationScreenTest`
Expected: `BUILD SUCCESSFUL`, both tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/notification/NotificationScreenTest.kt
git commit -m "test: add NotificationScreen instrumented tests for success and pagination"
```

---

### Task 5: NotificationScreenTest — error and retry

**Files:**
- Modify: `src/androidTest/kotlin/me/proxer/app/notification/NotificationScreenTest.kt`

**Interfaces:**
- Consumes: same as Task 4, plus `me.proxer.library.ProxerException`.

- [ ] **Step 1: Add an error-call helper and the two test cases**

Add this helper alongside `mockCall` (after it, same class):

```kotlin
    private fun mockErrorCall(): ProxerCall<List<ProxerNotification>> {
        val call = mockk<ProxerCall<List<ProxerNotification>>>(relaxed = true)

        every { call.clone() } returns call
        every { call.safeExecute() } throws ProxerException(ProxerException.ErrorType.IO)

        return call
    }
```

Add the import:
```kotlin
import me.proxer.library.ProxerException
```

Add these two `@Test` methods to the class:

```kotlin
    @Test
    fun error_shows_io_error_message_and_retry_button() {
        val endpoint = mockNotificationsEndpoint()
        every { endpoint.build() } returns mockErrorCall()

        ActivityScenario.launch(NotificationActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            val errorText = context.getString(me.proxer.app.R.string.error_io)
            val retryText = context.getString(me.proxer.app.R.string.error_action_retry)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(errorText).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(errorText).assertIsDisplayed()
            composeTestRule.onNodeWithText(retryText).assertIsDisplayed()
        }
    }

    @Test
    fun retry_replaces_error_with_content_on_success() {
        val endpoint = mockNotificationsEndpoint()
        val unread = listOf(notification("u0"))
        val read = listOf(notification("r0"))
        every { endpoint.build() } returns mockErrorCall()

        ActivityScenario.launch(NotificationActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            val retryText = context.getString(me.proxer.app.R.string.error_action_retry)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(retryText).fetchSemanticsNodes().isNotEmpty()
            }

            every { endpoint.build() } returnsMany listOf(mockCall(unread), mockCall(read))
            composeTestRule.onNodeWithText(retryText).performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("text-u0").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("text-u0").assertIsDisplayed()
        }
    }
```

Add the import for `performClick`:
```kotlin
import androidx.compose.ui.test.performClick
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.notification.NotificationScreenTest`
Expected: `BUILD SUCCESSFUL`, all four tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/notification/NotificationScreenTest.kt
git commit -m "test: add NotificationScreen error and retry instrumented tests"
```

---

### Task 6: NotificationScreenTest — login gate

**Files:**
- Modify: `src/androidTest/kotlin/me/proxer/app/notification/NotificationScreenTest.kt`

**Interfaces:**
- Consumes: `stubLoggedOut` (Task 3), `me.proxer.app.util.Validators`.

Verifies the harness correctly exercises `BaseViewModel.isLoginRequired` gating: a logged-out user should see the login-required message instead of content, without ever hitting the mocked `ProxerApi` endpoint.

- [ ] **Step 1: Inject `Validators` and add the login-gate test**

Add the injected property near `preferenceHelper`:
```kotlin
    private val validators: me.proxer.app.util.Validators by safeInject()
```

Add the test:
```kotlin
    @Test
    fun logged_out_user_sees_login_required_message_instead_of_content() {
        me.proxer.app.base.stubLoggedOut(storageHelper, preferenceHelper, validators)

        ActivityScenario.launch(NotificationActivity::class.java).use {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            val loginRequiredText = context.getString(me.proxer.app.R.string.error_login_required)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(loginRequiredText).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(loginRequiredText).assertIsDisplayed()
        }
    }
```

Note: this test calls `stubLoggedIn` in `@Before` first (existing setup), then overrides with `stubLoggedOut` inside the test body — `every {}` stubs are last-write-wins per mock, so this is safe and keeps `@Before` uniform across all tests in the class.

- [ ] **Step 2: Run the tests**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.proxer.app.notification.NotificationScreenTest`
Expected: `BUILD SUCCESSFUL`, all five tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/androidTest/kotlin/me/proxer/app/notification/NotificationScreenTest.kt
git commit -m "test: add NotificationScreen login-gate instrumented test"
```

---

## Follow-up (out of scope here)

Once this lands, each remaining feature package (auth, anime, chat, manga, media, news, profile, settings, forum, bookmark, comment, info, remaining notification/profile-settings screens) gets its own brainstorming → spec → plan cycle reusing this harness (`fakeAppModule()`, `stubLoggedIn`/`stubLoggedOut`, `TestApplication`/`TestRunner`).
