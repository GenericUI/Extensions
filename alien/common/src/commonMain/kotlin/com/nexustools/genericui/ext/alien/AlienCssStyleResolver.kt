package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.transition.CssTransform2D
import kotlin.math.roundToInt

/**
 * Resolves the exact browser-identical Bootstrap 5.3 + CSS visual style ([AlienResolvedVisualStyle]) for an
 * [AlienWidgetNode], combining:
 * 1. Inherited CSS custom variables (`--bs-*` and custom `--*` variables) and inherited CSS properties
 *    (`color`, `font-family`, `font-size`, `font-weight`, `text-align`, `text-shadow`, `white-space`),
 * 2. Browser Bootstrap 5.3 component defaults (`btn`, `form-control`, `form-select`, `form-check`,
 *    `card`, `alert`, `badge`, `progress`, `nav-tabs`, `navbar`, `table`, `modal`, `popover`),
 * 3. Strongly-typed Bootstrap 5 Enums (`BootstrapStyleSnapshot`), and
 * 4. Custom CSS/SASS `ComputedStyle` rules plus `:hover`, `:focus`, `:active`, `:disabled`, and `:checked` states.
 */
object AlienCssStyleResolver {

    fun resolve(node: AlienWidgetNode): AlienResolvedVisualStyle {
        val snapshot = node.styleSnapshot
        val customClasses = snapshot.customClasses
        val darkTheme = isDarkTheme(node)
        val cssVars = collectCssVariables(node, darkTheme)

        val prefix = node.componentClassPrefix.lowercase()
        val isButton = prefix == "btn" || "btn" in customClasses
        val isAlert = prefix == "alert" || "alert" in customClasses
        val isBadge = prefix == "badge" || "badge" in customClasses
        val isCard = prefix == "card" || "card" in customClasses
        val isFormControl = prefix == "form-control" || "form-control" in customClasses ||
            prefix == "form-select" || "form-select" in customClasses
        val isNavbar = prefix == "navbar" || "navbar" in customClasses

        // Do NOT force BootstrapType.PRIMARY on `.btn` when `snapshot.type == null`
        val effectiveType = snapshot.type ?: when {
            isAlert -> BootstrapType.PRIMARY
            isBadge -> BootstrapType.SECONDARY
            else -> null
        }

        // Resolve inherited properties from ancestor chain
        val inherited = resolveInheritedProperties(node, cssVars, darkTheme)

        // 1. Default Background, Text, & Border from Bootstrap 5.3 Browser Rules
        var bgColor: RgbaColor = RgbaColor.TRANSPARENT
        var textColor: RgbaColor = inherited.textColor
        var borderColor: RgbaColor = RgbaColor.TRANSPARENT
        var borderWidthPx = 0f
        var borderRadiusPx = when {
            isBadge && "rounded-pill" in customClasses -> 999f
            isBadge -> 4f
            isButton || isFormControl || isCard || isAlert ->
                if (snapshot.size == BootstrapSize.SMALL) AlienBootstrapThemeTokens.SMALL_BORDER_RADIUS_PX
                else if (snapshot.size == BootstrapSize.LARGE) AlienBootstrapThemeTokens.LARGE_BORDER_RADIUS_PX
                else AlienBootstrapThemeTokens.DEFAULT_BORDER_RADIUS_PX
            else -> 0f
        }
        var borderRadiusPercent: Float? = null

        if (isButton && effectiveType != null) {
            borderWidthPx = 1f
            if (snapshot.outline) {
                val baseTypeColor = resolveTypeColor(effectiveType, cssVars)
                if (node.activePressedState && node.enabled) {
                    bgColor = AlienBootstrapThemeTokens.activeColorForType(effectiveType)
                    borderColor = bgColor
                    textColor = AlienBootstrapThemeTokens.foregroundColorForType(effectiveType)
                } else if (node.hoveredState && node.enabled) {
                    bgColor = baseTypeColor
                    borderColor = baseTypeColor
                    textColor = AlienBootstrapThemeTokens.foregroundColorForType(effectiveType)
                } else {
                    bgColor = RgbaColor.TRANSPARENT
                    borderColor = baseTypeColor
                    textColor = baseTypeColor
                }
            } else {
                val baseTypeColor = resolveTypeColor(effectiveType, cssVars)
                bgColor = when {
                    node.activePressedState && node.enabled -> AlienBootstrapThemeTokens.activeColorForType(effectiveType)
                    node.hoveredState && node.enabled -> AlienBootstrapThemeTokens.hoverColorForType(effectiveType)
                    else -> baseTypeColor
                }
                borderColor = bgColor
                textColor = AlienBootstrapThemeTokens.foregroundColorForType(effectiveType)
            }
        } else if (isAlert && effectiveType != null) {
            val base = resolveTypeColor(effectiveType, cssVars)
            bgColor = base.blendOverSurface(
                if (darkTheme) AlienBootstrapThemeTokens.BODY_BG_DARK else AlienBootstrapThemeTokens.BODY_BG_LIGHT,
                0.18f
            )
            borderColor = base.withAlpha(0.4f)
            borderWidthPx = 1f
            textColor = if (darkTheme) AlienBootstrapThemeTokens.BODY_COLOR_DARK else base.adjustBrightness(-65)
        } else if (isBadge && effectiveType != null) {
            bgColor = resolveTypeColor(effectiveType, cssVars)
            textColor = AlienBootstrapThemeTokens.foregroundColorForType(effectiveType)
        } else if (isCard) {
            bgColor = if (darkTheme) AlienBootstrapThemeTokens.TERTIARY_BG_DARK else AlienBootstrapThemeTokens.BODY_BG_LIGHT
            borderColor = if (darkTheme) AlienBootstrapThemeTokens.BORDER_COLOR_DARK else AlienBootstrapThemeTokens.BORDER_COLOR_LIGHT
            borderWidthPx = 1f
        } else if (isFormControl) {
            bgColor = if (darkTheme) AlienBootstrapThemeTokens.BODY_BG_DARK else AlienBootstrapThemeTokens.BODY_BG_LIGHT
            borderColor = if (node.focusedState) {
                AlienBootstrapThemeTokens.PRIMARY.adjustBrightness(45)
            } else {
                if (darkTheme) AlienBootstrapThemeTokens.BORDER_COLOR_DARK else AlienBootstrapThemeTokens.BORDER_COLOR_LIGHT
            }
            borderWidthPx = 1f
        } else if (isNavbar) {
            bgColor = if (darkTheme) AlienBootstrapThemeTokens.TERTIARY_BG_DARK else AlienBootstrapThemeTokens.TERTIARY_BG_LIGHT
            borderColor = if (darkTheme) AlienBootstrapThemeTokens.BORDER_COLOR_DARK else AlienBootstrapThemeTokens.BORDER_COLOR_LIGHT
            borderWidthPx = 1f
        }

        // 2. Utility Enums (`bg-*`, `text-*`, `border-*`, `rounded-*`, `shadow-*`, `p-*`, `m-*`)
        snapshot.backgroundType?.let { bgColor = resolveTypeColor(it, cssVars) }
        snapshot.textColorType?.let { textColor = resolveTypeColor(it, cssVars) }
        if ("border" in customClasses || snapshot.borderType != null) {
            if (borderWidthPx <= 0f) borderWidthPx = 1f
            borderColor = snapshot.borderType?.let { resolveTypeColor(it, cssVars) }
                ?: resolveCssVarColor("--bs-border-color", cssVars)
                ?: if (darkTheme) AlienBootstrapThemeTokens.BORDER_COLOR_DARK else AlienBootstrapThemeTokens.BORDER_COLOR_LIGHT
        }
        snapshot.borderRadius?.let { borderRadiusPx = it.radiusPx.toFloat() }

        // 3. Typography Resolution (before padding/margin so `em` units resolve against `fontSizePx`)
        val baseComputed: ComputedStyle? = node.computedStyle
        val isDisabledClass = !node.enabled || "disabled" in customClasses
        val stateComputed = baseComputed?.resolveForState(
            hovered = node.hoveredState && !isDisabledClass,
            focused = node.focusedState && !isDisabledClass,
            active = node.activePressedState && !isDisabledClass,
            enabled = !isDisabledClass,
            checked = node.checkedState
        )

        var fontSizePx = if (snapshot.size != null) {
            AlienBootstrapThemeTokens.fontSizeForSize(snapshot.size)
        } else {
            inherited.fontSizePx
        }
        stateComputed?.fontSizePx?.let { fontSizePx = it }
        stateComputed?.fontSizePercent?.let { pct ->
            fontSizePx = (inherited.fontSizePx * (pct / 100f)).coerceAtLeast(6f)
        }
        stateComputed?.rawDeclarations?.get("font-size")?.let { rawFs ->
            parseLengthPx(rawFs, inherited.fontSizePx, inherited.fontSizePx)?.let { fontSizePx = it }
        }

        var fontWeight = snapshot.fontWeight?.numericWeight
            ?: stateComputed?.fontWeightNumeric
            ?: if (isBadge || node.tagName.lowercase().startsWith("h")) 700 else inherited.fontWeight
        val italic = "fst-italic" in customClasses || inherited.italic
        var textAlign = when (snapshot.textAlignment) {
            TextAlignment.CENTER -> "center"
            TextAlignment.END -> "right"
            TextAlignment.START -> "left"
            else -> stateComputed?.textAlign ?: if (isButton || isBadge) "center" else inherited.textAlign
        }
        val textTransform = when (snapshot.textTransform) {
            TextTransform.LOWERCASE -> "lowercase"
            TextTransform.UPPERCASE -> "uppercase"
            TextTransform.CAPITALIZE -> "capitalize"
            else -> stateComputed?.rawDeclarations?.get("text-transform")?.lowercase() ?: "none"
        }
        val textOverflow = when (snapshot.textOverflow) {
            TextOverflow.ELLIPSIS -> "ellipsis"
            else -> stateComputed?.rawDeclarations?.get("text-overflow")?.lowercase() ?: "clip"
        }
        val whiteSpace = stateComputed?.whiteSpace ?: inherited.whiteSpace

        // 4. Padding & Margin Resolution (including CSS shorthands `padding: 12px 20px`, `margin: 8px auto`)
        var resolvedPadding = node.padding
        if (resolvedPadding == EdgeInsets.ZERO) {
            snapshot.paddingAll?.let { scale ->
                resolvedPadding = EdgeInsets.all(scale.toPixels())
            } ?: run {
                if (isButton) {
                    resolvedPadding = AlienBootstrapThemeTokens.buttonPaddingForSize(snapshot.size)
                } else if (isAlert || isCard) {
                    resolvedPadding = EdgeInsets.all(16)
                } else if (isBadge) {
                    resolvedPadding = EdgeInsets(top = 4, right = 7, bottom = 4, left = 7)
                } else if (isFormControl) {
                    resolvedPadding = EdgeInsets(top = 6, right = 12, bottom = 6, left = 12)
                }
            }
        }

        var resolvedMargin = node.margin
        if (resolvedMargin == EdgeInsets.ZERO) {
            snapshot.marginAll?.let { scale ->
                resolvedMargin = EdgeInsets.all(scale.toPixels())
            }
        }
        var marginLeftAuto = "mx-auto" in customClasses || "ms-auto" in customClasses
        var marginRightAuto = "mx-auto" in customClasses || "me-auto" in customClasses

        if (stateComputed != null) {
            val raw = stateComputed.rawDeclarations
            raw["padding"]?.let { padShorthand ->
                resolvedPadding = parseBoxShorthandInsets(padShorthand, fontSizePx, resolvedPadding).first
            }
            val padTop = raw["padding-top"]?.let { parseLengthPx(it, fontSizePx, null)?.roundToInt() }
            val padRight = raw["padding-right"]?.let { parseLengthPx(it, fontSizePx, null)?.roundToInt() }
            val padBottom = raw["padding-bottom"]?.let { parseLengthPx(it, fontSizePx, null)?.roundToInt() }
            val padLeft = raw["padding-left"]?.let { parseLengthPx(it, fontSizePx, null)?.roundToInt() }
            if (padTop != null || padRight != null || padBottom != null || padLeft != null) {
                resolvedPadding = EdgeInsets(
                    top = padTop ?: resolvedPadding.top,
                    right = padRight ?: resolvedPadding.right,
                    bottom = padBottom ?: resolvedPadding.bottom,
                    left = padLeft ?: resolvedPadding.left
                )
            }

            raw["margin"]?.let { marginShorthand ->
                val (insets, autoFlags) = parseBoxShorthandInsets(marginShorthand, fontSizePx, resolvedMargin)
                resolvedMargin = insets
                if (autoFlags.first) marginLeftAuto = true
                if (autoFlags.second) marginRightAuto = true
            }
            val marTop = raw["margin-top"]?.let { parseLengthPx(it, fontSizePx, null)?.roundToInt() }
            val marRight = raw["margin-right"]?.let { parseLengthPx(it, fontSizePx, null)?.roundToInt() }
            val marBottom = raw["margin-bottom"]?.let { parseLengthPx(it, fontSizePx, null)?.roundToInt() }
            val marLeft = raw["margin-left"]?.let { parseLengthPx(it, fontSizePx, null)?.roundToInt() }
            if (raw["margin-left"]?.trim().equals("auto", ignoreCase = true)) marginLeftAuto = true
            if (raw["margin-right"]?.trim().equals("auto", ignoreCase = true)) marginRightAuto = true
            if (marTop != null || marRight != null || marBottom != null || marLeft != null) {
                resolvedMargin = EdgeInsets(
                    top = marTop ?: resolvedMargin.top,
                    right = marRight ?: resolvedMargin.right,
                    bottom = marBottom ?: resolvedMargin.bottom,
                    left = marLeft ?: resolvedMargin.left
                )
            }
        }

        node.attributes["data-tree-indent-px"]?.toIntOrNull()?.takeIf { it > resolvedPadding.left }?.let { indent ->
            resolvedPadding = resolvedPadding.copy(left = indent)
        }

        var boxShadowCss: String? = when (snapshot.shadow) {
            ShadowElevation.SMALL -> "0 2px 4px rgba(0, 0, 0, 0.08)"
            ShadowElevation.DEFAULT -> "0 8px 16px rgba(0, 0, 0, 0.15)"
            ShadowElevation.LARGE -> "0 16px 48px rgba(0, 0, 0, 0.18)"
            else -> null
        }

        // 5. Custom ComputedStyle + Pseudo-Class States (`:hover`, `:focus`, `:active`, `:disabled`, `:checked`)
        val backgroundGradient = node.backgroundGradient ?: stateComputed?.backgroundGradient
        stateComputed?.backgroundColor?.let { bgColor = it }
        stateComputed?.tint?.let { tint ->
            bgColor = if (bgColor.a == 0) tint else tint.blendOverSurface(bgColor, 0.65f)
        }
        stateComputed?.textColor?.let { textColor = it }
        stateComputed?.borderColor?.let { borderColor = it }
        stateComputed?.borderWidthPx?.let { borderWidthPx = it }
        stateComputed?.borderRadiusPx?.let { borderRadiusPx = it }
        stateComputed?.borderRadiusPercent?.let { borderRadiusPercent = it }
        stateComputed?.boxShadow?.let { boxShadowCss = it }

        // Resolve CSS variables & shorthands in rawDeclarations if present
        stateComputed?.rawDeclarations?.let { raw ->
            raw["background"]?.let { v -> resolveValueColor(v, cssVars)?.let { bgColor = it } }
            raw["background-color"]?.let { v -> resolveValueColor(v, cssVars)?.let { bgColor = it } }
            raw["color"]?.let { v -> resolveValueColor(v, cssVars)?.let { textColor = it } }
            raw["border-color"]?.let { v -> resolveValueColor(v, cssVars)?.let { borderColor = it } }
            raw["border-radius"]?.let { v ->
                val trimmed = v.trim()
                if (trimmed.endsWith("%")) {
                    borderRadiusPercent = trimmed.removeSuffix("%").trim().toFloatOrNull()
                } else {
                    parseLengthPx(trimmed, fontSizePx, null)?.let { borderRadiusPx = it }
                }
            }
        }

        val raw = stateComputed?.rawDeclarations.orEmpty()
        val rawBackgroundImage = raw["background-image"]
            ?: stateComputed?.backgroundImageUrl
            ?: raw["background"]?.takeIf { it.contains("url(", ignoreCase = true) }
        val backgroundImageUrl = rawBackgroundImage?.let { extractCssUrl(it) }
        val backgroundRepeat = (stateComputed?.backgroundRepeat ?: raw["background-repeat"] ?: "repeat")
            .replace("!important", "")
            .trim()
            .lowercase()
            .ifEmpty { "repeat" }
        val backgroundPosition = (stateComputed?.backgroundPosition ?: raw["background-position"] ?: "center")
            .replace("!important", "")
            .trim()
            .lowercase()
            .ifEmpty { "center" }
        val backgroundSize = (stateComputed?.backgroundSize ?: raw["background-size"] ?: "auto")
            .replace("!important", "")
            .trim()
            .lowercase()
            .ifEmpty { "auto" }

        // Handle `.disabled` class rules (`background: #aaa !important; border-color: #666 !important; color: #333 !important;`)
        val effectiveOpacity = (node.opacity * (stateComputed?.opacity ?: 1f) *
            (if (!isDisabledClass || stateComputed?.backgroundColor != null) 1f else 0.65f))
            .coerceIn(0f, 1f)

        val focusRingSpread = if (node.focusedState && !isDisabledClass && (isButton || isFormControl || node.focusRingEnabled)) {
            4f
        } else {
            0f
        }
        val focusRingColor = if (focusRingSpread > 0f) {
            AlienBootstrapThemeTokens.focusRingColorForType(effectiveType)
        } else {
            null
        }
        val display = when {
            !node.visible || "hidden" in customClasses || "d-none" in customClasses ||
                snapshot.display == DisplayMode.NONE ||
                stateComputed?.display?.equals("none", ignoreCase = true) == true -> "none"
            stateComputed?.display != null -> stateComputed.display!!.lowercase()
            snapshot.display == DisplayMode.FLEX || "d-flex" in customClasses -> "flex"
            snapshot.display == DisplayMode.INLINE_BLOCK || "d-inline-block" in customClasses -> "inline-block"
            snapshot.display == DisplayMode.INLINE || "d-inline" in customClasses -> "inline"
            snapshot.display == DisplayMode.BLOCK || "d-block" in customClasses -> "block"
            isButton -> "inline-block"
            else -> "block"
        }

        val position = when {
            !stateComputed?.position.isNullOrBlank() -> stateComputed!!.position!!.lowercase()
            snapshot.position == PositionMode.ABSOLUTE -> "absolute"
            snapshot.position == PositionMode.FIXED -> "fixed"
            snapshot.position == PositionMode.RELATIVE -> "relative"
            else -> "static"
        }

        val leftCss = stateComputed?.left ?: raw["left"]
        val topCss = stateComputed?.top ?: raw["top"]
        val rightCss = stateComputed?.right ?: raw["right"]
        val bottomCss = stateComputed?.bottom ?: raw["bottom"]

        val widthCss = raw["width"] ?: stateComputed?.width
            ?: if ("w-100" in customClasses) "100%" else null
        val heightCss = raw["height"] ?: stateComputed?.height
            ?: if ("h-100" in customClasses) "100%" else null
        val minWidthCss = raw["min-width"] ?: stateComputed?.minWidth
        val minHeightCss = raw["min-height"] ?: stateComputed?.minHeight
        val maxWidthCss = raw["max-width"] ?: stateComputed?.maxWidth
        val maxHeightCss = raw["max-height"] ?: stateComputed?.maxHeight

        val aspectRatio = stateComputed?.aspectRatio ?: parseAspectRatio(raw["aspect-ratio"])
        val flexGrow = raw["flex-grow"]?.trim()?.toFloatOrNull()
            ?: raw["flex"]?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.toFloatOrNull()
            ?: if ("flex-grow-1" in customClasses || "flex-fill" in customClasses) 1f else 0f
        val floatMode = (stateComputed?.floatSide ?: raw["float"] ?: when {
            "float-start" in customClasses || "prev" in customClasses && raw["float"] == null && "controls" in (node.parent?.styleSnapshot?.customClasses ?: emptySet()) -> "left"
            "float-end" in customClasses -> "right"
            else -> "none"
        }).lowercase()

        val textShadowCss = when {
            stateComputed?.textShadow != null ->
                if (stateComputed.textShadow.equals("none", ignoreCase = true)) null else stateComputed.textShadow
            else -> inherited.textShadowCss
        }

        val transform2D = CssTransform2D.parse(stateComputed?.transform)
        val zIndex = stateComputed?.zIndex ?: 0
        val pointerEventsEnabled = if (isDisabledClass) false else (stateComputed?.pointerEventsEnabled ?: true)

        return AlienResolvedVisualStyle(
            backgroundColor = bgColor,
            backgroundGradient = backgroundGradient,
            backgroundImageUrl = backgroundImageUrl,
            backgroundRepeat = backgroundRepeat,
            backgroundPosition = backgroundPosition,
            backgroundSize = backgroundSize,
            textColor = textColor,
            borderColor = borderColor,
            borderWidthPx = borderWidthPx,
            borderStyle = stateComputed?.borderStyle ?: "solid",
            borderRadiusPx = borderRadiusPx,
            borderRadiusPercent = borderRadiusPercent,
            fontSizePx = fontSizePx,
            fontFamily = stateComputed?.fontFamily ?: inherited.fontFamily,
            fontWeight = fontWeight,
            italic = italic,
            textAlign = textAlign,
            textTransform = textTransform,
            textOverflow = textOverflow,
            whiteSpace = whiteSpace,
            opacity = effectiveOpacity,
            boxShadowCss = boxShadowCss,
            textShadowCss = textShadowCss,
            focusRingColor = focusRingColor,
            focusRingSpreadPx = focusRingSpread,
            padding = resolvedPadding,
            margin = resolvedMargin,
            marginLeftAuto = marginLeftAuto,
            marginRightAuto = marginRightAuto,
            display = display,
            position = position,
            leftCss = leftCss,
            topCss = topCss,
            rightCss = rightCss,
            bottomCss = bottomCss,
            widthCss = widthCss,
            heightCss = heightCss,
            minWidthCss = minWidthCss,
            minHeightCss = minHeightCss,
            maxWidthCss = maxWidthCss,
            maxHeightCss = maxHeightCss,
            aspectRatio = aspectRatio,
            flexGrow = flexGrow,
            floatMode = floatMode,
            transform2D = transform2D,
            zIndex = zIndex,
            pointerEventsEnabled = pointerEventsEnabled,
            darkTheme = darkTheme
        )
    }

