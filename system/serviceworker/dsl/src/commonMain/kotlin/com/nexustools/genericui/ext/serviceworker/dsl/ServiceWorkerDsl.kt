@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package com.nexustools.genericui.ext.serviceworker.dsl

import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.shell.MenuItem
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.ext.serviceworker.ServiceWorker
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerChannel
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerClient
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerNotificationAction
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerReply
import com.nexustools.genericui.resources.ResourceID

/**
 * Classless kotlinx.html-style DSL builders for `:extensions:system:serviceworker:dsl`.
 */
inline fun <reified T : Any> ServiceWorker.onMessage(
    noinline handler: suspend (client: ServiceWorkerClient, message: T) -> Unit
): Disposable = onMessage(T::class, handler)

inline fun <reified T : Any> ServiceWorker.onRequest(
    noinline handler: suspend (client: ServiceWorkerClient, request: T, reply: ServiceWorkerReply) -> Unit
): Disposable = onRequest(T::class, handler)

inline fun <reified T : Any> ServiceWorkerChannel.onMessage(
    noinline handler: suspend (message: T) -> Unit
): Disposable = onMessage(T::class, handler)

inline fun <reified T : Any> ServiceWorkerChannel.onRequest(
    noinline handler: suspend (request: T, reply: ServiceWorkerReply) -> Unit
): Disposable = onRequest(T::class, handler)

/**
 * Builds and attaches a desktop tray [ContextMenu] onto this [ServiceWorker] in-place.
 */
fun ServiceWorker.trayMenu(build: ContextMenu.() -> Unit): ContextMenu {
    val menu = ContextMenu().apply(build)
    setTrayMenu(menu)
    return menu
}

/**
 * Adds a menu item inside a [ServiceWorker.trayMenu] DSL block.
 */
fun ContextMenu.item(
    label: String,
    icon: ResourceID? = null,
    onClick: () -> Unit = {}
): MenuItem = addItem(label = label, icon = icon?.toResourceUri(), onClick = onClick)

/**
 * Adds a separator line inside a [ServiceWorker.trayMenu] DSL block.
 */
fun ContextMenu.separator(): ContextMenu = addSeparator()

/**
 * Declares or extends ongoing foreground notification action buttons on this [ServiceWorker].
 */
fun ServiceWorker.notificationActions(
    replaceDefaultStop: Boolean = false,
    build: MutableList<ServiceWorkerNotificationAction>.() -> Unit
): List<ServiceWorkerNotificationAction> {
    val built = mutableListOf<ServiceWorkerNotificationAction>().apply(build)
    if (replaceDefaultStop) {
        replaceNotificationActions(built)
    } else {
        extendNotificationActions(built)
    }
    return getEffectiveNotificationActions()
}

/**
 * Adds a custom action button inside a [ServiceWorker.notificationActions] block.
 */
fun MutableList<ServiceWorkerNotificationAction>.action(
    id: String,
    label: String,
    icon: ResourceID? = null,
    triggersDefaultStop: Boolean = false,
    onClick: () -> Unit = {}
): ServiceWorkerNotificationAction {
    val entry = ServiceWorkerNotificationAction(
        id = id,
        label = label,
        icon = icon,
        triggersDefaultStop = triggersDefaultStop,
        onClick = onClick
    )
    add(entry)
    return entry
}

/**
 * Adds the default `"Stop"` action button inside a [ServiceWorker.notificationActions] block.
 */
fun MutableList<ServiceWorkerNotificationAction>.stopAction(
    label: String = "Stop",
    icon: ResourceID? = null,
    onClick: () -> Unit = {}
): ServiceWorkerNotificationAction {
    val entry = ServiceWorkerNotificationAction.createDefaultStopAction(
        label = label,
        icon = icon,
        onClick = onClick
    )
    add(entry)
    return entry
}
