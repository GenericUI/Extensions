package com.nexustools.genericui.ext.canvas3d.alien

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.GLBackendKind
import com.nexustools.genericui.component.media.GLContextInfo
import com.nexustools.genericui.component.media.GLContextVersion
import com.nexustools.genericui.component.media.GLShadingLanguage
import com.nexustools.genericui.ext.alien.AlienExtension
import com.nexustools.genericui.ext.alien.AlienExtensionImplProvider
import com.nexustools.genericui.ext.alien.impl.ContainerAlienImpl
import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import com.nexustools.genericui.ext.canvas3d.ScrollCanvas3D
import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType

/**
 * Registers `:extensions:graphics:canvas3d` with `:extensions:ui:alien` so [Canvas3D] and
 * [ScrollCanvas3D] instances realized inside an Alien session resolve to self-rendered
 * [Canvas3DAlienImpl] / Alien container widgets with zero OS underlay dependencies.
 */
object Canvas3DAlienExtension {

    private var installedFallbackRegistryProvider: Boolean = false

    private val extensionProvider = AlienExtensionImplProvider { component, session, _ ->
        when (component) {
            is Canvas3D -> Canvas3DAlienImpl(
                component = component,
                activeAcceleration = component.resolvedAcceleration,
                session = session
            )
            is ScrollCanvas3D -> ContainerAlienImpl(
                componentClassPrefix = component.componentClassPrefix,
                tagName = component.htmlTag,
                session = session
            )
            else -> null
        }
    }

    /**
     * Idempotently registers the Canvas3D Alien extension provider with [AlienExtension], and
     * installs an offscreen software fallback provider in [Canvas3DRegistry] if no platform
     * Canvas3D provider has been registered yet.
     */
    fun register() {
        AlienExtension.registerAlienExtensionImpl(extensionProvider)
        if (!Canvas3DRegistry.hasProvider()) {
            installedFallbackRegistryProvider = true
            Canvas3DRegistry.registerProvider(
                supportDetector = { backend ->
                    GpuBackendSupport(
                        backend = backend,
                        hardwareAvailable = false,
                        softwareAvailable = true
                    )
                },
                canvas3DFactory = { canvas, accel ->
                    Canvas3DAlienImpl(
                        component = canvas,
                        activeAcceleration = accel,
                        session = null
                    )
                },
                renderContextFactory = { backend, accel, cfg, width, height ->
                    when (backend) {
                        GpuBackendType.OPENGL_ES -> AlienOffscreenGlContext(
                            config = cfg.gl,
                            initialWidth = width,
                            initialHeight = height,
                            contextInfo = GLContextInfo(
                                backend = GLBackendKind.WEBGL2,
                                version = cfg.gl.version,
                                shadingLanguage = when (cfg.gl.version) {
                                    GLContextVersion.ES_3_0 -> GLShadingLanguage.GLSL_ES_300
                                    GLContextVersion.ES_3_1 -> GLShadingLanguage.GLSL_ES_310
                                    GLContextVersion.ES_3_2 -> GLShadingLanguage.GLSL_ES_320
                                },
                                acceleration = accel,
                                renderer = "Alien Offscreen Software WebGL2/GLES3",
                                vendor = "GenericUI Alien"
                            )
                        )
                        GpuBackendType.VULKAN -> Canvas3DRegistry.createDefaultPipelineContext(
                            backend = backend,
                            acceleration = accel,
                            config = cfg,
                            width = width,
                            height = height
                        )
                    }
                }
            )
        }
    }

    /**
     * Unregisters the Canvas3D Alien extension provider and removes any fallback registry provider
     * that [register] installed.
     */
    fun unregister() {
        AlienExtension.unregisterAlienExtensionImpl(extensionProvider)
        if (installedFallbackRegistryProvider) {
            installedFallbackRegistryProvider = false
            Canvas3DRegistry.resetForTesting()
        }
    }
}
