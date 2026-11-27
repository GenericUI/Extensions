package com.nexustools.genericui.ext.webview.desktop.njs

import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferCompositeOperation
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferFillRule
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferFontStyle
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferGradient2D
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferLineCap
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferLineJoin
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferPaintStyle
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferPath2D
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferPattern2D
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferPatternRepetition
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferTextAlign
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferTextBaseline
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomElement
import com.nexustools.genericui.ext.webview.desktop.engine.WebAlienDocumentEngine
import com.nexustools.genericui.ext.webview.desktop.engine.WebCssCascadeResolver
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.njs.runtime.AbstractFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.GenericArray
import com.nexustools.njs.runtime.GenericObject
import com.nexustools.njs.runtime.Global
import com.nexustools.njs.runtime.Null
import com.nexustools.njs.runtime.Scopable
import com.nexustools.njs.runtime.Uint8ClampedArray
import com.nexustools.njs.runtime.Undefined
import kotlin.math.roundToInt

/**
 * Full HTML5 `CanvasRenderingContext2D` + `Path2D` + `CanvasGradient` + `CanvasPattern` +
 * `ImageData` bridge backed by GenericUI's hardware-accelerated/Alien [ImageBuffer2DContext].
 */
class NjsCanvas2DBridge(
    private val jsGlobal: Global,
    private val engine: WebAlienDocumentEngine,
    private val registry: NjsDomNodeRegistry
) {
    private val gradientMap = LinkedHashMap<BaseObject, ImageBufferGradient2D>()
    private val patternMap = LinkedHashMap<BaseObject, ImageBufferPattern2D>()
    private val path2DMap = LinkedHashMap<BaseObject, ImageBufferPath2D>()
    private val contextCache = LinkedHashMap<WebDomElement, GenericObject>()

    fun installConstructors() {
        val path2DCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val p = ImageBufferPath2D()
                val arg0 = params.getOrNull(0)
                if (arg0 != null && arg0 !== Undefined && arg0 !== Null) {
                    val existing = path2DMap[arg0]
                    if (existing != null) {
                        // Copy commands via addPath if available or reconstruct from SVG string if stored
                        arg0.get("__svgPath", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toString()?.let {
                            applySvgPathCommands(p, it)
                        }
                    } else if (arg0.typeOf() == "string") {
                        applySvgPathCommands(p, arg0.toString())
                    }
                }
                return wrapPath2D(p, arg0?.takeIf { it.typeOf() == "string" }?.toString())
            }
            override fun name(): String = "Path2D"
        }
        val imageDataCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val arg0 = params.getOrNull(0)
                return if (arg0 is Uint8ClampedArray.Instance || arg0?.get("BYTES_PER_ELEMENT", Scopable.OR_NULL) != null) {
                    val len = arg0!!.get("length").toInt()
                    val w = (params.getOrNull(1)?.toInt() ?: 1).coerceAtLeast(1)
                    val h = (params.getOrNull(2)?.takeIf { it !== Undefined }?.toInt() ?: (len / (4 * w))).coerceAtLeast(1)
                    val bytes = ByteArray(w * h * 4)
                    for (i in 0 until minOf(len, bytes.size)) {
                        bytes[i] = arg0.get(i).toInt().coerceIn(0, 255).toByte()
                    }
                    wrapImageData(w, h, bytes)
                } else {
                    val w = (arg0?.toInt() ?: 1).coerceAtLeast(1)
                    val h = (params.getOrNull(1)?.toInt() ?: 1).coerceAtLeast(1)
                    wrapImageData(w, h, ByteArray(w * h * 4))
                }
            }
            override fun name(): String = "ImageData"
        }
        jsGlobal.setHidden("Path2D", path2DCtor)
        jsGlobal.setHidden("ImageData", imageDataCtor)
    }

    fun getOrCreateContext2D(canvasEl: WebDomElement): BaseObject {
        contextCache[canvasEl]?.let { return it }
        val ctx = engine.ensureCanvas2DContext(canvasEl) ?: return Null
        val js = GenericObject(jsGlobal)
        contextCache[canvasEl] = js

        var fillStyleRaw: BaseObject = jsGlobal.wrap("#000000")
        var strokeStyleRaw: BaseObject = jsGlobal.wrap("#000000")
        var fontRaw: String = "10px sans-serif"

        fun markCanvasDirty() {
            canvasEl.alienNode?.invalidateBounds()
            engine.session.requestLayoutAndRepaint(canvasEl.alienNode?.bounds)
        }

        js.defineProperty("canvas", fn("get_canvas") { _, _ -> registry.wrapNode(canvasEl) }, jsGlobal.NOOP)
        js.setHidden("save", fn("save") { _, _ -> ctx.save(); Undefined })
        js.setHidden("restore", fn("restore") { _, _ -> ctx.restore(); Undefined })
        js.setHidden("reset", fn("reset") { _, _ -> ctx.reset(); markCanvasDirty(); Undefined })
        js.setHidden("isContextLost", fn("isContextLost") { _, _ -> jsGlobal.wrap(ctx.isContextLost()) })

        // Transforms
        js.setHidden("scale", fn("scale") { _, p ->
            ctx.scale(p.getOrNull(0)?.toDouble()?.toFloat() ?: 1f, p.getOrNull(1)?.toDouble()?.toFloat() ?: 1f)
            Undefined
        })
        js.setHidden("rotate", fn("rotate") { _, p ->
            ctx.rotate(p.getOrNull(0)?.toDouble()?.toFloat() ?: 0f)
            Undefined
        })
        js.setHidden("translate", fn("translate") { _, p ->
            ctx.translate(p.getOrNull(0)?.toDouble()?.toFloat() ?: 0f, p.getOrNull(1)?.toDouble()?.toFloat() ?: 0f)
            Undefined
        })
        js.setHidden("transform", fn("transform") { _, p ->
            ctx.transform(
                f(p, 0, 1f), f(p, 1, 0f), f(p, 2, 0f), f(p, 3, 1f), f(p, 4, 0f), f(p, 5, 0f)
            )
            Undefined
        })
        js.setHidden("setTransform", fn("setTransform") { _, p ->
            val arg0 = p.getOrNull(0)
            if (arg0 is GenericObject && arg0.get("a", Scopable.OR_NULL) != null) {
                ctx.setTransform(
                    arg0.get("a").toDouble().toFloat(),
                    arg0.get("b").toDouble().toFloat(),
                    arg0.get("c").toDouble().toFloat(),
                    arg0.get("d").toDouble().toFloat(),
                    arg0.get("e").toDouble().toFloat(),
                    arg0.get("f").toDouble().toFloat()
                )
            } else {
                ctx.setTransform(
                    f(p, 0, 1f), f(p, 1, 0f), f(p, 2, 0f), f(p, 3, 1f), f(p, 4, 0f), f(p, 5, 0f)
                )
            }
            Undefined
        })
        js.setHidden("getTransform", fn("getTransform") { _, _ ->
            val m = ctx.getTransform()
            GenericObject(jsGlobal).apply {
                set("a", jsGlobal.wrap(m.a.toDouble()))
                set("b", jsGlobal.wrap(m.b.toDouble()))
                set("c", jsGlobal.wrap(m.c.toDouble()))
                set("d", jsGlobal.wrap(m.d.toDouble()))
                set("e", jsGlobal.wrap(m.e.toDouble()))
                set("f", jsGlobal.wrap(m.f.toDouble()))
            }
        })
        js.setHidden("resetTransform", fn("resetTransform") { _, _ -> ctx.resetTransform(); Undefined })

        // Paint & Compositing state
        js.defineProperty("globalAlpha", fn("get_alpha") { _, _ -> jsGlobal.wrap(ctx.globalAlpha.toDouble()) },
            fn("set_alpha") { _, p ->
                val v = p.getOrNull(0)?.toDouble()?.toFloat()
                if (v != null && v.isFinite() && v in 0f..1f) ctx.globalAlpha = v
                Undefined
            })
        js.defineProperty("globalCompositeOperation", fn("get_gco") { _, _ -> jsGlobal.wrap(ctx.globalCompositeOperation.webValue) },
            fn("set_gco") { _, p -> ctx.globalCompositeOperation = ImageBufferCompositeOperation.fromWebValue(p.getOrNull(0)?.toString().orEmpty()); Undefined })
        js.defineProperty("imageSmoothingEnabled", fn("get_ise") { _, _ -> jsGlobal.wrap(ctx.imageSmoothingEnabled) },
            fn("set_ise") { _, p -> ctx.imageSmoothingEnabled = p.getOrNull(0)?.toBool() ?: true; Undefined })
        js.defineProperty("filter", fn("get_filter") { _, _ -> jsGlobal.wrap(ctx.filter) },
            fn("set_filter") { _, p -> ctx.filter = p.getOrNull(0)?.toString().orEmpty(); Undefined })

        js.defineProperty("fillStyle", fn("get_fillStyle") { _, _ -> fillStyleRaw },
            fn("set_fillStyle") { _, p ->
                val v = p.getOrNull(0) ?: return@fn Undefined
                val resolved = resolvePaintStyle(v)
                if (resolved != null) {
                    fillStyleRaw = v
                    ctx.fillStyle = resolved
                }
                Undefined
            })
        js.defineProperty("strokeStyle", fn("get_strokeStyle") { _, _ -> strokeStyleRaw },
            fn("set_strokeStyle") { _, p ->
                val v = p.getOrNull(0) ?: return@fn Undefined
                val resolved = resolvePaintStyle(v)
                if (resolved != null) {
                    strokeStyleRaw = v
                    ctx.strokeStyle = resolved
                }
                Undefined
            })

        // Gradients & Patterns
        js.setHidden("createLinearGradient", fn("createLinearGradient") { _, p ->
            wrapGradient(ctx.createLinearGradient(f(p, 0), f(p, 1), f(p, 2), f(p, 3)))
        })
        js.setHidden("createRadialGradient", fn("createRadialGradient") { _, p ->
            wrapGradient(ctx.createRadialGradient(f(p, 0), f(p, 1), f(p, 2), f(p, 3), f(p, 4), f(p, 5)))
        })
        js.setHidden("createConicGradient", fn("createConicGradient") { _, p ->
            wrapGradient(ctx.createConicGradient(f(p, 0), f(p, 1), f(p, 2)))
        })
        js.setHidden("createPattern", fn("createPattern") { _, p ->
            val srcEl = registry.unwrapElement(p.getOrNull(0)) ?: return@fn Null
            val buf = engine.resolveCanvasBackingBuffer(srcEl) ?: return@fn Null
            val rep = ImageBufferPatternRepetition.fromWebValue(p.getOrNull(1)?.takeIf { it !== Undefined && it !== Null }?.toString() ?: "repeat")
            val pattern = ctx.createPattern(buf, rep)
            GenericObject(jsGlobal).also { patObj ->
                patternMap[patObj] = pattern
            }
        })

        // Shadows
        js.defineProperty("shadowBlur", fn("get_sb") { _, _ -> jsGlobal.wrap(ctx.shadowBlur.toDouble()) },
            fn("set_sb") { _, p -> ctx.shadowBlur = f(p, 0).coerceAtLeast(0f); Undefined })
        js.defineProperty("shadowOffsetX", fn("get_sox") { _, _ -> jsGlobal.wrap(ctx.shadowOffsetX.toDouble()) },
            fn("set_sox") { _, p -> ctx.shadowOffsetX = f(p, 0); Undefined })
        js.defineProperty("shadowOffsetY", fn("get_soy") { _, _ -> jsGlobal.wrap(ctx.shadowOffsetY.toDouble()) },
            fn("set_soy") { _, p -> ctx.shadowOffsetY = f(p, 0); Undefined })
        js.defineProperty("shadowColor", fn("get_sc") { _, _ -> jsGlobal.wrap(formatRgbaCss(ctx.shadowColor)) },
            fn("set_sc") { _, p -> parseCssColor(p.getOrNull(0)?.toString().orEmpty())?.let { ctx.shadowColor = it }; Undefined })

        // Line styles & dashes
        js.defineProperty("lineWidth", fn("get_lw") { _, _ -> jsGlobal.wrap(ctx.lineWidth.toDouble()) },
            fn("set_lw") { _, p -> val v = f(p, 0); if (v > 0f) ctx.lineWidth = v; Undefined })
        js.defineProperty("lineCap", fn("get_lc") { _, _ -> jsGlobal.wrap(ctx.lineCap.webValue) },
            fn("set_lc") { _, p -> ctx.lineCap = ImageBufferLineCap.fromWebValue(p.getOrNull(0)?.toString().orEmpty()); Undefined })
        js.defineProperty("lineJoin", fn("get_lj") { _, _ -> jsGlobal.wrap(ctx.lineJoin.webValue) },
            fn("set_lj") { _, p -> ctx.lineJoin = ImageBufferLineJoin.fromWebValue(p.getOrNull(0)?.toString().orEmpty()); Undefined })
        js.defineProperty("miterLimit", fn("get_ml") { _, _ -> jsGlobal.wrap(ctx.miterLimit.toDouble()) },
            fn("set_ml") { _, p -> val v = f(p, 0); if (v > 0f) ctx.miterLimit = v; Undefined })
        js.defineProperty("lineDashOffset", fn("get_ldo") { _, _ -> jsGlobal.wrap(ctx.lineDashOffset.toDouble()) },
            fn("set_ldo") { _, p -> ctx.lineDashOffset = f(p, 0); Undefined })
        js.setHidden("setLineDash", fn("setLineDash") { _, p ->
            val arr = p.getOrNull(0) ?: return@fn Undefined
            val len = arr.get("length").toInt().coerceAtLeast(0)
            val floats = FloatArray(len) { idx -> arr.get(idx).toDouble().toFloat() }
            ctx.setLineDash(floats)
            Undefined
        })
        js.setHidden("getLineDash", fn("getLineDash") { _, _ ->
            val arr = ctx.getLineDash()
            GenericArray(jsGlobal, jsGlobal.Array, arr.map { jsGlobal.wrap(it.toDouble()) as BaseObject? }.toTypedArray())
        })

        // Rectangles
        js.setHidden("clearRect", fn("clearRect") { _, p ->
            ctx.clearRect(f(p, 0), f(p, 1), f(p, 2), f(p, 3)); markCanvasDirty(); Undefined
        })
        js.setHidden("fillRect", fn("fillRect") { _, p ->
            ctx.fillRect(f(p, 0), f(p, 1), f(p, 2), f(p, 3)); markCanvasDirty(); Undefined
        })
        js.setHidden("strokeRect", fn("strokeRect") { _, p ->
            ctx.strokeRect(f(p, 0), f(p, 1), f(p, 2), f(p, 3)); markCanvasDirty(); Undefined
        })

        // Paths
        js.setHidden("beginPath", fn("beginPath") { _, _ -> ctx.beginPath(); Undefined })
        js.setHidden("closePath", fn("closePath") { _, _ -> ctx.closePath(); Undefined })
        js.setHidden("moveTo", fn("moveTo") { _, p -> ctx.moveTo(f(p, 0), f(p, 1)); Undefined })
        js.setHidden("lineTo", fn("lineTo") { _, p -> ctx.lineTo(f(p, 0), f(p, 1)); Undefined })
        js.setHidden("quadraticCurveTo", fn("quadraticCurveTo") { _, p ->
            ctx.quadraticCurveTo(f(p, 0), f(p, 1), f(p, 2), f(p, 3)); Undefined
        })
        js.setHidden("bezierCurveTo", fn("bezierCurveTo") { _, p ->
            ctx.bezierCurveTo(f(p, 0), f(p, 1), f(p, 2), f(p, 3), f(p, 4), f(p, 5)); Undefined
        })
        js.setHidden("arcTo", fn("arcTo") { _, p ->
            ctx.arcTo(f(p, 0), f(p, 1), f(p, 2), f(p, 3), f(p, 4)); Undefined
        })
        js.setHidden("arc", fn("arc") { _, p ->
            val ccw = p.getOrNull(5)?.takeIf { it !== Undefined }?.toBool() ?: false
            ctx.arc(f(p, 0), f(p, 1), f(p, 2), f(p, 3), f(p, 4), ccw)
            Undefined
        })
        js.setHidden("ellipse", fn("ellipse") { _, p ->
            val ccw = p.getOrNull(7)?.takeIf { it !== Undefined }?.toBool() ?: false
            ctx.ellipse(f(p, 0), f(p, 1), f(p, 2), f(p, 3), f(p, 4), f(p, 5), f(p, 6, 6.2831855f), ccw)
            Undefined
        })
        js.setHidden("rect", fn("rect") { _, p -> ctx.rect(f(p, 0), f(p, 1), f(p, 2), f(p, 3)); Undefined })
        js.setHidden("roundRect", fn("roundRect") { _, p ->
            val rArg = p.getOrNull(4)
            if (rArg is GenericArray) {
                val r0 = rArg.get(0).toDouble().toFloat()
                ctx.roundRect(f(p, 0), f(p, 1), f(p, 2), f(p, 3), r0)
            } else {
                ctx.roundRect(f(p, 0), f(p, 1), f(p, 2), f(p, 3), f(p, 4))
            }
            Undefined
        })
        js.setHidden("fill", fn("fill") { _, p ->
            val arg0 = p.getOrNull(0)
            val path = arg0?.let { path2DMap[it] }
            if (path != null) {
                val rule = ImageBufferFillRule.fromWebValue(p.getOrNull(1)?.takeIf { it !== Undefined }?.toString() ?: "nonzero")
                ctx.fill(path, rule)
            } else {
                val rule = ImageBufferFillRule.fromWebValue(arg0?.takeIf { it !== Undefined }?.toString() ?: "nonzero")
                ctx.fill(rule)
            }
            markCanvasDirty()
            Undefined
        })
        js.setHidden("stroke", fn("stroke") { _, p ->
            val path = p.getOrNull(0)?.let { path2DMap[it] }
            if (path != null) ctx.stroke(path) else ctx.stroke()
            markCanvasDirty()
            Undefined
        })
        js.setHidden("clip", fn("clip") { _, p ->
            val arg0 = p.getOrNull(0)
            val path = arg0?.let { path2DMap[it] }
            if (path != null) {
                val rule = ImageBufferFillRule.fromWebValue(p.getOrNull(1)?.takeIf { it !== Undefined }?.toString() ?: "nonzero")
                ctx.clip(path, rule)
            } else {
                val rule = ImageBufferFillRule.fromWebValue(arg0?.takeIf { it !== Undefined }?.toString() ?: "nonzero")
                ctx.clip(rule)
            }
            Undefined
        })
        js.setHidden("isPointInPath", fn("isPointInPath") { _, p ->
            val path = p.getOrNull(0)?.let { path2DMap[it] }
            val hit = if (path != null) {
                val rule = ImageBufferFillRule.fromWebValue(p.getOrNull(3)?.takeIf { it !== Undefined }?.toString() ?: "nonzero")
                ctx.isPointInPath(path, f(p, 1), f(p, 2), rule)
            } else {
                val rule = ImageBufferFillRule.fromWebValue(p.getOrNull(2)?.takeIf { it !== Undefined }?.toString() ?: "nonzero")
                ctx.isPointInPath(f(p, 0), f(p, 1), rule)
            }
            jsGlobal.wrap(hit)
        })
        js.setHidden("isPointInStroke", fn("isPointInStroke") { _, p ->
            val path = p.getOrNull(0)?.let { path2DMap[it] }
            val hit = if (path != null) ctx.isPointInStroke(path, f(p, 1), f(p, 2)) else ctx.isPointInStroke(f(p, 0), f(p, 1))
            jsGlobal.wrap(hit)
        })

        // Typography & Text
        js.defineProperty("font", fn("get_font") { _, _ -> jsGlobal.wrap(fontRaw) },
            fn("set_font") { _, p ->
                val raw = p.getOrNull(0)?.toString().orEmpty()
                if (raw.isNotBlank()) {
                    fontRaw = raw
                    applyCssFontShorthand(ctx, raw)
                }
                Undefined
            })
        js.defineProperty("textAlign", fn("get_ta") { _, _ -> jsGlobal.wrap(ctx.textAlign.webValue) },
            fn("set_ta") { _, p -> ctx.textAlign = ImageBufferTextAlign.fromWebValue(p.getOrNull(0)?.toString().orEmpty()); Undefined })
        js.defineProperty("textBaseline", fn("get_tb") { _, _ -> jsGlobal.wrap(ctx.textBaseline.webValue) },
            fn("set_tb") { _, p -> ctx.textBaseline = ImageBufferTextBaseline.fromWebValue(p.getOrNull(0)?.toString().orEmpty()); Undefined })
        js.setHidden("fillText", fn("fillText") { _, p ->
            val maxW = p.getOrNull(3)?.takeIf { it !== Undefined }?.toDouble()?.toFloat()
            ctx.fillText(p.getOrNull(0)?.toString().orEmpty(), f(p, 1), f(p, 2), maxW)
            markCanvasDirty()
            Undefined
        })
        js.setHidden("strokeText", fn("strokeText") { _, p ->
            val maxW = p.getOrNull(3)?.takeIf { it !== Undefined }?.toDouble()?.toFloat()
            ctx.strokeText(p.getOrNull(0)?.toString().orEmpty(), f(p, 1), f(p, 2), maxW)
            markCanvasDirty()
            Undefined
        })
        js.setHidden("measureText", fn("measureText") { _, p ->
            val metrics = ctx.measureText(p.getOrNull(0)?.toString().orEmpty())
            GenericObject(jsGlobal).apply {
                set("width", jsGlobal.wrap(metrics.width.toDouble()))
                set("actualBoundingBoxLeft", jsGlobal.wrap(metrics.actualBoundingBoxLeft.toDouble()))
                set("actualBoundingBoxRight", jsGlobal.wrap(metrics.actualBoundingBoxRight.toDouble()))
                set("actualBoundingBoxAscent", jsGlobal.wrap(metrics.actualBoundingBoxAscent.toDouble()))
                set("actualBoundingBoxDescent", jsGlobal.wrap(metrics.actualBoundingBoxDescent.toDouble()))
                set("fontBoundingBoxAscent", jsGlobal.wrap(metrics.fontBoundingBoxAscent.toDouble()))
                set("fontBoundingBoxDescent", jsGlobal.wrap(metrics.fontBoundingBoxDescent.toDouble()))
            }
        })

        // Images & Pixel Data
        js.setHidden("drawImage", fn("drawImage") { _, p ->
            val srcEl = registry.unwrapElement(p.getOrNull(0)) ?: return@fn Undefined
            val buf = engine.resolveCanvasBackingBuffer(srcEl) ?: return@fn Undefined
            when (p.size) {
                3 -> ctx.drawImage(buf, f(p, 1), f(p, 2))
                5 -> ctx.drawImage(buf, f(p, 1), f(p, 2), f(p, 3), f(p, 4))
                in 9..Int.MAX_VALUE -> ctx.drawImage(buf, f(p, 1), f(p, 2), f(p, 3), f(p, 4), f(p, 5), f(p, 6), f(p, 7), f(p, 8))
            }
            markCanvasDirty()
            Undefined
        })
        js.setHidden("createImageData", fn("createImageData") { _, p ->
            val arg0 = p.getOrNull(0)
            val w: Int
            val h: Int
            if (arg0 is GenericObject && arg0.get("width", Scopable.OR_NULL) != null) {
                w = arg0.get("width").toInt().coerceAtLeast(1)
                h = arg0.get("height").toInt().coerceAtLeast(1)
            } else {
                w = (arg0?.toInt() ?: 1).coerceAtLeast(1)
                h = (p.getOrNull(1)?.toInt() ?: 1).coerceAtLeast(1)
            }
            wrapImageData(w, h, ByteArray(w * h * 4))
        })
        js.setHidden("getImageData", fn("getImageData") { _, p ->
            val x = p.getOrNull(0)?.toInt() ?: 0
            val y = p.getOrNull(1)?.toInt() ?: 0
            val w = (p.getOrNull(2)?.toInt() ?: 1).coerceAtLeast(1)
            val h = (p.getOrNull(3)?.toInt() ?: 1).coerceAtLeast(1)
            val rgba = ctx.getImageDataRgba(x, y, w, h)
            wrapImageData(w, h, rgba)
        })
        js.setHidden("putImageData", fn("putImageData") { _, p ->
            val imageData = p.getOrNull(0) ?: return@fn Undefined
            val w = imageData.get("width").toInt().coerceAtLeast(1)
            val h = imageData.get("height").toInt().coerceAtLeast(1)
            val dataObj = imageData.get("data")
            val len = w * h * 4
            val rgba = ByteArray(len)
            if (dataObj is Uint8ClampedArray.Instance && dataObj.arrayStorage != null) {
                val src = dataObj.arrayStorage!!
                src.copyInto(rgba, 0, 0, minOf(len, src.size))
            } else {
                for (i in 0 until minOf(len, dataObj.get("length").toInt())) {
                    rgba[i] = dataObj.get(i).toInt().coerceIn(0, 255).toByte()
                }
            }
            val dx = p.getOrNull(1)?.toInt() ?: 0
            val dy = p.getOrNull(2)?.toInt() ?: 0
            if (p.size >= 7) {
                val dirtyX = p.getOrNull(3)?.toInt() ?: 0
                val dirtyY = p.getOrNull(4)?.toInt() ?: 0
                val dirtyW = p.getOrNull(5)?.toInt() ?: w
                val dirtyH = p.getOrNull(6)?.toInt() ?: h
                val imgBuf = ImageBuffer(w, h, PixelFormat.RGBA_8888, ImageColorSpace.SRGB, rgba)
                ctx.putImageData(imgBuf, dx, dy, dirtyX, dirtyY, dirtyW, dirtyH)
            } else {
                ctx.putImageDataRgba(rgba, w, h, dx, dy)
            }
            markCanvasDirty()
            Undefined
        })

        return js
    }

    private fun wrapImageData(width: Int, height: Int, rgba: ByteArray): GenericObject {
        val ctor = jsGlobal.get("Uint8ClampedArray", Scopable.OR_NULL)
        val clampedInst = if (ctor is com.nexustools.njs.runtime.BaseFunction) {
            ctor.construct(jsGlobal.wrap(rgba.size.toDouble()))
        } else {
            Uint8ClampedArray.Instance(jsGlobal, null, rgba.copyOf())
        }
        val destBytes = (clampedInst as? Uint8ClampedArray.Instance)?.arrayStorage
        if (destBytes != null) {
            rgba.copyInto(destBytes, 0, 0, minOf(rgba.size, destBytes.size))
        } else {
            for (i in rgba.indices) clampedInst.set(i, jsGlobal.wrap((rgba[i].toInt() and 0xFF).toDouble()))
        }
        return GenericObject(jsGlobal).apply {
            set("width", jsGlobal.wrap(width.toDouble()))
            set("height", jsGlobal.wrap(height.toDouble()))
            set("data", clampedInst)
        }
    }

    private fun wrapGradient(gradient: ImageBufferGradient2D): GenericObject {
        val js = GenericObject(jsGlobal)
        gradientMap[js] = gradient
        js.setHidden("addColorStop", fn("addColorStop") { _, p ->
            val off = f(p, 0)
            val col = parseCssColor(p.getOrNull(1)?.toString().orEmpty()) ?: RgbaColor.BLACK
            gradient.addColorStop(off, col)
            Undefined
        })
        return js
    }

    private fun wrapPath2D(path: ImageBufferPath2D, svgPathData: String?): GenericObject {
        val js = GenericObject(jsGlobal)
        path2DMap[js] = path
        if (svgPathData != null) js.setHidden("__svgPath", jsGlobal.wrap(svgPathData))
        js.setHidden("moveTo", fn("moveTo") { _, p -> path.moveTo(f(p, 0), f(p, 1)); Undefined })
        js.setHidden("lineTo", fn("lineTo") { _, p -> path.lineTo(f(p, 0), f(p, 1)); Undefined })
        js.setHidden("quadraticCurveTo", fn("quadraticCurveTo") { _, p -> path.quadraticCurveTo(f(p, 0), f(p, 1), f(p, 2), f(p, 3)); Undefined })
        js.setHidden("bezierCurveTo", fn("bezierCurveTo") { _, p -> path.bezierCurveTo(f(p, 0), f(p, 1), f(p, 2), f(p, 3), f(p, 4), f(p, 5)); Undefined })
        js.setHidden("arc", fn("arc") { _, p -> path.arc(f(p, 0), f(p, 1), f(p, 2), f(p, 3), f(p, 4), p.getOrNull(5)?.toBool() ?: false); Undefined })
        js.setHidden("rect", fn("rect") { _, p -> path.rect(f(p, 0), f(p, 1), f(p, 2), f(p, 3)); Undefined })
        js.setHidden("closePath", fn("closePath") { _, _ -> path.closePath(); Undefined })
        return js
    }

    private fun applySvgPathCommands(path: ImageBufferPath2D, d: String) {
        val tokens = Regex("[a-zA-Z]|[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?")
            .findAll(d).map { it.value }.toList()
        var idx = 0
        var cmd = 'M'
        var curX = 0f
        var curY = 0f
        while (idx < tokens.size) {
            val tok = tokens[idx]
            if (tok.length == 1 && tok[0].isLetter()) {
                cmd = tok[0]
                idx++
                if (cmd == 'Z' || cmd == 'z') {
                    path.closePath()
                    continue
                }
            }
            fun nextNum(): Float = tokens.getOrNull(idx++)?.toFloatOrNull() ?: 0f
            when (cmd) {
                'M' -> { curX = nextNum(); curY = nextNum(); path.moveTo(curX, curY); cmd = 'L' }
                'm' -> { curX += nextNum(); curY += nextNum(); path.moveTo(curX, curY); cmd = 'l' }
                'L' -> { curX = nextNum(); curY = nextNum(); path.lineTo(curX, curY) }
                'l' -> { curX += nextNum(); curY += nextNum(); path.lineTo(curX, curY) }
                'H' -> { curX = nextNum(); path.lineTo(curX, curY) }
                'h' -> { curX += nextNum(); path.lineTo(curX, curY) }
                'V' -> { curY = nextNum(); path.lineTo(curX, curY) }
                'v' -> { curY += nextNum(); path.lineTo(curX, curY) }
                'C' -> {
                    val x1 = nextNum(); val y1 = nextNum(); val x2 = nextNum(); val y2 = nextNum()
                    curX = nextNum(); curY = nextNum()
                    path.bezierCurveTo(x1, y1, x2, y2, curX, curY)
                }
                'c' -> {
                    val x1 = curX + nextNum(); val y1 = curY + nextNum()
                    val x2 = curX + nextNum(); val y2 = curY + nextNum()
                    curX += nextNum(); curY += nextNum()
                    path.bezierCurveTo(x1, y1, x2, y2, curX, curY)
                }
                'Q' -> {
                    val cx = nextNum(); val cy = nextNum(); curX = nextNum(); curY = nextNum()
                    path.quadraticCurveTo(cx, cy, curX, curY)
                }
                'q' -> {
                    val cx = curX + nextNum(); val cy = curY + nextNum()
                    curX += nextNum(); curY += nextNum()
                    path.quadraticCurveTo(cx, cy, curX, curY)
                }
                else -> idx++
            }
        }
    }

    private fun resolvePaintStyle(v: BaseObject): ImageBufferPaintStyle? {
        gradientMap[v]?.let { return ImageBufferPaintStyle.gradient(it) }
        patternMap[v]?.let { return ImageBufferPaintStyle.pattern(it) }
        val color = parseCssColor(v.toString()) ?: return null
        return ImageBufferPaintStyle.solid(color)
    }

    private fun parseCssColor(raw: String): RgbaColor? {
        val norm = raw.trim()
        if (norm.isEmpty()) return null
        return RgbaColor.fromCss(norm)
    }

    private fun formatRgbaCss(c: RgbaColor): String =
        if (c.a >= 255) "rgb(${c.r}, ${c.g}, ${c.b})"
        else "rgba(${c.r}, ${c.g}, ${c.b}, ${((c.a / 255.0) * 100.0).roundToInt() / 100.0})"

    private fun applyCssFontShorthand(ctx: ImageBuffer2DContext, raw: String) {
        val italic = raw.contains("italic", ignoreCase = true) || raw.contains("oblique", ignoreCase = true)
        val weight = when {
            raw.contains("bold", ignoreCase = true) -> 700
            else -> Regex("\\b([1-9]00)\\b").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 400
        }
        val sizeMatch = Regex("([0-9]+(?:\\.[0-9]+)?)px", RegexOption.IGNORE_CASE).find(raw)
        val sizePx = sizeMatch?.groupValues?.get(1)?.toFloatOrNull() ?: ctx.fontSizePx
        val afterSize = if (sizeMatch != null) raw.substring(sizeMatch.range.last + 1).trim() else "sans-serif"
        val family = afterSize.ifEmpty { "sans-serif" }
        ctx.setFont(family, sizePx, weight, italic)
    }

    private fun f(params: Array<out BaseObject>, idx: Int, defaultVal: Float = 0f): Float =
        params.getOrNull(idx)?.takeIf { it !== Undefined && it !== Null }?.toDouble()?.toFloat() ?: defaultVal

    private fun fn(name: String, body: (BaseObject, Array<out BaseObject>) -> BaseObject): AbstractFunction =
        object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = body(_this, params)
            override fun name(): String = name
        }
}
