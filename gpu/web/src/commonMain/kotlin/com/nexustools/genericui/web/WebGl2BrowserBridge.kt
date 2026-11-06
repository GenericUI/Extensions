package com.nexustools.genericui.web

/**
 * Multiplatform (`js` and `wasmJs`) hardware WebGL 2.0 (`WebGL2RenderingContext`) bridge for `:extensions:gpu:web`.
 *
 * Maintains per-context integer handle tables for WebGL objects (`WebGLShader`, `WebGLProgram`,
 * `WebGLBuffer`, `WebGLTexture`, `WebGLFramebuffer`, `WebGLRenderbuffer`, `WebGLVertexArrayObject`,
 * and `WebGLUniformLocation`) so [WebGLContextImpl] can delegate all [com.nexustools.genericui.component.media.GLContext]
 * operations directly to the browser's hardware GPU when running in a browser DOM environment.
 */
expect object WebGl2BrowserBridge {
    fun isWebGl2Supported(): Boolean
    fun getOrCreateContext(canvasElement: WebDomElement): Int
    fun destroyContext(contextId: Int)

    fun getString(contextId: Int, name: Int): String
    fun getInteger(contextId: Int, pname: Int): Int
    fun getError(contextId: Int): Int

    fun viewport(contextId: Int, x: Int, y: Int, width: Int, height: Int)
    fun scissor(contextId: Int, x: Int, y: Int, width: Int, height: Int)
    fun clearColor(contextId: Int, r: Float, g: Float, b: Float, a: Float)
    fun clearDepth(contextId: Int, depth: Float)
    fun clearStencil(contextId: Int, stencil: Int)
    fun clear(contextId: Int, mask: Int)

    fun enable(contextId: Int, cap: Int)
    fun disable(contextId: Int, cap: Int)
    fun blendFunc(contextId: Int, sfactor: Int, dfactor: Int)
    fun blendFuncSeparate(contextId: Int, srcRGB: Int, dstRGB: Int, srcAlpha: Int, dstAlpha: Int)
    fun blendEquation(contextId: Int, mode: Int)
    fun depthFunc(contextId: Int, func: Int)
    fun depthMask(contextId: Int, flag: Boolean)
    fun cullFace(contextId: Int, mode: Int)
    fun frontFace(contextId: Int, mode: Int)
    fun colorMask(contextId: Int, red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean)

    fun createShader(contextId: Int, type: Int): Int
    fun shaderSource(contextId: Int, shaderId: Int, source: String)
    fun compileShader(contextId: Int, shaderId: Int)
    fun getShaderParameter(contextId: Int, shaderId: Int, pname: Int): Int
    fun getShaderInfoLog(contextId: Int, shaderId: Int): String
    fun deleteShader(contextId: Int, shaderId: Int)

    fun createProgram(contextId: Int): Int
    fun attachShader(contextId: Int, programId: Int, shaderId: Int)
    fun detachShader(contextId: Int, programId: Int, shaderId: Int)
    fun linkProgram(contextId: Int, programId: Int)
    fun getProgramParameter(contextId: Int, programId: Int, pname: Int): Int
    fun getProgramInfoLog(contextId: Int, programId: Int): String
    fun useProgram(contextId: Int, programId: Int)
    fun deleteProgram(contextId: Int, programId: Int)

    fun getAttribLocation(contextId: Int, programId: Int, name: String): Int
    fun getUniformLocation(contextId: Int, programId: Int, name: String): Int
    fun getUniformBlockIndex(contextId: Int, programId: Int, blockName: String): Int
    fun uniformBlockBinding(contextId: Int, programId: Int, uniformBlockIndex: Int, uniformBlockBinding: Int)

    fun uniform1i(contextId: Int, locationId: Int, v0: Int)
    fun uniform1f(contextId: Int, locationId: Int, v0: Float)
    fun uniform2f(contextId: Int, locationId: Int, v0: Float, v1: Float)
    fun uniform3f(contextId: Int, locationId: Int, v0: Float, v1: Float, v2: Float)
    fun uniform4f(contextId: Int, locationId: Int, v0: Float, v1: Float, v2: Float, v3: Float)
    fun uniform1fv(contextId: Int, locationId: Int, values: FloatArray)
    fun uniform2fv(contextId: Int, locationId: Int, values: FloatArray)
    fun uniform3fv(contextId: Int, locationId: Int, values: FloatArray)
    fun uniform4fv(contextId: Int, locationId: Int, values: FloatArray)
    fun uniformMatrix3fv(contextId: Int, locationId: Int, transpose: Boolean, values: FloatArray)
    fun uniformMatrix4fv(contextId: Int, locationId: Int, transpose: Boolean, values: FloatArray)

    fun createBuffer(contextId: Int): Int
    fun bindBuffer(contextId: Int, target: Int, bufferId: Int)
    fun bindBufferBase(contextId: Int, target: Int, index: Int, bufferId: Int)
    fun bufferData(contextId: Int, target: Int, sizeBytes: Int, usage: Int)
    fun bufferData(contextId: Int, target: Int, data: ByteArray, usage: Int)
    fun bufferData(contextId: Int, target: Int, data: FloatArray, usage: Int)
    fun bufferData(contextId: Int, target: Int, data: IntArray, usage: Int)
    fun bufferData(contextId: Int, target: Int, data: ShortArray, usage: Int)
    fun bufferSubData(contextId: Int, target: Int, offsetBytes: Int, data: ByteArray)
    fun bufferSubData(contextId: Int, target: Int, offsetBytes: Int, data: FloatArray)
    fun deleteBuffer(contextId: Int, bufferId: Int)

    fun createVertexArray(contextId: Int): Int
    fun bindVertexArray(contextId: Int, vaoId: Int)
    fun deleteVertexArray(contextId: Int, vaoId: Int)
    fun enableVertexAttribArray(contextId: Int, index: Int)
    fun disableVertexAttribArray(contextId: Int, index: Int)
    fun vertexAttribPointer(
        contextId: Int,
        index: Int,
        size: Int,
        type: Int,
        normalized: Boolean,
        stride: Int,
        offset: Int
    )
    fun vertexAttribDivisor(contextId: Int, index: Int, divisor: Int)

    fun createTexture(contextId: Int): Int
    fun bindTexture(contextId: Int, target: Int, textureId: Int)
    fun activeTexture(contextId: Int, textureUnit: Int)
    fun texImage2D(
        contextId: Int,
        target: Int,
        level: Int,
        internalFormat: Int,
        width: Int,
        height: Int,
        border: Int,
        format: Int,
        type: Int,
        pixels: ByteArray?
    )
    fun texSubImage2D(
        contextId: Int,
        target: Int,
        level: Int,
        xOffset: Int,
        yOffset: Int,
        width: Int,
        height: Int,
        format: Int,
        type: Int,
        pixels: ByteArray
    )
    fun texParameteri(contextId: Int, target: Int, pname: Int, param: Int)
    fun texParameterf(contextId: Int, target: Int, pname: Int, param: Float)
    fun generateMipmap(contextId: Int, target: Int)
    fun deleteTexture(contextId: Int, textureId: Int)

    fun createFramebuffer(contextId: Int): Int
    fun bindFramebuffer(contextId: Int, target: Int, framebufferId: Int)
    fun framebufferTexture2D(
        contextId: Int,
        target: Int,
        attachment: Int,
        textarget: Int,
        textureId: Int,
        level: Int
    )
    fun framebufferRenderbuffer(
        contextId: Int,
        target: Int,
        attachment: Int,
        renderbufferTarget: Int,
        renderbufferId: Int
    )
    fun checkFramebufferStatus(contextId: Int, target: Int): Int
    fun deleteFramebuffer(contextId: Int, framebufferId: Int)

    fun createRenderbuffer(contextId: Int): Int
    fun bindRenderbuffer(contextId: Int, target: Int, renderbufferId: Int)
    fun renderbufferStorage(contextId: Int, target: Int, internalFormat: Int, width: Int, height: Int)
    fun deleteRenderbuffer(contextId: Int, renderbufferId: Int)

    fun drawArrays(contextId: Int, mode: Int, first: Int, count: Int)
    fun drawElements(contextId: Int, mode: Int, count: Int, type: Int, offset: Int)
    fun drawArraysInstanced(contextId: Int, mode: Int, first: Int, count: Int, instanceCount: Int)
    fun drawElementsInstanced(contextId: Int, mode: Int, count: Int, type: Int, offset: Int, instanceCount: Int)

    fun readPixels(
        contextId: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        format: Int,
        type: Int
    ): ByteArray
    fun finish(contextId: Int)
    fun flush(contextId: Int)
}
