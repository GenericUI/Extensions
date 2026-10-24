package com.nexustools.genericui.qt

import com.nexustools.genericui.desktop.system.DesktopMediaSessionManagerNativeImpl

/**
 * Qt 6 Multimedia / MPRIS2 / SMTC media session implementation of
 * [com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl].
 */
class QtMediaSessionManagerNativeImpl : DesktopMediaSessionManagerNativeImpl(
    backendToolkit = "Qt6 MediaSession (MPRIS2 / SMTC)"
)
