package com.nexustools.genericui.ext.pip.web

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.ext.overlay.OverlayHost
import com.nexustools.genericui.ext.overlay.OverlayStageProvider
import com.nexustools.genericui.ext.pip.ComponentReparentSlot
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.PipBackend
import com.nexustools.genericui.ext.pip.PipCapabilities
import com.nexustools.genericui.ext.pip.PipComponentClassifier
import com.nexustools.genericui.ext.pip.PipContentType
import com.nexustools.genericui.ext.pip.PipControlOverlay
import com.nexustools.genericui.ext.pip.PipExtension
import com.nexustools.genericui.ext.pip.PipSessionConfig
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.web.WebDomElement
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.WindowAspectRatioHelper
import kotlin.math.roundToInt

/**
 * Web Picture-in-Picture backend supporting:
 * 1. **Document Picture-in-Picture (`window.documentPictureInPicture`)** for arbitrary components
 *    with [PipControlOverlay] controls and [ComponentReparentSlot] preservation.
 * 2. **Native `<video>` Element Picture-in-Picture** when Document PiP is unavailable for raw video targets.
 * 3. **In-Place Fixed Corner Mini-Player Fallback** (`position: fixed` floating overlay shell)
 *    when Document PiP is unsupported, blocked without user gesture, or running headless.
 */
class WebPipBackend : PipBackend {

    override val capabilities: PipCapabilities
        get() = PipCapabilities(
            isSupported = true,
            supportedContentTypes = setOf(
                PipContentType.VIDEO,
                PipContentType.CANVAS,
                PipContentType.ANY_COMPONENT
            ),
            supportsCustomControlOverlay = true,
            supportsInPlaceWindowPip = true,
            supportsAutoEnterOnMinimize = false,
            supportsAutoEnterOnBackground = true,
            supportsAutoEnterOnTabSwitch = true
        )

    private var inPipModeState: Boolean = false
    override val isInPipMode: Boolean
        get() = inPipModeState

    var preferDocumentPip: Boolean = true
    var preferNativeVideoElementFallback: Boolean = false

    private var hostWindow: IWindow? = null
    private var hostFragment: Fragment? = null
    private var hostHelper: FragmentPipHelper? = null

    private val reparentSlot = ComponentReparentSlot()

    private var visibilityUnsubscribe: (() -> Unit)? = null
    private var pipRoot: FloatingWebPipRoot? = null
    private var controlOverlay: PipControlOverlay? = null
    private var activeTargetRef: AbstractComponent? = null
    private var suppressedStageOverlayHost: OverlayHost? = null
    private var activeMode: ActiveWebPipMode = ActiveWebPipMode.NONE
    private var autoEnteredOnHidden: Boolean = false
    private var inPlaceDragActive: Boolean = false
    private var hasInPlacePosition: Boolean = false
    private var inPlaceRightPx: Int = 24
    private var inPlaceBottomPx: Int = 24

    val activeFloatingRoot: LayeredContainer?
        get() = pipRoot

    val activeControlOverlay: PipControlOverlay?
        get() = controlOverlay

    override fun supportsComponent(component: AbstractComponent): Boolean =
        PipComponentClassifier.isSupportedForCapabilities(component, capabilities)

    override fun attachHost(window: IWindow?, fragment: Fragment?, helper: FragmentPipHelper) {
        if (hostWindow !== window) {
            hostWindow?.setOnWindowStateChanged(null)
        }
        visibilityUnsubscribe?.invoke()
        visibilityUnsubscribe = null

        hostWindow = window
        hostFragment = fragment
        hostHelper = helper

        window?.setOnWindowStateChanged { state ->
            handleHostWindowStateChanged(state)
        }
        visibilityUnsubscribe = WebPipInteropBridge.observeDocumentVisibility { hidden ->
            handleDocumentHiddenChanged(hidden)
        }
    }

