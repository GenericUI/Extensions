package com.nexustools.genericui.ext.webview.desktop

/**
 * Represents a `<script>` element extracted from an HTML document by [ChromiumBlinkEngine].
 */
data class ChromiumBlinkScriptEntry(
    val src: String? = null,
    val inlineCode: String = "",
    val type: String = "text/javascript",
    val dataWasm: String? = null
) {
    val isExecutableJavaScript: Boolean
        get() {
            val normalized = type.trim().lowercase()
            return normalized.isEmpty() ||
                normalized == "text/javascript" ||
                normalized == "application/javascript" ||
                normalized == "module"
        }
}
