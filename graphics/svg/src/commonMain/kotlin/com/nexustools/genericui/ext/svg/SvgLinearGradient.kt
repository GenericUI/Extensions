package com.nexustools.genericui.ext.svg

/**
 * SVG `<linearGradient>` paint server element.
 */
class SvgLinearGradient(
    id: String? = null,
    x1: Float = 0f,
    y1: Float = 0f,
    x2: Float = 1f,
    y2: Float = 0f,
    gradientUnits: SvgGradientUnits = SvgGradientUnits.OBJECT_BOUNDING_BOX,
    stops: List<SvgGradientStop> = emptyList(),
    spreadMethod: String = "pad"
) : SvgElement("linearGradient") {

    init {
        this.id = id
    }

    var x1: Float = x1
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    var y1: Float = y1
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    var x2: Float = x2
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    var y2: Float = y2
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
            s.ownerGradient = this@SvgLinearGradient
            add(s)
        }
    }

    val stops: MutableList<SvgGradientStop> = object : AbstractMutableList<SvgGradientStop>() {
        override val size: Int get() = backingStops.size
        override fun get(index: Int): SvgGradientStop = backingStops[index]
        override fun add(index: Int, element: SvgGradientStop) {
            element.ownerGradient = this@SvgLinearGradient
            backingStops.add(index, element)
            notifyMutated()
        }
        override fun removeAt(index: Int): SvgGradientStop {
            val removed = backingStops.removeAt(index)
            if (removed.ownerGradient === this@SvgLinearGradient) {
                removed.ownerGradient = null
            }
            notifyMutated()
            return removed
        }
        override fun set(index: Int, element: SvgGradientStop): SvgGradientStop {
            element.ownerGradient = this@SvgLinearGradient
            val prev = backingStops.set(index, element)
            if (prev !== element && prev.ownerGradient === this@SvgLinearGradient) {
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

    override fun cloneElement(): SvgLinearGradient = SvgLinearGradient(
        id = id,
        x1 = x1,
        y1 = y1,
        x2 = x2,
        y2 = y2,
        gradientUnits = gradientUnits,
        stops = backingStops.map { it.copy() },
        spreadMethod = spreadMethod
    ).also { copy ->
        copyCommonPropertiesTo(copy)
        copy.backingGradientTransforms.addAll(backingGradientTransforms)
    }
}
