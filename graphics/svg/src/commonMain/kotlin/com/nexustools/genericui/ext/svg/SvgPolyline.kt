package com.nexustools.genericui.ext.svg

/**
 * SVG `<polyline>` open shape element (`points`).
 */
class SvgPolyline(
    points: List<SvgPoint> = emptyList()
) : SvgElement("polyline") {

    var points: List<SvgPoint> = points.toList()
        set(value) {
            val copy = value.toList()
            if (field == copy) return
            field = copy
            notifyMutated()
        }

    override fun cloneElement(): SvgPolyline = SvgPolyline(points).also {
        copyCommonPropertiesTo(it)
    }
}
