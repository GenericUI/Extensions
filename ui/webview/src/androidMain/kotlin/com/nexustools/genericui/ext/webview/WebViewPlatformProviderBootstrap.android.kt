package com.nexustools.genericui.ext.webview

import com.nexustools.genericui.android.AndroidWebViewExtensionProvider

internal actual fun installDefaultPlatformWebViewProvider() {
    AndroidWebViewExtensionProvider.install()
}
