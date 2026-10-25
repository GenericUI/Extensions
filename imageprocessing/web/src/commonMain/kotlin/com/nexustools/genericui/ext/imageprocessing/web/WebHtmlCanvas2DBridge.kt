package com.nexustools.genericui.ext.imageprocessing.web

/**
 * Direct zero-overhead bridge to the HTML5 `<canvas>` 2D rendering context (`CanvasRenderingContext2D`)
 * taken directly from the underlying `HTMLCanvasElement`.
 */
expect object WebHtmlCanvas2DBridge {
    fun acquireContext2D(canvasPeer: Any?, width: Int, height: Int, antialias: Boolean): Any?
    fun resizeCanvas(canvasPeer: Any?, ctxPeer: Any?, width: Int, height: Int, antialias: Boolean)
    fun setAntialias(ctxPeer: Any?, antialias: Boolean)

    fun save(ctxPeer: Any?)
    fun restore(ctxPeer: Any?)
    fun scale(ctxPeer: Any?, sx: Float, sy: Float)
    fun rotate(ctxPeer: Any?, angleRadians: Float)
    fun translate(ctxPeer: Any?, dx: Float, dy: Float)
    fun setTransform(ctxPeer: Any?, a: Float, b: Float, c: Float, d: Float, e: Float, f: Float)
    fun resetTransform(ctxPeer: Any?)

    fun setGlobalAlpha(ctxPeer: Any?, alpha: Float)
    fun setGlobalCompositeOperation(ctxPeer: Any?, mode: String)
    fun setFillStyle(ctxPeer: Any?, cssColor: String)
    fun setStrokeStyle(ctxPeer: Any?, cssColor: String)
    fun setLineWidth(ctxPeer: Any?, width: Float)
    fun setLineCap(ctxPeer: Any?, cap: String)
    fun setLineJoin(ctxPeer: Any?, join: String)
    fun setLineDash(ctxPeer: Any?, segments: FloatArray, offset: Float)

    fun clearRect(ctxPeer: Any?, x: Float, y: Float, width: Float, height: Float)
    fun fillRect(ctxPeer: Any?, x: Float, y: Float, width: Float, height: Float)
    fun strokeRect(ctxPeer: Any?, x: Float, y: Float, width: Float, height: Float)
    fun roundRect(ctxPeer: Any?, x: Float, y: Float, width: Float, height: Float, radius: Float)

    fun beginPath(ctxPeer: Any?)
    fun closePath(ctxPeer: Any?)
    fun moveTo(ctxPeer: Any?, x: Float, y: Float)
    fun lineTo(ctxPeer: Any?, x: Float, y: Float)
    fun quadraticCurveTo(ctxPeer: Any?, cpx: Float, cpy: Float, x: Float, y: Float)
    fun bezierCurveTo(ctxPeer: Any?, cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float)
    fun arc(ctxPeer: Any?, x: Float, y: Float, radius: Float, startAngle: Float, endAngle: Float, counterClockwise: Boolean)
    fun fill(ctxPeer: Any?)
    fun stroke(ctxPeer: Any?)
    fun clip(ctxPeer: Any?)

    fun setFont(ctxPeer: Any?, cssFont: String)
    fun fillText(ctxPeer: Any?, text: String, x: Float, y: Float)
    fun strokeText(ctxPeer: Any?, text: String, x: Float, y: Float)
    fun measureTextWidth(ctxPeer: Any?, text: String): Float

    fun putImageDataRgba(ctxPeer: Any?, rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int)
    fun getImageDataRgba(ctxPeer: Any?, x: Int, y: Int, width: Int, height: Int): ByteArray

    fun stampBrushStroke(
        ctxPeer: Any?,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float,
        cssColor: String,
        opacity: Float,
        erase: Boolean
    )

    fun fillLinearGradient(
        ctxPeer: Any?,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        startCssColor: String,
        endCssColor: String,
        rectX: Float,
        rectY: Float,
        rectW: Float,
        rectH: Float
    )
}
