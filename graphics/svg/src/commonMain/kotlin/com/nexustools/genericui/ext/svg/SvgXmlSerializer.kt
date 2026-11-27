package com.nexustools.genericui.ext.svg

/**
 * Serializes a mutable [Svg] scene graph cleanly back into valid W3C SVG XML markup, and provides
 * structured attribute lists shared by DOM backends such as [com.nexustools.genericui.ext.svg.web.WebSvgNativeImpl].
 */
object SvgXmlSerializer {

    fun serialize(svg: Svg, pretty: Boolean = false): String = buildString {
        append("<svg")
        for ((k, v) in collectRootAttributes(svg)) {
            appendAttr(k, v)
        }
        val hasDefs = svg.defs.children.isNotEmpty()
        val hasChildren = svg.children.isNotEmpty()
        if (!hasDefs && !hasChildren) {
            append("/>")
            return@buildString
        }
        append(">")
        if (hasDefs) {
            if (pretty) append('\n')
            serializeElement(svg.defs, this, if (pretty) 1 else -1)
        }
        for (child in svg.children) {
            if (pretty) append('\n')
            serializeElement(child, this, if (pretty) 1 else -1)
        }
        if (pretty) append('\n')
        append("</svg>")
    }

    internal fun collectRootAttributes(svg: Svg): List<Pair<String, String>> {
        val attrs = ArrayList<Pair<String, String>>()
        attrs.add("xmlns" to svg.xmlns)
        svg.id?.takeIf { it.isNotEmpty() }?.let { attrs.add("id" to it) }
        svg.getClass().takeIf { it.isNotEmpty() }?.let { attrs.add("class" to it) }
        svg.svgWidth?.let { attrs.add("width" to SvgPoint.formatSvgNumber(it)) }
        svg.svgHeight?.let { attrs.add("height" to SvgPoint.formatSvgNumber(it)) }
        svg.viewBox?.let { attrs.add("viewBox" to it.toSvgString()) }
        if (svg.preserveAspectRatio != SvgPreserveAspectRatio.DEFAULT) {
            attrs.add("preserveAspectRatio" to svg.preserveAspectRatio.toSvgString())
        }
        for ((k, v) in svg.getAttributes()) {
            if (k !in ROOT_MANAGED_ATTRS && !k.startsWith("data-genericui-")) {
                attrs.add(k to v)
            }
        }
        return attrs
    }

