package com.nexustools.genericui.ext.canvas2d

import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.geometry.Point
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Interactive cubic-Bezier and spline curve editor widget in `:extensions:canvas2d` extending [Canvas2DExtSurface]
 * (which extends `AbstractCanvas`).
 */
class CurveEditorExt(
    width: Int = 240,
    height: Int = 240
) : Canvas2DExtSurface(width, height) {

    var controlPoint1: Point = Point(0.42f, 0.0f)
        set(value) {
            field = Point(value.x.coerceIn(0f, 1f), value.y)
            onCurveChanged?.invoke(field, controlPoint2)
            requestRender()
        }

    var controlPoint2: Point = Point(0.58f, 1.0f)
        set(value) {
            field = Point(value.x.coerceIn(0f, 1f), value.y)
            onCurveChanged?.invoke(controlPoint1, field)
            requestRender()
        }

    var onCurveChanged: ((Point, Point) -> Unit)? = null

    init {
        registerVBlankRender { ctx, _, _, _ ->
            renderCurveOverlay(ctx)
        }
    }

    /**
     * Evaluates the cubic-Bezier Y value at parameter `t` in `[0.0 .. 1.0]`.
     */
    fun sampleCurveY(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        val u = 1f - clamped
        return 3f * u * u * clamped * controlPoint1.y +
            3f * u * clamped * clamped * controlPoint2.y +
            clamped * clamped * clamped
    }

    /**
     * Generates a normalized `[0.0 .. 1.0]` lookup table of length [size] from the current curve.
     */
    fun sampleLut(size: Int = 256): FloatArray {
        val n = size.coerceAtLeast(2)
        return FloatArray(n) { idx ->
            sampleCurveY(idx.toFloat() / (n - 1).toFloat()).coerceIn(0f, 1f)
        }
    }

    fun renderCurveOverlay(ctx: Canvas2DContext) {
        val w = ctx.width.toFloat()
        val h = ctx.height.toFloat()
        ctx.clearRect(0f, 0f, w, h)
        ctx.setStrokeColor(RgbaColor(90, 160, 255, 255))
        ctx.setLineWidth(2f)
        ctx.beginPath()
        ctx.moveTo(0f, h)
        ctx.bezierCurveTo(
            cp1x = controlPoint1.x * w,
            cp1y = (1f - controlPoint1.y) * h,
            cp2x = controlPoint2.x * w,
            cp2y = (1f - controlPoint2.y) * h,
            x = w,
            y = 0f
        )
        ctx.stroke()
    }
}
