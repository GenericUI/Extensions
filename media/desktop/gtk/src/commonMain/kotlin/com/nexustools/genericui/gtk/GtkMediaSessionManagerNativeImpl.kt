package com.nexustools.genericui.gtk

import com.nexustools.genericui.desktop.system.DesktopMediaSessionManagerNativeImpl

/**
 * GTK / Linux MPRIS 2.2 D-Bus (`org.mpris.MediaPlayer2` / `org.mpris.MediaPlayer2.Player`)
 * implementation of [com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl].
 */
class GtkMediaSessionManagerNativeImpl : DesktopMediaSessionManagerNativeImpl(
    backendToolkit = "Linux D-Bus MPRIS2 (org.mpris.MediaPlayer2)"
)
