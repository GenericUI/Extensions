package com.nexustools.genericui.ext.box2d.dsl

import com.nexustools.genericui.ext.box2d.Body2D
import com.nexustools.genericui.ext.box2d.BodyDef2D
import com.nexustools.genericui.ext.box2d.BodyType2D
import com.nexustools.genericui.ext.box2d.Box2DDebugDrawer
import com.nexustools.genericui.ext.box2d.Vec2
import com.nexustools.genericui.ext.box2d.World2D
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker

/**
 * Fluent DSL scope for populating a 2D physics [World2D] and configuring its [Box2DDebugDrawer].
 */
@GenericUiDslMarker
class Box2DWorldDslScope(
    val world: World2D = World2D(),
    val debugDrawer: Box2DDebugDrawer = Box2DDebugDrawer()
) {
    /**
     * Creates a [Body2D] in [world] and configures its fixtures via [Box2DBodyDslScope].
     */
    fun body(
        type: BodyType2D = BodyType2D.DYNAMIC,
        x: Float = 0f,
        y: Float = 0f,
        angle: Float = 0f,
        block: (Box2DBodyDslScope.() -> Unit)? = null
    ): Body2D {
        val created = world.createBody(
            BodyDef2D(
                type = type,
                position = Vec2(x, y),
                angle = angle
            )
        )
        if (block != null) {
            Box2DBodyDslScope(created).block()
        }
        return created
    }

    /**
     * Creates a static [Body2D] in [world].
     */
    fun staticBody(
        x: Float = 0f,
        y: Float = 0f,
        angle: Float = 0f,
        block: (Box2DBodyDslScope.() -> Unit)? = null
    ): Body2D = body(
        type = BodyType2D.STATIC,
        x = x,
        y = y,
        angle = angle,
        block = block
    )

    /**
     * Creates a dynamic [Body2D] in [world].
     */
    fun dynamicBody(
        x: Float = 0f,
        y: Float = 0f,
        angle: Float = 0f,
        block: (Box2DBodyDslScope.() -> Unit)? = null
    ): Body2D = body(
        type = BodyType2D.DYNAMIC,
        x = x,
        y = y,
        angle = angle,
        block = block
    )

    /**
     * Creates a kinematic [Body2D] in [world].
     */
    fun kinematicBody(
        x: Float = 0f,
        y: Float = 0f,
        angle: Float = 0f,
        block: (Box2DBodyDslScope.() -> Unit)? = null
    ): Body2D = body(
        type = BodyType2D.KINEMATIC,
        x = x,
        y = y,
        angle = angle,
        block = block
    )
}
