package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.ext.engine.scene.GameScene
import com.nexustools.genericui.ext.engine.threading.GameLoopScheduler
import com.nexustools.genericui.ext.threejs.cameras.Camera
import com.nexustools.genericui.ext.threejs.cameras.PerspectiveCamera
import com.nexustools.genericui.ext.threejs.renderers.WebGLRenderer
import com.nexustools.genericui.ext.threejs.scenes.Scene

/**
 * Integrates a [GameScene], Three.js [Scene] + [WebGLRenderer] ([Canvas3D]), and dedicated
 * Logic Thread [GameLoopScheduler] so every hardware VBlank frame evaluates animations and
 * skeletal rigs before rendering the 3D scene.
 */
class Game3DCanvasHost(
    val scene: GameScene = GameScene("Game3DScene"),
    val renderer: WebGLRenderer = WebGLRenderer(),
    val threeScene: Scene = Scene(),
    var activeCamera: Camera = PerspectiveCamera(
        fov = 50f,
        aspect = renderer.canvas.initialWidth.toFloat() / renderer.canvas.initialHeight.toFloat(),
        near = 0.1f,
        far = 2000f
    ).apply { position.set(0f, 2f, 6f) },
    targetTicksPerSecond: Int = 60
) {
    val canvas: Canvas3D
        get() = renderer.canvas

    val scheduler: GameLoopScheduler = GameLoopScheduler(
        scene = scene,
        targetTicksPerSecond = targetTicksPerSecond
    )

    private val sceneGraphSubsystem: SceneGraph3DSubsystem = SceneGraph3DSubsystem(threeScene)
    private var vblankFrameCounter: Long = 0L

    init {
        scene.registerSubsystem(sceneGraphSubsystem)

        canvas.onPointerDown = { ev -> scene.input.notifyPointerDown(ev) }
        canvas.onPointerMove = { ev -> scene.input.notifyPointerMove(ev) }
        canvas.onPointerUp = { ev -> scene.input.notifyPointerUp(ev) }
        canvas.onWheel = { ev -> scene.input.notifyWheel(ev) }
        canvas.onKeyDown = { ev -> scene.input.notifyKeyDown(ev) }
        canvas.onKeyUp = { ev -> scene.input.notifyKeyUp(ev) }

        renderer.setAnimationLoop { _, elapsedTimeMs ->
            vblankFrameCounter++
            scene.vblankDriver.onBeforeFramePresent(vblankFrameCounter, elapsedTimeMs)
            scene.getSubsystem<PhysicsWorld3DSubsystem>()?.updateDebugRenderer()
            renderer.render(threeScene, activeCamera)
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
        renderer.setAnimationLoop(null)
        renderer.dispose()
        scene.dispose()
    }
}
