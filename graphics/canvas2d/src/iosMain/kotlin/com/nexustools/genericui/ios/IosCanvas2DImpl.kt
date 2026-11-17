@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.SystemGpuImageBuffer
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

/**
 * iOS & iPadOS `UIImageView` + `CADisplayLink` implementation of [Canvas2DNativeImpl] backed by a
 * double-buffered [SystemGpuImageBuffer] (`doubleBuffer = true`).
 */
class IosCanvas2DImpl(
    initialWidth: Int = 800,
    initialHeight: Int = 600
) : IosAbstractCanvasImpl<ImageBuffer2DContext>(
    backingView = UIImageView(
        frame = CGRectMake(0.0, 0.0, initialWidth.coerceAtLeast(1).toDouble(), initialHeight.coerceAtLeast(1).toDouble())
    ).apply {
        contentMode = UIViewContentMode.UIViewContentModeScaleToFill
        clipsToBounds = true
    },
    classPrefix = "canvas-2d",
    initialSurfaceWidth = initialWidth.coerceAtLeast(1),
    initialSurfaceHeight = initialHeight.coerceAtLeast(1)
), Canvas2DNativeImpl<UikitViewHandle> {

    val imageBuffer: SystemGpuImageBuffer = SystemGpuImageBuffer(
        width = initialWidth.coerceAtLeast(1),
        height = initialHeight.coerceAtLeast(1),
        doubleBuffer = true,
        initialAntialias = true
    )

    private var onSurfaceResizedListener: ((Int, Int) -> Unit)? = null
    private var onBeforeFramePresentListener: ((Long, Double) -> Unit)? = null
    private var presentedFrameCounter: Long = 0L

    override fun setPreferredSize(size: Size?) {
        super.setPreferredSize(size)
        if (size != null && size.width > 0 && size.height > 0) {
            val prevW = getSurfaceWidth()
            val prevH = getSurfaceHeight()
            val nextW = size.width.coerceAtLeast(1)
            val nextH = size.height.coerceAtLeast(1)
            initialSurfaceWidth = nextW
            initialSurfaceHeight = nextH
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
        val ctx2d = getRenderingContext()
        presentedFrameCounter++
        onBeforeFramePresentListener?.invoke(presentedFrameCounter, lastDeltaMs)
        ctx2d.swapBuffers()
        val w = ctx2d.width.coerceAtLeast(1)
        val h = ctx2d.height.coerceAtLeast(1)
        val rgba = ctx2d.presentFrontBufferRgba()
        if (rgba.size >= w * h * 4) {
            val frameBuffer = ImageBuffer(
                width = w,
                height = h,
                pixelFormat = PixelFormat.RGBA_8888,
                bytes = rgba
            )
            UikitGeometryAndColorUtil.imageBufferToUiImage(frameBuffer)?.let { uiImg ->
                (uiView as? UIImageView)?.image = uiImg
            }
        }
    }

    override fun captureFrameToRgba(): ByteArray =
        getRenderingContext().presentFrontBufferRgba()
}
