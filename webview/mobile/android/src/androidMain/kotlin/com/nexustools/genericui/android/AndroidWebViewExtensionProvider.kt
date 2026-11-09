package com.nexustools.genericui.android

import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension

/**
 * Installs the native Android `android.webkit.WebView` provider (without NJS) into [WebViewExtension].
 */
object AndroidWebViewExtensionProvider {
    fun install() {
        WebViewExtension.registerProvider(WebViewEngineType.ANDROID_NATIVE_WEBKIT) { config ->
            AndroidNativeWebViewImpl(config)
        }
    }
}
