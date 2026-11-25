package com.nexustools.genericui.ext.media

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.ext.media.desktop.DesktopVlcMediaExtensionProvider

internal actual fun installPlatformDefaultMediaProvidersIfNeeded() {
    val factory = GenericUI.activeFactory ?: return
    if (factory.backendTarget.isDesktop) {
        DesktopVlcMediaExtensionProvider.install(factory.backendName)
    }
}
