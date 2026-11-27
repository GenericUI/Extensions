@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.webview.desktop.njs

import com.nexustools.genericui.ext.alien.AlienDirtyTile
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomDocument
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomElement
import com.nexustools.genericui.ext.webview.desktop.dom.WebHtml5Parser
import com.nexustools.genericui.ext.webview.desktop.engine.WebAlienDocumentEngine
import com.nexustools.genericui.ext.webview.desktop.ipc.WebViewResolvedCapabilities
import com.nexustools.njs.compiler.RuntimeCompiler
import com.nexustools.njs.runtime.BaseFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.GenericObject
import com.nexustools.njs.runtime.Global
import com.nexustools.njs.runtime.JSBoolean
import com.nexustools.njs.runtime.JSError
import com.nexustools.njs.runtime.JSNumber
import com.nexustools.njs.runtime.JSString
import com.nexustools.njs.runtime.Null
import com.nexustools.njs.runtime.Scopable
import com.nexustools.njs.runtime.Scope
import com.nexustools.njs.runtime.Undefined
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.math.floor
import kotlin.time.TimeSource

/**
 * Coordinates one isolated desktop WebView instance inside the worker OS process, binding
 * [WebAlienDocumentEngine] (Alien + SVG + Canvas2D + Canvas3D + Video/Audio/WebAudio) to the NJS
 * runtime, event scheduler, DOM/CSSOM/BOM/Network/IPC bridges, input routing, and navigation state.
 */
