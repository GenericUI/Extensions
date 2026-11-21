package com.nexustools.genericui.ext.pip.gtk

import com.nexustools.genericui.ext.pip.desktop.DesktopPipProvider

/**
 * Registers the GTK Linux desktop Picture-in-Picture backend.
 */
object GtkPipProvider {
    fun register() {
        DesktopPipProvider.register()
    }
}
