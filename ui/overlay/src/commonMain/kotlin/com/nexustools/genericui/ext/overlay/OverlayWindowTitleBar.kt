package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.action.styled.StyledButton
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.Spacer
import com.nexustools.genericui.component.layout.styled.StyledContainer
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextOverflow
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.enums.window.TitleBarControlsPlacement
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.theme.SystemThemeDetector
import com.nexustools.genericui.style.theme.ThemeConfig
import com.nexustools.genericui.window.IWindow

/**
 * Overlay-embedded windowed titlebar (`data-overlay-role = "window-title-bar"`).
 *
 * Embedded **only** in windowed mode on windows where the backend can faithfully render custom
 * undecorated titlebars and embed components (`titleCapabilities.isTitleBarRequired &&
 * titleCapabilities.supportsCustomTitleBar && titleCapabilities.canEmbedComponentsInTitleBar`) —
 * never on Swing when unsupported, never on non-replicable Linux WMs, never in fullscreen
 * (`isFullscreen()` or `WindowState.FULLSCREEN`), and never on mobile/web browser tabs where
 * `isTitleBarRequired == false`.
 *
 * On macOS (`prefersBlurredTranslucentOverlayTitleBar == true`), adopts `22px` backdrop blur with
 * `1.4` saturation (`backdrop-filter: blur(22px) saturate(1.4)`), translucent frosted-glass fill
 * (`rgba(16, 18, 24, 0.22)` dark / `rgba(244, 246, 250, 0.28)` light), subtle `1px` bottom hairline,
 * and leading (`LEADING`) `12×12` traffic-light controls (`[close, minimize, maximize]`) with
 * group-hover glyphs (`×`, `–`, `+`) (or native macOS traffic lights when
 * `usesNativeOverlayTitleBarControls == true`), unfocused dimming, and a full-width centered title.
 * On Windows and supported replicable Linux WMs (`TRAILING`), uses `backdrop-filter: none`,
 * semi-transparent flat fill (`rgba(16, 18, 24, 0.42)` dark / `rgba(244, 246, 250, 0.48)` light),
 * and trailing caption controls (`[minimize, maximize, close]`).
 */
