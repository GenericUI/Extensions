package com.nexustools.genericui.ext.svg

/**
 * SVG `<text>` element (`x`, `y`, `text`, `fontSize`, `fontFamily`, `fontWeight`, `textAnchor`).
 */
class SvgText(
    x: Float = 0f,
    y: Float = 0f,
    text: String = "",
    fontSize: Float = 16f,
    fontFamily: String = "sans-serif",
    fontWeight: String = "normal",
    textAnchor: String = "start"
) : SvgElement("text") {

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

    var text: String = text
        set(value) {
            if (field == value) return
            field = value
            notifyMutated()
        }

    internal var explicitFontSize: Float? = if (fontSize != 16f) fontSize.coerceAtLeast(0.1f) else null
    var fontSize: Float
        get() = explicitFontSize ?: 16f
        set(value) {
            val coerced = value.coerceAtLeast(0.1f)
            if (explicitFontSize == coerced) return
            explicitFontSize = coerced
            notifyMutated()
        }

    internal var explicitFontFamily: String? = if (fontFamily != "sans-serif") fontFamily else null
    var fontFamily: String
        get() = explicitFontFamily ?: "sans-serif"
        set(value) {
            val trimmed = value.trim().ifEmpty { "sans-serif" }
            if (explicitFontFamily == trimmed) return
            explicitFontFamily = trimmed
            notifyMutated()
        }

    internal var explicitFontWeight: String? = if (fontWeight != "normal") fontWeight else null
    var fontWeight: String
        get() = explicitFontWeight ?: "normal"
        set(value) {
            val trimmed = value.trim().ifEmpty { "normal" }
            if (explicitFontWeight == trimmed) return
            explicitFontWeight = trimmed
            notifyMutated()
        }

    internal var explicitTextAnchor: String? = if (textAnchor != "start") textAnchor else null
    var textAnchor: String
        get() = explicitTextAnchor ?: "start"
        set(value) {
            val norm = value.trim().lowercase().ifEmpty { "start" }
            if (explicitTextAnchor == norm) return
            explicitTextAnchor = norm
            notifyMutated()
        }

    override fun cloneElement(): SvgText = SvgText(
        x = x,
        y = y,
        text = text,
        fontSize = fontSize,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        textAnchor = textAnchor
    ).also { copy ->
        copyCommonPropertiesTo(copy)
        copy.explicitFontSize = explicitFontSize
        copy.explicitFontFamily = explicitFontFamily
        copy.explicitFontWeight = explicitFontWeight
        copy.explicitTextAnchor = explicitTextAnchor
    }
}
