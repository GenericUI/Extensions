package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.ext.pip.android.AndroidPipProvider

internal actual object PlatformPipBootstrap {
    actual fun ensureDefaultRegistered() {
        AndroidPipProvider.register()
    }
}
