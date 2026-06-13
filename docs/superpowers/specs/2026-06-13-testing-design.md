# Testing Design — ProxerAndroid

**Date:** 2026-06-13  
**Scope:** Unit tests + Compose UI tests + CI integration  
**Strategy:** Classic testing pyramid — fast JVM unit tests at the base, instrumented Compose UI tests on top

---

## Context

Zero tests exist today. No test dependencies, no test source sets. The codebase uses MVVM + RxJava 2 + Koin 4 DI. The TV frontend is pure Compose; the rest of the app uses Fragments. Goal: catch regressions without rewriting the app.

---

## Test Scope

### Unit Tests (`src/test/kotlin/`) — pure JVM, no device

| Target | What's tested |
|---|---|
| `BBParser` | Plain text, bold/italic/url/spoiler/table/nested tags, malformed/unclosed tags |
| `UniqueQueue` | add/offer/poll/peek/remove deduplication, FIFO order, empty-queue exceptions |
| `ErrorUtils.getMessage()` | Every `ProxerException.ServerErrorType` → correct string resource ID; HTTP codes; custom exceptions |
| `ErrorUtils.handle()` | Error → `ErrorAction.buttonAction` mapping; `PartialException` data passthrough; `MangaLinkException` data |
| `BaseViewModel` load/error/reload | Loading state transitions; error set on failure; data cleared on reload; login-required reload on auth change |

### Compose UI Tests (`src/androidTest/kotlin/`) — instrumented, emulator

| Target | What's tested |
|---|---|
| `TvErrorView` | Retry button shown for generic error; Login button shown for LOGIN action; age-confirm button shown for AGE_CONFIRMATION; confirm dialog appears on button click; HIDE sentinel hides button |
| `TvSearchScreenContent` | Search field renders; results list renders with mock data; empty state renders |
| `TvMediaDetailScreenContent` | Title/subtitle render; loading state shows indicator; error state delegates to `TvErrorView` |
| `TvEpisodeScreenContent` | Episode list renders; selection state changes |

### Out of Scope (for now)

- Fragment-based screens (View system setup cost too high)
- `StreamResolver` implementations (hit real network)
- `MessengerDatabase` / Room (integration cost)
- `MainActivity` / navigation flow

---

## Tooling

### Unit test dependencies (`testImplementation`)

| Library | Purpose |
|---|---|
| `junit:junit:4.13.2` | Test runner |
| `org.jetbrains.kotlin:kotlin-test` | Kotlin assertion helpers |
| `io.mockk:mockk:1.13.12` | Kotlin-first mocking (handles `object`s, `data class`es) |
| `io.insert-koin:koin-test:4.2.1` | `KoinTest` base + `startKoin`/`stopKoin` for ViewModel tests |
| `io.insert-koin:koin-test-junit4:4.2.1` | `KoinTestRule` — auto start/stop Koin per test |
| `io.reactivex.rxjava2:rxjava` | Already in deps — use `TestObserver`/`TestScheduler` |
| `androidx.arch.core:core-testing:2.2.0` | `InstantTaskExecutorRule` — makes LiveData synchronous |

### Compose UI test dependencies (`androidTestImplementation`)

| Library | Purpose |
|---|---|
| `androidx.compose.ui:ui-test-junit4` | `createComposeRule`, `onNodeWithText`, `performClick` |
| `androidx.compose.ui:ui-test-manifest` | Required manifest merger |
| `androidx.test.ext:junit:1.2.x` | `AndroidJUnit4` runner |

### Source sets (`build.gradle`)

```groovy
sourceSets {
    test {
        java.srcDirs += "src/test/kotlin"
    }
    androidTest {
        java.srcDirs += "src/androidTest/kotlin"
    }
}
```

---

## Refactoring Plan

### `ErrorUtils` — extract `isLoggedIn` from Koin

