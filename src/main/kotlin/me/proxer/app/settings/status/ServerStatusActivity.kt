package me.proxer.app.settings.status

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.startActivity

/**
 * @author Ruben Gees
 */
class ServerStatusActivity : BaseActivity() {
    companion object {
        fun navigateTo(context: Activity) = context.startActivity<ServerStatusActivity>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                ServerStatusScreen()
            }
        }
    }
}
