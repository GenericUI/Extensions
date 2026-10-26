package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.PositionMode

/**
 * Concrete [Container] implementing [OverlayHost] that wraps a target [content] component,
 * stacks [overlayRoot] directly on top of [content], and places [belowHostContainer] directly
 * below [content] for the [OverlaySlot.BELOW_HOST] region.
 */
open class OverlayHostBox(
    content: AbstractComponent? = null,
    initialOverlay: AbstractOverlay? = null
) : Container("genericui-overlay-host-box"), OverlayHost {

    val viewportContainer: Container = Container("genericui-overlay-host-viewport").apply {
        positionMode = PositionMode.RELATIVE
    }

    val contentContainer: Container = Container("genericui-overlay-host-content")

    override val overlayRoot: Container = Container("genericui-overlay-host-root").apply {
        positionMode = PositionMode.ABSOLUTE
        topOffset = "0px"
        leftOffset = "0px"
    }

    override val belowHostContainer: Container = Container("genericui-overlay-host-below")

    var contentComponent: AbstractComponent? = null
        private set

    override var activeOverlay: AbstractOverlay? = null

    private var stateBacking: OverlayState = OverlayState()
    override var state: OverlayState
        get() = stateBacking
        set(value) {
            stateBacking = value
            if (value.isInPictureInPictureMode != pipBacking) {
                pipBacking = value.isInPictureInPictureMode
                overlayRoot.isVisible = !pipBacking
            }
            activeOverlay?.updateState(value)
        }

    private var actionsBacking: OverlayActions = OverlayActions()
    override var actions: OverlayActions
        get() = actionsBacking
        set(value) {
            actionsBacking = value
            activeOverlay?.updateActions(value)
        }

    private var pipBacking: Boolean = false
    override var isInPictureInPictureMode: Boolean
        get() = pipBacking
        set(value) {
            setPictureInPictureMode(value)
        }

    private var themeColorHexBacking: String? = null
    override var themeColorHex: String?
        get() = themeColorHexBacking ?: OverlayExtension.effectiveDefaultThemeColorHex()
        set(value) {
            themeColorHexBacking = value
            activeOverlay?.themeColorHex = value
        }

    init {
        htmlTag = "genericui:overlay-host-box"
        flexDirection = FlexDirection.COLUMN
        viewportContainer.addChild(contentContainer)
        viewportContainer.addChild(overlayRoot)
        addChild(viewportContainer)
        addChild(belowHostContainer)
        if (content != null) {
            setContent(content)
        }
        if (initialOverlay != null) {
            attachOverlay(initialOverlay)
        }
    }

    fun setContent(component: AbstractComponent?) {
        contentComponent?.let { contentContainer.removeChild(it) }
        contentComponent = component
        if (component != null) {
            contentContainer.addChild(component)
        }
    }

    override fun attachOverlay(overlay: AbstractOverlay) {
        if (activeOverlay === overlay) return
        detachOverlay()
        activeOverlay = overlay
        if (themeColorHexBacking != null) {
            overlay.themeColorHex = themeColorHexBacking
        }
        overlayRoot.addChild(overlay)
        belowHostContainer.addChild(overlay.belowHostSlotContainer)
        overlay.onAttachToHost(this)
        if (pipBacking) {
            overlay.setPictureInPictureMode(true)
            overlayRoot.isVisible = false
        }
    }

    override fun detachOverlay() {
        val current = activeOverlay ?: return
        activeOverlay = null
        current.onDetachFromHost()
        overlayRoot.removeChild(current)
        belowHostContainer.removeChild(current.belowHostSlotContainer)
    }

    override fun updateState(transform: (OverlayState) -> OverlayState) {
        state = transform(state)
    }

    override fun setPictureInPictureMode(enabled: Boolean) {
        pipBacking = enabled
        stateBacking = stateBacking.copy(isInPictureInPictureMode = enabled)
        overlayRoot.isVisible = !enabled
        activeOverlay?.setPictureInPictureMode(enabled)
    }

    override fun dispose() {
        val current = activeOverlay
        detachOverlay()
        current?.dispose()
        super.dispose()
    }
}