    private data class InheritedProps(
        val textColor: RgbaColor,
        val fontFamily: String,
        val fontSizePx: Float,
        val fontWeight: Int,
        val italic: Boolean,
        val textAlign: String,
        val textShadowCss: String?,
        val whiteSpace: String
    )

    private fun resolveInheritedProperties(
        node: AlienWidgetNode,
        cssVars: Map<String, String>,
        darkTheme: Boolean
    ): InheritedProps {
        val lineage = mutableListOf<AlienWidgetNode>()
        var cur: AlienWidgetNode? = node.parent
        while (cur != null) {
            lineage.add(cur)
            cur = cur.parent
        }

        var textColor = resolveCssVarColor("--bs-body-color", cssVars)
            ?: if (darkTheme) AlienBootstrapThemeTokens.BODY_COLOR_DARK else AlienBootstrapThemeTokens.BODY_COLOR_LIGHT
        var fontFamily = AlienBootstrapThemeTokens.DEFAULT_FONT_FAMILY
        var fontSizePx = AlienBootstrapThemeTokens.BASE_FONT_SIZE_PX
        var fontWeight = 400
        var italic = false
        var textAlign = "left"
        var textShadowCss: String? = null
        var whiteSpace = "normal"

        for (i in lineage.indices.reversed()) {
            val anc = lineage[i]
            val snap = anc.styleSnapshot
            val cs = anc.computedStyle
            snap.textColorType?.let { textColor = resolveTypeColor(it, cssVars) }
            cs?.textColor?.let { textColor = it }
            cs?.rawDeclarations?.get("color")?.let { v -> resolveValueColor(v, cssVars)?.let { textColor = it } }
            cs?.fontFamily?.let { fontFamily = it }
            cs?.fontSizePx?.let { fontSizePx = it }
            cs?.fontSizePercent?.let { pct -> fontSizePx = (fontSizePx * (pct / 100f)).coerceAtLeast(6f) }
            cs?.fontWeightNumeric?.let { fontWeight = it }
            snap.fontWeight?.numericWeight?.let { fontWeight = it }
            if ("fst-italic" in snap.customClasses) italic = true
            when (snap.textAlignment) {
                TextAlignment.CENTER -> textAlign = "center"
                TextAlignment.END -> textAlign = "right"
                TextAlignment.START -> textAlign = "left"
                else -> {}
            }
            cs?.textAlign?.let { textAlign = it }
            cs?.textShadow?.let { ts ->
                textShadowCss = if (ts.equals("none", ignoreCase = true)) null else ts
            }
            cs?.whiteSpace?.let { whiteSpace = it }
        }

        return InheritedProps(
            textColor = textColor,
            fontFamily = fontFamily,
            fontSizePx = fontSizePx,
            fontWeight = fontWeight,
            italic = italic,
            textAlign = textAlign,
            textShadowCss = textShadowCss,
            whiteSpace = whiteSpace
        )
    }

