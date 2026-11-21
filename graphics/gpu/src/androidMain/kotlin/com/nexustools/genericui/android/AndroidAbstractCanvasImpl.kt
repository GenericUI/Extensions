package com.nexustools.genericui.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageEncodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.PixelFormat
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
    private var robolectricFrameRunnable: Runnable? = null

    override fun getSurfaceWidth(): Int =
        if (nativeWidget.bounds.width > 0) nativeWidget.bounds.width else surfaceW

    override fun getSurfaceHeight(): Int =
        if (nativeWidget.bounds.height > 0) nativeWidget.bounds.height else surfaceH

    override fun getDevicePixelRatio(): Float =
        nativeWidget.androidView?.resources?.displayMetrics?.density?.takeIf { it > 0f } ?: 1f

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
                val looper = Looper.getMainLooper() ?: return@runCatching
                val handler = Handler(looper)
                if (IS_ROBOLECTRIC) {
                    val runnable = robolectricFrameRunnable ?: object : Runnable {
                        override fun run() {
                            if (!vblankLoopActive) return
                            val timestampMs = SystemClock.uptimeMillis().toDouble()
                            vblankCallback?.invoke(timestampMs)
                            onAfterVBlankFrame()
                            if (vblankLoopActive) {
                                runCatching { handler.postDelayed(this, 16L) }
                            }
                        }
                    }.also { robolectricFrameRunnable = it }
                    handler.removeCallbacks(runnable)
                    handler.postDelayed(runnable, 16L)
                    return@runCatching
                }
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
        val cb = choreographerCallback
        val runnable = robolectricFrameRunnable
        if (!isMainLooperAvailable()) {
            choreographerCallback = null
            robolectricFrameRunnable = null
            return
        }
        runOnMainThread {
            runCatching {
                if (runnable != null) {
                    Looper.getMainLooper()?.let { Handler(it).removeCallbacks(runnable) }
                }
                robolectricFrameRunnable = null
                if (cb != null) {
                    Choreographer.getInstance().removeFrameCallback(cb)
                }
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
        val view = nativeWidget.androidView
        if (view != null) {
            val captured = runCatching {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bmp)
                    if (view.width != w || view.height != h) {
                        view.measure(
                            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
                        )
                        view.layout(0, 0, w, h)
                    }
                    view.draw(canvas)
                    val argb = IntArray(w * h)
                    bmp.getPixels(argb, 0, w, 0, 0, w, h)
                    val rgba = ByteArray(w * h * 4)
                    var i = 0
                    var j = 0
                    while (i < argb.size) {
                        val px = argb[i++]
                        rgba[j++] = ((px ushr 16) and 0xFF).toByte()
                        rgba[j++] = ((px ushr 8) and 0xFF).toByte()
                        rgba[j++] = (px and 0xFF).toByte()
                        rgba[j++] = ((px ushr 24) and 0xFF).toByte()
                    }
                    rgba
                } finally {
                    bmp.recycle()
                }
            }.getOrNull()
            if (captured != null) return captured
        }
        return ByteArray(w * h * 4)
    }

    override fun captureFrameToJpeg(quality: Int): ByteArray {
        val w = getSurfaceWidth().coerceAtLeast(1)
        val h = getSurfaceHeight().coerceAtLeast(1)
        val rgba = captureFrameToRgba()
        val buf = ImageBuffer(width = w, height = h, pixelFormat = PixelFormat.RGBA_8888, bytes = rgba)
        return ImageLoader.encode(buf, ImageEncodeOptions(format = ImageFormat.JPEG, quality = quality))
    }

    override fun dispose() {
        vblankLoopActive = false
        vblankCallback = null
        cancelChoreographerLoop()
        super.dispose()
    }

    companion object {
        private val IS_ROBOLECTRIC: Boolean = runCatching {
            Class.forName("org.robolectric.Robolectric")
            true
        }.getOrDefault(false)
    }
}
