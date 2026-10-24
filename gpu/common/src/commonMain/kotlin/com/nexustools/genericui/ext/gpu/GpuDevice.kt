package com.nexustools.genericui.ext.gpu

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat

/**
 * Singleton registry and factory for acquiring the active [GpuOffscreenContext].
 *
 * Always prioritizes **Vulkan (`[GpuBackendType.VULKAN]`)** as the primary GPU backend,
 * falling back to **OpenGL ES / Google ANGLE (`[GpuBackendType.OPENGL_ES]`)** when Vulkan
 * is unavailable, and finally to a deterministic in-memory software fallback context for headless tests.
 */
object GpuDevice {
    private var vulkanProvider: (() -> GpuOffscreenContext?)? = null
    private var openGlEsProvider: (() -> GpuOffscreenContext?)? = null
    private var cachedVulkanContext: GpuOffscreenContext? = null
    private var cachedOpenGlEsContext: GpuOffscreenContext? = null
    private val softwareFallbackContext: GpuOffscreenContext by lazy { createSoftwareFallbackContext() }

    /**
     * Registers a platform GPU context provider for [backendType].
     */
    fun registerProvider(backendType: GpuBackendType, provider: () -> GpuOffscreenContext?) {
        when (backendType) {
            GpuBackendType.VULKAN -> {
                vulkanProvider = provider
                cachedVulkanContext = null
            }
            GpuBackendType.OPENGL_ES -> {
                openGlEsProvider = provider
                cachedOpenGlEsContext = null
            }
            GpuBackendType.SOFTWARE_FALLBACK -> Unit
        }
    }

    /**
     * Acquires the active [GpuOffscreenContext], preferring Vulkan as primary and OpenGL ES / ANGLE as fallback.
     */
    fun acquireOffscreenContext(preferVulkan: Boolean = true): GpuOffscreenContext {
        if (preferVulkan) {
            cachedVulkanContext?.let { return it }
            vulkanProvider?.invoke()?.let { ctx ->
                cachedVulkanContext = ctx
                return ctx
            }
        }
        cachedOpenGlEsContext?.let { return it }
        openGlEsProvider?.invoke()?.let { ctx ->
            cachedOpenGlEsContext = ctx
            return ctx
        }
        if (!preferVulkan) {
            cachedVulkanContext?.let { return it }
            vulkanProvider?.invoke()?.let { ctx ->
                cachedVulkanContext = ctx
                return ctx
            }
        }
        return softwareFallbackContext
    }

    val activeBackendType: GpuBackendType
        get() = acquireOffscreenContext().backendType

    fun resetProviders() {
        vulkanProvider = null
        openGlEsProvider = null
        cachedVulkanContext = null
        cachedOpenGlEsContext = null
    }

    private fun createSoftwareFallbackContext(): GpuOffscreenContext = object : GpuOffscreenContext {
        override val backendType: GpuBackendType = GpuBackendType.SOFTWARE_FALLBACK
        override val deviceName: String = "GenericUI Headless Software GPU Fallback"
        override val apiVersion: String = "Vulkan 1.3 / GLES 3.0 Emulated"
        override val vkContext: VKContext? = null
        override val glContext: GLContext? = null

        private var nextHandle = 1L
        private val textures = mutableMapOf<Long, ImageBuffer>()
        private val buffers = mutableMapOf<Long, FloatArray>()

        override fun uploadTexture(imageBuffer: ImageBuffer): GpuTexture {
            val id = nextHandle++
            textures[id] = imageBuffer.toRgba8888()
            return GpuTexture(
                id = id,
                width = imageBuffer.width,
                height = imageBuffer.height,
                pixelFormat = PixelFormat.RGBA_8888,
                backendType = backendType
            )
        }

        override fun updateTexture(texture: GpuTexture, imageBuffer: ImageBuffer) {
            textures[texture.id] = imageBuffer.toRgba8888()
        }

        override fun createEmptyTexture(width: Int, height: Int, pixelFormat: PixelFormat): GpuTexture {
            val id = nextHandle++
            textures[id] = ImageBuffer(width.coerceAtLeast(1), height.coerceAtLeast(1), PixelFormat.RGBA_8888)
            return GpuTexture(
                id = id,
                width = width.coerceAtLeast(1),
                height = height.coerceAtLeast(1),
                pixelFormat = pixelFormat,
                backendType = backendType
            )
        }

        override fun downloadTexture(texture: GpuTexture): ImageBuffer =
            textures[texture.id]?.copy() ?: ImageBuffer(texture.width, texture.height, texture.pixelFormat)

        override fun destroyTexture(texture: GpuTexture) {
            textures.remove(texture.id)
        }

        override fun createBuffer(sizeBytes: Long, usage: GpuBufferUsage): GpuBuffer {
            val id = nextHandle++
            buffers[id] = FloatArray((sizeBytes / 4L).toInt().coerceAtLeast(1))
            return GpuBuffer(id, sizeBytes, usage, backendType)
        }

        override fun uploadBufferFloats(buffer: GpuBuffer, data: FloatArray) {
            buffers[buffer.id] = data.copyOf()
        }

        override fun destroyBuffer(buffer: GpuBuffer) {
            buffers.remove(buffer.id)
        }

        override fun compileComputeOrFragmentKernel(
            vulkanGlsl450: String,
            gles300Shader: String
        ): GpuShaderProgram = GpuShaderProgram(
            id = nextHandle++,
            vulkanGlsl450Source = vulkanGlsl450,
            gles300ShaderSource = gles300Shader,
            stage = GpuShaderStage.FRAGMENT,
            backendType = backendType
        )

        override fun destroyShaderProgram(program: GpuShaderProgram) {}

        override fun dispatchImageKernel(
            program: GpuShaderProgram,
            inputs: List<GpuTexture>,
            output: GpuTexture,
            uniforms: FloatArray
        ) {
            val firstInput = inputs.firstOrNull()?.let { textures[it.id] }
            if (firstInput != null) {
                textures[output.id] = firstInput.copy()
            }
        }
    }
}
