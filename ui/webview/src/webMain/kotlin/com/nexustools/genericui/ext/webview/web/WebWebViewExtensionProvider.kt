package com.nexustools.genericui.ext.webview.web

import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension

/**
 * Installs the native HTML5 `<iframe>` WebView provider (without NJS) into [WebViewExtension].
 */
object WebWebViewExtensionProvider {
    fun install() {
        WebViewExtension.registerProvider(WebViewEngineType.WEB_NATIVE_IFRAME) { config ->
            WebNativeIframeWebViewImpl(config)
        }
    }
}
