package com.nexustools.genericui.ext.pip.macos

import com.nexustools.genericui.ext.pip.desktop.DesktopPipProvider

/**
 * Registers the macOS desktop Picture-in-Picture backend.
 */
object MacosPipProvider {
    fun register() {
        DesktopPipProvider.register()
    }
}
