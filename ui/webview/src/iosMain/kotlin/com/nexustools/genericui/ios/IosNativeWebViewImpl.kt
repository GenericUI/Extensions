@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDictionary
import platform.Foundation.NSError
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebpagePreferences
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS & iPadOS `WebViewNativeImpl` backed by real Apple `platform.WebKit.WKWebView`
 * (`WKNavigationDelegateProtocol`, `WKScriptMessageHandlerProtocol`, `WKUserContentController`)
 * wrapped inside [UikitViewHandle].
 *
 * **Strictly Apple Native WKWebView — Never Uses NJS.**
 */
class IosNativeWebViewImpl(
    val config: WebViewConfig = WebViewConfig(),
    val wkWebView: WKWebView = createConfiguredWkWebView(config)
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(wkWebView),
    componentClassPrefix = "webview"
), WebViewNativeImpl<UikitViewHandle> {

    private var currentUrl: String? = null
    private var currentFaviconUrlState: String? = null
    private var lastLoadedHtml: String? = null
    private val history = ArrayList<String>()
    private var historyIndex: Int = -1
    private var messageListener: ((String) -> Unit)? = null
    private var pageFinishedListener: ((String) -> Unit)? = null
    private var faviconChangedListener: ((String?) -> Unit)? = null
    private var consoleMessageListener: ((WebViewConsoleMessage) -> Unit)? = null
    private var jsErrorListener: ((String) -> Unit)? = null
    private val communicationHandlers = LinkedHashMap<String, (String) -> String>()

    private val webKitDelegate = WebKitBridgeDelegate()

    init {
        wkWebView.navigationDelegate = webKitDelegate
        if (config.enableJavaScript) {
            val ucc = wkWebView.configuration.userContentController
            runCatching {
                ucc.removeScriptMessageHandlerForName(BRIDGE_MESSAGE_HANDLER_NAME)
                ucc.addScriptMessageHandler(webKitDelegate, name = BRIDGE_MESSAGE_HANDLER_NAME)
                ucc.removeScriptMessageHandlerForName(CONSOLE_MESSAGE_HANDLER_NAME)
                ucc.addScriptMessageHandler(webKitDelegate, name = CONSOLE_MESSAGE_HANDLER_NAME)
            }
        }
        config.userAgentOverride?.let { wkWebView.customUserAgent = it }
    }

    private fun runOnMain(block: () -> Unit) {
        if (NSThread.isMainThread) {
            block()
        } else {
            dispatch_async(dispatch_get_main_queue(), block)
        }
    }

    override fun loadUrl(url: String) {
        currentUrl = url
        lastLoadedHtml = null
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(url)
        historyIndex = history.size - 1
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl != null) {
            runOnMain {
                runCatching {
                    wkWebView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
                }
            }
        }
        pageFinishedListener?.invoke(url)
        updateFaviconUrl(resolveFallbackFaviconUrl(url))
    }

    override fun getUrl(): String? = wkWebView.URL?.absoluteString ?: currentUrl

    override fun getFaviconUrl(): String? = currentFaviconUrlState

    override fun loadHtml(html: String, baseUrl: String?) {
        val effectiveUrl = baseUrl ?: "about:blank"
        currentUrl = effectiveUrl
        lastLoadedHtml = html
        val baseNsUrl = baseUrl?.let { NSURL.URLWithString(it) }
        runOnMain {
            runCatching {
                wkWebView.loadHTMLString(html, baseURL = baseNsUrl)
            }
        }
        pageFinishedListener?.invoke(effectiveUrl)
        updateFaviconUrl(resolveFallbackFaviconUrl(effectiveUrl))
    }

    override fun reload() {
        runOnMain {
            runCatching { wkWebView.reload() }
        }
        currentUrl?.let { pageFinishedListener?.invoke(it) }
    }

    override fun goBack() {
        if (wkWebView.canGoBack) {
            runOnMain {
                runCatching { wkWebView.goBack() }
            }
        }
        if (canGoBack()) {
            historyIndex--
            val url = history[historyIndex]
            currentUrl = url
            pageFinishedListener?.invoke(url)
        }
    }

    override fun goForward() {
        if (wkWebView.canGoForward) {
            runOnMain {
                runCatching { wkWebView.goForward() }
            }
        }
        if (canGoForward()) {
            historyIndex++
            val url = history[historyIndex]
            currentUrl = url
            pageFinishedListener?.invoke(url)
        }
    }

    override fun canGoBack(): Boolean = wkWebView.canGoBack || historyIndex > 0

    override fun canGoForward(): Boolean = wkWebView.canGoForward || (historyIndex >= 0 && historyIndex < history.size - 1)

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        if (!config.enableJavaScript) {
            callback?.invoke(null)
            return
        }
        val callRegex = Regex(
            "genericUi\\.(?:call|callCommunicationFunction)\\s*\\(\\s*['\"]([^'\"]+)['\"](?:\\s*,\\s*['\"]([^'\"]*)['\"])?\\s*\\)"
        )
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

        if (wkWebView.window != null) {
            runOnMain {
                wkWebView.evaluateJavaScript(script) { result, error ->
                    if (error != null) {
                        jsErrorListener?.invoke(error.localizedDescription)
                        callback?.invoke(null)
                    } else {
                        callback?.invoke(result?.toString())
                    }
                }
            }
        } else {
            callback?.invoke(script.trim())
        }
    }

    override fun postWebMessage(message: String) {
        if (config.enableJavaScript && wkWebView.window != null) {
            val escaped = message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            runOnMain {
                wkWebView.evaluateJavaScript(
                    "window.dispatchEvent(new MessageEvent('message', { data: \"$escaped\" }));",
                    completionHandler = null
                )
            }
        }
        messageListener?.invoke(message)
    }

    override fun setOnWebMessageReceived(listener: ((String) -> Unit)?) {
        messageListener = listener
    }

    override fun setOnPageFinished(listener: ((String) -> Unit)?) {
        pageFinishedListener = listener
    }

    override fun setOnFaviconChanged(listener: ((String?) -> Unit)?) {
        faviconChangedListener = listener
    }

    private fun updateFaviconUrl(next: String?) {
        if (next != currentFaviconUrlState) {
            currentFaviconUrlState = next
            runOnMain {
                faviconChangedListener?.invoke(next)
            }
        }
    }

    private fun resolveFallbackFaviconUrl(url: String?): String? {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
            return null
        }
        val schemeEnd = raw.indexOf("://")
        val scheme = raw.substring(0, schemeEnd).lowercase()
        val after = raw.substring(schemeEnd + 3)
        val authEnd = listOf(after.indexOf('/'), after.indexOf('?'), after.indexOf('#')).filter { it >= 0 }.minOrNull() ?: after.length
        val auth = after.substring(0, authEnd)
        return if (auth.isEmpty()) null else "$scheme://$auth/favicon.ico"
    }

    override fun registerCommunicationFunction(name: String, handler: (String) -> String) {
        communicationHandlers[name] = handler
    }

    override fun unregisterCommunicationFunction(name: String) {
        communicationHandlers.remove(name)
    }

    override fun callCommunicationFunction(
        name: String,
        payloadJson: String,
        callback: ((String?) -> Unit)?
    ) {
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

    override fun dispose() {
        runOnMain {
            runCatching {
                wkWebView.navigationDelegate = null
                val ucc = wkWebView.configuration.userContentController
                ucc.removeScriptMessageHandlerForName(BRIDGE_MESSAGE_HANDLER_NAME)
                ucc.removeScriptMessageHandlerForName(CONSOLE_MESSAGE_HANDLER_NAME)
                wkWebView.stopLoading()
            }
        }
        super.dispose()
    }

    private inner class WebKitBridgeDelegate :
        NSObject(),
        WKNavigationDelegateProtocol,
        WKScriptMessageHandlerProtocol {

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
            val resolvedUrl = webView.URL?.absoluteString ?: currentUrl ?: "about:blank"
            currentUrl = resolvedUrl
            pageFinishedListener?.invoke(resolvedUrl)
        }

        @ObjCSignatureOverride
        override fun webView(
            webView: WKWebView,
            didFailNavigation: WKNavigation?,
            withError: NSError
        ) {
            jsErrorListener?.invoke(withError.localizedDescription)
        }

        @ObjCSignatureOverride
        override fun webView(
            webView: WKWebView,
            didFailProvisionalNavigation: WKNavigation?,
            withError: NSError
        ) {
            jsErrorListener?.invoke(withError.localizedDescription)
        }

        override fun userContentController(
            userContentController: WKUserContentController,
            didReceiveScriptMessage: WKScriptMessage
        ) {
            when (didReceiveScriptMessage.name) {
                BRIDGE_MESSAGE_HANDLER_NAME -> {
                    val body = didReceiveScriptMessage.body
                    if (body is NSDictionary) {
                        val type = body.objectForKey("type")?.toString()
                        val name = body.objectForKey("name")?.toString().orEmpty()
                        val payload = body.objectForKey("payload")?.toString().orEmpty()
                        if (type == "call" && name.isNotEmpty()) {
                            val result = communicationHandlers[name]?.invoke(payload) ?: "{}"
                            val callId = body.objectForKey("callId")?.toString()
                            if (callId != null) {
                                val escaped = result
                                    .replace("\\", "\\\\")
                                    .replace("\"", "\\\"")
                                    .replace("\n", "\\n")
                                runOnMain {
                                    wkWebView.evaluateJavaScript(
                                        "window.__genericUiResolveCall && window.__genericUiResolveCall(\"$callId\", \"$escaped\");",
                                        completionHandler = null
                                    )
                                }
                            }
                            return
                        }
                    }
                    messageListener?.invoke(body?.toString().orEmpty())
                }

                CONSOLE_MESSAGE_HANDLER_NAME -> {
                    val body = didReceiveScriptMessage.body
                    if (body is NSDictionary) {
                        val level = body.objectForKey("level")?.toString() ?: "log"
                        val text = body.objectForKey("message")?.toString().orEmpty()
                        val msg = WebViewConsoleMessage(level = level, message = text)
                        consoleMessageListener?.invoke(msg)
                        if (level == "error") {
                            jsErrorListener?.invoke(text)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val BRIDGE_MESSAGE_HANDLER_NAME = "genericUi"
        private const val CONSOLE_MESSAGE_HANDLER_NAME = "genericUiConsole"

        private val BRIDGE_BOOTSTRAP_JS = """
            (function() {
                if (window.genericUi) return;
                var pending = {};
                var nextCallId = 1;
                window.__genericUiResolveCall = function(id, res) {
                    var cb = pending[id];
                    if (cb) {
                        delete pending[id];
                        cb(res);
                    }
                };
                window.genericUi = {
                    postMessage: function(msg) {
                        if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.genericUi) {
                            window.webkit.messageHandlers.genericUi.postMessage(String(msg));
                        }
                    },
                    call: function(name, payload) {
                        var id = String(nextCallId++);
                        if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.genericUi) {
                            window.webkit.messageHandlers.genericUi.postMessage({
                                type: "call",
                                callId: id,
                                name: String(name),
                                payload: payload === undefined || payload === null ? "" : String(payload)
                            });
                        }
                        return "{}";
                    },
                    callCommunicationFunction: function(name, payload) {
                        return window.genericUi.call(name, payload);
                    }
                };
            })();
        """.trimIndent()

        private fun createConfiguredWkWebView(config: WebViewConfig): WKWebView {
            val wkConfig = WKWebViewConfiguration()
            val webpagePrefs = WKWebpagePreferences().apply {
                allowsContentJavaScript = config.enableJavaScript
            }
            wkConfig.defaultWebpagePreferences = webpagePrefs
            wkConfig.websiteDataStore = if (config.domStorageEnabled) {
                WKWebsiteDataStore.defaultDataStore()
            } else {
                WKWebsiteDataStore.nonPersistentDataStore()
            }
            val ucc = WKUserContentController()
            if (config.enableJavaScript) {
                val script = WKUserScript(
                    source = BRIDGE_BOOTSTRAP_JS,
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                    forMainFrameOnly = false
                )
                ucc.addUserScript(script)
            }
            wkConfig.userContentController = ucc
            return WKWebView(
                frame = CGRectMake(0.0, 0.0, 320.0, 240.0),
                configuration = wkConfig
            ).apply {
                config.userAgentOverride?.let { customUserAgent = it }
            }
        }
    }
}
