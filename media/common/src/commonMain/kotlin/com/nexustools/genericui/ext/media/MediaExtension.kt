package com.nexustools.genericui.ext.media

import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuShaderProgram
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl

/**
 * Extension coordinator for `:extensions:media`, directly integrating `:extensions:gpu`
 * ([GpuDevice], [GpuOffscreenContext], [GpuBackendType], [GpuShaderProgram]) so [VideoPlayerExt]
 * and platform media providers execute video color-space conversion and HDR10/HLG/Dolby Vision
 * tone-mapping shaders using **Vulkan (`#version 450` SPIR-V)** as the primary pipeline and
 * **OpenGL ES (`#version 300 es` / Google ANGLE)** as the fallback pipeline.
 */
object MediaExtension {
    const val DEFAULT_VULKAN_VIDEO_SHADER = """
        #version 450
        layout(location = 0) in vec2 fragUv;
        layout(location = 0) out vec4 outColor;
        layout(set = 0, binding = 0) uniform sampler2D videoPlane0;
        layout(push_constant) uniform VideoConstants {
            float luminanceScale;
            float opacity;
        } ubo;
        void main() {
            vec4 sampleVal = texture(videoPlane0, fragUv);
            vec3 toneMapped = clamp(sampleVal.rgb * ubo.luminanceScale, 0.0, 1.0);
            outColor = vec4(toneMapped, clamp(sampleVal.a * ubo.opacity, 0.0, 1.0));
        }
    """

    const val DEFAULT_GLES_VIDEO_SHADER = """
        #version 300 es
        precision highp float;
        in vec2 fragUv;
        out vec4 outColor;
        uniform sampler2D videoPlane0;
        uniform float uLuminanceScale;
        uniform float uOpacity;
        void main() {
            vec4 sampleVal = texture(videoPlane0, fragUv);
            vec3 toneMapped = clamp(sampleVal.rgb * uLuminanceScale, 0.0, 1.0);
            outColor = vec4(toneMapped, clamp(sampleVal.a * uOpacity, 0.0, 1.0));
        }
    """

    private var videoPlayerProvider: (() -> VideoPlayerNativeImpl<*>)? = null
    private var audioPlayerProvider: (() -> AudioPlayerNativeImpl<*>)? = null
    private var audioAnalyzerProvider: ((Int) -> AudioAnalyzerNativeImpl)? = null
    private var mediaSessionManagerProvider: (() -> MediaSessionManagerNativeImpl)? = null

    fun registerProviders(
        videoPlayer: (() -> VideoPlayerNativeImpl<*>)? = null,
        audioPlayer: (() -> AudioPlayerNativeImpl<*>)? = null,
        audioAnalyzer: ((Int) -> AudioAnalyzerNativeImpl)? = null,
        mediaSessionManager: (() -> MediaSessionManagerNativeImpl)? = null
    ) {
        if (videoPlayer != null) videoPlayerProvider = videoPlayer
        if (audioPlayer != null) audioPlayerProvider = audioPlayer
        if (audioAnalyzer != null) audioAnalyzerProvider = audioAnalyzer
        if (mediaSessionManager != null) mediaSessionManagerProvider = mediaSessionManager
    }

    fun createVideoPlayerNative(): VideoPlayerNativeImpl<*>? = videoPlayerProvider?.invoke()

    fun createVideoPlayerNativeOrFallback(): VideoPlayerNativeImpl<*> =
        videoPlayerProvider?.invoke()
            ?: throw IllegalStateException("No VideoPlayerNativeImpl provider registered in MediaExtension")

    fun createAudioPlayerNative(): AudioPlayerNativeImpl<*>? = audioPlayerProvider?.invoke()

    fun createAudioPlayerNativeOrFallback(): AudioPlayerNativeImpl<*> =
        audioPlayerProvider?.invoke()
            ?: throw IllegalStateException("No AudioPlayerNativeImpl provider registered in MediaExtension")

    fun createAudioAnalyzerNative(fftSize: Int = 2048): AudioAnalyzerNativeImpl? =
        audioAnalyzerProvider?.invoke(fftSize)

    fun createMediaSessionManagerNative(): MediaSessionManagerNativeImpl? =
        mediaSessionManagerProvider?.invoke()

    fun acquireVideoGpuContext(preferVulkan: Boolean = true): GpuOffscreenContext =
        GpuDevice.acquireOffscreenContext(preferVulkan)

    val activeVideoGpuBackend: GpuBackendType
        get() = acquireVideoGpuContext().backendType

    /**
     * Compiles a video color-conversion and HDR tone-mapping [GpuShaderProgram] on `:extensions:gpu`.
     */
    fun compileVideoFrameShader(
        vulkanGlsl450: String = DEFAULT_VULKAN_VIDEO_SHADER.trimIndent(),
        gles300Shader: String = DEFAULT_GLES_VIDEO_SHADER.trimIndent(),
        gpu: GpuOffscreenContext = acquireVideoGpuContext()
    ): GpuShaderProgram = gpu.compileComputeOrFragmentKernel(vulkanGlsl450, gles300Shader)

    /**
     * Uploads [frameBuffer] to a `:extensions:gpu` texture, dispatches [program] with [uniforms],
     * and downloads the processed RGBA video frame.
     */
    fun renderVideoFrameOnGpu(
        frameBuffer: ImageBuffer,
        program: GpuShaderProgram,
        uniforms: FloatArray = floatArrayOf(1.0f, 1.0f),
        gpu: GpuOffscreenContext = acquireVideoGpuContext()
    ): ImageBuffer {
        val rgba = frameBuffer.toRgba8888()
        val srcTex = gpu.uploadTexture(rgba)
        val dstTex = gpu.createEmptyTexture(rgba.width, rgba.height, PixelFormat.RGBA_8888)
        gpu.dispatchImageKernel(program, listOf(srcTex), dstTex, uniforms)
        val output = gpu.downloadTexture(dstTex)
        gpu.destroyTexture(srcTex)
        gpu.destroyTexture(dstTex)
        return output
    }
}
