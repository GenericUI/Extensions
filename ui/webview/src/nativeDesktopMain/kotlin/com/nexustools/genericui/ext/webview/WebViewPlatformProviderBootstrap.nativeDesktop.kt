package com.nexustools.genericui.ext.webview

import com.nexustools.genericui.ext.webview.desktop.DesktopWebViewExtensionProvider

internal actual fun installDefaultPlatformWebViewProvider() {
    DesktopWebViewExtensionProvider.install()
}