    override fun detachHost() {
        if (inPipModeState) {
            exitPip()
        }
        visibilityUnsubscribe?.invoke()
        visibilityUnsubscribe = null
        hostWindow?.setOnWindowStateChanged(null)
        hostWindow = null
        hostFragment = null
        hostHelper = null
    }

    override fun syncSessionState() {
        if (!inPipModeState) return
        val helper = hostHelper ?: return
        val config = helper.resolveEffectiveConfig()
        val (num, den) = helper.resolveEffectiveAspectRatio()
        val pipSize = clampPipSize(config, num, den)

        controlOverlay?.apply {
            setOverlayTitle(config.title)
            updateActions(helper.resolveEffectiveActions())
        }
        if (activeMode == ActiveWebPipMode.IN_PLACE) {
            applyInPlaceFloatingStyles(pipRoot, pipSize, config.screenMarginPx)
        }
    }

    override fun enterPip(): Boolean {
        if (inPipModeState) return true
        val helper = hostHelper ?: return false
        if (!helper.isEffectivelyEnabled()) return false
        val target = helper.resolveEffectiveTarget() ?: return false
        if (!supportsComponent(target)) return false

        val config = helper.resolveEffectiveConfig()
        val (num, den) = helper.resolveEffectiveAspectRatio()
        val pipSize = clampPipSize(config, num, den)

        if (preferNativeVideoElementFallback && !WebPipInteropBridge.isDocumentPipSupported()) {
            val videoPeer = resolveBrowserVideoPeer(target)
            if (videoPeer != null && WebPipInteropBridge.isVideoElementPipSupported()) {
                activeTargetRef = target
                activeMode = ActiveWebPipMode.VIDEO_ELEMENT
                val requested = WebPipInteropBridge.requestVideoElementPip(
                    videoPeer = videoPeer,
                    onEntered = {
                        if (activeMode == ActiveWebPipMode.VIDEO_ELEMENT && !inPipModeState) {
                            inPipModeState = true
                            helper.dispatchPipModeChangedFromBackend(true)
                        }
                    },
                    onExited = {
                        if (activeMode == ActiveWebPipMode.VIDEO_ELEMENT) {
                            inPipModeState = false
                            activeMode = ActiveWebPipMode.NONE
                            activeTargetRef = null
                            autoEnteredOnHidden = false
                            helper.dispatchPipModeChangedFromBackend(false)
                        }
                    },
                    onFailed = {
                        if (activeMode == ActiveWebPipMode.VIDEO_ELEMENT && !inPipModeState) {
                            activeMode = ActiveWebPipMode.NONE
                            activeTargetRef = null
                            enterFloatingContainerPip(target, config, pipSize, helper)
                        }
                    }
                )
                if (requested) {
                    return true
                }
                activeMode = ActiveWebPipMode.NONE
                activeTargetRef = null
            }
        }

        return enterFloatingContainerPip(target, config, pipSize, helper)
    }

    override fun exitPip(): Boolean {
        if (!inPipModeState) return false
        val mode = activeMode
        val root = pipRoot
        val overlay = controlOverlay
        val target = activeTargetRef
        val stageOverlay = suppressedStageOverlayHost

        activeMode = ActiveWebPipMode.NONE
        pipRoot = null
        controlOverlay = null
        activeTargetRef = null
        suppressedStageOverlayHost = null
        inPipModeState = false
        autoEnteredOnHidden = false
        inPlaceDragActive = false
        hasInPlacePosition = false

        when (mode) {
            ActiveWebPipMode.DOCUMENT_PIP -> {
                reparentSlot.restore()
                if (target is OverlayHost) {
                    target.setPictureInPictureMode(false)
                }
                if (stageOverlay != null && stageOverlay !== target) {
                    stageOverlay.setPictureInPictureMode(false)
                }
                WebPipInteropBridge.closeDocumentPipWindow()
            }
            ActiveWebPipMode.IN_PLACE -> {
                reparentSlot.restore()
                if (target is OverlayHost) {
                    target.setPictureInPictureMode(false)
                }
                if (stageOverlay != null && stageOverlay !== target) {
                    stageOverlay.setPictureInPictureMode(false)
                }
                val rootDom = root?.ensureNativeImpl()?.nativeWidget as? WebDomElement
                WebPipInteropBridge.detachInPlaceRoot(rootDom?.nativeBrowserPeer)
            }
            ActiveWebPipMode.VIDEO_ELEMENT -> {
                WebPipInteropBridge.exitVideoElementPip()
            }
            ActiveWebPipMode.NONE -> Unit
        }

        overlay?.dispose()
        root?.dispose()
        hostHelper?.dispatchPipModeChangedFromBackend(false)
        return true
    }

