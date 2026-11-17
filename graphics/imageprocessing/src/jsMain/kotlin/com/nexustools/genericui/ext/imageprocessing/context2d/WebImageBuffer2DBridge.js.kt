package com.nexustools.genericui.ext.imageprocessing.context2d

import com.nexustools.genericui.web.WebBrowserDomBridge
import com.nexustools.genericui.web.WebDomElement

/**
 * Kotlin/JS implementation of [WebImageBuffer2DBridge] operating on the browser's HTML5 `<canvas>`
 * 2D context (`CanvasRenderingContext2D`) when running in a browser DOM environment, with headless
 * attribute synchronization when `document` is absent.
 */
actual object WebImageBuffer2DBridge {

    actual val isBrowserCanvasSupported: Boolean
        get() = WebBrowserDomBridge.isBrowserEnvironment

    private fun resolveCanvasPeer(surfaceElement: WebDomElement): dynamic {
        if (!isBrowserCanvasSupported) return null
        var peer: dynamic = surfaceElement.nativeBrowserPeer
        if (peer == null) {
            WebBrowserDomBridge.createPeer(surfaceElement)
            peer = surfaceElement.nativeBrowserPeer
        }
        return peer
    }

    private fun resolveContext2D(surfaceElement: WebDomElement): dynamic {
        val peer = resolveCanvasPeer(surfaceElement) ?: return null
        return try {
            if (jsTypeOf(peer.getContext) == "function") {
                peer.getContext("2d")
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun applyTransform(ctx: dynamic, transform: FloatArray) {
        if (transform.size >= 6) {
            ctx.setTransform(
                transform[0],
                transform[1],
                transform[2],
                transform[3],
                transform[4],
                transform[5]
            )
        } else {
            ctx.setTransform(1f, 0f, 0f, 1f, 0f, 0f)
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
        try {
            if (peer.width != safeW) peer.width = safeW
            if (peer.height != safeH) peer.height = safeH
        } catch (_: Throwable) {
        }
    }

    actual fun resizeSurface2D(surfaceElement: WebDomElement, width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val peer = resolveCanvasPeer(surfaceElement)
        var tmpCanvas: dynamic = null
        if (peer != null) {
            try {
                val oldW = (peer.width as? Number)?.toInt() ?: 0
                val oldH = (peer.height as? Number)?.toInt() ?: 0
                if ((oldW != safeW || oldH != safeH) &&
                    oldW > 0 &&
                    oldH > 0 &&
                    jsTypeOf(peer.getContext) == "function"
                ) {
                    val doc: dynamic = js("document")
                    tmpCanvas = doc.createElement("canvas")
                    tmpCanvas.width = oldW
                    tmpCanvas.height = oldH
                    val tmpCtx: dynamic = tmpCanvas.getContext("2d")
                    if (tmpCtx != null) {
                        tmpCtx.drawImage(peer, 0, 0)
                    }
                }
            } catch (_: Throwable) {
                tmpCanvas = null
            }
        }

        surfaceElement.clientWidth = safeW
        surfaceElement.clientHeight = safeH
        surfaceElement.setAttribute("width", safeW.toString())
        surfaceElement.setAttribute("height", safeH.toString())

        if (peer != null) {
            try {
                if (peer.width != safeW) peer.width = safeW
                if (peer.height != safeH) peer.height = safeH
                if (tmpCanvas != null && jsTypeOf(peer.getContext) == "function") {
                    val ctx: dynamic = peer.getContext("2d")
                    if (ctx != null) {
                        ctx.drawImage(tmpCanvas, 0, 0)
                    }
                }
            } catch (_: Throwable) {
            }
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
        val ctx = resolveContext2D(surfaceElement) ?: return
        try {
            ctx.save()
            applyTransform(ctx, transform)
            ctx.clearRect(x, y, width, height)
            ctx.restore()
        } catch (_: Throwable) {
        }
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
        val ctx = resolveContext2D(surfaceElement) ?: return
        try {
            ctx.save()
            applyTransform(ctx, transform)
            ctx.globalAlpha = globalAlpha
            ctx.globalCompositeOperation = compositeOp
            ctx.fillStyle = fillCss
            ctx.fillRect(x, y, width, height)
            ctx.restore()
        } catch (_: Throwable) {
        }
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
        val ctx = resolveContext2D(surfaceElement) ?: return
        try {
            ctx.save()
            applyTransform(ctx, transform)
            ctx.globalAlpha = globalAlpha
            ctx.globalCompositeOperation = compositeOp
            ctx.strokeStyle = strokeCss
            ctx.lineWidth = lineWidth
            ctx.lineCap = lineCap
            ctx.lineJoin = lineJoin
            ctx.miterLimit = miterLimit
            ctx.strokeRect(x, y, width, height)
            ctx.restore()
        } catch (_: Throwable) {
        }
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
        val ctx = resolveContext2D(surfaceElement) ?: return
        try {
            ctx.save()
            applyTransform(ctx, transform)
            ctx.globalAlpha = globalAlpha
            ctx.globalCompositeOperation = compositeOp
            ctx.font = fontCss
            ctx.fillStyle = fillCss
            ctx.textAlign = textAlignCss
            ctx.textBaseline = textBaselineCss
            if (maxWidth != null) {
                ctx.fillText(text, x, y, maxWidth)
            } else {
                ctx.fillText(text, x, y)
            }
            ctx.restore()
        } catch (_: Throwable) {
        }
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
        val ctx = resolveContext2D(surfaceElement) ?: return
        try {
            ctx.save()
            applyTransform(ctx, transform)
            ctx.globalAlpha = globalAlpha
            ctx.globalCompositeOperation = compositeOp
            ctx.font = fontCss
            ctx.strokeStyle = strokeCss
            ctx.lineWidth = lineWidth
            ctx.lineCap = lineCap
            ctx.lineJoin = lineJoin
            ctx.miterLimit = miterLimit
            ctx.textAlign = textAlignCss
            ctx.textBaseline = textBaselineCss
            if (maxWidth != null) {
                ctx.strokeText(text, x, y, maxWidth)
            } else {
                ctx.strokeText(text, x, y)
            }
            ctx.restore()
        } catch (_: Throwable) {
        }
    }

    actual fun measureTextWidth(
        surfaceElement: WebDomElement,
        text: String,
        fontCss: String,
        fallbackEstimate: Float
    ): Float {
        surfaceElement.setAttribute("data-last-measure-text", text)
        surfaceElement.setAttribute("data-font", fontCss)
        val ctx = resolveContext2D(surfaceElement) ?: return fallbackEstimate
        return try {
            ctx.font = fontCss
            val metrics: dynamic = ctx.measureText(text)
            val width = (metrics?.width as? Number)?.toFloat() ?: -1f
            if (width > 0f) width else fallbackEstimate
        } catch (_: Throwable) {
            fallbackEstimate
        }
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
        val ctx = resolveContext2D(surfaceElement) ?: return false
        val rawPeer: dynamic = if (sourcePeer is WebDomElement) sourcePeer.nativeBrowserPeer else sourcePeer
        if (rawPeer == null) return false
        return try {
            ctx.save()
            applyTransform(ctx, transform)
            ctx.globalAlpha = globalAlpha
            ctx.globalCompositeOperation = compositeOp
            ctx.imageSmoothingEnabled = imageSmoothingEnabled
            ctx.drawImage(rawPeer, sx, sy, sw, sh, dx, dy, dw, dh)
            ctx.restore()
            true
        } catch (_: Throwable) {
            false
        }
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
        val ctx = resolveContext2D(surfaceElement) ?: return false
        val safeW = srcWidth.coerceAtLeast(1)
        val safeH = srcHeight.coerceAtLeast(1)
        return try {
            val doc: dynamic = js("document")
            val tmpCanvas: dynamic = doc.createElement("canvas")
            tmpCanvas.width = safeW
            tmpCanvas.height = safeH
            val tmpCtx: dynamic = tmpCanvas.getContext("2d") ?: return false
            val imgData: dynamic = tmpCtx.createImageData(safeW, safeH)
            val data: dynamic = imgData.data
            val copyLen = minOf(rgbaBytes.size, safeW * safeH * 4)
            for (i in 0 until copyLen) {
                data[i] = rgbaBytes[i].toInt() and 0xFF
            }
            tmpCtx.putImageData(imgData, 0, 0)

            ctx.save()
            applyTransform(ctx, transform)
            ctx.globalAlpha = globalAlpha
            ctx.globalCompositeOperation = compositeOp
            ctx.imageSmoothingEnabled = imageSmoothingEnabled
            ctx.drawImage(tmpCanvas, sx, sy, sw, sh, dx, dy, dw, dh)
            ctx.restore()
            true
        } catch (_: Throwable) {
            false
        }
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
        val ctx = resolveContext2D(surfaceElement) ?: return
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        try {
            val imgData: dynamic = ctx.createImageData(safeW, safeH)
            val data: dynamic = imgData.data
            val copyLen = minOf(rgbaBytes.size, safeW * safeH * 4)
            for (i in 0 until copyLen) {
                data[i] = rgbaBytes[i].toInt() and 0xFF
            }
            ctx.putImageData(imgData, 0, 0, dirtyX, dirtyY, dirtyWidth, dirtyHeight)
        } catch (_: Throwable) {
        }
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
        val ctx = resolveContext2D(surfaceElement) ?: return fallbackBytes
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        return try {
            val imgData: dynamic = ctx.getImageData(x, y, safeW, safeH)
            val data: dynamic = imgData.data ?: return fallbackBytes
            val expectedLen = safeW * safeH * 4
            val out = ByteArray(expectedLen)
            for (i in 0 until expectedLen) {
                out[i] = ((data[i] as Number).toInt() and 0xFF).toByte()
            }
            out
        } catch (_: Throwable) {
            fallbackBytes
        }
    }

    actual fun toDataUrl(surfaceElement: WebDomElement, mimeType: String): String? {
        surfaceElement.setAttribute("data-last-export-mime", mimeType)
        val peer = resolveCanvasPeer(surfaceElement) ?: return null
        return try {
            if (jsTypeOf(peer.toDataURL) == "function") {
                val url = peer.toDataURL(mimeType) as? String
                if (!url.isNullOrEmpty() && url.startsWith("data:")) url else null
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
