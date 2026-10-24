package com.nexustools.genericui.gtk

import com.nexustools.genericui.desktop.canvas.DesktopWebViewNativeImpl

/**
 * GTK 4 WebKitGTK (`WebKitWebView` from `webkitgtk-6.0`) implementation of
 * [com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl].
 */
class GtkWebViewNativeImpl : DesktopWebViewNativeImpl<GtkWidgetHandle>(
    nativeWidget = GtkWidgetHandle("WebKitWebView"),
    engineName = "WebKitGTK 6.0 (WebKitWebView)"
)
