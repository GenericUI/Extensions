package com.nexustools.genericui.ext.svg

import com.nexustools.genericui.ext.svg.web.WebSvgProvider

internal actual fun installPlatformDefaultSvgProviders() {
    WebSvgProvider.install()
}