class WebViewWorkerRuntime(
    initialWidth: Int,
    initialHeight: Int,
    val enableJavaScript: Boolean,
    val allowFileAccess: Boolean,
    val capabilities: WebViewResolvedCapabilities,
    val userAgentOverride: String?,
    private val onConsoleMessage: (level: String, message: String, sourceId: String, line: Int) -> Unit,
    private val onJavaScriptError: (String) -> Unit,
    private val onPageFinished: (url: String, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    private val onWebMessageToHost: (String) -> Unit,
    private val onHostBridgeCall: (name: String, payloadJson: String) -> String
) {
    companion object {
        private const val DEFAULT_PAGE_SCRIPT_BUDGET_MS: Long = 8_000L
        private const val PROGRESSIVE_FRAME_INTERVAL_MS: Long = 150L
    }

    val engine: WebAlienDocumentEngine = WebAlienDocumentEngine(
        initialWidth = initialWidth,
        initialHeight = initialHeight,
        capabilities = capabilities,
        allowFileAccess = allowFileAccess
    ).apply {
        scriptingEnabled = enableJavaScript
    }
    private val global: Global = Global(RuntimeCompiler()).apply { initStandards() }
    private val scheduler: NjsEventScheduler = NjsEventScheduler()
    private val events: NjsDomEventSystem = NjsDomEventSystem(global, scheduler)
    private val windowObj: GenericObject = global
    private val registry: NjsDomNodeRegistry
    private val customElements: NjsCustomElementsRegistry
    private val cssomAndGeometry: NjsCssomAndGeometryBridge
    private val canvas2d: NjsCanvas2DBridge
    private val webgl: NjsWebGlBridge
    private val mediaAndAudio: NjsMediaAndAudioBridge
    private val bom: NjsBomApisBridge
    private val networkAndBridge: NjsNetworkAndHostBridge

    var onFaviconChanged: ((String?) -> Unit)? = null
    var onProgressiveFrame: ((List<AlienDirtyTile>) -> Unit)? = null
    var currentFaviconUrl: String? = null
        private set
    private var faviconCheckNeeded: Boolean = false
    private var lastLoadedUrl: String? = null
    private var lastLoadedHtml: String? = null
    private var lastLoadedBaseUrl: String? = null
    private var lastWasHtml: Boolean = false
    private val stopRequested = AtomicBoolean(false)
    private var lastTickMark = TimeSource.Monotonic.markNow()

    private val pointerButtonsDown = HashSet<Int>()
    private var hoveredElement: WebDomElement? = null
    private var activeElementForPointer: WebDomElement? = null
    private var lastDownElement: WebDomElement? = null
    private var isWindowFocused: Boolean = true
    private val registeredHostFunctions = LinkedHashSet<String>()

    init {
        engine.resourceLoader.isAbortedProvider = { stopRequested.load() }
        engine.onDiagnosticMessage = { level, msg ->
            onConsoleMessage(level, msg, engine.document.url, 0)
        }
        scheduler.onError = { t ->
            onJavaScriptError(formatJsThrowable(t))
        }

        NjsEs6Polyfills.install(global, scheduler)
        events.windowObject = windowObj
        events.attachEventTargetMethods(windowObj, windowObj)
        events.installConstructors()

        for (alias in listOf("window", "self", "globalThis", "top", "parent")) {
            windowObj.setHidden(alias, windowObj)
        }
        for (h in listOf("onload", "onresize", "onhashchange", "onpopstate", "onmessage")) {
            windowObj.setHidden(h, Null)
        }

        registry = NjsDomNodeRegistry(global, engine, events).also {
            it.installConstructors()
        }
        customElements = NjsCustomElementsRegistry(global, engine, registry, windowObj) { t ->
            onJavaScriptError(formatJsThrowable(t))
        }.also {
            registry.customElementsRegistry = it
            it.install()
        }
        cssomAndGeometry = NjsCssomAndGeometryBridge(global, engine, registry, events, windowObj).also {
            it.install()
        }
        canvas2d = NjsCanvas2DBridge(global, engine, registry).also {
            it.installConstructors()
        }
        webgl = NjsWebGlBridge(global, engine, registry).also {
            it.installConstructors()
        }
        registry.canvasContextFactory = { canvasEl, ctxId, attrs ->
            when (ctxId.lowercase()) {
                "2d" -> if (capabilities.canvas2d) canvas2d.getOrCreateContext2D(canvasEl) else Null
                "webgl", "experimental-webgl" -> if (capabilities.webgl) webgl.getOrCreateWebGlContext(canvasEl, 1, attrs) else Null
                "webgl2" -> if (capabilities.webgl) webgl.getOrCreateWebGlContext(canvasEl, 2, attrs) else Null
                else -> Null
            }
        }
        mediaAndAudio = NjsMediaAndAudioBridge(global, engine, registry, events, scheduler).also {
            it.install()
        }
        bom = NjsBomApisBridge(
            jsGlobal = global,
            engine = engine,
            scheduler = scheduler,
            events = events,
            registry = registry,
            windowObj = windowObj,
            userAgentOverride = userAgentOverride,
            onNavigateRequest = { url -> loadUrl(url) },
            onReloadRequest = { reload() },
            onConsoleLog = { lvl, msg -> onConsoleMessage(lvl, msg, engine.document.url, 0) }
        ).also {
            it.install()
        }
        networkAndBridge = NjsNetworkAndHostBridge(
            jsGlobal = global,
            engine = engine,
            scheduler = scheduler,
            events = events,
            registry = registry,
            bomBridge = bom,
            windowObj = windowObj,
            onConsoleLog = { lvl, msg -> onConsoleMessage(lvl, msg, engine.document.url, 0) },
            onPostMessageToHost = onWebMessageToHost,
            onHostBridgeCall = onHostBridgeCall
        ).also {
            it.install()
        }

        loadHtmlDocument("<html><head></head><body></body></html>", "about:blank", recordHistory = false, emitFinished = false)
    }

    val currentUrl: String get() = engine.document.url
    val currentTitle: String get() = engine.document.title
    val canGoBack: Boolean get() = bom.canGoBack()
    val canGoForward: Boolean get() = bom.canGoForward()
    val viewportWidth: Int get() = engine.session.viewportWidth
    val viewportHeight: Int get() = engine.session.viewportHeight
    val frameSequence: Long get() = engine.frameSequence

    fun registerHostBridgeName(name: String) {
        if (name.isNotEmpty()) registeredHostFunctions.add(name)
    }

    fun unregisterHostBridgeName(name: String) {
        registeredHostFunctions.remove(name)
    }

    fun loadUrl(rawUrl: String): List<AlienDirtyTile> {
        val target = try {
            engine.resourceLoader.resolveUrl(rawUrl, engine.document.url)
        } catch (t: Throwable) {
            onJavaScriptError("Invalid URL '$rawUrl': ${t.message ?: t::class.simpleName}")
            return buildAndLoadFallbackErrorPage(rawUrl.ifBlank { "about:blank" }, "Page Load Error", t.message ?: "Malformed URL")
        }
        lastWasHtml = false
        lastLoadedUrl = target
        stopRequested.store(false)

        val res = try {
            engine.resourceLoader.loadSync(target, engine.document.url)
        } catch (t: Throwable) {
            val detail = t.message ?: t::class.simpleName ?: "Network Error"
            onConsoleMessage("error", "Navigation error for '$target': $detail", target, 0)
            return buildAndLoadFallbackErrorPage(target, "Page Load Error", detail)
        }
        val finalUrl = res.url.ifBlank { target }
        lastLoadedUrl = finalUrl
        if (!res.ok) {
            val detail = "HTTP ${res.status} ${res.statusText}".trim()
            onConsoleMessage("error", "HTTP navigation failure for '$finalUrl': $detail", finalUrl, 0)
            return buildAndLoadFallbackErrorPage(finalUrl, "Page Load Error", detail)
        }
        val html = res.decodeUtf8Text()
        return try {
            loadHtmlDocument(html, finalUrl, recordHistory = true, emitFinished = true)
        } catch (t: Throwable) {
            val detail = t.message ?: t::class.simpleName ?: "Document Render Error"
            onJavaScriptError("Failed rendering '$finalUrl': $detail")
            buildAndLoadFallbackErrorPage(finalUrl, "Page Load Error", detail)
        }
    }

    private fun buildAndLoadFallbackErrorPage(targetUrl: String, heading: String, detail: String): List<AlienDirtyTile> {
        val safeUrl = escapeHtml(targetUrl)
        val safeHeading = escapeHtml(heading)
        val safeDetail = escapeHtml(detail)
        val fallbackHtml = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <title>Page Load Error</title>
              <style>
                html, body { margin: 0; padding: 0; background-color: #0f172a; color: #f8fafc; font-family: sans-serif; }
                .error-card { max-width: 680px; margin: 48px auto; padding: 28px; background-color: #1e293b; border: 1px solid #334155; border-radius: 10px; }
                .error-title { margin: 0 0 12px 0; font-size: 24px; color: #f87171; }
                .error-url { margin: 0 0 12px 0; font-size: 14px; color: #93c5fd; }
                .error-detail { margin: 0 0 20px 0; font-size: 14px; color: #cbd5e1; }
                .retry-btn { display: inline-block; padding: 10px 20px; background-color: #2563eb; color: #ffffff; border: 1px solid #3b82f6; border-radius: 6px; font-size: 14px; cursor: pointer; }
              </style>
            </head>
            <body>
              <div class="error-card">
                <h1 class="error-title">$safeHeading</h1>
                <p class="error-url">$safeUrl</p>
                <p class="error-detail">$safeDetail</p>
                <button id="gui-retry-btn" class="retry-btn" type="button" data-gui-retry="1">Retry</button>
              </div>
              <script>
                (function() {
                  var btn = document.getElementById('gui-retry-btn');
                  if (btn) {
                    btn.addEventListener('click', function() { location.reload(); });
                  }
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
        return loadHtmlDocument(fallbackHtml, targetUrl, recordHistory = true, emitFinished = true)
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    fun loadHtml(html: String, baseUrl: String?): List<AlienDirtyTile> {
        lastWasHtml = true
        lastLoadedHtml = html
        lastLoadedBaseUrl = baseUrl
        stopRequested.store(false)
        val effectiveUrl = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: "about:blank"
        return try {
            loadHtmlDocument(html, effectiveUrl, recordHistory = true, emitFinished = true)
        } catch (t: Throwable) {
            val detail = t.message ?: t::class.simpleName ?: "HTML Render Error"
            onJavaScriptError("Failed rendering HTML document: $detail")
            buildAndLoadFallbackErrorPage(effectiveUrl, "Render Error", detail)
        }
    }

    fun reload(): List<AlienDirtyTile> {
        return if (lastWasHtml && lastLoadedHtml != null) {
            loadHtmlDocument(lastLoadedHtml!!, lastLoadedBaseUrl ?: engine.document.url, recordHistory = false, emitFinished = true)
        } else if (!lastLoadedUrl.isNullOrEmpty()) {
            loadUrl(lastLoadedUrl!!)
        } else {
            renderDirtyTiles()
        }
    }

    fun stopLoading() {
        stopRequested.store(true)
    }

    fun goBack(): List<AlienDirtyTile> {
        val nextUrl = bom.historyGo(-1) ?: return emptyList()
        if (nextUrl.substringBefore('#') == engine.document.url.substringBefore('#')) {
            return pumpAndRenderDirtyTiles(0f)
        }
        return loadUrl(nextUrl)
    }

    fun goForward(): List<AlienDirtyTile> {
        val nextUrl = bom.historyGo(1) ?: return emptyList()
        if (nextUrl.substringBefore('#') == engine.document.url.substringBefore('#')) {
            return pumpAndRenderDirtyTiles(0f)
        }
        return loadUrl(nextUrl)
    }

    fun evaluateJavaScript(script: String): Pair<Boolean, String?> {
        if (!enableJavaScript) return false to null
        val result = try {
            val code = NjsEs6Preprocessor.preprocess(script)
            val s = Scope(global)
            global.compiler?.compile(code, engine.document.url, false)?.exec(global, s) ?: Undefined
        } catch (t: Throwable) {
            throw IllegalStateException(formatJsThrowable(t), t)
        }
        pumpCycle(consumeElapsedMs(), pumpIterations = 2)
        return serializeJsEvalResult(result)
    }

    fun callGuestCommunicationFunction(name: String, payloadJson: String): Pair<Boolean, String?> {
        if (!enableJavaScript) return false to null
        val (found, out) = networkAndBridge.invokeGuestCommunicationFunction(name, payloadJson)
        pumpCycle(consumeElapsedMs(), pumpIterations = 2)
        return if (found) true to out else false to null
    }

    fun deliverHostMessage(message: String) {
        if (!enableJavaScript) return
        networkAndBridge.deliverHostMessageToGuest(message)
        pumpCycle(consumeElapsedMs(), pumpIterations = 2)
    }

    fun resizeViewport(width: Int, height: Int): List<AlienDirtyTile> {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        engine.resizeViewport(w, h)
        events.dispatchSimpleEvent(windowObj, "resize", bubbles = false, cancelable = false)
        return pumpAndRenderDirtyTiles(0f)
    }

    fun handlePointerEvent(kind: String, x: Float, y: Float, button: Int): List<AlienDirtyTile> {
        val px = x.toDouble()
        val py = y.toDouble()
        val hit = engine.hitTestElement(px, py)
        when (kind.lowercase()) {
            "move", "mousemove", "pointermove" -> {
                if (hit !== hoveredElement) {
                    hoveredElement?.isHoveredState = false
                    hoveredElement?.let { prev ->
                        events.dispatchPointerOrMouse(prev, "pointerout", px, py, button, bubbles = true, cancelable = true)
                        events.dispatchPointerOrMouse(prev, "mouseout", px, py, button, bubbles = true, cancelable = true)
                    }
                    hoveredElement = hit
                    hit?.isHoveredState = true
                    hit?.let { cur ->
                        events.dispatchPointerOrMouse(cur, "pointerover", px, py, button, bubbles = true, cancelable = true)
                        events.dispatchPointerOrMouse(cur, "mouseover", px, py, button, bubbles = true, cancelable = true)
                    }
                }
                val target = hit ?: engine.document.body ?: engine.document.documentElement
                if (target != null) {
                    events.dispatchPointerOrMouse(target, "pointermove", px, py, button, bubbles = true, cancelable = true)
                    events.dispatchPointerOrMouse(target, "mousemove", px, py, button, bubbles = true, cancelable = true)
                }
            }
            "down", "mousedown", "pointerdown" -> {
                pointerButtonsDown.add(button)
                val target = hit ?: engine.document.body ?: engine.document.documentElement
                activeElementForPointer?.isActiveState = false
                activeElementForPointer = hit
                lastDownElement = hit
                hit?.isActiveState = true
                if (hit != null && hit.isFocusable()) {
                    moveFocus(hit)
                } else {
                    moveFocus(null)
                }
                if (target != null) {
                    events.dispatchPointerOrMouse(target, "pointerdown", px, py, button, bubbles = true, cancelable = true)
                    events.dispatchPointerOrMouse(target, "mousedown", px, py, button, bubbles = true, cancelable = true)
                }
            }
            "up", "mouseup", "pointerup" -> {
                pointerButtonsDown.remove(button)
                val target = hit ?: activeElementForPointer ?: engine.document.body ?: engine.document.documentElement
                activeElementForPointer?.isActiveState = false
                if (target != null) {
                    events.dispatchPointerOrMouse(target, "pointerup", px, py, button, bubbles = true, cancelable = true)
                    events.dispatchPointerOrMouse(target, "mouseup", px, py, button, bubbles = true, cancelable = true)
                    if (button == 0 && (hit === lastDownElement || hit == null && activeElementForPointer == null)) {
                        events.dispatchPointerOrMouse(target, "click", px, py, button, bubbles = true, cancelable = true)
                        if (hit?.getAttribute("data-gui-retry") == "1" && !enableJavaScript) {
                            activeElementForPointer = null
                            lastDownElement = null
                            return reload()
                        }
                    } else if (button == 2) {
                        events.dispatchPointerOrMouse(target, "contextmenu", px, py, button, bubbles = true, cancelable = true)
                    }
                }
                activeElementForPointer = null
                lastDownElement = null
            }
            "cancel", "pointercancel" -> {
                pointerButtonsDown.clear()
                activeElementForPointer?.isActiveState = false
                activeElementForPointer = null
                lastDownElement = null
            }
        }
        return pumpAndRenderDirtyTiles(0f)
    }

    fun handleWheelEvent(x: Float, y: Float, deltaX: Float, deltaY: Float): List<AlienDirtyTile> {
        val hit = engine.hitTestElement(x.toDouble(), y.toDouble())
            ?: engine.document.body
            ?: engine.document.documentElement
            ?: return emptyList()
        events.dispatchWheel(hit, deltaX.toDouble(), deltaY.toDouble())

        var cur: WebDomElement? = hit
        while (cur != null) {
            val rect = engine.computeDomRect(cur)
            val curScrollHeight = maxOf(cur.cachedLayoutHeight, (cur.alienNode?.scrollHeight ?: 0).toDouble())
            val curScrollWidth = maxOf(cur.cachedLayoutWidth, (cur.alienNode?.scrollWidth ?: 0).toDouble())
            if (curScrollHeight > rect.height + 0.5 || curScrollWidth > rect.width + 0.5) {
                val maxTop = (curScrollHeight - rect.height).coerceAtLeast(0.0)
                val maxLeft = (curScrollWidth - rect.width).coerceAtLeast(0.0)
                val nextTop = (cur.scrollTopPx + deltaY.toDouble()).coerceIn(0.0, maxTop)
                val nextLeft = (cur.scrollLeftPx + deltaX.toDouble()).coerceIn(0.0, maxLeft)
                if (nextTop != cur.scrollTopPx || nextLeft != cur.scrollLeftPx) {
                    cur.scrollTopPx = nextTop
                    cur.scrollLeftPx = nextLeft
                    cur.alienNode?.scrollTo(nextLeft.toInt(), nextTop.toInt())
                    events.dispatchSimpleEvent(cur, "scroll", bubbles = false, cancelable = false)
                }
                break
            }
            cur = cur.parentNode as? WebDomElement
        }
        return pumpAndRenderDirtyTiles(0f)
    }

    fun handleKeyEvent(
        kind: String,
        key: String,
        code: String,
        keyCode: Int,
        shift: Boolean,
        ctrl: Boolean,
        alt: Boolean,
        meta: Boolean,
        text: String
    ): List<AlienDirtyTile> {
        val target = engine.document.activeElement ?: engine.document.body ?: engine.document.documentElement
            ?: return emptyList()
        val kLower = kind.lowercase()
        val allowed = events.dispatchKeyboard(target, kLower, key, code, keyCode, shift, ctrl, alt, meta)
        if (allowed && kLower == "keydown" && !ctrl && !alt && !meta) {
            applyDefaultKeyEditToElement(target, key, text)
        }
        return pumpAndRenderDirtyTiles(0f)
    }

    fun handleFocusEvent(focused: Boolean): List<AlienDirtyTile> {
        isWindowFocused = focused
        if (!focused) {
            events.dispatchFocus(windowObj, "blur")
        } else {
            events.dispatchFocus(windowObj, "focus")
        }
        return pumpAndRenderDirtyTiles(0f)
    }

    fun needsPeriodicTick(): Boolean =
        scheduler.hasPendingAnimationFrames() ||
            scheduler.hasImminentTimers(2000.0) ||
            cssomAndGeometry.hasActiveAnimations() ||
            engine.hasActiveAlienAnimations() ||
            networkAndBridge.hasActiveWebSockets() ||
            networkAndBridge.hasPendingAsyncCallbacks() ||
            engine.hasPlayingMedia() ||
            engine.audioEngines.isNotEmpty()

    private fun consumeElapsedMs(): Float {
        val measured = lastTickMark.elapsedNow().inWholeMicroseconds / 1000f
        lastTickMark = TimeSource.Monotonic.markNow()
        return measured.coerceIn(0f, 5_000f)
    }

    fun stepPeriodicTick(explicitDeltaMs: Float? = null): List<AlienDirtyTile> {
        val dt = explicitDeltaMs ?: consumeElapsedMs().coerceAtLeast(1f)
        return pumpAndRenderDirtyTiles(dt)
    }

    fun renderDirtyTiles(): List<AlienDirtyTile> {
        engine.ensureLayoutSynced()
        return engine.renderAndConsumeDirtyTiles(fullFrame = false)
    }

    fun dispose() {
        scheduler.clearAll()
        bom.clearTransientState()
        networkAndBridge.dispose()
        events.clearAll()
        engine.dispose()
    }

    private fun loadHtmlDocument(
        html: String,
        url: String,
        recordHistory: Boolean,
        emitFinished: Boolean
    ): List<AlienDirtyTile> {
        scheduler.clearAll()
        events.clearAll()
        networkAndBridge.clearTransientState()
        registry.clearCache()
        customElements.clearForNewDocument()
        cssomAndGeometry.clearTransientState()
        hoveredElement = null
        activeElementForPointer = null
        lastDownElement = null
        pointerButtonsDown.clear()
        faviconCheckNeeded = false

        val parsedDoc = WebHtml5Parser.parseDocument(html, url, allowStorage = capabilities.storage)
        parsedDoc.readyState = "loading"
        engine.replaceDocument(parsedDoc)
        parsedDoc.onElementConnected = { el ->
            customElements.onElementConnected(el)
            val tag = el.lowerTagName
            if (tag == "link" || tag == "base" || tag == "head") {
                faviconCheckNeeded = true
            }
        }
        parsedDoc.onElementDisconnected = { el ->
            customElements.onElementDisconnected(el)
            val tag = el.lowerTagName
            if (tag == "link" || tag == "base") {
                faviconCheckNeeded = true
            }
        }
        parsedDoc.onAttributeChanged = { el, attrName, oldVal, newVal ->
            customElements.onAttributeChanged(el, attrName, oldVal, newVal)
            val tag = el.lowerTagName
            if ((tag == "link" || tag == "base") && (attrName == "href" || attrName == "rel")) {
                faviconCheckNeeded = true
            }
        }
        cssomAndGeometry.resetStylesheets()
        bom.syncForNewDocument(url, resetHistory = recordHistory && historyEmptyAfterResetCheck(url))

        val docJs = registry.wrapNode(parsedDoc)
        windowObj.setHidden("document", docJs)

        refreshFaviconIfChanged(forceNotify = true)

        engine.resourceLoader.beginSubresourceSession()
        var abortedOrStopped = false
        try {
            // Bind DOM geometry/cssom updates first so layout queries work during script startup
            try {
                engine.ensureLayoutSynced()
            } catch (t: Throwable) {
                onConsoleMessage("warn", "Initial layout sync fallback: ${t.message}", url, 0)
            }

            val scripts = if (enableJavaScript) collectDocumentScripts(parsedDoc) else emptyList()
            if (scripts.isNotEmpty() && !stopRequested.load()) {
                val initDirty = try {
                    engine.renderAndConsumeDirtyTiles(fullFrame = true)
                } catch (_: Throwable) {
                    emptyList()
                }
                if (initDirty.isNotEmpty()) {
                    onProgressiveFrame?.invoke(initDirty)
                }
            }

            if (enableJavaScript && !stopRequested.load()) {
                val scriptBudgetStart = TimeSource.Monotonic.markNow()
                var lastProgressiveMark = TimeSource.Monotonic.markNow()
                for (sc in scripts) {
                    if (stopRequested.load()) {
                        abortedOrStopped = true
                        break
                    }
                    if (scriptBudgetStart.elapsedNow().inWholeMilliseconds >= DEFAULT_PAGE_SCRIPT_BUDGET_MS) {
                        onConsoleMessage(
                            "warn",
                            "Page script budget (${DEFAULT_PAGE_SCRIPT_BUDGET_MS}ms) reached for '$url'; skipping remaining scripts",
                            url,
                            0
                        )
                        break
                    }
                    executeScriptElement(sc)
                    if (lastProgressiveMark.elapsedNow().inWholeMilliseconds >= PROGRESSIVE_FRAME_INTERVAL_MS && !stopRequested.load()) {
                        lastProgressiveMark = TimeSource.Monotonic.markNow()
                        try {
                            engine.ensureLayoutSynced()
                            val progDirty = engine.renderAndConsumeDirtyTiles(fullFrame = false)
                            if (progDirty.isNotEmpty()) {
                                onProgressiveFrame?.invoke(progDirty)
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            }

            // Dynamic scripts appended programmatically after parse run eagerly on insertion
            parsedDoc.onDynamicScriptInserted = { dynScript ->
                if (enableJavaScript && !stopRequested.load()) {
                    executeScriptElement(dynScript)
                }
            }

            parsedDoc.readyState = "interactive"
            if (!stopRequested.load()) {
                try {
                    events.dispatchSimpleEvent(parsedDoc, "readystatechange", bubbles = false, cancelable = false)
                    events.dispatchSimpleEvent(parsedDoc, "DOMContentLoaded", bubbles = true, cancelable = false)
                } catch (t: Throwable) {
                    onJavaScriptError(formatJsThrowable(t))
                }
            }

            // Load iframes & synchronous initial images/styles
            if (!stopRequested.load()) {
                networkAndBridge.loadOutstandingIframes()
                scheduler.drainMicrotasks()
                bom.evaluateObservers()
                scheduler.drainMicrotasks()
            }

            parsedDoc.readyState = "complete"
            if (!stopRequested.load()) {
                try {
                    events.dispatchSimpleEvent(parsedDoc, "readystatechange", bubbles = false, cancelable = false)
                    events.dispatchSimpleEvent(windowObj, "load", bubbles = false, cancelable = false)
                } catch (t: Throwable) {
                    onJavaScriptError(formatJsThrowable(t))
                }
            }
            lastTickMark = TimeSource.Monotonic.markNow()

            // Pump any immediate setTimeout(..., 0) & Promise/rAF chains triggered by onload
            if (!stopRequested.load()) {
                pumpCycle(0f, pumpIterations = 4)
            }
        } finally {
            engine.resourceLoader.endSubresourceSession()
        }
        refreshFaviconIfChanged(forceNotify = false)
        if (abortedOrStopped || stopRequested.load()) {
            if (emitFinished) {
                onPageFinished(parsedDoc.url, bom.canGoBack(), bom.canGoForward())
            }
            return emptyList()
        }
        engine.ensureLayoutSynced()
        val dirty = engine.renderAndConsumeDirtyTiles(fullFrame = true)

        if (emitFinished) {
            onPageFinished(parsedDoc.url, bom.canGoBack(), bom.canGoForward())
        }
        return dirty
    }

    private fun historyEmptyAfterResetCheck(url: String): Boolean {
        return !lastWasHtml && url != "about:blank"
    }

    private fun collectDocumentScripts(doc: WebDomDocument): List<WebDomElement> {
        val out = ArrayList<WebDomElement>()
        fun visit(el: WebDomElement) {
            val tag = el.lowerTagName
            if (tag == "template" || (enableJavaScript && tag == "noscript")) {
                return
            }
            if (tag == "script") {
                out.add(el)
            } else {
                for (c in el.children) visit(c)
            }
        }
        doc.documentElement?.let { visit(it) }
        return out
    }

    private fun executeScriptElement(scriptEl: WebDomElement) {
        val typeAttr = scriptEl.getAttribute("type")?.trim()?.lowercase().orEmpty()
        if (typeAttr.isNotEmpty() &&
            typeAttr != "text/javascript" &&
            typeAttr != "application/javascript" &&
            typeAttr != "application/ecmascript" &&
            typeAttr != "text/ecmascript"
        ) {
            return
        }
        val baseUrl = engine.resolveDocumentBaseUrl()
        val srcAttr = scriptEl.getAttribute("src")?.trim()
        val rawCode: String
        val sourceId: String
        if (!srcAttr.isNullOrEmpty()) {
            val res = try {
                engine.resourceLoader.loadSubresourceSync(srcAttr, baseUrl)
            } catch (t: Throwable) {
                onConsoleMessage("error", "Failed loading script '$srcAttr': ${t.message}", engine.document.url, 0)
                scheduler.queueMicrotask { events.dispatchSimpleEvent(scriptEl, "error", bubbles = false, cancelable = false) }
                return
            }
            if (!res.ok) {
                onConsoleMessage("warn", "Script '$srcAttr' returned status ${res.status} (${res.statusText})", engine.document.url, 0)
                scheduler.queueMicrotask { events.dispatchSimpleEvent(scriptEl, "error", bubbles = false, cancelable = false) }
                return
            }
            rawCode = res.decodeUtf8Text()
            sourceId = res.url
        } else {
            rawCode = scriptEl.textContent
            sourceId = engine.document.url
        }

        if (rawCode.isBlank() || stopRequested.load()) return
        val prevCurrentScript = engine.document.currentScript
        engine.document.currentScript = scriptEl
        try {
            val preprocessed = NjsEs6Preprocessor.preprocess(rawCode)
            if (stopRequested.load()) return
            val s = Scope(global)
            val compiled = global.compiler?.compile(preprocessed, sourceId, false)
            if (stopRequested.load()) return
            compiled?.exec(global, s)
            if (!stopRequested.load()) {
                scheduler.drainMicrotasks()
            }
        } catch (t: Throwable) {
            onJavaScriptError(formatJsThrowable(t))
        } finally {
            engine.document.currentScript = prevCurrentScript
        }
        if (!srcAttr.isNullOrEmpty() && !stopRequested.load()) {
            try {
                events.dispatchSimpleEvent(scriptEl, "load", bubbles = false, cancelable = false)
            } catch (t: Throwable) {
                onJavaScriptError(formatJsThrowable(t))
            }
        }
    }

    private fun pumpAndRenderDirtyTiles(deltaMs: Float): List<AlienDirtyTile> {
        pumpCycle(deltaMs, pumpIterations = 3)
        refreshFaviconIfChanged(forceNotify = false)
        engine.ensureLayoutSynced()
        return engine.renderAndConsumeDirtyTiles(fullFrame = false)
    }

    private fun refreshFaviconIfChanged(forceNotify: Boolean = false) {
        val next = engine.extractFaviconUrl(engine.document.url)
        faviconCheckNeeded = false
        if (forceNotify || next != currentFaviconUrl) {
            currentFaviconUrl = next
            onFaviconChanged?.invoke(next)
        }
    }

    private fun pumpCycle(deltaMs: Float, pumpIterations: Int) {
        if (deltaMs > 0f) {
            engine.stepAlienAnimationsAndLayout(deltaMs)
            engine.stepActiveMediaPlayback(deltaMs)
            cssomAndGeometry.stepTransitionsAndKeyframes(deltaMs)
        }
        for (i in 0 until pumpIterations) {
            networkAndBridge.pollPendingAsyncNetworkCallbacks()
            scheduler.drainMicrotasks()
            engine.ensureLayoutSynced()
            bom.evaluateObservers()
            scheduler.flushReadyTimersAndPump(maxTimers = 64)
            networkAndBridge.loadOutstandingIframes()
        }
        scheduler.drainMicrotasks()
        if (faviconCheckNeeded) {
            refreshFaviconIfChanged(forceNotify = false)
        }
    }

    private fun moveFocus(next: WebDomElement?) {
        val prev = engine.document.activeElement
        if (prev === next) return
        if (prev != null) {
            prev.isFocusedState = false
            events.dispatchFocus(prev, "blur", relatedTarget = next)
            if (prev.lowerTagName == "input" || prev.lowerTagName == "textarea" || prev.lowerTagName == "select") {
                events.dispatchSimpleEvent(prev, "change", bubbles = true, cancelable = false)
            }
        }
        engine.document.activeElement = next
        if (next != null) {
            next.isFocusedState = true
            events.dispatchFocus(next, "focus", relatedTarget = prev)
        }
    }

    private fun applyDefaultKeyEditToElement(el: WebDomElement, key: String, text: String) {
        val tag = el.lowerTagName
        if (tag != "input" && tag != "textarea") return
        if (el.hasAttribute("disabled") || el.hasAttribute("readonly")) return
        if (tag == "input") {
            val t = (el.getAttribute("type") ?: "text").trim().lowercase()
            if (t == "checkbox" || t == "radio" || t == "button" || t == "submit" || t == "reset") return
        }
        val cur = el.value
        val updated = when (key) {
            "Backspace" -> if (cur.isNotEmpty()) cur.dropLast(1) else cur
            "Enter" -> if (tag == "textarea") "$cur\n" else cur
            else -> {
                val chText = if (text.isNotEmpty()) text else if (key.length == 1) key else ""
                if (chText.isNotEmpty()) cur + chText else cur
            }
        }
        if (updated != cur) {
            el.value = updated
            events.dispatchInput(el, updated)
        }
    }

    private fun serializeJsEvalResult(value: BaseObject): Pair<Boolean, String?> {
        return when {
            value === Undefined -> false to null
            value === Null -> true to "null"
            value is JSString.Instance -> true to value.string
            value is JSBoolean.Instance -> true to value.value.toString()
            value is JSNumber.Instance -> {
                val d = value.value
                val str = when {
                    d.isNaN() -> "NaN"
                    d.isInfinite() -> if (d > 0) "Infinity" else "-Infinity"
                    d == floor(d) && d in -9007199254740991.0..9007199254740991.0 -> d.toLong().toString()
                    else -> d.toString()
                }
                true to str
            }
            value is GenericObject -> {
                val jsonObj = global.get("JSON", Scopable.OR_NULL)
                val stringify = jsonObj?.get("stringify", Scopable.OR_NULL) as? BaseFunction
                val serialized = try {
                    stringify?.call(jsonObj, value)?.takeIf { it !== Undefined }?.toString()
                } catch (t: Throwable) {
                    onConsoleMessage("warn", "JSON serialization fallback (${t.message ?: t::class.simpleName})", engine.document.url, 0)
                    null
                }
                true to (serialized ?: value.toString())
            }
            else -> true to value.toString()
        }
    }

    private fun formatJsThrowable(t: Throwable): String {
        if (t is JSError.Thrown) {
            val obj = t.what
            val msg = obj.get("message", Scopable.OR_NULL)?.takeIf { it !== Undefined && it !== Null }?.toString()
            val name = obj.get("name", Scopable.OR_NULL)?.takeIf { it !== Undefined && it !== Null }?.toString()
            return if (!name.isNullOrEmpty() && !msg.isNullOrEmpty()) "$name: $msg" else obj.toString()
        }
        if (t is JSError.JavaException) {
            return t.message
        }
        return t.message ?: t::class.simpleName ?: "JavaScriptError"
    }
}
