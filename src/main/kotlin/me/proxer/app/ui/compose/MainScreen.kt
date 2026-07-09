package me.proxer.app.ui.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import me.proxer.app.util.wrapper.DrawerItem
import me.proxer.library.enums.Category

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
                        selected = selectedItem == entry.item ||
                            (entry.item == DrawerItem.CHAT && selectedItem == DrawerItem.MESSENGER),
                        onClick = {
                            selectedItem = entry.item
                            scope.launch { drawerState.close() }
                        },
                    )
                }
            }
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
}
