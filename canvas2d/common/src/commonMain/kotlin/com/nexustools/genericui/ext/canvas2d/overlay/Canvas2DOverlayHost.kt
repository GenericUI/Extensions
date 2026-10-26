package com.nexustools.genericui.ext.canvas2d.overlay

import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtSurface
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.ext.overlay.OverlayHostBox

/**
 * Generic overlay host wrapping a [Canvas2DExtSurface] and extending [OverlayHostBox].
 *
 * Does not attach any default overlay unless an [AbstractOverlay] is explicitly provided via
 * [initialOverlay] or [attachOverlay].
 */
open class Canvas2DOverlayHost(
    val canvas: Canvas2DExtSurface = Canvas2DExtSurface(),
    initialOverlay: AbstractOverlay? = null
) : OverlayHostBox(
    content = canvas,
    initialOverlay = null
) {

    init {
        htmlTag = "genericui:canvas-2d-overlay-host"
        addClass("canvas-2d-overlay-host")
        syncOverlayBoundsToCanvas()
        if (initialOverlay != null) {
            attachOverlay(initialOverlay)
        }
    }

    /**
     * Synchronizes [overlayRoot] and any attached [activeOverlay] dimensions to match [canvas].
     */
    fun syncOverlayBoundsToCanvas() {
        overlayRoot.positionMode = PositionMode.ABSOLUTE
        overlayRoot.topOffset = "0px"
        overlayRoot.leftOffset = "0px"
        val w = canvas.widthPx
        val h = canvas.heightPx
        if (w != null) {
            overlayRoot.widthPx = w
            activeOverlay?.widthPx = w
        }
        if (h != null) {
            overlayRoot.heightPx = h
            activeOverlay?.heightPx = h
        }
    }

    /**
     * Resizes both the underlying [canvas] surface and [overlayRoot].
     */
    fun resizeCanvas(width: Int, height: Int) {
        canvas.syncSurfaceSize(width, height)
        syncOverlayBoundsToCanvas()
    }

    override fun attachOverlay(overlay: AbstractOverlay) {
        super.attachOverlay(overlay)
        syncOverlayBoundsToCanvas()
    }
}
