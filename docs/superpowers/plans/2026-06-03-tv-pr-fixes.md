# TV PR Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the critical and important issues found in the `tv-support` branch PR review: silent loads, discarded error actions, dead login buttons, and login success re-delivery.

**Architecture:** All TV screens are Compose + `ComponentActivity`. Error handling flows through `TvErrorView`, which already handles LOGIN/AGE_CONFIRMATION button actions. Fixes thread `onLoginClick` downward from each Activity and replace hand-rolled error UI with `TvErrorView`. `LoginViewModel.success` is converted to `ResettingMutableLiveData` to prevent double-invocation on recomposition.

**Tech Stack:** Kotlin, Compose for TV, Material3, Koin 3.5.6, RxJava 2, `ResettingMutableLiveData` (in-repo at `me.proxer.app.util.data`)

**Build verification command:** `./gradlew compileDebugKotlin` (fast type-check, no full APK needed)

---

## Files Modified

| File | Changes |
|------|---------|
| `src/main/kotlin/me/proxer/app/tv/stream/TvStreamScreen.kt` | Fix init load; capture resolutionError; TvErrorView for both error types; add `onLoginClick` param |
| `src/main/kotlin/me/proxer/app/tv/stream/TvStreamActivity.kt` | Add `val activity = this`; thread `onLoginClick` |
| `src/main/kotlin/me/proxer/app/tv/detail/TvMediaDetailScreen.kt` | Add `onLoginClick` param; fix blank-screen race; pass to TvErrorView |
| `src/main/kotlin/me/proxer/app/tv/detail/TvMediaDetailActivity.kt` | Add TvLoginActivity import; thread `onLoginClick` |
| `src/main/kotlin/me/proxer/app/tv/episode/TvEpisodeScreen.kt` | Add `onLoginClick` param; fix pagination retry; pass to TvErrorView |
| `src/main/kotlin/me/proxer/app/tv/episode/TvEpisodeActivity.kt` | Add TvLoginActivity import; thread `onLoginClick` |
| `src/main/kotlin/me/proxer/app/tv/search/TvSearchScreen.kt` | Add `onLoginClick` param; guard empty-query reload; hide spinner during error; pass to TvErrorView |
| `src/main/kotlin/me/proxer/app/tv/search/TvSearchActivity.kt` | Add TvLoginActivity import; thread `onLoginClick` |
| `src/main/kotlin/me/proxer/app/auth/LoginViewModel.kt` | `success` field: `MutableLiveData` → `ResettingMutableLiveData` |

---

### Task 1: Fix TvStreamScreen + TvStreamActivity

**Context:** `TvStreamScreen` has four problems: (1) `loadIfPossible()` on init silently skips load if the VM has a stale error, (2) resolution errors are displayed as hardcoded "Resolution error" text discarding the real `ErrorAction` message and buttons, (3) the stream list error branch is hand-rolled and loses LOGIN/AGE_CONFIRMATION actions, (4) no `onLoginClick` param so no screen in the stream flow can navigate to login.

`TvStreamActivity` currently has no `val activity = this` and doesn't import `TvLoginActivity`.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/tv/stream/TvStreamScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/tv/stream/TvStreamActivity.kt`

- [ ] **Step 1: Update TvStreamScreen imports**

Replace the import block in `TvStreamScreen.kt`. Remove unused `Button`, `Spacer`; add `TvErrorView` and `ErrorUtils`:

```kotlin
package me.proxer.app.tv.stream

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.proxer.app.anime.AnimeStream
import me.proxer.app.anime.AnimeViewModel
import me.proxer.app.anime.resolver.StreamResolutionResult
import me.proxer.app.tv.TvErrorView
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.extension.androidUri
import me.proxer.app.util.extension.toast
import me.proxer.library.enums.AnimeLanguage
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
```

- [ ] **Step 2: Update TvStreamScreen function signature and state**

Replace the `TvStreamScreen` function signature and local state declarations (lines 48–66):

