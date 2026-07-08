package me.proxer.app.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.ActivityUtils
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.intentFor
import me.proxer.library.enums.Category

/**
 * @author Ruben Gees
 */
class MediaActivity : BaseActivity() {
    companion object {
        private const val ID_EXTRA = "id"
        private const val NAME_EXTRA = "name"
        private const val CATEGORY_EXTRA = "category"

        fun navigateTo(
            context: Activity,
            id: String,
            name: String? = null,
            category: Category? = null,
            imageView: ImageView? = null,
        ) {
            getIntent(context, id, name, category).let {
                ActivityUtils.navigateToWithImageTransition(it, context, imageView)
            }
        }

        fun getIntent(context: Context, id: String, name: String? = null, category: Category? = null): Intent =
            context.intentFor<MediaActivity>(
                ID_EXTRA to id,
                NAME_EXTRA to name,
                CATEGORY_EXTRA to category,
            )
    }

    val id: String
        get() =
            when (intent.hasExtra(ID_EXTRA)) {
                true -> intent.getSafeStringExtra(ID_EXTRA)
                false -> intent.data?.pathSegments?.getOrNull(1) ?: "-1"
            }

    val name: String?
        get() = intent.getStringExtra(NAME_EXTRA)

    val category: Category?
        get() = IntentCompat.getSerializableExtra(intent, CATEGORY_EXTRA, Category::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                MediaScreen(
                    id = id,
                    name = name ?: "",
                    onBack = { finish() },
                )
            }
        }
    }
}
