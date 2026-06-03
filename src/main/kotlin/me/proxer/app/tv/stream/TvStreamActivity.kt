package me.proxer.app.tv.stream

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.MaterialTheme
import me.proxer.app.tv.auth.TvLoginActivity
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.startActivity
import me.proxer.library.enums.AnimeLanguage

class TvStreamActivity : ComponentActivity() {

    private val entryId: String get() = intent.getSafeStringExtra(ID_EXTRA)
    private val episode: Int get() = intent.getIntExtra(EPISODE_EXTRA, 1)
    @Suppress("DEPRECATION")
    private val language: AnimeLanguage get() =
        (intent.getSerializableExtra(LANGUAGE_EXTRA) as? AnimeLanguage) ?: AnimeLanguage.ENGLISH_SUB
    private val entryName: String get() = intent.getStringExtra(NAME_EXTRA) ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        val id = entryId
        val ep = episode
        val lang = language
        val name = entryName
        setContent {
            MaterialTheme {
                TvStreamScreen(
                    entryId = id,
                    episode = ep,
                    language = lang,
                    entryName = name,
                    onLoginClick = { activity.startActivity<TvLoginActivity>() },
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        private const val ID_EXTRA = "id"
        private const val EPISODE_EXTRA = "episode"
        private const val LANGUAGE_EXTRA = "language"
        private const val NAME_EXTRA = "name"

        fun navigateTo(context: Context, id: String, episode: Int, language: AnimeLanguage, name: String) {
            context.startActivity<TvStreamActivity>(
                ID_EXTRA to id,
                EPISODE_EXTRA to episode,
                LANGUAGE_EXTRA to language,
                NAME_EXTRA to name
            )
        }
    }
}
