package com.infinitehits.bubbleblast.ui

import android.graphics.Color
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.TextView
import com.infinitehits.bubbleblast.R
import com.infinitehits.bubbleblast.audio.AudioManager
import com.infinitehits.bubbleblast.util.Navigator
import com.infinitehits.bubbleblast.util.UiUtils

/**
 * Offline viewer for the bundled privacy policy and terms of service.
 * The documents are plain HTML in `assets/legal/` - no network call is ever made.
 */
class LegalActivity : BaseActivity() {

    override val musicScene: AudioManager.Scene get() = AudioManager.Scene.HOME

    private lateinit var webView: WebView
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal)
        UiUtils.applySystemBarInsets(findViewById(R.id.legal_root))

        val document = intent.getStringExtra(Navigator.EXTRA_LEGAL_DOCUMENT) ?: Navigator.LEGAL_PRIVACY
        findViewById<TextView>(R.id.legal_title).setText(
            if (document == Navigator.LEGAL_TERMS) R.string.legal_title_terms else R.string.legal_title_privacy
        )

        errorText = findViewById(R.id.legal_error_text)
        errorText.setText(R.string.legal_error)

        val back = findViewById<ImageButton>(R.id.legal_back_button)
        UiUtils.attachPressEffect(back)
        back.setOnClickListener {
            playClick()
            finish()
        }

        webView = findViewById(R.id.legal_webview)
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.settings.javaScriptEnabled = false
        webView.settings.allowFileAccess = false
        webView.isVerticalScrollBarEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                UiUtils.setVisible(errorText, false)
            }

            @Deprecated("Required for API 24-25 devices")
            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                showError()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                showError()
            }
        }

        val asset = if (document == Navigator.LEGAL_TERMS) TERMS_FILE else PRIVACY_FILE
        webView.loadUrl("file:///android_asset/legal/$asset")
    }

    /** The legal text ships inside the APK; this only runs if an asset is missing. */
    private fun showError() {
        UiUtils.setVisible(errorText, true)
        UiUtils.setVisible(webView, false)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val PRIVACY_FILE = "privacy_policy.html"
        private const val TERMS_FILE = "terms_and_conditions.html"
    }
}
