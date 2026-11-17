package com.nexustools.genericui.ext.mediacontrols

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.mediacontrols.gtk.GtkMediaControlsImpl
import com.nexustools.genericui.ext.mediacontrols.qt.QtMediaControlsImpl

internal actual fun defaultPlatformMediaControlsFactory(): (() -> MediaControlsNativeImpl)? = {
    if (GenericUI.activeFactory?.backendTarget == BackendTarget.Qt) {
        QtMediaControlsImpl()
    } else {
        GtkMediaControlsImpl()
    }
}
