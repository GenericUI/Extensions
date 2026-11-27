package com.nexustools.genericui.ext.webview.desktop.njs

import com.nexustools.genericui.component.media.GLBlendEquation
import com.nexustools.genericui.component.media.GLBlendFactor
import com.nexustools.genericui.component.media.GLBuffer
import com.nexustools.genericui.component.media.GLBufferTarget
import com.nexustools.genericui.component.media.GLBufferUsage
import com.nexustools.genericui.component.media.GLCapability
import com.nexustools.genericui.component.media.GLClearBuffer
import com.nexustools.genericui.component.media.GLCompareFunction
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
import com.nexustools.genericui.component.media.GLTexture
import com.nexustools.genericui.component.media.GLTextureFormat
import com.nexustools.genericui.component.media.GLTextureTarget
import com.nexustools.genericui.component.media.GLUniformLocation
import com.nexustools.genericui.component.media.GLVertexAttribLayout
import com.nexustools.genericui.component.media.GLWinding
import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.ext.canvas3d.alien.AlienOffscreenGlContext
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomElement
import com.nexustools.genericui.ext.webview.desktop.engine.WebAlienDocumentEngine
import com.nexustools.njs.runtime.AbstractArray
import com.nexustools.njs.runtime.AbstractFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.Float32Array
import com.nexustools.njs.runtime.GenericObject
import com.nexustools.njs.runtime.Global
import com.nexustools.njs.runtime.Int16Array
import com.nexustools.njs.runtime.Int32Array
import com.nexustools.njs.runtime.JSError
import com.nexustools.njs.runtime.Null
import com.nexustools.njs.runtime.Scopable
import com.nexustools.njs.runtime.Uint16Array
import com.nexustools.njs.runtime.Uint32Array
import com.nexustools.njs.runtime.Uint8Array
import com.nexustools.njs.runtime.Uint8ClampedArray
import com.nexustools.njs.runtime.Undefined

/**
 * Full WebGL 1 & WebGL 2 `WebGLRenderingContext` / `WebGL2RenderingContext` NJS bridge backed
 * directly by GenericUI's [AlienOffscreenGlContext] inside `AlienRenderSession`.
 */
