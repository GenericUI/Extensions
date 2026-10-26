package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Centered bottom status pill (`#88000000`, `12sp`, `14/6dp` padding) shown whenever
 * `state.statusText.isNotEmpty()` on [MobileOverlayPreset].
 */
open class OverlayStatusPill(
    override val elementId: String = "status-pill",
    override val slot: OverlaySlot = OverlaySlot.BOTTOM_STATUS
) : Container("genericui-overlay-status-pill"), OverlayElement {

    private var disposedState: Boolean = false

    override val component: AbstractComponent
        get() = this

    override val isDisposed: Boolean
        get() = disposedState

    val textLabel: Label = Label("", "span", "genericui-overlay-status-pill-text").apply {
        textColor = RgbaColor.WHITE
        setStyleProperty("font-size", "12px")
    }

    init {
        htmlTag = "genericui:overlay-status-pill"
        setDisplay(DisplayMode.FLEX)
        setAlignItems(AlignItems.CENTER)
        setJustifyContent(JustifyContent.CENTER)
        customBackgroundColor = RgbaColor(0, 0, 0, 0x88)
        borderRadiusPx = 999f
        setPadding(EdgeInsets.symmetric(vertical = 6, horizontal = 14))
        addChild(textLabel)
        isVisible = false
    }

    override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
        textLabel.text = state.statusText
        isVisible = state.statusText.isNotEmpty()
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        super<Container>.dispose()
    }
}
