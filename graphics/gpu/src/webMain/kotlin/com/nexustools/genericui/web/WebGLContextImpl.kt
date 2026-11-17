package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.GLBackendKind
import com.nexustools.genericui.component.media.GLBlendEquation
import com.nexustools.genericui.component.media.GLBlendFactor
import com.nexustools.genericui.component.media.GLBuffer
import com.nexustools.genericui.component.media.GLBufferTarget
import com.nexustools.genericui.component.media.GLBufferUsage
import com.nexustools.genericui.component.media.GLCapability
import com.nexustools.genericui.component.media.GLClearBuffer
import com.nexustools.genericui.component.media.GLCompareFunction
import com.nexustools.genericui.component.media.GLCompileStatus
import com.nexustools.genericui.component.media.GLContextAttributes
import com.nexustools.genericui.component.media.GLContextInfo
import com.nexustools.genericui.component.media.GLContextVersion
import com.nexustools.genericui.component.media.GLDataType
import com.nexustools.genericui.component.media.GLFaceSide
import com.nexustools.genericui.component.media.GLFramebuffer
import com.nexustools.genericui.component.media.GLFramebufferAttachment
import com.nexustools.genericui.component.media.GLFramebufferStatus
import com.nexustools.genericui.component.media.GLFramebufferTarget
import com.nexustools.genericui.component.media.GLIndexType
import com.nexustools.genericui.component.media.GLPrimitive
import com.nexustools.genericui.component.media.GLProgram
import com.nexustools.genericui.component.media.GLReadFormat
import com.nexustools.genericui.component.media.GLShader
import com.nexustools.genericui.component.media.GLShadingLanguage
import com.nexustools.genericui.component.media.GLTexture
import com.nexustools.genericui.component.media.GLTextureFilter
import com.nexustools.genericui.component.media.GLTextureFormat
import com.nexustools.genericui.component.media.GLTextureParameters
import com.nexustools.genericui.component.media.GLTextureTarget
import com.nexustools.genericui.component.media.GLTextureWrap
import com.nexustools.genericui.component.media.GLUniformLocation
import com.nexustools.genericui.component.media.GLVertexAttribLayout
import com.nexustools.genericui.component.media.GLWinding
import com.nexustools.genericui.desktop.canvas.DesktopGLContextImpl
import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.ext.gpu.GpuAcceleration

/**
 * Web (`WebGL2RenderingContext`) implementation of [com.nexustools.genericui.component.media.GLContext].
 *
 * When running in a browser DOM environment ([WebBrowserDomBridge.isBrowserEnvironment]), all OpenGL ES 3.0 /
 * WebGL 2.0 calls delegate directly to the browser's hardware GPU via [WebGl2BrowserBridge] without invoking
 * the CPU software fallback in [DesktopGLContextImpl] or mutating DOM attributes per command.
 * In headless unit tests (`!WebBrowserDomBridge.isBrowserEnvironment`), calls fall back to [DesktopGLContextImpl].
 */
