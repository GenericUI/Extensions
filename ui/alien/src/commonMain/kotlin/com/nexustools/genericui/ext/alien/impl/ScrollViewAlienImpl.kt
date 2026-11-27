package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl

/**
 * Self-rendered scrollable viewport (`ScrollViewAlienImpl`) implementing [ScrollViewNativeImpl].
 */
class ScrollViewAlienImpl(
    session: AlienRenderSession? = null,
    tagName: String = "div"
) : ContainerAlienImpl(
    componentClassPrefix = "overflow-auto",
    tagName = tagName,
    session = session
), ScrollViewNativeImpl<Any?> {

    init {
        node.onWheel = { wheel ->
            val nextX = node.scrollOffsetX + wheel.deltaX.toInt()
            val nextY = node.scrollOffsetY + wheel.deltaY.toInt()
            scrollTo(nextX, nextY)
        }
    }

    override fun scrollTo(x: Int, y: Int) {
        val maxX = if (node.scrollWidth > 0) (node.scrollWidth - node.bounds.width).coerceAtLeast(0) else Int.MAX_VALUE
        val maxY = if (node.scrollHeight > 0) (node.scrollHeight - node.bounds.height).coerceAtLeast(0) else Int.MAX_VALUE
        node.scrollOffsetX = x.coerceIn(0, maxX)
        node.scrollOffsetY = y.coerceIn(0, maxY)
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun scrollToBottom() {
        val totalContentH = if (node.scrollHeight > 0) node.scrollHeight else node.children.sumOf { it.measuredSize.height }
        node.scrollOffsetY = (totalContentH - node.bounds.height).coerceAtLeast(0)
        node.markLayoutDirty()
        node.invalidateBounds()
    }
}
