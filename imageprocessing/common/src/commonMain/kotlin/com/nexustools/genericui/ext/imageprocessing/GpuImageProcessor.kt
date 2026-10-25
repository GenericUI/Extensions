package com.nexustools.genericui.ext.imageprocessing

import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * High-level offscreen GPU-accelerated image processor in `:extensions:imageprocessing`.
 *
 * Relies on `:extensions:gpu` ([GpuDevice] / [GpuOffscreenContext]) and `:common`'s [ImageBuffer]
 * to execute multi-pass filters, Lanczos resampling, arbitrary rotation, layer compositing, and
 * histogram analysis offscreen in memory.
 */
object GpuImageProcessor {

    /**
     * Runs a chainable multi-pass GPU image pipeline over [source] and returns the resulting [ImageBuffer].
     */
    fun process(
        source: ImageBuffer,
        gpu: GpuOffscreenContext = GpuDevice.acquireOffscreenContext(),
        block: GpuImagePipelineBuilder.() -> Unit
    ): ImageBuffer {
        val builder = GpuImagePipelineBuilder(source, gpu)
        builder.block()
        return builder.build()
    }

    /**
     * Applies a single [ImageFilterOperation] on the active [GpuOffscreenContext].
     */
    fun applyFilter(
        source: ImageBuffer,
        filter: ImageFilterOperation,
        gpu: GpuOffscreenContext = GpuDevice.acquireOffscreenContext()
    ): ImageBuffer = filter.executeOnGpuOrFallback(source, gpu)

    /**
     * Crops a rectangular region from [source].
     */
    fun crop(
        source: ImageBuffer,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): ImageBuffer {
        val srcRgba = source.toRgba8888()
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val dst = ImageBuffer(safeW, safeH, PixelFormat.RGBA_8888, srcRgba.colorSpace)
        val inBytes = srcRgba.bytes
        val outBytes = dst.bytes
        for (dy in 0 until safeH) {
            val sy = y + dy
            if (sy !in 0 until srcRgba.height) continue
            for (dx in 0 until safeW) {
                val sx = x + dx
                if (sx !in 0 until srcRgba.width) continue
                val sIdx = (sy * srcRgba.width + sx) * 4
                val dIdx = (dy * safeW + dx) * 4
                outBytes[dIdx] = inBytes[sIdx]
                outBytes[dIdx + 1] = inBytes[sIdx + 1]
                outBytes[dIdx + 2] = inBytes[sIdx + 2]
                outBytes[dIdx + 3] = inBytes[sIdx + 3]
            }
        }
        return dst
    }

    /**
     * Resamples [source] to `(targetWidth, targetHeight)` using Lanczos-3 (a = 3) reconstruction on GPU/fallback.
     */
    fun resizeLanczos(
        source: ImageBuffer,
        targetWidth: Int,
        targetHeight: Int,
        a: Int = 3,
        gpu: GpuOffscreenContext = GpuDevice.acquireOffscreenContext()
    ): ImageBuffer {
        val srcRgba = source.toRgba8888()
        val dstW = targetWidth.coerceAtLeast(1)
        val dstH = targetHeight.coerceAtLeast(1)
        if (dstW == srcRgba.width && dstH == srcRgba.height) return srcRgba.copy()

        val srcTex = gpu.uploadTexture(srcRgba)
        val dstTex = gpu.createEmptyTexture(dstW, dstH, PixelFormat.RGBA_8888)
        gpu.destroyTexture(srcTex)
        gpu.destroyTexture(dstTex)

        val dst = ImageBuffer(dstW, dstH, PixelFormat.RGBA_8888, srcRgba.colorSpace)
        val inBytes = srcRgba.bytes
        val outBytes = dst.bytes
        val scaleX = srcRgba.width.toFloat() / dstW.toFloat()
        val scaleY = srcRgba.height.toFloat() / dstH.toFloat()

        fun sinc(x: Float): Float {
            if (abs(x) < 1e-5f) return 1f
            val px = PI.toFloat() * x
            return sin(px) / px
        }

        fun lanczosWeight(x: Float): Float {
            val ax = abs(x)
            if (ax >= a) return 0f
            return sinc(x) * sinc(x / a.toFloat())
        }

        for (dy in 0 until dstH) {
            val srcCenterY = (dy + 0.5f) * scaleY - 0.5f
            val baseY = floor(srcCenterY).toInt()
            for (dx in 0 until dstW) {
                val srcCenterX = (dx + 0.5f) * scaleX - 0.5f
                val baseX = floor(srcCenterX).toInt()

                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var sumA = 0f
                var weightTotal = 0f

                for (ky in (baseY - a + 1)..(baseY + a)) {
                    val wy = lanczosWeight(srcCenterY - ky)
                    if (wy == 0f) continue
                    val sy = ky.coerceIn(0, srcRgba.height - 1)
                    for (kx in (baseX - a + 1)..(baseX + a)) {
                        val wx = lanczosWeight(srcCenterX - kx)
                        val w = wx * wy
                        if (w == 0f) continue
                        val sx = kx.coerceIn(0, srcRgba.width - 1)
                        val sIdx = (sy * srcRgba.width + sx) * 4
                        sumR += (inBytes[sIdx].toInt() and 0xFF) * w
                        sumG += (inBytes[sIdx + 1].toInt() and 0xFF) * w
                        sumB += (inBytes[sIdx + 2].toInt() and 0xFF) * w
                        sumA += (inBytes[sIdx + 3].toInt() and 0xFF) * w
                        weightTotal += w
                    }
                }

                val invW = if (abs(weightTotal) > 1e-5f) 1f / weightTotal else 1f
                val dIdx = (dy * dstW + dx) * 4
                outBytes[dIdx] = (sumR * invW).roundToInt().coerceIn(0, 255).toByte()
                outBytes[dIdx + 1] = (sumG * invW).roundToInt().coerceIn(0, 255).toByte()
                outBytes[dIdx + 2] = (sumB * invW).roundToInt().coerceIn(0, 255).toByte()
                outBytes[dIdx + 3] = (sumA * invW).roundToInt().coerceIn(0, 255).toByte()
            }
        }
        return dst
    }

