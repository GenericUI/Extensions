package com.nexustools.genericui.ext.svg

/**
 * SVG `<radialGradient>` paint server element.
 */
class SvgRadialGradient(
    id: String? = null,
    cx: Float = 0.5f,
    cy: Float = 0.5f,
    r: Float = 0.5f,
    fx: Float? = null,
    fy: Float? = null,
    gradientUnits: SvgGradientUnits = SvgGradientUnits.OBJECT_BOUNDING_BOX,
    stops: List<SvgGradientStop> = emptyList(),
    spreadMethod: String = "pad"
) : SvgElement("radialGradient") {

    init {
        this.id = id
    }

    var cx: Float = cx
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    var cy: Float = cy
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    var r: Float = r.coerceAtLeast(0f)
        set(value) {
            val coerced = value.coerceAtLeast(0f)
            if (field == coerced) return
            field = coerced
            notifyMutated()
        }

    var fx: Float? = fx
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    var fy: Float? = fy
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    var gradientUnits: SvgGradientUnits = gradientUnits
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    var spreadMethod: String = spreadMethod
        set(value) {
            val norm = value.trim().lowercase().ifEmpty { "pad" }
            if (field == norm) return
            field = norm
            notifyMutated()
        }

    private val backingGradientTransforms = ArrayList<SvgTransform>()

    val gradientTransform: MutableList<SvgTransform> = object : AbstractMutableList<SvgTransform>() {
        override val size: Int get() = backingGradientTransforms.size
        override fun get(index: Int): SvgTransform = backingGradientTransforms[index]
        override fun add(index: Int, element: SvgTransform) {
            backingGradientTransforms.add(index, element)
            notifyMutated()
        }
        override fun removeAt(index: Int): SvgTransform {
            val r = backingGradientTransforms.removeAt(index)
            notifyMutated()
            return r
        }
        override fun set(index: Int, element: SvgTransform): SvgTransform {
            val prev = backingGradientTransforms.set(index, element)
            if (prev != element) notifyMutated()
            return prev
        }
    }

    private val backingStops = ArrayList<SvgGradientStop>().apply {
        for (s in stops) {
            s.ownerGradient = this@SvgRadialGradient
            add(s)
        }
    }

    val stops: MutableList<SvgGradientStop> = object : AbstractMutableList<SvgGradientStop>() {
        override val size: Int get() = backingStops.size
        override fun get(index: Int): SvgGradientStop = backingStops[index]
        override fun add(index: Int, element: SvgGradientStop) {
            element.ownerGradient = this@SvgRadialGradient
            backingStops.add(index, element)
            notifyMutated()
        }
        override fun removeAt(index: Int): SvgGradientStop {
            val removed = backingStops.removeAt(index)
            if (removed.ownerGradient === this@SvgRadialGradient) {
                removed.ownerGradient = null
            }
            notifyMutated()
            return removed
        }
        override fun set(index: Int, element: SvgGradientStop): SvgGradientStop {
            element.ownerGradient = this@SvgRadialGradient
            val prev = backingStops.set(index, element)
            if (prev !== element && prev.ownerGradient === this@SvgRadialGradient) {
                prev.ownerGradient = null
            }
            notifyMutated()
            return prev
        }
    }

    fun addStop(stop: SvgGradientStop): SvgGradientStop {
        stops.add(stop)
        return stop
    }

    fun addStop(
        offset: Float,
        color: com.nexustools.genericui.style.color.RgbaColor,
        opacity: Float = 1f
    ): SvgGradientStop = addStop(SvgGradientStop(offset, color, opacity))

    override fun cloneElement(): SvgRadialGradient = SvgRadialGradient(
        id = id,
        cx = cx,
        cy = cy,
        r = r,
        fx = fx,
        fy = fy,
        gradientUnits = gradientUnits,
        stops = backingStops.map { it.copy() },
        spreadMethod = spreadMethod
    ).also { copy ->
        copyCommonPropertiesTo(copy)
        copy.backingGradientTransforms.addAll(backingGradientTransforms)
    }
}
