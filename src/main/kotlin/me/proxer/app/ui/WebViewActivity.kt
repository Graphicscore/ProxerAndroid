package me.proxer.app.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.startActivity

/**
 * @author Ruben Gees
 */
class WebViewActivity : BaseActivity() {
    companion object {
        private const val URL_EXTRA = "url"

        fun navigateTo(context: Activity, url: String) = context.startActivity<WebViewActivity>(URL_EXTRA to url)
    }

    private val url: String
        get() = intent.getSafeStringExtra(URL_EXTRA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                WebViewScreen(url = url, onBack = { finish() })
            }
        }
    }
}
