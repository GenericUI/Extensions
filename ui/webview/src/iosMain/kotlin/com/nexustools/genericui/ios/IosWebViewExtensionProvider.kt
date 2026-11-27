package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension

/**
 * Installs the native Apple iOS & iPadOS WebKit `WKWebView` provider (without NJS)
 * into [WebViewExtension] for both [BackendTarget.IOS] and [BackendTarget.IPadOS].
 */
object IosWebViewExtensionProvider {
    fun supportsTarget(target: BackendTarget): Boolean = target.isIosFamily

    fun install() {
        WebViewExtension.registerProvider(WebViewEngineType.APPLE_WKWEBVIEW) { config ->
            IosNativeWebViewImpl(config)
        }
    }
}
