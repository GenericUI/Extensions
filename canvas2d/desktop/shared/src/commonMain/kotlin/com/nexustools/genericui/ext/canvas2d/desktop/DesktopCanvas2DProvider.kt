package com.nexustools.genericui.ext.canvas2d.desktop

import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DContextImpl
import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension

/**
 * Registers `:desktop`'s [DesktopCanvas2DContextImpl] and [DesktopCanvas2DNativeImpl] into [Canvas2DExtension].
 */
object DesktopCanvas2DProvider {
    fun install(nativeApiName: String = "Desktop2D") {
        Canvas2DExtension.registerContextFactory { width, height ->
            DesktopCanvas2DContextImpl(width = width, height = height, nativeApiName = nativeApiName)
        }
        Canvas2DExtension.registerCanvas2DFactory { _, _ ->
            DesktopCanvas2DNativeImpl(
                nativeWidget = DesktopNativeWidget(nativeApiName, "canvas-2d"),
                nativeApiName = nativeApiName
            )
        }
    }
}
