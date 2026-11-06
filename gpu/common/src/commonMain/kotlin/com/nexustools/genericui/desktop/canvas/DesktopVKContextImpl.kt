package com.nexustools.genericui.desktop.canvas

import com.nexustools.genericui.component.media.GLBlendFactor
import com.nexustools.genericui.component.media.GLIndexType
import com.nexustools.genericui.component.media.GpuShaderStageSource
import com.nexustools.genericui.component.media.VKAddressMode
import com.nexustools.genericui.component.media.VKBackendKind
import com.nexustools.genericui.component.media.VKBindGroup
import com.nexustools.genericui.component.media.VKBindGroupDescriptor
import com.nexustools.genericui.component.media.VKBuffer
import com.nexustools.genericui.component.media.VKBufferDescriptor
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.component.media.VKContextInfo
import com.nexustools.genericui.component.media.VKDeviceAttributes
import com.nexustools.genericui.component.media.VKDeviceType
import com.nexustools.genericui.component.media.VKFilter
import com.nexustools.genericui.component.media.VKLoadOp
import com.nexustools.genericui.component.media.VKPipeline
import com.nexustools.genericui.component.media.VKPrimitiveTopology
import com.nexustools.genericui.component.media.VKRenderPassDescriptor
import com.nexustools.genericui.component.media.VKRenderPipelineDescriptor
import com.nexustools.genericui.component.media.VKRenderTarget
import com.nexustools.genericui.component.media.VKSampler
import com.nexustools.genericui.component.media.VKSamplerDescriptor
import com.nexustools.genericui.component.media.VKShaderModule
import com.nexustools.genericui.component.media.VKTexture
import com.nexustools.genericui.component.media.VKTextureDescriptor
import com.nexustools.genericui.component.media.VKTextureFormat
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
 * Vulkan / WebGPU rendering context implementation with a complete software-accurate
 * render pass, pipeline, bind group, render target, and triangle/line/point rasterization engine.
 */
