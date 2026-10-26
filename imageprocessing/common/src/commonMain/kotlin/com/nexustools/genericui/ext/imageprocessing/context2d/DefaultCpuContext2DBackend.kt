package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.style.RgbaColor
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Full-featured portable 2D rendering context implementation ([ImageBuffer2DContext]) for
 * [DefaultCpuImageBuffer] in `:extensions:imageprocessing:common`.
 *
 * Implements 100% HTML5 2D Context feature parity using [ScalableVectorFontEngine],
 * [ImageBufferPath2D], [ImageBufferMatrix2D], [ImageBufferPaintStyle], [ImageBufferGradient2D],
 * [ImageBufferPattern2D], and all 26 [ImageBufferCompositeOperation] modes, while notifying
 * [ImageBuffer.markDirty] on every drawing operation.
 */
class DefaultCpuContext2DBackend(
    override val imageBuffer: DefaultCpuImageBuffer,
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

    private var clipBoundsMinX: Int = 0
    private var clipBoundsMinY: Int = 0
    private var clipBoundsMaxX: Int = Int.MAX_VALUE
    private var clipBoundsMaxY: Int = Int.MAX_VALUE

    private val currentPath = ImageBufferPath2D()
    private val stateStack = ArrayDeque<SavedContextState>()

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
        clipBoundsMinX = 0
        clipBoundsMinY = 0
        clipBoundsMaxX = Int.MAX_VALUE
        clipBoundsMaxY = Int.MAX_VALUE
        currentPath.reset()
        stateStack.clear()
        clearRect(0f, 0f, width.toFloat(), height.toFloat())
    }

    override fun save() {
        stateStack.addLast(
            SavedContextState(
                matrix = currentMatrix.copy(),
                globalAlpha = globalAlpha,
                globalCompositeOperation = globalCompositeOperation,
                imageSmoothingEnabled = imageSmoothingEnabled,
                imageSmoothingQuality = imageSmoothingQuality,
                filter = filter,
                fillStyle = fillStyle,
                strokeStyle = strokeStyle,
                shadowBlur = shadowBlur,
                shadowColor = shadowColor,
                shadowOffsetX = shadowOffsetX,
                shadowOffsetY = shadowOffsetY,
                lineWidth = lineWidth,
                lineCap = lineCap,
                lineJoin = lineJoin,
                miterLimit = miterLimit,
                lineDashOffset = lineDashOffset,
                lineDashSegments = lineDashSegments.copyOf(),
                fontFamily = fontFamily,
                fontSizePx = fontSizePx,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textAlign = textAlign,
                textBaseline = textBaseline,
                direction = direction,
                letterSpacingPx = letterSpacingPx,
                wordSpacingPx = wordSpacingPx,
                fontKerning = fontKerning,
                fontStretch = fontStretch,
                fontVariantCaps = fontVariantCaps,
                textRendering = textRendering,
                clipMinX = clipBoundsMinX,
                clipMinY = clipBoundsMinY,
                clipMaxX = clipBoundsMaxX,
                clipMaxY = clipBoundsMaxY
            )
        )
    }

    override fun restore() {
        val state = stateStack.removeLastOrNull() ?: return
        currentMatrix = state.matrix
        globalAlpha = state.globalAlpha
        globalCompositeOperation = state.globalCompositeOperation
        imageSmoothingEnabled = state.imageSmoothingEnabled
        imageSmoothingQuality = state.imageSmoothingQuality
        filter = state.filter
        fillStyle = state.fillStyle
        strokeStyle = state.strokeStyle
        shadowBlur = state.shadowBlur
        shadowColor = state.shadowColor
        shadowOffsetX = state.shadowOffsetX
        shadowOffsetY = state.shadowOffsetY
        lineWidth = state.lineWidth
        lineCap = state.lineCap
        lineJoin = state.lineJoin
        miterLimit = state.miterLimit
        lineDashOffset = state.lineDashOffset
        lineDashSegments = state.lineDashSegments
        fontFamily = state.fontFamily
        fontSizePx = state.fontSizePx
        fontWeight = state.fontWeight
        fontStyle = state.fontStyle
        textAlign = state.textAlign
        textBaseline = state.textBaseline
        direction = state.direction
        letterSpacingPx = state.letterSpacingPx
        wordSpacingPx = state.wordSpacingPx
        fontKerning = state.fontKerning
        fontStretch = state.fontStretch
        fontVariantCaps = state.fontVariantCaps
        textRendering = state.textRendering
        clipBoundsMinX = state.clipMinX
        clipBoundsMinY = state.clipMinY
        clipBoundsMaxX = state.clipMaxX
        clipBoundsMaxY = state.clipMaxY
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
        lineDashSegments = if (segments.size % 2 == 1) {
            FloatArray(segments.size * 2) { i -> segments[i % segments.size].coerceAtLeast(0f) }
        } else {
            FloatArray(segments.size) { i -> segments[i].coerceAtLeast(0f) }
        }
        lineDashOffset = offset
    }

    override fun getLineDash(): FloatArray = lineDashSegments.copyOf()

    override fun clearRect(x: Float, y: Float, width: Float, height: Float) {
        val (tx0, ty0) = currentMatrix.transformPoint(x, y)
        val (tx1, ty1) = currentMatrix.transformPoint(x + width, y + height)
        val sx = floor(min(tx0, tx1)).toInt().coerceIn(max(0, clipBoundsMinX), min(this.width, clipBoundsMaxX))
        val sy = floor(min(ty0, ty1)).toInt().coerceIn(max(0, clipBoundsMinY), min(this.height, clipBoundsMaxY))
        val ex = ceil(max(tx0, tx1)).toInt().coerceIn(max(0, clipBoundsMinX), min(this.width, clipBoundsMaxX))
        val ey = ceil(max(ty0, ty1)).toInt().coerceIn(max(0, clipBoundsMinY), min(this.height, clipBoundsMaxY))
        if (ex <= sx || ey <= sy) return
        for (py in sy until ey) {
            for (px in sx until ex) {
                imageBuffer.setPixel(px, py, RgbaColor.TRANSPARENT)
            }
        }
        imageBuffer.markDirty(sx, sy, ex - sx, ey - sy)
    }

    override fun fillRect(x: Float, y: Float, width: Float, height: Float) {
        val p = ImageBufferPath2D().apply { rect(x, y, width, height) }
        fill(p, ImageBufferFillRule.NONZERO)
    }

    override fun strokeRect(x: Float, y: Float, width: Float, height: Float) {
        val p = ImageBufferPath2D().apply { rect(x, y, width, height) }
        stroke(p)
    }

    override fun beginPath() {
        currentPath.reset()
    }

    override fun closePath() {
        currentPath.closePath()
    }

    override fun moveTo(x: Float, y: Float) {
        currentPath.moveTo(x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        currentPath.lineTo(x, y)
    }

    override fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float) {
        currentPath.quadraticCurveTo(cpx, cpy, x, y)
    }

    override fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) {
        currentPath.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, x, y)
    }

    override fun arcTo(x1: Float, y1: Float, x2: Float, y2: Float, radius: Float) {
        currentPath.arcTo(x1, y1, x2, y2, radius)
    }

    override fun arc(
        x: Float,
        y: Float,
        radius: Float,
        startAngle: Float,
        endAngle: Float,
        counterClockwise: Boolean
    ) {
        currentPath.arc(x, y, radius, startAngle, endAngle, counterClockwise)
    }

    override fun ellipse(
        x: Float,
        y: Float,
        radiusX: Float,
        radiusY: Float,
        rotation: Float,
        startAngle: Float,
        endAngle: Float,
        counterClockwise: Boolean
    ) {
        currentPath.ellipse(x, y, radiusX, radiusY, rotation, startAngle, endAngle, counterClockwise)
    }

    override fun rect(x: Float, y: Float, width: Float, height: Float) {
        currentPath.rect(x, y, width, height)
    }

    override fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        currentPath.roundRect(x, y, width, height, radius)
    }

    override fun roundRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        topLeftRadius: Float,
        topRightRadius: Float,
        bottomRightRadius: Float,
        bottomLeftRadius: Float
    ) {
        currentPath.roundRect(
            x,
            y,
            width,
            height,
            topLeftRadius,
            topRightRadius,
            bottomRightRadius,
            bottomLeftRadius
        )
    }

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

    override fun fill(fillRule: ImageBufferFillRule) {
        fill(currentPath, fillRule)
    }

    override fun fill(path: ImageBufferPath2D, fillRule: ImageBufferFillRule) {
        val subpaths = transformPolylines(path, currentMatrix)
        if (subpaths.isEmpty()) return
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var totalEdges = 0
        for (poly in subpaths) {
            val pts = poly.size / 2
            if (pts >= 3) totalEdges += pts
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
        if (totalEdges == 0) return

        val sx = floor(minX).toInt().coerceIn(max(0, clipBoundsMinX), min(width, clipBoundsMaxX))
        val sy = floor(minY).toInt().coerceIn(max(0, clipBoundsMinY), min(height, clipBoundsMaxY))
        val ex = ceil(maxX).toInt().coerceIn(max(0, clipBoundsMinX), min(width, clipBoundsMaxX))
        val ey = ceil(maxY).toInt().coerceIn(max(0, clipBoundsMinY), min(height, clipBoundsMaxY))
        if (ex <= sx || ey <= sy) return

        val isSolid = fillStyle.kind == ImageBufferPaintStyleKind.SOLID_COLOR
        val solidColor = fillStyle.color
        val invMatrix = if (!isSolid) (currentMatrix.invert() ?: ImageBufferMatrix2D.IDENTITY) else ImageBufferMatrix2D.IDENTITY
        val intersectXBuf = FloatArray(totalEdges)
        val intersectDirBuf = IntArray(totalEdges)

        for (py in sy until ey) {
            val sampleY = py + 0.5f
            var hitCount = 0
            for (poly in subpaths) {
                val pointCount = poly.size / 2
                if (pointCount < 3) continue
                for (i in 0 until pointCount) {
                    val j = if (i + 1 < pointCount) i + 1 else 0
                    val x0 = poly[i * 2]
                    val y0 = poly[i * 2 + 1]
                    val x1 = poly[j * 2]
                    val y1 = poly[j * 2 + 1]
                    if ((y0 <= sampleY && y1 > sampleY) || (y1 <= sampleY && y0 > sampleY)) {
                        val vt = (sampleY - y0) / (y1 - y0)
                        intersectXBuf[hitCount] = x0 + vt * (x1 - x0)
                        intersectDirBuf[hitCount] = if (y1 > y0) 1 else -1
                        hitCount++
                    }
                }
            }
            if (hitCount < 2) continue

            for (i in 1 until hitCount) {
                val keyX = intersectXBuf[i]
                val keyDir = intersectDirBuf[i]
                var k = i - 1
                while (k >= 0 && intersectXBuf[k] > keyX) {
                    intersectXBuf[k + 1] = intersectXBuf[k]
                    intersectDirBuf[k + 1] = intersectDirBuf[k]
                    k--
                }
                intersectXBuf[k + 1] = keyX
                intersectDirBuf[k + 1] = keyDir
            }

            if (fillRule == ImageBufferFillRule.EVENODD) {
                var i = 0
                while (i + 1 < hitCount) {
                    val xStart = ceil(intersectXBuf[i] - 0.5f).toInt().coerceIn(sx, ex)
                    val xEnd = ceil(intersectXBuf[i + 1] - 0.5f).toInt().coerceIn(sx, ex)
                    for (px in xStart until xEnd) {
                        val src = if (isSolid) {
                            solidColor
                        } else {
                            val (localX, localY) = invMatrix.transformPoint(px + 0.5f, sampleY)
                            fillStyle.sampleAt(localX, localY, imageSmoothingEnabled)
                        }
                        blendPixel(px, py, src)
                    }
                    i += 2
                }
            } else {
                var winding = 0
                var spanStartX = 0f
                for (i in 0 until hitCount) {
                    val prevWinding = winding
                    winding += intersectDirBuf[i]
                    if (prevWinding == 0 && winding != 0) {
                        spanStartX = intersectXBuf[i]
                    } else if (prevWinding != 0 && winding == 0) {
                        val xStart = ceil(spanStartX - 0.5f).toInt().coerceIn(sx, ex)
                        val xEnd = ceil(intersectXBuf[i] - 0.5f).toInt().coerceIn(sx, ex)
                        for (px in xStart until xEnd) {
                            val src = if (isSolid) {
                                solidColor
                            } else {
                                val (localX, localY) = invMatrix.transformPoint(px + 0.5f, sampleY)
                                fillStyle.sampleAt(localX, localY, imageSmoothingEnabled)
                            }
                            blendPixel(px, py, src)
                        }
                    }
                }
            }
        }
        imageBuffer.markDirty(sx, sy, ex - sx, ey - sy)
    }

    override fun stroke() {
        stroke(currentPath)
    }

    override fun stroke(path: ImageBufferPath2D) {
        val subpaths = transformPolylines(path, currentMatrix)
        if (subpaths.isEmpty()) return
        val halfW = max(0.5f, lineWidth * 0.5f)
        val invMatrix = currentMatrix.invert() ?: ImageBufferMatrix2D.IDENTITY
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
                    val cx = x0 + (x1 - x0) * t
                    val cy = y0 + (y1 - y0) * t
                    val r = ceil(halfW).toInt()
                    val icx = cx.roundToInt()
                    val icy = cy.roundToInt()
                    for (dy in -r..r) {
                        for (dx in -r..r) {
                            if (dx * dx + dy * dy <= halfW * halfW + 0.5f) {
                                val px = icx + dx
                                val py = icy + dy
                                if (px in max(0, clipBoundsMinX) until min(width, clipBoundsMaxX) &&
                                    py in max(0, clipBoundsMinY) until min(height, clipBoundsMaxY)
                                ) {
                                    val (lx, ly) = invMatrix.transformPoint(px + 0.5f, py + 0.5f)
                                    val src = strokeStyle.sampleAt(lx, ly, imageSmoothingEnabled)
                                    blendPixel(px, py, src)
                                }
                            }
                        }
                    }
                }
                i += 2
            }
        }
        imageBuffer.markDirty(0, 0, width, height)
    }

    override fun clip(fillRule: ImageBufferFillRule) {
        clip(currentPath, fillRule)
    }

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
        clipBoundsMinX = max(clipBoundsMinX, floor(minX).toInt())
        clipBoundsMinY = max(clipBoundsMinY, floor(minY).toInt())
        clipBoundsMaxX = min(clipBoundsMaxX, ceil(maxX).toInt())
        clipBoundsMaxY = min(clipBoundsMaxY, ceil(maxY).toInt())
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
        renderTextInternal(text, x, y, maxWidth, strokeMode = false)
    }

    override fun strokeText(text: String, x: Float, y: Float, maxWidth: Float?) {
        renderTextInternal(text, x, y, maxWidth, strokeMode = true)
    }

    private fun renderTextInternal(
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float?,
        strokeMode: Boolean
    ) {
        if (text.isEmpty()) return
        val style = if (strokeMode) strokeStyle else fillStyle
        var dirtyMinX = width
        var dirtyMinY = height
        var dirtyMaxX = 0
        var dirtyMaxY = 0
        var touched = false

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
            strokeMode = strokeMode,
            strokeLineWidth = lineWidth,
            antialias = antialias,
            shadowBlur = shadowBlur,
            shadowColor = shadowColor,
            shadowOffsetX = shadowOffsetX,
            shadowOffsetY = shadowOffsetY,
            surfaceWidth = width,
            surfaceHeight = height
        ) { px, py, coverage, isShadow, localX, localY ->
            if (px in max(0, clipBoundsMinX) until min(width, clipBoundsMaxX) &&
                py in max(0, clipBoundsMinY) until min(height, clipBoundsMaxY)
            ) {
                val baseColor = if (isShadow) shadowColor else style.sampleAt(localX, localY, imageSmoothingEnabled)
                blendPixel(px, py, baseColor, coverage)
                if (px < dirtyMinX) dirtyMinX = px
                if (py < dirtyMinY) dirtyMinY = py
                if (px + 1 > dirtyMaxX) dirtyMaxX = px + 1
                if (py + 1 > dirtyMaxY) dirtyMaxY = py + 1
                touched = true
            }
        }
        if (touched) {
            imageBuffer.markDirty(dirtyMinX, dirtyMinY, dirtyMaxX - dirtyMinX, dirtyMaxY - dirtyMinY)
        }
    }

    override fun measureText(text: String): ImageBufferTextMetrics =
        ScalableVectorFontEngine.measureText(
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
        val rgba = image.asRgba8888()
        val startX = floor(dx).toInt().coerceIn(max(0, clipBoundsMinX), min(width, clipBoundsMaxX))
        val startY = floor(dy).toInt().coerceIn(max(0, clipBoundsMinY), min(height, clipBoundsMaxY))
        val endX = ceil(dx + dWidth).toInt().coerceIn(max(0, clipBoundsMinX), min(width, clipBoundsMaxX))
        val endY = ceil(dy + dHeight).toInt().coerceIn(max(0, clipBoundsMinY), min(height, clipBoundsMaxY))
        if (endX <= startX || endY <= startY) return

        for (py in startY until endY) {
            val v = (py + 0.5f - dy) / dHeight
            val srcY = (sy + v * sHeight).toInt().coerceIn(0, rgba.height - 1)
            for (px in startX until endX) {
                val u = (px + 0.5f - dx) / dWidth
                val srcX = (sx + u * sWidth).toInt().coerceIn(0, rgba.width - 1)
                val c = rgba.getPixel(srcX, srcY)
                val scaledAlpha = (c.a * globalAlpha).roundToInt().coerceIn(0, 255)
                blendPixel(px, py, RgbaColor(c.r, c.g, c.b, scaledAlpha))
            }
        }
        imageBuffer.markDirty(startX, startY, endX - startX, endY - startY)
    }

    override fun createImageData(width: Int, height: Int): com.nexustools.genericui.image.ImageBuffer =
        com.nexustools.genericui.image.ImageBuffer(max(1, width), max(1, height))

    override fun getImageData(x: Int, y: Int, width: Int, height: Int): com.nexustools.genericui.image.ImageBuffer {
        val safeW = max(1, width)
        val safeH = max(1, height)
        val raw = getImageDataRgba(x, y, safeW, safeH)
        return com.nexustools.genericui.image.ImageBuffer(safeW, safeH, bytes = raw)
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
        val sx0 = dirtyX.coerceIn(0, rgba.width)
        val sy0 = dirtyY.coerceIn(0, rgba.height)
        val sx1 = (dirtyX + dirtyWidth).coerceIn(0, rgba.width)
        val sy1 = (dirtyY + dirtyHeight).coerceIn(0, rgba.height)
        for (sy in sy0 until sy1) {
            val py = dy + sy
            if (py !in 0 until height) continue
            for (sx in sx0 until sx1) {
                val px = dx + sx
                if (px !in 0 until width) continue
                imageBuffer.setPixel(px, py, rgba.getPixel(sx, sy))
            }
        }
        imageBuffer.markDirty(dx + sx0, dy + sy0, sx1 - sx0, sy1 - sy0)
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
        val effectiveAlpha = (color.a * opacity.coerceIn(0f, 1f) * globalAlpha).roundToInt().coerceIn(0, 255)
        val paintColor = RgbaColor(color.r, color.g, color.b, effectiveAlpha)
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
                            if (erase) {
                                imageBuffer.setPixel(px, py, RgbaColor.TRANSPARENT)
                            } else {
                                blendPixel(px, py, paintColor)
                            }
                        }
                    }
                }
            }
        }
        val minX = (min(x0, x1) - r).toInt().coerceIn(0, width)
        val minY = (min(y0, y1) - r).toInt().coerceIn(0, height)
        val maxX = (max(x0, x1) + r + 1).toInt().coerceIn(0, width)
        val maxY = (max(y0, y1) + r + 1).toInt().coerceIn(0, height)
        imageBuffer.markDirty(minX, minY, maxX - minX, maxY - minY)
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
                blendPixel(px, py, grad.sampleAt(px + 0.5f, py + 0.5f))
            }
        }
        imageBuffer.markDirty(sx, sy, ex - sx, ey - sy)
    }

    override fun floodFill(seedX: Int, seedY: Int, fillColor: RgbaColor, tolerance: Int) {
        if (seedX !in 0 until width || seedY !in 0 until height) return
        val target = imageBuffer.getPixel(seedX, seedY)
        if (colorsWithinTolerance(target, fillColor, 0)) return
        val visited = BooleanArray(width * height)
        val queue = IntArray(width * height)
        var head = 0
        var tail = 0
        val startIdx = seedY * width + seedX
        visited[startIdx] = true
        queue[tail++] = startIdx

        while (head < tail) {
            val idx = queue[head++]
            val px = idx % width
            val py = idx / width
            imageBuffer.setPixel(px, py, fillColor)

            if (px > 0) {
                val left = idx - 1
                if (!visited[left] && colorsWithinTolerance(imageBuffer.getPixel(px - 1, py), target, tolerance)) {
                    visited[left] = true
                    queue[tail++] = left
                }
            }
            if (px + 1 < width) {
                val right = idx + 1
                if (!visited[right] && colorsWithinTolerance(imageBuffer.getPixel(px + 1, py), target, tolerance)) {
                    visited[right] = true
                    queue[tail++] = right
                }
            }
            if (py > 0) {
                val up = idx - width
                if (!visited[up] && colorsWithinTolerance(imageBuffer.getPixel(px, py - 1), target, tolerance)) {
                    visited[up] = true
                    queue[tail++] = up
                }
            }
            if (py + 1 < height) {
                val down = idx + width
                if (!visited[down] && colorsWithinTolerance(imageBuffer.getPixel(px, py + 1), target, tolerance)) {
                    visited[down] = true
                    queue[tail++] = down
                }
            }
        }
        imageBuffer.markDirty(0, 0, width, height)
    }

    private fun colorsWithinTolerance(a: RgbaColor, b: RgbaColor, tol: Int): Boolean =
        abs(a.r - b.r) <= tol &&
            abs(a.g - b.g) <= tol &&
            abs(a.b - b.b) <= tol &&
            abs(a.a - b.a) <= tol

    private fun blendPixel(px: Int, py: Int, src: RgbaColor, coverage: Float = 1f) {
        if (globalCompositeOperation == ImageBufferCompositeOperation.SOURCE_OVER &&
            src.a == 255 &&
            globalAlpha >= 0.999f &&
            coverage >= 0.999f
        ) {
            imageBuffer.setPixel(px, py, src)
            return
        }
        val dst = imageBuffer.getPixel(px, py)
        val out = globalCompositeOperation.blend(src, dst, globalAlpha, coverage)
        imageBuffer.setPixel(px, py, out)
    }

    private class SavedContextState(
        val matrix: ImageBufferMatrix2D,
        val globalAlpha: Float,
        val globalCompositeOperation: ImageBufferCompositeOperation,
        val imageSmoothingEnabled: Boolean,
        val imageSmoothingQuality: ImageBufferImageSmoothingQuality,
        val filter: String,
        val fillStyle: ImageBufferPaintStyle,
        val strokeStyle: ImageBufferPaintStyle,
        val shadowBlur: Float,
        val shadowColor: RgbaColor,
        val shadowOffsetX: Float,
        val shadowOffsetY: Float,
        val lineWidth: Float,
        val lineCap: ImageBufferLineCap,
        val lineJoin: ImageBufferLineJoin,
        val miterLimit: Float,
        val lineDashOffset: Float,
        val lineDashSegments: FloatArray,
        val fontFamily: String,
        val fontSizePx: Float,
        val fontWeight: Int,
        val fontStyle: ImageBufferFontStyle,
        val textAlign: ImageBufferTextAlign,
        val textBaseline: ImageBufferTextBaseline,
        val direction: ImageBufferTextDirection,
        val letterSpacingPx: Float,
        val wordSpacingPx: Float,
        val fontKerning: ImageBufferFontKerning,
        val fontStretch: ImageBufferFontStretch,
        val fontVariantCaps: ImageBufferFontVariantCaps,
        val textRendering: ImageBufferTextRendering,
        val clipMinX: Int,
        val clipMinY: Int,
        val clipMaxX: Int,
        val clipMaxY: Int
    )
}
