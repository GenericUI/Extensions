package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.style.RgbaColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Scalable vector and analytical Signed Distance Field (SDF) glyph engine for `:extensions:imageprocessing`.
 *
 * Defines proportional vector stroke and curve outlines in normalized coordinates for all printable
 * ASCII characters (`0x20..0x7E`) and common UI symbols (`•`, `▶`, `▼`, `◀`, `▲`, `✓`, `×`, `—`, `–`, `…`),
 * evaluating exact capsule-SDF coverage per pixel with subpixel antialiasing, variable font weight (`100..900`),
 * italic/oblique shear, font stretch, small-caps variants, letter/word spacing, text alignment, baselines,
 * `maxWidth` horizontal scaling, and soft Gaussian text shadows.
 */
object ScalableVectorFontEngine {

    private const val CAP_TOP_Y = 0.14f
    private const val X_HEIGHT_Y = 0.36f
    private const val MID_CAP_Y = 0.50f
    private const val BASELINE_Y = 0.86f
    private const val DESCENDER_Y = 1.10f

    private class GlyphOutline(
        val advance: Float,
        val segments: FloatArray,
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float
    )

    private class GlyphBuilder(var advance: Float = 0.60f) {
        private val segs = mutableListOf<Float>()

        fun line(x0: Float, y0: Float, x1: Float, y1: Float): GlyphBuilder {
            segs.add(x0)
            segs.add(y0)
            segs.add(x1)
            segs.add(y1)
            return this
        }

        fun polyline(vararg pts: Float): GlyphBuilder {
            var i = 0
            while (i + 3 < pts.size) {
                line(pts[i], pts[i + 1], pts[i + 2], pts[i + 3])
                i += 2
            }
            return this
        }

        fun quad(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, steps: Int = 6): GlyphBuilder {
            var px = x0
            var py = y0
            for (s in 1..steps) {
                val t = s.toFloat() / steps.toFloat()
                val u = 1f - t
                val nx = u * u * x0 + 2f * u * t * cx + t * t * x1
                val ny = u * u * y0 + 2f * u * t * cy + t * t * y1
                line(px, py, nx, ny)
                px = nx
                py = ny
            }
            return this
        }

        fun arc(
            cx: Float,
            cy: Float,
            rx: Float,
            ry: Float,
            startDeg: Float,
            endDeg: Float,
            steps: Int = 10
        ): GlyphBuilder {
            val startRad = startDeg * (PI.toFloat() / 180f)
            val endRad = endDeg * (PI.toFloat() / 180f)
            var px = cx + rx * cos(startRad)
            var py = cy + ry * sin(startRad)
            for (s in 1..steps) {
                val t = s.toFloat() / steps.toFloat()
                val a = startRad + (endRad - startRad) * t
                val nx = cx + rx * cos(a)
                val ny = cy + ry * sin(a)
                line(px, py, nx, ny)
                px = nx
                py = ny
            }
            return this
        }

        fun dot(cx: Float, cy: Float, r: Float = 0.015f): GlyphBuilder {
            return line(cx, cy - r, cx, cy + r)
        }

        fun build(): GlyphOutline {
            val arr = segs.toFloatArray()
            if (arr.isEmpty()) {
                return GlyphOutline(advance, arr, 0f, advance, CAP_TOP_Y, BASELINE_Y)
            }
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            var i = 0
            while (i + 1 < arr.size) {
                val x = arr[i]
                val y = arr[i + 1]
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                i += 2
            }
            return GlyphOutline(advance, arr, minX, maxX, minY, maxY)
        }
    }

    private data class GlyphCacheKey(
        val codePoint: Int,
        val heightDeciPx: Int,
        val weightClass: Int,
        val styleCode: Int,
        val stretchPermille: Int,
        val strokeMode: Boolean,
        val strokeWidthDeciPx: Int,
        val antialias: Boolean
    )

    private class RasterizedGlyphMask(
        val originX: Int,
        val originY: Int,
        val width: Int,
        val height: Int,
        val coverage: ByteArray
    )

    private val glyphTable: Map<Int, GlyphOutline> = buildGlyphTable()
    private val fallbackGlyph: GlyphOutline = buildFallbackGlyph()
    private val maskCache = LinkedHashMap<GlyphCacheKey, RasterizedGlyphMask>(512)

    private fun glyphFor(ch: Char): GlyphOutline =
        glyphTable[ch.code] ?: fallbackGlyph

    /**
     * Measures [text] and returns full [ImageBufferTextMetrics].
     */
    fun measureText(
        text: String,
        fontSizePx: Float,
        fontWeight: Int = 400,
        fontStyle: ImageBufferFontStyle = ImageBufferFontStyle.NORMAL,
        fontStretch: ImageBufferFontStretch = ImageBufferFontStretch.NORMAL,
        fontVariantCaps: ImageBufferFontVariantCaps = ImageBufferFontVariantCaps.NORMAL,
        letterSpacingPx: Float = 0f,
        wordSpacingPx: Float = 0f,
        textAlign: ImageBufferTextAlign = ImageBufferTextAlign.START,
        textBaseline: ImageBufferTextBaseline = ImageBufferTextBaseline.ALPHABETIC,
        direction: ImageBufferTextDirection = ImageBufferTextDirection.INHERIT
    ): ImageBufferTextMetrics {
        val safeSize = max(4f, fontSizePx)
        val stretchScale = fontStretch.widthScale
        val strokeRadiusEm = strokeRadiusEmForWeight(fontWeight)

        var cursorX = 0f
        var minBoundX = Float.POSITIVE_INFINITY
        var maxBoundX = Float.NEGATIVE_INFINITY
        var minBoundY = Float.POSITIVE_INFINITY
        var maxBoundY = Float.NEGATIVE_INFINITY

        for (i in text.indices) {
            val rawChar = text[i]
            val (effectiveChar, scaleFactor) = resolveVariantCap(rawChar, fontVariantCaps)
            val glyph = glyphFor(effectiveChar)
            val charSize = safeSize * scaleFactor
            val charAdvance = glyph.advance * charSize * stretchScale
            if (glyph.segments.isNotEmpty()) {
                val pad = strokeRadiusEm * charSize
                val yOffset = (1f - scaleFactor) * BASELINE_Y * safeSize
                val gx0 = cursorX + glyph.minX * charSize * stretchScale - pad
                val gx1 = cursorX + glyph.maxX * charSize * stretchScale + pad
                val gy0 = yOffset + glyph.minY * charSize - pad
                val gy1 = yOffset + glyph.maxY * charSize + pad
                if (gx0 < minBoundX) minBoundX = gx0
                if (gx1 > maxBoundX) maxBoundX = gx1
                if (gy0 < minBoundY) minBoundY = gy0
                if (gy1 > maxBoundY) maxBoundY = gy1
            }
            cursorX += charAdvance
            if (i + 1 < text.length) {
                cursorX += letterSpacingPx
            }
            if (rawChar == ' ') {
                cursorX += wordSpacingPx
            }
        }

        val totalWidth = max(0f, cursorX)
        val alignShiftX = computeAlignOffsetX(totalWidth, textAlign, direction)
        val baselineY = computeBaselineAnchorY(safeSize, textBaseline)
        val fontAscent = baselineY - CAP_TOP_Y * safeSize
        val fontDescent = DESCENDER_Y * safeSize - baselineY

        val actualLeft = if (minBoundX.isFinite()) -(minBoundX + alignShiftX) else 0f
        val actualRight = if (maxBoundX.isFinite()) (maxBoundX + alignShiftX) else totalWidth
        val actualAscent = if (minBoundY.isFinite()) (baselineY - minBoundY) else fontAscent
        val actualDescent = if (maxBoundY.isFinite()) (maxBoundY - baselineY) else fontDescent

        return ImageBufferTextMetrics(
            width = totalWidth,
            actualBoundingBoxLeft = actualLeft,
            actualBoundingBoxRight = actualRight,
            fontBoundingBoxAscent = fontAscent,
            fontBoundingBoxDescent = fontDescent,
            actualBoundingBoxAscent = actualAscent,
            actualBoundingBoxDescent = actualDescent,
            emHeightAscent = fontAscent,
            emHeightDescent = fontDescent,
            hangingBaseline = fontAscent * 0.8f,
            alphabeticBaseline = 0f,
            ideographicBaseline = -fontDescent
        )
    }