```kotlin
@Composable
fun TvStreamScreen(
    entryId: String,
    episode: Int,
    language: AnimeLanguage,
    entryName: String,
    onLoginClick: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: AnimeViewModel = koinViewModel { parametersOf(entryId, language, episode) }
    val streamInfo by viewModel.data.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()
    val resolutionResult by viewModel.resolutionResult.observeAsState()
    val resolutionError by viewModel.resolutionError.observeAsState()
    val context = LocalContext.current
    var resolvingStreamId by remember { mutableStateOf<String?>(null) }
    // ResettingMutableLiveData suppresses null re-delivery to Compose, so we capture the
    // ErrorAction in local state before the LiveData resets to null.
    var showResolutionError by remember { mutableStateOf(false) }
    var resolutionErrorAction by remember { mutableStateOf<ErrorUtils.ErrorAction?>(null) }
```

- [ ] **Step 3: Fix init load and resolution error LaunchedEffects**

Replace lines 68–75 (the two `LaunchedEffect` blocks before the `Column`):

```kotlin
    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(resolutionError) {
        if (resolutionError != null) {
            resolutionErrorAction = resolutionError
            showResolutionError = true
            resolvingStreamId = null
        }
    }
```

- [ ] **Step 4: Replace resolution error Text with TvErrorView**

Replace lines 128–134 (the `if (showResolutionError)` block):

```kotlin
        if (showResolutionError && resolutionErrorAction != null) {
            TvErrorView(
                error = resolutionErrorAction!!,
                onLoginClick = onLoginClick,
                onRetryClick = { showResolutionError = false }
            )
        }
```

- [ ] **Step 5: Replace hand-rolled error branch with TvErrorView**

Replace lines 142–148 (the `error != null ->` branch inside the `when` block):

```kotlin
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TvErrorView(
                        error = error!!,
                        onLoginClick = onLoginClick,
                        onRetryClick = { viewModel.reload() }
                    )
                }
            }
```

- [ ] **Step 6: Update TvStreamActivity**

Replace the entire `TvStreamActivity.kt`:

```kotlin
package me.proxer.app.tv.stream

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.MaterialTheme
import me.proxer.app.tv.auth.TvLoginActivity
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.startActivity
import me.proxer.library.enums.AnimeLanguage

class TvStreamActivity : ComponentActivity() {

    private val entryId: String get() = intent.getSafeStringExtra(ID_EXTRA)
    private val episode: Int get() = intent.getIntExtra(EPISODE_EXTRA, 1)
    @Suppress("DEPRECATION")
    private val language: AnimeLanguage get() =
        (intent.getSerializableExtra(LANGUAGE_EXTRA) as? AnimeLanguage) ?: AnimeLanguage.ENGLISH_SUB
    private val entryName: String get() = intent.getStringExtra(NAME_EXTRA) ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        val id = entryId
        val ep = episode
        val lang = language
        val name = entryName
        setContent {
            MaterialTheme {
                TvStreamScreen(
                    entryId = id,
                    episode = ep,
                    language = lang,
                    entryName = name,
                    onLoginClick = { activity.startActivity<TvLoginActivity>() },
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        private const val ID_EXTRA = "id"
        private const val EPISODE_EXTRA = "episode"
        private const val LANGUAGE_EXTRA = "language"
        private const val NAME_EXTRA = "name"

        fun navigateTo(context: Context, id: String, episode: Int, language: AnimeLanguage, name: String) {
            context.startActivity<TvStreamActivity>(
                ID_EXTRA to id,
                EPISODE_EXTRA to episode,
                LANGUAGE_EXTRA to language,
                NAME_EXTRA to name
            )
        }
    }
}
```

- [ ] **Step 7: Verify it compiles**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If you see `Unresolved reference: TvErrorView` check the import in TvStreamScreen.kt. If you see `Too many arguments` on TvStreamScreen call in TvStreamActivity check you added the `onLoginClick` param before `onBack`.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/me/proxer/app/tv/stream/TvStreamScreen.kt \
        src/main/kotlin/me/proxer/app/tv/stream/TvStreamActivity.kt
git commit -m "fix(tv): fix TvStreamScreen critical error handling issues

- loadIfPossible() -> load() on init (was silently skipping load when
  AnimeViewModel had a stale error from a previous navigation)
