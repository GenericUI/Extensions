package com.nexustools.genericui.ext.engine.svg

/**
 * Base vector shape attached to a [Bone2D] via [SvgBonePart].
 */
sealed interface SvgShapePrimitive {
    fun toSvgElementXml(fillHex: String, strokeHex: String?, strokeWidth: Float): String
}
