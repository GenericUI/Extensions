package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl

/**
 * Self-rendered dock area (`DockAreaAlienImpl`) implementing [DockAreaNativeImpl].
 */
class DockAreaAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "dock-area",
    tagName = "aside",
    session = session
), DockAreaNativeImpl<Any?> {

    private var positionValue: DockPosition = DockPosition.LEFT
    private var collapsedState: Boolean = false

    override fun setPosition(position: DockPosition) {
        positionValue = position
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    fun getDockPosition(): DockPosition = positionValue

    override fun setCollapsed(collapsed: Boolean) {
        if (collapsedState != collapsed) {
            collapsedState = collapsed
            node.visible = !collapsed
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun isCollapsed(): Boolean = collapsedState
}
