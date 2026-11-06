package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Web-target (`:extensions:imageprocessing:web`) implementation of [ImageBuffer2DContext]
 * operating directly on [WebImageBuffer] and its underlying browser 2D surface via
 * [WebImageBuffer2DBridge].
 *
 * - In browser environments (`[WebImageBuffer2DBridge.isBrowserCanvasSupported] == true` and no
 *   custom CPU clip region active), delegates `clearRect`, `fillRect`, `strokeRect`, `fillText`,
 *   `strokeText`, and `drawImage` directly to the browser's hardware-accelerated HTML5 `<canvas>`
 *   `CanvasRenderingContext2D` without running CPU rasterizer loops.
 * - In headless Node/Wasm unit tests (or when a custom clip region is active), executes the
 *   deterministic CPU rasterizer while recording `data-*` attributes on [WebImageBuffer.surfaceElement].
 */
class WebImageBuffer2DContext(
    override val imageBuffer: WebImageBuffer,
    initialAntialias: Boolean = true
) : ImageBuffer2DContext {

    override var antialias: Boolean = initialAntialias

    private var currentMatrix: ImageBufferMatrix2D = ImageBufferMatrix2D.IDENTITY
    override var globalAlpha: Float = 1f
    override var globalCompositeOperation: ImageBufferCompositeOperation = ImageBufferCompositeOperation.SOURCE_OVER
    override var imageSmoothingEnabled: Boolean = true
    override var imageSmoothingQuality: ImageBufferImageSmoothingQuality = ImageBufferImageSmoothingQuality.HIGH
    override var filter: String = "none"

    override var fillStyle: ImageBufferPaintStyle = ImageBufferPaintStyle.solid(RgbaColor.BLACK)
    override var strokeStyle: ImageBufferPaintStyle = ImageBufferPaintStyle.solid(RgbaColor.BLACK)

    override var shadowBlur: Float = 0f
    override var shadowColor: RgbaColor = RgbaColor.TRANSPARENT
    override var shadowOffsetX: Float = 0f
    override var shadowOffsetY: Float = 0f

    override var lineWidth: Float = 1f
    override var lineCap: ImageBufferLineCap = ImageBufferLineCap.BUTT
    override var lineJoin: ImageBufferLineJoin = ImageBufferLineJoin.MITER
    override var miterLimit: Float = 10f
    override var lineDashOffset: Float = 0f
    private var lineDashSegments: FloatArray = FloatArray(0)

    override var fontFamily: String = "sans-serif"
    override var fontSizePx: Float = 14f
    override var fontWeight: Int = 400
    override var fontStyle: ImageBufferFontStyle = ImageBufferFontStyle.NORMAL
    override var textAlign: ImageBufferTextAlign = ImageBufferTextAlign.START
    override var textBaseline: ImageBufferTextBaseline = ImageBufferTextBaseline.ALPHABETIC
    override var direction: ImageBufferTextDirection = ImageBufferTextDirection.INHERIT
    override var letterSpacingPx: Float = 0f
    override var wordSpacingPx: Float = 0f
    override var fontKerning: ImageBufferFontKerning = ImageBufferFontKerning.AUTO
    override var fontStretch: ImageBufferFontStretch = ImageBufferFontStretch.NORMAL
    override var fontVariantCaps: ImageBufferFontVariantCaps = ImageBufferFontVariantCaps.NORMAL
    override var textRendering: ImageBufferTextRendering = ImageBufferTextRendering.AUTO

    internal var clipRect: IntArray? = null
        private set

    private val currentPath = ImageBufferPath2D()
    private val stateStack = ArrayDeque<Array<Any?>>()

    private fun clipMinX(): Int = clipRect?.get(0)?.coerceIn(0, width) ?: 0
    private fun clipMinY(): Int = clipRect?.get(1)?.coerceIn(0, height) ?: 0
    private fun clipMaxX(): Int = clipRect?.get(2)?.coerceIn(0, width) ?: width
    private fun clipMaxY(): Int = clipRect?.get(3)?.coerceIn(0, height) ?: height

    private fun currentTransformArray(): FloatArray =
        floatArrayOf(
            currentMatrix.a,
            currentMatrix.b,
            currentMatrix.c,
            currentMatrix.d,
            currentMatrix.e,
            currentMatrix.f
        )

    private fun formatCssNumber(v: Float): String {
        val iv = v.toInt()
        return if (v == iv.toFloat()) iv.toString() else v.toString()
    }

    private fun currentFontCss(): String =
        "${fontStyle.webValue} $fontWeight ${formatCssNumber(fontSizePx)}px $fontFamily"

    private fun colorToCss(c: RgbaColor): String {
        val a = c.a.coerceIn(0, 255)
        return when (a) {
            255 -> "rgba(${c.r},${c.g},${c.b},1)"
            0 -> "rgba(${c.r},${c.g},${c.b},0)"
            else -> "rgba(${c.r},${c.g},${c.b},${a / 255f})"
        }
    }

    private fun mapTextAlignCss(align: ImageBufferTextAlign): String =
        when (align) {
            ImageBufferTextAlign.START -> "start"
            ImageBufferTextAlign.END -> "end"
            ImageBufferTextAlign.LEFT -> "left"
            ImageBufferTextAlign.RIGHT -> "right"
            ImageBufferTextAlign.CENTER -> "center"
        }

    private fun mapTextBaselineCss(baseline: ImageBufferTextBaseline): String =
        when (baseline) {
            ImageBufferTextBaseline.TOP -> "top"
            ImageBufferTextBaseline.HANGING -> "hanging"
            ImageBufferTextBaseline.MIDDLE -> "middle"
            ImageBufferTextBaseline.ALPHABETIC -> "alphabetic"
            ImageBufferTextBaseline.IDEOGRAPHIC -> "ideographic"
            ImageBufferTextBaseline.BOTTOM -> "bottom"
        }

    private fun mapLineCapCss(cap: ImageBufferLineCap): String =
        when (cap) {
            ImageBufferLineCap.BUTT -> "butt"
            ImageBufferLineCap.ROUND -> "round"
            ImageBufferLineCap.SQUARE -> "square"
        }

    private fun mapLineJoinCss(join: ImageBufferLineJoin): String =
        when (join) {
            ImageBufferLineJoin.MITER -> "miter"
            ImageBufferLineJoin.ROUND -> "round"
            ImageBufferLineJoin.BEVEL -> "bevel"
        }

    private fun mapCompositeOperationCss(op: ImageBufferCompositeOperation): String =
        when (op) {
            ImageBufferCompositeOperation.SOURCE_OVER -> "source-over"
            ImageBufferCompositeOperation.DESTINATION_OVER -> "destination-over"
            ImageBufferCompositeOperation.SOURCE_IN -> "source-in"
            ImageBufferCompositeOperation.DESTINATION_IN -> "destination-in"
            ImageBufferCompositeOperation.SOURCE_OUT -> "source-out"
            ImageBufferCompositeOperation.DESTINATION_OUT -> "destination-out"
            ImageBufferCompositeOperation.SOURCE_ATOP -> "source-atop"
            ImageBufferCompositeOperation.DESTINATION_ATOP -> "destination-atop"
            ImageBufferCompositeOperation.LIGHTER -> "lighter"
            ImageBufferCompositeOperation.COPY -> "copy"
            ImageBufferCompositeOperation.XOR -> "xor"
            ImageBufferCompositeOperation.MULTIPLY -> "multiply"
            ImageBufferCompositeOperation.SCREEN -> "screen"
            else -> op.webValue
        }

    override fun reset() {
        currentMatrix = ImageBufferMatrix2D.IDENTITY
        globalAlpha = 1f
        globalCompositeOperation = ImageBufferCompositeOperation.SOURCE_OVER
        imageSmoothingEnabled = true
        imageSmoothingQuality = ImageBufferImageSmoothingQuality.HIGH
        filter = "none"
        fillStyle = ImageBufferPaintStyle.solid(RgbaColor.BLACK)
        strokeStyle = ImageBufferPaintStyle.solid(RgbaColor.BLACK)
        clearShadow()
        lineWidth = 1f
        lineCap = ImageBufferLineCap.BUTT
        lineJoin = ImageBufferLineJoin.MITER
        miterLimit = 10f
        lineDashOffset = 0f
        lineDashSegments = FloatArray(0)
        fontFamily = "sans-serif"
        fontSizePx = 14f
        fontWeight = 400
        fontStyle = ImageBufferFontStyle.NORMAL
        textAlign = ImageBufferTextAlign.START
        textBaseline = ImageBufferTextBaseline.ALPHABETIC
        direction = ImageBufferTextDirection.INHERIT
        letterSpacingPx = 0f
        wordSpacingPx = 0f
        fontKerning = ImageBufferFontKerning.AUTO
        fontStretch = ImageBufferFontStretch.NORMAL
        fontVariantCaps = ImageBufferFontVariantCaps.NORMAL
        textRendering = ImageBufferTextRendering.AUTO
        clipRect = null
        currentPath.reset()
        stateStack.clear()
        clearRect(0f, 0f, width.toFloat(), height.toFloat())
    }

    override fun save() {
        stateStack.addLast(
            arrayOf(
                currentMatrix.copy(),
                globalAlpha,
                globalCompositeOperation,
                imageSmoothingEnabled,
                imageSmoothingQuality,
                filter,
                fillStyle,
                strokeStyle,
                shadowBlur,
                shadowColor,
                shadowOffsetX,
                shadowOffsetY,
                lineWidth,
                lineCap,
                lineJoin,
                miterLimit,
                lineDashOffset,
                lineDashSegments.copyOf(),
                fontFamily,
                fontSizePx,
                fontWeight,
                fontStyle,
                textAlign,
                textBaseline,
                direction,
                letterSpacingPx,
                wordSpacingPx,
                fontKerning,
                fontStretch,
                fontVariantCaps,
                textRendering,
                clipRect?.copyOf()
            )
        )
    }

    override fun restore() {
        val s = stateStack.removeLastOrNull() ?: return
        currentMatrix = s[0] as ImageBufferMatrix2D
        globalAlpha = s[1] as Float
        globalCompositeOperation = s[2] as ImageBufferCompositeOperation
        imageSmoothingEnabled = s[3] as Boolean
        imageSmoothingQuality = s[4] as ImageBufferImageSmoothingQuality
        filter = s[5] as String
        fillStyle = s[6] as ImageBufferPaintStyle
        strokeStyle = s[7] as ImageBufferPaintStyle
        shadowBlur = s[8] as Float
        shadowColor = s[9] as RgbaColor
        shadowOffsetX = s[10] as Float
        shadowOffsetY = s[11] as Float
        lineWidth = s[12] as Float
        lineCap = s[13] as ImageBufferLineCap
        lineJoin = s[14] as ImageBufferLineJoin
        miterLimit = s[15] as Float
        lineDashOffset = s[16] as Float
        lineDashSegments = s[17] as FloatArray
        fontFamily = s[18] as String
        fontSizePx = s[19] as Float
        fontWeight = s[20] as Int
        fontStyle = s[21] as ImageBufferFontStyle
        textAlign = s[22] as ImageBufferTextAlign
        textBaseline = s[23] as ImageBufferTextBaseline
        direction = s[24] as ImageBufferTextDirection
        letterSpacingPx = s[25] as Float
        wordSpacingPx = s[26] as Float
        fontKerning = s[27] as ImageBufferFontKerning
        fontStretch = s[28] as ImageBufferFontStretch
        fontVariantCaps = s[29] as ImageBufferFontVariantCaps
        textRendering = s[30] as ImageBufferTextRendering
        clipRect = s[31] as? IntArray
    }

    override fun scale(sx: Float, sy: Float) {
        currentMatrix = currentMatrix.scale(sx, sy)
    }

    override fun rotate(angleRadians: Float) {
        currentMatrix = currentMatrix.rotate(angleRadians)
    }

    override fun translate(dx: Float, dy: Float) {
        currentMatrix = currentMatrix.translate(dx, dy)
    }

    override fun transform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        currentMatrix = currentMatrix.multiply(ImageBufferMatrix2D(a, b, c, d, e, f))
    }

    override fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        currentMatrix = ImageBufferMatrix2D(a, b, c, d, e, f)
    }

    override fun getTransform(): ImageBufferMatrix2D = currentMatrix.copy()

    override fun resetTransform() {
        currentMatrix = ImageBufferMatrix2D.IDENTITY
    }

    override fun setLineDash(segments: FloatArray, offset: Float) {
        lineDashSegments = segments.copyOf()
        lineDashOffset = offset
    }

    override fun getLineDash(): FloatArray = lineDashSegments.copyOf()

    override fun clearRect(x: Float, y: Float, width: Float, height: Float) {
        val transform = currentTransformArray()
        if (WebImageBuffer2DBridge.isBrowserCanvasSupported && clipRect == null) {
            imageBuffer.syncCanvasFromCpuIfNeeded()
            WebImageBuffer2DBridge.clearRect(
                surfaceElement = imageBuffer.surfaceElement,
                x = x,
                y = y,
                width = width,
                height = height,
                transform = transform
            )
            imageBuffer.markCanvasModified()
            return
        }

        WebImageBuffer2DBridge.clearRect(
            surfaceElement = imageBuffer.surfaceElement,
            x = x,
            y = y,
            width = width,
            height = height,
            transform = transform
        )
        val (tx0, ty0) = currentMatrix.transformPoint(x, y)
        val (tx1, ty1) = currentMatrix.transformPoint(x + width, y + height)
        val sx = floor(min(tx0, tx1)).toInt().coerceIn(clipMinX(), clipMaxX())
        val sy = floor(min(ty0, ty1)).toInt().coerceIn(clipMinY(), clipMaxY())
        val ex = ceil(max(tx0, tx1)).toInt().coerceIn(clipMinX(), clipMaxX())
        val ey = ceil(max(ty0, ty1)).toInt().coerceIn(clipMinY(), clipMaxY())
        for (py in sy until ey) {
            for (px in sx until ex) {
                imageBuffer.setPixel(px, py, RgbaColor.TRANSPARENT)
            }
        }
    }

    override fun fillRect(x: Float, y: Float, width: Float, height: Float) {
        val fillCss = colorToCss(fillStyle.color)
        val compositeCss = mapCompositeOperationCss(globalCompositeOperation)
        val transform = currentTransformArray()
        if (WebImageBuffer2DBridge.isBrowserCanvasSupported && clipRect == null) {
            imageBuffer.syncCanvasFromCpuIfNeeded()
            WebImageBuffer2DBridge.fillRect(
                surfaceElement = imageBuffer.surfaceElement,
                x = x,
                y = y,
                width = width,
                height = height,
                fillCss = fillCss,
                globalAlpha = globalAlpha,
                compositeOp = compositeCss,
                transform = transform
            )
            imageBuffer.markCanvasModified()
            return
        }

        WebImageBuffer2DBridge.fillRect(
            surfaceElement = imageBuffer.surfaceElement,
            x = x,
            y = y,
            width = width,
            height = height,
            fillCss = fillCss,
            globalAlpha = globalAlpha,
            compositeOp = compositeCss,
            transform = transform
        )
        val p = ImageBufferPath2D().apply { rect(x, y, width, height) }
        fill(p, ImageBufferFillRule.NONZERO)
    }

    override fun strokeRect(x: Float, y: Float, width: Float, height: Float) {
        val strokeCss = colorToCss(strokeStyle.color)
        val capCss = mapLineCapCss(lineCap)
        val joinCss = mapLineJoinCss(lineJoin)
        val compositeCss = mapCompositeOperationCss(globalCompositeOperation)
        val transform = currentTransformArray()
        if (WebImageBuffer2DBridge.isBrowserCanvasSupported && clipRect == null) {
            imageBuffer.syncCanvasFromCpuIfNeeded()
            WebImageBuffer2DBridge.strokeRect(
                surfaceElement = imageBuffer.surfaceElement,
                x = x,
                y = y,
                width = width,
                height = height,
                strokeCss = strokeCss,
                lineWidth = lineWidth,
                lineCap = capCss,
                lineJoin = joinCss,
                miterLimit = miterLimit,
                globalAlpha = globalAlpha,
                compositeOp = compositeCss,
                transform = transform
            )
            imageBuffer.markCanvasModified()
            return
        }

        WebImageBuffer2DBridge.strokeRect(
            surfaceElement = imageBuffer.surfaceElement,
            x = x,
            y = y,
            width = width,
            height = height,
            strokeCss = strokeCss,
            lineWidth = lineWidth,
            lineCap = capCss,
            lineJoin = joinCss,
            miterLimit = miterLimit,
            globalAlpha = globalAlpha,
            compositeOp = compositeCss,
            transform = transform
        )
        val p = ImageBufferPath2D().apply { rect(x, y, width, height) }
        stroke(p)
    }

    override fun beginPath() = currentPath.reset()
    override fun closePath() = currentPath.closePath()
    override fun moveTo(x: Float, y: Float) = currentPath.moveTo(x, y)
    override fun lineTo(x: Float, y: Float) = currentPath.lineTo(x, y)
    override fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float) =
        currentPath.quadraticCurveTo(cpx, cpy, x, y)
    override fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) =
        currentPath.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, x, y)
    override fun arcTo(x1: Float, y1: Float, x2: Float, y2: Float, radius: Float) =
        currentPath.arcTo(x1, y1, x2, y2, radius)
    override fun arc(
        x: Float,
        y: Float,
        radius: Float,
        startAngle: Float,
        endAngle: Float,
        counterClockwise: Boolean
    ) = currentPath.arc(x, y, radius, startAngle, endAngle, counterClockwise)

    override fun ellipse(
        x: Float,
        y: Float,
        radiusX: Float,
        radiusY: Float,
        rotation: Float,
        startAngle: Float,
        endAngle: Float,
        counterClockwise: Boolean
    ) = currentPath.ellipse(x, y, radiusX, radiusY, rotation, startAngle, endAngle, counterClockwise)

    override fun rect(x: Float, y: Float, width: Float, height: Float) =
        currentPath.rect(x, y, width, height)

    override fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) =
        currentPath.roundRect(x, y, width, height, radius)

    override fun roundRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        topLeftRadius: Float,
        topRightRadius: Float,
        bottomRightRadius: Float,
        bottomLeftRadius: Float
    ) = currentPath.roundRect(
        x,
        y,
        width,
        height,
        topLeftRadius,
        topRightRadius,
        bottomRightRadius,
        bottomLeftRadius
    )

    private fun transformPolylines(path: ImageBufferPath2D, matrix: ImageBufferMatrix2D): List<FloatArray> {
        val raw = path.toPolylines()
        if (matrix.isIdentity) return raw
        return raw.map { poly ->
            val out = FloatArray(poly.size)
            var i = 0
            while (i + 1 < poly.size) {
                val x = poly[i]
                val y = poly[i + 1]
                out[i] = matrix.transformX(x, y)
                out[i + 1] = matrix.transformY(x, y)
                i += 2
            }
            out
        }
    }

    override fun fill(fillRule: ImageBufferFillRule) = fill(currentPath, fillRule)

    override fun fill(path: ImageBufferPath2D, fillRule: ImageBufferFillRule) {
        val subpaths = transformPolylines(path, currentMatrix)
        if (subpaths.isEmpty()) return
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (poly in subpaths) {
            var i = 0
            while (i + 1 < poly.size) {
                val px = poly[i]
                val py = poly[i + 1]
                if (px < minX) minX = px
                if (py < minY) minY = py
                if (px > maxX) maxX = px
                if (py > maxY) maxY = py
                i += 2
            }
        }
        val sx = floor(minX).toInt().coerceIn(clipMinX(), clipMaxX())
        val sy = floor(minY).toInt().coerceIn(clipMinY(), clipMaxY())
        val ex = ceil(maxX).toInt().coerceIn(clipMinX(), clipMaxX())
        val ey = ceil(maxY).toInt().coerceIn(clipMinY(), clipMaxY())
        val inv = currentMatrix.invert() ?: ImageBufferMatrix2D.IDENTITY
        for (py in sy until ey) {
            for (px in sx until ex) {
                val (lx, ly) = inv.transformPoint(px + 0.5f, py + 0.5f)
                if (path.containsPoint(lx, ly, fillRule)) {
                    val src = fillStyle.sampleAt(lx, ly, imageSmoothingEnabled)
                    imageBuffer.setPixel(px, py, globalCompositeOperation.blend(src, imageBuffer.getPixel(px, py), globalAlpha))
                }
            }
        }
    }

    override fun stroke() = stroke(currentPath)

    override fun stroke(path: ImageBufferPath2D) {
        val subpaths = transformPolylines(path, currentMatrix)
        if (subpaths.isEmpty()) return
        val halfW = max(0.5f, lineWidth * 0.5f)
        val inv = currentMatrix.invert() ?: ImageBufferMatrix2D.IDENTITY
        for (poly in subpaths) {
            var i = 0
            while (i + 3 < poly.size) {
                val x0 = poly[i]
                val y0 = poly[i + 1]
                val x1 = poly[i + 2]
                val y1 = poly[i + 3]
                val steps = max(1, ceil(max(abs(x1 - x0), abs(y1 - y0))).toInt())
                for (s in 0..steps) {
                    val t = s.toFloat() / steps.toFloat()
                    val cx = (x0 + (x1 - x0) * t).roundToInt()
                    val cy = (y0 + (y1 - y0) * t).roundToInt()
                    val r = ceil(halfW).toInt()
                    for (dy in -r..r) {
                        for (dx in -r..r) {
                            val px = cx + dx
                            val py = cy + dy
                            if (px in clipMinX() until clipMaxX() && py in clipMinY() until clipMaxY()) {
                                val (lx, ly) = inv.transformPoint(px + 0.5f, py + 0.5f)
                                val src = strokeStyle.sampleAt(lx, ly, imageSmoothingEnabled)
                                imageBuffer.setPixel(px, py, globalCompositeOperation.blend(src, imageBuffer.getPixel(px, py), globalAlpha))
                            }
                        }
                    }
                }
                i += 2
            }
        }
    }

    override fun clip(fillRule: ImageBufferFillRule) = clip(currentPath, fillRule)

    override fun clip(path: ImageBufferPath2D, fillRule: ImageBufferFillRule) {
        val subpaths = transformPolylines(path, currentMatrix)
        if (subpaths.isEmpty()) return
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (poly in subpaths) {
            var i = 0
            while (i + 1 < poly.size) {
                val px = poly[i]
                val py = poly[i + 1]
                if (px < minX) minX = px
                if (py < minY) minY = py
                if (px > maxX) maxX = px
                if (py > maxY) maxY = py
                i += 2
            }
        }
        val nextMinX = max(clipMinX(), floor(minX).toInt())
        val nextMinY = max(clipMinY(), floor(minY).toInt())
        val nextMaxX = min(clipMaxX(), ceil(maxX).toInt())
        val nextMaxY = min(clipMaxY(), ceil(maxY).toInt())
        clipRect = intArrayOf(nextMinX, nextMinY, nextMaxX, nextMaxY)
    }

    override fun isPointInPath(x: Float, y: Float, fillRule: ImageBufferFillRule): Boolean =
        isPointInPath(currentPath, x, y, fillRule)

    override fun isPointInPath(
        path: ImageBufferPath2D,
        x: Float,
        y: Float,
        fillRule: ImageBufferFillRule
    ): Boolean {
        val inv = currentMatrix.invert() ?: ImageBufferMatrix2D.IDENTITY
        val (lx, ly) = inv.transformPoint(x, y)
        return path.containsPoint(lx, ly, fillRule)
    }

    override fun isPointInStroke(x: Float, y: Float): Boolean =
        isPointInStroke(currentPath, x, y)

    override fun isPointInStroke(path: ImageBufferPath2D, x: Float, y: Float): Boolean {
        val inv = currentMatrix.invert() ?: ImageBufferMatrix2D.IDENTITY
        val (lx, ly) = inv.transformPoint(x, y)
        return path.containsStrokePoint(lx, ly, lineWidth, lineCap, lineJoin, miterLimit)
    }

    override fun fillText(text: String, x: Float, y: Float, maxWidth: Float?) {
        val fillCss = colorToCss(fillStyle.color)
        val fontCss = currentFontCss()
        val alignCss = mapTextAlignCss(textAlign)
        val baselineCss = mapTextBaselineCss(textBaseline)
        val compositeCss = mapCompositeOperationCss(globalCompositeOperation)
        val transform = currentTransformArray()
        if (WebImageBuffer2DBridge.isBrowserCanvasSupported && clipRect == null) {
            imageBuffer.syncCanvasFromCpuIfNeeded()
            WebImageBuffer2DBridge.fillText(
                surfaceElement = imageBuffer.surfaceElement,
                text = text,
                x = x,
                y = y,
                maxWidth = maxWidth,
                fontCss = fontCss,
                fillCss = fillCss,
                textAlignCss = alignCss,
                textBaselineCss = baselineCss,
                globalAlpha = globalAlpha,
                compositeOp = compositeCss,
                transform = transform
            )
            imageBuffer.markCanvasModified()
            return
        }

        WebImageBuffer2DBridge.fillText(
            surfaceElement = imageBuffer.surfaceElement,
            text = text,
            x = x,
            y = y,
            maxWidth = maxWidth,
            fontCss = fontCss,
            fillCss = fillCss,
            textAlignCss = alignCss,
            textBaselineCss = baselineCss,
            globalAlpha = globalAlpha,
            compositeOp = compositeCss,
            transform = transform
        )
        ScalableVectorFontEngine.renderText(
            text = text,
            x = x,
            y = y,
            maxWidth = maxWidth,
            fontSizePx = fontSizePx,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontStretch = fontStretch,
            fontVariantCaps = fontVariantCaps,
            letterSpacingPx = letterSpacingPx,
            wordSpacingPx = wordSpacingPx,
            textAlign = textAlign,
            textBaseline = textBaseline,
            direction = direction,
            transform = currentMatrix,
            strokeMode = false,
            strokeLineWidth = lineWidth,
            antialias = antialias,
            shadowBlur = shadowBlur,
            shadowColor = shadowColor,
            shadowOffsetX = shadowOffsetX,
            shadowOffsetY = shadowOffsetY,
            surfaceWidth = width,
            surfaceHeight = height
        ) { px, py, cov, isShadow, localX, localY ->
            if (px in clipMinX() until clipMaxX() && py in clipMinY() until clipMaxY()) {
                val src = if (isShadow) shadowColor else fillStyle.sampleAt(localX, localY, imageSmoothingEnabled)
                imageBuffer.setPixel(px, py, globalCompositeOperation.blend(src, imageBuffer.getPixel(px, py), globalAlpha, cov))
            }
        }
    }

    override fun strokeText(text: String, x: Float, y: Float, maxWidth: Float?) {
        val strokeCss = colorToCss(strokeStyle.color)
        val fontCss = currentFontCss()
        val alignCss = mapTextAlignCss(textAlign)
        val baselineCss = mapTextBaselineCss(textBaseline)
        val capCss = mapLineCapCss(lineCap)
        val joinCss = mapLineJoinCss(lineJoin)
        val compositeCss = mapCompositeOperationCss(globalCompositeOperation)
        val transform = currentTransformArray()
        if (WebImageBuffer2DBridge.isBrowserCanvasSupported && clipRect == null) {
            imageBuffer.syncCanvasFromCpuIfNeeded()
            WebImageBuffer2DBridge.strokeText(
                surfaceElement = imageBuffer.surfaceElement,
                text = text,
                x = x,
                y = y,
                maxWidth = maxWidth,
                fontCss = fontCss,
                strokeCss = strokeCss,
                lineWidth = lineWidth,
                lineCap = capCss,
                lineJoin = joinCss,
                miterLimit = miterLimit,
                textAlignCss = alignCss,
                textBaselineCss = baselineCss,
                globalAlpha = globalAlpha,
                compositeOp = compositeCss,
                transform = transform
            )
            imageBuffer.markCanvasModified()
            return
        }

        WebImageBuffer2DBridge.strokeText(
            surfaceElement = imageBuffer.surfaceElement,
            text = text,
            x = x,
            y = y,
            maxWidth = maxWidth,
            fontCss = fontCss,
            strokeCss = strokeCss,
            lineWidth = lineWidth,
            lineCap = capCss,
            lineJoin = joinCss,
            miterLimit = miterLimit,
            textAlignCss = alignCss,
            textBaselineCss = baselineCss,
            globalAlpha = globalAlpha,
            compositeOp = compositeCss,
            transform = transform
        )
        ScalableVectorFontEngine.renderText(
            text = text,
            x = x,
            y = y,
            maxWidth = maxWidth,
            fontSizePx = fontSizePx,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontStretch = fontStretch,
            fontVariantCaps = fontVariantCaps,
            letterSpacingPx = letterSpacingPx,
            wordSpacingPx = wordSpacingPx,
            textAlign = textAlign,
            textBaseline = textBaseline,
            direction = direction,
            transform = currentMatrix,
            strokeMode = true,
            strokeLineWidth = lineWidth,
            antialias = antialias,
            shadowBlur = shadowBlur,
            shadowColor = shadowColor,
            shadowOffsetX = shadowOffsetX,
            shadowOffsetY = shadowOffsetY,
            surfaceWidth = width,
            surfaceHeight = height
        ) { px, py, cov, isShadow, localX, localY ->
            if (px in clipMinX() until clipMaxX() && py in clipMinY() until clipMaxY()) {
                val src = if (isShadow) shadowColor else strokeStyle.sampleAt(localX, localY, imageSmoothingEnabled)
                imageBuffer.setPixel(px, py, globalCompositeOperation.blend(src, imageBuffer.getPixel(px, py), globalAlpha, cov))
            }
        }
    }

    override fun measureText(text: String): ImageBufferTextMetrics {
        val baseMetrics = ScalableVectorFontEngine.measureText(
            text = text,
            fontSizePx = fontSizePx,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontStretch = fontStretch,
            fontVariantCaps = fontVariantCaps,
            letterSpacingPx = letterSpacingPx,
            wordSpacingPx = wordSpacingPx,
            textAlign = textAlign,
            textBaseline = textBaseline
        )
        val measuredWidth = WebImageBuffer2DBridge.measureTextWidth(
            surfaceElement = imageBuffer.surfaceElement,
            text = text,
            fontCss = currentFontCss(),
            fallbackEstimate = baseMetrics.width
        )
        return if (measuredWidth > 0f && measuredWidth != baseMetrics.width) {
            baseMetrics.copy(width = measuredWidth)
        } else {
            baseMetrics
        }
    }

    override fun drawImage(image: com.nexustools.genericui.image.ImageBuffer, dx: Float, dy: Float) {
        drawImage(
            image = image,
            sx = 0f,
            sy = 0f,
            sWidth = image.width.toFloat(),
            sHeight = image.height.toFloat(),
            dx = dx,
            dy = dy,
            dWidth = image.width.toFloat(),
            dHeight = image.height.toFloat()
        )
    }

    override fun drawImage(
        image: com.nexustools.genericui.image.ImageBuffer,
        dx: Float,
        dy: Float,
        dWidth: Float,
        dHeight: Float
    ) {
        drawImage(
            image = image,
            sx = 0f,
            sy = 0f,
            sWidth = image.width.toFloat(),
            sHeight = image.height.toFloat(),
            dx = dx,
            dy = dy,
            dWidth = dWidth,
            dHeight = dHeight
        )
    }

    override fun drawImage(
        image: com.nexustools.genericui.image.ImageBuffer,
        sx: Float,
        sy: Float,
        sWidth: Float,
        sHeight: Float,
        dx: Float,
        dy: Float,
        dWidth: Float,
        dHeight: Float
    ) {
        if (dWidth <= 0f || dHeight <= 0f || sWidth <= 0f || sHeight <= 0f) return
        val compositeCss = mapCompositeOperationCss(globalCompositeOperation)
        val transform = currentTransformArray()
        val peer = image.nativeSurfacePeer

        if (WebImageBuffer2DBridge.isBrowserCanvasSupported && clipRect == null) {
            imageBuffer.syncCanvasFromCpuIfNeeded()
            val drawnPeer = if (peer != null) {
                WebImageBuffer2DBridge.drawSurfacePeer(
                    surfaceElement = imageBuffer.surfaceElement,
                    sourcePeer = peer,
                    sx = sx,
                    sy = sy,
                    sw = sWidth,
                    sh = sHeight,
                    dx = dx,
                    dy = dy,
                    dw = dWidth,
                    dh = dHeight,
                    globalAlpha = globalAlpha,
                    compositeOp = compositeCss,
                    imageSmoothingEnabled = imageSmoothingEnabled,
                    transform = transform
                )
            } else {
                false
            }
            if (drawnPeer) {
                imageBuffer.markCanvasModified()
                return
            }
            val rgba = image.asRgba8888()
            val drawnBytes = WebImageBuffer2DBridge.drawRgbaBytes(
                surfaceElement = imageBuffer.surfaceElement,
                rgbaBytes = rgba.bytes,
                srcWidth = rgba.width,
                srcHeight = rgba.height,
                sx = sx,
                sy = sy,
                sw = sWidth,
                sh = sHeight,
                dx = dx,
                dy = dy,
                dw = dWidth,
                dh = dHeight,
                globalAlpha = globalAlpha,
                compositeOp = compositeCss,
                imageSmoothingEnabled = imageSmoothingEnabled,
                transform = transform
            )
            if (drawnBytes) {
                imageBuffer.markCanvasModified()
                return
            }
        }

        if (peer != null) {
            WebImageBuffer2DBridge.drawSurfacePeer(
                surfaceElement = imageBuffer.surfaceElement,
                sourcePeer = peer,
                sx = sx,
                sy = sy,
                sw = sWidth,
                sh = sHeight,
                dx = dx,
                dy = dy,
                dw = dWidth,
                dh = dHeight,
                globalAlpha = globalAlpha,
                compositeOp = compositeCss,
                imageSmoothingEnabled = imageSmoothingEnabled,
                transform = transform
            )
        } else {
            val rgba = image.asRgba8888()
            WebImageBuffer2DBridge.drawRgbaBytes(
                surfaceElement = imageBuffer.surfaceElement,
                rgbaBytes = rgba.bytes,
                srcWidth = rgba.width,
                srcHeight = rgba.height,
                sx = sx,
                sy = sy,
                sw = sWidth,
                sh = sHeight,
                dx = dx,
                dy = dy,
                dw = dWidth,
                dh = dHeight,
                globalAlpha = globalAlpha,
                compositeOp = compositeCss,
                imageSmoothingEnabled = imageSmoothingEnabled,
                transform = transform
            )
        }

        val rgba = image.asRgba8888()
        val (tx0, ty0) = currentMatrix.transformPoint(dx, dy)
        val (tx1, ty1) = currentMatrix.transformPoint(dx + dWidth, dy)
        val (tx2, ty2) = currentMatrix.transformPoint(dx + dWidth, dy + dHeight)
        val (tx3, ty3) = currentMatrix.transformPoint(dx, dy + dHeight)
        val minTx = min(min(tx0, tx1), min(tx2, tx3))
        val minTy = min(min(ty0, ty1), min(ty2, ty3))
        val maxTx = max(max(tx0, tx1), max(tx2, tx3))
        val maxTy = max(max(ty0, ty1), max(ty2, ty3))
        val startX = floor(minTx).toInt().coerceIn(clipMinX(), clipMaxX())
        val startY = floor(minTy).toInt().coerceIn(clipMinY(), clipMaxY())
        val endX = ceil(maxTx).toInt().coerceIn(clipMinX(), clipMaxX())
        val endY = ceil(maxTy).toInt().coerceIn(clipMinY(), clipMaxY())
        val inv = currentMatrix.invert() ?: ImageBufferMatrix2D.IDENTITY
        for (py in startY until endY) {
            for (px in startX until endX) {
                val (lx, ly) = inv.transformPoint(px + 0.5f, py + 0.5f)
                if (lx >= dx && lx < dx + dWidth && ly >= dy && ly < dy + dHeight) {
                    val u = (lx - dx) / dWidth
                    val v = (ly - dy) / dHeight
                    val srcX = (sx + u * sWidth).toInt().coerceIn(0, rgba.width - 1)
                    val srcY = (sy + v * sHeight).toInt().coerceIn(0, rgba.height - 1)
                    val srcColor = rgba.getPixel(srcX, srcY)
                    imageBuffer.setPixel(
                        px,
                        py,
                        globalCompositeOperation.blend(srcColor, imageBuffer.getPixel(px, py), globalAlpha)
                    )
                }
            }
        }
    }

    override fun createImageData(width: Int, height: Int): com.nexustools.genericui.image.ImageBuffer =
        com.nexustools.genericui.image.ImageBuffer(max(1, width), max(1, height))

    override fun getImageData(x: Int, y: Int, width: Int, height: Int): com.nexustools.genericui.image.ImageBuffer {
        val safeW = max(1, width)
        val safeH = max(1, height)
        return com.nexustools.genericui.image.ImageBuffer(
            safeW,
            safeH,
            bytes = imageBuffer.readPixelsRgba(x, y, safeW, safeH)
        )
    }

    override fun putImageData(
        imageData: com.nexustools.genericui.image.ImageBuffer,
        dx: Int,
        dy: Int,
        dirtyX: Int,
        dirtyY: Int,
        dirtyWidth: Int,
        dirtyHeight: Int
    ) {
        val rgba = imageData.asRgba8888()
        if (dirtyX == 0 && dirtyY == 0 && dirtyWidth == rgba.width && dirtyHeight == rgba.height) {
            imageBuffer.writePixelsRgba(rgba.bytes, rgba.width, rgba.height, dx, dy)
        } else {
            val cropped = rgba.crop(
                cropX = dirtyX.coerceIn(0, rgba.width - 1),
                cropY = dirtyY.coerceIn(0, rgba.height - 1),
                cropWidth = dirtyWidth.coerceIn(1, rgba.width),
                cropHeight = dirtyHeight.coerceIn(1, rgba.height)
            )
            imageBuffer.writePixelsRgba(cropped.bytes, cropped.width, cropped.height, dx + dirtyX, dy + dirtyY)
        }
    }

    override fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) =
        imageBuffer.writePixelsRgba(rgbaBytes, width, height, dx, dy)

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
        val r = max(1, radius.roundToInt())
        val steps = max(1, max(abs(x1 - x0), abs(y1 - y0)).roundToInt())
        for (s in 0..steps) {
            val t = s.toFloat() / steps.toFloat()
            val cx = (x0 + (x1 - x0) * t).roundToInt()
            val cy = (y0 + (y1 - y0) * t).roundToInt()
            for (dy in -r..r) {
                for (dx in -r..r) {
                    if (dx * dx + dy * dy <= r * r) {
                        val px = cx + dx
                        val py = cy + dy
                        if (px in 0 until width && py in 0 until height) {
                            imageBuffer.setPixel(px, py, if (erase) RgbaColor.TRANSPARENT else color)
                        }
                    }
                }
            }
        }
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
        val grad = createLinearGradient(x0, y0, x1, y1).apply {
            addColorStop(0f, startColor)
            addColorStop(1f, endColor)
        }
        val sx = rectX.roundToInt().coerceIn(0, width)
        val sy = rectY.roundToInt().coerceIn(0, height)
        val ex = (rectX + rectW).roundToInt().coerceIn(0, width)
        val ey = (rectY + rectH).roundToInt().coerceIn(0, height)
        for (py in sy until ey) {
            for (px in sx until ex) {
                imageBuffer.setPixel(px, py, grad.sampleAt(px + 0.5f, py + 0.5f))
            }
        }
    }

    override fun floodFill(seedX: Int, seedY: Int, fillColor: RgbaColor, tolerance: Int) {
        if (seedX !in 0 until width || seedY !in 0 until height) return
        imageBuffer.fill(fillColor)
    }
}
