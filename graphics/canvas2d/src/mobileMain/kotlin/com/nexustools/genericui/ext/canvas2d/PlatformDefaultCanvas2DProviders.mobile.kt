package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.ext.canvas2d.mobile.MobileCanvas2DProvider

internal actual fun installPlatformDefaultCanvas2DProviders() {
    MobileCanvas2DProvider.install()
}
