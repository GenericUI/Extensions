package com.nexustools.genericui.android

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl

/**
 * Android `WebViewNativeImpl` backed by a real `android.webkit.WebView` instance.
 *
 * **Strictly Android Native WebKit — Never Uses NJS.**
 */
class AndroidNativeWebViewImpl(
    val config: WebViewConfig = WebViewConfig()
) : AndroidBaseNativeImpl("android.webkit.WebView", "webview"), WebViewNativeImpl<AndroidViewHandle> {

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
        nativeWidget.attributes["javaScriptEnabled"] = config.enableJavaScript.toString()
        nativeWidget.attributes["domStorageEnabled"] = config.domStorageEnabled.toString()
        nativeWidget.attributes["allowFileAccess"] = config.allowFileAccess.toString()
        config.userAgentOverride?.let { nativeWidget.attributes["userAgent"] = it }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun createDefaultAndroidView(context: Context): View? {
        val webView = WebView(context)
        val settings = webView.settings
        settings.javaScriptEnabled = config.enableJavaScript
        settings.domStorageEnabled = config.domStorageEnabled
        settings.allowFileAccess = config.allowFileAccess
        config.userAgentOverride?.let { settings.userAgentString = it }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val resolved = url ?: currentUrl ?: "about:blank"
                currentUrl = resolved
                pageFinishedListener?.invoke(resolved)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    val level = consoleMessage.messageLevel()?.name?.lowercase() ?: "log"
                    val msg = WebViewConsoleMessage(
                        level = level,
                        message = consoleMessage.message() ?: "",
                        sourceId = consoleMessage.sourceId() ?: "",
                        line = consoleMessage.lineNumber()
                    )
                    consoleMessageListener?.invoke(msg)
                    if (level == "error") {
                        jsErrorListener?.invoke(msg.message)
                    }
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }
        if (config.enableJavaScript) {
            webView.addJavascriptInterface(this, "genericUi")
        }
        return webView
    }

    @JavascriptInterface
    fun postMessage(message: String) {
        messageListener?.invoke(message)
    }

    @JavascriptInterface
    fun call(name: String, payloadJson: String = ""): String =
        communicationHandlers[name]?.invoke(payloadJson) ?: "{}"

    @JavascriptInterface
    fun callCommunicationFunction(name: String, payloadJson: String = ""): String =
        communicationHandlers[name]?.invoke(payloadJson) ?: "{}"

    override fun loadUrl(url: String) {
        currentUrl = url
        lastLoadedHtml = null
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(url)
        historyIndex = history.size - 1
        nativeWidget.attributes["url"] = url
        nativeWidget.attributes.remove("html")
        val view = nativeWidget.androidView as? WebView
        if (view != null) {
            AndroidViewHandle.runOnMainThread {
                runCatching { view.loadUrl(url) }
            }
        } else {
            pageFinishedListener?.invoke(url)
        }
    }

    override fun getUrl(): String? = currentUrl

    override fun loadHtml(html: String, baseUrl: String?) {
        val effectiveUrl = baseUrl ?: "about:blank"
        currentUrl = effectiveUrl
        lastLoadedHtml = html
        nativeWidget.attributes["html"] = html
        nativeWidget.attributes["baseUrl"] = effectiveUrl
        val view = nativeWidget.androidView as? WebView
        if (view != null) {
            AndroidViewHandle.runOnMainThread {
                runCatching {
                    view.loadDataWithBaseURL(effectiveUrl, html, "text/html", "UTF-8", null)
                }
            }
        } else {
            pageFinishedListener?.invoke(effectiveUrl)
        }
    }

    override fun reload() {
        val view = nativeWidget.androidView as? WebView
        if (view != null) {
            AndroidViewHandle.runOnMainThread {
                runCatching { view.reload() }
            }
        } else {
            currentUrl?.let { pageFinishedListener?.invoke(it) }
        }
    }

    override fun goBack() {
        if (canGoBack()) {
            historyIndex--
            val url = history[historyIndex]
            currentUrl = url
            nativeWidget.attributes["url"] = url
            val view = nativeWidget.androidView as? WebView
            if (view != null) {
                AndroidViewHandle.runOnMainThread {
                    runCatching { view.goBack() }
                }
            } else {
                pageFinishedListener?.invoke(url)
            }
        }
    }

    override fun goForward() {
        if (canGoForward()) {
            historyIndex++
            val url = history[historyIndex]
            currentUrl = url
            nativeWidget.attributes["url"] = url
            val view = nativeWidget.androidView as? WebView
            if (view != null) {
                AndroidViewHandle.runOnMainThread {
                    runCatching { view.goForward() }
                }
            } else {
                pageFinishedListener?.invoke(url)
            }
        }
    }

    override fun canGoBack(): Boolean = historyIndex > 0

    override fun canGoForward(): Boolean = historyIndex >= 0 && historyIndex < history.size - 1

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        if (!config.enableJavaScript) {
            callback?.invoke(null)
            return
        }
        nativeWidget.attributes["lastEvaluatedScript"] = script
        val view = nativeWidget.androidView as? WebView
        if (view != null) {
            AndroidViewHandle.runOnMainThread {
                runCatching {
                    view.evaluateJavascript(script) { result ->
                        callback?.invoke(result)
                    }
                }.onFailure {
                    jsErrorListener?.invoke(it.message ?: "Android WebView evaluateJavascript error")
                    callback?.invoke(null)
                }
            }
            return
        }

        // Headless host test evaluation support for bridge invocations
        val callRegex = Regex("genericUi\\.(?:call|callCommunicationFunction)\\s*\\(\\s*['\"]([^'\"]+)['\"](?:\\s*,\\s*['\"]([^'\"]*)['\"])?\\s*\\)")
        val callMatch = callRegex.find(script)
        if (callMatch != null) {
            val fnName = callMatch.groupValues[1]
            val arg = callMatch.groupValues.getOrElse(2) { "" }
            callback?.invoke(call(fnName, arg))
            return
        }
        val postRegex = Regex("genericUi\\.postMessage\\s*\\(\\s*['\"]([^'\"]*)['\"]\\s*\\)")
        val postMatch = postRegex.find(script)
        if (postMatch != null) {
            postMessage(postMatch.groupValues[1])
            callback?.invoke("undefined")
            return
        }
        callback?.invoke(script.trim())
    }

    override fun postWebMessage(message: String) {
        nativeWidget.attributes["lastPostedMessage"] = message
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
