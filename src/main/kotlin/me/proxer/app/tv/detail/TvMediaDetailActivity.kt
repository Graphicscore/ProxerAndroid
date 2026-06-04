package me.proxer.app.tv.detail

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import me.proxer.app.tv.TvTheme
import me.proxer.app.tv.episode.TvEpisodeActivity
import me.proxer.app.util.extension.getSafeStringExtra
import me.proxer.app.util.extension.startActivity

class TvMediaDetailActivity : ComponentActivity() {

    private val entryId: String get() = intent.getSafeStringExtra(ID_EXTRA)
    private val entryName: String get() = intent.getStringExtra(NAME_EXTRA) ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        val id = entryId
        val name = entryName
        setContent {
            TvTheme {
                TvMediaDetailScreen(
                    entryId = id,
                    entryName = name,
                    onWatchEpisodes = { episodeAmount ->
                        TvEpisodeActivity.navigateTo(activity, id, name, episodeAmount)
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        private const val ID_EXTRA = "id"
        private const val NAME_EXTRA = "name"

        fun navigateTo(context: Context, id: String, name: String) {
            context.startActivity<TvMediaDetailActivity>(ID_EXTRA to id, NAME_EXTRA to name)
        }
    }
}
