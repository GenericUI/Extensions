package com.nexustools.genericui.ext.webview.swing

import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension

/**
 * Installs the Swing Chromium + NJS JIT provider into [WebViewExtension].
 */
object SwingWebViewExtensionProvider {
    fun install() {
        WebViewExtension.registerProvider(WebViewEngineType.CHROMIUM_NJS_JIT_DESKTOP) { config ->
            SwingChromiumNjsWebViewImpl(config)
        }
    }
}
