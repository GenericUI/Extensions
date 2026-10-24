package com.nexustools.genericui.ext.dsl

/**
 * Base Model class for Model-View-Controller (MVC) architectures in `GenericUI`.
 *
 * Allows [MvcController] and [MvcView] instances to subscribe to model state changes
 * and trigger view re-renders or partial component updates.
 */
open class MvcModel {
    private val changeListeners = mutableListOf<() -> Unit>()

    /**
     * Registers a listener invoked whenever [notifyModelChanged] is called.
     */
    fun addChangeListener(listener: () -> Unit) {
        changeListeners.add(listener)
    }

    /**
     * Removes a previously registered model change listener.
     */
    fun removeChangeListener(listener: () -> Unit) {
        changeListeners.remove(listener)
    }

    /**
     * Notifies all registered [MvcView] and [MvcController] listeners that this model's state has changed.
     */
    fun notifyModelChanged() {
        for (listener in changeListeners.toList()) {
            listener.invoke()
        }
    }
}
