@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.web.WebBrowserDomBridge
import com.nexustools.genericui.web.WebDomElement

@JsFun("(peer, w, h) => { try { if (peer.width !== w) peer.width = w; if (peer.height !== h) peer.height = h; } catch (_e) {} }")
private external fun wasmEnsureCanvasSize(peer: JsAny, w: Int, h: Int)

@JsFun("(peer, newW, newH) => { " +
    "try { " +
    "  const oldW = peer.width | 0; const oldH = peer.height | 0; " +
    "  if ((oldW !== newW || oldH !== newH) && oldW > 0 && oldH > 0 && typeof peer.getContext === 'function') { " +
    "    const tmp = document.createElement('canvas'); " +
    "    tmp.width = oldW; tmp.height = oldH; " +
    "    const tmpCtx = tmp.getContext('2d'); " +
    "    if (tmpCtx) tmpCtx.drawImage(peer, 0, 0); " +
    "    return tmp; " +
    "  } " +
    "} catch (_e) {} " +
    "return null; }")
private external fun wasmSnapshotCanvasBeforeResize(peer: JsAny, newW: Int, newH: Int): JsAny?

@JsFun("(peer, newW, newH, snapshot) => { " +
    "try { " +
    "  if (peer.width !== newW) peer.width = newW; " +
    "  if (peer.height !== newH) peer.height = newH; " +
    "  if (snapshot && typeof peer.getContext === 'function') { " +
    "    const ctx = peer.getContext('2d'); " +
    "    if (ctx) ctx.drawImage(snapshot, 0, 0); " +
    "  } " +
    "} catch (_e) {} }")
private external fun wasmFinishResizeSurface2D(peer: JsAny, newW: Int, newH: Int, snapshot: JsAny?)

@JsFun("(peer, x, y, w, h, a, b, c, d, e, f) => { " +
    "try { " +
    "  if (typeof peer.getContext !== 'function') return; " +
    "  const ctx = peer.getContext('2d'); if (!ctx) return; " +
    "  ctx.save(); " +
    "  ctx.setTransform(a, b, c, d, e, f); " +
    "  ctx.clearRect(x, y, w, h); " +
    "  ctx.restore(); " +
    "} catch (_e) {} }")
private external fun wasmClearRect(
    peer: JsAny,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    a: Float,
    b: Float,
    c: Float,
    d: Float,
    e: Float,
    f: Float
)

@JsFun("(peer, x, y, w, h, fillCss, alpha, compOp, a, b, c, d, e, f) => { " +
    "try { " +
    "  if (typeof peer.getContext !== 'function') return; " +
    "  const ctx = peer.getContext('2d'); if (!ctx) return; " +
    "  ctx.save(); " +
    "  ctx.setTransform(a, b, c, d, e, f); " +
    "  ctx.globalAlpha = alpha; " +
    "  ctx.globalCompositeOperation = compOp; " +
    "  ctx.fillStyle = fillCss; " +
    "  ctx.fillRect(x, y, w, h); " +
    "  ctx.restore(); " +
    "} catch (_e) {} }")
private external fun wasmFillRect(
    peer: JsAny,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    fillCss: String,
    alpha: Float,
    compOp: String,
    a: Float,
    b: Float,
    c: Float,
    d: Float,
    e: Float,
    f: Float
)

@JsFun("(peer, x, y, w, h, strokeCss, lineWidth, lineCap, lineJoin, miterLimit, alpha, compOp, a, b, c, d, e, f) => { " +
    "try { " +
    "  if (typeof peer.getContext !== 'function') return; " +
    "  const ctx = peer.getContext('2d'); if (!ctx) return; " +
    "  ctx.save(); " +
    "  ctx.setTransform(a, b, c, d, e, f); " +
    "  ctx.globalAlpha = alpha; " +
    "  ctx.globalCompositeOperation = compOp; " +
    "  ctx.strokeStyle = strokeCss; " +
    "  ctx.lineWidth = lineWidth; " +
    "  ctx.lineCap = lineCap; " +
    "  ctx.lineJoin = lineJoin; " +
    "  ctx.miterLimit = miterLimit; " +
    "  ctx.strokeRect(x, y, w, h); " +
    "  ctx.restore(); " +
    "} catch (_e) {} }")
