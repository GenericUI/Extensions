package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.style.RgbaColor

/**
 * Hardware-accelerated 2D rendering context operating directly on an [ImageBuffer] surface
 * (`imageBuffer.getContext()` or `canvas2D.getContext()`), providing 100% HTML5 2D Context
 * feature parity with strongly-typed `ImageBuffer*` enums and value types.
 *
 * All operations work identically across:
 * - **System targets** (`:extensions:imageprocessing:sharedSystem` -> `GpuAcceleratedContext2DBackend`),
 *   powered by Vulkan (`#version 450`) and OpenGL ES (`#version 300 es`) shaders and
 *   [ScalableVectorFontEngine].
 * - **Web targets** (`:extensions:imageprocessing:web` -> `WebImageBuffer2DContext`), delegating
 *   directly to the browser's 2D rendering context.
 * - **Portable CPU fallback** (`:extensions:imageprocessing:common` -> `DefaultCpuContext2DBackend`).
 */
interface ImageBuffer2DContext {
    /**
     * The [ImageBuffer] surface this 2D context renders into.
     */
    val imageBuffer: ImageBuffer

    val width: Int
        get() = imageBuffer.width

    val height: Int
        get() = imageBuffer.height

    var antialias: Boolean

    val swapCount: Long
        get() = imageBuffer.swapCount

    // -------------------------------------------------------------------------
    // Surface lifecycle & buffer management
    // -------------------------------------------------------------------------

    fun resize(newWidth: Int, newHeight: Int) {
        imageBuffer.resize(newWidth, newHeight)
    }

    fun resizeSurface(newWidth: Int, newHeight: Int) {
        resize(newWidth, newHeight)
    }

    fun swapBuffers() {
        imageBuffer.swapBuffers()
    }

    fun reset()

    // -------------------------------------------------------------------------
    // State stack & context attributes
    // -------------------------------------------------------------------------

    fun save()
    fun restore()
    fun isContextLost(): Boolean = false

    // -------------------------------------------------------------------------
    // Transformations (2D affine matrix)
    // -------------------------------------------------------------------------

