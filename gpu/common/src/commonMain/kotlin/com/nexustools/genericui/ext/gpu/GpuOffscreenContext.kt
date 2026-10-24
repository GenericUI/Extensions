package com.nexustools.genericui.ext.gpu

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat

/**
 * Cross-platform headless/offscreen GPU rendering and compute context over [GpuTexture]s and [GpuBuffer]s.
 *
 * Used by `:extensions:imageprocessing` for offscreen GPU image filtering & compositing,
 * `:extensions:media` for VLC video frame color-conversion & HDR tone-mapping shaders,
 * and `:extensions:canvas3d` for offscreen and swapchain GPU resource management.
 */
interface GpuOffscreenContext {
    val backendType: GpuBackendType
    val deviceName: String
    val apiVersion: String
    val vkContext: VKContext?
    val glContext: GLContext?

    fun uploadTexture(imageBuffer: ImageBuffer): GpuTexture

    fun updateTexture(texture: GpuTexture, imageBuffer: ImageBuffer)

    fun createEmptyTexture(
        width: Int,
        height: Int,
        pixelFormat: PixelFormat = PixelFormat.RGBA_8888
    ): GpuTexture

    fun downloadTexture(texture: GpuTexture): ImageBuffer

    fun destroyTexture(texture: GpuTexture)

    fun createBuffer(sizeBytes: Long, usage: GpuBufferUsage): GpuBuffer

    fun uploadBufferFloats(buffer: GpuBuffer, data: FloatArray)

    fun destroyBuffer(buffer: GpuBuffer)

    fun compileComputeOrFragmentKernel(
        vulkanGlsl450: String,
        gles300Shader: String
    ): GpuShaderProgram

    fun destroyShaderProgram(program: GpuShaderProgram)

    fun dispatchImageKernel(
        program: GpuShaderProgram,
        inputs: List<GpuTexture>,
        output: GpuTexture,
        uniforms: FloatArray
    )
}
