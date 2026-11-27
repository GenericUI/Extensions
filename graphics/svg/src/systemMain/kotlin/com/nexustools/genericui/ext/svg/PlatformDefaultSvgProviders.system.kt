package com.nexustools.genericui.ext.svg

import com.nexustools.genericui.ext.svg.system.SystemSvgProvider

internal actual fun installPlatformDefaultSvgProviders() {
    SystemSvgProvider.install()
}
