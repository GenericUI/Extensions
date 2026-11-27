package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopCanvasWidgetHooks
import com.nexustools.genericui.desktop.core.DesktopInteractiveSurfaceSpec
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.ext.webview.desktop.ipc.WebViewWorkerHostSession
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl

/**
 * Native desktop (`:windows`, `:macos`, `:gtk`, `:kde`) [WebViewNativeImpl] backed by an isolated
 * per-instance `genericui-webview-process` worker binary running Alien + Companion Extensions +
 * `NJS` over `:extensions:system:ipc` and compositing incoming RGBA tiles onto a
 * [DesktopInteractiveSurfaceSpec].
 */
class DesktopAlienNjsWebViewImpl(
    val config: WebViewConfig = WebViewConfig()
) : DesktopBaseNativeImpl<DesktopNativeWidget>(
    nativeWidget = DesktopCanvasWidgetHooks.createInteractiveSurfaceWidget?.invoke("AlienNjsWebView")
        ?: DesktopNativeWidget("Desktop", "AlienNjsWebView"),
    componentClassPrefix = "webview"
), WebViewNativeImpl<DesktopNativeWidget> {

    val workerSession: WebViewWorkerHostSession = WebViewWorkerHostSession(config)

    private var userPointerDown: ((PointerEvent) -> Unit)? = null
    private var userPointerMove: ((PointerEvent) -> Unit)? = null
    private var userPointerUp: ((PointerEvent) -> Unit)? = null
    private var userWheel: ((WheelEvent) -> Unit)? = null
    private var userKeyDown: ((KeyEvent) -> Unit)? = null
    private var userKeyUp: ((KeyEvent) -> Unit)? = null
    private var viewportRedrawListener: (() -> Unit)? = null

    init {
        DesktopInteractiveSurfaceSpec(
            surfaceWidth = { workerSession.framebuffer.width },
            surfaceHeight = { workerSession.framebuffer.height },
            swapCount = { workerSession.framebuffer.frameSequence },
            captureRgba = { workerSession.framebuffer.copyRgbaSnapshot() },
            captureBgra = { workerSession.framebuffer.copyBgraSnapshot() },
            resizeSurface = { w, h -> workerSession.resizeSmart(w, h) },
            preferHardwareStretchBlit = false,
            suppressDefaultBorder = true,
            contentFitMode = { ObjectFit.FILL }
        ).attachTo(nativeWidget)

        workerSession.onViewportRedraw = {
            val seq = workerSession.framebuffer.frameSequence
            nativeWidget.interactiveSurfaceSpec?.dispatchBeforePresentFrame(seq, 0.0)
            DesktopNativeWidget.notifySurfaceFrameReady(nativeWidget)
            viewportRedrawListener?.invoke()
        }

        super.setOnPointerDown { ev ->
            workerSession.dispatchPointerEventSmart("down", ev.x, ev.y, ev.button)
            userPointerDown?.invoke(ev)
        }
        super.setOnPointerMove { ev ->
            workerSession.dispatchPointerEventSmart("move", ev.x, ev.y, ev.button)
            userPointerMove?.invoke(ev)
        }
        super.setOnPointerUp { ev ->
            workerSession.dispatchPointerEventSmart("up", ev.x, ev.y, ev.button)
            userPointerUp?.invoke(ev)
        }
        super.setOnWheel { ev ->
            workerSession.dispatchWheelEventSmart(ev.x, ev.y, ev.deltaX, ev.deltaY)
            userWheel?.invoke(ev)
        }
        super.setOnKeyDown { ev ->
            workerSession.dispatchKeyEventSmart(
                kind = "down",
                key = ev.key,
                code = ev.code,
                keyCode = ev.keyCode,
                shift = ev.shiftKey,
                ctrl = ev.ctrlKey,
                alt = ev.altKey,
                meta = ev.metaKey
            )
            userKeyDown?.invoke(ev)
        }
        super.setOnKeyUp { ev ->
            workerSession.dispatchKeyEventSmart(
                kind = "up",
                key = ev.key,
                code = ev.code,
                keyCode = ev.keyCode,
                shift = ev.shiftKey,
                ctrl = ev.ctrlKey,
                alt = ev.altKey,
                meta = ev.metaKey
            )
            userKeyUp?.invoke(ev)
        }

        workerSession.ensureStartedSmart()
    }

    override fun setPreferredSize(size: Size?) {
        super.setPreferredSize(size)
        if (size != null && size.width > 0 && size.height > 0) {
            workerSession.resizeSmart(size.width, size.height)
        }
    }

    override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {
        userPointerDown = listener
    }

    override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) {
        userPointerMove = listener
    }

    override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) {
        userPointerUp = listener
    }

    override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) {
        userWheel = listener
    }

    override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) {
        userKeyDown = listener
    }

    override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) {
        userKeyUp = listener
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
}
