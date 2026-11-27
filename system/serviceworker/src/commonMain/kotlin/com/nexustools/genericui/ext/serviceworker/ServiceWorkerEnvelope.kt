package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.system.PreferenceElement

/**
 * Internal BinaryJson-backed IPC message envelope transported across desktop IPC (`IpcPacket`),
 * Android Service Binder, Web Worker `postMessage`, and test harnesses.
 */
internal data class ServiceWorkerEnvelope(
    val kind: Byte,
    val correlationId: Long = 0L,
    val clientId: String = "",
    val typeId: String = "",
    val payload: PreferenceElement = PreferenceElement.Null,
    val error: String = "",
    val targetFragmentId: String? = null,
    val sourceFragmentId: String? = null
) {
    fun toPreferenceElement(): PreferenceElement {
        val fields = linkedMapOf<String, PreferenceElement>(
            FIELD_KIND to PreferenceElement.ofByte(kind),
            FIELD_CORRELATION_ID to PreferenceElement.ofLong(correlationId),
            FIELD_CLIENT_ID to PreferenceElement.ofString(clientId),
            FIELD_TYPE_ID to PreferenceElement.ofString(typeId),
            FIELD_PAYLOAD to payload,
            FIELD_ERROR to PreferenceElement.ofString(error)
        )
        if (targetFragmentId != null) {
            fields[FIELD_TARGET_FRAGMENT_ID] = PreferenceElement.ofString(targetFragmentId)
        }
        if (sourceFragmentId != null) {
            fields[FIELD_SOURCE_FRAGMENT_ID] = PreferenceElement.ofString(sourceFragmentId)
        }
        return PreferenceElement.ofObject(fields)
    }

    companion object {
        const val KIND_AUTH: Byte = 1
        const val KIND_AUTH_OK: Byte = 2
        const val KIND_AUTH_ERR: Byte = 3
        const val KIND_MESSAGE: Byte = 4
        const val KIND_REQUEST: Byte = 5
        const val KIND_RESPONSE: Byte = 6
        const val KIND_RESPONSE_ERR: Byte = 7
        const val KIND_PING: Byte = 8
        const val KIND_BYE: Byte = 9

        const val CONTROL_OPEN_MAIN_WINDOW: String = "__genericui.sw.control.openMainWindow"
        const val CONTROL_CLOSE_MAIN_WINDOW: String = "__genericui.sw.control.closeMainWindow"
        const val CONTROL_EXIT_APP: String = "__genericui.sw.control.exitApp"
        const val CONTROL_REQUEST_ACTIVATE_MAIN_WINDOW: String = "__genericui.sw.control.requestActivateMainWindow"
        const val CONTROL_REQUEST_LAUNCH_FRAGMENT: String = "__genericui.sw.control.requestLaunchFragment"
        const val CONTROL_LAUNCH_FRAGMENT: String = "__genericui.sw.control.launchFragment"
        const val CONTROL_CLOSE_FRAGMENT: String = "__genericui.sw.control.closeFragment"
        const val CONTROL_FRAGMENT_MOUNTED: String = "__genericui.sw.control.fragmentMounted"
        const val CONTROL_FRAGMENT_UNMOUNTED: String = "__genericui.sw.control.fragmentUnmounted"
        const val CONTROL_QUERY_SHOULD_OPEN_MAIN_WINDOW: String = "__genericui.sw.control.queryShouldOpenMainWindow"

        private const val FIELD_KIND: String = "kind"
        private const val FIELD_CORRELATION_ID: String = "correlationId"
        private const val FIELD_CLIENT_ID: String = "clientId"
        private const val FIELD_TYPE_ID: String = "typeId"
        private const val FIELD_PAYLOAD: String = "payload"
        private const val FIELD_ERROR: String = "error"
        private const val FIELD_TARGET_FRAGMENT_ID: String = "targetFragmentId"
        private const val FIELD_SOURCE_FRAGMENT_ID: String = "sourceFragmentId"

        fun fromPreferenceElement(element: PreferenceElement): ServiceWorkerEnvelope? {
            if (!element.isObject && !element.isMap) return null
            val kindElem = element[FIELD_KIND] ?: return null
            if (!kindElem.isNumber) return null
            val kind = kindElem.asByte()
            val corrId = element[FIELD_CORRELATION_ID]?.asLong(0L) ?: 0L
            val clientId = element[FIELD_CLIENT_ID]?.asString("") ?: ""
            val typeId = element[FIELD_TYPE_ID]?.asString("") ?: ""
            val payload = element[FIELD_PAYLOAD] ?: PreferenceElement.Null
            val error = element[FIELD_ERROR]?.asString("") ?: ""
            val targetFragmentId = element[FIELD_TARGET_FRAGMENT_ID]?.let { if (it.isNull) null else it.asString() }
            val sourceFragmentId = element[FIELD_SOURCE_FRAGMENT_ID]?.let { if (it.isNull) null else it.asString() }
            return ServiceWorkerEnvelope(
                kind = kind,
                correlationId = corrId,
                clientId = clientId,
                typeId = typeId,
                payload = payload,
                error = error,
                targetFragmentId = targetFragmentId,
                sourceFragmentId = sourceFragmentId
            )
        }
    }
}
