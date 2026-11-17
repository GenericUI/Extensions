package com.nexustools.genericui.ext.alien.dsl

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.alien.AlienContainer
import com.nexustools.genericui.ext.alien.UnalienContainer
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker

/**
 * Declarative DSL builder for an [AlienContainer] inside any [AbstractContainer].
 *
 * On System targets, all `:common` children inside [block] are rendered via `*AlienImpl` onto
 * the container's `Canvas2D` with browser-identical Bootstrap 5.3 styling, full CSS theming,
 * and smooth CSS transitions/animations, while extension components and [unalienContainer]
 * blocks are layered below the `Canvas2D` with transparent hole cutting and mouse passthrough.
 * On the Web target, forwards directly to the parent DOM container.
 */
fun AbstractContainer.alienContainer(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker AlienContainer.() -> Unit)? = null
): AlienContainer {
    val container = AlienContainer()
    if (!classes.isNullOrBlank()) {
        container.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        container.id = id
    }
    if (block != null) {
        container.block()
    }
    addChild(container)
    return container
}

/**
 * Declarative DSL builder for an [UnalienContainer] inside an [AlienContainer], `AlienFragment`,
 * or any [AbstractContainer], restoring the host OS `NativeFactory` so genuine native `:common`
 * components can be mixed into an Alien interface.
 */
fun AbstractContainer.unalienContainer(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker UnalienContainer.() -> Unit)? = null
): UnalienContainer {
    val container = UnalienContainer()
    if (!classes.isNullOrBlank()) {
        container.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        container.id = id
    }
    if (block != null) {
        container.block()
    }
    addChild(container)
    return container
}
