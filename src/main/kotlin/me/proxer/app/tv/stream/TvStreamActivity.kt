package me.proxer.app.tv.stream

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import me.proxer.app.util.extension.startActivity
import me.proxer.library.enums.AnimeLanguage

class TvStreamActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }

    companion object {
        fun navigateTo(context: Context, id: String, episode: Int, language: AnimeLanguage, name: String) {
            context.startActivity<TvStreamActivity>(
                "id" to id, "episode" to episode, "language" to language, "name" to name
            )
        }
    }
}
