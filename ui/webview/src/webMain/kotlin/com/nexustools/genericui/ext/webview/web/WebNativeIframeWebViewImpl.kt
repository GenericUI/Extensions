package com.nexustools.genericui.ext.webview.web

import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
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
    private var lastLoadedHtml: String? = null
    private val history = ArrayList<String>()
    private var historyIndex: Int = -1
    private var messageListener: ((String) -> Unit)? = null
    private var pageFinishedListener: ((String) -> Unit)? = null
    private var consoleMessageListener: ((WebViewConsoleMessage) -> Unit)? = null
    private var jsErrorListener: ((String) -> Unit)? = null
    private val communicationHandlers = LinkedHashMap<String, (String) -> String>()

    init {
        WebIframeDomBridge.bindIframeElement(
            element = nativeWidget,
            onLoad = { loadedUrl ->
                currentUrl = loadedUrl
                pageFinishedListener?.invoke(loadedUrl)
            },
            onMessage = { msg ->
                messageListener?.invoke(msg)
            },
            onConsole = { consoleMsg ->
                consoleMessageListener?.invoke(consoleMsg)
            },
            onError = { err ->
                jsErrorListener?.invoke(err)
            },
            commInvoker = { name, payload ->
                communicationHandlers[name]?.invoke(payload) ?: "{}"
            }
        )
    }

    override fun loadUrl(url: String) {
        currentUrl = url
        lastLoadedHtml = null
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(url)
        historyIndex = history.size - 1
        nativeWidget.removeAttribute("srcdoc")
        nativeWidget.setAttribute("src", url)
        WebIframeDomBridge.navigateIframeSrc(nativeWidget, url)
        pageFinishedListener?.invoke(url)
    }

    override fun getUrl(): String? = currentUrl

    override fun loadHtml(html: String, baseUrl: String?) {
        val effectiveUrl = baseUrl ?: "about:srcdoc"
        currentUrl = effectiveUrl
        lastLoadedHtml = html
        nativeWidget.setAttribute("srcdoc", html)
        nativeWidget.setAttribute("data-base-url", effectiveUrl)
        WebIframeDomBridge.setIframeSrcdoc(nativeWidget, html, effectiveUrl)
        pageFinishedListener?.invoke(effectiveUrl)
    }

    override fun reload() {
        val targetUrl = currentUrl
        WebIframeDomBridge.reloadIframe(nativeWidget, targetUrl, lastLoadedHtml)
        if (targetUrl != null) {
            pageFinishedListener?.invoke(targetUrl)
        }
    }

    override fun goBack() {
        if (canGoBack()) {
            historyIndex--
            val url = history[historyIndex]
            currentUrl = url
            lastLoadedHtml = null
            nativeWidget.removeAttribute("srcdoc")
            nativeWidget.setAttribute("src", url)
            WebIframeDomBridge.navigateIframeSrc(nativeWidget, url)
            pageFinishedListener?.invoke(url)
        }
    }

    override fun goForward() {
        if (canGoForward()) {
            historyIndex++
            val url = history[historyIndex]
            currentUrl = url
            lastLoadedHtml = null
            nativeWidget.removeAttribute("srcdoc")
            nativeWidget.setAttribute("src", url)
            WebIframeDomBridge.navigateIframeSrc(nativeWidget, url)
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
        WebIframeDomBridge.evaluateInIframe(
            element = nativeWidget,
            script = script,
            commInvoker = { name, payload ->
                communicationHandlers[name]?.invoke(payload) ?: "{}"
            },
            onMessage = { msg ->
                messageListener?.invoke(msg)
            },
            onConsole = { consoleMsg ->
                consoleMessageListener?.invoke(consoleMsg)
            },
            onError = { err ->
                jsErrorListener?.invoke(err)
            },
            callback = callback
        )
    }

    override fun postWebMessage(message: String) {
        WebIframeDomBridge.postMessageToIframe(nativeWidget, message) { msg ->
            messageListener?.invoke(msg)
        }
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
        val guestRes = WebIframeDomBridge.callGuestFunctionInIframe(nativeWidget, name, payloadJson)
        if (guestRes != null) {
            callback?.invoke(guestRes)
            return
        }
        val hostRes = communicationHandlers[name]?.invoke(payloadJson)
        callback?.invoke(hostRes)
    }

    override fun setOnConsoleMessage(listener: ((WebViewConsoleMessage) -> Unit)?) {
        consoleMessageListener = listener
    }

    override fun setOnJavaScriptError(listener: ((String) -> Unit)?) {
        jsErrorListener = listener
    }
}
