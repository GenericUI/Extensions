package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.ext.engine.scene.GameScene
import com.nexustools.genericui.ext.engine.scene.WorldSubsystem
import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.scenes.Scene

/**
 * Scene subsystem owning the Three.js [threeScene] root for automatic [Object3D] mounting and
 * per-frame transform synchronization.
 */
class SceneGraph3DSubsystem(
    val threeScene: Scene = Scene()
) : WorldSubsystem {
    private var attachedScene: GameScene? = null

    override fun onAttach(scene: GameScene) {
        attachedScene = scene
        for (entity in scene.allEntities()) {
            for (module in entity.modules) {
                if (module is Node3DBindingModule) {
                    attachObject(module.object3D)
                }
            }
        }
    }

    fun attachObject(obj: Object3D) {
        if (obj.parent == null && obj !in threeScene.children) {
            threeScene.add(obj)
        }
    }

    fun detachObject(obj: Object3D) {
        if (obj.parent === threeScene) {
            threeScene.remove(obj)
        }
    }

    override fun onRenderVBlank(frameNumber: Long, deltaSeconds: Float, timestampMs: Double) {
        val scene = attachedScene ?: return
        for (entity in scene.allEntities()) {
            if (!entity.isActive) continue
            for (module in entity.modules) {
                if (module is Node3DBindingModule && module.isEnabled) {
                    module.syncFromEntity()
                }
            }
        }
        threeScene.updateMatrixWorld()
    }
}
