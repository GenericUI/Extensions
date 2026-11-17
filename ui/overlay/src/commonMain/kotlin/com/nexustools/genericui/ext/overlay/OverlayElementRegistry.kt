package com.nexustools.genericui.ext.overlay

/**
 * Registry of reusable [OverlayElementFactory] definitions keyed by string type ID.
 */
class OverlayElementRegistry {

    private val factories = linkedMapOf<String, OverlayElementFactory>()

    fun register(elementTypeId: String, factory: OverlayElementFactory) {
        val key = elementTypeId.trim()
        require(key.isNotEmpty()) { "Overlay element type ID must not be blank." }
        factories[key] = factory
    }

    fun unregister(elementTypeId: String): Boolean =
        factories.remove(elementTypeId.trim()) != null

    operator fun get(elementTypeId: String): OverlayElementFactory? =
        factories[elementTypeId.trim()]

    operator fun contains(elementTypeId: String): Boolean =
        factories.containsKey(elementTypeId.trim())

    fun createOrNull(elementTypeId: String, context: OverlayContext): OverlayElement? =
        get(elementTypeId)?.create(context)

    fun create(elementTypeId: String, context: OverlayContext): OverlayElement =
        createOrNull(elementTypeId, context)
            ?: error("No OverlayElementFactory registered for '$elementTypeId'.")

    fun registeredTypeIds(): Set<String> =
        factories.keys.toSet()

    fun clear() {
        factories.clear()
    }
}
