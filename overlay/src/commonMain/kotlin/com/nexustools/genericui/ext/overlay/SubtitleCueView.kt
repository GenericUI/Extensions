package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Generic subtitle/caption cue box (`18sp` bold white text on `#CC000000` rounded background)
 * visible whenever `state.subtitleCueText.isNotEmpty()`.
 */
open class SubtitleCueView(
    override val elementId: String = "subtitle-cue-view",
    override val slot: OverlaySlot = OverlaySlot.SUBTITLE_CUE
) : Container("genericui-overlay-subtitle-cue"), OverlayElement {

    private var disposedState: Boolean = false

    override val component: AbstractComponent
        get() = this

    override val isDisposed: Boolean
        get() = disposedState

    val cueLabel: Label = Label("", "span", "genericui-overlay-subtitle-cue-text").apply {
        fontWeight = FontWeight.BOLD
        textAlignment = TextAlignment.CENTER
        textColor = RgbaColor.WHITE
        setStyleProperty("font-size", "18px")
    }

    init {
        htmlTag = "genericui:overlay-subtitle-cue"
        setDisplay(DisplayMode.FLEX)
        setAlignItems(AlignItems.CENTER)
        setJustifyContent(JustifyContent.CENTER)
        customBackgroundColor = RgbaColor(0, 0, 0, 0xCC)
        borderRadiusPx = 6f
        setPadding(EdgeInsets.symmetric(vertical = 6, horizontal = 12))
        addChild(cueLabel)
        isVisible = false
    }

    override fun onStateChanged(state: OverlayState, actions: OverlayActions) {
        cueLabel.text = state.subtitleCueText
        isVisible = state.subtitleCueText.isNotEmpty()
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        super<Container>.dispose()
    }
}
