# Full-Branch Review Follow-Up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the Critical/Important/Suggestion findings from a fresh full-branch PR review of `compose-migration` vs `master` (spec: `docs/superpowers/specs/2026-07-11-full-branch-review-followup-design.md`), extending a proven one-shot-LiveData-event fix pattern from 6 screens to 15+, plus independent fixes for `refreshError`, `MessengerScreen`'s report handling, two silently-swallowed-link-failure sites, a test gap, and minor cleanup.

**Architecture:** A new shared `ObserveLiveDataEvent` composable replaces every hand-copied `DisposableEffect`+`Observer` block (6 existing + ~13 new). Each task after that is an independent file-level fix; most touch one screen. `MessengerScreen.kt` bundles three related fixes (report handling, `draft`, `refreshError`) into one task since they're the same file. Tasks can run in any order except: Task 1 (the helper) must land before every other Compose-file task.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), RxJava 2, Koin 4.2.1 DI, JUnit4 + MockK unit tests.

## Global Constraints

- Always use `./gradlew`, never system `gradle`; keep the Gradle daemon on.
- Fast type-check: `./gradlew compileDebugKotlin`. Unit tests: `./gradlew testDebugUnitTest --tests "fully.qualified.ClassName"`.
- Import ordering in this repo is case-sensitive lexicographic (detekt-enforced) — a recurring source of review findings in the prior pass. Double-check new import placement against the surrounding block, not just append-and-hope.
- `ResettingMutableLiveData<T>.observe()` resets its internal value to `null` after delivering to all active observers — this is intentional, matches Fragment-era `Observer` semantics, and is what makes `ObserveLiveDataEvent` (a raw `Observer`, not `observeAsState()`) the correct consumption pattern for these fields.
- Do not touch `BaseViewModel`'s no-auto-load contract or `LaunchedEffect(Unit) { viewModel.load() }` calls — out of scope for every task here.

---

### Task 1: Create the `ObserveLiveDataEvent` shared helper

**Files:**
- Create: `src/main/kotlin/me/proxer/app/ui/compose/ObserveLiveDataEvent.kt`

**Interfaces:**
- Produces: `@Composable fun <T> ObserveLiveDataEvent(liveData: LiveData<T>, onEvent: (T) -> Unit)` — every subsequent task in this plan consumes this exact signature.

- [ ] **Step 1: Write the file**

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

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/ui/compose/ObserveLiveDataEvent.kt
git commit -m "feat(compose): add ObserveLiveDataEvent helper for one-shot LiveData events

Replaces the hand-copied DisposableEffect+Observer block (present in 6
screens already) with a single reusable composable, since more screens
need the same fix for the same bug: observeAsState()+LaunchedEffect(value)
silently drops repeat structurally-equal one-shot events."
```

---

### Task 2: Retrofit `ChatScreen.kt` onto the helper + add `refreshError`

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/chat/pub/message/ChatScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent<T>(liveData: LiveData<T>, onEvent: (T) -> Unit)` (Task 1).
- Consumes: `ChatViewModel.refreshError: ResettingMutableLiveData<ErrorUtils.ErrorAction?>` (inherited from `PagedViewModel`, existing).

The file currently has two `DisposableEffect(lifecycleOwner, X) { val observer = Observer<T> {...}; X.observe(...); onDispose {...} }` blocks (for `sendMessageError` and `reportData`) written out by hand. Replace both with one-line `ObserveLiveDataEvent` calls, and add a third for `refreshError`.

- [ ] **Step 1: Replace the two hand-written blocks and add refreshError**

In `ChatScreenContent` (the private composable), find:
```kotlin
    // sendMessageError/reportData are ResettingMutableLiveData - each real failure/success is a
    // one-shot event, not a piece of continuous state. observeAsState()+LaunchedEffect(value) would
    // silently miss every event after the first structurally-equal one (e.g. two offline sends produce
    // the same ErrorAction, and Unit==Unit always), since Compose's default structural-equality state
    // policy skips recomposition when the "new" value equals the current one. A raw Observer bypasses
    // that: ResettingMutableLiveData already fires onChanged exactly once per genuine event.
    DisposableEffect(lifecycleOwner, sendMessageError) {
        val observer = Observer<ErrorUtils.ErrorAction?> { err ->
            if (err != null) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.error_chat_send_message, context.getString(err.message)),
                    )
                }
            }
        }
        sendMessageError.observe(lifecycleOwner, observer)
        onDispose { sendMessageError.removeObserver(observer) }
    }

    DisposableEffect(lifecycleOwner, reportData) {
        val observer = Observer<Unit?> { value ->
            if (value != null) {
                reportTarget = null
                reportReason = ""
                selectedIds = emptySet()
            }
        }
        reportData.observe(lifecycleOwner, observer)
        onDispose { reportData.removeObserver(observer) }
    }
```

Replace with:
```kotlin
    ObserveLiveDataEvent(sendMessageError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_chat_send_message, context.getString(err.message)),
            )
        }
    }

    ObserveLiveDataEvent(reportData) {
        reportTarget = null
        reportReason = ""
        selectedIds = emptySet()
    }

    ObserveLiveDataEvent(refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
    }
```

Add `refreshError: LiveData<ErrorUtils.ErrorAction?>` to `ChatScreenContent`'s parameter list (next to the existing `sendMessageError: LiveData<ErrorUtils.ErrorAction?>` param), and in the public `ChatScreen` composable, add `refreshError = viewModel.refreshError,` to the `ChatScreenContent(...)` call.

Remove the now-unused `lifecycleOwner`/`Observer` imports if nothing else in the file uses them (check `LocalLifecycleOwner`/`androidx.lifecycle.Observer` usage elsewhere in the file before removing — `LocalLifecycleOwner` is also used by the existing polling `DisposableEffect` in the public `ChatScreen`, so keep that import; only remove `androidx.lifecycle.Observer` if it's now unused). Add import `me.proxer.app.ui.compose.ObserveLiveDataEvent`.

Update `ChatScreenContentPreview` to add `refreshError = MutableLiveData(null),` to its call (needs `import androidx.lifecycle.MutableLiveData` if not already present — it is, from the prior session's fix).

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run covering tests**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.chat.pub.message.ChatViewModelTest" --tests "me.proxer.app.chat.pub.message.ChatReportViewModelTest"`
Expected: `BUILD SUCCESSFUL`, all pass (this task doesn't touch the ViewModel layer — safety check only).

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/chat/pub/message/ChatScreen.kt
git commit -m "refactor(chat): retrofit ChatScreen onto ObserveLiveDataEvent, add refreshError

Replaces the hand-copied DisposableEffect+Observer blocks with the new
shared helper (no behavior change), and adds refreshError observation -
ChatViewModel.refreshError was previously set on pull-to-refresh failure
but never surfaced to the user."
```

---

### Task 3: Retrofit `TopTenScreen.kt` onto the helper

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/profile/topten/TopTenScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1).

- [ ] **Step 1: Replace the hand-written block**

In `TopTenContent`, find:
```kotlin
    // itemDeletionError is a ResettingMutableLiveData - each failure is a one-shot event, not
    // continuous state. observeAsState()+LaunchedEffect(value) would silently miss every failure
    // after the first structurally-equal one, since Compose's default state-equality policy skips
    // recomposition when the "new" value equals the current one. A raw Observer bypasses that.
    DisposableEffect(lifecycleOwner, itemDeletionError) {
        val observer = Observer<ErrorAction?> { err ->
            if (err != null) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.error_top_ten_deletion, context.getString(err.message)),
                    )
                }
            }
        }
        itemDeletionError.observe(lifecycleOwner, observer)
        onDispose { itemDeletionError.removeObserver(observer) }
    }
```

Replace with:
```kotlin
    ObserveLiveDataEvent(itemDeletionError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_top_ten_deletion, context.getString(err.message)),
            )
        }
    }
```

Add import `me.proxer.app.ui.compose.ObserveLiveDataEvent`. Remove `androidx.compose.runtime.DisposableEffect` and `androidx.lifecycle.Observer` imports if now unused (check `lifecycleOwner`/`scope` locals too — if `lifecycleOwner` was only used by the removed block, remove its `val lifecycleOwner = LocalLifecycleOwner.current` line and the `LocalLifecycleOwner` import; `scope`/`rememberCoroutineScope` stays, still used by the snackbar launch).

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/profile/topten/TopTenScreen.kt
git commit -m "refactor(topten): retrofit TopTenScreen onto ObserveLiveDataEvent

No behavior change - replaces the hand-copied DisposableEffect+Observer
block with the new shared helper."
```

---

### Task 4: Retrofit `ProfileMediaListScreen.kt` onto the helper + add `refreshError`

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/profile/media/ProfileMediaListScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1).
- Consumes: `ProfileMediaListViewModel.refreshError` (inherited from `PagedViewModel`).

- [ ] **Step 1: Replace the hand-written block and add refreshError**

