package com.nexustools.genericui.ext.svg

import com.nexustools.genericui.style.color.RgbaColor

/**
 * SVG `fill` / `stroke` paint value: [None], [CurrentColor], [SolidColor], or [GradientRef].
 */
sealed interface SvgPaint {
    fun toSvgString(): String

    data object None : SvgPaint {
        override fun toSvgString(): String = "none"
    }

    data object CurrentColor : SvgPaint {
        override fun toSvgString(): String = "currentColor"
    }

    data class SolidColor(val color: RgbaColor) : SvgPaint {
        override fun toSvgString(): String = color.toHex(includeAlpha = color.a < 255)
    }

    data class GradientRef(val id: String, val fallbackColor: RgbaColor? = null) : SvgPaint {
        val cleanId: String
            get() = normalizeId(id)

        override fun toSvgString(): String = if (fallbackColor != null) {
            "url(#$cleanId) ${fallbackColor.toHex(includeAlpha = fallbackColor.a < 255)}"
        } else {
            "url(#$cleanId)"
        }
    }

    companion object {
        fun normalizeId(rawId: String): String =
            rawId.trim()
                .removePrefix("url(")
                .removeSuffix(")")
                .trim()
                .trim('\'', '"')
                .removePrefix("#")

        fun parse(raw: String?): SvgPaint? {
            val trimmed = raw?.trim() ?: return null
            if (trimmed.isEmpty()) return null
            val lower = trimmed.lowercase()
            if (lower == "none") return None
            if (lower == "currentcolor") return CurrentColor
            if (lower == "inherit") return null

            if (lower.startsWith("url(")) {
                val closeIdx = trimmed.indexOf(')')
                if (closeIdx > 4) {
                    val inner = trimmed.substring(4, closeIdx).trim().trim('\'', '"')
                    val refId = normalizeId(inner)
                    val trailing = trimmed.substring(closeIdx + 1).trim()
                    val fallback = if (trailing.isNotEmpty() && !trailing.equals("none", ignoreCase = true)) {
                        SvgColorParser.parse(trailing)
                    } else {
                        null
                    }
                    if (refId.isNotEmpty()) {
                        return GradientRef(refId, fallback)
                    }
                }
            }

            val parsedColor = SvgColorParser.parse(trimmed) ?: return null
            return SolidColor(parsedColor)
        }
    }
}
