package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.feedback.Spinner
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.layout.Spacer
import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.surface.TextBubble
import com.nexustools.genericui.component.surface.TextBubblePointerSide
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapHeight
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.BootstrapWidth
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.enums.css.CssDisplay
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssShadow
import com.nexustools.genericui.style.css.CssShadowList
import com.nexustools.genericui.style.transition.CssTimingFunction
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.style.transition.CssTransitionSpec
import kotlin.math.roundToInt

/**
 * YouTube-style 3-zone player/stage overlay built atop [AbstractOverlay].
 *
 * Structure:
 * - [hudControlsContainer] (`zIndex = 10`, `data-overlay-role = "controls-layer"`): driven by
 *   [autoHideController] while keeping the outer [YoutubePlayerOverlay] hit-testable so pointer
 *   movement or taps wake the HUD cleanly. Contains [topBar], [centerZone], and [bottomBar].
 * - [subtitleBanner] (`zIndex = 15`, `LayerAlignment.BOTTOM_CENTER`, `passThroughPointerEvents = true`,
 *   `data-overlay-role = "caption-banner"`): mounted outside [hudControlsContainer] so captions remain
 *   visible even when HUD controls auto-hide.
 * - Registered popovers (`zIndex = 30`): managed via [registerPopoverLayer], [setPopoverOpen], and
 *   [closeAllPopovers], automatically pinning/unpinning [autoHideController].
 */