In `ProfileMediaListContent`, find:
```kotlin
    // itemDeletionError is a ResettingMutableLiveData - each failure is a one-shot event, not
    // continuous state. observeAsState()+LaunchedEffect(value) would silently miss every failure
    // after the first structurally-equal one, since Compose's default state-equality policy skips
    // recomposition when the "new" value equals the current one. A raw Observer bypasses that.
    DisposableEffect(lifecycleOwner, itemDeletionError) {
        val observer = Observer<ErrorAction?> { err ->
            if (err != null) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.error_profile_media_list_deletion, context.getString(err.message)),
                    )
                }
            }
        }
        itemDeletionError.observe(lifecycleOwner, observer)
        onDispose { itemDeletionError.removeObserver(observer) }
    }
```

Replace with:
```kotlin
    ObserveLiveDataEvent(itemDeletionError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_profile_media_list_deletion, context.getString(err.message)),
            )
        }
    }

    ObserveLiveDataEvent(refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
    }
```

Add `refreshError: LiveData<ErrorAction?>` to `ProfileMediaListContent`'s parameter list, add `refreshError = viewModel.refreshError,` to the `ProfileMediaListContent(...)` call site in the public `ProfileMediaListScreen`. Add import `me.proxer.app.ui.compose.ObserveLiveDataEvent`; remove now-unused `DisposableEffect`/`Observer`/`LocalLifecycleOwner` imports and the `lifecycleOwner` local if nothing else in the file uses them.

Update `ProfileMediaListContentPreview` to add `refreshError = MutableLiveData(null),` (add `import androidx.lifecycle.MutableLiveData` if not already present — it is, from the prior session's fix).

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/profile/media/ProfileMediaListScreen.kt
git commit -m "refactor(profile-media): retrofit onto ObserveLiveDataEvent, add refreshError"
```

---

### Task 5: Retrofit `BookmarkScreen.kt` onto the helper

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/bookmark/BookmarkScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1).

`BookmarkScreen` has no `refreshError`-eligible ViewModel — `BookmarkViewModel` extends `PagedContentViewModel`, not directly relevant here, and was not in the review's 8-screen `refreshError` list — this task is retrofit-only.

- [ ] **Step 1: Replace the three hand-written blocks**

In `BookmarkContent`, find the three `DisposableEffect(lifecycleOwner, X) { ... }` blocks for `itemDeletionError`, `undoData`, `undoError` (each with the same explanatory-comment shape as Task 2-4). Replace with:

```kotlin
    ObserveLiveDataEvent(itemDeletionError) { err ->
        onDeletionFailed()
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_bookmark_deletion, context.getString(err.message)),
            )
        }
    }

    ObserveLiveDataEvent(undoData) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.fragment_bookmark_delete_message),
                actionLabel = context.getString(R.string.action_undo),
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    ObserveLiveDataEvent(undoError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_undo, context.getString(err.message)),
            )
        }
    }
```

Add import `me.proxer.app.ui.compose.ObserveLiveDataEvent`. Remove now-unused `DisposableEffect`/`Observer`/`LocalLifecycleOwner` imports and the `lifecycleOwner` local if nothing else in the file uses them.

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run covering test**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.bookmark.BookmarkViewModelTest"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/bookmark/BookmarkScreen.kt
git commit -m "refactor(bookmark): retrofit BookmarkScreen onto ObserveLiveDataEvent"
```

---

### Task 6: Retrofit `CommentsScreen.kt` onto the helper + add `refreshError`

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/media/comments/CommentsScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1).
- Consumes: `CommentsViewModel.refreshError` (inherited from `PagedViewModel`).

- [ ] **Step 1: Replace the hand-written block and add refreshError**

In `CommentsContent`, find:
```kotlin
    // itemDeletionError is a ResettingMutableLiveData - each failure is a one-shot event, not
    // continuous state. observeAsState()+LaunchedEffect(value) would silently miss every failure
    // after the first structurally-equal one, since Compose's default state-equality policy skips
    // recomposition when the "new" value equals the current one. A raw Observer bypasses that.
    DisposableEffect(lifecycleOwner, itemDeletionError) {
        val observer = Observer<ErrorAction?> { err ->
            if (err != null) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.error_comment_deletion, context.getString(err.message)),
                    )
                }
            }
        }
        itemDeletionError.observe(lifecycleOwner, observer)
        onDispose { itemDeletionError.removeObserver(observer) }
    }
```

Replace with:
```kotlin
    ObserveLiveDataEvent(itemDeletionError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_comment_deletion, context.getString(err.message)),
            )
        }
    }

    ObserveLiveDataEvent(refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
    }
```

Add `refreshError: LiveData<ErrorAction?>` to `CommentsContent`'s parameter list, add `refreshError = viewModel.refreshError,` to the `CommentsContent(...)` call site in the public `CommentsScreen`. Add import `me.proxer.app.ui.compose.ObserveLiveDataEvent`; remove now-unused imports.

Update `CommentsContentPreview` to add `refreshError = MutableLiveData(null), ` and `currentUserId = null,` stays as-is (already present from prior session). Add `import androidx.lifecycle.MutableLiveData` if not already present (it is, from the prior session's fix).

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/media/comments/CommentsScreen.kt
git commit -m "refactor(comments): retrofit onto ObserveLiveDataEvent, add refreshError"
```

---

### Task 7: Retrofit `ProfileCommentScreen.kt` onto the helper + add `refreshError`

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/profile/comment/ProfileCommentScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1).
- Consumes: `ProfileCommentViewModel.refreshError` (inherited from `PagedViewModel`).

- [ ] **Step 1: Replace the hand-written block and add refreshError**

Same shape as Task 6 — in `ProfileCommentContent`, replace the `itemDeletionError` `DisposableEffect` block with:
```kotlin
    ObserveLiveDataEvent(itemDeletionError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_comment_deletion, context.getString(err.message)),
            )
        }
    }

    ObserveLiveDataEvent(refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
    }
```

Add `refreshError: LiveData<ErrorAction?>` param, thread `viewModel.refreshError` from the public `ProfileCommentScreen`, add import, remove unused imports, update `ProfileCommentContentPreview` with `refreshError = MutableLiveData(null),`.

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/profile/comment/ProfileCommentScreen.kt
git commit -m "refactor(profile-comments): retrofit onto ObserveLiveDataEvent, add refreshError"
```

---

### Task 8: Add `refreshError` to `TopicScreen.kt` (new SnackbarHostState)

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/forum/TopicScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1), `TopicViewModel.refreshError` (inherited from `PagedViewModel`).

This screen has no `SnackbarHostState`/`SnackbarHost` at all today. Add one.

- [ ] **Step 1: Add imports**

Add to the import block:
```kotlin
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.LiveData
import kotlinx.coroutines.launch
import me.proxer.app.ui.compose.ObserveLiveDataEvent
```

- [ ] **Step 2: Thread `refreshError` from the public composable into `TopicContent`**

Change the public `TopicScreen` composable's `TopicContent(...)` call (currently ending `onLoadMore = { viewModel.loadIfPossible() },\n    )`) to add `refreshError = viewModel.refreshError,` as a new argument.

Change `TopicContent`'s signature — add `refreshError: LiveData<ErrorUtils.ErrorAction?>,` as a new parameter (after `error: ErrorUtils.ErrorAction?,`).

- [ ] **Step 3: Add the SnackbarHostState, ObserveLiveDataEvent call, and wire it into the Scaffold**

Inside `TopicContent`, after the existing `val listState = rememberLazyListState()` line, add:
```kotlin
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveLiveDataEvent(refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
    }
```

Change the `Scaffold(` call (currently `Scaffold(\n        topBar = {`) to add a `snackbarHost` parameter:
```kotlin
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
```

- [ ] **Step 4: Update the preview**

In `TopicContentPreview`, add `refreshError = MutableLiveData(null),` to the `TopicContent(...)` call (add `import androidx.lifecycle.MutableLiveData` to the import block).

- [ ] **Step 5: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/me/proxer/app/forum/TopicScreen.kt
git commit -m "fix(forum): surface TopicViewModel.refreshError

TopicViewModel (like 7 other PagedViewModel subclasses) sets refreshError
on pull-to-refresh/pagination failure, but no screen observed it -
failures were completely silent."
```

---

### Task 9: Add `refreshError` to `HistoryScreen.kt` (new SnackbarHostState)

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/profile/history/HistoryScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1), `HistoryViewModel.refreshError` (inherited from `PagedViewModel`).

Same shape as Task 8 — this screen also has no `SnackbarHostState`, and additionally has no `Scaffold` at all (its `ContentScreen` call has no `Scaffold` wrapper, unlike `TopicContent`).

- [ ] **Step 1: Add imports**

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.lifecycle.LiveData
import kotlinx.coroutines.launch
import me.proxer.app.ui.compose.ObserveLiveDataEvent
```
(`androidx.compose.ui.Modifier` is already imported.)

- [ ] **Step 2: Thread `refreshError` into `HistoryContent`**

