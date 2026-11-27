package com.nexustools.genericui.nativeimpl.media

import com.nexustools.genericui.core.NativeImpl

/**
 * Low-level native bridge contract used by the `:extensions:webview` extension ([com.nexustools.genericui.ext.webview.WebView]).
 *
 * - On Desktop (`:desktop` / `:windows`, `:macos`, `:gtk`, `:kde`, `:swing`), backed by an isolated
 *   per-instance Alien + Companion Extensions (`svg`, `canvas2d`, `canvas3d`, `media`) + `NJS`
 *   worker process communicating over `:extensions:system:ipc`.
 * - On Mobile (`:android` `android.webkit.WebView`, `:ios` `WKWebView`) and `:web` (`<iframe>`),
 *   backed by the native platform WebView (without NJS).
 */
interface WebViewNativeImpl<out W> : NativeImpl<W> {
    fun loadUrl(url: String)
    fun getUrl(): String?
    fun getTitle(): String? = null
    fun getFaviconUrl(): String? = null
    fun isLoading(): Boolean = false
    fun loadHtml(html: String, baseUrl: String? = null)
    fun reload()
    fun stopLoading() {}
    fun goBack()
    fun goForward()
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean
    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null)
    fun postWebMessage(message: String)
    fun setOnWebMessageReceived(listener: ((String) -> Unit)?)
    fun setOnPageFinished(listener: ((String) -> Unit)?)
    fun setOnTitleChanged(listener: ((String) -> Unit)?) {}
    fun setOnFaviconChanged(listener: ((String?) -> Unit)?) {}
    fun setOnLoadingChanged(listener: ((Boolean) -> Unit)?) {}
    fun setOnNavStateChanged(listener: ((canGoBack: Boolean, canGoForward: Boolean) -> Unit)?) {}
    fun registerCommunicationFunction(name: String, handler: (String) -> String)
    fun unregisterCommunicationFunction(name: String)
    fun callCommunicationFunction(name: String, payloadJson: String = "", callback: ((String?) -> Unit)? = null)
    fun setOnConsoleMessage(listener: ((com.nexustools.genericui.ext.webview.WebViewConsoleMessage) -> Unit)?)
    fun setOnJavaScriptError(listener: ((String) -> Unit)?)
    fun resizeViewport(width: Int, height: Int) {}
    fun captureViewportRgba(): ByteArray? = null
    fun captureViewportSize(): Pair<Int, Int>? = null
    fun getWorkerProcessId(): Long? = null
    fun setViewportRedrawListener(listener: (() -> Unit)?) {}
}
