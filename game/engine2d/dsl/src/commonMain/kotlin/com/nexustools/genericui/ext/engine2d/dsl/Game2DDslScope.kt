package com.nexustools.genericui.ext.engine2d.dsl

import com.nexustools.genericui.ext.box2d.Vec2
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.engine.animation.AnimationCurve
import com.nexustools.genericui.ext.engine.animation.AnimationWrapMode
import com.nexustools.genericui.ext.engine.animation.EasingFunction
import com.nexustools.genericui.ext.engine.animation.LambdaAnimationClip
import com.nexustools.genericui.ext.engine.dsl.EntityDslScope
import com.nexustools.genericui.ext.engine.dsl.GameSceneDslScope
import com.nexustools.genericui.ext.engine.dsl.GamepadBindingDslScope
import com.nexustools.genericui.ext.engine.scene.SceneEntity
import com.nexustools.genericui.ext.engine2d.Game2DCanvasHost
import com.nexustools.genericui.ext.engine2d.PhysicsWorld2DSubsystem
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Fluent DSL scope for constructing and configuring a 2D [Game2DCanvasHost] scene.
 */
@GenericUiDslMarker
class Game2DDslScope(
    val host: Game2DCanvasHost
) {
    val sceneScope: GameSceneDslScope = GameSceneDslScope(host.scene)

    var clearColor: RgbaColor?
        get() = host.renderer.clearColor
        set(value) {
            host.renderer.clearColor = value
        }

    fun physics2D(
        gravity: Vec2 = Vec2(0f, -9.81f),
        debugDraw: Boolean = false,
        block: (@GenericUiDslMarker PhysicsWorld2DSubsystem.() -> Unit)? = null
    ): PhysicsWorld2DSubsystem {
        val sub = PhysicsWorld2DSubsystem(gravity = gravity).apply {
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