private external fun wasmStrokeRect(
    peer: JsAny,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    strokeCss: String,
    lineWidth: Float,
    lineCap: String,
    lineJoin: String,
    miterLimit: Float,
    alpha: Float,
    compOp: String,
    a: Float,
    b: Float,
    c: Float,
    d: Float,
    e: Float,
    f: Float
)

@JsFun("(peer, text, x, y, hasMaxWidth, maxWidth, fontCss, fillCss, alignCss, baselineCss, alpha, compOp, a, b, c, d, e, f) => { " +
    "try { " +
    "  if (typeof peer.getContext !== 'function') return; " +
    "  const ctx = peer.getContext('2d'); if (!ctx) return; " +
    "  ctx.save(); " +
    "  ctx.setTransform(a, b, c, d, e, f); " +
    "  ctx.globalAlpha = alpha; " +
    "  ctx.globalCompositeOperation = compOp; " +
    "  ctx.font = fontCss; " +
    "  ctx.fillStyle = fillCss; " +
    "  ctx.textAlign = alignCss; " +
    "  ctx.textBaseline = baselineCss; " +
    "  if (hasMaxWidth) ctx.fillText(text, x, y, maxWidth); else ctx.fillText(text, x, y); " +
    "  ctx.restore(); " +
    "} catch (_e) {} }")
private external fun wasmFillText(
    peer: JsAny,
    text: String,
    x: Float,
    y: Float,
    hasMaxWidth: Boolean,
    maxWidth: Float,
    fontCss: String,
    fillCss: String,
    alignCss: String,
    baselineCss: String,
    alpha: Float,
    compOp: String,
    a: Float,
    b: Float,
    c: Float,
    d: Float,
    e: Float,
    f: Float
)

@JsFun("(peer, text, x, y, hasMaxWidth, maxWidth, fontCss, strokeCss, lineWidth, lineCap, lineJoin, miterLimit, alignCss, baselineCss, alpha, compOp, a, b, c, d, e, f) => { " +
    "try { " +
    "  if (typeof peer.getContext !== 'function') return; " +
    "  const ctx = peer.getContext('2d'); if (!ctx) return; " +
    "  ctx.save(); " +
    "  ctx.setTransform(a, b, c, d, e, f); " +
    "  ctx.globalAlpha = alpha; " +
    "  ctx.globalCompositeOperation = compOp; " +
    "  ctx.font = fontCss; " +
    "  ctx.strokeStyle = strokeCss; " +
    "  ctx.lineWidth = lineWidth; " +
    "  ctx.lineCap = lineCap; " +
    "  ctx.lineJoin = lineJoin; " +
    "  ctx.miterLimit = miterLimit; " +
    "  ctx.textAlign = alignCss; " +
    "  ctx.textBaseline = baselineCss; " +
    "  if (hasMaxWidth) ctx.strokeText(text, x, y, maxWidth); else ctx.strokeText(text, x, y); " +
    "  ctx.restore(); " +
    "} catch (_e) {} }")
private external fun wasmStrokeText(
    peer: JsAny,
    text: String,
    x: Float,
    y: Float,
    hasMaxWidth: Boolean,
    maxWidth: Float,
    fontCss: String,
    strokeCss: String,
    lineWidth: Float,
    lineCap: String,
    lineJoin: String,
    miterLimit: Float,
    alignCss: String,
    baselineCss: String,
    alpha: Float,
    compOp: String,
    a: Float,
    b: Float,
    c: Float,
    d: Float,
    e: Float,
    f: Float
)

