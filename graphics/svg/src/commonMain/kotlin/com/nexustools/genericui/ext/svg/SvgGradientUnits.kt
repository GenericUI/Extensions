package com.nexustools.genericui.ext.svg

/**
 * SVG coordinate unit spaces for `<linearGradient>`, `<radialGradient>`, and `<clipPath>`.
 */
enum class SvgGradientUnits(val xmlValue: String) {
    OBJECT_BOUNDING_BOX("objectBoundingBox"),
    USER_SPACE_ON_USE("userSpaceOnUse");

    companion object {
        fun fromXml(raw: String?): SvgGradientUnits? {
            val cleaned = raw?.trim() ?: return null
            return entries.firstOrNull { it.xmlValue.equals(cleaned, ignoreCase = true) }
        }
    }
}
