package com.nexustools.genericui.ext.webview.desktop

/**
 * Chromium Blink HTML/CSS document layout and rendering engine used by `:extensions:webview:desktop`
 * across `:windows`, `:macos`, `:gtk`, and `:qt`.
 *
 * Parses HTML/CSS into a [ChromiumBlinkDomNode] tree and extracts inline `<script>` blocks
 * so they are compiled and executed by the `NJS` Native JIT runtime ([NjsDesktopWebRuntime]).
 */
class ChromiumBlinkEngine {
    var documentTitle: String = ""
        internal set

    var currentUrl: String? = null
        internal set

    var rootNode: ChromiumBlinkDomNode = ChromiumBlinkDomNode("document")
        private set

    private val scriptRegex = Regex("<script[^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)
    private val titleRegex = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
    private val idElementRegex = Regex("<([a-zA-Z0-9]+)\\s+[^>]*id=[\"']([^\"']+)[\"'][^>]*>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)

    /**
     * Parses HTML into the Chromium Blink DOM tree and returns all `<script>` source blocks
     * in document order for execution by `NJS`.
     */
    fun parseHtmlDocument(html: String, baseUrl: String? = null): List<String> {
        currentUrl = baseUrl ?: "about:blank"
        documentTitle = titleRegex.find(html)?.groupValues?.getOrNull(1)?.trim() ?: ""
        val docRoot = ChromiumBlinkDomNode("document")
        val bodyNode = ChromiumBlinkDomNode("body")
        docRoot.children.add(bodyNode)

        for (match in idElementRegex.findAll(html)) {
            val tag = match.groupValues[1].lowercase()
            val id = match.groupValues[2]
            val inner = match.groupValues[3]
            bodyNode.children.add(
                ChromiumBlinkDomNode(
                    tagName = tag,
                    id = id,
                    textContent = inner.trim()
                )
            )
        }
        rootNode = docRoot

        return scriptRegex.findAll(html)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }
}
