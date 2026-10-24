package com.nexustools.genericui.windows

import com.nexustools.genericui.desktop.canvas.DesktopWebViewNativeImpl

/**
 * Windows native WebView2 (`ICoreWebView2Environment` / `ICoreWebView2Controller` / `ICoreWebView2`)
 * implementation of [com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl].
 */
class WindowsWebViewNativeImpl : DesktopWebViewNativeImpl<Win32HwndHandle>(
    nativeWidget = Win32HwndHandle("Chrome_WidgetWin_0_WebView2"),
    engineName = "Microsoft Edge WebView2 (ICoreWebView2)"
)
