package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.enums.window.TitleBarControlsPlacement

/**
 * Configuration options for an overlay-embedded windowed [OverlayWindowTitleBar].
 */
data class OverlayWindowTitleBarConfig(
    val enabled: Boolean = false,
    val heightPx: Int = DEFAULT_HEIGHT_PX,
    val showTitle: Boolean = true,
    val showMinimizeButton: Boolean = true,
    val showMaximizeButton: Boolean = true,
    val showCloseButton: Boolean = true,
    val title: String? = null,
    val controlsPlacement: TitleBarControlsPlacement = TitleBarControlsPlacement.PLATFORM_DEFAULT,
    val themeMode: ThemeMode = ThemeMode.AUTO
) {
    companion object {
        const val DEFAULT_HEIGHT_PX: Int = 32
    }
}