The only Koin dependency in `ErrorUtils` is `storageHelper.isLoggedIn` inside `getMessageForProxerException`, used for `USER_INSUFFICIENT_PERMISSIONS`. Solution: add an `internal` overload that accepts `isLoggedIn: Boolean`. Public API unchanged; tests call the internal overload directly.

```kotlin
// Public (unchanged, still uses Koin):
private fun getMessageForProxerException(error: ProxerException) =
    getMessageForProxerException(error, storageHelper.isLoggedIn)

// Testable overload:
internal fun getMessageForProxerException(error: ProxerException, isLoggedIn: Boolean): Int
```

### `BaseViewModel` — Koin test module with fakes

`BaseViewModel.init` accesses `storageHelper` via `safeInject` immediately on construction (subscribes to `isLoggedInObservable`). Constructor injection is not viable without changing the base class. Instead, use `koin-test` to start Koin with a fake module before each test.

Pattern:
```kotlin
class SomeViewModelTest : KoinTest {
    @get:Rule val koinTestRule = KoinTestRule.create {
        modules(module {
            single<StorageHelper> { mockk(relaxed = true) }
            single<PreferenceHelper> { mockk(relaxed = true) }
            single<ProxerApi> { mockk(relaxed = true) }
            single<RxBus> { RxBus() }
            single<Validators> { mockk(relaxed = true) }
        })
    }
    @get:Rule val instantExecutor = InstantTaskExecutorRule()

    private val storageHelper: StorageHelper by inject()
    private lateinit var viewModel: SomeViewModel

    @Before fun setup() {
        every { storageHelper.isLoggedInObservable } returns Observable.never()
        viewModel = SomeViewModel()
    }
}
```

No production code changes required.

---

## Test Patterns

### ViewModel test base

```kotlin
abstract class BaseViewModelTest : KoinTest {
    @get:Rule val koinTestRule = KoinTestRule.create { modules(fakeAppModule()) }
    @get:Rule val instantExecutor = InstantTaskExecutorRule()

    @Before fun setupSchedulers() {
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
    }

    @After fun teardownSchedulers() {
        RxAndroidPlugins.reset()
    }
}
```

`RxAndroidPlugins` override avoids "Looper not prepared" errors on JVM. `fakeAppModule()` is a shared test helper that provides MockK fakes for all Koin singletons.

### Compose UI test base

```kotlin
@RunWith(AndroidJUnit4::class)
class TvErrorViewTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test fun showsRetryButtonForGenericError() {
        composeTestRule.setContent {
            TvErrorView(
                error = ErrorUtils.ErrorAction(message = R.string.error_unknown),
                onRetryClick = {}
            )
        }
        val retryLabel = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.error_action_retry)
        composeTestRule.onNodeWithText(retryLabel).assertIsDisplayed()
    }
}
```

---

## CI Integration

Two new jobs added to `.github/workflows/ci.yml`, both depending on `build` succeeding:

### `unit-tests` job

- Runs on `ubuntu-latest`
- `./gradlew test`
- Uploads `build/reports/tests` on failure
- Blocking (no `continue-on-error`)
- ~3-4 min

### `instrumented-tests` job

- Runs on `ubuntu-latest` with KVM enabled
- Uses `reactivecircus/android-emulator-runner@v2` (api-level 30, x86_64)
- `./gradlew connectedDebugAndroidTest`
- Starts as `continue-on-error: true` — harden to blocking once suite is stable
- ~15-20 min

Both jobs share the same secrets fallback pattern as existing jobs.

---

## File Layout

```
src/
  test/kotlin/me/proxer/app/
    util/
      ErrorUtilsTest.kt
    ui/view/bbcode/
      BBParserTest.kt
    util/data/
      UniqueQueueTest.kt
    base/
      BaseViewModelTest.kt        ← abstract helper
      ConcreteViewModelTest.kt    ← tests load/error/reload via fake subclass
  androidTest/kotlin/me/proxer/app/
    tv/
      TvErrorViewTest.kt
      TvSearchScreenTest.kt
      TvMediaDetailScreenTest.kt
      TvEpisodeScreenTest.kt
```
