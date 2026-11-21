package com.nexustools.genericui.ext.schema

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KClass

/**
 * Thread-safe KMP registry mapping schema IDs and model [KClass]es to [SchemaDescriptor]s,
 * plus optional globally registered [SchemaCustomFieldRenderer] instances.
 */
@OptIn(ExperimentalAtomicApi::class)
object SchemaRegistry {

    private data class Snapshot(
        val byId: Map<String, SchemaDescriptor<*>> = emptyMap(),
        val byClass: Map<KClass<*>, SchemaDescriptor<*>> = emptyMap(),
        val customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap()
    )

    private val state = AtomicReference(Snapshot())

    fun register(descriptor: SchemaDescriptor<*>, modelClass: KClass<*>? = null) {
        while (true) {
            val cur = state.load()
            val nextById = LinkedHashMap(cur.byId).apply {
                put(descriptor.schema.id, descriptor)
            }
            val nextByClass = LinkedHashMap(cur.byClass).apply {
                if (modelClass != null) {
                    put(modelClass, descriptor)
                }
            }
            if (state.compareAndSet(cur, cur.copy(byId = nextById, byClass = nextByClass))) {
                return
            }
        }
    }

    inline fun <reified T : Any> register(descriptor: SchemaDescriptor<T>) {
        register(descriptor, T::class)
    }

    fun unregister(id: String) {
        while (true) {
            val cur = state.load()
            val nextById = LinkedHashMap(cur.byId).apply { remove(id) }
            val nextByClass = LinkedHashMap(cur.byClass).apply {
                entries.removeAll { it.value.schema.id == id }
            }
            if (state.compareAndSet(cur, cur.copy(byId = nextById, byClass = nextByClass))) {
                return
            }
        }
    }

    fun unregister(modelClass: KClass<*>) {
        while (true) {
            val cur = state.load()
            val nextByClass = LinkedHashMap(cur.byClass).apply { remove(modelClass) }
            if (state.compareAndSet(cur, cur.copy(byClass = nextByClass))) {
                return
            }
        }
    }

    fun getById(id: String): SchemaDescriptor<*>? = state.load().byId[id]

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getForClass(modelClass: KClass<T>): SchemaDescriptor<T>? =
        state.load().byClass[modelClass] as? SchemaDescriptor<T>

    inline fun <reified T : Any> getForClass(): SchemaDescriptor<T>? =
        getForClass(T::class)

    fun allDescriptors(): List<SchemaDescriptor<*>> = state.load().byId.values.toList()

    fun registerCustomRenderer(renderer: SchemaCustomFieldRenderer) {
        while (true) {
            val cur = state.load()
            val next = LinkedHashMap(cur.customRenderers).apply {
                put(renderer.rendererId, renderer)
            }
            if (state.compareAndSet(cur, cur.copy(customRenderers = next))) {
                return
            }
        }
    }

    fun unregisterCustomRenderer(rendererId: String) {
        while (true) {
            val cur = state.load()
            val next = LinkedHashMap(cur.customRenderers).apply { remove(rendererId) }
            if (state.compareAndSet(cur, cur.copy(customRenderers = next))) {
                return
            }
        }
    }

    fun getCustomRenderer(rendererId: String): SchemaCustomFieldRenderer? =
        state.load().customRenderers[rendererId]

    fun clear() {
        state.store(Snapshot())
    }
}
