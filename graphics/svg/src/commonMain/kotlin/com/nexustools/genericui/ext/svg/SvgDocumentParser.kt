package com.nexustools.genericui.ext.svg

import com.nexustools.genericui.style.color.RgbaColor

/**
 * Pure-Kotlin W3C SVG XML document parser converting `<svg ...>...</svg>` markup into a live
 * mutable [Svg] DOM hierarchy (`<defs>`, gradients, `<clipPath>`, `<g>`, shapes, `<path>`,
 * `<text>`, `<use>`, presentation attributes, and inline CSS `style="..."` properties).
 */
object SvgDocumentParser {

    fun parse(svgMarkup: String, target: Svg = Svg()): Svg {
        val nodes = parseXmlNodes(svgMarkup)
        val svgRootNode = findSvgRootNode(nodes)
            ?: throw IllegalArgumentException("Invalid SVG markup: missing <svg> root element")

        target.clearChildren()
        target.defs.clearChildren()
        target.viewBox = null
        target.svgWidth = null
        target.svgHeight = null
        target.preserveAspectRatio = SvgPreserveAspectRatio.DEFAULT
        target.xmlns = "http://www.w3.org/2000/svg"
        target.id = null
        target.setClass("")

        val rootAttrs = svgRootNode.attrs
        val rootStyles = parseStyleAttribute(rootAttrs["style"])
        rootAttrs["xmlns"]?.takeIf { it.isNotBlank() }?.let { target.xmlns = it.trim() }
        rootAttrs["id"]?.takeIf { it.isNotBlank() }?.let { target.id = it.trim() }
        rootAttrs["class"]?.takeIf { it.isNotBlank() }?.let { target.setClass(it) }
        SvgViewBox.parse(rootAttrs["viewBox"] ?: rootAttrs["viewbox"])?.let { target.viewBox = it }
        SvgPreserveAspectRatio.parse(rootAttrs["preserveAspectRatio"] ?: rootAttrs["preserveaspectratio"])?.let {
            target.preserveAspectRatio = it
        }

        val widthRaw = rootStyles["width"] ?: rootAttrs["width"]
        val heightRaw = rootStyles["height"] ?: rootAttrs["height"]
        widthRaw?.takeIf { !it.trim().endsWith("%") }?.let { raw ->
            parseSvgLength(raw, reference = target.viewBox?.width ?: 300f)?.let { target.svgWidth = it }
        }
        heightRaw?.takeIf { !it.trim().endsWith("%") }?.let { raw ->
            parseSvgLength(raw, reference = target.viewBox?.height ?: 150f)?.let { target.svgHeight = it }
        }

        for ((k, v) in rootAttrs) {
            if (k !in ROOT_HANDLED_ATTRS) {
                target.setAttribute(k, v)
            }
        }

        val refWidth = target.viewBox?.width ?: target.svgWidth ?: 300f
        val refHeight = target.viewBox?.height ?: target.svgHeight ?: 150f

        for (childNode in svgRootNode.children) {
            if (childNode.tagName.equals("defs", ignoreCase = true)) {
                for (defChild in childNode.children) {
                    buildElement(defChild, refWidth, refHeight)?.let { target.defs.addChild(it) }
                }
            } else {
                buildElement(childNode, refWidth, refHeight)?.let { target.addChild(it) }
            }
        }
        return target
    }

    private fun findSvgRootNode(nodes: List<XmlElementNode>): XmlElementNode? {
        for (node in nodes) {
            if (node.tagName.equals("svg", ignoreCase = true) ||
                node.tagName.substringAfter(':').equals("svg", ignoreCase = true)
            ) {
                return node
            }
            findSvgRootNode(node.children)?.let { return it }
        }
        return null
    }

