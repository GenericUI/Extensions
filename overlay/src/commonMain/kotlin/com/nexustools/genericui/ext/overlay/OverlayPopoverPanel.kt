package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Generic anchored popover panel (used on System + Web) that pins [AutoHideController] (`"popover"`)
 * while open and binds audio tracks (`state.audioTracks`), subtitle tracks (`state.subtitleTracks`),
 * playback speeds (`state.availableSpeeds`), plus any custom sections or items registered by the
 * consumer application.
 */
open class OverlayPopoverPanel(
    override val elementId: String = "overlay-popover-panel",
    override val slot: OverlaySlot = OverlaySlot.POPOVER,
    var autoHideController: AutoHideController? = null
) : Container("genericui-overlay-popover-panel"), OverlayElement {

    private var disposedState: Boolean = false
    private var currentState: OverlayState = OverlayState()
    private var currentActions: OverlayActions = OverlayActions()
    private val customSections = linkedMapOf<String, Pair<String, AbstractComponent>>()

    override val component: AbstractComponent
        get() = this

    override val isDisposed: Boolean
        get() = disposedState

    var isOpen: Boolean = false
        private set

    val audioTracksSection: VStack = VStack(gap = SpacingScale.S1).apply {
        addClass("genericui-overlay-popover-audio")
    }

    val subtitleTracksSection: VStack = VStack(gap = SpacingScale.S1).apply {
        addClass("genericui-overlay-popover-subtitles")
    }

    val speedSection: VStack = VStack(gap = SpacingScale.S1).apply {
        addClass("genericui-overlay-popover-speed")
    }

    val customSectionsContainer: VStack = VStack(gap = SpacingScale.S2).apply {
        addClass("genericui-overlay-popover-custom")
    }

    val registeredSectionIds: Set<String>
        get() = customSections.keys.toSet()

    init {
        htmlTag = "genericui:overlay-popover-panel"
        customBackgroundColor = RgbaColor(20, 20, 24, 242)
        borderRadiusPx = 8f
        setPadding(EdgeInsets.all(12))
        addChild(audioTracksSection)
        addChild(subtitleTracksSection)
        addChild(speedSection)
        addChild(customSectionsContainer)
        isVisible = false
    }

    fun open() {
        if (disposedState) return
        isOpen = true
        isVisible = true
        autoHideController?.setPinned(PIN_REASON_POPOVER, true)
        rebuildSections()
    }

    fun close() {
        if (disposedState) return
        isOpen = false
        isVisible = false
        autoHideController?.setPinned(PIN_REASON_POPOVER, false)
    }

    fun toggle() {
        if (isOpen) close() else open()
    }

    fun registerCustomSection(sectionId: String, title: String, content: AbstractComponent) {
        val key = sectionId.trim()
        require(key.isNotEmpty()) { "Custom section ID must not be blank." }
        customSections[key] = title to content
        rebuildCustomSections()
    }

    fun removeCustomSection(sectionId: String): Boolean {
        val removed = customSections.remove(sectionId.trim()) != null
        if (removed) {
            rebuildCustomSections()
        }
        return removed
    }

    fun clearCustomSections() {
        customSections.clear()
        rebuildCustomSections()
    }

    private fun rebuildSections() {
        rebuildAudioTracks()
        rebuildSubtitleTracks()
        rebuildSpeeds()
        rebuildCustomSections()
    }

    private fun rebuildAudioTracks() {
        audioTracksSection.clearChildren()
        if (currentState.audioTracks.isEmpty()) {
            audioTracksSection.isVisible = false
            return
        }
        audioTracksSection.isVisible = true
        audioTracksSection.addChild(createSectionHeader("Audio Track"))
        for (track in currentState.audioTracks) {
            val btn = Button(
                text = if (track.selected) "✓ ${track.label}" else track.label,
                type = if (track.selected) BootstrapType.PRIMARY else BootstrapType.SECONDARY,
                size = BootstrapSize.SM
            ).apply {
                onClick = {
                    autoHideController?.notifyUserActivity()
                    currentActions.onSelectAudioTrack?.invoke(track.id)
                }
            }
            audioTracksSection.addChild(btn)
        }
    }

    private fun rebuildSubtitleTracks() {
        subtitleTracksSection.clearChildren()
        if (currentState.subtitleTracks.isEmpty() && currentActions.onSelectSubtitleTrack == null) {
            subtitleTracksSection.isVisible = false
            return
        }
        subtitleTracksSection.isVisible = true
        subtitleTracksSection.addChild(createSectionHeader("Subtitles"))
        val noneSelected = currentState.subtitleTracks.none { it.selected }
        val offButton = Button(
            text = if (noneSelected) "✓ Off" else "Off",
            type = if (noneSelected) BootstrapType.PRIMARY else BootstrapType.SECONDARY,
            size = BootstrapSize.SM
        ).apply {
            onClick = {
                autoHideController?.notifyUserActivity()
                currentActions.onSelectSubtitleTrack?.invoke(null)
            }
        }
        subtitleTracksSection.addChild(offButton)

        for (track in currentState.subtitleTracks) {
            val btn = Button(
                text = if (track.selected) "✓ ${track.label}" else track.label,
                type = if (track.selected) BootstrapType.PRIMARY else BootstrapType.SECONDARY,
                size = BootstrapSize.SM
            ).apply {
                onClick = {
                    autoHideController?.notifyUserActivity()
                    currentActions.onSelectSubtitleTrack?.invoke(track.id)
                }
            }
            subtitleTracksSection.addChild(btn)
        }
    }

    private fun rebuildSpeeds() {
        speedSection.clearChildren()
        if (currentState.availableSpeeds.isEmpty()) {
            speedSection.isVisible = false
            return
        }
        speedSection.isVisible = true
        speedSection.addChild(createSectionHeader("Playback Speed"))
        val row = HStack(gap = SpacingScale.S1)
        for (speed in currentState.availableSpeeds) {
            val isCurrent = kotlin.math.abs(speed - currentState.playbackSpeed) < 0.001
            val btn = Button(
                text = "${speed}x",
                type = if (isCurrent) BootstrapType.PRIMARY else BootstrapType.SECONDARY,
                size = BootstrapSize.SM
            ).apply {
                onClick = {
                    autoHideController?.notifyUserActivity()
                    currentActions.onSpeedChange?.invoke(speed)
                }
            }
            row.addChild(btn)
        }
        speedSection.addChild(row)
    }

    private fun rebuildCustomSections() {
        customSectionsContainer.clearChildren()
        if (customSections.isEmpty()) {
            customSectionsContainer.isVisible = false
            return
        }
        customSectionsContainer.isVisible = true
        for ((_, pair) in customSections) {
            val (title, content) = pair
            val wrapper = VStack(gap = SpacingScale.S1)
            if (title.isNotBlank()) {
                wrapper.addChild(createSectionHeader(title))
            }
            wrapper.addChild(content)
            customSectionsContainer.addChild(wrapper)
        }
    }

    private fun createSectionHeader(title: String): Label =
        Label(title, "span", "genericui-overlay-popover-header").apply {
            fontWeight = FontWeight.BOLD
            textColor = RgbaColor.WHITE
            setStyleProperty("font-size", "12px")
        }

    override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
        currentState = state
        currentActions = actions
        rebuildSections()
        for ((_, pair) in customSections) {
            (pair.second as? OverlayElement)?.onStateChanged(state, actions)
        }
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        if (isOpen) {
            isOpen = false
            autoHideController?.setPinned(PIN_REASON_POPOVER, false)
        }
        customSections.clear()
        super<Container>.dispose()
    }

    companion object {
        const val PIN_REASON_POPOVER: String = "popover"
    }
}
