package com.nexustools.genericui.ext.pip.kde

import com.nexustools.genericui.ext.pip.desktop.DesktopPipProvider

/**
 * Registers the KDE Linux desktop Picture-in-Picture backend.
 */
object KdePipProvider {
    fun register() {
        DesktopPipProvider.register()
    }
}
