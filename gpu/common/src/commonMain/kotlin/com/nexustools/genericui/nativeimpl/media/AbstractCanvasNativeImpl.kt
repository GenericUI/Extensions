package com.nexustools.genericui.nativeimpl.media

import com.nexustools.genericui.core.NativeImpl

/**
 * Base native implementation contract for [com.nexustools.genericui.component.media.AbstractCanvas]
 * (`Canvas2D`, `GLCanvas`, `VKCanvas`).
 *
 * Implements VBlank-synchronized rendering (`setVBlankLoopActive`) where:
 * - On `:web`, `window.requestAnimationFrame` is registered and re-registered on every render frame.
 * - On native backends, hardware VSync/VBlank callbacks (`Choreographer`, `CADisplayLink`, `CVDisplayLink`,
 *   `GdkFrameClock`, `QWindow` VSync, `eglSwapInterval(1)` / `VK_PRESENT_MODE_FIFO_KHR` / `DwmFlush`) drive each frame.
 */
interface AbstractCanvasNativeImpl<out W, C> : NativeImpl<W> {
    fun getRenderingContext(): C
    fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit)
    fun requestSingleRender()
    fun setPointerEventsPassThrough(passThrough: Boolean)
    fun getSurfaceWidth(): Int
    fun getSurfaceHeight(): Int
    fun getDevicePixelRatio(): Float
    fun captureFrameToRgba(): ByteArray
    fun captureFrameToJpeg(quality: Int = 80): ByteArray
}
