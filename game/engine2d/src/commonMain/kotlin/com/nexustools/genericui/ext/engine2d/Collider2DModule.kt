package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.ext.box2d.Body2D
import com.nexustools.genericui.ext.box2d.CircleShape2D
import com.nexustools.genericui.ext.box2d.Fixture2D
import com.nexustools.genericui.ext.box2d.FixtureDef2D
import com.nexustools.genericui.ext.box2d.PolygonShape2D
import com.nexustools.genericui.ext.box2d.Shape2D
import com.nexustools.genericui.ext.engine.scene.EntityModule
import com.nexustools.genericui.ext.engine.scene.SceneEntity

/**
 * Attaches a Box2D [Shape2D] fixture to a sibling [RigidBody2DModule].
 */
class Collider2DModule(
    val shape: Shape2D,
    var density: Float = 1f,
    var friction: Float = 0.25f,
    var restitution: Float = 0.1f,
    var isSensor: Boolean = false
) : EntityModule() {

    var fixture: Fixture2D? = null
        private set

    override fun onAttach(owner: SceneEntity) {
        val body = owner.getModule<RigidBody2DModule>()?.ensureBodyCreated() ?: return
        attachToBody(body)
    }

    override fun onDetach(owner: SceneEntity) {
        val existing = fixture ?: return
        existing.body.destroyFixture(existing)
        fixture = null
    }

    internal fun attachToBody(body: Body2D) {
        if (fixture != null) return
        val def = FixtureDef2D(
            shape = shape,
            density = density,
            friction = friction,
            restitution = restitution,
            isSensor = isSensor,
            userData = entity
        )
        fixture = body.createFixture(def)
    }

    companion object {
        fun box(
            width: Float,
            height: Float,
            density: Float = 1f,
            friction: Float = 0.25f,
            restitution: Float = 0.1f,
            isSensor: Boolean = false
        ): Collider2DModule = Collider2DModule(
            shape = PolygonShape2D().setAsBox(width * 0.5f, height * 0.5f),
            density = density,
            friction = friction,
            restitution = restitution,
            isSensor = isSensor
        )

        fun circle(
            radius: Float,
            density: Float = 1f,
            friction: Float = 0.25f,
            restitution: Float = 0.1f,
            isSensor: Boolean = false
        ): Collider2DModule = Collider2DModule(
            shape = CircleShape2D(radius),
            density = density,
            friction = friction,
            restitution = restitution,
            isSensor = isSensor
        )
    }
}