    private fun enterFloatingContainerPip(
        target: AbstractComponent,
        config: PipSessionConfig,
        pipSize: Size,
        helper: FragmentPipHelper
    ): Boolean {
        val factory = resolveHostFactory()
        val root = FloatingWebPipRoot(factory).apply {
            id = "genericui-web-pip-root"
            addClass("pip-web-floating-root")
        }
        val contentHost = Container("genericui:pip-content", "pip-content-slot").apply {
            addClass("pip-content-slot")
        }
        val overlay = PipControlOverlay(
            onRestoreRequested = { exitPip() },
            onCloseRequested = {
                helper.dispatchCloseRequestedFromBackend()
                exitPip()
            },
            onDragMoveRequested = {
                controlOverlay?.showTemporarily()
                if (activeMode == ActiveWebPipMode.IN_PLACE) {
                    inPlaceDragActive = true
                }
            },
            actions = helper.resolveEffectiveActions(),
            onPlayPauseRequested = { helper.resolveEffectivePlayPauseAction()?.invoke() },
            onDoubleClickRequested = {
                helper.resolveEffectiveDoubleClickAction()?.invoke() ?: exitPip()
            }
        ).apply {
            setOverlayTitle(config.title)
        }
        attachInPlaceDragFallback(overlay, root)

        root.addLayer(contentHost, LayerConstraint.fill(zIndex = 0))
        root.addLayer(overlay, LayerConstraint.fill(zIndex = 1))
        root.applyStyleSheet(PipExtension.composeEffectiveStyleSheet())

        if (!reparentSlot.moveInto(target, contentHost)) {
            overlay.dispose()
            root.dispose()
            return false
        }
        val stageOverlayHost = resolveStageOverlayHost(target)
        suppressedStageOverlayHost = stageOverlayHost
        stageOverlayHost?.setPictureInPictureMode(true)

        val rootDom = root.ensureNativeImpl()?.nativeWidget as? WebDomElement
        val browserPeer = rootDom?.nativeBrowserPeer

        pipRoot = root
        controlOverlay = overlay
        activeTargetRef = target

        val useDocPip = preferDocumentPip &&
            WebPipInteropBridge.isDocumentPipSupported() &&
            browserPeer != null

        if (useDocPip) {
            activeMode = ActiveWebPipMode.DOCUMENT_PIP
            inPipModeState = true
            val initiated = WebPipInteropBridge.openDocumentPipWindow(
                widthPx = pipSize.width,
                heightPx = pipSize.height,
                title = config.title.ifBlank { DEFAULT_PIP_TITLE },
                rootPeer = browserPeer,
                onEntered = {
                    if (pipRoot === root && activeMode == ActiveWebPipMode.DOCUMENT_PIP) {
                        clearInPlaceFloatingStyles(root)
                    }
                },
                onClosed = {
                    if (pipRoot === root && inPipModeState) {
                        helper.dispatchCloseRequestedFromBackend()
                        exitPip()
                    }
                },
                onFailed = {
                    if (pipRoot === root && inPipModeState) {
                        // Document PiP was blocked (e.g. non-user-gesture tab switch): degrade to fixed overlay.
                        activeMode = ActiveWebPipMode.IN_PLACE
                        applyInPlaceFloatingStyles(root, pipSize, config.screenMarginPx)
                        WebPipInteropBridge.attachInPlaceRoot(browserPeer)
                    }
                }
            )
            if (!initiated) {
                activeMode = ActiveWebPipMode.IN_PLACE
                applyInPlaceFloatingStyles(root, pipSize, config.screenMarginPx)
                WebPipInteropBridge.attachInPlaceRoot(browserPeer)
            }
        } else {
            activeMode = ActiveWebPipMode.IN_PLACE
            inPipModeState = true
            applyInPlaceFloatingStyles(root, pipSize, config.screenMarginPx)
            WebPipInteropBridge.attachInPlaceRoot(browserPeer)
        }

        helper.dispatchPipModeChangedFromBackend(true)
        return true
    }