In the public `HistoryScreen`, change the `HistoryContent(...)` call to add `refreshError = viewModel.refreshError,`.

Change `HistoryContent`'s signature — add `refreshError: LiveData<ErrorAction?>,` (after `error: ErrorAction?,`).

- [ ] **Step 3: Add the SnackbarHostState/Box overlay, matching the Box+SnackbarHost pattern used elsewhere in this codebase for screens without a Scaffold (e.g. `TopTenScreen.kt`)**

Replace `HistoryContent`'s current body:
```kotlin
private fun HistoryContent(
    data: List<LocalUserHistoryEntry>?,
    error: ErrorAction?,
    isLoading: Boolean,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState.layoutInfo) {
        val total = gridState.layoutInfo.totalItemsCount
        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) onLoadMore()
    }

    ContentScreen(
        isLoading = isLoading && data.isNullOrEmpty(),
        error = if (data.isNullOrEmpty()) error else null,
        onRetry = onRetry,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(data ?: emptyList(), key = { it.id }) { entry ->
                HistoryCard(entry = entry)
            }
        }
    }
}
```

with:
```kotlin
private fun HistoryContent(
    data: List<LocalUserHistoryEntry>?,
    error: ErrorAction?,
    isLoading: Boolean,
    refreshError: LiveData<ErrorAction?>,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(gridState.layoutInfo) {
        val total = gridState.layoutInfo.totalItemsCount
        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) onLoadMore()
    }

    ObserveLiveDataEvent(refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ContentScreen(
            isLoading = isLoading && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = onRetry,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(data ?: emptyList(), key = { it.id }) { entry ->
                    HistoryCard(entry = entry)
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
```
(`val context = LocalContext.current` is new here — `HistoryContent` didn't previously need `context` at this level, only `HistoryCard` did via its own `LocalContext.current` call, which stays unchanged.)

- [ ] **Step 4: Update the preview**

In `HistoryContentPreview`, add `refreshError = MutableLiveData(null),` (add `import androidx.lifecycle.MutableLiveData`).

- [ ] **Step 5: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/me/proxer/app/profile/history/HistoryScreen.kt
git commit -m "fix(history): surface HistoryViewModel.refreshError"
```

---

### Task 10: Add `refreshError` + fix `deletionError` one-shot bug in `NotificationScreen.kt`

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/notification/NotificationScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1), `NotificationViewModel.refreshError` (inherited).

This screen already has a `SnackbarHostState` (used for `deletionError`), but that field is currently observed the unsafe way (`observeAsState()` + `LaunchedEffect`). Fix both in one pass.

- [ ] **Step 1: Replace the `deletionError` handling and add `refreshError`**

In the public `NotificationScreen` composable, find:
```kotlin
    val deletionError by viewModel.deletionError.observeAsState()
```
Remove this line (the raw LiveData is passed down instead).

Find:
```kotlin
    LaunchedEffect(deletionError) {
        deletionError?.let { errorAction ->
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_notification_deletion, context.getString(errorAction.message)),
            )
        }
    }
```
This `LaunchedEffect` and the `snackbarHostState`/`context` it uses currently live in the public `NotificationScreen` composable, not `NotificationContent` — check the live file to confirm exact placement before editing (research showed `snackbarHostState`/`context` created at the top of `NotificationScreen`, line 62-63). Remove this `LaunchedEffect` block and replace with:
```kotlin
    ObserveLiveDataEvent(viewModel.deletionError) { errorAction ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_notification_deletion, context.getString(errorAction.message)),
            )
        }
    }

    ObserveLiveDataEvent(viewModel.refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
    }
```
Add `val scope = rememberCoroutineScope()` next to the existing `val snackbarHostState = remember { SnackbarHostState() }` line if a coroutine scope isn't already in scope there (check the live file — if `context.getString` was previously called directly inside a suspend-free `LaunchedEffect(deletionError) { ... snackbarHostState.showSnackbar(...) }`, that `LaunchedEffect` itself was already a coroutine scope, so no separate `scope` was needed before; `ObserveLiveDataEvent`'s `onEvent` callback is NOT a suspend/coroutine context, so calling `snackbarHostState.showSnackbar(...)` (a suspend function) requires wrapping in `scope.launch { }` — add `rememberCoroutineScope()` import and the `val scope = rememberCoroutineScope()` line).

Add imports: `androidx.compose.runtime.rememberCoroutineScope`, `kotlinx.coroutines.launch`, `me.proxer.app.ui.compose.ObserveLiveDataEvent`. Remove `androidx.compose.runtime.livedata.observeAsState` import usage for `deletionError` specifically (keep the import itself, since `data`/`error`/`isLoading` still use `observeAsState()`).

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run covering test**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.notification.NotificationViewModelTest"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/notification/NotificationScreen.kt
git commit -m "fix(notification): fix one-shot deletionError drop, add refreshError

deletionError was observed via observeAsState()+LaunchedEffect(value),
which silently drops the second of two identical consecutive deletion
failures. Switched to ObserveLiveDataEvent; also added refreshError,
previously set on refresh failure but never surfaced."
```

---

### Task 11: Fix `AnimeScreen.kt`'s 4 one-shot fields

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/anime/AnimeScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1).

All 4 `LaunchedEffect`s live in the public `AnimeScreen` composable, which already has direct `viewModel` access — no `AnimeContent` signature/preview changes needed.

- [ ] **Step 1: Replace the 4 observeAsState()+LaunchedEffect pairs**

Remove these 4 lines:
```kotlin
    val resolutionResult by viewModel.resolutionResult.observeAsState()
    val resolutionError by viewModel.resolutionError.observeAsState()
    val userStateData by viewModel.userStateData.observeAsState()
    val userStateError by viewModel.userStateError.observeAsState()
```

Replace the 4 `LaunchedEffect` blocks:
```kotlin
LaunchedEffect(resolutionResult) {
    resolutionResult?.let { result ->
        when (result) {
            is StreamResolutionResult.Video -> {
                result.play(
                    context, id, name, episode, language,
                    ProxerUrls.entryImage(id).toString().let { Uri.parse(it) }, true,
                )
            }
            is StreamResolutionResult.Link -> {
                context.startActivity(result.makeIntent())
            }
            is StreamResolutionResult.App -> {
                result.navigate(context)
            }
            is StreamResolutionResult.Message -> {
                // Messages are shown inline in the stream list item.
            }
        }
    }
}

LaunchedEffect(resolutionError) {
    resolutionError?.let { action ->
        if (action is AppRequiredErrorAction) {
            appRequiredAction = action
        } else {
            snackbarHostState.showSnackbar(context.getString(action.message))
        }
    }
}

LaunchedEffect(userStateData) {
    if (userStateData != null) {
        snackbarHostState.showSnackbar(context.getString(R.string.fragment_set_user_info_success))
    }
}

LaunchedEffect(userStateError) {
    userStateError?.let { action ->
        snackbarHostState.showSnackbar(
            context.getString(R.string.error_set_user_info, context.getString(action.message)),
        )
    }
}
```
with:
```kotlin
ObserveLiveDataEvent(viewModel.resolutionResult) { result ->
    when (result) {
        is StreamResolutionResult.Video -> {
            result.play(
                context, id, name, episode, language,
                ProxerUrls.entryImage(id).toString().let { Uri.parse(it) }, true,
            )
        }
        is StreamResolutionResult.Link -> {
            context.startActivity(result.makeIntent())
        }
        is StreamResolutionResult.App -> {
            result.navigate(context)
        }
        is StreamResolutionResult.Message -> {
            // Messages are shown inline in the stream list item.
        }
    }
}

ObserveLiveDataEvent(viewModel.resolutionError) { action ->
    if (action is AppRequiredErrorAction) {
        appRequiredAction = action
    } else {
        scope.launch { snackbarHostState.showSnackbar(context.getString(action.message)) }
    }
}

ObserveLiveDataEvent(viewModel.userStateData) {
    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.fragment_set_user_info_success)) }
}

ObserveLiveDataEvent(viewModel.userStateError) { action ->
    scope.launch {
        snackbarHostState.showSnackbar(
            context.getString(R.string.error_set_user_info, context.getString(action.message)),
        )
    }
}
```

Note the callback bodies now need `scope.launch { }` around `snackbarHostState.showSnackbar(...)` since `ObserveLiveDataEvent`'s `onEvent` is not a suspend context (the old `LaunchedEffect` blocks were). Check whether `val scope = rememberCoroutineScope()` already exists in `AnimeScreen` (it's likely used elsewhere for `drawerState`-style interactions in other screens, but confirm in this file) — if not present, add `import androidx.compose.runtime.rememberCoroutineScope` and `val scope = rememberCoroutineScope()` near the existing `val snackbarHostState = remember { SnackbarHostState() }` line. Also add `import kotlinx.coroutines.launch` if not present.

