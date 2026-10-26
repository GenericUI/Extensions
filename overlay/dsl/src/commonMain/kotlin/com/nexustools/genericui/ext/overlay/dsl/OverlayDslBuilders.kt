package com.nexustools.genericui.ext.overlay.dsl

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.ext.overlay.AutoHideController
import com.nexustools.genericui.ext.overlay.CenterTransportCluster
import com.nexustools.genericui.ext.overlay.GradientScrim
import com.nexustools.genericui.ext.overlay.LoadingIndicator
import com.nexustools.genericui.ext.overlay.MetadataHeader
import com.nexustools.genericui.ext.overlay.MobileOverlayPreset
import com.nexustools.genericui.ext.overlay.OverlayBadge
import com.nexustools.genericui.ext.overlay.OverlayBadgeSpec
import com.nexustools.genericui.ext.overlay.OverlayBadgeStrip
import com.nexustools.genericui.ext.overlay.OverlayBadgeVariant
import com.nexustools.genericui.ext.overlay.OverlayButtonBar
import com.nexustools.genericui.ext.overlay.OverlayCurtain
import com.nexustools.genericui.ext.overlay.OverlayExtension
import com.nexustools.genericui.ext.overlay.OverlayHost
import com.nexustools.genericui.ext.overlay.OverlayHostBox
import com.nexustools.genericui.ext.overlay.OverlayIconButton
import com.nexustools.genericui.ext.overlay.OverlayModalDialog
import com.nexustools.genericui.ext.overlay.OverlayPillButton
import com.nexustools.genericui.ext.overlay.OverlayPopoverPanel
import com.nexustools.genericui.ext.overlay.OverlayPresetResolver
import com.nexustools.genericui.ext.overlay.OverlaySlot
import com.nexustools.genericui.ext.overlay.OverlayStatusPill
import com.nexustools.genericui.ext.overlay.PresetOverlay
import com.nexustools.genericui.ext.overlay.SliderSeekBar
import com.nexustools.genericui.ext.overlay.SubtitleCueView
import com.nexustools.genericui.ext.overlay.TrackSeekBar
import com.nexustools.genericui.ext.overlay.VolumeControl
import com.nexustools.genericui.ext.overlay.WebSystemOverlayPreset

/**
 * Top-level `kotlinx.html`-style entry point that creates and configures an [OverlayHostBox].
 */
fun buildOverlayHostBox(
    content: AbstractComponent? = null,
    initialOverlay: AbstractOverlay? = null,
    themeColorHex: String? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker OverlayHostBox.() -> Unit)? = null
): OverlayHostBox {
    val box = OverlayHostBox(content = content, initialOverlay = initialOverlay)
    if (themeColorHex != null) {
        box.themeColorHex = themeColorHex
    }
    if (!classes.isNullOrBlank()) {
        box.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        box.id = id
    }
    if (block != null) {
        box.block()
    }
    return box
}

/**
 * In-place [OverlayHostBox] builder inside any [AbstractContainer].
 */
fun AbstractContainer.overlayHostBox(
    content: AbstractComponent? = null,
    initialOverlay: AbstractOverlay? = null,
    themeColorHex: String? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker OverlayHostBox.() -> Unit)? = null
): OverlayHostBox {
    val box = buildOverlayHostBox(
        content = content,
        initialOverlay = initialOverlay,
        themeColorHex = themeColorHex,
        id = id,
        classes = classes,
        block = block
    )
    addChild(box)
    return box
}

/**
 * Creates, configures, and attaches a [MobileOverlayPreset] to this [OverlayHost].
 */
fun OverlayHost.mobileOverlayPreset(
    overlayId: String = "mobile-overlay",
    skipSeconds: Double = CenterTransportCluster.DEFAULT_SKIP_SECONDS,
    themeColorHex: String? = null,
    block: (@GenericUiDslMarker MobileOverlayPreset.() -> Unit)? = null
): MobileOverlayPreset {
    val preset = MobileOverlayPreset(overlayId = overlayId, skipSeconds = skipSeconds)
    if (themeColorHex != null) {
        preset.themeColorHex = themeColorHex
    }
    attachOverlay(preset)
    if (block != null) {
        preset.block()
    }
    return preset
}