open class YoutubePlayerOverlay(
    val config: YoutubeOverlayConfig = YoutubeOverlayConfig(),
    tagName: String = "genericui:youtube-overlay",
    classPrefix: String = "gui-overlay player-overlay"
) : AbstractOverlay(
    tagName = tagName,
    classPrefix = classPrefix,
    autoHideSpec = config.autoHideSpec
) {

    var onStopClick: (() -> Unit)? = null
    var onFullscreenToggle: ((Boolean) -> Unit)? = null
    var onCursorVisibilityChanged: ((Boolean) -> Unit)? = null

    var isCursorVisible: Boolean = true
        private set

    val isHudVisible: Boolean
        get() = autoHideController.isOverlayVisible

    var isLoading: Boolean = false
        private set

    var timelinePlacement: YoutubeTimelinePlacement = config.timelinePlacement
        private set

    internal var differenceControllerResizeHook: ((widthPx: Int, heightPx: Int) -> Unit)? = null

    private val registeredPopovers = linkedMapOf<String, AbstractComponent>()
    private val openPopoverIds = linkedSetOf<String>()
    private var cursorVisibilityInitialized: Boolean = false
    private var suppressCursorHideOnce: Boolean = false
    private var lastSyncedState: OverlayState = OverlayState()

    val isAnyPopoverOpen: Boolean
        get() = openPopoverIds.isNotEmpty() || registeredPopovers.values.any { it.isVisible }

    // -------------------------------------------------------------------------
    // Persistent Caption Layer (zIndex = 15, independent of HUD auto-hide)
    // -------------------------------------------------------------------------

    val subtitleBanner: TextBubble = TextBubble(
        text = "",
        pointerSide = TextBubblePointerSide.NONE
    ).apply {
        id = "subtitle-banner"
        addClass("subtitle-banner text-center fw-semibold")
        setAttribute("data-overlay-role", "caption-banner")
        backgroundColor = RgbaColor(0, 0, 0, 184)
        textColor = RgbaColor.WHITE
        setBorder(0, RgbaColor.TRANSPARENT, CssBorderStyle.NONE, radiusPx = 8)
        getStyle().apply {
            setBackgroundColor(RgbaColor(0, 0, 0, 184))
            setColor(RgbaColor.WHITE)
            setPadding(CssEdgeLengths.px(4, 12))
            setBorderRadius(CssCornerRadii.px(8))
            setPointerEvents(CssPointerEvents.NONE)
            setMaxWidth(CssLength.calcPercentPx(100f, -32f))
        }
        isVisible = false
    }

    // -------------------------------------------------------------------------
    // Auto-Hiding 3-Zone HUD Layer (zIndex = 10)
    // -------------------------------------------------------------------------

    val hudControlsContainer: StyledContainer = StyledContainer(
        classPrefix = "player-overlay-controls-layer"
    ).apply {
        id = "player-overlay-controls-layer"
        addClass("player-overlay-controls-layer hud-visible")
        setAttribute("data-overlay-role", "controls-layer")
        setDisplay(DisplayMode.FLEX)
        flexDirection = FlexDirection.COLUMN
        justifyContent = JustifyContent.BETWEEN
        backgroundColor = RgbaColor.TRANSPARENT
        borderWidthPx = 0f
        borderRadiusPx = 0f
        opacity = 1.0f
        getBootstrap().apply {
            padding = SpacingScale.S0
            width = BootstrapWidth.W_100
            height = BootstrapHeight.H_100
            display = DisplayMode.FLEX
            flexDirection = FlexDirection.COLUMN
            justifyContent = JustifyContent.BETWEEN
        }
        getStyle().apply {
            setDisplay(CssDisplay.FLEX)
            setFlexDirection(FlexDirection.COLUMN)
            setJustifyContent(JustifyContent.BETWEEN)
            setWidth(CssLength.percent(100))
            setHeight(CssLength.percent(100))
            setPadding(CssEdgeLengths.px(0, 0))
            setBorderRadius(CssCornerRadii.px(0))
            setBackgroundColor(RgbaColor.TRANSPARENT)
            setOpacity(1.0f)
            setPointerEvents(CssPointerEvents.AUTO)
            setTransitions(
                listOf(
                    CssTransitionSpec(
                        property = "opacity",
                        durationMs = config.autoHideSpec.fadeOutMs.toFloat().coerceAtLeast(0f),
                        timingFunction = CssTimingFunction.EASE_IN_OUT
                    )
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // 1. Top Bar
    // -------------------------------------------------------------------------

    val titleLabel: Text = Text(DEFAULT_TITLE).apply {
        id = "now-playing-show"
        addClass("fw-semibold text-truncate")
        textColor = RgbaColor.WHITE
        getStyle().apply {
            setColor(RgbaColor.WHITE)
            setTextShadow(CssShadowList.of(CssShadow.px(0f, 1f, blurRadius = 4f, color = RgbaColor(0, 0, 0, 0.85f))))
        }
    }

    val subtitleLabel: Text = Text("").apply {
        id = "now-playing-episode"
        addClass("small text-white-50 text-truncate")
        textColor = RgbaColor(255, 255, 255, 204)
        getStyle().apply {
            setColor(RgbaColor(255, 255, 255, 204))
            setTextShadow(CssShadowList.of(CssShadow.px(0f, 1f, blurRadius = 4f, color = RgbaColor(0, 0, 0, 0.85f))))
        }
    }

    val statusLabel: Text = Text("").apply {
        id = "now-playing-status"
        addClass("small text-info")
        isVisible = false
    }

    val titleStack: VStack = VStack(gapPx = 2).apply {
        addClass("now-playing-info")
        getStyle().apply {
            setFlex(0f, 1f, CssLength.AUTO)
            setMinWidth(CssLength.px(0))
        }
        addChild(titleLabel)
        addChild(subtitleLabel)
        addChild(statusLabel)
    }

    val topBadgesContainer: HStack = HStack(gapPx = 6).apply {
        addClass("player-overlay-top-badges")
        alignItems = AlignItems.CENTER
        getStyle().setFlexShrink(0f)
    }

    val topRightActionsRow: HStack = HStack(gapPx = 8).apply {
        addClass("player-overlay-top-actions")
        alignItems = AlignItems.CENTER
        getStyle().setFlexShrink(0f)
    }

    val topBar: StyledContainer = StyledContainer(
        classPrefix = "player-overlay-top"
    ).apply {
        addClass("player-overlay-top")
        setAttribute("data-overlay-role", "top-bar")
        setDisplay(DisplayMode.FLEX)
        flexDirection = FlexDirection.ROW
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.BETWEEN
        backgroundColor = RgbaColor.TRANSPARENT
        setBackground(config.topScrimGradient)
        borderWidthPx = 0f
        borderRadiusPx = 0f
        isVisible = config.showTopBar
        getStyle().apply {
            setBackgroundColor(RgbaColor.TRANSPARENT)
            setDisplay(CssDisplay.FLEX)
            setFlex(0f, 0f, CssLength.AUTO)
            setPadding(CssEdgeLengths.px(6, 8))
        }
        addChild(titleStack)
        addChild(topBadgesContainer)
        addChild(Spacer(widthPx = 8, weight = 1f))
        addChild(topRightActionsRow)
    }

    // -------------------------------------------------------------------------
    // 2. Center Zone
    // -------------------------------------------------------------------------

    val loadingSpinner: Spinner = Spinner(type = BootstrapType.LIGHT).apply {
        id = "player-spinner"
        addClass("player-spinner")
        isVisible = false
    }

    val centerPreviousButton: OverlayControlButton = OverlayControlButtons.previous(
        variant = OverlayButtonVariant.CENTER_SECONDARY,
        accentColor = config.accentColor,
        backdropFilter = config.centerButtonBackdropFilter
    ).apply {
        id = "btn-center-previous"
        isVisible = config.showCenterPreviousNext
    }

    val centerRewindButton: OverlayControlButton = OverlayControlButtons.rewind(
        variant = OverlayButtonVariant.CENTER_SECONDARY,
        accentColor = config.accentColor,
        backdropFilter = config.centerButtonBackdropFilter
    ).apply {
        id = "btn-center-rewind"
        isVisible = config.showCenterRewindFastForward
    }

    val centerPlayPauseButton: OverlayPlayPauseButton = OverlayControlButtons.playPause(
        isPlaying = false,
        variant = OverlayButtonVariant.CENTER_PRIMARY,
        accentColor = config.accentColor,
        backdropFilter = config.centerButtonBackdropFilter
    ).apply {
        id = "btn-center-play-pause"
        setAttribute("data-overlay-role", "primary-center-action")
        isVisible = config.showCenterPlayPause
    }

    val centerStopButton: OverlayControlButton = OverlayControlButtons.stop(
        variant = OverlayButtonVariant.CENTER_SECONDARY,
        accentColor = config.accentColor,
        backdropFilter = config.centerButtonBackdropFilter
    ).apply {
        id = "btn-center-stop"
        isVisible = config.showCenterStop
    }

    val centerFastForwardButton: OverlayControlButton = OverlayControlButtons.fastForward(
        variant = OverlayButtonVariant.CENTER_SECONDARY,
        accentColor = config.accentColor,
        backdropFilter = config.centerButtonBackdropFilter
    ).apply {
        id = "btn-center-fast-forward"
        isVisible = config.showCenterRewindFastForward
    }

    val centerNextButton: OverlayControlButton = OverlayControlButtons.next(
        variant = OverlayButtonVariant.CENTER_SECONDARY,
        accentColor = config.accentColor,
        backdropFilter = config.centerButtonBackdropFilter
    ).apply {
        id = "btn-center-next"
        isVisible = config.showCenterPreviousNext
    }

    val centerTransportRow: HStack = HStack(gapPx = 20).apply {
        id = "center-transport-row"
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.CENTER
        isVisible = config.showCenterTransport
        addChild(centerPreviousButton)
        addChild(centerRewindButton)
        addChild(centerPlayPauseButton)
        addChild(centerStopButton)
        addChild(centerFastForwardButton)
        addChild(centerNextButton)
    }

    val centerZone: HStack = HStack(gapPx = 16).apply {
        id = "player-overlay-center"
        addClass("player-overlay-center flex-grow-1 w-100")
        setAttribute("data-overlay-role", "center-zone")
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.CENTER
        getStyle().setFlex(1f, 1f, CssLength.AUTO)
        addChild(centerTransportRow)
        addChild(loadingSpinner)
    }

    // -------------------------------------------------------------------------
    // 3. Bottom Bar
    // -------------------------------------------------------------------------

    val seekHoverBubble: TextBubble = TextBubble(
        text = "0:00",
        pointerSide = TextBubblePointerSide.BOTTOM,
        pointerOffset = 0.5f
    ).apply {
        id = "seek-hover-bubble"
        addClass("seek-hover-bubble")
        setAttribute("data-overlay-role", "hover-bubble")
        getStyle().apply {
            setPosition(CssPosition.ABSOLUTE)
            setBottom(CssLength.px(68))
            setTransform(CssTransform2D(translateX = -50f, translateXIsPercent = true))
        }
        isVisible = false
    }

    val timelineContainer: StyledContainer = StyledContainer(
        classPrefix = "player-timeline-container"
    ).apply {
        id = "player-timeline-container"
        addClass("player-timeline-container")
        setAttribute("data-overlay-role", "seek-container")
        setDisplay(DisplayMode.FLEX)
        flexDirection = FlexDirection.COLUMN
        backgroundColor = RgbaColor.TRANSPARENT
        borderWidthPx = 0f
        borderRadiusPx = 0f
        getBootstrap().width = BootstrapWidth.W_100
        getStyle().apply {
            setDisplay(CssDisplay.FLEX)
            setFlexDirection(FlexDirection.COLUMN)
            setWidth(CssLength.percent(100))
            setPadding(CssEdgeLengths.px(0, 0))
            setBackgroundColor(RgbaColor.TRANSPARENT)
            setFlex(0f, 0f, CssLength.AUTO)
        }
    }

    private val timelineSpacer: Spacer = Spacer.vertical(6)

    val previousButton: OverlayControlButton = OverlayControlButtons.previous(
        variant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor = config.accentColor
    ).apply {
        id = "btn-previous"
        isVisible = config.showPrevious
    }

    val playPauseButton: OverlayPlayPauseButton = OverlayControlButtons.playPause(
        isPlaying = false,
        variant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor = config.accentColor
    ).apply {
        id = "btn-play-pause"
        isVisible = config.showPlayPause
    }

    val stopButton: OverlayControlButton = OverlayControlButtons.stop(
        variant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor = config.accentColor
    ).apply {
        id = "btn-stop"
        isVisible = config.showStop
    }

    val rewindButton: OverlayControlButton = OverlayControlButtons.rewind(
        variant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor = config.accentColor
    ).apply {
        id = "btn-rewind"
        isVisible = config.showRewind
    }

    val fastForwardButton: OverlayControlButton = OverlayControlButtons.fastForward(
        variant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor = config.accentColor
    ).apply {
        id = "btn-fast-forward"
        isVisible = config.showFastForward
    }

    val nextButton: OverlayControlButton = OverlayControlButtons.next(
        variant = OverlayButtonVariant.CIRCLE_PILL,
        accentColor = config.accentColor
    ).apply {
        id = "btn-next"
        isVisible = config.showNext
    }

    val leftAccessoryContainer: HStack = HStack(gapPx = 8).apply {
        addClass("player-left-accessories")
        alignItems = AlignItems.CENTER
        getStyle().setFlexShrink(0f)
    }

    val timeAccessoryContainer: HStack = HStack(gapPx = 6).apply {
        addClass("player-time-accessories")
        alignItems = AlignItems.CENTER
        getStyle().setFlexShrink(0f)
    }

    val timePillContainer: StyledContainer = OverlayControlButtons.createTimePill(
        initialText = "0:00 / --:--",
        id = "time-pill"
    ).apply {
        isVisible = config.showTimePill
        addChild(timeAccessoryContainer)
    }

    val timeReadoutLabel: Text = timePillContainer.children.firstOrNull { it is Text } as? Text
        ?: Text("0:00 / --:--").also { timePillContainer.addChild(it, 0) }

    val leftControlsRow: HStack = HStack(gapPx = 8).apply {
        addClass("player-controls-left")
        setAttribute("data-overlay-role", "controls-left")
        alignItems = AlignItems.CENTER
        getStyle().setFlexShrink(0f)
        addChild(previousButton)
        addChild(playPauseButton)
        addChild(stopButton)
        addChild(rewindButton)
        addChild(fastForwardButton)
        addChild(nextButton)
        addChild(leftAccessoryContainer)
        addChild(timePillContainer)
    }

    val rightBadgesContainer: HStack = HStack(gapPx = 6).apply {
        addClass("player-right-badges")
        alignItems = AlignItems.CENTER
        getStyle().setFlexShrink(0f)
    }

    val rightPillAccessoryRow: HStack = HStack(gapPx = 4).apply {
        addClass("player-right-pill-accessories")
        alignItems = AlignItems.CENTER
        getStyle().setFlexShrink(0f)
    }

    val fullscreenButton: OverlayFullscreenButton = OverlayControlButtons.fullscreenToggle(
        isFullscreen = false,
        variant = OverlayButtonVariant.INNER_PILL,
        accentColor = config.accentColor
    ).apply {
        id = "btn-fullscreen"
        isVisible = config.showFullscreen
    }

    val rightControlsPill: StyledContainer = OverlayControlButtons.createControlsPill(
        role = "controls-right",
        id = "right-controls-pill"
    ).apply {
        addChild(rightPillAccessoryRow)
        addChild(fullscreenButton)
    }

    val rightControlsRow: HStack = HStack(gapPx = 8).apply {
        addClass("player-controls-right")
        setAttribute("data-overlay-role", "controls-right")
        alignItems = AlignItems.CENTER
        getStyle().setFlexShrink(0f)
        addChild(rightBadgesContainer)
        addChild(rightControlsPill)
    }

    val controlsRow: HStack = HStack(gapPx = 8).apply {
        addClass("player-controls-row")
        setAttribute("data-overlay-role", "controls-row")
        alignItems = AlignItems.CENTER
        getStyle().setFlex(0f, 0f, CssLength.AUTO)
        addChild(leftControlsRow)
        addChild(Spacer(widthPx = 8, weight = 1f).apply { addClass("ms-auto") })
        addChild(rightControlsRow)
    }

    val bottomBar: StyledContainer = StyledContainer(
        classPrefix = "player-overlay-bottom"
    ).apply {
        addClass("player-overlay-bottom")
        setAttribute("data-overlay-role", "bottom-bar")
        setDisplay(DisplayMode.FLEX)
        flexDirection = FlexDirection.COLUMN
        backgroundColor = RgbaColor.TRANSPARENT
        setBackground(config.bottomScrimGradient)
        borderWidthPx = 0f
        borderRadiusPx = 0f
        getStyle().apply {
            setBackgroundColor(RgbaColor.TRANSPARENT)
            setDisplay(CssDisplay.FLEX)
            setFlex(0f, 0f, CssLength.AUTO)
            setPadding(CssEdgeLengths.px(6, 8))
        }
    }

    init {
        setAttribute("data-bs-theme", "dark")
        setAttribute("hudVisible", "true")
        setAttribute("popupOpen", "false")
        setAttribute("data-playing", "false")
        setAttribute("data-fullscreen", "false")

        if (config.centerButtonBackdropFilter == null) {
            setCenterTransportBackdropFilter(
                filter = null,
                fillColor = config.centerButtonFillColor,
                borderColor = config.centerButtonBorderColor
            )
        }

        wireDefaultTransportListeners()
        setTimelinePlacement(config.timelinePlacement)

        hudControlsContainer.addChild(topBar)
        hudControlsContainer.addChild(centerZone)
        hudControlsContainer.addChild(bottomBar)

        autoHideController.targetComponent = hudControlsContainer
        autoHideController.onVisibilityChanged = { visible, _ ->
            syncHudVisibilityVisuals(visible)
        }

        inputRouter.registerShortcut(
            OverlayShortcutBinding(
                id = "close_popovers_escape",
                key = "Escape",
                allowRepeat = false
            ) { _, _ ->
                if (isAnyPopoverOpen) {
                    closeAllPopovers()
                }
            }
        )

        addElement(
            OverlayElement(
                component = hudControlsContainer,
                constraint = LayerConstraint.fill(zIndex = 10, passThroughPointerEvents = false),
                elementId = "controls-layer"
            )
        )
        addElement(
            OverlayElement(
                component = subtitleBanner,
                constraint = LayerConstraint.float(
                    alignment = LayerAlignment.BOTTOM_CENTER,
                    leftPercent = 50f,
                    bottomPx = 18,
                    zIndex = 15,
                    passThroughPointerEvents = true
                ),
                elementId = "subtitle-banner"
            )
        )
        lastSyncedState = state
    }

    private fun wireDefaultTransportListeners() {
        val handlePlayPause: () -> Unit = {
            autoHideController.notifyUserActivity()
            actions.triggerPlayPause(playPauseButton.isPlaying)
        }
        playPauseButton.onPlayPauseToggle = handlePlayPause
        centerPlayPauseButton.onPlayPauseToggle = handlePlayPause

        val handlePrevious: () -> Unit = {
            autoHideController.notifyUserActivity()
            actions.onPrevious?.invoke()
        }
        previousButton.onClick = handlePrevious
        centerPreviousButton.onClick = handlePrevious

        val handleNext: () -> Unit = {
            autoHideController.notifyUserActivity()
            actions.onNext?.invoke()
        }
        nextButton.onClick = handleNext
        centerNextButton.onClick = handleNext

        val handleStop: () -> Unit = {
            autoHideController.notifyUserActivity()
            val stopHandler = onStopClick
            if (stopHandler != null) {
                stopHandler.invoke()
            } else {
                actions.onCustomAction?.invoke("stop", null)
            }
        }
        stopButton.onClick = handleStop
        centerStopButton.onClick = handleStop

        val handleRewind: () -> Unit = {
            autoHideController.notifyUserActivity()
            val delta = config.skipDeltaSeconds.coerceAtLeast(0.0)
            if (actions.onSkipDeltaSec != null) {
                actions.onSkipDeltaSec?.invoke(-delta)
            } else {
                actions.onSeekToSec?.invoke((state.positionSec - delta).coerceAtLeast(0.0))
            }
        }
        rewindButton.onClick = handleRewind
        centerRewindButton.onClick = handleRewind

        val handleFastForward: () -> Unit = {
            autoHideController.notifyUserActivity()
            val delta = config.skipDeltaSeconds.coerceAtLeast(0.0)
            if (actions.onSkipDeltaSec != null) {
                actions.onSkipDeltaSec?.invoke(delta)
            } else {
                val target = state.positionSec + delta
                val clamped = if (state.durationSec > 0.0) target.coerceAtMost(state.durationSec) else target
                actions.onSeekToSec?.invoke(clamped)
            }
        }
        fastForwardButton.onClick = handleFastForward
        centerFastForwardButton.onClick = handleFastForward

        fullscreenButton.onFullscreenToggle = { nextFs ->
            autoHideController.notifyUserActivity()
            applyFullscreenVisuals(nextFs)
            lastSyncedState = lastSyncedState.copy(isFullscreen = nextFs)
            actions.onToggleFullscreen?.invoke()
            onFullscreenToggle?.invoke(nextFs)
        }
    }

    fun showHud(scheduleHide: Boolean = true) {
        autoHideController.showOverlay(scheduleHide = scheduleHide)
    }

    fun hideHud(
        instant: Boolean = false,
        hideCursor: Boolean = autoHideController.spec.hideCursorWhenOverlayHidden
    ) {
        suppressCursorHideOnce = !hideCursor
        try {
            autoHideController.hideOverlay(instant = instant)
        } finally {
            suppressCursorHideOnce = false
        }
    }

    fun notifyUserActivity() {
        autoHideController.notifyUserActivity()
    }

    fun setTimelinePlacement(placement: YoutubeTimelinePlacement) {
        timelinePlacement = placement
        setAttribute("data-timeline-placement", placement.name)
        bottomBar.clearChildren()
        bottomBar.addChild(seekHoverBubble)
        when (placement) {
            YoutubeTimelinePlacement.ABOVE_CONTROLS -> {
                seekHoverBubble.getStyle().setBottom(CssLength.px(68))
                bottomBar.addChild(timelineContainer)
                bottomBar.addChild(timelineSpacer)
                bottomBar.addChild(controlsRow)
            }
            YoutubeTimelinePlacement.BELOW_CONTROLS -> {
                seekHoverBubble.getStyle().setBottom(CssLength.px(34))
                bottomBar.addChild(controlsRow)
                bottomBar.addChild(timelineSpacer)
                bottomBar.addChild(timelineContainer)
            }
        }
    }

    fun showTimelineHover(fraction: Double, label: String) {
        val clampedFraction = fraction.coerceIn(0.0, 1.0)
        val pctTenths = (clampedFraction * 1000.0).roundToInt().coerceIn(20, 980)
        val pct = pctTenths / 10f
        if (seekHoverBubble.text != label) {
            seekHoverBubble.text = label
        }
        seekHoverBubble.getStyle().apply {
            setPosition(CssPosition.ABSOLUTE)
            setLeft(CssLength.percent(pct))
            setBottom(CssLength.px(if (timelinePlacement == YoutubeTimelinePlacement.BELOW_CONTROLS) 34 else 68))
            setTransform(CssTransform2D(translateX = -50f, translateXIsPercent = true))
        }
        seekHoverBubble.setAttribute("layerLeftPercent", "${pctTenths / 10}.${pctTenths % 10}")
        if (!seekHoverBubble.isVisible) {
            seekHoverBubble.isVisible = true
        }
    }

    fun hideTimelineHover() {
        if (seekHoverBubble.isVisible) {
            seekHoverBubble.isVisible = false
        }
    }

    fun setMediaDisplay(title: String, subtitle: String = "", status: String = "") {
        titleLabel.text = title
        subtitleLabel.text = subtitle
        statusLabel.text = status
        statusLabel.isVisible = status.isNotBlank()
        lastSyncedState = lastSyncedState.copy(title = title, subtitle = subtitle, statusText = status)
    }

    fun setTimeText(text: String) {
        if (timeReadoutLabel.text != text) {
            timeReadoutLabel.text = text
        }
    }

    fun setPlayingState(playing: Boolean) {
        applyPlayingStateVisuals(playing)
        lastSyncedState = lastSyncedState.copy(isPlaying = playing)
    }

    fun setFullscreenState(fullscreen: Boolean) {
        applyFullscreenVisuals(fullscreen)
        lastSyncedState = lastSyncedState.copy(isFullscreen = fullscreen)
    }

    fun setLoadingState(loading: Boolean, message: String? = null) {
        applyLoadingStateVisuals(loading, message)
        lastSyncedState = lastSyncedState.copy(
            isLoading = loading,
            statusText = message ?: lastSyncedState.statusText
        )
    }

    fun setSubtitleCueText(cueText: String?) {
        val cleaned = cueText?.trim().orEmpty()
        if (subtitleBanner.text != cleaned) {
            subtitleBanner.text = cleaned
        }
        val shouldShow = cleaned.isNotBlank()
        if (subtitleBanner.isVisible != shouldShow) {
            subtitleBanner.isVisible = shouldShow
        }
        setAttribute("subtitleText", cleaned)
        lastSyncedState = lastSyncedState.copy(subtitleCueText = cueText.orEmpty())
    }

    fun addTopBadge(badge: AbstractComponent) {
        topBadgesContainer.addChild(badge)
    }

    fun addTopRightAction(component: AbstractComponent) {
        topRightActionsRow.addChild(component)
    }

    fun addRightPillButton(button: AbstractComponent) {
        rightPillAccessoryRow.addChild(button)
    }

    fun setCenterTransportBackdropFilter(
        filter: CssFilter?,
        fillColor: RgbaColor? = null,
        borderColor: RgbaColor? = null
    ) {
        val centerButtons = listOf(
            centerPreviousButton,
            centerRewindButton,
            centerPlayPauseButton,
            centerStopButton,
            centerFastForwardButton,
            centerNextButton
        )
        for (btn in centerButtons) {
            if (filter != null) {
                btn.applyFrostedGlassStyle(
                    filter = filter,
                    fillColor = fillColor ?: OverlayControlButton.DEFAULT_FROSTED_FILL_COLOR,
                    borderColor = borderColor ?: OverlayControlButton.DEFAULT_FROSTED_BORDER_COLOR
                )
            } else {
                btn.clearFrostedGlassStyle(
                    fillColor = fillColor ?: OverlayControlButton.DEFAULT_CENTER_FILL_COLOR,
                    borderColor = borderColor ?: OverlayControlButton.DEFAULT_CIRCLE_BORDER_COLOR
                )
            }
        }
    }

    fun registerPopoverLayer(
        id: String,
        component: AbstractComponent,
        constraint: LayerConstraint = LayerConstraint.float(
            alignment = LayerAlignment.BOTTOM_END,
            rightPx = 12,
            bottomPx = 58,
            zIndex = 30,
            passThroughPointerEvents = false
        ),
        initiallyOpen: Boolean = false
    ): AbstractComponent {
        val normalizedId = id.trim()
        require(normalizedId.isNotEmpty()) { "Popover id must not be blank" }
        val existing = registeredPopovers[normalizedId]
        if (existing != null && existing !== component) {
            removeElement(existing)
            openPopoverIds.remove(normalizedId)
        }
        registeredPopovers[normalizedId] = component
        component.isVisible = initiallyOpen
        val previousOutsideDismiss = component.onPopupOutsideClickDismiss
        component.hookMouseWhenPopupVisible = true
        component.onPopupOutsideClickDismiss = {
            setPopoverOpen(normalizedId, false)
            previousOutsideDismiss?.invoke()
        }
        if (initiallyOpen) {
            openPopoverIds.add(normalizedId)
        } else {
            openPopoverIds.remove(normalizedId)
        }
        addElement(
            OverlayElement(
                component = component,
                constraint = constraint,
                elementId = "popover-$normalizedId"
            ),
            constraint
        )
        syncPopoverPinState()
        return component
    }

    fun unregisterPopoverLayer(id: String): Boolean {
        val normalizedId = id.trim()
        val removed = registeredPopovers.remove(normalizedId) ?: return false
        openPopoverIds.remove(normalizedId)
        removeElement(removed)
        syncPopoverPinState()
        return true
    }

    fun isPopoverOpen(id: String): Boolean {
        val normalizedId = id.trim()
        return normalizedId in openPopoverIds || registeredPopovers[normalizedId]?.isVisible == true
    }

    fun setPopoverOpen(id: String, open: Boolean, closeOthers: Boolean = true) {
        val normalizedId = id.trim()
        if (normalizedId.isEmpty()) return
        if (open) {
            if (closeOthers) {
                for ((otherId, otherComp) in registeredPopovers) {
                    if (otherId != normalizedId) {
                        otherComp.isVisible = false
                        openPopoverIds.remove(otherId)
                    }
                }
                openPopoverIds.retainAll(setOf(normalizedId))
            }
            openPopoverIds.add(normalizedId)
            registeredPopovers[normalizedId]?.isVisible = true
        } else {
            openPopoverIds.remove(normalizedId)
            registeredPopovers[normalizedId]?.isVisible = false
        }
        syncPopoverPinState()
    }

    fun togglePopover(id: String, closeOthers: Boolean = true): Boolean {
        val next = !isPopoverOpen(id)
        setPopoverOpen(id, next, closeOthers = closeOthers)
        return next
    }

    fun closeAllPopovers() {
        openPopoverIds.clear()
        for (component in registeredPopovers.values) {
            component.isVisible = false
        }
        syncPopoverPinState()
    }

    override fun notifyTargetResize(widthPx: Int, heightPx: Int) {
        super.notifyTargetResize(widthPx, heightPx)
        differenceControllerResizeHook?.invoke(widthPx, heightPx)
    }

    override fun onStateUpdated(state: OverlayState, actions: OverlayActions) {
        val prev = lastSyncedState
        if (prev.title != state.title || prev.subtitle != state.subtitle || prev.statusText != state.statusText) {
            if (state.title.isNotBlank() || prev.title.isNotBlank()) {
                titleLabel.text = state.title.ifBlank { DEFAULT_TITLE }
            }
            subtitleLabel.text = state.subtitle
            statusLabel.text = state.statusText
            statusLabel.isVisible = state.statusText.isNotBlank()
        }
        if (prev.isPlaying != state.isPlaying) {
            applyPlayingStateVisuals(state.isPlaying)
        }
        if (prev.isFullscreen != state.isFullscreen) {
            applyFullscreenVisuals(state.isFullscreen)
        }
        if (prev.isLoading != state.isLoading || (state.isLoading && prev.statusText != state.statusText)) {
            applyLoadingStateVisuals(
                loading = state.isLoading,
                message = if (state.isLoading) state.statusText.takeIf { it.isNotBlank() } else null
            )
        }
        if (prev.positionSec != state.positionSec || prev.durationSec != state.durationSec) {
            setTimeText(state.formattedTimePair)
        }
        if (prev.subtitleCueText != state.subtitleCueText) {
            setSubtitleCueText(state.subtitleCueText)
        }
        lastSyncedState = state
    }

    private fun applyPlayingStateVisuals(playing: Boolean) {
        playPauseButton.isPlaying = playing
        centerPlayPauseButton.isPlaying = playing
        setAttribute("data-playing", playing.toString())
    }

    private fun applyFullscreenVisuals(fullscreen: Boolean) {
        fullscreenButton.isFullscreen = fullscreen
        setAttribute("data-fullscreen", fullscreen.toString())
    }

    private fun applyLoadingStateVisuals(loading: Boolean, message: String?) {
        isLoading = loading
        loadingSpinner.isVisible = loading
        centerPlayPauseButton.isVisible = !loading && config.showCenterPlayPause
        if (message != null) {
            statusLabel.text = message
            statusLabel.isVisible = message.isNotBlank()
        } else if (!loading && statusLabel.text.isBlank()) {
            statusLabel.isVisible = false
        }
    }

    private fun syncPopoverPinState() {
        val anyOpen = isAnyPopoverOpen
        setAttribute("popupOpen", anyOpen.toString())
        autoHideController.setPinned(POPOVER_PIN_REASON, anyOpen)
        if (anyOpen && !autoHideController.isInPictureInPictureMode) {
            autoHideController.showOverlay(scheduleHide = false)
        }
    }

    private fun syncHudVisibilityVisuals(visible: Boolean) {
        setAttribute("hudVisible", visible.toString())
        if (visible) {
            hudControlsContainer.removeClass("hud-hidden")
            hudControlsContainer.addClass("hud-visible")
            applyCursorVisibility(true)
        } else {
            hudControlsContainer.removeClass("hud-visible")
            hudControlsContainer.addClass("hud-hidden")
            hideTimelineHover()
            if (autoHideController.isInPictureInPictureMode) {
                closeAllPopovers()
            }
            val shouldHideCursor = !suppressCursorHideOnce &&
                !autoHideController.isInPictureInPictureMode &&
                autoHideController.spec.hideCursorWhenOverlayHidden
            applyCursorVisibility(!shouldHideCursor)
        }
        val shouldShowSub = subtitleBanner.text.isNotBlank()
        if (subtitleBanner.isVisible != shouldShowSub) {
            subtitleBanner.isVisible = shouldShowSub
        }
    }

    private fun applyCursorVisibility(visible: Boolean) {
        if (cursorVisibilityInitialized && isCursorVisible == visible) return
        cursorVisibilityInitialized = true
        isCursorVisible = visible
        val cssCursor = if (visible) CssCursor.DEFAULT else CssCursor.NONE
        val cursorStr = if (visible) "default" else "none"
        if (visible) {
            removeClass("hud-cursor-hidden")
            hudControlsContainer.removeClass("hud-cursor-hidden")
        } else {
            addClass("hud-cursor-hidden")
            hudControlsContainer.addClass("hud-cursor-hidden")
        }
        getStyle().setCursor(cssCursor)
        cursor = cursorStr
        onCursorVisibilityChanged?.invoke(visible)
    }

    override fun dispose() {
        differenceControllerResizeHook = null
        openPopoverIds.clear()
        registeredPopovers.clear()
        super.dispose()
    }

    companion object {
        const val DEFAULT_TITLE: String = ""
        const val POPOVER_PIN_REASON: String = "popover-open"
    }
}
