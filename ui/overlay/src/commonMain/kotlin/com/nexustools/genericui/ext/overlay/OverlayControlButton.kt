package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.action.StyledButton
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength

/**
 * Base cross-platform overlay transport/action [StyledButton] backed by `:extensions:ui:overlay`
 * `R.drawable.*` vector icons and optional hardware `backdropFilter` glass blur.
 *
 * Intentionally leaves [tooltipText] `null` unless explicitly provided by the consumer.
 */
open class OverlayControlButton(
    iconType: OverlayIconType? = null,
    variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
    accentColor: RgbaColor = DEFAULT_ACCENT_COLOR,
    backdropFilter: CssFilter? = null,
    tooltipText: String? = null
) : StyledButton(
    text = "",
    icon = iconType?.let { OverlayIcons.uriFor(it) },
    type = BootstrapType.NONE,
    size = when (variant) {
        OverlayButtonVariant.CENTER_PRIMARY,
        OverlayButtonVariant.CENTER_SECONDARY -> BootstrapSize.LG
        OverlayButtonVariant.CIRCLE_PILL,
        OverlayButtonVariant.INNER_PILL -> BootstrapSize.SM
    },
    outline = false
) {

    var iconType: OverlayIconType? = iconType
        set(value) {
            field = value
            applyIconType(value)
        }

    var variant: OverlayButtonVariant = variant
        set(value) {
            if (field != value) {
                field = value
                applyVariantStyle(value, frosted = this.backdropFilter != null)
            }
        }

    var accentColor: RgbaColor = accentColor
        set(value) {
            if (field != value) {
                field = value
                applyInteractiveStates(this.variant)
            }
        }

    init {
        if (tooltipText != null) {
            this.tooltipText = tooltipText
        }
        if (iconType != null) {
            applyIconType(iconType)
        }
        if (backdropFilter != null) {
            this.backdropFilter = backdropFilter
        }
        applyVariantStyle(variant, frosted = backdropFilter != null)
    }

    fun applyVariantStyle(
        targetVariant: OverlayButtonVariant = variant,
        frosted: Boolean = this.backdropFilter != null
    ) {
        size = when (targetVariant) {
            OverlayButtonVariant.CENTER_PRIMARY,
            OverlayButtonVariant.CENTER_SECONDARY -> BootstrapSize.LG
            OverlayButtonVariant.CIRCLE_PILL,
            OverlayButtonVariant.INNER_PILL -> BootstrapSize.SM
        }
        addClass("player-btn")
        if (targetVariant == OverlayButtonVariant.INNER_PILL) {
            removeClass("player-circle-btn")
            addClass("player-pill-inner-btn")
        } else {
            removeClass("player-pill-inner-btn")
            addClass("player-circle-btn")
        }
        if (targetVariant == OverlayButtonVariant.CENTER_PRIMARY) {
            setAttribute("data-overlay-role", "primary-center-action")
        } else if (getAttribute("data-overlay-role") == "primary-center-action") {
            removeAttribute("data-overlay-role")
        }

        val dim = targetVariant.sizePx
        val radius = targetVariant.cornerRadiusPx
        widthPx = dim
        heightPx = dim
        setPadding(EdgeInsets.ZERO)
        textColor = DEFAULT_FOREGROUND_COLOR
        getStyle().apply {
            setCursor(if (isEnabled) CssCursor.POINTER else CssCursor.NOT_ALLOWED)
            setWidth(CssLength.px(dim))
            setHeight(CssLength.px(dim))
            setPadding(CssEdgeLengths.px(0, 0))
            setBorderRadius(CssCornerRadii.px(radius))
        }

        if (targetVariant.isBorderless) {
            backgroundColor = RgbaColor.TRANSPARENT
            borderColor = RgbaColor.TRANSPARENT
            borderWidthPx = 0f
            borderStyle = CssBorderStyle.NONE
            borderRadiusPx = radius
            disabledBackgroundColor = RgbaColor.TRANSPARENT
            disabledBorderColor = RgbaColor.TRANSPARENT
            disabledTextColor = RgbaColor(148, 156, 172, 115)
            disabledOpacity = 0.35f
        } else {
            val fill = when {
                frosted -> DEFAULT_FROSTED_FILL_COLOR
                targetVariant == OverlayButtonVariant.CENTER_PRIMARY ||
                    targetVariant == OverlayButtonVariant.CENTER_SECONDARY -> DEFAULT_CENTER_FILL_COLOR
                else -> DEFAULT_CIRCLE_FILL_COLOR
            }
            val border = if (frosted) DEFAULT_FROSTED_BORDER_COLOR else DEFAULT_CIRCLE_BORDER_COLOR
            backgroundColor = fill
            setBorder(1, border, CssBorderStyle.SOLID, radiusPx = radius)
            disabledBackgroundColor = RgbaColor(18, 18, 22, 110)
            disabledBorderColor = RgbaColor(255, 255, 255, 18)
            disabledTextColor = RgbaColor(148, 156, 172, 115)
            disabledOpacity = 0.36f
        }

        applyInteractiveStates(targetVariant)
    }

    fun applyFrostedGlassStyle(
        filter: CssFilter = DEFAULT_FROSTED_BACKDROP_FILTER,
        fillColor: RgbaColor = DEFAULT_FROSTED_FILL_COLOR,
        borderColor: RgbaColor = DEFAULT_FROSTED_BORDER_COLOR
    ) {
        backdropFilter = filter
        backgroundColor = fillColor
        setBorder(1, borderColor, CssBorderStyle.SOLID, radiusPx = variant.cornerRadiusPx)
    }

    fun clearFrostedGlassStyle(
        fillColor: RgbaColor = defaultFillColorFor(variant),
        borderColor: RgbaColor = defaultBorderColorFor(variant)
    ) {
        backdropFilter = null
        if (variant.isBorderless) {
            backgroundColor = RgbaColor.TRANSPARENT
            this.borderColor = RgbaColor.TRANSPARENT
            borderWidthPx = 0f
            borderStyle = CssBorderStyle.NONE
            borderRadiusPx = variant.cornerRadiusPx
        } else {
            backgroundColor = fillColor
            setBorder(1, borderColor, CssBorderStyle.SOLID, radiusPx = variant.cornerRadiusPx)
        }
    }

    private fun applyIconType(type: OverlayIconType?) {
        if (type == null) {
            setImage(null, tintToTheme = tintImageToTheme)
        } else {
            setImage(OverlayIcons.resourceFor(type), tintToTheme = true)
        }
    }

    private fun applyInteractiveStates(targetVariant: OverlayButtonVariant) {
        val hoverBg = RgbaColor(accentColor.r, accentColor.g, accentColor.b, 68)
        val hoverBorder = if (targetVariant.isBorderless) {
            RgbaColor.TRANSPARENT
        } else {
            RgbaColor(accentColor.r, accentColor.g, accentColor.b, 210)
        }
        setHoverStyle(
            backgroundColor = hoverBg,
            borderColor = hoverBorder,
            textColor = RgbaColor.WHITE
        )

        val focusBg = RgbaColor(accentColor.r, accentColor.g, accentColor.b, 82)
        val focusBorder = if (targetVariant.isBorderless) {
            RgbaColor.TRANSPARENT
        } else {
            RgbaColor(accentColor.r, accentColor.g, accentColor.b, 245)
        }
        val focusRing = RgbaColor(accentColor.r, accentColor.g, accentColor.b, 115)
        setFocusStyle(
            backgroundColor = focusBg,
            borderColor = focusBorder,
            textColor = RgbaColor.WHITE,
            focusRingColor = focusRing,
            focusRingWidthPx = if (targetVariant.isBorderless) 0f else 2f
        )
    }

    companion object {
        val DEFAULT_ACCENT_COLOR: RgbaColor = RgbaColor(255, 126, 150, 255)
        val DEFAULT_FOREGROUND_COLOR: RgbaColor = RgbaColor(242, 245, 250, 255)
        val DEFAULT_CIRCLE_FILL_COLOR: RgbaColor = RgbaColor(18, 18, 22, 188)
        val DEFAULT_CIRCLE_BORDER_COLOR: RgbaColor = RgbaColor(255, 255, 255, 36)
        val DEFAULT_CENTER_FILL_COLOR: RgbaColor = RgbaColor(0, 0, 0, 136)
        val DEFAULT_FROSTED_BACKDROP_FILTER: CssFilter = CssFilter(
            blur = CssLength.px(16f),
            saturate = 1.2f
        )
        val DEFAULT_FROSTED_FILL_COLOR: RgbaColor = RgbaColor(20, 24, 32, 97)
        val DEFAULT_FROSTED_BORDER_COLOR: RgbaColor = RgbaColor(255, 255, 255, 56)

        fun defaultFillColorFor(variant: OverlayButtonVariant): RgbaColor = when (variant) {
            OverlayButtonVariant.INNER_PILL -> RgbaColor.TRANSPARENT
            OverlayButtonVariant.CENTER_PRIMARY,
            OverlayButtonVariant.CENTER_SECONDARY -> DEFAULT_CENTER_FILL_COLOR
            OverlayButtonVariant.CIRCLE_PILL -> DEFAULT_CIRCLE_FILL_COLOR
        }

        fun defaultBorderColorFor(variant: OverlayButtonVariant): RgbaColor = when (variant) {
            OverlayButtonVariant.INNER_PILL -> RgbaColor.TRANSPARENT
            OverlayButtonVariant.CENTER_PRIMARY,
            OverlayButtonVariant.CENTER_SECONDARY,
            OverlayButtonVariant.CIRCLE_PILL -> DEFAULT_CIRCLE_BORDER_COLOR
        }
    }
}
