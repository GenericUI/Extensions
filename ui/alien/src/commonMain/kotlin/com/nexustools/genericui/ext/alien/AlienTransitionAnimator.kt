package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.enums.css.CssUnit
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssShadow
import com.nexustools.genericui.style.css.CssShadowList
import com.nexustools.genericui.style.transition.CssTimingFunction
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.style.transition.CssTransitionSpec
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Per-node CSS transition and keyframe animation engine for [AlienWidgetNode].
 *
 * Supports:
 * - Default Bootstrap 5.3 `.15s ease-in-out` interactive transitions on `background-color`, `border-color`,
 *   `color`, `box-shadow`, and `opacity` when hovering, focusing, or pressing controls.
 * - Custom `transition:` specs across all animatable CSS properties (`all`, `background-color`, `color`,
 *   `border-color`, `accent-color`, `border-width`, `border-radius`, `opacity`, `transform`, `box-shadow`,
 *   `text-shadow`, `font-size`, `font-weight`, `line-height`, `letter-spacing`, `padding`, `margin`,
 *   `gap`, `row-gap`, `column-gap`, `flex-grow`, `flex-shrink`, `width`, `height`, `min-width`, `min-height`,
 *   `max-width`, `max-height`, `left`, `top`, `right`, `bottom`, `filter`, `backdrop-filter`) with cubic-Bezier timing curves.
 * - Continuous `@keyframes` animations (`spin`, `pulse`, `fade`) for `Spinner`, `ProgressBar` animated stripes,
 *   and custom animated elements.
 */
class AlienTransitionAnimator {

    private class ActivePropertyTransition(
        val property: String,
        val durationMs: Float,
        val delayMs: Float,
        val timing: CssTimingFunction,
        var elapsedMs: Float = 0f,
        val onComplete: (() -> Unit)? = null
    ) {
        val isComplete: Boolean
            get() = elapsedMs >= delayMs + durationMs

        fun progress(): Float {
            if (elapsedMs <= delayMs) return 0f
            val activeElapsed = (elapsedMs - delayMs).coerceIn(0f, durationMs)
            val rawT = if (durationMs <= 0f) 1f else (activeElapsed / durationMs).coerceIn(0f, 1f)
            return timing.evaluate(rawT)
        }
    }

    private var configuredSpecs: List<CssTransitionSpec> = emptyList()
    private val activeTransitions = linkedMapOf<String, ActivePropertyTransition>()

    private var previousTargetStyle: AlienResolvedVisualStyle? = null
    private var startStyleSnapshot: AlienResolvedVisualStyle? = null
    private var currentInterpolatedStyle: AlienResolvedVisualStyle? = null

    var continuousAnimationPhaseRad: Float = 0f
        private set

    val hasConfiguredTransitions: Boolean
        get() = configuredSpecs.isNotEmpty()

    val isAnimating: Boolean
        get() = activeTransitions.isNotEmpty()

    fun configureTransitions(specs: List<CssTransitionSpec>) {
        configuredSpecs = specs
    }

    fun startTransition(
        property: String,
        spec: CssTransitionSpec,
        onComplete: (() -> Unit)? = null
    ) {
        val norm = property.trim().lowercase()
        if (currentInterpolatedStyle != null) {
            startStyleSnapshot = currentInterpolatedStyle
        }
        activeTransitions[norm] = ActivePropertyTransition(
            property = norm,
            durationMs = spec.durationMs.coerceAtLeast(1f),
            delayMs = spec.delayMs.coerceAtLeast(0f),
            timing = spec.timingFunction,
            elapsedMs = 0f,
            onComplete = onComplete
        )
    }

