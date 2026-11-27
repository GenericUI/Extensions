package com.nexustools.genericui.ext.webview.desktop.dom

/**
 * W3C ShadowRoot node encapsulating a shadow DOM subtree attached to a host [WebDomElement].
 */
class WebDomShadowRoot(
    val host: WebDomElement,
    val mode: String,
    val delegatesFocus: Boolean = false,
    ownerDoc: WebDomDocument? = host.ownerDocument
) : WebDomDocumentFragment(ownerDoc) {
    val scopeId: String = "shadow-${host.elementUid}"

    var innerHTML: String
        get() = serializeInnerHtml()
        set(value) {
            val doc = ownerDocument ?: host.ownerDocument
            for (c in childNodes.toList()) {
                removeChild(c)
            }
            if (doc != null && value.isNotEmpty()) {
                val nodes = WebHtml5Parser.parseFragment(value, host, doc)
                for (n in nodes) {
                    appendChild(n)
                }
            }
        }

    fun getSlotAssignments(): Map<String, List<WebDomNode>> {
        val map = LinkedHashMap<String, MutableList<WebDomNode>>()
        for (lightChild in host.childNodes) {
            val slotName = (lightChild as? WebDomElement)?.getAttribute("slot").orEmpty()
            map.getOrPut(slotName) { ArrayList() }.add(lightChild)
        }
        return map
    }

    fun assignedNodesForSlot(slotEl: WebDomElement, flatten: Boolean = false): List<WebDomNode> {
        val name = slotEl.getAttribute("name").orEmpty()
        val matched = ArrayList<WebDomNode>()
        for (lightChild in host.childNodes) {
            val childSlotName = (lightChild as? WebDomElement)?.getAttribute("slot").orEmpty()
            if (childSlotName == name) {
                if (flatten && lightChild is WebDomElement && lightChild.lowerTagName == "slot") {
                    matched.addAll(findFallbackOrAssignedNodes(lightChild, flatten = true))
                } else {
                    matched.add(lightChild)
                }
            }
        }
        return if (matched.isEmpty() && flatten) {
            slotEl.childNodes.toList()
        } else {
            matched
        }
    }

    private fun findFallbackOrAssignedNodes(slotEl: WebDomElement, flatten: Boolean): List<WebDomNode> {
        val hostShadow = (slotEl.getRootNode() as? WebDomShadowRoot)
        if (hostShadow != null && hostShadow !== this) {
            val inner = hostShadow.assignedNodesForSlot(slotEl, flatten)
            if (inner.isNotEmpty()) return inner
        }
        return slotEl.childNodes.toList()
    }

    override fun cloneShallow(doc: WebDomDocument?): WebDomNode =
        throw UnsupportedOperationException("ShadowRoot cannot be cloned directly")
}
