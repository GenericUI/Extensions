package com.nexustools.genericui.ext.webview.desktop.ipc

/**
 * Packet type constants exchanged over `:extensions:system:ipc` BinaryJson between the
 * host [WebView] and each isolated per-instance worker OS process.
 */
object WebViewIpcPacketTypes {
    // Host -> Worker commands
    const val CMD_INIT: String = "webview.init"
    const val CMD_LOAD_URL: String = "webview.loadUrl"
    const val CMD_LOAD_HTML: String = "webview.loadHtml"
    const val CMD_RELOAD: String = "webview.reload"
    const val CMD_STOP_LOADING: String = "webview.stopLoading"
    const val CMD_GO_BACK: String = "webview.goBack"
    const val CMD_GO_FORWARD: String = "webview.goForward"
    const val CMD_EVALUATE_JS: String = "webview.evaluateJs"
    const val CMD_CALL_COMM_FN: String = "webview.callCommFn"
    const val CMD_POST_MESSAGE: String = "webview.postMessage"
    const val CMD_REGISTER_BRIDGE_FN: String = "webview.registerBridgeFn"
    const val CMD_UNREGISTER_BRIDGE_FN: String = "webview.unregisterBridgeFn"
    const val CMD_RESIZE: String = "webview.resize"
    const val CMD_POINTER_EVENT: String = "webview.pointerEvent"
    const val CMD_WHEEL_EVENT: String = "webview.wheelEvent"
    const val CMD_KEY_EVENT: String = "webview.keyEvent"
    const val CMD_FOCUS_EVENT: String = "webview.focusEvent"
    const val CMD_TICK: String = "webview.tick"
    const val CMD_REQUEST_FRAME: String = "webview.requestFrame"
    const val CMD_PING: String = "webview.ping"
    const val CMD_SHUTDOWN: String = "webview.shutdown"

    // Host -> Worker RPC reply
    const val RES_BRIDGE_CALL: String = "webview.bridgeCallReply"

    // Worker -> Host replies & events
    const val RES_OK: String = "webview.ok"
    const val RES_ERROR: String = "webview.error"
    const val EVT_READY: String = "webview.ready"
    const val EVT_FRAME_TILES: String = "webview.frameTiles"
    const val EVT_PAGE_FINISHED: String = "webview.pageFinished"
    const val EVT_LOADING_CHANGED: String = "webview.loadingChanged"
    const val EVT_TITLE_CHANGED: String = "webview.titleChanged"
    const val EVT_FAVICON_CHANGED: String = "webview.event.faviconChanged"
    const val EVT_WEB_MESSAGE: String = "webview.webMessage"
    const val EVT_CONSOLE_MESSAGE: String = "webview.consoleMessage"
    const val EVT_JS_ERROR: String = "webview.jsError"
    const val EVT_BRIDGE_CALL: String = "webview.bridgeCall"
}
