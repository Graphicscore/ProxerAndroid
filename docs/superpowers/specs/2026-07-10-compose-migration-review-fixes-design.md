# Compose Migration Review Fixes — Design

## Context

A comprehensive multi-agent PR review of the `compose-migration` branch (327 files, ~32k lines, full Fragment/View → Jetpack Compose rewrite) turned up a set of critical and important regressions and gaps, plus two explicit follow-ups from the branch owner (CI should be test-only, `InAppUpdateFlow` should still work). This spec covers the Critical + Important findings, scoped per owner decisions below. Suggestions/minor nits and the pre-existing Strengths from the review are out of scope.

Findings not covered by this spec (deferred): LazyColumn item keys, `snapshotFlow` pagination pattern, `BackPressAware`/dead-code cleanup, launch-counter rotation guard, test-infra style duplication (`NotificationViewModelTest` mock helpers, `ProfileMediaListViewModelTest` nullable-stub style), and the Industry/TranslatorGroup `runCatching` link-opening bypass.

## Goals

- Fix every Critical finding from the review.
- Fix the Important findings the owner selected to include.
- CI runs tests only — no `assembleDebug`/`assembleRelease`/build job.
- `InAppUpdateFlow` works again under the Compose-only Activity.
- Login-gate behavior (`isLoginRequired` / `NotLoggedInException`) is provably testable and tested for a representative slice of ViewModels.
- Bookmark swipe-delete/undo and comment deletion are restored with full parity to the pre-migration UX, adapted to Compose idioms.

## Non-goals

- Restoring or fixing anything in the Suggestions/minor-nit tier from the review.
- The Industry/TranslatorGroup `runCatching` link-opening bypass (deferred to a later pass).
- Achieving 100% login-gate test coverage across all ~38 ViewModel test files (scoped to infra fix + ~4 representative VMs).
- Any new architectural pattern (event bus, CompositionLocal) beyond what's already idiomatic in this codebase.

## A. Error-action architecture (Critical)

**Problem:** `ui/compose/ContentScreen.kt`'s error-state `Button` is hardcoded to `onClick = onRetry`. It reads `error.buttonMessage` for the label but completely ignores `error.buttonAction`. `ErrorUtils.ErrorAction.toClickListener(activity: BaseActivity)` still exists and correctly maps `CAPTCHA`/`NETWORK_SETTINGS`/`AGE_CONFIRMATION`/`OPEN_LINK` to real recovery actions, but has **zero live callers** anywhere in the app (verified via repo-wide grep) — only the two RemoteViews widget workers call the separate `toIntent()` helper. `AgeConfirmationDialog` is still a legacy `BaseDialog`/`com.afollestad.materialdialogs.MaterialDialog` Fragment class, duplicated by an inline `AlertDialog` composable already living in `SettingsScreen.kt`. Concretely: every error screen's recovery button (~30 screens use `ContentScreen`) just silently retries the identical failing request forever — age-confirmation, login, captcha, network-settings, and manga-link recovery are all unreachable from the error UI.

**Approach (chosen: self-contained in `ContentScreen`):**

1. Migrate `AgeConfirmationDialog` from `settings/AgeConfirmationDialog.kt` (`BaseDialog` + MaterialDialog) to a Compose composable `@Composable fun AgeConfirmationDialog(onDismiss: () -> Unit)` in the same package, mirroring `LoginDialog`/`LogoutDialog`'s shape (`AlertDialog`, confirm sets `preferenceHelper.isAgeRestrictedMediaAllowed = true`, dismiss/cancel just closes). Extract the logic from `SettingsScreen.kt`'s existing inline `AlertDialog` (lines ~134-153) into this shared composable; `SettingsScreen` calls the shared one instead of duplicating it. Delete the old Fragment class. If `com.afollestad.materialdialogs` has no other remaining callers after this, drop the dependency too (verify during implementation).
2. Trim `ErrorUtils.ErrorAction.toClickListener(activity: BaseActivity)` to only the three cases that are pure Activity-level side effects with no Compose state involved: `CAPTCHA`, `NETWORK_SETTINGS`, `OPEN_LINK`. Change its return type from `View.OnClickListener?` to `(() -> Unit)?` (safe — zero live callers today, and a plain lambda is the natural shape for a Compose `onClick`). Remove the `AGE_CONFIRMATION` branch (moves to step 3) and do not reintroduce a `LOGIN` branch (also moves to step 3 — there is no `LoginDialog.show(activity)` static entry point anymore, `LoginDialog` is a composable driven by local `remember` state).
3. `ContentScreen`: add `var showLoginDialog by remember { mutableStateOf(false) }` and `var showAgeConfirmationDialog by remember { mutableStateOf(false) }`. The error button's `onClick` dispatches on `error.buttonAction`:
   - `LOGIN` → `showLoginDialog = true`
   - `AGE_CONFIRMATION` → `showAgeConfirmationDialog = true`
   - `CAPTCHA` / `NETWORK_SETTINGS` / `OPEN_LINK` → `(LocalContext.current as? BaseActivity)?.let { error.toClickListener(it)?.invoke() }`
   - `null` / `BOOKMARK` / anything else → `onRetry()` (existing behavior, unchanged default)
   Render `if (showLoginDialog) LoginDialog(onDismiss = { showLoginDialog = false })` and the age-confirmation equivalent inside `ContentScreen`'s composable body.
