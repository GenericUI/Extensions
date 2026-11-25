package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.ext.canvas2d.swing.SwingCanvas2DProvider

internal actual fun installPlatformDefaultCanvas2DProviders() {
    SwingCanvas2DProvider.install()
}
