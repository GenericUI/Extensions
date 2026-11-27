package com.nexustools.genericui.ext.webview.desktop.ipc

import com.nexustools.genericui.ext.ipc.IpcChannelAddress

/**
 * Platform-specific child process launcher for isolated Alien + NJS WebView worker processes.
 */
expect object WebViewWorkerLauncher {
    fun currentOsProcessId(): Long

    fun resolveRuntimeDirectory(webViewId: String): String

    fun spawnWorker(
        address: IpcChannelAddress,
        webViewId: String,
        config: WebViewResolvedRuntimeConfig
    ): WebViewWorkerProcessHandle
}
