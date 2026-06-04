# TV Navigation Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a collapsible left-rail `NavigationDrawer` to the TV frontend that surfaces News/Bookmarks/Anime/Schedule/Info/Settings sections and shows the logged-in user (or a Sign-In prompt), replacing the current ad-hoc top-bar buttons.

**Architecture:** `TvMainActivity` hosts a `TvAppShell` composable that wraps `androidx.tv.material3.NavigationDrawer` around a content slot; section routing is local state (`TvSection` enum) in the shell. Auth state is observed by `TvShellViewModel` via `storageHelper.isLoggedInObservable` and exposed as `MutableLiveData<LocalUser?>`.

**Tech Stack:** Kotlin, Compose for TV (`androidx.tv:tv-material:1.0.0`), `NavigationDrawer` / `NavigationDrawerItem` from `androidx.tv.material3`, Koin 3.5.6, RxJava 2, Coil for avatar images.

---

## File Map

| Action | Path | Responsibility |
|--------|------|---------------|
| Create | `src/main/kotlin/me/proxer/app/tv/TvSection.kt` | Section enum |
| Create | `src/main/kotlin/me/proxer/app/tv/TvShellViewModel.kt` | Auth state + logout |
| Create | `src/main/kotlin/me/proxer/app/tv/TvPlaceholderScreen.kt` | Stub for unimplemented sections |
| Create | `src/main/kotlin/me/proxer/app/tv/TvNavigationDrawer.kt` | Drawer content composable |
| Create | `src/main/kotlin/me/proxer/app/tv/TvAppShell.kt` | NavigationDrawer + section switcher |
| Modify | `src/main/kotlin/me/proxer/app/MainModules.kt` | Register TvShellViewModel in Koin |
| Modify | `src/main/kotlin/me/proxer/app/tv/TvMainActivity.kt` | Use TvAppShell instead of TvBrowseScreen |
| Modify | `src/main/kotlin/me/proxer/app/tv/TvBrowseScreen.kt` | Remove `onLoginClick` param |
| Modify | `gradle/dependencies.gradle` | Add material-icons-extended |

---

## Task 1: TvSection enum + TvShellViewModel + Koin registration

**Goal:** Create the data model and ViewModel that power auth state and section routing.

**Files:**
- Create: `src/main/kotlin/me/proxer/app/tv/TvSection.kt`
- Create: `src/main/kotlin/me/proxer/app/tv/TvShellViewModel.kt`
- Modify: `src/main/kotlin/me/proxer/app/MainModules.kt`
- Modify: `gradle/dependencies.gradle`

**Acceptance Criteria:**
- [ ] `TvSection` enum has six values: `ANIME, NEWS, BOOKMARKS, SCHEDULE, INFO, SETTINGS`
- [ ] `TvShellViewModel.user` initializes with the current user and updates whenever login state changes
- [ ] `TvShellViewModel.logout()` calls the Proxer logout endpoint; `isLoggingOut` is `true` while in-flight
- [ ] `TvShellViewModel` is registered in Koin and obtainable via `koinViewModel()`
- [ ] `material-icons-extended` added to `gradle/dependencies.gradle`

**Verify:** `./gradlew compileDebugKotlin --no-daemon --max-workers 2` → BUILD SUCCESSFUL, zero errors

**Steps:**

- [ ] **Step 1: Create `TvSection.kt`**

```kotlin
// src/main/kotlin/me/proxer/app/tv/TvSection.kt
package me.proxer.app.tv

enum class TvSection {
    ANIME, NEWS, BOOKMARKS, SCHEDULE, INFO, SETTINGS
}
```

- [ ] **Step 2: Create `TvShellViewModel.kt`**

