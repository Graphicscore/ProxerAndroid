package me.proxer.app.profile

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

            getIntent(context, userId, username).let {
                ActivityUtils.navigateToWithImageTransition(it, context, imageView)
            }
        }

        fun getIntent(context: Context, userId: String? = null, username: String? = null): Intent =
            context.intentFor<ProfileActivity>(
                USER_ID_EXTRA to userId,
                USERNAME_EXTRA to username,
            )
    }

    val userId: String?
        get() =
            when (intent.hasExtra(USER_ID_EXTRA)) {
                true -> intent.getStringExtra(USER_ID_EXTRA)
                false -> intent.data?.pathSegments?.getOrNull(1)
            }

    val username: String?
        get() = intent.getStringExtra(USERNAME_EXTRA)

    private val initialTab: Int
        get() = when (intent.action) {
            Intent.ACTION_VIEW -> when (intent.data?.pathSegments?.getOrNull(2)) {
                "about" -> 1
                "anime" -> 3
                "manga" -> 4
                "chronik" -> 6
                else -> 0
            }

            else -> 0
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                ProfileScreen(
                    userId = userId,
                    username = username,
                    initialTab = initialTab,
                    onBack = { finish() },
                )
            }
        }
    }
}
