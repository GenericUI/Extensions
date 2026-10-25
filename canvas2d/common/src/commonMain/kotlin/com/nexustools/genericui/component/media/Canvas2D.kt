package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.ext.gpu.VBlankWatcher
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferDirtyListener
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
) : AbstractCanvas<ImageBuffer2DContext>() {

    private var backingImageBuffer: ImageBuffer? = null
    private var attachedDirtyBuffer: ImageBuffer? = null
    private var pendingPresentationScheduled: Boolean = false

    /**
     * Optional hook invoked whenever the double-buffered surface is swapped and presented.
     */
    var onSurfacePresented: (() -> Unit)? = null

    /**
     * Counter of automatic or explicit surface presentations.
     */
    var presentedFrameCount: Long = 0L
        private set

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
        buf.swapBuffers()
        presentedFrameCount++
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
        imageBuffer.swapBuffers()
        presentedFrameCount++
        onSurfacePresented?.invoke()
    }

    override fun resolveRenderingContext(): ImageBuffer2DContext? {
        super.resolveRenderingContext()?.let {
            it.antialias = antialias
            ensureDirtyListenerAttached(it.imageBuffer)
            return it
        }
        return imageBuffer.getContext().also {
            it.antialias = antialias
        }
    }

    override fun createNativeImpl(factory: NativeFactory): Canvas2DNativeImpl<*> =
        Canvas2DExtension.createCanvas2DNative(
            width = (widthPx ?: 800).coerceAtLeast(1),
            height = (heightPx ?: 600).coerceAtLeast(1)
        )
}
