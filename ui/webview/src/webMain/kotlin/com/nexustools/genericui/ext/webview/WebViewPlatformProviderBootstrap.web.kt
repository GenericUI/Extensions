package com.nexustools.genericui.ext.webview

import com.nexustools.genericui.ext.webview.web.WebWebViewExtensionProvider

internal actual fun installDefaultPlatformWebViewProvider() {
    WebWebViewExtensionProvider.install()
}
