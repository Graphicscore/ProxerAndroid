package me.proxer.app.media.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.proxer.library.enums.Category

@Composable
fun MediaListScreen(category: Category, onOpenDrawer: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("${category.name.lowercase().replaceFirstChar { it.uppercaseChar() }} — TODO")
    }
}
