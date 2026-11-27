package com.nexustools.genericui.ext.svg

/**
 * SVG `<circle>` shape element (`cx`, `cy`, `r`).
 */
class SvgCircle(
    cx: Float = 0f,
    cy: Float = 0f,
    r: Float = 0f
) : SvgElement("circle") {

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

    override fun cloneElement(): SvgCircle = SvgCircle(cx, cy, r).also {
        copyCommonPropertiesTo(it)
    }
}
