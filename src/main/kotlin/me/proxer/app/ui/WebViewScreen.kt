package me.proxer.app.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            loadUrl(url)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(url) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (webView.canGoBack()) webView.goBack() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        AndroidView(
            factory = { _ -> webView },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }
}
