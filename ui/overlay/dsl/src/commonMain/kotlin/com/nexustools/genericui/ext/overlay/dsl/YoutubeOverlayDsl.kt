package com.nexustools.genericui.ext.overlay.dsl

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.overlay.HybridOverlayDifferenceController
import com.nexustools.genericui.ext.overlay.HybridOverlayDifferenceSpec
import com.nexustools.genericui.ext.overlay.HybridOverlayLayoutMode
import com.nexustools.genericui.ext.overlay.OverlayActions
import com.nexustools.genericui.ext.overlay.OverlayButtonVariant
import com.nexustools.genericui.ext.overlay.OverlayControlButton
import com.nexustools.genericui.ext.overlay.OverlayFullscreenButton
import com.nexustools.genericui.ext.overlay.OverlayHost
import com.nexustools.genericui.ext.overlay.OverlayIconType
import com.nexustools.genericui.ext.overlay.OverlayPlayPauseButton
import com.nexustools.genericui.ext.overlay.OverlayState
import com.nexustools.genericui.ext.overlay.OverlayWindowTitleBarConfig
import com.nexustools.genericui.ext.overlay.YoutubeOverlayConfig
import com.nexustools.genericui.ext.overlay.YoutubePlayerOverlay
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter

/**
 * Builds a standalone [YoutubePlayerOverlay] without mounting it to a parent container.
 */
fun buildYoutubePlayerOverlay(
    config: YoutubeOverlayConfig = YoutubeOverlayConfig(),
    state: OverlayState = OverlayState(),
    actions: OverlayActions = OverlayActions(),
    themeColorHex: String? = null,
    windowTitleBarEnabled: Boolean = false,
    windowTitleBarConfig: OverlayWindowTitleBarConfig? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker YoutubePlayerOverlay.() -> Unit)? = null
): YoutubePlayerOverlay {
    val instance = YoutubePlayerOverlay(config = config)
    instance.updateState(state)
    instance.updateActions(actions)
    if (themeColorHex != null) {
        instance.themeColorHex = themeColorHex
    }
    if (windowTitleBarConfig != null) {
        instance.windowTitleBarConfig = windowTitleBarConfig.copy(
            enabled = windowTitleBarConfig.enabled || windowTitleBarEnabled
        )
    } else if (windowTitleBarEnabled) {
        instance.windowTitleBarEnabled = true
    }
    if (!classes.isNullOrBlank()) {
        instance.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        instance.id = id
    }
    if (block != null) {
        instance.block()
    }
    return instance
}

/**
 * Mounts a [YoutubePlayerOverlay] inside any [AbstractContainer].
 */
fun AbstractContainer.youtubePlayerOverlay(
    config: YoutubeOverlayConfig = YoutubeOverlayConfig(),
    state: OverlayState = OverlayState(),
    actions: OverlayActions = OverlayActions(),
    themeColorHex: String? = null,
    windowTitleBarEnabled: Boolean = false,
    windowTitleBarConfig: OverlayWindowTitleBarConfig? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker YoutubePlayerOverlay.() -> Unit)? = null
): YoutubePlayerOverlay {
    val instance = buildYoutubePlayerOverlay(
        config = config,
        state = state,
        actions = actions,
        themeColorHex = themeColorHex,
        windowTitleBarEnabled = windowTitleBarEnabled,
        windowTitleBarConfig = windowTitleBarConfig,
        id = id,
        classes = classes,
        block = block
    )
    addChild(instance)
    return instance
}

/**
 * Creates, configures, and attaches a [YoutubePlayerOverlay] to this [OverlayHost].
 */
fun OverlayHost.attachYoutubePlayerOverlay(
    config: YoutubeOverlayConfig = YoutubeOverlayConfig(),
    themeColorHex: String? = null,
    windowTitleBarEnabled: Boolean = false,
    windowTitleBarConfig: OverlayWindowTitleBarConfig? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker YoutubePlayerOverlay.() -> Unit)? = null
): YoutubePlayerOverlay {
    val instance = YoutubePlayerOverlay(config = config)
    if (themeColorHex != null) {
        instance.themeColorHex = themeColorHex
    }
    if (!classes.isNullOrBlank()) {
        instance.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        instance.id = id
    }
    attachOverlay(instance)
    if (windowTitleBarConfig != null) {
        instance.windowTitleBarConfig = windowTitleBarConfig.copy(
            enabled = windowTitleBarConfig.enabled || windowTitleBarEnabled
        )
    } else if (windowTitleBarEnabled) {
        instance.windowTitleBarEnabled = true
    }
    if (block != null) {
        instance.block()
    }
    return instance
}

