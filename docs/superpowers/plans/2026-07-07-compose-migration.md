# Compose Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all XML layouts, Fragments, and RecyclerView Adapters in the mobile UI with Jetpack Compose, screen by screen.

**Architecture:** Activities call `setContent { ProxerTheme { XxxScreen() } }` directly — no Fragments. ViewModels are unchanged; Compose screens observe `LiveData` via `observeAsState()`. Special Views with no Compose equivalent (`BBCodeView`, `SubsamplingScaleImageView`, `StyledPlayerView`) are wrapped in `AndroidView { }`.

**Tech Stack:** Jetpack Compose (BOM 2026.06.01), Material3, `runtime-livedata`, Koin `koinViewModel()` / `koinInject()`, Coil `AsyncImage`, RxJava 2 (ViewModels untouched).

## Global Constraints

- All Compose deps already present in `gradle/dependencies.gradle` — do NOT add duplicates.
- Source root: `src/main/kotlin/me/proxer/app/` and `src/main/res/`.
- Build check command: `./gradlew compileDebugKotlin --no-daemon` (fast type-check).
- Full build command: `./gradlew assembleDebug --no-daemon --max-workers 2`.
- Never touch `src/main/kotlin/me/proxer/app/tv/` — TV frontend already uses Compose, out of scope.
- App widgets in `anime/schedule/widget/` and `news/widget/` — out of scope, leave as-is.
- `kotterknife` and `rxbinding` deps stay in `gradle/dependencies.gradle` until the final cleanup task (Task 21).
- `DrawerActivity` stays as the base for all non-`MainActivity` activities until their task migrates them to extend `BaseActivity` directly.
- `MaterialDrawerWrapper` stays until Task 21 (final cleanup).
- Every Activity that migrates to Compose must call `WindowCompat.setDecorFitsSystemWindows(window, false)` in `onCreate` and use `Modifier.safeDrawingPadding()` or `imePadding()` on the top-level composable.

---

### Task 1: Compose Infrastructure — `ProxerTheme` and `ContentScreen`

**Files:**
- Create: `src/main/kotlin/me/proxer/app/ui/compose/ProxerTheme.kt`
- Create: `src/main/kotlin/me/proxer/app/ui/compose/ContentScreen.kt`

**Interfaces:**
- Produces:
  - `ProxerTheme(content: @Composable () -> Unit)` — used as root wrapper in every Activity's `setContent { }`
  - `ContentScreen(isLoading: Boolean, error: ErrorAction?, onRetry: () -> Unit, isSwipeToRefreshEnabled: Boolean = false, onRefresh: () -> Unit = {}, content: @Composable () -> Unit)` — used in every screen composable

- [ ] **Step 1: Create `ProxerTheme.kt`**

```kotlin
package me.proxer.app.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import me.proxer.app.R
import me.proxer.app.util.extension.resolveColor

@Composable
fun ProxerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val primary = Color(context.resolveColor(R.attr.colorPrimary))
    val onPrimary = Color(context.resolveColor(R.attr.colorOnPrimary))
    val secondary = Color(context.resolveColor(R.attr.colorSecondary))
    val onSecondary = Color(context.resolveColor(R.attr.colorOnSecondary))
    val background = Color(context.resolveColor(android.R.attr.colorBackground))
    val surface = Color(context.resolveColor(R.attr.colorSurface))
    val onSurface = Color(context.resolveColor(R.attr.colorOnSurface))
    val error = Color(context.resolveColor(R.attr.colorError))

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            background = background,
            surface = surface,
            onSurface = onSurface,
            error = error,
        ),
        content = content,
    )
}
```

Note: `resolveColor` is already in `util/extension/ViewExtensions.kt` as `fun Context.resolveColor(@AttrRes attr: Int): Int`. The XML theme is applied by `BaseActivity` before `setContent` is called, so `LocalContext.current.resolveColor` reads the correct theme-aware color.

- [ ] **Step 2: Create `ContentScreen.kt`**

```kotlin
package me.proxer.app.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.proxer.app.R
import me.proxer.app.util.ErrorUtils.ErrorAction
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
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(error.message))
                        if (error.buttonMessage != ACTION_MESSAGE_HIDE) {
                            Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                                val label = when (error.buttonMessage) {
                                    ActionMessage.ACTION_MESSAGE_DEFAULT -> stringResource(R.string.error_action_retry)
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
```

Fix the import — `ACTION_MESSAGE_DEFAULT` is in the companion object:
```kotlin
import me.proxer.app.util.ErrorUtils.ErrorAction.Companion.ACTION_MESSAGE_DEFAULT
```

Replace the `when` label with:
```kotlin
ACTION_MESSAGE_DEFAULT -> stringResource(R.string.error_action_retry)
```

- [ ] **Step 3: Build check**

```
./gradlew compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL (or only pre-existing warnings).

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/me/proxer/app/ui/compose/ProxerTheme.kt \
        src/main/kotlin/me/proxer/app/ui/compose/ContentScreen.kt
git commit -m "feat: add ProxerTheme and ContentScreen compose infrastructure"
```

---

### Task 2: MainActivity — Compose Navigation Drawer

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/MainActivity.kt`
- Create: `src/main/kotlin/me/proxer/app/ui/compose/MainScreen.kt`
- `DrawerActivity`, `MaterialDrawerWrapper`, other `DrawerActivity` subclasses: **unchanged in this task**

**Interfaces:**
- Consumes: `ProxerTheme` from Task 1
- Produces: `MainActivity` now calls `setContent { }` — no XML layout needed from `DrawerActivity`

**Key decision:** `MainActivity` changes to extend `BaseActivity` directly (not `DrawerActivity`). `DrawerActivity` remains for all other activities until their individual migration tasks.

- [ ] **Step 1: Create `MainScreen.kt`**

```kotlin
package me.proxer.app.ui.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.anime.schedule.ScheduleScreen
import me.proxer.app.bookmark.BookmarkScreen
import me.proxer.app.chat.ChatContainerScreen
import me.proxer.app.media.list.MediaListScreen
import me.proxer.app.news.NewsScreen
import me.proxer.app.settings.AboutScreen
import me.proxer.app.settings.SettingsScreen
import me.proxer.app.util.wrapper.MaterialDrawerWrapper.DrawerItem
import me.proxer.library.enums.Category

private data class DrawerEntry(
    val item: DrawerItem,
    val labelRes: Int,
    val icon: ImageVector,
)

private val drawerEntries = listOf(
    DrawerEntry(DrawerItem.NEWS, R.string.section_news, Icons.Default.Notifications),
    DrawerEntry(DrawerItem.CHAT, R.string.section_chat, Icons.Default.Chat),
    DrawerEntry(DrawerItem.BOOKMARKS, R.string.section_bookmarks, Icons.Default.Bookmarks),
    DrawerEntry(DrawerItem.ANIME, R.string.section_anime, Icons.Default.OndemandVideo),
    DrawerEntry(DrawerItem.SCHEDULE, R.string.section_schedule, Icons.Default.CalendarMonth),
    DrawerEntry(DrawerItem.MANGA, R.string.section_manga, Icons.Default.MenuBook),
    DrawerEntry(DrawerItem.INFO, R.string.section_info, Icons.Default.Info),
    DrawerEntry(DrawerItem.SETTINGS, R.string.section_settings, Icons.Default.Settings),
)

