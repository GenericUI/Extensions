package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.component.media.AbstractCanvas
import com.nexustools.genericui.ext.engine.scene.EntityBehavior
import com.nexustools.genericui.ext.engine.scene.SceneEntity
import com.nexustools.genericui.ext.threejs.cameras.PerspectiveCamera
import com.nexustools.genericui.ext.threejs.controls.OrbitControls

/**
 * Interactive spherical orbit camera module wrapping Three.js [PerspectiveCamera] and [OrbitControls].
 */
class OrbitCamera3DModule(
    val camera: PerspectiveCamera = PerspectiveCamera(50f, 800f / 600f, 0.1f, 2000f),
    canvas: AbstractCanvas<*>? = null
) : EntityBehavior() {

    val controls: OrbitControls = OrbitControls(camera = camera, canvas = canvas)

    fun attachToCanvas(canvas: AbstractCanvas<*>) {
        controls.attachToCanvas(canvas)
    }

    override fun onAttach(owner: SceneEntity) {
        camera.position.set(
            owner.transform.position.x,
            owner.transform.position.y,
            owner.transform.position.z
        )
        controls.update(0f)
        owner.scene?.getSubsystem<SceneGraph3DSubsystem>()?.attachObject(camera)
    }

    override fun onTick(deltaSeconds: Float) {
        controls.update(deltaSeconds)
        entity?.transform?.position?.set(camera.position.x, camera.position.y, camera.position.z)
    }
}
