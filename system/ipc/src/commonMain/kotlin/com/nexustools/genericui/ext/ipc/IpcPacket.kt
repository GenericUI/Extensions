package com.nexustools.genericui.ext.ipc

import com.nexustools.genericui.component.system.BinarySerializable
import com.nexustools.genericui.component.system.DataClassCodec
import com.nexustools.genericui.component.system.DataClassCodecRegistry
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.util.BinaryJson

/**
 * Structured cross-process IPC packet encoded via [BinaryJson] (`TYPE_OBJECT`) and preserving
 * arbitrary [PreferenceElement] payloads (including zero-copy-style raw [PreferenceElement.ofBytes]).
 */
@BinarySerializable
data class IpcPacket(
    val id: Long = 0L,
    val type: String,
    val channel: String = "",
    val payload: PreferenceElement = PreferenceElement.Null,
    val replyToId: Long? = null,
    val error: String? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    fun toPreferenceElement(): PreferenceElement {
        val fields = LinkedHashMap<String, PreferenceElement>(7)
        fields[FIELD_ID] = PreferenceElement.ofLong(id)
        fields[FIELD_TYPE] = PreferenceElement.ofString(type)
        fields[FIELD_CHANNEL] = PreferenceElement.ofString(channel)
        fields[FIELD_PAYLOAD] = payload
        fields[FIELD_REPLY_TO_ID] = replyToId?.let { PreferenceElement.ofLong(it) } ?: PreferenceElement.Null
        fields[FIELD_ERROR] = error?.let { PreferenceElement.ofString(it) } ?: PreferenceElement.Null
        val headerElements = LinkedHashMap<String, PreferenceElement>(headers.size)
        for ((k, v) in headers) {
            headerElements[k] = PreferenceElement.ofString(v)
        }
        fields[FIELD_HEADERS] = PreferenceElement.ofMap(headerElements)
        return PreferenceElement.ofObject(fields)
    }

    fun toBinaryJsonBytes(): ByteArray =
        BinaryJson.encodeElement(toPreferenceElement())

    companion object {
        private const val FIELD_ID = "id"
        private const val FIELD_TYPE = "type"
        private const val FIELD_CHANNEL = "channel"
        private const val FIELD_PAYLOAD = "payload"
        private const val FIELD_REPLY_TO_ID = "replyToId"
        private const val FIELD_ERROR = "error"
        private const val FIELD_HEADERS = "headers"

        val CODEC: DataClassCodec<IpcPacket> = object : DataClassCodec<IpcPacket> {
            override fun encode(value: IpcPacket): PreferenceElement = value.toPreferenceElement()

            override fun decode(element: PreferenceElement): IpcPacket =
                fromPreferenceElement(element)
                    ?: throw IpcException("Invalid PreferenceElement for IpcPacket (kind=${element.kind}).")
        }

        init {
            DataClassCodecRegistry.register(IpcPacket::class, CODEC)
        }

        fun fromPreferenceElement(element: PreferenceElement): IpcPacket? {
            if (!element.isObject && !element.isMap) return null
            val typeElem = element[FIELD_TYPE] ?: return null
            if (!typeElem.isString) return null
            val type = typeElem.stringValue ?: return null

            val id = element[FIELD_ID]?.asLong(0L) ?: 0L
            val channel = element[FIELD_CHANNEL]?.asString("") ?: ""
            val payload = element[FIELD_PAYLOAD] ?: PreferenceElement.Null
            val replyToId = element[FIELD_REPLY_TO_ID]?.let { elem ->
                if (elem.isNull || !elem.isNumber) null else elem.asLong()
            }
            val error = element[FIELD_ERROR]?.let { elem ->
                if (elem.isNull) null else elem.asString()
            }
            val headersElem = element[FIELD_HEADERS]
            val headers = if (headersElem != null && (headersElem.isMap || headersElem.isObject)) {
                val decoded = LinkedHashMap<String, String>(headersElem.mapEntries.size)
                for ((k, v) in headersElem.mapEntries) {
                    val valueStr = v.asString()
                    if (valueStr != null) {
                        decoded[k] = valueStr
                    }
                }
                decoded
            } else {
                emptyMap()
            }
            return IpcPacket(
                id = id,
                type = type,
                channel = channel,
                payload = payload,
                replyToId = replyToId,
                error = error,
                headers = headers
            )
        }

        fun fromBinaryJsonBytes(bytes: ByteArray): IpcPacket? {
            val element = BinaryJson.decodeElementOrNull(bytes) ?: return null
            return fromPreferenceElement(element)
        }
    }
}
