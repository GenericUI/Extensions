package com.nexustools.genericui.ext.webview

/**
 * Configuration options for a [WebView] instance in `:extensions:webview`.
 */
data class WebViewConfig(
    val enableJavaScript: Boolean = true,
    val domStorageEnabled: Boolean = true,
    val enableDevTools: Boolean = false,
    val userAgentOverride: String? = null,
    val allowFileAccess: Boolean = false,
    val capabilities: WebViewCapabilities = WebViewCapabilities(),
    val initialViewportWidth: Int = 1024,
    val initialViewportHeight: Int = 768
)