class WebGLContextImpl(
    val canvasElement: WebDomElement = WebDomElement("canvas"),
    config: GLContextAttributes = GLContextAttributes(),
    initialWidth: Int = 800,
    initialHeight: Int = 600,
    acceleration: GpuAcceleration = GpuAcceleration.HARDWARE
) : DesktopGLContextImpl(
    config = config,
    initialWidth = if (WebBrowserDomBridge.isBrowserEnvironment) 1 else initialWidth,
    initialHeight = if (WebBrowserDomBridge.isBrowserEnvironment) 1 else initialHeight,
    contextInfo = GLContextInfo(
        backend = GLBackendKind.WEBGL2,
        version = config.version,
        shadingLanguage = when (config.version) {
            GLContextVersion.ES_3_0 -> GLShadingLanguage.GLSL_ES_300
            GLContextVersion.ES_3_1 -> GLShadingLanguage.GLSL_ES_310
            GLContextVersion.ES_3_2 -> GLShadingLanguage.GLSL_ES_320
        },
        acceleration = acceleration,
        renderer = "WebGL 2.0",
        vendor = "WebKit / Blink"
    )
) {

    private var cachedHwContextId: Int = 0
    private var contextDestroyed: Boolean = false

    /**
     * Non-zero hardware context handle managed by [WebGl2BrowserBridge] when running in a browser
     * with WebGL 2 support; `0` in headless unit test environments.
     */
    val hwContextId: Int
        get() = resolveHwContextId()

    init {
        if (canvasElement.getAttribute("width") == null) {
            canvasElement.setAttribute("width", initialWidth.coerceAtLeast(1).toString())
        }
        if (canvasElement.getAttribute("height") == null) {
            canvasElement.setAttribute("height", initialHeight.coerceAtLeast(1).toString())
        }
        canvasElement.setAttribute("data-webgl2", "true")
        canvasElement.setAttribute("data-webgl-antialias", config.antialias.toString())
        canvasElement.setAttribute("data-webgl-depth", config.depth.toString())
        canvasElement.setAttribute("data-webgl-stencil", config.stencil.toString())
        canvasElement.setAttribute("data-webgl-alpha", config.alpha.toString())

        resolveHwContextId()
    }

    private fun resolveHwContextId(): Int {
        if (contextDestroyed || !WebBrowserDomBridge.isBrowserEnvironment) return 0
        if (cachedHwContextId == 0) {
            cachedHwContextId = WebGl2BrowserBridge.getOrCreateContext(canvasElement)
        }
        return cachedHwContextId
    }

    /**
     * Resizes the underlying WebGL2 `<canvas>` drawing buffer without allocating CPU software buffers
     * when hardware WebGL2 ([hwContextId] != 0) is active.
     */
    fun resizeWebSurface(width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val wStr = safeW.toString()
        val hStr = safeH.toString()
        if (canvasElement.getAttribute("width") != wStr) {
            canvasElement.setAttribute("width", wStr)
        }
        if (canvasElement.getAttribute("height") != hStr) {
            canvasElement.setAttribute("height", hStr)
        }
        canvasElement.clientWidth = safeW
        canvasElement.clientHeight = safeH
        if (hwContextId == 0) {
            resizeSurface(safeW, safeH)
        }
    }

    /**
     * Releases the hardware WebGL2 context entry in [WebGl2BrowserBridge] if active.
     */
    fun destroyContext() {
        contextDestroyed = true
        val id = cachedHwContextId
        cachedHwContextId = 0
        if (id != 0) {
            WebGl2BrowserBridge.destroyContext(id)
        }
    }

    override fun viewport(x: Int, y: Int, width: Int, height: Int) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.viewport(hwContextId, x, y, width, height)
            return
        }
        super.viewport(x, y, width, height)
        canvasElement.setAttribute("data-viewport", "$x,$y,$width,$height")
    }

    override fun scissor(x: Int, y: Int, width: Int, height: Int) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.scissor(hwContextId, x, y, width, height)
            return
        }
        super.scissor(x, y, width, height)
    }

    override fun clearColor(r: Float, g: Float, b: Float, a: Float) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.clearColor(hwContextId, r, g, b, a)
            return
        }
        super.clearColor(r, g, b, a)
        canvasElement.setAttribute("data-clear-color", "$r,$g,$b,$a")
    }

    override fun clearDepth(depth: Float) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.clearDepth(hwContextId, depth)
            return
        }
        super.clearDepth(depth)
    }

    override fun clearStencil(stencil: Int) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.clearStencil(hwContextId, stencil)
            return
        }
        super.clearStencil(stencil)
    }

    override fun clear(buffers: Set<GLClearBuffer>) {
        if (hwContextId != 0) {
            var mask = 0
            for (buf in buffers) {
                mask = mask or when (buf) {
                    GLClearBuffer.COLOR -> GL_COLOR_BUFFER_BIT
                    GLClearBuffer.DEPTH -> GL_DEPTH_BUFFER_BIT
                    GLClearBuffer.STENCIL -> GL_STENCIL_BUFFER_BIT
                }
            }
            if (mask != 0) {
                WebGl2BrowserBridge.clear(hwContextId, mask)
            }
            return
        }
        super.clear(buffers)
        canvasElement.setAttribute("data-last-clear-mask", buffers.joinToString(",") { it.name })
    }

    override fun enable(capability: GLCapability) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.enable(hwContextId, toGlCapability(capability))
            return
        }
        super.enable(capability)
    }

    override fun disable(capability: GLCapability) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.disable(hwContextId, toGlCapability(capability))
            return
        }
        super.disable(capability)
    }

    override fun blendFunc(sfactor: GLBlendFactor, dfactor: GLBlendFactor) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.blendFunc(hwContextId, toGlBlendFactor(sfactor), toGlBlendFactor(dfactor))
            return
        }
        super.blendFunc(sfactor, dfactor)
    }

    override fun blendFuncSeparate(
        srcRGB: GLBlendFactor,
        dstRGB: GLBlendFactor,
        srcAlpha: GLBlendFactor,
        dstAlpha: GLBlendFactor
    ) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.blendFuncSeparate(
                hwContextId,
                toGlBlendFactor(srcRGB),
                toGlBlendFactor(dstRGB),
                toGlBlendFactor(srcAlpha),
                toGlBlendFactor(dstAlpha)
            )
            return
        }
        super.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha)
    }

    override fun blendEquation(mode: GLBlendEquation) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.blendEquation(hwContextId, toGlBlendEquation(mode))
            return
        }
        super.blendEquation(mode)
    }

    override fun depthFunc(func: GLCompareFunction) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.depthFunc(hwContextId, toGlCompareFunc(func))
            return
        }
        super.depthFunc(func)
    }

    override fun depthMask(flag: Boolean) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.depthMask(hwContextId, flag)
            return
        }
        super.depthMask(flag)
    }

    override fun cullFace(mode: GLFaceSide) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.cullFace(hwContextId, toGlFaceSide(mode))
            return
        }
        super.cullFace(mode)
    }

    override fun frontFace(mode: GLWinding) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.frontFace(hwContextId, toGlWinding(mode))
            return
        }
        super.frontFace(mode)
    }

    override fun createShader(type: ShaderType, source: String): GLShader {
        if (hwContextId != 0) {
            val glType = when (type) {
                ShaderType.VERTEX -> GL_VERTEX_SHADER
                ShaderType.FRAGMENT, ShaderType.COMPUTE -> GL_FRAGMENT_SHADER
            }
            val shaderId = WebGl2BrowserBridge.createShader(hwContextId, glType)
            WebGl2BrowserBridge.shaderSource(hwContextId, shaderId, source)
            WebGl2BrowserBridge.compileShader(hwContextId, shaderId)
            val compiled = WebGl2BrowserBridge.getShaderParameter(hwContextId, shaderId, GL_COMPILE_STATUS) != 0
            val infoLog = if (!compiled) WebGl2BrowserBridge.getShaderInfoLog(hwContextId, shaderId) else ""
            return GLShader(
                id = shaderId,
                type = type,
                compileStatus = GLCompileStatus(success = compiled, infoLog = infoLog)
            )
        }
        return super.createShader(type, source)
    }

    override fun deleteShader(shader: GLShader) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.deleteShader(hwContextId, shader.id)
            return
        }
        super.deleteShader(shader)
    }

    override fun createProgram(vertexShader: GLShader, fragmentShader: GLShader): GLProgram {
        if (hwContextId != 0) {
            val programId = WebGl2BrowserBridge.createProgram(hwContextId)
            WebGl2BrowserBridge.attachShader(hwContextId, programId, vertexShader.id)
            WebGl2BrowserBridge.attachShader(hwContextId, programId, fragmentShader.id)
            WebGl2BrowserBridge.linkProgram(hwContextId, programId)
            val linked = WebGl2BrowserBridge.getProgramParameter(hwContextId, programId, GL_LINK_STATUS) != 0
            val infoLog = if (!linked) WebGl2BrowserBridge.getProgramInfoLog(hwContextId, programId) else ""
            return GLProgram(
                id = programId,
                linkStatus = GLCompileStatus(success = linked, infoLog = infoLog)
            )
        }
        return super.createProgram(vertexShader, fragmentShader)
    }

    override fun useProgram(program: GLProgram?) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.useProgram(hwContextId, program?.id ?: 0)
            return
        }
        super.useProgram(program)
        canvasElement.setAttribute("data-active-program", (program?.id ?: 0).toString())
    }

    override fun deleteProgram(program: GLProgram) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.deleteProgram(hwContextId, program.id)
            return
        }
        super.deleteProgram(program)
    }

    override fun getAttribLocation(program: GLProgram, name: String): Int {
        if (hwContextId != 0) {
            return WebGl2BrowserBridge.getAttribLocation(hwContextId, program.id, name)
        }
        return super.getAttribLocation(program, name)
    }

    override fun getUniformLocation(program: GLProgram, name: String): GLUniformLocation? {
        if (hwContextId != 0) {
            val locId = WebGl2BrowserBridge.getUniformLocation(hwContextId, program.id, name)
            return if (locId > 0) GLUniformLocation(id = locId, name = name) else null
        }
        return super.getUniformLocation(program, name)
    }

    override fun getUniformBlockIndex(program: GLProgram, blockName: String): Int {
        if (hwContextId != 0) {
            return WebGl2BrowserBridge.getUniformBlockIndex(hwContextId, program.id, blockName)
        }
        return super.getUniformBlockIndex(program, blockName)
    }

    override fun uniformBlockBinding(program: GLProgram, uniformBlockIndex: Int, uniformBlockBinding: Int) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.uniformBlockBinding(hwContextId, program.id, uniformBlockIndex, uniformBlockBinding)
            return
        }
        super.uniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding)
    }

    override fun uniform1i(location: GLUniformLocation, v0: Int) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.uniform1i(hwContextId, location.id, v0)
            return
        }
        super.uniform1i(location, v0)
    }

    override fun uniform1f(location: GLUniformLocation, v0: Float) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.uniform1f(hwContextId, location.id, v0)
            return
        }
        super.uniform1f(location, v0)
    }

    override fun uniform2f(location: GLUniformLocation, v0: Float, v1: Float) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.uniform2f(hwContextId, location.id, v0, v1)
            return
        }
        super.uniform2f(location, v0, v1)
    }

    override fun uniform3f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.uniform3f(hwContextId, location.id, v0, v1, v2)
            return
        }
        super.uniform3f(location, v0, v1, v2)
    }

    override fun uniform4f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float, v3: Float) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.uniform4f(hwContextId, location.id, v0, v1, v2, v3)
            return
        }
        super.uniform4f(location, v0, v1, v2, v3)
    }

    override fun uniformMatrix3fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.uniformMatrix3fv(hwContextId, location.id, transpose, values)
            return
        }
        super.uniformMatrix3fv(location, transpose, values)
    }

    override fun uniformMatrix4fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.uniformMatrix4fv(hwContextId, location.id, transpose, values)
            return
        }
        super.uniformMatrix4fv(location, transpose, values)
    }

    override fun createBuffer(): GLBuffer {
        if (hwContextId != 0) {
            return GLBuffer(id = WebGl2BrowserBridge.createBuffer(hwContextId))
        }
        return super.createBuffer()
    }

    override fun bindBuffer(target: GLBufferTarget, buffer: GLBuffer?) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.bindBuffer(hwContextId, toGlBufferTarget(target), buffer?.id ?: 0)
            return
        }
        super.bindBuffer(target, buffer)
    }

    override fun bindBufferBase(target: GLBufferTarget, index: Int, buffer: GLBuffer?) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.bindBufferBase(hwContextId, toGlBufferTarget(target), index, buffer?.id ?: 0)
            return
        }
        super.bindBufferBase(target, index, buffer)
    }

    override fun bufferData(target: GLBufferTarget, data: FloatArray, usage: GLBufferUsage) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.bufferData(hwContextId, toGlBufferTarget(target), data, toGlBufferUsage(usage))
            return
        }
        super.bufferData(target, data, usage)
    }

    override fun bufferData(target: GLBufferTarget, data: IntArray, usage: GLBufferUsage) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.bufferData(hwContextId, toGlBufferTarget(target), data, toGlBufferUsage(usage))
            return
        }
        super.bufferData(target, data, usage)
    }

    override fun bufferData(target: GLBufferTarget, data: ShortArray, usage: GLBufferUsage) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.bufferData(hwContextId, toGlBufferTarget(target), data, toGlBufferUsage(usage))
            return
        }
        super.bufferData(target, data, usage)
    }

    override fun bufferData(target: GLBufferTarget, data: ByteArray, usage: GLBufferUsage) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.bufferData(hwContextId, toGlBufferTarget(target), data, toGlBufferUsage(usage))
            return
        }
        super.bufferData(target, data, usage)
    }

    override fun deleteBuffer(buffer: GLBuffer) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.deleteBuffer(hwContextId, buffer.id)
            return
        }
        super.deleteBuffer(buffer)
    }

    override fun enableVertexAttribArray(index: Int) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.enableVertexAttribArray(hwContextId, index)
            return
        }
        super.enableVertexAttribArray(index)
    }

    override fun disableVertexAttribArray(index: Int) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.disableVertexAttribArray(hwContextId, index)
            return
        }
        super.disableVertexAttribArray(index)
    }

    override fun vertexAttribPointer(layout: GLVertexAttribLayout) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.vertexAttribPointer(
                contextId = hwContextId,
                index = layout.index,
                size = layout.size,
                type = toGlDataType(layout.type),
                normalized = layout.normalized,
                stride = layout.stride,
                offset = layout.offset
            )
            return
        }
        super.vertexAttribPointer(layout)
    }

    override fun createTexture(): GLTexture {
        if (hwContextId != 0) {
            return GLTexture(id = WebGl2BrowserBridge.createTexture(hwContextId))
        }
        return super.createTexture()
    }

    override fun bindTexture(target: GLTextureTarget, texture: GLTexture?) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.bindTexture(hwContextId, toGlTextureTarget(target), texture?.id ?: 0)
            return
        }
        super.bindTexture(target, texture)
    }

    override fun activeTexture(unit: Int) {
        if (hwContextId != 0) {
            val resolvedUnit = if (unit >= GL_TEXTURE0) unit else (GL_TEXTURE0 + unit.coerceIn(0, 31))
            WebGl2BrowserBridge.activeTexture(hwContextId, resolvedUnit)
            return
        }
        super.activeTexture(unit)
    }

    override fun texImage2D(
        target: GLTextureTarget,
        level: Int,
        format: GLTextureFormat,
        width: Int,
        height: Int,
        bytes: ByteArray?
    ) {
        if (hwContextId != 0) {
            val internalFormat = toGlInternalFormat(format)
            val glFormat = toGlBaseFormat(format)
            val glType = toGlPixelType(format, width, height, bytes)
            WebGl2BrowserBridge.texImage2D(
                contextId = hwContextId,
                target = toGlTextureTarget(target),
                level = level,
                internalFormat = internalFormat,
                width = width,
                height = height,
                border = 0,
                format = glFormat,
                type = glType,
                pixels = bytes
            )
            return
        }
        super.texImage2D(target, level, format, width, height, bytes)
    }

    override fun texParameters(target: GLTextureTarget, params: GLTextureParameters) {
        if (hwContextId != 0) {
            val glTarget = toGlTextureTarget(target)
            WebGl2BrowserBridge.texParameteri(hwContextId, glTarget, GL_TEXTURE_MIN_FILTER, toGlTextureFilter(params.minFilter))
            WebGl2BrowserBridge.texParameteri(hwContextId, glTarget, GL_TEXTURE_MAG_FILTER, toGlTextureFilter(params.magFilter))
            WebGl2BrowserBridge.texParameteri(hwContextId, glTarget, GL_TEXTURE_WRAP_S, toGlTextureWrap(params.wrapS))
            WebGl2BrowserBridge.texParameteri(hwContextId, glTarget, GL_TEXTURE_WRAP_T, toGlTextureWrap(params.wrapT))
            return
        }
        super.texParameters(target, params)
    }

    override fun generateMipmap(target: GLTextureTarget) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.generateMipmap(hwContextId, toGlTextureTarget(target))
            return
        }
        super.generateMipmap(target)
    }

    override fun deleteTexture(texture: GLTexture) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.deleteTexture(hwContextId, texture.id)
            return
        }
        super.deleteTexture(texture)
    }

    override fun createFramebuffer(): GLFramebuffer {
        if (hwContextId != 0) {
            return GLFramebuffer(id = WebGl2BrowserBridge.createFramebuffer(hwContextId))
        }
        return super.createFramebuffer()
    }

    override fun bindFramebuffer(target: GLFramebufferTarget, framebuffer: GLFramebuffer?) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.bindFramebuffer(hwContextId, toGlFramebufferTarget(target), framebuffer?.id ?: 0)
            return
        }
        super.bindFramebuffer(target, framebuffer)
    }

    override fun framebufferTexture2D(
        target: GLFramebufferTarget,
        attachment: GLFramebufferAttachment,
        textarget: GLTextureTarget,
        texture: GLTexture,
        level: Int
    ) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.framebufferTexture2D(
                contextId = hwContextId,
                target = toGlFramebufferTarget(target),
                attachment = toGlFramebufferAttachment(attachment),
                textarget = toGlTextureTarget(textarget),
                textureId = texture.id,
                level = level
            )
            return
        }
        super.framebufferTexture2D(target, attachment, textarget, texture, level)
    }

    override fun checkFramebufferStatus(target: GLFramebufferTarget): GLFramebufferStatus {
        if (hwContextId != 0) {
            return when (WebGl2BrowserBridge.checkFramebufferStatus(hwContextId, toGlFramebufferTarget(target))) {
                GL_FRAMEBUFFER_COMPLETE -> GLFramebufferStatus.COMPLETE
                GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT -> GLFramebufferStatus.INCOMPLETE_ATTACHMENT
                GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT -> GLFramebufferStatus.INCOMPLETE_MISSING_ATTACHMENT
                GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS -> GLFramebufferStatus.INCOMPLETE_DIMENSIONS
                GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE -> GLFramebufferStatus.INCOMPLETE_MULTISAMPLE
                else -> GLFramebufferStatus.UNSUPPORTED
            }
        }
        return super.checkFramebufferStatus(target)
    }

    override fun deleteFramebuffer(framebuffer: GLFramebuffer) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.deleteFramebuffer(hwContextId, framebuffer.id)
            return
        }
        super.deleteFramebuffer(framebuffer)
    }

    override fun drawArrays(mode: GLPrimitive, first: Int, count: Int) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.drawArrays(hwContextId, toGlPrimitive(mode), first, count)
            return
        }
        super.drawArrays(mode, first, count)
        canvasElement.setAttribute("data-last-draw", "drawArrays:${mode.name}:$first:$count")
    }

    override fun drawElements(mode: GLPrimitive, count: Int, type: GLIndexType, offset: Int) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.drawElements(hwContextId, toGlPrimitive(mode), count, toGlIndexType(type), offset)
            return
        }
        super.drawElements(mode, count, type, offset)
        canvasElement.setAttribute("data-last-draw", "drawElements:${mode.name}:$count:${type.name}:$offset")
    }

    override fun drawArraysInstanced(mode: GLPrimitive, first: Int, count: Int, instanceCount: Int) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.drawArraysInstanced(hwContextId, toGlPrimitive(mode), first, count, instanceCount)
            return
        }
        super.drawArraysInstanced(mode, first, count, instanceCount)
    }

    override fun drawElementsInstanced(
        mode: GLPrimitive,
        count: Int,
        type: GLIndexType,
        offset: Int,
        instanceCount: Int
    ) {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.drawElementsInstanced(
                hwContextId,
                toGlPrimitive(mode),
                count,
                toGlIndexType(type),
                offset,
                instanceCount
            )
            return
        }
        super.drawElementsInstanced(mode, count, type, offset, instanceCount)
    }

    override fun readPixels(x: Int, y: Int, width: Int, height: Int, format: GLReadFormat): ByteArray {
        if (hwContextId != 0) {
            val glFormat = when (format) {
                GLReadFormat.RGBA8 -> GL_RGBA
                GLReadFormat.RGB8 -> GL_RGB
            }
            return WebGl2BrowserBridge.readPixels(hwContextId, x, y, width, height, glFormat, GL_UNSIGNED_BYTE)
        }
        return super.readPixels(x, y, width, height, format)
    }

    override fun finish() {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.finish(hwContextId)
            return
        }
        super.finish()
    }

    override fun flush() {
        if (hwContextId != 0) {
            WebGl2BrowserBridge.flush(hwContextId)
            return
        }
        super.flush()
    }

    companion object {
        private const val GL_DEPTH_BUFFER_BIT = 0x00000100
        private const val GL_STENCIL_BUFFER_BIT = 0x00000400
        private const val GL_COLOR_BUFFER_BIT = 0x00004000

        private const val GL_POINTS = 0x0000
        private const val GL_LINES = 0x0001
        private const val GL_LINE_LOOP = 0x0002
        private const val GL_LINE_STRIP = 0x0003
        private const val GL_TRIANGLES = 0x0004
        private const val GL_TRIANGLE_STRIP = 0x0005
        private const val GL_TRIANGLE_FAN = 0x0006

        private const val GL_ZERO = 0
        private const val GL_ONE = 1
        private const val GL_SRC_COLOR = 0x0300
        private const val GL_ONE_MINUS_SRC_COLOR = 0x0301
        private const val GL_SRC_ALPHA = 0x0302
        private const val GL_ONE_MINUS_SRC_ALPHA = 0x0303
        private const val GL_DST_ALPHA = 0x0304
        private const val GL_ONE_MINUS_DST_ALPHA = 0x0305
        private const val GL_DST_COLOR = 0x0306
        private const val GL_ONE_MINUS_DST_COLOR = 0x0307
        private const val GL_SRC_ALPHA_SATURATE = 0x0308
        private const val GL_CONSTANT_COLOR = 0x8001
        private const val GL_ONE_MINUS_CONSTANT_COLOR = 0x8002
        private const val GL_CONSTANT_ALPHA = 0x8003
        private const val GL_ONE_MINUS_CONSTANT_ALPHA = 0x8004

        private const val GL_FUNC_ADD = 0x8006
        private const val GL_MIN = 0x8007
        private const val GL_MAX = 0x8008
        private const val GL_FUNC_SUBTRACT = 0x800A
        private const val GL_FUNC_REVERSE_SUBTRACT = 0x800B

        private const val GL_NEVER = 0x0200
        private const val GL_LESS = 0x0201
        private const val GL_EQUAL = 0x0202
        private const val GL_LEQUAL = 0x0203
        private const val GL_GREATER = 0x0204
        private const val GL_NOTEQUAL = 0x0205
        private const val GL_GEQUAL = 0x0206
        private const val GL_ALWAYS = 0x0207

        private const val GL_FRONT = 0x0404
        private const val GL_BACK = 0x0405
        private const val GL_FRONT_AND_BACK = 0x0408

        private const val GL_CW = 0x0900
        private const val GL_CCW = 0x0901

        private const val GL_CULL_FACE = 0x0B44
        private const val GL_DEPTH_TEST = 0x0B71
        private const val GL_STENCIL_TEST = 0x0B90
        private const val GL_BLEND = 0x0BE2
        private const val GL_SCISSOR_TEST = 0x0C11
        private const val GL_POLYGON_OFFSET_FILL = 0x8037
        private const val GL_SAMPLE_ALPHA_TO_COVERAGE = 0x809E

        private const val GL_TEXTURE_2D = 0x0DE1
        private const val GL_TEXTURE_3D = 0x806F
        private const val GL_TEXTURE_CUBE_MAP = 0x8513
        private const val GL_TEXTURE_2D_ARRAY = 0x8C1A

        private const val GL_BYTE = 0x1400
        private const val GL_UNSIGNED_BYTE = 0x1401
        private const val GL_SHORT = 0x1402
        private const val GL_UNSIGNED_SHORT = 0x1403
        private const val GL_INT = 0x1404
        private const val GL_UNSIGNED_INT = 0x1405
        private const val GL_FLOAT = 0x1406
        private const val GL_HALF_FLOAT = 0x140B

        private const val GL_DEPTH_COMPONENT = 0x1902
        private const val GL_RED = 0x1903
        private const val GL_RGB = 0x1907
        private const val GL_RGBA = 0x1908
        private const val GL_RG = 0x8227
        private const val GL_DEPTH_STENCIL = 0x84F9
        private const val GL_UNSIGNED_INT_24_8 = 0x84FA

        private const val GL_R8 = 0x8229
        private const val GL_RG8 = 0x822B
        private const val GL_RGB8 = 0x8051
        private const val GL_RGBA8 = 0x8058
        private const val GL_RGBA16F = 0x881A
        private const val GL_RGB16F = 0x881B
        private const val GL_DEPTH_COMPONENT24 = 0x81A6
        private const val GL_DEPTH24_STENCIL8 = 0x88F0

        private const val GL_NEAREST = 0x2600
        private const val GL_LINEAR = 0x2601
        private const val GL_NEAREST_MIPMAP_NEAREST = 0x2700
        private const val GL_LINEAR_MIPMAP_NEAREST = 0x2701
        private const val GL_NEAREST_MIPMAP_LINEAR = 0x2702
        private const val GL_LINEAR_MIPMAP_LINEAR = 0x2703

        private const val GL_TEXTURE_MAG_FILTER = 0x2800
        private const val GL_TEXTURE_MIN_FILTER = 0x2801
        private const val GL_TEXTURE_WRAP_S = 0x2802
        private const val GL_TEXTURE_WRAP_T = 0x2803

        private const val GL_REPEAT = 0x2901
        private const val GL_CLAMP_TO_EDGE = 0x812F
        private const val GL_MIRRORED_REPEAT = 0x8370

        private const val GL_TEXTURE0 = 0x84C0

        private const val GL_ARRAY_BUFFER = 0x8892
        private const val GL_ELEMENT_ARRAY_BUFFER = 0x8893
        private const val GL_PIXEL_PACK_BUFFER = 0x88EB
        private const val GL_PIXEL_UNPACK_BUFFER = 0x88EC
        private const val GL_UNIFORM_BUFFER = 0x8A11
        private const val GL_TRANSFORM_FEEDBACK_BUFFER = 0x8C8E
        private const val GL_COPY_READ_BUFFER = 0x8F36
        private const val GL_COPY_WRITE_BUFFER = 0x8F37

        private const val GL_STREAM_DRAW = 0x88E0
        private const val GL_STREAM_READ = 0x88E1
        private const val GL_STREAM_COPY = 0x88E2
        private const val GL_STATIC_DRAW = 0x88E4
        private const val GL_STATIC_READ = 0x88E5
        private const val GL_STATIC_COPY = 0x88E6
        private const val GL_DYNAMIC_DRAW = 0x88E8
        private const val GL_DYNAMIC_READ = 0x88E9
        private const val GL_DYNAMIC_COPY = 0x88EA

        private const val GL_FRAGMENT_SHADER = 0x8B30
        private const val GL_VERTEX_SHADER = 0x8B31
        private const val GL_COMPILE_STATUS = 0x8B81
        private const val GL_LINK_STATUS = 0x8B82

        private const val GL_READ_FRAMEBUFFER = 0x8CA8
        private const val GL_DRAW_FRAMEBUFFER = 0x8CA9
        private const val GL_FRAMEBUFFER = 0x8D40

        private const val GL_FRAMEBUFFER_COMPLETE = 0x8CD5
        private const val GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT = 0x8CD6
        private const val GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT = 0x8CD7
        private const val GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS = 0x8CD9
        private const val GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE = 0x8D56

        private const val GL_COLOR_ATTACHMENT0 = 0x8CE0
        private const val GL_COLOR_ATTACHMENT1 = 0x8CE1
        private const val GL_COLOR_ATTACHMENT2 = 0x8CE2
        private const val GL_COLOR_ATTACHMENT3 = 0x8CE3
        private const val GL_DEPTH_ATTACHMENT = 0x8D00
        private const val GL_STENCIL_ATTACHMENT = 0x8D20
        private const val GL_DEPTH_STENCIL_ATTACHMENT = 0x821A

        private fun toGlCapability(cap: GLCapability): Int = when (cap) {
            GLCapability.BLEND -> GL_BLEND
            GLCapability.DEPTH_TEST -> GL_DEPTH_TEST
            GLCapability.CULL_FACE -> GL_CULL_FACE
            GLCapability.SCISSOR_TEST -> GL_SCISSOR_TEST
            GLCapability.STENCIL_TEST -> GL_STENCIL_TEST
            GLCapability.POLYGON_OFFSET_FILL -> GL_POLYGON_OFFSET_FILL
            GLCapability.SAMPLE_ALPHA_TO_COVERAGE -> GL_SAMPLE_ALPHA_TO_COVERAGE
        }

        private fun toGlBlendFactor(factor: GLBlendFactor): Int = when (factor) {
            GLBlendFactor.ZERO -> GL_ZERO
            GLBlendFactor.ONE -> GL_ONE
            GLBlendFactor.SRC_COLOR -> GL_SRC_COLOR
            GLBlendFactor.ONE_MINUS_SRC_COLOR -> GL_ONE_MINUS_SRC_COLOR
            GLBlendFactor.SRC_ALPHA -> GL_SRC_ALPHA
            GLBlendFactor.ONE_MINUS_SRC_ALPHA -> GL_ONE_MINUS_SRC_ALPHA
            GLBlendFactor.DST_ALPHA -> GL_DST_ALPHA
            GLBlendFactor.ONE_MINUS_DST_ALPHA -> GL_ONE_MINUS_DST_ALPHA
            GLBlendFactor.DST_COLOR -> GL_DST_COLOR
            GLBlendFactor.ONE_MINUS_DST_COLOR -> GL_ONE_MINUS_DST_COLOR
            GLBlendFactor.CONSTANT_COLOR -> GL_CONSTANT_COLOR
            GLBlendFactor.ONE_MINUS_CONSTANT_COLOR -> GL_ONE_MINUS_CONSTANT_COLOR
            GLBlendFactor.CONSTANT_ALPHA -> GL_CONSTANT_ALPHA
            GLBlendFactor.ONE_MINUS_CONSTANT_ALPHA -> GL_ONE_MINUS_CONSTANT_ALPHA
            GLBlendFactor.SRC_ALPHA_SATURATE -> GL_SRC_ALPHA_SATURATE
        }

        private fun toGlBlendEquation(eq: GLBlendEquation): Int = when (eq) {
            GLBlendEquation.FUNC_ADD -> GL_FUNC_ADD
            GLBlendEquation.FUNC_SUBTRACT -> GL_FUNC_SUBTRACT
            GLBlendEquation.FUNC_REVERSE_SUBTRACT -> GL_FUNC_REVERSE_SUBTRACT
            GLBlendEquation.MIN -> GL_MIN
            GLBlendEquation.MAX -> GL_MAX
        }

        private fun toGlCompareFunc(func: GLCompareFunction): Int = when (func) {
            GLCompareFunction.NEVER -> GL_NEVER
            GLCompareFunction.LESS -> GL_LESS
            GLCompareFunction.EQUAL -> GL_EQUAL
            GLCompareFunction.LEQUAL -> GL_LEQUAL
            GLCompareFunction.GREATER -> GL_GREATER
            GLCompareFunction.NOTEQUAL -> GL_NOTEQUAL
            GLCompareFunction.GEQUAL -> GL_GEQUAL
            GLCompareFunction.ALWAYS -> GL_ALWAYS
        }

        private fun toGlFaceSide(side: GLFaceSide): Int = when (side) {
            GLFaceSide.FRONT -> GL_FRONT
            GLFaceSide.BACK -> GL_BACK
            GLFaceSide.FRONT_AND_BACK -> GL_FRONT_AND_BACK
        }

        private fun toGlWinding(winding: GLWinding): Int = when (winding) {
            GLWinding.CW -> GL_CW
            GLWinding.CCW -> GL_CCW
        }

        private fun toGlBufferTarget(target: GLBufferTarget): Int = when (target) {
            GLBufferTarget.ARRAY_BUFFER -> GL_ARRAY_BUFFER
            GLBufferTarget.ELEMENT_ARRAY_BUFFER -> GL_ELEMENT_ARRAY_BUFFER
            GLBufferTarget.UNIFORM_BUFFER -> GL_UNIFORM_BUFFER
            GLBufferTarget.TRANSFORM_FEEDBACK_BUFFER -> GL_TRANSFORM_FEEDBACK_BUFFER
            GLBufferTarget.COPY_READ_BUFFER -> GL_COPY_READ_BUFFER
            GLBufferTarget.COPY_WRITE_BUFFER -> GL_COPY_WRITE_BUFFER
            GLBufferTarget.PIXEL_PACK_BUFFER -> GL_PIXEL_PACK_BUFFER
            GLBufferTarget.PIXEL_UNPACK_BUFFER -> GL_PIXEL_UNPACK_BUFFER
        }

        private fun toGlBufferUsage(usage: GLBufferUsage): Int = when (usage) {
            GLBufferUsage.STATIC_DRAW -> GL_STATIC_DRAW
            GLBufferUsage.DYNAMIC_DRAW -> GL_DYNAMIC_DRAW
            GLBufferUsage.STREAM_DRAW -> GL_STREAM_DRAW
            GLBufferUsage.STATIC_READ -> GL_STATIC_READ
            GLBufferUsage.DYNAMIC_READ -> GL_DYNAMIC_READ
            GLBufferUsage.STREAM_READ -> GL_STREAM_READ
            GLBufferUsage.STATIC_COPY -> GL_STATIC_COPY
            GLBufferUsage.DYNAMIC_COPY -> GL_DYNAMIC_COPY
            GLBufferUsage.STREAM_COPY -> GL_STREAM_COPY
        }

        private fun toGlDataType(type: GLDataType): Int = when (type) {
            GLDataType.BYTE -> GL_BYTE
            GLDataType.UNSIGNED_BYTE -> GL_UNSIGNED_BYTE
            GLDataType.SHORT -> GL_SHORT
            GLDataType.UNSIGNED_SHORT -> GL_UNSIGNED_SHORT
            GLDataType.INT -> GL_INT
            GLDataType.UNSIGNED_INT -> GL_UNSIGNED_INT
            GLDataType.FLOAT -> GL_FLOAT
            GLDataType.HALF_FLOAT -> GL_HALF_FLOAT
        }

        private fun toGlTextureTarget(target: GLTextureTarget): Int = when (target) {
            GLTextureTarget.TEXTURE_2D -> GL_TEXTURE_2D
            GLTextureTarget.TEXTURE_CUBE_MAP -> GL_TEXTURE_CUBE_MAP
            GLTextureTarget.TEXTURE_2D_ARRAY -> GL_TEXTURE_2D_ARRAY
            GLTextureTarget.TEXTURE_3D -> GL_TEXTURE_3D
        }

        private fun toGlInternalFormat(format: GLTextureFormat): Int = when (format) {
            GLTextureFormat.R8 -> GL_R8
            GLTextureFormat.RG8 -> GL_RG8
            GLTextureFormat.RGB8 -> GL_RGB8
            GLTextureFormat.RGBA8 -> GL_RGBA8
            GLTextureFormat.RGBA16F -> GL_RGBA16F
            GLTextureFormat.RGB16F -> GL_RGB16F
            GLTextureFormat.DEPTH_COMPONENT24 -> GL_DEPTH_COMPONENT24
            GLTextureFormat.DEPTH24_STENCIL8 -> GL_DEPTH24_STENCIL8
        }

        private fun toGlBaseFormat(format: GLTextureFormat): Int = when (format) {
            GLTextureFormat.R8 -> GL_RED
            GLTextureFormat.RG8 -> GL_RG
            GLTextureFormat.RGB8 -> GL_RGB
            GLTextureFormat.RGBA8 -> GL_RGBA
            GLTextureFormat.RGBA16F -> GL_RGBA
            GLTextureFormat.RGB16F -> GL_RGB
            GLTextureFormat.DEPTH_COMPONENT24 -> GL_DEPTH_COMPONENT
            GLTextureFormat.DEPTH24_STENCIL8 -> GL_DEPTH_STENCIL
        }

        private fun toGlPixelType(
            format: GLTextureFormat,
            width: Int,
            height: Int,
            bytes: ByteArray?
        ): Int = when (format) {
            GLTextureFormat.R8,
            GLTextureFormat.RG8,
            GLTextureFormat.RGB8,
            GLTextureFormat.RGBA8 -> GL_UNSIGNED_BYTE
            GLTextureFormat.RGBA16F -> {
                if (bytes != null && bytes.size == width * height * 4) GL_UNSIGNED_BYTE else GL_HALF_FLOAT
            }
            GLTextureFormat.RGB16F -> {
                if (bytes != null && bytes.size == width * height * 3) GL_UNSIGNED_BYTE else GL_HALF_FLOAT
            }
            GLTextureFormat.DEPTH_COMPONENT24 -> GL_UNSIGNED_INT
            GLTextureFormat.DEPTH24_STENCIL8 -> GL_UNSIGNED_INT_24_8
        }

        private fun toGlTextureFilter(filter: GLTextureFilter): Int = when (filter) {
            GLTextureFilter.NEAREST -> GL_NEAREST
            GLTextureFilter.LINEAR -> GL_LINEAR
            GLTextureFilter.NEAREST_MIPMAP_NEAREST -> GL_NEAREST_MIPMAP_NEAREST
            GLTextureFilter.LINEAR_MIPMAP_NEAREST -> GL_LINEAR_MIPMAP_NEAREST
            GLTextureFilter.NEAREST_MIPMAP_LINEAR -> GL_NEAREST_MIPMAP_LINEAR
            GLTextureFilter.LINEAR_MIPMAP_LINEAR -> GL_LINEAR_MIPMAP_LINEAR
        }

        private fun toGlTextureWrap(wrap: GLTextureWrap): Int = when (wrap) {
            GLTextureWrap.REPEAT -> GL_REPEAT
            GLTextureWrap.CLAMP_TO_EDGE -> GL_CLAMP_TO_EDGE
            GLTextureWrap.MIRRORED_REPEAT -> GL_MIRRORED_REPEAT
        }

        private fun toGlFramebufferTarget(target: GLFramebufferTarget): Int = when (target) {
            GLFramebufferTarget.FRAMEBUFFER -> GL_FRAMEBUFFER
            GLFramebufferTarget.READ_FRAMEBUFFER -> GL_READ_FRAMEBUFFER
            GLFramebufferTarget.DRAW_FRAMEBUFFER -> GL_DRAW_FRAMEBUFFER
        }

        private fun toGlFramebufferAttachment(attachment: GLFramebufferAttachment): Int = when (attachment) {
            GLFramebufferAttachment.COLOR_ATTACHMENT0 -> GL_COLOR_ATTACHMENT0
            GLFramebufferAttachment.COLOR_ATTACHMENT1 -> GL_COLOR_ATTACHMENT1
            GLFramebufferAttachment.COLOR_ATTACHMENT2 -> GL_COLOR_ATTACHMENT2
            GLFramebufferAttachment.COLOR_ATTACHMENT3 -> GL_COLOR_ATTACHMENT3
            GLFramebufferAttachment.DEPTH_ATTACHMENT -> GL_DEPTH_ATTACHMENT
            GLFramebufferAttachment.STENCIL_ATTACHMENT -> GL_STENCIL_ATTACHMENT
            GLFramebufferAttachment.DEPTH_STENCIL_ATTACHMENT -> GL_DEPTH_STENCIL_ATTACHMENT
        }

        private fun toGlPrimitive(primitive: GLPrimitive): Int = when (primitive) {
            GLPrimitive.POINTS -> GL_POINTS
            GLPrimitive.LINES -> GL_LINES
            GLPrimitive.LINE_LOOP -> GL_LINE_LOOP
            GLPrimitive.LINE_STRIP -> GL_LINE_STRIP
            GLPrimitive.TRIANGLES -> GL_TRIANGLES
            GLPrimitive.TRIANGLE_STRIP -> GL_TRIANGLE_STRIP
            GLPrimitive.TRIANGLE_FAN -> GL_TRIANGLE_FAN
        }

        private fun toGlIndexType(type: GLIndexType): Int = when (type) {
            GLIndexType.UNSIGNED_BYTE -> GL_UNSIGNED_BYTE
            GLIndexType.UNSIGNED_SHORT -> GL_UNSIGNED_SHORT
            GLIndexType.UNSIGNED_INT -> GL_UNSIGNED_INT
        }
    }
}
