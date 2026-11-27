package com.nexustools.genericui.ext.webview.swing

import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.ext.webview.desktop.ipc.WebViewWorkerHostSession
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl
import com.nexustools.genericui.swing.SwingBaseNativeImpl
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Swing desktop implementation of [WebViewNativeImpl] backed by an isolated child JVM OS worker
 * process running the Alien + NJS Web engine over `:extensions:system:ipc` and rendering dirty
 * RGBA tiles onto a [WebViewSwingPanel] (never using `JEditorPane`).
 */
class SwingAlienNjsWebViewImpl(
    val config: WebViewConfig = WebViewConfig()
) : SwingBaseNativeImpl(
    baseWidget = createPanelPlaceholder(),
    componentClassPrefix = "webview"
), WebViewNativeImpl<JComponent> {

    val workerSession: WebViewWorkerHostSession = WebViewWorkerHostSession(config)
    private val viewportPanel: WebViewSwingPanel = WebViewSwingPanel(workerSession)
    private var viewportRedrawListener: (() -> Unit)? = null

    init {
        viewportPanel.preferredSize = java.awt.Dimension(
            config.initialViewportWidth.coerceAtLeast(1),
            config.initialViewportHeight.coerceAtLeast(1)
        )
        baseWidget.layout = java.awt.BorderLayout()
        baseWidget.add(viewportPanel, java.awt.BorderLayout.CENTER)
        workerSession.onViewportRedraw = {
            SwingUtilities.invokeLater {
                viewportPanel.repaint()
            }
            viewportRedrawListener?.invoke()
        }
        workerSession.ensureStartedSmart()
    }

    override fun setPreferredSize(size: Size?) {
        super.setPreferredSize(size)
        if (size != null && size.width > 0 && size.height > 0) {
            viewportPanel.preferredSize = java.awt.Dimension(size.width, size.height)
            workerSession.resizeSmart(size.width, size.height)
        }
    }

    override fun loadUrl(url: String) {
        workerSession.loadUrlSmart(url)
    }

    override fun getUrl(): String? = workerSession.getCurrentUrl()

    override fun getTitle(): String? = workerSession.getCurrentTitle()

    override fun getFaviconUrl(): String? = workerSession.getCurrentFaviconUrl()

    override fun isLoading(): Boolean = workerSession.isLoading()

    override fun loadHtml(html: String, baseUrl: String?) {
        workerSession.loadHtmlSmart(html, baseUrl)
    }

    override fun reload() {
        workerSession.reloadSmart()
    }

    override fun stopLoading() {
        workerSession.stopLoadingSmart()
    }

    override fun goBack() {
        workerSession.goBackSmart()
    }

    override fun goForward() {
        workerSession.goForwardSmart()
    }

    override fun canGoBack(): Boolean = workerSession.canGoBack()

    override fun canGoForward(): Boolean = workerSession.canGoForward()

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        workerSession.evaluateJavascriptSmart(script, callback)
    }

    override fun postWebMessage(message: String) {
        workerSession.postWebMessageSmart(message)
    }

    override fun setOnWebMessageReceived(listener: ((String) -> Unit)?) {
        workerSession.onWebMessageReceived = listener
    }

    override fun setOnPageFinished(listener: ((String) -> Unit)?) {
        workerSession.onPageFinished = listener
    }

    override fun setOnTitleChanged(listener: ((String) -> Unit)?) {
        workerSession.onTitleChanged = listener
    }

    override fun setOnFaviconChanged(listener: ((String?) -> Unit)?) {
        workerSession.onFaviconChanged = listener
    }

    override fun setOnLoadingChanged(listener: ((Boolean) -> Unit)?) {
        workerSession.onLoadingChanged = listener
    }

    override fun setOnNavStateChanged(listener: ((canGoBack: Boolean, canGoForward: Boolean) -> Unit)?) {
        workerSession.onNavStateChanged = listener
    }

    override fun registerCommunicationFunction(name: String, handler: (String) -> String) {
        workerSession.registerCommunicationFunctionSmart(name, handler)
    }

    override fun unregisterCommunicationFunction(name: String) {
        workerSession.unregisterCommunicationFunctionSmart(name)
    }

    override fun callCommunicationFunction(
        name: String,
        payloadJson: String,
        callback: ((String?) -> Unit)?
    ) {
        workerSession.callCommunicationFunctionSmart(name, payloadJson, callback)
    }

    override fun setOnConsoleMessage(listener: ((WebViewConsoleMessage) -> Unit)?) {
        workerSession.onConsoleMessage = listener
    }

    override fun setOnJavaScriptError(listener: ((String) -> Unit)?) {
        workerSession.onJavaScriptError = listener
    }

    override fun resizeViewport(width: Int, height: Int) {
        workerSession.resizeSmart(width, height)
        viewportPanel.repaint()
    }

    override fun captureViewportRgba(): ByteArray =
        workerSession.framebuffer.copyRgbaSnapshot()

    override fun captureViewportSize(): Pair<Int, Int> =
        workerSession.framebuffer.copySize()

    override fun getWorkerProcessId(): Long? = workerSession.currentWorkerPid

    override fun setViewportRedrawListener(listener: (() -> Unit)?) {
        viewportRedrawListener = listener
    }

    override fun dispose() {
        workerSession.disposeSmart()
        super.dispose()
    }

    private companion object {
        fun createPanelPlaceholder(): javax.swing.JPanel = javax.swing.JPanel()
    }
}
