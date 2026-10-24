package com.nexustools.genericui.ext.webview.mobile

import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension

/**
 * Installs the native mobile WebView provider (`android.webkit.WebView` on Android,
 * `WKWebView` on iOS — without NJS) into [WebViewExtension].
 */
object MobileWebViewExtensionProvider {
    fun install(isIos: Boolean = false) {
        val engine = if (isIos) {
            WebViewEngineType.IOS_NATIVE_WKWEBVIEW
        } else {
            WebViewEngineType.ANDROID_NATIVE_WEBKIT
        }
        WebViewExtension.registerProvider(engine) { config ->
            MobileNativeWebViewImpl(config)
        }
    }
}
