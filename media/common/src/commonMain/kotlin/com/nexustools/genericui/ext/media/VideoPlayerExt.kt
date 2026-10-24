package com.nexustools.genericui.ext.media

import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuShaderProgram
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl

/**
 * Extended hardware-accelerated video player in `:extensions:media` extending [VideoPlayer] and
 * directly integrating `:extensions:gpu` ([GpuOffscreenContext], [GpuShaderProgram], [GpuBackendType])
 * for Vulkan (`#version 450` SPIR-V primary) and OpenGL ES (`#version 300 es` fallback) video frame
 * color conversion and HDR tone-mapping.
 */
open class VideoPlayerExt(
    sourceUrl: String? = null,
    val preferVulkanGpuPipeline: Boolean = true
) : VideoPlayer(sourceUrl) {

    val gpuContext: GpuOffscreenContext
        get() = MediaExtension.acquireVideoGpuContext(preferVulkanGpuPipeline)

    val activeGpuBackend: GpuBackendType
        get() = gpuContext.backendType

    var activeVulkanFragmentShader: String = MediaExtension.DEFAULT_VULKAN_VIDEO_SHADER.trimIndent()
        private set

    var activeGlesFragmentShader: String = MediaExtension.DEFAULT_GLES_VIDEO_SHADER.trimIndent()
        private set

    val videoShaderProgram: GpuShaderProgram
        get() = MediaExtension.compileVideoFrameShader(
            vulkanGlsl450 = activeVulkanFragmentShader,
            gles300Shader = activeGlesFragmentShader,
            gpu = gpuContext
        )

    fun updateVideoFrameShaders(vulkanGlsl450: String, gles300Shader: String) {
        activeVulkanFragmentShader = vulkanGlsl450
        activeGlesFragmentShader = gles300Shader
    }

    /**
     * Executes the active Vulkan / OpenGL ES video tone-mapping shader on [frame] via `:extensions:gpu`.
     */
    fun processVideoFrameOnGpu(
        frame: ImageBuffer,
        luminanceScale: Float = 1.0f,
        opacity: Float = 1.0f
    ): ImageBuffer {
        val ctx = gpuContext
        val program = MediaExtension.compileVideoFrameShader(
            vulkanGlsl450 = activeVulkanFragmentShader,
            gles300Shader = activeGlesFragmentShader,
            gpu = ctx
        )
        val processed = MediaExtension.renderVideoFrameOnGpu(
            frameBuffer = frame,
            program = program,
            uniforms = floatArrayOf(luminanceScale, opacity),
            gpu = ctx
        )
        ctx.destroyShaderProgram(program)
        return processed
    }

    override fun createNativeImpl(factory: NativeFactory): VideoPlayerNativeImpl<*> =
        MediaExtension.createVideoPlayerNative() ?: super.createNativeImpl(factory)
}
