package com.nexustools.genericui.ext.webview.desktop.engine

import com.nexustools.genericui.component.layout.LayoutFactory
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.alien.AlienExtension
import com.nexustools.genericui.ext.alien.AlienFactory
import com.nexustools.genericui.ext.alien.system.SystemAlienBootstrap
import com.nexustools.genericui.ext.canvas2d.alien.Canvas2DAlienExtension
import com.nexustools.genericui.ext.canvas3d.alien.Canvas3DAlienExtension
import com.nexustools.genericui.ext.media.alien.MediaAlienExtension
import com.nexustools.genericui.ext.svg.alien.SvgAlienExtension
import com.nexustools.genericui.image.ImageLoader
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
import com.nexustools.genericui.nativeimpl.io.FileSystemNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import com.nexustools.genericui.nativeimpl.net.HttpClientNativeImpl
import com.nexustools.genericui.nativeimpl.net.TcpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.UdpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.WebSocketNativeImpl
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.nativeimpl.system.AnimationNativeImpl
import com.nexustools.genericui.nativeimpl.system.DatabaseNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PreferenceStoreNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.nativeimpl.system.UiTimerNativeImpl
import com.nexustools.genericui.nativeimpl.system.UserDirectoryNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl
import com.nexustools.genericui.resources.ResourceResolverNativeImpl
import com.nexustools.genericui.threading.UiThreadDispatcherNativeImpl

/**
 * Headless [NativeFactory] installed inside each isolated WebView worker OS process.
 *
 * Delegates all visual widget creation to [AlienFactory], installs Alien + companion extensions
 * (`svg`, `canvas2d`, `canvas3d`, `media`), and routes I/O, image codecs, and networking
 * through [WorkerPlatformServices] without creating any GUI toolkit window.
 */
class WorkerAlienHostFactory : NativeFactory {
    override val backendTarget: BackendTarget = WorkerPlatformServices.backendTarget
    override val backendName: String = WorkerPlatformServices.backendName
    override val layoutFactory: LayoutFactory = WorkerPlatformServices.layoutFactory
    override val supportsNativeHtmlLabelFor: Boolean
        get() = false
    override val ignoresAestheticStylesOnStandardComponents: Boolean
        get() = alienDelegate.ignoresAestheticStylesOnStandardComponents

    private val alienDelegate: AlienFactory = AlienFactory(this)

    init {
        SystemAlienBootstrap.install()
        AlienExtension.useSystemCanvasRenderer()
        SvgAlienExtension.register()
        Canvas2DAlienExtension.register()
        Canvas3DAlienExtension.register()
        MediaAlienExtension.register()
        WorkerPlatformServices.createImageCodec()?.let {
            ImageLoader.activeNativeCodec = it
        }
        GenericUI.activeFactory = this
    }

    override fun supportsTcp(): Boolean = true
    override fun supportsUdp(): Boolean = true

    override fun createApplication(): ApplicationNativeImpl<*> =
        throw UnsupportedOperationException("Headless WebView worker does not instantiate Application")

    override fun createWindow(): WindowNativeImpl<*> =
        throw UnsupportedOperationException("Headless WebView worker does not instantiate OS Windows")

