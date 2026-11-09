package com.nexustools.genericui.ext.webview.desktop.process

import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.ext.webview.desktop.ChromiumBlinkEngine
import com.nexustools.genericui.ext.webview.desktop.NjsDesktopWebRuntime

/**
 * Top-level multi-process Chromium Browser coordinator owned by desktop `WebView` instances.
 *
 * Manages isolated [ChromiumRenderProcessHost] (`--type=renderer`), [ChromiumGpuProcessHost]
 * (`--type=gpu`), and [ChromiumNetworkProcessHost] (`--type=network`) processes over Mojo IPC:
 * navigation -> network fetch -> renderer Blink+NJS execution -> GPU compositor frame
 * rasterization -> host callbacks.
 */
class ChromiumBrowserProcessHost(
    val instanceId: String = "webview-${nextInstanceCounter++}",
    var resourceLoader: ((String) -> String?)? = null,
    var onPostMessage: ((String) -> Unit)? = null,
    var onConsoleMessage: ((WebViewConsoleMessage) -> Unit)? = null,
    var onJavaScriptError: ((String) -> Unit)? = null,
    var onPageFinished: ((String) -> Unit)? = null
) {
    val networkProcessHost: ChromiumNetworkProcessHost = ChromiumNetworkProcessHost(
        channelId = "$instanceId-network",
        resourceLoader = { url -> resourceLoader?.invoke(url) }
    )

    val gpuProcessHost: ChromiumGpuProcessHost = ChromiumGpuProcessHost(
        channelId = "$instanceId-gpu"
    )

    val renderProcessHost: ChromiumRenderProcessHost = ChromiumRenderProcessHost(
        channelId = "$instanceId-renderer",
        resourceLoader = { url -> networkProcessHost.fetchResourceBody(url) ?: resourceLoader?.invoke(url) }
    )

    val blinkEngine: ChromiumBlinkEngine
        get() = renderProcessHost.blinkEngine

    val njsRuntime: NjsDesktopWebRuntime
        get() = renderProcessHost.njsRuntime

    var lastCompositorFrame: Map<String, String> = emptyMap()
        private set

    var lastGpuRasterResult: Map<String, String> = emptyMap()
        private set

    init {
        renderProcessHost.onPostMessage = { msg -> onPostMessage?.invoke(msg) }
        renderProcessHost.onConsoleMessage = { msg -> onConsoleMessage?.invoke(msg) }
        renderProcessHost.onJavaScriptError = { err -> onJavaScriptError?.invoke(err) }
        renderProcessHost.onNetworkFetchRequest = { url ->
            networkProcessHost.fetchResourceBody(url) ?: resourceLoader?.invoke(url)
        }
        renderProcessHost.onCompositorFrameSubmitted = { framePayload ->
            lastCompositorFrame = framePayload
            lastGpuRasterResult = gpuProcessHost.rasterizeFrame(framePayload)
        }
    }

    fun loadUrl(
        url: String,
        resolvedHtml: String? = null,
        enableJavaScript: Boolean = true
    ): Map<String, String> {
        val htmlToLoad = resolvedHtml ?: networkProcessHost.fetchResourceBody(url)
        val result = renderProcessHost.loadUrl(
            url = url,
            html = htmlToLoad,
            enableJavaScript = enableJavaScript
        )
        onPageFinished?.invoke(url)
        return result
    }

    fun loadHtml(
        html: String,
        baseUrl: String = "about:blank",
        enableJavaScript: Boolean = true
    ): Map<String, String> {
        val result = renderProcessHost.loadHtml(
            html = html,
            baseUrl = baseUrl,
            enableJavaScript = enableJavaScript
        )
        onPageFinished?.invoke(baseUrl)
        return result
    }

    fun evaluateJavaScript(script: String, sourceName: String = "<evaluateJavascript>"): String? =
        renderProcessHost.evaluateJavaScript(script, sourceName)

    fun postWebMessage(message: String): Boolean {
        val handledByGuest = renderProcessHost.postMessageToJs(message)
        if (!handledByGuest) {
            onPostMessage?.invoke(message)
        }
        return handledByGuest
    }

    fun registerCommunicationFunction(name: String, handler: (String) -> String) {
        renderProcessHost.registerCommunicationFunction(name, handler)
    }

    fun unregisterCommunicationFunction(name: String) {
        renderProcessHost.unregisterCommunicationFunction(name)
    }

    fun callCommunicationFunction(name: String, payloadJson: String = ""): String? =
        renderProcessHost.callCommunicationFunction(name, payloadJson)

    fun setViewportSize(width: Int, height: Int): Map<String, String> =
        renderProcessHost.setViewportSize(width, height)

    fun registerVirtualAsset(url: String, content: String) {
        networkProcessHost.registerVirtualAsset(url, content)
    }

    fun shutdown() {
        renderProcessHost.shutdown()
        gpuProcessHost.shutdown()
        networkProcessHost.shutdown()
    }

    companion object {
        private var nextInstanceCounter: Int = 1
    }
}
