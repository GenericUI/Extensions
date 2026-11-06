package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.web.WebDomElement

/**
 * Multiplatform (`js` + `wasmJs`) bridge in `:extensions:imageprocessing:web` that binds a
 * [WebImageBuffer] and [WebImageBuffer2DContext] directly to the browser's native HTML5 `<canvas>`
 * 2D rendering context (`CanvasRenderingContext2D`), while maintaining deterministic `data-*`
 * attribute recording on [WebDomElement] for headless unit test inspection.
 */
expect object WebImageBuffer2DBridge {
    val isBrowserCanvasSupported: Boolean

    fun ensureSurface2D(
        surfaceElement: WebDomElement,
        width: Int,
        height: Int,
        antialias: Boolean = true
    )

    fun resizeSurface2D(surfaceElement: WebDomElement, width: Int, height: Int)

    fun clearRect(
        surfaceElement: WebDomElement,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        transform: FloatArray = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    )

    fun fillRect(
        surfaceElement: WebDomElement,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        fillCss: String,
        globalAlpha: Float = 1f,
        compositeOp: String = "source-over",
        transform: FloatArray = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    )

    fun strokeRect(
        surfaceElement: WebDomElement,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        strokeCss: String,
        lineWidth: Float,
        lineCap: String = "butt",
        lineJoin: String = "miter",
        miterLimit: Float = 10f,
        globalAlpha: Float = 1f,
        compositeOp: String = "source-over",
        transform: FloatArray = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    )

    fun fillText(
        surfaceElement: WebDomElement,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float? = null,
        fontCss: String,
        fillCss: String,
        textAlignCss: String = "start",
        textBaselineCss: String = "alphabetic",
        globalAlpha: Float = 1f,
        compositeOp: String = "source-over",
        transform: FloatArray = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    )

    fun strokeText(
        surfaceElement: WebDomElement,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float? = null,
        fontCss: String,
        strokeCss: String,
        lineWidth: Float,
        lineCap: String = "butt",
        lineJoin: String = "miter",
        miterLimit: Float = 10f,
        textAlignCss: String = "start",
        textBaselineCss: String = "alphabetic",
        globalAlpha: Float = 1f,
        compositeOp: String = "source-over",
        transform: FloatArray = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    )

    fun measureTextWidth(
        surfaceElement: WebDomElement,
        text: String,
        fontCss: String,
        fallbackEstimate: Float = -1f
    ): Float

    fun drawSurfacePeer(
        surfaceElement: WebDomElement,
        sourcePeer: Any,
        sx: Float,
        sy: Float,
        sw: Float,
        sh: Float,
        dx: Float,
        dy: Float,
        dw: Float,
        dh: Float,
        globalAlpha: Float = 1f,
        compositeOp: String = "source-over",
        imageSmoothingEnabled: Boolean = true,
        transform: FloatArray = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    ): Boolean

    fun drawRgbaBytes(
        surfaceElement: WebDomElement,
        rgbaBytes: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        sx: Float,
        sy: Float,
        sw: Float,
        sh: Float,
        dx: Float,
        dy: Float,
        dw: Float,
        dh: Float,
        globalAlpha: Float = 1f,
        compositeOp: String = "source-over",
        imageSmoothingEnabled: Boolean = true,
        transform: FloatArray = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    ): Boolean

    fun putImageDataRgba(
        surfaceElement: WebDomElement,
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        dirtyX: Int = 0,
        dirtyY: Int = 0,
        dirtyWidth: Int = width,
        dirtyHeight: Int = height
    )

    fun getImageDataRgba(
        surfaceElement: WebDomElement,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        fallbackBytes: ByteArray = ByteArray(0)
    ): ByteArray

    fun toDataUrl(surfaceElement: WebDomElement, mimeType: String = "image/png"): String?
}
