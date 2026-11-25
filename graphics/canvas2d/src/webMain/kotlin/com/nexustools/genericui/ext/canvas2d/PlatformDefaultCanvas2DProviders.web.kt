package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.ext.canvas2d.web.WebCanvas2DProvider

internal actual fun installPlatformDefaultCanvas2DProviders() {
    WebCanvas2DProvider.install()
}
