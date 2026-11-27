package com.nexustools.genericui.ext.canvas3d.alien

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
import com.nexustools.genericui.component.media.GLContext
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
import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Offscreen WebGL 1 / WebGL 2 / OpenGL ES 3.0+ rendering context for `:extensions:graphics:canvas3d:alien`.
 *
 * Operates in two modes seamlessly:
 * - **Hardware Offscreen FBO + Readback** when a genuine hardware [hardwareDelegate] [GLContext]
 *   is supplied: maintains an offscreen FBO (`COLOR_ATTACHMENT0` + optional depth/stencil attachment)
 *   for the canvas default target, routes all WebGL/GL calls to hardware, and reads back RGBA bytes
 *   on [readDefaultFramebufferRgba].
 * - **Headless Software / Framebuffer Pipeline** when [hardwareDelegate] is `null`: provides a
 *   complete offscreen 3D rasterizer supporting clear, viewport, scissor, depth testing (`depthFunc`,
 *   `depthMask`), stencil, face culling (`cullFace`, `frontFace`), blending (`blendFunc`,
 *   `blendFuncSeparate`, `blendEquation`, `blendColor`), VBOs (`bufferData`, `bufferSubData`),
 *   VAOs (`createVertexArray`, `bindVertexArray`), textures & sampling, render-to-texture FBOs
 *   (`framebufferTexture2D`, `checkFramebufferStatus`), instanced & indexed draws across all
 *   [GLPrimitive] modes, and an AST-compiled WebGL 1 / WebGL 2 GLSL vertex+fragment evaluator.
 */
