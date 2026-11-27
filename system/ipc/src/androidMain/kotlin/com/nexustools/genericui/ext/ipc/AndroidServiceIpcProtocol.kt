package com.nexustools.genericui.ext.ipc

import android.os.IBinder

/**
 * Binder transaction codes, interface descriptors, and parcel chunk thresholds for Android
 * Service IPC helpers.
 */
object AndroidServiceIpcProtocol {
    const val INTERFACE_DESCRIPTOR: String = "com.nexustools.genericui.ext.ipc.IAndroidServiceIpc"
    const val CALLBACK_INTERFACE_DESCRIPTOR: String = "com.nexustools.genericui.ext.ipc.IAndroidServiceIpcCallback"

    const val TRANSACT_SEND_PACKET: Int = IBinder.FIRST_CALL_TRANSACTION + 1
    const val TRANSACT_CHUNK_BEGIN: Int = IBinder.FIRST_CALL_TRANSACTION + 2
    const val TRANSACT_CHUNK_PART: Int = IBinder.FIRST_CALL_TRANSACTION + 3
    const val TRANSACT_CHUNK_END: Int = IBinder.FIRST_CALL_TRANSACTION + 4
    const val TRANSACT_REGISTER_CALLBACK: Int = IBinder.FIRST_CALL_TRANSACTION + 5
    const val TRANSACT_UNREGISTER_CALLBACK: Int = IBinder.FIRST_CALL_TRANSACTION + 6
    const val TRANSACT_PING: Int = IBinder.FIRST_CALL_TRANSACTION + 7

    const val CALLBACK_TRANSACT_ON_PACKET: Int = IBinder.FIRST_CALL_TRANSACTION + 1
    const val CALLBACK_TRANSACT_CHUNK_BEGIN: Int = IBinder.FIRST_CALL_TRANSACTION + 2
    const val CALLBACK_TRANSACT_CHUNK_PART: Int = IBinder.FIRST_CALL_TRANSACTION + 3
    const val CALLBACK_TRANSACT_CHUNK_END: Int = IBinder.FIRST_CALL_TRANSACTION + 4

    const val MAX_DIRECT_PARCEL_BYTES: Int = 240 * 1024
    const val EXTRA_IPC_PACKET_BYTES: String = "com.nexustools.genericui.ext.ipc.PACKET_BYTES"
    internal const val DEFAULT_STALE_TRANSFER_TTL_MS: Long = 30_000L
}
