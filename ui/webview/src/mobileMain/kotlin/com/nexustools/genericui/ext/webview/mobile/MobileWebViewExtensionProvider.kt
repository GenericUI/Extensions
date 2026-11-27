package com.nexustools.genericui.ext.webview.mobile

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension

/**
 * Installs the native mobile WebView provider (`android.webkit.WebView` on Android and
 * `WKWebView` on iOS / iPadOS — without NJS) into [WebViewExtension].
 */
object MobileWebViewExtensionProvider {
    fun install(target: BackendTarget) {
        val isIosFamily = target.isIosFamily
        val engine = if (isIosFamily) {
            WebViewEngineType.APPLE_WKWEBVIEW
        } else {
            WebViewEngineType.ANDROID_WEBVIEW
        }
        WebViewExtension.registerProvider(engine) { config ->
            MobileNativeWebViewImpl(config)
        }
    }

    fun install(isIos: Boolean = GenericUI.activeFactory?.backendTarget?.isIosFamily == true) {
        val engine = if (isIos) {
            WebViewEngineType.APPLE_WKWEBVIEW
        } else {
            WebViewEngineType.ANDROID_WEBVIEW
        }
        WebViewExtension.registerProvider(engine) { config ->
            MobileNativeWebViewImpl(config)
        }
    }
}
