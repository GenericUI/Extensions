package com.nexustools.genericui.ext.svg

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.PI
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
 * Pure-Kotlin anti-aliased 2D software vector rasterizer and inverse-transform hit-tester
 * rendering [Svg] scene graphs directly into [ImageBuffer] (`RGBA_8888`).
 *
 * Supports `viewBox` + `preserveAspectRatio` viewport mapping, hierarchical [SvgTransform]
 * matrices, `NONZERO` & `EVENODD` scanline winding fill, stroke expansion (`strokeWidth`,
 * `strokeLinecap`, `strokeLinejoin`, `strokeDasharray`, `strokeDashoffset`), linear & radial
 * gradients, `<clipPath>` masking, group/element `opacity`, `<use>` references, and built-in
 * vector glyph rendering for `<text>`.
 */
object SvgSoftwareRasterizer {

    fun render(svg: Svg, widthPx: Int, heightPx: Int): ImageBuffer {
        val safeW = widthPx.coerceAtLeast(1)
        val safeH = heightPx.coerceAtLeast(1)
        val pixels = ByteArray(safeW * safeH * 4)
        val viewportMatrix = computeViewportMatrix(svg, safeW, safeH)
        val rootStyle = InheritedStyle.ROOT
        val visitedUseIds = HashSet<String>()

        for (child in svg.children) {
            renderNode(
                svg = svg,
                element = child,
                parentMatrix = viewportMatrix,
                inherited = rootStyle,
                targetBytes = pixels,
                widthPx = safeW,
                heightPx = safeH,
                activeClipMask = null,
                visitedUseIds = visitedUseIds
            )
        }

        return ImageBuffer(
            width = safeW,
            height = safeH,
            pixelFormat = PixelFormat.RGBA_8888,
            colorSpace = ImageColorSpace.SRGB,
            bytes = pixels
        )
    }

    fun hitTest(
        svg: Svg,
        viewportX: Float,
        viewportY: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): SvgElement? {
        if (viewportWidth <= 0f || viewportHeight <= 0f) return null
        val viewportMatrix = computeViewportMatrix(svg, viewportWidth.roundToInt().coerceAtLeast(1), viewportHeight.roundToInt().coerceAtLeast(1))
        val visitedUseIds = HashSet<String>()
        return hitTestChildrenReverse(
            svg = svg,
            elements = svg.children,
            parentMatrix = viewportMatrix,
            inherited = InheritedStyle.ROOT,
            vx = viewportX,
            vy = viewportY,
            visitedUseIds = visitedUseIds
        )
    }

    internal fun computeViewportMatrix(
        svg: Svg,
        widthPx: Int,
        heightPx: Int
    ): SvgTransform.AffineMatrix {
        val w = widthPx.toFloat().coerceAtLeast(1f)
        val h = heightPx.toFloat().coerceAtLeast(1f)
        val vb = svg.viewBox
        if (vb != null) {
            return svg.preserveAspectRatio.computeViewportMatrix(vb, w, h)
        }
        val sw = svg.svgWidth
        val sh = svg.svgHeight
        if (sw != null && sh != null && sw > 0f && sh > 0f) {
            return SvgTransform.AffineMatrix(
                a = w / sw,
                b = 0f,
                c = 0f,
                d = h / sh,
                e = 0f,
                f = 0f
            )
        }
        return SvgTransform.AffineMatrix.IDENTITY
    }

    private data class InheritedStyle(
        val fill: SvgPaint,
        val fillOpacity: Float,
        val fillRule: SvgFillRule,
        val stroke: SvgPaint,
        val strokeWidth: Float,
        val strokeOpacity: Float,
        val strokeLinecap: SvgStrokeLinecap,
        val strokeLinejoin: SvgStrokeLinejoin,
        val strokeDasharray: List<Float>?,
        val strokeDashoffset: Float,
        val fontSize: Float,
        val fontFamily: String,
        val fontWeight: String,
        val textAnchor: String,
        val currentColor: RgbaColor
    ) {
        fun resolveWith(el: SvgElement): InheritedStyle {
            val nextFontSize = (el as? SvgText)?.explicitFontSize ?: fontSize
            val nextFontFamily = (el as? SvgText)?.explicitFontFamily ?: fontFamily
            val nextFontWeight = (el as? SvgText)?.explicitFontWeight ?: fontWeight
            val nextTextAnchor = (el as? SvgText)?.explicitTextAnchor ?: textAnchor
            val nextColor = el.attributes["color"]?.let { SvgColorParser.parse(it, currentColor) } ?: currentColor
            return InheritedStyle(
                fill = el.fill ?: fill,
                fillOpacity = el.explicitFillOpacity ?: fillOpacity,
                fillRule = el.explicitFillRule ?: fillRule,
                stroke = el.stroke ?: stroke,
                strokeWidth = el.explicitStrokeWidth ?: strokeWidth,
                strokeOpacity = el.explicitStrokeOpacity ?: strokeOpacity,
                strokeLinecap = el.explicitStrokeLinecap ?: strokeLinecap,
                strokeLinejoin = el.explicitStrokeLinejoin ?: strokeLinejoin,
                strokeDasharray = el.strokeDasharray ?: strokeDasharray,
                strokeDashoffset = el.explicitStrokeDashoffset ?: strokeDashoffset,
                fontSize = nextFontSize,
                fontFamily = nextFontFamily,
                fontWeight = nextFontWeight,
                textAnchor = nextTextAnchor,
                currentColor = nextColor
            )
        }

        companion object {
            val ROOT = InheritedStyle(
                fill = SvgPaint.SolidColor(RgbaColor.BLACK),
                fillOpacity = 1f,
                fillRule = SvgFillRule.NONZERO,
                stroke = SvgPaint.None,
                strokeWidth = 1f,
                strokeOpacity = 1f,
                strokeLinecap = SvgStrokeLinecap.BUTT,
                strokeLinejoin = SvgStrokeLinejoin.MITER,
                strokeDasharray = null,
                strokeDashoffset = 0f,
                fontSize = 16f,
                fontFamily = "sans-serif",
                fontWeight = "normal",
                textAnchor = "start",
                currentColor = RgbaColor.BLACK
            )
        }
    }

    private class LocalGeometry(
        val subpaths: List<SvgPathParser.Subpath>,
        val fillable: Boolean,
        val bboxMinX: Float,
        val bboxMinY: Float,
        val bboxMaxX: Float,
        val bboxMaxY: Float
    ) {
        val bboxWidth: Float get() = (bboxMaxX - bboxMinX).coerceAtLeast(0f)
        val bboxHeight: Float get() = (bboxMaxY - bboxMinY).coerceAtLeast(0f)
    }

