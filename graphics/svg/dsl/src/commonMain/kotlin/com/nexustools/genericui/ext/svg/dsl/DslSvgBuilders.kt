package com.nexustools.genericui.ext.svg.dsl

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.svg.*
import com.nexustools.genericui.style.color.RgbaColor

@PublishedApi
internal inline fun <T : SvgElement> mountSvgElement(
    container: SvgContainer,
    element: T,
    id: String? = null,
    className: String? = null,
    fill: SvgPaint? = null,
    stroke: SvgPaint? = null,
    strokeWidth: Float? = null,
    opacity: Float? = null,
    transform: String? = null,
    clipPathId: String? = null,
    block: (@GenericUiDslMarker T).() -> Unit = {}
): T {
    if (!id.isNullOrBlank()) element.id = id
    if (!className.isNullOrBlank()) element.className = className
    if (fill != null) element.fill = fill
    if (stroke != null) element.stroke = stroke
    if (strokeWidth != null) element.strokeWidth = strokeWidth
    if (opacity != null) element.opacity = opacity
    if (!transform.isNullOrBlank()) element.setTransform(transform)
    if (!clipPathId.isNullOrBlank()) element.clipPathId = clipPathId
    element.block()
    container.addChild(element)
    return element
}

/**
 * Builds a standalone [Svg] scene graph component without attaching it to a parent container yet.
 */
inline fun buildSvg(
    viewBox: SvgViewBox? = null,
    width: Float? = null,
    height: Float? = null,
    preserveAspectRatio: SvgPreserveAspectRatio = SvgPreserveAspectRatio.DEFAULT,
    id: String? = null,
    classes: String? = null,
    widthPx: Int? = null,
    heightPx: Int? = null,
    block: (@GenericUiDslMarker Svg).() -> Unit = {}
): Svg {
    val component = Svg(
        viewBox = viewBox,
        svgWidth = width,
        svgHeight = height,
        preserveAspectRatio = preserveAspectRatio
    )
    if (!classes.isNullOrBlank()) component.addClass(classes)
    if (!id.isNullOrBlank()) component.id = id
    if (widthPx != null) component.widthPx = widthPx
    if (heightPx != null) component.heightPx = heightPx
    component.block()
    return component
}

/**
 * Creates a standalone [Svg] scene graph by parsing W3C SVG XML markup, with an optional
 * configuration [block] for querying and mutating parsed nodes.
 */
inline fun svgFromXml(
    svgMarkup: String,
    id: String? = null,
    classes: String? = null,
    widthPx: Int? = null,
    heightPx: Int? = null,
    block: (@GenericUiDslMarker Svg).() -> Unit = {}
): Svg {
    val component = Svg.fromXml(svgMarkup)
    if (!classes.isNullOrBlank()) component.addClass(classes)
    if (!id.isNullOrBlank()) component.id = id
    if (widthPx != null) component.widthPx = widthPx
    if (heightPx != null) component.heightPx = heightPx
    component.block()
    return component
}

/**
 * In-place [Svg] vector scene graph builder mounted into this [AbstractContainer].
 */
inline fun AbstractContainer.svg(
    viewBox: SvgViewBox? = null,
    width: Float? = null,
    height: Float? = null,
    preserveAspectRatio: SvgPreserveAspectRatio = SvgPreserveAspectRatio.DEFAULT,
    id: String? = null,
    classes: String? = null,
    widthPx: Int? = null,
    heightPx: Int? = null,
    block: (@GenericUiDslMarker Svg).() -> Unit = {}
): Svg = buildSvg(
    viewBox = viewBox,
    width = width,
    height = height,
    preserveAspectRatio = preserveAspectRatio,
    id = id,
    classes = classes,
    widthPx = widthPx,
    heightPx = heightPx,
    block = block
).also { addChild(it) }

/**
 * In-place XML-backed [Svg] component builder mounted into this [AbstractContainer].
 */
