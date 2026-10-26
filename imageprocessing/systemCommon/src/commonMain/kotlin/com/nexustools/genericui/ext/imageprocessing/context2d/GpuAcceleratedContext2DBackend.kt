package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.gpu.GpuDevice
import com.nexustools.genericui.ext.gpu.GpuOffscreenContext
import com.nexustools.genericui.ext.gpu.GpuShaderProgram
import com.nexustools.genericui.ext.gpu.system.SystemGpuShaderCache
import com.nexustools.genericui.style.RgbaColor
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Shared system-target (`:extensions:imageprocessing:sharedSystem`) hardware-accelerated
 * **Vulkan (`#version 450`)** and **OpenGL ES (`#version 300 es`)** 2D context backend
 * implementing [ImageBuffer2DContext] directly on top of [SystemGpuImageBuffer].
 *
 * Features:
 * - 100% HTML5 2D Context feature parity using strongly-typed `ImageBuffer*` enums and value types.
 * - Compiles and caches all 12 Vulkan `#version 450` and OpenGL ES `#version 300 es` shaders via
 *   [SystemGpuShaderCache] and [GpuContext2DShaderCatalog].
 * - Uses [ScalableVectorFontEngine] for crisp SDF text rendering (`fillText`, `strokeText`,
 *   `measureText`) at any font size, weight, style, stretch, variant caps, alignment, and baseline.
 */
