package com.nexustools.genericui.ext.svg

import com.nexustools.genericui.event.PointerEvent

/**
 * Base class for all mutable nodes inside an [Svg] scene graph (`<g>`, `<defs>`, `<rect>`,
 * `<circle>`, `<ellipse>`, `<line>`, `<polyline>`, `<polygon>`, `<path>`, `<text>`, `<use>`,
 * `<clipPath>`, `<linearGradient>`, `<radialGradient>`).
 *
 * Every property or collection mutation automatically notifies the owning [Svg] document so
 * native, alien, and web renderers re-sync or re-rasterize on demand.
 */
abstract class SvgElement(val tagName: String) {

    internal var ownerSvg: Svg? = null
        private set

    var parentElement: SvgElement? = null
        internal set

    protected fun notifyMutated() {
        ownerSvg?.markVectorSceneDirty()
    }

    internal open fun attachOwner(svg: Svg?) {
        ownerSvg = svg
    }

    internal fun detachFromParentContainer() {
        when (val p = parentElement) {
            is SvgGroup -> p.removeChild(this)
            is SvgDefs -> p.removeChild(this)
            is SvgClipPath -> p.removeChild(this)
            null -> ownerSvg?.removeChild(this)
        }
        parentElement = null
        attachOwner(null)
    }

    var id: String? = null
        set(value) {
            val norm = value?.trim()?.ifEmpty { null }
            if (field == norm) return
            field = norm
            notifyMutated()
        }

    private val backingClasses = linkedSetOf<String>()

    val classList: MutableSet<String> = object : AbstractMutableSet<String>() {
        override val size: Int
            get() = backingClasses.size

        override fun add(element: String): Boolean {
            val trimmed = element.trim()
            if (trimmed.isEmpty()) return false
            val added = backingClasses.add(trimmed)
            if (added) notifyMutated()
            return added
        }

        override fun remove(element: String): Boolean {
            val removed = backingClasses.remove(element.trim())
            if (removed) notifyMutated()
            return removed
        }

        override fun clear() {
            if (backingClasses.isNotEmpty()) {
                backingClasses.clear()
                notifyMutated()
            }
        }

        override fun iterator(): MutableIterator<String> {
            val it = backingClasses.iterator()
            return object : MutableIterator<String> {
                override fun hasNext(): Boolean = it.hasNext()
                override fun next(): String = it.next()
                override fun remove() {
                    it.remove()
                    notifyMutated()
                }
            }
        }
    }

    var className: String
        get() = backingClasses.joinToString(" ")
        set(value) {
            val tokens = value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (backingClasses.size == tokens.size && backingClasses.containsAll(tokens)) {
                var sameOrder = true
                val it = backingClasses.iterator()
                for (t in tokens) {
                    if (!it.hasNext() || it.next() != t) {
                        sameOrder = false
                        break
                    }
                }
                if (sameOrder) return
            }
            backingClasses.clear()
            backingClasses.addAll(tokens)
            notifyMutated()
        }

    fun hasClass(token: String): Boolean {
        val trimmed = token.trim()
        return trimmed.isNotEmpty() && trimmed in backingClasses
    }

    fun addClass(classNames: String) {
        var changed = false
        for (tok in classNames.trim().split(Regex("\\s+"))) {
            if (tok.isNotEmpty() && backingClasses.add(tok)) {
                changed = true
            }
        }
        if (changed) notifyMutated()
    }

    fun removeClass(classNames: String) {
        var changed = false
        for (tok in classNames.trim().split(Regex("\\s+"))) {
            if (tok.isNotEmpty() && backingClasses.remove(tok)) {
                changed = true
            }
        }
        if (changed) notifyMutated()
    }

