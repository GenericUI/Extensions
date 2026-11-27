package com.nexustools.genericui.ext.svg

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Mutable, interactive W3C `<svg>` vector scene graph component (`Svg`).
 *
 * - Supports loading/serializing full W3C SVG XML (`loadXml`, `fromXml`, `toXml`).
 * - Supports live DOM-style querying & mutation (`findById`, `queryByTag`, `querySelector`,
 *   `addChild`, `insertChild`, `removeChild`, `clearChildren`), automatically tracking scene
 *   dirty state on every structural or attribute change.
 * - Renders standalone via pure-Kotlin [SvgSoftwareRasterizer] into [ImageBuffer] and supports
 *   inverse-transform pointer hit-testing (`hitTest`).
 * - Resolves native peers via `factory.createExtensionNativeImpl(this)` first (for `:extensions:graphics:svg:alien`),
 *   then falls back to [SvgExtension.createSvgNative].
 */
open class Svg(
    viewBox: SvgViewBox? = null,
    svgWidth: Float? = null,
    svgHeight: Float? = null,
    preserveAspectRatio: SvgPreserveAspectRatio = SvgPreserveAspectRatio.DEFAULT
) : AbstractComponent(htmlTag = "svg"), SvgContainer {

    var sceneVersion: Long = 0L
        private set

    var onVectorSceneDirty: (() -> Unit)? = null

    var viewBox: SvgViewBox? = viewBox
        set(value) {
            if (field == value) return
            field = value
            markVectorSceneDirty()
        }

    var preserveAspectRatio: SvgPreserveAspectRatio = preserveAspectRatio
        set(value) {
            if (field == value) return
            field = value
            markVectorSceneDirty()
        }

    var svgWidth: Float? = svgWidth
        set(value) {
            val norm = value?.coerceAtLeast(0f)
            if (field == norm) return
            field = norm
            markVectorSceneDirty()
        }

    var svgHeight: Float? = svgHeight
        set(value) {
            val norm = value?.coerceAtLeast(0f)
            if (field == norm) return
            field = norm
            markVectorSceneDirty()
        }

    var xmlns: String = "http://www.w3.org/2000/svg"
        set(value) {
            val norm = value.trim().ifEmpty { "http://www.w3.org/2000/svg" }
            if (field == norm) return
            field = norm
            markVectorSceneDirty()
        }

    val defs: SvgDefs = SvgDefs().also { it.attachOwner(this) }

    private val backingChildren = ArrayList<SvgElement>()

    override val children: List<SvgElement>
        get() = backingChildren.toList()

    internal fun markVectorSceneDirty() {
        sceneVersion++
        invalidateRenderCache()
        onVectorSceneDirty?.invoke()
        runOnNativeUi { impl ->
            (impl as? SvgNativeImpl<*>)?.invalidateSvgScene()
        }
    }

    override fun addChild(element: SvgElement) {
        insertChild(backingChildren.size, element)
    }

    override fun insertChild(index: Int, element: SvgElement) {
        if (element.parentElement !== null || (element.ownerSvg !== null && element.ownerSvg !== this)) {
            element.detachFromParentContainer()
        } else {
            backingChildren.remove(element)
        }
        val clamped = index.coerceIn(0, backingChildren.size)
        backingChildren.add(clamped, element)
        element.parentElement = null
        element.attachOwner(this)
        markVectorSceneDirty()
    }

    override fun removeChild(element: SvgElement): Boolean {
        val removed = backingChildren.remove(element)
        if (removed) {
            element.parentElement = null
            element.attachOwner(null)
            markVectorSceneDirty()
        }
        return removed
    }

    override fun clearChildren() {
        if (backingChildren.isEmpty()) return
        for (child in backingChildren) {
            child.parentElement = null
            child.attachOwner(null)
        }
        backingChildren.clear()
        markVectorSceneDirty()
    }

    fun findById(id: String): SvgElement? {
        val clean = SvgPaint.normalizeId(id)
        if (clean.isEmpty()) return null
        defs.findById(clean)?.let { return it }
        for (child in backingChildren) {
            child.findByIdRecursive(clean)?.let { return it }
        }
        return null
    }

    fun queryByTag(tagName: String): List<SvgElement> {
        val target = tagName.trim()
        if (target.isEmpty()) return emptyList()
        val out = ArrayList<SvgElement>()
        defs.collectByTagRecursive(target, out)
        for (child in backingChildren) {
            child.collectByTagRecursive(target, out)
        }
        return out
    }

    fun querySelector(selector: String): SvgElement? =
        querySelectorAll(selector).firstOrNull()

    fun querySelectorAll(selector: String): List<SvgElement> {
        val trimmed = selector.trim()
        if (trimmed.isEmpty()) return emptyList()
        val results = ArrayList<SvgElement>()
        for (groupSel in trimmed.split(',')) {
            val part = groupSel.trim()
            if (part.isEmpty()) continue
            val chain = parseSelectorChain(part) ?: continue
            val allElements = ArrayList<SvgElement>()
            for (dc in defs.children) dc.collectAllDescendants(allElements)
            for (c in backingChildren) c.collectAllDescendants(allElements)
            for (el in allElements) {
                if (matchesSelectorChain(el, chain) && el !in results) {
                    results.add(el)
                }
            }
        }
        return results
    }

    fun loadXml(svgMarkup: String) {
        SvgDocumentParser.parse(svgMarkup, target = this)
        markVectorSceneDirty()
    }

    fun toXml(pretty: Boolean = false): String =
        SvgXmlSerializer.serialize(this, pretty = pretty)

    fun renderToImageBuffer(widthPx: Int, heightPx: Int): ImageBuffer =
        SvgSoftwareRasterizer.render(this, widthPx, heightPx)

    fun hitTest(
        viewportX: Float,
        viewportY: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): SvgElement? = SvgSoftwareRasterizer.hitTest(this, viewportX, viewportY, viewportWidth, viewportHeight)

    fun resolveIntrinsicPixelSize(maxWidthPx: Int? = null, maxHeightPx: Int? = null): Size {
        val vb = viewBox
        val natW = svgWidth?.takeIf { it > 0f } ?: vb?.width?.takeIf { it > 0f } ?: 300f
        val natH = svgHeight?.takeIf { it > 0f } ?: vb?.height?.takeIf { it > 0f } ?: 150f
        val ratio = natW / natH.coerceAtLeast(1e-4f)

        val expW = widthPx?.takeIf { it > 0 }
        val expH = heightPx?.takeIf { it > 0 }
        var outW: Float
        var outH: Float
        when {
            expW != null && expH != null -> {
                outW = expW.toFloat()
                outH = expH.toFloat()
            }
            expW != null -> {
                outW = expW.toFloat()
                outH = outW / ratio
            }
            expH != null -> {
                outH = expH.toFloat()
                outW = outH * ratio
            }
            else -> {
                outW = natW
                outH = natH
            }
        }

        if (maxWidthPx != null && maxWidthPx > 0 && outW > maxWidthPx) {
            val scale = maxWidthPx / outW
            outW = maxWidthPx.toFloat()
            if (expH == null) outH *= scale
        }
        if (maxHeightPx != null && maxHeightPx > 0 && outH > maxHeightPx) {
            val scale = maxHeightPx / outH
            outH = maxHeightPx.toFloat()
            if (expW == null) outW *= scale
        }

        return Size(outW.roundToInt().coerceAtLeast(1), outH.roundToInt().coerceAtLeast(1))
    }

    protected override fun createNativeImpl(factory: NativeFactory): NativeImpl<*> =
        (factory.createExtensionNativeImpl(this) as? SvgNativeImpl<*>)
            ?: SvgExtension.createSvgNative(this, factory)

    override fun syncStateToNative() {
        super.syncStateToNative()
        typedNative<SvgNativeImpl<*>>()?.syncSvgScene(this)
    }

    private class SimpleSelector(
        val tag: String? = null,
        val id: String? = null,
        val classes: List<String> = emptyList()
    ) {
        fun matches(element: SvgElement): Boolean {
            if (tag != null && tag != "*" && !element.tagName.equals(tag, ignoreCase = true)) {
                return false
            }
            if (id != null && element.id != id) {
                return false
            }
            for (cls in classes) {
                if (!element.hasClass(cls)) return false
            }
            return true
        }
    }

    private fun parseSelectorChain(raw: String): List<Pair<Boolean, SimpleSelector>>? {
        val normalized = raw.replace(">", " > ").trim()
        val tokens = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val chain = ArrayList<Pair<Boolean, SimpleSelector>>()
        var nextDirectChild = false
        for (tok in tokens) {
            if (tok == ">") {
                nextDirectChild = true
                continue
            }
            val parsed = parseSimpleSelector(tok) ?: return null
            chain.add(nextDirectChild to parsed)
            nextDirectChild = false
        }
        return chain.takeIf { it.isNotEmpty() }
    }

    private fun parseSimpleSelector(token: String): SimpleSelector? {
        if (token.isEmpty()) return null
        var idx = 0
        var tag: String? = null
        var id: String? = null
        val classes = ArrayList<String>()

        if (token[0] != '#' && token[0] != '.') {
            val start = idx
            while (idx < token.length && token[idx] != '#' && token[idx] != '.') idx++
            tag = token.substring(start, idx)
        }
        while (idx < token.length) {
            val marker = token[idx]
            idx++
            val start = idx
            while (idx < token.length && token[idx] != '#' && token[idx] != '.') idx++
            val ident = token.substring(start, idx)
            if (ident.isEmpty()) return null
            when (marker) {
                '#' -> id = ident
                '.' -> classes.add(ident)
            }
        }
        return SimpleSelector(tag, id, classes)
    }

    private fun matchesSelectorChain(
        element: SvgElement,
        chain: List<Pair<Boolean, SimpleSelector>>
    ): Boolean {
        val last = chain.last()
        if (!last.second.matches(element)) return false
        var chainIdx = chain.size - 2
        var requireDirect = last.first
        var cursor: SvgElement? = element.parentElement
        while (chainIdx >= 0) {
            val step = chain[chainIdx]
            if (cursor == null) return false
            if (requireDirect) {
                if (!step.second.matches(cursor)) return false
                requireDirect = step.first
                cursor = cursor.parentElement
                chainIdx--
            } else {
                while (cursor != null && !step.second.matches(cursor)) {
                    cursor = cursor.parentElement
                }
                if (cursor == null) return false
                requireDirect = step.first
                cursor = cursor.parentElement
                chainIdx--
            }
        }
        return true
    }

    companion object {
        fun fromXml(svgMarkup: String): Svg = Svg().apply {
            loadXml(svgMarkup)
        }
    }
}
