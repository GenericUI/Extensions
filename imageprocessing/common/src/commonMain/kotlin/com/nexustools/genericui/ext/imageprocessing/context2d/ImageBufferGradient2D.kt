package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.style.RgbaColor
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Strongly-typed 2D gradient descriptor supporting multi-stop `LINEAR`, `RADIAL`,
 * and `CONIC` gradients on an [ImageBuffer2DContext].
 */
class ImageBufferGradient2D(
    val kind: ImageBufferGradientKind,
    val x0: Float = 0f,
    val y0: Float = 0f,
    val r0: Float = 0f,
    val x1: Float = 0f,
    val y1: Float = 0f,
    val r1: Float = 0f,
    val startAngleRadians: Float = 0f
) {
    private class ColorStop(val offset: Float, val color: RgbaColor)

    private val stops = mutableListOf<ColorStop>()

    val stopsCount: Int
        get() = stops.size

    val stopOffsets: FloatArray
        get() = FloatArray(stops.size) { stops[it].offset }

    val stopColors: List<RgbaColor>
        get() = stops.map { it.color }

    /**
     * Adds a color stop at [offset] in `[0.0 .. 1.0]`.
     */
    fun addColorStop(offset: Float, color: RgbaColor): ImageBufferGradient2D {
        val clamped = offset.coerceIn(0f, 1f)
        val insertIdx = stops.indexOfFirst { it.offset > clamped }
        if (insertIdx < 0) {
            stops.add(ColorStop(clamped, color))
        } else {
            stops.add(insertIdx, ColorStop(clamped, color))
        }
        return this
    }

    /**
     * Evaluates the gradient color at coordinates `(px, py)`.
     */
    fun sampleAt(px: Float, py: Float): RgbaColor {
        if (stops.isEmpty()) return RgbaColor.TRANSPARENT
        if (stops.size == 1) return stops[0].color

        val t = when (kind) {
            ImageBufferGradientKind.LINEAR -> {
                val dx = x1 - x0
                val dy = y1 - y0
                val lenSq = dx * dx + dy * dy
                if (lenSq <= 1e-6f) 0f
                else (((px - x0) * dx + (py - y0) * dy) / lenSq).coerceIn(0f, 1f)
            }
            ImageBufferGradientKind.RADIAL -> {
                val cdx = x1 - x0
                val cdy = y1 - y0
                val dr = r1 - r0
                if (abs(cdx) <= 1e-5f && abs(cdy) <= 1e-5f) {
                    val dist = hypot(px - x0, py - y0)
                    val span = max(1e-5f, r1 - r0)
                    ((dist - r0) / span).coerceIn(0f, 1f)
                } else {
                    val pdx = px - x0
                    val pdy = py - y0
                    val a = cdx * cdx + cdy * cdy - dr * dr
                    val b = -2f * (pdx * cdx + pdy * cdy + r0 * dr)
                    val c = pdx * pdx + pdy * pdy - r0 * r0
                    val rawT = if (kotlin.math.abs(a) <= 1e-5f) {
                        if (kotlin.math.abs(b) <= 1e-5f) 0f else -c / b
                    } else {
                        val disc = max(0f, b * b - 4f * a * c)
                        val s = sqrt(disc)
                        val t1 = (-b + s) / (2f * a)
                        val t2 = (-b - s) / (2f * a)
                        max(t1, t2)
                    }
                    rawT.coerceIn(0f, 1f)
                }
            }
            ImageBufferGradientKind.CONIC -> {
                val twoPi = (PI * 2.0).toFloat()
                var angle = atan2(py - y0, px - x0) - startAngleRadians
                angle %= twoPi
                if (angle < 0f) angle += twoPi
                (angle / twoPi).coerceIn(0f, 1f)
            }
        }

        return interpolateStops(t)
    }

    fun interpolateStops(t: Float): RgbaColor {
        if (stops.isEmpty()) return RgbaColor.TRANSPARENT
        if (t <= stops.first().offset) return stops.first().color
        if (t >= stops.last().offset) return stops.last().color
        for (i in 0 until stops.size - 1) {
            val s0 = stops[i]
            val s1 = stops[i + 1]
            if (t <= s1.offset) {
                val span = s1.offset - s0.offset
                val localT = if (span <= 1e-6f) 0f else ((t - s0.offset) / span).coerceIn(0f, 1f)
                val u = 1f - localT
                return RgbaColor(
                    r = (s0.color.r * u + s1.color.r * localT).roundToInt().coerceIn(0, 255),
                    g = (s0.color.g * u + s1.color.g * localT).roundToInt().coerceIn(0, 255),
                    b = (s0.color.b * u + s1.color.b * localT).roundToInt().coerceIn(0, 255),
                    a = (s0.color.a * u + s1.color.a * localT).roundToInt().coerceIn(0, 255)
                )
            }
        }
        return stops.last().color
    }

    private fun abs(v: Float): Float = kotlin.math.abs(v)

    companion object {
        fun linear(x0: Float, y0: Float, x1: Float, y1: Float): ImageBufferGradient2D =
            ImageBufferGradient2D(
                kind = ImageBufferGradientKind.LINEAR,
                x0 = x0,
                y0 = y0,
                x1 = x1,
                y1 = y1
            )

        fun radial(
            x0: Float,
            y0: Float,
            r0: Float,
            x1: Float,
            y1: Float,
            r1: Float
        ): ImageBufferGradient2D =
            ImageBufferGradient2D(
                kind = ImageBufferGradientKind.RADIAL,
                x0 = x0,
                y0 = y0,
                r0 = max(0f, r0),
                x1 = x1,
                y1 = y1,
                r1 = max(0f, r1)
            )

        fun conic(startAngleRadians: Float, cx: Float, cy: Float): ImageBufferGradient2D =
            ImageBufferGradient2D(
                kind = ImageBufferGradientKind.CONIC,
                x0 = cx,
                y0 = cy,
                startAngleRadians = startAngleRadians
            )
    }
}
