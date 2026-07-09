package me.proxer.app.profile

import android.app.Activity
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ActivityUtils
import me.proxer.app.util.extension.intentFor

/**
 * @author Ruben Gees
 */
class ProfileActivity : BaseActivity() {
    companion object {
        private const val USER_ID_EXTRA = "user_id"
        private const val USERNAME_EXTRA = "username"

        fun navigateTo(
            context: Activity,
            userId: String? = null,
            username: String? = null,
            image: String? = null,
            imageView: ImageView? = null,
        ) {
            if (userId.isNullOrBlank() && username.isNullOrBlank()) {
                return
            }

            context
                .intentFor<ProfileActivity>(
                    USER_ID_EXTRA to userId,
                    USERNAME_EXTRA to username,
                ).let { ActivityUtils.navigateToWithImageTransition(it, context, imageView) }
        }
    }

    val userId: String?
        get() =
            when (intent.hasExtra(USER_ID_EXTRA)) {
                true -> intent.getStringExtra(USER_ID_EXTRA)
                false -> intent.data?.pathSegments?.getOrNull(1)
            }

    val username: String?
        get() = intent.getStringExtra(USERNAME_EXTRA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                ProfileScreen(
                    userId = userId,
                    username = username,
                    onBack = { finish() },
                )
            }
        }
    }
}
