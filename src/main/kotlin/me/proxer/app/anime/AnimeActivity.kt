package me.proxer.app.anime

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.startActivity
import me.proxer.library.enums.AnimeLanguage
import me.proxer.library.util.ProxerUtils

/**
 * @author Ruben Gees
 */
class AnimeActivity : BaseActivity() {
    companion object {
        private const val ID_EXTRA = "id"
        private const val EPISODE_EXTRA = "episode"
        private const val LANGUAGE_EXTRA = "language"
        private const val NAME_EXTRA = "name"
        private const val EPISODE_AMOUNT_EXTRA = "episode_amount"

        fun navigateTo(
            context: Activity,
            id: String,
            episode: Int,
            language: AnimeLanguage,
            name: String? = null,
            episodeAmount: Int? = null,
        ) {
            context.startActivity<AnimeActivity>(
                ID_EXTRA to id,
                EPISODE_EXTRA to episode,
                LANGUAGE_EXTRA to language,
                NAME_EXTRA to name,
                EPISODE_AMOUNT_EXTRA to episodeAmount,
            )
        }
    }

    private val id: String
        get() =
            when (intent.hasExtra(ID_EXTRA)) {
                true -> intent.getSafeStringExtra(ID_EXTRA)
                false -> intent?.data?.pathSegments?.getOrNull(1) ?: "-1"
            }

    private val initialEpisode: Int
        get() =
            when (intent.hasExtra(EPISODE_EXTRA)) {
                true -> intent.getIntExtra(EPISODE_EXTRA, 1)

                false ->
                    intent
                        ?.data
                        ?.pathSegments
                        ?.getOrNull(2)
                        ?.toIntOrNull() ?: 1
            }

    private val language: AnimeLanguage
        get() =
            when (intent.hasExtra(LANGUAGE_EXTRA)) {
                true ->
                    requireNotNull(
                        IntentCompat.getSerializableExtra(intent, LANGUAGE_EXTRA, AnimeLanguage::class.java),
                    ) {
                        "LANGUAGE_EXTRA present but deserialized to null in AnimeActivity"
                    }

                false ->
                    intent
                        ?.data
                        ?.pathSegments
                        ?.getOrNull(3)
                        ?.let { ProxerUtils.toApiEnum<AnimeLanguage>(it) }
                        ?: AnimeLanguage.ENGLISH_SUB
            }

    private val initialName: String?
        get() = intent.getStringExtra(NAME_EXTRA)

    private val initialEpisodeAmount: Int?
        get() =
            when (intent.hasExtra(EPISODE_AMOUNT_EXTRA)) {
                true -> intent.getIntExtra(EPISODE_AMOUNT_EXTRA, 1)
                false -> null
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ProxerTheme {
                AnimeScreen(
                    id = id,
                    initialEpisode = initialEpisode,
                    language = language,
                    initialName = initialName,
                    initialEpisodeAmount = initialEpisodeAmount,
                    onBack = { finish() },
                )
            }
        }
    }
}
