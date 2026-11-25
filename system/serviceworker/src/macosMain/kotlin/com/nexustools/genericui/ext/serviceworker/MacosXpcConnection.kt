@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.serviceworker

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSCondition
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.darwin._xpc_type_dictionary
import platform.darwin._xpc_type_error
import platform.darwin.xpc_connection_cancel
import platform.darwin.xpc_connection_send_message
import platform.darwin.xpc_connection_set_event_handler
import platform.darwin.xpc_connection_t
import platform.darwin.xpc_dictionary_create_empty
import platform.darwin.xpc_dictionary_create_reply
import platform.darwin.xpc_dictionary_get_data
import platform.darwin.xpc_dictionary_get_string
import platform.darwin.xpc_dictionary_set_bool
import platform.darwin.xpc_dictionary_set_data
import platform.darwin.xpc_get_type
import platform.darwin.xpc_object_t
import platform.posix.memcpy
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * macOS libxpc-backed [DesktopIpcConnection] wrapping an `xpc_connection_t` with an
 * `NSCondition`-synchronized inbound byte queue.
 *
 * `xpc_object_t` / `xpc_connection_t` are mapped to ARC-managed `NSObject?` in Kotlin/Native,
 * so no manual `xpc_retain` or `xpc_release` calls are performed.
 */
internal class MacosXpcConnection(
    private val conn: xpc_connection_t,
    private val probeToken: String? = null,
    private val onFirstRealMessage: ((MacosXpcConnection) -> Unit)? = null
) : DesktopIpcConnection {

    private val condition = NSCondition()
    private val inbox = ArrayDeque<ByteArray>()
    private val closedFlag = AtomicBoolean(conn == null)
    private val handlerAttached = AtomicBoolean(false)
    private val announced = AtomicBoolean(false)

    override val isClosed: Boolean
        get() = closedFlag.load() || conn == null

    /**
     * Attaches the XPC event handler. Must be called before `xpc_connection_activate(conn)`.
     */
    fun attachHandler() {
        if (!handlerAttached.compareAndSet(expectedValue = false, newValue = true)) {
            return
        }
        val connection = conn ?: return
        xpc_connection_set_event_handler(connection) { event ->
            handleEvent(event)
        }
    }

    override fun sendBody(bodyBytes: ByteArray): Boolean {
        if (isClosed) return false
        if (bodyBytes.size > ServiceWorkerFrameCodec.MAX_FRAME_BYTES) return false
        val connection = conn ?: return false
        val dict = xpc_dictionary_create_empty() ?: return false
        if (bodyBytes.isEmpty()) {
            memScoped {
                val empty = alloc<ByteVar>()
                xpc_dictionary_set_data(dict, KEY_BODY, empty.ptr, 0uL)
            }
        } else {
            bodyBytes.usePinned { pinned ->
                xpc_dictionary_set_data(
                    dict,
                    KEY_BODY,
                    pinned.addressOf(0),
                    bodyBytes.size.convert()
                )
            }
        }
        xpc_connection_send_message(connection, dict)
        return true
    }

    override fun receiveBody(timeoutMs: Int): ByteArray? {
        condition.lock()
        try {
            if (inbox.isNotEmpty()) {
                return inbox.removeFirst()
            }
            if (closedFlag.load() || conn == null || timeoutMs <= 0) {
                return null
            }
            val deadline = NSDate.dateWithTimeIntervalSinceNow(timeoutMs.toDouble() / 1000.0)
            while (inbox.isEmpty() && !closedFlag.load()) {
                if (!condition.waitUntilDate(deadline)) {
                    break
                }
            }
            return if (inbox.isNotEmpty()) inbox.removeFirst() else null
        } finally {
            condition.unlock()
        }
    }

    override fun close() {
        val shouldCancel: Boolean
        condition.lock()
        try {
            shouldCancel = closedFlag.compareAndSet(expectedValue = false, newValue = true)
            condition.broadcast()
        } finally {
            condition.unlock()
        }
        if (shouldCancel) {
            conn?.let { xpc_connection_cancel(it) }
        }
    }

    private fun handleEvent(event: xpc_object_t) {
        if (event == null) return
        when (xpc_get_type(event)) {
            _xpc_type_dictionary.ptr -> {
                if (probeToken != null &&
                    xpc_dictionary_get_string(event, KEY_PROBE_TOKEN)?.toKString() == probeToken
                ) {
                    val reply = xpc_dictionary_create_reply(event)
                    if (reply != null) {
                        xpc_dictionary_set_bool(reply, KEY_PROBE_OK, true)
                        conn?.let { xpc_connection_send_message(it, reply) }
                    }
                    return
                }
                val body = extractBody(event) ?: return
                condition.lock()
                try {
                    if (!closedFlag.load()) {
                        inbox.addLast(body)
                        condition.signal()
                    }
                } finally {
                    condition.unlock()
                }
                if (onFirstRealMessage != null &&
                    announced.compareAndSet(expectedValue = false, newValue = true)
                ) {
                    onFirstRealMessage.invoke(this)
                }
            }

            _xpc_type_error.ptr -> {
                close()
            }
        }
    }

    private fun extractBody(dict: xpc_object_t): ByteArray? = memScoped {
        val lenVar = alloc<ULongVar>()
        lenVar.value = 0uL
        val rawPtr = xpc_dictionary_get_data(dict, KEY_BODY, lenVar.ptr) ?: return@memScoped null
        val sizeULong = lenVar.value
        if (sizeULong > ServiceWorkerFrameCodec.MAX_FRAME_BYTES.toULong()) {
            return@memScoped null
        }
        val size = sizeULong.toInt()
        if (size == 0) {
            return@memScoped ByteArray(0)
        }
        val out = ByteArray(size)
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), rawPtr, sizeULong)
        }
        out
    }

    internal companion object {
        const val KEY_BODY: String = "body"
        const val KEY_PROBE_TOKEN: String = "probe_token"
        const val KEY_PROBE_OK: String = "probe_ok"
    }
}
