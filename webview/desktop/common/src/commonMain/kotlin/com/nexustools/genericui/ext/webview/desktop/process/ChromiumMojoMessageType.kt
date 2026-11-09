package com.nexustools.genericui.ext.webview.desktop.process

/**
 * Mojo IPC message types exchanged between Browser, Renderer, GPU, and Network processes.
 */
enum class ChromiumMojoMessageType {
    HANDSHAKE,
    HANDSHAKE_ACK,
    LOAD_URL,
    LOAD_HTML,
    EVALUATE_JS,
    EVALUATE_JS_RESULT,
    POST_MESSAGE_TO_JS,
    POST_MESSAGE_TO_HOST,
    DOM_MUTATION_COMMIT,
    SUBMIT_COMPOSITOR_FRAME,
    RASTERIZE_DISPLAY_LIST,
    RASTERIZE_FRAME_COMPLETE,
    NETWORK_FETCH_REQUEST,
    NETWORK_FETCH_RESPONSE,
    SET_VIEWPORT_SIZE,
    SHUTDOWN,
    PING,
    PONG
}
