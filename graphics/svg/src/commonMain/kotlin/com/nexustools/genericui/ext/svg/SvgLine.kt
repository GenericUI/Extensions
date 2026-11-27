package com.nexustools.genericui.ext.svg

/**
 * SVG `<line>` shape element (`x1`, `y1`, `x2`, `y2`).
 */
class SvgLine(
    x1: Float = 0f,
    y1: Float = 0f,
    x2: Float = 0f,
    y2: Float = 0f
) : SvgElement("line") {

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

    override fun cloneElement(): SvgLine = SvgLine(x1, y1, x2, y2).also {
        copyCommonPropertiesTo(it)
    }
}
