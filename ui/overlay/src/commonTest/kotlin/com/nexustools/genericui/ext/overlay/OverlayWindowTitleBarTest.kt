package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.bootstrap.ShadowElevation
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextOverflow
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.TitleBarControlsPlacement
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.nativeimpl.content.TableNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockGroupNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockPanelNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWorkspaceNativeImpl
import com.nexustools.genericui.nativeimpl.dock.ToolPaletteNativeImpl
import com.nexustools.genericui.nativeimpl.document.BookContainerNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl
import com.nexustools.genericui.nativeimpl.form.CheckboxNativeImpl
import com.nexustools.genericui.nativeimpl.form.RangeSliderNativeImpl
import com.nexustools.genericui.nativeimpl.form.SelectNativeImpl
import com.nexustools.genericui.nativeimpl.form.StyledTextInputNativeImpl
import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.theme.SystemThemeDetector
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OverlayWindowTitleBarTest {

    private var savedActiveFactory: NativeFactory? = null

    @BeforeTest
    fun setUp() {
        savedActiveFactory = GenericUI.activeFactory
        Animator.cancelAllAnimations()
        UiTimerScheduler.clearAllTimers()
    }

    @AfterTest
    fun tearDown() {
        Animator.cancelAllAnimations()
        UiTimerScheduler.clearAllTimers()
        val restored = savedActiveFactory
        if (restored != null) {
            GenericUI.initialize(restored)
        } else {
            GenericUI.activeFactory = null
        }
    }

    private open class StubNativeImpl(
        override val componentClassPrefix: String
    ) : NativeImpl<Any> {
        override val nativeWidget: Any = Any()
        override var isDisposed: Boolean = false
        private var className: String = ""
        private var type: BootstrapType? = null
        private var outline: Boolean = false
        private var size: BootstrapSize? = null
        private var bgType: BootstrapType? = null
        private var textColorType: BootstrapType? = null
        private var borderType: BootstrapType? = null
        private var textAlign: TextAlignment? = null
        private var fontWeight: FontWeight? = null
        private var textTransform: TextTransform? = null
        private var textOverflow: TextOverflow? = null
        private var borderRadius: BorderRadiusStyle? = null
        private var shadow: ShadowElevation? = null
        private var display: DisplayMode? = null
        private var flexDir: FlexDirection? = null
        private var flexWrap: FlexWrap? = null
        private var justify: JustifyContent? = null
        private var alignItems: AlignItems? = null
        private var alignSelf: AlignSelf? = null
        private var overflow: OverflowBehavior? = null
        private var position: PositionMode? = null
        private var marginAll: SpacingScale? = null
        private var paddingAll: SpacingScale? = null
        private var visible: Boolean = true
        private var enabled: Boolean = true
        private var opacity: Float = 1f
        private var tooltip: String? = null

        override fun dispose() { isDisposed = true }
        override fun setClass(className: String) { this.className = className }
        override fun getClass(): String = className
        override fun addClass(className: String) {
            val parts = this.className.split(" ").filter { it.isNotBlank() }.toMutableSet()
            parts.add(className)
            this.className = parts.joinToString(" ")
        }
        override fun removeClass(className: String) {
            val parts = this.className.split(" ").filter { it.isNotBlank() && it != className }
            this.className = parts.joinToString(" ")
        }
        override fun hasClass(className: String): Boolean =
            this.className.split(" ").any { it == className }
        override fun setType(type: BootstrapType?) { this.type = type }
        override fun getType(): BootstrapType? = type
        override fun setOutline(outline: Boolean) { this.outline = outline }
        override fun isOutline(): Boolean = outline
        override fun setSize(size: BootstrapSize?) { this.size = size }
        override fun getSize(): BootstrapSize? = size
        override fun setBackgroundType(type: BootstrapType?) { bgType = type }
        override fun getBackgroundType(): BootstrapType? = bgType
        override fun setTextColorType(type: BootstrapType?) { textColorType = type }
        override fun getTextColorType(): BootstrapType? = textColorType
        override fun setBorderType(type: BootstrapType?) { borderType = type }
        override fun getBorderType(): BootstrapType? = borderType
        override fun setTextAlignment(alignment: TextAlignment?) { textAlign = alignment }
        override fun getTextAlignment(): TextAlignment? = textAlign
        override fun setFontWeight(weight: FontWeight?) { fontWeight = weight }
        override fun getFontWeight(): FontWeight? = fontWeight
        override fun setTextTransform(transform: TextTransform?) { textTransform = transform }
        override fun getTextTransform(): TextTransform? = textTransform
        override fun setTextOverflow(overflow: TextOverflow?) { textOverflow = overflow }
        override fun getTextOverflow(): TextOverflow? = textOverflow
        override fun setBorderRadius(radius: BorderRadiusStyle?) { borderRadius = radius }
        override fun getBorderRadius(): BorderRadiusStyle? = borderRadius
        override fun setShadow(shadow: ShadowElevation?) { this.shadow = shadow }
        override fun getShadow(): ShadowElevation? = shadow
        override fun setDisplay(display: DisplayMode?) { this.display = display }
        override fun getDisplay(): DisplayMode? = display
        override fun setFlexDirection(direction: FlexDirection?) { flexDir = direction }
        override fun getFlexDirection(): FlexDirection? = flexDir
        override fun setFlexWrap(wrap: FlexWrap?) { flexWrap = wrap }
        override fun getFlexWrap(): FlexWrap? = flexWrap
        override fun setJustifyContent(justify: JustifyContent?) { this.justify = justify }
        override fun getJustifyContent(): JustifyContent? = justify
        override fun setAlignItems(align: AlignItems?) { alignItems = align }
        override fun getAlignItems(): AlignItems? = alignItems
        override fun setAlignSelf(align: AlignSelf?) { alignSelf = align }
        override fun getAlignSelf(): AlignSelf? = alignSelf
        override fun setOverflow(overflow: OverflowBehavior?) { this.overflow = overflow }
        override fun getOverflow(): OverflowBehavior? = overflow
        override fun setPosition(position: PositionMode?) { this.position = position }
        override fun getPosition(): PositionMode? = position
        override fun setMarginAll(scale: SpacingScale?) { marginAll = scale }
        override fun getMarginAll(): SpacingScale? = marginAll
        override fun setPaddingAll(scale: SpacingScale?) { paddingAll = scale }
        override fun getPaddingAll(): SpacingScale? = paddingAll
        override fun setVisible(visible: Boolean) { this.visible = visible }
        override fun isVisible(): Boolean = visible
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun isEnabled(): Boolean = enabled
        override fun setOpacity(opacity: Float) { this.opacity = opacity }
        override fun getOpacity(): Float = opacity
        override fun setTooltipText(tooltip: String?) { this.tooltip = tooltip }
        override fun getTooltipText(): String? = tooltip
        override fun setPreferredSize(size: Size?) {}
        override fun getBounds(): Rect = Rect.ZERO
        override fun setPadding(padding: EdgeInsets) {}
        override fun setMargin(margin: EdgeInsets) {}
        override fun applyComputedStyle(style: ComputedStyle) {}
        override fun setOnClick(listener: (() -> Unit)?) {}
        override fun setOnDoubleClick(listener: (() -> Unit)?) {}
        override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) {}
        override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) {}
        override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) {}
    }

    private class FakeContainerNativeImpl(prefix: String) : StubNativeImpl(prefix), ContainerNativeImpl<Any> {
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
    }

    private class FakeStyledContainerNativeImpl(prefix: String) :
        StubNativeImpl(prefix), StyledContainerNativeImpl<Any> {
        private var visuals: StyledVisualSpec = StyledVisualSpec()
        override fun setStyledVisuals(visuals: StyledVisualSpec) { this.visuals = visuals }
        override fun getStyledVisuals(): StyledVisualSpec = visuals
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
    }

    private class FakeStyledComponentNativeImpl(prefix: String) :
        StubNativeImpl(prefix), StyledComponentNativeImpl<Any> {
        private var visuals: StyledVisualSpec = StyledVisualSpec()
        private var hover: StyledVisualSpec? = null
        private var focus: StyledVisualSpec? = null
        private var active: StyledVisualSpec? = null
        private var disabled: StyledVisualSpec? = null
        override fun setStyledVisuals(visuals: StyledVisualSpec) { this.visuals = visuals }
        override fun getStyledVisuals(): StyledVisualSpec = visuals
        override fun setHoverVisuals(visuals: StyledVisualSpec?) { hover = visuals }
        override fun getHoverVisuals(): StyledVisualSpec? = hover
        override fun setFocusVisuals(visuals: StyledVisualSpec?) { focus = visuals }
        override fun getFocusVisuals(): StyledVisualSpec? = focus
        override fun setActiveVisuals(visuals: StyledVisualSpec?) { active = visuals }
        override fun getActiveVisuals(): StyledVisualSpec? = active
        override fun setDisabledVisuals(visuals: StyledVisualSpec?) { disabled = visuals }
        override fun getDisabledVisuals(): StyledVisualSpec? = disabled
    }

    private class FakeTextNativeImpl(prefix: String) : StubNativeImpl(prefix), TextNativeImpl<Any> {
        private var value: String = ""
        private var maxLinesValue: Int? = null
        override fun setText(text: String) { value = text }
        override fun getText(): String = value
        override fun setMaxLines(maxLines: Int?) { maxLinesValue = maxLines }
        override fun getMaxLines(): Int? = maxLinesValue
    }

    private open class FakeButtonNativeImpl(prefix: String) : StubNativeImpl(prefix), ButtonNativeImpl<Any> {
        private var label: String = ""
        private var iconName: String? = null
        override fun setText(text: String) { label = text }
        override fun getText(): String = label
        override fun setIcon(icon: String?) { iconName = icon }
        override fun getIcon(): String? = iconName
    }

    private class FakeStyledButtonNativeImpl(prefix: String) :
        FakeButtonNativeImpl(prefix), StyledButtonNativeImpl<Any> {
        private var base: StyledVisualSpec = StyledVisualSpec()
        private var hover: StyledVisualSpec? = null
        private var focus: StyledVisualSpec? = null
        private var active: StyledVisualSpec? = null
        private var disabled: StyledVisualSpec? = null
        override fun setBaseVisuals(visuals: StyledVisualSpec) { base = visuals }
        override fun getBaseVisuals(): StyledVisualSpec = base
        override fun setHoverVisuals(visuals: StyledVisualSpec?) { hover = visuals }
        override fun getHoverVisuals(): StyledVisualSpec? = hover
        override fun setFocusVisuals(visuals: StyledVisualSpec?) { focus = visuals }
        override fun getFocusVisuals(): StyledVisualSpec? = focus
        override fun setActiveVisuals(visuals: StyledVisualSpec?) { active = visuals }
        override fun getActiveVisuals(): StyledVisualSpec? = active
        override fun setDisabledVisuals(visuals: StyledVisualSpec?) { disabled = visuals }
        override fun getDisabledVisuals(): StyledVisualSpec? = disabled
    }

    private class FakeSpinnerNativeImpl : StubNativeImpl("spinner"), SpinnerNativeImpl<Any> {
        override fun setGrowStyle(grow: Boolean) {}
        override fun setLabel(label: String?) {}
    }

    private class FakeImageNativeImpl : StubNativeImpl("image"), ImageNativeImpl<Any> {
        private var url: String? = null
        private var fit: ObjectFit = ObjectFit.CONTAIN
        override fun setSourceUrl(url: String?) { this.url = url }
        override fun getSourceUrl(): String? = url
        override fun setFallbackText(fallback: String?) {}
        override fun setObjectFit(fit: ObjectFit) { this.fit = fit }
        override fun getObjectFit(): ObjectFit = fit
    }

    private class FakeWindowNativeImpl : StubNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = true
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        override val supportsNavbarMenuBar: Boolean = true
        override val supportsMobileNavbarMenuBar: Boolean = false
        private var title: String = "GenericUI"
        private var windowSize: Size = Size(800, 600)
        private var windowState: WindowState = WindowState.NORMAL
        private var fullscreen: Boolean = false
        private var undecorated: Boolean = false
        var startSystemMoveCount: Int = 0

        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setWindowSize(size: Size) { windowSize = size }
        override fun getWindowSize(): Size = windowSize
        override fun setWindowState(state: WindowState) {
            windowState = state
            fullscreen = (state == WindowState.FULLSCREEN)
        }
        override fun getWindowState(): WindowState = windowState
        override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
            this.fullscreen = fullscreen
            if (fullscreen) {
                windowState = WindowState.FULLSCREEN
            } else if (windowState == WindowState.FULLSCREEN) {
                windowState = WindowState.NORMAL
            }
        }
        override fun isFullscreen(): Boolean = fullscreen
        override fun setFullscreenElement(elementImpl: NativeImpl<*>?, cutoutMode: DisplayCutoutMode) {
            setFullscreen(elementImpl != null, cutoutMode)
        }
        override fun applyTitleBarInstructions(titleBar: TitleBar?) {}
        override fun setMenuBarNative(menuBarImpl: MenuBarNativeImpl<*>?) {}
        override fun setNavBarNative(navBarImpl: NativeImpl<*>?) {}
        override fun setBottomNavigationBarNative(bottomNavImpl: NativeImpl<*>?) {}
        override fun isUndecorated(): Boolean = undecorated
        override fun setUndecorated(undecorated: Boolean) { this.undecorated = undecorated }
        override fun startSystemMove() { startSystemMoveCount++ }
        override fun showWindow() {}
        override fun closeWindow() {}
        override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {}
        override fun setOnWindowStateChanged(listener: ((WindowState) -> Unit)?) {}
        override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {}
        override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {}
        override fun configureAsToolModalWindow(
            ownerWindow: WindowNativeImpl<*>?,
            showInTaskbar: Boolean,
            minimizable: Boolean,
            alwaysOnTopOfOwner: Boolean
        ) {}
    }

    private class FakeOverlayWindowTestFactory : NativeFactory {
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in OverlayWindowTitleBarTest")

        override val backendTarget: BackendTarget = BackendTarget.Swing
        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> = FakeWindowNativeImpl()
        override fun createMenuBar(): MenuBarNativeImpl<*> = unsupported()
        override fun createMenu(): MenuNativeImpl<*> = unsupported()
        override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> = unsupported()
        override fun createContextMenu(): ContextMenuNativeImpl<*> = unsupported()
        override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = unsupported()
        override fun createDockArea(): DockAreaNativeImpl<*> = unsupported()
        override fun createDockGroup(): DockGroupNativeImpl<*> = unsupported()
        override fun createDockPanel(): DockPanelNativeImpl<*> = unsupported()
        override fun createDockWindow(): DockWindowNativeImpl<*> = unsupported()
        override fun createToolPalette(): ToolPaletteNativeImpl<*> = unsupported()
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = FakeContainerNativeImpl(classPrefix)
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> =
            FakeStyledContainerNativeImpl(classPrefix)
        override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> =
            FakeStyledComponentNativeImpl(classPrefix)
        override fun createBookContainer(): BookContainerNativeImpl<*> = unsupported()
        override fun createScrollView(): ScrollViewNativeImpl<*> = unsupported()
        override fun createDivider(): DividerNativeImpl<*> = unsupported()
        override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = FakeTextNativeImpl(classPrefix)
        override fun createMarkdownView(): MarkdownViewNativeImpl<*> = unsupported()
        override fun createImage(): ImageNativeImpl<*> = FakeImageNativeImpl()
        override fun createTable(): TableNativeImpl<*> = unsupported()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = FakeButtonNativeImpl(classPrefix)
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> =
            FakeStyledButtonNativeImpl(classPrefix)
        override fun createDropdown(): DropdownNativeImpl<*> = unsupported()
        override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> = unsupported()
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String): StyledTextInputNativeImpl<*> = unsupported()
        override fun createSelect(): SelectNativeImpl<*> = unsupported()
        override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> = unsupported()
        override fun createRangeSlider(): RangeSliderNativeImpl<*> = unsupported()
        override fun createTab(closable: Boolean): TabNativeImpl<*> = unsupported()
        override fun createTabContainer(): TabContainerNativeImpl<*> = unsupported()
        override fun createNavbar(): NavbarNativeImpl<*> = unsupported()
        override fun createBottomNavigationBar(): BottomNavigationBarNativeImpl<*> = unsupported()
        override fun createProgressBar(): ProgressBarNativeImpl<*> = unsupported()
        override fun createSpinner(): SpinnerNativeImpl<*> = FakeSpinnerNativeImpl()
        override fun createPopover(): PopoverNativeImpl<*> = unsupported()
        override fun createDialogWindow(): DialogWindowNativeImpl<*> = unsupported()
        override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl = unsupported()
        override fun createStandardDialogs(): StandardDialogsNativeImpl = unsupported()
        override fun createLazyList(): LazyListNativeImpl<*> = unsupported()
        override fun createLazyGrid(): LazyGridNativeImpl<*> = unsupported()
        override fun createTreeView(): TreeViewNativeImpl<*> = unsupported()
        override fun createPermissionManager(): PermissionManagerNativeImpl = unsupported()
        override fun createSystemTray(): SystemTrayNativeImpl = unsupported()
        override fun createNotificationManager(): NotificationManagerNativeImpl = unsupported()
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = object : NetworkMonitorNativeImpl {
            override var isDisposed: Boolean = false
            override fun dispose() { isDisposed = true }
            override fun isOnline(): Boolean = true
            override fun setOnConnectivityChanged(listener: ((Boolean) -> Unit)?) {}
        }
        override fun supportsTcp(): Boolean = false
        override fun supportsUdp(): Boolean = false
    }

    private fun createTestWindow(
        title: String = "Demo Player",
        capabilities: TitleCapabilities = TitleCapabilities(
            isTitleBarRequired = true,
            defaultControlsPlacement = TitleBarControlsPlacement.TRAILING,
            prefersBlurredTranslucentOverlayTitleBar = false
        ),
        onSystemMove: () -> Unit = {},
        onMinimize: () -> Unit = {},
        onMaximize: () -> Unit = {},
        onRestore: () -> Unit = {},
        onClose: () -> Unit = {}
    ): Window = object : Window(title = title) {
        override val titleCapabilities: TitleCapabilities = capabilities

        override fun startSystemMove() {
            onSystemMove()
            super.startSystemMove()
        }

        override fun minimize() {
            onMinimize()
            super.minimize()
        }

        override fun maximize() {
            onMaximize()
            super.maximize()
        }

        override fun restore() {
            onRestore()
            super.restore()
        }

        override fun close() {
            onClose()
            super.close()
        }
    }

    @Test
    fun testWindowedOnlyVisibilityAndFullscreenHidingAcrossDesktopAndNonDesktopWindows() {
        val videoSurface = Container("video-surface").apply {
            widthPx = 1280
            heightPx = 720
        }
        val host = OverlayHostBox(content = videoSurface).apply {
            windowTitleBarEnabled = true
        }

        // Detached: must stay hidden with 0px collapsed height
        assertFalse(host.windowTitleBar.isWindowedTitleBarActive)
        assertFalse(host.windowTitleBar.isVisible)
        assertEquals(0, host.windowTitleBar.effectiveHeightPx)

        // Non-desktop / browser tab (!isTitleBarRequired): must stay hidden
        val mobileOrWebWindow = createTestWindow(
            title = "Mobile Tab",
            capabilities = TitleCapabilities(isTitleBarRequired = false)
        )
        mobileOrWebWindow.setContent(host)
        host.windowTitleBar.syncFromWindow()
        assertFalse(host.windowTitleBar.isWindowedTitleBarActive)
        assertFalse(host.windowTitleBar.isVisible)
        assertEquals(0, host.windowTitleBar.effectiveHeightPx)

        // Desktop windowed: must become visible with configured 32px height
        val desktopWindow = createTestWindow(title = "Desktop Window")
        desktopWindow.setContent(host)
        assertTrue(host.windowTitleBar.isWindowedTitleBarActive)
        assertTrue(host.windowTitleBar.isVisible)
        assertEquals(32, host.windowTitleBar.effectiveHeightPx)
        assertEquals("Desktop Window", host.windowTitleBar.titleLabel.text)
        assertEquals("window-title-bar", host.windowTitleBar.getAttribute("data-overlay-role"))

        // Enter fullscreen -> strictly hidden with 0px height
        desktopWindow.setFullscreen(true)
        assertFalse(host.windowTitleBar.isWindowedTitleBarActive)
        assertFalse(host.windowTitleBar.isVisible)
        assertEquals(0, host.windowTitleBar.effectiveHeightPx)

        // WindowState.FULLSCREEN via setWindowState -> strictly hidden
        desktopWindow.setFullscreen(false)
        assertTrue(host.windowTitleBar.isVisible)
        desktopWindow.setWindowState(WindowState.FULLSCREEN)
        assertFalse(host.windowTitleBar.isWindowedTitleBarActive)
        assertFalse(host.windowTitleBar.isVisible)
        assertEquals(0, host.windowTitleBar.effectiveHeightPx)

        // Exit fullscreen back to normal -> restores visibility and 32px height
        desktopWindow.setWindowState(WindowState.NORMAL)
        assertTrue(host.windowTitleBar.isWindowedTitleBarActive)
        assertTrue(host.windowTitleBar.isVisible)
        assertEquals(32, host.windowTitleBar.effectiveHeightPx)

        desktopWindow.dispose()
        mobileOrWebWindow.dispose()
        host.dispose()
    }

    @Test
    fun testMacosBlurVibrancyLeadingTrafficLightsVsWindowsLinuxTrailingStyle() {
        val macWindow = createTestWindow(
            title = "macOS Video",
            capabilities = TitleCapabilities(
                isTitleBarRequired = true,
                defaultControlsPlacement = TitleBarControlsPlacement.LEADING,
                prefersBlurredTranslucentOverlayTitleBar = true
            )
        )
        val macHost = OverlayHostBox(content = Container("mac-video")).apply {
            windowTitleBarEnabled = true
        }
        macWindow.setContent(macHost)

        val macBar = macHost.windowTitleBar
        macHost.applyStyleSheet(OverlayExtension.defaultStyleSheet())
        assertTrue(macBar.isVisible)
        assertEquals(TitleBarControlsPlacement.LEADING, macBar.resolvedControlsPlacement)
        assertTrue(macBar.hasClass("gui-overlay-titlebar-blurred"))
        assertTrue(macBar.hasClass("gui-overlay-titlebar-dark"))
        assertTrue(macBar.hasClass("gui-overlay-titlebar-leading-placement"))
        assertEquals(CssLength.px(22f), macBar.computedStyle?.backdropFilter?.blur)
        assertEquals(1.4f, macBar.computedStyle?.backdropFilter?.saturate)
        assertEquals(173, macBar.computedStyle?.backgroundColor?.a)
        assertEquals(RgbaColor.WHITE, macBar.titleLabel.computedStyle?.textColor)
        assertEquals("close", macBar.closeButton.getAttribute("data-window-control"))
        assertEquals("minimize", macBar.minimizeButton.getAttribute("data-window-control"))
        assertEquals("maximize", macBar.maximizeButton.getAttribute("data-window-control"))
        assertEquals(
            listOf<Any>(macBar.closeButton, macBar.minimizeButton, macBar.maximizeButton),
            macBar.controlsContainer.children
        )
        assertTrue(macBar.centerSlot.children.contains(macBar.titleLabel))
        assertEquals("macOS Video", macBar.titleLabel.text)

        val winWindow = createTestWindow(
            title = "Windows Video",
            capabilities = TitleCapabilities(
                isTitleBarRequired = true,
                defaultControlsPlacement = TitleBarControlsPlacement.TRAILING,
                prefersBlurredTranslucentOverlayTitleBar = false
            )
        )
        val winHost = OverlayHostBox(content = Container("win-video")).apply {
            windowTitleBarEnabled = true
        }
        winWindow.setContent(winHost)

        val winBar = winHost.windowTitleBar
        winHost.applyStyleSheet(OverlayExtension.defaultStyleSheet())
        assertTrue(winBar.isVisible)
        assertEquals(TitleBarControlsPlacement.TRAILING, winBar.resolvedControlsPlacement)
        assertFalse(winBar.hasClass("gui-overlay-titlebar-blurred"))
        assertTrue(winBar.hasClass("gui-overlay-titlebar-flat"))
        assertTrue(winBar.hasClass("gui-overlay-titlebar-trailing-placement"))
        assertEquals(CssFilter.NONE, winBar.computedStyle?.backdropFilter)
        assertFalse(winBar.computedStyle?.backdropFilter?.hasBlur == true)
        assertFalse(winBar.computedStyle?.backdropFilter?.hasActiveEffects == true)
        assertTrue(macBar.computedStyle?.backdropFilter?.hasPixelOps == true)
        assertEquals(
            listOf<Any>(winBar.minimizeButton, winBar.maximizeButton, winBar.closeButton),
            winBar.controlsContainer.children
        )
        assertTrue(winBar.leadingSlot.children.contains(winBar.titleLabel))
        assertEquals("Windows Video", winBar.titleLabel.text)

        macWindow.dispose()
        macHost.dispose()
        winWindow.dispose()
        winHost.dispose()
    }

    @Test
    fun testTitleBarDragDoubleClickControlsAndOverlayInputIsolation() {
        var dragMoves = 0
        var minimizeCalls = 0
        var maximizeCalls = 0
        var restoreCalls = 0
        var closeCalls = 0
        var playPauseCalls = 0
        var fullscreenCalls = 0

        val factory = FakeOverlayWindowTestFactory()
        GenericUI.initialize(factory)

        val window = createTestWindow(
            title = "Interactive Stage",
            capabilities = TitleCapabilities(
                isTitleBarRequired = true,
                defaultControlsPlacement = TitleBarControlsPlacement.LEADING,
                prefersBlurredTranslucentOverlayTitleBar = true
            ),
            onSystemMove = { dragMoves++ },
            onMinimize = { minimizeCalls++ },
            onMaximize = { maximizeCalls++ },
            onRestore = { restoreCalls++ },
            onClose = { closeCalls++ }
        )

        val overlay = YoutubePlayerOverlay().apply {
            windowTitleBarEnabled = true
        }
        val host = OverlayHostBox(
            content = Container("stage-video").apply {
                widthPx = 1280
                heightPx = 720
            },
            initialOverlay = overlay
        )
        host.actions = OverlayActions(
            onPlayPause = { playPauseCalls++ },
            onToggleFullscreen = { fullscreenCalls++ }
        )
        window.setContent(host)
        window.show(factory)

        val bar = overlay.windowTitleBar
        assertSame(bar, host.windowTitleBar)
        assertTrue(bar.isVisible)

        // Bar pointerDown starts system move and isolates from video play/pause
        bar.onPointerDown?.invoke(PointerEvent(120f, 14f))
        assertEquals(1, dragMoves)
        bar.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(300)
        assertEquals(0, playPauseCalls)

        // Double click on bar maximizes then restores without triggering video fullscreen toggle
        bar.onDoubleClick?.invoke()
        assertEquals(1, maximizeCalls)
        assertEquals(WindowState.MAXIMIZED, window.getWindowState())
        assertEquals(0, fullscreenCalls)

        bar.onDoubleClick?.invoke()
        assertEquals(1, restoreCalls)
        assertEquals(WindowState.NORMAL, window.getWindowState())
        assertEquals(0, fullscreenCalls)

        // Caption button clicks trigger window actions without toggling overlay play/pause
        bar.minimizeButton.onClick?.invoke()
        assertEquals(1, minimizeCalls)
        assertEquals(WindowState.MINIMIZED, window.getWindowState())
        window.restore()

        bar.maximizeButton.onClick?.invoke()
        assertEquals(2, maximizeCalls)
        bar.maximizeButton.onClick?.invoke()
        assertEquals(3, restoreCalls) // includes manual window.restore() above
        UiTimerScheduler.advanceTimeBy(1)

        // Normal video click / double-click still routes to play/pause and fullscreen!
        overlay.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        assertEquals(1, playPauseCalls)

        overlay.onDoubleClick?.invoke()
        assertEquals(1, fullscreenCalls)

        bar.closeButton.onClick?.invoke()
        assertEquals(1, closeCalls)

        host.dispose()
    }

    @Test
    fun testDefaultTitleBarOverlayAutoHideAndYoutubeTopBarWindowedOffsetSync() {
        val window = createTestWindow(title = "Stage Window")
        val host = OverlayHostBox(
            content = Container("stage-video").apply {
                widthPx = 1280
                heightPx = 720
            }
        ).apply {
            windowTitleBarEnabled = true
        }
        window.setContent(host)

        val sheet = OverlayExtension.defaultStyleSheet()
        host.applyStyleSheet(sheet)

        // DefaultWindowTitleBarOverlay auto-mounts when overlay == null inside unified fading root
        val mountedDefault = host.overlayRoot.children.firstOrNull() as? DefaultWindowTitleBarOverlay
        assertTrue(mountedDefault != null)
        assertTrue(mountedDefault.hasClass("overlay-fading-root"))
        assertSame(mountedDefault, mountedDefault.autoHideController.targetComponent)
        assertSame(mountedDefault, host.windowTitleBar.parent)
        assertTrue(host.windowTitleBar.isVisible)
        assertEquals(1.0f, mountedDefault.computedStyle?.opacity ?: -1f, 0.001f)

        // Auto-hide toggles .hidden atomically on the unified fading root (no alpha^2 or sticky titlebar)
        mountedDefault.autoHideController.hideOverlay(instant = true)
        assertFalse(mountedDefault.autoHideController.isOverlayVisible)
        assertTrue(mountedDefault.hasClass(AutoHideController.HIDDEN_CLASS))
        assertEquals(0.0f, mountedDefault.computedStyle?.opacity ?: -1f, 0.001f)
        assertEquals(false, mountedDefault.computedStyle?.pointerEventsEnabled)
        assertEquals(1.0f, host.windowTitleBar.opacity, 0.001f)
        assertEquals(1.0f, host.persistentOverlayRoot.computedStyle?.opacity ?: -1f, 0.001f)

        mountedDefault.autoHideController.notifyUserActivity()
        assertTrue(mountedDefault.autoHideController.isOverlayVisible)
        assertFalse(mountedDefault.hasClass(AutoHideController.HIDDEN_CLASS))
        assertEquals(1.0f, mountedDefault.computedStyle?.opacity ?: -1f, 0.001f)
        assertEquals(true, mountedDefault.computedStyle?.pointerEventsEnabled)

        // Attaching YoutubePlayerOverlay embeds BOTH windowTitleBar and HUD controls under ONE fading root,
        // while captions remain un-faded in host.persistentOverlayRoot
        val ytOverlay = YoutubePlayerOverlay()
        host.attachOverlay(ytOverlay)
        ytOverlay.setSubtitleCueText("Captions survive atomic fade")
        host.applyStyleSheet(sheet)
        assertTrue(ytOverlay.hasClass("overlay-fading-root"))
        assertSame(ytOverlay, ytOverlay.autoHideController.targetComponent)
        assertSame(ytOverlay, ytOverlay.windowTitleBar.parent)
        assertSame(ytOverlay, ytOverlay.hudControlsContainer.parent)
        assertSame(host.persistentOverlayRoot, ytOverlay.subtitleBanner.parent)
        assertTrue(ytOverlay.windowTitleBarEnabled)
        assertTrue(ytOverlay.windowTitleBar.isVisible)
        assertEquals("32", ytOverlay.topBar.getAttribute("data-titlebar-offset-px"))
        assertEquals(CssLength.px(32), ytOverlay.topBar.style.marginTop)

        // Atomic hide adds .hidden to ytOverlay only (titlebar & HUD fade together once; persistentOverlayRoot stays opacity 1)
        ytOverlay.hideHud(instant = false)
        assertTrue(ytOverlay.hasClass(AutoHideController.HIDDEN_CLASS))
        assertFalse(ytOverlay.windowTitleBar.hasClass(AutoHideController.HIDDEN_CLASS))
        assertFalse(ytOverlay.hudControlsContainer.hasClass(AutoHideController.HIDDEN_CLASS))
        assertFalse(host.persistentOverlayRoot.hasClass(AutoHideController.HIDDEN_CLASS))
        assertEquals(0.0f, ytOverlay.computedStyle?.opacity ?: -1f, 0.001f)
        assertEquals("none", ytOverlay.computedStyle?.cursor)
        assertEquals(1.0f, ytOverlay.windowTitleBar.opacity, 0.001f)
        assertEquals(1.0f, ytOverlay.hudControlsContainer.opacity, 0.001f)
        assertEquals(1.0f, host.persistentOverlayRoot.computedStyle?.opacity ?: -1f, 0.001f)
        assertTrue(ytOverlay.subtitleBanner.isVisible)

        // Moving pointer over host while .hidden wakes the overlay cleanly
        host.onPointerMove?.invoke(PointerEvent(x = 120f, y = 80f))
        assertTrue(ytOverlay.isHudVisible)
        assertFalse(ytOverlay.hasClass(AutoHideController.HIDDEN_CLASS))
        assertEquals(1.0f, ytOverlay.computedStyle?.opacity ?: -1f, 0.001f)

        // Dynamic title and height updates propagate cleanly
        window.setTitle("Updated Stage Title")
        assertEquals("Updated Stage Title", ytOverlay.windowTitleBar.titleLabel.text)
        host.windowTitleBarConfig = OverlayWindowTitleBarConfig(enabled = true, heightPx = 40)
        assertEquals(40, ytOverlay.windowTitleBar.effectiveHeightPx)
        assertEquals("40", ytOverlay.topBar.getAttribute("data-titlebar-offset-px"))
        assertEquals(CssLength.px(40), ytOverlay.topBar.style.marginTop)

        // Entering fullscreen hides titlebar and collapses topBar offset to 0px
        window.setFullscreen(true)
        assertFalse(ytOverlay.windowTitleBar.isVisible)
        assertEquals(0, ytOverlay.windowTitleBar.effectiveHeightPx)
        assertEquals("0", ytOverlay.topBar.getAttribute("data-titlebar-offset-px"))
        assertEquals(CssLength.px(0), ytOverlay.topBar.style.marginTop)

        // Exiting fullscreen restores titlebar and 40px topBar offset
        window.setFullscreen(false)
        assertTrue(ytOverlay.windowTitleBar.isVisible)
        assertEquals(40, ytOverlay.windowTitleBar.effectiveHeightPx)
        assertEquals("40", ytOverlay.topBar.getAttribute("data-titlebar-offset-px"))
        assertEquals(CssLength.px(40), ytOverlay.topBar.style.marginTop)

        window.dispose()
        host.dispose()
    }

    @Test
    fun testUnsupportedLinuxWmKeepsOverlayTitleBarInactiveAndCollapsedToZero() {
        var mutableCaps = TitleCapabilities(
            isSupported = true,
            isTitleBarRequired = true,
            supportsCustomTitleBar = false,
            supportsCustomComponents = false
        )
        val linuxWindow = object : Window(title = "Unsupported Linux WM") {
            override val titleCapabilities: TitleCapabilities
                get() = mutableCaps
        }
        val ytOverlay = YoutubePlayerOverlay().apply {
            windowTitleBarEnabled = true
        }
        val host = OverlayHostBox(
            content = Container("linux-video").apply {
                widthPx = 1280
                heightPx = 720
            },
            initialOverlay = ytOverlay
        )
        linuxWindow.setContent(host)
        ytOverlay.syncWindowTitleBar(linuxWindow)

        val bar = ytOverlay.windowTitleBar
        host.applyStyleSheet(OverlayExtension.defaultStyleSheet())
        assertFalse(bar.isWindowedTitleBarActive)
        assertFalse(bar.isVisible)
        assertTrue(bar.hasClass("gui-overlay-titlebar-inactive"))
        assertEquals(0, bar.effectiveHeightPx)
        assertEquals("false", bar.getAttribute("data-titlebar-active"))
        assertEquals("0", bar.getAttribute("data-height-px"))
        assertEquals("0", ytOverlay.topBar.getAttribute("data-titlebar-offset-px"))
        assertEquals(CssLength.ZERO, ytOverlay.topBar.style.marginTop)

        // Even when HUD wakes on pointer activity, titlebar stays completely inactive & hidden on unsupported WMs
        ytOverlay.autoHideController.notifyUserActivity()
        assertFalse(bar.isWindowedTitleBarActive)
        assertFalse(bar.isVisible)
        assertTrue(bar.hasClass("gui-overlay-titlebar-inactive"))
        assertEquals(0, bar.effectiveHeightPx)
        assertEquals(0.0f, bar.computedStyle?.opacity ?: -1f, 0.001f)
        assertEquals(false, bar.computedStyle?.pointerEventsEnabled)

        // When capabilities allow custom titlebar + embedded components, titlebar activates dynamically;
        // when custom components become unsupported again, syncIfWindowStateChanged collapses it back.
        mutableCaps = TitleCapabilities(
            isSupported = true,
            isTitleBarRequired = true,
            supportsCustomTitleBar = true,
            supportsCustomComponents = true
        )
        assertTrue(bar.isWindowedTitleBarActive)
        assertTrue(bar.isVisible)
        assertEquals(32, bar.effectiveHeightPx)

        mutableCaps = TitleCapabilities(
            isSupported = true,
            isTitleBarRequired = true,
            supportsCustomTitleBar = true,
            supportsCustomComponents = false
        )
        assertFalse(bar.isWindowedTitleBarActive)
        assertFalse(bar.isVisible)
        assertEquals(0, bar.effectiveHeightPx)

        linuxWindow.dispose()
        host.dispose()
    }

    @Test
    fun testTitleBarThemesAutoDarkLightCaptionControlAttributesAndClickDragIsolation() {
        val savedOverride = SystemThemeDetector.overrideDetectedTheme
        var systemMoveCount = 0
        var stageClickCount = 0
        val macWindow = createTestWindow(
            title = "Themeable Overlay",
            capabilities = TitleCapabilities(
                isTitleBarRequired = true,
                defaultControlsPlacement = TitleBarControlsPlacement.LEADING,
                prefersBlurredTranslucentOverlayTitleBar = true
            ),
            onSystemMove = { systemMoveCount++ }
        )
        val lightSurface = Container("light-stage").apply {
            customBackgroundColor = RgbaColor(250, 250, 252, 255)
        }
        val overlay = YoutubePlayerOverlay().apply {
            windowTitleBarConfig = OverlayWindowTitleBarConfig(
                enabled = true,
                themeMode = ThemeMode.AUTO
            )
            updateActions(OverlayActions(onPlayPause = { stageClickCount++ }))
        }
        val host = OverlayHostBox(
            content = lightSurface,
            initialOverlay = overlay
        )
        macWindow.setContent(host)
        overlay.syncWindowTitleBar(macWindow)

        try {
            val sheet = OverlayExtension.defaultStyleSheet()
            host.applyStyleSheet(sheet)
            val bar = overlay.windowTitleBar
            assertEquals("auto", bar.getAttribute("data-titlebar-theme"))
            assertEquals("light", bar.getAttribute("data-titlebar-effective-theme"))
            assertTrue(bar.hasClass("gui-overlay-titlebar-light"))
            assertFalse(bar.hasClass("gui-overlay-titlebar-dark"))
            assertTrue((bar.computedStyle?.backgroundColor?.r ?: 0) >= 200)
            assertTrue((bar.titleLabel.computedStyle?.textColor?.r ?: 255) < 60)
            assertEquals("close", bar.closeButton.getAttribute("data-window-control"))
            assertEquals("minimize", bar.minimizeButton.getAttribute("data-window-control"))
            assertEquals("maximize", bar.maximizeButton.getAttribute("data-window-control"))

            // Switching explicitly to ThemeMode.DARK applies white title + dark translucent vibrancy CSS classes
            overlay.windowTitleBarConfig = OverlayWindowTitleBarConfig(
                enabled = true,
                themeMode = ThemeMode.DARK
            )
            assertEquals("dark", bar.getAttribute("data-titlebar-theme"))
            assertEquals("dark", bar.getAttribute("data-titlebar-effective-theme"))
            assertTrue(bar.hasClass("gui-overlay-titlebar-dark"))
            assertTrue((bar.computedStyle?.backgroundColor?.r ?: 255) < 60)
            assertEquals(RgbaColor.WHITE, bar.titleLabel.computedStyle?.textColor)

            // Switching explicitly to ThemeMode.LIGHT switches back to light fill + dark title via CSS classes
            overlay.windowTitleBarConfig = OverlayWindowTitleBarConfig(
                enabled = true,
                themeMode = ThemeMode.LIGHT
            )
            assertEquals("light", bar.getAttribute("data-titlebar-theme"))
            assertEquals("light", bar.getAttribute("data-titlebar-effective-theme"))
            assertTrue(bar.hasClass("gui-overlay-titlebar-light"))
            assertTrue((bar.computedStyle?.backgroundColor?.r ?: 0) >= 200)
            assertTrue((bar.titleLabel.computedStyle?.textColor?.r ?: 255) < 60)

            // Clicking or double-clicking a caption button must not trigger window drag or stage click
            bar.minimizeButton.onPointerDown?.invoke(PointerEvent(x = 4f, y = 4f))
            bar.onPointerDown?.invoke(PointerEvent(x = 4f, y = 4f))
            bar.onPointerMove?.invoke(PointerEvent(x = 24f, y = 4f))
            bar.minimizeButton.onPointerUp?.invoke(PointerEvent(x = 4f, y = 4f))
            bar.minimizeButton.onClick?.invoke()
            bar.minimizeButton.onDoubleClick?.invoke()
            UiTimerScheduler.advanceTimeBy(300)
            assertEquals(0, systemMoveCount)
            assertEquals(0, stageClickCount)
        } finally {
            SystemThemeDetector.overrideDetectedTheme = savedOverride
            macWindow.dispose()
            host.dispose()
        }
    }

    @Test
    fun testTrailingToLeadingTransitionAfterAttachWindowResolvesTrafficLightsGroupHoverUnfocusedAndCentering() {
        val sheet = OverlayExtension.defaultStyleSheet()
        val overlay = YoutubePlayerOverlay().apply {
            windowTitleBarEnabled = true
        }
        val host = OverlayHostBox(
            content = Container("stage-video").apply {
                widthPx = 1280
                heightPx = 720
            },
            initialOverlay = overlay
        )
        val bar = overlay.windowTitleBar

        // Apply stylesheet while detached -> starts in TRAILING placement with 36x31 desktop buttons
        host.applyStyleSheet(sheet)
        assertEquals(TitleBarControlsPlacement.TRAILING, bar.resolvedControlsPlacement)
        assertTrue(bar.hasClass("gui-overlay-titlebar-trailing-placement"))
        assertEquals("36px", bar.closeButton.computedStyle?.width)
        assertEquals("31px", bar.closeButton.computedStyle?.height)
        assertEquals("✕", bar.closeButton.text)
        assertEquals("—", bar.minimizeButton.text)
        assertEquals("□", bar.maximizeButton.text)

        // Attach macOS-capable window and focus it (preserving existing setOnWindowFocusChanged listener)
        val windowFocusLog = mutableListOf<Boolean>()
        val macWindow = createTestWindow(
            title = "macOS Vibrancy Stage",
            capabilities = TitleCapabilities(
                isTitleBarRequired = true,
                defaultControlsPlacement = TitleBarControlsPlacement.LEADING,
                prefersBlurredTranslucentOverlayTitleBar = true
            )
        )
        macWindow.setOnWindowFocusChanged { focused ->
            windowFocusLog.add(focused)
        }
        macWindow.setContent(host)
        macWindow.dispatchWindowFocusChanged(true)
        assertTrue(bar.isWindowedTitleBarActive)
        assertEquals(listOf(true), windowFocusLog)

        // Transition TRAILING -> LEADING must re-cascade to 12x12 circular traffic lights (never stale 36x31)
        assertEquals(TitleBarControlsPlacement.LEADING, bar.resolvedControlsPlacement)
        assertTrue(bar.hasClass("gui-overlay-titlebar-leading-placement"))
        assertFalse(bar.hasClass("gui-overlay-titlebar-trailing-placement"))
        assertTrue(bar.hasClass("gui-overlay-titlebar-blurred"))
        assertFalse(bar.hasClass("gui-overlay-titlebar-flat"))
        assertFalse(bar.hasClass("gui-overlay-titlebar-unfocused"))

        // blur(22px) saturate(1.4) + macOS dark frosted-glass fill
        val bf = bar.computedStyle?.backdropFilter
        assertEquals(CssFilter(blur = CssLength.px(22f), saturate = 1.4f), bf)
        assertTrue(bf?.hasPixelOps == true)
        assertEquals(RgbaColor(16, 18, 24, 173), bar.computedStyle?.backgroundColor)
        assertEquals(1f, bar.computedStyle?.borderBottomWidthPx)
        assertEquals(RgbaColor(255, 255, 255, 51), bar.computedStyle?.borderBottomColor)

        // True full-width centering with non-blocking center slot & raised leading/trailing slots
        // (LayeredContainer float constraint sets position: absolute; standalone sets position: relative;
        // both establish the positioned containing block for the full-width center slot.)
        assertTrue(bar.computedStyle?.position in setOf("relative", "absolute"))
        assertEquals("relative", bar.leadingSlot.computedStyle?.position)
        assertEquals(2, bar.leadingSlot.computedStyle?.zIndex)
        assertEquals("relative", bar.trailingSlot.computedStyle?.position)
        assertEquals(2, bar.trailingSlot.computedStyle?.zIndex)
        assertEquals("absolute", bar.centerSlot.computedStyle?.position)
        assertEquals("100%", bar.centerSlot.computedStyle?.width)
        assertEquals("100%", bar.centerSlot.computedStyle?.height)
        assertEquals(1, bar.centerSlot.computedStyle?.zIndex)
        assertEquals(false, bar.centerSlot.computedStyle?.pointerEventsEnabled)
        assertEquals(false, bar.titleLabel.computedStyle?.pointerEventsEnabled)
        assertTrue(bar.centerSlot.children.contains(bar.titleLabel))
        assertEquals("macOS Vibrancy Stage", bar.titleLabel.text)

        for (btn in listOf(bar.closeButton, bar.minimizeButton, bar.maximizeButton)) {
            assertEquals("12px", btn.computedStyle?.width)
            assertEquals("12px", btn.computedStyle?.height)
            assertEquals(6f, btn.computedStyle?.borderRadiusPx)
            assertEquals(9f, btn.computedStyle?.fontSizePx)
            assertEquals(700, btn.computedStyle?.fontWeightNumeric)
            assertEquals(CssLength.px(12f), btn.computedStyle?.lineHeight)
            assertEquals("", btn.text)
        }
        assertEquals(RgbaColor(255, 95, 87, 255), bar.closeButton.computedStyle?.backgroundColor)
        assertEquals(RgbaColor(254, 188, 46, 255), bar.minimizeButton.computedStyle?.backgroundColor)
        assertEquals(RgbaColor(40, 200, 64, 255), bar.maximizeButton.computedStyle?.backgroundColor)

        // Group hover reveals contrasting glyphs on all 3 circles simultaneously
        bar.closeButton.dispatchPointerEnter(PointerEvent(x = 6f, y = 6f))
        assertTrue(bar.controlsContainer.hasClass("gui-overlay-titlebar-controls-hovered"))
        assertEquals("×", bar.closeButton.text)
        assertEquals("–", bar.minimizeButton.text)
        assertEquals("+", bar.maximizeButton.text)
        assertEquals(RgbaColor(77, 0, 0, 209), bar.closeButton.computedStyle?.textColor)
        assertEquals(RgbaColor(153, 87, 0, 217), bar.minimizeButton.computedStyle?.textColor)
        assertEquals(RgbaColor(0, 101, 0, 217), bar.maximizeButton.computedStyle?.textColor)

        bar.closeButton.dispatchPointerLeave(PointerEvent(x = 6f, y = 6f))
        assertFalse(bar.controlsContainer.hasClass("gui-overlay-titlebar-controls-hovered"))
        assertEquals("", bar.closeButton.text)
        assertEquals("", bar.minimizeButton.text)
        assertEquals("", bar.maximizeButton.text)

        // Unfocused dimming in dark theme, restored on hover, and returning to dimmed on leave
        macWindow.dispatchWindowFocusChanged(false)
        assertEquals(listOf(true, false), windowFocusLog)
        assertTrue(bar.hasClass("gui-overlay-titlebar-unfocused"))
        for (btn in listOf(bar.closeButton, bar.minimizeButton, bar.maximizeButton)) {
            assertEquals(RgbaColor(255, 255, 255, 56), btn.computedStyle?.backgroundColor)
        }
        bar.controlsContainer.dispatchPointerEnter(PointerEvent(x = 12f, y = 6f))
        assertTrue(bar.controlsContainer.hasClass("gui-overlay-titlebar-controls-hovered"))
        assertEquals(RgbaColor(255, 95, 87, 255), bar.closeButton.computedStyle?.backgroundColor)
        assertEquals(RgbaColor(254, 188, 46, 255), bar.minimizeButton.computedStyle?.backgroundColor)
        assertEquals(RgbaColor(40, 200, 64, 255), bar.maximizeButton.computedStyle?.backgroundColor)
        assertEquals("×", bar.closeButton.text)
        assertEquals("–", bar.minimizeButton.text)
        assertEquals("+", bar.maximizeButton.text)
        bar.controlsContainer.dispatchPointerLeave(PointerEvent(x = 12f, y = 6f))
        assertEquals(RgbaColor(255, 255, 255, 56), bar.closeButton.computedStyle?.backgroundColor)

        // Light theme unfocused dimming + translucent light frosted-glass fill
        overlay.windowTitleBarConfig = OverlayWindowTitleBarConfig(enabled = true, themeMode = ThemeMode.LIGHT)
        assertEquals(RgbaColor(244, 246, 250, 184), bar.computedStyle?.backgroundColor)
        assertEquals(RgbaColor(0, 0, 0, 36), bar.computedStyle?.borderBottomColor)
        assertEquals(CssFilter(blur = CssLength.px(22f), saturate = 1.4f), bar.computedStyle?.backdropFilter)
        for (btn in listOf(bar.closeButton, bar.minimizeButton, bar.maximizeButton)) {
            assertEquals(RgbaColor(0, 0, 0, 41), btn.computedStyle?.backgroundColor)
        }

        // Refocusing restores full traffic-light colors; switching to flat TRAILING window resolves backdrop-filter: none
        macWindow.dispatchWindowFocusChanged(true)
        assertFalse(bar.hasClass("gui-overlay-titlebar-unfocused"))
        assertEquals(RgbaColor(255, 95, 87, 255), bar.closeButton.computedStyle?.backgroundColor)

        val flatWindow = createTestWindow(
            title = "Flat Desktop Stage",
            capabilities = TitleCapabilities(
                isTitleBarRequired = true,
                defaultControlsPlacement = TitleBarControlsPlacement.TRAILING,
                prefersBlurredTranslucentOverlayTitleBar = false
            )
        )
        bar.bindWindow(flatWindow)
        assertEquals(TitleBarControlsPlacement.TRAILING, bar.resolvedControlsPlacement)
        assertTrue(bar.hasClass("gui-overlay-titlebar-flat"))
        assertFalse(bar.hasClass("gui-overlay-titlebar-blurred"))
        assertEquals(CssFilter.NONE, bar.computedStyle?.backdropFilter)
        assertEquals(RgbaColor(244, 246, 250, 204), bar.computedStyle?.backgroundColor)
        assertEquals("36px", bar.closeButton.computedStyle?.width)
        assertEquals("31px", bar.closeButton.computedStyle?.height)
        assertEquals("✕", bar.closeButton.text)

        overlay.windowTitleBarConfig = OverlayWindowTitleBarConfig(enabled = true, themeMode = ThemeMode.DARK)
        assertEquals(RgbaColor(16, 18, 24, 199), bar.computedStyle?.backgroundColor)
        assertEquals(CssFilter.NONE, bar.computedStyle?.backdropFilter)

        assertEquals(173, OverlayWindowTitleBar.MACOS_BAR_BACKGROUND.a)
        assertEquals(199, OverlayWindowTitleBar.TRANSLUCENT_DARK_BACKGROUND.a)
        assertEquals(184, OverlayWindowTitleBar.LIGHT_BLUR_BAR_BACKGROUND.a)
        assertEquals(204, OverlayWindowTitleBar.TRANSLUCENT_LIGHT_BACKGROUND.a)

        flatWindow.dispose()
        macWindow.dispose()
        host.dispose()
    }

    @Test
    fun testTitleBarDoubleClickRespectsCapabilitiesFullscreenExitAndMultiClickDoesNotStartMove() {
        var systemMoveCount = 0
        var maximizableFlag = true
        val factory = FakeOverlayWindowTestFactory()
        GenericUI.initialize(factory)

        val window = object : Window(title = "DoubleClick Test") {
            override val isMaximizable: Boolean
                get() = maximizableFlag
            override val titleCapabilities: TitleCapabilities = TitleCapabilities(
                isTitleBarRequired = true,
                defaultControlsPlacement = TitleBarControlsPlacement.TRAILING,
                prefersBlurredTranslucentOverlayTitleBar = false
            )

            override fun startSystemMove() {
                systemMoveCount++
                super.startSystemMove()
            }
        }
        val overlay = YoutubePlayerOverlay().apply {
            windowTitleBarEnabled = true
        }
        val host = OverlayHostBox(content = Container("stage"), initialOverlay = overlay)
        window.setContent(host)
        window.show(factory)

        val bar = overlay.windowTitleBar
        assertTrue(bar.isVisible)

        // Multi-click pointerDown (clickCount = 2) must not trigger startSystemMove
        bar.onPointerDown?.invoke(PointerEvent(x = 80f, y = 12f, clickCount = 2))
        assertEquals(0, systemMoveCount)

        // Single primary pointerDown still starts system move
        bar.onPointerDown?.invoke(PointerEvent(x = 80f, y = 12f, clickCount = 1))
        assertEquals(1, systemMoveCount)

        // Double-click toggles MAXIMIZED <-> NORMAL
        bar.handleTitleBarDoubleClick()
        assertEquals(WindowState.MAXIMIZED, window.getWindowState())
        bar.handleTitleBarDoubleClick()
        assertEquals(WindowState.NORMAL, window.getWindowState())

        // Respects config.showMaximizeButton == false and window.isMaximizable == false
        overlay.windowTitleBarConfig = OverlayWindowTitleBarConfig(enabled = true, showMaximizeButton = false)
        bar.handleTitleBarDoubleClick()
        assertEquals(WindowState.NORMAL, window.getWindowState())

        overlay.windowTitleBarConfig = OverlayWindowTitleBarConfig(enabled = true, showMaximizeButton = true)
        maximizableFlag = false
        bar.handleTitleBarDoubleClick()
        assertEquals(WindowState.NORMAL, window.getWindowState())

        // When fullscreen, handleTitleBarDoubleClick exits fullscreen cleanly
        maximizableFlag = true
        window.setFullscreen(true)
        assertTrue(window.isFullscreen())
        assertFalse(bar.isWindowedTitleBarActive)
        window.setFullscreen(false)
        assertFalse(window.isFullscreen())

        // Disposing bar detaches its focus observer cleanly
        bar.dispose()
        window.dispatchWindowFocusChanged(false)
        window.dispose()
        host.dispose()
    }

    @Test
    fun testNativeOverlayTitleBarControlsHideEmulatedChromeAndUse28PxHeight() {
        var mutableCaps = TitleCapabilities(
            isTitleBarRequired = true,
            defaultControlsPlacement = TitleBarControlsPlacement.LEADING,
            prefersBlurredTranslucentOverlayTitleBar = true,
            usesNativeOverlayTitleBarControls = true
        )
        val macWindow = object : Window(title = "Native Chrome Stage") {
            override val titleCapabilities: TitleCapabilities
                get() = mutableCaps
        }
        val overlay = YoutubePlayerOverlay().apply {
            windowTitleBarEnabled = true
        }
        val host = OverlayHostBox(
            content = Container("native-stage").apply {
                widthPx = 1280
                heightPx = 720
            },
            initialOverlay = overlay
        )
        macWindow.setContent(host)
        host.applyStyleSheet(OverlayExtension.defaultStyleSheet())

        val bar = overlay.windowTitleBar
        assertTrue(bar.isWindowedTitleBarActive)
        assertTrue(bar.isVisible)
        assertTrue(bar.hasClass("gui-overlay-titlebar-native-controls"))
        assertEquals("true", bar.getAttribute("data-native-titlebar-controls"))
        assertEquals("true", bar.getAttribute("data-titlebar-show-close"))
        assertEquals("true", bar.getAttribute("data-titlebar-show-minimize"))
        assertEquals("true", bar.getAttribute("data-titlebar-show-maximize"))
        assertEquals("true", bar.getAttribute("data-titlebar-show-title"))
        assertEquals("Native Chrome Stage", bar.getAttribute("data-titlebar-title"))

        // GenericUI emulated controls & title label are hidden while native host controls render
        assertFalse(bar.controlsContainer.isVisible)
        assertFalse(bar.closeButton.isVisible)
        assertFalse(bar.minimizeButton.isVisible)
        assertFalse(bar.maximizeButton.isVisible)
        assertFalse(bar.titleLabel.isVisible)

        // Default height adopts native 28px metrics (and topBar offset matches 28px)
        assertEquals(28, bar.effectiveHeightPx)
        assertEquals("28", bar.getAttribute("data-height-px"))
        assertEquals("28px", bar.computedStyle?.height)
        assertEquals("28", overlay.topBar.getAttribute("data-titlebar-offset-px"))

        // Updating title/button config updates published native-host attributes cleanly
        overlay.windowTitleBarConfig = OverlayWindowTitleBarConfig(
            enabled = true,
            showMaximizeButton = false,
            title = "Custom Native Title"
        )
        assertEquals("false", bar.getAttribute("data-titlebar-show-maximize"))
        assertEquals("Custom Native Title", bar.getAttribute("data-titlebar-title"))
        assertFalse(bar.titleLabel.isVisible)
        assertFalse(bar.controlsContainer.isVisible)

        // Switching back to emulated controls restores GenericUI buttons, titleLabel, and 32px height
        mutableCaps = TitleCapabilities(
            isTitleBarRequired = true,
            defaultControlsPlacement = TitleBarControlsPlacement.TRAILING,
            prefersBlurredTranslucentOverlayTitleBar = false,
            usesNativeOverlayTitleBarControls = false
        )
        assertTrue(bar.isWindowedTitleBarActive)
        assertFalse(bar.hasClass("gui-overlay-titlebar-native-controls"))
        assertEquals("false", bar.getAttribute("data-native-titlebar-controls"))
        assertTrue(bar.controlsContainer.isVisible)
        assertTrue(bar.closeButton.isVisible)
        assertTrue(bar.minimizeButton.isVisible)
        assertFalse(bar.maximizeButton.isVisible)
        assertTrue(bar.titleLabel.isVisible)
        assertEquals(32, bar.effectiveHeightPx)
        assertEquals("32", bar.getAttribute("data-height-px"))

        macWindow.dispose()
        host.dispose()
    }

    @Test
    fun testNativeTitleBarHoverActivePinsAndUnpinsAutoHideController() {
        val window = createTestWindow(
            title = "Hover Pin Stage",
            capabilities = TitleCapabilities(
                isTitleBarRequired = true,
                defaultControlsPlacement = TitleBarControlsPlacement.LEADING,
                prefersBlurredTranslucentOverlayTitleBar = true,
                usesNativeOverlayTitleBarControls = true
            )
        )
        val overlay = YoutubePlayerOverlay().apply {
            windowTitleBarEnabled = true
        }
        val host = OverlayHostBox(
            content = Container("hover-stage").apply {
                widthPx = 1280
                heightPx = 720
            },
            initialOverlay = overlay
        )
        window.setContent(host)

        val bar = overlay.windowTitleBar
        val autoHide = overlay.autoHideController
        assertFalse(autoHide.isPinned)

        // Direct setNativeTitleBarHoverActive pins/unpins AutoHideController
        bar.setNativeTitleBarHoverActive(true)
        assertTrue(autoHide.isPinned)
        assertTrue(autoHide.activePinReasons.contains("native-titlebar-hover"))
        autoHide.hideOverlay()
        assertTrue(autoHide.isOverlayVisible)

        bar.setNativeTitleBarHoverActive(false)
        assertFalse(autoHide.isPinned)
        assertFalse(autoHide.activePinReasons.contains("native-titlebar-hover"))

        // Hovering via pointer enter/leave on the titlebar also pins/unpins
        bar.dispatchPointerEnter(PointerEvent(x = 40f, y = 10f))
        assertTrue(autoHide.isPinned)
        assertTrue(autoHide.activePinReasons.contains("native-titlebar-hover"))

        bar.dispatchPointerLeave(PointerEvent(x = 40f, y = 40f))
        assertFalse(autoHide.isPinned)
        assertFalse(autoHide.activePinReasons.contains("native-titlebar-hover"))

        // Entering fullscreen clears active hover pin cleanly
        bar.setNativeTitleBarHoverActive(true)
        assertTrue(autoHide.isPinned)
        window.setFullscreen(true)
        assertFalse(bar.isWindowedTitleBarActive)
        assertFalse(autoHide.isPinned)

        window.dispose()
        host.dispose()
    }
}