    /**
     * Evaluates normalized SDF coverage `[0.0 .. 1.0]` for [ch] at normalized glyph coordinates
     * `(normX, normY)` where `normY` is in `[0..1]` (`CAP_TOP_Y = 0.14`, `BASELINE_Y = 0.86`).
     */
    fun sampleGlyphCoverage(
        ch: Char,
        normX: Float,
        normY: Float,
        fontWeight: Int = 400,
        fontStyle: ImageBufferFontStyle = ImageBufferFontStyle.NORMAL,
        feather: Float = 0.025f
    ): Float {
        val glyph = glyphFor(ch)
        if (glyph.segments.isEmpty()) return 0f
        val shear = when (fontStyle) {
            ImageBufferFontStyle.ITALIC -> 0.18f
            ImageBufferFontStyle.OBLIQUE -> 0.22f
            ImageBufferFontStyle.NORMAL -> 0f
        }
        val unShearedX = normX - (BASELINE_Y - normY) * shear
        val d = minDistanceToSegments(unShearedX, normY, glyph.segments)
        val r = strokeRadiusEmForWeight(fontWeight)
        return smoothCoverage(d, r, max(0.002f, feather))
    }

    /**
     * Renders [text] onto a target surface by invoking [pixelBlender] for each covered shadow
     * and glyph pixel.
     */
    fun renderText(
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float? = null,
        fontSizePx: Float,
        fontWeight: Int = 400,
        fontStyle: ImageBufferFontStyle = ImageBufferFontStyle.NORMAL,
        fontStretch: ImageBufferFontStretch = ImageBufferFontStretch.NORMAL,
        fontVariantCaps: ImageBufferFontVariantCaps = ImageBufferFontVariantCaps.NORMAL,
        letterSpacingPx: Float = 0f,
        wordSpacingPx: Float = 0f,
        textAlign: ImageBufferTextAlign = ImageBufferTextAlign.START,
        textBaseline: ImageBufferTextBaseline = ImageBufferTextBaseline.ALPHABETIC,
        direction: ImageBufferTextDirection = ImageBufferTextDirection.INHERIT,
        transform: ImageBufferMatrix2D = ImageBufferMatrix2D.IDENTITY,
        strokeMode: Boolean = false,
        strokeLineWidth: Float = 1f,
        antialias: Boolean = true,
        shadowBlur: Float = 0f,
        shadowColor: RgbaColor = RgbaColor.TRANSPARENT,
        shadowOffsetX: Float = 0f,
        shadowOffsetY: Float = 0f,
        surfaceWidth: Int,
        surfaceHeight: Int,
        pixelBlender: (px: Int, py: Int, coverage: Float, isShadow: Boolean, localX: Float, localY: Float) -> Unit
    ) {
        if (text.isEmpty() || surfaceWidth <= 0 || surfaceHeight <= 0) return
        val safeSize = max(4f, fontSizePx)
        val metrics = measureText(
            text = text,
            fontSizePx = safeSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontStretch = fontStretch,
            fontVariantCaps = fontVariantCaps,
            letterSpacingPx = letterSpacingPx,
            wordSpacingPx = wordSpacingPx,
            textAlign = textAlign,
            textBaseline = textBaseline,
            direction = direction
        )

        val maxWidthCompression = if (maxWidth != null && maxWidth > 0f && metrics.width > maxWidth) {
            (maxWidth / metrics.width).coerceIn(0.15f, 1f)
        } else {
            1f
        }
        val effectiveWidth = metrics.width * maxWidthCompression
        val alignOffsetX = computeAlignOffsetX(effectiveWidth, textAlign, direction)
        val baselineAnchorY = computeBaselineAnchorY(safeSize, textBaseline)

        val scaleX = kotlin.math.hypot(transform.a, transform.b).let { if (it <= 1e-4f) 1f else it }
        val scaleY = kotlin.math.hypot(transform.c, transform.d).let { if (it <= 1e-4f) 1f else it }
        val pureTranslationOrScale = abs(transform.b) <= 1e-4f && abs(transform.c) <= 1e-4f && transform.a > 0f && transform.d > 0f
        val invTransform = if (!pureTranslationOrScale) transform.invert() else null

        val hasShadow = shadowColor.a > 0 && (shadowBlur > 0.05f || abs(shadowOffsetX) > 0.05f || abs(shadowOffsetY) > 0.05f)

        if (hasShadow) {
            renderGlyphPass(
                text = text,
                baseX = x + alignOffsetX,
                baseY = y - baselineAnchorY,
                safeSize = safeSize,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                fontStretch = fontStretch,
                fontVariantCaps = fontVariantCaps,
                letterSpacingPx = letterSpacingPx,
                wordSpacingPx = wordSpacingPx,
                maxWidthCompression = maxWidthCompression,
                transform = transform,
                invTransform = invTransform,
                pureTranslationOrScale = pureTranslationOrScale,
                scaleX = scaleX,
                scaleY = scaleY,
                strokeMode = strokeMode,
                strokeLineWidth = strokeLineWidth,
                antialias = antialias,
                isShadowPass = true,
                shadowOffsetX = shadowOffsetX,
                shadowOffsetY = shadowOffsetY,
                shadowBlur = shadowBlur,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
                pixelBlender = pixelBlender
            )
        }

        renderGlyphPass(
            text = text,
            baseX = x + alignOffsetX,
            baseY = y - baselineAnchorY,
            safeSize = safeSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontStretch = fontStretch,
            fontVariantCaps = fontVariantCaps,
            letterSpacingPx = letterSpacingPx,
            wordSpacingPx = wordSpacingPx,
            maxWidthCompression = maxWidthCompression,
            transform = transform,
            invTransform = invTransform,
            pureTranslationOrScale = pureTranslationOrScale,
            scaleX = scaleX,
            scaleY = scaleY,
            strokeMode = strokeMode,
            strokeLineWidth = strokeLineWidth,
            antialias = antialias,
            isShadowPass = false,
            shadowOffsetX = 0f,
            shadowOffsetY = 0f,
            shadowBlur = 0f,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            pixelBlender = pixelBlender
        )
    }

