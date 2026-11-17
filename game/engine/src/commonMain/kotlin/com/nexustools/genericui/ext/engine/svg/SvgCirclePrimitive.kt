package com.nexustools.genericui.ext.engine.svg

/**
 * SVG circle primitive in local bone space (`cx, cy, radius`).
 */
data class SvgCirclePrimitive(
    val cx: Float = 0f,
    val cy: Float = 0f,
    val radius: Float = 10f
) : SvgShapePrimitive {
    override fun toSvgElementXml(fillHex: String, strokeHex: String?, strokeWidth: Float): String {
        val strokeAttr = if (strokeHex != null) {
            " stroke=\"$strokeHex\" stroke-width=\"$strokeWidth\""
        } else {
            " stroke=\"none\""
        }
        return "<circle cx=\"$cx\" cy=\"$cy\" r=\"$radius\" fill=\"$fillHex\"$strokeAttr />"
    }
}
