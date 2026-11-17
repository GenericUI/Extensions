package com.nexustools.genericui.ext.webview.swing

import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension
import com.nexustools.njs.compiler.JavaTranspiler

/**
 * Installs the Swing Chromium + NJS (`JavaTranspiler`) provider into [WebViewExtension].
 */
object SwingWebViewExtensionProvider {
    fun install() {
        JavaTranspiler.installIntoRuntimeCompiler()
        WebViewExtension.registerProvider(WebViewEngineType.CHROMIUM_NJS_JIT_DESKTOP) { config ->
            SwingChromiumNjsWebViewImpl(config)
        }
    }
}