    /**
     * Evaluates or initiates smooth transitions toward [targetStyle] and returns the effective
     * [AlienResolvedVisualStyle] for the current frame.
     */
    fun resolveVisualStyleForFrame(
        targetStyle: AlienResolvedVisualStyle,
        enableDefaultBootstrapHoverTransition: Boolean = true
    ): AlienResolvedVisualStyle {
        val prev = previousTargetStyle
        if (prev == null) {
            previousTargetStyle = targetStyle
            startStyleSnapshot = targetStyle
            currentInterpolatedStyle = targetStyle
            return targetStyle
        }

        if (prev != targetStyle) {
            val fromSnap = currentInterpolatedStyle ?: prev
            startStyleSnapshot = fromSnap
            previousTargetStyle = targetStyle

            val defaultSpec = if (enableDefaultBootstrapHoverTransition) {
                CssTransitionSpec(
                    property = "all",
                    durationMs = 150f,
                    timingFunction = CssTimingFunction.EASE_IN_OUT,
                    delayMs = 0f
                )
            } else {
                null
            }

            if (fromSnap.backgroundColor != targetStyle.backgroundColor) {
                findSpec("background-color", defaultSpec)?.let { startTransition("background-color", it) }
            }
            if (fromSnap.textColor != targetStyle.textColor) {
                findSpec("color", defaultSpec)?.let { startTransition("color", it) }
            }
            if (fromSnap.borderColor != targetStyle.borderColor) {
                findSpec("border-color", defaultSpec)?.let { startTransition("border-color", it) }
            }
            if (fromSnap.accentColor != targetStyle.accentColor) {
                findSpec("accent-color", defaultSpec)?.let { startTransition("accent-color", it) }
            }
            if (fromSnap.borderWidthPx != targetStyle.borderWidthPx) {
                findSpec("border-width", null)?.let { startTransition("border-width", it) }
            }
            if (fromSnap.borderRadiusPx != targetStyle.borderRadiusPx ||
                fromSnap.borderRadiusPercent != targetStyle.borderRadiusPercent ||
                fromSnap.cornerRadii != targetStyle.cornerRadii
            ) {
                findSpec("border-radius", null)?.let { startTransition("border-radius", it) }
            }
            if (fromSnap.opacity != targetStyle.opacity) {
                findSpec("opacity", defaultSpec)?.let { startTransition("opacity", it) }
            }
            if (fromSnap.transform2D != targetStyle.transform2D) {
                findSpec("transform", null)?.let { startTransition("transform", it) }
            }
            if (fromSnap.focusRingSpreadPx != targetStyle.focusRingSpreadPx ||
                fromSnap.boxShadowCss != targetStyle.boxShadowCss ||
                fromSnap.boxShadowList != targetStyle.boxShadowList
            ) {
                findSpec("box-shadow", defaultSpec)?.let { startTransition("box-shadow", it) }
            }
            if (fromSnap.textShadow != targetStyle.textShadow || fromSnap.textShadowCss != targetStyle.textShadowCss) {
                findSpec("text-shadow", null)?.let { startTransition("text-shadow", it) }
            }
            if (fromSnap.fontSizePx != targetStyle.fontSizePx) {
                findSpec("font-size", null)?.let { startTransition("font-size", it) }
            }
            if (fromSnap.fontWeight != targetStyle.fontWeight) {
                findSpec("font-weight", null)?.let { startTransition("font-weight", it) }
            }
            if (fromSnap.lineHeightPx != targetStyle.lineHeightPx) {
                findSpec("line-height", null)?.let { startTransition("line-height", it) }
            }
            if (fromSnap.letterSpacingPx != targetStyle.letterSpacingPx) {
                findSpec("letter-spacing", null)?.let { startTransition("letter-spacing", it) }
            }
            if (fromSnap.padding != targetStyle.padding) {
                (findSpec("padding", null)
                    ?: findSpec("padding-top", null)
                    ?: findSpec("padding-right", null)
                    ?: findSpec("padding-bottom", null)
                    ?: findSpec("padding-left", null))?.let { startTransition("padding", it) }
            }
            if (fromSnap.margin != targetStyle.margin) {
                (findSpec("margin", null)
                    ?: findSpec("margin-top", null)
                    ?: findSpec("margin-right", null)
                    ?: findSpec("margin-bottom", null)
                    ?: findSpec("margin-left", null))?.let { startTransition("margin", it) }
            }
            if (fromSnap.gapPx != targetStyle.gapPx ||
                fromSnap.rowGapPx != targetStyle.rowGapPx ||
                fromSnap.columnGapPx != targetStyle.columnGapPx
            ) {
                (findSpec("gap", null)
                    ?: findSpec("row-gap", null)
                    ?: findSpec("column-gap", null))?.let { startTransition("gap", it) }
            }
            if (fromSnap.flexGrow != targetStyle.flexGrow) {
                findSpec("flex-grow", null)?.let { startTransition("flex-grow", it) }
            }
            if (fromSnap.flexShrink != targetStyle.flexShrink) {
                findSpec("flex-shrink", null)?.let { startTransition("flex-shrink", it) }
            }
            if (fromSnap.widthCss != targetStyle.widthCss) {
                findSpec("width", null)?.let { startTransition("width", it) }
            }
            if (fromSnap.heightCss != targetStyle.heightCss) {
                findSpec("height", null)?.let { startTransition("height", it) }
            }
            if (fromSnap.minWidthCss != targetStyle.minWidthCss) {
                findSpec("min-width", null)?.let { startTransition("min-width", it) }
            }
            if (fromSnap.minHeightCss != targetStyle.minHeightCss) {
                findSpec("min-height", null)?.let { startTransition("min-height", it) }
            }
            if (fromSnap.maxWidthCss != targetStyle.maxWidthCss) {
                findSpec("max-width", null)?.let { startTransition("max-width", it) }
            }
            if (fromSnap.maxHeightCss != targetStyle.maxHeightCss) {
                findSpec("max-height", null)?.let { startTransition("max-height", it) }
            }
            if (fromSnap.leftCss != targetStyle.leftCss) {
                findSpec("left", null)?.let { startTransition("left", it) }
            }
            if (fromSnap.topCss != targetStyle.topCss) {
                findSpec("top", null)?.let { startTransition("top", it) }
            }
            if (fromSnap.rightCss != targetStyle.rightCss) {
                findSpec("right", null)?.let { startTransition("right", it) }
            }
            if (fromSnap.bottomCss != targetStyle.bottomCss) {
                findSpec("bottom", null)?.let { startTransition("bottom", it) }
            }
            if (fromSnap.filter != targetStyle.filter) {
                findSpec("filter", null)?.let { startTransition("filter", it) }
            }
            if (fromSnap.backdropFilter != targetStyle.backdropFilter) {
                findSpec("backdrop-filter", null)?.let { startTransition("backdrop-filter", it) }
            }
        }

        if (activeTransitions.isEmpty()) {
            currentInterpolatedStyle = targetStyle
            return targetStyle
        }

        val start = startStyleSnapshot ?: targetStyle
        val bgT = progressFor("background-color")
        val textT = progressFor("color")
        val borderT = progressFor("border-color")
        val accentT = progressFor("accent-color")
        val borderWidthT = progressFor("border-width")
        val radiusT = progressFor("border-radius")
        val opacityT = progressFor("opacity")
        val transformT = progressFor("transform")
        val shadowT = progressFor("box-shadow")
        val textShadowT = progressFor("text-shadow")
        val fontSizeT = progressFor("font-size")
        val fontWeightT = progressFor("font-weight")
        val lineHeightT = progressFor("line-height")
        val letterSpacingT = progressFor("letter-spacing")
        val paddingT = progressFor("padding", "padding-top", "padding-right", "padding-bottom", "padding-left")
        val marginT = progressFor("margin", "margin-top", "margin-right", "margin-bottom", "margin-left")
        val gapT = progressFor("gap", "row-gap", "column-gap")
        val flexGrowT = progressFor("flex-grow")
        val flexShrinkT = progressFor("flex-shrink")
        val widthT = progressFor("width")
        val heightT = progressFor("height")
        val minWidthT = progressFor("min-width")
        val minHeightT = progressFor("min-height")
        val maxWidthT = progressFor("max-width")
        val maxHeightT = progressFor("max-height")
        val leftT = progressFor("left")
        val topT = progressFor("top")
        val rightT = progressFor("right")
        val bottomT = progressFor("bottom")
        val filterT = progressFor("filter")
        val backdropFilterT = progressFor("backdrop-filter")

        val interpolatedBoxShadows = if (shadowT < 1f) {
            lerpShadowList(start.boxShadowList, targetStyle.boxShadowList, shadowT)
        } else {
            targetStyle.boxShadowList
        }
        val interpolatedTextShadow = if (textShadowT < 1f) {
            lerpShadow(start.textShadow, targetStyle.textShadow, textShadowT)
        } else {
            targetStyle.textShadow
        }

        val resolved = targetStyle.copy(
            backgroundColor = if (bgT < 1f) lerpColor(start.backgroundColor, targetStyle.backgroundColor, bgT) else targetStyle.backgroundColor,
            textColor = if (textT < 1f) lerpColor(start.textColor, targetStyle.textColor, textT) else targetStyle.textColor,
            borderColor = if (borderT < 1f) lerpColor(start.borderColor, targetStyle.borderColor, borderT) else targetStyle.borderColor,
            accentColor = if (accentT < 1f) lerpColor(start.accentColor, targetStyle.accentColor, accentT) else targetStyle.accentColor,
            borderWidthPx = if (borderWidthT < 1f) lerpFloat(start.borderWidthPx, targetStyle.borderWidthPx, borderWidthT) else targetStyle.borderWidthPx,
            borderRadiusPx = if (radiusT < 1f) lerpFloat(start.borderRadiusPx, targetStyle.borderRadiusPx, radiusT) else targetStyle.borderRadiusPx,
            borderRadiusPercent = if (radiusT < 1f && start.borderRadiusPercent != null && targetStyle.borderRadiusPercent != null) {
                lerpFloat(start.borderRadiusPercent, targetStyle.borderRadiusPercent, radiusT)
            } else {
                targetStyle.borderRadiusPercent
            },
            cornerRadii = if (radiusT < 1f) lerpCornerRadii(start.cornerRadii, targetStyle.cornerRadii, radiusT) else targetStyle.cornerRadii,
            opacity = if (opacityT < 1f) lerpFloat(start.opacity, targetStyle.opacity, opacityT) else targetStyle.opacity,
            transform2D = if (transformT < 1f) start.transform2D.interpolateTo(targetStyle.transform2D, transformT) else targetStyle.transform2D,
            focusRingSpreadPx = if (shadowT < 1f) lerpFloat(start.focusRingSpreadPx, targetStyle.focusRingSpreadPx, shadowT) else targetStyle.focusRingSpreadPx,
            boxShadowList = interpolatedBoxShadows,
            boxShadowCss = if (shadowT < 1f && interpolatedBoxShadows != null) interpolatedBoxShadows.toCssString() else targetStyle.boxShadowCss,
            textShadow = interpolatedTextShadow,
            textShadowCss = if (textShadowT < 1f && interpolatedTextShadow != null) interpolatedTextShadow.toCssString() else targetStyle.textShadowCss,
            fontSizePx = if (fontSizeT < 1f) lerpFloat(start.fontSizePx, targetStyle.fontSizePx, fontSizeT) else targetStyle.fontSizePx,
            fontWeight = if (fontWeightT < 1f) lerpInt(start.fontWeight, targetStyle.fontWeight, fontWeightT).coerceIn(100, 900) else targetStyle.fontWeight,
            lineHeightPx = if (lineHeightT < 1f && (start.lineHeightPx != null || targetStyle.lineHeightPx != null)) {
                lerpFloat(start.lineHeightPx ?: (start.fontSizePx * 1.35f), targetStyle.lineHeightPx ?: (targetStyle.fontSizePx * 1.35f), lineHeightT)
            } else {
                targetStyle.lineHeightPx
            },
            letterSpacingPx = if (letterSpacingT < 1f) lerpFloat(start.letterSpacingPx, targetStyle.letterSpacingPx, letterSpacingT) else targetStyle.letterSpacingPx,
            padding = if (paddingT < 1f) lerpEdgeInsets(start.padding, targetStyle.padding, paddingT) else targetStyle.padding,
            margin = if (marginT < 1f) lerpEdgeInsets(start.margin, targetStyle.margin, marginT) else targetStyle.margin,
            gapPx = if (gapT < 1f) lerpFloat(start.gapPx, targetStyle.gapPx, gapT) else targetStyle.gapPx,
            rowGapPx = if (gapT < 1f) lerpFloat(start.rowGapPx, targetStyle.rowGapPx, gapT) else targetStyle.rowGapPx,
            columnGapPx = if (gapT < 1f) lerpFloat(start.columnGapPx, targetStyle.columnGapPx, gapT) else targetStyle.columnGapPx,
            flexGrow = if (flexGrowT < 1f) lerpFloat(start.flexGrow, targetStyle.flexGrow, flexGrowT) else targetStyle.flexGrow,
            flexShrink = if (flexShrinkT < 1f) lerpFloat(start.flexShrink, targetStyle.flexShrink, flexShrinkT) else targetStyle.flexShrink,
            widthCss = if (widthT < 1f) lerpCssDimensionString(start.widthCss, targetStyle.widthCss, widthT) else targetStyle.widthCss,
            heightCss = if (heightT < 1f) lerpCssDimensionString(start.heightCss, targetStyle.heightCss, heightT) else targetStyle.heightCss,
            minWidthCss = if (minWidthT < 1f) lerpCssDimensionString(start.minWidthCss, targetStyle.minWidthCss, minWidthT) else targetStyle.minWidthCss,
            minHeightCss = if (minHeightT < 1f) lerpCssDimensionString(start.minHeightCss, targetStyle.minHeightCss, minHeightT) else targetStyle.minHeightCss,
            maxWidthCss = if (maxWidthT < 1f) lerpCssDimensionString(start.maxWidthCss, targetStyle.maxWidthCss, maxWidthT) else targetStyle.maxWidthCss,
            maxHeightCss = if (maxHeightT < 1f) lerpCssDimensionString(start.maxHeightCss, targetStyle.maxHeightCss, maxHeightT) else targetStyle.maxHeightCss,
            leftCss = if (leftT < 1f) lerpCssDimensionString(start.leftCss, targetStyle.leftCss, leftT) else targetStyle.leftCss,
            topCss = if (topT < 1f) lerpCssDimensionString(start.topCss, targetStyle.topCss, topT) else targetStyle.topCss,
            rightCss = if (rightT < 1f) lerpCssDimensionString(start.rightCss, targetStyle.rightCss, rightT) else targetStyle.rightCss,
            bottomCss = if (bottomT < 1f) lerpCssDimensionString(start.bottomCss, targetStyle.bottomCss, bottomT) else targetStyle.bottomCss,
            filter = if (filterT < 1f) lerpFilter(start.filter, targetStyle.filter, filterT) else targetStyle.filter,
            backdropFilter = if (backdropFilterT < 1f) lerpFilter(start.backdropFilter, targetStyle.backdropFilter, backdropFilterT) else targetStyle.backdropFilter
        )
        currentInterpolatedStyle = resolved
        return resolved
    }

