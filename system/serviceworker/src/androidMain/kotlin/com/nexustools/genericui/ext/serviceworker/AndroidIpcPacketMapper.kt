package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.ext.ipc.IpcPacket

/**
 * Bidirectional 1:1 mapper between internal [ServiceWorkerEnvelope] frames and
 * `:extensions:system:ipc` [IpcPacket] objects transported over Android Binder IPC.
 */
internal object AndroidIpcPacketMapper {
    private const val HEADER_KIND: String = "sw.kind"
    private const val HEADER_TARGET_FRAGMENT_ID: String = "sw.targetFragmentId"
    private const val HEADER_SOURCE_FRAGMENT_ID: String = "sw.sourceFragmentId"

    private const val TYPE_AUTH: String = "__sw.auth"
    private const val TYPE_AUTH_OK: String = "__sw.auth_ok"
    private const val TYPE_AUTH_ERR: String = "__sw.auth_err"
    private const val TYPE_MESSAGE: String = "__sw.message"
    private const val TYPE_REQUEST: String = "__sw.request"
    private const val TYPE_RESPONSE: String = "__sw.response"
    private const val TYPE_RESPONSE_ERR: String = "__sw.response_err"
    private const val TYPE_PING: String = "__sw.ping"
    private const val TYPE_BYE: String = "__sw.bye"

    fun toIpcPacket(envelope: ServiceWorkerEnvelope): IpcPacket {
        val headers = linkedMapOf(
            HEADER_KIND to envelope.kind.toInt().toString()
        )
        envelope.targetFragmentId?.let { headers[HEADER_TARGET_FRAGMENT_ID] = it }
        envelope.sourceFragmentId?.let { headers[HEADER_SOURCE_FRAGMENT_ID] = it }
        val packetType = envelope.typeId.ifEmpty { kindPacketType(envelope.kind) }
        val replyToId = if (
            envelope.kind == ServiceWorkerEnvelope.KIND_RESPONSE ||
            envelope.kind == ServiceWorkerEnvelope.KIND_RESPONSE_ERR
        ) {
            envelope.correlationId
        } else {
            null
        }
        return IpcPacket(
            id = envelope.correlationId,
            type = packetType,
            channel = envelope.clientId,
            payload = envelope.payload,
            replyToId = replyToId,
            error = envelope.error.ifEmpty { null },
            headers = headers
        )
    }

    fun fromIpcPacket(packet: IpcPacket): ServiceWorkerEnvelope? {
        val kindHeader = packet.headers[HEADER_KIND]
        val kind: Byte = if (kindHeader != null) {
            val parsed = kindHeader.toIntOrNull() ?: return null
            if (parsed < ServiceWorkerEnvelope.KIND_AUTH || parsed > ServiceWorkerEnvelope.KIND_BYE) {
                return null
            }
            parsed.toByte()
        } else {
            inferKind(packet)
        }
        val fallbackType = kindPacketType(kind)
        val typeId = if (packet.type == fallbackType) "" else packet.type
        val correlationId = packet.replyToId ?: packet.id
        return ServiceWorkerEnvelope(
            kind = kind,
            correlationId = correlationId,
            clientId = packet.channel,
            typeId = typeId,
            payload = packet.payload,
            error = packet.error.orEmpty(),
            targetFragmentId = packet.headers[HEADER_TARGET_FRAGMENT_ID],
            sourceFragmentId = packet.headers[HEADER_SOURCE_FRAGMENT_ID]
        )
    }

    private fun kindPacketType(kind: Byte): String = when (kind) {
        ServiceWorkerEnvelope.KIND_AUTH -> TYPE_AUTH
        ServiceWorkerEnvelope.KIND_AUTH_OK -> TYPE_AUTH_OK
        ServiceWorkerEnvelope.KIND_AUTH_ERR -> TYPE_AUTH_ERR
        ServiceWorkerEnvelope.KIND_MESSAGE -> TYPE_MESSAGE
        ServiceWorkerEnvelope.KIND_REQUEST -> TYPE_REQUEST
        ServiceWorkerEnvelope.KIND_RESPONSE -> TYPE_RESPONSE
        ServiceWorkerEnvelope.KIND_RESPONSE_ERR -> TYPE_RESPONSE_ERR
        ServiceWorkerEnvelope.KIND_PING -> TYPE_PING
        ServiceWorkerEnvelope.KIND_BYE -> TYPE_BYE
        else -> "__sw.kind.${kind.toInt()}"
    }

    private fun inferKind(packet: IpcPacket): Byte = when (packet.type) {
        TYPE_AUTH -> ServiceWorkerEnvelope.KIND_AUTH
        TYPE_AUTH_OK -> ServiceWorkerEnvelope.KIND_AUTH_OK
        TYPE_AUTH_ERR -> ServiceWorkerEnvelope.KIND_AUTH_ERR
        TYPE_MESSAGE -> ServiceWorkerEnvelope.KIND_MESSAGE
        TYPE_REQUEST -> ServiceWorkerEnvelope.KIND_REQUEST
        TYPE_RESPONSE -> ServiceWorkerEnvelope.KIND_RESPONSE
        TYPE_RESPONSE_ERR -> ServiceWorkerEnvelope.KIND_RESPONSE_ERR
        TYPE_PING -> ServiceWorkerEnvelope.KIND_PING
        TYPE_BYE -> ServiceWorkerEnvelope.KIND_BYE
        else -> when {
            packet.error != null -> ServiceWorkerEnvelope.KIND_RESPONSE_ERR
            packet.replyToId != null -> ServiceWorkerEnvelope.KIND_RESPONSE
            packet.id != 0L -> ServiceWorkerEnvelope.KIND_REQUEST
            else -> ServiceWorkerEnvelope.KIND_MESSAGE
        }
    }
}
