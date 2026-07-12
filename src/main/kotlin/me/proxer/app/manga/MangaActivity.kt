package me.proxer.app.manga

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.startActivity
import me.proxer.library.enums.Language
import me.proxer.library.util.ProxerUtils

/**
 * @author Ruben Gees
 */
class MangaActivity : BaseActivity() {
    companion object {
        private const val ID_EXTRA = "id"
        private const val EPISODE_EXTRA = "episode"
        private const val LANGUAGE_EXTRA = "language"
        private const val CHAPTER_TITLE_EXTRA = "chapter_title"
        private const val NAME_EXTRA = "name"
        private const val EPISODE_AMOUNT_EXTRA = "episode_amount"

        fun navigateTo(
            context: Activity,
            id: String,
            episode: Int,
            language: Language,
            chapterTitle: String?,
            name: String? = null,
            episodeAmount: Int? = null,
        ) {
            context.startActivity<MangaActivity>(
                ID_EXTRA to id,
                EPISODE_EXTRA to episode,
                LANGUAGE_EXTRA to language,
                CHAPTER_TITLE_EXTRA to chapterTitle,
                NAME_EXTRA to name,
                EPISODE_AMOUNT_EXTRA to episodeAmount,
            )
        }
    }

    private val id: String
        get() =
            when (intent.hasExtra(ID_EXTRA)) {
                true -> intent.getSafeStringExtra(ID_EXTRA)
                false -> intent.data?.pathSegments?.getOrNull(1) ?: "-1"
            }

    private val episode: Int
        get() =
            when (intent.hasExtra(EPISODE_EXTRA)) {
                true -> intent.getIntExtra(EPISODE_EXTRA, 1)

                false ->
                    intent.data
                        ?.pathSegments
                        ?.getOrNull(2)
                        ?.toIntOrNull() ?: 1
            }

    private val language: Language
        get() =
            when (intent.hasExtra(LANGUAGE_EXTRA)) {
                true -> {
                    requireNotNull(IntentCompat.getSerializableExtra(intent, LANGUAGE_EXTRA, Language::class.java)) {
                        "LANGUAGE_EXTRA present but deserialized to null in MangaActivity"
                    }
                }

                false -> {
                    intent.data
                        ?.pathSegments
                        ?.getOrNull(3)
                        ?.let { ProxerUtils.toApiEnum<Language>(it) }
                        ?: Language.ENGLISH
                }
            }

    private val chapterTitle: String?
        get() = intent.getStringExtra(CHAPTER_TITLE_EXTRA)

    private val name: String?
        get() = intent.getStringExtra(NAME_EXTRA)

    private val episodeAmount: Int?
        get() =
            when (intent.hasExtra(EPISODE_AMOUNT_EXTRA)) {
                true -> intent.getIntExtra(EPISODE_AMOUNT_EXTRA, 1)
                else -> null
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ProxerTheme {
                MangaScreen(
                    id = id,
                    initialEpisode = episode,
                    language = language,
                    initialName = name,
                    initialChapterTitle = chapterTitle,
                    initialEpisodeAmount = episodeAmount,
                    onBack = { finish() },
                )
            }
        }
    }
}
