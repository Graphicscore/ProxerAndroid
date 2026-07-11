package me.proxer.app.ui.compose

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.anime.schedule.ScheduleScreen
import me.proxer.app.auth.LocalUser
import me.proxer.app.auth.LoginDialog
import me.proxer.app.auth.LogoutDialog
import me.proxer.app.bookmark.BookmarkScreen
import me.proxer.app.chat.ChatContainerScreen
import me.proxer.app.media.list.MediaListScreen
import me.proxer.app.news.NewsScreen
import me.proxer.app.notification.NotificationActivity
import me.proxer.app.profile.ProfileActivity
import me.proxer.app.profile.settings.ProfileSettingsActivity
import me.proxer.app.settings.AboutScreen
import me.proxer.app.settings.SettingsScreen
import me.proxer.app.util.InAppUpdateFlow
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.wrapper.DrawerItem
import me.proxer.library.enums.Category
import me.proxer.library.util.ProxerUrls
import org.koin.compose.koinInject

private data class DrawerEntry(
    val item: DrawerItem,
    val labelRes: Int,
    val icon: ImageVector,
)

private val drawerEntries = listOf(
    DrawerEntry(DrawerItem.NEWS, R.string.section_news, Icons.Default.Notifications),
    DrawerEntry(DrawerItem.CHAT, R.string.section_chat, Icons.AutoMirrored.Filled.Chat),
    DrawerEntry(DrawerItem.BOOKMARKS, R.string.section_bookmarks, Icons.Default.Bookmarks),
    DrawerEntry(DrawerItem.ANIME, R.string.section_anime, Icons.Default.OndemandVideo),
    DrawerEntry(DrawerItem.SCHEDULE, R.string.section_schedule, Icons.Default.CalendarMonth),
    DrawerEntry(DrawerItem.MANGA, R.string.section_manga, Icons.AutoMirrored.Filled.MenuBook),
    DrawerEntry(DrawerItem.INFO, R.string.section_info, Icons.Default.Info),
    DrawerEntry(DrawerItem.SETTINGS, R.string.section_settings, Icons.Default.Settings),
)

@Composable
private fun DrawerHeader(
    user: LocalUser?,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (user != null) {
                AsyncImage(
                    model = ProxerUrls.userImage(user.image).toString(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable { onProfileClick() },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.clickable { onProfileClick() },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = stringResource(R.string.section_notifications),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onNotificationsClick() },
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.section_profile_settings),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onProfileSettingsClick() },
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(R.string.section_logout),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onLogoutClick() },
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onLoginClick() }
                        .padding(vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.section_login),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreenDrawerSheet(
    user: LocalUser?,
    selectedItem: DrawerItem,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSelect: (DrawerItem) -> Unit,
) {
    ModalDrawerSheet {
        DrawerHeader(
            user = user,
            onLoginClick = onLoginClick,
            onProfileClick = onProfileClick,
            onNotificationsClick = onNotificationsClick,
            onProfileSettingsClick = onProfileSettingsClick,
            onLogoutClick = onLogoutClick,
        )
        HorizontalDivider()
        drawerEntries.forEach { entry ->
            NavigationDrawerItem(
                icon = { Icon(entry.icon, contentDescription = null) },
                label = { Text(stringResource(entry.labelRes)) },
                selected = selectedItem == entry.item ||
                    (entry.item == DrawerItem.CHAT && selectedItem == DrawerItem.MESSENGER),
                onClick = { onSelect(entry.item) },
            )
        }
    }
}

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
                MainScreenDrawerSheet(
                    user = user,
                    selectedItem = selectedItem,
                    onLoginClick = {
                        scope.launch { drawerState.close() }
                        showLoginDialog = true
                    },
                    onProfileClick = {
                        scope.launch { drawerState.close() }
                        (context as? Activity)?.let { activity ->
                            user?.let { u ->
                                ProfileActivity.navigateTo(activity, userId = u.id, username = u.name)
                            }
                        }
                    },
                    onNotificationsClick = {
                        scope.launch { drawerState.close() }
                        (context as? Activity)?.let { NotificationActivity.navigateTo(it) }
                    },
                    onProfileSettingsClick = {
                        scope.launch { drawerState.close() }
                        (context as? Activity)?.let { ProfileSettingsActivity.navigateTo(it) }
                    },
                    onLogoutClick = {
                        scope.launch { drawerState.close() }
                        showLogoutDialog = true
                    },
                    onSelect = { item ->
                        selectedItem = item
                        scope.launch { drawerState.close() }
                    },
                )
            },
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

@Preview(showBackground = true)
@Composable
private fun MainScreenLoggedOutPreview() {
    var selectedItem by rememberSaveable { mutableStateOf(DrawerItem.NEWS) }
    ProxerTheme {
        ModalNavigationDrawer(
            drawerState = rememberDrawerState(DrawerValue.Open),
            drawerContent = {
                MainScreenDrawerSheet(
                    user = null,
                    selectedItem = selectedItem,
                    onLoginClick = {},
                    onProfileClick = {},
                    onNotificationsClick = {},
                    onProfileSettingsClick = {},
                    onLogoutClick = {},
                    onSelect = { selectedItem = it },
                )
            },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Section: ${selectedItem.name}")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenLoggedInPreview() {
    var selectedItem by rememberSaveable { mutableStateOf(DrawerItem.NEWS) }
    val fakeUser = LocalUser(token = "", id = "1", name = "Nutzer", image = "")
    ProxerTheme {
        ModalNavigationDrawer(
            drawerState = rememberDrawerState(DrawerValue.Open),
            drawerContent = {
                MainScreenDrawerSheet(
                    user = fakeUser,
                    selectedItem = selectedItem,
                    onLoginClick = {},
                    onProfileClick = {},
                    onNotificationsClick = {},
                    onProfileSettingsClick = {},
                    onLogoutClick = {},
                    onSelect = { selectedItem = it },
                )
            },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Section: ${selectedItem.name}")
            }
        }
    }
}
