package me.proxer.app.chat.prv.conference.info

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.chat.prv.LocalConference
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.getSafeParcelableExtra
import me.proxer.app.util.extension.startActivity

/**
 * @author Ruben Gees
 */
class ConferenceInfoActivity : BaseActivity() {
    companion object {
        private const val CONFERENCE_EXTRA = "conference"

        fun navigateTo(context: Activity, conference: LocalConference) {
            context.startActivity<ConferenceInfoActivity>(CONFERENCE_EXTRA to conference)
        }
    }

    private val conference: LocalConference
        get() = intent.getSafeParcelableExtra(CONFERENCE_EXTRA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                ConferenceInfoScreen(
                    conference = conference,
                    onBack = { finish() },
                )
            }
        }
    }
}
