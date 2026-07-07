package me.proxer.app.profile.settings

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.startActivity

/**
 * @author Ruben Gees
 */
class ProfileSettingsActivity : BaseActivity() {
    companion object {
        fun navigateTo(context: Activity) = context.startActivity<ProfileSettingsActivity>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        storageHelper.isLoggedInObservable
            .filter { it.not() }
            .autoDisposable(this.scope())
            .subscribe { finish() }

        setContent {
            ProxerTheme {
                ProfileSettingsScreen(onBack = { finish() })
            }
        }
    }
}
