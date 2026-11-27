package com.nexustools.genericui.ext.webview.desktop.dom

/**
 * Structured DOM mutation notification payload delivered to `MutationObserver` subscribers.
 */
data class WebDomMutationRecord(
    val type: String,
    val target: WebDomNode,
    val addedNodes: List<WebDomNode> = emptyList(),
    val removedNodes: List<WebDomNode> = emptyList(),
    val previousSibling: WebDomNode? = null,
    val nextSibling: WebDomNode? = null,
    val attributeName: String? = null,
    val oldValue: String? = null
)