/**
 * Creates and configures a standalone [MobileOverlayPreset].
 */
fun mobileOverlayPreset(
    overlayId: String = "mobile-overlay",
    skipSeconds: Double = CenterTransportCluster.DEFAULT_SKIP_SECONDS,
    themeColorHex: String? = null,
    block: (@GenericUiDslMarker MobileOverlayPreset.() -> Unit)? = null
): MobileOverlayPreset {
    val preset = MobileOverlayPreset(overlayId = overlayId, skipSeconds = skipSeconds)
    if (themeColorHex != null) {
        preset.themeColorHex = themeColorHex
    }
    if (block != null) {
        preset.block()
    }
    return preset
}

/**
 * Creates, configures, and attaches a [WebSystemOverlayPreset] to this [OverlayHost].
 */
fun OverlayHost.webSystemOverlayPreset(
    overlayId: String = "web-system-overlay",
    themeColorHex: String? = null,
    block: (@GenericUiDslMarker WebSystemOverlayPreset.() -> Unit)? = null
): WebSystemOverlayPreset {
    val preset = WebSystemOverlayPreset(overlayId = overlayId)
    if (themeColorHex != null) {
        preset.themeColorHex = themeColorHex
    }
    attachOverlay(preset)
    if (block != null) {
        preset.block()
    }
    return preset
}

/**
 * Creates and configures a standalone [WebSystemOverlayPreset].
 */
fun webSystemOverlayPreset(
    overlayId: String = "web-system-overlay",
    themeColorHex: String? = null,
    block: (@GenericUiDslMarker WebSystemOverlayPreset.() -> Unit)? = null
): WebSystemOverlayPreset {
    val preset = WebSystemOverlayPreset(overlayId = overlayId)
    if (themeColorHex != null) {
        preset.themeColorHex = themeColorHex
    }
    if (block != null) {
        preset.block()
    }
    return preset
}

/**
 * Resolves, configures, and attaches the default [PresetOverlay] for [backend] onto this [OverlayHost].
 */
fun OverlayHost.defaultOverlayPreset(
    backend: BackendTarget = GenericUI.activeFactory?.backendTarget ?: BackendTarget.Windows,
    themeColorHex: String? = null,
    block: (@GenericUiDslMarker PresetOverlay.() -> Unit)? = null
): PresetOverlay {
    val preset = OverlayPresetResolver.resolveForBackend(backend)
    if (themeColorHex != null) {
        preset.themeColorHex = themeColorHex
    }
    attachOverlay(preset)
    if (block != null) {
        preset.block()
    }
    return preset
}

/**
 * In-place [OverlayBadge] builder inside any [AbstractContainer].
 */
fun AbstractContainer.overlayBadge(
    id: String,
    text: String = id,
    variant: OverlayBadgeVariant = OverlayBadgeVariant.NEUTRAL,
    outlinedStyle: Boolean = (variant == OverlayBadgeVariant.OUTLINED),
    tooltip: String? = null,
    visible: Boolean = true,
    clickable: Boolean = false,
    themeColorHex: String = OverlayExtension.effectiveDefaultThemeColorHex(),
    onClick: (() -> Unit)? = null,
    block: (@GenericUiDslMarker OverlayBadge.() -> Unit)? = null
): OverlayBadge {
    val spec = OverlayBadgeSpec(
        id = id,
        text = text,
        variant = variant,
        tooltip = tooltip,
        visible = visible,
        clickable = clickable || onClick != null,
        onClick = onClick
    )
    val badge = OverlayBadge(
        spec = spec,
        outlinedStyle = outlinedStyle,
        themeColorHex = themeColorHex
    )
    if (block != null) {
        badge.block()
    }
    addChild(badge)
    return badge
}

