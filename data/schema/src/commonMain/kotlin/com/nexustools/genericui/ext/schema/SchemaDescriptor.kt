package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceStore

/**
 * Compile-time generated (or hand-written) typed descriptor binding a Kotlin data class [T]
 * to its [ObjectSchema], value-map codecs, JSON codecs, and [PreferenceStore] persistence.
 */
interface SchemaDescriptor<T : Any> {
    val schema: ObjectSchema
    fun defaultInstance(): T
    fun toValueMap(instance: T): Map<String, Any?>
    fun fromValueMap(values: Map<String, Any?>): T
    fun toPreferenceElement(instance: T): PreferenceElement
    fun fromPreferenceElement(element: PreferenceElement): T
    fun toJson(instance: T): String
    fun fromJson(json: String): T
    fun saveTo(store: PreferenceStore, keyOrPrefix: String, instance: T)
    fun loadFrom(store: PreferenceStore, keyOrPrefix: String): T
}
