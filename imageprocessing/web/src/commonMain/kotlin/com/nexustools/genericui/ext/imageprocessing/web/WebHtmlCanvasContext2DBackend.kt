package com.nexustools.genericui.ext.imageprocessing.web

import com.nexustools.genericui.ext.imageprocessing.context2d.ImageProcessingContext2DBackend
import com.nexustools.genericui.style.RgbaColor

/**
 * Web implementation of [ImageProcessingContext2DBackend] (`:extensions:imageprocessing:web`).
 *
 * Operates **directly** on the underlying HTML5 `<canvas>` 2D rendering context
 * (`CanvasRenderingContext2D` acquired from `imageBuffer.canvasElement.nativeBrowserPeer` via
 * [WebHtmlCanvas2DBridge]), removing all intermediate buffer or WebGPU/OffscreenCanvas overhead.
 */
class WebHtmlCanvasContext2DBackend(
    override val imageBuffer: WebCanvasImageBuffer = WebCanvasImageBuffer(),
    initialAntialias: Boolean = true
) : ImageProcessingContext2DBackend {

    override val width: Int
        get() = imageBuffer.width

    override val height: Int
        get() = imageBuffer.height

    /**
     * Direct handle to the browser's `CanvasRenderingContext2D` (`canvas.getContext("2d")`).
     */
    val ctxPeer: Any? = WebHtmlCanvas2DBridge.acquireContext2D(
        canvasPeer = imageBuffer.canvasElement.nativeBrowserPeer,
        width = imageBuffer.width,
        height = imageBuffer.height,
        antialias = initialAntialias
    )

    override var antialias: Boolean = initialAntialias
        set(value) {
            field = value
            WebHtmlCanvas2DBridge.setAntialias(ctxPeer, value)
        }

    override val swapCount: Long
        get() = imageBuffer.flushCount

    override fun resize(newWidth: Int, newHeight: Int) {
        imageBuffer.resize(newWidth, newHeight)
    }

    override fun swapBuffers() {
        imageBuffer.flush()
    }

    override fun save() {
        WebHtmlCanvas2DBridge.save(ctxPeer)
    }

    override fun restore() {
        WebHtmlCanvas2DBridge.restore(ctxPeer)
    }

    override fun scale(sx: Float, sy: Float) {
        WebHtmlCanvas2DBridge.scale(ctxPeer, sx, sy)
    }

    override fun rotate(angleRadians: Float) {
        WebHtmlCanvas2DBridge.rotate(ctxPeer, angleRadians)
    }

    override fun translate(dx: Float, dy: Float) {
        WebHtmlCanvas2DBridge.translate(ctxPeer, dx, dy)
    }

    override fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        WebHtmlCanvas2DBridge.setTransform(ctxPeer, a, b, c, d, e, f)
    }

    override fun resetTransform() {
        WebHtmlCanvas2DBridge.resetTransform(ctxPeer)
    }

    override fun setGlobalAlpha(alpha: Float) {
        WebHtmlCanvas2DBridge.setGlobalAlpha(ctxPeer, alpha.coerceIn(0f, 1f))
    }

    override fun setGlobalCompositeOperation(mode: String) {
        WebHtmlCanvas2DBridge.setGlobalCompositeOperation(ctxPeer, mode)
    }

    override fun setFillColor(color: RgbaColor) {
        WebHtmlCanvas2DBridge.setFillStyle(ctxPeer, color.toCssRgba())
    }

    override fun setStrokeColor(color: RgbaColor) {
        WebHtmlCanvas2DBridge.setStrokeStyle(ctxPeer, color.toCssRgba())
    }

    override fun setLineWidth(width: Float) {
        WebHtmlCanvas2DBridge.setLineWidth(ctxPeer, width.coerceAtLeast(0.1f))
    }

    override fun setLineCap(cap: String) {
        WebHtmlCanvas2DBridge.setLineCap(ctxPeer, cap)
    }

    override fun setLineJoin(join: String) {
        WebHtmlCanvas2DBridge.setLineJoin(ctxPeer, join)
    }

    override fun setLineDash(segments: FloatArray, offset: Float) {
        WebHtmlCanvas2DBridge.setLineDash(ctxPeer, segments, offset)
    }

    override fun clearRect(x: Float, y: Float, width: Float, height: Float) {
        WebHtmlCanvas2DBridge.clearRect(ctxPeer, x, y, width, height)
    }

    override fun fillRect(x: Float, y: Float, width: Float, height: Float) {
        WebHtmlCanvas2DBridge.fillRect(ctxPeer, x, y, width, height)
    }

    override fun strokeRect(x: Float, y: Float, width: Float, height: Float) {
        WebHtmlCanvas2DBridge.strokeRect(ctxPeer, x, y, width, height)
    }

    override fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        WebHtmlCanvas2DBridge.roundRect(ctxPeer, x, y, width, height, radius)
    }

    override fun beginPath() {
        WebHtmlCanvas2DBridge.beginPath(ctxPeer)
    }

    override fun closePath() {
        WebHtmlCanvas2DBridge.closePath(ctxPeer)
    }

    override fun moveTo(x: Float, y: Float) {
        WebHtmlCanvas2DBridge.moveTo(ctxPeer, x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        WebHtmlCanvas2DBridge.lineTo(ctxPeer, x, y)
    }

    override fun quadraticCurveTo(cpx: Float, cpy: Float, x: Float, y: Float) {
        WebHtmlCanvas2DBridge.quadraticCurveTo(ctxPeer, cpx, cpy, x, y)
    }

    override fun bezierCurveTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) {
        WebHtmlCanvas2DBridge.bezierCurveTo(ctxPeer, cp1x, cp1y, cp2x, cp2y, x, y)
    }

    override fun arc(x: Float, y: Float, radius: Float, startAngle: Float, endAngle: Float, counterClockwise: Boolean) {
        WebHtmlCanvas2DBridge.arc(ctxPeer, x, y, radius, startAngle, endAngle, counterClockwise)
    }

    override fun fill() {
        WebHtmlCanvas2DBridge.fill(ctxPeer)
    }

    override fun stroke() {
        WebHtmlCanvas2DBridge.stroke(ctxPeer)
    }

    override fun clip() {
        WebHtmlCanvas2DBridge.clip(ctxPeer)
    }

    override fun setFont(fontFamily: String, fontSizePx: Float, fontWeight: Int, italic: Boolean) {
        val stylePrefix = if (italic) "italic " else ""
        WebHtmlCanvas2DBridge.setFont(ctxPeer, "$stylePrefix$fontWeight ${fontSizePx}px $fontFamily")
    }

    override fun fillText(text: String, x: Float, y: Float) {
        WebHtmlCanvas2DBridge.fillText(ctxPeer, text, x, y)
    }

    override fun strokeText(text: String, x: Float, y: Float) {
        WebHtmlCanvas2DBridge.strokeText(ctxPeer, text, x, y)
    }

    override fun measureTextWidth(text: String): Float {
        return WebHtmlCanvas2DBridge.measureTextWidth(ctxPeer, text)
    }

    override fun putImageDataRgba(rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) {
        imageBuffer.writePixelsRgba(rgbaBytes, width, height, dx, dy)
    }

    override fun getImageDataRgba(x: Int, y: Int, width: Int, height: Int): ByteArray {
        return imageBuffer.readPixelsRgba(x, y, width, height)
    }

    override fun drawImageBuffer(buffer: com.nexustools.genericui.image.ImageBuffer, dx: Float, dy: Float) {
        val rgba = buffer.asRgba8888()
        WebHtmlCanvas2DBridge.putImageDataRgba(ctxPeer, rgba.bytes, rgba.width, rgba.height, dx.toInt(), dy.toInt())
    }

    override fun presentFrontBufferRgba(): ByteArray = imageBuffer.directRgbaBytes()

    override fun presentFrontBufferBgra(): ByteArray = imageBuffer.directBgraBytes()

    override fun toImageBuffer(): com.nexustools.genericui.image.ImageBuffer = imageBuffer.toImageBuffer()

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
        WebHtmlCanvas2DBridge.stampBrushStroke(
            ctxPeer = ctxPeer,
            x0 = x0,
            y0 = y0,
            x1 = x1,
            y1 = y1,
            radius = radius,
            cssColor = color.toCssRgba(),
            opacity = opacity,
            erase = erase
        )
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
        WebHtmlCanvas2DBridge.fillLinearGradient(
            ctxPeer = ctxPeer,
            x0 = x0,
            y0 = y0,
            x1 = x1,
            y1 = y1,
            startCssColor = startColor.toCssRgba(),
            endCssColor = endColor.toCssRgba(),
            rectX = rectX,
            rectY = rectY,
            rectW = rectW,
            rectH = rectH
        )
    }

    override fun floodFill(seedX: Int, seedY: Int, fillColor: RgbaColor, tolerance: Int) {
        val w = width
        val h = height
        if (seedX !in 0 until w || seedY !in 0 until h) return
        val bytes = getImageDataRgba(0, 0, w, h)
        if (bytes.size < w * h * 4) return
        val seedIdx = (seedY * w + seedX) * 4
        val sr = bytes[seedIdx].toInt() and 0xFF
        val sg = bytes[seedIdx + 1].toInt() and 0xFF
        val sb = bytes[seedIdx + 2].toInt() and 0xFF
        val sa = bytes[seedIdx + 3].toInt() and 0xFF
        val fr = fillColor.r.coerceIn(0, 255)
        val fg = fillColor.g.coerceIn(0, 255)
        val fb = fillColor.b.coerceIn(0, 255)
        val fa = fillColor.a.coerceIn(0, 255)
        if (sr == fr && sg == fg && sb == fb && sa == fa) return
        val tol = tolerance.coerceIn(0, 255)
        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        val startFlat = seedY * w + seedX
        visited[startFlat] = true
        queue[tail++] = startFlat
        while (head < tail) {
            val flat = queue[head++]
            val px = flat % w
            val py = flat / w
            val idx = flat * 4
            bytes[idx] = fr.toByte()
            bytes[idx + 1] = fg.toByte()
            bytes[idx + 2] = fb.toByte()
            bytes[idx + 3] = fa.toByte()
            val neighbors = intArrayOf(
                if (px > 0) flat - 1 else -1,
                if (px + 1 < w) flat + 1 else -1,
                if (py > 0) flat - w else -1,
                if (py + 1 < h) flat + w else -1
            )
            for (nFlat in neighbors) {
                if (nFlat < 0 || visited[nFlat]) continue
                val nIdx = nFlat * 4
                val nr = bytes[nIdx].toInt() and 0xFF
                val ng = bytes[nIdx + 1].toInt() and 0xFF
                val nb = bytes[nIdx + 2].toInt() and 0xFF
                val na = bytes[nIdx + 3].toInt() and 0xFF
                if (kotlin.math.abs(nr - sr) <= tol &&
                    kotlin.math.abs(ng - sg) <= tol &&
                    kotlin.math.abs(nb - sb) <= tol &&
                    kotlin.math.abs(na - sa) <= tol
                ) {
                    visited[nFlat] = true
                    queue[tail++] = nFlat
                }
            }
        }
        putImageDataRgba(bytes, w, h, 0, 0)
    }

    private fun RgbaColor.toCssRgba(): String = "rgba($r, $g, $b, $a)"
}
