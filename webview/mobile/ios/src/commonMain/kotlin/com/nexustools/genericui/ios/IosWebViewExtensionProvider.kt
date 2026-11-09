package com.nexustools.genericui.ios

import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension

/**
 * Installs the native iOS WebKit `WKWebView` provider (without NJS) into [WebViewExtension].
 */
object IosWebViewExtensionProvider {
    fun install() {
        WebViewExtension.registerProvider(WebViewEngineType.IOS_NATIVE_WKWEBVIEW) { config ->
            IosNativeWebViewImpl(config)
        }
    }
}