    private fun renderNode(
        svg: Svg,
        element: SvgElement,
        parentMatrix: SvgTransform.AffineMatrix,
        inherited: InheritedStyle,
        targetBytes: ByteArray,
        widthPx: Int,
        heightPx: Int,
        activeClipMask: FloatArray?,
        visitedUseIds: MutableSet<String>
    ) {
        if (!element.visible || element.opacity <= 0.001f) return
        if (element is SvgDefs || element is SvgClipPath || element is SvgLinearGradient || element is SvgRadialGradient) {
            return
        }

        val ctm = parentMatrix.multiply(element.directTransformMatrix())
        val style = inherited.resolveWith(element)

        if (element is SvgGroup) {
            val groupClipMask = resolveClipMask(
                svg = svg,
                clipPathId = element.clipPathId,
                elementCtm = ctm,
                localBbox = null,
                widthPx = widthPx,
                heightPx = heightPx,
                parentClipMask = activeClipMask,
                visitedUseIds = visitedUseIds
            )
            if (element.opacity < 0.999f) {
                val temp = ByteArray(widthPx * heightPx * 4)
                for (child in element.children) {
                    renderNode(svg, child, ctm, style, temp, widthPx, heightPx, groupClipMask, visitedUseIds)
                }
                compositeBufferOver(temp, targetBytes, widthPx, heightPx, element.opacity)
            } else {
                for (child in element.children) {
                    renderNode(svg, child, ctm, style, targetBytes, widthPx, heightPx, groupClipMask, visitedUseIds)
                }
            }
            return
        }

        if (element is SvgUse) {
            val targetId = element.targetId
            if (targetId.isEmpty() || targetId in visitedUseIds) return
            val referenced = svg.findById(targetId) ?: return
            visitedUseIds.add(targetId)
            try {
                val useMatrix = if (element.x != 0f || element.y != 0f) {
                    ctm.multiply(SvgTransform.AffineMatrix(1f, 0f, 0f, 1f, element.x, element.y))
                } else {
                    ctm
                }
                val useClipMask = resolveClipMask(
                    svg = svg,
                    clipPathId = element.clipPathId,
                    elementCtm = useMatrix,
                    localBbox = null,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    parentClipMask = activeClipMask,
                    visitedUseIds = visitedUseIds
                )
                if (element.opacity < 0.999f) {
                    val temp = ByteArray(widthPx * heightPx * 4)
                    renderNode(svg, referenced, useMatrix, style, temp, widthPx, heightPx, useClipMask, visitedUseIds)
                    compositeBufferOver(temp, targetBytes, widthPx, heightPx, element.opacity)
                } else {
                    renderNode(svg, referenced, useMatrix, style, targetBytes, widthPx, heightPx, useClipMask, visitedUseIds)
                }
            } finally {
                visitedUseIds.remove(targetId)
            }
            return
        }

        if (element is SvgText) {
            renderTextElement(svg, element, ctm, style, targetBytes, widthPx, heightPx, activeClipMask, visitedUseIds)
            return
        }

        val tol = computeLocalTolerance(ctm)
        val geom = extractLocalGeometry(element, tol) ?: return
        val effectiveClipMask = resolveClipMask(
            svg = svg,
            clipPathId = element.clipPathId,
            elementCtm = ctm,
            localBbox = geom,
            widthPx = widthPx,
            heightPx = heightPx,
            parentClipMask = activeClipMask,
            visitedUseIds = visitedUseIds
        )

        val hasFill = geom.fillable && style.fill != SvgPaint.None && style.fillOpacity > 0.001f
        val hasStroke = style.stroke != SvgPaint.None && style.strokeWidth > 0f && style.strokeOpacity > 0.001f
        if (!hasFill && !hasStroke) return

        val isolateElementOpacity = element.opacity < 0.999f && hasFill && hasStroke
        val dstBuffer = if (isolateElementOpacity) ByteArray(widthPx * heightPx * 4) else targetBytes
        val opacityMul = if (isolateElementOpacity) 1f else element.opacity

        if (hasFill) {
            val devicePolys = geom.subpaths.map { sp ->
                SvgPathParser.Subpath(sp.points.map { ctm.mapPoint(it) }, sp.closed)
            }
            val sampler = createPaintSampler(svg, style.fill, style.fillOpacity * opacityMul, style.currentColor, ctm, geom)
            if (sampler != null) {
                rasterizeFillSubpaths(
                    subpaths = devicePolys,
                    fillRule = style.fillRule,
                    sampler = sampler,
                    targetBytes = dstBuffer,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    clipMask = effectiveClipMask
                )
            }
        }

        if (hasStroke) {
            val sampler = createPaintSampler(svg, style.stroke, style.strokeOpacity * opacityMul, style.currentColor, ctm, geom)
            if (sampler != null) {
                rasterizeStrokeSubpaths(
                    localSubpaths = geom.subpaths,
                    ctm = ctm,
                    strokeWidth = style.strokeWidth,
                    linecap = style.strokeLinecap,
                    linejoin = style.strokeLinejoin,
                    dasharray = style.strokeDasharray,
                    dashoffset = style.strokeDashoffset,
                    localTolerance = tol,
                    sampler = sampler,
                    targetBytes = dstBuffer,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    clipMask = effectiveClipMask
                )
            }
        }

        if (isolateElementOpacity) {
            compositeBufferOver(dstBuffer, targetBytes, widthPx, heightPx, element.opacity)
        }
    }

    private fun renderTextElement(
        svg: Svg,
        textEl: SvgText,
        ctm: SvgTransform.AffineMatrix,
        style: InheritedStyle,
        targetBytes: ByteArray,
        widthPx: Int,
        heightPx: Int,
        activeClipMask: FloatArray?,
        visitedUseIds: MutableSet<String>
    ) {
        if (textEl.text.isEmpty()) return
        val strokes = SvgBuiltinFont.buildTextStrokeSubpaths(
            text = textEl.text,
            x = textEl.x,
            y = textEl.y,
            fontSize = style.fontSize,
            textAnchor = style.textAnchor
        )
        if (strokes.isEmpty()) return
        val subpaths = strokes.map { SvgPathParser.Subpath(it, closed = false) }
        val geom = buildGeometryFromSubpaths(subpaths, fillable = true) ?: return
        val effectiveClipMask = resolveClipMask(
            svg = svg,
            clipPathId = textEl.clipPathId,
            elementCtm = ctm,
            localBbox = geom,
            widthPx = widthPx,
            heightPx = heightPx,
            parentClipMask = activeClipMask,
            visitedUseIds = visitedUseIds
        )

        val isBold = style.fontWeight.contains("bold", ignoreCase = true) ||
            (style.fontWeight.toIntOrNull()?.let { it >= 600 } == true)
        val glyphWeight = max(0.8f, style.fontSize * if (isBold) 0.16f else 0.11f)
        val tol = computeLocalTolerance(ctm)

        val paintToUse = if (style.fill != SvgPaint.None && style.fillOpacity > 0.001f) {
            style.fill to (style.fillOpacity * textEl.opacity)
        } else if (style.stroke != SvgPaint.None && style.strokeOpacity > 0.001f) {
            style.stroke to (style.strokeOpacity * textEl.opacity)
        } else {
            return
        }

        val sampler = createPaintSampler(svg, paintToUse.first, paintToUse.second, style.currentColor, ctm, geom) ?: return
        rasterizeStrokeSubpaths(
            localSubpaths = subpaths,
            ctm = ctm,
            strokeWidth = glyphWeight,
            linecap = SvgStrokeLinecap.ROUND,
            linejoin = SvgStrokeLinejoin.ROUND,
            dasharray = null,
            dashoffset = 0f,
            localTolerance = tol,
            sampler = sampler,
            targetBytes = targetBytes,
            widthPx = widthPx,
            heightPx = heightPx,
            clipMask = effectiveClipMask
        )
    }

    private fun resolveClipMask(
        svg: Svg,
        clipPathId: String?,
        elementCtm: SvgTransform.AffineMatrix,
        localBbox: LocalGeometry?,
        widthPx: Int,
        heightPx: Int,
        parentClipMask: FloatArray?,
        visitedUseIds: MutableSet<String>
    ): FloatArray? {
        if (clipPathId.isNullOrBlank()) return parentClipMask
        val clipNode = svg.findById(clipPathId) as? SvgClipPath ?: return parentClipMask
        val mask = FloatArray(widthPx * heightPx)

        val baseCtm = if (clipNode.clipPathUnits == SvgGradientUnits.OBJECT_BOUNDING_BOX && localBbox != null) {
            val bboxMatrix = SvgTransform.AffineMatrix(
                a = localBbox.bboxWidth.coerceAtLeast(1e-4f),
                b = 0f,
                c = 0f,
                d = localBbox.bboxHeight.coerceAtLeast(1e-4f),
                e = localBbox.bboxMinX,
                f = localBbox.bboxMinY
            )
            elementCtm.multiply(clipNode.directTransformMatrix()).multiply(bboxMatrix)
        } else {
            elementCtm.multiply(clipNode.directTransformMatrix())
        }

        for (child in clipNode.children) {
            renderShapeToMask(svg, child, baseCtm, mask, widthPx, heightPx, visitedUseIds)
        }

        if (parentClipMask != null) {
            for (i in mask.indices) {
                mask[i] = min(mask[i], parentClipMask[i])
            }
        }
        return mask
    }