    var opacity: Float = 1f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field == clamped) return
            field = clamped
            notifyMutated()
        }

    var fill: SvgPaint? = null
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    internal var explicitFillOpacity: Float? = null
    var fillOpacity: Float
        get() = explicitFillOpacity ?: 1f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (explicitFillOpacity == clamped) return
            explicitFillOpacity = clamped
            notifyMutated()
        }

    internal var explicitFillRule: SvgFillRule? = null
    var fillRule: SvgFillRule
        get() = explicitFillRule ?: SvgFillRule.NONZERO
        set(value) {
            if (explicitFillRule == value) return
            explicitFillRule = value
            notifyMutated()
        }

    var stroke: SvgPaint? = null
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    internal var explicitStrokeWidth: Float? = null
    var strokeWidth: Float
        get() = explicitStrokeWidth ?: 1f
        set(value) {
            val clamped = value.coerceAtLeast(0f)
            if (explicitStrokeWidth == clamped) return
            explicitStrokeWidth = clamped
            notifyMutated()
        }

    internal var explicitStrokeOpacity: Float? = null
    var strokeOpacity: Float
        get() = explicitStrokeOpacity ?: 1f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (explicitStrokeOpacity == clamped) return
            explicitStrokeOpacity = clamped
            notifyMutated()
        }

    internal var explicitStrokeLinecap: SvgStrokeLinecap? = null
    var strokeLinecap: SvgStrokeLinecap
        get() = explicitStrokeLinecap ?: SvgStrokeLinecap.BUTT
        set(value) {
            if (explicitStrokeLinecap == value) return
            explicitStrokeLinecap = value
            notifyMutated()
        }

    internal var explicitStrokeLinejoin: SvgStrokeLinejoin? = null
    var strokeLinejoin: SvgStrokeLinejoin
        get() = explicitStrokeLinejoin ?: SvgStrokeLinejoin.MITER
        set(value) {
            if (explicitStrokeLinejoin == value) return
            explicitStrokeLinejoin = value
            notifyMutated()
        }

    var strokeDasharray: List<Float>? = null
        set(value) {
            val copy = value?.toList()
            if (field == copy) return
            field = copy
            notifyMutated()
        }

    internal var explicitStrokeDashoffset: Float? = null
    var strokeDashoffset: Float
        get() = explicitStrokeDashoffset ?: 0f
        set(value) {
            if (explicitStrokeDashoffset == value) return
            explicitStrokeDashoffset = value
            notifyMutated()
        }

    private val backingTransforms = ArrayList<SvgTransform>()

    val transformList: MutableList<SvgTransform> = object : AbstractMutableList<SvgTransform>() {
        override val size: Int
            get() = backingTransforms.size

        override fun get(index: Int): SvgTransform = backingTransforms[index]

        override fun add(index: Int, element: SvgTransform) {
            backingTransforms.add(index, element)
            notifyMutated()
        }

        override fun removeAt(index: Int): SvgTransform {
            val removed = backingTransforms.removeAt(index)
            notifyMutated()
            return removed
        }

        override fun set(index: Int, element: SvgTransform): SvgTransform {
            val prev = backingTransforms.set(index, element)
            if (prev != element) notifyMutated()
            return prev
        }
    }

    fun setTransform(rawTransform: String?) {
        val parsed = SvgTransform.parseTransformList(rawTransform)
        backingTransforms.clear()
        backingTransforms.addAll(parsed)
        notifyMutated()
    }

    var clipPathId: String? = null
        set(value) {
            val normalized = value?.let { SvgPaint.normalizeId(it).ifEmpty { null } }
            if (field == normalized) return
            field = normalized
            notifyMutated()
        }

    var visible: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    private val backingAttributes = linkedMapOf<String, String>()

    val attributes: MutableMap<String, String> = object : AbstractMutableMap<String, String>() {
        override val entries: MutableSet<MutableMap.MutableEntry<String, String>>
            get() = object : AbstractMutableSet<MutableMap.MutableEntry<String, String>>() {
                override val size: Int
                    get() = backingAttributes.size

                override fun add(element: MutableMap.MutableEntry<String, String>): Boolean {
                    val prev = backingAttributes.put(element.key, element.value)
                    if (prev != element.value) notifyMutated()
                    return prev != element.value
                }

                override fun iterator(): MutableIterator<MutableMap.MutableEntry<String, String>> {
                    val it = backingAttributes.entries.iterator()
                    return object : MutableIterator<MutableMap.MutableEntry<String, String>> {
                        override fun hasNext(): Boolean = it.hasNext()
                        override fun next(): MutableMap.MutableEntry<String, String> = it.next()
                        override fun remove() {
                            it.remove()
                            notifyMutated()
                        }
                    }
                }
            }

        override fun put(key: String, value: String): String? {
            val prev = backingAttributes.put(key, value)
            if (prev != value) notifyMutated()
            return prev
        }

        override fun remove(key: String): String? {
            val existed = backingAttributes.containsKey(key)
            val prev = backingAttributes.remove(key)
            if (existed) notifyMutated()
            return prev
        }

        override fun clear() {
            if (backingAttributes.isNotEmpty()) {
                backingAttributes.clear()
                notifyMutated()
            }
        }
    }

    var onClick: (() -> Unit)? = null
    var onPointerEnter: ((PointerEvent) -> Unit)? = null
    var onPointerLeave: ((PointerEvent) -> Unit)? = null

    internal fun copyCommonPropertiesTo(target: SvgElement) {
        target.id = id
        target.className = className
        target.opacity = opacity
        target.fill = fill
        target.explicitFillOpacity = explicitFillOpacity
        target.explicitFillRule = explicitFillRule
        target.stroke = stroke
        target.explicitStrokeWidth = explicitStrokeWidth
        target.explicitStrokeOpacity = explicitStrokeOpacity
        target.explicitStrokeLinecap = explicitStrokeLinecap
        target.explicitStrokeLinejoin = explicitStrokeLinejoin
        target.strokeDasharray = strokeDasharray?.toList()
        target.explicitStrokeDashoffset = explicitStrokeDashoffset
        target.backingTransforms.clear()
        target.backingTransforms.addAll(backingTransforms)
        target.clipPathId = clipPathId
        target.visible = visible
        target.backingAttributes.clear()
        target.backingAttributes.putAll(backingAttributes)
        target.onClick = onClick
        target.onPointerEnter = onPointerEnter
        target.onPointerLeave = onPointerLeave
    }

    internal fun directTransformMatrix(): SvgTransform.AffineMatrix =
        if (backingTransforms.isEmpty()) SvgTransform.AffineMatrix.IDENTITY
        else SvgTransform.composeMatrix(backingTransforms)

    internal open fun findByIdRecursive(targetId: String): SvgElement? =
        if (id == targetId) this else null

    internal open fun collectByTagRecursive(targetTagLower: String, out: MutableList<SvgElement>) {
        if (tagName.equals(targetTagLower, ignoreCase = true)) {
            out.add(this)
        }
    }

    internal open fun collectAllDescendants(out: MutableList<SvgElement>) {
        out.add(this)
    }

    abstract fun cloneElement(): SvgElement
}
