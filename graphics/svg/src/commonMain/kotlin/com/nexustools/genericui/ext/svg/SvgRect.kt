package com.nexustools.genericui.ext.svg

/**
 * SVG `<rect>` shape element (`x`, `y`, `width`, `height`, `rx`, `ry`).
 */
class SvgRect(
    x: Float = 0f,
    y: Float = 0f,
    width: Float = 0f,
    height: Float = 0f,
    rx: Float = 0f,
    ry: Float = 0f
) : SvgElement("rect") {

    var x: Float = x
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    var y: Float = y
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    var width: Float = width.coerceAtLeast(0f)
        set(value) {
            val coerced = value.coerceAtLeast(0f)
            if (field == coerced) return
            field = coerced
            notifyMutated()
        }

    var height: Float = height.coerceAtLeast(0f)
        set(value) {
            val coerced = value.coerceAtLeast(0f)
            if (field == coerced) return
            field = coerced
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

    override fun cloneElement(): SvgRect = SvgRect(x, y, width, height, rx, ry).also {
        copyCommonPropertiesTo(it)
    }
}
