package com.nexustools.genericui.ext.webview.desktop.dom

/**
 * W3C DOM Text node (`nodeType = 3`).
 */
class WebDomText(
    var data: String = "",
    ownerDoc: WebDomDocument? = null
) : WebDomNode(WebDomNodeType.TEXT_NODE, "#text", ownerDoc) {

    override var nodeValue: String?
        get() = data
        set(value) {
            textContent = value.orEmpty()
        }

    val length: Int
        get() = data.length

    val wholeText: String
        get() = data

    fun splitText(offset: Int): WebDomText {
        val safe = offset.coerceIn(0, data.length)
        val rest = data.substring(safe)
        data = data.substring(0, safe)
        val newText = WebDomText(rest, ownerDocument)
        parentNode?.insertBefore(newText, nextSibling)
        return newText
    }

    override fun cloneShallow(doc: WebDomDocument?): WebDomNode = WebDomText(data, doc)

    override fun serializeOuterHtml(): String =
        data.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