@Composable
fun MainScreen(initialItem: DrawerItem) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedItem by rememberSaveable { mutableStateOf(initialItem) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                drawerEntries.forEach { entry ->
                    NavigationDrawerItem(
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.labelRes)) },
                        selected = selectedItem == entry.item,
                        onClick = {
                            selectedItem = entry.item
                            scope.launch { drawerState.close() }
                        },
                    )
                }
            }
        },
    ) {
        when (selectedItem) {
            DrawerItem.NEWS -> NewsScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
            DrawerItem.CHAT, DrawerItem.MESSENGER -> ChatContainerScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
            DrawerItem.BOOKMARKS -> BookmarkScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
            DrawerItem.ANIME -> MediaListScreen(category = Category.ANIME, onOpenDrawer = { scope.launch { drawerState.open() } })
            DrawerItem.SCHEDULE -> ScheduleScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
            DrawerItem.MANGA -> MediaListScreen(category = Category.MANGA, onOpenDrawer = { scope.launch { drawerState.open() } })
            DrawerItem.INFO -> AboutScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
            DrawerItem.SETTINGS -> SettingsScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
        }
    }
}
```

Note: `NewsScreen`, `BookmarkScreen`, etc. are stub composables that will be implemented in their respective tasks. Add stubs now:

- [ ] **Step 2: Add screen stubs** — create each stub in the relevant package so `MainScreen.kt` compiles. Each stub is a `Box(Modifier.fillMaxSize())` placeholder:

`src/main/kotlin/me/proxer/app/news/NewsScreen.kt`:
```kotlin
package me.proxer.app.news
import androidx.compose.foundation.layout.Box; import androidx.compose.foundation.layout.fillMaxSize; import androidx.compose.material3.Text; import androidx.compose.runtime.Composable; import androidx.compose.ui.Alignment; import androidx.compose.ui.Modifier
@Composable fun NewsScreen(onOpenDrawer: () -> Unit) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("News — TODO") } }
```

Create equivalent stubs for:
- `src/main/kotlin/me/proxer/app/chat/ChatContainerScreen.kt` — `package me.proxer.app.chat`
- `src/main/kotlin/me/proxer/app/bookmark/BookmarkScreen.kt` — `package me.proxer.app.bookmark`
- `src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt` — `package me.proxer.app.media.list`, signature: `fun MediaListScreen(category: Category, onOpenDrawer: () -> Unit)`
- `src/main/kotlin/me/proxer/app/anime/schedule/ScheduleScreen.kt` — `package me.proxer.app.anime.schedule`
- `src/main/kotlin/me/proxer/app/settings/AboutScreen.kt` — `package me.proxer.app.settings`
- `src/main/kotlin/me/proxer/app/settings/SettingsScreen.kt` — `package me.proxer.app.settings`

- [ ] **Step 3: Update `MainActivity.kt`**

Change `class MainActivity : DrawerActivity()` → `class MainActivity : BaseActivity()`.

Replace `onCreate` body. The full new `MainActivity.kt`:

```kotlin
package me.proxer.app

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.settings.theme.Theme
import me.proxer.app.settings.theme.ThemeContainer
import me.proxer.app.settings.theme.ThemeVariant
import me.proxer.app.ui.compose.MainScreen
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.intentFor
import me.proxer.app.util.wrapper.IntroductionWrapper
import me.proxer.app.util.wrapper.MaterialDrawerWrapper.DrawerItem

class MainActivity : BaseActivity() {
    companion object {
        private const val SECTION_EXTRA = "section"
        private const val SECTION_ACTION_PREFIX = "me.proxer.app.intent.action."

        fun navigateToSection(context: Context, section: DrawerItem) =
            context.startActivity(getSectionIntent(context, section))

        fun getSectionIntent(context: Context, section: DrawerItem): Intent =
            context.intentFor<MainActivity>(SECTION_EXTRA to section)
                .setAction(SECTION_ACTION_PREFIX + section.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (BuildConfig.LOG && VERSION.SDK_INT >= VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE), 1)
        }

        val shouldIntroduce = preferenceHelper.launches <= 0 && intent.action == Intent.ACTION_MAIN
        if (shouldIntroduce) {
            preferenceHelper.incrementLaunches()
            IntroductionWrapper.introduce(this)
            return
        }

        if (intent.action == Intent.ACTION_MAIN) {
            preferenceHelper.incrementLaunches()
        }

        val initialItem = getItemToLoad()

        setContent {
            ProxerTheme {
                MainScreen(initialItem = initialItem)
            }
        }
    }

    private fun getItemToLoad(): DrawerItem {
        if (intent.action == Intent.ACTION_VIEW) {
            val section = when (intent.data?.pathSegments?.firstOrNull()) {
                "news" -> DrawerItem.NEWS
                "chat" -> DrawerItem.CHAT
                "messages" -> DrawerItem.MESSENGER
                "reminder" -> DrawerItem.BOOKMARKS
                "anime" -> DrawerItem.ANIME
                "calendar" -> DrawerItem.SCHEDULE
                "manga" -> DrawerItem.MANGA
                else -> null
            }
            if (section != null) return section
        }
        val sectionExtra = IntentCompat.getSerializableExtra(intent, SECTION_EXTRA, DrawerItem::class.java)
        return sectionExtra ?: preferenceHelper.startPage
    }
}
```

Note: `preferenceHelper.incrementLaunches()` — check if `incrementLaunches()` is the right method name; if not, call `preferenceHelper.launches` setter indirectly. Looking at the source, `launches` has a private setter — add a public `incrementLaunches()` method to `PreferenceHelper` if needed, or replicate the existing logic in `MainActivity`. Existing `MainActivity` calls `preferenceHelper.incrementLaunches()` so it already exists.

- [ ] **Step 4: Remove imports no longer used in `MainActivity.kt`**

Remove any import for `DrawerActivity`, `TabLayout`, `kotterknife.bindView`, `Fragment`, `commitNow`, `IntroductionBuilder`, `OPTION_RESULT`, `IntroductionWrapper` (keep if still used), `InAppUpdateFlow`, `RatingDialog`, `ProfileSettingsViewModel`.

- [ ] **Step 5: Build check**

```
./gradlew compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/me/proxer/app/MainActivity.kt \
        src/main/kotlin/me/proxer/app/ui/compose/MainScreen.kt \
        src/main/kotlin/me/proxer/app/news/NewsScreen.kt \
        src/main/kotlin/me/proxer/app/chat/ChatContainerScreen.kt \
        src/main/kotlin/me/proxer/app/bookmark/BookmarkScreen.kt \
        src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt \
        src/main/kotlin/me/proxer/app/anime/schedule/ScheduleScreen.kt \
        src/main/kotlin/me/proxer/app/settings/AboutScreen.kt \
        src/main/kotlin/me/proxer/app/settings/SettingsScreen.kt
git commit -m "feat: migrate MainActivity to Compose with ModalNavigationDrawer"
```

---

### Task 3: ServerStatus screen (canonical list-screen pattern)

This task is the template for all subsequent list-screen migrations. Follow its exact pattern.

**Files:**
- Create: `src/main/kotlin/me/proxer/app/settings/status/ServerStatusScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/settings/status/ServerStatusActivity.kt`
- Delete: `src/main/kotlin/me/proxer/app/settings/status/ServerStatusFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/settings/status/ServerStatusAdapter.kt`
- Delete: `src/main/res/layout/fragment_server_status.xml`
- Delete: `src/main/res/layout/item_server_status.xml`

**Interfaces:**
- Consumes: `ContentScreen` from Task 1, `ProxerTheme` from Task 1
- Produces: `ServerStatusScreen` composable (used only by `ServerStatusActivity`)

- [ ] **Step 1: Create `ServerStatusScreen.kt`**

```kotlin
package me.proxer.app.settings.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LanguageOff
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.proxer.app.R
import me.proxer.app.ui.compose.ContentScreen
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerStatusScreen() {
    val viewModel = koinViewModel<ServerStatusViewModel>()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.section_server_status)) })
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading == true,
            error = error,
            onRetry = { viewModel.load() },
            isSwipeToRefreshEnabled = true,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding),
        ) {
            val servers = data ?: return@ContentScreen
            Column {
                val allOnline = servers.all { it.online }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Icon(
                            if (allOnline) Icons.Default.Language else Icons.Default.LanguageOff,
                            contentDescription = null,
                            tint = if (allOnline) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = stringResource(
                                if (allOnline) R.string.fragment_server_status_overall_online
                                else R.string.fragment_server_status_overall_offline,
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(servers) { server -> ServerStatusItem(server) }
                }
            }
        }
    }
}

