package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.ext.box2d.Body2D
import com.nexustools.genericui.ext.box2d.BodyDef2D
import com.nexustools.genericui.ext.box2d.BodyType2D
import com.nexustools.genericui.ext.box2d.Vec2
import com.nexustools.genericui.ext.engine.scene.EntityModule
import com.nexustools.genericui.ext.engine.scene.SceneEntity

/**
 * Attaches a Box2D [Body2D] to a [SceneEntity], lazily registering with [PhysicsWorld2DSubsystem]
 * and keeping [SceneEntity.transform] synchronized with physics integration.
 */
class RigidBody2DModule(
    val bodyType: BodyType2D = BodyType2D.DYNAMIC,
    var gravityScale: Float = 1f,
    var linearDamping: Float = 0f,
    var angularDamping: Float = 0.05f,
    var fixedRotation: Boolean = false
) : EntityModule() {

    var body: Body2D? = null
        private set

    override fun onAttach(owner: SceneEntity) {
        ensureBodyCreated()
    }

    override fun onDetach(owner: SceneEntity) {
        val existing = body ?: return
        val world = owner.scene?.getSubsystem<PhysicsWorld2DSubsystem>()?.world ?: existing.world
        world.destroyBody(existing)
        body = null
    }

    fun ensureBodyCreated(): Body2D? {
        body?.let { return it }
        val owner = entity ?: return null
        val subsystem = owner.scene?.getSubsystem<PhysicsWorld2DSubsystem>() ?: return null
        val def = BodyDef2D(
            type = bodyType,
            position = Vec2(owner.transform.position.x, owner.transform.position.y),
            angle = owner.transform.rotation2DRad,
            linearDamping = linearDamping,
            angularDamping = angularDamping,
            fixedRotation = fixedRotation,
            gravityScale = gravityScale,
            userData = owner
        )
        val created = subsystem.world.createBody(def)
        body = created
        for (collider in owner.findModules<Collider2DModule>()) {
            collider.attachToBody(created)
        }
        return created
    }

    fun syncFromEntityBeforeStep() {
        val b = body ?: ensureBodyCreated() ?: return
        val owner = entity ?: return
        if (b.type == BodyType2D.KINEMATIC) {
            b.setTransform(
                Vec2(owner.transform.position.x, owner.transform.position.y),
                owner.transform.rotation2DRad
            )
        }
    }

    fun syncToEntityAfterStep() {
        val b = body ?: return
        val owner = entity ?: return
        owner.transform.setPosition2D(b.position.x, b.position.y)
        owner.transform.rotation2DRad = b.angle
    }

    fun applyForce(fx: Float, fy: Float) {
        ensureBodyCreated()?.applyForceToCenter(Vec2(fx, fy))
    }

    fun applyImpulse(ix: Float, iy: Float) {
        ensureBodyCreated()?.applyLinearImpulseToCenter(Vec2(ix, iy))
    }
}
