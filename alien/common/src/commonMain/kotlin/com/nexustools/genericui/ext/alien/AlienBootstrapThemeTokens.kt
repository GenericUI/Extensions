package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Exact browser Bootstrap 5.3 color palette, typography scale, border radii, shadows, and `--bs-*`
 * CSS custom property defaults so Alien components look identical to how Bootstrap 5.3 renders in a browser.
 */
object AlienBootstrapThemeTokens {

    // Semantic Bootstrap 5.3 Colors
    val PRIMARY = RgbaColor(13, 110, 253, 255)        // #0d6efd
    val PRIMARY_HOVER = RgbaColor(11, 94, 215, 255)   // #0b5ed7
    val PRIMARY_ACTIVE = RgbaColor(10, 88, 202, 255)  // #0a58ca

    val SECONDARY = RgbaColor(108, 117, 125, 255)     // #6c757d
    val SECONDARY_HOVER = RgbaColor(92, 99, 106, 255) // #5c636a
    val SECONDARY_ACTIVE = RgbaColor(86, 94, 100, 255)// #565e64

    val SUCCESS = RgbaColor(25, 135, 84, 255)         // #198754
    val SUCCESS_HOVER = RgbaColor(21, 115, 71, 255)   // #157347
    val SUCCESS_ACTIVE = RgbaColor(20, 108, 67, 255)  // #146c43

    val DANGER = RgbaColor(220, 53, 69, 255)          // #dc3545
    val DANGER_HOVER = RgbaColor(187, 45, 59, 255)    // #bb2d3b
    val DANGER_ACTIVE = RgbaColor(176, 42, 55, 255)   // #b02a37

    val WARNING = RgbaColor(255, 193, 7, 255)         // #ffc107
    val WARNING_HOVER = RgbaColor(255, 202, 44, 255)  // #ffca2c
    val WARNING_ACTIVE = RgbaColor(255, 205, 57, 255) // #ffcd39

    val INFO = RgbaColor(13, 202, 240, 255)           // #0dcaf0
    val INFO_HOVER = RgbaColor(49, 210, 242, 255)     // #31d2f2
    val INFO_ACTIVE = RgbaColor(61, 213, 243, 255)    // #3dd5f3

    val LIGHT = RgbaColor(248, 249, 250, 255)         // #f8f9fa
    val LIGHT_HOVER = RgbaColor(211, 212, 213, 255)   // #d3d4d5
    val LIGHT_ACTIVE = RgbaColor(198, 199, 200, 255)  // #c6c7c8

    val DARK = RgbaColor(33, 37, 41, 255)             // #212529
    val DARK_HOVER = RgbaColor(66, 70, 73, 255)       // #424649
    val DARK_ACTIVE = RgbaColor(77, 81, 84, 255)      // #4d5154

    // Light & Dark Theme Body / Surface Tokens
    val BODY_BG_LIGHT = RgbaColor(255, 255, 255, 255)        // #ffffff
    val BODY_COLOR_LIGHT = RgbaColor(33, 37, 41, 255)        // #212529
    val SECONDARY_BG_LIGHT = RgbaColor(233, 236, 239, 255)   // #e9ecef
    val TERTIARY_BG_LIGHT = RgbaColor(248, 249, 250, 255)    // #f8f9fa
    val BORDER_COLOR_LIGHT = RgbaColor(222, 226, 230, 255)   // #dee2e6

    val BODY_BG_DARK = RgbaColor(33, 37, 41, 255)            // #212529
    val BODY_COLOR_DARK = RgbaColor(222, 226, 230, 255)      // #dee2e6
    val SECONDARY_BG_DARK = RgbaColor(52, 58, 64, 255)       // #343a40
    val TERTIARY_BG_DARK = RgbaColor(43, 48, 53, 255)        // #2b3035
    val BORDER_COLOR_DARK = RgbaColor(73, 80, 87, 255)       // #495057

    // Focus Ring
    val FOCUS_RING_PRIMARY = RgbaColor(13, 110, 253, 64)     // rgba(13, 110, 253, 0.25)

    // Typography & Radii Defaults
    const val DEFAULT_FONT_FAMILY = "system-ui, -apple-system, Segoe UI, Roboto, Helvetica Neue, Arial, sans-serif"
    const val BASE_FONT_SIZE_PX = 16f
    const val SMALL_FONT_SIZE_PX = 14f
    const val LARGE_FONT_SIZE_PX = 20f
    const val DEFAULT_BORDER_RADIUS_PX = 6f
    const val SMALL_BORDER_RADIUS_PX = 4f
    const val LARGE_BORDER_RADIUS_PX = 8f

    val DEFAULT_LIGHT_CSS_VARIABLES: Map<String, String> = mapOf(
        "--bs-primary" to "#0d6efd",
        "--bs-secondary" to "#6c757d",
        "--bs-success" to "#198754",
        "--bs-info" to "#0dcaf0",
        "--bs-warning" to "#ffc107",
        "--bs-danger" to "#dc3545",
        "--bs-light" to "#f8f9fa",
        "--bs-dark" to "#212529",
        "--bs-body-bg" to "#ffffff",
        "--bs-body-color" to "#212529",
        "--bs-secondary-bg" to "#e9ecef",
        "--bs-tertiary-bg" to "#f8f9fa",
        "--bs-border-color" to "#dee2e6",
        "--bs-border-radius" to "6px",
        "--bs-border-radius-sm" to "4px",
        "--bs-border-radius-lg" to "8px"
    )

