package com.nexustools.genericui.ext.webview

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl

/**
 * Modular WebView component provided by `:extensions:webview`.
 *
 * - **Desktop (`:windows`, `:macos`, `:gtk`, `:qt`, `:swing`)**: Uses the **Chromium** project
 *   (`ChromiumBlinkEngine`) for HTML/CSS layout and rendering, paired with **`NJS`**
 *   (`D:\IdeaProjects\NJS` symlinked at `lib/NJS` with AsmJit/SLJIT Native JIT) for the
 *   JavaScript runtime.
 * - **Mobile (`:android`, `:ios`) & `:web`**: Uses the native platform WebView
 *   (`android.webkit.WebView` on Android, `WKWebView` on iOS, and `<iframe>` on Web) and
 *   **never** uses NJS.
 */
class WebView(
    initialUrl: String? = null,
    val config: WebViewConfig = WebViewConfig()
) : AbstractComponent() {

    var url: String? = initialUrl
        private set

    var htmlContent: String? = null
        private set

    var htmlBaseUrl: String? = null
        private set

    private val communicationFunctions = LinkedHashMap<String, (String) -> String>()
    private var standaloneWebViewImpl: WebViewNativeImpl<*>? = null

    var onWebMessageReceived: ((String) -> Unit)? = null
        set(value) {
            field = value
            activeWebViewImpl()?.setOnWebMessageReceived(value)
        }

    var onPageFinished: ((String) -> Unit)? = null
        set(value) {
            field = value
            activeWebViewImpl()?.setOnPageFinished(value)
        }

    var onConsoleMessage: ((WebViewConsoleMessage) -> Unit)? = null
        set(value) {
            field = value
            activeWebViewImpl()?.setOnConsoleMessage(value)
        }

    var onJavaScriptError: ((String) -> Unit)? = null
        set(value) {
            field = value
            activeWebViewImpl()?.setOnJavaScriptError(value)
        }

    val engineType: WebViewEngineType
        get() = WebViewExtension.activeEngineType

    fun registerCommunicationFunction(name: String, handler: (String) -> String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        communicationFunctions[trimmed] = handler
        activeWebViewImpl()?.registerCommunicationFunction(trimmed, handler)
    }

    fun unregisterCommunicationFunction(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        communicationFunctions.remove(trimmed)
        activeWebViewImpl()?.unregisterCommunicationFunction(trimmed)
    }

    fun hasCommunicationFunction(name: String): Boolean =
        name.trim() in communicationFunctions

    fun getCommunicationFunctionNames(): Set<String> =
        communicationFunctions.keys.toSet()

    fun callCommunicationFunction(
        name: String,
        payloadJson: String = "",
        callback: ((String?) -> Unit)? = null
    ) {
        if (!config.enableJavaScript) {
            callback?.invoke(null)
            return
        }
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            callback?.invoke(null)
            return
        }
        val impl = activeWebViewImpl(autoCreate = true)
        if (impl == null) {
            callback?.invoke(null)
            return
        }
        impl.callCommunicationFunction(trimmed, payloadJson, callback)
    }

    fun invokeCommunicationFunctionFromGuest(name: String, payloadJson: String = ""): String =
        communicationFunctions[name.trim()]?.invoke(payloadJson) ?: "{}"

    fun loadUrl(targetUrl: String) {
        url = targetUrl
        htmlContent = null
        activeWebViewImpl(autoCreate = true)?.loadUrl(targetUrl)
    }

    fun loadHtml(html: String, baseUrl: String? = null) {
        htmlContent = html
        htmlBaseUrl = baseUrl
        url = null
        activeWebViewImpl(autoCreate = true)?.loadHtml(html, baseUrl)
    }

    fun reload() {
        activeWebViewImpl()?.reload()
    }

    fun goBack() {
        activeWebViewImpl()?.goBack()
    }

    fun goForward() {
        activeWebViewImpl()?.goForward()
    }

    fun canGoBack(): Boolean =
        activeWebViewImpl()?.canGoBack() ?: false

    fun canGoForward(): Boolean =
        activeWebViewImpl()?.canGoForward() ?: false

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null) {
        if (!config.enableJavaScript) {
            callback?.invoke(null)
            return
        }
        val impl = activeWebViewImpl(autoCreate = true)
        if (impl == null) {
            callback?.invoke(null)
            return
        }
        impl.evaluateJavascript(script, callback)
    }

    fun postWebMessage(message: String) {
        activeWebViewImpl(autoCreate = true)?.postWebMessage(message)
    }

    protected override fun createNativeImpl(factory: NativeFactory): WebViewNativeImpl<*> {
        standaloneWebViewImpl?.let { return it }
        val extFactory = WebViewExtension.nativeImplFactory
        return extFactory?.invoke(config)
            ?: throw IllegalStateException("No WebViewNativeImpl provider registered in WebViewExtension")
    }

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<WebViewNativeImpl<*>>() ?: return
        if (impl === standaloneWebViewImpl) return
        syncWebViewStateTo(impl)
    }

    private fun activeWebViewImpl(autoCreate: Boolean = false): WebViewNativeImpl<*>? {
        typedNative<WebViewNativeImpl<*>>()?.let { return it }
        standaloneWebViewImpl?.let { return it }
        if (!autoCreate) return null
        val extFactory = WebViewExtension.nativeImplFactory ?: return null
        val created = extFactory.invoke(config)
        standaloneWebViewImpl = created
        syncWebViewStateTo(created, loadPendingPage = false)
        return created
    }

    private fun syncWebViewStateTo(impl: WebViewNativeImpl<*>, loadPendingPage: Boolean = true) {
        impl.setOnWebMessageReceived(onWebMessageReceived)
        impl.setOnPageFinished(onPageFinished)
        impl.setOnConsoleMessage(onConsoleMessage)
        impl.setOnJavaScriptError(onJavaScriptError)
        for ((fnName, fnHandler) in communicationFunctions) {
            impl.registerCommunicationFunction(fnName, fnHandler)
        }
        if (loadPendingPage) {
            when {
                url != null -> impl.loadUrl(url!!)
                htmlContent != null -> impl.loadHtml(htmlContent!!, htmlBaseUrl)
            }
        }
    }
}