4. No changes required at any of the ~30 `ContentScreen(...)` call sites — `onRetry` keeps its existing signature and meaning.
5. Reload-after-recovery: unchanged from current reactive behavior. `AgeConfirmationDialog` confirm sets `preferenceHelper.isAgeRestrictedMediaAllowed`, which the owning ViewModel already reactively observes and auto-reloads on (per existing `AnimeViewModel`/`MediaInfoViewModel` behavior, documented in `CLAUDE.md`). `LoginDialog`'s successful login flips `isLoggedInObservable`, which is not subject to the cold-start `.skip(1)` caveat in this mid-session case, so the owning screen's reactive login-state handling already reloads correctly.

## B. Unobserved error/status LiveData (Critical + Important)

**Problem:** Several ViewModels correctly set an error/status `LiveData` on failure, but the migrated Compose screen never observes it, so the failure is silent:

- `ChatViewModel.sendMessageError` (`chat/pub/message/ChatViewModel.kt:264`) — not observed in `ChatScreen.kt`. On send failure, the optimistically-added message is silently stripped from the visible chat log with no explanation.
- Chat message report flow (`ReportViewModel`/`ChatReportViewModel` `error`/`data`/`isLoading`) — `ChatScreen.kt`'s inline report dialog clears its local state and closes unconditionally right after calling `sendReport()`, regardless of outcome.
- `TopTenViewModel.itemDeletionError` (`profile/topten/TopTenViewModel.kt:127`) — not observed in `TopTenScreen.kt`.
- `ProfileMediaListViewModel.itemDeletionError` (`profile/media/ProfileMediaListViewModel.kt:114`) — not observed in `ProfileMediaListScreen.kt`.

**Approach:** Standardize on the pattern already used correctly elsewhere in this same branch (`EpisodeScreen.kt` for `bookmarkError`, `MediaInfoScreen.kt` for `userInfoUpdateError`, `NotificationScreen.kt`):

```kotlin
val someError by viewModel.someError.observeAsState()
LaunchedEffect(someError) {
    someError?.let { snackbarHostState.showSnackbar(context.getString(it.message)) }
}
```

