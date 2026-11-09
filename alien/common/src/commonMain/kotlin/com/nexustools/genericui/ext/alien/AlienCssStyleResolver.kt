package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.enums.css.*
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.*
import com.nexustools.genericui.style.transition.CssTransform2D
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Resolves the exact browser-identical Bootstrap 5.3 + CSS visual style ([AlienResolvedVisualStyle]) for an
 * [AlienWidgetNode], combining:
 * 1. Inherited CSS custom variables (`--bs-*` and custom `--*` variables) and inherited CSS properties
 *    (`color`, `font-family`, `font-size`, `font-weight`, `font-style`, `line-height`, `letter-spacing`,
 *    `text-align`, `text-transform`, `text-shadow`, `white-space`, `word-break`, `cursor`, `user-select`, `visibility`),
 * 2. Browser Bootstrap 5.3 component defaults (`btn`, `form-control`, `form-select`, `form-check`,
 *    `card`, `alert`, `badge`, `progress`, `nav-tabs`, `navbar`, `table`, `modal`, `popover`),
 * 3. Strongly-typed Bootstrap 5 Enums (`BootstrapStyleSnapshot`), and
 * 4. Custom CSS/SASS `ComputedStyle` rules, inline `ComponentStyle` (`!important` aware), and
 *    `:hover`, `:focus`, `:active`, `:disabled`, and `:checked` pseudo-class states.
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

        val activeSession = resolveActiveSession(node)
        val viewportWidthPx = activeSession?.viewportWidth ?: 1280
        val useBootstrapSheet = activeSession == null || activeSession.hasBootstrapStyleSheet
        val insertAppIdx = activeSession?.insertApplicationStyleSheets ?: 1
        val isDisabledClass = !node.enabled || "disabled" in customClasses

        // Do NOT force BootstrapType.PRIMARY on `.btn` when `snapshot.type` is null or BootstrapType.NONE
        val semanticType = snapshot.type?.takeIf { it.isSemantic }
        val effectiveType = semanticType ?: when {
            isAlert -> BootstrapType.PRIMARY
            isBadge -> BootstrapType.SECONDARY
            else -> null
        }

        // Resolve inherited properties from ancestor chain
        val inherited = resolveInheritedProperties(node, cssVars, darkTheme)

        // 1. Precompiled Bootstrap 5.3 Stylesheet (`AlienPrecompiledBootstrapStyleSheet` from `bootstrap.scss`)
        val bootstrapStateComputed: ComputedStyle? = if (useBootstrapSheet) {
            AlienPrecompiledBootstrapStyleSheet
                .resolveForNode(node, darkTheme, viewportWidthPx)
                .resolveForState(
                    hovered = node.hoveredState && !isDisabledClass,
                    focused = node.focusedState && !isDisabledClass,
                    active = node.activePressedState && !isDisabledClass,
                    enabled = !isDisabledClass,
                    checked = node.checkedState
                )
        } else {
            null
        }

        var bgColor: RgbaColor = bootstrapStateComputed?.backgroundColor ?: RgbaColor.TRANSPARENT
        var textColor: RgbaColor = bootstrapStateComputed?.textColor ?: inherited.textColor
        var accentColor: RgbaColor = effectiveType?.let { resolveTypeColor(it, cssVars) }
            ?: resolveCssVarColor("--bs-primary", cssVars)
            ?: AlienBootstrapThemeTokens.PRIMARY
        var borderColor: RgbaColor = bootstrapStateComputed?.borderColor ?: RgbaColor.TRANSPARENT
        var borderWidthPx = bootstrapStateComputed?.borderWidthPx ?: 0f
        var borderRadiusPx = bootstrapStateComputed?.borderRadiusPx ?: when {
            !useBootstrapSheet -> 0f
            isBadge && ("rounded-pill" in customClasses || snapshot.borderRadius == BorderRadiusStyle.PILL) -> 9999f
            isBadge -> 4f
            isButton || isFormControl || isCard || isAlert ->
                if (snapshot.size == BootstrapSize.SMALL) AlienBootstrapThemeTokens.SMALL_BORDER_RADIUS_PX
                else if (snapshot.size == BootstrapSize.LARGE) AlienBootstrapThemeTokens.LARGE_BORDER_RADIUS_PX
                else AlienBootstrapThemeTokens.DEFAULT_BORDER_RADIUS_PX
            else -> 0f
        }
        var borderRadiusPercent: Float? = bootstrapStateComputed?.borderRadiusPercent

        if (useBootstrapSheet) {
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
            } else if (isAlert && effectiveType != null && bootstrapStateComputed?.backgroundColor == null) {
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
            } else if (isCard && bootstrapStateComputed?.backgroundColor == null) {
                bgColor = if (darkTheme) AlienBootstrapThemeTokens.TERTIARY_BG_DARK else AlienBootstrapThemeTokens.BODY_BG_LIGHT
                borderColor = if (darkTheme) AlienBootstrapThemeTokens.BORDER_COLOR_DARK else AlienBootstrapThemeTokens.BORDER_COLOR_LIGHT
                borderWidthPx = 1f
            } else if (isFormControl && bootstrapStateComputed?.backgroundColor == null) {
                bgColor = if (darkTheme) AlienBootstrapThemeTokens.BODY_BG_DARK else AlienBootstrapThemeTokens.BODY_BG_LIGHT
                borderColor = if (node.focusedState) {
                    AlienBootstrapThemeTokens.PRIMARY.adjustBrightness(45)
                } else {
                    if (darkTheme) AlienBootstrapThemeTokens.BORDER_COLOR_DARK else AlienBootstrapThemeTokens.BORDER_COLOR_LIGHT
                }
                borderWidthPx = 1f
            } else if (isNavbar && bootstrapStateComputed?.backgroundColor == null) {
                bgColor = if (darkTheme) AlienBootstrapThemeTokens.TERTIARY_BG_DARK else AlienBootstrapThemeTokens.TERTIARY_BG_LIGHT
                borderColor = if (darkTheme) AlienBootstrapThemeTokens.BORDER_COLOR_DARK else AlienBootstrapThemeTokens.BORDER_COLOR_LIGHT
                borderWidthPx = 1f
            }
        }

        // 2. Utility Enums (`bg-*`, `text-*`, `border-*`, `border-0`..`border-5`, `rounded-*`, `shadow-*`, `opacity-*`, `fs-*`, `lh-*`)
        snapshot.backgroundType?.takeIf { it.isSemantic }?.let { bgColor = resolveTypeColor(it, cssVars) }
        snapshot.textColorType?.takeIf { it.isSemantic }?.let { textColor = resolveTypeColor(it, cssVars) }
        val semanticBorderType = snapshot.borderType?.takeIf { it.isSemantic }
        if ("border" in customClasses || semanticBorderType != null || snapshot.borderWidth != null) {
            if (borderWidthPx <= 0f) borderWidthPx = 1f
            borderColor = semanticBorderType?.let { resolveTypeColor(it, cssVars) }
                ?: resolveCssVarColor("--bs-border-color", cssVars)
                ?: if (darkTheme) AlienBootstrapThemeTokens.BORDER_COLOR_DARK else AlienBootstrapThemeTokens.BORDER_COLOR_LIGHT
        }
        snapshot.borderWidth?.let { borderWidthPx = it.widthPx.toFloat() }
        snapshot.borderRadius?.let { br ->
            borderRadiusPx = br.radiusPx.toFloat()
            if (br == BorderRadiusStyle.CIRCLE) {
                borderRadiusPercent = 50f
            }
        }

        // 3. Ordered Stylesheet Cascade (`insertApplicationStyleSheets`) + Inline ComponentStyle (`!important` aware)
        val alienSheets: List<PrecompiledStyleSheet> =
            activeSession?.styleSheets ?: listOf(AlienPrecompiledBootstrapStyleSheet)
        val appStateComputed: ComputedStyle? = if (insertAppIdx >= 0) {
            node.computedStyle?.resolveForState(
                hovered = node.hoveredState && !isDisabledClass,
                focused = node.focusedState && !isDisabledClass,
                active = node.activePressedState && !isDisabledClass,
                enabled = !isDisabledClass,
                checked = node.checkedState
            )
        } else {
            null
        }

        fun resolveAlienSheet(sheet: PrecompiledStyleSheet): ComputedStyle? {
            if (sheet === AlienPrecompiledBootstrapStyleSheet ||
                sheet.name.equals(AlienPrecompiledBootstrapStyleSheet.name, ignoreCase = true)
            ) {
                val baseBs = bootstrapStateComputed ?: return null
                return baseBs.copy(
                    backgroundColor = if (bgColor != RgbaColor.TRANSPARENT) bgColor else baseBs.backgroundColor,
                    textColor = baseBs.textColor,
                    borderColor = if (borderColor != RgbaColor.TRANSPARENT) borderColor else baseBs.borderColor,
                    borderWidthPx = if (borderWidthPx > 0f) borderWidthPx else baseBs.borderWidthPx,
                    borderRadiusPx = if (borderRadiusPx > 0f) borderRadiusPx else baseBs.borderRadiusPx,
                    borderRadiusPercent = borderRadiusPercent ?: baseBs.borderRadiusPercent
                )
            }
            return AlienPrecompiledBootstrapStyleSheet
                .resolveStyleSheetForNode(sheet.styleSheet, node, darkTheme, viewportWidthPx)
                .resolveForState(
                    hovered = node.hoveredState && !isDisabledClass,
                    focused = node.focusedState && !isDisabledClass,
                    active = node.activePressedState && !isDisabledClass,
                    enabled = !isDisabledClass,
                    checked = node.checkedState
                )
        }

        var rawStateComputed: ComputedStyle? = null
        fun mergeIntoCascade(next: ComputedStyle?) {
            if (next != null) {
                rawStateComputed = rawStateComputed?.mergeWith(next) ?: next
            }
        }

        if (insertAppIdx < 0) {
            for (sheet in alienSheets) {
                if (sheet !== AlienPrecompiledBootstrapStyleSheet) {
                    mergeIntoCascade(resolveAlienSheet(sheet))
                }
            }
        } else {
            val clampedIdx = insertAppIdx.coerceIn(0, alienSheets.size)
            for (i in 0 until clampedIdx) {
                val sheet = alienSheets[i]
                if (sheet !== AlienPrecompiledBootstrapStyleSheet) {
                    mergeIntoCascade(resolveAlienSheet(sheet))
                }
            }
            mergeIntoCascade(appStateComputed)
            for (i in clampedIdx until alienSheets.size) {
                mergeIntoCascade(resolveAlienSheet(alienSheets[i]))
            }
        }
        val nodeStyle: ComponentStyle? = node.inlineComponentStyle
        val stateComputed: ComputedStyle? = when {
            nodeStyle != null -> nodeStyle.toComputedStyle(rawStateComputed)
            else -> rawStateComputed
        }

        fun nodeStyleWins(prop: CssProperty): Boolean {
            if (nodeStyle == null) return false
            val baseHasImp = rawStateComputed?.isImportant(prop) == true
            return !baseHasImp || nodeStyle.isImportant(prop)
        }

        // 4. Typography Resolution (before padding/margin so `em` units resolve against `fontSizePx`)
        var fontSizePx = when {
            snapshot.fontSize != null -> snapshot.fontSize!!.sizePx
            snapshot.size != null -> AlienBootstrapThemeTokens.fontSizeForSize(snapshot.size)
            else -> inherited.fontSizePx
        }
        stateComputed?.fontSizePx?.let { fontSizePx = it }
        stateComputed?.fontSizePercent?.let { pct ->
            fontSizePx = (inherited.fontSizePx * (pct / 100f)).coerceAtLeast(6f)
        }
        if (nodeStyle?.getFontSize() != null && nodeStyleWins(CssProperty.FONT_SIZE)) {
            nodeStyle.getFontSize()?.resolvePx(
                referencePx = inherited.fontSizePx,
                rootFontSizePx = 16f,
                parentFontSizePx = inherited.fontSizePx
            )?.let { fontSizePx = it.coerceAtLeast(6f) }
        }

        var fontWeight = snapshot.fontWeight?.numericWeight
            ?: stateComputed?.fontWeightNumeric
            ?: if (isBadge || node.tagName.lowercase().startsWith("h")) 700 else inherited.fontWeight
        if (nodeStyle?.fontWeightNumeric != null && nodeStyleWins(CssProperty.FONT_WEIGHT)) {
            fontWeight = nodeStyle.fontWeightNumeric!!
        }

        var italic = "fst-italic" in customClasses ||
            stateComputed?.fontStyle == CssFontStyle.ITALIC ||
            stateComputed?.fontStyle == CssFontStyle.OBLIQUE ||
            inherited.italic
        if (nodeStyle?.getFontStyle() != null && nodeStyleWins(CssProperty.FONT_STYLE)) {
            italic = nodeStyle.getFontStyle() == CssFontStyle.ITALIC || nodeStyle.getFontStyle() == CssFontStyle.OBLIQUE
        }

        var lineHeightPx: Float? = snapshot.lineHeight?.let { it.multiplier * fontSizePx } ?: inherited.lineHeightPx
        stateComputed?.lineHeight?.let { lh ->
            lineHeightPx = if (lh.isAuto || lh.isNone) {
                null
            } else {
                lh.resolvePx(
                    referencePx = fontSizePx,
                    rootFontSizePx = 16f,
                    parentFontSizePx = fontSizePx
                )
            }
        }
        if (nodeStyle?.getLineHeight() != null && nodeStyleWins(CssProperty.LINE_HEIGHT)) {
            val lh = nodeStyle.getLineHeight()!!
            lineHeightPx = if (lh.isAuto || lh.isNone) {
                null
            } else {
                lh.resolvePx(
                    referencePx = fontSizePx,
                    rootFontSizePx = 16f,
                    parentFontSizePx = fontSizePx
                )
            }
        }

        var letterSpacingPx: Float = inherited.letterSpacingPx
        stateComputed?.letterSpacing?.resolvePx(
            referencePx = fontSizePx,
            rootFontSizePx = 16f,
            parentFontSizePx = fontSizePx
        )?.let { letterSpacingPx = it }
        if (nodeStyle?.getLetterSpacing() != null && nodeStyleWins(CssProperty.LETTER_SPACING)) {
            nodeStyle.getLetterSpacing()?.resolvePx(
                referencePx = fontSizePx,
                rootFontSizePx = 16f,
                parentFontSizePx = fontSizePx
            )?.let { letterSpacingPx = it }
        }

        val computedTextAlign = stateComputed?.textAlign
        val textAlign = when {
            computedTextAlign != null -> when (computedTextAlign.lowercase()) {
                "start", "left" -> "left"
                "end", "right" -> "right"
                "center" -> "center"
                "justify" -> "justify"
                else -> computedTextAlign.lowercase()
            }
            snapshot.textAlignment == TextAlignment.CENTER -> "center"
            snapshot.textAlignment == TextAlignment.END -> "right"
            snapshot.textAlignment == TextAlignment.START -> "left"
            snapshot.textAlignment == TextAlignment.JUSTIFY -> "justify"
            isButton || isBadge -> "center"
            else -> inherited.textAlign
        }

        var textDecoration: CssTextDecoration = stateComputed?.textDecoration ?: CssTextDecoration.NONE
        if (nodeStyle?.getTextDecoration() != null && nodeStyleWins(CssProperty.TEXT_DECORATION)) {
            textDecoration = nodeStyle.getTextDecoration()!!
        }

        val effectiveTextTransform = if (nodeStyle?.getTextTransform() != null && nodeStyleWins(CssProperty.TEXT_TRANSFORM)) {
            nodeStyle.getTextTransform()
        } else {
            stateComputed?.textTransform ?: snapshot.textTransform
        }
        val textTransform = when (effectiveTextTransform) {
            TextTransform.LOWERCASE -> "lowercase"
            TextTransform.UPPERCASE -> "uppercase"
            TextTransform.CAPITALIZE -> "capitalize"
            TextTransform.NONE -> "none"
            null -> inherited.textTransform
        }

        val effectiveTextOverflow = if (nodeStyle?.getTextOverflow() != null && nodeStyleWins(CssProperty.TEXT_OVERFLOW)) {
            nodeStyle.getTextOverflow()
        } else {
            stateComputed?.textOverflow ?: snapshot.textOverflow
        }
        val textOverflow = if (effectiveTextOverflow == TextOverflow.ELLIPSIS) "ellipsis" else "clip"

        val whiteSpace = when {
            stateComputed?.whiteSpace != null -> stateComputed.whiteSpace!!
            snapshot.textOverflow == TextOverflow.NOWRAP || snapshot.textOverflow == TextOverflow.ELLIPSIS -> "nowrap"
            snapshot.textOverflow == TextOverflow.WRAP -> "normal"
            else -> inherited.whiteSpace
        }

        var wordBreak: CssWordBreak = stateComputed?.wordBreak
            ?: if ("text-break" in customClasses) CssWordBreak.BREAK_WORD else inherited.wordBreak
        if (nodeStyle?.getWordBreak() != null && nodeStyleWins(CssProperty.WORD_BREAK)) {
            wordBreak = nodeStyle.getWordBreak()!!
        }

        var verticalAlign: CssVerticalAlign = stateComputed?.verticalAlign?.let { CssVerticalAlign.fromCss(it) }
            ?: CssVerticalAlign.BASELINE
        if (nodeStyle?.getVerticalAlign() != null && nodeStyleWins(CssProperty.VERTICAL_ALIGN)) {
            verticalAlign = nodeStyle.getVerticalAlign()!!
        }

        // 5. Padding & Margin Resolution (including directional Bootstrap SpacingScale & CSS shorthands)
        var resolvedPadding = node.padding
        if (resolvedPadding == EdgeInsets.ZERO) {
            val hasBootstrapPadding = snapshot.paddingAll != null ||
                snapshot.paddingX != null || snapshot.paddingY != null ||
                snapshot.paddingTop != null || snapshot.paddingBottom != null ||
                snapshot.paddingStart != null || snapshot.paddingEnd != null
            if (hasBootstrapPadding) {
                val allPx = snapshot.paddingAll?.toPixels() ?: 0
                val xPx = snapshot.paddingX?.toPixels() ?: allPx
                val yPx = snapshot.paddingY?.toPixels() ?: allPx
                resolvedPadding = EdgeInsets(
                    top = snapshot.paddingTop?.toPixels() ?: yPx,
                    right = snapshot.paddingEnd?.toPixels() ?: xPx,
                    bottom = snapshot.paddingBottom?.toPixels() ?: yPx,
                    left = snapshot.paddingStart?.toPixels() ?: xPx
                )
            } else {
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
        var marginLeftAuto = "mx-auto" in customClasses || "ms-auto" in customClasses ||
            snapshot.marginAll == SpacingScale.AUTO ||
            snapshot.marginX == SpacingScale.AUTO ||
            snapshot.marginStart == SpacingScale.AUTO
        var marginRightAuto = "mx-auto" in customClasses || "me-auto" in customClasses ||
            snapshot.marginAll == SpacingScale.AUTO ||
            snapshot.marginX == SpacingScale.AUTO ||
            snapshot.marginEnd == SpacingScale.AUTO

        if (resolvedMargin == EdgeInsets.ZERO) {
            val allPx = snapshot.marginAll?.toPixels() ?: 0
            val xPx = snapshot.marginX?.toPixels() ?: allPx
            val yPx = snapshot.marginY?.toPixels() ?: allPx
            resolvedMargin = EdgeInsets(
                top = snapshot.marginTop?.toPixels() ?: yPx,
                right = snapshot.marginEnd?.toPixels() ?: xPx,
                bottom = snapshot.marginBottom?.toPixels() ?: yPx,
                left = snapshot.marginStart?.toPixels() ?: xPx
            )
        }

        if (stateComputed != null) {
            stateComputed.padding?.let { padEdges ->
                resolvedPadding = EdgeInsets(
                    top = padEdges.top.resolvePx(0f, 16f, fontSizePx)?.roundToInt() ?: resolvedPadding.top,
                    right = padEdges.right.resolvePx(0f, 16f, fontSizePx)?.roundToInt() ?: resolvedPadding.right,
                    bottom = padEdges.bottom.resolvePx(0f, 16f, fontSizePx)?.roundToInt() ?: resolvedPadding.bottom,
                    left = padEdges.left.resolvePx(0f, 16f, fontSizePx)?.roundToInt() ?: resolvedPadding.left
                )
            }
            val padTop = stateComputed.paddingTop?.resolvePx(0f, 16f, fontSizePx)?.roundToInt()
            val padRight = stateComputed.paddingRight?.resolvePx(0f, 16f, fontSizePx)?.roundToInt()
            val padBottom = stateComputed.paddingBottom?.resolvePx(0f, 16f, fontSizePx)?.roundToInt()
            val padLeft = stateComputed.paddingLeft?.resolvePx(0f, 16f, fontSizePx)?.roundToInt()
            if (padTop != null || padRight != null || padBottom != null || padLeft != null) {
                resolvedPadding = EdgeInsets(
                    top = padTop ?: resolvedPadding.top,
                    right = padRight ?: resolvedPadding.right,
                    bottom = padBottom ?: resolvedPadding.bottom,
                    left = padLeft ?: resolvedPadding.left
                )
            }

            stateComputed.margin?.let { marEdges ->
                if (marEdges.left.isAuto) marginLeftAuto = true
                if (marEdges.right.isAuto) marginRightAuto = true
                resolvedMargin = EdgeInsets(
                    top = marEdges.top.resolvePx(0f, 16f, fontSizePx)?.roundToInt() ?: resolvedMargin.top,
                    right = if (marEdges.right.isAuto) 0 else (marEdges.right.resolvePx(0f, 16f, fontSizePx)?.roundToInt() ?: resolvedMargin.right),
                    bottom = marEdges.bottom.resolvePx(0f, 16f, fontSizePx)?.roundToInt() ?: resolvedMargin.bottom,
                    left = if (marEdges.left.isAuto) 0 else (marEdges.left.resolvePx(0f, 16f, fontSizePx)?.roundToInt() ?: resolvedMargin.left)
                )
            }
            val marTop = stateComputed.marginTop?.resolvePx(0f, 16f, fontSizePx)?.roundToInt()
            val marRight = stateComputed.marginRight?.resolvePx(0f, 16f, fontSizePx)?.roundToInt()
            val marBottom = stateComputed.marginBottom?.resolvePx(0f, 16f, fontSizePx)?.roundToInt()
            val marLeft = stateComputed.marginLeft?.resolvePx(0f, 16f, fontSizePx)?.roundToInt()
            if (stateComputed.marginLeft?.isAuto == true) marginLeftAuto = true
            if (stateComputed.marginRight?.isAuto == true) marginRightAuto = true
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
            ShadowElevation.NONE -> null
            ShadowElevation.SMALL -> "0 2px 4px rgba(0, 0, 0, 0.08)"
            ShadowElevation.DEFAULT -> "0 8px 16px rgba(0, 0, 0, 0.15)"
            ShadowElevation.LARGE -> "0 16px 48px rgba(0, 0, 0, 0.18)"
            else -> null
        }

        // 6. Custom ComputedStyle + Inline ComponentStyle
        val backgroundGradient = node.backgroundGradient ?: stateComputed?.backgroundGradient
        stateComputed?.backgroundColor?.let { bgColor = it }
        stateComputed?.textColor?.let { textColor = it }
        stateComputed?.accentColor?.let { accentColor = it }
        stateComputed?.borderColor?.let { borderColor = it }
        stateComputed?.borderWidthPx?.let { borderWidthPx = it }
        stateComputed?.borderRadiusPx?.let { borderRadiusPx = it }
        stateComputed?.borderRadiusPercent?.let { borderRadiusPercent = it }
        stateComputed?.boxShadow?.let { boxShadowCss = if (it.equals("none", ignoreCase = true)) null else it }

        var cornerRadii: CssCornerRadii? = stateComputed?.borderRadius
        if (nodeStyle?.getBorderRadius() != null && nodeStyleWins(CssProperty.BORDER_RADIUS)) {
            cornerRadii = nodeStyle.getBorderRadius()
        }

        var boxShadowList: CssShadowList? = stateComputed?.boxShadowList ?: boxShadowCss?.let { CssShadowList.parse(it) }
        if (nodeStyle?.getBoxShadow() != null && nodeStyleWins(CssProperty.BOX_SHADOW)) {
            boxShadowList = nodeStyle.getBoxShadow()
            boxShadowCss = boxShadowList?.toCssString()
        }

        val rawBackgroundImage = stateComputed?.backgroundImageUrl
        var backgroundImage: CssBackgroundImage? = when {
            stateComputed?.backgroundGradient != null -> CssBackgroundImage.fromGradient(stateComputed.backgroundGradient!!)
            rawBackgroundImage != null -> CssBackgroundImage.parse(rawBackgroundImage)
            else -> null
        }
        if (nodeStyle?.getBackgroundImage() != null && nodeStyleWins(CssProperty.BACKGROUND_IMAGE)) {
            backgroundImage = nodeStyle.getBackgroundImage()
        }
        val backgroundImageUrl = backgroundImage?.urlOrPath ?: rawBackgroundImage?.let { extractCssUrl(it) }
        val backgroundRepeat = (stateComputed?.backgroundRepeat ?: "repeat")
            .replace("!important", "")
            .trim()
            .lowercase()
            .ifEmpty { "repeat" }
        val backgroundPosition = (stateComputed?.backgroundPosition ?: "center")
            .replace("!important", "")
            .trim()
            .lowercase()
            .ifEmpty { "center" }
        val backgroundSize = (stateComputed?.backgroundSize ?: "auto")
            .replace("!important", "")
            .trim()
            .lowercase()
            .ifEmpty { "auto" }
        val backgroundClip = (if (nodeStyle?.getBackgroundClip() != null && nodeStyleWins(CssProperty.BACKGROUND_CLIP)) {
            nodeStyle.getBackgroundClip()
        } else {
            stateComputed?.backgroundClip
        }) ?: CssBackgroundClip.BORDER_BOX
        val backgroundOrigin = (if (nodeStyle?.getBackgroundOrigin() != null && nodeStyleWins(CssProperty.BACKGROUND_ORIGIN)) {
            nodeStyle.getBackgroundOrigin()
        } else {
            stateComputed?.backgroundOrigin
        }) ?: CssBackgroundOrigin.PADDING_BOX
        val backgroundAttachment = (if (nodeStyle?.getBackgroundAttachment() != null && nodeStyleWins(CssProperty.BACKGROUND_ATTACHMENT)) {
            nodeStyle.getBackgroundAttachment()
        } else {
            stateComputed?.backgroundAttachment?.let { CssBackgroundAttachment.fromCss(it) }
        }) ?: CssBackgroundAttachment.SCROLL

        // Resolve opacity without squaring when `node.opacity` was synced from `computedStyle` or `inlineComponentStyle`
        val hasComputedOpacity = stateComputed != null && (stateComputed.hasProperty(CssProperty.OPACITY) || stateComputed.opacity != 1.0f)
        val baseNodeOpacity = when {
            hasComputedOpacity && (node.opacity == 1.0f || abs(node.opacity - stateComputed!!.opacity) < 1e-4f) ->
                stateComputed!!.opacity
            hasComputedOpacity ->
                node.opacity * stateComputed!!.opacity
            snapshot.opacity != null ->
                node.opacity * snapshot.opacity!!.alpha
            else ->
                node.opacity
        }
        val effectiveOpacity = (baseNodeOpacity *
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

        val visible = when {
            !node.visible -> false
            nodeStyle?.getVisibility() != null && nodeStyleWins(CssProperty.VISIBILITY) ->
                nodeStyle.getVisibility() != CssVisibility.HIDDEN && nodeStyle.getVisibility() != CssVisibility.COLLAPSE
            stateComputed?.visibility != null ->
                stateComputed.visibility != CssVisibility.HIDDEN && stateComputed.visibility != CssVisibility.COLLAPSE
            snapshot.visibility == BootstrapVisibility.INVISIBLE || "invisible" in customClasses -> false
            snapshot.visibility == BootstrapVisibility.VISIBLE || "visible" in customClasses -> true
            else -> inherited.visible
        }

        val allNodeClasses = LinkedHashSet<String>().apply {
            addAll(com.nexustools.genericui.style.mapper.BootstrapClassMapper.toEnumClassSet(snapshot))
            addAll(customClasses)
        }
        val responsiveDisplay = resolveResponsiveBootstrapDisplay(allNodeClasses, viewportWidthPx)
        val computedDisplay = stateComputed?.display?.replace("!important", "")?.trim()?.lowercase()
        val computedDisplayImportant = stateComputed?.isImportant(CssProperty.DISPLAY) == true

        val display = when {
            !node.visible || "hidden" in customClasses -> "none"
            responsiveDisplay != null -> responsiveDisplay
            nodeStyle?.getDisplay() != null && nodeStyleWins(CssProperty.DISPLAY) ->
                nodeStyle.getDisplay()!!.cssValue
            computedDisplayImportant && !computedDisplay.isNullOrBlank() -> computedDisplay
            !computedDisplay.isNullOrBlank() -> computedDisplay
            snapshot.display == DisplayMode.NONE -> "none"
            snapshot.display == DisplayMode.FLEX -> "flex"
            snapshot.display == DisplayMode.INLINE_FLEX -> "inline-flex"
            snapshot.display == DisplayMode.GRID -> "grid"
            snapshot.display == DisplayMode.INLINE_BLOCK -> "inline-block"
            snapshot.display == DisplayMode.INLINE -> "inline"
            snapshot.display == DisplayMode.BLOCK -> "block"
            node.tagName.equals("table", ignoreCase = true) -> "table"
            node.tagName.equals("tr", ignoreCase = true) -> "table-row"
            node.tagName.equals("td", ignoreCase = true) || node.tagName.equals("th", ignoreCase = true) -> "table-cell"
            isButton -> "inline-block"
            else -> "block"
        }

        val position = when {
            !stateComputed?.position.isNullOrBlank() -> stateComputed!!.position!!.lowercase()
            snapshot.position == PositionMode.ABSOLUTE -> "absolute"
            snapshot.position == PositionMode.FIXED -> "fixed"
            snapshot.position == PositionMode.RELATIVE -> "relative"
            snapshot.position == PositionMode.STICKY -> "sticky"
            else -> "static"
        }

        val leftCss = stateComputed?.left
        val topCss = stateComputed?.top
        val rightCss = stateComputed?.right
        val bottomCss = stateComputed?.bottom

        val bootstrapWidthCss = when (snapshot.width) {
            BootstrapWidth.W_25 -> "25%"
            BootstrapWidth.W_50 -> "50%"
            BootstrapWidth.W_75 -> "75%"
            BootstrapWidth.W_100 -> "100%"
            BootstrapWidth.W_AUTO -> "auto"
            BootstrapWidth.VW_100 -> "100%"
            else -> null
        }
        val bootstrapHeightCss = when (snapshot.height) {
            BootstrapHeight.H_25 -> "25%"
            BootstrapHeight.H_50 -> "50%"
            BootstrapHeight.H_75 -> "75%"
            BootstrapHeight.H_100 -> "100%"
            BootstrapHeight.H_AUTO -> "auto"
            BootstrapHeight.VH_100 -> "100%"
            else -> null
        }

        val widthCss = stateComputed?.width ?: bootstrapWidthCss
            ?: if ("w-100" in customClasses) "100%" else null
        val heightCss = stateComputed?.height ?: bootstrapHeightCss
            ?: if ("h-100" in customClasses) "100%" else null
        val minWidthCss = stateComputed?.minWidth
            ?: if (snapshot.width == BootstrapWidth.MIN_VW_100) "100%" else null
        val minHeightCss = stateComputed?.minHeight
            ?: if (snapshot.height == BootstrapHeight.MIN_VH_100) "100%" else null
        val maxWidthCss = stateComputed?.maxWidth
            ?: if (snapshot.width == BootstrapWidth.MW_100 || "mw-100" in customClasses) "100%" else null
        val maxHeightCss = stateComputed?.maxHeight
            ?: if (snapshot.height == BootstrapHeight.MH_100 || "mh-100" in customClasses) "100%" else null

        val boxSizing = (if (nodeStyle?.getBoxSizing() != null && nodeStyleWins(CssProperty.BOX_SIZING)) {
            nodeStyle.getBoxSizing()
        } else {
            stateComputed?.boxSizing
        }) ?: CssBoxSizing.BORDER_BOX

        val aspectRatio = stateComputed?.aspectRatio
            ?: snapshot.aspectRatio?.numericRatio

        val objectFit: ObjectFit = when {
            nodeStyle?.getObjectFit() != null && nodeStyleWins(CssProperty.OBJECT_FIT) -> nodeStyle.getObjectFit()!!
            stateComputed?.objectFit != null -> stateComputed.objectFit
            snapshot.objectFit == BootstrapObjectFit.CONTAIN -> ObjectFit.CONTAIN
            snapshot.objectFit == BootstrapObjectFit.COVER -> ObjectFit.COVER
            snapshot.objectFit == BootstrapObjectFit.SCALE -> ObjectFit.SCALE_DOWN
            snapshot.objectFit == BootstrapObjectFit.NONE -> ObjectFit.NONE
            else -> ObjectFit.FILL
        }

        val classOverflow = when {
            "overflow-hidden" in customClasses -> OverflowBehavior.HIDDEN
            "overflow-auto" in customClasses -> OverflowBehavior.AUTO
            "overflow-scroll" in customClasses -> OverflowBehavior.SCROLL
            "overflow-visible" in customClasses -> OverflowBehavior.VISIBLE
            else -> null
        }
        val overflow = parseOverflow(stateComputed?.overflow)
            ?: snapshot.overflow
            ?: classOverflow
            ?: if (snapshot.textOverflow == TextOverflow.ELLIPSIS) OverflowBehavior.HIDDEN else OverflowBehavior.VISIBLE
        val overflowX = (if (nodeStyle?.getOverflowX() != null && nodeStyleWins(CssProperty.OVERFLOW_X)) {
            nodeStyle.getOverflowX()
        } else {
            stateComputed?.overflowX
                ?: when {
                    "overflow-x-hidden" in customClasses -> OverflowBehavior.HIDDEN
                    "overflow-x-auto" in customClasses -> OverflowBehavior.AUTO
                    "overflow-x-scroll" in customClasses -> OverflowBehavior.SCROLL
                    "overflow-x-visible" in customClasses -> OverflowBehavior.VISIBLE
                    else -> null
                }
        }) ?: overflow
        val overflowY = (if (nodeStyle?.getOverflowY() != null && nodeStyleWins(CssProperty.OVERFLOW_Y)) {
            nodeStyle.getOverflowY()
        } else {
            stateComputed?.overflowY
                ?: when {
                    "overflow-y-hidden" in customClasses -> OverflowBehavior.HIDDEN
                    "overflow-y-auto" in customClasses -> OverflowBehavior.AUTO
                    "overflow-y-scroll" in customClasses -> OverflowBehavior.SCROLL
                    "overflow-y-visible" in customClasses -> OverflowBehavior.VISIBLE
                    else -> null
                }
        }) ?: overflow

        val flexDirection = stateComputed?.flexDirection ?: snapshot.flexDirection ?: FlexDirection.ROW
        val flexWrap = stateComputed?.flexWrap ?: snapshot.flexWrap ?: FlexWrap.NOWRAP
        val justifyContent = stateComputed?.justifyContent ?: snapshot.justifyContent ?: JustifyContent.START
        val alignItems = stateComputed?.alignItems ?: snapshot.alignItems ?: AlignItems.STRETCH
        val alignSelf = stateComputed?.alignSelf ?: snapshot.alignSelf ?: AlignSelf.AUTO
        val alignContent = (if (nodeStyle?.getAlignContent() != null && nodeStyleWins(CssProperty.ALIGN_CONTENT)) {
            nodeStyle.getAlignContent()
        } else {
            stateComputed?.alignContent
        }) ?: CssAlignContent.STRETCH

        val flexGrow = stateComputed?.flexGrow
            ?: snapshot.flexGrow?.factor?.toFloat()
            ?: if (snapshot.flexFill || "flex-grow-1" in customClasses || "flex-fill" in customClasses) 1f else 0f
        val flexShrink = stateComputed?.flexShrink
            ?: snapshot.flexShrink?.factor?.toFloat()
            ?: 1f
        val flexBasisCss = stateComputed?.flexBasis?.toCssString() ?: if (snapshot.flexFill) "auto" else null

        val defaultComponentGap = if (prefix == "vstack" || prefix == "hstack") 8f else 0f
        val customClassGap = resolveCustomClassGapPx(customClasses)
        val baseGapPx = stateComputed?.gap?.resolvePx(0f, 16f, fontSizePx)
            ?: snapshot.gap?.toPixels()?.toFloat()
            ?: customClassGap
            ?: defaultComponentGap
        val rowGapPx = stateComputed?.rowGap?.resolvePx(0f, 16f, fontSizePx)
            ?: snapshot.rowGap?.toPixels()?.toFloat()
            ?: baseGapPx
        val columnGapPx = stateComputed?.columnGap?.resolvePx(0f, 16f, fontSizePx)
            ?: snapshot.columnGap?.toPixels()?.toFloat()
            ?: baseGapPx

        val floatMode = (stateComputed?.floatSide ?: when {
            snapshot.floatSide == BootstrapFloat.START -> "left"
            snapshot.floatSide == BootstrapFloat.END -> "right"
            snapshot.floatSide == BootstrapFloat.NONE -> "none"
            "float-start" in customClasses || ("prev" in customClasses && stateComputed?.floatSide == null && "controls" in (node.parent?.styleSnapshot?.customClasses ?: emptySet())) -> "left"
            "float-end" in customClasses -> "right"
            else -> "none"
        }).lowercase()
        val clearMode = (stateComputed?.clearSide ?: "none").lowercase()

        val textShadowCss = when {
            stateComputed?.textShadow != null ->
                if (stateComputed.textShadow.equals("none", ignoreCase = true)) null else stateComputed.textShadow
            else -> inherited.textShadowCss
        }
        val textShadow = (if (nodeStyle?.getTextShadow() != null && nodeStyleWins(CssProperty.TEXT_SHADOW)) {
            nodeStyle.getTextShadow()?.shadows?.firstOrNull()
        } else {
            stateComputed?.textShadowList?.shadows?.firstOrNull() ?: textShadowCss?.let { CssShadow.parse(it) }
        }) ?: inherited.textShadow

        val cursor: CssCursorValue = when {
            node.customCursor != null -> node.customCursor!!
            stateComputed?.customCursor != null -> stateComputed.customCursor!!
            stateComputed?.cursor != null -> CssCustomCursor.parse(stateComputed.cursor) ?: CssCursor.DEFAULT
            isButton && !isDisabledClass -> CssCursor.POINTER
            else -> inherited.cursor
        }

        val userSelect: CssUserSelect = when {
            nodeStyle?.getUserSelect() != null && nodeStyleWins(CssProperty.USER_SELECT) -> nodeStyle.getUserSelect()!!
            stateComputed?.userSelect != null -> stateComputed.userSelect
            snapshot.userSelect == BootstrapUserSelect.ALL -> CssUserSelect.ALL
            snapshot.userSelect == BootstrapUserSelect.NONE -> CssUserSelect.NONE
            snapshot.userSelect == BootstrapUserSelect.AUTO -> CssUserSelect.AUTO
            else -> inherited.userSelect
        }

        val transform2D = if (nodeStyle?.getTransform() != null && nodeStyleWins(CssProperty.TRANSFORM)) {
            nodeStyle.getTransform()!!
        } else {
            stateComputed?.transform2D ?: CssTransform2D.parse(stateComputed?.transform)
        }
        val filter = if (nodeStyle?.getFilter() != null && nodeStyleWins(CssProperty.FILTER)) {
            nodeStyle.getFilter()
        } else {
            stateComputed?.filter
        }
        val backdropFilter = if (nodeStyle?.getBackdropFilter() != null && nodeStyleWins(CssProperty.BACKDROP_FILTER)) {
            nodeStyle.getBackdropFilter()
        } else {
            stateComputed?.backdropFilter
        }

        val zIndex = stateComputed?.zIndex ?: 0
        val pointerEventsEnabled = when {
            isDisabledClass -> false
            snapshot.pointerEvents == BootstrapPointerEvents.NONE -> false
            stateComputed != null -> stateComputed.pointerEventsEnabled
            else -> true
        }

        return AlienResolvedVisualStyle(
            backgroundColor = bgColor,
            backgroundGradient = backgroundGradient,
            backgroundImage = backgroundImage,
            backgroundImageUrl = backgroundImageUrl,
            backgroundRepeat = backgroundRepeat,
            backgroundPosition = backgroundPosition,
            backgroundSize = backgroundSize,
            backgroundClip = backgroundClip,
            backgroundOrigin = backgroundOrigin,
            backgroundAttachment = backgroundAttachment,
            textColor = textColor,
            accentColor = accentColor,
            borderColor = borderColor,
            borderWidthPx = borderWidthPx,
            borderStyle = stateComputed?.borderStyle ?: "solid",
            borderRadiusPx = borderRadiusPx,
            borderRadiusPercent = borderRadiusPercent,
            cornerRadii = cornerRadii,
            fontSizePx = fontSizePx,
            fontFamily = stateComputed?.fontFamily ?: if ("font-monospace" in customClasses) "monospace" else inherited.fontFamily,
            fontWeight = fontWeight,
            italic = italic,
            lineHeightPx = lineHeightPx,
            letterSpacingPx = letterSpacingPx,
            textAlign = textAlign,
            textDecoration = textDecoration,
            textTransform = textTransform,
            textOverflow = textOverflow,
            whiteSpace = whiteSpace,
            wordBreak = wordBreak,
            verticalAlign = verticalAlign,
            opacity = effectiveOpacity,
            boxShadowCss = boxShadowCss,
            boxShadowList = boxShadowList,
            textShadowCss = textShadowCss,
            textShadow = textShadow,
            focusRingColor = focusRingColor,
            focusRingSpreadPx = focusRingSpread,
            padding = resolvedPadding,
            margin = resolvedMargin,
            marginLeftAuto = marginLeftAuto,
            marginRightAuto = marginRightAuto,
            display = display,
            visible = visible,
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
            boxSizing = boxSizing,
            aspectRatio = aspectRatio,
            objectFit = objectFit,
            overflow = overflow,
            overflowX = overflowX,
            overflowY = overflowY,
            flexDirection = flexDirection,
            flexWrap = flexWrap,
            justifyContent = justifyContent,
            alignItems = alignItems,
            alignSelf = alignSelf,
            alignContent = alignContent,
            flexGrow = flexGrow,
            flexShrink = flexShrink,
            flexBasisCss = flexBasisCss,
            gapPx = baseGapPx,
            rowGapPx = rowGapPx,
            columnGapPx = columnGapPx,
            floatMode = floatMode,
            clearMode = clearMode,
            cursor = cursor,
            userSelect = userSelect,
            transform2D = transform2D,
            filter = filter,
            backdropFilter = backdropFilter,
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
        val lineHeightPx: Float?,
        val letterSpacingPx: Float,
        val textAlign: String,
        val textTransform: String,
        val textShadowCss: String?,
        val textShadow: CssShadow?,
        val whiteSpace: String,
        val wordBreak: CssWordBreak,
        val cursor: CssCursorValue,
        val userSelect: CssUserSelect,
        val visible: Boolean
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
        var lineHeightPx: Float? = null
        var letterSpacingPx = 0f
        var textAlign = "left"
        var textTransform = "none"
        var textShadowCss: String? = null
        var textShadow: CssShadow? = null
        var whiteSpace = "normal"
        var wordBreak = CssWordBreak.NORMAL
        var cursor: CssCursorValue = CssCursor.DEFAULT
        var userSelect = CssUserSelect.AUTO
        var visible = true

        for (i in lineage.indices.reversed()) {
            val anc = lineage[i]
            val snap = anc.styleSnapshot
            val inline = anc.inlineComponentStyle
            val cs = if (inline != null) inline.toComputedStyle(anc.computedStyle) else anc.computedStyle
            snap.textColorType?.takeIf { it.isSemantic }?.let { textColor = resolveTypeColor(it, cssVars) }
            cs?.textColor?.let { textColor = it }
            cs?.fontFamily?.let { fontFamily = it }
            snap.fontSize?.let { fontSizePx = it.sizePx }
            cs?.fontSizePx?.let { fontSizePx = it }
            cs?.fontSizePercent?.let { pct -> fontSizePx = (fontSizePx * (pct / 100f)).coerceAtLeast(6f) }
            cs?.fontWeightNumeric?.let { fontWeight = it }
            snap.fontWeight?.numericWeight?.let { fontWeight = it }
            if ("fst-italic" in snap.customClasses || cs?.fontStyle == CssFontStyle.ITALIC || cs?.fontStyle == CssFontStyle.OBLIQUE || inline?.getFontStyle() == CssFontStyle.ITALIC) italic = true
            snap.lineHeight?.let { lineHeightPx = it.multiplier * fontSizePx }
            cs?.lineHeight?.resolvePx(fontSizePx, 16f, fontSizePx)?.let { lineHeightPx = it }
            inline?.getLineHeight()?.resolvePx(fontSizePx, 16f, fontSizePx)?.let { lineHeightPx = it }
            cs?.letterSpacing?.resolvePx(fontSizePx, 16f, fontSizePx)?.let { letterSpacingPx = it }
            inline?.getLetterSpacing()?.resolvePx(fontSizePx, 16f, fontSizePx)?.let { letterSpacingPx = it }
            when (snap.textAlignment) {
                TextAlignment.CENTER -> textAlign = "center"
                TextAlignment.END -> textAlign = "right"
                TextAlignment.START -> textAlign = "left"
                TextAlignment.JUSTIFY -> textAlign = "justify"
                else -> {}
            }
            cs?.textAlign?.let { textAlign = it }
            when (cs?.textTransform ?: snap.textTransform) {
                TextTransform.LOWERCASE -> textTransform = "lowercase"
                TextTransform.UPPERCASE -> textTransform = "uppercase"
                TextTransform.CAPITALIZE -> textTransform = "capitalize"
                TextTransform.NONE -> textTransform = "none"
                null -> {}
            }
            cs?.textShadow?.let { ts ->
                textShadowCss = if (ts.equals("none", ignoreCase = true)) null else ts
                textShadow = cs.textShadowList?.shadows?.firstOrNull() ?: textShadowCss?.let { CssShadow.parse(it) }
            }
            cs?.whiteSpace?.let { whiteSpace = it }
            cs?.wordBreak?.let { wordBreak = it }
            inline?.getWordBreak()?.let { wordBreak = it }
            anc.customCursor?.let { cursor = it }
            cs?.customCursor?.let { cursor = it }
            cs?.userSelect?.let { userSelect = it }
            inline?.getUserSelect()?.let { userSelect = it }
            if (snap.visibility == BootstrapVisibility.INVISIBLE || cs?.visibility == CssVisibility.HIDDEN || inline?.getVisibility() == CssVisibility.HIDDEN) {
                visible = false
            } else if (snap.visibility == BootstrapVisibility.VISIBLE || cs?.visibility == CssVisibility.VISIBLE || inline?.getVisibility() == CssVisibility.VISIBLE) {
                visible = true
            }
        }

        return InheritedProps(
            textColor = textColor,
            fontFamily = fontFamily,
            fontSizePx = fontSizePx,
            fontWeight = fontWeight,
            italic = italic,
            lineHeightPx = lineHeightPx,
            letterSpacingPx = letterSpacingPx,
            textAlign = textAlign,
            textTransform = textTransform,
            textShadowCss = textShadowCss,
            textShadow = textShadow,
            whiteSpace = whiteSpace,
            wordBreak = wordBreak,
            cursor = cursor,
            userSelect = userSelect,
            visible = visible
        )
    }

    private val BOOTSTRAP_DISPLAY_BREAKPOINTS: List<Pair<String, Int>> = listOf(
        "" to 0,
        "sm-" to 576,
        "md-" to 768,
        "lg-" to 992,
        "xl-" to 1200,
        "xxl-" to 1400
    )

    private val BOOTSTRAP_DISPLAY_VALUES: List<String> = listOf(
        "none",
        "inline",
        "inline-block",
        "block",
        "grid",
        "inline-grid",
        "table",
        "table-row",
        "table-cell",
        "flex",
        "inline-flex"
    )

    internal fun resolveResponsiveBootstrapDisplay(
        classes: Set<String>,
        viewportWidthPx: Int
    ): String? {
        if (classes.isEmpty()) return null
        var resolved: String? = null
        for ((bpPrefix, minWidth) in BOOTSTRAP_DISPLAY_BREAKPOINTS) {
            if (viewportWidthPx < minWidth) break
            val classPrefix = "d-$bpPrefix"
            for (value in BOOTSTRAP_DISPLAY_VALUES) {
                if ("$classPrefix$value" in classes) {
                    resolved = value
                }
            }
        }
        return resolved
    }

    private fun parseOverflow(raw: String?): OverflowBehavior? {
        if (raw.isNullOrBlank()) return null
        return when (raw.replace("!important", "").trim().lowercase()) {
            "auto" -> OverflowBehavior.AUTO
            "hidden", "clip" -> OverflowBehavior.HIDDEN
            "visible" -> OverflowBehavior.VISIBLE
            "scroll" -> OverflowBehavior.SCROLL
            else -> null
        }
    }

    private fun resolveCustomClassGapPx(customClasses: Set<String>): Float? {
        for (cls in customClasses) {
            if (cls.startsWith("gap-")) {
                return when (cls.removePrefix("gap-")) {
                    "0" -> 0f
                    "1" -> 4f
                    "2" -> 8f
                    "3" -> 16f
                    "4" -> 24f
                    "5" -> 48f
                    else -> 8f
                }
            }
        }
        return null
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
            val anc = lineage[i]
            for ((k, v) in anc.attributes) {
                if (k.startsWith("--")) {
                    vars[k] = v
                }
            }
            val inlineDecls = anc.inlineComponentStyle?.toCssPropertyMap().orEmpty()
            for ((k, pair) in inlineDecls) {
                if (k.startsWith("--")) {
                    vars[k] = pair.first
                }
            }
        }
        return vars
    }

    private fun resolveActiveSession(node: AlienWidgetNode): AlienRenderSession? {
        var cur: AlienWidgetNode? = node
        while (cur != null) {
            cur.session?.let { return it }
            cur = cur.parent
        }
        return null
    }

    private fun resolveTypeColor(type: BootstrapType, cssVars: Map<String, String>): RgbaColor {
        if (!type.isSemantic) return RgbaColor.TRANSPARENT
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
