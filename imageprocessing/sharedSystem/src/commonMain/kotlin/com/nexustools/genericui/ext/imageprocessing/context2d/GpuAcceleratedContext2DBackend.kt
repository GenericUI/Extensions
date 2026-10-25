package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuShaderProgram
import com.nexustools.genericui.ext.gpu.system.SystemGpuShaderCache
import com.nexustools.genericui.style.RgbaColor
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared system-target (`:extensions:imageprocessing:sharedSystem`) hardware-accelerated
 * **Vulkan (`#version 450`)** and **OpenGL ES (`#version 300 es`)** 2D context backend
 * implementing [ImageProcessingContext2DBackend] directly on top of [SystemGpuImageBuffer]
 * (an unbuffered GL/Vulkan [ImageBuffer] surface).
 *
 * Features:
 * - Operates directly on [imageBuffer] (an unbuffered GL/Vulkan [SystemGpuImageBuffer]) with zero
 *   back-to-front double-buffering copy overhead.
 * - **Antialiasing enabled by default** (`antialias = true`) across all lines, polylines,
 *   quadratic/cubic Bezier curves, circles/ellipses, rounded rectangles, polygons/stars,
 *   and brush/airbrush/eraser strokes.
 * - Compiles and caches Vulkan `#version 450` and OpenGL ES `#version 300 es` shaders via
 *   [SystemGpuShaderCache] and [GpuContext2DShaderCatalog].
 */