    private fun renderGlyphPass(
        text: String,
        baseX: Float,
        baseY: Float,
        safeSize: Float,
        fontWeight: Int,
        fontStyle: ImageBufferFontStyle,
        fontStretch: ImageBufferFontStretch,
        fontVariantCaps: ImageBufferFontVariantCaps,
        letterSpacingPx: Float,
        wordSpacingPx: Float,
        maxWidthCompression: Float,
        transform: ImageBufferMatrix2D,
        invTransform: ImageBufferMatrix2D?,
        pureTranslationOrScale: Boolean,
        scaleX: Float,
        scaleY: Float,
        strokeMode: Boolean,
        strokeLineWidth: Float,
        antialias: Boolean,
        isShadowPass: Boolean,
        shadowOffsetX: Float,
        shadowOffsetY: Float,
        shadowBlur: Float,
        surfaceWidth: Int,
        surfaceHeight: Int,
        pixelBlender: (px: Int, py: Int, coverage: Float, isShadow: Boolean, localX: Float, localY: Float) -> Unit
    ) {
        val totalStretch = fontStretch.widthScale * maxWidthCompression
        var cursorLocalX = baseX

        for (i in text.indices) {
            val rawChar = text[i]
            val (effectiveChar, capScale) = resolveVariantCap(rawChar, fontVariantCaps)
            val glyph = glyphFor(effectiveChar)
            val charSizeLocal = safeSize * capScale
            val charAdvanceLocal = glyph.advance * charSizeLocal * totalStretch
            val yCapOffsetLocal = (1f - capScale) * BASELINE_Y * safeSize

            if (glyph.segments.isNotEmpty()) {
                if (pureTranslationOrScale && !isShadowPass) {
                    val screenX = transform.transformX(cursorLocalX, baseY + yCapOffsetLocal)
                    val screenY = transform.transformY(cursorLocalX, baseY + yCapOffsetLocal)
                    val screenSizeY = charSizeLocal * transform.d
                    val screenStretch = totalStretch * (transform.a / max(1e-4f, transform.d))
                    val mask = getOrRasterizeGlyphMask(
                        codePoint = effectiveChar.code,
                        glyph = glyph,
                        sizePx = screenSizeY,
                        fontWeight = fontWeight,
                        fontStyle = fontStyle,
                        stretchScale = screenStretch,
                        strokeMode = strokeMode,
                        strokeLineWidth = strokeLineWidth * transform.d,
                        antialias = antialias
                    )
                    val dstX0 = screenX.roundToInt() + mask.originX
                    val dstY0 = screenY.roundToInt() + mask.originY
                    val invA = 1f / max(1e-4f, transform.a)
                    val invD = 1f / max(1e-4f, transform.d)
                    for (my in 0 until mask.height) {
                        val py = dstY0 + my
                        if (py !in 0 until surfaceHeight) continue
                        val rowIdx = my * mask.width
                        val localY = ((py + 0.5f) - transform.f) * invD
                        for (mx in 0 until mask.width) {
                            val px = dstX0 + mx
                            if (px !in 0 until surfaceWidth) continue
                            val covByte = mask.coverage[rowIdx + mx].toInt() and 0xFF
                            if (covByte > 0) {
                                val localX = ((px + 0.5f) - transform.e) * invA
                                pixelBlender(px, py, covByte / 255f, false, localX, localY)
                            }
                        }
                    }
                } else {
                    renderGlyphAnalytical(
                        glyph = glyph,
                        glyphOriginLocalX = cursorLocalX,
                        glyphOriginLocalY = baseY + yCapOffsetLocal,
                        charSizeLocal = charSizeLocal,
                        totalStretch = totalStretch,
                        fontWeight = fontWeight,
                        fontStyle = fontStyle,
                        transform = transform,
                        invTransform = invTransform ?: transform.invert() ?: ImageBufferMatrix2D.IDENTITY,
                        scaleY = scaleY,
                        strokeMode = strokeMode,
                        strokeLineWidth = strokeLineWidth,
                        antialias = antialias,
                        isShadowPass = isShadowPass,
                        shadowOffsetX = shadowOffsetX,
                        shadowOffsetY = shadowOffsetY,
                        shadowBlur = shadowBlur,
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight,
                        pixelBlender = pixelBlender
                    )
                }
            }

            cursorLocalX += charAdvanceLocal
            if (i + 1 < text.length) {
                cursorLocalX += letterSpacingPx * maxWidthCompression
            }
            if (rawChar == ' ') {
                cursorLocalX += wordSpacingPx * maxWidthCompression
            }
        }
    }

