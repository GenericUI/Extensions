package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.web.WebDomElement

/**
 * Multiplatform (`js` + `wasmJs`) bridge in `:extensions:imageprocessing:web` that binds a
 * [WebImageBuffer] and [WebImageBuffer2DContext] directly to the browser's native 2D rendering context.
 */
expect object WebImageBuffer2DBridge {
    fun ensureSurface2D(surfaceElement: WebDomElement, width: Int, height: Int, antialias: Boolean)
    fun resizeSurface2D(surfaceElement: WebDomElement, width: Int, height: Int)
    fun clearRect(surfaceElement: WebDomElement, x: Float, y: Float, width: Float, height: Float)
    fun fillRect(surfaceElement: WebDomElement, x: Float, y: Float, width: Float, height: Float, rgbaCss: String)
    fun strokeRect(
        surfaceElement: WebDomElement,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rgbaCss: String,
        lineWidth: Float
    )
    fun fillText(
        surfaceElement: WebDomElement,
        text: String,
        x: Float,
        y: Float,
        fontCss: String,
        rgbaCss: String
    )
    fun measureTextWidth(surfaceElement: WebDomElement, text: String, fontCss: String): Float
    fun putImageDataRgba(
        surfaceElement: WebDomElement,
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        dx: Int,
        dy: Int
    )
    fun getImageDataRgba(
        surfaceElement: WebDomElement,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): ByteArray
}
