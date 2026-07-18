package me.proxer.app.anime.stream

import android.app.Activity
import android.net.Uri
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.smoothstreaming.DefaultSsChunkSource
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.ads.AdsMediaSource
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import me.proxer.app.MainApplication.Companion.USER_AGENT
import me.proxer.app.util.DefaultActivityLifecycleCallbacks
import me.proxer.app.util.ErrorUtils
import okhttp3.OkHttpClient
import java.lang.ref.WeakReference
import kotlin.properties.Delegates

/**
 * @author Ruben Gees
 */
class StreamPlayerManager(context: StreamActivity, rawClient: OkHttpClient, private val adTag: Uri?) {
    private companion object {
        private const val WAS_PLAYING_EXTRA = "was_playing"
        private const val LAST_POSITION_EXTRA = "last_position"
    }

    private val weakContext = WeakReference(context)

    private val castSessionAvailabilityListener =
        object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                if (castPlayer != null) {
                    castPlayer.run {
                        setMediaItem(castMediaItem)
                        seekTo(localPlayer.currentPosition)
                        prepare()
                    }

                    currentPlayer = castPlayer
                }
            }

            override fun onCastSessionUnavailable() {
                currentPlayer = localPlayer

                if (!isResumed) {
                    wasPlaying = false
                }
            }
        }

    private val eventListener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING, Player.STATE_IDLE -> {
                        playerStateSubject.onNext(PlayerState.LOADING)
                    }

                    Player.STATE_ENDED -> {
                        playerStateSubject.onNext(PlayerState.PAUSING)
                        playbackEndedSubject.onNext(Unit)
                    }

                    Player.STATE_READY -> {
                        playerStateSubject.onNext(
                            when (currentPlayer.playWhenReady) {
                                true -> PlayerState.PLAYING
                                false -> PlayerState.PAUSING
                            },
                        )
                    }
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (currentPlayer.playbackState == Player.STATE_READY) {
                    playerStateSubject.onNext(
                        when (playWhenReady) {
                            true -> PlayerState.PLAYING
                            false -> PlayerState.PAUSING
                        },
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                lastPosition = currentPlayer.currentPosition

                errorSubject.onNext(ErrorUtils.handle(error))
            }
        }

    private val lifecycleCallbacks =
        object : DefaultActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity == weakContext.get()) {
                    isResumed = true
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (activity == weakContext.get()) {
                    isFirstStart = false
                    isResumed = false
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (activity == weakContext.get()) {
                    activity.application.unregisterActivityLifecycleCallbacks(this)

                    localPlayer.release()
                    castPlayer?.release()

                    localPlayer.removeListener(eventListener)
                    castPlayer?.removeListener(eventListener)

                    @Suppress("DEPRECATION")
                    castPlayer?.setSessionAvailabilityListener(null)

                    adsLoader?.release()
                    adsLoader = null
                }
            }
        }

    private val client = buildClient(rawClient)

    private val localPlayer = buildLocalPlayer(context)
    private val castPlayer = buildCastPlayer(context)

    private var adsLoader: ImaAdsLoader? =
        when {
            adTag != null -> {
                ImaAdsLoader.Builder(context).build().apply {
                    setPlayer(localPlayer)
                }
            }

            else -> {
                null
            }
        }

    private var localMediaSource = buildLocalMediaSourceWithAds(client, uri)
    private var castMediaItem = buildCastMediaItem(name, episode, coverUri, uri)

    private val uri get() = requireNotNull(weakContext.get()?.uri)
    private val name: String? get() = weakContext.get()?.name
    private val episode: Int? get() = weakContext.get()?.episode
    private val coverUri: Uri? get() = weakContext.get()?.coverUri
    private val referer: String? get() = weakContext.get()?.referer

    private var lastPosition: Long
        get() = weakContext.get()?.intent?.getLongExtra(LAST_POSITION_EXTRA, -1) ?: -1
        set(value) {
            weakContext.get()?.intent?.putExtra(LAST_POSITION_EXTRA, value)
        }

    private var wasPlaying: Boolean
        get() = weakContext.get()?.intent?.getBooleanExtra(WAS_PLAYING_EXTRA, false) ?: false
        set(value) {
            weakContext.get()?.intent?.putExtra(WAS_PLAYING_EXTRA, value)
        }

    private var isResumed = false
    private var isFirstStart = true

    var currentPlayer by Delegates.observable<Player>(localPlayer) { _, old, new ->
        old.playWhenReady = false
        new.playWhenReady = isResumed

        new.seekTo(old.currentPosition)

        playerReadySubject.onNext(new)
    }
        private set

    val isPlayingAd: Boolean
        get() = localPlayer.isPlayingAd

    val playerReadySubject = BehaviorSubject.createDefault<Player>(localPlayer)
    val playerStateSubject = PublishSubject.create<PlayerState>()
    val errorSubject = PublishSubject.create<ErrorUtils.ErrorAction>()
    val playbackEndedSubject = PublishSubject.create<Unit>()

    init {
        localPlayer.addListener(eventListener)
        castPlayer?.addListener(eventListener)

        localPlayer.setMediaSource(localMediaSource)
        localPlayer.prepare()

        context.application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    fun play(position: Long? = null) {
        if (isFirstStart && position != null) {
            lastPosition = position
        }

        if (currentPlayer.currentPosition <= 0 && lastPosition > 0) {
            currentPlayer.seekTo(lastPosition)
        }

        if (isFirstStart || wasPlaying) {
            currentPlayer.playWhenReady = true
        }
    }

    fun pause() {
        wasPlaying = currentPlayer.playWhenReady == true && currentPlayer.playbackState == Player.STATE_READY
        lastPosition = currentPlayer.currentPosition

        localPlayer.playWhenReady = false
    }

    fun toggle() {
        currentPlayer.playWhenReady = currentPlayer.playWhenReady.not()
    }

    fun retry() {
        if (currentPlayer == localPlayer) {
            localPlayer.setMediaSource(localMediaSource, false)
            localPlayer.prepare()
        } else if (currentPlayer == castPlayer) {
            castPlayer.run {
                setMediaItem(castMediaItem)
                seekTo(lastPosition)
                prepare()
            }
        }
    }

    fun reset(startPosition: Long = -1) {
        currentPlayer.playWhenReady = false

        wasPlaying = false
        // Coerced because an episode that has never been watched has no stored position (-1), and
        // that value would otherwise reach CastPlayer.seekTo via retry().
        lastPosition = startPosition.coerceAtLeast(0)

        localMediaSource = buildLocalMediaSourceWithAds(client, uri)
        castMediaItem = buildCastMediaItem(name, episode, coverUri, uri)

        retry()

        // retry() prepares the local player with setMediaSource(source, resetPosition = false), which
        // keeps the *outgoing* episode's position. play() is not called on this path either, so the
        // new media must always be seeked explicitly — including to 0. Skipping the seek when the
        // position is 0 would start a fresh episode at the previous one's runtime, which ends it
        // instantly and chain-loads the rest of the series.
        currentPlayer.seekTo(lastPosition)

        currentPlayer.playWhenReady = true
    }

    private fun buildClient(rawClient: OkHttpClient): OkHttpClient = referer.let { referer ->
        if (referer == null) {
            rawClient
        } else {
            rawClient
                .newBuilder()
                .addInterceptor {
                    val requestWithReferer =
                        it
                            .request()
                            .newBuilder()
                            .header("Referer", referer)
                            .build()

                    it.proceed(requestWithReferer)
                }.build()
        }
    }

    private fun buildLocalMediaSourceWithAds(client: OkHttpClient, uri: Uri): MediaSource {
        val context = requireNotNull(weakContext.get())
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(client).setUserAgent(USER_AGENT)
        val localMediaSource = buildLocalMediaSource(okHttpDataSourceFactory, uri)
        val safeAdsLoader = adsLoader
        val safeAdTag = adTag

        return if (safeAdsLoader != null && safeAdTag != null) {
            AdsMediaSource(
                localMediaSource,
                DataSpec(safeAdTag),
                safeAdTag,
                DefaultMediaSourceFactory(okHttpDataSourceFactory),
                safeAdsLoader,
                context.playerView,
            )
        } else {
            localMediaSource
        }
    }

    private fun buildLocalMediaSource(dataSourceFactory: DataSource.Factory, uri: Uri): MediaSource {
        val mediaItem = MediaItem.fromUri(uri)
        return when (val streamType = Util.inferContentType(uri)) {
            C.CONTENT_TYPE_SS -> {
                SsMediaSource
                    .Factory(DefaultSsChunkSource.Factory(dataSourceFactory), dataSourceFactory)
                    .createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_DASH -> {
                DashMediaSource
                    .Factory(DefaultDashChunkSource.Factory(dataSourceFactory), dataSourceFactory)
                    .createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_HLS -> {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_OTHER -> {
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }

            else -> {
                error("Unknown streamType: $streamType")
            }
        }
    }

    private fun buildCastMediaItem(name: String?, episode: Int?, coverUri: Uri?, uri: Uri): MediaItem {
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle(name)
                .build()
        return MediaItem
            .Builder()
            .setUri(uri)
            .setMimeType(MimeTypes.VIDEO_MP4)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildLocalPlayer(context: StreamActivity): ExoPlayer = ExoPlayer.Builder(context).build().apply {
        val audioAttributes =
            AudioAttributes
                .Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

        setWakeMode(C.WAKE_MODE_NETWORK)
        setHandleAudioBecomingNoisy(true)
        setAudioAttributes(audioAttributes, true)
    }

    @Suppress("DEPRECATION")
    private fun buildCastPlayer(context: StreamActivity): CastPlayer? = context
        .getSafeCastContext()
        ?.let { CastPlayer(it) }
        ?.apply { setSessionAvailabilityListener(castSessionAvailabilityListener) }

    enum class PlayerState {
        PLAYING,
        PAUSING,
        LOADING,
    }
}
