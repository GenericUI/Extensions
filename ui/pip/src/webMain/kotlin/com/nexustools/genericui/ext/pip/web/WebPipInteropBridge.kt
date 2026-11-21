package com.nexustools.genericui.ext.pip.web

/**
 * Platform-specific JS / WasmJS browser Picture-in-Picture and Page Visibility interop bridge.
 */
internal expect object WebPipInteropBridge {
    fun isBrowserEnvironment(): Boolean
    fun isDocumentPipSupported(): Boolean
    fun isVideoElementPipSupported(): Boolean
    fun isDocumentHidden(): Boolean
    fun observeDocumentVisibility(onHiddenChanged: (Boolean) -> Unit): () -> Unit
    fun openDocumentPipWindow(
        widthPx: Int,
        heightPx: Int,
        title: String,
        rootPeer: Any?,
        onEntered: () -> Unit,
        onClosed: () -> Unit,
        onFailed: () -> Unit
    ): Boolean
    fun closeDocumentPipWindow()
    fun attachInPlaceRoot(rootPeer: Any?): Boolean
    fun detachInPlaceRoot(rootPeer: Any?)
    fun isHtmlVideoElement(peer: Any?): Boolean
    fun requestVideoElementPip(
        videoPeer: Any?,
        onEntered: () -> Unit,
        onExited: () -> Unit,
        onFailed: () -> Unit
    ): Boolean
    fun exitVideoElementPip(): Boolean
}
