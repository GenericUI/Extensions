package com.nexustools.genericui.ios

import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl

/**
 * iOS `WebViewNativeImpl` backed by native WebKit `WKWebView` (`IosNativeView("WKWebView")`).
 *
 * **Strictly iOS Native WKWebView — Never Uses NJS.**
 */
class IosNativeWebViewImpl(
    val config: WebViewConfig = WebViewConfig()
) : IosBaseNativeImpl("WKWebView", "webview"), WebViewNativeImpl<IosNativeView> {

    private var currentUrl: String? = null
    private var lastLoadedHtml: String? = null
    private val history = ArrayList<String>()
    private var historyIndex: Int = -1
    private var messageListener: ((String) -> Unit)? = null
    private var pageFinishedListener: ((String) -> Unit)? = null
    private var consoleMessageListener: ((WebViewConsoleMessage) -> Unit)? = null
    private var jsErrorListener: ((String) -> Unit)? = null
    private val communicationHandlers = LinkedHashMap<String, (String) -> String>()

    init {
        nativeWidget.metadata["wkWebViewConfiguration"] = "WKWebViewConfiguration"
        nativeWidget.metadata["wkUserContentController"] = "genericUi"
        nativeWidget.metadata["javaScriptEnabled"] = config.enableJavaScript.toString()
        nativeWidget.metadata["domStorageEnabled"] = config.domStorageEnabled.toString()
        nativeWidget.metadata["allowFileAccess"] = config.allowFileAccess.toString()
        config.userAgentOverride?.let { nativeWidget.metadata["customUserAgent"] = it }
    }

    override fun loadUrl(url: String) {
        currentUrl = url
        lastLoadedHtml = null
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(url)
        historyIndex = history.size - 1
        nativeWidget.metadata["wkWebViewUrl"] = url
        nativeWidget.metadata.remove("wkWebViewHtml")
        pageFinishedListener?.invoke(url)
    }

    override fun getUrl(): String? = currentUrl

    override fun loadHtml(html: String, baseUrl: String?) {
        val effectiveUrl = baseUrl ?: "about:blank"
        currentUrl = effectiveUrl
        lastLoadedHtml = html
        nativeWidget.metadata["wkWebViewHtml"] = html
        nativeWidget.metadata["wkWebViewBaseUrl"] = effectiveUrl
        pageFinishedListener?.invoke(effectiveUrl)
    }

    override fun reload() {
        currentUrl?.let { pageFinishedListener?.invoke(it) }
    }

    override fun goBack() {
        if (canGoBack()) {
            historyIndex--
            val url = history[historyIndex]
            currentUrl = url
            nativeWidget.metadata["wkWebViewUrl"] = url
            pageFinishedListener?.invoke(url)
        }
    }

    override fun goForward() {
        if (canGoForward()) {
            historyIndex++
            val url = history[historyIndex]
            currentUrl = url
            nativeWidget.metadata["wkWebViewUrl"] = url
            pageFinishedListener?.invoke(url)
        }
    }

    override fun canGoBack(): Boolean = historyIndex > 0

    override fun canGoForward(): Boolean = historyIndex >= 0 && historyIndex < history.size - 1

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        if (!config.enableJavaScript) {
            callback?.invoke(null)
            return
        }
        nativeWidget.metadata["lastEvaluatedJavaScript"] = script
        val callRegex = Regex("genericUi\\.(?:call|callCommunicationFunction)\\s*\\(\\s*['\"]([^'\"]+)['\"](?:\\s*,\\s*['\"]([^'\"]*)['\"])?\\s*\\)")
        val callMatch = callRegex.find(script)
        if (callMatch != null) {
            val fnName = callMatch.groupValues[1]
            val arg = callMatch.groupValues.getOrElse(2) { "" }
            val out = communicationHandlers[fnName]?.invoke(arg) ?: "{}"
            callback?.invoke(out)
            return
        }
        val postRegex = Regex("genericUi\\.postMessage\\s*\\(\\s*['\"]([^'\"]*)['\"]\\s*\\)")
        val postMatch = postRegex.find(script)
        if (postMatch != null) {
            messageListener?.invoke(postMatch.groupValues[1])
            callback?.invoke("undefined")
            return
        }
        callback?.invoke(script.trim())
    }

    override fun postWebMessage(message: String) {
        nativeWidget.metadata["lastPostedMessage"] = message
        messageListener?.invoke(message)
    }

    override fun setOnWebMessageReceived(listener: ((String) -> Unit)?) {
        messageListener = listener
    }

    override fun setOnPageFinished(listener: ((String) -> Unit)?) {
        pageFinishedListener = listener
    }

    override fun registerCommunicationFunction(name: String, handler: (String) -> String) {
        communicationHandlers[name] = handler
    }

    override fun unregisterCommunicationFunction(name: String) {
        communicationHandlers.remove(name)
    }

    override fun callCommunicationFunction(name: String, payloadJson: String, callback: ((String?) -> Unit)?) {
        if (!config.enableJavaScript) {
            callback?.invoke(null)
            return
        }
        val res = communicationHandlers[name]?.invoke(payloadJson)
        callback?.invoke(res)
    }

    override fun setOnConsoleMessage(listener: ((WebViewConsoleMessage) -> Unit)?) {
        consoleMessageListener = listener
    }

    override fun setOnJavaScriptError(listener: ((String) -> Unit)?) {
        jsErrorListener = listener
    }
}