/**
 * In-place [OverlayBadgeStrip] builder inside any [AbstractContainer].
 */
fun AbstractContainer.overlayBadgeStrip(
    elementId: String = "badge-strip",
    slot: OverlaySlot = OverlaySlot.TOP_BAR,
    outlinedStyle: Boolean = (slot == OverlaySlot.TOP_BAR),
    themeColorHex: String = OverlayExtension.effectiveDefaultThemeColorHex(),
    badges: List<OverlayBadgeSpec> = emptyList(),
    block: (@GenericUiDslMarker OverlayBadgeStrip.() -> Unit)? = null
): OverlayBadgeStrip {
    val strip = OverlayBadgeStrip(
        elementId = elementId,
        slot = slot,
        outlinedStyle = outlinedStyle,
        themeColorHex = themeColorHex
    )
    if (badges.isNotEmpty()) {
        strip.setBadges(badges)
    }
    if (block != null) {
        strip.block()
    }
    addChild(strip)
    return strip
}

/**
 * In-place [OverlayIconButton] builder inside any [AbstractContainer].
 */
fun AbstractContainer.overlayIconButton(
    elementId: String,
    symbol: String,
    tooltip: String? = null,
    slot: OverlaySlot = OverlaySlot.BOTTOM_BAR,
    circleSizeDp: Int? = null,
    autoHideController: AutoHideController? = null,
    onClick: (() -> Unit)? = null,
    block: (@GenericUiDslMarker OverlayIconButton.() -> Unit)? = null
): OverlayIconButton {
    val button = OverlayIconButton(
        elementId = elementId,
        symbol = symbol,
        tooltip = tooltip,
        slot = slot,
        circleSizeDp = circleSizeDp,
        autoHideController = autoHideController,
        onAction = onClick
    )
    if (block != null) {
        button.block()
    }
    addChild(button)
    return button
}

/**
 * In-place [OverlayPillButton] builder inside any [AbstractContainer].
 */
fun AbstractContainer.overlayPillButton(
    elementId: String,
    text: String,
    variant: OverlayBadgeVariant = OverlayBadgeVariant.NEUTRAL,
    slot: OverlaySlot = OverlaySlot.BOTTOM_BAR,
    themeColorHex: String = OverlayExtension.effectiveDefaultThemeColorHex(),
    autoHideController: AutoHideController? = null,
    onClick: (() -> Unit)? = null,
    block: (@GenericUiDslMarker OverlayPillButton.() -> Unit)? = null
): OverlayPillButton {
    val button = OverlayPillButton(
        elementId = elementId,
        text = text,
        variant = variant,
        slot = slot,
        themeColorHex = themeColorHex,
        autoHideController = autoHideController,
        onAction = onClick
    )
    if (block != null) {
        button.block()
    }
    addChild(button)
    return button
}

/**
 * In-place [OverlayButtonBar] builder inside any [AbstractContainer].
 */
fun AbstractContainer.overlayButtonBar(
    elementId: String = "button-bar",
    slot: OverlaySlot = OverlaySlot.BOTTOM_BAR,
    block: (@GenericUiDslMarker OverlayButtonBar.() -> Unit)? = null
): OverlayButtonBar {
    val bar = OverlayButtonBar(elementId = elementId, slot = slot)
    if (block != null) {
        bar.block()
    }
    addChild(bar)
    return bar
}

/**
 * In-place [MetadataHeader] builder inside any [AbstractContainer].
 */
fun AbstractContainer.metadataHeader(
    elementId: String = "metadata-header",
    slot: OverlaySlot = OverlaySlot.TOP_BAR,
    showInlineStatusText: Boolean = false,
    outlinedTopBadges: Boolean = true,
    themeColorHex: String = OverlayExtension.effectiveDefaultThemeColorHex(),
    block: (@GenericUiDslMarker MetadataHeader.() -> Unit)? = null
): MetadataHeader {
    val header = MetadataHeader(
        elementId = elementId,
        slot = slot,
        showInlineStatusText = showInlineStatusText,
        outlinedTopBadges = outlinedTopBadges,
        themeColorHex = themeColorHex
    )
    if (block != null) {
        header.block()
    }
    addChild(header)
    return header
}

