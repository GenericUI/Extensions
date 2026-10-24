package com.nexustools.genericui.ext.webview.web

import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl
import com.nexustools.genericui.web.WebBaseNativeImpl
import com.nexustools.genericui.web.WebDomElement

/**
 * Web `WebViewNativeImpl` backed by a native HTML5 `<iframe>` (`HTMLIFrameElement`).
 *
 * **Note**: Per architecture rules, `:web` uses the browser's native `<iframe>` element
 * and **NEVER** uses `NJS`.
 */
class WebNativeIframeWebViewImpl(
    val config: WebViewConfig = WebViewConfig()
) : WebBaseNativeImpl("iframe", "webview-iframe"), WebViewNativeImpl<WebDomElement> {

    private var currentUrl: String? = null
    private val history = ArrayList<String>()
    private var historyIndex: Int = -1
    private var messageListener: ((String) -> Unit)? = null
    private var pageFinishedListener: ((String) -> Unit)? = null

    override fun loadUrl(url: String) {
        currentUrl = url
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(url)
        historyIndex = history.size - 1
        nativeWidget.setAttribute("src", url)
        pageFinishedListener?.invoke(url)
    }

    override fun getUrl(): String? = currentUrl

    override fun loadHtml(html: String, baseUrl: String?) {
        currentUrl = baseUrl ?: "about:srcdoc"
        nativeWidget.setAttribute("srcdoc", html)
        pageFinishedListener?.invoke(currentUrl!!)
    }

    override fun reload() {
        currentUrl?.let { pageFinishedListener?.invoke(it) }
    }

    override fun goBack() {
        if (canGoBack()) {
            historyIndex--
            currentUrl = history[historyIndex]
        }
    }

    override fun goForward() {
        if (canGoForward()) {
            historyIndex++
            currentUrl = history[historyIndex]
        }
    }

    override fun canGoBack(): Boolean = historyIndex > 0

    override fun canGoForward(): Boolean = historyIndex >= 0 && historyIndex < history.size - 1

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        if (!config.enableJavaScript) {
            callback?.invoke(null)
            return
        }
        callback?.invoke("true")
    }

    override fun postWebMessage(message: String) {
        messageListener?.invoke(message)
    }

    override fun setOnWebMessageReceived(listener: ((String) -> Unit)?) {
        messageListener = listener
    }

    override fun setOnPageFinished(listener: ((String) -> Unit)?) {
        pageFinishedListener = listener
    }
}