    fun scale(sx: Float, sy: Float)
    fun rotate(angleRadians: Float)
    fun translate(dx: Float, dy: Float)
    fun transform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float)
    fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float)
    fun setTransform(matrix: ImageBufferMatrix2D) {
        setTransform(matrix.a, matrix.b, matrix.c, matrix.d, matrix.e, matrix.f)
    }
    fun getTransform(): ImageBufferMatrix2D
    fun resetTransform()

    // -------------------------------------------------------------------------
    // Compositing & Blending (26 Porter-Duff & W3C blend modes)
    // -------------------------------------------------------------------------

    var globalAlpha: Float
    var globalCompositeOperation: ImageBufferCompositeOperation

    fun setGlobalAlpha(alpha: Float, clamp: Boolean = true) {
        globalAlpha = if (clamp) alpha.coerceIn(0f, 1f) else alpha
    }

    fun setGlobalCompositeOperation(operation: ImageBufferCompositeOperation, resetAlpha: Boolean = false) {
        globalCompositeOperation = operation
        if (resetAlpha) {
            globalAlpha = 1f
        }
    }

    fun setGlobalCompositeOperation(mode: String) {
        globalCompositeOperation = ImageBufferCompositeOperation.fromWebValue(mode)
    }

    // -------------------------------------------------------------------------
    // Image smoothing & CSS filter string
    // -------------------------------------------------------------------------

    var imageSmoothingEnabled: Boolean
    var imageSmoothingQuality: ImageBufferImageSmoothingQuality
    var filter: String

    // -------------------------------------------------------------------------
    // Fill & Stroke Paint Styles (Colors, Gradients, Patterns)
    // -------------------------------------------------------------------------

    var fillStyle: ImageBufferPaintStyle
    var strokeStyle: ImageBufferPaintStyle

    fun setFillColor(color: RgbaColor) {
        fillStyle = ImageBufferPaintStyle.solid(color)
    }

    fun setStrokeColor(color: RgbaColor) {
        strokeStyle = ImageBufferPaintStyle.solid(color)
    }

    fun setFillGradient(gradient: ImageBufferGradient2D) {
        fillStyle = ImageBufferPaintStyle.gradient(gradient)
    }

    fun setStrokeGradient(gradient: ImageBufferGradient2D) {
        strokeStyle = ImageBufferPaintStyle.gradient(gradient)
    }

    fun setFillPattern(pattern: ImageBufferPattern2D) {
        fillStyle = ImageBufferPaintStyle.pattern(pattern)
    }

    fun setStrokePattern(pattern: ImageBufferPattern2D) {
        strokeStyle = ImageBufferPaintStyle.pattern(pattern)
    }

    fun createLinearGradient(x0: Float, y0: Float, x1: Float, y1: Float): ImageBufferGradient2D =
        ImageBufferGradient2D.linear(x0, y0, x1, y1)

    fun createRadialGradient(
        x0: Float,
        y0: Float,
        r0: Float,
        x1: Float,
        y1: Float,
        r1: Float
    ): ImageBufferGradient2D =
        ImageBufferGradient2D.radial(x0, y0, r0, x1, y1, r1)

    fun createConicGradient(startAngleRadians: Float, x: Float, y: Float): ImageBufferGradient2D =
        ImageBufferGradient2D.conic(startAngleRadians, x, y)

    fun createPattern(
        image: com.nexustools.genericui.image.ImageBuffer,
        repetition: ImageBufferPatternRepetition = ImageBufferPatternRepetition.REPEAT
    ): ImageBufferPattern2D =
        ImageBufferPattern2D(image.asRgba8888(), repetition)

    // -------------------------------------------------------------------------
    // Shadows
    // -------------------------------------------------------------------------

    var shadowBlur: Float
    var shadowColor: RgbaColor
    var shadowOffsetX: Float
    var shadowOffsetY: Float

    fun setShadow(
        blur: Float,
        color: RgbaColor,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ) {
        shadowBlur = blur.coerceAtLeast(0f)
        shadowColor = color
        shadowOffsetX = offsetX
        shadowOffsetY = offsetY
    }

    fun clearShadow() {
        shadowBlur = 0f
        shadowColor = RgbaColor.TRANSPARENT
        shadowOffsetX = 0f
        shadowOffsetY = 0f
    }

    // -------------------------------------------------------------------------
    // Line styles & dashes
    // -------------------------------------------------------------------------

    var lineWidth: Float
    var lineCap: ImageBufferLineCap
    var lineJoin: ImageBufferLineJoin
    var miterLimit: Float
    var lineDashOffset: Float

    fun setLineWidth(width: Float, minWidth: Float = 0.001f) {
        lineWidth = width.coerceAtLeast(minWidth)
    }

    fun setLineCap(cap: ImageBufferLineCap, resetDash: Boolean = false) {
        lineCap = cap
        if (resetDash) {
            lineDashOffset = 0f
        }
    }

    fun setLineCap(cap: String) {
        lineCap = ImageBufferLineCap.fromWebValue(cap)
    }

    fun setLineJoin(join: ImageBufferLineJoin, miterLimitPx: Float = miterLimit) {
        lineJoin = join
        miterLimit = miterLimitPx
    }

    fun setLineJoin(join: String) {
        lineJoin = ImageBufferLineJoin.fromWebValue(join)
    }

    fun setMiterLimit(limit: Float, minLimit: Float = 0.001f) {
        miterLimit = limit.coerceAtLeast(minLimit)
    }

    fun setLineDash(segments: FloatArray, offset: Float = lineDashOffset)
    fun getLineDash(): FloatArray

    // -------------------------------------------------------------------------
    // Rectangles
    // -------------------------------------------------------------------------

    fun clearRect(x: Float, y: Float, width: Float, height: Float)
    fun fillRect(x: Float, y: Float, width: Float, height: Float)
    fun strokeRect(x: Float, y: Float, width: Float, height: Float)

    // -------------------------------------------------------------------------
    // Paths & ImageBufferPath2D
    // -------------------------------------------------------------------------

    fun beginPath()
    fun closePath()
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float)
    fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float)
    fun arcTo(x1: Float, y1: Float, x2: Float, y2: Float, radius: Float)
    fun arc(
        x: Float,
        y: Float,
        radius: Float,
        startAngle: Float,
        endAngle: Float,
        counterClockwise: Boolean = false
    )
    fun ellipse(
        x: Float,
        y: Float,
        radiusX: Float,
        radiusY: Float,
        rotation: Float = 0f,
        startAngle: Float = 0f,
        endAngle: Float = 6.2831855f,
        counterClockwise: Boolean = false
    )
    fun rect(x: Float, y: Float, width: Float, height: Float)
    fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float)
    fun roundRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        topLeftRadius: Float,
        topRightRadius: Float,
        bottomRightRadius: Float,
        bottomLeftRadius: Float
    )

    fun fill(fillRule: ImageBufferFillRule = ImageBufferFillRule.NONZERO)
    fun fill(path: ImageBufferPath2D, fillRule: ImageBufferFillRule = ImageBufferFillRule.NONZERO)
    fun stroke()
    fun stroke(path: ImageBufferPath2D)
    fun clip(fillRule: ImageBufferFillRule = ImageBufferFillRule.NONZERO)
    fun clip(path: ImageBufferPath2D, fillRule: ImageBufferFillRule = ImageBufferFillRule.NONZERO)

    fun isPointInPath(
        x: Float,
        y: Float,
        fillRule: ImageBufferFillRule = ImageBufferFillRule.NONZERO
    ): Boolean

    fun isPointInPath(
        path: ImageBufferPath2D,
        x: Float,
        y: Float,
        fillRule: ImageBufferFillRule = ImageBufferFillRule.NONZERO
    ): Boolean

    fun isPointInStroke(x: Float, y: Float): Boolean
    fun isPointInStroke(path: ImageBufferPath2D, x: Float, y: Float): Boolean

    // -------------------------------------------------------------------------
    // Typography & Text Metrics (ScalableVectorFontEngine)
    // -------------------------------------------------------------------------

    var fontFamily: String
    var fontSizePx: Float
    var fontWeight: Int
    var fontStyle: ImageBufferFontStyle
    var textAlign: ImageBufferTextAlign
    var textBaseline: ImageBufferTextBaseline
    var direction: ImageBufferTextDirection
    var letterSpacingPx: Float
    var wordSpacingPx: Float
    var fontKerning: ImageBufferFontKerning
    var fontStretch: ImageBufferFontStretch
    var fontVariantCaps: ImageBufferFontVariantCaps
    var textRendering: ImageBufferTextRendering

    fun setFont(
        fontFamily: String,
        fontSizePx: Float,
        fontWeight: Int = 400,
        italic: Boolean = false
    ) {
        this.fontFamily = fontFamily
        this.fontSizePx = fontSizePx.coerceAtLeast(1f)
        this.fontWeight = fontWeight.coerceIn(100, 900)
        this.fontStyle = if (italic) ImageBufferFontStyle.ITALIC else ImageBufferFontStyle.NORMAL
    }

    fun setFont(
        fontFamily: String,
        fontSizePx: Float,
        fontWeight: Int = 400,
        fontStyle: ImageBufferFontStyle = ImageBufferFontStyle.NORMAL,
        fontStretch: ImageBufferFontStretch = ImageBufferFontStretch.NORMAL,
        fontVariantCaps: ImageBufferFontVariantCaps = ImageBufferFontVariantCaps.NORMAL
    ) {
        this.fontFamily = fontFamily
        this.fontSizePx = fontSizePx.coerceAtLeast(1f)
        this.fontWeight = fontWeight.coerceIn(100, 900)
        this.fontStyle = fontStyle
        this.fontStretch = fontStretch
        this.fontVariantCaps = fontVariantCaps
    }

    fun fillText(text: String, x: Float, y: Float, maxWidth: Float? = null)
    fun strokeText(text: String, x: Float, y: Float, maxWidth: Float? = null)
    fun measureText(text: String): ImageBufferTextMetrics
    fun measureTextWidth(text: String): Float = measureText(text).width

    // -------------------------------------------------------------------------
    // Drawing Images (3-arg, 5-arg, 9-arg) & Pixel Manipulation
    // -------------------------------------------------------------------------

    fun drawImage(image: com.nexustools.genericui.image.ImageBuffer, dx: Float, dy: Float)
    fun drawImage(
        image: com.nexustools.genericui.image.ImageBuffer,
        dx: Float,
        dy: Float,
        dWidth: Float,
        dHeight: Float
    )
    fun drawImage(
        image: com.nexustools.genericui.image.ImageBuffer,
        sx: Float,
        sy: Float,
        sWidth: Float,
        sHeight: Float,
        dx: Float,
        dy: Float,
        dWidth: Float,
        dHeight: Float
    )

    fun drawImageBuffer(buffer: com.nexustools.genericui.image.ImageBuffer, dx: Float, dy: Float) {
        drawImage(buffer, dx, dy)
    }

    fun createImageData(width: Int, height: Int): com.nexustools.genericui.image.ImageBuffer
    fun getImageData(x: Int, y: Int, width: Int, height: Int): com.nexustools.genericui.image.ImageBuffer
    fun putImageData(
        imageData: com.nexustools.genericui.image.ImageBuffer,
        dx: Int,
        dy: Int,
        dirtyX: Int = 0,
        dirtyY: Int = 0,
        dirtyWidth: Int = imageData.width,
        dirtyHeight: Int = imageData.height
    )

    fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int)
    fun getImageDataRgba(x: Int, y: Int, width: Int, height: Int): ByteArray

    fun presentFrontBufferRgba(): ByteArray = imageBuffer.directRgbaBytes()
    fun presentFrontBufferBgra(): ByteArray = imageBuffer.directBgraBytes()
    fun toImageBuffer(): com.nexustools.genericui.image.ImageBuffer = imageBuffer.toImageBuffer()

    // -------------------------------------------------------------------------
    // Accelerated Paint & Fill Helpers
    // -------------------------------------------------------------------------

    fun stampBrushStroke(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float,
        hardness: Float,
        color: RgbaColor,
        opacity: Float = 1f,
        erase: Boolean = false
    )

    fun fillLinearGradient(
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
    )

    fun floodFill(
        seedX: Int,
        seedY: Int,
        fillColor: RgbaColor,
        tolerance: Int = 0
    )
}
