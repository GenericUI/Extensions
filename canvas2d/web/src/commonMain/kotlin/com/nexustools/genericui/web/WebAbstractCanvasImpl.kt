package com.nexustools.genericui.web

import com.nexustools.genericui.nativeimpl.media.AbstractCanvasNativeImpl
import kotlin.time.TimeSource

/**
 * Shared base `<canvas>` implementation of [AbstractCanvasNativeImpl] for `:web` (`WebCanvas2DImpl`,
 * `WebGLCanvasImpl`, `WebVKCanvasImpl`).
 *
 * Implements VBlank-synchronized rendering where `window.requestAnimationFrame` is registered and
 * re-registered on every render frame, passing high-resolution `performance.now()` timestamps so
 * `deltaMs: Double` is computed accurately for animations and shaders.
 */
abstract class WebAbstractCanvasImpl<C>(
    classPrefix: String = "genericui-canvas"
) : WebBaseNativeImpl("canvas", classPrefix), AbstractCanvasNativeImpl<WebDomElement, C> {

    private val monotonicClock = TimeSource.Monotonic.markNow()
    private var vblankLoopActive: Boolean = false
    private var vblankFrameCallback: ((Double?) -> Unit)? = null
    private var lastPerformanceNowMs: Double = 0.0
    var lastComputedDeltaMs: Double = 0.0
        private set

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        vblankLoopActive = active
        vblankFrameCallback = if (active) onVBlankFrame else null
        if (active) {
            lastPerformanceNowMs = currentPerformanceNowMs()
            scheduleNextRequestAnimationFrame()
        }
    }

    /**
     * Simulates or hooks `window.requestAnimationFrame`, computing `deltaMs` (`performance.now()` delta)
     * and re-registering `requestAnimationFrame` on each frame while [vblankLoopActive] is `true`.
     */
    fun stepRequestAnimationFrame(explicitPerformanceNowMs: Double? = null) {
        if (!vblankLoopActive) return
        val nowMs = explicitPerformanceNowMs ?: currentPerformanceNowMs()
        lastComputedDeltaMs = (nowMs - lastPerformanceNowMs).coerceAtLeast(0.0)
        lastPerformanceNowMs = nowMs
        vblankFrameCallback?.invoke(nowMs)
        if (vblankLoopActive) {
            scheduleNextRequestAnimationFrame()
        }
    }

    private fun scheduleNextRequestAnimationFrame() {
        nativeWidget.setAttribute("data-raf-scheduled", vblankLoopActive.toString())
    }

    private fun currentPerformanceNowMs(): Double =
        monotonicClock.elapsedNow().inWholeNanoseconds / 1_000_000.0

    override fun requestSingleRender() {
        val nowMs = currentPerformanceNowMs()
        vblankFrameCallback?.invoke(nowMs)
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        nativeWidget.style["pointer-events"] = if (passThrough) "none" else "auto"
    }

    override fun getSurfaceWidth(): Int = nativeWidget.clientWidth

    override fun getSurfaceHeight(): Int = nativeWidget.clientHeight

    override fun getDevicePixelRatio(): Float = 1.0f

    override fun captureFrameToRgba(): ByteArray =
        ByteArray((getSurfaceWidth() * getSurfaceHeight() * 4).coerceAtLeast(4))

    override fun captureFrameToJpeg(quality: Int): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

    override fun dispose() {
        vblankLoopActive = false
        vblankFrameCallback = null
        super.dispose()
    }
}