    internal fun collectElementAttributes(el: SvgElement): List<Pair<String, String>> {
        val attrs = ArrayList<Pair<String, String>>()
        el.id?.takeIf { it.isNotEmpty() }?.let { attrs.add("id" to it) }
        el.className.takeIf { it.isNotEmpty() }?.let { attrs.add("class" to it) }

        when (el) {
            is SvgRect -> {
                if (el.x != 0f) attrs.add("x" to SvgPoint.formatSvgNumber(el.x))
                if (el.y != 0f) attrs.add("y" to SvgPoint.formatSvgNumber(el.y))
                attrs.add("width" to SvgPoint.formatSvgNumber(el.width))
                attrs.add("height" to SvgPoint.formatSvgNumber(el.height))
                if (el.rx > 0f) attrs.add("rx" to SvgPoint.formatSvgNumber(el.rx))
                if (el.ry > 0f) attrs.add("ry" to SvgPoint.formatSvgNumber(el.ry))
            }
            is SvgCircle -> {
                attrs.add("cx" to SvgPoint.formatSvgNumber(el.cx))
                attrs.add("cy" to SvgPoint.formatSvgNumber(el.cy))
                attrs.add("r" to SvgPoint.formatSvgNumber(el.r))
            }
            is SvgEllipse -> {
                attrs.add("cx" to SvgPoint.formatSvgNumber(el.cx))
                attrs.add("cy" to SvgPoint.formatSvgNumber(el.cy))
                attrs.add("rx" to SvgPoint.formatSvgNumber(el.rx))
                attrs.add("ry" to SvgPoint.formatSvgNumber(el.ry))
            }
            is SvgLine -> {
                attrs.add("x1" to SvgPoint.formatSvgNumber(el.x1))
                attrs.add("y1" to SvgPoint.formatSvgNumber(el.y1))
                attrs.add("x2" to SvgPoint.formatSvgNumber(el.x2))
                attrs.add("y2" to SvgPoint.formatSvgNumber(el.y2))
            }
            is SvgPolyline -> {
                attrs.add("points" to SvgPoint.formatPoints(el.points))
            }
            is SvgPolygon -> {
                attrs.add("points" to SvgPoint.formatPoints(el.points))
            }
            is SvgPath -> {
                attrs.add("d" to el.d)
            }
            is SvgText -> {
                attrs.add("x" to SvgPoint.formatSvgNumber(el.x))
                attrs.add("y" to SvgPoint.formatSvgNumber(el.y))
                el.explicitFontSize?.let { attrs.add("font-size" to SvgPoint.formatSvgNumber(it)) }
                el.explicitFontFamily?.let { attrs.add("font-family" to it) }
                el.explicitFontWeight?.let { attrs.add("font-weight" to it) }
                el.explicitTextAnchor?.let { attrs.add("text-anchor" to it) }
            }
            is SvgUse -> {
                if (el.href.isNotEmpty()) attrs.add("href" to el.href)
                if (el.x != 0f) attrs.add("x" to SvgPoint.formatSvgNumber(el.x))
                if (el.y != 0f) attrs.add("y" to SvgPoint.formatSvgNumber(el.y))
                el.width?.let { attrs.add("width" to SvgPoint.formatSvgNumber(it)) }
                el.height?.let { attrs.add("height" to SvgPoint.formatSvgNumber(it)) }
            }
            is SvgClipPath -> {
                if (el.clipPathUnits != SvgGradientUnits.USER_SPACE_ON_USE) {
                    attrs.add("clipPathUnits" to el.clipPathUnits.xmlValue)
                }
            }
            is SvgLinearGradient -> {
                attrs.add("x1" to SvgPoint.formatSvgNumber(el.x1))
                attrs.add("y1" to SvgPoint.formatSvgNumber(el.y1))
                attrs.add("x2" to SvgPoint.formatSvgNumber(el.x2))
                attrs.add("y2" to SvgPoint.formatSvgNumber(el.y2))
                if (el.gradientUnits != SvgGradientUnits.OBJECT_BOUNDING_BOX) {
                    attrs.add("gradientUnits" to el.gradientUnits.xmlValue)
                }
                if (el.spreadMethod != "pad") {
                    attrs.add("spreadMethod" to el.spreadMethod)
                }
                if (el.gradientTransform.isNotEmpty()) {
                    attrs.add("gradientTransform" to SvgTransform.formatTransformList(el.gradientTransform))
                }
            }
            is SvgRadialGradient -> {
                attrs.add("cx" to SvgPoint.formatSvgNumber(el.cx))
                attrs.add("cy" to SvgPoint.formatSvgNumber(el.cy))
                attrs.add("r" to SvgPoint.formatSvgNumber(el.r))
                el.fx?.let { attrs.add("fx" to SvgPoint.formatSvgNumber(it)) }
                el.fy?.let { attrs.add("fy" to SvgPoint.formatSvgNumber(it)) }
                if (el.gradientUnits != SvgGradientUnits.OBJECT_BOUNDING_BOX) {
                    attrs.add("gradientUnits" to el.gradientUnits.xmlValue)
                }
                if (el.spreadMethod != "pad") {
                    attrs.add("spreadMethod" to el.spreadMethod)
                }
                if (el.gradientTransform.isNotEmpty()) {
                    attrs.add("gradientTransform" to SvgTransform.formatTransformList(el.gradientTransform))
                }
            }
        }

        if (el.opacity < 0.9999f) {
            attrs.add("opacity" to SvgPoint.formatSvgNumber(el.opacity))
        }
        el.fill?.let { attrs.add("fill" to it.toSvgString()) }
        el.explicitFillOpacity?.let { attrs.add("fill-opacity" to SvgPoint.formatSvgNumber(it)) }
        el.explicitFillRule?.let { attrs.add("fill-rule" to it.xmlValue) }
        el.stroke?.let { attrs.add("stroke" to it.toSvgString()) }
        el.explicitStrokeWidth?.let { attrs.add("stroke-width" to SvgPoint.formatSvgNumber(it)) }
        el.explicitStrokeOpacity?.let { attrs.add("stroke-opacity" to SvgPoint.formatSvgNumber(it)) }
        el.explicitStrokeLinecap?.let { attrs.add("stroke-linecap" to it.xmlValue) }
        el.explicitStrokeLinejoin?.let { attrs.add("stroke-linejoin" to it.xmlValue) }
        el.strokeDasharray?.let { arr ->
            attrs.add("stroke-dasharray" to arr.joinToString(",") { SvgPoint.formatSvgNumber(it) })
        }
        el.explicitStrokeDashoffset?.let { attrs.add("stroke-dashoffset" to SvgPoint.formatSvgNumber(it)) }
        if (el.transformList.isNotEmpty()) {
            attrs.add("transform" to SvgTransform.formatTransformList(el.transformList))
        }
        el.clipPathId?.takeIf { it.isNotEmpty() }?.let { attrs.add("clip-path" to "url(#$it)") }
        if (!el.visible) {
            attrs.add("display" to "none")
        }
        for ((k, v) in el.attributes) {
            if (k !in ELEMENT_MANAGED_ATTRS) {
                attrs.add(k to v)
            }
        }
        return attrs
    }

