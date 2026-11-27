package com.nexustools.genericui.ext.canvas3d.alien

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLBackendKind
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLContextInfo
import com.nexustools.genericui.component.media.GLContextVersion
import com.nexustools.genericui.component.media.GLShadingLanguage
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.component.media.VBlankSubscription
import com.nexustools.genericui.component.media.VKBackendKind
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.component.media.VKContextInfo
import com.nexustools.genericui.component.media.VKDeviceType
import com.nexustools.genericui.desktop.canvas.DesktopGLContextImpl
import com.nexustools.genericui.desktop.canvas.DesktopVKContextImpl
import com.nexustools.genericui.enums.bootstrap.BootstrapPointerEvents
import com.nexustools.genericui.ext.alien.AlienCssStyleResolver
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienResolvedVisualStyle
import com.nexustools.genericui.ext.alien.impl.AlienImpl
import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.VBlankFrameListener
import com.nexustools.genericui.ext.gpu.VBlankWatcher
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageEncodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Self-rendered Alien [Canvas3DNativeImpl] implementation (`<canvas>` element in an Alien scene graph).
 *
 * Bridges `:extensions:graphics:canvas3d` ([GLContext] / [VKContext]) to Alien's compositor
 * (`ImageBuffer2DContext`) via:
 * - Offscreen FBO + `glReadPixels` when a genuine hardware GL/VK context is available, OR
 * - [AlienOffscreenGlContext] WebGL 1 / WebGL 2 / GLES 3 software/FBO rasterization when running
 *   headless (CI, headless JVM, `mingwX64Test`, or offscreen Alien sessions).
 *
 * Calling [present] / [swapBuffers] reads the active framebuffer into an RGBA [ImageBuffer] and
 * invalidates [node] bounds so the next Alien repaint composites it cleanly with CSS sizing,
 * clipping (`border-radius`, padding), and opacity.
 */
