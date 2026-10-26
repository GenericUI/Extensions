package com.nexustools.genericui.ext.canvas2d.overlay

import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.ext.canvas2d.ScrollCanvas2D
import com.nexustools.genericui.ext.canvas2d.ScrollCanvas2DListener
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.ext.overlay.OverlayHostBox

/**
 * Generic overlay host wrapping a [ScrollCanvas2D] and extending [OverlayHostBox] where
 * [overlayRoot] covers only the visible viewport area (`viewportWidth` x `viewportHeight`),
 * excluding the horizontal and vertical scrollbars.
 *
 * Does not attach any default overlay unless an [AbstractOverlay] is explicitly provided via
 * [initialOverlay] or [attachOverlay].
 */
open class ScrollCanvas2DOverlayHost(
    val scrollCanvas: ScrollCanvas2D = ScrollCanvas2D(),
    initialOverlay: AbstractOverlay? = null
) : OverlayHostBox(
    content = scrollCanvas,
    initialOverlay = null
) {

    /**
     * Indicates that [overlayRoot] is restricted to the viewport area and never covers the scrollbars.
     */
    val overlayCoversScrollbars: Boolean = false

    /**
     * The viewport-sized [Canvas2D] inside [scrollCanvas].
     */
    val viewportCanvas: Canvas2D
        get() = scrollCanvas.viewportCanvas

    /**
     * Current width of [overlayRoot], matching [ScrollCanvas2D.viewportWidth].
     */
    val overlayViewportWidth: Int
        get() = overlayRoot.widthPx ?: scrollCanvas.viewportWidth

    /**
     * Current height of [overlayRoot], matching [ScrollCanvas2D.viewportHeight].
     */
    val overlayViewportHeight: Int
        get() = overlayRoot.heightPx ?: scrollCanvas.viewportHeight

    private val viewportSyncListener = ScrollCanvas2DListener { _, _, viewportWidth, viewportHeight, _, _ ->
        applyViewportOverlayBounds(viewportWidth, viewportHeight)
    }

    init {
        htmlTag = "genericui:scroll-canvas-2d-overlay-host"
        addClass("scroll-canvas-2d-overlay-host")
        syncOverlayBoundsToViewport()
        scrollCanvas.addScrollListener(viewportSyncListener)
        if (initialOverlay != null) {
            attachOverlay(initialOverlay)
        }
    }

    /**
     * Synchronizes [overlayRoot] and any attached [activeOverlay] to cover only the
     * [ScrollCanvas2D] viewport area (`0, 0, viewportWidth, viewportHeight`), excluding scrollbars.
     */
    fun syncOverlayBoundsToViewport() {
        applyViewportOverlayBounds(scrollCanvas.viewportWidth, scrollCanvas.viewportHeight)
    }

    private fun applyViewportOverlayBounds(viewportWidth: Int, viewportHeight: Int) {
        val safeW = viewportWidth.coerceAtLeast(1)
        val safeH = viewportHeight.coerceAtLeast(1)
        overlayRoot.positionMode = PositionMode.ABSOLUTE
        overlayRoot.topOffset = "0px"
        overlayRoot.leftOffset = "0px"
        overlayRoot.widthPx = safeW
        overlayRoot.heightPx = safeH
        overlayRoot.setAttribute("data-overlay-region", "viewport-only")
        overlayRoot.setAttribute("data-excludes-scrollbars", "true")
        activeOverlay?.let { overlay ->
            overlay.widthPx = safeW
            overlay.heightPx = safeH
        }
    }

    /**
     * Updates the visible viewport size on [scrollCanvas] and synchronizes [overlayRoot] bounds.
     */
    fun setViewportSize(width: Int, height: Int) {
        scrollCanvas.setViewportSize(width, height)
        syncOverlayBoundsToViewport()
    }

    /**
     * Updates the virtual scrollable content dimensions on [scrollCanvas].
     */
    fun setContentSize(width: Int, height: Int) {
        scrollCanvas.setContentSize(width, height)
    }

    /**
     * Scrolls [scrollCanvas] to `(x, y)`.
     */
    fun scrollTo(x: Int, y: Int) {
        scrollCanvas.scrollTo(x, y)
    }

    /**
     * Scrolls [scrollCanvas] by `(dx, dy)` relative to its current scroll offset.
     */
    fun scrollBy(dx: Int, dy: Int) {
        scrollCanvas.scrollBy(dx, dy)
    }

    override fun attachOverlay(overlay: AbstractOverlay) {
        super.attachOverlay(overlay)
        syncOverlayBoundsToViewport()
    }

    override fun dispose() {
        scrollCanvas.removeScrollListener(viewportSyncListener)
        super.dispose()
    }
}
