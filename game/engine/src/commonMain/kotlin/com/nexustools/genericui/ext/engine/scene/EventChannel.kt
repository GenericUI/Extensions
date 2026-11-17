package com.nexustools.genericui.ext.engine.scene

/**
 * Lightweight typed publish/subscribe event channel used across entities and subsystems.
 */
class EventChannel<T> {
    private val listeners = mutableListOf<(T) -> Unit>()

    val subscriberCount: Int
        get() = listeners.size

    fun subscribe(listener: (T) -> Unit): () -> Unit {
        if (listener !in listeners) {
            listeners.add(listener)
        }
        return { unsubscribe(listener) }
    }

    fun unsubscribe(listener: (T) -> Unit) {
        listeners.remove(listener)
    }

    fun publish(event: T) {
        if (listeners.isEmpty()) return
        for (listener in listeners.toList()) {
            listener(event)
        }
    }

    fun clear() {
        listeners.clear()
    }
}
