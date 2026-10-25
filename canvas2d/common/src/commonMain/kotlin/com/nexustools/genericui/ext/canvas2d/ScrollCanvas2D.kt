package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.component.form.RangeSlider
import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.component.media.VBlankRenderCallback
import com.nexustools.genericui.component.media.VBlankSubscription
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.nativeimpl.media.ScrollCanvas2DNativeImpl
import kotlin.math.roundToInt

/**
 * Scrollable 2D canvas with a configurable virtual content size ([contentWidth] x [contentHeight])
 * where **the actual hardware/HTML `<canvas>` only fills the visible viewport ([viewportWidth] x [viewportHeight])
 * and scrolling is simulated** via [onScroll] / [ScrollCanvas2DListener].
 *
 * This guarantees that all GenericUI backends can provide a scrollable 2D canvas with scrollbars
 * over arbitrarily large virtual documents without allocating an oversized GPU/CPU framebuffer:
 * - **Web (`:extensions:canvas2d:web`)**: Renders a `<genericui:scrollcanvas2d>` element with
 *   `position: relative` and `overflow: auto`, an inner `<div>` sized to `[contentWidth]px` x `[contentHeight]px`,
 *   and an HTML `<canvas>` overtop the `<div>` with `position: absolute; left: 0; top: 0; width: 0; height: 0;`.
 * - **System Targets (`:desktop`, `:mobile`, `:swing`)**: Composes the viewport-sized [viewportCanvas] ([Canvas2D])
 *   with a vertical scrollbar component at the right ([rightScrollBar]) and a horizontal scrollbar component
 *   at the bottom ([bottomScrollBar]).
 */