open class OverlayWindowTitleBar(
    config: OverlayWindowTitleBarConfig = OverlayWindowTitleBarConfig(),
    attachedWindow: IWindow? = null
) : StyledContainer(
    classPrefix = "gui-overlay-window-title-bar",
    tagName = "genericui:overlay-window-title-bar"
) {

    var config: OverlayWindowTitleBarConfig = config
        set(value) {
            if (field == value) return
            field = value
            appliedDisabledInactiveState = false
            syncFromWindow()
        }

    private var explicitBoundWindow: IWindow? = attachedWindow
    private var lastSyncedWindow: IWindow? = null
    private var isSyncingFromWindow: Boolean = false
    private var appliedDisabledInactiveState: Boolean = false
    private var lastObservedWindowRef: IWindow? = null
    private var lastObservedFullscreen: Boolean? = null
    private var lastObservedWindowState: WindowState? = null
    private var lastObservedTitle: String? = null
    private var lastObservedTitleBarRequired: Boolean? = null
    private var lastObservedSupportsCustomTitleBar: Boolean? = null
    private var lastObservedCanEmbedComponents: Boolean? = null
    private var lastObservedPrefersBlur: Boolean? = null
    private var lastObservedUsesNativeControls: Boolean? = null
    private var lastObservedControlsPlacement: TitleBarControlsPlacement? = null
    private var lastObservedConfigThemeMode: ThemeMode? = null
    private var lastObservedEffectiveTheme: ThemeMode? = null
    private var lastObservedWindowFocused: Boolean? = null
    private var observedFullscreenComponent: AbstractComponent? = null
    private var observedTitleWindow: IWindow? = null
    private var observedFocusWindow: IWindow? = null
    private var previousObservedFullscreenListener: ((Boolean) -> Unit)? = null
    private val observedFullscreenForwarder: (Boolean) -> Unit = { fs ->
        setNativeTitleBarHoverActive(false)
        previousObservedFullscreenListener?.invoke(fs)
        syncFromWindow()
    }
    private val observedTitleChangedListener: (String) -> Unit = { _ ->
        syncFromWindow()
    }
    private val observedWindowFocusChangedListener: (Boolean) -> Unit = { focused ->
        if (!isDisposed) {
            applyWindowFocusStyle(focused)
        }
    }
    private var appliedPlacement: TitleBarControlsPlacement? = null
    private var appliedBlurredStyle: Boolean? = null
    private var appliedEffectiveTheme: ThemeMode? = null
    private var appliedWindowFocused: Boolean? = null
    private var appliedUsesNativeControls: Boolean? = null
    private var appliedHeightPx: Int = -1
    private var appliedMaximizedState: Boolean? = null
    private var nativeTitleBarHoverActive: Boolean = false
    private var controlsContainerHovered: Boolean = false
    private val hoveredControlButtons = linkedSetOf<StyledButton>()
    private var suppressBarPressFromChildButton: Boolean = false
    private var suppressBarPressResetTimerId: Int = -1

    internal var inputRouter: OverlayInputRouter? = null

    var onCloseAction: (() -> Unit)? = null
    var onMinimizeAction: (() -> Unit)? = null
    var onMaximizeRestoreAction: (() -> Unit)? = null
    var onDragMoveAction: (() -> Unit)? = null
    var onVisibilityOrHeightChanged: ((active: Boolean, effectiveHeightPx: Int) -> Unit)? = null

    override var isVisible: Boolean
        get() {
            syncIfWindowStateChanged()
            return super.isVisible
        }
        set(value) {
            super.isVisible = value
        }

    val attachedWindow: IWindow?
        get() = resolveAttachedWindow()

    val isWindowedTitleBarActive: Boolean
        get() {
            syncIfWindowStateChanged()
            return evaluateWindowedTitleBarActive(resolveAttachedWindow())
        }

    val effectiveHeightPx: Int
        get() = if (isWindowedTitleBarActive) resolveBarHeightPx(resolveAttachedWindow()) else 0

    val resolvedControlsPlacement: TitleBarControlsPlacement
        get() = resolvePlacement(resolveAttachedWindow())

    val resolvedEffectiveThemeMode: ThemeMode
        get() = resolveEffectiveThemeMode()

    val titleLabel: Text = Text("").apply {
        id = "overlay-window-title-text"
        addClass("gui-overlay-window-title-text fw-semibold text-truncate")
        fontWeight = FontWeight.SEMIBOLD
        textOverflow = TextOverflow.ELLIPSIS
    }

    val closeButton: StyledButton = createWindowButton(
        id = "overlay-window-close-btn",
        className = "gui-overlay-titlebar-btn gui-overlay-titlebar-close",
        controlRole = "close",
        symbol = "✕"
    ) {
        val custom = onCloseAction
        if (custom != null) {
            custom.invoke()
        } else {
            resolveAttachedWindow()?.close()
        }
    }

    val minimizeButton: StyledButton = createWindowButton(
        id = "overlay-window-minimize-btn",
        className = "gui-overlay-titlebar-btn gui-overlay-titlebar-minimize",
        controlRole = "minimize",
        symbol = "—"
    ) {
        val custom = onMinimizeAction
        if (custom != null) {
            custom.invoke()
        } else {
            val win = resolveAttachedWindow()
            win?.minimize()
            syncFromWindow(win)
        }
    }

    val maximizeButton: StyledButton = createWindowButton(
        id = "overlay-window-maximize-btn",
        className = "gui-overlay-titlebar-btn gui-overlay-titlebar-maximize",
        controlRole = "maximize",
        symbol = "□"
    ) {
        val custom = onMaximizeRestoreAction
        if (custom != null) {
            custom.invoke()
        } else {
            toggleMaximizeRestore()
        }
    }

    val controlsContainer: HStack = HStack(gapPx = 6).apply {
        id = "overlay-window-controls"
        addClass("gui-overlay-window-controls")
        alignItems = AlignItems.CENTER
    }

    val leadingAccessorySlot: HStack = HStack(gapPx = 4).apply {
        id = "overlay-window-leading-accessories"
        addClass("gui-overlay-window-leading-accessories")
        alignItems = AlignItems.CENTER
    }

    val trailingAccessorySlot: HStack = HStack(gapPx = 4).apply {
        id = "overlay-window-trailing-accessories"
        addClass("gui-overlay-window-trailing-accessories")
        alignItems = AlignItems.CENTER
    }

    val leadingSlot: HStack = HStack(gapPx = 8).apply {
        id = "overlay-window-titlebar-leading"
        addClass("gui-overlay-titlebar-leading")
        alignItems = AlignItems.CENTER
    }

    val centerSlot: HStack = HStack(gapPx = 6).apply {
        id = "overlay-window-titlebar-center"
        addClass("gui-overlay-titlebar-center")
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.CENTER
    }

    val trailingSlot: HStack = HStack(gapPx = 6).apply {
        id = "overlay-window-titlebar-trailing"
        addClass("gui-overlay-titlebar-trailing")
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.END
    }

    init {
        OverlayExtension.ensureStyleSheetRegistered()
        id = "overlay-window-title-bar"
        addClass("gui-overlay-window-title-bar gui-overlay-titlebar")
        setAttribute("data-overlay-role", "window-title-bar")
        setDisplay(DisplayMode.FLEX)
        flexDirection = FlexDirection.ROW
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.BETWEEN

        addChild(leadingSlot)
        addChild(centerSlot)
        addChild(trailingSlot)

        installControlGroupHoverListeners()

        onPointerDown = { ev ->
            if (consumeChildButtonPress()) {
                isolateInputFromOverlay(isDoubleClick = false)
            } else {
                val isMultiClick = ev.clickCount >= 2
                isolateInputFromOverlay(isDoubleClick = isMultiClick)
                if (isWindowedTitleBarActive && ev.button == 0 && !isMultiClick) {
                    val customDrag = onDragMoveAction
                    if (customDrag != null) {
                        customDrag.invoke()
                    } else {
                        val win = resolveAttachedWindow()
                        if (win != null && !(win is Window && !win.isWindowShown)) {
                            win.startSystemMove()
                        }
                    }
                }
            }
        }
        onPointerUp = {
            inputRouter?.endIsolatedInteraction()
        }
        onClick = {
            isolateInputFromOverlay(isDoubleClick = false)
            inputRouter?.endIsolatedInteraction()
        }
        onDoubleClick = {
            try {
                if (consumeChildButtonPress()) {
                    isolateInputFromOverlay(isDoubleClick = true)
                } else {
                    isolateInputFromOverlay(isDoubleClick = true)
                    handleTitleBarDoubleClick()
                }
            } finally {
                inputRouter?.endIsolatedInteraction()
            }
        }
        onPointerEnter = {
            if (isWindowedTitleBarActive) {
                setNativeTitleBarHoverActive(true)
            }
        }
        onPointerLeave = {
            setNativeTitleBarHoverActive(false)
        }

        syncFromWindow(attachedWindow)
    }

    /**
     * Marks whether the pointer is currently hovering the native or overlay titlebar region,
     * pinning or unpinning [AutoHideController] with reason `"native-titlebar-hover"`.
     */
    fun setNativeTitleBarHoverActive(hovered: Boolean) {
        if (isDisposed) return
        nativeTitleBarHoverActive = hovered
        val expectedAttr = if (hovered) "true" else "false"
        if (getAttribute("data-native-titlebar-hover-pinned") != expectedAttr) {
            setAttribute("data-native-titlebar-hover-pinned", expectedAttr)
        }
        inputRouter?.autoHideController?.setPinned(NATIVE_TITLEBAR_HOVER_PIN_REASON, hovered)
    }

    fun bindWindow(window: IWindow?) {
        explicitBoundWindow = window
        syncFromWindow(window)
    }

    fun resolveAttachedWindow(): IWindow? {
        explicitBoundWindow?.let { return it }
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

    fun addLeadingAccessory(component: AbstractComponent) {
        isolateAccessoryInteractions(component)
        if (!leadingAccessorySlot.children.contains(component)) {
            leadingAccessorySlot.addChild(component)
        }
    }

    fun addTrailingAccessory(component: AbstractComponent) {
        isolateAccessoryInteractions(component)
        if (!trailingAccessorySlot.children.contains(component)) {
            trailingAccessorySlot.addChild(component)
        }
    }

    fun removeAccessory(component: AbstractComponent) {
        leadingAccessorySlot.removeChild(component)
        trailingAccessorySlot.removeChild(component)
    }

    fun handleTitleBarDoubleClick() {
        if (!isWindowedTitleBarActive) return
        val customMax = onMaximizeRestoreAction
        if (customMax != null) {
            customMax.invoke()
            return
        }
        val win = resolveAttachedWindow() ?: return
        if (!config.showMaximizeButton || !win.isMaximizable) return
        if (win.isFullscreen() || win.getWindowState() == WindowState.FULLSCREEN) {
            win.setFullscreen(false)
        } else if (win.getWindowState() == WindowState.MAXIMIZED) {
            win.restore()
        } else {
            win.maximize()
        }
        syncFromWindow(win)
    }

    fun toggleMaximizeRestore() {
        val win = resolveAttachedWindow() ?: return
        if (!win.isMaximizable) return
        if (win.isFullscreen() || win.getWindowState() == WindowState.FULLSCREEN) {
            win.setFullscreen(false)
        } else if (win.getWindowState() == WindowState.MAXIMIZED) {
            win.restore()
        } else {
            win.maximize()
        }
        syncFromWindow(win)
    }

    internal fun syncIfWindowStateChanged() {
        if (isSyncingFromWindow || isDisposed) return
        val w = resolveAttachedWindow()
        val fs = w?.isFullscreen()
        val ws = w?.getWindowState()
        val wt = w?.getTitle()
        val wf = w?.isWindowFocused
        val caps = w?.titleCapabilities
        val req = caps?.isTitleBarRequired
        val customBar = caps?.supportsCustomTitleBar
        val canEmbed = caps?.canEmbedComponentsInTitleBar
        val blur = caps?.prefersBlurredTranslucentOverlayTitleBar
        val usesNative = caps?.usesNativeOverlayTitleBarControls
        val defaultPlace = caps?.defaultControlsPlacement
        val cfgTheme = config.themeMode
        val effTheme = resolveEffectiveThemeMode()
        val expectedTitle = config.title ?: wt.orEmpty()
        val expectedShowTitle = config.showTitle && (caps?.supportsWindowTitle ?: true) && expectedTitle.isNotEmpty()
        val expectedTitleLabelVisible = !(usesNative == true) && expectedShowTitle
        if (
            w !== lastObservedWindowRef ||
            fs != lastObservedFullscreen ||
            ws != lastObservedWindowState ||
            wt != lastObservedTitle ||
            wf != lastObservedWindowFocused ||
            req != lastObservedTitleBarRequired ||
            customBar != lastObservedSupportsCustomTitleBar ||
            canEmbed != lastObservedCanEmbedComponents ||
            blur != lastObservedPrefersBlur ||
            usesNative != lastObservedUsesNativeControls ||
            defaultPlace != lastObservedControlsPlacement ||
            cfgTheme != lastObservedConfigThemeMode ||
            effTheme != lastObservedEffectiveTheme ||
            titleLabel.text != expectedTitle ||
            titleLabel.isVisible != expectedTitleLabelVisible
        ) {
            syncFromWindow(w)
        }
    }

    fun syncFromWindow(window: IWindow? = resolveAttachedWindow()): Boolean {
        if (isSyncingFromWindow) return super.isVisible
        isSyncingFromWindow = true
        try {
            val resolved = window ?: resolveAttachedWindow()
            if (!config.enabled &&
                appliedDisabledInactiveState &&
                !super.isVisible &&
                (heightPx ?: 0) == 0 &&
                lastObservedWindowRef === resolved
            ) {
                return false
            }
            val caps = resolved?.titleCapabilities
            val effectiveTheme = resolveEffectiveThemeMode()
            val isDark = (effectiveTheme != ThemeMode.LIGHT)
            val windowFocused = resolved?.isWindowFocused ?: true
            lastSyncedWindow = resolved
            lastObservedWindowRef = resolved
            lastObservedFullscreen = resolved?.isFullscreen()
            lastObservedWindowState = resolved?.getWindowState()
            lastObservedTitle = resolved?.getTitle()
            lastObservedWindowFocused = resolved?.isWindowFocused
            lastObservedTitleBarRequired = caps?.isTitleBarRequired
            lastObservedSupportsCustomTitleBar = caps?.supportsCustomTitleBar
            lastObservedCanEmbedComponents = caps?.canEmbedComponentsInTitleBar
            lastObservedPrefersBlur = caps?.prefersBlurredTranslucentOverlayTitleBar
            lastObservedUsesNativeControls = caps?.usesNativeOverlayTitleBarControls
            lastObservedControlsPlacement = caps?.defaultControlsPlacement
            lastObservedConfigThemeMode = config.themeMode
            lastObservedEffectiveTheme = effectiveTheme
            attachFullscreenObserverIfNeeded(resolved)

            val prevActive = super.isVisible && (heightPx ?: 0) > 0
            val prevEffectiveH = if (prevActive) (heightPx ?: 0) else 0

            val active = evaluateWindowedTitleBarActive(resolved)
            val placement = resolvePlacement(resolved)
            val prefersBlur = caps?.prefersBlurredTranslucentOverlayTitleBar == true
            val usesNativeControls = caps?.usesNativeOverlayTitleBarControls == true
            val styleModeChanged = appliedBlurredStyle != prefersBlur || appliedEffectiveTheme != effectiveTheme
            val barHeight = resolveBarHeightPx(resolved)
            val isMaximized = resolved?.getWindowState() == WindowState.MAXIMIZED

            applyPlacementAndButtonStyles(placement, barHeight, styleModeChanged, isMaximized, isDark)
            applyBarVisualStyle(prefersBlur, effectiveTheme)
            applyWindowFocusStyle(windowFocused)

            val resolvedTitle = config.title ?: resolved?.getTitle().orEmpty()
            if (titleLabel.text != resolvedTitle) {
                titleLabel.text = resolvedTitle
            }
            val showTitle = config.showTitle && (caps?.supportsWindowTitle ?: true) && resolvedTitle.isNotEmpty()
            titleLabel.setAttribute("data-title-text", resolvedTitle)
            titleLabel.setAttribute("data-title-visible", showTitle.toString())

            val showClose = config.showCloseButton && (resolved?.isClosable ?: true) && (caps?.supportsCloseButton ?: true)
            val showMinimize = config.showMinimizeButton && (resolved?.isMinimizable ?: true) && (caps?.supportsMinimizeButton ?: true)
            val showMaximize = config.showMaximizeButton && (resolved?.isMaximizable ?: true) && (caps?.supportsMaximizeButton ?: true)

            if (appliedUsesNativeControls != usesNativeControls) {
                appliedUsesNativeControls = usesNativeControls
                toggleClass("gui-overlay-titlebar-native-controls", usesNativeControls)
                reapplyStyleCascadeToTitleBarDescendants()
            }
            setAttribute("data-titlebar-enabled", config.enabled.toString())
            setAttribute("data-native-titlebar-controls", usesNativeControls.toString())
            setAttribute("data-titlebar-show-close", showClose.toString())
            setAttribute("data-titlebar-show-minimize", showMinimize.toString())
            setAttribute("data-titlebar-show-maximize", showMaximize.toString())
            setAttribute("data-titlebar-show-title", showTitle.toString())
            setAttribute("data-titlebar-title", resolvedTitle)

            if (usesNativeControls) {
                if (controlsContainer.isVisible) controlsContainer.isVisible = false
                if (closeButton.isVisible) closeButton.isVisible = false
                if (minimizeButton.isVisible) minimizeButton.isVisible = false
                if (maximizeButton.isVisible) maximizeButton.isVisible = false
                if (titleLabel.isVisible) titleLabel.isVisible = false
            } else {
                if (!controlsContainer.isVisible) controlsContainer.isVisible = true
                if (titleLabel.isVisible != showTitle) titleLabel.isVisible = showTitle
                if (closeButton.isVisible != showClose) closeButton.isVisible = showClose
                if (minimizeButton.isVisible != showMinimize) minimizeButton.isVisible = showMinimize
                if (maximizeButton.isVisible != showMaximize) maximizeButton.isVisible = showMaximize
            }

            setAttribute("data-controls-placement", placement.name)
            setAttribute("data-blurred-vibrancy", prefersBlur.toString())
            setAttribute("data-window-state", (resolved?.getWindowState() ?: WindowState.NORMAL).name)
            setAttribute("data-titlebar-active", active.toString())
            setAttribute("data-titlebar-theme", config.themeMode.bsThemeValue)
            setAttribute("data-titlebar-effective-theme", effectiveTheme.bsThemeValue)

            if (active) {
                removeClass("gui-overlay-titlebar-inactive")
                if (!super.isVisible) {
                    super.isVisible = true
                }
                removeAttribute("visibilityHidden")
                heightPx = barHeight
                if (barHeight != OverlayWindowTitleBarConfig.DEFAULT_HEIGHT_PX) {
                    style.setHeight(CssLength.px(barHeight))
                    style.setMinHeight(CssLength.px(barHeight))
                    style.setMaxHeight(CssLength.px(barHeight))
                } else {
                    style.setHeight(null)
                    style.setMinHeight(null)
                    style.setMaxHeight(null)
                }
                setAttribute("data-height-px", barHeight.toString())
            } else {
                setNativeTitleBarHoverActive(false)
                addClass("gui-overlay-titlebar-inactive")
                if (super.isVisible) {
                    super.isVisible = false
                }
                heightPx = 0
                style.setHeight(null)
                style.setMinHeight(null)
                style.setMaxHeight(null)
                setAttribute("data-height-px", "0")
            }

            appliedDisabledInactiveState = !config.enabled && !active
            val newEffectiveH = if (active) barHeight else 0
            if (prevActive != active || prevEffectiveH != newEffectiveH) {
                onVisibilityOrHeightChanged?.invoke(active, newEffectiveH)
            }
            return active
        } finally {
            isSyncingFromWindow = false
        }
    }

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        syncFromWindow()
    }

    protected override fun onAncestorHierarchyChanged() {
        super.onAncestorHierarchyChanged()
        syncFromWindow()
    }

    override fun dispose() {
        if (nativeTitleBarHoverActive) {
            nativeTitleBarHoverActive = false
            setAttribute("data-native-titlebar-hover-pinned", "false")
            inputRouter?.autoHideController?.setPinned(NATIVE_TITLEBAR_HOVER_PIN_REASON, false)
        }
        detachFullscreenObserver()
        detachTitleObserver()
        detachFocusObserver()
        clearChildButtonPressTimer()
        onVisibilityOrHeightChanged = null
        inputRouter = null
        explicitBoundWindow = null
        lastSyncedWindow = null
        super.dispose()
    }

    private fun attachFullscreenObserverIfNeeded(window: IWindow?) {
        if (window !== observedTitleWindow) {
            detachTitleObserver()
            observedTitleWindow = window
            window?.addOnWindowTitleChanged(observedTitleChangedListener)
        }
        if (window !== observedFocusWindow) {
            detachFocusObserver()
            observedFocusWindow = window
            window?.addOnWindowFocusChanged(observedWindowFocusChangedListener)
        }
        val target = window as? AbstractComponent
        if (target === observedFullscreenComponent) return
        detachFullscreenObserver()
        if (target != null && !target.isDisposed) {
            previousObservedFullscreenListener = target.onFullscreenChanged
            target.onFullscreenChanged = observedFullscreenForwarder
            observedFullscreenComponent = target
        }
    }

    private fun detachTitleObserver() {
        observedTitleWindow?.removeOnWindowTitleChanged(observedTitleChangedListener)
        observedTitleWindow = null
    }

    private fun detachFocusObserver() {
        observedFocusWindow?.removeOnWindowFocusChanged(observedWindowFocusChangedListener)
        observedFocusWindow = null
    }

    private fun detachFullscreenObserver() {
        val prevTarget = observedFullscreenComponent
        val prevListener = previousObservedFullscreenListener
        observedFullscreenComponent = null
        previousObservedFullscreenListener = null
        if (prevTarget != null && prevTarget.onFullscreenChanged === observedFullscreenForwarder) {
            prevTarget.onFullscreenChanged = prevListener
        }
    }

    private fun evaluateWindowedTitleBarActive(window: IWindow?): Boolean {
        if (!config.enabled || window == null) return false
        val caps = window.titleCapabilities
        if (!caps.isTitleBarRequired || !caps.supportsCustomTitleBar || !caps.canEmbedComponentsInTitleBar) {
            return false
        }
        if (window.isFullscreen() || window.getWindowState() == WindowState.FULLSCREEN) return false
        return true
    }

    private fun resolveBarHeightPx(window: IWindow?): Int {
        val usesNative = window?.titleCapabilities?.usesNativeOverlayTitleBarControls == true
        return if (usesNative && config.heightPx == OverlayWindowTitleBarConfig.DEFAULT_HEIGHT_PX) {
            MACOS_NATIVE_HEIGHT_PX
        } else {
            config.heightPx.coerceAtLeast(1)
        }
    }

    private fun resolvePlacement(window: IWindow?): TitleBarControlsPlacement =
        when (config.controlsPlacement) {
            TitleBarControlsPlacement.PLATFORM_DEFAULT ->
                window?.titleCapabilities?.defaultControlsPlacement ?: TitleBarControlsPlacement.TRAILING
            else -> config.controlsPlacement
        }

    private fun resolveEffectiveThemeMode(): ThemeMode = when (config.themeMode) {
        ThemeMode.DARK -> ThemeMode.DARK
        ThemeMode.LIGHT -> ThemeMode.LIGHT
        ThemeMode.AUTO -> {
            val attached = resolveAttachedWindow()
            val attachedAppTheme = Application.findApplicationForWindow(attached)?.getResolvedTheme()
                ?: Application.current?.getResolvedTheme()
            val explicitOverride = SystemThemeDetector.overrideDetectedTheme?.takeIf { !it.isAuto }
            when {
                attachedAppTheme == ThemeMode.LIGHT -> ThemeMode.LIGHT
                attachedAppTheme == ThemeMode.DARK -> ThemeMode.DARK
                explicitOverride != null -> explicitOverride
                else -> resolveSurfaceFallbackThemeMode(attached)
            }
        }
    }

    private fun resolveSurfaceFallbackThemeMode(attached: IWindow?): ThemeMode {
        var cur: AbstractComponent? = parent
        while (cur != null) {
            val hostContent = (cur as? OverlayHostBox)?.contentComponent
            val candidateBg = hostContent?.let {
                it.getStyle().backgroundColor ?: it.customBackgroundColor ?: it.computedStyle?.backgroundColor
            } ?: (cur.getStyle().backgroundColor ?: cur.customBackgroundColor ?: cur.computedStyle?.backgroundColor)
            if (candidateBg != null && candidateBg.a >= 200) {
                return if (ThemeConfig.relativeLuminance(candidateBg) >= 180) {
                    ThemeMode.LIGHT
                } else {
                    ThemeMode.DARK
                }
            }
            cur = cur.parent
        }
        val rootContent = (attached as? Window)?.getContent()
        val rootBg = rootContent?.let {
            it.getStyle().backgroundColor ?: it.customBackgroundColor ?: it.computedStyle?.backgroundColor
        }
        if (rootBg != null && rootBg.a >= 200 && ThemeConfig.relativeLuminance(rootBg) >= 180) {
            return ThemeMode.LIGHT
        }
        return ThemeMode.DARK
    }

    private fun applyBarVisualStyle(prefersBlur: Boolean, effectiveTheme: ThemeMode) {
        val isDark = effectiveTheme != ThemeMode.LIGHT
        val titleFg = if (isDark) DARK_TITLE_TEXT_COLOR else LIGHT_TITLE_TEXT_COLOR
        titleLabel.setAttribute("data-title-color", titleFg.toCssRgbaString())

        if (appliedBlurredStyle == prefersBlur && appliedEffectiveTheme == effectiveTheme) return
        appliedBlurredStyle = prefersBlur
        appliedEffectiveTheme = effectiveTheme
        if (prefersBlur) {
            removeClass("gui-overlay-titlebar-flat")
            addClass("gui-overlay-titlebar-blurred")
        } else {
            removeClass("gui-overlay-titlebar-blurred")
            addClass("gui-overlay-titlebar-flat")
        }
        if (isDark) {
            removeClass("gui-overlay-titlebar-light")
            addClass("gui-overlay-titlebar-dark")
        } else {
            removeClass("gui-overlay-titlebar-dark")
            addClass("gui-overlay-titlebar-light")
        }
    }

    private fun applyWindowFocusStyle(focused: Boolean) {
        lastObservedWindowFocused = focused
        if (appliedWindowFocused == focused) return
        appliedWindowFocused = focused
        toggleClass("gui-overlay-titlebar-unfocused", !focused)
        setAttribute("data-window-focused", focused.toString())
    }

    private fun applyPlacementAndButtonStyles(
        placement: TitleBarControlsPlacement,
        barHeight: Int,
        styleModeChanged: Boolean,
        isMaximized: Boolean,
        isDark: Boolean
    ) {
        val placementChanged = appliedPlacement != placement
        val heightChanged = appliedHeightPx != barHeight
        val maxStateChanged = appliedMaximizedState != isMaximized

        if (placementChanged) {
            appliedPlacement = placement
            controlsContainer.clearChildren()
            leadingSlot.clearChildren()
            centerSlot.clearChildren()
            trailingSlot.clearChildren()

            // Reparent controlsContainer, buttons, and slots FIRST before toggling placement classes
            // so handleCustomClassesChanged cascades stylesheet rules to all attached descendants.
            when (placement) {
                TitleBarControlsPlacement.LEADING -> {
                    controlsContainer.gapPx = 8
                    controlsContainer.addChild(closeButton)
                    controlsContainer.addChild(minimizeButton)
                    controlsContainer.addChild(maximizeButton)

                    leadingSlot.addChild(controlsContainer)
                    leadingSlot.addChild(leadingAccessorySlot)

                    centerSlot.isVisible = true
                    titleLabel.textAlignment = TextAlignment.CENTER
                    centerSlot.addChild(titleLabel)

                    trailingSlot.addChild(trailingAccessorySlot)

                    removeClass("gui-overlay-titlebar-trailing-placement")
                    addClass("gui-overlay-titlebar-leading-placement")
                }
                else -> {
                    controlsContainer.gapPx = 0
                    controlsContainer.addChild(minimizeButton)
                    controlsContainer.addChild(maximizeButton)
                    controlsContainer.addChild(closeButton)

                    titleLabel.textAlignment = TextAlignment.START
                    leadingSlot.addChild(leadingAccessorySlot)
                    leadingSlot.addChild(titleLabel)

                    centerSlot.isVisible = false

                    trailingSlot.addChild(trailingAccessorySlot)
                    trailingSlot.addChild(controlsContainer)

                    removeClass("gui-overlay-titlebar-leading-placement")
                    addClass("gui-overlay-titlebar-trailing-placement")
                }
            }

            reapplyStyleCascadeToTitleBarDescendants()
        }

        if (placementChanged || heightChanged || styleModeChanged || maxStateChanged) {
            appliedHeightPx = barHeight
            appliedMaximizedState = isMaximized
            syncControlButtonGlyphs(placement, isMaximized)
        }
    }

    private fun reapplyStyleCascadeToTitleBarDescendants() {
        val activeApp = Application.current
        if (activeApp != null) {
            val viewportW = widthPx?.takeIf { it > 0 } ?: 1280
            val needsAppStyle = !isStyledForCurrentTheme() ||
                !controlsContainer.isStyledForCurrentTheme() ||
                !closeButton.isStyledForCurrentTheme() ||
                !minimizeButton.isStyledForCurrentTheme() ||
                !maximizeButton.isStyledForCurrentTheme() ||
                !centerSlot.isStyledForCurrentTheme() ||
                !titleLabel.isStyledForCurrentTheme()
            if (needsAppStyle) {
                activeApp.applyActiveStyleSheetsTo(this, viewportWidthPx = viewportW, refreshThemeDependents = false)
            }
        }
    }

    private fun installControlGroupHoverListeners() {
        val prevContainerEnter = controlsContainer.onPointerEnter
        val prevContainerLeave = controlsContainer.onPointerLeave
        controlsContainer.onPointerEnter = { ev ->
            if (!isDisposed) {
                controlsContainerHovered = true
                updateControlGroupHoverState()
            }
            prevContainerEnter?.invoke(ev)
        }
        controlsContainer.onPointerLeave = { ev ->
            if (!isDisposed) {
                controlsContainerHovered = false
                if (!closeButton.isHovered && !minimizeButton.isHovered && !maximizeButton.isHovered) {
                    hoveredControlButtons.clear()
                }
                updateControlGroupHoverState()
            }
            prevContainerLeave?.invoke(ev)
        }

        for (btn in listOf(closeButton, minimizeButton, maximizeButton)) {
            val prevEnter = btn.onPointerEnter
            val prevLeave = btn.onPointerLeave
            btn.onPointerEnter = { ev ->
                if (!isDisposed) {
                    hoveredControlButtons.add(btn)
                    updateControlGroupHoverState()
                }
                prevEnter?.invoke(ev)
            }
            btn.onPointerLeave = { ev ->
                if (!isDisposed) {
                    hoveredControlButtons.remove(btn)
                    updateControlGroupHoverState()
                }
                prevLeave?.invoke(ev)
            }
        }
    }

    private fun isControlGroupHovered(): Boolean =
        controlsContainerHovered ||
            hoveredControlButtons.isNotEmpty() ||
            controlsContainer.isHovered ||
            closeButton.isHovered ||
            minimizeButton.isHovered ||
            maximizeButton.isHovered

    private fun updateControlGroupHoverState() {
        val placement = appliedPlacement ?: resolvePlacement(resolveAttachedWindow())
        val isMaximized = appliedMaximizedState ?: (resolveAttachedWindow()?.getWindowState() == WindowState.MAXIMIZED)
        syncControlButtonGlyphs(placement, isMaximized)
    }

    private fun syncControlButtonGlyphs(placement: TitleBarControlsPlacement, isMaximized: Boolean) {
        val useTrafficLights = placement == TitleBarControlsPlacement.LEADING
        if (useTrafficLights) {
            val hovered = isControlGroupHovered()
            controlsContainer.toggleClass("gui-overlay-titlebar-controls-hovered", hovered)
            controlsContainer.setAttribute("data-controls-hovered", hovered.toString())
            configureTrafficLightButton(closeButton, "×", showGlyph = hovered)
            configureTrafficLightButton(minimizeButton, "–", showGlyph = hovered)
            configureTrafficLightButton(maximizeButton, if (isMaximized) "–" else "+", showGlyph = hovered)
        } else {
            controlsContainer.removeClass("gui-overlay-titlebar-controls-hovered")
            controlsContainer.setAttribute("data-controls-hovered", "false")
            configureDesktopCaptionButton(minimizeButton, "—")
            configureDesktopCaptionButton(maximizeButton, if (isMaximized) "❐" else "□")
            configureDesktopCaptionButton(closeButton, "✕")
        }
    }

    private fun configureTrafficLightButton(
        button: StyledButton,
        glyph: String,
        showGlyph: Boolean
    ) {
        button.text = if (showGlyph) glyph else ""
        button.setAttribute("data-traffic-light-glyph", glyph)
    }

    private fun configureDesktopCaptionButton(
        button: StyledButton,
        glyph: String
    ) {
        button.text = glyph
    }

    private fun createWindowButton(
        id: String,
        className: String,
        controlRole: String,
        symbol: String,
        action: () -> Unit
    ): StyledButton = StyledButton(
        text = symbol,
        icon = null,
        type = BootstrapType.NONE,
        size = BootstrapSize.SM,
        outline = false
    ).apply {
        this.id = id
        addClass(className)
        setAttribute("data-window-control", controlRole)
        onPointerDown = {
            markChildButtonPressed(fromPointerOrDoubleClick = true)
            isolateInputFromOverlay(isDoubleClick = false)
        }
        onPointerUp = {
            inputRouter?.endIsolatedInteraction()
        }
        onDoubleClick = {
            markChildButtonPressed(fromPointerOrDoubleClick = true)
            isolateInputFromOverlay(isDoubleClick = true)
            inputRouter?.endIsolatedInteraction()
        }
        onClick = {
            markChildButtonPressed(fromPointerOrDoubleClick = false)
            isolateInputFromOverlay(isDoubleClick = false)
            try {
                action()
            } finally {
                inputRouter?.endIsolatedInteraction()
            }
        }
    }

    private fun isolateAccessoryInteractions(component: AbstractComponent) {
        val prevDown = component.onPointerDown
        val prevUp = component.onPointerUp
        val prevClick = component.onClick
        val prevDbl = component.onDoubleClick
        component.onPointerDown = { ev ->
            markChildButtonPressed(fromPointerOrDoubleClick = true)
            isolateInputFromOverlay(isDoubleClick = false)
            prevDown?.invoke(ev)
        }
        component.onPointerUp = { ev ->
            try {
                prevUp?.invoke(ev)
            } finally {
                inputRouter?.endIsolatedInteraction()
            }
        }
        component.onClick = {
            markChildButtonPressed(fromPointerOrDoubleClick = false)
            isolateInputFromOverlay(isDoubleClick = false)
            try {
                prevClick?.invoke()
            } finally {
                inputRouter?.endIsolatedInteraction()
            }
        }
        component.onDoubleClick = {
            markChildButtonPressed(fromPointerOrDoubleClick = true)
            isolateInputFromOverlay(isDoubleClick = true)
            try {
                prevDbl?.invoke()
            } finally {
                inputRouter?.endIsolatedInteraction()
            }
        }
    }

    private fun isolateInputFromOverlay(isDoubleClick: Boolean) {
        inputRouter?.notifyIsolatedInteraction(isDoubleClick = isDoubleClick, wakeHud = true)
    }

    private fun markChildButtonPressed(fromPointerOrDoubleClick: Boolean = true) {
        if (fromPointerOrDoubleClick || GenericUI.activeFactory?.backendTarget?.isBrowser == true) {
            suppressBarPressFromChildButton = true
            clearChildButtonPressTimer()
            suppressBarPressResetTimerId = UiTimerScheduler.setTimeout(0) {
                suppressBarPressResetTimerId = -1
                suppressBarPressFromChildButton = false
            }
        }
    }

    private fun consumeChildButtonPress(): Boolean {
        if (!suppressBarPressFromChildButton) return false
        suppressBarPressFromChildButton = false
        clearChildButtonPressTimer()
        return true
    }

    private fun clearChildButtonPressTimer() {
        val timerId = suppressBarPressResetTimerId
        suppressBarPressResetTimerId = -1
        if (timerId > 0) {
            UiTimerScheduler.clearTimer(timerId)
        }
    }

    companion object {
        private const val NATIVE_TITLEBAR_HOVER_PIN_REASON: String = "native-titlebar-hover"
        const val MACOS_BACKDROP_BLUR_PX: Float = 22f
        const val MACOS_NATIVE_HEIGHT_PX: Int = 28
        val MACOS_BAR_BACKGROUND: RgbaColor = RgbaColor(16, 18, 24, 56)
        val MACOS_HAIRLINE_COLOR: RgbaColor = RgbaColor(255, 255, 255, 41)
        val TRANSLUCENT_DARK_BACKGROUND: RgbaColor = RgbaColor(16, 18, 24, 107)
        val TRANSLUCENT_DARK_HAIRLINE: RgbaColor = RgbaColor(255, 255, 255, 24)
        val DARK_TITLE_TEXT_COLOR: RgbaColor = RgbaColor.WHITE
        val DARK_CAPTION_GLYPH_COLOR: RgbaColor = RgbaColor(242, 245, 250, 245)
        val LIGHT_BLUR_BAR_BACKGROUND: RgbaColor = RgbaColor(244, 246, 250, 71)
        val TRANSLUCENT_LIGHT_BACKGROUND: RgbaColor = RgbaColor(244, 246, 250, 122)
        val LIGHT_HAIRLINE_COLOR: RgbaColor = RgbaColor(0, 0, 0, 28)
        val TRANSLUCENT_LIGHT_HAIRLINE: RgbaColor = RgbaColor(0, 0, 0, 24)
        val LIGHT_TITLE_TEXT_COLOR: RgbaColor = RgbaColor(24, 26, 32, 245)
        val LIGHT_CAPTION_GLYPH_COLOR: RgbaColor = RgbaColor(32, 36, 44, 235)
    }
}
