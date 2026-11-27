package com.nexustools.genericui.ext.webview.desktop.dom

import com.nexustools.genericui.ext.alien.AlienWidgetNode

/**
 * Base W3C DOM Node implementation bridging DOM tree structure, live mutation notifications,
 * serialization (`innerHTML`/`outerHTML`/`textContent`), and backing [AlienWidgetNode] association.
 */
abstract class WebDomNode(
    val nodeType: Int,
    open val nodeName: String,
    var ownerDocument: WebDomDocument? = null
) {
    var parentNode: WebDomNode? = null
        internal set

    val childNodes: MutableList<WebDomNode> = ArrayList()

    var alienNode: AlienWidgetNode? = null
        internal set

    var cachedLayoutX: Double = 0.0
    var cachedLayoutY: Double = 0.0
    var cachedLayoutWidth: Double = 0.0
    var cachedLayoutHeight: Double = 0.0

    open val parentElement: WebDomElement?
        get() = parentNode as? WebDomElement

    open val firstChild: WebDomNode?
        get() = childNodes.firstOrNull()

    open val lastChild: WebDomNode?
        get() = childNodes.lastOrNull()

    open val previousSibling: WebDomNode?
        get() {
            val siblings = parentNode?.childNodes ?: return null
            val idx = siblings.indexOf(this)
            return if (idx > 0) siblings[idx - 1] else null
        }

    open val nextSibling: WebDomNode?
        get() {
            val siblings = parentNode?.childNodes ?: return null
            val idx = siblings.indexOf(this)
            return if (idx >= 0 && idx + 1 < siblings.size) siblings[idx + 1] else null
        }

    open val children: List<WebDomElement>
        get() = childNodes.filterIsInstance<WebDomElement>()

    open val childElementCount: Int
        get() = childNodes.count { it is WebDomElement }

    open val firstElementChild: WebDomElement?
        get() = childNodes.firstOrNull { it is WebDomElement } as? WebDomElement

    open val lastElementChild: WebDomElement?
        get() = childNodes.lastOrNull { it is WebDomElement } as? WebDomElement

    open val previousElementSibling: WebDomElement?
        get() {
            val siblings = parentNode?.childNodes ?: return null
            var i = siblings.indexOf(this) - 1
            while (i >= 0) {
                val c = siblings[i]
                if (c is WebDomElement) return c
                i--
            }
            return null
        }

    open val nextElementSibling: WebDomElement?
        get() {
            val siblings = parentNode?.childNodes ?: return null
            var i = siblings.indexOf(this) + 1
            while (i < siblings.size) {
                val c = siblings[i]
                if (c is WebDomElement) return c
                i++
            }
            return null
        }

    open val isConnected: Boolean
        get() {
            var cur: WebDomNode? = this
            while (cur != null) {
                if (cur is WebDomDocument) return true
                cur = if (cur is WebDomShadowRoot) cur.host else cur.parentNode
            }
            return false
        }

    open var nodeValue: String?
        get() = null
        set(_) {}

    open var textContent: String
        get() = when (this) {
            is WebDomText -> data
            is WebDomComment -> data
            is WebDomDocumentType, is WebDomDocument -> ""
            else -> buildString {
                for (child in childNodes) {
                    if (child is WebDomComment) continue
                    append(child.textContent)
                }
            }
        }
        set(value) {
            if (this is WebDomText) {
                val old = data
                data = value
                ownerDocument?.recordMutation(
                    WebDomMutationRecord(type = "characterData", target = this, oldValue = old)
                )
                if (isConnected) ownerDocument?.notifyDomChanged()
                return
            }
            if (this is WebDomComment) {
                val old = data
                data = value
                ownerDocument?.recordMutation(
                    WebDomMutationRecord(type = "characterData", target = this, oldValue = old)
                )
                return
            }
            val removed = childNodes.toList()
            val wasConnected = isConnected
            for (c in removed) {
                c.parentNode = null
                if (wasConnected) ownerDocument?.notifyElementDisconnectedSubtree(c)
            }
            childNodes.clear()
            val added = if (value.isNotEmpty()) {
                val txt = WebDomText(value, ownerDocument ?: (this as? WebDomDocument))
                txt.parentNode = this
                childNodes.add(txt)
                listOf(txt)
            } else {
                emptyList()
            }
            if (removed.isNotEmpty() || added.isNotEmpty()) {
                ownerDocument?.recordMutation(
                    WebDomMutationRecord(
                        type = "childList",
                        target = this,
                        addedNodes = added,
                        removedNodes = removed
                    )
                )
            }
            if (isConnected) ownerDocument?.notifyDomChanged()
        }

    fun hasChildNodes(): Boolean = childNodes.isNotEmpty()

    fun contains(other: WebDomNode?): Boolean {
        var cur: WebDomNode? = other
        while (cur != null) {
            if (cur === this) return true
            cur = cur.parentNode
        }
        return false
    }

    open fun appendChild(child: WebDomNode): WebDomNode = insertBefore(child, null)

    open fun insertBefore(newNode: WebDomNode, referenceNode: WebDomNode?): WebDomNode {
        if (newNode === this || newNode.contains(this)) {
            throw IllegalArgumentException("HierarchyRequestError: The new child element contains the parent.")
        }
        if (referenceNode != null && referenceNode.parentNode !== this) {
            throw IllegalArgumentException("NotFoundError: Reference node is not a child of this node.")
        }
        if (newNode === referenceNode) {
            return newNode
        }
        if (newNode is WebDomDocumentFragment && newNode !is WebDomShadowRoot) {
            val fragmentChildren = newNode.childNodes.toList()
            if (fragmentChildren.isEmpty()) return newNode
            for (fc in fragmentChildren) {
                fc.parentNode = null
            }
            newNode.childNodes.clear()
            if (fragmentChildren.isNotEmpty()) {
                ownerDocument?.recordMutation(
                    WebDomMutationRecord(
                        type = "childList",
                        target = newNode,
                        removedNodes = fragmentChildren
                    )
                )
            }
            val insertIdx = if (referenceNode == null) childNodes.size else childNodes.indexOf(referenceNode)
            val prevSib = if (insertIdx > 0) childNodes[insertIdx - 1] else null
            val nextSib = referenceNode
            for ((offset, fc) in fragmentChildren.withIndex()) {
                fc.parentNode = this
                propagateOwnerDocument(fc, ownerDocument ?: (this as? WebDomDocument))
                childNodes.add(insertIdx + offset, fc)
            }
            ownerDocument?.recordMutation(
                WebDomMutationRecord(
                    type = "childList",
                    target = this,
                    addedNodes = fragmentChildren,
                    previousSibling = prevSib,
                    nextSibling = nextSib
                )
            )
            if (isConnected) {
                val doc = ownerDocument ?: (this as? WebDomDocument)
                doc?.notifyDomChanged()
                for (fc in fragmentChildren) {
                    doc?.notifyElementConnectedSubtree(fc)
                    notifyDynamicScriptSubtree(fc)
                }
            }
            return newNode
        }

        newNode.parentNode?.removeChild(newNode)
        val insertIdx = if (referenceNode == null) childNodes.size else childNodes.indexOf(referenceNode)
        val prevSib = if (insertIdx > 0) childNodes[insertIdx - 1] else null
        newNode.parentNode = this
        propagateOwnerDocument(newNode, ownerDocument ?: (this as? WebDomDocument))
        childNodes.add(insertIdx, newNode)
        ownerDocument?.recordMutation(
            WebDomMutationRecord(
                type = "childList",
                target = this,
                addedNodes = listOf(newNode),
                previousSibling = prevSib,
                nextSibling = referenceNode
            )
        )
        if (isConnected) {
            val doc = ownerDocument ?: (this as? WebDomDocument)
            doc?.notifyDomChanged()
            doc?.notifyElementConnectedSubtree(newNode)
            notifyDynamicScriptSubtree(newNode)
        }
        return newNode
    }

    open fun removeChild(child: WebDomNode): WebDomNode {
        val idx = childNodes.indexOf(child)
        if (idx < 0) {
            throw IllegalArgumentException("NotFoundError: Node to be removed is not a child of this node.")
        }
        val wasConnected = isConnected
        val prevSib = if (idx > 0) childNodes[idx - 1] else null
        val nextSib = if (idx + 1 < childNodes.size) childNodes[idx + 1] else null
        childNodes.removeAt(idx)
        child.parentNode = null
        ownerDocument?.recordMutation(
            WebDomMutationRecord(
                type = "childList",
                target = this,
                removedNodes = listOf(child),
                previousSibling = prevSib,
                nextSibling = nextSib
            )
        )
        if (wasConnected) {
            ownerDocument?.notifyElementDisconnectedSubtree(child)
            ownerDocument?.notifyDomChanged()
        }
        return child
    }

    open fun replaceChild(newChild: WebDomNode, oldChild: WebDomNode): WebDomNode {
        if (oldChild.parentNode !== this) {
            throw IllegalArgumentException("NotFoundError: Child to be replaced is not a child of this node.")
        }
        if (newChild === oldChild) return oldChild
        val next = oldChild.nextSibling
        removeChild(oldChild)
        insertBefore(newChild, next)
        return oldChild
    }

    fun remove() {
        parentNode?.removeChild(this)
    }

    fun replaceWith(vararg nodes: WebDomNode) {
        val p = parentNode ?: return
        val next = nextSibling
        p.removeChild(this)
        for (n in nodes) {
            p.insertBefore(n, next)
        }
    }

    fun append(vararg nodes: WebDomNode) {
        for (n in nodes) {
            appendChild(n)
        }
    }

    fun prepend(vararg nodes: WebDomNode) {
        val first = firstChild
        for (n in nodes) {
            insertBefore(n, first)
        }
    }

    fun before(vararg nodes: WebDomNode) {
        val p = parentNode ?: return
        for (n in nodes) {
            p.insertBefore(n, this)
        }
    }

    fun after(vararg nodes: WebDomNode) {
        val p = parentNode ?: return
        val next = nextSibling
        for (n in nodes) {
            p.insertBefore(n, next)
        }
    }

    fun normalize() {
        var i = 0
        while (i < childNodes.size) {
            val child = childNodes[i]
            if (child is WebDomText) {
                if (child.data.isEmpty()) {
                    removeChild(child)
                    continue
                }
                while (i + 1 < childNodes.size && childNodes[i + 1] is WebDomText) {
                    val nextText = childNodes[i + 1] as WebDomText
                    child.data += nextText.data
                    removeChild(nextText)
                }
            } else {
                child.normalize()
            }
            i++
        }
    }

    fun cloneNode(deep: Boolean = false): WebDomNode {
        val copy = cloneShallow(ownerDocument)
        if (deep) {
            for (child in childNodes) {
                val clonedChild = child.cloneNode(true)
                copy.childNodes.add(clonedChild)
                clonedChild.parentNode = copy
            }
            if (this is WebDomElement && copy is WebDomElement && isTemplateTag) {
                val targetContent = copy.templateContent
                for (tc in templateContent.childNodes) {
                    val clonedTc = tc.cloneNode(true)
                    targetContent.childNodes.add(clonedTc)
                    clonedTc.parentNode = targetContent
                }
            }
        }
        return copy
    }

    protected abstract fun cloneShallow(doc: WebDomDocument?): WebDomNode

    fun compareDocumentPosition(other: WebDomNode): Int {
        if (other === this) return 0
        val myRoot = findRoot()
        val otherRoot = other.findRoot()
        if (myRoot !== otherRoot) return 1 or 32
        if (this.contains(other)) return 4 or 16
        if (other.contains(this)) return 2 or 8
        val all = ArrayList<WebDomNode>()
        collectPreOrder(myRoot, all)
        val myIdx = all.indexOf(this)
        val otherIdx = all.indexOf(other)
        return if (myIdx < otherIdx) 4 else 2
    }

    fun querySelector(selectors: String): WebDomElement? =
        WebDomSelectorMatcher.querySelector(this, selectors)

    fun querySelectorAll(selectors: String): List<WebDomElement> =
        WebDomSelectorMatcher.querySelectorAll(this, selectors)

    fun getRootNode(composed: Boolean = false): WebDomNode {
        var cur: WebDomNode = this
        while (true) {
            val next = if (cur is WebDomShadowRoot && composed) cur.host else cur.parentNode
            if (next == null) return cur
            cur = next
        }
    }

    internal fun serializeInnerHtml(): String = buildString {
        for (c in childNodes) {
            append(c.serializeOuterHtml())
        }
    }

    internal open fun serializeOuterHtml(): String = serializeInnerHtml()

    private fun findRoot(): WebDomNode {
        var cur: WebDomNode = this
        while (cur.parentNode != null) {
            cur = cur.parentNode!!
        }
        return cur
    }

    private fun collectPreOrder(node: WebDomNode, out: MutableList<WebDomNode>) {
        out.add(node)
        for (c in node.childNodes) {
            collectPreOrder(c, out)
        }
    }

    private fun propagateOwnerDocument(node: WebDomNode, doc: WebDomDocument?) {
        if (doc == null) return
        node.ownerDocument = doc
        for (c in node.childNodes) {
            propagateOwnerDocument(c, doc)
        }
    }

    private fun notifyDynamicScriptSubtree(node: WebDomNode) {
        val doc = ownerDocument ?: return
        if (node is WebDomElement && node.lowerTagName == "script") {
            doc.onDynamicScriptInserted?.invoke(node)
        } else {
            for (c in node.childNodes.toList()) {
                notifyDynamicScriptSubtree(c)
            }
        }
    }
}
