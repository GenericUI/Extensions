package com.nexustools.genericui.ext.engine.dsl

import com.nexustools.genericui.ext.engine.scene.EntityBehavior

/**
 * Callback-configured [EntityBehavior] created via the `behavior { ... }` DSL builder.
 */
class InlineEntityBehavior : EntityBehavior() {
    var onStartAction: (() -> Unit)? = null
    var onFixedStepAction: ((fixedDeltaSeconds: Float) -> Unit)? = null
    var onTickAction: ((deltaSeconds: Float) -> Unit)? = null
    var onLateTickAction: ((deltaSeconds: Float) -> Unit)? = null
    var onRenderVBlankAction: ((frameNumber: Long, deltaSeconds: Float, timestampMs: Double) -> Unit)? = null

    fun onStart(block: () -> Unit) {
        onStartAction = block
    }

    fun onFixedStep(block: (fixedDeltaSeconds: Float) -> Unit) {
        onFixedStepAction = block
    }

    fun onTick(block: (deltaSeconds: Float) -> Unit) {
        onTickAction = block
    }

    fun onLateTick(block: (deltaSeconds: Float) -> Unit) {
        onLateTickAction = block
    }

    fun onRenderVBlank(block: (frameNumber: Long, deltaSeconds: Float, timestampMs: Double) -> Unit) {
        onRenderVBlankAction = block
    }

    override fun onStart() {
        onStartAction?.invoke()
    }

    override fun onFixedStep(fixedDeltaSeconds: Float) {
        onFixedStepAction?.invoke(fixedDeltaSeconds)
    }

    override fun onTick(deltaSeconds: Float) {
        onTickAction?.invoke(deltaSeconds)
    }

    override fun onLateTick(deltaSeconds: Float) {
        onLateTickAction?.invoke(deltaSeconds)
    }

    override fun onRenderVBlank(frameNumber: Long, deltaSeconds: Float, timestampMs: Double) {
        onRenderVBlankAction?.invoke(frameNumber, deltaSeconds, timestampMs)
    }
}