    private fun renderGlyphAnalytical(
        glyph: GlyphOutline,
        glyphOriginLocalX: Float,
        glyphOriginLocalY: Float,
        charSizeLocal: Float,
        totalStretch: Float,
        fontWeight: Int,
        fontStyle: ImageBufferFontStyle,
        transform: ImageBufferMatrix2D,
        invTransform: ImageBufferMatrix2D,
        scaleY: Float,
        strokeMode: Boolean,
        strokeLineWidth: Float,
        antialias: Boolean,
        isShadowPass: Boolean,
        shadowOffsetX: Float,
        shadowOffsetY: Float,
        shadowBlur: Float,
        surfaceWidth: Int,
        surfaceHeight: Int,
        pixelBlender: (px: Int, py: Int, coverage: Float, isShadow: Boolean, localX: Float, localY: Float) -> Unit
    ) {
        val shear = when (fontStyle) {
            ImageBufferFontStyle.ITALIC -> 0.18f
            ImageBufferFontStyle.OBLIQUE -> 0.22f
            ImageBufferFontStyle.NORMAL -> 0f
        }
        val strokeRadiusEm = strokeRadiusEmForWeight(fontWeight)
        val blurNorm = if (isShadowPass) max(0f, shadowBlur) / max(1f, charSizeLocal * scaleY) else 0f
        val featherNorm = (if (antialias) 0.72f / max(1f, charSizeLocal * scaleY) else 0.005f) + blurNorm * 0.85f
        val padNorm = strokeRadiusEm + featherNorm + 0.12f

        val lx0 = glyphOriginLocalX + (glyph.minX - padNorm - 0.25f) * charSizeLocal * totalStretch
        val lx1 = glyphOriginLocalX + (glyph.maxX + padNorm + 0.25f) * charSizeLocal * totalStretch
        val ly0 = glyphOriginLocalY + (glyph.minY - padNorm) * charSizeLocal
        val ly1 = glyphOriginLocalY + (glyph.maxY + padNorm) * charSizeLocal

        val c0x = transform.transformX(lx0, ly0) + shadowOffsetX
        val c0y = transform.transformY(lx0, ly0) + shadowOffsetY
        val c1x = transform.transformX(lx1, ly0) + shadowOffsetX
        val c1y = transform.transformY(lx1, ly0) + shadowOffsetY
        val c2x = transform.transformX(lx1, ly1) + shadowOffsetX
        val c2y = transform.transformY(lx1, ly1) + shadowOffsetY
        val c3x = transform.transformX(lx0, ly1) + shadowOffsetX
        val c3y = transform.transformY(lx0, ly1) + shadowOffsetY

        val ix0 = floor(min(min(c0x, c1x), min(c2x, c3x)) - 1f).toInt().coerceIn(0, surfaceWidth)
        val iy0 = floor(min(min(c0y, c1y), min(c2y, c3y)) - 1f).toInt().coerceIn(0, surfaceHeight)
        val ix1 = ceil(max(max(c0x, c1x), max(c2x, c3x)) + 1f).toInt().coerceIn(0, surfaceWidth)
        val iy1 = ceil(max(max(c0y, c1y), max(c2y, c3y)) + 1f).toInt().coerceIn(0, surfaceHeight)
        if (ix1 <= ix0 || iy1 <= iy0) return

        val invCharW = 1f / max(1e-4f, charSizeLocal * totalStretch)
        val invCharH = 1f / max(1e-4f, charSizeLocal)
        val outlineHalfNorm = (strokeLineWidth * 0.5f) * invCharH

        for (py in iy0 until iy1) {
            val sy = py + 0.5f - shadowOffsetY
            for (px in ix0 until ix1) {
                val sx = px + 0.5f - shadowOffsetX
                val localX = invTransform.transformX(sx, sy)
                val localY = invTransform.transformY(sx, sy)
                val normY = (localY - glyphOriginLocalY) * invCharH
                val rawNormX = (localX - glyphOriginLocalX) * invCharW
                val normX = rawNormX - (BASELINE_Y - normY) * shear

                if (normX < glyph.minX - padNorm || normX > glyph.maxX + padNorm ||
                    normY < glyph.minY - padNorm || normY > glyph.maxY + padNorm
                ) {
                    continue
                }

                val dist = minDistanceToSegments(normX, normY, glyph.segments)
                val cov = if (strokeMode && !isShadowPass) {
                    val ringDist = abs(dist - strokeRadiusEm * 0.65f)
                    smoothCoverage(ringDist, max(0.018f, outlineHalfNorm), featherNorm)
                } else {
                    smoothCoverage(dist, strokeRadiusEm + blurNorm * 0.25f, featherNorm)
                }
                if (cov > 0.004f) {
                    pixelBlender(px, py, cov, isShadowPass, localX, localY)
                }
            }
        }
    }

    private fun getOrRasterizeGlyphMask(
        codePoint: Int,
        glyph: GlyphOutline,
        sizePx: Float,
        fontWeight: Int,
        fontStyle: ImageBufferFontStyle,
        stretchScale: Float,
        strokeMode: Boolean,
        strokeLineWidth: Float,
        antialias: Boolean
    ): RasterizedGlyphMask {
        val heightDeci = (sizePx * 4f).roundToInt().coerceIn(16, 4096)
        val quantizedSize = heightDeci / 4f
        val weightClass = ((fontWeight + 50) / 100).coerceIn(1, 9) * 100
        val stretchPermille = (stretchScale * 1000f).roundToInt().coerceIn(250, 4000)
        val strokeDeci = (strokeLineWidth * 4f).roundToInt().coerceIn(2, 400)
        val key = GlyphCacheKey(
            codePoint = codePoint,
            heightDeciPx = heightDeci,
            weightClass = weightClass,
            styleCode = fontStyle.gpuCode,
            stretchPermille = stretchPermille,
            strokeMode = strokeMode,
            strokeWidthDeciPx = if (strokeMode) strokeDeci else 0,
            antialias = antialias
        )
        maskCache[key]?.let { return it }

        val shear = when (fontStyle) {
            ImageBufferFontStyle.ITALIC -> 0.18f
            ImageBufferFontStyle.OBLIQUE -> 0.22f
            ImageBufferFontStyle.NORMAL -> 0f
        }
        val strokeRadiusEm = strokeRadiusEmForWeight(weightClass)
        val featherNorm = if (antialias) 0.68f / max(1f, quantizedSize) else 0.004f
        val padNorm = strokeRadiusEm + featherNorm + 0.08f
        val charW = max(1f, quantizedSize * (stretchPermille / 1000f))
        val charH = max(1f, quantizedSize)

        val minPx = floor((glyph.minX - padNorm - abs(shear) * 0.4f) * charW).toInt()
        val maxPx = ceil((glyph.maxX + padNorm + abs(shear) * 0.8f) * charW).toInt()
        val minPy = floor((glyph.minY - padNorm) * charH).toInt()
        val maxPy = ceil((glyph.maxY + padNorm) * charH).toInt()

        val maskW = max(1, maxPx - minPx)
        val maskH = max(1, maxPy - minPy)
        val coverage = ByteArray(maskW * maskH)
        val invW = 1f / charW
        val invH = 1f / charH
        val outlineHalfNorm = ((strokeDeci / 4f) * 0.5f) * invH

        for (my in 0 until maskH) {
            val normY = (minPy + my + 0.5f) * invH
            val shearOffset = (BASELINE_Y - normY) * shear
            val rowOffset = my * maskW
            for (mx in 0 until maskW) {
                val rawNormX = (minPx + mx + 0.5f) * invW
                val normX = rawNormX - shearOffset
                val dist = minDistanceToSegments(normX, normY, glyph.segments)
                val cov = if (strokeMode) {
                    val ringDist = abs(dist - strokeRadiusEm * 0.65f)
                    smoothCoverage(ringDist, max(0.018f, outlineHalfNorm), featherNorm)
                } else {
                    smoothCoverage(dist, strokeRadiusEm, featherNorm)
                }
                if (cov > 0.004f) {
                    coverage[rowOffset + mx] = (cov * 255f).roundToInt().coerceIn(0, 255).toByte()
                }
            }
        }

        val mask = RasterizedGlyphMask(
            originX = minPx,
            originY = minPy,
            width = maskW,
            height = maskH,
            coverage = coverage
        )
        if (maskCache.size >= 512) {
            val firstKey = maskCache.keys.firstOrNull()
            if (firstKey != null) maskCache.remove(firstKey)
        }
        maskCache[key] = mask
        return mask
    }