```kotlin
// src/main/kotlin/me/proxer/app/tv/TvShellViewModel.kt
package me.proxer.app.tv

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import me.proxer.app.auth.LocalUser
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.buildSingle
import me.proxer.app.util.extension.safeInject
import me.proxer.app.util.extension.subscribeAndLogErrors
import me.proxer.library.ProxerApi

class TvShellViewModel : ViewModel() {

    private val storageHelper by safeInject<StorageHelper>()
    private val api by safeInject<ProxerApi>()

    val user = MutableLiveData<LocalUser?>(storageHelper.user)
    val logoutError = MutableLiveData<ErrorUtils.ErrorAction?>()
    val isLoggingOut = MutableLiveData<Boolean?>()

    private val disposables = CompositeDisposable()
    private var logoutDisposable: Disposable? = null

    init {
        disposables += storageHelper.isLoggedInObservable
            .subscribe { user.value = storageHelper.user }
    }

    override fun onCleared() {
        logoutDisposable?.dispose()
        disposables.dispose()
        super.onCleared()
    }

    fun logout() {
        if (isLoggingOut.value != true) {
            logoutDisposable?.dispose()
            logoutDisposable = api.user.logout()
                .buildSingle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    logoutError.value = null
                    isLoggingOut.value = true
                }
                .doAfterTerminate { isLoggingOut.value = false }
                .subscribeAndLogErrors(
                    {},
                    { logoutError.value = ErrorUtils.handle(it) }
                )
        }
    }
}
```

- [ ] **Step 3: Register in Koin — open `MainModules.kt` and add `viewModel { TvShellViewModel() }` after the `LogoutViewModel` line**

Find this block (around line 237):
```kotlin
viewModel { LoginViewModel() }
viewModel { LogoutViewModel() }
```

Add after `LogoutViewModel`:
```kotlin
viewModel { TvShellViewModel() }
```

Also add the import at the top of `MainModules.kt`:
```kotlin
import me.proxer.app.tv.TvShellViewModel
```

- [ ] **Step 4: Add `material-icons-extended` to `gradle/dependencies.gradle`**

Find the Compose block (around line 108):
```
implementation "androidx.compose.material3:material3"
```

Add after it:
```
implementation "androidx.compose.material:material-icons-extended"
```

- [ ] **Step 5: Verify compile**

```bash
./gradlew compileDebugKotlin --no-daemon --max-workers 2
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/me/proxer/app/tv/TvSection.kt \
        src/main/kotlin/me/proxer/app/tv/TvShellViewModel.kt \
        src/main/kotlin/me/proxer/app/MainModules.kt \
        gradle/dependencies.gradle
git commit -m "feat(tv): add TvSection enum and TvShellViewModel for auth state"
```

---

## Task 2: TvPlaceholderScreen

**Goal:** Provide a simple stub screen for sections (News, Bookmarks, Schedule, Info, Settings) that don't have TV implementations yet.

**Files:**
- Create: `src/main/kotlin/me/proxer/app/tv/TvPlaceholderScreen.kt`

**Acceptance Criteria:**
- [ ] `TvPlaceholderScreen(sectionName)` renders the section name centred on a dark background
- [ ] Shows "Coming soon" subtitle below the name

**Verify:** `./gradlew compileDebugKotlin --no-daemon --max-workers 2` → BUILD SUCCESSFUL

**Steps:**

- [ ] **Step 1: Create `TvPlaceholderScreen.kt`**

```kotlin
// src/main/kotlin/me/proxer/app/tv/TvPlaceholderScreen.kt
package me.proxer.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TvPlaceholderScreen(sectionName: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(sectionName, fontSize = 28.sp, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Coming soon", fontSize = 16.sp, color = Color.Gray)
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew compileDebugKotlin --no-daemon --max-workers 2
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/tv/TvPlaceholderScreen.kt
git commit -m "feat(tv): add TvPlaceholderScreen for unimplemented sections"
```

---

## Task 3: TvNavigationDrawer content composable

**Goal:** Build the stateless drawer-content composable: profile header (avatar/name + Sign In/Out), nav items for all six sections, footer items (Info, Settings).

