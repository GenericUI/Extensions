package com.nexustools.genericui.ext.webview

import com.nexustools.genericui.ext.webview.swing.SwingWebViewExtensionProvider

internal actual fun installDefaultPlatformWebViewProvider() {
    SwingWebViewExtensionProvider.install()
}
