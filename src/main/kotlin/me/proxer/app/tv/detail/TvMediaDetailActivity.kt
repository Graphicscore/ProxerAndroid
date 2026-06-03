package me.proxer.app.tv.detail

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity

class TvMediaDetailActivity : ComponentActivity() {
    companion object {
        fun navigateTo(context: Context, id: String, name: String) {
            // Stub: will be implemented in a later unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
