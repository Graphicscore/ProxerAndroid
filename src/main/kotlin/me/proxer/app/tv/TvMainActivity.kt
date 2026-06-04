package me.proxer.app.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import me.proxer.app.tv.detail.TvMediaDetailActivity
import me.proxer.app.tv.search.TvSearchActivity
import me.proxer.app.util.extension.startActivity

class TvMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        setContent {
            TvTheme {
                TvAppShell(
                    onMediaClick = { id, name -> TvMediaDetailActivity.navigateTo(activity, id, name) },
                    onSearchClick = { activity.startActivity<TvSearchActivity>() },
                )
            }
        }
    }
}
