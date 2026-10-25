@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.nexustools.genericui.ext.imageprocessing.web

import com.nexustools.genericui.web.WebBrowserDomBridge
import kotlin.math.max

@JsFun("(canvasPeer, hasCanvas, w, h, aa) => { " +
    "const c = hasCanvas ? canvasPeer : document.createElement('canvas'); " +
    "if (c.width !== w) c.width = w; " +
    "if (c.height !== h) c.height = h; " +
    "const ctx = c.getContext('2d'); " +
    "if (ctx) { ctx.imageSmoothingEnabled = aa; ctx.imageSmoothingQuality = 'high'; } " +
    "return ctx; }")
private external fun wasmAcquireCtx2D(canvasPeer: JsAny?, hasCanvas: Boolean, w: Int, h: Int, aa: Boolean): JsAny?

@JsFun("(ctx, w, h, aa) => { " +
    "const c = ctx.canvas; " +
    "if (c) { if (c.width !== w) c.width = w; if (c.height !== h) c.height = h; } " +
    "ctx.imageSmoothingEnabled = aa; ctx.imageSmoothingQuality = 'high'; }")
private external fun wasmResizeCtx2D(ctx: JsAny, w: Int, h: Int, aa: Boolean)

@JsFun("(ctx, aa) => { ctx.imageSmoothingEnabled = aa; ctx.imageSmoothingQuality = 'high'; }")
private external fun wasmSetAa(ctx: JsAny, aa: Boolean)

@JsFun("(ctx) => { ctx.save(); }")
private external fun wasmSave(ctx: JsAny)

@JsFun("(ctx) => { ctx.restore(); }")
private external fun wasmRestore(ctx: JsAny)

@JsFun("(ctx, sx, sy) => { ctx.scale(sx, sy); }")
private external fun wasmScale(ctx: JsAny, sx: Float, sy: Float)

@JsFun("(ctx, rad) => { ctx.rotate(rad); }")
private external fun wasmRotate(ctx: JsAny, rad: Float)

@JsFun("(ctx, dx, dy) => { ctx.translate(dx, dy); }")
private external fun wasmTranslate(ctx: JsAny, dx: Float, dy: Float)

@JsFun("(ctx, a, b, c, d, e, f) => { ctx.setTransform(a, b, c, d, e, f); }")
private external fun wasmSetTransform(ctx: JsAny, a: Float, b: Float, c: Float, d: Float, e: Float, f: Float)

@JsFun("(ctx) => { if (typeof ctx.resetTransform === 'function') ctx.resetTransform(); else ctx.setTransform(1,0,0,1,0,0); }")
private external fun wasmResetTransform(ctx: JsAny)

@JsFun("(ctx, a) => { ctx.globalAlpha = a; }")
private external fun wasmSetGlobalAlpha(ctx: JsAny, a: Float)

@JsFun("(ctx, mode) => { ctx.globalCompositeOperation = mode; }")
private external fun wasmSetGlobalCompositeOp(ctx: JsAny, mode: String)

@JsFun("(ctx, color) => { ctx.fillStyle = color; }")
private external fun wasmSetFillStyle(ctx: JsAny, color: String)

@JsFun("(ctx, color) => { ctx.strokeStyle = color; }")
private external fun wasmSetStrokeStyle(ctx: JsAny, color: String)

@JsFun("(ctx, w) => { ctx.lineWidth = w; }")
private external fun wasmSetLineWidth(ctx: JsAny, w: Float)

@JsFun("(ctx, cap) => { ctx.lineCap = cap; }")
private external fun wasmSetLineCap(ctx: JsAny, cap: String)

@JsFun("(ctx, join) => { ctx.lineJoin = join; }")
private external fun wasmSetLineJoin(ctx: JsAny, join: String)

@JsFun("(ctx, offset) => { ctx.lineDashOffset = offset; }")
private external fun wasmSetLineDashOffset(ctx: JsAny, offset: Float)

@JsFun("(ctx, x, y, w, h) => { ctx.clearRect(x, y, w, h); }")
private external fun wasmClearRect(ctx: JsAny, x: Float, y: Float, w: Float, h: Float)

