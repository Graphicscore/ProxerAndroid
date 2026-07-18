package me.proxer.app.anime

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.core.content.getSystemService
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import me.proxer.app.R
import me.proxer.app.anime.resolver.AnimeStreamContext
import me.proxer.app.anime.resolver.ProxerStreamResolver
import me.proxer.app.anime.resolver.StreamResolutionResult
import me.proxer.app.auth.LoginDialog
import me.proxer.app.info.translatorgroup.TranslatorGroupActivity
import me.proxer.app.media.MediaActivity
import me.proxer.app.profile.ProfileActivity
import me.proxer.app.profile.settings.ProfileSettingsActivity
import me.proxer.app.ui.compose.ContentScreen
import me.proxer.app.ui.compose.ObserveLiveDataEvent
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.ui.compose.ProxerTopAppBar
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.Utils
import me.proxer.app.util.compat.isConnectedToCellular
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.data.StorageHelper
import me.proxer.app.util.extension.toEpisodeAppString
import me.proxer.app.util.extension.toLocalDateTime
import me.proxer.library.enums.AnimeLanguage
import me.proxer.library.enums.Category
import me.proxer.library.util.ProxerUrls
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.threeten.bp.Instant
import org.threeten.bp.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeScreen(
    id: String,
    initialEpisode: Int,
    language: AnimeLanguage,
    initialName: String?,
    initialEpisodeAmount: Int?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val storageHelper: StorageHelper = koinInject()
    val preferenceHelper: PreferenceHelper = koinInject()

    var episode by remember { mutableStateOf(initialEpisode) }

    val viewModel = koinViewModel<AnimeViewModel> { parametersOf(id, language, episode) }
    val data by viewModel.data.observeAsState()
    val error by viewModel.error.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)

    val name = data?.name ?: initialName
    val episodeAmount = data?.episodeAmount ?: initialEpisodeAmount
    val streams = data?.streams ?: emptyList()

    var expandedStreamId by remember { mutableStateOf<String?>(null) }
    var noWifiStream by remember { mutableStateOf<AnimeStream?>(null) }
    var noWifiRemember by remember { mutableStateOf(false) }
    // The stream the user actually tapped, so the player can be told which hoster it is playing.
    var appRequiredAction by remember { mutableStateOf<AppRequiredErrorAction?>(null) }
    var lastAdAlertDate by remember { mutableStateOf(storageHelper.lastAdAlertDate) }
    var isLoggedIn by remember { mutableStateOf(storageHelper.isLoggedIn) }
    var showLoginDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val disposable = storageHelper.isLoggedInObservable
            .subscribe { isLoggedIn = storageHelper.isLoggedIn }
        onDispose { disposable.dispose() }
    }

    LaunchedEffect(Unit) { viewModel.load() }

    ObserveLiveDataEvent(viewModel.resolutionResult) { result ->
        when (result) {
            is StreamResolutionResult.Video -> {
                result.play(
                    context,
                    AnimeStreamContext(
                        id = id,
                        name = name,
                        episode = episode,
                        episodeAmount = episodeAmount ?: -1,
                        language = language,
                        coverUri = Uri.parse(ProxerUrls.entryImage(id).toString()),
                        hosterName = viewModel.resolvingHosterName,
                    ),
                    forceInternal = true,
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

    AnimeContent(
        name = name,
        episode = episode,
        episodeAmount = episodeAmount,
        streams = streams,
        isLoading = isLoading == true,
        error = error,
        isLoggedIn = isLoggedIn,
        lastAdAlertDate = lastAdAlertDate,
        expandedStreamId = expandedStreamId,
        noWifiStream = noWifiStream,
        noWifiRemember = noWifiRemember,
        appRequiredAction = appRequiredAction,
        showLoginDialog = showLoginDialog,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onMediaClick = {
            name?.let { MediaActivity.navigateTo(context as Activity, id, it, Category.ANIME) }
        },
        onShare = {
            if (name != null) {
                ShareCompat
                    .IntentBuilder(context as Activity)
                    .setText(
                        context.getString(
                            R.string.share_anime,
                            episode,
                            name,
                            ProxerUrls.animeWeb(id, episode, language),
                        ),
                    ).setType("text/plain")
                    .setChooserTitle(context.getString(R.string.share_title))
                    .startChooser()
            }
        },
        onRetry = { viewModel.load() },
        onPrevious = {
            val newEpisode = episode - 1
            episode = newEpisode
            viewModel.episode = newEpisode
        },
        onNext = {
            val newEpisode = episode + 1
            if (preferenceHelper.areBookmarksAutomatic && isLoggedIn) {
                viewModel.bookmark(newEpisode)
            }
            episode = newEpisode
            viewModel.episode = newEpisode
        },
        onBookmarkThis = { viewModel.bookmark(episode) },
        onBookmarkOrFinish = {
            if (episode < (episodeAmount ?: Int.MAX_VALUE)) {
                viewModel.bookmark(episode + 1)
            } else {
                viewModel.markAsFinished()
            }
        },
        onStreamHeaderClick = { streamId ->
            expandedStreamId = if (expandedStreamId == streamId) null else streamId
        },
        onUploaderClick = { uploaderId, uploaderName ->
            ProfileActivity.navigateTo(context as Activity, uploaderId, uploaderName)
        },
        onTranslatorGroupClick = { tgId, tgName ->
            TranslatorGroupActivity.navigateTo(context as Activity, tgId, tgName)
        },
        onDismissAdAlert = {
            storageHelper.lastAdAlertDate = Instant.now()
            lastAdAlertDate = Instant.now()
        },
        onSetAdInterval = {
            storageHelper.lastAdAlertDate = Instant.now()
            lastAdAlertDate = Instant.now()
            ProfileSettingsActivity.navigateTo(context as Activity)
        },
        onPlay = { stream ->
            val connectivityManager = requireNotNull(context.getSystemService<ConnectivityManager>())
            if (connectivityManager.isConnectedToCellular && preferenceHelper.shouldCheckCellular) {
                noWifiStream = stream
            } else {
                viewModel.resolve(stream)
            }
        },
        onLoginClick = { showLoginDialog = true },
        onDismissNoWifi = {
            noWifiStream = null
            noWifiRemember = false
        },
        onNoWifiRememberChange = { noWifiRemember = it },
        onConfirmNoWifi = {
            if (noWifiRemember) preferenceHelper.shouldCheckCellular = false
            noWifiStream?.let {
                viewModel.resolve(it)
            }
            noWifiStream = null
            noWifiRemember = false
        },
        onDismissLoginDialog = { showLoginDialog = false },
        onDismissAppRequired = { appRequiredAction = null },
        onConfirmAppRequired = {
            appRequiredAction?.let { action ->
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${action.appPackage}")),
                    )
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=${action.appPackage}"),
                        ),
                    )
                }
            }
            appRequiredAction = null
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimeContent(
    name: String?,
    episode: Int,
    episodeAmount: Int?,
    streams: List<AnimeStream>,
    isLoading: Boolean,
    error: ErrorAction?,
    isLoggedIn: Boolean,
    lastAdAlertDate: Instant,
    expandedStreamId: String?,
    noWifiStream: AnimeStream?,
    noWifiRemember: Boolean,
    appRequiredAction: AppRequiredErrorAction?,
    showLoginDialog: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onMediaClick: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBookmarkThis: () -> Unit,
    onBookmarkOrFinish: () -> Unit,
    onStreamHeaderClick: (String) -> Unit,
    onUploaderClick: (String, String) -> Unit,
    onTranslatorGroupClick: (String, String) -> Unit,
    onDismissAdAlert: () -> Unit,
    onSetAdInterval: () -> Unit,
    onPlay: (AnimeStream) -> Unit,
    onLoginClick: () -> Unit,
    onDismissNoWifi: () -> Unit,
    onNoWifiRememberChange: (Boolean) -> Unit,
    onConfirmNoWifi: () -> Unit,
    onDismissLoginDialog: () -> Unit,
    onDismissAppRequired: () -> Unit,
    onConfirmAppRequired: () -> Unit,
) {
    val context = LocalContext.current

    if (noWifiStream != null) {
        AlertDialog(
            onDismissRequest = onDismissNoWifi,
            text = {
                Column {
                    Text(stringResource(R.string.dialog_no_wifi_content))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = noWifiRemember,
                            onCheckedChange = onNoWifiRememberChange,
                        )
                        Text(stringResource(R.string.dialog_no_wifi_remember))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmNoWifi) {
                    Text(stringResource(R.string.dialog_no_wifi_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissNoWifi) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showLoginDialog) {
        LoginDialog(onDismiss = onDismissLoginDialog)
    }

    appRequiredAction?.let { action ->
        AlertDialog(
            onDismissRequest = onDismissAppRequired,
            title = { Text(stringResource(R.string.dialog_app_required_title, action.name)) },
            text = { Text(stringResource(R.string.dialog_app_required_content, action.name)) },
            confirmButton = {
                TextButton(onClick = onConfirmAppRequired) {
                    Text(stringResource(R.string.dialog_app_required_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissAppRequired) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            ProxerTopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable { onMediaClick() },
                    ) {
                        if (name != null) {
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            text = Category.ANIME.toEpisodeAppString(context, episode),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                onBack = onBack,
                actions = {
                    if (name != null) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        ContentScreen(
            isLoading = isLoading,
            error = error,
            onRetry = onRetry,
            modifier = Modifier.padding(padding),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (episodeAmount != null) {
                    item {
                        EpisodeControlCard(
                            episode = episode,
                            episodeAmount = episodeAmount,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onBookmarkThis = onBookmarkThis,
                            onBookmarkOrFinish = onBookmarkOrFinish,
                        )
                    }
                }

                if (streams.isEmpty() && !isLoading && error == null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.error_no_data_anime))
                        }
                    }
                }

                items(streams, key = { it.id }) { stream ->
                    if (stream.resolutionResult is StreamResolutionResult.Message) {
                        MessageStreamItem(stream = stream)
                    } else {
                        StreamItem(
                            stream = stream,
                            isExpanded = expandedStreamId == stream.id,
                            isLoggedIn = isLoggedIn,
                            lastAdAlertDate = lastAdAlertDate,
                            onHeaderClick = { onStreamHeaderClick(stream.id) },
                            onUploaderClick = { onUploaderClick(stream.uploaderId, stream.uploaderName) },
                            onTranslatorGroupClick = {
                                val tgId = stream.translatorGroupId ?: return@StreamItem
                                val tgName = stream.translatorGroupName ?: return@StreamItem
                                onTranslatorGroupClick(tgId, tgName)
                            },
                            onDismissAdAlert = onDismissAdAlert,
                            onSetAdInterval = onSetAdInterval,
                            onPlay = { onPlay(stream) },
                            onLoginClick = onLoginClick,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AnimeContentPreview() {
    ProxerTheme {
        AnimeContent(
            name = "My Anime",
            episode = 1,
            episodeAmount = 12,
            streams = emptyList(),
            isLoading = true,
            error = null,
            isLoggedIn = false,
            lastAdAlertDate = Instant.now(),
            expandedStreamId = null,
            noWifiStream = null,
            noWifiRemember = false,
            appRequiredAction = null,
            showLoginDialog = false,
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onMediaClick = {},
            onShare = {},
            onRetry = {},
            onPrevious = {},
            onNext = {},
            onBookmarkThis = {},
            onBookmarkOrFinish = {},
            onStreamHeaderClick = {},
            onUploaderClick = { _, _ -> },
            onTranslatorGroupClick = { _, _ -> },
            onDismissAdAlert = {},
            onSetAdInterval = {},
            onPlay = {},
            onLoginClick = {},
            onDismissNoWifi = {},
            onNoWifiRememberChange = {},
            onConfirmNoWifi = {},
            onDismissLoginDialog = {},
            onDismissAppRequired = {},
            onConfirmAppRequired = {},
        )
    }
}

@Composable
private fun EpisodeControlCard(
    episode: Int,
    episodeAmount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBookmarkThis: () -> Unit,
    onBookmarkOrFinish: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (episode > 1) {
                    TextButton(onClick = onPrevious) {
                        Text(stringResource(R.string.fragment_anime_previous_episode))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (episode < episodeAmount) {
                    TextButton(onClick = onNext) {
                        Text(stringResource(R.string.fragment_anime_next_episode))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBookmarkThis) {
                    Text(stringResource(R.string.fragment_anime_bookmark_this_episode))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onBookmarkOrFinish) {
                    val label = if (episode < episodeAmount) {
                        stringResource(R.string.fragment_anime_bookmark_next_episode)
                    } else {
                        stringResource(R.string.view_media_control_finish)
                    }
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun StreamItem(
    stream: AnimeStream,
    isExpanded: Boolean,
    isLoggedIn: Boolean,
    lastAdAlertDate: Instant,
    onHeaderClick: () -> Unit,
    onUploaderClick: () -> Unit,
    onTranslatorGroupClick: () -> Unit,
    onDismissAdAlert: () -> Unit,
    onSetAdInterval: () -> Unit,
    onPlay: () -> Unit,
    onLoginClick: () -> Unit,
) {
    val context = LocalContext.current
    val storageHelper: StorageHelper = koinInject()
    val isLoginRequired = !stream.isPublic && !isLoggedIn

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHeaderClick)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = ProxerUrls.hosterImage(stream.image).toString(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(64.dp)
                        .padding(4.dp),
                )
                Text(
                    text = stream.hosterName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            if (isExpanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = onUploaderClick) {
                        Text(stream.uploaderName)
                    }
                    TextButton(onClick = onTranslatorGroupClick) {
                        Text(
                            stream.translatorGroupName
                                ?: stringResource(R.string.fragment_anime_empty_subgroup),
                        )
                    }
                    Text(
                        text = stream.date.toLocalDateTime().format(Utils.dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )

                    val shouldShowAdAlert = ProxerStreamResolver.supports(stream.hosterName) &&
                        lastAdAlertDate.plus(14, ChronoUnit.DAYS).isBefore(Instant.now()) &&
                        storageHelper.profileSettings.adInterval <= 0

                    if (shouldShowAdAlert) {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = stringResource(R.string.fragment_anime_ad_alert),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Row {
                                    TextButton(onClick = onDismissAdAlert) {
                                        Text(stringResource(R.string.fragment_anime_ad_alert_dismiss))
                                    }
                                    TextButton(onClick = onSetAdInterval) {
                                        Text(stringResource(R.string.fragment_anime_ad_alert_set_interval))
                                    }
                                }
                            }
                        }
                    } else if (stream.isSupported) {
                        if (isLoginRequired) {
                            Text(
                                text = stringResource(R.string.fragment_anime_stream_login_required_warning),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            Button(
                                onClick = onLoginClick,
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
                            ) {
                                Text(stringResource(R.string.error_action_login))
                            }
                        } else {
                            if (stream.isOfficial) {
                                Text(
                                    text = stringResource(R.string.fragment_anime_stream_official_info),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                            Button(
                                onClick = onPlay,
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
                            ) {
                                Text(stringResource(R.string.fragment_anime_stream_play))
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.error_unsupported_hoster),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageStreamItem(stream: AnimeStream) {
    val message = (stream.resolutionResult as StreamResolutionResult.Message).message
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = message.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}
