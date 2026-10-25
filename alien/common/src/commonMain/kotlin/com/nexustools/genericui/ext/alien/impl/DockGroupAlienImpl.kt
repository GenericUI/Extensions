package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.enums.dock.DockGroupMode
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.nativeimpl.dock.DockGroupNativeImpl

/**
 * Self-rendered dock group (`DockGroupAlienImpl`) implementing [DockGroupNativeImpl].
 */
class DockGroupAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "dock-group",
    tagName = "div",
    session = session
), DockGroupNativeImpl<Any?> {

    private var groupModeValue: DockGroupMode = DockGroupMode.TABBED
    private var activeIndexValue: Int = 0

    override fun setGroupMode(mode: DockGroupMode) {
        groupModeValue = mode
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun getGroupMode(): DockGroupMode = groupModeValue

    override fun setActivePanelIndex(index: Int) {
        activeIndexValue = index.coerceAtLeast(0)
        node.invalidateBounds()
    }

    override fun getActivePanelIndex(): Int = activeIndexValue
}
