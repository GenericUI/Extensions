package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.FramePresentTarget
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.ext.canvas2d.overlay.Canvas2DOverlayHost
import com.nexustools.genericui.ext.gpu.VBlankWatcher
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferDirtyListener
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.nativeimpl.media.Canvas2DNativeImpl

/**
 * Dedicated 2D vector and pixel drawing canvas extending [AbstractCanvas], backed by a
 * double-buffered `:extensions:imageprocessing` [ImageBuffer] (`doubleBuffer = true` by default).
 *
 * - Provides [getContext] (`canvas2D.getContext()`) returning an instance of [ImageBuffer2DContext]
 *   directly (`imageBuffer.getContext()`).
 * - On **System targets** (`sharedSystem`), attaches an [ImageBufferDirtyListener] to [imageBuffer]
 *   so that when [imageBuffer] is dirtied:
 *   - Inside a VBlank callback (`VBlankWatcher.isInVBlankCallback == true`), the double-buffered
 *     surface swaps and presents immediately at the end of that VBlank callback.
 *   - Outside a VBlank callback (`VBlankWatcher.isInVBlankCallback == false`), the double-buffered
 *     surface schedules a swap and presentation on the next VBlank tick.
 * - On **Web** (`:extensions:imageprocessing:web` / `:extensions:canvas2d:web`), dirty tracking and
 *   manual buffer swapping are disabled (`enableDirtyTracking = false`) because HTML5 `<canvas>`
 *   natively double-buffers and composites automatically.
 */
