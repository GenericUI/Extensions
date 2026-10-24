package com.nexustools.genericui.component.media

import com.nexustools.genericui.enums.media.ShaderType

/**
 * Cross-platform OpenGL ES 2.0 / 3.0 / 3.2 rendering context exposed by [GLCanvas].
 *
 * Maps to:
 * - Google ANGLE (`EGL` + `GLESv2`/`GLESv3`) on Windows (`:windows`, `:qt`, `:gtk`, `:swing` with LWJGL)
 * - Native `EGL` / `GLESv3` on Linux and Android
 * - Angle / Metal-backed `EAGLContext` / `CGL` on macOS and iOS
 * - `WebGL2RenderingContext` / `WebGLRenderingContext` on `:web`
 */
interface GLContext {
    val rendererString: String
    val vendorString: String
    val versionString: String
    val isAngleBackend: Boolean

    fun viewport(x: Int, y: Int, width: Int, height: Int)
    fun scissor(x: Int, y: Int, width: Int, height: Int)
    fun clearColor(r: Float, g: Float, b: Float, a: Float)
    fun clearDepth(depth: Float)
    fun clearStencil(stencil: Int)
    fun clear(mask: Int)

    fun enable(cap: Int)
    fun disable(cap: Int)
    fun blendFunc(sfactor: Int, dfactor: Int)
    fun blendFuncSeparate(srcRGB: Int, dstRGB: Int, srcAlpha: Int, dstAlpha: Int)
    fun depthFunc(func: Int)
    fun depthMask(flag: Boolean)
    fun cullFace(mode: Int)
    fun frontFace(mode: Int)

    fun createShader(type: ShaderType, source: String): GLShader
    fun deleteShader(shader: GLShader)
    fun createProgram(vertexShader: GLShader, fragmentShader: GLShader): GLProgram
    fun useProgram(program: GLProgram?)
    fun deleteProgram(program: GLProgram)

    fun getAttribLocation(program: GLProgram, name: String): Int
    fun getUniformLocation(program: GLProgram, name: String): GLUniformLocation?
    fun uniform1i(location: GLUniformLocation, v0: Int)
    fun uniform1f(location: GLUniformLocation, v0: Float)
    fun uniform2f(location: GLUniformLocation, v0: Float, v1: Float)
    fun uniform3f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float)
    fun uniform4f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float, v3: Float)
    fun uniformMatrix3fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray)
    fun uniformMatrix4fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray)

    fun createBuffer(): GLBuffer
    fun bindBuffer(target: Int, buffer: GLBuffer?)
    fun bufferDataFloat(target: Int, data: FloatArray, usage: Int)
    fun bufferDataInt(target: Int, data: IntArray, usage: Int)
    fun deleteBuffer(buffer: GLBuffer)

    fun enableVertexAttribArray(index: Int)
    fun disableVertexAttribArray(index: Int)
    fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int)

    fun createTexture(): GLTexture
    fun bindTexture(target: Int, texture: GLTexture?)
    fun activeTexture(textureUnit: Int)
    fun texImage2DRgba(target: Int, level: Int, width: Int, height: Int, rgbaBytes: ByteArray?)
    fun texParameteri(target: Int, pname: Int, param: Int)
    fun generateMipmap(target: Int)
    fun deleteTexture(texture: GLTexture)

    fun createFramebuffer(): GLFramebuffer
    fun bindFramebuffer(target: Int, framebuffer: GLFramebuffer?)
    fun framebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: GLTexture, level: Int)
    fun deleteFramebuffer(framebuffer: GLFramebuffer)

    fun drawArrays(mode: Int, first: Int, count: Int)
    fun drawElements(mode: Int, count: Int, type: Int, offset: Int)
    fun drawArraysInstanced(mode: Int, first: Int, count: Int, instanceCount: Int)
    fun drawElementsInstanced(mode: Int, count: Int, type: Int, offset: Int, instanceCount: Int)

    fun readPixelsRgba(x: Int, y: Int, width: Int, height: Int): ByteArray
    fun finish()
    fun flush()

    companion object {
        const val GL_COLOR_BUFFER_BIT = 0x00004000
        const val GL_DEPTH_BUFFER_BIT = 0x00000100
        const val GL_STENCIL_BUFFER_BIT = 0x00000400

        const val GL_TRIANGLES = 0x0004
        const val GL_TRIANGLE_STRIP = 0x0005
        const val GL_LINES = 0x0001
        const val GL_LINE_STRIP = 0x0003
        const val GL_POINTS = 0x0000

        const val GL_ARRAY_BUFFER = 0x8892
        const val GL_ELEMENT_ARRAY_BUFFER = 0x8893
        const val GL_STATIC_DRAW = 0x88E4
        const val GL_DYNAMIC_DRAW = 0x88E8

        const val GL_TEXTURE_2D = 0x0DE1
        const val GL_TEXTURE0 = 0x84C0
        const val GL_FRAMEBUFFER = 0x8D40
        const val GL_COLOR_ATTACHMENT0 = 0x8CE0

        const val GL_DEPTH_TEST = 0x0B71
        const val GL_BLEND = 0x0BE2
        const val GL_CULL_FACE = 0x0B44
        const val GL_SCISSOR_TEST = 0x0C11

        const val GL_NEVER = 0x0200
        const val GL_LESS = 0x0201
        const val GL_EQUAL = 0x0202
        const val GL_LEQUAL = 0x0203
        const val GL_GREATER = 0x0204
        const val GL_NOTEQUAL = 0x0205
        const val GL_GEQUAL = 0x0206
        const val GL_ALWAYS = 0x0207

        const val GL_FRONT = 0x0404
        const val GL_BACK = 0x0405
        const val GL_FRONT_AND_BACK = 0x0408

        const val GL_FLOAT = 0x1406
        const val GL_UNSIGNED_INT = 0x1405
        const val GL_UNSIGNED_SHORT = 0x1403
        const val GL_UNSIGNED_BYTE = 0x1401
    }
}