inline fun AbstractContainer.svgFromXml(
    svgMarkup: String,
    id: String? = null,
    classes: String? = null,
    widthPx: Int? = null,
    heightPx: Int? = null,
    block: (@GenericUiDslMarker Svg).() -> Unit = {}
): Svg = com.nexustools.genericui.ext.svg.dsl.svgFromXml(
    svgMarkup = svgMarkup,
    id = id,
    classes = classes,
    widthPx = widthPx,
    heightPx = heightPx,
    block = block
).also { addChild(it) }

/**
 * Sets [Svg.viewBox] concisely inside a `svg { ... }` block.
 */
fun Svg.viewBox(minX: Float, minY: Float, width: Float, height: Float) {
    viewBox = SvgViewBox(minX, minY, width, height)
}

/**
 * Configures this [Svg] document's `<defs>` container.
 */
inline fun Svg.defs(
    block: (@GenericUiDslMarker SvgDefs).() -> Unit
): SvgDefs {
    defs.block()
    return defs
}

/**
 * Adds a `<linearGradient>` inside `<defs>`.
 */
inline fun SvgDefs.linearGradient(
    id: String? = null,
    x1: Float = 0f,
    y1: Float = 0f,
    x2: Float = 1f,
    y2: Float = 0f,
    gradientUnits: SvgGradientUnits = SvgGradientUnits.OBJECT_BOUNDING_BOX,
    spreadMethod: String = "pad",
    block: (@GenericUiDslMarker SvgLinearGradient).() -> Unit = {}
): SvgLinearGradient = SvgLinearGradient(
    id = id,
    x1 = x1,
    y1 = y1,
    x2 = x2,
    y2 = y2,
    gradientUnits = gradientUnits,
    spreadMethod = spreadMethod
).also { grad ->
    grad.block()
    addChild(grad)
}

/**
 * Adds a `<radialGradient>` inside `<defs>`.
 */
inline fun SvgDefs.radialGradient(
    id: String? = null,
    cx: Float = 0.5f,
    cy: Float = 0.5f,
    r: Float = 0.5f,
    fx: Float? = null,
    fy: Float? = null,
    gradientUnits: SvgGradientUnits = SvgGradientUnits.OBJECT_BOUNDING_BOX,
    spreadMethod: String = "pad",
    block: (@GenericUiDslMarker SvgRadialGradient).() -> Unit = {}
): SvgRadialGradient = SvgRadialGradient(
    id = id,
    cx = cx,
    cy = cy,
    r = r,
    fx = fx,
    fy = fy,
    gradientUnits = gradientUnits,
    spreadMethod = spreadMethod
).also { grad ->
    grad.block()
    addChild(grad)
}

fun SvgLinearGradient.stop(
    offset: Float,
    color: RgbaColor,
    opacity: Float = 1f
): SvgGradientStop = addStop(offset, color, opacity)

fun SvgLinearGradient.stop(
    offset: Float,
    color: String,
    opacity: Float = 1f
): SvgGradientStop {
    val parsed = SvgColorParser.parse(color)
        ?: throw IllegalArgumentException("Invalid SVG stop color: '$color'")
    return addStop(offset, parsed, opacity)
}

fun SvgRadialGradient.stop(
    offset: Float,
    color: RgbaColor,
    opacity: Float = 1f
): SvgGradientStop = addStop(offset, color, opacity)

fun SvgRadialGradient.stop(
    offset: Float,
    color: String,
    opacity: Float = 1f
): SvgGradientStop {
    val parsed = SvgColorParser.parse(color)
        ?: throw IllegalArgumentException("Invalid SVG stop color: '$color'")
    return addStop(offset, parsed, opacity)
}

/**
 * Adds a `<clipPath>` container inside `<defs>`.
 */
inline fun SvgDefs.clipPath(
    id: String? = null,
    clipPathUnits: SvgGradientUnits = SvgGradientUnits.USER_SPACE_ON_USE,
    block: (@GenericUiDslMarker SvgClipPath).() -> Unit = {}
): SvgClipPath = SvgClipPath(id = id, clipPathUnits = clipPathUnits).also { clip ->
    clip.block()
    addChild(clip)
}

