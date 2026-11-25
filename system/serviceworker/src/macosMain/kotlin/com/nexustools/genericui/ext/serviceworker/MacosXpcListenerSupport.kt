@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.ptr
import platform.Foundation.NSCondition
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.darwin.XPC_CONNECTION_MACH_SERVICE_LISTENER
import platform.darwin._xpc_type_connection
import platform.darwin._xpc_type_dictionary
import platform.darwin._xpc_type_error
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t
import platform.darwin.xpc_connection_activate
import platform.darwin.xpc_connection_cancel
import platform.darwin.xpc_connection_create
import platform.darwin.xpc_connection_create_from_endpoint
import platform.darwin.xpc_connection_create_mach_service
import platform.darwin.xpc_connection_get_euid
import platform.darwin.xpc_connection_send_message_with_reply
import platform.darwin.xpc_connection_set_event_handler
import platform.darwin.xpc_connection_set_target_queue
import platform.darwin.xpc_connection_t
import platform.darwin.xpc_dictionary_create_empty
import platform.darwin.xpc_dictionary_get_bool
import platform.darwin.xpc_dictionary_set_string
import platform.darwin.xpc_endpoint_create
import platform.darwin.xpc_endpoint_t
import platform.darwin.xpc_get_type
import platform.posix.geteuid
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Handle owning an active XPC listener (`xpc_connection_t`) that can be cancelled cleanly on shutdown.
 */
internal class XpcListenerHandle(
    private val listener: xpc_connection_t
) {
    private val closedFlag = AtomicBoolean(listener == null)

    fun close() {
        if (!closedFlag.compareAndSet(expectedValue = false, newValue = true)) {
            return
        }
        listener?.let { xpc_connection_cancel(it) }
    }
}

/**
 * Helpers for macOS XPC Mach service listeners/clients and anonymous in-process XPC endpoints.
 */
internal object MacosXpcListenerSupport {

    fun machServiceNameForAppId(appId: String): String {
        val clean = appId.trim().ifEmpty { "com.nexustools.genericui.app" }.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return "$clean.serviceworker"
    }

    fun isRunningWithDefaultRuntimeDir(appId: String, runtimeDir: String): Boolean =
        runtimeDir.trimEnd('/') == DesktopOsHelpers.runtimeDataDirectory(appId).trimEnd('/')

    fun tryStartMachServiceListener(
        machName: String,
        expectedUid: UInt = geteuid(),
        onClientConnected: (DesktopIpcConnection) -> Unit
    ): XpcListenerHandle? {
        val listenerQueue = dispatch_queue_create("com.nexustools.genericui.xpc.listener", null)
        val acceptQueue = dispatch_queue_create("com.nexustools.genericui.xpc.accept", null)
        val listener = xpc_connection_create_mach_service(
            machName,
            listenerQueue,
            XPC_CONNECTION_MACH_SERVICE_LISTENER.toULong()
        ) ?: return null

        val condition = NSCondition()
        val listenerError = AtomicBoolean(false)
        val probeSucceeded = AtomicBoolean(false)
        val probeFailed = AtomicBoolean(false)
        val probeToken = DesktopOsHelpers.generateRandomHexToken(16)

        xpc_connection_set_event_handler(listener) { event ->
            if (event == null) return@xpc_connection_set_event_handler
            when (xpc_get_type(event)) {
                _xpc_type_error.ptr -> {
                    listenerError.store(true)
                    signalCondition(condition)
                }

                _xpc_type_connection.ptr -> {
                    acceptPeerConnection(event, expectedUid, acceptQueue, probeToken, onClientConnected)
                }
            }
        }

        xpc_connection_activate(listener)

        // Actively probe whether launchd actually checked us in as the registered MachServices listener.
        val probeQueue = dispatch_queue_create("com.nexustools.genericui.xpc.probe", null)
        val probeConn = xpc_connection_create_mach_service(machName, probeQueue, 0uL)
        if (probeConn == null) {
            xpc_connection_cancel(listener)
            return null
        }
        xpc_connection_set_event_handler(probeConn) { event ->
            if (event != null && xpc_get_type(event) == _xpc_type_error.ptr) {
                probeFailed.store(true)
                signalCondition(condition)
            }
        }
        xpc_connection_activate(probeConn)

        val probeMsg = xpc_dictionary_create_empty()
        if (probeMsg == null) {
            xpc_connection_cancel(probeConn)
            xpc_connection_cancel(listener)
            return null
        }
        xpc_dictionary_set_string(probeMsg, MacosXpcConnection.KEY_PROBE_TOKEN, probeToken)
        xpc_connection_send_message_with_reply(probeConn, probeMsg, probeQueue) { reply ->
            if (reply != null &&
                xpc_get_type(reply) == _xpc_type_dictionary.ptr &&
                xpc_dictionary_get_bool(reply, MacosXpcConnection.KEY_PROBE_OK)
            ) {
                probeSucceeded.store(true)
            } else {
                probeFailed.store(true)
            }
            signalCondition(condition)
        }

        val deadline = NSDate.dateWithTimeIntervalSinceNow(0.150)
        condition.lock()
        try {
            while (!listenerError.load() && !probeSucceeded.load() && !probeFailed.load()) {
                if (!condition.waitUntilDate(deadline)) {
                    break
                }
            }
        } finally {
            condition.unlock()
        }
        xpc_connection_cancel(probeConn)

        if (!probeSucceeded.load() || listenerError.load()) {
            xpc_connection_cancel(listener)
            return null
        }
        return XpcListenerHandle(listener)
    }

