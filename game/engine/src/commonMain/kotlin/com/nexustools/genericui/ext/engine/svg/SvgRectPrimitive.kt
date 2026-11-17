package com.nexustools.genericui.ext.engine.svg

/**
 * SVG rectangle primitive in local bone space with optional corner radius (`rx`).
 */
data class SvgRectPrimitive(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 10f,
    val height: Float = 10f,
    val cornerRadius: Float = 0f
) : SvgShapePrimitive {
    override fun toSvgElementXml(fillHex: String, strokeHex: String?, strokeWidth: Float): String {
        val strokeAttr = if (strokeHex != null) {
            " stroke=\"$strokeHex\" stroke-width=\"$strokeWidth\""
        } else {
            " stroke=\"none\""
        }
        val rxAttr = if (cornerRadius > 0f) " rx=\"$cornerRadius\" ry=\"$cornerRadius\"" else ""
        return "<rect x=\"$x\" y=\"$y\" width=\"$width\" height=\"$height\"$rxAttr fill=\"$fillHex\"$strokeAttr />"
    }
}
