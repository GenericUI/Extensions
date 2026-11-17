package com.nexustools.genericui.ext.engine3d.dsl

import com.nexustools.genericui.ext.box3d.Vec3
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.engine.animation.AnimationCurve
import com.nexustools.genericui.ext.engine.animation.AnimationWrapMode
import com.nexustools.genericui.ext.engine.animation.EasingFunction
import com.nexustools.genericui.ext.engine.animation.LambdaAnimationClip
import com.nexustools.genericui.ext.engine.dsl.EntityDslScope
import com.nexustools.genericui.ext.engine.dsl.GameSceneDslScope
import com.nexustools.genericui.ext.engine.dsl.GamepadBindingDslScope
import com.nexustools.genericui.ext.engine.scene.SceneEntity
import com.nexustools.genericui.ext.engine3d.Game3DCanvasHost
import com.nexustools.genericui.ext.engine3d.PhysicsWorld3DSubsystem
import com.nexustools.genericui.ext.threejs.cameras.Camera
import com.nexustools.genericui.ext.threejs.math.Color

/**
 * Fluent DSL scope for constructing and configuring a 3D [Game3DCanvasHost] scene.
 */
@GenericUiDslMarker
class Game3DDslScope(
    val host: Game3DCanvasHost
) {
    val sceneScope: GameSceneDslScope = GameSceneDslScope(host.scene)

    fun background(colorHex: Int) {
        host.threeScene.background = Color(colorHex)
    }

    fun activeCamera(camera: Camera) {
        host.activeCamera = camera
    }

    fun physics3D(
        gravity: Vec3 = Vec3(0f, -9.81f, 0f),
        debugDraw: Boolean = false,
        block: (@GenericUiDslMarker PhysicsWorld3DSubsystem.() -> Unit)? = null
    ): PhysicsWorld3DSubsystem {
        val sub = PhysicsWorld3DSubsystem(gravity = gravity, threeScene = host.threeScene).apply {
            debugDrawEnabled = debugDraw
        }
        block?.invoke(sub)
        return host.scene.registerSubsystem(sub)
    }

    fun entity(
        name: String = "Entity",
        tag: String = "Default",
        parent: SceneEntity? = null,
        block: (@GenericUiDslMarker EntityDslScope.() -> Unit)? = null
    ): SceneEntity = sceneScope.entity(name = name, tag = tag, parent = parent, block = block)

    fun gamepadBindings(block: (@GenericUiDslMarker GamepadBindingDslScope.() -> Unit)) {
        sceneScope.gamepadBindings(block)
    }

    fun animateLambda(
        durationSeconds: Float,
        curve: AnimationCurve = EasingFunction.LINEAR,
        wrapMode: AnimationWrapMode = AnimationWrapMode.ONCE,
        onComplete: (() -> Unit)? = null,
        onProgress: (progress: Float) -> Unit
    ): LambdaAnimationClip = sceneScope.animateLambda(durationSeconds, curve, wrapMode, onComplete, onProgress)
}
