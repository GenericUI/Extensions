package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.fragment.FragmentState
import com.nexustools.genericui.enums.window.WindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * UI-process coordinator managing secondary fragment [Window]s, attached and standalone
 * `@AppFragment(dialog = true)` [DialogWindow]s, initial secondary CLI launches
 * (`app.suppressInitialMainWindowShow == true`), and live `__genericui.sw.control.launchFragment` /
 * `__genericui.sw.control.closeFragment` control frames arriving over [ServiceWorkerExtension.channel].
 */
internal object ServiceWorkerUiWindowCoordinator {

    private class TrackedSecondaryWindow(
        val fragmentId: String,
        var instanceId: String,
        val route: String?,
        val fragmentClass: KClass<out Fragment>,
        val window: Window,
        val container: FragmentContainer,
        val fragment: Fragment,
        var closed: Boolean = false,
        var handlingClose: Boolean = false,
        var coordinatorManagedMount: Boolean = false,
        var lastMountedEpoch: Long = -1L,
        var everSentMounted: Boolean = false
    )

    private class TrackedDialogEntry(
        val fragmentId: String,
        var instanceId: String,
        val route: String?,
        val fragmentClass: KClass<out Fragment>,
        val dialogWindow: DialogWindow,
        val container: FragmentContainer,
        val fragment: Fragment,
        var closed: Boolean = false,
        var handlingClose: Boolean = false,
        var coordinatorManagedMount: Boolean = false,
        var lastMountedEpoch: Long = -1L,
        var everSentMounted: Boolean = false
    )