/**
 * Adds a `<g>` group container inside any [SvgContainer] (`Svg`, `SvgGroup`, `SvgDefs`, `SvgClipPath`).
 */
inline fun SvgContainer.g(
    id: String? = null,
    className: String? = null,
    fill: SvgPaint? = null,
    stroke: SvgPaint? = null,
    strokeWidth: Float? = null,
    opacity: Float? = null,
    transform: String? = null,
    clipPathId: String? = null,
    block: (@GenericUiDslMarker SvgGroup).() -> Unit = {}
): SvgGroup = mountSvgElement(
    container = this,
    element = SvgGroup(),
    id = id,
    className = className,
    fill = fill,
    stroke = stroke,
    strokeWidth = strokeWidth,
    opacity = opacity,
    transform = transform,
    clipPathId = clipPathId,
    block = block
)

/**
 * Adds a `<rect>` shape inside any [SvgContainer].
 */
inline fun SvgContainer.rect(
    x: Float = 0f,
    y: Float = 0f,
    width: Float = 0f,
    height: Float = 0f,
    rx: Float = 0f,
    ry: Float = 0f,
    id: String? = null,
    className: String? = null,
    fill: SvgPaint? = null,
    stroke: SvgPaint? = null,
    strokeWidth: Float? = null,
    opacity: Float? = null,
    transform: String? = null,
    clipPathId: String? = null,
    block: (@GenericUiDslMarker SvgRect).() -> Unit = {}
): SvgRect = mountSvgElement(
    container = this,
    element = SvgRect(x = x, y = y, width = width, height = height, rx = rx, ry = ry),
    id = id,
    className = className,
    fill = fill,
    stroke = stroke,
    strokeWidth = strokeWidth,
    opacity = opacity,
    transform = transform,
    clipPathId = clipPathId,
    block = block
)

/**
 * Adds a `<circle>` shape inside any [SvgContainer].
 */
inline fun SvgContainer.circle(
    cx: Float = 0f,
    cy: Float = 0f,
    r: Float = 0f,
    id: String? = null,
    className: String? = null,
    fill: SvgPaint? = null,
    stroke: SvgPaint? = null,
    strokeWidth: Float? = null,
    opacity: Float? = null,
    transform: String? = null,
    clipPathId: String? = null,
    block: (@GenericUiDslMarker SvgCircle).() -> Unit = {}
): SvgCircle = mountSvgElement(
    container = this,
    element = SvgCircle(cx = cx, cy = cy, r = r),
    id = id,
    className = className,
    fill = fill,
    stroke = stroke,
    strokeWidth = strokeWidth,
    opacity = opacity,
    transform = transform,
    clipPathId = clipPathId,
    block = block
)

/**
 * Adds an `<ellipse>` shape inside any [SvgContainer].
 */
inline fun SvgContainer.ellipse(
    cx: Float = 0f,
    cy: Float = 0f,
    rx: Float = 0f,
    ry: Float = 0f,
    id: String? = null,
    className: String? = null,
    fill: SvgPaint? = null,
    stroke: SvgPaint? = null,
    strokeWidth: Float? = null,
    opacity: Float? = null,
    transform: String? = null,
    clipPathId: String? = null,
    block: (@GenericUiDslMarker SvgEllipse).() -> Unit = {}
): SvgEllipse = mountSvgElement(
    container = this,
    element = SvgEllipse(cx = cx, cy = cy, rx = rx, ry = ry),
    id = id,
    className = className,
    fill = fill,
    stroke = stroke,
    strokeWidth = strokeWidth,
    opacity = opacity,
    transform = transform,
    clipPathId = clipPathId,
    block = block
)

/**
 * Adds a `<line>` shape inside any [SvgContainer].
 */
inline fun SvgContainer.line(
    x1: Float = 0f,
    y1: Float = 0f,
    x2: Float = 0f,
    y2: Float = 0f,
    id: String? = null,
    className: String? = null,
    stroke: SvgPaint? = null,
    strokeWidth: Float? = null,
    opacity: Float? = null,
    transform: String? = null,
    clipPathId: String? = null,
    block: (@GenericUiDslMarker SvgLine).() -> Unit = {}
): SvgLine = mountSvgElement(
    container = this,
    element = SvgLine(x1 = x1, y1 = y1, x2 = x2, y2 = y2),
    id = id,
    className = className,
    stroke = stroke,
    strokeWidth = strokeWidth,
    opacity = opacity,
    transform = transform,
    clipPathId = clipPathId,
    block = block
)

