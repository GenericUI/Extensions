package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceObjectCodec

/**
 * Authenticated loopback TCP discovery payload serialized atomically into
 * `<runtimeDir>/serviceworker-endpoint.json`.
 */
internal data class DesktopEndpointDescriptor(
    val port: Int,
    val token: String,
    val pid: Long,
    val appId: String
) {
    fun toJson(): String {
        val obj = linkedMapOf(
            "port" to PreferenceElement.ofInt(port),
            "token" to PreferenceElement.ofString(token),
            "pid" to PreferenceElement.ofLong(pid),
            "appId" to PreferenceElement.ofString(appId)
        )
        return PreferenceObjectCodec.elementToJson(PreferenceElement.ofObject(obj))
    }

    companion object {
        fun parse(jsonText: String?): DesktopEndpointDescriptor? {
            if (jsonText.isNullOrBlank()) return null
            val elem = runCatching { PreferenceObjectCodec.fromJsonElement(jsonText) }.getOrNull() ?: return null
            if (!elem.isObject && !elem.isMap) return null
            val port = elem["port"]?.asInt(0) ?: 0
            val token = elem["token"]?.asString("") ?: ""
            val pid = elem["pid"]?.asLong(0L) ?: 0L
            val appId = elem["appId"]?.asString("") ?: ""
            if (port <= 0 || token.isBlank()) return null
            return DesktopEndpointDescriptor(
                port = port,
                token = token,
                pid = pid,
                appId = appId
            )
        }
    }
}
