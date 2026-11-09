package com.nexustools.genericui.ext.canvas3d.overlay

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.FramePresentTarget
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.css.CssDisplay
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.ext.overlay.OverlayHostBox
import com.nexustools.genericui.style.css.CssLength

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
        positionMode = PositionMode.RELATIVE
        style.position = CssPosition.RELATIVE
        style.display = CssDisplay.FLEX
        style.flexGrow = 1f
        style.flexShrink = 1f
        style.flexBasis = CssLength.percent(0f)
        style.width = CssLength.percent(100f)
        style.height = CssLength.percent(100f)
        style.minWidth = CssLength.ZERO
        style.minHeight = CssLength.ZERO
        style.overflow = OverflowBehavior.HIDDEN
        canvas.style.width = CssLength.percent(100f)
        canvas.style.height = CssLength.percent(100f)
        canvas.style.display = CssDisplay.BLOCK
        overlayRoot.positionMode = PositionMode.ABSOLUTE
        overlayRoot.style.position = CssPosition.ABSOLUTE
        overlayRoot.style.top = CssLength.ZERO
        overlayRoot.style.left = CssLength.ZERO
        overlayRoot.style.right = CssLength.ZERO
        overlayRoot.style.bottom = CssLength.ZERO
        overlayRoot.style.width = CssLength.percent(100f)
        overlayRoot.style.height = CssLength.percent(100f)
        overlayRoot.style.pointerEvents = CssPointerEvents.NONE
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
        overlayRoot.style.position = CssPosition.ABSOLUTE
        overlayRoot.style.top = CssLength.ZERO
        overlayRoot.style.left = CssLength.ZERO
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