    internal fun parseLengthPx(raw: String?, fontSizePx: Float, percentBasePx: Float?): Float? {
        if (raw.isNullOrBlank()) return null
        val t = raw.replace("!important", "").trim().lowercase()
        if (t == "0" || t == "0px" || t == "0em" || t == "0rem") return 0f
        if (t.endsWith("px")) return t.removeSuffix("px").trim().toFloatOrNull()
        if (t.endsWith("rem")) return t.removeSuffix("rem").trim().toFloatOrNull()?.let { it * 16f }
        if (t.endsWith("em")) return t.removeSuffix("em").trim().toFloatOrNull()?.let { it * fontSizePx }
        if (t.endsWith("%") && percentBasePx != null) {
            return t.removeSuffix("%").trim().toFloatOrNull()?.let { (it / 100f) * percentBasePx }
        }
        return t.toFloatOrNull()
    }

    private fun parseBoxShorthandInsets(
        shorthand: String,
        fontSizePx: Float,
        fallback: EdgeInsets
    ): Pair<EdgeInsets, Pair<Boolean, Boolean>> {
        val tokens = shorthand.replace("!important", "").trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return fallback to (false to false)

        val topTok = tokens[0]
        val rightTok = tokens.getOrElse(1) { topTok }
        val bottomTok = tokens.getOrElse(2) { topTok }
        val leftTok = tokens.getOrElse(3) { rightTok }

        val leftAuto = leftTok.equals("auto", ignoreCase = true)
        val rightAuto = rightTok.equals("auto", ignoreCase = true)

        val topPx = parseLengthPx(topTok, fontSizePx, null)?.roundToInt() ?: fallback.top
        val rightPx = if (rightAuto) 0 else (parseLengthPx(rightTok, fontSizePx, null)?.roundToInt() ?: fallback.right)
        val bottomPx = parseLengthPx(bottomTok, fontSizePx, null)?.roundToInt() ?: fallback.bottom
        val leftPx = if (leftAuto) 0 else (parseLengthPx(leftTok, fontSizePx, null)?.roundToInt() ?: fallback.left)

        return EdgeInsets(top = topPx, right = rightPx, bottom = bottomPx, left = leftPx) to (leftAuto to rightAuto)
    }

