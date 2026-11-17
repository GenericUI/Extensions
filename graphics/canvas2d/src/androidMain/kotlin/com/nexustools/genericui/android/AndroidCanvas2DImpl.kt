package com.nexustools.genericui.android

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

/**
 * Android hardware-accelerated `android.view.View` implementation of [Canvas2DNativeImpl]
 * backed by a double-buffered [SystemGpuImageBuffer] (`doubleBuffer = true`) that presents
 * swapped RGBA frames into a native Android `ImageView` surface.
 */
class AndroidCanvas2DImpl(
    initialWidth: Int = 800,
    initialHeight: Int = 600
) : AndroidAbstractCanvasImpl<ImageBuffer2DContext>("android.view.View"), Canvas2DNativeImpl<AndroidViewHandle> {

    val imageBuffer: SystemGpuImageBuffer = SystemGpuImageBuffer(
        width = initialWidth.coerceAtLeast(1),
        height = initialHeight.coerceAtLeast(1),
        doubleBuffer = true,
        initialAntialias = true
    )

    private var onSurfaceResizedListener: ((Int, Int) -> Unit)? = null
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

    override fun setPreferredSize(size: Size?) {
        super.setPreferredSize(size)
        if (size != null && size.width > 0 && size.height > 0) {
            val prevW = getSurfaceWidth()
            val prevH = getSurfaceHeight()
            val nextW = size.width.coerceAtLeast(1)
            val nextH = size.height.coerceAtLeast(1)
            surfaceW = nextW
            surfaceH = nextH
            if (imageBuffer.width != nextW || imageBuffer.height != nextH) {
                imageBuffer.resize(nextW, nextH)
            }
            if (prevW != nextW || prevH != nextH) {
                onSurfaceResizedListener?.invoke(nextW, nextH)
            }
        }
    }

    override fun setOnSurfaceResizedListener(listener: ((Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
    }

    override fun setOnBeforeFramePresentListener(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {
        onBeforeFramePresentListener = listener
    }

    override fun getRenderingContext(): ImageBuffer2DContext {
        val w = getSurfaceWidth().coerceAtLeast(1)
        val h = getSurfaceHeight().coerceAtLeast(1)
        if (imageBuffer.width != w || imageBuffer.height != h) {
            imageBuffer.resize(w, h)
        }
        return imageBuffer.getContext()
    }

    override fun onAfterVBlankFrame() {
        presentedFrameCounter++
        val nowMs = System.nanoTime() / 1_000_000.0
        onBeforeFramePresentListener?.invoke(presentedFrameCounter, nowMs)
        val ctx2d = getRenderingContext()
        ctx2d.swapBuffers()
        presentFrameToAndroidView(ctx2d.width, ctx2d.height, ctx2d.presentFrontBufferRgba())
    }

    private fun presentFrameToAndroidView(w: Int, h: Int, rgba: ByteArray) {
        if (!isMainLooperAvailable() || w <= 0 || h <= 0) return
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

    override fun captureFrameToRgba(): ByteArray =
        getRenderingContext().presentFrontBufferRgba()

    override fun dispose() {
        cachedBitmap?.recycle()
        cachedBitmap = null
        super.dispose()
    }
}
