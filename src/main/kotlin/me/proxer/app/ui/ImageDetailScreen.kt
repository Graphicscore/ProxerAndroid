package me.proxer.app.ui

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import me.proxer.app.util.extension.logErrors

@Composable
fun ImageDetailScreen(url: String, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x7F000000))
            .systemBarsPadding()
            .clickable { onClose() },
    ) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    Glide.with(ctx)
                        .load(url)
                        .logErrors()
                        .into(this)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
