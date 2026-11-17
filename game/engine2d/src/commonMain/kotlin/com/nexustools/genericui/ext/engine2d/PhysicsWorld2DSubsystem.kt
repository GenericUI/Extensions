package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.ext.box2d.Box2DDebugDrawer
import com.nexustools.genericui.ext.box2d.Vec2
import com.nexustools.genericui.ext.box2d.World2D
import com.nexustools.genericui.ext.engine.scene.GameScene
import com.nexustools.genericui.ext.engine.scene.WorldSubsystem
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext

/**
 * [WorldSubsystem] bridging `:extensions:physics:box2d` ([World2D]) into a [GameScene] fixed
 * simulation loop with synchronized rigid-body transforms and optional [Box2DDebugDrawer].
 */
class PhysicsWorld2DSubsystem(
    gravity: Vec2 = Vec2(0f, -9.81f),
    allowSleeping: Boolean = true,
    var velocityIterations: Int = 8,
    var positionIterations: Int = 3
) : WorldSubsystem {
    val world: World2D = World2D(gravity = gravity, allowSleeping = allowSleeping)
    val debugDrawer: Box2DDebugDrawer = Box2DDebugDrawer()
    var debugDrawEnabled: Boolean = false

    private var attachedScene: GameScene? = null

    override fun onAttach(scene: GameScene) {
        attachedScene = scene
    }

    override fun onDetach(scene: GameScene) {
        if (attachedScene === scene) {
            attachedScene = null
        }
    }

    override fun onFixedStep(fixedDeltaSeconds: Float) {
        val scene = attachedScene
        if (scene != null) {
            for (entity in scene.allEntities()) {
                if (!entity.isActive) continue
                entity.getModule<RigidBody2DModule>()?.syncFromEntityBeforeStep()
            }
        }
        world.step(fixedDeltaSeconds, velocityIterations, positionIterations)
        if (scene != null) {
            for (entity in scene.allEntities()) {
                if (!entity.isActive) continue
                entity.getModule<RigidBody2DModule>()?.syncToEntityAfterStep()
            }
        }
    }

    fun drawDebug(ctx: ImageBuffer2DContext) {
        if (debugDrawEnabled) {
            debugDrawer.drawWorld(world, ctx)
        }
    }
}
