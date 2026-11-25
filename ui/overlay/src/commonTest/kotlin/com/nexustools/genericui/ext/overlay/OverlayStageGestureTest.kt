package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.StyledContainer
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
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.TitleBarControlsPlacement
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerDragManager
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
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverlayStageGestureTest {

    private var savedActiveFactory: NativeFactory? = null

    @BeforeTest
    fun setUp() {
        savedActiveFactory = GenericUI.activeFactory
        Animator.cancelAllAnimations()
        PointerDragManager.cancelActiveDrag()
        UiTimerScheduler.clearAllTimers()
    }

    @AfterTest
    fun tearDown() {
        Animator.cancelAllAnimations()
        PointerDragManager.cancelActiveDrag()
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

    private class FakeStageTestFactory : NativeFactory {
        val createdWindows = mutableListOf<FakeWindowNativeImpl>()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in OverlayStageGestureTest")

        override val backendTarget: BackendTarget = BackendTarget.Swing
        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> = FakeWindowNativeImpl().also { createdWindows.add(it) }
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

    private class MockStageWindow : IWindow {
        override val windowId: String = "mock-stage-window"
        override val ownerWindow: IWindow? = null
        override val childWindows: List<IWindow> = emptyList()
        override val titleCapabilities: TitleCapabilities = TitleCapabilities(
            isTitleBarRequired = true,
            defaultControlsPlacement = TitleBarControlsPlacement.TRAILING,
            prefersBlurredTranslucentOverlayTitleBar = false
        )
        private var title: String = "Mock"
        private var icon: String? = null
        private var titleBar: TitleBar? = null
        private var size: Size = Size(640, 360)
        override var windowBounds: Rect = Rect(0, 0, 640, 360)
        private var state: WindowState = WindowState.NORMAL
        private var fullscreen: Boolean = false
        private var contentContainer: Container? = null
        var startSystemMoveCount: Int = 0
        override val isWindowShown: Boolean = true
        override val isVisible: Boolean = true
        override val isWindowFocused: Boolean = true
        override val isClosable: Boolean = true
        override val isMinimizable: Boolean = true
        override val isMaximizable: Boolean = true
        override val isResizable: Boolean = true
        override val isModal: Boolean = false
        override val isToolWindow: Boolean = false
        override val isAlwaysOnTop: Boolean = false
        override var isDisposed: Boolean = false
        override val fragmentContainer: FragmentContainer? = null

        override fun getTitle(): String = title
        override fun setTitle(title: String) { this.title = title }
        override fun getWindowIcon(): String? = icon
        override fun setWindowIcon(iconPathOrSvg: String?) { icon = iconPathOrSvg }
        override fun getTitleBar(): TitleBar? = titleBar
        override fun setTitleBar(titleBar: TitleBar?) { this.titleBar = titleBar }
        override fun getWindowSize(): Size = size
        override fun setWindowSize(size: Size) { this.size = size }
        override fun getWindowState(): WindowState = state
        override fun setWindowState(state: WindowState) { this.state = state }
        override fun startSystemMove() { startSystemMoveCount++ }
        override fun isFullscreen(): Boolean = fullscreen
        override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
            this.fullscreen = fullscreen
            state = if (fullscreen) WindowState.FULLSCREEN else WindowState.NORMAL
        }
        override fun show(factory: NativeFactory?) {}
        override fun hide() {}
        override fun close() {}
        override fun minimize() { state = WindowState.MINIMIZED }
        override fun maximize() { state = WindowState.MAXIMIZED }
        override fun restore() { state = WindowState.NORMAL; fullscreen = false }
        override fun focus() {}
        override fun bringToFront() {}
        override fun setContent(container: Container) { contentContainer = container }
        override fun getContent(): Container? = contentContainer
        override fun getOrCreateFragmentContainer(): FragmentContainer =
            FragmentContainer(initialFragment = null, hostWindow = null).apply {
                hostingIWindow = this@MockStageWindow
            }
        override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {}
        override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {}
        override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {}
        override fun dispose() { isDisposed = true }
    }

    @Test
    fun windowDragEnabledDefaultsFalseAndDisambiguatesDragClickDoubleClickWhenEnabled() {
        var playPauseCalls = 0
        var fullscreenCalls = 0
        var customDragCalls = 0

        val overlay = YoutubePlayerOverlay()
        val host = OverlayHostBox(content = Container("stage"), initialOverlay = overlay)
        host.actions = OverlayActions(
            onPlayPause = { playPauseCalls++ },
            onToggleFullscreen = { fullscreenCalls++ }
        )

        // Safe defaults: stage window drag disabled by default.
        assertFalse(host.windowDragEnabled)
        assertFalse(overlay.windowDragEnabled)

        host.onWindowDragMove = { customDragCalls++ }
        overlay.hudControlsContainer.onPointerDown?.invoke(PointerEvent(x = 20f, y = 20f))
        overlay.hudControlsContainer.onPointerMove?.invoke(PointerEvent(x = 40f, y = 20f))
        overlay.hudControlsContainer.onPointerUp?.invoke(PointerEvent(x = 40f, y = 20f))
        overlay.hudControlsContainer.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        assertEquals(0, customDragCalls)
        assertEquals(1, playPauseCalls)

        // Enabling host.windowDragEnabled propagates to overlay.
        host.windowDragEnabled = true
        assertTrue(overlay.windowDragEnabled)

        // Sub-slop movement (< 5px): does NOT start drag, schedules 250ms debounced Play/Pause.
        overlay.hudControlsContainer.onPointerDown?.invoke(PointerEvent(x = 50f, y = 50f))
        assertEquals(0, customDragCalls)
        overlay.hudControlsContainer.onPointerMove?.invoke(PointerEvent(x = 53f, y = 52f))
        assertEquals(0, customDragCalls)
        overlay.hudControlsContainer.onPointerUp?.invoke(PointerEvent(x = 53f, y = 52f))
        overlay.hudControlsContainer.onClick?.invoke()

        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS - 1)
        assertEquals(1, playPauseCalls)
        UiTimerScheduler.advanceTimeBy(1)
        assertEquals(2, playPauseCalls)

        // Drag >= 5px (using screen coordinates): triggers drag ONCE, suppresses release onClick.
        overlay.hudControlsContainer.onPointerDown?.invoke(
            PointerEvent(x = 50f, y = 50f, screenX = 200f, screenY = 200f)
        )
        overlay.hudControlsContainer.onPointerMove?.invoke(
            PointerEvent(x = 52f, y = 50f, screenX = 206f, screenY = 200f)
        )
        assertEquals(1, customDragCalls)
        overlay.hudControlsContainer.onPointerMove?.invoke(
            PointerEvent(x = 55f, y = 55f, screenX = 220f, screenY = 220f)
        )
        assertEquals(1, customDragCalls)
        overlay.hudControlsContainer.onPointerUp?.invoke(
            PointerEvent(x = 55f, y = 55f, screenX = 220f, screenY = 220f)
        )
        overlay.hudControlsContainer.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 20)
        assertEquals(2, playPauseCalls)

        // Next click works normally after drag release.
        overlay.hudControlsContainer.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        assertEquals(3, playPauseCalls)

        // Long stage drag (>400ms) keeps both single-click and double-click suppressed until pointer release.
        overlay.hudControlsContainer.onPointerDown?.invoke(PointerEvent(x = 10f, y = 10f, screenX = 100f, screenY = 100f))
        overlay.hudControlsContainer.onPointerMove?.invoke(PointerEvent(x = 25f, y = 10f, screenX = 120f, screenY = 100f))
        assertEquals(2, customDragCalls)
        UiTimerScheduler.advanceTimeBy(650)
        overlay.hudControlsContainer.onClick?.invoke()
        overlay.hudControlsContainer.onDoubleClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        assertEquals(3, playPauseCalls, "Drag >400ms must keep clicks suppressed while still dragging.")
        assertEquals(0, fullscreenCalls, "Drag >400ms must suppress double-clicks while still dragging.")

        // Release clears active drag and consumes release click, then subsequent click & double-click work cleanly.
        overlay.hudControlsContainer.onPointerUp?.invoke(PointerEvent(x = 25f, y = 10f, screenX = 120f, screenY = 100f))
        overlay.hudControlsContainer.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        assertEquals(3, playPauseCalls, "Release click after long drag must still be suppressed once.")

        overlay.hudControlsContainer.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        assertEquals(4, playPauseCalls)

        // Double-click cancels pending 250ms click and triggers fullscreen without confusing clickCount=2 with drag.
        overlay.hudControlsContainer.onClick?.invoke()
        overlay.hudControlsContainer.onPointerDown?.invoke(PointerEvent(x = 10f, y = 10f, clickCount = 2))
        overlay.hudControlsContainer.onPointerMove?.invoke(PointerEvent(x = 30f, y = 30f, clickCount = 2))
        overlay.hudControlsContainer.onDoubleClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        assertEquals(2, customDragCalls)
        assertEquals(4, playPauseCalls)
        assertEquals(1, fullscreenCalls)

        host.dispose()
    }

    @Test
    fun sameTurnBubblingDedupeAndBareOverlayHostBoxDefaultTitleBarDragWork() {
        val factory = FakeStageTestFactory()
        GenericUI.initialize(factory)

        val window = Window("Bare Stage")
        val host = OverlayHostBox(content = Container("video-surface")).apply {
            windowTitleBarEnabled = false
            windowDragEnabled = true
        }
        var playPauseCalls = 0
        var fullscreenCalls = 0
        host.actions = OverlayActions(
            onPlayPause = { playPauseCalls++ },
            onToggleFullscreen = { fullscreenCalls++ }
        )
        window.setContent(host)
        window.show(factory)

        val defaultOverlay = host.overlayRoot.children.firstOrNull()
        assertIs<DefaultWindowTitleBarOverlay>(defaultOverlay)
        assertFalse(defaultOverlay.windowTitleBar.config.enabled)
        assertFalse(defaultOverlay.windowTitleBar.isVisible)
        assertEquals(false, host.getLayerConstraint(host.overlayRoot)?.passThroughPointerEvents)

        val fakeWindowImpl = factory.createdWindows.single()
        defaultOverlay.onPointerDown?.invoke(PointerEvent(x = 20f, y = 20f))
        defaultOverlay.onPointerMove?.invoke(PointerEvent(x = 30f, y = 20f))
        assertEquals(1, fakeWindowImpl.startSystemMoveCount)
        defaultOverlay.onPointerUp?.invoke(PointerEvent(x = 30f, y = 20f))
        defaultOverlay.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        assertEquals(0, playPauseCalls)

        // Single click & double click on bare stage work cleanly.
        defaultOverlay.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        assertEquals(1, playPauseCalls)
        defaultOverlay.onDoubleClick?.invoke()
        assertEquals(1, fullscreenCalls)

        // Same-turn bubbling across centerZone, hudControlsContainer, and YoutubePlayerOverlay deduplicates to 1.
        val ytOverlay = YoutubePlayerOverlay()
        host.attachOverlay(ytOverlay)
        ytOverlay.centerZone.onClick?.invoke()
        ytOverlay.hudControlsContainer.onClick?.invoke()
        ytOverlay.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        assertEquals(2, playPauseCalls)

        ytOverlay.centerZone.onDoubleClick?.invoke()
        ytOverlay.hudControlsContainer.onDoubleClick?.invoke()
        ytOverlay.onDoubleClick?.invoke()
        assertEquals(2, fullscreenCalls)

        host.dispose()
        window.close()
    }

    @Test
    fun stageDragGuardsHandleUnshownWindowShownMockWindowMaximizedFullscreenTouchRightClickAndActiveDrag() {
        val factory = FakeStageTestFactory()
        GenericUI.initialize(factory)

        // Unshown Window: triggerWindowDragMove safely skips startSystemMove without throwing.
        val unshownWindow = Window("Unshown Window")
        val unshownHost = OverlayHostBox(content = Container("c")).apply {
            windowDragEnabled = true
        }
        unshownWindow.setContent(unshownHost)
        val unshownOverlay = assertIs<DefaultWindowTitleBarOverlay>(unshownHost.overlayRoot.children.firstOrNull())
        unshownOverlay.triggerWindowDragMove()
        assertTrue(factory.createdWindows.isEmpty())
        unshownHost.dispose()

        // Shown Mock IWindow + Maximize / Fullscreen / Right-click / Touch / Interactive Drag guards.
        val mockWindow = MockStageWindow()
        val overlay = YoutubePlayerOverlay().apply {
            windowDragEnabled = true
        }
        val host = OverlayHostBox(content = Container("mock-video"), initialOverlay = overlay)
        overlay.windowTitleBar.bindWindow(mockWindow)

        // Right-click (button = 2) never starts drag.
        overlay.hudControlsContainer.onPointerDown?.invoke(PointerEvent(x = 10f, y = 10f, button = 2))
        overlay.hudControlsContainer.onPointerMove?.invoke(PointerEvent(x = 30f, y = 10f, button = 2))
        assertEquals(0, mockWindow.startSystemMoveCount)

        // Active PointerDragManager session blocks stage drag.
        PointerDragManager.startDrag(overlay.timelineContainer, PointerEvent(0f, 0f), onDragMove = {})
        overlay.hudControlsContainer.onPointerDown?.invoke(PointerEvent(x = 10f, y = 10f))
        overlay.hudControlsContainer.onPointerMove?.invoke(PointerEvent(x = 30f, y = 10f))
        assertEquals(0, mockWindow.startSystemMoveCount)
        PointerDragManager.cancelActiveDrag()

        // Touch-primary mode disables drag and toggles HUD visibility on click.
        overlay.inputRouter.isTouchPrimaryMode = true
        overlay.hudControlsContainer.onPointerDown?.invoke(PointerEvent(x = 10f, y = 10f))
        overlay.hudControlsContainer.onPointerMove?.invoke(PointerEvent(x = 30f, y = 10f))
        assertEquals(0, mockWindow.startSystemMoveCount)
        overlay.hudControlsContainer.onClick?.invoke()
        Animator.flushAll()
        assertFalse(overlay.autoHideController.isOverlayVisible)
        overlay.inputRouter.isTouchPrimaryMode = false

        // Maximized & Fullscreen states disable canDragHostingWindow().
        mockWindow.maximize()
        assertFalse(overlay.canDragHostingWindow())
        overlay.hudControlsContainer.onPointerDown?.invoke(PointerEvent(x = 10f, y = 10f))
        overlay.hudControlsContainer.onPointerMove?.invoke(PointerEvent(x = 30f, y = 10f))
        assertEquals(0, mockWindow.startSystemMoveCount)

        mockWindow.restore()
        mockWindow.setFullscreen(true)
        assertFalse(overlay.canDragHostingWindow())
        overlay.hudControlsContainer.onPointerDown?.invoke(PointerEvent(x = 10f, y = 10f))
        overlay.hudControlsContainer.onPointerMove?.invoke(PointerEvent(x = 30f, y = 10f))
        assertEquals(0, mockWindow.startSystemMoveCount)

        mockWindow.restore()
        assertTrue(overlay.canDragHostingWindow())
        overlay.hudControlsContainer.onPointerDown?.invoke(PointerEvent(x = 10f, y = 10f))
        overlay.hudControlsContainer.onPointerMove?.invoke(PointerEvent(x = 30f, y = 10f))
        assertEquals(1, mockWindow.startSystemMoveCount)

        host.dispose()
    }

    @Test
    fun allIsolatedYoutubeControlsAndPopoversNeverTriggerStageDragPlayPauseOrFullscreenAndFullscreenToggleIsReentrantSafe() {
        var playCalls = 0
        var pauseCalls = 0
        var stageFullscreenToggleEvents = 0
        var dragCalls = 0

        val overlay = YoutubePlayerOverlay().apply {
            windowDragEnabled = true
            onWindowDragMove = { dragCalls++ }
        }
        val host = OverlayHostBox(content = Container("stage"), initialOverlay = overlay)
        host.actions = OverlayActions(
            onPlay = { playCalls++ },
            onPause = { pauseCalls++ }
        )

        val extraTopButton = Button("Stats")
        val extraPillButton = Button("Quality")
        val settingsPopover = StyledContainer(classPrefix = "settings-popover")
        overlay.addTopRightAction(extraTopButton)
        overlay.addRightPillButton(extraPillButton)
        overlay.registerPopoverLayer("settings", settingsPopover)

        val nonPlaybackIsolatedControls = listOf(
            overlay.timelineContainer,
            extraTopButton,
            extraPillButton,
            settingsPopover,
            overlay.windowTitleBar
        )

        for (control in nonPlaybackIsolatedControls) {
            control.onPointerDown?.invoke(PointerEvent(x = 10f, y = 10f))
            control.onPointerMove?.invoke(PointerEvent(x = 40f, y = 10f))
            control.onClick?.invoke()
            control.onDoubleClick?.invoke()
            UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        }
        assertEquals(0, dragCalls)
        assertEquals(0, playCalls)
        assertEquals(0, pauseCalls)
        assertFalse(overlay.state.isFullscreen)

        // Built-in buttons also isolate pointer drags & bubbled stage clicks.
        overlay.playPauseButton.onPointerDown?.invoke(PointerEvent(x = 10f, y = 10f))
        overlay.playPauseButton.onPointerMove?.invoke(PointerEvent(x = 40f, y = 10f))
        overlay.centerPlayPauseButton.onPointerDown?.invoke(PointerEvent(x = 10f, y = 10f))
        overlay.centerPlayPauseButton.onPointerMove?.invoke(PointerEvent(x = 40f, y = 10f))
        overlay.fullscreenButton.onPointerDown?.invoke(PointerEvent(x = 10f, y = 10f))
        overlay.fullscreenButton.onPointerMove?.invoke(PointerEvent(x = 40f, y = 10f))
        assertEquals(0, dragCalls)

        extraPillButton.onPointerDown?.invoke(PointerEvent(x = 5f, y = 5f))
        extraPillButton.onClick?.invoke()
        overlay.hudControlsContainer.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        assertEquals(0, playCalls)

        // requestFullscreenToggle + fullscreenButton remain re-entrancy safe without double-firing.
        overlay.onFullscreenToggle = {
            stageFullscreenToggleEvents++
            // Simulate listener attempting re-entrant call.
            overlay.requestFullscreenToggle()
        }
        overlay.hudControlsContainer.onDoubleClick?.invoke()
        assertEquals(1, stageFullscreenToggleEvents)
        assertTrue(overlay.state.isFullscreen)
        assertTrue(overlay.fullscreenButton.isFullscreen)

        overlay.fullscreenButton.onClick?.invoke()
        assertEquals(2, stageFullscreenToggleEvents)
        assertFalse(overlay.state.isFullscreen)
        assertFalse(overlay.fullscreenButton.isFullscreen)
        assertEquals(0, playCalls)
        assertEquals(0, dragCalls)

        host.dispose()
    }

    @Test
    fun overlayHostBoxSetsHostRootAttributeWithoutBlockingActiveOverlayAndResetsStaleHudAlpha() {
        val factory = FakeStageTestFactory()
        GenericUI.initialize(factory)

        val window = Window("Host Root Attributes")
        val overlay = YoutubePlayerOverlay()
        val host = OverlayHostBox(
            content = Container("video-surface"),
            initialOverlay = overlay
        )
        window.setContent(host)
        window.show(factory)

        assertEquals("true", host.overlayRoot.getAttribute("data-overlay-host-root"))
        assertNull(
            host.overlayRoot.getAttribute("data-overlay-root"),
            "Inert LayeredContainer overlayRoot must NOT carry data-overlay-root so stage scanners reach active overlay."
        )
        assertEquals("true", overlay.getAttribute("data-overlay-root"))

        // Simulate hidden overlay state before showOverlay/wake; autoHideController removes .hidden cleanly.
        val sheet = OverlayExtension.defaultStyleSheet()
        host.applyStyleSheet(sheet)
        overlay.autoHideController.targetComponent?.addClass(AutoHideController.HIDDEN_CLASS)
        assertTrue(overlay.autoHideController.targetComponent?.hasClass(AutoHideController.HIDDEN_CLASS) == true)
        overlay.autoHideController.showOverlay(scheduleHide = false)
        assertFalse(overlay.autoHideController.targetComponent?.hasClass(AutoHideController.HIDDEN_CLASS) == true)
        assertEquals(1.0f, overlay.autoHideController.targetComponent?.computedStyle?.opacity ?: -1f, 0.001f)
        assertEquals(true, overlay.autoHideController.targetComponent?.computedStyle?.pointerEventsEnabled)
        assertEquals("true", overlay.getAttribute("hudVisible"))
        assertEquals("false", overlay.getAttribute("hudPointerLeftHidden"))

        window.dispose()
        host.dispose()
    }
}
