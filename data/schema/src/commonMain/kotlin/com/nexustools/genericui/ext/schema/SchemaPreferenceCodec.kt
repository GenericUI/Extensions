package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceObjectCodec
import com.nexustools.genericui.component.system.PreferenceStore

/**
 * Bridges [ObjectSchema] value maps directly with GenericUI's [PreferenceElement] AST and
 * [PreferenceStore] persistence.
 */
object SchemaPreferenceCodec {

    fun toPreferenceElement(schema: ObjectSchema, values: Map<String, Any?>): PreferenceElement {
        val coerced = SchemaValueCodec.coerceObjectValues(schema, values)
        val entries = LinkedHashMap<String, PreferenceElement>(coerced.size)
        for ((k, v) in coerced) {
            entries[k] = PreferenceObjectCodec.toElement(v)
        }
        return PreferenceElement.ofObject(entries)
    }

    fun fromPreferenceElement(schema: ObjectSchema, element: PreferenceElement?): LinkedHashMap<String, Any?> {
        if (element == null || element.isNull) {
            return schema.resolveDefaults()
        }
        val rawMap = LinkedHashMap<String, Any?>(element.mapEntries.size)
        for ((k, v) in element.mapEntries) {
            rawMap[k] = v.toPlainValue()
        }
        return SchemaValueCodec.coerceObjectValues(schema, rawMap)
    }

    fun saveTo(
        store: PreferenceStore,
        keyOrPrefix: String,
        schema: ObjectSchema,
        values: Map<String, Any?>,
        perFieldKeys: Boolean = false
    ) {
        val coerced = SchemaValueCodec.coerceObjectValues(schema, values)
        if (perFieldKeys) {
            for ((k, v) in coerced) {
                store.putElement("$keyOrPrefix.$k", PreferenceObjectCodec.toElement(v))
            }
        } else {
            store.putElement(keyOrPrefix, toPreferenceElement(schema, coerced))
        }
        store.flush()
    }

    fun loadFrom(
        store: PreferenceStore,
        keyOrPrefix: String,
        schema: ObjectSchema
    ): LinkedHashMap<String, Any?> {
        val singleElement = store.getElement(keyOrPrefix)
        if (singleElement != null && (singleElement.isObject || singleElement.isMap)) {
            return fromPreferenceElement(schema, singleElement)
        }
        val prefix = "$keyOrPrefix."
        val gathered = LinkedHashMap<String, Any?>()
        var foundPrefixed = false
        for (node in schema.nodes) {
            if (node is SchemaSeparatorSpec) continue
            val direct = store.getElement("$prefix${node.key}")
            if (direct != null) {
                gathered[node.key] = direct.toPlainValue()
                foundPrefixed = true
            } else if (node is SchemaFieldSpec) {
                for (legacy in node.legacyKeys) {
                    val legacyElem = store.getElement("$prefix$legacy") ?: store.getElement(legacy)
                    if (legacyElem != null) {
                        gathered[node.key] = legacyElem.toPlainValue()
                        foundPrefixed = true
                        break
                    }
                }
            }
        }
        return if (foundPrefixed) {
            SchemaValueCodec.coerceObjectValues(schema, gathered)
        } else {
            schema.resolveDefaults()
        }
    }
}
