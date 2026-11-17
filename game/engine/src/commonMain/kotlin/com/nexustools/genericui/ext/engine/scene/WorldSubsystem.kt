package com.nexustools.genericui.ext.engine.scene

/**
 * Scene-wide simulation or rendering subsystem (such as 2D/3D physics worlds) attached to a [GameScene].
 */
interface WorldSubsystem {
    fun onAttach(scene: GameScene) {}

    fun onDetach(scene: GameScene) {}

    fun onFixedStep(fixedDeltaSeconds: Float) {}

    fun onTick(deltaSeconds: Float) {}

    fun onLateTick(deltaSeconds: Float) {}

    fun onRenderVBlank(frameNumber: Long, deltaSeconds: Float, timestampMs: Double) {}

    fun onDispose() {}
}
