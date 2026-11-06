package com.nexustools.genericui.ext.canvas2d.overlay

import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.ext.overlay.OverlayHostBox

/**
 * Generic overlay host wrapping a [Canvas2D] and extending [OverlayHostBox].
 *
 * Does not attach any default overlay unless an [AbstractOverlay] is explicitly provided via
 * [initialOverlay] or [attachOverlay].
 */
open class Canvas2DOverlayHost(
    val canvas: Canvas2D = Canvas2D(),
    initialOverlay: AbstractOverlay? = null
) : OverlayHostBox(
    content = canvas,
    initialOverlay = null
) {

    init {
        htmlTag = "genericui:canvas-2d-overlay-host"
        addClass("canvas-2d-overlay-host")
        bindToFramePresentTarget(canvas)
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
        if (w != null && h != null) {
            syncWithTargetResize(w, h)
        } else {
            if (w != null) {
                overlayRoot.widthPx = w
                overlay?.widthPx = w
            }
            if (h != null) {
                overlayRoot.heightPx = h
                overlay?.heightPx = h
            }
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