/**
 * In-place [CenterTransportCluster] builder inside any [AbstractContainer].
 */
fun AbstractContainer.centerTransportCluster(
    elementId: String = "center-transport-cluster",
    slot: OverlaySlot = OverlaySlot.CENTER,
    skipSeconds: Double = CenterTransportCluster.DEFAULT_SKIP_SECONDS,
    showSkipBackward: Boolean = true,
    showSkipForward: Boolean = true,
    autoHideController: AutoHideController? = null,
    block: (@GenericUiDslMarker CenterTransportCluster.() -> Unit)? = null
): CenterTransportCluster {
    val cluster = CenterTransportCluster(
        elementId = elementId,
        slot = slot,
        skipSeconds = skipSeconds,
        showSkipBackward = showSkipBackward,
        showSkipForward = showSkipForward,
        autoHideController = autoHideController
    )
    if (block != null) {
        cluster.block()
    }
    addChild(cluster)
    return cluster
}

/**
 * In-place [TrackSeekBar] builder inside any [AbstractContainer].
 */
fun AbstractContainer.trackSeekBar(
    elementId: String = "track-seek-bar",
    slot: OverlaySlot = OverlaySlot.BOTTOM_SEEK,
    themeColorHex: String? = null,
    autoHideController: AutoHideController? = null,
    block: (@GenericUiDslMarker TrackSeekBar.() -> Unit)? = null
): TrackSeekBar {
    val bar = TrackSeekBar(
        elementId = elementId,
        slot = slot,
        themeColorHex = themeColorHex,
        autoHideController = autoHideController
    )
    if (block != null) {
        bar.block()
    }
    addChild(bar)
    return bar
}

/**
 * In-place [SliderSeekBar] builder inside any [AbstractContainer].
 */
fun AbstractContainer.sliderSeekBar(
    elementId: String = "slider-seek-bar",
    slot: OverlaySlot = OverlaySlot.BOTTOM_SEEK,
    themeColorHex: String? = null,
    autoHideController: AutoHideController? = null,
    block: (@GenericUiDslMarker SliderSeekBar.() -> Unit)? = null
): SliderSeekBar {
    val bar = SliderSeekBar(
        elementId = elementId,
        slot = slot,
        themeColorHex = themeColorHex,
        autoHideController = autoHideController
    )
    if (block != null) {
        bar.block()
    }
    addChild(bar)
    return bar
}

/**
 * In-place [VolumeControl] builder inside any [AbstractContainer].
 */
fun AbstractContainer.volumeControl(
    elementId: String = "volume-control",
    slot: OverlaySlot = OverlaySlot.BOTTOM_BAR,
    revealSliderOnHover: Boolean = true,
    autoHideController: AutoHideController? = null,
    block: (@GenericUiDslMarker VolumeControl.() -> Unit)? = null
): VolumeControl {
    val control = VolumeControl(
        elementId = elementId,
        slot = slot,
        revealSliderOnHover = revealSliderOnHover,
        autoHideController = autoHideController
    )
    if (block != null) {
        control.block()
    }
    addChild(control)
    return control
}

/**
 * In-place [OverlayStatusPill] builder inside any [AbstractContainer].
 */
fun AbstractContainer.overlayStatusPill(
    elementId: String = "status-pill",
    slot: OverlaySlot = OverlaySlot.BOTTOM_STATUS,
    block: (@GenericUiDslMarker OverlayStatusPill.() -> Unit)? = null
): OverlayStatusPill {
    val pill = OverlayStatusPill(elementId = elementId, slot = slot)
    if (block != null) {
        pill.block()
    }
    addChild(pill)
    return pill
}

/**
 * In-place [SubtitleCueView] builder inside any [AbstractContainer].
 */