open class Canvas2D(
    width: Int = 800,
    height: Int = 600,
    val doubleBuffer: Boolean = true,
    initialAntialias: Boolean = true
) : AbstractCanvas<ImageBuffer2DContext>(), FramePresentTarget {

    private var backingImageBuffer: ImageBuffer? = null
    private var attachedDirtyBuffer: ImageBuffer? = null
    private var pendingPresentationScheduled: Boolean = false
    private val beforeFramePresentListeners = mutableListOf<(Long, Double) -> Unit>()
    private var lastNotifiedBeforePresentFrame: Long = -1L

    /**
     * Optional hook invoked whenever the double-buffered surface is swapped and presented.
     */
    var onSurfacePresented: (() -> Unit)? = null

    /**
     * Counter of automatic or explicit surface presentations.
     */
    final override var presentedFrameCount: Long = 0L
        private set

    override fun addBeforeFramePresentListener(listener: (frameIndex: Long, timestampMs: Double) -> Unit) {
        if (listener !in beforeFramePresentListeners) {
            beforeFramePresentListeners.add(listener)
        }
    }

    override fun removeBeforeFramePresentListener(listener: (frameIndex: Long, timestampMs: Double) -> Unit) {
        beforeFramePresentListeners.remove(listener)
    }

    internal fun notifyBeforeFramePresent(frameIndex: Long, timestampMs: Double = 0.0) {
        if (frameIndex > presentedFrameCount) {
            presentedFrameCount = frameIndex
        }
        if (frameIndex == lastNotifiedBeforePresentFrame) return
        lastNotifiedBeforePresentFrame = frameIndex
        if (beforeFramePresentListeners.isNotEmpty()) {
            for (listener in beforeFramePresentListeners.toList()) {
                listener(frameIndex, timestampMs)
            }
        }
    }

    private val dirtyListener = ImageBufferDirtyListener { _, _, _, _, _ ->
        if (!pendingPresentationScheduled) {
            pendingPresentationScheduled = true
            VBlankWatcher.notifySurfaceDirty {
                pendingPresentationScheduled = false
                presentDirtySurface()
            }
        }
    }

    /**
     * Smooth edge antialiasing and bilinear texture filtering (`true` by default).
     */
    var antialias: Boolean = initialAntialias
        set(value) {
            field = value
            super.resolveRenderingContext()?.antialias = value
            backingImageBuffer?.getContext()?.antialias = value
        }

    /**
     * The `:extensions:imageprocessing` [ImageBuffer] surface backing this [Canvas2D].
     */
    val imageBuffer: ImageBuffer
        get() {
            super.resolveRenderingContext()?.imageBuffer?.let { nativeBuf ->
                ensureDirtyListenerAttached(nativeBuf)
                return nativeBuf
            }
            val safeW = (widthPx ?: 800).coerceAtLeast(1)
            val safeH = (heightPx ?: 600).coerceAtLeast(1)
            val existing = backingImageBuffer
            if (existing != null) {
                if (existing.width != safeW || existing.height != safeH) {
                    existing.resize(safeW, safeH)
                }
                ensureDirtyListenerAttached(existing)
                return existing
            }
            return Canvas2DExtension.createImageBuffer(
                width = safeW,
                height = safeH,
                doubleBuffer = doubleBuffer,
                antialias = antialias
            ).also {
                backingImageBuffer = it
                ensureDirtyListenerAttached(it)
            }
        }

    init {
        this.widthPx = width
        this.heightPx = height
    }

    private fun ensureDirtyListenerAttached(buffer: ImageBuffer) {
        if (attachedDirtyBuffer === buffer) return
        attachedDirtyBuffer?.removeDirtyListener(dirtyListener)
        attachedDirtyBuffer = buffer
        buffer.addDirtyListener(dirtyListener)
    }

    private fun presentDirtySurface() {
        val buf = attachedDirtyBuffer ?: backingImageBuffer ?: super.resolveRenderingContext()?.imageBuffer ?: return
        val nextFrame = presentedFrameCount + 1L
        presentedFrameCount = nextFrame
        notifyBeforeFramePresent(nextFrame, 0.0)
        buf.swapBuffers()
        onSurfacePresented?.invoke()
    }

    /**
     * Fetches the [ImageBuffer2DContext] operating directly on [imageBuffer].
     */
    fun getContext(): ImageBuffer2DContext =
        resolveRenderingContext() ?: imageBuffer.getContext()

    /**
     * Flushes / swaps the underlying [imageBuffer] surface.
     */
    fun swapBuffers() {
        pendingPresentationScheduled = false
        val nextFrame = presentedFrameCount + 1L
        presentedFrameCount = nextFrame
        notifyBeforeFramePresent(nextFrame, 0.0)
        imageBuffer.swapBuffers()
        onSurfacePresented?.invoke()
    }

    /**
     * Draws an [com.nexustools.genericui.image.ImageBuffer] directly onto this 2D canvas surface at `(dx, dy)` using `:extensions:imageprocessing`.
     */
    fun drawImage(buffer: com.nexustools.genericui.image.ImageBuffer, dx: Float = 0f, dy: Float = 0f) {
        val ctx = resolveRenderingContext() ?: return
        Canvas2DExtension.drawImage(ctx, buffer, dx, dy)
    }

    /**
     * Extracts the current 2D canvas framebuffer as an [com.nexustools.genericui.image.ImageBuffer] via `:extensions:imageprocessing`.
     */
    fun toImageBuffer(): com.nexustools.genericui.image.ImageBuffer {
        val ctx = resolveRenderingContext()
            ?: return com.nexustools.genericui.image.ImageBuffer(surfaceWidth.coerceAtLeast(1), surfaceHeight.coerceAtLeast(1))
        return Canvas2DExtension.extractImageBuffer(ctx, 0, 0, ctx.width, ctx.height)
    }

    /**
     * Returns `true` when this surface is backed by an attached platform `Canvas2DNativeImpl`.
     */
    fun hasNativeRenderingContext(): Boolean = typedCanvasNative()?.getRenderingContext() != null

    /**
     * Synchronizes both the logical [widthPx]/[heightPx] and the underlying [imageBuffer] surface dimensions.
     */
    fun syncSurfaceSize(width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        if (widthPx != safeW || heightPx != safeH) {
            widthPx = safeW
            heightPx = safeH
        }
        val ctx = resolveRenderingContext()
        if (ctx != null && (ctx.width != safeW || ctx.height != safeH)) {
            ctx.resizeSurface(safeW, safeH)
        }
    }

    /**
     * Wraps this [Canvas2D] in a [Canvas2DOverlayHost] with the given [overlay] attached.
     */
    fun attachOverlay(overlay: AbstractOverlay): Canvas2DOverlayHost =
        Canvas2DOverlayHost(canvas = this, initialOverlay = overlay)

    /**
     * Wraps this [Canvas2D] in a [Canvas2DOverlayHost] with an optional [overlay] (`null` by default).
     */
    fun withOverlay(overlay: AbstractOverlay? = null): Canvas2DOverlayHost =
        Canvas2DOverlayHost(canvas = this, initialOverlay = overlay)

    public override fun resolveRenderingContext(): ImageBuffer2DContext? {
        super.resolveRenderingContext()?.let {
            it.antialias = antialias
            ensureDirtyListenerAttached(it.imageBuffer)
            return it
        }
        return imageBuffer.getContext().also {
            it.antialias = antialias
        }
    }

    protected override fun createNativeImpl(factory: NativeFactory): Canvas2DNativeImpl<*> =
        Canvas2DExtension.createCanvas2DNative(
            width = (widthPx ?: 800).coerceAtLeast(1),
            height = (heightPx ?: 600).coerceAtLeast(1)
        )

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<Canvas2DNativeImpl<*>>() ?: return
        impl.setOnBeforeFramePresentListener { frameIdx, tsMs ->
            notifyBeforeFramePresent(frameIdx, tsMs)
        }
    }
}
