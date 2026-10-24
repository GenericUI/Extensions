package com.nexustools.genericui.component.media

import com.nexustools.genericui.geometry.Point

/**
 * Interactive cubic-Bezier and spline curve editor widget built on [Canvas2D].
 *
 * Used in HAEditor for animation timeline keyframe easing curves (`(x1, y1, x2, y2)`)
 * and GIMP filter tone/color curves.
 */
class CurveEditor(
    width: Int = 240,
    height: Int = 240
) : Canvas2D(width, height) {
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

    /**
     * Evaluates the cubic-Bezier curve Y value at normalized parameter `t` in `[0.0 .. 1.0]`.
     */
    fun sampleCurveY(t: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * controlPoint1.y +
            3f * u * t * t * controlPoint2.y +
            t * t * t
    }
}
