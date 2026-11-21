package com.nexustools.genericui.ext.pip.desktop

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.ext.overlay.OverlayHost
import com.nexustools.genericui.ext.pip.ComponentReparentSlot
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.PipBackend
import com.nexustools.genericui.ext.pip.PipCapabilities
import com.nexustools.genericui.ext.pip.PipComponentClassifier
import com.nexustools.genericui.ext.pip.PipContentType
import com.nexustools.genericui.ext.pip.PipControlOverlay
import com.nexustools.genericui.ext.pip.PipSessionConfig
import com.nexustools.genericui.ext.pip.PipTargetBoundsTracker
import com.nexustools.genericui.ext.pip.PipWindowTransitionAnimator
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.window.IWindow
import kotlin.math.roundToInt

/**
 * Always-on-top undecorated floating window [PipBackend] for native desktop backends
 * (Win32, macOS AppKit, Linux GTK4/Qt6).
 */
class DesktopFloatingPipBackend : PipBackend {

    override val capabilities: PipCapabilities = PipCapabilities(
        isSupported = true,
        supportedContentTypes = setOf(
            PipContentType.VIDEO,
            PipContentType.CANVAS,
            PipContentType.ANY_COMPONENT
        ),
        supportsCustomControlOverlay = true,
        supportsInPlaceWindowPip = false,
        supportsAutoEnterOnMinimize = true,
        supportsAutoEnterOnBackground = true,
        supportsAutoEnterOnTabSwitch = true
    )

    private var inPipModeState: Boolean = false
    override val isInPipMode: Boolean
        get() = inPipModeState

    var headlessImmediateTransitions: Boolean = false

    private var hostWindow: IWindow? = null
    private var hostFragment: Fragment? = null
    private var hostHelper: FragmentPipHelper? = null

    private val boundsTracker = PipTargetBoundsTracker()
    private val reparentSlot = ComponentReparentSlot()
    private val surfaceMirror = DesktopPipSurfaceMirror()
    private val animator = PipWindowTransitionAnimator()

    private var pipWindow: Window? = null
    private var pendingClosingWindow: Window? = null
    private var controlOverlay: PipControlOverlay? = null
    private var pipContentHost: Container? = null
    private var activeTargetRef: AbstractComponent? = null
    private var autoEnteredOnMinimize: Boolean = false

    val activePipWindow: Window?
        get() = pipWindow

    val activeControlOverlay: PipControlOverlay?
        get() = controlOverlay

    override fun supportsComponent(component: AbstractComponent): Boolean =
        PipComponentClassifier.isSupportedForCapabilities(component, capabilities)

    override fun attachHost(window: IWindow?, fragment: Fragment?, helper: FragmentPipHelper) {
        if (hostWindow !== window) {
            hostWindow?.setOnWindowStateChanged(null)
        }
        hostWindow = window
        hostFragment = fragment
        hostHelper = helper

        val target = helper.resolveEffectiveTarget()
        val (num, den) = helper.resolveEffectiveAspectRatio()
        boundsTracker.configure(window, target, num, den)

        window?.setOnWindowStateChanged { newState ->
            handleHostWindowStateChanged(newState)
        }
    }

    override fun detachHost() {
        if (inPipModeState) {
            exitPipImmediate()
        }
        animator.cancel()
        closePendingClosingWindow()
        hostWindow?.setOnWindowStateChanged(null)
        hostWindow = null
        hostFragment = null
        hostHelper = null
        boundsTracker.reset()
    }

    override fun syncSessionState() {
        val helper = hostHelper ?: return
        val target = helper.resolveEffectiveTarget()
        val (num, den) = helper.resolveEffectiveAspectRatio()
        boundsTracker.configure(hostWindow, target, num, den)

        if (!inPipModeState) return

        val config = helper.resolveEffectiveConfig()
        pipWindow?.let { win ->
            win.setTitle(config.title.ifBlank { DEFAULT_PIP_TITLE })
            win.setAspectRatio(num, den)
            win.setMinWindowSize(Size(MIN_PIP_WIDTH_PX, computeMinHeight(num, den)))
        }
        controlOverlay?.apply {
            setOverlayTitle(config.title)
            updateActions(helper.resolveEffectiveActions())
        }
    }

