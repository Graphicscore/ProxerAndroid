package me.proxer.app.forum

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
import me.proxer.app.util.extension.startActivity

/**
 * @author Ruben Gees
 */
class TopicActivity : BaseActivity() {
    companion object {
        private const val ID_EXTRA = "id"
        private const val CATEGORY_ID_EXTRA = "category_id"
        private const val TOPIC_EXTRA = "topic"

        private const val TOUZAI_PATH = "/touzai"
        private const val TOUZAI_CATEGORY = "310"

        fun navigateTo(context: Activity, id: String, categoryId: String, topic: String? = null) {
            context.startActivity<TopicActivity>(
                ID_EXTRA to id,
                CATEGORY_ID_EXTRA to categoryId,
                TOPIC_EXTRA to topic,
            )
        }

        fun getIntent(context: Context, id: String, categoryId: String, topic: String? = null): Intent =
            context.intentFor<TopicActivity>(
                ID_EXTRA to id,
                CATEGORY_ID_EXTRA to categoryId,
                TOPIC_EXTRA to topic,
            )
    }

    val id: String
        get() =
            when (intent.hasExtra(ID_EXTRA)) {
                true -> {
                    intent.getSafeStringExtra(ID_EXTRA)
                }

                false -> {
                    when (intent.data?.path == TOUZAI_PATH) {
                        true -> intent.data?.getQueryParameter("id") ?: "-1"
                        else -> intent.data?.pathSegments?.getOrNull(2) ?: "-1"
                    }
                }
            }

    val categoryId: String
        get() =
            when (intent.hasExtra(CATEGORY_ID_EXTRA)) {
                true -> {
                    intent.getSafeStringExtra(CATEGORY_ID_EXTRA)
                }

                false -> {
                    when (intent.data?.path == TOUZAI_PATH) {
                        true -> TOUZAI_CATEGORY
                        else -> intent.data?.pathSegments?.getOrNull(1) ?: "-1"
                    }
                }
            }

    val topic: String?
        get() = intent.getStringExtra(TOPIC_EXTRA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                TopicScreen(
                    id = id,
                    categoryId = categoryId,
                    subject = topic,
                    onBack = { finish() },
                )
            }
        }
    }
}
