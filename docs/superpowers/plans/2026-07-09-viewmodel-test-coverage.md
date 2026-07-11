# ViewModel Unit Test Coverage Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add unit tests for all 39 concrete mobile ViewModels in `me.proxer.app`, following the approved design in `docs/superpowers/specs/2026-07-09-viewmodel-test-coverage-design.md`. Same coverage bar everywhere: success/error/loading/reload for the base state machine, plus pagination-specific behavior for paged ViewModels, plus bespoke logic (validation, side effects, custom state) per ViewModel. TV ViewModels and Compose screen UI tests are out of scope (TV lives on a separate branch; screen tests are a later phase).

**Architecture:** All async work is RxJava 2 (no coroutines) wrapped in `LiveData` by the `BaseViewModel`/`BaseContentViewModel`/`PagedViewModel`/`PagedContentViewModel` hierarchy in `me.proxer.app.base`, plus a separate `ReportViewModel` abstract base in `me.proxer.app.chat`. Tests run on the JVM (not instrumented) using MockK for network/DI mocks and `RxJavaPlugins`/`RxAndroidPlugins` trampoline scheduler overrides for synchronous execution. Koin is driven via `koin-test`'s `KoinTestRule` with a shared `fakeAppModule()` that each task extends additively as new ViewModels need new fake singletons (e.g. Room DAOs).

**Tech Stack:** JUnit 4.13.2, MockK 1.13.12, koin-test 4.2.1 + koin-test-junit4, androidx.arch.core:core-testing 2.2.0 (`InstantTaskExecutorRule`), ProxerLibJava 5.4.0 (`Endpoint`, `PagingLimitEndpoint`, `ProxerCall`).

**39 ViewModels, grouped by base class:**

- **Family 1 — `BaseViewModel`/`BaseContentViewModel` (19):** simple load/error/reload state machine. `AnimeViewModel`, `ScheduleViewModel`, `ConferenceViewModel`, `ConferenceInfoViewModel`, `ChatRoomViewModel`, `ChatRoomInfoViewModel`, `EditCommentViewModel`, `IndustryInfoViewModel`, `TranslatorGroupInfoViewModel`, `MangaViewModel`, `MediaInfoViewModel`, `DiscussionViewModel`, `EpisodeViewModel`, `RecommendationViewModel`, `RelationViewModel`, `ProfileViewModel`, `ProfileAboutViewModel`, `TopTenViewModel`, `ServerStatusViewModel`.
- **Family 2 — `PagedViewModel`/`PagedContentViewModel` (13):** adds pagination (page/limit, `hasReachedEnd`, `refreshError`, merge-on-refresh). `BookmarkViewModel`, `MessengerViewModel`, `ChatViewModel`, `TopicViewModel`, `IndustryProjectViewModel`, `TranslatorGroupProjectViewModel`, `CommentsViewModel`, `MediaListViewModel`, `NewsViewModel`, `NotificationViewModel`, `ProfileCommentViewModel`, `HistoryViewModel`, `ProfileMediaListViewModel`.
- **Family 3 — plain `ViewModel`/`ReportViewModel` (7):** bespoke per-class logic, no shared base state machine. `LoginViewModel`, `LogoutViewModel`, `CreateConferenceViewModel`, `ProfileSettingsViewModel`, `LinkCheckViewModel`, `MessengerReportViewModel`, `ChatReportViewModel`.

---

## Shared Test Infrastructure Reference

These pieces are created once in Task 2 and reused by every later task. Later task Steps assume they already exist and only show `import`s, not re-definitions.

**`RxTrampolineRule`** (`src/test/kotlin/me/proxer/app/base/RxTrampolineRule.kt`) — a `TestWatcher` `@get:Rule` that forces `Schedulers.trampoline()` on all RxJava/RxAndroid scheduler hooks for the test's duration, replacing the old manual `@Before`/`@After` `RxAndroidPlugins`/`RxJavaPlugins` setup/reset.

**`ProxerEndpointTestUtils.kt`** (`src/test/kotlin/me/proxer/app/base/ProxerEndpointTestUtils.kt`) — mocking recipe for ProxerLibJava network calls:
- `Endpoint<T>.stubSuccess(value: T)` — stubs `endpoint.build()` so `buildSingle()` emits `value`.
- `Endpoint<T>.stubError(exception: ProxerException = ProxerException(ProxerException.ErrorType.IO))` — stubs `buildSingle()` to error.
- `PagingLimitEndpoint<T>.stubPagingSuccess(value: T)` / `.stubPagingError(exception)` — same, but also stubs `.page(any())`/`.limit(any())` to return `this` so the `endpoint.page(page).limit(itemsOnPage).buildSingle()` chain in `PagedContentViewModel` resolves.

Usage pattern for any ViewModel that exposes `mockk<Endpoint<Foo>>(relaxed = true)` (typically via a mocked `ProxerApi`/API-group interface returning the endpoint):

```kotlin
val endpoint = mockk<Endpoint<Foo>>(relaxed = true)
every { api.someGroup().someCall(any()) } returns endpoint
endpoint.stubSuccess(someFoo)
```

**`fakeAppModule()`** (`src/test/kotlin/me/proxer/app/base/FakeAppModule.kt`) — Koin test module with relaxed mocks for `StorageHelper`, `PreferenceHelper`, `ProxerApi`, `Validators`, and a real `RxBus`. Extended additively per task when a ViewModel needs another Koin singleton (Room DAOs, etc.) — each such task's Steps show the exact addition.

**Every ViewModel test class follows this skeleton:**

```kotlin
class FooViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    // ...other injected fakes as needed

    private lateinit var viewModel: FooViewModel

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        viewModel = FooViewModel(/* constructor args, if any */)
    }

    // tests...
}
```

The `isLoggedInObservable`/`isAgeRestrictedMediaAllowedObservable` stubs are required for every test — `BaseViewModel`'s `init` block subscribes to both immediately on construction, and unstubbed relaxed mocks return `null` for `Observable<T>`, which NPEs on subscribe.

**Known risk — concrete endpoint types:** Every task below was drafted by reading real source, but a self-review compile-check against 8 sampled tasks found that some `ProxerApi` methods return a *concrete* endpoint interface (e.g. `api.info.entryCore(id)` returns `EntryCoreEndpoint`, not `Endpoint<EntryCore>`) rather than the bare generic `Endpoint<T>`/`PagingLimitEndpoint<T>` — these concrete interfaces typically exist because the endpoint has extra builder methods (`.secretKey()`, `.includeProxerStreams()`, etc.) beyond the base interface. Mocking with the wrong (too-generic) type fails to compile with `Argument type mismatch`. This was found and fixed in Tasks 3, 12, 29, and 41 during self-review; it was not exhaustively checked across all 39 tasks. **When executing any task below, if `mockk<Endpoint<X>>()` (or `mockk<PagingLimitEndpoint<List<X>>>()`) doesn't type-check against the real `ProxerApi` call site, check that call site's real return type and mock the concrete interface instead** — the fix is always the same shape as the Task 3/12 fixes above.

---

## File Map

