package com.nexustools.genericui.ext.svg

/**
 * SVG `stroke-linecap` styles (`butt`, `round`, `square`).
 */
enum class SvgStrokeLinecap(val xmlValue: String) {
    BUTT("butt"),
    ROUND("round"),
    SQUARE("square");

    companion object {
        fun fromXml(raw: String?): SvgStrokeLinecap? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.xmlValue == cleaned }
        }
    }
}
