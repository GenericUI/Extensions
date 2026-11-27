package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.action.styled.StyledButton
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter

/**
 * Base cross-platform overlay transport/action [StyledButton] backed by `:extensions:ui:overlay`
 * `R.drawable.*` vector icons and stylesheet-driven styling (`overlay/style.scss` + `theme.scss`).
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
                applyVariantStyle(value, frosted = isFrosted)
            }
        }

    var accentColor: RgbaColor = accentColor
        set(value) {
            if (field != value) {
                field = value
                setAttribute("data-accent-color", value.toCssRgbaString())
            }
        }

    var isFrosted: Boolean = backdropFilter != null
        private set

    init {
        OverlayExtension.ensureStyleSheetRegistered()
        if (tooltipText != null) {
            this.tooltipText = tooltipText
        }
        if (iconType != null) {
            applyIconType(iconType)
        }
        setAttribute("data-accent-color", accentColor.toCssRgbaString())
        applyVariantStyle(variant, frosted = backdropFilter != null)
    }

    fun applyVariantStyle(
        targetVariant: OverlayButtonVariant = variant,
        frosted: Boolean = isFrosted
    ) {
        size = when (targetVariant) {
            OverlayButtonVariant.CENTER_PRIMARY,
            OverlayButtonVariant.CENTER_SECONDARY -> BootstrapSize.LG
            OverlayButtonVariant.CIRCLE_PILL,
            OverlayButtonVariant.INNER_PILL -> BootstrapSize.SM
        }
        addClass("player-btn")
        removeClass("player-circle-btn")
        removeClass("player-pill-inner-btn")
        removeClass("player-btn-center-secondary")
        removeClass("player-btn-center-primary")
        when (targetVariant) {
            OverlayButtonVariant.CIRCLE_PILL -> addClass("player-circle-btn")
            OverlayButtonVariant.INNER_PILL -> addClass("player-pill-inner-btn")
            OverlayButtonVariant.CENTER_SECONDARY -> addClass("player-btn-center-secondary")
            OverlayButtonVariant.CENTER_PRIMARY -> addClass("player-btn-center-primary")
        }
        if (targetVariant == OverlayButtonVariant.CENTER_PRIMARY) {
            setAttribute("data-overlay-role", "primary-center-action")
        } else if (getAttribute("data-overlay-role") == "primary-center-action") {
            removeAttribute("data-overlay-role")
        }
        setAttribute("data-overlay-variant", targetVariant.name)
        isFrosted = frosted
        if (frosted) {
            addClass("player-btn-frosted")
            setAttribute("data-frosted", "true")
        } else {
            removeClass("player-btn-frosted")
            removeAttribute("data-frosted")
        }
    }

    fun applyFrostedGlassStyle(
        filter: CssFilter = DEFAULT_FROSTED_BACKDROP_FILTER,
        fillColor: RgbaColor = DEFAULT_FROSTED_FILL_COLOR,
        borderColor: RgbaColor = DEFAULT_FROSTED_BORDER_COLOR
    ) {
        isFrosted = true
        addClass("player-btn-frosted")
        setAttribute("data-frosted", "true")
    }

    fun clearFrostedGlassStyle(
        fillColor: RgbaColor = defaultFillColorFor(variant),
        borderColor: RgbaColor = defaultBorderColorFor(variant)
    ) {
        isFrosted = false
        removeClass("player-btn-frosted")
        removeAttribute("data-frosted")
    }

    private fun applyIconType(type: OverlayIconType?) {
        if (type == null) {
            setImage(null, tintToTheme = tintImageToTheme)
        } else {
            setImage(OverlayIcons.resourceFor(type), tintToTheme = true)
        }
    }

    companion object {
        val DEFAULT_ACCENT_COLOR: RgbaColor = RgbaColor(255, 126, 150, 255)
        val DEFAULT_FOREGROUND_COLOR: RgbaColor = RgbaColor(242, 245, 250, 255)
        val DEFAULT_CIRCLE_FILL_COLOR: RgbaColor = RgbaColor(18, 18, 22, 188)
        val DEFAULT_CIRCLE_BORDER_COLOR: RgbaColor = RgbaColor(255, 255, 255, 36)
        val DEFAULT_CENTER_FILL_COLOR: RgbaColor = RgbaColor(0, 0, 0, 136)
        val DEFAULT_FROSTED_FILL_COLOR: RgbaColor = RgbaColor(20, 24, 32, 97)
        val DEFAULT_FROSTED_BORDER_COLOR: RgbaColor = RgbaColor(255, 255, 255, 56)
        val DEFAULT_FROSTED_BACKDROP_FILTER: CssFilter = CssFilter(
            blur = com.nexustools.genericui.style.css.CssLength.px(16f),
            saturate = 1.2f
        )

        fun defaultFillColorFor(variant: OverlayButtonVariant): RgbaColor = when {
            variant.isBorderless -> RgbaColor.TRANSPARENT
            variant == OverlayButtonVariant.CENTER_PRIMARY ||
                variant == OverlayButtonVariant.CENTER_SECONDARY -> DEFAULT_CENTER_FILL_COLOR
            else -> DEFAULT_CIRCLE_FILL_COLOR
        }

        fun defaultBorderColorFor(variant: OverlayButtonVariant): RgbaColor = when {
            variant.isBorderless -> RgbaColor.TRANSPARENT
            else -> DEFAULT_CIRCLE_BORDER_COLOR
        }
    }
}
