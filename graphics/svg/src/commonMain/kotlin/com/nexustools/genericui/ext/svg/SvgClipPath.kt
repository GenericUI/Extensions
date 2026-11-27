package com.nexustools.genericui.ext.svg

/**
 * SVG `<clipPath>` element containing child clip shapes.
 */
class SvgClipPath(
    id: String? = null,
    clipPathUnits: SvgGradientUnits = SvgGradientUnits.USER_SPACE_ON_USE
) : SvgElement("clipPath"), SvgContainer {

    init {
        this.id = id
    }

    var clipPathUnits: SvgGradientUnits = clipPathUnits
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    private val backingChildren = ArrayList<SvgElement>()

    override val children: List<SvgElement>
        get() = backingChildren.toList()

    override fun attachOwner(svg: Svg?) {
        super.attachOwner(svg)
        for (child in backingChildren) {
            child.attachOwner(svg)
        }
    }

    override fun addChild(element: SvgElement) {
        insertChild(backingChildren.size, element)
    }

    override fun insertChild(index: Int, element: SvgElement) {
        if (element.parentElement !== this) {
            element.detachFromParentContainer()
        } else {
            backingChildren.remove(element)
        }
        val clamped = index.coerceIn(0, backingChildren.size)
        backingChildren.add(clamped, element)
        element.parentElement = this
        element.attachOwner(ownerSvg)
        notifyMutated()
    }

    override fun removeChild(element: SvgElement): Boolean {
        val removed = backingChildren.remove(element)
        if (removed) {
            if (element.parentElement === this) {
                element.parentElement = null
            }
            element.attachOwner(null)
            notifyMutated()
        }
        return removed
    }

    override fun clearChildren() {
        if (backingChildren.isEmpty()) return
        for (child in backingChildren) {
            if (child.parentElement === this) {
                child.parentElement = null
            }
            child.attachOwner(null)
        }
        backingChildren.clear()
        notifyMutated()
    }

    override fun findByIdRecursive(targetId: String): SvgElement? {
        if (id == targetId) return this
        for (child in backingChildren) {
            child.findByIdRecursive(targetId)?.let { return it }
        }
        return null
    }

    override fun collectByTagRecursive(targetTagLower: String, out: MutableList<SvgElement>) {
        super.collectByTagRecursive(targetTagLower, out)
        for (child in backingChildren) {
            child.collectByTagRecursive(targetTagLower, out)
        }
    }

    override fun collectAllDescendants(out: MutableList<SvgElement>) {
        super.collectAllDescendants(out)
        for (child in backingChildren) {
            child.collectAllDescendants(out)
        }
    }

    override fun cloneElement(): SvgClipPath = SvgClipPath(id, clipPathUnits).also { copy ->
        copyCommonPropertiesTo(copy)
        for (child in backingChildren) {
            copy.addChild(child.cloneElement())
        }
    }
}