    private class TrackedMainFragmentMount(
        val fragment: Fragment,
        val fragmentId: String,
        val instanceId: String,
        val route: String?,
        var lastMountedEpoch: Long = -1L,
        var everSentMounted: Boolean = false
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeApplication: Application? = null
    private var activeFactory: NativeFactory? = null
    private var eventLoopStarted: Boolean = false
    private var mainWindowHiddenByClose: Boolean = false
    private var holdingExitWhenNoFragmentsOpen: Boolean = false
    private val secondaryWindows = mutableListOf<TrackedSecondaryWindow>()
    private val dialogWindows = mutableListOf<TrackedDialogEntry>()
    private val pendingLaunchRequests = mutableListOf<ServiceWorkerFragmentLaunchRequest>()
    private var trackedMainMount: TrackedMainFragmentMount? = null
    private var connectionSyncHandle: Disposable? = null
    private var reconnectSyncHandle: Disposable? = null

    fun onAppPrepared(app: Application) {
        activeApplication = app
        eventLoopStarted = false
        mainWindowHiddenByClose = false
        holdingExitWhenNoFragmentsOpen = false
        ServiceWorkerFragmentBinding.setAppStartupMountHold(true)
        ensureChannelMountSyncInstalled()
    }

    fun onAppEventLoopStarted(app: Application, factory: NativeFactory) {
        activeApplication = app
        activeFactory = factory
        eventLoopStarted = true
        ensureChannelMountSyncInstalled()

        try {
            if (app.suppressInitialMainWindowShow) {
                val launchArgs = app.launchArguments
                val requestedId = launchArgs?.getString(FragmentBundle.KEY_LAUNCH_FRAGMENT_ID)?.trim()?.takeIf { it.isNotEmpty() }
                val requestedRoute = launchArgs?.getString(FragmentBundle.KEY_LAUNCH_ROUTE)?.trim()?.takeIf { it.isNotEmpty() }
                val openAdditional = launchArgs?.getBoolean(FragmentBundle.KEY_LAUNCH_OPEN_ADDITIONAL_WINDOW, false) ?: false

                val resolvedClass = requestedId?.let { FragmentRegistry.resolveFragmentClass(it) }
                    ?: requestedRoute?.let { FragmentRegistry.resolveFragmentClass(it) }
                if (resolvedClass != null) {
                    val canonicalId = FragmentRegistry.resolveFragmentId(resolvedClass)
                    val resolvedRoute = requestedRoute ?: FragmentRegistry.resolveRoute(resolvedClass)
                    val request = ServiceWorkerFragmentLaunchRequest(
                        fragmentId = requestedId ?: canonicalId,
                        route = resolvedRoute,
                        arguments = launchArgs,
                        openAdditionalWindow = openAdditional,
                        clearTask = false,
                        bringToFront = true
                    )
                    if (FragmentRegistry.isDialogFragment(resolvedClass)) {
                        openOrUpdateDialogWindow(
                            app = app,
                            targetClass = resolvedClass,
                            canonicalId = canonicalId,
                            request = request
                        )
                    } else {
                        openOrUpdateSecondaryWindow(
                            app = app,
                            targetClass = resolvedClass,
                            canonicalId = canonicalId,
                            request = request
                        )
                    }
                }
            } else {
                val currentMainFrag = app.rootFragmentContainer?.currentFragment ?: app.mainWindow.fragmentContainer?.currentFragment
                if (currentMainFrag != null) {
                    syncMainFragmentMountIfUnbound(currentMainFrag)
                }
            }

            if (pendingLaunchRequests.isNotEmpty()) {
                val queued = pendingLaunchRequests.toList()
                pendingLaunchRequests.clear()
                for (req in queued) {
                    handleLaunchFragment(req)
                }
            }
        } finally {
            ServiceWorkerFragmentBinding.setAppStartupMountHold(false)
        }
    }

    fun onAppShutdown(app: IApplication?) {
        if (app != null && activeApplication != null && activeApplication !== app) return
        val secondaryCopy = secondaryWindows.toList()
        secondaryWindows.clear()
        for (entry in secondaryCopy) {
            if (!entry.closed) {
                entry.closed = true
                disposeSecondaryEntryResources(entry, closeWindow = false)
            }
        }
        val dialogCopy = dialogWindows.toList()
        dialogWindows.clear()
        for (entry in dialogCopy) {
            if (!entry.closed) {
                entry.closed = true
                disposeDialogEntryResources(entry, closeWindow = false)
            }
        }
        trackedMainMount?.let { mount ->
            sendCoordinatorUnmountedIfNeeded(mount.fragmentId, mount.instanceId, mount.route, mount.everSentMounted)
            ServiceWorkerFragmentBinding.disposeBindingsForFragment(mount.fragment)
        }
        trackedMainMount = null
        (app as? Application)?.let { activeApp ->
            (activeApp.rootFragmentContainer?.currentFragment ?: activeApp.mainWindow.fragmentContainer?.currentFragment)
        }?.let { mainFrag ->
            ServiceWorkerFragmentBinding.disposeBindingsForFragment(mainFrag)
        }
        pendingLaunchRequests.clear()
        connectionSyncHandle?.dispose()
        connectionSyncHandle = null
        reconnectSyncHandle?.dispose()
        reconnectSyncHandle = null
        eventLoopStarted = false
        mainWindowHiddenByClose = false
        holdingExitWhenNoFragmentsOpen = false
        activeFactory = null
        activeApplication = null
        ServiceWorkerFragmentBinding.setAppStartupMountHold(false)
    }

    fun handleLaunchFragment(request: ServiceWorkerFragmentLaunchRequest) {
        val app = activeApplication ?: Application.current
        if (app == null) {
            pendingLaunchRequests.add(request)
            return
        }
        if (activeApplication != null && !eventLoopStarted && activeFactory == null && GenericUI.activeFactory == null) {
            pendingLaunchRequests.add(request)
            return
        }
        val targetClass = FragmentRegistry.resolveFragmentClass(request.fragmentId)
            ?: request.route?.let { FragmentRegistry.resolveFragmentClass(it) }
            ?: return

        val canonicalId = FragmentRegistry.resolveFragmentId(targetClass)
        val mainClass = app.mainFragmentClass
        val isMainFragment = mainClass != null && targetClass == mainClass
        val isDialog = FragmentRegistry.isDialogFragment(targetClass)

        if (isMainFragment) {
            if (request.openAdditionalWindow) {
                openOrUpdateSecondaryWindow(app, targetClass, canonicalId, request, isMain = true)
            } else {
                launchOrShowMainWindow(app, targetClass, canonicalId, request)
            }
            return
        }

        if (isDialog) {
            openOrUpdateDialogWindow(app, targetClass, canonicalId, request)
            return
        }

        if (request.openAdditionalWindow) {
            openOrUpdateSecondaryWindow(app, targetClass, canonicalId, request, isMain = false)
            return
        }

        if (request.clearTask) {
            launchInMainWindow(app, targetClass, canonicalId, request)
            return
        }

        pruneClosedSecondaryWindows()
        val hasExistingSecondary = secondaryWindows.any { entry ->
            !entry.closed &&
                (entry.fragmentId == canonicalId ||
                    entry.fragmentId == request.fragmentId ||
                    entry.fragmentClass == targetClass)
        }
        if (hasExistingSecondary || !isMainFragmentWindowOpen(app)) {
            openOrUpdateSecondaryWindow(app, targetClass, canonicalId, request, isMain = false)
        } else {
            launchInMainWindow(app, targetClass, canonicalId, request)
        }
    }

    fun handleCloseFragment(fragmentId: String, instanceId: String? = null) {
        pruneClosedSecondaryWindows()
        pruneClosedDialogWindows()
        val app = activeApplication ?: Application.current
        val targetClass = FragmentRegistry.resolveFragmentClass(fragmentId)

        val matchingDialogs = dialogWindows.filter { entry ->
            !entry.closed &&
                (instanceId == null || entry.instanceId == instanceId) &&
                (entry.fragmentId == fragmentId ||
                    (targetClass != null && entry.fragmentClass == targetClass))
        }
        if (matchingDialogs.isNotEmpty()) {
            for (entry in matchingDialogs) {
                closeTrackedDialogEntry(entry, closeWindow = true)
            }
            if (app != null) {
                exitAppIfNoWindowsRemain(app)
            }
            return
        }

        val matchingSecondary = secondaryWindows.filter { entry ->
            !entry.closed &&
                (instanceId == null || entry.instanceId == instanceId) &&
                (entry.fragmentId == fragmentId ||
                    (targetClass != null && entry.fragmentClass == targetClass))
        }
        if (matchingSecondary.isNotEmpty()) {
            for (entry in matchingSecondary) {
                closeTrackedSecondaryEntry(entry, closeWindow = true)
            }
            if (app != null) {
                exitAppIfNoWindowsRemain(app)
            }
            return
        }

        if (app == null) return
        val mainClass = app.mainFragmentClass
        val mainFragId = mainClass?.let { FragmentRegistry.resolveFragmentId(it) }
        val activeMainFrag = app.rootFragmentContainer?.currentFragment ?: app.mainWindow.fragmentContainer?.currentFragment
        val matchesMain = (mainClass != null && (targetClass == mainClass || fragmentId == mainFragId)) ||
            (activeMainFrag != null && (
                FragmentRegistry.resolveFragmentId(activeMainFrag::class) == fragmentId ||
                    (targetClass != null && activeMainFrag::class == targetClass)
                ))
        if (matchesMain) {
            trackedMainMount?.let { mount ->
                sendCoordinatorUnmountedIfNeeded(mount.fragmentId, mount.instanceId, mount.route, mount.everSentMounted)
            }
            trackedMainMount = null
            activeMainFrag?.let { frag ->
                ServiceWorkerFragmentBinding.disposeBindingsForFragment(frag)
            }
            app.rootFragmentContainer?.clearCurrentFragment()
            if (app.mainWindow.fragmentContainer !== app.rootFragmentContainer) {
                app.mainWindow.fragmentContainer?.clearCurrentFragment()
            }
            app.mainWindow.hide()
            mainWindowHiddenByClose = true
            exitAppIfNoWindowsRemain(app)
        }
    }

    internal fun activeSecondaryWindowCountForTests(): Int {
        pruneClosedSecondaryWindows()
        return secondaryWindows.count { !it.closed }
    }

    internal fun activeSecondaryWindowsForTests(): List<Window> {
        pruneClosedSecondaryWindows()
        return secondaryWindows.filter { !it.closed }.map { it.window }
    }

    internal fun activeDialogWindowCountForTests(): Int {
        pruneClosedDialogWindows()
        return dialogWindows.count { !it.closed }
    }

    internal fun activeDialogWindowsForTests(): List<DialogWindow> {
        pruneClosedDialogWindows()
        return dialogWindows.filter { !it.closed }.map { it.dialogWindow }
    }

    internal fun resetForTests() {
        val secondaryCopy = secondaryWindows.toList()
        secondaryWindows.clear()
        for (entry in secondaryCopy) {
            if (!entry.closed) {
                entry.closed = true
                if (!entry.window.isDisposed) {
                    entry.window.close()
                }
            }
        }
        val dialogCopy = dialogWindows.toList()
        dialogWindows.clear()
        for (entry in dialogCopy) {
            if (!entry.closed) {
                entry.closed = true
                if (!entry.dialogWindow.isDisposed) {
                    entry.dialogWindow.close()
                    entry.dialogWindow.dispose()
                }
            }
        }
        trackedMainMount = null
        pendingLaunchRequests.clear()
        connectionSyncHandle?.dispose()
        connectionSyncHandle = null
        reconnectSyncHandle?.dispose()
        reconnectSyncHandle = null
        activeApplication = null
        activeFactory = null
        eventLoopStarted = false
        mainWindowHiddenByClose = false
        holdingExitWhenNoFragmentsOpen = false
        ServiceWorkerFragmentBinding.setAppStartupMountHold(false)
    }

    private fun launchOrShowMainWindow(
        app: Application,
        targetClass: KClass<out Fragment>,
        canonicalId: String,
        request: ServiceWorkerFragmentLaunchRequest
    ) {
        ServiceWorkerFragmentBinding.runWithDeferredMountEmissions {
            app.suppressInitialMainWindowShow = false
            mainWindowHiddenByClose = false
            val mainWin = app.mainWindow
            val container = app.rootFragmentContainer ?: mainWin.getOrCreateFragmentContainer()
            val existingFrag = container.currentFragment

            val targetFragment: Fragment
            if (existingFrag != null && existingFrag::class == targetClass) {
                if (request.arguments != null) {
                    existingFrag.arguments = request.arguments
                    app.onLaunchArgumentsApplied(request.arguments)
                }
                targetFragment = existingFrag
            } else {
                trackedMainMount?.let { prev ->
                    sendCoordinatorUnmountedIfNeeded(prev.fragmentId, prev.instanceId, prev.route, prev.everSentMounted)
                }
                trackedMainMount = null
                existingFrag?.let { ServiceWorkerFragmentBinding.disposeBindingsForFragment(it) }

                val newInstanceId = ServiceWorkerFragmentBinding.nextInstanceId()
                val instantiated = instantiateFragmentForApp(app, targetClass, request.arguments, isMain = true)
                ServiceWorkerFragmentBinding.assignPendingInstanceId(instantiated, newInstanceId)
                targetFragment = instantiated
                container.setFragment(instantiated)
                if (request.arguments != null) {
                    app.onLaunchArgumentsApplied(request.arguments)
                }
            }

            app.applyActiveStyleSheetsToRootContent()
            val factory = activeFactory ?: GenericUI.activeFactory
            if (!mainWin.isWindowShown || !mainWin.isVisible) {
                mainWin.show(factory)
            }
            if (mainWin.getWindowState() == WindowState.MINIMIZED) {
                mainWin.restore()
            }
            if (request.bringToFront) {
                mainWin.bringToFront()
                mainWin.focus()
            }
            app.applyActiveStyleSheetsToRootContent()
            syncActiveDialogsAboveMainWindow(mainWin)
            syncMainFragmentMountIfUnbound(targetFragment, canonicalId, request.route)
        }
    }

    private fun launchInMainWindow(
        app: Application,
        targetClass: KClass<out Fragment>,
        canonicalId: String,
        request: ServiceWorkerFragmentLaunchRequest
    ) {
        ServiceWorkerFragmentBinding.runWithDeferredMountEmissions {
            app.suppressInitialMainWindowShow = false
            mainWindowHiddenByClose = false
            val mainWin = app.mainWindow
            val container = app.rootFragmentContainer ?: mainWin.getOrCreateFragmentContainer()
            val existingFrag = container.currentFragment
            val targetFragment: Fragment
            if (!request.openAdditionalWindow && !request.clearTask && existingFrag != null && existingFrag::class == targetClass) {
                if (request.arguments != null) {
                    existingFrag.arguments = request.arguments
                }
                targetFragment = existingFrag
            } else {
                trackedMainMount?.let { prev ->
                    sendCoordinatorUnmountedIfNeeded(prev.fragmentId, prev.instanceId, prev.route, prev.everSentMounted)
                }
                trackedMainMount = null
                existingFrag?.let { ServiceWorkerFragmentBinding.disposeBindingsForFragment(it) }

                val newInstanceId = ServiceWorkerFragmentBinding.nextInstanceId()
                val instantiated = instantiateFragmentForApp(app, targetClass, request.arguments, isMain = false)
                ServiceWorkerFragmentBinding.assignPendingInstanceId(instantiated, newInstanceId)
                targetFragment = instantiated
                if (request.clearTask) {
                    container.setFragment(instantiated)
                } else {
                    container.pushFragment(instantiated)
                }
            }
            app.applyActiveStyleSheetsToRootContent()
            val factory = activeFactory ?: GenericUI.activeFactory
            if (!mainWin.isWindowShown || !mainWin.isVisible) {
                mainWin.show(factory)
            }
            if (request.bringToFront) {
                mainWin.bringToFront()
                mainWin.focus()
            }
            app.applyActiveStyleSheetsToRootContent()
            syncActiveDialogsAboveMainWindow(mainWin)
            syncMainFragmentMountIfUnbound(targetFragment, canonicalId, request.route)
        }
    }

    private fun openOrUpdateSecondaryWindow(
        app: Application,
        targetClass: KClass<out Fragment>,
        canonicalId: String,
        request: ServiceWorkerFragmentLaunchRequest,
        isMain: Boolean = false
    ) {
        pruneClosedSecondaryWindows()
        if (!request.openAdditionalWindow) {
            val existing = secondaryWindows.firstOrNull { entry ->
                !entry.closed &&
                    (entry.fragmentId == canonicalId ||
                        entry.fragmentId == request.fragmentId ||
                        entry.fragmentClass == targetClass)
            }
            if (existing != null) {
                if (request.arguments != null) {
                    existing.fragment.arguments = request.arguments
                }
                val factory = activeFactory ?: GenericUI.activeFactory
                if (!existing.window.isVisible || !existing.window.isWindowShown) {
                    existing.window.show(factory)
                }
                if (existing.window.getWindowState() == WindowState.MINIMIZED) {
                    existing.window.restore()
                }
                if (request.bringToFront) {
                    existing.window.bringToFront()
                    existing.window.focus()
                }
                syncSecondaryEntryMountState(existing)
                return
            }
        }

        val initialInstanceId = ServiceWorkerFragmentBinding.nextInstanceId()
        val fragment = instantiateFragmentForApp(app, targetClass, request.arguments, isMain = isMain)
        ServiceWorkerFragmentBinding.assignPendingInstanceId(fragment, initialInstanceId)

        val fallbackTitle = app.mainWindow.getTitle().ifBlank { "GenericUI" }
        val initialTitle = fragment.pageTitle.ifBlank { fallbackTitle }
        val secondaryWindow = Window(title = initialTitle)
        app.mainWindow.getWindowIcon()?.let { secondaryWindow.setWindowIcon(it) }
        val container = secondaryWindow.getOrCreateFragmentContainer()

        val effectiveRoute = request.route ?: FragmentRegistry.resolveRoute(targetClass)
        val entry = TrackedSecondaryWindow(
            fragmentId = canonicalId,
            instanceId = initialInstanceId,
            route = effectiveRoute,
            fragmentClass = targetClass,
            window = secondaryWindow,
            container = container,
            fragment = fragment
        )
        secondaryWindows.add(entry)

        secondaryWindow.setOnWindowCloseRequested {
            if (!entry.handlingClose) {
                entry.handlingClose = true
                closeTrackedSecondaryEntry(entry, closeWindow = false)
                exitAppIfNoWindowsRemain(app)
            }
            true
        }

        ServiceWorkerFragmentBinding.runWithDeferredMountEmissions {
            val factory = activeFactory ?: GenericUI.activeFactory
            container.setFragment(fragment)
            if (fragment.pageTitle.isNotBlank()) {
                secondaryWindow.setTitle(fragment.pageTitle)
            }
            val targetCaps = factory?.defaultTitleCapabilities ?: secondaryWindow.titleCapabilities
            val canUseCustomUndecoratedChrome = targetCaps.supportsCustomTitleBar || !targetCaps.isTitleBarRequired
            if (fragment.prefersUndecoratedWindow && canUseCustomUndecoratedChrome) {
                secondaryWindow.setTitleBar(null)
                secondaryWindow.setUndecorated(true)
            }

            app.applyActiveStyleSheetsTo(fragment)
            secondaryWindow.show(factory)
            if (fragment.prefersUndecoratedWindow) {
                val runtimeCaps = secondaryWindow.titleCapabilities
                val runtimeCanUseUndecorated = runtimeCaps.supportsCustomTitleBar || !runtimeCaps.isTitleBarRequired
                if (!runtimeCanUseUndecorated && secondaryWindow.isUndecorated) {
                    secondaryWindow.setUndecorated(false)
                }
            }
            if (request.bringToFront) {
                secondaryWindow.bringToFront()
                secondaryWindow.focus()
            }

            syncSecondaryEntryMountState(entry)
            acquireExitHoldIfNeeded(app)
        }
    }

    private fun openOrUpdateDialogWindow(
        app: Application,
        targetClass: KClass<out Fragment>,
        canonicalId: String,
        request: ServiceWorkerFragmentLaunchRequest
    ) {
        pruneClosedDialogWindows()
        val ownerWindow = app.mainWindow.takeIf { isMainFragmentWindowOpen(app) }
        val factory = activeFactory ?: GenericUI.activeFactory

        if (!request.openAdditionalWindow) {
            val existing = dialogWindows.firstOrNull { entry ->
                !entry.closed &&
                    (entry.fragmentId == canonicalId ||
                        entry.fragmentId == request.fragmentId ||
                        entry.fragmentClass == targetClass)
            }
            if (existing != null) {
                if (request.arguments != null) {
                    existing.fragment.arguments = request.arguments
                }
                if (ownerWindow != null && existing.dialogWindow.ownerWindow == null) {
                    existing.dialogWindow.open(ownerWindow)
                } else if (!existing.dialogWindow.isVisible || !existing.dialogWindow.isWindowShown) {
                    if (ownerWindow != null) {
                        existing.dialogWindow.open(ownerWindow)
                    } else if (factory != null) {
                        existing.dialogWindow.show(factory)
                    } else {
                        existing.dialogWindow.open(null)
                    }
                }
                if (request.bringToFront) {
                    existing.dialogWindow.bringToFront()
                    existing.dialogWindow.focus()
                }
                syncDialogEntryMountState(existing)
                return
            }
        }

        val initialInstanceId = ServiceWorkerFragmentBinding.nextInstanceId()
        val fragment = instantiateFragmentForApp(app, targetClass, request.arguments, isMain = false)
        ServiceWorkerFragmentBinding.assignPendingInstanceId(fragment, initialInstanceId)

        val fallbackTitle = FragmentRegistry.getMetadata(targetClass)?.label?.takeIf { it.isNotBlank() }
            ?: app.mainWindow.getTitle().ifBlank { "GenericUI" }
        val initialTitle = fragment.pageTitle.ifBlank { fallbackTitle }
        val dialogWindow = DialogWindow(title = initialTitle)
        app.mainWindow.getWindowIcon()?.let { dialogWindow.setWindowIcon(it) }
        val container = dialogWindow.getOrCreateFragmentContainer()
        container.hostingIWindow = dialogWindow

        val effectiveRoute = request.route ?: FragmentRegistry.resolveRoute(targetClass)
        val entry = TrackedDialogEntry(
            fragmentId = canonicalId,
            instanceId = initialInstanceId,
            route = effectiveRoute,
            fragmentClass = targetClass,
            dialogWindow = dialogWindow,
            container = container,
            fragment = fragment
        )
        dialogWindows.add(entry)

        dialogWindow.onCloseHandler = {
            if (!entry.handlingClose) {
                entry.handlingClose = true
                closeTrackedDialogEntry(entry, closeWindow = false)
                exitAppIfNoWindowsRemain(app)
            }
        }

        ServiceWorkerFragmentBinding.runWithDeferredMountEmissions {
            container.setFragment(fragment)
            if (fragment.pageTitle.isNotBlank()) {
                dialogWindow.setTitle(fragment.pageTitle)
            }
            val dialogCaps = dialogWindow.titleCapabilities
            val canUseCustomUndecoratedChrome = dialogCaps.supportsCustomTitleBar || !dialogCaps.isTitleBarRequired
            if (fragment.prefersUndecoratedWindow && canUseCustomUndecoratedChrome) {
                dialogWindow.setTitleBar(null)
                dialogWindow.setUndecorated(true)
            }

            app.applyActiveStyleSheetsTo(fragment)
            if (ownerWindow != null) {
                dialogWindow.open(ownerWindow)
            } else if (factory != null && GenericUI.activeFactory == null) {
                dialogWindow.show(factory)
            } else {
                dialogWindow.open(null)
            }
            if (request.bringToFront) {
                dialogWindow.bringToFront()
                dialogWindow.focus()
            }

            syncDialogEntryMountState(entry)
            acquireExitHoldIfNeeded(app)
        }
    }

    private fun syncActiveDialogsAboveMainWindow(mainWin: Window) {
        pruneClosedDialogWindows()
        for (entry in dialogWindows.toList()) {
            if (entry.closed) continue
            if (entry.dialogWindow.ownerWindow == null) {
                entry.dialogWindow.open(mainWin)
            }
            entry.dialogWindow.bringToFront()
        }
    }

    private fun isMainFragmentWindowOpen(app: Application): Boolean =
        !app.suppressInitialMainWindowShow &&
            !mainWindowHiddenByClose &&
            app.mainWindow.isWindowShown &&
            app.mainWindow.isVisible &&
            ((app.rootFragmentContainer?.currentFragment ?: app.mainWindow.fragmentContainer?.currentFragment) != null ||
                app.mainWindow.getContent() != null)

    private fun instantiateFragmentForApp(
        app: Application,
        targetClass: KClass<out Fragment>,
        args: FragmentBundle?,
        isMain: Boolean
    ): Fragment {
        val created = if (isMain) {
            app.createMainFragment(args) ?: FragmentRegistry.instantiate(targetClass, args)
        } else {
            FragmentRegistry.instantiate(targetClass, args)
        }
        if (args != null) {
            created.arguments = args
        }
        created.application = app
        return created
    }

    private fun syncSecondaryEntryMountState(entry: TrackedSecondaryWindow) {
        val activeBinding = ServiceWorkerFragmentBinding.activeBindingFor(entry.fragment)
        if (activeBinding != null) {
            entry.instanceId = activeBinding.instanceId
            entry.coordinatorManagedMount = false
            return
        }
        entry.coordinatorManagedMount = true
        sendCoordinatorMountedIfNeeded(
            fragmentId = entry.fragmentId,
            instanceId = entry.instanceId,
            route = entry.route,
            lastEpoch = entry.lastMountedEpoch,
            onSent = { epoch ->
                entry.everSentMounted = true
                entry.lastMountedEpoch = epoch
            }
        )
    }

    private fun syncDialogEntryMountState(entry: TrackedDialogEntry) {
        val activeBinding = ServiceWorkerFragmentBinding.activeBindingFor(entry.fragment)
        if (activeBinding != null) {
            entry.instanceId = activeBinding.instanceId
            entry.coordinatorManagedMount = false
            return
        }
        entry.coordinatorManagedMount = true
        sendCoordinatorMountedIfNeeded(
            fragmentId = entry.fragmentId,
            instanceId = entry.instanceId,
            route = entry.route,
            lastEpoch = entry.lastMountedEpoch,
            onSent = { epoch ->
                entry.everSentMounted = true
                entry.lastMountedEpoch = epoch
            }
        )
    }

    private fun syncMainFragmentMountIfUnbound(
        fragment: Fragment,
        explicitId: String? = null,
        explicitRoute: String? = null
    ) {
        if (ServiceWorkerFragmentBinding.hasActiveBindingFor(fragment)) {
            trackedMainMount = null
            return
        }
        val resolvedId = explicitId ?: FragmentRegistry.resolveFragmentId(fragment::class)
        val resolvedRoute = explicitRoute ?: FragmentRegistry.resolveRoute(fragment::class)
        val existing = trackedMainMount
        val mount = if (existing != null && existing.fragment === fragment) {
            existing
        } else {
            TrackedMainFragmentMount(
                fragment = fragment,
                fragmentId = resolvedId,
                instanceId = ServiceWorkerFragmentBinding.nextInstanceId(),
                route = resolvedRoute
            ).also { trackedMainMount = it }
        }
        sendCoordinatorMainMountedIfNeeded(mount)
    }

    private fun ensureChannelMountSyncInstalled() {
        val channel = ServiceWorkerExtension.channel
        if (connectionSyncHandle == null) {
            connectionSyncHandle = channel.addConnectionListener { connected ->
                if (connected) {
                    scope.launch {
                        resendCoordinatorMounts()
                    }
                }
            }
        }
        if (reconnectSyncHandle == null) {
            reconnectSyncHandle = channel.onReconnected {
                resendCoordinatorMounts()
            }
        }
    }

    private fun resendCoordinatorMounts() {
        for (entry in secondaryWindows.toList()) {
            if (!entry.closed && entry.coordinatorManagedMount) {
                sendCoordinatorMountedIfNeeded(
                    fragmentId = entry.fragmentId,
                    instanceId = entry.instanceId,
                    route = entry.route,
                    lastEpoch = entry.lastMountedEpoch,
                    onSent = { epoch ->
                        entry.everSentMounted = true
                        entry.lastMountedEpoch = epoch
                    }
                )
            }
        }
        for (entry in dialogWindows.toList()) {
            if (!entry.closed && entry.coordinatorManagedMount) {
                sendCoordinatorMountedIfNeeded(
                    fragmentId = entry.fragmentId,
                    instanceId = entry.instanceId,
                    route = entry.route,
                    lastEpoch = entry.lastMountedEpoch,
                    onSent = { epoch ->
                        entry.everSentMounted = true
                        entry.lastMountedEpoch = epoch
                    }
                )
            }
        }
        trackedMainMount?.let { mount ->
            if (!ServiceWorkerFragmentBinding.hasActiveBindingFor(mount.fragment)) {
                sendCoordinatorMainMountedIfNeeded(mount)
            }
        }
    }

    private fun sendCoordinatorMountedIfNeeded(
        fragmentId: String,
        instanceId: String,
        route: String?,
        lastEpoch: Long,
        onSent: (Long) -> Unit
    ) {
        val channel = ServiceWorkerExtension.channel
        if (!channel.isSupported || !channel.isConnected) return
        val epoch = channel.connectionEpoch
        if (lastEpoch == epoch && epoch > 0L) return
        onSent(epoch)
        val payload = ServiceWorkerFragmentLaunchRequest.encodeMountControlPayload(
            fragmentId = fragmentId,
            instanceId = instanceId,
            route = route
        )
        channel.sendControlAsync(
            typeId = ServiceWorkerEnvelope.CONTROL_FRAGMENT_MOUNTED,
            payloadJson = payload,
            sourceFragmentId = fragmentId
        )
    }

    private fun sendCoordinatorMainMountedIfNeeded(mount: TrackedMainFragmentMount) {
        val channel = ServiceWorkerExtension.channel
        if (!channel.isSupported || !channel.isConnected) return
        val epoch = channel.connectionEpoch
        if (mount.lastMountedEpoch == epoch && epoch > 0L) return
        mount.everSentMounted = true
        mount.lastMountedEpoch = epoch
        val payload = ServiceWorkerFragmentLaunchRequest.encodeMountControlPayload(
            fragmentId = mount.fragmentId,
            instanceId = mount.instanceId,
            route = mount.route
        )
        channel.sendControlAsync(
            typeId = ServiceWorkerEnvelope.CONTROL_FRAGMENT_MOUNTED,
            payloadJson = payload,
            sourceFragmentId = mount.fragmentId
        )
    }

    private fun sendCoordinatorUnmountedIfNeeded(
        fragmentId: String,
        instanceId: String,
        route: String?,
        everMounted: Boolean
    ) {
        if (!everMounted) return
        val channel = ServiceWorkerExtension.channel
        if (!channel.isSupported || !channel.isConnected) return
        val payload = ServiceWorkerFragmentLaunchRequest.encodeMountControlPayload(
            fragmentId = fragmentId,
            instanceId = instanceId,
            route = route
        )
        channel.sendControlAsync(
            typeId = ServiceWorkerEnvelope.CONTROL_FRAGMENT_UNMOUNTED,
            payloadJson = payload,
            sourceFragmentId = fragmentId
        )
    }

    private fun closeTrackedSecondaryEntry(entry: TrackedSecondaryWindow, closeWindow: Boolean) {
        if (entry.closed) return
        entry.closed = true
        entry.handlingClose = true
        secondaryWindows.remove(entry)
        disposeSecondaryEntryResources(entry, closeWindow)
    }

    private fun disposeSecondaryEntryResources(entry: TrackedSecondaryWindow, closeWindow: Boolean) {
        ServiceWorkerFragmentBinding.disposeBindingsForFragment(entry.fragment)
        if (entry.coordinatorManagedMount) {
            sendCoordinatorUnmountedIfNeeded(
                fragmentId = entry.fragmentId,
                instanceId = entry.instanceId,
                route = entry.route,
                everMounted = entry.everSentMounted
            )
        }
        if (closeWindow) {
            if (!entry.window.isDisposed) {
                entry.window.close()
            }
        } else {
            if (!entry.container.isDisposed) {
                entry.container.clearCurrentFragment()
            }
            if (!entry.window.isDisposed) {
                entry.window.dispose()
            }
        }
    }

    private fun closeTrackedDialogEntry(entry: TrackedDialogEntry, closeWindow: Boolean) {
        if (entry.closed) return
        entry.closed = true
        entry.handlingClose = true
        dialogWindows.remove(entry)
        disposeDialogEntryResources(entry, closeWindow)
    }

    private fun disposeDialogEntryResources(entry: TrackedDialogEntry, closeWindow: Boolean) {
        ServiceWorkerFragmentBinding.disposeBindingsForFragment(entry.fragment)
        if (entry.coordinatorManagedMount) {
            sendCoordinatorUnmountedIfNeeded(
                fragmentId = entry.fragmentId,
                instanceId = entry.instanceId,
                route = entry.route,
                everMounted = entry.everSentMounted
            )
        }
        if (closeWindow) {
            if (!entry.dialogWindow.isDisposed) {
                entry.dialogWindow.close()
            }
        } else if (!entry.container.isDisposed) {
            entry.container.clearCurrentFragment()
        }
        if (!entry.dialogWindow.isDisposed) {
            entry.dialogWindow.dispose()
        }
    }

    internal fun hasOpenCoordinatorFragments(app: Application): Boolean =
        activeApplication === app && (hasOpenSecondaryWindows() || hasOpenDialogWindows())

    private fun hasOpenSecondaryWindows(): Boolean {
        pruneClosedSecondaryWindows()
        return secondaryWindows.any { !it.closed }
    }

    private fun hasOpenDialogWindows(): Boolean {
        pruneClosedDialogWindows()
        return dialogWindows.any { !it.closed }
    }

    private fun pruneClosedSecondaryWindows() {
        secondaryWindows.removeAll { entry ->
            entry.closed ||
                entry.fragment.state == FragmentState.DESTROYED ||
                entry.window.nativeImpl?.isDisposed == true
        }
    }

    private fun pruneClosedDialogWindows() {
        dialogWindows.removeAll { entry ->
            entry.closed ||
                entry.fragment.state == FragmentState.DESTROYED ||
                entry.dialogWindow.isDisposed ||
                !entry.dialogWindow.isWindowShown ||
                entry.dialogWindow.fragmentContainer?.currentFragment == null
        }
    }

    private fun acquireExitHoldIfNeeded(app: Application) {
        if (hasOpenSecondaryWindows() || hasOpenDialogWindows()) {
            if (app.exitWhenNoFragmentsOpen) {
                app.exitWhenNoFragmentsOpen = false
                holdingExitWhenNoFragmentsOpen = true
            }
        }
    }

    private fun releaseExitHoldIfNoTrackedWindows(app: Application) {
        if (holdingExitWhenNoFragmentsOpen && !hasOpenSecondaryWindows() && !hasOpenDialogWindows()) {
            holdingExitWhenNoFragmentsOpen = false
            app.exitWhenNoFragmentsOpen = true
        }
    }

    private fun exitAppIfNoWindowsRemain(app: Application) {
        if (hasOpenSecondaryWindows() || hasOpenDialogWindows()) return
        releaseExitHoldIfNoTrackedWindows(app)
        if (!app.exitWhenNoFragmentsOpen) return
        if (app.hasAnyOpenFragment()) return
        val wasActiveApp = activeApplication === app
        app.checkExitIfNoFragmentsOpen()
        if (wasActiveApp && activeApplication === app && !app.hasAnyOpenFragment()) {
            app.exit(0)
        }
    }
}
