@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ext.ipc

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
import kotlin.concurrent.atomics.AtomicReference

/**
 * macOS libxpc-backed [IpcConnection] wrapping an `xpc_connection_t` with an
 * `NSCondition`-synchronized inbound byte queue.
 */
internal class MacosXpcConnection(
    private val conn: xpc_connection_t,
    private val maxPacketBytes: Int = IpcPacketFramer.MAX_PACKET_BYTES,
    private val probeToken: String? = null,
    private val onFirstRealMessage: ((MacosXpcConnection) -> Unit)? = null
) : IpcConnection {

    private val condition = NSCondition()
    private val inbox = ArrayDeque<ByteArray>()
    private val stateRef = AtomicReference(
        if (conn == null) IpcConnectionState.CLOSED else IpcConnectionState.OPEN
    )
    private val handlerAttached = AtomicBoolean(false)
    private val announced = AtomicBoolean(false)

    override val state: IpcConnectionState
        get() = stateRef.load()

    override val isClosed: Boolean
        get() {
            val s = stateRef.load()
            return s == IpcConnectionState.CLOSING || s == IpcConnectionState.CLOSED || conn == null
        }

    fun attachHandler() {
        if (!handlerAttached.compareAndSet(expectedValue = false, newValue = true)) {
            return
        }
        val connection = conn ?: return
        xpc_connection_set_event_handler(connection) { event ->
            handleEvent(event)
        }
    }

    override fun sendPacket(packet: IpcPacket): Boolean {
        if (isClosed) return false
        return sendBody(packet.toBinaryJsonBytes())
    }

    override fun receivePacket(timeoutMs: Int): IpcPacket? {
        val body = readBody(timeoutMs) ?: return null
        return IpcPacketFramer.decodePacket(body)
    }

    override fun sendBody(bodyBytes: ByteArray): Boolean {
        if (isClosed || bodyBytes.isEmpty() || bodyBytes.size > maxPacketBytes) return false
        val connection = conn ?: return false
        val dict = xpc_dictionary_create_empty() ?: return false
        bodyBytes.usePinned { pinned ->
            xpc_dictionary_set_data(
                dict,
                KEY_BODY,
                pinned.addressOf(0),
                bodyBytes.size.convert()
            )
        }
        xpc_connection_send_message(connection, dict)
        return true
    }

    override fun readBody(timeoutMs: Int): ByteArray? {
        condition.lock()
        try {
            if (inbox.isNotEmpty()) {
                return inbox.removeFirst()
            }
            if (isClosed || timeoutMs == 0) {
                return null
            }
            if (timeoutMs < 0) {
                while (inbox.isEmpty() && !isClosed) {
                    condition.wait()
                }
                return if (inbox.isNotEmpty()) inbox.removeFirst() else null
            }
            val deadline = NSDate.dateWithTimeIntervalSinceNow(timeoutMs.toDouble() / 1000.0)
            while (inbox.isEmpty() && !isClosed) {
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
        while (true) {
            val cur = stateRef.load()
            if (cur == IpcConnectionState.CLOSING || cur == IpcConnectionState.CLOSED) {
                return
            }
            if (stateRef.compareAndSet(cur, IpcConnectionState.CLOSING)) {
                break
            }
        }
        condition.lock()
        try {
            condition.broadcast()
        } finally {
            condition.unlock()
        }
        try {
            conn?.let { xpc_connection_cancel(it) }
        } finally {
            stateRef.store(IpcConnectionState.CLOSED)
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
                    if (!isClosed) {
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
        if (sizeULong == 0uL || sizeULong > maxPacketBytes.toULong()) {
            return@memScoped null
        }
        val size = sizeULong.toInt()
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
