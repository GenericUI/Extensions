package com.nexustools.genericui.ext.engine.scene

/**
 * Interactive gameplay/simulation behavior module receiving dedicated Game Logic Thread lifecycle
 * callbacks ([onStart], [onFixedStep], [onTick], [onLateTick]) as well as VBlank Render Thread
 * pre-present evaluation ([onRenderVBlank]).
 */
abstract class EntityBehavior : EntityModule() {
    internal var hasStarted: Boolean = false

    open fun onStart() {}

    open fun onFixedStep(fixedDeltaSeconds: Float) {}

    open fun onTick(deltaSeconds: Float) {}

    open fun onLateTick(deltaSeconds: Float) {}

    open fun onRenderVBlank(frameNumber: Long, deltaSeconds: Float, timestampMs: Double) {}
}
