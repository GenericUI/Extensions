package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.feedback.Spinner
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Spinner indicator shown when `state.isLoading` is `true`.
 */
open class LoadingIndicator(
    override val elementId: String = "loading-indicator",
    override val slot: OverlaySlot = OverlaySlot.CENTER,
    sizeDp: Int = 64,
    labelText: String? = "Loading..."
) : Container("genericui-overlay-loading-indicator"), OverlayElement {

    private var disposedState: Boolean = false

    override val component: AbstractComponent
        get() = this

    override val isDisposed: Boolean
        get() = disposedState

    val spinner: Spinner = Spinner(
        growStyle = false,
        label = labelText,
        type = BootstrapType.LIGHT
    )

    init {
        htmlTag = "genericui:overlay-loading-indicator"
        setDisplay(DisplayMode.FLEX)
        setAlignItems(AlignItems.CENTER)
        setJustifyContent(JustifyContent.CENTER)
        widthPx = sizeDp
        heightPx = sizeDp
        borderRadiusPx = sizeDp / 2f
        customBackgroundColor = RgbaColor(0, 0, 0, 0x88)
        addChild(spinner)
        isVisible = false
    }

    override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
        isVisible = state.isLoading
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        super<Container>.dispose()
    }
}