/**
 * Binds a responsive [HybridOverlayDifferenceController] to this [YoutubePlayerOverlay].
 */
fun YoutubePlayerOverlay.hybridOverlayDifference(
    spec: HybridOverlayDifferenceSpec = HybridOverlayDifferenceSpec(),
    initialMode: HybridOverlayLayoutMode? = null,
    forcedMode: HybridOverlayLayoutMode? = null,
    bindToOverlayResize: Boolean = true,
    onLayoutModeChanged: ((HybridOverlayLayoutMode) -> Unit)? = null,
    block: (@GenericUiDslMarker HybridOverlayDifferenceController.() -> Unit)? = null
): HybridOverlayDifferenceController {
    val controller = HybridOverlayDifferenceController(
        overlay = this,
        spec = spec,
        initialMode = initialMode,
        forcedMode = forcedMode,
        bindToOverlayResize = bindToOverlayResize,
        onLayoutModeChanged = onLayoutModeChanged
    )
    if (block != null) {
        controller.block()
    }
    return controller
}

/**
 * Creates and mounts an [OverlayControlButton] inside this [AbstractContainer] with zero default tooltip.
 */
fun AbstractContainer.overlayControlButton(
    iconType: OverlayIconType? = null,
    variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
    accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
    backdropFilter: CssFilter? = null,
    tooltipText: String? = null,
    id: String? = null,
    classes: String? = null,
    onClick: (() -> Unit)? = null,
    block: (@GenericUiDslMarker OverlayControlButton.() -> Unit)? = null
): OverlayControlButton {
    val btn = OverlayControlButton(
        iconType = iconType,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText
    )
    if (!classes.isNullOrBlank()) {
        btn.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        btn.id = id
    }
    if (onClick != null) {
        btn.onClick = onClick
    }
    if (block != null) {
        btn.block()
    }
    addChild(btn)
    return btn
}

/**
 * Creates and mounts a stateful [OverlayPlayPauseButton] inside this [AbstractContainer] with zero default tooltip.
 */
fun AbstractContainer.overlayPlayPauseButton(
    isPlaying: Boolean = false,
    variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
    accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
    backdropFilter: CssFilter? = null,
    tooltipText: String? = null,
    id: String? = null,
    classes: String? = null,
    onPlayPauseToggle: (() -> Unit)? = null,
    block: (@GenericUiDslMarker OverlayPlayPauseButton.() -> Unit)? = null
): OverlayPlayPauseButton {
    val btn = OverlayPlayPauseButton(
        isPlaying = isPlaying,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText,
        onPlayPauseToggle = onPlayPauseToggle
    )
    if (!classes.isNullOrBlank()) {
        btn.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        btn.id = id
    }
    if (block != null) {
        btn.block()
    }
    addChild(btn)
    return btn
}

/**
 * Creates and mounts a stateful [OverlayFullscreenButton] inside this [AbstractContainer] with zero default tooltip.
 */
fun AbstractContainer.overlayFullscreenButton(
    isFullscreen: Boolean = false,
    variant: OverlayButtonVariant = OverlayButtonVariant.CIRCLE_PILL,
    accentColor: RgbaColor = OverlayControlButton.DEFAULT_ACCENT_COLOR,
    backdropFilter: CssFilter? = null,
    tooltipText: String? = null,
    id: String? = null,
    classes: String? = null,
    onFullscreenToggle: ((Boolean) -> Unit)? = null,
    block: (@GenericUiDslMarker OverlayFullscreenButton.() -> Unit)? = null
): OverlayFullscreenButton {
    val btn = OverlayFullscreenButton(
        isFullscreen = isFullscreen,
        variant = variant,
        accentColor = accentColor,
        backdropFilter = backdropFilter,
        tooltipText = tooltipText,
        onFullscreenToggle = onFullscreenToggle
    )
    if (!classes.isNullOrBlank()) {
        btn.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        btn.id = id
    }
    if (block != null) {
        btn.block()
    }
    addChild(btn)
    return btn
}
