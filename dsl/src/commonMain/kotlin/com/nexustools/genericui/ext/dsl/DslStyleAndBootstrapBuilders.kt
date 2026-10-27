package com.nexustools.genericui.ext.dsl

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.css.ComponentStyle

/**
 * Configures this component's inline W3C CSS [ComponentStyle] (`getStyle()`) inside the `:extensions:dsl` builder.
 *
 * Example:
 * ```kotlin
 * button("Save") {
 *     bootstrap {
 *         type = BootstrapType.PRIMARY
 *         width = BootstrapWidth.W_100
 *     }
 *     style {
 *         cursor = CssCursor.POINTER
 *         important {
 *             borderRadius = CssCornerRadii.all(8f)
 *         }
 *     }
 * }
 * ```
 */
inline fun <T : AbstractComponent> T.style(
    block: (@GenericUiDslMarker ComponentStyle).() -> Unit
): T {
    getStyle().block()
    return this
}

/**
 * Configures this component's Bootstrap 5 utility properties via [ComponentBootstrap] (`getBootstrap()`)
 * inside the `:extensions:dsl` builder.
 *
 * All Bootstrap enum utilities configured here are mapped to invisible enum classes on `:web`
 * (`WebDomElement.enumClassList`) and executed natively on all non-web backends, while remaining
 * completely invisible to `getClasses()` and `hasClass()`.
 */
inline fun <T : AbstractComponent> T.bootstrap(
    block: (@GenericUiDslMarker ComponentBootstrap).() -> Unit
): T {
    getBootstrap().block()
    return this
}

/**
 * DSL scope helper inside `style { ... }` that marks every property assigned within [block] as `!important`.
 */
inline fun ComponentStyle.important(
    block: (@GenericUiDslMarker ComponentStyle).() -> Unit
) {
    val previous = isDefaultImportant()
    setDefaultImportant(true)
    try {
        this.block()
    } finally {
        setDefaultImportant(previous)
    }
}
