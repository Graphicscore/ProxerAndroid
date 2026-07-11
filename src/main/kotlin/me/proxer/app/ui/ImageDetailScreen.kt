package me.proxer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import me.proxer.app.ui.compose.ProxerTheme
import timber.log.Timber

@Composable
fun ImageDetailScreen(url: String, onClose: () -> Unit) {
    var hasError by remember { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x7F000000))
            .systemBarsPadding()
            .clickable { onClose() },
    ) {
        if (hasError) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(64.dp)
                        .clickable {
                            hasError = false
                            retryCount++
                        },
                )
            }
        } else if (LocalInspectionMode.current) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
        } else {
            key(retryCount) {
                AndroidView(
                    factory = { ctx ->
                        SubsamplingScaleImageView(ctx).apply {
                            setMinimumTileDpi(160)

                            setOnImageEventListener(
                                object : SubsamplingScaleImageView.OnImageEventListener {
                                    override fun onReady() = Unit
                                    override fun onImageLoaded() = Unit

                                    override fun onPreviewLoadError(e: Exception) {
                                        Timber.e(e, "Failed to load image preview for $url")
                                    }

                                    override fun onImageLoadError(e: Exception) {
                                        Timber.e(e, "Failed to load image for $url")
                                        hasError = true
                                    }

                                    override fun onTileLoadError(e: Exception) {
                                        Timber.e(e, "Failed to load image tile for $url")
                                    }

                                    override fun onPreviewReleased() = Unit
                                },
                            )

                            setImage(ImageSource.uri(url))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
