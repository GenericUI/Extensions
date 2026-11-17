package com.nexustools.genericui.ext.threejs.core

/**
 * Three.js event bus base class (`THREE.EventDispatcher`).
 */
open class EventDispatcher {
    private val listeners = mutableMapOf<String, MutableList<(Map<String, Any?>) -> Unit>>()

    fun addEventListener(type: String, listener: (Map<String, Any?>) -> Unit) {
        val list = listeners.getOrPut(type) { mutableListOf() }
        if (listener !in list) {
            list.add(listener)
        }
    }

    fun hasEventListener(type: String, listener: (Map<String, Any?>) -> Unit): Boolean =
        listeners[type]?.contains(listener) == true

    fun removeEventListener(type: String, listener: (Map<String, Any?>) -> Unit) {
        listeners[type]?.remove(listener)
    }

    fun dispatchEvent(type: String, data: Map<String, Any?> = emptyMap()) {
        val snapshot = listeners[type]?.toList() ?: return
        val payload = data + ("type" to type) + ("target" to this)
        for (listener in snapshot) {
            listener(payload)
        }
    }
}
