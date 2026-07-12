package me.proxer.app.anime.stream

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.os.postDelayed
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import me.proxer.app.R
import me.proxer.app.anime.resolver.StreamResolutionResult
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.AD_TAG_EXTRA
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.COVER_EXTRA
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.EPISODE_EXTRA
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.ID_EXTRA
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.INTERNAL_PLAYER_ONLY_EXTRA
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.LANGUAGE_EXTRA
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.NAME_EXTRA
import me.proxer.app.anime.resolver.StreamResolutionResult.Video.Companion.REFERER_EXTRA
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.newTask
import me.proxer.app.util.extension.safeInject
import me.proxer.app.util.extension.toPrefixedUrlOrNull
import me.proxer.app.util.extension.toast
import me.proxer.app.util.extension.unsafeLazy
import me.proxer.library.enums.AnimeLanguage
import me.proxer.library.util.ProxerUrls.hasProxerStreamFileHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * @author Ruben Gees
 */
class StreamActivity : BaseActivity() {
    companion object {
        private const val CLOUDFLARE_HOST = "videodelivery.net"
    }

    internal val id: String
        get() = intent.getSafeStringExtra(ID_EXTRA)

    internal val name: String
        get() = intent.getSafeStringExtra(NAME_EXTRA)

    internal val episode: Int
        get() = intent.getIntExtra(EPISODE_EXTRA, -1).let { if (it <= 0) 1 else it }

    internal val language: AnimeLanguage
        get() =
            requireNotNull(IntentCompat.getSerializableExtra(intent, LANGUAGE_EXTRA, AnimeLanguage::class.java)) {
                "LANGUAGE_EXTRA missing from StreamActivity intent. id=$id episode=$episode"
            }

    internal val coverUri: Uri?
        get() = IntentCompat.getParcelableExtra(intent, COVER_EXTRA, Uri::class.java)

    internal val referer: String?
        get() = intent.getStringExtra(REFERER_EXTRA)

    internal val uri: Uri
        get() = requireNotNull(intent.data)

    internal val isProxerStream: Boolean
        get() {
            val url = intent.dataString?.toPrefixedUrlOrNull()

            return url != null && (url.hasProxerStreamFileHost || url.host == CLOUDFLARE_HOST)
        }

    private val mimeType: String
        get() = requireNotNull(intent.type)

    internal val isInternalPlayerOnly: Boolean
        get() = intent.getBooleanExtra(INTERNAL_PLAYER_ONLY_EXTRA, false)

    private val adTag: Uri?
        get() = IntentCompat.getParcelableExtra(intent, AD_TAG_EXTRA, Uri::class.java)

    private val client by safeInject<OkHttpClient>()
    internal val playerManager by unsafeLazy { StreamPlayerManager(this, client, adTag) }

    internal lateinit var playerView: TouchablePlayerView
        private set

    internal var isLandscapeMode by mutableStateOf(false)
        private set

    internal var isInFullscreenMode = false
        private set

    private var pendingSystemBarsVisible: Boolean? = null

    private val clearPendingSystemBarsRunnable = Runnable { pendingSystemBarsVisible = null }

    private var contentKey by mutableIntStateOf(0)

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        playerView = TouchablePlayerView(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            val systemBarsVisible = insets.isVisible(WindowInsetsCompat.Type.systemBars())
            val expected = pendingSystemBarsVisible

            when {
                expected == null -> handleUIChange(systemBarsVisible)

                systemBarsVisible == expected -> {
                    pendingSystemBarsVisible = null
                    mainHandler.removeCallbacks(clearPendingSystemBarsRunnable)
                }

                else -> Unit
            }

            ViewCompat.onApplyWindowInsets(v, insets)
        }

        setContent {
            ProxerTheme {
                key(contentKey) {
                    StreamScreen(activity = this@StreamActivity, playerManager = playerManager)
                }
            }
        }

        if (savedInstanceState == null) {
            toggleOrientation()
        }
    }

    override fun onStart() {
        super.onStart()

        playerManager.play(storageHelper.getLastAnimePosition(id, episode, language))
    }

    override fun onStop() {
        playerManager.pause()

        val lastPosition = playerManager.currentPlayer.currentPosition

        if (lastPosition > 0) {
            storageHelper.putLastAnimePosition(id, episode, language, lastPosition)
        }

        super.onStop()
    }

    override fun onDestroy() {
        playerView.player = null

        mainHandler.removeCallbacksAndMessages(null)

        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.data != null && intent.data != this.intent.data) {
            this.intent = intent

            playerManager.reset()
            contentKey++
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)

        handleUIChange()
    }

    internal fun getSafeCastContext(): CastContext? {
        val availabilityResult = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)

        return if (availabilityResult == ConnectionResult.SUCCESS) {
            try {
                CastContext.getSharedInstance(this)
            } catch (error: Exception) {
                Timber.e(error)

                null
            }
        } else {
            null
        }
    }

    internal fun toggleOrientation() {
        if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            isLandscapeMode = false
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            isLandscapeMode = true
        }
    }

    internal fun toggleFullscreen(wantFullscreen: Boolean) {
        val isInMultiWindowMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && this.isInMultiWindowMode
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        pendingSystemBarsVisible = !wantFullscreen || isInMultiWindowMode
        mainHandler.removeCallbacks(clearPendingSystemBarsRunnable)
        // Safety margin in case the system bars are already at the target state and never
        // redispatch insets, which would otherwise leave pendingSystemBarsVisible stuck forever.
        mainHandler.postDelayed(clearPendingSystemBarsRunnable, 500)

        if (wantFullscreen && !isInMultiWindowMode) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            isInFullscreenMode = true
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            isInFullscreenMode = false
        }
    }

    internal fun handleUIChange(systemBarsVisible: Boolean = !isInFullscreenMode) {
        if (!playerManager.isPlayingAd) {
            if (systemBarsVisible) {
                playerView.showController()
            } else {
                playerView.hideController()
            }
        } else {
            mainHandler.postDelayed(3_000) {
                toggleFullscreen(true)
            }
        }
    }

    internal fun toggleStableControls(stable: Boolean) {
        if (stable) {
            playerView.controllerHideOnTouch = false
            playerView.controllerShowTimeoutMs = 0
            playerView.showController()
        } else {
            playerView.controllerHideOnTouch = true
            playerView.controllerShowTimeoutMs = 2_000
        }
    }

    internal fun openInOtherApp(): Boolean = try {
        val intent =
            StreamResolutionResult
                .Video(uri.toString().toHttpUrl(), mimeType, referer)
                .makeIntent(this)
                .newTask()

        startActivity(intent)
        finish()

        true
    } catch (ignored: ActivityNotFoundException) {
        toast(R.string.activity_stream_open_no_app)

        false
    }
}
