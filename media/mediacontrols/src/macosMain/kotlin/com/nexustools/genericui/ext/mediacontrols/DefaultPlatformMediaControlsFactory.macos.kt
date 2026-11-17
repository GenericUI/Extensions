package com.nexustools.genericui.ext.mediacontrols

import com.nexustools.genericui.ext.mediacontrols.macos.MacosMediaControlsImpl

internal actual fun defaultPlatformMediaControlsFactory(): (() -> MediaControlsNativeImpl)? = {
    MacosMediaControlsImpl()
}
