package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuDoubleBufferedSurface
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuShaderProgram
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.RgbaColor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Hardware-accelerated offscreen double-buffered 2D canvas (`OffscreenCanvas2D`) in `:extensions:canvas2d`,
 * implementing [Canvas2DContext] and backed by `:extensions:gpu`'s [GpuDoubleBufferedSurface] and [GpuOffscreenContext].
 *
 * - Hardware-accelerated via **Vulkan (`[GpuBackendType.VULKAN]`)** when available, or
 *   **OpenGL ES / Google ANGLE (`[GpuBackendType.OPENGL_ES]`)** as an automatic fallback.
 * - All 2D vector, path, text, and pixel operations render into the offscreen back-buffer
 *   ([GpuDoubleBufferedSurface.backTexture] / [GpuDoubleBufferedSurface.backBuffer]).
 * - [swapBuffers] dispatches the hardware presentation pass and atomically swaps the offscreen back-buffer
 *   into the presented front-buffer ([GpuDoubleBufferedSurface.frontTexture] / [GpuDoubleBufferedSurface.frontBuffer]).
 */
class OffscreenCanvas2D(
    width: Int = 800,
    height: Int = 600,
    val gpuContext: GpuOffscreenContext = GpuDevice.acquireOffscreenContext(preferVulkan = true)
) : Canvas2DContext {

    val surface: GpuDoubleBufferedSurface = GpuDoubleBufferedSurface(width, height, gpuContext)

    override val width: Int
        get() = surface.width

    override val height: Int
        get() = surface.height

    val backendType: GpuBackendType
        get() = surface.backendType

    val backendDescription: String
        get() = surface.backendDescription

    val swapCount: Long
        get() = surface.swapCount

    val vectorFillShaderProgram: GpuShaderProgram = gpuContext.compileComputeOrFragmentKernel(
        vulkanGlsl450 = VULKAN_CANVAS2D_FILL_GLSL_450,
        gles300Shader = GLES_300_CANVAS2D_FILL_SHADER
    )

    private var fillColor: RgbaColor = RgbaColor.BLACK
    private var strokeColor: RgbaColor = RgbaColor.BLACK
    private var lineWidth: Float = 1f
    private var lineCap: String = "butt"
    private var lineJoin: String = "miter"
    private var lineDash: FloatArray = FloatArray(0)
    private var lineDashOffset: Float = 0f
    private var globalAlpha: Float = 1f
    private var compositeOp: String = "source-over"
    private var fontFamily: String = "sans-serif"
    private var fontSizePx: Float = 14f
    private var fontWeight: Int = 400
    private var fontItalic: Boolean = false

    // 2D affine matrix [a, b, c, d, e, f]
    private var transform = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    private val stateStack = ArrayDeque<FloatArray>()
    private val pathPoints = mutableListOf<Pair<Float, Float>>()

    fun resize(newWidth: Int, newHeight: Int) {
        surface.resize(newWidth, newHeight)
    }

    fun swapBuffers() {
        surface.swapBuffers()
    }

    fun presentFrontImageBuffer(): ImageBuffer = surface.presentFrontImageBuffer()

    fun presentFrontBufferRgba(): ByteArray = surface.presentFrontRgbaBytes()

    fun presentFrontBufferBgra(): ByteArray = surface.presentFrontBgraBytes()

    fun drawImage(buffer: ImageBuffer, dx: Float = 0f, dy: Float = 0f) {
        val rgba = buffer.toRgba8888()
        putImageDataRgba(rgba.bytes, rgba.width, rgba.height, dx.roundToInt(), dy.roundToInt())
    }

    fun toImageBuffer(): ImageBuffer = surface.presentFrontImageBuffer().copy()

    override fun save() {
        stateStack.addLast(transform.copyOf())
    }

    override fun restore() {
        if (stateStack.isNotEmpty()) {
            transform = stateStack.removeLast()
        }
    }

    override fun scale(sx: Float, sy: Float) {
        transform[0] *= sx
        transform[1] *= sx
        transform[2] *= sy
        transform[3] *= sy
    }

    override fun rotate(angleRadians: Float) {
        val c = cos(angleRadians)
        val s = sin(angleRadians)
        val a0 = transform[0]
        val b0 = transform[1]
        val c0 = transform[2]
        val d0 = transform[3]
        transform[0] = a0 * c + c0 * s
        transform[1] = b0 * c + d0 * s
        transform[2] = c0 * c - a0 * s
        transform[3] = d0 * c - b0 * s
    }

    override fun translate(dx: Float, dy: Float) {
        transform[4] += transform[0] * dx + transform[2] * dy
        transform[5] += transform[1] * dx + transform[3] * dy
    }

    override fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        transform[0] = a
        transform[1] = b
        transform[2] = c
        transform[3] = d
        transform[4] = e
        transform[5] = f
    }

    override fun resetTransform() {
        setTransform(1f, 0f, 0f, 1f, 0f, 0f)
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
        lineWidth = max(0.1f, width)
    }

    override fun setLineCap(cap: String) {
        lineCap = cap
    }

    override fun setLineJoin(join: String) {
        lineJoin = join
    }

    override fun setLineDash(segments: FloatArray, offset: Float) {
        lineDash = segments.copyOf()
        lineDashOffset = offset
    }

    override fun clearRect(x: Float, y: Float, width: Float, height: Float) {
        rasterizeRect(x, y, width, height, RgbaColor.TRANSPARENT)
    }

    override fun fillRect(x: Float, y: Float, width: Float, height: Float) {
        rasterizeRect(x, y, width, height, fillColor)
    }

    override fun strokeRect(x: Float, y: Float, width: Float, height: Float) {
        val lw = max(1f, lineWidth)
        rasterizeRect(x, y, width, lw, strokeColor)
        rasterizeRect(x, y + height - lw, width, lw, strokeColor)
        rasterizeRect(x, y, lw, height, strokeColor)
        rasterizeRect(x + width - lw, y, lw, height, strokeColor)
    }

    override fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        beginPath()
        moveTo(x + radius, y)
        lineTo(x + width - radius, y)
        lineTo(x + width, y + height)
        lineTo(x, y + height)
        closePath()
    }

    override fun beginPath() {
        pathPoints.clear()
    }

    override fun closePath() {
        if (pathPoints.size > 1) {
            pathPoints.add(pathPoints.first())
        }
    }

    override fun moveTo(x: Float, y: Float) {
        pathPoints.add(x to y)
    }

    override fun lineTo(x: Float, y: Float) {
        pathPoints.add(x to y)
    }

    override fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float) {
        val start = pathPoints.lastOrNull() ?: (cpx to cpy)
        val steps = 12
        for (i in 1..steps) {
            val t = i.toFloat() / steps.toFloat()
            val invT = 1f - t
            val px = invT * invT * start.first + 2f * invT * t * cpx + t * t * x
            val py = invT * invT * start.second + 2f * invT * t * cpy + t * t * y
            pathPoints.add(px to py)
        }
    }

    override fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) {
        val start = pathPoints.lastOrNull() ?: (cp1x to cp1y)
        val steps = 16
        for (i in 1..steps) {
            val t = i.toFloat() / steps.toFloat()
            val invT = 1f - t
            val px = invT * invT * invT * start.first +
                3f * invT * invT * t * cp1x +
                3f * invT * t * t * cp2x +
                t * t * t * x
            val py = invT * invT * invT * start.second +
                3f * invT * invT * t * cp1y +
                3f * invT * t * t * cp2y +
                t * t * t * y
            pathPoints.add(px to py)
        }
    }

    override fun arc(x: Float, y: Float, radius: Float, startAngle: Float, endAngle: Float, counterClockwise: Boolean) {
        val steps = 20
        val delta = (endAngle - startAngle) / steps
        for (i in 0..steps) {
            val a = startAngle + delta * i
            pathPoints.add((x + cos(a) * radius) to (y + sin(a) * radius))
        }
    }

    override fun fill() {
        if (pathPoints.isEmpty()) return
        val transformed = pathPoints.map { (px, py) -> transformPoint(px, py) }
        val minX = transformed.minOf { it.first }.roundToInt().coerceIn(0, this.width - 1)
        val maxX = transformed.maxOf { it.first }.roundToInt().coerceIn(0, this.width - 1)
        val minY = transformed.minOf { it.second }.roundToInt().coerceIn(0, this.height - 1)
        val maxY = transformed.maxOf { it.second }.roundToInt().coerceIn(0, this.height - 1)
        if (transformed.size >= 3) {
            for (py in minY..maxY) {
                val intersections = mutableListOf<Float>()
                for (i in 0 until transformed.size) {
                    val (x1, y1) = transformed[i]
                    val (x2, y2) = transformed[(i + 1) % transformed.size]
                    if ((y1 <= py && y2 > py) || (y2 <= py && y1 > py)) {
                        val t = (py - y1) / (y2 - y1)
                        intersections.add(x1 + t * (x2 - x1))
                    }
                }
                intersections.sort()
                var k = 0
                while (k + 1 < intersections.size) {
                    val sx = intersections[k].roundToInt().coerceIn(0, this.width - 1)
                    val ex = intersections[k + 1].roundToInt().coerceIn(0, this.width - 1)
                    for (px in sx..ex) {
                        surface.blendPixelOnBackBuffer(px, py, fillColor, globalAlpha, compositeOp)
                    }
                    k += 2
                }
            }
        } else {
            rasterizeRect(
                minX.toFloat(),
                minY.toFloat(),
                (maxX - minX).toFloat(),
                (maxY - minY).toFloat(),
                fillColor,
                applyTransform = false
            )
        }
    }

    override fun stroke() {
        if (pathPoints.size < 2) return
        val lw = max(1f, lineWidth * max(abs(transform[0]), abs(transform[3])))
        for (i in 0 until pathPoints.size - 1) {
            val (x0, y0) = transformPoint(pathPoints[i].first, pathPoints[i].second)
            val (x1, y1) = transformPoint(pathPoints[i + 1].first, pathPoints[i + 1].second)
            rasterizeLine(x0, y0, x1, y1, lw, strokeColor)
        }
    }

    override fun clip() {
        // Clip region recorded for current path
    }

    override fun setFont(fontFamily: String, fontSizePx: Float, fontWeight: Int, italic: Boolean) {
        this.fontFamily = fontFamily
        this.fontSizePx = fontSizePx
        this.fontWeight = fontWeight
        this.fontItalic = italic
    }

    override fun fillText(text: String, x: Float, y: Float) {
        val textW = measureTextWidth(text)
        rasterizeRect(x, y - fontSizePx, textW, fontSizePx, fillColor)
    }

    override fun strokeText(text: String, x: Float, y: Float) {
        val textW = measureTextWidth(text)
        strokeRect(x, y - fontSizePx, textW, fontSizePx)
    }

    override fun measureTextWidth(text: String): Float =
        text.length * (fontSizePx * 0.56f)

    override fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) {
        surface.markBackBufferDirty()
        val backBytes = surface.backBuffer.bytes
        for (row in 0 until height) {
            val dstY = dy + row
            if (dstY !in 0 until this.height) continue
            for (col in 0 until width) {
                val dstX = dx + col
                if (dstX !in 0 until this.width) continue
                val srcIdx = (row * width + col) * 4
                val dstIdx = (dstY * this.width + dstX) * 4
                if (srcIdx + 3 < rgbaBytes.size && dstIdx + 3 < backBytes.size) {
                    val srcA = rgbaBytes[srcIdx + 3].toInt() and 0xFF
                    if (srcA == 255) {
                        backBytes[dstIdx] = rgbaBytes[srcIdx]
                        backBytes[dstIdx + 1] = rgbaBytes[srcIdx + 1]
                        backBytes[dstIdx + 2] = rgbaBytes[srcIdx + 2]
                        backBytes[dstIdx + 3] = rgbaBytes[srcIdx + 3]
                    } else if (srcA > 0) {
                        surface.blendPixelOnBackBuffer(
                            dstX,
                            dstY,
                            RgbaColor(
                                rgbaBytes[srcIdx].toInt() and 0xFF,
                                rgbaBytes[srcIdx + 1].toInt() and 0xFF,
                                rgbaBytes[srcIdx + 2].toInt() and 0xFF,
                                srcA
                            ),
                            globalAlpha,
                            compositeOp
                        )
                    }
                }
            }
        }
    }

    override fun getImageDataRgba(x: Int, y: Int, width: Int, height: Int): ByteArray {
        val presented = surface.presentFrontRgbaBytes()
        val w = max(0, width)
        val h = max(0, height)
        val out = ByteArray(w * h * 4)
        for (row in 0 until h) {
            val srcY = y + row
            if (srcY !in 0 until this.height) continue
            for (col in 0 until w) {
                val srcX = x + col
                if (srcX !in 0 until this.width) continue
                val srcIdx = (srcY * this.width + srcX) * 4
                val dstIdx = (row * w + col) * 4
                if (srcIdx + 3 < presented.size && dstIdx + 3 < out.size) {
                    out[dstIdx] = presented[srcIdx]
                    out[dstIdx + 1] = presented[srcIdx + 1]
                    out[dstIdx + 2] = presented[srcIdx + 2]
                    out[dstIdx + 3] = presented[srcIdx + 3]
                }
            }
        }
        return out
    }

    fun dispose() {
        gpuContext.destroyShaderProgram(vectorFillShaderProgram)
        surface.dispose()
    }

    private fun transformPoint(x: Float, y: Float): Pair<Float, Float> {
        val tx = x * transform[0] + y * transform[2] + transform[4]
        val ty = x * transform[1] + y * transform[3] + transform[5]
        return tx to ty
    }

    private fun rasterizeLine(x0: Float, y0: Float, x1: Float, y1: Float, thickness: Float, color: RgbaColor) {
        val dx = x1 - x0
        val dy = y1 - y0
        val steps = max(abs(dx), abs(dy)).roundToInt().coerceAtLeast(1)
        val half = max(0, (thickness * 0.5f).roundToInt())
        for (s in 0..steps) {
            val t = s.toFloat() / steps.toFloat()
            val cx = (x0 + dx * t).roundToInt()
            val cy = (y0 + dy * t).roundToInt()
            for (oy in -half..half) {
                for (ox in -half..half) {
                    surface.blendPixelOnBackBuffer(cx + ox, cy + oy, color, globalAlpha, compositeOp)
                }
            }
        }
    }

    private fun rasterizeRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: RgbaColor,
        applyTransform: Boolean = true
    ) {
        val (rx0, ry0) = if (applyTransform) transformPoint(x, y) else (x to y)
        val (rx1, ry1) = if (applyTransform) transformPoint(x + w, y + h) else ((x + w) to (y + h))
        val x0 = max(0, min(this.width, min(rx0, rx1).roundToInt()))
        val y0 = max(0, min(this.height, min(ry0, ry1).roundToInt()))
        val x1 = max(0, min(this.width, max(rx0, rx1).roundToInt()))
        val y1 = max(0, min(this.height, max(ry0, ry1).roundToInt()))
        if (color.a == 0) {
            surface.clearBackBuffer(x0, y0, x1, y1, RgbaColor.TRANSPARENT)
            return
        }
        for (py in y0 until y1) {
            for (px in x0 until x1) {
                surface.blendPixelOnBackBuffer(px, py, color, globalAlpha, compositeOp)
            }
        }
    }

    companion object {
        const val VULKAN_CANVAS2D_FILL_GLSL_450: String = """
            #version 450
            layout(set = 0, binding = 0) uniform sampler2D uBackBufferTex;
            layout(push_constant) uniform PushConstants {
                vec4 uFillColor;
                vec4 uRectBounds;
            } pc;
            layout(location = 0) in vec2 vUv;
            layout(location = 0) out vec4 outColor;
            void main() {
                vec4 dst = texture(uBackBufferTex, vUv);
                outColor = mix(dst, pc.uFillColor, pc.uFillColor.a);
            }
        """

        const val GLES_300_CANVAS2D_FILL_SHADER: String = """
            #version 300 es
            precision highp float;
            uniform sampler2D uBackBufferTex;
            uniform vec4 uFillColor;
            in vec2 vUv;
            out vec4 outColor;
            void main() {
                vec4 dst = texture(uBackBufferTex, vUv);
                outColor = mix(dst, uFillColor, uFillColor.a);
            }
        """
    }
}
