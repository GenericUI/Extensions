package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.resources.ResourceID

/**
 * Action button descriptor for ongoing Android foreground service notifications managed by a
 * [ServiceWorker].
 */
data class ServiceWorkerNotificationAction(
    val id: String,
    val label: String,
    val icon: ResourceID? = null,
    val triggersDefaultStop: Boolean = false,
    val onClick: () -> Unit = {}
) {
    companion object {
        const val DEFAULT_STOP_ACTION_ID: String = "genericui.serviceworker.action.STOP"

        /**
         * Creates the default ongoing foreground notification `"Stop"` button.
         */
        fun createDefaultStopAction(
            label: String = "Stop",
            icon: ResourceID? = null,
            onClick: () -> Unit = {}
        ): ServiceWorkerNotificationAction = ServiceWorkerNotificationAction(
            id = DEFAULT_STOP_ACTION_ID,
            label = label,
            icon = icon,
            triggersDefaultStop = true,
            onClick = onClick
        )
    }
}