@JsFun("(peer, text, fontCss, fallback) => { " +
    "try { " +
    "  if (typeof peer.getContext !== 'function') return fallback; " +
    "  const ctx = peer.getContext('2d'); if (!ctx) return fallback; " +
    "  ctx.font = fontCss; " +
    "  const m = ctx.measureText(text); " +
    "  const w = (m && typeof m.width === 'number') ? m.width : -1; " +
    "  return w > 0 ? w : fallback; " +
    "} catch (_e) { return fallback; } }")
private external fun wasmMeasureTextWidth(peer: JsAny, text: String, fontCss: String, fallback: Float): Float

@JsFun("(peer, sourcePeer, sx, sy, sw, sh, dx, dy, dw, dh, alpha, compOp, smoothing, a, b, c, d, e, f) => { " +
    "try { " +
    "  if (typeof peer.getContext !== 'function') return false; " +
    "  const ctx = peer.getContext('2d'); if (!ctx) return false; " +
    "  ctx.save(); " +
    "  ctx.setTransform(a, b, c, d, e, f); " +
    "  ctx.globalAlpha = alpha; " +
    "  ctx.globalCompositeOperation = compOp; " +
    "  ctx.imageSmoothingEnabled = !!smoothing; " +
    "  ctx.drawImage(sourcePeer, sx, sy, sw, sh, dx, dy, dw, dh); " +
    "  ctx.restore(); " +
    "  return true; " +
    "} catch (_e) { return false; } }")
private external fun wasmDrawSurfacePeer(
    peer: JsAny,
    sourcePeer: JsAny,
    sx: Float,
    sy: Float,
    sw: Float,
    sh: Float,
    dx: Float,
    dy: Float,
    dw: Float,
    dh: Float,
    alpha: Float,
    compOp: String,
    smoothing: Boolean,
    a: Float,
    b: Float,
    c: Float,
    d: Float,
    e: Float,
    f: Float
): Boolean

@JsFun("(peer, w, h) => { " +
    "try { " +
    "  if (typeof peer.getContext !== 'function') return null; " +
    "  const ctx = peer.getContext('2d'); if (!ctx) return null; " +
    "  return ctx.createImageData(w, h); " +
    "} catch (_e) { return null; } }")
private external fun wasmCreateImageData(peer: JsAny, w: Int, h: Int): JsAny?

@JsFun("(imgData) => imgData.data")
private external fun wasmGetImageDataArray(imgData: JsAny): JsAny

@JsFun("(u8, byteOffset, packedRgba) => { " +
    "u8[byteOffset] = packedRgba & 0xFF; " +
    "u8[byteOffset + 1] = (packedRgba >>> 8) & 0xFF; " +
    "u8[byteOffset + 2] = (packedRgba >>> 16) & 0xFF; " +
    "u8[byteOffset + 3] = (packedRgba >>> 24) & 0xFF; " +
    "}")
private external fun wasmWriteRgbaPixel(u8: JsAny, byteOffset: Int, packedRgba: Int)

@JsFun("(u8, byteOffset) => { " +
    "return ((u8[byteOffset] & 0xFF) | ((u8[byteOffset + 1] & 0xFF) << 8) | ((u8[byteOffset + 2] & 0xFF) << 16) | ((u8[byteOffset + 3] & 0xFF) << 24)) | 0; " +
    "}")
private external fun wasmReadRgbaPixel(u8: JsAny, byteOffset: Int): Int

@JsFun("(peer, imgData, dirtyX, dirtyY, dirtyW, dirtyH) => { " +
    "try { " +
    "  const ctx = peer.getContext('2d'); if (!ctx) return; " +
    "  ctx.putImageData(imgData, 0, 0, dirtyX, dirtyY, dirtyW, dirtyH); " +
    "} catch (_e) {} }")