@Composable
private fun ServerStatusItem(server: ServerStatus) {
    Card(modifier = Modifier.padding(4.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                when (server.type) {
                    ServerType.MAIN -> Icons.Default.Language
                    ServerType.MANGA -> Icons.Default.Book
                    ServerType.STREAM -> Icons.Default.Tv
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(server.name, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Icon(
                if (server.online) Icons.Default.Language else Icons.Default.LanguageOff,
                contentDescription = null,
                tint = if (server.online) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
```

- [ ] **Step 2: Replace `ServerStatusActivity.kt`**

```kotlin
package me.proxer.app.settings.status

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.startActivity

class ServerStatusActivity : BaseActivity() {
    companion object {
        fun navigateTo(context: Activity) = context.startActivity<ServerStatusActivity>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                ServerStatusScreen()
            }
        }
    }
}
```

- [ ] **Step 3: Delete old files**

```bash
rm src/main/kotlin/me/proxer/app/settings/status/ServerStatusFragment.kt
rm src/main/kotlin/me/proxer/app/settings/status/ServerStatusAdapter.kt
rm src/main/res/layout/fragment_server_status.xml
rm src/main/res/layout/item_server_status.xml
```

- [ ] **Step 4: Build check**

```
./gradlew compileDebugKotlin --no-daemon
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: migrate ServerStatus screen to Compose"
```

---

### Task 4: About screen

**Files:**
- Replace stub: `src/main/kotlin/me/proxer/app/settings/AboutScreen.kt` (created as stub in Task 2)
- Modify: `src/main/kotlin/me/proxer/app/MainActivity.kt` (if `AboutFragment` import present, remove it)
- Keep: `src/main/kotlin/me/proxer/app/settings/AboutFragment.kt` — **do not delete yet** (it's referenced by `DrawerActivity`/nothing now). Actually check: `AboutFragment` was replaced in `MainActivity` in Task 2, so it can be deleted now.
- Delete: `src/main/kotlin/me/proxer/app/settings/AboutFragment.kt`
- Delete: `src/main/res/layout/fragment_about.xml`
- Delete: `src/main/res/layout/layout_about_row.xml`

**Note:** `AboutFragment` uses `MaterialAboutFragment` (third-party library `com.danielstone.materialaboutlibrary`). The Compose replacement uses a `LazyColumn` of clickable rows — no library dependency needed.

- [ ] **Step 1: Replace `AboutScreen.kt` stub with full implementation**

```kotlin
package me.proxer.app.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.getSystemService
import me.proxer.app.BuildConfig
import me.proxer.app.R
import me.proxer.app.settings.status.ServerStatusActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onOpenDrawer: () -> Unit = {}) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_info)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.section_server_status)) },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                    modifier = Modifier.clickable { ServerStatusActivity.navigateTo(context as android.app.Activity) },
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_contact_email)) },
                    supportingContent = { Text("appsupport@proxer.de") },
                    leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService<ClipboardManager>()
                        clipboard?.setPrimaryClip(ClipData.newPlainText("email", "appsupport@proxer.de"))
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("GitHub") },
                    supportingContent = { Text("github.com/proxer/ProxerAndroid") },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/proxer/ProxerAndroid")))
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_app_version)) },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    leadingContent = { Icon(Icons.Default.Group, contentDescription = null) },
                )
            }
        }
    }
}
```

Adapt to include all rows from the original `AboutFragment.getMaterialAboutList()` — team link, Facebook, Twitter, YouTube, Discord (no icon), version, open source libs link.

- [ ] **Step 2: Delete old files**

```bash
rm src/main/kotlin/me/proxer/app/settings/AboutFragment.kt
rm src/main/res/layout/fragment_about.xml
rm src/main/res/layout/layout_about_row.xml
```

- [ ] **Step 3: Build check + commit**

```
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate About screen to Compose"
```

---

### Task 5: Settings + ProfileSettings screens

**Files:**
- Replace stub: `src/main/kotlin/me/proxer/app/settings/SettingsScreen.kt`
- Create: `src/main/kotlin/me/proxer/app/profile/settings/ProfileSettingsScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/profile/settings/ProfileSettingsActivity.kt` — extend `BaseActivity`, call `setContent { }`
- Delete: `src/main/kotlin/me/proxer/app/settings/SettingsFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/profile/settings/ProfileSettingsFragment.kt`
- Delete: `src/main/res/layout/activity_profile_settings.xml`
- Delete menu XML: `src/main/res/menu/fragment_notifications.xml` (if settings-owned)

**Note:** `SettingsFragment` extends `PreferenceFragmentCompat`. Compose has no `PreferenceScreen` equivalent. Use `LazyColumn` with `ListItem` composables — one per preference. Toggle preferences with `Switch`, single-choice with `AlertDialog`.

- [ ] **Step 1: Replace `SettingsScreen.kt` stub**

```kotlin
package me.proxer.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.proxer.app.R
import me.proxer.app.util.data.PreferenceHelper
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenDrawer: () -> Unit = {}) {
    val preferenceHelper = koinInject<PreferenceHelper>()
    var newsNotifications by remember { mutableStateOf(preferenceHelper.areNewsNotificationsEnabled) }
    var accountNotifications by remember { mutableStateOf(preferenceHelper.areAccountNotificationsEnabled) }
    var ageRestricted by remember { mutableStateOf(preferenceHelper.isAgeRestrictedMediaAllowed) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_settings)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_notifications_news)) },
                    trailingContent = {
                        Switch(
                            checked = newsNotifications,
                            onCheckedChange = {
                                newsNotifications = it
                                preferenceHelper.areNewsNotificationsEnabled = it
                            },
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_notifications_account)) },
                    trailingContent = {
                        Switch(
                            checked = accountNotifications,
                            onCheckedChange = {
                                accountNotifications = it
                                preferenceHelper.areAccountNotificationsEnabled = it
                            },
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_age_restricted_media)) },
                    trailingContent = {
                        Switch(
                            checked = ageRestricted,
                            onCheckedChange = {
                                ageRestricted = it
                                preferenceHelper.isAgeRestrictedMediaAllowed = it
                            },
                        )
                    },
                )
            }
        }
    }
}
```

Add all remaining preference items from `SettingsFragment` (start page, theme, link check, auto-bookmark, cellular check, manga reader orientation, HTTP logging — debug only). Use `AlertDialog` for single-choice preferences like start page and theme.

- [ ] **Step 2: Create `ProfileSettingsScreen.kt`** — follows the same `LazyColumn`/`ListItem` pattern. Observes `ProfileSettingsViewModel` for user-specific settings.

- [ ] **Step 3: Update `ProfileSettingsActivity.kt`**

Change to extend `BaseActivity`. Replace `setContentView` + Fragment transaction with:
```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
setContent { ProxerTheme { ProfileSettingsScreen() } }
```

- [ ] **Step 4: Delete old files**

```bash
rm src/main/kotlin/me/proxer/app/settings/SettingsFragment.kt
rm src/main/kotlin/me/proxer/app/profile/settings/ProfileSettingsFragment.kt
rm src/main/res/layout/activity_profile_settings.xml
```

- [ ] **Step 5: Build check + commit**

```
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate Settings and ProfileSettings screens to Compose"
```

---

### Task 6: Schedule screen

**Files:**
- Replace stub: `src/main/kotlin/me/proxer/app/anime/schedule/ScheduleScreen.kt`
- Delete: `src/main/kotlin/me/proxer/app/anime/schedule/ScheduleFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/anime/schedule/ScheduleAdapter.kt`
- Delete: `src/main/kotlin/me/proxer/app/anime/schedule/ScheduleEntryAdapter.kt`
- Delete: `src/main/res/layout/fragment_schedule.xml`
- Delete: `src/main/res/layout/item_schedule_day.xml`
- Delete: `src/main/res/layout/item_schedule_entry.xml`

- [ ] **Step 1: Replace `ScheduleScreen.kt` stub**

```kotlin
package me.proxer.app.anime.schedule

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.proxer.app.R
import me.proxer.app.ui.compose.ContentScreen
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onOpenDrawer: () -> Unit = {}) {
    val viewModel = koinViewModel<ScheduleViewModel>()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_schedule)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null) }
                },
            )
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading == true,
            error = error,
            onRetry = { viewModel.load() },
            isSwipeToRefreshEnabled = true,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding),
        ) {
            // ScheduleViewModel returns grouped data — inspect ScheduleViewModel.dataSingle
            // return type to determine the data model, then render day headers + entry rows.
            val days = data ?: return@ContentScreen
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // days is a List<ScheduleDay> where ScheduleDay has a label and List<ScheduleEntry>
                // Adapt to actual return type of ScheduleViewModel.
                items(days) { day ->
                    Text(day.name, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    day.entries.forEach { entry ->
                        ListItem(
                            headlineContent = { Text(entry.name) },
                            supportingContent = { Text(entry.episode.toString()) },
                        )
                    }
                }
            }
        }
    }
}
```

Inspect `ScheduleViewModel` and `ScheduleAdapter` to determine the actual data types and adapt the composable accordingly. Map all existing `item_schedule_day.xml` and `item_schedule_entry.xml` fields to `ListItem` / custom composables.

- [ ] **Step 2: Delete old files + build check + commit**

```bash
rm src/main/kotlin/me/proxer/app/anime/schedule/ScheduleFragment.kt
rm src/main/kotlin/me/proxer/app/anime/schedule/ScheduleAdapter.kt
rm src/main/kotlin/me/proxer/app/anime/schedule/ScheduleEntryAdapter.kt
rm src/main/res/layout/fragment_schedule.xml
rm src/main/res/layout/item_schedule_day.xml
rm src/main/res/layout/item_schedule_entry.xml
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate Schedule screen to Compose"
```

---

### Task 7: News screen

**Files:**
- Replace stub: `src/main/kotlin/me/proxer/app/news/NewsScreen.kt`
- Delete: `src/main/kotlin/me/proxer/app/news/NewsFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/news/NewsAdapter.kt`
- Delete: `src/main/res/layout/fragment_paged.xml` — **only delete if no other screen still uses it. Check first. If other screens still use Fragment+ViewPager, keep it.**
- Delete: `src/main/res/layout/item_news.xml`

**Paged loading pattern:** `NewsViewModel` extends `PagedViewModel`. In Compose, trigger `viewModel.loadIfPossible()` when the last visible item is near the list end:

```kotlin
val listState = rememberLazyListState()
LaunchedEffect(listState.firstVisibleItemIndex) {
    val total = listState.layoutInfo.totalItemsCount
    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    if (total > 0 && last >= total - 5) viewModel.loadIfPossible()
}
```

- [ ] **Step 1: Replace `NewsScreen.kt` stub**

```kotlin
package me.proxer.app.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import me.proxer.app.R
import me.proxer.app.forum.TopicActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.library.entity.notifications.NewsArticle
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(onOpenDrawer: () -> Unit = {}) {
    val viewModel = koinViewModel<NewsViewModel>()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(listState.firstVisibleItemIndex) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) viewModel.loadIfPossible()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_news)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null) }
                },
            )
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading == true && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = { viewModel.load() },
            isSwipeToRefreshEnabled = true,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(data ?: emptyList()) { article ->
                    NewsItem(
                        article = article,
                        onClick = {
                            TopicActivity.navigateTo(
                                context as android.app.Activity,
                                article.threadId,
                                article.categoryId,
                                article.subject,
                            )
                        },
                    )
                }
                if (isLoading == true && !data.isNullOrEmpty()) {
                    item { androidx.compose.material3.CircularProgressIndicator() }
                }
            }
        }
    }
}

