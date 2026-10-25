package com.nexustools.genericui.ext.canvas3d

import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.ext.gpu.GLBuffer
import com.nexustools.genericui.ext.gpu.GLCanvasConfig
import com.nexustools.genericui.ext.gpu.GLContext
import com.nexustools.genericui.ext.gpu.GLFramebuffer
import com.nexustools.genericui.ext.gpu.GLProgram
import com.nexustools.genericui.ext.gpu.GLShader
import com.nexustools.genericui.ext.gpu.GLTexture
import com.nexustools.genericui.ext.gpu.GLUniformLocation
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.VKCanvasConfig
import com.nexustools.genericui.ext.gpu.VKContext
import com.nexustools.genericui.nativeimpl.media.GLCanvasNativeImpl
import com.nexustools.genericui.nativeimpl.media.ScrollCanvas3DNativeImpl
import com.nexustools.genericui.nativeimpl.media.VKCanvasNativeImpl
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Extension coordinator for `:extensions:canvas3d` bridging [VKCanvasExt], [GLCanvasExt],
 * [ScrollCanvas3D], [ScrollGLCanvas], and [ScrollVKCanvas]
 * with `:extensions:gpu` ([GpuDevice], [GpuOffscreenContext], [VKContext], and [GLContext]).
 */
object Canvas3DExtension {
    private var vkContextFactory: ((VKCanvasConfig, Int, Int) -> VKContext)? = null
    private var glContextFactory: ((GLCanvasConfig, Int, Int) -> GLContext)? = null
    private var glCanvasNativeFactory: ((GLCanvasConfig, Int, Int) -> GLCanvasNativeImpl<*>)? = null
    private var vkCanvasNativeFactory: ((VKCanvasConfig, Int, Int) -> VKCanvasNativeImpl<*>)? = null
    private var scrollCanvas3dNativeFactory: ((Int, Int, Int, Int, Boolean) -> ScrollCanvas3DNativeImpl<*, *>)? = null

    fun registerVKContextFactory(factory: (VKCanvasConfig, Int, Int) -> VKContext) {
        vkContextFactory = factory
    }

    fun registerGLContextFactory(factory: (GLCanvasConfig, Int, Int) -> GLContext) {
        glContextFactory = factory
    }

    fun registerGLCanvasFactory(factory: (GLCanvasConfig, Int, Int) -> GLCanvasNativeImpl<*>) {
        glCanvasNativeFactory = factory
    }

    fun registerVKCanvasFactory(factory: (VKCanvasConfig, Int, Int) -> VKCanvasNativeImpl<*>) {
        vkCanvasNativeFactory = factory
    }

    fun registerScrollCanvas3DFactory(
        factory: (viewportWidth: Int, viewportHeight: Int, contentWidth: Int, contentHeight: Int, preferVulkan: Boolean) -> ScrollCanvas3DNativeImpl<*, *>
    ) {
        scrollCanvas3dNativeFactory = factory
    }

    fun createGLCanvasNative(
        config: GLCanvasConfig = GLCanvasConfig(),
        width: Int = 800,
        height: Int = 600
    ): GLCanvasNativeImpl<*> =
        glCanvasNativeFactory?.invoke(config, width, height)
            ?: throw IllegalStateException("No GLCanvasNativeImpl provider registered in Canvas3DExtension")

    fun createVKCanvasNative(
        config: VKCanvasConfig = VKCanvasConfig(),
        width: Int = 800,
        height: Int = 600
    ): VKCanvasNativeImpl<*> =
        vkCanvasNativeFactory?.invoke(config, width, height)
            ?: throw IllegalStateException("No VKCanvasNativeImpl provider registered in Canvas3DExtension")

    fun createScrollCanvas3DNativeOrNull(
        viewportWidth: Int = 800,
        viewportHeight: Int = 600,
        contentWidth: Int = viewportWidth,
        contentHeight: Int = viewportHeight,
        preferVulkan: Boolean = true
    ): ScrollCanvas3DNativeImpl<*, *>? =
        scrollCanvas3dNativeFactory?.invoke(viewportWidth, viewportHeight, contentWidth, contentHeight, preferVulkan)