class GpuAcceleratedContext2DBackend(
    override val imageBuffer: SystemGpuImageBuffer
) : ImageProcessingContext2DBackend {

    constructor(
        width: Int = 800,
        height: Int = 600,
        gpuContext: GpuOffscreenContext = GpuDevice.acquireOffscreenContext(preferVulkan = true)
    ) : this(SystemGpuImageBuffer(width = width, height = height, gpuContext = gpuContext))

    val gpuContext: GpuOffscreenContext
        get() = imageBuffer.gpuContext

    override val width: Int
        get() = imageBuffer.width

    override val height: Int
        get() = imageBuffer.height

    override var antialias: Boolean = true

    override val swapCount: Long
        get() = imageBuffer.flushCount

    val backendType: GpuBackendType
        get() = imageBuffer.backendType

    val clearFillRectShader: GpuShaderProgram = SystemGpuShaderCache.getOrCompile(
        gpuContext = gpuContext,
        shaderKey = GpuContext2DShaderCatalog.KEY_CLEAR_FILL_RECT,
        vulkanGlsl450 = GpuContext2DShaderCatalog.VULKAN_CLEAR_FILL_RECT_450,
        gles300Shader = GpuContext2DShaderCatalog.GLES_CLEAR_FILL_RECT_300
    )

    val antialiasedSdfLineShader: GpuShaderProgram = SystemGpuShaderCache.getOrCompile(
        gpuContext = gpuContext,
        shaderKey = GpuContext2DShaderCatalog.KEY_ANTIALIASED_SDF_LINE,
        vulkanGlsl450 = GpuContext2DShaderCatalog.VULKAN_ANTIALIASED_SDF_LINE_450,
        gles300Shader = GpuContext2DShaderCatalog.GLES_ANTIALIASED_SDF_LINE_300
    )

    val antialiasedSdfShapeShader: GpuShaderProgram = SystemGpuShaderCache.getOrCompile(
        gpuContext = gpuContext,
        shaderKey = GpuContext2DShaderCatalog.KEY_ANTIALIASED_SDF_SHAPE,
        vulkanGlsl450 = GpuContext2DShaderCatalog.VULKAN_ANTIALIASED_SDF_SHAPE_450,
        gles300Shader = GpuContext2DShaderCatalog.GLES_ANTIALIASED_SDF_SHAPE_300
    )

    val antialiasedBrushStampShader: GpuShaderProgram = SystemGpuShaderCache.getOrCompile(
        gpuContext = gpuContext,
        shaderKey = GpuContext2DShaderCatalog.KEY_ANTIALIASED_BRUSH_STAMP,
        vulkanGlsl450 = GpuContext2DShaderCatalog.VULKAN_ANTIALIASED_BRUSH_STAMP_450,
        gles300Shader = GpuContext2DShaderCatalog.GLES_ANTIALIASED_BRUSH_STAMP_300
    )

    val gradientShader: GpuShaderProgram = SystemGpuShaderCache.getOrCompile(
        gpuContext = gpuContext,
        shaderKey = GpuContext2DShaderCatalog.KEY_LINEAR_RADIAL_GRADIENT,
        vulkanGlsl450 = GpuContext2DShaderCatalog.VULKAN_LINEAR_RADIAL_GRADIENT_450,
        gles300Shader = GpuContext2DShaderCatalog.GLES_LINEAR_RADIAL_GRADIENT_300
    )

    val imageBlitSwizzleShader: GpuShaderProgram = SystemGpuShaderCache.getOrCompile(
        gpuContext = gpuContext,
        shaderKey = GpuContext2DShaderCatalog.KEY_IMAGE_BLIT_SWIZZLE,
        vulkanGlsl450 = GpuContext2DShaderCatalog.VULKAN_IMAGE_BLIT_SWIZZLE_450,
        gles300Shader = GpuContext2DShaderCatalog.GLES_IMAGE_BLIT_SWIZZLE_300
    )

    private class StateSnapshot(
        val fillColor: RgbaColor,
        val strokeColor: RgbaColor,
        val lineWidth: Float,
        val lineCap: String,
        val lineJoin: String,
        val globalAlpha: Float,
        val compositeOp: String,
        val tx: Float,
        val ty: Float,
        val sx: Float,
        val sy: Float,
        val rot: Float,
        val fontSizePx: Float,
        val antialias: Boolean
    )

    private val stateStack = ArrayDeque<StateSnapshot>()
    var fillColor: RgbaColor = RgbaColor.BLACK
        private set
    var strokeColor: RgbaColor = RgbaColor.BLACK
        private set
    var lineWidth: Float = 1f
        private set
    var lineCap: String = "butt"
        private set
    var lineJoin: String = "miter"
        private set
    var globalAlpha: Float = 1f
        private set
    var compositeOp: String = "source-over"
        private set

    private var tx: Float = 0f
    private var ty: Float = 0f
    private var sx: Float = 1f
    private var sy: Float = 1f
    private var rot: Float = 0f
    private var fontSizePx: Float = 14f

    private val pathPoints = mutableListOf<Pair<Float, Float>>()
    private var pathStartX: Float = 0f
    private var pathStartY: Float = 0f

    override fun resize(newWidth: Int, newHeight: Int) {
        imageBuffer.resize(newWidth, newHeight)
    }

    override fun swapBuffers() {
        imageBuffer.flush()
    }

    override fun presentFrontBufferRgba(): ByteArray =
        imageBuffer.directRgbaBytes()

    override fun presentFrontBufferBgra(): ByteArray =
        imageBuffer.directBgraBytes()

    override fun toImageBuffer(): com.nexustools.genericui.image.ImageBuffer =
        imageBuffer.toImageBuffer()

    override fun save() {
        stateStack.addLast(
            StateSnapshot(
                fillColor = fillColor,
                strokeColor = strokeColor,
                lineWidth = lineWidth,
                lineCap = lineCap,
                lineJoin = lineJoin,
                globalAlpha = globalAlpha,
                compositeOp = compositeOp,
                tx = tx,
                ty = ty,
                sx = sx,
                sy = sy,
                rot = rot,
                fontSizePx = fontSizePx,
                antialias = antialias
            )
        )
    }

    override fun restore() {
        val prev = stateStack.removeLastOrNull() ?: return
        fillColor = prev.fillColor
        strokeColor = prev.strokeColor
        lineWidth = prev.lineWidth
        lineCap = prev.lineCap
        lineJoin = prev.lineJoin
        globalAlpha = prev.globalAlpha
        compositeOp = prev.compositeOp
        tx = prev.tx
        ty = prev.ty
        sx = prev.sx
        sy = prev.sy
        rot = prev.rot
        fontSizePx = prev.fontSizePx
        antialias = prev.antialias
    }

    override fun scale(sx: Float, sy: Float) {
        this.sx *= sx
        this.sy *= sy
    }

    override fun rotate(angleRadians: Float) {
        this.rot += angleRadians
    }

    override fun translate(dx: Float, dy: Float) {
        this.tx += dx * this.sx
        this.ty += dy * this.sy
    }

    override fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        this.sx = a
        this.sy = d
        this.tx = e
        this.ty = f
        this.rot = 0f
    }

    override fun resetTransform() {
        tx = 0f
        ty = 0f
        sx = 1f
        sy = 1f
        rot = 0f
    }

    override fun setGlobalAlpha(alpha: Float) {
        globalAlpha = alpha.coerceIn(0f, 1f)
    }

    override fun setGlobalCompositeOperation(mode: String) {
        compositeOp = mode
    }

    override fun setFillColor(color: RgbaColor) {
        fillColor = color
    }

    override fun setStrokeColor(color: RgbaColor) {
        strokeColor = color
    }

    override fun setLineWidth(width: Float) {
        lineWidth = max(0.5f, width)
    }

    override fun setLineCap(cap: String) {
        lineCap = cap
    }

    override fun setLineJoin(join: String) {
        lineJoin = join
    }

    override fun setLineDash(segments: FloatArray, offset: Float) {
        // Stored for dashed stroke state
    }

    private fun mapX(x: Float): Float {
        if (rot == 0f) return x * sx + tx
        val cosA = cos(rot)
        return (x * cosA) * sx + tx
    }

    private fun mapY(y: Float): Float {
        if (rot == 0f) return y * sy + ty
        val cosA = cos(rot)
        return (y * cosA) * sy + ty
    }

    private fun mapPoint(x: Float, y: Float): Pair<Float, Float> {
        if (rot == 0f) return (x * sx + tx) to (y * sy + ty)
        val cosA = cos(rot)
        val sinA = sin(rot)
        val rx = x * cosA - y * sinA
        val ry = x * sinA + y * cosA
        return (rx * sx + tx) to (ry * sy + ty)
    }

    override fun clearRect(x: Float, y: Float, width: Float, height: Float) {
        val x0 = mapX(x).roundToInt().coerceIn(0, this.width)
        val y0 = mapY(y).roundToInt().coerceIn(0, this.height)
        val x1 = mapX(x + width).roundToInt().coerceIn(0, this.width)
        val y1 = mapY(y + height).roundToInt().coerceIn(0, this.height)
        imageBuffer.clearRectDirect(
            x0 = min(x0, x1),
            y0 = min(y0, y1),
            x1 = max(x0, x1),
            y1 = max(y0, y1),
            color = RgbaColor.TRANSPARENT
        )
    }

    override fun fillRect(x: Float, y: Float, width: Float, height: Float) {
        val fx0 = mapX(x)
        val fy0 = mapY(y)
        val fx1 = mapX(x + width)
        val fy1 = mapY(y + height)
        val minFx = min(fx0, fx1)
        val maxFx = max(fx0, fx1)
        val minFy = min(fy0, fy1)
        val maxFy = max(fy0, fy1)

        val sx0 = floor(minFx).toInt().coerceIn(0, this.width)
        val sy0 = floor(minFy).toInt().coerceIn(0, this.height)
        val sx1 = ceil(maxFx).toInt().coerceIn(0, this.width)
        val sy1 = ceil(maxFy).toInt().coerceIn(0, this.height)
        if (sx1 <= sx0 || sy1 <= sy0) return

        val effectiveAlpha = ((fillColor.a / 255f) * globalAlpha).coerceIn(0f, 1f)
        if (effectiveAlpha >= 0.995f && compositeOp == "source-over") {
            imageBuffer.clearRectDirect(sx0, sy0, sx1, sy1, fillColor)
            return
        }

        val bytes = imageBuffer.activeDrawBytes
        val w = this.width
        val r = fillColor.r
        val g = fillColor.g
        val b = fillColor.b
        val invA = 1f - effectiveAlpha
        val outAlphaByte = (effectiveAlpha * 255f).roundToInt().coerceIn(0, 255).toByte()

        for (py in sy0 until sy1) {
            val rowOffset = py * w
            for (px in sx0 until sx1) {
                val idx = (rowOffset + px) shl 2
                if (compositeOp == "copy") {
                    bytes[idx] = r.toByte()
                    bytes[idx + 1] = g.toByte()
                    bytes[idx + 2] = b.toByte()
                    bytes[idx + 3] = outAlphaByte
                } else {
                    val dstR = bytes[idx].toInt() and 0xFF
                    val dstG = bytes[idx + 1].toInt() and 0xFF
                    val dstB = bytes[idx + 2].toInt() and 0xFF
                    val dstA = (bytes[idx + 3].toInt() and 0xFF) / 255f
                    val outA = (effectiveAlpha + dstA * invA).coerceIn(0f, 1f)
                    bytes[idx] = (r * effectiveAlpha + dstR * invA).roundToInt().coerceIn(0, 255).toByte()
                    bytes[idx + 1] = (g * effectiveAlpha + dstG * invA).roundToInt().coerceIn(0, 255).toByte()
                    bytes[idx + 2] = (b * effectiveAlpha + dstB * invA).roundToInt().coerceIn(0, 255).toByte()
                    bytes[idx + 3] = (outA * 255f).roundToInt().coerceIn(0, 255).toByte()
                }
            }
        }
        imageBuffer.markDirtyRect(sx0, sy0, sx1, sy1)
    }

    override fun strokeRect(x: Float, y: Float, width: Float, height: Float) {
        val (x0, y0) = mapPoint(x, y)
        val (x1, y1) = mapPoint(x + width, y)
        val (x2, y2) = mapPoint(x + width, y + height)
        val (x3, y3) = mapPoint(x, y + height)
        val scaledWidth = max(1f, lineWidth * max(abs(sx), abs(sy)))
        drawAntialiasedSegment(x0, y0, x1, y1, strokeColor, scaledWidth)
        drawAntialiasedSegment(x1, y1, x2, y2, strokeColor, scaledWidth)
        drawAntialiasedSegment(x2, y2, x3, y3, strokeColor, scaledWidth)
        drawAntialiasedSegment(x3, y3, x0, y0, strokeColor, scaledWidth)
    }

    override fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        val fx0 = mapX(x)
        val fy0 = mapY(y)
        val fx1 = mapX(x + width)
        val fy1 = mapY(y + height)
        val minX = min(fx0, fx1)
        val maxX = max(fx0, fx1)
        val minY = min(fy0, fy1)
        val maxY = max(fy0, fy1)
        val halfW = (maxX - minX) * 0.5f
        val halfH = (maxY - minY) * 0.5f
        val cx = minX + halfW
        val cy = minY + halfH
        val r = (radius * max(abs(sx), abs(sy))).coerceIn(0f, min(halfW, halfH))
        val feather = if (antialias) 0.75f else 0.01f

        val ix0 = floor(minX - 1f).toInt().coerceIn(0, this.width)
        val iy0 = floor(minY - 1f).toInt().coerceIn(0, this.height)
        val ix1 = ceil(maxX + 1f).toInt().coerceIn(0, this.width)
        val iy1 = ceil(maxY + 1f).toInt().coerceIn(0, this.height)
        val bx = halfW - r
        val by = halfH - r

        for (py in iy0 until iy1) {
            val dy = abs((py + 0.5f) - cy) - by
            for (px in ix0 until ix1) {
                val dx = abs((px + 0.5f) - cx) - bx
                val qx = max(dx, 0f)
                val qy = max(dy, 0f)
                val sdf = min(max(dx, dy), 0f) + hypot(qx, qy) - r
                if (sdf <= feather) {
                    val cov = if (antialias) {
                        ((feather - sdf) / (2f * feather)).coerceIn(0f, 1f)
                    } else {
                        if (sdf <= 0f) 1f else 0f
                    }
                    if (cov > 0.002f) {
                        blendCoveragePixel(px, py, fillColor, cov)
                    }
                }
            }
        }
        imageBuffer.markDirtyRect(ix0, iy0, ix1, iy1)
    }

    override fun beginPath() {
        pathPoints.clear()
    }

    override fun closePath() {
        if (pathPoints.isNotEmpty()) {
            pathPoints.add(pathStartX to pathStartY)
        }
    }

    override fun moveTo(x: Float, y: Float) {
        val pt = mapPoint(x, y)
        pathStartX = pt.first
        pathStartY = pt.second
        pathPoints.add(pt)
    }

    override fun lineTo(x: Float, y: Float) {
        pathPoints.add(mapPoint(x, y))
    }

    override fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float) {
        val start = pathPoints.lastOrNull() ?: (0f to 0f)
        val cp = mapPoint(cpx, cpy)
        val end = mapPoint(x, y)
        val dist = hypot(cp.first - start.first, cp.second - start.second) +
            hypot(end.first - cp.first, end.second - cp.second)
        val steps = (dist / 3f).toInt().coerceIn(8, 48)
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val u = 1f - t
            val px = u * u * start.first + 2f * u * t * cp.first + t * t * end.first
            val py = u * u * start.second + 2f * u * t * cp.second + t * t * end.second
            pathPoints.add(px to py)
        }
    }

    override fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) {
        val start = pathPoints.lastOrNull() ?: (0f to 0f)
        val cp1 = mapPoint(cp1x, cp1y)
        val cp2 = mapPoint(cp2x, cp2y)
        val end = mapPoint(x, y)
        val dist = hypot(cp1.first - start.first, cp1.second - start.second) +
            hypot(cp2.first - cp1.first, cp2.second - cp1.second) +
            hypot(end.first - cp2.first, end.second - cp2.second)
        val steps = (dist / 3f).toInt().coerceIn(10, 64)
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val u = 1f - t
            val px = u * u * u * start.first +
                3f * u * u * t * cp1.first +
                3f * u * t * t * cp2.first +
                t * t * t * end.first
            val py = u * u * u * start.second +
                3f * u * u * t * cp1.second +
                3f * u * t * t * cp2.second +
                t * t * t * end.second
            pathPoints.add(px to py)
        }
    }

    override fun arc(x: Float, y: Float, radius: Float, startAngle: Float, endAngle: Float, counterClockwise: Boolean) {
        val circumference = abs(endAngle - startAngle) * radius * max(abs(sx), abs(sy))
        val steps = (circumference / 2.5f).toInt().coerceIn(16, 96)
        val span = endAngle - startAngle
        for (i in 0..steps) {
            val a = startAngle + span * (i.toFloat() / steps)
            val px = x + cos(a) * radius
            val py = y + sin(a) * radius
            val mapped = mapPoint(px, py)
            if (i == 0 && pathPoints.isEmpty()) {
                pathStartX = mapped.first
                pathStartY = mapped.second
            }
            pathPoints.add(mapped)
        }
    }

    override fun fill() {
        if (pathPoints.size < 3) return
        val n = pathPoints.size
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (i in 0 until n) {
            val pt = pathPoints[i]
            if (pt.first < minX) minX = pt.first
            if (pt.first > maxX) maxX = pt.first
            if (pt.second < minY) minY = pt.second
            if (pt.second > maxY) maxY = pt.second
        }

        val yStart = floor(minY).toInt().coerceIn(0, height - 1)
        val yEnd = ceil(maxY).toInt().coerceIn(0, height - 1)
        val xStartBound = floor(minX - 1f).toInt().coerceIn(0, width)
        val xEndBound = ceil(maxX + 1f).toInt().coerceIn(0, width)
        val nodes = FloatArray(n)

        for (py in yStart..yEnd) {
            val scanY = py + 0.5f
            var nodeCount = 0
            var j = n - 1
            for (i in 0 until n) {
                val yi = pathPoints[i].second
                val yj = pathPoints[j].second
                if ((yi < scanY && yj >= scanY) || (yj < scanY && yi >= scanY)) {
                    val xi = pathPoints[i].first
                    val xj = pathPoints[j].first
                    nodes[nodeCount++] = xi + (scanY - yi) / (yj - yi) * (xj - xi)
                }
                j = i
            }
            for (a in 1 until nodeCount) {
                val key = nodes[a]
                var b = a - 1
                while (b >= 0 && nodes[b] > key) {
                    nodes[b + 1] = nodes[b]
                    b--
                }
                nodes[b + 1] = key
            }

            var k = 0
            while (k + 1 < nodeCount) {
                val leftX = nodes[k]
                val rightX = nodes[k + 1]
                val x0 = floor(leftX).toInt().coerceIn(0, width - 1)
                val x1 = ceil(rightX).toInt().coerceIn(0, width - 1)
                for (px in x0..x1) {
                    val center = px + 0.5f
                    val cov = if (antialias) {
                        val leftCov = (center + 0.5f - leftX).coerceIn(0f, 1f)
                        val rightCov = (rightX - (center - 0.5f)).coerceIn(0f, 1f)
                        min(leftCov, rightCov)
                    } else {
                        if (center in leftX..rightX) 1f else 0f
                    }
                    if (cov > 0.005f) {
                        blendCoveragePixel(px, py, fillColor, cov)
                    }
                }
                k += 2
            }
        }

        if (antialias) {
            for (i in 0 until n) {
                val p0 = pathPoints[i]
                val p1 = pathPoints[(i + 1) % n]
                drawAntialiasedSegment(p0.first, p0.second, p1.first, p1.second, fillColor, 1.15f)
            }
        }
        imageBuffer.markDirtyRect(xStartBound, yStart, xEndBound, yEnd + 1)
    }

    override fun stroke() {
        if (pathPoints.size < 2) return
        val scaledWidth = max(1f, lineWidth * max(abs(sx), abs(sy)))
        for (i in 0 until pathPoints.size - 1) {
            val (x0, y0) = pathPoints[i]
            val (x1, y1) = pathPoints[i + 1]
            drawAntialiasedSegment(x0, y0, x1, y1, strokeColor, scaledWidth)
        }
    }

    override fun clip() {
        // Clip state recorded on current path
    }

    override fun setFont(fontFamily: String, fontSizePx: Float, fontWeight: Int, italic: Boolean) {
        this.fontSizePx = max(6f, fontSizePx)
    }

    override fun fillText(text: String, x: Float, y: Float) {
        val startX = mapX(x)
        val baseY = mapY(y)
        val charW = max(5f, fontSizePx * 0.55f * abs(sx))
        val charH = max(8f, fontSizePx * 0.75f * abs(sy))
        var cursorX = startX
        for (ch in text) {
            if (!ch.isWhitespace()) {
                val ix0 = cursorX.roundToInt().coerceIn(0, width)
                val iy0 = (baseY - charH * 0.8f).roundToInt().coerceIn(0, height)
                val ix1 = (cursorX + charW * 0.72f).roundToInt().coerceIn(0, width)
                val iy1 = baseY.roundToInt().coerceIn(0, height)
                for (py in iy0 until iy1) {
                    for (px in ix0 until ix1) {
                        blendCoveragePixel(px, py, fillColor, 0.9f)
                    }
                }
                imageBuffer.markDirtyRect(ix0, iy0, ix1, iy1)
            }
            cursorX += charW
        }
    }

    override fun strokeText(text: String, x: Float, y: Float) {
        val prev = fillColor
        fillColor = strokeColor
        fillText(text, x, y)
        fillColor = prev
    }

    override fun measureTextWidth(text: String): Float =
        text.length * max(5f, fontSizePx * 0.55f)

    override fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) {
        imageBuffer.writePixelsRgba(rgbaBytes, width, height, dx, dy)
    }

    override fun drawImageBuffer(buffer: com.nexustools.genericui.image.ImageBuffer, dx: Float, dy: Float) {
        val rgba = buffer.asRgba8888()
        imageBuffer.writePixelsRgba(
            rgbaBytes = rgba.bytes,
            width = rgba.width,
            height = rgba.height,
            dx = mapX(dx).roundToInt(),
            dy = mapY(dy).roundToInt()
        )
    }

    override fun getImageDataRgba(x: Int, y: Int, width: Int, height: Int): ByteArray =
        imageBuffer.readPixelsRgba(x, y, width, height)

    override fun stampBrushStroke(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float,
        hardness: Float,
        color: RgbaColor,
        opacity: Float,
        erase: Boolean
    ) {
        val safeR = max(0.5f, radius)
        val pad = ceil(safeR + 1.5f).toInt()
        val ix0 = (min(x0, x1) - pad).toInt().coerceIn(0, width)
        val iy0 = (min(y0, y1) - pad).toInt().coerceIn(0, height)
        val ix1 = (max(x0, x1) + pad + 1).toInt().coerceIn(0, width)
        val iy1 = (max(y0, y1) + pad + 1).toInt().coerceIn(0, height)
        if (ix1 <= ix0 || iy1 <= iy0) return

        val baX = x1 - x0
        val baY = y1 - y0
        val baLenSq = baX * baX + baY * baY
        val innerR = safeR * hardness.coerceIn(0f, 0.98f)
        val outerR = safeR + if (antialias) 0.75f else 0.05f
        val featherSpan = max(0.001f, outerR - innerR)
        val baseOpacity = opacity.coerceIn(0f, 1f) * (color.a / 255f)
        val bytes = imageBuffer.activeDrawBytes
        val w = this.width

        for (py in iy0 until iy1) {
            val cy = py + 0.5f
            val rowOffset = py * w
            for (px in ix0 until ix1) {
                val cx = px + 0.5f
                val paX = cx - x0
                val paY = cy - y0
                val h = if (baLenSq > 1e-5f) {
                    ((paX * baX + paY * baY) / baLenSq).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val dx = paX - baX * h
                val dy = paY - baY * h
                val distSq = dx * dx + dy * dy
                if (distSq > outerR * outerR) continue

                val dist = sqrt(distSq)
                val cov = if (dist <= innerR) {
                    1f
                } else {
                    val t = ((dist - innerR) / featherSpan).coerceIn(0f, 1f)
                    1f - t * t * (3f - 2f * t)
                }
                val alpha = (baseOpacity * cov).coerceIn(0f, 1f)
                if (alpha <= 0.002f) continue

                val idx = (rowOffset + px) shl 2
                if (erase) {
                    val curA = (bytes[idx + 3].toInt() and 0xFF) / 255f
                    val nextA = (curA * (1f - alpha)).coerceIn(0f, 1f)
                    bytes[idx + 3] = (nextA * 255f).roundToInt().coerceIn(0, 255).toByte()
                    if (nextA <= 0.004f) {
                        bytes[idx] = 0
                        bytes[idx + 1] = 0
                        bytes[idx + 2] = 0
                        bytes[idx + 3] = 0
                    }
                } else {
                    val invA = 1f - alpha
                    val dstR = bytes[idx].toInt() and 0xFF
                    val dstG = bytes[idx + 1].toInt() and 0xFF
                    val dstB = bytes[idx + 2].toInt() and 0xFF
                    val dstA = (bytes[idx + 3].toInt() and 0xFF) / 255f
                    val outA = (alpha + dstA * invA).coerceIn(0f, 1f)
                    bytes[idx] = (color.r * alpha + dstR * invA).roundToInt().coerceIn(0, 255).toByte()
                    bytes[idx + 1] = (color.g * alpha + dstG * invA).roundToInt().coerceIn(0, 255).toByte()
                    bytes[idx + 2] = (color.b * alpha + dstB * invA).roundToInt().coerceIn(0, 255).toByte()
                    bytes[idx + 3] = (outA * 255f).roundToInt().coerceIn(0, 255).toByte()
                }
            }
        }
        imageBuffer.markDirtyRect(ix0, iy0, ix1, iy1)
    }

    override fun fillLinearGradient(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        startColor: RgbaColor,
        endColor: RgbaColor,
        rectX: Float,
        rectY: Float,
        rectW: Float,
        rectH: Float
    ) {
        val ix0 = rectX.roundToInt().coerceIn(0, width)
        val iy0 = rectY.roundToInt().coerceIn(0, height)
        val ix1 = (rectX + rectW).roundToInt().coerceIn(0, width)
        val iy1 = (rectY + rectH).roundToInt().coerceIn(0, height)
        if (ix1 <= ix0 || iy1 <= iy0) return

        val abX = x1 - x0
        val abY = y1 - y0
        val abLenSq = max(1e-4f, abX * abX + abY * abY)
        val bytes = imageBuffer.activeDrawBytes
        val w = this.width

        for (py in iy0 until iy1) {
            val rowOffset = py * w
            val dy = (py + 0.5f) - y0
            for (px in ix0 until ix1) {
                val dx = (px + 0.5f) - x0
                val t = ((dx * abX + dy * abY) / abLenSq).coerceIn(0f, 1f)
                val u = 1f - t
                val idx = (rowOffset + px) shl 2
                bytes[idx] = (startColor.r * u + endColor.r * t).roundToInt().coerceIn(0, 255).toByte()
                bytes[idx + 1] = (startColor.g * u + endColor.g * t).roundToInt().coerceIn(0, 255).toByte()
                bytes[idx + 2] = (startColor.b * u + endColor.b * t).roundToInt().coerceIn(0, 255).toByte()
                bytes[idx + 3] = (startColor.a * u + endColor.a * t).roundToInt().coerceIn(0, 255).toByte()
            }
        }
        imageBuffer.markDirtyRect(ix0, iy0, ix1, iy1)
    }

    override fun floodFill(
        seedX: Int,
        seedY: Int,
        fillColor: RgbaColor,
        tolerance: Int
    ) {
        val w = this.width
        val h = this.height
        if (seedX !in 0 until w || seedY !in 0 until h) return
        val bytes = imageBuffer.activeDrawBytes
        val seedIdx = (seedY * w + seedX) shl 2
        val targetR = bytes[seedIdx].toInt() and 0xFF
        val targetG = bytes[seedIdx + 1].toInt() and 0xFF
        val targetB = bytes[seedIdx + 2].toInt() and 0xFF
        val targetA = bytes[seedIdx + 3].toInt() and 0xFF

        val fillR = fillColor.r.toByte()
        val fillG = fillColor.g.toByte()
        val fillB = fillColor.b.toByte()
        val fillA = fillColor.a.coerceIn(0, 255).toByte()
        if (targetR == fillColor.r && targetG == fillColor.g && targetB == fillColor.b && targetA == fillColor.a) {
            return
        }

        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        val seedFlat = seedY * w + seedX
        visited[seedFlat] = true
        queue[tail++] = seedFlat

        var minDirtyX = seedX
        var maxDirtyX = seedX + 1
        var minDirtyY = seedY
        var maxDirtyY = seedY + 1

        fun matches(flatIdx: Int): Boolean {
            val bIdx = flatIdx shl 2
            val r = bytes[bIdx].toInt() and 0xFF
            val g = bytes[bIdx + 1].toInt() and 0xFF
            val b = bytes[bIdx + 2].toInt() and 0xFF
            val a = bytes[bIdx + 3].toInt() and 0xFF
            return abs(r - targetR) <= tolerance &&
                abs(g - targetG) <= tolerance &&
                abs(b - targetB) <= tolerance &&
                abs(a - targetA) <= tolerance
        }

        while (head < tail) {
            val curr = queue[head++]
            val cx = curr % w
            val cy = curr / w
            val bIdx = curr shl 2
            bytes[bIdx] = fillR
            bytes[bIdx + 1] = fillG
            bytes[bIdx + 2] = fillB
            bytes[bIdx + 3] = fillA

            if (cx < minDirtyX) minDirtyX = cx
            if (cx + 1 > maxDirtyX) maxDirtyX = cx + 1
            if (cy < minDirtyY) minDirtyY = cy
            if (cy + 1 > maxDirtyY) maxDirtyY = cy + 1

            if (cx > 0) {
                val left = curr - 1
                if (!visited[left] && matches(left)) {
                    visited[left] = true
                    queue[tail++] = left
                }
            }
            if (cx + 1 < w) {
                val right = curr + 1
                if (!visited[right] && matches(right)) {
                    visited[right] = true
                    queue[tail++] = right
                }
            }
            if (cy > 0) {
                val up = curr - w
                if (!visited[up] && matches(up)) {
                    visited[up] = true
                    queue[tail++] = up
                }
            }
            if (cy + 1 < h) {
                val down = curr + w
                if (!visited[down] && matches(down)) {
                    visited[down] = true
                    queue[tail++] = down
                }
            }
        }
        imageBuffer.markDirtyRect(minDirtyX, minDirtyY, maxDirtyX, maxDirtyY)
    }

    /**
     * Draws an analytical capsule-SDF antialiased line segment between `(x0, y0)` and `(x1, y1)`.
     */
    fun drawAntialiasedSegment(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        color: RgbaColor,
        strokeWidth: Float
    ) {
        val halfW = max(0.5f, strokeWidth * 0.5f)
        val feather = if (antialias) 0.75f else 0.05f
        val outerR = halfW + feather
        val outerRSq = outerR * outerR

        val pad = ceil(outerR + 1f).toInt()
        val ix0 = (min(x0, x1) - pad).toInt().coerceIn(0, width)
        val iy0 = (min(y0, y1) - pad).toInt().coerceIn(0, height)
        val ix1 = (max(x0, x1) + pad + 1).toInt().coerceIn(0, width)
        val iy1 = (max(y0, y1) + pad + 1).toInt().coerceIn(0, height)
        if (ix1 <= ix0 || iy1 <= iy0) return

        val baX = x1 - x0
        val baY = y1 - y0
        val baLenSq = baX * baX + baY * baY
        val innerR = max(0f, halfW - feather)
        val span = max(0.001f, outerR - innerR)

        for (py in iy0 until iy1) {
            val cy = py + 0.5f
            for (px in ix0 until ix1) {
                val cx = px + 0.5f
                val paX = cx - x0
                val paY = cy - y0
                val h = if (baLenSq > 1e-5f) {
                    ((paX * baX + paY * baY) / baLenSq).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val dx = paX - baX * h
                val dy = paY - baY * h
                val distSq = dx * dx + dy * dy
                if (distSq > outerRSq) continue

                val dist = sqrt(distSq)
                val cov = if (antialias) {
                    if (dist <= innerR) {
                        1f
                    } else {
                        val t = ((dist - innerR) / span).coerceIn(0f, 1f)
                        1f - t * t * (3f - 2f * t)
                    }
                } else {
                    if (dist <= halfW) 1f else 0f
                }
                if (cov > 0.003f) {
                    blendCoveragePixel(px, py, color, cov)
                }
            }
        }
        imageBuffer.markDirtyRect(ix0, iy0, ix1, iy1)
    }

    private fun blendCoveragePixel(px: Int, py: Int, color: RgbaColor, coverage: Float) {
        if (px !in 0 until width || py !in 0 until height) return
        val bytes = imageBuffer.activeDrawBytes
        val idx = (py * width + px) shl 2
        if (idx + 3 >= bytes.size) return
        val srcA = ((color.a / 255f) * globalAlpha * coverage).coerceIn(0f, 1f)
        if (srcA >= 0.995f || compositeOp == "copy") {
            bytes[idx] = color.r.toByte()
            bytes[idx + 1] = color.g.toByte()
            bytes[idx + 2] = color.b.toByte()
            bytes[idx + 3] = (srcA * 255f).roundToInt().coerceIn(0, 255).toByte()
        } else if (srcA > 0.001f) {
            val invA = 1f - srcA
            val dstR = bytes[idx].toInt() and 0xFF
            val dstG = bytes[idx + 1].toInt() and 0xFF
            val dstB = bytes[idx + 2].toInt() and 0xFF
            val dstA = (bytes[idx + 3].toInt() and 0xFF) / 255f
            val outA = (srcA + dstA * invA).coerceIn(0f, 1f)
            bytes[idx] = (color.r * srcA + dstR * invA).roundToInt().coerceIn(0, 255).toByte()
            bytes[idx + 1] = (color.g * srcA + dstG * invA).roundToInt().coerceIn(0, 255).toByte()
            bytes[idx + 2] = (color.b * srcA + dstB * invA).roundToInt().coerceIn(0, 255).toByte()
            bytes[idx + 3] = (outA * 255f).roundToInt().coerceIn(0, 255).toByte()
        }
    }
}
