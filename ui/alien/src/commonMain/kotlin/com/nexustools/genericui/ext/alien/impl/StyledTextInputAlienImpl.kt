package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienResolvedVisualStyle
import com.nexustools.genericui.nativeimpl.form.StyledTextInputNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.StyledVisualSpec

/**
 * Self-rendered [StyledTextInputNativeImpl] implementation (`StyledTextInputAlienImpl`) in `:extensions:alien`.
 */
class StyledTextInputAlienImpl(
    multiline: Boolean = false,
    componentClassPrefix: String = if (multiline) "styled-textarea" else "styled-textfield",
    session: AlienRenderSession? = null
) : TextInputAlienImpl(
    multiline = multiline,
    componentClassPrefix = componentClassPrefix,
    tagName = if (multiline) "textarea" else "input",
    session = session
), StyledTextInputNativeImpl<Any?> {

    private var baseVisuals: StyledVisualSpec = StyledVisualSpec()
    private var hoverVisuals: StyledVisualSpec? = null
    private var focusVisuals: StyledVisualSpec? = null
    private var activeVisuals: StyledVisualSpec? = null
    private var disabledVisuals: StyledVisualSpec? = null

    private fun syncCurrentVisuals() {
        AlienStyledVisualSyncUtil.applyVisualsToNode(node, resolveCurrentVisuals())
    }

    override fun resolvePlaceholderColor(style: AlienResolvedVisualStyle): RgbaColor =
        resolveCurrentVisuals().placeholderColor ?: super.resolvePlaceholderColor(style)

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
        super<TextInputAlienImpl>.setHoveredState(hovered)
        syncCurrentVisuals()
    }

    override fun setFocusedState(focused: Boolean) {
        super<TextInputAlienImpl>.setFocusedState(focused)
        syncCurrentVisuals()
    }

    override fun setActivePressedState(active: Boolean) {
        super<TextInputAlienImpl>.setActivePressedState(active)
        syncCurrentVisuals()
    }

    override fun setEnabled(enabled: Boolean) {
        super<TextInputAlienImpl>.setEnabled(enabled)
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
