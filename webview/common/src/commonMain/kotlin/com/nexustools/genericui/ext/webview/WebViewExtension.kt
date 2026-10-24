package com.nexustools.genericui.ext.webview

import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl

/**
 * Registry and platform provider bridge for the `:extensions:webview` extension.
 *
 * - `:extensions:webview:desktop` and `:extensions:webview:swing` register a Chromium + `NJS`
 *   JIT provider (`WebViewEngineType.CHROMIUM_NJS_JIT_DESKTOP`).
 * - `:extensions:webview:mobile` registers native `android.webkit.WebView` / `WKWebView`
 *   providers (no NJS).
 * - `:extensions:webview:web` registers a native HTML5 `<iframe>` provider (no NJS).
 */
object WebViewExtension {
    var activeEngineType: WebViewEngineType = WebViewEngineType.CHROMIUM_NJS_JIT_DESKTOP
        private set

    var nativeImplFactory: ((WebViewConfig) -> WebViewNativeImpl<*>)? = null
        private set

    fun registerProvider(
        engineType: WebViewEngineType,
        factory: (WebViewConfig) -> WebViewNativeImpl<*>
    ) {
        activeEngineType = engineType
        nativeImplFactory = factory
    }
}