@JsFun("(ctx, x, y, w, h) => { ctx.fillRect(x, y, w, h); }")
private external fun wasmFillRect(ctx: JsAny, x: Float, y: Float, w: Float, h: Float)

@JsFun("(ctx, x, y, w, h) => { ctx.strokeRect(x, y, w, h); }")
private external fun wasmStrokeRect(ctx: JsAny, x: Float, y: Float, w: Float, h: Float)

@JsFun("(ctx, x, y, w, h, r) => { ctx.beginPath(); if (typeof ctx.roundRect === 'function') ctx.roundRect(x, y, w, h, r); else ctx.rect(x, y, w, h); ctx.fill(); }")
private external fun wasmRoundRect(ctx: JsAny, x: Float, y: Float, w: Float, h: Float, r: Float)

@JsFun("(ctx) => { ctx.beginPath(); }")
private external fun wasmBeginPath(ctx: JsAny)

@JsFun("(ctx) => { ctx.closePath(); }")
private external fun wasmClosePath(ctx: JsAny)

@JsFun("(ctx, x, y) => { ctx.moveTo(x, y); }")
private external fun wasmMoveTo(ctx: JsAny, x: Float, y: Float)

@JsFun("(ctx, x, y) => { ctx.lineTo(x, y); }")
private external fun wasmLineTo(ctx: JsAny, x: Float, y: Float)

@JsFun("(ctx, cpx, cpy, x, y) => { ctx.quadraticCurveTo(cpx, cpy, x, y); }")
private external fun wasmQuadCurveTo(ctx: JsAny, cpx: Float, cpy: Float, x: Float, y: Float)

@JsFun("(ctx, cp1x, cp1y, cp2x, cp2y, x, y) => { ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, x, y); }")
private external fun wasmBezierCurveTo(ctx: JsAny, cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float)

@JsFun("(ctx, x, y, r, sa, ea, ccw) => { ctx.arc(x, y, r, sa, ea, ccw); }")
private external fun wasmArc(ctx: JsAny, x: Float, y: Float, r: Float, sa: Float, ea: Float, ccw: Boolean)

@JsFun("(ctx) => { ctx.fill(); }")
private external fun wasmFill(ctx: JsAny)

@JsFun("(ctx) => { ctx.stroke(); }")
private external fun wasmStroke(ctx: JsAny)

@JsFun("(ctx) => { ctx.clip(); }")
private external fun wasmClip(ctx: JsAny)

@JsFun("(ctx, font) => { ctx.font = font; }")
private external fun wasmSetFont(ctx: JsAny, font: String)

@JsFun("(ctx, text, x, y) => { ctx.fillText(text, x, y); }")
private external fun wasmFillText(ctx: JsAny, text: String, x: Float, y: Float)

@JsFun("(ctx, text, x, y) => { ctx.strokeText(text, x, y); }")
private external fun wasmStrokeText(ctx: JsAny, text: String, x: Float, y: Float)

@JsFun("(ctx, text) => { const m = ctx.measureText(text); return m && typeof m.width === 'number' ? m.width : (text.length * 8.0); }")
private external fun wasmMeasureText(ctx: JsAny, text: String): Float

@JsFun("(ctx, w, h) => ctx.createImageData(w, h)")
private external fun wasmCreateImageData(ctx: JsAny, w: Int, h: Int): JsAny

@JsFun("(imgData, idx, v) => { imgData.data[idx] = v; }")
private external fun wasmSetImageDataByte(imgData: JsAny, idx: Int, v: Int)

@JsFun("(ctx, imgData, dx, dy) => { ctx.putImageData(imgData, dx, dy); }")
private external fun wasmPutImageData(ctx: JsAny, imgData: JsAny, dx: Int, dy: Int)

@JsFun("(ctx, x, y, w, h) => ctx.getImageData(x, y, w, h)")
private external fun wasmGetImageData(ctx: JsAny, x: Int, y: Int, w: Int, h: Int): JsAny

