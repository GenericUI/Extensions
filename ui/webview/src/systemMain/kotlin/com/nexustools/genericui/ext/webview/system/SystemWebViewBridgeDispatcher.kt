package com.nexustools.genericui.ext.webview.system

/**
 * Shared system-target (`:extensions:webview:systemCommon`) native JS bridge message queue
 * used across Desktop, Swing, and Mobile WebView implementations.
 */
class SystemWebViewBridgeDispatcher {
    private val handlers = LinkedHashMap<String, (String) -> String>()

    fun registerHandler(channel: String, handler: (String) -> String) {
        handlers[channel] = handler
    }

    fun unregisterHandler(channel: String) {
        handlers.remove(channel)
    }

    fun dispatch(channel: String, payloadJson: String): String =
        handlers[channel]?.invoke(payloadJson) ?: "{}"
}
