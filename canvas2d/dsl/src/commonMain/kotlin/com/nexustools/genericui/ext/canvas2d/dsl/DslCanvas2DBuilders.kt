package com.nexustools.genericui.ext.canvas2d.dsl

import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.component.media.CurveEditor
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtSurface
import com.nexustools.genericui.ext.canvas2d.CurveEditorExt
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.geometry.Point

/**
 * In-place [Canvas2DExtSurface] builder with optional VBlank render callback and `style { ... }` support.
 */
fun AbstractContainer.canvas2DExt(
    width: Int = 800,
    height: Int = 600,
    id: String? = null,
    classes: String? = null,
    onRender: ((Canvas2DContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker Canvas2DExtSurface.() -> Unit)? = null
): Canvas2DExtSurface {
    val surface = Canvas2DExtSurface(width = width, height = height)
    if (!classes.isNullOrBlank()) {
        surface.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        surface.id = id
    }
    if (onRender != null) {
        surface.registerVBlankRender { ctx, deltaMs, elapsedMs, frameIdx ->
            onRender(ctx, deltaMs, elapsedMs, frameIdx)
        }
    }
    if (block != null) {
        surface.block()
    }
    addChild(surface)
    return surface
}

/**
 * In-place [Canvas2D] builder.
 */
fun AbstractContainer.canvas2D(
    width: Int = 800,
    height: Int = 600,
    id: String? = null,
    classes: String? = null,
    onRender: ((Canvas2DContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker Canvas2D.() -> Unit)? = null
): Canvas2D {
    val canvas = Canvas2D(width = width, height = height)
    if (!classes.isNullOrBlank()) {
        canvas.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        canvas.id = id
    }
    if (onRender != null) {
        canvas.registerVBlankRender { ctx, deltaMs, elapsedMs, frameIdx ->
            onRender(ctx, deltaMs, elapsedMs, frameIdx)
        }
    }
    if (block != null) {
        canvas.block()
    }
    addChild(canvas)
    return canvas
}

/**
 * In-place [CurveEditorExt] cubic-Bezier curve editor builder.
 */
fun AbstractContainer.curveEditorExt(
    width: Int = 240,
    height: Int = 240,
    controlPoint1: Point = Point(0.42f, 0.0f),
    controlPoint2: Point = Point(0.58f, 1.0f),
    id: String? = null,
    classes: String? = null,
    onCurveChanged: ((Point, Point) -> Unit)? = null,
    block: (@GenericUiDslMarker CurveEditorExt.() -> Unit)? = null
): CurveEditorExt {
    val editor = CurveEditorExt(width = width, height = height)
    editor.controlPoint1 = controlPoint1
    editor.controlPoint2 = controlPoint2
    if (onCurveChanged != null) {
        editor.onCurveChanged = onCurveChanged
    }
    if (!classes.isNullOrBlank()) {
        editor.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        editor.id = id
    }
    if (block != null) {
        editor.block()
    }
    addChild(editor)
    return editor
}

/**
 * In-place [CurveEditor] builder.
 */
fun AbstractContainer.curveEditor(
    width: Int = 240,
    height: Int = 240,
    controlPoint1: Point = Point(0.42f, 0.0f),
    controlPoint2: Point = Point(0.58f, 1.0f),
    id: String? = null,
    classes: String? = null,
    onCurveChanged: ((Point, Point) -> Unit)? = null,
    block: (@GenericUiDslMarker CurveEditor.() -> Unit)? = null
): CurveEditor {
    val editor = CurveEditor(width = width, height = height)
    editor.controlPoint1 = controlPoint1
    editor.controlPoint2 = controlPoint2
    if (onCurveChanged != null) {
        editor.onCurveChanged = onCurveChanged
    }
    if (!classes.isNullOrBlank()) {
        editor.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        editor.id = id
    }
    if (block != null) {
        editor.block()
    }
    addChild(editor)
    return editor
}
