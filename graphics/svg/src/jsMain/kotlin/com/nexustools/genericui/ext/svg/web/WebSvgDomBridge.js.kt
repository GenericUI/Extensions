package com.nexustools.genericui.ext.svg.web

private const val SVG_NAMESPACE = "http://www.w3.org/2000/svg"

private var nextHandleId: Int = 1
private val nodeMap = HashMap<Int, dynamic>()

private fun hasDom(): Boolean =
    js("typeof window !== 'undefined' && window !== null && typeof document !== 'undefined' && document !== null") as Boolean

private fun extractCoords(svgRootEl: dynamic, el: dynamic, ev: dynamic): Array<Float> {
    val cx = if (ev != null && js("typeof ev.clientX === 'number'") as Boolean) (ev.clientX as Number).toFloat() else 0f
    val cy = if (ev != null && js("typeof ev.clientY === 'number'") as Boolean) (ev.clientY as Number).toFloat() else 0f
    val target = svgRootEl ?: el
    var lx = cx
    var ly = cy
    if (target != null && js("typeof target.getBoundingClientRect === 'function'") as Boolean) {
        val rect = target.getBoundingClientRect()
        lx = cx - (rect.left as Number).toFloat()
        ly = cy - (rect.top as Number).toFloat()
    }
    return arrayOf(lx, ly, cx, cy)
}

internal actual object WebSvgDomBridge {

    actual fun isBrowserEnvironment(): Boolean = hasDom()

    actual fun createSvgNode(tagName: String): Int {
        if (!hasDom()) return 0
        val doc: dynamic = js("document")
        val el: dynamic = doc.createElementNS(SVG_NAMESPACE, tagName)
        val id = nextHandleId++
        nodeMap[id] = el
        return id
    }

    actual fun mountSvgRoot(hostPeer: Any?, svgRootHandle: Int): Boolean {
        if (!hasDom() || hostPeer == null) return false
        val rootEl = nodeMap[svgRootHandle] ?: return false
        val host = hostPeer.asDynamic()
        if (rootEl.parentNode !== host) {
            host.appendChild(rootEl)
        }
        return true
    }

    actual fun unmountSvgRoot(hostPeer: Any?, svgRootHandle: Int) {
        if (!hasDom()) return
        val rootEl = nodeMap[svgRootHandle] ?: return
        val parent = rootEl.parentNode
        if (parent != null) {
            parent.removeChild(rootEl)
        }
    }

    actual fun releaseHandle(handle: Int) {
        nodeMap.remove(handle)
    }

    actual fun clearNodeAttributes(handle: Int) {
        val el = nodeMap[handle] ?: return
        val attrs = el.attributes ?: return
        while ((attrs.length as Int) > 0) {
            el.removeAttribute(attrs[0].name)
        }
    }

    actual fun setAttribute(handle: Int, name: String, value: String) {
        val el = nodeMap[handle] ?: return
        el.setAttribute(name, value)
    }

    actual fun setStyleProperty(handle: Int, name: String, value: String) {
        val el = nodeMap[handle] ?: return
        el.style?.setProperty(name, value)
    }

    actual fun removeStyleProperty(handle: Int, name: String) {
        val el = nodeMap[handle] ?: return
        el.style?.removeProperty(name)
    }

    actual fun setTextContent(handle: Int, text: String) {
        val el = nodeMap[handle] ?: return
        el.textContent = text
    }

    actual fun clearChildren(handle: Int) {
        val el = nodeMap[handle] ?: return
        while (el.firstChild != null) {
            el.removeChild(el.firstChild)
        }
    }

    actual fun appendChild(parentHandle: Int, childHandle: Int) {
        val parent = nodeMap[parentHandle] ?: return
        val child = nodeMap[childHandle] ?: return
        parent.appendChild(child)
    }

    actual fun attachElementListeners(
        handle: Int,
        svgRootHandle: Int,
        onClick: () -> Unit,
        onPointerEnter: (Float, Float, Float, Float) -> Unit,
        onPointerLeave: (Float, Float, Float, Float) -> Unit
    ) {
        val el = nodeMap[handle] ?: return
        val rootEl = nodeMap[svgRootHandle]
        el.addEventListener("click") { _: dynamic ->
            onClick()
        }
        el.addEventListener("mouseenter") { ev: dynamic ->
            val c = extractCoords(rootEl, el, ev)
            onPointerEnter(c[0], c[1], c[2], c[3])
        }
        el.addEventListener("mouseleave") { ev: dynamic ->
            val c = extractCoords(rootEl, el, ev)
            onPointerLeave(c[0], c[1], c[2], c[3])
        }
    }
}
