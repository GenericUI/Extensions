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

    var onWebMessageReceived: ((String) -> Unit)? = null
    var onPageFinished: ((String) -> Unit)? = null

    val engineType: WebViewEngineType
        get() = WebViewExtension.activeEngineType

    fun loadUrl(targetUrl: String) {
        url = targetUrl
        htmlContent = null
        typedNative<WebViewNativeImpl<*>>()?.loadUrl(targetUrl)
    }

    fun loadHtml(html: String, baseUrl: String? = null) {
        htmlContent = html
        htmlBaseUrl = baseUrl
        url = null
        typedNative<WebViewNativeImpl<*>>()?.loadHtml(html, baseUrl)
    }

    fun reload() {
        typedNative<WebViewNativeImpl<*>>()?.reload()
    }

    fun goBack() {
        typedNative<WebViewNativeImpl<*>>()?.goBack()
    }

    fun goForward() {
        typedNative<WebViewNativeImpl<*>>()?.goForward()
    }

    fun canGoBack(): Boolean =
        typedNative<WebViewNativeImpl<*>>()?.canGoBack() ?: false

    fun canGoForward(): Boolean =
        typedNative<WebViewNativeImpl<*>>()?.canGoForward() ?: false

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null) {
        if (!config.enableJavaScript) {
            callback?.invoke(null)
            return
        }
        typedNative<WebViewNativeImpl<*>>()?.evaluateJavascript(script, callback)
    }

    fun postWebMessage(message: String) {
        typedNative<WebViewNativeImpl<*>>()?.postWebMessage(message)
    }

    override fun createNativeImpl(factory: NativeFactory): WebViewNativeImpl<*> {
        val extFactory = WebViewExtension.nativeImplFactory
        return extFactory?.invoke(config)
            ?: throw IllegalStateException("No WebViewNativeImpl provider registered in WebViewExtension")
    }

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<WebViewNativeImpl<*>>() ?: return
        impl.setOnWebMessageReceived(onWebMessageReceived)
        impl.setOnPageFinished(onPageFinished)
        when {
            url != null -> impl.loadUrl(url!!)
            htmlContent != null -> impl.loadHtml(htmlContent!!, htmlBaseUrl)
        }
    }
}
