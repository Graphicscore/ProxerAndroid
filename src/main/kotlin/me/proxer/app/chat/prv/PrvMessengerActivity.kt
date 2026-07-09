package me.proxer.app.chat.prv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.WindowCompat
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import me.proxer.app.base.BaseActivity
import me.proxer.app.chat.prv.conference.ConferenceScreen
import me.proxer.app.chat.prv.message.MessengerScreen
import me.proxer.app.chat.prv.sync.MessengerDao
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.intentFor
import me.proxer.app.util.extension.safeInject
import me.proxer.app.util.extension.startActivity
import timber.log.Timber

/**
 * @author Ruben Gees
 */
class PrvMessengerActivity : BaseActivity() {
    companion object {
        private const val CONFERENCE_EXTRA = "conference"

        fun navigateTo(context: Activity, conference: LocalConference, initialMessage: String? = null) {
            context.startActivity<PrvMessengerActivity>(
                CONFERENCE_EXTRA to conference,
                Intent.EXTRA_TEXT to initialMessage,
            )
        }

        fun getIntent(context: Context, conference: LocalConference, initialMessage: String? = null): Intent =
            context.intentFor<PrvMessengerActivity>(
                CONFERENCE_EXTRA to conference,
                Intent.EXTRA_TEXT to initialMessage,
            )

        fun getIntent(context: Context, conferenceId: String, initialMessage: String? = null): Intent =
            context.intentFor<PrvMessengerActivity>(
                ShortcutManagerCompat.EXTRA_SHORTCUT_ID to conferenceId,
                Intent.EXTRA_TEXT to initialMessage,
            )
    }

    private val messengerDao by safeInject<MessengerDao>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val conference = IntentCompat.getParcelableExtra(intent, CONFERENCE_EXTRA, LocalConference::class.java)
        val initialMessage = intent.getStringExtra(Intent.EXTRA_TEXT)

        when {
            conference != null -> setContent {
                ProxerTheme {
                    MessengerScreen(
                        conference = conference,
                        initialMessage = initialMessage,
                        onBack = { finish() },
                    )
                }
            }

            intent.hasExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID) -> {
                val conferenceId = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)?.toLongOrNull()
                if (conferenceId != null) {
                    val foundConference = mutableStateOf<LocalConference?>(null)

                    messengerDao.getConferenceMaybe(conferenceId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .autoDisposable(this.scope())
                        .subscribe(
                            {
                                if (it != null) foundConference.value = it else finish()
                            },
                            {
                                Timber.e(it)
                                finish()
                            },
                        )

                    setContent {
                        ProxerTheme {
                            val conf = foundConference.value
                            if (conf != null) {
                                MessengerScreen(
                                    conference = conf,
                                    initialMessage = initialMessage,
                                    onBack = { finish() },
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                } else {
                    finish()
                }
            }

            else -> setContent {
                ProxerTheme {
                    ConferenceScreen(
                        initialMessage = initialMessage,
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
