package com.nexustools.genericui.ext.canvas3d.overlay

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.FramePresentTarget
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.ext.overlay.OverlayHostBox

/**
 * Generic overlay host wrapping a 3D canvas surface ([Canvas3D] or any [AbstractComponent])
 * and extending [OverlayHostBox].
 *
 * Does not attach any default overlay unless an [AbstractOverlay] is explicitly provided via
 * [initialOverlay] or [attachOverlay].
 */
open class Canvas3DOverlayHost(
    val canvas: AbstractComponent,
    initialOverlay: AbstractOverlay? = null
) : OverlayHostBox(
    content = canvas,
    initialOverlay = null
) {

    /**
     * Returns [canvas] as a [Canvas3D] when hosting a unified 3D canvas, or `null` otherwise.
     */
    val canvas3D: Canvas3D?
        get() = canvas as? Canvas3D

    init {
        htmlTag = "genericui:canvas-3d-overlay-host"
        addClass("canvas-3d-overlay-host")
        if (canvas is FramePresentTarget) {
            bindToFramePresentTarget(canvas)
        }
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
     * Resizes both the underlying 3D [canvas] component and [overlayRoot].
     */
    fun resizeCanvas(width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        canvas.widthPx = safeW
        canvas.heightPx = safeH
        syncOverlayBoundsToCanvas()
    }

    override fun attachOverlay(overlay: AbstractOverlay) {
        super.attachOverlay(overlay)
        syncOverlayBoundsToCanvas()
    }
}
