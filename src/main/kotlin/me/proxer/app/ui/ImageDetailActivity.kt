package me.proxer.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ActivityUtils
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.intentFor
import okhttp3.HttpUrl

/**
 * @author Ruben Gees
 */
class ImageDetailActivity : BaseActivity() {
    companion object {
        private const val URL_EXTRA = "url"

        fun getIntent(context: Context, url: HttpUrl): Intent =
            context.intentFor<ImageDetailActivity>(URL_EXTRA to url.toString())

        fun navigateTo(context: Activity, url: HttpUrl, imageView: ImageView? = null) {
            ActivityUtils.navigateToWithImageTransition(getIntent(context, url), context, imageView)
        }
    }

    override val theme: Int
        get() = preferenceHelper.themeContainer.theme.noBackground

    private val url: String
        get() = intent.getSafeStringExtra(URL_EXTRA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                ImageDetailScreen(url = url, onClose = { supportFinishAfterTransition() })
            }
        }
    }
}
