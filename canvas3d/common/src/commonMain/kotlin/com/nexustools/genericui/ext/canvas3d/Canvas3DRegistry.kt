package com.nexustools.genericui.ext.canvas3d

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLBackendKind
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLContextInfo
import com.nexustools.genericui.component.media.GLContextVersion
import com.nexustools.genericui.component.media.GLShadingLanguage
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.component.media.VKBackendKind
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.component.media.VKContextInfo
import com.nexustools.genericui.component.media.VKDeviceType
import com.nexustools.genericui.desktop.canvas.DesktopGLContextImpl
import com.nexustools.genericui.desktop.canvas.DesktopVKContextImpl
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl
import com.nexustools.genericui.style.css.ComputedStyle

/**
 * Registry managing platform support detection, acceleration policy validation, and native
 * surface/context creation for [Canvas3D].
 */
object Canvas3DRegistry {

    private class HeadlessCanvas3DNativeImpl(
        override val config: Canvas3DConfig,
        override val activeBackend: GpuBackendType,
        override val activeAcceleration: GpuAcceleration,
        private val renderContext: GpuRenderContext,
        private var surfaceW: Int,
        private var surfaceH: Int
    ) : Canvas3DNativeImpl<Any> {
        override val nativeWidget: Any = "headless-canvas-3d"
        override val componentClassPrefix: String = "canvas-3d"
        override val vkContext: VKContext? get() = renderContext as? VKContext
        override val glContext: GLContext? get() = renderContext as? GLContext

        private var vBlankActive = false
        private var frameCallback: ((Double?) -> Unit)? = null
        private var onSurfaceCreatedListener: ((GpuRenderContext, Int, Int) -> Unit)? = null
        private var onSurfaceResizedListener: ((GpuRenderContext, Int, Int) -> Unit)? = null
        private var passThrough = false
        private var classStr = componentClassPrefix
        private var visibleFlag = true
        private var enabledFlag = true
        private var opacityVal = 1f
        private var tooltipVal: String? = null
        private var preferredSizeVal: Size? = null

        override fun getRenderingContext(): GpuRenderContext = renderContext

        override fun setOnSurfaceCreated(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
            onSurfaceCreatedListener = listener
            listener?.invoke(renderContext, surfaceW, surfaceH)
        }

        override fun setOnSurfaceResized(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
            onSurfaceResizedListener = listener
        }

        override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
            vBlankActive = active
            frameCallback = if (active) onVBlankFrame else null
        }

        override fun requestSingleRender() {
            frameCallback?.invoke(null)
        }

        override fun setPointerEventsPassThrough(passThrough: Boolean) {
            this.passThrough = passThrough
        }

        override fun getSurfaceWidth(): Int = surfaceW
        override fun getSurfaceHeight(): Int = surfaceH
        override fun getDevicePixelRatio(): Float = 1.0f

        override fun captureFrameToRgba(): ByteArray = when (val ctx = renderContext) {
            is VKContext -> ctx.readPixels(0, 0, surfaceW, surfaceH)
            is GLContext -> ctx.readPixels(0, 0, surfaceW, surfaceH)
            else -> ByteArray((surfaceW * surfaceH * 4).coerceAtLeast(4))
        }

        override fun captureFrameToJpeg(quality: Int): ByteArray =
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