fun AbstractContainer.subtitleCueView(
    elementId: String = "subtitle-cue-view",
    slot: OverlaySlot = OverlaySlot.SUBTITLE_CUE,
    block: (@GenericUiDslMarker SubtitleCueView.() -> Unit)? = null
): SubtitleCueView {
    val view = SubtitleCueView(elementId = elementId, slot = slot)
    if (block != null) {
        view.block()
    }
    addChild(view)
    return view
}

/**
 * In-place [OverlayPopoverPanel] builder inside any [AbstractContainer].
 */
fun AbstractContainer.overlayPopoverPanel(
    elementId: String = "overlay-popover-panel",
    slot: OverlaySlot = OverlaySlot.POPOVER,
    autoHideController: AutoHideController? = null,
    block: (@GenericUiDslMarker OverlayPopoverPanel.() -> Unit)? = null
): OverlayPopoverPanel {
    val panel = OverlayPopoverPanel(
        elementId = elementId,
        slot = slot,
        autoHideController = autoHideController
    )
    if (block != null) {
        panel.block()
    }
    addChild(panel)
    return panel
}

/**
 * In-place [OverlayModalDialog] builder inside any [AbstractContainer].
 */
fun AbstractContainer.overlayModalDialog(
    elementId: String = "overlay-modal-dialog",
    slot: OverlaySlot = OverlaySlot.POPOVER,
    titleText: String = "Playback Options",
    autoHideController: AutoHideController? = null,
    block: (@GenericUiDslMarker OverlayModalDialog.() -> Unit)? = null
): OverlayModalDialog {
    val dialog = OverlayModalDialog(
        elementId = elementId,
        slot = slot,
        titleText = titleText,
        autoHideController = autoHideController
    )
    if (block != null) {
        dialog.block()
    }
    addChild(dialog)
    return dialog
}

/**
 * In-place [OverlayCurtain] builder inside any [AbstractContainer].
 */
fun AbstractContainer.overlayCurtain(
    elementId: String = "overlay-curtain",
    slot: OverlaySlot = OverlaySlot.CURTAIN,
    autoHideController: AutoHideController? = null,
    initialVisible: Boolean = false,
    block: (@GenericUiDslMarker OverlayCurtain.() -> Unit)? = null
): OverlayCurtain {
    val curtain = OverlayCurtain(
        elementId = elementId,
        slot = slot,
        autoHideController = autoHideController,
        initialVisible = initialVisible
    )
    if (block != null) {
        curtain.block()
    }
    addChild(curtain)
    return curtain
}

/**
 * In-place [GradientScrim] builder inside any [AbstractContainer].
 */
fun AbstractContainer.gradientScrim(
    elementId: String = "gradient-scrim",
    slot: OverlaySlot = OverlaySlot.TOP_BAR,
    angleDegrees: Float = 180f,
    colorStopsCss: List<String> = listOf("rgba(0, 0, 0, 0.65)", "transparent"),
    block: (@GenericUiDslMarker GradientScrim.() -> Unit)? = null
): GradientScrim {
    val scrim = GradientScrim(
        elementId = elementId,
        slot = slot,
        angleDegrees = angleDegrees,
        colorStopsCss = colorStopsCss
    )
    if (block != null) {
        scrim.block()
    }
    addChild(scrim)
    return scrim
}

/**
 * In-place [LoadingIndicator] builder inside any [AbstractContainer].
 */
fun AbstractContainer.loadingIndicator(
    elementId: String = "loading-indicator",
    slot: OverlaySlot = OverlaySlot.CENTER,
    sizeDp: Int = 64,
    labelText: String? = "Loading...",
    block: (@GenericUiDslMarker LoadingIndicator.() -> Unit)? = null
): LoadingIndicator {
    val indicator = LoadingIndicator(
        elementId = elementId,
        slot = slot,
        sizeDp = sizeDp,
        labelText = labelText
    )
    if (block != null) {
        indicator.block()
    }
    addChild(indicator)
    return indicator
}
