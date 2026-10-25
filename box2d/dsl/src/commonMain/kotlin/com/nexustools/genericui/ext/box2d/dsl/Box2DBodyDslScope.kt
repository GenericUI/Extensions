package com.nexustools.genericui.ext.box2d.dsl

import com.nexustools.genericui.ext.box2d.Body2D
import com.nexustools.genericui.ext.box2d.CircleShape2D
import com.nexustools.genericui.ext.box2d.EdgeShape2D
import com.nexustools.genericui.ext.box2d.Fixture2D
import com.nexustools.genericui.ext.box2d.FixtureDef2D
import com.nexustools.genericui.ext.box2d.PolygonShape2D
import com.nexustools.genericui.ext.box2d.Vec2
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker

/**
 * Fluent DSL scope for attaching [Fixture2D] collision shapes to a [Body2D].
 */
@GenericUiDslMarker
class Box2DBodyDslScope(
    val body: Body2D
) {
    /**
     * Creates and attaches a [Fixture2D] configured via [FixtureDef2D].
     */
    fun fixture(
        def: FixtureDef2D = FixtureDef2D(),
        block: (FixtureDef2D.() -> Unit)? = null
    ): Fixture2D {
        if (block != null) {
            def.block()
        }
        return body.createFixture(def)
    }

    /**
     * Creates and attaches a circular [Fixture2D] to [body].
     */
    fun circleFixture(
        radius: Float = 0.5f,
        density: Float = 1f,
        friction: Float = 0.2f,
        restitution: Float = 0f,
        isSensor: Boolean = false
    ): Fixture2D = body.createFixture(
        FixtureDef2D(
            shape = CircleShape2D(radius = radius),
            density = density,
            friction = friction,
            restitution = restitution,
            isSensor = isSensor
        )
    )

    /**
     * Creates and attaches a box [PolygonShape2D] [Fixture2D] to [body].
     */
    fun boxFixture(
        halfWidth: Float = 0.5f,
        halfHeight: Float = 0.5f,
        density: Float = 1f,
        friction: Float = 0.2f,
        restitution: Float = 0f,
        isSensor: Boolean = false
    ): Fixture2D = body.createFixture(
        FixtureDef2D(
            shape = PolygonShape2D().setAsBox(halfWidth, halfHeight),
            density = density,
            friction = friction,
            restitution = restitution,
            isSensor = isSensor
        )
    )

    /**
     * Creates and attaches a two-sided [EdgeShape2D] [Fixture2D] to [body].
     */
    fun edgeFixture(
        v1: Vec2,
        v2: Vec2,
        density: Float = 0f,
        friction: Float = 0.2f,
        restitution: Float = 0f
    ): Fixture2D = body.createFixture(
        FixtureDef2D(
            shape = EdgeShape2D(v1, v2),
            density = density,
            friction = friction,
            restitution = restitution
        )
    )
}
