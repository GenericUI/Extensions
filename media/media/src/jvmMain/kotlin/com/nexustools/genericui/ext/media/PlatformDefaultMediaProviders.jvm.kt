package com.nexustools.genericui.ext.media

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.ext.media.swing.SwingMediaExtensionProvider
import com.nexustools.genericui.swing.SwingNativeFactory

internal actual fun installPlatformDefaultMediaProvidersIfNeeded() {
    val factory = GenericUI.activeFactory ?: return
    if (factory is SwingNativeFactory || factory.backendName.startsWith("alien(swing", ignoreCase = true)) {
        SwingMediaExtensionProvider.install()
    }
}
