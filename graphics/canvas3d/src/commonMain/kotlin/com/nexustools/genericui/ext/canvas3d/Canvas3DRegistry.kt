package com.nexustools.genericui.ext.canvas3d

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLBackendKind
import com.nexustools.genericui.component.media.GLContextInfo
import com.nexustools.genericui.component.media.GLContextVersion
import com.nexustools.genericui.component.media.GLShadingLanguage
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.component.media.VKBackendKind
import com.nexustools.genericui.component.media.VKContextInfo
import com.nexustools.genericui.component.media.VKDeviceType
import com.nexustools.genericui.desktop.canvas.DesktopGLContextImpl
import com.nexustools.genericui.desktop.canvas.DesktopVKContextImpl
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl

/**
 * Registry managing platform support detection, acceleration policy validation, and native
 * surface/context creation for [Canvas3D].
 */
object Canvas3DRegistry {

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
            ?: throw IllegalStateException(
                "No Canvas3D native factory is registered in Canvas3DRegistry. Register the platform Canvas3D provider (DesktopCanvas3DProvider, SwingCanvas3DProvider, MobileCanvas3DProvider, or WebCanvas3DProvider) before realizing Canvas3D."
            )
        return factory(canvas, resolvedAccel)
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
