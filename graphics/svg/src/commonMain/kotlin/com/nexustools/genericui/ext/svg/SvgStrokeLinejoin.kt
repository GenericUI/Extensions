package com.nexustools.genericui.ext.svg

/**
 * SVG `stroke-linejoin` styles (`miter`, `round`, `bevel`).
 */
enum class SvgStrokeLinejoin(val xmlValue: String) {
    MITER("miter"),
    ROUND("round"),
    BEVEL("bevel");

    companion object {
        fun fromXml(raw: String?): SvgStrokeLinejoin? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.xmlValue == cleaned }
        }
    }
}
