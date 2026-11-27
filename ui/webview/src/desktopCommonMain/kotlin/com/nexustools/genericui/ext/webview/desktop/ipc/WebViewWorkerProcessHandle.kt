package com.nexustools.genericui.ext.webview.desktop.ipc

/**
 * OS handle for an isolated child worker process launched by a desktop [com.nexustools.genericui.ext.webview.WebView].
 */
interface WebViewWorkerProcessHandle {
    val pid: Long
    fun isAlive(): Boolean
    fun killForcibly()
    fun waitForExit(timeoutMs: Int): Boolean
    fun exitCodeOrNull(): Int?
    fun diagnosticsSummary(): String
    fun closeHandle() {}
}
