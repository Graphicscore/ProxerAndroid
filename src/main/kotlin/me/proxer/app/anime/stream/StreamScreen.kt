package me.proxer.app.anime.stream

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.cast.CastPlayer
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.cast.framework.CastButtonFactory
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.delay
import me.proxer.app.R
import me.proxer.app.anime.stream.StreamPlayerManager.PlayerState
import me.proxer.app.ui.compose.ObserveLiveDataEvent
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ErrorUtils.ErrorAction
import me.proxer.app.util.data.PreferenceHelper
import me.proxer.app.util.extension.logErrors
import me.proxer.app.util.extension.toEpisodeAppString
import me.proxer.app.util.extension.toast
import me.proxer.library.enums.Category
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/** How long the autoplay countdown runs before the next episode is loaded. */
private const val AUTOPLAY_COUNTDOWN_SECONDS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(activity: StreamActivity, playerManager: StreamPlayerManager) {
    var playerState by remember { mutableStateOf(PlayerState.LOADING) }
    var error by remember { mutableStateOf<ErrorAction?>(null) }
    var isToolbarVisible by remember { mutableStateOf(false) }

    // Rewind indicator
    var rewindCount by remember { mutableIntStateOf(0) }
    var rewindVisible by remember { mutableStateOf(false) }
    var rewindHideKey by remember { mutableIntStateOf(0) }

    // FastForward indicator
    var ffCount by remember { mutableIntStateOf(0) }
    var ffVisible by remember { mutableStateOf(false) }
    var ffHideKey by remember { mutableIntStateOf(0) }

    // Volume / brightness control overlay
    var controlValue by remember { mutableIntStateOf(0) }
    var controlVisible by remember { mutableStateOf(false) }
    var controlHideKey by remember { mutableIntStateOf(0) }

    // Episode navigation / autoplay
    val preferenceHelper: PreferenceHelper = koinInject()

    val episodeViewModel = koinViewModel<StreamEpisodeViewModel> {
        parametersOf(activity.id, activity.language)
    }

    // Deliberately activity-scoped rather than remembered here: an episode swap tears this subtree
    // down, but the navigation observers below survive it and may run against the disposed
    // composition. See StreamActivity.autoplaySecondsLeftState for the full reasoning.
    var autoplaySecondsLeft by activity.autoplaySecondsLeftState
    var endedEpisode by activity.endedEpisodeState

    val isNavigating by episodeViewModel.isNavigating.observeAsState(false)

    fun navigateToEpisode(episode: Int) {
        autoplaySecondsLeft = null

        episodeViewModel.navigateTo(episode, activity.hosterName)
    }

    DisposableEffect(playerManager) {
        val disposables = CompositeDisposable()

        disposables.add(
            playerManager.playerReadySubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { player ->
                    activity.playerView.player = player
                    activity.toggleStableControls(player is CastPlayer)
                },
        )

        disposables.add(
            playerManager.playerStateSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { state ->
                    playerState = state
                    activity.playerView.keepScreenOn = state == PlayerState.PLAYING
                },
        )

        disposables.add(
            playerManager.errorSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { action -> error = action },
        )

        disposables.add(
            playerManager.playbackEndedSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    val current = activity.episode

                    // A cast handover can produce a second STATE_ENDED for the same episode.
                    if (endedEpisode != current) {
                        endedEpisode = current

                        if (preferenceHelper.isAutoplayNextEpisodeEnabled && activity.hasNextEpisode) {
                            autoplaySecondsLeft = AUTOPLAY_COUNTDOWN_SECONDS
                        }
                    }
                },
        )

        disposables.add(
            activity.playerView.rewindSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    ffVisible = false
                    ffCount = 0
                    rewindCount += 10
                    rewindVisible = true
                    rewindHideKey++
                },
        )

        disposables.add(
            activity.playerView.fastForwardSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    rewindVisible = false
                    rewindCount = 0
                    ffCount += 10
                    ffVisible = true
                    ffHideKey++
                },
        )

        disposables.add(
            activity.playerView.volumeChangeSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { value ->
                    controlValue = value
                    controlVisible = true
                    controlHideKey++
                },
        )

        disposables.add(
            activity.playerView.brightnessChangeSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { value ->
                    controlValue = value
                    controlVisible = true
                    controlHideKey++
                },
        )

        onDispose { disposables.dispose() }
    }

    ObserveLiveDataEvent(episodeViewModel.episodeNavigationResult) { target ->
        // endedEpisode still holding the current episode means playback ended by itself, so the
        // outgoing position should not be restored the next time this episode is opened.
        val wasAutoplay = endedEpisode == activity.episode

        if (target.episode > activity.episode) {
            episodeViewModel.bookmark(target.episode)
        }

        // Must stay last: this reassigns the intent and bumps the content key, which rebuilds this
        // whole subtree — so there is no point clearing any remembered state beforehand.
        activity.switchToEpisode(
            target.episode,
            target.video,
            target.hosterName,
            clearOutgoingPosition = wasAutoplay,
        )
    }

    ObserveLiveDataEvent(episodeViewModel.episodeNavigationError) { action ->
        autoplaySecondsLeft = null

        activity.toast(action.message)
    }

    // Auto-hide timers
    LaunchedEffect(rewindHideKey) {
        if (rewindVisible) {
            delay(1_000)
            rewindVisible = false
            rewindCount = 0
        }
    }

    LaunchedEffect(ffHideKey) {
        if (ffVisible) {
            delay(1_000)
            ffVisible = false
            ffCount = 0
        }
    }

    LaunchedEffect(controlHideKey) {
        if (controlVisible) {
            delay(1_000)
            controlVisible = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(autoplaySecondsLeft != null) {
        if (autoplaySecondsLeft == null) return@LaunchedEffect

        // repeatOnLifecycle suspends while the activity is stopped, so a backgrounded player cannot
        // chain-load episodes; the countdown resumes where it left off.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var remaining = autoplaySecondsLeft

            while (remaining != null && remaining > 0) {
                delay(1_000)

                // Cancelling nulls the state mid-tick, which ends the loop without advancing.
                remaining = autoplaySecondsLeft?.minus(1)
                autoplaySecondsLeft = remaining
            }

            if (remaining != null) {
                navigateToEpisode(activity.episode + 1)
            }
        }
    }

    StreamContent(
        playerState = playerState,
        error = error,
        isToolbarVisible = isToolbarVisible,
        rewindVisible = rewindVisible,
        rewindCount = rewindCount,
        ffVisible = ffVisible,
        ffCount = ffCount,
        controlVisible = controlVisible,
        controlValue = controlValue,
        name = activity.name,
        episode = activity.episode,
        isLandscapeMode = activity.isLandscapeMode,
        isInternalPlayerOnly = activity.isInternalPlayerOnly,
        isProxerStream = activity.isProxerStream,
        hasPreviousEpisode = activity.hasPreviousEpisode,
        hasNextEpisode = activity.hasNextEpisode,
        isNavigating = isNavigating,
        autoplaySecondsLeft = autoplaySecondsLeft,
        onPreviousEpisode = { navigateToEpisode(activity.episode - 1) },
        onNextEpisode = { navigateToEpisode(activity.episode + 1) },
        onCancelAutoplay = { autoplaySecondsLeft = null },
        playerContent = {
            AndroidView(
                factory = { _ ->
                    activity.playerView.also { view ->
                        // The player view outlives the composition: an episode swap bumps the content key and
                        // rebuilds this subtree, and Compose constructs the new holder before disposing the old
                        // one. Without detaching first, adding the same view to the new holder throws
                        // "The specified child already has a parent".
                        (view.parent as? ViewGroup)?.removeView(view)

                        view.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)

                        activity.coverUri?.let { uri ->
                            Glide.with(view)
                                .load(uri)
                                .logErrors()
                                .into(
                                    object : CustomViewTarget<PlayerView, Drawable>(view) {
                                        override fun onLoadFailed(errorDrawable: Drawable?) = Unit
                                        override fun onResourceCleared(placeholder: Drawable?) {
                                            view.defaultArtwork = null
                                        }
                                        override fun onResourceReady(
                                            resource: Drawable,
                                            transition: Transition<in Drawable>?,
                                        ) {
                                            view.defaultArtwork = resource
                                        }
                                    },
                                )
                        }

                        view.setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                isToolbarVisible = visibility == View.VISIBLE
                                activity.toggleFullscreen(visibility == View.GONE)
                            },
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        },
        castButtonContent = if (activity.isProxerStream) {
            {
                AndroidView(
                    factory = { ctx ->
                        MediaRouteButton(ctx).also { button ->
                            CastButtonFactory.setUpMediaRouteButton(ctx, button)
                        }
                    },
                )
            }
        } else {
            null
        },
        onBack = {
            @Suppress("DEPRECATION")
            activity.onBackPressed()
        },
        onRewindClick = {
            activity.playerView.rewind()
            rewindCount += 10
            rewindHideKey++
        },
        onFastForwardClick = {
            activity.playerView.fastForward()
            ffCount += 10
            ffHideKey++
        },
        onRetry = {
            playerManager.retry()
            error = null
        },
        onFinish = { activity.finish() },
        onOpenInOtherApp = { activity.openInOtherApp() },
        onToggleOrientation = { activity.toggleOrientation() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamContent(
    playerState: PlayerState,
    error: ErrorAction?,
    isToolbarVisible: Boolean,
    rewindVisible: Boolean,
    rewindCount: Int,
    ffVisible: Boolean,
    ffCount: Int,
    controlVisible: Boolean,
    controlValue: Int,
    name: String,
    episode: Int,
    isLandscapeMode: Boolean,
    isInternalPlayerOnly: Boolean,
    isProxerStream: Boolean,
    hasPreviousEpisode: Boolean,
    hasNextEpisode: Boolean,
    isNavigating: Boolean,
    autoplaySecondsLeft: Int?,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onCancelAutoplay: () -> Unit,
    playerContent: @Composable () -> Unit,
    castButtonContent: (@Composable () -> Unit)?,
    onBack: () -> Unit,
    onRewindClick: () -> Unit,
    onFastForwardClick: () -> Unit,
    onRetry: () -> Unit,
    onFinish: () -> Unit,
    onOpenInOtherApp: () -> Unit,
    onToggleOrientation: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Player view (replaced with grey placeholder in preview)
        if (LocalInspectionMode.current) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
        } else {
            playerContent()
        }

        // Loading indicator
        if (playerState == PlayerState.LOADING) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        // Rewind indicator
        AnimatedVisibility(
            visible = rewindVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = dimensionResource(R.dimen.stream_indicator_margin)),
        ) {
            Text(
                text = rewindCount.toString(),
                color = Color.White,
                modifier = Modifier.clickable { onRewindClick() },
            )
        }

        // FastForward indicator
        AnimatedVisibility(
            visible = ffVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = dimensionResource(R.dimen.stream_indicator_margin)),
        ) {
            Text(
                text = ffCount.toString(),
                color = Color.White,
                modifier = Modifier.clickable { onFastForwardClick() },
            )
        }

        // Volume / brightness control value overlay
        AnimatedVisibility(
            visible = controlVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                text = "$controlValue%",
                color = Color.White,
            )
        }

        // Autoplay countdown
        AnimatedVisibility(
            visible = autoplaySecondsLeft != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                // Keeps the card clear of the Media3 controller's seek bar.
                .padding(bottom = 72.dp),
        ) {
            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(
                            R.string.stream_autoplay_countdown,
                            autoplaySecondsLeft ?: 0,
                        ),
                    )
                    Row {
                        TextButton(onClick = onCancelAutoplay) {
                            Text(stringResource(R.string.cancel))
                        }
                        // Gated like the toolbar buttons: repeated taps would otherwise dispose and
                        // restart an in-flight resolve.
                        Button(onClick = onNextEpisode, enabled = !isNavigating) {
                            Text(stringResource(R.string.stream_autoplay_play_now))
                        }
                    }
                }
            }
        }

        // Overlay toolbar (shows/hides with player controls)
        AnimatedVisibility(
            visible = isToolbarVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(text = name, color = Color.White)
                        Text(
                            text = Category.ANIME.toEpisodeAppString(context, episode),
                            color = Color.White,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    if (hasPreviousEpisode) {
                        IconButton(onClick = onPreviousEpisode, enabled = !isNavigating) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(R.string.fragment_anime_previous_episode),
                                tint = Color.White,
                            )
                        }
                    }
                    if (hasNextEpisode) {
                        IconButton(onClick = onNextEpisode, enabled = !isNavigating) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = stringResource(R.string.fragment_anime_next_episode),
                                tint = Color.White,
                            )
                        }
                    }
                    if (!isInternalPlayerOnly) {
                        IconButton(onClick = onOpenInOtherApp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.action_open_in_other_app),
                                tint = Color.White,
                            )
                        }
                    }
                    IconButton(onClick = onToggleOrientation) {
                        Icon(
                            imageVector = if (isLandscapeMode) {
                                Icons.Filled.FullscreenExit
                            } else {
                                Icons.Filled.Fullscreen
                            },
                            contentDescription = stringResource(R.string.exoplayer_fullscreen_description),
                            tint = Color.White,
                        )
                    }
                    castButtonContent?.invoke()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0x80000000L.toInt()),
                ),
                modifier = Modifier.statusBarsPadding(),
            )
        }
    }

    // Error dialog
    error?.let { action ->
        AlertDialog(
            onDismissRequest = onFinish,
            text = { Text(stringResource(action.message)) },
            confirmButton = {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.error_action_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = onFinish) {
                    Text(stringResource(R.string.error_action_finish))
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StreamContentPreview() {
    ProxerTheme {
        StreamContent(
            playerState = PlayerState.LOADING,
            error = null,
            isToolbarVisible = false,
            rewindVisible = false,
            rewindCount = 0,
            ffVisible = false,
            ffCount = 0,
            controlVisible = false,
            controlValue = 0,
            name = "My Anime",
            episode = 1,
            isLandscapeMode = false,
            isInternalPlayerOnly = false,
            isProxerStream = false,
            hasPreviousEpisode = true,
            hasNextEpisode = true,
            isNavigating = false,
            autoplaySecondsLeft = null,
            onPreviousEpisode = {},
            onNextEpisode = {},
            onCancelAutoplay = {},
            playerContent = {},
            castButtonContent = null,
            onBack = {},
            onRewindClick = {},
            onFastForwardClick = {},
            onRetry = {},
            onFinish = {},
            onOpenInOtherApp = {},
            onToggleOrientation = {},
        )
    }
}