@Composable
private fun NewsItem(article: NewsArticle, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(article.subject) },
        supportingContent = { Text(article.description) },
        leadingContent = {
            AsyncImage(
                model = ProxerUrls.newsImage(article.id, article.image).toString(),
                contentDescription = null,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
```

- [ ] **Step 2: Delete old files + build check + commit**

```bash
rm src/main/kotlin/me/proxer/app/news/NewsFragment.kt
rm src/main/kotlin/me/proxer/app/news/NewsAdapter.kt
rm src/main/res/layout/item_news.xml
# Only delete fragment_paged.xml when ALL paged fragments are gone (do this in Task 21)
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate News screen to Compose"
```

---

### Task 8: Bookmarks screen

**Files:**
- Replace stub: `src/main/kotlin/me/proxer/app/bookmark/BookmarkScreen.kt`
- Delete: `src/main/kotlin/me/proxer/app/bookmark/BookmarkFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/bookmark/BookmarkAdapter.kt`
- Delete: `src/main/res/layout/item_bookmark.xml`
- Delete: `src/main/res/layout/item_bookmark_language.xml`
- Delete: `src/main/res/menu/fragment_bookmarks.xml`

- [ ] **Step 1: Replace `BookmarkScreen.kt` stub**

Follow the paged list pattern from Task 7 (`NewsScreen`). Key differences:
- `BookmarkViewModel` is the ViewModel class
- Items navigate to `AnimeActivity.navigateTo()` or the manga equivalent depending on `bookmark.category`
- Toolbar has filter menu action (filter by category) — add `IconButton` to `TopAppBar` actions
- Uses `item_bookmark.xml` fields: cover image, title, episode, language chip row (`item_bookmark_language.xml`)

```kotlin
package me.proxer.app.bookmark

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import me.proxer.app.R
import me.proxer.app.anime.AnimeActivity
import me.proxer.app.manga.MangaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.library.enums.Category
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(onOpenDrawer: () -> Unit = {}) {
    val viewModel = koinViewModel<BookmarkViewModel>()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(listState.firstVisibleItemIndex) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) viewModel.loadIfPossible()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_bookmarks)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null) }
                },
            )
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading == true && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = { viewModel.load() },
            isSwipeToRefreshEnabled = true,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(data ?: emptyList()) { bookmark ->
                    ListItem(
                        headlineContent = { Text(bookmark.name) },
                        modifier = Modifier.clickable {
                            // Navigate based on category — inspect BookmarkViewModel data type
                            // for exact field names (bookmark.category, bookmark.id, etc.)
                        },
                    )
                }
            }
        }
    }
}
```

Inspect `BookmarkAdapter` and `BookmarkViewModel` for exact data type fields and fill in navigation + item display.

- [ ] **Step 2: Delete old files + build check + commit**

```bash
rm src/main/kotlin/me/proxer/app/bookmark/BookmarkFragment.kt
rm src/main/kotlin/me/proxer/app/bookmark/BookmarkAdapter.kt
rm src/main/res/layout/item_bookmark.xml
rm src/main/res/layout/item_bookmark_language.xml
rm src/main/res/menu/fragment_bookmarks.xml
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate Bookmarks screen to Compose"
```

---

### Task 9: Notifications screen

**Files:**
- Create: `src/main/kotlin/me/proxer/app/notification/NotificationScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/notification/NotificationActivity.kt` — extend `BaseActivity`, `setContent { }`
- Delete: `src/main/kotlin/me/proxer/app/notification/NotificationFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/notification/NotificationAdapter.kt`
- Delete: `src/main/kotlin/me/proxer/app/notification/NotificationDeletionConfirmationDialog.kt` — replace with `AlertDialog` in `NotificationScreen`
- Delete: `src/main/res/layout/item_notification.xml`
- Delete: `src/main/res/menu/fragment_notifications.xml`

- [ ] **Step 1: Create `NotificationScreen.kt`** — paged list pattern (same as Task 7/8). Observe `NotificationViewModel`. Items show notification title + date. Swipe-to-delete: use `SwipeToDismissBox` from Material3. Delete confirmation: `AlertDialog` composable instead of `NotificationDeletionConfirmationDialog`.

- [ ] **Step 2: Update `NotificationActivity.kt`** to extend `BaseActivity`, call `setContent { ProxerTheme { NotificationScreen() } }`.

- [ ] **Step 3: Delete old files + build check + commit**

```bash
rm src/main/kotlin/me/proxer/app/notification/NotificationFragment.kt
rm src/main/kotlin/me/proxer/app/notification/NotificationAdapter.kt
rm src/main/kotlin/me/proxer/app/notification/NotificationDeletionConfirmationDialog.kt
rm src/main/res/layout/item_notification.xml
rm src/main/res/menu/fragment_notifications.xml
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate Notifications screen to Compose"
```

---

### Task 10: Media list screen (anime/manga browse)

**Files:**
- Replace stub: `src/main/kotlin/me/proxer/app/media/list/MediaListScreen.kt`
- Delete: `src/main/kotlin/me/proxer/app/media/list/MediaListFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/media/list/MediaAdapter.kt`
- Delete: `src/main/kotlin/me/proxer/app/media/list/MediaListSearchBottomSheet.kt` — replace with `ModalBottomSheet`
- Delete: `src/main/res/layout/fragment_media_list.xml`
- Delete: `src/main/res/layout/fragment_media_list_bottom_sheet.xml`
- Delete: `src/main/res/layout/item_media_entry.xml`
- Delete: `src/main/res/menu/fragment_media_list.xml`

- [ ] **Step 1: Replace `MediaListScreen.kt` stub**

```kotlin
package me.proxer.app.media.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import me.proxer.app.R
import me.proxer.app.media.MediaActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.library.enums.Category
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListScreen(category: Category, onOpenDrawer: () -> Unit = {}) {
    val viewModel = koinViewModel<MediaListViewModel>()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState()
    var showFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(listState.firstVisibleItemIndex) {
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && last >= total - 5) viewModel.loadIfPossible()
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = bottomSheetState,
        ) {
            // Filter options — map fields from MediaListSearchBottomSheet to Compose
            // (genre chips, type selector, sort order, etc.)
            Text("Filters — TODO: map from MediaListSearchBottomSheet")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (category == Category.ANIME) R.string.section_anime else R.string.section_manga)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null) }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading == true && data.isNullOrEmpty(),
            error = if (data.isNullOrEmpty()) error else null,
            onRetry = { viewModel.load() },
            isSwipeToRefreshEnabled = true,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(data ?: emptyList()) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.name) },
                        leadingContent = {
                            AsyncImage(model = entry.image.toString(), contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            MediaActivity.navigateTo(context as android.app.Activity, entry.id, entry.name, category)
                        },
                    )
                }
            }
        }
    }
}
```

Inspect `MediaListViewModel`, `MediaAdapter`, and `MediaListSearchBottomSheet` for exact field names and filter parameters. Map `ExpandableSelectionView` genres to `FlowRow` + `FilterChip` composables.

- [ ] **Step 2: Delete old files + build check + commit**

```bash
rm src/main/kotlin/me/proxer/app/media/list/MediaListFragment.kt
rm src/main/kotlin/me/proxer/app/media/list/MediaAdapter.kt
rm src/main/kotlin/me/proxer/app/media/list/MediaListSearchBottomSheet.kt
rm src/main/res/layout/fragment_media_list.xml
rm src/main/res/layout/fragment_media_list_bottom_sheet.xml
rm src/main/res/layout/item_media_entry.xml
rm src/main/res/menu/fragment_media_list.xml
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate MediaList screen to Compose"
```

---

### Task 11: Media detail screen (tabs: episodes, comments, recommendations, relations, discussion)

**Files:**
- Create: `src/main/kotlin/me/proxer/app/media/MediaScreen.kt`
- Create: `src/main/kotlin/me/proxer/app/media/episode/EpisodeScreen.kt`
- Create: `src/main/kotlin/me/proxer/app/media/comments/CommentsScreen.kt`
- Create: `src/main/kotlin/me/proxer/app/media/recommendation/RecommendationScreen.kt`
- Create: `src/main/kotlin/me/proxer/app/media/relation/RelationScreen.kt`
- Create: `src/main/kotlin/me/proxer/app/media/discussion/DiscussionScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/media/MediaActivity.kt` — change to extend `BaseActivity`, call `setContent { }`
- Delete: `src/main/kotlin/me/proxer/app/media/episode/EpisodeFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/media/episode/EpisodeAdapter.kt`
- Delete: `src/main/kotlin/me/proxer/app/media/episode/BookmarkLanguageDialog.kt` — replace with `AlertDialog`
- Delete: `src/main/kotlin/me/proxer/app/media/comments/CommentsFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/media/comments/CommentsAdapter.kt`
- Delete: `src/main/kotlin/me/proxer/app/media/recommendation/RecommendationFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/media/recommendation/RecommendationAdapter.kt`
- Delete: `src/main/kotlin/me/proxer/app/media/relation/RelationFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/media/relation/RelationAdapter.kt`
- Delete: `src/main/kotlin/me/proxer/app/media/discussion/DiscussionFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/media/discussion/DiscussionAdapter.kt`
- Delete: `src/main/kotlin/me/proxer/app/media/info/MediaInfoFragment.kt`
- Delete: all `fragment_episode.xml`, `fragment_comments.xml`, `fragment_recommendation.xml`, `fragment_relation.xml`, `fragment_discussion.xml`, `fragment_media_info.xml`
- Delete: all corresponding `item_*.xml` for this screen
- Delete: `src/main/kotlin/me/proxer/app/base/ImageTabsActivity.kt` — only if `MediaActivity` and `ProfileActivity` (Task 12) are both migrated; do it in Task 12 cleanup

- [ ] **Step 1: Create `MediaScreen.kt`** — `TabRow` + `HorizontalPager` shell

```kotlin
package me.proxer.app.media

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.media.comments.CommentsScreen
import me.proxer.app.media.discussion.DiscussionScreen
import me.proxer.app.media.episode.EpisodeScreen
import me.proxer.app.media.recommendation.RecommendationScreen
import me.proxer.app.media.relation.RelationScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(id: String, name: String, onBack: () -> Unit) {
    val tabs = listOf(
        R.string.section_media_episodes,
        R.string.section_media_comments,
        R.string.section_media_recommendations,
        R.string.section_media_relations,
        R.string.section_media_discussions,
    )
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, titleRes ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(titleRes)) },
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> EpisodeScreen(mediaId = id)
                    1 -> CommentsScreen(mediaId = id)
                    2 -> RecommendationScreen(mediaId = id)
                    3 -> RelationScreen(mediaId = id)
                    4 -> DiscussionScreen(mediaId = id)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Create each tab screen** (`EpisodeScreen`, `CommentsScreen`, `RecommendationScreen`, `RelationScreen`, `DiscussionScreen`) — each follows the paged list pattern. `EpisodeScreen` has language expansion rows (replace `item_episode.xml` + `layout_episode_language.xml` with expandable `ListItem`). BBCode in `CommentsScreen` uses `AndroidView { BBCodeView(context).apply { setCode(comment.content, args) } }`.

