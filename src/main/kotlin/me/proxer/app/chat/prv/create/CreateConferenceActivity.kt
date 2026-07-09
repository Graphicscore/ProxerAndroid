package me.proxer.app.chat.prv.create

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.chat.prv.Participant
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.intentFor
import me.proxer.app.util.extension.startActivity

/**
 * @author Ruben Gees
 */
class CreateConferenceActivity : BaseActivity() {
    companion object {
        private const val IS_GROUP_EXTRA = "is_group"
        private const val INITIAL_PARTICIPANT_EXTRA = "initial_participant"

        fun navigateTo(context: Activity, isGroup: Boolean = false, initialParticipant: Participant? = null) {
            context.startActivity<CreateConferenceActivity>(
                IS_GROUP_EXTRA to isGroup,
                INITIAL_PARTICIPANT_EXTRA to initialParticipant,
            )
        }

        fun getIntent(context: Activity, isGroup: Boolean = false, initialParticipant: Participant? = null): Intent =
            context.intentFor<CreateConferenceActivity>(
                IS_GROUP_EXTRA to isGroup,
                INITIAL_PARTICIPANT_EXTRA to initialParticipant,
            )
    }

    private val isGroup: Boolean
        get() = intent.getBooleanExtra(IS_GROUP_EXTRA, false)

    private val initialParticipant: Participant?
        get() = IntentCompat.getParcelableExtra(intent, INITIAL_PARTICIPANT_EXTRA, Participant::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                CreateConferenceScreen(
                    isGroup = isGroup,
                    initialParticipant = initialParticipant,
                    onBack = { finish() },
                )
            }
        }
    }
}