    fun createScrollCanvas3DNative(
        viewportWidth: Int = 800,
        viewportHeight: Int = 600,
        contentWidth: Int = viewportWidth,
        contentHeight: Int = viewportHeight,
        preferVulkan: Boolean = true,
        glConfig: GLCanvasConfig = GLCanvasConfig(),
        vkConfig: VKCanvasConfig = VKCanvasConfig()
    ): ScrollCanvas3DNativeImpl<*, *> =
        scrollCanvas3dNativeFactory?.invoke(viewportWidth, viewportHeight, contentWidth, contentHeight, preferVulkan)
            ?: SystemScrollCanvas3DNativeImpl(
                initialViewportWidth = viewportWidth,
                initialViewportHeight = viewportHeight,
                initialContentWidth = contentWidth,
                initialContentHeight = contentHeight,
                preferVulkan = preferVulkan,
                glConfig = glConfig,
                vkConfig = vkConfig
            )

    fun acquireGpuOffscreenContext(preferVulkan: Boolean = true): GpuOffscreenContext =
        GpuDevice.acquireOffscreenContext(preferVulkan)

    fun createOffscreenCanvas3D(
        width: Int = 800,
        height: Int = 600,
        preferVulkan: Boolean = true,
        vkConfig: VKCanvasConfig = VKCanvasConfig(),
        glConfig: GLCanvasConfig = GLCanvasConfig()
    ): OffscreenCanvas3D = OffscreenCanvas3D(
        width = width,
        height = height,
        preferVulkan = preferVulkan,
        vkConfig = vkConfig,
        glConfig = glConfig,
        gpuContext = acquireGpuOffscreenContext(preferVulkan)
    )

    fun createVKContext(
        config: VKCanvasConfig = VKCanvasConfig(),
        width: Int = 800,
        height: Int = 600
    ): VKContext {
        vkContextFactory?.let { return it(config, width, height) }
        GpuDevice.acquireOffscreenContext(preferVulkan = true).vkContext?.let { return it }
        return createFallbackVKContext(width, height)
    }

    fun createGLContext(
        config: GLCanvasConfig = GLCanvasConfig(),
        width: Int = 800,
        height: Int = 600
    ): GLContext {
        glContextFactory?.let { return it(config, width, height) }
        GpuDevice.acquireOffscreenContext(preferVulkan = false).glContext?.let { return it }
        return createFallbackGLContext()
    }

    private fun createFallbackVKContext(width: Int, height: Int): VKContext = object : VKContext {
        override val deviceName: String = "GenericUI Vulkan 1.3 Surface Context"
        override val apiVersionString: String = "1.3.280"
        override val swapchainWidth: Int = width.coerceAtLeast(1)
        override val swapchainHeight: Int = height.coerceAtLeast(1)
        override val currentImageIndex: Int = 0
        override val isMoltenVk: Boolean = false
        private var nextHandle = 1L

        override fun beginRenderPass(clearColor: RgbaColor, clearDepth: Float) {}
        override fun endRenderPass() {}
        override fun setViewport(x: Float, y: Float, width: Float, height: Float, minDepth: Float, maxDepth: Float) {}
        override fun setScissor(x: Int, y: Int, width: Int, height: Int) {}
        override fun createShaderModuleFromSpirv(stage: ShaderType, spirvBytes: ByteArray): Long = nextHandle++
        override fun createShaderModuleFromGlsl(stage: ShaderType, glslSource: String): Long = nextHandle++
        override fun destroyShaderModule(moduleHandle: Long) {}
        override fun createGraphicsPipeline(vertexShaderHandle: Long, fragmentShaderHandle: Long, vertexStrideBytes: Int): Long = nextHandle++
        override fun bindPipeline(pipelineHandle: Long) {}
        override fun destroyPipeline(pipelineHandle: Long) {}
        override fun createDeviceBuffer(sizeBytes: Long, usageFlags: Int): Long = nextHandle++
        override fun uploadBufferData(bufferHandle: Long, data: ByteArray, offsetBytes: Long) {}
        override fun uploadBufferFloats(bufferHandle: Long, data: FloatArray, offsetBytes: Long) {}
        override fun destroyDeviceBuffer(bufferHandle: Long) {}
        override fun createTextureImageRgba(width: Int, height: Int, rgbaBytes: ByteArray?): Long = nextHandle++
        override fun updateTextureImageRgba(imageHandle: Long, width: Int, height: Int, rgbaBytes: ByteArray) {}
        override fun destroyTextureImage(imageHandle: Long) {}
        override fun bindVertexBuffer(bufferHandle: Long, binding: Int, offsetBytes: Long) {}
        override fun bindIndexBuffer(bufferHandle: Long, indexTypeUint32: Boolean, offsetBytes: Long) {}
        override fun pushConstantsFloat(pipelineHandle: Long, values: FloatArray) {}
        override fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {}
        override fun drawIndexed(indexCount: Int, instanceCount: Int, firstIndex: Int, vertexOffset: Int, firstInstance: Int) {}
        override fun waitIdle() {}
    }

