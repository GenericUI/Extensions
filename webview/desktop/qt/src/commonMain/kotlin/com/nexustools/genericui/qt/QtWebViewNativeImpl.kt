package com.nexustools.genericui.qt

import com.nexustools.genericui.desktop.canvas.DesktopWebViewNativeImpl

/**
 * Qt 6 WebEngine (`QWebEngineView` + `QWebEnginePage` + `QWebChannel`) implementation of
 * [com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl].
 */
class QtWebViewNativeImpl : DesktopWebViewNativeImpl<QtWidgetHandle>(
    nativeWidget = QtWidgetHandle("QWebEngineView"),
    engineName = "Qt6 WebEngine (QWebEngineView / Chromium)"
)
