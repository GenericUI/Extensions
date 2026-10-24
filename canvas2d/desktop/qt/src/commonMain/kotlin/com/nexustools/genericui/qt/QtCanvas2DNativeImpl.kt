package com.nexustools.genericui.qt

import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DNativeImpl

/**
 * Qt 6 `QWidget` + `QPainter` (`QPainter::Antialiasing | QPainter::TextAntialiasing`) implementation
 * of [com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl] synchronized with `QWindow::requestUpdate()` VSync.
 */
class QtCanvas2DNativeImpl : DesktopCanvas2DNativeImpl<QtWidgetHandle>(
    nativeWidget = QtWidgetHandle("QWidget_QPainterCanvas"),
    nativeApiName = "Qt6 QPainter 2D"
)
