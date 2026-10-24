package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl

/**
 * Android `android.webkit.WebView` (`addJavascriptInterface` / `evaluateJavascript`) implementation of [WebViewNativeImpl].
 */
class AndroidWebViewImpl : AndroidBaseNativeImpl("android.webkit.WebView", "webview"), WebViewNativeImpl<AndroidViewHandle> {
    private var currentUrl: String? = null
    private val historyBack = ArrayDeque<String>()
    private val historyForward = ArrayDeque<String>()
    private var onWebMessageListener: ((String) -> Unit)? = null
    private var onNavigationListener: ((String) -> Unit)? = null

    override fun loadUrl(url: String) {
        currentUrl?.let { historyBack.addLast(it) }
        historyForward.clear()
        currentUrl = url
        nativeWidget.attributes["src"] = url
        onNavigationListener?.invoke(url)
    }

    override fun loadHtml(html: String, baseUrl: String?) {
        currentUrl = baseUrl ?: "about:srcdoc"
        nativeWidget.text = html
        onNavigationListener?.invoke(currentUrl!!)
    }

    override fun getUrl(): String? = currentUrl

    override fun reload() {
        currentUrl?.let { onNavigationListener?.invoke(it) }
    }

    override fun canGoBack(): Boolean = historyBack.isNotEmpty()
    override fun canGoForward(): Boolean = historyForward.isNotEmpty()

    override fun goBack() {
        if (historyBack.isNotEmpty()) {
            currentUrl?.let { historyForward.addFirst(it) }
            currentUrl = historyBack.removeLast()
            currentUrl?.let { onNavigationListener?.invoke(it) }
        }
    }

    override fun goForward() {
        if (historyForward.isNotEmpty()) {
            currentUrl?.let { historyBack.addLast(it) }
            currentUrl = historyForward.removeFirst()
            currentUrl?.let { onNavigationListener?.invoke(it) }
        }
    }

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        callback?.invoke("undefined")
    }

    override fun postWebMessage(message: String) {
        onWebMessageListener?.invoke(message)
    }

    override fun setOnWebMessageReceived(listener: ((String) -> Unit)?) {
        onWebMessageListener = listener
    }

    override fun setOnPageFinished(listener: ((String) -> Unit)?) {
        onNavigationListener = listener
    }
}