    private fun minDistanceToSegments(px: Float, py: Float, segs: FloatArray): Float {
        var minDistSq = Float.POSITIVE_INFINITY
        var i = 0
        while (i + 3 < segs.size) {
            val ax = segs[i]
            val ay = segs[i + 1]
            val bx = segs[i + 2]
            val by = segs[i + 3]
            val baX = bx - ax
            val baY = by - ay
            val paX = px - ax
            val paY = py - ay
            val lenSq = baX * baX + baY * baY
            val h = if (lenSq > 1e-6f) {
                ((paX * baX + paY * baY) / lenSq).coerceIn(0f, 1f)
            } else {
                0f
            }
            val dx = paX - baX * h
            val dy = paY - baY * h
            val dSq = dx * dx + dy * dy
            if (dSq < minDistSq) {
                minDistSq = dSq
            }
            i += 4
        }
        return sqrt(minDistSq)
    }

    private fun smoothCoverage(dist: Float, radius: Float, feather: Float): Float {
        val inner = radius - feather
        val outer = radius + feather
        if (dist <= inner) return 1f
        if (dist >= outer) return 0f
        val t = ((dist - inner) / max(1e-5f, outer - inner)).coerceIn(0f, 1f)
        return 1f - t * t * (3f - 2f * t)
    }

    private fun strokeRadiusEmForWeight(fontWeight: Int): Float {
        val w = fontWeight.coerceIn(100, 900)
        return 0.036f + ((w - 100f) / 800f) * 0.044f
    }

    private fun resolveVariantCap(ch: Char, variantCaps: ImageBufferFontVariantCaps): Pair<Char, Float> {
        return when (variantCaps) {
            ImageBufferFontVariantCaps.SMALL_CAPS, ImageBufferFontVariantCaps.PETITE_CAPS -> {
                if (ch in 'a'..'z') ch.uppercaseChar() to 0.78f else ch to 1f
            }
            ImageBufferFontVariantCaps.ALL_SMALL_CAPS, ImageBufferFontVariantCaps.ALL_PETITE_CAPS -> {
                if (ch.isLetter()) ch.uppercaseChar() to 0.78f else ch to 1f
            }
            ImageBufferFontVariantCaps.UNICASE -> {
                if (ch in 'a'..'z') ch.uppercaseChar() to 0.82f else ch to 1f
            }
            else -> ch to 1f
        }
    }

    private fun computeAlignOffsetX(
        width: Float,
        align: ImageBufferTextAlign,
        direction: ImageBufferTextDirection
    ): Float {
        val isRtl = direction == ImageBufferTextDirection.RTL
        return when (align) {
            ImageBufferTextAlign.LEFT -> 0f
            ImageBufferTextAlign.RIGHT -> -width
            ImageBufferTextAlign.CENTER -> -width * 0.5f
            ImageBufferTextAlign.START -> if (isRtl) -width else 0f
            ImageBufferTextAlign.END -> if (isRtl) 0f else -width
        }
    }

    private fun computeBaselineAnchorY(
        fontSizePx: Float,
        baseline: ImageBufferTextBaseline
    ): Float {
        return when (baseline) {
            ImageBufferTextBaseline.TOP -> CAP_TOP_Y * fontSizePx * 0.5f
            ImageBufferTextBaseline.HANGING -> CAP_TOP_Y * fontSizePx
            ImageBufferTextBaseline.MIDDLE -> MID_CAP_Y * fontSizePx
            ImageBufferTextBaseline.ALPHABETIC -> BASELINE_Y * fontSizePx
            ImageBufferTextBaseline.IDEOGRAPHIC -> DESCENDER_Y * fontSizePx * 0.94f
            ImageBufferTextBaseline.BOTTOM -> DESCENDER_Y * fontSizePx
        }
    }

    private fun buildFallbackGlyph(): GlyphOutline =
        GlyphBuilder(0.56f)
            .polyline(0.10f, CAP_TOP_Y, 0.46f, CAP_TOP_Y, 0.46f, BASELINE_Y, 0.10f, BASELINE_Y, 0.10f, CAP_TOP_Y)
            .build()