    override fun createMenuBar(): MenuBarNativeImpl<*> = alienDelegate.createMenuBar()
    override fun createMenu(): MenuNativeImpl<*> = alienDelegate.createMenu()
    override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> =
        alienDelegate.createMenuItem(checkable, radio)
    override fun createContextMenu(): ContextMenuNativeImpl<*> = alienDelegate.createContextMenu()
    override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = alienDelegate.createDockWorkspace()
    override fun createDockArea(): DockAreaNativeImpl<*> = alienDelegate.createDockArea()
    override fun createDockGroup(): DockGroupNativeImpl<*> = alienDelegate.createDockGroup()
    override fun createDockPanel(): DockPanelNativeImpl<*> = alienDelegate.createDockPanel()
    override fun createDockWindow(): DockWindowNativeImpl<*> = alienDelegate.createDockWindow()
    override fun createToolPalette(): ToolPaletteNativeImpl<*> = alienDelegate.createToolPalette()
    override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = alienDelegate.createContainer(classPrefix)
    override fun createContainer(classPrefix: String, tagName: String): ContainerNativeImpl<*> =
        alienDelegate.createContainer(classPrefix, tagName)
    override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> =
        alienDelegate.createStyledContainer(classPrefix, tagName)
    override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> =
        alienDelegate.createStyledComponent(classPrefix, tagName)
    override fun createBookContainer(): BookContainerNativeImpl<*> = alienDelegate.createBookContainer()
    override fun createScrollView(): ScrollViewNativeImpl<*> = alienDelegate.createScrollView()
    override fun createDivider(): DividerNativeImpl<*> = alienDelegate.createDivider()
    override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> =
        alienDelegate.createText(tagName, classPrefix)
    override fun createMarkdownView(): MarkdownViewNativeImpl<*> = alienDelegate.createMarkdownView()
    override fun createImage(): ImageNativeImpl<*> = alienDelegate.createImage()
    override fun createTable(): TableNativeImpl<*> = alienDelegate.createTable()
    override fun createButton(classPrefix: String): ButtonNativeImpl<*> = alienDelegate.createButton(classPrefix)
    override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> =
        alienDelegate.createStyledButton(classPrefix)
    override fun createDropdown(): DropdownNativeImpl<*> = alienDelegate.createDropdown()
    override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> = alienDelegate.createTextInput(multiline)
    override fun createStyledTextInput(multiline: Boolean, classPrefix: String): StyledTextInputNativeImpl<*> =
        alienDelegate.createStyledTextInput(multiline, classPrefix)
    override fun createSelect(): SelectNativeImpl<*> = alienDelegate.createSelect()
    override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> =
        alienDelegate.createCheckbox(switchStyle, radioStyle)
    override fun createRangeSlider(): RangeSliderNativeImpl<*> = alienDelegate.createRangeSlider()
    override fun createTab(closable: Boolean): TabNativeImpl<*> = alienDelegate.createTab(closable)
    override fun createTabContainer(): TabContainerNativeImpl<*> = alienDelegate.createTabContainer()
    override fun createNavbar(): NavbarNativeImpl<*> = alienDelegate.createNavbar()
    override fun createBottomNavigationBar(): BottomNavigationBarNativeImpl<*> = alienDelegate.createBottomNavigationBar()
    override fun createProgressBar(): ProgressBarNativeImpl<*> = alienDelegate.createProgressBar()
    override fun createSpinner(): SpinnerNativeImpl<*> = alienDelegate.createSpinner()
    override fun createPopover(): PopoverNativeImpl<*> = alienDelegate.createPopover()
    override fun createDialogWindow(): DialogWindowNativeImpl<*> = alienDelegate.createDialogWindow()
    override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl =
        throw UnsupportedOperationException("Headless WebView worker does not instantiate FileBrowserDialog")
    override fun createStandardDialogs(): StandardDialogsNativeImpl =
        throw UnsupportedOperationException("Headless WebView worker does not instantiate StandardDialogs")
    override fun createLazyList(): LazyListNativeImpl<*> = alienDelegate.createLazyList()
    override fun createLazyGrid(): LazyGridNativeImpl<*> = alienDelegate.createLazyGrid()
    override fun createTreeView(): TreeViewNativeImpl<*> = alienDelegate.createTreeView()
    override fun createPermissionManager(): PermissionManagerNativeImpl =
        throw UnsupportedOperationException("Headless WebView worker does not instantiate PermissionManager")
    override fun createSystemTray(): SystemTrayNativeImpl =
        throw UnsupportedOperationException("Headless WebView worker does not instantiate SystemTray")
    override fun createNotificationManager(): NotificationManagerNativeImpl =
        throw UnsupportedOperationException("Headless WebView worker does not instantiate NotificationManager")
    override fun createNetworkMonitor(): NetworkMonitorNativeImpl =
        throw UnsupportedOperationException("Headless WebView worker does not instantiate NetworkMonitor")

    override fun createUiThreadDispatcher(): UiThreadDispatcherNativeImpl = WorkerPlatformServices.createUiThreadDispatcher()
    override fun createUiTimerScheduler(): UiTimerNativeImpl = WorkerPlatformServices.createUiTimerScheduler()
    override fun createAnimationScheduler(): AnimationNativeImpl = WorkerPlatformServices.createAnimationScheduler()
    override fun createPreferenceStore(appName: String): PreferenceStoreNativeImpl =
        WorkerPlatformServices.createPreferenceStore(appName)
    override fun createConfigDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        WorkerPlatformServices.createConfigDirectory(appName, subdirectory)
    override fun createCacheDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        WorkerPlatformServices.createCacheDirectory(appName, subdirectory)
    override fun createFileSystem(): FileSystemNativeImpl = WorkerPlatformServices.createFileSystem()
    override fun createDatabase(databaseName: String, filePath: String?, version: Int): DatabaseNativeImpl =
        WorkerPlatformServices.createDatabase(databaseName, filePath, version)
    override fun createResourceResolver(): ResourceResolverNativeImpl = WorkerPlatformServices.createResourceResolver()
    override fun createTcpSocket(): TcpSocketNativeImpl = WorkerPlatformServices.createTcpSocket()
    override fun createUdpSocket(): UdpSocketNativeImpl = WorkerPlatformServices.createUdpSocket()
    override fun createHttpClient(): HttpClientNativeImpl = WorkerPlatformServices.createHttpClient()
    override fun createWebSocket(): WebSocketNativeImpl = WorkerPlatformServices.createWebSocket()
}