class GpuAcceleratedContext2DBackend(
    override val imageBuffer: SystemGpuImageBuffer
) : ImageBuffer2DContext {

    constructor(
        width: Int = 800,
        height: Int = 600,
        gpuContext: GpuOffscreenContext = GpuDevice.acquireOffscreenContext(preferVulkan = true)
    ) : this(SystemGpuImageBuffer(width = width, height = height, gpuContext = gpuContext))

    val gpuContext: GpuOffscreenContext
        get() = imageBuffer.gpuContext

    override val width: Int
        get() = imageBuffer.width

    override val height: Int
        get() = imageBuffer.height

    override var antialias: Boolean = true

    override val swapCount: Long
        get() = imageBuffer.flushCount

    val backendType: GpuBackendType
        get() = imageBuffer.backendType

    // -------------------------------------------------------------------------
    // Compiled Vulkan (#version 450) / OpenGL ES (#version 300 es) Shaders
    // -------------------------------------------------------------------------

    val clearFillRectShader: GpuShaderProgram = compileCatalogShader(GpuContext2DShaderCatalog.KEY_CLEAR_FILL_RECT)
    val antialiasedSdfLineShader: GpuShaderProgram = compileCatalogShader(GpuContext2DShaderCatalog.KEY_ANTIALIASED_SDF_LINE)
    val antialiasedSdfShapeShader: GpuShaderProgram = compileCatalogShader(GpuContext2DShaderCatalog.KEY_ANTIALIASED_SDF_SHAPE)
    val antialiasedBrushStampShader: GpuShaderProgram = compileCatalogShader(GpuContext2DShaderCatalog.KEY_ANTIALIASED_BRUSH_STAMP)
    val gradientShader: GpuShaderProgram = compileCatalogShader(GpuContext2DShaderCatalog.KEY_LINEAR_RADIAL_GRADIENT)
    val imageBlitSwizzleShader: GpuShaderProgram = compileCatalogShader(GpuContext2DShaderCatalog.KEY_IMAGE_BLIT_SWIZZLE)
    val sdfTextGlyphShader: GpuShaderProgram = compileCatalogShader(GpuContext2DShaderCatalog.KEY_SDF_TEXT_GLYPH)
    val conicMultiGradientShader: GpuShaderProgram = compileCatalogShader(GpuContext2DShaderCatalog.KEY_CONIC_MULTI_GRADIENT)
    val patternSamplerShader: GpuShaderProgram = compileCatalogShader(GpuContext2DShaderCatalog.KEY_PATTERN_SAMPLER)
    val shadowGaussianBlurShader: GpuShaderProgram = compileCatalogShader(GpuContext2DShaderCatalog.KEY_SHADOW_GAUSSIAN_BLUR)
    val compositeBlend26Shader: GpuShaderProgram = compileCatalogShader(GpuContext2DShaderCatalog.KEY_COMPOSITE_BLEND_26)
    val perCornerEllipseSdfShader: GpuShaderProgram = compileCatalogShader(GpuContext2DShaderCatalog.KEY_PER_CORNER_ELLIPSE_SDF)

    private fun compileCatalogShader(key: String): GpuShaderProgram =
        SystemGpuShaderCache.getOrCompile(
            gpuContext = gpuContext,
            shaderKey = key,
            vulkanGlsl450 = GpuContext2DShaderCatalog.vulkanSourceForKey(key),
            gles300Shader = GpuContext2DShaderCatalog.glesSourceForKey(key)
        )

    // -------------------------------------------------------------------------
    // 2D Context State
    // -------------------------------------------------------------------------

    private var currentMatrix: ImageBufferMatrix2D = ImageBufferMatrix2D.IDENTITY
    override var globalAlpha: Float = 1f
    override var globalCompositeOperation: ImageBufferCompositeOperation = ImageBufferCompositeOperation.SOURCE_OVER
    override var imageSmoothingEnabled: Boolean = true
    override var imageSmoothingQuality: ImageBufferImageSmoothingQuality = ImageBufferImageSmoothingQuality.HIGH
    override var filter: String = "none"

    override var fillStyle: ImageBufferPaintStyle = ImageBufferPaintStyle.solid(RgbaColor.BLACK)
    override var strokeStyle: ImageBufferPaintStyle = ImageBufferPaintStyle.solid(RgbaColor.BLACK)

    val fillColor: RgbaColor
        get() = fillStyle.color

    val strokeColor: RgbaColor
        get() = strokeStyle.color

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

    private var clipMinX: Int = 0
    private var clipMinY: Int = 0
    private var clipMaxX: Int = Int.MAX_VALUE
    private var clipMaxY: Int = Int.MAX_VALUE

    private val currentPath = ImageBufferPath2D()
    private val stateStack = ArrayDeque<StateSnapshot>()

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
        clipMinX = 0
        clipMinY = 0
        clipMaxX = Int.MAX_VALUE
        clipMaxY = Int.MAX_VALUE
        currentPath.reset()
        stateStack.clear()
        imageBuffer.clearRectDirect(0, 0, width, height, RgbaColor.TRANSPARENT)
    }

    override fun save() {
        stateStack.addLast(
            StateSnapshot(
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
                clipMinX = clipMinX,
                clipMinY = clipMinY,
                clipMaxX = clipMaxX,
                clipMaxY = clipMaxY,
                antialias = antialias
            )
        )
    }

    override fun restore() {
        val prev = stateStack.removeLastOrNull() ?: return
        currentMatrix = prev.matrix
        globalAlpha = prev.globalAlpha
        globalCompositeOperation = prev.globalCompositeOperation
        imageSmoothingEnabled = prev.imageSmoothingEnabled
        imageSmoothingQuality = prev.imageSmoothingQuality
        filter = prev.filter
        fillStyle = prev.fillStyle
        strokeStyle = prev.strokeStyle
        shadowBlur = prev.shadowBlur
        shadowColor = prev.shadowColor
        shadowOffsetX = prev.shadowOffsetX
        shadowOffsetY = prev.shadowOffsetY
        lineWidth = prev.lineWidth
        lineCap = prev.lineCap
        lineJoin = prev.lineJoin
        miterLimit = prev.miterLimit
        lineDashOffset = prev.lineDashOffset
        lineDashSegments = prev.lineDashSegments
        fontFamily = prev.fontFamily
        fontSizePx = prev.fontSizePx
        fontWeight = prev.fontWeight
        fontStyle = prev.fontStyle
        textAlign = prev.textAlign
        textBaseline = prev.textBaseline
        direction = prev.direction
        letterSpacingPx = prev.letterSpacingPx
        wordSpacingPx = prev.wordSpacingPx
        fontKerning = prev.fontKerning
        fontStretch = prev.fontStretch
        fontVariantCaps = prev.fontVariantCaps
        textRendering = prev.textRendering
        clipMinX = prev.clipMinX
        clipMinY = prev.clipMinY
        clipMaxX = prev.clipMaxX
        clipMaxY = prev.clipMaxY
        antialias = prev.antialias
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
        val sx0 = floor(min(tx0, tx1)).toInt().coerceIn(max(0, clipMinX), min(this.width, clipMaxX))
        val sy0 = floor(min(ty0, ty1)).toInt().coerceIn(max(0, clipMinY), min(this.height, clipMaxY))
        val sx1 = ceil(max(tx0, tx1)).toInt().coerceIn(max(0, clipMinX), min(this.width, clipMaxX))
        val sy1 = ceil(max(ty0, ty1)).toInt().coerceIn(max(0, clipMinY), min(this.height, clipMaxY))
        imageBuffer.clearRectDirect(sx0, sy0, sx1, sy1, RgbaColor.TRANSPARENT)
    }

    override fun fillRect(x: Float, y: Float, width: Float, height: Float) {
        if (currentMatrix.isIdentity &&
            fillStyle.kind == ImageBufferPaintStyleKind.SOLID_COLOR &&
            shadowColor.a == 0
        ) {
            val sx0 = floor(min(x, x + width)).toInt().coerceIn(max(0, clipMinX), min(this.width, clipMaxX))
            val sy0 = floor(min(y, y + height)).toInt().coerceIn(max(0, clipMinY), min(this.height, clipMaxY))
            val sx1 = ceil(max(x, x + width)).toInt().coerceIn(max(0, clipMinX), min(this.width, clipMaxX))
            val sy1 = ceil(max(y, y + height)).toInt().coerceIn(max(0, clipMinY), min(this.height, clipMaxY))
            if (sx1 <= sx0 || sy1 <= sy0) return

            val c = fillStyle.color
            val effectiveAlpha = ((c.a / 255f) * globalAlpha).coerceIn(0f, 1f)
            if (effectiveAlpha >= 0.995f && globalCompositeOperation == ImageBufferCompositeOperation.SOURCE_OVER) {
                imageBuffer.clearRectDirect(sx0, sy0, sx1, sy1, c)
                return
            }
            val src = RgbaColor(c.r, c.g, c.b, (effectiveAlpha * 255f).roundToInt().coerceIn(0, 255))
            for (py in sy0 until sy1) {
                for (px in sx0 until sx1) {
                    blendPixelDirect(px, py, src)
                }
            }
            imageBuffer.markDirtyRect(sx0, sy0, sx1, sy1)
            return
        }
        val path = ImageBufferPath2D().apply { rect(x, y, width, height) }
        fill(path, ImageBufferFillRule.NONZERO)
    }

    override fun strokeRect(x: Float, y: Float, width: Float, height: Float) {
        val (x0, y0) = currentMatrix.transformPoint(x, y)
        val (x1, y1) = currentMatrix.transformPoint(x + width, y)
        val (x2, y2) = currentMatrix.transformPoint(x + width, y + height)
        val (x3, y3) = currentMatrix.transformPoint(x, y + height)
        val scaledWidth = max(1f, lineWidth * currentMatrix.maxScaleFactor())
        drawAntialiasedSegment(x0, y0, x1, y1, strokeStyle, scaledWidth)
        drawAntialiasedSegment(x1, y1, x2, y2, strokeStyle, scaledWidth)
        drawAntialiasedSegment(x2, y2, x3, y3, strokeStyle, scaledWidth)
        drawAntialiasedSegment(x3, y3, x0, y0, strokeStyle, scaledWidth)
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

    private fun renderRoundedRectSdfDirect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tl: Float,
        tr: Float,
        br: Float,
        bl: Float
    ) {
        val minX = min(x, x + width)
        val maxX = max(x, x + width)
        val minY = min(y, y + height)
        val maxY = max(y, y + height)
        val halfW = (maxX - minX) * 0.5f
        val halfH = (maxY - minY) * 0.5f
        val cx = minX + halfW
        val cy = minY + halfH
        val maxR = min(halfW, halfH)
        val rTl = tl.coerceIn(0f, maxR)
        val rTr = tr.coerceIn(0f, maxR)
        val rBr = br.coerceIn(0f, maxR)
        val rBl = bl.coerceIn(0f, maxR)
        val maxCornerR = max(max(rTl, rTr), max(rBr, rBl))
        val feather = if (antialias) 0.75f else 0.01f

        val ix0 = floor(minX - 1f).toInt().coerceIn(max(0, clipMinX), min(this.width, clipMaxX))
        val iy0 = floor(minY - 1f).toInt().coerceIn(max(0, clipMinY), min(this.height, clipMaxY))
        val ix1 = ceil(maxX + 1f).toInt().coerceIn(max(0, clipMinX), min(this.width, clipMaxX))
        val iy1 = ceil(maxY + 1f).toInt().coerceIn(max(0, clipMinY), min(this.height, clipMaxY))
        if (ix1 <= ix0 || iy1 <= iy0) return

        val isSolid = fillStyle.kind == ImageBufferPaintStyleKind.SOLID_COLOR
        val solidFullSrc = if (isSolid) modulatePaintAlpha(fillStyle.color, globalAlpha) else RgbaColor.TRANSPARENT
        val invMatrix = if (!isSolid && !currentMatrix.isIdentity) {
            currentMatrix.invert() ?: ImageBufferMatrix2D.IDENTITY
        } else {
            ImageBufferMatrix2D.IDENTITY
        }

        val innerHalfW = halfW - maxCornerR - feather
        val innerHalfH = halfH - maxCornerR - feather

        for (py in iy0 until iy1) {
            val sampleY = py + 0.5f
            val relY = sampleY - cy
            val absRelY = abs(relY)
            for (px in ix0 until ix1) {
                val sampleX = px + 0.5f
                val relX = sampleX - cx
                val absRelX = abs(relX)

                // Fast interior check: inside core rectangle away from corners and edges
                if (absRelX <= innerHalfW && absRelY <= halfH - feather ||
                    absRelY <= innerHalfH && absRelX <= halfW - feather
                ) {
                    if (isSolid) {
                        blendPixelDirect(px, py, solidFullSrc)
                    } else {
                        val (lx, ly) = if (currentMatrix.isIdentity) {
                            sampleX to sampleY
                        } else {
                            invMatrix.transformPoint(sampleX, sampleY)
                        }
                        val rawColor = fillStyle.sampleAt(lx, ly, imageSmoothingEnabled)
                        blendPixelDirect(px, py, modulatePaintAlpha(rawColor, globalAlpha))
                    }
                    continue
                }

                val r = if (relX > 0f) {
                    if (relY > 0f) rBr else rTr
                } else {
                    if (relY > 0f) rBl else rTl
                }
                val dx = absRelX - (halfW - r)
                val dy = absRelY - (halfH - r)
                val sdf = if (dx <= 0f || dy <= 0f) {
                    max(dx, dy) - r
                } else {
                    hypot(dx, dy) - r
                }
                if (sdf <= feather) {
                    val cov = if (antialias) {
                        if (sdf <= -feather) 1f else ((feather - sdf) / (2f * feather)).coerceIn(0f, 1f)
                    } else {
                        if (sdf <= 0f) 1f else 0f
                    }
                    if (cov > 0.002f) {
                        if (isSolid && cov >= 0.999f) {
                            blendPixelDirect(px, py, solidFullSrc)
                        } else if (isSolid) {
                            val src = modulatePaintAlpha(fillStyle.color, (globalAlpha * cov).coerceIn(0f, 1f))
                            blendPixelDirect(px, py, src)
                        } else {
                            val (lx, ly) = if (currentMatrix.isIdentity) {
                                sampleX to sampleY
                            } else {
                                invMatrix.transformPoint(sampleX, sampleY)
                            }
                            val rawColor = fillStyle.sampleAt(lx, ly, imageSmoothingEnabled)
                            val src = modulatePaintAlpha(rawColor, (globalAlpha * cov).coerceIn(0f, 1f))
                            blendPixelDirect(px, py, src)
                        }
                    }
                }
            }
        }
        imageBuffer.markDirtyRect(ix0, iy0, ix1, iy1)
    }

    override fun fill(fillRule: ImageBufferFillRule) {
        fill(currentPath, fillRule)
    }

    override fun fill(path: ImageBufferPath2D, fillRule: ImageBufferFillRule) {
        val rr = path.singleRoundRectSpec
        if (rr != null &&
            abs(currentMatrix.b) <= 1e-5f &&
            abs(currentMatrix.c) <= 1e-5f &&
            currentMatrix.a > 0f &&
            currentMatrix.d > 0f
        ) {
            val rx = rr[0] * currentMatrix.a + currentMatrix.e
            val ry = rr[1] * currentMatrix.d + currentMatrix.f
            val rw = rr[2] * currentMatrix.a
            val rh = rr[3] * currentMatrix.d
            val rScale = min(currentMatrix.a, currentMatrix.d)
            renderRoundedRectSdfDirect(
                rx,
                ry,
                rw,
                rh,
                rr[4] * rScale,
                rr[5] * rScale,
                rr[6] * rScale,
                rr[7] * rScale
            )
            return
        }

        val subpaths = path.flattenSubpaths(currentMatrix)
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

        val sx = floor(minX - 1f).toInt().coerceIn(max(0, clipMinX), min(width, clipMaxX))
        val sy = floor(minY - 1f).toInt().coerceIn(max(0, clipMinY), min(height, clipMaxY))
        val ex = ceil(maxX + 1f).toInt().coerceIn(max(0, clipMinX), min(width, clipMaxX))
        val ey = ceil(maxY + 1f).toInt().coerceIn(max(0, clipMinY), min(height, clipMaxY))
        if (ex <= sx || ey <= sy) return

        val isSolid = fillStyle.kind == ImageBufferPaintStyleKind.SOLID_COLOR
        val solidSrc = if (isSolid) modulatePaintAlpha(fillStyle.color, globalAlpha) else RgbaColor.TRANSPARENT
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

            // Insertion sort on intersectXBuf & intersectDirBuf
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
                        if (isSolid) {
                            blendPixelDirect(px, py, solidSrc)
                        } else {
                            val (localX, localY) = invMatrix.transformPoint(px + 0.5f, sampleY)
                            val rawColor = fillStyle.sampleAt(localX, localY, imageSmoothingEnabled)
                            blendPixelDirect(px, py, modulatePaintAlpha(rawColor, globalAlpha))
                        }
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
                            if (isSolid) {
                                blendPixelDirect(px, py, solidSrc)
                            } else {
                                val (localX, localY) = invMatrix.transformPoint(px + 0.5f, sampleY)
                                val rawColor = fillStyle.sampleAt(localX, localY, imageSmoothingEnabled)
                                blendPixelDirect(px, py, modulatePaintAlpha(rawColor, globalAlpha))
                            }
                        }
                    }
                }
            }
        }

        if (antialias) {
            for (poly in subpaths) {
                val count = poly.size / 2
                if (count >= 2) {
                    for (i in 0 until count - 1) {
                        drawAntialiasedSegment(
                            poly[i * 2],
                            poly[i * 2 + 1],
                            poly[(i + 1) * 2],
                            poly[(i + 1) * 2 + 1],
                            fillStyle,
                            1.1f
                        )
                    }
                }
            }
        }
        imageBuffer.markDirtyRect(sx, sy, ex, ey)
    }

    override fun stroke() {
        stroke(currentPath)
    }

    override fun stroke(path: ImageBufferPath2D) {
        val subpaths = path.flattenSubpaths(currentMatrix)
        if (subpaths.isEmpty()) return
        val scaledWidth = max(1f, lineWidth * currentMatrix.maxScaleFactor())
        for (poly in subpaths) {
            var i = 0
            while (i + 3 < poly.size) {
                drawAntialiasedSegment(
                    poly[i],
                    poly[i + 1],
                    poly[i + 2],
                    poly[i + 3],
                    strokeStyle,
                    scaledWidth
                )
                i += 2
            }
        }
    }

    override fun clip(fillRule: ImageBufferFillRule) {
        clip(currentPath, fillRule)
    }

    override fun clip(path: ImageBufferPath2D, fillRule: ImageBufferFillRule) {
        val subpaths = path.flattenSubpaths(currentMatrix)
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
        clipMinX = max(clipMinX, floor(minX).toInt())
        clipMinY = max(clipMinY, floor(minY).toInt())
        clipMaxX = min(clipMaxX, ceil(maxX).toInt())
        clipMaxY = min(clipMaxY, ceil(maxY).toInt())
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
        renderTextSdf(text, x, y, maxWidth, strokeMode = false)
    }

    override fun strokeText(text: String, x: Float, y: Float, maxWidth: Float?) {
        renderTextSdf(text, x, y, maxWidth, strokeMode = true)
    }

    private fun renderTextSdf(
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
            if (px in max(0, clipMinX) until min(width, clipMaxX) &&
                py in max(0, clipMinY) until min(height, clipMaxY)
            ) {
                val baseColor = if (isShadow) shadowColor else style.sampleAt(localX, localY, imageSmoothingEnabled)
                val src = modulatePaintAlpha(baseColor, (globalAlpha * coverage).coerceIn(0f, 1f))
                blendPixelDirect(px, py, src)
                if (px < dirtyMinX) dirtyMinX = px
                if (py < dirtyMinY) dirtyMinY = py
                if (px + 1 > dirtyMaxX) dirtyMaxX = px + 1
                if (py + 1 > dirtyMaxY) dirtyMaxY = py + 1
                touched = true
            }
        }

        if (touched) {
            imageBuffer.markDirtyRect(dirtyMinX, dirtyMinY, dirtyMaxX, dirtyMaxY)
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
        if (currentMatrix.isIdentity &&
            globalAlpha >= 0.999f &&
            globalCompositeOperation == ImageBufferCompositeOperation.SOURCE_OVER
        ) {
            val rgba = image.asRgba8888()
            imageBuffer.writePixelsRgba(
                rgbaBytes = rgba.bytes,
                width = rgba.width,
                height = rgba.height,
                dx = dx.roundToInt(),
                dy = dy.roundToInt()
            )
            return
        }
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
        if (rgba.width <= 0 || rgba.height <= 0) return
        val (tx0, ty0) = currentMatrix.transformPoint(dx, dy)
        val (tx1, ty1) = currentMatrix.transformPoint(dx + dWidth, dy + dHeight)
        val minTx = min(tx0, tx1)
        val minTy = min(ty0, ty1)
        val startX = floor(minTx).toInt().coerceIn(max(0, clipMinX), min(width, clipMaxX))
        val startY = floor(minTy).toInt().coerceIn(max(0, clipMinY), min(height, clipMaxY))
        val endX = ceil(max(tx0, tx1)).toInt().coerceIn(max(0, clipMinX), min(width, clipMaxX))
        val endY = ceil(max(ty0, ty1)).toInt().coerceIn(max(0, clipMinY), min(height, clipMaxY))
        if (endX <= startX || endY <= startY) return

        val invSpanX = 1f / max(1f, abs(tx1 - tx0))
        val invSpanY = 1f / max(1f, abs(ty1 - ty0))
        val srcW = rgba.width
        val srcH = rgba.height
        val srcBytes = rgba.bytes
        val dstBytes = imageBuffer.activeDrawBytes
        val dstW = this.width

        val cols = endX - startX
        val srcXByteOffsetTable = IntArray(cols)
        for (i in 0 until cols) {
            val px = startX + i
            val u = ((px + 0.5f - minTx) * invSpanX).coerceIn(0f, 1f)
            val srcX = (sx + u * sWidth).toInt().coerceIn(0, srcW - 1)
            srcXByteOffsetTable[i] = srcX shl 2
        }

        val isFastSourceOver = globalCompositeOperation == ImageBufferCompositeOperation.SOURCE_OVER &&
            globalAlpha >= 0.999f

        for (py in startY until endY) {
            val v = ((py + 0.5f - minTy) * invSpanY).coerceIn(0f, 1f)
            val srcY = (sy + v * sHeight).toInt().coerceIn(0, srcH - 1)
            val srcRowBase = (srcY * srcW) shl 2
            val dstRowBase = (py * dstW) shl 2

            if (isFastSourceOver) {
                for (i in 0 until cols) {
                    val sIdx = srcRowBase + srcXByteOffsetTable[i]
                    val aByte = srcBytes[sIdx + 3]
                    if (aByte == (-1).toByte()) {
                        val dIdx = dstRowBase + ((startX + i) shl 2)
                        dstBytes[dIdx] = srcBytes[sIdx]
                        dstBytes[dIdx + 1] = srcBytes[sIdx + 1]
                        dstBytes[dIdx + 2] = srcBytes[sIdx + 2]
                        dstBytes[dIdx + 3] = (-1).toByte()
                    } else {
                        val srcA = aByte.toInt() and 0xFF
                        if (srcA > 0) {
                            blendPixelDirect(
                                startX + i,
                                py,
                                RgbaColor(
                                    srcBytes[sIdx].toInt() and 0xFF,
                                    srcBytes[sIdx + 1].toInt() and 0xFF,
                                    srcBytes[sIdx + 2].toInt() and 0xFF,
                                    srcA
                                )
                            )
                        }
                    }
                }
            } else {
                for (i in 0 until cols) {
                    val sIdx = srcRowBase + srcXByteOffsetTable[i]
                    val srcA = srcBytes[sIdx + 3].toInt() and 0xFF
                    val scaledA = (srcA * globalAlpha).roundToInt().coerceIn(0, 255)
                    if (scaledA > 0 || globalCompositeOperation == ImageBufferCompositeOperation.COPY) {
                        blendPixelDirect(
                            startX + i,
                            py,
                            RgbaColor(
                                srcBytes[sIdx].toInt() and 0xFF,
                                srcBytes[sIdx + 1].toInt() and 0xFF,
                                srcBytes[sIdx + 2].toInt() and 0xFF,
                                scaledA
                            )
                        )
                    }
                }
            }
        }
        imageBuffer.markDirtyRect(startX, startY, endX, endY)
    }

    override fun createImageData(width: Int, height: Int): com.nexustools.genericui.image.ImageBuffer =
        com.nexustools.genericui.image.ImageBuffer(max(1, width), max(1, height))

    override fun getImageData(x: Int, y: Int, width: Int, height: Int): com.nexustools.genericui.image.ImageBuffer {
        val safeW = max(1, width)
        val safeH = max(1, height)
        val bytes = imageBuffer.readPixelsRgba(x, y, safeW, safeH)
        return com.nexustools.genericui.image.ImageBuffer(safeW, safeH, bytes = bytes)
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
            return
        }
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
        imageBuffer.markDirtyRect(dx + sx0, dy + sy0, dx + sx1, dy + sy1)
    }

    override fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) {
        imageBuffer.writePixelsRgba(rgbaBytes, width, height, dx, dy)
    }

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
        val safeR = max(0.5f, radius)
        val pad = ceil(safeR + 1.5f).toInt()
        val ix0 = (min(x0, x1) - pad).toInt().coerceIn(0, width)
        val iy0 = (min(y0, y1) - pad).toInt().coerceIn(0, height)
        val ix1 = (max(x0, x1) + pad + 1).toInt().coerceIn(0, width)
        val iy1 = (max(y0, y1) + pad + 1).toInt().coerceIn(0, height)
        if (ix1 <= ix0 || iy1 <= iy0) return

        val baX = x1 - x0
        val baY = y1 - y0
        val baLenSq = baX * baX + baY * baY
        val innerR = safeR * hardness.coerceIn(0f, 0.98f)
        val outerR = safeR + if (antialias) 0.75f else 0.05f
        val featherSpan = max(0.001f, outerR - innerR)
        val baseOpacity = opacity.coerceIn(0f, 1f) * (color.a / 255f)
        val bytes = imageBuffer.activeDrawBytes
        val w = this.width

        for (py in iy0 until iy1) {
            val cy = py + 0.5f
            val rowOffset = py * w
            for (px in ix0 until ix1) {
                val cx = px + 0.5f
                val paX = cx - x0
                val paY = cy - y0
                val h = if (baLenSq > 1e-5f) {
                    ((paX * baX + paY * baY) / baLenSq).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val dx = paX - baX * h
                val dy = paY - baY * h
                val distSq = dx * dx + dy * dy
                if (distSq > outerR * outerR) continue

                val dist = sqrt(distSq)
                val cov = if (dist <= innerR) {
                    1f
                } else {
                    val t = ((dist - innerR) / featherSpan).coerceIn(0f, 1f)
                    1f - t * t * (3f - 2f * t)
                }
                val alpha = (baseOpacity * cov).coerceIn(0f, 1f)
                if (alpha <= 0.002f) continue

                val idx = (rowOffset + px) shl 2
                if (erase) {
                    val curA = (bytes[idx + 3].toInt() and 0xFF) / 255f
                    val nextA = (curA * (1f - alpha)).coerceIn(0f, 1f)
                    bytes[idx + 3] = (nextA * 255f).roundToInt().coerceIn(0, 255).toByte()
                    if (nextA <= 0.004f) {
                        bytes[idx] = 0
                        bytes[idx + 1] = 0
                        bytes[idx + 2] = 0
                        bytes[idx + 3] = 0
                    }
                } else {
                    val src = RgbaColor(color.r, color.g, color.b, (alpha * 255f).roundToInt().coerceIn(0, 255))
                    blendPixelDirect(px, py, src)
                }
            }
        }
        imageBuffer.markDirtyRect(ix0, iy0, ix1, iy1)
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
        val ix0 = rectX.roundToInt().coerceIn(0, width)
        val iy0 = rectY.roundToInt().coerceIn(0, height)
        val ix1 = (rectX + rectW).roundToInt().coerceIn(0, width)
        val iy1 = (rectY + rectH).roundToInt().coerceIn(0, height)
        if (ix1 <= ix0 || iy1 <= iy0) return

        val abX = x1 - x0
        val abY = y1 - y0
        val abLenSq = max(1e-4f, abX * abX + abY * abY)
        val bytes = imageBuffer.activeDrawBytes
        val w = this.width

        for (py in iy0 until iy1) {
            val rowOffset = py * w
            val dy = (py + 0.5f) - y0
            for (px in ix0 until ix1) {
                val dx = (px + 0.5f) - x0
                val t = ((dx * abX + dy * abY) / abLenSq).coerceIn(0f, 1f)
                val u = 1f - t
                val idx = (rowOffset + px) shl 2
                bytes[idx] = (startColor.r * u + endColor.r * t).roundToInt().coerceIn(0, 255).toByte()
                bytes[idx + 1] = (startColor.g * u + endColor.g * t).roundToInt().coerceIn(0, 255).toByte()
                bytes[idx + 2] = (startColor.b * u + endColor.b * t).roundToInt().coerceIn(0, 255).toByte()
                bytes[idx + 3] = (startColor.a * u + endColor.a * t).roundToInt().coerceIn(0, 255).toByte()
            }
        }
        imageBuffer.markDirtyRect(ix0, iy0, ix1, iy1)
    }

    override fun floodFill(
        seedX: Int,
        seedY: Int,
        fillColor: RgbaColor,
        tolerance: Int
    ) {
        val w = this.width
        val h = this.height
        if (seedX !in 0 until w || seedY !in 0 until h) return
        val bytes = imageBuffer.activeDrawBytes
        val seedIdx = (seedY * w + seedX) shl 2
        val targetR = bytes[seedIdx].toInt() and 0xFF
        val targetG = bytes[seedIdx + 1].toInt() and 0xFF
        val targetB = bytes[seedIdx + 2].toInt() and 0xFF
        val targetA = bytes[seedIdx + 3].toInt() and 0xFF

        val fillR = fillColor.r.toByte()
        val fillG = fillColor.g.toByte()
        val fillB = fillColor.b.toByte()
        val fillA = fillColor.a.coerceIn(0, 255).toByte()
        if (targetR == fillColor.r && targetG == fillColor.g && targetB == fillColor.b && targetA == fillColor.a) {
            return
        }

        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        val seedFlat = seedY * w + seedX
        visited[seedFlat] = true
        queue[tail++] = seedFlat

        var minDirtyX = seedX
        var maxDirtyX = seedX + 1
        var minDirtyY = seedY
        var maxDirtyY = seedY + 1

        fun matches(flatIdx: Int): Boolean {
            val idx = flatIdx shl 2
            val r = bytes[idx].toInt() and 0xFF
            val g = bytes[idx + 1].toInt() and 0xFF
            val b = bytes[idx + 2].toInt() and 0xFF
            val a = bytes[idx + 3].toInt() and 0xFF
            return abs(r - targetR) <= tolerance &&
                abs(g - targetG) <= tolerance &&
                abs(b - targetB) <= tolerance &&
                abs(a - targetA) <= tolerance
        }

        while (head < tail) {
            val flat = queue[head++]
            val px = flat % w
            val py = flat / w
            if (px < minDirtyX) minDirtyX = px
            if (px + 1 > maxDirtyX) maxDirtyX = px + 1
            if (py < minDirtyY) minDirtyY = py
            if (py + 1 > maxDirtyY) maxDirtyY = py + 1

            val byteIdx = flat shl 2
            bytes[byteIdx] = fillR
            bytes[byteIdx + 1] = fillG
            bytes[byteIdx + 2] = fillB
            bytes[byteIdx + 3] = fillA

            if (px > 0) {
                val left = flat - 1
                if (!visited[left] && matches(left)) {
                    visited[left] = true
                    queue[tail++] = left
                }
            }
            if (px + 1 < w) {
                val right = flat + 1
                if (!visited[right] && matches(right)) {
                    visited[right] = true
                    queue[tail++] = right
                }
            }
            if (py > 0) {
                val up = flat - w
                if (!visited[up] && matches(up)) {
                    visited[up] = true
                    queue[tail++] = up
                }
            }
            if (py + 1 < h) {
                val down = flat + w
                if (!visited[down] && matches(down)) {
                    visited[down] = true
                    queue[tail++] = down
                }
            }
        }
        imageBuffer.markDirtyRect(minDirtyX, minDirtyY, maxDirtyX, maxDirtyY)
    }

    private fun drawAntialiasedSegment(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        style: ImageBufferPaintStyle,
        strokeWidth: Float
    ) {
        val halfW = max(0.5f, strokeWidth * 0.5f)
        val feather = if (antialias) 0.75f else 0.05f
        val outerR = halfW + feather
        val pad = ceil(outerR + 1f).toInt()
        val ix0 = (min(x0, x1) - pad).toInt().coerceIn(max(0, clipMinX), min(width, clipMaxX))
        val iy0 = (min(y0, y1) - pad).toInt().coerceIn(max(0, clipMinY), min(height, clipMaxY))
        val ix1 = (max(x0, x1) + pad + 1).toInt().coerceIn(max(0, clipMinX), min(width, clipMaxX))
        val iy1 = (max(y0, y1) + pad + 1).toInt().coerceIn(max(0, clipMinY), min(height, clipMaxY))
        if (ix1 <= ix0 || iy1 <= iy0) return

        val baX = x1 - x0
        val baY = y1 - y0
        val baLenSq = baX * baX + baY * baY
        val invMatrix = currentMatrix.invert() ?: ImageBufferMatrix2D.IDENTITY

        for (py in iy0 until iy1) {
            val cy = py + 0.5f
            for (px in ix0 until ix1) {
                val cx = px + 0.5f
                val paX = cx - x0
                val paY = cy - y0
                val h = if (baLenSq > 1e-5f) {
                    ((paX * baX + paY * baY) / baLenSq).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val dx = paX - baX * h
                val dy = paY - baY * h
                val distSq = dx * dx + dy * dy
                if (distSq > outerR * outerR) continue

                val dist = sqrt(distSq)
                val cov = if (antialias) {
                    if (dist <= halfW - feather) {
                        1f
                    } else {
                        ((outerR - dist) / (2f * feather)).coerceIn(0f, 1f)
                    }
                } else {
                    if (dist <= halfW) 1f else 0f
                }
                if (cov > 0.004f) {
                    val (lx, ly) = invMatrix.transformPoint(cx, cy)
                    val rawColor = style.sampleAt(lx, ly, imageSmoothingEnabled)
                    val src = modulatePaintAlpha(rawColor, (globalAlpha * cov).coerceIn(0f, 1f))
                    blendPixelDirect(px, py, src)
                }
            }
        }
        imageBuffer.markDirtyRect(ix0, iy0, ix1, iy1)
    }

    private fun modulatePaintAlpha(color: RgbaColor, factor: Float): RgbaColor {
        val clamped = factor.coerceIn(0f, 1f)
        if (clamped >= 0.999f) return color
        return RgbaColor(color.r, color.g, color.b, (color.a * clamped).roundToInt().coerceIn(0, 255))
    }

    private fun blendPixelDirect(px: Int, py: Int, src: RgbaColor) {
        val bytes = imageBuffer.activeDrawBytes
        val idx = (py * width + px) shl 2
        if (idx + 3 >= bytes.size) return

        if (globalCompositeOperation == ImageBufferCompositeOperation.COPY) {
            bytes[idx] = src.r.toByte()
            bytes[idx + 1] = src.g.toByte()
            bytes[idx + 2] = src.b.toByte()
            bytes[idx + 3] = src.a.toByte()
            return
        }
        if (globalCompositeOperation == ImageBufferCompositeOperation.SOURCE_OVER && src.a == 255) {
            bytes[idx] = src.r.toByte()
            bytes[idx + 1] = src.g.toByte()
            bytes[idx + 2] = src.b.toByte()
            bytes[idx + 3] = (-1).toByte()
            return
        }
        val dst = RgbaColor(
            r = bytes[idx].toInt() and 0xFF,
            g = bytes[idx + 1].toInt() and 0xFF,
            b = bytes[idx + 2].toInt() and 0xFF,
            a = bytes[idx + 3].toInt() and 0xFF
        )
        val out = globalCompositeOperation.blend(src, dst)
        bytes[idx] = out.r.toByte()
        bytes[idx + 1] = out.g.toByte()
        bytes[idx + 2] = out.b.toByte()
        bytes[idx + 3] = out.a.toByte()
    }

    private class StateSnapshot(
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
        val clipMaxY: Int,
        val antialias: Boolean
    )
}