Add import `me.proxer.app.ui.compose.ObserveLiveDataEvent`. Remove `androidx.compose.runtime.livedata.observeAsState` usage for these 4 fields specifically if it becomes otherwise-unused in the file (check `data`/`error`/`isLoading`-style fields elsewhere in `AnimeScreen` — if any remain, keep the import).

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/anime/AnimeScreen.kt
git commit -m "fix(anime): fix one-shot event drop for resolutionResult/Error, userStateData/Error

observeAsState()+LaunchedEffect(value) silently drops the second of two
structurally-equal consecutive events - e.g. retrying a failed stream
resolution twice while offline showed the error snackbar only once."
```

---

### Task 12: Fix `MangaScreen.kt`'s 2 one-shot fields

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/manga/MangaScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1).

Both `LaunchedEffect`s live in the public `MangaScreen` composable (local vals `userStateSaved`/`userStateErr`), which has direct `viewModel` access.

- [ ] **Step 1: Replace the 2 observeAsState()+LaunchedEffect pairs**

Remove:
```kotlin
    val userStateSaved by viewModel.userStateData.observeAsState()
    val userStateErr by viewModel.userStateError.observeAsState()
```

Replace:
```kotlin
LaunchedEffect(userStateSaved) {
    if (userStateSaved != null) {
        snackbarHostState.showSnackbar(context.getString(R.string.fragment_set_user_info_success))
    }
}

LaunchedEffect(userStateErr) {
    val err = userStateErr
    if (err != null) {
        snackbarHostState.showSnackbar(
            context.getString(R.string.error_set_user_info, context.getString(err.message)),
        )
    }
}
```
with:
```kotlin
ObserveLiveDataEvent(viewModel.userStateData) {
    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.fragment_set_user_info_success)) }
}

ObserveLiveDataEvent(viewModel.userStateError) { err ->
    scope.launch {
        snackbarHostState.showSnackbar(
            context.getString(R.string.error_set_user_info, context.getString(err.message)),
        )
    }
}
```

`MangaScreen.kt` already imports `androidx.compose.runtime.rememberCoroutineScope` and `kotlinx.coroutines.launch` (confirmed present — used for `readerOrientation` toggle/fullscreen logic), so a `scope` local likely already exists; reuse it if so, otherwise add `val scope = rememberCoroutineScope()`. Add import `me.proxer.app.ui.compose.ObserveLiveDataEvent`.

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/manga/MangaScreen.kt
git commit -m "fix(manga): fix one-shot event drop for userStateData/userStateError"
```

---

### Task 13: Fix `MediaInfoScreen.kt`'s 2 one-shot fields (Content signature change)

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/media/info/MediaInfoScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1).

Both `LaunchedEffect`s live inside the private `MediaInfoContent` composable, which owns the `SnackbarHostState` — this requires changing `MediaInfoContent`'s signature to take the raw LiveData instead of the extracted `Unit?`/`ErrorAction?` values, and updating its preview.

- [ ] **Step 1: Change the public `MediaInfoScreen` composable**

Remove:
```kotlin
    val updateResult by viewModel.userInfoUpdateData.observeAsState()
    val updateError by viewModel.userInfoUpdateError.observeAsState()
```

Change the `MediaInfoContent(...)` call — replace `updateResult = updateResult,` and `updateError = updateError,` with:
```kotlin
        updateResult = viewModel.userInfoUpdateData,
        updateError = viewModel.userInfoUpdateError,
```

- [ ] **Step 2: Change `MediaInfoContent`'s signature and body**

Change the signature:
```kotlin
private fun MediaInfoContent(
    data: Entry?,
    error: ErrorAction?,
    isLoading: Boolean,
    userInfo: MediaUserInfo?,
    updateResult: Unit?,
    updateError: ErrorAction?,
    onRetry: () -> Unit,
    onNote: () -> Unit,
    onFavorite: () -> Unit,
    onFinish: () -> Unit,
    onSubscribe: () -> Unit,
) {
```
to:
```kotlin
private fun MediaInfoContent(
    data: Entry?,
    error: ErrorAction?,
    isLoading: Boolean,
    userInfo: MediaUserInfo?,
    updateResult: LiveData<Unit?>,
    updateError: LiveData<ErrorAction?>,
    onRetry: () -> Unit,
    onNote: () -> Unit,
    onFavorite: () -> Unit,
    onFinish: () -> Unit,
    onSubscribe: () -> Unit,
) {
```

Replace:
```kotlin
LaunchedEffect(updateResult) {
    if (updateResult != null) {
        snackbarHostState.showSnackbar(context.getString(R.string.fragment_set_user_info_success))
    }
}

LaunchedEffect(updateError) {
    val err = updateError
    if (err != null) {
        snackbarHostState.showSnackbar(
            context.getString(R.string.error_set_user_info, context.getString(err.message)),
        )
    }
}
```
with:
```kotlin
val scope = rememberCoroutineScope()

ObserveLiveDataEvent(updateResult) {
    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.fragment_set_user_info_success)) }
}

ObserveLiveDataEvent(updateError) { err ->
    scope.launch {
        snackbarHostState.showSnackbar(
            context.getString(R.string.error_set_user_info, context.getString(err.message)),
        )
    }
}
```

- [ ] **Step 3: Add imports and fix the preview**

Add to the import block: `androidx.compose.runtime.rememberCoroutineScope`, `androidx.lifecycle.LiveData`, `androidx.lifecycle.MutableLiveData`, `kotlinx.coroutines.launch`, `me.proxer.app.ui.compose.ObserveLiveDataEvent`.

In `MediaInfoContentPreview`, change `updateResult = null,` and `updateError = null,` to `updateResult = MutableLiveData(null),` and `updateError = MutableLiveData(null),`.

- [ ] **Step 4: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/media/info/MediaInfoScreen.kt
git commit -m "fix(media-info): fix one-shot event drop for userInfoUpdateData/Error"
```

---

### Task 14: Fix `EditCommentScreen.kt`'s 2 one-shot fields

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/comment/EditCommentScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1).

Both `LaunchedEffect`s live in the public `EditCommentScreen` composable — no `EditCommentContent` signature/preview change needed.

- [ ] **Step 1: Replace the 2 observeAsState()+LaunchedEffect pairs**

Remove:
```kotlin
    val publishResult by viewModel.publishResult.observeAsState()
    val publishError by viewModel.publishError.observeAsState()
```

Replace:
```kotlin
LaunchedEffect(publishResult) {
    val comment = publishResult ?: return@LaunchedEffect
    val activity = context as? Activity ?: return@LaunchedEffect
    activity.setResult(Activity.RESULT_OK, Intent().putExtra(EditCommentActivity.COMMENT_EXTRA, comment))
    Toast.makeText(context, R.string.fragment_edit_comment_published, Toast.LENGTH_SHORT).show()
    activity.finish()
}

LaunchedEffect(publishError) {
    publishError?.let { errorAction ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_comment_publish, context.getString(errorAction.message)),
            )
        }
    }
}
```
with:
```kotlin
ObserveLiveDataEvent(viewModel.publishResult) { comment ->
    val activity = context as? Activity ?: return@ObserveLiveDataEvent
    activity.setResult(Activity.RESULT_OK, Intent().putExtra(EditCommentActivity.COMMENT_EXTRA, comment))
    Toast.makeText(context, R.string.fragment_edit_comment_published, Toast.LENGTH_SHORT).show()
    activity.finish()
}

ObserveLiveDataEvent(viewModel.publishError) { errorAction ->
    scope.launch {
        snackbarHostState.showSnackbar(
            context.getString(R.string.error_comment_publish, context.getString(errorAction.message)),
        )
    }
}
```
(`scope`/`snackbarHostState`/`context` already exist in this composable per the research — reused as-is. `return@ObserveLiveDataEvent` is valid since `ObserveLiveDataEvent`'s trailing lambda is the `onEvent` parameter, a normal lambda with its own implicit label matching the function name.)

Add import `me.proxer.app.ui.compose.ObserveLiveDataEvent`.

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/comment/EditCommentScreen.kt
git commit -m "fix(edit-comment): fix one-shot event drop for publishResult/publishError"
```

---

### Task 15: Fix `CreateConferenceScreen.kt`'s 2 one-shot fields (partial Content signature change)

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/create/CreateConferenceScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1).

`result`'s `LaunchedEffect` lives in the public `CreateConferenceScreen` (no signature change needed for that one); `error`'s `LaunchedEffect` lives inside the private `CreateConferenceContent`, which owns the `SnackbarHostState` (needs a signature change for that one, from `error: ErrorUtils.ErrorAction?` to `error: LiveData<ErrorUtils.ErrorAction>`).

- [ ] **Step 1: Fix `result` in the public composable**

Remove:
```kotlin
    val result by viewModel.result.observeAsState()
```

