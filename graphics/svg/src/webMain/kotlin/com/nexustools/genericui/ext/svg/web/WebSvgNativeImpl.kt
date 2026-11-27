package com.nexustools.genericui.ext.svg.web

import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.ext.svg.*
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.web.WebBaseNativeImpl
import com.nexustools.genericui.web.WebDomElement

/**
 * Web native implementation of [SvgNativeImpl].
 *
 * - In a live browser (`js` / `wasmJs` DOM), creates genuine namespace-qualified SVG elements via
 *   `document.createElementNS("http://www.w3.org/2000/svg", ...)` through [WebSvgDomBridge],
 *   syncs root/defs/gradient/clip/shape/text attributes, and attaches DOM `click`, `mouseenter`,
 *   and `mouseleave` listeners to each vector node.
 * - In headless `:web` test environments without a browser DOM, mirrors the complete `<svg>` DOM
 *   subtree on [svgRootElement] (attached to [nativeWidget]) with simulated click/pointer dispatch
 *   so `toOuterHtml()`, attribute checks, and DOM interaction tests run deterministically.
 */
class WebSvgNativeImpl(
    val svgComponent: Svg
) : WebBaseNativeImpl("genericui:svg", "svg"), SvgNativeImpl<WebDomElement> {

    val svgRootElement: WebDomElement = WebDomElement("svg")

    private val isBrowser: Boolean = WebSvgDomBridge.isBrowserEnvironment()
    private var liveRootHandle: Int = 0
    private val liveChildHandles = ArrayList<Int>()

    private var lastSyncedVersion: Long = -1L
    private var dirty: Boolean = true
    private var preferredSizeVal: Size? = null
    private var componentClickListener: (() -> Unit)? = null

    init {
        nativeWidget.setStyle("display", "inline-block")
        if (isBrowser) {
            liveRootHandle = WebSvgDomBridge.createSvgNode("svg")
            if (liveRootHandle > 0) {
                WebSvgDomBridge.setStyleProperty(liveRootHandle, "display", "block")
                WebSvgDomBridge.mountSvgRoot(nativeWidget.nativeBrowserPeer, liveRootHandle)
            }
        } else {
            nativeWidget.appendChild(svgRootElement)
        }
    }

    override fun setPreferredSize(size: Size?) {
        preferredSizeVal = size
        super.setPreferredSize(size)
        syncRootSizingStyles()
    }

    override fun setOnClick(listener: (() -> Unit)?) {
        componentClickListener = listener
        super.setOnClick(listener)
    }

    override fun syncSvgScene(svg: Svg) {
        if (isDisposed) return
        if (!dirty && lastSyncedVersion == svg.sceneVersion) return
        lastSyncedVersion = svg.sceneVersion
        dirty = false

        if (isBrowser) {
            syncLiveBrowserSvg(svg)
        } else {
            syncHeadlessDomSvg(svg)
        }
    }

    override fun invalidateSvgScene() {
        dirty = true
        if (!isDisposed) {
            syncSvgScene(svgComponent)
        }
    }

    override fun hitTestViewport(viewportX: Float, viewportY: Float): SvgElement? {
        val bounds = getBounds()
        val intrinsic = svgComponent.resolveIntrinsicPixelSize()
        val vw = when {
            bounds.width > 0 -> bounds.width.toFloat()
            preferredSizeVal != null && preferredSizeVal!!.width > 0 -> preferredSizeVal!!.width.toFloat()
            else -> intrinsic.width.toFloat()
        }
        val vh = when {
            bounds.height > 0 -> bounds.height.toFloat()
            preferredSizeVal != null && preferredSizeVal!!.height > 0 -> preferredSizeVal!!.height.toFloat()
            else -> intrinsic.height.toFloat()
        }
        return svgComponent.hitTest(viewportX, viewportY, vw, vh)
    }

    fun findSvgDomById(id: String): WebDomElement? {
        val clean = SvgPaint.normalizeId(id)
        if (clean.isEmpty()) return null
        return findDomByIdRecursive(svgRootElement, clean)
    }

    private fun findDomByIdRecursive(node: WebDomElement, id: String): WebDomElement? {
        if (node.id == id) return node
        for (child in node.children) {
            findDomByIdRecursive(child, id)?.let { return it }
        }
        return null
    }

    private fun syncRootSizingStyles() {
        val explicitW = svgComponent.widthPx != null || (preferredSizeVal?.width ?: 0) > 0
        val explicitH = svgComponent.heightPx != null || (preferredSizeVal?.height ?: 0) > 0
        if (isBrowser && liveRootHandle > 0) {
            if (explicitW) {
                WebSvgDomBridge.setStyleProperty(liveRootHandle, "width", "100%")
            } else {
                WebSvgDomBridge.removeStyleProperty(liveRootHandle, "width")
            }
            if (explicitH) {
                WebSvgDomBridge.setStyleProperty(liveRootHandle, "height", "100%")
            } else {
                WebSvgDomBridge.removeStyleProperty(liveRootHandle, "height")
            }
        } else {
            svgRootElement.setStyle("display", "block")
            svgRootElement.setStyle("width", if (explicitW) "100%" else null)
            svgRootElement.setStyle("height", if (explicitH) "100%" else null)
        }
    }

    private fun syncLiveBrowserSvg(svg: Svg) {
        if (liveRootHandle <= 0) {
            liveRootHandle = WebSvgDomBridge.createSvgNode("svg")
        }
        if (liveRootHandle <= 0) return

        WebSvgDomBridge.mountSvgRoot(nativeWidget.nativeBrowserPeer, liveRootHandle)
        WebSvgDomBridge.clearChildren(liveRootHandle)
        for (oldHandle in liveChildHandles) {
            WebSvgDomBridge.releaseHandle(oldHandle)
        }
        liveChildHandles.clear()

        WebSvgDomBridge.clearNodeAttributes(liveRootHandle)
        for ((k, v) in SvgXmlSerializer.collectRootAttributes(svg)) {
            if (k != "id" && k != "class") {
                WebSvgDomBridge.setAttribute(liveRootHandle, k, v)
            }
        }
        WebSvgDomBridge.setStyleProperty(liveRootHandle, "display", "block")
        syncRootSizingStyles()

        if (svg.defs.children.isNotEmpty()) {
            val defsHandle = buildLiveNode(svg.defs)
            if (defsHandle > 0) {
                WebSvgDomBridge.appendChild(liveRootHandle, defsHandle)
            }
        }
        for (child in svg.children) {
            val childHandle = buildLiveNode(child)
            if (childHandle > 0) {
                WebSvgDomBridge.appendChild(liveRootHandle, childHandle)
            }
        }
    }

    private fun buildLiveNode(el: SvgElement): Int {
        val handle = WebSvgDomBridge.createSvgNode(el.tagName)
        if (handle <= 0) return 0
        liveChildHandles.add(handle)

        for ((k, v) in SvgXmlSerializer.collectElementAttributes(el)) {
            WebSvgDomBridge.setAttribute(handle, k, v)
        }
        if (el is SvgText) {
            WebSvgDomBridge.setTextContent(handle, el.text)
        }
        WebSvgDomBridge.attachElementListeners(
            handle = handle,
            svgRootHandle = liveRootHandle,
            onClick = { el.onClick?.invoke() },
            onPointerEnter = { lx, ly, sx, sy ->
                el.onPointerEnter?.invoke(PointerEvent(x = lx, y = ly, screenX = sx, screenY = sy))
            },
            onPointerLeave = { lx, ly, sx, sy ->
                el.onPointerLeave?.invoke(PointerEvent(x = lx, y = ly, screenX = sx, screenY = sy))
            }
        )

        when (el) {
            is SvgLinearGradient -> {
                for (stop in el.stops) {
                    val stopHandle = buildLiveStopNode(stop)
                    if (stopHandle > 0) WebSvgDomBridge.appendChild(handle, stopHandle)
                }
            }
            is SvgRadialGradient -> {
                for (stop in el.stops) {
                    val stopHandle = buildLiveStopNode(stop)
                    if (stopHandle > 0) WebSvgDomBridge.appendChild(handle, stopHandle)
                }
            }
            is SvgContainer -> {
                for (child in el.children) {
                    val childHandle = buildLiveNode(child)
                    if (childHandle > 0) WebSvgDomBridge.appendChild(handle, childHandle)
                }
            }
        }
        return handle
    }

    private fun buildLiveStopNode(stop: SvgGradientStop): Int {
        val stopHandle = WebSvgDomBridge.createSvgNode("stop")
        if (stopHandle <= 0) return 0
        liveChildHandles.add(stopHandle)
        for ((k, v) in SvgXmlSerializer.collectStopAttributes(stop)) {
            WebSvgDomBridge.setAttribute(stopHandle, k, v)
        }
        return stopHandle
    }

    private fun syncHeadlessDomSvg(svg: Svg) {
        if (svgRootElement.parent !== nativeWidget) {
            nativeWidget.appendChild(svgRootElement)
        }
        svgRootElement.id = ""
        svgRootElement.setClassName("")
        for (existingKey in svgRootElement.attributes.keys.toList()) {
            svgRootElement.removeAttribute(existingKey)
        }
        for ((k, v) in SvgXmlSerializer.collectRootAttributes(svg)) {
            if (k != "id" && k != "class") {
                svgRootElement.setAttribute(k, v)
            }
        }
        syncRootSizingStyles()

        svgRootElement.clearChildren()
        if (svg.defs.children.isNotEmpty()) {
            svgRootElement.appendChild(buildHeadlessDomNode(svg.defs))
        }
        for (child in svg.children) {
            svgRootElement.appendChild(buildHeadlessDomNode(child))
        }
    }

    private fun buildHeadlessDomNode(el: SvgElement): WebDomElement {
        val node = WebDomElement(el.tagName)
        for ((k, v) in SvgXmlSerializer.collectElementAttributes(el)) {
            when (k) {
                "id" -> node.id = v
                "class" -> node.setClassName(v)
                else -> node.setAttribute(k, v)
            }
        }
        if (el is SvgText) {
            node.textContent = el.text
        }
        node.addEventListener("click") {
            var cur: SvgElement? = el
            while (cur != null) {
                cur.onClick?.invoke()
                cur = cur.parentElement
            }
            componentClickListener?.invoke()
        }
        node.addEventListener("mouseenter") { payload ->
            val ev = (payload as? PointerEvent) ?: PointerEvent()
            el.onPointerEnter?.invoke(ev)
        }
        node.addEventListener("mouseleave") { payload ->
            val ev = (payload as? PointerEvent) ?: PointerEvent()
            el.onPointerLeave?.invoke(ev)
        }

        when (el) {
            is SvgLinearGradient -> {
                for (stop in el.stops) {
                    val stopNode = WebDomElement("stop")
                    for ((k, v) in SvgXmlSerializer.collectStopAttributes(stop)) {
                        stopNode.setAttribute(k, v)
                    }
                    node.appendChild(stopNode)
                }
            }
            is SvgRadialGradient -> {
                for (stop in el.stops) {
                    val stopNode = WebDomElement("stop")
                    for ((k, v) in SvgXmlSerializer.collectStopAttributes(stop)) {
                        stopNode.setAttribute(k, v)
                    }
                    node.appendChild(stopNode)
                }
            }
            is SvgContainer -> {
                for (child in el.children) {
                    node.appendChild(buildHeadlessDomNode(child))
                }
            }
        }
        return node
    }

    override fun dispose() {
        if (isDisposed) return
        if (isBrowser && liveRootHandle > 0) {
            WebSvgDomBridge.clearChildren(liveRootHandle)
            for (handle in liveChildHandles) {
                WebSvgDomBridge.releaseHandle(handle)
            }
            liveChildHandles.clear()
            WebSvgDomBridge.unmountSvgRoot(nativeWidget.nativeBrowserPeer, liveRootHandle)
            WebSvgDomBridge.releaseHandle(liveRootHandle)
            liveRootHandle = 0
        }
        svgRootElement.clearChildren()
        super.dispose()
    }
}
