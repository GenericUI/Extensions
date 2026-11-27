package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.ext.ipc.AndroidServiceIpcBinder
import com.nexustools.genericui.ext.ipc.IpcConfig
import com.nexustools.genericui.ext.ipc.IpcPacket

/**
 * Android Binder linking UI Activities/clients to the [AndroidServiceWorkerController] service host
 * on top of `:extensions:system:ipc`'s [AndroidServiceIpcBinder] BinaryJson chunked Binder transport.
 */
class AndroidServiceWorkerBinder internal constructor(
    internal val controller: AndroidServiceWorkerController,
    config: IpcConfig = IpcConfig(),
    onPacketReceived: ((IpcPacket) -> IpcPacket?)? = null
) : AndroidServiceIpcBinder(
    config = config,
    onPacketReceived = onPacketReceived
)
