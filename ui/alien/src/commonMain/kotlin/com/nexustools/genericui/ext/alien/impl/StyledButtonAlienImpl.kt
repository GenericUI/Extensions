package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.style.css.StyledVisualSpec

/**
 * Self-rendered [StyledButtonNativeImpl] implementation (`StyledButtonAlienImpl`) in `:extensions:alien`.
 */
class StyledButtonAlienImpl(
    componentClassPrefix: String = "styled-btn",
    session: AlienRenderSession? = null
) : ButtonAlienImpl(
    componentClassPrefix = componentClassPrefix,
    session = session
), StyledButtonNativeImpl<Any?> {

    private var baseVisuals: StyledVisualSpec = StyledVisualSpec()
    private var hoverVisuals: StyledVisualSpec? = null
    private var focusVisuals: StyledVisualSpec? = null
    private var activeVisuals: StyledVisualSpec? = null
    private var disabledVisuals: StyledVisualSpec? = null

    private fun syncCurrentVisuals() {
        val current = resolveCurrentVisuals()
        current.backgroundGradient?.let { node.backgroundGradient = it }
        current.opacity?.let { node.opacity = it.coerceIn(0f, 1f) }
        current.padding?.let { node.padding = it }
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setBaseVisuals(visuals: StyledVisualSpec) {
        baseVisuals = visuals
        syncCurrentVisuals()
    }

    override fun getBaseVisuals(): StyledVisualSpec = baseVisuals

    override fun setHoverVisuals(visuals: StyledVisualSpec?) {
        hoverVisuals = visuals
        syncCurrentVisuals()
    }

    override fun getHoverVisuals(): StyledVisualSpec? = hoverVisuals

    override fun setFocusVisuals(visuals: StyledVisualSpec?) {
        focusVisuals = visuals
        syncCurrentVisuals()
    }

    override fun getFocusVisuals(): StyledVisualSpec? = focusVisuals

    override fun setActiveVisuals(visuals: StyledVisualSpec?) {
        activeVisuals = visuals
        syncCurrentVisuals()
    }

    override fun getActiveVisuals(): StyledVisualSpec? = activeVisuals

    override fun setDisabledVisuals(visuals: StyledVisualSpec?) {
        disabledVisuals = visuals
        syncCurrentVisuals()
    }

    override fun getDisabledVisuals(): StyledVisualSpec? = disabledVisuals

    override fun setHoveredState(hovered: Boolean) {
        super<ButtonAlienImpl>.setHoveredState(hovered)
        syncCurrentVisuals()
    }

    override fun setFocusedState(focused: Boolean) {
        super<ButtonAlienImpl>.setFocusedState(focused)
        syncCurrentVisuals()
    }

    override fun setActivePressedState(active: Boolean) {
        super<ButtonAlienImpl>.setActivePressedState(active)
        syncCurrentVisuals()
    }

    override fun resolveCurrentVisuals(): StyledVisualSpec {
        val computed = node.computedStyle
        val computedBase = StyledVisualSpec.fromComponentAndComputed(node.inlineComponentStyle, computed)
        var effective = computedBase.mergeWith(baseVisuals)

        if (!isEnabled()) {
            val computedDisabled = computed?.disabledStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
            effective = effective.mergeWith(computedDisabled).mergeWith(disabledVisuals)
            return effective
        }

        if (isHoveredState()) {
            val computedHover = computed?.hoverStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
            effective = effective.mergeWith(computedHover).mergeWith(hoverVisuals)
        }

        if (isFocusedState()) {
            val computedFocus = computed?.focusStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
            effective = effective.mergeWith(computedFocus).mergeWith(focusVisuals)
        }

        if (isActivePressedState()) {
            val computedActive = computed?.activeStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
            effective = effective.mergeWith(computedActive).mergeWith(activeVisuals)
        }

        return effective
    }
}
