package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.gradient.GradientStop
import com.nexustools.genericui.style.gradient.LinearGradient

/**
 * Configurable top or bottom gradient scrim container used by overlay presets to improve text and
 * control legibility over dynamic video or canvas content.
 */
open class GradientScrim(
    override val elementId: String = "gradient-scrim",
    override val slot: OverlaySlot = OverlaySlot.TOP_BAR,
    angleDegrees: Float = 180f,
    colorStopsCss: List<String> = listOf("rgba(0, 0, 0, 0.65)", "transparent"),
    scrimPadding: EdgeInsets = EdgeInsets.ZERO
) : Container("genericui-overlay-gradient-scrim"), OverlayElement {

    private var disposedState: Boolean = false

    override val component: AbstractComponent
        get() = this

    override val isDisposed: Boolean
        get() = disposedState

    var angleDegrees: Float = angleDegrees
        set(value) {
            field = value
            rebuildGradient()
        }

    var colorStopsCss: List<String> = colorStopsCss
        set(value) {
            field = value
            rebuildGradient()
        }

    init {
        htmlTag = "genericui:overlay-gradient-scrim"
        setPadding(scrimPadding)
        rebuildGradient()
    }

    private fun rebuildGradient() {
        val stops = colorStopsCss.mapNotNull { raw ->
            val color = parseOverlayColor(raw) ?: return@mapNotNull null
            GradientStop(color)
        }
        if (stops.isNotEmpty()) {
            backgroundGradient = LinearGradient(
                angleDegrees = angleDegrees,
                stops = stops,
                repeating = false
            )
        }
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        super<Container>.dispose()
    }

    companion object {
        /**
         * Parses standard CSS colors as well as Android-style `#AARRGGBB` 8-digit hex strings
         * (such as `#EE000000`, `#99000000`, `#CC000000`, `#88000000`) when the last 6 digits are `000000`
         * or via explicit ARGB conversion.
         */
        internal fun parseOverlayColor(raw: String): RgbaColor? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.startsWith("#") && trimmed.length == 9) {
                val hex = trimmed.removePrefix("#")
                val c1 = hex.substring(0, 2).toIntOrNull(16)
                val c2 = hex.substring(2, 4).toIntOrNull(16)
                val c3 = hex.substring(4, 6).toIntOrNull(16)
                val c4 = hex.substring(6, 8).toIntOrNull(16)
                if (c1 != null && c2 != null && c3 != null && c4 != null) {
                    // Support Android #AARRGGBB notation when alpha is in the leading byte and RGB is 000000/ffffff
                    if (c2 == 0 && c3 == 0 && c4 == 0 && c1 > 0) {
                        return RgbaColor(r = 0, g = 0, b = 0, a = c1)
                    }
                    return RgbaColor(r = c1, g = c2, b = c3, a = c4)
                }
            }
            return RgbaColor.fromCss(trimmed)
        }

        /**
         * Mobile top scrim (`#EE000000 -> #99000000 -> transparent`) with `16/12/16/28dp` padding.
         */
        fun mobileTopScrim(elementId: String = "mobile-top-scrim"): GradientScrim =
            GradientScrim(
                elementId = elementId,
                slot = OverlaySlot.TOP_BAR,
                angleDegrees = 180f,
                colorStopsCss = listOf("#EE000000", "#99000000", "transparent"),
                scrimPadding = EdgeInsets(top = 12, right = 16, bottom = 28, left = 16)
            )

        /**
         * Mobile bottom scrim (`transparent -> #99000000 -> #EE000000`).
         */
        fun mobileBottomScrim(elementId: String = "mobile-bottom-scrim"): GradientScrim =
            GradientScrim(
                elementId = elementId,
                slot = OverlaySlot.BOTTOM_BAR,
                angleDegrees = 180f,
                colorStopsCss = listOf("transparent", "#99000000", "#EE000000"),
                scrimPadding = EdgeInsets(top = 20, right = 16, bottom = 12, left = 16)
            )

        /**
         * System + Web top scrim (`rgba(0, 0, 0, 0.65) -> transparent`).
         */
        fun webSystemTopScrim(elementId: String = "web-system-top-scrim"): GradientScrim =
            GradientScrim(
                elementId = elementId,
                slot = OverlaySlot.TOP_BAR,
                angleDegrees = 180f,
                colorStopsCss = listOf("rgba(0, 0, 0, 0.65)", "transparent"),
                scrimPadding = EdgeInsets(top = 12, right = 16, bottom = 24, left = 16)
            )

        /**
         * System + Web bottom scrim (`rgba(0, 0, 0, 0.8) -> transparent` from bottom to top).
         */
        fun webSystemBottomScrim(elementId: String = "web-system-bottom-scrim"): GradientScrim =
            GradientScrim(
                elementId = elementId,
                slot = OverlaySlot.BOTTOM_BAR,
                angleDegrees = 0f,
                colorStopsCss = listOf("rgba(0, 0, 0, 0.8)", "transparent"),
                scrimPadding = EdgeInsets(top = 24, right = 16, bottom = 12, left = 16)
            )
    }
}
