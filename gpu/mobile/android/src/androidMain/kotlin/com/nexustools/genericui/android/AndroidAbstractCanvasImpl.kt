package com.nexustools.genericui.android

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
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
    private var choreographerCallback: Choreographer.FrameCallback? = null

    override fun getSurfaceWidth(): Int =
        if (nativeWidget.bounds.width > 0) nativeWidget.bounds.width else surfaceW

    override fun getSurfaceHeight(): Int =
        if (nativeWidget.bounds.height > 0) nativeWidget.bounds.height else surfaceH

    override fun getDevicePixelRatio(): Float = 1f

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (deltaSeconds: Double?) -> Unit) {
        vblankLoopActive = active
        vblankCallback = if (active) onVBlankFrame else null
        if (active) {
            scheduleChoreographerLoop()
        } else {
            cancelChoreographerLoop()
        }
    }

    override fun requestSingleRender() {
        vblankCallback?.invoke(null)
        onAfterVBlankFrame()
    }

    protected open fun onAfterVBlankFrame() {}

    protected fun isMainLooperAvailable(): Boolean = runCatching {
        val looper = Looper.getMainLooper()
        looper != null && looper.thread != null
    }.getOrDefault(false)

    protected fun runOnMainThread(block: () -> Unit) {
        val looper = runCatching { Looper.getMainLooper() }.getOrNull() ?: return
        val thread = runCatching { looper.thread }.getOrNull() ?: return
        if (Thread.currentThread() === thread) {
            runCatching { block() }
        } else {
            runCatching {
                Handler(looper).post {
                    runCatching { block() }
                }
            }
        }
    }

    private fun scheduleChoreographerLoop() {
        if (!isMainLooperAvailable()) return
        runOnMainThread {
            if (!vblankLoopActive) return@runOnMainThread
            runCatching {
                val cb = choreographerCallback ?: Choreographer.FrameCallback { frameTimeNanos ->
                    if (!vblankLoopActive) return@FrameCallback
                    val timestampMs = frameTimeNanos / 1_000_000.0
                    vblankCallback?.invoke(timestampMs)
                    onAfterVBlankFrame()
                    if (vblankLoopActive) {
                        runCatching {
                            Choreographer.getInstance().postFrameCallback(choreographerCallback)
                        }
                    }
                }.also { choreographerCallback = it }
                Choreographer.getInstance().removeFrameCallback(cb)
                Choreographer.getInstance().postFrameCallback(cb)
            }
        }
    }

    private fun cancelChoreographerLoop() {
        val cb = choreographerCallback ?: return
        if (!isMainLooperAvailable()) {
            choreographerCallback = null
            return
        }
        runOnMainThread {
            runCatching {
                Choreographer.getInstance().removeFrameCallback(cb)
            }
        }
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

    override fun dispose() {
        vblankLoopActive = false
        vblankCallback = null
        cancelChoreographerLoop()
        super.dispose()
    }
}
