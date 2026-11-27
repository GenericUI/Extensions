package com.nexustools.genericui.ext.webview.desktop.dom

/**
 * W3C DOM DocumentFragment node (`nodeType = 11`).
 */
open class WebDomDocumentFragment(
    ownerDoc: WebDomDocument? = null
) : WebDomNode(WebDomNodeType.DOCUMENT_FRAGMENT_NODE, "#document-fragment", ownerDoc) {

    fun getElementById(id: String): WebDomElement? {
        for (c in childNodes) {
            if (c is WebDomElement) {
                val found = c.findDescendantById(id)
                if (found != null) return found
            }
        }
        return null
    }

    override fun cloneShallow(doc: WebDomDocument?): WebDomNode = WebDomDocumentFragment(doc)
}