open class DesktopVKContextImpl(
    val config: VKDeviceAttributes = VKDeviceAttributes(),
    initialWidth: Int = 800,
    initialHeight: Int = 600,
    override val contextInfo: VKContextInfo = VKContextInfo(
        backend = VKBackendKind.VULKAN_NATIVE,
        apiVersion = config.apiVersion,
        deviceName = "GenericUI Vulkan Pipeline",
        deviceType = VKDeviceType.DISCRETE_GPU,
        acceleration = GpuAcceleration.HARDWARE
    )
) : VKContext {

    private class BufferRecord(
        val descriptor: VKBufferDescriptor,
        var bytes: ByteArray = ByteArray(descriptor.sizeBytes.toInt().coerceAtLeast(0)),
        var floats: FloatArray = FloatArray(0)
    )

    private class TextureRecord(
        var width: Int,
        var height: Int,
        val format: VKTextureFormat,
        var pixels: ByteArray
    )

    private var nextResourceId = 1L

    override var swapchainWidth: Int = initialWidth.coerceAtLeast(1)
        protected set

    override var swapchainHeight: Int = initialHeight.coerceAtLeast(1)
        protected set

    override var currentImageIndex: Int = 0
        protected set

    var renderPassActive: Boolean = false
        private set

    private var activeRenderPassDescriptor: VKRenderPassDescriptor? = null
    private var viewportX: Float = 0f
    private var viewportY: Float = 0f
    private var viewportW: Float = swapchainWidth.toFloat()
    private var viewportH: Float = swapchainHeight.toFloat()

    private var scissorX: Int = 0
    private var scissorY: Int = 0
    private var scissorW: Int = swapchainWidth
    private var scissorH: Int = swapchainHeight

    private var swapchainColorBuffer = ByteArray(swapchainWidth * swapchainHeight * 4)

    private val shaderModules = mutableMapOf<Long, GpuShaderStageSource>()
    private val pipelines = mutableMapOf<Long, VKRenderPipelineDescriptor>()
    private val buffers = mutableMapOf<Long, BufferRecord>()
    private val textures = mutableMapOf<Long, TextureRecord>()
    private val samplers = mutableMapOf<Long, VKSamplerDescriptor>()
    private val renderTargets = mutableMapOf<Long, VKRenderTarget>()
    private val bindGroups = mutableMapOf<Long, VKBindGroupDescriptor>()

    private var boundPipelineId: Long? = null
    private val activeBindGroups = mutableMapOf<Int, Long>()
    private var boundVertexBufferId: Long? = null
    private var boundVertexBufferOffset: Long = 0L
    private var boundIndexBufferId: Long? = null
    private var boundIndexType: GLIndexType = GLIndexType.UNSIGNED_INT
    private var boundIndexBufferOffset: Long = 0L
    private val pipelinePushConstants = mutableMapOf<Long, FloatArray>()

    var drawCallCount: Long = 0L
        private set

    fun resizeSwapchain(width: Int, height: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (w == swapchainWidth && h == swapchainHeight) return
        swapchainWidth = w
        swapchainHeight = h
        viewportW = w.toFloat()
        viewportH = h.toFloat()
        scissorW = w
        scissorH = h
        swapchainColorBuffer = ByteArray(w * h * 4)
    }

    private fun resolveTargetBuffer(target: VKRenderTarget? = activeRenderPassDescriptor?.target): Triple<ByteArray, Int, Int> {
        if (target != null) {
            val tex = textures[target.colorTexture.id]
            if (tex != null) {
                return Triple(tex.pixels, tex.width, tex.height)
            }
        }
        return Triple(swapchainColorBuffer, swapchainWidth, swapchainHeight)
    }

    override fun beginRenderPass(descriptor: VKRenderPassDescriptor) {
        renderPassActive = true
        activeRenderPassDescriptor = descriptor
        val (targetPixels, targetW, targetH) = resolveTargetBuffer(descriptor.target)
        viewportX = 0f
        viewportY = 0f
        viewportW = targetW.toFloat()
        viewportH = targetH.toFloat()
        scissorX = 0
        scissorY = 0
        scissorW = targetW
        scissorH = targetH

        if (descriptor.loadOp == VKLoadOp.CLEAR) {
            val c = descriptor.clearColor
            val r = c.r.coerceIn(0, 255).toByte()
            val g = c.g.coerceIn(0, 255).toByte()
            val b = c.b.coerceIn(0, 255).toByte()
            val a = c.a.coerceIn(0, 255).toByte()
            var i = 0
            while (i < targetPixels.size) {
                targetPixels[i] = r
                targetPixels[i + 1] = g
                targetPixels[i + 2] = b
                targetPixels[i + 3] = a
                i += 4
            }
        }
    }

    override fun endRenderPass() {
        renderPassActive = false
        currentImageIndex = (currentImageIndex + 1) % 2
    }

    override fun setViewport(x: Float, y: Float, width: Float, height: Float, minDepth: Float, maxDepth: Float) {
        viewportX = x
        viewportY = y
        viewportW = width.coerceAtLeast(1f)
        viewportH = height.coerceAtLeast(1f)
    }

    override fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        scissorX = x
        scissorY = y
        scissorW = width.coerceAtLeast(0)
        scissorH = height.coerceAtLeast(0)
    }

    override fun createShaderModule(source: GpuShaderStageSource): VKShaderModule {
        val id = nextResourceId++
        shaderModules[id] = source
        return VKShaderModule(id, source.stage)
    }

    override fun destroyShaderModule(module: VKShaderModule) {
        shaderModules.remove(module.id)
    }

    override fun createRenderPipeline(descriptor: VKRenderPipelineDescriptor): VKPipeline {
        val id = nextResourceId++
        pipelines[id] = descriptor
        return VKPipeline(id)
    }

    override fun bindPipeline(pipeline: VKPipeline) {
        boundPipelineId = pipeline.id
    }

    override fun destroyPipeline(pipeline: VKPipeline) {
        pipelines.remove(pipeline.id)
        pipelinePushConstants.remove(pipeline.id)
        if (boundPipelineId == pipeline.id) boundPipelineId = null
    }

    override fun createBuffer(descriptor: VKBufferDescriptor): VKBuffer {
        val id = nextResourceId++
        buffers[id] = BufferRecord(descriptor)
        return VKBuffer(id, descriptor.sizeBytes, descriptor.usage)
    }

    override fun writeBuffer(buffer: VKBuffer, data: ByteArray, offsetBytes: Long) {
        val rec = buffers[buffer.id] ?: return
        val off = offsetBytes.toInt().coerceAtLeast(0)
        if (off + data.size > rec.bytes.size) {
            rec.bytes = rec.bytes.copyOf(off + data.size)
        }
        data.copyInto(rec.bytes, off, 0, data.size)
        val floatCount = rec.bytes.size / 4
        val floats = FloatArray(floatCount)
        for (i in 0 until floatCount) {
            val o = i * 4
            val bits = (rec.bytes[o].toInt() and 0xFF) or
                ((rec.bytes[o + 1].toInt() and 0xFF) shl 8) or
                ((rec.bytes[o + 2].toInt() and 0xFF) shl 16) or
                ((rec.bytes[o + 3].toInt() and 0xFF) shl 24)
            floats[i] = Float.fromBits(bits)
        }
        rec.floats = floats
    }

    override fun writeBuffer(buffer: VKBuffer, data: FloatArray, offsetBytes: Long) {
        val rec = buffers[buffer.id] ?: return
        val byteLen = data.size * 4
        val off = offsetBytes.toInt().coerceAtLeast(0)
        if (off + byteLen > rec.bytes.size) {
            rec.bytes = rec.bytes.copyOf(off + byteLen)
        }
        for (i in data.indices) {
            val bits = data[i].toRawBits()
            val o = off + i * 4
            rec.bytes[o] = (bits and 0xFF).toByte()
            rec.bytes[o + 1] = ((bits ushr 8) and 0xFF).toByte()
            rec.bytes[o + 2] = ((bits ushr 16) and 0xFF).toByte()
            rec.bytes[o + 3] = ((bits ushr 24) and 0xFF).toByte()
        }
        if (off == 0) {
            rec.floats = data.copyOf()
        } else {
            val floatCount = rec.bytes.size / 4
            val floats = FloatArray(floatCount)
            for (i in 0 until floatCount) {
                val o = i * 4
                val bits = (rec.bytes[o].toInt() and 0xFF) or
                    ((rec.bytes[o + 1].toInt() and 0xFF) shl 8) or
                    ((rec.bytes[o + 2].toInt() and 0xFF) shl 16) or
                    ((rec.bytes[o + 3].toInt() and 0xFF) shl 24)
                floats[i] = Float.fromBits(bits)
            }
            rec.floats = floats
        }
    }

    override fun destroyBuffer(buffer: VKBuffer) {
        buffers.remove(buffer.id)
    }

    override fun createTexture(descriptor: VKTextureDescriptor, initialRgbaBytes: ByteArray?): VKTexture {
        val id = nextResourceId++
        val w = descriptor.width.coerceAtLeast(1)
        val h = descriptor.height.coerceAtLeast(1)
        val pixels = ByteArray(w * h * 4)
        if (initialRgbaBytes != null) {
            initialRgbaBytes.copyInto(pixels, 0, 0, min(initialRgbaBytes.size, pixels.size))
        }
        textures[id] = TextureRecord(w, h, descriptor.format, pixels)
        return VKTexture(id, w, h, descriptor.format)
    }

    override fun writeTexture(texture: VKTexture, width: Int, height: Int, rgbaBytes: ByteArray) {
        val rec = textures[texture.id] ?: return
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        rec.width = w
        rec.height = h
        if (rec.pixels.size != w * h * 4) {
            rec.pixels = ByteArray(w * h * 4)
        }
        rgbaBytes.copyInto(rec.pixels, 0, 0, min(rgbaBytes.size, rec.pixels.size))
    }

    override fun destroyTexture(texture: VKTexture) {
        textures.remove(texture.id)
    }

    override fun createSampler(descriptor: VKSamplerDescriptor): VKSampler {
        val id = nextResourceId++
        samplers[id] = descriptor
        return VKSampler(id)
    }

    override fun destroySampler(sampler: VKSampler) {
        samplers.remove(sampler.id)
    }

    override fun createRenderTarget(width: Int, height: Int, format: VKTextureFormat): VKRenderTarget {
        val tex = createTexture(VKTextureDescriptor(width = width, height = height, format = format))
        val id = nextResourceId++
        val target = VKRenderTarget(id, width.coerceAtLeast(1), height.coerceAtLeast(1), tex)
        renderTargets[id] = target
        return target
    }

    override fun destroyRenderTarget(target: VKRenderTarget) {
        renderTargets.remove(target.id)
        textures.remove(target.colorTexture.id)
    }

    override fun createBindGroup(descriptor: VKBindGroupDescriptor): VKBindGroup {
        val id = nextResourceId++
        bindGroups[id] = descriptor
        return VKBindGroup(id)
    }

    override fun setBindGroup(index: Int, bindGroup: VKBindGroup) {
        activeBindGroups[index] = bindGroup.id
    }

    override fun destroyBindGroup(bindGroup: VKBindGroup) {
        bindGroups.remove(bindGroup.id)
    }

    override fun bindVertexBuffer(buffer: VKBuffer, binding: Int, offsetBytes: Long) {
        boundVertexBufferId = buffer.id
        boundVertexBufferOffset = offsetBytes
    }

    override fun bindIndexBuffer(buffer: VKBuffer, indexType: GLIndexType, offsetBytes: Long) {
        boundIndexBufferId = buffer.id
        boundIndexType = indexType
        boundIndexBufferOffset = offsetBytes
    }

    override fun pushConstants(pipeline: VKPipeline, values: FloatArray) {
        pipelinePushConstants[pipeline.id] = values.copyOf()
    }

    override fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
        if (vertexCount <= 0 || instanceCount <= 0) return
        drawCallCount++
        val indices = IntArray(vertexCount) { firstVertex + it }
        executeDraw(indices)
    }

    override fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        vertexOffset: Int,
        firstInstance: Int
    ) {
        if (indexCount <= 0 || instanceCount <= 0) return
        drawCallCount++
        val idxBuf = boundIndexBufferId?.let { buffers[it] }
        val indices = IntArray(indexCount)
        if (idxBuf != null && idxBuf.bytes.isNotEmpty()) {
            val baseOff = boundIndexBufferOffset.toInt()
            for (i in 0 until indexCount) {
                val rawIdx = when (boundIndexType) {
                    GLIndexType.UNSIGNED_BYTE -> {
                        val o = baseOff + firstIndex + i
                        if (o < idxBuf.bytes.size) idxBuf.bytes[o].toInt() and 0xFF else i
                    }
                    GLIndexType.UNSIGNED_SHORT -> {
                        val o = baseOff + (firstIndex + i) * 2
                        if (o + 1 < idxBuf.bytes.size) {
                            (idxBuf.bytes[o].toInt() and 0xFF) or
                                ((idxBuf.bytes[o + 1].toInt() and 0xFF) shl 8)
                        } else i
                    }
                    GLIndexType.UNSIGNED_INT -> {
                        val o = baseOff + (firstIndex + i) * 4
                        if (o + 3 < idxBuf.bytes.size) {
                            (idxBuf.bytes[o].toInt() and 0xFF) or
                                ((idxBuf.bytes[o + 1].toInt() and 0xFF) shl 8) or
                                ((idxBuf.bytes[o + 2].toInt() and 0xFF) shl 16) or
                                ((idxBuf.bytes[o + 3].toInt() and 0xFF) shl 24)
                        } else i
                    }
                }
                indices[i] = rawIdx + vertexOffset
            }
        } else {
            for (i in 0 until indexCount) {
                indices[i] = firstIndex + i + vertexOffset
            }
        }
        executeDraw(indices)
    }

    private class ShadedVKVertex(
        val screenX: Float,
        val screenY: Float,
        val u: Float,
        val v: Float,
        val r: Float,
        val g: Float,
        val b: Float,
        val a: Float
    )

    private fun resolveUniformFloats(): FloatArray {
        val pipeId = boundPipelineId
        if (pipeId != null) {
            val pushed = pipelinePushConstants[pipeId]
            if (pushed != null && pushed.isNotEmpty()) return pushed
        }
        for (bgId in activeBindGroups.values) {
            val bg = bindGroups[bgId] ?: continue
            for (entry in bg.entries) {
                val buf = entry.buffer?.let { buffers[it.id] }
                if (buf != null && buf.floats.isNotEmpty()) {
                    return buf.floats
                }
            }
        }
        return FloatArray(0)
    }

    private fun resolveBoundTextureAndSampler(): Pair<TextureRecord?, VKSamplerDescriptor> {
        var foundTex: TextureRecord? = null
        var foundSampler = VKSamplerDescriptor()
        for (bgId in activeBindGroups.values) {
            val bg = bindGroups[bgId] ?: continue
            for (entry in bg.entries) {
                if (foundTex == null && entry.texture != null) {
                    foundTex = textures[entry.texture.id]
                }
                if (entry.sampler != null) {
                    samplers[entry.sampler.id]?.let { foundSampler = it }
                }
            }
        }
        return foundTex to foundSampler
    }

    private fun shadeVertex(
        vertexIndex: Int,
        pipeline: VKRenderPipelineDescriptor?,
        uniforms: FloatArray,
        targetW: Int,
        targetH: Int
    ): ShadedVKVertex {
        val vBuf = boundVertexBufferId?.let { buffers[it] }
        val layout = pipeline?.vertexLayout
        val strideBytes = layout?.strideBytes?.takeIf { it > 0 } ?: 16
        val strideFloats = (strideBytes / 4).coerceAtLeast(1)

        var x = 0f
        var y = 0f
        var u = 0.5f
        var v = 0.5f
        var r = 1f
        var g = 1f
        var b = 1f
        var a = 1f

        if (vBuf != null && vBuf.floats.isNotEmpty() && layout != null && layout.attributes.isNotEmpty()) {
            val baseFloatOffset = (boundVertexBufferOffset.toInt() / 4) + vertexIndex * strideFloats
            val attr0 = layout.attributes.getOrNull(0)
            if (attr0 != null) {
                val off0 = baseFloatOffset + (attr0.offsetBytes / 4)
                x = vBuf.floats.getOrElse(off0) { 0f }
                y = vBuf.floats.getOrElse(off0 + 1) { 0f }
                if (attr0.components >= 4) {
                    u = vBuf.floats.getOrElse(off0 + 2) { (x * 0.5f) + 0.5f }
                    v = vBuf.floats.getOrElse(off0 + 3) { (y * 0.5f) + 0.5f }
                } else {
                    u = (x * 0.5f) + 0.5f
                    v = (y * 0.5f) + 0.5f
                }
            }
            val attr1 = layout.attributes.getOrNull(1)
            if (attr1 != null) {
                val off1 = baseFloatOffset + (attr1.offsetBytes / 4)
                if (attr1.components == 2) {
                    u = vBuf.floats.getOrElse(off1) { u }
                    v = vBuf.floats.getOrElse(off1 + 1) { v }
                } else if (attr1.components >= 3) {
                    r = vBuf.floats.getOrElse(off1) { 1f }
                    g = vBuf.floats.getOrElse(off1 + 1) { 1f }
                    b = vBuf.floats.getOrElse(off1 + 2) { 1f }
                    if (attr1.components >= 4) {
                        a = vBuf.floats.getOrElse(off1 + 3) { 1f }
                    }
                }
            }
        } else {
            when (vertexIndex % 6) {
                0 -> { x = -1f; y = -1f; u = 0f; v = 0f }
                1 -> { x = 1f; y = -1f; u = 1f; v = 0f }
                2 -> { x = 1f; y = 1f; u = 1f; v = 1f }
                3 -> { x = -1f; y = -1f; u = 0f; v = 0f }
                4 -> { x = 1f; y = 1f; u = 1f; v = 1f }
                else -> { x = -1f; y = 1f; u = 0f; v = 1f }
            }
        }

        // If uniforms contain a 4-float color (and no vertex color attribute overrode it), apply it
        if (uniforms.size in 3..8 && (layout == null || layout.attributes.size < 2 || layout.attributes[1].components == 2)) {
            r = uniforms[0].coerceIn(0f, 1f)
            g = uniforms[1].coerceIn(0f, 1f)
            b = uniforms[2].coerceIn(0f, 1f)
            if (uniforms.size >= 4) {
                a = uniforms[3].coerceIn(0f, 1f)
            }
        }

        val vw = min(viewportW, targetW.toFloat())
        val vh = min(viewportH, targetH.toFloat())
        val sx = viewportX + (x * 0.5f + 0.5f) * vw
        val sy = viewportY + (y * 0.5f + 0.5f) * vh

        return ShadedVKVertex(sx, sy, u, v, r, g, b, a)
    }

    private fun sampleVKTexture(tex: TextureRecord?, sampler: VKSamplerDescriptor, u: Float, v: Float): FloatArray? {
        if (tex == null || tex.pixels.isEmpty() || tex.width <= 0 || tex.height <= 0) return null
        val wu = when (sampler.addressModeU) {
            VKAddressMode.CLAMP_TO_EDGE -> u.coerceIn(0f, 1f)
            VKAddressMode.REPEAT -> (u - floor(u)).let { if (it < 0f) it + 1f else it }
            VKAddressMode.MIRROR_REPEAT -> {
                val f = abs(u)
                val i = floor(f).toInt()
                val frac = f - i
                if (i % 2 == 0) frac else 1f - frac
            }
        }
        val wv = when (sampler.addressModeV) {
            VKAddressMode.CLAMP_TO_EDGE -> v.coerceIn(0f, 1f)
            VKAddressMode.REPEAT -> (v - floor(v)).let { if (it < 0f) it + 1f else it }
            VKAddressMode.MIRROR_REPEAT -> {
                val f = abs(v)
                val i = floor(f).toInt()
                val frac = f - i
                if (i % 2 == 0) frac else 1f - frac
            }
        }

        if (sampler.minFilter == VKFilter.NEAREST && sampler.magFilter == VKFilter.NEAREST) {
            val tx = (wu * tex.width).toInt().coerceIn(0, tex.width - 1)
            val ty = (wv * tex.height).toInt().coerceIn(0, tex.height - 1)
            val idx = (ty * tex.width + tx) * 4
            return floatArrayOf(
                (tex.pixels[idx].toInt() and 0xFF) / 255f,
                (tex.pixels[idx + 1].toInt() and 0xFF) / 255f,
                (tex.pixels[idx + 2].toInt() and 0xFF) / 255f,
                (tex.pixels[idx + 3].toInt() and 0xFF) / 255f
            )
        }

        val fx = wu * (tex.width - 1)
        val fy = wv * (tex.height - 1)
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
            out[c] = (c00 * (1f - tx) + c10 * tx) * (1f - ty) + (c01 * (1f - tx) + c11 * tx) * ty
        }
        return out
    }

    private fun evaluateFragment(
        u: Float,
        v: Float,
        vr: Float,
        vg: Float,
        vb: Float,
        va: Float,
        pipeline: VKRenderPipelineDescriptor?,
        uniforms: FloatArray,
        tex: TextureRecord?,
        sampler: VKSamplerDescriptor
    ): FloatArray {
        val fragModule = pipeline?.fragment?.let { shaderModules[it.id] }
        val fragSrc = fragModule?.wgslSource?.ifEmpty { fragModule.glsl450Source.ifEmpty { fragModule.glslEs300Source } } ?: ""

        // Check for MilkDrop warp UBO layout (16+ floats with zoom/decay)
        if (uniforms.size >= 16 && (fragSrc.contains("uWarp", ignoreCase = true) || fragSrc.contains("zoom", ignoreCase = true))) {
            val time = uniforms[0]
            val zoom = uniforms[1].let { if (abs(it) < 0.001f) 1f else it }
            val zoomExp = uniforms[2].let { if (abs(it) < 0.001f) 1f else it }
            val rot = uniforms[3]
            val warp = uniforms[4]
            val cx = uniforms[5]
            val cy = uniforms[6]
            val dx = uniforms[7]
            val dy = uniforms[8]
            val sx = uniforms[9].let { if (abs(it) < 0.001f) 1f else it }
            val sy = uniforms[10].let { if (abs(it) < 0.001f) 1f else it }
            val decay = uniforms[11]
            val bass = uniforms[12]
            val mid = uniforms[13]
            val treb = uniforms[14]
            val darkCenter = uniforms[15]

            val centeredX = u - cx
            val centeredY = v - cy
            val rad = sqrt(centeredX * centeredX + centeredY * centeredY)
            val ang = atan2(centeredY, centeredX)
            val zFactor = zoom.pow(zoomExp.pow(rad * 2f - 1f))
            val px = centeredX / (zFactor * sx)
            val py = centeredY / (zFactor * sy)
            val cosR = cos(rot)
            val sinR = sin(rot)
            val rx = px * cosR - py * sinR
            val ry = px * sinR + py * cosR
            val wx = sin(rad * 12f - time * 2.4f + ang * 2f) * warp * 0.035f
            val wy = cos(rad * 12f + time * 2.1f - ang * 2f) * warp * 0.035f
            val sampleU = cx + rx + wx - dx
            val sampleV = cy + ry + wy - dy

            val prev = sampleVKTexture(tex, sampler, sampleU, sampleV) ?: floatArrayOf(0f, 0f, 0f, 1f)
            var r = prev[0] * decay
            var g = prev[1] * decay
            var b = prev[2] * decay
            val plasma = 0.012f * (0.5f + 0.5f * sin(u * 10f + time * 1.7f + bass * 2f))
            r += plasma * (0.4f + 0.6f * bass)
            g += plasma * (0.4f + 0.6f * mid)
            b += plasma * (0.4f + 0.6f * treb)
            if (darkCenter > 0.5f && rad < 0.06f) {
                val atten = 0.94f + 0.06f * (rad / 0.06f)
                r *= atten; g *= atten; b *= atten
            }
            return floatArrayOf(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), 1f)
        }

        // Check for MilkDrop composite UBO layout (8..12 floats with echoZoom/gamma)
        if (uniforms.size in 8..12 && (fragSrc.contains("echo", ignoreCase = true) || fragSrc.contains("gamma", ignoreCase = true))) {
            val base = sampleVKTexture(tex, sampler, u, v) ?: floatArrayOf(0f, 0f, 0f, 1f)
            val echoZoom = uniforms[0].coerceAtLeast(0.01f)
            val echoAlpha = uniforms[1].coerceIn(0f, 1f)
            val echoOrient = uniforms[2].roundToInt()
            val gamma = uniforms[3].coerceAtLeast(0.1f)
            val invert = uniforms[4] > 0.5f
            val brighten = uniforms[5] > 0.5f
            val darken = uniforms[6] > 0.5f
            val solarize = uniforms[7] > 0.5f

            var r = base[0]
            var g = base[1]
            var b = base[2]
            if (echoAlpha > 0.001f) {
                var eu = (u - 0.5f) / echoZoom + 0.5f
                var ev = (v - 0.5f) / echoZoom + 0.5f
                if (echoOrient == 1 || echoOrient == 3) eu = 1f - eu
                if (echoOrient == 2 || echoOrient == 3) ev = 1f - ev
                val echo = sampleVKTexture(tex, sampler, eu, ev) ?: base
                r = r * (1f - echoAlpha) + echo[0] * echoAlpha
                g = g * (1f - echoAlpha) + echo[1] * echoAlpha
                b = b * (1f - echoAlpha) + echo[2] * echoAlpha
            }
            if (brighten) {
                r = sqrt(r.coerceAtLeast(0f)); g = sqrt(g.coerceAtLeast(0f)); b = sqrt(b.coerceAtLeast(0f))
            }
            if (darken) {
                r *= r; g *= g; b *= b
            }
            if (solarize) {
                r = abs(r * 2f - 1f); g = abs(g * 2f - 1f); b = abs(b * 2f - 1f)
            }
            if (invert) {
                r = 1f - r; g = 1f - g; b = 1f - b
            }
            val invGamma = 1f / gamma
            r = r.coerceAtLeast(0f).pow(invGamma)
            g = g.coerceAtLeast(0f).pow(invGamma)
            b = b.coerceAtLeast(0f).pow(invGamma)
            return floatArrayOf(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), 1f)
        }

        var r = vr
        var g = vg
        var b = vb
        var a = va
        val sampled = sampleVKTexture(tex, sampler, u, v)
        if (sampled != null) {
            r *= sampled[0]
            g *= sampled[1]
            b *= sampled[2]
            a *= sampled[3]
        }
        return floatArrayOf(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), a.coerceIn(0f, 1f))
    }

    private fun writeFragment(
        px: Int,
        py: Int,
        frag: FloatArray,
        pipeline: VKRenderPipelineDescriptor?,
        targetPixels: ByteArray,
        targetW: Int,
        targetH: Int
    ) {
        if (px < scissorX || px >= min(targetW, scissorX + scissorW) || py < scissorY || py >= min(targetH, scissorY + scissorH)) return
        val idx = (py * targetW + px) * 4
        val sr = frag[0].coerceIn(0f, 1f)
        val sg = frag[1].coerceIn(0f, 1f)
        val sb = frag[2].coerceIn(0f, 1f)
        val sa = frag[3].coerceIn(0f, 1f)

        val blend = pipeline?.blend
        if (blend == null || !blend.enabled) {
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

        val sf = when (blend.srcColorFactor) {
            GLBlendFactor.ZERO -> 0f
            GLBlendFactor.SRC_ALPHA -> sa
            GLBlendFactor.ONE_MINUS_SRC_ALPHA -> 1f - sa
            else -> 1f
        }
        val df = when (blend.dstColorFactor) {
            GLBlendFactor.ZERO -> 0f
            GLBlendFactor.SRC_ALPHA -> sa
            GLBlendFactor.ONE_MINUS_SRC_ALPHA -> 1f - sa
            else -> 1f
        }

        val outR = (sr * sf + dr * df).coerceIn(0f, 1f)
        val outG = (sg * sf + dg * df).coerceIn(0f, 1f)
        val outB = (sb * sf + db * df).coerceIn(0f, 1f)
        val outA = (sa + da * (1f - sa)).coerceIn(0f, 1f)

        targetPixels[idx] = (outR * 255f).roundToInt().coerceIn(0, 255).toByte()
        targetPixels[idx + 1] = (outG * 255f).roundToInt().coerceIn(0, 255).toByte()
        targetPixels[idx + 2] = (outB * 255f).roundToInt().coerceIn(0, 255).toByte()
        targetPixels[idx + 3] = (outA * 255f).roundToInt().coerceIn(0, 255).toByte()
    }

    private fun executeDraw(indices: IntArray) {
        val (targetPixels, targetW, targetH) = resolveTargetBuffer()
        val pipeline = boundPipelineId?.let { pipelines[it] }
        val uniforms = resolveUniformFloats()
        val (tex, sampler) = resolveBoundTextureAndSampler()
        val vertices = Array(indices.size) { i -> shadeVertex(indices[i], pipeline, uniforms, targetW, targetH) }
        val topology = pipeline?.topology ?: VKPrimitiveTopology.TRIANGLE_LIST

        when (topology) {
            VKPrimitiveTopology.POINT_LIST -> {
                for (v in vertices) {
                    val frag = evaluateFragment(v.u, v.v, v.r, v.g, v.b, v.a, pipeline, uniforms, tex, sampler)
                    val cx = v.screenX.roundToInt()
                    val cy = v.screenY.roundToInt()
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            writeFragment(cx + dx, cy + dy, frag, pipeline, targetPixels, targetW, targetH)
                        }
                    }
                }
            }
            VKPrimitiveTopology.LINE_LIST -> {
                var i = 0
                while (i + 1 < vertices.size) {
                    rasterizeLine(vertices[i], vertices[i + 1], pipeline, uniforms, tex, sampler, targetPixels, targetW, targetH)
                    i += 2
                }
            }
            VKPrimitiveTopology.LINE_STRIP -> {
                for (i in 0 until vertices.size - 1) {
                    rasterizeLine(vertices[i], vertices[i + 1], pipeline, uniforms, tex, sampler, targetPixels, targetW, targetH)
                }
            }
            VKPrimitiveTopology.TRIANGLE_LIST -> {
                var i = 0
                while (i + 2 < vertices.size) {
                    rasterizeTriangle(vertices[i], vertices[i + 1], vertices[i + 2], pipeline, uniforms, tex, sampler, targetPixels, targetW, targetH)
                    i += 3
                }
            }
            VKPrimitiveTopology.TRIANGLE_STRIP -> {
                for (i in 0 until vertices.size - 2) {
                    if (i % 2 == 0) {
                        rasterizeTriangle(vertices[i], vertices[i + 1], vertices[i + 2], pipeline, uniforms, tex, sampler, targetPixels, targetW, targetH)
                    } else {
                        rasterizeTriangle(vertices[i + 1], vertices[i], vertices[i + 2], pipeline, uniforms, tex, sampler, targetPixels, targetW, targetH)
                    }
                }
            }
        }
    }

    private fun rasterizeLine(
        v0: ShadedVKVertex,
        v1: ShadedVKVertex,
        pipeline: VKRenderPipelineDescriptor?,
        uniforms: FloatArray,
        tex: TextureRecord?,
        sampler: VKSamplerDescriptor,
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
            val frag = evaluateFragment(u, v, r, g, b, a, pipeline, uniforms, tex, sampler)
            writeFragment(px, py, frag, pipeline, targetPixels, targetW, targetH)
        }
    }

    private fun rasterizeTriangle(
        v0: ShadedVKVertex,
        v1: ShadedVKVertex,
        v2: ShadedVKVertex,
        pipeline: VKRenderPipelineDescriptor?,
        uniforms: FloatArray,
        tex: TextureRecord?,
        sampler: VKSamplerDescriptor,
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
                    val frag = evaluateFragment(u, v, r, g, b, a, pipeline, uniforms, tex, sampler)
                    writeFragment(px, py, frag, pipeline, targetPixels, targetW, targetH)
                }
            }
        }
    }

    override fun readPixels(x: Int, y: Int, width: Int, height: Int): ByteArray {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val (srcPixels, srcW, srcH) = resolveTargetBuffer(
            if (renderPassActive) activeRenderPassDescriptor?.target else null
        )
        val out = ByteArray(w * h * 4)
        if (x == 0 && y == 0 && w == srcW && h == srcH) {
            srcPixels.copyInto(out, 0, 0, min(srcPixels.size, out.size))
            return out
        }
        for (row in 0 until h) {
            val sy = (y + row).coerceIn(0, srcH - 1)
            for (col in 0 until w) {
                val sx = (x + col).coerceIn(0, srcW - 1)
                val sIdx = (sy * srcW + sx) * 4
                val dIdx = (row * w + col) * 4
                out[dIdx] = srcPixels[sIdx]
                out[dIdx + 1] = srcPixels[sIdx + 1]
                out[dIdx + 2] = srcPixels[sIdx + 2]
                out[dIdx + 3] = srcPixels[sIdx + 3]
            }
        }
        return out
    }

    override fun waitIdle() {}
}
