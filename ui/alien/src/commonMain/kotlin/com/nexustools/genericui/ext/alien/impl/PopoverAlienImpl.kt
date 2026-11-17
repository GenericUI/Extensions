package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.ui.Placement
import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl

/**
 * Self-rendered Bootstrap 5.3 `.popover` floating surface (`PopoverAlienImpl`) implementing [PopoverNativeImpl].
 */
class PopoverAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "popover",
    tagName = "div",
    session = session
), PopoverNativeImpl<Any?> {

    private var titleValue: String? = null
    private var placementValue: Placement = Placement.BOTTOM
    var isShown: Boolean = false
        private set

    init {
        node.visible = false
        node.customPaint = { ctx, bounds, style ->
            val t = titleValue
            if (!t.isNullOrBlank()) {
                val headerRect = Rect(bounds.x + 12, bounds.y + 6, (bounds.width - 24).coerceAtLeast(0), 28)
                AlienBootstrapPainter.paintText(
                    ctx = ctx,
                    text = t,
                    contentBounds = headerRect,
                    style = style.copy(fontWeight = 600),
                    verticalCenter = true,
                    maxLines = 1
                )
            }
        }
    }

    override fun setTitle(title: String?) {
        titleValue = title
        node.invalidateBounds()
    }

    override fun setPlacement(placement: Placement) {
        placementValue = placement
        node.invalidateBounds()
    }

    override fun showAnchored(anchor: NativeImpl<*>?, x: Int, y: Int) {
        isShown = true
        node.visible = true
        val anchorBounds = anchor?.getBounds()
        val px = if (anchorBounds != null) anchorBounds.x + x else x
        val py = if (anchorBounds != null) anchorBounds.bottom + y else y
        node.bounds = Rect(px, py, maxOf(node.bounds.width, 220), maxOf(node.bounds.height, 100))
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun hide() {
        isShown = false
        node.visible = false
        node.invalidateBounds()
    }
}
