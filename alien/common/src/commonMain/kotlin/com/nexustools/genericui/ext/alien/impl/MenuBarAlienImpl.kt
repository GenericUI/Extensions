package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl

/**
 * Self-rendered Bootstrap 5.3 menu bar (`MenuBarAlienImpl`) implementing [MenuBarNativeImpl].
 */
class MenuBarAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "menubar",
    tagName = "nav",
    session = session
), MenuBarNativeImpl<Any?> {

    init {
        node.customMeasure = { availW, _, _ ->
            Size(width = if (availW > 0) availW else 640, height = 36)
        }
    }
}
