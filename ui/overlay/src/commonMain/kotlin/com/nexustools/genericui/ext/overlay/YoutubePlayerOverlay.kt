package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.feedback.Spinner
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.layout.Spacer
import com.nexustools.genericui.component.layout.styled.StyledContainer
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapHeight
import com.nexustools.genericui.enums.bootstrap.BootstrapWidth
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import kotlin.math.roundToInt

/**
 * YouTube-style 3-zone player/stage overlay built atop [FadingOverlay].
 *
 * Structure:
 * - Unified `.overlay-fading-root` (`this`): embeds [windowTitleBar] (`zIndex = 25`) and
 *   [hudControlsContainer] (`zIndex = 10`, `data-overlay-role = "controls-layer"` containing
 *   [topBar], [centerZone], and [bottomBar]), fading atomically together when `"hidden"` is
 *   toggled by [autoHideController] (zero $\alpha^2$ double fade and zero sticky titlebar).
 * - Persistent stage layers ([subtitleBanner] `zIndex = 15`, [loadingSpinner] `zIndex = 18`):
 *   mounted via [addPersistentElement] into `OverlayHostBox.persistentOverlayRoot` at `opacity = 1`
 *   so captions and buffering indicators remain visible even while the HUD fades out.
 * - Registered popovers (`zIndex = 30`): managed via [registerPopoverLayer], [setPopoverOpen], and
 *   [closeAllPopovers], automatically pinning/unpinning [autoHideController].
 */
