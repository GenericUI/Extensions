package com.nexustools.genericui.ext.webview.desktop.engine

import com.nexustools.genericui.ext.alien.AlienPrecompiledBootstrapStyleSheet
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.ext.webview.desktop.dom.WebCssStyleSheetModel
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomDocument
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomElement
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomNode
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomSelectorMatcher
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomShadowRoot
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssStyleSheet
import kotlin.math.roundToInt

/**
 * Compiles document `<style>`, `<link rel="stylesheet">`, `@import`, CSSOM rules, `@media` queries,
 * `@keyframes`, and Shadow DOM scoped styles into Alien [PrecompiledStyleSheet]s and computes W3C
 * cascaded/inherited styles and CSS custom property (`var(--*)`) substitutions for `getComputedStyle`.
 */
class WebCssCascadeResolver(
    private val resourceLoader: WebResourceLoader,
    private val onDiagnostic: ((String, String) -> Unit)? = null
) {
    private data class IndexedStyleRule(
        val selector: String,
        val declarations: Map<String, String>,
        val importantProps: Set<String>,
        val originOrder: Int,
        val scopeId: String? = null
    ) {
        val hasPseudoBefore: Boolean = selector.contains("::before", ignoreCase = true)
        val hasPseudoAfter: Boolean = selector.contains("::after", ignoreCase = true)
        val compiledSelector: WebDomSelectorMatcher.CompiledSelector = WebDomSelectorMatcher.compileSelectorList(
            if (hasPseudoBefore || hasPseudoAfter) {
                selector.replace("::before", "", ignoreCase = true)
                    .replace("::after", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { "*" }
            } else {
                selector
            }
        )
    }

    internal data class KeyframeStop(
        val offsetFraction: Float,
        val declarations: Map<String, String>
    )

    companion object {
        private val UA_CSS = """
            html { display: block; color: #000000; font-size: 16px; }
            body { display: block; margin: 8px; }
            div, section, header, main, footer, nav, article, aside, p, form, fieldset, blockquote, figure, figcaption, dl, dd, dt { display: block; }
            h1 { display: block; font-size: 32px; font-weight: 700; margin-top: 10px; margin-bottom: 10px; }
            h2 { display: block; font-size: 24px; font-weight: 700; margin-top: 8px; margin-bottom: 8px; }
            h3 { display: block; font-size: 18.72px; font-weight: 700; margin-top: 6px; margin-bottom: 6px; }
            h4 { display: block; font-size: 16px; font-weight: 700; }
            h5 { display: block; font-size: 13.28px; font-weight: 700; }
            h6 { display: block; font-size: 10.72px; font-weight: 700; }
            ul, ol { display: block; margin-top: 8px; margin-bottom: 8px; padding-left: 40px; }
            li { display: list-item; }
            pre { display: block; font-family: monospace; white-space: pre; }
            code, kbd, samp, tt { font-family: monospace; }
            span, a, em, strong, b, i, u, s, small, sub, sup, label, abbr, cite, q { display: inline; }
            strong, b { font-weight: 700; }
            em, i { font-style: italic; }
            table { display: table; border-collapse: separate; border-spacing: 2px; }
            thead { display: table-header-group; }
            tbody { display: table-row-group; }
            tfoot { display: table-footer-group; }
            tr { display: table-row; }
            td, th { display: table-cell; padding: 1px; }
            th { font-weight: 700; text-align: center; }
            button, input, select, textarea, img, canvas, svg, video, iframe { display: inline-block; }
            head, meta, link, title, style, script, template, source, track, option { display: none; }
        """.trimIndent()

        private val INHERITED_PROPERTIES = setOf(
            "color", "font-family", "font-size", "font-weight", "font-style",
            "line-height", "text-align", "text-indent", "text-transform", "letter-spacing",
            "word-spacing", "white-space", "visibility", "cursor", "direction",
            "list-style-type", "border-collapse", "border-spacing", "pointer-events"
        )

        private val NAMED_COLORS = mapOf(
            "transparent" to "rgba(0, 0, 0, 0)",
            "black" to "rgb(0, 0, 0)",
            "white" to "rgb(255, 255, 255)",
            "red" to "rgb(255, 0, 0)",
            "lime" to "rgb(0, 255, 0)",
            "green" to "rgb(0, 128, 0)",
            "blue" to "rgb(0, 0, 255)",
            "yellow" to "rgb(255, 255, 0)",
            "cyan" to "rgb(0, 255, 255)",
            "aqua" to "rgb(0, 255, 255)",
            "magenta" to "rgb(255, 0, 255)",
            "fuchsia" to "rgb(255, 0, 255)",
            "orange" to "rgb(255, 165, 0)",
            "purple" to "rgb(128, 0, 128)",
            "rebeccapurple" to "rgb(102, 51, 153)",
            "pink" to "rgb(255, 192, 203)",
            "gray" to "rgb(128, 128, 128)",
            "grey" to "rgb(128, 128, 128)",
            "silver" to "rgb(192, 192, 192)",
            "maroon" to "rgb(128, 0, 0)",
            "navy" to "rgb(0, 0, 128)",
            "teal" to "rgb(0, 128, 128)",
            "olive" to "rgb(128, 128, 0)",
            "gold" to "rgb(255, 215, 0)",
            "coral" to "rgb(255, 127, 80)",
            "crimson" to "rgb(220, 20, 60)",
            "indigo" to "rgb(75, 0, 130)"
        )

        private val URL_IMPORT_REGEX = Regex("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)", RegexOption.IGNORE_CASE)
        private val QUOTED_IMPORT_REGEX = Regex("['\"]([^'\"]+)['\"]")
        private const val MAX_IMPORT_DEPTH: Int = 8
    }

    private val uaCssSheet: CssStyleSheet = CssStyleSheet.parse(UA_CSS)
    private val uaPrecompiled: PrecompiledStyleSheet =
        AlienPrecompiledBootstrapStyleSheet.wrapCssStyleSheet(uaCssSheet, "web-ua")
    private val uaIndexedRules: List<IndexedStyleRule> by lazy {
        ArrayList<IndexedStyleRule>().also { out ->
            extractRulesRecursive(UA_CSS, "", 1024, 768, null, HashSet(), out, 0)
        }
    }

    var scriptingEnabled: Boolean = true

    private var indexedRules: List<IndexedStyleRule> = emptyList()
    private var idRuleBuckets: Map<String, IntArray> = emptyMap()
    private var classRuleBuckets: Map<String, IntArray> = emptyMap()
    private var tagRuleBuckets: Map<String, IntArray> = emptyMap()
    private var universalRuleBucket: IntArray = IntArray(0)
    private var pseudoElementRuleIndices: IntArray = IntArray(0)
    private val keyframesRegistry: LinkedHashMap<String, List<KeyframeStop>> = LinkedHashMap()

    fun rebuildFromDocument(
        doc: WebDomDocument,
        viewportWidth: Int,
        viewportHeight: Int
    ): List<PrecompiledStyleSheet> {
        if (doc.styleSheets.isEmpty()) {
            fun collectInlineStyleElements(node: WebDomNode) {
                for (c in node.childNodes) {
                    if (c is WebDomElement) {
                        if (c.lowerTagName == "style") {
                            doc.styleSheets.add(
                                WebCssStyleSheetModel(ownerNode = c, initialCss = c.textContent)
                            )
                        } else {
                            collectInlineStyleElements(c)
                        }
                    }
                }
            }
            doc.documentElement?.let { collectInlineStyleElements(it) }
        }

        val compiledSheets = ArrayList<PrecompiledStyleSheet>()
        compiledSheets.add(uaPrecompiled)

        val collectedRules = ArrayList<IndexedStyleRule>()
        keyframesRegistry.clear()
        collectedRules.addAll(uaIndexedRules)
        var orderCounter = uaIndexedRules.size

        for ((idx, sheetModel) in doc.styleSheets.withIndex()) {
            if (resourceLoader.isAbortedProvider?.invoke() == true) break
            if (sheetModel.disabled) continue
            val rawCss = sheetModel.serializeCssText()
            if (rawCss.isBlank()) continue
            val expanded = try {
                expandImports(rawCss, sheetModel.href ?: doc.url, HashSet())
            } catch (t: Throwable) {
                onDiagnostic?.invoke("warn", "Failed expanding CSS imports for sheet $idx: ${t.message}")
                rawCss
            }
            if (resourceLoader.isAbortedProvider?.invoke() == true) break
            orderCounter = try {
                extractRulesRecursive(
                    cssText = expanded,
                    baseUrl = sheetModel.href ?: doc.url,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    scopeId = null,
                    visitedImports = HashSet(),
                    outRules = collectedRules,
                    startOrder = orderCounter
                )
            } catch (t: Throwable) {
                onDiagnostic?.invoke("warn", "Failed extracting CSS rules for sheet $idx: ${t.message}")
                orderCounter
            }
            val parsed = try {
                CssStyleSheet.parse(expanded)
            } catch (t: Throwable) {
                onDiagnostic?.invoke("warn", "Ignoring malformed stylesheet $idx: ${t.message}")
                null
            }
            if (parsed != null) {
                compiledSheets.add(AlienPrecompiledBootstrapStyleSheet.wrapCssStyleSheet(parsed, "doc-sheet-$idx"))
            }
        }

        // Collect ShadowRoot scoped styles
        fun collectShadowSheets(node: WebDomNode) {
            if (node is WebDomElement && node.shadowRoot != null) {
                val sr = node.shadowRoot!!
                val srStyles = collectShadowStyleText(sr)
                if (srStyles.isNotBlank()) {
                    try {
                        val scopedCss = rewriteShadowCssForAlien(srStyles, sr.scopeId)
                        val parsedSr = CssStyleSheet.parse(scopedCss)
                        compiledSheets.add(AlienPrecompiledBootstrapStyleSheet.wrapCssStyleSheet(parsedSr, sr.scopeId))
                        orderCounter = extractRulesRecursive(
                            cssText = srStyles,
                            baseUrl = doc.url,
                            viewportWidth = viewportWidth,
                            viewportHeight = viewportHeight,
                            scopeId = sr.scopeId,
                            visitedImports = HashSet(),
                            outRules = collectedRules,
                            startOrder = orderCounter
                        )
                    } catch (t: Throwable) {
                        onDiagnostic?.invoke("warn", "Ignoring malformed shadow stylesheet '${sr.scopeId}': ${t.message}")
                    }
                }
                for (c in sr.childNodes) collectShadowSheets(c)
            }
            for (c in node.childNodes) collectShadowSheets(c)
        }
        doc.documentElement?.let { collectShadowSheets(it) }

        indexedRules = collectedRules
        rebuildRuleBuckets(collectedRules)
        return compiledSheets
    }

    private fun rebuildRuleBuckets(rules: List<IndexedStyleRule>) {
        val idMap = LinkedHashMap<String, ArrayList<Int>>()
        val classMap = LinkedHashMap<String, ArrayList<Int>>()
        val tagMap = LinkedHashMap<String, ArrayList<Int>>()
        val universalList = ArrayList<Int>()
        val pseudoList = ArrayList<Int>()

        for (i in rules.indices) {
            val rule = rules[i]
            if (rule.hasPseudoBefore || rule.hasPseudoAfter) {
                pseudoList.add(i)
                continue
            }
            val compiled = rule.compiledSelector
            if (compiled.chains.isEmpty()) continue
            for (k in compiled.idKeys) idMap.getOrPut(k) { ArrayList(2) }.add(i)
            for (k in compiled.classKeys) classMap.getOrPut(k) { ArrayList(4) }.add(i)
            for (k in compiled.tagKeys) tagMap.getOrPut(k) { ArrayList(4) }.add(i)
            if (compiled.needsUniversalBucket) universalList.add(i)
        }

        idRuleBuckets = idMap.mapValues { (_, list) -> list.toIntArray() }
        classRuleBuckets = classMap.mapValues { (_, list) -> list.toIntArray() }
        tagRuleBuckets = tagMap.mapValues { (_, list) -> list.toIntArray() }
        universalRuleBucket = universalList.toIntArray()
        pseudoElementRuleIndices = pseudoList.toIntArray()
    }

    internal fun hasAnyKeyframes(): Boolean = keyframesRegistry.isNotEmpty()

    internal fun getKeyframeStops(name: String): List<KeyframeStop>? =
        keyframesRegistry[name] ?: keyframesRegistry.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    fun syncDocumentStyleSheets(
        doc: WebDomDocument,
        session: AlienRenderSession,
        viewportWidth: Int,
        viewportHeight: Int
    ) {
        val compiledSheets = rebuildFromDocument(doc, viewportWidth, viewportHeight)

        // Replace AlienRenderSession styleSheets cleanly (without AlienPrecompiledBootstrapStyleSheet so W3C content-box default holds)
        // and place inline styles after all document/shadow sheets per CSS Cascade.
        session.styleSheets.clear()
        session.styleSheets.addAll(compiledSheets)
        session.insertApplicationStyleSheets = compiledSheets.size
        session.requestLayoutAndRepaint()
    }

    fun computeStyle(
        element: WebDomElement,
        doc: WebDomDocument,
        viewportWidth: Int,
        viewportHeight: Int
    ): Map<String, String> = resolveComputedMap(element, viewportWidth, viewportHeight, pseudoElement = null)

    fun resolveComputedMap(
        element: WebDomElement,
        viewportWidth: Int,
        viewportHeight: Int,
        pseudoElement: String? = null
    ): Map<String, String> {
        val parentComputed = (element.parentNode as? WebDomElement)?.let {
            if (it.computedStyleSnapshot.isEmpty()) {
                resolveComputedMap(it, viewportWidth, viewportHeight, null)
            } else {
                it.computedStyleSnapshot
            }
        } ?: emptyMap()

        val result = LinkedHashMap<String, String>()
        for ((k, v) in parentComputed) {
            if (k in INHERITED_PROPERTIES || k.startsWith("--")) {
                result[k] = v
            }
        }

        val matching = ArrayList<Pair<WebDomSelectorMatcher.Specificity, IndexedStyleRule>>()
        val elementRoot = element.getRootNode(composed = false)
        val elScopeId = (elementRoot as? WebDomShadowRoot)?.scopeId

        fun evaluateCandidateRule(rule: IndexedStyleRule, normalizedPseudo: String?) {
            if (rule.scopeId != null) {
                val isHostRule = rule.selector.contains(":host") && element.shadowRoot?.scopeId == rule.scopeId
                val isInShadow = elScopeId == rule.scopeId
                if (!isHostRule && !isInShadow) return
            } else if (elScopeId != null && rule.originOrder >= 25) {
                // Shadow boundary shields light-document author styles while keeping UA styles (orders < 25)
                return
            }

            if (normalizedPseudo != null) {
                val spec = when {
                    normalizedPseudo == "::before" ->
                        if (rule.hasPseudoBefore) WebDomSelectorMatcher.maxMatchingSpecificity(element, rule.compiledSelector) else null
                    normalizedPseudo == "::after" ->
                        if (rule.hasPseudoAfter) WebDomSelectorMatcher.maxMatchingSpecificity(element, rule.compiledSelector) else null
                    else -> {
                        if (!rule.selector.contains(normalizedPseudo, ignoreCase = true)) null
                        else {
                            val baseSel = rule.selector.replace(normalizedPseudo, "", ignoreCase = true).trim().ifEmpty { "*" }
                            WebDomSelectorMatcher.maxMatchingSpecificity(element, baseSel)
                        }
                    }
                }
                if (spec != null) matching.add(spec to rule)
            } else {
                if (rule.hasPseudoBefore || rule.hasPseudoAfter) return
                val spec = WebDomSelectorMatcher.maxMatchingSpecificity(element, rule.compiledSelector)
                if (spec != null) matching.add(spec to rule)
            }
        }

        if (pseudoElement != null) {
            val normalizedPseudo = pseudoElement.lowercase()
            if (normalizedPseudo == "::before" || normalizedPseudo == "::after") {
                for (ruleIdx in pseudoElementRuleIndices) {
                    evaluateCandidateRule(indexedRules[ruleIdx], normalizedPseudo)
                }
            } else {
                for (rule in indexedRules) {
                    evaluateCandidateRule(rule, normalizedPseudo)
                }
            }
        } else {
            val seenIndices = HashSet<Int>(32)
            fun checkBucket(bucket: IntArray?) {
                if (bucket == null) return
                for (ruleIdx in bucket) {
                    if (seenIndices.add(ruleIdx)) {
                        evaluateCandidateRule(indexedRules[ruleIdx], null)
                    }
                }
            }
            val elId = element.id
            if (elId.isNotEmpty()) {
                checkBucket(idRuleBuckets[elId])
            }
            for (cls in element.classOrder) {
                if (cls.isNotEmpty()) {
                    checkBucket(classRuleBuckets[cls])
                }
            }
            checkBucket(tagRuleBuckets[element.lowerTagName])
            checkBucket(universalRuleBucket)
        }

        matching.sortWith { a, b ->
            val cmp = a.first.compareTo(b.first)
            if (cmp != 0) cmp else a.second.originOrder.compareTo(b.second.originOrder)
        }

        val propValues = LinkedHashMap<String, String>()
        val importantWinners = LinkedHashSet<String>()
        val authorPropValues = LinkedHashMap<String, String>()
        val authorImportantWinners = LinkedHashSet<String>()
        val uaRuleCount = uaIndexedRules.size

        for ((_, rule) in matching) {
            val isAuthorRule = rule.originOrder >= uaRuleCount
            for ((prop, value) in rule.declarations) {
                val isImp = prop in rule.importantProps
                if (isImp) {
                    importantWinners.add(prop)
                    propValues[prop] = value
                } else if (prop !in importantWinners) {
                    propValues[prop] = value
                }
                if (pseudoElement == null && isAuthorRule) {
                    if (isImp) {
                        authorImportantWinners.add(prop)
                        authorPropValues[prop] = value
                    } else if (prop !in authorImportantWinners) {
                        authorPropValues[prop] = value
                    }
                }
            }
        }

        if (pseudoElement == null && scriptingEnabled && element.lowerTagName == "noscript") {
            propValues["display"] = "none"
            authorPropValues["display"] = "none"
        }

        if (pseudoElement == null) {
            for ((prop, value) in element.inlineStyleMap) {
                val isImp = prop in element.inlineImportantProps
                if (isImp) {
                    importantWinners.add(prop)
                    propValues[prop] = value
                } else if (prop !in importantWinners) {
                    propValues[prop] = value
                }
            }
            if (element.animatedStyleOverrides.isNotEmpty()) {
                for ((prop, value) in element.animatedStyleOverrides) {
                    if (prop !in importantWinners) {
                        propValues[prop] = value
                    }
                }
            }
        }

        // Expand shorthand properties (margin, padding, background, border-width/color, border, flex, overflow, gap)
        expandShorthands(propValues)

        // Apply custom variables cleanly first
        for ((k, v) in propValues) {
            if (k.startsWith("--")) {
                result[k] = v
            }
        }

        // Resolve var() references in non-custom properties
        for ((k, rawV) in propValues) {
            if (k.startsWith("--")) continue
            val substituted = resolveCssVars(rawV, result)
            if (substituted.equals("inherit", ignoreCase = true)) {
                parentComputed[k]?.let { result[k] = it }
            } else {
                result[k] = substituted
            }
        }

        val rootFontSize = (element.ownerDocument?.documentElement?.resolvedFontSizePx ?: 16f)
        val parentFontSize = (element.parentNode as? WebDomElement)?.resolvedFontSizePx ?: 16f
        val fontSizePx = result["font-size"]?.let { fs ->
            WebCssMathEvaluator.evaluateLengthPx(fs, parentFontSize, rootFontSize, parentFontSize, viewportWidth.toFloat(), viewportHeight.toFloat())
        } ?: parentFontSize
        element.resolvedFontSizePx = fontSizePx
        result["font-size"] = formatPx(fontSizePx)

        // Apply active layout dimensions from Alien geometry if computed dimensions resolve from layout
        normalizeComputedValues(element, result, fontSizePx, rootFontSize, viewportWidth, viewportHeight, pseudoElement != null)

        if (pseudoElement == null) {
            element.computedStyleSnapshot.clear()
            element.computedStyleSnapshot.putAll(result)
            element.authorStyleSnapshot.clear()
            element.authorImportantSnapshot.clear()
            val parentEl = element.parentNode as? WebDomElement
            for ((rawProp, rawVal) in authorPropValues) {
                if (rawProp.startsWith("--")) continue
                val resolvedVal = resolveCssValue(
                    prop = rawProp,
                    rawValue = rawVal,
                    scopeVars = result,
                    fontSizePx = fontSizePx,
                    rootFontSizePx = rootFontSize,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    parentElement = parentEl
                )
                element.authorStyleSnapshot[rawProp] = resolvedVal
                if (rawProp in authorImportantWinners) {
                    element.authorImportantSnapshot.add(rawProp)
                }
            }
        }
        return result
    }

    fun resolveCssValue(
        prop: String,
        rawValue: String,
        scopeVars: Map<String, String>,
        fontSizePx: Float = 16f,
        rootFontSizePx: Float = 16f,
        viewportWidth: Int = 1024,
        viewportHeight: Int = 768,
        parentElement: WebDomElement? = null
    ): String {
        val substituted = resolveCssVars(rawValue, scopeVars)
        val lowerProp = prop.lowercase()
        val lowerVal = substituted.lowercase()
        if (!lowerVal.contains("calc(") &&
            !lowerVal.contains("min(") &&
            !lowerVal.contains("max(") &&
            !lowerVal.contains("clamp(")
        ) {
            return substituted
        }
        val isVertical = lowerProp == "height" ||
            lowerProp == "min-height" ||
            lowerProp == "max-height" ||
            lowerProp == "top" ||
            lowerProp == "bottom" ||
            lowerProp == "margin-top" ||
            lowerProp == "margin-bottom" ||
            lowerProp == "padding-top" ||
            lowerProp == "padding-bottom" ||
            lowerProp == "row-gap"
        val percentBasis = if (isVertical) {
            parentElement?.cachedLayoutHeight?.takeIf { it > 0.0 }?.toFloat() ?: viewportHeight.toFloat()
        } else {
            parentElement?.cachedLayoutWidth?.takeIf { it > 0.0 }?.toFloat() ?: viewportWidth.toFloat()
        }
        val isUnitlessNumberProp = lowerProp == "opacity" ||
            lowerProp == "z-index" ||
            lowerProp == "flex-grow" ||
            lowerProp == "flex-shrink" ||
            lowerProp == "order" ||
            lowerProp == "line-height" ||
            lowerProp == "font-weight"

        fun evalSingleToken(tok: String): String {
            val tl = tok.lowercase()
            if (!tl.startsWith("calc(") &&
                !tl.startsWith("min(") &&
                !tl.startsWith("max(") &&
                !tl.startsWith("clamp(")
            ) {
                return tok
            }
            val evaluated = WebCssMathEvaluator.evaluateLengthPx(
                rawValue = tok,
                fontSizePx = fontSizePx,
                rootFontSizePx = rootFontSizePx,
                percentBasePx = percentBasis,
                viewportWidthPx = viewportWidth.toFloat(),
                viewportHeightPx = viewportHeight.toFloat()
            ) ?: return tok
            return if (isUnitlessNumberProp) {
                val rounded = (evaluated * 1000f).roundToInt() / 1000f
                if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
            } else {
                formatPx(evaluated)
            }
        }

        val trimmed = substituted.trim()
        if ((lowerVal.startsWith("calc(") || lowerVal.startsWith("min(") || lowerVal.startsWith("max(") || lowerVal.startsWith("clamp(")) &&
            trimmed.endsWith(")") && findMatchingParen(trimmed, trimmed.indexOf('(')) == trimmed.lastIndex
        ) {
            return evalSingleToken(trimmed)
        }
        return splitSpaceTokens(trimmed).joinToString(" ") { evalSingleToken(it) }
    }

    fun getResolvedInlineStyleWithVars(element: WebDomElement): String {
        val merged = LinkedHashMap<String, String>()
        val parentVars = element.computedStyleSnapshot.filterKeys { it.startsWith("--") }
        val resolved = element.computedStyleSnapshot
        // Emit any resolved var-substituted properties onto inlineComponentStyle cleanly
        for ((k, v) in element.inlineStyleMap) {
            merged[k] = resolveCssVars(v, resolved.ifEmpty { parentVars })
        }
        return buildString {
            merged.entries.forEachIndexed { idx, (k, v) ->
                if (idx > 0) append("; ")
                append(k).append(": ").append(v)
                if (k in element.inlineImportantProps) append(" !important")
            }
        }
    }

    fun hasKeyframe(name: String): Boolean = name in keyframesRegistry

    fun sampleKeyframeDeclarations(name: String, progress: Float): Map<String, String> {
        val stops = keyframesRegistry[name] ?: return emptyMap()
        if (stops.isEmpty()) return emptyMap()
        val p = progress.coerceIn(0f, 1f)
        val ordered = stops.sortedBy { it.offsetFraction }
        var before = ordered.first()
        var after = ordered.last()
        for (i in ordered.indices) {
            if (ordered[i].offsetFraction <= p) before = ordered[i]
            if (ordered[i].offsetFraction >= p) {
                after = ordered[i]
                break
            }
        }
        if (before === after || after.offsetFraction == before.offsetFraction) {
            return before.declarations
        }
        val localT = ((p - before.offsetFraction) / (after.offsetFraction - before.offsetFraction)).coerceIn(0f, 1f)
        val out = LinkedHashMap<String, String>()
        val keys = (before.declarations.keys + after.declarations.keys).toSet()
        for (k in keys) {
            val fromV = before.declarations[k]
            val toV = after.declarations[k]
            if (fromV != null && toV != null) {
                val fPx = WebCssMathEvaluator.evaluateLengthPx(fromV)
                val tPx = WebCssMathEvaluator.evaluateLengthPx(toV)
                if (fPx != null && tPx != null && (fromV.endsWith("px") || toV.endsWith("px") || fromV == "0" || toV == "0")) {
                    out[k] = formatPx(fPx + (tPx - fPx) * localT)
                } else {
                    val fNum = fromV.toFloatOrNull()
                    val tNum = toV.toFloatOrNull()
                    if (fNum != null && tNum != null) {
                        out[k] = (fNum + (tNum - fNum) * localT).toString()
                    } else {
                        out[k] = if (localT < 0.5f) fromV else toV
                    }
                }
            } else {
                out[k] = toV ?: fromV.orEmpty()
            }
        }
        return out
    }

    private fun extractRulesRecursive(
        cssText: String,
        baseUrl: String?,
        viewportWidth: Int,
        viewportHeight: Int,
        scopeId: String?,
        visitedImports: MutableSet<String>,
        outRules: MutableList<IndexedStyleRule>,
        startOrder: Int,
        importDepth: Int = 0
    ): Int {
        var order = startOrder
        val cleaned = stripComments(cssText)
        var i = 0
        while (i < cleaned.length) {
            while (i < cleaned.length && cleaned[i].isWhitespace()) i++
            if (i >= cleaned.length) break

            val headerStart = i
            var inQuote: Char? = null
            var foundBrace = -1
            var foundSemi = -1
            while (i < cleaned.length) {
                val c = cleaned[i]
                if (inQuote != null) {
                    if (c == '\\' && i + 1 < cleaned.length) { i += 2; continue }
                    if (c == inQuote) inQuote = null
                } else {
                    if (c == '\'' || c == '"') inQuote = c
                    else if (c == '{') { foundBrace = i; break }
                    else if (c == ';') { foundSemi = i; break }
                }
                i++
            }

            if (foundSemi >= 0) {
                val stmt = cleaned.substring(headerStart, foundSemi).trim()
                if (stmt.startsWith("@import", ignoreCase = true) && importDepth < MAX_IMPORT_DEPTH) {
                    val importUrl = extractImportUrl(stmt)
                    if (importUrl != null) {
                        val resolvedUrl = resourceLoader.resolveUrl(importUrl, baseUrl)
                        if (visitedImports.add(resolvedUrl)) {
                            val res = resourceLoader.loadSubresourceSync(resolvedUrl, baseUrl)
                            if (res.ok) {
                                order = extractRulesRecursive(
                                    res.decodeUtf8Text(),
                                    resolvedUrl,
                                    viewportWidth,
                                    viewportHeight,
                                    scopeId,
                                    visitedImports,
                                    outRules,
                                    order,
                                    importDepth + 1
                                )
                            } else {
                                onDiagnostic?.invoke("warn", "Skipping @import CSS '$resolvedUrl': HTTP ${res.status} ${res.statusText}")
                            }
                        }
                    }
                }
                i = foundSemi + 1
                continue
            }

            if (foundBrace < 0) break
            val header = cleaned.substring(headerStart, foundBrace).trim()
            val bodyStart = foundBrace + 1
            var depth = 1
            i = bodyStart
            inQuote = null
            while (i < cleaned.length && depth > 0) {
                val c = cleaned[i]
                if (inQuote != null) {
                    if (c == '\\' && i + 1 < cleaned.length) { i += 2; continue }
                    if (c == inQuote) inQuote = null
                } else {
                    when (c) {
                        '\'', '"' -> inQuote = c
                        '{' -> depth++
                        '}' -> depth--
                    }
                }
                i++
            }
            val bodyEnd = (i - 1).coerceAtLeast(bodyStart)
            val body = cleaned.substring(bodyStart, bodyEnd)

            when {
                header.startsWith("@media", ignoreCase = true) -> {
                    if (matchesMediaQuery(header, viewportWidth, viewportHeight)) {
                        order = extractRulesRecursive(
                            body, baseUrl, viewportWidth, viewportHeight, scopeId, visitedImports, outRules, order
                        )
                    }
                }
                header.startsWith("@keyframes", ignoreCase = true) ||
                    header.startsWith("@-webkit-keyframes", ignoreCase = true) -> {
                    val kfName = header.substringAfter(' ').trim()
                    if (kfName.isNotEmpty()) {
                        keyframesRegistry[kfName] = parseKeyframeStops(body)
                    }
                }
                header.startsWith("@") -> Unit
                header.isNotEmpty() -> {
                    val (decls, imps) = parseDeclarationsWithImportant(body)
                    outRules.add(IndexedStyleRule(header, decls, imps, order++, scopeId))
                }
            }
        }
        return order
    }

    private fun parseKeyframeStops(body: String): List<KeyframeStop> {
        val stops = ArrayList<KeyframeStop>()
        var i = 0
        while (i < body.length) {
            while (i < body.length && body[i].isWhitespace()) i++
            if (i >= body.length) break
            val brace = body.indexOf('{', i)
            if (brace < 0) break
            val close = findMatchingBrace(body, brace)
            val sel = body.substring(i, brace).trim()
            val declBody = body.substring(brace + 1, close)
            val (decls, _) = parseDeclarationsWithImportant(declBody)
            for (part in sel.split(',')) {
                val tok = part.trim().lowercase()
                val frac = when {
                    tok == "from" -> 0f
                    tok == "to" -> 1f
                    tok.endsWith("%") -> tok.dropLast(1).toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f)
                    else -> null
                }
                if (frac != null) {
                    stops.add(KeyframeStop(frac, decls))
                }
            }
            i = close + 1
        }
        return stops
    }

    private fun matchesMediaQuery(header: String, viewportW: Int, viewportH: Int): Boolean {
        val expr = header.substringAfter("@media", "").trim().lowercase()
        if (expr.isEmpty() || expr == "all" || expr == "screen") return true
        if (expr.contains("print") && !expr.contains("not print") && !expr.contains("screen")) return false
        val parts = expr.split(" and ")
        for (part in parts) {
            val clean = part.trim().removePrefix("(").removeSuffix(")").trim()
            if (clean == "screen" || clean == "all" || clean == "only screen") continue
            val colonIdx = clean.indexOf(':')
            if (colonIdx > 0) {
                val feat = clean.substring(0, colonIdx).trim()
                val valStr = clean.substring(colonIdx + 1).trim()
                val px = WebCssMathEvaluator.evaluateLengthPx(valStr, 16f, 16f, 0f, viewportW.toFloat(), viewportH.toFloat())
                if (px != null) {
                    when (feat) {
                        "min-width" -> if (viewportW < px) return false
                        "max-width" -> if (viewportW > px) return false
                        "min-height" -> if (viewportH < px) return false
                        "max-height" -> if (viewportH > px) return false
                        "width" -> if (viewportW.toFloat() != px) return false
                        "height" -> if (viewportH.toFloat() != px) return false
                    }
                }
            }
        }
        return true
    }

    private fun expandImports(
        cssText: String,
        baseUrl: String?,
        visited: MutableSet<String>,
        importDepth: Int = 0
    ): String {
        if (importDepth >= MAX_IMPORT_DEPTH) return cssText
        val sb = StringBuilder()
        val cleaned = stripComments(cssText)
        for (line in cleaned.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("@import", ignoreCase = true) && trimmed.endsWith(";")) {
                val importUrl = extractImportUrl(trimmed.dropLast(1))
                if (importUrl != null) {
                    val resolved = resourceLoader.resolveUrl(importUrl, baseUrl)
                    if (visited.add(resolved)) {
                        val res = resourceLoader.loadSubresourceSync(resolved, baseUrl)
                        if (res.ok) {
                            sb.append(expandImports(res.decodeUtf8Text(), resolved, visited, importDepth + 1)).append('\n')
                        } else {
                            onDiagnostic?.invoke("warn", "Skipping expanded @import '$resolved': HTTP ${res.status} ${res.statusText}")
                        }
                    }
                    continue
                }
            }
            sb.append(line).append('\n')
        }
        return sb.toString()
    }

    private fun extractImportUrl(stmt: String): String? {
        val urlFn = URL_IMPORT_REGEX.find(stmt)
        if (urlFn != null) return urlFn.groupValues[1].trim()
        val quoted = QUOTED_IMPORT_REGEX.find(stmt)
        return quoted?.groupValues?.get(1)?.trim()
    }

    private fun collectShadowStyleText(shadowRoot: WebDomShadowRoot): String = buildString {
        fun walk(n: WebDomNode) {
            for (c in n.childNodes) {
                if (c is WebDomElement) {
                    if (c.lowerTagName == "style") append(c.textContent).append('\n')
                    else walk(c)
                }
            }
        }
        walk(shadowRoot)
    }

    private fun rewriteShadowCssForAlien(css: String, scopeId: String): String {
        val rules = WebCssStyleSheetModel(initialCss = css).rules
        return buildString {
            for (rule in rules) {
                val brace = rule.indexOf('{')
                if (brace <= 0) {
                    append(rule).append('\n')
                    continue
                }
                val sel = rule.substring(0, brace).trim()
                val body = rule.substring(brace)
                val rewrittenSel = sel.split(',').joinToString(", ") { part ->
                    val p = part.trim()
                    if (p.contains(":host")) {
                        p.replace(":host", "[data-gui-shadow-host=\"$scopeId\"]")
                    } else {
                        "[data-gui-shadow-scope=\"$scopeId\"] $p"
                    }
                }
                append(rewrittenSel).append(' ').append(body).append('\n')
            }
        }
    }

    private fun parseDeclarationsWithImportant(body: String): Pair<Map<String, String>, Set<String>> {
        val decls = LinkedHashMap<String, String>()
        val imps = LinkedHashSet<String>()
        var start = 0
        var paren = 0
        var quote: Char? = null
        var i = 0
        fun commit(segment: String) {
            val colon = segment.indexOf(':')
            if (colon <= 0) return
            val rawProp = segment.substring(0, colon).trim()
            val prop = if (rawProp.startsWith("--")) rawProp else rawProp.lowercase()
            val rawVal = segment.substring(colon + 1).trim()
            if (rawVal.isEmpty()) return
            val isImp = rawVal.endsWith("!important", ignoreCase = true)
            val cleanVal = if (isImp) rawVal.dropLast(10).trim() else rawVal
            decls[prop] = cleanVal
            if (isImp) imps.add(prop) else imps.remove(prop)
        }
        while (i < body.length) {
            val c = body[i]
            if (quote != null) {
                if (c == '\\' && i + 1 < body.length) { i += 2; continue }
                if (c == quote) quote = null
            } else {
                when (c) {
                    '\'', '"' -> quote = c
                    '(' -> paren++
                    ')' -> if (paren > 0) paren--
                    ';' -> if (paren == 0) {
                        commit(body.substring(start, i))
                        start = i + 1
                    }
                }
            }
            i++
        }
        if (start < body.length) commit(body.substring(start))
        return decls to imps
    }

    private fun expandShorthands(props: MutableMap<String, String>) {
        props["margin"]?.let { expandBoxEdges(it, "margin", "", props) }
        props["padding"]?.let { expandBoxEdges(it, "padding", "", props) }
        props["border-width"]?.let { expandBoxEdges(it, "border", "-width", props) }
        props["border-color"]?.let { expandBoxEdges(it, "border", "-color", props) }
        props["border"]?.let { b ->
            val parts = splitSpaceTokens(b)
            var widthTok: String? = null
            var styleTok: String? = null
            var colorTok: String? = null
            for (p in parts) {
                val pl = p.lowercase()
                when {
                    pl == "none" -> { widthTok = "0px"; styleTok = "none" }
                    pl == "solid" || pl == "dashed" || pl == "dotted" || pl == "double" -> styleTok = pl
                    pl.endsWith("px") || pl.endsWith("em") || pl.endsWith("rem") || pl == "0" -> widthTok = p
                    else -> colorTok = p
                }
            }
            if (widthTok != null) {
                for (side in listOf("top", "right", "bottom", "left")) setDefault(props, "border-$side-width", widthTok)
            }
            if (styleTok != null) {
                for (side in listOf("top", "right", "bottom", "left")) setDefault(props, "border-$side-style", styleTok)
            }
            if (colorTok != null) {
                for (side in listOf("top", "right", "bottom", "left")) setDefault(props, "border-$side-color", colorTok)
            }
        }
        for (side in listOf("top", "right", "bottom", "left")) {
            props["border-$side"]?.let { bs ->
                val parts = splitSpaceTokens(bs)
                for (p in parts) {
                    val pl = p.lowercase()
                    when {
                        pl == "none" -> {
                            setDefault(props, "border-$side-width", "0px")
                            setDefault(props, "border-$side-style", "none")
                        }
                        pl == "solid" || pl == "dashed" || pl == "dotted" -> setDefault(props, "border-$side-style", pl)
                        pl.endsWith("px") || pl == "0" -> setDefault(props, "border-$side-width", p)
                        else -> setDefault(props, "border-$side-color", p)
                    }
                }
            }
        }
        props["background"]?.let { bg ->
            val trimmed = bg.trim()
            if (trimmed.contains("gradient(", ignoreCase = true) || trimmed.contains("url(", ignoreCase = true)) {
                setDefault(props, "background-image", trimmed)
            } else {
                setDefault(props, "background-color", trimmed)
            }
        }
        props["gap"]?.let { g ->
            val parts = splitSpaceTokens(g)
            if (parts.size == 1) {
                setDefault(props, "row-gap", parts[0])
                setDefault(props, "column-gap", parts[0])
            } else if (parts.size >= 2) {
                setDefault(props, "row-gap", parts[0])
                setDefault(props, "column-gap", parts[1])
            }
        }
        props["overflow"]?.let { o ->
            setDefault(props, "overflow-x", o)
            setDefault(props, "overflow-y", o)
        }
        props["border-radius"]?.let { br ->
            val tok = splitSpaceTokens(br).firstOrNull() ?: br
            setDefault(props, "border-top-left-radius", tok)
            setDefault(props, "border-top-right-radius", tok)
            setDefault(props, "border-bottom-right-radius", tok)
            setDefault(props, "border-bottom-left-radius", tok)
        }
    }

    private fun expandBoxEdges(raw: String, prefix: String, suffix: String, props: MutableMap<String, String>) {
        val parts = splitSpaceTokens(raw)
        val (t, r, b, l) = when (parts.size) {
            1 -> listOf(parts[0], parts[0], parts[0], parts[0])
            2 -> listOf(parts[0], parts[1], parts[0], parts[1])
            3 -> listOf(parts[0], parts[1], parts[2], parts[1])
            4 -> parts
            else -> return
        }
        setDefault(props, "$prefix-top$suffix", t)
        setDefault(props, "$prefix-right$suffix", r)
        setDefault(props, "$prefix-bottom$suffix", b)
        setDefault(props, "$prefix-left$suffix", l)
    }

    private fun normalizeComputedValues(
        element: WebDomElement,
        map: MutableMap<String, String>,
        fontSizePx: Float,
        rootFontSizePx: Float,
        viewportW: Int,
        viewportH: Int,
        isPseudo: Boolean
    ) {
        setDefault(map, "display", "inline")
        setDefault(map, "position", "static")
        setDefault(map, "box-sizing", "content-box")
        setDefault(map, "visibility", "visible")
        setDefault(map, "opacity", "1")
        setDefault(map, "z-index", "auto")
        setDefault(map, "color", "rgb(0, 0, 0)")
        setDefault(map, "background-color", "rgba(0, 0, 0, 0)")
        setDefault(map, "background-image", "none")
        setDefault(map, "font-weight", "400")
        setDefault(map, "overflow", "visible")
        setDefault(map, "text-indent", "0px")
        setDefault(map, "flex-grow", "0")
        setDefault(map, "flex-shrink", "1")
        setDefault(map, "order", "0")

        for (colorProp in listOf("color", "background-color", "border-top-color", "border-right-color", "border-bottom-color", "border-left-color", "border-color")) {
            map[colorProp]?.let { map[colorProp] = normalizeColorToCss(it) }
        }

        if (map["font-weight"].equals("bold", ignoreCase = true)) map["font-weight"] = "700"
        if (map["font-weight"].equals("normal", ignoreCase = true)) map["font-weight"] = "400"

        for (lenProp in listOf(
            "margin-top", "margin-right", "margin-bottom", "margin-left",
            "padding-top", "padding-right", "padding-bottom", "padding-left",
            "border-top-width", "border-right-width", "border-bottom-width", "border-left-width",
            "border-radius", "outline-width", "text-indent", "letter-spacing",
            "gap", "row-gap", "column-gap", "top", "left", "right", "bottom"
        )) {
            val v = map[lenProp]
            if (v != null && !v.equals("auto", ignoreCase = true)) {
                val px = WebCssMathEvaluator.evaluateLengthPx(v, fontSizePx, rootFontSizePx, 0f, viewportW.toFloat(), viewportH.toFloat())
                if (px != null) map[lenProp] = formatPx(px)
            } else if (v == null && (lenProp.startsWith("margin-") || lenProp.startsWith("padding-") || lenProp.startsWith("border-"))) {
                map[lenProp] = "0px"
            }
        }

        if (!isPseudo && element.cachedLayoutWidth > 0.0 && (map["width"] == null || map["width"] == "auto" || map["width"]?.endsWith("%") == true || map["width"]?.startsWith("calc(") == true)) {
            map["width"] = formatPx(element.cachedLayoutWidth.toFloat())
        } else {
            map["width"]?.let { w ->
                val px = WebCssMathEvaluator.evaluateLengthPx(w, fontSizePx, rootFontSizePx, viewportW.toFloat(), viewportW.toFloat(), viewportH.toFloat())
                if (px != null) map["width"] = formatPx(px)
            } ?: run { map["width"] = "auto" }
        }
        if (!isPseudo && element.cachedLayoutHeight > 0.0 && (map["height"] == null || map["height"] == "auto" || map["height"]?.endsWith("%") == true || map["height"]?.startsWith("calc(") == true)) {
            map["height"] = formatPx(element.cachedLayoutHeight.toFloat())
        } else {
            map["height"]?.let { h ->
                val px = WebCssMathEvaluator.evaluateLengthPx(h, fontSizePx, rootFontSizePx, viewportH.toFloat(), viewportW.toFloat(), viewportH.toFloat())
                if (px != null) map["height"] = formatPx(px)
            } ?: run { map["height"] = "auto" }
        }
    }

    fun normalizeColorToCss(raw: String): String {
        val t = raw.trim().lowercase()
        NAMED_COLORS[t]?.let { return it }
        if (t.startsWith("rgb(") || t.startsWith("rgba(")) {
            val parsed = RgbaColor.fromCss(raw) ?: return raw.trim()
            return if (parsed.a >= 255) {
                "rgb(${parsed.r}, ${parsed.g}, ${parsed.b})"
            } else {
                val alphaStr = ((parsed.a / 255.0) * 1000.0).roundToInt() / 1000.0
                val cleanAlpha = if (alphaStr == 0.0) "0" else alphaStr.toString().trimEnd('0').trimEnd('.')
                "rgba(${parsed.r}, ${parsed.g}, ${parsed.b}, $cleanAlpha)"
            }
        }
        val parsed = RgbaColor.fromCss(raw)
        if (parsed != null) {
            return if (parsed.a >= 255) {
                "rgb(${parsed.r}, ${parsed.g}, ${parsed.b})"
            } else {
                val alphaStr = ((parsed.a / 255.0) * 1000.0).roundToInt() / 1000.0
                val cleanAlpha = if (alphaStr == 0.0) "0" else alphaStr.toString().trimEnd('0').trimEnd('.')
                "rgba(${parsed.r}, ${parsed.g}, ${parsed.b}, $cleanAlpha)"
            }
        }
        return raw.trim()
    }

    fun resolveCssVars(value: String, scopeVars: Map<String, String>, depth: Int = 0): String {
        if (depth > 8 || !value.contains("var(", ignoreCase = true)) return value
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            if (value.regionMatches(i, "var(", 0, 4, ignoreCase = true)) {
                val close = findMatchingParen(value, i + 3)
                if (close >= value.length || i + 4 > close) {
                    sb.append(value.substring(i))
                    break
                }
                val inner = value.substring(i + 4, close)
                val commaIdx = findTopLevelComma(inner)
                val varName = (if (commaIdx >= 0) inner.substring(0, commaIdx) else inner).trim()
                val fallback = if (commaIdx >= 0) inner.substring(commaIdx + 1).trim() else ""
                val resolved = scopeVars[varName]?.takeIf { it.isNotEmpty() } ?: fallback
                sb.append(resolveCssVars(resolved, scopeVars, depth + 1))
                i = close + 1
            } else {
                sb.append(value[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun stripComments(src: String): String {
        val source = src.removePrefix("\uFEFF")
        val sb = StringBuilder(source.length)
        var i = 0
        var quote: Char? = null
        while (i < source.length) {
            val c = source[i]
            if (quote != null) {
                sb.append(c)
                if (c == '\\' && i + 1 < source.length) { sb.append(source[++i]) }
                else if (c == quote) quote = null
                i++
            } else if (c == '/' && i + 1 < source.length && source[i + 1] == '*') {
                val end = source.indexOf("*/", i + 2)
                i = if (end < 0) source.length else end + 2
            } else {
                if (c == '\'' || c == '"') quote = c
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun splitSpaceTokens(raw: String): List<String> {
        val out = ArrayList<String>()
        var start = 0
        var depth = 0
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '(' -> depth++
                c == ')' -> if (depth > 0) depth--
                c.isWhitespace() && depth == 0 -> {
                    val seg = raw.substring(start, i).trim()
                    if (seg.isNotEmpty()) out.add(seg)
                    start = i + 1
                }
            }
            i++
        }
        val tail = raw.substring(start).trim()
        if (tail.isNotEmpty()) out.add(tail)
        return out
    }

    private fun findMatchingBrace(text: String, openBrace: Int): Int {
        var depth = 0
        var i = openBrace
        while (i < text.length) {
            if (text[i] == '{') depth++
            else if (text[i] == '}') {
                depth--
                if (depth == 0) return i
            }
            i++
        }
        return (text.length - 1).coerceAtLeast(openBrace)
    }

    private fun findMatchingParen(text: String, openParen: Int): Int {
        var depth = 0
        var i = openParen
        while (i < text.length) {
            if (text[i] == '(') depth++
            else if (text[i] == ')') {
                depth--
                if (depth == 0) return i
            }
            i++
        }
        return text.length
    }

    private fun findTopLevelComma(text: String): Int {
        var depth = 0
        for (i in text.indices) {
            when (text[i]) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                ',' -> if (depth == 0) return i
            }
        }
        return -1
    }

    private fun setDefault(map: MutableMap<String, String>, key: String, value: String) {
        if (!map.containsKey(key)) {
            map[key] = value
        }
    }

    private fun formatPx(px: Float): String {
        val rounded = (px * 100f).roundToInt() / 100f
        return if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}px" else "${rounded}px"
    }
}
