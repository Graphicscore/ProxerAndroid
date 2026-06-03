package me.proxer.app.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.MaterialTheme
import me.proxer.app.tv.auth.TvLoginActivity
import me.proxer.app.tv.detail.TvMediaDetailActivity
import me.proxer.app.tv.search.TvSearchActivity
import me.proxer.app.util.extension.startActivity

class TvMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        setContent {
            MaterialTheme {
                TvBrowseScreen(
                    onMediaClick = { id, name -> TvMediaDetailActivity.navigateTo(activity, id, name) },
                    onSearchClick = { activity.startActivity<TvSearchActivity>() },
                    onLoginClick = { activity.startActivity<TvLoginActivity>() }
                )
            }
        }
    }
}