- Capture resolutionError ErrorAction in local state before
  ResettingMutableLiveData resets it to null; display via TvErrorView
  so AppRequiredErrorAction and login errors show correct buttons
- Replace hand-rolled stream-list error UI with TvErrorView so
  LOGIN/AGE_CONFIRMATION button actions work from the stream screen
- Add onLoginClick param; thread from TvStreamActivity"
```

---

### Task 2: Fix TvMediaDetailScreen + TvMediaDetailActivity

**Context:** `TvMediaDetailScreen` calls `TvErrorView` without `onLoginClick`, so the LOGIN button is a no-op. `MediaInfoViewModel` has `isLoginRequired = true` by default, so a logged-out user hits an unactionable "Login" button. The spinner condition `isLoading == true && entry == null` can also leave a blank screen during the brief window between `reload()` clearing data and the ViewModel posting `isLoading = true`.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/tv/detail/TvMediaDetailScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/tv/detail/TvMediaDetailActivity.kt`

- [ ] **Step 1: Update TvMediaDetailScreen signature and when block**

Replace the function signature and `when` block (lines 36–102):

```kotlin
@Composable
fun TvMediaDetailScreen(
    entryId: String,
    entryName: String,
    onLoginClick: () -> Unit,
    onWatchEpisodes: (episodeAmount: Int) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: MediaInfoViewModel = koinViewModel { parametersOf(entryId) }
    val entry by viewModel.data.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        AsyncImage(
            model = ProxerUrls.entryImage(entryId).toString(),
            contentDescription = entryName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onBack) { Text("← Back", color = Color.White) }

            when {
                isLoading == true -> CircularProgressIndicator(color = Color.White)
                error != null -> {
                    TvErrorView(
                        error = error!!,
                        onLoginClick = onLoginClick,
                        onRetryClick = { viewModel.load() }
                    )
                }
                else -> entry?.let { e ->
                    Text(e.name, fontSize = 28.sp, color = Color.White)
                    Text(
                        "Rating: ${"%.1f".format(e.rating.toDouble())}/10",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Text("Episodes: ${e.episodeAmount}", color = Color.LightGray, fontSize = 14.sp)
                    if (e.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Synopsis", color = Color.White, fontSize = 18.sp)
                        Text(e.description, color = Color.LightGray, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onWatchEpisodes(e.episodeAmount) }) {
                        Text("Watch Episodes", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Update TvMediaDetailActivity**

Replace the entire `TvMediaDetailActivity.kt`:

```kotlin
package me.proxer.app.tv.detail

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.MaterialTheme
import me.proxer.app.tv.auth.TvLoginActivity
import me.proxer.app.tv.episode.TvEpisodeActivity
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.startActivity

class TvMediaDetailActivity : ComponentActivity() {

