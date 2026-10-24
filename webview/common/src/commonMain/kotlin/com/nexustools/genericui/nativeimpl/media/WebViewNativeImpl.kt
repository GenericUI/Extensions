package com.nexustools.genericui.nativeimpl.media

import com.nexustools.genericui.core.NativeImpl

/**
 * Low-level native bridge contract used by the `:extensions:webview` extension (`WebView`).
 *
 * - On Desktop (`:desktop` / `:windows`, `:macos`, `:gtk`, `:qt`, `:swing`), backed by the
 *   Chromium project (`ChromiumBlinkEngine`) coupled with `NJS` (`lib/NJS` Native JIT) for the
 *   JavaScript runtime.
 * - On Mobile (`:android` `android.webkit.WebView`, `:ios` `WKWebView`) and `:web` (`<iframe>`),
 *   backed by the native platform WebView (without NJS).
 */
interface WebViewNativeImpl<out W> : NativeImpl<W> {
    fun loadUrl(url: String)
    fun getUrl(): String?
    fun loadHtml(html: String, baseUrl: String? = null)
    fun reload()
    fun goBack()
    fun goForward()
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean
    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null)
    fun postWebMessage(message: String)
    fun setOnWebMessageReceived(listener: ((String) -> Unit)?)
    fun setOnPageFinished(listener: ((String) -> Unit)?)
}
