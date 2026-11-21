package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.ext.pip.ios.IosPipProvider

internal actual object PlatformPipBootstrap {
    actual fun ensureDefaultRegistered() {
        IosPipProvider.register()
    }
}
