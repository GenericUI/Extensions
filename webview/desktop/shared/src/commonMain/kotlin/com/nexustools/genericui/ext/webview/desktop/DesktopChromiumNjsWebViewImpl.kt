package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl

/**
 * Desktop `WebViewNativeImpl` implementation backed by the **Chromium** Blink layout/rendering
 * engine ([ChromiumBlinkEngine]) and the **`NJS`** Native JIT runtime ([NjsDesktopWebRuntime])
 * across `:windows`, `:macos`, `:gtk`, and `:qt`.
 */
class DesktopChromiumNjsWebViewImpl(
    val config: WebViewConfig = WebViewConfig(),
    nativeHandle: DesktopNativeWidget = DesktopNativeWidget("Desktop", "ChromiumNjsWebView")
) : DesktopBaseNativeImpl<DesktopNativeWidget>(nativeHandle, "webview"), WebViewNativeImpl<DesktopNativeWidget> {

    val blinkEngine: ChromiumBlinkEngine = ChromiumBlinkEngine()

    private val history = ArrayList<String>()
    private var historyIndex: Int = -1
    private var messageListener: ((String) -> Unit)? = null
    private var pageFinishedListener: ((String) -> Unit)? = null

    val njsRuntime: NjsDesktopWebRuntime = NjsDesktopWebRuntime(
        blinkEngine = blinkEngine,
        onPostMessage = { msg -> messageListener?.invoke(msg) }
    )

    override fun loadUrl(url: String) {
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(url)
        historyIndex = history.size - 1
        blinkEngine.currentUrl = url
        pageFinishedListener?.invoke(url)
    }

    override fun getUrl(): String? = blinkEngine.currentUrl?.ifEmpty { null }

    override fun loadHtml(html: String, baseUrl: String?) {
        val inlineScripts = blinkEngine.parseHtmlDocument(html, baseUrl)
        if (config.enableJavaScript) {
            for ((index, script) in inlineScripts.withIndex()) {
                njsRuntime.evaluateScript(script, "${baseUrl ?: "inline"}#script$index")
            }
        }
        pageFinishedListener?.invoke(baseUrl ?: "about:blank")
    }

    override fun reload() {
        val url = getUrl()
        if (url != null) {
            pageFinishedListener?.invoke(url)
        }
    }

    override fun goBack() {
        if (canGoBack()) {
            historyIndex--
            blinkEngine.currentUrl = history[historyIndex]
        }
    }

    override fun goForward() {
        if (canGoForward()) {
            historyIndex++
            blinkEngine.currentUrl = history[historyIndex]
        }
    }

    override fun canGoBack(): Boolean = historyIndex > 0

    override fun canGoForward(): Boolean = historyIndex >= 0 && historyIndex < history.size - 1

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        if (!config.enableJavaScript) {
            callback?.invoke(null)
            return
        }
        val result = njsRuntime.evaluateScript(script, "<evaluateJavascript>")
        callback?.invoke(result)
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
