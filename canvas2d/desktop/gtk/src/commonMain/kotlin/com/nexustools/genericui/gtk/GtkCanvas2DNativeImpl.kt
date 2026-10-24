package com.nexustools.genericui.gtk

import com.nexustools.genericui.desktop.canvas.DesktopCanvas2DNativeImpl

/**
 * GTK 4 `GtkDrawingArea` + Cairo (`cairo_t`) + Pango (`PangoLayout`) implementation of
 * [com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl] synchronized with `gtk_widget_add_tick_callback` (`GdkFrameClock`).
 */
class GtkCanvas2DNativeImpl : DesktopCanvas2DNativeImpl<GtkWidgetHandle>(
    nativeWidget = GtkWidgetHandle("GtkDrawingArea"),
    nativeApiName = "Cairo 2D (cairo_t) + Pango"
)
