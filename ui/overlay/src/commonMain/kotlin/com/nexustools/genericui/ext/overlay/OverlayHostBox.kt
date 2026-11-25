package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.FramePresentTarget
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.window.IWindow

/**
 * Pure, background-free [LayeredContainer] implementing [OverlayHost].
 *
 * Mounts [targetComponent] as a full-bleed base layer (`zIndex = 0`), mounts the active fading
 * [overlay] inside [overlayRoot] (`zIndex = 10`), and mounts persistent non-fading items
 * (such as captions and buffering indicators) inside [persistentOverlayRoot] (`zIndex = 20`)
 * at `opacity = 1`.
 */
open class OverlayHostBox(
    content: AbstractComponent? = null,
    initialOverlay: AbstractOverlay? = null
) : LayeredContainer("genericui:overlay-host", "gui-overlay-host"), OverlayHost {

    private var fallbackTarget: Container? = if (content == null) {
        Container("genericui-overlay-host-target").apply {
            addClass("genericui-overlay-host-target")
        }
    } else {
        null
    }

    var contentComponent: AbstractComponent? = content
        private set

    override val targetComponent: AbstractComponent
        get() = contentComponent ?: fallbackTarget!!

    override val overlayRoot: LayeredContainer = LayeredContainer(
        tagName = "genericui:overlay-host-root",
        classPrefix = "gui-overlay-host-root"
    ).apply {
        addClass("gui-overlay-host-root")
    }

    override val persistentOverlayRoot: LayeredContainer = LayeredContainer(
        tagName = "genericui:overlay-persistent-root",
        classPrefix = "gui-overlay-persistent-root"
    ).apply {
        addClass("gui-overlay-persistent-root")
    }

    private var disposedState: Boolean = false

    override val isDisposed: Boolean
        get() = disposedState || super<LayeredContainer>.isDisposed

    private var overlayBacking: AbstractOverlay? = null
    private var defaultTitleBarOverlay: DefaultWindowTitleBarOverlay? = null
    private var windowTitleBarConfigBacking: OverlayWindowTitleBarConfig = OverlayWindowTitleBarConfig(enabled = false)
    private var explicitWindowTitleBarConfigOnHost: Boolean = false
    private var windowDragEnabledBacking: Boolean = false
    private var onWindowDragMoveBacking: (() -> Unit)? = null

    val windowTitleBar: OverlayWindowTitleBar
        get() = (overlayBacking?.windowTitleBar ?: ensureDefaultTitleBarOverlay().windowTitleBar).also {
            it.syncIfWindowStateChanged()
        }

    override var windowTitleBarConfig: OverlayWindowTitleBarConfig
        get() = overlayBacking?.windowTitleBarConfig
            ?: defaultTitleBarOverlay?.windowTitleBarConfig
            ?: windowTitleBarConfigBacking
        set(value) {
            explicitWindowTitleBarConfigOnHost = true
            windowTitleBarConfigBacking = value
            applyWindowTitleBarConfigToActiveOverlay()
        }

    override var windowTitleBarEnabled: Boolean
        get() = windowTitleBarConfig.enabled
        set(value) {
            explicitWindowTitleBarConfigOnHost = true
            windowTitleBarConfigBacking = windowTitleBarConfig.copy(enabled = value)
            applyWindowTitleBarConfigToActiveOverlay()
        }

    override var windowDragEnabled: Boolean
        get() = overlayBacking?.windowDragEnabled
            ?: defaultTitleBarOverlay?.windowDragEnabled
            ?: windowDragEnabledBacking
        set(value) {
            windowDragEnabledBacking = value
            applyWindowDragConfigToActiveOverlay()
        }

    override var onWindowDragMove: (() -> Unit)?
        get() = overlayBacking?.onWindowDragMove
            ?: defaultTitleBarOverlay?.onWindowDragMove
            ?: onWindowDragMoveBacking
        set(value) {
            onWindowDragMoveBacking = value
            applyWindowDragConfigToActiveOverlay()
        }

    override var overlay: AbstractOverlay?
        get() = overlayBacking
        set(value) {
            if (overlayBacking === value) return
            if (value == null) {
                detachOverlay()
            } else {
                attachOverlay(value)
            }
        }

    override var activeOverlay: AbstractOverlay?
        get() = overlay
        set(value) {
            overlay = value
        }

    private var stateBacking: OverlayState = OverlayState()
    override var state: OverlayState
        get() = stateBacking
        set(value) {
            stateBacking = value
            val pipChanged = value.isInPictureInPictureMode != pipBacking
            if (pipChanged) {
                pipBacking = value.isInPictureInPictureMode
                overlayRoot.isVisible = !pipBacking
                persistentOverlayRoot.isVisible = !pipBacking
            }
            overlay?.updateState(value)
            if (overlay == null) {
                defaultTitleBarOverlay?.updateState(value)
            }
            if (pipChanged) {
                syncCursorHiddenClassFromOverlay()
            }
        }

    private var actionsBacking: OverlayActions = OverlayActions()
    override var actions: OverlayActions
        get() = actionsBacking
        set(value) {
            actionsBacking = value
            overlay?.updateActions(value)
            if (overlay == null) {
                defaultTitleBarOverlay?.updateActions(value)
            }
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
            overlay?.themeColorHex = value
        }

    private var boundPresentTarget: FramePresentTarget? = null
    private val beforePresentListener: (Long, Double) -> Unit = { frameIndex, timestampMs ->
        dispatchBeforeTargetFramePresent(frameIndex, timestampMs)
    }
    private var observedAutoHideController: AutoHideController? = null
    private val autoHideVisibilityObserver: (Boolean, Float) -> Unit = { _, _ ->
        syncCursorHiddenClassFromOverlay()
    }

    init {
        OverlayExtension.ensureStyleSheetRegistered()
        addClass("gui-overlay-host")
        setAttribute("data-overlay-host", "true")
        overlayRoot.setAttribute("data-overlay-host-root", "true")
        persistentOverlayRoot.setAttribute("data-overlay-persistent-root", "true")
        addLayer(targetComponent, LayerConstraint.fill(zIndex = 0))
        addLayer(overlayRoot, LayerConstraint.fill(zIndex = 10, passThroughPointerEvents = true))
        addLayer(persistentOverlayRoot, LayerConstraint.fill(zIndex = 20, passThroughPointerEvents = true))
        val wakeActiveOverlay: (PointerEvent) -> Unit = { _ ->
            val target = overlayBacking ?: defaultTitleBarOverlay
            if (target != null && !pipBacking && !target.inputRouter.isTouchPrimaryMode && !target.autoHideController.isOverlayVisible) {
                target.autoHideController.notifyUserActivity()
            }
        }
        val prevMove = this.onPointerMove
        val prevDown = this.onPointerDown
        this.onPointerMove = { event ->
            wakeActiveOverlay(event)
            prevMove?.invoke(event)
        }
        this.onPointerDown = { event ->
            wakeActiveOverlay(event)
            prevDown?.invoke(event)
        }
        val initW = content?.widthPx ?: 0
        val initH = content?.heightPx ?: 0
        if (initW > 0 && initH > 0) {
            overlayRoot.widthPx = initW
            overlayRoot.heightPx = initH
            persistentOverlayRoot.widthPx = initW
            persistentOverlayRoot.heightPx = initH
        }
        if (content is FramePresentTarget) {
            bindToFramePresentTarget(content)
        }
        if (initialOverlay != null) {
            attachOverlay(initialOverlay)
        }
    }

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        impl.setOnBeforeTargetFramePresent { sc, ts ->
            dispatchBeforeTargetFramePresent(sc, ts ?: 0.0)
        }
        overlayRoot.ensureNativeImpl()?.setOnBeforeTargetFramePresent { sc, ts ->
            dispatchBeforeTargetFramePresent(sc, ts ?: 0.0)
        }
        syncWindowTitleBar()
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        syncWithTargetResize(viewportWidth, viewportHeight)
    }

    fun setContent(component: AbstractComponent?) {
        val currentTarget = targetComponent
        clearCursorHiddenClassesFromTarget(currentTarget)
        removeLayer(currentTarget)
        contentComponent = component
        if (component is FramePresentTarget) {
            bindToFramePresentTarget(component)
        } else {
            boundPresentTarget?.removeBeforeFramePresentListener(beforePresentListener)
            boundPresentTarget = null
        }
        if (component == null && fallbackTarget == null) {
            fallbackTarget = Container("genericui-overlay-host-target").apply {
                addClass("genericui-overlay-host-target")
            }
        }
        addLayer(targetComponent, LayerConstraint.fill(zIndex = 0))
        setLayerConstraint(
            overlayRoot,
            LayerConstraint.fill(
                zIndex = 10,
                passThroughPointerEvents = (overlay == null && !shouldMountDefaultTitleBarOverlay())
            )
        )
        syncCursorHiddenClassFromOverlay()
        val w = component?.widthPx ?: 0
        val h = component?.heightPx ?: 0
        if (w > 0 && h > 0) {
            syncWithTargetResize(w, h)
        }
    }

    override fun attachOverlay(overlay: AbstractOverlay) {
        val current = this.overlayBacking
        if (current === overlay) return
        if (current != null) {
            detachOverlayInternal(remountDefaultTitleBar = false)
        }
        unmountDefaultTitleBarOverlay()
        overlayBacking = overlay
        if (themeColorHexBacking != null) {
            overlay.themeColorHex = themeColorHexBacking
        }
        if (explicitWindowTitleBarConfigOnHost) {
            val targetConfig = if (overlay.explicitWindowTitleBarConfig) {
                overlay.windowTitleBarConfig.copy(enabled = windowTitleBarConfigBacking.enabled)
            } else {
                windowTitleBarConfigBacking
            }
            overlay.applyWindowTitleBarConfigFromHost(targetConfig)
        } else if (overlay.windowTitleBarEnabled) {
            windowTitleBarConfigBacking = overlay.windowTitleBarConfig
        }
        if (!overlay.explicitWindowDragConfig) {
            overlay.applyWindowDragFromHost(windowDragEnabledBacking, onWindowDragMoveBacking)
        }
        val targetW = overlayRoot.widthPx ?: contentComponent?.widthPx ?: widthPx ?: 0
        val targetH = overlayRoot.heightPx ?: contentComponent?.heightPx ?: heightPx ?: 0
        if (targetW > 0 && targetH > 0) {
            overlay.notifyTargetResize(targetW, targetH)
        }
        overlayRoot.addLayer(overlay, LayerConstraint.fill(zIndex = 10))
        setLayerConstraint(
            overlayRoot,
            LayerConstraint.fill(zIndex = 10, passThroughPointerEvents = false)
        )
        overlay.onAttachToHost(this)
        bindAutoHideObserver(overlay.autoHideController)
        if (pipBacking) {
            overlay.setPictureInPictureMode(true)
            overlayRoot.isVisible = false
        }
        syncCursorHiddenClassFromOverlay()
    }

    override fun detachOverlay() {
        detachOverlayInternal(remountDefaultTitleBar = true)
    }

    private fun detachOverlayInternal(remountDefaultTitleBar: Boolean) {
        val current = this.overlayBacking ?: return
        overlayBacking = null
        unbindAutoHideObserver()
        current.onDetachFromHost()
        overlayRoot.removeLayer(current)
        if (remountDefaultTitleBar && shouldMountDefaultTitleBarOverlay()) {
            mountDefaultTitleBarOverlayIfNeeded()
        } else {
            setLayerConstraint(
                overlayRoot,
                LayerConstraint.fill(zIndex = 10, passThroughPointerEvents = true)
            )
        }
        clearCursorHiddenClasses()
    }

    override fun syncWithTargetRender(frameTimestampMs: Long) {
        val targetW = overlayRoot.widthPx ?: contentComponent?.widthPx ?: widthPx ?: 0
        val targetH = overlayRoot.heightPx ?: contentComponent?.heightPx ?: heightPx ?: 0
        val target = overlay ?: defaultTitleBarOverlay
        target?.notifyTargetRenderFrame(frameTimestampMs, targetW, targetH)
    }

    override fun dispatchBeforeTargetFramePresent(frameIndex: Long, timestampMs: Double) {
        val target = overlay ?: defaultTitleBarOverlay
        target?.onBeforeTargetFramePresent(frameIndex, timestampMs)
        syncWithTargetRender(timestampMs.toLong().coerceAtLeast(0L))
    }

    override fun bindToFramePresentTarget(target: FramePresentTarget) {
        if (boundPresentTarget === target) return
        boundPresentTarget?.removeBeforeFramePresentListener(beforePresentListener)
        boundPresentTarget = target
        target.addBeforeFramePresentListener(beforePresentListener)
    }

    override fun syncWithTargetResize(widthPx: Int, heightPx: Int) {
        if (widthPx > 0 && heightPx > 0) {
            overlayRoot.widthPx = widthPx
            overlayRoot.heightPx = heightPx
            persistentOverlayRoot.widthPx = widthPx
            persistentOverlayRoot.heightPx = heightPx
        }
        overlay?.notifyTargetResize(widthPx, heightPx)
        if (overlay == null) {
            defaultTitleBarOverlay?.notifyTargetResize(widthPx, heightPx)
        }
    }

    fun syncWindowTitleBar(window: IWindow? = null) {
        (overlayBacking ?: defaultTitleBarOverlay)?.syncWindowTitleBar(window)
    }

    override fun updateState(transform: (OverlayState) -> OverlayState) {
        state = transform(state)
    }

    override fun setPictureInPictureMode(enabled: Boolean) {
        pipBacking = enabled
        stateBacking = stateBacking.copy(isInPictureInPictureMode = enabled)
        overlayRoot.isVisible = !enabled
        persistentOverlayRoot.isVisible = !enabled
        overlay?.setPictureInPictureMode(enabled)
        if (overlay == null) {
            defaultTitleBarOverlay?.setPictureInPictureMode(enabled)
        }
        syncCursorHiddenClassFromOverlay()
    }

    private fun shouldMountDefaultTitleBarOverlay(): Boolean =
        windowTitleBarConfigBacking.enabled || windowDragEnabledBacking

    private fun ensureDefaultTitleBarOverlay(): DefaultWindowTitleBarOverlay {
        val existing = defaultTitleBarOverlay
        if (existing != null && !existing.isDisposed) return existing
        return DefaultWindowTitleBarOverlay(titleBarConfig = windowTitleBarConfigBacking).also { created ->
            created.applyWindowDragFromHost(windowDragEnabledBacking, onWindowDragMoveBacking)
            defaultTitleBarOverlay = created
        }
    }

    private fun applyWindowTitleBarConfigToActiveOverlay() {
        val userOverlay = overlayBacking
        if (userOverlay != null) {
            userOverlay.applyWindowTitleBarConfigFromHost(windowTitleBarConfigBacking)
            return
        }
        syncDefaultTitleBarOverlayPresence()
    }

    private fun applyWindowDragConfigToActiveOverlay() {
        val userOverlay = overlayBacking
        if (userOverlay != null) {
            if (!userOverlay.explicitWindowDragConfig) {
                userOverlay.applyWindowDragFromHost(windowDragEnabledBacking, onWindowDragMoveBacking)
            }
            return
        }
        syncDefaultTitleBarOverlayPresence()
    }

    private fun syncDefaultTitleBarOverlayPresence() {
        if (shouldMountDefaultTitleBarOverlay()) {
            mountDefaultTitleBarOverlayIfNeeded()
        } else {
            unmountDefaultTitleBarOverlay()
            setLayerConstraint(
                overlayRoot,
                LayerConstraint.fill(zIndex = 10, passThroughPointerEvents = true)
            )
        }
    }

    private fun mountDefaultTitleBarOverlayIfNeeded() {
        if (overlayBacking != null || !shouldMountDefaultTitleBarOverlay()) return
        val defOverlay = ensureDefaultTitleBarOverlay()
        defOverlay.applyWindowTitleBarConfigFromHost(windowTitleBarConfigBacking)
        defOverlay.applyWindowDragFromHost(windowDragEnabledBacking, onWindowDragMoveBacking)
        if (themeColorHexBacking != null) {
            defOverlay.themeColorHex = themeColorHexBacking
        }
        val targetW = overlayRoot.widthPx ?: contentComponent?.widthPx ?: widthPx ?: 0
        val targetH = overlayRoot.heightPx ?: contentComponent?.heightPx ?: heightPx ?: 0
        if (targetW > 0 && targetH > 0) {
            defOverlay.notifyTargetResize(targetW, targetH)
        }
        if (!overlayRoot.layers.contains(defOverlay)) {
            overlayRoot.addLayer(defOverlay, LayerConstraint.fill(zIndex = 10))
            defOverlay.onAttachToHost(this)
        } else {
            defOverlay.syncWindowTitleBar()
        }
        setLayerConstraint(
            overlayRoot,
            LayerConstraint.fill(zIndex = 10, passThroughPointerEvents = false)
        )
        if (pipBacking) {
            defOverlay.setPictureInPictureMode(true)
            overlayRoot.isVisible = false
        }
    }

    private fun unmountDefaultTitleBarOverlay() {
        val defOverlay = defaultTitleBarOverlay ?: return
        if (overlayRoot.layers.contains(defOverlay)) {
            defOverlay.onDetachFromHost()
            overlayRoot.removeLayer(defOverlay)
        }
    }

    private fun bindAutoHideObserver(controller: AutoHideController) {
        if (observedAutoHideController === controller) return
        unbindAutoHideObserver()
        observedAutoHideController = controller
        if (!controller.visibilityObservers.contains(autoHideVisibilityObserver)) {
            controller.visibilityObservers.add(autoHideVisibilityObserver)
        }
    }

    private fun unbindAutoHideObserver() {
        observedAutoHideController?.visibilityObservers?.remove(autoHideVisibilityObserver)
        observedAutoHideController = null
    }

    private fun syncCursorHiddenClassFromOverlay() {
        val activeOverlay = overlayBacking
        val hideCursor = !disposedState &&
            activeOverlay != null &&
            !pipBacking &&
            !activeOverlay.autoHideController.isOverlayVisible &&
            activeOverlay.autoHideController.spec.hideCursorWhenOverlayHidden &&
            !activeOverlay.hasClass(AutoHideController.KEEP_CURSOR_CLASS)
        setClassPresent(this, CURSOR_HIDDEN_CLASS, hideCursor)
        setClassPresent(overlayRoot, CURSOR_HIDDEN_CLASS, hideCursor)
        setClassPresent(targetComponent, TARGET_CURSOR_HIDDEN_CLASS, hideCursor)
        if (contentComponent != null && fallbackTarget != null && fallbackTarget !== contentComponent) {
            clearCursorHiddenClassesFromTarget(fallbackTarget)
        }
    }

    private fun clearCursorHiddenClasses() {
        setClassPresent(this, CURSOR_HIDDEN_CLASS, false)
        setClassPresent(overlayRoot, CURSOR_HIDDEN_CLASS, false)
        clearCursorHiddenClassesFromTarget(contentComponent)
        clearCursorHiddenClassesFromTarget(fallbackTarget)
    }

    private fun clearCursorHiddenClassesFromTarget(target: AbstractComponent?) {
        setClassPresent(target, TARGET_CURSOR_HIDDEN_CLASS, false)
    }

    private fun setClassPresent(component: AbstractComponent?, className: String, present: Boolean) {
        if (component == null) return
        if (present) {
            if (!component.hasClass(className)) {
                component.addClass(className)
            }
        } else if (component.hasClass(className)) {
            component.removeClass(className)
        }
    }

    override fun dispose() {
        disposedState = true
        unbindAutoHideObserver()
        boundPresentTarget?.removeBeforeFramePresentListener(beforePresentListener)
        boundPresentTarget = null
        val current = overlay
        detachOverlayInternal(remountDefaultTitleBar = false)
        unmountDefaultTitleBarOverlay()
        defaultTitleBarOverlay?.dispose()
        defaultTitleBarOverlay = null
        current?.dispose()
        clearCursorHiddenClasses()
        super.dispose()
    }

    companion object {
        private const val CURSOR_HIDDEN_CLASS: String = "gui-overlay-cursor-hidden"
        private const val TARGET_CURSOR_HIDDEN_CLASS: String = "gui-overlay-host-target-cursor-hidden"
    }
}
