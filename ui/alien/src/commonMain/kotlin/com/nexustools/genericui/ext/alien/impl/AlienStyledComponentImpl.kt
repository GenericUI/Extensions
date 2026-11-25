package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.style.css.StyledVisualSpec

/**
 * Self-rendered [StyledComponentNativeImpl] implementation (`AlienStyledComponentImpl`) in
 * `:extensions:alien`, supporting full [StyledVisualSpec] properties and interactive `:hover`,
 * `:focus`, `:active`, and `:disabled` visual state overrides.
 */
class AlienStyledComponentImpl(
    componentClassPrefix: String = "styled-component",
    tagName: String = "genericui:styled-component",
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = componentClassPrefix,
    tagName = tagName,
    session = session
), StyledComponentNativeImpl<Any?> {

    private var baseVisuals: StyledVisualSpec = StyledVisualSpec()
    private var hoverVisuals: StyledVisualSpec? = null
    private var focusVisuals: StyledVisualSpec? = null
    private var activeVisuals: StyledVisualSpec? = null
    private var disabledVisuals: StyledVisualSpec? = null

    private fun syncCurrentVisuals() {
        AlienStyledVisualSyncUtil.applyVisualsToNode(node, resolveCurrentVisuals())
    }

    override fun setStyledVisuals(visuals: StyledVisualSpec) {
        baseVisuals = visuals
        syncCurrentVisuals()
    }

    override fun getStyledVisuals(): StyledVisualSpec =
        StyledVisualSpec.fromComponentAndComputed(node.inlineComponentStyle, node.computedStyle)
            .mergeWith(baseVisuals)

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
        super<AlienImpl>.setHoveredState(hovered)
        syncCurrentVisuals()
    }

    override fun setFocusedState(focused: Boolean) {
        super<AlienImpl>.setFocusedState(focused)
        syncCurrentVisuals()
    }

    override fun setActivePressedState(active: Boolean) {
        super<AlienImpl>.setActivePressedState(active)
        syncCurrentVisuals()
    }

    override fun setEnabled(enabled: Boolean) {
        super<AlienImpl>.setEnabled(enabled)
        syncCurrentVisuals()
    }

    override fun resolveCurrentVisuals(): StyledVisualSpec =
        AlienStyledVisualSyncUtil.resolveInteractiveVisuals(
            node = node,
            baseVisuals = baseVisuals,
            hoverVisuals = hoverVisuals,
            focusVisuals = focusVisuals,
            activeVisuals = activeVisuals,
            disabledVisuals = disabledVisuals,
            isEnabled = isEnabled(),
            isHovered = isHoveredState(),
            isFocused = isFocusedState(),
            isActivePressed = isActivePressedState()
        )
}
