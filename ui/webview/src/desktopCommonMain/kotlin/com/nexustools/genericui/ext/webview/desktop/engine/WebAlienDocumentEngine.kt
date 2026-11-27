package com.nexustools.genericui.ext.webview.desktop.engine

import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLContextAttributes
import com.nexustools.genericui.component.media.GLContextVersion
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienDirtyTile
import com.nexustools.genericui.ext.alien.AlienExtension
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.ext.alien.impl.ImageAlienImpl
import com.nexustools.genericui.ext.canvas2d.alien.Canvas2DAlienImpl
import com.nexustools.genericui.ext.canvas3d.alien.AlienOffscreenGlContext
import com.nexustools.genericui.ext.canvas3d.alien.Canvas3DAlienImpl
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.media.alien.VideoPlayerAlienImpl
import com.nexustools.genericui.ext.svg.Svg
import com.nexustools.genericui.ext.svg.alien.SvgAlienImpl
import com.nexustools.genericui.ext.webview.desktop.dom.WebCssStyleSheetModel
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomDocument
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomElement
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomNode
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomShadowRoot
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomText
import com.nexustools.genericui.ext.webview.desktop.ipc.WebViewResolvedCapabilities
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.ImageEncodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.res.DataUrlCodec
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.transition.CssTransform2D
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Coordinates the headless [AlienRenderSession], companion extensions (`SvgAlienImpl`,
 * `Canvas2DAlienImpl`, `Canvas3DAlienImpl`, `VideoPlayerAlienImpl`), [ImageLoader],
 * [WebCssCascadeResolver], and bidirectional [WebDomDocument] <-> [AlienWidgetNode] synchronization
 * inside an isolated desktop WebView worker OS process.
 */
