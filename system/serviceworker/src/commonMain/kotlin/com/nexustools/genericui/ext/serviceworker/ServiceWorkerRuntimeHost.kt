package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.resources.ResourceID

/**
 * Platform runtime host callback surface attached to an active [ServiceWorker] instance.
 */
internal interface ServiceWorkerRuntimeHost {
    fun updateNotification(
        title: String,
        text: String,
        subText: String?,
        icon: ResourceID?,
        actions: List<ServiceWorkerNotificationAction>
    )

    fun updateTray(
        iconPathOrUrl: String?,
        iconResource: ResourceID?,
        tooltip: String?,
        badgeText: String?,
        menu: ContextMenu?,
        onClick: (() -> Unit)?
    )

    fun openMainWindow(route: String?)

    fun closeMainWindow()

    fun stopSelf()

    fun exitApplication(exitCode: Int)

    fun isAutostartEnabled(): Boolean

    fun setAutostartEnabled(enabled: Boolean): Boolean

    suspend fun sendToClient(clientId: String, envelope: ServiceWorkerEnvelope): Boolean

    suspend fun broadcastEnvelope(envelope: ServiceWorkerEnvelope): Int

    fun disconnectClient(clientId: String)
}
