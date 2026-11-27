package com.nexustools.genericui.component.media

import com.nexustools.genericui.component.action.styled.StyledButton
import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.content.Icon
import com.nexustools.genericui.component.form.styled.StyledSlider
import com.nexustools.genericui.component.layout.styled.StyledContainer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.resources.ResourceID
import kotlin.math.roundToInt

/**
 * Encapsulated media volume composite (`<genericui:expanding-volume-slider>`) extending
 * `:common`'s [Composite] (NOT `AbstractContainer`) and containing:
 * - A borderless mute/unmute [StyledButton] (`.expanding-volume-mute-btn`)
 * - An inline [StyledSlider] (`.expanding-volume-slider-control`, NOT `SeekBar`)
 * - A pointer-pass-through 5-layer vector speaker icon stack (`speaker`, `wave-1`, `wave-2`,
 *   `wave-3`, `mute-cross`) backed by [MediaVolumeIcons] SVG resources in `R.drawable`.
 *
 * Wave visibility, mute cross state, hover/focus expansion, and visual theme colors are
 * toggled purely via semantic CSS classes (`"muted"`, `"volume-zero"`, `"volume-low"`,
 * `"volume-medium"`, `"volume-high"`, `"expanded"`, `"scrubbing"`, `"always-expanded"`,
 * `"compact-popup"`) and styled in `media/style.scss` + consumer-compiled `theme.scss`,
 * with zero exposed public child-component fields and zero inline Kotlin style setters.
 */
