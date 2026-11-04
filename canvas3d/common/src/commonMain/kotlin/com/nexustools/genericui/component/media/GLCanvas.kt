package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.canvas3d.Canvas3DExtension
import com.nexustools.genericui.ext.canvas3d.overlay.Canvas3DOverlayHost
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuTexture
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.nativeimpl.media.GLCanvasNativeImpl

/**
 * Dedicated OpenGL ES 2.0 / 3.0 / 3.2 canvas extending [AbstractCanvas].
 *
 * On Windows, [GLCanvas] uses Google ANGLE (`libEGL.dll` + `libGLESv2.dll`) so OpenGL ES shaders
 * and draw calls run with hardware acceleration over Direct3D 11 / Vulkan.
 */
open class GLCanvas(
    val config: GLCanvasConfig = GLCanvasConfig(),
    width: Int = 800,
    height: Int = 600
) : AbstractCanvas<GLContext>() {

    private var fallbackGlContext: GLContext? = null

    val gpuContext: GpuOffscreenContext
        get() = Canvas3DExtension.acquireGpuOffscreenContext(preferVulkan = false)

    var onSurfaceCreated: ((GLContext, Int, Int) -> Unit)? = null
        set(value) {
            field = value
            typedNative<GLCanvasNativeImpl<*>>()?.setOnSurfaceCreated(value)
        }

    var onSurfaceResized: ((GLContext, Int, Int) -> Unit)? = null
        set(value) {
            field = value
            typedNative<GLCanvasNativeImpl<*>>()?.setOnSurfaceResized(value)
        }

    init {
        this.widthPx = width
        this.heightPx = height
    }

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
     * Wraps this [GLCanvas] in a [Canvas3DOverlayHost] with the given [overlay] attached.
     */
    fun attachOverlay(overlay: AbstractOverlay): Canvas3DOverlayHost =
        Canvas3DOverlayHost(canvas = this, initialOverlay = overlay)

    /**
     * Wraps this [GLCanvas] in a [Canvas3DOverlayHost] with an optional [overlay] (`null` by default).
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

    protected override fun createNativeImpl(factory: NativeFactory): GLCanvasNativeImpl<*> =
        Canvas3DExtension.createGLCanvasNative(
            config = config,
            width = (widthPx ?: 800).coerceAtLeast(1),
            height = (heightPx ?: 600).coerceAtLeast(1)
        )

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<GLCanvasNativeImpl<*>>() ?: return
        impl.setOnSurfaceCreated(onSurfaceCreated)
        impl.setOnSurfaceResized(onSurfaceResized)
    }
}