    private fun parseAspectRatio(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace("!important", "").trim()
        if ('/' in cleaned) {
            val parts = cleaned.split('/').map { it.trim().toFloatOrNull() }
            val w = parts.getOrNull(0)
            val h = parts.getOrNull(1)
            if (w != null && h != null && w > 0f && h > 0f) {
                return w / h
            }
        }
        return cleaned.toFloatOrNull()?.takeIf { it > 0f }
    }

    private fun isDarkTheme(node: AlienWidgetNode): Boolean {
        var cur: AlienWidgetNode? = node
        while (cur != null) {
            val themeAttr = cur.attributes["data-bs-theme"]?.lowercase()
            if (themeAttr == "dark") return true
            if (themeAttr == "light") return false
            if ("bg-dark" in cur.styleSnapshot.customClasses || cur.styleSnapshot.backgroundType == BootstrapType.DARK) {
                return true
            }
            cur = cur.parent
        }
        return false
    }

    private fun collectCssVariables(node: AlienWidgetNode, darkTheme: Boolean): Map<String, String> {
        val vars = LinkedHashMap<String, String>()
        vars.putAll(
            if (darkTheme) AlienBootstrapThemeTokens.DEFAULT_DARK_CSS_VARIABLES
            else AlienBootstrapThemeTokens.DEFAULT_LIGHT_CSS_VARIABLES
        )
        val lineage = mutableListOf<AlienWidgetNode>()
        var cur: AlienWidgetNode? = node
        while (cur != null) {
            lineage.add(cur)
            cur = cur.parent
        }
        for (i in lineage.indices.reversed()) {
            val raw = lineage[i].computedStyle?.rawDeclarations ?: continue
            for ((k, v) in raw) {
                if (k.startsWith("--")) {
                    vars[k] = v
                }
            }
        }
        return vars
    }

