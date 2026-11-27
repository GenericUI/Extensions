package com.nexustools.genericui.ext.svg.web

/**
 * Platform-specific DOM namespace bridge (`http://www.w3.org/2000/svg`) for `:web` (`js` and `wasmJs`).
 *
 * Uses integer node handles so common `:webMain` code never references `dynamic` or platform-only
 * `JsAny` types directly.
 */
internal expect object WebSvgDomBridge {
    fun isBrowserEnvironment(): Boolean
    fun createSvgNode(tagName: String): Int
    fun mountSvgRoot(hostPeer: Any?, svgRootHandle: Int): Boolean
    fun unmountSvgRoot(hostPeer: Any?, svgRootHandle: Int)
    fun releaseHandle(handle: Int)
    fun clearNodeAttributes(handle: Int)
    fun setAttribute(handle: Int, name: String, value: String)
    fun setStyleProperty(handle: Int, name: String, value: String)
    fun removeStyleProperty(handle: Int, name: String)
    fun setTextContent(handle: Int, text: String)
    fun clearChildren(handle: Int)
    fun appendChild(parentHandle: Int, childHandle: Int)
    fun attachElementListeners(
        handle: Int,
        svgRootHandle: Int,
        onClick: () -> Unit,
        onPointerEnter: (Float, Float, Float, Float) -> Unit,
        onPointerLeave: (Float, Float, Float, Float) -> Unit
    )
}
