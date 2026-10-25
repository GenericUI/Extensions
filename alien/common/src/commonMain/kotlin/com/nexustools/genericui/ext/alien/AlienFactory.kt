package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.alien.impl.*
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.nativeimpl.content.TableNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.dock.*
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
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl
import com.nexustools.genericui.nativeimpl.shell.*
import com.nexustools.genericui.nativeimpl.surface.CollapseNativeImpl
import com.nexustools.genericui.nativeimpl.system.*
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl

/**
 * [NativeFactory] implementation that provides self-rendered `*AlienImpl` instances for every
 * component in `:common`, while delegating OS-level `Application`, `Window`, `FileBrowserDialog`,
 * and system services (`PermissionManager`, `SystemTray`, `NotificationManager`, `NetworkMonitor`,
 * `PreferenceStore`) to [hostFactory].
 */
class AlienFactory(
    val hostFactory: NativeFactory,
    val session: AlienRenderSession? = null
) : NativeFactory {

    override val backendName: String
        get() = "alien(${hostFactory.backendName})"

    // Shell & Window (OS Window/Application delegated to hostFactory; UI chrome self-rendered)
    override fun createApplication(): ApplicationNativeImpl<*> = hostFactory.createApplication()
    override fun createWindow(): WindowNativeImpl<*> = hostFactory.createWindow()
    override fun createMenuBar(): MenuBarNativeImpl<*> = MenuBarAlienImpl(session)
    override fun createMenu(): MenuNativeImpl<*> = MenuAlienImpl(session)
    override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> =
        MenuItemAlienImpl(checkable = checkable, radio = radio, session = session)
    override fun createContextMenu(): ContextMenuNativeImpl<*> = ContextMenuAlienImpl(session)
    override fun createSplitPane(): SplitPaneNativeImpl<*> = SplitPaneAlienImpl(session)

    // Dockable Panel System
    override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = DockWorkspaceAlienImpl(session)
    override fun createDockArea(): DockAreaNativeImpl<*> = DockAreaAlienImpl(session)
    override fun createDockGroup(): DockGroupNativeImpl<*> = DockGroupAlienImpl(session)
    override fun createDockPanel(): DockPanelNativeImpl<*> = DockPanelAlienImpl(session)
    override fun createDockWindow(): DockWindowNativeImpl<*> = DockWindowAlienImpl(session)
    override fun createToolPalette(): ToolPaletteNativeImpl<*> = ToolPaletteAlienImpl(session)

    // Layout & Printable Book Documents
    override fun createContainer(classPrefix: String): ContainerNativeImpl<*> =
        ContainerAlienImpl(componentClassPrefix = classPrefix, tagName = "div", session = session)

    override fun createContainer(classPrefix: String, tagName: String): ContainerNativeImpl<*> =
        ContainerAlienImpl(componentClassPrefix = classPrefix, tagName = tagName, session = session)

    override fun createBookContainer(): BookContainerNativeImpl<*> =
        BookContainerAlienImpl(hostFactory = hostFactory, session = session)

    override fun createScrollView(): ScrollViewNativeImpl<*> = ScrollViewAlienImpl(session)
    override fun createDivider(): DividerNativeImpl<*> = DividerAlienImpl(session)

    // Typography & Content
    override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> =
        TextAlienImpl(tagName = tagName, componentClassPrefix = classPrefix, session = session)

    override fun createMarkdownView(): MarkdownViewNativeImpl<*> = MarkdownViewAlienImpl(session)
    override fun createImage(): ImageNativeImpl<*> = ImageAlienImpl(session)
    override fun createTable(): TableNativeImpl<*> = TableAlienImpl(session)

    // Action & Form
    override fun createButton(classPrefix: String): ButtonNativeImpl<*> =
        ButtonAlienImpl(componentClassPrefix = classPrefix, session = session)

    override fun createDropdown(): DropdownNativeImpl<*> = DropdownAlienImpl(session)
    override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> =
        TextInputAlienImpl(multiline = multiline, session = session)

    override fun createSelect(): SelectNativeImpl<*> = SelectAlienImpl(session)
    override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> =
        CheckboxAlienImpl(switchStyle = switchStyle, radioStyle = radioStyle, session = session)

    override fun createRangeSlider(): RangeSliderNativeImpl<*> = RangeSliderAlienImpl(session)

    // Surface, Tabs, Navigation, Feedback & Overlay
    override fun createCollapse(): CollapseNativeImpl<*> = CollapseAlienImpl(session)
    override fun createTab(closable: Boolean): TabNativeImpl<*> =
        TabAlienImpl(initialClosable = closable, session = session)

    override fun createTabContainer(): TabContainerNativeImpl<*> = TabContainerAlienImpl(session)
    override fun createNavbar(): NavbarNativeImpl<*> = NavbarAlienImpl(session)
    override fun createBottomNavigationBar(): BottomNavigationBarNativeImpl<*> =
        BottomNavigationBarAlienImpl(session)

    override fun createProgressBar(): ProgressBarNativeImpl<*> = ProgressBarAlienImpl(session)
    override fun createSpinner(): SpinnerNativeImpl<*> = SpinnerAlienImpl(session)
    override fun createPopover(): PopoverNativeImpl<*> = PopoverAlienImpl(session)

    // Dialogs & Native File/Folder Browser
    override fun createDialogWindow(): DialogWindowNativeImpl<*> = DialogWindowAlienImpl(session)
    override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl = hostFactory.createFileBrowserDialog()
    override fun createStandardDialogs(): StandardDialogsNativeImpl = hostFactory.createStandardDialogs()

    // Lazy Virtualized Lists, Grids & Trees
    override fun createLazyList(): LazyListNativeImpl<*> = LazyListAlienImpl(session)
    override fun createLazyGrid(): LazyGridNativeImpl<*> = LazyGridAlienImpl(session)
    override fun createTreeView(): TreeViewNativeImpl<*> = TreeViewAlienImpl(session)

    // System Services (delegated to hostFactory)
    override fun createPermissionManager(): PermissionManagerNativeImpl = hostFactory.createPermissionManager()
    override fun createSystemTray(): SystemTrayNativeImpl = hostFactory.createSystemTray()
    override fun createNotificationManager(): NotificationManagerNativeImpl = hostFactory.createNotificationManager()
    override fun createNetworkMonitor(): NetworkMonitorNativeImpl = hostFactory.createNetworkMonitor()
    override fun createPreferenceStore(appName: String): PreferenceStoreNativeImpl =
        hostFactory.createPreferenceStore(appName)
    override fun createConfigDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        hostFactory.createConfigDirectory(appName, subdirectory)
    override fun createCacheDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        hostFactory.createCacheDirectory(appName, subdirectory)

    companion object {
        /**
         * Unwraps any nested [AlienFactory] instances to return the underlying host OS [NativeFactory].
         */
        fun unwrapHostFactory(factory: NativeFactory?): NativeFactory? {
            var cur = factory
            while (cur is AlienFactory) {
                cur = cur.hostFactory
            }
            return cur ?: GenericUI.activeFactory?.let { active ->
                var f: NativeFactory? = active
                while (f is AlienFactory) {
                    f = f.hostFactory
                }
                f
            }
        }
    }
}