/**
 * Adds a `<polyline>` shape inside any [SvgContainer].
 */
inline fun SvgContainer.polyline(
    points: List<SvgPoint> = emptyList(),
    id: String? = null,
    className: String? = null,
    fill: SvgPaint? = null,
    stroke: SvgPaint? = null,
    strokeWidth: Float? = null,
    opacity: Float? = null,
    transform: String? = null,
    clipPathId: String? = null,
    block: (@GenericUiDslMarker SvgPolyline).() -> Unit = {}
): SvgPolyline = mountSvgElement(
    container = this,
    element = SvgPolyline(points = points),
    id = id,
    className = className,
    fill = fill,
    stroke = stroke,
    strokeWidth = strokeWidth,
    opacity = opacity,
    transform = transform,
    clipPathId = clipPathId,
    block = block
)

/**
 * Adds a `<polyline>` shape inside any [SvgContainer] parsed from an SVG `points` coordinate string.
 */
inline fun SvgContainer.polyline(
    points: String,
    id: String? = null,
    className: String? = null,
    fill: SvgPaint? = null,
    stroke: SvgPaint? = null,
    strokeWidth: Float? = null,
    opacity: Float? = null,
    transform: String? = null,
    clipPathId: String? = null,
    block: (@GenericUiDslMarker SvgPolyline).() -> Unit = {}
): SvgPolyline = polyline(
    points = SvgPoint.parsePoints(points),
    id = id,
    className = className,
    fill = fill,
    stroke = stroke,
    strokeWidth = strokeWidth,
    opacity = opacity,
    transform = transform,
    clipPathId = clipPathId,
    block = block
)

/**
 * Adds a `<polygon>` shape inside any [SvgContainer].
 */
inline fun SvgContainer.polygon(
    points: List<SvgPoint> = emptyList(),
    id: String? = null,
    className: String? = null,
    fill: SvgPaint? = null,
    stroke: SvgPaint? = null,
    strokeWidth: Float? = null,
    opacity: Float? = null,
    transform: String? = null,
    clipPathId: String? = null,
    block: (@GenericUiDslMarker SvgPolygon).() -> Unit = {}
): SvgPolygon = mountSvgElement(
    container = this,
    element = SvgPolygon(points = points),
    id = id,
    className = className,
    fill = fill,
    stroke = stroke,
    strokeWidth = strokeWidth,
    opacity = opacity,
    transform = transform,
    clipPathId = clipPathId,
    block = block
)

/**
 * Adds a `<polygon>` shape inside any [SvgContainer] parsed from an SVG `points` coordinate string.
 */
inline fun SvgContainer.polygon(
    points: String,
    id: String? = null,
    className: String? = null,
    fill: SvgPaint? = null,
    stroke: SvgPaint? = null,
    strokeWidth: Float? = null,
    opacity: Float? = null,
    transform: String? = null,
    clipPathId: String? = null,
    block: (@GenericUiDslMarker SvgPolygon).() -> Unit = {}
): SvgPolygon = polygon(
    points = SvgPoint.parsePoints(points),
    id = id,
    className = className,
    fill = fill,
    stroke = stroke,
    strokeWidth = strokeWidth,
    opacity = opacity,
    transform = transform,
    clipPathId = clipPathId,
    block = block
)

/**
 * Adds a `<path>` shape inside any [SvgContainer].
 */
