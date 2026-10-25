package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.max
import kotlin.math.min

/**
 * Universal hardware-accelerated 2D surface and image buffer in `:extensions:imageprocessing`
 * extending `:common`'s [com.nexustools.genericui.image.ImageBuffer].
 *
 * Used both as the backing surface of `:extensions:canvas2d`'s `Canvas2D` and directly for
 * offscreen 2D rendering via [getContext] (`imageBuffer.getContext()`).
 *
 * - Supports a [doubleBuffer] flag (`false` by default; `true` when used by `Canvas2D`):
 *   - On **Web** (`:extensions:imageprocessing:web` -> `WebImageBuffer`), uses a browser 2D surface
 *     with `enableDirtyTracking = false` and no extra double-buffer swap code because HTML5 surfaces
 *     natively double-buffer and composite automatically in the browser.
 *   - On **System targets** (`:extensions:imageprocessing:sharedSystem` -> `SystemGpuImageBuffer`),
 *     uses a direct Vulkan / OpenGL ES surface (`gpuTexture` + direct [bytes]) when `doubleBuffer == false`,
 *     or an offscreen back buffer committed on [flush]/[swapBuffers] when `doubleBuffer == true`, which
 *     [getContext] works on directly via Vulkan (`#version 450`) and OpenGL ES (`#version 300 es`) shaders.
 */
abstract class ImageBuffer(
    width: Int = 800,
    height: Int = 600,
    val doubleBuffer: Boolean = false,
    val enableDirtyTracking: Boolean = true,
    pixelFormat: PixelFormat = PixelFormat.RGBA_8888,
    colorSpace: ImageColorSpace = ImageColorSpace.SRGB,
    bytes: ByteArray = ByteArray(width.coerceAtLeast(1) * height.coerceAtLeast(1) * pixelFormat.bytesPerPixel)
) : com.nexustools.genericui.image.ImageBuffer(
    width = width,
    height = height,
    pixelFormat = pixelFormat,
    colorSpace = colorSpace,
    bytes = bytes
) {
    abstract val backendTypeName: String
    abstract val backendDescription: String
    abstract val flushCount: Long

    val swapCount: Long
        get() = flushCount

    /**
     * Returns the accelerated [ImageBuffer2DContext] that operates directly on this [ImageBuffer].
     */
    abstract val context2D: ImageBuffer2DContext

    /**
     * Fetches the 2D rendering context ([ImageBuffer2DContext]) that operates directly
     * on this [ImageBuffer], allowing [ImageBuffer] to be used both as the backend of `Canvas2D`
     * and for offscreen 2D rendering.
     */
    open fun getContext(contextType: String = "2d"): ImageBuffer2DContext = context2D

    // -------------------------------------------------------------------------
    // Dirty tracking for double-buffered System surfaces (no-op on Web)
    // -------------------------------------------------------------------------

    private val dirtyListeners = mutableListOf<ImageBufferDirtyListener>()

    var isDirty: Boolean = false
        protected set

    var dirtyMinX: Int = 0
        protected set

    var dirtyMinY: Int = 0
        protected set

    var dirtyMaxX: Int = 0
        protected set

    var dirtyMaxY: Int = 0
        protected set

    fun addDirtyListener(listener: ImageBufferDirtyListener) {
        if (!enableDirtyTracking) return
        if (!dirtyListeners.contains(listener)) {
            dirtyListeners.add(listener)
        }
    }

    fun removeDirtyListener(listener: ImageBufferDirtyListener) {
        dirtyListeners.remove(listener)
    }

    /**
     * Marks a region of this [ImageBuffer] as modified and notifies registered [ImageBufferDirtyListener]s.
     * On `:web` ([enableDirtyTracking] == `false`), this method is a fast no-op.
     */
    open fun markDirty(
        x: Int = 0,
        y: Int = 0,
        dirtyWidth: Int = this.width,
        dirtyHeight: Int = this.height
    ) {
        if (!enableDirtyTracking) return
        val x0 = x.coerceIn(0, this.width)
        val y0 = y.coerceIn(0, this.height)
        val x1 = (x + max(0, dirtyWidth)).coerceIn(0, this.width)
        val y1 = (y + max(0, dirtyHeight)).coerceIn(0, this.height)
        if (!isDirty) {
            isDirty = true
            dirtyMinX = x0
            dirtyMinY = y0
            dirtyMaxX = x1
            dirtyMaxY = y1
        } else {
            dirtyMinX = min(dirtyMinX, x0)
            dirtyMinY = min(dirtyMinY, y0)
            dirtyMaxX = max(dirtyMaxX, x1)
            dirtyMaxY = max(dirtyMaxY, y1)
        }
        if (dirtyListeners.isNotEmpty()) {
            val snapshot = dirtyListeners.toList()
            for (listener in snapshot) {
                listener.onImageBufferDirty(this, dirtyMinX, dirtyMinY, dirtyMaxX, dirtyMaxY)
            }
        }
    }

    open fun clearDirtyState() {
        isDirty = false
        dirtyMinX = 0
        dirtyMinY = 0
        dirtyMaxX = 0
        dirtyMaxY = 0
    }

    /**
     * Resizes this [ImageBuffer] surface in-place.
     */
    abstract fun resize(newWidth: Int, newHeight: Int)

    /**
     * Flushes any pending GPU commands (and swaps back-to-front on System targets when [doubleBuffer] is `true`).
     */
    abstract fun flush()

    /**
     * Alias for [flush] when used as a double-buffered surface.
     */
    fun swapBuffers() {
        flush()
    }

    /**
     * Fetches RGBA8888 pixels from `(x, y, width, height)` directly from this [ImageBuffer].
     */
    abstract fun readPixelsRgba(
        x: Int = 0,
        y: Int = 0,
        width: Int = this.width,
        height: Int = this.height
    ): ByteArray

    /**
     * Writes RGBA8888 pixels into `(dx, dy, width, height)` directly onto this [ImageBuffer].
     */
    abstract fun writePixelsRgba(
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        dx: Int = 0,
        dy: Int = 0
    )

    /**
     * Returns the direct RGBA8888 byte array backing this [ImageBuffer].
     */
    abstract fun directRgbaBytes(): ByteArray

    /**
     * Returns the direct BGRA8888 byte array view for native OS presentation (Win32/GDI/Direct2D/Qt).
     */
    abstract fun directBgraBytes(): ByteArray

    /**
     * Returns a snapshot [com.nexustools.genericui.image.ImageBuffer] of the current surface contents.
     */
    open fun toImageBuffer(): com.nexustools.genericui.image.ImageBuffer {
        flush()
        return copy()
    }

    /**
     * Releases underlying GPU textures or DOM resources.
     */
    abstract fun dispose()

    companion object {
        private var platformFactory: ((width: Int, height: Int, doubleBuffer: Boolean, antialias: Boolean) -> ImageBuffer)? = null

        fun registerFactory(
            factory: (width: Int, height: Int, doubleBuffer: Boolean, antialias: Boolean) -> ImageBuffer
        ) {
            platformFactory = factory
        }

        /**
         * Creates an `:extensions:imageprocessing` [ImageBuffer] surface (`SystemGpuImageBuffer` on
         * System targets, or `WebImageBuffer` on Web).
         */
        fun create(
            width: Int = 800,
            height: Int = 600,
            doubleBuffer: Boolean = false,
            antialias: Boolean = true
        ): ImageBuffer {
            platformFactory?.let { return it(width, height, doubleBuffer, antialias) }
            return DefaultCpuImageBuffer(
                width = width,
                height = height,
                doubleBuffer = doubleBuffer,
                initialAntialias = antialias
            )
        }
    }
}
