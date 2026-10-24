package com.nexustools.genericui.macos

import com.nexustools.genericui.desktop.canvas.DesktopWebViewNativeImpl

/**
 * macOS WebKit `WKWebView` (`WKWebViewConfiguration` / `WKUserContentController`) implementation
 * of [com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl].
 */
class MacosWebViewNativeImpl : DesktopWebViewNativeImpl<MacosViewHandle>(
    nativeWidget = MacosViewHandle("WKWebView"),
    engineName = "Apple WebKit WKWebView"
)
