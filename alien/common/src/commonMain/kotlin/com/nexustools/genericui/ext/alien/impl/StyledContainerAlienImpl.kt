package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.style.css.StyledVisualSpec

/**
 * Self-rendered [StyledContainerNativeImpl] implementation (`StyledContainerAlienImpl`) in `:extensions:alien`.
 */
class StyledContainerAlienImpl(
    componentClassPrefix: String = "styled-container",
    tagName: String = "genericui:styled-container",
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = componentClassPrefix,
    tagName = tagName,
    session = session
), StyledContainerNativeImpl<Any?> {

    private var styledVisuals: StyledVisualSpec = StyledVisualSpec()

    override fun setStyledVisuals(visuals: StyledVisualSpec) {
        styledVisuals = visuals
        visuals.backgroundGradient?.let { node.backgroundGradient = it }
        visuals.opacity?.let { node.opacity = it.coerceIn(0f, 1f) }
        visuals.padding?.let { node.padding = it }
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun getStyledVisuals(): StyledVisualSpec {
        val computedBase = StyledVisualSpec.fromComponentAndComputed(node.inlineComponentStyle, node.computedStyle)
        return computedBase.mergeWith(styledVisuals)
    }
}
