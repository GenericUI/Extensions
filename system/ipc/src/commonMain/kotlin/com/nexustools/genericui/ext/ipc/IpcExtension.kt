package com.nexustools.genericui.ext.ipc

import com.nexustools.genericui.component.system.DataClassCodecRegistry
import com.nexustools.genericui.core.GenericUiExtensionRegistry

/**
 * Capability discovery and codec registration coordinator for `:extensions:system:ipc`.
 */
object IpcExtension {
    val isSupported: Boolean
        get() = IpcPlatformExtensionInfo.isSupported

    val backendName: String
        get() = IpcPlatformExtensionInfo.backendName

    fun registerProviders() {
        DataClassCodecRegistry.register(IpcPacket::class, IpcPacket.CODEC)
        GenericUiExtensionRegistry.setExtensionProperty("ipc.supported", isSupported)
        GenericUiExtensionRegistry.setExtensionProperty("ipc.backend", backendName)
    }
}