@JsFun("(imgData, idx) => imgData.data[idx] | 0")
private external fun wasmGetImageDataByte(imgData: JsAny, idx: Int): Int

@JsFun("(ctx, x0, y0, x1, y1, radius, color, opacity, erase) => { " +
    "ctx.save(); " +
    "ctx.globalAlpha = opacity; " +
    "ctx.globalCompositeOperation = erase ? 'destination-out' : 'source-over'; " +
    "ctx.strokeStyle = color; " +
    "ctx.lineWidth = Math.max(1.0, radius * 2.0); " +
    "ctx.lineCap = 'round'; ctx.lineJoin = 'round'; " +
    "ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.stroke(); " +
    "ctx.restore(); }")
private external fun wasmStampBrushStroke(
    ctx: JsAny,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    radius: Float,
    color: String,
    opacity: Float,
    erase: Boolean
)

@JsFun("(ctx, x0, y0, x1, y1, c0, c1, rx, ry, rw, rh) => { " +
    "ctx.save(); " +
    "const g = ctx.createLinearGradient(x0, y0, x1, y1); " +
    "g.addColorStop(0.0, c0); g.addColorStop(1.0, c1); " +
    "ctx.fillStyle = g; ctx.fillRect(rx, ry, rw, rh); " +
    "ctx.restore(); }")
private external fun wasmFillLinearGradient(
    ctx: JsAny,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    c0: String,
    c1: String,
    rx: Float,
    ry: Float,
    rw: Float,
    rh: Float
)

