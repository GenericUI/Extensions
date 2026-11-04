package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.canvas3d.Canvas3DExtension
import com.nexustools.genericui.ext.canvas3d.overlay.Canvas3DOverlayHost
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuTexture
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.nativeimpl.media.VKCanvasNativeImpl

/**
 * Dedicated Vulkan 1.1 / 1.2 / 1.3 canvas extending [AbstractCanvas].
 */
open class VKCanvas(
    val config: VKCanvasConfig = VKCanvasConfig(),
    width: Int = 800,
    height: Int = 600
) : AbstractCanvas<VKContext>() {

    private var fallbackVkContext: VKContext? = null

    val gpuContext: GpuOffscreenContext
        get() = Canvas3DExtension.acquireGpuOffscreenContext(preferVulkan = true)

    var onSurfaceCreated: ((VKContext, Int, Int) -> Unit)? = null
        set(value) {
            field = value
            typedNative<VKCanvasNativeImpl<*>>()?.setOnSurfaceCreated(value)
        }

    var onSurfaceResized: ((VKContext, Int, Int) -> Unit)? = null
        set(value) {
            field = value
            typedNative<VKCanvasNativeImpl<*>>()?.setOnSurfaceResized(value)
        }

    init {
        this.widthPx = width
        this.heightPx = height
    }

    /**
     * Uploads a `:common` [ImageBuffer] to a Vulkan device texture image on this canvas's [VKContext].
     */
    fun uploadImageBufferTexture(imageBuffer: ImageBuffer): Long {
        val ctx = resolveRenderingContext() ?: return 0L
        val rgba = imageBuffer.toRgba8888()
        return ctx.createTextureImageRgba(rgba.width, rgba.height, rgba.bytes)
    }

    /**
     * Uploads an [ImageBuffer] to the shared `:extensions:gpu` [GpuOffscreenContext].
     */
    fun uploadGpuTexture(imageBuffer: ImageBuffer): GpuTexture =
        gpuContext.uploadTexture(imageBuffer)

    /**
     * Wraps this [VKCanvas] in a [Canvas3DOverlayHost] with the given [overlay] attached.
     */
    fun attachOverlay(overlay: AbstractOverlay): Canvas3DOverlayHost =
        Canvas3DOverlayHost(canvas = this, initialOverlay = overlay)

    /**
     * Wraps this [VKCanvas] in a [Canvas3DOverlayHost] with an optional [overlay] (`null` by default).
     */
    fun withOverlay(overlay: AbstractOverlay? = null): Canvas3DOverlayHost =
        Canvas3DOverlayHost(canvas = this, initialOverlay = overlay)

    public override fun resolveRenderingContext(): VKContext? {
        super.resolveRenderingContext()?.let { return it }
        if (fallbackVkContext == null) {
            fallbackVkContext = Canvas3DExtension.createVKContext(
                config = config,
                width = (widthPx ?: 800).coerceAtLeast(1),
                height = (heightPx ?: 600).coerceAtLeast(1)
            )
        }
        return fallbackVkContext
    }

    protected override fun createNativeImpl(factory: NativeFactory): VKCanvasNativeImpl<*> =
        Canvas3DExtension.createVKCanvasNative(
            config = config,
            width = (widthPx ?: 800).coerceAtLeast(1),
            height = (heightPx ?: 600).coerceAtLeast(1)
        )

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<VKCanvasNativeImpl<*>>() ?: return
        impl.setOnSurfaceCreated(onSurfaceCreated)
        impl.setOnSurfaceResized(onSurfaceResized)
    }
}
