package com.nexustools.genericui.ext.webview.desktop.dom

/**
 * W3C DOM Comment node (`nodeType = 8`).
 */
class WebDomComment(
    var data: String = "",
    ownerDoc: WebDomDocument? = null
) : WebDomNode(WebDomNodeType.COMMENT_NODE, "#comment", ownerDoc) {

    override var nodeValue: String?
        get() = data
        set(value) {
            textContent = value.orEmpty()
        }

    val length: Int
        get() = data.length

    override fun cloneShallow(doc: WebDomDocument?): WebDomNode = WebDomComment(data, doc)

    override fun serializeOuterHtml(): String = "<!--$data-->"
}
