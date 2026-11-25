package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceObjectCodec

/**
 * Authenticated native IPC discovery payload serialized atomically into
 * `<runtimeDir>/serviceworker-endpoint.json`.
 */
internal data class DesktopEndpointDescriptor(
    val transport: String,
    val address: String,
    val token: String,
    val pid: Long,
    val appId: String
) {
    fun toJson(): String {
        val obj = linkedMapOf(
            "transport" to PreferenceElement.ofString(transport),
            "address" to PreferenceElement.ofString(address),
            "token" to PreferenceElement.ofString(token),
            "pid" to PreferenceElement.ofLong(pid),
            "appId" to PreferenceElement.ofString(appId)
        )
        return PreferenceObjectCodec.elementToJson(PreferenceElement.ofObject(obj))
    }

    companion object {
        const val TRANSPORT_MACOS_XPC_MACH: String = "xpc_mach"
        const val TRANSPORT_UNIX_SOCKET: String = "unix"
        const val TRANSPORT_WINDOWS_NAMED_PIPE: String = "named_pipe"

        fun parse(jsonText: String?): DesktopEndpointDescriptor? {
            if (jsonText.isNullOrBlank()) return null
            val elem = try {
                PreferenceObjectCodec.fromJsonElement(jsonText)
            } catch (_: IllegalArgumentException) {
                return null
            }
            if (!elem.isObject && !elem.isMap) return null
            val transport = elem["transport"]?.asString("") ?: ""
            val address = elem["address"]?.asString("") ?: ""
            val token = elem["token"]?.asString("") ?: ""
            val pid = elem["pid"]?.asLong(0L) ?: 0L
            val appId = elem["appId"]?.asString("") ?: ""
            if (transport.isBlank() || address.isBlank() || token.isBlank()) return null
            return DesktopEndpointDescriptor(
                transport = transport,
                address = address,
                token = token,
                pid = pid,
                appId = appId
            )
        }
    }
}
