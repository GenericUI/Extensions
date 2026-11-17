package com.nexustools.genericui.ext.engine.svg

import com.nexustools.genericui.ext.engine.math.Vec2f

/**
 * SVG closed polygon primitive defined by vertices in local bone space.
 */
data class SvgPolygonPrimitive(
    val points: List<Vec2f>
) : SvgShapePrimitive {
    override fun toSvgElementXml(fillHex: String, strokeHex: String?, strokeWidth: Float): String {
        val strokeAttr = if (strokeHex != null) {
            " stroke=\"$strokeHex\" stroke-width=\"$strokeWidth\""
        } else {
            " stroke=\"none\""
        }
        val pts = points.joinToString(" ") { "${it.x},${it.y}" }
        return "<polygon points=\"$pts\" fill=\"$fillHex\"$strokeAttr />"
    }
}
