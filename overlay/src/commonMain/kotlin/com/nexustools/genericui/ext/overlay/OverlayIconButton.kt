package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Generic icon/symbol button supporting circle styling (`44dp`/`56dp`/`64dp` with `#88000000`
 * background for Mobile) and flat icon button styling (for System+Web), with tooltip and click
 * callback that automatically notifies [AutoHideController.notifyUserActivity].
 */
open class OverlayIconButton(
    override val elementId: String,
    symbol: String,
    tooltip: String? = null,
    override val slot: OverlaySlot = OverlaySlot.BOTTOM_BAR,
    circleSizeDp: Int? = null,
    var autoHideController: AutoHideController? = null,
    onAction: (() -> Unit)? = null
) : Button(
    text = symbol,
    icon = symbol,
    type = BootstrapType.SECONDARY,
    size = BootstrapSize.SM,
    outline = false,
    classPrefix = "btn genericui-overlay-icon-button"
), OverlayElement {

    private var disposedState: Boolean = false

    override val component: AbstractComponent
        get() = this

    override val isDisposed: Boolean
        get() = disposedState

    var symbol: String = symbol
        set(value) {
            field = value
            text = value
            icon = value
        }

    var circleSizeDp: Int? = circleSizeDp
        set(value) {
            field = value
            applyVisualStyle()
        }

    var onAction: (() -> Unit)? = onAction

    init {
        htmlTag = "genericui:overlay-icon-button"
        this.tooltipText = tooltip
        applyVisualStyle()
        super.onClick = {
            autoHideController?.notifyUserActivity()
            this.onAction?.invoke()
        }
    }

    private fun applyVisualStyle() {
        val diameter = circleSizeDp
        textColor = RgbaColor.WHITE
        cursor = "pointer"
        if (diameter != null && diameter > 0) {
            widthPx = diameter
            heightPx = diameter
            borderRadiusPx = diameter / 2f
            customBackgroundColor = RgbaColor(0, 0, 0, 0x88)
        } else {
            widthPx = null
            heightPx = null
            borderRadiusPx = 4f
            customBackgroundColor = RgbaColor.TRANSPARENT
        }
    }

    fun triggerClick() {
        onClick?.invoke()
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        super<Button>.dispose()
    }
}