- [ ] **Step 3: Update `MediaActivity.kt`**

Change to extend `BaseActivity`. Replace `setContentView` + `ImageTabsActivity` machinery with:
```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
setContent {
    ProxerTheme {
        MediaScreen(
            id = id,
            name = name ?: "",
            onBack = { finish() },
        )
    }
}
```

Read `id` and `name` from intent extras as before.

- [ ] **Step 4: Delete old files + build check + commit**

```bash
# Delete all listed files above
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate MediaDetail screen to Compose"
```

---

### Task 12: Profile screen (tabs: info, about, media lists, comments, history, top-ten)

**Files:**
- Create: `src/main/kotlin/me/proxer/app/profile/ProfileScreen.kt`
- Create tab screens: `ProfileInfoScreen.kt`, `ProfileAboutScreen.kt`, `ProfileMediaListScreen.kt`, `ProfileCommentScreen.kt`, `HistoryScreen.kt`, `TopTenScreen.kt` (each in their existing package)
- Modify: `src/main/kotlin/me/proxer/app/profile/ProfileActivity.kt` — extend `BaseActivity`, `setContent { }`
- Delete: all Fragment files in `profile/`
- Delete: all Adapter files in `profile/`
- Delete: `src/main/kotlin/me/proxer/app/base/ImageTabsActivity.kt` (both `MediaActivity` and `ProfileActivity` now migrated)
- Delete: all layout XMLs for profile screens