class NjsWebGlBridge(
    private val jsGlobal: Global,
    private val engine: WebAlienDocumentEngine,
    private val registry: NjsDomNodeRegistry
) {
    private class MutableShaderRef(var shader: GLShader)
    private class MutableProgramRef(var program: GLProgram, val attached: MutableList<MutableShaderRef> = ArrayList())
    private class RenderbufferRef(val id: Int, var format: Int = 0, var w: Int = 0, var h: Int = 0)

    private val contextCache = LinkedHashMap<WebDomElement, GenericObject>()

    fun installConstructors() {
        val webglCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                throw JSError.JavaException("TypeError", "Illegal constructor: WebGLRenderingContext")
            override fun construct(vararg params: BaseObject): BaseObject =
                throw JSError.JavaException("TypeError", "Illegal constructor: WebGLRenderingContext")
            override fun name(): String = "WebGLRenderingContext"
        }
        val webgl2Ctor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject =
                throw JSError.JavaException("TypeError", "Illegal constructor: WebGL2RenderingContext")
            override fun construct(vararg params: BaseObject): BaseObject =
                throw JSError.JavaException("TypeError", "Illegal constructor: WebGL2RenderingContext")
            override fun name(): String = "WebGL2RenderingContext"
        }
        jsGlobal.setHidden("WebGLRenderingContext", webglCtor)
        jsGlobal.setHidden("WebGL2RenderingContext", webgl2Ctor)
    }

    fun getOrCreateWebGlContext(
        canvasEl: WebDomElement,
        version: Int,
        attrs: BaseObject?
    ): BaseObject {
        contextCache[canvasEl]?.let { return it }
        val alpha = attrs?.get("alpha", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toBool() ?: true
        val depth = attrs?.get("depth", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toBool() ?: true
        val stencil = attrs?.get("stencil", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toBool() ?: false
        val gl = engine.ensureWebGLContext(canvasEl, version, alpha, depth, stencil) ?: return Null

        val js = GenericObject(jsGlobal)
        contextCache[canvasEl] = js

        val shaderMap = LinkedHashMap<BaseObject, MutableShaderRef>()
        val programMap = LinkedHashMap<BaseObject, MutableProgramRef>()
        val bufferMap = LinkedHashMap<BaseObject, GLBuffer>()
        val textureMap = LinkedHashMap<BaseObject, GLTexture>()
        val fboMap = LinkedHashMap<BaseObject, GLFramebuffer>()
        val rboMap = LinkedHashMap<BaseObject, RenderbufferRef>()
        val vaoMap = LinkedHashMap<BaseObject, Int>()
        val uniformMap = LinkedHashMap<BaseObject, GLUniformLocation>()
        val boundTex2DByUnit = LinkedHashMap<Int, BaseObject>()
        val texSizeMap = LinkedHashMap<BaseObject, Pair<Int, Int>>()
        val fboHeightMap = LinkedHashMap<BaseObject, Int>()
        var boundDrawFbo: BaseObject? = null
        var boundReadFbo: BaseObject? = null
        var activeTextureUnit = 0
        val enabledCaps = HashSet<Int>()
        var cullFaceCode = 0x0405
        var frontFaceCode = 0x0901
        var depthFuncCode = 0x0201
        var viewportArr = intArrayOf(0, 0, gl.surfaceWidth, gl.surfaceHeight)
        var scissorArr = intArrayOf(0, 0, gl.surfaceWidth, gl.surfaceHeight)
        var nextRboId = 1
        var flipYOnTexImage = false
        var lastError = 0

        fun markDirty() {
            canvasEl.alienNode?.invalidateBounds()
            engine.session.requestLayoutAndRepaint(canvasEl.alienNode?.bounds)
        }

        installGlConstants(js)
        js.defineProperty("canvas", fn("get_canvas") { _, _ -> registry.wrapNode(canvasEl) }, jsGlobal.NOOP)
        js.defineProperty("drawingBufferWidth", fn("get_dbw") { _, _ -> jsGlobal.wrap(gl.surfaceWidth.toDouble()) }, jsGlobal.NOOP)
        js.defineProperty("drawingBufferHeight", fn("get_dbh") { _, _ -> jsGlobal.wrap(gl.surfaceHeight.toDouble()) }, jsGlobal.NOOP)

        js.setHidden("getError", fn("getError") { _, _ ->
            val e = lastError
            lastError = 0
            jsGlobal.wrap(e.toDouble())
        })
        js.setHidden("getContextAttributes", fn("getContextAttributes") { _, _ ->
            GenericObject(jsGlobal).apply {
                set("alpha", jsGlobal.wrap(alpha))
                set("depth", jsGlobal.wrap(depth))
                set("stencil", jsGlobal.wrap(stencil))
            }
        })
        js.setHidden("getParameter", fn("getParameter") { _, p ->
            when (val pname = p.getOrNull(0)?.toInt() ?: 0) {
                0x1F00 -> jsGlobal.wrap(gl.contextInfo.vendor)
                0x1F01 -> jsGlobal.wrap(gl.contextInfo.renderer)
                0x1F02 -> jsGlobal.wrap(if (version >= 2) "WebGL 2.0 (Alien OpenGL ES 3.0)" else "WebGL 1.0 (Alien OpenGL ES 3.0)")
                0x8B8C -> jsGlobal.wrap("WebGL GLSL ES 3.00")
                0x0D33 -> jsGlobal.wrap(4096.0)
                0x0BE2, 0x0B71, 0x0B44, 0x0C11, 0x0B90 -> jsGlobal.wrap(pname in enabledCaps)
                0x0B45 -> jsGlobal.wrap(cullFaceCode.toDouble())
                0x0B46 -> jsGlobal.wrap(frontFaceCode.toDouble())
                0x0B74 -> jsGlobal.wrap(depthFuncCode.toDouble())
                0x0BA2 -> Int32Array.Instance(jsGlobal, 4).apply {
                    for (idx in 0 until 4) set(idx, jsGlobal.wrap(viewportArr[idx].toDouble()))
                }
                0x0C10 -> Int32Array.Instance(jsGlobal, 4).apply {
                    for (idx in 0 until 4) set(idx, jsGlobal.wrap(scissorArr[idx].toDouble()))
                }
                else -> jsGlobal.wrap(0.0)
            }
        })
        js.setHidden("isEnabled", fn("isEnabled") { _, p ->
            jsGlobal.wrap((p.getOrNull(0)?.toInt() ?: 0) in enabledCaps)
        })
        js.setHidden("getExtension", fn("getExtension") { _, _ -> GenericObject(jsGlobal) })
        js.setHidden("getSupportedExtensions", fn("getSupportedExtensions") { _, _ -> com.nexustools.njs.runtime.GenericArray(jsGlobal) })
        js.setHidden("isContextLost", fn("isContextLost") { _, _ -> jsGlobal.wrap(false) })

        // Viewport, Scissor, Clear
        js.setHidden("viewport", fn("viewport") { _, p ->
            val vx = i(p, 0); val vy = i(p, 1); val vw = i(p, 2); val vh = i(p, 3)
            viewportArr = intArrayOf(vx, vy, vw, vh)
            gl.viewport(vx, vy, vw, vh); Undefined
        })
        js.setHidden("scissor", fn("scissor") { _, p ->
            val sx = i(p, 0); val sy = i(p, 1); val sw = i(p, 2); val sh = i(p, 3)
            scissorArr = intArrayOf(sx, sy, sw, sh)
            gl.scissor(sx, sy, sw, sh); Undefined
        })
        js.setHidden("clearColor", fn("clearColor") { _, p ->
            gl.clearColor(f(p, 0), f(p, 1), f(p, 2), f(p, 3)); Undefined
        })
        js.setHidden("clearDepth", fn("clearDepth") { _, p -> gl.clearDepth(f(p, 0, 1f)); Undefined })
        js.setHidden("clearStencil", fn("clearStencil") { _, p -> gl.clearStencil(i(p, 0)); Undefined })
        js.setHidden("clear", fn("clear") { _, p ->
            val mask = i(p, 0)
            val set = LinkedHashSet<GLClearBuffer>()
            if ((mask and 0x4000) != 0) set.add(GLClearBuffer.COLOR)
            if ((mask and 0x0100) != 0) set.add(GLClearBuffer.DEPTH)
            if ((mask and 0x0400) != 0) set.add(GLClearBuffer.STENCIL)
            gl.clear(set)
            markDirty()
            Undefined
        })

        // Capabilities & Blend & Depth & Culling
        js.setHidden("enable", fn("enable") { _, p ->
            val code = i(p, 0)
            enabledCaps.add(code)
            mapCapability(code)?.let { gl.enable(it) }
            Undefined
        })
        js.setHidden("disable", fn("disable") { _, p ->
            val code = i(p, 0)
            enabledCaps.remove(code)
            mapCapability(code)?.let { gl.disable(it) }
            Undefined
        })
        js.setHidden("blendFunc", fn("blendFunc") { _, p ->
            gl.blendFunc(mapBlendFactor(i(p, 0)), mapBlendFactor(i(p, 1))); Undefined
        })
        js.setHidden("blendFuncSeparate", fn("blendFuncSeparate") { _, p ->
            gl.blendFuncSeparate(mapBlendFactor(i(p, 0)), mapBlendFactor(i(p, 1)), mapBlendFactor(i(p, 2)), mapBlendFactor(i(p, 3)))
            Undefined
        })
        js.setHidden("blendEquation", fn("blendEquation") { _, p ->
            val eq = when (i(p, 0)) {
                0x800A -> GLBlendEquation.FUNC_SUBTRACT
                0x800B -> GLBlendEquation.FUNC_REVERSE_SUBTRACT
                0x8007 -> GLBlendEquation.MIN
                0x8008 -> GLBlendEquation.MAX
                else -> GLBlendEquation.FUNC_ADD
            }
            gl.blendEquation(eq)
            Undefined
        })
        js.setHidden("blendColor", fn("blendColor") { _, p ->
            gl.blendColor(f(p, 0), f(p, 1), f(p, 2), f(p, 3)); Undefined
        })
        js.setHidden("depthFunc", fn("depthFunc") { _, p ->
            val code = i(p, 0)
            depthFuncCode = code
            val fnEnum = when (code) {
                0x0200 -> GLCompareFunction.NEVER
                0x0201 -> GLCompareFunction.LESS
                0x0202 -> GLCompareFunction.EQUAL
                0x0203 -> GLCompareFunction.LEQUAL
                0x0204 -> GLCompareFunction.GREATER
                0x0205 -> GLCompareFunction.NOTEQUAL
                0x0206 -> GLCompareFunction.GEQUAL
                else -> GLCompareFunction.ALWAYS
            }
            gl.depthFunc(fnEnum)
            Undefined
        })
        js.setHidden("depthMask", fn("depthMask") { _, p -> gl.depthMask(p.getOrNull(0)?.toBool() ?: true); Undefined })
        js.setHidden("cullFace", fn("cullFace") { _, p ->
            val code = i(p, 0)
            cullFaceCode = code
            val mode = when (code) {
                0x0404 -> GLFaceSide.FRONT
                0x0408 -> GLFaceSide.FRONT_AND_BACK
                else -> GLFaceSide.BACK
            }
            gl.cullFace(mode)
            Undefined
        })
        js.setHidden("frontFace", fn("frontFace") { _, p ->
            val code = i(p, 0)
            frontFaceCode = code
            gl.frontFace(if (code == 0x0900) GLWinding.CW else GLWinding.CCW); Undefined
        })
        js.setHidden("pixelStorei", fn("pixelStorei") { _, p ->
            if (i(p, 0) == 0x9240) flipYOnTexImage = i(p, 1) != 0
            Undefined
        })

        // Shaders & Programs
        js.setHidden("createShader", fn("createShader") { _, p ->
            val type = if (i(p, 0) == 0x8B31) ShaderType.VERTEX else ShaderType.FRAGMENT
            val sh = gl.createShader(type)
            GenericObject(jsGlobal).also { shaderMap[it] = MutableShaderRef(sh) }
        })
        js.setHidden("shaderSource", fn("shaderSource") { _, p ->
            val ref = p.getOrNull(0)?.let { shaderMap[it] } ?: return@fn Undefined
            gl.shaderSource(ref.shader, p.getOrNull(1)?.toString().orEmpty())
            Undefined
        })
        js.setHidden("compileShader", fn("compileShader") { _, p ->
            val ref = p.getOrNull(0)?.let { shaderMap[it] } ?: return@fn Undefined
            ref.shader = gl.compileShader(ref.shader)
            Undefined
        })
        js.setHidden("getShaderParameter", fn("getShaderParameter") { _, p ->
            val ref = p.getOrNull(0)?.let { shaderMap[it] } ?: return@fn Null
            when (i(p, 1)) {
                0x8B81 -> jsGlobal.wrap(ref.shader.compileStatus.success)
                0x8B4F -> jsGlobal.wrap(if (ref.shader.type == ShaderType.VERTEX) 0x8B31.toDouble() else 0x8B30.toDouble())
                else -> Null
            }
        })
        js.setHidden("getShaderInfoLog", fn("getShaderInfoLog") { _, p ->
            val ref = p.getOrNull(0)?.let { shaderMap[it] } ?: return@fn jsGlobal.wrap("")
            jsGlobal.wrap(ref.shader.compileStatus.infoLog)
        })
        js.setHidden("deleteShader", fn("deleteShader") { _, p ->
            val key = p.getOrNull(0) ?: return@fn Undefined
            shaderMap.remove(key)?.let { gl.deleteShader(it.shader) }
            Undefined
        })

        js.setHidden("createProgram", fn("createProgram") { _, _ ->
            val prog = gl.createProgram()
            GenericObject(jsGlobal).also { programMap[it] = MutableProgramRef(prog) }
        })
        js.setHidden("attachShader", fn("attachShader") { _, p ->
            val pRef = p.getOrNull(0)?.let { programMap[it] } ?: return@fn Undefined
            val sRef = p.getOrNull(1)?.let { shaderMap[it] } ?: return@fn Undefined
            pRef.attached.add(sRef)
            gl.attachShader(pRef.program, sRef.shader)
            Undefined
        })
        js.setHidden("linkProgram", fn("linkProgram") { _, p ->
            val pRef = p.getOrNull(0)?.let { programMap[it] } ?: return@fn Undefined
            pRef.program = gl.linkProgram(pRef.program)
            Undefined
        })
        js.setHidden("getProgramParameter", fn("getProgramParameter") { _, p ->
            val pRef = p.getOrNull(0)?.let { programMap[it] } ?: return@fn Null
            when (i(p, 1)) {
                0x8B82 -> jsGlobal.wrap(pRef.program.linkStatus.success)
                else -> Null
            }
        })
        js.setHidden("getProgramInfoLog", fn("getProgramInfoLog") { _, p ->
            val pRef = p.getOrNull(0)?.let { programMap[it] } ?: return@fn jsGlobal.wrap("")
            jsGlobal.wrap(pRef.program.linkStatus.infoLog)
        })
        js.setHidden("useProgram", fn("useProgram") { _, p ->
            val prog = p.getOrNull(0)?.takeIf { it !== Null && it !== Undefined }?.let { programMap[it]?.program }
            gl.useProgram(prog)
            Undefined
        })
        js.setHidden("deleteProgram", fn("deleteProgram") { _, p ->
            val key = p.getOrNull(0) ?: return@fn Undefined
            programMap.remove(key)?.let { gl.deleteProgram(it.program) }
            Undefined
        })

        // Attribs & Uniforms
        js.setHidden("getAttribLocation", fn("getAttribLocation") { _, p ->
            val pRef = p.getOrNull(0)?.let { programMap[it] } ?: return@fn jsGlobal.wrap(-1.0)
            jsGlobal.wrap(gl.getAttribLocation(pRef.program, p.getOrNull(1)?.toString().orEmpty()).toDouble())
        })
        js.setHidden("getUniformLocation", fn("getUniformLocation") { _, p ->
            val pRef = p.getOrNull(0)?.let { programMap[it] } ?: return@fn Null
            val loc = gl.getUniformLocation(pRef.program, p.getOrNull(1)?.toString().orEmpty()) ?: return@fn Null
            GenericObject(jsGlobal).also { uniformMap[it] = loc }
        })
        js.setHidden("uniform1i", fn("uniform1i") { _, p ->
            val loc = p.getOrNull(0)?.let { uniformMap[it] } ?: return@fn Undefined
            gl.uniform1i(loc, i(p, 1)); Undefined
        })
        js.setHidden("uniform1f", fn("uniform1f") { _, p ->
            val loc = p.getOrNull(0)?.let { uniformMap[it] } ?: return@fn Undefined
            gl.uniform1f(loc, f(p, 1)); Undefined
        })
        js.setHidden("uniform2f", fn("uniform2f") { _, p ->
            val loc = p.getOrNull(0)?.let { uniformMap[it] } ?: return@fn Undefined
            gl.uniform2f(loc, f(p, 1), f(p, 2)); Undefined
        })
        js.setHidden("uniform3f", fn("uniform3f") { _, p ->
            val loc = p.getOrNull(0)?.let { uniformMap[it] } ?: return@fn Undefined
            gl.uniform3f(loc, f(p, 1), f(p, 2), f(p, 3)); Undefined
        })
        js.setHidden("uniform4f", fn("uniform4f") { _, p ->
            val loc = p.getOrNull(0)?.let { uniformMap[it] } ?: return@fn Undefined
            gl.uniform4f(loc, f(p, 1), f(p, 2), f(p, 3), f(p, 4)); Undefined
        })
        js.setHidden("uniform2fv", fn("uniform2fv") { _, p ->
            val loc = p.getOrNull(0)?.let { uniformMap[it] } ?: return@fn Undefined
            val arr = extractFloatArray(p.getOrNull(1))
            if (arr.size >= 2) gl.uniform2f(loc, arr[0], arr[1]); Undefined
        })
        js.setHidden("uniform3fv", fn("uniform3fv") { _, p ->
            val loc = p.getOrNull(0)?.let { uniformMap[it] } ?: return@fn Undefined
            val arr = extractFloatArray(p.getOrNull(1))
            if (arr.size >= 3) gl.uniform3f(loc, arr[0], arr[1], arr[2]); Undefined
        })
        js.setHidden("uniform4fv", fn("uniform4fv") { _, p ->
            val loc = p.getOrNull(0)?.let { uniformMap[it] } ?: return@fn Undefined
            val arr = extractFloatArray(p.getOrNull(1))
            if (arr.size >= 4) gl.uniform4f(loc, arr[0], arr[1], arr[2], arr[3]); Undefined
        })
        js.setHidden("uniformMatrix3fv", fn("uniformMatrix3fv") { _, p ->
            val loc = p.getOrNull(0)?.let { uniformMap[it] } ?: return@fn Undefined
            val trans = p.getOrNull(1)?.toBool() ?: false
            gl.uniformMatrix3fv(loc, trans, extractFloatArray(p.getOrNull(2))); Undefined
        })
        js.setHidden("uniformMatrix4fv", fn("uniformMatrix4fv") { _, p ->
            val loc = p.getOrNull(0)?.let { uniformMap[it] } ?: return@fn Undefined
            val trans = p.getOrNull(1)?.toBool() ?: false
            gl.uniformMatrix4fv(loc, trans, extractFloatArray(p.getOrNull(2))); Undefined
        })

        // Buffers & VAO & Attributes
        js.setHidden("createBuffer", fn("createBuffer") { _, _ ->
            GenericObject(jsGlobal).also { bufferMap[it] = gl.createBuffer() }
        })
        js.setHidden("bindBuffer", fn("bindBuffer") { _, p ->
            val target = mapBufferTarget(i(p, 0))
            val buf = p.getOrNull(1)?.takeIf { it !== Null && it !== Undefined }?.let { bufferMap[it] }
            gl.bindBuffer(target, buf)
            Undefined
        })
        js.setHidden("bufferData", fn("bufferData") { _, p ->
            val target = mapBufferTarget(i(p, 0))
            val src = p.getOrNull(1)
            val usage = mapBufferUsage(i(p, 2, 0x88E4))
            uploadBufferData(gl, target, src, usage, byteOffset = null)
            Undefined
        })
        js.setHidden("bufferSubData", fn("bufferSubData") { _, p ->
            val target = mapBufferTarget(i(p, 0))
            val offset = i(p, 1)
            val src = p.getOrNull(2)
            uploadBufferData(gl, target, src, GLBufferUsage.DYNAMIC_DRAW, byteOffset = offset)
            Undefined
        })
        js.setHidden("deleteBuffer", fn("deleteBuffer") { _, p ->
            val key = p.getOrNull(0) ?: return@fn Undefined
            bufferMap.remove(key)?.let { gl.deleteBuffer(it) }
            Undefined
        })
        js.setHidden("createVertexArray", fn("createVertexArray") { _, _ ->
            GenericObject(jsGlobal).also { vaoMap[it] = gl.createVertexArray() }
        })
        js.setHidden("bindVertexArray", fn("bindVertexArray") { _, p ->
            val id = p.getOrNull(0)?.takeIf { it !== Null && it !== Undefined }?.let { vaoMap[it] }
            gl.bindVertexArray(id)
            Undefined
        })
        js.setHidden("deleteVertexArray", fn("deleteVertexArray") { _, p ->
            p.getOrNull(0)?.let { vaoMap.remove(it) }?.let { gl.deleteVertexArray(it) }
            Undefined
        })
        js.setHidden("enableVertexAttribArray", fn("enableVertexAttribArray") { _, p -> gl.enableVertexAttribArray(i(p, 0)); Undefined })
        js.setHidden("disableVertexAttribArray", fn("disableVertexAttribArray") { _, p -> gl.disableVertexAttribArray(i(p, 0)); Undefined })
        js.setHidden("vertexAttribPointer", fn("vertexAttribPointer") { _, p ->
            gl.vertexAttribPointer(
                GLVertexAttribLayout(
                    index = i(p, 0),
                    size = i(p, 1, 4),
                    type = mapDataType(i(p, 2, 0x1406)),
                    normalized = p.getOrNull(3)?.toBool() ?: false,
                    stride = i(p, 4, 0),
                    offset = i(p, 5, 0)
                )
            )
            Undefined
        })
        js.setHidden("vertexAttribDivisor", fn("vertexAttribDivisor") { _, p ->
            gl.vertexAttribDivisor(i(p, 0), i(p, 1)); Undefined
        })

        // Textures
        js.setHidden("createTexture", fn("createTexture") { _, _ ->
            GenericObject(jsGlobal).also { textureMap[it] = gl.createTexture() }
        })
        js.setHidden("bindTexture", fn("bindTexture") { _, p ->
            val targetCode = i(p, 0)
            val target = mapTexTarget(targetCode)
            val texJs = p.getOrNull(1)?.takeIf { it !== Null && it !== Undefined }
            val tex = texJs?.let { textureMap[it] }
            if (target == GLTextureTarget.TEXTURE_2D) {
                if (texJs != null) boundTex2DByUnit[activeTextureUnit] = texJs else boundTex2DByUnit.remove(activeTextureUnit)
            }
            gl.bindTexture(target, tex)
            Undefined
        })
        js.setHidden("activeTexture", fn("activeTexture") { _, p ->
            val unit = (i(p, 0) - 0x84C0).coerceAtLeast(0)
            activeTextureUnit = unit
            gl.activeTexture(unit)
            Undefined
        })
        js.setHidden("texParameteri", fn("texParameteri") { _, p ->
            gl.texParameteri(mapTexTarget(i(p, 0)), i(p, 1), i(p, 2))
            Undefined
        })
        js.setHidden("generateMipmap", fn("generateMipmap") { _, p ->
            gl.generateMipmap(mapTexTarget(i(p, 0))); Undefined
        })
        js.setHidden("texImage2D", fn("texImage2D") { _, p ->
            val target = mapTexTarget(i(p, 0))
            val level = i(p, 1)
            val internalFmt = mapTexFormat(i(p, 2))
            val shouldFlipForTopDownStore = !flipYOnTexImage
            if (p.size >= 9) {
                val w = i(p, 3).coerceAtLeast(1)
                val h = i(p, 4).coerceAtLeast(1)
                if (target == GLTextureTarget.TEXTURE_2D) {
                    boundTex2DByUnit[activeTextureUnit]?.let { texSizeMap[it] = w to h }
                }
                val pixels = p.getOrNull(8)
                val rawBytes = if (pixels == null || pixels === Null || pixels === Undefined) {
                    null
                } else {
                    val bytes = extractByteArray(pixels, w * h * 4)
                    if (shouldFlipForTopDownStore) flipRgbaRows(bytes, w, h) else bytes
                }
                gl.texImage2D(target, level, internalFmt, w, h, rawBytes)
            } else if (p.size >= 6) {
                // texImage2D(target, level, internalformat, format, type, sourceElement)
                val srcEl = registry.unwrapElement(p.getOrNull(5))
                val buf = srcEl?.let { engine.resolveCanvasBackingBuffer(it) }
                if (buf != null) {
                    if (target == GLTextureTarget.TEXTURE_2D) {
                        boundTex2DByUnit[activeTextureUnit]?.let { texSizeMap[it] = buf.width to buf.height }
                    }
                    val rgba = buf.toRgba8888().bytes
                    val oriented = if (shouldFlipForTopDownStore) flipRgbaRows(rgba.copyOf(), buf.width, buf.height) else rgba
                    gl.texImage2D(target, level, internalFmt, buf.width, buf.height, oriented)
                }
            }
            Undefined
        })
        js.setHidden("deleteTexture", fn("deleteTexture") { _, p ->
            p.getOrNull(0)?.let { jsTex ->
                texSizeMap.remove(jsTex)
                textureMap.remove(jsTex)
            }?.let { gl.deleteTexture(it) }
            Undefined
        })

        // Framebuffers & Renderbuffers
        js.setHidden("createFramebuffer", fn("createFramebuffer") { _, _ ->
            GenericObject(jsGlobal).also { fboMap[it] = gl.createFramebuffer() }
        })
        js.setHidden("bindFramebuffer", fn("bindFramebuffer") { _, p ->
            val fboTarget = mapFboTarget(i(p, 0))
            val fboJs = p.getOrNull(1)?.takeIf { it !== Null && it !== Undefined }
            val fbo = fboJs?.let { fboMap[it] }
            when (fboTarget) {
                GLFramebufferTarget.FRAMEBUFFER -> {
                    boundDrawFbo = fboJs
                    boundReadFbo = fboJs
                }
                GLFramebufferTarget.DRAW_FRAMEBUFFER -> boundDrawFbo = fboJs
                GLFramebufferTarget.READ_FRAMEBUFFER -> boundReadFbo = fboJs
            }
            gl.bindFramebuffer(fboTarget, fbo)
            Undefined
        })
        js.setHidden("framebufferTexture2D", fn("framebufferTexture2D") { _, p ->
            val texJs = p.getOrNull(3) ?: return@fn Undefined
            val tex = textureMap[texJs] ?: return@fn Undefined
            val fboTarget = mapFboTarget(i(p, 0))
            val att = when (i(p, 1)) {
                0x8D00 -> GLFramebufferAttachment.DEPTH_ATTACHMENT
                0x8D20 -> GLFramebufferAttachment.STENCIL_ATTACHMENT
                else -> GLFramebufferAttachment.COLOR_ATTACHMENT0
            }
            if (att == GLFramebufferAttachment.COLOR_ATTACHMENT0) {
                val targetFbo = if (fboTarget == GLFramebufferTarget.READ_FRAMEBUFFER) boundReadFbo else boundDrawFbo
                val texH = texSizeMap[texJs]?.second
                if (targetFbo != null && texH != null) {
                    fboHeightMap[targetFbo] = texH
                }
            }
            gl.framebufferTexture2D(fboTarget, att, mapTexTarget(i(p, 2)), tex, i(p, 4, 0))
            Undefined
        })
        js.setHidden("checkFramebufferStatus", fn("checkFramebufferStatus") { _, p ->
            val status = gl.checkFramebufferStatus(mapFboTarget(i(p, 0)))
            jsGlobal.wrap(if (status == GLFramebufferStatus.COMPLETE) 0x8CD5.toDouble() else 0x8CD6.toDouble())
        })
        js.setHidden("deleteFramebuffer", fn("deleteFramebuffer") { _, p ->
            p.getOrNull(0)?.let { fboMap.remove(it) }?.let { gl.deleteFramebuffer(it) }
            Undefined
        })
        js.setHidden("createRenderbuffer", fn("createRenderbuffer") { _, _ ->
            GenericObject(jsGlobal).also { rboMap[it] = RenderbufferRef(nextRboId++) }
        })
        js.setHidden("bindRenderbuffer", fn("bindRenderbuffer") { _, _ -> Undefined })
        js.setHidden("renderbufferStorage", fn("renderbufferStorage") { _, _ -> Undefined })
        js.setHidden("framebufferRenderbuffer", fn("framebufferRenderbuffer") { _, _ -> Undefined })

        // Draw & ReadPixels
        js.setHidden("drawArrays", fn("drawArrays") { _, p ->
            gl.drawArrays(mapPrimitive(i(p, 0)), i(p, 1), i(p, 2))
            markDirty()
            Undefined
        })
        js.setHidden("drawElements", fn("drawElements") { _, p ->
            gl.drawElements(mapPrimitive(i(p, 0)), i(p, 1), mapIndexType(i(p, 2)), i(p, 3))
            markDirty()
            Undefined
        })
        js.setHidden("drawArraysInstanced", fn("drawArraysInstanced") { _, p ->
            gl.drawArraysInstanced(mapPrimitive(i(p, 0)), i(p, 1), i(p, 2), i(p, 3))
            markDirty()
            Undefined
        })
        js.setHidden("drawElementsInstanced", fn("drawElementsInstanced") { _, p ->
            gl.drawElementsInstanced(mapPrimitive(i(p, 0)), i(p, 1), mapIndexType(i(p, 2)), i(p, 3), i(p, 4))
            markDirty()
            Undefined
        })
        js.setHidden("readPixels", fn("readPixels") { _, p ->
            val x = i(p, 0)
            val y = i(p, 1)
            val w = i(p, 2).coerceAtLeast(1)
            val h = i(p, 3).coerceAtLeast(1)
            val dest = p.getOrNull(6)
            val targetH = (boundReadFbo?.let { fboHeightMap[it] } ?: gl.surfaceHeight).coerceAtLeast(1)
            val topDownY = (targetH - y - h).coerceIn(0, (targetH - 1).coerceAtLeast(0))
            val bytes = gl.readPixels(x, topDownY, w, h, GLReadFormat.RGBA8)
            if (h > 1) flipRgbaRows(bytes, w, h)
            if (dest != null && dest !== Null && dest !== Undefined) {
                for (k in 0 until minOf(bytes.size, dest.get("length").toInt())) {
                    dest.set(k, jsGlobal.wrap((bytes[k].toInt() and 0xFF).toDouble()))
                }
            }
            Undefined
        })
        js.setHidden("flush", fn("flush") { _, _ -> gl.flush(); markDirty(); Undefined })
        js.setHidden("finish", fn("finish") { _, _ -> gl.finish(); markDirty(); Undefined })

        return js
    }

    private fun uploadBufferData(
        gl: AlienOffscreenGlContext,
        target: GLBufferTarget,
        src: BaseObject?,
        usage: GLBufferUsage,
        byteOffset: Int?
    ) {
        if (src == null || src === Null || src === Undefined) return
        when (src) {
            is Float32Array.Instance -> {
                val arr = FloatArray(src.length()) { src.get(it).toDouble().toFloat() }
                if (byteOffset != null) gl.bufferSubData(target, byteOffset, arr) else gl.bufferData(target, arr, usage)
            }
            is Uint16Array.Instance, is Int16Array.Instance -> {
                val len = src.get("length").toInt()
                val arr = ShortArray(len) { src.get(it).toInt().toShort() }
                if (byteOffset != null) gl.bufferSubData(target, byteOffset, arr) else gl.bufferData(target, arr, usage)
            }
            is Uint32Array.Instance, is Int32Array.Instance -> {
                val len = src.get("length").toInt()
                val arr = IntArray(len) { src.get(it).toInt() }
                if (byteOffset != null) gl.bufferSubData(target, byteOffset, arr) else gl.bufferData(target, arr, usage)
            }
            else -> {
                val len = src.get("length").toInt().coerceAtLeast(0)
                if (src.get("BYTES_PER_ELEMENT", Scopable.OR_NULL)?.toInt() == 4) {
                    val arr = FloatArray(len) { src.get(it).toDouble().toFloat() }
                    if (byteOffset != null) gl.bufferSubData(target, byteOffset, arr) else gl.bufferData(target, arr, usage)
                } else {
                    val arr = ByteArray(len) { src.get(it).toInt().toByte() }
                    if (byteOffset != null) gl.bufferSubData(target, byteOffset, arr) else gl.bufferData(target, arr, usage)
                }
            }
        }
    }

    private fun extractFloatArray(src: BaseObject?): FloatArray {
        if (src == null || src === Null || src === Undefined) return FloatArray(0)
        val len = src.get("length").toInt().coerceAtLeast(0)
        return FloatArray(len) { src.get(it).toDouble().toFloat() }
    }

    private fun extractByteArray(src: BaseObject, expectedMin: Int): ByteArray {
        val len = src.get("length").toInt().coerceAtLeast(0)
        val out = ByteArray(maxOf(expectedMin, len))
        for (i in 0 until len) out[i] = src.get(i).toInt().toByte()
        return out
    }

    private fun flipRgbaRows(bytes: ByteArray, w: Int, h: Int): ByteArray {
        val rowBytes = w * 4
        val temp = ByteArray(rowBytes)
        for (y in 0 until h / 2) {
            val topOff = y * rowBytes
            val botOff = (h - 1 - y) * rowBytes
            bytes.copyInto(temp, 0, topOff, topOff + rowBytes)
            bytes.copyInto(bytes, topOff, botOff, botOff + rowBytes)
            temp.copyInto(bytes, botOff, 0, rowBytes)
        }
        return bytes
    }

    private fun mapCapability(code: Int): GLCapability? = when (code) {
        0x0BE2 -> GLCapability.BLEND
        0x0B71 -> GLCapability.DEPTH_TEST
        0x0B44 -> GLCapability.CULL_FACE
        0x0C11 -> GLCapability.SCISSOR_TEST
        0x0B90 -> GLCapability.STENCIL_TEST
        else -> null
    }

    private fun mapBlendFactor(code: Int): GLBlendFactor = when (code) {
        0 -> GLBlendFactor.ZERO
        1 -> GLBlendFactor.ONE
        0x0300 -> GLBlendFactor.SRC_COLOR
        0x0301 -> GLBlendFactor.ONE_MINUS_SRC_COLOR
        0x0302 -> GLBlendFactor.SRC_ALPHA
        0x0303 -> GLBlendFactor.ONE_MINUS_SRC_ALPHA
        0x0304 -> GLBlendFactor.DST_ALPHA
        0x0305 -> GLBlendFactor.ONE_MINUS_DST_ALPHA
        0x0306 -> GLBlendFactor.DST_COLOR
        0x0307 -> GLBlendFactor.ONE_MINUS_DST_COLOR
        else -> GLBlendFactor.ONE
    }

    private fun mapBufferTarget(code: Int): GLBufferTarget = when (code) {
        0x8893 -> GLBufferTarget.ELEMENT_ARRAY_BUFFER
        0x8A11 -> GLBufferTarget.UNIFORM_BUFFER
        else -> GLBufferTarget.ARRAY_BUFFER
    }

    private fun mapBufferUsage(code: Int): GLBufferUsage = when (code) {
        0x88E8 -> GLBufferUsage.DYNAMIC_DRAW
        0x88E0 -> GLBufferUsage.STREAM_DRAW
        else -> GLBufferUsage.STATIC_DRAW
    }

    private fun mapDataType(code: Int): GLDataType = when (code) {
        0x1400 -> GLDataType.BYTE
        0x1401 -> GLDataType.UNSIGNED_BYTE
        0x1402 -> GLDataType.SHORT
        0x1403 -> GLDataType.UNSIGNED_SHORT
        0x1404 -> GLDataType.INT
        0x1405 -> GLDataType.UNSIGNED_INT
        else -> GLDataType.FLOAT
    }

    private fun mapTexTarget(code: Int): GLTextureTarget = when (code) {
        0x8513 -> GLTextureTarget.TEXTURE_CUBE_MAP
        else -> GLTextureTarget.TEXTURE_2D
    }

    private fun mapTexFormat(code: Int): GLTextureFormat = when (code) {
        0x1907 -> GLTextureFormat.RGB8
        else -> GLTextureFormat.RGBA8
    }

    private fun mapFboTarget(code: Int): GLFramebufferTarget = when (code) {
        0x8CA8 -> GLFramebufferTarget.READ_FRAMEBUFFER
        0x8CA9 -> GLFramebufferTarget.DRAW_FRAMEBUFFER
        else -> GLFramebufferTarget.FRAMEBUFFER
    }

    private fun mapPrimitive(code: Int): GLPrimitive = when (code) {
        0 -> GLPrimitive.POINTS
        1 -> GLPrimitive.LINES
        2 -> GLPrimitive.LINE_LOOP
        3 -> GLPrimitive.LINE_STRIP
        5 -> GLPrimitive.TRIANGLE_STRIP
        6 -> GLPrimitive.TRIANGLE_FAN
        else -> GLPrimitive.TRIANGLES
    }

    private fun mapIndexType(code: Int): GLIndexType = when (code) {
        0x1401 -> GLIndexType.UNSIGNED_BYTE
        0x1405 -> GLIndexType.UNSIGNED_INT
        else -> GLIndexType.UNSIGNED_SHORT
    }

    private fun installGlConstants(js: GenericObject) {
        val constants = mapOf(
            "DEPTH_BUFFER_BIT" to 0x0100, "STENCIL_BUFFER_BIT" to 0x0400, "COLOR_BUFFER_BIT" to 0x4000,
            "POINTS" to 0, "LINES" to 1, "LINE_LOOP" to 2, "LINE_STRIP" to 3,
            "TRIANGLES" to 4, "TRIANGLE_STRIP" to 5, "TRIANGLE_FAN" to 6,
            "ZERO" to 0, "ONE" to 1, "SRC_COLOR" to 0x0300, "ONE_MINUS_SRC_COLOR" to 0x0301,
            "SRC_ALPHA" to 0x0302, "ONE_MINUS_SRC_ALPHA" to 0x0303, "DST_ALPHA" to 0x0304,
            "ONE_MINUS_DST_ALPHA" to 0x0305, "DST_COLOR" to 0x0306, "ONE_MINUS_DST_COLOR" to 0x0307,
            "FUNC_ADD" to 0x8006, "FUNC_SUBTRACT" to 0x800A, "FUNC_REVERSE_SUBTRACT" to 0x800B,
            "BLEND" to 0x0BE2, "DEPTH_TEST" to 0x0B71, "CULL_FACE" to 0x0B44, "SCISSOR_TEST" to 0x0C11,
            "STENCIL_TEST" to 0x0B90, "FRONT" to 0x0404, "BACK" to 0x0405, "FRONT_AND_BACK" to 0x0408,
            "CW" to 0x0900, "CCW" to 0x0901, "NEVER" to 0x0200, "LESS" to 0x0201, "EQUAL" to 0x0202,
            "LEQUAL" to 0x0203, "GREATER" to 0x0204, "NOTEQUAL" to 0x0205, "GEQUAL" to 0x0206, "ALWAYS" to 0x0207,
            "ARRAY_BUFFER" to 0x8892, "ELEMENT_ARRAY_BUFFER" to 0x8893,
            "STATIC_DRAW" to 0x88E4, "DYNAMIC_DRAW" to 0x88E8, "STREAM_DRAW" to 0x88E0,
            "BYTE" to 0x1400, "UNSIGNED_BYTE" to 0x1401, "SHORT" to 0x1402, "UNSIGNED_SHORT" to 0x1403,
            "INT" to 0x1404, "UNSIGNED_INT" to 0x1405, "FLOAT" to 0x1406,
            "RGB" to 0x1907, "RGBA" to 0x1908, "RGBA8" to 0x8058,
            "VERTEX_SHADER" to 0x8B31, "FRAGMENT_SHADER" to 0x8B30,
            "COMPILE_STATUS" to 0x8B81, "LINK_STATUS" to 0x8B82,
            "TEXTURE_2D" to 0x0DE1, "TEXTURE_CUBE_MAP" to 0x8513,
            "TEXTURE_MAG_FILTER" to 0x2800, "TEXTURE_MIN_FILTER" to 0x2801,
            "TEXTURE_WRAP_S" to 0x2802, "TEXTURE_WRAP_T" to 0x2803,
            "NEAREST" to 0x2600, "LINEAR" to 0x2601, "NEAREST_MIPMAP_NEAREST" to 0x2700,
            "LINEAR_MIPMAP_NEAREST" to 0x2701, "NEAREST_MIPMAP_LINEAR" to 0x2702, "LINEAR_MIPMAP_LINEAR" to 0x2703,
            "REPEAT" to 0x2901, "CLAMP_TO_EDGE" to 0x812F, "MIRRORED_REPEAT" to 0x8370,
            "TEXTURE0" to 0x84C0, "TEXTURE1" to 0x84C1, "TEXTURE2" to 0x84C2, "TEXTURE3" to 0x84C3,
            "UNPACK_FLIP_Y_WEBGL" to 0x9240,
            "FRAMEBUFFER" to 0x8D40, "RENDERBUFFER" to 0x8D41,
            "COLOR_ATTACHMENT0" to 0x8CE0, "DEPTH_ATTACHMENT" to 0x8D00, "STENCIL_ATTACHMENT" to 0x8D20,
            "DEPTH_COMPONENT16" to 0x81A5, "FRAMEBUFFER_COMPLETE" to 0x8CD5,
            "VENDOR" to 0x1F00, "RENDERER" to 0x1F01, "VERSION" to 0x1F02,
            "SHADING_LANGUAGE_VERSION" to 0x8B8C, "MAX_TEXTURE_SIZE" to 0x0D33, "NO_ERROR" to 0
        )
        for ((k, v) in constants) {
            js.setHidden(k, jsGlobal.wrap(v.toDouble()))
        }
    }

    private fun i(p: Array<out BaseObject>, idx: Int, defaultVal: Int = 0): Int =
        p.getOrNull(idx)?.takeIf { it !== Undefined && it !== Null }?.toInt() ?: defaultVal

    private fun f(p: Array<out BaseObject>, idx: Int, defaultVal: Float = 0f): Float =
        p.getOrNull(idx)?.takeIf { it !== Undefined && it !== Null }?.toDouble()?.toFloat() ?: defaultVal

    private fun fn(name: String, body: (BaseObject, Array<out BaseObject>) -> BaseObject): AbstractFunction =
        object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = body(_this, params)
            override fun name(): String = name
        }
}
