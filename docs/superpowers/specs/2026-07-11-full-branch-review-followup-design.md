# Full-Branch Review Follow-Up — Design

## Context

After merging the `compose-migration-review-fixes` pass, a fresh full-branch PR review (`compose-migration` vs `master`, 4 parallel specialized agents: Compose UI, infra/build, silent-failure hunt, test coverage) found that the review-fixes pass's own patterns hold up correctly under independent re-review, but surfaced further real issues — some are the *same bug class* the review-fixes pass fixed in 6 screens, just not yet applied to the rest of the branch; others are new findings the original PR review missed entirely.

This spec covers the full set of Critical + Important + Suggestions findings from that follow-up review, per owner decision to include everything.

## Goals

- Fix both Critical findings: unobserved `refreshError` across 8 screens, and `MessengerScreen`'s report-handling silently discarding failures.
- Fix all three Important findings: the one-shot-event bug in 9 more screens, Industry/TranslatorGroup link-opening silently swallowing failures, and the `deleteComment` failure-path test gap.
- Extract a shared `ObserveLiveDataEvent` composable so the one-shot-event fix pattern (proven correct in 6 screens already) stops being hand-copied, and retrofit the 6 existing screens onto it for consistency.
- Address all named Suggestions: `ResettingMutableLiveData` direct test coverage, detekt unused-import cleanup, assertion-strength consistency in two test files, `MainActivity` launch-counter guard, `RatingDialog` dead-code removal, CI checkout-action version consistency.

## Non-goals

- No further whole-codebase audit beyond what the 4 review agents already found — this spec fixes named findings, it doesn't re-run the hunt.
- No redesign of `ResettingMutableLiveData` itself (e.g. switching to `Channel`/`SharedFlow`) — the existing class's behavior is correct and proven; only its Compose consumption pattern was wrong.
- No new features — every item here is a fix to an existing, already-implemented capability that isn't correctly surfaced to the user, or a test/cleanup gap.

## A. Shared `ObserveLiveDataEvent` helper

