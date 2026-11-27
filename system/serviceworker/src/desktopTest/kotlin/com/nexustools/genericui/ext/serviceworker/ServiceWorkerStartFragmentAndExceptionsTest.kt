@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.shell.FragmentApplication
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiExtensionRegistry
import com.nexustools.genericui.core.GenericUiLaunchSequence
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
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
import com.nexustools.genericui.enums.dialog.DialogPresentationStyle
import com.nexustools.genericui.enums.fragment.FragmentState
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.exception.FragmentMultipleWindowsUnsupportedException
import com.nexustools.genericui.exception.FragmentNotRegisteredException
import com.nexustools.genericui.exception.MultipleWindowsUnsupportedException
import com.nexustools.genericui.exception.SystemTrayUnsupportedException
import com.nexustools.genericui.exception.UnsupportedBackendCapabilityException
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
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
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
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.threading.GenericUiThreading
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ServiceWorkerStartFragmentAndExceptionsTest {

    private class MainFrag : Fragment() {
        init {
            pageTitle = "Main"
        }
    }

    private class SingletonFrag : Fragment() {
        init {
            pageTitle = "Singleton"
        }
    }

    private class MultiWindowFrag : Fragment() {
        init {
            pageTitle = "Multi Window"
        }
    }

    private class SingletonDialogFrag : Fragment() {
        var startedCount: Int = 0
        var resumedCount: Int = 0
        var pausedCount: Int = 0
        var stoppedCount: Int = 0
        var destroyedCount: Int = 0

        init {
            pageTitle = "Settings Dialog"
        }

        override fun onStart() {
            startedCount++
        }

        override fun onResume() {
            resumedCount++
        }

        override fun onPause() {
            pausedCount++
        }

        override fun onStop() {
            stoppedCount++
        }

        override fun onDestroy() {
            destroyedCount++
        }
    }

    private class UnregisteredFrag : Fragment()

    private class TestWorker : ServiceWorker()

    private class CapabilityMockRuntime(
        private val backendTarget: BackendTarget,
        override val isSupported: Boolean,
        override val supportsMultipleWindows: Boolean,
        override val supportsFragmentLaunch: Boolean,
        override val supportsDialogFragments: Boolean,
        override val supportsSystemTray: Boolean,
        override val supportsOngoingNotification: Boolean,
        override val supportsAutostart: Boolean,
        private val autostartApplyResult: Boolean = supportsAutostart,
        private val connectShouldSucceed: Boolean = false
    ) : ServiceWorkerPlatformRuntime, ServiceWorkerRuntimeHost {

        override val backendName: String = backendTarget.displayName
        val launchRequests = mutableListOf<ServiceWorkerFragmentLaunchRequest>()

        override fun configure(options: ServiceWorkerOptions) = Unit
        override fun isClientConnected(): Boolean = false

        override suspend fun connectClient(
            options: ServiceWorkerOptions,
            onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
            onConnectionChanged: (Boolean) -> Unit
        ): Boolean = connectShouldSucceed

        override suspend fun disconnectClient() = Unit
        override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean = false

        override fun setAutostartEnabled(options: ServiceWorkerOptions, enabled: Boolean): Boolean =
            autostartApplyResult

        override fun updateNotification(
            title: String,
            text: String,
            subText: String?,
            icon: ResourceID?,
            actions: List<ServiceWorkerNotificationAction>
        ) = Unit

        override fun updateTray(
            iconPathOrUrl: String?,
            iconResource: ResourceID?,
            tooltip: String?,
            badgeText: String?,
            menu: ContextMenu?,
            onClick: (() -> Unit)?,
            macosIconPathOrUrl: String?
        ) = Unit

        override fun openMainWindow(route: String?) = Unit
        override fun closeMainWindow() = Unit

        override fun requestLaunchFragment(request: ServiceWorkerFragmentLaunchRequest) {
            launchRequests.add(request)
        }

        override fun requestCloseFragment(fragmentId: String, instanceId: String?) = Unit
        override fun stopSelf() = Unit
        override fun exitApplication(exitCode: Int) = Unit
        override fun isAutostartEnabled(): Boolean = false
        override fun setAutostartEnabled(enabled: Boolean): Boolean = autostartApplyResult
        override suspend fun sendToClient(clientId: String, envelope: ServiceWorkerEnvelope): Boolean = false
        override suspend fun broadcastEnvelope(envelope: ServiceWorkerEnvelope): Int = 0
        override fun disconnectClient(clientId: String) = Unit
        override fun currentBackendTarget(): BackendTarget = backendTarget
    }

    private class InMemoryCoordinatorRuntime(
        private val worker: ServiceWorker
    ) : ServiceWorkerPlatformRuntime, ServiceWorkerRuntimeHost {

        override val backendName: String = BackendTarget.Swing.displayName
        override val isSupported: Boolean = true
        override val supportsMultipleWindows: Boolean = true
        override val supportsFragmentLaunch: Boolean = true
        override val supportsDialogFragments: Boolean = true
        override val supportsSystemTray: Boolean = true
        override val supportsOngoingNotification: Boolean = false
        override val supportsAutostart: Boolean = false

        @Volatile
        private var connected: Boolean = false

        @Volatile
        private var incomingToUi: ((ServiceWorkerEnvelope) -> Unit)? = null

        @Volatile
        private var connectionChanged: ((Boolean) -> Unit)? = null

        override fun configure(options: ServiceWorkerOptions) = Unit
        override fun isClientConnected(): Boolean = connected

        override fun ensureBackgroundStarted(
            options: ServiceWorkerOptions,
            workerFactory: (() -> ServiceWorker)?
        ): Boolean {
            if (!worker.isRunning) {
                worker.attachAndStart(this, options)
            }
            return true
        }

        override suspend fun connectClient(
            options: ServiceWorkerOptions,
            onIncomingEnvelope: (ServiceWorkerEnvelope) -> Unit,
            onConnectionChanged: (Boolean) -> Unit
        ): Boolean {
            if (!worker.isRunning) {
                worker.attachAndStart(this, options)
            }
            incomingToUi = onIncomingEnvelope
            connectionChanged = onConnectionChanged
            connected = true
            worker.dispatchClientConnected("ui-main")
            onConnectionChanged(true)
            return true
        }

        override suspend fun disconnectClient() {
            if (connected) {
                connected = false
                connectionChanged?.invoke(false)
                worker.dispatchClientDisconnected("ui-main")
            }
        }

        override suspend fun sendClientEnvelope(envelope: ServiceWorkerEnvelope): Boolean {
            if (!connected) return false
            worker.dispatchIncomingEnvelope("ui-main", envelope.copy(clientId = "ui-main"))
            return true
        }

        override fun updateNotification(
            title: String,
            text: String,
            subText: String?,
            icon: ResourceID?,
            actions: List<ServiceWorkerNotificationAction>
        ) {
            throw ServiceWorkerOngoingNotificationUnsupportedException(backendName)
        }

        override fun updateTray(
            iconPathOrUrl: String?,
            iconResource: ResourceID?,
            tooltip: String?,
            badgeText: String?,
            menu: ContextMenu?,
            onClick: (() -> Unit)?,
            macosIconPathOrUrl: String?
        ) = Unit

        override fun openMainWindow(route: String?) {
            incomingToUi?.invoke(
                ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                    correlationId = 0L,
                    clientId = "ui-main",
                    typeId = ServiceWorkerEnvelope.CONTROL_OPEN_MAIN_WINDOW,
                    payload = route?.let { PreferenceElement.ofString(it) } ?: PreferenceElement.Null
                )
            )
        }

        override fun closeMainWindow() {
            incomingToUi?.invoke(
                ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                    correlationId = 0L,
                    clientId = "ui-main",
                    typeId = ServiceWorkerEnvelope.CONTROL_CLOSE_MAIN_WINDOW,
                    payload = PreferenceElement.Null
                )
            )
        }

        override fun requestLaunchFragment(request: ServiceWorkerFragmentLaunchRequest) {
            incomingToUi?.invoke(
                ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                    correlationId = 0L,
                    clientId = "ui-main",
                    typeId = ServiceWorkerEnvelope.CONTROL_LAUNCH_FRAGMENT,
                    payload = request.toControlPayloadElement(),
                    targetFragmentId = request.fragmentId
                )
            )
        }

        override fun requestCloseFragment(fragmentId: String, instanceId: String?) {
            incomingToUi?.invoke(
                ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                    correlationId = 0L,
                    clientId = "ui-main",
                    typeId = ServiceWorkerEnvelope.CONTROL_CLOSE_FRAGMENT,
                    payload = ServiceWorkerFragmentLaunchRequest.encodeCloseControlPayload(fragmentId, instanceId),
                    targetFragmentId = fragmentId
                )
            )
        }

        override fun stopSelf() = Unit
        override fun exitApplication(exitCode: Int) = Unit
        override fun isAutostartEnabled(): Boolean = false
        override fun setAutostartEnabled(enabled: Boolean): Boolean = false

        override suspend fun sendToClient(clientId: String, envelope: ServiceWorkerEnvelope): Boolean {
            if (!connected) return false
            incomingToUi?.invoke(envelope)
            return true
        }

        override suspend fun broadcastEnvelope(envelope: ServiceWorkerEnvelope): Int {
            if (!connected) return 0
            incomingToUi?.invoke(envelope)
            return 1
        }

        override fun disconnectClient(clientId: String) = Unit
        override fun activeWorker(): ServiceWorker = worker
        override fun currentBackendTarget(): BackendTarget = BackendTarget.Swing
    }

    private open class StubNativeImpl(val tag: String) : NativeImpl<Any> {
        override val nativeWidget: Any = tag
        override val componentClassPrefix: String = tag
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
        var visibleFlag: Boolean = true
        private var enabled: Boolean = true
        private var opacity: Float = 1f
        private var tooltip: String? = null

        override fun dispose() { isDisposed = true }
        override fun setClass(className: String) { this.className = className }
        override fun getClass(): String = className
        override fun addClass(className: String) { this.className = className }
        override fun removeClass(className: String) {}
        override fun hasClass(className: String): Boolean = this.className == className
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
        override fun setVisible(visible: Boolean) { this.visibleFlag = visible }
        override fun isVisible(): Boolean = visibleFlag
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

    private class FakeWindowNativeImpl : StubNativeImpl("window"), WindowNativeImpl<Any> {
        override val supportsCustomTitleBar: Boolean = true
        override val supportsEmbeddedMenuBarInTitleBar: Boolean = true
        override val supportsMultiWindow: Boolean = true
        var showWindowCalls: Int = 0
        var closeWindowCalls: Int = 0
        var bringToFrontCalls: Int = 0
        private var title: String = ""
        private var size: Size = Size(1280, 800)
        private var state: WindowState = WindowState.NORMAL
        private var fullscreen: Boolean = false
        private var undecorated: Boolean = false

        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setWindowSize(size: Size) { this.size = size }
        override fun getWindowSize(): Size = size
        override fun setWindowState(state: WindowState) { this.state = state }
        override fun getWindowState(): WindowState = state
        override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) { this.fullscreen = fullscreen }
        override fun isFullscreen(): Boolean = fullscreen
        override fun applyTitleBarInstructions(titleBar: TitleBar?) {}
        override fun setMenuBarNative(menuBarImpl: MenuBarNativeImpl<*>?) {}
        override fun setNavBarNative(navBarImpl: NativeImpl<*>?) {}
        override fun setBottomNavigationBarNative(bottomNavImpl: NativeImpl<*>?) {}
        override fun isUndecorated(): Boolean = undecorated
        override fun setUndecorated(undecorated: Boolean) { this.undecorated = undecorated }
        override fun showWindow() { showWindowCalls++ }
        override fun closeWindow() { closeWindowCalls++ }
        override fun bringToFront() { bringToFrontCalls++ }
        override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {}
        override fun setOnWindowStateChanged(listener: ((WindowState) -> Unit)?) {}
        override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {}
        override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {}
    }

    private class FakeDialogWindowNativeImpl : StubNativeImpl("dialog-window"), DialogWindowNativeImpl<Any> {
        private var title: String = ""
        private var style: DialogPresentationStyle = DialogPresentationStyle.MODAL_CENTERED
        private var onClosed: (() -> Unit)? = null
        var showDialogCalls: Int = 0
        var closeDialogCalls: Int = 0
        var lastOwnerWindow: WindowNativeImpl<*>? = null

        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setPresentationStyle(style: DialogPresentationStyle) { this.style = style }
        override fun getPresentationStyle(): DialogPresentationStyle = style
        override fun setModal(modal: Boolean) {}
        override fun setFullscreen(fullscreen: Boolean) {}

        override fun showDialog(ownerWindow: WindowNativeImpl<*>?) {
            showDialogCalls++
            lastOwnerWindow = ownerWindow
        }

        override fun closeDialog() {
            closeDialogCalls++
        }

        override fun setOnDialogClosed(listener: (() -> Unit)?) {
            onClosed = listener
        }

        fun simulateUserClosed() {
            onClosed?.invoke()
        }
    }

    private class FakeApplicationNativeImpl : ApplicationNativeImpl<Any> {
        override val nativeApplication: Any = "fake-app"
        override var isDisposed: Boolean = false
        private var mode: ThemeMode = ThemeMode.AUTO
        var exitCalls: Int = 0
        var lastExitCode: Int? = null

        override fun dispose() { isDisposed = true }
        override fun setThemeMode(mode: ThemeMode) { this.mode = mode }
        override fun getThemeMode(): ThemeMode = mode
        override fun runEventLoop(onStarted: () -> Unit) {
            onStarted()
        }
        override fun exitApplication(exitCode: Int) {
            exitCalls++
            lastExitCode = exitCode
        }
    }

    private class FakeNetworkMonitorNativeImpl : NetworkMonitorNativeImpl {
        override var isDisposed: Boolean = false
        override fun dispose() { isDisposed = true }
        override fun isOnline(): Boolean = true
        override fun setOnConnectivityChanged(listener: ((Boolean) -> Unit)?) {}
    }

    private class FakeDesktopFactory : NativeFactory {
        val windows = mutableListOf<FakeWindowNativeImpl>()
        val dialogWindows = mutableListOf<FakeDialogWindowNativeImpl>()
        val apps = mutableListOf<FakeApplicationNativeImpl>()
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in test")
        override val backendTarget: BackendTarget = BackendTarget.Swing
        override fun createApplication(): ApplicationNativeImpl<*> = FakeApplicationNativeImpl().also { apps.add(it) }
        override fun createWindow(): WindowNativeImpl<*> = FakeWindowNativeImpl().also { windows.add(it) }
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
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> = unsupported()
        override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> = unsupported()
        override fun createBookContainer(): BookContainerNativeImpl<*> = unsupported()
        override fun createScrollView(): ScrollViewNativeImpl<*> = unsupported()
        override fun createDivider(): DividerNativeImpl<*> = unsupported()
        override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = unsupported()
        override fun createMarkdownView(): MarkdownViewNativeImpl<*> = unsupported()
        override fun createImage(): ImageNativeImpl<*> = unsupported()
        override fun createTable(): TableNativeImpl<*> = unsupported()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = unsupported()
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> = unsupported()
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
        override fun createSpinner(): SpinnerNativeImpl<*> = unsupported()
        override fun createPopover(): PopoverNativeImpl<*> = unsupported()
        override fun createDialogWindow(): DialogWindowNativeImpl<*> = FakeDialogWindowNativeImpl().also { dialogWindows.add(it) }
        override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl = unsupported()
        override fun createStandardDialogs(): StandardDialogsNativeImpl = unsupported()
        override fun createLazyList(): LazyListNativeImpl<*> = unsupported()
        override fun createLazyGrid(): LazyGridNativeImpl<*> = unsupported()
        override fun createTreeView(): TreeViewNativeImpl<*> = unsupported()
        override fun createPermissionManager(): PermissionManagerNativeImpl = unsupported()
        override fun createSystemTray(): SystemTrayNativeImpl = unsupported()
        override fun createNotificationManager(): NotificationManagerNativeImpl = unsupported()
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = FakeNetworkMonitorNativeImpl()
        override fun supportsTcp(): Boolean = false
        override fun supportsUdp(): Boolean = false
    }

    private var savedFactory: NativeFactory? = null

    @BeforeTest
    fun setUp() {
        savedFactory = GenericUI.activeFactory
        ServiceWorkerExtension.resetForTests()
        FragmentRegistry.clear()
        GenericUiExtensionRegistry.clear()
        Application.current = null
    }

    @AfterTest
    fun tearDown() {
        ServiceWorkerExtension.resetForTests()
        FragmentRegistry.clear()
        GenericUiExtensionRegistry.clear()
        GenericUI.activeFactory = savedFactory
        Application.current = null
    }

    private fun registerStandardMetadata() {
        FragmentRegistry.register(MainFrag::class, multipleWindows = false, dialog = false) { MainFrag() }
        FragmentRegistry.registerMetadata(
            fragmentClass = MainFrag::class,
            fragmentId = "com.test.MainFrag",
            route = "/main",
            label = "Main",
            isMainFragment = true,
            multipleWindows = false,
            dialog = false
        )
        FragmentRegistry.register(SingletonFrag::class, multipleWindows = false, dialog = false) { SingletonFrag() }
        FragmentRegistry.registerMetadata(
            fragmentClass = SingletonFrag::class,
            fragmentId = "com.test.SingletonFrag",
            route = "/singleton",
            label = "Singleton",
            isMainFragment = false,
            multipleWindows = false,
            dialog = false
        )
        FragmentRegistry.register(MultiWindowFrag::class, multipleWindows = true, dialog = false) { MultiWindowFrag() }
        FragmentRegistry.registerMetadata(
            fragmentClass = MultiWindowFrag::class,
            fragmentId = "com.test.MultiWindowFrag",
            route = "/multi",
            label = "Multi",
            isMainFragment = false,
            multipleWindows = true,
            dialog = false
        )
        FragmentRegistry.register(SingletonDialogFrag::class, multipleWindows = false, dialog = true) { SingletonDialogFrag() }
        FragmentRegistry.registerMetadata(
            fragmentClass = SingletonDialogFrag::class,
            fragmentId = "com.test.SingletonDialogFrag",
            route = "/dialog",
            label = "Dialog",
            isMainFragment = false,
            multipleWindows = false,
            dialog = true
        )
    }

    private suspend fun waitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!predicate()) {
                GenericUiThreading.flushAllQueues()
                delay(10L)
            }
            GenericUiThreading.flushAllQueues()
        }
    }

    private fun createCoordinatorDescriptor(
        worker: ServiceWorker,
        runtime: InMemoryCoordinatorRuntime
    ): GenericUiAppDescriptor = GenericUiAppDescriptor(
        appId = "com.test.swdialog",
        appName = "SwDialogApp",
        mainFragmentClass = MainFrag::class,
        mainFragmentFactory = { MainFrag() },
        applicationFactory = { FragmentApplication(MainFrag()) },
        providerInstallers = listOf {
            ServiceWorkerExtension.install(
                ServiceWorkerOptions(
                    appId = "com.test.swdialog",
                    appName = "SwDialogApp",
                    openWindowOnLaunch = true,
                    hasWorker = true
                )
            )
            ServiceWorkerExtension.registerWorkerFactory { worker }
            ServiceWorkerExtension.installRuntimeOverrideForTests(runtime)
        },
        fragmentRegistrations = listOf(
            MainFrag::class to { _ -> MainFrag() },
            SingletonFrag::class to { _ -> SingletonFrag() },
            MultiWindowFrag::class to { _ -> MultiWindowFrag() },
            SingletonDialogFrag::class to { _ -> SingletonDialogFrag() }
        ),
        fragmentIds = mapOf(
            MainFrag::class to "com.test.MainFrag",
            SingletonFrag::class to "com.test.SingletonFrag",
            MultiWindowFrag::class to "com.test.MultiWindowFrag",
            SingletonDialogFrag::class to "com.test.SingletonDialogFrag"
        ),
        fragmentRoutes = mapOf(
            MainFrag::class to "/main",
            SingletonFrag::class to "/singleton",
            MultiWindowFrag::class to "/multi",
            SingletonDialogFrag::class to "/dialog"
        ),
        fragmentMetadata = mapOf(
            MainFrag::class to FragmentRegistry.getMetadata(MainFrag::class)!!,
            SingletonFrag::class to FragmentRegistry.getMetadata(SingletonFrag::class)!!,
            MultiWindowFrag::class to FragmentRegistry.getMetadata(MultiWindowFrag::class)!!,
            SingletonDialogFrag::class to FragmentRegistry.getMetadata(SingletonDialogFrag::class)!!
        )
    )

    @Test
    fun capabilityQueriesAndTypedExceptionsForUnsupportedBackendsAndValidation() = runDesktopBlockingTest {
        registerStandardMetadata()

        // 1. Completely unsupported runtime (iOS/iPadOS model)
        val iosRuntime = CapabilityMockRuntime(
            backendTarget = BackendTarget.IOS,
            isSupported = false,
            supportsMultipleWindows = false,
            supportsFragmentLaunch = false,
            supportsDialogFragments = false,
            supportsSystemTray = false,
            supportsOngoingNotification = false,
            supportsAutostart = false
        )
        ServiceWorkerExtension.installRuntimeOverrideForTests(iosRuntime)
        assertFalse(ServiceWorkerExtension.isSupported)
        assertFalse(ServiceWorkerExtension.supportsMultipleWindows)
        assertFalse(ServiceWorkerExtension.supportsFragmentLaunch)
        assertFalse(ServiceWorkerExtension.supportsDialogFragments)
        assertFalse(ServiceWorkerExtension.supportsSystemTray)
        assertFalse(ServiceWorkerExtension.supportsOngoingNotification)
        assertFalse(ServiceWorkerExtension.supportsAutostart)
        assertFalse(ServiceWorkerExtension.canOpenAdditionalWindow<MultiWindowFrag>())

        assertFailsWith<ServiceWorkerUnsupportedException> {
            ServiceWorkerExtension.ensureStarted()
        }
        assertFailsWith<ServiceWorkerAutostartUnsupportedException> {
            ServiceWorkerExtension.setAutostartEnabled(true)
        }
        assertFailsWith<ServiceWorkerUnsupportedException> {
            ServiceWorkerExtension.channel.send("ping")
        }
        assertFailsWith<ServiceWorkerUnsupportedException> {
            ServiceWorkerExtension.channel.request<String>("ping")
        }

        val iosWorker = TestWorker()
        assertFailsWith<ServiceWorkerFragmentLaunchUnsupportedException> {
            iosWorker.startFragment<SingletonFrag>()
        }
        assertFailsWith<SystemTrayUnsupportedException> {
            iosWorker.setTrayTooltip("No tray")
        }
        assertFailsWith<ServiceWorkerOngoingNotificationUnsupportedException> {
            iosWorker.setNotificationTitle("No notif")
        }

        // 2. Single-window mobile/web runtime with ongoing notification (Android model)
        val androidRuntime = CapabilityMockRuntime(
            backendTarget = BackendTarget.Android,
            isSupported = true,
            supportsMultipleWindows = false,
            supportsFragmentLaunch = true,
            supportsDialogFragments = true,
            supportsSystemTray = false,
            supportsOngoingNotification = true,
            supportsAutostart = true,
            autostartApplyResult = false
        )
        ServiceWorkerExtension.installRuntimeOverrideForTests(androidRuntime)
        assertFalse(ServiceWorkerExtension.canOpenAdditionalWindow<MultiWindowFrag>())
        val androidWorker = TestWorker()
        androidWorker.attachAndStart(androidRuntime, ServiceWorkerOptions())
        try {
            assertFailsWith<MultipleWindowsUnsupportedException> {
                androidWorker.startFragment<MultiWindowFrag>(openAdditionalWindow = true)
            }
            assertFailsWith<SystemTrayUnsupportedException> {
                androidWorker.setTrayMenu(ContextMenu())
            }
            assertFailsWith<ServiceWorkerAutostartUnsupportedException> {
                androidWorker.setAutostartEnabled(true)
            }
            androidWorker.setNotificationTitle("Active")
            assertEquals("Active", androidWorker.notificationTitle)
        } finally {
            androidWorker.performStop()
        }

        // 3. Desktop multi-window runtime with tray but no ongoing notifications
        val desktopRuntime = CapabilityMockRuntime(
            backendTarget = BackendTarget.Swing,
            isSupported = true,
            supportsMultipleWindows = true,
            supportsFragmentLaunch = true,
            supportsDialogFragments = true,
            supportsSystemTray = true,
            supportsOngoingNotification = false,
            supportsAutostart = true,
            connectShouldSucceed = false
        )
        ServiceWorkerExtension.installRuntimeOverrideForTests(desktopRuntime)
        assertTrue(ServiceWorkerExtension.canOpenAdditionalWindow<MultiWindowFrag>())
        assertFalse(ServiceWorkerExtension.canOpenAdditionalWindow<SingletonFrag>())
        assertFalse(ServiceWorkerExtension.canOpenAdditionalWindow<UnregisteredFrag>())

        assertFailsWith<ServiceWorkerIpcException> {
            ServiceWorkerExtension.channel.send("connect-failure")
        }

        val desktopWorker = TestWorker()
        desktopWorker.attachAndStart(desktopRuntime, ServiceWorkerOptions())
        try {
            assertFailsWith<FragmentNotRegisteredException> {
                desktopWorker.startFragment<UnregisteredFrag>()
            }
            assertFailsWith<FragmentMultipleWindowsUnsupportedException> {
                desktopWorker.startFragment<SingletonFrag>(openAdditionalWindow = true)
            }
            assertFailsWith<ServiceWorkerOngoingNotificationUnsupportedException> {
                desktopWorker.extendNotificationActions(ServiceWorkerNotificationAction("sync", "Sync"))
            }
            assertFailsWith<ServiceWorkerIpcException> {
                desktopWorker.sendToFragment<SingletonFrag>("ping", launchIfNeeded = false)
            }
            assertFailsWith<ServiceWorkerIpcException> {
                desktopWorker.startFragmentAndAwait<SingletonFrag>(timeoutMs = 50L)
            }
            desktopWorker.startFragment<MultiWindowFrag>(
                args = FragmentBundle.of("tab" to "first"),
                openAdditionalWindow = true
            )
            assertEquals(2, desktopRuntime.launchRequests.size)
            assertFalse(desktopRuntime.launchRequests[0].openAdditionalWindow)
            assertTrue(desktopRuntime.launchRequests[1].openAdditionalWindow)
        } finally {
            desktopWorker.performStop()
        }

        // 4. Dialog capability disabled
        val noDialogRuntime = CapabilityMockRuntime(
            backendTarget = BackendTarget.Web,
            isSupported = true,
            supportsMultipleWindows = false,
            supportsFragmentLaunch = true,
            supportsDialogFragments = false,
            supportsSystemTray = false,
            supportsOngoingNotification = false,
            supportsAutostart = false
        )
        ServiceWorkerExtension.installRuntimeOverrideForTests(noDialogRuntime)
        val webWorker = TestWorker()
        webWorker.attachAndStart(noDialogRuntime, ServiceWorkerOptions())
        try {
            assertFailsWith<UnsupportedBackendCapabilityException> {
                webWorker.startFragment<SingletonDialogFrag>()
            }
        } finally {
            webWorker.performStop()
        }
    }

    @Test
    fun startFragmentReusesSingletonOrOpensAdditionalWindowWhenAllowed() = runDesktopBlockingTest {
        registerStandardMetadata()
        val worker = TestWorker()
        val runtime = InMemoryCoordinatorRuntime(worker)
        val factory = FakeDesktopFactory()

        val app = GenericUiLaunchSequence.launch(
            factory = factory,
            args = emptyArray(),
            descriptor = createCoordinatorDescriptor(worker, runtime),
            isSystemTarget = true
        ) as Application

        try {
            assertTrue(ServiceWorkerExtension.channel.connect())
            val firstHandle = worker.startFragmentAndAwait<MultiWindowFrag>(
                args = FragmentBundle.of("tab" to "one"),
                openAdditionalWindow = false,
                timeoutMs = 5_000L
            )
            assertEquals("com.test.MultiWindowFrag", firstHandle.fragmentId)
            assertEquals(0, ServiceWorkerUiWindowCoordinator.activeSecondaryWindowCountForTests())
            assertEquals("one", app.mainWindow.fragmentContainer?.currentFragment?.arguments?.getString("tab"))

            // Launching again with openAdditionalWindow = false updates the open in-window fragment.
            worker.startFragment<MultiWindowFrag>(
                args = FragmentBundle.of("tab" to "two"),
                openAdditionalWindow = false
            )
            waitUntil { app.mainWindow.fragmentContainer?.currentFragment?.arguments?.getString("tab") == "two" }
            assertEquals(0, ServiceWorkerUiWindowCoordinator.activeSecondaryWindowCountForTests())

            // Launching with openAdditionalWindow = true spawns secondary window #1.
            worker.startFragment<MultiWindowFrag>(
                args = FragmentBundle.of("tab" to "three"),
                openAdditionalWindow = true
            )
            waitUntil { ServiceWorkerUiWindowCoordinator.activeSecondaryWindowCountForTests() == 1 }
            val firstWin = ServiceWorkerUiWindowCoordinator.activeSecondaryWindowsForTests().single()
            val firstNativeWin = firstWin.nativeImpl as FakeWindowNativeImpl
            assertEquals("three", firstWin.fragmentContainer?.currentFragment?.arguments?.getString("tab"))

            // Launching again with openAdditionalWindow = false reuses the existing secondary Window and updates args.
            worker.startFragment<MultiWindowFrag>(
                args = FragmentBundle.of("tab" to "four"),
                openAdditionalWindow = false
            )
            waitUntil { firstWin.fragmentContainer?.currentFragment?.arguments?.getString("tab") == "four" }
            assertEquals(1, ServiceWorkerUiWindowCoordinator.activeSecondaryWindowCountForTests())
            assertTrue(firstNativeWin.bringToFrontCalls >= 2)

            // Launching with openAdditionalWindow = true spawns a distinct second Window.
            worker.startFragment<MultiWindowFrag>(
                args = FragmentBundle.of("tab" to "five"),
                openAdditionalWindow = true
            )
            waitUntil { ServiceWorkerUiWindowCoordinator.activeSecondaryWindowCountForTests() == 2 }
            val windows = ServiceWorkerUiWindowCoordinator.activeSecondaryWindowsForTests()
            assertEquals(2, windows.size)
            assertNotSame(windows[0], windows[1])
            assertEquals("five", windows[1].fragmentContainer?.currentFragment?.arguments?.getString("tab"))
            assertFalse(app.mainWindow.isDisposed)
        } finally {
            worker.performStop()
        }
    }

    @Test
    fun coordinatorMountsAttachedDialogFragmentAboveMainWindowAndDelaysExitUntilDialogCloses() = runDesktopBlockingTest {
        registerStandardMetadata()
        val worker = TestWorker()
        val runtime = InMemoryCoordinatorRuntime(worker)
        val factory = FakeDesktopFactory()

        val app = GenericUiLaunchSequence.launch(
            factory = factory,
            args = emptyArray(),
            descriptor = createCoordinatorDescriptor(worker, runtime),
            isSystemTarget = true
        ) as Application

        val fakeApp = factory.apps.single()
        assertFalse(app.suppressInitialMainWindowShow)

        try {
            assertTrue(ServiceWorkerExtension.channel.connect())
            val handle = worker.startFragmentAndAwait<SingletonDialogFrag>(
                args = FragmentBundle.of("step" to 1),
                timeoutMs = 5_000L
            )
            assertEquals("com.test.SingletonDialogFrag", handle.fragmentId)
            assertEquals(1, ServiceWorkerUiWindowCoordinator.activeDialogWindowCountForTests())
            val dialogWin: DialogWindow = ServiceWorkerUiWindowCoordinator.activeDialogWindowsForTests().single()
            val dialogFrag = dialogWin.fragmentContainer?.currentFragment as? SingletonDialogFrag
            assertNotNull(dialogFrag)
            assertSame(app.mainWindow, dialogWin.ownerWindow)
            val dialogNative = dialogWin.nativeImpl as FakeDialogWindowNativeImpl
            assertSame(app.mainWindow.nativeImpl, dialogNative.lastOwnerWindow)
            assertEquals(1, dialogFrag.arguments?.getInt("step"))
            assertEquals(FragmentState.RESUMED, dialogFrag.state)
            assertEquals(1, dialogFrag.startedCount)
            assertEquals(1, dialogFrag.resumedCount)

            // Relaunching singleton dialog with openAdditionalWindow = false focuses & updates in place.
            worker.startFragment<SingletonDialogFrag>(
                args = FragmentBundle.of("step" to 2),
                openAdditionalWindow = false
            )
            waitUntil { dialogFrag.arguments?.getInt("step") == 2 }
            assertEquals(1, ServiceWorkerUiWindowCoordinator.activeDialogWindowCountForTests())
            assertTrue(dialogNative.showDialogCalls >= 2)

            // Closing main window while dialog stays open must NOT exit the UI process.
            worker.closeFragment<MainFrag>()
            waitUntil { app.mainWindow.fragmentContainer?.currentFragment == null }
            assertEquals(0, fakeApp.exitCalls, "Process 2 must remain alive while dialog window stays open.")
            assertEquals(1, ServiceWorkerUiWindowCoordinator.activeDialogWindowCountForTests())

            // Closing final open dialog triggers fragment stop/destroy and clean app.exit(0).
            worker.closeFragment<SingletonDialogFrag>()
            waitUntil { fakeApp.exitCalls == 1 }
            assertEquals(0, ServiceWorkerUiWindowCoordinator.activeDialogWindowCountForTests())
            assertEquals(0, fakeApp.lastExitCode)
            assertTrue(dialogFrag.pausedCount >= 1)
            assertTrue(dialogFrag.stoppedCount >= 1)
            assertTrue(dialogFrag.destroyedCount >= 1)
        } finally {
            worker.performStop()
        }
    }

    @Test
    fun coordinatorMountsStandaloneDialogWindowWhenMainSuppressedAndReattachesWhenMainOpens() = runDesktopBlockingTest {
        registerStandardMetadata()
        val worker = TestWorker()
        val runtime = InMemoryCoordinatorRuntime(worker)
        val factory = FakeDesktopFactory()

        val initialArgs = FragmentBundle.of("standalone" to true).toBase64Url()
        val app = GenericUiLaunchSequence.launch(
            factory = factory,
            args = arrayOf("--fragment=SingletonDialogFrag", "--fragment-args-b64=$initialArgs"),
            descriptor = createCoordinatorDescriptor(worker, runtime),
            isSystemTarget = true
        ) as Application

        val fakeApp = factory.apps.single()
        assertTrue(app.suppressInitialMainWindowShow)
        assertNull(app.mainWindow.fragmentContainer?.currentFragment)
        assertEquals(1, ServiceWorkerUiWindowCoordinator.activeDialogWindowCountForTests())
        val dialogWin = ServiceWorkerUiWindowCoordinator.activeDialogWindowsForTests().single()
        val dialogNative = dialogWin.nativeImpl as FakeDialogWindowNativeImpl
        assertNull(dialogWin.ownerWindow, "Dialog launched with no active main window must be standalone.")
        assertNull(dialogNative.lastOwnerWindow)

        try {
            assertTrue(ServiceWorkerExtension.channel.connect())

            // Opening MainFrag while standalone dialog is active attaches dialog overtop main window.
            worker.startFragmentAndAwait<MainFrag>(timeoutMs = 5_000L)
            waitUntil { app.mainWindow.fragmentContainer?.currentFragment is MainFrag }
            waitUntil { dialogWin.ownerWindow === app.mainWindow }
            assertSame(app.mainWindow.nativeImpl, dialogNative.lastOwnerWindow)
            assertEquals(0, fakeApp.exitCalls)

            // Simulating OS dialog close clears dialog tracking cleanly while keeping main window open.
            dialogNative.simulateUserClosed()
            waitUntil { ServiceWorkerUiWindowCoordinator.activeDialogWindowCountForTests() == 0 }
            assertEquals(0, fakeApp.exitCalls)

            // Closing the remaining main window exits Process 2 cleanly.
            worker.closeFragment<MainFrag>()
            waitUntil { fakeApp.exitCalls == 1 }
            assertEquals(0, fakeApp.lastExitCode)
        } finally {
            worker.performStop()
        }
    }
}
