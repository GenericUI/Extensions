package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeImpl

/**
 * Pure, background-free layered overlay container.
 *
 * Extends [LayeredContainer] so child components and [OverlayElement] instances can be
 * mounted as either full-overlay layers ([LayerConstraint.fill]) or positioned floating
 * layers ([LayerConstraint.float]) without imposing any background color, gradient scrim,
 * curtain, or pre-baked transport chrome.
 */
abstract class AbstractOverlay(
    tagName: String = "genericui:overlay",
    classPrefix: String = "gui-overlay",
    autoHideSpec: AutoHideSpec = AutoHideSpec()
) : LayeredContainer(
    tagName = if (tagName.contains(':')) tagName else "genericui:overlay",
    classPrefix = classPrefix
) {

    constructor(
        overlayId: String,
        autoHideSpec: AutoHideSpec
    ) : this(
        tagName = overlayId,
        classPrefix = "gui-overlay",
        autoHideSpec = autoHideSpec
    )

    val overlayId: String = tagName

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

    var targetWidthPx: Int = 0
        private set

    var targetHeightPx: Int = 0
        private set

    var lastFrameTimestampMs: Long = 0L
        private set

    var onTargetFrameRendered: ((frameTimestampMs: Long, widthPx: Int, heightPx: Int) -> Unit)? = null
    var onTargetResized: ((widthPx: Int, heightPx: Int) -> Unit)? = null
    var onBeforeTargetFramePresentCallback: ((frameIndex: Long, timestampMs: Double) -> Unit)? = null

    init {
        setStyleProperty("width", "100%")
        setStyleProperty("height", "100%")
        setAttribute("data-overlay-id", overlayId)
        setAttribute("data-overlay-layer", "true")
        setAttribute("data-overlay-root", "true")
        setStyleProperty("background", "transparent")
        setStyleProperty("background-color", "transparent")
        inputRouter.bindToComponent(
            component = this,
            stateProvider = { state },
            actionsProvider = { actions }
        )
    }

    fun addElement(
        element: OverlayElement,
        constraint: LayerConstraint = element.constraint
    ): OverlayElement {
        val existing = mountedElements.firstOrNull { it.elementId == element.elementId && it !== element }
        if (existing != null) {
            removeElement(existing)
        }
        element.constraint = constraint
        if (!mountedElements.contains(element)) {
            mountedElements.add(element)
        }
        addLayer(element.component, constraint)
        element.onStateChanged(state, actions)
        if (targetWidthPx > 0 && targetHeightPx > 0) {
            element.onTargetResize(targetWidthPx, targetHeightPx)
        }
        return element
    }

    fun addElement(
        component: AbstractComponent,
        constraint: LayerConstraint = LayerConstraint.fill()
    ): OverlayElement {
        val existing = mountedElements.firstOrNull { it.component === component }
        if (existing != null) {
            existing.constraint = constraint
            setLayerConstraint(component, constraint)
            return existing
        }
        val element = OverlayElement(
            component = component,
            constraint = constraint,
            elementId = component.id ?: "overlay-element-${mountedElements.size}"
        )
        return addElement(element, constraint)
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
        return addElement(created, created.constraint)
    }

    fun removeElement(element: OverlayElement) {
        mountedElements.remove(element)
        removeLayer(element.component)
        element.dispose()
    }

    fun removeElement(component: AbstractComponent) {
        val matching = mountedElements.filter { it.component === component }
        for (element in matching) {
            mountedElements.remove(element)
            element.dispose()
        }
        removeLayer(component)
    }

    fun removeElement(elementId: String): Boolean {
        val index = mountedElements.indexOfFirst { it.elementId == elementId }
        if (index < 0) return false
        val removed = mountedElements.removeAt(index)
        removeLayer(removed.component)
        removed.dispose()
        return true
    }

    fun getElement(elementId: String): OverlayElement? =
        mountedElements.firstOrNull { it.elementId == elementId }

    fun clearElements() {
        val snapshot = mountedElements.toList()
        mountedElements.clear()
        clearLayers()
        for (element in snapshot) {
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

    open fun setPictureInPictureMode(enabled: Boolean) {
        autoHideController.setPictureInPictureMode(enabled)
        if (state.isInPictureInPictureMode != enabled) {
            state = state.copy(isInPictureInPictureMode = enabled)
            notifyElementsAndSubclass()
        }
    }

    open fun onBeforeTargetFramePresent(frameIndex: Long, timestampMs: Double) {
        onBeforeTargetFramePresentCallback?.invoke(frameIndex, timestampMs)
    }

    open fun notifyTargetRenderFrame(
        frameTimestampMs: Long = 0L,
        widthPx: Int = targetWidthPx,
        heightPx: Int = targetHeightPx
    ) {
        lastFrameTimestampMs = frameTimestampMs
        if (widthPx > 0 && heightPx > 0 && (widthPx != targetWidthPx || heightPx != targetHeightPx)) {
            notifyTargetResize(widthPx, heightPx)
        }
        onTargetFrameRendered?.invoke(frameTimestampMs, targetWidthPx, targetHeightPx)
        for (element in mountedElements.toList()) {
            element.onTargetRenderFrame(frameTimestampMs, targetWidthPx, targetHeightPx)
        }
    }

    open fun notifyTargetResize(widthPx: Int, heightPx: Int) {
        if (widthPx < 0 || heightPx < 0) return
        targetWidthPx = widthPx
        targetHeightPx = heightPx
        if (widthPx > 0 && heightPx > 0) {
            this.widthPx = widthPx
            this.heightPx = heightPx
        }
        onTargetResized?.invoke(widthPx, heightPx)
        for (element in mountedElements.toList()) {
            element.onTargetResize(widthPx, heightPx)
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

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        impl.setOnBeforeTargetFramePresent { sc, ts ->
            onBeforeTargetFramePresent(sc, ts ?: 0.0)
        }
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
        super.dispose()
    }
}
