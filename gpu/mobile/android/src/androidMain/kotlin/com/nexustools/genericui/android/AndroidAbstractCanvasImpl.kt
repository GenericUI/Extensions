package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.media.AbstractCanvasNativeImpl

/**
 * Shared Android `android.view.Choreographer` VBlank base implementation for `AndroidCanvas2DImpl`,
 * `AndroidGLCanvasImpl`, and `AndroidVKCanvasImpl`.
 */
abstract class AndroidAbstractCanvasImpl<C>(
    viewClassName: String = "android.view.View"
) : AndroidBaseNativeImpl(viewClassName, "canvas"), AbstractCanvasNativeImpl<AndroidViewHandle, C> {

    protected var surfaceW: Int = 800
    protected var surfaceH: Int = 600
    protected var vblankLoopActive: Boolean = false
    protected var vblankCallback: ((Double?) -> Unit)? = null
    protected var pointerPassThrough: Boolean = false

    override fun getSurfaceWidth(): Int =
        if (nativeWidget.bounds.width > 0) nativeWidget.bounds.width else surfaceW

    override fun getSurfaceHeight(): Int =
        if (nativeWidget.bounds.height > 0) nativeWidget.bounds.height else surfaceH

    override fun getDevicePixelRatio(): Float = 1f

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (deltaSeconds: Double?) -> Unit) {
        vblankLoopActive = active
        vblankCallback = if (active) onVBlankFrame else null
    }

    override fun requestSingleRender() {
        vblankCallback?.invoke(null)
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        pointerPassThrough = passThrough
        nativeWidget.attributes["pointer-events"] = if (passThrough) "none" else "auto"
    }

    override fun captureFrameToRgba(): ByteArray {
        val w = getSurfaceWidth().coerceAtLeast(1)
        val h = getSurfaceHeight().coerceAtLeast(1)
        return ByteArray(w * h * 4)
    }

    override fun captureFrameToJpeg(quality: Int): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
}
