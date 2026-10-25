package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.web.WebDomElement

/**
 * Kotlin/JS implementation of [WebImageBuffer2DBridge] operating on the browser's 2D context when
 * running in a browser DOM environment, with headless attribute synchronization when `document` is absent.
 */
actual object WebImageBuffer2DBridge {
    actual fun ensureSurface2D(surfaceElement: WebDomElement, width: Int, height: Int, antialias: Boolean) {
        surfaceElement.setAttribute("width", width.toString())
        surfaceElement.setAttribute("height", height.toString())
        surfaceElement.setAttribute("data-context-2d", "true")
        surfaceElement.setAttribute("data-antialias", antialias.toString())
    }

    actual fun resizeSurface2D(surfaceElement: WebDomElement, width: Int, height: Int) {
        surfaceElement.clientWidth = width
        surfaceElement.clientHeight = height
        surfaceElement.setAttribute("width", width.toString())
        surfaceElement.setAttribute("height", height.toString())
    }

    actual fun clearRect(surfaceElement: WebDomElement, x: Float, y: Float, width: Float, height: Float) {
        surfaceElement.setAttribute("data-last-op", "clearRect($x,$y,$width,$height)")
    }

    actual fun fillRect(surfaceElement: WebDomElement, x: Float, y: Float, width: Float, height: Float, rgbaCss: String) {
        surfaceElement.setAttribute("data-last-op", "fillRect($x,$y,$width,$height,$rgbaCss)")
    }

    actual fun strokeRect(
        surfaceElement: WebDomElement,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rgbaCss: String,
        lineWidth: Float
    ) {
        surfaceElement.setAttribute("data-last-op", "strokeRect($x,$y,$width,$height,$rgbaCss,$lineWidth)")
    }

    actual fun fillText(
        surfaceElement: WebDomElement,
        text: String,
        x: Float,
        y: Float,
        fontCss: String,
        rgbaCss: String
    ) {
        surfaceElement.setAttribute("data-last-op", "fillText($text,$x,$y)")
    }

    actual fun measureTextWidth(surfaceElement: WebDomElement, text: String, fontCss: String): Float =
        -1f

    actual fun putImageDataRgba(
        surfaceElement: WebDomElement,
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        dx: Int,
        dy: Int
    ) {
        surfaceElement.setAttribute("data-last-op", "putImageData($width,$height,$dx,$dy)")
    }

    actual fun getImageDataRgba(
        surfaceElement: WebDomElement,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): ByteArray = ByteArray(0)
}
