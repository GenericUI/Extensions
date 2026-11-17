package com.nexustools.genericui.ext.engine.dsl

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.engine.animation.AnimationCurve
import com.nexustools.genericui.ext.engine.animation.AnimationScheduler
import com.nexustools.genericui.ext.engine.animation.AnimationWrapMode
import com.nexustools.genericui.ext.engine.animation.EasingFunction
import com.nexustools.genericui.ext.engine.animation.LambdaAnimationClip
import com.nexustools.genericui.ext.engine.scene.GameScene
import com.nexustools.genericui.ext.engine.svg.Skeleton2D
import com.nexustools.genericui.ext.engine.ui.UiStageHost

/**
 * Constructs a standalone [GameScene] via the game engine DSL.
 */
fun stageScene(
    name: String = "GameScene",
    block: (@GenericUiDslMarker GameSceneDslScope.() -> Unit)? = null
): GameScene {
    val scene = GameScene(name = name)
    if (block != null) {
        GameSceneDslScope(scene).block()
    }
    return scene
}

/**
 * Mounts an in-place pure-UI [UiStageHost] inside this [AbstractContainer].
 */
fun AbstractContainer.uiStageHost(
    name: String = "UiStageScene",
    targetTicksPerSecond: Int = 60,
    autoStart: Boolean = false,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker GameSceneDslScope.() -> Unit)? = null
): UiStageHost {
    val scene = stageScene(name = name, block = block)
    val host = UiStageHost(scene = scene, targetTicksPerSecond = targetTicksPerSecond)
    if (!classes.isNullOrBlank()) {
        host.rootContainer.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        host.rootContainer.id = id
    }
    addChild(host.rootContainer)
    if (autoStart) {
        host.start()
    }
    return host
}

/**
 * Configures Xbox-mapped gamepad, key, and pointer action bindings on this [GameScene].
 */
fun GameScene.gamepadBindings(block: (@GenericUiDslMarker GamepadBindingDslScope.() -> Unit)) {
    GamepadBindingDslScope(actionMap).block()
}

/**
 * Registers a VBlank-evaluated [LambdaAnimationClip] onto this [AnimationScheduler].
 */
fun AnimationScheduler.animateLambda(
    durationSeconds: Float,
    curve: AnimationCurve = EasingFunction.LINEAR,
    wrapMode: AnimationWrapMode = AnimationWrapMode.ONCE,
    onComplete: (() -> Unit)? = null,
    onProgress: (progress: Float) -> Unit
): LambdaAnimationClip = playLambda(
    durationSeconds = durationSeconds,
    curve = curve,
    wrapMode = wrapMode,
    onComplete = onComplete,
    onProgress = onProgress
)

/**
 * Constructs an articulated 2D [Skeleton2D] via the skeleton DSL.
 */
fun skeleton2D(
    rootOffsetX: Float = 0f,
    rootOffsetY: Float = 0f,
    block: (@GenericUiDslMarker Skeleton2DDslScope.() -> Unit)
): Skeleton2D {
    val scope = Skeleton2DDslScope(rootOffsetX = rootOffsetX, rootOffsetY = rootOffsetY)
    scope.block()
    return scope.build()
}
