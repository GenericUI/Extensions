package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Generic chip/badge component rendered from an [OverlayBadgeSpec], supporting both outlined chip
 * styling (`1dp` white stroke, `3dp` radius, `10sp` bold for Mobile top bar) and solid/clickable
 * badge styling (for System+Web [OverlaySlot.BELOW_HOST] strips).
 */
open class OverlayBadge(
    spec: OverlayBadgeSpec,
    outlinedStyle: Boolean = (spec.variant == OverlayBadgeVariant.OUTLINED),
    themeColorHex: String = OverlayExtension.effectiveDefaultThemeColorHex(),
    override val elementId: String = "badge-${spec.id}",
    override val slot: OverlaySlot = OverlaySlot.TOP_BAR,
    var onBadgeClicked: ((OverlayBadgeSpec) -> Unit)? = null
) : Container("genericui-overlay-badge"), OverlayElement {

    private var disposedState: Boolean = false
    private var currentActions: OverlayActions = OverlayActions()

    override val component: AbstractComponent
        get() = this

    override val isDisposed: Boolean
        get() = disposedState

    val textLabel: Label = Label(spec.text, "span", "genericui-overlay-badge-text")

    var spec: OverlayBadgeSpec = spec
        set(value) {
            field = value
            applyBadgeSpec()
        }

    var outlinedStyle: Boolean = outlinedStyle
        set(value) {
            field = value
            applyBadgeSpec()
        }

    var themeColorHex: String = themeColorHex
        set(value) {
            field = value
            applyBadgeSpec()
        }

    init {
        htmlTag = "genericui:overlay-badge"
        addChild(textLabel)
        applyBadgeSpec()
    }

    private fun applyBadgeSpec() {
        val current = spec
        textLabel.text = current.text
        tooltipText = current.tooltip
        isVisible = current.visible
        fontWeight = FontWeight.BOLD
        textLabel.fontWeight = FontWeight.BOLD

        val explicitText = current.textColorHex?.let { GradientScrim.parseOverlayColor(it) }
        val explicitBg = current.backgroundColorHex?.let { GradientScrim.parseOverlayColor(it) }
        val explicitBorder = current.borderColorHex?.let { GradientScrim.parseOverlayColor(it) }

        if (outlinedStyle || current.variant == OverlayBadgeVariant.OUTLINED) {
            // Outlined chip styling: 1dp white stroke, 3dp radius, 10sp bold
            borderRadiusPx = 3f
            setPadding(EdgeInsets.symmetric(vertical = 2, horizontal = 6))
            borderColor = explicitBorder ?: RgbaColor.WHITE
            customBackgroundColor = explicitBg ?: RgbaColor.TRANSPARENT
            val resolvedTextColor = explicitText ?: RgbaColor.WHITE
            textColor = resolvedTextColor
            textLabel.textColor = resolvedTextColor
            setStyleProperty("border-width", "1px")
            setStyleProperty("border-style", "solid")
            setStyleProperty("font-size", "10px")
            textLabel.setStyleProperty("font-size", "10px")
        } else {
            // Solid/clickable badge styling
            borderRadiusPx = 4f
            setPadding(EdgeInsets.symmetric(vertical = 4, horizontal = 8))
            val resolvedBg = explicitBg ?: when (current.variant) {
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
                OverlayBadgeVariant.NEUTRAL,
                OverlayBadgeVariant.SOLID_SECONDARY,
                OverlayBadgeVariant.OUTLINED,
                OverlayBadgeVariant.CUSTOM ->
                    RgbaColor(108, 117, 125, 255)
            }
            customBackgroundColor = resolvedBg
            borderColor = explicitBorder
            val resolvedText = explicitText ?: if (current.variant == OverlayBadgeVariant.WARNING) {
                RgbaColor.BLACK
            } else {
                RgbaColor.WHITE
            }
            textColor = resolvedText
            textLabel.textColor = resolvedText
            setStyleProperty("font-size", "12px")
            textLabel.setStyleProperty("font-size", "12px")
        }

        cursor = if (current.isClickable || onBadgeClicked != null || currentActions.onBadgeClick != null) {
            "pointer"
        } else {
            null
        }
        onClick = {
            current.onClick?.invoke()
            onBadgeClicked?.invoke(current)
            currentActions.onBadgeClick?.invoke(current.id)
        }
    }

    override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
        currentActions = actions
        cursor = if (spec.isClickable || onBadgeClicked != null || actions.onBadgeClick != null) {
            "pointer"
        } else {
            null
        }
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        super<Container>.dispose()
    }
}