    override fun enterPip(): Boolean {
        if (inPipModeState) return true
        val helper = hostHelper ?: return false
        if (!helper.isEffectivelyEnabled()) return false
        val target = helper.resolveEffectiveTarget() ?: return false
        if (!supportsComponent(target)) return false

        animator.cancel()
        closePendingClosingWindow()

        val config = helper.resolveEffectiveConfig()
        val (num, den) = helper.resolveEffectiveAspectRatio()
        boundsTracker.configure(hostWindow, target, num, den)

        val startRect = boundsTracker.resolveCurrentOrCachedScreenRect(config)
        val endRect = boundsTracker.resolveDefaultDockRect(config)

        val windowTitle = config.title.ifBlank { DEFAULT_PIP_TITLE }
        val createdWindow = FloatingDesktopPipWindow(
            title = windowTitle,
            initialSize = Size(startRect.width, startRect.height)
        ).apply {
            setUndecorated(true)
            setAlwaysOnTop(true)
            setAspectRatio(num, den)
            setMinWindowSize(Size(MIN_PIP_WIDTH_PX, computeMinHeight(num, den)))
            windowBounds = startRect
        }

        val rootLayer = LayeredContainer("genericui:pip-root", "pip-window-root").apply {
            style.width = CssLength.percent(100f)
            style.height = CssLength.percent(100f)
            style.backgroundColor = RgbaColor.BLACK
        }
        val contentHost = Container("genericui:pip-content", "pip-content-slot").apply {
            style.width = CssLength.percent(100f)
            style.height = CssLength.percent(100f)
            style.backgroundColor = RgbaColor.BLACK
        }
        val overlay = PipControlOverlay(
            onRestoreRequested = { restoreOwnerAndExitPip() },
            onCloseRequested = { handleCloseRequested() },
            onDragMoveRequested = { createdWindow.startSystemMove() },
            actions = helper.resolveEffectiveActions()
        ).apply {
            setOverlayTitle(config.title)
        }

        rootLayer.addLayer(contentHost, LayerConstraint.fill(zIndex = 0))
        rootLayer.addLayer(overlay, LayerConstraint.fill(zIndex = 1))
        createdWindow.setContent(rootLayer)
        createdWindow.setOnWindowCloseRequested {
            handleCloseRequested()
            false
        }

        val factory = resolveHostFactory()
        createdWindow.show(factory)

        val mountedAsMirror = tryMountInteractiveSurfaceMirror(target, contentHost)
        if (!mountedAsMirror) {
            if (!reparentSlot.moveInto(target, contentHost)) {
                createdWindow.close()
                return false
            }
        }
        if (target is OverlayHost) {
            target.setPictureInPictureMode(true)
        }

        pipWindow = createdWindow
        controlOverlay = overlay
        pipContentHost = contentHost
        activeTargetRef = target
        inPipModeState = true

        helper.dispatchPipModeChangedFromBackend(true)

        val immediate = shouldSkipAnimation(factory)
        animator.animateBounds(
            fromRect = startRect,
            toRect = endRect,
            immediate = immediate,
            onStep = { rect ->
                if (pipWindow === createdWindow && !createdWindow.isDisposed) {
                    createdWindow.windowBounds = rect
                }
            }
        )
        return true
    }

    override fun exitPip(): Boolean {
        if (!inPipModeState) return false
        return performExit(immediate = shouldSkipAnimation(resolveHostFactory()))
    }

    private fun exitPipImmediate(): Boolean {
        if (!inPipModeState) return false
        return performExit(immediate = true)
    }

    private fun performExit(immediate: Boolean): Boolean {
        animator.cancel()
        closePendingClosingWindow()

        val closingWindow = pipWindow
        val currentBounds = closingWindow?.windowBounds
        val closingOverlay = controlOverlay
        val target = activeTargetRef

        surfaceMirror.unmount()
        reparentSlot.restore()
        if (target is OverlayHost) {
            target.setPictureInPictureMode(false)
        }

        pipWindow = null
        controlOverlay = null
        pipContentHost = null
        activeTargetRef = null
        inPipModeState = false
        autoEnteredOnMinimize = false

        closingOverlay?.dispose()

        val config = hostHelper?.resolveEffectiveConfig() ?: PipSessionConfig()
        hostHelper?.dispatchPipModeChangedFromBackend(false)

        if (closingWindow != null && !closingWindow.isDisposed) {
            if (immediate || currentBounds == null || hostWindow?.getWindowState() == WindowState.MINIMIZED) {
                closingWindow.close()
            } else {
                val returnRect = boundsTracker.resolveCurrentOrCachedScreenRect(config)
                pendingClosingWindow = closingWindow
                animator.animateBounds(
                    fromRect = currentBounds,
                    toRect = returnRect,
                    immediate = false,
                    onStep = { rect ->
                        if (pendingClosingWindow === closingWindow && !closingWindow.isDisposed) {
                            closingWindow.windowBounds = rect
                        }
                    },
                    onComplete = {
                        if (pendingClosingWindow === closingWindow) {
                            pendingClosingWindow = null
                        }
                        if (!closingWindow.isDisposed) {
                            closingWindow.close()
                        }
                    }
                )
            }
        }
        return true
    }

