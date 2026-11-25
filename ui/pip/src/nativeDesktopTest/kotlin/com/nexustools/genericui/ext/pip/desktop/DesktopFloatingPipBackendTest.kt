package com.nexustools.genericui.ext.pip.desktop

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.desktop.core.DesktopInteractiveSurfaceSpec
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
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
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.FragmentPipRegistry
import com.nexustools.genericui.ext.pip.PipRegistry
import com.nexustools.genericui.ext.pip.PipSessionConfig
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
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopFloatingPipBackendTest {

    private var savedActiveFactory: NativeFactory? = null

    @BeforeTest
    fun setUp() {
        savedActiveFactory = GenericUI.activeFactory
        PipRegistry.resetForTest()
        FragmentPipRegistry.clearForTest()
        UiTimerScheduler.clearAllTimers()
    }

    @AfterTest
    fun tearDown() {
        UiTimerScheduler.clearAllTimers()
        FragmentPipRegistry.clearForTest()
        PipRegistry.resetForTest()
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
        override fun setFocusVisuals(visuals: StyledVisualSpec?) { focus = visuals }
        override fun setActiveVisuals(visuals: StyledVisualSpec?) { active = visuals }
        override fun setDisabledVisuals(visuals: StyledVisualSpec?) { disabled = visuals }
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

    private class FakeWindowNativeImpl(
        private val capabilities: TitleCapabilities = TitleCapabilities(
            isSupported = true,
            isTitleBarRequired = true,
            supportsCustomTitleBar = true,
            supportsCustomComponents = true
        )
    ) : StubNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = capabilities.supportsCustomTitleBar
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        override val supportsNavbarMenuBar: Boolean = true
        override val supportsMobileNavbarMenuBar: Boolean = false
        override fun getTitleCapabilities(): TitleCapabilities = capabilities
        private var title: String = "GenericUI"
        private var windowSize: Size = Size(800, 600)
        private var windowState: WindowState = WindowState.NORMAL
        private var fullscreen: Boolean = false
        private var undecorated: Boolean = false
        private var alwaysOnTop: Boolean = false
        val setUndecoratedCalls = mutableListOf<Boolean>()
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
        override fun setUndecorated(undecorated: Boolean) {
            this.undecorated = undecorated
            setUndecoratedCalls.add(undecorated)
        }
        override fun setAlwaysOnTop(alwaysOnTop: Boolean) { this.alwaysOnTop = alwaysOnTop }
        override fun isAlwaysOnTop(): Boolean = alwaysOnTop
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

    private class FakeDesktopPipTestFactory(
        override val backendTarget: BackendTarget = BackendTarget.Windows,
        override val defaultTitleCapabilities: TitleCapabilities = TitleCapabilities(
            isSupported = true,
            isTitleBarRequired = true,
            supportsCustomTitleBar = true,
            supportsCustomComponents = true
        ),
        private val windowCapabilitiesOverride: TitleCapabilities = defaultTitleCapabilities
    ) : NativeFactory {
        val createdWindows = mutableListOf<FakeWindowNativeImpl>()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in DesktopFloatingPipBackendTest")

        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> =
            FakeWindowNativeImpl(windowCapabilitiesOverride).also { createdWindows.add(it) }
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

    @Test
    fun surfaceMirrorForwardsVBlankDeduplicatesSimultaneousTicksAndRestoresOriginal() {
        var dispatchCount = 0
        val receivedTimestamps = mutableListOf<Double?>()
        val originalVBlank: (Double?) -> Unit = { ts ->
            dispatchCount++
            receivedTimestamps.add(ts)
        }

        val spec = DesktopInteractiveSurfaceSpec(
            surfaceWidth = { 640 },
            surfaceHeight = { 360 },
            swapCount = { 42L }
        )
        val sourceWidget = DesktopNativeWidget("TestToolkit", "VideoCanvas")
        spec.attachTo(sourceWidget)
        sourceWidget.vblankDispatch = originalVBlank

        val mirrorWidget = DesktopNativeWidget("TestToolkit", "PipMirror")
        val mirror = DesktopPipSurfaceMirror()

        assertTrue(mirror.mount(sourceWidget, mirrorWidget))
        assertTrue(mirror.isMounted)
        assertSame(spec, mirrorWidget.interactiveSurfaceSpec)
        assertNotNull(mirrorWidget.vblankDispatch)

        // Mirrored widget drives source VBlank when main window is minimized.
        mirrorWidget.vblankDispatch?.invoke(16.67)
        assertEquals(1, dispatchCount)

        // Simultaneous macOS tick with the same timestamp on source widget is deduplicated.
        sourceWidget.vblankDispatch?.invoke(16.67)
        assertEquals(1, dispatchCount)

        // Distinct frame timestamp passes cleanly.
        mirrorWidget.vblankDispatch?.invoke(33.34)
        assertEquals(2, dispatchCount)
        assertEquals(listOf<Double?>(16.67, 33.34), receivedTimestamps)

        mirror.unmount()
        assertFalse(mirror.isMounted)
        assertNull(mirrorWidget.vblankDispatch)
        assertNull(mirrorWidget.interactiveSurfaceSpec)
        assertSame(originalVBlank, sourceWidget.vblankDispatch)
    }

    @Test
    fun desktopBackendMinimizeAutoEnterAndRestoreCycle() {
        DesktopPipProvider.register()
        assertTrue(PipRegistry.hasRegisteredFactory())

        val backend = DesktopFloatingPipBackend().apply {
            headlessImmediateTransitions = true
        }
        PipRegistry.registerBackendFactory { backend }

        val factory = FakeDesktopPipTestFactory()
        GenericUI.initialize(factory)

        val window = Window("Desktop Main", Size(1280, 720)).apply {
            windowBounds = Rect(50, 50, 1280, 720)
        }
        val fragment = Fragment()
        val target = Container("desktop-video").apply {
            htmlTag = "video"
            bounds = Rect(0, 0, 640, 360)
        }
        fragment.addChild(target)
        window.setContent(fragment)
        window.show(factory)

        val helper = FragmentPipHelper(fragment).apply {
            pipTarget = target
            isActivelyPlayingProvider = { true }
            configProvider = { PipSessionConfig(title = "Desktop Clip") }
        }

        try {
            helper.bindWindow(window)
            window.minimize()
            assertTrue(helper.isInPipMode)
            assertNotNull(backend.activePipWindow)
            val pipHost = assertNotNull(backend.activeOverlayHost)
            val pipOverlay = assertNotNull(backend.activeControlOverlay)
            assertTrue(pipHost.windowTitleBarEnabled)
            assertTrue(pipHost.hasClass("pip-overlay-host"))
            assertNull(pipHost.style.borderRadius)
            assertEquals("0", pipHost.getAttribute("borderRadiusPx"))
            assertEquals(0f, pipHost.windowTitleBar.computedStyle?.borderRadiusPx)
            assertTrue(pipHost.windowTitleBar.isVisible)
            assertEquals(32, pipHost.windowTitleBar.effectiveHeightPx)
            assertTrue(pipHost.windowTitleBar.closeButton.isVisible)
            assertFalse(pipHost.windowTitleBar.minimizeButton.isVisible)
            assertFalse(pipHost.windowTitleBar.maximizeButton.isVisible)
            assertTrue(pipHost.windowTitleBar.trailingAccessorySlot.children.contains(pipOverlay.restoreButton))
            assertEquals("Desktop Clip", pipHost.windowTitleBar.titleLabel.text)

            window.restore()
            assertFalse(helper.isInPipMode)
            assertNull(backend.activePipWindow)
            assertNull(backend.activeOverlayHost)
            assertSame(fragment, target.parent)
        } finally {
            helper.dispose()
            window.close()
        }
    }

    @Test
    fun desktopBackendKeepsPipWindowDecoratedAndHidesDuplicateChromeWhenUnsupportedLinuxWm() {
        DesktopPipProvider.register()
        val backend = DesktopFloatingPipBackend().apply {
            headlessImmediateTransitions = true
        }
        PipRegistry.registerBackendFactory { backend }

        val unsupportedLinuxCaps = TitleCapabilities(
            isSupported = true,
            isTitleBarRequired = true,
            supportsCustomTitleBar = false,
            supportsCustomComponents = false
        )
        val factory = FakeDesktopPipTestFactory(
            backendTarget = BackendTarget.Gtk,
            defaultTitleCapabilities = unsupportedLinuxCaps
        )
        GenericUI.initialize(factory)

        val window = Window("Linux Main", Size(1280, 720)).apply {
            windowBounds = Rect(50, 50, 1280, 720)
        }
        val fragment = Fragment()
        val target = Container("linux-video").apply {
            htmlTag = "video"
            bounds = Rect(0, 0, 640, 360)
        }
        fragment.addChild(target)
        window.setContent(fragment)
        window.show(factory)

        val helper = FragmentPipHelper(fragment).apply {
            pipTarget = target
            isActivelyPlayingProvider = { true }
            configProvider = { PipSessionConfig(title = "Linux PiP Clip") }
        }

        try {
            helper.bindWindow(window)
            window.minimize()
            assertTrue(helper.isInPipMode)

            val pipWindow = assertNotNull(backend.activePipWindow)
            val pipHost = assertNotNull(backend.activeOverlayHost)
            val pipOverlay = assertNotNull(backend.activeControlOverlay)
            val pipImpl = factory.createdWindows.last()

            assertFalse(pipWindow.isUndecorated)
            assertFalse(pipImpl.isUndecorated())
            assertFalse(pipImpl.setUndecoratedCalls.contains(true))
            assertFalse(pipHost.windowTitleBarEnabled)
            assertFalse(pipHost.windowTitleBar.isWindowedTitleBarActive)
            assertFalse(pipHost.windowTitleBar.isVisible)
            assertEquals(0, pipHost.windowTitleBar.effectiveHeightPx)

            assertFalse(pipOverlay.fallbackTitleLabel.isVisible)
            assertFalse(pipOverlay.fallbackCloseButton.isVisible)
            assertFalse(pipOverlay.topScrim.isVisible)
            assertTrue(pipOverlay.topBar.isVisible)
            assertTrue(pipOverlay.restoreButton.isVisible)
            assertFalse(pipHost.windowTitleBar.trailingAccessorySlot.children.contains(pipOverlay.restoreButton))

            pipOverlay.restoreButton.onClick?.invoke()
            assertFalse(helper.isInPipMode)
            assertNull(backend.activePipWindow)
            assertSame(fragment, target.parent)
        } finally {
            helper.dispose()
            window.close()
        }
    }
}
