package com.nexustools.genericui.android

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLReadFormat
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl

/**
 * Android `android.view.SurfaceView` / `android.opengl.GLSurfaceView` + `EGL14` / `VK_KHR_android_surface`
 * implementation of [Canvas3DNativeImpl].
 */
class AndroidCanvas3DImpl(
    override val config: Canvas3DConfig = Canvas3DConfig(),
    override val activeBackend: GpuBackendType = GpuBackendType.VULKAN,
    override val activeAcceleration: GpuAcceleration = GpuAcceleration.HARDWARE,
    initialWidth: Int = 800,
    initialHeight: Int = 600
) : AndroidAbstractCanvasImpl<GpuRenderContext>(
    if (activeBackend == GpuBackendType.VULKAN) "android.view.SurfaceView" else "android.opengl.GLSurfaceView"
), Canvas3DNativeImpl<AndroidViewHandle> {

    private val renderContext: GpuRenderContext = when (activeBackend) {
        GpuBackendType.VULKAN -> AndroidVKContextImpl(
            config = config.vulkan,
            initialWidth = initialWidth.coerceAtLeast(1),
            initialHeight = initialHeight.coerceAtLeast(1),
            acceleration = activeAcceleration
        )
        GpuBackendType.OPENGL_ES -> AndroidGLContextImpl(
            config = config.gl,
            initialWidth = initialWidth.coerceAtLeast(1),
            initialHeight = initialHeight.coerceAtLeast(1),
            acceleration = activeAcceleration
        )
    }

    override val vkContext: VKContext?
        get() = renderContext as? VKContext

    override val glContext: GLContext?
        get() = renderContext as? GLContext

    private var onSurfaceCreatedListener: ((GpuRenderContext, Int, Int) -> Unit)? = null
    private var onSurfaceResizedListener: ((GpuRenderContext, Int, Int) -> Unit)? = null
    private var onBeforeFramePresentListener: ((Long, Double) -> Unit)? = null
    private var presentedFrameCounter: Long = 0L
    private var cachedBitmap: Bitmap? = null
    private var cachedArgbPixels: IntArray = IntArray(0)

    init {
        surfaceW = initialWidth.coerceAtLeast(1)
        surfaceH = initialHeight.coerceAtLeast(1)
        if (isMainLooperAvailable()) {
            val ctx = AndroidViewHandle.activeContext
            if (ctx != null && nativeWidget.androidView == null) {
                runCatching {
                    nativeWidget.androidView = ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_XY
                    }
                }
            }
        }
    }

    override fun getSurfaceWidth(): Int = surfaceW

    override fun getSurfaceHeight(): Int = surfaceH

    override fun getRenderingContext(): GpuRenderContext = renderContext

    override fun setOnSurfaceCreated(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        onSurfaceCreatedListener = listener
        listener?.invoke(renderContext, getSurfaceWidth(), getSurfaceHeight())
    }

    override fun setOnSurfaceResized(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
        listener?.invoke(renderContext, getSurfaceWidth(), getSurfaceHeight())
    }

    override fun setOnBeforeFramePresentListener(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {
        onBeforeFramePresentListener = listener
    }

    override fun onAfterVBlankFrame() {
        presentedFrameCounter++
        val nowMs = System.nanoTime() / 1_000_000.0
        onBeforeFramePresentListener?.invoke(presentedFrameCounter, nowMs)
        presentFrameToAndroidView()
    }

    private fun presentFrameToAndroidView() {
        if (!isMainLooperAvailable()) return
        val w = getSurfaceWidth().coerceAtLeast(1)
        val h = getSurfaceHeight().coerceAtLeast(1)
        val rgba = captureFrameToRgba()
        val pixelCount = w * h
        if (rgba.size < pixelCount * 4) return

        runOnMainThread {
            runCatching {
                if (nativeWidget.androidView == null) {
                    val ctx = AndroidViewHandle.activeContext
                    if (ctx != null) {
                        nativeWidget.androidView = ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_XY
                        }
                    }
                }
                val view = nativeWidget.androidView ?: return@runCatching
                if (cachedArgbPixels.size != pixelCount) {
                    cachedArgbPixels = IntArray(pixelCount)
                }
                val argb = cachedArgbPixels
                var srcIdx = 0
                for (i in 0 until pixelCount) {
                    val r = rgba[srcIdx].toInt() and 0xFF
                    val g = rgba[srcIdx + 1].toInt() and 0xFF
                    val b = rgba[srcIdx + 2].toInt() and 0xFF
                    val a = rgba[srcIdx + 3].toInt() and 0xFF
                    argb[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    srcIdx += 4
                }
                var bmp = cachedBitmap
                if (bmp == null || bmp.width != w || bmp.height != h) {
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    cachedBitmap = bmp
                }
                bmp.setPixels(argb, 0, w, 0, 0, w, h)
                if (view is ImageView) {
                    view.scaleType = ImageView.ScaleType.FIT_XY
                    view.setImageBitmap(bmp)
                } else {
                    view.background = BitmapDrawable(view.resources, bmp)
                }
                view.invalidate()
            }
        }
    }

    override fun captureFrameToRgba(): ByteArray = when (val ctx = renderContext) {
        is GLContext -> ctx.readPixels(0, 0, getSurfaceWidth(), getSurfaceHeight(), GLReadFormat.RGBA8)
        is VKContext -> ctx.readPixels(0, 0, getSurfaceWidth(), getSurfaceHeight())
        else -> super.captureFrameToRgba()
    }
}