inline fun SvgContainer.path(
    d: String = "",
    id: String? = null,
    className: String? = null,
    fill: SvgPaint? = null,
    fillRule: SvgFillRule? = null,
    stroke: SvgPaint? = null,
    strokeWidth: Float? = null,
    opacity: Float? = null,
    transform: String? = null,
    clipPathId: String? = null,
    block: (@GenericUiDslMarker SvgPath).() -> Unit = {}
): SvgPath = mountSvgElement(
    container = this,
    element = SvgPath(d = d).also { if (fillRule != null) it.fillRule = fillRule },
    id = id,
    className = className,
    fill = fill,
    stroke = stroke,
    strokeWidth = strokeWidth,
    opacity = opacity,
    transform = transform,
    clipPathId = clipPathId,
    block = block
)

/**
 * Adds a `<text>` element inside any [SvgContainer].
 */
inline fun SvgContainer.text(
    x: Float = 0f,
    y: Float = 0f,
    content: String = "",
    fontSize: Float = 16f,
    fontFamily: String = "sans-serif",
    fontWeight: String = "normal",
    textAnchor: String = "start",
    id: String? = null,
    className: String? = null,
    fill: SvgPaint? = null,
    stroke: SvgPaint? = null,
    strokeWidth: Float? = null,
    opacity: Float? = null,
    transform: String? = null,
    clipPathId: String? = null,
    block: (@GenericUiDslMarker SvgText).() -> Unit = {}
): SvgText = mountSvgElement(
    container = this,
    element = SvgText(
        x = x,
        y = y,
        text = content,
        fontSize = fontSize,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        textAnchor = textAnchor
    ),
    id = id,
    className = className,
    fill = fill,
    stroke = stroke,
    strokeWidth = strokeWidth,
    opacity = opacity,
    transform = transform,
    clipPathId = clipPathId,
    block = block
)

/**
 * Adds a `<use>` reference element inside any [SvgContainer].
 */
inline fun SvgContainer.use(
    href: String = "",
    x: Float = 0f,
    y: Float = 0f,
    width: Float? = null,
    height: Float? = null,
    id: String? = null,
    className: String? = null,
    fill: SvgPaint? = null,
    stroke: SvgPaint? = null,
    strokeWidth: Float? = null,
    opacity: Float? = null,
    transform: String? = null,
    clipPathId: String? = null,
    block: (@GenericUiDslMarker SvgUse).() -> Unit = {}
): SvgUse = mountSvgElement(
    container = this,
    element = SvgUse(href = href, x = x, y = y, width = width, height = height),
    id = id,
    className = className,
    fill = fill,
    stroke = stroke,
    strokeWidth = strokeWidth,
    opacity = opacity,
    transform = transform,
    clipPathId = clipPathId,
    block = block
)

fun SvgElement.fillColor(color: RgbaColor) {
    fill = SvgPaint.SolidColor(color)
}

fun SvgElement.fill(paint: String) {
    fill = SvgPaint.parse(paint)
}

fun SvgElement.fillGradient(id: String, fallbackColor: RgbaColor? = null) {
    fill = SvgPaint.GradientRef(id = id, fallbackColor = fallbackColor)
}

fun SvgElement.strokeColor(color: RgbaColor, width: Float? = null) {
    stroke = SvgPaint.SolidColor(color)
    if (width != null) strokeWidth = width
}

fun SvgElement.stroke(paint: String, width: Float? = null) {
    stroke = SvgPaint.parse(paint)
    if (width != null) strokeWidth = width
}

fun SvgElement.translate(tx: Float, ty: Float = 0f) {
    transformList.add(SvgTransform.Translate(tx, ty))
}

fun SvgElement.scale(sx: Float, sy: Float = sx) {
    transformList.add(SvgTransform.Scale(sx, sy))
}

fun SvgElement.rotate(angleDeg: Float, cx: Float = 0f, cy: Float = 0f) {
    transformList.add(SvgTransform.Rotate(angleDeg, cx, cy))
}

fun SvgElement.skewX(angleDeg: Float) {
    transformList.add(SvgTransform.SkewX(angleDeg))
}

fun SvgElement.skewY(angleDeg: Float) {
    transformList.add(SvgTransform.SkewY(angleDeg))
}

fun SvgElement.transform(rawTransform: String) {
    setTransform(rawTransform)
}

fun SvgElement.onClick(handler: () -> Unit) {
    onClick = handler
}
