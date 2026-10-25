package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.gradient.Gradient
import com.nexustools.genericui.style.transition.CssTransform2D

/**
 * Resolved visual style snapshot for an [AlienWidgetNode] on a given frame after combining:
 * 1. Inherited CSS custom properties (`--bs-*`) and inherited typography/color/shadow properties,
 * 2. Browser-identical Bootstrap 5.3 component defaults (`btn`, `form-control`, `card`, `alert`, `badge`, `nav-tabs`, etc.),
 * 3. `BootstrapStyleSnapshot` enums (`BootstrapType`, `BootstrapSize`, `BorderRadiusStyle`, `ShadowElevation`, etc.),
 * 4. Custom CSS/SASS `ComputedStyle` rules and interactive `:hover` / `:focus` / `:active` / `:disabled` states, and
 * 5. Active CSS transition/keyframe interpolations (`AlienTransitionAnimator`).
 */
data class AlienResolvedVisualStyle(
    val backgroundColor: RgbaColor = RgbaColor.TRANSPARENT,
    val backgroundGradient: Gradient? = null,
    var backgroundImageUrl: String? = null,
    var backgroundRepeat: String = "repeat",
    var backgroundPosition: String = "center",
    var backgroundSize: String = "auto",
    val textColor: RgbaColor = AlienBootstrapThemeTokens.BODY_COLOR_LIGHT,
    val borderColor: RgbaColor = RgbaColor.TRANSPARENT,
    val borderWidthPx: Float = 0f,
    val borderStyle: String = "solid",
    val borderRadiusPx: Float = 0f,
    val borderRadiusPercent: Float? = null,
    val fontSizePx: Float = AlienBootstrapThemeTokens.BASE_FONT_SIZE_PX,
    val fontFamily: String = AlienBootstrapThemeTokens.DEFAULT_FONT_FAMILY,
    val fontWeight: Int = 400,
    val italic: Boolean = false,
    val textAlign: String = "left",
    val textTransform: String = "none",
    val textOverflow: String = "clip",
    val whiteSpace: String = "normal",
    val opacity: Float = 1.0f,
    val boxShadowCss: String? = null,
    val textShadowCss: String? = null,
    val focusRingColor: RgbaColor? = null,
    val focusRingSpreadPx: Float = 0f,
    val padding: EdgeInsets = EdgeInsets.ZERO,
    val margin: EdgeInsets = EdgeInsets.ZERO,
    val marginLeftAuto: Boolean = false,
    val marginRightAuto: Boolean = false,
    val display: String = "block",
    val position: String = "static",
    val leftCss: String? = null,
    val topCss: String? = null,
    val rightCss: String? = null,
    val bottomCss: String? = null,
    val widthCss: String? = null,
    val heightCss: String? = null,
    val minWidthCss: String? = null,
    val minHeightCss: String? = null,
    val maxWidthCss: String? = null,
    val maxHeightCss: String? = null,
    val aspectRatio: Float? = null,
    val flexGrow: Float = 0f,
    val floatMode: String = "none",
    val transform2D: CssTransform2D = CssTransform2D.IDENTITY,
    val zIndex: Int = 0,
    val pointerEventsEnabled: Boolean = true,
    val darkTheme: Boolean = false
)
