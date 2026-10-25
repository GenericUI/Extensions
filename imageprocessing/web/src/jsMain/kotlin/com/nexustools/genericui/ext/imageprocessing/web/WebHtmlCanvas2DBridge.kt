package com.nexustools.genericui.ext.imageprocessing.web

import com.nexustools.genericui.web.WebBrowserDomBridge
import kotlin.math.max

actual object WebHtmlCanvas2DBridge {

    actual fun acquireContext2D(canvasPeer: Any?, width: Int, height: Int, antialias: Boolean): Any? {
        if (!WebBrowserDomBridge.isBrowserEnvironment) return null
        val canvas: dynamic = canvasPeer ?: js("document.createElement('canvas')")
        val safeW = max(1, width)
        val safeH = max(1, height)
        if ((canvas.width as? Number)?.toInt() != safeW) canvas.width = safeW
        if ((canvas.height as? Number)?.toInt() != safeH) canvas.height = safeH
        val ctx: dynamic = canvas.getContext("2d") ?: return null
        ctx.imageSmoothingEnabled = antialias
        ctx.imageSmoothingQuality = "high"
        return ctx
    }

    actual fun resizeCanvas(canvasPeer: Any?, ctxPeer: Any?, width: Int, height: Int, antialias: Boolean) {
        val canvas: dynamic = canvasPeer ?: (ctxPeer?.asDynamic()?.canvas) ?: return
        val safeW = max(1, width)
        val safeH = max(1, height)
        if ((canvas.width as? Number)?.toInt() != safeW) canvas.width = safeW
        if ((canvas.height as? Number)?.toInt() != safeH) canvas.height = safeH
        val ctx: dynamic = ctxPeer ?: return
        ctx.imageSmoothingEnabled = antialias
        ctx.imageSmoothingQuality = "high"
    }

    actual fun setAntialias(ctxPeer: Any?, antialias: Boolean) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.imageSmoothingEnabled = antialias
        ctx.imageSmoothingQuality = "high"
    }

    actual fun save(ctxPeer: Any?) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.save()
    }

    actual fun restore(ctxPeer: Any?) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.restore()
    }

    actual fun scale(ctxPeer: Any?, sx: Float, sy: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.scale(sx, sy)
    }

    actual fun rotate(ctxPeer: Any?, angleRadians: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.rotate(angleRadians)
    }

    actual fun translate(ctxPeer: Any?, dx: Float, dy: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.translate(dx, dy)
    }

    actual fun setTransform(ctxPeer: Any?, a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.setTransform(a, b, c, d, e, f)
    }

    actual fun resetTransform(ctxPeer: Any?) {
        val ctx: dynamic = ctxPeer ?: return
        if (js("typeof ctx.resetTransform === 'function'") as Boolean) {
            ctx.resetTransform()
        } else {
            ctx.setTransform(1, 0, 0, 1, 0, 0)
        }
    }

    actual fun setGlobalAlpha(ctxPeer: Any?, alpha: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.globalAlpha = alpha
    }

    actual fun setGlobalCompositeOperation(ctxPeer: Any?, mode: String) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.globalCompositeOperation = mode
    }

    actual fun setFillStyle(ctxPeer: Any?, cssColor: String) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.fillStyle = cssColor
    }

    actual fun setStrokeStyle(ctxPeer: Any?, cssColor: String) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.strokeStyle = cssColor
    }

    actual fun setLineWidth(ctxPeer: Any?, width: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.lineWidth = width
    }

    actual fun setLineCap(ctxPeer: Any?, cap: String) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.lineCap = cap
    }

    actual fun setLineJoin(ctxPeer: Any?, join: String) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.lineJoin = join
    }

    actual fun setLineDash(ctxPeer: Any?, segments: FloatArray, offset: Float) {
        val ctx: dynamic = ctxPeer ?: return
        val arr: dynamic = js("[]")
        for (i in segments.indices) {
            arr.push(segments[i])
        }
        ctx.setLineDash(arr)
        ctx.lineDashOffset = offset
    }

    actual fun clearRect(ctxPeer: Any?, x: Float, y: Float, width: Float, height: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.clearRect(x, y, width, height)
    }

    actual fun fillRect(ctxPeer: Any?, x: Float, y: Float, width: Float, height: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.fillRect(x, y, width, height)
    }

    actual fun strokeRect(ctxPeer: Any?, x: Float, y: Float, width: Float, height: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.strokeRect(x, y, width, height)
    }

    actual fun roundRect(ctxPeer: Any?, x: Float, y: Float, width: Float, height: Float, radius: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.beginPath()
        if (js("typeof ctx.roundRect === 'function'") as Boolean) {
            ctx.roundRect(x, y, width, height, radius)
        } else {
            ctx.rect(x, y, width, height)
        }
        ctx.fill()
    }

    actual fun beginPath(ctxPeer: Any?) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.beginPath()
    }

    actual fun closePath(ctxPeer: Any?) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.closePath()
    }

    actual fun moveTo(ctxPeer: Any?, x: Float, y: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.moveTo(x, y)
    }

    actual fun lineTo(ctxPeer: Any?, x: Float, y: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.lineTo(x, y)
    }

    actual fun quadraticCurveTo(ctxPeer: Any?, cpx: Float, cpy: Float, x: Float, y: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.quadraticCurveTo(cpx, cpy, x, y)
    }

    actual fun bezierCurveTo(ctxPeer: Any?, cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, x, y)
    }

    actual fun arc(
        ctxPeer: Any?,
        x: Float,
        y: Float,
        radius: Float,
        startAngle: Float,
        endAngle: Float,
        counterClockwise: Boolean
    ) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.arc(x, y, radius, startAngle, endAngle, counterClockwise)
    }

    actual fun fill(ctxPeer: Any?) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.fill()
    }

    actual fun stroke(ctxPeer: Any?) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.stroke()
    }

    actual fun clip(ctxPeer: Any?) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.clip()
    }

    actual fun setFont(ctxPeer: Any?, cssFont: String) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.font = cssFont
    }

    actual fun fillText(ctxPeer: Any?, text: String, x: Float, y: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.fillText(text, x, y)
    }

    actual fun strokeText(ctxPeer: Any?, text: String, x: Float, y: Float) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.strokeText(text, x, y)
    }

    actual fun measureTextWidth(ctxPeer: Any?, text: String): Float {
        val ctx: dynamic = ctxPeer ?: return text.length * 8f
        val metrics: dynamic = ctx.measureText(text)
        return (metrics?.width as? Number)?.toFloat() ?: (text.length * 8f)
    }

    actual fun putImageDataRgba(ctxPeer: Any?, rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) {
        val ctx: dynamic = ctxPeer ?: return
        val safeW = max(1, width)
        val safeH = max(1, height)
        val imgData: dynamic = ctx.createImageData(safeW, safeH)
        val data: dynamic = imgData.data
        val count = minOf(rgbaBytes.size, safeW * safeH * 4)
        for (i in 0 until count) {
            data[i] = rgbaBytes[i].toInt() and 0xFF
        }
        ctx.putImageData(imgData, dx, dy)
    }

    actual fun getImageDataRgba(ctxPeer: Any?, x: Int, y: Int, width: Int, height: Int): ByteArray {
        val safeW = max(1, width)
        val safeH = max(1, height)
        val ctx: dynamic = ctxPeer ?: return ByteArray(safeW * safeH * 4)
        val imgData: dynamic = ctx.getImageData(x, y, safeW, safeH)
        val data: dynamic = imgData.data
        val len = (data.length as? Number)?.toInt() ?: (safeW * safeH * 4)
        return ByteArray(len) { idx -> ((data[idx] as Number).toInt() and 0xFF).toByte() }
    }

    actual fun stampBrushStroke(
        ctxPeer: Any?,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float,
        cssColor: String,
        opacity: Float,
        erase: Boolean
    ) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.save()
        ctx.globalAlpha = opacity
        ctx.globalCompositeOperation = if (erase) "destination-out" else "source-over"
        ctx.strokeStyle = cssColor
        ctx.lineWidth = max(1f, radius * 2f)
        ctx.lineCap = "round"
        ctx.lineJoin = "round"
        ctx.beginPath()
        ctx.moveTo(x0, y0)
        ctx.lineTo(x1, y1)
        ctx.stroke()
        ctx.restore()
    }

    actual fun fillLinearGradient(
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
    ) {
        val ctx: dynamic = ctxPeer ?: return
        ctx.save()
        val grad: dynamic = ctx.createLinearGradient(x0, y0, x1, y1)
        grad.addColorStop(0.0, startCssColor)
        grad.addColorStop(1.0, endCssColor)
        ctx.fillStyle = grad
        ctx.fillRect(rectX, rectY, rectW, rectH)
        ctx.restore()
    }
}