    private fun restoreOwnerAndExitPip() {
        autoEnteredOnMinimize = false
        hostWindow?.let { win ->
            if (win.getWindowState() == WindowState.MINIMIZED) {
                win.restore()
            }
            win.bringToFront()
            win.focus()
        }
        exitPip()
    }

    private fun handleCloseRequested() {
        hostHelper?.dispatchCloseRequestedFromBackend()
        exitPip()
    }

    private fun handleHostWindowStateChanged(newState: WindowState) {
        val helper = hostHelper ?: return
        when (newState) {
            WindowState.MINIMIZED -> {
                val config = helper.resolveEffectiveConfig()
                if (!inPipModeState &&
                    config.autoEnterOnMinimize &&
                    helper.isEffectivelyEnabled() &&
                    helper.isEffectivelyPlaying()
                ) {
                    autoEnteredOnMinimize = true
                    if (!helper.enterPip()) {
                        autoEnteredOnMinimize = false
                    }
                }
            }
            WindowState.NORMAL, WindowState.MAXIMIZED -> {
                if (inPipModeState && autoEnteredOnMinimize) {
                    exitPip()
                }
                boundsTracker.captureIfVisible()
            }
            WindowState.FULLSCREEN -> Unit
        }
    }

    private fun tryMountInteractiveSurfaceMirror(
        target: AbstractComponent,
        contentHost: Container
    ): Boolean {
        val sourceWidget = findDesktopSurfaceWidget(target) ?: return false
        if (sourceWidget.interactiveSurfaceSpec == null) return false

        val mirrorHost = Container("genericui:pip-mirror", "pip-surface-mirror").apply {
            style.width = CssLength.percent(100f)
            style.height = CssLength.percent(100f)
            style.backgroundColor = RgbaColor.BLACK
        }
        contentHost.addChild(mirrorHost)

        val mirrorImpl = mirrorHost.ensureNativeImpl() as? DesktopBaseNativeImpl<*>
        val mirrorWidget = mirrorImpl?.nativeWidget
        if (mirrorWidget == null || !surfaceMirror.mount(sourceWidget, mirrorWidget)) {
            contentHost.removeChild(mirrorHost)
            mirrorHost.dispose()
            return false
        }
        return true
    }

    private fun findDesktopSurfaceWidget(root: AbstractComponent): DesktopNativeWidget? {
        val visited = mutableListOf<AbstractComponent>()
        var current: AbstractComponent? = root
        while (current != null && visited.none { it === current }) {
            visited.add(current)
            val impl = if (current.parent != null && current.isVisible && current.resolveActiveFactory() != null) {
                current.ensureNativeImpl()
            } else {
                null
            }
            val widget = (impl as? DesktopBaseNativeImpl<*>)?.nativeWidget
            if (widget?.interactiveSurfaceSpec != null) {
                return widget
            }
            current = (current as? OverlayHost)?.targetComponent
        }
        return null
    }

    private fun resolveHostFactory(): NativeFactory? =
        (hostWindow as? Window)?.resolveActiveFactory() ?: GenericUI.activeFactory

    private fun shouldSkipAnimation(factory: NativeFactory?): Boolean =
        headlessImmediateTransitions || factory == null

    private fun closePendingClosingWindow() {
        val lingering = pendingClosingWindow
        pendingClosingWindow = null
        if (lingering != null && !lingering.isDisposed) {
            lingering.close()
        }
    }

    private fun computeMinHeight(numerator: Int, denominator: Int): Int {
        if (numerator <= 0 || denominator <= 0) return 100
        return (MIN_PIP_WIDTH_PX.toDouble() * denominator.toDouble() / numerator.toDouble())
            .roundToInt()
            .coerceAtLeast(80)
    }

    private class FloatingDesktopPipWindow(
        title: String,
        initialSize: Size
    ) : Window(title = title, initialSize = initialSize) {
        override val isMinimizable: Boolean = false
        override val isMaximizable: Boolean = false
        override val isToolWindow: Boolean = true
    }

    private companion object {
        const val DEFAULT_PIP_TITLE = "Picture-in-Picture"
        const val MIN_PIP_WIDTH_PX = 180
    }
}
