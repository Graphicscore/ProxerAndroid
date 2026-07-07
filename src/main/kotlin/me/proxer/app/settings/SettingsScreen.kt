package me.proxer.app.settings

import android.content.Context
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.proxer.app.BuildConfig
import me.proxer.app.R
import me.proxer.app.chat.prv.sync.MessengerWorker
import me.proxer.app.notification.NotificationWorker
import me.proxer.app.profile.settings.ProfileSettingsActivity
import me.proxer.app.settings.theme.ThemeDialog
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.wrapper.MaterialDrawerWrapper.DrawerItem
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenDrawer: () -> Unit = {}) {
    val context = LocalContext.current
    val preferenceHelper = koinInject<PreferenceHelper>()
    val storageHelper = koinInject<StorageHelper>()

    // --- mutable UI state ---
    var isLoggedIn by remember { mutableStateOf(storageHelper.isLoggedIn) }
    var ageRestricted by remember { mutableStateOf(preferenceHelper.isAgeRestrictedMediaAllowed) }
    var autoBookmark by remember { mutableStateOf(preferenceHelper.areBookmarksAutomatic) }
    var checkCellular by remember { mutableStateOf(preferenceHelper.shouldCheckCellular) }
    var checkLinks by remember { mutableStateOf(preferenceHelper.shouldCheckLinks) }
    var externalCache by remember { mutableStateOf(preferenceHelper.shouldCacheExternally) }
    var startPage by remember { mutableStateOf(preferenceHelper.startPage) }
    var themeLabel by remember { mutableStateOf(buildThemeLabel(context, preferenceHelper)) }
    var newsNotifications by remember { mutableStateOf(preferenceHelper.areNewsNotificationsEnabled) }
    var accountNotifications by remember { mutableStateOf(preferenceHelper.areAccountNotificationsEnabled) }
    var chatNotifications by remember { mutableStateOf(preferenceHelper.areChatNotificationsEnabled) }
    var notificationsInterval by remember { mutableStateOf(preferenceHelper.notificationsInterval) }
    var httpLogLevelIdx by remember { mutableIntStateOf(preferenceHelper.httpLogLevelIndex) }
    var httpVerbose by remember { mutableStateOf(preferenceHelper.shouldLogHttpVerbose) }
    var httpRedactToken by remember { mutableStateOf(preferenceHelper.shouldRedactToken) }

    // --- dialog state ---
    var showAgeConfirmationDialog by remember { mutableStateOf(false) }
    var showStartPageDialog by remember { mutableStateOf(false) }
    var showNotificationsIntervalDialog by remember { mutableStateOf(false) }
    var showHttpLogLevelDialog by remember { mutableStateOf(false) }

    // Restart-needed snackbar trigger (incremented to fire the LaunchedEffect)
    var restartTrigger by remember { mutableIntStateOf(0) }

    val showExternalCache = remember {
        !Environment.isExternalStorageEmulated() &&
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val restartMessage = stringResource(R.string.fragment_settings_restart_message)

    // Show restart snackbar when restartTrigger changes
    LaunchedEffect(restartTrigger) {
        if (restartTrigger > 0) snackbarHostState.showSnackbar(restartMessage)
    }

    // Observe login state (isLoggedInObservable skips first value, so seed from isLoggedIn)
    DisposableEffect(storageHelper) {
        val disposable = storageHelper.isLoggedInObservable.subscribe { isLoggedIn = it }
        onDispose { disposable.dispose() }
    }

    // Refresh theme label on theme change (activity recreates anyway, but update label if dialog is cancelled)
    DisposableEffect(preferenceHelper) {
        val disposable = preferenceHelper.themeObservable
            .subscribe { themeLabel = buildThemeLabel(context, preferenceHelper) }
        onDispose { disposable.dispose() }
    }

    // --- string arrays for dialogs ---
    val startPageTitles = stringArrayResource(R.array.start_page_titles)
    val notificationIntervalTitles = stringArrayResource(R.array.notifications_interval_titles)
    val httpLogLevelTitles = stringArrayResource(R.array.http_log_level_titles)
    val notificationIntervalValues = longArrayOf(30L, 120L, 480L)

    // Ordered list matching start_page_values array [0,1,2,3,4,5]
    val startPageItems = remember {
        listOf(
            DrawerItem.NEWS,
            DrawerItem.CHAT,
            DrawerItem.BOOKMARKS,
            DrawerItem.ANIME,
            DrawerItem.SCHEDULE,
            DrawerItem.MANGA,
        )
    }

    // ---- Dialogs ----

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

    if (showStartPageDialog) {
        var selected by remember { mutableStateOf(startPage) }
        AlertDialog(
            onDismissRequest = { showStartPageDialog = false },
            title = { Text(stringResource(R.string.preference_start_page_title)) },
            text = {
                androidx.compose.foundation.layout.Column(
                    Modifier.verticalScroll(rememberScrollState()),
                ) {
                    startPageItems.forEachIndexed { i, item ->
                        ListItem(
                            headlineContent = { Text(startPageTitles.getOrElse(i) { "" }) },
                            leadingContent = {
                                RadioButton(selected = selected == item, onClick = null)
                            },
                            modifier = Modifier.clickable { selected = item },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    startPage = selected
                    preferenceHelper.startPage = selected
                    showStartPageDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartPageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showNotificationsIntervalDialog) {
        val currentIdx = notificationIntervalValues.indexOfFirst { it == notificationsInterval }
            .takeIf { it >= 0 } ?: 1
        var selected by remember { mutableIntStateOf(currentIdx) }
        AlertDialog(
            onDismissRequest = { showNotificationsIntervalDialog = false },
            title = { Text(stringResource(R.string.preference_notifications_interval_title)) },
            text = {
                androidx.compose.foundation.layout.Column(
                    Modifier.verticalScroll(rememberScrollState()),
                ) {
                    notificationIntervalTitles.forEachIndexed { i, title ->
                        ListItem(
                            headlineContent = { Text(title) },
                            leadingContent = {
                                RadioButton(selected = selected == i, onClick = null)
                            },
                            modifier = Modifier.clickable { selected = i },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    notificationsInterval = notificationIntervalValues[selected]
                    preferenceHelper.notificationsInterval = notificationIntervalValues[selected]
                    NotificationWorker.enqueueIfPossible()
                    MessengerWorker.enqueueSynchronizationIfPossible()
                    showNotificationsIntervalDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationsIntervalDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showHttpLogLevelDialog) {
        var selected by remember { mutableIntStateOf(httpLogLevelIdx) }
        AlertDialog(
            onDismissRequest = { showHttpLogLevelDialog = false },
            title = { Text(stringResource(R.string.preference_developer_options_http_log_level_title)) },
            text = {
                androidx.compose.foundation.layout.Column(
                    Modifier.verticalScroll(rememberScrollState()),
                ) {
                    httpLogLevelTitles.forEachIndexed { i, title ->
                        ListItem(
                            headlineContent = { Text(title) },
                            leadingContent = {
                                RadioButton(selected = selected == i, onClick = null)
                            },
                            modifier = Modifier.clickable { selected = i },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    httpLogLevelIdx = selected
                    preferenceHelper.httpLogLevelIndex = selected
                    restartTrigger++
                    showHttpLogLevelDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showHttpLogLevelDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Profile link
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.section_profile_settings)) },
                    supportingContent = { Text(stringResource(R.string.preference_profile_summary)) },
                    modifier = Modifier.clickable(enabled = isLoggedIn) {
                        ProfileSettingsActivity.navigateTo(context as AppCompatActivity)
                    },
                )
            }

            // General category
            item { CategoryHeader(stringResource(R.string.preference_category_general_title)) }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.preference_age_confirmation_title)) },
                    supportingContent = {
                        Text(
                            if (ageRestricted) stringResource(R.string.preference_age_confirmation_summary_on)
                            else stringResource(R.string.preference_age_confirmation_summary_off),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = ageRestricted,
                            onCheckedChange = { newValue ->
                                if (newValue) {
                                    showAgeConfirmationDialog = true
                                } else {
                                    ageRestricted = false
                                    preferenceHelper.isAgeRestrictedMediaAllowed = false
                                }
                            },
                        )
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.preference_auto_bookmark_title)) },
                    supportingContent = {
                        Text(
                            if (autoBookmark) stringResource(R.string.preference_auto_bookmark_summary_on)
                            else stringResource(R.string.preference_auto_bookmark_summary_off),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = autoBookmark,
                            onCheckedChange = {
                                autoBookmark = it
                                preferenceHelper.areBookmarksAutomatic = it
                            },
                        )
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.preference_check_wifi_title)) },
                    supportingContent = {
                        Text(
                            if (checkCellular) stringResource(R.string.preference_check_wifi_summary_on)
                            else stringResource(R.string.preference_check_wifi_summary_off),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = checkCellular,
                            onCheckedChange = {
                                checkCellular = it
                                preferenceHelper.shouldCheckCellular = it
                            },
                        )
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.preference_check_links_title)) },
                    supportingContent = {
                        Text(
                            if (checkLinks) stringResource(R.string.preference_check_links_summary_on)
                            else stringResource(R.string.preference_check_links_summary_off),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = checkLinks,
                            onCheckedChange = {
                                checkLinks = it
                                preferenceHelper.shouldCheckLinks = it
                            },
                        )
                    },
                )
            }

            if (showExternalCache) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.preference_external_cache_title)) },
                        supportingContent = {
                            Text(
                                if (externalCache) stringResource(R.string.preference_external_cache_summary_on)
                                else stringResource(R.string.preference_external_cache_summary_off),
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = externalCache,
                                onCheckedChange = {
                                    externalCache = it
                                    preferenceHelper.shouldCacheExternally = it
                                    restartTrigger++
                                },
                            )
                        },
                    )
                }
            }

            item {
                val startPageTitle = startPageItems.indexOf(startPage)
                    .takeIf { it >= 0 }
                    ?.let { startPageTitles.getOrElse(it) { "" } }
                    ?: startPageTitles.firstOrNull() ?: ""
                ListItem(
                    headlineContent = { Text(stringResource(R.string.preference_start_page_title)) },
                    supportingContent = { Text(startPageTitle) },
                    modifier = Modifier.clickable { showStartPageDialog = true },
                )
            }

            // Design category
            item { CategoryHeader(stringResource(R.string.preference_category_design_title)) }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.dialog_theme_title)) },
                    supportingContent = { Text(themeLabel) },
                    modifier = Modifier.clickable {
                        ThemeDialog.show(context as AppCompatActivity)
                    },
                )
            }

            // Notifications category
            item { CategoryHeader(stringResource(R.string.preference_category_notifications_title)) }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.preference_notifications_news_title)) },
                    supportingContent = {
                        Text(
                            if (newsNotifications) stringResource(R.string.preference_notifications_summary_on)
                            else stringResource(R.string.preference_notifications_summary_off),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = newsNotifications,
                            onCheckedChange = {
                                newsNotifications = it
                                preferenceHelper.areNewsNotificationsEnabled = it
                                NotificationWorker.enqueueIfPossible()
                            },
                        )
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.preference_notifications_account_title)) },
                    supportingContent = {
                        Text(
                            if (accountNotifications) stringResource(R.string.preference_notifications_summary_on)
                            else stringResource(R.string.preference_notifications_summary_off),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = accountNotifications,
                            onCheckedChange = {
                                accountNotifications = it
                                preferenceHelper.areAccountNotificationsEnabled = it
                                NotificationWorker.enqueueIfPossible()
                            },
                        )
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.preference_notifications_chat_title)) },
                    supportingContent = {
                        Text(
                            if (chatNotifications) stringResource(R.string.preference_notifications_summary_on)
                            else stringResource(R.string.preference_notifications_summary_off),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = chatNotifications,
                            onCheckedChange = {
                                chatNotifications = it
                                preferenceHelper.areChatNotificationsEnabled = it
                                MessengerWorker.enqueueSynchronizationIfPossible()
                            },
                        )
                    },
                )
            }

            item {
                val intervalEnabled = newsNotifications || accountNotifications
                val intervalTitle = notificationIntervalValues
                    .indexOfFirst { it == notificationsInterval }
                    .takeIf { it >= 0 }
                    ?.let { notificationIntervalTitles.getOrElse(it) { "" } }
                    ?: notificationIntervalTitles.getOrElse(1) { "" }
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.preference_notifications_interval_title),
                            color = if (!intervalEnabled) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    supportingContent = { Text(intervalTitle) },
                    modifier = Modifier.clickable(enabled = intervalEnabled) {
                        showNotificationsIntervalDialog = true
                    },
                )
            }

            // Developer options (visible in debug or LOG builds)
            if (BuildConfig.DEBUG || BuildConfig.LOG) {
                item { CategoryHeader(stringResource(R.string.preference_category_developer_option)) }

                item {
                    val logLevelTitle = httpLogLevelTitles.getOrElse(httpLogLevelIdx) { "" }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.preference_developer_options_http_log_level_title)) },
                        supportingContent = { Text(logLevelTitle) },
                        modifier = Modifier.clickable { showHttpLogLevelDialog = true },
                    )
                }

                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.preference_developer_options_http_log_verbose_title)) },
                        supportingContent = {
                            Text(
                                if (httpVerbose) {
                                    stringResource(R.string.preference_developer_options_http_log_verbose_summary_on)
                                } else {
                                    stringResource(R.string.preference_developer_options_http_log_verbose_summary_off)
                                },
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = httpVerbose,
                                onCheckedChange = {
                                    httpVerbose = it
                                    preferenceHelper.shouldLogHttpVerbose = it
                                    restartTrigger++
                                },
                            )
                        },
                    )
                }

                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.preference_developer_options_http_redact_token_title)) },
                        supportingContent = {
                            Text(
                                if (httpRedactToken) {
                                    stringResource(R.string.preference_developer_options_http_redact_token_summary_on)
                                } else {
                                    stringResource(R.string.preference_developer_options_http_redact_token_summary_off)
                                },
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = httpRedactToken,
                                onCheckedChange = {
                                    httpRedactToken = it
                                    preferenceHelper.shouldRedactToken = it
                                    restartTrigger++
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

private fun buildThemeLabel(context: Context, preferenceHelper: PreferenceHelper): String {
    val (theme, variant) = preferenceHelper.themeContainer
    return buildString {
        append(context.getString(theme.themeName))
        variant.variantName?.let {
            append(" ")
            append(context.getString(it))
        }
    }
}