class WebAlienDocumentEngine(
    initialWidth: Int,
    initialHeight: Int,
    val capabilities: WebViewResolvedCapabilities,
    allowFileAccess: Boolean
) {
    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val VIEWBOX_SPLIT_REGEX = Regex("[\\s,]+")
        private val NUMBER_REGEX = Regex("-?\\d+(?:\\.\\d+)?")
    }

    val hostFactory: WorkerAlienHostFactory = WorkerAlienHostFactory()
    val session: AlienRenderSession = AlienRenderSession(
        hostFactory = hostFactory,
        initialWidth = initialWidth.coerceAtLeast(1),
        initialHeight = initialHeight.coerceAtLeast(1),
        offscreenOnly = true
    )

    val resourceLoader: WebResourceLoader = WebResourceLoader(
        fileSystem = hostFactory.createFileSystem(),
        httpClient = hostFactory.createHttpClient(),
        allowFileAccess = allowFileAccess
    )

    val cssResolver: WebCssCascadeResolver = WebCssCascadeResolver(resourceLoader) { level, msg ->
        onDiagnosticMessage?.invoke(level, msg)
    }
    val audioEngines: MutableList<WebAudioEngine> = ArrayList()

    internal data class DomRect(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val top: Double = y,
        val left: Double = x,
        val right: Double = x + width,
        val bottom: Double = y + height
    )

    var document: WebDomDocument = WebDomDocument("about:blank", capabilities.storage)
        private set

    var frameSequence: Long = 0L
        private set

    private val domToAlienImpl = LinkedHashMap<WebDomElement, Any>()
    private val alienToDom = LinkedHashMap<AlienWidgetNode, WebDomElement>()
    private val lastSyncedStyleCss = LinkedHashMap<WebDomElement, String>()
    private val loadedStylesheetNodes = LinkedHashMap<WebDomElement, WebCssStyleSheetModel>()
    private val loadedMediaSrcByElement = LinkedHashMap<WebDomElement, String>()
    private val loadedSvgMarkupByElement = LinkedHashMap<WebDomElement, String>()
    internal val parsedTextTracksByMediaElement = LinkedHashMap<WebDomElement, MutableList<Pair<String, List<WebVttCue>>>>()

    var onImageEvent: ((WebDomElement, String) -> Unit)? = null
    var onMediaEvent: ((WebDomElement, String) -> Unit)? = null
    var onDiagnosticMessage: ((String, String) -> Unit)? = null

    private var styleDirty: Boolean = true
    private var domTreeDirty: Boolean = true
    private var lastStylesheetFingerprint: String = ""
    var scriptingEnabled: Boolean = true
        set(value) {
            field = value
            cssResolver.scriptingEnabled = value
        }

    init {
        session.styleSheets.clear()
        installDocumentHooks(document)
    }

    fun resolveDocumentBaseUrl(fallbackUrl: String = document.url): String {
        val baseEl = document.head?.children?.firstOrNull { it.lowerTagName == "base" && !it.getAttribute("href").isNullOrBlank() }
            ?: document.documentElement?.children?.firstOrNull { it.lowerTagName == "base" && !it.getAttribute("href").isNullOrBlank() }
        val href = baseEl?.getAttribute("href")?.trim()?.takeIf { it.isNotEmpty() } ?: return fallbackUrl
        return try {
            resourceLoader.resolveUrl(href, fallbackUrl)
        } catch (_: Throwable) {
            fallbackUrl
        }
    }

    fun extractFaviconUrl(baseUrl: String = document.url): String? {
        val docUrl = baseUrl.trim().ifBlank { document.url.trim() }
        val resolvedBase = resolveDocumentBaseUrl(docUrl.ifBlank { "about:blank" })
        val headRoot = document.head ?: document.documentElement
        var standardIconHref: String? = null
        var fallbackTouchHref: String? = null

        fun scanForFavicon(node: WebDomNode) {
            for (c in node.childNodes) {
                if (c is WebDomElement) {
                    val tag = c.lowerTagName
                    if (tag == "template" || tag == "noscript") continue
                    if (tag == "link") {
                        val href = c.getAttribute("href")?.trim()?.takeIf { it.isNotEmpty() }
                        val relRaw = c.getAttribute("rel")?.trim()?.lowercase().orEmpty()
                        if (href != null && relRaw.isNotEmpty()) {
                            val tokens = relRaw.split(WHITESPACE_REGEX)
                            val isAppleTouch = tokens.any { it == "apple-touch-icon" || it == "apple-touch-icon-precomposed" }
                            val isIcon = tokens.any { it == "icon" }
                            if (isIcon && !isAppleTouch && standardIconHref == null) {
                                standardIconHref = href
                            } else if (isAppleTouch && fallbackTouchHref == null) {
                                fallbackTouchHref = href
                            }
                        }
                    } else if (tag != "body") {
                        scanForFavicon(c)
                    }
                }
            }
        }
        if (headRoot != null) {
            scanForFavicon(headRoot)
        }
        val chosenHref = standardIconHref ?: fallbackTouchHref
        if (chosenHref != null) {
            return try {
                resourceLoader.resolveUrl(chosenHref, resolvedBase)
            } catch (_: Throwable) {
                null
            }
        }
        if (docUrl.startsWith("http://", ignoreCase = true) || docUrl.startsWith("https://", ignoreCase = true)) {
            val schemeEnd = docUrl.indexOf("://")
            val scheme = docUrl.substring(0, schemeEnd).lowercase()
            val afterScheme = docUrl.substring(schemeEnd + 3)
            val authorityEnd = listOf(
                afterScheme.indexOf('/'),
                afterScheme.indexOf('?'),
                afterScheme.indexOf('#')
            ).filter { it >= 0 }.minOrNull() ?: afterScheme.length
            val authority = afterScheme.substring(0, authorityEnd)
            if (authority.isNotEmpty()) {
                return "$scheme://$authority/favicon.ico"
            }
        }
        return null
    }

    fun replaceDocument(newDoc: WebDomDocument) {
        disposeDocumentResources()
        document = newDoc
        installDocumentHooks(newDoc)
        styleDirty = true
        domTreeDirty = true
        lastStylesheetFingerprint = ""
        session.repaintManager.markFullViewportDirty()
    }

    fun markStyleAndDomDirty() {
        styleDirty = true
        domTreeDirty = true
        session.requestLayoutAndRepaint()
    }

    fun resizeViewport(width: Int, height: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (session.viewportWidth != w || session.viewportHeight != h) {
            session.resizeViewport(w, h)
            styleDirty = true
            domTreeDirty = true
            session.repaintManager.markFullViewportDirty()
        }
    }

    fun ensureLayoutSynced() {
        if (styleDirty) {
            syncStylesheetsFromDom()
            styleDirty = false
        }
        if (domTreeDirty) {
            syncDomTreeToAlien()
            domTreeDirty = false
        }
        if (session.isLayoutDirty) {
            session.layoutEngine.performLayout(
                root = session.rootNode,
                viewportWidth = session.viewportWidth,
                viewportHeight = session.viewportHeight,
                updateTracker = session.repaintManager.updateTracker
            )
            session.isLayoutDirty = false
        }
        syncLayoutBoundsBackToDom()
    }

    fun hasPlayingMedia(): Boolean =
        domToAlienImpl.values.any { it is VideoPlayerAlienImpl && it.isPlaying() } ||
            audioEngines.isNotEmpty()

    fun hasActiveAlienAnimations(): Boolean {
        fun check(node: AlienWidgetNode): Boolean {
            if (node.transitionAnimator.isAnimating || node.hasContinuousAnimation) return true
            for (c in node.children) {
                if (check(c)) return true
            }
            return false
        }
        return check(session.rootNode)
    }

    fun stepAlienAnimationsAndLayout(deltaMs: Float) {
        val safeDt = deltaMs.coerceAtLeast(0f)
        if (safeDt <= 0f || !hasActiveAlienAnimations()) return
        var steppedAny = false
        fun stepTree(node: AlienWidgetNode) {
            if (!node.visible) return
            node.resolveVisualStyle()
            if (node.transitionAnimator.step(safeDt, hasContinuousAnimation = node.hasContinuousAnimation)) {
                steppedAny = true
                session.repaintManager.markDirty(node.bounds, padPx = 6)
            }
            for (c in node.children) {
                stepTree(c)
            }
        }
        val startedPass = session.activeStylePassId < 0L
        if (startedPass) session.beginStylePass()
        try {
            stepTree(session.rootNode)
        } finally {
            if (startedPass) session.endStylePass()
        }
        if (steppedAny) {
            session.requestLayoutAndRepaint()
            ensureLayoutSynced()
        }
    }

    fun stepActiveMediaPlayback(deltaMs: Float) {
        val deltaSec = (deltaMs / 1000.0).coerceAtLeast(0.0)
        for (impl in domToAlienImpl.values) {
            if (impl is VideoPlayerAlienImpl && impl.isPlaying() && deltaSec > 0.0) {
                impl.stepHeadlessPlaybackIfNeeded(deltaSec)
            }
        }
        for (audioEngine in audioEngines) {
            audioEngine.pumpSamples(1024)
        }
    }

    fun renderAndConsumeDirtyTiles(deltaMs: Float = 0f, fullFrame: Boolean = false): List<AlienDirtyTile> {
        ensureLayoutSynced()
        if (deltaMs > 0f) {
            stepAlienAnimationsAndLayout(deltaMs)
        }
        stepActiveMediaPlayback(deltaMs)
        if (fullFrame) {
            session.repaintManager.markFullViewportDirty()
        }
        val tiles = session.consumeDirtyTiles(tileSize = 128, deltaMs = 0f)
        if (tiles.isNotEmpty()) {
            frameSequence++
        }
        syncLayoutBoundsBackToDom()
        return tiles
    }

    internal fun computeDomRect(element: WebDomElement): DomRect {
        ensureLayoutSynced()
        val style = cssResolver.resolveComputedMap(element, session.viewportWidth, session.viewportHeight)
        if (style["display"] == "none") {
            return DomRect(0.0, 0.0, 0.0, 0.0)
        }
        if (element.namespaceURI == WebDomElement.SVG_NAMESPACE && element.lowerTagName != "svg") {
            var ancestor = element.parentElement
            while (ancestor != null && ancestor.lowerTagName != "svg") {
                ancestor = ancestor.parentElement
            }
            if (ancestor != null) {
                val svgRect = computeDomRect(ancestor)
                val vbTokens = ancestor.getAttribute("viewBox")?.trim()?.split(VIEWBOX_SPLIT_REGEX)?.mapNotNull { it.toDoubleOrNull() }
                val vbMinX = if (vbTokens != null && vbTokens.size >= 4) vbTokens[0] else 0.0
                val vbMinY = if (vbTokens != null && vbTokens.size >= 4) vbTokens[1] else 0.0
                val vbW = if (vbTokens != null && vbTokens.size >= 4 && vbTokens[2] > 0.0) vbTokens[2] else svgRect.width.coerceAtLeast(1.0)
                val vbH = if (vbTokens != null && vbTokens.size >= 4 && vbTokens[3] > 0.0) vbTokens[3] else svgRect.height.coerceAtLeast(1.0)
                val scaleX = svgRect.width / vbW
                val scaleY = svgRect.height / vbH
                val local = computeSvgElementLocalBounds(element)
                if (local != null) {
                    return DomRect(
                        x = svgRect.x + (local.x - vbMinX) * scaleX,
                        y = svgRect.y + (local.y - vbMinY) * scaleY,
                        width = local.width * scaleX,
                        height = local.height * scaleY
                    )
                }
            }
        }
        val node = element.alienNode
        val b = node?.bounds
        var x = (b?.x?.toDouble() ?: element.cachedLayoutX)
        var y = (b?.y?.toDouble() ?: element.cachedLayoutY)
        var w = (b?.width?.toDouble() ?: element.cachedLayoutWidth)
        var h = (b?.height?.toDouble() ?: element.cachedLayoutHeight)

        // Apply CSS transform effects (translate / scale / rotate) for W3C getBoundingClientRect()
        val transformStr = style["transform"]
        if (!transformStr.isNullOrBlank() && !transformStr.equals("none", ignoreCase = true)) {
            val t2d = CssTransform2D.parse(transformStr)
            if (t2d != null && t2d != CssTransform2D.IDENTITY) {
                val (tx, ty) = t2d.resolveTranslationPx(w.toInt(), h.toInt())
                // AlienLayoutEngine already offsets absolute translate; for non-absolute apply visual transform rect
                val pos = style["position"] ?: "static"
                if (pos != "absolute" && pos != "fixed") {
                    x += tx.toDouble()
                    y += ty.toDouble()
                }
                val sx = t2d.scaleX.toDouble()
                val sy = t2d.scaleY.toDouble()
                val rotDeg = t2d.rotateDeg.toDouble()
                val cx = x + w * 0.5
                val cy = y + h * 0.5
                val scaledW = kotlin.math.abs(w * sx)
                val scaledH = kotlin.math.abs(h * sy)
                if (rotDeg != 0.0) {
                    val rad = kotlin.math.abs(rotDeg) * kotlin.math.PI / 180.0
                    val c = kotlin.math.abs(cos(rad))
                    val s = kotlin.math.abs(sin(rad))
                    w = scaledW * c + scaledH * s
                    h = scaledW * s + scaledH * c
                } else {
                    w = scaledW
                    h = scaledH
                }
                x = cx - w * 0.5
                y = cy - h * 0.5
            }
        }
        return DomRect(x, y, w, h)
    }

    fun getComputedStyleMap(element: WebDomElement, pseudoElement: String? = null): Map<String, String> {
        ensureLayoutSynced()
        return cssResolver.resolveComputedMap(element, session.viewportWidth, session.viewportHeight, pseudoElement)
    }

    fun hitTestElement(x: Double, y: Double): WebDomElement? {
        ensureLayoutSynced()
        val hitNode = session.eventDispatcher.elementFromPoint(x.toFloat(), y.toFloat()) ?: return document.body
        var cur: AlienWidgetNode? = hitNode
        while (cur != null) {
            alienToDom[cur]?.let { return it }
            cur = cur.parent
        }
        return document.body
    }

    fun hitTestElements(x: Double, y: Double): List<WebDomElement> {
        val top = hitTestElement(x, y) ?: return emptyList()
        val chain = ArrayList<WebDomElement>()
        var cur: WebDomElement? = top
        while (cur != null) {
            chain.add(cur)
            cur = cur.parentElement
        }
        return chain
    }

    private fun computeSvgElementLocalBounds(el: WebDomElement): DomRect? {
        fun dAttr(name: String): Double? = el.getAttribute(name)?.trim()?.removeSuffix("px")?.toDoubleOrNull()
        return when (el.lowerTagName) {
            "circle" -> {
                val cx = dAttr("cx") ?: 0.0
                val cy = dAttr("cy") ?: 0.0
                val r = (dAttr("r") ?: 0.0).coerceAtLeast(0.0)
                DomRect(cx - r, cy - r, r * 2.0, r * 2.0)
            }
            "ellipse" -> {
                val cx = dAttr("cx") ?: 0.0
                val cy = dAttr("cy") ?: 0.0
                val rx = (dAttr("rx") ?: 0.0).coerceAtLeast(0.0)
                val ry = (dAttr("ry") ?: 0.0).coerceAtLeast(0.0)
                DomRect(cx - rx, cy - ry, rx * 2.0, ry * 2.0)
            }
            "rect" -> {
                val x = dAttr("x") ?: 0.0
                val y = dAttr("y") ?: 0.0
                val w = (dAttr("width") ?: 0.0).coerceAtLeast(0.0)
                val h = (dAttr("height") ?: 0.0).coerceAtLeast(0.0)
                DomRect(x, y, w, h)
            }
            "line" -> {
                val x1 = dAttr("x1") ?: 0.0
                val y1 = dAttr("y1") ?: 0.0
                val x2 = dAttr("x2") ?: 0.0
                val y2 = dAttr("y2") ?: 0.0
                val minX = min(x1, x2)
                val minY = min(y1, y2)
                DomRect(minX, minY, max(1.0, abs(x2 - x1)), max(1.0, abs(y2 - y1)))
            }
            "polygon", "polyline" -> {
                val nums = el.getAttribute("points")?.let { raw ->
                    NUMBER_REGEX.findAll(raw).mapNotNull { it.value.toDoubleOrNull() }.toList()
                }.orEmpty()
                if (nums.size < 2) return null
                var minX = Double.POSITIVE_INFINITY
                var minY = Double.POSITIVE_INFINITY
                var maxX = Double.NEGATIVE_INFINITY
                var maxY = Double.NEGATIVE_INFINITY
                var idx = 0
                while (idx + 1 < nums.size) {
                    val px = nums[idx]
                    val py = nums[idx + 1]
                    if (px < minX) minX = px
                    if (py < minY) minY = py
                    if (px > maxX) maxX = px
                    if (py > maxY) maxY = py
                    idx += 2
                }
                DomRect(minX, minY, (maxX - minX).coerceAtLeast(1.0), (maxY - minY).coerceAtLeast(1.0))
            }
            "path" -> {
                val nums = el.getAttribute("d")?.let { raw ->
                    NUMBER_REGEX.findAll(raw).mapNotNull { it.value.toDoubleOrNull() }.toList()
                }.orEmpty()
                if (nums.size < 2) return null
                var minX = Double.POSITIVE_INFINITY
                var minY = Double.POSITIVE_INFINITY
                var maxX = Double.NEGATIVE_INFINITY
                var maxY = Double.NEGATIVE_INFINITY
                var idx = 0
                while (idx + 1 < nums.size) {
                    val px = nums[idx]
                    val py = nums[idx + 1]
                    if (px < minX) minX = px
                    if (py < minY) minY = py
                    if (px > maxX) maxX = px
                    if (py > maxY) maxY = py
                    idx += 2
                }
                DomRect(minX, minY, (maxX - minX).coerceAtLeast(1.0), (maxY - minY).coerceAtLeast(1.0))
            }
            "text", "tspan" -> {
                val x = dAttr("x") ?: 0.0
                val y = dAttr("y") ?: 0.0
                val fontSize = (dAttr("font-size") ?: 14.0).coerceAtLeast(4.0)
                val len = el.textContent.length.coerceAtLeast(1)
                DomRect(x, (y - fontSize).coerceAtLeast(0.0), len * fontSize * 0.6, fontSize * 1.2)
            }
            "g", "svg" -> {
                var minX = Double.POSITIVE_INFINITY
                var minY = Double.POSITIVE_INFINITY
                var maxX = Double.NEGATIVE_INFINITY
                var maxY = Double.NEGATIVE_INFINITY
                var any = false
                for (child in el.children) {
                    val cb = computeSvgElementLocalBounds(child) ?: continue
                    any = true
                    if (cb.x < minX) minX = cb.x
                    if (cb.y < minY) minY = cb.y
                    if (cb.x + cb.width > maxX) maxX = cb.x + cb.width
                    if (cb.y + cb.height > maxY) maxY = cb.y + cb.height
                }
                if (!any) null else DomRect(minX, minY, (maxX - minX).coerceAtLeast(0.0), (maxY - minY).coerceAtLeast(0.0))
            }
            else -> null
        }
    }

    fun ensureCanvas2DContext(canvasElement: WebDomElement): ImageBuffer2DContext? {
        if (!capabilities.canvas2d) return null
        val w = (canvasElement.getAttribute("width")?.toIntOrNull() ?: 300).coerceAtLeast(1)
        val h = (canvasElement.getAttribute("height")?.toIntOrNull() ?: 150).coerceAtLeast(1)
        val existing = domToAlienImpl[canvasElement] as? Canvas2DAlienImpl
        if (existing != null) {
            canvasElement.activeContextKind = "2d"
            return existing.getRenderingContext()
        }
        if (canvasElement.activeContextKind == "webgl") return null
        val comp = Canvas2D(width = w, height = h, doubleBuffer = true, initialAntialias = true)
        val impl = (AlienExtension.createExtensionNativeImpl(comp, session, hostFactory) as? Canvas2DAlienImpl)
            ?: Canvas2DAlienImpl(canvasComponent = comp, doubleBuffer = true, session = session)
        impl.node.preferredSize = Size(w, h)
        domToAlienImpl[canvasElement] = impl
        canvasElement.backingCanvas2D = comp
        canvasElement.activeContextKind = "2d"
        bindAlienNodeToElement(canvasElement, impl.node)
        domTreeDirty = true
        return impl.getRenderingContext()
    }

    fun ensureWebGLContext(canvasElement: WebDomElement, version: Int, alpha: Boolean, depth: Boolean, stencil: Boolean): AlienOffscreenGlContext? {
        if (!capabilities.webgl) return null
        val w = (canvasElement.getAttribute("width")?.toIntOrNull() ?: 300).coerceAtLeast(1)
        val h = (canvasElement.getAttribute("height")?.toIntOrNull() ?: 150).coerceAtLeast(1)
        val existing = domToAlienImpl[canvasElement] as? Canvas3DAlienImpl
        if (existing != null) {
            canvasElement.activeContextKind = "webgl"
            return existing.getRenderingContext() as? AlienOffscreenGlContext
        }
        if (canvasElement.activeContextKind == "2d") return null
        val glAttrs = GLContextAttributes(
            version = if (version >= 2) GLContextVersion.ES_3_0 else GLContextVersion.ES_3_0,
            alpha = alpha,
            depth = depth,
            stencil = stencil
        )
        val comp = Canvas3D(
            backend = GpuBackendType.OPENGL_ES,
            acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
            config = Canvas3DConfig(gl = glAttrs),
            width = w,
            height = h
        )
        val impl = (AlienExtension.createExtensionNativeImpl(comp, session, hostFactory) as? Canvas3DAlienImpl)
            ?: Canvas3DAlienImpl(component = comp, session = session)
        impl.node.preferredSize = Size(w, h)
        domToAlienImpl[canvasElement] = impl
        canvasElement.backingCanvas3D = comp
        canvasElement.activeContextKind = "webgl"
        bindAlienNodeToElement(canvasElement, impl.node)
        domTreeDirty = true
        return impl.getRenderingContext() as? AlienOffscreenGlContext
    }

    fun canvasToDataUrl(canvasElement: WebDomElement, mimeType: String = "image/png", quality: Float = 0.92f): String {
        val impl2d = domToAlienImpl[canvasElement] as? Canvas2DAlienImpl
        val impl3d = domToAlienImpl[canvasElement] as? Canvas3DAlienImpl
        val buffer: ImageBuffer = when {
            impl2d != null -> impl2d.getRenderingContext().toImageBuffer()
            impl3d != null -> {
                impl3d.present()
                val rgba = impl3d.captureFrameToRgba()
                val w = impl3d.getSurfaceWidth()
                val h = impl3d.getSurfaceHeight()
                ImageBuffer(w, h, PixelFormat.RGBA_8888, ImageColorSpace.SRGB, rgba)
            }
            else -> {
                val w = (canvasElement.getAttribute("width")?.toIntOrNull() ?: 300).coerceAtLeast(1)
                val h = (canvasElement.getAttribute("height")?.toIntOrNull() ?: 150).coerceAtLeast(1)
                ImageBuffer(w, h, PixelFormat.RGBA_8888, ImageColorSpace.SRGB, ByteArray(w * h * 4))
            }
        }
        val cleanMime = mimeType.trim().lowercase()
        val format = when (cleanMime) {
            "image/jpeg", "image/jpg" -> ImageFormat.JPEG
            "image/webp" -> ImageFormat.PNG
            else -> ImageFormat.PNG
        }
        val opts = ImageEncodeOptions(
            format = format,
            quality = (quality * 100f).roundToInt().coerceIn(1, 100)
        )
        val encoded = ImageLoader.encode(buffer, opts)
        val actualMime = if (format == ImageFormat.JPEG) "image/jpeg" else "image/png"
        return DataUrlCodec.encodeBytes(encoded, actualMime)
    }

    fun resolveCanvasBackingBuffer(sourceElement: WebDomElement): ImageBuffer? {
        val impl2d = domToAlienImpl[sourceElement] as? Canvas2DAlienImpl
        if (impl2d != null) return impl2d.getRenderingContext().toImageBuffer()
        val impl3d = domToAlienImpl[sourceElement] as? Canvas3DAlienImpl
        if (impl3d != null) {
            impl3d.present()
            val rgba = impl3d.captureFrameToRgba()
            return ImageBuffer(impl3d.getSurfaceWidth(), impl3d.getSurfaceHeight(), PixelFormat.RGBA_8888, ImageColorSpace.SRGB, rgba)
        }
        if (sourceElement.lowerTagName == "img") {
            loadImageElementIfNeeded(sourceElement)
            return sourceElement.backingImageBuffer
        }
        if (sourceElement.lowerTagName == "video") {
            val vImpl = domToAlienImpl[sourceElement] as? VideoPlayerAlienImpl
            if (vImpl != null) return vImpl.copyLatestVideoFrame()
        }
        return null
    }

    fun loadImageElementIfNeeded(imgEl: WebDomElement) {
        val src = imgEl.getAttribute("src")?.trim().orEmpty()
        if (src.isEmpty()) return
        val loadedKey = loadedMediaSrcByElement[imgEl]
        if (loadedKey == src && imgEl.backingImageBuffer != null) return
        loadedMediaSrcByElement[imgEl] = src
        try {
            val res = resourceLoader.loadSubresourceSync(src, resolveDocumentBaseUrl())
            if (!res.ok) {
                imgEl.imageLoadedComplete = true
                imgEl.imageNaturalWidth = 0
                imgEl.imageNaturalHeight = 0
                onImageEvent?.invoke(imgEl, "error")
                return
            }
            val bytes = res.bytes
            val isSvg = res.mimeType.contains("svg", ignoreCase = true) ||
                src.endsWith(".svg", ignoreCase = true) ||
                (bytes.size >= 4 && bytes.decodeToString().trimStart().startsWith("<svg", ignoreCase = true))
            val buffer = if (isSvg) {
                val svg = Svg.fromXml(bytes.decodeToString())
                val natural = svg.resolveIntrinsicPixelSize()
                svg.renderToImageBuffer(natural.width.coerceAtLeast(1), natural.height.coerceAtLeast(1))
            } else {
                ImageLoader.loadFromBytes(bytes)
            }
            imgEl.backingImageBuffer = buffer
            imgEl.imageNaturalWidth = buffer.width
            imgEl.imageNaturalHeight = buffer.height
            imgEl.imageLoadedComplete = true
            (domToAlienImpl[imgEl] as? ImageAlienImpl)?.let { imgImpl ->
                imgImpl.node.intrinsicWidth = buffer.width.toFloat()
                imgImpl.node.intrinsicHeight = buffer.height.toFloat()
                imgImpl.setSourceUrl(res.url)
                imgImpl.node.markMeasureDirty()
            }
            onImageEvent?.invoke(imgEl, "load")
        } catch (t: Throwable) {
            imgEl.imageLoadedComplete = true
            imgEl.imageNaturalWidth = 0
            imgEl.imageNaturalHeight = 0
            onDiagnosticMessage?.invoke("warn", "Failed to decode <img> '$src': ${t.message}")
            onImageEvent?.invoke(imgEl, "error")
        }
    }

    fun loadMediaElementSource(mediaEl: WebDomElement): Pair<String?, WebWavDecoder.DecodedAudio?> {
        if (!capabilities.media) return null to null
        val vImpl = ensureVideoPlayerImpl(mediaEl)
        val explicitSrc = mediaEl.getAttribute("src")?.trim()?.takeIf { it.isNotEmpty() }
        val candidates = ArrayList<String>()
        if (explicitSrc != null) candidates.add(explicitSrc)
        for (c in mediaEl.children) {
            if (c.lowerTagName == "source") {
                val s = c.getAttribute("src")?.trim()?.takeIf { it.isNotEmpty() }
                val mime = c.getAttribute("type")?.trim()?.lowercase().orEmpty()
                if (s != null && (mime.isEmpty() || canPlayMimeType(mime).isNotEmpty())) {
                    candidates.add(s)
                }
            }
        }
        var chosenResolvedUrl: String? = null
        var decodedAudio: WebWavDecoder.DecodedAudio? = null
        val mediaBaseUrl = resolveDocumentBaseUrl()
        for (candidate in candidates) {
            val localFile = resourceLoader.resolveLocalFilePathIfAccessible(candidate, mediaBaseUrl)
            if (localFile != null) {
                chosenResolvedUrl = localFile
                val res = resourceLoader.loadSubresourceSync(candidate, mediaBaseUrl)
                if (res.ok) {
                    decodedAudio = WebWavDecoder.decodeOrNull(res.bytes)
                    vImpl.setSource(localFile)
                }
                break
            } else {
                val res = resourceLoader.loadSubresourceSync(candidate, mediaBaseUrl)
                if (res.ok && res.bytes.isNotEmpty()) {
                    chosenResolvedUrl = res.url
                    decodedAudio = WebWavDecoder.decodeOrNull(res.bytes)
                    vImpl.setSource(res.url)
                    break
                }
            }
        }
        // Load <track> WebVTT cues cleanly
        val trackList = parsedTextTracksByMediaElement.getOrPut(mediaEl) { ArrayList() }
        trackList.clear()
        for (c in mediaEl.children) {
            if (c.lowerTagName == "track") {
                val trackSrc = c.getAttribute("src")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val label = c.getAttribute("label")?.takeIf { it.isNotBlank() } ?: "Subtitles"
                val lang = c.getAttribute("srclang")?.takeIf { it.isNotBlank() } ?: "en"
                val resolvedTrack = resourceLoader.resolveLocalFilePathIfAccessible(trackSrc, mediaBaseUrl)
                    ?: resourceLoader.resolveUrl(trackSrc, mediaBaseUrl)
                vImpl.addExternalSubtitleTrack(label, lang, resolvedTrack)
                val res = resourceLoader.loadSubresourceSync(trackSrc, mediaBaseUrl)
                if (res.ok) {
                    val parsedCues = WebVttParser.parse(res.decodeUtf8Text())
                    trackList.add(label to parsedCues)
                } else {
                    onDiagnosticMessage?.invoke("warn", "Failed loading <track> '$trackSrc': ${res.status} ${res.statusText}")
                }
            }
        }
        return chosenResolvedUrl to decodedAudio
    }

    fun ensureVideoPlayerImpl(mediaEl: WebDomElement): VideoPlayerAlienImpl {
        val existing = domToAlienImpl[mediaEl] as? VideoPlayerAlienImpl
        if (existing != null) return existing
        val player = VideoPlayer()
        val impl = (AlienExtension.createExtensionNativeImpl(player, session, hostFactory) as? VideoPlayerAlienImpl)
            ?: VideoPlayerAlienImpl(session = session)
        impl.enableOffscreenFrameCapture(true)
        impl.setMuted(mediaEl.hasAttribute("muted"))
        impl.setLoop(mediaEl.hasAttribute("loop"))
        domToAlienImpl[mediaEl] = impl
        mediaEl.backingVideoPlayer = player
        bindAlienNodeToElement(mediaEl, impl.node)
        domTreeDirty = true
        return impl
    }

    fun canPlayMimeType(rawMime: String): String {
        val m = rawMime.trim().lowercase().substringBefore(';').trim()
        return when (m) {
            "video/mp4", "audio/mp4", "audio/wav", "audio/wave", "audio/x-wav", "audio/mpeg", "audio/ogg" -> "probably"
            "video/webm", "video/ogg", "audio/webm", "audio/aac", "audio/flac" -> "maybe"
            else -> ""
        }
    }

    private fun syncStylesheetsFromDom() {
        val activeModels = ArrayList<WebCssStyleSheetModel>()
        val seenSheets = HashSet<WebCssStyleSheetModel>()

        val docBaseUrl = resolveDocumentBaseUrl()
        fun collectFrom(node: WebDomNode) {
            for (c in node.childNodes) {
                if (resourceLoader.isAbortedProvider?.invoke() == true) return
                if (c is WebDomElement) {
                    if (c.lowerTagName == "style") {
                        val existing = loadedStylesheetNodes[c]
                        val text = c.textContent
                        val model = if (existing != null) {
                            if (existing.serializeCssText() != text && !existing.disabled) {
                                // Only replace if programmatic CSSOM rules weren't manually modified out-of-band
                            }
                            existing
                        } else {
                            WebCssStyleSheetModel(ownerNode = c, href = docBaseUrl, initialCss = text).also { m ->
                                m.onModified = { markStyleAndDomDirty() }
                                loadedStylesheetNodes[c] = m
                            }
                        }
                        if (seenSheets.add(model)) activeModels.add(model)
                    } else if (c.lowerTagName == "link") {
                        val relTokens = c.getAttribute("rel")?.trim()?.lowercase()?.split(WHITESPACE_REGEX).orEmpty()
                        if ("stylesheet" in relTokens && "alternate" !in relTokens) {
                            val href = c.getAttribute("href")?.trim().orEmpty()
                            if (href.isNotEmpty()) {
                                val existing = loadedStylesheetNodes[c]
                                val model = existing ?: run {
                                    if (resourceLoader.isAbortedProvider?.invoke() == true) return
                                    val res = resourceLoader.loadSubresourceSync(href, docBaseUrl)
                                    if (!res.ok) {
                                        onDiagnosticMessage?.invoke("warn", "Skipping stylesheet '$href': ${res.status} ${res.statusText}")
                                    }
                                    val css = if (res.ok) res.decodeUtf8Text() else ""
                                    WebCssStyleSheetModel(ownerNode = c, href = res.url, initialCss = css).also { m ->
                                        m.onModified = { markStyleAndDomDirty() }
                                        loadedStylesheetNodes[c] = m
                                    }
                                }
                                if (seenSheets.add(model)) activeModels.add(model)
                            }
                        }
                    } else {
                        collectFrom(c)
                    }
                }
            }
        }
        document.documentElement?.let { collectFrom(it) }
        if (resourceLoader.isAbortedProvider?.invoke() == true) return

        // Preserve any sheets manually pushed or modified
        for (existing in document.styleSheets) {
            if (seenSheets.add(existing) && (existing.ownerNode == null || existing.ownerNode!!.isConnected)) {
                activeModels.add(existing)
            }
        }
        document.styleSheets.clear()
        document.styleSheets.addAll(activeModels)
        val fingerprint = buildString {
            append(session.viewportWidth).append('x').append(session.viewportHeight).append(';')
            for (m in activeModels) {
                append(m.disabled).append('|').append(m.href.orEmpty()).append('|').append(m.serializeCssText()).append('\n')
            }
            fun appendShadowStyles(n: WebDomNode) {
                if (n is WebDomElement && n.shadowRoot != null) {
                    val sr = n.shadowRoot!!
                    append("SR:").append(sr.scopeId).append(':')
                    fun walkSr(sc: WebDomNode) {
                        for (ch in sc.childNodes) {
                            if (ch is WebDomElement) {
                                if (ch.lowerTagName == "style") append(ch.textContent).append('\n')
                                else walkSr(ch)
                            }
                        }
                    }
                    walkSr(sr)
                }
                for (c in n.childNodes) appendShadowStyles(c)
            }
            document.documentElement?.let { appendShadowStyles(it) }
        }
        if (fingerprint == lastStylesheetFingerprint && session.styleSheets.isNotEmpty()) {
            return
        }
        lastStylesheetFingerprint = fingerprint
        cssResolver.syncDocumentStyleSheets(document, session, session.viewportWidth, session.viewportHeight)
    }

    private fun resolveViewportCanvasBackground(): String {
        fun nonTransparentBg(el: WebDomElement?): String? {
            if (el == null) return null
            val style = cssResolver.resolveComputedMap(el, session.viewportWidth, session.viewportHeight)
            val bg = style["background-color"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val lower = bg.lowercase()
            if (lower == "transparent" || lower == "rgba(0, 0, 0, 0)" || lower == "rgba(0,0,0,0)") return null
            return bg
        }
        return nonTransparentBg(document.documentElement) ?: nonTransparentBg(document.body) ?: "#ffffff"
    }

    private fun resolveViewportCanvasBackgroundImage(): String? {
        fun nonNoneBgImg(el: WebDomElement?): String? {
            if (el == null) return null
            val style = cssResolver.resolveComputedMap(el, session.viewportWidth, session.viewportHeight)
            val img = style["background-image"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return if (img.equals("none", ignoreCase = true)) null else img
        }
        return nonNoneBgImg(document.documentElement) ?: nonNoneBgImg(document.body)
    }

    private fun syncDomTreeToAlien() {
        alienToDom.clear()
        session.rootNode.clearChildren()
        val canvasBg = resolveViewportCanvasBackground()
        val canvasBgImg = resolveViewportCanvasBackgroundImage()
        val rootInlineCss = buildString {
            append("width: ").append(session.viewportWidth).append("px; ")
            append("min-height: ").append(session.viewportHeight).append("px; ")
            append("height: ").append(session.viewportHeight).append("px; ")
            append("background-color: ").append(canvasBg).append(';')
            if (!canvasBgImg.isNullOrBlank()) {
                append(" background-image: ").append(canvasBgImg).append(';')
            }
        }
        session.rootNode.computedStyle = CssStyleSheet.parseInlineStyle(rootInlineCss)
        val rootEl = document.documentElement ?: return
        val rootAlien = syncElementSubtree(rootEl, shadowScopeId = null)
        if (rootAlien != null) {
            session.rootNode.addChild(rootAlien)
        }
        session.requestLayoutAndRepaint()
    }

    private fun syncElementSubtree(element: WebDomElement, shadowScopeId: String?): AlienWidgetNode? {
        val tag = element.lowerTagName
        if (tag == "head" || tag == "meta" || tag == "link" || tag == "title" ||
            tag == "style" || tag == "script" || tag == "template" || tag == "source" || tag == "track" ||
            (tag == "noscript" && scriptingEnabled)
        ) {
            return null
        }

        val resolvedStyle = cssResolver.resolveComputedMap(element, session.viewportWidth, session.viewportHeight)
        val alienNode = obtainAlienNodeForElement(element, shadowScopeId, resolvedStyle)
        alienNode.clearChildren()
        alienToDom[alienNode] = element

        // Sync attributes, classes, and CSS inline/var-resolved computed style
        syncElementAttributesAndStyleToAlien(element, alienNode, shadowScopeId, resolvedStyle)

        if (tag == "svg" || tag == "canvas" || tag == "img" || tag == "video" || tag == "audio") {
            return alienNode
        }

        val shadowRoot = element.shadowRoot
        if (shadowRoot != null && capabilities.shadowDom) {
            for (srChild in shadowRoot.childNodes) {
                appendVisualNode(srChild, alienNode, shadowRoot.scopeId, shadowRoot)
            }
        } else {
            for (child in element.childNodes) {
                appendVisualNode(child, alienNode, shadowScopeId, activeShadowRoot = null)
            }
        }
        return alienNode
    }

    private fun appendVisualNode(
        node: WebDomNode,
        parentAlien: AlienWidgetNode,
        shadowScopeId: String?,
        activeShadowRoot: WebDomShadowRoot?
    ) {
        when (node) {
            is WebDomElement -> {
                if (node.lowerTagName == "slot" && activeShadowRoot != null) {
                    val assigned = activeShadowRoot.assignedNodesForSlot(node, flatten = true)
                    for (dist in assigned) {
                        appendVisualNode(dist, parentAlien, shadowScopeId = null, activeShadowRoot = null)
                    }
                    return
                }
                val childAlien = syncElementSubtree(node, shadowScopeId)
                if (childAlien != null) {
                    parentAlien.addChild(childAlien)
                }
            }
            is WebDomText -> {
                val txt = node.data
                val parentEl = node.parentElement
                val preserveWhitespace = parentEl?.computedStyleSnapshot?.get("white-space")?.let {
                    it == "pre" || it == "pre-wrap"
                } == true || parentEl?.lowerTagName == "pre"
                val normalized = if (preserveWhitespace) txt else txt.replace(WHITESPACE_REGEX, " ")
                if (normalized.isBlank() && parentEl?.computedStyleSnapshot?.get("white-space") != "pre") {
                    return
                }
                val textNode = (node.alienNode ?: AlienWidgetNode(
                    componentClassPrefix = "web-text",
                    tagName = "#text",
                    session = session
                ).also { node.alienNode = it })
                textNode.customMeasure = { availW, _, _ ->
                    val pStyle = parentEl?.alienNode?.resolveVisualStyle() ?: textNode.resolveVisualStyle()
                    val fontPx = pStyle.fontSizePx.coerceAtLeast(8f)
                    val ctx = session.canvas2D.getContext()
                    ctx.fontSizePx = fontPx
                    ctx.fontFamily = pStyle.fontFamily
                    ctx.fontWeight = pStyle.fontWeight
                    val measuredW = ctx.measureTextWidth(normalized.trim()).roundToInt().coerceAtLeast(1)
                    val whiteSpace = parentEl?.computedStyleSnapshot?.get("white-space").orEmpty()
                    val nowrap = whiteSpace == "nowrap"
                    val lines = if (!nowrap && availW > 0 && measuredW > availW) {
                        ((measuredW + availW - 1) / availW).coerceAtLeast(1)
                    } else if (preserveWhitespace) {
                        normalized.count { it == '\n' } + 1
                    } else {
                        1
                    }
                    val finalW = if (!nowrap && availW > 0) min(measuredW, availW) else measuredW
                    val lineH = (fontPx * 1.35f).roundToInt().coerceAtLeast(14)
                    Size(finalW, lines * lineH)
                }
                textNode.customPaint = { ctx, bounds, _ ->
                    val pStyle = parentEl?.alienNode?.resolveVisualStyle() ?: textNode.resolveVisualStyle()
                    AlienBootstrapPainter.paintText(
                        ctx = ctx,
                        text = if (preserveWhitespace) txt else normalized.trim(),
                        contentBounds = bounds,
                        style = pStyle,
                        verticalCenter = false,
                        maxLines = null
                    )
                }
                parentAlien.addChild(textNode)
            }
        }
    }

    private fun obtainAlienNodeForElement(
        element: WebDomElement,
        shadowScopeId: String?,
        resolvedStyle: Map<String, String>
    ): AlienWidgetNode {
        val tag = element.lowerTagName
        if (tag == "svg" && capabilities.svg) {
            val markup = element.serializeOuterHtml()
            val prevMarkup = loadedSvgMarkupByElement[element]
            val existing = domToAlienImpl[element] as? SvgAlienImpl
            if (existing != null && prevMarkup == markup) {
                return existing.node
            }
            val svgNode = try {
                val svgComp = Svg.fromXml(markup)
                val impl = existing ?: ((AlienExtension.createExtensionNativeImpl(svgComp, session, hostFactory) as? SvgAlienImpl)
                    ?: SvgAlienImpl(svgComponent = svgComp, session = session))
                impl.syncSvgScene(svgComp)
                element.backingSvg = svgComp
                domToAlienImpl[element] = impl
                loadedSvgMarkupByElement[element] = markup
                bindAlienNodeToElement(element, impl.node)
                impl.node
            } catch (t: Throwable) {
                onDiagnosticMessage?.invoke("warn", "Ignoring malformed inline <svg>: ${t.message}")
                loadedSvgMarkupByElement[element] = markup
                null
            }
            if (svgNode != null) {
                return svgNode
            }
        }

        if (tag == "img") {
            val existing = domToAlienImpl[element] as? ImageAlienImpl
            val impl = existing ?: ImageAlienImpl(session = session).also {
                domToAlienImpl[element] = it
                bindAlienNodeToElement(element, it.node)
            }
            loadImageElementIfNeeded(element)
            return impl.node
        }

        if (tag == "canvas") {
            val w = (element.getAttribute("width")?.toIntOrNull() ?: 300).coerceAtLeast(1)
            val h = (element.getAttribute("height")?.toIntOrNull() ?: 150).coerceAtLeast(1)
            val impl2d = domToAlienImpl[element] as? Canvas2DAlienImpl
            val impl3d = domToAlienImpl[element] as? Canvas3DAlienImpl
            if (impl2d != null) {
                if (impl2d.getSurfaceWidth() != w || impl2d.getSurfaceHeight() != h) {
                    impl2d.getRenderingContext().resizeSurface(w, h)
                }
                impl2d.node.preferredSize = Size(w, h)
                return impl2d.node
            }
            if (impl3d != null) {
                if (impl3d.getSurfaceWidth() != w || impl3d.getSurfaceHeight() != h) {
                    impl3d.resizeSurface(w, h)
                }
                impl3d.node.preferredSize = Size(w, h)
                return impl3d.node
            }
            // Do NOT eagerly lock <canvas> to 2d before JS calls getContext('2d' | 'webgl' | 'webgl2').
            val placeholder = element.alienNode ?: AlienWidgetNode(
                componentClassPrefix = "web-canvas",
                tagName = "canvas",
                session = session
            ).also { element.alienNode = it }
            placeholder.preferredSize = Size(w, h)
            return placeholder
        }

        if ((tag == "video" || tag == "audio") && capabilities.media) {
            val impl = ensureVideoPlayerImpl(element)
            val key = element.getAttribute("src").orEmpty() + "|" + element.children.filter { it.lowerTagName == "source" }.joinToString(",") { it.getAttribute("src").orEmpty() }
            if (loadedMediaSrcByElement[element] != key && key.isNotEmpty() && key != "|") {
                loadedMediaSrcByElement[element] = key
                loadMediaElementSource(element)
            }
            if (tag == "audio" && !element.hasAttribute("controls")) {
                impl.node.preferredSize = Size(0, 0)
            } else {
                val wAttr = element.getAttribute("width")?.toIntOrNull()
                val hAttr = element.getAttribute("height")?.toIntOrNull()
                if (wAttr != null || hAttr != null) {
                    impl.node.preferredSize = Size(wAttr ?: 300, hAttr ?: 150)
                }
            }
            return impl.node
        }

        val node = element.alienNode ?: AlienWidgetNode(
            componentClassPrefix = if (tag == "body" || tag == "html") "" else "web-$tag",
            tagName = tag,
            session = session
        ).also {
            element.alienNode = it
            configureControlPaintAndMeasureIfNeeded(element, it)
        }
        @Suppress("UNUSED_VARIABLE")
        val unused = resolvedStyle to shadowScopeId
        return node
    }

    private fun configureControlPaintAndMeasureIfNeeded(element: WebDomElement, node: AlienWidgetNode) {
        val tag = element.lowerTagName
        if (tag == "input" || tag == "button" || tag == "textarea" || tag == "select") {
            node.customMeasure = { _, _, style ->
                val textLabel = when (tag) {
                    "button" -> element.textContent
                    "input" -> element.value.ifEmpty { element.getAttribute("placeholder").orEmpty() }
                    "textarea" -> element.value
                    "select" -> element.collectOptions().firstOrNull { it.checked }?.textContent.orEmpty()
                    else -> ""
                }
                val isCheckRadio = tag == "input" && (element.inputType == "checkbox" || element.inputType == "radio")
                val padX = style.padding.left + style.padding.right + 12
                val padY = style.padding.top + style.padding.bottom + 8
                if (isCheckRadio) {
                    Size(16, 16)
                } else {
                    val estW = max(60, (textLabel.length * style.fontSizePx * 0.55f).roundToInt() + padX)
                    val estH = max(24, (style.fontSizePx * 1.4f).roundToInt() + padY)
                    Size(estW, estH)
                }
            }
            node.customPaint = { ctx, bounds, style ->
                val textLabel = when (tag) {
                    "button" -> element.textContent
                    "input" -> if (element.inputType == "password") "*".repeat(element.value.length) else element.value
                    "textarea" -> element.value
                    else -> ""
                }
                if (tag == "input" && (element.inputType == "checkbox" || element.inputType == "radio")) {
                    ctx.setStrokeColor(RgbaColor(60, 60, 60, 255))
                    ctx.lineWidth = 1.5f
                    if (element.checked) {
                        ctx.setFillColor(RgbaColor(0, 120, 215, 255))
                        ctx.fillRect(bounds.x.toFloat(), bounds.y.toFloat(), bounds.width.toFloat(), bounds.height.toFloat())
                    } else {
                        ctx.strokeRect(bounds.x.toFloat(), bounds.y.toFloat(), bounds.width.toFloat(), bounds.height.toFloat())
                    }
                } else if (textLabel.isNotEmpty()) {
                    AlienBootstrapPainter.paintText(
                        ctx = ctx,
                        text = textLabel,
                        contentBounds = bounds,
                        style = style,
                        verticalCenter = true,
                        maxLines = 1
                    )
                }
            }
        }
    }

    private fun bindAlienNodeToElement(element: WebDomElement, node: AlienWidgetNode) {
        element.alienNode = node
        alienToDom[node] = element
    }

    private fun syncElementAttributesAndStyleToAlien(
        element: WebDomElement,
        node: AlienWidgetNode,
        shadowScopeId: String?,
        resolvedStyle: Map<String, String>
    ) {
        node.attributes.clear()
        for ((k, v) in element.attributesMap) {
            node.attributes[k] = v
        }
        if (shadowScopeId != null) {
            node.attributes["data-gui-shadow-scope"] = shadowScopeId
        }
        element.shadowRoot?.let { sr ->
            node.attributes["data-gui-shadow-host"] = sr.scopeId
        }
        node.styleSnapshot = node.styleSnapshot.copy(
            customClasses = element.classOrder.toSet(),
            componentPrefix = if (element.lowerTagName == "body" || element.lowerTagName == "html") "" else "web-${element.lowerTagName}"
        )
        node.hoveredState = element.isHoveredState
        node.focusedState = element.isFocusedState
        node.textContentForSelector = element.childNodes.any { it is WebDomText && it.data.isNotBlank() }

        val effectiveInlineCss = buildString {
            val mergedInline = if (element.animatedStyleOverrides.isEmpty()) {
                element.inlineStyleMap
            } else {
                LinkedHashMap(element.inlineStyleMap).apply {
                    for ((k, v) in element.animatedStyleOverrides) {
                        if (k !in element.inlineImportantProps) {
                            put(k, v)
                        }
                    }
                }
            }
            // Emit resolved author stylesheet declarations (with var(), calc/min/max/clamp, complex selectors, Shadow DOM)
            for ((k, v) in element.authorStyleSnapshot) {
                if (k.startsWith("--")) continue
                if (mergedInline.containsKey(k)) {
                    val authorImportantWins = k in element.authorImportantSnapshot && k !in element.inlineImportantProps
                    if (!authorImportantWins) continue
                }
                append(k).append(": ").append(v)
                if (k in element.authorImportantSnapshot) append(" !important")
                append("; ")
            }
            val rootFontPx = element.ownerDocument?.documentElement?.resolvedFontSizePx ?: 16f
            val parentEl = element.parentNode as? WebDomElement
            for ((k, v) in mergedInline) {
                if (k.startsWith("--")) continue
                if (k in element.authorImportantSnapshot && k !in element.inlineImportantProps) {
                    continue
                }
                val resolvedV = cssResolver.resolveCssValue(
                    prop = k,
                    rawValue = v,
                    scopeVars = resolvedStyle,
                    fontSizePx = element.resolvedFontSizePx,
                    rootFontSizePx = rootFontPx,
                    viewportWidth = session.viewportWidth,
                    viewportHeight = session.viewportHeight,
                    parentElement = parentEl
                )
                append(k).append(": ").append(resolvedV)
                if (k in element.inlineImportantProps) append(" !important")
                append("; ")
            }
        }
        if (lastSyncedStyleCss[element] != effectiveInlineCss) {
            lastSyncedStyleCss[element] = effectiveInlineCss
            node.computedStyle = if (effectiveInlineCss.isBlank()) null else CssStyleSheet.parseInlineStyle(effectiveInlineCss)
        }
        node.visible = resolvedStyle["display"] != "none" && !element.hasAttribute("hidden")
        if (node.bounds.width > 0 && node.bounds.height > 0) {
            val sx = element.scrollLeftPx.roundToInt()
            val sy = element.scrollTopPx.roundToInt()
            if (node.scrollOffsetX != sx || node.scrollOffsetY != sy) {
                node.scrollTo(sx, sy)
            }
        }
    }

    private fun syncLayoutBoundsBackToDom() {
        fun walk(node: WebDomNode) {
            if (node is WebDomElement) {
                val a = node.alienNode
                if (a != null) {
                    node.cachedLayoutX = a.bounds.x.toDouble()
                    node.cachedLayoutY = a.bounds.y.toDouble()
                    node.cachedLayoutWidth = a.bounds.width.toDouble()
                    node.cachedLayoutHeight = a.bounds.height.toDouble()
                }
                node.shadowRoot?.let { walk(it) }
            }
            for (c in node.childNodes) walk(c)
        }
        document.documentElement?.let { walk(it) }
    }

    private fun installDocumentHooks(doc: WebDomDocument) {
        doc.onDomChanged = {
            styleDirty = true
            domTreeDirty = true
            session.requestLayoutAndRepaint()
        }
        doc.elementFromPointProvider = { x, y -> hitTestElement(x, y) }
        doc.elementsFromPointProvider = { x, y -> hitTestElements(x, y) }
    }

    fun disposeDocumentResources() {
        for (impl in domToAlienImpl.values) {
            when (impl) {
                is Canvas2DAlienImpl -> impl.dispose()
                is Canvas3DAlienImpl -> impl.dispose()
                is SvgAlienImpl -> impl.dispose()
                is VideoPlayerAlienImpl -> impl.dispose()
                is ImageAlienImpl -> impl.dispose()
            }
        }
        domToAlienImpl.clear()
        alienToDom.clear()
        lastSyncedStyleCss.clear()
        loadedStylesheetNodes.clear()
        loadedMediaSrcByElement.clear()
        loadedSvgMarkupByElement.clear()
        lastStylesheetFingerprint = ""
        for (ae in audioEngines) {
            ae.disposeAll()
        }
        audioEngines.clear()
    }

    fun dispose() {
        disposeDocumentResources()
        session.canvas2D.dispose()
    }
}
