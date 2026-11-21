package com.nexustools.genericui.ext.pip.qt

import com.nexustools.genericui.ext.pip.desktop.DesktopPipProvider

/**
 * Registers the Qt Linux desktop Picture-in-Picture backend.
 */
object QtPipProvider {
    fun register() {
        DesktopPipProvider.register()
    }
}