**Files:**
- Create: `src/main/kotlin/me/proxer/app/tv/TvNavigationDrawer.kt`

**Acceptance Criteria:**
- [ ] Drawer content is an extension function on `NavigationDrawerScope` (required to call `NavigationDrawerItem`)
- [ ] Profile header: when `user == null` shows AccountCircle icon + "Sign In" text via `NavigationDrawerItem` that calls `onLoginClick`
- [ ] Profile header: when `user != null` shows avatar (Coil `AsyncImage` if `user.image` non-blank, else `AccountCircle` icon) + username + "Sign Out" supporting text via `NavigationDrawerItem` that calls `onLogoutClick`
- [ ] Nav items: Anime, News, Bookmarks, Schedule use `NavigationDrawerItem` with `Icons.Default.*` icons and animated label visibility
- [ ] Footer items: Info and Settings pinned to bottom with `Spacer(Modifier.weight(1f))` separator
- [ ] Active section item has `selected = true`

**Verify:** `./gradlew compileDebugKotlin --no-daemon --max-workers 2` → BUILD SUCCESSFUL

**Steps:**

- [ ] **Step 1: Create `TvNavigationDrawer.kt`**

```kotlin
// src/main/kotlin/me/proxer/app/tv/TvNavigationDrawer.kt
package me.proxer.app.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import coil.compose.AsyncImage
import me.proxer.app.auth.LocalUser
import me.proxer.library.util.ProxerUrls

@Composable
fun NavigationDrawerScope.TvNavigationDrawerContent(
    currentSection: TvSection,
    user: LocalUser?,
    drawerValue: DrawerValue,
    onSectionSelected: (TvSection) -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 8.dp)
    ) {
        // Profile header as a NavigationDrawerItem
        if (user != null) {
            NavigationDrawerItem(
                selected = false,
                onClick = onLogoutClick,
                leadingContent = {
                    if (user.image.isNotBlank()) {
                        AsyncImage(
                            model = ProxerUrls.userImage(user.image).toString(),
                            contentDescription = user.name,
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFF3B82F6)
                        )
                    }
                },
                supportingContent = {
                    AnimatedVisibility(visible = drawerValue == DrawerValue.Open) {
                        Text("Sign Out", color = Color(0xFFEF4444), fontSize = 11.sp)
                    }
                }
            ) {
                AnimatedVisibility(visible = drawerValue == DrawerValue.Open) {
                    Text(user.name, fontSize = 13.sp)
                }
            }
        } else {
            NavigationDrawerItem(
                selected = false,
                onClick = onLoginClick,
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Sign In",
                        modifier = Modifier.size(32.dp),
                        tint = Color.Gray
                    )
                }
            ) {
                AnimatedVisibility(visible = drawerValue == DrawerValue.Open) {
                    Text("Sign In", color = Color(0xFF3B82F6), fontSize = 13.sp)
                }
            }
        }

        // Main nav items
        TvNavItem(TvSection.ANIME, "Anime", Icons.Default.Tv, currentSection, drawerValue, onSectionSelected)
        TvNavItem(TvSection.NEWS, "News", Icons.Default.Newspaper, currentSection, drawerValue, onSectionSelected)
        TvNavItem(TvSection.BOOKMARKS, "Bookmarks", Icons.Default.Bookmarks, currentSection, drawerValue, onSectionSelected)
        TvNavItem(TvSection.SCHEDULE, "Schedule", Icons.Default.DateRange, currentSection, drawerValue, onSectionSelected)

        Spacer(Modifier.weight(1f))

        // Footer items
        TvNavItem(TvSection.INFO, "Info", Icons.Default.Info, currentSection, drawerValue, onSectionSelected)
        TvNavItem(TvSection.SETTINGS, "Settings", Icons.Default.Settings, currentSection, drawerValue, onSectionSelected)
    }
}

@Composable
private fun NavigationDrawerScope.TvNavItem(
    section: TvSection,
    label: String,
    icon: ImageVector,
    currentSection: TvSection,
    drawerValue: DrawerValue,
    onSectionSelected: (TvSection) -> Unit
) {
    NavigationDrawerItem(
        selected = currentSection == section,
        onClick = { onSectionSelected(section) },
        leadingContent = {
            Icon(imageVector = icon, contentDescription = label)
        }
    ) {
        AnimatedVisibility(visible = drawerValue == DrawerValue.Open) {
            Text(label)
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew compileDebugKotlin --no-daemon --max-workers 2
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/me/proxer/app/tv/TvNavigationDrawer.kt
git commit -m "feat(tv): add TvNavigationDrawerContent composable"
```

