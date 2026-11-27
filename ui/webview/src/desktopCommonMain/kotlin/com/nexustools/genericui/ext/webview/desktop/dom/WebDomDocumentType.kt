package com.nexustools.genericui.ext.webview.desktop.dom

/**
 * W3C DOM DocumentType node (`nodeType = 10`).
 */
class WebDomDocumentType(
    val name: String = "html",
    val publicId: String = "",
    val systemId: String = "",
    ownerDoc: WebDomDocument? = null
) : WebDomNode(WebDomNodeType.DOCUMENT_TYPE_NODE, name, ownerDoc) {

    override fun cloneShallow(doc: WebDomDocument?): WebDomNode =
        WebDomDocumentType(name, publicId, systemId, doc)

    override fun serializeOuterHtml(): String = "<!DOCTYPE $name>"
}
