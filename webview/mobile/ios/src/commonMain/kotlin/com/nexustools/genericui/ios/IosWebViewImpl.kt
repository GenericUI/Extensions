package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl

/**
 * iOS `WKWebView` implementation of [WebViewNativeImpl].
 */
class IosWebViewImpl : IosBaseNativeImpl("WKWebView", "webview"), WebViewNativeImpl<IosNativeView> {
    private var currentUrl: String? = null
    private val historyBack = ArrayDeque<String>()
    private val historyForward = ArrayDeque<String>()
    private var onMessageListener: ((String) -> Unit)? = null
    private var onPageFinishedListener: ((String) -> Unit)? = null

    override fun loadUrl(url: String) {
        currentUrl?.let { historyBack.addLast(it) }
        historyForward.clear()
        currentUrl = url
        onPageFinishedListener?.invoke(url)
    }

    override fun getUrl(): String? = currentUrl

    override fun loadHtml(html: String, baseUrl: String?) {
        currentUrl = baseUrl ?: "about:blank"
        onPageFinishedListener?.invoke(currentUrl!!)
    }

    override fun reload() {
        currentUrl?.let { onPageFinishedListener?.invoke(it) }
    }

    override fun goBack() {
        if (historyBack.isNotEmpty()) {
            currentUrl?.let { historyForward.addLast(it) }
            currentUrl = historyBack.removeLast()
        }
    }

    override fun goForward() {
        if (historyForward.isNotEmpty()) {
            currentUrl?.let { historyBack.addLast(it) }
            currentUrl = historyForward.removeLast()
        }
    }

    override fun canGoBack(): Boolean = historyBack.isNotEmpty()
    override fun canGoForward(): Boolean = historyForward.isNotEmpty()

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        callback?.invoke(null)
    }

    override fun postWebMessage(message: String) {
        onMessageListener?.invoke(message)
    }

    override fun setOnWebMessageReceived(listener: ((String) -> Unit)?) { onMessageListener = listener }
    override fun setOnPageFinished(listener: ((String) -> Unit)?) { onPageFinishedListener = listener }
}
