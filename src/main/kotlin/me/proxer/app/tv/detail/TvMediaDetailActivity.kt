package me.proxer.app.tv.detail

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import me.proxer.app.util.extension.startActivity

class TvMediaDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
    companion object {
        private const val ID_EXTRA = "id"
        private const val NAME_EXTRA = "name"
        fun navigateTo(context: Context, id: String, name: String) {
            context.startActivity<TvMediaDetailActivity>(ID_EXTRA to id, NAME_EXTRA to name)
        }
    }
}
