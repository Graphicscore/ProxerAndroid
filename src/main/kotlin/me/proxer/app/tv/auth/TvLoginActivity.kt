package me.proxer.app.tv.auth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import me.proxer.app.tv.TvTheme

class TvLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvTheme {
                TvLoginScreen(onLoginSuccess = { finish() })
            }
        }
    }
}
