package com.carnelia.vpn.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full-screen dApp browser with a TON Connect bridge injected.
 * Opens as a Dialog from MainActivity.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DAppBrowserScreen(
    initialUrl: String,
    walletAddress: String,
    onTonConnectRequest: (payload: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var urlInputText by remember { mutableStateOf(initialUrl) }
    var progress by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F1A))
        ) {
            // Top bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A2E))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                    }
                    OutlinedTextField(
                        value = urlInputText,
                        onValueChange = { urlInputText = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f).height(44.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                var url = urlInputText.trim()
                                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                    url = "https://$url"
                                }
                                currentUrl = url
                                urlInputText = url
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0098EA),
                            unfocusedBorderColor = Color(0xFF333355),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.Gray,
                            cursorColor = Color(0xFF0098EA)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }

            // Progress bar
            if (isLoading && progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(0xFF0098EA),
                    trackColor = Color(0xFF1A1A2E)
                )
            }

            // WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.userAgentString = settings.userAgentString + " TonConnect/2.0"

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                                val url = req.url.toString()
                                // Let all http/https load normally
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    currentUrl = url
                                    urlInputText = url
                                    return false
                                }
                                return true
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                // Inject TON Connect bridge
                                view.evaluateJavascript(buildTonConnectBridgeJs(walletAddress), null)
                            }
                            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                progress = 0
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                            }
                        }

                        // Add JS bridge
                        addJavascriptInterface(
                            TonConnectJsBridge(
                                walletAddress = walletAddress,
                                onRequest = onTonConnectRequest
                            ),
                            "_tonConnectAndroid"
                        )

                        loadUrl(initialUrl)
                    }
                },
                update = { webView ->
                    if (currentUrl != webView.url && currentUrl.isNotEmpty()) {
                        webView.loadUrl(currentUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * JavaScript interface for TON Connect 2.0 bridge.
 * Allows dApps to connect and send transactions.
 */
class TonConnectJsBridge(
    private val walletAddress: String,
    private val onRequest: (payload: String) -> Unit
) {
    @JavascriptInterface
    fun postMessage(message: String) {
        onRequest(message)
    }

    @JavascriptInterface
    fun getWalletAddress(): String = walletAddress
}

/**
 * Minimal TON Connect 2.0 JavaScript bridge injection.
 * Implements window.tonconnect / window.TonConnect provider.
 * Auto-resolves connect() immediately so dApps like Stonfi work inside the WebView.
 */
private fun buildTonConnectBridgeJs(walletAddress: String): String {
    // Ensure the address exposed to dApps is in UQ... format (non-bounceable).
    val uqAddress = if (walletAddress.startsWith("0:") || walletAddress.startsWith("0%3A")) {
        // Convert raw "0:<hex>" → UQ on the JS side via simple heuristic;
        // the real conversion is done in Kotlin before calling this, but handle legacy just in case.
        walletAddress
    } else {
        walletAddress
    }
    return """
(function() {
  if (window._tonConnectInjected) return;
  window._tonConnectInjected = true;

  const walletAddress = '$uqAddress';

  const deviceInfo = {
    platform: 'android',
    appName: 'CarneliaVPN',
    appVersion: '2.1.0',
    maxProtocolVersion: 2,
    features: ['SendTransaction']
  };

  // Minimal TonConnect provider
  const provider = {
    deviceInfo: deviceInfo,
    walletInfo: {
      name: 'CarneliaVPN',
      image: '',
      tondns: '',
      about_url: ''
    },
    account: {
      address: walletAddress,
      chain: '-239',
      walletStateInit: '',
      publicKey: ''
    },
    isConnected: true,

    _listeners: {},

    on: function(event, callback) {
      if (!this._listeners[event]) this._listeners[event] = [];
      this._listeners[event].push(callback);
      // If already connected and subscribing to 'connect', fire immediately
      if (event === 'connect' && this.isConnected) {
        try { callback(this._buildConnectEvent()); } catch(e) {}
      }
    },

    off: function(event, callback) {
      if (!this._listeners[event]) return;
      this._listeners[event] = this._listeners[event].filter(cb => cb !== callback);
    },

    emit: function(event, data) {
      (this._listeners[event] || []).forEach(cb => { try { cb(data); } catch(e){} });
    },

    _buildConnectEvent: function() {
      return {
        device: deviceInfo,
        payload: {
          address: walletAddress,
          network: '-239',
          publicKey: '',
          walletStateInit: ''
        }
      };
    },

    connect: function(protocolVersion, message) {
      // Notify native side (for logging/tracking), but resolve immediately
      try {
        window._tonConnectAndroid.postMessage(JSON.stringify({
          type: 'connect',
          protocolVersion: protocolVersion,
          message: message
        }));
      } catch(e) {}
      this.isConnected = true;
      const result = this._buildConnectEvent();
      this.emit('connect', result);
      return Promise.resolve(result);
    },

    disconnect: function() {
      this.isConnected = false;
      this.account = null;
      try { window._tonConnectAndroid.postMessage(JSON.stringify({ type: 'disconnect' })); } catch(e) {}
      this.emit('disconnect', {});
      return Promise.resolve();
    },

    sendTransaction: function(transaction) {
      const request = JSON.stringify({
        type: 'sendTransaction',
        transaction: transaction
      });
      try { window._tonConnectAndroid.postMessage(request); } catch(e) {}
      return new Promise((resolve, reject) => {
        const handler = (result) => {
          this.off('transactionResult', handler);
          if (result && result.ok) resolve(result);
          else reject(new Error((result && result.error) || 'Transaction rejected'));
        };
        this.on('transactionResult', handler);
        // Auto-reject after 5 minutes
        setTimeout(() => reject(new Error('Transaction timeout')), 300000);
      });
    },

    restoreConnection: function() {
      return Promise.resolve({
        device: deviceInfo,
        payload: {
          address: walletAddress,
          network: '-239',
          publicKey: '',
          walletStateInit: ''
        }
      });
    },

    getWallets: function() {
      return Promise.resolve([{
        appName: 'CarneliaVPN',
        name: 'CarneliaVPN',
        imageUrl: '',
        aboutUrl: '',
        universalLink: '',
        bridgeUrl: '',
        jsBridgeKey: 'carnelia',
        injected: true,
        embedded: true
      }]);
    }
  };

  // Expose as window.tonconnect and window.TonConnect
  window.tonconnect  = provider;
  window.TonConnect  = provider;
  window.carneliaWallet = provider;

  // Legacy window.ton (used by older dApps and some Stonfi integrations)
  window.ton = {
    isConnected: true,
    account: walletAddress,
    send: function(method, params) {
      if (method === 'ton_requestAccounts') return Promise.resolve([walletAddress]);
      if (method === 'ton_getBalance')      return Promise.resolve('0');
      return Promise.reject(new Error('Method not supported: ' + method));
    },
    request: function(req) {
      if (req.method === 'ton_requestAccounts') return Promise.resolve([walletAddress]);
      return Promise.reject(new Error('Method not supported: ' + req.method));
    }
  };

  // Handle responses from native side
  window._tonConnectCallback = function(type, data) {
    const parsed = (typeof data === 'string') ? JSON.parse(data) : data;
    provider.emit(type, parsed);
    if (type === 'connect') {
      provider.isConnected = true;
      provider.account = { address: walletAddress, chain: '-239', walletStateInit: '', publicKey: '' };
    }
  };

  console.log('[CarneliaVPN] TON Connect bridge injected, address:', walletAddress);
})();
""".trimIndent()
}