---

## Task 4: TvAppShell + wire TvMainActivity + update TvBrowseScreen

**Goal:** Assemble the shell composable that owns section state and wraps `NavigationDrawer`, update `TvMainActivity` to use it, and remove the now-redundant `onLoginClick` parameter from `TvBrowseScreen`.

**Files:**
- Create: `src/main/kotlin/me/proxer/app/tv/TvAppShell.kt`
- Modify: `src/main/kotlin/me/proxer/app/tv/TvMainActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/tv/TvBrowseScreen.kt`

**Acceptance Criteria:**
- [ ] `TvAppShell` wraps `NavigationDrawer` from `androidx.tv.material3` with `TvNavigationDrawerContent` as `drawerContent`
- [ ] Section switches happen via `currentSection` state; correct screen composable renders in the content slot
- [ ] Sign In navigates to `TvLoginActivity`; Sign Out calls `viewModel.logout()`
- [ ] `TvBrowseScreen` no longer has an `onLoginClick` parameter
- [ ] `TvMainActivity` uses `TvAppShell()` and passes `onMediaClick` / `onSearchClick` down
- [ ] `./gradlew assembleDebug` builds successfully

**Verify:** `./gradlew assembleDebug --no-daemon --max-workers 2` → BUILD SUCCESSFUL

**Steps:**

- [ ] **Step 1: Create `TvAppShell.kt`**

```kotlin
// src/main/kotlin/me/proxer/app/tv/TvAppShell.kt
package me.proxer.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.NavigationDrawer
import me.proxer.app.tv.auth.TvLoginActivity
import me.proxer.app.util.extension.startActivity
import org.koin.androidx.compose.koinViewModel

@Composable
fun TvAppShell(
    onMediaClick: (id: String, name: String) -> Unit,
    onSearchClick: () -> Unit
) {
    val viewModel: TvShellViewModel = koinViewModel()
    val user by viewModel.user.observeAsState()
    val context = LocalContext.current

    var currentSection by remember { mutableStateOf(TvSection.ANIME) }

    NavigationDrawer(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        drawerContent = { drawerValue ->
            TvNavigationDrawerContent(
                currentSection = currentSection,
                user = user,
                drawerValue = drawerValue,
                onSectionSelected = { currentSection = it },
                onLoginClick = { context.startActivity<TvLoginActivity>() },
                onLogoutClick = { viewModel.logout() }
            )
        }
    ) {
        when (currentSection) {
            TvSection.ANIME -> TvBrowseScreen(
                onMediaClick = onMediaClick,
                onSearchClick = onSearchClick
            )
            TvSection.NEWS -> TvPlaceholderScreen("News")
            TvSection.BOOKMARKS -> TvPlaceholderScreen("Bookmarks")
            TvSection.SCHEDULE -> TvPlaceholderScreen("Schedule")
            TvSection.INFO -> TvPlaceholderScreen("Info")
            TvSection.SETTINGS -> TvPlaceholderScreen("Settings")
        }
    }
}
```

- [ ] **Step 2: Update `TvBrowseScreen` — remove `onLoginClick` parameter**

Open `src/main/kotlin/me/proxer/app/tv/TvBrowseScreen.kt`.

