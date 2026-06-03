package me.proxer.app.tv.episode

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import me.proxer.app.util.extension.startActivity

class TvEpisodeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
    companion object {
        fun navigateTo(context: Context, id: String, name: String, episodeAmount: Int) {
            context.startActivity<TvEpisodeActivity>("id" to id, "name" to name, "episode_amount" to episodeAmount)
        }
    }
}
