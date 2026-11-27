package com.nexustools.genericui.ext.webview

import com.nexustools.genericui.ios.IosWebViewExtensionProvider

internal actual fun installDefaultPlatformWebViewProvider() {
    IosWebViewExtensionProvider.install()
}
