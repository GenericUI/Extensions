package com.nexustools.genericui.component.media

import com.nexustools.genericui.component.action.StyledButton
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.enums.css.CssDisplay
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssLength
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
 * [muteButton]. When hovered (`onPointerEnter`), focused, or actively scrubbed, [sliderContainer]
 * smoothly animates its expansion and collapse across all backends.
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
    private var lastNonZeroPercent: Double = value.coerceIn(0.0, 100.0).let { if (it > 0.0) it else 100.0 }
    private var pointerInside: Boolean = false
    private val animationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var animationJob: Job? = null

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
        icon = DEFAULT_VOLUME_SVG,
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
        setStyleProperty("width", "${initialW}px")
        setStyleProperty("min-width", "${initialW}px")
        setStyleProperty("flex-shrink", "0")
        setStyleProperty("box-sizing", "border-box")
    }

    val sliderContainer: Container = Container(
        tagName = "div",
        classPrefix = "expanding-volume-slider-track-host"
    ).apply {
        setDisplay(DisplayMode.FLEX)
        alignItems = AlignItems.CENTER
        getStyle().setDisplay(CssDisplay.FLEX)
        setStyleProperty("display", "inline-flex")
        setStyleProperty("align-items", "center")
        setStyleProperty("overflow", "hidden")
        setStyleProperty("box-sizing", "border-box")
        setStyleProperty(
            "transition",
            "width ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1), max-width ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1), opacity ${(animationDurationMs * 0.8).toLong()}ms ease, margin-left ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1), margin-right ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1)"
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
            syncMuteButtonIcon()
            syncStateAttributes()
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
            syncMuteButtonIcon()
            syncStateAttributes()
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
        heightPx = 40
        setDisplay(DisplayMode.FLEX)
        flexDirection = FlexDirection.ROW
        alignItems = AlignItems.CENTER
        backgroundColor = RgbaColor(18, 18, 22, 188)
        setBorder(1, RgbaColor(255, 255, 255, 36), CssBorderStyle.SOLID, radiusPx = 20)
        getStyle().apply {
            setDisplay(CssDisplay.FLEX)
            setHeight(CssLength.px(40))
            setPadding(CssEdgeLengths.px(3, 4))
            setBorderRadius(CssCornerRadii.px(20))
        }
        setStyleProperty("display", "inline-flex")
        setStyleProperty("flex-direction", "row")
        setStyleProperty("align-items", "center")
        setStyleProperty("height", "40px")
        setStyleProperty("box-sizing", "border-box")

        addChild(muteButton)
        addChild(sliderContainer)

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
                if (!nextMuted && this.value <= 0.0) {
                    this.value = lastNonZeroPercent.coerceIn(5.0, 100.0)
                }
                updateVolumeState(this.value, nextMuted)
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

        syncMuteButtonIcon()
        syncExpansionStyles(animate = false)
        syncStateAttributes()
    }

    fun setVolumeIcons(volumeIcon: ResourceID, muteIcon: ResourceID, tintToTheme: Boolean = true) {
        this.volumeIconRes = volumeIcon
        this.muteIconRes = muteIcon
        this.tintIconsToTheme = tintToTheme
        syncMuteButtonIcon()
    }

    fun updateVolumeState(volumePercent: Double, muted: Boolean) {
        val clamped = volumePercent.coerceIn(0.0, 100.0)
        if (clamped > 0.0) {
            lastNonZeroPercent = clamped
        }
        this.value = clamped
        this.isMuted = muted
    }

    private fun syncMuteButtonIcon() {
        val showMute = isMuted || value <= 0.0
        val volRes = volumeIconRes
        val mRes = muteIconRes
        if (volRes != null && mRes != null) {
            val targetRes = if (showMute) mRes else volRes
            if (muteButton.iconRes != targetRes) {
                muteButton.setImage(targetRes, tintToTheme = tintIconsToTheme)
            }
        } else {
            val targetSvg = if (showMute) DEFAULT_MUTE_SVG else DEFAULT_VOLUME_SVG
            if (muteButton.icon != targetSvg) {
                muteButton.icon = targetSvg
            }
        }
    }

    private fun syncExpansionStyles(animate: Boolean = true) {
        val showInlineSlider = isExpanded && !compactPopupMode
        sliderContainer.isVisible = !compactPopupMode
        val w = expandedSliderWidthPx.coerceAtLeast(48)
        slider.setStyleProperty("width", "${w}px")
        slider.setStyleProperty("min-width", "${w}px")
        slider.setStyleProperty("flex-shrink", "0")
        sliderContainer.setStyleProperty(
            "transition",
            "width ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1), max-width ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1), opacity ${(animationDurationMs * 0.8).toLong()}ms ease, margin-left ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1), margin-right ${animationDurationMs}ms cubic-bezier(0.22, 1, 0.36, 1)"
        )
        if (showInlineSlider) {
            sliderContainer.setStyleProperty("width", "${w}px")
            sliderContainer.setStyleProperty("max-width", "${w}px")
            sliderContainer.setStyleProperty("opacity", "1")
            sliderContainer.setStyleProperty("margin-left", "4px")
            sliderContainer.setStyleProperty("margin-right", "6px")
            sliderContainer.setStyleProperty("pointer-events", "auto")
            setAttribute("data-expanded", "true")
        } else {
            sliderContainer.setStyleProperty("width", "0px")
            sliderContainer.setStyleProperty("max-width", "0px")
            sliderContainer.setStyleProperty("opacity", "0")
            sliderContainer.setStyleProperty("margin-left", "0px")
            sliderContainer.setStyleProperty("margin-right", "0px")
            sliderContainer.setStyleProperty("pointer-events", "none")
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
        super.dispose()
    }

    private fun syncStateAttributes() {
        setAttribute("data-volume-percent", value.toInt().toString())
        setAttribute("data-muted", isMuted.toString())
    }

    fun bindTo(videoPlayer: VideoPlayer): Disposable {
        var disposed = false
        val syncFromPlayer: () -> Unit = {
            if (!disposed && !slider.isScrubbing) {
                updateVolumeState((videoPlayer.volume * 100.0).coerceIn(0.0, 100.0), videoPlayer.isMuted)
            }
        }
        videoPlayer.addStateChangeObserver(syncFromPlayer)
        val prevVolHandler = onVolumeChanged
        val prevMuteHandler = onMuteToggle
        onVolumeChanged = { vol ->
            if (!disposed) {
                videoPlayer.volume = vol
                videoPlayer.isMuted = vol <= 0f
            }
            prevVolHandler?.invoke(vol)
        }
        onMuteToggle = { muted ->
            if (!disposed) {
                videoPlayer.isMuted = muted
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
                videoPlayer.removeStateChangeObserver(syncFromPlayer)
            }
        }
    }

    fun bindTo(audioPlayer: AudioPlayer): Disposable {
        var disposed = false
        val syncFromPlayer: () -> Unit = {
            if (!disposed && !slider.isScrubbing) {
                val vol = audioPlayer.volume.coerceIn(0f, 1f)
                updateVolumeState((vol * 100.0).coerceIn(0.0, 100.0), vol <= 0f)
            }
        }
        audioPlayer.addStateChangeObserver(syncFromPlayer)
        val prevVolHandler = onVolumeChanged
        val prevMuteHandler = onMuteToggle
        onVolumeChanged = { vol ->
            if (!disposed) {
                audioPlayer.volume = vol
            }
            prevVolHandler?.invoke(vol)
        }
        onMuteToggle = { muted ->
            if (!disposed) {
                audioPlayer.volume = if (muted) 0f else (value / 100.0).toFloat().coerceIn(0f, 1f)
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
                audioPlayer.removeStateChangeObserver(syncFromPlayer)
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
