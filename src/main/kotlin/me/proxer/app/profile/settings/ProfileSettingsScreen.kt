package me.proxer.app.profile.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.ui.compose.ObserveLiveDataEvent
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.library.enums.UcpSettingConstraint
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun ProfileSettingsScreen(onBack: () -> Unit = {}) {
    val viewModel = koinViewModel<ProfileSettingsViewModel>()

    val settings by viewModel.data.observeAsState()

    ProfileSettingsContent(
        settings = settings,
        error = viewModel.error,
        updateError = viewModel.updateError,
        onBack = onBack,
        onUpdate = { viewModel.update(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSettingsContent(
    settings: LocalProfileSettings?,
    error: LiveData<ErrorAction>,
    updateError: LiveData<ErrorAction>,
    onBack: () -> Unit,
    onUpdate: (LocalProfileSettings) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
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

    // Constraint arrays
    val constraintTitles = stringArrayResource(R.array.profile_settings_constraint_titles)
    val friendRequestTitles = stringArrayResource(R.array.profile_settings_friend_request_constraint_titles)
    // friend_request constraint values are [0, 3, 4] → DEFAULT, EVERYONE, PRIVATE
    val friendRequestConstraintValues = intArrayOf(0, 3, 4)

    val videoAdsIntervalTitles = stringArrayResource(R.array.profile_settings_video_ads_interval_titles)
    val videoAdsIntervalValues = intArrayOf(0, 1, 3, 5, 10)

    // Active constraint dialog state
    var activeConstraintDialog by remember { mutableStateOf<ConstraintDialogConfig?>(null) }

    // Video ads interval dialog state
    var showVideoAdsDialog by remember { mutableStateOf(false) }

    // ---- Constraint dialog ----
    activeConstraintDialog?.let { config ->
        var selected by remember { mutableIntStateOf(config.currentIndex) }
        AlertDialog(
            onDismissRequest = { activeConstraintDialog = null },
            title = { Text(config.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    config.titles.forEachIndexed { i, title ->
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
                    config.onSelect(selected)
                    activeConstraintDialog = null
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { activeConstraintDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // ---- Video ads interval dialog ----
    if (showVideoAdsDialog) {
        val currentInterval = settings?.adInterval ?: 0
        val normalizedInterval = videoAdsIntervalValues.sortedDescending().find { currentInterval >= it } ?: 0
        val currentIdx = videoAdsIntervalValues.indexOf(normalizedInterval).takeIf { it >= 0 } ?: 0
        var selected by remember { mutableIntStateOf(currentIdx) }
        AlertDialog(
            onDismissRequest = { showVideoAdsDialog = false },
            title = { Text(stringResource(R.string.profile_preference_video_ads_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    videoAdsIntervalTitles.forEachIndexed { i, title ->
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
                    val newInterval = videoAdsIntervalValues[selected]
                    settings?.let { onUpdate(it.copy(adInterval = newInterval)) }
                    showVideoAdsDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showVideoAdsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_profile_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val currentSettings = settings ?: return@Scaffold

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ---- Ads category (bannerAdsEnabled hidden per original) ----
            item {
                ProfileCategoryHeader(stringResource(R.string.profile_preference_ads_category_title))
            }

            item {
                val currentInterval = currentSettings.adInterval
                val normalizedInterval = videoAdsIntervalValues.sortedDescending()
                    .find { currentInterval >= it } ?: 0
                val titleIdx = videoAdsIntervalValues.indexOf(normalizedInterval).takeIf { it >= 0 } ?: 0
                val keyword = videoAdsIntervalTitles.getOrElse(titleIdx) { "" }
                    .lowercase(Locale.GERMANY)
                val summary = stringResource(R.string.profile_preference_video_ads_summary, keyword)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.profile_preference_video_ads_title)) },
                    supportingContent = { Text(summary) },
                    modifier = Modifier.clickable { showVideoAdsDialog = true },
                )
            }

            // ---- Privacy category ----
            item {
                ProfileCategoryHeader(stringResource(R.string.profile_preference_privacy_category_title))
            }

            item {
                ConstraintListItem(
                    title = stringResource(R.string.profile_preference_profile),
                    constraint = currentSettings.profileVisibility,
                    titles = constraintTitles,
                    onClick = {
                        activeConstraintDialog = ConstraintDialogConfig(
                            title = context.getString(R.string.profile_preference_profile),
                            titles = constraintTitles,
                            currentIndex = currentSettings.profileVisibility.ordinal,
                        ) { idx ->
                            onUpdate(
                                currentSettings.copy(profileVisibility = UcpSettingConstraint.values()[idx]),
                            )
                        }
                    },
                )
            }

            item {
                ConstraintListItem(
                    title = stringResource(R.string.profile_preference_topten),
                    constraint = currentSettings.topTenVisibility,
                    titles = constraintTitles,
                    onClick = {
                        activeConstraintDialog = ConstraintDialogConfig(
                            title = context.getString(R.string.profile_preference_topten),
                            titles = constraintTitles,
                            currentIndex = currentSettings.topTenVisibility.ordinal,
                        ) { idx ->
                            onUpdate(
                                currentSettings.copy(topTenVisibility = UcpSettingConstraint.values()[idx]),
                            )
                        }
                    },
                )
            }

            item {
                ConstraintListItem(
                    title = stringResource(R.string.profile_preference_anime),
                    constraint = currentSettings.animeVisibility,
                    titles = constraintTitles,
                    onClick = {
                        activeConstraintDialog = ConstraintDialogConfig(
                            title = context.getString(R.string.profile_preference_anime),
                            titles = constraintTitles,
                            currentIndex = currentSettings.animeVisibility.ordinal,
                        ) { idx ->
                            onUpdate(
                                currentSettings.copy(animeVisibility = UcpSettingConstraint.values()[idx]),
                            )
                        }
                    },
                )
            }

            item {
                ConstraintListItem(
                    title = stringResource(R.string.profile_preference_manga),
                    constraint = currentSettings.mangaVisibility,
                    titles = constraintTitles,
                    onClick = {
                        activeConstraintDialog = ConstraintDialogConfig(
                            title = context.getString(R.string.profile_preference_manga),
                            titles = constraintTitles,
                            currentIndex = currentSettings.mangaVisibility.ordinal,
                        ) { idx ->
                            onUpdate(
                                currentSettings.copy(mangaVisibility = UcpSettingConstraint.values()[idx]),
                            )
                        }
                    },
                )
            }

            item {
                ConstraintListItem(
                    title = stringResource(R.string.profile_preference_comment),
                    constraint = currentSettings.commentVisibility,
                    titles = constraintTitles,
                    onClick = {
                        activeConstraintDialog = ConstraintDialogConfig(
                            title = context.getString(R.string.profile_preference_comment),
                            titles = constraintTitles,
                            currentIndex = currentSettings.commentVisibility.ordinal,
                        ) { idx ->
                            onUpdate(
                                currentSettings.copy(commentVisibility = UcpSettingConstraint.values()[idx]),
                            )
                        }
                    },
                )
            }

            item {
                ConstraintListItem(
                    title = stringResource(R.string.profile_preference_forum),
                    constraint = currentSettings.forumVisibility,
                    titles = constraintTitles,
                    onClick = {
                        activeConstraintDialog = ConstraintDialogConfig(
                            title = context.getString(R.string.profile_preference_forum),
                            titles = constraintTitles,
                            currentIndex = currentSettings.forumVisibility.ordinal,
                        ) { idx ->
                            onUpdate(
                                currentSettings.copy(forumVisibility = UcpSettingConstraint.values()[idx]),
                            )
                        }
                    },
                )
            }

            item {
                ConstraintListItem(
                    title = stringResource(R.string.profile_preference_friend),
                    constraint = currentSettings.friendVisibility,
                    titles = constraintTitles,
                    onClick = {
                        activeConstraintDialog = ConstraintDialogConfig(
                            title = context.getString(R.string.profile_preference_friend),
                            titles = constraintTitles,
                            currentIndex = currentSettings.friendVisibility.ordinal,
                        ) { idx ->
                            onUpdate(
                                currentSettings.copy(friendVisibility = UcpSettingConstraint.values()[idx]),
                            )
                        }
                    },
                )
            }

            item {
                // friend_request uses different constraint set: DEFAULT(0), EVERYONE(3), PRIVATE(4)
                val friendRequestIdx = when (currentSettings.friendRequestConstraint) {
                    UcpSettingConstraint.DEFAULT -> 0
                    UcpSettingConstraint.EVERYONE -> 1
                    else -> 2 // PRIVATE
                }
                val displayTitle = friendRequestTitles.getOrElse(friendRequestIdx) { "" }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.profile_preference_friend_request)) },
                    supportingContent = { Text(displayTitle) },
                    modifier = Modifier.clickable {
                        activeConstraintDialog = ConstraintDialogConfig(
                            title = context.getString(R.string.profile_preference_friend_request),
                            titles = friendRequestTitles,
                            currentIndex = friendRequestIdx,
                        ) { idx ->
                            val constraint = when (idx) {
                                0 -> UcpSettingConstraint.DEFAULT
                                1 -> UcpSettingConstraint.EVERYONE
                                else -> UcpSettingConstraint.PRIVATE
                            }
                            onUpdate(
                                currentSettings.copy(friendRequestConstraint = constraint),
                            )
                        }
                    },
                )
            }

            item {
                ConstraintListItem(
                    title = stringResource(R.string.profile_preference_about),
                    constraint = currentSettings.aboutVisibility,
                    titles = constraintTitles,
                    onClick = {
                        activeConstraintDialog = ConstraintDialogConfig(
                            title = context.getString(R.string.profile_preference_about),
                            titles = constraintTitles,
                            currentIndex = currentSettings.aboutVisibility.ordinal,
                        ) { idx ->
                            onUpdate(
                                currentSettings.copy(aboutVisibility = UcpSettingConstraint.values()[idx]),
                            )
                        }
                    },
                )
            }

            item {
                ConstraintListItem(
                    title = stringResource(R.string.profile_preference_history),
                    constraint = currentSettings.historyVisibility,
                    titles = constraintTitles,
                    onClick = {
                        activeConstraintDialog = ConstraintDialogConfig(
                            title = context.getString(R.string.profile_preference_history),
                            titles = constraintTitles,
                            currentIndex = currentSettings.historyVisibility.ordinal,
                        ) { idx ->
                            onUpdate(
                                currentSettings.copy(historyVisibility = UcpSettingConstraint.values()[idx]),
                            )
                        }
                    },
                )
            }

            item {
                ConstraintListItem(
                    title = stringResource(R.string.profile_preference_guest_book),
                    constraint = currentSettings.guestBookVisibility,
                    titles = constraintTitles,
                    onClick = {
                        activeConstraintDialog = ConstraintDialogConfig(
                            title = context.getString(R.string.profile_preference_guest_book),
                            titles = constraintTitles,
                            currentIndex = currentSettings.guestBookVisibility.ordinal,
                        ) { idx ->
                            onUpdate(
                                currentSettings.copy(guestBookVisibility = UcpSettingConstraint.values()[idx]),
                            )
                        }
                    },
                )
            }

            item {
                ConstraintListItem(
                    title = stringResource(R.string.profile_preference_guest_book_entry),
                    constraint = currentSettings.guestBookEntryConstraint,
                    titles = constraintTitles,
                    onClick = {
                        activeConstraintDialog = ConstraintDialogConfig(
                            title = context.getString(R.string.profile_preference_guest_book_entry),
                            titles = constraintTitles,
                            currentIndex = currentSettings.guestBookEntryConstraint.ordinal,
                        ) { idx ->
                            onUpdate(
                                currentSettings.copy(guestBookEntryConstraint = UcpSettingConstraint.values()[idx]),
                            )
                        }
                    },
                )
            }

            item {
                ConstraintListItem(
                    title = stringResource(R.string.profile_preference_gallery),
                    constraint = currentSettings.galleryVisibility,
                    titles = constraintTitles,
                    onClick = {
                        activeConstraintDialog = ConstraintDialogConfig(
                            title = context.getString(R.string.profile_preference_gallery),
                            titles = constraintTitles,
                            currentIndex = currentSettings.galleryVisibility.ordinal,
                        ) { idx ->
                            onUpdate(
                                currentSettings.copy(galleryVisibility = UcpSettingConstraint.values()[idx]),
                            )
                        }
                    },
                )
            }

            item {
                ConstraintListItem(
                    title = stringResource(R.string.profile_preference_article),
                    constraint = currentSettings.articleVisibility,
                    titles = constraintTitles,
                    onClick = {
                        activeConstraintDialog = ConstraintDialogConfig(
                            title = context.getString(R.string.profile_preference_article),
                            titles = constraintTitles,
                            currentIndex = currentSettings.articleVisibility.ordinal,
                        ) { idx ->
                            onUpdate(
                                currentSettings.copy(articleVisibility = UcpSettingConstraint.values()[idx]),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ConstraintListItem(
    title: String,
    constraint: UcpSettingConstraint,
    titles: Array<String>,
    onClick: () -> Unit,
) {
    val displayTitle = titles.getOrElse(constraint.ordinal) { "" }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(displayTitle) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ProfileCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

private data class ConstraintDialogConfig(
    val title: String,
    val titles: Array<String>,
    val currentIndex: Int,
    val onSelect: (Int) -> Unit,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConstraintDialogConfig) return false
        return title == other.title && currentIndex == other.currentIndex
    }

    override fun hashCode(): Int = 31 * title.hashCode() + currentIndex
}

@Preview(showBackground = true)
@Composable
private fun ProfileSettingsScreenPreview() {
    ProxerTheme {
        ProfileSettingsContent(
            settings = LocalProfileSettings.default(),
            error = MutableLiveData(),
            updateError = MutableLiveData(),
            onBack = {},
            onUpdate = {},
        )
    }
}
