package com.nexustools.genericui.ext.webview.desktop.njs

import com.nexustools.genericui.ext.webview.desktop.dom.WebCssStyleSheetModel
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomDocument
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomElement
import com.nexustools.genericui.ext.webview.desktop.engine.WebAlienDocumentEngine
import com.nexustools.njs.runtime.AbstractFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.GenericArray
import com.nexustools.njs.runtime.GenericObject
import com.nexustools.njs.runtime.Global
import com.nexustools.njs.runtime.JSBoolean
import com.nexustools.njs.runtime.JSNumber
import com.nexustools.njs.runtime.JSString
import com.nexustools.njs.runtime.Null
import com.nexustools.njs.runtime.Or
import com.nexustools.njs.runtime.Scopable
import com.nexustools.njs.runtime.Undefined
import kotlin.math.roundToInt

/**
 * Installs CSSOM (`window.getComputedStyle`, `document.styleSheets`, `CSSStyleSheet.insertRule/deleteRule`,
 * `matchMedia`), Geometry (`getBoundingClientRect`, `getClientRects`, `offset*`, `client*`, `scroll*`,
 * `scrollIntoView`), and CSS transition / `@keyframes` interpolation + `transitionend` dispatching.
 */
class NjsCssomAndGeometryBridge(
    private val jsGlobal: Global,
    private val engine: WebAlienDocumentEngine,
    private val registry: NjsDomNodeRegistry,
    private val events: NjsDomEventSystem,
    private val windowObj: GenericObject
) {
    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val NUMBER_UNIT_REGEX = Regex("^(-?\\d+(?:\\.\\d+)?)(px|em|rem|%|deg)?$", RegexOption.IGNORE_CASE)
        private val DURATION_TOKEN_REGEX = Regex("([0-9.]+m?s)", RegexOption.IGNORE_CASE)
    }

    private data class ActiveTransition(
        val element: WebDomElement,
        val property: String,
        val durationMs: Float,
        var elapsedMs: Float = 0f
    )

    private data class ActiveAnimation(
        val key: String,
        val name: String,
        val durationMs: Float,
        val fillMode: String,
        val iterationCount: Float,
        var elapsedMs: Float = 0f
    )

    private val sheetWrappers = LinkedHashMap<WebCssStyleSheetModel, GenericObject>()
    private val activeTransitions = ArrayList<ActiveTransition>()
    private val activeAnimations = LinkedHashMap<WebDomElement, ActiveAnimation>()
    private val completedAnimations = LinkedHashMap<WebDomElement, String>()

    fun hasActiveAnimations(): Boolean = activeTransitions.isNotEmpty() || activeAnimations.isNotEmpty()

    fun install() {
        registry.geometryBindHook = { el, js -> attachGeometryProperties(el, js) }
        registry.documentCssomBindHook = { doc, docJs -> attachDocumentStyleSheets(doc, docJs) }

        windowObj.setHidden("getComputedStyle", fn("getComputedStyle") { _, params ->
            val el = registry.unwrapElement(params.getOrNull(0)) ?: return@fn GenericObject(jsGlobal)
            val pseudo = params.getOrNull(1)?.takeIf { it !== Null && it !== Undefined }?.toString()
            buildComputedStyleDeclaration(el, pseudo)
        })

        windowObj.setHidden("matchMedia", fn("matchMedia") { _, params ->
            val query = params.getOrNull(0)?.toString().orEmpty()
            val matches = evaluateMediaQuery(query, engine.session.viewportWidth, engine.session.viewportHeight)
            GenericObject(jsGlobal).apply {
                set("media", jsGlobal.wrap(query))
                set("matches", jsGlobal.wrap(matches))
                setHidden("addEventListener", jsGlobal.NOOP)
                setHidden("removeEventListener", jsGlobal.NOOP)
                setHidden("addListener", jsGlobal.NOOP)
                setHidden("removeListener", jsGlobal.NOOP)
            }
        })
    }

    fun clearTransientState() {
        sheetWrappers.clear()
        activeTransitions.clear()
        activeAnimations.clear()
        completedAnimations.clear()
    }

    fun resetStylesheets() {
        sheetWrappers.clear()
        activeTransitions.clear()
        activeAnimations.clear()
        completedAnimations.clear()
    }

    fun stepTransitionsAndKeyframes(deltaMs: Float) {
        val dt = deltaMs.coerceAtLeast(0f)
        if (activeTransitions.isNotEmpty()) {
            val completed = ArrayList<ActiveTransition>()
            for (tr in activeTransitions) {
                tr.elapsedMs += dt
                if (tr.elapsedMs >= tr.durationMs) {
                    completed.add(tr)
                }
            }
            if (completed.isNotEmpty()) {
                activeTransitions.removeAll(completed.toSet())
                for (tr in completed) {
                    events.dispatchSimpleEvent(
                        targetKey = tr.element,
                        type = "transitionend",
                        bubbles = true,
                        cancelable = false,
                        extraProps = { evt ->
                            evt.set("propertyName", jsGlobal.wrap(tr.property))
                            evt.set("elapsedTime", jsGlobal.wrap((tr.durationMs / 1000.0)))
                        }
                    )
                }
            }
        }
        stepCssKeyframeAnimations(dt)
    }

    private fun stepCssKeyframeAnimations(dt: Float) {
        if (!engine.cssResolver.hasAnyKeyframes() && activeAnimations.isEmpty()) return
        val docRoot = engine.document.documentElement ?: return
        var anyDomStyleChanged = false
        val finishedEvents = ArrayList<Pair<WebDomElement, ActiveAnimation>>()

        fun resolveAnimationSpec(el: WebDomElement): ActiveAnimation? {
            val style = engine.cssResolver.resolveComputedMap(el, engine.session.viewportWidth, engine.session.viewportHeight)
            val explicitName = style["animation-name"]?.trim()?.takeIf { it.isNotEmpty() && !it.equals("none", ignoreCase = true) }
            val shorthand = style["animation"]?.trim()?.takeIf { it.isNotEmpty() && !it.equals("none", ignoreCase = true) }
            val animName = explicitName ?: shorthand?.let { extractKeyframeNameFromShorthand(it) } ?: return null
            if (engine.cssResolver.getKeyframeStops(animName) == null) return null

            val durRaw = style["animation-duration"] ?: shorthand?.let { extractDurationToken(it) } ?: "0s"
            val durMs = (parseDurationMs(durRaw) ?: 0f).coerceAtLeast(0f)
            val fill = (style["animation-fill-mode"] ?: shorthand?.let { extractFillModeToken(it) } ?: "none").trim().lowercase()
            val iterRaw = style["animation-iteration-count"]?.trim()?.lowercase()
            val iter = when {
                iterRaw == "infinite" || (explicitName == null && shorthand?.lowercase()?.contains("infinite") == true) -> Float.POSITIVE_INFINITY
                iterRaw != null -> iterRaw.toFloatOrNull()?.coerceAtLeast(0f) ?: 1f
                else -> 1f
            }
            return ActiveAnimation(
                key = "$animName|$durMs|$fill|$iter",
                name = animName,
                durationMs = durMs,
                fillMode = fill,
                iterationCount = iter
            )
        }

        val visited = HashSet<WebDomElement>()
        fun walk(node: com.nexustools.genericui.ext.webview.desktop.dom.WebDomNode) {
            if (node is WebDomElement) {
                visited.add(node)
                val spec = resolveAnimationSpec(node)
                if (spec == null) {
                    if (activeAnimations.remove(node) != null || completedAnimations.remove(node) != null || node.animatedStyleOverrides.isNotEmpty()) {
                        node.animatedStyleOverrides.clear()
                        anyDomStyleChanged = true
                    }
                } else if (completedAnimations[node] != spec.key) {
                    val stops = engine.cssResolver.getKeyframeStops(spec.name).orEmpty().sortedBy { it.offsetFraction }
                    if (stops.isNotEmpty()) {
                        val active = activeAnimations[node]?.takeIf { it.key == spec.key }
                            ?: spec.also { activeAnimations[node] = it }
                        active.elapsedMs += dt
                        val totalDur = if (active.iterationCount.isInfinite()) Float.POSITIVE_INFINITY else active.durationMs * active.iterationCount
                        if (active.durationMs <= 0f || active.elapsedMs >= totalDur) {
                            activeAnimations.remove(node)
                            completedAnimations[node] = active.key
                            if (active.fillMode == "forwards" || active.fillMode == "both") {
                                val finalProps = interpolateKeyframeStops(stops, 1f)
                                if (node.animatedStyleOverrides != finalProps) {
                                    node.animatedStyleOverrides.clear()
                                    node.animatedStyleOverrides.putAll(finalProps)
                                    anyDomStyleChanged = true
                                }
                            } else if (node.animatedStyleOverrides.isNotEmpty()) {
                                node.animatedStyleOverrides.clear()
                                anyDomStyleChanged = true
                            }
                            finishedEvents.add(node to active)
                        } else {
                            val cycleProgress = (active.elapsedMs / active.durationMs) % 1f
                            val props = interpolateKeyframeStops(stops, cycleProgress)
                            if (node.animatedStyleOverrides != props) {
                                node.animatedStyleOverrides.clear()
                                node.animatedStyleOverrides.putAll(props)
                                anyDomStyleChanged = true
                            }
                        }
                    }
                }
            }
            for (c in node.childNodes) walk(c)
        }
        walk(docRoot)

        activeAnimations.keys.toList().filter { it !in visited }.forEach { orphan ->
            activeAnimations.remove(orphan)
            orphan.animatedStyleOverrides.clear()
            anyDomStyleChanged = true
        }
        completedAnimations.keys.toList().filter { it !in visited }.forEach { orphan ->
            completedAnimations.remove(orphan)
        }

        if (anyDomStyleChanged) {
            engine.markStyleAndDomDirty()
            engine.ensureLayoutSynced()
        }
        for ((el, anim) in finishedEvents) {
            events.dispatchSimpleEvent(
                targetKey = el,
                type = "animationend",
                bubbles = true,
                cancelable = false,
                extraProps = { evt ->
                    evt.set("animationName", jsGlobal.wrap(anim.name))
                    evt.set("elapsedTime", jsGlobal.wrap((anim.durationMs / 1000.0)))
                }
            )
        }
    }

    private fun extractKeyframeNameFromShorthand(shorthand: String): String? {
        val ignored = setOf(
            "none", "normal", "reverse", "alternate", "alternate-reverse",
            "forwards", "backwards", "both", "infinite", "running", "paused",
            "linear", "ease", "ease-in", "ease-out", "ease-in-out", "step-start", "step-end"
        )
        val tokens = shorthand.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        for (tok in tokens.reversed()) {
            val lower = tok.lowercase()
            if (lower in ignored) continue
            if (lower.endsWith("ms") || lower.endsWith("s")) {
                val num = lower.removeSuffix("ms").removeSuffix("s").toFloatOrNull()
                if (num != null) continue
            }
            if (lower.startsWith("cubic-bezier(") || lower.startsWith("steps(")) continue
            if (lower.toFloatOrNull() != null) continue
            if (engine.cssResolver.getKeyframeStops(tok) != null) return tok
        }
        return null
    }

    private fun extractFillModeToken(shorthand: String): String? {
        for (tok in shorthand.split(WHITESPACE_REGEX)) {
            when (tok.lowercase()) {
                "forwards", "backwards", "both", "none" -> return tok.lowercase()
            }
        }
        return null
    }

    private fun interpolateKeyframeStops(
        stops: List<com.nexustools.genericui.ext.webview.desktop.engine.WebCssCascadeResolver.KeyframeStop>,
        progress: Float
    ): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val p = progress.coerceIn(0f, 1f)
        if (stops.isEmpty()) return out
        if (stops.size == 1 || p >= stops.last().offsetFraction) {
            out.putAll(stops.last().declarations)
            return out
        }
        if (p <= stops.first().offsetFraction) {
            out.putAll(stops.first().declarations)
            return out
        }
        val allProps = LinkedHashSet<String>()
        for (s in stops) allProps.addAll(s.declarations.keys)
        for (prop in allProps) {
            val propStops = stops.filter { it.declarations.containsKey(prop) }
            if (propStops.isEmpty()) continue
            var prev = propStops.first()
            var next = propStops.last()
            for (i in 0 until propStops.size - 1) {
                if (p >= propStops[i].offsetFraction && p <= propStops[i + 1].offsetFraction) {
                    prev = propStops[i]
                    next = propStops[i + 1]
                    break
                }
            }
            val span = (next.offsetFraction - prev.offsetFraction)
            val localT = if (span <= 1e-6f) 1f else ((p - prev.offsetFraction) / span).coerceIn(0f, 1f)
            val a = prev.declarations[prop].orEmpty()
            val b = next.declarations[prop].orEmpty()
            out[prop] = interpolateCssPropertyValue(a, b, localT)
        }
        return out
    }

    private fun interpolateCssPropertyValue(a: String, b: String, t: Float): String {
        if (t <= 0f) return a
        if (t >= 1f) return b
        val mA = NUMBER_UNIT_REGEX.matchEntire(a.trim())
        val mB = NUMBER_UNIT_REGEX.matchEntire(b.trim())
        if (mA != null && mB != null && mA.groupValues[2].equals(mB.groupValues[2], ignoreCase = true)) {
            val vA = mA.groupValues[1].toDoubleOrNull()
            val vB = mB.groupValues[1].toDoubleOrNull()
            if (vA != null && vB != null) {
                val interp = vA + (vB - vA) * t
                val unit = mA.groupValues[2]
                val formatted = if (unit.isNotEmpty() || interp % 1.0 == 0.0) {
                    ((interp * 100.0).roundToInt() / 100.0).toString().removeSuffix(".0")
                } else {
                    interp.toString()
                }
                return "$formatted$unit"
            }
        }
        val cA = com.nexustools.genericui.style.color.RgbaColor.fromCss(a.trim())
        val cB = com.nexustools.genericui.style.color.RgbaColor.fromCss(b.trim())
        if (cA != null && cB != null) {
            val r = (cA.r + (cB.r - cA.r) * t).roundToInt().coerceIn(0, 255)
            val g = (cA.g + (cB.g - cA.g) * t).roundToInt().coerceIn(0, 255)
            val bl = (cA.b + (cB.b - cA.b) * t).roundToInt().coerceIn(0, 255)
            return "rgb($r, $g, $bl)"
        }
        return if (t < 0.5f) a else b
    }

    fun trackTransitionStartIfConfigured(el: WebDomElement, property: String) {
        val styleMap = engine.getComputedStyleMap(el)
        val transProp = styleMap["transition-property"] ?: styleMap["transition"] ?: return
        val durStr = styleMap["transition-duration"] ?: extractDurationToken(transProp) ?: return
        val durMs = parseDurationMs(durStr) ?: return
        if (durMs <= 0f) return
        val propMatch = transProp.contains("all", ignoreCase = true) || transProp.contains(property, ignoreCase = true)
        if (!propMatch) return
        activeTransitions.add(ActiveTransition(el, property, durMs))
    }

    private fun attachDocumentStyleSheets(doc: WebDomDocument, docJs: GenericObject) {
        docJs.defineProperty("styleSheets", fn("get_styleSheets") { _, _ ->
            engine.ensureLayoutSynced()
            val wrapped = doc.styleSheets.map { wrapStyleSheet(it) as BaseObject? }.toTypedArray()
            GenericArray(jsGlobal, jsGlobal.Array, wrapped)
        }, jsGlobal.NOOP)
    }

    private fun wrapStyleSheet(model: WebCssStyleSheetModel): GenericObject {
        sheetWrappers[model]?.let { return it }
        val js = GenericObject(jsGlobal)
        sheetWrappers[model] = js

        js.defineProperty("href", fn("get_href") { _, _ ->
            model.href?.let { jsGlobal.wrap(it) } ?: Null
        }, jsGlobal.NOOP)
        js.defineProperty("ownerNode", fn("get_ownerNode") { _, _ ->
            registry.wrapNodeOrNull(model.ownerNode)
        }, jsGlobal.NOOP)
        js.defineProperty("disabled", fn("get_disabled") { _, _ -> jsGlobal.wrap(model.disabled) },
            fn("set_disabled") { _, p -> model.disabled = p.getOrNull(0)?.toBool() ?: false; Undefined })

        val rulesGetter = fn("get_cssRules") { _, _ ->
            val items = model.rules.map { ruleTxt ->
                GenericObject(jsGlobal).apply {
                    set("cssText", jsGlobal.wrap(ruleTxt))
                    val selectorPart = ruleTxt.substringBefore('{').trim()
                    set("selectorText", jsGlobal.wrap(selectorPart))
                } as BaseObject?
            }.toTypedArray()
            GenericArray(jsGlobal, jsGlobal.Array, items)
        }
        js.defineProperty("cssRules", rulesGetter, jsGlobal.NOOP)
        js.defineProperty("rules", rulesGetter, jsGlobal.NOOP)

        js.setHidden("insertRule", fn("insertRule") { _, p ->
            val ruleText = p.getOrNull(0)?.toString().orEmpty()
            val idx = p.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: 0
            val inserted = model.insertRule(ruleText, idx)
            engine.markStyleAndDomDirty()
            jsGlobal.wrap(inserted.toDouble())
        })
        js.setHidden("deleteRule", fn("deleteRule") { _, p ->
            val idx = p.getOrNull(0)?.toInt() ?: 0
            model.deleteRule(idx)
            engine.markStyleAndDomDirty()
            Undefined
        })
        return js
    }

    private fun buildComputedStyleDeclaration(el: WebDomElement, pseudo: String?): GenericObject {
        val map = engine.getComputedStyleMap(el, pseudo)
        return object : GenericObject(jsGlobal) {
            init {
                setHidden("getPropertyValue", fn("getPropertyValue") { _, p ->
                    val propName = normalizeCssName(p.getOrNull(0)?.toString().orEmpty())
                    jsGlobal.wrap(map[propName].orEmpty())
                })
                setHidden("getPropertyPriority", fn("getPropertyPriority") { _, _ -> jsGlobal.wrap("") })
            }

            override fun get(key: String, _this: BaseObject, or: Or<out BaseObject?>): BaseObject? {
                super.get(key, _this, Scopable.OR_NULL)?.let { return it }
                val cssKey = normalizeCssName(key)
                return jsGlobal.wrap(map[cssKey].orEmpty())
            }
        }
    }

    private fun attachGeometryProperties(el: WebDomElement, js: GenericObject) {
        js.setHidden("getBoundingClientRect", fn("getBoundingClientRect") { _, _ ->
            val r = engine.computeDomRect(el)
            createDomRectObj(r.x, r.y, r.width, r.height)
        })
        js.setHidden("getClientRects", fn("getClientRects") { _, _ ->
            val r = engine.computeDomRect(el)
            if (r.width <= 0.0 && r.height <= 0.0) {
                GenericArray(jsGlobal)
            } else {
                GenericArray(jsGlobal, jsGlobal.Array, arrayOf(createDomRectObj(r.x, r.y, r.width, r.height)))
            }
        })

        js.defineProperty("offsetWidth", fn("get_offsetWidth") { _, _ ->
            engine.ensureLayoutSynced()
            jsGlobal.wrap(el.cachedLayoutWidth.roundToInt().toDouble())
        }, jsGlobal.NOOP)
        js.defineProperty("offsetHeight", fn("get_offsetHeight") { _, _ ->
            engine.ensureLayoutSynced()
            jsGlobal.wrap(el.cachedLayoutHeight.roundToInt().toDouble())
        }, jsGlobal.NOOP)
        js.defineProperty("offsetLeft", fn("get_offsetLeft") { _, _ ->
            engine.ensureLayoutSynced()
            val pX = el.parentElement?.cachedLayoutX ?: 0.0
            jsGlobal.wrap((el.cachedLayoutX - pX).roundToInt().toDouble())
        }, jsGlobal.NOOP)
        js.defineProperty("offsetTop", fn("get_offsetTop") { _, _ ->
            engine.ensureLayoutSynced()
            val pY = el.parentElement?.cachedLayoutY ?: 0.0
            jsGlobal.wrap((el.cachedLayoutY - pY).roundToInt().toDouble())
        }, jsGlobal.NOOP)
        js.defineProperty("offsetParent", fn("get_offsetParent") { _, _ ->
            engine.ensureLayoutSynced()
            val style = engine.getComputedStyleMap(el)
            if (style["display"] == "none" || style["position"] == "fixed") Null
            else registry.wrapNodeOrNull(el.parentElement ?: el.ownerDocument?.body)
        }, jsGlobal.NOOP)

        js.defineProperty("clientLeft", fn("get_clientLeft") { _, _ ->
            val style = engine.getComputedStyleMap(el)
            val bw = WebCssMathEvaluatorLength(style["border-left-width"])
            jsGlobal.wrap(bw.roundToInt().toDouble())
        }, jsGlobal.NOOP)
        js.defineProperty("clientTop", fn("get_clientTop") { _, _ ->
            val style = engine.getComputedStyleMap(el)
            val bw = WebCssMathEvaluatorLength(style["border-top-width"])
            jsGlobal.wrap(bw.roundToInt().toDouble())
        }, jsGlobal.NOOP)
        js.defineProperty("clientWidth", fn("get_clientWidth") { _, _ ->
            engine.ensureLayoutSynced()
            val style = engine.getComputedStyleMap(el)
            val bl = WebCssMathEvaluatorLength(style["border-left-width"])
            val br = WebCssMathEvaluatorLength(style["border-right-width"])
            jsGlobal.wrap((el.cachedLayoutWidth - bl - br).coerceAtLeast(0.0).roundToInt().toDouble())
        }, jsGlobal.NOOP)
        js.defineProperty("clientHeight", fn("get_clientHeight") { _, _ ->
            engine.ensureLayoutSynced()
            val style = engine.getComputedStyleMap(el)
            val bt = WebCssMathEvaluatorLength(style["border-top-width"])
            val bb = WebCssMathEvaluatorLength(style["border-bottom-width"])
            jsGlobal.wrap((el.cachedLayoutHeight - bt - bb).coerceAtLeast(0.0).roundToInt().toDouble())
        }, jsGlobal.NOOP)

        js.defineProperty("scrollWidth", fn("get_scrollWidth") { _, _ ->
            engine.ensureLayoutSynced()
            val node = el.alienNode
            val sw = maxOf(el.cachedLayoutWidth, (node?.scrollWidth ?: 0).toDouble())
            jsGlobal.wrap(sw.roundToInt().toDouble())
        }, jsGlobal.NOOP)
        js.defineProperty("scrollHeight", fn("get_scrollHeight") { _, _ ->
            engine.ensureLayoutSynced()
            val node = el.alienNode
            val sh = maxOf(el.cachedLayoutHeight, (node?.scrollHeight ?: 0).toDouble())
            jsGlobal.wrap(sh.roundToInt().toDouble())
        }, jsGlobal.NOOP)
        js.defineProperty("scrollLeft", fn("get_scrollLeft") { _, _ -> jsGlobal.wrap(el.scrollLeftPx) },
            fn("set_scrollLeft") { _, p ->
                val v = (p.getOrNull(0)?.toDouble() ?: 0.0).coerceAtLeast(0.0)
                if (el.scrollLeftPx != v) {
                    el.scrollLeftPx = v
                    el.alienNode?.scrollTo(v.roundToInt(), el.scrollTopPx.roundToInt())
                    events.dispatchSimpleEvent(el, "scroll", bubbles = false)
                }
                Undefined
            })
        js.defineProperty("scrollTop", fn("get_scrollTop") { _, _ -> jsGlobal.wrap(el.scrollTopPx) },
            fn("set_scrollTop") { _, p ->
                val v = (p.getOrNull(0)?.toDouble() ?: 0.0).coerceAtLeast(0.0)
                if (el.scrollTopPx != v) {
                    el.scrollTopPx = v
                    el.alienNode?.scrollTo(el.scrollLeftPx.roundToInt(), v.roundToInt())
                    events.dispatchSimpleEvent(el, "scroll", bubbles = false)
                }
                Undefined
            })

        js.setHidden("scrollTo", fn("scrollTo") { _, p ->
            val arg0 = p.getOrNull(0)
            val isOptsDict = arg0 is GenericObject &&
                arg0 !is JSNumber.Instance &&
                arg0 !is JSBoolean.Instance &&
                arg0 !is JSString.Instance
            val x: Double
            val y: Double
            if (isOptsDict) {
                x = arg0.get("left", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toDouble() ?: el.scrollLeftPx
                y = arg0.get("top", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toDouble() ?: el.scrollTopPx
            } else {
                x = arg0?.toDouble() ?: 0.0
                y = p.getOrNull(1)?.toDouble() ?: 0.0
            }
            el.scrollLeftPx = x.coerceAtLeast(0.0)
            el.scrollTopPx = y.coerceAtLeast(0.0)
            el.alienNode?.scrollTo(el.scrollLeftPx.roundToInt(), el.scrollTopPx.roundToInt())
            events.dispatchSimpleEvent(el, "scroll", bubbles = false)
            Undefined
        })
        js.setHidden("scroll", js.get("scrollTo"))
        js.setHidden("scrollBy", fn("scrollBy") { _, p ->
            val arg0 = p.getOrNull(0)
            val isOptsDict = arg0 is GenericObject &&
                arg0 !is JSNumber.Instance &&
                arg0 !is JSBoolean.Instance &&
                arg0 !is JSString.Instance
            val dx: Double
            val dy: Double
            if (isOptsDict) {
                dx = arg0.get("left", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toDouble() ?: 0.0
                dy = arg0.get("top", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toDouble() ?: 0.0
            } else {
                dx = arg0?.toDouble() ?: 0.0
                dy = p.getOrNull(1)?.toDouble() ?: 0.0
            }
            el.scrollLeftPx = (el.scrollLeftPx + dx).coerceAtLeast(0.0)
            el.scrollTopPx = (el.scrollTopPx + dy).coerceAtLeast(0.0)
            el.alienNode?.scrollTo(el.scrollLeftPx.roundToInt(), el.scrollTopPx.roundToInt())
            events.dispatchSimpleEvent(el, "scroll", bubbles = false)
            Undefined
        })
        js.setHidden("scrollIntoView", fn("scrollIntoView") { _, _ ->
            engine.ensureLayoutSynced()
            var scroller: WebDomElement? = el.parentElement
            while (scroller != null) {
                val ancStyle = engine.getComputedStyleMap(scroller)
                val ov = ancStyle["overflow"]?.lowercase().orEmpty()
                val ovy = ancStyle["overflow-y"]?.lowercase().orEmpty()
                val aNode = scroller.alienNode
                val canScroll = ov in setOf("auto", "scroll") ||
                    ovy in setOf("auto", "scroll") ||
                    (aNode != null && aNode.scrollHeight > aNode.bounds.height && scroller.lowerTagName != "body" && scroller.lowerTagName != "html")
                if (canScroll) break
                scroller = scroller.parentElement
            }
            val targetScroller = scroller ?: el.ownerDocument?.scrollingElement ?: el.ownerDocument?.body
            if (targetScroller != null) {
                val sNode = targetScroller.alienNode
                val scrollerTop = sNode?.bounds?.y?.toDouble() ?: targetScroller.cachedLayoutY
                val scrollerH = sNode?.bounds?.height?.toDouble() ?: targetScroller.cachedLayoutHeight
                val maxScrollY = ((sNode?.scrollHeight?.toDouble() ?: scrollerH) - scrollerH).coerceAtLeast(0.0)
                val targetY = (el.cachedLayoutY - scrollerTop + targetScroller.scrollTopPx).coerceIn(0.0, maxScrollY)
                if (targetScroller.scrollTopPx != targetY) {
                    targetScroller.scrollTopPx = targetY
                    sNode?.scrollTo(targetScroller.scrollLeftPx.roundToInt(), targetY.roundToInt())
                    events.dispatchSimpleEvent(targetScroller, "scroll", bubbles = false)
                }
            }
            Undefined
        })
    }

    private fun createDomRectObj(x: Double, y: Double, w: Double, h: Double): GenericObject =
        GenericObject(jsGlobal).apply {
            set("x", jsGlobal.wrap(x))
            set("y", jsGlobal.wrap(y))
            set("width", jsGlobal.wrap(w))
            set("height", jsGlobal.wrap(h))
            set("top", jsGlobal.wrap(y))
            set("left", jsGlobal.wrap(x))
            set("right", jsGlobal.wrap(x + w))
            set("bottom", jsGlobal.wrap(y + h))
            setHidden("toJSON", fn("toJSON") { _this, _ -> _this })
        }

    private fun evaluateMediaQuery(query: String, vw: Int, vh: Int): Boolean {
        val expr = query.trim().lowercase()
        if (expr.isEmpty() || expr == "all" || expr == "screen") return true
        val parts = expr.split(" and ")
        for (part in parts) {
            val clean = part.trim().removePrefix("(").removeSuffix(")").trim()
            if (clean == "screen" || clean == "all") continue
            val colon = clean.indexOf(':')
            if (colon > 0) {
                val feat = clean.substring(0, colon).trim()
                val v = WebCssMathEvaluatorLength(clean.substring(colon + 1).trim())
                when (feat) {
                    "min-width" -> if (vw < v) return false
                    "max-width" -> if (vw > v) return false
                    "min-height" -> if (vh < v) return false
                    "max-height" -> if (vh > v) return false
                }
            }
        }
        return true
    }

    private fun WebCssMathEvaluatorLength(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        val clean = raw.trim().lowercase()
        return clean.removeSuffix("px").toDoubleOrNull() ?: 0.0
    }

    private fun normalizeCssName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("--")) return trimmed
        if ('-' in trimmed) return trimmed.lowercase()
        val sb = StringBuilder(trimmed.length + 4)
        for (ch in trimmed) {
            if (ch.isUpperCase()) sb.append('-').append(ch.lowercaseChar()) else sb.append(ch)
        }
        return sb.toString()
    }

    private fun extractDurationToken(raw: String): String? =
        DURATION_TOKEN_REGEX.find(raw)?.groupValues?.get(1)

    private fun parseDurationMs(raw: String): Float? {
        val t = raw.trim().lowercase()
        return when {
            t.endsWith("ms") -> t.dropLast(2).toFloatOrNull()
            t.endsWith("s") -> t.dropLast(1).toFloatOrNull()?.times(1000f)
            else -> t.toFloatOrNull()
        }
    }

    private fun fn(name: String, body: (BaseObject, Array<out BaseObject>) -> BaseObject): AbstractFunction =
        object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = body(_this, params)
            override fun name(): String = name
        }
}