Applied to `ChatScreen.kt` (`sendMessageError`), `TopTenScreen.kt` and `ProfileMediaListScreen.kt` (`itemDeletionError`). For the report dialog specifically: observe `reportViewModel.error`/`.data`/`.isLoading`; only clear `reportTarget`/dismiss the dialog when `data` becomes non-null (confirmed success), show the error inline (mirroring `LoginDialog`'s inline error `Text`) on `error`, and disable the confirm button / show a progress indicator while `isLoading == true`.

## C. Deep-link tab restoration (Important)

**Problem:** `MediaScreen.kt`/`ProfileScreen.kt` both start `rememberPagerState { tabs.size }` at page 0 unconditionally. The pre-migration `MediaActivity`/`ProfileActivity` parsed `intent.data.pathSegments[2]` (`customItemToDisplay`) to open the right tab for deep links like `proxer.me/info/{id}/comments` or `proxer.me/user/{id}/anime`. The new `MediaActivity`/`ProfileActivity` only pass `id`/`name`/`userId`/`username` through — the sub-section is dropped.

**Approach:** Port `customItemToDisplay` as an `initialTab: Int` computed property on each Activity (same `pathSegments.getOrNull(2)` mapping: Media → comments=1, episodes=2, relations=3, recommendations=4, discussions=5; Profile → about=1, anime=3, manga=4, history=5; else 0), pass it into `MediaScreen`/`ProfileScreen` as a parameter, and use it as `rememberPagerState(initialPage = initialTab) { tabs.size }`.

## D. CI: test-only, fix broken job graph (Critical + owner directive)

**Problem:** `.github/workflows/ci.yml:88` — the `instrumented-tests` job declares `needs: build`, but the `build` job (which ran `assembleDebug`/`assembleRelease`) was already removed from the workflow. GitHub Actions rejects the entire workflow file when a `needs:` target doesn't exist — `analysis` and `unit-tests` don't run either, not just `instrumented-tests`.

**Approach:** Delete the `needs: build` line. `connectedDebugAndroidTest` (what `instrumented-tests` runs) assembles its own debug + androidTest APKs, so no separate build job is required. Per the owner's directive, no `build`/`assemble` job is reintroduced — CI stays three jobs, all test/analysis-only: `analysis` (detekt), `unit-tests` (`./gradlew test`), `instrumented-tests` (`./gradlew connectedDebugAndroidTest`).

## E. `InAppUpdateFlow` restoration (owner directive)

**Problem:** `util/InAppUpdateFlow.kt` is unreferenced by any Activity post-migration. Its `start(context: Activity, rootView: ViewGroup)` signature depends on a `ViewGroup` to construct a View-based `com.google.android.material.snackbar.Snackbar` — the Compose-only `MainActivity` has no root `ViewGroup` to hand it. `BaseActivity` already has a deprecated (`DeprecationLevel.ERROR`) `snackbar()` stub whose message says exactly this: "Compose Activities have no root view. Use SnackbarHostState in the composable instead."

**Approach:**

1. Refactor `InAppUpdateFlow.start()` to drop the `ViewGroup` parameter in favor of callbacks: `start(activity: Activity, onUpdateAvailable: (download: () -> Unit) -> Unit, onUpdateReady: (install: () -> Unit) -> Unit, onCanceled: () -> Unit)`. The Play Core `successListener`/`progressListener` internals stay the same, they just invoke the callback instead of building a `Snackbar` directly.
2. `MainScreen.kt` currently has no `Scaffold`/`SnackbarHostState` — add a `SnackbarHostState` (via a minimal wrapper that doesn't disrupt the existing `ModalNavigationDrawer` layout; exact placement — `Scaffold` vs. a `Box` + `SnackbarHost` overlay — decided during implementation by reading `MainScreen.kt`'s current root composable structure). Wire a `LaunchedEffect(Unit)` that calls `inAppUpdateFlow.start(...)`, with the callbacks calling `snackbarHostState.showSnackbar(message, actionLabel = ..., duration = SnackbarDuration.Indefinite)` and branching on `SnackbarResult.ActionPerformed` to trigger `appUpdateManager.startUpdateFlowForResult(...)` / `completeUpdate()`.
3. `MainActivity` re-gains an `InAppUpdateFlow` instance (constructed in `onCreate`/as a property) and calls `.stop()` from `onDestroy()`.
4. The existing `@Suppress("DEPRECATION")` on `startUpdateFlowForResult` stays as-is — modernizing to the Activity Result API is out of scope for this restoration.

## F. Login-gate testability (Critical, scoped)

**Problem:** `test/.../base/FakeAppModule.kt` binds `Validators` as `mockk(relaxed = true)`. `BaseViewModel.validate()` calls `validators.validateLogin()`, which on a real `Validators` throws `NotLoggedInException` when logged out — but on the relaxed mock this is a permanent no-op regardless of `storageHelper.isLoggedIn`. ~38 of the 40 ViewModels default `isLoginRequired = true`, and none of the 40 test files currently exercises "logged-out user hits a login-gated screen." `BaseViewModelTest.kt`'s own `TestViewModel` doesn't route `validate()` through `dataSingle` either, so even the base-class contract isn't verified.

**Approach (scoped to infra + representative coverage, per owner decision):**

1. `FakeAppModule.kt`: replace the fully-relaxed `Validators` mock with one that has explicit default stubs (`every { validateLogin() } just Runs`, `every { validateAgeConfirmation() } just Runs`, or equivalent), so tests can override per-case with `every { validateLogin() } throws NotLoggedInException()` without relaxed-mock magic silently absorbing the call.
2. `BaseViewModelTest.kt`: extend `TestViewModel` (or add a sibling test case) so `validate()` is actually part of the tested `dataSingle` chain, and add one test proving a `NotLoggedInException` from `validators.validateLogin()` surfaces as the expected login-required `ErrorAction`.
3. Add the same login-required assertion to 4 representative ViewModel test files, chosen to span the different `BaseViewModel` usage shapes already present in the suite: `AnimeViewModelTest` (content-loading), `BookmarkViewModelTest` (paged), `ChatViewModelTest` (messaging), `ProfileSettingsViewModelTest` (standalone/settings-style VM).

## G. Test gaps (Important, same effort as F)

1. `MediaInfoViewModelTest.kt`: add age-restriction tests mirroring the pattern already correct in `AnimeViewModelTest`/`MangaViewModelTest` — not-logged-in + age-restricted entry → `NotLoggedInException`; logged-in + not-confirmed + age-restricted entry → `AgeConfirmationRequiredException`.
2. `NotificationViewModelTest.kt`: the existing `` `deleteAll clears data on success and sets deletionError on failure` `` test (line 217) only asserts the success half. Split into two tests or add the missing failure stub + assertion for `deletionError`.

## H. Bookmark undo + comment deletion restoration (Important, full UX)

**Problem:** `BookmarkViewModel.itemDeletionError`/`undoError`/`undoData` and its `addItemToDelete()`/`undo()` functions, plus `CommentsViewModel`/`ProfileCommentViewModel`'s `itemDeletionError`/`deleteComment()`, are all still present in the ViewModels but never invoked from `BookmarkScreen.kt`/`CommentsScreen.kt`/`ProfileCommentScreen.kt`. The pre-migration swipe-to-delete-with-undo (bookmarks) and CAB-based delete (comments) UX appears to not have been ported to Compose at all.

**Approach:**

1. `BookmarkScreen.kt`: wrap each `LazyColumn` item in a `SwipeToDismissBox`, triggering `viewModel.addItemToDelete(item)` on swipe. Observe `viewModel.undoData` to show a `Snackbar` with an "Undo" action wired to `viewModel.undo()`, matching the old Fragment's swipe+undo flow. Also wire `itemDeletionError` per section B's pattern.
2. `CommentsScreen.kt` / `ProfileCommentScreen.kt`: restore a delete action calling `viewModel.deleteComment(comment)`. The exact interaction affordance (the old Fragments used a CAB/multi-select `ItemTouchHelper` combo) is an open implementation detail — the plan will read the old `CommentsFragment`/`ProfileCommentFragment` source in full before deciding between a per-item delete icon, long-press selection mode, or swipe, to match user-facing behavior as closely as practical in Compose. Wire `itemDeletionError` per section B's pattern regardless of which affordance is chosen.

## Testing Plan

- **Unit tests**: sections F and G above (login-gate infra + 4 representative VMs, MediaInfo age-restriction tests, Notification `deleteAll` failure test).
- **Manual verification** (this pass is UI-heavy; several fixes have no meaningful unit-test surface):
  - Each `ErrorAction.buttonAction` case fires the right recovery UI: log out and hit a login-gated screen (LOGIN), view age-restricted content (AGE_CONFIRMATION), force a network-settings error, force a captcha/IP-blocked error, trigger a manga open-link error.
  - Chat send-failure shows a snackbar and doesn't silently drop the message; report-dialog failure keeps the dialog open with an error shown.
  - TopTen/ProfileMediaList delete-failure shows a snackbar.
  - Media/Profile deep links (`proxer.me/info/{id}/comments`, `proxer.me/user/{id}/anime`, etc.) land on the correct tab.
  - Bookmark swipe+undo and comment deletion work end-to-end.
  - In-app-update snackbar appears when an update is available (code-review only if no real Play Store update is available to trigger it in a test environment).
- **CI**: confirm the workflow YAML's job graph resolves (no dangling `needs:`) after the fix — e.g. a local YAML/job-graph sanity check, since `gh` isn't available in this environment to watch a live run.

## Assumptions / Open Items Carried Into the Plan

- `MainScreen.kt`'s exact root composable structure (for placing the new `SnackbarHostState` without disrupting `ModalNavigationDrawer`) will be re-read at plan/implementation time.
- Whether `com.afollestad.materialdialogs` can be fully removed as a dependency after `AgeConfirmationDialog`'s migration depends on whether anything else in the codebase still references it — to be verified during implementation.
- The exact interaction affordance for comment deletion (icon vs. long-press vs. swipe) is deferred to plan time, pending a full read of the old `CommentsFragment`/`ProfileCommentFragment` CAB logic.
- Old `MainActivity`'s `onActivityResult` did not appear to branch on `InAppUpdateFlow.REQUEST_CODE` (the flexible-update flow completes via `InstallStateUpdatedListener`, not activity result) — assumed unnecessary to add in the restored version; to be confirmed during implementation.
