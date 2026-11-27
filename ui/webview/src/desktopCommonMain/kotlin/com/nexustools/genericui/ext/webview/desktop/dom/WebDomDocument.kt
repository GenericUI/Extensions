package com.nexustools.genericui.ext.webview.desktop.dom

import com.nexustools.genericui.style.css.CssStyleSheet

/**
 * W3C DOM Document (`nodeType = 9`) managing document structure (`doctype`, `documentElement`,
 * `head`, `body`), element creation, selector queries, `document.styleSheets` CSSOM sheets,
 * cookies, active element focus, and mutation observation hooks.
 */
class WebDomDocument(
    var url: String = "about:blank",
    val allowStorage: Boolean = true
) : WebDomNode(WebDomNodeType.DOCUMENT_NODE, "#document", null) {

    init {
        ownerDocument = this
    }

    var doctype: WebDomDocumentType? = null
        internal set

    var documentElement: WebDomElement? = null
        internal set

    var readyState: String = "loading"
    var characterSet: String = "UTF-8"
    var compatMode: String = "CSS1Compat"
    var activeElement: WebDomElement? = null
    var currentScript: WebDomElement? = null
    var hidden: Boolean = false
    var visibilityState: String = "visible"

    val styleSheets: MutableList<WebCssStyleSheetModel> = ArrayList()
    internal val cookieStore: LinkedHashMap<String, String> = LinkedHashMap()

    var onMutation: ((WebDomMutationRecord) -> Unit)? = null
    var onDomChanged: (() -> Unit)? = null
    var onDynamicScriptInserted: ((WebDomElement) -> Unit)? = null
    var onElementConnected: ((WebDomElement) -> Unit)? = null
    var onElementDisconnected: ((WebDomElement) -> Unit)? = null
    var onAttributeChanged: ((WebDomElement, String, String?, String?) -> Unit)? = null
    var elementFromPointProvider: ((x: Double, y: Double) -> WebDomElement?)? = null
    var elementsFromPointProvider: ((x: Double, y: Double) -> List<WebDomElement>)? = null

    val head: WebDomElement?
        get() = documentElement?.children?.firstOrNull { it.lowerTagName == "head" }

    val body: WebDomElement?
        get() = documentElement?.children?.firstOrNull { it.lowerTagName == "body" }

    val scrollingElement: WebDomElement?
        get() = documentElement ?: body

    var title: String
        get() = head?.let { h ->
            h.children.firstOrNull { it.lowerTagName == "title" }?.textContent
        }.orEmpty()
        set(value) {
            val h = head ?: documentElement ?: return
            val existing = h.children.firstOrNull { it.lowerTagName == "title" }
            if (existing != null) {
                existing.textContent = value
            } else {
                val t = createElement("title")
                t.textContent = value
                h.appendChild(t)
            }
        }

    var cookieString: String
        get() = cookieStore.entries.joinToString("; ") { "${it.key}=${it.value}" }
        set(assignment) {
            val parts = assignment.split(';')
            val nv = parts.firstOrNull()?.trim().orEmpty()
            val eqIdx = nv.indexOf('=')
            if (eqIdx <= 0) return
            val name = nv.substring(0, eqIdx).trim()
            val value = nv.substring(eqIdx + 1).trim()
            var expired = false
            for (i in 1 until parts.size) {
                val attr = parts[i].trim().lowercase()
                if (attr.startsWith("max-age=")) {
                    val sec = attr.substringAfter("max-age=").trim().toLongOrNull()
                    if (sec != null && sec <= 0L) expired = true
                }
            }
            if (expired) {
                cookieStore.remove(name)
            } else {
                cookieStore[name] = value
            }
        }

    fun createElement(tagName: String): WebDomElement =
        WebDomElement(tagName.trim(), WebDomElement.HTML_NAMESPACE, this)

    fun createElementNS(namespaceURI: String?, qualifiedName: String): WebDomElement {
        val local = qualifiedName.substringAfter(':', qualifiedName)
        return WebDomElement(local.trim(), namespaceURI, this)
    }

    fun createTextNode(data: String): WebDomText = WebDomText(data, this)

    fun createComment(data: String): WebDomComment = WebDomComment(data, this)

    fun createDocumentFragment(): WebDomDocumentFragment = WebDomDocumentFragment(this)

    fun importNode(node: WebDomNode, deep: Boolean = false): WebDomNode {
        val cloned = node.cloneNode(deep)
        fun setDoc(n: WebDomNode) {
            n.ownerDocument = this
            for (c in n.childNodes) setDoc(c)
        }
        setDoc(cloned)
        return cloned
    }

    fun adoptNode(node: WebDomNode): WebDomNode {
        node.parentNode?.removeChild(node)
        fun setDoc(n: WebDomNode) {
            n.ownerDocument = this
            for (c in n.childNodes) setDoc(c)
        }
        setDoc(node)
        return node
    }

    fun getElementById(id: String): WebDomElement? {
        if (id.isEmpty()) return null
        return documentElement?.findDescendantById(id)
    }

    fun getElementsByTagName(tagName: String): List<WebDomElement> {
        val out = ArrayList<WebDomElement>()
        val rootEl = documentElement ?: return out
        val lower = tagName.lowercase()
        if (lower == "*" || rootEl.lowerTagName == lower) {
            out.add(rootEl)
        }
        rootEl.collectDescendantsByTagName(tagName, out)
        return out
    }

    fun getElementsByClassName(classNames: String): List<WebDomElement> {
        val tokens = classNames.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val out = ArrayList<WebDomElement>()
        val rootEl = documentElement ?: return out
        if (tokens.all { rootEl.classOrder.contains(it) }) {
            out.add(rootEl)
        }
        rootEl.collectDescendantsByClassName(tokens, out)
        return out
    }

    fun getElementsByName(name: String): List<WebDomElement> {
        val out = ArrayList<WebDomElement>()
        fun walk(n: WebDomNode) {
            for (c in n.childNodes) {
                if (c is WebDomElement) {
                    if (c.getAttribute("name") == name) out.add(c)
                    walk(c)
                }
            }
        }
        documentElement?.let { walk(it) }
        return out
    }

    fun elementFromPoint(x: Double, y: Double): WebDomElement? =
        elementFromPointProvider?.invoke(x, y)

    fun elementsFromPoint(x: Double, y: Double): List<WebDomElement> =
        elementsFromPointProvider?.invoke(x, y).orEmpty()

    fun getDomain(): String {
        val clean = url.trim()
        if (!clean.contains("://")) return ""
        return clean.substringAfter("://").substringBefore('/').substringBefore(':')
    }

    internal fun recordMutation(record: WebDomMutationRecord) {
        onMutation?.invoke(record)
    }

    internal fun notifyDomChanged() {
        onDomChanged?.invoke()
    }

    internal fun notifyElementConnectedSubtree(node: WebDomNode) {
        val cb = onElementConnected ?: return
        fun walk(n: WebDomNode) {
            if (n is WebDomElement) {
                cb(n)
                n.shadowRoot?.let { sr ->
                    for (sc in sr.childNodes.toList()) walk(sc)
                }
            }
            for (c in n.childNodes.toList()) walk(c)
        }
        walk(node)
    }

    internal fun notifyElementDisconnectedSubtree(node: WebDomNode) {
        val cb = onElementDisconnected ?: return
        fun walk(n: WebDomNode) {
            if (n is WebDomElement) {
                cb(n)
                n.shadowRoot?.let { sr ->
                    for (sc in sr.childNodes.toList()) walk(sc)
                }
            }
            for (c in n.childNodes.toList()) walk(c)
        }
        walk(node)
    }

    fun rebuildDocumentRoots(
        newDoctype: WebDomDocumentType?,
        newHtml: WebDomElement
    ) {
        for (c in childNodes.toList()) {
            c.parentNode = null
        }
        childNodes.clear()
        doctype = newDoctype
        if (newDoctype != null) {
            newDoctype.ownerDocument = this
            newDoctype.parentNode = this
            childNodes.add(newDoctype)
        }
        newHtml.ownerDocument = this
        newHtml.parentNode = this
        childNodes.add(newHtml)
        documentElement = newHtml
    }

    override fun cloneShallow(doc: WebDomDocument?): WebDomNode = WebDomDocument(url, allowStorage)
}
