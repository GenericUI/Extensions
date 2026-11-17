package com.nexustools.genericui.qt

import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DNativeImpl

/**
 * Qt 6 `QWidget` + `QPainter` (`QPainter::Antialiasing | QPainter::TextAntialiasing`) implementation
 * of [com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl] synchronized with `QWindow::requestUpdate()` VSync.
 */
class QtCanvas2DNativeImpl(
    initialWidth: Int = 800,
    initialHeight: Int = 600,
    preferVulkan: Boolean = true
) : DesktopCanvas2DNativeImpl<QtWidgetHandle>(
    nativeWidget = QtWidgetHandle("QWidget_QPainterCanvas"),
    nativeApiName = "Qt6 QPainter 2D",
    initialWidth = initialWidth,
    initialHeight = initialHeight,
    preferVulkan = preferVulkan
)