class AlienOffscreenGlContext(
    val config: GLContextAttributes = GLContextAttributes(),
    initialWidth: Int = 300,
    initialHeight: Int = 150,
    private val hardwareDelegate: GLContext? = null,
    override val contextInfo: GLContextInfo = GLContextInfo(
        backend = GLBackendKind.WEBGL2,
        version = config.version,
        shadingLanguage = when (config.version) {
            GLContextVersion.ES_3_0 -> GLShadingLanguage.GLSL_ES_300
            GLContextVersion.ES_3_1 -> GLShadingLanguage.GLSL_ES_310
            GLContextVersion.ES_3_2 -> GLShadingLanguage.GLSL_ES_320
        },
        acceleration = hardwareDelegate?.acceleration ?: GpuAcceleration.SOFTWARE,
        renderer = if (hardwareDelegate != null) {
            "Alien Offscreen FBO (${hardwareDelegate.contextInfo.renderer})"
        } else {
            "Alien Offscreen Software WebGL2/GLES3"
        },
        vendor = "GenericUI Alien"
    )
) : GLContext {

    var surfaceWidth: Int = initialWidth.coerceAtLeast(1)
        private set

    var surfaceHeight: Int = initialHeight.coerceAtLeast(1)
        private set

    var defaultFramebufferVersion: Long = 0L
        private set

    private var clearR: Float = 0f
    private var clearG: Float = 0f
    private var clearB: Float = 0f
    private var clearA: Float = 0f
    private var clearDepthValue: Float = 1f
    private var clearStencilValue: Int = 0

    private var viewportX: Int = 0
    private var viewportY: Int = 0
    private var viewportW: Int = surfaceWidth
    private var viewportH: Int = surfaceHeight

    private var scissorX: Int = 0
    private var scissorY: Int = 0
    private var scissorW: Int = surfaceWidth
    private var scissorH: Int = surfaceHeight

    private val enabledCapabilities = mutableSetOf<GLCapability>()
    private var blendSrcRGB: GLBlendFactor = GLBlendFactor.ONE
    private var blendDstRGB: GLBlendFactor = GLBlendFactor.ZERO
    private var blendSrcAlpha: GLBlendFactor = GLBlendFactor.ONE
    private var blendDstAlpha: GLBlendFactor = GLBlendFactor.ZERO
    private var blendEquationMode: GLBlendEquation = GLBlendEquation.FUNC_ADD
    private var blendConstantR: Float = 0f
    private var blendConstantG: Float = 0f
    private var blendConstantB: Float = 0f
    private var blendConstantA: Float = 0f

    private var depthFuncMode: GLCompareFunction = GLCompareFunction.LESS
    private var depthWriteMask: Boolean = true
    private var cullFaceMode: GLFaceSide = GLFaceSide.BACK
    private var frontFaceMode: GLWinding = GLWinding.CCW

    private var defaultColorBuffer: ByteArray = ByteArray(surfaceWidth * surfaceHeight * 4)
    private var defaultDepthBuffer: FloatArray = FloatArray(surfaceWidth * surfaceHeight) { 1f }
    private var defaultStencilBuffer: IntArray = IntArray(surfaceWidth * surfaceHeight)

    private var nextResourceId: Int = 1
    private val shaders = mutableMapOf<Int, ShaderRecord>()
    private val programs = mutableMapOf<Int, ProgramRecord>()
    private val buffers = mutableMapOf<Int, BufferRecord>()
    private val textures = mutableMapOf<Int, TextureRecord>()
    private val framebuffers = mutableMapOf<Int, FramebufferRecord>()
    private val vaos = mutableMapOf<Int, VaoRecord>().apply {
        put(0, VaoRecord(0))
    }

    private var activeProgramId: Int? = null
    private var activeVaoId: Int = 0
    private var activeTextureUnit: Int = 0
    private val boundBuffers = mutableMapOf<GLBufferTarget, Int?>()
    private val boundUniformBuffers = mutableMapOf<Int, Int?>()
    private val boundTexturesByUnit = mutableMapOf<Int, MutableMap<GLTextureTarget, Int?>>()
    private var boundDrawFramebufferId: Int? = null
    private var boundReadFramebufferId: Int? = null

    // Hardware FBO delegation state
    private var hwDrawFramebuffer: GLFramebuffer? = null
    private var hwReadFramebuffer: GLFramebuffer? = null
    private var hwDefaultFbo: GLFramebuffer? = null
    private var hwDefaultColorTex: GLTexture? = null
    private var hwDefaultDepthTex: GLTexture? = null
    private var hwDefaultFboW: Int = -1
    private var hwDefaultFboH: Int = -1
    private val hwAttribDivisors = mutableMapOf<Int, Int>()

    private fun ensureHardwareDefaultFbo(): GLFramebuffer {
        val hw = hardwareDelegate
            ?: throw IllegalStateException("ensureHardwareDefaultFbo requires a non-null hardwareDelegate")
        if (hwDefaultFbo != null && hwDefaultFboW == surfaceWidth && hwDefaultFboH == surfaceHeight) {
            return hwDefaultFbo!!
        }
        hwDefaultColorTex?.let { hw.deleteTexture(it) }
        hwDefaultDepthTex?.let { hw.deleteTexture(it) }
        hwDefaultFbo?.let { hw.deleteFramebuffer(it) }

        val colorTex = hw.createTexture()
        hw.bindTexture(GLTextureTarget.TEXTURE_2D, colorTex)
        hw.texImage2D(GLTextureTarget.TEXTURE_2D, 0, GLTextureFormat.RGBA8, surfaceWidth, surfaceHeight, null)
        hw.texParameters(
            GLTextureTarget.TEXTURE_2D,
            GLTextureParameters(
                minFilter = GLTextureFilter.NEAREST,
                magFilter = GLTextureFilter.NEAREST,
                wrapS = GLTextureWrap.CLAMP_TO_EDGE,
                wrapT = GLTextureWrap.CLAMP_TO_EDGE
            )
        )

        val fbo = hw.createFramebuffer()
        hw.bindFramebuffer(GLFramebufferTarget.FRAMEBUFFER, fbo)
        hw.framebufferTexture2D(
            GLFramebufferTarget.FRAMEBUFFER,
            GLFramebufferAttachment.COLOR_ATTACHMENT0,
            GLTextureTarget.TEXTURE_2D,
            colorTex,
            0
        )

        var depthTex: GLTexture? = null
        if (config.depth || config.stencil) {
            val dt = hw.createTexture()
            hw.bindTexture(GLTextureTarget.TEXTURE_2D, dt)
            val depthFmt = if (config.stencil) GLTextureFormat.DEPTH24_STENCIL8 else GLTextureFormat.DEPTH_COMPONENT24
            val attach = if (config.stencil) {
                GLFramebufferAttachment.DEPTH_STENCIL_ATTACHMENT
            } else {
                GLFramebufferAttachment.DEPTH_ATTACHMENT
            }
            hw.texImage2D(GLTextureTarget.TEXTURE_2D, 0, depthFmt, surfaceWidth, surfaceHeight, null)
            hw.framebufferTexture2D(GLFramebufferTarget.FRAMEBUFFER, attach, GLTextureTarget.TEXTURE_2D, dt, 0)
            if (hw.checkFramebufferStatus(GLFramebufferTarget.FRAMEBUFFER) == GLFramebufferStatus.COMPLETE) {
                depthTex = dt
            } else {
                hw.deleteTexture(dt)
                hw.framebufferTexture2D(
                    GLFramebufferTarget.FRAMEBUFFER,
                    GLFramebufferAttachment.COLOR_ATTACHMENT0,
                    GLTextureTarget.TEXTURE_2D,
                    colorTex,
                    0
                )
            }
        }

        hwDefaultFbo = fbo
        hwDefaultColorTex = colorTex
        hwDefaultDepthTex = depthTex
        hwDefaultFboW = surfaceWidth
        hwDefaultFboH = surfaceHeight

        hw.bindFramebuffer(GLFramebufferTarget.DRAW_FRAMEBUFFER, hwDrawFramebuffer ?: fbo)
        hw.bindFramebuffer(GLFramebufferTarget.READ_FRAMEBUFFER, hwReadFramebuffer ?: fbo)
        return fbo
    }

    private fun markTargetMutated() {
        if (boundDrawFramebufferId == null && hwDrawFramebuffer == null) {
            defaultFramebufferVersion++
        }
    }

    fun resizeSurface(width: Int, height: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (w == surfaceWidth && h == surfaceHeight) return
        val wasFullViewport = viewportX == 0 && viewportY == 0 && viewportW == surfaceWidth && viewportH == surfaceHeight
        val wasFullScissor = scissorX == 0 && scissorY == 0 && scissorW == surfaceWidth && scissorH == surfaceHeight
        surfaceWidth = w
        surfaceHeight = h
        if (wasFullViewport) {
            viewportW = w
            viewportH = h
        }
        if (wasFullScissor) {
            scissorW = w
            scissorH = h
        }
        if (hardwareDelegate != null) {
            hwDefaultFboW = -1
            hwDefaultFboH = -1
            if (hwDrawFramebuffer == null) {
                ensureHardwareDefaultFbo()
                hardwareDelegate.viewport(viewportX, viewportY, viewportW, viewportH)
            }
        } else {
            defaultColorBuffer = ByteArray(w * h * 4)
            defaultDepthBuffer = FloatArray(w * h) { clearDepthValue }
            defaultStencilBuffer = IntArray(w * h) { clearStencilValue }
        }
        defaultFramebufferVersion++
    }

    fun readDefaultFramebufferRgba(): ByteArray {
        val hw = hardwareDelegate
        if (hw != null) {
            val prevRead = hwReadFramebuffer
            val fbo = ensureHardwareDefaultFbo()
            hw.bindFramebuffer(GLFramebufferTarget.READ_FRAMEBUFFER, fbo)
            hw.finish()
            val pixels = hw.readPixels(0, 0, surfaceWidth, surfaceHeight, GLReadFormat.RGBA8)
            hw.bindFramebuffer(GLFramebufferTarget.READ_FRAMEBUFFER, prevRead ?: fbo)
            return pixels
        }
        return defaultColorBuffer.copyOf()
    }

    override fun viewport(x: Int, y: Int, width: Int, height: Int) {
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("gl.viewport dimensions must be non-negative: $width x $height")
        }
        viewportX = x
        viewportY = y
        viewportW = width
        viewportH = height
        hardwareDelegate?.viewport(x, y, width, height)
    }

    override fun scissor(x: Int, y: Int, width: Int, height: Int) {
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("gl.scissor dimensions must be non-negative: $width x $height")
        }
        scissorX = x
        scissorY = y
        scissorW = width
        scissorH = height
        hardwareDelegate?.scissor(x, y, width, height)
    }

    override fun clearColor(r: Float, g: Float, b: Float, a: Float) {
        clearR = r.coerceIn(0f, 1f)
        clearG = g.coerceIn(0f, 1f)
        clearB = b.coerceIn(0f, 1f)
        clearA = a.coerceIn(0f, 1f)
        hardwareDelegate?.clearColor(clearR, clearG, clearB, clearA)
    }

    override fun clearDepth(depth: Float) {
        clearDepthValue = depth.coerceIn(0f, 1f)
        hardwareDelegate?.clearDepth(clearDepthValue)
    }

    override fun clearStencil(stencil: Int) {
        clearStencilValue = stencil
        hardwareDelegate?.clearStencil(stencil)
    }

    override fun clear(buffers: Set<GLClearBuffer>) {
        if (buffers.isEmpty()) return
        val hw = hardwareDelegate
        if (hw != null) {
            if (hwDrawFramebuffer == null) {
                val fbo = ensureHardwareDefaultFbo()
                hw.bindFramebuffer(GLFramebufferTarget.DRAW_FRAMEBUFFER, fbo)
            }
            hw.clear(buffers)
            markTargetMutated()
            return
        }

        val target = resolveDrawTarget() ?: return
        val rByte = (clearR * 255f).roundToInt().coerceIn(0, 255).toByte()
        val gByte = (clearG * 255f).roundToInt().coerceIn(0, 255).toByte()
        val bByte = (clearB * 255f).roundToInt().coerceIn(0, 255).toByte()
        val aByte = (clearA * 255f).roundToInt().coerceIn(0, 255).toByte()

        val useScissor = GLCapability.SCISSOR_TEST in enabledCapabilities
        val startX = if (useScissor) scissorX.coerceIn(0, target.width) else 0
        val endX = if (useScissor) (scissorX + scissorW).coerceIn(startX, target.width) else target.width
        val startY = if (useScissor) resolveScreenScissorStartY(target.height) else 0
        val endY = if (useScissor) resolveScreenScissorEndY(target.height, startY) else target.height

        if (GLClearBuffer.COLOR in buffers && target.colorPixels != null) {
            val px = target.colorPixels
            if (startX == 0 && endX == target.width && startY == 0 && endY == target.height) {
                var i = 0
                val limit = target.width * target.height * 4
                while (i < limit) {
                    px[i] = rByte
                    px[i + 1] = gByte
                    px[i + 2] = bByte
                    px[i + 3] = aByte
                    i += 4
                }
            } else {
                for (row in startY until endY) {
                    var i = (row * target.width + startX) * 4
                    for (col in startX until endX) {
                        px[i] = rByte
                        px[i + 1] = gByte
                        px[i + 2] = bByte
                        px[i + 3] = aByte
                        i += 4
                    }
                }
            }
        }

        if (GLClearBuffer.DEPTH in buffers && target.depthBuffer != null) {
            val db = target.depthBuffer
            if (startX == 0 && endX == target.width && startY == 0 && endY == target.height) {
                db.fill(clearDepthValue)
            } else {
                for (row in startY until endY) {
                    val base = row * target.width
                    db.fill(clearDepthValue, base + startX, base + endX)
                }
            }
            target.syncDepthTextureFromBuffer()
        }

        if (GLClearBuffer.STENCIL in buffers && target.stencilBuffer != null) {
            val sb = target.stencilBuffer
            if (startX == 0 && endX == target.width && startY == 0 && endY == target.height) {
                sb.fill(clearStencilValue)
            } else {
                for (row in startY until endY) {
                    val base = row * target.width
                    sb.fill(clearStencilValue, base + startX, base + endX)
                }
            }
        }

        markTargetMutated()
    }

    override fun enable(capability: GLCapability) {
        enabledCapabilities.add(capability)
        hardwareDelegate?.enable(capability)
    }

    override fun disable(capability: GLCapability) {
        enabledCapabilities.remove(capability)
        hardwareDelegate?.disable(capability)
    }

    override fun blendFunc(sfactor: GLBlendFactor, dfactor: GLBlendFactor) {
        blendSrcRGB = sfactor
        blendDstRGB = dfactor
        blendSrcAlpha = sfactor
        blendDstAlpha = dfactor
        hardwareDelegate?.blendFunc(sfactor, dfactor)
    }

    override fun blendFuncSeparate(
        srcRGB: GLBlendFactor,
        dstRGB: GLBlendFactor,
        srcAlpha: GLBlendFactor,
        dstAlpha: GLBlendFactor
    ) {
        blendSrcRGB = srcRGB
        blendDstRGB = dstRGB
        blendSrcAlpha = srcAlpha
        blendDstAlpha = dstAlpha
        hardwareDelegate?.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha)
    }

    override fun blendEquation(mode: GLBlendEquation) {
        blendEquationMode = mode
        hardwareDelegate?.blendEquation(mode)
    }

    fun blendColor(r: Float, g: Float, b: Float, a: Float) {
        blendConstantR = r.coerceIn(0f, 1f)
        blendConstantG = g.coerceIn(0f, 1f)
        blendConstantB = b.coerceIn(0f, 1f)
        blendConstantA = a.coerceIn(0f, 1f)
    }

    override fun depthFunc(func: GLCompareFunction) {
        depthFuncMode = func
        hardwareDelegate?.depthFunc(func)
    }

    override fun depthMask(flag: Boolean) {
        depthWriteMask = flag
        hardwareDelegate?.depthMask(flag)
    }

    override fun cullFace(mode: GLFaceSide) {
        cullFaceMode = mode
        hardwareDelegate?.cullFace(mode)
    }

    override fun frontFace(mode: GLWinding) {
        frontFaceMode = mode
        hardwareDelegate?.frontFace(mode)
    }

    // -------------------------------------------------------------------------
    // Shaders & Programs (GLContext + WebGL separate create/shaderSource/compile/attach/link)
    // -------------------------------------------------------------------------

    fun createShader(type: ShaderType): GLShader {
        val rec = ShaderRecord(id = nextResourceId++, type = type, source = "")
        shaders[rec.id] = rec
        return GLShader(rec.id, type, GLCompileStatus(success = false, infoLog = ""))
    }

    fun shaderSource(shader: GLShader, source: String) {
        val rec = shaders[shader.id]
            ?: throw IllegalArgumentException("Unknown shader id ${shader.id}")
        rec.source = source
        rec.ast = null
        rec.compiled = false
    }

    fun compileShader(shader: GLShader): GLShader {
        val rec = shaders[shader.id]
            ?: throw IllegalArgumentException("Unknown shader id ${shader.id}")
        val hw = hardwareDelegate
        if (hw != null) {
            val hwShader = hw.createShader(rec.type, rec.source)
            rec.hardwareShader = hwShader
            rec.compiled = hwShader.compileStatus.success
            rec.compileStatus = hwShader.compileStatus
            return GLShader(shader.id, rec.type, hwShader.compileStatus)
        }
        val parsed = compileAstOrError(rec.type, rec.source)
        rec.ast = parsed.first
        rec.compileStatus = parsed.second
        rec.compiled = parsed.second.success
        return GLShader(shader.id, rec.type, rec.compileStatus)
    }

    override fun createShader(type: ShaderType, source: String): GLShader {
        val hw = hardwareDelegate
        if (hw != null) {
            val hwShader = hw.createShader(type, source)
            val rec = ShaderRecord(
                id = nextResourceId++,
                type = type,
                source = source,
                compiled = hwShader.compileStatus.success,
                compileStatus = hwShader.compileStatus,
                hardwareShader = hwShader
            )
            shaders[rec.id] = rec
            return GLShader(rec.id, type, hwShader.compileStatus)
        }
        val parsed = compileAstOrError(type, source)
        val rec = ShaderRecord(
            id = nextResourceId++,
            type = type,
            source = source,
            ast = parsed.first,
            compiled = parsed.second.success,
            compileStatus = parsed.second
        )
        shaders[rec.id] = rec
        return GLShader(rec.id, type, parsed.second)
    }

    override fun deleteShader(shader: GLShader) {
        val rec = shaders.remove(shader.id)
        rec?.hardwareShader?.let { hardwareDelegate?.deleteShader(it) }
    }

    fun createProgram(): GLProgram {
        val rec = ProgramRecord(id = nextResourceId++)
        programs[rec.id] = rec
        return GLProgram(rec.id, GLCompileStatus(success = false, infoLog = ""))
    }

    fun attachShader(program: GLProgram, shader: GLShader) {
        val prog = programs[program.id]
            ?: throw IllegalArgumentException("Unknown program id ${program.id}")
        val sh = shaders[shader.id]
            ?: throw IllegalArgumentException("Unknown shader id ${shader.id}")
        when (sh.type) {
            ShaderType.VERTEX -> prog.vertexShaderId = sh.id
            ShaderType.FRAGMENT -> prog.fragmentShaderId = sh.id
            ShaderType.COMPUTE -> throw IllegalArgumentException("Compute shader cannot be attached to graphics program")
        }
    }

    fun linkProgram(program: GLProgram): GLProgram {
        val prog = programs[program.id]
            ?: throw IllegalArgumentException("Unknown program id ${program.id}")
        val vs = prog.vertexShaderId?.let { shaders[it] }
        val fs = prog.fragmentShaderId?.let { shaders[it] }
        if (vs == null || fs == null) {
            val status = GLCompileStatus(false, "Program requires both vertex and fragment shaders")
            prog.linkStatus = status
            return GLProgram(prog.id, status)
        }
        if (!vs.compiled || !fs.compiled) {
            val status = GLCompileStatus(
                false,
                "Uncompiled shader(s): vs='${vs.compileStatus.infoLog}' fs='${fs.compileStatus.infoLog}'"
            )
            prog.linkStatus = status
            return GLProgram(prog.id, status)
        }
        val hw = hardwareDelegate
        if (hw != null) {
            val hwVs = vs.hardwareShader
                ?: hw.createShader(ShaderType.VERTEX, vs.source).also { vs.hardwareShader = it }
            val hwFs = fs.hardwareShader
                ?: hw.createShader(ShaderType.FRAGMENT, fs.source).also { fs.hardwareShader = it }
            val hwProg = hw.createProgram(hwVs, hwFs)
            prog.hardwareProgram = hwProg
            prog.linkStatus = hwProg.linkStatus
            return GLProgram(prog.id, hwProg.linkStatus)
        }
        populateProgramMetadata(prog, vs, fs)
        prog.linkStatus = GLCompileStatus(true)
        return GLProgram(prog.id, prog.linkStatus)
    }

    override fun createProgram(vertexShader: GLShader, fragmentShader: GLShader): GLProgram {
        val prog = createProgram()
        attachShader(prog, vertexShader)
        attachShader(prog, fragmentShader)
        return linkProgram(prog)
    }

    override fun useProgram(program: GLProgram?) {
        val hw = hardwareDelegate
        if (hw != null) {
            val hwProg = program?.let { programs[it.id]?.hardwareProgram }
            activeProgramId = program?.id
            hw.useProgram(hwProg)
            return
        }
        activeProgramId = program?.id?.takeIf { programs.containsKey(it) }
    }

    override fun deleteProgram(program: GLProgram) {
        val rec = programs.remove(program.id)
        rec?.hardwareProgram?.let { hardwareDelegate?.deleteProgram(it) }
        if (activeProgramId == program.id) {
            activeProgramId = null
        }
    }

    override fun getAttribLocation(program: GLProgram, name: String): Int {
        val rec = programs[program.id] ?: return -1
        val hw = hardwareDelegate
        if (hw != null) {
            val hwProg = rec.hardwareProgram ?: return -1
            return hw.getAttribLocation(hwProg, name)
        }
        return rec.attribLocations[name] ?: -1
    }

    override fun getUniformLocation(program: GLProgram, name: String): GLUniformLocation? {
        val rec = programs[program.id] ?: return null
        val hw = hardwareDelegate
        if (hw != null) {
            val hwProg = rec.hardwareProgram ?: return null
            val hwLoc = hw.getUniformLocation(hwProg, name) ?: return null
            val wrapperId = nextResourceId++
            rec.hwUniformByWrapperId[wrapperId] = hwLoc
            return GLUniformLocation(wrapperId, name)
        }
        if (name !in rec.declaredUniformNames) return null
        return rec.uniformLocations.getOrPut(name) {
            GLUniformLocation(nextResourceId++, name)
        }
    }

    override fun getUniformBlockIndex(program: GLProgram, blockName: String): Int {
        val rec = programs[program.id] ?: return -1
        val hw = hardwareDelegate
        if (hw != null) {
            val hwProg = rec.hardwareProgram ?: return -1
            return hw.getUniformBlockIndex(hwProg, blockName)
        }
        return rec.uniformBlockNames.indexOf(blockName)
    }

    override fun uniformBlockBinding(program: GLProgram, uniformBlockIndex: Int, uniformBlockBinding: Int) {
        val rec = programs[program.id] ?: return
        val hw = hardwareDelegate
        if (hw != null) {
            rec.hardwareProgram?.let { hw.uniformBlockBinding(it, uniformBlockIndex, uniformBlockBinding) }
            return
        }
        rec.uniformBlockBindings[uniformBlockIndex] = uniformBlockBinding
    }

    override fun uniform1i(location: GLUniformLocation, v0: Int) {
        val prog = activeProgram() ?: return
        val hw = hardwareDelegate
        if (hw != null) {
            prog.hwUniformByWrapperId[location.id]?.let { hw.uniform1i(it, v0) }
            return
        }
        prog.intUniforms[location.name] = v0
        prog.floatUniforms[location.name] = floatArrayOf(v0.toFloat())
    }

    override fun uniform1f(location: GLUniformLocation, v0: Float) {
        val prog = activeProgram() ?: return
        val hw = hardwareDelegate
        if (hw != null) {
            prog.hwUniformByWrapperId[location.id]?.let { hw.uniform1f(it, v0) }
            return
        }
        prog.floatUniforms[location.name] = floatArrayOf(v0)
    }

    override fun uniform2f(location: GLUniformLocation, v0: Float, v1: Float) {
        val prog = activeProgram() ?: return
        val hw = hardwareDelegate
        if (hw != null) {
            prog.hwUniformByWrapperId[location.id]?.let { hw.uniform2f(it, v0, v1) }
            return
        }
        prog.floatUniforms[location.name] = floatArrayOf(v0, v1)
    }

    override fun uniform3f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float) {
        val prog = activeProgram() ?: return
        val hw = hardwareDelegate
        if (hw != null) {
            prog.hwUniformByWrapperId[location.id]?.let { hw.uniform3f(it, v0, v1, v2) }
            return
        }
        prog.floatUniforms[location.name] = floatArrayOf(v0, v1, v2)
    }

    override fun uniform4f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float, v3: Float) {
        val prog = activeProgram() ?: return
        val hw = hardwareDelegate
        if (hw != null) {
            prog.hwUniformByWrapperId[location.id]?.let { hw.uniform4f(it, v0, v1, v2, v3) }
            return
        }
        prog.floatUniforms[location.name] = floatArrayOf(v0, v1, v2, v3)
    }

    override fun uniformMatrix3fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray) {
        if (values.size < 9) {
            throw IllegalArgumentException("uniformMatrix3fv requires at least 9 floats, got ${values.size}")
        }
        val prog = activeProgram() ?: return
        val hw = hardwareDelegate
        if (hw != null) {
            prog.hwUniformByWrapperId[location.id]?.let { hw.uniformMatrix3fv(it, transpose, values) }
            return
        }
        val stored = FloatArray(9)
        if (transpose) {
            for (c in 0 until 3) {
                for (r in 0 until 3) {
                    stored[c * 3 + r] = values[r * 3 + c]
                }
            }
        } else {
            values.copyInto(stored, 0, 0, 9)
        }
        prog.floatUniforms[location.name] = stored
    }

    override fun uniformMatrix4fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray) {
        if (values.size < 16) {
            throw IllegalArgumentException("uniformMatrix4fv requires at least 16 floats, got ${values.size}")
        }
        val prog = activeProgram() ?: return
        val hw = hardwareDelegate
        if (hw != null) {
            prog.hwUniformByWrapperId[location.id]?.let { hw.uniformMatrix4fv(it, transpose, values) }
            return
        }
        val stored = FloatArray(16)
        if (transpose) {
            for (c in 0 until 4) {
                for (r in 0 until 4) {
                    stored[c * 4 + r] = values[r * 4 + c]
                }
            }
        } else {
            values.copyInto(stored, 0, 0, 16)
        }
        prog.floatUniforms[location.name] = stored
    }

    // -------------------------------------------------------------------------
    // Buffers & Vertex Array Objects
    // -------------------------------------------------------------------------

    override fun createBuffer(): GLBuffer {
        hardwareDelegate?.let { return it.createBuffer() }
        val buf = BufferRecord(nextResourceId++)
        buffers[buf.id] = buf
        return GLBuffer(buf.id)
    }

    override fun bindBuffer(target: GLBufferTarget, buffer: GLBuffer?) {
        hardwareDelegate?.let {
            it.bindBuffer(target, buffer)
            return
        }
        val id = buffer?.id?.takeIf { it != 0 }
        boundBuffers[target] = id
        if (target == GLBufferTarget.ELEMENT_ARRAY_BUFFER) {
            currentVao().elementArrayBufferId = id
        }
    }

    override fun bindBufferBase(target: GLBufferTarget, index: Int, buffer: GLBuffer?) {
        hardwareDelegate?.let {
            it.bindBufferBase(target, index, buffer)
            return
        }
        if (target == GLBufferTarget.UNIFORM_BUFFER) {
            boundUniformBuffers[index] = buffer?.id?.takeIf { it != 0 }
        }
    }

    override fun bufferData(target: GLBufferTarget, data: FloatArray, usage: GLBufferUsage) {
        hardwareDelegate?.let {
            it.bufferData(target, data, usage)
            return
        }
        val rec = requireBoundBuffer(target)
        val out = ByteArray(data.size * 4)
        for (i in data.indices) {
            writeInt32LE(out, i * 4, data[i].toRawBits())
        }
        rec.bytes = out
    }

    override fun bufferData(target: GLBufferTarget, data: IntArray, usage: GLBufferUsage) {
        hardwareDelegate?.let {
            it.bufferData(target, data, usage)
            return
        }
        val rec = requireBoundBuffer(target)
        val out = ByteArray(data.size * 4)
        for (i in data.indices) {
            writeInt32LE(out, i * 4, data[i])
        }
        rec.bytes = out
    }

    override fun bufferData(target: GLBufferTarget, data: ShortArray, usage: GLBufferUsage) {
        hardwareDelegate?.let {
            it.bufferData(target, data, usage)
            return
        }
        val rec = requireBoundBuffer(target)
        val out = ByteArray(data.size * 2)
        for (i in data.indices) {
            writeInt16LE(out, i * 2, data[i].toInt())
        }
        rec.bytes = out
    }

    override fun bufferData(target: GLBufferTarget, data: ByteArray, usage: GLBufferUsage) {
        hardwareDelegate?.let {
            it.bufferData(target, data, usage)
            return
        }
        val rec = requireBoundBuffer(target)
        rec.bytes = data.copyOf()
    }

    fun bufferSubData(target: GLBufferTarget, dstByteOffset: Int, data: FloatArray) {
        val bytes = ByteArray(data.size * 4)
        for (i in data.indices) {
            writeInt32LE(bytes, i * 4, data[i].toRawBits())
        }
        bufferSubData(target, dstByteOffset, bytes)
    }

    fun bufferSubData(target: GLBufferTarget, dstByteOffset: Int, data: IntArray) {
        val bytes = ByteArray(data.size * 4)
        for (i in data.indices) {
            writeInt32LE(bytes, i * 4, data[i])
        }
        bufferSubData(target, dstByteOffset, bytes)
    }

    fun bufferSubData(target: GLBufferTarget, dstByteOffset: Int, data: ShortArray) {
        val bytes = ByteArray(data.size * 2)
        for (i in data.indices) {
            writeInt16LE(bytes, i * 2, data[i].toInt())
        }
        bufferSubData(target, dstByteOffset, bytes)
    }

    fun bufferSubData(target: GLBufferTarget, dstByteOffset: Int, data: ByteArray) {
        if (hardwareDelegate != null) {
            throw UnsupportedOperationException(
                "bufferSubData is not exposed on hardware GLContext interface; upload via bufferData instead"
            )
        }
        val rec = requireBoundBuffer(target)
        if (dstByteOffset < 0 || dstByteOffset + data.size > rec.bytes.size) {
            throw IllegalArgumentException(
                "bufferSubData out of bounds: offset=$dstByteOffset size=${data.size} bufferBytes=${rec.bytes.size}"
            )
        }
        data.copyInto(rec.bytes, dstByteOffset)
    }

    override fun deleteBuffer(buffer: GLBuffer) {
        hardwareDelegate?.let {
            it.deleteBuffer(buffer)
            return
        }
        buffers.remove(buffer.id)
        for ((target, id) in boundBuffers) {
            if (id == buffer.id) boundBuffers[target] = null
        }
        for (vao in vaos.values) {
            if (vao.elementArrayBufferId == buffer.id) {
                vao.elementArrayBufferId = null
            }
        }
    }

    fun createVertexArray(): Int {
        val id = nextResourceId++
        vaos[id] = VaoRecord(id)
        return id
    }

    fun bindVertexArray(vaoId: Int?) {
        val resolved = vaoId?.takeIf { it != 0 } ?: 0
        if (resolved !in vaos) {
            throw IllegalArgumentException("Unknown VAO id $resolved")
        }
        activeVaoId = resolved
        if (hardwareDelegate == null) {
            boundBuffers[GLBufferTarget.ELEMENT_ARRAY_BUFFER] = vaos[resolved]?.elementArrayBufferId
        }
    }

    fun deleteVertexArray(vaoId: Int) {
        if (vaoId == 0) return
        vaos.remove(vaoId)
        if (activeVaoId == vaoId) {
            bindVertexArray(0)
        }
    }

    override fun enableVertexAttribArray(index: Int) {
        hardwareDelegate?.enableVertexAttribArray(index)
        currentVao().enabledAttribs.add(index)
    }

    override fun disableVertexAttribArray(index: Int) {
        hardwareDelegate?.disableVertexAttribArray(index)
        currentVao().enabledAttribs.remove(index)
    }

    override fun vertexAttribPointer(layout: GLVertexAttribLayout) {
        hardwareDelegate?.let {
            it.vertexAttribPointer(layout)
            return
        }
        val bufferId = boundBuffers[GLBufferTarget.ARRAY_BUFFER]
            ?: throw IllegalStateException("vertexAttribPointer requires an ARRAY_BUFFER to be bound")
        currentVao().attribBindings[layout.index] = AttribBinding(bufferId = bufferId, layout = layout)
    }

    fun vertexAttribDivisor(index: Int, divisor: Int) {
        val safeDiv = divisor.coerceAtLeast(0)
        hwAttribDivisors[index] = safeDiv
        currentVao().attribDivisors[index] = safeDiv
    }

    // -------------------------------------------------------------------------
    // Textures & Framebuffers
    // -------------------------------------------------------------------------

    override fun createTexture(): GLTexture {
        hardwareDelegate?.let { return it.createTexture() }
        val tex = TextureRecord(id = nextResourceId++)
        textures[tex.id] = tex
        return GLTexture(tex.id, 0, 0)
    }

    override fun bindTexture(target: GLTextureTarget, texture: GLTexture?) {
        hardwareDelegate?.let {
            it.bindTexture(target, texture)
            return
        }
        val map = boundTexturesByUnit.getOrPut(activeTextureUnit) { mutableMapOf() }
        map[target] = texture?.id?.takeIf { it != 0 }
    }

    override fun activeTexture(unit: Int) {
        val normalized = if (unit >= GL_TEXTURE0) unit - GL_TEXTURE0 else unit
        activeTextureUnit = normalized.coerceAtLeast(0)
        hardwareDelegate?.activeTexture(normalized.coerceAtLeast(0))
    }

    override fun texImage2D(
        target: GLTextureTarget,
        level: Int,
        format: GLTextureFormat,
        width: Int,
        height: Int,
        bytes: ByteArray?
    ) {
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("texImage2D dimensions must be non-negative: $width x $height")
        }
        hardwareDelegate?.let {
            it.texImage2D(target, level, format, width, height, bytes)
            return
        }
        if (level != 0) {
            // Mipmap levels beyond level 0 are accepted after base allocation
            return
        }
        val texId = boundTexturesByUnit[activeTextureUnit]?.get(target)
            ?: throw IllegalStateException("texImage2D requires a texture bound to $target")
        val rec = textures[texId]
            ?: throw IllegalStateException("Bound texture $texId does not exist")
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        rec.width = safeW
        rec.height = safeH
        rec.format = format

        val rgba = ByteArray(safeW * safeH * 4)
        var depthVals: FloatArray? = null
        if (format == GLTextureFormat.DEPTH_COMPONENT24 || format == GLTextureFormat.DEPTH24_STENCIL8) {
            depthVals = FloatArray(safeW * safeH) { 1f }
            rgba.fill((-1).toByte())
        }
        if (bytes != null) {
            when (format) {
                GLTextureFormat.RGB8 -> {
                    var s = 0
                    var d = 0
                    while (s + 2 < bytes.size && d + 3 < rgba.size) {
                        rgba[d] = bytes[s]
                        rgba[d + 1] = bytes[s + 1]
                        rgba[d + 2] = bytes[s + 2]
                        rgba[d + 3] = -1
                        s += 3
                        d += 4
                    }
                }
                GLTextureFormat.R8 -> {
                    val count = min(bytes.size, safeW * safeH)
                    for (p in 0 until count) {
                        val d = p * 4
                        rgba[d] = bytes[p]
                        rgba[d + 1] = 0
                        rgba[d + 2] = 0
                        rgba[d + 3] = -1
                    }
                }
                GLTextureFormat.RG8 -> {
                    var s = 0
                    var d = 0
                    while (s + 1 < bytes.size && d + 3 < rgba.size) {
                        rgba[d] = bytes[s]
                        rgba[d + 1] = bytes[s + 1]
                        rgba[d + 2] = 0
                        rgba[d + 3] = -1
                        s += 2
                        d += 4
                    }
                }
                else -> {
                    bytes.copyInto(rgba, 0, 0, min(bytes.size, rgba.size))
                }
            }
        }
        rec.rgbaPixels = rgba
        rec.depthValues = depthVals
    }

    override fun texParameters(target: GLTextureTarget, params: GLTextureParameters) {
        hardwareDelegate?.let {
            it.texParameters(target, params)
            return
        }
        val texId = boundTexturesByUnit[activeTextureUnit]?.get(target) ?: return
        textures[texId]?.params = params
    }

    fun texParameteri(target: GLTextureTarget, pname: Int, param: Int) {
        val texId = boundTexturesByUnit[activeTextureUnit]?.get(target) ?: return
        val tex = textures[texId] ?: return
        val cur = tex.params
        val updated = when (pname) {
            GL_TEXTURE_MIN_FILTER -> cur.copy(minFilter = decodeGlFilter(param))
            GL_TEXTURE_MAG_FILTER -> cur.copy(magFilter = decodeGlFilter(param))
            GL_TEXTURE_WRAP_S -> cur.copy(wrapS = decodeGlWrap(param))
            GL_TEXTURE_WRAP_T -> cur.copy(wrapT = decodeGlWrap(param))
            else -> throw IllegalArgumentException("Unsupported texParameteri pname=0x${pname.toString(16)}")
        }
        tex.params = updated
        hardwareDelegate?.texParameters(target, updated)
    }

    override fun generateMipmap(target: GLTextureTarget) {
        hardwareDelegate?.generateMipmap(target)
    }

    override fun deleteTexture(texture: GLTexture) {
        hardwareDelegate?.let {
            it.deleteTexture(texture)
            return
        }
        textures.remove(texture.id)
        for (unitMap in boundTexturesByUnit.values) {
            for ((target, id) in unitMap) {
                if (id == texture.id) unitMap[target] = null
            }
        }
    }

    override fun createFramebuffer(): GLFramebuffer {
        hardwareDelegate?.let { return it.createFramebuffer() }
        val rec = FramebufferRecord(nextResourceId++)
        framebuffers[rec.id] = rec
        return GLFramebuffer(rec.id)
    }

    override fun bindFramebuffer(target: GLFramebufferTarget, framebuffer: GLFramebuffer?) {
        val hw = hardwareDelegate
        if (hw != null) {
            val resolvedHw = framebuffer ?: ensureHardwareDefaultFbo()
            when (target) {
                GLFramebufferTarget.FRAMEBUFFER -> {
                    hwDrawFramebuffer = framebuffer
                    hwReadFramebuffer = framebuffer
                }
                GLFramebufferTarget.DRAW_FRAMEBUFFER -> hwDrawFramebuffer = framebuffer
                GLFramebufferTarget.READ_FRAMEBUFFER -> hwReadFramebuffer = framebuffer
            }
            hw.bindFramebuffer(target, resolvedHw)
            return
        }
        val id = framebuffer?.id?.takeIf { it != 0 }
        if (id != null && id !in framebuffers) {
            framebuffers[id] = FramebufferRecord(id)
        }
        when (target) {
            GLFramebufferTarget.FRAMEBUFFER -> {
                boundDrawFramebufferId = id
                boundReadFramebufferId = id
            }
            GLFramebufferTarget.DRAW_FRAMEBUFFER -> boundDrawFramebufferId = id
            GLFramebufferTarget.READ_FRAMEBUFFER -> boundReadFramebufferId = id
        }
    }

    override fun framebufferTexture2D(
        target: GLFramebufferTarget,
        attachment: GLFramebufferAttachment,
        textarget: GLTextureTarget,
        texture: GLTexture,
        level: Int
    ) {
        val hw = hardwareDelegate
        if (hw != null) {
            hw.framebufferTexture2D(target, attachment, textarget, texture, level)
            return
        }
        val fboId = when (target) {
            GLFramebufferTarget.READ_FRAMEBUFFER -> boundReadFramebufferId
            else -> boundDrawFramebufferId
        } ?: throw IllegalStateException("Cannot call framebufferTexture2D with default framebuffer bound")
        val rec = framebuffers.getOrPut(fboId) { FramebufferRecord(fboId) }
        val texId = texture.id.takeIf { it != 0 }
        when (attachment) {
            GLFramebufferAttachment.COLOR_ATTACHMENT0 -> setFboColorSlot(rec, 0, texId)
            GLFramebufferAttachment.COLOR_ATTACHMENT1 -> setFboColorSlot(rec, 1, texId)
            GLFramebufferAttachment.COLOR_ATTACHMENT2 -> setFboColorSlot(rec, 2, texId)
            GLFramebufferAttachment.COLOR_ATTACHMENT3 -> setFboColorSlot(rec, 3, texId)
            GLFramebufferAttachment.DEPTH_ATTACHMENT -> {
                rec.depthTextureId = texId
                rec.internalDepthBuffer = null
            }
            GLFramebufferAttachment.STENCIL_ATTACHMENT -> {
                rec.stencilTextureId = texId
                rec.internalStencilBuffer = null
            }
            GLFramebufferAttachment.DEPTH_STENCIL_ATTACHMENT -> {
                rec.depthTextureId = texId
                rec.stencilTextureId = texId
                rec.internalDepthBuffer = null
                rec.internalStencilBuffer = null
            }
        }
    }

    private fun setFboColorSlot(rec: FramebufferRecord, slot: Int, texId: Int?) {
        if (texId == null) {
            rec.colorAttachments.remove(slot)
        } else {
            rec.colorAttachments[slot] = texId
        }
    }

    override fun checkFramebufferStatus(target: GLFramebufferTarget): GLFramebufferStatus {
        val hw = hardwareDelegate
        if (hw != null) {
            return hw.checkFramebufferStatus(target)
        }
        val fboId = when (target) {
            GLFramebufferTarget.READ_FRAMEBUFFER -> boundReadFramebufferId
            else -> boundDrawFramebufferId
        } ?: return GLFramebufferStatus.COMPLETE
        val rec = framebuffers[fboId] ?: return GLFramebufferStatus.INCOMPLETE_MISSING_ATTACHMENT

        val attachedIds = buildList {
            addAll(rec.colorAttachments.values)
            rec.depthTextureId?.let { add(it) }
            rec.stencilTextureId?.let { add(it) }
        }
        if (attachedIds.isEmpty()) return GLFramebufferStatus.INCOMPLETE_MISSING_ATTACHMENT

        var refW = -1
        var refH = -1
        for (id in attachedIds) {
            val tex = textures[id] ?: return GLFramebufferStatus.INCOMPLETE_ATTACHMENT
            if (tex.width <= 0 || tex.height <= 0) return GLFramebufferStatus.INCOMPLETE_ATTACHMENT
            if (refW < 0) {
                refW = tex.width
                refH = tex.height
            } else if (tex.width != refW || tex.height != refH) {
                return GLFramebufferStatus.INCOMPLETE_DIMENSIONS
            }
        }
        return GLFramebufferStatus.COMPLETE
    }

    override fun deleteFramebuffer(framebuffer: GLFramebuffer) {
        val hw = hardwareDelegate
        if (hw != null) {
            if (hwDrawFramebuffer?.id == framebuffer.id) {
                hwDrawFramebuffer = null
                hw.bindFramebuffer(GLFramebufferTarget.DRAW_FRAMEBUFFER, ensureHardwareDefaultFbo())
            }
            if (hwReadFramebuffer?.id == framebuffer.id) {
                hwReadFramebuffer = null
                hw.bindFramebuffer(GLFramebufferTarget.READ_FRAMEBUFFER, ensureHardwareDefaultFbo())
            }
            hw.deleteFramebuffer(framebuffer)
            return
        }
        framebuffers.remove(framebuffer.id)
        if (boundDrawFramebufferId == framebuffer.id) boundDrawFramebufferId = null
        if (boundReadFramebufferId == framebuffer.id) boundReadFramebufferId = null
    }

    // -------------------------------------------------------------------------
    // Drawing & Readback
    // -------------------------------------------------------------------------

    override fun drawArrays(mode: GLPrimitive, first: Int, count: Int) {
        drawArraysInstanced(mode, first, count, 1)
    }

    override fun drawArraysInstanced(mode: GLPrimitive, first: Int, count: Int, instanceCount: Int) {
        if (count <= 0 || instanceCount <= 0) return
        val hw = hardwareDelegate
        if (hw != null) {
            if (hwDrawFramebuffer == null) {
                hw.bindFramebuffer(GLFramebufferTarget.DRAW_FRAMEBUFFER, ensureHardwareDefaultFbo())
            }
            if (instanceCount == 1) hw.drawArrays(mode, first, count)
            else hw.drawArraysInstanced(mode, first, count, instanceCount)
            markTargetMutated()
            return
        }
        val indices = IntArray(count) { first + it }
        for (inst in 0 until instanceCount) {
            executeDraw(mode, indices, inst)
        }
        markTargetMutated()
    }

    override fun drawElements(mode: GLPrimitive, count: Int, type: GLIndexType, offset: Int) {
        drawElementsInstanced(mode, count, type, offset, 1)
    }

    override fun drawElementsInstanced(
        mode: GLPrimitive,
        count: Int,
        type: GLIndexType,
        offset: Int,
        instanceCount: Int
    ) {
        if (count <= 0 || instanceCount <= 0) return
        val hw = hardwareDelegate
        if (hw != null) {
            if (hwDrawFramebuffer == null) {
                hw.bindFramebuffer(GLFramebufferTarget.DRAW_FRAMEBUFFER, ensureHardwareDefaultFbo())
            }
            if (instanceCount == 1) hw.drawElements(mode, count, type, offset)
            else hw.drawElementsInstanced(mode, count, type, offset, instanceCount)
            markTargetMutated()
            return
        }
        val eboId = currentVao().elementArrayBufferId
            ?: boundBuffers[GLBufferTarget.ELEMENT_ARRAY_BUFFER]
            ?: throw IllegalStateException("drawElements requires an ELEMENT_ARRAY_BUFFER bound")
        val buf = buffers[eboId]
            ?: throw IllegalStateException("Bound ELEMENT_ARRAY_BUFFER $eboId does not exist")
        val stride = when (type) {
            GLIndexType.UNSIGNED_BYTE -> 1
            GLIndexType.UNSIGNED_SHORT -> 2
            GLIndexType.UNSIGNED_INT -> 4
        }
        val indices = IntArray(count)
        for (i in 0 until count) {
            val byteOff = offset + i * stride
            if (byteOff < 0 || byteOff + stride > buf.bytes.size) {
                throw IllegalArgumentException(
                    "Index read out of bounds at offset=$byteOff (bufferBytes=${buf.bytes.size})"
                )
            }
            indices[i] = when (type) {
                GLIndexType.UNSIGNED_BYTE -> buf.bytes[byteOff].toInt() and 0xFF
                GLIndexType.UNSIGNED_SHORT -> readUint16LE(buf.bytes, byteOff)
                GLIndexType.UNSIGNED_INT -> readInt32LE(buf.bytes, byteOff)
            }
        }
        for (inst in 0 until instanceCount) {
            executeDraw(mode, indices, inst)
        }
        markTargetMutated()
    }

    override fun readPixels(x: Int, y: Int, width: Int, height: Int, format: GLReadFormat): ByteArray {
        val hw = hardwareDelegate
        if (hw != null) {
            if (hwReadFramebuffer == null) {
                hw.bindFramebuffer(GLFramebufferTarget.READ_FRAMEBUFFER, ensureHardwareDefaultFbo())
            }
            return hw.readPixels(x, y, width, height, format)
        }
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val (srcPixels, srcW, srcH) = resolveReadPixelsSource()
        val channels = if (format == GLReadFormat.RGB8) 3 else 4
        val out = ByteArray(w * h * channels)
        if (channels == 4 && x == 0 && y == 0 && w == srcW && h == srcH) {
            srcPixels.copyInto(out, 0, 0, min(srcPixels.size, out.size))
            return out
        }
        for (row in 0 until h) {
            val sy = (y + row).coerceIn(0, srcH - 1)
            for (col in 0 until w) {
                val sx = (x + col).coerceIn(0, srcW - 1)
                val sIdx = (sy * srcW + sx) * 4
                val dIdx = (row * w + col) * channels
                out[dIdx] = srcPixels[sIdx]
                out[dIdx + 1] = srcPixels[sIdx + 1]
                out[dIdx + 2] = srcPixels[sIdx + 2]
                if (channels == 4) {
                    out[dIdx + 3] = srcPixels[sIdx + 3]
                }
            }
        }
        return out
    }

    override fun finish() {
        hardwareDelegate?.finish()
    }

    override fun flush() {
        hardwareDelegate?.flush()
    }

    // -------------------------------------------------------------------------
    // Software Pipeline Execution
    // -------------------------------------------------------------------------

    private fun executeDraw(mode: GLPrimitive, indices: IntArray, instanceId: Int) {
        val target = resolveDrawTarget() ?: return
        val prog = activeProgram()
            ?: throw IllegalStateException("Cannot draw without an active GLProgram (useProgram)")
        if (!prog.linkStatus.success) {
            throw IllegalStateException("Cannot draw with unlinked GLProgram: ${prog.linkStatus.infoLog}")
        }
        val vao = currentVao()
        val exec = ShaderExecContext(prog)

        val shadedCache = HashMap<Int, ShadedVertex>(indices.size)
        fun getShadedVertex(vertIdx: Int): ShadedVertex =
            shadedCache.getOrPut(vertIdx) {
                shadeVertex(prog, vao, vertIdx, instanceId, target.width, target.height, exec)
            }

        when (mode) {
            GLPrimitive.POINTS -> {
                for (vIdx in indices) {
                    rasterizePoint(getShadedVertex(vIdx), prog, target, exec)
                }
            }
            GLPrimitive.LINES -> {
                var i = 0
                while (i + 1 < indices.size) {
                    rasterizeLine(getShadedVertex(indices[i]), getShadedVertex(indices[i + 1]), prog, target, exec)
                    i += 2
                }
            }
            GLPrimitive.LINE_STRIP -> {
                for (i in 0 until indices.size - 1) {
                    rasterizeLine(getShadedVertex(indices[i]), getShadedVertex(indices[i + 1]), prog, target, exec)
                }
            }
            GLPrimitive.LINE_LOOP -> {
                if (indices.size >= 2) {
                    for (i in 0 until indices.size - 1) {
                        rasterizeLine(getShadedVertex(indices[i]), getShadedVertex(indices[i + 1]), prog, target, exec)
                    }
                    rasterizeLine(getShadedVertex(indices.last()), getShadedVertex(indices.first()), prog, target, exec)
                }
            }
            GLPrimitive.TRIANGLES -> {
                var i = 0
                while (i + 2 < indices.size) {
                    submitClippedTriangle(
                        getShadedVertex(indices[i]),
                        getShadedVertex(indices[i + 1]),
                        getShadedVertex(indices[i + 2]),
                        prog,
                        target,
                        exec
                    )
                    i += 3
                }
            }
            GLPrimitive.TRIANGLE_STRIP -> {
                for (i in 0 until indices.size - 2) {
                    val v0 = getShadedVertex(indices[i])
                    val v1 = getShadedVertex(indices[i + 1])
                    val v2 = getShadedVertex(indices[i + 2])
                    if (i % 2 == 0) {
                        submitClippedTriangle(v0, v1, v2, prog, target, exec)
                    } else {
                        submitClippedTriangle(v1, v0, v2, prog, target, exec)
                    }
                }
            }
            GLPrimitive.TRIANGLE_FAN -> {
                if (indices.size >= 3) {
                    val anchor = getShadedVertex(indices[0])
                    for (i in 1 until indices.size - 1) {
                        submitClippedTriangle(
                            anchor,
                            getShadedVertex(indices[i]),
                            getShadedVertex(indices[i + 1]),
                            prog,
                            target,
                            exec
                        )
                    }
                }
            }
        }
        target.syncDepthTextureFromBuffer()
    }

    private fun shadeVertex(
        prog: ProgramRecord,
        vao: VaoRecord,
        vertexIndex: Int,
        instanceId: Int,
        targetW: Int,
        targetH: Int,
        exec: ShaderExecContext
    ): ShadedVertex {
        exec.resetForVertex(vertexIndex, instanceId)
        for ((attrName, loc) in prog.attribLocations) {
            val typeName = prog.attribTypes[attrName] ?: "vec4"
            val dim = glslTypeDim(typeName)
            val v = readAttribForVertex(vao, loc, vertexIndex, instanceId, dim)
            exec.vars[attrName] = v
        }
        exec.vars["gl_Position"] = GlslVal(0f, 0f, 0f, 1f, dim = 4)
        exec.vars["gl_PointSize"] = GlslVal(1f, 0f, 0f, 0f, dim = 1)

        val vsAst = shaders[prog.vertexShaderId]?.ast
        if (vsAst != null) {
            executeFunctionBody(vsAst, vsAst.mainStmts, exec)
        }

        val pos = exec.vars["gl_Position"] ?: GlslVal(0f, 0f, 0f, 1f, dim = 4)
        val pointSize = (exec.vars["gl_PointSize"]?.x ?: 1f).coerceAtLeast(1f)
        val varyings = LinkedHashMap<String, GlslVal>(prog.varyingNames.size)
        for (vName in prog.varyingNames) {
            val valOut = exec.vars[vName]
            varyings[vName] = if (valOut != null) valOut.copy() else GlslVal(dim = glslTypeDim(prog.varyingTypes[vName] ?: "vec4"))
        }

        val invW = if (abs(pos.w) > 1e-6f) 1f / pos.w else 1f
        val ndcX = pos.x * invW
        val ndcY = pos.y * invW
        val ndcZ = pos.z * invW
        val (sx, sy) = ndcToScreen(ndcX, ndcY, targetW, targetH)
        return ShadedVertex(
            clipX = pos.x,
            clipY = pos.y,
            clipZ = pos.z,
            clipW = pos.w,
            ndcX = ndcX,
            ndcY = ndcY,
            ndcZ = ndcZ,
            screenX = sx,
            screenY = sy,
            pointSize = pointSize,
            varyings = varyings
        )
    }

    private fun ndcToScreen(ndcX: Float, ndcY: Float, targetW: Int, targetH: Int): Pair<Float, Float> {
        val vx = viewportX.toFloat()
        val vw = viewportW.coerceAtLeast(1).toFloat()
        val vh = viewportH.coerceAtLeast(1).toFloat()
        // OpenGL glViewport(x, y, w, h) places y from bottom edge; full-surface (0,0,W,H) maps vyTop = 0.
        val vyTop = (targetH - viewportY - viewportH).toFloat()
        val sx = vx + (ndcX * 0.5f + 0.5f) * vw
        val sy = vyTop + (0.5f - ndcY * 0.5f) * vh
        return sx to sy
    }

    private fun submitClippedTriangle(
        v0: ShadedVertex,
        v1: ShadedVertex,
        v2: ShadedVertex,
        prog: ProgramRecord,
        target: RenderTargetView,
        exec: ShaderExecContext
    ) {
        // Near-plane homogeneous clip (z + w >= 0) so behind-camera vertices project safely
        val clipped = clipPolygonAgainstNearPlane(listOf(v0, v1, v2), target.width, target.height)
        if (clipped.size < 3) return
        val a = clipped[0]
        for (i in 1 until clipped.size - 1) {
            rasterizeTriangle(a, clipped[i], clipped[i + 1], prog, target, exec)
        }
    }

    private fun clipPolygonAgainstNearPlane(
        poly: List<ShadedVertex>,
        targetW: Int,
        targetH: Int
    ): List<ShadedVertex> {
        var allInside = true
        for (v in poly) {
            if (v.clipZ + v.clipW < -1e-5f || v.clipW <= 0f) {
                allInside = false
                break
            }
        }
        if (allInside) return poly

        val out = ArrayList<ShadedVertex>(poly.size + 1)
        for (i in poly.indices) {
            val cur = poly[i]
            val next = poly[(i + 1) % poly.size]
            val curDist = cur.clipZ + cur.clipW
            val nextDist = next.clipZ + next.clipW
            val curIn = curDist >= 0f && cur.clipW > 0f
            val nextIn = nextDist >= 0f && next.clipW > 0f
            if (curIn) {
                out.add(cur)
                if (!nextIn) {
                    val t = curDist / (curDist - nextDist).let { if (abs(it) < 1e-6f) 1e-6f else it }
                    out.add(lerpShadedVertex(cur, next, t.coerceIn(0f, 1f), targetW, targetH))
                }
            } else if (nextIn) {
                val t = curDist / (curDist - nextDist).let { if (abs(it) < 1e-6f) 1e-6f else it }
                out.add(lerpShadedVertex(cur, next, t.coerceIn(0f, 1f), targetW, targetH))
            }
        }
        return out
    }

    private fun lerpShadedVertex(
        a: ShadedVertex,
        b: ShadedVertex,
        t: Float,
        targetW: Int,
        targetH: Int
    ): ShadedVertex {
        val invT = 1f - t
        val cx = a.clipX * invT + b.clipX * t
        val cy = a.clipY * invT + b.clipY * t
        val cz = a.clipZ * invT + b.clipZ * t
        val cw = (a.clipW * invT + b.clipW * t).let { if (abs(it) < 1e-6f) 1e-6f else it }
        val invW = 1f / cw
        val ndcX = cx * invW
        val ndcY = cy * invW
        val ndcZ = cz * invW
        val (sx, sy) = ndcToScreen(ndcX, ndcY, targetW, targetH)
        val varyings = LinkedHashMap<String, GlslVal>(a.varyings.size)
        for ((k, av) in a.varyings) {
            val bv = b.varyings[k]
            varyings[k] = if (bv != null) {
                GlslVal(
                    x = av.x * invT + bv.x * t,
                    y = av.y * invT + bv.y * t,
                    z = av.z * invT + bv.z * t,
                    w = av.w * invT + bv.w * t,
                    dim = av.dim
                )
            } else {
                av.copy()
            }
        }
        return ShadedVertex(
            clipX = cx,
            clipY = cy,
            clipZ = cz,
            clipW = cw,
            ndcX = ndcX,
            ndcY = ndcY,
            ndcZ = ndcZ,
            screenX = sx,
            screenY = sy,
            pointSize = a.pointSize * invT + b.pointSize * t,
            varyings = varyings
        )
    }

    private fun rasterizeTriangle(
        v0: ShadedVertex,
        v1: ShadedVertex,
        v2: ShadedVertex,
        prog: ProgramRecord,
        target: RenderTargetView,
        exec: ShaderExecContext
    ) {
        val ndcArea2 = (v1.ndcX - v0.ndcX) * (v2.ndcY - v0.ndcY) -
            (v2.ndcX - v0.ndcX) * (v1.ndcY - v0.ndcY)
        val isFront = if (frontFaceMode == GLWinding.CCW) ndcArea2 > 0f else ndcArea2 < 0f
        if (GLCapability.CULL_FACE in enabledCapabilities) {
            when (cullFaceMode) {
                GLFaceSide.FRONT_AND_BACK -> return
                GLFaceSide.BACK -> if (!isFront) return
                GLFaceSide.FRONT -> if (isFront) return
            }
        }

        val minX = floor(min(v0.screenX, min(v1.screenX, v2.screenX))).toInt().coerceIn(0, target.width - 1)
        val maxX = floor(max(v0.screenX, max(v1.screenX, v2.screenX))).toInt().coerceIn(0, target.width - 1)
        val minY = floor(min(v0.screenY, min(v1.screenY, v2.screenY))).toInt().coerceIn(0, target.height - 1)
        val maxY = floor(max(v0.screenY, max(v1.screenY, v2.screenY))).toInt().coerceIn(0, target.height - 1)
        if (minX > maxX || minY > maxY) return

        val denom = (v1.screenY - v2.screenY) * (v0.screenX - v2.screenX) +
            (v2.screenX - v1.screenX) * (v0.screenY - v2.screenY)
        if (abs(denom) < 1e-6f) return
        val invDenom = 1f / denom
        val windingSign = if (denom > 0f) 1f else -1f

        fun isTopLeftEdge(ax: Float, ay: Float, bx: Float, by: Float): Boolean {
            val dx = (bx - ax) * windingSign
            val dy = (by - ay) * windingSign
            return dy < -1e-5f || (abs(dy) <= 1e-5f && dx > 1e-5f)
        }
        val topLeft0 = isTopLeftEdge(v1.screenX, v1.screenY, v2.screenX, v2.screenY)
        val topLeft1 = isTopLeftEdge(v2.screenX, v2.screenY, v0.screenX, v0.screenY)
        val topLeft2 = isTopLeftEdge(v0.screenX, v0.screenY, v1.screenX, v1.screenY)
        fun edgeInside(b: Float, topLeft: Boolean): Boolean =
            if (topLeft) b >= -1e-5f else b > 1e-5f

        val dw0dx = (v1.screenY - v2.screenY) * invDenom
        val dw0dy = (v2.screenX - v1.screenX) * invDenom
        val dw1dx = (v2.screenY - v0.screenY) * invDenom
        val dw1dy = (v0.screenX - v2.screenX) * invDenom

        val invW0 = if (abs(v0.clipW) > 1e-6f) 1f / v0.clipW else 1f
        val invW1 = if (abs(v1.clipW) > 1e-6f) 1f / v1.clipW else 1f
        val invW2 = if (abs(v2.clipW) > 1e-6f) 1f / v2.clipW else 1f
        val depthTestEnabled = GLCapability.DEPTH_TEST in enabledCapabilities && target.depthBuffer != null
        val scissorEnabled = GLCapability.SCISSOR_TEST in enabledCapabilities
        val scX0 = if (scissorEnabled) scissorX.coerceIn(0, target.width) else 0
        val scX1 = if (scissorEnabled) (scissorX + scissorW).coerceIn(scX0, target.width) else target.width
        val scY0 = if (scissorEnabled) resolveScreenScissorStartY(target.height) else 0
        val scY1 = if (scissorEnabled) resolveScreenScissorEndY(target.height, scY0) else target.height

        val startFx = minX + 0.5f - v2.screenX
        var rowW0 = dw0dx * startFx + dw0dy * (minY + 0.5f - v2.screenY)
        var rowW1 = dw1dx * startFx + dw1dy * (minY + 0.5f - v2.screenY)

        for (py in minY..maxY) {
            if (scissorEnabled && (py < scY0 || py >= scY1)) {
                rowW0 += dw0dy
                rowW1 += dw1dy
                continue
            }
            var b0 = rowW0
            var b1 = rowW1
            for (px in minX..maxX) {
                if (scissorEnabled && (px < scX0 || px >= scX1)) {
                    b0 += dw0dx
                    b1 += dw1dx
                    continue
                }
                val b2 = 1f - b0 - b1
                if (edgeInside(b0, topLeft0) && edgeInside(b1, topLeft1) && edgeInside(b2, topLeft2)) {
                    val ndcZ = b0 * v0.ndcZ + b1 * v1.ndcZ + b2 * v2.ndcZ
                    val fragDepth = ndcZ * 0.5f + 0.5f
                    if (fragDepth >= -1e-4f && fragDepth <= 1.0001f) {
                        val clampedDepth = fragDepth.coerceIn(0f, 1f)
                        val pixIdx = py * target.width + px
                        if (!depthTestEnabled || passesDepthTest(clampedDepth, target.depthBuffer!![pixIdx])) {
                            val invW = b0 * invW0 + b1 * invW1 + b2 * invW2
                            val pw0: Float
                            val pw1: Float
                            val pw2: Float
                            if (abs(invW) > 1e-7f) {
                                val recW = 1f / invW
                                pw0 = b0 * invW0 * recW
                                pw1 = b1 * invW1 * recW
                                pw2 = b2 * invW2 * recW
                            } else {
                                pw0 = b0
                                pw1 = b1
                                pw2 = b2
                            }
                            shadeAndCommitFragment(
                                px = px,
                                py = py,
                                pixIdx = pixIdx,
                                depth = clampedDepth,
                                fragW = if (abs(invW) > 1e-7f) 1f / invW else 1f,
                                isFront = isFront,
                                v0 = v0,
                                v1 = v1,
                                v2 = v2,
                                pw0 = pw0,
                                pw1 = pw1,
                                pw2 = pw2,
                                prog = prog,
                                target = target,
                                exec = exec,
                                depthTestEnabled = depthTestEnabled
                            )
                        }
                    }
                }
                b0 += dw0dx
                b1 += dw1dx
            }
            rowW0 += dw0dy
            rowW1 += dw1dy
        }
    }

    private fun rasterizeLine(
        v0: ShadedVertex,
        v1: ShadedVertex,
        prog: ProgramRecord,
        target: RenderTargetView,
        exec: ShaderExecContext
    ) {
        val dx = v1.screenX - v0.screenX
        val dy = v1.screenY - v0.screenY
        val steps = max(abs(dx), abs(dy)).ceilToIntCompat().coerceAtLeast(1)
        val depthTestEnabled = GLCapability.DEPTH_TEST in enabledCapabilities && target.depthBuffer != null
        val scissorEnabled = GLCapability.SCISSOR_TEST in enabledCapabilities
        val scX0 = if (scissorEnabled) scissorX.coerceIn(0, target.width) else 0
        val scX1 = if (scissorEnabled) (scissorX + scissorW).coerceIn(scX0, target.width) else target.width
        val scY0 = if (scissorEnabled) resolveScreenScissorStartY(target.height) else 0
        val scY1 = if (scissorEnabled) resolveScreenScissorEndY(target.height, scY0) else target.height

        for (step in 0..steps) {
            val t = step.toFloat() / steps.toFloat()
            val invT = 1f - t
            val px = floor(v0.screenX * invT + v1.screenX * t).toInt()
            val py = floor(v0.screenY * invT + v1.screenY * t).toInt()
            if (px !in 0 until target.width || py !in 0 until target.height) continue
            if (scissorEnabled && (px < scX0 || px >= scX1 || py < scY0 || py >= scY1)) continue
            val fragDepth = ((v0.ndcZ * invT + v1.ndcZ * t) * 0.5f + 0.5f).coerceIn(0f, 1f)
            val pixIdx = py * target.width + px
            if (!depthTestEnabled || passesDepthTest(fragDepth, target.depthBuffer!![pixIdx])) {
                shadeAndCommitFragment(
                    px = px,
                    py = py,
                    pixIdx = pixIdx,
                    depth = fragDepth,
                    fragW = 1f,
                    isFront = true,
                    v0 = v0,
                    v1 = v1,
                    v2 = v0,
                    pw0 = invT,
                    pw1 = t,
                    pw2 = 0f,
                    prog = prog,
                    target = target,
                    exec = exec,
                    depthTestEnabled = depthTestEnabled
                )
            }
        }
    }

    private fun rasterizePoint(
        v: ShadedVertex,
        prog: ProgramRecord,
        target: RenderTargetView,
        exec: ShaderExecContext
    ) {
        val half = max(0.5f, v.pointSize * 0.5f)
        val minX = floor(v.screenX - half).toInt().coerceIn(0, target.width - 1)
        val maxX = floor(v.screenX + half - 0.001f).toInt().coerceIn(0, target.width - 1)
        val minY = floor(v.screenY - half).toInt().coerceIn(0, target.height - 1)
        val maxY = floor(v.screenY + half - 0.001f).toInt().coerceIn(0, target.height - 1)
        if (minX > maxX || minY > maxY) return

        val fragDepth = (v.ndcZ * 0.5f + 0.5f).coerceIn(0f, 1f)
        val depthTestEnabled = GLCapability.DEPTH_TEST in enabledCapabilities && target.depthBuffer != null
        val scissorEnabled = GLCapability.SCISSOR_TEST in enabledCapabilities
        val scX0 = if (scissorEnabled) scissorX.coerceIn(0, target.width) else 0
        val scX1 = if (scissorEnabled) (scissorX + scissorW).coerceIn(scX0, target.width) else target.width
        val scY0 = if (scissorEnabled) resolveScreenScissorStartY(target.height) else 0
        val scY1 = if (scissorEnabled) resolveScreenScissorEndY(target.height, scY0) else target.height

        for (py in minY..maxY) {
            if (scissorEnabled && (py < scY0 || py >= scY1)) continue
            for (px in minX..maxX) {
                if (scissorEnabled && (px < scX0 || px >= scX1)) continue
                val pixIdx = py * target.width + px
                if (!depthTestEnabled || passesDepthTest(fragDepth, target.depthBuffer!![pixIdx])) {
                    shadeAndCommitFragment(
                        px = px,
                        py = py,
                        pixIdx = pixIdx,
                        depth = fragDepth,
                        fragW = 1f,
                        isFront = true,
                        v0 = v,
                        v1 = v,
                        v2 = v,
                        pw0 = 1f,
                        pw1 = 0f,
                        pw2 = 0f,
                        prog = prog,
                        target = target,
                        exec = exec,
                        depthTestEnabled = depthTestEnabled
                    )
                }
            }
        }
    }

    private fun shadeAndCommitFragment(
        px: Int,
        py: Int,
        pixIdx: Int,
        depth: Float,
        fragW: Float,
        isFront: Boolean,
        v0: ShadedVertex,
        v1: ShadedVertex,
        v2: ShadedVertex,
        pw0: Float,
        pw1: Float,
        pw2: Float,
        prog: ProgramRecord,
        target: RenderTargetView,
        exec: ShaderExecContext,
        depthTestEnabled: Boolean
    ) {
        exec.resetForFragment(
            fragX = px + 0.5f,
            fragY = (target.height - 1 - py) + 0.5f,
            fragZ = depth,
            fragW = if (abs(fragW) > 1e-6f) 1f / fragW else 1f,
            isFrontFacing = isFront
        )
        for (vName in prog.varyingNames) {
            val a = v0.varyings[vName]
            val b = v1.varyings[vName]
            val c = v2.varyings[vName]
            if (a != null && b != null && c != null) {
                exec.vars[vName] = GlslVal(
                    x = a.x * pw0 + b.x * pw1 + c.x * pw2,
                    y = a.y * pw0 + b.y * pw1 + c.y * pw2,
                    z = a.z * pw0 + b.z * pw1 + c.z * pw2,
                    w = a.w * pw0 + b.w * pw1 + c.w * pw2,
                    dim = a.dim
                )
            }
        }
        val outVarName = prog.fragOutName ?: "gl_FragColor"
        exec.vars[outVarName] = GlslVal(0f, 0f, 0f, 1f, dim = 4)

        val fsAst = shaders[prog.fragmentShaderId]?.ast
        if (fsAst != null) {
            executeFunctionBody(fsAst, fsAst.mainStmts, exec)
        }
        if (exec.discarded) return

        val fragOut = exec.vars[outVarName] ?: GlslVal(0f, 0f, 0f, 1f, dim = 4)
        val colorBuf = target.colorPixels
        if (colorBuf != null) {
            writeColorPixel(colorBuf, pixIdx * 4, fragOut.x, fragOut.y, fragOut.z, fragOut.w)
        }
        if (depthTestEnabled && depthWriteMask) {
            target.depthBuffer!![pixIdx] = depth
        }
    }

    private fun passesDepthTest(incoming: Float, stored: Float): Boolean = when (depthFuncMode) {
        GLCompareFunction.NEVER -> false
        GLCompareFunction.LESS -> incoming < stored
        GLCompareFunction.EQUAL -> abs(incoming - stored) <= 1e-5f
        GLCompareFunction.LEQUAL -> incoming <= stored
        GLCompareFunction.GREATER -> incoming > stored
        GLCompareFunction.NOTEQUAL -> abs(incoming - stored) > 1e-5f
        GLCompareFunction.GEQUAL -> incoming >= stored
        GLCompareFunction.ALWAYS -> true
    }

    private fun writeColorPixel(
        pixels: ByteArray,
        idx: Int,
        srcR: Float,
        srcG: Float,
        srcB: Float,
        srcA: Float
    ) {
        val sr = srcR.coerceIn(0f, 1f)
        val sg = srcG.coerceIn(0f, 1f)
        val sb = srcB.coerceIn(0f, 1f)
        val sa = srcA.coerceIn(0f, 1f)

        if (GLCapability.BLEND !in enabledCapabilities) {
            pixels[idx] = (sr * 255f).roundToInt().coerceIn(0, 255).toByte()
            pixels[idx + 1] = (sg * 255f).roundToInt().coerceIn(0, 255).toByte()
            pixels[idx + 2] = (sb * 255f).roundToInt().coerceIn(0, 255).toByte()
            pixels[idx + 3] = (sa * 255f).roundToInt().coerceIn(0, 255).toByte()
            return
        }

        val dr = (pixels[idx].toInt() and 0xFF) / 255f
        val dg = (pixels[idx + 1].toInt() and 0xFF) / 255f
        val db = (pixels[idx + 2].toInt() and 0xFF) / 255f
        val da = (pixels[idx + 3].toInt() and 0xFF) / 255f

        val sfR = evalBlendFactor(blendSrcRGB, sr, sg, sb, sa, dr, dg, db, da, 0)
        val sfG = evalBlendFactor(blendSrcRGB, sr, sg, sb, sa, dr, dg, db, da, 1)
        val sfB = evalBlendFactor(blendSrcRGB, sr, sg, sb, sa, dr, dg, db, da, 2)
        val sfA = evalBlendFactor(blendSrcAlpha, sr, sg, sb, sa, dr, dg, db, da, 3)

        val dfR = evalBlendFactor(blendDstRGB, sr, sg, sb, sa, dr, dg, db, da, 0)
        val dfG = evalBlendFactor(blendDstRGB, sr, sg, sb, sa, dr, dg, db, da, 1)
        val dfB = evalBlendFactor(blendDstRGB, sr, sg, sb, sa, dr, dg, db, da, 2)
        val dfA = evalBlendFactor(blendDstAlpha, sr, sg, sb, sa, dr, dg, db, da, 3)

        val outR = evalBlendEquation(blendEquationMode, sr * sfR, dr * dfR)
        val outG = evalBlendEquation(blendEquationMode, sg * sfG, dg * dfG)
        val outB = evalBlendEquation(blendEquationMode, sb * sfB, db * dfB)
        val outA = evalBlendEquation(blendEquationMode, sa * sfA, da * dfA)

        pixels[idx] = (outR.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255).toByte()
        pixels[idx + 1] = (outG.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255).toByte()
        pixels[idx + 2] = (outB.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255).toByte()
        pixels[idx + 3] = (outA.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255).toByte()
    }

    private fun evalBlendFactor(
        factor: GLBlendFactor,
        sr: Float, sg: Float, sb: Float, sa: Float,
        dr: Float, dg: Float, db: Float, da: Float,
        channel: Int
    ): Float = when (factor) {
        GLBlendFactor.ZERO -> 0f
        GLBlendFactor.ONE -> 1f
        GLBlendFactor.SRC_COLOR -> when (channel) { 0 -> sr; 1 -> sg; 2 -> sb; else -> sa }
        GLBlendFactor.ONE_MINUS_SRC_COLOR -> 1f - when (channel) { 0 -> sr; 1 -> sg; 2 -> sb; else -> sa }
        GLBlendFactor.DST_COLOR -> when (channel) { 0 -> dr; 1 -> dg; 2 -> db; else -> da }
        GLBlendFactor.ONE_MINUS_DST_COLOR -> 1f - when (channel) { 0 -> dr; 1 -> dg; 2 -> db; else -> da }
        GLBlendFactor.SRC_ALPHA -> sa
        GLBlendFactor.ONE_MINUS_SRC_ALPHA -> 1f - sa
        GLBlendFactor.DST_ALPHA -> da
        GLBlendFactor.ONE_MINUS_DST_ALPHA -> 1f - da
        GLBlendFactor.CONSTANT_COLOR -> when (channel) {
            0 -> blendConstantR
            1 -> blendConstantG
            2 -> blendConstantB
            else -> blendConstantA
        }
        GLBlendFactor.ONE_MINUS_CONSTANT_COLOR -> 1f - when (channel) {
            0 -> blendConstantR
            1 -> blendConstantG
            2 -> blendConstantB
            else -> blendConstantA
        }
        GLBlendFactor.CONSTANT_ALPHA -> blendConstantA
        GLBlendFactor.ONE_MINUS_CONSTANT_ALPHA -> 1f - blendConstantA
        GLBlendFactor.SRC_ALPHA_SATURATE -> if (channel == 3) 1f else min(sa, 1f - da)
    }

    private fun evalBlendEquation(eq: GLBlendEquation, sTerm: Float, dTerm: Float): Float = when (eq) {
        GLBlendEquation.FUNC_ADD -> sTerm + dTerm
        GLBlendEquation.FUNC_SUBTRACT -> sTerm - dTerm
        GLBlendEquation.FUNC_REVERSE_SUBTRACT -> dTerm - sTerm
        GLBlendEquation.MIN -> min(sTerm, dTerm)
        GLBlendEquation.MAX -> max(sTerm, dTerm)
    }

    // -------------------------------------------------------------------------
    // Target Resolution & Helpers
    // -------------------------------------------------------------------------

    private fun resolveScreenScissorStartY(targetH: Int): Int =
        (targetH - scissorY - scissorH).coerceIn(0, targetH)

    private fun resolveScreenScissorEndY(targetH: Int, startY: Int): Int =
        (targetH - scissorY).coerceIn(startY, targetH)

    private fun resolveDrawTarget(): RenderTargetView? {
        val fboId = boundDrawFramebufferId ?: return RenderTargetView(
            width = surfaceWidth,
            height = surfaceHeight,
            colorPixels = defaultColorBuffer,
            depthBuffer = defaultDepthBuffer,
            stencilBuffer = defaultStencilBuffer,
            depthTexture = null
        )
        val rec = framebuffers[fboId] ?: return null
        val colorTex = rec.colorAttachments[0]?.let { textures[it] }
        val depthTex = rec.depthTextureId?.let { textures[it] }
        val targetW = colorTex?.width ?: depthTex?.width ?: return null
        val targetH = colorTex?.height ?: depthTex?.height ?: return null
        val pixelCount = targetW * targetH

        val depthBuf = if (depthTex?.depthValues != null && depthTex.depthValues!!.size == pixelCount) {
            depthTex.depthValues!!
        } else {
            val existing = rec.internalDepthBuffer
            if (existing == null || existing.size != pixelCount) {
                FloatArray(pixelCount) { clearDepthValue }.also { rec.internalDepthBuffer = it }
            } else {
                existing
            }
        }
        val stencilBuf = rec.internalStencilBuffer?.takeIf { it.size == pixelCount }
            ?: IntArray(pixelCount) { clearStencilValue }.also { rec.internalStencilBuffer = it }

        return RenderTargetView(
            width = targetW,
            height = targetH,
            colorPixels = colorTex?.rgbaPixels,
            depthBuffer = depthBuf,
            stencilBuffer = stencilBuf,
            depthTexture = depthTex
        )
    }

    private fun resolveReadPixelsSource(): Triple<ByteArray, Int, Int> {
        val fboId = boundReadFramebufferId ?: return Triple(defaultColorBuffer, surfaceWidth, surfaceHeight)
        val rec = framebuffers[fboId]
            ?: throw IllegalStateException("Bound READ_FRAMEBUFFER $fboId does not exist")
        val colorTex = rec.colorAttachments[0]?.let { textures[it] }
            ?: throw IllegalStateException("READ_FRAMEBUFFER $fboId has no valid COLOR_ATTACHMENT0 texture")
        return Triple(colorTex.rgbaPixels, colorTex.width, colorTex.height)
    }

    private fun sampleTexture(unit: Int, u: Float, v: Float): GlslVal {
        val texId = boundTexturesByUnit[unit]?.get(GLTextureTarget.TEXTURE_2D)
            ?: return GlslVal(0f, 0f, 0f, 1f, dim = 4)
        val tex = textures[texId] ?: return GlslVal(0f, 0f, 0f, 1f, dim = 4)
        if (tex.rgbaPixels.isEmpty() || tex.width <= 0 || tex.height <= 0) {
            return GlslVal(0f, 0f, 0f, 1f, dim = 4)
        }
        val wu = wrapTexCoord(u, tex.params.wrapS)
        val wv = wrapTexCoord(v, tex.params.wrapT)
        // Top-down pixel buffer: v = 1 -> top row (0), v = 0 -> bottom row (height - 1)
        val flippedV = 1f - wv

        if (tex.params.minFilter == GLTextureFilter.NEAREST && tex.params.magFilter == GLTextureFilter.NEAREST) {
            val tx = (wu * tex.width).toInt().coerceIn(0, tex.width - 1)
            val ty = (flippedV * tex.height).toInt().coerceIn(0, tex.height - 1)
            val idx = (ty * tex.width + tx) * 4
            return GlslVal(
                x = (tex.rgbaPixels[idx].toInt() and 0xFF) / 255f,
                y = (tex.rgbaPixels[idx + 1].toInt() and 0xFF) / 255f,
                z = (tex.rgbaPixels[idx + 2].toInt() and 0xFF) / 255f,
                w = (tex.rgbaPixels[idx + 3].toInt() and 0xFF) / 255f,
                dim = 4
            )
        }

        val fx = wu * (tex.width - 1)
        val fy = flippedV * (tex.height - 1)
        val x0 = floor(fx).toInt().coerceIn(0, tex.width - 1)
        val y0 = floor(fy).toInt().coerceIn(0, tex.height - 1)
        val x1 = (x0 + 1).coerceAtMost(tex.width - 1)
        val y1 = (y0 + 1).coerceAtMost(tex.height - 1)
        val tx = (fx - x0).coerceIn(0f, 1f)
        val ty = (fy - y0).coerceIn(0f, 1f)

        val i00 = (y0 * tex.width + x0) * 4
        val i10 = (y0 * tex.width + x1) * 4
        val i01 = (y1 * tex.width + x0) * 4
        val i11 = (y1 * tex.width + x1) * 4
        fun bilerp(c: Int): Float {
            val c00 = (tex.rgbaPixels[i00 + c].toInt() and 0xFF) / 255f
            val c10 = (tex.rgbaPixels[i10 + c].toInt() and 0xFF) / 255f
            val c01 = (tex.rgbaPixels[i01 + c].toInt() and 0xFF) / 255f
            val c11 = (tex.rgbaPixels[i11 + c].toInt() and 0xFF) / 255f
            val top = c00 * (1f - tx) + c10 * tx
            val bot = c01 * (1f - tx) + c11 * tx
            return top * (1f - ty) + bot * ty
        }
        return GlslVal(bilerp(0), bilerp(1), bilerp(2), bilerp(3), dim = 4)
    }

    private fun wrapTexCoord(coord: Float, mode: GLTextureWrap): Float = when (mode) {
        GLTextureWrap.CLAMP_TO_EDGE -> coord.coerceIn(0f, 1f)
        GLTextureWrap.REPEAT -> {
            val f = coord - floor(coord)
            if (f < 0f) f + 1f else f
        }
        GLTextureWrap.MIRRORED_REPEAT -> {
            val fl = floor(coord)
            val f = coord - fl
            if ((fl.toInt() and 1) != 0) 1f - f else f
        }
    }

    private fun activeProgram(): ProgramRecord? = activeProgramId?.let { programs[it] }
    private fun currentVao(): VaoRecord = vaos[activeVaoId] ?: vaos.getValue(0)
    private fun requireBoundBuffer(target: GLBufferTarget): BufferRecord {
        val id = boundBuffers[target]
            ?: throw IllegalStateException("No GLBuffer bound to $target")
        return buffers[id]
            ?: throw IllegalStateException("Bound buffer $id on $target does not exist")
    }

    private fun readAttribForVertex(
        vao: VaoRecord,
        location: Int,
        vertexIndex: Int,
        instanceId: Int,
        targetDim: Int
    ): GlslVal {
        val out = FloatArray(4) { i -> if (i == 3) 1f else 0f }
        if (location in vao.enabledAttribs) {
            val binding = vao.attribBindings[location]
            val buf = binding?.bufferId?.let { buffers[it] }
            if (buf != null && buf.bytes.isNotEmpty()) {
                val layout = binding.layout
                val divisor = vao.attribDivisors[location] ?: 0
                val elemIndex = if (divisor > 0) instanceId / divisor else vertexIndex
                val compCount = layout.size.coerceIn(1, 4)
                val elemBytes = byteSizeOfDataType(layout.type)
                val stride = if (layout.stride > 0) layout.stride else compCount * elemBytes
                val baseByte = layout.offset + elemIndex * stride
                for (c in 0 until compCount) {
                    val bOff = baseByte + c * elemBytes
                    if (bOff >= 0 && bOff + elemBytes <= buf.bytes.size) {
                        out[c] = readBufferScalar(buf.bytes, bOff, layout.type, layout.normalized)
                    }
                }
            }
        }
        return GlslVal(out[0], out[1], out[2], out[3], dim = targetDim)
    }

    private fun byteSizeOfDataType(type: GLDataType): Int = when (type) {
        GLDataType.BYTE, GLDataType.UNSIGNED_BYTE -> 1
        GLDataType.SHORT, GLDataType.UNSIGNED_SHORT, GLDataType.HALF_FLOAT -> 2
        GLDataType.INT, GLDataType.UNSIGNED_INT, GLDataType.FLOAT -> 4
    }

    private fun readBufferScalar(bytes: ByteArray, off: Int, type: GLDataType, normalized: Boolean): Float =
        when (type) {
            GLDataType.FLOAT -> Float.fromBits(readInt32LE(bytes, off))
            GLDataType.HALF_FLOAT -> halfFloatToFloat(readUint16LE(bytes, off))
            GLDataType.BYTE -> {
                val v = bytes[off].toFloat()
                if (normalized) (v / 127f).coerceAtLeast(-1f) else v
            }
            GLDataType.UNSIGNED_BYTE -> {
                val v = (bytes[off].toInt() and 0xFF).toFloat()
                if (normalized) v / 255f else v
            }
            GLDataType.SHORT -> {
                val s = readInt16LE(bytes, off).toFloat()
                if (normalized) (s / 32767f).coerceAtLeast(-1f) else s
            }
            GLDataType.UNSIGNED_SHORT -> {
                val u = readUint16LE(bytes, off).toFloat()
                if (normalized) u / 65535f else u
            }
            GLDataType.INT -> {
                val i = readInt32LE(bytes, off).toFloat()
                if (normalized) (i / 2147483647f).coerceAtLeast(-1f) else i
            }
            GLDataType.UNSIGNED_INT -> {
                val u = (readInt32LE(bytes, off).toLong() and 0xFFFF_FFFFL).toFloat()
                if (normalized) u / 4294967295f else u
            }
        }

    // -------------------------------------------------------------------------
    // GLSL Parser & Interpreter (WebGL 1 + WebGL 2 / GLSL ES 3.00)
    // -------------------------------------------------------------------------

    private fun compileAstOrError(type: ShaderType, rawSource: String): Pair<GlslShaderAst?, GLCompileStatus> {
        if (rawSource.isBlank()) {
            return null to GLCompileStatus(false, "Shader source is blank")
        }
        val cleaned = preprocessGlsl(rawSource)
        val tokens = tokenizeGlsl(cleaned)
        val parser = GlslParser(tokens, type)
        val ast = parser.parseShader()
        if (parser.errorMessage != null) {
            return null to GLCompileStatus(false, parser.errorMessage!!)
        }
        if (!ast.hasMain) {
            return null to GLCompileStatus(false, "Missing void main() entry point in $type shader")
        }
        return ast to GLCompileStatus(true)
    }

    private fun populateProgramMetadata(prog: ProgramRecord, vs: ShaderRecord, fs: ShaderRecord) {
        prog.attribLocations.clear()
        prog.attribTypes.clear()
        prog.varyingNames.clear()
        prog.varyingTypes.clear()
        prog.declaredUniformNames.clear()
        prog.declaredUniformTypes.clear()
        prog.uniformLocations.clear()
        prog.uniformBlockNames.clear()
        prog.fragOutName = null

        val vsAst = vs.ast ?: return
        val fsAst = fs.ast ?: return

        val usedLocs = mutableSetOf<Int>()
        for (attr in vsAst.attributes) {
            if (attr.explicitLocation != null) {
                prog.attribLocations[attr.name] = attr.explicitLocation
                usedLocs.add(attr.explicitLocation)
            }
            prog.attribTypes[attr.name] = attr.typeName
        }
        var nextAutoLoc = 0
        for (attr in vsAst.attributes) {
            if (attr.explicitLocation == null) {
                while (nextAutoLoc in usedLocs) nextAutoLoc++
                prog.attribLocations[attr.name] = nextAutoLoc
                usedLocs.add(nextAutoLoc)
                nextAutoLoc++
            }
        }

        for (vOut in vsAst.varyingsOut) {
            if (vOut.name !in prog.varyingNames) {
                prog.varyingNames.add(vOut.name)
                prog.varyingTypes[vOut.name] = vOut.typeName
            }
        }
        for (vIn in fsAst.varyingsIn) {
            if (vIn.name !in prog.varyingNames) {
                prog.varyingNames.add(vIn.name)
                prog.varyingTypes[vIn.name] = vIn.typeName
            }
        }

        for (u in vsAst.uniforms + fsAst.uniforms) {
            prog.declaredUniformNames.add(u.name)
            prog.declaredUniformTypes[u.name] = u.typeName
        }
        for (b in vsAst.uniformBlocks + fsAst.uniformBlocks) {
            if (b !in prog.uniformBlockNames) {
                prog.uniformBlockNames.add(b)
            }
        }

        val outDecl = fsAst.fragOutputs.firstOrNull()
        prog.fragOutName = outDecl?.name ?: "gl_FragColor"

        // Evaluate shader const declarations
        prog.consts.clear()
        val dummyExec = ShaderExecContext(prog)
        for (c in vsAst.consts + fsAst.consts) {
            prog.consts[c.name] = evalExpr(c.expr, dummyExec, vsAst)
        }
    }

    private fun executeFunctionBody(ast: GlslShaderAst, stmts: List<GlslStmt>, exec: ShaderExecContext): GlslVal? {
        for (stmt in stmts) {
            if (exec.discarded || exec.returned) break
            execStmt(ast, stmt, exec)
        }
        return exec.returnValue
    }

    private fun execStmt(ast: GlslShaderAst, stmt: GlslStmt, exec: ShaderExecContext) {
        if (exec.discarded || exec.returned) return
        when (stmt) {
            is GlslStmt.Block -> {
                for (s in stmt.stmts) {
                    if (exec.discarded || exec.returned) break
                    execStmt(ast, s, exec)
                }
            }
            is GlslStmt.VarDecl -> {
                for ((name, initExpr) in stmt.decls) {
                    val v = if (initExpr != null) {
                        coerceValToType(evalExpr(initExpr, exec, ast), stmt.typeName)
                    } else {
                        GlslVal(dim = glslTypeDim(stmt.typeName)).also {
                            if (stmt.typeName == "mat3") it.mat = identityMat(3)
                            else if (stmt.typeName == "mat4") it.mat = identityMat(4)
                        }
                    }
                    exec.vars[name] = v
                }
            }
            is GlslStmt.ExprStmt -> {
                evalExpr(stmt.expr, exec, ast)
            }
            is GlslStmt.If -> {
                val cond = evalExpr(stmt.cond, exec, ast)
                if (cond.x != 0f) {
                    execStmt(ast, stmt.thenBranch, exec)
                } else if (stmt.elseBranch != null) {
                    execStmt(ast, stmt.elseBranch, exec)
                }
            }
            is GlslStmt.For -> {
                stmt.init?.let { execStmt(ast, it, exec) }
                var guard = 0
                while (guard < 4096) {
                    guard++
                    if (stmt.cond != null && evalExpr(stmt.cond, exec, ast).x == 0f) break
                    execStmt(ast, stmt.body, exec)
                    if (exec.discarded || exec.returned) break
                    stmt.step?.let { evalExpr(it, exec, ast) }
                }
            }
            is GlslStmt.Return -> {
                exec.returnValue = stmt.expr?.let { evalExpr(it, exec, ast) }
                exec.returned = true
            }
            GlslStmt.Discard -> {
                exec.discarded = true
            }
        }
    }

    private fun evalExpr(expr: GlslExpr, exec: ShaderExecContext, ast: GlslShaderAst): GlslVal = when (expr) {
        is GlslExpr.Literal -> GlslVal(expr.value, dim = 1)
        is GlslExpr.Ident -> resolveIdent(expr.name, exec, ast)
        is GlslExpr.Unary -> {
            val v = evalExpr(expr.operand, exec, ast)
            when (expr.op) {
                "-" -> scaleVal(v, -1f)
                "+" -> v
                "!" -> GlslVal(if (v.x == 0f) 1f else 0f, dim = 1)
                "++" -> {
                    val inc = addVals(v, GlslVal(1f, dim = 1))
                    assignTarget(expr.operand, inc, exec, ast)
                    inc
                }
                "--" -> {
                    val dec = subVals(v, GlslVal(1f, dim = 1))
                    assignTarget(expr.operand, dec, exec, ast)
                    dec
                }
                else -> v
            }
        }
        is GlslExpr.PostfixIncDec -> {
            val prev = evalExpr(expr.target, exec, ast).copy()
            val updated = if (expr.op == "++") {
                addVals(prev, GlslVal(1f, dim = 1))
            } else {
                subVals(prev, GlslVal(1f, dim = 1))
            }
            assignTarget(expr.target, updated, exec, ast)
            prev
        }
        is GlslExpr.Binary -> evalBinary(expr, exec, ast)
        is GlslExpr.Ternary -> {
            val cond = evalExpr(expr.cond, exec, ast)
            if (cond.x != 0f) evalExpr(expr.thenExpr, exec, ast) else evalExpr(expr.elseExpr, exec, ast)
        }
        is GlslExpr.Assign -> {
            val rhs = evalExpr(expr.rhs, exec, ast)
            val finalVal = when (expr.op) {
                "=" -> rhs
                "+=" -> addVals(evalExpr(expr.lhs, exec, ast), rhs)
                "-=" -> subVals(evalExpr(expr.lhs, exec, ast), rhs)
                "*=" -> mulVals(evalExpr(expr.lhs, exec, ast), rhs)
                "/=" -> divVals(evalExpr(expr.lhs, exec, ast), rhs)
                else -> rhs
            }
            assignTarget(expr.lhs, finalVal, exec, ast)
            finalVal
        }
        is GlslExpr.Member -> {
            val base = evalExpr(expr.target, exec, ast)
            readSwizzle(base, expr.member)
        }
        is GlslExpr.Index -> {
            val base = evalExpr(expr.target, exec, ast)
            val idx = evalExpr(expr.index, exec, ast).x.roundToInt()
            readIndex(base, idx)
        }
        is GlslExpr.Call -> evalCall(expr, exec, ast)
    }

    private fun resolveIdent(name: String, exec: ShaderExecContext, ast: GlslShaderAst): GlslVal {
        exec.vars[name]?.let { return it }
        exec.program.consts[name]?.let { return it }
        val uFloat = exec.program.floatUniforms[name]
        if (uFloat != null) {
            return when (uFloat.size) {
                1 -> GlslVal(uFloat[0], dim = 1)
                2 -> GlslVal(uFloat[0], uFloat[1], dim = 2)
                3 -> GlslVal(uFloat[0], uFloat[1], uFloat[2], dim = 3)
                4 -> GlslVal(uFloat[0], uFloat[1], uFloat[2], uFloat[3], dim = 4)
                9 -> GlslVal(dim = 9, mat = uFloat)
                16 -> GlslVal(dim = 16, mat = uFloat)
                else -> GlslVal(uFloat[0], dim = 1)
            }
        }
        exec.program.intUniforms[name]?.let { return GlslVal(it.toFloat(), dim = 1) }
        val declaredType = exec.program.declaredUniformTypes[name]
        if (declaredType != null) {
            return when (declaredType) {
                "mat3" -> GlslVal(dim = 9, mat = identityMat(3))
                "mat4" -> GlslVal(dim = 16, mat = identityMat(4))
                else -> GlslVal(dim = glslTypeDim(declaredType))
            }
        }
        return GlslVal(0f, dim = 1)
    }

    private fun assignTarget(target: GlslExpr, value: GlslVal, exec: ShaderExecContext, ast: GlslShaderAst) {
        when (target) {
            is GlslExpr.Ident -> {
                val existing = exec.vars[target.name]
                if (existing != null) {
                    existing.copyFromPreservingDim(value)
                } else {
                    exec.vars[target.name] = value.copy()
                }
            }
            is GlslExpr.Member -> {
                if (target.target is GlslExpr.Ident) {
                    val container = exec.vars.getOrPut(target.target.name) { GlslVal(dim = 4) }
                    writeSwizzle(container, target.member, value)
                }
            }
            is GlslExpr.Index -> {
                if (target.target is GlslExpr.Ident) {
                    val container = exec.vars[target.target.name] ?: return
                    val idx = evalExpr(target.index, exec, ast).x.roundToInt()
                    writeIndex(container, idx, value)
                }
            }
            else -> {}
        }
    }

    private fun evalBinary(expr: GlslExpr.Binary, exec: ShaderExecContext, ast: GlslShaderAst): GlslVal {
        if (expr.op == "&&") {
            val l = evalExpr(expr.left, exec, ast)
            if (l.x == 0f) return GlslVal(0f, dim = 1)
            val r = evalExpr(expr.right, exec, ast)
            return GlslVal(if (r.x != 0f) 1f else 0f, dim = 1)
        }
        if (expr.op == "||") {
            val l = evalExpr(expr.left, exec, ast)
            if (l.x != 0f) return GlslVal(1f, dim = 1)
            val r = evalExpr(expr.right, exec, ast)
            return GlslVal(if (r.x != 0f) 1f else 0f, dim = 1)
        }
        val l = evalExpr(expr.left, exec, ast)
        val r = evalExpr(expr.right, exec, ast)
        return when (expr.op) {
            "+" -> addVals(l, r)
            "-" -> subVals(l, r)
            "*" -> mulVals(l, r)
            "/" -> divVals(l, r)
            "%" -> {
                val dv = if (abs(r.x) < 1e-8f) 1f else r.x
                GlslVal(l.x - dv * floor(l.x / dv), dim = 1)
            }
            "<" -> GlslVal(if (l.x < r.x) 1f else 0f, dim = 1)
            "<=" -> GlslVal(if (l.x <= r.x) 1f else 0f, dim = 1)
            ">" -> GlslVal(if (l.x > r.x) 1f else 0f, dim = 1)
            ">=" -> GlslVal(if (l.x >= r.x) 1f else 0f, dim = 1)
            "==" -> GlslVal(if (valsEqual(l, r)) 1f else 0f, dim = 1)
            "!=" -> GlslVal(if (!valsEqual(l, r)) 1f else 0f, dim = 1)
            else -> GlslVal(0f, dim = 1)
        }
    }

    private fun evalCall(expr: GlslExpr.Call, exec: ShaderExecContext, ast: GlslShaderAst): GlslVal {
        val name = expr.name
        val args = expr.args

        // User-defined GLSL function lookup
        ast.functions[name]?.let { fnDef ->
            val savedVars = HashMap<String, GlslVal>(exec.vars)
            val savedRet = exec.returned
            val savedRetVal = exec.returnValue
            for (i in fnDef.params.indices) {
                val argVal = if (i < args.size) evalExpr(args[i], exec, ast).copy() else GlslVal(0f, dim = 1)
                exec.vars[fnDef.params[i].second] = argVal
            }
            exec.returned = false
            exec.returnValue = null
            val ret = executeFunctionBody(ast, fnDef.body, exec) ?: GlslVal(0f, dim = 1)
            val result = ret.copy()
            exec.vars.clear()
            exec.vars.putAll(savedVars)
            exec.returned = savedRet
            exec.returnValue = savedRetVal
            return result
        }

        if (name == "texture" || name == "texture2D") {
            val unit = if (args.isNotEmpty() && args[0] is GlslExpr.Ident) {
                val uName = (args[0] as GlslExpr.Ident).name
                exec.program.intUniforms[uName] ?: 0
            } else if (args.isNotEmpty()) {
                evalExpr(args[0], exec, ast).x.roundToInt()
            } else {
                0
            }
            val uv = if (args.size >= 2) evalExpr(args[1], exec, ast) else GlslVal(0f, 0f, dim = 2)
            return sampleTexture(unit, uv.x, uv.y)
        }

        if (name == "textureSize") {
            val unit = if (args.isNotEmpty() && args[0] is GlslExpr.Ident) {
                val uName = (args[0] as GlslExpr.Ident).name
                exec.program.intUniforms[uName] ?: 0
            } else 0
            val texId = boundTexturesByUnit[unit]?.get(GLTextureTarget.TEXTURE_2D)
            val tex = texId?.let { textures[it] }
            return GlslVal((tex?.width ?: 1).toFloat(), (tex?.height ?: 1).toFloat(), dim = 2)
        }

        val evaluated = args.map { evalExpr(it, exec, ast) }
        return when (name) {
            "float" -> GlslVal(evaluated.firstOrNull()?.x ?: 0f, dim = 1)
            "int" -> GlslVal((evaluated.firstOrNull()?.x ?: 0f).toInt().toFloat(), dim = 1)
            "bool" -> GlslVal(if ((evaluated.firstOrNull()?.x ?: 0f) != 0f) 1f else 0f, dim = 1)
            "vec2", "ivec2", "bvec2" -> constructVec(evaluated, 2)
            "vec3", "ivec3", "bvec3" -> constructVec(evaluated, 3)
            "vec4", "ivec4", "bvec4" -> constructVec(evaluated, 4)
            "mat3" -> constructMat(evaluated, 3)
            "mat4" -> constructMat(evaluated, 4)
            "clamp" -> {
                val x = evaluated[0]; val lo = evaluated[1]; val hi = evaluated[2]
                map3(x, lo, hi) { a, b, c -> a.coerceIn(min(b, c), max(b, c)) }
            }
            "min" -> map2(evaluated[0], evaluated[1]) { a, b -> min(a, b) }
            "max" -> map2(evaluated[0], evaluated[1]) { a, b -> max(a, b) }
            "mix" -> {
                val x = evaluated[0]; val y = evaluated[1]; val t = evaluated[2]
                map3(x, y, t) { a, b, k -> a + (b - a) * k }
            }
            "step" -> {
                val edge = evaluated[0]; val x = evaluated[1]
                map2SameDim(x, edge) { xv, ev -> if (xv < ev) 0f else 1f }
            }
            "smoothstep" -> {
                val e0 = evaluated[0]; val e1 = evaluated[1]; val x = evaluated[2]
                map3(x, e0, e1) { xv, lo, hi ->
                    val span = hi - lo
                    val u = if (abs(span) < 1e-8f) 0f else ((xv - lo) / span).coerceIn(0f, 1f)
                    u * u * (3f - 2f * u)
                }
            }
            "dot" -> {
                val a = evaluated[0]; val b = evaluated[1]
                val d = max(a.dim, b.dim).coerceAtMost(4)
                var sum = a.x * b.x
                if (d >= 2) sum += a.y * b.y
                if (d >= 3) sum += a.z * b.z
                if (d >= 4) sum += a.w * b.w
                GlslVal(sum, dim = 1)
            }
            "cross" -> {
                val a = evaluated[0]; val b = evaluated[1]
                GlslVal(
                    x = a.y * b.z - a.z * b.y,
                    y = a.z * b.x - a.x * b.z,
                    z = a.x * b.y - a.y * b.x,
                    dim = 3
                )
            }
            "length" -> {
                val a = evaluated[0]
                GlslVal(sqrt(dotSelf(a)), dim = 1)
            }
            "distance" -> {
                val diff = subVals(evaluated[0], evaluated[1])
                GlslVal(sqrt(dotSelf(diff)), dim = 1)
            }
            "normalize" -> {
                val a = evaluated[0]
                val len = sqrt(dotSelf(a))
                if (len > 1e-8f) scaleVal(a, 1f / len) else a.copy()
            }
            "reflect" -> {
                val i = evaluated[0]; val n = evaluated[1]
                val k = 2f * evalDot(n, i)
                subVals(i, scaleVal(n, k))
            }
            "refract" -> {
                val i = evaluated[0]; val n = evaluated[1]; val eta = evaluated[2].x
                val ndi = evalDot(n, i)
                val k = 1f - eta * eta * (1f - ndi * ndi)
                if (k < 0f) GlslVal(dim = i.dim)
                else subVals(scaleVal(i, eta), scaleVal(n, eta * ndi + sqrt(k)))
            }
            "abs" -> map1(evaluated[0]) { abs(it) }
            "sign" -> map1(evaluated[0]) { sign(it) }
            "floor" -> map1(evaluated[0]) { floor(it) }
            "ceil" -> map1(evaluated[0]) { ceil(it) }
            "round" -> map1(evaluated[0]) { round(it) }
            "fract" -> map1(evaluated[0]) { it - floor(it) }
            "mod" -> map2(evaluated[0], evaluated[1]) { a, b ->
                val dv = if (abs(b) < 1e-8f) 1f else b
                a - dv * floor(a / dv)
            }
            "pow" -> map2(evaluated[0], evaluated[1]) { a, b -> a.pow(b) }
            "exp" -> map1(evaluated[0]) { exp(it) }
            "log" -> map1(evaluated[0]) { ln(max(it, 1e-8f)) }
            "sqrt" -> map1(evaluated[0]) { sqrt(max(it, 0f)) }
            "inversesqrt" -> map1(evaluated[0]) {
                val s = sqrt(max(it, 1e-8f))
                1f / s
            }
            "sin" -> map1(evaluated[0]) { sin(it) }
            "cos" -> map1(evaluated[0]) { cos(it) }
            "tan" -> map1(evaluated[0]) { tan(it) }
            "asin" -> map1(evaluated[0]) { asin(it.coerceIn(-1f, 1f)) }
            "acos" -> map1(evaluated[0]) { acos(it.coerceIn(-1f, 1f)) }
            "atan" -> if (evaluated.size >= 2) {
                map2(evaluated[0], evaluated[1]) { y, x -> atan2(y, x) }
            } else {
                map1(evaluated[0]) { atan(it) }
            }
            "radians" -> map1(evaluated[0]) { (it * PI / 180.0).toFloat() }
            "degrees" -> map1(evaluated[0]) { (it * 180.0 / PI).toFloat() }
            "dFdx", "dFdy", "fwidth" -> GlslVal(dim = evaluated.firstOrNull()?.dim ?: 1)
            else -> evaluated.firstOrNull()?.copy() ?: GlslVal(0f, dim = 1)
        }
    }

    private fun evalDot(a: GlslVal, b: GlslVal): Float {
        val d = max(a.dim, b.dim).coerceAtMost(4)
        var s = a.x * b.x
        if (d >= 2) s += a.y * b.y
        if (d >= 3) s += a.z * b.z
        if (d >= 4) s += a.w * b.w
        return s
    }

    private fun dotSelf(a: GlslVal): Float = when (a.dim) {
        1 -> a.x * a.x
        2 -> a.x * a.x + a.y * a.y
        3 -> a.x * a.x + a.y * a.y + a.z * a.z
        else -> a.x * a.x + a.y * a.y + a.z * a.z + a.w * a.w
    }

    private fun constructVec(args: List<GlslVal>, dim: Int): GlslVal {
        if (args.size == 1 && args[0].dim == 1) {
            val s = args[0].x
            return GlslVal(s, if (dim >= 2) s else 0f, if (dim >= 3) s else 0f, if (dim >= 4) s else 0f, dim = dim)
        }
        val flat = FloatArray(4)
        var outIdx = 0
        for (a in args) {
            val count = a.dim.coerceIn(1, 4)
            for (c in 0 until count) {
                if (outIdx >= dim) break
                flat[outIdx++] = a.getComponent(c)
            }
            if (outIdx >= dim) break
        }
        return GlslVal(flat[0], flat[1], flat[2], flat[3], dim = dim)
    }

    private fun constructMat(args: List<GlslVal>, n: Int): GlslVal {
        val out = FloatArray(n * n)
        if (args.size == 1 && args[0].dim == 1) {
            val diag = args[0].x
            for (i in 0 until n) out[i * n + i] = diag
            return GlslVal(dim = n * n, mat = out)
        }
        if (args.size == 1 && args[0].mat != null) {
            val src = args[0].mat!!
            val srcN = if (src.size == 16) 4 else 3
            for (c in 0 until n) {
                for (r in 0 until n) {
                    out[c * n + r] = if (c < srcN && r < srcN) src[c * srcN + r] else if (c == r) 1f else 0f
                }
            }
            return GlslVal(dim = n * n, mat = out)
        }
        var idx = 0
        for (a in args) {
            if (a.mat != null) {
                for (v in a.mat!!) {
                    if (idx < out.size) out[idx++] = v
                }
            } else {
                for (c in 0 until a.dim.coerceIn(1, 4)) {
                    if (idx < out.size) out[idx++] = a.getComponent(c)
                }
            }
        }
        return GlslVal(dim = n * n, mat = out)
    }

    private fun coerceValToType(v: GlslVal, typeName: String): GlslVal {
        val targetDim = glslTypeDim(typeName)
        if (targetDim == v.dim) return v.copy()
        if (v.dim == 1 && targetDim in 2..4) {
            return GlslVal(v.x, v.x, if (targetDim >= 3) v.x else 0f, if (targetDim >= 4) v.x else 0f, dim = targetDim)
        }
        return v.copy(dim = targetDim)
    }

    private fun addVals(a: GlslVal, b: GlslVal): GlslVal = map2(a, b) { x, y -> x + y }
    private fun subVals(a: GlslVal, b: GlslVal): GlslVal = map2(a, b) { x, y -> x - y }
    private fun divVals(a: GlslVal, b: GlslVal): GlslVal = map2(a, b) { x, y ->
        if (abs(y) < 1e-8f) 0f else x / y
    }

    private fun mulVals(a: GlslVal, b: GlslVal): GlslVal {
        if (a.mat != null && b.mat == null && b.dim in 3..4) {
            val m = a.mat!!
            return if (a.dim == 16) {
                val vx = b.x; val vy = b.y; val vz = b.z; val vw = if (b.dim >= 4) b.w else 1f
                GlslVal(
                    x = m[0] * vx + m[4] * vy + m[8] * vz + m[12] * vw,
                    y = m[1] * vx + m[5] * vy + m[9] * vz + m[13] * vw,
                    z = m[2] * vx + m[6] * vy + m[10] * vz + m[14] * vw,
                    w = m[3] * vx + m[7] * vy + m[11] * vz + m[15] * vw,
                    dim = 4
                )
            } else {
                val vx = b.x; val vy = b.y; val vz = b.z
                GlslVal(
                    x = m[0] * vx + m[3] * vy + m[6] * vz,
                    y = m[1] * vx + m[4] * vy + m[7] * vz,
                    z = m[2] * vx + m[5] * vy + m[8] * vz,
                    dim = 3
                )
            }
        }
        if (a.mat != null && b.mat != null) {
            val n = if (a.dim == 16 && b.dim == 16) 4 else 3
            val ma = a.mat!!
            val mb = b.mat!!
            val out = FloatArray(n * n)
            for (col in 0 until n) {
                for (row in 0 until n) {
                    var sum = 0f
                    for (k in 0 until n) {
                        sum += ma[k * n + row] * mb[col * n + k]
                    }
                    out[col * n + row] = sum
                }
            }
            return GlslVal(dim = n * n, mat = out)
        }
        return map2(a, b) { x, y -> x * y }
    }

    private fun scaleVal(v: GlslVal, s: Float): GlslVal {
        if (v.mat != null) {
            return GlslVal(dim = v.dim, mat = FloatArray(v.mat!!.size) { v.mat!![it] * s })
        }
        return GlslVal(v.x * s, v.y * s, v.z * s, v.w * s, dim = v.dim)
    }

    private inline fun map1(a: GlslVal, fn: (Float) -> Float): GlslVal = GlslVal(
        x = fn(a.x),
        y = if (a.dim >= 2) fn(a.y) else 0f,
        z = if (a.dim >= 3) fn(a.z) else 0f,
        w = if (a.dim >= 4) fn(a.w) else 0f,
        dim = a.dim
    )

    private inline fun map2(a: GlslVal, b: GlslVal, fn: (Float, Float) -> Float): GlslVal {
        val d = max(a.dim, b.dim).coerceIn(1, 4)
        return GlslVal(
            x = fn(a.x, b.x),
            y = if (d >= 2) fn(if (a.dim == 1) a.x else a.y, if (b.dim == 1) b.x else b.y) else 0f,
            z = if (d >= 3) fn(if (a.dim == 1) a.x else a.z, if (b.dim == 1) b.x else b.z) else 0f,
            w = if (d >= 4) fn(if (a.dim == 1) a.x else a.w, if (b.dim == 1) b.x else b.w) else 0f,
            dim = d
        )
    }

    private inline fun map2SameDim(x: GlslVal, other: GlslVal, fn: (Float, Float) -> Float): GlslVal =
        map2(x, other, fn)

    private inline fun map3(a: GlslVal, b: GlslVal, c: GlslVal, fn: (Float, Float, Float) -> Float): GlslVal {
        val d = max(a.dim, max(b.dim, c.dim)).coerceIn(1, 4)
        return GlslVal(
            x = fn(a.x, b.x, c.x),
            y = if (d >= 2) fn(if (a.dim == 1) a.x else a.y, if (b.dim == 1) b.x else b.y, if (c.dim == 1) c.x else c.y) else 0f,
            z = if (d >= 3) fn(if (a.dim == 1) a.x else a.z, if (b.dim == 1) b.x else b.z, if (c.dim == 1) c.x else c.z) else 0f,
            w = if (d >= 4) fn(if (a.dim == 1) a.x else a.w, if (b.dim == 1) b.x else b.w, if (c.dim == 1) c.x else c.w) else 0f,
            dim = d
        )
    }

    private fun valsEqual(a: GlslVal, b: GlslVal): Boolean {
        val d = max(a.dim, b.dim).coerceIn(1, 4)
        for (i in 0 until d) {
            val av = if (a.dim == 1) a.x else a.getComponent(i)
            val bv = if (b.dim == 1) b.x else b.getComponent(i)
            if (abs(av - bv) > 1e-6f) return false
        }
        return true
    }

    private fun swizzleSlot(ch: Char): Int = when (ch) {
        'x', 'r', 's' -> 0
        'y', 'g', 't' -> 1
        'z', 'b', 'p' -> 2
        'w', 'a', 'q' -> 3
        else -> 0
    }

    private fun readSwizzle(v: GlslVal, member: String): GlslVal {
        val len = member.length.coerceIn(1, 4)
        val comps = FloatArray(4)
        for (i in 0 until len) {
            comps[i] = v.getComponent(swizzleSlot(member[i]))
        }
        return GlslVal(comps[0], comps[1], comps[2], comps[3], dim = len)
    }

    private fun writeSwizzle(v: GlslVal, member: String, value: GlslVal) {
        for (i in member.indices) {
            if (i >= 4) break
            val slot = swizzleSlot(member[i])
            val srcVal = if (value.dim == 1) value.x else value.getComponent(i)
            v.setComponent(slot, srcVal)
        }
    }

    private fun readIndex(base: GlslVal, idx: Int): GlslVal {
        val m = base.mat
        if (m != null) {
            val n = if (m.size == 16) 4 else 3
            val c = idx.coerceIn(0, n - 1) * n
            return GlslVal(m[c], m[c + 1], m[c + 2], if (n == 4) m[c + 3] else 0f, dim = n)
        }
        return GlslVal(base.getComponent(idx.coerceIn(0, 3)), dim = 1)
    }

    private fun writeIndex(base: GlslVal, idx: Int, value: GlslVal) {
        val m = base.mat
        if (m != null) {
            val n = if (m.size == 16) 4 else 3
            val c = idx.coerceIn(0, n - 1) * n
            for (r in 0 until n) m[c + r] = value.getComponent(r)
            return
        }
        base.setComponent(idx.coerceIn(0, 3), value.x)
    }

    // -------------------------------------------------------------------------
    // Preprocessor, Tokenizer, and Parser
    // -------------------------------------------------------------------------

    private fun preprocessGlsl(source: String): String {
        val noBlock = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            if (i + 1 < source.length && source[i] == '/' && source[i + 1] == '*') {
                val close = source.indexOf("*/", i + 2)
                i = if (close < 0) source.length else close + 2
            } else if (i + 1 < source.length && source[i] == '/' && source[i + 1] == '/') {
                val nl = source.indexOf('\n', i + 2)
                i = if (nl < 0) source.length else nl
            } else {
                noBlock.append(source[i])
                i++
            }
        }
        val defines = LinkedHashMap<String, String>()
        val lines = noBlock.toString().lines()
        val kept = StringBuilder()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#")) {
                if (trimmed.startsWith("#define ")) {
                    val rest = trimmed.removePrefix("#define ").trim()
                    val sp = rest.indexOfFirst { it.isWhitespace() }
                    if (sp > 0) {
                        val macroName = rest.substring(0, sp)
                        val macroVal = rest.substring(sp + 1).trim()
                        if ('(' !in macroName && macroVal.isNotEmpty()) {
                            defines[macroName] = macroVal
                        }
                    }
                }
                continue
            }
            if (trimmed.startsWith("precision ") && trimmed.endsWith(";")) continue
            kept.append(line).append('\n')
        }
        var out = kept.toString()
        for ((k, v) in defines) {
            out = out.replace(Regex("\\b${Regex.escape(k)}\\b"), v)
        }
        return out
    }

    private fun tokenizeGlsl(src: String): List<Tok> {
        val tokens = ArrayList<Tok>()
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            if (c.isWhitespace()) {
                i++
                continue
            }
            if (c.isDigit() || (c == '.' && i + 1 < n && src[i + 1].isDigit())) {
                val start = i
                var seenDot = (c == '.')
                i++
                while (i < n) {
                    val ch = src[i]
                    if (ch.isDigit()) {
                        i++
                    } else if (ch == '.' && !seenDot) {
                        seenDot = true
                        i++
                    } else if ((ch == 'e' || ch == 'E') && i + 1 < n) {
                        i++
                        if (i < n && (src[i] == '+' || src[i] == '-')) i++
                    } else {
                        break
                    }
                }
                if (i < n && (src[i] == 'f' || src[i] == 'F' || src[i] == 'u' || src[i] == 'U')) i++
                val text = src.substring(start, i)
                val cleanNum = text.trimEnd('f', 'F', 'u', 'U')
                val num = cleanNum.toFloatOrNull() ?: 0f
                tokens.add(Tok(TokKind.NUM, text, num))
                continue
            }
            if (c.isLetter() || c == '_') {
                val start = i
                i++
                while (i < n && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                val word = src.substring(start, i)
                tokens.add(Tok(TokKind.IDENT, word))
                continue
            }
            if (i + 1 < n) {
                val two = src.substring(i, i + 2)
                if (two in TWO_CHAR_OPS) {
                    tokens.add(Tok(TokKind.SYM, two))
                    i += 2
                    continue
                }
            }
            tokens.add(Tok(TokKind.SYM, c.toString()))
            i++
        }
        tokens.add(Tok(TokKind.EOF, ""))
        return tokens
    }

    private inner class GlslParser(
        private val tokens: List<Tok>,
        private val shaderType: ShaderType
    ) {
        private var pos: Int = 0
        var errorMessage: String? = null

        private fun peek(offset: Int = 0): Tok =
            tokens[(pos + offset).coerceIn(0, tokens.size - 1)]

        private fun match(text: String): Boolean {
            if (peek().text == text) {
                pos++
                return true
            }
            return false
        }

        private fun expect(text: String): Boolean {
            if (match(text)) return true
            if (errorMessage == null) {
                errorMessage = "GLSL syntax error: expected '$text' near '${peek().text}'"
            }
            return false
        }

        fun parseShader(): GlslShaderAst {
            val ast = GlslShaderAst()
            while (peek().kind != TokKind.EOF && errorMessage == null) {
                if (peek().text == "layout") {
                    pos++
                    var locNum: Int? = null
                    var blockBinding: String? = null
                    if (match("(")) {
                        while (peek().text != ")" && peek().kind != TokKind.EOF) {
                            val k = peek().text
                            pos++
                            if (match("=")) {
                                if (k == "location" && peek().kind == TokKind.NUM) {
                                    locNum = peek().numValue.roundToInt()
                                    pos++
                                } else {
                                    blockBinding = peek().text
                                    pos++
                                }
                            }
                            match(",")
                        }
                        expect(")")
                    }
                    parseTopLevelDecl(ast, explicitLocation = locNum, layoutTag = blockBinding)
                } else {
                    parseTopLevelDecl(ast, explicitLocation = null, layoutTag = null)
                }
            }
            return ast
        }

        private fun skipPrecisionAndQualifiers(): MutableList<String> {
            val quals = mutableListOf<String>()
            while (peek().text in STORAGE_OR_PRECISION_QUALIFIERS) {
                quals.add(peek().text)
                pos++
            }
            return quals
        }

        private fun parseTopLevelDecl(
            ast: GlslShaderAst,
            explicitLocation: Int?,
            layoutTag: String?
        ) {
            val quals = skipPrecisionAndQualifiers()
            if ("precision" in quals) {
                while (peek().text != ";" && peek().kind != TokKind.EOF) pos++
                match(";")
                return
            }
            if (peek().text == ";") {
                pos++
                return
            }
            if (peek().text == "struct") {
                pos++
                if (peek().kind == TokKind.IDENT) pos++
                if (match("{")) {
                    var depth = 1
                    while (depth > 0 && peek().kind != TokKind.EOF) {
                        if (match("{")) depth++
                        else if (match("}")) depth--
                        else pos++
                    }
                }
                match(";")
                return
            }

            // Uniform block: `uniform BlockName { ... } optionalVar;`
            if ("uniform" in quals && peek().kind == TokKind.IDENT && peek(1).text == "{") {
                val blockName = peek().text
                ast.uniformBlocks.add(blockName)
                pos++
                match("{")
                var depth = 1
                while (depth > 0 && peek().kind != TokKind.EOF) {
                    if (match("{")) depth++
                    else if (match("}")) depth--
                    else pos++
                }
                if (peek().kind == TokKind.IDENT) pos++
                match(";")
                return
            }

            if (peek().kind != TokKind.IDENT) {
                errorMessage = "GLSL syntax error: expected type or declaration near '${peek().text}'"
                return
            }
            val typeName = peek().text
            pos++
            if (peek().kind != TokKind.IDENT) {
                errorMessage = "GLSL syntax error: expected identifier after type '$typeName'"
                return
            }
            val name = peek().text
            pos++

            if (match("(")) {
                // Function declaration / definition
                val params = mutableListOf<Pair<String, String>>()
                if (!match(")")) {
                    while (peek().kind != TokKind.EOF) {
                        while (peek().text in PARAM_QUALIFIERS) pos++
                        val pType = peek().text
                        pos++
                        val pName = if (peek().kind == TokKind.IDENT) peek().text.also { pos++ } else "_"
                        if (match("[")) {
                            while (peek().text != "]" && peek().kind != TokKind.EOF) pos++
                            match("]")
                        }
                        params.add(pType to pName)
                        if (!match(",")) break
                    }
                    expect(")")
                }
                if (match(";")) return
                expect("{")
                val body = parseBlockStmts()
                if (name == "main") {
                    ast.mainStmts = body
                    ast.hasMain = true
                } else {
                    ast.functions[name] = GlslFunctionDef(name, typeName, params, body)
                }
                return
            }

            // Variable / qualifier declaration (can declare multiple comma-separated names)
            parseVarRestAndRegister(ast, quals, typeName, name, explicitLocation)
        }

        private fun parseVarRestAndRegister(
            ast: GlslShaderAst,
            quals: List<String>,
            typeName: String,
            firstName: String,
            explicitLocation: Int?
        ) {
            var curName = firstName
            var curLoc = explicitLocation
            while (true) {
                if (match("[")) {
                    while (peek().text != "]" && peek().kind != TokKind.EOF) pos++
                    match("]")
                }
                val initExpr = if (match("=")) parseExpr() else null
                registerTopVar(ast, quals, typeName, curName, curLoc, initExpr)
                if (!match(",")) break
                if (peek().kind != TokKind.IDENT) break
                curName = peek().text
                curLoc = null
                pos++
            }
            expect(";")
        }

        private fun registerTopVar(
            ast: GlslShaderAst,
            quals: List<String>,
            typeName: String,
            name: String,
            explicitLocation: Int?,
            initExpr: GlslExpr?
        ) {
            val isUniform = "uniform" in quals
            val isConst = "const" in quals
            val isAttribute = "attribute" in quals || ("in" in quals && shaderType == ShaderType.VERTEX)
            val isVaryingOut = ("out" in quals && shaderType == ShaderType.VERTEX) ||
                ("varying" in quals && shaderType == ShaderType.VERTEX)
            val isVaryingIn = ("in" in quals && shaderType == ShaderType.FRAGMENT) ||
                ("varying" in quals && shaderType == ShaderType.FRAGMENT)
            val isFragOut = "out" in quals && shaderType == ShaderType.FRAGMENT

            when {
                isUniform -> ast.uniforms.add(GlslDecl(name, typeName, explicitLocation))
                isConst && initExpr != null -> ast.consts.add(GlslConstDecl(name, typeName, initExpr))
                isAttribute -> ast.attributes.add(GlslDecl(name, typeName, explicitLocation))
                isVaryingOut -> ast.varyingsOut.add(GlslDecl(name, typeName, explicitLocation))
                isVaryingIn -> ast.varyingsIn.add(GlslDecl(name, typeName, explicitLocation))
                isFragOut -> ast.fragOutputs.add(GlslDecl(name, typeName, explicitLocation))
            }
        }

        private fun parseBlockStmts(): List<GlslStmt> {
            val list = ArrayList<GlslStmt>()
            while (peek().text != "}" && peek().kind != TokKind.EOF && errorMessage == null) {
                list.add(parseStmt())
            }
            expect("}")
            return list
        }

        private fun parseStmt(): GlslStmt {
            if (match("{")) {
                return GlslStmt.Block(parseBlockStmts())
            }
            if (match(";")) {
                return GlslStmt.Block(emptyList())
            }
            if (match("discard")) {
                expect(";")
                return GlslStmt.Discard
            }
            if (match("return")) {
                val e = if (peek().text == ";") null else parseExpr()
                expect(";")
                return GlslStmt.Return(e)
            }
            if (match("if")) {
                expect("(")
                val cond = parseExpr()
                expect(")")
                val thenBranch = parseStmt()
                val elseBranch = if (match("else")) parseStmt() else null
                return GlslStmt.If(cond, thenBranch, elseBranch)
            }
            if (match("for")) {
                expect("(")
                val init = if (match(";")) null else parseStmt()
                val cond = if (peek().text == ";") null else parseExpr()
                expect(";")
                val step = if (peek().text == ")") null else parseExpr()
                expect(")")
                val body = parseStmt()
                return GlslStmt.For(init, cond, step, body)
            }
            // Local variable declaration (`const TYPE ...` or `TYPE name ...`)
            val looksLikeDecl = if (peek().text == "const") {
                true
            } else {
                peek().text in GLSL_VALUE_TYPES && peek(1).kind == TokKind.IDENT
            }
            if (looksLikeDecl) {
                if (peek().text == "const") pos++
                val typeName = peek().text
                pos++
                val decls = mutableListOf<Pair<String, GlslExpr?>>()
                while (peek().kind == TokKind.IDENT) {
                    val vName = peek().text
                    pos++
                    if (match("[")) {
                        while (peek().text != "]" && peek().kind != TokKind.EOF) pos++
                        match("]")
                    }
                    val init = if (match("=")) parseExpr() else null
                    decls.add(vName to init)
                    if (!match(",")) break
                }
                expect(";")
                return GlslStmt.VarDecl(typeName, decls)
            }
            val expr = parseExpr()
            expect(";")
            return GlslStmt.ExprStmt(expr)
        }

        private fun parseExpr(): GlslExpr = parseAssign()

        private fun parseAssign(): GlslExpr {
            val left = parseTernary()
            val op = peek().text
            if (op == "=" || op == "+=" || op == "-=" || op == "*=" || op == "/=") {
                pos++
                val right = parseAssign()
                return GlslExpr.Assign(op, left, right)
            }
            return left
        }

        private fun parseTernary(): GlslExpr {
            val cond = parseOr()
            if (match("?")) {
                val tExpr = parseExpr()
                expect(":")
                val fExpr = parseTernary()
                return GlslExpr.Ternary(cond, tExpr, fExpr)
            }
            return cond
        }

        private fun parseOr(): GlslExpr {
            var left = parseAnd()
            while (match("||")) {
                left = GlslExpr.Binary("||", left, parseAnd())
            }
            return left
        }

        private fun parseAnd(): GlslExpr {
            var left = parseEq()
            while (match("&&")) {
                left = GlslExpr.Binary("&&", left, parseEq())
            }
            return left
        }

        private fun parseEq(): GlslExpr {
            var left = parseRel()
            while (peek().text == "==" || peek().text == "!=") {
                val op = peek().text
                pos++
                left = GlslExpr.Binary(op, left, parseRel())
            }
            return left
        }

        private fun parseRel(): GlslExpr {
            var left = parseAdd()
            while (peek().text == "<" || peek().text == "<=" || peek().text == ">" || peek().text == ">=") {
                val op = peek().text
                pos++
                left = GlslExpr.Binary(op, left, parseAdd())
            }
            return left
        }

        private fun parseAdd(): GlslExpr {
            var left = parseMul()
            while (peek().text == "+" || peek().text == "-") {
                val op = peek().text
                pos++
                left = GlslExpr.Binary(op, left, parseMul())
            }
            return left
        }

        private fun parseMul(): GlslExpr {
            var left = parseUnary()
            while (peek().text == "*" || peek().text == "/" || peek().text == "%") {
                val op = peek().text
                pos++
                left = GlslExpr.Binary(op, left, parseUnary())
            }
            return left
        }

        private fun parseUnary(): GlslExpr {
            val op = peek().text
            if (op == "-" || op == "+" || op == "!" || op == "++" || op == "--") {
                pos++
                return GlslExpr.Unary(op, parseUnary())
            }
            return parsePostfix()
        }

        private fun parsePostfix(): GlslExpr {
            var expr = parsePrimary()
            while (true) {
                when {
                    match(".") -> {
                        val member = peek().text
                        pos++
                        expr = GlslExpr.Member(expr, member)
                    }
                    match("[") -> {
                        val idx = parseExpr()
                        expect("]")
                        expr = GlslExpr.Index(expr, idx)
                    }
                    peek().text == "++" || peek().text == "--" -> {
                        val op = peek().text
                        pos++
                        expr = GlslExpr.PostfixIncDec(op, expr)
                    }
                    else -> break
                }
            }
            return expr
        }

        private fun parsePrimary(): GlslExpr {
            val tok = peek()
            if (tok.kind == TokKind.NUM) {
                pos++
                return GlslExpr.Literal(tok.numValue)
            }
            if (tok.text == "true") {
                pos++
                return GlslExpr.Literal(1f)
            }
            if (tok.text == "false") {
                pos++
                return GlslExpr.Literal(0f)
            }
            if (match("(")) {
                val inner = parseExpr()
                expect(")")
                return inner
            }
            if (tok.kind == TokKind.IDENT) {
                pos++
                if (match("(")) {
                    val args = mutableListOf<GlslExpr>()
                    if (!match(")")) {
                        while (peek().kind != TokKind.EOF) {
                            args.add(parseExpr())
                            if (!match(",")) break
                        }
                        expect(")")
                    }
                    return GlslExpr.Call(tok.text, args)
                }
                return GlslExpr.Ident(tok.text)
            }
            errorMessage = "GLSL syntax error: unexpected token '${tok.text}'"
            pos++
            return GlslExpr.Literal(0f)
        }
    }

    // -------------------------------------------------------------------------
    // Internal Data Models (strictly private — 1 public type in file)
    // -------------------------------------------------------------------------

    private class GlslVal(
        var x: Float = 0f,
        var y: Float = 0f,
        var z: Float = 0f,
        var w: Float = 0f,
        val dim: Int = 1,
        var mat: FloatArray? = null
    ) {
        fun getComponent(index: Int): Float = when (index) {
            0 -> x
            1 -> y
            2 -> z
            else -> w
        }

        fun setComponent(index: Int, value: Float) {
            when (index) {
                0 -> x = value
                1 -> y = value
                2 -> z = value
                else -> w = value
            }
        }

        fun copy(dim: Int = this.dim): GlslVal =
            GlslVal(x, y, z, w, dim = dim, mat = mat?.copyOf())

        fun copyFromPreservingDim(other: GlslVal) {
            if (mat != null && other.mat != null) {
                other.mat!!.copyInto(mat!!, 0, 0, min(mat!!.size, other.mat!!.size))
                return
            }
            x = other.x
            if (dim >= 2) y = if (other.dim == 1) other.x else other.y
            if (dim >= 3) z = if (other.dim == 1) other.x else other.z
            if (dim >= 4) w = if (other.dim == 1) other.x else other.w
        }
    }

    private class ShaderExecContext(val program: ProgramRecord) {
        val vars = HashMap<String, GlslVal>(32)
        var discarded: Boolean = false
        var returned: Boolean = false
        var returnValue: GlslVal? = null

        fun resetForVertex(vertexId: Int, instanceId: Int) {
            vars.clear()
            discarded = false
            returned = false
            returnValue = null
            vars["gl_VertexID"] = GlslVal(vertexId.toFloat(), dim = 1)
            vars["gl_InstanceID"] = GlslVal(instanceId.toFloat(), dim = 1)
        }

        fun resetForFragment(fragX: Float, fragY: Float, fragZ: Float, fragW: Float, isFrontFacing: Boolean) {
            vars.clear()
            discarded = false
            returned = false
            returnValue = null
            vars["gl_FragCoord"] = GlslVal(fragX, fragY, fragZ, fragW, dim = 4)
            vars["gl_FrontFacing"] = GlslVal(if (isFrontFacing) 1f else 0f, dim = 1)
        }
    }

    private class ShaderRecord(
        val id: Int,
        val type: ShaderType,
        var source: String,
        var ast: GlslShaderAst? = null,
        var compiled: Boolean = false,
        var compileStatus: GLCompileStatus = GLCompileStatus(true),
        var hardwareShader: GLShader? = null
    )

    private class ProgramRecord(val id: Int) {
        var vertexShaderId: Int? = null
        var fragmentShaderId: Int? = null
        var linkStatus: GLCompileStatus = GLCompileStatus(false)
        var hardwareProgram: GLProgram? = null
        val hwUniformByWrapperId = mutableMapOf<Int, GLUniformLocation>()

        val attribLocations = LinkedHashMap<String, Int>()
        val attribTypes = HashMap<String, String>()
        val varyingNames = mutableListOf<String>()
        val varyingTypes = HashMap<String, String>()
        val declaredUniformNames = mutableSetOf<String>()
        val declaredUniformTypes = HashMap<String, String>()
        val uniformLocations = mutableMapOf<String, GLUniformLocation>()
        val uniformBlockNames = mutableListOf<String>()
        val uniformBlockBindings = mutableMapOf<Int, Int>()
        val floatUniforms = mutableMapOf<String, FloatArray>()
        val intUniforms = mutableMapOf<String, Int>()
        val consts = mutableMapOf<String, GlslVal>()
        var fragOutName: String? = null
    }

    private class BufferRecord(
        val id: Int,
        var bytes: ByteArray = ByteArray(0)
    )

    private class AttribBinding(
        val bufferId: Int,
        val layout: GLVertexAttribLayout
    )

    private class VaoRecord(val id: Int) {
        val enabledAttribs = mutableSetOf<Int>()
        val attribBindings = mutableMapOf<Int, AttribBinding>()
        val attribDivisors = mutableMapOf<Int, Int>()
        var elementArrayBufferId: Int? = null
    }

    private class TextureRecord(
        val id: Int,
        var width: Int = 0,
        var height: Int = 0,
        var format: GLTextureFormat = GLTextureFormat.RGBA8,
        var rgbaPixels: ByteArray = ByteArray(0),
        var depthValues: FloatArray? = null,
        var params: GLTextureParameters = GLTextureParameters()
    )

    private class FramebufferRecord(val id: Int) {
        val colorAttachments = mutableMapOf<Int, Int>()
        var depthTextureId: Int? = null
        var stencilTextureId: Int? = null
        var internalDepthBuffer: FloatArray? = null
        var internalStencilBuffer: IntArray? = null
    }

    private class RenderTargetView(
        val width: Int,
        val height: Int,
        val colorPixels: ByteArray?,
        val depthBuffer: FloatArray?,
        val stencilBuffer: IntArray?,
        private val depthTexture: TextureRecord?
    ) {
        fun syncDepthTextureFromBuffer() {
            val dt = depthTexture ?: return
            val src = depthBuffer ?: return
            val out = dt.rgbaPixels
            val count = min(src.size, out.size / 4)
            for (i in 0 until count) {
                val byteVal = (src[i].coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255).toByte()
                val off = i * 4
                out[off] = byteVal
                out[off + 1] = byteVal
                out[off + 2] = byteVal
                out[off + 3] = -1
            }
        }
    }

    private class ShadedVertex(
        val clipX: Float,
        val clipY: Float,
        val clipZ: Float,
        val clipW: Float,
        val ndcX: Float,
        val ndcY: Float,
        val ndcZ: Float,
        val screenX: Float,
        val screenY: Float,
        val pointSize: Float,
        val varyings: Map<String, GlslVal>
    )

    private enum class TokKind { NUM, IDENT, SYM, EOF }

    private class Tok(val kind: TokKind, val text: String, val numValue: Float = 0f)

    private data class GlslDecl(val name: String, val typeName: String, val explicitLocation: Int?)
    private data class GlslConstDecl(val name: String, val typeName: String, val expr: GlslExpr)
    private data class GlslFunctionDef(
        val name: String,
        val returnType: String,
        val params: List<Pair<String, String>>,
        val body: List<GlslStmt>
    )

    private class GlslShaderAst {
        val attributes = mutableListOf<GlslDecl>()
        val varyingsOut = mutableListOf<GlslDecl>()
        val varyingsIn = mutableListOf<GlslDecl>()
        val fragOutputs = mutableListOf<GlslDecl>()
        val uniforms = mutableListOf<GlslDecl>()
        val uniformBlocks = mutableListOf<String>()
        val consts = mutableListOf<GlslConstDecl>()
        val functions = mutableMapOf<String, GlslFunctionDef>()
        var mainStmts: List<GlslStmt> = emptyList()
        var hasMain: Boolean = false
    }

    private sealed interface GlslStmt {
        class Block(val stmts: List<GlslStmt>) : GlslStmt
        class VarDecl(val typeName: String, val decls: List<Pair<String, GlslExpr?>>) : GlslStmt
        class ExprStmt(val expr: GlslExpr) : GlslStmt
        class If(val cond: GlslExpr, val thenBranch: GlslStmt, val elseBranch: GlslStmt?) : GlslStmt
        class For(val init: GlslStmt?, val cond: GlslExpr?, val step: GlslExpr?, val body: GlslStmt) : GlslStmt
        class Return(val expr: GlslExpr?) : GlslStmt
        data object Discard : GlslStmt
    }

    private sealed interface GlslExpr {
        class Literal(val value: Float) : GlslExpr
        class Ident(val name: String) : GlslExpr
        class Unary(val op: String, val operand: GlslExpr) : GlslExpr
        class PostfixIncDec(val op: String, val target: GlslExpr) : GlslExpr
        class Binary(val op: String, val left: GlslExpr, val right: GlslExpr) : GlslExpr
        class Ternary(val cond: GlslExpr, val thenExpr: GlslExpr, val elseExpr: GlslExpr) : GlslExpr
        class Assign(val op: String, val lhs: GlslExpr, val rhs: GlslExpr) : GlslExpr
        class Member(val target: GlslExpr, val member: String) : GlslExpr
        class Index(val target: GlslExpr, val index: GlslExpr) : GlslExpr
        class Call(val name: String, val args: List<GlslExpr>) : GlslExpr
    }

    companion object {
        const val GL_TEXTURE0: Int = 0x84C0
        const val GL_TEXTURE_MAG_FILTER: Int = 0x2800
        const val GL_TEXTURE_MIN_FILTER: Int = 0x2801
        const val GL_TEXTURE_WRAP_S: Int = 0x2802
        const val GL_TEXTURE_WRAP_T: Int = 0x2803
        const val GL_NEAREST: Int = 0x2600
        const val GL_LINEAR: Int = 0x2601
        const val GL_NEAREST_MIPMAP_NEAREST: Int = 0x2700
        const val GL_LINEAR_MIPMAP_NEAREST: Int = 0x2701
        const val GL_NEAREST_MIPMAP_LINEAR: Int = 0x2702
        const val GL_LINEAR_MIPMAP_LINEAR: Int = 0x2703
        const val GL_REPEAT: Int = 0x2901
        const val GL_CLAMP_TO_EDGE: Int = 0x812F
        const val GL_MIRRORED_REPEAT: Int = 0x8370

        private val TWO_CHAR_OPS = setOf("==", "!=", "<=", ">=", "&&", "||", "+=", "-=", "*=", "/=", "++", "--")
        private val STORAGE_OR_PRECISION_QUALIFIERS = setOf(
            "in", "out", "inout", "attribute", "varying", "uniform", "const",
            "flat", "smooth", "centroid", "highp", "mediump", "lowp", "precision"
        )
        private val PARAM_QUALIFIERS = setOf("in", "out", "inout", "const", "highp", "mediump", "lowp")
        private val GLSL_VALUE_TYPES = setOf(
            "float", "int", "uint", "bool",
            "vec2", "vec3", "vec4",
            "ivec2", "ivec3", "ivec4",
            "uvec2", "uvec3", "uvec4",
            "bvec2", "bvec3", "bvec4",
            "mat2", "mat3", "mat4"
        )

        private fun glslTypeDim(typeName: String): Int = when (typeName) {
            "vec2", "ivec2", "uvec2", "bvec2" -> 2
            "vec3", "ivec3", "uvec3", "bvec3" -> 3
            "vec4", "ivec4", "uvec4", "bvec4" -> 4
            "mat3" -> 9
            "mat4" -> 16
            else -> 1
        }

        private fun identityMat(n: Int): FloatArray = FloatArray(n * n).apply {
            for (i in 0 until n) this[i * n + i] = 1f
        }

        private fun decodeGlFilter(param: Int): GLTextureFilter = when (param) {
            GL_NEAREST -> GLTextureFilter.NEAREST
            GL_LINEAR -> GLTextureFilter.LINEAR
            GL_NEAREST_MIPMAP_NEAREST -> GLTextureFilter.NEAREST_MIPMAP_NEAREST
            GL_LINEAR_MIPMAP_NEAREST -> GLTextureFilter.LINEAR_MIPMAP_NEAREST
            GL_NEAREST_MIPMAP_LINEAR -> GLTextureFilter.NEAREST_MIPMAP_LINEAR
            GL_LINEAR_MIPMAP_LINEAR -> GLTextureFilter.LINEAR_MIPMAP_LINEAR
            else -> throw IllegalArgumentException("Unsupported GL texture filter 0x${param.toString(16)}")
        }

        private fun decodeGlWrap(param: Int): GLTextureWrap = when (param) {
            GL_REPEAT -> GLTextureWrap.REPEAT
            GL_CLAMP_TO_EDGE -> GLTextureWrap.CLAMP_TO_EDGE
            GL_MIRRORED_REPEAT -> GLTextureWrap.MIRRORED_REPEAT
            else -> throw IllegalArgumentException("Unsupported GL texture wrap mode 0x${param.toString(16)}")
        }

        private fun readInt16LE(bytes: ByteArray, off: Int): Int {
            val lo = bytes[off].toInt() and 0xFF
            val hi = bytes[off + 1].toInt()
            return (hi shl 8) or lo
        }

        private fun readUint16LE(bytes: ByteArray, off: Int): Int =
            (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8)

        private fun readInt32LE(bytes: ByteArray, off: Int): Int =
            (bytes[off].toInt() and 0xFF) or
                ((bytes[off + 1].toInt() and 0xFF) shl 8) or
                ((bytes[off + 2].toInt() and 0xFF) shl 16) or
                ((bytes[off + 3].toInt() and 0xFF) shl 24)

        private fun writeInt16LE(bytes: ByteArray, off: Int, value: Int) {
            bytes[off] = (value and 0xFF).toByte()
            bytes[off + 1] = ((value ushr 8) and 0xFF).toByte()
        }

        private fun writeInt32LE(bytes: ByteArray, off: Int, value: Int) {
            bytes[off] = (value and 0xFF).toByte()
            bytes[off + 1] = ((value ushr 8) and 0xFF).toByte()
            bytes[off + 2] = ((value ushr 16) and 0xFF).toByte()
            bytes[off + 3] = ((value ushr 24) and 0xFF).toByte()
        }

        private fun halfFloatToFloat(h: Int): Float {
            val mant = h and 0x03FF
            val exp = h and 0x7C00
            val sign = (h and 0x8000) shl 16
            val bits = when {
                exp == 0x7C00 -> sign or 0x7F800000 or (mant shl 13)
                exp != 0 -> sign or ((exp + 0x1C000) shl 13) or (mant shl 13)
                mant == 0 -> sign
                else -> {
                    var m = mant
                    var e = 0x1C400
                    while ((m and 0x0400) == 0) {
                        m = m shl 1
                        e -= 0x0400
                    }
                    sign or ((e shl 13) or ((m and 0x03FF) shl 13))
                }
            }
            return Float.fromBits(bits)
        }

        private fun Float.ceilToIntCompat(): Int = ceil(this).toInt()
    }
}
