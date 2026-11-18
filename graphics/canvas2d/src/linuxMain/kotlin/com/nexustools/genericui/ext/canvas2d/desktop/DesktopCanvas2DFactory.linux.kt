package com.nexustools.genericui.ext.canvas2d.desktop

import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DNativeImpl
import com.nexustools.genericui.desktop.core.DesktopCanvasWidgetHooks
import com.nexustools.genericui.desktop.core.DesktopNativeWidget

internal actual fun createPlatformDesktopCanvas2DNativeImpl(
    initialWidth: Int,
    initialHeight: Int,
    preferVulkan: Boolean
): DesktopCanvas2DNativeImpl<*> {
    val (widget, apiName) = DesktopCanvasWidgetHooks.createCanvas2DWidgetAndApiName?.invoke()
        ?: (DesktopNativeWidget("Linux_Desktop", "Canvas2DSurface") to "Cairo 2D (cairo_t) + Pango")
    return DesktopCanvas2DNativeImpl(
        nativeWidget = widget,
        nativeApiName = apiName,
        initialWidth = initialWidth,
        initialHeight = initialHeight,
        preferVulkan = preferVulkan
    )
}
