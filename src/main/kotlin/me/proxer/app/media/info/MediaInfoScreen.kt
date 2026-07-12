package me.proxer.app.media.info

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.media.MediaActivity
import me.proxer.app.media.MediaInfoViewModel
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ObserveLiveDataEvent
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.extension.toAppString
import me.proxer.app.util.extension.toCategory
import me.proxer.app.util.extension.toEndAppString
import me.proxer.app.util.extension.toStartAppString
import me.proxer.app.util.extension.toTypeAppString
import me.proxer.library.entity.info.Entry
import me.proxer.library.entity.info.MediaUserInfo
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaInfoScreen(id: String) {
    val viewModel = koinViewModel<MediaInfoViewModel> { parametersOf(id) }

    LaunchedEffect(Unit) { viewModel.load() }

    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val userInfo by viewModel.userInfoData.observeAsState()

    MediaInfoContent(
        data = data,
        error = error,
        isLoading = isLoading == true,
        userInfo = userInfo,
        updateResult = viewModel.userInfoUpdateData,
        updateError = viewModel.userInfoUpdateError,
        onRetry = { viewModel.load() },
        onNote = { viewModel.note() },
        onFavorite = { viewModel.toggleFavorite() },
        onFinish = { viewModel.markAsFinished() },
        onSubscribe = { viewModel.toggleSubscription() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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

    Box(modifier = Modifier.fillMaxSize()) {
        ContentScreen(
            isLoading = isLoading,
            error = error,
            onRetry = onRetry,
        ) {
            if (data != null) {
                MediaInfoBody(
                    entry = data,
                    userInfo = userInfo,
                    onNote = onNote,
                    onFavorite = onFavorite,
                    onFinish = onFinish,
                    onSubscribe = onSubscribe,
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaInfoBody(
    entry: Entry,
    userInfo: MediaUserInfo?,
    onNote: () -> Unit,
    onFavorite: () -> Unit,
    onFinish: () -> Unit,
    onSubscribe: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Rating
        if (entry.rating > 0) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "%.1f".format(entry.rating / 2.0f),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Text(
                        text = "(${entry.ratingAmount})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }

        // User info action buttons
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onNote) {
                    Icon(
                        Icons.Default.BookmarkAdd,
                        contentDescription = stringResource(R.string.fragment_media_info_note),
                        tint = if (userInfo?.isNoted == true) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = stringResource(R.string.fragment_media_info_favor),
                        tint = if (userInfo?.isTopTen == true) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                IconButton(onClick = onFinish) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.fragment_media_info_finish),
                        tint = if (userInfo?.isFinished == true) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                IconButton(onClick = onSubscribe) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = stringResource(R.string.fragment_media_info_subscribe),
                        tint = if (userInfo?.isSubscribed == true) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }

        item { HorizontalDivider() }

        // Synonyms
        if (entry.synonyms.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    entry.synonyms.forEach { synonym ->
                        InfoRow(
                            label = synonym.toTypeAppString(context),
                            value = synonym.name,
                        )
                    }
                }
            }
        }

        // Seasons
        if (entry.seasons.isNotEmpty()) {
            item {
                val start = entry.seasons[0].toStartAppString(context)
                val end = if (entry.seasons.size >= 2) entry.seasons[1].toEndAppString(context) else null
                InfoRow(
                    label = stringResource(R.string.fragment_media_info_season_title),
                    value = if (end != null) "$start – $end" else start,
                )
            }
        }

        // Status
        item {
            InfoRow(
                label = stringResource(R.string.fragment_media_info_status_title),
                value = entry.state.toAppString(context),
            )
        }

        // License
        item {
            InfoRow(
                label = stringResource(R.string.fragment_media_info_license_title),
                value = entry.license.toAppString(context),
            )
        }

        // Adaption
        if (entry.adaptionInfo.id != "0") {
            item {
                val medium = entry.adaptionInfo.medium
                val adaptionText = buildString {
                    append(entry.adaptionInfo.name)
                    if (medium != null) append(" (${medium.toAppString(context)})")
                }
                InfoRow(
                    label = stringResource(R.string.fragment_media_info_adaption_title),
                    value = adaptionText,
                    onClick = if (activity != null) {
                        {
                            MediaActivity.navigateTo(
                                activity,
                                entry.adaptionInfo.id,
                                entry.adaptionInfo.name,
                                medium?.toCategory(),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }

        item { HorizontalDivider() }

        // Description
        if (entry.description.isNotBlank()) {
            item {
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item { HorizontalDivider() }
        }

        // Genres
        if (entry.genres.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.fragment_media_info_genres),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    entry.genres.forEach { genre ->
                        AssistChip(onClick = {}, label = { Text(genre.name) })
                    }
                }
            }
        }

        // Tags (rated non-spoiler only in simplified view)
        val visibleTags = entry.tags.filter { it.isRated && !it.isSpoiler }
        if (visibleTags.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.fragment_media_info_tags),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    visibleTags.forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag.name) })
                    }
                }
            }
        }

        // Translator groups
        if (entry.translatorGroups.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.fragment_media_info_translator_groups),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    entry.translatorGroups.forEach { group ->
                        AssistChip(onClick = {}, label = { Text(group.name) })
                    }
                }
            }
        }

        // Industries
        if (entry.industries.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.fragment_media_info_industries),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    entry.industries.forEach { industry ->
                        AssistChip(onClick = {}, label = { Text(industry.name) })
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MediaInfoContentPreview() {
    ProxerTheme {
        MediaInfoContent(
            data = null,
            error = null,
            isLoading = true,
            userInfo = null,
            updateResult = MutableLiveData(null),
            updateError = MutableLiveData(null),
            onRetry = {},
            onNote = {},
            onFavorite = {},
            onFinish = {},
            onSubscribe = {},
        )
    }
}
