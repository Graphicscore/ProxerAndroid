package me.proxer.app.info.translatorgroup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.intentFor

/**
 * @author Ruben Gees
 */
class TranslatorGroupActivity : BaseActivity() {
    companion object {
        private const val ID_EXTRA = "id"
        private const val NAME_EXTRA = "name"

        fun navigateTo(context: Activity, id: String, name: String? = null) {
            context.startActivity(getIntent(context, id, name))
        }

        fun getIntent(context: Context, id: String, name: String? = null): Intent =
            context.intentFor<TranslatorGroupActivity>(
                ID_EXTRA to id,
                NAME_EXTRA to name,
            )
    }

    val id: String
        get() = intent.getSafeStringExtra(ID_EXTRA)

    val name: String?
        get() = intent.getStringExtra(NAME_EXTRA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                TranslatorGroupScreen(
                    id = id,
                    initialName = name,
                    onBack = { finish() },
                )
            }
        }
    }
}
