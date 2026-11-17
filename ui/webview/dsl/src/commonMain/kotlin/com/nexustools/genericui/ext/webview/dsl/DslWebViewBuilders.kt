package com.nexustools.genericui.ext.webview.dsl

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.webview.WebView
import com.nexustools.genericui.ext.webview.WebViewConfig

/**
 * In-place [WebView] builder with URL/HTML loading, JS bridge callbacks, and `style { ... }` support.
 */
fun AbstractContainer.webViewExt(
    initialUrl: String? = null,
    config: WebViewConfig = WebViewConfig(),
    htmlContent: String? = null,
    htmlBaseUrl: String? = null,
    id: String? = null,
    classes: String? = null,
    onWebMessageReceived: ((String) -> Unit)? = null,
    onPageFinished: ((String) -> Unit)? = null,
    block: (@GenericUiDslMarker WebView.() -> Unit)? = null
): WebView {
    val view = WebView(initialUrl = initialUrl, config = config)
    if (htmlContent != null) {
        view.loadHtml(htmlContent, htmlBaseUrl)
    }
    if (onWebMessageReceived != null) {
        view.onWebMessageReceived = onWebMessageReceived
    }
    if (onPageFinished != null) {
        view.onPageFinished = onPageFinished
    }
    if (!classes.isNullOrBlank()) {
        view.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        view.id = id
    }
    if (block != null) {
        view.block()
    }
    addChild(view)
    return view
}

/**
 * Alias for [webViewExt] that mounts and configures a [WebView].
 */
fun AbstractContainer.webView(
    initialUrl: String? = null,
    config: WebViewConfig = WebViewConfig(),
    htmlContent: String? = null,
    htmlBaseUrl: String? = null,
    id: String? = null,
    classes: String? = null,
    onWebMessageReceived: ((String) -> Unit)? = null,
    onPageFinished: ((String) -> Unit)? = null,
    block: (@GenericUiDslMarker WebView.() -> Unit)? = null
): WebView = webViewExt(
    initialUrl = initialUrl,
    config = config,
    htmlContent = htmlContent,
    htmlBaseUrl = htmlBaseUrl,
    id = id,
    classes = classes,
    onWebMessageReceived = onWebMessageReceived,
    onPageFinished = onPageFinished,
    block = block
)
