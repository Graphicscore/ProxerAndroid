package me.proxer.app.notification

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.intentFor
import me.proxer.app.util.extension.startActivity

/**
 * @author Ruben Gees
 */
class NotificationActivity : BaseActivity() {
    companion object {
        fun navigateTo(context: Activity) = context.startActivity<NotificationActivity>()

        fun getIntent(context: Context) = context.intentFor<NotificationActivity>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                NotificationScreen(onBack = { finish() })
            }
        }
    }
}
