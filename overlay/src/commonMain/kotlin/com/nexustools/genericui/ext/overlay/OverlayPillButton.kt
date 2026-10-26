package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Generic pill/action button for inline action pills, clock/time pills, or custom status triggers
 * with configurable text, [OverlayBadgeVariant], visibility, and click callback.
 */
open class OverlayPillButton(
    override val elementId: String,
    text: String,
    variant: OverlayBadgeVariant = OverlayBadgeVariant.NEUTRAL,
    override val slot: OverlaySlot = OverlaySlot.BOTTOM_BAR,
    themeColorHex: String = OverlayExtension.effectiveDefaultThemeColorHex(),
    var autoHideController: AutoHideController? = null,
    onAction: (() -> Unit)? = null
) : Button(
    text = text,
    icon = null,
    type = BootstrapType.SECONDARY,
    size = BootstrapSize.SM,
    outline = false,
    classPrefix = "btn rounded-pill genericui-overlay-pill-button"
), OverlayElement {

    private var disposedState: Boolean = false

    override val component: AbstractComponent
        get() = this

    override val isDisposed: Boolean
        get() = disposedState

    var variant: OverlayBadgeVariant = variant
        set(value) {
            field = value
            applyPillStyle()
        }

    var themeColorHex: String = themeColorHex
        set(value) {
            field = value
            applyPillStyle()
        }

    var onAction: (() -> Unit)? = onAction

    init {
        htmlTag = "genericui:overlay-pill-button"
        borderRadiusPx = 999f
        setPadding(EdgeInsets.symmetric(vertical = 6, horizontal = 14))
        applyPillStyle()
        super.onClick = {
            autoHideController?.notifyUserActivity()
            this.onAction?.invoke()
        }
    }

    private fun applyPillStyle() {
        val bg = when (variant) {
            OverlayBadgeVariant.ACCENT ->
                GradientScrim.parseOverlayColor(themeColorHex) ?: RgbaColor.PRIMARY_BLUE
            OverlayBadgeVariant.SUCCESS, OverlayBadgeVariant.SOLID_SUCCESS ->
                RgbaColor.GREEN
            OverlayBadgeVariant.WARNING ->
                RgbaColor(255, 193, 7, 255)
            OverlayBadgeVariant.DANGER, OverlayBadgeVariant.SOLID_DANGER ->
                RgbaColor.RED
            OverlayBadgeVariant.SOLID_INFO ->
                RgbaColor(13, 202, 240, 255)
            OverlayBadgeVariant.OUTLINED ->
                RgbaColor(0, 0, 0, 0x88)
            OverlayBadgeVariant.NEUTRAL,
            OverlayBadgeVariant.SOLID_SECONDARY,
            OverlayBadgeVariant.CUSTOM ->
                RgbaColor(0, 0, 0, 0x88)
        }
        customBackgroundColor = bg
        textColor = if (variant == OverlayBadgeVariant.WARNING) RgbaColor.BLACK else RgbaColor.WHITE
        if (variant == OverlayBadgeVariant.OUTLINED) {
            borderColor = RgbaColor.WHITE
            setStyleProperty("border-width", "1px")
            setStyleProperty("border-style", "solid")
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
