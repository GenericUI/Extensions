package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.style.css.StyledVisualSpec

/**
 * Shared helper synchronizing [StyledVisualSpec] base and interactive (`:hover`, `:focus`,
 * `:active`, `:disabled`) overrides onto an [AlienWidgetNode].
 */
internal object AlienStyledVisualSyncUtil {

    fun resolveInteractiveVisuals(
        node: AlienWidgetNode,
        baseVisuals: StyledVisualSpec,
        hoverVisuals: StyledVisualSpec?,
        focusVisuals: StyledVisualSpec?,
        activeVisuals: StyledVisualSpec?,
        disabledVisuals: StyledVisualSpec?,
        isEnabled: Boolean,
        isHovered: Boolean,
        isFocused: Boolean,
        isActivePressed: Boolean
    ): StyledVisualSpec {
        val computed = node.computedStyle
        val computedBase = StyledVisualSpec.fromComponentAndComputed(node.inlineComponentStyle, computed)
        var effective = computedBase.mergeWith(baseVisuals)

        if (!isEnabled) {
            val computedDisabled = computed?.disabledStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
            return effective.mergeWith(computedDisabled).mergeWith(disabledVisuals)
        }
        if (isHovered) {
            val computedHover = computed?.hoverStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
            effective = effective.mergeWith(computedHover).mergeWith(hoverVisuals)
        }
        if (isFocused) {
            val computedFocus = computed?.focusStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
            effective = effective.mergeWith(computedFocus).mergeWith(focusVisuals)
        }
        if (isActivePressed) {
            val computedActive = computed?.activeStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
            effective = effective.mergeWith(computedActive).mergeWith(activeVisuals)
        }
        return effective
    }

    fun applyVisualsToNode(node: AlienWidgetNode, effective: StyledVisualSpec) {
        node.styledVisualOverride = effective
        node.backgroundGradient = effective.backgroundGradient
        effective.opacity?.let { node.opacity = it.coerceIn(0f, 1f) }
        effective.padding?.let { node.padding = it }
        node.markLayoutDirty()
        node.invalidateBounds()
    }
}
