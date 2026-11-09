package com.nexustools.genericui.ext.webview.desktop

/**
 * Chromium Blink HTML/CSS document layout and rendering engine used by `:extensions:webview:desktop`
 * across `:windows`, `:macos`, `:gtk`, and `:qt`.
 *
 * Parses HTML/CSS into a [ChromiumBlinkDomNode] tree and extracts inline and external `<script>`
 * blocks so they are compiled and executed by the `NJS` Native JIT runtime ([NjsDesktopWebRuntime]).
 */
class ChromiumBlinkEngine {
    var documentTitle: String = ""

    var currentUrl: String? = null

    var readyState: String = "complete"

    var rootNode: ChromiumBlinkDomNode = ChromiumBlinkDomNode("html")
        private set

    var headNode: ChromiumBlinkDomNode = ChromiumBlinkDomNode("head")
        private set

    var bodyNode: ChromiumBlinkDomNode = ChromiumBlinkDomNode("body")
        private set

    init {
        rootNode.appendChild(headNode)
        rootNode.appendChild(bodyNode)
    }

    private val titleRegex = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
    private val linkRegex = Regex("<link\\b([^>]*?)/?>", RegexOption.IGNORE_CASE)
    private val scriptTagRegex = Regex("<script\\b([^>]*)>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)
    private val attrPairRegex = Regex("([a-zA-Z0-9_:-]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>/]+)))?")
    private val elementRegex = Regex(
        "<([a-zA-Z0-9:_-]+)\\b([^>]*?)>([\\s\\S]*?)</\\1>",
        RegexOption.IGNORE_CASE
    )
    private val voidElementRegex = Regex(
        "<(input|img|br|hr|meta|source|track)\\b([^>]*?)/?>",
        RegexOption.IGNORE_CASE
    )

    private fun parseAttributes(attrText: String): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()
        for (match in attrPairRegex.findAll(attrText)) {
            val key = match.groupValues[1].trim()
            if (key.isEmpty()) continue
            val value = when {
                match.groups[2] != null -> match.groupValues[2]
                match.groups[3] != null -> match.groupValues[3]
                match.groups[4] != null -> match.groupValues[4]
                else -> ""
            }
            map[key] = value
        }
        return map
    }

    /**
     * Parses HTML into the Chromium Blink DOM tree and returns all inline `<script>` source blocks
     * in document order for execution by `NJS`.
     */
    fun parseHtmlDocument(html: String, baseUrl: String? = null): List<String> =
        parseHtmlDocumentWithScripts(html, baseUrl)
            .filter { it.isExecutableJavaScript && it.inlineCode.isNotEmpty() }
            .map { it.inlineCode }

    /**
     * Parses HTML into the Chromium Blink DOM tree (`html` -> `head` + `body`) and returns
     * all executable [ChromiumBlinkScriptEntry] items (both external `src` and inline scripts)
     * in document order while preserving inert `<script type="loader-js">` / `<script type="loader-wasm">`
     * and `<link rel="resources">` elements in the DOM tree.
     */
    fun parseHtmlDocumentWithScripts(html: String, baseUrl: String? = null): List<ChromiumBlinkScriptEntry> {
        currentUrl = baseUrl ?: "about:blank"
        readyState = "loading"
        documentTitle = titleRegex.find(html)?.groupValues?.getOrNull(1)?.trim() ?: ""

        val htmlRoot = ChromiumBlinkDomNode("html")
        val newHead = ChromiumBlinkDomNode("head")
        val newBody = ChromiumBlinkDomNode("body")
        htmlRoot.appendChild(newHead)
        htmlRoot.appendChild(newBody)

        if (documentTitle.isNotEmpty()) {
            val titleNode = ChromiumBlinkDomNode("title", textContent = documentTitle)
            newHead.appendChild(titleNode)
        }

        // Parse <link ...> elements into <head>
        for (match in linkRegex.findAll(html)) {
            val attrs = parseAttributes(match.groupValues[1])
            val linkNode = ChromiumBlinkDomNode(
                tagName = "link",
                id = attrs["id"] ?: "",
                attributes = attrs
            )
            newHead.appendChild(linkNode)
        }

        // Parse <script ...>...</script> elements into DOM and collect executable scripts
        val executableScripts = ArrayList<ChromiumBlinkScriptEntry>()
        for (match in scriptTagRegex.findAll(html)) {
            val attrs = parseAttributes(match.groupValues[1])
            val bodyCode = match.groupValues[2].trim()
            val scriptType = (attrs["type"] ?: "").trim()
            val scriptSrc = attrs["src"]?.trim()?.takeIf { it.isNotEmpty() }
            val dataWasm = attrs["data-wasm"]?.trim()?.takeIf { it.isNotEmpty() }
            val scriptNode = ChromiumBlinkDomNode(
                tagName = "script",
                id = attrs["id"] ?: "",
                attributes = attrs,
                textContent = bodyCode
            )
            newHead.appendChild(scriptNode)

            val entry = ChromiumBlinkScriptEntry(
                src = scriptSrc,
                inlineCode = bodyCode,
                type = scriptType.ifEmpty { "text/javascript" },
                dataWasm = dataWasm
            )
            if (entry.isExecutableJavaScript && (entry.src != null || entry.inlineCode.isNotEmpty())) {
                executableScripts.add(entry)
            }
        }

        // Extract body region if present, otherwise scan whole HTML excluding <head> and <script>
        val bodyContentRegex = Regex("<body\\b[^>]*>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE)
        val rawBodyHtml = bodyContentRegex.find(html)?.groupValues?.getOrNull(1) ?: html
        val bodyWithoutScripts = rawBodyHtml
            .replace(scriptTagRegex, "")
            .replace(titleRegex, "")
            .replace(linkRegex, "")

        populateContainerFromHtmlFragment(newBody, bodyWithoutScripts)

        // Ensure <genericui:app> exists if present in HTML even as empty tag
        if (html.contains("<genericui:app", ignoreCase = true) &&
            htmlRoot.findBySelector("genericui:app") == null
        ) {
            newBody.appendChild(ChromiumBlinkDomNode("genericui:app"))
        }

        rootNode = htmlRoot
        headNode = newHead
        bodyNode = newBody
        readyState = "complete"
        return executableScripts
    }

    internal fun populateContainerFromHtmlFragment(parent: ChromiumBlinkDomNode, htmlFragment: String) {
        val ignoredStructuralTags = setOf("html", "head", "body", "script", "title", "link", "style")
        for (match in elementRegex.findAll(htmlFragment)) {
            val tag = match.groupValues[1].trim()
            if (tag.lowercase() in ignoredStructuralTags) continue
            val attrs = parseAttributes(match.groupValues[2])
            val inner = match.groupValues[3]
            val plainText = inner.replace(Regex("<[^>]+>"), "").trim()
            val node = ChromiumBlinkDomNode(
                tagName = tag.lowercase(),
                id = attrs["id"] ?: "",
                attributes = attrs,
                textContent = plainText
            ).apply {
                innerHTML = inner.trim()
            }
            parent.appendChild(node)
            if (inner.contains('<')) {
                populateContainerFromHtmlFragment(node, inner)
            }
        }
        for (match in voidElementRegex.findAll(htmlFragment)) {
            val tag = match.groupValues[1].trim().lowercase()
            val attrs = parseAttributes(match.groupValues[2])
            val id = attrs["id"] ?: ""
            if (id.isNotEmpty() && parent.findById(id) != null) continue
            val node = ChromiumBlinkDomNode(
                tagName = tag,
                id = id,
                attributes = attrs
            )
            parent.appendChild(node)
        }
    }
}
