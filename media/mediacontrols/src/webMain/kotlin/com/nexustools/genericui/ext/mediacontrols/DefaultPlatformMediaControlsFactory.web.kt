package com.nexustools.genericui.ext.mediacontrols

import com.nexustools.genericui.ext.mediacontrols.web.WebMediaControlsImpl

internal actual fun defaultPlatformMediaControlsFactory(): (() -> MediaControlsNativeImpl)? = {
    WebMediaControlsImpl()
}
