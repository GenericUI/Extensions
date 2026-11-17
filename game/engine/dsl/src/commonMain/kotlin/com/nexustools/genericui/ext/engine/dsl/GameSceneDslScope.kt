package com.nexustools.genericui.ext.engine.dsl

import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.engine.animation.AnimationCurve
import com.nexustools.genericui.ext.engine.animation.AnimationWrapMode
import com.nexustools.genericui.ext.engine.animation.EasingFunction
import com.nexustools.genericui.ext.engine.animation.LambdaAnimationClip
import com.nexustools.genericui.ext.engine.scene.GameScene
import com.nexustools.genericui.ext.engine.scene.SceneEntity
import com.nexustools.genericui.ext.engine.scene.WorldSubsystem

/**
 * Fluent DSL scope for populating a [GameScene] with entities, subsystems, animations, and
 * Xbox-mapped gamepad input bindings.
 */
@GenericUiDslMarker
class GameSceneDslScope(
    val scene: GameScene
) {
    fun entity(
        name: String = "Entity",
        tag: String = "Default",
        parent: SceneEntity? = null,
        block: (@GenericUiDslMarker EntityDslScope.() -> Unit)? = null
    ): SceneEntity {
        val created = scene.createEntity(name = name, tag = tag, parent = parent)
        if (block != null) {
            EntityDslScope(created).block()
        }
        return created
    }

    fun <T : WorldSubsystem> subsystem(sub: T): T = scene.registerSubsystem(sub)

    fun gamepadBindings(block: (@GenericUiDslMarker GamepadBindingDslScope.() -> Unit)) {
        GamepadBindingDslScope(scene.actionMap).block()
    }

    fun animateLambda(
        durationSeconds: Float,
        curve: AnimationCurve = EasingFunction.LINEAR,
        wrapMode: AnimationWrapMode = AnimationWrapMode.ONCE,
        onComplete: (() -> Unit)? = null,
        onProgress: (progress: Float) -> Unit
    ): LambdaAnimationClip = scene.animations.playLambda(
        durationSeconds = durationSeconds,
        curve = curve,
        wrapMode = wrapMode,
        onComplete = onComplete,
        onProgress = onProgress
    )
}
