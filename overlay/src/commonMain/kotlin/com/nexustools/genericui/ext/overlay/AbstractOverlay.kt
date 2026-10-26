package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container

/**
 * Base overlay class managing [OverlaySlot] containers, [OverlayState], [OverlayActions],
 * [AutoHideController], [OverlayInputRouter], and Picture-in-Picture mode suppression.
 */
abstract class AbstractOverlay(
    val overlayId: String = "overlay",
    autoHideSpec: AutoHideSpec = AutoHideSpec()
) : Container("genericui-overlay") {

    val topBarSlotContainer: Container = Container("genericui-overlay-slot-top-bar")
    val centerSlotContainer: Container = Container("genericui-overlay-slot-center")
    val subtitleCueSlotContainer: Container = Container("genericui-overlay-slot-subtitle-cue")
    val bottomSeekSlotContainer: Container = Container("genericui-overlay-slot-bottom-seek")
    val bottomBarSlotContainer: Container = Container("genericui-overlay-slot-bottom-bar")
    val bottomStatusSlotContainer: Container = Container("genericui-overlay-slot-bottom-status")
    val popoverSlotContainer: Container = Container("genericui-overlay-slot-popover")
    val curtainSlotContainer: Container = Container("genericui-overlay-slot-curtain")
    val belowHostSlotContainer: Container = Container("genericui-overlay-slot-below-host")

    private val slotContainers: Map<OverlaySlot, Container> = mapOf(
        OverlaySlot.TOP_BAR to topBarSlotContainer,
        OverlaySlot.CENTER to centerSlotContainer,
        OverlaySlot.SUBTITLE_CUE to subtitleCueSlotContainer,
        OverlaySlot.BOTTOM_SEEK to bottomSeekSlotContainer,
        OverlaySlot.BOTTOM_BAR to bottomBarSlotContainer,
        OverlaySlot.BOTTOM_STATUS to bottomStatusSlotContainer,
        OverlaySlot.POPOVER to popoverSlotContainer,
        OverlaySlot.CURTAIN to curtainSlotContainer,
        OverlaySlot.BELOW_HOST to belowHostSlotContainer
    )

    private val mountedElements = mutableListOf<OverlayElement>()

    var host: OverlayHost? = null
        internal set

    var state: OverlayState = OverlayState()
        private set

    var actions: OverlayActions = OverlayActions()
        private set

    var themeColorHex: String? = null
        set(value) {
            field = value
            notifyElementsAndSubclass()
        }

    val effectiveThemeColorHex: String
        get() = themeColorHex?.takeIf { it.isNotBlank() }
            ?: host?.themeColorHex?.takeIf { it.isNotBlank() }
            ?: OverlayExtension.effectiveDefaultThemeColorHex()

    val autoHideController: AutoHideController = AutoHideController(
        spec = autoHideSpec,
        targetComponent = this
    )

    val inputRouter: OverlayInputRouter = OverlayInputRouter(
        autoHideController = autoHideController
    )

    val isInPictureInPictureMode: Boolean
        get() = autoHideController.isInPictureInPictureMode

    val elements: List<OverlayElement>
        get() = mountedElements.toList()

    init {
        htmlTag = "genericui:overlay"
        addChild(topBarSlotContainer)
        addChild(centerSlotContainer)
        addChild(subtitleCueSlotContainer)
        addChild(bottomSeekSlotContainer)
        addChild(bottomBarSlotContainer)
        addChild(bottomStatusSlotContainer)
        addChild(popoverSlotContainer)
        addChild(curtainSlotContainer)
        inputRouter.bindToComponent(
            component = this,
            stateProvider = { state },
            actionsProvider = { actions }
        )
    }

    fun getSlotContainer(slot: OverlaySlot): Container =
        slotContainers.getValue(slot)

    fun addElement(element: OverlayElement) {
        removeElement(element.elementId)
        mountedElements.add(element)
        getSlotContainer(element.slot).addChild(element.component)
        element.onStateChanged(state, actions)
    }

    fun addElement(factory: OverlayElementFactory): OverlayElement {
        val currentHost = host ?: OverlayHostBox(initialOverlay = null)
        val created = factory.create(
            OverlayContext(
                host = currentHost,
                state = state,
                actions = actions,
                themeColorHex = effectiveThemeColorHex
            )
        )
        addElement(created)
        return created
    }

    fun removeElement(elementId: String): Boolean {
        val index = mountedElements.indexOfFirst { it.elementId == elementId }
        if (index < 0) return false
        val removed = mountedElements.removeAt(index)
        getSlotContainer(removed.slot).removeChild(removed.component)
        removed.dispose()
        return true
    }

    fun getElement(elementId: String): OverlayElement? =
        mountedElements.firstOrNull { it.elementId == elementId }

    fun getElementsInSlot(slot: OverlaySlot): List<OverlayElement> =
        mountedElements.filter { it.slot == slot }

    fun clearSlot(slot: OverlaySlot) {
        val toRemove = mountedElements.filter { it.slot == slot }
        for (element in toRemove) {
            mountedElements.remove(element)
            getSlotContainer(slot).removeChild(element.component)
            element.dispose()
        }
    }

    fun clearElements() {
        val snapshot = mountedElements.toList()
        mountedElements.clear()
        for (element in snapshot) {
            getSlotContainer(element.slot).removeChild(element.component)
            element.dispose()
        }
    }

    open fun updateState(newState: OverlayState) {
        val pipChanged = newState.isInPictureInPictureMode != state.isInPictureInPictureMode
        state = newState
        if (pipChanged) {
            autoHideController.setPictureInPictureMode(newState.isInPictureInPictureMode)
        }
        notifyElementsAndSubclass()
    }

    open fun updateState(transform: (OverlayState) -> OverlayState) {
        updateState(transform(state))
    }

    open fun updateActions(newActions: OverlayActions) {
        actions = newActions
        notifyElementsAndSubclass()
    }

    open fun updateActions(transform: (OverlayActions) -> OverlayActions) {
        updateActions(transform(actions))
    }

    /**
     * Enables or disables Picture-in-Picture mode on this overlay, immediately hiding and disabling
     * the overlay while in PiP mode.
     */
    open fun setPictureInPictureMode(enabled: Boolean) {
        autoHideController.setPictureInPictureMode(enabled)
        if (state.isInPictureInPictureMode != enabled) {
            state = state.copy(isInPictureInPictureMode = enabled)
            notifyElementsAndSubclass()
        }
    }

    internal open fun onAttachToHost(targetHost: OverlayHost) {
        host = targetHost
        state = targetHost.state
        actions = targetHost.actions
        if (targetHost.isInPictureInPictureMode) {
            setPictureInPictureMode(true)
        }
        notifyElementsAndSubclass()
    }

    internal open fun onDetachFromHost() {
        host = null
    }

    protected open fun onStateUpdated(state: OverlayState, actions: OverlayActions) {}

    private fun notifyElementsAndSubclass() {
        for (element in mountedElements.toList()) {
            element.onStateChanged(state, actions)
        }
        onStateUpdated(state, actions)
    }

    override fun dispose() {
        inputRouter.dispose()
        autoHideController.dispose()
        clearElements()
        belowHostSlotContainer.dispose()
        super.dispose()
    }
}