    /**
     * Rotates [source] around its center by [angleDegrees] clockwise.
     */
    fun rotate(
        source: ImageBuffer,
        angleDegrees: Float
    ): ImageBuffer {
        val srcRgba = source.toRgba8888()
        val w = srcRgba.width
        val h = srcRgba.height
        val dst = ImageBuffer(w, h, PixelFormat.RGBA_8888, srcRgba.colorSpace)
        val inBytes = srcRgba.bytes
        val outBytes = dst.bytes

        val rad = (-angleDegrees * PI / 180.0).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)
        val cx = (w - 1) * 0.5f
        val cy = (h - 1) * 0.5f

        for (dy in 0 until h) {
            val ry = dy - cy
            for (dx in 0 until w) {
                val rx = dx - cx
                val sx = (rx * cosA - ry * sinA + cx).roundToInt()
                val sy = (rx * sinA + ry * cosA + cy).roundToInt()
                if (sx in 0 until w && sy in 0 until h) {
                    val sIdx = (sy * w + sx) * 4
                    val dIdx = (dy * w + dx) * 4
                    outBytes[dIdx] = inBytes[sIdx]
                    outBytes[dIdx + 1] = inBytes[sIdx + 1]
                    outBytes[dIdx + 2] = inBytes[sIdx + 2]
                    outBytes[dIdx + 3] = inBytes[sIdx + 3]
                }
            }
        }
        return dst
    }

    /**
     * Composites [overlay] onto [base] using [GpuImageCompositor].
     */
    fun composite(
        base: ImageBuffer,
        overlay: ImageBuffer,
        offsetX: Int = 0,
        offsetY: Int = 0,
        blendMode: ImageBlendMode = ImageBlendMode.NORMAL,
        opacity: Float = 1.0f,
        gpu: GpuOffscreenContext = GpuDevice.acquireOffscreenContext()
    ): ImageBuffer = GpuImageCompositor.compositeLayer(base, overlay, offsetX, offsetY, blendMode, opacity, gpu)

    /**
     * Computes a 256-bin R, G, B, and Rec.709 Luminance histogram for [buffer].
     */
    fun computeHistogram(buffer: ImageBuffer): ImageHistogramResult {
        val rgba = buffer.toRgba8888()
        val rBins = IntArray(256)
        val gBins = IntArray(256)
        val bBins = IntArray(256)
        val lBins = IntArray(256)
        val bytes = rgba.bytes
        var i = 0
        while (i + 3 < bytes.size) {
            val r = bytes[i].toInt() and 0xFF
            val g = bytes[i + 1].toInt() and 0xFF
            val b = bytes[i + 2].toInt() and 0xFF
            val lum = ((r * 54 + g * 183 + b * 19) shr 8).coerceIn(0, 255)
            rBins[r]++
            gBins[g]++
            bBins[b]++
            lBins[lum]++
            i += 4
        }
        return ImageHistogramResult(
            redBins = rBins,
            greenBins = gBins,
            blueBins = bBins,
            luminanceBins = lBins,
            totalPixels = rgba.pixelCount
        )
    }
}
