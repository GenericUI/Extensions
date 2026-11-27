package com.nexustools.genericui.ext.svg

/**
 * SVG `<use>` element referencing another element by `href` (`#id`) and translating by `(x, y)`.
 */
class SvgUse(
    href: String = "",
    x: Float = 0f,
    y: Float = 0f,
    width: Float? = null,
    height: Float? = null
) : SvgElement("use") {

    var href: String = href
        set(value) {
            val trimmed = value.trim()
            if (field == trimmed) return
            field = trimmed
            notifyMutated()
        }

    val targetId: String
        get() = SvgPaint.normalizeId(href)

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

    var width: Float? = width
        set(value) {
            val coerced = value?.coerceAtLeast(0f)
            if (field == coerced) return
            field = coerced
            notifyMutated()
        }

    var height: Float? = height
        set(value) {
            val coerced = value?.coerceAtLeast(0f)
            if (field == coerced) return
            field = coerced
            notifyMutated()
        }

    override fun cloneElement(): SvgUse = SvgUse(href, x, y, width, height).also {
        copyCommonPropertiesTo(it)
    }
}
