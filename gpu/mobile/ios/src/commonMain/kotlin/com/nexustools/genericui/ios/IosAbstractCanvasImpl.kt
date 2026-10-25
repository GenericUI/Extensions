package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.media.AbstractCanvasNativeImpl
import kotlin.time.TimeSource

/**
 * Shared base iOS `UIView` / `GLKView` / `MTKView` implementation of [AbstractCanvasNativeImpl].
 *
 * Drives `registerVBlankRender` via iOS `CADisplayLink` (supporting 60Hz and 120Hz ProMotion displays),
 * calculating high-precision `deltaMs: Double` between `CADisplayLink.timestamp` ticks.
 */
abstract class IosAbstractCanvasImpl<C>(
    uiViewClassName: String = "UIView",
    classPrefix: String = "canvas"
) : IosBaseNativeImpl(uiViewClassName, classPrefix), AbstractCanvasNativeImpl<IosNativeView, C> {

    private val monotonicClock = TimeSource.Monotonic.markNow()
    private var displayLinkActive: Boolean = false
    private var vblankCallback: ((Double?) -> Unit)? = null
    private var lastDisplayLinkTimestampMs: Double = 0.0
    var lastDeltaMs: Double = 0.0
        private set

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        displayLinkActive = active
        vblankCallback = if (active) onVBlankFrame else null
        nativeWidget.metadata["CADisplayLink.isPaused"] = (!active).toString()
        if (active) {
            lastDisplayLinkTimestampMs = nowMs()
        }
    }

    /**
     * Invoked by the `CADisplayLink` selector on each hardware VBlank tick.
     */
    fun onDisplayLinkTick(displayLinkTimestampSeconds: Double? = null) {
        if (!displayLinkActive) return
        val timestampMs = displayLinkTimestampSeconds?.let { it * 1000.0 } ?: nowMs()
        lastDeltaMs = (timestampMs - lastDisplayLinkTimestampMs).coerceAtLeast(0.0)
        lastDisplayLinkTimestampMs = timestampMs
        vblankCallback?.invoke(timestampMs)
    }

    private fun nowMs(): Double =
        monotonicClock.elapsedNow().inWholeNanoseconds / 1_000_000.0

    override fun requestSingleRender() {
        vblankCallback?.invoke(nowMs())
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        nativeWidget.isUserInteractionEnabled = !passThrough
    }

    override fun getSurfaceWidth(): Int = nativeWidget.frame.width

    override fun getSurfaceHeight(): Int = nativeWidget.frame.height

    override fun getDevicePixelRatio(): Float = 3.0f

    override fun captureFrameToRgba(): ByteArray =
        ByteArray((getSurfaceWidth() * getSurfaceHeight() * 4).coerceAtLeast(4))

    override fun captureFrameToJpeg(quality: Int): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

    override fun dispose() {
        displayLinkActive = false
        vblankCallback = null
        super.dispose()
    }
}
