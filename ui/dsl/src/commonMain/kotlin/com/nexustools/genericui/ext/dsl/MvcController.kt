package com.nexustools.genericui.ext.dsl

/**
 * Base Controller class for Model-View-Controller (MVC) architectures in `GenericUI`.
 *
 * Coordinates user input events from [view], mutates [model] state via [updateModel],
 * and triggers [MvcView.refreshView] when needed.
 */
abstract class MvcController<M : Any, V : MvcView<M>>(
    val model: M,
    val view: V
) {
    var isAttached: Boolean = false
        private set

    /**
     * Attaches this controller to its [view] and performs initial view rendering.
     */
    open fun attach() {
        if (!isAttached) {
            isAttached = true
            onAttach()
            view.refreshView()
        }
    }

    /**
     * Detaches this controller from its [view].
     */
    open fun detach() {
        if (isAttached) {
            isAttached = false
            onDetach()
        }
    }

    /**
     * Mutates [model] in-place and notifies listeners (or refreshes [view] directly if [model] is not an [MvcModel]).
     */
    inline fun updateModel(mutator: M.() -> Unit) {
        model.mutator()
        val mvcModel = model as? MvcModel
        if (mvcModel != null) {
            mvcModel.notifyModelChanged()
        } else {
            view.refreshView()
        }
    }

    protected open fun onAttach() {}
    protected open fun onDetach() {}
}
