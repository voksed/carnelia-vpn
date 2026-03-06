package com.carnelia.vpn

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity


import com.carnelia.vpn.utils.PrefsManager

class PrivateBrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var urlInput: EditText
    private lateinit var btnClear: ImageView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.browser_progress)
        urlInput = findViewById(R.id.address_input)
        btnClear = findViewById(R.id.btn_clear_input)

        setupWebView()
        setupControls()

        // Load custom homepage
        loadUrl("file:///android_asset/home.html")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_NO_CACHE // Private mode

        // Dark Mode support for web content
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.FORCE_DARK)) {
            androidx.webkit.WebSettingsCompat.setForceDark(
                settings,
                androidx.webkit.WebSettingsCompat.FORCE_DARK_ON
            )
        }

        // TOR / I2P Proxy Logic Removed

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                if (!(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://"))) {
                    Toast.makeText(this@PrivateBrowserActivity, "Открытие внешних приложений заблокировано", Toast.LENGTH_SHORT).show()
                    return true
                }
                return false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (!(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://"))) {
                    Toast.makeText(this@PrivateBrowserActivity, "Открытие внешних приложений заблокировано", Toast.LENGTH_SHORT).show()
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                urlInput.setText(url)
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.INVISIBLE
                super.onPageFinished(view, url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = View.INVISIBLE
                } else {
                    progressBar.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupControls() {
        urlInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                loadUrl(v.text.toString())
                // Hide keyboard logic could be here
                true
            } else {
                false
            }
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }

        findViewById<ImageButton>(R.id.btn_forward).setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }

        findViewById<ImageButton>(R.id.btn_reload).setOnClickListener {
            webView.reload()
        }

        // Flame Button - Incinerate History
        findViewById<ImageButton>(R.id.btn_flame).setOnClickListener {
            clearDataAndFinish()
        }
    }

    private fun loadUrl(query: String) {
        var url = query.trim()
        if (url.isEmpty()) return

        // Check if it's a URL or search query
        if (url == "file:///android_asset/home.html") {
            // Allow
        } else if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://")) {
            if (url.contains(".") && !url.contains(" ")) {
                 url = "https://$url"
            } else {
                 // Use SearXNG (Public Instance) as Open Source Search Engine
                 url = "https://searx.be/search?q=$url"
            }
        }
        
        webView.loadUrl(url)
        urlInput.clearFocus()
        
        // Update input text only if not home
        if (!url.contains("file:///android_asset/home.html")) {
            urlInput.setText(url)
        } else {
            urlInput.setText("")
            urlInput.hint = "Search or Enter URL"
        }
    }

    private fun clearDataAndFinish() {
        webView.clearHistory()
        webView.clearCache(true)
        webView.clearFormData()
        
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        
        WebSettings.getDefaultUserAgent(this) // Reset things if needed
        
        Toast.makeText(this, "История уничтожена 🔥", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            clearDataAndFinish()
        }
    }
}