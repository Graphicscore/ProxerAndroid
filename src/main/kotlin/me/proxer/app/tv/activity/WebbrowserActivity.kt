package me.proxer.app.tv.activity

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import kotterknife.bindView
import me.proxer.app.R

/**
 * @author Graphicscore (Dominik Louven)
 */
class WebbrowserActivity : Activity(){
    companion object {
        public const val EXTRA_URL = "url"
    }

    val webView by bindView<WebView>(R.id.webview)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tv_activity_web)

        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; U; Android 4.4.2; en-us; SCH-I535 Build/KOT49H) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30"
        if(intent.hasExtra(EXTRA_URL)){
            webView.loadUrl(intent.getParcelableExtra<Uri>(EXTRA_URL)!!.toString())
        } else {
            finish()
        }
    }
}
