package me.proxer.app.tv.episode

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import me.proxer.app.tv.TvTheme
import me.proxer.app.tv.stream.TvStreamActivity
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.startActivity
import me.proxer.library.enums.AnimeLanguage

class TvEpisodeActivity : ComponentActivity() {

    private val entryId: String get() = intent.getSafeStringExtra(ID_EXTRA)
    private val entryName: String get() = intent.getStringExtra(NAME_EXTRA) ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        val id = entryId
        val name = entryName
        setContent {
            TvTheme {
                TvEpisodeScreen(
                    entryId = id,
                    entryName = name,
                    onEpisodeClick = { episode, language ->
                        TvStreamActivity.navigateTo(activity, id, episode, language, name)
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        private const val ID_EXTRA = "id"
        private const val NAME_EXTRA = "name"
        private const val EPISODE_AMOUNT_EXTRA = "episode_amount"

        fun navigateTo(context: Context, id: String, name: String, episodeAmount: Int) {
            context.startActivity<TvEpisodeActivity>(
                ID_EXTRA to id, NAME_EXTRA to name, EPISODE_AMOUNT_EXTRA to episodeAmount
            )
        }
    }
}