    private fun renderShapeToMask(
        svg: Svg,
        element: SvgElement,
        parentMatrix: SvgTransform.AffineMatrix,
        outMask: FloatArray,
        widthPx: Int,
        heightPx: Int,
        visitedUseIds: MutableSet<String>
    ) {
        if (!element.visible) return
        val ctm = parentMatrix.multiply(element.directTransformMatrix())
        when (element) {
            is SvgGroup -> {
                for (child in element.children) {
                    renderShapeToMask(svg, child, ctm, outMask, widthPx, heightPx, visitedUseIds)
                }
            }
            is SvgUse -> {
                val targetId = element.targetId
                if (targetId.isEmpty() || targetId in visitedUseIds) return
                val target = svg.findById(targetId) ?: return
                visitedUseIds.add(targetId)
                try {
                    val m = if (element.x != 0f || element.y != 0f) {
                        ctm.multiply(SvgTransform.AffineMatrix(1f, 0f, 0f, 1f, element.x, element.y))
                    } else ctm
                    renderShapeToMask(svg, target, m, outMask, widthPx, heightPx, visitedUseIds)
                } finally {
                    visitedUseIds.remove(targetId)
                }
            }
            else -> {
                val tol = computeLocalTolerance(ctm)
                val geom = extractLocalGeometry(element, tol) ?: return
                val devicePolys = geom.subpaths.map { sp ->
                    SvgPathParser.Subpath(sp.points.map { ctm.mapPoint(it) }, sp.closed)
                }
                accumulateCoverageMask(devicePolys, element.fillRule, outMask, widthPx, heightPx)
            }
        }
    }

    private fun computeLocalTolerance(ctm: SvgTransform.AffineMatrix): Float {
        val scale = max(ctm.maxAxisScale(), 1e-3f)
        return (0.2f / scale).coerceIn(0.002f, 0.5f)
    }

    private fun extractLocalGeometry(element: SvgElement, tolerance: Float): LocalGeometry? {
        val subpaths: List<SvgPathParser.Subpath>
        val fillable: Boolean
        when (element) {
            is SvgRect -> {
                if (element.width <= 0f || element.height <= 0f) return null
                subpaths = listOf(buildRoundedRectSubpath(element.x, element.y, element.width, element.height, element.rx, element.ry, tolerance))
                fillable = true
            }
            is SvgCircle -> {
                if (element.r <= 0f) return null
                subpaths = listOf(buildEllipseSubpath(element.cx, element.cy, element.r, element.r, tolerance))
                fillable = true
            }
            is SvgEllipse -> {
                if (element.rx <= 0f || element.ry <= 0f) return null
                subpaths = listOf(buildEllipseSubpath(element.cx, element.cy, element.rx, element.ry, tolerance))
                fillable = true
            }
            is SvgLine -> {
                subpaths = listOf(SvgPathParser.Subpath(listOf(SvgPoint(element.x1, element.y1), SvgPoint(element.x2, element.y2)), closed = false))
                fillable = false
            }
            is SvgPolyline -> {
                if (element.points.size < 2) return null
                subpaths = listOf(SvgPathParser.Subpath(element.points, closed = false))
                fillable = true
            }
            is SvgPolygon -> {
                if (element.points.size < 2) return null
                subpaths = listOf(SvgPathParser.Subpath(element.points, closed = true))
                fillable = true
            }
            is SvgPath -> {
                val parsed = element.resolveSubpaths(tolerance)
                if (parsed.isEmpty()) return null
                subpaths = parsed
                fillable = true
            }
            else -> return null
        }
        return buildGeometryFromSubpaths(subpaths, fillable)
    }

