package me.proxer.app.tv.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import me.proxer.app.tv.TvTheme
import me.proxer.app.tv.detail.TvMediaDetailActivity

class TvSearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        setContent {
            TvTheme {
                TvSearchScreen(
                    onMediaClick = { id, name -> TvMediaDetailActivity.navigateTo(activity, id, name) },
                    onBack = { finish() }
                )
            }
        }
    }
}
