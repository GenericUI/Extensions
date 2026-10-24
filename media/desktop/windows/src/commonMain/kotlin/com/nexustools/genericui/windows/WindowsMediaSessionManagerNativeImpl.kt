package com.nexustools.genericui.windows

import com.nexustools.genericui.desktop.system.DesktopMediaSessionManagerNativeImpl

/**
 * Windows System Media Transport Controls (`ISystemMediaTransportControlsInterop` / `SystemMediaTransportControls`)
 * implementation of [com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl].
 */
class WindowsMediaSessionManagerNativeImpl : DesktopMediaSessionManagerNativeImpl(
    backendToolkit = "Windows SMTC (SystemMediaTransportControls)"
)
