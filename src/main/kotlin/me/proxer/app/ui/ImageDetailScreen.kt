package me.proxer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import me.proxer.app.ui.compose.ProxerTheme

@Composable
fun ImageDetailScreen(url: String, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x7F000000))
            .systemBarsPadding()
            .clickable { onClose() },
    ) {
        if (LocalInspectionMode.current) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
        } else {
            AndroidView(
                factory = { ctx ->
                    SubsamplingScaleImageView(ctx).apply {
                        setMinimumTileDpi(160)
                        setImage(ImageSource.uri(url))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ImageDetailScreenPreview() {
    ProxerTheme {
        ImageDetailScreen(url = "https://example.com/image.jpg", onClose = {})
    }
}
