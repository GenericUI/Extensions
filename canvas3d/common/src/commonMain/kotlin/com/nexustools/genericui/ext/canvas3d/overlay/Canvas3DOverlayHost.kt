package com.nexustools.genericui.ext.canvas3d.overlay

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.ext.canvas3d.GLCanvasExt
import com.nexustools.genericui.ext.canvas3d.VKCanvasExt
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.ext.overlay.OverlayHostBox

/**
 * Generic overlay host wrapping a 3D canvas surface ([GLCanvasExt], [VKCanvasExt], or any [AbstractComponent])
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
     * Returns [canvas] as a [GLCanvasExt] when hosting an OpenGL ES 3D canvas, or `null` otherwise.
     */
    val glCanvas: GLCanvasExt?
        get() = canvas as? GLCanvasExt

    /**
     * Returns [canvas] as a [VKCanvasExt] when hosting a Vulkan 3D canvas, or `null` otherwise.
     */
    val vkCanvas: VKCanvasExt?
        get() = canvas as? VKCanvasExt

    init {
        htmlTag = "genericui:canvas-3d-overlay-host"
        addClass("canvas-3d-overlay-host")
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