open class ScrollCanvas2D(
    viewportWidth: Int = 800,
    viewportHeight: Int = 600,
    contentWidth: Int = viewportWidth,
    contentHeight: Int = viewportHeight,
    val doubleBuffer: Boolean = true,
    initialAntialias: Boolean = true
) : AbstractContainer("scroll-canvas-2d") {

    private var currentViewportWidth: Int = viewportWidth.coerceAtLeast(1)
    private var currentViewportHeight: Int = viewportHeight.coerceAtLeast(1)
    private var currentContentWidth: Int = contentWidth.coerceAtLeast(1)
    private var currentContentHeight: Int = contentHeight.coerceAtLeast(1)

    /**
     * The actual viewport-sized [Canvas2D] (`viewportWidth` x `viewportHeight`).
     * Its backing framebuffer never exceeds the viewport dimensions.
     */
    val viewportCanvas: Canvas2D = Canvas2D(
        width = currentViewportWidth,
        height = currentViewportHeight,
        doubleBuffer = doubleBuffer,
        initialAntialias = initialAntialias
    )

    /**
     * Vertical scrollbar component placed at the right on System implementations.
     */
    val rightScrollBar: RangeSlider = RangeSlider(
        minValue = 0.0,
        maxValue = (currentContentHeight - currentViewportHeight).coerceAtLeast(0).toDouble(),
        min = 0.0,
        max = (currentContentHeight - currentViewportHeight).coerceAtLeast(0).toDouble(),
        step = 1.0
    ).also {
        it.addClass("scroll-canvas-scrollbar-right")
        it.setAttribute("data-orientation", "vertical")
        it.isEnabled = (currentContentHeight - currentViewportHeight) > 0
    }

    /**
     * Horizontal scrollbar component placed at the bottom on System implementations.
     */
    val bottomScrollBar: RangeSlider = RangeSlider(
        minValue = 0.0,
        maxValue = (currentContentWidth - currentViewportWidth).coerceAtLeast(0).toDouble(),
        min = 0.0,
        max = (currentContentWidth - currentViewportWidth).coerceAtLeast(0).toDouble(),
        step = 1.0
    ).also {
        it.addClass("scroll-canvas-scrollbar-bottom")
        it.setAttribute("data-orientation", "horizontal")
        it.isEnabled = (currentContentWidth - currentViewportWidth) > 0
    }

    var viewportWidth: Int
        get() = currentViewportWidth
        set(value) {
            setViewportSize(value, currentViewportHeight)
        }

    var viewportHeight: Int
        get() = currentViewportHeight
        set(value) {
            setViewportSize(currentViewportWidth, value)
        }

    var contentWidth: Int
        get() = currentContentWidth
        set(value) {
            setContentSize(value, currentContentHeight)
        }

    var contentHeight: Int
        get() = currentContentHeight
        set(value) {
            setContentSize(currentContentWidth, value)
        }

    var scrollX: Int = 0
        private set

    var scrollY: Int = 0
        private set

    val maxScrollX: Int
        get() = (currentContentWidth - currentViewportWidth).coerceAtLeast(0)

    val maxScrollY: Int
        get() = (currentContentHeight - currentViewportHeight).coerceAtLeast(0)

    var antialias: Boolean
        get() = viewportCanvas.antialias
        set(value) {
            viewportCanvas.antialias = value
        }

    val imageBuffer: ImageBuffer
        get() = viewportCanvas.imageBuffer

    val surfaceWidth: Int
        get() = currentViewportWidth

    val surfaceHeight: Int
        get() = currentViewportHeight

    val presentedFrameCount: Long
        get() = viewportCanvas.presentedFrameCount

    private val scrollListeners = mutableListOf<ScrollCanvas2DListener>()
    private var isSyncingScrollBars: Boolean = false
    private var explicitScrollNative: ScrollCanvas2DNativeImpl<*>? = null

    init {
        htmlTag = "genericui:scrollcanvas2d"
        widthPx = currentViewportWidth
        heightPx = currentViewportHeight

        addChild(viewportCanvas)
        addChild(rightScrollBar)
        addChild(bottomScrollBar)

        rightScrollBar.onValueChanged = { newValue ->
            if (!isSyncingScrollBars) {
                scrollTo(scrollX, newValue.roundToInt())
            }
        }
        bottomScrollBar.onValueChanged = { newValue ->
            if (!isSyncingScrollBars) {
                scrollTo(newValue.roundToInt(), scrollY)
            }
        }

        val wheelHandler: (WheelEvent) -> Unit = { event ->
            handleWheelEvent(event)
        }
        onWheel = wheelHandler
        viewportCanvas.onWheel = wheelHandler
    }

    /**
     * Updates the virtual scrollable content dimensions (`contentWidth` x `contentHeight`) without
     * resizing the physical [viewportCanvas] framebuffer (`viewportWidth` x `viewportHeight`).
     */
    fun setContentSize(width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val changed = safeW != currentContentWidth || safeH != currentContentHeight
        currentContentWidth = safeW
        currentContentHeight = safeH

        val clampedX = scrollX.coerceIn(0, maxScrollX)
        val clampedY = scrollY.coerceIn(0, maxScrollY)
        val scrollChanged = clampedX != scrollX || clampedY != scrollY
        scrollX = clampedX
        scrollY = clampedY

        syncScrollBarRangesAndValues()
        activeScrollNative()?.let { impl ->
            impl.setScrollableContentSize(currentContentWidth, currentContentHeight)
            impl.setScrollPosition(scrollX, scrollY)
        }
        if (changed || scrollChanged) {
            dispatchScrollChanged()
        }
    }

    /**
     * Updates the visible viewport dimensions (`viewportWidth` x `viewportHeight`), resizing
     * the physical [viewportCanvas] to match the viewport while preserving virtual [contentWidth] x [contentHeight].
     */
    fun setViewportSize(width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val changed = safeW != currentViewportWidth || safeH != currentViewportHeight
        currentViewportWidth = safeW
        currentViewportHeight = safeH
        widthPx = safeW
        heightPx = safeH
        viewportCanvas.widthPx = safeW
        viewportCanvas.heightPx = safeH

        val clampedX = scrollX.coerceIn(0, maxScrollX)
        val clampedY = scrollY.coerceIn(0, maxScrollY)
        val scrollChanged = clampedX != scrollX || clampedY != scrollY
        scrollX = clampedX
        scrollY = clampedY

        syncScrollBarRangesAndValues()
        activeScrollNative()?.let { impl ->
            impl.setViewportSize(currentViewportWidth, currentViewportHeight)
            impl.setScrollPosition(scrollX, scrollY)
        }
        if (changed || scrollChanged) {
            dispatchScrollChanged()
        }
    }

    /**
     * Scrolls the simulated viewport to `(x, y)` (clamped to `[0 .. maxScrollX]` and `[0 .. maxScrollY]`),
     * synchronizes the right and bottom scrollbars (or Web scroll container), and notifies all [ScrollCanvas2DListener]s.
     */
    fun scrollTo(x: Int, y: Int) {
        val targetX = x.coerceIn(0, maxScrollX)
        val targetY = y.coerceIn(0, maxScrollY)
        val changed = targetX != scrollX || targetY != scrollY
        scrollX = targetX
        scrollY = targetY
        syncScrollBarRangesAndValues()
        activeScrollNative()?.setScrollPosition(scrollX, scrollY)
        if (changed) {
            dispatchScrollChanged()
        }
    }

    /**
     * Scrolls the simulated viewport by `(dx, dy)` pixels relative to the current `(scrollX, scrollY)`.
     */
    fun scrollBy(dx: Int, dy: Int) {
        scrollTo(scrollX + dx, scrollY + dy)
    }

    /**
     * Handles a mouse wheel or trackpad scroll gesture on the canvas, updating `scrollX` / `scrollY`
     * and synchronizing the right and bottom scrollbars.
     */
    fun handleWheelEvent(event: WheelEvent) {
        val dx = if (event.shiftKey && event.deltaX == 0f) {
            event.deltaY.roundToInt()
        } else {
            event.deltaX.roundToInt()
        }
        val dy = if (event.shiftKey && event.deltaX == 0f) {
            0
        } else {
            event.deltaY.roundToInt()
        }
        if (dx != 0 || dy != 0) {
            scrollBy(dx, dy)
        }
    }

    /**
     * Registers a [ScrollCanvas2DListener] invoked whenever `scrollX`, `scrollY`, viewport size,
     * or content size changes so the application can re-render the viewport canvas.
     */
    fun onScroll(listener: ScrollCanvas2DListener) {
        addScrollListener(listener)
    }

    fun addScrollListener(listener: ScrollCanvas2DListener) {
        if (listener !in scrollListeners) {
            scrollListeners.add(listener)
        }
    }

    fun removeScrollListener(listener: ScrollCanvas2DListener) {
        scrollListeners.remove(listener)
    }

    /**
     * Returns the [ImageBuffer2DContext] for the viewport-sized canvas.
     */
    fun getContext(): ImageBuffer2DContext {
        activeScrollNative()?.let { return it.getRenderingContext() }
        return viewportCanvas.getContext()
    }

    /**
     * Registers a VBlank-synchronized render callback on the viewport canvas.
     */
    fun registerVBlankRender(callback: VBlankRenderCallback<ImageBuffer2DContext>): VBlankSubscription =
        viewportCanvas.registerVBlankRender(callback)

    /**
     * Dispatches a VBlank frame to the viewport canvas.
     */
    fun dispatchVBlankFrame(explicitTimestampMs: Double? = null) {
        viewportCanvas.dispatchVBlankFrame(explicitTimestampMs)
    }

    /**
     * Requests a single on-demand render frame on the viewport canvas.
     */
    fun requestRender() {
        viewportCanvas.requestRender()
    }

    /**
     * Swaps the double-buffered surface of the viewport canvas.
     */
    fun swapBuffers() {
        viewportCanvas.swapBuffers()
    }

    /**
     * Draws a `:common` [com.nexustools.genericui.image.ImageBuffer] onto the viewport canvas at `(dx, dy)`.
     */
    fun drawImage(buffer: com.nexustools.genericui.image.ImageBuffer, dx: Float = 0f, dy: Float = 0f) {
        Canvas2DExtension.drawImage(getContext(), buffer, dx, dy)
    }

    /**
     * Extracts the current viewport canvas framebuffer (`viewportWidth` x `viewportHeight`) as a `:common` [com.nexustools.genericui.image.ImageBuffer].
     */
    fun toImageBuffer(): com.nexustools.genericui.image.ImageBuffer {
        val ctx = getContext()
        return Canvas2DExtension.extractImageBuffer(ctx, 0, 0, ctx.width, ctx.height)
    }

    /**
     * Binds a [ScrollCanvas2DNativeImpl] peer directly (used by `:web` or tests) and synchronizes
     * viewport size, scrollable content size, scroll position, and scroll listener callbacks.
     */
    fun attachScrollNativeImpl(impl: ScrollCanvas2DNativeImpl<*>) {
        explicitScrollNative = impl
        configureScrollNativeImpl(impl)
    }

    private fun activeScrollNative(): ScrollCanvas2DNativeImpl<*>? =
        explicitScrollNative ?: typedNative<ScrollCanvas2DNativeImpl<*>>()

    private fun configureScrollNativeImpl(impl: ScrollCanvas2DNativeImpl<*>) {
        impl.setViewportSize(currentViewportWidth, currentViewportHeight)
        impl.setScrollableContentSize(currentContentWidth, currentContentHeight)
        impl.setScrollPosition(scrollX, scrollY)
        impl.setOnScrollListener { newScrollX, newScrollY ->
            val clampedX = newScrollX.coerceIn(0, maxScrollX)
            val clampedY = newScrollY.coerceIn(0, maxScrollY)
            if (clampedX != scrollX || clampedY != scrollY) {
                scrollX = clampedX
                scrollY = clampedY
                syncScrollBarRangesAndValues()
                dispatchScrollChanged()
            }
        }
    }

    private fun syncScrollBarRangesAndValues() {
        isSyncingScrollBars = true
        try {
            val maxX = maxScrollX.toDouble()
            val maxY = maxScrollY.toDouble()
            bottomScrollBar.max = maxX
            bottomScrollBar.upperValue = maxX
            bottomScrollBar.value = scrollX.toDouble().coerceIn(0.0, maxX)
            bottomScrollBar.isEnabled = maxScrollX > 0

            rightScrollBar.max = maxY
            rightScrollBar.upperValue = maxY
            rightScrollBar.value = scrollY.toDouble().coerceIn(0.0, maxY)
            rightScrollBar.isEnabled = maxScrollY > 0
        } finally {
            isSyncingScrollBars = false
        }
    }

    private fun dispatchScrollChanged() {
        val snapshot = scrollListeners.toList()
        for (listener in snapshot) {
            listener.onScrollChanged(
                scrollX = scrollX,
                scrollY = scrollY,
                viewportWidth = currentViewportWidth,
                viewportHeight = currentViewportHeight,
                contentWidth = currentContentWidth,
                contentHeight = currentContentHeight
            )
        }
    }

    override fun createNativeImpl(factory: NativeFactory): NativeImpl<*> =
        Canvas2DExtension.createScrollCanvas2DNativeOrNull(
            viewportWidth = currentViewportWidth,
            viewportHeight = currentViewportHeight,
            contentWidth = currentContentWidth,
            contentHeight = currentContentHeight
        ) ?: factory.createContainer(componentClassPrefix, htmlTag)

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = activeScrollNative() ?: return
        configureScrollNativeImpl(impl)
    }
}