    private fun resolveTypeColor(type: BootstrapType, cssVars: Map<String, String>): RgbaColor {
        val varName = "--bs-${type.cssSuffix}"
        resolveCssVarColor(varName, cssVars)?.let { return it }
        return AlienBootstrapThemeTokens.baseColorForType(type)
    }

    private fun resolveCssVarColor(varName: String, cssVars: Map<String, String>): RgbaColor? {
        val raw = cssVars[varName] ?: return null
        return RgbaColor.fromCss(raw)
    }

    private fun resolveValueColor(rawValue: String, cssVars: Map<String, String>): RgbaColor? {
        val trimmed = rawValue.replace("!important", "").trim()
        if (trimmed.startsWith("var(") && trimmed.endsWith(")")) {
            val inner = trimmed.removePrefix("var(").removeSuffix(")").split(',').map { it.trim() }
            val varKey = inner.firstOrNull().orEmpty()
            cssVars[varKey]?.let { resolved ->
                RgbaColor.fromCss(resolved)?.let { return it }
            }
            if (inner.size >= 2) {
                return RgbaColor.fromCss(inner[1])
            }
            return null
        }
        return RgbaColor.fromCss(trimmed)
    }

    internal fun extractCssUrl(raw: String): String? {
        val trimmed = raw.replace("!important", "").trim()
        if (trimmed.isEmpty() || trimmed.equals("none", ignoreCase = true)) return null
        val urlMatch = Regex("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)", RegexOption.IGNORE_CASE).find(trimmed)
        val extracted = (urlMatch?.groupValues?.get(1) ?: trimmed.takeIf { !it.contains("(") })
            ?.trim()
            ?.removeSurrounding("'")
            ?.removeSurrounding("\"")
            ?.trim()
            ?: return null
        if (extracted.isEmpty() || extracted.equals("none", ignoreCase = true)) return null
        return extracted
    }
}
