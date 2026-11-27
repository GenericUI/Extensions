package com.nexustools.genericui.ext.svg

/**
 * SVG `fill-rule` / `clip-rule` winding policies (`nonzero`, `evenodd`).
 */
enum class SvgFillRule(val xmlValue: String) {
    NONZERO("nonzero"),
    EVENODD("evenodd");

    companion object {
        fun fromXml(raw: String?): SvgFillRule? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.xmlValue == cleaned }
        }
    }
}