    private fun buildGeometryFromSubpaths(
        subpaths: List<SvgPathParser.Subpath>,
        fillable: Boolean
    ): LocalGeometry? {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var anyPoint = false
        for (sp in subpaths) {
            for (pt in sp.points) {
                anyPoint = true
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
        }
        if (!anyPoint) return null
        return LocalGeometry(subpaths, fillable, minX, minY, maxX, maxY)
    }

    private fun buildRoundedRectSubpath(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        rxIn: Float,
        ryIn: Float,
        tolerance: Float
    ): SvgPathParser.Subpath {
        val rx = min(rxIn.coerceAtLeast(0f), w * 0.5f)
        val ry = min(ryIn.coerceAtLeast(0f), h * 0.5f)
        if (rx <= 1e-4f || ry <= 1e-4f) {
            return SvgPathParser.Subpath(
                points = listOf(
                    SvgPoint(x, y),
                    SvgPoint(x + w, y),
                    SvgPoint(x + w, y + h),
                    SvgPoint(x, y + h)
                ),
                closed = true
            )
        }
        val maxR = max(rx, ry)
        val segsPerCorner = max(6, ceil((PI * 0.5 * sqrt(maxR / max(tolerance, 0.01f))).toDouble()).toInt()).coerceAtMost(32)
        val pts = ArrayList<SvgPoint>(segsPerCorner * 4 + 4)

        fun addCornerArc(cx: Float, cy: Float, startDeg: Float, endDeg: Float) {
            for (i in 0..segsPerCorner) {
                val t = i.toFloat() / segsPerCorner.toFloat()
                val rad = (startDeg + (endDeg - startDeg) * t) * (PI / 180.0)
                pts.add(SvgPoint((cx + rx * cos(rad)).toFloat(), (cy + ry * sin(rad)).toFloat()))
            }
        }

        addCornerArc(x + w - rx, y + ry, -90f, 0f)
        addCornerArc(x + w - rx, y + h - ry, 0f, 90f)
        addCornerArc(x + rx, y + h - ry, 90f, 180f)
        addCornerArc(x + rx, y + ry, 180f, 270f)
        return SvgPathParser.Subpath(pts, closed = true)
    }

    private fun buildEllipseSubpath(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        tolerance: Float
    ): SvgPathParser.Subpath {
        val maxR = max(rx, ry)
        val steps = max(32, ceil((2.0 * PI * sqrt(maxR / max(tolerance, 0.01f)))).toInt()).coerceAtMost(256)
        val pts = ArrayList<SvgPoint>(steps)
        for (i in 0 until steps) {
            val angle = 2.0 * PI * i / steps
            pts.add(SvgPoint((cx + rx * cos(angle)).toFloat(), (cy + ry * sin(angle)).toFloat()))
        }
        return SvgPathParser.Subpath(pts, closed = true)
    }

    // -------------------------------------------------------------------------
    // Paint & Gradient Sampling
    // -------------------------------------------------------------------------

    private fun interface PixelPaintSampler {
        fun sampleRgba(pxCenterX: Float, pxCenterY: Float, coverage: Float): IntArray?
    }

    private fun createPaintSampler(
        svg: Svg,
        paint: SvgPaint,
        opacityMul: Float,
        currentColor: RgbaColor,
        elementCtm: SvgTransform.AffineMatrix,
        localGeom: LocalGeometry
    ): ((Float, Float) -> RgbaColor)? {
        val alphaScale = opacityMul.coerceIn(0f, 1f)
        if (alphaScale <= 0.001f) return null
        return when (paint) {
            SvgPaint.None -> null
            SvgPaint.CurrentColor -> {
                val effA = ((currentColor.a / 255f) * alphaScale * 255f).roundToInt().coerceIn(0, 255)
                if (effA == 0) return null
                val c = RgbaColor(currentColor.r, currentColor.g, currentColor.b, effA);
                { _, _ -> c }
            }
            is SvgPaint.SolidColor -> {
                val effA = ((paint.color.a / 255f) * alphaScale * 255f).roundToInt().coerceIn(0, 255)
                if (effA == 0) return null
                val c = RgbaColor(paint.color.r, paint.color.g, paint.color.b, effA);
                { _, _ -> c }
            }
            is SvgPaint.GradientRef -> {
                val gradEl = svg.findById(paint.cleanId)
                when (gradEl) {
                    is SvgLinearGradient -> buildLinearGradientSampler(gradEl, alphaScale, elementCtm, localGeom)
                    is SvgRadialGradient -> buildRadialGradientSampler(gradEl, alphaScale, elementCtm, localGeom)
                    else -> {
                        val fb = paint.fallbackColor ?: return null
                        val effA = ((fb.a / 255f) * alphaScale * 255f).roundToInt().coerceIn(0, 255)
                        val c = RgbaColor(fb.r, fb.g, fb.b, effA);
                        { _, _ -> c }
                    }
                }
            }
        }
    }

    private fun buildGradientToDeviceInverse(
        units: SvgGradientUnits,
        gradientTransform: List<SvgTransform>,
        elementCtm: SvgTransform.AffineMatrix,
        localGeom: LocalGeometry
    ): SvgTransform.AffineMatrix? {
        val gradMat = SvgTransform.composeMatrix(gradientTransform)
        val combined = if (units == SvgGradientUnits.OBJECT_BOUNDING_BOX) {
            val bboxW = localGeom.bboxWidth.coerceAtLeast(1e-4f)
            val bboxH = localGeom.bboxHeight.coerceAtLeast(1e-4f)
            val bboxMat = SvgTransform.AffineMatrix(bboxW, 0f, 0f, bboxH, localGeom.bboxMinX, localGeom.bboxMinY)
            elementCtm.multiply(bboxMat).multiply(gradMat)
        } else {
            elementCtm.multiply(gradMat)
        }
        return combined.inverse()
    }

    private fun buildLinearGradientSampler(
        grad: SvgLinearGradient,
        alphaScale: Float,
        elementCtm: SvgTransform.AffineMatrix,
        localGeom: LocalGeometry
    ): ((Float, Float) -> RgbaColor)? {
        val stops = grad.stops.sortedBy { it.offset }
        if (stops.isEmpty()) return null
        val inv = buildGradientToDeviceInverse(grad.gradientUnits, grad.gradientTransform, elementCtm, localGeom)
            ?: return null
        val dx = grad.x2 - grad.x1
        val dy = grad.y2 - grad.y1
        val len2 = dx * dx + dy * dy
        return { devX, devY ->
            val gx = inv.mapX(devX, devY)
            val gy = inv.mapY(devX, devY)
            val rawT = if (len2 < 1e-8f) 0f else ((gx - grad.x1) * dx + (gy - grad.y1) * dy) / len2
            val t = applySpreadMethod(rawT, grad.spreadMethod)
            sampleStops(stops, t, alphaScale)
        }
    }

    private fun buildRadialGradientSampler(
        grad: SvgRadialGradient,
        alphaScale: Float,
        elementCtm: SvgTransform.AffineMatrix,
        localGeom: LocalGeometry
    ): ((Float, Float) -> RgbaColor)? {
        val stops = grad.stops.sortedBy { it.offset }
        if (stops.isEmpty()) return null
        val inv = buildGradientToDeviceInverse(grad.gradientUnits, grad.gradientTransform, elementCtm, localGeom)
            ?: return null
        val cx = grad.cx
        val cy = grad.cy
        val r = grad.r.coerceAtLeast(1e-5f)
        val fx = grad.fx ?: cx
        val fy = grad.fy ?: cy
        val sameFocus = abs(fx - cx) < 1e-4f && abs(fy - cy) < 1e-4f

        return { devX, devY ->
            val gx = inv.mapX(devX, devY)
            val gy = inv.mapY(devX, devY)
            val rawT = if (sameFocus) {
                hypot((gx - cx).toDouble(), (gy - cy).toDouble()).toFloat() / r
            } else {
                val dx = gx - fx
                val dy = gy - fy
                val fcx = fx - cx
                val fcy = fy - cy
                val aCoeff = dx * dx + dy * dy
                if (aCoeff < 1e-8f) {
                    0f
                } else {
                    val bCoeff = 2f * (dx * fcx + dy * fcy)
                    val cCoeff = fcx * fcx + fcy * fcy - r * r
                    val disc = max(0f, bCoeff * bCoeff - 4f * aCoeff * cCoeff)
                    val u = (-bCoeff + sqrt(disc)) / (2f * aCoeff)
                    if (u > 1e-5f) 1f / u else 1f
                }
            }
            val t = applySpreadMethod(rawT, grad.spreadMethod)
            sampleStops(stops, t, alphaScale)
        }
    }

    private fun applySpreadMethod(t: Float, spreadMethod: String): Float = when (spreadMethod) {
        "repeat" -> t - floor(t)
        "reflect" -> {
            val mod2 = t - 2f * floor(t * 0.5f)
            if (mod2 <= 1f) mod2 else 2f - mod2
        }
        else -> t.coerceIn(0f, 1f)
    }

    private fun sampleStops(stops: List<SvgGradientStop>, t: Float, alphaScale: Float): RgbaColor {
        val first = stops.first()
        if (t <= first.offset || stops.size == 1) {
            return scaleStopColor(first, alphaScale)
        }
        val last = stops.last()
        if (t >= last.offset) {
            return scaleStopColor(last, alphaScale)
        }
        for (i in 0 until stops.size - 1) {
            val s0 = stops[i]
            val s1 = stops[i + 1]
            if (t <= s1.offset) {
                val span = s1.offset - s0.offset
                val localT = if (span <= 1e-6f) 1f else ((t - s0.offset) / span).coerceIn(0f, 1f)
                val a0 = (s0.color.a / 255f) * s0.opacity * alphaScale
                val a1 = (s1.color.a / 255f) * s1.opacity * alphaScale
                val r = (s0.color.r + (s1.color.r - s0.color.r) * localT).roundToInt().coerceIn(0, 255)
                val g = (s0.color.g + (s1.color.g - s0.color.g) * localT).roundToInt().coerceIn(0, 255)
                val b = (s0.color.b + (s1.color.b - s0.color.b) * localT).roundToInt().coerceIn(0, 255)
                val a = ((a0 + (a1 - a0) * localT) * 255f).roundToInt().coerceIn(0, 255)
                return RgbaColor(r, g, b, a)
            }
        }
        return scaleStopColor(last, alphaScale)
    }

    private fun scaleStopColor(stop: SvgGradientStop, alphaScale: Float): RgbaColor {
        val a = ((stop.color.a / 255f) * stop.opacity * alphaScale * 255f).roundToInt().coerceIn(0, 255)
        return RgbaColor(stop.color.r, stop.color.g, stop.color.b, a)
    }

    // -------------------------------------------------------------------------
    // Scanline Anti-Aliased Polygon Rasterization
    // -------------------------------------------------------------------------

    private data class ScanEdge(
        val x0: Float,
        val y0: Float,
        val x1: Float,
        val y1: Float,
        val minY: Float,
        val maxY: Float,
        val dir: Int
    )

    private fun buildScanEdges(subpaths: List<SvgPathParser.Subpath>): Pair<List<ScanEdge>, FloatArray> {
        val edges = ArrayList<ScanEdge>()
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        for (sp in subpaths) {
            val pts = sp.points
            if (pts.size < 2) continue
            for (pt in pts) {
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
            for (i in pts.indices) {
                val p0 = pts[i]
                val p1 = pts[(i + 1) % pts.size]
                if (abs(p0.y - p1.y) < 1e-6f) continue
                if (p0.y < p1.y) {
                    edges.add(ScanEdge(p0.x, p0.y, p1.x, p1.y, p0.y, p1.y, dir = 1))
                } else {
                    edges.add(ScanEdge(p1.x, p1.y, p0.x, p0.y, p1.y, p0.y, dir = -1))
                }
            }
        }
        return edges to floatArrayOf(minX, minY, maxX, maxY)
    }

    private fun rasterizeFillSubpaths(
        subpaths: List<SvgPathParser.Subpath>,
        fillRule: SvgFillRule,
        sampler: (Float, Float) -> RgbaColor,
        targetBytes: ByteArray,
        widthPx: Int,
        heightPx: Int,
        clipMask: FloatArray?
    ) {
        val (edges, bounds) = buildScanEdges(subpaths)
        if (edges.isEmpty()) return
        val startPx = max(0, floor(bounds[0]).toInt())
        val endPx = min(widthPx - 1, floor(bounds[2]).toInt())
        val startPy = max(0, floor(bounds[1]).toInt())
        val endPy = min(heightPx - 1, floor(bounds[3]).toInt())
        if (startPx > endPx || startPy > endPy) return

        val rowWidth = endPx - startPx + 1
        val rowCov = FloatArray(rowWidth)
        val crossX = ArrayList<Float>(16)
        val crossDir = ArrayList<Int>(16)
        val sortOrder = ArrayList<Int>(16)

        for (py in startPy..endPy) {
            rowCov.fill(0f)
            for (sub in 0 until 4) {
                val sy = py + (sub + 0.5f) * 0.25f
                crossX.clear()
                crossDir.clear()
                for (edge in edges) {
                    if (sy >= edge.minY && sy < edge.maxY) {
                        val t = (sy - edge.y0) / (edge.y1 - edge.y0)
                        crossX.add(edge.x0 + t * (edge.x1 - edge.x0))
                        crossDir.add(edge.dir)
                    }
                }
                if (crossX.size < 2) continue
                sortOrder.clear()
                for (i in crossX.indices) sortOrder.add(i)
                sortOrder.sortWith { a, b -> crossX[a].compareTo(crossX[b]) }

                if (fillRule == SvgFillRule.EVENODD) {
                    var i = 0
                    while (i + 1 < sortOrder.size) {
                        val xA = crossX[sortOrder[i]]
                        val xB = crossX[sortOrder[i + 1]]
                        addScanSpanCoverage(xA, xB, startPx, endPx, rowCov, 0.25f)
                        i += 2
                    }
                } else {
                    var winding = 0
                    for (i in 0 until sortOrder.size - 1) {
                        winding += crossDir[sortOrder[i]]
                        if (winding != 0) {
                            val xA = crossX[sortOrder[i]]
                            val xB = crossX[sortOrder[i + 1]]
                            addScanSpanCoverage(xA, xB, startPx, endPx, rowCov, 0.25f)
                        }
                    }
                }
            }

            val rowOffset = py * widthPx
            for (dx in 0 until rowWidth) {
                var cov = rowCov[dx].coerceIn(0f, 1f)
                if (cov <= 0.001f) continue
                val px = startPx + dx
                if (clipMask != null) {
                    cov *= clipMask[rowOffset + px]
                    if (cov <= 0.001f) continue
                }
                val src = sampler(px + 0.5f, py + 0.5f)
                blendPixel(targetBytes, (rowOffset + px) shl 2, src, cov)
            }
        }
    }

    private fun accumulateCoverageMask(
        subpaths: List<SvgPathParser.Subpath>,
        fillRule: SvgFillRule,
        outMask: FloatArray,
        widthPx: Int,
        heightPx: Int
    ) {
        val (edges, bounds) = buildScanEdges(subpaths)
        if (edges.isEmpty()) return
        val startPx = max(0, floor(bounds[0]).toInt())
        val endPx = min(widthPx - 1, floor(bounds[2]).toInt())
        val startPy = max(0, floor(bounds[1]).toInt())
        val endPy = min(heightPx - 1, floor(bounds[3]).toInt())
        if (startPx > endPx || startPy > endPy) return

        val rowWidth = endPx - startPx + 1
        val rowCov = FloatArray(rowWidth)
        val crossX = ArrayList<Float>(16)
        val crossDir = ArrayList<Int>(16)
        val sortOrder = ArrayList<Int>(16)

        for (py in startPy..endPy) {
            rowCov.fill(0f)
            for (sub in 0 until 4) {
                val sy = py + (sub + 0.5f) * 0.25f
                crossX.clear()
                crossDir.clear()
                for (edge in edges) {
                    if (sy >= edge.minY && sy < edge.maxY) {
                        val t = (sy - edge.y0) / (edge.y1 - edge.y0)
                        crossX.add(edge.x0 + t * (edge.x1 - edge.x0))
                        crossDir.add(edge.dir)
                    }
                }
                if (crossX.size < 2) continue
                sortOrder.clear()
                for (i in crossX.indices) sortOrder.add(i)
                sortOrder.sortWith { a, b -> crossX[a].compareTo(crossX[b]) }

                if (fillRule == SvgFillRule.EVENODD) {
                    var i = 0
                    while (i + 1 < sortOrder.size) {
                        addScanSpanCoverage(crossX[sortOrder[i]], crossX[sortOrder[i + 1]], startPx, endPx, rowCov, 0.25f)
                        i += 2
                    }
                } else {
                    var winding = 0
                    for (i in 0 until sortOrder.size - 1) {
                        winding += crossDir[sortOrder[i]]
                        if (winding != 0) {
                            addScanSpanCoverage(crossX[sortOrder[i]], crossX[sortOrder[i + 1]], startPx, endPx, rowCov, 0.25f)
                        }
                    }
                }
            }

            val rowOffset = py * widthPx
            for (dx in 0 until rowWidth) {
                val cov = rowCov[dx].coerceIn(0f, 1f)
                if (cov > 0f) {
                    val idx = rowOffset + startPx + dx
                    if (cov > outMask[idx]) outMask[idx] = cov
                }
            }
        }
    }

    private fun addScanSpanCoverage(
        xStart: Float,
        xEnd: Float,
        startPx: Int,
        endPx: Int,
        rowCov: FloatArray,
        weight: Float
    ) {
        if (xEnd <= xStart) return
        val firstPx = max(startPx, floor(xStart).toInt())
        val lastPx = min(endPx, floor(xEnd).toInt())
        if (firstPx > lastPx) return
        for (px in firstPx..lastPx) {
            val left = max(px.toFloat(), xStart)
            val right = min((px + 1).toFloat(), xEnd)
            val overlap = (right - left).coerceIn(0f, 1f)
            if (overlap > 0f) {
                rowCov[px - startPx] += overlap * weight
            }
        }
    }

    // -------------------------------------------------------------------------
    // Stroke Expansion, Linecaps, Linejoins, & Dash Arrays
    // -------------------------------------------------------------------------

    private fun rasterizeStrokeSubpaths(
        localSubpaths: List<SvgPathParser.Subpath>,
        ctm: SvgTransform.AffineMatrix,
        strokeWidth: Float,
        linecap: SvgStrokeLinecap,
        linejoin: SvgStrokeLinejoin,
        dasharray: List<Float>?,
        dashoffset: Float,
        localTolerance: Float,
        sampler: (Float, Float) -> RgbaColor,
        targetBytes: ByteArray,
        widthPx: Int,
        heightPx: Int,
        clipMask: FloatArray?
    ) {
        if (strokeWidth <= 0f) return
        val halfW = strokeWidth * 0.5f

        val cleanedLocal = ArrayList<SvgPathParser.Subpath>()
        for (sp in localSubpaths) {
            val cleanPts = dedupPoints(sp.points, sp.closed)
            if (cleanPts.isEmpty()) continue
            if (dasharray != null && dasharray.isNotEmpty() && dasharray.any { it > 0f }) {
                cleanedLocal.addAll(applyDashArray(cleanPts, sp.closed, dasharray, dashoffset))
            } else {
                cleanedLocal.add(SvgPathParser.Subpath(cleanPts, sp.closed))
            }
        }
        if (cleanedLocal.isEmpty()) return

        val strokePieces = ArrayList<List<SvgPoint>>()
        for (sp in cleanedLocal) {
            val pts = sp.points
            if (pts.size == 1) {
                if (sp.closed && linecap != SvgStrokeLinecap.BUTT) {
                    addCapPolygon(pts[0], 1f, 0f, halfW, linecap, isStart = false, localTolerance, strokePieces)
                    addCapPolygon(pts[0], -1f, 0f, halfW, linecap, isStart = false, localTolerance, strokePieces)
                }
                continue
            }

            val segCount = if (sp.closed) pts.size else pts.size - 1
            for (i in 0 until segCount) {
                val p0 = pts[i]
                val p1 = pts[(i + 1) % pts.size]
                val dx = p1.x - p0.x
                val dy = p1.y - p0.y
                val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (len < 1e-6f) continue
                val ux = dx / len
                val uy = dy / len
                val nx = -uy * halfW
                val ny = ux * halfW

                // Segment quad
                strokePieces.add(
                    listOf(
                        SvgPoint(p0.x + nx, p0.y + ny),
                        SvgPoint(p1.x + nx, p1.y + ny),
                        SvgPoint(p1.x - nx, p1.y - ny),
                        SvgPoint(p0.x - nx, p0.y - ny)
                    )
                )

                // Caps at open ends
                if (!sp.closed) {
                    if (i == 0) {
                        addCapPolygon(p0, -ux, -uy, halfW, linecap, isStart = true, localTolerance, strokePieces)
                    }
                    if (i == segCount - 1) {
                        addCapPolygon(p1, ux, uy, halfW, linecap, isStart = false, localTolerance, strokePieces)
                    }
                }
            }

            // Joins at vertices
            val joinStart = if (sp.closed) 0 else 1
            val joinEnd = if (sp.closed) pts.size else pts.size - 1
            for (j in joinStart until joinEnd) {
                val prev = pts[(j - 1 + pts.size) % pts.size]
                val curr = pts[j]
                val next = pts[(j + 1) % pts.size]
                addJoinPolygon(prev, curr, next, halfW, linejoin, localTolerance, strokePieces)
            }
        }

        if (strokePieces.isEmpty()) return
        val devicePieces = strokePieces.map { poly -> poly.map { ctm.mapPoint(it) } }
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (poly in devicePieces) {
            for (pt in poly) {
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
        }
        val startPx = max(0, floor(minX).toInt())
        val endPx = min(widthPx - 1, floor(maxX).toInt())
        val startPy = max(0, floor(minY).toInt())
        val endPy = min(heightPx - 1, floor(maxY).toInt())
        if (startPx > endPx || startPy > endPy) return

        val boxW = endPx - startPx + 1
        val boxH = endPy - startPy + 1
        val unionMask = FloatArray(boxW * boxH)

        val rowCov = FloatArray(boxW)
        val crossX = ArrayList<Float>(8)
        for (poly in devicePieces) {
            val (edges, bounds) = buildScanEdges(listOf(SvgPathParser.Subpath(poly, closed = true)))
            if (edges.isEmpty()) continue
            val pStartPx = max(startPx, floor(bounds[0]).toInt())
            val pEndPx = min(endPx, floor(bounds[2]).toInt())
            val pStartPy = max(startPy, floor(bounds[1]).toInt())
            val pEndPy = min(endPy, floor(bounds[3]).toInt())
            if (pStartPx > pEndPx || pStartPy > pEndPy) continue

            for (py in pStartPy..pEndPy) {
                val localRowOffset = (py - startPy) * boxW
                val sliceW = pEndPx - pStartPx + 1
                rowCov.fill(0f, 0, sliceW)
                for (sub in 0 until 4) {
                    val sy = py + (sub + 0.5f) * 0.25f
                    crossX.clear()
                    for (edge in edges) {
                        if (sy >= edge.minY && sy < edge.maxY) {
                            val t = (sy - edge.y0) / (edge.y1 - edge.y0)
                            crossX.add(edge.x0 + t * (edge.x1 - edge.x0))
                        }
                    }
                    if (crossX.size < 2) continue
                    crossX.sort()
                    var k = 0
                    while (k + 1 < crossX.size) {
                        addScanSpanCoverage(crossX[k], crossX[k + 1], pStartPx, pEndPx, rowCov, 0.25f)
                        k += 2
                    }
                }
                for (dx in 0 until sliceW) {
                    val c = rowCov[dx].coerceIn(0f, 1f)
                    val maskIdx = localRowOffset + (pStartPx - startPx) + dx
                    if (c > unionMask[maskIdx]) {
                        unionMask[maskIdx] = c
                    }
                }
            }
        }

        for (dy in 0 until boxH) {
            val py = startPy + dy
            val devRowOff = py * widthPx
            val maskRowOff = dy * boxW
            for (dx in 0 until boxW) {
                var cov = unionMask[maskRowOff + dx]
                if (cov <= 0.001f) continue
                val px = startPx + dx
                if (clipMask != null) {
                    cov *= clipMask[devRowOff + px]
                    if (cov <= 0.001f) continue
                }
                val src = sampler(px + 0.5f, py + 0.5f)
                blendPixel(targetBytes, (devRowOff + px) shl 2, src, cov)
            }
        }
    }

    private fun dedupPoints(points: List<SvgPoint>, closed: Boolean): List<SvgPoint> {
        if (points.isEmpty()) return emptyList()
        val out = ArrayList<SvgPoint>(points.size)
        for (p in points) {
            if (out.isEmpty() || hypot((p.x - out.last().x).toDouble(), (p.y - out.last().y).toDouble()) > 1e-4) {
                out.add(p)
            }
        }
        if (closed && out.size > 1 && hypot((out.first().x - out.last().x).toDouble(), (out.first().y - out.last().y).toDouble()) <= 1e-4) {
            out.removeAt(out.size - 1)
        }
        return out
    }

    private fun applyDashArray(
        points: List<SvgPoint>,
        closed: Boolean,
        rawPattern: List<Float>,
        dashoffset: Float
    ): List<SvgPathParser.Subpath> {
        val pattern = if (rawPattern.size % 2 == 1) rawPattern + rawPattern else rawPattern
        val totalLen = pattern.sum()
        if (totalLen <= 1e-6f) return listOf(SvgPathParser.Subpath(points, closed))

        var normOffset = dashoffset % totalLen
        if (normOffset < 0f) normOffset += totalLen

        var patIdx = 0
        var patRemaining = pattern[0]
        var isDashOn = true
        var skip = normOffset
        while (skip > 1e-6f) {
            if (skip >= patRemaining) {
                skip -= patRemaining
                patIdx = (patIdx + 1) % pattern.size
                isDashOn = (patIdx % 2 == 0)
                patRemaining = pattern[patIdx]
            } else {
                patRemaining -= skip
                skip = 0f
            }
        }

        val result = ArrayList<SvgPathParser.Subpath>()
        var activePts: ArrayList<SvgPoint>? = null
        val segCount = if (closed) points.size else points.size - 1
        if (segCount <= 0) return emptyList()

        for (i in 0 until segCount) {
            val p0 = points[i]
            val p1 = points[(i + 1) % points.size]
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y
            val segLen = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (segLen < 1e-6f) continue

            var segPos = 0f
            while (segPos < segLen - 1e-5f) {
                val step = min(patRemaining, segLen - segPos)
                val t0 = segPos / segLen
                val t1 = (segPos + step) / segLen
                val sx = p0.x + dx * t0
                val sy = p0.y + dy * t0
                val ex = p0.x + dx * t1
                val ey = p0.y + dy * t1

                if (isDashOn) {
                    if (activePts == null) {
                        activePts = ArrayList()
                        activePts.add(SvgPoint(sx, sy))
                    }
                    activePts.add(SvgPoint(ex, ey))
                } else if (activePts != null) {
                    if (activePts.size >= 2) {
                        result.add(SvgPathParser.Subpath(activePts, closed = false))
                    }
                    activePts = null
                }

                segPos += step
                patRemaining -= step
                if (patRemaining <= 1e-5f) {
                    patIdx = (patIdx + 1) % pattern.size
                    isDashOn = (patIdx % 2 == 0)
                    patRemaining = pattern[patIdx]
                    if (!isDashOn && activePts != null) {
                        if (activePts.size >= 2) {
                            result.add(SvgPathParser.Subpath(activePts, closed = false))
                        }
                        activePts = null
                    }
                }
            }
        }
        if (activePts != null && activePts.size >= 2) {
            result.add(SvgPathParser.Subpath(activePts, closed = false))
        }
        return result
    }

    private fun addCapPolygon(
        pt: SvgPoint,
        outDirX: Float,
        outDirY: Float,
        halfW: Float,
        linecap: SvgStrokeLinecap,
        isStart: Boolean,
        tolerance: Float,
        outPolys: MutableList<List<SvgPoint>>
    ) {
        when (linecap) {
            SvgStrokeLinecap.BUTT -> return
            SvgStrokeLinecap.SQUARE -> {
                val nx = -outDirY * halfW
                val ny = outDirX * halfW
                val ex = pt.x + outDirX * halfW
                val ey = pt.y + outDirY * halfW
                outPolys.add(
                    listOf(
                        SvgPoint(pt.x + nx, pt.y + ny),
                        SvgPoint(ex + nx, ey + ny),
                        SvgPoint(ex - nx, ey - ny),
                        SvgPoint(pt.x - nx, pt.y - ny)
                    )
                )
            }
            SvgStrokeLinecap.ROUND -> {
                val steps = max(8, ceil(PI * sqrt(halfW / max(tolerance, 0.01f))).toInt()).coerceAtMost(32)
                val poly = ArrayList<SvgPoint>(steps + 2)
                val nx = -outDirY * halfW
                val ny = outDirX * halfW
                val startAng = kotlin.math.atan2(ny.toDouble(), nx.toDouble())
                val midAng = kotlin.math.atan2(outDirY.toDouble(), outDirX.toDouble())
                val deltaCheck = normalizeAngle(midAng - startAng)
                val dir = if (deltaCheck >= 0.0) 1.0 else -1.0
                for (i in 0..steps) {
                    val a = startAng + dir * (PI * i / steps)
                    poly.add(SvgPoint((pt.x + halfW * cos(a)).toFloat(), (pt.y + halfW * sin(a)).toFloat()))
                }
                outPolys.add(poly)
            }
        }
    }

    private fun addJoinPolygon(
        prev: SvgPoint,
        curr: SvgPoint,
        next: SvgPoint,
        halfW: Float,
        linejoin: SvgStrokeLinejoin,
        tolerance: Float,
        outPolys: MutableList<List<SvgPoint>>
    ) {
        val dx1 = curr.x - prev.x
        val dy1 = curr.y - prev.y
        val len1 = hypot(dx1.toDouble(), dy1.toDouble()).toFloat()
        val dx2 = next.x - curr.x
        val dy2 = next.y - curr.y
        val len2 = hypot(dx2.toDouble(), dy2.toDouble()).toFloat()
        if (len1 < 1e-6f || len2 < 1e-6f) return

        val ux1 = dx1 / len1
        val uy1 = dy1 / len1
        val ux2 = dx2 / len2
        val uy2 = dy2 / len2

        val cross = ux1 * uy2 - uy1 * ux2
        if (abs(cross) < 1e-4f) return

        // Determine outer side of bend
        val sign = if (cross > 0f) -1f else 1f
        val n1x = -uy1 * halfW * sign
        val n1y = ux1 * halfW * sign
        val n2x = -uy2 * halfW * sign
        val n2y = ux2 * halfW * sign

        val p1 = SvgPoint(curr.x + n1x, curr.y + n1y)
        val p2 = SvgPoint(curr.x + n2x, curr.y + n2y)

        when (linejoin) {
            SvgStrokeLinejoin.BEVEL -> {
                outPolys.add(listOf(curr, p1, p2))
            }
            SvgStrokeLinejoin.MITER -> {
                // Line intersection of p1 + t*u1 and p2 + s*u2
                val denom = ux1 * uy2 - uy1 * ux2
                if (abs(denom) < 1e-6f) {
                    outPolys.add(listOf(curr, p1, p2))
                    return
                }
                val t = ((p2.x - p1.x) * uy2 - (p2.y - p1.y) * ux2) / denom
                val mx = p1.x + t * ux1
                val my = p1.y + t * uy1
                val miterLen = hypot((mx - curr.x).toDouble(), (my - curr.y).toDouble()).toFloat()
                if (miterLen <= 4f * halfW) {
                    outPolys.add(listOf(curr, p1, SvgPoint(mx, my), p2))
                } else {
                    outPolys.add(listOf(curr, p1, p2))
                }
            }
            SvgStrokeLinejoin.ROUND -> {
                val a1 = kotlin.math.atan2(n1y.toDouble(), n1x.toDouble())
                val a2 = kotlin.math.atan2(n2y.toDouble(), n2x.toDouble())
                val sweep = normalizeAngle(a2 - a1)
                val steps = max(4, ceil(abs(sweep) * sqrt(halfW / max(tolerance, 0.01f))).toInt()).coerceAtMost(24)
                val poly = ArrayList<SvgPoint>(steps + 2)
                poly.add(curr)
                for (i in 0..steps) {
                    val a = a1 + sweep * (i.toDouble() / steps)
                    poly.add(SvgPoint((curr.x + halfW * cos(a)).toFloat(), (curr.y + halfW * sin(a)).toFloat()))
                }
                outPolys.add(poly)
            }
        }
    }

    private fun normalizeAngle(a: Double): Double {
        var res = a
        while (res > PI) res -= 2.0 * PI
        while (res < -PI) res += 2.0 * PI
        return res
    }

    // -------------------------------------------------------------------------
    // Alpha Compositing
    // -------------------------------------------------------------------------

    private fun blendPixel(bytes: ByteArray, offset: Int, src: RgbaColor, coverage: Float) {
        val sa = ((src.a / 255f) * coverage).coerceIn(0f, 1f)
        if (sa <= 0.001f) return
        if (sa >= 0.999f) {
            bytes[offset] = src.r.toByte()
            bytes[offset + 1] = src.g.toByte()
            bytes[offset + 2] = src.b.toByte()
            bytes[offset + 3] = 255.toByte()
            return
        }
        val da = (bytes[offset + 3].toInt() and 0xFF) / 255f
        if (da <= 0.001f) {
            bytes[offset] = src.r.toByte()
            bytes[offset + 1] = src.g.toByte()
            bytes[offset + 2] = src.b.toByte()
            bytes[offset + 3] = (sa * 255f).roundToInt().coerceIn(0, 255).toByte()
            return
        }
        val invSa = 1f - sa
        val outA = sa + da * invSa
        if (outA <= 0.001f) return
        val dr = bytes[offset].toInt() and 0xFF
        val dg = bytes[offset + 1].toInt() and 0xFF
        val db = bytes[offset + 2].toInt() and 0xFF
        val outR = ((src.r * sa + dr * da * invSa) / outA).roundToInt().coerceIn(0, 255)
        val outG = ((src.g * sa + dg * da * invSa) / outA).roundToInt().coerceIn(0, 255)
        val outB = ((src.b * sa + db * da * invSa) / outA).roundToInt().coerceIn(0, 255)
        bytes[offset] = outR.toByte()
        bytes[offset + 1] = outG.toByte()
        bytes[offset + 2] = outB.toByte()
        bytes[offset + 3] = (outA * 255f).roundToInt().coerceIn(0, 255).toByte()
    }

    private fun compositeBufferOver(
        srcBytes: ByteArray,
        dstBytes: ByteArray,
        widthPx: Int,
        heightPx: Int,
        groupOpacity: Float
    ) {
        val op = groupOpacity.coerceIn(0f, 1f)
        if (op <= 0.001f) return
        val total = widthPx * heightPx
        var idx = 0
        for (i in 0 until total) {
            val saByte = srcBytes[idx + 3].toInt() and 0xFF
            if (saByte > 0) {
                val srcColor = RgbaColor(
                    r = srcBytes[idx].toInt() and 0xFF,
                    g = srcBytes[idx + 1].toInt() and 0xFF,
                    b = srcBytes[idx + 2].toInt() and 0xFF,
                    a = saByte
                )
                blendPixel(dstBytes, idx, srcColor, op)
            }
            idx += 4
        }
    }

    // -------------------------------------------------------------------------
    // Inverse-Transform Hit Testing
    // -------------------------------------------------------------------------

    private fun hitTestChildrenReverse(
        svg: Svg,
        elements: List<SvgElement>,
        parentMatrix: SvgTransform.AffineMatrix,
        inherited: InheritedStyle,
        vx: Float,
        vy: Float,
        visitedUseIds: MutableSet<String>
    ): SvgElement? {
        for (i in elements.indices.reversed()) {
            val el = elements[i]
            val hit = hitTestElement(svg, el, parentMatrix, inherited, vx, vy, visitedUseIds)
            if (hit != null) return hit
        }
        return null
    }

    private fun hitTestElement(
        svg: Svg,
        element: SvgElement,
        parentMatrix: SvgTransform.AffineMatrix,
        inherited: InheritedStyle,
        vx: Float,
        vy: Float,
        visitedUseIds: MutableSet<String>
    ): SvgElement? {
        if (!element.visible) return null
        if (element is SvgDefs || element is SvgClipPath || element is SvgLinearGradient || element is SvgRadialGradient) {
            return null
        }

        val ctm = parentMatrix.multiply(element.directTransformMatrix())
        val style = inherited.resolveWith(element)

        if (element is SvgGroup) {
            if (!hitTestClipPath(svg, element.clipPathId, ctm, null, vx, vy, visitedUseIds)) {
                return null
            }
            return hitTestChildrenReverse(svg, element.children, ctm, style, vx, vy, visitedUseIds)
        }

        if (element is SvgUse) {
            val targetId = element.targetId
            if (targetId.isEmpty() || targetId in visitedUseIds) return null
            val referenced = svg.findById(targetId) ?: return null
            val useMatrix = if (element.x != 0f || element.y != 0f) {
                ctm.multiply(SvgTransform.AffineMatrix(1f, 0f, 0f, 1f, element.x, element.y))
            } else ctm
            if (!hitTestClipPath(svg, element.clipPathId, useMatrix, null, vx, vy, visitedUseIds)) {
                return null
            }
            visitedUseIds.add(targetId)
            return try {
                if (hitTestElement(svg, referenced, useMatrix, style, vx, vy, visitedUseIds) != null) element else null
            } finally {
                visitedUseIds.remove(targetId)
            }
        }

        val inv = ctm.inverse() ?: return null
        val lx = inv.mapX(vx, vy)
        val ly = inv.mapY(vx, vy)

        if (element is SvgText) {
            if (!hitTestClipPath(svg, element.clipPathId, ctm, null, vx, vy, visitedUseIds)) {
                return null
            }
            val w = SvgBuiltinFont.measureTextWidth(element.text, style.fontSize)
            val startX = when (style.textAnchor.trim().lowercase()) {
                "middle" -> element.x - w * 0.5f
                "end" -> element.x - w
                else -> element.x
            }
            val hit = lx in startX..(startX + w) && ly in (element.y - style.fontSize * 0.85f)..(element.y + style.fontSize * 0.25f)
            return if (hit) element else null
        }

        val tol = computeLocalTolerance(ctm)
        val geom = extractLocalGeometry(element, tol) ?: return null
        if (!hitTestClipPath(svg, element.clipPathId, ctm, geom, vx, vy, visitedUseIds)) {
            return null
        }

        val hasFill = geom.fillable && style.fill != SvgPaint.None && style.fillOpacity > 0.001f
        val hasStroke = style.stroke != SvgPaint.None && style.strokeWidth > 0f && style.strokeOpacity > 0.001f
        val strokeHalfW = if (hasStroke) style.strokeWidth * 0.5f else 0f

        val insideFill = if (hasFill) {
            when (element) {
                is SvgRect -> isPointInRect(lx, ly, element.x, element.y, element.width, element.height, element.rx, element.ry)
                is SvgCircle -> {
                    val dx = lx - element.cx
                    val dy = ly - element.cy
                    dx * dx + dy * dy <= element.r * element.r
                }
                is SvgEllipse -> {
                    val nx = (lx - element.cx) / element.rx
                    val ny = (ly - element.cy) / element.ry
                    nx * nx + ny * ny <= 1f
                }
                else -> isPointInSubpaths(lx, ly, geom.subpaths, style.fillRule)
            }
        } else {
            false
        }
        if (insideFill) return element

        if (hasStroke || element is SvgLine) {
            val hitRadius = max(strokeHalfW, if (element is SvgLine) 1.5f else 0f)
            if (hitRadius > 0f && isPointNearSubpathStrokes(lx, ly, geom.subpaths, hitRadius)) {
                return element
            }
        }
        return null
    }

    private fun hitTestClipPath(
        svg: Svg,
        clipPathId: String?,
        elementCtm: SvgTransform.AffineMatrix,
        localBbox: LocalGeometry?,
        vx: Float,
        vy: Float,
        visitedUseIds: MutableSet<String>
    ): Boolean {
        if (clipPathId.isNullOrBlank()) return true
        val clipNode = svg.findById(clipPathId) as? SvgClipPath ?: return true
        val baseCtm = if (clipNode.clipPathUnits == SvgGradientUnits.OBJECT_BOUNDING_BOX && localBbox != null) {
            val bboxMatrix = SvgTransform.AffineMatrix(
                a = localBbox.bboxWidth.coerceAtLeast(1e-4f),
                b = 0f,
                c = 0f,
                d = localBbox.bboxHeight.coerceAtLeast(1e-4f),
                e = localBbox.bboxMinX,
                f = localBbox.bboxMinY
            )
            elementCtm.multiply(clipNode.directTransformMatrix()).multiply(bboxMatrix)
        } else {
            elementCtm.multiply(clipNode.directTransformMatrix())
        }
        for (child in clipNode.children) {
            if (isPointInClipShape(svg, child, baseCtm, vx, vy, visitedUseIds)) {
                return true
            }
        }
        return false
    }

    private fun isPointInClipShape(
        svg: Svg,
        element: SvgElement,
        parentMatrix: SvgTransform.AffineMatrix,
        vx: Float,
        vy: Float,
        visitedUseIds: MutableSet<String>
    ): Boolean {
        if (!element.visible) return false
        val ctm = parentMatrix.multiply(element.directTransformMatrix())
        when (element) {
            is SvgGroup -> return element.children.any { isPointInClipShape(svg, it, ctm, vx, vy, visitedUseIds) }
            is SvgUse -> {
                val targetId = element.targetId
                if (targetId.isEmpty() || targetId in visitedUseIds) return false
                val target = svg.findById(targetId) ?: return false
                visitedUseIds.add(targetId)
                return try {
                    val m = if (element.x != 0f || element.y != 0f) {
                        ctm.multiply(SvgTransform.AffineMatrix(1f, 0f, 0f, 1f, element.x, element.y))
                    } else ctm
                    isPointInClipShape(svg, target, m, vx, vy, visitedUseIds)
                } finally {
                    visitedUseIds.remove(targetId)
                }
            }
            else -> {
                val inv = ctm.inverse() ?: return false
                val lx = inv.mapX(vx, vy)
                val ly = inv.mapY(vx, vy)
                val tol = computeLocalTolerance(ctm)
                val geom = extractLocalGeometry(element, tol) ?: return false
                return isPointInSubpaths(lx, ly, geom.subpaths, element.fillRule)
            }
        }
    }

    private fun isPointInRect(
        px: Float,
        py: Float,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        rxIn: Float,
        ryIn: Float
    ): Boolean {
        if (px < x || px > x + w || py < y || py > y + h) return false
        val rx = min(rxIn.coerceAtLeast(0f), w * 0.5f)
        val ry = min(ryIn.coerceAtLeast(0f), h * 0.5f)
        if (rx <= 1e-4f || ry <= 1e-4f) return true
        val cx = when {
            px < x + rx -> x + rx
            px > x + w - rx -> x + w - rx
            else -> return true
        }
        val cy = when {
            py < y + ry -> y + ry
            py > y + h - ry -> y + h - ry
            else -> return true
        }
        val nx = (px - cx) / rx
        val ny = (py - cy) / ry
        return nx * nx + ny * ny <= 1f
    }

    private fun isPointInSubpaths(
        px: Float,
        py: Float,
        subpaths: List<SvgPathParser.Subpath>,
        fillRule: SvgFillRule
    ): Boolean {
        var winding = 0
        var crossings = 0
        for (sp in subpaths) {
            val pts = sp.points
            if (pts.size < 2) continue
            for (i in pts.indices) {
                val p0 = pts[i]
                val p1 = pts[(i + 1) % pts.size]
                if ((p0.y <= py && p1.y > py) || (p1.y <= py && p0.y > py)) {
                    val t = (py - p0.y) / (p1.y - p0.y)
                    val intersectX = p0.x + t * (p1.x - p0.x)
                    if (intersectX > px) {
                        crossings++
                    }
                    if (p0.y < p1.y) {
                        if (intersectX > px) winding++
                    } else {
                        if (intersectX > px) winding--
                    }
                }
            }
        }
        return if (fillRule == SvgFillRule.EVENODD) {
            (crossings and 1) == 1
        } else {
            winding != 0
        }
    }

    private fun isPointNearSubpathStrokes(
        px: Float,
        py: Float,
        subpaths: List<SvgPathParser.Subpath>,
        radius: Float
    ): Boolean {
        val rSq = radius * radius
        for (sp in subpaths) {
            val pts = sp.points
            if (pts.size < 2) continue
            val segCount = if (sp.closed) pts.size else pts.size - 1
            for (i in 0 until segCount) {
                val p0 = pts[i]
                val p1 = pts[(i + 1) % pts.size]
                if (distSqPointToSegment(px, py, p0.x, p0.y, p1.x, p1.y) <= rSq) {
                    return true
                }
            }
        }
        return false
    }

    private fun distSqPointToSegment(
        px: Float,
        py: Float,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float
    ): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-8f) {
            val ex = px - x0
            val ey = py - y0
            return ex * ex + ey * ey
        }
        val t = (((px - x0) * dx + (py - y0) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = x0 + t * dx
        val projY = y0 + t * dy
        val ex = px - projX
        val ey = py - projY
        return ex * ex + ey * ey
    }
}
