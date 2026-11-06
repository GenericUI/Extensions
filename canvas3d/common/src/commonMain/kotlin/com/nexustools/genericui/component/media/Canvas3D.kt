package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.FramePresentTarget
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.canvas3d.Canvas3DExtension
import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import com.nexustools.genericui.ext.canvas3d.overlay.Canvas3DOverlayHost
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuTexture
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl

/**
 * Unified hardware-accelerated 3D rendering surface supporting Vulkan ([VKContext]) and
 * OpenGL ES 3.0+ / ANGLE / WebGL2 ([GLContext]) backends with explicit [GpuAccelerationPolicy] enforcement.
 */
open class Canvas3D(
    val backend: GpuBackendType = GpuBackendType.VULKAN,
    val acceleration: GpuAccelerationPolicy = GpuAccelerationPolicy.HARDWARE_ONLY,
    val config: Canvas3DConfig = Canvas3DConfig(),
    width: Int = 800,
    height: Int = 600
) : AbstractCanvas<GpuRenderContext>(), FramePresentTarget {

    val initialWidth: Int = width.coerceAtLeast(1)
    val initialHeight: Int = height.coerceAtLeast(1)

    val resolvedAcceleration: GpuAcceleration = Canvas3DRegistry.resolveAcceleration(backend, acceleration)

    private var fallbackRenderContext: GpuRenderContext? = null
    private val beforeFramePresentListeners = mutableListOf<(Long, Double) -> Unit>()
    private var lastNotifiedBeforePresentFrame: Long = -1L

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

    val context: GpuRenderContext?
        get() = resolveRenderingContext()

    val vkContext: VKContext?
        get() = resolveRenderingContext() as? VKContext

    val glContext: GLContext?
        get() = resolveRenderingContext() as? GLContext

    val gpuContext: GpuOffscreenContext
        get() = Canvas3DExtension.acquireGpuOffscreenContext(preferVulkan = backend == GpuBackendType.VULKAN)

    var onSurfaceCreated: ((GpuRenderContext, Int, Int) -> Unit)? = null
        set(value) {
            field = value
            val impl = typedNative<Canvas3DNativeImpl<*>>()
            if (impl != null) {
                impl.setOnSurfaceCreated(value)
            } else if (value != null) {
                resolveRenderingContext()?.let { ctx ->
                    value(ctx, (widthPx ?: initialWidth).coerceAtLeast(1), (heightPx ?: initialHeight).coerceAtLeast(1))
                }
            }
        }

    var onSurfaceResized: ((GpuRenderContext, Int, Int) -> Unit)? = null
        set(value) {
            field = value
            typedNative<Canvas3DNativeImpl<*>>()?.setOnSurfaceResized(value)
        }

    init {
        this.widthPx = initialWidth
        this.heightPx = initialHeight
    }

    /**
     * Uploads an [ImageBuffer] into a [GLTexture] on this canvas's [GLContext].
     */
    fun uploadImageBufferTexture(imageBuffer: ImageBuffer): GLTexture {
        val rgba = imageBuffer.toRgba8888()
        val gl = glContext
            ?: throw IllegalStateException("uploadImageBufferTexture requires an active GLContext (backend = OPENGL_ES)")
        val tex = gl.createTexture()
        gl.bindTexture(GLTextureTarget.TEXTURE_2D, tex)
        gl.texImage2D(
            target = GLTextureTarget.TEXTURE_2D,
            level = 0,
            format = GLTextureFormat.RGBA8,
            width = rgba.width,
            height = rgba.height,
            bytes = rgba.bytes
        )
        return tex
    }

    /**
     * Uploads an [ImageBuffer] directly into the `:extensions:gpu` hardware [GpuOffscreenContext].
     */
    fun uploadGpuTexture(imageBuffer: ImageBuffer): GpuTexture =
        gpuContext.uploadTexture(imageBuffer.toRgba8888())

    /**
     * Wraps this [Canvas3D] in a [Canvas3DOverlayHost] and attaches [overlay] above it.
     */
    fun attachOverlay(overlay: AbstractOverlay): Canvas3DOverlayHost =
        Canvas3DOverlayHost(canvas = this, initialOverlay = overlay)

    /**
     * Wraps this [Canvas3D] in a [Canvas3DOverlayHost] with an optional initial [overlay].
     */
    fun withOverlay(overlay: AbstractOverlay? = null): Canvas3DOverlayHost =
        Canvas3DOverlayHost(canvas = this, initialOverlay = overlay)

    public override fun resolveRenderingContext(): GpuRenderContext? {
        super.resolveRenderingContext()?.let { return it }
        if (fallbackRenderContext == null) {
            fallbackRenderContext = Canvas3DRegistry.createRenderContext(
                backend = backend,
                accelerationPolicy = acceleration,
                config = config,
                width = (widthPx ?: initialWidth).coerceAtLeast(1),
                height = (heightPx ?: initialHeight).coerceAtLeast(1)
            )
        }
        return fallbackRenderContext
    }

    protected override fun createNativeImpl(factory: NativeFactory): Canvas3DNativeImpl<*> =
        Canvas3DRegistry.createCanvas3DNative(this)

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<Canvas3DNativeImpl<*>>() ?: return
        impl.setOnSurfaceCreated(onSurfaceCreated)
        impl.setOnSurfaceResized(onSurfaceResized)
        impl.setOnBeforeFramePresentListener { frameIdx, tsMs ->
            notifyBeforeFramePresent(frameIdx, tsMs)
        }
    }

    companion object {
        fun detectSupport(backend: GpuBackendType): GpuBackendSupport =
            Canvas3DRegistry.detectSupport(backend)
    }
}
