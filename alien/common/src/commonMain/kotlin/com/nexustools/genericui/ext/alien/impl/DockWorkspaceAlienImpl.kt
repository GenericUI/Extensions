package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWorkspaceNativeImpl

/**
 * Self-rendered dockable workspace (`DockWorkspaceAlienImpl`) implementing [DockWorkspaceNativeImpl].
 */
class DockWorkspaceAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "dock-workspace",
    tagName = "div",
    session = session
), DockWorkspaceNativeImpl<Any?> {

    var centralDocumentImpl: NativeImpl<*>? = null
        private set

    val dockAreas: MutableMap<DockPosition, DockAreaNativeImpl<*>> = linkedMapOf()
    var windowModeName: String = "SINGLE_WINDOW"
        private set

    override fun setCentralDocumentNative(centralImpl: NativeImpl<*>?) {
        val prev = centralDocumentImpl
        if (prev != null && prev !== centralImpl) {
            removeChildNative(prev)
        }
        centralDocumentImpl = centralImpl
        if (centralImpl != null) {
            addChildNative(centralImpl, 0)
        }
    }

    override fun attachDockAreaNative(position: DockPosition, areaImpl: DockAreaNativeImpl<*>) {
        dockAreas[position] = areaImpl
        addChildNative(areaImpl, -1)
    }

    override fun setWindowModeNative(modeName: String) {
        windowModeName = modeName
        node.invalidateBounds()
    }
}
