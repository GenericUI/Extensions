package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.SpacingScale

/**
 * Generic horizontal button bar with [leadingContainer], [centerContainer], and [trailingContainer]
 * component slots so presets and consumer applications can mount custom [OverlayIconButton]s,
 * [OverlayPillButton]s, or arbitrary components.
 */
open class OverlayButtonBar(
    override val elementId: String = "button-bar",
    override val slot: OverlaySlot = OverlaySlot.BOTTOM_BAR
) : Container("genericui-overlay-button-bar"), OverlayElement {

    private var disposedState: Boolean = false

    override val component: AbstractComponent
        get() = this

    override val isDisposed: Boolean
        get() = disposedState

    val leadingContainer: HStack = HStack(gap = SpacingScale.S2).apply {
        addClass("genericui-overlay-button-bar-leading")
        setAlignItems(AlignItems.CENTER)
    }

    val centerContainer: HStack = HStack(gap = SpacingScale.S2).apply {
        addClass("genericui-overlay-button-bar-center")
        setAlignItems(AlignItems.CENTER)
    }

    val trailingContainer: HStack = HStack(gap = SpacingScale.S2).apply {
        addClass("genericui-overlay-button-bar-trailing")
        setAlignItems(AlignItems.CENTER)
    }

    init {
        htmlTag = "genericui:overlay-button-bar"
        setDisplay(DisplayMode.FLEX)
        setFlexDirection(FlexDirection.ROW)
        setAlignItems(AlignItems.CENTER)
        setJustifyContent(JustifyContent.BETWEEN)
        addChild(leadingContainer)
        addChild(centerContainer)
        addChild(trailingContainer)
    }

    fun addLeading(child: AbstractComponent) {
        leadingContainer.addChild(child)
    }

    fun addCenter(child: AbstractComponent) {
        centerContainer.addChild(child)
    }

    fun addTrailing(child: AbstractComponent) {
        trailingContainer.addChild(child)
    }

    fun removeCustomComponent(child: AbstractComponent): Boolean =
        leadingContainer.removeChild(child) ||
            centerContainer.removeChild(child) ||
            trailingContainer.removeChild(child)

    fun clearLeading() {
        leadingContainer.clearChildren()
    }

    fun clearCenter() {
        centerContainer.clearChildren()
    }

    fun clearTrailing() {
        trailingContainer.clearChildren()
    }

    override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
        for (slotGroup in listOf(leadingContainer, centerContainer, trailingContainer)) {
            for (child in slotGroup.children) {
                (child as? OverlayElement)?.onStateChanged(state, actions)
            }
        }
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        super<Container>.dispose()
    }
}