class Canvas3DAlienImpl(
    private val component: Canvas3D,
    override val activeAcceleration: GpuAcceleration = component.resolvedAcceleration,
    session: AlienRenderSession? = null,
    hardwareGlDelegate: GLContext? = null,
    initialVkContext: VKContext? = null
) : AlienImpl(
    componentClassPrefix = "canvas3d",
    tagName = "canvas",
    session = session
), Canvas3DNativeImpl<Any?> {

    override val config: Canvas3DConfig = component.config
    override val activeBackend: GpuBackendType = component.backend

    private var surfaceW: Int = (component.widthPx ?: component.initialWidth).coerceAtLeast(1)
    private var surfaceH: Int = (component.heightPx ?: component.initialHeight).coerceAtLeast(1)

    private val internalGlContext: AlienOffscreenGlContext?
    private val internalVkContext: VKContext?
    private val primaryContext: GpuRenderContext

    override val vkContext: VKContext?
        get() = internalVkContext

    override val glContext: GLContext?
        get() = internalGlContext

    private var onSurfaceCreatedListener: ((GpuRenderContext, Int, Int) -> Unit)? = null
    private var onSurfaceResizedListener: ((GpuRenderContext, Int, Int) -> Unit)? = null
    private var onBeforeFramePresentListener: ((frameIndex: Long, timestampMs: Double) -> Unit)? = null

    private var vblankActive: Boolean = false
    private var vblankCallback: ((Double?) -> Unit)? = null
    private var vblankSubscription: VBlankSubscription? = null

    private var presentedBuffer: ImageBuffer? = null
    private var presentedFrameCounter: Long = 0L
    private var lastSyncedGlVersion: Long = -1L

    init {
        when (activeBackend) {
            GpuBackendType.OPENGL_ES -> {
                val hwCandidate: GLContext? = hardwareGlDelegate ?: resolveOptionalHardwareGlDelegate()
                val offscreenGl = AlienOffscreenGlContext(
                    config = config.gl,
                    initialWidth = surfaceW,
                    initialHeight = surfaceH,
                    hardwareDelegate = hwCandidate,
                    contextInfo = GLContextInfo(
                        backend = GLBackendKind.WEBGL2,
                        version = config.gl.version,
                        shadingLanguage = when (config.gl.version) {
                            GLContextVersion.ES_3_0 -> GLShadingLanguage.GLSL_ES_300
                            GLContextVersion.ES_3_1 -> GLShadingLanguage.GLSL_ES_310
                            GLContextVersion.ES_3_2 -> GLShadingLanguage.GLSL_ES_320
                        },
                        acceleration = if (hwCandidate != null) hwCandidate.acceleration else GpuAcceleration.SOFTWARE,
                        renderer = if (hwCandidate != null) {
                            "Alien Offscreen FBO (${hwCandidate.contextInfo.renderer})"
                        } else {
                            "Alien Offscreen Software WebGL2/GLES3"
                        },
                        vendor = "GenericUI Alien"
                    )
                )
                internalGlContext = offscreenGl
                internalVkContext = null
                primaryContext = offscreenGl
            }
            GpuBackendType.VULKAN -> {
                val vk = initialVkContext ?: resolveVulkanContext()
                internalVkContext = vk
                internalGlContext = null
                primaryContext = vk
            }
        }

        node.intrinsicWidth = surfaceW.toFloat()
        node.intrinsicHeight = surfaceH.toFloat()

        node.customMeasure = { availW, availH, style ->
            measureCanvas(availW, availH, style)
        }
        node.customPaint = { ctx, bounds, style ->
            paintCanvas(ctx, bounds, style)
        }
    }

    private fun resolveOptionalHardwareGlDelegate(): GLContext? {
        if (activeAcceleration != GpuAcceleration.HARDWARE || !Canvas3DRegistry.hasProvider()) {
            return null
        }
        val candidate = Canvas3DRegistry.createRenderContext(
            backend = GpuBackendType.OPENGL_ES,
            accelerationPolicy = component.acceleration,
            config = config,
            width = surfaceW,
            height = surfaceH
        ) as? GLContext ?: return null

        // DesktopGLContextImpl (and its CPU-backed default subclasses) lack full DEPTH_TEST / CULL_FACE /
        // general GLSL expression evaluation in software mode, so only delegate when a non-CPU GLContext is supplied.
        if (candidate is DesktopGLContextImpl) {
            return null
        }
        return candidate
    }

    private fun resolveVulkanContext(): VKContext {
        if (activeAcceleration == GpuAcceleration.HARDWARE && Canvas3DRegistry.hasProvider()) {
            val candidate = Canvas3DRegistry.createRenderContext(
                backend = GpuBackendType.VULKAN,
                accelerationPolicy = component.acceleration,
                config = config,
                width = surfaceW,
                height = surfaceH
            ) as? VKContext
            if (candidate != null) return candidate
        }
        return DesktopVKContextImpl(
            config = config.vulkan,
            initialWidth = surfaceW,
            initialHeight = surfaceH,
            contextInfo = VKContextInfo(
                backend = VKBackendKind.VULKAN_NATIVE,
                apiVersion = config.vulkan.apiVersion,
                deviceName = "Alien Offscreen Software Vulkan",
                deviceType = VKDeviceType.CPU,
                acceleration = GpuAcceleration.SOFTWARE
            )
        )
    }

    private fun measureCanvas(availW: Int, availH: Int, style: AlienResolvedVisualStyle): Size {
        val explicitPref = node.preferredSize
        val rawWidthCss = style.widthCss?.trim()?.takeUnless { it.equals("auto", ignoreCase = true) }
        val rawHeightCss = style.heightCss?.trim()?.takeUnless { it.equals("auto", ignoreCase = true) }

        val cssW = rawWidthCss?.let {
            AlienCssStyleResolver.parseLengthPx(it, style.fontSizePx, availW.toFloat())?.roundToInt()
        }?.takeIf { it > 0 } ?: explicitPref?.width?.takeIf { it > 0 }

        val cssH = rawHeightCss?.let {
            AlienCssStyleResolver.parseLengthPx(it, style.fontSizePx, availH.toFloat())?.roundToInt()
        }?.takeIf { it > 0 } ?: explicitPref?.height?.takeIf { it > 0 }

        val fallbackW = component.widthPx?.takeIf { it > 0 } ?: 300
        val fallbackH = component.heightPx?.takeIf { it > 0 } ?: 150

        val ratio = style.aspectRatio
        var targetW = cssW ?: if (cssH != null && ratio != null && ratio > 0f) {
            (cssH * ratio).roundToInt().coerceAtLeast(1)
        } else {
            fallbackW
        }
        var targetH = cssH ?: if (cssW != null && ratio != null && ratio > 0f) {
            (cssW / ratio).roundToInt().coerceAtLeast(1)
        } else {
            fallbackH
        }

        val maxW = style.maxWidthCss?.trim()?.takeUnless { it.equals("none", ignoreCase = true) }?.let {
            AlienCssStyleResolver.parseLengthPx(it, style.fontSizePx, availW.toFloat())?.roundToInt()
        }?.takeIf { it > 0 }
        val maxH = style.maxHeightCss?.trim()?.takeUnless { it.equals("none", ignoreCase = true) }?.let {
            AlienCssStyleResolver.parseLengthPx(it, style.fontSizePx, availH.toFloat())?.roundToInt()
        }?.takeIf { it > 0 }

        if (maxW != null && targetW > maxW) {
            targetW = maxW
        }
        if (maxH != null && targetH > maxH) {
            targetH = maxH
        }

        return Size(targetW.coerceAtLeast(1), targetH.coerceAtLeast(1))
    }

    private fun syncPresentedBufferFromContext(): ImageBuffer {
        val currentGl = internalGlContext
        val existing = presentedBuffer
        if (currentGl != null) {
            val ver = currentGl.defaultFramebufferVersion
            if (existing != null &&
                existing.width == surfaceW &&
                existing.height == surfaceH &&
                lastSyncedGlVersion == ver
            ) {
                return existing
            }
            val rgba = currentGl.readDefaultFramebufferRgba()
            val updated = ImageBuffer(
                width = surfaceW,
                height = surfaceH,
                pixelFormat = PixelFormat.RGBA_8888,
                bytes = rgba
            )
            presentedBuffer = updated
            lastSyncedGlVersion = ver
            return updated
        }

        val currentVk = internalVkContext
            ?: throw IllegalStateException("Canvas3DAlienImpl has neither GLContext nor VKContext")
        if (existing != null && existing.width == surfaceW && existing.height == surfaceH) {
            return existing
        }
        val rgba = currentVk.readPixels(0, 0, surfaceW, surfaceH)
        val updated = ImageBuffer(
            width = surfaceW,
            height = surfaceH,
            pixelFormat = PixelFormat.RGBA_8888,
            bytes = rgba
        )
        presentedBuffer = updated
        return updated
    }

    private fun paintCanvas(ctx: ImageBuffer2DContext, bounds: Rect, style: AlienResolvedVisualStyle) {
        if (bounds.width <= 0 || bounds.height <= 0) return

        val pad = style.padding
        val drawX = (bounds.x + pad.left).toFloat()
        val drawY = (bounds.y + pad.top).toFloat()
        val drawW = (bounds.width - pad.left - pad.right).coerceAtLeast(0).toFloat()
        val drawH = (bounds.height - pad.top - pad.bottom).coerceAtLeast(0).toFloat()
        if (drawW <= 0f || drawH <= 0f) return

        val frameBuffer = syncPresentedBufferFromContext()
        val radius = resolveBorderRadiusPx(style, drawW, drawH)

        ctx.save()
        ctx.setGlobalAlpha(style.opacity.coerceIn(0f, 1f))
        ctx.beginPath()
        if (radius > 0.5f) {
            ctx.roundRect(drawX, drawY, drawW, drawH, radius)
        } else {
            ctx.rect(drawX, drawY, drawW, drawH)
        }
        ctx.clip()
        ctx.drawImage(frameBuffer, drawX, drawY, drawW, drawH)
        ctx.restore()
    }

    private fun resolveBorderRadiusPx(style: AlienResolvedVisualStyle, w: Float, h: Float): Float {
        val raw = when {
            style.borderRadiusPercent != null -> (style.borderRadiusPercent / 100f) * min(w, h)
            style.borderRadiusPx > 0f -> style.borderRadiusPx
            style.cornerRadii != null -> style.cornerRadii.topLeft.resolvePx(min(w, h), 16f, style.fontSizePx) ?: 0f
            else -> 0f
        }
        return min(raw.coerceAtLeast(0f), min(w, h) * 0.5f)
    }

    /**
     * Captures the latest rendered frame into an RGBA [ImageBuffer], triggers the
     * `onBeforeFramePresentListener`, and invalidates [node] bounds for Alien compositing.
     */
    fun present(timestampMs: Double = 0.0): ImageBuffer {
        val rgba = captureFrameToRgba()
        val buf = ImageBuffer(
            width = surfaceW,
            height = surfaceH,
            pixelFormat = PixelFormat.RGBA_8888,
            bytes = rgba
        )
        presentedBuffer = buf
        lastSyncedGlVersion = internalGlContext?.defaultFramebufferVersion ?: -1L
        presentedFrameCounter++
        onBeforeFramePresentListener?.invoke(presentedFrameCounter, timestampMs)
        node.invalidateBounds()
        node.session?.repaintManager?.markDirty(node.bounds)
        return buf
    }

    /**
     * Swaps / presents the rendered 3D framebuffer into the backing RGBA [ImageBuffer] and
     * marks [node] dirty for the Alien repaint pass.
     */
    fun swapBuffers() {
        present(0.0)
    }

    /**
     * Resizes the underlying 3D drawing surface and notifies any registered `onSurfaceResized` listener.
     */
    fun resizeSurface(width: Int, height: Int) {
        val nextW = width.coerceAtLeast(1)
        val nextH = height.coerceAtLeast(1)
        if (nextW == surfaceW && nextH == surfaceH) return
        surfaceW = nextW
        surfaceH = nextH
        node.intrinsicWidth = nextW.toFloat()
        node.intrinsicHeight = nextH.toFloat()

        internalGlContext?.resizeSurface(nextW, nextH)
        val vk = internalVkContext
        if (vk is DesktopVKContextImpl) {
            vk.resizeSwapchain(nextW, nextH)
        }
        presentedBuffer = null
        lastSyncedGlVersion = -1L
        node.markMeasureDirty()
        node.invalidateBounds()
        onSurfaceResizedListener?.invoke(primaryContext, nextW, nextH)
    }

    override fun setPreferredSize(size: Size?) {
        super.setPreferredSize(size)
        if (size != null && size.width > 0 && size.height > 0) {
            resizeSurface(size.width, size.height)
        }
    }

    override fun getRenderingContext(): GpuRenderContext = primaryContext

    override fun setOnSurfaceCreated(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        onSurfaceCreatedListener = listener
        listener?.invoke(primaryContext, surfaceW, surfaceH)
    }

    override fun setOnSurfaceResized(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        onSurfaceResizedListener = listener
    }

    override fun setOnBeforeFramePresentListener(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {
        onBeforeFramePresentListener = listener
    }

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        vblankActive = active
        vblankSubscription?.dispose()
        vblankSubscription = null
        if (active) {
            val wrapped: (Double?) -> Unit = { ts ->
                onVBlankFrame(ts)
                present(ts ?: 0.0)
            }
            vblankCallback = wrapped
            if (node.session?.offscreenOnly != true) {
                vblankSubscription = VBlankWatcher.watch(
                    VBlankFrameListener { _, totalElapsedMs, _ ->
                        wrapped(totalElapsedMs)
                    }
                )
            }
        } else {
            vblankCallback = null
        }
    }

    override fun requestSingleRender() {
        val cb = vblankCallback
        if (cb != null) {
            cb(null)
        } else {
            present(0.0)
        }
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        node.styleSnapshot = node.styleSnapshot.copy(
            pointerEvents = if (passThrough) BootstrapPointerEvents.NONE else null
        )
        node.invalidateBounds()
    }

    override fun getSurfaceWidth(): Int = surfaceW

    override fun getSurfaceHeight(): Int = surfaceH

    override fun getDevicePixelRatio(): Float = 1.0f

    override fun captureFrameToRgba(): ByteArray {
        internalGlContext?.let { return it.readDefaultFramebufferRgba() }
        internalVkContext?.let { return it.readPixels(0, 0, surfaceW, surfaceH) }
        throw IllegalStateException("Canvas3DAlienImpl has no active GLContext or VKContext")
    }

    override fun captureFrameToJpeg(quality: Int): ByteArray {
        val rgba = captureFrameToRgba()
        val buf = ImageBuffer(
            width = surfaceW,
            height = surfaceH,
            pixelFormat = PixelFormat.RGBA_8888,
            bytes = rgba
        )
        return ImageLoader.encode(buf, ImageEncodeOptions(format = ImageFormat.JPEG, quality = quality.coerceIn(1, 100)))
    }

    override fun dispose() {
        vblankActive = false
        vblankCallback = null
        vblankSubscription?.dispose()
        vblankSubscription = null
        super.dispose()
    }
}
