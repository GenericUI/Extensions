package com.nexustools.genericui.ext.webview

/**
 * Configuration options for a [WebView] instance in `:extensions:webview`.
 */
data class WebViewConfig(
    val enableJavaScript: Boolean = true,
    val enableDevTools: Boolean = false,
    val userAgentOverride: String? = null,
    val allowFileAccess: Boolean = false
)
