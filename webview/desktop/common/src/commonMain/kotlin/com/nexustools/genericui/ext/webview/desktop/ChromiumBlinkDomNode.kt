package com.nexustools.genericui.ext.webview.desktop

/**
 * Represents a DOM node inside the Chromium Blink layout tree for Desktop WebView.
 */
class ChromiumBlinkDomNode(
    val tagName: String,
    var id: String = "",
    val attributes: MutableMap<String, String> = LinkedHashMap(),
    var textContent: String = "",
    val children: MutableList<ChromiumBlinkDomNode> = ArrayList()
) {
    fun findById(targetId: String): ChromiumBlinkDomNode? {
        if (id == targetId) return this
        for (child in children) {
            val found = child.findById(targetId)
            if (found != null) return found
        }
        return null
    }
}
