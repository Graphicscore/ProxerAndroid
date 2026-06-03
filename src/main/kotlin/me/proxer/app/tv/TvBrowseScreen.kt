package me.proxer.app.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text

@Composable
fun TvBrowseScreen(
    onMediaClick: (id: String, name: String) -> Unit,
    onSearchClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("ProxerTV — Loading...")
    }
}
