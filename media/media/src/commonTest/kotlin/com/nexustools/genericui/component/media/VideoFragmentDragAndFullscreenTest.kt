package com.nexustools.genericui.component.media

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
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerDragManager
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.overlay.VideoOverlayHost
import com.nexustools.genericui.ext.media.overlay.YoutubeMediaOverlayHelper
import com.nexustools.genericui.ext.overlay.DefaultWindowTitleBarOverlay
import com.nexustools.genericui.ext.overlay.OverlayInputRouter
import com.nexustools.genericui.ext.overlay.OverlayStageProvider
import com.nexustools.genericui.ext.overlay.YoutubePlayerOverlay
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
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl
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
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VideoFragmentDragAndFullscreenTest {

    private var savedActiveFactory: NativeFactory? = null

    @BeforeTest
    fun setUp() {
        savedActiveFactory = GenericUI.activeFactory
        PointerDragManager.cancelActiveDrag()
        UiTimerScheduler.clearAllTimers()
        MediaExtension.resetProvidersForTest()
        MediaExtension.registerProviders(
            videoPlayer = { FakeVideoPlayerNativeImpl() }
        )
    }

    @AfterTest
    fun tearDown() {
        PointerDragManager.cancelActiveDrag()
        UiTimerScheduler.clearAllTimers()
        MediaExtension.resetProvidersForTest()
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

    private class FakeStyledComponentNativeImpl(prefix: String) :
        StubNativeImpl(prefix), StyledComponentNativeImpl<Any> {
        private var base: StyledVisualSpec = StyledVisualSpec()
        private var hover: StyledVisualSpec? = null
        private var focus: StyledVisualSpec? = null
        private var active: StyledVisualSpec? = null
        private var disabled: StyledVisualSpec? = null
        override fun setStyledVisuals(visuals: StyledVisualSpec) { base = visuals }
        override fun getStyledVisuals(): StyledVisualSpec = base
        override fun setHoverVisuals(visuals: StyledVisualSpec?) { hover = visuals }
        override fun getHoverVisuals(): StyledVisualSpec? = hover
        override fun setFocusVisuals(visuals: StyledVisualSpec?) { focus = visuals }
        override fun getFocusVisuals(): StyledVisualSpec? = focus
        override fun setActiveVisuals(visuals: StyledVisualSpec?) { active = visuals }
        override fun getActiveVisuals(): StyledVisualSpec? = active
        override fun setDisabledVisuals(visuals: StyledVisualSpec?) { disabled = visuals }
        override fun getDisabledVisuals(): StyledVisualSpec? = disabled
    }

    private class FakeVideoPlayerNativeImpl : StubNativeImpl("video-player"), VideoPlayerNativeImpl<Any> {
        private var currentSeconds: Double = 0.0
        private var durationSeconds: Double = 120.0
        private var playing: Boolean = false
        private var speed: Float = 1f
        private var volumeValue: Float = 1f
        private var mutedValue: Boolean = false
        private var resizeModeValue: VideoResizeMode = VideoResizeMode.FIT
        override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
            currentSeconds = seekOffsetSeconds
            if (durationOverrideSeconds != null) durationSeconds = durationOverrideSeconds
        }
        override fun play() { playing = true }
        override fun pause() { playing = false }
        override fun stop() { playing = false; currentSeconds = 0.0 }
        override fun seekTo(seconds: Double) { currentSeconds = seconds }
        override fun getCurrentTimeSeconds(): Double = currentSeconds
        override fun getDurationSeconds(): Double = durationSeconds
        override fun isPlaying(): Boolean = playing
        override fun setPlaybackSpeed(speed: Float) { this.speed = speed }
        override fun getPlaybackSpeed(): Float = speed
        override fun setVolume(volume: Float) { volumeValue = volume }
        override fun getVolume(): Float = volumeValue
        override fun setMuted(muted: Boolean) { mutedValue = muted }
        override fun isMuted(): Boolean = mutedValue
        override fun setResizeMode(mode: VideoResizeMode) { resizeModeValue = mode }
        override fun getResizeMode(): VideoResizeMode = resizeModeValue
        override fun getAudioTracks(): List<MediaTrackInfo> = emptyList()
        override fun selectAudioTrack(trackId: String): Boolean = false
        override fun getSubtitleTracks(): List<SubtitleTrackInfo> = emptyList()
        override fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {}
        override fun selectSubtitleTrack(trackId: String?): Boolean = false
        override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {}
        override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) {}
        override fun setOnDurationChanged(listener: ((Double) -> Unit)?) {}
        override fun setOnEnded(listener: (() -> Unit)?) {}
        override fun setOnBufferingChanged(listener: ((Boolean) -> Unit)?) {}
        override fun setOnError(listener: ((String) -> Unit)?) {}
    }

    private class FakeWindowNativeImpl : StubNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = true
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        override val supportsNavbarMenuBar: Boolean = true
        override val supportsMobileNavbarMenuBar: Boolean = false
        private var title: String = "GenericUI"
        private var windowSize: Size = Size(1280, 720)
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

    private class FakeMediaTestFactory : NativeFactory {
        val createdWindows = mutableListOf<FakeWindowNativeImpl>()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in VideoFragmentDragAndFullscreenTest")

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

    @Test
    fun videoFragmentDefaultsAndOverlayStageProviderContract() {
        val fragment = VideoFragment(sourceUrl = "https://example.com/clip.mp4")
        val standaloneHost = VideoOverlayHost()

        assertTrue(fragment.windowTitleBarEnabled)
        assertTrue(fragment.windowDragEnabled)
        assertFalse(standaloneHost.windowDragEnabled)

        assertEquals("true", fragment.videoPlayer.getAttribute("data-pip-compatible"))
        assertEquals("true", fragment.overlayHost.getAttribute("data-pip-compatible"))

        val stage: OverlayStageProvider = fragment
        assertSame(fragment.stageOverlayHost, fragment.overlayHost)
        assertSame(fragment.stageTargetComponent, fragment.videoPlayer)
        assertFalse(stage.isStagePlaying)

        fragment.overlayHost.updateState { it.copy(title = "Stage Episode") }
        assertEquals("Stage Episode", stage.stageTitle)
        assertEquals(16 to 9, stage.stageAspectRatio)

        fragment.videoPlayer.setAttribute("data-video-width", "1920")
        fragment.videoPlayer.setAttribute("data-video-height", "1080")
        assertEquals(1920 to 1080, stage.stageAspectRatio)

        stage.toggleStagePlayPause()
        assertTrue(stage.isStagePlaying)
        assertTrue(fragment.isPlaying)

        stage.pauseStagePlayback()
        assertFalse(stage.isStagePlaying)
        assertFalse(fragment.isPlaying)

        standaloneHost.dispose()
        fragment.dispose()
    }

    @Test
    fun bareVideoFragmentWithDisabledTitleBarStillSupportsStageDragClickAndDoubleClickFullscreen() {
        val factory = FakeMediaTestFactory()
        GenericUI.initialize(factory)

        val window = Window(title = "Bare Video Stage", initialSize = Size(1280, 720))
        val fragment = VideoFragment(sourceUrl = "https://example.com/bare.mp4").apply {
            windowTitleBarEnabled = false
        }
        window.setFragment(fragment)
        window.show(factory)

        val fakeWindow = factory.createdWindows.single()
        val bareOverlay = assertIs<DefaultWindowTitleBarOverlay>(
            fragment.overlayHost.overlayRoot.children.firstOrNull()
        )
        assertFalse(bareOverlay.windowTitleBar.config.enabled)
        assertFalse(bareOverlay.windowTitleBar.isVisible)
        assertTrue(bareOverlay.windowDragEnabled)

        // Small jitter + click toggles playback without initiating window drag.
        bareOverlay.onPointerDown?.invoke(PointerEvent(x = 20f, y = 20f))
        assertEquals(0, fakeWindow.startSystemMoveCount)
        bareOverlay.onPointerMove?.invoke(PointerEvent(x = 22f, y = 21f))
        assertEquals(0, fakeWindow.startSystemMoveCount)
        bareOverlay.onPointerUp?.invoke(PointerEvent(x = 22f, y = 21f))
        bareOverlay.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        assertTrue(fragment.isPlaying)

        // Stage drag >= 5px calls Window.startSystemMove() and suppresses release click.
        bareOverlay.onPointerDown?.invoke(PointerEvent(x = 20f, y = 20f))
        bareOverlay.onPointerMove?.invoke(PointerEvent(x = 34f, y = 20f))
        assertEquals(1, fakeWindow.startSystemMoveCount)
        bareOverlay.onPointerUp?.invoke(PointerEvent(x = 34f, y = 20f))
        bareOverlay.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        assertTrue(fragment.isPlaying)

        // Double click toggles fullscreen across fragment, window, and overlayHost state, disabling drag while fullscreen.
        bareOverlay.onDoubleClick?.invoke()
        assertTrue(fragment.isFullscreen())
        assertTrue(window.isFullscreen())
        assertTrue(fragment.overlayHost.state.isFullscreen)

        bareOverlay.onPointerDown?.invoke(PointerEvent(x = 20f, y = 20f))
        bareOverlay.onPointerMove?.invoke(PointerEvent(x = 40f, y = 20f))
        assertEquals(1, fakeWindow.startSystemMoveCount)

        // Second double click exits fullscreen and re-enables windowed stage drag.
        bareOverlay.onDoubleClick?.invoke()
        assertFalse(fragment.isFullscreen())
        assertFalse(window.isFullscreen())
        assertFalse(fragment.overlayHost.state.isFullscreen)

        bareOverlay.onPointerDown?.invoke(PointerEvent(x = 20f, y = 20f))
        bareOverlay.onPointerMove?.invoke(PointerEvent(x = 40f, y = 20f))
        assertEquals(2, fakeWindow.startSystemMoveCount)

        window.close()
    }

    @Test
    fun youtubeOverlayWithMediaHelperIsolatesSeekBarAndVolumeSliderAndSyncsFullscreenAndKeyboardShortcut() {
        val factory = FakeMediaTestFactory()
        GenericUI.initialize(factory)

        val ytOverlay = YoutubePlayerOverlay()
        val mediaHelper = YoutubeMediaOverlayHelper(ytOverlay)
        val window = Window(title = "YouTube Stage", initialSize = Size(1280, 720))
        val fragment = VideoFragment(
            sourceUrl = "https://example.com/yt.mp4",
            initialOverlay = ytOverlay
        )
        window.setFragment(fragment)
        window.show(factory)

        val fakeWindow = factory.createdWindows.single()
        assertTrue(fragment.windowTitleBar.isVisible)
        assertTrue(fragment.windowDragEnabled)
        assertTrue(ytOverlay.windowDragEnabled)

        // SeekBar & ExpandingVolumeSlider controls stay strictly isolated from stage drag, play/pause, and fullscreen.
        val volumeSlider = mediaHelper.volumeSlider
        val isolatedMediaControls = buildList {
            add(mediaHelper.seekBar)
            addAll(mediaHelper.seekBar.mediaCompositeChildren())
            if (volumeSlider != null) {
                add(volumeSlider)
                addAll(volumeSlider.mediaCompositeChildren())
            }
        }
        for (control in isolatedMediaControls) {
            control.onPointerDown?.invoke(PointerEvent(x = 12f, y = 12f))
            control.onPointerMove?.invoke(PointerEvent(x = 45f, y = 12f))
            control.onPointerUp?.invoke(PointerEvent(x = 45f, y = 12f))
            control.onClick?.invoke()
            control.onDoubleClick?.invoke()
            UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        }
        assertEquals(0, fakeWindow.startSystemMoveCount)
        assertFalse(fragment.isPlaying)
        assertFalse(fragment.isFullscreen())
        assertFalse(window.isFullscreen())

        // Dragging on centerZone moves the window once past slop.
        ytOverlay.centerZone.onPointerDown?.invoke(PointerEvent(x = 100f, y = 100f))
        ytOverlay.centerZone.onPointerMove?.invoke(PointerEvent(x = 120f, y = 100f))
        assertEquals(1, fakeWindow.startSystemMoveCount)
        ytOverlay.centerZone.onPointerUp?.invoke(PointerEvent(x = 120f, y = 100f))
        UiTimerScheduler.advanceTimeBy(1)

        // Single click on centerZone toggles play/pause.
        ytOverlay.centerZone.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)
        assertTrue(fragment.isPlaying)

        // 'f' keyboard shortcut toggles fullscreen across Window, Fragment, OverlayState, FullscreenButton, and titlebar.
        window.triggerKeyDown(KeyEvent(key = "f"))
        assertTrue(window.isFullscreen())
        assertTrue(fragment.isFullscreen())
        assertTrue(fragment.overlayHost.state.isFullscreen)
        assertTrue(ytOverlay.state.isFullscreen)
        assertTrue(ytOverlay.fullscreenButton.isFullscreen)
        assertFalse(fragment.windowTitleBar.isVisible)

        // Clicking overlay fullscreenButton exits fullscreen cleanly and restores windowed titlebar.
        ytOverlay.fullscreenButton.onClick?.invoke()
        assertFalse(window.isFullscreen())
        assertFalse(fragment.isFullscreen())
        assertFalse(fragment.overlayHost.state.isFullscreen)
        assertFalse(ytOverlay.state.isFullscreen)
        assertFalse(ytOverlay.fullscreenButton.isFullscreen)
        assertTrue(fragment.windowTitleBar.isVisible)

        mediaHelper.dispose()
        window.close()
    }
}
