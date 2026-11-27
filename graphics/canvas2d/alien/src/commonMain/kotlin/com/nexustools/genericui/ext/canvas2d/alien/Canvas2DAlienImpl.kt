package com.nexustools.genericui.ext.canvas2d.alien

import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.impl.AlienImpl
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferDirtyListener
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageEncodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Self-rendered Alien implementation of [Canvas2DNativeImpl] (`Canvas2DAlienImpl`).
 *
 * Maintains an offscreen `:extensions:graphics:imageprocessing` [ImageBuffer] and
 * [ImageBuffer2DContext] and blits its presented front buffer directly into the enclosing
 * [AlienRenderSession]'s `Canvas2D` during `customPaint`, eliminating OS underlay holes and
 * preserving Z-order compositing, CSS opacity/padding/borders/transforms, and pointer/wheel/key
 * event routing.
 */
class Canvas2DAlienImpl(
    val canvasComponent: Canvas2D? = null,
    initialWidth: Int = canvasComponent?.widthPx ?: 800,
    initialHeight: Int = canvasComponent?.heightPx ?: 600,
    val doubleBuffer: Boolean = canvasComponent?.doubleBuffer ?: true,
    initialAntialias: Boolean = canvasComponent?.antialias ?: true,
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = "canvas2d",
    tagName = "canvas",
    session = session
), Canvas2DNativeImpl<Any?> {

    val imageBuffer: ImageBuffer = Canvas2DExtension.createImageBuffer(
        width = initialWidth.coerceAtLeast(1),
        height = initialHeight.coerceAtLeast(1),
        doubleBuffer = doubleBuffer,
        antialias = initialAntialias
    )

    private val contextImpl: ImageBuffer2DContext
        get() = imageBuffer.getContext()

    private var disposedFlag: Boolean = false
    override val isDisposed: Boolean
        get() = disposedFlag

    private var vblankActive: Boolean = false
    private var vblankCallback: ((Double?) -> Unit)? = null
    private var vblankWatcherId: Int = -1
    private var pointerEventsPassThroughFlag: Boolean = false
    private var presentedFrameCounter: Long = 0L

    private var onSurfaceResizedListener: ((Int, Int) -> Unit)? = null
    private var onBeforeFramePresentListener: ((frameIndex: Long, timestampMs: Double) -> Unit)? = null
    private var componentOnPointerEnter: ((PointerEvent) -> Unit)? = null
    private var componentOnPointerLeave: ((PointerEvent) -> Unit)? = null
    private var lastLocalPointerX: Float = 0f
    private var lastLocalPointerY: Float = 0f

    private val dirtyListener = ImageBufferDirtyListener { _, _, _, _, _ ->
        if (!disposedFlag) {
            node.invalidateBounds()
        }
    }

    private val beforePresentInvalidator: (Long, Double) -> Unit = { _, _ ->
        if (!disposedFlag) {
            node.invalidateBounds()
        }
    }

    init {
        node.intrinsicWidth = imageBuffer.width.toFloat()
        node.intrinsicHeight = imageBuffer.height.toFloat()

        imageBuffer.addDirtyListener(dirtyListener)
        canvasComponent?.addBeforeFramePresentListener(beforePresentInvalidator)

        node.customMeasure = { _, _, style ->
            val pref = node.preferredSize
            val targetW = pref?.width?.takeIf { it > 0 } ?: imageBuffer.width.coerceAtLeast(1)
            val targetH = pref?.height?.takeIf { it > 0 } ?: imageBuffer.height.coerceAtLeast(1)
            val padW = style.padding.left + style.padding.right
            val padH = style.padding.top + style.padding.bottom
            Size(
                width = (targetW + padW).coerceAtLeast(1),
                height = (targetH + padH).coerceAtLeast(1)
            )
        }

        node.customPaint = { ctx, bounds, style ->
            val pad = style.padding
            val drawX = bounds.x + pad.left
            val drawY = bounds.y + pad.top
            val drawW = (bounds.width - pad.left - pad.right).coerceAtLeast(0)
            val drawH = (bounds.height - pad.top - pad.bottom).coerceAtLeast(0)
            if (drawW > 0 && drawH > 0) {
                val bufW = imageBuffer.width.coerceAtLeast(1)
                val bufH = imageBuffer.height.coerceAtLeast(1)
                val rgbaBytes = imageBuffer.directRgbaBytes()
                if (rgbaBytes.size >= bufW * bufH * 4) {
                    val snapshot = com.nexustools.genericui.image.ImageBuffer(
                        width = bufW,
                        height = bufH,
                        pixelFormat = PixelFormat.RGBA_8888,
                        colorSpace = imageBuffer.colorSpace,
                        bytes = rgbaBytes
                    )
                    ctx.save()
                    ctx.setGlobalAlpha(style.opacity.coerceIn(0f, 1f))
                    ctx.imageSmoothingEnabled = contextImpl.antialias
                    ctx.drawImage(
                        image = snapshot,
                        sx = 0f,
                        sy = 0f,
                        sWidth = bufW.toFloat(),
                        sHeight = bufH.toFloat(),
                        dx = drawX.toFloat(),
                        dy = drawY.toFloat(),
                        dWidth = drawW.toFloat(),
                        dHeight = drawH.toFloat()
                    )
                    ctx.restore()
                }
            }
        }
    }

    fun getContext(): ImageBuffer2DContext = getRenderingContext()

    fun resizeSurface(width: Int, height: Int) {
        if (disposedFlag) return
        val nextW = width.coerceAtLeast(1)
        val nextH = height.coerceAtLeast(1)
        if (imageBuffer.width == nextW && imageBuffer.height == nextH) return
        imageBuffer.resize(nextW, nextH)
        node.intrinsicWidth = nextW.toFloat()
        node.intrinsicHeight = nextH.toFloat()
        onSurfaceResizedListener?.invoke(nextW, nextH)
        node.markMeasureDirty()
        node.invalidateBounds()
    }

    fun swapBuffers() {
        if (disposedFlag) return
        presentBufferFrame(null)
    }

    fun getPixel(x: Int, y: Int): RgbaColor {
        val w = imageBuffer.width
        val h = imageBuffer.height
        if (x !in 0 until w || y !in 0 until h) return RgbaColor.TRANSPARENT
        val rgba = imageBuffer.directRgbaBytes()
        val idx = (y * w + x) shl 2
        if (idx + 3 >= rgba.size) return RgbaColor.TRANSPARENT
        return RgbaColor(
            r = rgba[idx].toInt() and 0xFF,
            g = rgba[idx + 1].toInt() and 0xFF,
            b = rgba[idx + 2].toInt() and 0xFF,
            a = rgba[idx + 3].toInt() and 0xFF
        )
    }

    fun readPixels(
        x: Int = 0,
        y: Int = 0,
        width: Int = imageBuffer.width,
        height: Int = imageBuffer.height
    ): ByteArray = imageBuffer.readPixelsRgba(x, y, width, height)

    override fun setPreferredSize(size: Size?) {
        super.setPreferredSize(size)
        if (size != null && (size.width > 0 || size.height > 0)) {
            val nextW = (if (size.width > 0) size.width else imageBuffer.width).coerceAtLeast(1)
            val nextH = (if (size.height > 0) size.height else imageBuffer.height).coerceAtLeast(1)
            resizeSurface(nextW, nextH)
        }
    }

    override fun getRenderingContext(): ImageBuffer2DContext = contextImpl

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        vblankActive = active
        vblankCallback = if (active) onVBlankFrame else null
        if (active && !disposedFlag) {
            if (vblankWatcherId <= 0) {
                vblankWatcherId = Animator.watchVBlank { _, timestampMs, _ ->
                    if (vblankActive && !disposedFlag) {
                        vblankCallback?.invoke(timestampMs)
                        presentBufferFrame(timestampMs)
                    }
                }
            }
        } else {
            stopVBlankWatcher()
        }
    }

    override fun requestSingleRender() {
        if (disposedFlag) return
        vblankCallback?.invoke(null)
        presentBufferFrame(null)
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        pointerEventsPassThroughFlag = passThrough
        node.attributes["data-pointer-events-passthrough"] = passThrough.toString()
    }

    fun isPointerEventsPassThrough(): Boolean = pointerEventsPassThroughFlag

    override fun getSurfaceWidth(): Int = imageBuffer.width

    override fun getSurfaceHeight(): Int = imageBuffer.height

    override fun getDevicePixelRatio(): Float = 1.0f

    override fun captureFrameToRgba(): ByteArray =
        contextImpl.presentFrontBufferRgba()

    override fun captureFrameToJpeg(quality: Int): ByteArray {
        val w = getSurfaceWidth().coerceAtLeast(1)
        val h = getSurfaceHeight().coerceAtLeast(1)
        val rgba = captureFrameToRgba()
        val buf = com.nexustools.genericui.image.ImageBuffer(
            width = w,
            height = h,
            pixelFormat = PixelFormat.RGBA_8888,
            bytes = rgba
        )
        return ImageLoader.encode(buf, ImageEncodeOptions(format = ImageFormat.JPEG, quality = quality))
    }

    override fun setOnSurfaceResizedListener(listener: ((Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
    }

    override fun setOnBeforeFramePresentListener(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {
        onBeforeFramePresentListener = listener
    }

    override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {
        node.onPointerDown = listener?.let { cb ->
            { event -> cb(toLocalPointerEvent(event)) }
        }
    }

    override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) {
        node.onPointerMove = listener?.let { cb ->
            { event ->
                val local = toLocalPointerEvent(event)
                lastLocalPointerX = local.x
                lastLocalPointerY = local.y
                cb(local)
            }
        }
    }

    override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) {
        node.onPointerUp = listener?.let { cb ->
            { event ->
                val local = toLocalPointerEvent(event)
                lastLocalPointerX = local.x
                lastLocalPointerY = local.y
                cb(local)
            }
        }
    }

    override fun setOnPointerEnter(listener: ((PointerEvent) -> Unit)?) {
        componentOnPointerEnter = listener
    }

    override fun setOnPointerLeave(listener: ((PointerEvent) -> Unit)?) {
        componentOnPointerLeave = listener
    }

    override fun setHoveredState(hovered: Boolean) {
        val wasHovered = isHoveredState()
        super<AlienImpl>.setHoveredState(hovered)
        if (!wasHovered && hovered) {
            componentOnPointerEnter?.invoke(PointerEvent(x = lastLocalPointerX, y = lastLocalPointerY))
        } else if (wasHovered && !hovered) {
            componentOnPointerLeave?.invoke(PointerEvent(x = lastLocalPointerX, y = lastLocalPointerY))
        }
    }

    override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) {
        node.onContextMenu = listener?.let { cb ->
            { event -> cb(toLocalPointerEvent(event)) }
        }
    }

    override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) {
        node.onWheel = listener?.let { cb ->
            { event -> cb(toLocalWheelEvent(event)) }
        }
    }

    private fun presentBufferFrame(timestampMs: Double?) {
        if (disposedFlag) return
        val nextFrame = presentedFrameCounter + 1L
        presentedFrameCounter = nextFrame
        onBeforeFramePresentListener?.invoke(nextFrame, timestampMs ?: 0.0)
        imageBuffer.swapBuffers()
        node.invalidateBounds()
    }

    private fun toLocalPointerEvent(event: PointerEvent): PointerEvent {
        val content = node.contentBounds
        val originX = if (content.width > 0 && content.height > 0) content.x else node.bounds.x
        val originY = if (content.width > 0 && content.height > 0) content.y else node.bounds.y
        return event.copy(
            x = event.x - originX,
            y = event.y - originY
        )
    }

    private fun toLocalWheelEvent(event: WheelEvent): WheelEvent {
        val content = node.contentBounds
        val originX = if (content.width > 0 && content.height > 0) content.x else node.bounds.x
        val originY = if (content.width > 0 && content.height > 0) content.y else node.bounds.y
        return event.copy(
            x = event.x - originX,
            y = event.y - originY
        )
    }

    private fun stopVBlankWatcher() {
        val id = vblankWatcherId
        vblankWatcherId = -1
        if (id > 0) {
            Animator.unwatchVBlank(id)
        }
    }

    override fun dispose() {
        if (disposedFlag) return
        disposedFlag = true
        vblankActive = false
        vblankCallback = null
        stopVBlankWatcher()
        onSurfaceResizedListener = null
        onBeforeFramePresentListener = null
        imageBuffer.removeDirtyListener(dirtyListener)
        canvasComponent?.removeBeforeFramePresentListener(beforePresentInvalidator)
        imageBuffer.dispose()
        super.dispose()
    }
}
