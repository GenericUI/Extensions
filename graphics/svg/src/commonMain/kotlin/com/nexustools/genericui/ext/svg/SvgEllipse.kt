package com.nexustools.genericui.ext.svg

/**
 * SVG `<ellipse>` shape element (`cx`, `cy`, `rx`, `ry`).
 */
class SvgEllipse(
    cx: Float = 0f,
    cy: Float = 0f,
    rx: Float = 0f,
    ry: Float = 0f
) : SvgElement("ellipse") {

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

    var rx: Float = rx.coerceAtLeast(0f)
        set(value) {
            val coerced = value.coerceAtLeast(0f)
            if (field == coerced) return
            field = coerced
            notifyMutated()
        }

    var ry: Float = ry.coerceAtLeast(0f)
        set(value) {
            val coerced = value.coerceAtLeast(0f)
            if (field == coerced) return
            field = coerced
            notifyMutated()
        }

    override fun cloneElement(): SvgEllipse = SvgEllipse(cx, cy, rx, ry).also {
        copyCommonPropertiesTo(it)
    }
}
