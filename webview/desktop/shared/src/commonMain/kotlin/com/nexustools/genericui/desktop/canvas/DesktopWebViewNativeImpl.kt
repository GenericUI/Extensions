package com.nexustools.genericui.desktop.canvas

import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl

/**
 * Shared desktop base implementation of [WebViewNativeImpl] for `ICoreWebView2` (`:windows`),
 * `WKWebView` (`:macos`), `WebKitWebView` (`:gtk`), and `QWebEngineView` (`:qt`).
 */
open class DesktopWebViewNativeImpl<out W : DesktopNativeWidget>(
    nativeWidget: W,
    val engineName: String = "DesktopWebView"
) : DesktopBaseNativeImpl<W>(nativeWidget, "webview"), WebViewNativeImpl<W> {

    protected var currentUrl: String? = null
    protected val historyStack: MutableList<String> = mutableListOf()
    protected var historyIndex: Int = -1
    protected var onWebMessageReceivedListener: ((String) -> Unit)? = null
    protected var onPageFinishedListener: ((String) -> Unit)? = null

    override fun loadUrl(url: String) {
        currentUrl = url
        if (historyIndex < historyStack.size - 1) {
            historyStack.subList(historyIndex + 1, historyStack.size).clear()
        }
        historyStack.add(url)
        historyIndex = historyStack.lastIndex
        nativeWidget.attributes["url"] = url
        onPageFinishedListener?.invoke(url)
    }

    override fun getUrl(): String? = currentUrl

    override fun loadHtml(html: String, baseUrl: String?) {
        currentUrl = baseUrl ?: "about:blank"
        nativeWidget.attributes["html"] = html
        onPageFinishedListener?.invoke(currentUrl!!)
    }

    override fun reload() {
        currentUrl?.let { onPageFinishedListener?.invoke(it) }
    }

    override fun goBack() {
        if (canGoBack()) {
            historyIndex--
            currentUrl = historyStack[historyIndex]
            onPageFinishedListener?.invoke(currentUrl!!)
        }
    }

    override fun goForward() {
        if (canGoForward()) {
            historyIndex++
            currentUrl = historyStack[historyIndex]
            onPageFinishedListener?.invoke(currentUrl!!)
        }
    }

    override fun canGoBack(): Boolean = historyIndex > 0

    override fun canGoForward(): Boolean = historyIndex >= 0 && historyIndex < historyStack.size - 1

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        nativeWidget.attributes["lastScript"] = script
        callback?.invoke("null")
    }

    override fun postWebMessage(message: String) {
        onWebMessageReceivedListener?.invoke(message)
    }

    override fun setOnWebMessageReceived(listener: ((String) -> Unit)?) {
        onWebMessageReceivedListener = listener
    }

    override fun setOnPageFinished(listener: ((String) -> Unit)?) {
        onPageFinishedListener = listener
    }
}
