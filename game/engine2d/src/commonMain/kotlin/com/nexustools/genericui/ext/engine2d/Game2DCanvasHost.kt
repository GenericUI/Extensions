package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.component.media.VBlankSubscription
import com.nexustools.genericui.ext.engine.scene.GameScene
import com.nexustools.genericui.ext.engine.threading.GameLoopScheduler

/**
 * Integrates a [GameScene], [Canvas2D], dedicated Logic Thread [GameLoopScheduler], and VBlank
 * [Engine2DSceneRenderer] so every hardware VBlank frame evaluates animations and draws the 2D scene.
 */
class Game2DCanvasHost(
    val scene: GameScene = GameScene("Game2DScene"),
    val canvas: Canvas2D = Canvas2D(width = 800, height = 600),
    targetTicksPerSecond: Int = 60,
    val renderer: Engine2DSceneRenderer = Engine2DSceneRenderer()
) {
    val scheduler: GameLoopScheduler = GameLoopScheduler(
        scene = scene,
        targetTicksPerSecond = targetTicksPerSecond
    )

    private var vblankSubscription: VBlankSubscription? = null

    init {
        canvas.onPointerDown = { ev -> scene.input.notifyPointerDown(ev) }
        canvas.onPointerMove = { ev -> scene.input.notifyPointerMove(ev) }
        canvas.onPointerUp = { ev -> scene.input.notifyPointerUp(ev) }
        canvas.onWheel = { ev -> scene.input.notifyWheel(ev) }
        canvas.onKeyDown = { ev -> scene.input.notifyKeyDown(ev) }
        canvas.onKeyUp = { ev -> scene.input.notifyKeyUp(ev) }

        vblankSubscription = canvas.registerVBlankRender { ctx, _, totalElapsedMs, frameNumber ->
            scene.vblankDriver.onBeforeFramePresent(frameNumber, totalElapsedMs)
            renderer.render(scene, ctx)
        }
    }

    fun start() {
        scheduler.start()
    }

    fun stop() {
        scheduler.stop()
    }

    fun stepVBlank(explicitTimestampMs: Double? = null) {
        canvas.dispatchVBlankFrame(explicitTimestampMs)
    }

    fun dispose() {
        scheduler.stop()
        vblankSubscription?.dispose()
        vblankSubscription = null
        scene.dispose()
    }
}
