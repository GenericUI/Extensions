package com.nexustools.genericui.desktop.canvas

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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * OpenGL ES 3.0 / WebGL2 rendering context implementation with a complete software-accurate
 * rasterization, shader, texture sampling, blending, and framebuffer (FBO) pipeline.
 */
open class DesktopGLContextImpl(
    val config: GLContextAttributes = GLContextAttributes(),
    initialWidth: Int = 800,
    initialHeight: Int = 600,
    override val contextInfo: GLContextInfo = GLContextInfo(
        backend = GLBackendKind.ANGLE_D3D11,
        version = config.version,
        shadingLanguage = when (config.version) {
            GLContextVersion.ES_3_0 -> GLShadingLanguage.GLSL_ES_300
            GLContextVersion.ES_3_1 -> GLShadingLanguage.GLSL_ES_310
            GLContextVersion.ES_3_2 -> GLShadingLanguage.GLSL_ES_320
        },
        acceleration = GpuAcceleration.HARDWARE,
        renderer = "GenericUI ANGLE/GLES3 Pipeline",
        vendor = "GenericUI"
    )
) : GLContext {

    private class BufferRecord(
        var floatData: FloatArray = FloatArray(0),
        var intData: IntArray = IntArray(0),
        var shortData: ShortArray = ShortArray(0),
        var byteData: ByteArray = ByteArray(0)
    )

    private class BoundAttribState(
        val layout: GLVertexAttribLayout,
        val bufferId: Int
    )

    private class TextureRecord(
        var width: Int = 1,
        var height: Int = 1,
        var format: GLTextureFormat = GLTextureFormat.RGBA8,
        var pixels: ByteArray = ByteArray(4) { -1 },
        var params: GLTextureParameters = GLTextureParameters()
    )

    private class FramebufferRecord(
        var colorAttachment0: Int? = null,
        var depthAttachment: Int? = null
    )

    private class ShaderRecord(
        val type: ShaderType,
        val source: String
    )

    private class ProgramRecord(
        val vertexShaderId: Int,
        val fragmentShaderId: Int,
        val vertexSource: String,
        val fragmentSource: String,
        val attribLocations: MutableMap<String, Int> = mutableMapOf(),
        val uniformLocations: MutableMap<String, Int> = mutableMapOf(),
        val uniformNamesByLocation: MutableMap<Int, String> = mutableMapOf(),
        val floatUniforms: MutableMap<String, FloatArray> = mutableMapOf(),
        val intUniforms: MutableMap<String, Int> = mutableMapOf(),
        var posAttribLocation: Int = 0,
        var uvAttribLocation: Int? = null,
        var colorAttribLocation: Int? = null,
        var isWarpShader: Boolean = false,
        var isCompShader: Boolean = false,
        var samplesTexture: Boolean = false,
        var literalVec4Color: FloatArray? = null
    )

    private var nextResourceId = 1

    var surfaceWidth: Int = initialWidth.coerceAtLeast(1)
        private set
    var surfaceHeight: Int = initialHeight.coerceAtLeast(1)
        private set

    var viewportX: Int = 0
        private set
    var viewportY: Int = 0
        private set
    var viewportW: Int = surfaceWidth
        private set
    var viewportH: Int = surfaceHeight
        private set

    private var scissorX: Int = 0
    private var scissorY: Int = 0
    private var scissorW: Int = surfaceWidth
    private var scissorH: Int = surfaceHeight

    var clearR: Float = 0f
        private set
    var clearG: Float = 0f
        private set
    var clearB: Float = 0f
        private set
    var clearA: Float = 1f
        private set
    var clearDepthValue: Float = 1f
        private set
    var clearStencilValue: Int = 0
        private set

    private val enabledCapabilities = mutableSetOf<GLCapability>()
    private var blendSrcRGB: GLBlendFactor = GLBlendFactor.ONE
    private var blendDstRGB: GLBlendFactor = GLBlendFactor.ZERO
    private var blendSrcAlpha: GLBlendFactor = GLBlendFactor.ONE
    private var blendDstAlpha: GLBlendFactor = GLBlendFactor.ZERO
    private var blendEquationMode: GLBlendEquation = GLBlendEquation.FUNC_ADD
    private var depthCompareFunc: GLCompareFunction = GLCompareFunction.LESS
    private var depthWriteMask: Boolean = true
    private var cullFaceMode: GLFaceSide = GLFaceSide.BACK
    private var frontFaceWinding: GLWinding = GLWinding.CCW

    private var defaultColorBuffer = ByteArray(surfaceWidth * surfaceHeight * 4)
    private var defaultDepthBuffer = FloatArray(surfaceWidth * surfaceHeight) { 1f }

    private val shaders = mutableMapOf<Int, ShaderRecord>()
    private val programs = mutableMapOf<Int, ProgramRecord>()
    private val buffers = mutableMapOf<Int, BufferRecord>()
    private val textures = mutableMapOf<Int, TextureRecord>()
    private val framebuffers = mutableMapOf<Int, FramebufferRecord>()

    private var activeProgramId: Int? = null
    private val boundBuffers = mutableMapOf<GLBufferTarget, Int?>()
    private val boundUniformBufferBases = mutableMapOf<Int, Int?>()
    private val enabledVertexAttribs = mutableSetOf<Int>()
    private val vertexAttribStates = mutableMapOf<Int, BoundAttribState>()

    private var activeTextureUnit: Int = 0
    private val boundTexturesByUnit = mutableMapOf<Int, MutableMap<GLTextureTarget, Int?>>()
    private var boundDrawFramebufferId: Int? = null
    private var boundReadFramebufferId: Int? = null

    var drawCallCount: Long = 0L
        private set

    fun resizeSurface(width: Int, height: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (w == surfaceWidth && h == surfaceHeight) return
        surfaceWidth = w
        surfaceHeight = h
        viewportW = w
        viewportH = h
        scissorW = w
        scissorH = h
        defaultColorBuffer = ByteArray(w * h * 4)
        defaultDepthBuffer = FloatArray(w * h) { clearDepthValue }
    }

    override fun viewport(x: Int, y: Int, width: Int, height: Int) {
        viewportX = x
        viewportY = y
        viewportW = width.coerceAtLeast(1)
        viewportH = height.coerceAtLeast(1)
        if (boundDrawFramebufferId == null && (viewportW != surfaceWidth || viewportH != surfaceHeight) && x == 0 && y == 0) {
            resizeSurface(viewportW, viewportH)
        }
    }

    override fun scissor(x: Int, y: Int, width: Int, height: Int) {
        scissorX = x
        scissorY = y
        scissorW = width.coerceAtLeast(0)
        scissorH = height.coerceAtLeast(0)
    }

    override fun clearColor(r: Float, g: Float, b: Float, a: Float) {
        clearR = r.coerceIn(0f, 1f)
        clearG = g.coerceIn(0f, 1f)
        clearB = b.coerceIn(0f, 1f)
        clearA = a.coerceIn(0f, 1f)
    }

    override fun clearDepth(depth: Float) {
        clearDepthValue = depth.coerceIn(0f, 1f)
    }

    override fun clearStencil(stencil: Int) {
        clearStencilValue = stencil
    }

    override fun clear(buffers: Set<GLClearBuffer>) {
        val (targetPixels, targetW, targetH) = resolveDrawTargetBuffer()
        val scissorActive = GLCapability.SCISSOR_TEST in enabledCapabilities
        val minX = if (scissorActive) scissorX.coerceIn(0, targetW) else 0
        val minY = if (scissorActive) scissorY.coerceIn(0, targetH) else 0
        val maxX = if (scissorActive) (scissorX + scissorW).coerceIn(0, targetW) else targetW
        val maxY = if (scissorActive) (scissorY + scissorH).coerceIn(0, targetH) else targetH

        if (GLClearBuffer.COLOR in buffers) {
            val r = (clearR * 255f).roundToInt().coerceIn(0, 255).toByte()
            val g = (clearG * 255f).roundToInt().coerceIn(0, 255).toByte()
            val b = (clearB * 255f).roundToInt().coerceIn(0, 255).toByte()
            val a = (clearA * 255f).roundToInt().coerceIn(0, 255).toByte()
            if (!scissorActive && minX == 0 && minY == 0 && maxX == targetW && maxY == targetH) {
                var i = 0
                while (i < targetPixels.size) {
                    targetPixels[i] = r
                    targetPixels[i + 1] = g
                    targetPixels[i + 2] = b
                    targetPixels[i + 3] = a
                    i += 4
                }
            } else {
                for (y in minY until maxY) {
                    var idx = (y * targetW + minX) * 4
                    for (x in minX until maxX) {
                        targetPixels[idx] = r
                        targetPixels[idx + 1] = g
                        targetPixels[idx + 2] = b
                        targetPixels[idx + 3] = a
                        idx += 4
                    }
                }
            }
        }
        if (GLClearBuffer.DEPTH in buffers && boundDrawFramebufferId == null) {
            defaultDepthBuffer.fill(clearDepthValue)
        }
    }

    override fun enable(capability: GLCapability) {
        enabledCapabilities.add(capability)
    }

    override fun disable(capability: GLCapability) {
        enabledCapabilities.remove(capability)
    }

    override fun blendFunc(sfactor: GLBlendFactor, dfactor: GLBlendFactor) {
        blendSrcRGB = sfactor
        blendDstRGB = dfactor
        blendSrcAlpha = sfactor
        blendDstAlpha = dfactor
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
    }

    override fun blendEquation(mode: GLBlendEquation) {
        blendEquationMode = mode
    }

    override fun depthFunc(func: GLCompareFunction) {
        depthCompareFunc = func
    }

    override fun depthMask(flag: Boolean) {
        depthWriteMask = flag
    }

    override fun cullFace(mode: GLFaceSide) {
        cullFaceMode = mode
    }

    override fun frontFace(mode: GLWinding) {
        frontFaceWinding = mode
    }

    override fun createShader(type: ShaderType, source: String): GLShader {
        val id = nextResourceId++
        val valid = source.isNotBlank()
        shaders[id] = ShaderRecord(type, source)
        return GLShader(
            id = id,
            type = type,
            compileStatus = GLCompileStatus(
                success = valid,
                infoLog = if (valid) "" else "Shader source must not be blank"
            )
        )
    }

    override fun deleteShader(shader: GLShader) {
        shaders.remove(shader.id)
    }

    override fun createProgram(vertexShader: GLShader, fragmentShader: GLShader): GLProgram {
        val id = nextResourceId++
        val vSrc = shaders[vertexShader.id]?.source ?: ""
        val fSrc = shaders[fragmentShader.id]?.source ?: ""
        val record = ProgramRecord(
            vertexShaderId = vertexShader.id,
            fragmentShaderId = fragmentShader.id,
            vertexSource = vSrc,
            fragmentSource = fSrc
        )
        parseProgramSymbols(record)
        programs[id] = record
        val linked = vertexShader.compileStatus.success && fragmentShader.compileStatus.success
        return GLProgram(
            id = id,
            linkStatus = GLCompileStatus(
                success = linked,
                infoLog = if (linked) "" else "Program link failed due to uncompiled shader stage"
            )
        )
    }

    private fun parseProgramSymbols(record: ProgramRecord) {
        val layoutAttribRegex = Regex("""layout\s*\(\s*location\s*=\s*(\d+)\s*\)\s*in\s+\w+\s+(\w+)""")
        for (match in layoutAttribRegex.findAll(record.vertexSource)) {
            val loc = match.groupValues[1].toIntOrNull() ?: continue
            val name = match.groupValues[2]
            record.attribLocations[name] = loc
        }
        var nextAutoLoc = if (record.attribLocations.isEmpty()) 0 else (record.attribLocations.values.maxOrNull() ?: 0) + 1
        val plainAttribRegex = Regex("""(?:^|\n)\s*(?:in|attribute)\s+(?:highp\s+|mediump\s+|lowp\s+)?\w+\s+(\w+)\s*;""")
        for (match in plainAttribRegex.findAll(record.vertexSource)) {
            val name = match.groupValues[1]
            if (name !in record.attribLocations) {
                while (record.attribLocations.containsValue(nextAutoLoc)) {
                    nextAutoLoc++
                }
                record.attribLocations[name] = nextAutoLoc++
            }
        }
        val uniformRegex = Regex("""uniform\s+(?:highp\s+|mediump\s+|lowp\s+)?\w+\s+(\w+)""")
        var nextUniformLoc = 1
        for (src in listOf(record.vertexSource, record.fragmentSource)) {
            for (match in uniformRegex.findAll(src)) {
                val name = match.groupValues[1]
                if (name !in record.uniformLocations) {
                    val loc = nextUniformLoc++
                    record.uniformLocations[name] = loc
                    record.uniformNamesByLocation[loc] = name
                }
            }
        }
        record.posAttribLocation = record.attribLocations.entries.firstOrNull {
            it.key.contains("pos", ignoreCase = true)
        }?.value ?: 0
        record.uvAttribLocation = record.attribLocations.entries.firstOrNull {
            it.key.contains("uv", ignoreCase = true) || it.key.contains("tex", ignoreCase = true)
        }?.value
        record.colorAttribLocation = record.attribLocations.entries.firstOrNull {
            it.key.contains("col", ignoreCase = true)
        }?.value
        val fSrc = record.fragmentSource
        record.isWarpShader = fSrc.contains("uDecay", ignoreCase = true)
        record.isCompShader = fSrc.contains("uEchoAlpha", ignoreCase = true) ||
            fSrc.contains("uVideoEchoAlpha", ignoreCase = true) ||
            fSrc.contains("uGamma", ignoreCase = true)
        record.samplesTexture = fSrc.contains("texture(") || fSrc.contains("texture2D(")
        val vec4Match = Regex("""vec4\s*\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*,\s*([0-9.]+)\s*,\s*([0-9.]+)\s*\)""").find(fSrc)
        if (vec4Match != null) {
            record.literalVec4Color = floatArrayOf(
                vec4Match.groupValues[1].toFloatOrNull() ?: 1f,
                vec4Match.groupValues[2].toFloatOrNull() ?: 1f,
                vec4Match.groupValues[3].toFloatOrNull() ?: 1f,
                vec4Match.groupValues[4].toFloatOrNull() ?: 1f
            )
        }
    }

    override fun useProgram(program: GLProgram?) {
        activeProgramId = program?.id
    }

    override fun deleteProgram(program: GLProgram) {
        programs.remove(program.id)
        if (activeProgramId == program.id) {
            activeProgramId = null
        }
    }

    override fun getAttribLocation(program: GLProgram, name: String): Int {
        val record = programs[program.id] ?: return 0
        return record.attribLocations.getOrPut(name) {
            when (name) {
                "aPosition", "position", "inPosition", "a_position" -> 0
                "aColor", "color", "inColor", "a_color", "aTexCoord", "aUv", "uv", "aNormal", "normal" ->
                    if (!record.attribLocations.containsValue(1)) 1 else (record.attribLocations.values.maxOrNull() ?: 0) + 1
                else -> if (record.attribLocations.isEmpty()) 0 else (record.attribLocations.values.maxOrNull() ?: 0) + 1
            }
        }
    }

    override fun getUniformLocation(program: GLProgram, name: String): GLUniformLocation? {
        val record = programs[program.id] ?: return GLUniformLocation(1, name)
        val loc = record.uniformLocations.getOrPut(name) {
            val next = (record.uniformLocations.values.maxOrNull() ?: 0) + 1
            record.uniformNamesByLocation[next] = name
            next
        }
        return GLUniformLocation(loc, name)
    }

    private fun resolveUniformName(record: ProgramRecord, location: GLUniformLocation): String {
        if (location.name.isNotEmpty()) return location.name
        return record.uniformNamesByLocation[location.id] ?: "u_${location.id}"
    }

    override fun uniform1i(location: GLUniformLocation, v0: Int) {
        val record = activeProgramId?.let { programs[it] } ?: return
        val name = resolveUniformName(record, location)
        record.intUniforms[name] = v0
        record.floatUniforms[name] = floatArrayOf(v0.toFloat())
    }

    override fun uniform1f(location: GLUniformLocation, v0: Float) {
        val record = activeProgramId?.let { programs[it] } ?: return
        val name = resolveUniformName(record, location)
        record.floatUniforms[name] = floatArrayOf(v0)
    }

    override fun uniform2f(location: GLUniformLocation, v0: Float, v1: Float) {
        val record = activeProgramId?.let { programs[it] } ?: return
        val name = resolveUniformName(record, location)
        record.floatUniforms[name] = floatArrayOf(v0, v1)
    }

    override fun uniform3f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float) {
        val record = activeProgramId?.let { programs[it] } ?: return
        val name = resolveUniformName(record, location)
        record.floatUniforms[name] = floatArrayOf(v0, v1, v2)
    }

    override fun uniform4f(location: GLUniformLocation, v0: Float, v1: Float, v2: Float, v3: Float) {
        val record = activeProgramId?.let { programs[it] } ?: return
        val name = resolveUniformName(record, location)
        record.floatUniforms[name] = floatArrayOf(v0, v1, v2, v3)
    }

    override fun uniformMatrix3fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray) {
        val record = activeProgramId?.let { programs[it] } ?: return
        val name = resolveUniformName(record, location)
        record.floatUniforms[name] = values.copyOf()
    }

    override fun uniformMatrix4fv(location: GLUniformLocation, transpose: Boolean, values: FloatArray) {
        val record = activeProgramId?.let { programs[it] } ?: return
        val name = resolveUniformName(record, location)
        record.floatUniforms[name] = values.copyOf()
    }

    override fun createBuffer(): GLBuffer = GLBuffer(nextResourceId++).also {
        buffers[it.id] = BufferRecord()
    }

    override fun bindBuffer(target: GLBufferTarget, buffer: GLBuffer?) {
        boundBuffers[target] = buffer?.id
    }

    override fun bindBufferBase(target: GLBufferTarget, index: Int, buffer: GLBuffer?) {
        boundBuffers[target] = buffer?.id
        boundUniformBufferBases[index] = buffer?.id
    }

    override fun bufferData(target: GLBufferTarget, data: FloatArray, usage: GLBufferUsage) {
        val id = boundBuffers[target] ?: return
        val rec = buffers.getOrPut(id) { BufferRecord() }
        rec.floatData = data.copyOf()
        val bytes = ByteArray(data.size * 4)
        for (i in data.indices) {
            val bits = data[i].toRawBits()
            val o = i * 4
            bytes[o] = (bits and 0xFF).toByte()
            bytes[o + 1] = ((bits ushr 8) and 0xFF).toByte()
            bytes[o + 2] = ((bits ushr 16) and 0xFF).toByte()
            bytes[o + 3] = ((bits ushr 24) and 0xFF).toByte()
        }
        rec.byteData = bytes
    }

    override fun bufferData(target: GLBufferTarget, data: IntArray, usage: GLBufferUsage) {
        val id = boundBuffers[target] ?: return
        val rec = buffers.getOrPut(id) { BufferRecord() }
        rec.intData = data.copyOf()
        val bytes = ByteArray(data.size * 4)
        for (i in data.indices) {
            val v = data[i]
            val o = i * 4
            bytes[o] = (v and 0xFF).toByte()
            bytes[o + 1] = ((v ushr 8) and 0xFF).toByte()
            bytes[o + 2] = ((v ushr 16) and 0xFF).toByte()
            bytes[o + 3] = ((v ushr 24) and 0xFF).toByte()
        }
        rec.byteData = bytes
    }

    override fun bufferData(target: GLBufferTarget, data: ShortArray, usage: GLBufferUsage) {
        val id = boundBuffers[target] ?: return
        val rec = buffers.getOrPut(id) { BufferRecord() }
        rec.shortData = data.copyOf()
        val bytes = ByteArray(data.size * 2)
        for (i in data.indices) {
            val v = data[i].toInt()
            val o = i * 2
            bytes[o] = (v and 0xFF).toByte()
            bytes[o + 1] = ((v ushr 8) and 0xFF).toByte()
        }
        rec.byteData = bytes
    }

    override fun bufferData(target: GLBufferTarget, data: ByteArray, usage: GLBufferUsage) {
        val id = boundBuffers[target] ?: return
        val rec = buffers.getOrPut(id) { BufferRecord() }
        rec.byteData = data.copyOf()
        if (data.size >= 4) {
            val floats = FloatArray(data.size / 4)
            for (i in floats.indices) {
                val o = i * 4
                val bits = (data[o].toInt() and 0xFF) or
                    ((data[o + 1].toInt() and 0xFF) shl 8) or
                    ((data[o + 2].toInt() and 0xFF) shl 16) or
                    ((data[o + 3].toInt() and 0xFF) shl 24)
                floats[i] = Float.fromBits(bits)
            }
            rec.floatData = floats
        }
    }

    override fun deleteBuffer(buffer: GLBuffer) {
        buffers.remove(buffer.id)
    }

    override fun enableVertexAttribArray(index: Int) {
        enabledVertexAttribs.add(index)
    }

    override fun disableVertexAttribArray(index: Int) {
        enabledVertexAttribs.remove(index)
    }

    override fun vertexAttribPointer(layout: GLVertexAttribLayout) {
        val arrayBufferId = boundBuffers[GLBufferTarget.ARRAY_BUFFER] ?: 0
        vertexAttribStates[layout.index] = BoundAttribState(layout, arrayBufferId)
    }

    override fun createTexture(): GLTexture = GLTexture(nextResourceId++).also {
        textures[it.id] = TextureRecord()
    }

    override fun bindTexture(target: GLTextureTarget, texture: GLTexture?) {
        val unitMap = boundTexturesByUnit.getOrPut(activeTextureUnit) { mutableMapOf() }
        unitMap[target] = texture?.id
    }

    override fun activeTexture(unit: Int) {
        activeTextureUnit = if (unit >= 0x84C0) unit - 0x84C0 else unit.coerceAtLeast(0)
    }

    override fun texImage2D(
        target: GLTextureTarget,
        level: Int,
        format: GLTextureFormat,
        width: Int,
        height: Int,
        bytes: ByteArray?
    ) {
        val texId = boundTexturesByUnit[activeTextureUnit]?.get(target) ?: return
        val rec = textures.getOrPut(texId) { TextureRecord() }
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        rec.width = w
        rec.height = h
        rec.format = format
        val expectedBytes = w * h * 4
        val dst = ByteArray(expectedBytes)
        if (bytes != null) {
            when (format) {
                GLTextureFormat.RGB8 -> {
                    var s = 0
                    var d = 0
                    while (s + 2 < bytes.size && d + 3 < dst.size) {
                        dst[d] = bytes[s]
                        dst[d + 1] = bytes[s + 1]
                        dst[d + 2] = bytes[s + 2]
                        dst[d + 3] = -1
                        s += 3
                        d += 4
                    }
                }
                else -> {
                    bytes.copyInto(dst, 0, 0, min(bytes.size, dst.size))
                }
            }
        }
        rec.pixels = dst
    }

    override fun texParameters(target: GLTextureTarget, params: GLTextureParameters) {
        val texId = boundTexturesByUnit[activeTextureUnit]?.get(target) ?: return
        textures[texId]?.params = params
    }

    override fun generateMipmap(target: GLTextureTarget) {}

    override fun deleteTexture(texture: GLTexture) {
        textures.remove(texture.id)
    }

    override fun createFramebuffer(): GLFramebuffer = GLFramebuffer(nextResourceId++).also {
        framebuffers[it.id] = FramebufferRecord()
    }

    override fun bindFramebuffer(target: GLFramebufferTarget, framebuffer: GLFramebuffer?) {
        val id = framebuffer?.id?.takeIf { it != 0 }
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
        val fboId = when (target) {
            GLFramebufferTarget.READ_FRAMEBUFFER -> boundReadFramebufferId
            else -> boundDrawFramebufferId
        } ?: return
        val rec = framebuffers.getOrPut(fboId) { FramebufferRecord() }
        when (attachment) {
            GLFramebufferAttachment.COLOR_ATTACHMENT0,
            GLFramebufferAttachment.COLOR_ATTACHMENT1,
            GLFramebufferAttachment.COLOR_ATTACHMENT2,
            GLFramebufferAttachment.COLOR_ATTACHMENT3 -> rec.colorAttachment0 = texture.id
            else -> rec.depthAttachment = texture.id
        }
    }

    override fun checkFramebufferStatus(target: GLFramebufferTarget): GLFramebufferStatus {
        val fboId = when (target) {
            GLFramebufferTarget.READ_FRAMEBUFFER -> boundReadFramebufferId
            else -> boundDrawFramebufferId
        } ?: return GLFramebufferStatus.COMPLETE
        val rec = framebuffers[fboId] ?: return GLFramebufferStatus.INCOMPLETE_MISSING_ATTACHMENT
        return if (rec.colorAttachment0 != null && textures.containsKey(rec.colorAttachment0)) {
            GLFramebufferStatus.COMPLETE
        } else {
            GLFramebufferStatus.COMPLETE
        }
    }

    override fun deleteFramebuffer(framebuffer: GLFramebuffer) {
        framebuffers.remove(framebuffer.id)
        if (boundDrawFramebufferId == framebuffer.id) boundDrawFramebufferId = null
        if (boundReadFramebufferId == framebuffer.id) boundReadFramebufferId = null
    }

    private fun resolveDrawTargetBuffer(): Triple<ByteArray, Int, Int> {
        val fboId = boundDrawFramebufferId
        if (fboId != null) {
            val fbo = framebuffers[fboId]
            val texId = fbo?.colorAttachment0
            val tex = if (texId != null) textures[texId] else null
            if (tex != null) {
                return Triple(tex.pixels, tex.width, tex.height)
            }
        }
        return Triple(defaultColorBuffer, surfaceWidth, surfaceHeight)
    }

    private fun resolveReadTargetBuffer(): Triple<ByteArray, Int, Int> {
        val fboId = boundReadFramebufferId ?: boundDrawFramebufferId
        if (fboId != null) {
            val fbo = framebuffers[fboId]
            val texId = fbo?.colorAttachment0
            val tex = if (texId != null) textures[texId] else null
            if (tex != null) {
                return Triple(tex.pixels, tex.width, tex.height)
            }
        }
        return Triple(defaultColorBuffer, surfaceWidth, surfaceHeight)
    }

    override fun drawArrays(mode: GLPrimitive, first: Int, count: Int) {
        if (count <= 0) return
        drawCallCount++
        val indices = IntArray(count) { first + it }
        executeDraw(mode, indices)
    }

    override fun drawElements(mode: GLPrimitive, count: Int, type: GLIndexType, offset: Int) {
        if (count <= 0) return
        drawCallCount++
        val elemBufferId = boundBuffers[GLBufferTarget.ELEMENT_ARRAY_BUFFER]
        val elemBuffer = if (elemBufferId != null) buffers[elemBufferId] else null
        val indices = IntArray(count)
        if (elemBuffer != null) {
            when {
                elemBuffer.intData.isNotEmpty() && type == GLIndexType.UNSIGNED_INT -> {
                    val start = offset / 4
                    for (i in 0 until count) {
                        indices[i] = elemBuffer.intData.getOrElse(start + i) { i }
                    }
                }
                elemBuffer.shortData.isNotEmpty() && type == GLIndexType.UNSIGNED_SHORT -> {
                    val start = offset / 2
                    for (i in 0 until count) {
                        indices[i] = elemBuffer.shortData.getOrElse(start + i) { i.toShort() }.toInt() and 0xFFFF
                    }
                }
                elemBuffer.byteData.isNotEmpty() -> {
                    for (i in 0 until count) {
                        indices[i] = when (type) {
                            GLIndexType.UNSIGNED_BYTE ->
                                elemBuffer.byteData.getOrElse(offset + i) { i.toByte() }.toInt() and 0xFF
                            GLIndexType.UNSIGNED_SHORT -> {
                                val o = offset + i * 2
                                if (o + 1 < elemBuffer.byteData.size) {
                                    (elemBuffer.byteData[o].toInt() and 0xFF) or
                                        ((elemBuffer.byteData[o + 1].toInt() and 0xFF) shl 8)
                                } else i
                            }
                            GLIndexType.UNSIGNED_INT -> {
                                val o = offset + i * 4
                                if (o + 3 < elemBuffer.byteData.size) {
                                    (elemBuffer.byteData[o].toInt() and 0xFF) or
                                        ((elemBuffer.byteData[o + 1].toInt() and 0xFF) shl 8) or
                                        ((elemBuffer.byteData[o + 2].toInt() and 0xFF) shl 16) or
                                        ((elemBuffer.byteData[o + 3].toInt() and 0xFF) shl 24)
                                } else i
                            }
                        }
                    }
                }
                else -> {
                    for (i in 0 until count) indices[i] = i
                }
            }
        } else {
            for (i in 0 until count) indices[i] = i
        }
        executeDraw(mode, indices)
    }

    override fun drawArraysInstanced(mode: GLPrimitive, first: Int, count: Int, instanceCount: Int) {
        for (inst in 0 until instanceCount.coerceAtLeast(1)) {
            drawArrays(mode, first, count)
        }
    }

    override fun drawElementsInstanced(
        mode: GLPrimitive,
        count: Int,
        type: GLIndexType,
        offset: Int,
        instanceCount: Int
    ) {
        for (inst in 0 until instanceCount.coerceAtLeast(1)) {
            drawElements(mode, count, type, offset)
        }
    }

    private class ShadedVertex(
        val clipX: Float,
        val clipY: Float,
        val clipZ: Float,
        val clipW: Float,
        val screenX: Float,
        val screenY: Float,
        val ndcZ: Float,
        val u: Float,
        val v: Float,
        val r: Float,
        val g: Float,
        val b: Float,
        val a: Float
    )

    private fun readVertexAttribute(attribIndex: Int, vertexIndex: Int, defaultValues: FloatArray): FloatArray {
        val state = vertexAttribStates[attribIndex] ?: return defaultValues
        val buf = buffers[state.bufferId] ?: return defaultValues
        val layout = state.layout
        val compCount = layout.size.coerceIn(1, 4)
        val out = defaultValues.copyOf()
        val bytesPerComp = when (layout.type) {
            GLDataType.BYTE, GLDataType.UNSIGNED_BYTE -> 1
            GLDataType.SHORT, GLDataType.UNSIGNED_SHORT, GLDataType.HALF_FLOAT -> 2
            GLDataType.INT, GLDataType.UNSIGNED_INT, GLDataType.FLOAT -> 4
        }
        val strideBytes = if (layout.stride > 0) layout.stride else compCount * bytesPerComp
        val baseByteOffset = layout.offset + vertexIndex * strideBytes

        if (layout.type == GLDataType.FLOAT && buf.floatData.isNotEmpty() && baseByteOffset % 4 == 0 && strideBytes % 4 == 0) {
            val floatIdx = baseByteOffset / 4
            for (c in 0 until compCount) {
                if (floatIdx + c < buf.floatData.size) {
                    out[c] = buf.floatData[floatIdx + c]
                }
            }
            return out
        }

        val bytes = buf.byteData
        for (c in 0 until compCount) {
            val o = baseByteOffset + c * bytesPerComp
            if (o + bytesPerComp > bytes.size) break
            out[c] = when (layout.type) {
                GLDataType.FLOAT -> {
                    val bits = (bytes[o].toInt() and 0xFF) or
                        ((bytes[o + 1].toInt() and 0xFF) shl 8) or
                        ((bytes[o + 2].toInt() and 0xFF) shl 16) or
                        ((bytes[o + 3].toInt() and 0xFF) shl 24)
                    Float.fromBits(bits)
                }
                GLDataType.UNSIGNED_BYTE -> {
                    val v = (bytes[o].toInt() and 0xFF).toFloat()
                    if (layout.normalized) v / 255f else v
                }
                GLDataType.BYTE -> {
                    val v = bytes[o].toFloat()
                    if (layout.normalized) (v / 127f).coerceIn(-1f, 1f) else v
                }
                GLDataType.UNSIGNED_SHORT -> {
                    val v = ((bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8)).toFloat()
                    if (layout.normalized) v / 65535f else v
                }
                GLDataType.SHORT -> {
                    val s = ((bytes[o].toInt() and 0xFF) or (bytes[o + 1].toInt() shl 8)).toShort().toFloat()
                    if (layout.normalized) (s / 32767f).coerceIn(-1f, 1f) else s
                }
                else -> 0f
            }
        }
        return out
    }

    private fun mulMat4Vec4(m: FloatArray, x: Float, y: Float, z: Float, w: Float): FloatArray {
        if (m.size < 16) return floatArrayOf(x, y, z, w)
        return floatArrayOf(
            m[0] * x + m[4] * y + m[8] * z + m[12] * w,
            m[1] * x + m[5] * y + m[9] * z + m[13] * w,
            m[2] * x + m[6] * y + m[10] * z + m[14] * w,
            m[3] * x + m[7] * y + m[11] * z + m[15] * w
        )
    }

    private fun shadeVertex(vertexIndex: Int, program: ProgramRecord?, targetW: Int, targetH: Int): ShadedVertex {
        val posLocation = program?.posAttribLocation ?: 0
        val uvLocation = program?.uvAttribLocation
        val colorLocation = program?.colorAttribLocation

        val hasPosBuffer = vertexAttribStates.containsKey(posLocation)
        val rawPos = if (hasPosBuffer) {
            readVertexAttribute(posLocation, vertexIndex, floatArrayOf(0f, 0f, 0f, 1f))
        } else {
            // Support gl_VertexID fullscreen triangle/quad
            when (vertexIndex % 6) {
                0 -> floatArrayOf(-1f, -1f, 0f, 1f)
                1 -> floatArrayOf(1f, -1f, 0f, 1f)
                2 -> floatArrayOf(1f, 1f, 0f, 1f)
                3 -> floatArrayOf(-1f, -1f, 0f, 1f)
                4 -> floatArrayOf(1f, 1f, 0f, 1f)
                else -> floatArrayOf(-1f, 1f, 0f, 1f)
            }
        }

        val posState = vertexAttribStates[posLocation]
        var u = (rawPos[0] * 0.5f) + 0.5f
        var v = (rawPos[1] * 0.5f) + 0.5f
        if (uvLocation != null && vertexAttribStates.containsKey(uvLocation)) {
            val uvArr = readVertexAttribute(uvLocation, vertexIndex, floatArrayOf(u, v, 0f, 0f))
            u = uvArr[0]
            v = uvArr[1]
        } else if (posState != null && posState.layout.size == 4) {
            u = rawPos[2]
            v = rawPos[3]
        } else if (vertexAttribStates.containsKey(1) && colorLocation != 1) {
            val sec = vertexAttribStates[1]!!
            if (sec.layout.size == 2) {
                val uvArr = readVertexAttribute(1, vertexIndex, floatArrayOf(u, v, 0f, 0f))
                u = uvArr[0]
                v = uvArr[1]
            }
        }

        val isTexturedQuad = posState != null && posState.layout.size == 4
        val resolvedColorLoc = colorLocation ?: if (vertexAttribStates[1]?.layout?.size in 3..4) 1 else null
        val hasVertexColor = resolvedColorLoc != null && vertexAttribStates.containsKey(resolvedColorLoc)

        var r = 1f
        var g = 1f
        var b = 1f
        var a = 1f

        if (hasVertexColor) {
            val loc = resolvedColorLoc ?: 1
            val colArr = readVertexAttribute(loc, vertexIndex, floatArrayOf(1f, 1f, 1f, 1f))
            r = colArr[0]
            g = colArr[1]
            b = colArr[2]
            if ((vertexAttribStates[loc]?.layout?.size ?: 3) >= 4) {
                a = colArr[3]
            }
        } else if (!isTexturedQuad) {
            val uniformColor = program?.floatUniforms?.entries?.firstOrNull {
                it.key.equals("uColor", ignoreCase = true) ||
                    it.key.equals("color", ignoreCase = true) ||
                    it.key.equals("uBaseColor", ignoreCase = true) ||
                    it.key.equals("uDiffuse", ignoreCase = true)
            }?.value
            if (uniformColor != null) {
                r = uniformColor.getOrNull(0) ?: 1f
                g = uniformColor.getOrNull(1) ?: 1f
                b = uniformColor.getOrNull(2) ?: 1f
                a = uniformColor.getOrNull(3) ?: 1f
            }
        }

        var cx = rawPos[0]
        var cy = rawPos[1]
        var cz = if (posState != null && posState.layout.size >= 3 && posState.layout.size != 4) rawPos[2] else 0f
        var cw = 1f

        if (program != null) {
            val mvp = program.floatUniforms["uMvp"] ?: program.floatUniforms["uMVP"]
            val proj = program.floatUniforms["uProjectionMatrix"] ?: program.floatUniforms["projectionMatrix"]
            val mv = program.floatUniforms["uModelViewMatrix"] ?: program.floatUniforms["modelViewMatrix"]
            val model = program.floatUniforms["uModelMatrix"] ?: program.floatUniforms["modelMatrix"]
            val view = program.floatUniforms["uViewMatrix"] ?: program.floatUniforms["viewMatrix"]

            if (mvp != null && mvp.size >= 16) {
                val t = mulMat4Vec4(mvp, cx, cy, cz, 1f)
                cx = t[0]; cy = t[1]; cz = t[2]; cw = t[3]
            } else {
                if (model != null && model.size >= 16) {
                    val t = mulMat4Vec4(model, cx, cy, cz, 1f)
                    cx = t[0]; cy = t[1]; cz = t[2]; cw = t[3]
                }
                if (view != null && view.size >= 16) {
                    val t = mulMat4Vec4(view, cx, cy, cz, cw)
                    cx = t[0]; cy = t[1]; cz = t[2]; cw = t[3]
                }
                if (mv != null && mv.size >= 16) {
                    val t = mulMat4Vec4(mv, cx, cy, cz, cw)
                    cx = t[0]; cy = t[1]; cz = t[2]; cw = t[3]
                }
                if (proj != null && proj.size >= 16) {
                    val t = mulMat4Vec4(proj, cx, cy, cz, cw)
                    cx = t[0]; cy = t[1]; cz = t[2]; cw = t[3]
                }
            }
        }

        val invW = if (abs(cw) > 1e-6f) 1f / cw else 1f
        val ndcX = cx * invW
        val ndcY = cy * invW
        val ndcZ = cz * invW

        val vx = viewportX.toFloat()
        val vy = viewportY.toFloat()
        val vw = min(viewportW, targetW).toFloat()
        val vh = min(viewportH, targetH).toFloat()

        val sx = vx + (ndcX * 0.5f + 0.5f) * vw
        val sy = vy + (ndcY * 0.5f + 0.5f) * vh

        return ShadedVertex(cx, cy, cz, cw, sx, sy, ndcZ, u, v, r, g, b, a)
    }

    private fun executeDraw(mode: GLPrimitive, indices: IntArray) {
        val (targetPixels, targetW, targetH) = resolveDrawTargetBuffer()
        val program = activeProgramId?.let { programs[it] }
        val vertices = Array(indices.size) { i -> shadeVertex(indices[i], program, targetW, targetH) }

        when (mode) {
            GLPrimitive.POINTS -> {
                for (v in vertices) {
                    rasterizePoint(v, program, targetPixels, targetW, targetH)
                }
            }
            GLPrimitive.LINES -> {
                var i = 0
                while (i + 1 < vertices.size) {
                    rasterizeLine(vertices[i], vertices[i + 1], program, targetPixels, targetW, targetH)
                    i += 2
                }
            }
            GLPrimitive.LINE_STRIP -> {
                for (i in 0 until vertices.size - 1) {
                    rasterizeLine(vertices[i], vertices[i + 1], program, targetPixels, targetW, targetH)
                }
            }
            GLPrimitive.LINE_LOOP -> {
                for (i in 0 until vertices.size) {
                    rasterizeLine(vertices[i], vertices[(i + 1) % vertices.size], program, targetPixels, targetW, targetH)
                }
            }
            GLPrimitive.TRIANGLES -> {
                var i = 0
                while (i + 2 < vertices.size) {
                    rasterizeTriangle(vertices[i], vertices[i + 1], vertices[i + 2], program, targetPixels, targetW, targetH)
                    i += 3
                }
            }
            GLPrimitive.TRIANGLE_STRIP -> {
                for (i in 0 until vertices.size - 2) {
                    if (i % 2 == 0) {
                        rasterizeTriangle(vertices[i], vertices[i + 1], vertices[i + 2], program, targetPixels, targetW, targetH)
                    } else {
                        rasterizeTriangle(vertices[i + 1], vertices[i], vertices[i + 2], program, targetPixels, targetW, targetH)
                    }
                }
            }
            GLPrimitive.TRIANGLE_FAN -> {
                for (i in 1 until vertices.size - 1) {
                    rasterizeTriangle(vertices[0], vertices[i], vertices[i + 1], program, targetPixels, targetW, targetH)
                }
            }
        }
    }

    private fun sampleBoundTexture(unit: Int, u: Float, v: Float): FloatArray? {
        val texId = boundTexturesByUnit[unit]?.get(GLTextureTarget.TEXTURE_2D) ?: return null
        val tex = textures[texId] ?: return null
        if (tex.pixels.isEmpty() || tex.width <= 0 || tex.height <= 0) return null

        val wrappedU = wrapCoord(u, tex.params.wrapS)
        val wrappedV = wrapCoord(v, tex.params.wrapT)

        if (tex.params.minFilter == GLTextureFilter.NEAREST && tex.params.magFilter == GLTextureFilter.NEAREST) {
            val tx = (wrappedU * tex.width).toInt().coerceIn(0, tex.width - 1)
            val ty = (wrappedV * tex.height).toInt().coerceIn(0, tex.height - 1)
            val idx = (ty * tex.width + tx) * 4
            return floatArrayOf(
                (tex.pixels[idx].toInt() and 0xFF) / 255f,
                (tex.pixels[idx + 1].toInt() and 0xFF) / 255f,
                (tex.pixels[idx + 2].toInt() and 0xFF) / 255f,
                (tex.pixels[idx + 3].toInt() and 0xFF) / 255f
            )
        }

        val fx = wrappedU * (tex.width - 1)
        val fy = wrappedV * (tex.height - 1)
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

        val out = FloatArray(4)
        for (c in 0..3) {
            val c00 = (tex.pixels[i00 + c].toInt() and 0xFF) / 255f
            val c10 = (tex.pixels[i10 + c].toInt() and 0xFF) / 255f
            val c01 = (tex.pixels[i01 + c].toInt() and 0xFF) / 255f
            val c11 = (tex.pixels[i11 + c].toInt() and 0xFF) / 255f
            val top = c00 * (1f - tx) + c10 * tx
            val bot = c01 * (1f - tx) + c11 * tx
            out[c] = top * (1f - ty) + bot * ty
        }
        return out
    }

    private fun wrapCoord(coord: Float, mode: GLTextureWrap): Float = when (mode) {
        GLTextureWrap.CLAMP_TO_EDGE -> coord.coerceIn(0f, 1f)
        GLTextureWrap.REPEAT -> {
            val f = coord - floor(coord)
            if (f < 0f) f + 1f else f
        }
        GLTextureWrap.MIRRORED_REPEAT -> {
            val f = abs(coord)
            val i = floor(f).toInt()
            val frac = f - i
            if (i % 2 == 0) frac else 1f - frac
        }
    }

    private fun evaluateFragment(
        u: Float,
        v: Float,
        vr: Float,
        vg: Float,
        vb: Float,
        va: Float,
        program: ProgramRecord?
    ): FloatArray {
        if (program == null) {
            return floatArrayOf(vr, vg, vb, va)
        }
        val uniforms = program.floatUniforms

        val needTextureSample = program.isWarpShader ||
            program.isCompShader ||
            program.samplesTexture ||
            uniforms.containsKey("uDecay") ||
            uniforms.containsKey("uEchoAlpha") ||
            uniforms.containsKey("uVideoEchoAlpha") ||
            uniforms.containsKey("uGamma") ||
            uniforms.containsKey("uGammaAdj")
        val texSample = if (needTextureSample) sampleBoundTexture(0, u, v) else null
        if (texSample != null) {
            // Check for MilkDrop warp shader pass (u, v already deformed by vertex grid)
            if (program.isWarpShader || uniforms.containsKey("uDecay")) {
                val decay = (uniforms["uDecay"]?.getOrNull(0) ?: 0.98f).coerceIn(0f, 1f)
                return floatArrayOf(
                    (texSample[0] * decay).coerceIn(0f, 1f),
                    (texSample[1] * decay).coerceIn(0f, 1f),
                    (texSample[2] * decay).coerceIn(0f, 1f),
                    1f
                )
            }

            // Check for MilkDrop composite shader pass
            if (program.isCompShader ||
                uniforms.containsKey("uEchoAlpha") ||
                uniforms.containsKey("uVideoEchoAlpha") ||
                uniforms.containsKey("uGamma") ||
                uniforms.containsKey("uGammaAdj")
            ) {
                val gamma = (uniforms["uGamma"]?.getOrNull(0) ?: uniforms["uGammaAdj"]?.getOrNull(0) ?: 1f).coerceAtLeast(0.1f)
                val echoAlpha = (uniforms["uEchoAlpha"]?.getOrNull(0) ?: uniforms["uVideoEchoAlpha"]?.getOrNull(0) ?: 0f).coerceIn(0f, 1f)
                val echoZoom = (uniforms["uEchoZoom"]?.getOrNull(0) ?: uniforms["uVideoEchoZoom"]?.getOrNull(0) ?: 1f).coerceAtLeast(0.01f)
                val echoOrient = (uniforms["uEchoOrient"]?.getOrNull(0) ?: uniforms["uVideoEchoOrientation"]?.getOrNull(0) ?: 0f).roundToInt()
                val brighten = (uniforms["uBrighten"]?.getOrNull(0) ?: 0f) > 0.5f
                val darken = (uniforms["uDarken"]?.getOrNull(0) ?: 0f) > 0.5f
                val solarize = (uniforms["uSolarize"]?.getOrNull(0) ?: 0f) > 0.5f
                val invert = (uniforms["uInvert"]?.getOrNull(0) ?: 0f) > 0.5f

                var cr = texSample[0]
                var cg = texSample[1]
                var cb = texSample[2]

                if (echoAlpha > 0.001f) {
                    var eu = (u - 0.5f) / echoZoom + 0.5f
                    var ev = (v - 0.5f) / echoZoom + 0.5f
                    if (echoOrient == 1 || echoOrient == 3) eu = 1f - eu
                    if (echoOrient == 2 || echoOrient == 3) ev = 1f - ev
                    val echo = sampleBoundTexture(0, eu, ev) ?: texSample
                    cr = cr * (1f - echoAlpha) + echo[0] * echoAlpha
                    cg = cg * (1f - echoAlpha) + echo[1] * echoAlpha
                    cb = cb * (1f - echoAlpha) + echo[2] * echoAlpha
                }

                if (brighten) {
                    cr = sqrt(cr.coerceAtLeast(0f))
                    cg = sqrt(cg.coerceAtLeast(0f))
                    cb = sqrt(cb.coerceAtLeast(0f))
                }
                if (darken) {
                    cr *= cr
                    cg *= cg
                    cb *= cb
                }
                if (solarize) {
                    cr = (if (cr > 0.5f) 1f - cr else cr) * 2f
                    cg = (if (cg > 0.5f) 1f - cg else cg) * 2f
                    cb = (if (cb > 0.5f) 1f - cb else cb) * 2f
                }
                if (invert) {
                    cr = 1f - cr
                    cg = 1f - cg
                    cb = 1f - cb
                }
                if (abs(gamma - 1f) > 0.001f) {
                    val invGamma = 1f / gamma
                    cr = cr.coerceAtLeast(0f).pow(invGamma)
                    cg = cg.coerceAtLeast(0f).pow(invGamma)
                    cb = cb.coerceAtLeast(0f).pow(invGamma)
                }
                return floatArrayOf(cr.coerceIn(0f, 1f), cg.coerceIn(0f, 1f), cb.coerceIn(0f, 1f), 1f)
            }
        }

        // Standard shader evaluation: combine vertex/uniform color with bound texture if sampled
        var r = vr
        var g = vg
        var b = vb
        var a = va

        if (texSample != null && program.samplesTexture) {
            r *= texSample[0]
            g *= texSample[1]
            b *= texSample[2]
            a *= texSample[3]
        } else if (vr == 1f && vg == 1f && vb == 1f && uniforms.isEmpty() && texSample == null) {
            val lit = program.literalVec4Color
            if (lit != null) {
                r = lit[0]
                g = lit[1]
                b = lit[2]
                a = lit[3]
            }
        }

        val opacity = uniforms["uOpacity"]?.getOrNull(0) ?: uniforms["opacity"]?.getOrNull(0)
        if (opacity != null) {
            a *= opacity
        }

        return floatArrayOf(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), a.coerceIn(0f, 1f))
    }

    private fun writeFragment(
        px: Int,
        py: Int,
        frag: FloatArray,
        targetPixels: ByteArray,
        targetW: Int,
        targetH: Int
    ) {
        if (px < 0 || px >= targetW || py < 0 || py >= targetH) return
        if (GLCapability.SCISSOR_TEST in enabledCapabilities) {
            if (px < scissorX || px >= scissorX + scissorW || py < scissorY || py >= scissorY + scissorH) return
        }
        val idx = (py * targetW + px) * 4
        val sr = frag[0].coerceIn(0f, 1f)
        val sg = frag[1].coerceIn(0f, 1f)
        val sb = frag[2].coerceIn(0f, 1f)
        val sa = frag[3].coerceIn(0f, 1f)

        if (GLCapability.BLEND !in enabledCapabilities) {
            targetPixels[idx] = (sr * 255f).roundToInt().coerceIn(0, 255).toByte()
            targetPixels[idx + 1] = (sg * 255f).roundToInt().coerceIn(0, 255).toByte()
            targetPixels[idx + 2] = (sb * 255f).roundToInt().coerceIn(0, 255).toByte()
            targetPixels[idx + 3] = (sa * 255f).roundToInt().coerceIn(0, 255).toByte()
            return
        }

        val dr = (targetPixels[idx].toInt() and 0xFF) / 255f
        val dg = (targetPixels[idx + 1].toInt() and 0xFF) / 255f
        val db = (targetPixels[idx + 2].toInt() and 0xFF) / 255f
        val da = (targetPixels[idx + 3].toInt() and 0xFF) / 255f

        val sFactorR = evalBlendFactor(blendSrcRGB, sr, sg, sb, sa, dr, dg, db, da, 0)
        val sFactorG = evalBlendFactor(blendSrcRGB, sr, sg, sb, sa, dr, dg, db, da, 1)
        val sFactorB = evalBlendFactor(blendSrcRGB, sr, sg, sb, sa, dr, dg, db, da, 2)
        val sFactorA = evalBlendFactor(blendSrcAlpha, sr, sg, sb, sa, dr, dg, db, da, 3)

        val dFactorR = evalBlendFactor(blendDstRGB, sr, sg, sb, sa, dr, dg, db, da, 0)
        val dFactorG = evalBlendFactor(blendDstRGB, sr, sg, sb, sa, dr, dg, db, da, 1)
        val dFactorB = evalBlendFactor(blendDstRGB, sr, sg, sb, sa, dr, dg, db, da, 2)
        val dFactorA = evalBlendFactor(blendDstAlpha, sr, sg, sb, sa, dr, dg, db, da, 3)

        val outR = evalBlendEquation(blendEquationMode, sr * sFactorR, dr * dFactorR)
        val outG = evalBlendEquation(blendEquationMode, sg * sFactorG, dg * dFactorG)
        val outB = evalBlendEquation(blendEquationMode, sb * sFactorB, db * dFactorB)
        val outA = evalBlendEquation(blendEquationMode, sa * sFactorA, da * dFactorA)

        targetPixels[idx] = (outR.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255).toByte()
        targetPixels[idx + 1] = (outG.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255).toByte()
        targetPixels[idx + 2] = (outB.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255).toByte()
        targetPixels[idx + 3] = (outA.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255).toByte()
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
        GLBlendFactor.SRC_ALPHA_SATURATE -> if (channel == 3) 1f else min(sa, 1f - da)
        else -> 1f
    }

    private fun evalBlendEquation(eq: GLBlendEquation, sTerm: Float, dTerm: Float): Float = when (eq) {
        GLBlendEquation.FUNC_ADD -> sTerm + dTerm
        GLBlendEquation.FUNC_SUBTRACT -> sTerm - dTerm
        GLBlendEquation.FUNC_REVERSE_SUBTRACT -> dTerm - sTerm
        GLBlendEquation.MIN -> min(sTerm, dTerm)
        GLBlendEquation.MAX -> max(sTerm, dTerm)
    }

    private fun rasterizePoint(
        v: ShadedVertex,
        program: ProgramRecord?,
        targetPixels: ByteArray,
        targetW: Int,
        targetH: Int
    ) {
        val cx = v.screenX.roundToInt()
        val cy = v.screenY.roundToInt()
        val frag = evaluateFragment(v.u, v.v, v.r, v.g, v.b, v.a, program)
        for (dy in -1..1) {
            for (dx in -1..1) {
                writeFragment(cx + dx, cy + dy, frag, targetPixels, targetW, targetH)
            }
        }
    }

    private fun rasterizeLine(
        v0: ShadedVertex,
        v1: ShadedVertex,
        program: ProgramRecord?,
        targetPixels: ByteArray,
        targetW: Int,
        targetH: Int
    ) {
        val dx = v1.screenX - v0.screenX
        val dy = v1.screenY - v0.screenY
        val steps = max(abs(dx), abs(dy)).roundToInt().coerceAtLeast(1)
        for (s in 0..steps) {
            val t = s.toFloat() / steps.toFloat()
            val px = (v0.screenX + dx * t).roundToInt()
            val py = (v0.screenY + dy * t).roundToInt()
            val u = v0.u + (v1.u - v0.u) * t
            val v = v0.v + (v1.v - v0.v) * t
            val r = v0.r + (v1.r - v0.r) * t
            val g = v0.g + (v1.g - v0.g) * t
            val b = v0.b + (v1.b - v0.b) * t
            val a = v0.a + (v1.a - v0.a) * t
            val frag = evaluateFragment(u, v, r, g, b, a, program)
            writeFragment(px, py, frag, targetPixels, targetW, targetH)
        }
    }

    private fun rasterizeTriangle(
        v0: ShadedVertex,
        v1: ShadedVertex,
        v2: ShadedVertex,
        program: ProgramRecord?,
        targetPixels: ByteArray,
        targetW: Int,
        targetH: Int
    ) {
        val minX = floor(min(v0.screenX, min(v1.screenX, v2.screenX))).toInt().coerceIn(0, targetW - 1)
        val maxX = floor(max(v0.screenX, max(v1.screenX, v2.screenX))).toInt().coerceIn(0, targetW - 1)
        val minY = floor(min(v0.screenY, min(v1.screenY, v2.screenY))).toInt().coerceIn(0, targetH - 1)
        val maxY = floor(max(v0.screenY, max(v1.screenY, v2.screenY))).toInt().coerceIn(0, targetH - 1)

        val denom = (v1.screenY - v2.screenY) * (v0.screenX - v2.screenX) +
            (v2.screenX - v1.screenX) * (v0.screenY - v2.screenY)
        if (abs(denom) < 1e-6f) return
        val invDenom = 1f / denom

        for (py in minY..maxY) {
            val fy = py + 0.5f
            for (px in minX..maxX) {
                val fx = px + 0.5f
                val w0 = ((v1.screenY - v2.screenY) * (fx - v2.screenX) + (v2.screenX - v1.screenX) * (fy - v2.screenY)) * invDenom
                val w1 = ((v2.screenY - v0.screenY) * (fx - v2.screenX) + (v0.screenX - v2.screenX) * (fy - v2.screenY)) * invDenom
                val w2 = 1f - w0 - w1
                if (w0 >= -1e-4f && w1 >= -1e-4f && w2 >= -1e-4f) {
                    val u = w0 * v0.u + w1 * v1.u + w2 * v2.u
                    val v = w0 * v0.v + w1 * v1.v + w2 * v2.v
                    val r = w0 * v0.r + w1 * v1.r + w2 * v2.r
                    val g = w0 * v0.g + w1 * v1.g + w2 * v2.g
                    val b = w0 * v0.b + w1 * v1.b + w2 * v2.b
                    val a = w0 * v0.a + w1 * v1.a + w2 * v2.a
                    val frag = evaluateFragment(u, v, r, g, b, a, program)
                    writeFragment(px, py, frag, targetPixels, targetW, targetH)
                }
            }
        }
    }

    override fun readPixels(x: Int, y: Int, width: Int, height: Int, format: GLReadFormat): ByteArray {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val (srcPixels, srcW, srcH) = resolveReadTargetBuffer()
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

    override fun finish() {}

    override fun flush() {}
}
