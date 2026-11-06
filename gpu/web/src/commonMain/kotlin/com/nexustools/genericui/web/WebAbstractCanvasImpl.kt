package com.nexustools.genericui.web

import com.nexustools.genericui.ext.gpu.web.WebVBlankSurfaceSynchronizer
import com.nexustools.genericui.nativeimpl.media.AbstractCanvasNativeImpl
import kotlin.time.TimeSource

/**
 * Shared base `<canvas>` implementation of [AbstractCanvasNativeImpl] for `:web` (`WebCanvas2DImpl`,
 * `WebCanvas3DImpl`).
 *
 * Implements single-flight VBlank-synchronized rendering via [WebBrowserDomBridge.requestAnimationFrame]
 * and [WebBrowserDomBridge.cancelAnimationFrame], synchronizing DOM `<canvas>` dimensions before each
 * frame and passing high-resolution timestamps so `deltaMs: Double` is computed accurately.
 */
abstract class WebAbstractCanvasImpl<C>(
    classPrefix: String = "genericui-canvas"
) : WebBaseNativeImpl("canvas", classPrefix), AbstractCanvasNativeImpl<WebDomElement, C> {

    init {
        WebVBlankSurfaceSynchronizer.install()
        if (nativeWidget.getAttribute("width") == null) {
            nativeWidget.setAttribute("width", "800")
        }
        if (nativeWidget.getAttribute("height") == null) {
            nativeWidget.setAttribute("height", "600")
        }
    }

    private val monotonicClock = TimeSource.Monotonic.markNow()
    private var vblankLoopActive: Boolean = false
    private var vblankFrameCallback: ((Double?) -> Unit)? = null
    private var lastPerformanceNowMs: Double = 0.0
    private var rafScheduled: Boolean = false
    private var activeRafHandle: Int = 0
    var lastComputedDeltaMs: Double = 0.0
        private set

    val isRafScheduled: Boolean
        get() = rafScheduled

    val pendingRafHandle: Int
        get() = activeRafHandle

    protected open fun onAfterFrameRendered() {}

    /**
     * Updates the `<canvas>` surface backing resolution to `(width, height)`.
     * Subclasses such as `WebCanvas3DImpl` override this to resize the underlying GPU context
     * and notify surface resize listeners.
     */
    open fun resizeSurface(width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val wStr = safeW.toString()
        val hStr = safeH.toString()
        if (nativeWidget.getAttribute("width") != wStr) {
            nativeWidget.setAttribute("width", wStr)
        }
        if (nativeWidget.getAttribute("height") != hStr) {
            nativeWidget.setAttribute("height", hStr)
        }
        nativeWidget.clientWidth = safeW
        nativeWidget.clientHeight = safeH
    }

    private fun syncDomCanvasDimensions() {
        val domW = WebBrowserDomBridge.getClientWidth(nativeWidget, nativeWidget.clientWidth)
        val domH = WebBrowserDomBridge.getClientHeight(nativeWidget, nativeWidget.clientHeight)
        if (domW > 0 && domH > 0 && (domW != getSurfaceWidth() || domH != getSurfaceHeight())) {
            resizeSurface(domW, domH)
        }
    }

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        vblankLoopActive = active
        vblankFrameCallback = if (active) onVBlankFrame else null
        if (active) {
            lastPerformanceNowMs = currentPerformanceNowMs()
            scheduleNextRequestAnimationFrame()
        } else {
            cancelPendingRequestAnimationFrame()
            nativeWidget.setAttribute("data-raf-scheduled", "false")
        }
    }

    /**
     * Executes a single VBlank animation frame, syncing DOM `<canvas>` dimensions, computing `deltaMs`,
     * and scheduling the next single-flight `requestAnimationFrame` while [vblankLoopActive] is `true`.
     */
    fun stepRequestAnimationFrame(explicitPerformanceNowMs: Double? = null) {
        if (!vblankLoopActive) return
        if (activeRafHandle != 0) {
            WebBrowserDomBridge.cancelAnimationFrame(activeRafHandle)
            activeRafHandle = 0
        }
        rafScheduled = false
        syncDomCanvasDimensions()
        val nowMs = explicitPerformanceNowMs ?: currentPerformanceNowMs()
        lastComputedDeltaMs = (nowMs - lastPerformanceNowMs).coerceAtLeast(0.0)
        lastPerformanceNowMs = nowMs
        vblankFrameCallback?.invoke(nowMs)
        onAfterFrameRendered()
        if (vblankLoopActive) {
            scheduleNextRequestAnimationFrame()
        }
    }

    private fun scheduleNextRequestAnimationFrame() {
        if (!vblankLoopActive || rafScheduled) return
        rafScheduled = true
        nativeWidget.setAttribute("data-raf-scheduled", "true")
        if (WebBrowserDomBridge.isBrowserEnvironment) {
            activeRafHandle = WebBrowserDomBridge.requestAnimationFrame {
                rafScheduled = false
                activeRafHandle = 0
                if (vblankLoopActive) {
                    stepRequestAnimationFrame(null)
                }
            }
        }
    }

    private fun cancelPendingRequestAnimationFrame() {
        if (activeRafHandle != 0) {
            WebBrowserDomBridge.cancelAnimationFrame(activeRafHandle)
            activeRafHandle = 0
        }
        rafScheduled = false
    }

    private fun currentPerformanceNowMs(): Double =
        monotonicClock.elapsedNow().inWholeNanoseconds / 1_000_000.0

    override fun requestSingleRender() {
        syncDomCanvasDimensions()
        val nowMs = currentPerformanceNowMs()
        vblankFrameCallback?.invoke(nowMs)
        onAfterFrameRendered()
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        nativeWidget.style["pointer-events"] = if (passThrough) "none" else "auto"
    }

    override fun getSurfaceWidth(): Int =
        nativeWidget.getAttribute("width")?.toIntOrNull()
            ?: nativeWidget.clientWidth.takeIf { it > 0 }
            ?: 800

    override fun getSurfaceHeight(): Int =
        nativeWidget.getAttribute("height")?.toIntOrNull()
            ?: nativeWidget.clientHeight.takeIf { it > 0 }
            ?: 600

    override fun getDevicePixelRatio(): Float = 1.0f

    override fun captureFrameToRgba(): ByteArray =
        ByteArray((getSurfaceWidth() * getSurfaceHeight() * 4).coerceAtLeast(4))

    override fun captureFrameToJpeg(quality: Int): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

    override fun dispose() {
        vblankLoopActive = false
        vblankFrameCallback = null
        cancelPendingRequestAnimationFrame()
        nativeWidget.setAttribute("data-raf-scheduled", "false")
        super.dispose()
    }
}