**Problem:** The one-shot-event bug (`ResettingMutableLiveData` consumed via `observeAsState()` + `LaunchedEffect(value)`, which silently drops the second of two structurally-equal events because Compose's default state-equality policy skips recomposition) was fixed in 6 screens during the prior pass by hand-writing a `DisposableEffect` + raw `Observer` block per field, each ~15 lines with a repeated explanatory comment. This pass fixes the same bug in 9 more screens (~13 more fields) plus 8 `refreshError` fields — continuing to hand-copy the block would mean ~30 near-identical blocks across the codebase, with room for drift.

**Approach:** New file `src/main/kotlin/me/proxer/app/ui/compose/ObserveLiveDataEvent.kt`:

```kotlin
package me.proxer.app.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

/**
 * Observes a one-shot LiveData event (e.g. a ResettingMutableLiveData error/success signal)
 * via a raw Observer, bypassing Compose's observeAsState()/mutableStateOf structural-equality
 * state diffing. observeAsState() would silently drop the second of two structurally-equal
 * events (Unit == Unit is always true; two identical ErrorActions from repeated identical
 * failures) because Compose skips recomposition when a "new" state value equals the current
 * one - a raw Observer fires once per genuine LiveData delivery regardless.
 */
@Composable
fun <T> ObserveLiveDataEvent(liveData: LiveData<T>, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, liveData) {
        val observer = Observer<T> { value -> if (value != null) onEvent(value) }
        liveData.observe(lifecycleOwner, observer)
        onDispose { liveData.removeObserver(observer) }
    }
}
```

The `value != null` check compiles and behaves correctly regardless of whether the ViewModel declared the field as `ResettingMutableLiveData<X?>` (the common case) or `ResettingMutableLiveData<X>` with no `?` (`ProfileSettingsViewModel.error`/`.updateError`) — `LiveData`'s Kotlin binding uses platform types since it's a Java class, and `ResettingMutableLiveData.observe()`'s own override already relies on this exact null-check idiom internally for both cases today, so this is a proven idiom in this codebase, not a new risk.

Every call site collapses to one line, e.g.:
```kotlin
ObserveLiveDataEvent(viewModel.itemDeletionError) { err ->
    scope.launch {
        snackbarHostState.showSnackbar(context.getString(R.string.error_bookmark_deletion, context.getString(err.message)))
    }
}
```

## B. Retrofit the 6 already-fixed screens

Replace each hand-written `DisposableEffect`+`Observer` block with a call to `ObserveLiveDataEvent`, in: `ChatScreen.kt` (`sendMessageError`, `reportData`), `TopTenScreen.kt` (`itemDeletionError`), `ProfileMediaListScreen.kt` (`itemDeletionError`), `BookmarkScreen.kt` (`itemDeletionError`, `undoData`, `undoError`), `CommentsScreen.kt` (`itemDeletionError`), `ProfileCommentScreen.kt` (`itemDeletionError`). Pure mechanical swap — same side-effect bodies, same `lifecycleOwner`/`scope` usage, verified by the existing covering ViewModel tests + `compileDebugKotlin`.

## C. `refreshError` wiring (Critical)

**Problem:** `PagedViewModel.refreshError` (`base/PagedViewModel.kt:14`, `ResettingMutableLiveData<ErrorUtils.ErrorAction?>`) is set on refresh failure by 8 ViewModels — `MessengerViewModel`, `ChatViewModel`, `TopicViewModel`, `CommentsViewModel`, `NotificationViewModel`, `ProfileCommentViewModel`, `HistoryViewModel`, `ProfileMediaListViewModel` — but no Compose screen observes it. The deleted `PagedContentFragment` used to show a snackbar (`error_refresh` string, already exists, one `%s` placeholder) on pull-to-refresh failure; the Compose screens show nothing.

**Approach:** Add `ObserveLiveDataEvent(viewModel.refreshError) { err -> scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_refresh, context.getString(err.message))) } }` to each of the 8 screens: `MessengerScreen.kt`, `ChatScreen.kt`, `TopicScreen.kt`, `CommentsScreen.kt`, `NotificationScreen.kt`, `ProfileCommentScreen.kt`, `HistoryScreen.kt`, `ProfileMediaListScreen.kt`. Several of these screens are also touched by section B (retrofit) or D (9-screen fix) — the implementation plan will land those as one combined edit per file where they overlap, not separate commits touching the same lines twice.

## D. One-shot-event bug in 9 more screens (Important)

Same `ObserveLiveDataEvent` fix, applied to:
- `AnimeScreen.kt` — `resolutionResult`, `resolutionError`, `userStateData`, `userStateError`
- `MangaScreen.kt` — `userStateData`/`userStateError`
- `MediaInfoScreen.kt` — `userInfoUpdateData`/`userInfoUpdateError` (renamed from the review's shorthand `updateResult`/`updateError`)
- `EditCommentScreen.kt` — `publishResult`, `publishError`
- `NotificationScreen.kt` — `deletionError` (also touched by section C for `refreshError` — one combined edit)
- `CreateConferenceScreen.kt` — `result`, `error`
- `ProfileSettingsScreen.kt` — `error`, `updateError`
- `EpisodeScreen.kt` — `bookmarkData`/`bookmarkResult`, `bookmarkError`
- `MessengerScreen.kt` — `draft` (also touched by sections C and E — one combined edit)

Each field's exact current variable names and the surrounding `LaunchedEffect` body will be confirmed by reading the live file at plan-writing time (some of the review's field names are shorthand/approximate, e.g. `MediaInfoScreen`'s fields are actually `userInfoUpdateData`/`userInfoUpdateError` per the original review-fixes pass's own work in that file).

## E. `MessengerScreen` full report-handling fix (Critical)

**Problem:** `MessengerScreen.kt`'s report dialog confirm button calls `reportViewModel.sendReport(...)` then immediately clears `showReportDialog`/`reportReason`/`selectedIds`, regardless of whether the request succeeds. `MessengerReportViewModel`/`ReportViewModel`'s `data`/`error`/`isLoading` are never observed. Confirmed structurally near-identical to pre-fix `ChatScreen.kt` (same `Scaffold`/`AlertDialog`/`selectedIds` shape) — this is the exact same bug already fixed there.

**Approach:** Mirror `ChatScreen.kt`'s current (fixed) structure exactly: observe `reportViewModel.error`/`.isLoading` via `observeAsState()` for inline dialog display (disable confirm + text field while loading, show inline error text) — these are plain `MutableLiveData`, not one-shot, so `observeAsState()` is correct for them. Only clear `reportTarget`/`reportReason`/`selectedIds` via `ObserveLiveDataEvent(reportViewModel.data) { ... }` (confirmed success), not unconditionally on button click.

## F. Industry/TranslatorGroup link-opening (Important)

**Problem:** `IndustryScreen.kt:276-278` and `TranslatorGroupScreen.kt:257-259` use `runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkText))) }` with the `Result` fully discarded — a malformed URI or missing handler app produces a silent no-op tap with zero feedback.

**Approach:** Replace with `try { context.startActivity(...) } catch (_: ActivityNotFoundException) { context.toast(R.string.<existing or new string>) }`, matching the pattern already used in `AboutScreen.kt` for the identical failure mode. Exact string resource (reuse `about_error_mail_no_activity` or add a dedicated one) decided at plan-writing time after checking whether the existing string's wording fits an external-link context or needs a new key.

## G. `deleteComment` failure-path test coverage (Important)

**Problem:** `CommentsViewModelTest.kt` and `ProfileCommentViewModelTest.kt` only test `deleteComment()`'s success branch, despite the review-fixes pass having just made the Compose UI depend on the failure branch (`itemDeletionError`) firing correctly. Sibling ViewModels (`Bookmark`, `ProfileMediaList`, `TopTen`) all test both branches of the identical optimistic-delete pattern.

**Approach:** Add `deleteComment sets itemDeletionError on failure` to both files, mirroring `BookmarkViewModelTest.kt`'s existing failure-branch test shape (stub the update endpoint to error, assert `itemDeletionError.value` is non-null and `data` is unchanged).

## H. Minor cleanup (Suggestions)

1. **`ResettingMutableLiveData` direct test coverage** — new `ResettingMutableLiveDataTest.kt` using a minimal always-active fake `LifecycleOwner` (a small local helper, since this project has no Robolectric), locking in the reset-to-null-after-delivery contract directly rather than only indirectly through consuming ViewModel tests that never call `.observe(...)`.
2. **Detekt unused imports**: `EditCommentViewModelTest.kt:18`, `CommentsViewModelTest.kt:38`, `ProfileMediaListViewModelTest.kt:10,13,14,17` — remove.
3. **Assertion-strength consistency**: `BaseViewModelTest.kt`'s and `NotificationViewModelTest.kt`'s login-required tests currently assert only `assertNotNull(error.value)`; tighten both to `assertEquals(ButtonAction.LOGIN, error.value?.buttonAction)`, matching the 3 sibling tests (`AnimeViewModelTest`, `BookmarkViewModelTest`, `ChatViewModelTest`) that already do this.
4. **`MainActivity` launch-counter guard**: restore the `savedInstanceState == null` check around `preferenceHelper.incrementLaunches()` so rotation doesn't inflate the counter.
5. **`RatingDialog` removal**: `src/main/kotlin/me/proxer/app/ui/view/RatingDialog.kt` is orphaned (no caller since the migration) — delete it.
6. **CI checkout-action consistency**: `.github/workflows/ci.yml`'s `analysis` job uses `actions/checkout@v7` while `unit-tests`/`instrumented-tests` use `@v6` — align to one version (use `@v7`, the newer one, across all three).

## Testing Plan

- **Unit tests**: section G's two new tests, section H.1's new `ResettingMutableLiveDataTest`, section H.3's tightened assertions. Full suite (`./gradlew testDebugUnitTest`) run after each task and once at the end.
- **Type-check**: `./gradlew compileDebugKotlin` after every Compose-file change (sections A-F).
- **Detekt**: `./gradlew detekt` after section H.2's cleanup and spot-checked after each new test file, to avoid reintroducing the import-ordering/unused-import class of issue that came up twice in the prior pass.
- **Manual verification** (UI-heavy, no automated coverage exists for Compose screens in this codebase): pull-to-refresh failure on each of the 8 `refreshError` screens shows a snackbar; repeat-identical-error retry on Anime/Manga stream resolution and user-state actions shows the snackbar every time, not just the first; Messenger report failure keeps the dialog open with an inline error; Industry/TranslatorGroup link tap with no handling app shows a toast instead of nothing.

## Assumptions / Open Items Carried Into the Plan

- Exact current field names/line numbers for section D's 9 screens will be re-verified by reading each live file at plan-writing time — the review agents' line numbers are a starting point, not verified-exact for implementation.
- Section F's toast string (reuse vs. new key) decided at plan-writing time.
- Whether all 8 of section C's ViewModels actually extend `PagedViewModel` (and thus genuinely have `refreshError`) will be confirmed by reading each ViewModel at plan-writing time, not assumed from the review agent's list alone.
