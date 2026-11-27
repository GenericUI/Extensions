package com.nexustools.genericui.ext.svg

/**
 * SVG `<polygon>` closed shape element (`points`).
 */
class SvgPolygon(
    points: List<SvgPoint> = emptyList()
) : SvgElement("polygon") {

    var points: List<SvgPoint> = points.toList()
        set(value) {
            val copy = value.toList()
            if (field == copy) return
            field = copy
            notifyMutated()
        }

    override fun cloneElement(): SvgPolygon = SvgPolygon(points).also {
        copyCommonPropertiesTo(it)
    }
}
