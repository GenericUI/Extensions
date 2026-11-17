package com.nexustools.genericui.ext.engine.scene

import com.nexustools.genericui.ext.engine.threading.GameLoopScheduler

/**
 * Multi-scene coordinator owning registered [GameScene]s and a [GameLoopScheduler] executing on
 * the dedicated Game Logic Tick Thread.
 */
class GameWorld(
    initialScene: GameScene = GameScene("MainScene"),
    targetTicksPerSecond: Int = 60
) {
    private val scenes = LinkedHashMap<String, GameScene>()

    var activeScene: GameScene = initialScene
        private set

    var scheduler: GameLoopScheduler = GameLoopScheduler(
        scene = initialScene,
        targetTicksPerSecond = targetTicksPerSecond
    )
        private set

    init {
        scenes[initialScene.name] = initialScene
    }

    fun registerScene(scene: GameScene) {
        scenes[scene.name] = scene
    }

    fun getScene(name: String): GameScene? = scenes[name]

    fun loadScene(name: String): GameScene {
        val target = scenes[name]
            ?: throw IllegalArgumentException("No GameScene registered under name '$name'")
        val wasRunning = scheduler.isRunning
        val tps = scheduler.targetTicksPerSecond
        if (wasRunning) {
            scheduler.stop()
        }
        activeScene = target
        scheduler = GameLoopScheduler(scene = target, targetTicksPerSecond = tps)
        if (wasRunning) {
            scheduler.start()
        }
        return target
    }

    fun start() {
        scheduler.start()
    }

    fun stop() {
        scheduler.stop()
    }

    fun dispose() {
        scheduler.stop()
        for (scene in scenes.values) {
            scene.dispose()
        }
        scenes.clear()
    }
}