**Created:**
- `src/test/kotlin/me/proxer/app/base/RxTrampolineRule.kt`
- `src/test/kotlin/me/proxer/app/base/ProxerEndpointTestUtils.kt`
- 39 ViewModel test files (one per Task 3–41; exact paths in each task's **Files** section)

**Modified:**
- `src/test/kotlin/me/proxer/app/util/ErrorUtilsTest.kt` — fix pre-existing compile break (Task 1)
- `src/test/kotlin/me/proxer/app/base/BaseViewModelTest.kt` — adopt `RxTrampolineRule` (Task 2)
- `src/test/kotlin/me/proxer/app/base/FakeAppModule.kt` — extended additively as needed per task

---

### Task 1: Fix pre-existing ErrorUtilsTest compile break

**Goal:** `ErrorUtilsTest.kt` calls `ErrorUtils.getMessage(error, isLoggedIn = ...)`, but `ErrorUtils.getMessage` was refactored down to a single-arg `getMessage(error: Throwable)` that reads `storageHelper.isLoggedIn` internally — the two-arg call sites no longer compile. This blocks `./gradlew test`/`compileDebugUnitTestKotlin` entirely, before any ViewModel test work can be verified.

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/util/ErrorUtilsTest.kt`

**Acceptance Criteria:**
- [ ] `./gradlew compileDebugUnitTestKotlin` succeeds
- [ ] `./gradlew test --tests "me.proxer.app.util.ErrorUtilsTest"` passes, `0 tests failed`
- [ ] The `USER_INSUFFICIENT_PERMISSIONS` logged-in/logged-out branching (the only `getMessage` behavior that actually depends on `isLoggedIn`) is still covered

**Verify:** `./gradlew test --tests "me.proxer.app.util.ErrorUtilsTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Replace `src/test/kotlin/me/proxer/app/util/ErrorUtilsTest.kt` in full**

`getMessage(error, isLoggedIn)` calls become `getMessage(error)` (32 call sites — none of those specific error types depend on login state). The two `USER_INSUFFICIENT_PERMISSIONS` message tests collapse into one test that mutates the Koin-mocked `StorageHelper.isLoggedIn` stub twice, because `ErrorUtils`'s `private val storageHelper by safeInject<StorageHelper>()` is a `by lazy` that binds once per JVM and never re-resolves — two separate `@Test` methods would let the first one's mock get cached forever, silently ignoring the second test's own `KoinTestRule` mock. `ErrorUtils.handle(error, isLoggedIn)` (the `internal` two-arg overload) is untouched and still used everywhere, since it never depended on `storageHelper`.

Replace the whole file with:

```kotlin
package me.proxer.app.util

import io.mockk.every
import me.proxer.app.R
import me.proxer.app.base.fakeAppModule
import me.proxer.app.comment.CommentInvalidProgressException
import me.proxer.app.comment.CommentTooLongException
import me.proxer.app.exception.AgeConfirmationRequiredException
import me.proxer.app.exception.NotConnectedException
import me.proxer.app.exception.NotLoggedInException
import me.proxer.app.exception.PartialException
import me.proxer.app.exception.StreamResolutionException
import me.proxer.app.manga.MangaLinkException
import me.proxer.app.manga.MangaNotAvailableException
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.ErrorUtils.ErrorAction.Companion.ACTION_MESSAGE_DEFAULT
import me.proxer.app.util.ErrorUtils.ErrorAction.Companion.ACTION_MESSAGE_HIDE
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerException
import me.proxer.library.ProxerException.ErrorType
import me.proxer.library.ProxerException.ServerErrorType
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLPeerUnverifiedException

class ErrorUtilsTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    private val storageHelper: StorageHelper by inject()

    // ── getMessage ──────────────────────────────────────────────────────────

    @Test fun `IO errorType maps to error_io`() {
        val ex = ProxerException(ErrorType.IO)
        assertEquals(R.string.error_io, ErrorUtils.getMessage(ex))
    }

    @Test fun `TIMEOUT errorType maps to error_timeout`() {
        val ex = ProxerException(ErrorType.TIMEOUT)
        assertEquals(R.string.error_timeout, ErrorUtils.getMessage(ex))
    }

    @Test fun `PARSING errorType maps to error_parsing`() {
        val ex = ProxerException(ErrorType.PARSING)
        assertEquals(R.string.error_parsing, ErrorUtils.getMessage(ex))
    }

    @Test fun `UNKNOWN errorType maps to error_unknown`() {
        val ex = ProxerException(ErrorType.UNKNOWN)
        assertEquals(R.string.error_unknown, ErrorUtils.getMessage(ex))
    }

    @Test fun `CANCELLED errorType maps to error_unknown`() {
        val ex = ProxerException(ErrorType.CANCELLED)
        assertEquals(R.string.error_unknown, ErrorUtils.getMessage(ex))
    }

    @Test fun `SERVER IP_BLOCKED maps to error_captcha`() {
        val ex = ProxerException(ErrorType.SERVER, ServerErrorType.IP_BLOCKED)
        assertEquals(R.string.error_captcha, ErrorUtils.getMessage(ex))
    }

    @Test fun `SERVER RATE_LIMIT maps to error_rate_limit`() {
        val ex = ProxerException(ErrorType.SERVER, ServerErrorType.RATE_LIMIT)
        assertEquals(R.string.error_rate_limit, ErrorUtils.getMessage(ex))
    }

    @Test fun `SERVER INVALID_TOKEN maps to error_invalid_token`() {
        val ex = ProxerException(ErrorType.SERVER, ServerErrorType.INVALID_TOKEN)
        assertEquals(R.string.error_invalid_token, ErrorUtils.getMessage(ex))
    }

    @Test fun `SERVER LOGIN_INVALID_CREDENTIALS maps to error_login_credentials`() {
        val ex = ProxerException(ErrorType.SERVER, ServerErrorType.LOGIN_INVALID_CREDENTIALS)
        assertEquals(R.string.error_login_credentials, ErrorUtils.getMessage(ex))
    }

    // ErrorUtils resolves `storageHelper` through a `by safeInject<StorageHelper>()` delegate — a `by lazy`
    // that binds once per JVM and never re-resolves. Splitting this into two @Test methods would let the
    // first one's KoinTestRule mock get cached forever, so both login states are asserted here in one test
    // against that same cached mock instead.
    @Test fun `SERVER USER_INSUFFICIENT_PERMISSIONS message depends on login state`() {
        val ex = ProxerException(ErrorType.SERVER, ServerErrorType.USER_INSUFFICIENT_PERMISSIONS)

        every { storageHelper.isLoggedIn } returns true
        assertEquals(R.string.error_insufficient_permissions_logged_in, ErrorUtils.getMessage(ex))

        every { storageHelper.isLoggedIn } returns false
        assertEquals(R.string.error_insufficient_permissions, ErrorUtils.getMessage(ex))
    }

    @Test fun `SERVER USER_2FA_SECRET_REQUIRED maps to error_login_two_factor_authentication`() {
        val ex = ProxerException(ErrorType.SERVER, ServerErrorType.USER_2FA_SECRET_REQUIRED)
        assertEquals(R.string.error_login_two_factor_authentication, ErrorUtils.getMessage(ex))
    }

    @Test fun `SocketTimeoutException maps to error_timeout`() {
        assertEquals(R.string.error_timeout, ErrorUtils.getMessage(SocketTimeoutException()))
    }

    @Test fun `SSLPeerUnverifiedException maps to error_ssl`() {
        assertEquals(R.string.error_ssl, ErrorUtils.getMessage(SSLPeerUnverifiedException("x")))
    }

    @Test fun `NotConnectedException maps to error_no_network`() {
        assertEquals(R.string.error_no_network, ErrorUtils.getMessage(NotConnectedException()))
    }

    @Test fun `IOException maps to error_io`() {
        assertEquals(R.string.error_io, ErrorUtils.getMessage(IOException("test")))
    }

    @Test fun `NotLoggedInException maps to error_login_required`() {
        assertEquals(R.string.error_login_required, ErrorUtils.getMessage(NotLoggedInException()))
    }

    @Test fun `AgeConfirmationRequiredException maps to error_age_confirmation_needed`() {
        assertEquals(
            R.string.error_age_confirmation_needed,
            ErrorUtils.getMessage(AgeConfirmationRequiredException()),
        )
    }

    @Test fun `StreamResolutionException maps to error_stream_resolution`() {
        assertEquals(
            R.string.error_stream_resolution,
            ErrorUtils.getMessage(StreamResolutionException()),
        )
    }

    @Test fun `MangaNotAvailableException maps to error_manga_not_available`() {
        assertEquals(
            R.string.error_manga_not_available,
            ErrorUtils.getMessage(MangaNotAvailableException()),
        )
    }

    @Test fun `CommentTooLongException maps to error_comment_too_long`() {
        assertEquals(
            R.string.error_comment_too_long,
            ErrorUtils.getMessage(CommentTooLongException()),
        )
    }

    @Test fun `CommentInvalidProgressException maps to error_comment_invalid_progress`() {
        assertEquals(
            R.string.error_comment_invalid_progress,
            ErrorUtils.getMessage(CommentInvalidProgressException()),
        )
    }

    @Test fun `MangaLinkException maps to error_manga_link`() {
        val ex = MangaLinkException("Chapter 1", "https://example.com/manga".toHttpUrl())
        assertEquals(R.string.error_manga_link, ErrorUtils.getMessage(ex))
    }

    @Test fun `unknown exception maps to error_unknown`() {
        assertEquals(R.string.error_unknown, ErrorUtils.getMessage(RuntimeException("unexpected")))
    }

    @Test fun `PartialException unwraps to inner error message`() {
        val inner = NotLoggedInException()
        val ex = PartialException(inner, "some partial data")
        assertEquals(R.string.error_login_required, ErrorUtils.getMessage(ex))
    }

    // ── handle ──────────────────────────────────────────────────────────────

    @Test fun `handle NotLoggedInException sets LOGIN buttonAction`() {
        val action = ErrorUtils.handle(NotLoggedInException(), isLoggedIn = false)
        assertEquals(ButtonAction.LOGIN, action.buttonAction)
    }

    @Test fun `handle NotConnectedException sets NETWORK_SETTINGS buttonAction`() {
        val action = ErrorUtils.handle(NotConnectedException(), isLoggedIn = false)
        assertEquals(ButtonAction.NETWORK_SETTINGS, action.buttonAction)
    }

    @Test fun `handle AgeConfirmationRequiredException sets AGE_CONFIRMATION buttonAction`() {
        val action = ErrorUtils.handle(AgeConfirmationRequiredException(), isLoggedIn = false)
        assertEquals(ButtonAction.AGE_CONFIRMATION, action.buttonAction)
    }

    @Test fun `handle IP_BLOCKED sets CAPTCHA buttonAction`() {
        val action = ErrorUtils.handle(
            ProxerException(ErrorType.SERVER, ServerErrorType.IP_BLOCKED),
            isLoggedIn = false,
        )
        assertEquals(ButtonAction.CAPTCHA, action.buttonAction)
    }

    @Test fun `handle INVALID_TOKEN sets LOGIN buttonAction`() {
        val action = ErrorUtils.handle(
            ProxerException(ErrorType.SERVER, ServerErrorType.INVALID_TOKEN),
            isLoggedIn = false,
        )
        assertEquals(ButtonAction.LOGIN, action.buttonAction)
    }

    @Test fun `handle MEDIA_REMOVED_DUE_TO_COPYRIGHT hides button`() {
        val action = ErrorUtils.handle(
            ProxerException(ErrorType.SERVER, ServerErrorType.MEDIA_REMOVED_DUE_TO_COPYRIGHT),
            isLoggedIn = false,
        )
        assertNull(action.buttonAction)
        assertEquals(ACTION_MESSAGE_HIDE, action.buttonMessage)
    }

    @Test fun `handle generic server error has default button`() {
        val action = ErrorUtils.handle(
            ProxerException(ErrorType.SERVER, ServerErrorType.INTERNAL),
            isLoggedIn = false,
        )
        assertNull(action.buttonAction)
        assertEquals(ACTION_MESSAGE_DEFAULT, action.buttonMessage)
    }

    @Test fun `handle PartialException passes partialData through`() {
        val inner = NotLoggedInException()
        val ex = PartialException(inner, "entry_42")
        val action = ErrorUtils.handle(ex, isLoggedIn = false)
        assertEquals("entry_42", action.data[ErrorUtils.ENTRY_DATA_KEY])
        assertEquals(ButtonAction.LOGIN, action.buttonAction)
    }

    @Test fun `handle MangaLinkException sets OPEN_LINK and passes link data`() {
        val url = "https://example.com/manga".toHttpUrl()
        val ex = MangaLinkException("Chapter 1", url)
        val action = ErrorUtils.handle(ex, isLoggedIn = false)
        assertEquals(ButtonAction.OPEN_LINK, action.buttonAction)
        assertEquals(url, action.data[ErrorUtils.LINK_DATA_KEY])
        assertEquals("Chapter 1", action.data[ErrorUtils.CHAPTER_TITLE_DATA_KEY])
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.util.ErrorUtilsTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/util/ErrorUtilsTest.kt
git commit -m "fix(test): repair ErrorUtilsTest after getMessage signature change"
```

---

### Task 2: Add shared ViewModel test infrastructure

**Goal:** Create `RxTrampolineRule` and the ProxerLibJava `Endpoint`/`PagingLimitEndpoint` mocking recipe used by every ViewModel test in Tasks 3–41, and migrate `BaseViewModelTest` to the new rule (dropping the old manual `@Before`/`@After` scheduler setup it duplicates).

**Files:**
- Create: `src/test/kotlin/me/proxer/app/base/RxTrampolineRule.kt`
- Create: `src/test/kotlin/me/proxer/app/base/ProxerEndpointTestUtils.kt`
- Modify: `src/test/kotlin/me/proxer/app/base/BaseViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `RxTrampolineRule` forces `Schedulers.trampoline()` for `RxAndroidPlugins`/`RxJavaPlugins` main/io/computation/newThread handlers around each test
- [ ] `Endpoint<T>.stubSuccess`/`.stubError` and `PagingLimitEndpoint<T>.stubPagingSuccess`/`.stubPagingError` compile against ProxerLibJava 5.4.0's actual API (`Endpoint.build(): ProxerCall<T>`, `ProxerCall.clone()`/`.safeExecute()`, `PagingLimitEndpoint.page(Integer)`/`.limit(Integer)`)
- [ ] `BaseViewModelTest` uses `RxTrampolineRule` instead of manual `@Before`/`@After` scheduler plumbing, and all its existing tests still pass
- [ ] `./gradlew test --tests "me.proxer.app.base.BaseViewModelTest"` passes

**Verify:** `./gradlew test --tests "me.proxer.app.base.BaseViewModelTest" --tests "me.proxer.app.base.ProxerEndpointTestUtilsTest"` → `BUILD SUCCESSFUL`, `0 tests failed` (no dedicated test file for the mocking utils themselves — they're exercised transitively by every later task)

**Steps:**

- [ ] **Step 1: Create `RxTrampolineRule`**

Create `src/test/kotlin/me/proxer/app/base/RxTrampolineRule.kt`:

```kotlin
package me.proxer.app.base

import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Forces all RxJava/RxAndroid schedulers to [Schedulers.trampoline] for the duration of a test,
 * so `subscribeOn`/`observeOn` chains in ViewModels execute synchronously on the test thread.
 */
class RxTrampolineRule : TestWatcher() {
    override fun starting(description: Description) {
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setNewThreadSchedulerHandler { Schedulers.trampoline() }
    }

    override fun finished(description: Description) {
        RxAndroidPlugins.reset()
        RxJavaPlugins.reset()
    }
}
```

- [ ] **Step 2: Create `ProxerEndpointTestUtils`**

Create `src/test/kotlin/me/proxer/app/base/ProxerEndpointTestUtils.kt`:

```kotlin
package me.proxer.app.base

import io.mockk.every
import io.mockk.mockk
import me.proxer.library.ProxerCall
import me.proxer.library.ProxerException
import me.proxer.library.api.Endpoint
import me.proxer.library.api.PagingLimitEndpoint

/**
 * Creates a relaxed mock [ProxerCall] whose [ProxerCall.safeExecute] returns [value].
 * `clone()` is stubbed to return the mock itself, mirroring [me.proxer.app.util.rx.ProxerCallSingle].
 */
fun <T : Any> mockProxerCallSuccess(value: T): ProxerCall<T> {
    val call = mockk<ProxerCall<T>>(relaxed = true)

    every { call.clone() } returns call
    every { call.safeExecute() } returns value

    return call
}

/**
 * Creates a relaxed mock [ProxerCall] whose [ProxerCall.safeExecute] throws [exception].
 */
fun <T : Any> mockProxerCallError(
    exception: ProxerException = ProxerException(ProxerException.ErrorType.IO),
): ProxerCall<T> {
    val call = mockk<ProxerCall<T>>(relaxed = true)

    every { call.clone() } returns call
    every { call.safeExecute() } throws exception

    return call
}

/** Stubs this mocked [Endpoint] so `buildSingle()` emits [value]. */
fun <T : Any> Endpoint<T>.stubSuccess(value: T) {
    every { build() } returns mockProxerCallSuccess(value)
}

/** Stubs this mocked [Endpoint] so `buildSingle()` errors with [exception]. */
fun <T : Any> Endpoint<T>.stubError(
    exception: ProxerException = ProxerException(ProxerException.ErrorType.IO),
) {
    every { build() } returns mockProxerCallError(exception)
}

/**
 * Stubs this mocked [PagingLimitEndpoint] so calling `.page(x).limit(y).buildSingle()` (as done by
 * [me.proxer.app.base.PagedContentViewModel]) emits [value] regardless of the requested page/limit.
 */
fun <T : Any> PagingLimitEndpoint<T>.stubPagingSuccess(value: T) {
    every { page(any()) } returns this
    every { limit(any()) } returns this
    every { build() } returns mockProxerCallSuccess(value)
}

/**
 * Stubs this mocked [PagingLimitEndpoint] so calling `.page(x).limit(y).buildSingle()` errors with [exception].
 */
fun <T : Any> PagingLimitEndpoint<T>.stubPagingError(
    exception: ProxerException = ProxerException(ProxerException.ErrorType.IO),
) {
    every { page(any()) } returns this
    every { limit(any()) } returns this
    every { build() } returns mockProxerCallError(exception)
}
```

- [ ] **Step 3: Migrate `BaseViewModelTest` to `RxTrampolineRule`**

In `src/test/kotlin/me/proxer/app/base/BaseViewModelTest.kt`:

Remove these imports:
```kotlin
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import org.junit.After
```

Add this rule alongside the existing two:
```kotlin
    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()
```

Remove the `RxAndroidPlugins.setInitMainThreadSchedulerHandler { ... }` and `RxJavaPlugins.setIoSchedulerHandler { ... }` lines from `@Before fun setup()`, and delete the whole `@After fun teardown() { ... }` method — `RxTrampolineRule` now owns both.

The full resulting file:

```kotlin
package me.proxer.app.base

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.reactivex.Observable
import io.reactivex.Single
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.io.IOException

class BaseViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: TestViewModel

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        viewModel = TestViewModel()
    }

    @Test
    fun `load sets data on success`() {
        viewModel.nextResponse = Single.just("hello")
        viewModel.load()
        assertEquals("hello", viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        viewModel.nextResponse = Single.error(IOException("net error"))
        viewModel.load()
        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        viewModel.nextResponse = Single.just("ok")
        viewModel.load()
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        viewModel.nextResponse = Single.error(IOException("fail"))
        viewModel.load()
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error before new load completes`() {
        viewModel.nextResponse = Single.just("first")
        viewModel.load()
        assertEquals("first", viewModel.data.value)

        // Stall the reload with Single.never() so we can observe the cleared state
        viewModel.nextResponse = Single.never()
        viewModel.reload()
        // Data and error should be null immediately after reload() is called, before load completes
        assertNull(viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `reload loads new data after clearing`() {
        viewModel.nextResponse = Single.just("first")
        viewModel.load()
        assertEquals("first", viewModel.data.value)

        viewModel.nextResponse = Single.just("second")
        viewModel.reload()
        assertEquals("second", viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `loadIfPossible skips when already loading`() {
        // Use Single.never() so load never completes — isLoading stays true
        viewModel.nextResponse = Single.never()
        viewModel.load()
        // At this point isLoading should be true (never completed)
        assertTrue(viewModel.isLoading.value == true)

        // Second loadIfPossible should not change state
        viewModel.nextResponse = Single.just("should not land")
        viewModel.loadIfPossible()
        // data still null — load was skipped
        assertNull(viewModel.data.value)
    }

    @Test
    fun `load clears previous error`() {
        viewModel.nextResponse = Single.error(IOException("fail"))
        viewModel.load()
        assertNotNull(viewModel.error.value)

        viewModel.nextResponse = Single.just("ok")
        viewModel.load()
        assertNull(viewModel.error.value)
        assertEquals("ok", viewModel.data.value)
    }

    // Inner fake ViewModel — override dataSingle via var so tests can control responses.
    // isLoginRequired is overridden to false so that the isLoggedInObservable subscription
    // in init does not trigger spurious reload() calls during tests.
    private inner class TestViewModel : BaseViewModel<String>() {
        override val isLoginRequired = false
        var nextResponse: Single<String> = Single.never()
        override val dataSingle: Single<String> get() = nextResponse
    }
}
```

- [ ] **Step 4: Verify**

```bash
./gradlew test --tests "me.proxer.app.base.BaseViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/me/proxer/app/base/RxTrampolineRule.kt \
        src/test/kotlin/me/proxer/app/base/ProxerEndpointTestUtils.kt \
        src/test/kotlin/me/proxer/app/base/BaseViewModelTest.kt
git commit -m "test: add RxTrampolineRule and ProxerLibJava endpoint mocking utils"
```

---
### Task 3: Unit tests — AnimeViewModel

**Goal:** Verify `AnimeViewModel`'s `dataSingle` (entry + stream resolution pipeline) success/error/reload behavior and its age-confirmation error mapping, using Koin test fakes and RxJava scheduler overrides.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/anime/AnimeViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets `data` to the mapped `AnimeStreamInfo` on success
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] An age-restricted entry with `isAgeRestrictedMediaAllowed = false` sets `error.value?.buttonAction == ButtonAction.AGE_CONFIRMATION`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.anime.AnimeViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/anime/AnimeViewModelTest.kt`**

```kotlin
package me.proxer.app.anime

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.anime.StreamsEndpoint
import me.proxer.library.api.info.EntryCoreEndpoint
import me.proxer.library.entity.info.AdaptionInfo
import me.proxer.library.entity.info.EntryCore
import me.proxer.library.enums.AnimeLanguage
import me.proxer.library.enums.Category
import me.proxer.library.enums.License
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class AnimeViewModelTest : KoinTest {

    private companion object {
        private const val ENTRY_ID = "12345"
        private val LANGUAGE = AnimeLanguage.GERMAN_SUB
        private const val EPISODE = 1
    }

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val entryEndpoint = mockk<EntryCoreEndpoint>(relaxed = true)
    private val streamsEndpoint = mockk<StreamsEndpoint>(relaxed = true)

    private lateinit var viewModel: AnimeViewModel

    private fun entryCore(medium: Medium = Medium.ANIMESERIES) = EntryCore(
        id = ENTRY_ID,
        name = "Test Anime",
        genres = emptySet(),
        fskConstraints = emptySet(),
        description = "A description",
        medium = medium,
        episodeAmount = 12,
        state = MediaState.FINISHED,
        ratingSum = 100,
        ratingAmount = 20,
        clicks = 500,
        category = Category.ANIME,
        license = License.UNKNOWN,
        adaptionInfo = AdaptionInfo("99", "Related Manga", Medium.MANGASERIES),
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns true

        every { api.info.entryCore(ENTRY_ID) } returns entryEndpoint
        every { api.anime.streams(ENTRY_ID, EPISODE, LANGUAGE) } returns streamsEndpoint
        every { streamsEndpoint.includeProxerStreams(true) } returns streamsEndpoint

        viewModel = AnimeViewModel(ENTRY_ID, LANGUAGE, EPISODE)
    }

    @Test
    fun `load sets data on success`() {
        entryEndpoint.stubSuccess(entryCore())
        streamsEndpoint.stubSuccess(emptyList())

        viewModel.load()

        assertEquals(AnimeStreamInfo("Test Anime", 12, emptyList()), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        entryEndpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        entryEndpoint.stubSuccess(entryCore())
        streamsEndpoint.stubSuccess(emptyList())

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        entryEndpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        // The first attempt fails so entrySingle() never caches an EntryCore, keeping the reload observable
        // (once cached, entrySingle() bypasses api.info.entryCore() entirely on subsequent loads).
        entryEndpoint.stubError()
        viewModel.load()
        assertNotNull(viewModel.error.value)

        entryEndpoint.stubSuccess(entryCore())
        streamsEndpoint.stubSuccess(emptyList())
        viewModel.reload()

        assertEquals(AnimeStreamInfo("Test Anime", 12, emptyList()), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets age confirmation error for age restricted entry when not confirmed`() {
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
        entryEndpoint.stubSuccess(entryCore(medium = Medium.HENTAI))
        streamsEndpoint.stubSuccess(emptyList())

        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.AGE_CONFIRMATION, viewModel.error.value?.buttonAction)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.anime.AnimeViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/anime/AnimeViewModelTest.kt
git commit -m "test: add AnimeViewModel unit tests"
```

---

### Task 4: Unit tests — ScheduleViewModel

**Goal:** Verify `ScheduleViewModel`'s calendar grouping/sorting on success, error/reload handling, and its `isLoginRequired = false` override.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/anime/schedule/ScheduleViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` groups calendar entries by `CalendarDay` and sorts each group by date
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `isLoginRequired = false` is honored: `validators.validateLogin()` is never called during `load()`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.anime.schedule.ScheduleViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/anime/schedule/ScheduleViewModelTest.kt`**

```kotlin
package me.proxer.app.anime.schedule

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.Validators
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.media.CalendarEndpoint
import me.proxer.library.entity.media.CalendarEntry
import me.proxer.library.enums.CalendarDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.util.Date

class ScheduleViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()
    private val validators: Validators by inject()

    private val endpoint = mockk<CalendarEndpoint>(relaxed = true)

    private lateinit var viewModel: ScheduleViewModel

    private val now = Date()

    private val mondayLater = CalendarEntry(
        id = "1", entryId = "100", name = "Show A", episode = 1, episodeTitle = "Ep 1",
        date = Date(now.time + 3_600_000), timezone = "Europe/Berlin", industryId = "10", industryName = "Studio A",
        weekDay = CalendarDay.MONDAY, uploadDate = now, genres = emptySet(), ratingSum = 10, ratingAmount = 2,
    )

    private val mondayEarlier = CalendarEntry(
        id = "2", entryId = "101", name = "Show B", episode = 2, episodeTitle = "Ep 2",
        date = now, timezone = "Europe/Berlin", industryId = "11", industryName = "Studio B",
        weekDay = CalendarDay.MONDAY, uploadDate = now, genres = emptySet(), ratingSum = 20, ratingAmount = 4,
    )

    private val tuesdayEntry = CalendarEntry(
        id = "3", entryId = "102", name = "Show C", episode = 5, episodeTitle = "Ep 5",
        date = now, timezone = "Europe/Berlin", industryId = "12", industryName = "Studio C",
        weekDay = CalendarDay.TUESDAY, uploadDate = now, genres = emptySet(), ratingSum = 5, ratingAmount = 1,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns false
        every { api.media.calendar() } returns endpoint

        viewModel = ScheduleViewModel()
    }

    @Test
    fun `load groups and sorts calendar entries by weekday and date`() {
        endpoint.stubSuccess(listOf(mondayLater, mondayEarlier, tuesdayEntry))

        viewModel.load()

        assertEquals(
            mapOf(
                CalendarDay.MONDAY to listOf(mondayEarlier, mondayLater),
                CalendarDay.TUESDAY to listOf(tuesdayEntry),
            ),
            viewModel.data.value,
        )
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        endpoint.stubSuccess(listOf(tuesdayEntry))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        endpoint.stubSuccess(listOf(mondayEarlier))
        viewModel.load()
        assertEquals(mapOf(CalendarDay.MONDAY to listOf(mondayEarlier)), viewModel.data.value)

        endpoint.stubSuccess(listOf(tuesdayEntry))
        viewModel.reload()

        assertEquals(mapOf(CalendarDay.TUESDAY to listOf(tuesdayEntry)), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `isLoginRequired is false so validateLogin is never called`() {
        endpoint.stubSuccess(listOf(tuesdayEntry))

        viewModel.load()

        verify(exactly = 0) { validators.validateLogin() }
        assertNotNull(viewModel.data.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.anime.schedule.ScheduleViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/anime/schedule/ScheduleViewModelTest.kt
git commit -m "test: add ScheduleViewModel unit tests"
```

---

### Task 5: Unit tests — ConferenceViewModel

**Goal:** Verify `ConferenceViewModel`'s reactive `MediatorLiveData` source (backed by `MessengerDao`) correctly reflects the underlying Room `LiveData`, honors the login/synchronization guards, handles `MessengerErrorEvent`s from the bus, and re-sources when `searchQuery` changes. This VM does not use the normal `load()`/`dataSingle` success path (`dataSingle` triggers `MessengerWorker` and returns `Single.never()`), so it is tested entirely through its reactive `data` source and public API.

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/base/FakeAppModule.kt` (add a `MessengerDao` singleton)
- Create: `src/test/kotlin/me/proxer/app/chat/prv/conference/ConferenceViewModelTest.kt`

**Acceptance Criteria:**
- [ ] A source emission with relevant data (non-empty, or synchronized) populates `data` and clears `isLoading`/`error`
- [ ] An empty source emission with `areConferencesSynchronized = false` does not update `data`
- [ ] A source emission is ignored when `storageHelper.isLoggedIn == false`
- [ ] A `MessengerErrorEvent` posted while `isLoading == true` clears `data`, sets `error`, and sets `isLoading = false`
- [ ] A `MessengerErrorEvent` posted while not loading is ignored (`error` stays `null`)
- [ ] Changing `searchQuery` switches the underlying `MessengerDao` source
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.chat.prv.conference.ConferenceViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Extend `src/test/kotlin/me/proxer/app/base/FakeAppModule.kt` with a `MessengerDao` singleton**

```kotlin
package me.proxer.app.base

import com.rubengees.rxbus.RxBus
import io.mockk.mockk
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.util.Validators
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import org.koin.dsl.module

fun fakeAppModule() = module {
    single<StorageHelper> { mockk(relaxed = true) }
    single<PreferenceHelper> { mockk(relaxed = true) }
    single<ProxerApi> { mockk(relaxed = true) }
    single<RxBus> { RxBus() }
    single<Validators> { mockk(relaxed = true) }
    single<MessengerDao> { mockk(relaxed = true) }
}
```

- [ ] **Step 2: Create `src/test/kotlin/me/proxer/app/chat/prv/conference/ConferenceViewModelTest.kt`**

```kotlin
package me.proxer.app.chat.prv.conference

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.rubengees.rxbus.RxBus
import io.mockk.every
import io.mockk.verify
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.chat.prv.ConferenceWithMessage
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.chat.prv.sync.MessengerErrorEvent
import me.proxer.app.exception.ChatSynchronizationException
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import org.threeten.bp.Instant
import java.io.IOException

class ConferenceViewModelTest : KoinTest {

    private companion object {
        private const val SEARCH_QUERY = ""
    }

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()
    private val messengerDao: MessengerDao by inject()
    private val bus: RxBus by inject()

    private lateinit var conferencesLiveData: MutableLiveData<List<ConferenceWithMessage>>
    private lateinit var viewModel: ConferenceViewModel

    private val localConference = LocalConference(
        id = 1L,
        topic = "friend",
        customTopic = "",
        participantAmount = 2,
        image = "",
        imageType = "",
        isGroup = false,
        localIsRead = false,
        isRead = false,
        date = Instant.now(),
        unreadMessageAmount = 0,
        lastReadMessageId = "0",
        isFullyLoaded = true,
    )

    private val conferenceWithMessage = ConferenceWithMessage(localConference, null)

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { storageHelper.areConferencesSynchronized } returns true

        conferencesLiveData = MutableLiveData()
        every { messengerDao.getConferencesLiveData(any()) } returns conferencesLiveData

        viewModel = ConferenceViewModel(SEARCH_QUERY)
    }

    @Test
    fun `source emission with data populates data and clears loading state`() {
        conferencesLiveData.value = listOf(conferenceWithMessage)

        assertEquals(listOf(conferenceWithMessage), viewModel.data.value)
        assertNull(viewModel.error.value)
        assertEquals(false, viewModel.isLoading.value)
    }

    @Test
    fun `empty source emission without synchronized conferences does not update data`() {
        every { storageHelper.areConferencesSynchronized } returns false

        conferencesLiveData.value = emptyList()

        assertNull(viewModel.data.value)
    }

    @Test
    fun `source emission is ignored when not logged in`() {
        every { storageHelper.isLoggedIn } returns false

        conferencesLiveData.value = listOf(conferenceWithMessage)

        assertNull(viewModel.data.value)
    }

    @Test
    fun `error event while loading clears data and sets error`() {
        viewModel.isLoading.value = true

        bus.post(MessengerErrorEvent(ChatSynchronizationException(IOException("sync failed"))))

        assertNull(viewModel.data.value)
        assertEquals(false, viewModel.isLoading.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `error event is ignored when not loading`() {
        viewModel.isLoading.value = false

        bus.post(MessengerErrorEvent(ChatSynchronizationException(IOException("sync failed"))))

        assertNull(viewModel.error.value)
    }

    @Test
    fun `changing searchQuery switches the underlying source`() {
        val newLiveData = MutableLiveData<List<ConferenceWithMessage>>()
        every { messengerDao.getConferencesLiveData("new query") } returns newLiveData

        viewModel.searchQuery = "new query"
        verify { messengerDao.getConferencesLiveData("new query") }

        newLiveData.value = listOf(conferenceWithMessage)

        assertEquals(listOf(conferenceWithMessage), viewModel.data.value)
    }
}
```

- [ ] **Step 3: Verify**

```bash
./gradlew test --tests "me.proxer.app.chat.prv.conference.ConferenceViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/me/proxer/app/base/FakeAppModule.kt src/test/kotlin/me/proxer/app/chat/prv/conference/ConferenceViewModelTest.kt
git commit -m "test: add ConferenceViewModel unit tests"
```

---

### Task 6: Unit tests — ConferenceInfoViewModel

**Goal:** Verify `ConferenceInfoViewModel`'s standard `BaseContentViewModel` load/error/reload state machine against `api.messenger.conferenceInfo(conferenceId)`.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/chat/prv/conference/info/ConferenceInfoViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets `data` on success
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.chat.prv.conference.info.ConferenceInfoViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/chat/prv/conference/info/ConferenceInfoViewModelTest.kt`**

```kotlin
package me.proxer.app.chat.prv.conference.info

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.Endpoint
import me.proxer.library.entity.messenger.ConferenceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.util.Date

class ConferenceInfoViewModelTest : KoinTest {

    private companion object {
        private const val CONFERENCE_ID = "777"
    }

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val endpoint = mockk<Endpoint<ConferenceInfo>>(relaxed = true)

    private lateinit var viewModel: ConferenceInfoViewModel

    private val firstConferenceInfo = ConferenceInfo(
        topic = "Friends",
        participantAmount = 2,
        firstMessageTime = Date(),
        lastMessageTime = Date(),
        leaderId = "1",
        participants = emptyList(),
    )

    private val secondConferenceInfo = firstConferenceInfo.copy(topic = "Updated Friends")

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { api.messenger.conferenceInfo(CONFERENCE_ID) } returns endpoint

        viewModel = ConferenceInfoViewModel(CONFERENCE_ID)
    }

    @Test
    fun `load sets data on success`() {
        endpoint.stubSuccess(firstConferenceInfo)

        viewModel.load()

        assertEquals(firstConferenceInfo, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        endpoint.stubSuccess(firstConferenceInfo)

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        endpoint.stubSuccess(firstConferenceInfo)
        viewModel.load()
        assertEquals(firstConferenceInfo, viewModel.data.value)

        endpoint.stubSuccess(secondConferenceInfo)
        viewModel.reload()

        assertEquals(secondConferenceInfo, viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.chat.prv.conference.info.ConferenceInfoViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/chat/prv/conference/info/ConferenceInfoViewModelTest.kt
git commit -m "test: add ConferenceInfoViewModel unit tests"
```

---

### Task 7: Unit tests — ChatRoomViewModel

**Goal:** Verify `ChatRoomViewModel`'s load/error/reload behavior against `api.chat.publicRooms()`, plus its logged-in-only merge with `api.chat.userRooms()` (dedupe by id, sort by id).

**Files:**
- Create: `src/test/kotlin/me/proxer/app/chat/pub/room/ChatRoomViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets `data` (distinct, sorted by id) on success when not logged in (only `publicRooms()` used)
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] When logged in, `publicRooms()` and `userRooms()` results are merged, deduplicated by id, and sorted by id
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.chat.pub.room.ChatRoomViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/chat/pub/room/ChatRoomViewModelTest.kt`**

```kotlin
package me.proxer.app.chat.pub.room

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.chat.PublicChatRoomsEndpoint
import me.proxer.library.api.chat.UserChatRoomsEndpoint
import me.proxer.library.entity.chat.ChatRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class ChatRoomViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val publicRoomsEndpoint = mockk<PublicChatRoomsEndpoint>(relaxed = true)
    private val userRoomsEndpoint = mockk<UserChatRoomsEndpoint>(relaxed = true)

    private lateinit var viewModel: ChatRoomViewModel

    private val roomOne = ChatRoom(id = "1", name = "General", topic = "Talk", isReadOnly = false)
    private val roomTwo = ChatRoom(id = "2", name = "Anime", topic = "Anime talk", isReadOnly = false)
    private val roomThree = ChatRoom(id = "3", name = "VIP", topic = "VIP talk", isReadOnly = true)

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns false
        every { api.chat.publicRooms() } returns publicRoomsEndpoint
        every { api.chat.userRooms() } returns userRoomsEndpoint

        viewModel = ChatRoomViewModel()
    }

    @Test
    fun `load sets data on success`() {
        publicRoomsEndpoint.stubSuccess(listOf(roomTwo, roomOne))

        viewModel.load()

        assertEquals(listOf(roomOne, roomTwo), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        publicRoomsEndpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        publicRoomsEndpoint.stubSuccess(listOf(roomOne))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        publicRoomsEndpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        publicRoomsEndpoint.stubSuccess(listOf(roomOne))
        viewModel.load()
        assertEquals(listOf(roomOne), viewModel.data.value)

        publicRoomsEndpoint.stubSuccess(listOf(roomOne, roomTwo))
        viewModel.reload()

        assertEquals(listOf(roomOne, roomTwo), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load merges and deduplicates public and user rooms when logged in`() {
        every { storageHelper.isLoggedIn } returns true
        publicRoomsEndpoint.stubSuccess(listOf(roomOne, roomTwo))
        userRoomsEndpoint.stubSuccess(listOf(roomTwo, roomThree))

        viewModel.load()

        assertEquals(listOf(roomOne, roomTwo, roomThree), viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.chat.pub.room.ChatRoomViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/chat/pub/room/ChatRoomViewModelTest.kt
git commit -m "test: add ChatRoomViewModel unit tests"
```

---

### Task 8: Unit tests — ChatRoomInfoViewModel

**Goal:** Verify `ChatRoomInfoViewModel`'s sorted (moderators first, then by name) load/error/reload behavior against `api.chat.roomUsers(chatRoomId)`, and its polling controls. `dataSingle`'s `doOnSuccess` starts a 10s `repeatWhen`/`retryWhen`/`delaySubscription` poll on the computation scheduler; since `RxTrampolineRule`'s trampoline scheduler honors real delays (verified empirically), letting that poll actually run under the rule would either block the test for 10+ real seconds or, worse, recurse forever (the `pollingDisposable` guard reads `null` reentrantly before the assignment completes). To keep the test both correct and fast, computation is pinned to a fresh, never-advanced `TestScheduler` so the pending poll is scheduled but never fires.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/chat/pub/room/info/ChatRoomInfoViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets `data` sorted with moderators first, then by name, on success
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `pausePolling()`/`resumePolling()` do not throw and preserve loaded data
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.chat.pub.room.info.ChatRoomInfoViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/chat/pub/room/info/ChatRoomInfoViewModelTest.kt`**

```kotlin
package me.proxer.app.chat.pub.room.info

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.Endpoint
import me.proxer.library.entity.chat.ChatRoomUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class ChatRoomInfoViewModelTest : KoinTest {

    private companion object {
        private const val CHAT_ROOM_ID = "555"
    }

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val endpoint = mockk<Endpoint<List<ChatRoomUser>>>(relaxed = true)

    private lateinit var viewModel: ChatRoomInfoViewModel

    private val moderator = ChatRoomUser(id = "1", name = "Zed", image = "", status = "online", isModerator = true)
    private val regular = ChatRoomUser(id = "2", name = "Amy", image = "", status = "offline", isModerator = false)

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { api.chat.roomUsers(CHAT_ROOM_ID) } returns endpoint

        // The real dataSingle starts a repeating 10s poll (delaySubscription/repeatWhen/retryWhen, all on the
        // computation scheduler) after every successful load. Pinning computation to a manually-driven
        // TestScheduler (instead of RxTrampolineRule's trampoline, which honors real delays) keeps that pending
        // poll from ever firing during the test, since it is never advanced.
        RxJavaPlugins.setComputationSchedulerHandler { TestScheduler() }

        viewModel = ChatRoomInfoViewModel(CHAT_ROOM_ID)
    }

    @Test
    fun `load sets sorted data on success`() {
        endpoint.stubSuccess(listOf(regular, moderator))

        viewModel.load()

        assertEquals(listOf(moderator, regular), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        endpoint.stubSuccess(listOf(moderator))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        endpoint.stubSuccess(listOf(moderator))
        viewModel.load()
        assertEquals(listOf(moderator), viewModel.data.value)

        endpoint.stubSuccess(listOf(moderator, regular))
        viewModel.reload()

        assertEquals(listOf(moderator, regular), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `pausePolling and resumePolling do not throw and preserve loaded data`() {
        endpoint.stubSuccess(listOf(moderator, regular))
        viewModel.load()

        viewModel.pausePolling()
        viewModel.resumePolling()

        assertEquals(listOf(moderator, regular), viewModel.data.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.chat.pub.room.info.ChatRoomInfoViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/chat/pub/room/info/ChatRoomInfoViewModelTest.kt
git commit -m "test: add ChatRoomInfoViewModel unit tests"
```

---

### Task 9: Unit tests — EditCommentViewModel

**Goal:** Verify `EditCommentViewModel`'s load/error/reload behavior against `api.comment.comment(id, entryId)`, its `onErrorResumeNext` fallback to `defaultComment` for a `COMMENT_INVALID_COMMENT` server error, and its constructor invariant requiring `id` or `entryId`.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/comment/EditCommentViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets `data` (mapped via `toLocalComment()`) on success when loading an existing comment by id
- [ ] `load()` sets `error` on a generic failure
- [ ] `load()` resolves a `COMMENT_INVALID_COMMENT` server error to the `defaultComment` instead of setting `error`
- [ ] `isLoading` is `false` after a successful load
- [ ] `reload()` clears data/error then loads new data
- [ ] The constructor throws `IllegalArgumentException` when both `id` and `entryId` are `null`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.comment.EditCommentViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/comment/EditCommentViewModelTest.kt`**

```kotlin
package me.proxer.app.comment

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.toLocalComment
import me.proxer.library.ProxerApi
import me.proxer.library.ProxerException
import me.proxer.library.api.Endpoint
import me.proxer.library.entity.info.Comment
import me.proxer.library.entity.info.RatingDetails
import me.proxer.library.enums.UserMediaProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.util.Date

class EditCommentViewModelTest : KoinTest {

    private companion object {
        private const val COMMENT_ID = "42"
        private const val ENTRY_ID = "100"
    }

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val commentEndpoint = mockk<Endpoint<Comment>>(relaxed = true)

    private lateinit var viewModel: EditCommentViewModel

    private val remoteComment = Comment(
        id = COMMENT_ID,
        entryId = ENTRY_ID,
        authorId = "7",
        mediaProgress = UserMediaProgress.WATCHED,
        ratingDetails = RatingDetails(genre = 1, story = 2, animation = 3, characters = 4, music = 5),
        content = "Great show",
        overallRating = 8,
        episode = 5,
        helpfulVotes = 3,
        date = Date(),
        author = "Someone",
        image = "avatar.png",
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { api.comment.comment(COMMENT_ID, null) } returns commentEndpoint

        viewModel = EditCommentViewModel(COMMENT_ID, null)
    }

    @Test
    fun `load sets data on success`() {
        commentEndpoint.stubSuccess(remoteComment)

        viewModel.load()

        assertEquals(remoteComment.toLocalComment(), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        commentEndpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `load resolves invalid comment error to default comment`() {
        every { api.comment.comment(null, ENTRY_ID) } returns commentEndpoint
        commentEndpoint.stubError(
            ProxerException(ProxerException.ErrorType.SERVER, ProxerException.ServerErrorType.COMMENT_INVALID_COMMENT),
        )
        val newCommentViewModel = EditCommentViewModel(null, ENTRY_ID)

        newCommentViewModel.load()

        val expectedDefault = LocalComment(
            id = "",
            entryId = "",
            mediaProgress = UserMediaProgress.WATCHED,
            ratingDetails = RatingDetails(genre = 0, story = 0, animation = 0, characters = 0, music = 0),
            content = "",
            overallRating = 0,
            episode = 0,
        )

        assertEquals(expectedDefault, newCommentViewModel.data.value)
        assertNull(newCommentViewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        commentEndpoint.stubSuccess(remoteComment)

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        commentEndpoint.stubError()
        viewModel.load()
        assertNotNull(viewModel.error.value)

        commentEndpoint.stubSuccess(remoteComment)
        viewModel.reload()

        assertEquals(remoteComment.toLocalComment(), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor throws when both id and entryId are null`() {
        EditCommentViewModel(null, null)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.comment.EditCommentViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/comment/EditCommentViewModelTest.kt
git commit -m "test: add EditCommentViewModel unit tests"
```

---

### Task 10: Unit tests — IndustryInfoViewModel

**Goal:** Verify `IndustryInfoViewModel`'s standard `BaseContentViewModel` load/error/reload state machine against `api.info.industry(industryId)`.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/info/industry/IndustryInfoViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets `data` on success
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.info.industry.IndustryInfoViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/info/industry/IndustryInfoViewModelTest.kt`**

```kotlin
package me.proxer.app.info.industry

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.Endpoint
import me.proxer.library.entity.info.Industry
import me.proxer.library.enums.Country
import me.proxer.library.enums.IndustryType
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class IndustryInfoViewModelTest : KoinTest {

    private companion object {
        private const val INDUSTRY_ID = "321"
    }

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val endpoint = mockk<Endpoint<Industry>>(relaxed = true)

    private lateinit var viewModel: IndustryInfoViewModel

    private val firstIndustry = Industry(
        id = INDUSTRY_ID,
        name = "Test Studio",
        type = IndustryType.STUDIO,
        country = Country.JAPAN,
        link = "https://proxer.me".toHttpUrl(),
        description = "A description",
    )

    private val secondIndustry = firstIndustry.copy(name = "Updated Studio")

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { api.info.industry(INDUSTRY_ID) } returns endpoint

        viewModel = IndustryInfoViewModel(INDUSTRY_ID)
    }

    @Test
    fun `load sets data on success`() {
        endpoint.stubSuccess(firstIndustry)

        viewModel.load()

        assertEquals(firstIndustry, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        endpoint.stubSuccess(firstIndustry)

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        endpoint.stubSuccess(firstIndustry)
        viewModel.load()
        assertEquals(firstIndustry, viewModel.data.value)

        endpoint.stubSuccess(secondIndustry)
        viewModel.reload()

        assertEquals(secondIndustry, viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.info.industry.IndustryInfoViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/info/industry/IndustryInfoViewModelTest.kt
git commit -m "test: add IndustryInfoViewModel unit tests"
```

---

### Task 11: Unit tests — TranslatorGroupInfoViewModel

**Goal:** Verify `TranslatorGroupInfoViewModel`'s standard `BaseContentViewModel` load/error/reload state machine against `api.info.translatorGroup(translatorGroupId)`.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupInfoViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets `data` on success
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.info.translatorgroup.TranslatorGroupInfoViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupInfoViewModelTest.kt`**

```kotlin
package me.proxer.app.info.translatorgroup

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.Endpoint
import me.proxer.library.entity.info.TranslatorGroup
import me.proxer.library.enums.Country
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class TranslatorGroupInfoViewModelTest : KoinTest {

    private companion object {
        private const val TRANSLATOR_GROUP_ID = "654"
    }

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val endpoint = mockk<Endpoint<TranslatorGroup>>(relaxed = true)

    private lateinit var viewModel: TranslatorGroupInfoViewModel

    private val firstTranslatorGroup = TranslatorGroup(
        id = TRANSLATOR_GROUP_ID,
        name = "Test Group",
        country = Country.GERMANY,
        image = "group.png",
        link = "https://proxer.me".toHttpUrl(),
        description = "A description",
        clicks = 1000,
        projectAmount = 12,
    )

    private val secondTranslatorGroup = firstTranslatorGroup.copy(name = "Updated Group")

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { api.info.translatorGroup(TRANSLATOR_GROUP_ID) } returns endpoint

        viewModel = TranslatorGroupInfoViewModel(TRANSLATOR_GROUP_ID)
    }

    @Test
    fun `load sets data on success`() {
        endpoint.stubSuccess(firstTranslatorGroup)

        viewModel.load()

        assertEquals(firstTranslatorGroup, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        endpoint.stubSuccess(firstTranslatorGroup)

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        endpoint.stubSuccess(firstTranslatorGroup)
        viewModel.load()
        assertEquals(firstTranslatorGroup, viewModel.data.value)

        endpoint.stubSuccess(secondTranslatorGroup)
        viewModel.reload()

        assertEquals(secondTranslatorGroup, viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.info.translatorgroup.TranslatorGroupInfoViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupInfoViewModelTest.kt
git commit -m "test: add TranslatorGroupInfoViewModel unit tests"
```

---

### Task 12: Unit tests — MangaViewModel

**Goal:** Verify `MangaViewModel`'s `dataSingle` (entry + chapter pipeline) success/error/reload behavior and its age-confirmation error mapping (which, unlike `AnimeViewModel`, only checks `preferenceHelper.isAgeRestrictedMediaAllowed`, not login state), using Koin test fakes and RxJava scheduler overrides.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/manga/MangaViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets `data` to the mapped `MangaChapterInfo` on success
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] An age-restricted entry with `isAgeRestrictedMediaAllowed = false` sets `error.value?.buttonAction == ButtonAction.AGE_CONFIRMATION`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.manga.MangaViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/manga/MangaViewModelTest.kt`**

```kotlin
package me.proxer.app.manga

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.info.EntryCoreEndpoint
import me.proxer.library.api.manga.ChapterEndpoint
import me.proxer.library.entity.info.AdaptionInfo
import me.proxer.library.entity.info.EntryCore
import me.proxer.library.entity.manga.Chapter
import me.proxer.library.enums.Category
import me.proxer.library.enums.Language
import me.proxer.library.enums.License
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.util.Date

class MangaViewModelTest : KoinTest {

    private companion object {
        private const val ENTRY_ID = "9001"
        private val LANGUAGE = Language.GERMAN
        private const val EPISODE = 3
    }

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val entryEndpoint = mockk<EntryCoreEndpoint>(relaxed = true)
    private val chapterEndpoint = mockk<ChapterEndpoint>(relaxed = true)

    private lateinit var viewModel: MangaViewModel

    // Shared so repeated chapter() calls stay structurally equal (Date() is otherwise non-deterministic).
    private val fixedDate = Date()

    private fun entryCore(medium: Medium = Medium.MANGASERIES) = EntryCore(
        id = ENTRY_ID,
        name = "Test Manga",
        genres = emptySet(),
        fskConstraints = emptySet(),
        description = "A description",
        medium = medium,
        episodeAmount = 30,
        state = MediaState.FINISHED,
        ratingSum = 80,
        ratingAmount = 16,
        clicks = 200,
        category = Category.MANGA,
        license = License.UNKNOWN,
        adaptionInfo = AdaptionInfo("50", "Related Anime", Medium.ANIMESERIES),
    )

    private fun chapter() = Chapter(
        id = "1",
        entryId = ENTRY_ID,
        title = "Chapter 3",
        uploaderId = "7",
        uploaderName = "Uploader",
        date = fixedDate,
        scanGroupId = "1",
        scanGroupName = "Scan Group",
        server = "https://example.com/manga",
        // Non-null pages makes Chapter.isOfficial false regardless of the server host, avoiding the
        // MangaLinkException/MangaNotAvailableException branches for this basic load/error/reload coverage.
        pages = emptyList(),
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns true

        every { api.info.entryCore(ENTRY_ID) } returns entryEndpoint
        every { api.manga.chapter(ENTRY_ID, EPISODE, LANGUAGE) } returns chapterEndpoint

        viewModel = MangaViewModel(ENTRY_ID, LANGUAGE, EPISODE)
    }

    @Test
    fun `load sets data on success`() {
        entryEndpoint.stubSuccess(entryCore())
        chapterEndpoint.stubSuccess(chapter())

        viewModel.load()

        assertEquals(MangaChapterInfo(chapter(), "Test Manga", 30), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        entryEndpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        entryEndpoint.stubSuccess(entryCore())
        chapterEndpoint.stubSuccess(chapter())

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        entryEndpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        // The first attempt fails so entrySingle() never caches an EntryCore, keeping the reload observable
        // (once cached, entrySingle() bypasses api.info.entryCore() entirely on subsequent loads).
        entryEndpoint.stubError()
        viewModel.load()
        assertNotNull(viewModel.error.value)

        entryEndpoint.stubSuccess(entryCore())
        chapterEndpoint.stubSuccess(chapter())
        viewModel.reload()

        assertEquals(MangaChapterInfo(chapter(), "Test Manga", 30), viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets age confirmation error for age restricted entry when not confirmed`() {
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
        entryEndpoint.stubSuccess(entryCore(medium = Medium.HMANGA))

        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.AGE_CONFIRMATION, viewModel.error.value?.buttonAction)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.manga.MangaViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/manga/MangaViewModelTest.kt
git commit -m "test: add MangaViewModel unit tests"
```

---
### Task 13: Unit tests — MediaInfoViewModel

**Goal:** Verify `MediaInfoViewModel`'s load/error/reload state machine plus its `doAfterSuccess` `userInfoData` population when the user is logged in.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/media/MediaInfoViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets `data` to the fetched `Entry` on success
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears `data`/`error` then loads new data
- [ ] `userInfoData` is populated via `api.info.userInfo(entryId)` after a successful load when `storageHelper.isLoggedIn` is `true`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.media.MediaInfoViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/media/MediaInfoViewModelTest.kt`**

```kotlin
package me.proxer.app.media

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.info.EntryEndpoint
import me.proxer.library.api.info.MediaUserInfoEndpoint
import me.proxer.library.entity.info.AdaptionInfo
import me.proxer.library.entity.info.Entry
import me.proxer.library.entity.info.MediaUserInfo
import me.proxer.library.enums.Category
import me.proxer.library.enums.FskConstraint
import me.proxer.library.enums.License
import me.proxer.library.enums.MediaLanguage
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class MediaInfoViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val entryId = "12345"

    private lateinit var viewModel: MediaInfoViewModel

    private fun createEntry(
        id: String = entryId,
        isAgeRestricted: Boolean = false,
        fskConstraints: Set<FskConstraint> = emptySet(),
    ) = Entry(
        id = id,
        name = "Test Anime",
        fskConstraints = fskConstraints,
        description = "A test description",
        medium = Medium.ANIMESERIES,
        episodeAmount = 12,
        state = MediaState.FINISHED,
        ratingSum = 500,
        ratingAmount = 100,
        clicks = 10000,
        category = Category.ANIME,
        license = License.LICENSED,
        adaptionInfo = AdaptionInfo(id = "99999", name = "Adaption", medium = Medium.MANGASERIES),
        isAgeRestricted = isAgeRestricted,
        synonyms = emptyList(),
        languages = setOf(MediaLanguage.GERMAN_SUB),
        seasons = emptyList(),
        translatorGroups = emptyList(),
        industries = emptyList(),
        tags = emptyList(),
        genres = emptyList(),
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns false
        viewModel = MediaInfoViewModel(entryId)
    }

    @Test
    fun `load sets data on success`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)
        val entry = createEntry()

        every { api.info.entry(entryId) } returns entryEndpoint
        entryEndpoint.stubSuccess(entry)

        viewModel.load()

        assertEquals(entry, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)

        every { api.info.entry(entryId) } returns entryEndpoint
        entryEndpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)

        every { api.info.entry(entryId) } returns entryEndpoint
        entryEndpoint.stubSuccess(createEntry())

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)

        every { api.info.entry(entryId) } returns entryEndpoint
        entryEndpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)
        val firstEntry = createEntry()
        val secondEntry = createEntry(fskConstraints = setOf(FskConstraint.FSK_16))

        every { api.info.entry(entryId) } returns entryEndpoint
        entryEndpoint.stubSuccess(firstEntry)
        viewModel.load()
        assertEquals(firstEntry, viewModel.data.value)

        entryEndpoint.stubSuccess(secondEntry)
        viewModel.reload()

        assertEquals(secondEntry, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `userInfoData is populated after successful load when logged in`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)
        val userInfoEndpoint = mockk<MediaUserInfoEndpoint>(relaxed = true)
        val entry = createEntry()
        val mediaUserInfo = MediaUserInfo(
            isNoted = true,
            isFinished = false,
            isCanceled = false,
            isTopTen = false,
            isSubscribed = false,
        )

        every { storageHelper.isLoggedIn } returns true
        every { api.info.entry(entryId) } returns entryEndpoint
        every { api.info.userInfo(entryId) } returns userInfoEndpoint
        entryEndpoint.stubSuccess(entry)
        userInfoEndpoint.stubSuccess(mediaUserInfo)

        viewModel.load()

        assertEquals(entry, viewModel.data.value)
        assertEquals(mediaUserInfo, viewModel.userInfoData.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.media.MediaInfoViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/media/MediaInfoViewModelTest.kt
git commit -m "test: add MediaInfoViewModel unit tests"
```

---

### Task 14: Unit tests — DiscussionViewModel

**Goal:** Verify `DiscussionViewModel`'s (`BaseContentViewModel<List<ForumDiscussion>>`) load/error/reload state machine.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/media/discussion/DiscussionViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets `data` to the fetched list of `ForumDiscussion` on success
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears `data`/`error` then loads new data
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.media.discussion.DiscussionViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/media/discussion/DiscussionViewModelTest.kt`**

```kotlin
package me.proxer.app.media.discussion

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.info.ForumDiscussionsEndpoint
import me.proxer.library.entity.info.ForumDiscussion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.util.Date

class DiscussionViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val entryId = "12345"

    private lateinit var viewModel: DiscussionViewModel

    private fun createDiscussion(id: String) = ForumDiscussion(
        id = id,
        categoryId = "10",
        categoryName = "General",
        subject = "Test Discussion $id",
        postAmount = 5,
        hits = 100,
        firstPostDate = Date(0),
        firstPostUserId = "1",
        firstPostUsername = "user1",
        lastPostDate = Date(0),
        lastPostUserId = "2",
        lastPostUsername = "user2",
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        viewModel = DiscussionViewModel(entryId)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockk<ForumDiscussionsEndpoint>(relaxed = true)
        val discussions = listOf(createDiscussion("1"), createDiscussion("2"))

        every { api.info.forumDiscussions(entryId) } returns endpoint
        endpoint.stubSuccess(discussions)

        viewModel.load()

        assertEquals(discussions, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockk<ForumDiscussionsEndpoint>(relaxed = true)

        every { api.info.forumDiscussions(entryId) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockk<ForumDiscussionsEndpoint>(relaxed = true)

        every { api.info.forumDiscussions(entryId) } returns endpoint
        endpoint.stubSuccess(listOf(createDiscussion("1")))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockk<ForumDiscussionsEndpoint>(relaxed = true)

        every { api.info.forumDiscussions(entryId) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockk<ForumDiscussionsEndpoint>(relaxed = true)
        val firstDiscussions = listOf(createDiscussion("1"))
        val secondDiscussions = listOf(createDiscussion("2"), createDiscussion("3"))

        every { api.info.forumDiscussions(entryId) } returns endpoint
        endpoint.stubSuccess(firstDiscussions)
        viewModel.load()
        assertEquals(firstDiscussions, viewModel.data.value)

        endpoint.stubSuccess(secondDiscussions)
        viewModel.reload()

        assertEquals(secondDiscussions, viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.media.discussion.DiscussionViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/media/discussion/DiscussionViewModelTest.kt
git commit -m "test: add DiscussionViewModel unit tests"
```

---

### Task 15: Unit tests — EpisodeViewModel

**Goal:** Verify `EpisodeViewModel`'s load/error/reload state machine (episode grouping into `EpisodeRow`s via the `EpisodeInfoEndpoint`, a `PagingLimitEndpoint`) plus the `bookmark()` side-effect method.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/media/episode/EpisodeViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` groups episodes by number into sorted `EpisodeRow`s on success
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears `data`/`error` then loads new data
- [ ] `bookmark()` sets `bookmarkData` on success via `api.ucp.setBookmark(...)`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.media.episode.EpisodeViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/media/episode/EpisodeViewModelTest.kt`**

```kotlin
package me.proxer.app.media.episode

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.info.EpisodeInfoEndpoint
import me.proxer.library.api.ucp.SetBookmarkEndpoint
import me.proxer.library.entity.info.AnimeEpisode
import me.proxer.library.entity.info.Episode
import me.proxer.library.entity.info.EpisodeInfo
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class EpisodeViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val entryId = "12345"

    private lateinit var viewModel: EpisodeViewModel

    private fun createEpisodeInfo(episodes: List<Episode>) = EpisodeInfo(
        firstEpisode = 1,
        lastEpisode = episodes.size,
        category = Category.ANIME,
        availableLanguages = setOf(MediaLanguage.GERMAN_SUB),
        userProgress = 0,
        episodes = episodes,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        viewModel = EpisodeViewModel(entryId)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockk<EpisodeInfoEndpoint>(relaxed = true)
        val episodes = listOf(
            AnimeEpisode(1, MediaLanguage.GERMAN_SUB, setOf("hoster1"), listOf("image1")),
            AnimeEpisode(2, MediaLanguage.GERMAN_SUB, setOf("hoster1"), listOf("image1")),
        )

        every { api.info.episodeInfo(entryId) } returns endpoint
        endpoint.stubPagingSuccess(createEpisodeInfo(episodes))

        viewModel.load()

        assertEquals(2, viewModel.data.value?.size)
        assertEquals(listOf(1, 2), viewModel.data.value?.map { it.number })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockk<EpisodeInfoEndpoint>(relaxed = true)

        every { api.info.episodeInfo(entryId) } returns endpoint
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockk<EpisodeInfoEndpoint>(relaxed = true)

        every { api.info.episodeInfo(entryId) } returns endpoint
        endpoint.stubPagingSuccess(createEpisodeInfo(emptyList()))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockk<EpisodeInfoEndpoint>(relaxed = true)

        every { api.info.episodeInfo(entryId) } returns endpoint
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockk<EpisodeInfoEndpoint>(relaxed = true)
        val firstEpisodes = listOf(AnimeEpisode(1, MediaLanguage.GERMAN_SUB, emptySet(), emptyList()))
        val secondEpisodes = listOf(
            AnimeEpisode(1, MediaLanguage.GERMAN_SUB, emptySet(), emptyList()),
            AnimeEpisode(2, MediaLanguage.GERMAN_SUB, emptySet(), emptyList()),
        )

        every { api.info.episodeInfo(entryId) } returns endpoint
        endpoint.stubPagingSuccess(createEpisodeInfo(firstEpisodes))
        viewModel.load()
        assertEquals(1, viewModel.data.value?.size)

        endpoint.stubPagingSuccess(createEpisodeInfo(secondEpisodes))
        viewModel.reload()

        assertEquals(2, viewModel.data.value?.size)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `bookmark sets bookmarkData on success`() {
        val bookmarkEndpoint = mockk<SetBookmarkEndpoint>(relaxed = true)

        every {
            api.ucp.setBookmark(entryId, 1, MediaLanguage.GERMAN_SUB, Category.ANIME)
        } returns bookmarkEndpoint
        bookmarkEndpoint.stubSuccess(Unit)

        viewModel.bookmark(1, MediaLanguage.GERMAN_SUB, Category.ANIME)

        assertEquals(Unit, viewModel.bookmarkData.value)
        assertNull(viewModel.bookmarkError.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.media.episode.EpisodeViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/media/episode/EpisodeViewModelTest.kt
git commit -m "test: add EpisodeViewModel unit tests"
```

---

### Task 16: Unit tests — RecommendationViewModel

**Goal:** Verify `RecommendationViewModel`'s (`BaseContentViewModel<List<Recommendation>>`) load/error/reload state machine.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/media/recommendation/RecommendationViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets `data` to the fetched list of `Recommendation` on success
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears `data`/`error` then loads new data
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.media.recommendation.RecommendationViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/media/recommendation/RecommendationViewModelTest.kt`**

```kotlin
package me.proxer.app.media.recommendation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.info.RecommendationsEndpoint
import me.proxer.library.entity.info.Recommendation
import me.proxer.library.enums.Category
import me.proxer.library.enums.License
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class RecommendationViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val entryId = "12345"

    private lateinit var viewModel: RecommendationViewModel

    private fun createRecommendation(id: String) = Recommendation(
        id = id,
        name = "Recommended Anime $id",
        genres = setOf("Action"),
        fskConstraints = emptySet(),
        description = "A description",
        medium = Medium.ANIMESERIES,
        episodeAmount = 12,
        state = MediaState.FINISHED,
        ratingSum = 100,
        ratingAmount = 20,
        clicks = 500,
        category = Category.ANIME,
        license = License.LICENSED,
        positiveVotes = 10,
        negativeVotes = 2,
        userVote = null,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        viewModel = RecommendationViewModel(entryId)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockk<RecommendationsEndpoint>(relaxed = true)
        val recommendations = listOf(createRecommendation("1"), createRecommendation("2"))

        every { api.info.recommendations(entryId) } returns endpoint
        endpoint.stubSuccess(recommendations)

        viewModel.load()

        assertEquals(recommendations, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockk<RecommendationsEndpoint>(relaxed = true)

        every { api.info.recommendations(entryId) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockk<RecommendationsEndpoint>(relaxed = true)

        every { api.info.recommendations(entryId) } returns endpoint
        endpoint.stubSuccess(listOf(createRecommendation("1")))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockk<RecommendationsEndpoint>(relaxed = true)

        every { api.info.recommendations(entryId) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockk<RecommendationsEndpoint>(relaxed = true)
        val firstRecommendations = listOf(createRecommendation("1"))
        val secondRecommendations = listOf(createRecommendation("2"), createRecommendation("3"))

        every { api.info.recommendations(entryId) } returns endpoint
        endpoint.stubSuccess(firstRecommendations)
        viewModel.load()
        assertEquals(firstRecommendations, viewModel.data.value)

        endpoint.stubSuccess(secondRecommendations)
        viewModel.reload()

        assertEquals(secondRecommendations, viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.media.recommendation.RecommendationViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/media/recommendation/RecommendationViewModelTest.kt
git commit -m "test: add RecommendationViewModel unit tests"
```

---

### Task 17: Unit tests — RelationViewModel

**Goal:** Verify `RelationViewModel`'s (`BaseContentViewModel<List<Relation>>`) load/error/reload state machine, including its `dataSingle` override that filters the entry itself out of the results.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/media/relation/RelationViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets `data` to the fetched list of `Relation` on success
- [ ] `load()` filters the entry itself (matching `entryId`) out of the resulting list
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears `data`/`error` then loads new data
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.media.relation.RelationViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/media/relation/RelationViewModelTest.kt`**

```kotlin
package me.proxer.app.media.relation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.info.RelationsEndpoint
import me.proxer.library.entity.info.Relation
import me.proxer.library.enums.Category
import me.proxer.library.enums.License
import me.proxer.library.enums.MediaLanguage
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class RelationViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val entryId = "12345"

    private lateinit var viewModel: RelationViewModel

    private fun createRelation(id: String) = Relation(
        id = id,
        name = "Related Anime $id",
        genres = setOf("Action"),
        fskConstraints = emptySet(),
        description = "A description",
        medium = Medium.ANIMESERIES,
        episodeAmount = 12,
        state = MediaState.FINISHED,
        ratingSum = 100,
        ratingAmount = 20,
        clicks = 500,
        category = Category.ANIME,
        license = License.LICENSED,
        languages = setOf(MediaLanguage.GERMAN_SUB),
        year = 2020,
        season = null,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
        viewModel = RelationViewModel(entryId)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockk<RelationsEndpoint>(relaxed = true)
        val relations = listOf(createRelation("1"), createRelation("2"))

        every { api.info.relations(entryId) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        endpoint.stubSuccess(relations)

        viewModel.load()

        assertEquals(relations, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load filters out the entry itself from relations`() {
        val endpoint = mockk<RelationsEndpoint>(relaxed = true)
        val selfRelation = createRelation(entryId)
        val otherRelation = createRelation("2")

        every { api.info.relations(entryId) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        endpoint.stubSuccess(listOf(selfRelation, otherRelation))

        viewModel.load()

        assertEquals(listOf(otherRelation), viewModel.data.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockk<RelationsEndpoint>(relaxed = true)

        every { api.info.relations(entryId) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockk<RelationsEndpoint>(relaxed = true)

        every { api.info.relations(entryId) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        endpoint.stubSuccess(listOf(createRelation("1")))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockk<RelationsEndpoint>(relaxed = true)

        every { api.info.relations(entryId) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockk<RelationsEndpoint>(relaxed = true)
        val firstRelations = listOf(createRelation("1"))
        val secondRelations = listOf(createRelation("2"), createRelation("3"))

        every { api.info.relations(entryId) } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        endpoint.stubSuccess(firstRelations)
        viewModel.load()
        assertEquals(firstRelations, viewModel.data.value)

        endpoint.stubSuccess(secondRelations)
        viewModel.reload()

        assertEquals(secondRelations, viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.media.relation.RelationViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/media/relation/RelationViewModelTest.kt
git commit -m "test: add RelationViewModel unit tests"
```

---

### Task 18: Unit tests — ProfileViewModel

**Goal:** Verify `ProfileViewModel`'s load/error/reload state machine, including its branch that additionally fetches `watchedEpisodes` via `api.ucp.watchedEpisodes()` only when the loaded profile belongs to the currently logged-in user (`storageHelper.user?.matches(userId, username)`).

**Files:**
- Create: `src/test/kotlin/me/proxer/app/profile/ProfileViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets `data.info` to the fetched `UserInfo` and leaves `watchedEpisodes` `null` for another user's profile
- [ ] `load()` additionally fetches and sets `watchedEpisodes` when the profile matches `storageHelper.user`
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears `data`/`error` then loads new data
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.profile.ProfileViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/profile/ProfileViewModelTest.kt`**

```kotlin
package me.proxer.app.profile

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.auth.LocalUser
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.ucp.WatchedEpisodesEndpoint
import me.proxer.library.api.user.UserInfoEndpoint
import me.proxer.library.entity.user.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.util.Date

class ProfileViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val profileUserId = "54321"
    private val profileUsername = "testuser"

    private fun createUserInfo(status: String = "status") = UserInfo(
        id = profileUserId,
        username = profileUsername,
        image = "avatar.png",
        isTeamMember = false,
        isDonator = false,
        status = status,
        lastStatusChange = Date(0),
        uploadPoints = 10,
        forumPoints = 20,
        animePoints = 30,
        mangaPoints = 40,
        infoPoints = 5,
        miscPoints = 1,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { storageHelper.user } returns null
    }

    @Test
    fun `load sets data on success for another users profile`() {
        val viewModel = ProfileViewModel(profileUserId, profileUsername)
        val userInfoEndpoint = mockk<UserInfoEndpoint>(relaxed = true)
        val userInfo = createUserInfo()

        every { api.user.info(profileUserId, profileUsername) } returns userInfoEndpoint
        userInfoEndpoint.stubSuccess(userInfo)

        viewModel.load()

        assertEquals(userInfo, viewModel.data.value?.info)
        assertNull(viewModel.data.value?.watchedEpisodes)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load fetches watched episodes for own profile`() {
        every { storageHelper.user } returns LocalUser("token", profileUserId, profileUsername, "avatar.png")

        val viewModel = ProfileViewModel(profileUserId, profileUsername)
        val userInfoEndpoint = mockk<UserInfoEndpoint>(relaxed = true)
        val watchedEpisodesEndpoint = mockk<WatchedEpisodesEndpoint>(relaxed = true)
        val userInfo = createUserInfo()

        every { api.user.info(profileUserId, profileUsername) } returns userInfoEndpoint
        every { api.ucp.watchedEpisodes() } returns watchedEpisodesEndpoint
        userInfoEndpoint.stubSuccess(userInfo)
        watchedEpisodesEndpoint.stubSuccess(42)

        viewModel.load()

        assertEquals(userInfo, viewModel.data.value?.info)
        assertEquals(42, viewModel.data.value?.watchedEpisodes)
    }

    @Test
    fun `load sets error on failure`() {
        val viewModel = ProfileViewModel(profileUserId, profileUsername)
        val userInfoEndpoint = mockk<UserInfoEndpoint>(relaxed = true)

        every { api.user.info(profileUserId, profileUsername) } returns userInfoEndpoint
        userInfoEndpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val viewModel = ProfileViewModel(profileUserId, profileUsername)
        val userInfoEndpoint = mockk<UserInfoEndpoint>(relaxed = true)

        every { api.user.info(profileUserId, profileUsername) } returns userInfoEndpoint
        userInfoEndpoint.stubSuccess(createUserInfo())

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val viewModel = ProfileViewModel(profileUserId, profileUsername)
        val userInfoEndpoint = mockk<UserInfoEndpoint>(relaxed = true)

        every { api.user.info(profileUserId, profileUsername) } returns userInfoEndpoint
        userInfoEndpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val viewModel = ProfileViewModel(profileUserId, profileUsername)
        val userInfoEndpoint = mockk<UserInfoEndpoint>(relaxed = true)
        val firstUserInfo = createUserInfo(status = "First")
        val secondUserInfo = createUserInfo(status = "Second")

        every { api.user.info(profileUserId, profileUsername) } returns userInfoEndpoint
        userInfoEndpoint.stubSuccess(firstUserInfo)
        viewModel.load()
        assertEquals(firstUserInfo, viewModel.data.value?.info)

        userInfoEndpoint.stubSuccess(secondUserInfo)
        viewModel.reload()

        assertEquals(secondUserInfo, viewModel.data.value?.info)
        assertNull(viewModel.error.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.profile.ProfileViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/profile/ProfileViewModelTest.kt
git commit -m "test: add ProfileViewModel unit tests"
```

---

### Task 19: Unit tests — ProfileAboutViewModel

**Goal:** Verify `ProfileAboutViewModel`'s (`BaseContentViewModel<UserAbout>`) load/error/reload state machine.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/profile/about/ProfileAboutViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets `data` to the fetched `UserAbout` on success
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears `data`/`error` then loads new data
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.profile.about.ProfileAboutViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/profile/about/ProfileAboutViewModelTest.kt`**

```kotlin
package me.proxer.app.profile.about

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.user.UserAboutEndpoint
import me.proxer.library.entity.user.UserAbout
import me.proxer.library.enums.Gender
import me.proxer.library.enums.RelationshipStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class ProfileAboutViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val profileUserId = "54321"
    private val profileUsername = "testuser"

    private lateinit var viewModel: ProfileAboutViewModel

    private fun createUserAbout(city: String = "Berlin") = UserAbout(
        website = "https://example.com",
        occupation = "Tester",
        interests = "Anime",
        city = city,
        country = "Germany",
        about = "About me",
        facebook = "facebook.user",
        youtube = "youtube.user",
        chatango = "chatango.user",
        twitter = "twitter.user",
        skype = "skype.user",
        deviantart = "deviantart.user",
        birthday = "2000-01-01",
        gender = Gender.UNKNOWN,
        relationshipStatus = RelationshipStatus.UNKNOWN,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        viewModel = ProfileAboutViewModel(profileUserId, profileUsername)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockk<UserAboutEndpoint>(relaxed = true)
        val userAbout = createUserAbout()

        every { api.user.about(profileUserId, profileUsername) } returns endpoint
        endpoint.stubSuccess(userAbout)

        viewModel.load()

        assertEquals(userAbout, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockk<UserAboutEndpoint>(relaxed = true)

        every { api.user.about(profileUserId, profileUsername) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockk<UserAboutEndpoint>(relaxed = true)

        every { api.user.about(profileUserId, profileUsername) } returns endpoint
        endpoint.stubSuccess(createUserAbout())

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockk<UserAboutEndpoint>(relaxed = true)

        every { api.user.about(profileUserId, profileUsername) } returns endpoint
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockk<UserAboutEndpoint>(relaxed = true)
        val firstUserAbout = createUserAbout(city = "Berlin")
        val secondUserAbout = createUserAbout(city = "Munich")

        every { api.user.about(profileUserId, profileUsername) } returns endpoint
        endpoint.stubSuccess(firstUserAbout)
        viewModel.load()
        assertEquals(firstUserAbout, viewModel.data.value)

        endpoint.stubSuccess(secondUserAbout)
        viewModel.reload()

        assertEquals(secondUserAbout, viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.profile.about.ProfileAboutViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/profile/about/ProfileAboutViewModelTest.kt
git commit -m "test: add ProfileAboutViewModel unit tests"
```

---

### Task 20: Unit tests — TopTenViewModel

**Goal:** Verify `TopTenViewModel`'s load/error/reload state machine across both of its data-source branches — `api.ucp.topTen()` when viewing the logged-in user's own profile (`storageHelper.user?.matches(userId, username)`), and the zipped `api.user.topTen(userId, username).includeHentai(...).category(...)` calls for anime/manga otherwise — plus the `addItemToDelete` deletion-queue side effect.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/profile/topten/TopTenViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` for the logged-in user's own profile populates `animeEntries`/`mangaEntries` via `api.ucp.topTen()`
- [ ] `load()` for another user's profile populates `animeEntries`/`mangaEntries` via the zipped `api.user.topTen(...)` calls
- [ ] `load()` sets `error` on failure
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears `data`/`error` then loads new data
- [ ] `addItemToDelete()` removes the deleted item from `data` on success via `api.ucp.deleteFavorite(id)`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.profile.topten.TopTenViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/profile/topten/TopTenViewModelTest.kt`**

```kotlin
package me.proxer.app.profile.topten

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.auth.LocalUser
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.ucp.DeleteFavoriteEndpoint
import me.proxer.library.api.ucp.UcpTopTenEndpoint
import me.proxer.library.api.user.UserTopTenEndpoint
import me.proxer.library.entity.ucp.UcpTopTenEntry
import me.proxer.library.entity.user.TopTenEntry
import me.proxer.library.enums.Category
import me.proxer.library.enums.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class TopTenViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private val profileUserId = "54321"
    private val profileUsername = "testuser"

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { storageHelper.user } returns null
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
    }

    @Test
    fun `load own profile populates data via ucp top ten`() {
        every { storageHelper.user } returns LocalUser("token", profileUserId, profileUsername, "avatar.png")

        val viewModel = TopTenViewModel(profileUserId, profileUsername)
        val ucpTopTenEndpoint = mockk<UcpTopTenEndpoint>(relaxed = true)
        val entries = listOf(
            UcpTopTenEntry("a1", "e1", "Anime Entry", Medium.ANIMESERIES, Category.ANIME),
            UcpTopTenEntry("m1", "e2", "Manga Entry", Medium.MANGASERIES, Category.MANGA),
        )

        every { api.ucp.topTen() } returns ucpTopTenEndpoint
        ucpTopTenEndpoint.stubSuccess(entries)

        viewModel.load()

        assertEquals(1, viewModel.data.value?.animeEntries?.size)
        assertEquals(1, viewModel.data.value?.mangaEntries?.size)
        assertEquals("Anime Entry", viewModel.data.value?.animeEntries?.first()?.name)
        assertEquals("Manga Entry", viewModel.data.value?.mangaEntries?.first()?.name)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load other profile populates data via user top ten`() {
        val viewModel = TopTenViewModel(profileUserId, profileUsername)
        val baseEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)
        val animeEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)
        val mangaEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)

        every { api.user.topTen(profileUserId, profileUsername) } returns baseEndpoint
        every { baseEndpoint.includeHentai(any()) } returns baseEndpoint
        every { baseEndpoint.category(Category.ANIME) } returns animeEndpoint
        every { baseEndpoint.category(Category.MANGA) } returns mangaEndpoint
        animeEndpoint.stubSuccess(listOf(TopTenEntry("a1", "Anime Entry", Category.ANIME, Medium.ANIMESERIES)))
        mangaEndpoint.stubSuccess(listOf(TopTenEntry("m1", "Manga Entry", Category.MANGA, Medium.MANGASERIES)))

        viewModel.load()

        assertEquals(1, viewModel.data.value?.animeEntries?.size)
        assertEquals(1, viewModel.data.value?.mangaEntries?.size)
        assertEquals("Anime Entry", viewModel.data.value?.animeEntries?.first()?.name)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val viewModel = TopTenViewModel(profileUserId, profileUsername)
        val baseEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)
        val animeEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)
        val mangaEndpoint = mockk<UserTopTenEndpoint>(relaxed = true)

        every { api.user.topTen(profileUserId, profileUsername) } returns baseEndpoint
        every { baseEndpoint.includeHentai(any()) } returns baseEndpoint
        every { baseEndpoint.category(Category.ANIME) } returns animeEndpoint
        every { baseEndpoint.category(Category.MANGA) } returns mangaEndpoint
        animeEndpoint.stubError()
        mangaEndpoint.stubSuccess(listOf(TopTenEntry("m1", "Manga Entry", Category.MANGA, Medium.MANGASERIES)))

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        every { storageHelper.user } returns LocalUser("token", profileUserId, profileUsername, "avatar.png")

        val viewModel = TopTenViewModel(profileUserId, profileUsername)
        val ucpTopTenEndpoint = mockk<UcpTopTenEndpoint>(relaxed = true)

        every { api.ucp.topTen() } returns ucpTopTenEndpoint
        ucpTopTenEndpoint.stubSuccess(emptyList())

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        every { storageHelper.user } returns LocalUser("token", profileUserId, profileUsername, "avatar.png")

        val viewModel = TopTenViewModel(profileUserId, profileUsername)
        val ucpTopTenEndpoint = mockk<UcpTopTenEndpoint>(relaxed = true)

        every { api.ucp.topTen() } returns ucpTopTenEndpoint
        ucpTopTenEndpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        every { storageHelper.user } returns LocalUser("token", profileUserId, profileUsername, "avatar.png")

        val viewModel = TopTenViewModel(profileUserId, profileUsername)
        val ucpTopTenEndpoint = mockk<UcpTopTenEndpoint>(relaxed = true)

        every { api.ucp.topTen() } returns ucpTopTenEndpoint
        ucpTopTenEndpoint.stubSuccess(
            listOf(UcpTopTenEntry("a1", "e1", "First Entry", Medium.ANIMESERIES, Category.ANIME)),
        )
        viewModel.load()
        assertEquals("First Entry", viewModel.data.value?.animeEntries?.first()?.name)

        ucpTopTenEndpoint.stubSuccess(
            listOf(UcpTopTenEntry("a2", "e2", "Second Entry", Medium.ANIMESERIES, Category.ANIME)),
        )
        viewModel.reload()

        assertEquals("Second Entry", viewModel.data.value?.animeEntries?.first()?.name)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `addItemToDelete removes the item from data on success`() {
        every { storageHelper.user } returns LocalUser("token", profileUserId, profileUsername, "avatar.png")

        val viewModel = TopTenViewModel(profileUserId, profileUsername)
        val ucpTopTenEndpoint = mockk<UcpTopTenEndpoint>(relaxed = true)
        val deleteEndpoint = mockk<DeleteFavoriteEndpoint>(relaxed = true)

        every { api.ucp.topTen() } returns ucpTopTenEndpoint
        ucpTopTenEndpoint.stubSuccess(
            listOf(UcpTopTenEntry("a1", "e1", "Anime Entry", Medium.ANIMESERIES, Category.ANIME)),
        )
        viewModel.load()
        assertEquals(1, viewModel.data.value?.animeEntries?.size)

        val itemToDelete = LocalTopTenEntry.Ucp("a1", "Anime Entry", Category.ANIME, Medium.ANIMESERIES, "e1")

        every { api.ucp.deleteFavorite("a1") } returns deleteEndpoint
        deleteEndpoint.stubSuccess(Unit)

        viewModel.addItemToDelete(itemToDelete)

        assertTrue(viewModel.data.value?.animeEntries.isNullOrEmpty())
        assertNull(viewModel.itemDeletionError.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.profile.topten.TopTenViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/profile/topten/TopTenViewModelTest.kt
git commit -m "test: add TopTenViewModel unit tests"
```

---

### Task 21: Unit tests — ServerStatusViewModel

**Goal:** Verify `ServerStatusViewModel`'s load/error/reload state machine around its `OkHttpClient` + Jsoup scraping `dataSingle` (not a `ProxerApi` call). This VM overrides `isLoginRequired = false`, so unlike every other VM in this suite, `storageHelper.isLoggedIn` is deliberately left unstubbed — only the two `init`-block Observables are stubbed, since `BaseViewModel`'s `init` block subscribes to them unconditionally regardless of `isLoginRequired`.

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/base/FakeAppModule.kt` (add an `OkHttpClient` singleton, needed by `ServerStatusViewModel`'s `private val client by safeInject<OkHttpClient>()`)
- Create: `src/test/kotlin/me/proxer/app/settings/status/ServerStatusViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` scrapes the server table HTML into `List<ServerStatus>` on success
- [ ] `load()` sets `error` on failure (e.g. `Call.execute()` throwing `IOException`)
- [ ] `isLoading` is `false` after a successful load
- [ ] `isLoading` is `false` after a failed load
- [ ] `reload()` clears `data`/`error` then loads new data
- [ ] `load()` succeeds with no `storageHelper.isLoggedIn` stub at all, confirming `isLoginRequired = false` skips `validators.validateLogin()`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.settings.status.ServerStatusViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Update `src/test/kotlin/me/proxer/app/base/FakeAppModule.kt` to add the `OkHttpClient` singleton**

`MessengerDao` was already added by Task 5 — keep it alongside the new `OkHttpClient` entry (these `FakeAppModule.kt` edits are additive across tasks; never drop an existing `single<...>` line):

```kotlin
package me.proxer.app.base

import com.rubengees.rxbus.RxBus
import io.mockk.mockk
import me.proxer.app.chat.prv.sync.MessengerDao
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
}
```

- [ ] **Step 2: Create `src/test/kotlin/me/proxer/app/settings/status/ServerStatusViewModelTest.kt`**

```kotlin
package me.proxer.app.settings.status

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.io.IOException

class ServerStatusViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()
    private val client: OkHttpClient by inject()

    private lateinit var viewModel: ServerStatusViewModel

    private val onlineHtml = """
        <html><body><table>
        <tr><td>Server 1:</td><td><b>Online</b></td></tr>
        <tr><td>Server 2:</td><td><b>Offline</b></td></tr>
        </table></body></html>
    """.trimIndent()

    // NOTE: ServerStatusViewModel overrides isLoginRequired = false, so unlike every other
    // ViewModel in this suite, `storageHelper.isLoggedIn` is never consulted by validate() and
    // is deliberately left unstubbed here. Only the two init-block Observables are stubbed, since
    // BaseViewModel's init block subscribes to them unconditionally regardless of isLoginRequired.
    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        viewModel = ServerStatusViewModel()
    }

    private fun buildResponse(html: String) = Response.Builder()
        .request(Request.Builder().url("https://proxer.de").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(html.toResponseBody("text/html".toMediaType()))
        .build()

    @Test
    fun `load sets data on success`() {
        val call = mockk<Call>(relaxed = true)

        every { call.clone() } returns call
        every { call.execute() } returns buildResponse(onlineHtml)
        every { client.newCall(any()) } returns call

        viewModel.load()

        assertEquals(2, viewModel.data.value?.size)
        assertEquals("Server 1", viewModel.data.value?.get(0)?.name)
        assertTrue(viewModel.data.value?.get(0)?.online == true)
        assertEquals("Server 2", viewModel.data.value?.get(1)?.name)
        assertFalse(viewModel.data.value?.get(1)?.online == true)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val call = mockk<Call>(relaxed = true)

        every { call.clone() } returns call
        every { call.execute() } throws IOException("network down")
        every { client.newCall(any()) } returns call

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val call = mockk<Call>(relaxed = true)

        every { call.clone() } returns call
        every { call.execute() } returns buildResponse(onlineHtml)
        every { client.newCall(any()) } returns call

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val call = mockk<Call>(relaxed = true)

        every { call.clone() } returns call
        every { call.execute() } throws IOException("network down")
        every { client.newCall(any()) } returns call

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val call = mockk<Call>(relaxed = true)

        every { call.clone() } returns call
        every { client.newCall(any()) } returns call
        every { call.execute() } returns buildResponse(onlineHtml)

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        val secondHtml = """
            <html><body><table>
            <tr><td>Server 1:</td><td><b>Offline</b></td></tr>
            </table></body></html>
        """.trimIndent()

        every { call.execute() } returns buildResponse(secondHtml)
        viewModel.reload()

        assertEquals(1, viewModel.data.value?.size)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load succeeds without any login state stubbed since isLoginRequired is false`() {
        val call = mockk<Call>(relaxed = true)

        every { call.clone() } returns call
        every { call.execute() } returns buildResponse(onlineHtml)
        every { client.newCall(any()) } returns call

        viewModel.load()

        assertNotNull(viewModel.data.value)
        assertNull(viewModel.error.value)
    }
}
```

- [ ] **Step 3: Verify**

```bash
./gradlew test --tests "me.proxer.app.settings.status.ServerStatusViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/me/proxer/app/base/FakeAppModule.kt src/test/kotlin/me/proxer/app/settings/status/ServerStatusViewModelTest.kt
git commit -m "test: add ServerStatusViewModel unit tests"
```
### Task 22: Unit tests — BookmarkViewModel

**Goal:** Verify `BookmarkViewModel`'s pagination (via `PagedContentViewModel<Bookmark>`), its `refresh() = reload()` override (which disables the `refreshError` branch), the `searchQuery`/`category`/`filterAvailable` reload triggers, and the deletion/undo queue.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/bookmark/BookmarkViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets data on success and clears error
- [ ] `load()` sets error on failure
- [ ] `isLoading` is false after a successful load
- [ ] `isLoading` is false after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] A second `load()`/`loadIfPossible()` call appends a new page and dedups by `id`; `loadIfPossible()` no-ops once the last (short) page has been loaded
- [ ] `refresh()` behaves like `reload()` — it nulls `data` before loading, so a page-0 error with previously-loaded data still lands on `error`, never `refreshError`
- [ ] Changing `category` triggers a reload
- [ ] `addItemToDelete()` removes the item from `data` and populates `undoData` on success, or sets `itemDeletionError` on failure
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.bookmark.BookmarkViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/bookmark/BookmarkViewModelTest.kt`**

```kotlin
package me.proxer.app.bookmark

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.ucp.BookmarksEndpoint
import me.proxer.library.api.ucp.DeleteBookmarkEndpoint
import me.proxer.library.entity.ucp.Bookmark
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaLanguage
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class BookmarkViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: BookmarkViewModel

    private fun bookmark(id: String) = Bookmark(
        id,
        "entry-$id",
        Category.ANIME,
        "Bookmark $id",
        3,
        MediaLanguage.GERMAN_SUB,
        Medium.ANIMESERIES,
        MediaState.FINISHED,
        "Chapter $id",
        true,
    )

    private fun fullPage(prefix: String) = (0 until 30).map { bookmark("$prefix-$it") }

    private fun mockBookmarksEndpoint(): BookmarksEndpoint {
        val endpoint = mockk<BookmarksEndpoint>(relaxed = true)

        every { api.ucp.bookmarks() } returns endpoint
        every { endpoint.name(any()) } returns endpoint
        every { endpoint.category(any()) } returns endpoint
        every { endpoint.filterAvailable(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        viewModel = BookmarkViewModel(null, null, false)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockBookmarksEndpoint()
        val page = fullPage("p0")
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockBookmarksEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockBookmarksEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockBookmarksEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockBookmarksEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val secondPage = fullPage("p1")
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `second load appends new page, advances the page number and dedups by id`() {
        val endpoint = mockBookmarksEndpoint()
        val pageSlot = slot<Int>()

        val firstPage = fullPage("p0")
        endpoint.stubPagingSuccess(firstPage)
        every { endpoint.page(capture(pageSlot)) } returns endpoint

        viewModel.load()
        assertEquals(0, pageSlot.captured)
        assertEquals(30, viewModel.data.value?.size)

        val secondPage = listOf(bookmark("p1-0"), bookmark("p1-1"))
        endpoint.stubPagingSuccess(secondPage)
        every { endpoint.page(capture(pageSlot)) } returns endpoint

        viewModel.loadIfPossible()

        assertEquals(1, pageSlot.captured)
        assertEquals(firstPage + secondPage, viewModel.data.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockBookmarksEndpoint()
        val lastPage = listOf(bookmark("last-0"), bookmark("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(lastPage, viewModel.data.value)

        endpoint.stubPagingSuccess(listOf(bookmark("should-not-appear")))

        viewModel.loadIfPossible()

        assertEquals(lastPage, viewModel.data.value)
    }

    @Test
    fun `refresh behaves like reload and clears data first, so errors never become refreshError`() {
        val endpoint = mockBookmarksEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        endpoint.stubPagingError()

        viewModel.refresh()

        // BookmarkViewModel overrides refresh() as reload(), which nulls data before the
        // failing load runs, so PagedViewModel's "page 0 with existing data" refreshError
        // branch is never reachable through this VM's public API.
        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
        assertNull(viewModel.refreshError.value)
    }

    @Test
    fun `category change triggers reload`() {
        val endpoint = mockBookmarksEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val newPage = listOf(bookmark("manga-0"))
        endpoint.stubPagingSuccess(newPage)

        viewModel.category = Category.MANGA

        assertEquals(newPage, viewModel.data.value)
    }

    @Test
    fun `addItemToDelete removes item from data and sets undoData on success`() {
        val endpoint = mockBookmarksEndpoint()
        val items = listOf(bookmark("a"), bookmark("b"))
        endpoint.stubPagingSuccess(items)
        viewModel.load()

        val deleteEndpoint = mockk<DeleteBookmarkEndpoint>(relaxed = true)
        every { api.ucp.deleteBookmark("a") } returns deleteEndpoint
        deleteEndpoint.stubSuccess(Unit)

        viewModel.addItemToDelete(items[0])

        assertEquals(listOf(items[1]), viewModel.data.value)
        assertNotNull(viewModel.undoData.value)
    }

    @Test
    fun `addItemToDelete sets itemDeletionError on failure`() {
        val endpoint = mockBookmarksEndpoint()
        val items = listOf(bookmark("a"))
        endpoint.stubPagingSuccess(items)
        viewModel.load()

        val deleteEndpoint = mockk<DeleteBookmarkEndpoint>(relaxed = true)
        every { api.ucp.deleteBookmark("a") } returns deleteEndpoint
        deleteEndpoint.stubError()

        viewModel.addItemToDelete(items[0])

        assertNotNull(viewModel.itemDeletionError.value)
        assertEquals(items, viewModel.data.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.bookmark.BookmarkViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/bookmark/BookmarkViewModelTest.kt
git commit -m "test: add BookmarkViewModel unit tests"
```

---

### Task 23: Unit tests — MessengerViewModel

**Goal:** Verify `MessengerViewModel`'s Room-`LiveData`-driven data flow (its `data`/`conference` fields are `MediatorLiveData`, not populated via the usual `dataSingle` pagination pipeline), its `hasReachedEnd` override (`conference.value.isFullyLoaded`), draft handling, `sendMessage()`, and the `MessengerErrorEvent` bus handler. `MessengerWorker`'s companion functions touch a real `WorkManager` singleton, so they are mocked via `mockkObject`.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/chat/prv/message/MessengerViewModelTest.kt` (`FakeAppModule.kt`'s `MessengerDao` fake, added in Task 5, already covers this VM — no change needed there)

**Acceptance Criteria:**
- [ ] `conference` is seeded from the constructor's `initialConference`
- [ ] `data` populates from the DAO's messages `LiveData` when it emits a non-empty list
- [ ] An empty message emission enqueues a message load via `MessengerWorker` when the conference isn't fully loaded, and does not when it is
- [ ] `conference` updates when the DAO's conference `LiveData` emits, and a `null` emission sets `deleted`
- [ ] `load()` on page 0 marks the conference as read; beyond page 0 (and not fully loaded) it enqueues a message load
- [ ] `loadDraft()`/`updateDraft()` read/write drafts through `StorageHelper`
- [ ] `sendMessage()` inserts a pending message via the DAO and enqueues synchronization
- [ ] A `MessengerErrorEvent` carrying a `ChatMessageException` sets `error` and clears `isLoading`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.chat.prv.message.MessengerViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: `FakeAppModule.kt` already has everything this VM needs — no change**

`MessengerDao` was already added by Task 5 and `OkHttpClient` by Task 21; nothing new is required here. `FakeAppModule.kt` should read:

```kotlin
package me.proxer.app.base

import com.rubengees.rxbus.RxBus
import io.mockk.mockk
import me.proxer.app.chat.prv.sync.MessengerDao
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
}
```

- [ ] **Step 2: Create `src/test/kotlin/me/proxer/app/chat/prv/message/MessengerViewModelTest.kt`**

```kotlin
package me.proxer.app.chat.prv.message

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.rubengees.rxbus.RxBus
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.reactivex.Observable
import me.proxer.app.auth.LocalUser
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.chat.prv.LocalMessage
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.chat.prv.sync.MessengerErrorEvent
import me.proxer.app.chat.prv.sync.MessengerWorker
import me.proxer.app.exception.ChatMessageException
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.enums.Device
import me.proxer.library.enums.MessageAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import org.threeten.bp.Instant

class MessengerViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()
    private val messengerDao: MessengerDao by inject()
    private val bus: RxBus by inject()

    private lateinit var messagesLiveData: MediatorLiveData<List<LocalMessage>>
    private lateinit var conferenceLiveData: MutableLiveData<LocalConference?>
    private lateinit var initialConference: LocalConference
    private lateinit var viewModel: MessengerViewModel

    private fun conference(id: Long, isFullyLoaded: Boolean = false) = LocalConference(
        id,
        "Topic $id",
        "",
        2,
        "",
        "",
        false,
        true,
        true,
        Instant.now(),
        0,
        "0",
        isFullyLoaded,
    )

    private fun message(id: Long, conferenceId: Long) = LocalMessage(
        id,
        conferenceId,
        "u1",
        "User",
        "Hello $id",
        MessageAction.NONE,
        Instant.now(),
        Device.MOBILE,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        mockkObject(MessengerWorker.Companion)
        every { MessengerWorker.enqueueMessageLoad(any()) } just Runs
        every { MessengerWorker.enqueueSynchronization() } just Runs
        every { MessengerWorker.isRunning } returns false

        initialConference = conference(1L)
        messagesLiveData = MediatorLiveData()
        conferenceLiveData = MutableLiveData()

        every { messengerDao.getMessagesLiveDataForConference(1L) } returns messagesLiveData
        every { messengerDao.getConferenceLiveData(1L) } returns conferenceLiveData

        viewModel = MessengerViewModel(initialConference)

        // MessengerViewModel's data/conference are MediatorLiveData: they only forward
        // from their sources while "active" (i.e. observed). Attach permanent observers so
        // the addSource() wiring done in init actually delivers emissions during the test.
        viewModel.data.observeForever {}
        viewModel.conference.observeForever {}
    }

    @After
    fun teardown() {
        unmockkObject(MessengerWorker.Companion)
    }

    @Test
    fun `initial conference value is seeded from constructor`() {
        assertEquals(initialConference, viewModel.conference.value)
    }

    @Test
    fun `data populates when dao emits non-empty messages`() {
        val messages = listOf(message(1L, 1L), message(2L, 1L))

        messagesLiveData.value = messages

        assertEquals(messages, viewModel.data.value)
        assertEquals(false, viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `empty message emission triggers a message load when not fully loaded`() {
        messagesLiveData.value = emptyList()

        verify { MessengerWorker.enqueueMessageLoad(1L) }
    }

    @Test
    fun `empty message emission does not enqueue a load when the conference is fully loaded`() {
        conferenceLiveData.value = conference(1L, isFullyLoaded = true)

        messagesLiveData.value = emptyList()

        verify(exactly = 0) { MessengerWorker.enqueueMessageLoad(any()) }
    }

    @Test
    fun `conference update from dao is reflected`() {
        val updated = conference(1L, isFullyLoaded = true)

        conferenceLiveData.value = updated

        assertEquals(updated, viewModel.conference.value)
    }

    @Test
    fun `null conference from dao marks conference as deleted`() {
        conferenceLiveData.value = null

        assertNotNull(viewModel.deleted.value)
    }

    @Test
    fun `load on page 0 marks the conference as read`() {
        viewModel.load()

        verify { messengerDao.markConferenceAsRead(1L) }
    }

    @Test
    fun `load enqueues a message load once past the first page and not fully loaded`() {
        messagesLiveData.value = List(MessengerWorker.MESSAGES_ON_PAGE) { message(it.toLong() + 1, 1L) }

        viewModel.load()

        verify { MessengerWorker.enqueueMessageLoad(1L) }
    }

    @Test
    fun `loadDraft populates draft from storage`() {
        every { storageHelper.getMessageDraft("1") } returns "draft text"

        viewModel.loadDraft()

        assertEquals("draft text", viewModel.draft.value)
    }

    @Test
    fun `updateDraft stores non-blank text and deletes blank text`() {
        viewModel.updateDraft("hello")
        verify { storageHelper.putMessageDraft("1", "hello") }

        viewModel.updateDraft("   ")
        verify { storageHelper.deleteMessageDraft("1") }
    }

    @Test
    fun `sendMessage inserts a pending message and enqueues synchronization`() {
        val user = LocalUser("token", "u1", "User", "image.png")

        every { storageHelper.user } returns user
        every { messengerDao.insertMessageToSend(user, "hi", 1L) } returns message(-1L, 1L)

        viewModel.sendMessage("hi")

        verify { messengerDao.insertMessageToSend(user, "hi", 1L) }
        verify { MessengerWorker.enqueueSynchronization() }
    }

    @Test
    fun `chat message error event sets error and clears loading`() {
        bus.post(MessengerErrorEvent(ChatMessageException(RuntimeException("boom"))))

        assertNotNull(viewModel.error.value)
        assertEquals(false, viewModel.isLoading.value)
    }
}
```

- [ ] **Step 3: Verify**

```bash
./gradlew test --tests "me.proxer.app.chat.prv.message.MessengerViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/me/proxer/app/base/FakeAppModule.kt src/test/kotlin/me/proxer/app/chat/prv/message/MessengerViewModelTest.kt
git commit -m "test: add MessengerViewModel unit tests"
```

---

### Task 24: Unit tests — ChatViewModel

**Goal:** Verify `ChatViewModel`'s fully custom `load()` override (it does not use `PagedViewModel.load()`), its `messageId`-based merge logic, its deviation from the base `refreshError` rule (it checks `data.value` only, not the current page), draft handling and `sendMessage()`'s optimistic-insert/rollback behavior.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/chat/pub/message/ChatViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets data on success (mapped to `ParsedChatMessage`) and clears error
- [ ] `load()` sets error on failure when there is no existing data
- [ ] `isLoading` is false after a successful load
- [ ] `isLoading` is false after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `hasReachedEnd` becomes true once fewer than `itemsOnPage` messages are returned; `loadIfPossible()` then no-ops
- [ ] An error with existing data always sets `refreshError` (not `error`) — `ChatViewModel.load()` only checks `data.value`, unlike the base `PagedViewModel` which also requires page 0
- [ ] `sendMessage()` optimistically prepends a pending message and, on the network failure path, drops unsent messages and sets `sendMessageError`
- [ ] `loadDraft()`/`updateDraft()` read/write drafts through `StorageHelper`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.chat.pub.message.ChatViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/chat/pub/message/ChatViewModelTest.kt`**

```kotlin
package me.proxer.app.chat.pub.message

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.chat.ChatMessagesEndpoint
import me.proxer.library.api.chat.SendChatMessageEndpoint
import me.proxer.library.entity.chat.ChatMessage
import me.proxer.library.enums.ChatMessageAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.util.Date

class ChatViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: ChatViewModel

    private fun chatMessage(id: String) = ChatMessage(
        id,
        "user-$id",
        "User $id",
        "image.png",
        "Message $id",
        ChatMessageAction.NONE,
        Date(),
    )

    private fun fullPage(prefix: String) = (0 until 50).map { chatMessage("$prefix-$it") }

    private fun mockMessagesEndpoint(): ChatMessagesEndpoint {
        val endpoint = mockk<ChatMessagesEndpoint>(relaxed = true)

        every { api.chat.messages("room-1") } returns endpoint
        every { endpoint.messageId(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        viewModel = ChatViewModel("room-1")
    }

    @After
    fun teardown() {
        viewModel.onCleared()
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockMessagesEndpoint()
        endpoint.stubSuccess(fullPage("p0"))

        viewModel.load()

        assertEquals(50, viewModel.data.value?.size)
        assertEquals("p0-0", viewModel.data.value?.first()?.id)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure when there is no existing data`() {
        val endpoint = mockMessagesEndpoint()
        endpoint.stubError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockMessagesEndpoint()
        endpoint.stubSuccess(fullPage("p0"))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockMessagesEndpoint()
        endpoint.stubError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockMessagesEndpoint()
        endpoint.stubSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(50, viewModel.data.value?.size)

        val secondPage = fullPage("p1")
        endpoint.stubSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage.map { it.id }.toSet(), viewModel.data.value?.map { it.id }?.toSet())
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockMessagesEndpoint()
        val shortPage = listOf(chatMessage("last-0"), chatMessage("last-1"))
        endpoint.stubSuccess(shortPage)

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        endpoint.stubSuccess(listOf(chatMessage("should-not-appear")))

        viewModel.loadIfPossible()

        assertEquals(2, viewModel.data.value?.size)
    }

    @Test
    fun `an error with existing data always sets refreshError, regardless of page`() {
        val endpoint = mockMessagesEndpoint()
        endpoint.stubSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(50, viewModel.data.value?.size)

        endpoint.stubError()

        viewModel.loadIfPossible()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `sendMessage optimistically prepends the message and clears it on failure`() {
        every { storageHelper.user } returns me.proxer.app.auth.LocalUser("token", "self", "Self", "image.png")

        val sendEndpoint = mockk<SendChatMessageEndpoint>(relaxed = true)
        every { api.chat.sendMessage("room-1", "hi") } returns sendEndpoint
        sendEndpoint.stubError()

        viewModel.sendMessage("hi")

        assertNotNull(viewModel.sendMessageError.value)
        assertEquals(true, viewModel.data.value?.isEmpty() != false)
    }

    @Test
    fun `loadDraft and updateDraft go through StorageHelper`() {
        every { storageHelper.getMessageDraft("room-1") } returns "draft text"

        viewModel.loadDraft()
        assertEquals("draft text", viewModel.draft.value)

        viewModel.updateDraft("new text")
        verify { storageHelper.putMessageDraft("room-1", "new text") }

        viewModel.updateDraft("")
        verify { storageHelper.deleteMessageDraft("room-1") }
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.chat.pub.message.ChatViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/chat/pub/message/ChatViewModelTest.kt
git commit -m "test: add ChatViewModel unit tests"
```

---

### Task 25: Unit tests — TopicViewModel

**Goal:** Verify `TopicViewModel`'s pagination (a plain `PagedViewModel<ParsedPost>`, not `PagedContentViewModel`, with an endpoint whose success type is a single `Topic`, not a `List`), its `metaData` side-channel, and the canonical `refresh()`/`refreshError` behavior (this VM does not override either, so the base `PagedViewModel` rules apply exactly). Its `dataSingle` hops onto `Schedulers.computation()` for post-parsing, so the computation scheduler is also forced onto the trampoline for the duration of the test.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/forum/TopicViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets data (posts mapped to `ParsedPost`) and populates `metaData`
- [ ] `load()` sets error on failure
- [ ] `isLoading` is false after a successful load
- [ ] `isLoading` is false after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `hasReachedEnd` becomes true once fewer than `itemsOnPage` posts are returned; `loadIfPossible()` then no-ops
- [ ] A second `load()` (page > 0) appends new posts, dedup'd by `id`
- [ ] `refresh()` merges page-0 results with existing data (new items first) without nulling `data` first
- [ ] An error during `refresh()` with existing data sets `refreshError`, not `error`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.forum.TopicViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/forum/TopicViewModelTest.kt`**

```kotlin
package me.proxer.app.forum

import android.content.res.Resources
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.forum.TopicEndpoint
import me.proxer.library.entity.forum.Post
import me.proxer.library.entity.forum.Topic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.util.Date

class TopicViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: TopicViewModel

    private fun post(id: String) = Post(
        id,
        "0",
        "user-$id",
        "User $id",
        "image.png",
        Date(),
        null,
        null,
        null,
        null,
        null,
        "Message $id",
        0,
    )

    private fun topic(posts: List<Post>) = Topic(
        "cat-1",
        "Category",
        "Subject",
        false,
        posts.size,
        10,
        Date(),
        Date(),
        posts,
    )

    private fun fullPage(prefix: String) = (0 until 10).map { post("$prefix-$it") }

    private fun mockTopicEndpoint(): TopicEndpoint {
        val endpoint = mockk<TopicEndpoint>(relaxed = true)

        every { api.forum.topic("topic-1") } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        // TopicViewModel's dataSingle hops onto Schedulers.computation() while mapping posts;
        // force that scheduler onto the trampoline too so load() completes synchronously.
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }

        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        viewModel = TopicViewModel("topic-1", mockk<Resources>(relaxed = true))
    }

    @After
    fun teardown() {
        RxJavaPlugins.setComputationSchedulerHandler(null)
    }

    @Test
    fun `load sets data and populates metaData`() {
        val endpoint = mockTopicEndpoint()
        val posts = fullPage("p0")
        endpoint.stubPagingSuccess(topic(posts))

        viewModel.load()

        assertEquals(posts.map { it.id }, viewModel.data.value?.map { it.id })
        assertEquals("Subject", viewModel.metaData.value?.subject)
        assertEquals("cat-1", viewModel.metaData.value?.categoryId)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockTopicEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockTopicEndpoint()
        endpoint.stubPagingSuccess(topic(fullPage("p0")))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockTopicEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockTopicEndpoint()
        endpoint.stubPagingSuccess(topic(fullPage("p0")))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val secondPosts = fullPage("p1")
        endpoint.stubPagingSuccess(topic(secondPosts))
        viewModel.reload()

        assertEquals(secondPosts.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `second load appends new posts and dedups by id`() {
        val endpoint = mockTopicEndpoint()
        val firstPage = fullPage("p0")
        endpoint.stubPagingSuccess(topic(firstPage))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val secondPage = listOf(post("p1-0"), post("p1-1"))
        endpoint.stubPagingSuccess(topic(secondPage))
        viewModel.loadIfPossible()

        val expectedIds = firstPage.map { it.id } + secondPage.map { it.id }
        assertEquals(expectedIds, viewModel.data.value?.map { it.id })
    }

    @Test
    fun `refresh merges page-0 results with existing data, new items first`() {
        val endpoint = mockTopicEndpoint()
        val original = fullPage("p0")
        endpoint.stubPagingSuccess(topic(original))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val refreshed = listOf(post("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(topic(refreshed))
        viewModel.refresh()

        val expectedIds = refreshed.map { it.id } + listOf(original.first().id)
        assertEquals(expectedIds, viewModel.data.value?.map { it.id })
    }

    @Test
    fun `error during refresh with existing data sets refreshError, not error`() {
        val endpoint = mockTopicEndpoint()
        endpoint.stubPagingSuccess(topic(fullPage("p0")))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
        assertEquals(10, viewModel.data.value?.size)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.forum.TopicViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/forum/TopicViewModelTest.kt
git commit -m "test: add TopicViewModel unit tests"
```

---

### Task 26: Unit tests — IndustryProjectViewModel

**Goal:** Verify `IndustryProjectViewModel`'s pagination (a plain `PagedContentViewModel<IndustryProject>` with no overrides) and that `includeHentai` is wired from `preferenceHelper.isAgeRestrictedMediaAllowed && storageHelper.isLoggedIn`.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/info/industry/IndustryProjectViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets data on success and clears error
- [ ] `load()` sets error on failure
- [ ] `isLoading` is false after a successful load
- [ ] `isLoading` is false after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `hasReachedEnd` becomes true once fewer than `itemsOnPage` items are returned; `loadIfPossible()` then no-ops
- [ ] `refresh()` merges page-0 results with existing data (new items first)
- [ ] An error during `refresh()` with existing data sets `refreshError`, not `error`
- [ ] `includeHentai` is `true` only when both age-restricted media is allowed and the user is logged in
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.info.industry.IndustryProjectViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/info/industry/IndustryProjectViewModelTest.kt`**

```kotlin
package me.proxer.app.info.industry

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.list.IndustryProjectListEndpoint
import me.proxer.library.entity.list.IndustryProject
import me.proxer.library.enums.FskConstraint
import me.proxer.library.enums.IndustryType
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class IndustryProjectViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: IndustryProjectViewModel

    private fun project(id: String) = IndustryProject(
        id,
        "Project $id",
        setOf("Action"),
        setOf(FskConstraint.FSK_0),
        Medium.ANIMESERIES,
        IndustryType.STUDIO,
        MediaState.FINISHED,
        10,
        2,
    )

    private fun fullPage(prefix: String) = (0 until 30).map { project("$prefix-$it") }

    private fun mockIndustryEndpoint(): IndustryProjectListEndpoint {
        val endpoint = mockk<IndustryProjectListEndpoint>(relaxed = true)

        every { api.list.industryProjectList("industry-1") } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        viewModel = IndustryProjectViewModel("industry-1")
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockIndustryEndpoint()
        val page = fullPage("p0")
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockIndustryEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockIndustryEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockIndustryEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockIndustryEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val secondPage = fullPage("p1")
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockIndustryEndpoint()
        val lastPage = listOf(project("last-0"), project("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(lastPage, viewModel.data.value)

        endpoint.stubPagingSuccess(listOf(project("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(lastPage, viewModel.data.value)
    }

    @Test
    fun `refresh merges page-0 results with existing data, new items first`() {
        val endpoint = mockIndustryEndpoint()
        val original = fullPage("p0")
        endpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val refreshed = listOf(project("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()

        val expected = refreshed + listOf(original.first())
        assertEquals(expected, viewModel.data.value)
    }

    @Test
    fun `error during refresh with existing data sets refreshError, not error`() {
        val endpoint = mockIndustryEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
        assertEquals(30, viewModel.data.value?.size)
    }

    @Test
    fun `includeHentai reflects age-restricted media allowance and login state`() {
        val endpoint = mockIndustryEndpoint()
        endpoint.stubPagingSuccess(emptyList())

        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns true
        every { storageHelper.isLoggedIn } returns true
        viewModel.load()
        verify { endpoint.includeHentai(true) }

        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
        viewModel.load()
        verify { endpoint.includeHentai(false) }
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.info.industry.IndustryProjectViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/info/industry/IndustryProjectViewModelTest.kt
git commit -m "test: add IndustryProjectViewModel unit tests"
```

---

### Task 27: Unit tests — TranslatorGroupProjectViewModel

**Goal:** Verify `TranslatorGroupProjectViewModel`'s pagination (a plain `PagedContentViewModel<TranslatorGroupProject>`, structurally identical to `IndustryProjectViewModel`) and its `includeHentai` wiring.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupProjectViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets data on success and clears error
- [ ] `load()` sets error on failure
- [ ] `isLoading` is false after a successful load
- [ ] `isLoading` is false after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `hasReachedEnd` becomes true once fewer than `itemsOnPage` items are returned; `loadIfPossible()` then no-ops
- [ ] `refresh()` merges page-0 results with existing data (new items first)
- [ ] An error during `refresh()` with existing data sets `refreshError`, not `error`
- [ ] `includeHentai` is `true` only when both age-restricted media is allowed and the user is logged in
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.info.translatorgroup.TranslatorGroupProjectViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupProjectViewModelTest.kt`**

```kotlin
package me.proxer.app.info.translatorgroup

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.list.TranslatorGroupProjectListEndpoint
import me.proxer.library.entity.list.TranslatorGroupProject
import me.proxer.library.enums.FskConstraint
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import me.proxer.library.enums.ProjectState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class TranslatorGroupProjectViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: TranslatorGroupProjectViewModel

    private fun project(id: String) = TranslatorGroupProject(
        id,
        "Project $id",
        setOf("Action"),
        setOf(FskConstraint.FSK_0),
        Medium.ANIMESERIES,
        ProjectState.ONGOING,
        MediaState.FINISHED,
        10,
        2,
    )

    private fun fullPage(prefix: String) = (0 until 30).map { project("$prefix-$it") }

    private fun mockTranslatorGroupEndpoint(): TranslatorGroupProjectListEndpoint {
        val endpoint = mockk<TranslatorGroupProjectListEndpoint>(relaxed = true)

        every { api.list.translatorGroupProjectList("group-1") } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        viewModel = TranslatorGroupProjectViewModel("group-1")
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockTranslatorGroupEndpoint()
        val page = fullPage("p0")
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockTranslatorGroupEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockTranslatorGroupEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockTranslatorGroupEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockTranslatorGroupEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val secondPage = fullPage("p1")
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockTranslatorGroupEndpoint()
        val lastPage = listOf(project("last-0"), project("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(lastPage, viewModel.data.value)

        endpoint.stubPagingSuccess(listOf(project("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(lastPage, viewModel.data.value)
    }

    @Test
    fun `refresh merges page-0 results with existing data, new items first`() {
        val endpoint = mockTranslatorGroupEndpoint()
        val original = fullPage("p0")
        endpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val refreshed = listOf(project("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()

        val expected = refreshed + listOf(original.first())
        assertEquals(expected, viewModel.data.value)
    }

    @Test
    fun `error during refresh with existing data sets refreshError, not error`() {
        val endpoint = mockTranslatorGroupEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
        assertEquals(30, viewModel.data.value?.size)
    }

    @Test
    fun `includeHentai reflects age-restricted media allowance and login state`() {
        val endpoint = mockTranslatorGroupEndpoint()
        endpoint.stubPagingSuccess(emptyList())

        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns true
        every { storageHelper.isLoggedIn } returns true
        viewModel.load()
        verify { endpoint.includeHentai(true) }

        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
        viewModel.load()
        verify { endpoint.includeHentai(false) }
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.info.translatorgroup.TranslatorGroupProjectViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupProjectViewModelTest.kt
git commit -m "test: add TranslatorGroupProjectViewModel unit tests"
```

---

### Task 28: Unit tests — CommentsViewModel

**Goal:** Verify `CommentsViewModel`'s pagination (a plain `PagedViewModel<ParsedComment>` with a custom `dataSingle`, not `PagedContentViewModel`), the `sortCriteria` reload trigger, and `deleteComment()`/`updateComment()`. Its `dataSingle` hops onto `Schedulers.computation()` for comment-parsing, so the computation scheduler is forced onto the trampoline too.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/media/comments/CommentsViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets data on success and clears error
- [ ] `load()` sets error on failure
- [ ] `isLoading` is false after a successful load
- [ ] `isLoading` is false after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `hasReachedEnd` becomes true once fewer than `itemsOnPage` comments are returned; `loadIfPossible()` then no-ops
- [ ] `refresh()` merges page-0 results with existing data (new items first); an error during that refresh with existing data sets `refreshError`, not `error`
- [ ] Changing `sortCriteria` triggers a reload
- [ ] `deleteComment()` removes the item from `data` on success and deletes the stored draft
- [ ] `updateComment()` updates the matching item's rating/content/instant in place
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.media.comments.CommentsViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/media/comments/CommentsViewModelTest.kt`**

```kotlin
package me.proxer.app.media.comments

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.base.stubSuccess
import me.proxer.app.comment.LocalComment
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.comment.UpdateCommentEndpoint
import me.proxer.library.api.info.CommentsEndpoint
import me.proxer.library.entity.info.Comment
import me.proxer.library.entity.info.RatingDetails
import me.proxer.library.enums.CommentSortCriteria
import me.proxer.library.enums.UserMediaProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import org.threeten.bp.Instant
import java.util.Date

class CommentsViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: CommentsViewModel

    private fun comment(id: String) = Comment(
        id,
        "entry-1",
        "author-$id",
        UserMediaProgress.WATCHED,
        RatingDetails(),
        "Comment $id",
        5,
        1,
        0,
        Date(),
        "Author $id",
        "image.png",
    )

    private fun fullPage(prefix: String) = (0 until 10).map { comment("$prefix-$it") }

    private fun mockCommentsEndpoint(): CommentsEndpoint {
        val endpoint = mockk<CommentsEndpoint>(relaxed = true)

        every { api.info.comments("entry-1") } returns endpoint
        every { endpoint.sort(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        // CommentsViewModel's dataSingle hops onto Schedulers.computation() while mapping
        // comments; force that scheduler onto the trampoline too so load() is synchronous.
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }

        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        viewModel = CommentsViewModel("entry-1", CommentSortCriteria.TIME)
    }

    @After
    fun teardown() {
        RxJavaPlugins.setComputationSchedulerHandler(null)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockCommentsEndpoint()
        val page = fullPage("p0")
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockCommentsEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockCommentsEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockCommentsEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockCommentsEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val secondPage = fullPage("p1")
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockCommentsEndpoint()
        val lastPage = listOf(comment("last-0"), comment("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        endpoint.stubPagingSuccess(listOf(comment("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(2, viewModel.data.value?.size)
    }

    @Test
    fun `refresh merges page-0 results and a refresh error sets refreshError`() {
        val endpoint = mockCommentsEndpoint()
        val original = fullPage("p0")
        endpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val refreshed = listOf(comment("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()

        val expectedIds = refreshed.map { it.id } + listOf(original.first().id)
        assertEquals(expectedIds, viewModel.data.value?.map { it.id })

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `sortCriteria change triggers reload`() {
        val endpoint = mockCommentsEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val ratingSorted = listOf(comment("rating-0"))
        endpoint.stubPagingSuccess(ratingSorted)

        viewModel.sortCriteria = CommentSortCriteria.RATING

        assertEquals(ratingSorted.map { it.id }, viewModel.data.value?.map { it.id })
    }

    @Test
    fun `deleteComment removes the item and deletes the draft on success`() {
        val endpoint = mockCommentsEndpoint()
        val comments = fullPage("p0")
        endpoint.stubPagingSuccess(comments)
        viewModel.load()

        val target = viewModel.data.value!!.first()
        val updateEndpoint = mockk<UpdateCommentEndpoint>(relaxed = true)
        every { api.comment.update(target.id) } returns updateEndpoint
        every { updateEndpoint.comment(any()) } returns updateEndpoint
        every { updateEndpoint.rating(any()) } returns updateEndpoint
        updateEndpoint.stubSuccess(Unit)

        viewModel.deleteComment(target)

        assertEquals(9, viewModel.data.value?.size)
        assertFalse(viewModel.data.value!!.any { it.id == target.id })
    }

    @Test
    fun `updateComment updates the matching entry in place`() {
        val endpoint = mockCommentsEndpoint()
        val comments = fullPage("p0")
        endpoint.stubPagingSuccess(comments)
        viewModel.load()

        val target = viewModel.data.value!!.first()
        val update = LocalComment(target.id, target.entryId, UserMediaProgress.WILL_WATCH, RatingDetails(9, 8, 7, 6, 5), "Updated", 9, target.episode)

        viewModel.updateComment(update)

        val updated = viewModel.data.value!!.first { it.id == target.id }
        assertEquals(9, updated.overallRating)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.media.comments.CommentsViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/media/comments/CommentsViewModelTest.kt
git commit -m "test: add CommentsViewModel unit tests"
```

---

### Task 29: Unit tests — MediaListViewModel

**Goal:** Verify `MediaListViewModel`'s pagination (a `PagedContentViewModel<MediaListEntry>` with an unusually large search/filter constructor surface), that `sortCriteria`/`type` changes trigger reload (and that crossing the hentai/non-hentai boundary also triggers `loadTags()`), that `loadTags()` uses the cache when fresh and hits the network when stale, and that age confirmation is always validated (`isAgeConfirmationRequired` mirrors `isLoginRequired`, which is unconditionally `true` in `BaseViewModel`, so `MediaListViewModel`'s override is effectively always-on regardless of `type`).

**Files:**
- Edit: `src/test/kotlin/me/proxer/app/base/FakeAppModule.kt` (add a `TagDao` fake, additive to the `MessengerDao` entry from Task 23)
- Create: `src/test/kotlin/me/proxer/app/media/list/MediaListViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets data on success and clears error
- [ ] `load()` sets error on failure
- [ ] `isLoading` is false after a successful load
- [ ] `isLoading` is false after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `hasReachedEnd` becomes true once fewer than `itemsOnPage` items are returned; `loadIfPossible()` then no-ops
- [ ] `refresh()` merges page-0 results with existing data (new items first); an error during that refresh with existing data sets `refreshError`
- [ ] Changing `sortCriteria` triggers a reload; changing `type` across the hentai boundary triggers both reload and `loadTags()`, while a same-boundary `type` change only reloads
- [ ] `loadTags()` uses the DAO cache directly when it is fresh, split into `genreData`/`tagData` by `TagType`
- [ ] `loadTags()` fetches and persists remote tags (via a zipped normal + H-tag call) when the cache is stale
- [ ] `validate()` always calls `validators.validateAgeConfirmation()`, regardless of `type`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.media.list.MediaListViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Extend `src/test/kotlin/me/proxer/app/base/FakeAppModule.kt` with a `TagDao` fake**

`MessengerDao` (Task 5) and `OkHttpClient` (Task 21) are already present — keep both, adding only `TagDao`:

```kotlin
package me.proxer.app.base

import com.rubengees.rxbus.RxBus
import io.mockk.mockk
import me.proxer.app.chat.prv.sync.MessengerDao
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
}
```

- [ ] **Step 2: Create `src/test/kotlin/me/proxer/app/media/list/MediaListViewModelTest.kt`**

```kotlin
package me.proxer.app.media.list

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.base.stubSuccess
import me.proxer.app.exception.AgeConfirmationRequiredException
import me.proxer.app.media.LocalTag
import me.proxer.app.media.TagDao
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.Validators
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.list.MediaSearchEndpoint
import me.proxer.library.api.list.TagListEndpoint
import me.proxer.library.entity.list.MediaListEntry
import me.proxer.library.entity.list.Tag
import me.proxer.library.enums.MediaSearchSortCriteria
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.MediaType
import me.proxer.library.enums.Medium
import me.proxer.library.enums.TagSubType
import me.proxer.library.enums.TagType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import org.threeten.bp.Instant
import java.util.EnumSet

class MediaListViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()
    private val validators: Validators by inject()
    private val tagDao: TagDao by inject()

    private lateinit var viewModel: MediaListViewModel

    private fun entry(id: String) = MediaListEntry(
        id,
        "Entry $id",
        setOf("Action"),
        Medium.ANIMESERIES,
        12,
        MediaState.FINISHED,
        10,
        2,
        emptySet(),
    )

    private fun fullPage(prefix: String) = (0 until 30).map { entry("$prefix-$it") }

    private fun tag(id: String, type: TagType) = Tag(id, type, "Tag $id", "desc", TagSubType.OTHER, false)

    private fun localTag(id: String, type: TagType) = LocalTag(id, type, "Tag $id", "desc", TagSubType.OTHER, false)

    private fun mockMediaSearchEndpoint(): MediaSearchEndpoint {
        val endpoint = mockk<MediaSearchEndpoint>(relaxed = true)

        every { api.list.mediaSearch() } returns endpoint
        every { endpoint.sort(any()) } returns endpoint
        every { endpoint.name(any()) } returns endpoint
        every { endpoint.language(any()) } returns endpoint
        every { endpoint.type(any()) } returns endpoint
        every { endpoint.tagRateFilter(any()) } returns endpoint
        every { endpoint.tagSpoilerFilter(any()) } returns endpoint
        every { endpoint.fskConstraints(any()) } returns endpoint
        every { endpoint.hideFinished(any()) } returns endpoint
        every { endpoint.tags(any()) } returns endpoint
        every { endpoint.excludedTags(any()) } returns endpoint
        every { endpoint.genres(any()) } returns endpoint
        every { endpoint.excludedGenres(any()) } returns endpoint

        return endpoint
    }

    private fun newViewModel(type: MediaType = MediaType.ANIMESERIES) = MediaListViewModel(
        MediaSearchSortCriteria.RELEVANCE,
        type,
        null,
        null,
        emptyList(),
        emptyList(),
        EnumSet.noneOf(me.proxer.library.enums.FskConstraint::class.java),
        emptyList(),
        emptyList(),
        null,
        null,
        null,
    )

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        viewModel = newViewModel()
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockMediaSearchEndpoint()
        val page = fullPage("p0")
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockMediaSearchEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockMediaSearchEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockMediaSearchEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockMediaSearchEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val secondPage = fullPage("p1")
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockMediaSearchEndpoint()
        val lastPage = listOf(entry("last-0"), entry("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(lastPage, viewModel.data.value)

        endpoint.stubPagingSuccess(listOf(entry("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(lastPage, viewModel.data.value)
    }

    @Test
    fun `refresh merges page-0 results and a refresh error sets refreshError`() {
        val endpoint = mockMediaSearchEndpoint()
        val original = fullPage("p0")
        endpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val refreshed = listOf(entry("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()
        assertEquals(refreshed + listOf(original.first()), viewModel.data.value)

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `sortCriteria change triggers reload`() {
        val endpoint = mockMediaSearchEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()

        val resorted = listOf(entry("sorted-0"))
        endpoint.stubPagingSuccess(resorted)

        viewModel.sortCriteria = MediaSearchSortCriteria.NAME

        assertEquals(resorted, viewModel.data.value)
    }

    @Test
    fun `type change within the same hentai boundary reloads but does not reload tags`() {
        val endpoint = mockMediaSearchEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()

        val movieResult = listOf(entry("movie-0"))
        endpoint.stubPagingSuccess(movieResult)

        viewModel.type = MediaType.MOVIE

        assertEquals(movieResult, viewModel.data.value)
        verify(exactly = 0) { api.list.tagList() }
    }

    @Test
    fun `type change across the hentai boundary reloads and reloads tags`() {
        val endpoint = mockMediaSearchEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()

        val hentaiResult = listOf(entry("hentai-0"))
        endpoint.stubPagingSuccess(hentaiResult)

        every { tagDao.getTags() } returns emptyList()

        val tagListEndpoint = mockk<TagListEndpoint>(relaxed = true)
        every { api.list.tagList() } returns tagListEndpoint
        val hTagListEndpoint = mockk<TagListEndpoint>(relaxed = true)
        every { tagListEndpoint.type(TagType.H_TAG) } returns hTagListEndpoint
        tagListEndpoint.stubSuccess(emptyList())
        hTagListEndpoint.stubSuccess(emptyList())

        viewModel.type = MediaType.HENTAI

        assertEquals(hentaiResult, viewModel.data.value)
        verify { api.list.tagList() }
    }

    @Test
    fun `loadTags uses the cache directly when it is fresh`() {
        every { preferenceHelper.lastTagUpdateDate } returns Instant.now()
        every { tagDao.getTags() } returns listOf(
            localTag("g1", TagType.GENRE),
            localTag("t1", TagType.TAG),
        )

        viewModel.loadTags()

        assertEquals(listOf("g1"), viewModel.genreData.value?.map { it.id })
        assertEquals(listOf("t1"), viewModel.tagData.value?.map { it.id })
        verify(exactly = 0) { api.list.tagList() }
    }

    @Test
    fun `loadTags fetches and persists remote tags when the cache is stale`() {
        every { preferenceHelper.lastTagUpdateDate } returns Instant.now().minusSeconds(60 * 60 * 24 * 30)
        every { tagDao.getTags() } returns emptyList()

        val tagListEndpoint = mockk<TagListEndpoint>(relaxed = true)
        every { api.list.tagList() } returns tagListEndpoint
        val hTagListEndpoint = mockk<TagListEndpoint>(relaxed = true)
        every { tagListEndpoint.type(TagType.H_TAG) } returns hTagListEndpoint

        tagListEndpoint.stubSuccess(listOf(tag("g1", TagType.GENRE)))
        hTagListEndpoint.stubSuccess(listOf(tag("h1", TagType.H_TAG)))

        viewModel.loadTags()

        verify { tagDao.replaceTags(any()) }
        verify { preferenceHelper.lastTagUpdateDate = any() }
        assertEquals(listOf("g1"), viewModel.genreData.value?.map { it.id })
    }

    @Test
    fun `validate always requires age confirmation, regardless of type`() {
        val endpoint = mockMediaSearchEndpoint()
        every { validators.validateAgeConfirmation() } throws AgeConfirmationRequiredException()

        viewModel.load()

        assertEquals(ButtonAction.AGE_CONFIRMATION, viewModel.error.value?.buttonAction)
    }
}
```

- [ ] **Step 3: Verify**

```bash
./gradlew test --tests "me.proxer.app.media.list.MediaListViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/me/proxer/app/base/FakeAppModule.kt src/test/kotlin/me/proxer/app/media/list/MediaListViewModelTest.kt
git commit -m "test: add MediaListViewModel unit tests"
```

---

### Task 30: Unit tests — NewsViewModel

**Goal:** Verify `NewsViewModel`'s pagination (`PagedContentViewModel<NewsArticle>`, no constructor args), that `markAsRead` is only `true` on page 0, and that `preferenceHelper.lastNewsDate` is updated from the first article only on a page-0 success.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/news/NewsViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets data on success and clears error
- [ ] `load()` sets error on failure
- [ ] `isLoading` is false after a successful load
- [ ] `isLoading` is false after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `hasReachedEnd` becomes true once fewer than `itemsOnPage` articles are returned; `loadIfPossible()` then no-ops
- [ ] `refresh()` merges page-0 results with existing data; an error during that refresh with existing data sets `refreshError`
- [ ] `markAsRead(true)` is sent on page 0 and `markAsRead(false)` beyond page 0
- [ ] `preferenceHelper.lastNewsDate` is set from the first article's date, only on a page-0 success
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.news.NewsViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/news/NewsViewModelTest.kt`**

```kotlin
package me.proxer.app.news

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.notifications.NewsEndpoint
import me.proxer.library.entity.notifications.NewsArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.util.Date

class NewsViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: NewsViewModel

    private fun article(id: String, date: Date = Date()) = NewsArticle(
        id,
        date,
        "Description $id",
        "image.png",
        "Subject $id",
        10,
        "thread-$id",
        "author-$id",
        "Author $id",
        0,
        "cat-1",
        "Category",
    )

    private fun fullPage(prefix: String) = (0 until 15).map { article("$prefix-$it") }

    private fun mockNewsEndpoint(): NewsEndpoint {
        val endpoint = mockk<NewsEndpoint>(relaxed = true)

        every { api.notifications.news() } returns endpoint
        every { endpoint.markAsRead(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        viewModel = NewsViewModel()
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockNewsEndpoint()
        val page = fullPage("p0")
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockNewsEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockNewsEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockNewsEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockNewsEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(15, viewModel.data.value?.size)

        val secondPage = fullPage("p1")
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage, viewModel.data.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockNewsEndpoint()
        val lastPage = listOf(article("last-0"), article("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(lastPage, viewModel.data.value)

        endpoint.stubPagingSuccess(listOf(article("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(lastPage, viewModel.data.value)
    }

    @Test
    fun `refresh merges page-0 results and a refresh error sets refreshError`() {
        val endpoint = mockNewsEndpoint()
        val original = fullPage("p0")
        endpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(15, viewModel.data.value?.size)

        val refreshed = listOf(article("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()
        assertEquals(refreshed + listOf(original.first()), viewModel.data.value)

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `markAsRead is true on page 0 and false beyond it`() {
        val endpoint = mockNewsEndpoint()
        val firstPage = fullPage("p0")
        endpoint.stubPagingSuccess(firstPage)

        viewModel.load()
        verify { endpoint.markAsRead(true) }

        val secondPage = listOf(article("p1-0"))
        endpoint.stubPagingSuccess(secondPage)
        viewModel.loadIfPossible()

        verify { endpoint.markAsRead(false) }
    }

    @Test
    fun `lastNewsDate is set from the first article only on a page-0 success`() {
        val endpoint = mockNewsEndpoint()
        val firstDate = Date(1_700_000_000_000L)
        val firstPage = listOf(article("p0-0", firstDate)) + fullPage("p0").drop(1)
        endpoint.stubPagingSuccess(firstPage)

        viewModel.load()

        verify { preferenceHelper.lastNewsDate = firstDate.toInstant().let { org.threeten.bp.Instant.ofEpochMilli(it.toEpochMilli()) } }
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.news.NewsViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/news/NewsViewModelTest.kt
git commit -m "test: add NewsViewModel unit tests"
```

---

### Task 31: Unit tests — NotificationViewModel

**Goal:** Verify `NotificationViewModel`'s custom two-phase `dataSingle` (page 0 zips an "unread" call and a "read" call through the *same* `api.notifications.notifications()` endpoint, calling `.build()` twice), its `areItemsTheSame` override (plain `==`, not `ProxerIdItem.id`), its `refresh() = reload()` override (same effect as `BookmarkViewModel`: `refreshError` is unreachable via the public API), and the deletion queue / `deleteAll()`.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/notification/NotificationViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` on page 0 merges the unread and read results into `data`, and clears error
- [ ] `load()` sets error on failure (unread call fails)
- [ ] `isLoading` is false after a successful load
- [ ] `isLoading` is false after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `hasReachedEnd` becomes true once the combined page result is smaller than `itemsOnPage`; `loadIfPossible()` then no-ops
- [ ] `refresh()` behaves like `reload()` — an error with existing data still lands on `error`, never `refreshError`
- [ ] `areItemsTheSame` uses full equality, not just `id` — two notifications sharing an `id` but differing elsewhere are not deduplicated
- [ ] `deleteAll()` clears `data` on success and sets `deletionError` on failure
- [ ] `addItemToDelete()` removes the item and chains to the next queued deletion
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.notification.NotificationViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/notification/NotificationViewModelTest.kt`**

```kotlin
package me.proxer.app.notification

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.ProxerNotification
import me.proxer.library.ProxerApi
import me.proxer.library.ProxerCall
import me.proxer.library.ProxerException
import me.proxer.library.api.notifications.DeleteNotificationEndpoint
import me.proxer.library.api.notifications.NotificationsEndpoint
import me.proxer.library.entity.notifications.Notification
import me.proxer.library.enums.NotificationType
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.util.Date

class NotificationViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: NotificationViewModel

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

        return endpoint
    }

    private fun mockCall(value: List<ProxerNotification>): ProxerCall<List<ProxerNotification>> {
        val call = mockk<ProxerCall<List<ProxerNotification>>>(relaxed = true)

        every { call.clone() } returns call
        every { call.safeExecute() } returns value

        return call
    }

    private fun mockErrorCall(): ProxerCall<List<ProxerNotification>> {
        val call = mockk<ProxerCall<List<ProxerNotification>>>(relaxed = true)

        every { call.clone() } returns call
        every { call.safeExecute() } throws ProxerException(ProxerException.ErrorType.IO)

        return call
    }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        viewModel = NotificationViewModel()
    }

    @Test
    fun `load merges unread and read results on page 0`() {
        val endpoint = mockNotificationsEndpoint()
        val unread = (0 until 17).map { notification("u$it") }
        val read = (0 until 15).map { notification("r$it") }
        every { endpoint.build() } returnsMany listOf(mockCall(unread), mockCall(read))

        viewModel.load()

        assertEquals(32, viewModel.data.value?.size)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error when the unread call fails`() {
        val endpoint = mockNotificationsEndpoint()
        every { endpoint.build() } returns mockErrorCall()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockNotificationsEndpoint()
        every { endpoint.build() } returnsMany listOf(mockCall(emptyList()), mockCall(emptyList()))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockNotificationsEndpoint()
        every { endpoint.build() } returns mockErrorCall()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockNotificationsEndpoint()
        val unread = (0 until 17).map { notification("u$it") }
        val read = (0 until 15).map { notification("r$it") }
        every { endpoint.build() } returnsMany listOf(mockCall(unread), mockCall(read))
        viewModel.load()
        assertEquals(32, viewModel.data.value?.size)

        val secondUnread = listOf(notification("u2-0"))
        val secondRead = listOf(notification("r2-0"))
        every { endpoint.build() } returnsMany listOf(mockCall(secondUnread), mockCall(secondRead))
        viewModel.reload()

        assertEquals(2, viewModel.data.value?.size)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockNotificationsEndpoint()
        val unread = listOf(notification("u0"))
        val read = listOf(notification("r0"))
        every { endpoint.build() } returnsMany listOf(mockCall(unread), mockCall(read))

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        every { endpoint.build() } returns mockCall(listOf(notification("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(2, viewModel.data.value?.size)
    }

    @Test
    fun `refresh behaves like reload, so an error with existing data still sets error`() {
        val endpoint = mockNotificationsEndpoint()
        val unread = (0 until 17).map { notification("u$it") }
        val read = (0 until 15).map { notification("r$it") }
        every { endpoint.build() } returnsMany listOf(mockCall(unread), mockCall(read))
        viewModel.load()
        assertEquals(32, viewModel.data.value?.size)

        every { endpoint.build() } returns mockErrorCall()
        viewModel.refresh()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
        assertNull(viewModel.refreshError.value)
    }

    @Test
    fun `areItemsTheSame uses full equality, not just id`() {
        val endpoint = mockNotificationsEndpoint()
        val original = listOf(notification("shared-id", "original text"))
        every { endpoint.build() } returnsMany listOf(mockCall(original), mockCall(emptyList()))
        viewModel.load()
        assertEquals(1, viewModel.data.value?.size)

        val changed = listOf(notification("shared-id", "different text"))
        every { endpoint.build() } returns mockCall(changed)
        viewModel.loadIfPossible()

        // Same id but different content: NotificationViewModel's areItemsTheSame() override
        // uses plain equals(), so the old entry is treated as distinct and both remain.
        assertEquals(2, viewModel.data.value?.size)
    }

    @Test
    fun `deleteAll clears data on success and sets deletionError on failure`() {
        every { api.notifications.deleteAllNotifications() } returns mockk(relaxed = true) {
            every { build() } returns mockCall(emptyList()) as ProxerCall<Nothing>
        }

        viewModel.deleteAll()

        assertEquals(emptyList<ProxerNotification>(), viewModel.data.value)
    }

    @Test
    fun `addItemToDelete removes the item and chains to the next queued deletion`() {
        val endpoint = mockNotificationsEndpoint()
        val items = listOf(notification("a"), notification("b"))
        every { endpoint.build() } returnsMany listOf(mockCall(items), mockCall(emptyList()))
        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        val deleteEndpointA = mockk<DeleteNotificationEndpoint>(relaxed = true)
        every { api.notifications.deleteNotification("a") } returns deleteEndpointA
        val deleteCallA = mockk<ProxerCall<Unit>>(relaxed = true)
        every { deleteCallA.clone() } returns deleteCallA
        every { deleteCallA.safeExecute() } returns Unit
        every { deleteEndpointA.build() } returns deleteCallA

        viewModel.addItemToDelete(items[0])

        assertTrue(viewModel.data.value!!.none { it.id == "a" })
        assertEquals(1, viewModel.data.value?.size)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.notification.NotificationViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/notification/NotificationViewModelTest.kt
git commit -m "test: add NotificationViewModel unit tests"
```

---

### Task 32: Unit tests — ProfileCommentViewModel

**Goal:** Verify `ProfileCommentViewModel`'s pagination (a plain `PagedViewModel<ParsedUserComment>` with a custom `dataSingle`), the `category` reload trigger, and `deleteComment()`/`updateComment()`. Like `CommentsViewModel`, its `dataSingle` hops onto `Schedulers.computation()`, so that scheduler is forced onto the trampoline too.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/profile/comment/ProfileCommentViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets data on success and clears error
- [ ] `load()` sets error on failure
- [ ] `isLoading` is false after a successful load
- [ ] `isLoading` is false after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `hasReachedEnd` becomes true once fewer than `itemsOnPage` comments are returned; `loadIfPossible()` then no-ops
- [ ] `refresh()` merges page-0 results with existing data; an error during that refresh with existing data sets `refreshError`
- [ ] Changing `category` triggers a reload
- [ ] `deleteComment()` removes the item from `data` on success and deletes the stored draft
- [ ] `updateComment()` updates the matching item's rating/content/instant in place
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.profile.comment.ProfileCommentViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/profile/comment/ProfileCommentViewModelTest.kt`**

```kotlin
package me.proxer.app.profile.comment

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.base.stubSuccess
import me.proxer.app.comment.LocalComment
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.comment.UpdateCommentEndpoint
import me.proxer.library.api.user.UserCommentsEndpoint
import me.proxer.library.entity.info.RatingDetails
import me.proxer.library.entity.user.UserComment
import me.proxer.library.enums.Category
import me.proxer.library.enums.Medium
import me.proxer.library.enums.UserMediaProgress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.util.Date

class ProfileCommentViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: ProfileCommentViewModel

    private fun comment(id: String) = UserComment(
        id,
        "entry-1",
        "Entry Name",
        Medium.ANIMESERIES,
        Category.ANIME,
        "author-$id",
        UserMediaProgress.WATCHED,
        RatingDetails(),
        "Comment $id",
        5,
        1,
        0,
        Date(),
        "Author $id",
        "image.png",
    )

    private fun fullPage(prefix: String) = (0 until 10).map { comment("$prefix-$it") }

    private fun mockUserCommentsEndpoint(): UserCommentsEndpoint {
        val endpoint = mockk<UserCommentsEndpoint>(relaxed = true)

        every { api.user.comments("user-1", null) } returns endpoint
        every { endpoint.category(any()) } returns endpoint
        every { endpoint.hasContent(*anyVararg()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        // ProfileCommentViewModel's dataSingle hops onto Schedulers.computation() while
        // mapping comments; force that scheduler onto the trampoline too.
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }

        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true

        viewModel = ProfileCommentViewModel("user-1", null, null)
    }

    @After
    fun teardown() {
        RxJavaPlugins.setComputationSchedulerHandler(null)
    }

    @Test
    fun `load sets data on success`() {
        val endpoint = mockUserCommentsEndpoint()
        val page = fullPage("p0")
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockUserCommentsEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful load`() {
        val endpoint = mockUserCommentsEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `isLoading is false after failed load`() {
        val endpoint = mockUserCommentsEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockUserCommentsEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val secondPage = fullPage("p1")
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockUserCommentsEndpoint()
        val lastPage = listOf(comment("last-0"), comment("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        endpoint.stubPagingSuccess(listOf(comment("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(2, viewModel.data.value?.size)
    }

    @Test
    fun `refresh merges page-0 results and a refresh error sets refreshError`() {
        val endpoint = mockUserCommentsEndpoint()
        val original = fullPage("p0")
        endpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val refreshed = listOf(comment("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()
        assertEquals(refreshed.map { it.id } + listOf(original.first().id), viewModel.data.value?.map { it.id })

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `category change triggers reload`() {
        val endpoint = mockUserCommentsEndpoint()
        endpoint.stubPagingSuccess(fullPage("p0"))
        viewModel.load()
        assertEquals(10, viewModel.data.value?.size)

        val mangaOnly = listOf(comment("manga-0"))
        endpoint.stubPagingSuccess(mangaOnly)

        viewModel.category = Category.MANGA

        assertEquals(mangaOnly.map { it.id }, viewModel.data.value?.map { it.id })
    }

    @Test
    fun `deleteComment removes the item and deletes the draft on success`() {
        val endpoint = mockUserCommentsEndpoint()
        val comments = fullPage("p0")
        endpoint.stubPagingSuccess(comments)
        viewModel.load()

        val target = viewModel.data.value!!.first()
        val updateEndpoint = mockk<UpdateCommentEndpoint>(relaxed = true)
        every { api.comment.update(target.id) } returns updateEndpoint
        every { updateEndpoint.comment(any()) } returns updateEndpoint
        every { updateEndpoint.rating(any()) } returns updateEndpoint
        updateEndpoint.stubSuccess(Unit)

        viewModel.deleteComment(target)

        assertEquals(9, viewModel.data.value?.size)
        assertFalse(viewModel.data.value!!.any { it.id == target.id })
    }

    @Test
    fun `updateComment updates the matching entry in place`() {
        val endpoint = mockUserCommentsEndpoint()
        val comments = fullPage("p0")
        endpoint.stubPagingSuccess(comments)
        viewModel.load()

        val target = viewModel.data.value!!.first()
        val update = LocalComment(
            target.id,
            target.entryId,
            UserMediaProgress.WILL_WATCH,
            RatingDetails(9, 8, 7, 6, 5),
            "Updated",
            9,
            target.episode,
        )

        viewModel.updateComment(update)

        val updated = viewModel.data.value!!.first { it.id == target.id }
        assertEquals(9, updated.overallRating)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.profile.comment.ProfileCommentViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/profile/comment/ProfileCommentViewModelTest.kt
git commit -m "test: add ProfileCommentViewModel unit tests"
```

---

### Task 33: Unit tests — HistoryViewModel

**Goal:** Verify `HistoryViewModel`'s pagination (a plain `PagedViewModel<LocalUserHistoryEntry>` with a custom `dataSingle`), its own-profile-vs-other-profile branching (`storageHelper.user?.matches(userId, username)`), and the extra `storageHelper.isLoggedInObservable` subscription that reloads on login-state changes.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/profile/history/HistoryViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets data on success and clears error, using `api.ucp.history()` for the current user's own profile
- [ ] `load()` uses `api.user.history(userId, username)` (with `includeHentai` wired from preferences/login state) for another user's profile
- [ ] `load()` sets error on failure
- [ ] `isLoading` is false after a successful load
- [ ] `isLoading` is false after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `hasReachedEnd` becomes true once fewer than `itemsOnPage` entries are returned; `loadIfPossible()` then no-ops
- [ ] `refresh()` merges page-0 results with existing data; an error during that refresh with existing data sets `refreshError`
- [ ] A login-state change on `storageHelper.isLoggedInObservable` triggers a reload
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.profile.history.HistoryViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/profile/history/HistoryViewModelTest.kt`**

```kotlin
package me.proxer.app.profile.history

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import me.proxer.app.auth.LocalUser
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.ucp.UcpHistoryEndpoint
import me.proxer.library.api.user.UserHistoryEndpoint
import me.proxer.library.entity.ucp.UcpHistoryEntry
import me.proxer.library.entity.user.UserHistoryEntry
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaLanguage
import me.proxer.library.enums.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.util.Date

class HistoryViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private fun ucpEntry(id: String) = UcpHistoryEntry(
        id,
        "entry-$id",
        "Entry $id",
        MediaLanguage.GERMAN_SUB,
        Medium.ANIMESERIES,
        Category.ANIME,
        1,
        Date(),
    )

    private fun userEntry(id: String) = UserHistoryEntry(
        id,
        "entry-$id",
        "Entry $id",
        MediaLanguage.GERMAN_SUB,
        Medium.ANIMESERIES,
        Category.ANIME,
        1,
    )

    private fun mockUcpHistoryEndpoint(): UcpHistoryEndpoint {
        val endpoint = mockk<UcpHistoryEndpoint>(relaxed = true)

        every { api.ucp.history() } returns endpoint

        return endpoint
    }

    private fun mockUserHistoryEndpoint(): UserHistoryEndpoint {
        val endpoint = mockk<UserHistoryEndpoint>(relaxed = true)

        every { api.user.history("other-id", "othername") } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { storageHelper.user } returns LocalUser("token", "self-id", "Self", "image.png")
    }

    @Test
    fun `load sets data on success for the current user's own profile`() {
        val viewModel = HistoryViewModel("self-id", null)
        val endpoint = mockUcpHistoryEndpoint()
        val page = (0 until 50).map { ucpEntry("p0-$it") }
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load uses the user endpoint for another user's profile`() {
        val viewModel = HistoryViewModel("other-id", "othername")
        val endpoint = mockUserHistoryEndpoint()
        val page = (0 until 50).map { userEntry("p0-$it") }
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val viewModel = HistoryViewModel("self-id", null)
        val endpoint = mockUcpHistoryEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful and failed loads`() {
        val viewModel = HistoryViewModel("self-id", null)
        val endpoint = mockUcpHistoryEndpoint()

        endpoint.stubPagingSuccess(listOf(ucpEntry("a")))
        viewModel.load()
        assertFalse(viewModel.isLoading.value == true)

        endpoint.stubPagingError()
        viewModel.load()
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val viewModel = HistoryViewModel("self-id", null)
        val endpoint = mockUcpHistoryEndpoint()
        val page = (0 until 50).map { ucpEntry("p0-$it") }
        endpoint.stubPagingSuccess(page)
        viewModel.load()
        assertEquals(50, viewModel.data.value?.size)

        val secondPage = (0 until 50).map { ucpEntry("p1-$it") }
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val viewModel = HistoryViewModel("self-id", null)
        val endpoint = mockUcpHistoryEndpoint()
        val lastPage = listOf(ucpEntry("last-0"), ucpEntry("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        endpoint.stubPagingSuccess(listOf(ucpEntry("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(2, viewModel.data.value?.size)
    }

    @Test
    fun `refresh merges page-0 results and a refresh error sets refreshError`() {
        val viewModel = HistoryViewModel("self-id", null)
        val endpoint = mockUcpHistoryEndpoint()
        val original = (0 until 50).map { ucpEntry("p0-$it") }
        endpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(50, viewModel.data.value?.size)

        val refreshed = listOf(ucpEntry("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()
        assertEquals(refreshed.map { it.id } + listOf(original.first().id), viewModel.data.value?.map { it.id })

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `a login state change triggers a reload`() {
        val loginSubject = PublishSubject.create<Boolean>()
        every { storageHelper.isLoggedInObservable } returns loginSubject

        val viewModel = HistoryViewModel("self-id", null)
        val endpoint = mockUcpHistoryEndpoint()
        val firstPage = listOf(ucpEntry("first"))
        endpoint.stubPagingSuccess(firstPage)
        viewModel.load()
        assertEquals(firstPage.map { it.id }, viewModel.data.value?.map { it.id })

        val secondPage = listOf(ucpEntry("second"))
        endpoint.stubPagingSuccess(secondPage)

        loginSubject.onNext(true)

        assertEquals(secondPage.map { it.id }, viewModel.data.value?.map { it.id })
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.profile.history.HistoryViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/profile/history/HistoryViewModelTest.kt
git commit -m "test: add HistoryViewModel unit tests"
```

---

### Task 34: Unit tests — ProfileMediaListViewModel

**Goal:** Verify `ProfileMediaListViewModel`'s pagination (a plain `PagedViewModel<LocalUserMediaListEntry>` with a custom `dataSingle`), its own-profile-vs-other-profile branching, the `category`/`filter` reload triggers, the `storageHelper.isLoggedInObservable` reload subscription, and the comment-deletion queue.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/profile/media/ProfileMediaListViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `load()` sets data on success, using `api.ucp.mediaList()` for the current user's own profile
- [ ] `load()` uses `api.user.mediaList(userId, username)` for another user's profile
- [ ] `load()` sets error on failure
- [ ] `isLoading` is false after a successful load
- [ ] `isLoading` is false after a failed load
- [ ] `reload()` clears data/error then loads new data
- [ ] `hasReachedEnd` becomes true once fewer than `itemsOnPage` entries are returned; `loadIfPossible()` then no-ops
- [ ] `refresh()` merges page-0 results with existing data; an error during that refresh with existing data sets `refreshError`
- [ ] Changing `category` or `filter` triggers a reload
- [ ] `addItemToDelete()` removes the item's comment on success and chains to the next queued deletion, or sets `itemDeletionError` on failure
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.profile.media.ProfileMediaListViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/profile/media/ProfileMediaListViewModelTest.kt`**

```kotlin
package me.proxer.app.profile.media

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import me.proxer.app.auth.LocalUser
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubPagingError
import me.proxer.app.base.stubPagingSuccess
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.library.ProxerApi
import me.proxer.library.api.ucp.DeleteCommentEndpoint
import me.proxer.library.api.ucp.UcpMediaListEndpoint
import me.proxer.library.api.user.UserMediaListEndpoint
import me.proxer.library.entity.user.UserMediaListEntry
import me.proxer.library.enums.Category
import me.proxer.library.enums.MediaState
import me.proxer.library.enums.Medium
import me.proxer.library.enums.UserMediaListFilterType
import me.proxer.library.enums.UserMediaProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class ProfileMediaListViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: ProfileMediaListViewModel

    private fun mediaEntry(id: String, commentId: String = "comment-$id") = UserMediaListEntry(
        id,
        "Entry $id",
        12,
        Medium.ANIMESERIES,
        MediaState.FINISHED,
        commentId,
        "Comment content",
        UserMediaProgress.WATCHING,
        5,
        8,
    )

    private fun mockUcpMediaListEndpoint(): UcpMediaListEndpoint {
        val endpoint = mockk<UcpMediaListEndpoint>(relaxed = true)

        every { api.ucp.mediaList() } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        every { endpoint.category(any()) } returns endpoint
        every { endpoint.filter(any()) } returns endpoint

        return endpoint
    }

    @Before
    fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        every { preferenceHelper.isAgeRestrictedMediaAllowedObservable } returns Observable.never()
        every { storageHelper.isLoggedIn } returns true
        every { storageHelper.user } returns LocalUser("token", "self-id", "Self", "image.png")

        viewModel = ProfileMediaListViewModel("self-id", null, Category.ANIME, UserMediaListFilterType.WATCHING)
    }

    @Test
    fun `load sets data on success for the current user's own profile`() {
        val endpoint = mockUcpMediaListEndpoint()
        val page = (0 until 30).map { mediaEntry("p0-$it") }
        endpoint.stubPagingSuccess(page)

        viewModel.load()

        assertEquals(page.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `load uses the user endpoint for another user's profile`() {
        val other = ProfileMediaListViewModel("other-id", "othername", Category.ANIME, null)
        val endpoint = mockk<UserMediaListEndpoint>(relaxed = true)
        every { api.user.mediaList("other-id", "othername") } returns endpoint
        every { endpoint.includeHentai(any()) } returns endpoint
        every { endpoint.category(any()) } returns endpoint
        every { endpoint.filter(any()) } returns endpoint

        val page = (0 until 30).map { mediaEntry("p0-$it") }
        endpoint.stubPagingSuccess(page)

        other.load()

        assertEquals(page.map { it.id }, other.data.value?.map { it.id })
        assertNull(other.error.value)
    }

    @Test
    fun `load sets error on failure`() {
        val endpoint = mockUcpMediaListEndpoint()
        endpoint.stubPagingError()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is false after successful and failed loads`() {
        val endpoint = mockUcpMediaListEndpoint()

        endpoint.stubPagingSuccess(listOf(mediaEntry("a")))
        viewModel.load()
        assertFalse(viewModel.isLoading.value == true)

        endpoint.stubPagingError()
        viewModel.load()
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `reload clears data and error then loads new data`() {
        val endpoint = mockUcpMediaListEndpoint()
        val page = (0 until 30).map { mediaEntry("p0-$it") }
        endpoint.stubPagingSuccess(page)
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val secondPage = (0 until 30).map { mediaEntry("p1-$it") }
        endpoint.stubPagingSuccess(secondPage)
        viewModel.reload()

        assertEquals(secondPage.map { it.id }, viewModel.data.value?.map { it.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `hasReachedEnd stops further loads via loadIfPossible`() {
        val endpoint = mockUcpMediaListEndpoint()
        val lastPage = listOf(mediaEntry("last-0"), mediaEntry("last-1"))
        endpoint.stubPagingSuccess(lastPage)

        viewModel.load()
        assertEquals(2, viewModel.data.value?.size)

        endpoint.stubPagingSuccess(listOf(mediaEntry("should-not-appear")))
        viewModel.loadIfPossible()

        assertEquals(2, viewModel.data.value?.size)
    }

    @Test
    fun `refresh merges page-0 results and a refresh error sets refreshError`() {
        val endpoint = mockUcpMediaListEndpoint()
        val original = (0 until 30).map { mediaEntry("p0-$it") }
        endpoint.stubPagingSuccess(original)
        viewModel.load()
        assertEquals(30, viewModel.data.value?.size)

        val refreshed = listOf(mediaEntry("new-0")) + original.drop(1)
        endpoint.stubPagingSuccess(refreshed)
        viewModel.refresh()
        assertEquals(refreshed.map { it.id } + listOf(original.first().id), viewModel.data.value?.map { it.id })

        endpoint.stubPagingError()
        viewModel.refresh()

        assertNotNull(viewModel.refreshError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `category and filter changes trigger a reload`() {
        val endpoint = mockUcpMediaListEndpoint()
        endpoint.stubPagingSuccess(listOf(mediaEntry("initial")))
        viewModel.load()

        val afterCategory = listOf(mediaEntry("manga-0"))
        endpoint.stubPagingSuccess(afterCategory)
        viewModel.category = Category.MANGA
        assertEquals(afterCategory.map { it.id }, viewModel.data.value?.map { it.id })

        val afterFilter = listOf(mediaEntry("watched-0"))
        endpoint.stubPagingSuccess(afterFilter)
        viewModel.filter = UserMediaListFilterType.WATCHED
        assertEquals(afterFilter.map { it.id }, viewModel.data.value?.map { it.id })
    }

    @Test
    fun `addItemToDelete removes the comment on success and chains to the next queued deletion`() {
        val endpoint = mockUcpMediaListEndpoint()
        val items = listOf(mediaEntry("a", "comment-a"), mediaEntry("b", "comment-b"))
        endpoint.stubPagingSuccess(items)
        viewModel.load()

        val deleteEndpoint = mockk<DeleteCommentEndpoint>(relaxed = true)
        every { api.ucp.deleteComment("comment-a") } returns deleteEndpoint
        deleteEndpoint.stubSuccess(Unit)

        viewModel.addItemToDelete(items[0])

        assertEquals(listOf(items[1]), viewModel.data.value)
    }

    @Test
    fun `addItemToDelete sets itemDeletionError on failure`() {
        val endpoint = mockUcpMediaListEndpoint()
        val items = listOf(mediaEntry("a", "comment-a"))
        endpoint.stubPagingSuccess(items)
        viewModel.load()

        val deleteEndpoint = mockk<DeleteCommentEndpoint>(relaxed = true)
        every { api.ucp.deleteComment("comment-a") } returns deleteEndpoint
        deleteEndpoint.stubError()

        viewModel.addItemToDelete(items[0])

        assertNotNull(viewModel.itemDeletionError.value)
        assertEquals(items, viewModel.data.value)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.profile.media.ProfileMediaListViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/profile/media/ProfileMediaListViewModelTest.kt
git commit -m "test: add ProfileMediaListViewModel unit tests"
```

---
### Task 35: Unit tests — LoginViewModel

**Goal:** Verify `LoginViewModel`'s login flow — success (user + profile settings persisted), generic failure, 2FA-required failure (enables the persisted flag), the "already loading" guard, and the `secretKey`/`temporaryToken` plumbing.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/auth/LoginViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `init` seeds `isTwoFactorAuthenticationEnabled` from `preferenceHelper.isTwoFactorAuthenticationEnabled`
- [ ] `login()` success sets `success = Unit`, clears `error`, sets `isLoading = false`, and persists `storageHelper.user` / `storageHelper.profileSettings`
- [ ] `login()` clears `storageHelper.temporaryToken` after completion (set during the call, cleared in `doFinally`)
- [ ] `login()` generic failure sets `error`, leaves `success` null, does not touch `isTwoFactorAuthenticationEnabled`
- [ ] `login()` failing with `USER_2FA_SECRET_REQUIRED` persists `preferenceHelper.isTwoFactorAuthenticationEnabled = true` and updates the LiveData
- [ ] `login()` is a no-op while `isLoading == true`
- [ ] `login()` forwards the `secretKey` argument to the endpoint
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.auth.LoginViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/auth/LoginViewModelTest.kt`**

```kotlin
package me.proxer.app.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.profile.settings.LocalProfileSettings
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.toLocalSettings
import me.proxer.library.ProxerApi
import me.proxer.library.ProxerException
import me.proxer.library.api.ucp.SettingsEndpoint
import me.proxer.library.api.user.LoginEndpoint
import me.proxer.library.entity.user.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class LoginViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()
    private val preferenceHelper: PreferenceHelper by inject()

    private lateinit var viewModel: LoginViewModel

    private val fixtureUser = User("42", "image.jpg", false, false, "token123")
    private val fixtureSettings = LocalProfileSettings.default().toNonLocalSettings()

    @Before
    fun setup() {
        every { preferenceHelper.isTwoFactorAuthenticationEnabled } returns false

        val settingsEndpoint = mockk<SettingsEndpoint>(relaxed = true)
        every { api.ucp.settings() } returns settingsEndpoint
        settingsEndpoint.stubSuccess(fixtureSettings)

        viewModel = LoginViewModel()
    }

    private fun stubLogin(): LoginEndpoint {
        val loginEndpoint = mockk<LoginEndpoint>(relaxed = true)

        every { api.user.login(any(), any()) } returns loginEndpoint
        every { loginEndpoint.secretKey(any()) } returns loginEndpoint

        return loginEndpoint
    }

    @Test
    fun `init seeds isTwoFactorAuthenticationEnabled from preferenceHelper`() {
        every { preferenceHelper.isTwoFactorAuthenticationEnabled } returns true

        val freshViewModel = LoginViewModel()

        assertTrue(freshViewModel.isTwoFactorAuthenticationEnabled.value == true)
    }

    @Test
    fun `login sets success and stores user and settings on success`() {
        val loginEndpoint = stubLogin()
        loginEndpoint.stubSuccess(fixtureUser)

        viewModel.login("someuser", "hunter2", null)

        assertEquals(Unit, viewModel.success.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)

        verify { storageHelper.user = LocalUser("token123", "42", "someuser", "image.jpg") }
        verify { storageHelper.profileSettings = fixtureSettings.toLocalSettings() }
    }

    @Test
    fun `login clears temporaryToken after completion`() {
        val loginEndpoint = stubLogin()
        loginEndpoint.stubSuccess(fixtureUser)

        viewModel.login("someuser", "hunter2", null)

        verify { storageHelper.temporaryToken = "token123" }
        verify { storageHelper.temporaryToken = null }
    }

    @Test
    fun `login sets error on generic failure`() {
        val loginEndpoint = stubLogin()
        loginEndpoint.stubError()

        viewModel.login("someuser", "wrong", null)

        assertNull(viewModel.success.value)
        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)
        assertFalse(viewModel.isTwoFactorAuthenticationEnabled.value == true)
    }

    @Test
    fun `login enables two-factor flag when server requires it`() {
        val loginEndpoint = stubLogin()
        loginEndpoint.stubError(
            ProxerException(
                ProxerException.ErrorType.SERVER,
                ProxerException.ServerErrorType.USER_2FA_SECRET_REQUIRED,
            ),
        )

        viewModel.login("someuser", "hunter2", null)

        assertNotNull(viewModel.error.value)
        assertTrue(viewModel.isTwoFactorAuthenticationEnabled.value == true)
        verify { preferenceHelper.isTwoFactorAuthenticationEnabled = true }
    }

    @Test
    fun `login is ignored while already loading`() {
        viewModel.isLoading.value = true

        viewModel.login("someuser", "hunter2", null)

        verify(exactly = 0) { api.user.login(any(), any()) }
    }

    @Test
    fun `login passes the secretKey to the endpoint`() {
        val loginEndpoint = stubLogin()
        loginEndpoint.stubSuccess(fixtureUser)

        viewModel.login("someuser", "hunter2", "123456")

        verify { loginEndpoint.secretKey("123456") }
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.auth.LoginViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/auth/LoginViewModelTest.kt
git commit -m "test: add LoginViewModel unit tests"
```

---

### Task 36: Unit tests — LogoutViewModel

**Goal:** Verify `LogoutViewModel`'s logout flow — success, failure, and the "already loading" guard.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/auth/LogoutViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `logout()` success sets `success = Unit`, clears `error`, sets `isLoading = false`
- [ ] `logout()` failure sets `error`, leaves `success` null, sets `isLoading = false`
- [ ] `logout()` is a no-op while `isLoading == true`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.auth.LogoutViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/auth/LogoutViewModelTest.kt`**

```kotlin
package me.proxer.app.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.library.ProxerApi
import me.proxer.library.api.user.LogoutEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class LogoutViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()

    private lateinit var viewModel: LogoutViewModel

    @Before
    fun setup() {
        viewModel = LogoutViewModel()
    }

    @Test
    fun `logout sets success on success`() {
        val logoutEndpoint = mockk<LogoutEndpoint>(relaxed = true)
        every { api.user.logout() } returns logoutEndpoint
        logoutEndpoint.stubSuccess(Unit)

        viewModel.logout()

        assertEquals(Unit, viewModel.success.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `logout sets error on failure`() {
        val logoutEndpoint = mockk<LogoutEndpoint>(relaxed = true)
        every { api.user.logout() } returns logoutEndpoint
        logoutEndpoint.stubError()

        viewModel.logout()

        assertNull(viewModel.success.value)
        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `logout is ignored while already loading`() {
        viewModel.isLoading.value = true

        viewModel.logout()

        verify(exactly = 0) { api.user.logout() }
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.auth.LogoutViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/auth/LogoutViewModelTest.kt
git commit -m "test: add LogoutViewModel unit tests"
```

---

### Task 37: Unit tests — CreateConferenceViewModel

**Goal:** Verify `CreateConferenceViewModel`'s creation flow — endpoint dispatch for both 1:1 and group chats, the deferred completion via `RxBus` (`MessengerWorker.SynchronizationEvent` / `MessengerErrorEvent`), and the immediate endpoint-failure path. This VM injects `MessengerDao` and (transitively, through `MessengerWorker.enqueueSynchronization()`) `WorkManager`, neither of which exist in `fakeAppModule()` yet.

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/base/FakeAppModule.kt`
- Create: `src/test/kotlin/me/proxer/app/chat/prv/create/CreateConferenceViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `createChat()` calls `api.messenger.createConference(firstMessage, username)` and sets `isLoading = true`
- [ ] `createGroup()` calls `api.messenger.createConferenceGroup(topic, firstMessage, usernames)` with participants mapped to usernames
- [ ] A successful endpoint call only completes (`result` set, `isLoading = false`) once the matching `MessengerWorker.SynchronizationEvent` arrives on the bus and `messengerDao.findConference(id)` resolves
- [ ] Endpoint failure sets `error` and `isLoading = false` immediately, without needing a bus event
- [ ] A `MessengerErrorEvent` posted after a pending creation sets `error`, `isLoading = false`, `result = null`
- [ ] A `SynchronizationEvent` posted with no pending creation is a no-op (`result`/`isLoading` stay untouched)
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.chat.prv.create.CreateConferenceViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Modify `src/test/kotlin/me/proxer/app/base/FakeAppModule.kt`**

`MessengerDao` (Task 5), `OkHttpClient` (Task 21), and `TagDao` (Task 29) are already present — keep all three, adding only `WorkManager`:

```kotlin
package me.proxer.app.base

import androidx.work.WorkManager
import com.rubengees.rxbus.RxBus
import io.mockk.mockk
import me.proxer.app.chat.prv.sync.MessengerDao
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
}
```

- [ ] **Step 2: Create `src/test/kotlin/me/proxer/app/chat/prv/create/CreateConferenceViewModelTest.kt`**

```kotlin
package me.proxer.app.chat.prv.create

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.rubengees.rxbus.RxBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.chat.prv.Participant
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.chat.prv.sync.MessengerErrorEvent
import me.proxer.app.chat.prv.sync.MessengerWorker
import me.proxer.app.exception.ChatException
import me.proxer.library.ProxerApi
import me.proxer.library.api.messenger.CreateConferenceEndpoint
import me.proxer.library.api.messenger.CreateConferenceGroupEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import org.threeten.bp.Instant
import java.io.IOException

class CreateConferenceViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val bus: RxBus by inject()
    private val messengerDao: MessengerDao by inject()

    private lateinit var viewModel: CreateConferenceViewModel

    private val fixtureConference = LocalConference(
        id = 123L,
        topic = "Some topic",
        customTopic = "",
        participantAmount = 2,
        image = "",
        imageType = "",
        isGroup = false,
        localIsRead = true,
        isRead = true,
        date = Instant.ofEpochMilli(0L),
        unreadMessageAmount = 0,
        lastReadMessageId = "0",
        isFullyLoaded = true,
    )

    @Before
    fun setup() {
        viewModel = CreateConferenceViewModel()
    }

    private fun stubChatEndpoint(): CreateConferenceEndpoint {
        val endpoint = mockk<CreateConferenceEndpoint>(relaxed = true)

        every { api.messenger.createConference(any(), any()) } returns endpoint

        return endpoint
    }

    private fun stubGroupEndpoint(): CreateConferenceGroupEndpoint {
        val endpoint = mockk<CreateConferenceGroupEndpoint>(relaxed = true)

        every { api.messenger.createConferenceGroup(any(), any(), any()) } returns endpoint

        return endpoint
    }

    @Test
    fun `createChat calls the correct endpoint and sets isLoading`() {
        val endpoint = stubChatEndpoint()
        endpoint.stubSuccess("123")

        viewModel.createChat("hello", Participant("bob"))

        verify { api.messenger.createConference("hello", "bob") }
        assertTrue(viewModel.isLoading.value == true)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `createGroup calls the correct endpoint with mapped participant usernames`() {
        val endpoint = stubGroupEndpoint()
        endpoint.stubSuccess("123")

        viewModel.createGroup("topic", "hello", listOf(Participant("bob"), Participant("alice")))

        verify { api.messenger.createConferenceGroup("topic", "hello", listOf("bob", "alice")) }
        assertTrue(viewModel.isLoading.value == true)
    }

    @Test
    fun `successful creation completes once the synchronization bus event arrives`() {
        val endpoint = stubChatEndpoint()
        endpoint.stubSuccess("123")
        every { messengerDao.findConference(123L) } returns fixtureConference

        viewModel.createChat("hello", Participant("bob"))
        bus.post(MessengerWorker.SynchronizationEvent())

        assertEquals(fixtureConference, viewModel.result.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `endpoint failure sets error and stops loading immediately`() {
        val endpoint = stubChatEndpoint()
        endpoint.stubError()

        viewModel.createChat("hello", Participant("bob"))

        assertNotNull(viewModel.error.value)
        assertNull(viewModel.result.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `synchronization error event sets error once a creation is pending`() {
        val endpoint = stubChatEndpoint()
        endpoint.stubSuccess("123")

        viewModel.createChat("hello", Participant("bob"))
        bus.post(MessengerErrorEvent(ChatException(IOException("sync failed"))))

        assertNotNull(viewModel.error.value)
        assertNull(viewModel.result.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `synchronization bus event is ignored when no creation is pending`() {
        bus.post(MessengerWorker.SynchronizationEvent())

        assertNull(viewModel.result.value)
        assertNull(viewModel.isLoading.value)
    }
}
```

- [ ] **Step 3: Verify**

```bash
./gradlew test --tests "me.proxer.app.chat.prv.create.CreateConferenceViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/me/proxer/app/base/FakeAppModule.kt src/test/kotlin/me/proxer/app/chat/prv/create/CreateConferenceViewModelTest.kt
git commit -m "test: add CreateConferenceViewModel unit tests"
```

---

### Task 38: Unit tests — ProfileSettingsViewModel

**Goal:** Verify `ProfileSettingsViewModel`'s implicit-load-on-construct behavior, `refresh()` success/failure, `update()`'s optimistic-write pattern with its separate `updateError` channel, and `retryUpdate()`.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/profile/settings/ProfileSettingsViewModelTest.kt`

**Acceptance Criteria:**
- [ ] Construction seeds `data` from `storageHelper.profileSettings`, then immediately triggers `refresh()`, which overwrites `data` with the network result and persists it via `storageHelper.profileSettings =`
- [ ] `refresh()` failure sets `error` and leaves the previous `data` untouched
- [ ] `refresh()` success clears a previous `error`
- [ ] `update()` sets `data` optimistically (before the network call resolves) and, on success, persists via `storageHelper.profileSettings =` without touching `error`
- [ ] `update()` failure sets `updateError` (not `error`) while the optimistic `data` value is retained
- [ ] `retryUpdate()` resends the current `data` value through `update()`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.profile.settings.ProfileSettingsViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/profile/settings/ProfileSettingsViewModelTest.kt`**

```kotlin
package me.proxer.app.profile.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.toLocalSettings
import me.proxer.library.ProxerApi
import me.proxer.library.api.ucp.SetSettingsEndpoint
import me.proxer.library.api.ucp.SettingsEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class ProfileSettingsViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val storageHelper: StorageHelper by inject()

    private val fixtureSettings = LocalProfileSettings.default().toNonLocalSettings()
    private val otherFixtureSettings = fixtureSettings.copy(adInterval = 7)

    private lateinit var settingsEndpoint: SettingsEndpoint
    private lateinit var viewModel: ProfileSettingsViewModel

    @Before
    fun setup() {
        every { storageHelper.profileSettings } returns LocalProfileSettings.default()

        settingsEndpoint = mockk(relaxed = true)
        every { api.ucp.settings() } returns settingsEndpoint
        settingsEndpoint.stubSuccess(fixtureSettings)

        viewModel = ProfileSettingsViewModel()
    }

    @Test
    fun `construction triggers a refresh and populates data`() {
        assertEquals(fixtureSettings.toLocalSettings(), viewModel.data.value)
        assertNull(viewModel.error.value)

        verify { storageHelper.profileSettings = fixtureSettings.toLocalSettings() }
    }

    @Test
    fun `refresh sets error on failure and keeps previous data`() {
        val previousData = viewModel.data.value

        settingsEndpoint.stubError()
        viewModel.refresh()

        assertEquals(previousData, viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `refresh clears a previous error on success`() {
        settingsEndpoint.stubError()
        viewModel.refresh()
        assertNotNull(viewModel.error.value)

        settingsEndpoint.stubSuccess(otherFixtureSettings)
        viewModel.refresh()

        assertNull(viewModel.error.value)
        assertEquals(otherFixtureSettings.toLocalSettings(), viewModel.data.value)
    }

    @Test
    fun `update optimistically sets data and persists on success`() {
        val setSettingsEndpoint = mockk<SetSettingsEndpoint>(relaxed = true)
        every { api.ucp.setSettings(any()) } returns setSettingsEndpoint
        setSettingsEndpoint.stubSuccess(listOf("profileVisibility"))

        val newData = otherFixtureSettings.toLocalSettings()
        viewModel.update(newData)

        assertEquals(newData, viewModel.data.value)
        assertNull(viewModel.updateError.value)

        verify { storageHelper.profileSettings = newData }
    }

    @Test
    fun `update sets updateError on failure but keeps the optimistic data`() {
        val setSettingsEndpoint = mockk<SetSettingsEndpoint>(relaxed = true)
        every { api.ucp.setSettings(any()) } returns setSettingsEndpoint
        setSettingsEndpoint.stubError()

        val newData = otherFixtureSettings.toLocalSettings()
        viewModel.update(newData)

        assertEquals(newData, viewModel.data.value)
        assertNotNull(viewModel.updateError.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `retryUpdate resends the current data`() {
        val setSettingsEndpoint = mockk<SetSettingsEndpoint>(relaxed = true)
        every { api.ucp.setSettings(any()) } returns setSettingsEndpoint
        setSettingsEndpoint.stubSuccess(listOf("profileVisibility"))

        val newData = otherFixtureSettings.toLocalSettings()
        viewModel.update(newData)
        viewModel.retryUpdate()

        verify(exactly = 2) { api.ucp.setSettings(newData.toNonLocalSettings()) }
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.profile.settings.ProfileSettingsViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/profile/settings/ProfileSettingsViewModelTest.kt
git commit -m "test: add ProfileSettingsViewModel unit tests"
```

---

### Task 39: Unit tests — LinkCheckViewModel

**Goal:** Verify `LinkCheckViewModel.check()` — success for secure/insecure links, the fallback-to-`false` behavior on failure (this VM deliberately never surfaces an error to `data`), and that a second call uses the new link.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/ui/LinkCheckViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `check()` success with `isSecure = true` sets `data = true`, `isLoading = false`
- [ ] `check()` success with `isSecure = false` sets `data = false`
- [ ] `check()` failure falls back to `data = false` (no error channel exists on this VM)
- [ ] `check()` calls `api.messenger.checkLink(link)` with the given `HttpUrl`
- [ ] A second `check()` call with a different link disposes the first and reflects the new link's result
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.ui.LinkCheckViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/ui/LinkCheckViewModelTest.kt`**

```kotlin
package me.proxer.app.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.library.ProxerApi
import me.proxer.library.api.messenger.CheckLinkEndpoint
import me.proxer.library.entity.messenger.LinkCheckResponse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class LinkCheckViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()

    private lateinit var viewModel: LinkCheckViewModel

    private val fixtureLink = "https://example.com".toHttpUrl()

    @Before
    fun setup() {
        viewModel = LinkCheckViewModel()
    }

    private fun stubCheck(): CheckLinkEndpoint {
        val endpoint = mockk<CheckLinkEndpoint>(relaxed = true)

        every { api.messenger.checkLink(any<HttpUrl>()) } returns endpoint

        return endpoint
    }

    @Test
    fun `check sets data to true for a secure link`() {
        val endpoint = stubCheck()
        endpoint.stubSuccess(LinkCheckResponse(true, fixtureLink.toString()))

        viewModel.check(fixtureLink)

        assertTrue(viewModel.data.value == true)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `check sets data to false for an insecure link`() {
        val endpoint = stubCheck()
        endpoint.stubSuccess(LinkCheckResponse(false, fixtureLink.toString()))

        viewModel.check(fixtureLink)

        assertFalse(viewModel.data.value == true)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `check falls back to false on failure`() {
        val endpoint = stubCheck()
        endpoint.stubError()

        viewModel.check(fixtureLink)

        assertFalse(viewModel.data.value == true)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `check calls the endpoint with the given link`() {
        val endpoint = stubCheck()
        endpoint.stubSuccess(LinkCheckResponse(true, fixtureLink.toString()))

        viewModel.check(fixtureLink)

        verify { api.messenger.checkLink(fixtureLink) }
    }

    @Test
    fun `a second check disposes the previous one and uses the new link`() {
        val firstEndpoint = mockk<CheckLinkEndpoint>(relaxed = true)
        val secondLink = "https://second.example.com".toHttpUrl()
        val secondEndpoint = mockk<CheckLinkEndpoint>(relaxed = true)

        every { api.messenger.checkLink(fixtureLink) } returns firstEndpoint
        every { api.messenger.checkLink(secondLink) } returns secondEndpoint
        firstEndpoint.stubSuccess(LinkCheckResponse(true, fixtureLink.toString()))
        secondEndpoint.stubSuccess(LinkCheckResponse(false, secondLink.toString()))

        viewModel.check(fixtureLink)
        assertTrue(viewModel.data.value == true)

        viewModel.check(secondLink)
        assertFalse(viewModel.data.value == true)
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.ui.LinkCheckViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/ui/LinkCheckViewModelTest.kt
git commit -m "test: add LinkCheckViewModel unit tests"
```

---

### Task 40: Unit tests — MessengerReportViewModel

**Goal:** Verify `MessengerReportViewModel.reportSingle()` (inherited `sendReport()` flow from `ReportViewModel`) — success deletes local messages/conference for a non-group chat but keeps them for a group chat, failure sets `error`, and the "already loading" guard. This VM additionally injects `MessengerDatabase`, which `fakeAppModule()` doesn't have yet (`MessengerDao` was already added in Task 37).

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/base/FakeAppModule.kt`
- Create: `src/test/kotlin/me/proxer/app/chat/prv/message/MessengerReportViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `sendReport()` success for a non-group conference deletes its messages and the conference row (`messengerDao.deleteMessagesByConferenceId` / `deleteConferenceById`)
- [ ] `sendReport()` success for a group conference keeps local data (delete methods not called)
- [ ] `sendReport()` failure sets `error`, leaves `data` null, sets `isLoading = false`
- [ ] `sendReport()` is a no-op while `isLoading == true`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.chat.prv.message.MessengerReportViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Modify `src/test/kotlin/me/proxer/app/base/FakeAppModule.kt`**

`MessengerDao` (Task 5), `OkHttpClient` (Task 21), `TagDao` (Task 29), and `WorkManager` (Task 37) are already present — keep all four, adding only `MessengerDatabase`:

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

- [ ] **Step 2: Create `src/test/kotlin/me/proxer/app/chat/prv/message/MessengerReportViewModelTest.kt`**

```kotlin
package me.proxer.app.chat.prv.message

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.chat.prv.sync.MessengerDatabase
import me.proxer.library.ProxerApi
import me.proxer.library.api.messenger.ReportConferenceEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import org.threeten.bp.Instant

class MessengerReportViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()
    private val messengerDao: MessengerDao by inject()
    private val messengerDatabase: MessengerDatabase by inject()

    private lateinit var viewModel: MessengerReportViewModel

    private fun fixtureConference(isGroup: Boolean) = LocalConference(
        id = 5L,
        topic = "Some topic",
        customTopic = "",
        participantAmount = 2,
        image = "",
        imageType = "",
        isGroup = isGroup,
        localIsRead = true,
        isRead = true,
        date = Instant.ofEpochMilli(0L),
        unreadMessageAmount = 0,
        lastReadMessageId = "0",
        isFullyLoaded = true,
    )

    @Before
    fun setup() {
        // The mocked MessengerDatabase would not run the transaction body unless stubbed to do so.
        every { messengerDatabase.runInTransaction(any<Runnable>()) } answers { firstArg<Runnable>().run() }

        viewModel = MessengerReportViewModel()
    }

    private fun stubReport(): ReportConferenceEndpoint {
        val endpoint = mockk<ReportConferenceEndpoint>(relaxed = true)

        every { api.messenger.report(any(), any()) } returns endpoint

        return endpoint
    }

    @Test
    fun `sendReport deletes local messages and conference for a non-group chat`() {
        val endpoint = stubReport()
        endpoint.stubSuccess(Unit)
        every { messengerDao.getConference(5L) } returns fixtureConference(isGroup = false)

        viewModel.sendReport("5", "spam")

        assertEquals(Unit, viewModel.data.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)

        verify { messengerDao.deleteMessagesByConferenceId("5") }
        verify { messengerDao.deleteConferenceById("5") }
    }

    @Test
    fun `sendReport keeps local data for a group chat`() {
        val endpoint = stubReport()
        endpoint.stubSuccess(Unit)
        every { messengerDao.getConference(5L) } returns fixtureConference(isGroup = true)

        viewModel.sendReport("5", "spam")

        assertEquals(Unit, viewModel.data.value)

        verify(exactly = 0) { messengerDao.deleteMessagesByConferenceId(any()) }
        verify(exactly = 0) { messengerDao.deleteConferenceById(any()) }
    }

    @Test
    fun `sendReport sets error on failure`() {
        val endpoint = stubReport()
        endpoint.stubError()

        viewModel.sendReport("5", "spam")

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `sendReport is ignored while already loading`() {
        viewModel.isLoading.value = true

        viewModel.sendReport("5", "spam")

        verify(exactly = 0) { api.messenger.report(any(), any()) }
    }
}
```

- [ ] **Step 3: Verify**

```bash
./gradlew test --tests "me.proxer.app.chat.prv.message.MessengerReportViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/me/proxer/app/base/FakeAppModule.kt src/test/kotlin/me/proxer/app/chat/prv/message/MessengerReportViewModelTest.kt
git commit -m "test: add MessengerReportViewModel unit tests"
```

---

### Task 41: Unit tests — ChatReportViewModel

**Goal:** Verify `ChatReportViewModel.reportSingle()` (inherited `sendReport()` flow from `ReportViewModel`) — success, failure, and the "already loading" guard, using `api.chat.reportMessage(id, message)`.

**Files:**
- Create: `src/test/kotlin/me/proxer/app/chat/pub/message/ChatReportViewModelTest.kt`

**Acceptance Criteria:**
- [ ] `sendReport()` success sets `data = Unit`, clears `error`, sets `isLoading = false`
- [ ] `sendReport()` calls `api.chat.reportMessage(id, message)` with the given ids
- [ ] `sendReport()` failure sets `error`, leaves `data` null, sets `isLoading = false`
- [ ] `sendReport()` is a no-op while `isLoading == true`
- [ ] `./gradlew test` passes

**Verify:** `./gradlew test --tests "me.proxer.app.chat.pub.message.ChatReportViewModelTest"` → `BUILD SUCCESSFUL`, `0 tests failed`

**Steps:**

- [ ] **Step 1: Create `src/test/kotlin/me/proxer/app/chat/pub/message/ChatReportViewModelTest.kt`**

```kotlin
package me.proxer.app.chat.pub.message

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.proxer.app.base.RxTrampolineRule
import me.proxer.app.base.fakeAppModule
import me.proxer.library.ProxerApi
import me.proxer.library.ProxerCall
import me.proxer.library.ProxerException
import me.proxer.library.api.chat.ReportChatMessageEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class ChatReportViewModelTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val rxTrampolineRule = RxTrampolineRule()

    private val api: ProxerApi by inject()

    private lateinit var viewModel: ChatReportViewModel

    @Before
    fun setup() {
        viewModel = ChatReportViewModel()
    }

    // reportSingle() resolves to `api.chat.reportMessage(id, message).buildSingle()`, declared as
    // `Single<Optional<Unit>>`. Depending on which `buildSingle()` overload the compiler picks, the
    // underlying ProxerCall is driven via either `safeExecute()` or `execute()` — both are stubbed
    // here so the test is correct regardless of the resolved overload.
    private fun stubReportSuccess(): ReportChatMessageEndpoint {
        val call = mockk<ProxerCall<Unit?>>(relaxed = true)
        every { call.clone() } returns call
        every { call.safeExecute() } returns Unit
        every { call.execute() } returns Unit

        val endpoint = mockk<ReportChatMessageEndpoint>(relaxed = true)
        every { endpoint.build() } returns call
        every { api.chat.reportMessage(any(), any()) } returns endpoint

        return endpoint
    }

    private fun stubReportError(
        exception: ProxerException = ProxerException(ProxerException.ErrorType.IO),
    ): ReportChatMessageEndpoint {
        val call = mockk<ProxerCall<Unit?>>(relaxed = true)
        every { call.clone() } returns call
        every { call.safeExecute() } throws exception
        every { call.execute() } throws exception

        val endpoint = mockk<ReportChatMessageEndpoint>(relaxed = true)
        every { endpoint.build() } returns call
        every { api.chat.reportMessage(any(), any()) } returns endpoint

        return endpoint
    }

    @Test
    fun `sendReport sets data on success`() {
        stubReportSuccess()

        viewModel.sendReport("77", "spam")

        assertEquals(Unit, viewModel.data.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `sendReport calls reportMessage with the given ids`() {
        stubReportSuccess()

        viewModel.sendReport("77", "spam")

        verify { api.chat.reportMessage("77", "spam") }
    }

    @Test
    fun `sendReport sets error on failure`() {
        stubReportError()

        viewModel.sendReport("77", "spam")

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value == true)
    }

    @Test
    fun `sendReport is ignored while already loading`() {
        viewModel.isLoading.value = true

        viewModel.sendReport("77", "spam")

        verify(exactly = 0) { api.chat.reportMessage(any(), any()) }
    }
}
```

- [ ] **Step 2: Verify**

```bash
./gradlew test --tests "me.proxer.app.chat.pub.message.ChatReportViewModelTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/chat/pub/message/ChatReportViewModelTest.kt
git commit -m "test: add ChatReportViewModel unit tests"
```

---
