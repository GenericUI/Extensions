package com.nexustools.genericui.web

import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl

/**
 * Web `<iframe>` (`HTMLIFrameElement`) implementation of [WebViewNativeImpl].
 */
class WebWebViewImpl : WebBaseNativeImpl("iframe", "webview-iframe"), WebViewNativeImpl<WebDomElement> {
    private var currentUrl: String? = null
    private val historyBack = ArrayDeque<String>()
    private val historyForward = ArrayDeque<String>()
    private var onMessageListener: ((String) -> Unit)? = null
    private var onPageFinishedListener: ((String) -> Unit)? = null

    override fun loadUrl(url: String) {
        currentUrl?.let { historyBack.addLast(it) }
        historyForward.clear()
        currentUrl = url
        nativeWidget.setAttribute("src", url)
        onPageFinishedListener?.invoke(url)
    }

    override fun getUrl(): String? = currentUrl

    override fun loadHtml(html: String, baseUrl: String?) {
        currentUrl = baseUrl ?: "about:srcdoc"
        nativeWidget.setAttribute("srcdoc", html)
        onPageFinishedListener?.invoke(currentUrl!!)
    }

    override fun reload() {
        currentUrl?.let { onPageFinishedListener?.invoke(it) }
    }

    override fun goBack() {
        if (historyBack.isNotEmpty()) {
            currentUrl?.let { historyForward.addLast(it) }
            val prev = historyBack.removeLast()
            currentUrl = prev
            nativeWidget.setAttribute("src", prev)
        }
    }

    override fun goForward() {
        if (historyForward.isNotEmpty()) {
            currentUrl?.let { historyBack.addLast(it) }
            val next = historyForward.removeLast()
            currentUrl = next
            nativeWidget.setAttribute("src", next)
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

    override fun setOnWebMessageReceived(listener: ((String) -> Unit)?) {
        onMessageListener = listener
    }

    override fun setOnPageFinished(listener: ((String) -> Unit)?) {
        onPageFinishedListener = listener
    }
}
