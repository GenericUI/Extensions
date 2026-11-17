package com.nexustools.genericui.ext.engine.svg

/**
 * SVG `<path d="...">` primitive in local bone space.
 */
data class SvgPathPrimitive(
    val pathData: String
) : SvgShapePrimitive {
    override fun toSvgElementXml(fillHex: String, strokeHex: String?, strokeWidth: Float): String {
        val strokeAttr = if (strokeHex != null) {
            " stroke=\"$strokeHex\" stroke-width=\"$strokeWidth\""
        } else {
            " stroke=\"none\""
        }
        val escaped = pathData.replace("\"", "&quot;")
        return "<path d=\"$escaped\" fill=\"$fillHex\"$strokeAttr />"
    }
}
