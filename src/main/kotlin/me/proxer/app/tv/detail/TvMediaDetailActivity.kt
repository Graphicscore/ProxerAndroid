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
        fun navigateTo(context: Context, id: String, name: String) {
            context.startActivity<TvMediaDetailActivity>("id" to id, "name" to name)
        }
    }
}
