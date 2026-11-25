package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.ext.canvas2d.desktop.DesktopCanvas2DProvider

internal actual fun installPlatformDefaultCanvas2DProviders() {
    DesktopCanvas2DProvider.install()
}