    private fun buildElement(node: XmlElementNode, refW: Float, refH: Float): SvgElement? {
        val localTag = node.tagName.substringAfter(':').lowercase()
        val attrs = node.attrs
        val styles = parseStyleAttribute(attrs["style"])

        fun attr(name: String): String? =
            styles[name] ?: attrs[name] ?: attrs.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        val element: SvgElement = when (localTag) {
            "g" -> SvgGroup().also { grp ->
                for (child in node.children) {
                    if (child.tagName.equals("defs", ignoreCase = true)) {
                        for (dc in child.children) {
                            buildElement(dc, refW, refH)?.let { grp.addChild(it) }
                        }
                    } else {
                        buildElement(child, refW, refH)?.let { grp.addChild(it) }
                    }
                }
            }
            "defs" -> SvgDefs().also { defs ->
                for (child in node.children) {
                    buildElement(child, refW, refH)?.let { defs.addChild(it) }
                }
            }
            "rect" -> SvgRect(
                x = parseSvgLength(attr("x"), refW) ?: 0f,
                y = parseSvgLength(attr("y"), refH) ?: 0f,
                width = parseSvgLength(attr("width"), refW) ?: 0f,
                height = parseSvgLength(attr("height"), refH) ?: 0f,
                rx = parseSvgLength(attr("rx"), refW) ?: 0f,
                ry = parseSvgLength(attr("ry"), refH) ?: 0f
            ).also { rect ->
                if (attr("rx") != null && attr("ry") == null) rect.ry = rect.rx
                if (attr("ry") != null && attr("rx") == null) rect.rx = rect.ry
            }
            "circle" -> SvgCircle(
                cx = parseSvgLength(attr("cx"), refW) ?: 0f,
                cy = parseSvgLength(attr("cy"), refH) ?: 0f,
                r = parseSvgLength(attr("r"), refW) ?: 0f
            )
            "ellipse" -> SvgEllipse(
                cx = parseSvgLength(attr("cx"), refW) ?: 0f,
                cy = parseSvgLength(attr("cy"), refH) ?: 0f,
                rx = parseSvgLength(attr("rx"), refW) ?: 0f,
                ry = parseSvgLength(attr("ry"), refH) ?: 0f
            )
            "line" -> SvgLine(
                x1 = parseSvgLength(attr("x1"), refW) ?: 0f,
                y1 = parseSvgLength(attr("y1"), refH) ?: 0f,
                x2 = parseSvgLength(attr("x2"), refW) ?: 0f,
                y2 = parseSvgLength(attr("y2"), refH) ?: 0f
            )
            "polyline" -> SvgPolyline(
                points = SvgPoint.parsePoints(attr("points"))
            )
            "polygon" -> SvgPolygon(
                points = SvgPoint.parsePoints(attr("points"))
            )
            "path" -> SvgPath(
                d = attr("d").orEmpty()
            )
            "text" -> {
                val collectedText = buildString {
                    append(node.textContent)
                    for (c in node.children) {
                        append(collectAllText(c))
                    }
                }.trim()
                val textEl = SvgText(
                    x = parseSvgLength(attr("x"), refW) ?: 0f,
                    y = parseSvgLength(attr("y"), refH) ?: 0f,
                    text = collectedText
                )
                attr("font-size")?.let { fs -> parseSvgLength(fs, 16f)?.let { textEl.fontSize = it } }
                attr("font-family")?.takeIf { it.isNotBlank() }?.let { textEl.fontFamily = it }
                attr("font-weight")?.takeIf { it.isNotBlank() }?.let { textEl.fontWeight = it }
                attr("text-anchor")?.takeIf { it.isNotBlank() }?.let { textEl.textAnchor = it }
                textEl
            }
            "use" -> SvgUse(
                href = attr("href") ?: attr("xlink:href") ?: "",
                x = parseSvgLength(attr("x"), refW) ?: 0f,
                y = parseSvgLength(attr("y"), refH) ?: 0f,
                width = parseSvgLength(attr("width"), refW),
                height = parseSvgLength(attr("height"), refH)
            )
            "clippath" -> SvgClipPath(
                id = attrs["id"],
                clipPathUnits = SvgGradientUnits.fromXml(attr("clipPathUnits")) ?: SvgGradientUnits.USER_SPACE_ON_USE
            ).also { clip ->
                for (child in node.children) {
                    buildElement(child, refW, refH)?.let { clip.addChild(it) }
                }
            }
            "lineargradient" -> {
                val units = SvgGradientUnits.fromXml(attr("gradientUnits")) ?: SvgGradientUnits.OBJECT_BOUNDING_BOX
                val refX = if (units == SvgGradientUnits.OBJECT_BOUNDING_BOX) 1f else refW
                val refY = if (units == SvgGradientUnits.OBJECT_BOUNDING_BOX) 1f else refH
                val grad = SvgLinearGradient(
                    id = attrs["id"],
                    x1 = parseGradientCoord(attr("x1"), refX, 0f),
                    y1 = parseGradientCoord(attr("y1"), refY, 0f),
                    x2 = parseGradientCoord(attr("x2"), refX, 1f * refX),
                    y2 = parseGradientCoord(attr("y2"), refY, 0f),
                    gradientUnits = units,
                    spreadMethod = attr("spreadMethod") ?: "pad"
                )
                attr("gradientTransform")?.let {
                    grad.gradientTransform.addAll(SvgTransform.parseTransformList(it))
                }
                for (child in node.children) {
                    if (child.tagName.equals("stop", ignoreCase = true)) {
                        grad.addStop(parseGradientStop(child))
                    }
                }
                grad
            }
            "radialgradient" -> {
                val units = SvgGradientUnits.fromXml(attr("gradientUnits")) ?: SvgGradientUnits.OBJECT_BOUNDING_BOX
                val refX = if (units == SvgGradientUnits.OBJECT_BOUNDING_BOX) 1f else refW
                val refY = if (units == SvgGradientUnits.OBJECT_BOUNDING_BOX) 1f else refH
                val cx = parseGradientCoord(attr("cx"), refX, 0.5f * refX)
                val cy = parseGradientCoord(attr("cy"), refY, 0.5f * refY)
                val r = parseGradientCoord(attr("r"), refX, 0.5f * refX)
                val fx = attr("fx")?.let { parseGradientCoord(it, refX, cx) }
                val fy = attr("fy")?.let { parseGradientCoord(it, refY, cy) }
                val grad = SvgRadialGradient(
                    id = attrs["id"],
                    cx = cx,
                    cy = cy,
                    r = r,
                    fx = fx,
                    fy = fy,
                    gradientUnits = units,
                    spreadMethod = attr("spreadMethod") ?: "pad"
                )
                attr("gradientTransform")?.let {
                    grad.gradientTransform.addAll(SvgTransform.parseTransformList(it))
                }
                for (child in node.children) {
                    if (child.tagName.equals("stop", ignoreCase = true)) {
                        grad.addStop(parseGradientStop(child))
                    }
                }
                grad
            }
            else -> return null
        }

        applyCommonAttributes(element, attrs, styles)
        return element
    }