    internal fun collectStopAttributes(stop: SvgGradientStop): List<Pair<String, String>> {
        val attrs = ArrayList<Pair<String, String>>(3)
        attrs.add("offset" to SvgPoint.formatSvgNumber(stop.offset))
        attrs.add("stop-color" to stop.color.toHex(includeAlpha = stop.color.a < 255))
        if (stop.opacity < 0.9999f) {
            attrs.add("stop-opacity" to SvgPoint.formatSvgNumber(stop.opacity))
        }
        return attrs
    }

    private fun serializeElement(el: SvgElement, out: StringBuilder, indentLevel: Int) {
        if (indentLevel > 0) {
            repeat(indentLevel) { out.append("  ") }
        }
        out.append('<').append(el.tagName)
        for ((k, v) in collectElementAttributes(el)) {
            out.appendAttr(k, v)
        }

        when (el) {
            is SvgText -> {
                out.append('>').append(escapeXmlText(el.text)).append("</text>")
            }
            is SvgLinearGradient -> {
                if (el.stops.isEmpty()) {
                    out.append("/>")
                } else {
                    out.append('>')
                    for (stop in el.stops) {
                        if (indentLevel >= 0) {
                            out.append('\n')
                            repeat(indentLevel + 1) { out.append("  ") }
                        }
                        serializeStop(stop, out)
                    }
                    if (indentLevel >= 0) {
                        out.append('\n')
                        repeat(indentLevel) { out.append("  ") }
                    }
                    out.append("</linearGradient>")
                }
            }
            is SvgRadialGradient -> {
                if (el.stops.isEmpty()) {
                    out.append("/>")
                } else {
                    out.append('>')
                    for (stop in el.stops) {
                        if (indentLevel >= 0) {
                            out.append('\n')
                            repeat(indentLevel + 1) { out.append("  ") }
                        }
                        serializeStop(stop, out)
                    }
                    if (indentLevel >= 0) {
                        out.append('\n')
                        repeat(indentLevel) { out.append("  ") }
                    }
                    out.append("</radialGradient>")
                }
            }
            is SvgContainer -> {
                val children = el.children
                if (children.isEmpty()) {
                    out.append("/>")
                } else {
                    out.append('>')
                    for (child in children) {
                        if (indentLevel >= 0) out.append('\n')
                        serializeElement(child, out, if (indentLevel >= 0) indentLevel + 1 else -1)
                    }
                    if (indentLevel >= 0) {
                        out.append('\n')
                        repeat(indentLevel) { out.append("  ") }
                    }
                    out.append("</").append(el.tagName).append('>')
                }
            }
            else -> out.append("/>")
        }
    }

    private fun serializeStop(stop: SvgGradientStop, out: StringBuilder) {
        out.append("<stop")
        for ((k, v) in collectStopAttributes(stop)) {
            out.appendAttr(k, v)
        }
        out.append("/>")
    }

    private fun StringBuilder.appendAttr(name: String, value: String) {
        append(' ').append(name).append("=\"").append(escapeXmlAttr(value)).append('"')
    }

    internal fun escapeXmlText(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    internal fun escapeXmlAttr(value: String): String =
        escapeXmlText(value)
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private val ROOT_MANAGED_ATTRS = setOf(
        "xmlns", "id", "class", "width", "height", "viewBox", "preserveAspectRatio"
    )

    private val ELEMENT_MANAGED_ATTRS = setOf(
        "id", "class", "opacity", "fill", "fill-opacity", "fill-rule",
        "stroke", "stroke-width", "stroke-opacity", "stroke-linecap", "stroke-linejoin",
        "stroke-dasharray", "stroke-dashoffset", "transform", "clip-path", "display",
        "x", "y", "width", "height", "rx", "ry", "cx", "cy", "r", "fx", "fy",
        "x1", "y1", "x2", "y2", "points", "d", "font-size", "font-family", "font-weight",
        "text-anchor", "href", "xlink:href", "clipPathUnits", "gradientUnits",
        "spreadMethod", "gradientTransform", "style"
    )
}