open class YoutubePlayerOverlay(
    val config: YoutubeOverlayConfig = YoutubeOverlayConfig(),
    tagName: String = "genericui:youtube-overlay",
    classPrefix: String = "gui-overlay overlay-fading-root player-overlay"
) : FadingOverlay(
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
    private var dispatchingFullscreenToggle: Boolean = false
    private var handlingFullscreenButtonEvent: Boolean = false

    val isAnyPopoverOpen: Boolean
        get() = openPopoverIds.isNotEmpty() || registeredPopovers.values.any { it.isVisible }

    // -------------------------------------------------------------------------
    // Persistent Layers (zIndex = 15 / 18 in OverlayHostBox.persistentOverlayRoot)
    // -------------------------------------------------------------------------

    val subtitleBannerLabel: Text = Text("").apply {
        id = "subtitle-banner-label"
        addClass("subtitle-banner-label text-center fw-semibold")
    }

    val subtitleBanner: StyledContainer = StyledContainer(
        classPrefix = "subtitle-banner"
    ).apply {
        id = "subtitle-banner"
        addClass("subtitle-banner text-center fw-semibold")
        setAttribute("data-overlay-role", "caption-banner")
        addChild(subtitleBannerLabel)
        isVisible = false
    }

    // -------------------------------------------------------------------------
    // Auto-Hiding 3-Zone HUD Layer (zIndex = 10 inside unified fading root)
    // -------------------------------------------------------------------------

    val hudControlsContainer: StyledContainer = StyledContainer(
        classPrefix = "player-overlay-controls-layer"
    ).apply {
        id = "player-overlay-controls-layer"
        addClass("player-overlay-controls-layer")
        setAttribute("data-overlay-role", "controls-layer")
        setDisplay(DisplayMode.FLEX)
        flexDirection = FlexDirection.COLUMN
        justifyContent = JustifyContent.BETWEEN
        getBootstrap().apply {
            padding = SpacingScale.S0
            width = BootstrapWidth.W_100
            height = BootstrapHeight.H_100
            display = DisplayMode.FLEX
            flexDirection = FlexDirection.COLUMN
            justifyContent = JustifyContent.BETWEEN
        }
    }

    // -------------------------------------------------------------------------
    // 1. Top Bar
    // -------------------------------------------------------------------------

    val titleLabel: Text = Text(DEFAULT_TITLE).apply {
        id = "now-playing-show"
        addClass("player-title-label fw-semibold text-truncate")
    }

    val subtitleLabel: Text = Text("").apply {
        id = "now-playing-episode"
        addClass("player-subtitle-label small text-white-50 text-truncate")
    }

    val statusLabel: Text = Text("").apply {
        id = "now-playing-status"
        addClass("small text-info player-status-label")
        isVisible = false
    }

    val titleStack: VStack = VStack(gapPx = 2).apply {
        addClass("now-playing-info")
        addChild(titleLabel)
        addChild(subtitleLabel)
        addChild(statusLabel)
    }

    val topBadgesContainer: HStack = HStack(gapPx = 6).apply {
        addClass("player-overlay-top-badges")
        alignItems = AlignItems.CENTER
    }

    val topRightActionsRow: HStack = HStack(gapPx = 8).apply {
        addClass("player-overlay-top-actions")
        alignItems = AlignItems.CENTER
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
        isVisible = config.showTopBar
        addChild(titleStack)
        addChild(topBadgesContainer)
        addChild(Spacer(widthPx = 8, weight = 1f))
        addChild(topRightActionsRow)
    }

    // -------------------------------------------------------------------------
    // 2. Center Zone
    // -------------------------------------------------------------------------

    val loadingSpinner: Spinner = Spinner().apply {
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
        addChild(centerTransportRow)
    }

    // -------------------------------------------------------------------------
    // 3. Bottom Bar
    // -------------------------------------------------------------------------

    val seekHoverLabel: Text = Text("0:00").apply {
        id = "seek-hover-label"
        addClass("seek-hover-label small fw-semibold")
    }

    val seekHoverBubble: StyledContainer = StyledContainer(
        classPrefix = "seek-hover-bubble"
    ).apply {
        id = "seek-hover-bubble"
        addClass("seek-hover-bubble seek-hover-bubble-above")
        setAttribute("data-overlay-role", "hover-bubble")
        addChild(seekHoverLabel)
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
        getBootstrap().width = BootstrapWidth.W_100
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
    }

    val timeAccessoryContainer: HStack = HStack(gapPx = 6).apply {
        addClass("player-time-accessories")
        alignItems = AlignItems.CENTER
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
    }

    val rightPillAccessoryRow: HStack = HStack(gapPx = 4).apply {
        addClass("player-right-pill-accessories")
        alignItems = AlignItems.CENTER
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
        addChild(rightBadgesContainer)
        addChild(rightControlsPill)
    }

    val controlsRow: HStack = HStack(gapPx = 8).apply {
        addClass("player-controls-row")
        setAttribute("data-overlay-role", "controls-row")
        alignItems = AlignItems.CENTER
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

        updateActions(OverlayActions(onToggleFullscreen = { requestFullscreenToggle() }))
        wireDefaultTransportListeners()
        setTimelinePlacement(config.timelinePlacement)

        hudControlsContainer.addChild(topBar)
        hudControlsContainer.addChild(centerZone)
        hudControlsContainer.addChild(bottomBar)

        inputRouter.bindStageSurface(
            component = hudControlsContainer,
            stateProvider = { state },
            actionsProvider = { actions }
        )
        inputRouter.bindStageSurface(
            component = centerZone,
            stateProvider = { state },
            actionsProvider = { actions }
        )
        isolateInteractiveBuiltInControls()

        autoHideController.targetComponent = this
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
        addPersistentElement(
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
        addPersistentElement(
            OverlayElement(
                component = loadingSpinner,
                constraint = LayerConstraint.float(
                    alignment = LayerAlignment.CENTER,
                    zIndex = 18,
                    passThroughPointerEvents = true
                ),
                elementId = "loading-spinner"
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
            if (!handlingFullscreenButtonEvent) {
                handlingFullscreenButtonEvent = true
                val wasDispatching = dispatchingFullscreenToggle
                dispatchingFullscreenToggle = true
                try {
                    autoHideController.notifyUserActivity()
                    applyFullscreenVisuals(nextFs)
                    updateState(state.copy(isFullscreen = nextFs))
                    lastSyncedState = lastSyncedState.copy(isFullscreen = nextFs)
                    onFullscreenToggle?.invoke(nextFs)
                    if (!wasDispatching) {
                        actions.onToggleFullscreen?.invoke()
                    }
                } finally {
                    dispatchingFullscreenToggle = wasDispatching
                    handlingFullscreenButtonEvent = false
                }
            }
        }
    }

    private fun isolateInteractiveBuiltInControls() {
        listOf(
            previousButton,
            centerPreviousButton,
            playPauseButton,
            centerPlayPauseButton,
            stopButton,
            centerStopButton,
            rewindButton,
            centerRewindButton,
            fastForwardButton,
            centerFastForwardButton,
            nextButton,
            centerNextButton,
            fullscreenButton,
            timelineContainer,
            leftAccessoryContainer,
            topRightActionsRow,
            rightPillAccessoryRow
        ).forEach { inputRouter.isolateInteractiveComponent(it) }
    }

    override fun updateActions(newActions: OverlayActions) {
        val effective = if (newActions.onToggleFullscreen == null) {
            newActions.copy(onToggleFullscreen = { requestFullscreenToggle() })
        } else {
            newActions
        }
        super.updateActions(effective)
    }

    fun requestFullscreenToggle() {
        if (dispatchingFullscreenToggle) return
        val nextFs = !state.isFullscreen
        dispatchingFullscreenToggle = true
        try {
            autoHideController.notifyUserActivity()
            applyFullscreenVisuals(nextFs)
            updateState(state.copy(isFullscreen = nextFs))
            lastSyncedState = lastSyncedState.copy(isFullscreen = nextFs)
            onFullscreenToggle?.invoke(nextFs)
            if (!handlingFullscreenButtonEvent) {
                actions.onToggleFullscreen?.invoke()
            }
        } finally {
            dispatchingFullscreenToggle = false
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
            if (!hideCursor) {
                addClass(AutoHideController.KEEP_CURSOR_CLASS)
            }
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
                seekHoverBubble.removeClass("seek-hover-bubble-below")
                seekHoverBubble.addClass("seek-hover-bubble-above")
                bottomBar.addChild(timelineContainer)
                bottomBar.addChild(timelineSpacer)
                bottomBar.addChild(controlsRow)
            }
            YoutubeTimelinePlacement.BELOW_CONTROLS -> {
                seekHoverBubble.removeClass("seek-hover-bubble-above")
                seekHoverBubble.addClass("seek-hover-bubble-below")
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
        if (seekHoverLabel.text != label) {
            seekHoverLabel.text = label
        }
        seekHoverBubble.getStyle().setLeft(CssLength.percent(pct))
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
        if (subtitleBannerLabel.text != cleaned) {
            subtitleBannerLabel.text = cleaned
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
        inputRouter.isolateInteractiveComponent(component)
        topRightActionsRow.addChild(component)
    }

    fun addRightPillButton(button: AbstractComponent) {
        inputRouter.isolateInteractiveComponent(button)
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
        inputRouter.isolateInteractiveComponent(component)
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

    override fun syncTopBarWindowTitleBarOffset() {
        val offsetPx = windowTitleBar.effectiveHeightPx
        val targetMargin = if (offsetPx > 0) CssLength.px(offsetPx) else CssLength.ZERO
        if (topBar.getStyle().marginTop != targetMargin) {
            topBar.getStyle().setMarginTop(targetMargin)
        }
        topBar.setAttribute("data-titlebar-offset-px", offsetPx.toString())
        super.syncTopBarWindowTitleBarOffset()
    }

    private fun syncHudVisibilityVisuals(visible: Boolean) {
        setAttribute("hudVisible", visible.toString())
        if (visible) {
            applyCursorVisibility(true)
        } else {
            hideTimelineHover()
            if (autoHideController.isInPictureInPictureMode) {
                closeAllPopovers()
            }
            val shouldHideCursor = !suppressCursorHideOnce &&
                !autoHideController.isInPictureInPictureMode &&
                autoHideController.spec.hideCursorWhenOverlayHidden
            applyCursorVisibility(!shouldHideCursor)
        }
        val shouldShowSub = subtitleBannerLabel.text.isNotBlank()
        if (subtitleBanner.isVisible != shouldShowSub) {
            subtitleBanner.isVisible = shouldShowSub
        }
    }

    private fun applyCursorVisibility(visible: Boolean) {
        if (cursorVisibilityInitialized && isCursorVisible == visible) return
        cursorVisibilityInitialized = true
        isCursorVisible = visible
        if (visible) {
            removeClass("hud-cursor-hidden")
            removeClass(AutoHideController.KEEP_CURSOR_CLASS)
        } else {
            addClass("hud-cursor-hidden")
            removeClass(AutoHideController.KEEP_CURSOR_CLASS)
        }
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
