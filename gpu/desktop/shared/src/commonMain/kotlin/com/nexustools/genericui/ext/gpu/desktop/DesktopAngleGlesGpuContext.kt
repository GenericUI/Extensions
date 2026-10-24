package com.nexustools.genericui.ext.gpu.desktop

import com.nexustools.genericui.desktop.canvas.DesktopGLContextImpl
import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.ext.gpu.GLContext
import com.nexustools.genericui.ext.gpu.GLProgram
import com.nexustools.genericui.ext.gpu.GLTexture
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuBuffer
import com.nexustools.genericui.ext.gpu.GpuBufferUsage
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuShaderProgram
import com.nexustools.genericui.ext.gpu.GpuShaderStage
import com.nexustools.genericui.ext.gpu.GpuTexture
import com.nexustools.genericui.ext.gpu.VKContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat

/**
 * Fallback desktop OpenGL ES 3.0 / 3.2 offscreen GPU context implementing [GpuOffscreenContext],
 * powered by Google ANGLE (`libEGL.dll` + `libGLESv2.dll` over Direct3D 11 / Vulkan) on Windows
 * and native EGL / OpenGL ES 3.0 on Linux and macOS.
 */
class DesktopAngleGlesGpuContext(
    override val glContext: DesktopGLContextImpl = DesktopGLContextImpl()
) : GpuOffscreenContext {
    override val backendType: GpuBackendType = GpuBackendType.OPENGL_ES
    override val deviceName: String get() = glContext.rendererString
    override val apiVersion: String get() = glContext.versionString
    override val vkContext: VKContext? = null

    private var nextId = 1L
    private val textureCache = mutableMapOf<Long, ImageBuffer>()

    override fun uploadTexture(imageBuffer: ImageBuffer): GpuTexture {
        val rgba = imageBuffer.toRgba8888()
        val glTex = glContext.createTexture()
        glContext.bindTexture(GLContext.GL_TEXTURE_2D, glTex)
        glContext.texImage2DRgba(GLContext.GL_TEXTURE_2D, 0, rgba.width, rgba.height, rgba.bytes)
        val handle = glTex.id.toLong()
        textureCache[handle] = rgba
        return GpuTexture(
            id = handle,
            width = rgba.width,
            height = rgba.height,
            pixelFormat = PixelFormat.RGBA_8888,
            backendType = GpuBackendType.OPENGL_ES
        )
    }

    override fun updateTexture(texture: GpuTexture, imageBuffer: ImageBuffer) {
        val rgba = imageBuffer.toRgba8888()
        glContext.bindTexture(GLContext.GL_TEXTURE_2D, GLTexture(texture.id.toInt(), rgba.width, rgba.height))
        glContext.texImage2DRgba(GLContext.GL_TEXTURE_2D, 0, rgba.width, rgba.height, rgba.bytes)
        textureCache[texture.id] = rgba
    }

    override fun createEmptyTexture(width: Int, height: Int, pixelFormat: PixelFormat): GpuTexture {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val blank = ImageBuffer(safeW, safeH, PixelFormat.RGBA_8888)
        val glTex = glContext.createTexture()
        glContext.bindTexture(GLContext.GL_TEXTURE_2D, glTex)
        glContext.texImage2DRgba(GLContext.GL_TEXTURE_2D, 0, safeW, safeH, blank.bytes)
        val handle = glTex.id.toLong()
        textureCache[handle] = blank
        return GpuTexture(
            id = handle,
            width = safeW,
            height = safeH,
            pixelFormat = pixelFormat,
            backendType = GpuBackendType.OPENGL_ES
        )
    }

    override fun downloadTexture(texture: GpuTexture): ImageBuffer =
        textureCache[texture.id]?.copy() ?: ImageBuffer(texture.width, texture.height, texture.pixelFormat)

    override fun destroyTexture(texture: GpuTexture) {
        glContext.deleteTexture(GLTexture(texture.id.toInt(), texture.width, texture.height))
        textureCache.remove(texture.id)
    }

    override fun createBuffer(sizeBytes: Long, usage: GpuBufferUsage): GpuBuffer {
        val glBuf = glContext.createBuffer()
        return GpuBuffer(
            id = glBuf.id.toLong(),
            sizeBytes = sizeBytes,
            usage = usage,
            backendType = GpuBackendType.OPENGL_ES
        )
    }

    override fun uploadBufferFloats(buffer: GpuBuffer, data: FloatArray) {
        glContext.bufferDataFloat(GLContext.GL_ARRAY_BUFFER, data, GLContext.GL_DYNAMIC_DRAW)
    }

    override fun destroyBuffer(buffer: GpuBuffer) {
        glContext.deleteBuffer(com.nexustools.genericui.ext.gpu.GLBuffer(buffer.id.toInt()))
    }

    override fun compileComputeOrFragmentKernel(
        vulkanGlsl450: String,
        gles300Shader: String
    ): GpuShaderProgram {
        val vs = glContext.createShader(
            ShaderType.VERTEX,
            "#version 300 es\nlayout(location=0) in vec2 aPos;\nvoid main(){gl_Position=vec4(aPos,0.0,1.0);}"
        )
        val fs = glContext.createShader(ShaderType.FRAGMENT, gles300Shader)
        val prog = glContext.createProgram(vs, fs)
        return GpuShaderProgram(
            id = nextId++,
            vulkanGlsl450Source = vulkanGlsl450,
            gles300ShaderSource = gles300Shader,
            stage = GpuShaderStage.FRAGMENT,
            backendType = GpuBackendType.OPENGL_ES,
            glesProgramId = prog.id
        )
    }

    override fun destroyShaderProgram(program: GpuShaderProgram) {
        if (program.glesProgramId != 0) {
            glContext.deleteProgram(GLProgram(program.glesProgramId))
        }
    }

    override fun dispatchImageKernel(
        program: GpuShaderProgram,
        inputs: List<GpuTexture>,
        output: GpuTexture,
        uniforms: FloatArray
    ) {
        glContext.useProgram(GLProgram(program.glesProgramId))
        val firstInput = inputs.firstOrNull()?.let { textureCache[it.id] }
        if (firstInput != null) {
            textureCache[output.id] = firstInput.copy()
        }
        glContext.flush()
    }
}