private external fun wasmPutImageData(
    peer: JsAny,
    imgData: JsAny,
    dirtyX: Int,
    dirtyY: Int,
    dirtyW: Int,
    dirtyH: Int
)

@JsFun("(peer, x, y, w, h) => { " +
    "try { " +
    "  if (typeof peer.getContext !== 'function') return null; " +
    "  const ctx = peer.getContext('2d'); if (!ctx) return null; " +
    "  return ctx.getImageData(x, y, w, h); " +
    "} catch (_e) { return null; } }")
private external fun wasmGetImageData(peer: JsAny, x: Int, y: Int, w: Int, h: Int): JsAny?

@JsFun("(w, h) => { " +
    "try { " +
    "  const c = document.createElement('canvas'); c.width = w; c.height = h; return c; " +
    "} catch (_e) { return null; } }")
private external fun wasmCreateOffscreenCanvas(w: Int, h: Int): JsAny?

@JsFun("(peer, mimeType) => { " +
    "try { " +
    "  if (typeof peer.toDataURL === 'function') { " +
    "    const s = peer.toDataURL(mimeType); " +
    "    return typeof s === 'string' ? s : ''; " +
    "  } " +
    "} catch (_e) {} " +
    "return ''; }")
private external fun wasmToDataUrl(peer: JsAny, mimeType: String): String

/**
 * Kotlin/WasmJS implementation of [WebImageBuffer2DBridge] operating on the browser's HTML5 `<canvas>`
 * 2D context (`CanvasRenderingContext2D`) when running in a browser DOM environment, with headless
 * attribute synchronization when `document` is absent.
 */
