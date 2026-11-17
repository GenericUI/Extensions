package com.nexustools.genericui.ext.mediacontrols

import com.nexustools.genericui.ext.mediacontrols.windows.WindowsMediaControlsImpl

internal actual fun defaultPlatformMediaControlsFactory(): (() -> MediaControlsNativeImpl)? = {
    WindowsMediaControlsImpl()
}
