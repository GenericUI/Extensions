package com.nexustools.genericui.ext.media

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.ext.media.swing.SwingMediaExtensionProvider
import com.nexustools.genericui.swing.SwingNativeFactory

internal actual fun installPlatformDefaultMediaProvidersIfNeeded() {
    if (GenericUI.activeFactory is SwingNativeFactory) {
        SwingMediaExtensionProvider.install()
    }
}
