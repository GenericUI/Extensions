@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageEncodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.media.AbstractCanvasNativeImpl
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.QuartzCore.CADisplayLink
import platform.UIKit.UIImageView
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.objc.sel_registerName
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * Shared base iOS / iPadOS `UIView` / `UIImageView` / `MTKView` implementation of [AbstractCanvasNativeImpl].
 *
 * Drives `registerVBlankRender` via Apple `CADisplayLink` (supporting 60Hz and 120Hz ProMotion displays on
 * both iPhone and iPad), calculating high-precision `deltaMs: Double` between `CADisplayLink.timestamp` ticks.
 */
abstract class IosAbstractCanvasImpl<C>(
    val backingView: UIView = UIImageView(frame = CGRectMake(0.0, 0.0, 800.0, 600.0)),
    classPrefix: String = "canvas",
    protected var initialSurfaceWidth: Int = 800,
    protected var initialSurfaceHeight: Int = 600
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(backingView),
    componentClassPrefix = classPrefix
), AbstractCanvasNativeImpl<UikitViewHandle, C> {

    private class DisplayLinkTarget(
        private val onTick: (Double?) -> Unit
    ) : NSObject() {
        @ObjCAction
        fun handleDisplayLinkTick(link: CADisplayLink?) {
            onTick(link?.timestamp)
        }
    }

    private val monotonicClock = TimeSource.Monotonic.markNow()
    private var displayLinkActive: Boolean = false
    private var vblankCallback: ((Double?) -> Unit)? = null
    private var lastDisplayLinkTimestampMs: Double = 0.0
    private var displayLink: CADisplayLink? = null
    private var displayLinkTarget: DisplayLinkTarget? = null

    var lastDeltaMs: Double = 0.0
        private set

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        displayLinkActive = active
        vblankCallback = if (active) onVBlankFrame else null
        if (active) {
            lastDisplayLinkTimestampMs = nowMs()
            ensureDisplayLinkStarted()
        } else {
            stopDisplayLink()
        }
    }

    private fun ensureDisplayLinkStarted() {
        if (displayLink != null) {
            runCatching { displayLink?.paused = false }
            return
        }
        runCatching {
            val target = DisplayLinkTarget { ts -> onDisplayLinkTick(ts) }.also {
                displayLinkTarget = it
            }
            val link = CADisplayLink.displayLinkWithTarget(
                target = target,
                selector = sel_registerName("handleDisplayLinkTick:")
            )
            link.addToRunLoop(NSRunLoop.mainRunLoop, forMode = NSRunLoopCommonModes)
            displayLink = link
        }
    }

    private fun stopDisplayLink() {
        runCatching {
            displayLink?.paused = true
            displayLink?.invalidate()
        }
        displayLink = null
        displayLinkTarget = null
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
        onAfterVBlankFrame()
    }

    protected open fun onAfterVBlankFrame() {}

    private fun nowMs(): Double =
        monotonicClock.elapsedNow().inWholeNanoseconds / 1_000_000.0

    override fun requestSingleRender() {
        vblankCallback?.invoke(nowMs())
        onAfterVBlankFrame()
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        uiView.userInteractionEnabled = !passThrough
    }

    override fun getSurfaceWidth(): Int {
        val w = uiView.bounds.useContents { size.width.roundToInt() }
        return if (w > 0) w else initialSurfaceWidth.coerceAtLeast(1)
    }

    override fun getSurfaceHeight(): Int {
        val h = uiView.bounds.useContents { size.height.roundToInt() }
        return if (h > 0) h else initialSurfaceHeight.coerceAtLeast(1)
    }

    override fun getDevicePixelRatio(): Float = UikitEnvironment.mainScreenScale()

    override fun captureFrameToRgba(): ByteArray {
        val w = getSurfaceWidth().coerceAtLeast(1)
        val h = getSurfaceHeight().coerceAtLeast(1)
        val bytes = ByteArray(w * h * 4)
        val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return bytes
        try {
            bytes.usePinned { pinned ->
                val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big
                val ctx = CGBitmapContextCreate(
                    data = pinned.addressOf(0),
                    width = w.toULong(),
                    height = h.toULong(),
                    bitsPerComponent = 8uL,
                    bytesPerRow = (w * 4).toULong(),
                    space = colorSpace,
                    bitmapInfo = bitmapInfo
                ) ?: return bytes
                try {
                    uiView.layer.renderInContext(ctx)
                } finally {
                    CGContextRelease(ctx)
                }
            }
        } finally {
            CGColorSpaceRelease(colorSpace)
        }
        return bytes
    }

    override fun captureFrameToJpeg(quality: Int): ByteArray {
        val w = getSurfaceWidth().coerceAtLeast(1)
        val h = getSurfaceHeight().coerceAtLeast(1)
        val rgba = captureFrameToRgba()
        val buf = ImageBuffer(width = w, height = h, pixelFormat = PixelFormat.RGBA_8888, bytes = rgba)
        return ImageLoader.encode(buf, ImageEncodeOptions(format = ImageFormat.JPEG, quality = quality))
    }

    override fun dispose() {
        displayLinkActive = false
        vblankCallback = null
        stopDisplayLink()
        super.dispose()
    }
}
