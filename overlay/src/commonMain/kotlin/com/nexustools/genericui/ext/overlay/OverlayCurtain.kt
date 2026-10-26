package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Generic full-surface curtain container in [OverlaySlot.CURTAIN] that pins [AutoHideController]
 * (`"curtain"`) when visible so consumer applications can display a custom full-surface state.
 */
open class OverlayCurtain(
    override val elementId: String = "overlay-curtain",
    override val slot: OverlaySlot = OverlaySlot.CURTAIN,
    var autoHideController: AutoHideController? = null,
    initialVisible: Boolean = false
) : Container("genericui-overlay-curtain"), OverlayElement {

    private var disposedState: Boolean = false

    override val component: AbstractComponent
        get() = this

    override val isDisposed: Boolean
        get() = disposedState

    var contentComponent: AbstractComponent? = null
        private set

    override var isVisible: Boolean
        get() = super.isVisible
        set(value) {
            super.isVisible = value
            if (!disposedState) {
                autoHideController?.setPinned(PIN_REASON_CURTAIN, value)
            }
        }

    init {
        htmlTag = "genericui:overlay-curtain"
        positionMode = PositionMode.ABSOLUTE
        leftOffset = "0px"
        topOffset = "0px"
        setDisplay(DisplayMode.FLEX)
        setAlignItems(AlignItems.CENTER)
        setJustifyContent(JustifyContent.CENTER)
        customBackgroundColor = RgbaColor(0, 0, 0, 0xCC)
        isVisible = initialVisible
    }

    fun setCurtainContent(content: AbstractComponent?, show: Boolean = content != null) {
        contentComponent?.let { removeChild(it) }
        contentComponent = content
        if (content != null) {
            addChild(content)
        }
        isVisible = show
    }

    fun showCurtain() {
        isVisible = true
    }

    fun hideCurtain() {
        isVisible = false
    }

    override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
        (contentComponent as? OverlayElement)?.onStateChanged(state, actions)
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        autoHideController?.setPinned(PIN_REASON_CURTAIN, false)
        super<Container>.dispose()
    }

    companion object {
        const val PIN_REASON_CURTAIN: String = "curtain"
    }
}
