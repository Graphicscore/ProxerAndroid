# Instrumented UI Test Infra Design — ProxerAndroid

**Date:** 2026-07-15
**Scope:** Infra sub-project (first of several). Builds the shared harness for full-integration instrumented UI tests and proves it on one screen. Per-package test coverage (auth, anime, chat, manga, media, news, profile, settings, etc.) is out of scope here — each gets its own follow-up spec once this harness is approved and working.
**Supersedes:** the instrumented-test portion of `2026-06-13-testing-design.md`, which assumed Fragment-based screens were permanent and explicitly out of scope ("View system setup cost too high"). The app has since fully migrated to Compose (see `2026-07-14-aboutlibraries-compose-migration-design.md`) — there are no Fragment-based content screens left on `master`, only `DialogFragment`s. `CLAUDE.md`'s references to `BaseContentFragment` are stale as of this migration and should be corrected separately.

---

## Context

Instrumented tests exist today (`src/androidTest/kotlin/me/proxer/app/tv/`) but only cover the TV frontend, and only at the "stateless content composable" level: each test calls `composeTestRule.setContent { TvSearchScreenContent(...) }` with hand-fed parameters. No Activity is launched, no ViewModel runs, no Koin DI is involved, no network layer is touched. This is fast and simple, but it can't catch DI wiring bugs, navigation bugs, or ViewModel-to-UI wiring bugs — exactly the class of regression most likely when 15+ feature packages get touched independently.

Every screen in the app is now `Activity.setContent { Screen() }` (confirmed via full-repo search — zero non-dialog `Fragment` subclasses remain). This means the same harness pattern (`ActivityScenario` + Compose test rule + real Koin DI with mocked dependencies) can cover every screen in the app uniformly — no Espresso, no `FragmentScenario`, no dual test pattern needed.

CI already runs `connectedDebugAndroidTest` on an x86_64 emulator (api-level 30) via `reactivecircus/android-emulator-runner@v2` in `.github/workflows/ci.yml`. No CI changes are needed for this sub-project — new tests under `src/androidTest/` are picked up automatically by the existing Gradle task.

---

## Goals

- A reusable harness so that per-screen instrumented tests can launch a real `Activity`, with real Koin DI wired to mocked dependencies (`ProxerApi`, `StorageHelper`, etc.), and real `ViewModel`s — catching wiring bugs the current stateless-content pattern misses.
- Shared login-state fixtures, since most `BaseViewModel`s gate on `isLoginRequired = true` by default.
- Zero risk of a test hitting the real Proxer API or real encrypted storage.
- One worked example (`NotificationScreen`) proving the full path: login-gated screen, real `ProxerApi` call (mocked), pagination, error/retry states.

## Non-goals

- Test coverage for any other screen/package — follow-up specs, one per package or logical group.
- Screenshot/visual regression testing.
- Changing CI configuration (already sufficient).
- Migrating or removing the existing TV stateless-content tests — they remain valid for cheap, ViewModel-independent rendering checks and are not superseded by this harness (different purpose: fast prop-driven checks vs. full wiring checks).

---

## Architecture

```
androidTest/kotlin/me/proxer/app/
  base/
    FakeAppModule.kt       — Koin module: mockk(relaxed = true) for ProxerApi, StorageHelper,
                              PreferenceHelper, OkHttpClient, WorkManager, MessengerDatabase, etc.
                              Mirrors src/test/kotlin/me/proxer/app/base/FakeAppModule.kt.
    LoginFixtures.kt       — loggedInStorageHelper(user = fakeUser()), loggedOutStorageHelper()
    TestApplication.kt     — Application subclass; startKoin { modules(fakeAppModule()) }
    TestRunner.kt          — AndroidJUnitRunner override; newApplication() returns TestApplication
  notification/
    NotificationScreenTest.kt   — POC: 4 cases (success, error, retry, pagination)
```

**DI override — custom test runner + Application.** `MainApplication.onCreate()` calls `startKoin{}` with real modules before any test code runs, so the swap has to happen at `Application` construction, not after. `build.gradle`'s `defaultConfig.testInstrumentationRunner` is repointed to `me.proxer.app.base.TestRunner` for the `androidTest` source set only (`debug`/`release` builds untouched — this only affects `connectedDebugAndroidTest`). `TestRunner` overrides `newApplication()` to instantiate `TestApplication` instead of `MainApplication`, so real network/storage initialization code never executes during tests.