        override fun setClass(className: String) { classStr = className }
        override fun getClass(): String = classStr
        override fun addClass(className: String) { classStr = "$classStr $className".trim() }
        override fun removeClass(className: String) {
            classStr = classStr.split(Regex("\\s+")).filter { it != className }.joinToString(" ")
        }
        override fun hasClass(className: String): Boolean = className in classStr.split(Regex("\\s+"))
        override fun setType(type: BootstrapType?) {}
        override fun getType(): BootstrapType? = null
        override fun setOutline(outline: Boolean) {}
        override fun isOutline(): Boolean = false
        override fun setSize(size: BootstrapSize?) {}
        override fun getSize(): BootstrapSize? = null
        override fun setBackgroundType(type: BootstrapType?) {}
        override fun getBackgroundType(): BootstrapType? = null
        override fun setTextColorType(type: BootstrapType?) {}
        override fun getTextColorType(): BootstrapType? = null
        override fun setBorderType(type: BootstrapType?) {}
        override fun getBorderType(): BootstrapType? = null
        override fun setTextAlignment(alignment: TextAlignment?) {}
        override fun getTextAlignment(): TextAlignment? = null
        override fun setFontWeight(weight: FontWeight?) {}
        override fun getFontWeight(): FontWeight? = null
        override fun setTextTransform(transform: TextTransform?) {}
        override fun getTextTransform(): TextTransform? = null
        override fun setTextOverflow(overflow: TextOverflow?) {}
        override fun getTextOverflow(): TextOverflow? = null
        override fun setBorderRadius(radius: BorderRadiusStyle?) {}
        override fun getBorderRadius(): BorderRadiusStyle? = null
        override fun setShadow(shadow: ShadowElevation?) {}
        override fun getShadow(): ShadowElevation? = null
        override fun setDisplay(display: DisplayMode?) {}
        override fun getDisplay(): DisplayMode? = null
        override fun setFlexDirection(direction: FlexDirection?) {}
        override fun getFlexDirection(): FlexDirection? = null
        override fun setFlexWrap(wrap: FlexWrap?) {}
        override fun getFlexWrap(): FlexWrap? = null
        override fun setJustifyContent(justify: JustifyContent?) {}
        override fun getJustifyContent(): JustifyContent? = null
        override fun setAlignItems(align: AlignItems?) {}
        override fun getAlignItems(): AlignItems? = null
        override fun setAlignSelf(align: AlignSelf?) {}
        override fun getAlignSelf(): AlignSelf? = null
        override fun setOverflow(overflow: OverflowBehavior?) {}
        override fun getOverflow(): OverflowBehavior? = null
        override fun setPosition(position: PositionMode?) {}
        override fun getPosition(): PositionMode? = null
        override fun setMarginAll(scale: SpacingScale?) {}
        override fun getMarginAll(): SpacingScale? = null
        override fun setPaddingAll(scale: SpacingScale?) {}
        override fun getPaddingAll(): SpacingScale? = null
        override fun setVisible(visible: Boolean) { visibleFlag = visible }
        override fun isVisible(): Boolean = visibleFlag
        override fun setEnabled(enabled: Boolean) { enabledFlag = enabled }
        override fun isEnabled(): Boolean = enabledFlag
        override fun setOpacity(opacity: Float) { opacityVal = opacity }
        override fun getOpacity(): Float = opacityVal
        override fun setTooltipText(tooltip: String?) { tooltipVal = tooltip }
        override fun getTooltipText(): String? = tooltipVal
        override fun setAttribute(name: String, value: String?) {}
        override fun applyComputedStyle(style: ComputedStyle) {}
        override fun setPreferredSize(size: Size?) { preferredSizeVal = size }
        override fun getBounds(): Rect = Rect(0, 0, surfaceW, surfaceH)
        override fun setPadding(padding: EdgeInsets) {}
        override fun setMargin(margin: EdgeInsets) {}
        override fun setOnClick(listener: (() -> Unit)?) {}
        override fun setOnDoubleClick(listener: (() -> Unit)?) {}
        override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) {}
        override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) {}
        override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) {}
        override fun dispose() {}
    }

    private var supportDetector: ((GpuBackendType) -> GpuBackendSupport)? = null
    private var canvas3DFactory: ((Canvas3D, GpuAcceleration) -> Canvas3DNativeImpl<*>)? = null
    private var renderContextFactory: ((GpuBackendType, GpuAcceleration, Canvas3DConfig, Int, Int) -> GpuRenderContext)? = null

    private val supportCache = mutableMapOf<GpuBackendType, GpuBackendSupport>()
    private val supportOverridesForTesting = mutableMapOf<GpuBackendType, GpuBackendSupport>()

    fun registerProvider(
        supportDetector: (GpuBackendType) -> GpuBackendSupport,
        canvas3DFactory: (Canvas3D, GpuAcceleration) -> Canvas3DNativeImpl<*>,
        renderContextFactory: ((GpuBackendType, GpuAcceleration, Canvas3DConfig, Int, Int) -> GpuRenderContext)? = null
    ) {
        this.supportDetector = supportDetector
        this.canvas3DFactory = canvas3DFactory
        this.renderContextFactory = renderContextFactory
        this.supportCache.clear()
    }

    fun registerCanvas3DFactory(factory: (Canvas3D) -> Canvas3DNativeImpl<*>) {
        this.canvas3DFactory = { canvas, _ -> factory(canvas) }
    }

    fun hasProvider(): Boolean =
        (supportDetector != null && canvas3DFactory != null) || supportOverridesForTesting.isNotEmpty()

    fun overrideSupportForTesting(backend: GpuBackendType, support: GpuBackendSupport) {
        supportOverridesForTesting[backend] = support
        supportCache[backend] = support
    }

    fun overrideAllSupportForTesting(
        hardwareAvailable: Boolean = true,
        softwareAvailable: Boolean = true
    ) {
        for (backend in GpuBackendType.entries) {
            overrideSupportForTesting(
                backend,
                GpuBackendSupport(
                    backend = backend,
                    hardwareAvailable = hardwareAvailable,
                    softwareAvailable = softwareAvailable
                )
            )
        }
    }

    fun resetForTesting() {
        supportDetector = null
        canvas3DFactory = null
        renderContextFactory = null
        supportCache.clear()
        supportOverridesForTesting.clear()
    }

    fun clearSupportCache() {
        supportCache.clear()
        supportCache.putAll(supportOverridesForTesting)
    }

    /**
     * Queries cached runtime hardware/software support for [backend].
     *
     * @throws IllegalStateException if no Canvas3D platform provider has been registered.
     */
    fun detectSupport(backend: GpuBackendType): GpuBackendSupport {
        supportOverridesForTesting[backend]?.let { return it }
        supportCache[backend]?.let { return it }
        val detector = supportDetector
            ?: throw IllegalStateException(
                "No Canvas3D provider is registered in Canvas3DRegistry. Register the platform Canvas3D provider before querying support or creating Canvas3D."
            )
        val detected = detector(backend)
        supportCache[backend] = detected
        return detected
    }

    /**
     * Validates [policy] against [detectSupport] for [backend] and returns the concrete [GpuAcceleration].
     *
     * @throws IllegalStateException if no provider is registered.
     * @throws UnsupportedOperationException if the requested acceleration policy is not supported.
     */
    fun resolveAcceleration(backend: GpuBackendType, policy: GpuAccelerationPolicy): GpuAcceleration {
        val support = detectSupport(backend)
        return when (policy) {
            GpuAccelerationPolicy.HARDWARE_ONLY -> {
                if (!support.hardwareAvailable) {
                    throw UnsupportedOperationException("Hardware acceleration for $backend is not available")
                }
                GpuAcceleration.HARDWARE
            }
            GpuAccelerationPolicy.SOFTWARE_ONLY -> {
                if (!support.softwareAvailable) {
                    throw UnsupportedOperationException("Software acceleration for $backend is not available")
                }
                GpuAcceleration.SOFTWARE
            }
            GpuAccelerationPolicy.HARDWARE_PREFERRED -> {
                when {
                    support.hardwareAvailable -> GpuAcceleration.HARDWARE
                    support.softwareAvailable -> GpuAcceleration.SOFTWARE
                    else -> throw UnsupportedOperationException(
                        "Neither hardware nor software acceleration for $backend is available"
                    )
                }
            }
        }
    }

    fun createRenderContext(
        backend: GpuBackendType,
        accelerationPolicy: GpuAccelerationPolicy,
        config: Canvas3DConfig = Canvas3DConfig(),
        width: Int = 800,
        height: Int = 600
    ): GpuRenderContext {
        val resolvedAccel = resolveAcceleration(backend, accelerationPolicy)
        val customFactory = renderContextFactory
        if (customFactory != null) {
            return customFactory(backend, resolvedAccel, config, width, height)
        }
        return createDefaultPipelineContext(backend, resolvedAccel, config, width, height)
    }

    fun createCanvas3DNative(canvas: Canvas3D): Canvas3DNativeImpl<*> {
        val resolvedAccel = resolveAcceleration(canvas.backend, canvas.acceleration)
        val factory = canvas3DFactory
        if (factory != null) {
            return factory(canvas, resolvedAccel)
        }
        val ctx = createDefaultPipelineContext(
            canvas.backend,
            resolvedAccel,
            canvas.config,
            canvas.initialWidth,
            canvas.initialHeight
        )
        return HeadlessCanvas3DNativeImpl(
            config = canvas.config,
            activeBackend = canvas.backend,
            activeAcceleration = resolvedAccel,
            renderContext = ctx,
            surfaceW = canvas.initialWidth,
            surfaceH = canvas.initialHeight
        )
    }

    fun createDefaultPipelineContext(
        backend: GpuBackendType,
        acceleration: GpuAcceleration,
        config: Canvas3DConfig = Canvas3DConfig(),
        width: Int = 800,
        height: Int = 600
    ): GpuRenderContext = when (backend) {
        GpuBackendType.VULKAN -> DesktopVKContextImpl(
            config = config.vulkan,
            initialWidth = width,
            initialHeight = height,
            contextInfo = VKContextInfo(
                backend = VKBackendKind.VULKAN_NATIVE,
                apiVersion = config.vulkan.apiVersion,
                deviceName = if (acceleration == GpuAcceleration.HARDWARE) "GenericUI Vulkan GPU" else "GenericUI Software Vulkan",
                deviceType = if (acceleration == GpuAcceleration.HARDWARE) VKDeviceType.DISCRETE_GPU else VKDeviceType.CPU,
                acceleration = acceleration
            )
        )
        GpuBackendType.OPENGL_ES -> DesktopGLContextImpl(
            config = config.gl,
            initialWidth = width,
            initialHeight = height,
            contextInfo = GLContextInfo(
                backend = GLBackendKind.ANGLE_D3D11,
                version = config.gl.version,
                shadingLanguage = when (config.gl.version) {
                    GLContextVersion.ES_3_0 -> GLShadingLanguage.GLSL_ES_300
                    GLContextVersion.ES_3_1 -> GLShadingLanguage.GLSL_ES_310
                    GLContextVersion.ES_3_2 -> GLShadingLanguage.GLSL_ES_320
                },
                acceleration = acceleration,
                renderer = if (acceleration == GpuAcceleration.HARDWARE) "GenericUI ANGLE/GLES3" else "GenericUI Software GLES3",
                vendor = "GenericUI"
            )
        )
    }
}
