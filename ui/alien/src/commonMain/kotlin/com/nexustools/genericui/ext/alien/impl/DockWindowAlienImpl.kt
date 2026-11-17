package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.dock.DockWindowNativeImpl

/**
 * Self-rendered floating dock window (`DockWindowAlienImpl`) implementing [DockWindowNativeImpl].
 */
class DockWindowAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "card",
    tagName = "div",
    session = session
), DockWindowNativeImpl<Any?> {

    private var titleValue: String = ""
    private var onCloseRequestedListener: (() -> Unit)? = null

    init {
        node.customPaint = { ctx, bounds, style ->
            if (titleValue.isNotEmpty()) {
                val headerRect = Rect(bounds.x + 12, bounds.y + 4, (bounds.width - 24).coerceAtLeast(0), 28)
                AlienBootstrapPainter.paintText(
                    ctx = ctx,
                    text = titleValue,
                    contentBounds = headerRect,
                    style = style.copy(fontWeight = 600),
                    verticalCenter = true,
                    maxLines = 1
                )
            }
        }
    }

    override fun setTitle(title: String) {
        titleValue = title
        node.invalidateBounds()
    }

    override fun setWindowBounds(bounds: Rect) {
        node.bounds = bounds
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun showDockWindow() {
        node.visible = true
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun hideDockWindow() {
        node.visible = false
        node.invalidateBounds()
    }

    override fun setOnDockWindowCloseRequested(listener: (() -> Unit)?) {
        onCloseRequestedListener = listener
    }
}
