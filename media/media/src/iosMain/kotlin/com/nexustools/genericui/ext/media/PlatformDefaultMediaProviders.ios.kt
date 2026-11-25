package com.nexustools.genericui.ext.media

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.ios.IosMediaProvider
import com.nexustools.genericui.ios.IosNativeFactory
import com.nexustools.genericui.ipados.IpadosNativeFactory

internal actual fun installPlatformDefaultMediaProvidersIfNeeded() {
    val factory = GenericUI.activeFactory
    if (factory is IosNativeFactory || factory is IpadosNativeFactory) {
        IosMediaProvider.install()
    }
}