    val DEFAULT_DARK_CSS_VARIABLES: Map<String, String> = mapOf(
        "--bs-primary" to "#0d6efd",
        "--bs-secondary" to "#6c757d",
        "--bs-success" to "#198754",
        "--bs-info" to "#0dcaf0",
        "--bs-warning" to "#ffc107",
        "--bs-danger" to "#dc3545",
        "--bs-light" to "#f8f9fa",
        "--bs-dark" to "#212529",
        "--bs-body-bg" to "#212529",
        "--bs-body-color" to "#dee2e6",
        "--bs-secondary-bg" to "#343a40",
        "--bs-tertiary-bg" to "#2b3035",
        "--bs-border-color" to "#495057",
        "--bs-border-radius" to "6px",
        "--bs-border-radius-sm" to "4px",
        "--bs-border-radius-lg" to "8px"
    )

    fun baseColorForType(type: BootstrapType): RgbaColor = when (type) {
        BootstrapType.NONE -> RgbaColor.TRANSPARENT
        BootstrapType.PRIMARY -> PRIMARY
        BootstrapType.SECONDARY -> SECONDARY
        BootstrapType.SUCCESS -> SUCCESS
        BootstrapType.DANGER -> DANGER
        BootstrapType.WARNING -> WARNING
        BootstrapType.INFO -> INFO
        BootstrapType.LIGHT -> LIGHT
        BootstrapType.DARK -> DARK
        BootstrapType.LINK -> RgbaColor.TRANSPARENT
    }

    fun hoverColorForType(type: BootstrapType): RgbaColor = when (type) {
        BootstrapType.NONE -> RgbaColor.TRANSPARENT
        BootstrapType.PRIMARY -> PRIMARY_HOVER
        BootstrapType.SECONDARY -> SECONDARY_HOVER
        BootstrapType.SUCCESS -> SUCCESS_HOVER
        BootstrapType.DANGER -> DANGER_HOVER
        BootstrapType.WARNING -> WARNING_HOVER
        BootstrapType.INFO -> INFO_HOVER
        BootstrapType.LIGHT -> LIGHT_HOVER
        BootstrapType.DARK -> DARK_HOVER
        BootstrapType.LINK -> RgbaColor.TRANSPARENT
    }

    fun activeColorForType(type: BootstrapType): RgbaColor = when (type) {
        BootstrapType.NONE -> RgbaColor.TRANSPARENT
        BootstrapType.PRIMARY -> PRIMARY_ACTIVE
        BootstrapType.SECONDARY -> SECONDARY_ACTIVE
        BootstrapType.SUCCESS -> SUCCESS_ACTIVE
        BootstrapType.DANGER -> DANGER_ACTIVE
        BootstrapType.WARNING -> WARNING_ACTIVE
        BootstrapType.INFO -> INFO_ACTIVE
        BootstrapType.LIGHT -> LIGHT_ACTIVE
        BootstrapType.DARK -> DARK_ACTIVE
        BootstrapType.LINK -> RgbaColor.TRANSPARENT
    }

    fun foregroundColorForType(type: BootstrapType): RgbaColor = when (type) {
        BootstrapType.NONE -> BODY_COLOR_LIGHT
        BootstrapType.WARNING, BootstrapType.INFO, BootstrapType.LIGHT -> BODY_COLOR_LIGHT
        BootstrapType.LINK -> PRIMARY
        else -> RgbaColor.WHITE
    }

    fun focusRingColorForType(type: BootstrapType?): RgbaColor {
        val base = type?.takeIf { it.isSemantic }?.let { baseColorForType(it) } ?: PRIMARY
        return if (base.a == 0) FOCUS_RING_PRIMARY else base.withAlpha(0.25f)
    }

    fun buttonPaddingForSize(size: BootstrapSize?): EdgeInsets = when (size) {
        BootstrapSize.EXTRA_SMALL, BootstrapSize.SMALL -> EdgeInsets(top = 4, right = 8, bottom = 4, left = 8)
        BootstrapSize.LARGE, BootstrapSize.EXTRA_LARGE, BootstrapSize.EXTRA_EXTRA_LARGE ->
            EdgeInsets(top = 8, right = 16, bottom = 8, left = 16)
        else -> EdgeInsets(top = 6, right = 12, bottom = 6, left = 12)
    }

    fun fontSizeForSize(size: BootstrapSize?): Float = when (size) {
        BootstrapSize.EXTRA_SMALL -> 12f
        BootstrapSize.SMALL -> SMALL_FONT_SIZE_PX
        BootstrapSize.LARGE -> LARGE_FONT_SIZE_PX
        BootstrapSize.EXTRA_LARGE -> 24f
        BootstrapSize.EXTRA_EXTRA_LARGE -> 28f
        else -> BASE_FONT_SIZE_PX
    }
}
