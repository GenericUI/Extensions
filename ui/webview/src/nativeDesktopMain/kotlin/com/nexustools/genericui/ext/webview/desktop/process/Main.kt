package com.nexustools.genericui.ext.webview.desktop.process

/**
 * Kotlin/Native Desktop executable entry point (`webviewProcess` / `genericui-webview-process`)
 * delegating directly to the cross-platform [WebViewWorkerMain].
 */
fun main(args: Array<String>) {
    WebViewWorkerMain.main(args)
}