actual object WebHtmlCanvas2DBridge {

    actual fun acquireContext2D(canvasPeer: Any?, width: Int, height: Int, antialias: Boolean): Any? {
        if (!WebBrowserDomBridge.isBrowserEnvironment) return null
        val jsPeer = canvasPeer as? JsAny
        return wasmAcquireCtx2D(jsPeer, jsPeer != null, max(1, width), max(1, height), antialias)
    }

    actual fun resizeCanvas(canvasPeer: Any?, ctxPeer: Any?, width: Int, height: Int, antialias: Boolean) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmResizeCtx2D(ctx, max(1, width), max(1, height), antialias)
    }

    actual fun setAntialias(ctxPeer: Any?, antialias: Boolean) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmSetAa(ctx, antialias)
    }

    actual fun save(ctxPeer: Any?) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmSave(ctx)
    }

    actual fun restore(ctxPeer: Any?) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmRestore(ctx)
    }

    actual fun scale(ctxPeer: Any?, sx: Float, sy: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmScale(ctx, sx, sy)
    }

    actual fun rotate(ctxPeer: Any?, angleRadians: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmRotate(ctx, angleRadians)
    }

    actual fun translate(ctxPeer: Any?, dx: Float, dy: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmTranslate(ctx, dx, dy)
    }

    actual fun setTransform(ctxPeer: Any?, a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmSetTransform(ctx, a, b, c, d, e, f)
    }

    actual fun resetTransform(ctxPeer: Any?) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmResetTransform(ctx)
    }

    actual fun setGlobalAlpha(ctxPeer: Any?, alpha: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmSetGlobalAlpha(ctx, alpha)
    }

    actual fun setGlobalCompositeOperation(ctxPeer: Any?, mode: String) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmSetGlobalCompositeOp(ctx, mode)
    }

    actual fun setFillStyle(ctxPeer: Any?, cssColor: String) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmSetFillStyle(ctx, cssColor)
    }

    actual fun setStrokeStyle(ctxPeer: Any?, cssColor: String) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmSetStrokeStyle(ctx, cssColor)
    }

    actual fun setLineWidth(ctxPeer: Any?, width: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmSetLineWidth(ctx, width)
    }

    actual fun setLineCap(ctxPeer: Any?, cap: String) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmSetLineCap(ctx, cap)
    }

    actual fun setLineJoin(ctxPeer: Any?, join: String) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmSetLineJoin(ctx, join)
    }

    actual fun setLineDash(ctxPeer: Any?, segments: FloatArray, offset: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmSetLineDashOffset(ctx, offset)
    }

    actual fun clearRect(ctxPeer: Any?, x: Float, y: Float, width: Float, height: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmClearRect(ctx, x, y, width, height)
    }

    actual fun fillRect(ctxPeer: Any?, x: Float, y: Float, width: Float, height: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmFillRect(ctx, x, y, width, height)
    }

    actual fun strokeRect(ctxPeer: Any?, x: Float, y: Float, width: Float, height: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmStrokeRect(ctx, x, y, width, height)
    }

    actual fun roundRect(ctxPeer: Any?, x: Float, y: Float, width: Float, height: Float, radius: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmRoundRect(ctx, x, y, width, height, radius)
    }

    actual fun beginPath(ctxPeer: Any?) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmBeginPath(ctx)
    }

    actual fun closePath(ctxPeer: Any?) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmClosePath(ctx)
    }

    actual fun moveTo(ctxPeer: Any?, x: Float, y: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmMoveTo(ctx, x, y)
    }

    actual fun lineTo(ctxPeer: Any?, x: Float, y: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmLineTo(ctx, x, y)
    }

    actual fun quadraticCurveTo(ctxPeer: Any?, cpx: Float, cpy: Float, x: Float, y: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmQuadCurveTo(ctx, cpx, cpy, x, y)
    }

    actual fun bezierCurveTo(ctxPeer: Any?, cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmBezierCurveTo(ctx, cp1x, cp1y, cp2x, cp2y, x, y)
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
        val ctx = ctxPeer as? JsAny ?: return
        wasmArc(ctx, x, y, radius, startAngle, endAngle, counterClockwise)
    }

    actual fun fill(ctxPeer: Any?) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmFill(ctx)
    }

    actual fun stroke(ctxPeer: Any?) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmStroke(ctx)
    }

    actual fun clip(ctxPeer: Any?) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmClip(ctx)
    }

    actual fun setFont(ctxPeer: Any?, cssFont: String) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmSetFont(ctx, cssFont)
    }

    actual fun fillText(ctxPeer: Any?, text: String, x: Float, y: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmFillText(ctx, text, x, y)
    }

    actual fun strokeText(ctxPeer: Any?, text: String, x: Float, y: Float) {
        val ctx = ctxPeer as? JsAny ?: return
        wasmStrokeText(ctx, text, x, y)
    }

    actual fun measureTextWidth(ctxPeer: Any?, text: String): Float {
        val ctx = ctxPeer as? JsAny ?: return text.length * 8f
        return wasmMeasureText(ctx, text)
    }

    actual fun putImageDataRgba(ctxPeer: Any?, rgbaBytes: ByteArray, width: Int, height: Int, dx: Int, dy: Int) {
        val ctx = ctxPeer as? JsAny ?: return
        val safeW = max(1, width)
        val safeH = max(1, height)
        val imgData = wasmCreateImageData(ctx, safeW, safeH)
        val count = minOf(rgbaBytes.size, safeW * safeH * 4)
        for (i in 0 until count) {
            wasmSetImageDataByte(imgData, i, rgbaBytes[i].toInt() and 0xFF)
        }
        wasmPutImageData(ctx, imgData, dx, dy)
    }

    actual fun getImageDataRgba(ctxPeer: Any?, x: Int, y: Int, width: Int, height: Int): ByteArray {
        val safeW = max(1, width)
        val safeH = max(1, height)
        val ctx = ctxPeer as? JsAny ?: return ByteArray(safeW * safeH * 4)
        val imgData = wasmGetImageData(ctx, x, y, safeW, safeH)
        val total = safeW * safeH * 4
        return ByteArray(total) { idx -> (wasmGetImageDataByte(imgData, idx) and 0xFF).toByte() }
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
        val ctx = ctxPeer as? JsAny ?: return
        wasmStampBrushStroke(ctx, x0, y0, x1, y1, radius, cssColor, opacity, erase)
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
        val ctx = ctxPeer as? JsAny ?: return
        wasmFillLinearGradient(ctx, x0, y0, x1, y1, startCssColor, endCssColor, rectX, rectY, rectW, rectH)
    }
}