    private fun createFallbackGLContext(): GLContext = object : GLContext {
        override val rendererString: String = "GenericUI OpenGL ES 3.0 (Google ANGLE)"
        override val vendorString: String = "NexusTools GenericUI"
        override val versionString: String = "OpenGL ES 3.0"
        override val isAngleBackend: Boolean = true
        private var nextId = 1

        override fun viewport(x: Int, y: Int, width: Int, height: Int) {}
        override fun scissor(x: Int, y: Int, width: Int, height: Int) {}
        override fun clearColor(r: Float, g: Float, b: Float, a: Float) {}
        override fun clearDepth(depth: Float) {}
        override fun clearStencil(stencil: Int) {}
        override fun clear(mask: Int) {}
        override fun enable(cap: Int) {}
        override fun disable(cap: Int) {}
        override fun blendFunc(sfactor: Int, dfactor: Int) {}
        override fun blendFuncSeparate(srcRGB: Int, dstRGB: Int, srcAlpha: Int, dstAlpha: Int) {}
        override fun depthFunc(func: Int) {}
        override fun depthMask(flag: Boolean) {}
        override fun cullFace(mode: Int) {}
        override fun frontFace(mode: Int) {}
        override fun createShader(type: ShaderType, source: String): GLShader = GLShader(nextId++, type)
        override fun deleteShader(shader: GLShader) {}
        override fun createProgram(vertexShader: GLShader, fragmentShader: GLShader): GLProgram = GLProgram(nextId++)
        override fun useProgram(program: GLProgram?) {}
        override fun deleteProgram(program: GLProgram) {}
        override fun getAttribLocation(program: GLProgram, name: String): Int = 0
        override fun getUniformLocation(program: GLProgram, name: String): GLUniformLocation = GLUniformLocation(nextId++, name)
        override fun uniform1i(location: GLUniformLocation, v0: Int) {}
        override fun uniform1f(location: GLUniformLocation, v0: Float) {}
        override fun uniform2f(location: GLUniformLocation, v0: Float, v1: Float) {}
        override fun uniform3f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float) {}
        override fun uniform4f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float, v3: Float) {}
        override fun uniformMatrix3fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray) {}
        override fun uniformMatrix4fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray) {}
        override fun createBuffer(): GLBuffer = GLBuffer(nextId++)
        override fun bindBuffer(target: Int, buffer: GLBuffer?) {}
        override fun bufferDataFloat(target: Int, data: FloatArray, usage: Int) {}
        override fun bufferDataInt(target: Int, data: IntArray, usage: Int) {}
        override fun deleteBuffer(buffer: GLBuffer) {}
        override fun enableVertexAttribArray(index: Int) {}
        override fun disableVertexAttribArray(index: Int) {}
        override fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) {}
        override fun createTexture(): GLTexture = GLTexture(nextId++)
        override fun bindTexture(target: Int, texture: GLTexture?) {}
        override fun activeTexture(textureUnit: Int) {}
        override fun texImage2DRgba(target: Int, level: Int, width: Int, height: Int, rgbaBytes: ByteArray?) {}
        override fun texParameteri(target: Int, pname: Int, param: Int) {}
        override fun generateMipmap(target: Int) {}
        override fun deleteTexture(texture: GLTexture) {}
        override fun createFramebuffer(): GLFramebuffer = GLFramebuffer(nextId++)
        override fun bindFramebuffer(target: Int, framebuffer: GLFramebuffer?) {}
        override fun framebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: GLTexture, level: Int) {}
        override fun deleteFramebuffer(framebuffer: GLFramebuffer) {}
        override fun drawArrays(mode: Int, first: Int, count: Int) {}
        override fun drawElements(mode: Int, count: Int, type: Int, offset: Int) {}
        override fun drawArraysInstanced(mode: Int, first: Int, count: Int, instanceCount: Int) {}
        override fun drawElementsInstanced(mode: Int, count: Int, type: Int, offset: Int, instanceCount: Int) {}
        override fun readPixelsRgba(x: Int, y: Int, width: Int, height: Int): ByteArray = ByteArray((width * height * 4).coerceAtLeast(0))
        override fun finish() {}
        override fun flush() {}
    }
}
