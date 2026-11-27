package com.nexustools.genericui.ext.webview.swing

import com.nexustools.genericui.ext.ipc.IpcExtension
import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension
import com.nexustools.genericui.ext.webview.desktop.NjsPlatformCompilerBootstrap

/**
 * Installs the Swing Alien + NJS isolated OS-worker provider into [WebViewExtension].
 */
object SwingWebViewExtensionProvider {
    fun install() {
        IpcExtension.registerProviders()
        NjsPlatformCompilerBootstrap.ensureInitialized()
        WebViewExtension.registerProvider(WebViewEngineType.ALIEN_NJS) { config ->
            SwingAlienNjsWebViewImpl(config)
        }
    }
}
