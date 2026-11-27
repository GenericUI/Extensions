package com.nexustools.genericui.ext.webview.desktop.engine

/**
 * Resolved resource fetch result inside a desktop WebView worker process.
 */
data class WebResourceResult(
    val url: String,
    val status: Int,
    val statusText: String,
    val mimeType: String,
    val headers: Map<String, String> = emptyMap(),
    val bytes: ByteArray = ByteArray(0)
) {
    val ok: Boolean get() = status in 200..299

    fun decodeUtf8Text(): String = bytes.decodeToString().removePrefix("\uFEFF")
}