**Per-test flow:**
1. `TestApplication` boots, Koin starts with `fakeAppModule()`.
2. Test `@Before` configures endpoint mocks (`every { api.notificationEndpoint()... } returns ...`, matching the concrete-subtype-mocking pattern already established in JVM tests) and a login fixture (`loggedInStorageHelper()` or `loggedOutStorageHelper()`), then rebinds them into the running Koin instance for the test (`loadKoinModules(..., allowOverride = true)` scoped per-test, `unloadKoinModules` in `@After` to prevent bleed between tests).
3. `composeTestRule = createAndroidComposeRule<NotificationActivity>()` launches the real Activity.
4. Assertions via `onNodeWithText` / `onNodeWithTag`, matching the existing TV test style.

**Why not runtime-override without a custom Application:** rejected during design — real `MainApplication.onCreate()` would still execute once before any override could apply (real network client construction, real encrypted-prefs init), which is unnecessary risk and slower. The custom-runner approach keeps real init code from ever running in the androidTest process.

---

## Test Patterns (POC: `NotificationScreen`)

`NotificationActivity` → `NotificationViewModel : PagedViewModel<ProxerNotification>()` (login-required, calls `ProxerApi` for paged results) → `NotificationScreen` composable. Chosen over `ServerStatusScreen` (simpler, but scrapes HTML via a raw `OkHttpClient` call rather than `ProxerApi`, so it wouldn't exercise the API-mocking path this harness is built around) because it's representative of the majority pattern across the app: paged list + login gate + real API call.

Four cases in `NotificationScreenTest.kt`:

1. **Success/pagination** — mock page 1 results, assert items rendered; scroll to trigger page 2 request (mock returns page 2), assert appended items.
2. **Error state** — mock endpoint to throw, assert `ErrorUtils`-mapped message text is shown (`onNodeWithText(context.getString(R.string.error_...))`), matching the existing `TvSearchScreenTest` error-state assertion style.
3. **Retry** — first call errors, second succeeds; tap retry; assert content replaces error.
4. **Login gate** — launch with `loggedOutStorageHelper()`; assert login-prompt state instead of content. Per the `isLoggedInObservable.skip(1)` gotcha (see `CLAUDE.md`), the fixture sets state *before* Activity launch, so the screen's `LaunchedEffect(Unit) { viewModel.load() }` — not the reactive path — is what surfaces the gate. This matches real cold-start behavior for an already-logged-out user and is worth asserting explicitly since it's a documented footgun.

---

## Error Handling

No new error handling in production code — this is test-only infra. The one existing footgun this harness must respect: `BaseViewModel` has no auto-load (`BaseContentFragment.onViewCreated` doesn't exist anymore since Fragments are gone, but the equivalent — `LaunchedEffect(Unit) { viewModel.load() }` — must be present in every screen composable under test, or tests will hang waiting for content that's never requested). If a screen is missing this call, that's a real bug the test should catch, not something the harness should paper over.

---

## Testing

- `NotificationScreenTest.kt` runs via the existing `connectedDebugAndroidTest` Gradle task, already wired into CI.
- Manual verification: run `./gradlew connectedDebugAndroidTest --tests "*NotificationScreenTest*"` locally against a connected emulator/device before considering the sub-project done.
- Confirm the existing TV tests (`TvSearchScreenTest` etc.) still pass unaffected by the new `TestRunner`/`TestApplication` — they don't use Koin today, so they should be unaffected, but this needs verifying since the test `Application` now changes for the whole `androidTest` variant, not just new tests.

---

## Follow-up (out of scope here)

Once this infra lands and the POC passes review, each feature package (auth, anime, chat, manga, media, news, profile, settings, forum, bookmark, notification-remaining-screens, profile-settings, comment, info) gets its own brainstorming → spec → plan cycle reusing this harness. Suggested grouping/priority order to be decided when we get there, not now.
