@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.serviceworker

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
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.enums.window.DisplayCutoutMode
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceWorkerUiCoordinatorLifecycleTest {

    private data class MainMsg(val text: String)
    private data class SettingsMsg(val key: String)
    private data class FragPingReq(val tag: String)
    private data class FragPingResp(val fromFragment: String, val tag: String)
    private data class UiToWorkerNote(val origin: String, val note: String)
    private data class UiToWorkerQuery(val value: Int)
    private data class UiToWorkerAnswer(val origin: String, val doubled: Int)

    private class MainTestFragment : Fragment() {
        val lock = AtomicBoolean(false)
        val receivedMainMsgs = mutableListOf<MainMsg>()
        val receivedPingReqs = mutableListOf<FragPingReq>()
        var binding: ServiceWorkerFragmentBinding? = null
            private set

        init {
            pageTitle = "Main View"
        }

        override fun onStart() {
            if (binding == null) {
                binding = ServiceWorkerFragmentBinding(this).apply {
                    onMessage<MainMsg> { msg ->
                        while (!lock.compareAndSet(expectedValue = false, newValue = true)) {}
                        try {
                            receivedMainMsgs.add(msg)
                        } finally {
                            lock.store(false)
                        }
                    }
                    onRequest<FragPingReq, FragPingResp> { req ->
                        while (!lock.compareAndSet(expectedValue = false, newValue = true)) {}
                        try {
                            receivedPingReqs.add(req)
                        } finally {
                            lock.store(false)
                        }
                        FragPingResp("MainTestFragment", req.tag)
                    }
                    start()
                }
            }
        }
    }

    private class SettingsTestFragment : Fragment() {
        val lock = AtomicBoolean(false)
        val receivedSettingsMsgs = mutableListOf<SettingsMsg>()
        val receivedPingReqs = mutableListOf<FragPingReq>()
        var binding: ServiceWorkerFragmentBinding? = null
            private set

        override val prefersUndecoratedWindow: Boolean = true

        init {
            pageTitle = "Settings Panel"
        }

        override fun onStart() {
            if (binding == null) {
                binding = ServiceWorkerFragmentBinding(this).apply {
                    onMessage<SettingsMsg> { msg ->
                        while (!lock.compareAndSet(expectedValue = false, newValue = true)) {}
                        try {
                            receivedSettingsMsgs.add(msg)
                        } finally {
                            lock.store(false)
                        }
                    }
                    onRequest<FragPingReq, FragPingResp> { req ->
                        while (!lock.compareAndSet(expectedValue = false, newValue = true)) {}
                        try {
                            receivedPingReqs.add(req)
                        } finally {
                            lock.store(false)
                        }
                        FragPingResp("SettingsTestFragment", req.tag)
                    }
                    start()
                }
            }
        }
    }

    private class CoordinatorWorker : ServiceWorker() {
        private val lock = AtomicBoolean(false)
        val receivedNotes = mutableListOf<Pair<String, UiToWorkerNote>>()
        val disconnectedFragmentIds = mutableListOf<String>()

        override fun onStart() {
            onFragmentMessage<MainTestFragment, UiToWorkerNote> { handle, msg ->
                withLock { receivedNotes.add("main:${handle.fragmentId}" to msg) }
            }
            onFragmentMessage<SettingsTestFragment, UiToWorkerNote> { handle, msg ->
                withLock { receivedNotes.add("settings:${handle.fragmentId}" to msg) }
            }
            onFragmentRequest<MainTestFragment, UiToWorkerQuery, UiToWorkerAnswer> { handle, req ->
                UiToWorkerAnswer("worker->main:${handle.fragmentId}", req.value * 2)
            }
            onFragmentRequest<SettingsTestFragment, UiToWorkerQuery, UiToWorkerAnswer> { handle, req ->
                UiToWorkerAnswer("worker->settings:${handle.fragmentId}", req.value * 2)
            }
        }

        override fun onFragmentDisconnected(handle: ServiceWorkerFragmentHandle) {
            withLock { disconnectedFragmentIds.add(handle.fragmentId) }
        }

        fun snapshotNotes(): List<Pair<String, UiToWorkerNote>> = withLock { receivedNotes.toList() }
        fun snapshotDisconnectedIds(): List<String> = withLock { disconnectedFragmentIds.toList() }

        private inline fun <T> withLock(block: () -> T): T {
            while (!lock.compareAndSet(expectedValue = false, newValue = true)) {}
            return try {
                block()
            } finally {
                lock.store(false)
            }
        }
    }

    private class InMemoryLoopbackRuntime(
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

        override fun configure(options: ServiceWorkerOptions) {}

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
        ) {}

        override fun updateTray(
            iconPathOrUrl: String?,
            iconResource: ResourceID?,
            tooltip: String?,
            badgeText: String?,
            menu: ContextMenu?,
            onClick: (() -> Unit)?,
            macosIconPathOrUrl: String?
        ) {}

        override fun openMainWindow(route: String?) {
            val env = ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                correlationId = 0L,
                clientId = "ui-main",
                typeId = ServiceWorkerEnvelope.CONTROL_OPEN_MAIN_WINDOW,
                payloadJson = route?.let { "\"$it\"" } ?: "null"
            )
            incomingToUi?.invoke(env)
        }

        override fun closeMainWindow() {
            incomingToUi?.invoke(
                ServiceWorkerEnvelope(
                    kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                    correlationId = 0L,
                    clientId = "ui-main",
                    typeId = ServiceWorkerEnvelope.CONTROL_CLOSE_MAIN_WINDOW,
                    payloadJson = "null"
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
                    payloadJson = request.toControlPayloadJson(),
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
                    payloadJson = ServiceWorkerFragmentLaunchRequest.encodeCloseControlPayload(fragmentId, instanceId),
                    targetFragmentId = fragmentId
                )
            )
        }

        override fun stopSelf() {}
        override fun exitApplication(exitCode: Int) {}
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

        override fun disconnectClient(clientId: String) {}
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

    private class FakeApplicationNativeImpl : ApplicationNativeImpl<Any> {
        override val nativeApplication: Any = "fake-app"
        override var isDisposed: Boolean = false
        private var mode: ThemeMode = ThemeMode.AUTO
        var runEventLoopCalls: Int = 0
        var exitCalls: Int = 0
        var lastExitCode: Int? = null

        override fun dispose() { isDisposed = true }
        override fun setThemeMode(mode: ThemeMode) { this.mode = mode }
        override fun getThemeMode(): ThemeMode = mode
        override fun runEventLoop(onStarted: () -> Unit) {
            runEventLoopCalls++
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

    private class FakeDialogWindowNativeImpl : StubNativeImpl("dialog"), DialogWindowNativeImpl<Any> {
        var showCalls: Int = 0
        var closeCalls: Int = 0
        private var title: String = ""
        private var style: DialogPresentationStyle = DialogPresentationStyle.MODAL_CENTERED
        private var onClosed: (() -> Unit)? = null
        override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {}
        override fun removeChildNative(childImpl: NativeImpl<*>) {}
        override fun clearChildrenNative() {}
        override fun setTitle(title: String) { this.title = title }
        override fun getTitle(): String = title
        override fun setPresentationStyle(style: DialogPresentationStyle) { this.style = style }
        override fun getPresentationStyle(): DialogPresentationStyle = style
        override fun setModal(modal: Boolean) {}
        override fun setFullscreen(fullscreen: Boolean) {}
        override fun showDialog(ownerWindow: WindowNativeImpl<*>?) { showCalls++ }
        override fun closeDialog() {
            closeCalls++
            onClosed?.invoke()
        }
        override fun setOnDialogClosed(listener: (() -> Unit)?) { onClosed = listener }
    }

    private class FakeDesktopFactory : NativeFactory {
        val windows = mutableListOf<FakeWindowNativeImpl>()
        val dialogs = mutableListOf<FakeDialogWindowNativeImpl>()
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
        override fun createDialogWindow(): DialogWindowNativeImpl<*> = FakeDialogWindowNativeImpl().also { dialogs.add(it) }
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

    private val codecIds = listOf(
        "coord.MainMsg",
        "coord.SettingsMsg",
        "coord.FragPingReq",
        "coord.FragPingResp",
        "coord.UiToWorkerNote",
        "coord.UiToWorkerQuery",
        "coord.UiToWorkerAnswer"
    )

    private var savedActiveFactory: NativeFactory? = null

    private fun registerCodecs() {
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<MainMsg> {
            override val typeId = "coord.MainMsg"
            override val messageClass = MainMsg::class
            override fun encode(value: MainMsg) = PreferenceElement.ofObject(
                mapOf("text" to PreferenceElement.ofString(value.text))
            )
            override fun decode(element: PreferenceElement) = MainMsg(
                element.mapEntries["text"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<SettingsMsg> {
            override val typeId = "coord.SettingsMsg"
            override val messageClass = SettingsMsg::class
            override fun encode(value: SettingsMsg) = PreferenceElement.ofObject(
                mapOf("key" to PreferenceElement.ofString(value.key))
            )
            override fun decode(element: PreferenceElement) = SettingsMsg(
                element.mapEntries["key"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<FragPingReq> {
            override val typeId = "coord.FragPingReq"
            override val messageClass = FragPingReq::class
            override fun encode(value: FragPingReq) = PreferenceElement.ofObject(
                mapOf("tag" to PreferenceElement.ofString(value.tag))
            )
            override fun decode(element: PreferenceElement) = FragPingReq(
                element.mapEntries["tag"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<FragPingResp> {
            override val typeId = "coord.FragPingResp"
            override val messageClass = FragPingResp::class
            override fun encode(value: FragPingResp) = PreferenceElement.ofObject(
                mapOf(
                    "fromFragment" to PreferenceElement.ofString(value.fromFragment),
                    "tag" to PreferenceElement.ofString(value.tag)
                )
            )
            override fun decode(element: PreferenceElement) = FragPingResp(
                fromFragment = element.mapEntries["fromFragment"]?.asString().orEmpty(),
                tag = element.mapEntries["tag"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<UiToWorkerNote> {
            override val typeId = "coord.UiToWorkerNote"
            override val messageClass = UiToWorkerNote::class
            override fun encode(value: UiToWorkerNote) = PreferenceElement.ofObject(
                mapOf(
                    "origin" to PreferenceElement.ofString(value.origin),
                    "note" to PreferenceElement.ofString(value.note)
                )
            )
            override fun decode(element: PreferenceElement) = UiToWorkerNote(
                origin = element.mapEntries["origin"]?.asString().orEmpty(),
                note = element.mapEntries["note"]?.asString().orEmpty()
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<UiToWorkerQuery> {
            override val typeId = "coord.UiToWorkerQuery"
            override val messageClass = UiToWorkerQuery::class
            override fun encode(value: UiToWorkerQuery) = PreferenceElement.ofObject(
                mapOf("value" to PreferenceElement.ofInt(value.value))
            )
            override fun decode(element: PreferenceElement) = UiToWorkerQuery(
                value = element.mapEntries["value"]?.asInt() ?: 0
            )
        })
        ServiceWorkerCodecRegistry.register(object : ServiceWorkerMessageCodec<UiToWorkerAnswer> {
            override val typeId = "coord.UiToWorkerAnswer"
            override val messageClass = UiToWorkerAnswer::class
            override fun encode(value: UiToWorkerAnswer) = PreferenceElement.ofObject(
                mapOf(
                    "origin" to PreferenceElement.ofString(value.origin),
                    "doubled" to PreferenceElement.ofInt(value.doubled)
                )
            )
            override fun decode(element: PreferenceElement) = UiToWorkerAnswer(
                origin = element.mapEntries["origin"]?.asString().orEmpty(),
                doubled = element.mapEntries["doubled"]?.asInt() ?: 0
            )
        })
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

    @BeforeTest
    fun setUp() {
        savedActiveFactory = GenericUI.activeFactory
        ServiceWorkerExtension.resetForTests()
        FragmentRegistry.clear()
        GenericUiExtensionRegistry.clear()
        Application.current = null
        registerCodecs()
    }

    @AfterTest
    fun tearDown() {
        ServiceWorkerExtension.resetForTests()
        codecIds.forEach { ServiceWorkerCodecRegistry.unregister(it) }
        FragmentRegistry.clear()
        GenericUiExtensionRegistry.clear()
        GenericUI.activeFactory = savedActiveFactory
        Application.current = null
    }

    @Test
    fun secondaryWindowLaunchLiveMainLaunchTargetedRoutingAndCleanShutdown() = runDesktopBlockingTest {
        val worker = CoordinatorWorker()
        val bridgeRuntime = InMemoryLoopbackRuntime(worker)
        val factory = FakeDesktopFactory()

        val initialArgs = FragmentBundle.of("section" to "appearance").toBase64Url()
        val descriptor = GenericUiAppDescriptor(
            appId = "com.test.coordapp",
            appName = "CoordApp",
            mainFragmentClass = MainTestFragment::class,
            mainFragmentFactory = { MainTestFragment() },
            providerInstallers = listOf {
                ServiceWorkerExtension.install(
                    ServiceWorkerOptions(
                        appId = "com.test.coordapp",
                        appName = "CoordApp",
                        hasWorker = true
                    )
                )
                ServiceWorkerExtension.installRuntimeOverrideForTests(bridgeRuntime)
            },
            fragmentRegistrations = listOf(
                MainTestFragment::class to { _ -> MainTestFragment() },
                SettingsTestFragment::class to { _ -> SettingsTestFragment() }
            ),
            fragmentIds = mapOf(
                MainTestFragment::class to "com.test.MainTestFragment",
                SettingsTestFragment::class to "com.test.SettingsTestFragment"
            ),
            fragmentRoutes = mapOf(
                MainTestFragment::class to "/main",
                SettingsTestFragment::class to "/settings"
            )
        )

        val app = GenericUiLaunchSequence.launch(
            factory = factory,
            args = arrayOf("--fragment=SettingsTestFragment", "--fragment-args-b64=$initialArgs"),
            descriptor = descriptor,
            isSystemTarget = true
        ) as Application

        // Step 1: Secondary CLI launch suppressed mainWindow and tailored secondary Window.
        assertTrue(app.suppressInitialMainWindowShow)
        assertNull(app.mainWindow.fragmentContainer?.currentFragment)
        assertEquals(1, ServiceWorkerUiWindowCoordinator.activeSecondaryWindowCountForTests())
        assertEquals(1, factory.windows.size)
        val settingsWin = ServiceWorkerUiWindowCoordinator.activeSecondaryWindowsForTests().single()
        val settingsNativeWin = settingsWin.nativeImpl as FakeWindowNativeImpl
        assertTrue(settingsNativeWin.isUndecorated())
        assertEquals("Settings Panel", settingsNativeWin.getTitle())
        assertEquals(1, settingsNativeWin.showWindowCalls)
        val settingsFrag = settingsWin.fragmentContainer?.currentFragment as? SettingsTestFragment
        assertNotNull(settingsFrag)
        assertEquals("appearance", settingsFrag.arguments?.getString("section"))

        waitUntil { worker.isFragmentActive<SettingsTestFragment>() }
        assertFalse(worker.isFragmentActive<MainTestFragment>())

        // Step 2: Worker live-launches MainTestFragment while SettingsTestFragment remains open.
        val mainArgs = FragmentBundle.of("project" to "alpha")
        val mainHandle = launchMainAndWait(worker, mainArgs)
        assertEquals("com.test.MainTestFragment", mainHandle.fragmentId)
        waitUntil { app.mainWindow.fragmentContainer?.currentFragment is MainTestFragment }
        val mainFrag = app.mainWindow.fragmentContainer?.currentFragment as MainTestFragment
        assertEquals("alpha", mainFrag.arguments?.getString("project"))
        val mainNativeWin = app.mainWindow.nativeImpl as FakeWindowNativeImpl
        assertEquals(1, mainNativeWin.showWindowCalls)
        assertTrue(worker.isFragmentActive<MainTestFragment>())
        assertTrue(worker.isFragmentActive<SettingsTestFragment>())

        // Step 3: Targeted bidirectional routing isolation across both fragments on one channel.
        worker.sendToFragment<SettingsTestFragment>(SettingsMsg("theme=dark"))
        worker.sendToFragment<MainTestFragment>(MainMsg("refresh-feed"))
        waitUntil {
            settingsFrag.receivedSettingsMsgs.size == 1 && mainFrag.receivedMainMsgs.size == 1
        }
        assertEquals(listOf(SettingsMsg("theme=dark")), settingsFrag.receivedSettingsMsgs)
        assertEquals(listOf(MainMsg("refresh-feed")), mainFrag.receivedMainMsgs)

        val settingsResp = worker.requestFromFragment<SettingsTestFragment, FragPingResp>(
            FragPingReq("s-ping")
        )
        val mainResp = worker.requestFromFragment<MainTestFragment, FragPingResp>(
            FragPingReq("m-ping")
        )
        assertEquals(FragPingResp("SettingsTestFragment", "s-ping"), settingsResp)
        assertEquals(FragPingResp("MainTestFragment", "m-ping"), mainResp)
        assertEquals(listOf(FragPingReq("s-ping")), settingsFrag.receivedPingReqs)
        assertEquals(listOf(FragPingReq("m-ping")), mainFrag.receivedPingReqs)

        val settingsBinding = settingsFrag.binding!!
        val mainBinding = mainFrag.binding!!
        settingsBinding.send(UiToWorkerNote("settings", "saved"))
        mainBinding.send(UiToWorkerNote("main", "scrolled"))
        waitUntil { worker.snapshotNotes().size == 2 }
        assertTrue(worker.snapshotNotes().contains("settings:com.test.SettingsTestFragment" to UiToWorkerNote("settings", "saved")))
        assertTrue(worker.snapshotNotes().contains("main:com.test.MainTestFragment" to UiToWorkerNote("main", "scrolled")))

        val settingsWorkerAns = settingsBinding.request<UiToWorkerAnswer>(UiToWorkerQuery(21))
        val mainWorkerAns = mainBinding.request<UiToWorkerAnswer>(UiToWorkerQuery(9))
        assertEquals(UiToWorkerAnswer("worker->settings:com.test.SettingsTestFragment", 42), settingsWorkerAns)
        assertEquals(UiToWorkerAnswer("worker->main:com.test.MainTestFragment", 18), mainWorkerAns)

        // Step 4: Closing MainTestFragment while SettingsTestFragment stays open hides mainWindow without exiting.
        val fakeApp = factory.apps.single()
        worker.closeFragment<MainTestFragment>()
        waitUntil { !worker.isFragmentActive<MainTestFragment>() && app.mainWindow.fragmentContainer?.currentFragment == null }
        assertFalse(mainNativeWin.visibleFlag, "mainWindow must be hidden after close when secondary remains.")
        assertEquals(0, fakeApp.exitCalls, "Application must NOT exit while SettingsTestFragment is open.")
        assertEquals(1, ServiceWorkerUiWindowCoordinator.activeSecondaryWindowCountForTests())
        assertTrue(worker.isFragmentActive<SettingsTestFragment>())

        // Re-launching MainTestFragment re-shows mainWindow and mounts MainTestFragment cleanly.
        launchMainAndWait(worker, FragmentBundle.of("reopened" to true))
        waitUntil { app.mainWindow.fragmentContainer?.currentFragment is MainTestFragment && worker.isFragmentActive<MainTestFragment>() }
        assertTrue(mainNativeWin.visibleFlag)
        assertEquals(2, mainNativeWin.showWindowCalls)

        // Step 5: Closing SettingsTestFragment closes its secondary Window while MainTestFragment remains open.
        worker.closeFragment<SettingsTestFragment>()
        waitUntil {
            !worker.isFragmentActive<SettingsTestFragment>() &&
                ServiceWorkerUiWindowCoordinator.activeSecondaryWindowCountForTests() == 0
        }
        assertEquals(1, settingsNativeWin.closeWindowCalls)
        assertEquals(0, fakeApp.exitCalls, "Application must remain alive while MainTestFragment is still open.")
        assertTrue(worker.isFragmentActive<MainTestFragment>())

        // Step 6: Closing the final active window triggers clean app.exit(0).
        worker.closeFragment<MainTestFragment>()
        waitUntil { fakeApp.exitCalls == 1 }
        assertEquals(0, fakeApp.lastExitCode)
        worker.performStop()
    }

    private suspend fun launchMainAndWait(
        worker: CoordinatorWorker,
        args: FragmentBundle?
    ): ServiceWorkerFragmentHandle {
        val job = worker.startFragmentAndAwait<MainTestFragment>(
            args = args,
            timeoutMs = 5_000L
        )
        return job
    }

    @Test
    fun dialogOnlyLaunchStaysAliveWithoutMainFragmentAndExitsWhenDialogCloses() = runDesktopBlockingTest {
        val worker = CoordinatorWorker()
        val bridgeRuntime = InMemoryLoopbackRuntime(worker)
        val factory = FakeDesktopFactory()

        val descriptor = GenericUiAppDescriptor(
            appId = "com.test.dialogonly",
            appName = "DialogOnlyApp",
            mainFragmentClass = MainTestFragment::class,
            mainFragmentFactory = { MainTestFragment() },
            providerInstallers = listOf {
                ServiceWorkerExtension.install(
                    ServiceWorkerOptions(
                        appId = "com.test.dialogonly",
                        appName = "DialogOnlyApp",
                        hasWorker = true
                    )
                )
                ServiceWorkerExtension.installRuntimeOverrideForTests(bridgeRuntime)
            },
            fragmentRegistrations = listOf(
                MainTestFragment::class to { _ -> MainTestFragment() },
                SettingsTestFragment::class to { _ -> SettingsTestFragment() }
            ),
            fragmentIds = mapOf(
                MainTestFragment::class to "com.test.MainTestFragment",
                SettingsTestFragment::class to "com.test.SettingsTestFragment"
            ),
            fragmentRoutes = mapOf(
                MainTestFragment::class to "/main",
                SettingsTestFragment::class to "/settings"
            ),
            fragmentMetadata = mapOf(
                SettingsTestFragment::class to com.nexustools.genericui.component.fragment.FragmentMetadata(
                    fragmentId = "com.test.SettingsTestFragment",
                    route = "/settings",
                    dialog = true
                )
            )
        )

        val app = GenericUiLaunchSequence.launch(
            factory = factory,
            args = arrayOf("--fragment=SettingsTestFragment"),
            descriptor = descriptor,
            isSystemTarget = true
        ) as Application

        assertTrue(app.suppressInitialMainWindowShow)
        assertEquals(1, ServiceWorkerUiWindowCoordinator.activeDialogWindowCountForTests())
        val fakeApp = factory.apps.single()
        assertEquals(0, fakeApp.exitCalls, "Dialog-only launch must stay alive while dialog is open")

        val dialogWindow = ServiceWorkerUiWindowCoordinator.activeDialogWindowsForTests().single()
        val dialogFrag = dialogWindow.fragmentContainer?.currentFragment as? SettingsTestFragment
        assertNotNull(dialogFrag)
        assertTrue(dialogFrag.finishFragment())

        waitUntil { fakeApp.exitCalls == 1 }
        assertEquals(0, fakeApp.lastExitCode)
        assertEquals(0, ServiceWorkerUiWindowCoordinator.activeDialogWindowCountForTests())
        worker.performStop()
    }
}