- [ ] **Step 1: Create `ProfileScreen.kt`** — same `TabRow` + `HorizontalPager` pattern as `MediaScreen`. Tabs: Info, About, Anime, Manga, Comments, History, Top Ten. BBCode content in `ProfileInfoScreen` / `ProfileAboutScreen` uses `AndroidView { BBCodeView }`.

- [ ] **Step 2: Update `ProfileActivity.kt`** — extend `BaseActivity`, `setContent { ProxerTheme { ProfileScreen(userId, username, onBack = { finish() }) } }`.

- [ ] **Step 3: Delete `ImageTabsActivity.kt`** — no longer has subclasses.

- [ ] **Step 4: Delete old files + build check + commit**

```bash
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate Profile screen to Compose"
```

---

### Task 13: Chat and Messenger screens

**Files (public chat):**
- Create: `src/main/kotlin/me/proxer/app/chat/pub/message/ChatScreen.kt`
- Create: `src/main/kotlin/me/proxer/app/chat/pub/room/ChatRoomScreen.kt`
- Create: `src/main/kotlin/me/proxer/app/chat/pub/room/info/ChatRoomInfoScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/chat/pub/message/ChatActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/chat/pub/room/info/ChatRoomInfoActivity.kt`
- Delete: Fragment + Adapter + Dialog + XML for each

**Files (private messenger):**
- Replace stub: `src/main/kotlin/me/proxer/app/chat/ChatContainerScreen.kt`
- Create: `src/main/kotlin/me/proxer/app/chat/prv/conference/ConferenceScreen.kt`
- Create: `src/main/kotlin/me/proxer/app/chat/prv/message/MessengerScreen.kt`
- Create: `src/main/kotlin/me/proxer/app/chat/prv/conference/info/ConferenceInfoScreen.kt`
- Create: `src/main/kotlin/me/proxer/app/chat/prv/create/CreateConferenceScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/PrvMessengerActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/conference/info/ConferenceInfoActivity.kt`
- Modify: `src/main/kotlin/me/proxer/app/chat/prv/create/CreateConferenceActivity.kt`
- Delete: all Fragment + Adapter + Dialog files in chat
- Delete: all chat layout and menu XMLs

**Key details:**
- Message list is reversed (newest at bottom): use `reverseLayout = true` on `LazyColumn`
- Long-press for CAB selection: use `combinedClickable` + `selectedIds: Set<Long>` state + `TopAppBar` swaps to contextual bar when `selectedIds.isNotEmpty()`
- `ChatReportDialog` / `MessengerReportDialog` → `AlertDialog` composable
- BBCode in chat messages uses `AndroidView { BBCodeView }`
- Input bar (chat send) maps to `OutlinedTextField` + send `IconButton`

- [ ] **Step 1: Create all screen composables** following the pattern: `LazyColumn(reverseLayout = true)` for message screens, paged list for room/conference lists.

- [ ] **Step 2: Update all Activity files** to extend `BaseActivity`, call `setContent { }`.

- [ ] **Step 3: Delete old files + build check + commit**

```bash
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate Chat and Messenger screens to Compose"
```

---

### Task 14: Forum / Topic screen

**Files:**
- Create: `src/main/kotlin/me/proxer/app/forum/TopicScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/forum/TopicActivity.kt` — extend `BaseActivity`
- Delete: `src/main/kotlin/me/proxer/app/forum/TopicFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/forum/PostAdapter.kt`
- Delete: `src/main/res/layout/item_post.xml`
- Delete: `src/main/res/menu/fragment_topic.xml`

- [ ] **Step 1: Create `TopicScreen.kt`** — paged list, each post shows `BBCodeView` via `AndroidView`. Share action in `TopAppBar`.

- [ ] **Step 2: Update `TopicActivity.kt`** — `BaseActivity`, `setContent { ProxerTheme { TopicScreen(id, title, onBack = { finish() }) } }`.

- [ ] **Step 3: Delete + build check + commit**

```bash
rm src/main/kotlin/me/proxer/app/forum/TopicFragment.kt
rm src/main/kotlin/me/proxer/app/forum/PostAdapter.kt
rm src/main/res/layout/item_post.xml
rm src/main/res/menu/fragment_topic.xml
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate Forum/Topic screen to Compose"
```

---

### Task 15: Industry and TranslatorGroup screens

**Files:**
- Create: `src/main/kotlin/me/proxer/app/info/industry/IndustryScreen.kt`
- Create: `src/main/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/info/industry/IndustryActivity.kt` — `BaseActivity`
- Modify: `src/main/kotlin/me/proxer/app/info/translatorgroup/TranslatorGroupActivity.kt` — `BaseActivity`
- Delete: `IndustryInfoFragment`, `IndustryProjectFragment`, `IndustryProjectAdapter`
- Delete: `TranslatorGroupInfoFragment`, `TranslatorGroupProjectFragment`, `TranslatorGroupProjectAdapter`
- Delete: `src/main/res/layout/fragment_industry.xml`, `fragment_translator_group.xml`, `item_project.xml`

- [ ] **Step 1: Create `IndustryScreen.kt`** — `TabRow` + `HorizontalPager` with Info tab and Projects tab (paged list). `IndustryInfoViewModel` + `IndustryProjectViewModel`.

- [ ] **Step 2: Create `TranslatorGroupScreen.kt`** — same structure.

- [ ] **Step 3: Update activities + delete + build check + commit**

```bash
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate Industry and TranslatorGroup screens to Compose"
```

---

### Task 16: Edit Comment screen

**Files:**
- Create: `src/main/kotlin/me/proxer/app/comment/EditCommentScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/comment/EditCommentActivity.kt` — already extends `BaseActivity`; add `setContent { }`
- Delete: `src/main/kotlin/me/proxer/app/comment/EditCommentFragment.kt`
- Delete: `src/main/res/layout/fragment_edit_comment.xml`
- Delete: `src/main/res/layout/fragment_edit_comment_preview.xml`
- Delete: `src/main/res/menu/fragment_edit_comment.xml`
- Delete: `src/main/res/menu/fragment_edit_comment_color.xml`
- Delete: `src/main/res/menu/fragment_edit_comment_size.xml`

**Key detail:** Toolbar has bold/italic/underline/size/color format buttons. In Compose, these become `IconButton` items in the `TopAppBar` actions. The text field is `OutlinedTextField`. Preview tab shows `AndroidView { BBCodeView }`.

- [ ] **Step 1: Create `EditCommentScreen.kt`**

```kotlin
package me.proxer.app.comment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.ui.view.bbcode.BBArgs
import me.proxer.app.ui.view.bbcode.BBCodeView
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCommentScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<EditCommentViewModel>()
    var text by remember { mutableStateOf("") }
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Comment") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { text += "[b][/b]" }) { Icon(Icons.Default.FormatBold, null) }
                    IconButton(onClick = { text += "[i][/i]" }) { Icon(Icons.Default.FormatItalic, null) }
                    // Add remaining format actions from menu/fragment_edit_comment.xml
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(selected = pagerState.currentPage == 0, onClick = { scope.launch { pagerState.scrollToPage(0) } }, text = { Text("Edit") })
                Tab(selected = pagerState.currentPage == 1, onClick = { scope.launch { pagerState.scrollToPage(1) } }, text = { Text("Preview") })
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                    1 -> AndroidView(
                        factory = { ctx -> BBCodeView(ctx) },
                        update = { view -> view.setCode(text, BBArgs()) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
```

Map remaining format actions and submit logic from `EditCommentFragment` and `EditCommentViewModel`.