    private fun parseGradientStop(node: XmlElementNode): SvgGradientStop {
        val attrs = node.attrs
        val styles = parseStyleAttribute(attrs["style"])
        val offsetRaw = (styles["offset"] ?: attrs["offset"])?.trim()
        val offset = when {
            offsetRaw == null -> 0f
            offsetRaw.endsWith("%") -> (offsetRaw.dropLast(1).trim().toFloatOrNull() ?: 0f) / 100f
            else -> offsetRaw.toFloatOrNull() ?: 0f
        }.coerceIn(0f, 1f)

        val colorRaw = styles["stop-color"] ?: attrs["stop-color"]
        val color = colorRaw?.let { SvgColorParser.parse(it) } ?: RgbaColor.BLACK

        val opacityRaw = (styles["stop-opacity"] ?: attrs["stop-opacity"])?.trim()
        val opacity = opacityRaw?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f

        return SvgGradientStop(offset = offset, color = color, opacity = opacity)
    }

    private fun applyCommonAttributes(
        element: SvgElement,
        attrs: Map<String, String>,
        styles: Map<String, String>
    ) {
        fun attr(name: String): String? = styles[name] ?: attrs[name]

        attrs["id"]?.takeIf { it.isNotBlank() }?.let { element.id = it.trim() }
        attrs["class"]?.takeIf { it.isNotBlank() }?.let { element.className = it }
        attr("opacity")?.toFloatOrNull()?.let { element.opacity = it }
        attr("fill")?.let { raw -> SvgPaint.parse(raw)?.let { element.fill = it } }
        attr("fill-opacity")?.toFloatOrNull()?.let { element.fillOpacity = it }
        attr("fill-rule")?.let { raw -> SvgFillRule.fromXml(raw)?.let { element.fillRule = it } }
        attr("stroke")?.let { raw -> SvgPaint.parse(raw)?.let { element.stroke = it } }
        attr("stroke-width")?.let { raw -> parseSvgLength(raw, 1f)?.let { element.strokeWidth = it } }
        attr("stroke-opacity")?.toFloatOrNull()?.let { element.strokeOpacity = it }
        attr("stroke-linecap")?.let { raw -> SvgStrokeLinecap.fromXml(raw)?.let { element.strokeLinecap = it } }
        attr("stroke-linejoin")?.let { raw -> SvgStrokeLinejoin.fromXml(raw)?.let { element.strokeLinejoin = it } }
        attr("stroke-dasharray")?.let { raw ->
            val trimmed = raw.trim().lowercase()
            if (trimmed == "none") {
                element.strokeDasharray = null
            } else {
                val nums = SvgPoint.parseNumberList(raw).filter { it >= 0f }
                if (nums.isNotEmpty()) {
                    element.strokeDasharray = nums
                }
            }
        }
        attr("stroke-dashoffset")?.let { raw -> parseSvgLength(raw, 1f)?.let { element.strokeDashoffset = it } }
        attr("transform")?.let { raw -> element.setTransform(raw) }
        attr("clip-path")?.let { raw ->
            if (!raw.trim().equals("none", ignoreCase = true)) {
                element.clipPathId = raw
            }
        }
        val display = attr("display")?.trim()?.lowercase()
        val visibility = attr("visibility")?.trim()?.lowercase()
        if (display == "none" || visibility == "hidden" || visibility == "collapse") {
            element.visible = false
        }

        for ((k, v) in attrs) {
            if (k !in COMMON_HANDLED_ATTRS) {
                element.attributes[k] = v
            }
        }
    }

