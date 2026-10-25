package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl

/**
 * Self-rendered scrollable viewport (`ScrollViewAlienImpl`) implementing [ScrollViewNativeImpl].
 */
class ScrollViewAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "overflow-auto",
    tagName = "div",
    session = session
), ScrollViewNativeImpl<Any?> {

    init {
        node.onWheel = { wheel ->
            node.scrollOffsetX = (node.scrollOffsetX + wheel.deltaX.toInt()).coerceAtLeast(0)
            node.scrollOffsetY = (node.scrollOffsetY + wheel.deltaY.toInt()).coerceAtLeast(0)
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun scrollTo(x: Int, y: Int) {
        node.scrollOffsetX = x.coerceAtLeast(0)
        node.scrollOffsetY = y.coerceAtLeast(0)
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun scrollToBottom() {
        val totalContentH = node.children.sumOf { it.measuredSize.height }
        node.scrollOffsetY = (totalContentH - node.bounds.height).coerceAtLeast(0)
        node.markLayoutDirty()
        node.invalidateBounds()
    }
}