- [ ] **Step 2: Update `EditCommentActivity.kt`**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    setContent {
        ProxerTheme {
            EditCommentScreen(onBack = { finish() })
        }
    }
}
```

- [ ] **Step 3: Delete + build check + commit**

```bash
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate EditComment screen to Compose"
```

---

### Task 17: Manga reader screen

**Files:**
- Create: `src/main/kotlin/me/proxer/app/manga/MangaScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/manga/MangaActivity.kt` — already extends `BaseActivity`; add `setContent { }`
- Delete: `src/main/kotlin/me/proxer/app/manga/MangaFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/manga/MangaAdapter.kt`
- Delete: `src/main/res/layout/fragment_manga.xml`
- Delete: `src/main/res/layout/item_manga_page.xml`
- Delete: `src/main/res/layout/item_manga_page_gif.xml`
- Keep: `AndroidPdfDecoder.kt`, `AndroidPdfRegionDecoder.kt`, `MangaLinearLayoutManger.kt`, `MangaPreloader.kt`, `MangaViewModel.kt`, `MangaChapterInfo.kt`, `MangaReaderOrientation.kt`

**Key:** `SubsamplingScaleImageView` has no Compose equivalent. Wrap in `AndroidView`. Each page is a `SubsamplingScaleImageView` instance inside a `LazyColumn` item.

- [ ] **Step 1: Create `MangaScreen.kt`**

```kotlin
package me.proxer.app.manga

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import me.proxer.app.ui.compose.ContentScreen
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<MangaViewModel>()
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
            )
        },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading == true,
            error = error,
            onRetry = { viewModel.load() },
            modifier = Modifier.padding(padding),
        ) {
            val pages = data ?: return@ContentScreen
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(pages) { page ->
                    AndroidView(
                        factory = { ctx ->
                            SubsamplingScaleImageView(ctx).apply {
                                // Configure as done in MangaAdapter.ViewHolder.bind()
                            }
                        },
                        update = { view ->
                            // Set image source from page — inspect MangaAdapter for exact call
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
```

Read `MangaAdapter` carefully for how it configures `SubsamplingScaleImageView` per page (image source, decoder, orientation). Replicate that configuration in the `AndroidView` factory/update lambdas.

- [ ] **Step 2: Update `MangaActivity.kt`**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    setContent {
        ProxerTheme {
            MangaScreen(onBack = { finish() })
        }
    }
}
```

Remove `setContentView`, Fragment transaction, `ViewPager`, menu inflation.

- [ ] **Step 3: Delete + build check + commit**

```bash
rm src/main/kotlin/me/proxer/app/manga/MangaFragment.kt
rm src/main/kotlin/me/proxer/app/manga/MangaAdapter.kt
rm src/main/res/layout/fragment_manga.xml
rm src/main/res/layout/item_manga_page.xml
rm src/main/res/layout/item_manga_page_gif.xml
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate Manga reader screen to Compose"
```

---

### Task 18: Stream player screen

**Files:**
- Create: `src/main/kotlin/me/proxer/app/anime/stream/StreamScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/anime/stream/StreamActivity.kt` — already extends `BaseActivity`; add `setContent { }`
- Keep: `StreamPlayerManager.kt`, `TouchablePlayerView.kt`, `CastOptionsProvider.kt`, `PreviewLoader.kt` — all untouched
- Delete: `src/main/res/layout/activity_stream.xml`
- Delete: `src/main/res/layout/layout_exoplayer_controls.xml`
- Delete: `src/main/res/layout/exo_simple_player_view.xml`
- Delete: `src/main/res/menu/activity_stream.xml`

**Key:** `TouchablePlayerView` is a custom `StyledPlayerView` subclass. Wrap it in `AndroidView`. `StreamPlayerManager` owns the `ExoPlayer` instance — pass it to `AndroidView.update`.

- [ ] **Step 1: Create `StreamScreen.kt`**

```kotlin
package me.proxer.app.anime.stream

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun StreamScreen(playerManager: StreamPlayerManager) {
    val context = LocalContext.current

    DisposableEffect(playerManager) {
        onDispose { /* playerManager lifecycle handled by Activity */ }
    }

    AndroidView(
        factory = { ctx ->
            TouchablePlayerView(ctx).also { view ->
                view.player = playerManager.player
                view.setControllerVisibilityListener(
                    androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
                        playerManager.onControllerVisibilityChanged(visibility)
                    }
                )
            }
        },
        update = { view ->
            view.player = playerManager.player
        },
        modifier = Modifier.fillMaxSize(),
    )
}
```

Adapt based on how `StreamActivity` currently configures `TouchablePlayerView` (read `StreamActivity.kt` fully).

- [ ] **Step 2: Update `StreamActivity.kt`** — remove `setContentView`. Add `setContent { ProxerTheme { StreamScreen(playerManager) } }`. Keep all `StreamPlayerManager` init, Cast setup, and lifecycle handling unchanged.

- [ ] **Step 3: Delete + build check + commit**

```bash
rm src/main/res/layout/activity_stream.xml
rm src/main/res/layout/layout_exoplayer_controls.xml
rm src/main/res/layout/exo_simple_player_view.xml
rm src/main/res/menu/activity_stream.xml
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate Stream player screen to Compose"
```

---

### Task 19: ImageDetail and WebView screens

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/ui/ImageDetailActivity.kt` — already extends `BaseActivity`
- Modify: `src/main/kotlin/me/proxer/app/ui/WebViewActivity.kt` — extends `AppCompatActivity`; change to `BaseActivity`
- Delete: `src/main/res/layout/activity_image_detail.xml`
- Delete: `src/main/res/layout/layout_image.xml`
- Delete: `src/main/res/layout/activity_web_view.xml`

**Key:** `ImageDetailActivity` uses `SubsamplingScaleImageView` for full-screen photo viewing — `AndroidView` wrapper. `WebViewActivity` wraps `ProxerWebView` (custom `WebView` subclass) — `AndroidView` wrapper.

- [ ] **Step 1: Replace `ImageDetailActivity` body with Compose**

```kotlin
setContent {
    ProxerTheme {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            AndroidView(
                factory = { ctx ->
                    SubsamplingScaleImageView(ctx).also { view ->
                        view.setImage(ImageSource.uri(imageUri))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Close button, share button
        }
    }
}
```

- [ ] **Step 2: Replace `WebViewActivity` body with Compose**

```kotlin
setContent {
    ProxerTheme {
        AndroidView(
            factory = { ctx -> ProxerWebView(ctx).also { it.loadUrl(url) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
```

- [ ] **Step 3: Delete XMLs + build check + commit**

```bash
rm src/main/res/layout/activity_image_detail.xml
rm src/main/res/layout/layout_image.xml
rm src/main/res/layout/activity_web_view.xml
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate ImageDetail and WebView screens to Compose"
```

---

### Task 20: AnimeActivity and remaining DrawerActivity subclasses

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/anime/AnimeActivity.kt` — change to `BaseActivity`, `setContent { }`
- Create: `src/main/kotlin/me/proxer/app/anime/AnimeScreen.kt`
- Modify: `src/main/kotlin/me/proxer/app/settings/ProxerLibsActivity.kt` — change to `BaseActivity`
- Delete: `src/main/kotlin/me/proxer/app/anime/AnimeFragment.kt`
- Delete: `src/main/kotlin/me/proxer/app/anime/AnimeAdapter.kt`
- Delete: `src/main/res/layout/fragment_anime.xml`
- Delete: `src/main/res/menu/activity_share.xml` (if only used by AnimeActivity — check first)

Also handle crash activity:
- Modify: `src/main/release/kotlin/me/proxer/app/ui/crash/CrashActivity.kt` — `setContent { }` with simple error UI composable

- [ ] **Step 1: Create `AnimeScreen.kt`** — read `AnimeFragment` + `AnimeAdapter` for exact layout (episode select, language chips, stream list). `AnimeActivity` exposes `id`, `episode`, `language`, `name` properties — pass these to the composable.

- [ ] **Step 2: Update all remaining `DrawerActivity` subclasses** to extend `BaseActivity`. There are no more `DrawerActivity` subclasses after this task.

- [ ] **Step 3: Build check + commit**

```bash
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate AnimeActivity and remaining screens to Compose"
```

---

### Task 21: Auth dialogs, Login/Logout Compose dialogs

**Files:**
- Modify: `src/main/kotlin/me/proxer/app/auth/LoginDialog.kt` — replace `DialogFragment` with `@Composable` `AlertDialog`
- Modify: `src/main/kotlin/me/proxer/app/auth/LogoutDialog.kt` — same
- Delete: `src/main/res/layout/dialog_login.xml`
- Delete: `src/main/res/layout/dialog_logout.xml`
- Delete: `src/main/res/layout/dialog_no_wifi.xml`
- Delete: `src/main/res/layout/dialog_link_check.xml`
- Delete: `src/main/res/layout/dialog_theme.xml`

**Key:** `LoginDialog` currently extends `DialogFragment`. Change to a top-level `@Composable` function that takes `onDismiss: () -> Unit` and shows `AlertDialog`. Call sites (currently `show(fragmentManager, tag)`) must be changed to show the dialog via a `var showLogin by remember { mutableStateOf(false) }` state flag in the parent composable.

- [ ] **Step 1: Rewrite `LoginDialog.kt`**

```kotlin
package me.proxer.app.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import me.proxer.app.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginDialog(onDismiss: () -> Unit) {
    val viewModel = koinViewModel<LoginViewModel>()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val data by viewModel.data.observeAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    if (data != null) {
        onDismiss()
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_login)) },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.dialog_login_username)) },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.dialog_login_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.login(username, password) },
                enabled = isLoading != true,
            ) { Text(stringResource(R.string.action_login)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
```

Update all call sites from `LoginDialog().show(supportFragmentManager, "login")` to exposing a `showLoginDialog` state flag in the parent composable and conditionally rendering `LoginDialog { showLoginDialog = false }`.

- [ ] **Step 2: Rewrite `LogoutDialog.kt`** — same pattern with `LogoutViewModel`.

- [ ] **Step 3: Delete dialog XMLs + build check + commit**

```bash
./gradlew compileDebugKotlin --no-daemon
git add -A
git commit -m "feat: migrate auth dialogs to Compose"
```

---

### Task 22: Final cleanup

**Files:**
- Modify: `gradle/dependencies.gradle` — remove `kotterknife`, `rxbinding*`, `materialdrawer`, `crossfader`, `materialdrawer-iconics`
- Delete: `src/main/kotlin/me/proxer/app/util/wrapper/MaterialDrawerWrapper.kt`
- Delete: `src/main/kotlin/me/proxer/app/base/DrawerActivity.kt`
- Delete: `src/main/kotlin/me/proxer/app/ui/view/CrossfadingDrawerLayout.kt`
- Delete: `src/main/res/layout/activity_default.xml`
- Delete: `src/main/res/layout/activity_no_drawer.xml`
- Delete: `src/main/res/layout/activity_main.xml`
- Delete: `src/main/res/layout/activity_image_tabs.xml` (if it exists)
- Delete: `src/main/res/layout/fragment_paged.xml`
- Delete: `src/main/res/layout/layout_error.xml`
- Delete: `src/main/res/layout/layout_ad_alert.xml`
- Delete: `src/main/res/layout/layout_upload_info.xml`
- Delete: `src/main/res/layout/layout_media_control.xml`
- Delete: `src/main/res/layout/view_bb_spoiler.xml`
- Delete: `src/main/res/layout/view_expandable_multi_selection.xml`
- Delete: `src/main/res/layout/view_media_control.xml`
- Delete: `src/main/res/layout/layout_media_info_row.xml`
- Delete: `src/main/res/layout/layout_media_info_seasons_row.xml`
- Delete: `src/main/res/layout/layout_chat_bar.xml`
- Delete: any remaining `src/main/res/layout/*.xml` not belonging to widgets (verify with `find src/main/res/layout -name "*.xml" | grep -v widget`)
- Delete: `src/main/res/menu/*.xml` — all remaining non-widget menus
- Keep: `src/main/res/layout/layout_widget_*.xml` — widget layouts, out of scope

- [ ] **Step 1: Verify no remaining references to deleted libraries**

```bash
grep -r "kotterknife\|bindView\|MaterialDrawerWrapper\|RxBinding\|rxbinding\|crossfader\|materialdrawer" \
  src/main/kotlin/me/proxer/app --include="*.kt" | grep -v "tv/"
```

Expected: no output. Fix any remaining usages before proceeding.

- [ ] **Step 2: Remove dependencies from `gradle/dependencies.gradle`**

Remove these lines:
```
implementation "com.jakewharton.rxbinding3:rxbinding-core:$rxBindingVersion"
implementation "com.jakewharton.rxbinding3:rxbinding-appcompat:$rxBindingVersion"
implementation "com.jakewharton.rxbinding3:rxbinding-recyclerview:$rxBindingVersion"
implementation "com.jakewharton.rxbinding3:rxbinding-swiperefreshlayout:$rxBindingVersion"
implementation "com.jakewharton.rxbinding3:rxbinding-material:$rxBindingVersion"
implementation "com.github.rubengees:kotterknife:$kotterknifeVersion"
implementation "com.mikepenz:materialdrawer:$materialDrawerVersion"
implementation "com.mikepenz:materialdrawer-iconics:$materialDrawerVersion"
implementation "com.mikepenz:crossfader:$crossfaderVersion@aar"
```

Also check `gradle/versions.gradle` and remove associated version variables if they're now unused.

- [ ] **Step 3: Delete all listed files**

```bash
rm src/main/kotlin/me/proxer/app/util/wrapper/MaterialDrawerWrapper.kt
rm src/main/kotlin/me/proxer/app/base/DrawerActivity.kt
rm src/main/kotlin/me/proxer/app/ui/view/CrossfadingDrawerLayout.kt
# Delete remaining XML files
find src/main/res/layout -name "*.xml" | grep -v widget | xargs rm
find src/main/res/menu -name "*.xml" | xargs rm
```

- [ ] **Step 4: Full build**

```
./gradlew assembleDebug --no-daemon --max-workers 2
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: remove XML layouts, View-binding libs, and MaterialDrawer after Compose migration"
```

---

## Self-Review Notes

**Spec coverage:**
- ✅ Big-bang strategy with per-screen commits (Tasks 3–21)
- ✅ BBCode wrapped in `AndroidView` (Tasks 11, 12, 13, 14, 16)
- ✅ RxJava kept, bridged via `observeAsState` (all screen tasks)
- ✅ Multi-Activity kept, Fragments deleted (all screen tasks)
- ✅ App widgets untouched
- ✅ `ProxerTheme` + `ContentScreen` infrastructure (Task 1)
- ✅ `DrawerActivity` → `ModalNavigationDrawer` in `MainActivity` (Task 2)
- ✅ `SubsamplingScaleImageView` wrapped in `AndroidView` (Task 17)
- ✅ Media3 `StyledPlayerView` wrapped in `AndroidView` (Task 18)
- ✅ Paged list pattern documented (Tasks 7–10)
- ✅ Tab screens use `TabRow` + `HorizontalPager` (Tasks 11, 12, 15, 16)
- ✅ Dialogs migrated to `AlertDialog` composables (Tasks 9, 13, 21)
- ✅ Cleanup task removes all legacy deps (Task 22)

**Type consistency check:** All composable signatures referenced in `MainScreen.kt` (Task 2) match the stub signatures created in Task 2's Step 2 and later replaced in Tasks 3–20. `ContentScreen` parameters used identically across all tasks.

**Scope check:** 22 tasks is large but each is independently testable via `compileDebugKotlin`. Tasks 11–13 are the largest (multi-tab screens); they can be split into sub-commits (one per tab screen) if needed.