    private val entryId: String get() = intent.getSafeStringExtra(ID_EXTRA)
    private val entryName: String get() = intent.getStringExtra(NAME_EXTRA) ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        val id = entryId
        val name = entryName
        setContent {
            MaterialTheme {
                TvMediaDetailScreen(
                    entryId = id,
                    entryName = name,
                    onLoginClick = { activity.startActivity<TvLoginActivity>() },
                    onWatchEpisodes = { episodeAmount ->
                        TvEpisodeActivity.navigateTo(activity, id, name, episodeAmount)
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        private const val ID_EXTRA = "id"
        private const val NAME_EXTRA = "name"

        fun navigateTo(context: Context, id: String, name: String) {
            context.startActivity<TvMediaDetailActivity>(ID_EXTRA to id, NAME_EXTRA to name)
        }
    }
}
```

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/tv/detail/TvMediaDetailScreen.kt \
        src/main/kotlin/me/proxer/app/tv/detail/TvMediaDetailActivity.kt
git commit -m "fix(tv): add onLoginClick to TvMediaDetailScreen, fix spinner race"
```

---

### Task 3: Fix TvEpisodeScreen + TvEpisodeActivity

**Context:** `TvEpisodeScreen` calls `TvErrorView` without `onLoginClick` (dead LOGIN button) and its retry callback always calls `viewModel.reload()` even during pagination errors — resetting the entire episode list. Fix retry to call `loadIfPossible()` when the list is non-empty so only the failed page is retried.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/tv/episode/TvEpisodeScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/tv/episode/TvEpisodeActivity.kt`

- [ ] **Step 1: Update TvEpisodeScreen signature and error branch**

Replace the function signature (lines 38–44) and the error branch inside the `when` block (lines 75–81):

New signature:
```kotlin
@Composable
fun TvEpisodeScreen(
    entryId: String,
    entryName: String,
    onLoginClick: () -> Unit,
    onEpisodeClick: (episode: Int, language: AnimeLanguage) -> Unit,
    onBack: () -> Unit
)
```

New error branch inside `when` (replace the `error != null ->` case):
```kotlin
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TvErrorView(
                        error = error!!,
                        onLoginClick = onLoginClick,
                        onRetryClick = {
                            if (episodes.isNullOrEmpty()) viewModel.reload()
                            else viewModel.loadIfPossible()
                        }
                    )
                }
            }
```

- [ ] **Step 2: Update TvEpisodeActivity**

Replace the entire `TvEpisodeActivity.kt`:

```kotlin
package me.proxer.app.tv.episode

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.MaterialTheme
import me.proxer.app.tv.auth.TvLoginActivity
import me.proxer.app.tv.stream.TvStreamActivity
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.startActivity
import me.proxer.library.enums.AnimeLanguage

class TvEpisodeActivity : ComponentActivity() {

