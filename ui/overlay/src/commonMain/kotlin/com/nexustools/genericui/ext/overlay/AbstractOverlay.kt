package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.WindowConfiguration

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
    private val persistentElementsList = mutableListOf<OverlayElement>()

    private var observedWindowFullscreenComponent: AbstractComponent? = null
    private var previousWindowFullscreenListener: ((Boolean) -> Unit)? = null
    private var lastObservedWindowFullscreen: Boolean? = null
    private var isHandlingWindowFullscreenCallback: Boolean = false
    private val observedWindowFullscreenForwarder: (Boolean) -> Unit = { fs ->
        previousWindowFullscreenListener?.invoke(fs)
        handleAttachedWindowFullscreenChanged(fs)
    }

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

    val windowTitleBar: OverlayWindowTitleBar = OverlayWindowTitleBar(
        config = OverlayWindowTitleBarConfig(enabled = false)
    )

    internal var explicitWindowTitleBarConfig: Boolean = false
    internal var explicitWindowDragConfig: Boolean = false
    private var explicitWindowDragHandler: Boolean = false

    private var windowDragEnabledBacking: Boolean = false
    var windowDragEnabled: Boolean
        get() = windowDragEnabledBacking
        set(value) {
            explicitWindowDragConfig = true
            windowDragEnabledBacking = value
        }

    private var onWindowDragMoveBacking: (() -> Unit)? = null
    var onWindowDragMove: (() -> Unit)?
        get() = onWindowDragMoveBacking
        set(value) {
            explicitWindowDragHandler = true
            onWindowDragMoveBacking = value
        }

    var windowTitleBarConfig: OverlayWindowTitleBarConfig
        get() = windowTitleBar.config
        set(value) {
            explicitWindowTitleBarConfig = true
            applyWindowTitleBarConfig(value)
        }

    var windowTitleBarEnabled: Boolean
        get() = windowTitleBar.config.enabled
        set(value) {
            explicitWindowTitleBarConfig = true
            if (windowTitleBar.config.enabled != value) {
                applyWindowTitleBarConfig(windowTitleBar.config.copy(enabled = value))
            } else {
                syncWindowTitleBar()
            }
        }

    val isInPictureInPictureMode: Boolean
        get() = autoHideController.isInPictureInPictureMode

    val elements: List<OverlayElement>
        get() = mountedElements.toList()

    val persistentElements: List<OverlayElement>
        get() = persistentElementsList.toList()

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
        OverlayExtension.ensureStyleSheetRegistered()
        addClass("gui-overlay")
        setAttribute("data-overlay-id", overlayId)
        setAttribute("data-overlay-layer", "true")
        setAttribute("data-overlay-root", "true")
        setAttribute("hudVisible", "true")
        setAttribute("hudPointerLeftHidden", "false")
        setAttribute("stageActivitySeq", "1")
        inputRouter.windowDragEnabledProvider = { canDragHostingWindow() }
        inputRouter.onWindowDragMove = { triggerWindowDragMove() }
        inputRouter.bindToComponent(
            component = this,
            stateProvider = { state },
            actionsProvider = { actions }
        )
        windowTitleBar.inputRouter = inputRouter
        inputRouter.isolateInteractiveComponent(windowTitleBar)
        inputRouter.isolateInteractiveComponent(windowTitleBar.closeButton)
        inputRouter.isolateInteractiveComponent(windowTitleBar.minimizeButton)
        inputRouter.isolateInteractiveComponent(windowTitleBar.maximizeButton)
        windowTitleBar.onVisibilityOrHeightChanged = { _, _ ->
            syncTopBarWindowTitleBarOffset()
        }
        autoHideController.visibilityObservers.add { visible, opacity ->
            onHudVisibilityChanged(visible, opacity)
        }
    }

    override fun addChild(child: AbstractComponent, index: Int) {
        super.addChild(child, index)
        if (child !== windowTitleBar) {
            syncFullscreenButtons(state.isFullscreen)
            if (windowTitleBarEnabled) {
                syncTopBarWindowTitleBarOffset()
            }
        }
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
        syncFullscreenButtons(state.isFullscreen)
        if (targetWidthPx > 0 && targetHeightPx > 0) {
            element.onTargetResize(targetWidthPx, targetHeightPx)
        }
        if (windowTitleBarEnabled) {
            syncTopBarWindowTitleBarOffset()
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
            ?: persistentElementsList.firstOrNull { it.elementId == elementId }

    fun getEffectiveLayerConstraint(component: AbstractComponent): LayerConstraint? =
        host?.persistentOverlayRoot?.getLayerConstraint(component) ?: getLayerConstraint(component)

    /**
     * Registers a persistent overlay element (such as captions or loading indicators) that
     * should remain mounted in `host.persistentOverlayRoot` at `opacity = 1` outside the
     * fading overlay root. When unhosted, mounts as a fallback layer on this overlay until attached.
     */
    fun addPersistentElement(
        element: OverlayElement,
        constraint: LayerConstraint = element.constraint
    ): OverlayElement {
        val existing = persistentElementsList.firstOrNull { it.elementId == element.elementId && it !== element }
        if (existing != null) {
            removePersistentElement(existing)
        }
        element.constraint = constraint
        if (!persistentElementsList.contains(element)) {
            persistentElementsList.add(element)
        }
        val persistentRoot = host?.persistentOverlayRoot
        if (persistentRoot != null) {
            if (layers.contains(element.component)) {
                removeLayer(element.component)
            }
            persistentRoot.addLayer(element.component, constraint)
        } else {
            addLayer(element.component, constraint)
        }
        element.onStateChanged(state, actions)
        syncFullscreenButtons(state.isFullscreen)
        if (targetWidthPx > 0 && targetHeightPx > 0) {
            element.onTargetResize(targetWidthPx, targetHeightPx)
        }
        return element
    }

    fun removePersistentElement(element: OverlayElement) {
        persistentElementsList.remove(element)
        host?.persistentOverlayRoot?.removeLayer(element.component)
        removeLayer(element.component)
        element.dispose()
    }

    fun clearElements() {
        val snapshot = mountedElements.toList()
        mountedElements.clear()
        val persistentSnapshot = persistentElementsList.toList()
        persistentElementsList.clear()
        for (element in persistentSnapshot) {
            host?.persistentOverlayRoot?.removeLayer(element.component)
            element.dispose()
        }
        clearLayers()
        for (element in snapshot) {
            element.dispose()
        }
        if (windowTitleBarEnabled) {
            ensureWindowTitleBarLayerMounted()
            syncWindowTitleBar()
        }
    }

    open fun updateState(newState: OverlayState) {
        val pipChanged = newState.isInPictureInPictureMode != state.isInPictureInPictureMode
        state = newState
        if (pipChanged) {
            autoHideController.setPictureInPictureMode(newState.isInPictureInPictureMode)
        }
        syncFullscreenButtons(newState.isFullscreen)
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
        if (windowTitleBarEnabled) {
            windowTitleBar.syncIfWindowStateChanged()
        }
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
        syncWindowTitleBar()
        onTargetResized?.invoke(widthPx, heightPx)
        for (element in mountedElements.toList()) {
            element.onTargetResize(widthPx, heightPx)
        }
        for (element in persistentElementsList.toList()) {
            element.onTargetResize(widthPx, heightPx)
        }
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        syncWindowTitleBar()
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        syncWindowTitleBar()
    }

    internal open fun onAttachToHost(targetHost: OverlayHost) {
        host = targetHost
        val persistentRoot = targetHost.persistentOverlayRoot
        if (persistentRoot != null) {
            for (element in persistentElementsList.toList()) {
                if (layers.contains(element.component)) {
                    removeLayer(element.component)
                }
                persistentRoot.addLayer(element.component, element.constraint)
            }
        }
        state = targetHost.state
        actions = OverlayActions(
            onPlayPause = targetHost.actions.onPlayPause ?: actions.onPlayPause,
            onPlay = targetHost.actions.onPlay ?: actions.onPlay,
            onPause = targetHost.actions.onPause ?: actions.onPause,
            onSeekToSec = targetHost.actions.onSeekToSec ?: actions.onSeekToSec,
            onSkipDeltaSec = targetHost.actions.onSkipDeltaSec ?: actions.onSkipDeltaSec,
            onNext = targetHost.actions.onNext ?: actions.onNext,
            onPrevious = targetHost.actions.onPrevious ?: actions.onPrevious,
            onVolumeChange = targetHost.actions.onVolumeChange ?: actions.onVolumeChange,
            onMuteToggle = targetHost.actions.onMuteToggle ?: actions.onMuteToggle,
            onSpeedChange = targetHost.actions.onSpeedChange ?: actions.onSpeedChange,
            onSelectAudioTrack = targetHost.actions.onSelectAudioTrack ?: actions.onSelectAudioTrack,
            onSelectSubtitleTrack = targetHost.actions.onSelectSubtitleTrack ?: actions.onSelectSubtitleTrack,
            onToggleFullscreen = targetHost.actions.onToggleFullscreen ?: actions.onToggleFullscreen,
            onBadgeClick = targetHost.actions.onBadgeClick ?: actions.onBadgeClick,
            onCustomAction = targetHost.actions.onCustomAction ?: actions.onCustomAction
        )
        if (targetHost.isInPictureInPictureMode) {
            setPictureInPictureMode(true)
        }
        val resolvedWin = resolveAttachedWindow()
        attachWindowFullscreenObserverIfNeeded(resolvedWin)
        val winFs = resolvedWin?.isFullscreen()
        if (winFs != null) {
            lastObservedWindowFullscreen = winFs
            if (winFs && !state.isFullscreen) {
                state = state.copy(isFullscreen = true)
            }
        }
        syncFullscreenButtons(state.isFullscreen)
        syncWindowTitleBar()
        notifyElementsAndSubclass()
    }

    internal open fun onDetachFromHost() {
        val prevPersistentRoot = host?.persistentOverlayRoot
        if (prevPersistentRoot != null) {
            for (element in persistentElementsList.toList()) {
                prevPersistentRoot.removeLayer(element.component)
                addLayer(element.component, element.constraint)
            }
        }
        detachWindowFullscreenObserver()
        lastObservedWindowFullscreen = null
        host = null
        syncWindowTitleBar()
    }

    internal fun applyWindowDragFromHost(enabled: Boolean, handler: (() -> Unit)?) {
        if (!explicitWindowDragConfig) {
            windowDragEnabledBacking = enabled
        }
        if (!explicitWindowDragHandler) {
            onWindowDragMoveBacking = handler
        }
    }

    fun resolveAttachedWindow(): IWindow? {
        windowTitleBar.resolveAttachedWindow()?.let { return it }
        var cur: AbstractComponent? = parent
        while (cur != null) {
            if (cur is IWindow) return cur
            if (cur is Fragment) {
                cur.hostingWindow?.let { return it }
                cur.attachedWindow?.let { return it }
            }
            if (cur is FragmentContainer) {
                cur.hostingIWindow?.let { return it }
            }
            cur = cur.parent
        }
        return null
    }

    open fun canDragHostingWindow(): Boolean {
        if (!windowDragEnabled || state.isFullscreen) return false
        val win = resolveAttachedWindow() ?: return true
        return !win.isFullscreen() && win.getWindowState() == WindowState.NORMAL
    }

    open fun triggerWindowDragMove() {
        if (!canDragHostingWindow()) return
        val customHandler = onWindowDragMove
        if (customHandler != null) {
            customHandler.invoke()
            return
        }
        val win = resolveAttachedWindow() ?: return
        if (win is Window && !win.isWindowShown) return
        win.startSystemMove()
    }

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        impl.setOnBeforeTargetFramePresent { sc, ts ->
            onBeforeTargetFramePresent(sc, ts ?: 0.0)
        }
        setAttribute("hudPointerLeftHidden", "false")
        if (!isInPictureInPictureMode && autoHideController.isOverlayVisible) {
            setAttribute("hudVisible", "true")
            autoHideController.targetComponent?.removeClass(AutoHideController.HIDDEN_CLASS)
        }
        syncWindowTitleBar()
    }

    protected override fun onAncestorHierarchyChanged() {
        super.onAncestorHierarchyChanged()
        syncWindowTitleBar()
    }

    internal fun applyWindowTitleBarConfigFromHost(value: OverlayWindowTitleBarConfig) {
        applyWindowTitleBarConfig(value)
    }

    private fun applyWindowTitleBarConfig(value: OverlayWindowTitleBarConfig) {
        windowTitleBar.config = value
        if (value.enabled) {
            ensureWindowTitleBarLayerMounted()
        } else {
            detachWindowTitleBarLayer()
        }
        syncWindowTitleBar()
    }

    protected open fun detachWindowTitleBarLayer() {
        if (layers.contains(windowTitleBar)) {
            removeLayer(windowTitleBar)
        }
    }

    protected open fun ensureWindowTitleBarLayerMounted() {
        val constraint = LayerConstraint.float(
            alignment = LayerAlignment.TOP_START,
            offsetXPx = 0,
            offsetYPx = 0,
            zIndex = WINDOW_TITLE_BAR_Z_INDEX,
            passThroughPointerEvents = false
        )
        if (!layers.contains(windowTitleBar)) {
            addLayer(windowTitleBar, constraint)
        } else if (getLayerConstraint(windowTitleBar) != constraint) {
            setLayerConstraint(windowTitleBar, constraint)
        }
    }

    fun syncWindowTitleBar(window: IWindow? = null) {
        val resolvedWin = window ?: resolveAttachedWindow()
        attachWindowFullscreenObserverIfNeeded(resolvedWin)
        val winFs = resolvedWin?.isFullscreen()
        if (winFs != null) {
            val prevWinFs = lastObservedWindowFullscreen
            lastObservedWindowFullscreen = winFs
            if ((prevWinFs == null && winFs && !state.isFullscreen) ||
                (prevWinFs != null && prevWinFs != winFs && state.isFullscreen != winFs)
            ) {
                state = state.copy(isFullscreen = winFs)
                for (element in mountedElements.toList()) {
                    element.onStateChanged(state, actions)
                }
                for (element in persistentElementsList.toList()) {
                    element.onStateChanged(state, actions)
                }
                onStateUpdated(state, actions)
            }
        }
        windowTitleBar.syncFromWindow(window)
        syncFullscreenButtons(state.isFullscreen)
        syncTopBarWindowTitleBarOffset()
    }

    fun syncFullscreenButtons(isFullscreen: Boolean) {
        syncFullscreenButtonsRecursively(this, isFullscreen)
        val persistentRoot = host?.persistentOverlayRoot
        if (persistentRoot != null) {
            for (element in persistentElementsList.toList()) {
                syncFullscreenButtonsRecursively(element.component, isFullscreen)
            }
        }
    }

    private fun syncFullscreenButtonsRecursively(node: AbstractComponent, isFullscreen: Boolean) {
        if (node is OverlayFullscreenButton && node.isFullscreen != isFullscreen) {
            node.isFullscreen = isFullscreen
        }
        if (node is AbstractContainer) {
            for (child in node.children) {
                syncFullscreenButtonsRecursively(child, isFullscreen)
            }
        }
    }

    private fun attachWindowFullscreenObserverIfNeeded(window: IWindow?) {
        val target = window as? AbstractComponent
        if (target === observedWindowFullscreenComponent) return
        detachWindowFullscreenObserver()
        if (target != null && !target.isDisposed) {
            previousWindowFullscreenListener = target.onFullscreenChanged
            target.onFullscreenChanged = observedWindowFullscreenForwarder
            observedWindowFullscreenComponent = target
        }
    }

    private fun detachWindowFullscreenObserver() {
        val prevTarget = observedWindowFullscreenComponent
        val prevListener = previousWindowFullscreenListener
        observedWindowFullscreenComponent = null
        previousWindowFullscreenListener = null
        if (prevTarget != null && prevTarget.onFullscreenChanged === observedWindowFullscreenForwarder) {
            prevTarget.onFullscreenChanged = prevListener
        }
    }

    private fun handleAttachedWindowFullscreenChanged(fs: Boolean) {
        if (isDisposed || isHandlingWindowFullscreenCallback) return
        isHandlingWindowFullscreenCallback = true
        try {
            lastObservedWindowFullscreen = fs
            if (state.isFullscreen != fs) {
                updateState(state.copy(isFullscreen = fs))
            } else {
                syncFullscreenButtons(fs)
                syncWindowTitleBar()
            }
        } finally {
            isHandlingWindowFullscreenCallback = false
        }
    }

    protected open fun onHudVisibilityChanged(visible: Boolean, opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        setAttribute("hudVisible", visible.toString())
        setAttribute("data-hud-opacity", clamped.toString())
    }

    protected open fun syncTopBarWindowTitleBarOffset() {
        val offsetPx = windowTitleBar.effectiveHeightPx
        applyTopBarOffsetRecursively(this, offsetPx)
    }

    private fun applyTopBarOffsetRecursively(node: AbstractComponent, offsetPx: Int) {
        if (node === windowTitleBar) return
        if (node !== this && node.getAttribute("data-overlay-role") == "top-bar") {
            val targetMargin = if (offsetPx > 0) CssLength.px(offsetPx) else CssLength.ZERO
            if (node.getStyle().marginTop != targetMargin) {
                node.getStyle().setMarginTop(targetMargin)
            }
            val offsetStr = offsetPx.toString()
            if (node.getAttribute("data-titlebar-offset-px") != offsetStr) {
                node.setAttribute("data-titlebar-offset-px", offsetStr)
            }
        }
        if (node is AbstractContainer) {
            for (child in node.children) {
                applyTopBarOffsetRecursively(child, offsetPx)
            }
        }
    }

    protected open fun onStateUpdated(state: OverlayState, actions: OverlayActions) {}

    private fun notifyElementsAndSubclass() {
        for (element in mountedElements.toList()) {
            element.onStateChanged(state, actions)
        }
        for (element in persistentElementsList.toList()) {
            element.onStateChanged(state, actions)
        }
        onStateUpdated(state, actions)
        syncWindowTitleBar()
    }

    override fun dispose() {
        inputRouter.dispose()
        autoHideController.dispose()
        clearElements()
        if (!windowTitleBar.isDisposed) {
            windowTitleBar.dispose()
        }
        detachWindowFullscreenObserver()
        lastObservedWindowFullscreen = null
        super.dispose()
    }

    companion object {
        const val WINDOW_TITLE_BAR_Z_INDEX: Int = 25
    }
}
