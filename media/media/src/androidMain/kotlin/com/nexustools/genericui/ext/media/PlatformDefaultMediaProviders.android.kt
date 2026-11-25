package com.nexustools.genericui.ext.media

import com.nexustools.genericui.android.AndroidMediaProvider
import com.nexustools.genericui.android.AndroidNativeFactory
import com.nexustools.genericui.core.GenericUI

internal actual fun installPlatformDefaultMediaProvidersIfNeeded() {
    if (GenericUI.activeFactory is AndroidNativeFactory) {
        AndroidMediaProvider.install()
    }
}