    private val entryId: String get() = intent.getSafeStringExtra(ID_EXTRA)
    private val entryName: String get() = intent.getStringExtra(NAME_EXTRA) ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        val id = entryId
        val name = entryName
        setContent {
            MaterialTheme {
                TvEpisodeScreen(
                    entryId = id,
                    entryName = name,
                    onLoginClick = { activity.startActivity<TvLoginActivity>() },
                    onEpisodeClick = { episode, language ->
                        TvStreamActivity.navigateTo(activity, id, episode, language, name)
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        private const val ID_EXTRA = "id"
        private const val NAME_EXTRA = "name"
        private const val EPISODE_AMOUNT_EXTRA = "episode_amount"

        fun navigateTo(context: Context, id: String, name: String, episodeAmount: Int) {
            context.startActivity<TvEpisodeActivity>(
                ID_EXTRA to id, NAME_EXTRA to name, EPISODE_AMOUNT_EXTRA to episodeAmount
            )
        }
    }
}
```

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/tv/episode/TvEpisodeScreen.kt \
        src/main/kotlin/me/proxer/app/tv/episode/TvEpisodeActivity.kt
git commit -m "fix(tv): add onLoginClick to TvEpisodeScreen, fix pagination retry"
```

---

### Task 4: Fix TvSearchScreen + TvSearchActivity

**Context:** `TvSearchScreen` has three issues: (1) `TvErrorView` called without `onLoginClick` (dead LOGIN button), (2) `LaunchedEffect(query)` fires on first composition with an empty query, performing an unintended load and then clearing any error state without user acknowledgment when the user starts typing, (3) the loading spinner renders simultaneously with error state because they're in independent `if` blocks.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/tv/search/TvSearchScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/tv/search/TvSearchActivity.kt`

- [ ] **Step 1: Update TvSearchScreen signature**

Replace the function signature (lines 62–65):

```kotlin
@Composable
fun TvSearchScreen(
    onMediaClick: (id: String, name: String) -> Unit,
    onLoginClick: () -> Unit,
    onBack: () -> Unit
)
```

- [ ] **Step 2: Guard the empty-query LaunchedEffect**

Replace lines 97–101:

```kotlin
    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        delay(500)
        viewModel.searchQuery = query
        viewModel.reload()
    }
```

- [ ] **Step 3: Hide spinner during error state**

Replace line 133 (the `if (isLoading == true)` in the header Row):

```kotlin
            if (isLoading == true && error == null) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color.White)
            }
```

- [ ] **Step 4: Pass onLoginClick to TvErrorView**

Replace lines 139–143 (the `TvErrorView` call inside the `if (error != null)` block):

```kotlin
                TvErrorView(
                    error = error!!,
                    onLoginClick = onLoginClick,
                    onRetryClick = { viewModel.reload() }
                )
```

- [ ] **Step 5: Update TvSearchActivity**

Replace the entire `TvSearchActivity.kt`:

```kotlin
package me.proxer.app.tv.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.MaterialTheme
import me.proxer.app.tv.auth.TvLoginActivity
import me.proxer.app.tv.detail.TvMediaDetailActivity
import me.proxer.app.util.extension.startActivity

class TvSearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        setContent {
            MaterialTheme {
                TvSearchScreen(
                    onMediaClick = { id, name -> TvMediaDetailActivity.navigateTo(activity, id, name) },
                    onLoginClick = { activity.startActivity<TvLoginActivity>() },
                    onBack = { finish() }
                )
            }
        }
    }
}
```

- [ ] **Step 6: Verify it compiles**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/me/proxer/app/tv/search/TvSearchScreen.kt \
        src/main/kotlin/me/proxer/app/tv/search/TvSearchActivity.kt
git commit -m "fix(tv): add onLoginClick to TvSearchScreen, fix empty-query load"
```

---

### Task 5: Fix LoginViewModel success re-delivery

**Context:** `LoginViewModel.success` is a plain `MutableLiveData<Unit?>`. On configuration change (rotation, system back-stack restore), `observeAsState()` re-delivers the last non-null value to all observers. `TvLoginScreen` watches this via `LaunchedEffect(success)` and calls `onLoginSuccess()` when non-null. This means a rotation after a successful login re-invokes `onLoginSuccess()`, potentially double-finishing the Activity or starting duplicate Activities.

`ResettingMutableLiveData` is already used throughout the codebase for exactly this pattern (e.g., `AnimeViewModel.resolutionResult`, `AnimeViewModel.resolutionError`). It wraps the observer to suppress null deliveries and automatically resets the value to null after all observers have received it, preventing re-delivery on resubscription.

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/auth/LoginViewModel.kt`

- [ ] **Step 1: Change success to ResettingMutableLiveData**

In `LoginViewModel.kt`, replace the import and the `success` field declaration:

Replace:
```kotlin
import androidx.lifecycle.MutableLiveData
```
With (add alongside the existing MutableLiveData import — keep MutableLiveData since `error`, `isLoading`, `isTwoFactorAuthenticationEnabled` still use it):
```kotlin
import androidx.lifecycle.MutableLiveData
import me.proxer.app.util.data.ResettingMutableLiveData
```

Replace line 24:
```kotlin
    val success = MutableLiveData<Unit?>()
```
With:
```kotlin
    val success = ResettingMutableLiveData<Unit?>()
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. `ResettingMutableLiveData` extends `MutableLiveData`, so all call sites remain compatible. No changes needed in `TvLoginScreen` — the `LaunchedEffect(success)` pattern already correctly handles the case where `success` returns to null.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/auth/LoginViewModel.kt
git commit -m "fix(tv): prevent double onLoginSuccess() on config change

LoginViewModel.success was MutableLiveData; observeAsState() re-delivered
the last Unit value on recomposition after rotation, causing onLoginSuccess()
to fire twice. Switch to ResettingMutableLiveData (same pattern as
AnimeViewModel.resolutionResult) to prevent re-delivery."
```

---

## Issues Not in This Plan

These were identified in the review but excluded:

- **TvEpisodeScreen: silent ENGLISH_SUB fallback** (`languageHosterList.firstOrNull()?.first?.toAnimeLanguage() ?: AnimeLanguage.ENGLISH_SUB`) — fixing this requires a language picker UI. Product decision needed.
- **TvStreamScreen: `resolvingStreamId` state lost on Activity recreation** — architectural issue with mixing Compose `remember` state with ViewModel lifetime across back stack. Deferred.
- **TvBrowseScreen / TvEpisodeScreen blank-screen race** — 1-frame gap before `isLoading` posts to main thread. Imperceptible at 60fps on TV hardware. Not worth the complexity of distinguishing "never loaded" vs "loaded empty."
