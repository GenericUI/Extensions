package com.nexustools.genericui.ext.box2d.dsl

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.box2d.Box2DDebugDrawer
import com.nexustools.genericui.ext.box2d.Vec2
import com.nexustools.genericui.ext.box2d.World2D
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtSurface
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker

/**
 * In-place 2D physics debug canvas builder backed by [Canvas2DExtSurface], [World2D], and [Box2DDebugDrawer].
 */
fun AbstractContainer.box2dDebugCanvas(
    world: World2D = World2D(),
    width: Int = 800,
    height: Int = 600,
    pixelsPerMeter: Float = 50f,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker Box2DWorldDslScope.() -> Unit)? = null
): Canvas2DExtSurface {
    val surface = Canvas2DExtSurface(width = width, height = height)
    if (!classes.isNullOrBlank()) {
        surface.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        surface.id = id
    }
    val drawer = Box2DDebugDrawer(
        pixelsPerMeter = pixelsPerMeter,
        offsetX = width * 0.5f,
        offsetY = height * 0.5f
    )
    val scope = Box2DWorldDslScope(world = world, debugDrawer = drawer)
    if (block != null) {
        scope.block()
    }
    drawer.bindToCanvas2D(world = world, canvas = surface)
    addChild(surface)
    return surface
}

/**
 * Standalone [World2D] DSL builder.
 */
fun world2D(
    gravity: Vec2 = Vec2(0f, -9.81f),
    allowSleeping: Boolean = true,
    block: (@GenericUiDslMarker Box2DWorldDslScope.() -> Unit)? = null
): World2D {
    val world = World2D(gravity = gravity, allowSleeping = allowSleeping)
    if (block != null) {
        Box2DWorldDslScope(world = world).block()
    }
    return world
}
