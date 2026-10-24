package com.nexustools.genericui.ext.canvas3d

import com.nexustools.genericui.component.media.AbstractCanvas
import com.nexustools.genericui.component.media.VKCanvas
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuTexture
import com.nexustools.genericui.ext.gpu.VKCanvasConfig
import com.nexustools.genericui.ext.gpu.VKContext
import com.nexustools.genericui.image.ImageBuffer

/**
 * Extended Vulkan 1.1 / 1.2 / 1.3 (and MoltenVK / WebGPU) 3D canvas in `:extensions:canvas3d`
 * extending [VKCanvas] (and [AbstractCanvas]).
 *
 * Directly integrates with `:extensions:gpu` ([GpuOffscreenContext], [GpuTexture], [VKContext]) and
 * `:common`'s [ImageBuffer], while supporting `registerVBlankRender` (`deltaMs`) animation loops.
 */
open class VKCanvasExt(
    config: VKCanvasConfig = VKCanvasConfig(),
    width: Int = 800,
    height: Int = 600
) : VKCanvas(config, width, height) {

    private var fallbackVkContext: VKContext? = null

    val gpuContext: GpuOffscreenContext
        get() = Canvas3DExtension.acquireGpuOffscreenContext(preferVulkan = true)

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

    override fun resolveRenderingContext(): VKContext? {
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
}