Replace:
```kotlin
LaunchedEffect(result) {
    val conf: LocalConference? = result
    if (conf != null) {
        val activity = context as? Activity ?: return@LaunchedEffect
        activity.finish()
        PrvMessengerActivity.navigateTo(activity, conf)
    }
}
```
with:
```kotlin
ObserveLiveDataEvent(viewModel.result) { conf ->
    val activity = context as? Activity ?: return@ObserveLiveDataEvent
    activity.finish()
    PrvMessengerActivity.navigateTo(activity, conf)
}
```

- [ ] **Step 2: Fix `error` — change `CreateConferenceContent`'s signature**

Change the `CreateConferenceContent(...)` call site in the public composable — replace `error = error,` with `error = viewModel.error,` (remove the now-unused `val error by viewModel.error.observeAsState()` line too, if it's not read anywhere else in the public composable — check the live file first).

Change `CreateConferenceContent`'s signature:
```kotlin
private fun CreateConferenceContent(
    isGroup: Boolean,
    isLoading: Boolean,
    error: ErrorUtils.ErrorAction?,
    initialParticipant: Participant?,
    onCreateChat: (message: String, participant: Participant) -> Unit,
    onCreateGroup: (topic: String, message: String, participants: List<Participant>) -> Unit,
    onBack: () -> Unit,
) {
```
to:
```kotlin
private fun CreateConferenceContent(
    isGroup: Boolean,
    isLoading: Boolean,
    error: LiveData<ErrorUtils.ErrorAction>,
    initialParticipant: Participant?,
    onCreateChat: (message: String, participant: Participant) -> Unit,
    onCreateGroup: (topic: String, message: String, participants: List<Participant>) -> Unit,
    onBack: () -> Unit,
) {
```

Replace:
```kotlin
LaunchedEffect(error) {
    error?.let { snackbarHostState.showSnackbar(context.getString(it.message)) }
}
```
with:
```kotlin
val scope = rememberCoroutineScope()

ObserveLiveDataEvent(error) {
    scope.launch { snackbarHostState.showSnackbar(context.getString(it.message)) }
}
```
(Confirmed from the full import block gathered during plan research: `CreateConferenceScreen.kt` does NOT currently import `rememberCoroutineScope` or `kotlinx.coroutines.launch` anywhere, so `scope` does not already exist in `CreateConferenceContent` — this addition is required, not conditional.)

- [ ] **Step 3: Add imports and fix the preview**

Add `androidx.compose.runtime.rememberCoroutineScope`, `androidx.lifecycle.LiveData`, `androidx.lifecycle.MutableLiveData`, `kotlinx.coroutines.launch`, `me.proxer.app.ui.compose.ObserveLiveDataEvent` to the import block.

In `CreateConferenceContentPreview`, change `error = null,` to `error = MutableLiveData(),` (a `MutableLiveData<ErrorUtils.ErrorAction>()` with no initial value, matching the non-nullable-generic type — its `.value` is `null` until set, same runtime behavior as before).

- [ ] **Step 4: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/chat/prv/create/CreateConferenceScreen.kt
git commit -m "fix(create-conference): fix one-shot event drop for result/error"
```

---

### Task 16: Fix `ProfileSettingsScreen.kt`'s 2 one-shot fields (Content signature change)

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/profile/settings/ProfileSettingsScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1).

Both fields (`error`, `updateError`) are declared as `ResettingMutableLiveData<ErrorUtils.ErrorAction>` (no `?` in the generic) — `ObserveLiveDataEvent<T>`'s generic still works correctly here (see Task 1's design note on platform types).

- [ ] **Step 1: Change the public `ProfileSettingsScreen` composable**

Remove:
```kotlin
    val error by viewModel.error.observeAsState()
    val updateError by viewModel.updateError.observeAsState()
```

Change the `ProfileSettingsContent(...)` call — replace `error = error,` and `updateError = updateError,` with `error = viewModel.error,` and `updateError = viewModel.updateError,`.

- [ ] **Step 2: Change `ProfileSettingsContent`'s signature and body**

Change:
```kotlin
private fun ProfileSettingsContent(
    settings: LocalProfileSettings?,
    error: ErrorAction?,
    updateError: ErrorAction?,
    onBack: () -> Unit,
    onUpdate: (LocalProfileSettings) -> Unit,
) {
```
to:
```kotlin
private fun ProfileSettingsContent(
    settings: LocalProfileSettings?,
    error: LiveData<ErrorAction>,
    updateError: LiveData<ErrorAction>,
    onBack: () -> Unit,
    onUpdate: (LocalProfileSettings) -> Unit,
) {
```

Replace:
```kotlin
// Show load error as snackbar
LaunchedEffect(error) {
    error?.let {
        snackbarHostState.showSnackbar(
            context.getString(R.string.error_refresh, context.getString(it.message)),
        )
    }
}

// Show update error as snackbar
LaunchedEffect(updateError) {
    updateError?.let {
        snackbarHostState.showSnackbar(
            context.getString(R.string.error_set_user_info, context.getString(it.message)),
        )
    }
}
```
with:
```kotlin
val scope = rememberCoroutineScope()

// Show load error as snackbar
ObserveLiveDataEvent(error) {
    scope.launch {
        snackbarHostState.showSnackbar(
            context.getString(R.string.error_refresh, context.getString(it.message)),
        )
    }
}

// Show update error as snackbar
ObserveLiveDataEvent(updateError) {
    scope.launch {
        snackbarHostState.showSnackbar(
            context.getString(R.string.error_set_user_info, context.getString(it.message)),
        )
    }
}
```

- [ ] **Step 3: Add imports and fix the preview**

Add `androidx.compose.runtime.rememberCoroutineScope`, `androidx.lifecycle.LiveData`, `androidx.lifecycle.MutableLiveData`, `kotlinx.coroutines.launch`, `me.proxer.app.ui.compose.ObserveLiveDataEvent`.

In `ProfileSettingsScreenPreview`, change `error = null,` and `updateError = null,` to `error = MutableLiveData(),` and `updateError = MutableLiveData(),`.

- [ ] **Step 4: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/profile/settings/ProfileSettingsScreen.kt
git commit -m "fix(profile-settings): fix one-shot event drop for error/updateError"
```

---

### Task 17: Fix `EpisodeScreen.kt`'s 2 one-shot fields (Content signature change)

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/media/episode/EpisodeScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1).

- [ ] **Step 1: Change the public `EpisodeScreen` composable**

Remove:
```kotlin
    val bookmarkResult by viewModel.bookmarkData.observeAsState()
    val bookmarkError by viewModel.bookmarkError.observeAsState()
```

Change the `EpisodeContent(...)` call — replace `bookmarkResult = bookmarkResult,` and `bookmarkError = bookmarkError,` with `bookmarkResult = viewModel.bookmarkData,` and `bookmarkError = viewModel.bookmarkError,`.

- [ ] **Step 2: Change `EpisodeContent`'s signature and body**

Change:
```kotlin
private fun EpisodeContent(
    data: List<EpisodeRow>?,
    error: ErrorAction?,
    isLoading: Boolean,
    mediaId: String,
    mediaName: String?,
    bookmarkResult: Unit?,
    bookmarkError: ErrorAction?,
    onRetry: () -> Unit,
    onBookmark: (Int, MediaLanguage, Category) -> Unit,
) {
```
to:
```kotlin
private fun EpisodeContent(
    data: List<EpisodeRow>?,
    error: ErrorAction?,
    isLoading: Boolean,
    mediaId: String,
    mediaName: String?,
    bookmarkResult: LiveData<Unit?>,
    bookmarkError: LiveData<ErrorAction?>,
    onRetry: () -> Unit,
    onBookmark: (Int, MediaLanguage, Category) -> Unit,
) {
```

Replace:
```kotlin
LaunchedEffect(bookmarkResult) {
    if (bookmarkResult != null) {
        snackbarHostState.showSnackbar(context.getString(R.string.fragment_set_user_info_success))
    }
}

LaunchedEffect(bookmarkError) {
    val err = bookmarkError
    if (err != null) {
        snackbarHostState.showSnackbar(
            context.getString(R.string.error_set_user_info, context.getString(err.message)),
        )
    }
}
```
with:
```kotlin
val scope = rememberCoroutineScope()

ObserveLiveDataEvent(bookmarkResult) {
    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.fragment_set_user_info_success)) }
}

ObserveLiveDataEvent(bookmarkError) { err ->
    scope.launch {
        snackbarHostState.showSnackbar(
            context.getString(R.string.error_set_user_info, context.getString(err.message)),
        )
    }
}
```

- [ ] **Step 3: Add imports and fix the preview**

Add `androidx.compose.runtime.rememberCoroutineScope`, `androidx.lifecycle.LiveData`, `androidx.lifecycle.MutableLiveData`, `kotlinx.coroutines.launch`, `me.proxer.app.ui.compose.ObserveLiveDataEvent`.

In `EpisodeContentPreview`, change `bookmarkResult = null,` and `bookmarkError = null,` to `bookmarkResult = MutableLiveData(null),` and `bookmarkError = MutableLiveData(null),`.

- [ ] **Step 4: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/media/episode/EpisodeScreen.kt
git commit -m "fix(episode): fix one-shot event drop for bookmarkData/bookmarkError"
```

---

### Task 18: Fix `MessengerScreen.kt` — report handling, `draft`, `refreshError` (combined)

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/message/MessengerScreen.kt`

**Interfaces:**
- Consumes: `ObserveLiveDataEvent` (Task 1), `MessengerViewModel.refreshError` (inherited from `PagedViewModel`), `MessengerViewModel.draft: ResettingMutableLiveData<String?>`, `MessengerReportViewModel`'s `data: ResettingMutableLiveData<Unit?>` / `error: MutableLiveData<ErrorUtils.ErrorAction?>` / `isLoading: MutableLiveData<Boolean?>` (all inherited from `ReportViewModel`).

This is the largest task in the plan — three related fixes to the same file, landed together since separating them would mean touching the same lines three times. The report-handling fix mirrors `ChatScreen.kt`'s already-correct pattern exactly (adapted for `MessengerScreen`'s boolean `showReportDialog`/single-reason-string shape, which differs slightly from `ChatScreen`'s nullable-report-target shape since Messenger reports the whole conference, not a specific message).

- [ ] **Step 1: Replace the full file**

Replace the entire contents of `src/main/kotlin/me/proxer/app/chat/prv/message/MessengerScreen.kt`:

```kotlin
package me.proxer.app.chat.prv.message

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.chat.prv.LocalMessage
import me.proxer.app.chat.prv.conference.info.ConferenceInfoActivity
import me.proxer.app.profile.ProfileActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ObserveLiveDataEvent
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.view.bbcode.BBCodeView
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.distanceInWordsToNow
import me.proxer.app.util.extension.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.threeten.bp.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerScreen(
    conference: LocalConference,
    initialMessage: String? = null,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<MessengerViewModel> { parametersOf(conference) }
    val reportViewModel = koinViewModel<MessengerReportViewModel>()
    val storageHelper: StorageHelper = koinInject()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()
    val currentConference by viewModel.conference.observeAsState(initial = conference)
    val deleted by viewModel.deleted.observeAsState()
    val reportError by reportViewModel.error.observeAsState()
    val reportIsLoading by reportViewModel.isLoading.observeAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.loadDraft()
    }

    LaunchedEffect(deleted) {
        if (deleted != null) onBack()
    }

    MessengerContent(
        messages = data,
        error = error,
        isLoading = isLoading,
        conference = currentConference,
        myUserId = storageHelper.user?.id,
        draft = viewModel.draft,
        refreshError = viewModel.refreshError,
        reportData = reportViewModel.data,
        reportError = reportError,
        reportIsLoading = reportIsLoading,
        initialMessage = initialMessage,
        onBack = onBack,
        onSend = { viewModel.sendMessage(it) },
        onReport = { reason -> reportViewModel.sendReport(currentConference.id.toString(), reason) },
        onDraftUpdate = { viewModel.updateDraft(it) },
        onRetry = { viewModel.load() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessengerContent(
    messages: List<LocalMessage>?,
    error: ErrorUtils.ErrorAction?,
    isLoading: Boolean?,
    conference: LocalConference,
    myUserId: String?,
    draft: LiveData<String?>,
    refreshError: LiveData<ErrorUtils.ErrorAction?>,
    reportData: LiveData<Unit?>,
    reportError: ErrorUtils.ErrorAction?,
    reportIsLoading: Boolean?,
    initialMessage: String?,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onReport: (reason: String) -> Unit,
    onDraftUpdate: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var messageText by rememberSaveable { mutableStateOf(initialMessage ?: "") }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var reportTarget by remember { mutableStateOf<LocalConference?>(null) }
    var reportReason by remember { mutableStateOf("") }

    ObserveLiveDataEvent(draft) {
        if (messageText.isBlank()) messageText = it ?: ""
    }

    ObserveLiveDataEvent(refreshError) { err ->
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_refresh, context.getString(err.message)),
            )
        }
    }

    // reportData is a ResettingMutableLiveData - a one-shot success event, not continuous state.
    // observeAsState()+LaunchedEffect(value) would silently miss every event after the first
    // structurally-equal one (Unit == Unit always), since Compose's default state-equality policy
    // skips recomposition when the "new" value equals the current one. Only dismiss the dialog on
    // confirmed success, not eagerly on click - mirrors ChatScreen.kt's report-dialog handling.
    ObserveLiveDataEvent(reportData) {
        reportTarget = null
        reportReason = ""
        selectedIds = emptySet()
    }

    if (reportTarget != null) {
        AlertDialog(
            onDismissRequest = {
                reportTarget = null
                reportReason = ""
            },
            title = { Text(stringResource(R.string.dialog_chat_report_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        label = { Text(stringResource(R.string.dialog_chat_report_message_hint)) },
                        enabled = reportIsLoading != true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    reportError?.let { err ->
                        Text(
                            text = stringResource(err.message),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onReport(reportReason) },
                    enabled = reportIsLoading != true,
                ) {
                    Text(stringResource(R.string.dialog_chat_report_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null; reportReason = "" }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectedIds.isEmpty()) {
                TopAppBar(
                    title = {
                        TextButton(
                            onClick = {
                                val activity = context as? Activity ?: return@TextButton
                                if (conference.isGroup) {
                                    ConferenceInfoActivity.navigateTo(activity, conference)
                                } else {
                                    ProfileActivity.navigateTo(
                                        activity,
                                        null,
                                        conference.topic,
                                        conference.image,
                                    )
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                text = conference.topic,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(selectedIds.size.toString()) },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                reportTarget = conference
                                reportReason = ""
                            },
                        ) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = stringResource(R.string.action_report),
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = {
                        messageText = it
                        onDraftUpdate(it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.fragment_messenger_message)) },
                    singleLine = false,
                    maxLines = 5,
                )
                IconButton(
                    onClick = {
                        val text = messageText.trim()
                        if (text.isNotBlank()) {
                            onSend(text)
                            messageText = ""
                        }
                    },
                    enabled = messageText.trim().isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                }
            }
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading == true && messages == null,
            error = if (messages == null) error else null,
            onRetry = onRetry,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
            ) {
                items(messages ?: emptyList(), key = { it.id }) { message ->
                    val activity = context as? Activity ?: return@items
                    MessageItem(
                        message = message,
                        isOwnMessage = message.userId == myUserId,
                        isSelected = message.id in selectedIds,
                        onUsernameClick = {
                            ProfileActivity.navigateTo(activity, message.userId, message.username)
                        },
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                selectedIds = if (message.id in selectedIds) {
                                    selectedIds - message.id
                                } else {
                                    selectedIds + message.id
                                }
                            }
                        },
                        onLongClick = {
                            selectedIds = selectedIds + message.id
                        },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MessengerContentPreview() {
    val conf = LocalConference(
        id = 1L,
        topic = "Sample Chat",
        customTopic = "",
        participantAmount = 2,
        image = "",
        imageType = "",
        isGroup = false,
        localIsRead = true,
        isRead = true,
        date = Instant.EPOCH,
        unreadMessageAmount = 0,
        lastReadMessageId = "",
        isFullyLoaded = false,
    )
    ProxerTheme {
        MessengerContent(
            messages = null,
            error = null,
            isLoading = true,
            conference = conf,
            myUserId = null,
            draft = MutableLiveData(null),
            refreshError = MutableLiveData(null),
            reportData = MutableLiveData(null),
            reportError = null,
            reportIsLoading = null,
            initialMessage = null,
            onBack = {},
            onSend = {},
            onReport = {},
            onDraftUpdate = {},
            onRetry = {},
        )
    }
}

@Composable
private fun MessageItem(
    message: LocalMessage,
    isOwnMessage: Boolean,
    isSelected: Boolean,
    onUsernameClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
    ) {
        if (!isOwnMessage) {
            TextButton(
                onClick = onUsernameClick,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = message.username,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isOwnMessage) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            AndroidView(
                factory = { ctx -> BBCodeView(ctx) },
                update = { view -> view.tree = message.styledMessage },
                modifier = Modifier.padding(8.dp),
            )
        }
        Text(
            text = message.date.toLocalDateTime().distanceInWordsToNow(context),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
```

Key differences from the original: `draft` is now the raw `LiveData<String?>` passed down (not pre-extracted), consumed via `ObserveLiveDataEvent` instead of `LaunchedEffect(draft)`; `refreshError` is new; the report flow now uses a nullable `reportTarget: LocalConference?` (holding the conference being reported, mirroring `ChatScreen`'s nullable-target pattern) instead of a plain `showReportDialog: Boolean`, observes `reportError`/`reportIsLoading` for inline dialog display, and only clears state via the `reportData` `ObserveLiveDataEvent` on confirmed success — the confirm button no longer eagerly closes the dialog.

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run covering tests**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.chat.prv.message.MessengerReportViewModelTest"`
Expected: `BUILD SUCCESSFUL` (this task doesn't touch the ViewModel layer — safety check only; there's no `MessengerViewModelTest` covering `draft`/`refreshError` behavior changes since those are Screen-only observation fixes, not ViewModel logic changes).

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/chat/prv/message/MessengerScreen.kt
git commit -m "fix(messenger): fix silent report-failure swallowing, one-shot draft drop, add refreshError

The report dialog closed unconditionally on tap regardless of success/
failure - reportViewModel.data/error/isLoading were never observed.
Mirrors ChatScreen.kt's already-correct pattern: only dismiss on
confirmed success, show inline error/loading state otherwise. Also
fixed draft's one-shot-event drop and added refreshError, both
previously silent."
```

---

### Task 19: Fix `IndustryScreen.kt` link-opening failure swallowing

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/info/industry/IndustryScreen.kt`
- Modify: `src/main/res/values/strings.xml`

**Interfaces:** None (self-contained fix).

- [ ] **Step 1: Add the new string**

In `src/main/res/values/strings.xml`, add near `about_error_mail_no_activity` (line 897):
```xml
    <string name="error_open_link_no_activity">Der Link konnte nicht geöffnet werden</string>
```

- [ ] **Step 2: Fix the swallowed exception**

In `IndustryScreen.kt`, replace (lines 275-279):
```kotlin
                          .clickable {
                              runCatching {
                                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkText)))
                              }
                          },
```
with:
```kotlin
                          .clickable {
                              try {
                                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkText)))
                              } catch (_: ActivityNotFoundException) {
                                  context.toast(R.string.error_open_link_no_activity)
                              }
                          },
```

Add imports: `android.content.ActivityNotFoundException`, `me.proxer.app.util.extension.toast` (check the exact `toast` extension function's package by grepping `import.*\.toast$` in `AboutScreen.kt` — confirm it's `me.proxer.app.util.extension.toast` before adding).

- [ ] **Step 3: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/info/industry/IndustryScreen.kt src/main/res/values/strings.xml
git commit -m "fix(industry): surface link-opening failures instead of silently swallowing them

runCatching discarded the Result entirely - a malformed link or missing
handler app produced a silent no-op tap. Matches the ActivityNotFoundException
handling pattern already used in AboutScreen.kt."
```

---

### Task 20: Fix `TranslatorGroupScreen.kt` link-opening failure swallowing

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupScreen.kt`

**Interfaces:** Consumes the `error_open_link_no_activity` string added in Task 19.

- [ ] **Step 1: Fix the swallowed exception**

Same fix as Task 19, applied to `TranslatorGroupScreen.kt` (lines 256-260):
```kotlin
                          .clickable {
                              runCatching {
                                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkText)))
                              }
                          },
```
becomes:
```kotlin
                          .clickable {
                              try {
                                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkText)))
                              } catch (_: ActivityNotFoundException) {
                                  context.toast(R.string.error_open_link_no_activity)
                              }
                          },
```

Add the same two imports as Task 19.

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupScreen.kt
git commit -m "fix(translator-group): surface link-opening failures instead of silently swallowing them"
```

---

### Task 21: Add `deleteComment` failure test to `CommentsViewModelTest`

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/media/comments/CommentsViewModelTest.kt`

**Interfaces:** None (test-only).

- [ ] **Step 1: Write the failing test**

Add after the existing `` `deleteComment removes the item and deletes the draft on success` `` test (after line 222):
```kotlin
    @Test
    fun `deleteComment sets itemDeletionError on failure`() {
        val comments = fullPage("p0")
        commentsEndpoint.stubPagingSuccess(comments)
        viewModel.load()

        val target = viewModel.data.value!!.first()
        val updateEndpoint = mockk<UpdateCommentEndpoint>(relaxed = true)
        every { api.comment.update(target.id) } returns updateEndpoint
        every { updateEndpoint.comment(any()) } returns updateEndpoint
        every { updateEndpoint.rating(any()) } returns updateEndpoint
        every { updateEndpoint.build() } returns mockProxerCallNullableError()

        viewModel.deleteComment(target)

        assertNotNull(viewModel.itemDeletionError.value)
        assertEquals(comments.map { it.id }, viewModel.data.value?.map { it.id })
    }
```

This needs `mockProxerCallNullableError` imported: add `import me.proxer.app.base.mockProxerCallNullableError` to the import block (alongside the existing `import me.proxer.app.base.mockProxerCallNullableSuccess`, in correct lexicographic position — `mockProxerCallNullableError` sorts before `mockProxerCallNullableSuccess`, `E` < `S`).

- [ ] **Step 2: Run it to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.media.comments.CommentsViewModelTest"`
Expected: `BUILD SUCCESSFUL`, all tests pass including the new one.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/media/comments/CommentsViewModelTest.kt
git commit -m "test(comments): cover deleteComment's failure branch

Only the success branch was tested, despite the review-fixes pass having
made the Compose UI depend on itemDeletionError firing correctly on
failure. Mirrors BookmarkViewModelTest's equivalent failure-branch test."
```

---

### Task 22: Add `deleteComment` failure test to `ProfileCommentViewModelTest`

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/profile/comment/ProfileCommentViewModelTest.kt`

**Interfaces:** None (test-only).

- [ ] **Step 1: Write the failing test**

Add after the existing `` `deleteComment removes the item and deletes the draft on success` `` test:
```kotlin
    @Test
    fun `deleteComment sets itemDeletionError on failure`() {
        val endpoint = mockUserCommentsEndpoint()
        val comments = fullPage("p0")
        endpoint.stubPagingSuccess(comments)
        viewModel.load()

        val target = viewModel.data.value!!.first()
        val updateEndpoint = mockk<UpdateCommentEndpoint>(relaxed = true)
        every { api.comment.update(target.id) } returns updateEndpoint
        every { updateEndpoint.comment(any()) } returns updateEndpoint
        every { updateEndpoint.rating(any()) } returns updateEndpoint
        every { updateEndpoint.build() } returns mockProxerCallNullableError()

        viewModel.deleteComment(target)

        assertNotNull(viewModel.itemDeletionError.value)
        assertEquals(comments.map { it.id }, viewModel.data.value?.map { it.id })
    }
```

Add `import me.proxer.app.base.mockProxerCallNullableError` (correct lexicographic position, before `mockProxerCallNullableSuccess`).

- [ ] **Step 2: Run it to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.profile.comment.ProfileCommentViewModelTest"`
Expected: `BUILD SUCCESSFUL`, all tests pass including the new one.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/profile/comment/ProfileCommentViewModelTest.kt
git commit -m "test(profile-comments): cover deleteComment's failure branch"
```

---

### Task 23: Add direct `ResettingMutableLiveData` unit test

**Files:**
- Create: `src/test/kotlin/me/proxer/app/util/data/ResettingMutableLiveDataTest.kt`

**Interfaces:** None (test-only, no production code change).

`ResettingMutableLiveData.observe(owner, observer)` needs a real (or fake) `LifecycleOwner` whose lifecycle is in an active state (`STARTED`/`RESUMED`) for `LiveData`'s internal dispatch to actually deliver — this project has no Robolectric, so a minimal hand-rolled fake is needed.

- [ ] **Step 1: Write the test file**

```kotlin
package me.proxer.app.util.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ResettingMutableLiveDataTest {

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    private class AlwaysActiveLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        init {
            registry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `observer receives a value and the LiveData resets to null internally afterwards`() {
        val liveData = ResettingMutableLiveData<String?>()
        val owner = AlwaysActiveLifecycleOwner()
        val received = mutableListOf<String?>()

        liveData.observe(owner, Observer { received.add(it) })

        liveData.value = "first"

        assertEquals(listOf("first"), received)
        assertNull(liveData.value)
    }

    @Test
    fun `two consecutive structurally equal values are both delivered to the observer`() {
        val liveData = ResettingMutableLiveData<String?>()
        val owner = AlwaysActiveLifecycleOwner()
        val received = mutableListOf<String?>()

        liveData.observe(owner, Observer { received.add(it) })

        liveData.value = "same"
        liveData.value = "same"

        assertEquals(listOf("same", "same"), received)
    }

    @Test
    fun `setting value to null directly does not notify the observer`() {
        val liveData = ResettingMutableLiveData<String?>()
        val owner = AlwaysActiveLifecycleOwner()
        val received = mutableListOf<String?>()

        liveData.observe(owner, Observer { received.add(it) })

        liveData.value = null

        assertEquals(emptyList<String?>(), received)
    }
}
```

- [ ] **Step 2: Run it to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.util.data.ResettingMutableLiveDataTest"`
Expected: `BUILD SUCCESSFUL`, all 3 tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/util/data/ResettingMutableLiveDataTest.kt
git commit -m "test(util): add direct ResettingMutableLiveData coverage

This class is at the center of two bugs found and fixed this session
(ChatScreen's report dialog, and the wider one-shot-event drop across
15 screens), but had zero direct test coverage - only indirect coverage
through ViewModel tests that never call .observe(), so a regression to
the reset-after-delivery contract wouldn't have been caught."
```

---

### Task 24: Remove unused imports flagged by detekt

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/comment/EditCommentViewModelTest.kt`
- Modify: `src/test/kotlin/me/proxer/app/media/comments/CommentsViewModelTest.kt`
- Modify: `src/test/kotlin/me/proxer/app/profile/media/ProfileMediaListViewModelTest.kt`

**Interfaces:** None (cleanup only).

- [ ] **Step 1: Remove the unused imports**

In `EditCommentViewModelTest.kt`, remove line 18:
```kotlin
import me.proxer.app.util.extension.toLocalComment
```

In `CommentsViewModelTest.kt`, remove line 38:
```kotlin
import org.threeten.bp.Instant
```
(Note: this file was also modified by Task 21, which adds an import — apply Task 21 first if running sequentially, then remove this line from the resulting file; the line number may shift.)

In `ProfileMediaListViewModelTest.kt`, remove lines 10, 13, 14, 17:
```kotlin
import me.proxer.app.base.mockProxerCallError
import me.proxer.app.base.mockProxerCallSuccess
import me.proxer.app.base.stubError
import me.proxer.app.base.stubSuccess
```
(Keep lines 11/12/15/16 — `mockProxerCallNullableError`, `mockProxerCallNullableSuccess`, `stubPagingError`, `stubPagingSuccess` — which detekt did NOT flag, i.e. are actually used.)

- [ ] **Step 2: Verify detekt is clean on these three files**

Run: `./gradlew detekt 2>&1 | grep -E "EditCommentViewModelTest|CommentsViewModelTest|ProfileMediaListViewModelTest" | grep "NoUnusedImports"`
Expected: no output (empty).

- [ ] **Step 3: Verify the files still compile and their tests still pass**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.comment.EditCommentViewModelTest" --tests "me.proxer.app.media.comments.CommentsViewModelTest" --tests "me.proxer.app.profile.media.ProfileMediaListViewModelTest"`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/me/proxer/app/comment/EditCommentViewModelTest.kt src/test/kotlin/me/proxer/app/media/comments/CommentsViewModelTest.kt src/test/kotlin/me/proxer/app/profile/media/ProfileMediaListViewModelTest.kt
git commit -m "fix(test): remove unused imports flagged by detekt"
```

---

### Task 25: Tighten login-required assertions in `BaseViewModelTest`/`NotificationViewModelTest`

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/base/BaseViewModelTest.kt`
- Modify: `src/test/kotlin/me/proxer/app/notification/NotificationViewModelTest.kt`

**Interfaces:** None (test-only).

- [ ] **Step 1: Tighten `BaseViewModelTest`'s assertion**

Change:
```kotlin
    @Test
    fun `load surfaces a NotLoggedInException from validate() as a login-required error`() {
        every { validators.validateLogin() } throws NotLoggedInException()
        viewModel.isLoginRequired = true

        viewModel.nextResponse = Single.just("ignored")
        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }
```
to:
```kotlin
    @Test
    fun `load surfaces a NotLoggedInException from validate() as a login-required error`() {
        every { validators.validateLogin() } throws NotLoggedInException()
        viewModel.isLoginRequired = true

        viewModel.nextResponse = Single.just("ignored")
        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.LOGIN, viewModel.error.value?.buttonAction)
    }
```

Add import `me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction` in correct lexicographic position (check the existing import block — likely alphabetically near other `me.proxer.app.util.*` imports; if `assertEquals` isn't already imported from `org.junit.Assert`, add it too — check the file first, it's very likely already imported since other tests in the file use it, e.g. `` `reload loads new data after clearing` ``).

- [ ] **Step 2: Tighten `NotificationViewModelTest`'s assertion**

Change:
```kotlin
    @Test
    fun `load sets login-required error when validators rejects a logged-out user`() {
        every { validators.validateLogin() } throws NotLoggedInException()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }
```
to:
```kotlin
    @Test
    fun `load sets login-required error when validators rejects a logged-out user`() {
        every { validators.validateLogin() } throws NotLoggedInException()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.LOGIN, viewModel.error.value?.buttonAction)
    }
```

Add import `me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction` (correct lexicographic position — this file doesn't currently import it, check surrounding `me.proxer.app.util.*` imports for placement).

- [ ] **Step 3: Type-check and run both files' tests**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.base.BaseViewModelTest" --tests "me.proxer.app.notification.NotificationViewModelTest"`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/me/proxer/app/base/BaseViewModelTest.kt src/test/kotlin/me/proxer/app/notification/NotificationViewModelTest.kt
git commit -m "test: tighten login-required assertions to check ButtonAction.LOGIN

Matches the assertion strength already used by the 3 sibling
login-required tests (Anime/Bookmark/Chat) - assertNotNull(error.value)
would also pass for an unrelated error, not just the login-required one."
```

---

### Task 26: Restore `MainActivity` launch-counter `savedInstanceState` guard

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/MainActivity.kt`

**Interfaces:** None.

- [ ] **Step 1: Read the current file to confirm exact lines, then add the guard**

Read `src/main/kotlin/me/proxer/app/MainActivity.kt`'s `onCreate` method first to confirm the exact current code (it should closely match, but confirm before editing):
```kotlin
        val shouldIntroduce = preferenceHelper.launches <= 0 && intent.action == Intent.ACTION_MAIN
        if (shouldIntroduce) {
            preferenceHelper.incrementLaunches()
            IntroductionWrapper.introduce(this)
            return
        }

        if (intent.action == Intent.ACTION_MAIN) {
            preferenceHelper.incrementLaunches()
        }
```

Change to:
```kotlin
        val shouldIntroduce = savedInstanceState == null &&
            preferenceHelper.launches <= 0 &&
            intent.action == Intent.ACTION_MAIN
        if (shouldIntroduce) {
            preferenceHelper.incrementLaunches()
            IntroductionWrapper.introduce(this)
            return
        }

        if (savedInstanceState == null && intent.action == Intent.ACTION_MAIN) {
            preferenceHelper.incrementLaunches()
        }
```

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/MainActivity.kt
git commit -m "fix(main): restore savedInstanceState guard on launch counter

incrementLaunches() ran on every rotation/process-recreation since
MainActivity declares no android:configChanges, inflating the counter.
Restores the guard the pre-Compose Activity had."
```

---

### Task 27: Delete orphaned `RatingDialog`

**Files:**
- Delete: `src/main/kotlin/me/proxer/app/ui/view/RatingDialog.kt`

**Interfaces:** None.

- [ ] **Step 1: Confirm no remaining references, then delete**

Run: `grep -rn "RatingDialog" /home/asteria/Projects/ProxerAndroid/src/main/kotlin` — expected: only self-references inside `RatingDialog.kt` itself (already confirmed during plan research; re-verify before deleting in case something changed).

```bash
rm src/main/kotlin/me/proxer/app/ui/view/RatingDialog.kt
```

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add -A src/main/kotlin/me/proxer/app/ui/view/RatingDialog.kt
git commit -m "chore: remove orphaned RatingDialog

Unreferenced anywhere since the Fragment→Compose migration dropped the
launches>=3 rating-prompt trigger from MainActivity."
```

---

### Task 28: Align CI checkout action versions

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:** None.

- [ ] **Step 1: Bump the two `@v6` occurrences to `@v7`**

Change line 54 (`unit-tests` job):
```yaml
      - name: Checkout
        uses: actions/checkout@v6
```
to:
```yaml
      - name: Checkout
        uses: actions/checkout@v7
```

Change line 92 (`instrumented-tests` job) identically.

(Line 16, `analysis` job, is already `@v7` — leave unchanged.)

- [ ] **Step 2: Verify the YAML still parses**

Run: `python3 -c "import yaml; d = yaml.safe_load(open('.github/workflows/ci.yml')); print([s['uses'] for j in d['jobs'].values() for s in j['steps'] if s.get('name') == 'Checkout'])"`
Expected: `['actions/checkout@v7', 'actions/checkout@v7', 'actions/checkout@v7']`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "chore(ci): align checkout action version across all three jobs

analysis used @v7, unit-tests and instrumented-tests used @v6 -
harmless inconsistency, aligned to the newer version."
```

---

## Post-plan verification

After all 28 tasks are applied, run the full unit test suite and detekt once to catch any cross-task interaction the per-task runs missed:

```bash
./gradlew testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

```bash
./gradlew compileDebugKotlin detekt
```
Expected: `compileDebugKotlin` `BUILD SUCCESSFUL`. `detekt` will still report the ~655 pre-existing violations unrelated to this branch's files (confirmed during the prior session's final review — this repo's detekt config is permissive per CLAUDE.md and CI's `analysis` job has `continue-on-error: true`); confirm no *new* violations were introduced in any file this plan touched by re-running the same targeted grep used in Task 24's Step 2 plus a spot-check of the newly-created `ObserveLiveDataEvent.kt` and `ResettingMutableLiveDataTest.kt`.
