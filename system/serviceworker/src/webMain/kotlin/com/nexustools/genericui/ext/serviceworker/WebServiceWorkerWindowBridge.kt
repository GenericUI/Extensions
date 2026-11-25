package com.nexustools.genericui.ext.serviceworker

/**
 * Web-worker helper focusing/messaging an open browser client window or opening a new window URL.
 */
internal expect object WebServiceWorkerWindowBridge {
    fun canLaunchClientWindow(): Boolean
    fun launchOrFocusClientWindow(launchControlWireText: String, openUrl: String): Boolean
}