open class ExpandingVolumeSlider(
    value: Double = 100.0,
    muted: Boolean = false,
    var expandedSliderWidthPx: Int = 92,
    var animationDurationMs: Long = 180L
) : Composite(
    classPrefix = DEFAULT_CLASS_PREFIX,
    tagName = DEFAULT_TAG_NAME
) {

    private var volumeIconRes: ResourceID? = null
    private var muteIconRes: ResourceID? = null
    private var tintIconsToTheme: Boolean = true
    private var isInternalSync: Boolean = false
    private var suppressClassSync: Boolean = false
    private var suppressVolumeSliderPush: Boolean = false
    private var lastSyncedVolumePercent: Double = value.coerceIn(0.0, 100.0)
    private var lastSyncedMuted: Boolean = muted
    private var lastNonZeroPercent: Double = value.coerceIn(0.0, 100.0).let { if (it > 0.0) it else 100.0 }
    private var explicitExpanded: Boolean = false

    private val muteButton: StyledButton = StyledButton(
        text = "",
        icon = null,
        styledClassPrefix = MUTE_BUTTON_CLASS
    ).apply {
        addClass(MUTE_BUTTON_CLASS)
        addClass(PILL_INNER_BTN_CLASS)
    }

    private val speakerIconLayer: Icon = createVolumeLayerIcon(MediaVolumeIcons.speaker, "speaker")
    private val wave1Layer: Icon = createVolumeLayerIcon(MediaVolumeIcons.wave1, "wave-1")
    private val wave2Layer: Icon = createVolumeLayerIcon(MediaVolumeIcons.wave2, "wave-2")
    private val wave3Layer: Icon = createVolumeLayerIcon(MediaVolumeIcons.wave3, "wave-3")
    private val muteCrossLayer: Icon = createVolumeLayerIcon(MediaVolumeIcons.muteCross, "mute-cross")

    private val iconStackContainer: StyledContainer = StyledContainer(
        classPrefix = ICON_STACK_CLASS,
        tagName = StyledContainer_TAG_NAME
    ).apply {
        addClass(ICON_STACK_CLASS)
        setAttribute("data-overlay-role", "volume-icon-stack")
        addChild(speakerIconLayer)
        addChild(wave1Layer)
        addChild(wave2Layer)
        addChild(wave3Layer)
        addChild(muteCrossLayer)
    }

    private val volumeSlider: StyledSlider = StyledSlider(
        min = 0.0,
        max = 100.0,
        value = if (muted) 0.0 else value.coerceIn(0.0, 100.0),
        step = 1.0
    ).apply {
        addClass(SLIDER_CONTROL_CLASS)
        addClass(LEGACY_VOLUME_SLIDER_CLASS)
    }

    var value: Double = value.coerceIn(0.0, 100.0)
        set(newValue) {
            val clamped = newValue.coerceIn(0.0, 100.0)
            if (field == clamped && lastSyncedVolumePercent == clamped) return
            field = clamped
            lastSyncedVolumePercent = clamped
            if (clamped > 0.0) {
                lastNonZeroPercent = clamped
            }
            if (!suppressVolumeSliderPush) {
                isInternalSync = true
                try {
                    volumeSlider.value = if (isMuted) 0.0 else clamped
                } finally {
                    isInternalSync = false
                }
            }
            if (!suppressClassSync) {
                syncMuteButtonIconAndClasses()
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
            if (field == newMuted && lastSyncedMuted == newMuted) return
            field = newMuted
            lastSyncedMuted = newMuted
            if (!suppressVolumeSliderPush) {
                isInternalSync = true
                try {
                    volumeSlider.value = if (newMuted) 0.0 else value.coerceIn(0.0, 100.0)
                } finally {
                    isInternalSync = false
                }
            }
            if (!suppressClassSync) {
                syncMuteButtonIconAndClasses()
                syncStateAttributes()
            }
        }

    val isScrubbing: Boolean
        get() = volumeSlider.isScrubbing

    var alwaysExpanded: Boolean = false
        set(value) {
            field = value
            if (value) addClass(ALWAYS_EXPANDED_CLASS) else removeClass(ALWAYS_EXPANDED_CLASS)
            syncExpansionClasses()
        }

    var compactPopupMode: Boolean = false
        set(value) {
            field = value
            if (value) addClass(COMPACT_POPUP_CLASS) else removeClass(COMPACT_POPUP_CLASS)
            if (value) {
                explicitExpanded = false
            }
            syncExpansionClasses()
        }

    var isExpanded: Boolean
        get() = !compactPopupMode && (explicitExpanded || isHovered || alwaysExpanded || volumeSlider.isScrubbing)
        set(value) {
            val nextExplicit = if (compactPopupMode) false else value
            if (explicitExpanded != nextExplicit) {
                explicitExpanded = nextExplicit
                syncExpansionClasses()
                onExpandedChanged?.invoke(isExpanded)
            }
        }

    val expansionProgress: Float
        get() = if (isExpanded && !compactPopupMode) 1f else 0f

    val currentSliderWidthPx: Int
        get() = if (isExpanded && !compactPopupMode) expandedSliderWidthPx.coerceAtLeast(48) else 0

    var onVolumeChanged: ((Float) -> Unit)? = null
    var onValueChanged: ((Double) -> Unit)? = null
    var onMutedChanged: ((Boolean) -> Unit)? = null
    var onMuteToggle: ((Boolean) -> Unit)? = null
    var onCompactButtonClick: (() -> Unit)? = null
    var onExpandedChanged: ((Boolean) -> Unit)? = null

    init {
        MediaExtension.ensureStyleSheetRegistered()
        addClass(DEFAULT_CLASS_PREFIX)
        addClass(PILL_CONTROLS_CLASS)
        addClass(PILL_VOLUME_CLASS)
        setAttribute("data-overlay-role", "expanding-slider-pill")

        addCompositeChild(muteButton)
        addCompositeChild(volumeSlider)
        addCompositeChild(iconStackContainer)

        muteButton.onClick = {
            if (compactPopupMode && onCompactButtonClick != null) {
                onCompactButtonClick?.invoke()
            } else {
                toggleMute()
            }
        }

        volumeSlider.onScrubStateChanged = { scrubbing ->
            if (scrubbing) {
                addClass(SCRUBBING_CLASS)
            } else {
                removeClass(SCRUBBING_CLASS)
            }
            syncExpansionClasses()
        }

        volumeSlider.onValueChanged { nextPct ->
            if (!isInternalSync) {
                val clamped = nextPct.coerceIn(0.0, 100.0)
                val nextMuted = clamped <= 0.0
                updateVolumeState(clamped, nextMuted)
                onValueChanged?.invoke(clamped)
                onVolumeChanged?.invoke((clamped / 100.0).toFloat().coerceIn(0f, 1f))
            }
        }

        syncMuteButtonIconAndClasses()
        syncExpansionClasses()
        syncStateAttributes()
    }

    internal fun internalMuteButton(): StyledButton = muteButton
    internal fun internalVolumeSlider(): StyledSlider = volumeSlider
    internal fun internalIconStack(): StyledContainer = iconStackContainer
    internal fun internalIconLayers(): List<Icon> =
        listOf(speakerIconLayer, wave1Layer, wave2Layer, wave3Layer, muteCrossLayer)

    internal fun mediaCompositeChildren(): List<AbstractComponent> = compositeChildren

    fun beginScrub() {
        volumeSlider.beginScrub()
    }

    fun endScrub() {
        volumeSlider.endScrub()
    }

    fun toggleMute() {
        val nextMuted = !isMuted
        val nextValue = if (!nextMuted && this.value <= 0.0) {
            lastNonZeroPercent.coerceIn(5.0, 100.0)
        } else {
            this.value
        }
        updateVolumeState(nextValue, nextMuted)
        onMuteToggle?.invoke(nextMuted)
        onMutedChanged?.invoke(nextMuted)
    }

    fun setExpanded(expanded: Boolean, animate: Boolean = true) {
        val effective = if (compactPopupMode) false else expanded
        if (explicitExpanded != effective) {
            isExpanded = effective
        } else {
            syncExpansionClasses()
        }
    }

    fun setVolumeIcons(volumeIcon: ResourceID, muteIcon: ResourceID, tintToTheme: Boolean = true) {
        this.volumeIconRes = volumeIcon
        this.muteIconRes = muteIcon
        this.tintIconsToTheme = tintToTheme
        setAttribute("data-custom-volume-icons", "true")
        iconStackContainer.isVisible = false
        syncMuteButtonIconAndClasses()
    }

    fun updateVolumeState(volumePercent: Double, muted: Boolean) {
        val clamped = volumePercent.coerceIn(0.0, 100.0)
        if (clamped == lastSyncedVolumePercent && muted == lastSyncedMuted) return
        if (clamped > 0.0) {
            lastNonZeroPercent = clamped
        }
        suppressClassSync = true
        suppressVolumeSliderPush = true
        try {
            this.value = clamped
            this.isMuted = muted
        } finally {
            suppressVolumeSliderPush = false
            suppressClassSync = false
        }
        lastSyncedVolumePercent = clamped
        lastSyncedMuted = muted
        isInternalSync = true
        try {
            volumeSlider.value = if (muted) 0.0 else clamped
        } finally {
            isInternalSync = false
        }
        syncMuteButtonIconAndClasses()
        syncStateAttributes()
    }

    fun bindTo(player: Player): Disposable {
        var disposed = false
        val syncFromPlayer: () -> Unit = {
            if (!disposed && !isScrubbing) {
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

    private fun createVolumeLayerIcon(resId: ResourceID, layerTag: String): Icon = Icon(
        resId = resId,
        sizePx = 18
    ).apply {
        placeholderUrl = ""
        addClass(ICON_LAYER_CLASS)
        addClass("volume-layer-$layerTag")
        setAttribute("data-volume-layer", layerTag)
    }

    private fun syncMuteButtonIconAndClasses() {
        val tierClass = resolveVolumeTierClass(value, isMuted)
        for (cls in VOLUME_TIER_CLASSES) {
            if (cls == tierClass) {
                addClass(cls)
            } else {
                removeClass(cls)
            }
        }
        setAttribute("data-volume-tier", tierClass)

        val volRes = volumeIconRes
        val mRes = muteIconRes
        if (volRes != null && mRes != null) {
            iconStackContainer.isVisible = false
            val targetRes = if (isMuted || value <= 0.0) mRes else volRes
            if (muteButton.iconRes != targetRes) {
                muteButton.setImage(targetRes, tintToTheme = tintIconsToTheme)
            }
        } else {
            if (!iconStackContainer.isVisible) {
                iconStackContainer.isVisible = true
            }
            if (muteButton.icon != null || muteButton.image != null) {
                muteButton.icon = null
                muteButton.setImage(null, tintToTheme = tintIconsToTheme)
            }
        }
    }

    private fun syncExpansionClasses() {
        val showInlineSlider = isExpanded && !compactPopupMode
        volumeSlider.isVisible = !compactPopupMode
        if (explicitExpanded && !compactPopupMode) {
            addClass(EXPANDED_CLASS)
        } else {
            removeClass(EXPANDED_CLASS)
        }
        setAttribute("data-expanded", showInlineSlider.toString())
        setAttribute("data-expansion-progress", if (showInlineSlider) "1.0" else "0.0")
        setAttribute("data-animated-slider-width-px", currentSliderWidthPx.toString())
    }

    private fun syncStateAttributes() {
        setAttribute("data-volume-percent", value.roundToInt().toString())
        setAttribute("data-muted", isMuted.toString())
    }

    private fun resolveVolumeTierClass(percent: Double, muted: Boolean): String = when {
        muted -> CLASS_MUTED
        percent <= 0.0 -> CLASS_VOLUME_ZERO
        percent <= 33.0 -> CLASS_VOLUME_LOW
        percent <= 66.0 -> CLASS_VOLUME_MEDIUM
        else -> CLASS_VOLUME_HIGH
    }

    companion object {
        const val DEFAULT_TAG_NAME: String = "genericui:expanding-volume-slider"
        const val DEFAULT_CLASS_PREFIX: String = "expanding-volume-slider"
        const val MUTE_BUTTON_CLASS: String = "expanding-volume-mute-btn"
        const val ICON_STACK_CLASS: String = "expanding-volume-icon-stack"
        const val ICON_LAYER_CLASS: String = "expanding-volume-icon-layer"
        const val SLIDER_CONTROL_CLASS: String = "expanding-volume-slider-control"
        private const val StyledContainer_TAG_NAME: String = "genericui:volume-icon-stack"
        private const val PILL_CONTROLS_CLASS: String = "player-controls-pill"
        private const val PILL_VOLUME_CLASS: String = "expanding-volume-pill"
        private const val PILL_INNER_BTN_CLASS: String = "player-pill-inner-btn"
        private const val LEGACY_VOLUME_SLIDER_CLASS: String = "volume-slider"
        const val EXPANDED_CLASS: String = "expanded"
        const val ALWAYS_EXPANDED_CLASS: String = "always-expanded"
        const val COMPACT_POPUP_CLASS: String = "compact-popup"
        const val SCRUBBING_CLASS: String = "scrubbing"
        const val CLASS_MUTED: String = "muted"
        const val CLASS_VOLUME_ZERO: String = "volume-zero"
        const val CLASS_VOLUME_LOW: String = "volume-low"
        const val CLASS_VOLUME_MEDIUM: String = "volume-medium"
        const val CLASS_VOLUME_HIGH: String = "volume-high"

        private val VOLUME_TIER_CLASSES: List<String> = listOf(
            CLASS_MUTED,
            CLASS_VOLUME_ZERO,
            CLASS_VOLUME_LOW,
            CLASS_VOLUME_MEDIUM,
            CLASS_VOLUME_HIGH
        )
    }
}
