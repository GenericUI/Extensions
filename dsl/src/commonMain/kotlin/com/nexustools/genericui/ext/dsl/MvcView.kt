package com.nexustools.genericui.ext.dsl

import com.nexustools.genericui.component.layout.Container

/**
 * Base View class for Model-View-Controller (MVC) architectures in `GenericUI`, powered by
 * `kotlinx.html`-style in-place DSL component builders (`div`, `row`, `col`, `h1`, `p`, `button`, `select`, `table`, etc.).
 *
 * Subclasses implement [render] using the in-place DSL functions directly on the receiver [Container].
 */
@GenericUiDslMarker
abstract class MvcView<M : Any>(
    var model: M,
    classPrefix: String = ""
) : Container("div", classPrefix) {

    private val modelListener: () -> Unit = {
        if (autoRefreshOnModelChange) {
            refreshView()
        }
    }

    /**
     * When `true` and [model] is an [MvcModel], calling [MvcModel.notifyModelChanged]
     * automatically invokes [refreshView].
     */
    var autoRefreshOnModelChange: Boolean = true

    init {
        (model as? MvcModel)?.addChangeListener(modelListener)
    }

    /**
     * Builds this view's component hierarchy in-place using `kotlinx.html`-style DSL functions
     * (`div { ... }`, `row { col { ... } }`, `h1("...")`, `button("...")`, etc.).
     */
    abstract fun Container.render()

    /**
     * Clears and rebuilds this view's component tree by invoking [render], then invalidates
     * the container's render cache.
     */
    open fun refreshView() {
        clearChildren()
        this.render()
        invalidateRenderCache()
    }

    /**
     * Updates the bound [newModel] instance and refreshes the view.
     */
    open fun bindModel(newModel: M) {
        (model as? MvcModel)?.removeChangeListener(modelListener)
        model = newModel
        (model as? MvcModel)?.addChangeListener(modelListener)
        refreshView()
    }

    override fun dispose() {
        (model as? MvcModel)?.removeChangeListener(modelListener)
        super.dispose()
    }
}
