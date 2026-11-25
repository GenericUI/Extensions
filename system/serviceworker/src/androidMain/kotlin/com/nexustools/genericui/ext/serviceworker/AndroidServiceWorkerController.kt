package com.nexustools.genericui.ext.serviceworker

import android.app.Activity
import android.app.Notification
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.nexustools.genericui.android.AndroidActivityLifecycleObserver
import com.nexustools.genericui.android.AndroidActivityRegistry
import com.nexustools.genericui.android.AndroidExtensionRuntimeBridge
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.exception.SystemTrayUnsupportedException
import com.nexustools.genericui.resources.ResourceID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile

/**
 * Coordinates the Android `android.app.Service` lifecycle, dedicated `"GenericUI-ServiceWorker"`
 * background thread dispatcher, ongoing foreground notification + dynamic updates, notification
 * button intents, local [AndroidServiceWorkerBinder] IPC, and boot receiver delegation.
 */
object AndroidServiceWorkerController {

    internal val runtimeHost: ServiceWorkerRuntimeHost = object : ServiceWorkerRuntimeHost {
        override val backendName: String = BackendTarget.Android.displayName
        override val supportsMultipleWindows: Boolean = false
        override val supportsFragmentLaunch: Boolean = true
        override val supportsDialogFragments: Boolean = true
        override val supportsSystemTray: Boolean = false
        override val supportsOngoingNotification: Boolean = true
        override val supportsAutostart: Boolean = true

        override fun updateNotification(
            title: String,
            text: String,
            subText: String?,
            icon: ResourceID?,
            actions: List<ServiceWorkerNotificationAction>
        ) {
            this@AndroidServiceWorkerController.updateNotification(title, text, subText, icon, actions)
        }

        override fun updateTray(
            iconPathOrUrl: String?,
            iconResource: ResourceID?,
            tooltip: String?,
            badgeText: String?,
            menu: ContextMenu?,
            onClick: (() -> Unit)?,
            macosIconPathOrUrl: String?
        ) {
            throw SystemTrayUnsupportedException(BackendTarget.Android.displayName)
        }

        override fun openMainWindow(route: String?) {
            this@AndroidServiceWorkerController.openMainWindow(route)
        }

        override fun closeMainWindow() {
            this@AndroidServiceWorkerController.closeMainWindow()
        }

        override fun requestLaunchFragment(request: ServiceWorkerFragmentLaunchRequest) {
            this@AndroidServiceWorkerController.requestLaunchFragment(request)
        }

        override fun requestCloseFragment(fragmentId: String, instanceId: String?) {
            this@AndroidServiceWorkerController.requestCloseFragment(fragmentId, instanceId)
        }

        override fun stopSelf() {
            this@AndroidServiceWorkerController.stopSelf()
        }

        override fun exitApplication(exitCode: Int) {
            this@AndroidServiceWorkerController.exitApplication(exitCode)
        }

        override fun isAutostartEnabled(): Boolean =
            this@AndroidServiceWorkerController.isAutostartEnabled()

        override fun setAutostartEnabled(enabled: Boolean): Boolean =
            this@AndroidServiceWorkerController.setAutostartEnabled(enabled)

        override suspend fun sendToClient(clientId: String, envelope: ServiceWorkerEnvelope): Boolean =
            this@AndroidServiceWorkerController.sendToClient(clientId, envelope)

        override suspend fun broadcastEnvelope(envelope: ServiceWorkerEnvelope): Int =
            this@AndroidServiceWorkerController.broadcastEnvelope(envelope)

        override fun disconnectClient(clientId: String) {
            this@AndroidServiceWorkerController.disconnectClient(clientId)
        }
    }

    const val ACTION_STOP_SERVICE: String = "com.nexustools.genericui.ext.serviceworker.ACTION_STOP"
    const val ACTION_NOTIFICATION_BUTTON: String = "com.nexustools.genericui.ext.serviceworker.ACTION_BUTTON"
    const val EXTRA_ACTION_ID: String = "genericui_sw_action_id"
    const val NOTIFICATION_ID: Int = 7401

    private const val PREF_FILE: String = "genericui_serviceworker_prefs"
    private const val PREF_KEY_AUTOSTART: String = "autostart_enabled"
    private const val ANDROID_CLIENT_ID: String = "android-ui-client"