    private fun parseStyleAttribute(rawStyle: String?): Map<String, String> {
        if (rawStyle.isNullOrBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        for (decl in rawStyle.split(';')) {
            val colon = decl.indexOf(':')
            if (colon <= 0) continue
            val key = decl.substring(0, colon).trim().lowercase()
            val value = decl.substring(colon + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                map[key] = value
            }
        }
        return map
    }

    private fun parseGradientCoord(raw: String?, refUnit: Float, defaultVal: Float): Float {
        if (raw.isNullOrBlank()) return defaultVal
        val trimmed = raw.trim().lowercase()
        if (trimmed.endsWith("%")) {
            val pct = trimmed.dropLast(1).trim().toFloatOrNull() ?: return defaultVal
            return (pct / 100f) * refUnit
        }
        return parseSvgLength(trimmed, refUnit) ?: defaultVal
    }

    private fun parseSvgLength(raw: String?, reference: Float): Float? {
        if (raw.isNullOrBlank()) return null
        val t = raw.trim().lowercase()
        return when {
            t.endsWith("%") -> t.dropLast(1).trim().toFloatOrNull()?.let { (it / 100f) * reference }
            t.endsWith("px") -> t.dropLast(2).trim().toFloatOrNull()
            t.endsWith("pt") -> t.dropLast(2).trim().toFloatOrNull()?.let { it * (96f / 72f) }
            t.endsWith("pc") -> t.dropLast(2).trim().toFloatOrNull()?.let { it * 16f }
            t.endsWith("in") -> t.dropLast(2).trim().toFloatOrNull()?.let { it * 96f }
            t.endsWith("cm") -> t.dropLast(2).trim().toFloatOrNull()?.let { it * (96f / 2.54f) }
            t.endsWith("mm") -> t.dropLast(2).trim().toFloatOrNull()?.let { it * (96f / 25.4f) }
            t.endsWith("em") || t.endsWith("rem") -> t.removeSuffix("rem").removeSuffix("em").trim().toFloatOrNull()?.let { it * 16f }
            else -> t.toFloatOrNull()
        }
    }

    private fun collectAllText(node: XmlElementNode): String = buildString {
        append(node.textContent)
        for (c in node.children) {
            append(collectAllText(c))
        }
    }

    private class XmlElementNode(
        val tagName: String,
        val attrs: MutableMap<String, String> = LinkedHashMap(),
        val children: MutableList<XmlElementNode> = ArrayList(),
        val textContent: StringBuilder = StringBuilder()
    )

    private fun parseXmlNodes(xml: String): List<XmlElementNode> {
        val roots = ArrayList<XmlElementNode>()
        val stack = ArrayList<XmlElementNode>()
        val len = xml.length
        var idx = 0

        while (idx < len) {
            val lt = xml.indexOf('<', idx)
            if (lt < 0) {
                if (stack.isNotEmpty()) {
                    stack.last().textContent.append(decodeXmlEntities(xml.substring(idx)))
                }
                break
            }
            if (lt > idx && stack.isNotEmpty()) {
                stack.last().textContent.append(decodeXmlEntities(xml.substring(idx, lt)))
            }
            if (lt + 1 >= len) break

            // Comment
            if (xml.startsWith("<!--", lt)) {
                val endComment = xml.indexOf("-->", lt + 4)
                idx = if (endComment < 0) len else endComment + 3
                continue
            }
            // CDATA
            if (xml.startsWith("<![CDATA[", lt)) {
                val endCdata = xml.indexOf("]]>", lt + 9)
                if (endCdata < 0) {
                    if (stack.isNotEmpty()) stack.last().textContent.append(xml.substring(lt + 9))
                    break
                }
                if (stack.isNotEmpty()) stack.last().textContent.append(xml.substring(lt + 9, endCdata))
                idx = endCdata + 3
                continue
            }
            // Processing instruction or DOCTYPE
            if (xml[lt + 1] == '?' || xml[lt + 1] == '!') {
                val closeBracket = findTagClose(xml, lt + 2)
                idx = if (closeBracket < 0) len else closeBracket + 1
                continue
            }
            // Closing tag
            if (xml[lt + 1] == '/') {
                val closeAngle = xml.indexOf('>', lt + 2)
                val rawEndTag = if (closeAngle < 0) xml.substring(lt + 2).trim() else xml.substring(lt + 2, closeAngle).trim()
                val matchIdx = stack.indexOfLast { it.tagName.equals(rawEndTag, ignoreCase = true) }
                if (matchIdx >= 0) {
                    while (stack.size > matchIdx) {
                        stack.removeAt(stack.size - 1)
                    }
                }
                idx = if (closeAngle < 0) len else closeAngle + 1
                continue
            }

            // Opening or self-closing tag
            val closeAngle = findTagClose(xml, lt + 1)
            if (closeAngle < 0) break
            var tagBody = xml.substring(lt + 1, closeAngle).trim()
            val selfClosing = tagBody.endsWith("/")
            if (selfClosing) {
                tagBody = tagBody.dropLast(1).trim()
            }
            if (tagBody.isNotEmpty()) {
                val (tagName, attrs) = parseTagHeader(tagBody)
                if (tagName.isNotEmpty()) {
                    val node = XmlElementNode(tagName, attrs)
                    if (stack.isNotEmpty()) {
                        stack.last().children.add(node)
                    } else {
                        roots.add(node)
                    }
                    if (!selfClosing) {
                        stack.add(node)
                    }
                }
            }
            idx = closeAngle + 1
        }
        return roots
    }

    private fun findTagClose(xml: String, startIdx: Int): Int {
        var i = startIdx
        var quote = '\u0000'
        while (i < xml.length) {
            val ch = xml[i]
            if (quote != '\u0000') {
                if (ch == quote) quote = '\u0000'
            } else {
                if (ch == '"' || ch == '\'') {
                    quote = ch
                } else if (ch == '>') {
                    return i
                }
            }
            i++
        }
        return -1
    }

    private fun parseTagHeader(body: String): Pair<String, LinkedHashMap<String, String>> {
        val len = body.length
        var i = 0
        while (i < len && !body[i].isWhitespace()) {
            i++
        }
        val tagName = body.substring(0, i)
        val attrs = LinkedHashMap<String, String>()
        while (i < len) {
            while (i < len && body[i].isWhitespace()) i++
            if (i >= len) break
            val keyStart = i
            while (i < len && !body[i].isWhitespace() && body[i] != '=') i++
            val key = body.substring(keyStart, i)
            while (i < len && body[i].isWhitespace()) i++
            if (i < len && body[i] == '=') {
                i++
                while (i < len && body[i].isWhitespace()) i++
                if (i >= len) break
                val q = body[i]
                val valStr: String
                if (q == '"' || q == '\'') {
                    i++
                    val valStart = i
                    while (i < len && body[i] != q) i++
                    valStr = decodeXmlEntities(body.substring(valStart, i))
                    if (i < len && body[i] == q) i++
                } else {
                    val valStart = i
                    while (i < len && !body[i].isWhitespace()) i++
                    valStr = decodeXmlEntities(body.substring(valStart, i))
                }
                if (key.isNotEmpty()) {
                    attrs[key] = valStr
                }
            } else if (key.isNotEmpty()) {
                attrs[key] = ""
            }
        }
        return tagName to attrs
    }

    private fun decodeXmlEntities(raw: String): String {
        if ('&' !in raw) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '&') {
                val semi = raw.indexOf(';', i + 1)
                if (semi > i + 1 && semi - i <= 12) {
                    val entity = raw.substring(i + 1, semi)
                    val decoded = when (entity) {
                        "amp" -> "&"
                        "lt" -> "<"
                        "gt" -> ">"
                        "quot" -> "\""
                        "apos" -> "'"
                        else -> if (entity.startsWith("#x", ignoreCase = true)) {
                            entity.substring(2).toIntOrNull(16)?.let { code ->
                                Char(code.coerceIn(0, 0xFFFF)).toString()
                            }
                        } else if (entity.startsWith("#")) {
                            entity.substring(1).toIntOrNull()?.let { code ->
                                Char(code.coerceIn(0, 0xFFFF)).toString()
                            }
                        } else {
                            null
                        }
                    }
                    if (decoded != null) {
                        out.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            out.append(raw[i])
            i++
        }
        return out.toString()
    }

    private val ROOT_HANDLED_ATTRS = setOf(
        "xmlns", "id", "class", "width", "height", "viewBox", "viewbox",
        "preserveAspectRatio", "preserveaspectratio", "style"
    )

    private val COMMON_HANDLED_ATTRS = setOf(
        "id", "class", "opacity", "fill", "fill-opacity", "fill-rule",
        "stroke", "stroke-width", "stroke-opacity", "stroke-linecap", "stroke-linejoin",
        "stroke-dasharray", "stroke-dashoffset", "transform", "clip-path", "display", "visibility",
        "x", "y", "width", "height", "rx", "ry", "cx", "cy", "r", "fx", "fy",
        "x1", "y1", "x2", "y2", "points", "d", "font-size", "font-family", "font-weight",
        "text-anchor", "href", "xlink:href", "clipPathUnits", "gradientUnits",
        "spreadMethod", "gradientTransform", "style"
    )
}