actual object WebImageBuffer2DBridge {

    actual val isBrowserCanvasSupported: Boolean
        get() = WebBrowserDomBridge.isBrowserEnvironment

    private fun resolveCanvasPeer(surfaceElement: WebDomElement): JsAny? {
        if (!isBrowserCanvasSupported) return null
        var peer = surfaceElement.nativeBrowserPeer as? JsAny
        if (peer == null) {
            WebBrowserDomBridge.createPeer(surfaceElement)
            peer = surfaceElement.nativeBrowserPeer as? JsAny
        }
        return peer
    }

    private fun copyBytesToImageData(rgbaBytes: ByteArray, imgData: JsAny, totalBytes: Int) {
        val u8 = wasmGetImageDataArray(imgData)
        val limit = minOf(rgbaBytes.size, totalBytes)
        var i = 0
        while (i + 3 < limit) {
            val packed = (rgbaBytes[i].toInt() and 0xFF) or
                ((rgbaBytes[i + 1].toInt() and 0xFF) shl 8) or
                ((rgbaBytes[i + 2].toInt() and 0xFF) shl 16) or
                ((rgbaBytes[i + 3].toInt() and 0xFF) shl 24)
            wasmWriteRgbaPixel(u8, i, packed)
            i += 4
        }
    }

    actual fun ensureSurface2D(
        surfaceElement: WebDomElement,
        width: Int,
        height: Int,
        antialias: Boolean
    ) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        surfaceElement.clientWidth = safeW
        surfaceElement.clientHeight = safeH
        surfaceElement.setAttribute("width", safeW.toString())
        surfaceElement.setAttribute("height", safeH.toString())
        surfaceElement.setAttribute("data-context-2d", "true")
        surfaceElement.setAttribute("data-antialias", antialias.toString())

        val peer = resolveCanvasPeer(surfaceElement) ?: return
        wasmEnsureCanvasSize(peer, safeW, safeH)
    }

    actual fun resizeSurface2D(surfaceElement: WebDomElement, width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val peer = resolveCanvasPeer(surfaceElement)
        val snapshot = if (peer != null) wasmSnapshotCanvasBeforeResize(peer, safeW, safeH) else null

        surfaceElement.clientWidth = safeW
        surfaceElement.clientHeight = safeH
        surfaceElement.setAttribute("width", safeW.toString())
        surfaceElement.setAttribute("height", safeH.toString())

        if (peer != null) {
            wasmFinishResizeSurface2D(peer, safeW, safeH, snapshot)
        }
    }

    actual fun clearRect(
        surfaceElement: WebDomElement,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        transform: FloatArray
    ) {
        surfaceElement.setAttribute("data-last-op", "clearRect")
        val peer = resolveCanvasPeer(surfaceElement) ?: return
        wasmClearRect(
            peer = peer,
            x = x,
            y = y,
            w = width,
            h = height,
            a = if (transform.size > 0) transform[0] else 1f,
            b = if (transform.size > 1) transform[1] else 0f,
            c = if (transform.size > 2) transform[2] else 0f,
            d = if (transform.size > 3) transform[3] else 1f,
            e = if (transform.size > 4) transform[4] else 0f,
            f = if (transform.size > 5) transform[5] else 0f
        )
    }

    actual fun fillRect(
        surfaceElement: WebDomElement,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        fillCss: String,
        globalAlpha: Float,
        compositeOp: String,
        transform: FloatArray
    ) {
        surfaceElement.setAttribute("data-last-op", "fillRect")
        surfaceElement.setAttribute("data-fill-style", fillCss)
        val peer = resolveCanvasPeer(surfaceElement) ?: return
        wasmFillRect(
            peer = peer,
            x = x,
            y = y,
            w = width,
            h = height,
            fillCss = fillCss,
            alpha = globalAlpha,
            compOp = compositeOp,
            a = if (transform.size > 0) transform[0] else 1f,
            b = if (transform.size > 1) transform[1] else 0f,
            c = if (transform.size > 2) transform[2] else 0f,
            d = if (transform.size > 3) transform[3] else 1f,
            e = if (transform.size > 4) transform[4] else 0f,
            f = if (transform.size > 5) transform[5] else 0f
        )
    }

    actual fun strokeRect(
        surfaceElement: WebDomElement,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        strokeCss: String,
        lineWidth: Float,
        lineCap: String,
        lineJoin: String,
        miterLimit: Float,
        globalAlpha: Float,
        compositeOp: String,
        transform: FloatArray
    ) {
        surfaceElement.setAttribute("data-last-op", "strokeRect")
        surfaceElement.setAttribute("data-stroke-style", strokeCss)
        surfaceElement.setAttribute("data-line-width", lineWidth.toString())
        val peer = resolveCanvasPeer(surfaceElement) ?: return
        wasmStrokeRect(
            peer = peer,
            x = x,
            y = y,
            w = width,
            h = height,
            strokeCss = strokeCss,
            lineWidth = lineWidth,
            lineCap = lineCap,
            lineJoin = lineJoin,
            miterLimit = miterLimit,
            alpha = globalAlpha,
            compOp = compositeOp,
            a = if (transform.size > 0) transform[0] else 1f,
            b = if (transform.size > 1) transform[1] else 0f,
            c = if (transform.size > 2) transform[2] else 0f,
            d = if (transform.size > 3) transform[3] else 1f,
            e = if (transform.size > 4) transform[4] else 0f,
            f = if (transform.size > 5) transform[5] else 0f
        )
    }

    actual fun fillText(
        surfaceElement: WebDomElement,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float?,
        fontCss: String,
        fillCss: String,
        textAlignCss: String,
        textBaselineCss: String,
        globalAlpha: Float,
        compositeOp: String,
        transform: FloatArray
    ) {
        surfaceElement.setAttribute("data-last-op", "fillText")
        surfaceElement.setAttribute("data-last-text", text)
        surfaceElement.setAttribute("data-font", fontCss)
        surfaceElement.setAttribute("data-fill-style", fillCss)
        val peer = resolveCanvasPeer(surfaceElement) ?: return
        wasmFillText(
            peer = peer,
            text = text,
            x = x,
            y = y,
            hasMaxWidth = maxWidth != null,
            maxWidth = maxWidth ?: 0f,
            fontCss = fontCss,
            fillCss = fillCss,
            alignCss = textAlignCss,
            baselineCss = textBaselineCss,
            alpha = globalAlpha,
            compOp = compositeOp,
            a = if (transform.size > 0) transform[0] else 1f,
            b = if (transform.size > 1) transform[1] else 0f,
            c = if (transform.size > 2) transform[2] else 0f,
            d = if (transform.size > 3) transform[3] else 1f,
            e = if (transform.size > 4) transform[4] else 0f,
            f = if (transform.size > 5) transform[5] else 0f
        )
    }

    actual fun strokeText(
        surfaceElement: WebDomElement,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float?,
        fontCss: String,
        strokeCss: String,
        lineWidth: Float,
        lineCap: String,
        lineJoin: String,
        miterLimit: Float,
        textAlignCss: String,
        textBaselineCss: String,
        globalAlpha: Float,
        compositeOp: String,
        transform: FloatArray
    ) {
        surfaceElement.setAttribute("data-last-op", "strokeText")
        surfaceElement.setAttribute("data-last-text", text)
        surfaceElement.setAttribute("data-font", fontCss)
        surfaceElement.setAttribute("data-stroke-style", strokeCss)
        val peer = resolveCanvasPeer(surfaceElement) ?: return
        wasmStrokeText(
            peer = peer,
            text = text,
            x = x,
            y = y,
            hasMaxWidth = maxWidth != null,
            maxWidth = maxWidth ?: 0f,
            fontCss = fontCss,
            strokeCss = strokeCss,
            lineWidth = lineWidth,
            lineCap = lineCap,
            lineJoin = lineJoin,
            miterLimit = miterLimit,
            alignCss = textAlignCss,
            baselineCss = textBaselineCss,
            alpha = globalAlpha,
            compOp = compositeOp,
            a = if (transform.size > 0) transform[0] else 1f,
            b = if (transform.size > 1) transform[1] else 0f,
            c = if (transform.size > 2) transform[2] else 0f,
            d = if (transform.size > 3) transform[3] else 1f,
            e = if (transform.size > 4) transform[4] else 0f,
            f = if (transform.size > 5) transform[5] else 0f
        )
    }

    actual fun measureTextWidth(
        surfaceElement: WebDomElement,
        text: String,
        fontCss: String,
        fallbackEstimate: Float
    ): Float {
        surfaceElement.setAttribute("data-last-measure-text", text)
        surfaceElement.setAttribute("data-font", fontCss)
        val peer = resolveCanvasPeer(surfaceElement) ?: return fallbackEstimate
        return wasmMeasureTextWidth(peer, text, fontCss, fallbackEstimate)
    }

    actual fun drawSurfacePeer(
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
        globalAlpha: Float,
        compositeOp: String,
        imageSmoothingEnabled: Boolean,
        transform: FloatArray
    ): Boolean {
        surfaceElement.setAttribute("data-last-op", "drawImage")
        val peer = resolveCanvasPeer(surfaceElement) ?: return false
        val rawPeer = (if (sourcePeer is WebDomElement) sourcePeer.nativeBrowserPeer else sourcePeer) as? JsAny
            ?: return false
        return wasmDrawSurfacePeer(
            peer = peer,
            sourcePeer = rawPeer,
            sx = sx,
            sy = sy,
            sw = sw,
            sh = sh,
            dx = dx,
            dy = dy,
            dw = dw,
            dh = dh,
            alpha = globalAlpha,
            compOp = compositeOp,
            smoothing = imageSmoothingEnabled,
            a = if (transform.size > 0) transform[0] else 1f,
            b = if (transform.size > 1) transform[1] else 0f,
            c = if (transform.size > 2) transform[2] else 0f,
            d = if (transform.size > 3) transform[3] else 1f,
            e = if (transform.size > 4) transform[4] else 0f,
            f = if (transform.size > 5) transform[5] else 0f
        )
    }

    actual fun drawRgbaBytes(
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
        globalAlpha: Float,
        compositeOp: String,
        imageSmoothingEnabled: Boolean,
        transform: FloatArray
    ): Boolean {
        surfaceElement.setAttribute("data-last-op", "drawImage")
        val peer = resolveCanvasPeer(surfaceElement) ?: return false
        val safeW = srcWidth.coerceAtLeast(1)
        val safeH = srcHeight.coerceAtLeast(1)
        val tmpCanvas = wasmCreateOffscreenCanvas(safeW, safeH) ?: return false
        val imgData = wasmCreateImageData(tmpCanvas, safeW, safeH) ?: return false
        copyBytesToImageData(rgbaBytes, imgData, safeW * safeH * 4)
        wasmPutImageData(tmpCanvas, imgData, 0, 0, safeW, safeH)
        return wasmDrawSurfacePeer(
            peer = peer,
            sourcePeer = tmpCanvas,
            sx = sx,
            sy = sy,
            sw = sw,
            sh = sh,
            dx = dx,
            dy = dy,
            dw = dw,
            dh = dh,
            alpha = globalAlpha,
            compOp = compositeOp,
            smoothing = imageSmoothingEnabled,
            a = if (transform.size > 0) transform[0] else 1f,
            b = if (transform.size > 1) transform[1] else 0f,
            c = if (transform.size > 2) transform[2] else 0f,
            d = if (transform.size > 3) transform[3] else 1f,
            e = if (transform.size > 4) transform[4] else 0f,
            f = if (transform.size > 5) transform[5] else 0f
        )
    }

    actual fun putImageDataRgba(
        surfaceElement: WebDomElement,
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        dirtyX: Int,
        dirtyY: Int,
        dirtyWidth: Int,
        dirtyHeight: Int
    ) {
        surfaceElement.setAttribute("data-pixel-sync", "putImageData")
        surfaceElement.setAttribute("data-byte-length", rgbaBytes.size.toString())
        surfaceElement.setAttribute("data-last-op", "putImageData")
        val peer = resolveCanvasPeer(surfaceElement) ?: return
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val imgData = wasmCreateImageData(peer, safeW, safeH) ?: return
        copyBytesToImageData(rgbaBytes, imgData, safeW * safeH * 4)
        wasmPutImageData(peer, imgData, dirtyX, dirtyY, dirtyWidth, dirtyHeight)
    }

    actual fun getImageDataRgba(
        surfaceElement: WebDomElement,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        fallbackBytes: ByteArray
    ): ByteArray {
        surfaceElement.setAttribute("data-pixel-sync", "getImageData")
        val peer = resolveCanvasPeer(surfaceElement) ?: return fallbackBytes
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val imgData = wasmGetImageData(peer, x, y, safeW, safeH) ?: return fallbackBytes
        val u8 = wasmGetImageDataArray(imgData)
        val totalBytes = safeW * safeH * 4
        val out = ByteArray(totalBytes)
        var i = 0
        while (i + 3 < totalBytes) {
            val packed = wasmReadRgbaPixel(u8, i)
            out[i] = (packed and 0xFF).toByte()
            out[i + 1] = ((packed ushr 8) and 0xFF).toByte()
            out[i + 2] = ((packed ushr 16) and 0xFF).toByte()
            out[i + 3] = ((packed ushr 24) and 0xFF).toByte()
            i += 4
        }
        return out
    }

    actual fun toDataUrl(surfaceElement: WebDomElement, mimeType: String): String? {
        surfaceElement.setAttribute("data-last-export-mime", mimeType)
        val peer = resolveCanvasPeer(surfaceElement) ?: return null
        val url = wasmToDataUrl(peer, mimeType)
        return if (url.startsWith("data:")) url else null
    }
}
