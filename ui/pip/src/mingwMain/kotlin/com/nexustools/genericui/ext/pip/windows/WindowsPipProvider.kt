package com.nexustools.genericui.ext.pip.windows

import com.nexustools.genericui.ext.pip.desktop.DesktopPipProvider

/**
 * Registers the Windows desktop Picture-in-Picture backend.
 */
object WindowsPipProvider {
    fun register() {
        DesktopPipProvider.register()
    }
}