Change the function signature from:
```kotlin
@Composable
fun TvBrowseScreen(
    onMediaClick: (id: String, name: String) -> Unit,
    onSearchClick: () -> Unit,
    onLoginClick: () -> Unit
)
```

To:
```kotlin
@Composable
fun TvBrowseScreen(
    onMediaClick: (id: String, name: String) -> Unit,
    onSearchClick: () -> Unit
)
```

Remove the "Sign In" button from the header Row in the body. The header Row currently contains:
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = onSearchClick) { Text("Search") }
    OutlinedButton(onClick = onLoginClick) { Text("Sign In") }
}
```

Change to:
```kotlin
OutlinedButton(onClick = onSearchClick) { Text("Search") }
```

Also pass `onLoginClick = onLoginClick` is used in `TvErrorView` call:
```kotlin
TvErrorView(
    error = error!!,
    onLoginClick = onLoginClick,
    onRetryClick = { viewModel.load() }
)
```

Check `TvErrorView`'s signature — `onLoginClick` there needs to stay because error state can prompt login. Pass a no-op or remove depending on the actual `TvErrorView` signature. Open `src/main/kotlin/me/proxer/app/tv/TvErrorView.kt` and check the `onLoginClick` usage there.

> **Note for implementer:** `TvErrorView` is defined in the TV package. If its `onLoginClick` navigates to `TvLoginActivity`, pass a lambda that does `context.startActivity<TvLoginActivity>()` using `LocalContext.current`. Example:

```kotlin
val context = LocalContext.current
// ...
TvErrorView(
    error = error!!,
    onLoginClick = { context.startActivity<TvLoginActivity>() },
    onRetryClick = { viewModel.load() }
)
```

Add `import androidx.compose.ui.platform.LocalContext` and `import me.proxer.app.tv.auth.TvLoginActivity` to `TvBrowseScreen.kt`.

- [ ] **Step 3: Update `TvMainActivity` — swap to `TvAppShell`**

Replace the entire `setContent` block in `TvMainActivity.kt`:

Before:
```kotlin
class TvMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        setContent {
            MaterialTheme {
                TvBrowseScreen(
                    onMediaClick = { id, name -> TvMediaDetailActivity.navigateTo(activity, id, name) },
                    onSearchClick = { activity.startActivity<TvSearchActivity>() },
                    onLoginClick = { activity.startActivity<TvLoginActivity>() }
                )
            }
        }
    }
}
```

After:
```kotlin
class TvMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        setContent {
            MaterialTheme {
                TvAppShell(
                    onMediaClick = { id, name -> TvMediaDetailActivity.navigateTo(activity, id, name) },
                    onSearchClick = { activity.startActivity<TvSearchActivity>() }
                )
            }
        }
    }
}
```

Remove the `import me.proxer.app.tv.auth.TvLoginActivity` line from `TvMainActivity.kt` if it was only used for the login button.

- [ ] **Step 4: Verify full build**

```bash
./gradlew assembleDebug --no-daemon --max-workers 2
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/me/proxer/app/tv/TvAppShell.kt \
        src/main/kotlin/me/proxer/app/tv/TvMainActivity.kt \
        src/main/kotlin/me/proxer/app/tv/TvBrowseScreen.kt
git commit -m "feat(tv): add TvAppShell with NavigationDrawer, wire to TvMainActivity"
```

---

## Notes

- No unit tests exist in this project (documented in CLAUDE.md). Verification is compile + build checks only.
- `isLoggedInObservable` uses `.skip(1)` — it does NOT fire at cold start. `TvShellViewModel.user` is initialized with `storageHelper.user` to cover the already-logged-in case.
- The `NavigationDrawer` from `androidx.tv.material3` auto-expands when D-pad focus enters the rail and collapses when focus leaves. No manual state management required.
- `TvErrorView`'s `onLoginClick` remains wired — it is triggered by auth-required errors, not the top-bar button.
- `.superpowers/` should be added to `.gitignore` to avoid committing brainstorm artifacts.
