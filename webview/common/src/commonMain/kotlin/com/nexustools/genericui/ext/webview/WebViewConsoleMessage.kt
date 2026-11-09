package com.nexustools.genericui.ext.webview

/**
 * Represents a JavaScript console log, info, warning, or error message emitted inside a [WebView].
 */
data class WebViewConsoleMessage(
    val level: String,
    val message: String,
    val sourceId: String = "",
    val line: Int = 0
)
