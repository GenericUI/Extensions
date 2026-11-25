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
        AlienStyledVisualSyncUtil.applyVisualsToNode(node, resolveCurrentVisuals())
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

    override fun setEnabled(enabled: Boolean) {
        super<ButtonAlienImpl>.setEnabled(enabled)
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
