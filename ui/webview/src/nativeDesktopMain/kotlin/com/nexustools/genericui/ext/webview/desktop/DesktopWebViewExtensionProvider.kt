package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.genericui.ext.ipc.IpcExtension
import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension

/**
 * Installs the Native Desktop Alien + NJS isolated OS-worker provider into [WebViewExtension]
 * for `:windows`, `:macos`, `:gtk`, and `:kde`.
 */
object DesktopWebViewExtensionProvider {
    fun install() {
        IpcExtension.registerProviders()
        NjsPlatformCompilerBootstrap.ensureInitialized()
        WebViewExtension.registerProvider(WebViewEngineType.ALIEN_NJS) { config ->
            DesktopAlienNjsWebViewImpl(config)
        }
    }
}
