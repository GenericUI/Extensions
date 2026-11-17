package com.nexustools.genericui.ext.mediacontrols

import com.nexustools.genericui.ext.mediacontrols.android.AndroidMediaControlsImpl

internal actual fun defaultPlatformMediaControlsFactory(): (() -> MediaControlsNativeImpl)? = {
    AndroidMediaControlsImpl()
}