    private fun applyInPlaceFloatingStyles(
        root: FloatingWebPipRoot?,
        size: Size,
        marginPx: Int
    ) {
        if (root == null) return
        val clampedMargin = marginPx.coerceAtLeast(8)
        if (!hasInPlacePosition) {
            inPlaceRightPx = clampedMargin
            inPlaceBottomPx = clampedMargin
            hasInPlacePosition = true
        }
        root.addClass("pip-web-floating-pip")
        root.style.width = CssLength.px(size.width)
        root.style.height = CssLength.px(size.height)
        val dom = root.ensureNativeImpl()?.nativeWidget as? WebDomElement ?: return
        dom.style["position"] = "fixed"
        dom.style["right"] = "${inPlaceRightPx}px"
        dom.style["bottom"] = "${inPlaceBottomPx}px"
        dom.style["width"] = "${size.width}px"
        dom.style["height"] = "${size.height}px"
        dom.style["z-index"] = "2147483000"
        dom.style["border-radius"] = "12px"
        dom.style["overflow"] = "hidden"
        dom.style["box-shadow"] = "0 12px 32px rgba(0, 0, 0, 0.45)"
        dom.style["background"] = "#000000"
    }

    private fun clearInPlaceFloatingStyles(root: FloatingWebPipRoot?) {
        inPlaceDragActive = false
        hasInPlacePosition = false
        if (root == null) return
        root.removeClass("pip-web-floating-pip")
        root.style.width = null
        root.style.height = null
        val dom = root.ensureNativeImpl()?.nativeWidget as? WebDomElement ?: return
        dom.style["position"] = "relative"
        dom.style.remove("right")
        dom.style.remove("bottom")
        dom.style["width"] = "100%"
        dom.style["height"] = "100%"
        dom.style.remove("z-index")
        dom.style.remove("border-radius")
        dom.style.remove("box-shadow")
    }

    private fun attachInPlaceDragFallback(
        overlay: PipControlOverlay,
        root: FloatingWebPipRoot
    ) {
        val prevDown = overlay.dragSurface.onPointerDown
        val prevMove = overlay.dragSurface.onPointerMove
        val prevUp = overlay.dragSurface.onPointerUp
        var lastPointerX = 0f
        var lastPointerY = 0f
        var pointerTracked = false

        overlay.dragSurface.onPointerDown = { event ->
            inPlaceDragActive = false
            lastPointerX = if (event.screenX != 0f || event.screenY != 0f) event.screenX else event.x
            lastPointerY = if (event.screenX != 0f || event.screenY != 0f) event.screenY else event.y
            pointerTracked = true
            prevDown?.invoke(event)
        }
        overlay.dragSurface.onPointerMove = { event ->
            prevMove?.invoke(event)
            if (activeMode == ActiveWebPipMode.IN_PLACE && inPlaceDragActive && pointerTracked) {
                val curX = if (event.screenX != 0f || event.screenY != 0f) event.screenX else event.x
                val curY = if (event.screenX != 0f || event.screenY != 0f) event.screenY else event.y
                val dx = (curX - lastPointerX).roundToInt()
                val dy = (curY - lastPointerY).roundToInt()
                lastPointerX = curX
                lastPointerY = curY
                inPlaceRightPx = (inPlaceRightPx - dx).coerceAtLeast(8)
                inPlaceBottomPx = (inPlaceBottomPx - dy).coerceAtLeast(8)
                hasInPlacePosition = true
                val dom = root.ensureNativeImpl()?.nativeWidget as? WebDomElement
                if (dom != null) {
                    dom.style["right"] = "${inPlaceRightPx}px"
                    dom.style["bottom"] = "${inPlaceBottomPx}px"
                }
            }
        }
        overlay.dragSurface.onPointerUp = { event ->
            inPlaceDragActive = false
            pointerTracked = false
            prevUp?.invoke(event)
        }
    }