    private val workerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GenericUI-ServiceWorker").apply { isDaemon = true }
    }
    private val workerDispatcher: CoroutineDispatcher = workerExecutor.asCoroutineDispatcher()
    private val workerControlScope = CoroutineScope(SupervisorJob() + workerDispatcher)

    @Volatile
    private var serviceInstance: Service? = null

    @Volatile
    private var registeredServiceClass: Class<out Service>? = null

    @Volatile
    private var workerInstance: ServiceWorker? = null

    @Volatile
    private var currentOptions: ServiceWorkerOptions = ServiceWorkerOptions()

    @Volatile
    private var clientEnvelopeListener: ((ServiceWorkerEnvelope) -> Unit)? = null

    @Volatile
    private var clientConnectionListener: ((Boolean) -> Unit)? = null

    @Volatile
    private var serviceBound: Boolean = false

    @Volatile
    private var lastNotification: Notification? = null

    private val lifecycleObserverInstalled = AtomicBoolean(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AndroidServiceWorkerBinder
            if (binder != null) {
                serviceBound = true
                workerInstance?.dispatchClientConnected(ANDROID_CLIENT_ID)
                clientConnectionListener?.invoke(true)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            clientConnectionListener?.invoke(false)
        }
    }

    private val activityObserver = object : AndroidActivityLifecycleObserver {
        override fun onActivityStarted(activity: Activity) {
            if (ServiceWorkerExtension.hasRegisteredWorkerFactory() && serviceInstance == null) {
                ensureServiceStartedAndBound(activity.applicationContext)
            }
        }
    }

    fun registerServiceClass(serviceClass: Class<out Service>) {
        registeredServiceClass = serviceClass
    }

    fun installLifecycleObserverIfNeeded() {
        if (lifecycleObserverInstalled.compareAndSet(false, true)) {
            AndroidExtensionRuntimeBridge.registerActivityLifecycleObserver(activityObserver)
        }
    }

    fun updateOptions(options: ServiceWorkerOptions) {
        currentOptions = options
    }

    fun activeWorker(): ServiceWorker? = workerInstance

    internal fun isInProcessClientConnected(): Boolean =
        serviceBound && clientEnvelopeListener != null && workerInstance != null

    fun lastPostedNotification(): Notification? = lastNotification

    fun resolveChannelId(context: Context? = null): String {
        val pkg = currentOptions.appId.ifBlank { context?.packageName ?: "com.nexustools.genericui.app" }
        return "genericui.serviceworker.$pkg"
    }

    fun onServiceCreated(
        service: Service,
        explicitWorkerFactory: (() -> ServiceWorker)? = null
    ) {
        registeredServiceClass = service::class.java
        serviceInstance = service
        val opts = currentOptions
        val channelId = resolveChannelId(service)
        AndroidExtensionRuntimeBridge.ensureNotificationChannel(
            context = service,
            channelId = channelId,
            channelName = "${opts.appName.ifBlank { "GenericUI" }} Background Service",
            description = "Keeps ${opts.appName.ifBlank { "GenericUI" }} background tasks running"
        )

        val worker = workerInstance ?: (explicitWorkerFactory ?: ServiceWorkerExtension.resolvedWorkerFactory())?.invoke()?.also {
            workerInstance = it
        }
        val title = worker?.notificationTitle?.takeIf { it.isNotBlank() && it != "GenericUI" }
            ?: opts.appName.ifBlank { "GenericUI" }
        val text = worker?.notificationText ?: "Running in background"
        val subText = worker?.notificationSubText
        val iconRes = worker?.notificationIcon
        val actions = worker?.getEffectiveNotificationActions()
            ?: listOf(ServiceWorkerNotificationAction.createDefaultStopAction())

        val initialNotification = buildNotification(
            context = service,
            serviceClass = service::class.java,
            title = title,
            text = text,
            subText = subText,
            icon = iconRes,
            actions = actions
        )
        lastNotification = initialNotification
        AndroidExtensionRuntimeBridge.promoteToForegroundService(
            service = service,
            notificationId = NOTIFICATION_ID,
            notification = initialNotification,
            foregroundServiceType = resolveForegroundServiceTypeMask(opts.androidForegroundServiceType)
        )

        if (worker != null && !worker.isRunning) {
            workerControlScope.launch {
                worker.attachAndStart(runtimeHost, opts)
                if (serviceBound) {
                    worker.dispatchClientConnected(ANDROID_CLIENT_ID)
                }
            }
        }
    }

    fun onServiceStartCommand(
        service: Service,
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        serviceInstance = service
        val actionStr = intent?.action
        val worker = workerInstance
        when (actionStr) {
            ACTION_STOP_SERVICE -> {
                val actionId = intent.getStringExtra(EXTRA_ACTION_ID)
                    ?: ServiceWorkerNotificationAction.DEFAULT_STOP_ACTION_ID
                if (worker != null) {
                    workerControlScope.launch {
                        if (!worker.dispatchNotificationActionById(actionId)) {
                            worker.stopSelf()
                        }
                    }
                } else {
                    stopSelf()
                }
                return Service.START_NOT_STICKY
            }
            ACTION_NOTIFICATION_BUTTON -> {
                val actionId = intent.getStringExtra(EXTRA_ACTION_ID)
                if (!actionId.isNullOrBlank() && worker != null) {
                    workerControlScope.launch {
                        worker.dispatchNotificationActionById(actionId)
                    }
                }
                return Service.START_STICKY
            }
            else -> {
                if (worker != null) {
                    updateNotification(
                        title = worker.notificationTitle,
                        text = worker.notificationText,
                        subText = worker.notificationSubText,
                        icon = worker.notificationIcon,
                        actions = worker.getEffectiveNotificationActions()
                    )
                }
                return Service.START_STICKY
            }
        }
    }

    fun onServiceBind(service: Service, intent: Intent?): IBinder {
        serviceInstance = service
        return AndroidServiceWorkerBinder(this)
    }

    fun onServiceDestroyed(service: Service) {
        val worker = workerInstance
        workerInstance = null
        if (serviceInstance === service) {
            serviceInstance = null
        }
        serviceBound = false
        worker?.performStop()
        lastNotification = null
        AndroidExtensionRuntimeBridge.cancelNotification(service, NOTIFICATION_ID)
        clientEnvelopeListener = null
        clientConnectionListener?.invoke(false)
    }

    fun onBootCompleted(context: Context, serviceClass: Class<out Service>) {
        registerServiceClass(serviceClass)
        val autostartAllowed = isAutostartEnabledForContext(context)
        if (!autostartAllowed) return
        val intent = Intent(context, serviceClass)
        AndroidExtensionRuntimeBridge.startForegroundServiceCompat(context, intent)
    }

    fun ensureServiceStartedAndBound(context: Context?): Boolean {
        val ctx = context ?: AndroidExtensionRuntimeBridge.resolveApplicationContext() ?: return false
        val serviceClass = resolveRegisteredServiceClass(ctx) ?: return false
        val intent = Intent(ctx, serviceClass)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AndroidExtensionRuntimeBridge.startForegroundServiceCompat(ctx, intent)
        } else {
            AndroidExtensionRuntimeBridge.startServiceCompat(ctx, intent)
        }
        return AndroidExtensionRuntimeBridge.bindServiceCompat(ctx, intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    internal fun connectInProcessClient(
        listener: (ServiceWorkerEnvelope) -> Unit,
        connectionChanged: (Boolean) -> Unit
    ): Boolean {
        clientEnvelopeListener = listener
        clientConnectionListener = connectionChanged
        val ctx = AndroidExtensionRuntimeBridge.resolveApplicationContext()
        val hasServiceClass = ctx != null && resolveRegisteredServiceClass(ctx) != null
        if (serviceInstance != null || workerInstance != null) {
            serviceBound = true
            workerInstance?.dispatchClientConnected(ANDROID_CLIENT_ID)
            connectionChanged(true)
            return true
        }
        if (hasServiceClass) {
            val started = ensureServiceStartedAndBound(ctx)
            connectionChanged(started || serviceBound)
            return started || serviceBound
        }
        val factory = ServiceWorkerExtension.resolvedWorkerFactory() ?: return false
        val worker = factory()
        workerInstance = worker
        worker.attachAndStart(runtimeHost, currentOptions)
        serviceBound = true
        worker.dispatchClientConnected(ANDROID_CLIENT_ID)
        connectionChanged(true)
        return true
    }

    internal fun disconnectInProcessClient() {
        val ctx = AndroidExtensionRuntimeBridge.resolveApplicationContext()
        if (ctx != null && serviceBound) {
            AndroidExtensionRuntimeBridge.unbindServiceSafe(ctx, serviceConnection)
        }
        serviceBound = false
        workerInstance?.dispatchClientDisconnected(ANDROID_CLIENT_ID)
        clientEnvelopeListener = null
        clientConnectionListener?.invoke(false)
    }

    internal fun dispatchFromClient(envelope: ServiceWorkerEnvelope): Boolean {
        val worker = workerInstance ?: return false
        workerControlScope.launch {
            worker.dispatchIncomingEnvelope(ANDROID_CLIENT_ID, envelope)
        }
        return true
    }

    fun updateNotification(
        title: String,
        text: String,
        subText: String?,
        icon: ResourceID?,
        actions: List<ServiceWorkerNotificationAction>
    ) {
        val ctx = serviceInstance ?: AndroidExtensionRuntimeBridge.resolveApplicationContext() ?: return
        val sClass = serviceInstance?.javaClass ?: resolveRegisteredServiceClass(ctx) ?: return
        val notification = buildNotification(
            context = ctx,
            serviceClass = sClass,
            title = title,
            text = text,
            subText = subText,
            icon = icon,
            actions = actions
        )
        lastNotification = notification
        AndroidExtensionRuntimeBridge.notifyOngoing(ctx, NOTIFICATION_ID, notification)
    }

    fun openMainWindow(route: String?) {
        val ctx = serviceInstance ?: AndroidExtensionRuntimeBridge.resolveApplicationContext()
            ?: throw ServiceWorkerFragmentLaunchUnsupportedException(
                fragmentId = "__main__",
                backendName = BackendTarget.Android.displayName,
                message = "No Android Context is available to open the main Activity."
            )
        val mainClass = AndroidActivityRegistry.mainActivityClass
        val launchIntent = if (mainClass != null) {
            Intent(ctx, mainClass)
        } else {
            ctx.packageManager?.getLaunchIntentForPackage(ctx.packageName)
        } ?: throw ServiceWorkerFragmentLaunchUnsupportedException(
            fragmentId = "__main__",
            backendName = BackendTarget.Android.displayName,
            message = "No main launcher Activity is registered on Android."
        )
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (!route.isNullOrBlank()) {
            launchIntent.putExtra("genericui_route", route)
        }
        try {
            ctx.startActivity(launchIntent)
        } catch (t: Throwable) {
            throw ServiceWorkerFragmentLaunchUnsupportedException(
                fragmentId = "__main__",
                backendName = BackendTarget.Android.displayName,
                message = "Android blocked or failed starting main Activity: ${t.message}",
                cause = t
            )
        }
    }

    fun closeMainWindow() {
        val activity = AndroidActivityRegistry.resolveActiveActivity() ?: return
        activity.finish()
    }

    fun requestLaunchFragment(request: ServiceWorkerFragmentLaunchRequest) {
        if (serviceBound && clientEnvelopeListener != null) {
            val env = ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                typeId = ServiceWorkerEnvelope.CONTROL_LAUNCH_FRAGMENT,
                payloadJson = request.toControlPayloadJson(),
                targetFragmentId = request.fragmentId
            )
            workerControlScope.launch {
                if (!sendToClient(ANDROID_CLIENT_ID, env)) {
                    throw ServiceWorkerIpcException(
                        "Failed to send launch control envelope for fragment '${request.fragmentId}' to Android UI client."
                    )
                }
            }
            return
        }
        val ctx = serviceInstance ?: AndroidExtensionRuntimeBridge.resolveApplicationContext()
            ?: throw ServiceWorkerFragmentLaunchUnsupportedException(
                fragmentId = request.fragmentId,
                backendName = BackendTarget.Android.displayName,
                message = "No Android Context is available to start fragment '${request.fragmentId}'."
            )
        val targetActivityClass = AndroidActivityRegistry.resolveByFragmentId(request.fragmentId)
            ?: request.route?.let { route ->
                FragmentRegistry.resolveFragmentClass(route)?.let { fragClass ->
                    AndroidActivityRegistry.getActivityClass(fragClass)
                }
            }
            ?: AndroidActivityRegistry.mainActivityClass
        val launchIntent = if (targetActivityClass != null) {
            Intent(ctx, targetActivityClass)
        } else {
            ctx.packageManager?.getLaunchIntentForPackage(ctx.packageName)
        } ?: throw ServiceWorkerFragmentLaunchUnsupportedException(
            fragmentId = request.fragmentId,
            backendName = BackendTarget.Android.displayName,
            message = "No launcher Activity is registered on Android for fragment '${request.fragmentId}'."
        )
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (request.clearTask) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val bundle = FragmentBundle().apply {
            request.arguments?.let { putAll(it) }
            putString(FragmentBundle.KEY_LAUNCH_FRAGMENT_ID, request.fragmentId)
            request.route?.takeIf { it.isNotBlank() }?.let { putString(FragmentBundle.KEY_LAUNCH_ROUTE, it) }
            putBoolean(FragmentBundle.KEY_LAUNCH_OPEN_ADDITIONAL_WINDOW, request.openAdditionalWindow)
        }
        AndroidActivityRegistry.putFragmentBundleExtras(launchIntent, bundle)
        launchIntent.putExtra(FragmentBundle.KEY_LAUNCH_FRAGMENT_ID, request.fragmentId)
        request.route?.takeIf { it.isNotBlank() }?.let { launchIntent.putExtra("genericui_route", it) }
        try {
            ctx.startActivity(launchIntent)
        } catch (t: Throwable) {
            throw ServiceWorkerFragmentLaunchUnsupportedException(
                fragmentId = request.fragmentId,
                backendName = BackendTarget.Android.displayName,
                message = "Android blocked or failed starting Activity for fragment '${request.fragmentId}': ${t.message}",
                cause = t
            )
        }
    }

    fun requestCloseFragment(fragmentId: String, instanceId: String? = null) {
        if (serviceBound && clientEnvelopeListener != null) {
            val env = ServiceWorkerEnvelope(
                kind = ServiceWorkerEnvelope.KIND_MESSAGE,
                typeId = ServiceWorkerEnvelope.CONTROL_CLOSE_FRAGMENT,
                payloadJson = ServiceWorkerFragmentLaunchRequest.encodeCloseControlPayload(fragmentId, instanceId),
                targetFragmentId = fragmentId
            )
            workerControlScope.launch {
                if (!sendToClient(ANDROID_CLIENT_ID, env)) {
                    throw ServiceWorkerIpcException(
                        "Failed to send close control envelope for fragment '$fragmentId' to Android UI client."
                    )
                }
            }
            return
        }
        AndroidActivityRegistry.resolveActiveActivity()?.finish()
    }

    fun stopSelf() {
        val service = serviceInstance
        workerInstance?.performStop()
        workerInstance = null
        if (service != null) {
            ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
            service.stopSelf()
            serviceInstance = null
        }
        serviceBound = false
        clientEnvelopeListener = null
        clientConnectionListener?.invoke(false)
    }

    fun exitApplication(exitCode: Int) {
        stopSelf()
        AndroidActivityRegistry.resolveActiveActivity()?.finishAffinity()
    }

    fun isAutostartEnabled(): Boolean {
        val ctx = serviceInstance ?: AndroidExtensionRuntimeBridge.resolveApplicationContext()
            ?: return currentOptions.autostart
        return isAutostartEnabledForContext(ctx)
    }

    fun setAutostartEnabled(enabled: Boolean): Boolean {
        val ctx = serviceInstance ?: AndroidExtensionRuntimeBridge.resolveApplicationContext()
            ?: throw ServiceWorkerAutostartUnsupportedException(
                backendName = BackendTarget.Android.displayName,
                message = "No Android Context is available to update autostart preference."
            )
        ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_KEY_AUTOSTART, enabled)
            .apply()
        val bootClass = resolveBootReceiverClass(ctx)
        if (bootClass != null) {
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            try {
                ctx.packageManager.setComponentEnabledSetting(
                    ComponentName(ctx, bootClass),
                    state,
                    PackageManager.DONT_KILL_APP
                )
            } catch (t: Throwable) {
                throw ServiceWorkerAutostartUnsupportedException(
                    backendName = BackendTarget.Android.displayName,
                    message = "Failed to update Android BootReceiver state: ${t.message}",
                    cause = t
                )
            }
        }
        return true
    }

    internal suspend fun sendToClient(clientId: String, envelope: ServiceWorkerEnvelope): Boolean {
        val listener = clientEnvelopeListener ?: return false
        listener(envelope)
        return true
    }

    internal suspend fun broadcastEnvelope(envelope: ServiceWorkerEnvelope): Int {
        val listener = clientEnvelopeListener ?: return 0
        listener(envelope)
        return 1
    }

    fun disconnectClient(clientId: String) {
        disconnectInProcessClient()
    }

    fun resetForTests() {
        workerInstance?.performStop()
        workerInstance = null
        serviceInstance = null
        registeredServiceClass = null
        serviceBound = false
        lastNotification = null
        clientEnvelopeListener = null
        clientConnectionListener = null
        currentOptions = ServiceWorkerOptions()
    }

    private fun isAutostartEnabledForContext(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        if (prefs.contains(PREF_KEY_AUTOSTART)) {
            return prefs.getBoolean(PREF_KEY_AUTOSTART, currentOptions.autostart)
        }
        return currentOptions.autostart
    }

    private fun buildNotification(
        context: Context,
        serviceClass: Class<out Service>,
        title: String,
        text: String,
        subText: String?,
        icon: ResourceID?,
        actions: List<ServiceWorkerNotificationAction>
    ): Notification {
        val smallIconRes = AndroidExtensionRuntimeBridge.resolveSmallIconResource(
            context,
            icon?.toResourceUri() ?: currentOptions.trayIcon
        )
        val specs = actions.mapIndexed { index, action ->
            val btnIntent = Intent(context, serviceClass).apply {
                this.action = if (action.triggersDefaultStop && action.id == ServiceWorkerNotificationAction.DEFAULT_STOP_ACTION_ID) {
                    ACTION_STOP_SERVICE
                } else {
                    ACTION_NOTIFICATION_BUTTON
                }
                putExtra(EXTRA_ACTION_ID, action.id)
            }
            val btnIcon = if (action.icon != null) {
                AndroidExtensionRuntimeBridge.resolveSmallIconResource(context, action.icon.toResourceUri())
            } else {
                0
            }
            AndroidExtensionRuntimeBridge.NotificationActionSpec(
                title = action.label,
                serviceIntent = btnIntent,
                requestCode = 1000 + index,
                iconResId = btnIcon
            )
        }
        return AndroidExtensionRuntimeBridge.buildForegroundNotification(
            context = context,
            channelId = resolveChannelId(context),
            title = title,
            text = text,
            subText = subText,
            smallIconResId = smallIconRes,
            actions = specs
        )
    }

    private fun resolveForegroundServiceTypeMask(rawTypes: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val tokens = rawTypes.split('|', ',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        }
        var mask = 0
        for (token in tokens) {
            mask = mask or when (token) {
                "dataSync" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                "mediaPlayback" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                "phoneCall" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                "location" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                "connectedDevice" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                "mediaProjection" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                "camera" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
                "microphone" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
                "health" -> if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
                "remoteMessaging" -> if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
                "systemExempted" -> if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
                "shortService" -> if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0
                "specialUse" -> if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
                else -> 0
            }
        }
        return mask
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveRegisteredServiceClass(context: Context): Class<out Service>? {
        registeredServiceClass?.let { return it }
        val candidates = listOf(
            "${currentOptions.appId}.GeneratedAndroidServiceWorkerService",
            "${context.packageName}.GeneratedAndroidServiceWorkerService"
        ).distinct()
        for (fqn in candidates) {
            val loaded = try {
                Class.forName(fqn)
            } catch (_: ClassNotFoundException) {
                null
            }
            if (loaded != null && Service::class.java.isAssignableFrom(loaded)) {
                val cls = loaded as Class<out Service>
                registeredServiceClass = cls
                return cls
            }
        }
        return null
    }

    private fun resolveBootReceiverClass(context: Context): Class<*>? {
        val candidates = listOf(
            "${currentOptions.appId}.GeneratedAndroidServiceWorkerBootReceiver",
            "${context.packageName}.GeneratedAndroidServiceWorkerBootReceiver"
        ).distinct()
        for (fqn in candidates) {
            val loaded = try {
                Class.forName(fqn)
            } catch (_: ClassNotFoundException) {
                null
            }
            if (loaded != null) return loaded
        }
        return null
    }
}
