package com.nexustools.genericui.ext.pip.swing

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.ext.overlay.OverlayHost
import com.nexustools.genericui.ext.overlay.OverlayHostBox
import com.nexustools.genericui.ext.overlay.OverlayStageProvider
import com.nexustools.genericui.ext.pip.ComponentReparentSlot
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.PiPCompatible
import com.nexustools.genericui.ext.pip.PipBackend
import com.nexustools.genericui.ext.pip.PipCapabilities
import com.nexustools.genericui.ext.pip.PipComponentClassifier
import com.nexustools.genericui.ext.pip.PipContentType
import com.nexustools.genericui.ext.pip.PipControlOverlay
import com.nexustools.genericui.ext.pip.PipExtension
import com.nexustools.genericui.ext.pip.PipSessionConfig
import com.nexustools.genericui.ext.pip.PipTargetBoundsTracker
import com.nexustools.genericui.ext.pip.PipWindowTransitionAnimator
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.swing.SwingWindowImpl
import com.nexustools.genericui.window.IWindow
import java.awt.GraphicsEnvironment
import kotlin.math.roundToInt

/**
 * Always-on-top undecorated floating window [PipBackend] for the JVM Swing backend.
 *
 * Uses [ComponentReparentSlot] to host any component (`VideoPlayer`, `Canvas3D`, `@PiPCompatible`,
 * or arbitrary `AbstractComponent` subtrees) inside an undecorated top-level utility [Window]
 * with a floating [PipControlOverlay] and restores the component cleanly on exit.
 */
class SwingFloatingPipBackend : PipBackend {

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
    private val animator = PipWindowTransitionAnimator()

    private var pipWindow: Window? = null
    private var pendingClosingWindow: Window? = null
    private var controlOverlay: PipControlOverlay? = null
    private var overlayHost: OverlayHostBox? = null
    private var pipContentHost: Container? = null
    private var activeTargetRef: AbstractComponent? = null
    private var autoEnteredOnMinimize: Boolean = false

    val activePipWindow: Window?
        get() = pipWindow

    val activeControlOverlay: PipControlOverlay?
        get() = controlOverlay

    val activeOverlayHost: OverlayHostBox?
        get() = overlayHost

    override fun supportsComponent(component: AbstractComponent): Boolean =
        hasPipCompatibleAnnotation(component) ||
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
        overlayHost?.windowTitleBar?.syncFromWindow(pipWindow)
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
        val createdWindow = FloatingSwingPipWindow(
            title = windowTitle,
            initialSize = Size(startRect.width, startRect.height)
        ).apply {
            setUndecorated(true)
            setAlwaysOnTop(true)
            setAspectRatio(num, den)
            setMinWindowSize(Size(MIN_PIP_WIDTH_PX, computeMinHeight(num, den)))
            windowBounds = startRect
        }

        val contentHost = Container("genericui:pip-content", "pip-content-slot").apply {
            addClass("pip-content-slot")
        }
        val overlay = PipControlOverlay(
            onRestoreRequested = { restoreOwnerAndExitPip() },
            onCloseRequested = { handleCloseRequested() },
            onDragMoveRequested = {
                if (!createdWindow.isDisposed && createdWindow.isWindowShown) {
                    createdWindow.startSystemMove()
                }
            },
            actions = helper.resolveEffectiveActions(),
            onPlayPauseRequested = { helper.resolveEffectivePlayPauseAction()?.invoke() },
            onDoubleClickRequested = {
                helper.resolveEffectiveDoubleClickAction()?.invoke() ?: restoreOwnerAndExitPip()
            }
        ).apply {
            setOverlayTitle(config.title)
        }
        val hostBox = OverlayHostBox(
            content = contentHost,
            initialOverlay = overlay
        ).apply {
            id = "pip-overlay-host"
            addClass("pip-overlay-host")
            setAttribute("borderRadiusPx", "0")
            windowTitleBarConfig = overlay.windowTitleBarConfig.copy(
                enabled = true,
                title = config.title.ifBlank { null }
            )
            applyStyleSheet(PipExtension.composeEffectiveStyleSheet())
        }

        createdWindow.setContent(hostBox)
        hostBox.windowTitleBar.syncFromWindow(createdWindow)
        createdWindow.setOnWindowCloseRequested {
            handleCloseRequested()
            false
        }

        if (!reparentSlot.moveInto(target, contentHost)) {
            createdWindow.close()
            return false
        }
        val stageHost = (target as? OverlayHost) ?: (hostFragment as? OverlayStageProvider)?.stageOverlayHost
        stageHost?.setPictureInPictureMode(true)

        val factory = resolveHostFactory()
        createdWindow.show(factory)

        if (!GraphicsEnvironment.isHeadless() && factory != null) {
            val swingImpl = createdWindow.ensureNativeImpl() as? SwingWindowImpl
            swingImpl?.jFrame?.let { frame ->
                runCatching {
                    if (!frame.isDisplayable) {
                        frame.type = java.awt.Window.Type.UTILITY
                    }
                    frame.isAlwaysOnTop = true
                }
            }
        }

        pipWindow = createdWindow
        controlOverlay = overlay
        overlayHost = hostBox
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
        val closingHost = overlayHost
        val target = activeTargetRef

        reparentSlot.restore()
        val stageHost = (target as? OverlayHost) ?: (hostFragment as? OverlayStageProvider)?.stageOverlayHost
        stageHost?.setPictureInPictureMode(false)

        pipWindow = null
        controlOverlay = null
        overlayHost = null
        pipContentHost = null
        activeTargetRef = null
        inPipModeState = false
        autoEnteredOnMinimize = false

        closingOverlay?.dispose()
        closingHost?.dispose()

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

    private fun hasPipCompatibleAnnotation(component: AbstractComponent): Boolean {
        var cls: Class<*>? = component::class.java
        while (cls != null) {
            if (cls.isAnnotationPresent(PiPCompatible::class.java)) {
                return true
            }
            cls = cls.superclass
        }
        return false
    }

    private fun resolveHostFactory(): NativeFactory? =
        (hostWindow as? Window)?.resolveActiveFactory() ?: GenericUI.activeFactory

    private fun shouldSkipAnimation(factory: NativeFactory?): Boolean =
        headlessImmediateTransitions || GraphicsEnvironment.isHeadless() || factory == null

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

    private class FloatingSwingPipWindow(
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
