# Compose Migration Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the Critical + Important findings from the compose-migration PR review (spec: `docs/superpowers/specs/2026-07-10-compose-migration-review-fixes-design.md`) and restore CI + InAppUpdateFlow per owner directive.

**Architecture:** 17 independent-ish tasks grouped by spec section (A-H). Most tasks touch one Compose screen or one test file and have no dependency on each other; Task 4 depends on Task 3 (needs the new `AgeConfirmationDialog` composable). Tasks can be executed and reviewed in any order except that constraint.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), RxJava 2, Koin 4.2.1 DI, JUnit4 + MockK unit tests, GitHub Actions CI.

## Global Constraints

- Always use `./gradlew`, never system `gradle`; keep the Gradle daemon on (no `--no-daemon`).
- `secrets.properties` must exist for any Gradle invocation — if missing: `echo "PROXER_API_KEY=dummy_build_key" > secrets.properties` (never overwrite an existing one).
- Fast type-check: `./gradlew compileDebugKotlin` (no `:app:` prefix, this project's source root is `src/`, no `app/` subdir).
- Unit tests: `./gradlew testDebugUnitTest --tests "fully.qualified.ClassName"` (the plain `test` task doesn't accept `--tests` here). Never run two `test*` invocations concurrently on the same checkout.
- In composables, inject dependencies via `koinInject<T>()` — `get()`/`sharedViewModel()` don't exist in Koin 4.x.
- All RxJava subscriptions must be disposed — in Compose, via `DisposableEffect { ... onDispose { disposable.dispose() } }` (not AutoDispose, which is Fragment/Activity-scoped only).
- `BaseViewModel.load()` is not auto-invoked by Compose screens — every screen must call `LaunchedEffect(Unit) { viewModel.load() }` itself. This already holds everywhere touched by this plan; do not remove it.
- Follow existing repo commit style: `type: short description` (`feat:`, `fix:`, `test:`, `docs:`, `chore:`), no body unless the why isn't obvious from the diff.

---

### Task 1: Fix CI job graph (`needs: build` dangling reference)

**Files:**
- Modify: `.github/workflows/ci.yml:86-89`

**Interfaces:** None (standalone YAML fix, no code dependencies).

- [ ] **Step 1: Remove the dangling `needs: build` line**

Current (lines 86-89):
```yaml
  instrumented-tests:
    runs-on: ubuntu-latest
    needs: build
    continue-on-error: true
```

New:
```yaml
  instrumented-tests:
    runs-on: ubuntu-latest
    continue-on-error: true
```

- [ ] **Step 2: Verify the YAML still parses and no other job references `build`**

Run: `python3 -c "import yaml; d = yaml.safe_load(open('.github/workflows/ci.yml')); print(list(d['jobs'].keys())); assert all('build' not in (j.get('needs') or []) for j in d['jobs'].values())"`
Expected: prints `['analysis', 'unit-tests', 'instrumented-tests']` and does not raise `AssertionError`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "fix(ci): remove needs: build reference to a job that no longer exists

The build job (assembleDebug/assembleRelease) was already removed; the
dangling needs: build on instrumented-tests invalidated the entire
workflow graph, so analysis and unit-tests silently never ran either.
connectedDebugAndroidTest assembles its own APKs, so no build dependency
is needed. CI stays test/analysis-only per project decision."
```

---

### Task 2: Restore Media/Profile deep-link tab selection

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/media/MediaActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/media/MediaScreen.kt:42,63`
- Modify: `src/main/kotlin/me/proxer/app/profile/ProfileActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/profile/ProfileScreen.kt:43,66`

**Interfaces:**
- Produces: `MediaScreen(id: String, name: String, initialTab: Int = 0, onBack: () -> Unit)`, `ProfileScreen(userId: String?, username: String?, initialTab: Int = 0, onBack: () -> Unit)` — new optional `initialTab` param on both, defaulting to 0 so all other existing call sites keep compiling unchanged.

- [ ] **Step 1: Add `initialTab` to `MediaActivity`**

In `src/main/kotlin/me/proxer/app/media/MediaActivity.kt`, add a computed property mirroring master's `customItemToDisplay`, and pass it through. Insert after the existing `category` val (after line 58):

```kotlin
    private val initialTab: Int
        get() = when (intent.action) {
            Intent.ACTION_VIEW -> when (intent.data?.pathSegments?.getOrNull(2)) {
                "comments" -> 1
                "episodes", "list" -> 2
                "relation" -> 3
                "recommendations" -> 4
                "forum" -> 5
                else -> 0
            }
            else -> 0
        }
```

Then change the `setContent` block (currently lines 63-71):
```kotlin
        setContent {
            ProxerTheme {
                MediaScreen(
                    id = id,
                    name = name ?: "",
                    initialTab = initialTab,
                    onBack = { finish() },
                )
            }
        }
```

- [ ] **Step 2: Thread `initialTab` through `MediaScreen`**

In `src/main/kotlin/me/proxer/app/media/MediaScreen.kt`, change the function signature (line 42):
```kotlin
fun MediaScreen(id: String, name: String, initialTab: Int = 0, onBack: () -> Unit) {
```

And change the pager state creation (line 60):
```kotlin
    val pagerState = rememberPagerState(initialPage = initialTab) { tabs.size }
```

- [ ] **Step 3: Add `initialTab` to `ProfileActivity`**

In `src/main/kotlin/me/proxer/app/profile/ProfileActivity.kt`, add after the `username` val (after line 48):
```kotlin
    private val initialTab: Int
        get() = when (intent.action) {
            Intent.ACTION_VIEW -> when (intent.data?.pathSegments?.getOrNull(2)) {
                "about" -> 1
                "anime" -> 3
                "manga" -> 4
                "chronik" -> 6
                else -> 0
            }
            else -> 0
        }
```

Note: `"chronik"` (history) maps to index **6**, not 5 — the pre-migration `ProfileActivity` mapped it to 5, but the current `ProfileScreen` tab order is `Info(0), About(1), TopTen(2), Anime(3), Manga(4), Comments(5), History(6)`, which has an extra "Comments" tab the old pager didn't have at that position. Copying the old integer verbatim would silently open the wrong tab.

Then change the `setContent` block (currently lines 53-61):
```kotlin
        setContent {
            ProxerTheme {
                ProfileScreen(
                    userId = userId,
                    username = username,
                    initialTab = initialTab,
                    onBack = { finish() },
                )
            }
        }
```

- [ ] **Step 4: Thread `initialTab` through `ProfileScreen`**

In `src/main/kotlin/me/proxer/app/profile/ProfileScreen.kt`, change the function signature (line 43):
```kotlin
fun ProfileScreen(userId: String?, username: String?, initialTab: Int = 0, onBack: () -> Unit) {
```

And change the pager state creation (line 63):
```kotlin
    val pagerState = rememberPagerState(initialPage = initialTab) { tabs.size }
```

- [ ] **Step 5: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Manual verification**

Install a debug build, then from `adb shell`: `am start -a android.intent.action.VIEW -d "https://proxer.me/info/12345/comments"` (any real entry id) — confirm the Comments tab is selected on open, not Info. Repeat for a profile URL with `/chronik` and confirm History (not Comments) opens.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/me/proxer/app/media/MediaActivity.kt src/main/kotlin/me/proxer/app/media/MediaScreen.kt src/main/kotlin/me/proxer/app/profile/ProfileActivity.kt src/main/kotlin/me/proxer/app/profile/ProfileScreen.kt
git commit -m "fix(deeplink): restore sub-section tab selection for Media/Profile

Compose migration dropped intent.data path-segment parsing, so deep
links like /info/{id}/comments always opened the Info tab. Ports the
old mapping, corrected for ProfileScreen's new tab order (History
moved from index 5 to 6 after Comments was inserted)."
```

---

### Task 3: Migrate `AgeConfirmationDialog` to Compose

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/settings/AgeConfirmationDialog.kt` (full rewrite, Fragment → composable)
- Modify: `src/main/kotlin/me/proxer/app/settings/SettingsScreen.kt:134-153`

**Interfaces:**
- Produces: `@Composable fun AgeConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit = {})` in package `me.proxer.app.settings` — sets `preferenceHelper.isAgeRestrictedMediaAllowed = true` and calls `onConfirm()` then `onDismiss()` on confirm; calls `onDismiss()` on cancel/dismiss.
- Consumed by: Task 4 (`ContentScreen`), and this task's own `SettingsScreen` update.

- [ ] **Step 1: Rewrite `AgeConfirmationDialog.kt` as a composable**

Replace the entire contents of `src/main/kotlin/me/proxer/app/settings/AgeConfirmationDialog.kt`:

```kotlin
package me.proxer.app.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import me.proxer.app.R
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.data.PreferenceHelper
import org.koin.compose.koinInject

@Composable
fun AgeConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit = {}) {
    val preferenceHelper = koinInject<PreferenceHelper>()

    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.dialog_age_confirmation_content)) },
        confirmButton = {
            TextButton(
                onClick = {
                    preferenceHelper.isAgeRestrictedMediaAllowed = true
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.dialog_age_confirmation_positive))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AgeConfirmationDialogPreview() {
    ProxerTheme {
        AgeConfirmationDialog(onDismiss = {})
    }
}
```

Note: `com.afollestad.materialdialogs` (the old dialog's dependency) stays in `build.gradle` — it's still used by `AppRequiredDialog.kt`, `LinkCheckDialog.kt`, and `RatingDialog.kt`, none of which are in scope here.

- [ ] **Step 2: Update `SettingsScreen.kt` to use the shared composable**

In `src/main/kotlin/me/proxer/app/settings/SettingsScreen.kt`, replace lines 134-153:

Old:
```kotlin
    if (showAgeConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showAgeConfirmationDialog = false },
            text = { Text(stringResource(R.string.dialog_age_confirmation_content)) },
            confirmButton = {
                TextButton(onClick = {
                    preferenceHelper.isAgeRestrictedMediaAllowed = true
                    ageRestricted = true
                    showAgeConfirmationDialog = false
                }) {
                    Text(stringResource(R.string.dialog_age_confirmation_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAgeConfirmationDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
```

New:
```kotlin
    if (showAgeConfirmationDialog) {
        AgeConfirmationDialog(
            onDismiss = { showAgeConfirmationDialog = false },
            onConfirm = { ageRestricted = true },
        )
    }
```

`AlertDialog`/`TextButton`/`Text` imports in `SettingsScreen.kt` stay — they're still used by the other dialogs in the same file (`showStartPageDialog`, etc.).

- [ ] **Step 3: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Manual verification**

Open Settings, tap the age-restricted-media toggle, confirm the dialog still shows and confirming it flips the switch and dismisses.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/settings/AgeConfirmationDialog.kt src/main/kotlin/me/proxer/app/settings/SettingsScreen.kt
git commit -m "feat(settings): migrate AgeConfirmationDialog to Compose

Replaces the legacy BaseDialog/MaterialDialog Fragment with a
composable matching LoginDialog/LogoutDialog's shape, and de-dupes
SettingsScreen's own inline copy of the same dialog. Needed so
ContentScreen (next commit) can drive it from local remember state
the way it already drives LoginDialog."
```

---

### Task 4: Wire `ContentScreen` error-action dispatch (depends on Task 3)

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/util/ErrorUtils.kt:6,21,591-621`
- Modify: `src/main/kotlin/me/proxer/app/ui/compose/ContentScreen.kt`

**Interfaces:**
- Consumes: `AgeConfirmationDialog(onDismiss, onConfirm)` from Task 3; existing `LoginDialog(onDismiss)`.
- Produces: `ErrorUtils.ErrorAction.toClickListener(activity: BaseActivity): (() -> Unit)?` (signature change from `View.OnClickListener?`) — no other current caller in the codebase (verified: only `toIntent()` is called elsewhere, by the two widget workers, and `toClickListener` has zero callers before this change).

- [ ] **Step 1: Trim and retype `ErrorAction.toClickListener` in `ErrorUtils.kt`**

Remove the now-unused import (line 6):
```kotlin
import android.view.View
```

Remove the `AgeConfirmationDialog` import (line 21):
```kotlin
import me.proxer.app.settings.AgeConfirmationDialog
```

Replace `toClickListener` (currently lines 591-621):

Old:
```kotlin
        fun toClickListener(activity: BaseActivity) = when (buttonAction) {
            CAPTCHA -> {
                View.OnClickListener {
                    activity.showPage(ProxerUrls.captchaWeb(Utils.getIpAddress(), Device.MOBILE), skipCheck = true)
                }
            }

            NETWORK_SETTINGS -> {
                View.OnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        activity.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                    } else {
                        activity.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                    }
                }
            }

            AGE_CONFIRMATION -> {
                View.OnClickListener { AgeConfirmationDialog.show(activity) }
            }

            OPEN_LINK -> {
                data[LINK_DATA_KEY].let { link ->
                    when (link) {
                        is HttpUrl -> View.OnClickListener { activity.showPage(link, skipCheck = true) }
                        else -> null
                    }
                }
            }

            else -> {
                null
            }
        }
```

New:
```kotlin
        fun toClickListener(activity: BaseActivity): (() -> Unit)? = when (buttonAction) {
            CAPTCHA -> {
                {
                    activity.showPage(ProxerUrls.captchaWeb(Utils.getIpAddress(), Device.MOBILE), skipCheck = true)
                }
            }

            NETWORK_SETTINGS -> {
                {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        activity.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                    } else {
                        activity.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                    }
                }
            }

            OPEN_LINK -> {
                data[LINK_DATA_KEY].let { link ->
                    when (link) {
                        is HttpUrl -> {
                            { activity.showPage(link, skipCheck = true) }
                        }
                        else -> null
                    }
                }
            }

            else -> {
                null
            }
        }
```

(`AGE_CONFIRMATION` and `LOGIN` branches are gone — `ContentScreen` now handles both directly with Compose dialog state, since neither can be expressed as a plain `BaseActivity` side effect.)

- [ ] **Step 2: Wire `ContentScreen.kt` to dispatch on `buttonAction`**

Replace the full contents of `src/main/kotlin/me/proxer/app/ui/compose/ContentScreen.kt`:

```kotlin
package me.proxer.app.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.proxer.app.R
import me.proxer.app.auth.LoginDialog
import me.proxer.app.base.BaseActivity
import me.proxer.app.settings.AgeConfirmationDialog
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
import me.proxer.app.util.ErrorUtils.ErrorAction.Companion.ACTION_MESSAGE_DEFAULT
import me.proxer.app.util.ErrorUtils.ErrorAction.Companion.ACTION_MESSAGE_HIDE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentScreen(
    isLoading: Boolean,
    error: ErrorAction?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isSwipeToRefreshEnabled: Boolean = false,
    onRefresh: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var showLoginDialog by remember { mutableStateOf(false) }
    var showAgeConfirmationDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        LoginDialog(onDismiss = { showLoginDialog = false })
    }
    if (showAgeConfirmationDialog) {
        AgeConfirmationDialog(onDismiss = { showAgeConfirmationDialog = false })
    }

    PullToRefreshBox(
        isRefreshing = isLoading && isSwipeToRefreshEnabled,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            isLoading && !isSwipeToRefreshEnabled -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(error.message))
                        if (error.buttonMessage != ACTION_MESSAGE_HIDE) {
                            Button(
                                onClick = {
                                    when (error.buttonAction) {
                                        ButtonAction.LOGIN -> showLoginDialog = true
                                        ButtonAction.AGE_CONFIRMATION -> showAgeConfirmationDialog = true
                                        ButtonAction.CAPTCHA,
                                        ButtonAction.NETWORK_SETTINGS,
                                        ButtonAction.OPEN_LINK,
                                        -> {
                                            (context as? BaseActivity)?.let { activity ->
                                                error.toClickListener(activity)?.invoke()
                                            }
                                        }
                                        else -> onRetry()
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                val label = when (error.buttonMessage) {
                                    ACTION_MESSAGE_DEFAULT -> stringResource(R.string.error_action_retry)
                                    else -> stringResource(error.buttonMessage)
                                }
                                Text(label)
                            }
                        }
                    }
                }
            }
            else -> content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContentScreenLoadingPreview() {
    ProxerTheme {
        ContentScreen(isLoading = true, error = null, onRetry = {}) {
            Text("Content")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContentScreenErrorPreview() {
    ProxerTheme {
        ContentScreen(
            isLoading = false,
            error = ErrorAction(R.string.error_unknown),
            onRetry = {},
        ) {
            Text("Content")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContentScreenContentPreview() {
    ProxerTheme {
        ContentScreen(isLoading = false, error = null, onRetry = {}) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Content loaded!")
            }
        }
    }
}
```

(Only change to the three `@Preview` functions is none — they're reproduced verbatim so the file is complete and compiles; `ProxerTheme` is already in the same package so no new import is needed for it.)

- [ ] **Step 3: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run the existing unit test suite to catch any regression in `ErrorUtils` consumers**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.util.ErrorUtilsTest"`
Expected: `BUILD SUCCESSFUL`, all tests pass (this test file doesn't call `toClickListener` today, so it should be unaffected — this step is a safety check).

- [ ] **Step 5: Manual verification**

Exercise each `buttonAction` path on a real device/emulator:
- Log out, open a login-gated screen (e.g. Bookmarks) — error button should say "Anmelden" and open `LoginDialog`.
- View an age-restricted anime/manga entry without confirmation — button should say "Bestätigen" and open the age-confirmation dialog; confirming should make the screen reload with content.
- Turn off network, trigger a load — button should say "Netzwerkeinstellungen" (or similar) and open Android's network settings.
- Trigger a manga link error (or inspect via debugger) — button should open the external chapter link.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/me/proxer/app/util/ErrorUtils.kt src/main/kotlin/me/proxer/app/ui/compose/ContentScreen.kt
git commit -m "fix(errors): wire ContentScreen's error button to buttonAction

ContentScreen's error button was hardcoded to onRetry regardless of
error.buttonAction, so login/age-confirmation/captcha/network-settings/
manga-link recovery were all unreachable from the error UI across every
screen using ContentScreen (~30 screens) — the button just retried the
same failing request forever. LOGIN and AGE_CONFIRMATION now open their
Compose dialogs directly from local state (mirroring how MainScreen
already drives LoginDialog); CAPTCHA/NETWORK_SETTINGS/OPEN_LINK still
go through ErrorAction.toClickListener, retyped from View.OnClickListener
to a plain () -> Unit since Compose has no View to hand it."
```

---

### Task 5: Wire `ChatScreen` send-error and report-dialog observation

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/chat/pub/message/ChatScreen.kt`

**Interfaces:**
- Consumes: `ChatViewModel.sendMessageError: ResettingMutableLiveData<ErrorUtils.ErrorAction?>` (existing, line 41), `ReportViewModel.data: MutableLiveData<Unit?>` / `.error: MutableLiveData<ErrorUtils.ErrorAction?>` / `.isLoading: MutableLiveData<Boolean?>` (existing, `ChatReportViewModel` inherits these from `ReportViewModel`).

- [ ] **Step 1: Observe `sendMessageError` and `reportViewModel`'s state in the public `ChatScreen` composable**

In `src/main/kotlin/me/proxer/app/chat/pub/message/ChatScreen.kt`, change the public `ChatScreen` function (currently lines 69-116):

Old:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatRoomId: String,
    chatRoomName: String,
    chatRoomIsReadOnly: Boolean,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<ChatViewModel> { parametersOf(chatRoomId) }
    val reportViewModel = koinViewModel<ChatReportViewModel>()
    val storageHelper: StorageHelper = koinInject()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.loadDraft()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.resumePolling()
                Lifecycle.Event.ON_PAUSE -> viewModel.pausePolling()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ChatScreenContent(
        messages = data,
        error = error,
        isLoading = isLoading,
        chatRoomId = chatRoomId,
        chatRoomName = chatRoomName,
        chatRoomIsReadOnly = chatRoomIsReadOnly,
        myUserId = storageHelper.user?.id,
        isLoggedIn = storageHelper.isLoggedIn,
        onBack = onBack,
        onSend = { viewModel.sendMessage(it) },
        onReport = { messageId, reason -> reportViewModel.sendReport(messageId, reason) },
        onDraftUpdate = { viewModel.updateDraft(it) },
        onRetry = { viewModel.load() },
    )
}
```

New:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatRoomId: String,
    chatRoomName: String,
    chatRoomIsReadOnly: Boolean,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<ChatViewModel> { parametersOf(chatRoomId) }
    val reportViewModel = koinViewModel<ChatReportViewModel>()
    val storageHelper: StorageHelper = koinInject()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState()
    val sendMessageError by viewModel.sendMessageError.observeAsState()
    val reportData by reportViewModel.data.observeAsState()
    val reportError by reportViewModel.error.observeAsState()
    val reportIsLoading by reportViewModel.isLoading.observeAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.loadDraft()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.resumePolling()
                Lifecycle.Event.ON_PAUSE -> viewModel.pausePolling()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ChatScreenContent(
        messages = data,
        error = error,
        isLoading = isLoading,
        sendMessageError = sendMessageError,
        reportData = reportData,
        reportError = reportError,
        reportIsLoading = reportIsLoading,
        chatRoomId = chatRoomId,
        chatRoomName = chatRoomName,
        chatRoomIsReadOnly = chatRoomIsReadOnly,
        myUserId = storageHelper.user?.id,
        isLoggedIn = storageHelper.isLoggedIn,
        onBack = onBack,
        onSend = { viewModel.sendMessage(it) },
        onReport = { messageId, reason -> reportViewModel.sendReport(messageId, reason) },
        onDraftUpdate = { viewModel.updateDraft(it) },
        onRetry = { viewModel.load() },
    )
}
```

- [ ] **Step 2: Add the new params, a `SnackbarHostState`, and error-observing `LaunchedEffect`s to `ChatScreenContent`**

Change the `ChatScreenContent` signature (currently lines 118-134):

Old:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreenContent(
    messages: List<ParsedChatMessage>?,
    error: ErrorUtils.ErrorAction?,
    isLoading: Boolean?,
    chatRoomId: String,
    chatRoomName: String,
    chatRoomIsReadOnly: Boolean,
    myUserId: String?,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onReport: (messageId: String, reason: String) -> Unit,
    onDraftUpdate: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    var messageText by rememberSaveable { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var reportTarget by remember { mutableStateOf<ParsedChatMessage?>(null) }
    var reportReason by remember { mutableStateOf("") }

    val inputEnabled = !chatRoomIsReadOnly && isLoggedIn && !messages.isNullOrEmpty()

    if (reportTarget != null) {
        AlertDialog(
            onDismissRequest = {
                reportTarget = null
                reportReason = ""
            },
            title = { Text(stringResource(R.string.dialog_chat_report_title)) },
            text = {
                OutlinedTextField(
                    value = reportReason,
                    onValueChange = { reportReason = it },
                    label = { Text(stringResource(R.string.dialog_chat_report_message_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReport(reportTarget!!.id, reportReason)
                        reportTarget = null
                        reportReason = ""
                        selectedIds = emptySet()
                    },
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
```

New:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreenContent(
    messages: List<ParsedChatMessage>?,
    error: ErrorUtils.ErrorAction?,
    isLoading: Boolean?,
    sendMessageError: ErrorUtils.ErrorAction?,
    reportData: Unit?,
    reportError: ErrorUtils.ErrorAction?,
    reportIsLoading: Boolean?,
    chatRoomId: String,
    chatRoomName: String,
    chatRoomIsReadOnly: Boolean,
    myUserId: String?,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onReport: (messageId: String, reason: String) -> Unit,
    onDraftUpdate: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var messageText by rememberSaveable { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var reportTarget by remember { mutableStateOf<ParsedChatMessage?>(null) }
    var reportReason by remember { mutableStateOf("") }

    val inputEnabled = !chatRoomIsReadOnly && isLoggedIn && !messages.isNullOrEmpty()

    LaunchedEffect(sendMessageError) {
        val err = sendMessageError
        if (err != null) {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_chat_send_message, context.getString(err.message)),
            )
        }
    }

    LaunchedEffect(reportData) {
        if (reportData != null) {
            reportTarget = null
            reportReason = ""
            selectedIds = emptySet()
        }
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
                    onClick = { onReport(reportTarget!!.id, reportReason) },
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
```

- [ ] **Step 3: Add the `SnackbarHost` to the existing `Scaffold`**

In the same function, change the `Scaffold(...)` call (currently starting at line 178, no `snackbarHost` param today):

Old:
```kotlin
    Scaffold(
        topBar = {
```

New:
```kotlin
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
```

(Leave everything else in the `Scaffold` call — `bottomBar`, the trailing `{ padding -> ... }` content lambda — unchanged.)

- [ ] **Step 4: Add the new imports**

Add to the import block in `ChatScreen.kt`:
```kotlin
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
```
(`Column` and `MaterialTheme` are already imported in this file.)

- [ ] **Step 5: Update `ChatScreenContentPreview` for the new required params**

`ChatScreenContent` now has four new params with no default value (`sendMessageError`, `reportData`, `reportError`, `reportIsLoading`), so the existing preview call site won't compile until it passes them too. Change `ChatScreenContentPreview` (near the bottom of the file):

Old:
```kotlin
@Preview(showBackground = true)
@Composable
private fun ChatScreenContentPreview() {
    ProxerTheme {
        ChatScreenContent(
            messages = null,
            error = null,
            isLoading = true,
            chatRoomId = "1",
            chatRoomName = "General",
            chatRoomIsReadOnly = false,
            myUserId = null,
            isLoggedIn = false,
            onBack = {},
            onSend = {},
            onReport = { _, _ -> },
            onDraftUpdate = {},
            onRetry = {},
        )
    }
}
```

New:
```kotlin
@Preview(showBackground = true)
@Composable
private fun ChatScreenContentPreview() {
    ProxerTheme {
        ChatScreenContent(
            messages = null,
            error = null,
            isLoading = true,
            sendMessageError = null,
            reportData = null,
            reportError = null,
            reportIsLoading = null,
            chatRoomId = "1",
            chatRoomName = "General",
            chatRoomIsReadOnly = false,
            myUserId = null,
            isLoggedIn = false,
            onBack = {},
            onSend = {},
            onReport = { _, _ -> },
            onDraftUpdate = {},
            onRetry = {},
        )
    }
}
```

- [ ] **Step 6: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Confirm the existing ViewModel-level test for this behavior still passes**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.chat.pub.message.ChatViewModelTest"`
Expected: `BUILD SUCCESSFUL` — in particular `sendMessage optimistically prepends the message and clears it on failure` (already asserts `viewModel.sendMessageError.value` is set) should still pass unchanged; this task doesn't touch the ViewModel.

- [ ] **Step 8: Manual verification**

Send a chat message with the device offline (or mock a failing endpoint) — confirm a snackbar appears instead of the message just silently vanishing. Long-press a message to select it, tap the report flag, submit with a failing report — confirm the dialog stays open and shows the error instead of closing.

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/me/proxer/app/chat/pub/message/ChatScreen.kt
git commit -m "fix(chat): surface send-message and report failures instead of silently dropping them

sendMessageError was already set by ChatViewModel on send failure but
never observed - a failed send just silently stripped the optimistic
message from the log. The report dialog also closed unconditionally on
tap regardless of whether the report actually succeeded server-side.
Both now show feedback via a SnackbarHostState/inline error, mirroring
the pattern already used in EpisodeScreen/MediaInfoScreen."
```

---

### Task 6: Wire `TopTenScreen` `itemDeletionError`

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/profile/topten/TopTenScreen.kt`

**Interfaces:**
- Consumes: `TopTenViewModel.itemDeletionError: ResettingMutableLiveData<ErrorUtils.ErrorAction?>` (existing, line 58).

- [ ] **Step 1: Observe `itemDeletionError` and surface it via a `SnackbarHostState`**

Replace the full contents of `src/main/kotlin/me/proxer/app/profile/topten/TopTenScreen.kt` from the top through the end of `TopTenContent` (lines 1-74):

```kotlin
package me.proxer.app.profile.topten

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.proxer.app.R
import me.proxer.app.media.MediaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TopTenScreen(userId: String?, username: String?) {
    val viewModel = koinViewModel<TopTenViewModel> { parametersOf(userId, username) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val itemDeletionError by viewModel.itemDeletionError.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    TopTenContent(
        data = data,
        error = error,
        isLoading = isLoading == true,
        itemDeletionError = itemDeletionError,
        onRetry = { viewModel.load() },
        onDelete = { viewModel.addItemToDelete(it) },
    )
}

@Composable
private fun TopTenContent(
    data: TopTenViewModel.ZippedTopTenResult?,
    error: ErrorAction?,
    isLoading: Boolean,
    itemDeletionError: ErrorAction?,
    onRetry: () -> Unit,
    onDelete: (LocalTopTenEntry) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(itemDeletionError) {
        val err = itemDeletionError
        if (err != null) {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_top_ten_deletion, context.getString(err.message)),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ContentScreen(
            isLoading = isLoading,
            error = error,
            onRetry = onRetry,
        ) {
            if (data != null) {
                TopTenBody(data = data, onDelete = onDelete)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
```

(`TopTenGrid`, `TopTenCard`, and the trailing `TopTenContentPreview` stay exactly as they are today — not reproduced here, do not delete them.)

- [ ] **Step 2: Update the preview to pass the new param**

In the existing `TopTenContentPreview` (near the bottom of the file), add `itemDeletionError = null,` to the `TopTenContent(...)` call.

- [ ] **Step 3: Add the `error_top_ten_deletion` string**

Confirmed not present today (`grep -n "error_top_ten_deletion" src/main/res/values/strings.xml` returns nothing). Add it in `src/main/res/values/strings.xml` right after line 194 (`error_bookmark_deletion`):
```xml
    <string name="error_top_ten_deletion">Der Eintrag konnte nicht gelöscht werden: %s</string>
```

- [ ] **Step 4: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Manual verification**

On a profile's Top Ten tab (your own profile, logged in), tap delete on an entry with the network disabled — confirm a snackbar appears instead of nothing happening.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/me/proxer/app/profile/topten/TopTenScreen.kt src/main/res/values/strings.xml
git commit -m "fix(topten): surface itemDeletionError instead of silently failing

Deleting a Top Ten entry that fails server-side previously showed
nothing - the item just stayed and no explanation was given. Wires the
existing itemDeletionError LiveData to a SnackbarHostState, matching
the pattern already used in EpisodeScreen/MediaInfoScreen."
```

---

### Task 7: Wire `ProfileMediaListScreen` `itemDeletionError` and add the missing delete trigger

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/profile/media/ProfileMediaListScreen.kt`

**Interfaces:**
- Consumes: `ProfileMediaListViewModel.itemDeletionError: ResettingMutableLiveData<ErrorUtils.ErrorAction?>` (existing, line 63), `ProfileMediaListViewModel.addItemToDelete(item: LocalUserMediaListEntry)` (existing).

Note: this screen's `onDelete` callback is threaded all the way to `MediaListCard` but **never actually invoked** by anything in that composable — there is no delete UI at all today, so simply observing the error wouldn't fix anything reachable. This task adds a small delete icon button to each card (matching `TopTenScreen`'s existing delete-button precedent) in addition to wiring the error.

- [ ] **Step 1: Replace the full file**

Replace the full contents of `src/main/kotlin/me/proxer/app/profile/media/ProfileMediaListScreen.kt`:

```kotlin
package me.proxer.app.profile.media

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.proxer.app.R
import me.proxer.app.media.MediaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.extension.toAppString
import me.proxer.app.util.extension.toCategory
import me.proxer.app.util.extension.toEpisodeAppString
import me.proxer.library.enums.Category
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ProfileMediaListScreen(userId: String?, username: String?, category: Category) {
    val viewModel = koinViewModel<ProfileMediaListViewModel>(
        key = "profile_media_${category.name}",
    ) { parametersOf(userId, username, category, null) }

    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val itemDeletionError by viewModel.itemDeletionError.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    ProfileMediaListContent(
        data = data,
        error = error,
        isLoading = isLoading == true,
        itemDeletionError = itemDeletionError,
        onRetry = { viewModel.load() },
        onLoadMore = { viewModel.loadIfPossible() },
        onDelete = { viewModel.addItemToDelete(it) },
    )
}

@Composable
private fun ProfileMediaListContent(
    data: List<LocalUserMediaListEntry>?,
    error: ErrorAction?,
    isLoading: Boolean,
    itemDeletionError: ErrorAction?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onDelete: (LocalUserMediaListEntry) -> Unit,
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(gridState.layoutInfo) {
        val total = gridState.layoutInfo.totalItemsCount
        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) onLoadMore()
    }

    LaunchedEffect(itemDeletionError) {
        val err = itemDeletionError
        if (err != null) {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_profile_media_list_deletion, context.getString(err.message)),
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
                    MediaListCard(
                        entry = entry,
                        onDelete = onDelete,
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MediaListCard(entry: LocalUserMediaListEntry, onDelete: (LocalUserMediaListEntry) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    Card(
        onClick = {
            if (activity != null) {
                MediaActivity.navigateTo(activity, entry.id, entry.name, entry.medium.toCategory())
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            AsyncImage(
                model = ProxerUrls.entryImage(entry.id).toString(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.8f),
            )
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                text = entry.medium.toAppString(context),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                text = entry.mediaProgress.toEpisodeAppString(context, entry.episode, entry.medium.toCategory()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            IconButton(onClick = { onDelete(entry) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileMediaListContentPreview() {
    ProxerTheme {
        ProfileMediaListContent(
            data = null,
            error = null,
            isLoading = true,
            itemDeletionError = null,
            onRetry = {},
            onLoadMore = {},
            onDelete = {},
        )
    }
}
```

- [ ] **Step 2: Add the two new strings**

Confirmed neither exists today (`grep -n "action_delete\b\|error_profile_media_list_deletion" src/main/res/values/strings.xml` returns nothing — note `action_delete_all` at line 917 is a different key). In `src/main/res/values/strings.xml`, add `action_delete` right before `action_delete_all` (line 917):
```xml
    <string name="action_delete">Löschen</string>
    <string name="action_delete_all">Alle löschen</string>
```
And add `error_profile_media_list_deletion` right after `error_top_ten_deletion` (added by Task 6, near line 195):
```xml
    <string name="error_profile_media_list_deletion">Der Eintrag konnte nicht gelöscht werden: %s</string>
```

- [ ] **Step 3: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Manual verification**

On your own profile's Anime or Manga list tab, confirm a delete icon now appears on each card, and tapping it with the network disabled shows a snackbar.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/profile/media/ProfileMediaListScreen.kt src/main/res/values/strings.xml
git commit -m "fix(profile-media): add missing delete trigger and surface itemDeletionError

MediaListCard accepted an onDelete callback but never called it from
anywhere, so this screen's deletion feature was completely unreachable.
Adds a delete icon button (matching TopTenScreen's existing delete
button) and wires itemDeletionError to a snackbar."
```

---

### Task 8: Restore `InAppUpdateFlow` under the Compose-only Activity

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/util/InAppUpdateFlow.kt`
- Modify: `src/main/kotlin/me/proxer/app/ui/compose/MainScreen.kt`

**Interfaces:**
- Produces: `InAppUpdateFlow.start(activity: Activity, onUpdateAvailable: (download: () -> Unit) -> Unit, onUpdateReady: (install: () -> Unit) -> Unit)` (signature change — drops `rootView: ViewGroup`, drops internal `Snackbar` construction, adds two callbacks). `InAppUpdateFlow.stop()` keeps its existing signature.

- [ ] **Step 1: Rewrite `InAppUpdateFlow.kt` to use callbacks instead of a `ViewGroup`/`Snackbar`**

Replace the full contents of `src/main/kotlin/me/proxer/app/util/InAppUpdateFlow.kt`:

```kotlin
package me.proxer.app.util

import android.app.Activity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import timber.log.Timber

/**
 * @author Ruben Gees
 */
class InAppUpdateFlow {
    companion object {
        const val REQUEST_CODE = 5276
    }

    private var appUpdateManager: AppUpdateManager? = null

    private lateinit var successListener: OnSuccessListener<in AppUpdateInfo>
    private var progressListener: InstallStateUpdatedListener? = null
    private var failureListener: OnFailureListener? = null

    fun start(activity: Activity, onUpdateAvailable: (download: () -> Unit) -> Unit, onUpdateReady: (install: () -> Unit) -> Unit) {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity) == ConnectionResult.SUCCESS) {
            appUpdateManager =
                AppUpdateManagerFactory.create(activity).also { appUpdateManager ->
                    successListener = successListener(activity, appUpdateManager, onUpdateAvailable)
                    progressListener = progressListener(appUpdateManager, onUpdateReady)
                    failureListener = failureListener()

                    appUpdateManager.appUpdateInfo.addOnSuccessListener(successListener)
                    appUpdateManager.appUpdateInfo.addOnFailureListener(requireNotNull(failureListener))
                    appUpdateManager.registerListener(requireNotNull(progressListener))
                }
        }
    }

    private fun successListener(
        activity: Activity,
        appUpdateManager: AppUpdateManager,
        onUpdateAvailable: (download: () -> Unit) -> Unit,
    ) = OnSuccessListener<AppUpdateInfo> { appUpdateInfo ->
        if (
            appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
            appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
        ) {
            onUpdateAvailable {
                @Suppress("DEPRECATION")
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.FLEXIBLE,
                    activity,
                    REQUEST_CODE,
                )
            }
        }
    }

    private fun failureListener() = OnFailureListener { error ->
        Timber.e(error)
    }

    private fun progressListener(appUpdateManager: AppUpdateManager, onUpdateReady: (install: () -> Unit) -> Unit) =
        InstallStateUpdatedListener {
            if (it.installStatus() == InstallStatus.DOWNLOADED) {
                onUpdateReady { appUpdateManager.completeUpdate() }
            }
        }

    fun stop() {
        progressListener?.also { appUpdateManager?.unregisterListener(it) }

        appUpdateManager = null
        failureListener = null
        progressListener = null
    }
}
```

(`InstallStatus.CANCELED` previously just dismissed the Snackbar; there's no equivalent state to clean up now since the caller owns the `SnackbarHostState` and a `SnackbarHost` naturally dismisses on its own timeout/swipe — so that branch is dropped, not replaced.)

- [ ] **Step 2: Wire it from `MainScreen.kt`**

In `src/main/kotlin/me/proxer/app/ui/compose/MainScreen.kt`, add these imports:
```kotlin
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import me.proxer.app.util.InAppUpdateFlow
```
(`DisposableEffect`, `rememberCoroutineScope`, `LocalContext` are already imported.)

Change the `MainScreen` function body (currently lines 210-281). Old opening:
```kotlin
@Composable
fun MainScreen(initialItem: DrawerItem) {
    val storageHelper = koinInject<StorageHelper>()
    val context = LocalContext.current

    var user by remember { mutableStateOf(storageHelper.user) }
    DisposableEffect(Unit) {
        val disposable = storageHelper.isLoggedInObservable.subscribe {
            user = storageHelper.user
        }
        onDispose { disposable.dispose() }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedItem by rememberSaveable { mutableStateOf(initialItem) }

    var showLoginDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) LoginDialog(onDismiss = { showLoginDialog = false })
    if (showLogoutDialog) LogoutDialog(onDismiss = { showLogoutDialog = false })

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
```

New:
```kotlin
@Composable
fun MainScreen(initialItem: DrawerItem) {
    val storageHelper = koinInject<StorageHelper>()
    val context = LocalContext.current

    var user by remember { mutableStateOf(storageHelper.user) }
    DisposableEffect(Unit) {
        val disposable = storageHelper.isLoggedInObservable.subscribe {
            user = storageHelper.user
        }
        onDispose { disposable.dispose() }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedItem by rememberSaveable { mutableStateOf(initialItem) }

    var showLoginDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) LoginDialog(onDismiss = { showLoginDialog = false })
    if (showLogoutDialog) LogoutDialog(onDismiss = { showLogoutDialog = false })

    val snackbarHostState = remember { SnackbarHostState() }
    val inAppUpdateFlow = remember { InAppUpdateFlow() }
    val updateAvailableMessage = stringResource(R.string.activity_update_available)
    val updateDownloadAction = stringResource(R.string.activity_update_action_download)
    val updateReadyMessage = stringResource(R.string.activity_update_ready)
    val updateInstallAction = stringResource(R.string.activity_update_action_install)

    LaunchedEffect(Unit) {
        (context as? Activity)?.let { activity ->
            inAppUpdateFlow.start(
                activity = activity,
                onUpdateAvailable = { download ->
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = updateAvailableMessage,
                            actionLabel = updateDownloadAction,
                            duration = SnackbarDuration.Indefinite,
                        )
                        if (result == SnackbarResult.ActionPerformed) download()
                    }
                },
                onUpdateReady = { install ->
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = updateReadyMessage,
                            actionLabel = updateInstallAction,
                            duration = SnackbarDuration.Indefinite,
                        )
                        if (result == SnackbarResult.ActionPerformed) install()
                    }
                },
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { inAppUpdateFlow.stop() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
```

Then, at the very end of the function, the existing `ModalNavigationDrawer(...) { ... }` call's closing brace needs a matching `Box` close plus the `SnackbarHost`. Current end of function (lines 268-281):
```kotlin
    ) {
        val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
        when (selectedItem) {
            DrawerItem.NEWS -> NewsScreen(onOpenDrawer = openDrawer)
            DrawerItem.CHAT, DrawerItem.MESSENGER -> ChatContainerScreen(onOpenDrawer = openDrawer)
            DrawerItem.BOOKMARKS -> BookmarkScreen(onOpenDrawer = openDrawer)
            DrawerItem.ANIME -> MediaListScreen(category = Category.ANIME, onOpenDrawer = openDrawer)
            DrawerItem.SCHEDULE -> ScheduleScreen(onOpenDrawer = openDrawer)
            DrawerItem.MANGA -> MediaListScreen(category = Category.MANGA, onOpenDrawer = openDrawer)
            DrawerItem.INFO -> AboutScreen(onOpenDrawer = openDrawer)
            DrawerItem.SETTINGS -> SettingsScreen(onOpenDrawer = openDrawer)
        }
    }
}
```

New:
```kotlin
        ) {
            val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
            when (selectedItem) {
                DrawerItem.NEWS -> NewsScreen(onOpenDrawer = openDrawer)
                DrawerItem.CHAT, DrawerItem.MESSENGER -> ChatContainerScreen(onOpenDrawer = openDrawer)
                DrawerItem.BOOKMARKS -> BookmarkScreen(onOpenDrawer = openDrawer)
                DrawerItem.ANIME -> MediaListScreen(category = Category.ANIME, onOpenDrawer = openDrawer)
                DrawerItem.SCHEDULE -> ScheduleScreen(onOpenDrawer = openDrawer)
                DrawerItem.MANGA -> MediaListScreen(category = Category.MANGA, onOpenDrawer = openDrawer)
                DrawerItem.INFO -> AboutScreen(onOpenDrawer = openDrawer)
                DrawerItem.SETTINGS -> SettingsScreen(onOpenDrawer = openDrawer)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
```

Note the body of the `ModalNavigationDrawer(...) { ... }` content lambda is now indented one level deeper (inside the new `Box`) — re-indent it accordingly; `Alignment` and `Box`/`Modifier`/`fillMaxSize` are already imported in this file (used by the two `@Preview` functions at the bottom).

- [ ] **Step 3: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Manual verification**

This needs a real Play Store update to be available to trigger end-to-end, which usually isn't available in a dev environment — treat this as code-review-only unless a real update can be staged. At minimum, confirm the app still launches and the drawer/navigation still works with no update available (i.e. `GoogleApiAvailability` check fails gracefully, no crash).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/util/InAppUpdateFlow.kt src/main/kotlin/me/proxer/app/ui/compose/MainScreen.kt
git commit -m "fix(update): restore InAppUpdateFlow under the Compose-only Activity

InAppUpdateFlow needed a ViewGroup to show a View-based Snackbar, which
no longer exists now that MainActivity is Compose-only (BaseActivity's
deprecated snackbar() stub already pointed at this). Refactors it to
take callbacks instead, driven from a SnackbarHostState owned by
MainScreen."
```

---

### Task 9: Fix `BaseViewModelTest`'s `TestViewModel` to route through `validate()`

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/base/BaseViewModelTest.kt:132-136`

**Interfaces:**
- Produces: `BaseViewModelTest.TestViewModel` now calls `validate()` as part of `dataSingle`, so a test can stub `validators.validateLogin()` to throw and observe it surface through `viewModel.error`.

Note: no change to `FakeAppModule.kt` is needed for this — `Validators` is already a MockK `relaxed = true` mock there, and relaxed mocks fully support per-test `every { ... } throws ...` overrides. The actual gap is narrower than "the mock can't be overridden": it's that `TestViewModel.dataSingle` never calls `validate()` at all today, so `BaseViewModel`'s own contract (does `load()` actually run `validate()` before `dataSingle`?) has zero test coverage, and no VM-level test anywhere stubs the login-required path either (that's Task 10-13).

Confirmed the file's actual structure by reading it in full: `viewModel: TestViewModel` is a `lateinit var` field, freshly reconstructed in `@Before setup()` for every test (line 43: `viewModel = TestViewModel()`), so mutating a property on it inside one test never leaks into another. `storageHelper.isLoggedInObservable` is already stubbed to `Observable.never()` in that same `setup()` (line 40) — since `BaseViewModel`'s `init` block subscribes to it and reads `isLoginRequired` reactively (`src/main/kotlin/me/proxer/app/base/BaseViewModel.kt:44-47`), but `Observable.never()` never emits, that subscription never fires during tests regardless of `isLoginRequired`'s value — so changing `isLoginRequired` after construction is safe and won't trigger a spurious `reload()`.

- [ ] **Step 1: Add a `validators` field and the failing test**

In `src/test/kotlin/me/proxer/app/base/BaseViewModelTest.kt`, add to the imports:
```kotlin
import me.proxer.app.exception.NotLoggedInException
import me.proxer.app.util.Validators
```

Add a new field next to the existing `storageHelper`/`preferenceHelper` (after line 34):
```kotlin
    private val validators: Validators by inject()
```

Add a new test near the other `TestViewModel`-based tests (e.g. after `` `load clears previous error` ``, line 127):
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

- [ ] **Step 2: Make `TestViewModel.dataSingle` actually call `validate()`**

Change the `TestViewModel` inner class (currently lines 132-136):

Old:
```kotlin
    private inner class TestViewModel : BaseViewModel<String>() {
        override val isLoginRequired = false
        var nextResponse: Single<String> = Single.never()
        override val dataSingle: Single<String> get() = nextResponse
    }
```

New:
```kotlin
    private inner class TestViewModel : BaseViewModel<String>() {
        override val isLoginRequired = false
        var nextResponse: Single<String> = Single.never()
        override val dataSingle: Single<String>
            get() = Single.fromCallable { validate() }.flatMap { nextResponse }
    }
```

- [ ] **Step 3: Make `isLoginRequired` mutable so the new test can flip it on**

`TestViewModel.isLoginRequired` is hardcoded `false` via `val` (needed so the other existing tests in this file, which construct `TestViewModel` without caring about login-gating, aren't affected). Change it to an overridable `var` defaulting to the same `false`:

```kotlin
    private inner class TestViewModel : BaseViewModel<String>() {
        override var isLoginRequired = false
        var nextResponse: Single<String> = Single.never()
        override val dataSingle: Single<String>
            get() = Single.fromCallable { validate() }.flatMap { nextResponse }
    }
```

- [ ] **Step 4: Run it and confirm it fails first (TDD), then passes after steps 2-3**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.base.BaseViewModelTest"`
Expected (with only step 1's test added, before steps 2-3's `TestViewModel` changes): FAIL, because `dataSingle` never calls `validate()` so `error.value` stays null.
After applying steps 2-3: `BUILD SUCCESSFUL`, all tests in this file pass, including the new one.

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/me/proxer/app/base/BaseViewModelTest.kt
git commit -m "test(base): cover validate() actually running as part of load()

TestViewModel's dataSingle bypassed validate() entirely, so
BaseViewModel's own login/age-confirmation gating contract had zero
test coverage at the base-class level - a regression dropping the
validate() call from load()'s chain would have shipped silently."
```

---

### Task 10: Add login-required test to `AnimeViewModelTest`

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/anime/AnimeViewModelTest.kt`

**Interfaces:** None new — uses existing `Validators` mock via Koin injection and existing `AnimeViewModel`.

- [ ] **Step 1: Add the `validators` field and the new test**

In `src/test/kotlin/me/proxer/app/anime/AnimeViewModelTest.kt`, add to the imports:
```kotlin
import me.proxer.app.exception.NotLoggedInException
import me.proxer.app.util.Validators
```

Add a new field alongside the existing `api`/`storageHelper`/`preferenceHelper` injections (after line 54):
```kotlin
    private val validators: Validators by inject()
```

Add a new test after `` `load sets age confirmation error for age restricted entry when not confirmed` `` (after line 158):
```kotlin
    @Test
    fun `load sets login-required error when validators rejects a logged-out user`() {
        every { validators.validateLogin() } throws NotLoggedInException()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.LOGIN, viewModel.error.value?.buttonAction)
    }
```

- [ ] **Step 2: Run it**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.anime.AnimeViewModelTest"`
Expected: `BUILD SUCCESSFUL`, all tests including the new one pass. (`AnimeViewModel.dataSingle` already starts with `Single.fromCallable { validate() }` per line 43 of the source, and `isLoginRequired` defaults to `true` and is not overridden in `AnimeViewModel`, so the stub takes effect without any other setup changes.)

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/anime/AnimeViewModelTest.kt
git commit -m "test(anime): cover the login-required error path

Closes part of the login-gate coverage gap: no test in this suite
previously exercised what happens when a login-required ViewModel is
loaded while logged out."
```

---

### Task 11: Add login-required test to `BookmarkViewModelTest`

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/bookmark/BookmarkViewModelTest.kt`

**Interfaces:** None new.

- [ ] **Step 1: Add the `validators` field and the new test**

Add to the imports:
```kotlin
import me.proxer.app.exception.NotLoggedInException
import me.proxer.app.util.Validators
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
```

Add a new field alongside `api`/`storageHelper`/`preferenceHelper` (after line 48):
```kotlin
    private val validators: Validators by inject()
```

Add a new test after `` `load sets error on failure` `` (after line 108):
```kotlin
    @Test
    fun `load sets login-required error when validators rejects a logged-out user`() {
        every { validators.validateLogin() } throws NotLoggedInException()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.LOGIN, viewModel.error.value?.buttonAction)
    }
```

- [ ] **Step 2: Run it**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.bookmark.BookmarkViewModelTest"`
Expected: `BUILD SUCCESSFUL`. (`BookmarkViewModel` extends `PagedContentViewModel`, whose `dataSingle` calls `.fromCallable { validate() }` per `src/main/kotlin/me/proxer/app/base/PagedContentViewModel.kt:14`, and `isLoginRequired` is not overridden by `BookmarkViewModel`, so it defaults to `true`.)

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/bookmark/BookmarkViewModelTest.kt
git commit -m "test(bookmark): cover the login-required error path"
```

---

### Task 12: Add login-required test to `ChatViewModelTest`

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/chat/pub/message/ChatViewModelTest.kt`

**Interfaces:** None new.

- [ ] **Step 1: Add the `validators` field and the new test**

Add to the imports:
```kotlin
import me.proxer.app.exception.NotLoggedInException
import me.proxer.app.util.Validators
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
```

Add a new field alongside `api`/`storageHelper`/`preferenceHelper` (after line 52):
```kotlin
    private val validators: Validators by inject()
```

Add a new test after `` `load sets error on failure when there is no existing data` `` (after line 128):
```kotlin
    @Test
    fun `load sets login-required error when validators rejects a logged-out user`() {
        every { validators.validateLogin() } throws NotLoggedInException()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.LOGIN, viewModel.error.value?.buttonAction)
    }
```

This test does not need the `TextPrototype`/`computationScheduler` machinery the rest of the file uses for successful-load cases — `validate()` throws before any message parsing or polling would start, since it's the first step in `dataSingle`'s chain (`Single.fromCallable { validate() }`, `ChatViewModel.kt:33`).

- [ ] **Step 2: Run it**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.chat.pub.message.ChatViewModelTest"`
Expected: `BUILD SUCCESSFUL`, all tests pass including the new one and the pre-existing `sendMessage optimistically prepends the message and clears it on failure` (unaffected by this change).

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/chat/pub/message/ChatViewModelTest.kt
git commit -m "test(chat): cover the login-required error path"
```

---

### Task 13: Fix `NotificationViewModelTest` — `deleteAll` failure assertion + login-required test

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/notification/NotificationViewModelTest.kt`

**Interfaces:** None new.

- [ ] **Step 1: Add a `Unit?`-typed error-call helper, mirroring the existing `mockErrorCall()`**

Add near the existing `mockUnitCall()` helper (after line 91):
```kotlin
    private fun mockErrorUnitCall(): ProxerCall<Unit?> {
        val call = mockk<ProxerCall<Unit?>>(relaxed = true)

        every { call.clone() } returns call
        every { call.safeExecute() } throws ProxerException(ProxerException.ErrorType.IO)

        return call
    }
```

- [ ] **Step 2: Split the mis-named `deleteAll` test into two, one per branch**

Replace the existing test (currently lines 216-225):

Old:
```kotlin
    @Test
    fun `deleteAll clears data on success and sets deletionError on failure`() {
        every { api.notifications.deleteAllNotifications() } returns mockk(relaxed = true) {
            every { build() } returns mockUnitCall()
        }

        viewModel.deleteAll()

        assertEquals(emptyList<ProxerNotification>(), viewModel.data.value)
    }
```

New:
```kotlin
    @Test
    fun `deleteAll clears data on success`() {
        every { api.notifications.deleteAllNotifications() } returns mockk(relaxed = true) {
            every { build() } returns mockUnitCall()
        }

        viewModel.deleteAll()

        assertEquals(emptyList<ProxerNotification>(), viewModel.data.value)
        assertNull(viewModel.deletionError.value)
    }

    @Test
    fun `deleteAll sets deletionError on failure`() {
        every { api.notifications.deleteAllNotifications() } returns mockk(relaxed = true) {
            every { build() } returns mockErrorUnitCall()
        }

        viewModel.deleteAll()

        assertNotNull(viewModel.deletionError.value)
    }
```

- [ ] **Step 3: Add the `validators` field and the login-required test**

Add to the imports:
```kotlin
import me.proxer.app.exception.NotLoggedInException
import me.proxer.app.util.Validators
```

Add a new field alongside `api`/`storageHelper`/`preferenceHelper` (after line 47):
```kotlin
    private val validators: Validators by inject()
```

Add a new test (anywhere after `setup()`, e.g. right after `` `load sets error when the unread call fails` ``):
```kotlin
    @Test
    fun `load sets login-required error when validators rejects a logged-out user`() {
        every { validators.validateLogin() } throws NotLoggedInException()

        viewModel.load()

        assertNull(viewModel.data.value)
        assertNotNull(viewModel.error.value)
    }
```

(`NotificationViewModel.dataSingle` starts with `Single.fromCallable { validators.validateLogin() }` directly, not via `validate()` — so this doesn't need `ButtonAction` imported/asserted as precisely; `assertNotNull` on `error.value` is enough and matches this file's existing assertion style for error tests.)

- [ ] **Step 4: Run it**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.notification.NotificationViewModelTest"`
Expected: `BUILD SUCCESSFUL`, all tests pass including the two split `deleteAll` tests and the new login-required test.

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/me/proxer/app/notification/NotificationViewModelTest.kt
git commit -m "test(notification): split deleteAll test to actually cover its failure branch, add login-required coverage

The old test's name claimed to cover both success and failure but only
ever stubbed and asserted success. Split into two tests, and added a
mockErrorUnitCall() helper for the Unit? failure shape."
```

---

### Task 14: Add age-restriction tests to `MediaInfoViewModelTest`

**Files:**
- Modify: `src/test/kotlin/me/proxer/app/media/MediaInfoViewModelTest.kt`

**Interfaces:** None new — `Entry.isAgeRestricted` already exists on the test's `createEntry(...)` helper.

- [ ] **Step 1: Add the two missing tests**

`MediaInfoViewModel.dataSingle`'s `doOnSuccess` (source, lines 32-39) throws `NotLoggedInException` when `entry.isTrulyAgeRestricted && !storageHelper.isLoggedIn`, and `AgeConfirmationRequiredException` when `entry.isTrulyAgeRestricted && storageHelper.isLoggedIn && !preferenceHelper.isAgeRestrictedMediaAllowed`. Add these imports:
```kotlin
import me.proxer.app.util.ErrorUtils.ErrorAction.ButtonAction
```

Add two new tests after `` `load sets error on failure` `` (after line 116):
```kotlin
    @Test
    fun `load sets login-required error for age restricted entry when not logged in`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)

        every { api.info.entry(entryId) } returns entryEndpoint
        entryEndpoint.stubSuccess(createEntry(isAgeRestricted = true))

        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.LOGIN, viewModel.error.value?.buttonAction)
    }

    @Test
    fun `load sets age confirmation error for age restricted entry when logged in but not confirmed`() {
        val entryEndpoint = mockk<EntryEndpoint>(relaxed = true)

        every { storageHelper.isLoggedIn } returns true
        every { preferenceHelper.isAgeRestrictedMediaAllowed } returns false
        every { api.info.entry(entryId) } returns entryEndpoint
        entryEndpoint.stubSuccess(createEntry(isAgeRestricted = true))

        viewModel.load()

        assertNull(viewModel.data.value)
        assertEquals(ButtonAction.AGE_CONFIRMATION, viewModel.error.value?.buttonAction)
    }
```

(`setup()` already sets `storageHelper.isLoggedIn` to `false` by default, so the first test needs no override; `preferenceHelper.isAgeRestrictedMediaAllowed` is never stubbed anywhere in this file today, defaulting to MockK's relaxed `false` — the second test stubs both explicitly to be unambiguous about what it's testing rather than relying on that default.)

- [ ] **Step 2: Run it**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.media.MediaInfoViewModelTest"`
Expected: `BUILD SUCCESSFUL`, all tests including the two new ones pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/me/proxer/app/media/MediaInfoViewModelTest.kt
git commit -m "test(media-info): cover the age-restriction gate

MediaInfoViewModel implements the same login/age-confirmation gating
for restricted entries as AnimeViewModel/MangaViewModel, both of which
already have this coverage - MediaInfoViewModelTest was the one sibling
missing it entirely."
```

---

### Task 15: Restore bookmark swipe-to-delete + undo in `BookmarkScreen`

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/bookmark/BookmarkScreen.kt`

**Interfaces:**
- Consumes: `BookmarkViewModel.itemDeletionError`, `.undoData`, `.undoError` (all `ResettingMutableLiveData<...>`, existing), `.addItemToDelete(item: Bookmark)`, `.undo()` (existing).

- [ ] **Step 1: Add observation, local dismiss state, snackbars, and `SwipeToDismissBox` to the item list**

Replace the full contents of `src/main/kotlin/me/proxer/app/bookmark/BookmarkScreen.kt`:

```kotlin
package me.proxer.app.bookmark

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.proxer.app.R
import me.proxer.app.anime.AnimeActivity
import me.proxer.app.manga.MangaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.extension.toAnimeLanguage
import me.proxer.app.util.extension.toAppString
import me.proxer.app.util.extension.toEpisodeAppString
import me.proxer.app.util.extension.toGeneralLanguage
import me.proxer.library.entity.ucp.Bookmark
import me.proxer.library.enums.Category
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(onOpenDrawer: () -> Unit = {}) {
    val viewModel = koinViewModel<BookmarkViewModel> { parametersOf(null, null, false) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val itemDeletionError by viewModel.itemDeletionError.observeAsState()
    val undoData by viewModel.undoData.observeAsState()
    val undoError by viewModel.undoError.observeAsState()
    val context = LocalContext.current

    var showFilterMenu by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var filterAvailable by remember { mutableStateOf(false) }
    val dismissedIds = remember { mutableStateOf(emptySet<String>()) }
    val displayedData = (data ?: emptyList()).filterNot { it.id in dismissedIds.value }

    LaunchedEffect(Unit) { viewModel.load() }

    BookmarkContent(
        data = data,
        displayedData = displayedData,
        error = error,
        isLoading = isLoading == true,
        itemDeletionError = itemDeletionError,
        undoData = undoData,
        undoError = undoError,
        showFilterMenu = showFilterMenu,
        selectedCategory = selectedCategory,
        filterAvailable = filterAvailable,
        onOpenDrawer = onOpenDrawer,
        onRetry = { viewModel.load() },
        onRefresh = { viewModel.refresh() },
        onLoadMore = { viewModel.loadIfPossible() },
        onShowFilterMenu = { showFilterMenu = it },
        onSelectCategory = { category ->
            selectedCategory = category
            viewModel.category = category
        },
        onSetFilterAvailable = { available ->
            filterAvailable = available
            viewModel.filterAvailable = available
        },
        onDeleteItem = { bookmark ->
            dismissedIds.value = dismissedIds.value + bookmark.id
            viewModel.addItemToDelete(bookmark)
        },
        onUndo = {
            dismissedIds.value = emptySet()
            viewModel.undo()
        },
        onBookmarkClick = { bookmark ->
            val activity = context as? Activity
            activity?.let {
                when (bookmark.category) {
                    Category.ANIME -> AnimeActivity.navigateTo(
                        it,
                        bookmark.entryId,
                        bookmark.episode,
                        bookmark.language.toAnimeLanguage(),
                        bookmark.name,
                    )
                    Category.MANGA, Category.NOVEL -> MangaActivity.navigateTo(
                        it,
                        bookmark.entryId,
                        bookmark.episode,
                        bookmark.language.toGeneralLanguage(),
                        bookmark.chapterName,
                        bookmark.name,
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkContent(
    data: List<Bookmark>?,
    displayedData: List<Bookmark>,
    error: ErrorAction?,
    isLoading: Boolean,
    itemDeletionError: ErrorAction?,
    undoData: Unit?,
    undoError: ErrorAction?,
    showFilterMenu: Boolean,
    selectedCategory: Category?,
    filterAvailable: Boolean,
    onOpenDrawer: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onShowFilterMenu: (Boolean) -> Unit,
    onSelectCategory: (Category?) -> Unit,
    onSetFilterAvailable: (Boolean) -> Unit,
    onDeleteItem: (Bookmark) -> Unit,
    onUndo: () -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(listState.layoutInfo) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) onLoadMore()
    }

    LaunchedEffect(itemDeletionError) {
        val err = itemDeletionError
        if (err != null) {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_bookmark_deletion, context.getString(err.message)),
            )
        }
    }

    LaunchedEffect(undoData) {
        if (undoData != null) {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.fragment_bookmark_delete_message),
                actionLabel = context.getString(R.string.action_undo),
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    LaunchedEffect(undoError) {
        val err = undoError
        if (err != null) {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_undo, context.getString(err.message)),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_bookmarks)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { onShowFilterMenu(true) }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.action_filter),
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { onShowFilterMenu(false) },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_filter_all)) },
                                onClick = {
                                    onSelectCategory(null)
                                    onShowFilterMenu(false)
                                },
                                trailingIcon = if (selectedCategory == null) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_filter_anime)) },
                                onClick = {
                                    onSelectCategory(Category.ANIME)
                                    onShowFilterMenu(false)
                                },
                                trailingIcon = if (selectedCategory == Category.ANIME) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_filter_manga)) },
                                onClick = {
                                    onSelectCategory(Category.MANGA)
                                    onShowFilterMenu(false)
                                },
                                trailingIcon = if (selectedCategory == Category.MANGA) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_filter_available)) },
                                onClick = { onSetFilterAvailable(!filterAvailable) },
                                trailingIcon = if (filterAvailable) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = onRetry,
            isSwipeToRefreshEnabled = true,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(displayedData, key = { it.id }) { bookmark ->
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            onDeleteItem(bookmark)
                        }
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    ) {
                        BookmarkItem(
                            bookmark = bookmark,
                            onClick = { onBookmarkClick(bookmark) },
                        )
                    }
                }
                if (isLoading && !data.isNullOrEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookmarkItem(bookmark: Bookmark, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row {
            AsyncImage(
                model = ProxerUrls.entryImage(bookmark.entryId).toString(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(0.8f),
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = bookmark.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = bookmark.medium.toAppString(context),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = bookmark.chapterName ?: bookmark.category.toEpisodeAppString(context, bookmark.episode),
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(bookmark.language.toAppString(context)) },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BookmarkContentPreview() {
    ProxerTheme {
        BookmarkContent(
            data = null,
            displayedData = emptyList(),
            error = null,
            isLoading = true,
            itemDeletionError = null,
            undoData = null,
            undoError = null,
            showFilterMenu = false,
            selectedCategory = null,
            filterAvailable = false,
            onOpenDrawer = {},
            onRetry = {},
            onRefresh = {},
            onLoadMore = {},
            onShowFilterMenu = {},
            onSelectCategory = {},
            onSetFilterAvailable = {},
            onDeleteItem = {},
            onUndo = {},
            onBookmarkClick = {},
        )
    }
}
```

Note `onUndo` resets `dismissedIds` to empty — matching `NotificationScreen.kt`'s `onDeleteAll` precedent of clearing dismissed-state — since `BookmarkViewModel.undo()` re-adds the item to `data` on success, and the local optimistic-hide filter must stop hiding it once that happens (there's only ever one outstanding undo item, so clearing the whole set is equivalent to clearing just that one id).

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Confirm the existing ViewModel-level tests for this behavior still pass unchanged**

Run: `./gradlew testDebugUnitTest --tests "me.proxer.app.bookmark.BookmarkViewModelTest"`
Expected: `BUILD SUCCESSFUL` — this task only touches the Screen, not the ViewModel, so `` `addItemToDelete removes item from data and sets undoData on success` `` and `` `addItemToDelete sets itemDeletionError on failure` `` (already present) should be unaffected.

- [ ] **Step 4: Manual verification**

On the Bookmarks screen, swipe an item away — confirm it disappears and an "Undo" snackbar appears; tap Undo and confirm the item reappears. Swipe an item with the network disabled — confirm a deletion-error snackbar appears instead.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/bookmark/BookmarkScreen.kt
git commit -m "feat(bookmark): restore swipe-to-delete with undo

BookmarkViewModel's addItemToDelete/undo/itemDeletionError/undoData/
undoError were all still present and correctly tested at the ViewModel
level, but nothing in the Compose screen ever called or observed them -
the whole swipe-delete-with-undo UX was dropped during the migration.
Restores it using the SwipeToDismissBox + optimistic-hide pattern
already established in NotificationScreen."
```

---

### Task 16: Restore comment deletion in `CommentsScreen`

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/media/comments/CommentsScreen.kt`

**Interfaces:**
- Consumes: `CommentsViewModel.itemDeletionError: ResettingMutableLiveData<ErrorUtils.ErrorAction?>` (existing), `.deleteComment(comment: ParsedComment)` (existing).

The old Fragment used a per-item delete affordance that opened a confirmation dialog before calling `deleteComment` (not swipe, not a CAB/multi-select) — this restores that same shape in Compose.

- [ ] **Step 1: Add a delete button, confirmation dialog, and error snackbar**

Replace the full contents of `src/main/kotlin/me/proxer/app/media/comments/CommentsScreen.kt`:

```kotlin
package me.proxer.app.media.comments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.proxer.app.R
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.view.bbcode.BBCodeView
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.extension.distanceInWordsToNow
import me.proxer.library.enums.CommentSortCriteria
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CommentsScreen(mediaId: String) {
    val viewModel = koinViewModel<CommentsViewModel> { parametersOf(mediaId, CommentSortCriteria.RATING) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val itemDeletionError by viewModel.itemDeletionError.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    CommentsContent(
        data = data,
        error = error,
        isLoading = isLoading == true,
        itemDeletionError = itemDeletionError,
        onRetry = { viewModel.load() },
        onRefresh = { viewModel.refresh() },
        onLoadMore = { viewModel.loadIfPossible() },
        onDelete = { viewModel.deleteComment(it) },
    )
}

@Composable
private fun CommentsContent(
    data: List<ParsedComment>?,
    error: ErrorAction?,
    isLoading: Boolean,
    itemDeletionError: ErrorAction?,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDelete: (ParsedComment) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<ParsedComment?>(null) }

    LaunchedEffect(listState.layoutInfo) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) onLoadMore()
    }

    LaunchedEffect(itemDeletionError) {
        val err = itemDeletionError
        if (err != null) {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_comment_deletion, context.getString(err.message)),
            )
        }
    }

    deleteTarget?.let { comment ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            text = { Text(stringResource(R.string.dialog_comment_delete_message, comment.author)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(comment)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.dialog_comment_delete_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ContentScreen(
            isLoading = isLoading && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = onRetry,
            isSwipeToRefreshEnabled = true,
            onRefresh = onRefresh,
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(data ?: emptyList(), key = { it.id }) { comment ->
                    CommentItem(comment = comment, onDelete = { deleteTarget = comment })
                    HorizontalDivider()
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun CommentItem(comment: ParsedComment, onDelete: () -> Unit) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = comment.author,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (comment.overallRating > 0) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "%.1f".format(comment.overallRating / 2.0f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                )
            }
        }

        Text(
            text = comment.date.distanceInWordsToNow(context),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!comment.parsedContent.isBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            AndroidView(
                factory = { ctx -> BBCodeView(ctx) },
                update = { view -> view.tree = comment.parsedContent },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CommentsContentPreview() {
    ProxerTheme {
        CommentsContent(
            data = null,
            error = null,
            isLoading = true,
            itemDeletionError = null,
            onRetry = {},
            onRefresh = {},
            onLoadMore = {},
            onDelete = {},
        )
    }
}
```

Note: this unconditionally shows a delete button on every comment, including other users' — the old Fragment's adapter-level `deleteClickSubject` likewise had no ownership check in the code paths inspected (deletion authorization is presumably enforced server-side, matching how `ProfileMediaListScreen`'s delete works too — see Task 7's note). If this turns out to be wrong (i.e. the server allows deleting only your own comments and old the adapter did hide the button for others), that's a follow-up, not a regression introduced here.

- [ ] **Step 2: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Manual verification**

Open a media entry's comments, tap the delete icon on one of your own comments, confirm the dialog, confirm the comment disappears. Trigger a failure (e.g. airplane mode) and confirm a snackbar appears instead.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/media/comments/CommentsScreen.kt
git commit -m "feat(comments): restore comment deletion

CommentsViewModel.deleteComment()/itemDeletionError were unchanged from
master but nothing in the Compose screen called or observed them.
Restores the old confirm-dialog-then-delete flow (not swipe/CAB - the
old Fragment used a plain per-item delete button)."
```

---

### Task 17: Restore comment deletion in `ProfileCommentScreen`

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/profile/comment/ProfileCommentScreen.kt`

**Interfaces:**
- Consumes: `ProfileCommentViewModel.itemDeletionError: ResettingMutableLiveData<ErrorUtils.ErrorAction?>` (existing), `.deleteComment(comment: ParsedUserComment)` (existing).

Same shape as Task 16, applied to the profile variant (`ParsedUserComment` instead of `ParsedComment`, entry-name instead of author in the confirmation text, per the old `ProfileCommentFragment`'s `dialog_comment_delete_message` format arg).

- [ ] **Step 1: Add a delete button, confirmation dialog, and error snackbar**

Replace the full contents of `src/main/kotlin/me/proxer/app/profile/comment/ProfileCommentScreen.kt`:

```kotlin
package me.proxer.app.profile.comment

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.proxer.app.media.MediaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.view.bbcode.BBCodeView
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.extension.distanceInWordsToNow
import me.proxer.app.util.extension.toEpisodeAppString
import me.proxer.app.R
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ProfileCommentScreen(userId: String?, username: String?) {
    val viewModel = koinViewModel<ProfileCommentViewModel> { parametersOf(userId, username, null) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val itemDeletionError by viewModel.itemDeletionError.observeAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    ProfileCommentContent(
        data = data,
        error = error,
        isLoading = isLoading == true,
        itemDeletionError = itemDeletionError,
        onRetry = { viewModel.load() },
        onLoadMore = { viewModel.loadIfPossible() },
        onDelete = { viewModel.deleteComment(it) },
    )
}

@Composable
private fun ProfileCommentContent(
    data: List<ParsedUserComment>?,
    error: ErrorAction?,
    isLoading: Boolean,
    itemDeletionError: ErrorAction?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onDelete: (ParsedUserComment) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<ParsedUserComment?>(null) }

    LaunchedEffect(listState.layoutInfo) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 3) onLoadMore()
    }

    LaunchedEffect(itemDeletionError) {
        val err = itemDeletionError
        if (err != null) {
            snackbarHostState.showSnackbar(
                context.getString(R.string.error_comment_deletion, context.getString(err.message)),
            )
        }
    }

    deleteTarget?.let { comment ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            text = { Text(stringResource(R.string.dialog_comment_delete_message, comment.entryName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(comment)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.dialog_comment_delete_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ContentScreen(
            isLoading = isLoading && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = onRetry,
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(data ?: emptyList(), key = { it.id }) { comment ->
                    ProfileCommentItem(comment = comment, onDelete = { deleteTarget = comment })
                    HorizontalDivider()
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ProfileCommentItem(comment: ParsedUserComment, onDelete: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = comment.entryName,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        activity?.let {
                            MediaActivity.navigateTo(it, comment.entryId, comment.entryName, comment.category)
                        }
                    },
            )
            if (comment.overallRating > 0) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "%.1f".format(comment.overallRating / 2.0f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                )
            }
        }

        Text(
            text = comment.date.distanceInWordsToNow(context),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = comment.mediaProgress.toEpisodeAppString(context, comment.episode, comment.category),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!comment.parsedContent.isBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            AndroidView(
                factory = { ctx -> BBCodeView(ctx) },
                update = { view -> view.tree = comment.parsedContent },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileCommentContentPreview() {
    ProxerTheme {
        ProfileCommentContent(
            data = null,
            error = null,
            isLoading = true,
            itemDeletionError = null,
            onRetry = {},
            onLoadMore = {},
            onDelete = {},
        )
    }
}
```

- [ ] **Step 2: Verify the `action_delete` string added in Task 7 covers this too**

If Task 7 already added `action_delete` to `strings.xml`, no action needed here — this file reuses the same key.

- [ ] **Step 3: Type-check**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Manual verification**

On your own profile's Comments tab, delete a comment via the confirm dialog, confirm it disappears; trigger a failure and confirm a snackbar.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/profile/comment/ProfileCommentScreen.kt
git commit -m "feat(profile-comments): restore comment deletion

Same fix as CommentsScreen (previous commit), applied to the profile
comment list variant."
```

---

## Post-plan verification

After all 17 tasks are applied, run the full unit test suite once to catch any cross-task interaction the per-task runs missed:

```bash
./gradlew testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`, all tests pass (345+ pre-existing plus the ~7 new ones added by Tasks 9-14).

Then a final full type-check + lint pass:
```bash
./gradlew compileDebugKotlin detekt
```
Expected: `BUILD SUCCESSFUL` (detekt is configured permissively per CLAUDE.md, so warnings are fine, failures are not).