    /**
     * Advances active transitions and continuous animations by [deltaMs] milliseconds.
     * Returns `true` if any visual property changed and requires a dirty region repaint.
     */
    fun step(deltaMs: Float, hasContinuousAnimation: Boolean = false): Boolean {
        val safeDelta = deltaMs.coerceAtLeast(0f)
        var changed = false

        if (hasContinuousAnimation && safeDelta > 0f) {
            continuousAnimationPhaseRad = (continuousAnimationPhaseRad + (safeDelta / 750f) * 6.2831855f) % 6.2831855f
            changed = true
        }

        if (activeTransitions.isEmpty()) return changed

        val completedCallbacks = mutableListOf<() -> Unit>()
        val iterator = activeTransitions.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            entry.elapsedMs += safeDelta
            changed = true
            if (entry.isComplete) {
                entry.onComplete?.let { completedCallbacks.add(it) }
                iterator.remove()
            }
        }

        for (cb in completedCallbacks) {
            cb()
        }
        return changed
    }

    private fun findSpec(property: String, fallback: CssTransitionSpec?): CssTransitionSpec? =
        CssTransitionSpec.findMatching(configuredSpecs, property) ?: fallback

    private fun progressFor(vararg properties: String): Float {
        for (prop in properties) {
            activeTransitions[prop]?.let { return it.progress() }
        }
        return activeTransitions["all"]?.progress() ?: 1f
    }

    companion object {
        fun lerpFloat(a: Float, b: Float, t: Float): Float =
            a + (b - a) * t.coerceIn(0f, 1f)

        fun lerpInt(a: Int, b: Int, t: Float): Int =
            (a + (b - a) * t.coerceIn(0f, 1f)).roundToInt()

        fun lerpColor(from: RgbaColor, to: RgbaColor, t: Float): RgbaColor {
            val clamped = t.coerceIn(0f, 1f)
            return RgbaColor(
                r = (from.r + (to.r - from.r) * clamped).roundToInt().coerceIn(0, 255),
                g = (from.g + (to.g - from.g) * clamped).roundToInt().coerceIn(0, 255),
                b = (from.b + (to.b - from.b) * clamped).roundToInt().coerceIn(0, 255),
                a = (from.a + (to.a - from.a) * clamped).roundToInt().coerceIn(0, 255)
            )
        }

        fun lerpEdgeInsets(from: EdgeInsets, to: EdgeInsets, t: Float): EdgeInsets =
            EdgeInsets(
                top = lerpInt(from.top, to.top, t),
                right = lerpInt(from.right, to.right, t),
                bottom = lerpInt(from.bottom, to.bottom, t),
                left = lerpInt(from.left, to.left, t)
            )

        fun lerpCssLength(from: CssLength, to: CssLength, t: Float): CssLength {
            val unit = if (to.unit != CssUnit.AUTO && to.unit != CssUnit.NONE) to.unit else from.unit
            return CssLength(lerpFloat(from.value, to.value, t), unit)
        }

        fun lerpCornerRadii(from: CssCornerRadii?, to: CssCornerRadii?, t: Float): CssCornerRadii? {
            if (from == null && to == null) return null
            val a = from ?: CssCornerRadii.ZERO
            val b = to ?: CssCornerRadii.ZERO
            return CssCornerRadii(
                topLeft = lerpCssLength(a.topLeft, b.topLeft, t),
                topRight = lerpCssLength(a.topRight, b.topRight, t),
                bottomRight = lerpCssLength(a.bottomRight, b.bottomRight, t),
                bottomLeft = lerpCssLength(a.bottomLeft, b.bottomLeft, t)
            )
        }

        fun lerpShadow(from: CssShadow?, to: CssShadow?, t: Float): CssShadow? {
            if (from == null && to == null) return null
            val zero = CssShadow.px(0f, 0f, 0f, 0f, RgbaColor.TRANSPARENT)
            val a = from ?: zero.copy(inset = to?.inset ?: false)
            val b = to ?: zero.copy(inset = from?.inset ?: false)
            return CssShadow(
                offsetX = lerpCssLength(a.offsetX, b.offsetX, t),
                offsetY = lerpCssLength(a.offsetY, b.offsetY, t),
                blurRadius = lerpCssLength(a.blurRadius, b.blurRadius, t),
                spreadRadius = lerpCssLength(a.spreadRadius, b.spreadRadius, t),
                color = lerpColor(a.color, b.color, t),
                inset = if (t < 0.5f) a.inset else b.inset
            )
        }

        fun lerpShadowList(from: CssShadowList?, to: CssShadowList?, t: Float): CssShadowList? {
            if (from == null && to == null) return null
            val listA = from?.shadows.orEmpty()
            val listB = to?.shadows.orEmpty()
            val count = max(listA.size, listB.size)
            if (count == 0) return if (t < 0.5f) from else to
            val interpolated = (0 until count).mapNotNull { idx ->
                lerpShadow(listA.getOrNull(idx), listB.getOrNull(idx), t)
            }
            return CssShadowList(interpolated, isNone = false)
        }

        fun lerpFilter(from: CssFilter?, to: CssFilter?, t: Float): CssFilter? {
            if (from == null && to == null) return null
            val a = from ?: CssFilter.NONE
            val b = to ?: CssFilter.NONE
            return a.interpolateTo(b, t)
        }

        fun lerpCssDimensionString(from: String?, to: String?, t: Float): String? {
            if (from == null || to == null) return if (t < 0.5f) from else to
            val parsedA = CssLength.parse(from)
            val parsedB = CssLength.parse(to)
            if (parsedA != null && parsedB != null && !parsedA.isAuto && !parsedA.isNone && !parsedB.isAuto && !parsedB.isNone) {
                return lerpCssLength(parsedA, parsedB, t).toCssString()
            }
            return if (t < 0.5f) from else to
        }
    }
}
