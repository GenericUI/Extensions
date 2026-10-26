package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Generic modal dialog helper (used on Mobile) that pins [AutoHideController] (`"dialog"`) while
 * open and renders audio tracks, subtitle tracks, playback speeds, or custom consumer-supplied
 * tabs and sections.
 */
open class OverlayModalDialog(
    override val elementId: String = "overlay-modal-dialog",
    override val slot: OverlaySlot = OverlaySlot.POPOVER,
    titleText: String = "Playback Options",
    var autoHideController: AutoHideController? = null
) : Container("genericui-overlay-modal-dialog"), OverlayElement {

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

    var activeSectionId: String? = null
        private set

    val headerBar: HStack = HStack(gap = SpacingScale.S2).apply {
        setAlignItems(AlignItems.CENTER)
        setJustifyContent(JustifyContent.BETWEEN)
    }

    val titleLabel: Label = Label(titleText, "span", "genericui-overlay-modal-title").apply {
        fontWeight = FontWeight.BOLD
        textColor = RgbaColor.WHITE
        setStyleProperty("font-size", "16px")
    }

    val closeButton: Button = Button(
        text = "✕",
        type = BootstrapType.SECONDARY,
        size = BootstrapSize.SM
    ).apply {
        onClick = { close() }
    }

    val audioTracksSection: VStack = VStack(gap = SpacingScale.S1)
    val subtitleTracksSection: VStack = VStack(gap = SpacingScale.S1)
    val speedSection: VStack = VStack(gap = SpacingScale.S1)
    val customSectionsContainer: VStack = VStack(gap = SpacingScale.S2)

    val registeredSectionIds: Set<String>
        get() = customSections.keys.toSet()

    init {
        htmlTag = "genericui:overlay-modal-dialog"
        customBackgroundColor = RgbaColor(18, 18, 22, 248)
        borderRadiusPx = 12f
        setPadding(EdgeInsets.all(16))

        headerBar.addChild(titleLabel)
        headerBar.addChild(closeButton)

        addChild(headerBar)
        addChild(audioTracksSection)
        addChild(subtitleTracksSection)
        addChild(speedSection)
        addChild(customSectionsContainer)
        isVisible = false
    }

    fun open(sectionId: String? = null) {
        if (disposedState) return
        activeSectionId = sectionId
        isOpen = true
        isVisible = true
        autoHideController?.setPinned(PIN_REASON_DIALOG, true)
        rebuildContent()
    }

    fun close() {
        if (disposedState) return
        isOpen = false
        isVisible = false
        autoHideController?.setPinned(PIN_REASON_DIALOG, false)
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

    private fun rebuildContent() {
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
        audioTracksSection.addChild(createSectionTitle("Audio Track"))
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
        subtitleTracksSection.addChild(createSectionTitle("Subtitles"))
        val noneSelected = currentState.subtitleTracks.none { it.selected }
        subtitleTracksSection.addChild(
            Button(
                text = if (noneSelected) "✓ Off" else "Off",
                type = if (noneSelected) BootstrapType.PRIMARY else BootstrapType.SECONDARY,
                size = BootstrapSize.SM
            ).apply {
                onClick = {
                    autoHideController?.notifyUserActivity()
                    currentActions.onSelectSubtitleTrack?.invoke(null)
                }
            }
        )
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
        speedSection.addChild(createSectionTitle("Playback Speed"))
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
        for ((sectionId, pair) in customSections) {
            if (activeSectionId != null && activeSectionId != sectionId) continue
            val (title, content) = pair
            val wrapper = VStack(gap = SpacingScale.S1)
            if (title.isNotBlank()) {
                wrapper.addChild(createSectionTitle(title))
            }
            wrapper.addChild(content)
            customSectionsContainer.addChild(wrapper)
        }
    }

    private fun createSectionTitle(title: String): Label =
        Label(title, "span", "genericui-overlay-modal-section-title").apply {
            fontWeight = FontWeight.BOLD
            textColor = RgbaColor.WHITE
            setStyleProperty("font-size", "13px")
        }

    override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
        currentState = state
        currentActions = actions
        rebuildContent()
        for ((_, pair) in customSections) {
            (pair.second as? OverlayElement)?.onStateChanged(state, actions)
        }
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        if (isOpen) {
            isOpen = false
            autoHideController?.setPinned(PIN_REASON_DIALOG, false)
        }
        customSections.clear()
        super<Container>.dispose()
    }

    companion object {
        const val PIN_REASON_DIALOG: String = "dialog"
    }
}
