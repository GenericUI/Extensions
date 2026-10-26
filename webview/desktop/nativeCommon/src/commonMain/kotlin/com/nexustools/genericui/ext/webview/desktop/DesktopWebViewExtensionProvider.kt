package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension

/**
 * Installs the Desktop Chromium + NJS Native JIT provider into [WebViewExtension]
 * for `:windows`, `:macos`, `:gtk`, and `:qt`.
 */
object DesktopWebViewExtensionProvider {
    fun install() {
        WebViewExtension.registerProvider(WebViewEngineType.CHROMIUM_NJS_JIT_DESKTOP) { config ->
            DesktopChromiumNjsWebViewImpl(config)
        }
    }
}
