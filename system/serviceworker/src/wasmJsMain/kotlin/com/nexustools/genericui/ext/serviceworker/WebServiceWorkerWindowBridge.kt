@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.nexustools.genericui.ext.serviceworker

@JsFun("""() => typeof self !== 'undefined' && self.clients != null && (typeof self.clients.matchAll === 'function' || typeof self.clients.openWindow === 'function')""")
private external fun wasmCanLaunchClientWindow(): Boolean

@JsFun("""(wireText, openUrl) => {
    if (typeof self === 'undefined' || !self.clients) return false;
    if (typeof self.clients.matchAll === 'function') {
        self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
            if (list && list.length > 0) {
                let focused = false;
                for (let i = 0; i < list.length; i++) {
                    const c = list[i];
                    if (c) {
                        if (typeof c.postMessage === 'function') {
                            c.postMessage(wireText);
                        }
                        if (!focused && typeof c.focus === 'function') {
                            c.focus();
                            focused = true;
                        }
                    }
                }
            } else if (typeof self.clients.openWindow === 'function') {
                self.clients.openWindow(openUrl);
            }
        });
        return true;
    }
    if (typeof self.clients.openWindow === 'function') {
        self.clients.openWindow(openUrl);
        return true;
    }
    return false;
}""")
private external fun wasmLaunchOrFocusClientWindow(wireText: String, openUrl: String): Boolean

internal actual object WebServiceWorkerWindowBridge {
    actual fun canLaunchClientWindow(): Boolean =
        wasmCanLaunchClientWindow()

    actual fun launchOrFocusClientWindow(launchControlWireText: String, openUrl: String): Boolean =
        wasmLaunchOrFocusClientWindow(launchControlWireText, openUrl)
}
