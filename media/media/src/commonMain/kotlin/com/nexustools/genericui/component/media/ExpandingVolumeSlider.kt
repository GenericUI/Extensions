package com.nexustools.genericui.component.media

import com.nexustools.genericui.component.action.StyledButton
import com.nexustools.genericui.component.content.Icon
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.css.CssBoxSizing
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.enums.css.CssDisplay
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.color.ImageTintMode
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.transition.CssTransitionSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * On-hover expanding media volume control (`<genericui:expanding-volume-slider>`) combining a
 * borderless circular mute/unmute [StyledButton] and an inline [SeekBar] volume slider inside
 * a bordered translucent pill [StyledContainer].
 *
 * At rest (`isExpanded == false`), the control renders as a compact `40x40` circular pill around
 * [muteButton] overlaid with a 5-layer vector stack (`speaker + wave1 + wave2 + wave3 + muteCross`)
 * backed by `:extensions:media:media` `R.drawable.*` SVGs with smooth per-wave fade transitions.
 * When hovered (`onPointerEnter`), focused, or actively scrubbed, [sliderContainer] smoothly
 * animates its expansion and collapse across all backends.
 */
open class ExpandingVolumeSlider(
    value: Double = 100.0,
    muted: Boolean = false,
    var expandedSliderWidthPx: Int = 92,
    var animationDurationMs: Long = 180L
) : StyledContainer("expanding-volume-slider", "genericui:expanding-volume-slider") {

    private var volumeIconRes: ResourceID? = null
    private var muteIconRes: ResourceID? = null
    private var tintIconsToTheme: Boolean = true
    private var isInternalSync: Boolean = false
    private var suppressIconSync: Boolean = false
    private var lastNonZeroPercent: Double = value.coerceIn(0.0, 100.0).let { if (it > 0.0) it else 100.0 }
    private var pointerInside: Boolean = false
    private val animationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var animationJob: Job? = null
    private var iconAnimationJob: Job? = null
    private var currentWave1Opacity: Float = 0f
    private var currentWave2Opacity: Float = 0f
    private var currentWave3Opacity: Float = 0f
    private var currentMuteCrossOpacity: Float = 0f

    var iconAnimationDurationMs: Long = 160L
        set(value) {
            field = value.coerceAtLeast(0L)
            syncLayerTransitions()
        }

    /**
     * Normalized expansion progress in `[0f..1f]` (`0f` = collapsed, `1f` = fully expanded).
     */
    var expansionProgress: Float = 0f
        private set

    /**
     * Current animated pixel width of [sliderContainer] in `[0..expandedSliderWidthPx]`.
     */
    val currentSliderWidthPx: Int
        get() = (expandedSliderWidthPx.coerceAtLeast(48) * expansionProgress.coerceIn(0f, 1f)).roundToInt()

    val muteButton: StyledButton = StyledButton(
        text = "",
        icon = null,
        type = BootstrapType.NONE,
        size = BootstrapSize.SM,
        outline = false
    ).apply {
        addClass("expanding-volume-mute-btn player-pill-inner-btn")
        widthPx = 32
        heightPx = 32
        setPadding(EdgeInsets.ZERO)
        setPaddingAll(SpacingScale.S0)
        backgroundColor = RgbaColor.TRANSPARENT
        borderWidthPx = 0f
        borderRadiusPx = 16f
        textColor = RgbaColor(242, 245, 250, 255)
        getStyle().apply {
            setCursor(CssCursor.POINTER)
            setWidth(CssLength.px(32))
            setHeight(CssLength.px(32))
            setPadding(CssEdgeLengths.px(0, 0))
            setBorderWidth(CssEdgeLengths.px(0))
            setBorderRadius(CssCornerRadii.px(16))
        }
        setHoverStyle(
            backgroundColor = RgbaColor(255, 126, 150, 56),
            borderColor = RgbaColor.TRANSPARENT,
            borderWidthPx = 0f,
            textColor = RgbaColor.WHITE
        )
        setFocusStyle(
            backgroundColor = null,
            borderColor = null,
            borderWidthPx = 0f,
            focusRingColor = null,
            focusRingWidthPx = 0f
        )
    }

    val speakerIconLayer: Icon = createVolumeLayerIcon(MediaVolumeIcons.speaker, "speaker")
    val wave1Layer: Icon = createVolumeLayerIcon(MediaVolumeIcons.wave1, "wave-1")
    val wave2Layer: Icon = createVolumeLayerIcon(MediaVolumeIcons.wave2, "wave-2")
    val wave3Layer: Icon = createVolumeLayerIcon(MediaVolumeIcons.wave3, "wave-3")
    val muteCrossLayer: Icon = createVolumeLayerIcon(MediaVolumeIcons.muteCross, "mute-cross")

    val muteIconOverlay: LayeredContainer = LayeredContainer(
        tagName = "genericui:volume-icon-stack",
        classPrefix = "expanding-volume-icon-stack"
    ).apply {
        setAttribute("data-overlay-role", "volume-icon-stack")
        widthPx = 32
        heightPx = 32
        getStyle().apply {
            setPosition(CssPosition.ABSOLUTE)
            setLeft(CssLength.px(4))
            setTop(CssLength.px(3))
            setWidth(CssLength.px(32))
            setHeight(CssLength.px(32))
            setPointerEvents(CssPointerEvents.NONE)
            setZIndex(2)
        }
        addFloatingLayer(
            component = speakerIconLayer,
            alignment = LayerAlignment.CENTER,
            zIndex = 1,
            passThroughPointerEvents = true
        )
        addFloatingLayer(
            component = wave1Layer,
            alignment = LayerAlignment.CENTER,
            zIndex = 2,
            passThroughPointerEvents = true
        )
        addFloatingLayer(
            component = wave2Layer,
            alignment = LayerAlignment.CENTER,
            zIndex = 3,
            passThroughPointerEvents = true
        )
        addFloatingLayer(
            component = wave3Layer,
            alignment = LayerAlignment.CENTER,
            zIndex = 4,
            passThroughPointerEvents = true
        )
        addFloatingLayer(
            component = muteCrossLayer,
            alignment = LayerAlignment.CENTER,
            zIndex = 5,
            passThroughPointerEvents = true
        )
    }

    val slider: SeekBar = SeekBar(
        value = if (muted) 0.0 else value.coerceIn(0.0, 100.0),
        min = 0.0,
        max = 100.0,
        step = 1.0
    ).apply {
        addClass("volume-slider expanding-volume-seekbar")
        trackHeightPx = 4f
        thumbRadiusPx = 5.5f
        accentColor = RgbaColor(255, 126, 150, 255)
        bufferedColor = RgbaColor.TRANSPARENT
        trackColor = RgbaColor(255, 255, 255, 62)
        thumbColor = RgbaColor(242, 245, 250, 255)
        val initialW = expandedSliderWidthPx.coerceAtLeast(48)
        style.width = CssLength.px(initialW)
        style.minWidth = CssLength.px(initialW)
        style.flexShrink = 0f
        style.boxSizing = CssBoxSizing.BORDER_BOX
    }

    val sliderContainer: Container = Container(
        tagName = "div",
        classPrefix = "expanding-volume-slider-track-host"
    ).apply {
        setDisplay(DisplayMode.INLINE_FLEX)
        alignItems = AlignItems.CENTER
        style.display = CssDisplay.INLINE_FLEX
        style.alignItems = AlignItems.CENTER
        style.overflow = OverflowBehavior.HIDDEN
        style.boxSizing = CssBoxSizing.BORDER_BOX
        style.setTransitions(
            CssTransitionSpec.parseList(
                "width ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1), max-width ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1), opacity ${(animationDurationMs * 0.8).toLong()}ms ease, margin-left ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1), margin-right ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1)"
            )
        )
        addChild(slider)
    }

    var value: Double = value.coerceIn(0.0, 100.0)
        set(newValue) {
            val clamped = newValue.coerceIn(0.0, 100.0)
            field = clamped
            if (clamped > 0.0) {
                lastNonZeroPercent = clamped
            }
            isInternalSync = true
            try {
                slider.value = if (isMuted) 0.0 else clamped
            } finally {
                isInternalSync = false
            }
            if (!suppressIconSync) {
                syncMuteButtonIcon(animate = true)
                syncStateAttributes()
            }
        }

    var volume: Float
        get() = if (isMuted) 0f else (value / 100.0).toFloat().coerceIn(0f, 1f)
        set(newVolume) {
            val pct = (newVolume.coerceIn(0f, 1f) * 100.0).coerceIn(0.0, 100.0)
            updateVolumeState(pct, muted = pct <= 0.0)
        }

    var isMuted: Boolean = muted
        set(newMuted) {
            field = newMuted
            isInternalSync = true
            try {
                slider.value = if (newMuted) 0.0 else value.coerceIn(0.0, 100.0)
            } finally {
                isInternalSync = false
            }
            if (!suppressIconSync) {
                syncMuteButtonIcon(animate = true)
                syncStateAttributes()
            }
        }

    var alwaysExpanded: Boolean = false
        set(value) {
            field = value
            if (value && !compactPopupMode) {
                isExpanded = true
            } else if (!value && !pointerInside && !slider.isScrubbing) {
                isExpanded = false
            }
            syncExpansionStyles()
        }

    var compactPopupMode: Boolean = false
        set(value) {
            field = value
            if (value) {
                isExpanded = false
            } else if (alwaysExpanded || pointerInside) {
                isExpanded = true
            }
            syncExpansionStyles()
        }

    var isExpanded: Boolean = false
        set(value) {
            val effective = if (compactPopupMode) false else (value || alwaysExpanded)
            if (field != effective) {
                field = effective
                syncExpansionStyles(animate = true)
                onExpandedChanged?.invoke(effective)
            }
        }

    var onVolumeChanged: ((Float) -> Unit)? = null
    var onValueChanged: ((Double) -> Unit)? = null
    var onMuteToggle: ((Boolean) -> Unit)? = null
    var onCompactButtonClick: (() -> Unit)? = null
    var onExpandedChanged: ((Boolean) -> Unit)? = null

    fun setExpanded(expanded: Boolean, animate: Boolean = true) {
        val effective = if (compactPopupMode) false else (expanded || alwaysExpanded)
        if (this.isExpanded == effective && animate) {
            return
        }
        val changed = isExpanded != effective
        if (changed) {
            isExpanded = effective
        }
        if (!animate) {
            syncExpansionStyles(animate = false)
        }
    }

    init {
        addClass("player-controls-pill expanding-volume-pill")
        setAttribute("data-overlay-role", "expanding-slider-pill")
        heightPx = 40
        setDisplay(DisplayMode.FLEX)
        flexDirection = FlexDirection.ROW
        alignItems = AlignItems.CENTER
        backgroundColor = RgbaColor(18, 18, 22, 188)
        setBorder(1, RgbaColor(255, 255, 255, 36), CssBorderStyle.SOLID, radiusPx = 20)
        getStyle().apply {
            setPosition(CssPosition.RELATIVE)
            setDisplay(CssDisplay.INLINE_FLEX)
            setFlexDirection(FlexDirection.ROW)
            setAlignItems(AlignItems.CENTER)
            setHeight(CssLength.px(40))
            setPadding(CssEdgeLengths.px(3, 4))
            setBorderRadius(CssCornerRadii.px(20))
            setBoxSizing(CssBoxSizing.BORDER_BOX)
        }

        addChild(muteButton)
        addChild(sliderContainer)
        addChild(muteIconOverlay)

        onPointerEnter = {
            pointerInside = true
            if (!compactPopupMode) {
                isExpanded = true
            }
        }
        onPointerLeave = {
            pointerInside = false
            if (!slider.isScrubbing && !alwaysExpanded) {
                isExpanded = false
            }
        }

        muteButton.onClick = {
            if (compactPopupMode && onCompactButtonClick != null) {
                onCompactButtonClick?.invoke()
            } else {
                val nextMuted = !isMuted
                val nextValue = if (!nextMuted && this.value <= 0.0) {
                    lastNonZeroPercent.coerceIn(5.0, 100.0)
                } else {
                    this.value
                }
                updateVolumeState(nextValue, nextMuted)
                onMuteToggle?.invoke(nextMuted)
            }
        }

        slider.onSeekStart = {
            if (!compactPopupMode) {
                isExpanded = true
            }
        }
        slider.onValueChanged = { nextPct ->
            if (!isInternalSync) {
                val clamped = nextPct.coerceIn(0.0, 100.0)
                val nextMuted = clamped <= 0.0
                updateVolumeState(clamped, nextMuted)
                onValueChanged?.invoke(clamped)
                onVolumeChanged?.invoke((clamped / 100.0).toFloat().coerceIn(0f, 1f))
            }
        }
        slider.onSeekCommit = {
            if (!pointerInside && !alwaysExpanded) {
                isExpanded = false
            }
        }

        syncMuteButtonIcon(animate = false)
        syncExpansionStyles(animate = false)
        syncStateAttributes()
    }

    fun setVolumeIcons(volumeIcon: ResourceID, muteIcon: ResourceID, tintToTheme: Boolean = true) {
        this.volumeIconRes = volumeIcon
        this.muteIconRes = muteIcon
        this.tintIconsToTheme = tintToTheme
        setAttribute("data-custom-volume-icons", "true")
        muteIconOverlay.isVisible = false
        syncMuteButtonIcon(animate = false)
    }

    fun updateVolumeState(volumePercent: Double, muted: Boolean) {
        val clamped = volumePercent.coerceIn(0.0, 100.0)
        if (clamped > 0.0) {
            lastNonZeroPercent = clamped
        }
        suppressIconSync = true
        try {
            this.value = clamped
            this.isMuted = muted
        } finally {
            suppressIconSync = false
        }
        syncMuteButtonIcon(animate = true)
        syncStateAttributes()
    }

    private fun createVolumeLayerIcon(resId: ResourceID, layerTag: String): Icon = Icon(
        resId = resId,
        sizePx = 18
    ).apply {
        placeholderUrl = ""
        tintMode = ImageTintMode.SRC_IN
        tintColor = RgbaColor(242, 245, 250, 255)
        cssFilter = CssFilter(brightness = 0f, invert = 1f)
        setAttribute("data-volume-layer", layerTag)
        getStyle().apply {
            setWidth(CssLength.px(18))
            setHeight(CssLength.px(18))
            setPointerEvents(CssPointerEvents.NONE)
            setTransitions(CssTransitionSpec.parseList("opacity ${iconAnimationDurationMs.coerceAtLeast(1L)}ms ease"))
        }
    }

    private fun syncLayerTransitions() {
        val dur = iconAnimationDurationMs.coerceAtLeast(1L)
        val specs = CssTransitionSpec.parseList("opacity ${dur}ms ease")
        speakerIconLayer.style.setTransitions(specs)
        wave1Layer.style.setTransitions(specs)
        wave2Layer.style.setTransitions(specs)
        wave3Layer.style.setTransitions(specs)
        muteCrossLayer.style.setTransitions(specs)
    }

    private fun computeTargetLayerOpacities(percent: Double, muted: Boolean): FloatArray {
        if (muted) {
            return floatArrayOf(0f, 0f, 0f, 1f)
        }
        if (percent <= 0.0) {
            return floatArrayOf(0f, 0f, 0f, 0f)
        }
        val w1 = (percent / 10.0).toFloat().coerceIn(0f, 1f)
        val w2 = if (percent <= 33.0) 0f else ((percent - 33.0) / 12.0).toFloat().coerceIn(0f, 1f)
        val w3 = if (percent <= 66.0) 0f else ((percent - 66.0) / 14.0).toFloat().coerceIn(0f, 1f)
        return floatArrayOf(w1, w2, w3, 0f)
    }

    private fun applyIconLayerOpacities(
        wave1: Float,
        wave2: Float,
        wave3: Float,
        muteCross: Float
    ) {
        val w1 = wave1.coerceIn(0f, 1f)
        val w2 = wave2.coerceIn(0f, 1f)
        val w3 = wave3.coerceIn(0f, 1f)
        val mc = muteCross.coerceIn(0f, 1f)
        currentWave1Opacity = w1
        currentWave2Opacity = w2
        currentWave3Opacity = w3
        currentMuteCrossOpacity = mc
        speakerIconLayer.opacity = 1f
        wave1Layer.opacity = w1
        wave2Layer.opacity = w2
        wave3Layer.opacity = w3
        muteCrossLayer.opacity = mc
        setAttribute("data-wave-1-opacity", formatOpacityAttr(w1))
        setAttribute("data-wave-2-opacity", formatOpacityAttr(w2))
        setAttribute("data-wave-3-opacity", formatOpacityAttr(w3))
        setAttribute("data-mute-cross-opacity", formatOpacityAttr(mc))
    }

    private fun formatOpacityAttr(value: Float): String {
        val clamped = value.coerceIn(0f, 1f)
        return when {
            clamped <= 0.0005f -> "0"
            clamped >= 0.9995f -> "1"
            else -> ((clamped * 1000f).roundToInt() / 1000f).toString()
        }
    }

    private fun syncMuteButtonIcon(animate: Boolean = true) {
        val showMute = isMuted || value <= 0.0
        val volRes = volumeIconRes
        val mRes = muteIconRes
        if (volRes != null && mRes != null) {
            iconAnimationJob?.cancel()
            iconAnimationJob = null
            muteIconOverlay.isVisible = false
            val targetRes = if (showMute) mRes else volRes
            if (muteButton.iconRes != targetRes) {
                muteButton.setImage(targetRes, tintToTheme = tintIconsToTheme)
            }
            val targets = computeTargetLayerOpacities(value, isMuted)
            applyIconLayerOpacities(targets[0], targets[1], targets[2], targets[3])
            return
        }

        if (!muteIconOverlay.isVisible) {
            muteIconOverlay.isVisible = true
        }
        if (muteButton.icon != null || muteButton.image != null) {
            muteButton.icon = null
            muteButton.setImage(null, tintToTheme = tintIconsToTheme)
        }

        val targets = computeTargetLayerOpacities(value, isMuted)
        val targetW1 = targets[0]
        val targetW2 = targets[1]
        val targetW3 = targets[2]
        val targetCross = targets[3]

        iconAnimationJob?.cancel()
        iconAnimationJob = null

        val activeBackendName = resolveActiveFactory()?.backendTarget?.name
        val shouldInterpolateSystem = animate &&
            activeBackendName != null &&
            activeBackendName != "WEB" &&
            iconAnimationDurationMs > 0L &&
            (
                abs(currentWave1Opacity - targetW1) > 0.005f ||
                    abs(currentWave2Opacity - targetW2) > 0.005f ||
                    abs(currentWave3Opacity - targetW3) > 0.005f ||
                    abs(currentMuteCrossOpacity - targetCross) > 0.005f
                )

        if (!shouldInterpolateSystem) {
            applyIconLayerOpacities(targetW1, targetW2, targetW3, targetCross)
            return
        }

        val startW1 = currentWave1Opacity
        val startW2 = currentWave2Opacity
        val startW3 = currentWave3Opacity
        val startCross = currentMuteCrossOpacity
        val stepIntervalMs = 16L
        val totalSteps = (iconAnimationDurationMs / stepIntervalMs).toInt().coerceAtLeast(2)

        val firstT = 1f / totalSteps.toFloat()
        val firstEased = firstT * firstT * (3f - 2f * firstT)
        applyIconLayerOpacities(
            startW1 + (targetW1 - startW1) * firstEased,
            startW2 + (targetW2 - startW2) * firstEased,
            startW3 + (targetW3 - startW3) * firstEased,
            startCross + (targetCross - startCross) * firstEased
        )

        iconAnimationJob = animationScope.launch {
            for (step in 2..totalSteps) {
                delay(stepIntervalMs)
                val t = (step.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
                val eased = t * t * (3f - 2f * t)
                if (step == totalSteps) {
                    applyIconLayerOpacities(targetW1, targetW2, targetW3, targetCross)
                } else {
                    applyIconLayerOpacities(
                        startW1 + (targetW1 - startW1) * eased,
                        startW2 + (targetW2 - startW2) * eased,
                        startW3 + (targetW3 - startW3) * eased,
                        startCross + (targetCross - startCross) * eased
                    )
                }
            }
        }
    }

    private fun syncExpansionStyles(animate: Boolean = true) {
        val showInlineSlider = isExpanded && !compactPopupMode
        sliderContainer.isVisible = !compactPopupMode
        val w = expandedSliderWidthPx.coerceAtLeast(48)
        slider.style.width = CssLength.px(w)
        slider.style.minWidth = CssLength.px(w)
        slider.style.flexShrink = 0f
        sliderContainer.style.setTransitions(
            CssTransitionSpec.parseList(
                "width ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1), max-width ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1), opacity ${(animationDurationMs * 0.8).toLong()}ms ease, margin-left ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1), margin-right ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1)"
            )
        )
        if (showInlineSlider) {
            sliderContainer.style.width = CssLength.px(w)
            sliderContainer.style.maxWidth = CssLength.px(w)
            sliderContainer.style.opacity = 1f
            sliderContainer.style.marginLeft = CssLength.px(4)
            sliderContainer.style.marginRight = CssLength.px(6)
            sliderContainer.style.pointerEvents = CssPointerEvents.AUTO
            setAttribute("data-expanded", "true")
        } else {
            sliderContainer.style.width = CssLength.ZERO
            sliderContainer.style.maxWidth = CssLength.ZERO
            sliderContainer.style.opacity = 0f
            sliderContainer.style.marginLeft = CssLength.ZERO
            sliderContainer.style.marginRight = CssLength.ZERO
            sliderContainer.style.pointerEvents = CssPointerEvents.NONE
            setAttribute("data-expanded", "false")
        }
        val targetProgress = if (showInlineSlider) 1f else 0f
        startExpansionAnimation(targetProgress, animate = animate && animationDurationMs > 0L)
    }

    private fun startExpansionAnimation(targetProgress: Float, animate: Boolean) {
        animationJob?.cancel()
        animationJob = null
        val clampedTarget = targetProgress.coerceIn(0f, 1f)
        if (!animate || animationDurationMs <= 0L || abs(expansionProgress - clampedTarget) <= 0.005f) {
            expansionProgress = clampedTarget
            applyAnimatedFrame(clampedTarget)
            return
        }
        val startProgress = expansionProgress
        val delta = clampedTarget - startProgress
        val expanding = delta > 0f
        val totalDurationMs = (animationDurationMs * abs(delta)).toLong().coerceAtLeast(32L)
        val stepIntervalMs = 16L
        val totalSteps = (totalDurationMs / stepIntervalMs).toInt().coerceAtLeast(2)
        // Apply first slight step immediately so native renderers begin moving on the first repaint
        val firstT = 1f / totalSteps.toFloat()
        val firstEased = if (expanding) 1f - (1f - firstT).pow(3) else firstT * firstT * (3f - 2f * firstT)
        expansionProgress = (startProgress + delta * firstEased).coerceIn(0f, 1f)
        applyAnimatedFrame(expansionProgress)

        animationJob = animationScope.launch {
            for (step in 2..totalSteps) {
                delay(stepIntervalMs)
                val t = (step.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
                val eased = if (expanding) {
                    1f - (1f - t).pow(3)
                } else {
                    t * t * (3f - 2f * t)
                }
                val current = if (step == totalSteps) clampedTarget else (startProgress + delta * eased).coerceIn(0f, 1f)
                expansionProgress = current
                applyAnimatedFrame(current)
            }
        }
    }

    private fun applyAnimatedFrame(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        val maxW = expandedSliderWidthPx.coerceAtLeast(48)
        val animW = (maxW * clamped).roundToInt()
        setAttribute("data-expansion-progress", clamped.toString())
        setAttribute("data-animated-slider-width-px", animW.toString())
        sliderContainer.setAttribute("data-expansion-progress", clamped.toString())
        sliderContainer.setAttribute("data-animated-width-px", animW.toString())
        val activeBackendName = resolveActiveFactory()?.backendTarget?.name
        if (activeBackendName != null && activeBackendName != "WEB") {
            if (sliderContainer.widthPx != animW) {
                sliderContainer.widthPx = animW
            }
            sliderContainer.opacity = clamped
        }
    }

    override fun dispose() {
        animationJob?.cancel()
        animationJob = null
        iconAnimationJob?.cancel()
        iconAnimationJob = null
        super.dispose()
    }

    private fun syncStateAttributes() {
        setAttribute("data-volume-percent", value.toInt().toString())
        setAttribute("data-muted", isMuted.toString())
    }

    fun bindTo(player: Player): Disposable {
        var disposed = false
        val syncFromPlayer: () -> Unit = {
            if (!disposed && !slider.isScrubbing) {
                updateVolumeState((player.volume * 100.0).coerceIn(0.0, 100.0), player.isMuted)
            }
        }
        when (player) {
            is VideoPlayer -> player.addStateChangeObserver(syncFromPlayer)
            is AudioPlayer -> player.addStateChangeObserver(syncFromPlayer)
        }
        val prevVolHandler = onVolumeChanged
        val prevMuteHandler = onMuteToggle
        onVolumeChanged = { vol ->
            if (!disposed) {
                player.volume = vol
                player.isMuted = vol <= 0f
            }
            prevVolHandler?.invoke(vol)
        }
        onMuteToggle = { muted ->
            if (!disposed) {
                player.isMuted = muted
            }
            prevMuteHandler?.invoke(muted)
        }
        syncFromPlayer()
        return object : Disposable {
            override val isDisposed: Boolean
                get() = disposed

            override fun dispose() {
                if (disposed) return
                disposed = true
                when (player) {
                    is VideoPlayer -> player.removeStateChangeObserver(syncFromPlayer)
                    is AudioPlayer -> player.removeStateChangeObserver(syncFromPlayer)
                }
            }
        }
    }

    companion object {
        const val DEFAULT_VOLUME_SVG: String =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"18\" height=\"18\" fill=\"currentColor\"><path d=\"M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z\"/></svg>"

        const val DEFAULT_MUTE_SVG: String =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"18\" height=\"18\" fill=\"currentColor\"><path d=\"M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z\"/></svg>"
    }
}
