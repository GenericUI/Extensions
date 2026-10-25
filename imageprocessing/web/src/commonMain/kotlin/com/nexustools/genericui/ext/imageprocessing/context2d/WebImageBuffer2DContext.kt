package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.style.RgbaColor
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

    private val currentPath = ImageBufferPath2D()
    private val stateStack = ArrayDeque<SavedWebContextState>()

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
        currentPath.reset()
        stateStack.clear()
        clearRect(0f, 0f, width.toFloat(), height.toFloat())
    }

    override fun save() {
        stateStack.addLast(
            SavedWebContextState(
                matrix = currentMatrix.copy(),
                globalAlpha = globalAlpha,
                globalCompositeOperation = globalCompositeOperation,
                fillStyle = fillStyle,
                strokeStyle = strokeStyle,
                lineWidth = lineWidth,
                lineCap = lineCap,
                lineJoin = lineJoin,
                fontFamily = fontFamily,
                fontSizePx = fontSizePx,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textAlign = textAlign,
                textBaseline = textBaseline
            )
        )
    }

    override fun restore() {
        val s = stateStack.removeLastOrNull() ?: return
        currentMatrix = s.matrix
        globalAlpha = s.globalAlpha
        globalCompositeOperation = s.globalCompositeOperation
        fillStyle = s.fillStyle
        strokeStyle = s.strokeStyle
        lineWidth = s.lineWidth
        lineCap = s.lineCap
        lineJoin = s.lineJoin
        fontFamily = s.fontFamily
        fontSizePx = s.fontSizePx
        fontWeight = s.fontWeight
        fontStyle = s.fontStyle
        textAlign = s.textAlign
        textBaseline = s.textBaseline
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
        WebImageBuffer2DBridge.clearRect(imageBuffer.surfaceElement, x, y, width, height)
        val sx = floor(x).toInt().coerceIn(0, this.width)
        val sy = floor(y).toInt().coerceIn(0, this.height)
        val ex = ceil(x + width).toInt().coerceIn(0, this.width)
        val ey = ceil(y + height).toInt().coerceIn(0, this.height)
        for (py in sy until ey) {
            for (px in sx until ex) {
                imageBuffer.setPixel(px, py, RgbaColor.TRANSPARENT)
            }
        }
    }

    override fun fillRect(x: Float, y: Float, width: Float, height: Float) {
        val c = fillStyle.color
        WebImageBuffer2DBridge.fillRect(
            imageBuffer.surfaceElement,
            x,
            y,
            width,
            height,
            "rgba(${c.r},${c.g},${c.b},${(c.a / 255f) * globalAlpha})"
        )
        val p = ImageBufferPath2D().apply { rect(x, y, width, height) }
        fill(p, ImageBufferFillRule.NONZERO)
    }

    override fun strokeRect(x: Float, y: Float, width: Float, height: Float) {
        val c = strokeStyle.color
        WebImageBuffer2DBridge.strokeRect(
            imageBuffer.surfaceElement,
            x,
            y,
            width,
            height,
            "rgba(${c.r},${c.g},${c.b},${(c.a / 255f) * globalAlpha})",
            lineWidth
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
        val sx = floor(minX).toInt().coerceIn(0, width)
        val sy = floor(minY).toInt().coerceIn(0, height)
        val ex = ceil(maxX).toInt().coerceIn(0, width)
        val ey = ceil(maxY).toInt().coerceIn(0, height)
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
                            if (px in 0 until width && py in 0 until height) {
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

    override fun clip(fillRule: ImageBufferFillRule) {}
    override fun clip(path: ImageBufferPath2D, fillRule: ImageBufferFillRule) {}

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
        val c = fillStyle.color
        val fontCss = "${fontStyle.webValue} $fontWeight ${fontSizePx}px $fontFamily"
        WebImageBuffer2DBridge.fillText(
            imageBuffer.surfaceElement,
            text,
            x,
            y,
            fontCss,
            "rgba(${c.r},${c.g},${c.b},${(c.a / 255f) * globalAlpha})"
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
            if (px in 0 until width && py in 0 until height) {
                val src = if (isShadow) shadowColor else fillStyle.sampleAt(localX, localY, imageSmoothingEnabled)
                imageBuffer.setPixel(px, py, globalCompositeOperation.blend(src, imageBuffer.getPixel(px, py), globalAlpha, cov))
            }
        }
    }

    override fun strokeText(text: String, x: Float, y: Float, maxWidth: Float?) {
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
            if (px in 0 until width && py in 0 until height) {
                val src = if (isShadow) shadowColor else strokeStyle.sampleAt(localX, localY, imageSmoothingEnabled)
                imageBuffer.setPixel(px, py, globalCompositeOperation.blend(src, imageBuffer.getPixel(px, py), globalAlpha, cov))
            }
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
        val rgba = image.asRgba8888()
        imageBuffer.writePixelsRgba(rgba.bytes, rgba.width, rgba.height, dx.roundToInt(), dy.roundToInt())
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
        val startX = floor(dx).toInt().coerceIn(0, width)
        val startY = floor(dy).toInt().coerceIn(0, height)
        val endX = ceil(dx + dWidth).toInt().coerceIn(0, width)
        val endY = ceil(dy + dHeight).toInt().coerceIn(0, height)
        for (py in startY until endY) {
            val v = (py + 0.5f - dy) / dHeight
            val srcY = (sy + v * sHeight).toInt().coerceIn(0, rgba.height - 1)
            for (px in startX until endX) {
                val u = (px + 0.5f - dx) / dWidth
                val srcX = (sx + u * sWidth).toInt().coerceIn(0, rgba.width - 1)
                imageBuffer.setPixel(px, py, rgba.getPixel(srcX, srcY))
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
        imageBuffer.writePixelsRgba(rgba.bytes, rgba.width, rgba.height, dx, dy)
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

    private class SavedWebContextState(
        val matrix: ImageBufferMatrix2D,
        val globalAlpha: Float,
        val globalCompositeOperation: ImageBufferCompositeOperation,
        val fillStyle: ImageBufferPaintStyle,
        val strokeStyle: ImageBufferPaintStyle,
        val lineWidth: Float,
        val lineCap: ImageBufferLineCap,
        val lineJoin: ImageBufferLineJoin,
        val fontFamily: String,
        val fontSizePx: Float,
        val fontWeight: Int,
        val fontStyle: ImageBufferFontStyle,
        val textAlign: ImageBufferTextAlign,
        val textBaseline: ImageBufferTextBaseline
    )
}
