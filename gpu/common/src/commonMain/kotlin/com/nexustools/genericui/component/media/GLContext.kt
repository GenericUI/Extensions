package com.nexustools.genericui.component.media

import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType

/**
 * Typed WebGL2 / OpenGL ES 3.0+ rendering context interface.
 */
interface GLContext : GpuRenderContext {
    override val backendType: GpuBackendType
        get() = GpuBackendType.OPENGL_ES
    val contextInfo: GLContextInfo
    override val acceleration: GpuAcceleration
        get() = contextInfo.acceleration

    fun viewport(x: Int, y: Int, width: Int, height: Int)
    fun scissor(x: Int, y: Int, width: Int, height: Int)
    fun clearColor(r: Float, g: Float, b: Float, a: Float)
    fun clearDepth(depth: Float)
    fun clearStencil(stencil: Int)
    fun clear(buffers: Set<GLClearBuffer>)

    fun enable(capability: GLCapability)
    fun disable(capability: GLCapability)
    fun blendFunc(sfactor: GLBlendFactor, dfactor: GLBlendFactor)
    fun blendFuncSeparate(srcRGB: GLBlendFactor, dstRGB: GLBlendFactor, srcAlpha: GLBlendFactor, dstAlpha: GLBlendFactor)
    fun blendEquation(mode: GLBlendEquation = GLBlendEquation.FUNC_ADD)
    fun depthFunc(func: GLCompareFunction)
    fun depthMask(flag: Boolean)
    fun cullFace(mode: GLFaceSide)
    fun frontFace(mode: GLWinding)

    fun createShader(type: ShaderType, source: String): GLShader
    fun createShader(source: GpuShaderStageSource): GLShader =
        createShader(source.stage, source.glslEs300Source.ifEmpty { source.glsl450Source })
    fun deleteShader(shader: GLShader)
    fun createProgram(vertexShader: GLShader, fragmentShader: GLShader): GLProgram
    fun useProgram(program: GLProgram?)
    fun deleteProgram(program: GLProgram)

    fun getAttribLocation(program: GLProgram, name: String): Int
    fun getUniformLocation(program: GLProgram, name: String): GLUniformLocation?
    fun getUniformBlockIndex(program: GLProgram, blockName: String): Int = 0
    fun uniformBlockBinding(program: GLProgram, uniformBlockIndex: Int, uniformBlockBinding: Int) {}
    fun uniform1i(location: GLUniformLocation, v0: Int)
    fun uniform1f(location: GLUniformLocation, v0: Float)
    fun uniform2f(location: GLUniformLocation, v0: Float, v1: Float)
    fun uniform3f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float)
    fun uniform4f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float, v3: Float)
    fun uniformMatrix3fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray)
    fun uniformMatrix4fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray)

    fun createBuffer(): GLBuffer
    fun bindBuffer(target: GLBufferTarget, buffer: GLBuffer?)
    fun bindBufferBase(target: GLBufferTarget, index: Int, buffer: GLBuffer?) {}
    fun bufferData(target: GLBufferTarget, data: FloatArray, usage: GLBufferUsage)
    fun bufferData(target: GLBufferTarget, data: IntArray, usage: GLBufferUsage)
    fun bufferData(target: GLBufferTarget, data: ShortArray, usage: GLBufferUsage)
    fun bufferData(target: GLBufferTarget, data: ByteArray, usage: GLBufferUsage)
    fun deleteBuffer(buffer: GLBuffer)

    fun enableVertexAttribArray(index: Int)
    fun disableVertexAttribArray(index: Int)
    fun vertexAttribPointer(layout: GLVertexAttribLayout)

    fun createTexture(): GLTexture
    fun bindTexture(target: GLTextureTarget, texture: GLTexture?)
    fun activeTexture(unit: Int)
    fun texImage2D(target: GLTextureTarget, level: Int, format: GLTextureFormat, width: Int, height: Int, bytes: ByteArray?)
    fun texParameters(target: GLTextureTarget, params: GLTextureParameters)
    fun generateMipmap(target: GLTextureTarget)
    fun deleteTexture(texture: GLTexture)

    fun createFramebuffer(): GLFramebuffer
    fun bindFramebuffer(target: GLFramebufferTarget, framebuffer: GLFramebuffer?)
    fun framebufferTexture2D(
        target: GLFramebufferTarget,
        attachment: GLFramebufferAttachment,
        textarget: GLTextureTarget,
        texture: GLTexture,
        level: Int = 0
    )
    fun checkFramebufferStatus(target: GLFramebufferTarget = GLFramebufferTarget.FRAMEBUFFER): GLFramebufferStatus =
        GLFramebufferStatus.COMPLETE
    fun deleteFramebuffer(framebuffer: GLFramebuffer)

    fun drawArrays(mode: GLPrimitive, first: Int, count: Int)
    fun drawElements(mode: GLPrimitive, count: Int, type: GLIndexType, offset: Int)
    fun drawArraysInstanced(mode: GLPrimitive, first: Int, count: Int, instanceCount: Int)
    fun drawElementsInstanced(mode: GLPrimitive, count: Int, type: GLIndexType, offset: Int, instanceCount: Int)

    fun readPixels(x: Int, y: Int, width: Int, height: Int, format: GLReadFormat = GLReadFormat.RGBA8): ByteArray
    fun finish()
    fun flush()
}
