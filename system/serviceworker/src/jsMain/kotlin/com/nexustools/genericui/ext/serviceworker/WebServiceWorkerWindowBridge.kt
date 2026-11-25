package com.nexustools.genericui.ext.serviceworker

internal actual object WebServiceWorkerWindowBridge {
    actual fun canLaunchClientWindow(): Boolean =
        js("typeof self !== 'undefined' && self.clients != null && (typeof self.clients.matchAll === 'function' || typeof self.clients.openWindow === 'function')") as Boolean

    actual fun launchOrFocusClientWindow(launchControlWireText: String, openUrl: String): Boolean {
        if (!canLaunchClientWindow()) return false
        val clientsApi: dynamic = js("self.clients")
        val hasMatchAll = js("typeof self.clients.matchAll === 'function'") as Boolean
        if (hasMatchAll) {
            val opts: dynamic = js("({ type: 'window', includeUncontrolled: true })")
            clientsApi.matchAll(opts).then { list: dynamic ->
                val len = (list?.length as? Int) ?: 0
                if (len > 0) {
                    var focused = false
                    for (i in 0 until len) {
                        val client: dynamic = list[i]
                        if (client != null) {
                            if (jsTypeOf(client.postMessage) == "function") {
                                client.postMessage(launchControlWireText)
                            }
                            if (!focused && jsTypeOf(client.focus) == "function") {
                                client.focus()
                                focused = true
                            }
                        }
                    }
                } else if (jsTypeOf(clientsApi.openWindow) == "function") {
                    clientsApi.openWindow(openUrl)
                }
                null
            }
            return true
        }
        if (jsTypeOf(clientsApi.openWindow) == "function") {
            clientsApi.openWindow(openUrl)
            return true
        }
        return false
    }
}