    internal fun startAnonymousListener(
        expectedUid: UInt = geteuid(),
        onClientConnected: (DesktopIpcConnection) -> Unit
    ): Pair<XpcListenerHandle, xpc_endpoint_t>? {
        val listenerQueue = dispatch_queue_create("com.nexustools.genericui.xpc.anon.listener", null)
        val acceptQueue = dispatch_queue_create("com.nexustools.genericui.xpc.anon.accept", null)
        val listener = xpc_connection_create(null, listenerQueue) ?: return null

        xpc_connection_set_event_handler(listener) { event ->
            if (event == null) return@xpc_connection_set_event_handler
            if (xpc_get_type(event) == _xpc_type_connection.ptr) {
                acceptPeerConnection(event, expectedUid, acceptQueue, null, onClientConnected)
            }
        }

        xpc_connection_activate(listener)
        val endpoint = xpc_endpoint_create(listener) ?: run {
            xpc_connection_cancel(listener)
            return null
        }
        return XpcListenerHandle(listener) to endpoint
    }

    internal fun connectAnonymousEndpoint(endpoint: xpc_endpoint_t): DesktopIpcConnection? {
        if (endpoint == null) return null
        val conn = xpc_connection_create_from_endpoint(endpoint) ?: return null
        val peerQueue = dispatch_queue_create("com.nexustools.genericui.xpc.anon.client", null)
        xpc_connection_set_target_queue(conn, peerQueue)
        val wrapped = MacosXpcConnection(conn)
        wrapped.attachHandler()
        xpc_connection_activate(conn)
        return wrapped
    }

    fun connectMachService(machName: String): DesktopIpcConnection? {
        val peerQueue = dispatch_queue_create("com.nexustools.genericui.xpc.client", null)
        val conn = xpc_connection_create_mach_service(machName, peerQueue, 0uL) ?: return null
        val wrapped = MacosXpcConnection(conn)
        wrapped.attachHandler()
        xpc_connection_activate(conn)
        return wrapped
    }

    private fun acceptPeerConnection(
        event: xpc_connection_t,
        expectedUid: UInt,
        acceptQueue: dispatch_queue_t,
        probeToken: String?,
        onClientConnected: (DesktopIpcConnection) -> Unit
    ) {
        if (event == null) return
        if (xpc_connection_get_euid(event) != expectedUid) {
            xpc_connection_cancel(event)
            return
        }
        val peerQueue = dispatch_queue_create("com.nexustools.genericui.xpc.peer", null)
        xpc_connection_set_target_queue(event, peerQueue)
        val conn = if (probeToken != null) {
            MacosXpcConnection(
                conn = event,
                probeToken = probeToken,
                onFirstRealMessage = { readyConn ->
                    dispatch_async(acceptQueue) {
                        onClientConnected(readyConn)
                    }
                }
            )
        } else {
            MacosXpcConnection(event)
        }
        conn.attachHandler()
        xpc_connection_activate(event)
        if (probeToken == null) {
            dispatch_async(acceptQueue) {
                onClientConnected(conn)
            }
        }
    }

    private fun signalCondition(condition: NSCondition) {
        condition.lock()
        try {
            condition.broadcast()
        } finally {
            condition.unlock()
        }
    }
}
