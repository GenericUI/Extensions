package com.nexustools.genericui.ext.mediacontrols

import com.nexustools.genericui.ext.mediacontrols.ios.IosMediaControlsImpl

internal actual fun defaultPlatformMediaControlsFactory(): (() -> MediaControlsNativeImpl)? = {
    IosMediaControlsImpl()
}
