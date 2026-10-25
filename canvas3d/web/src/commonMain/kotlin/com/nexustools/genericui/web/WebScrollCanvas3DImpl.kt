package com.nexustools.genericui.web

import com.nexustools.genericui.ext.gpu.GLContext
import com.nexustools.genericui.ext.gpu.VKContext
import com.nexustools.genericui.ext.gpu.web.WebVBlankSurfaceSynchronizer
import com.nexustools.genericui.nativeimpl.media.ScrollCanvas3DNativeImpl
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * Web `<genericui:scrollcanvas3d>` implementation of [ScrollCanvas3DNativeImpl].
 *
 * Renders:
 * - Outer `<genericui:scrollcanvas3d>` (`nativeWidget`) with `position: relative` and `overflow: auto`.
 * - Inner `<div>` ([contentSizerDiv]) with `width: ${contentWidth}px; height: ${contentHeight}px;`
 *   so native browser scrollbars reflect the virtual 3D document/surface size.
 * - An HTML `<canvas>` ([canvasElement]) overtop the `<div>` with
 *   `position: absolute; left: 0; top: 0; width: 0; height: 0;` whose WebGPU/WebGL2 swapchain
 *   only allocates the visible viewport (`viewportWidth` x `viewportHeight`).
 */
class WebScrollCanvas3DImpl(
    initialViewportWidth: Int = 800,
    initialViewportHeight: Int = 600,
    initialContentWidth: Int = initialViewportWidth,
    initialContentHeight: Int = initialViewportHeight,
    val preferVulkan: Boolean = true
) : WebBaseNativeImpl("genericui:scrollcanvas3d", "scroll-canvas-3d"), ScrollCanvas3DNativeImpl<WebDomElement, Any> {

    override val isWebScrollContainer: Boolean = true

    private var viewportW: Int = initialViewportWidth.coerceAtLeast(1)
    private var viewportH: Int = initialViewportHeight.coerceAtLeast(1)
    private var contentW: Int = initialContentWidth.coerceAtLeast(1)
    private var contentH: Int = initialContentHeight.coerceAtLeast(1)
    private var currentScrollX: Int = 0
    private var currentScrollY: Int = 0
    private var onScrollListener: ((Int, Int) -> Unit)? = null

    /**
     * Inner `<div>` sized to `(contentWidth, contentHeight)` to establish the scrollable bounds.
     */
    val contentSizerDiv: WebDomElement = WebDomElement("div")

    /**
     * Alias for [contentSizerDiv].
     */
    val spacerDiv: WebDomElement
        get() = contentSizerDiv

    /**
     * Viewport-sized HTML `<canvas>` positioned overtop [contentSizerDiv] with
     * `position: absolute; left: 0; top: 0; width: 0; height: 0;`.
     */
    val canvasElement: WebDomElement = WebDomElement("canvas")

    val glContext: WebGLContextImpl = WebGLContextImpl()
    val vkContext: WebVKContextImpl = WebVKContextImpl(
        swapchainWidth = viewportW,
        swapchainHeight = viewportH
    )

    private val monotonicClock = TimeSource.Monotonic.markNow()
    private var vblankLoopActive: Boolean = false
    private var vblankFrameCallback: ((Double?) -> Unit)? = null

    init {
        WebVBlankSurfaceSynchronizer.install()

        nativeWidget.style["position"] = "relative"
        nativeWidget.style["overflow"] = "auto"
        nativeWidget.clientWidth = viewportW
        nativeWidget.clientHeight = viewportH
        nativeWidget.scrollWidth = contentW
        nativeWidget.scrollHeight = contentH

        contentSizerDiv.style["width"] = "${contentW}px"
        contentSizerDiv.style["height"] = "${contentH}px"

        canvasElement.style["position"] = "absolute"
        canvasElement.style["left"] = "0"
        canvasElement.style["top"] = "0"
        canvasElement.style["width"] = "0"
        canvasElement.style["height"] = "0"
        canvasElement.setAttribute("width", viewportW.toString())
        canvasElement.setAttribute("height", viewportH.toString())
        canvasElement.clientWidth = viewportW
        canvasElement.clientHeight = viewportH

        nativeWidget.appendChild(contentSizerDiv)
        nativeWidget.appendChild(canvasElement)

        glContext.viewport(0, 0, viewportW, viewportH)

        nativeWidget.addEventListener("scroll") {
            val maxX = (contentW - viewportW).coerceAtLeast(0)
            val maxY = (contentH - viewportH).coerceAtLeast(0)
            val nextX = nativeWidget.scrollLeft.roundToInt().coerceIn(0, maxX)
            val nextY = nativeWidget.scrollTop.roundToInt().coerceIn(0, maxY)
            currentScrollX = nextX
            currentScrollY = nextY
            canvasElement.setAttribute("data-scroll-x", currentScrollX.toString())
            canvasElement.setAttribute("data-scroll-y", currentScrollY.toString())
            onScrollListener?.invoke(currentScrollX, currentScrollY)
        }
    }

    override fun setScrollableContentSize(contentWidth: Int, contentHeight: Int) {
        contentW = contentWidth.coerceAtLeast(1)
        contentH = contentHeight.coerceAtLeast(1)
        contentSizerDiv.style["width"] = "${contentW}px"
        contentSizerDiv.style["height"] = "${contentH}px"
        nativeWidget.scrollWidth = contentW
        nativeWidget.scrollHeight = contentH
        val maxX = (contentW - viewportW).coerceAtLeast(0)
        val maxY = (contentH - viewportH).coerceAtLeast(0)
        currentScrollX = currentScrollX.coerceIn(0, maxX)
        currentScrollY = currentScrollY.coerceIn(0, maxY)
    }

    override fun setViewportSize(viewportWidth: Int, viewportHeight: Int) {
        viewportW = viewportWidth.coerceAtLeast(1)
        viewportH = viewportHeight.coerceAtLeast(1)
        nativeWidget.clientWidth = viewportW
        nativeWidget.clientHeight = viewportH
        canvasElement.setAttribute("width", viewportW.toString())
        canvasElement.setAttribute("height", viewportH.toString())
        canvasElement.clientWidth = viewportW
        canvasElement.clientHeight = viewportH
        vkContext.swapchainWidth = viewportW
        vkContext.swapchainHeight = viewportH
        glContext.viewport(0, 0, viewportW, viewportH)
        val maxX = (contentW - viewportW).coerceAtLeast(0)
        val maxY = (contentH - viewportH).coerceAtLeast(0)
        currentScrollX = currentScrollX.coerceIn(0, maxX)
        currentScrollY = currentScrollY.coerceIn(0, maxY)
    }

    override fun setScrollPosition(scrollX: Int, scrollY: Int) {
        val maxX = (contentW - viewportW).coerceAtLeast(0)
        val maxY = (contentH - viewportH).coerceAtLeast(0)
        currentScrollX = scrollX.coerceIn(0, maxX)
        currentScrollY = scrollY.coerceIn(0, maxY)
        nativeWidget.scrollLeft = currentScrollX.toDouble()
        nativeWidget.scrollTop = currentScrollY.toDouble()
        canvasElement.setAttribute("data-scroll-x", currentScrollX.toString())
        canvasElement.setAttribute("data-scroll-y", currentScrollY.toString())
    }

    override fun getScrollX(): Int = currentScrollX
    override fun getScrollY(): Int = currentScrollY

    override fun setOnScrollListener(listener: ((scrollX: Int, scrollY: Int) -> Unit)?) {
        onScrollListener = listener
    }

    override fun getRenderingContext(): Any = if (preferVulkan) {
        vkContext.swapchainWidth = viewportW
        vkContext.swapchainHeight = viewportH
        vkContext as VKContext
    } else {
        glContext as GLContext
    }

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        vblankLoopActive = active
        vblankFrameCallback = if (active) onVBlankFrame else null
        canvasElement.setAttribute("data-raf-scheduled", vblankLoopActive.toString())
    }

    override fun requestSingleRender() {
        val nowMs = monotonicClock.elapsedNow().inWholeNanoseconds / 1_000_000.0
        vblankFrameCallback?.invoke(nowMs)
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        canvasElement.style["pointer-events"] = if (passThrough) "none" else "auto"
    }

    override fun getSurfaceWidth(): Int = viewportW
    override fun getSurfaceHeight(): Int = viewportH
    override fun getDevicePixelRatio(): Float = 1.0f

    override fun captureFrameToRgba(): ByteArray =
        ByteArray((viewportW * viewportH * 4).coerceAtLeast(4))

    override fun captureFrameToJpeg(quality: Int): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
}
