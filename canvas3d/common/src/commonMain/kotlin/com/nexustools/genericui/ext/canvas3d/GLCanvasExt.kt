package com.nexustools.genericui.ext.canvas3d

import com.nexustools.genericui.component.media.AbstractCanvas
import com.nexustools.genericui.component.media.GLCanvas
import com.nexustools.genericui.ext.canvas3d.overlay.Canvas3DOverlayHost
import com.nexustools.genericui.ext.gpu.GLCanvasConfig
import com.nexustools.genericui.ext.gpu.GLContext
import com.nexustools.genericui.ext.gpu.GLTexture
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuTexture
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.image.ImageBuffer

/**
 * Extended OpenGL ES 2.0 / 3.0 / 3.2 (and Google ANGLE on Windows) 3D canvas in `:extensions:canvas3d`
 * extending [GLCanvas] (and [AbstractCanvas]).
 *
 * Directly integrates with `:extensions:gpu` ([GpuOffscreenContext], [GpuTexture], [GLContext]) and
 * `:common`'s [ImageBuffer], while supporting `registerVBlankRender` (`deltaMs`) animation loops.
 */
open class GLCanvasExt(
    config: GLCanvasConfig = GLCanvasConfig(),
    width: Int = 800,
    height: Int = 600
) : GLCanvas(config, width, height) {

    private var fallbackGlContext: GLContext? = null

    val gpuContext: GpuOffscreenContext
        get() = Canvas3DExtension.acquireGpuOffscreenContext(preferVulkan = false)

    /**
     * Uploads a `:common` [ImageBuffer] to an OpenGL ES 2D texture on this canvas's [GLContext].
     */
    fun uploadImageBufferTexture(imageBuffer: ImageBuffer): GLTexture {
        val ctx = resolveRenderingContext() ?: return GLTexture(0, imageBuffer.width, imageBuffer.height)
        val rgba = imageBuffer.toRgba8888()
        val tex = ctx.createTexture()
        ctx.bindTexture(GLContext.GL_TEXTURE_2D, tex)
        ctx.texImage2DRgba(GLContext.GL_TEXTURE_2D, 0, rgba.width, rgba.height, rgba.bytes)
        return GLTexture(tex.id, rgba.width, rgba.height)
    }

    /**
     * Uploads an [ImageBuffer] to the shared `:extensions:gpu` [GpuOffscreenContext].
     */
    fun uploadGpuTexture(imageBuffer: ImageBuffer): GpuTexture =
        gpuContext.uploadTexture(imageBuffer)

    /**
     * Wraps this [GLCanvasExt] in a [Canvas3DOverlayHost] with the given [overlay] attached.
     */
    fun attachOverlay(overlay: AbstractOverlay): Canvas3DOverlayHost =
        Canvas3DOverlayHost(canvas = this, initialOverlay = overlay)

    /**
     * Wraps this [GLCanvasExt] in a [Canvas3DOverlayHost] with an optional [overlay] (`null` by default).
     */
    fun withOverlay(overlay: AbstractOverlay? = null): Canvas3DOverlayHost =
        Canvas3DOverlayHost(canvas = this, initialOverlay = overlay)

    public override fun resolveRenderingContext(): GLContext? {
        super.resolveRenderingContext()?.let { return it }
        if (fallbackGlContext == null) {
            fallbackGlContext = Canvas3DExtension.createGLContext(
                config = config,
                width = (widthPx ?: 800).coerceAtLeast(1),
                height = (heightPx ?: 600).coerceAtLeast(1)
            )
        }
        return fallbackGlContext
    }
}