    private fun buildGlyphTable(): Map<Int, GlyphOutline> {
        val m = HashMap<Int, GlyphOutline>(128)
        val t = CAP_TOP_Y
        val xh = X_HEIGHT_Y
        val mid = MID_CAP_Y
        val b = BASELINE_Y
        val desc = DESCENDER_Y
        val xMid = (xh + b) * 0.5f

        fun put(ch: Char, advance: Float, block: GlyphBuilder.() -> Unit) {
            val gb = GlyphBuilder(advance)
            gb.block()
            m[ch.code] = gb.build()
        }

        put(' ', 0.32f) {}

        put('A', 0.64f) {
            line(0.08f, b, 0.32f, t)
            line(0.32f, t, 0.56f, b)
            line(0.16f, 0.64f, 0.48f, 0.64f)
        }
        put('B', 0.62f) {
            line(0.12f, t, 0.12f, b)
            line(0.12f, t, 0.36f, t)
            arc(0.36f, 0.32f, 0.16f, 0.18f, -90f, 90f)
            line(0.36f, mid, 0.12f, mid)
            line(0.12f, mid, 0.38f, mid)
            arc(0.38f, 0.68f, 0.17f, 0.18f, -90f, 90f)
            line(0.38f, b, 0.12f, b)
        }
        put('C', 0.62f) {
            arc(0.34f, mid, 0.24f, 0.36f, 35f, 325f, 14)
        }
        put('D', 0.64f) {
            line(0.12f, t, 0.12f, b)
            line(0.12f, t, 0.32f, t)
            arc(0.32f, mid, 0.24f, 0.36f, -90f, 90f, 12)
            line(0.32f, b, 0.12f, b)
        }
        put('E', 0.56f) {
            line(0.12f, t, 0.12f, b)
            line(0.12f, t, 0.48f, t)
            line(0.12f, mid, 0.43f, mid)
            line(0.12f, b, 0.48f, b)
        }
        put('F', 0.54f) {
            line(0.12f, t, 0.12f, b)
            line(0.12f, t, 0.46f, t)
            line(0.12f, mid, 0.41f, mid)
        }
        put('G', 0.66f) {
            arc(0.35f, mid, 0.25f, 0.36f, 20f, 325f, 14)
            line(0.60f, mid, 0.60f, 0.64f)
            line(0.36f, mid, 0.60f, mid)
        }
        put('H', 0.64f) {
            line(0.12f, t, 0.12f, b)
            line(0.52f, t, 0.52f, b)
            line(0.12f, mid, 0.52f, mid)
        }
        put('I', 0.34f) {
            line(0.17f, t, 0.17f, b)
            line(0.08f, t, 0.26f, t)
            line(0.08f, b, 0.26f, b)
        }
        put('J', 0.52f) {
            line(0.40f, t, 0.40f, 0.66f)
            arc(0.25f, 0.66f, 0.15f, 0.20f, 0f, 175f, 10)
        }
        put('K', 0.62f) {
            line(0.12f, t, 0.12f, b)
            line(0.50f, t, 0.12f, 0.54f)
            line(0.24f, 0.44f, 0.52f, b)
        }
        put('L', 0.54f) {
            line(0.12f, t, 0.12f, b)
            line(0.12f, b, 0.46f, b)
        }
        put('M', 0.74f) {
            line(0.10f, b, 0.10f, t)
            line(0.10f, t, 0.37f, 0.72f)
            line(0.37f, 0.72f, 0.64f, t)
            line(0.64f, t, 0.64f, b)
        }
        put('N', 0.66f) {
            line(0.12f, b, 0.12f, t)
            line(0.12f, t, 0.54f, b)
            line(0.54f, b, 0.54f, t)
        }
        put('O', 0.68f) {
            arc(0.34f, mid, 0.24f, 0.36f, 0f, 360f, 16)
        }
        put('P', 0.60f) {
            line(0.12f, t, 0.12f, b)
            line(0.12f, t, 0.35f, t)
            arc(0.35f, 0.33f, 0.17f, 0.19f, -90f, 90f, 10)
            line(0.35f, 0.52f, 0.12f, 0.52f)
        }
        put('Q', 0.68f) {
            arc(0.34f, mid, 0.24f, 0.36f, 0f, 360f, 16)
            line(0.38f, 0.66f, 0.58f, 0.92f)
        }
        put('R', 0.62f) {
            line(0.12f, t, 0.12f, b)
            line(0.12f, t, 0.35f, t)
            arc(0.35f, 0.33f, 0.17f, 0.19f, -90f, 90f, 10)
            line(0.35f, 0.52f, 0.12f, 0.52f)
            line(0.32f, 0.52f, 0.52f, b)
        }
        put('S', 0.58f) {
            arc(0.30f, 0.32f, 0.18f, 0.18f, 150f, 340f, 10)
            line(0.20f, 0.44f, 0.38f, 0.55f)
            arc(0.29f, 0.68f, 0.19f, 0.18f, -30f, 165f, 10)
        }
        put('T', 0.58f) {
            line(0.06f, t, 0.52f, t)
            line(0.29f, t, 0.29f, b)
        }
        put('U', 0.64f) {
            line(0.12f, t, 0.12f, 0.64f)
            arc(0.32f, 0.64f, 0.20f, 0.22f, 180f, 0f, 10)
            line(0.52f, 0.64f, 0.52f, t)
        }
        put('V', 0.62f) {
            line(0.08f, t, 0.31f, b)
            line(0.31f, b, 0.54f, t)
        }
        put('W', 0.80f) {
            line(0.06f, t, 0.22f, b)
            line(0.22f, b, 0.40f, 0.36f)
            line(0.40f, 0.36f, 0.58f, b)
            line(0.58f, b, 0.74f, t)
        }
        put('X', 0.62f) {
            line(0.10f, t, 0.52f, b)
            line(0.52f, t, 0.10f, b)
        }
        put('Y', 0.60f) {
            line(0.08f, t, 0.30f, mid)
            line(0.52f, t, 0.30f, mid)
            line(0.30f, mid, 0.30f, b)
        }
        put('Z', 0.58f) {
            line(0.10f, t, 0.48f, t)
            line(0.48f, t, 0.10f, b)
            line(0.10f, b, 0.48f, b)
        }

        put('a', 0.54f) {
            arc(0.26f, 0.48f, 0.15f, 0.12f, 200f, 360f, 8)
            line(0.41f, 0.48f, 0.41f, b)
            arc(0.25f, 0.72f, 0.16f, 0.14f, 0f, 360f, 12)
        }
        put('b', 0.56f) {
            line(0.11f, t, 0.11f, b)
            arc(0.28f, xMid, 0.17f, 0.25f, 0f, 360f, 14)
        }
        put('c', 0.50f) {
            arc(0.28f, xMid, 0.17f, 0.25f, 40f, 320f, 12)
        }
        put('d', 0.56f) {
            line(0.45f, t, 0.45f, b)
            arc(0.28f, xMid, 0.17f, 0.25f, 0f, 360f, 14)
        }
        put('e', 0.54f) {
            line(0.10f, xMid, 0.44f, xMid)
            arc(0.27f, xMid, 0.17f, 0.25f, 35f, 360f, 14)
        }
        put('f', 0.36f) {
            arc(0.26f, 0.25f, 0.09f, 0.11f, 180f, 320f, 6)
            line(0.17f, 0.25f, 0.17f, b)
            line(0.07f, xh, 0.30f, xh)
        }
        put('g', 0.56f) {
            arc(0.27f, xMid, 0.16f, 0.24f, 0f, 360f, 14)
            line(0.43f, xh, 0.43f, 0.94f)
            arc(0.27f, 0.94f, 0.16f, 0.16f, 0f, 165f, 8)
        }
        put('h', 0.56f) {
            line(0.11f, t, 0.11f, b)
            arc(0.27f, 0.52f, 0.16f, 0.16f, 180f, 360f, 8)
            line(0.43f, 0.52f, 0.43f, b)
        }
        put('i', 0.26f) {
            dot(0.13f, 0.20f, 0.018f)
            line(0.13f, xh, 0.13f, b)
        }
        put('j', 0.28f) {
            dot(0.17f, 0.20f, 0.018f)
            line(0.17f, xh, 0.17f, 0.96f)
            arc(0.09f, 0.96f, 0.08f, 0.12f, 0f, 160f, 6)
        }
        put('k', 0.52f) {
            line(0.11f, t, 0.11f, b)
            line(0.42f, xh, 0.11f, 0.64f)
            line(0.22f, 0.55f, 0.44f, b)
        }
        put('l', 0.26f) {
            line(0.13f, t, 0.13f, b)
        }
        put('m', 0.80f) {
            line(0.10f, xh, 0.10f, b)
            arc(0.24f, 0.52f, 0.14f, 0.16f, 180f, 360f, 8)
            line(0.38f, 0.52f, 0.38f, b)
            arc(0.52f, 0.52f, 0.14f, 0.16f, 180f, 360f, 8)
            line(0.66f, 0.52f, 0.66f, b)
        }
        put('n', 0.56f) {
            line(0.11f, xh, 0.11f, b)
            arc(0.27f, 0.52f, 0.16f, 0.16f, 180f, 360f, 8)
            line(0.43f, 0.52f, 0.43f, b)
        }
        put('o', 0.56f) {
            arc(0.28f, xMid, 0.18f, 0.25f, 0f, 360f, 14)
        }
        put('p', 0.56f) {
            line(0.11f, xh, 0.11f, desc)
            arc(0.28f, xMid, 0.17f, 0.25f, 0f, 360f, 14)
        }
        put('q', 0.56f) {
            line(0.45f, xh, 0.45f, desc)
            arc(0.28f, xMid, 0.17f, 0.25f, 0f, 360f, 14)
        }
        put('r', 0.38f) {
            line(0.11f, xh, 0.11f, b)
            arc(0.24f, 0.50f, 0.13f, 0.14f, 180f, 310f, 6)
        }
        put('s', 0.48f) {
            arc(0.25f, 0.48f, 0.14f, 0.12f, 150f, 340f, 8)
            line(0.16f, 0.56f, 0.33f, 0.64f)
            arc(0.24f, 0.74f, 0.15f, 0.12f, -30f, 165f, 8)
        }
        put('t', 0.36f) {
            line(0.17f, 0.22f, 0.17f, 0.76f)
            arc(0.24f, 0.76f, 0.07f, 0.10f, 180f, 60f, 6)
            line(0.07f, xh, 0.30f, xh)
        }
        put('u', 0.56f) {
            line(0.11f, xh, 0.11f, 0.70f)
            arc(0.27f, 0.70f, 0.16f, 0.16f, 180f, 0f, 8)
            line(0.43f, xh, 0.43f, b)
        }
        put('v', 0.50f) {
            line(0.08f, xh, 0.25f, b)
            line(0.25f, b, 0.42f, xh)
        }
        put('w', 0.72f) {
            line(0.06f, xh, 0.20f, b)
            line(0.20f, b, 0.36f, 0.52f)
            line(0.36f, 0.52f, 0.52f, b)
            line(0.52f, b, 0.66f, xh)
        }
        put('x', 0.50f) {
            line(0.09f, xh, 0.41f, b)
            line(0.41f, xh, 0.09f, b)
        }
        put('y', 0.52f) {
            line(0.08f, xh, 0.26f, b)
            line(0.44f, xh, 0.16f, desc)
        }
        put('z', 0.48f) {
            line(0.09f, xh, 0.39f, xh)
            line(0.39f, xh, 0.09f, b)
            line(0.09f, b, 0.39f, b)
        }

        put('0', 0.58f) {
            arc(0.29f, mid, 0.19f, 0.36f, 0f, 360f, 16)
        }
        put('1', 0.54f) {
            line(0.16f, 0.28f, 0.29f, t)
            line(0.29f, t, 0.29f, b)
            line(0.16f, b, 0.42f, b)
        }
        put('2', 0.58f) {
            arc(0.29f, 0.32f, 0.18f, 0.18f, 195f, 35f, 10)
            line(0.44f, 0.42f, 0.10f, b)
            line(0.10f, b, 0.48f, b)
        }
        put('3', 0.58f) {
            arc(0.28f, 0.32f, 0.17f, 0.18f, 200f, 90f, 10)
            arc(0.28f, 0.68f, 0.19f, 0.18f, -90f, 155f, 10)
        }
        put('4', 0.58f) {
            line(0.38f, t, 0.08f, 0.64f)
            line(0.08f, 0.64f, 0.50f, 0.64f)
            line(0.38f, t, 0.38f, b)
        }
        put('5', 0.58f) {
            line(0.45f, t, 0.13f, t)
            line(0.13f, t, 0.11f, 0.48f)
            arc(0.28f, 0.65f, 0.19f, 0.21f, -115f, 150f, 12)
        }
        put('6', 0.58f) {
            arc(0.31f, mid, 0.19f, 0.36f, 150f, 325f, 10)
            arc(0.29f, 0.65f, 0.19f, 0.21f, 0f, 360f, 14)
        }
        put('7', 0.58f) {
            line(0.09f, t, 0.49f, t)
            line(0.49f, t, 0.21f, b)
        }
        put('8', 0.58f) {
            arc(0.29f, 0.31f, 0.17f, 0.17f, 0f, 360f, 12)
            arc(0.29f, 0.67f, 0.19f, 0.19f, 0f, 360f, 12)
        }
        put('9', 0.58f) {
            arc(0.29f, 0.35f, 0.19f, 0.21f, 0f, 360f, 14)
            arc(0.27f, mid, 0.19f, 0.36f, -30f, 145f, 10)
        }

        put('!', 0.30f) {
            line(0.15f, t, 0.15f, 0.66f)
            dot(0.15f, b, 0.02f)
        }
        put('"', 0.38f) {
            line(0.12f, t, 0.12f, 0.34f)
            line(0.26f, t, 0.26f, 0.34f)
        }
        put('#', 0.60f) {
            line(0.22f, t, 0.16f, b)
            line(0.44f, t, 0.38f, b)
            line(0.08f, 0.38f, 0.52f, 0.38f)
            line(0.06f, 0.62f, 0.50f, 0.62f)
        }
        put('$', 0.58f) {
            arc(0.30f, 0.33f, 0.17f, 0.16f, 150f, 340f, 8)
            line(0.20f, 0.44f, 0.38f, 0.55f)
            arc(0.29f, 0.67f, 0.18f, 0.16f, -30f, 165f, 8)
            line(0.29f, 0.06f, 0.29f, 0.94f)
        }
        put('%', 0.72f) {
            arc(0.19f, 0.29f, 0.10f, 0.13f, 0f, 360f, 10)
            line(0.56f, t, 0.16f, b)
            arc(0.53f, 0.71f, 0.10f, 0.13f, 0f, 360f, 10)
        }
        put('&', 0.66f) {
            arc(0.29f, 0.29f, 0.13f, 0.14f, 0f, 360f, 10)
            arc(0.28f, 0.66f, 0.18f, 0.20f, 45f, 315f, 10)
            line(0.18f, 0.40f, 0.56f, b)
        }
        put('\'', 0.26f) {
            line(0.13f, t, 0.13f, 0.34f)
        }
        put('(', 0.36f) {
            quad(0.26f, 0.08f, 0.08f, mid, 0.26f, 0.98f, 8)
        }
        put(')', 0.36f) {
            quad(0.10f, 0.08f, 0.28f, mid, 0.10f, 0.98f, 8)
        }
        put('*', 0.46f) {
            line(0.23f, 0.18f, 0.23f, 0.52f)
            line(0.08f, 0.26f, 0.38f, 0.44f)
            line(0.38f, 0.26f, 0.08f, 0.44f)
        }
        put('+', 0.58f) {
            line(0.29f, 0.30f, 0.29f, 0.74f)
            line(0.09f, 0.52f, 0.49f, 0.52f)
        }
        put(',', 0.28f) {
            line(0.15f, 0.82f, 0.10f, 0.98f)
        }
        put('-', 0.42f) {
            line(0.08f, 0.54f, 0.34f, 0.54f)
        }
        put('.', 0.28f) {
            dot(0.14f, b, 0.02f)
        }
        put('/', 0.42f) {
            line(0.34f, t, 0.08f, b)
        }
        put(':', 0.28f) {
            dot(0.14f, 0.46f, 0.02f)
            dot(0.14f, b, 0.02f)
        }
        put(';', 0.28f) {
            dot(0.14f, 0.46f, 0.02f)
            line(0.15f, 0.82f, 0.10f, 0.98f)
        }
        put('<', 0.56f) {
            line(0.46f, 0.30f, 0.10f, 0.52f)
            line(0.10f, 0.52f, 0.46f, 0.74f)
        }
        put('=', 0.56f) {
            line(0.09f, 0.42f, 0.47f, 0.42f)
            line(0.09f, 0.62f, 0.47f, 0.62f)
        }
        put('>', 0.56f) {
            line(0.10f, 0.30f, 0.46f, 0.52f)
            line(0.46f, 0.52f, 0.10f, 0.74f)
        }
        put('?', 0.52f) {
            arc(0.26f, 0.30f, 0.16f, 0.16f, 190f, 65f, 10)
            line(0.32f, 0.45f, 0.26f, 0.64f)
            dot(0.26f, b, 0.02f)
        }
        put('@', 0.78f) {
            arc(0.39f, mid, 0.28f, 0.34f, 25f, 335f, 16)
            arc(0.39f, mid, 0.12f, 0.15f, 0f, 360f, 10)
            line(0.51f, 0.35f, 0.51f, 0.65f)
        }
        put('[', 0.34f) {
            polyline(0.25f, 0.10f, 0.11f, 0.10f, 0.11f, 0.96f, 0.25f, 0.96f)
        }
        put('\\', 0.42f) {
            line(0.08f, t, 0.34f, b)
        }
        put(']', 0.34f) {
            polyline(0.09f, 0.10f, 0.23f, 0.10f, 0.23f, 0.96f, 0.09f, 0.96f)
        }
        put('^', 0.52f) {
            line(0.10f, 0.42f, 0.26f, t)
            line(0.26f, t, 0.42f, 0.42f)
        }
        put('_', 0.52f) {
            line(0.04f, 0.96f, 0.48f, 0.96f)
        }
        put('`', 0.32f) {
            line(0.11f, t, 0.21f, 0.28f)
        }
        put('{', 0.38f) {
            polyline(0.27f, 0.10f, 0.19f, 0.16f, 0.19f, 0.46f, 0.11f, 0.53f, 0.19f, 0.60f, 0.19f, 0.90f, 0.27f, 0.96f)
        }
        put('|', 0.28f) {
            line(0.14f, 0.08f, 0.14f, 0.98f)
        }
        put('}', 0.38f) {
            polyline(0.11f, 0.10f, 0.19f, 0.16f, 0.19f, 0.46f, 0.27f, 0.53f, 0.19f, 0.60f, 0.19f, 0.90f, 0.11f, 0.96f)
        }
        put('~', 0.58f) {
            quad(0.08f, 0.54f, 0.19f, 0.38f, 0.29f, 0.52f, 6)
            quad(0.29f, 0.52f, 0.39f, 0.66f, 0.50f, 0.50f, 6)
        }

        put('•', 0.44f) {
            arc(0.22f, 0.52f, 0.07f, 0.07f, 0f, 360f, 8)
            dot(0.22f, 0.52f, 0.02f)
        }
        put('▶', 0.56f) {
            polyline(0.12f, 0.26f, 0.46f, 0.52f, 0.12f, 0.78f, 0.12f, 0.26f)
        }
        put('▼', 0.56f) {
            polyline(0.10f, 0.34f, 0.46f, 0.34f, 0.28f, 0.74f, 0.10f, 0.34f)
        }
        put('◀', 0.56f) {
            polyline(0.44f, 0.26f, 0.10f, 0.52f, 0.44f, 0.78f, 0.44f, 0.26f)
        }
        put('▲', 0.56f) {
            polyline(0.10f, 0.74f, 0.46f, 0.74f, 0.28f, 0.30f, 0.10f, 0.74f)
        }
        put('✓', 0.58f) {
            line(0.10f, 0.56f, 0.24f, 0.78f)
            line(0.24f, 0.78f, 0.48f, 0.24f)
        }
        put('×', 0.56f) {
            line(0.12f, 0.32f, 0.44f, 0.72f)
            line(0.44f, 0.32f, 0.12f, 0.72f)
        }
        put('—', 0.72f) {
            line(0.06f, 0.54f, 0.66f, 0.54f)
        }
        put('–', 0.54f) {
            line(0.07f, 0.54f, 0.47f, 0.54f)
        }
        put('…', 0.72f) {
            dot(0.14f, b, 0.02f)
            dot(0.36f, b, 0.02f)
            dot(0.58f, b, 0.02f)
        }
        put('Δ', 0.62f) {
            polyline(0.08f, b, 0.31f, t, 0.54f, b, 0.08f, b)
        }

        return m
    }
}
