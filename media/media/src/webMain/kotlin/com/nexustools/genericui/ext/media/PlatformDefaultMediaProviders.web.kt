package com.nexustools.genericui.ext.media

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.ext.media.web.WebMediaExtensionProvider
import com.nexustools.genericui.web.WebNativeFactory

internal actual fun installPlatformDefaultMediaProvidersIfNeeded() {
    if (GenericUI.activeFactory is WebNativeFactory) {
        WebMediaExtensionProvider.install()
    }
}
