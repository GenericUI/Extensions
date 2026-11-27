package com.nexustools.genericui.ext.serviceworker

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceObjectCodec
import kotlin.reflect.KClass

/**
 * Thread-safe registry mapping Kotlin message classes and wire `typeId`s to reflection-free
 * [ServiceWorkerMessageCodec]s, with built-in support for standard Kotlin primitives and collections.
 */
object ServiceWorkerCodecRegistry {

    private const val TYPE_UNIT: String = "kotlin.Unit"
    private const val TYPE_NULL: String = "kotlin.Nothing?"
    private const val TYPE_STRING: String = "kotlin.String"
    private const val TYPE_INT: String = "kotlin.Int"
    private const val TYPE_LONG: String = "kotlin.Long"
    private const val TYPE_FLOAT: String = "kotlin.Float"
    private const val TYPE_DOUBLE: String = "kotlin.Double"
    private const val TYPE_BOOLEAN: String = "kotlin.Boolean"
    private const val TYPE_PREFERENCE_ELEMENT: String = "com.nexustools.genericui.component.system.PreferenceElement"
    private const val TYPE_BYTE_ARRAY: String = "kotlin.ByteArray"
    private const val TYPE_LIST: String = "kotlin.collections.List"
    private const val TYPE_MAP: String = "kotlin.collections.Map"

    private val codecsByClass = linkedMapOf<KClass<*>, ServiceWorkerMessageCodec<*>>()
    private val codecsByTypeId = linkedMapOf<String, ServiceWorkerMessageCodec<*>>()

    fun <T : Any> register(codec: ServiceWorkerMessageCodec<T>) {
        codecsByClass[codec.messageClass] = codec
        codecsByTypeId[codec.typeId] = codec
    }

    fun unregister(typeId: String) {
        val removed = codecsByTypeId.remove(typeId)
        if (removed != null) {
            codecsByClass.remove(removed.messageClass)
        }
    }

    fun resolveTypeId(kClass: KClass<*>): String {
        codecsByClass[kClass]?.let { return it.typeId }
        return when (kClass) {
            Unit::class -> TYPE_UNIT
            String::class -> TYPE_STRING
            Int::class -> TYPE_INT
            Long::class -> TYPE_LONG
            Float::class -> TYPE_FLOAT
            Double::class -> TYPE_DOUBLE
            Boolean::class -> TYPE_BOOLEAN
            PreferenceElement::class -> TYPE_PREFERENCE_ELEMENT
            ByteArray::class -> TYPE_BYTE_ARRAY
            List::class -> TYPE_LIST
            Map::class -> TYPE_MAP
            else -> kClass.simpleName ?: "kotlin.Any"
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun encodePayload(value: Any?): Pair<String, PreferenceElement> {
        if (value == null) return TYPE_NULL to PreferenceElement.Null
        if (value === Unit) return TYPE_UNIT to PreferenceElement.Null

        val registered = codecsByClass[value::class] as? ServiceWorkerMessageCodec<Any>
        if (registered != null) {
            return registered.typeId to registered.encode(value)
        }

        return when (value) {
            is String -> TYPE_STRING to PreferenceElement.ofString(value)
            is Long -> TYPE_LONG to PreferenceElement.ofLong(value)
            is Number -> {
                val asDouble = value.toDouble()
                val asInt = asDouble.toInt()
                val isWholeInt = asDouble.isFinite() && asInt.toDouble() == asDouble
                when {
                    value is Int && isWholeInt -> TYPE_INT to PreferenceElement.ofInt(asInt)
                    value is Double -> TYPE_DOUBLE to PreferenceElement.ofDouble(asDouble)
                    value is Float -> TYPE_FLOAT to PreferenceElement.ofFloat(value)
                    else -> TYPE_DOUBLE to PreferenceElement.ofDouble(asDouble)
                }
            }
            is Boolean -> TYPE_BOOLEAN to PreferenceElement.ofBoolean(value)
            is PreferenceElement -> TYPE_PREFERENCE_ELEMENT to value
            is ByteArray -> TYPE_BYTE_ARRAY to PreferenceElement.ofBytes(value)
            is List<*> -> TYPE_LIST to PreferenceObjectCodec.toElement(value)
            is Map<*, *> -> TYPE_MAP to PreferenceObjectCodec.toElement(value)
            else -> throw IllegalArgumentException(
                "No ServiceWorkerMessageCodec registered for ${value::class.simpleName ?: "Unknown"}. " +
                    "Annotate the payload data class with @ServiceWorkerMessage or register a codec explicitly."
            )
        }
    }

    fun decodePayload(typeId: String, payload: PreferenceElement): Any? {
        if (typeId == TYPE_UNIT) return Unit
        if (typeId == TYPE_NULL) return null

        val codec = codecsByTypeId[typeId]
        if (codec != null) {
            return codec.decode(payload)
        }

        return when (typeId) {
            TYPE_STRING -> payload.asString("") ?: ""
            TYPE_INT -> payload.asInt(0)
            TYPE_LONG -> payload.asLong(0L)
            TYPE_FLOAT -> payload.asFloat(0f)
            TYPE_DOUBLE -> payload.asDouble(0.0)
            TYPE_BOOLEAN -> payload.asBoolean(false)
            TYPE_PREFERENCE_ELEMENT -> payload
            TYPE_BYTE_ARRAY -> payload.asBytes()
            TYPE_LIST -> payload.toPlainValue()
            TYPE_MAP -> payload.toPlainValue()
            else -> payload.toPlainValue()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> decodeAs(typeId: String, payload: PreferenceElement, expectedClass: KClass<T>): T {
        val raw = decodePayload(typeId, payload)
        if (expectedClass == Unit::class) {
            return Unit as T
        }
        val registered = codecsByClass[expectedClass] as? ServiceWorkerMessageCodec<T>
        if (registered != null) {
            if (registered.messageClass.isInstance(raw)) {
                return raw as T
            }
            return registered.decode(payload)
        }
        return when (expectedClass) {
            String::class -> ((raw as? String) ?: raw?.toString().orEmpty()) as T
            Int::class -> ((raw as? Number)?.toInt() ?: raw.toString().toInt()) as T
            Long::class -> ((raw as? Number)?.toLong() ?: raw.toString().toLong()) as T
            Float::class -> ((raw as? Number)?.toFloat() ?: raw.toString().toFloat()) as T
            Double::class -> ((raw as? Number)?.toDouble() ?: raw.toString().toDouble()) as T
            Boolean::class -> ((raw as? Boolean) ?: raw.toString().toBoolean()) as T
            ByteArray::class -> (raw as? ByteArray ?: payload.asBytes()) as T
            PreferenceElement::class -> (raw as? PreferenceElement ?: payload) as T
            else -> raw as? T ?: throw IllegalStateException(
                "Decoded ServiceWorker payload of wire type '$typeId' is incompatible with expected class ${expectedClass.simpleName ?: "Unknown"}."
            )
        }
    }

    fun isTypeRegistered(kClass: KClass<*>): Boolean =
        kClass in codecsByClass || kClass in setOf(
            Unit::class,
            String::class,
            Int::class,
            Long::class,
            Float::class,
            Double::class,
            Boolean::class,
            PreferenceElement::class,
            ByteArray::class,
            List::class,
            Map::class
        )

    fun clearCustomCodecs() {
        codecsByClass.clear()
        codecsByTypeId.clear()
    }
}