    private fun resolveStageOverlayHost(target: AbstractComponent?): OverlayHost? =
        (target as? OverlayHost) ?: (hostFragment as? OverlayStageProvider)?.stageOverlayHost

    private fun resolveBrowserVideoPeer(component: AbstractComponent): Any? {
        val target = (component as? OverlayHost)?.targetComponent ?: component
        val impl = if (target.resolveActiveFactory() != null || GenericUI.activeFactory != null) {
            target.ensureNativeImpl()
        } else {
            null
        }
        val dom = impl?.nativeWidget as? WebDomElement ?: return null
        val peer = dom.nativeBrowserPeer ?: return null
        return if (WebPipInteropBridge.isHtmlVideoElement(peer)) peer else null
    }

    private fun handleHostWindowStateChanged(state: WindowState) {
        val helper = hostHelper ?: return
        val config = helper.resolveEffectiveConfig()
        when (state) {
            WindowState.MINIMIZED -> {
                if (!inPipModeState &&
                    config.autoEnterOnMinimize &&
                    helper.isEffectivelyEnabled() &&
                    helper.isEffectivelyPlaying()
                ) {
                    autoEnteredOnHidden = true
                    if (!helper.enterPip()) {
                        autoEnteredOnHidden = false
                    }
                }
            }
            WindowState.NORMAL, WindowState.MAXIMIZED -> {
                if (inPipModeState && autoEnteredOnHidden) {
                    exitPip()
                }
            }
            WindowState.FULLSCREEN -> Unit
        }
    }

    private fun handleDocumentHiddenChanged(hidden: Boolean) {
        val helper = hostHelper ?: return
        val config = helper.resolveEffectiveConfig()
        if (hidden) {
            if (!inPipModeState &&
                (config.autoEnterOnTabSwitch || config.autoEnterOnBackground) &&
                helper.isEffectivelyEnabled() &&
                helper.isEffectivelyPlaying()
            ) {
                autoEnteredOnHidden = true
                if (!helper.enterPip()) {
                    autoEnteredOnHidden = false
                }
            }
        } else if (inPipModeState && autoEnteredOnHidden) {
            exitPip()
        }
    }

    private fun clampPipSize(config: PipSessionConfig, num: Int, den: Int): Size {
        val ratio = if (num > 0 && den > 0) num.toDouble() / den.toDouble() else 16.0 / 9.0
        val w = config.preferredWidthPx.takeIf { it > 0 } ?: 420
        val h = config.preferredHeightPx.takeIf { it > 0 } ?: (w.toDouble() / ratio).roundToInt()
        return WindowAspectRatioHelper.clampClientSize(
            size = Size(w, h),
            aspectRatio = ratio,
            minSize = Size(160, 90),
            preferWidth = true
        )
    }

    private fun resolveHostFactory(): NativeFactory? =
        (hostWindow as? Window)?.resolveActiveFactory() ?: GenericUI.activeFactory

    private enum class ActiveWebPipMode {
        NONE,
        DOCUMENT_PIP,
        IN_PLACE,
        VIDEO_ELEMENT
    }

    private class FloatingWebPipRoot(
        private val explicitFactory: NativeFactory?
    ) : LayeredContainer("genericui:web-pip-root", "genericui-web-pip-root") {
        override fun resolveActiveFactory(): NativeFactory? =
            parent?.resolveChildFactory() ?: explicitFactory ?: GenericUI.activeFactory
    }

    private companion object {
        const val DEFAULT_PIP_TITLE = "Picture-in-Picture"
    }
}
