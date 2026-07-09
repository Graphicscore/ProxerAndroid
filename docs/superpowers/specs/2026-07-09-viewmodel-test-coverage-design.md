# ViewModel Unit Test Coverage — ProxerAndroid

**Date:** 2026-07-09
**Scope:** Unit tests for all 45 mobile `ViewModel` classes (Phase 1 of full Compose UI test coverage)
**Builds on:** [2026-06-13-testing-design.md](./2026-06-13-testing-design.md) — establishes the infra/patterns this spec uses (mockk, koin-test, `InstantTaskExecutorRule`, `BaseViewModelTest`, `FakeAppModule`), and already runs in CI (`unit-tests` job in `.github/workflows/ci.yml`).

---

## Context

The app finished migrating from Fragments to Compose (see `2026-07-07-compose-migration-design.md`). Test infra exists (mockk, koin-test, `ui-test-junit4`) and is proven on the `tv-support` branch (4 Compose UI test files) and for 3 mobile utility classes + `BaseViewModel` itself. Zero of the 45 mobile `ViewModel` classes have tests. Zero of the 41 mobile Compose screens have tests.

Full scope (VMs + screens) is too large for one plan. This spec covers **ViewModels only**. Compose screen UI tests are a separate, later spec.

---

## VM Families and Coverage Bar

Same coverage bar for every VM — no family gets a lighter pass. Fixtures/setup differ by family since only some share a base class.

### 1. `BaseViewModel<T>` subclasses (~16)

Pattern already proven in `BaseViewModelTest.kt`. Per VM, test:
- Success path — `data` set, `error` null
- Error path — `error` set, `data` null
- `isLoading` transitions (true during in-flight `Single`, false after completion/error)
- `reload()` clears state before refetching
- Any VM-specific transform (sort/filter params passed into the `dataSingle` call, request construction)

### 2. `PagedViewModel` / `PagedContentViewModel` subclasses (~6)

Same bar as above, plus:
- First-page load
- Next-page append (existing items retained, new page appended)
- Page-load error does not clobber already-loaded data
- End-of-data stops further page requests

### 3. Standalone VMs (~23)

No shared base (`LoginViewModel`, `LogoutViewModel`, `MessengerViewModel`, `ChatViewModel`, `ChatReportViewModel`, `CommentsViewModel`, translator/industry info VMs, etc.). No generic loading-state boilerplate applies. Each file hand-tests that VM's actual logic:
- `LoginViewModel` — credential submit success/failure, 2FA step transition, 2FA code submit success/failure
- `MessengerViewModel` — send message, receive/poll update, retry-on-failure
- Report VMs (`ChatReportViewModel`, `MessengerReportViewModel`) — submit success/failure
- Others — whatever branching the VM actually has; no test written for logic that doesn't exist

---

## Shared Infra Additions

Small, additive — not a generic test framework.

**`RxTrampolineRule`** (new `@Rule`, `src/test/kotlin/me/proxer/app/base/`) — extracts the `RxAndroidPlugins.setInitMainThreadSchedulerHandler` / `RxJavaPlugins.setIoSchedulerHandler` setup and `RxAndroidPlugins.reset()` / `RxJavaPlugins.reset()` teardown currently duplicated in `BaseViewModelTest`'s `@Before`/`@After`. Every new VM test file uses this rule instead of re-declaring the boilerplate.

**`FakeAppModule` growth** — currently provides `StorageHelper`, `PreferenceHelper`, `ProxerApi`, `RxBus`, `Validators`. As VMs needing additional Koin singletons get tested (Room DAOs like `MessengerDatabase`/`TagDatabase`, other helpers), add `mockk<T>(relaxed = true)` singles for them. Purely additive — no restructuring of the existing module or its consumers.

**File layout** — mirrors source package, same convention as existing tests:
```
src/test/kotlin/me/proxer/app/
  auth/LoginViewModelTest.kt
  auth/LogoutViewModelTest.kt
  chat/prv/message/MessengerViewModelTest.kt
  media/list/MediaListViewModelTest.kt
  ...
```

---

## Error Handling

Every VM test file covers at least one `Single.error(...)` case per network-backed load path — verifies existing `ErrorUtils`/`BaseViewModel` error-propagation contract. This is test-writing only; no production error-handling code changes.

---

## Out of Scope

- Compose screen rendering/interaction tests (Phase 2, separate spec)
- Activities (thin shells post-migration, no logic to unit test)
- TV module (`tv-support` branch, already has its own test suite)
- Instrumented/Espresso-style tests for VMs — pure JUnit is sufficient; existing `BaseViewModelTest` pattern shows no Android framework dependency is needed
- New CI jobs/config — `unit-tests` job already runs `./gradlew test` on every PR/push to `main`/`master`; new files pick this up automatically

---

## Rollout

Tracked as a checklist of 45 files (one per VM), executed as a single implementation plan since the pattern is mechanical within each family. Order: `BaseViewModel` family first (fastest, most reference value for the pattern), then `PagedViewModel` family, then standalone VMs (most bespoke, benefit from having the other two families' fixtures/`FakeAppModule` entries already in place).
